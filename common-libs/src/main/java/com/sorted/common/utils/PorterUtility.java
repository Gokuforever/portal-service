package com.sorted.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sorted.common.beans.DeliveryRequestAttempts;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.Order_Details_Service;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.*;
import com.sorted.common.exceptions.BadRequestException;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.exceptions.DeliveryNotAvailableException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.helper.MailBuilder;
import com.sorted.common.helper.OrderTemplateHelper;
import com.sorted.common.helper.SEResponse;
import com.sorted.common.helper.ThirdPartAPITraceHelper;
import com.sorted.common.notifications.EmailSenderImpl;
import com.sorted.common.notifications.SMSService;
import com.sorted.common.notifications.helper.SmsTraceHelper;
import com.sorted.common.porter.req.beans.CreateOrderBean;
import com.sorted.common.porter.req.beans.GetQuoteRequest;
import com.sorted.common.porter.req.beans.PorterWebhookBean;
import com.sorted.common.porter.res.beans.CreateOrderResBean;
import com.sorted.common.porter.res.beans.CreateOrderResBean.CreateOrderResBeanBuilder;
import com.sorted.common.porter.res.beans.FetchOrderRes;
import com.sorted.common.porter.res.beans.FetchOrderRes.*;
import com.sorted.common.porter.res.beans.FetchOrderRes.FareDetails.FareAmountDetails;
import com.sorted.common.porter.res.beans.FetchOrderRes.FareDetails.FareAmountDetails.FareAmountDetailsBuilder;
import com.sorted.common.porter.res.beans.FetchOrderRes.FareDetails.FareDetailsBuilder;
import com.sorted.common.porter.res.beans.FetchOrderRes.PartnerInfo.PartnerInfoBuilder;
import com.sorted.common.porter.res.beans.GetQuoteResponse;
import com.sorted.common.porter.res.beans.GetQuoteResponse.Vehicle;
import com.sorted.common.porter.res.beans.GetQuoteResponse.Vehicle.Fare;
import com.sorted.common.porter.res.beans.GetQuoteResponse.Vehicle.Fare.FareBuilder;
import com.sorted.common.porter.res.beans.GetQuoteResponse.Vehicle.VehicleBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class PorterUtility {
    private final ObjectMapper mapper = new ObjectMapper();

    private final Order_Details_Service order_Details_Service;
    private final Users_Service usersService;
    private final EmailSenderImpl emailSenderImpl;
    private final OrderTemplateHelper orderTemplateHelper;
    private final Order_Item_Service order_Item_Service;
    private final GenerateInvoiceService generateInvoiceService;
    private final InternalMailService internalMailService;
    private final ThirdPartAPITraceHelper traceHelper;
    private final SmsTraceHelper smsTraceHelper;
    private final SMSService smsService;
    private final RestTemplate restTemplate = new RestTemplate();


    @Value("${se.porter.store.operational.check.enabled:false}")
    private boolean porterStoreOperationalCheckEnabled;

    @Value("${porter.base.url}")
    private String porterBaseUrl;

    @Value("${porter.api.create.endpoint}")
    private String porterCreateOrderEndpoint;

    @Value("${porter.api.get.endpoint}")
    private String porterGetOrderEndpoint;

    @Value("${porter.api.quote.endpoint}")
    private String porterGetQuoteEndpoint;

    @Value("${porter.api.key}")
    private String porterApiKey;

    @Value("${porter.country.code}")
    private String countryCode;

    @Value("${porter.error.message}")
    private String porterErrorMessage;

    @Value("${se.enable.sms:false}")
    private boolean enableSms;

    @Value("${porter.mock.enabled:false}")
    private boolean porterResponseMockEnabled;

    public GetQuoteResponse getDeliveryQuote(GetQuoteRequest request) {
        return traceHelper.runWithTrace(ThirdPartyAPIType.PORTER_GET_QUOTE, request, () -> this.getQuote(request));
    }

    public CreateOrderResBean createOrderForPickup(CreateOrderBean order) {
        return traceHelper.runWithTrace(ThirdPartyAPIType.PORTER_CREATE_ORDER, order, () -> this.createOrder(order));
    }

    public FetchOrderRes getOrderStatus(String porterOrderId) {
        return traceHelper.runWithTrace(ThirdPartyAPIType.PORTER_GET_ORDER_STATUS, porterOrderId, () -> this.getOrder(porterOrderId));
    }

    private CreateOrderResBean createOrder(CreateOrderBean order) {

        CreateOrderBean.Address pickup_address = order.getPickup_details().getAddress();
        CreateOrderBean.Address drop_address = order.getDrop_details().getAddress();
//        if (porterMockLocationEnabled) {
//            pickup_address.setLat(BigDecimal.valueOf(mockPickupLat));
//            pickup_address.setLng(BigDecimal.valueOf(mockPickupLng));
//            drop_address.setLat(BigDecimal.valueOf(mockDropLat));
//            drop_address.setLng(BigDecimal.valueOf(mockDropLng));
//        }
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(Order_Details.Fields.code, order.getRequest_id()));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details order_Details = order_Details_Service.repoFindOne(filterOD);
        if (order_Details == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
        }

        List<DeliveryRequestAttempts> delivery_request_attempts = CollectionUtils.isEmpty(order_Details.getDelivery_request_attempts()) ? new ArrayList<>() : order_Details.getDelivery_request_attempts();

        String url = porterBaseUrl + porterCreateOrderEndpoint;

        // Set the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", porterApiKey);

        Gson gson = GsonUtils.getGson();
        String payload = gson.toJson(order);

        HttpEntity<String> request = new HttpEntity<>(payload, headers);


        ResponseEntity<String> response = null;
        if (porterResponseMockEnabled) {
            String mockResponse = "{\"request_id\": \"" + order.getRequest_id() + "\", " +
                    "\"order_id\": \"CRN" + order.getRequest_id() + "\", " +
                    "\"estimated_pickup_time\": 1642473111, " +
                    "\"estimated_fare_details\": { " +
                    "  \"currency\": \"" + "INR" + "\", " +
                    "  \"minor_amount\": 35000 }, " +
                    "\"tracking_url\": \"https://porter.in/track_live_order?booking_id=CRN" + order.getRequest_id() + "&customer_uuid=0337fe22-0745-4d5c-8514-3003912be89a\"}";
            response = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        } else {
            // Make the POST request
            try {
                response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            } catch (HttpServerErrorException.InternalServerError ex) {
                log.error("Exception occurred with message: {}", ex.getMessage(), ex);
                String responseBody = ex.getResponseBodyAsString();
                extractError(order_Details, delivery_request_attempts, responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        if (response == null) {
            extractError(order_Details, delivery_request_attempts, "No Response", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        assert response != null;
        HttpStatus httpStatus = HttpStatus.resolve(response.getStatusCode().value());

        if (httpStatus == null) {
            throw new CustomIllegalArgumentsException(porterErrorMessage);
        }
        switch (httpStatus) {
            case CREATED, OK:
                break;
            default:
                extractError(order_Details, delivery_request_attempts, response.getBody(), httpStatus);
        }

        JsonNode root;
        try {
            root = mapper.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        CreateOrderResBeanBuilder createOrderResBeanBuilder = CreateOrderResBean.builder();

        String request_id = root.path("request_id").asText(null);
        String order_id = root.path("order_id").asText(null);
        String tracking_url = root.path("tracking_url").asText(null);
        long estimated_pickup_time = root.path("estimated_pickup_time").asLong();
        LocalDateTime estimated_pickup_time_ldt = CommonUtils.convertEpochToLocalDateTime(estimated_pickup_time);

        FareAmountDetailsBuilder estimatedFareDetailsBuilder = FareAmountDetails.builder();
        JsonNode estimated_fare_details = root.path("estimated_fare_details");

        if (!estimated_fare_details.isNull()) {
            String currency = estimated_fare_details.path("currency").asText(null);
            Long minor_amount = estimated_fare_details.path("minor_amount").asLong();
            estimatedFareDetailsBuilder.currency(currency).minor_amount(minor_amount);
        }

        FareAmountDetails estimatedFareDetails = estimatedFareDetailsBuilder.build();

        return createOrderResBeanBuilder.request_id(request_id).order_id(order_id).tracking_url(tracking_url).estimated_pickup_time(estimated_pickup_time_ldt).estimated_fare_details(estimatedFareDetails).build();
    }

    private void extractError(Order_Details order_Details, List<DeliveryRequestAttempts> delivery_request_attempts, String response, HttpStatus httpStatus) {
        Gson gson = GsonUtils.getGson();
        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
        String type = jsonResponse.has("type") ? jsonResponse.get("type").getAsString() : null;
        String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : null;

        delivery_request_attempts.add(DeliveryRequestAttempts.builder().count(delivery_request_attempts.size() + 1).message(message).type(type).response_code(httpStatus.value()).build());
        order_Details.setDelivery_request_attempts(delivery_request_attempts);
        order_Details_Service.update(order_Details.getId(), order_Details, "porter");
        throw new CustomIllegalArgumentsException(porterErrorMessage);
    }

    private FetchOrderRes getOrder(String porterOrderId) {

        // Define the URL
        String url = porterBaseUrl + porterGetOrderEndpoint + porterOrderId;

        String orderId = porterOrderId.substring(3);
        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", porterApiKey);

        // Create an HttpEntity with the headers (no body needed)
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response;
        if (porterResponseMockEnabled) {
            String mockResponse = "{\n" +
                    "    \"order_id\": \"" + porterOrderId + "\",\n" +
                    "    \"status\": \"ended\",\n" +
                    "    \"partner_info\":\n" +
                    "    {\n" +
                    "        \"name\": \"Anupam Patel\",\n" +
                    "        \"vehicle_number\": \"AK-02-HH-2020\",\n" +
                    "        \"vehicle_type\": \"TWO_WHEELER\",\n" +
                    "        \"mobile\":\n" +
                    "        {\n" +
                    "            \"country_code\": \"" + countryCode + "\",\n" +
                    "            \"mobile_number\": \"9535321734\"\n" +
                    "        },\n" +
                    "        \"partner_secondary_mobile\":\n" +
                    "        {\n" +
                    "            \"country_code\": \"" + countryCode + "\",\n" +
                    "            \"mobile_number\": \"9535321734\"\n" +
                    "        },\n" +
                    "        \"location\": null\n" +
                    "    },\n" +
                    "    \"order_timings\":\n" +
                    "    {\n" +
                    "        \"pickup_time\": 1669879581,\n" +
                    "        \"order_accepted_time\": 1669877932,\n" +
                    "        \"order_started_time\": 1669877997,\n" +
                    "        \"order_ended_time\": 1669878042\n" +
                    "    },\n" +
                    "    \"fare_details\":\n" +
                    "    {\n" +
                    "        \"estimated_fare_details\": null,\n" +
                    "        \"actual_fare_details\":\n" +
                    "        {\n" +
                    "            \"currency\": \"" + "INR" + "\",\n" +
                    "            \"minor_amount\": 5500\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";
            response = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        } else {
            // Make the GET request
            response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
        }
        // Print the response
        log.info("Response:: " + response.getBody());
        String body = response.getBody();
        return this.parseAndAccessFields(body);

    }

    public GetQuoteRequest buildGetQuoteRequest(Address pickupAddress, Address dropAddress, String mobile, String customerFullName) {
        return GetQuoteRequest.builder()
                .pickup_details(GetQuoteRequest.PickupDetails.builder()
                        .lat(pickupAddress.getLat().doubleValue())
                        .lng(pickupAddress.getLng().doubleValue())
                        .build())
                .drop_details(GetQuoteRequest.DropDetails.builder()
                        .lat(dropAddress.getLat().doubleValue())
                        .lng(dropAddress.getLng().doubleValue())
                        .build())
                .customer(GetQuoteRequest.Customer.builder()
                        .name(customerFullName)
                        .mobile(GetQuoteRequest.Customer.Mobile.builder()
                                .country_code(countryCode)
                                .number(mobile.length() > 10 ? mobile.substring(mobile.length() - 11, mobile.length() - 1) : mobile)
                                .build())
                        .build())
                .build();
    }

    private GetQuoteResponse getQuote(GetQuoteRequest quoteRequest) {
        RestTemplate restTemplate = new RestTemplate();

        String url = porterBaseUrl + porterGetQuoteEndpoint;

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", porterApiKey);

        // @formatter:off
        // Build the request object using Builder
//        GetQuoteRequest quoteRequest = GetQuoteRequest.builder()
//                .pickup_details(GetQuoteRequest.PickupDetails.builder()
//                        .lat(19.9982)
//                        .lng(73.7531)
//                        .build())
//                .drop_details(GetQuoteRequest.DropDetails.builder()
//                        .lat(12.89795704454522)
//                        .lng(77.62119799020186)
//                        .build())
//                .customer(GetQuoteRequest.Customer.builder()
//                        .name("Porter Test User")
//                        .mobile(GetQuoteRequest.Customer.Mobile.builder()
//                                .country_code("+91")
//                                .number("7678139714")
//                                .build())
//                        .build())
//                .build();
//        // @formatter:on


        // Create the HttpEntity with headers and the request body
        HttpEntity<GetQuoteRequest> request = new HttpEntity<>(quoteRequest, headers);

        // Make the POST request
        ResponseEntity<String> response = null;
        HttpStatus httpStatus;
        String responseBody = null;
        if (porterResponseMockEnabled) {
            httpStatus = HttpStatus.OK;
            responseBody = "{\"vehicles\":[{\"type\":\"Tata 407\",\"eta\":null,\"fare\":{\"currency\":\"INR\",\"minor_amount\":84621},\"capacity\":{\"value\":2500.0,\"unit\":\"kg\"},\"size\":{\"length\":{\"value\":9.0,\"unit\":\"ft\"},\"breadth\":{\"value\":5.5,\"unit\":\"ft\"},\"height\":{\"value\":6.0,\"unit\":\"ft\"}}},{\"type\":\"Ace (Helper + 1 Labour)\",\"eta\":null,\"fare\":{\"currency\":\"INR\",\"minor_amount\":54275},\"capacity\":{\"value\":750.0,\"unit\":\"kg\"},\"size\":{\"length\":{\"value\":7.0,\"unit\":\"ft\"},\"breadth\":{\"value\":4.5,\"unit\":\"ft\"},\"height\":{\"value\":5.5,\"unit\":\"ft\"}}},{\"type\":\"3 Wheeler\",\"eta\":null,\"fare\":{\"currency\":\"INR\",\"minor_amount\":36697},\"capacity\":{\"value\":500.0,\"unit\":\"kg\"},\"size\":{\"length\":{\"value\":6.0,\"unit\":\"ft\"},\"breadth\":{\"value\":5.0,\"unit\":\"ft\"},\"height\":{\"value\":5.0,\"unit\":\"ft\"}}},{\"type\":\"2 Wheeler\",\"eta\":null,\"fare\":{\"currency\":\"INR\",\"minor_amount\":8556},\"capacity\":{\"value\":20.0,\"unit\":\"kg\"},\"size\":{\"length\":{\"value\":9.0,\"unit\":\"ft\"},\"breadth\":{\"value\":5.5,\"unit\":\"ft\"},\"height\":{\"value\":6.0,\"unit\":\"ft\"}}}]}";
        } else {
            try {
                response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                responseBody = response.getBody();
                httpStatus = HttpStatus.resolve(response.getStatusCode().value());
            } catch (HttpClientErrorException e) {
                HttpStatusCode statusCode = e.getStatusCode();
                httpStatus = HttpStatus.resolve(statusCode.value());
            }
        }

        if (httpStatus == null) {
            throw new CustomIllegalArgumentsException(porterErrorMessage);
        }
        JsonObject jsonResponse = new JsonObject();
        if (responseBody != null) {
            jsonResponse = GsonUtils.getGson().fromJson(responseBody, JsonObject.class);
        }
        switch (httpStatus) {
            case OK:
                break;
            case BAD_REQUEST:
                String type = jsonResponse.has("type") ? jsonResponse.get("type").getAsString() : null;
                if (type != null && type.equals("different_city_error")) {
                    throw new CustomIllegalArgumentsException("pickup and drop address belongs to different cities");
                }
                throw new CustomIllegalArgumentsException(porterErrorMessage);
            case UNPROCESSABLE_ENTITY:
                throw new DeliveryNotAvailableException();
//                String restricted_location = jsonResponse.has("type") ? jsonResponse.get("type").getAsString(): "restricted_location";
//                String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : httpStatus.getReasonPhrase();
//                log.error("Exception occurred:: message: {}", message);
//                if (message.equals("restricted drop location")) {
//                }
//                throw new CustomIllegalArgumentsException(message);
            default:
                String type1 = jsonResponse.has("type") ? jsonResponse.get("type").getAsString() : null;
                String message1 = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : null;
                log.error("Exception occurred:: type: {}, message: {}", type1, message1);
                throw new CustomIllegalArgumentsException(porterErrorMessage);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            assert responseBody != null;
            rootNode = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            log.error("Error while processing response from porter:: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        JsonNode vehicles = rootNode.get("vehicles");
        VehicleBuilder vehicleBuilder = Vehicle.builder();
        if (vehicles.isNull() || !vehicles.isArray()) {
            throw new CustomIllegalArgumentsException(porterErrorMessage);
        }
        for (JsonNode vehicle : vehicles) {
            String prettyString = vehicle.toPrettyString();
            log.debug("prettyString:: {}", prettyString);
            String type = vehicle.get("type").asText(null);
            if (type == null || !type.equals("2 Wheeler")) {
                continue;
            }
            JsonNode eta = vehicle.get("eta");
            Vehicle.Eta.EtaBuilder etaBuilder = Vehicle.Eta.builder();
            if (!eta.isNull()) {
                etaBuilder.unit(eta.get("unit").asText()).value(eta.get("value").asLong());
            }
            JsonNode fareNode = vehicle.get("fare");
            FareBuilder fareBuilder = Fare.builder();
            if (!fareNode.isNull()) {
                String currency = fareNode.get("currency").asText();
                long minor_amount = fareNode.get("minor_amount").asLong();
                fareBuilder.currency(currency).minor_amount(minor_amount);
            }
            vehicleBuilder.type(type).eta(etaBuilder.build()).fare(fareBuilder.build());
        }

        Vehicle vehicle = vehicleBuilder.build();
        log.info("Quote fetched successfully: {}", responseBody);
        return GetQuoteResponse.builder().vehicle(vehicle).build();

    }

    // @formatter:off
	private FetchOrderRes parseAndAccessFields(String jsonResponse) {
	    try {
	        JsonNode root = mapper.readTree(jsonResponse);

	        // Safely access basic fields
	        String orderId = root.path("order_id").asText(null);
	        String statusStr = root.path("status").asText(null);

	        // Build PartnerInfo
	        PartnerInfo partnerInfo = buildPartnerInfo(root.path("partner_info"));

	        // Build OrderTimings
	        OrderTimings orderTimings = buildOrderTimings(root.path("order_timings"));

	        // Build FareDetails
	        FareDetails fareDetails = buildFareDetails(root.path("fare_details"));

	        // Convert status to enum
	        Status status = convertStatus(statusStr);

	        // Build and return the FetchOrderRes object
	        return FetchOrderRes.builder()
	                .order_id(orderId)
	                .status(status)
	                .partner_info(partnerInfo)
	                .order_timings(orderTimings)
	                .fare_details(fareDetails)
                    .trackingLink(root.path("tracking_url").asText(null))
	                .build();

	    } catch (Exception e) {
	        log.error("Failed to parse JSON response with message: {}.", e.getMessage(), e);
	        return null;
	    }
	}

	private PartnerInfo buildPartnerInfo(JsonNode partnerInfoNode) {
	    if (partnerInfoNode.isNull()) return null;

	    PartnerInfoBuilder partnerInfoBuilder = PartnerInfo.builder()
	            .name(partnerInfoNode.path("name").asText(null))
	            .vehicle_number(partnerInfoNode.path("vehicle_number").asText(null))
	            .vehicle_type(partnerInfoNode.path("vehicle_type").asText(null));

	    // Mobile
	    JsonNode mobileNode = partnerInfoNode.path("mobile");
	    if (!mobileNode.isNull()) {
	        MobileNo mobile = MobileNo.builder()
	                .country_code(mobileNode.path("country_code").asText(null))
	                .mobile_number(mobileNode.path("mobile_number").asText(null))
	                .build();
	        partnerInfoBuilder.mobile(mobile);
	    }

	    // Partner Secondary Mobile
	    JsonNode secondaryMobileNode = partnerInfoNode.path("partner_secondary_mobile");
	    if (!secondaryMobileNode.isNull()) {
	        MobileNo secondaryMobile = MobileNo.builder()
	                .country_code(secondaryMobileNode.path("country_code").asText(null))
	                .mobile_number(secondaryMobileNode.path("mobile_number").asText(null))
	                .build();
	        partnerInfoBuilder.partner_secondary_mobile(secondaryMobile);
	    }

	    // Location
	    JsonNode locationNode = partnerInfoNode.path("location");
	    if (!locationNode.isNull()) {
	        Location location = Location.builder()
	                .lat(locationNode.path("lat").asText(null))
	                .lng(locationNode.path("long").asText(null))
	                .build();
	        partnerInfoBuilder.location(location);
	    }

	    return partnerInfoBuilder.build();
	}

	private OrderTimings buildOrderTimings(JsonNode orderTimingsNode) {
	    if (orderTimingsNode.isNull()) return null;

	    return OrderTimings.builder()
	            .pickup_time(orderTimingsNode.path("pickup_time").asLong())
	            .order_accepted_time(orderTimingsNode.path("order_accepted_time").asLong())
	            .order_started_time(orderTimingsNode.path("order_started_time").asLong())
	            .order_ended_time(orderTimingsNode.path("order_ended_time").asLong())
	            .build();
	}

	private FareDetails buildFareDetails(JsonNode fareDetailsNode) {
	    if (fareDetailsNode.isNull()) return null;

	    FareDetailsBuilder fareDetailsBuilder = FareDetails.builder();

	    // Estimated Fare Details
	    JsonNode estimatedFareNode = fareDetailsNode.path("estimated_fare_details");
	    if (!estimatedFareNode.isNull()) {
	        FareAmountDetails estimatedFare = FareAmountDetails.builder()
	                .currency(estimatedFareNode.path("currency").asText(null))
	                .minor_amount(estimatedFareNode.path("minor_amount").asLong())
	                .build();
	        fareDetailsBuilder.estimated_fare_details(estimatedFare);
	    }

	    // Actual Fare Details
	    JsonNode actualFareNode = fareDetailsNode.path("actual_fare_details");
	    if (!actualFareNode.isNull()) {
	        FareAmountDetails actualFare = FareAmountDetails.builder()
	                .currency(actualFareNode.path("currency").asText(null))
	                .minor_amount(actualFareNode.path("minor_amount").asLong())
	                .build();
	        fareDetailsBuilder.actual_fare_details(actualFare);
	    }

	    return fareDetailsBuilder.build();
	}

    private Status convertStatus(String statusStr) {
        for (Status status : Status.values()) {
            if (status.toString().equals(statusStr)) {
                return status;
            }
        }
        return null; // or a default status if applicable
    }
    // @formatter:on

    public void updateOrderStatus(Order_Details details, FetchOrderRes fetchOrderRes) {

        MailTemplate mailTemplate = null;
        OrderStatus currentOrderStatus;

        currentOrderStatus = switch (fetchOrderRes.getStatus()) {
            case open -> OrderStatus.READY_FOR_PICK_UP;
            case accepted -> OrderStatus.RIDER_ASSIGNED;
            case cancelled -> {
                internalMailService.sendMailOnError("Order Cancelled - order id: " + details.getId() + "/" + details.getCode() + ", user id: " + details.getUser_id(), "Order Cancelled");
                yield OrderStatus.ORDER_CANCELLED;
            }
            case ended, completed -> OrderStatus.DELIVERED;
            case live -> OrderStatus.OUT_FOR_DELIVERY;
        };


        String invoiceUrl = null;
        if (!details.getStatus().equals(currentOrderStatus)) {
            if (currentOrderStatus.equals(OrderStatus.OUT_FOR_DELIVERY) && enableSms) {
                String code = details.getCode();
                String firstName = StringUtils.hasText(details.getDelivery_address().getFirst_name()) ? details.getDelivery_address().getFirst_name() : "Student";
                String content = firstName + " |" + code + " |" + code;
                smsTraceHelper.runWithTrace(List.of(details.getDelivery_address().getPhone_no()),
                        content,
                        SmsTemplate.ORDER_DISPATCHED,
                        Defaults.AUTO,
                        () -> smsService.sendSMS(List.of(details.getDelivery_address().getPhone_no()), content, SmsTemplate.ORDER_DISPATCHED)
                );
            }

            if (currentOrderStatus.equals(OrderStatus.DELIVERED) && enableSms) {
                String code = details.getCode();
                String firstName = StringUtils.hasText(details.getDelivery_address().getFirst_name()) ? details.getDelivery_address().getFirst_name() : "Student";
                String content = firstName + " |" + code;
                smsTraceHelper.runWithTrace(List.of(details.getDelivery_address().getPhone_no()),
                        content,
                        SmsTemplate.DELIVERED,
                        Defaults.AUTO,
                        () -> smsService.sendSMS(List.of(details.getDelivery_address().getPhone_no()), content, SmsTemplate.DELIVERED)
                );
            }
//            if (currentOrderStatus.equals(OrderStatus.DELIVERY_FAILED) && enableSms) {
            String firstName = StringUtils.hasText(details.getDelivery_address().getFirst_name()) ? details.getDelivery_address().getFirst_name() : "Student";
//                smsTraceHelper.runWithTrace(List.of(details.getDelivery_address().getPhone_no()),
//                        firstName,
//                        SmsTemplate.DELIVERED,
//                        Defaults.AUTO,
//                        () -> smsService.sendSMS(List.of(details.getDelivery_address().getPhone_no()), firstName, SmsTemplate.DELIVERED)
//                );
            // TODO: Delivery Failed
//            }
            details.setFare_details(fetchOrderRes.getFare_details());
            details.setStatus(currentOrderStatus, Defaults.PORTER_STCHK_CRON);


            order_Details_Service.update(details.getId(), details, Defaults.PORTER_STCHK_CRON);
            if (currentOrderStatus == OrderStatus.DELIVERED) {
                try {
                    invoiceUrl = generateInvoiceService.generateInvoice(details);
                } catch (Exception e) {
                    internalMailService.sendMailOnError("Error in generating invoice for order id : " + details.getCode(), "Error in generating invoice for order id : " + details.getCode(), e);
                }
            }

            if (Objects.nonNull(mailTemplate)) {
                sendMailWithOrderDetails(details, mailTemplate, invoiceUrl);
            }
        }


    }

    @Async
    public void sendMailWithOrderDetails(Order_Details details, MailTemplate mailTemplate, String invoiceUrl) {
        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, details.getUser_id()));

        Users user = usersService.repoFindOne(filterU);
        if (user == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
        String userName = user.getFirst_name() + " " + user.getLast_name();
        String orderTemplateTable = orderTemplateHelper.getOrderTemplateTable(details);

        String mailContent = userName + "|" + orderTemplateTable;

        MailBuilder builder = new MailBuilder();
        builder.setTo(user.getEmail_id());
        builder.setContent(mailContent);
        builder.setTemplate(mailTemplate);
        if (invoiceUrl != null) {
            builder.setAttachmentUrls(invoiceUrl);
        }
        emailSenderImpl.sendEmailHtmlTemplate(builder);
    }

    private List<Order_Item> getSecureOrderItems(Order_Details details) {
        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, details.getId()));
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.type, PurchaseType.SECURE.name()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> listOI = order_Item_Service.repoFind(filterOI);
        if (CollectionUtils.isEmpty(listOI)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        return listOI;
    }

    public SEResponse handleWebhookResponse(PorterWebhookBean response) {
        try {
            if (response == null) {
                throw new BadRequestException("Payload is null.");
            }
            if (!StringUtils.hasText(response.getStatus())) {
                throw new BadRequestException("Status is missing.");
            }
            if (!StringUtils.hasText(response.getOrderId())) {
                throw new BadRequestException("Order Id is missing.");
            }
            if (response.getOrderDetails() == null) {
                throw new BadRequestException("Order details is null.");
            }

            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            filterOD.addClause(WhereClause.eq(Order_Details.Fields.dp_order_id, response.getOrderId()));
            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Order_Details details = order_Details_Service.repoFindOne(filterOD);
            if (details == null) {
                throw new BadRequestException("Invalid order id.");
            }
            Status status = switch (response.getStatus()) {
                case "order_accepted" -> Status.accepted;
                case "order_start_trip" -> Status.live;
                case "order_end_job" -> Status.ended;
                case "order_reopen" -> Status.open;
                case "order_cancel" -> Status.cancelled;
                case "completed" -> Status.ended;
                default -> throw new BadRequestException("Unexpected value: " + response.getStatus());
            };

            FareDetails fareDetails = details.getFare_details();
            if (fareDetails.getActual_fare_details() == null && response.getOrderDetails().getActualTripFare() != null) {
                fareDetails.setActual_fare_details(FareAmountDetails.builder().minor_amount(response.getOrderDetails().getActualTripFare()).build());
            }
            if (fareDetails.getEstimated_fare_details() == null && response.getOrderDetails().getEstimatedTripFare() != null) {
                fareDetails.setEstimated_fare_details(FareAmountDetails.builder().minor_amount(response.getOrderDetails().getEstimatedTripFare()).build());
            }

            this.updateOrderStatus(details, FetchOrderRes.builder().status(status).fare_details(fareDetails).build());
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (Exception e) {
            if (e instanceof BadRequestException) {
                throw e;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void cancelOrder(Order_Details order, String cudBy) {
        String url = porterBaseUrl + porterGetOrderEndpoint + order.getDp_order_id().trim() + "/cancel";

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", porterApiKey);

        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        } catch (HttpServerErrorException.InternalServerError ex) {
            log.error("Exception occurred with message: {}", ex.getMessage(), ex);
            String responseBody = ex.getResponseBodyAsString();
            internalMailService.sendMailOnError("Error on cancelling order : " + order.getDp_order_id(), ex.getMessage() + "\n\n" + responseBody, ex);
        }

        if (response == null) {
            internalMailService.sendMailOnError("Error on cancelling order : " + order.getDp_order_id(), "Response is null on cancel");
        }
        assert response != null;
        HttpStatus httpStatus = HttpStatus.resolve(response.getStatusCode().value());

        if (httpStatus == null) {
            throw new CustomIllegalArgumentsException(porterErrorMessage);
        }
        switch (httpStatus) {
            case CREATED, OK:
                break;
            default:
                throw new CustomIllegalArgumentsException("Invalid response code: " + httpStatus);
        }

        order.setStatus(OrderStatus.ORDER_CANCELLED, cudBy);
        order_Details_Service.update(order.getId(), order, cudBy);
    }

    public void updateOrderStatus(Order_Details details) {
        FetchOrderRes fetchOrderRes;
        if (StringUtils.hasText(details.getSecure_dp_order_id())) {
            fetchOrderRes = this.getOrderStatus(details.getSecure_dp_order_id());
            if (!details.getSecure_dp_order_id().equals(fetchOrderRes.getOrder_id())) {
                internalMailService.sendMailOnError("Order id mismatch from porter.", details.getDp_order_id(), new InvalidParameterException("Order id mismatch from porter."));
                throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
            }
        } else {
            fetchOrderRes = this.getOrderStatus(details.getDp_order_id());
            if (!details.getDp_order_id().equals(fetchOrderRes.getOrder_id())) {
                internalMailService.sendMailOnError("Order id mismatch from porter.", details.getDp_order_id(), new InvalidParameterException("Order id mismatch from porter."));
                throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
            }
        }
        this.updateOrderStatus(details, fetchOrderRes);
    }

}
