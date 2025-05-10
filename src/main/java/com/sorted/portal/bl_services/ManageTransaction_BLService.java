package com.sorted.portal.bl_services;

import com.phonepe.sdk.pg.common.models.PgV2InstrumentType;
import com.phonepe.sdk.pg.common.models.response.OrderStatusResponse;
import com.phonepe.sdk.pg.common.models.response.PaymentDetail;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Item;
import com.sorted.commons.beans.Order_Status_History;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.req.beans.GetQuoteRequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GsonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.razorpay.RazorpayUtility;
import com.sorted.portal.request.beans.PayNowBean;
import com.sorted.portal.response.beans.*;
import com.sorted.portal.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.NotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageTransaction_BLService {

    private final Users_Service users_Service;
    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Address_Service address_Service;
    private final Order_Details_Service order_Details_Service;
    private final Order_Item_Service order_Item_Service;
    private final RazorpayUtility razorpayUtility;
    private final Seller_Service seller_Service;
    private final PorterUtility porterUtility;
    private final Order_Dump_Service orderDumpService;
    private final OrderService orderService;
    private final PhonePeUtility phonePeUtility;
    private final File_Upload_Details_Service file_Upload_Details_Service;

    @PostMapping("/test/pay")
    public StandardCheckoutPayResponse testPay() {
        return phonePeUtility.createOrder(UUID.randomUUID().toString(), 100)
                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.PG_ORDER_GEN_FAILED));
    }

    @GetMapping("/status")
    public FindOneOrder status(@RequestParam("orderId") String orderId) {
        if (!StringUtils.hasText(orderId)) {
            log.error("status:: Order ID is empty or null");
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
        }

        log.info("status:: Checking status for order ID: {}", orderId);

        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, orderId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details order_Details = order_Details_Service.repoFindOne(filterOD);
        if (order_Details == null) {
            log.error("status:: Order not found with ID: {}", orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        try {
            // Only check payment status if not already completed
            if (!(StringUtils.hasText(order_Details.getPayment_status()) && order_Details.getPayment_status().equals("COMPLETED"))) {
                log.info("status:: Payment not completed, checking with PhonePe for order ID: {}", orderId);
                Optional<OrderStatusResponse> orderStatusResponseOptional = phonePeUtility.checkStatus(orderId);

                if (orderStatusResponseOptional.isEmpty()) {
                    log.error("status:: Empty response from PhonePe for order ID: {}", orderId);
                    throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
                }

                OrderStatusResponse orderStatusResponse = orderStatusResponseOptional.get();
                if (CollectionUtils.isEmpty(orderStatusResponse.getPaymentDetails())) {
                    log.error("status:: Invalid response from PhonePe payment status check for order ID: {}", orderId);
                    throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
                }

                List<PaymentDetail> paymentDetails = orderStatusResponse.getPaymentDetails();
                PaymentDetail paymentDetail = paymentDetails.get(paymentDetails.size() - 1);
                String state = paymentDetail.getState();
                PgV2InstrumentType paymentMode = paymentDetail.getPaymentMode();
                String transactionId = paymentDetail.getTransactionId();

                log.info("status:: Payment details - state: {}, mode: {}, transactionId: {}",
                        state, paymentMode, transactionId);

                order_Details.setPayment_status(state);
                order_Details.setPayment_mode(paymentMode != null ? paymentMode.name() : null);
                order_Details.setTransaction_id(transactionId);

                // Update order status only if payment is successful
                OrderStatus status = switch (state) {
                    case "COMPLETED" -> OrderStatus.TRANSACTION_PROCESSED;
                    case "FAILED" -> OrderStatus.TRANSACTION_FAILED;
                    default -> OrderStatus.TRANSACTION_PENDING;
                };
                order_Details.setStatus(status, Defaults.SYSTEM_ADMIN);
                log.info("status:: Order status updated to TRANSACTION_PROCESSED for order ID: {}", orderId);

                order_Details_Service.update(order_Details.getId(), order_Details, Defaults.SYSTEM_ADMIN);
            }

            List<OrderItemResponse> orderItemResponseList = new ArrayList<>();

            SEFilter filterOI = new SEFilter(SEFilterType.AND);
            filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, order_Details.getId()));
            filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Order_Item> orderItems = order_Item_Service.repoFind(filterOI);
            if (!CollectionUtils.isEmpty(orderItems)) {
                List<String> productIds = orderItems.stream()
                        .map(Order_Item::getProduct_id)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();

                if (!CollectionUtils.isEmpty(productIds)) {
                    SEFilter filterP = new SEFilter(SEFilterType.AND);
                    filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
                    filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                    List<Products> products = productService.repoFind(filterP);
                    Map<String, Products> mapP = !CollectionUtils.isEmpty(products) ?
                            products.stream().collect(Collectors.toMap(Products::getId, product -> product, (p1, p2) -> p1)) :
                            new HashMap<>();

                    Map<String, String> mapFUD = new HashMap<>();

                    // Only search for documents if we have products
                    if (!mapP.isEmpty()) {
                        SEFilter filterFUD = new SEFilter(SEFilterType.AND);
                        filterFUD.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(mapP.keySet())));
                        filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                        List<File_Upload_Details> listFUD = file_Upload_Details_Service.repoFind(filterFUD);
                        if (!CollectionUtils.isEmpty(listFUD)) {
                            listFUD.forEach(fud -> {
                                if (StringUtils.hasText(fud.getId()) && StringUtils.hasText(fud.getDocument_id())) {
                                    mapFUD.putIfAbsent(fud.getId(), fud.getDocument_id());
                                }
                            });
                        }
                    }

                    for (Order_Item item : orderItems) {
                        OrderItemResponse response = convertToResponse(mapP, item, mapFUD);
                        if (response != null) {
                            orderItemResponseList.add(response);
                        }
                    }
                }
            }
            AddressDTO deliveryAddress = order_Details.getDelivery_address();
            AddressResponse addressResponse = AddressResponse.builder().addressType(deliveryAddress.getAddress_type())
                    .city(deliveryAddress.getCity())
                    .pincode(deliveryAddress.getPincode())
                    .state(deliveryAddress.getState())
                    .street_1(deliveryAddress.getStreet_1())
                    .street_2(deliveryAddress.getStreet_2())
                    .landmark(deliveryAddress.getLandmark())
                    .addressTypeDesc(deliveryAddress.getAddress_type_desc())
                    .phone_no(deliveryAddress.getPhone_no())
                    .build();

            log.info("status:: Successfully retrieved status for order ID: {}, with {} items",
                    orderId, orderItemResponseList.size());

            // Build the complete response with code field included
            return FindOneOrder.builder()
                    .id(orderId)
                    .code(order_Details.getCode())
                    .paymentStatus(order_Details.getPayment_status())
                    .paymentMode(order_Details.getPayment_mode())
                    .totalAmount(order_Details.getTotal_amount())
                    .status(order_Details.getStatus() != null ? order_Details.getStatus().getCustomer_status() : null)
                    .transactionId(order_Details.getTransaction_id())
                    .orderItems(orderItemResponseList)
                    .deliveryAddress(addressResponse)
                    .build();
        } catch (NotFoundException e) {
            log.error("status:: Order not found: {}", e.getMessage());
            throw e;
        } catch (CustomIllegalArgumentsException e) {
            log.error("status:: Custom exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("status:: Unexpected error occurred for order ID {}: {}", orderId, e.getMessage(), e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private OrderItemResponse convertToResponse(Map<String, Products> mapP, Order_Item orderItem, Map<String, String> mapFUD) {
        if (orderItem == null || !StringUtils.hasText(orderItem.getProduct_id())) {
            log.warn("convertToResponse:: Invalid order item or missing product ID");
            return null;
        }

        Products product = mapP.getOrDefault(orderItem.getProduct_id(), null);
        String img = mapFUD.getOrDefault(orderItem.getProduct_id(), null);
        String productName = (product == null) ? "" : product.getName();

        return OrderItemResponse.builder()
                .productCode(orderItem.getProduct_code())
                .productId(orderItem.getProduct_id())
                .productName(productName)
                .documentId(img)
                .purchaseType(orderItem.getType() != null ? orderItem.getType().name() : "")
                .quantity(orderItem.getQuantity())
                .sellingPrice(orderItem.getSelling_price())
                .totalCost(orderItem.getTotal_cost())
                .build();
    }


    @PostMapping("/pay")
    public PayNowResponse pay(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        Order_Dump orderDump = new Order_Dump(GsonUtils.getGson().toJson(request.getRequestData()));
        orderDumpService.create(orderDump, this.getClass().getSimpleName());
        try {
            log.info("/pay:: API started!");
            PayNowBean req = request.getGenericRequestDataObject(PayNowBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PURCHASE);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER:
                    break;
                case GUEST:
                    throw new CustomIllegalArgumentsException(ResponseCode.PROMT_SIGNUP);
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            if (!StringUtils.hasText(req.getDelivery_address_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_DELIVERY_ADD);
            }

            SEFilter filterA = new SEFilter(SEFilterType.AND);
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getDelivery_address_id()));
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
            filterA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.CUSTOMER.name()));

            Address address = address_Service.repoFindOne(filterA);
            if (address == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
            }

            SEFilter filterC = new SEFilter(SEFilterType.AND);
            filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
            filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Cart cart = cart_Service.repoFindOne(filterC);
            if (cart == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            List<Item> cart_items = cart.getCart_items();
            if (CollectionUtils.isEmpty(cart_items)) {
                throw new CustomIllegalArgumentsException(ResponseCode.CART_EMPTY);
            }
            LocalDateTime return_date = null;
            boolean has_secure = cart_items.stream().anyMatch(Item::is_secure);
            if (has_secure) {
                if (!StringUtils.hasText(req.getReturn_date())) {
                    throw new CustomIllegalArgumentsException(ResponseCode.MISSING_RETURN_DATE);
                }
                try {
                    return_date = LocalDate.parse(req.getReturn_date()).atTime(LocalTime.MAX);
                } catch (Exception e) {
                    log.error("pay:: Exception occurred:: {}", e.getMessage());
                    throw new CustomIllegalArgumentsException(ResponseCode.INVALID_RETURN_DATE);
                }
            }
            Set<String> product_ids = cart_items.stream().map(Item::getProduct_id).collect(Collectors.toSet());

            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(product_ids)));
            filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Products> listP = productService.repoFind(filterP);
            if (CollectionUtils.isEmpty(listP)) {
                throw new CustomIllegalArgumentsException(ResponseCode.OUT_OF_STOCK);
            }
            List<String> seller_ids = listP.stream().map(Products::getSeller_id).distinct().toList();
            if (seller_ids.isEmpty()) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_FOR_ORDER);
//                throw new CustomIllegalArgumentsException(ResponseCode.MULTIPLE_SELLERS);
            }
            String seller_id = seller_ids.get(0);
            SEFilter filterS = new SEFilter(SEFilterType.AND);
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, seller_id));
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Seller seller = seller_Service.repoFindOne(filterS);
            if (seller == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            SEFilter filterSA = new SEFilter(SEFilterType.AND);
            filterSA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterSA.addClause(WhereClause.eq(Address.Fields.entity_id, seller.getId()));
            filterSA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.SELLER.name()));

            Address sellerAddress = address_Service.repoFindOne(filterSA);
            if (sellerAddress == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
            }

            Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e));

            List<Order_Item> listOI = new ArrayList<>();

            for (Item item : cart_items) {
                if (!mapP.containsKey(item.getProduct_id())) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DELETED_PRODUCT);
                }
                Products product = mapP.get(item.getProduct_id());
                if (product == null) {
                    continue;
                }
                if (product.getQuantity().compareTo(item.getQuantity()) < 0) {
                    throw new CustomIllegalArgumentsException(ResponseCode.FEW_OUT_OF_STOCK);
                }

                Order_Item order_Item = getOrderItem(item, product, return_date);
                listOI.add(order_Item);
            }
            Map<String, Long> mapPQ = listOI.stream()
                    .collect(Collectors.toMap(Order_Item::getProduct_id, Order_Item::getQuantity));
            long totalSum = listOI.stream().mapToLong(Order_Item::getTotal_cost).sum();
            if (totalSum < 1) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_AMOUNT);
            }

            // @formatter:off
						GetQuoteRequest quoteRequest = GetQuoteRequest.builder()
				                .pickup_details(GetQuoteRequest.PickupDetails.builder()
				                        .lat(sellerAddress.getLat().doubleValue())
				                        .lng(sellerAddress.getLng().doubleValue())
				                        .build())
				                .drop_details(GetQuoteRequest.DropDetails.builder()
				                        .lat(address.getLat().doubleValue())
				                        .lng(address.getLng().doubleValue())
				                        .build())
				                .customer(GetQuoteRequest.Customer.builder()
				                        .name(usersBean.getFirst_name() + " " + usersBean.getLast_name())
				                        .mobile(GetQuoteRequest.Customer.Mobile.builder()
				                                .country_code("+91")
				                                .number(usersBean.getMobile_no())
				                                .build())
				                        .build())
				                .build();
						// @formatter:on
//            GetQuoteResponse quote = porterUtility.getQuote(quoteRequest, usersBean.getId());

            Order_Details order = new Order_Details();

            if (StringUtils.hasText(usersBean.getId())) {
                order.setUser_id(usersBean.getId());
            }
            order.setSeller_id(seller_id);
            order.setTotal_amount(totalSum);
            order.setStatus(OrderStatus.ORDER_PLACED, usersBean.getId());
            order.setStatus_id(OrderStatus.ORDER_PLACED.getId());
            Order_Status_History order_history = Order_Status_History.builder().status(OrderStatus.ORDER_PLACED)
                    .modification_date(LocalDateTime.now()).modified_by(usersBean.getId()).build();
            order.setOrder_status_history(Collections.singletonList(order_history));

            //@formatter:off
			AddressDTO del_address =  new AddressDTO();
			if (StringUtils.hasText(address.getStreet_1())){del_address.setStreet_1(address.getStreet_1());}
			if (StringUtils.hasText(address.getStreet_2())){del_address.setStreet_2(address.getStreet_2());}
			if (StringUtils.hasText(address.getLandmark())){del_address.setLandmark(address.getLandmark());}
			if (StringUtils.hasText(address.getCity())){del_address.setCity(address.getCity());}
			if (StringUtils.hasText(address.getState())){del_address.setState(address.getState());}
			if (StringUtils.hasText(address.getPincode())){del_address.setPincode(address.getPincode());}
			if (address.getAddress_type()!=null){del_address.setAddress_type(address.getAddress_type().name());}
			del_address.setLat(address.getLat());
			del_address.setLng(address.getLng());
			if (StringUtils.hasText(address.getAddress_type_desc())){del_address.setAddress_type_desc(address.getAddress_type_desc());}
			
			AddressDTO pickup_address =  new AddressDTO();
			if (StringUtils.hasText(sellerAddress.getStreet_1())){pickup_address.setStreet_1(sellerAddress.getStreet_1());}
			if (StringUtils.hasText(sellerAddress.getStreet_2())){pickup_address.setStreet_2(sellerAddress.getStreet_2());}
			if (StringUtils.hasText(sellerAddress.getLandmark())){pickup_address.setLandmark(sellerAddress.getLandmark());}
			if (StringUtils.hasText(sellerAddress.getCity())){pickup_address.setCity(sellerAddress.getCity());}
			if (StringUtils.hasText(sellerAddress.getState())){pickup_address.setState(sellerAddress.getState());}
			if (StringUtils.hasText(sellerAddress.getPincode())){pickup_address.setPincode(sellerAddress.getPincode());}
			if (sellerAddress.getAddress_type()!=null){pickup_address.setAddress_type(sellerAddress.getAddress_type().name());}
			if (StringUtils.hasText(sellerAddress.getAddress_type_desc())){pickup_address.setAddress_type_desc(sellerAddress.getAddress_type_desc());}
			pickup_address.setLat(sellerAddress.getLat());
			pickup_address.setLng(sellerAddress.getLng());
			order.setPickup_address(pickup_address);
			order.setDelivery_address(del_address);
//            order.setEstimated_quote(GsonUtils.getGson().toJson(quote));
			// @formatter:on

            orderService.reduceProductQuantity(mapP.values().stream().toList(), mapPQ);

//			cart.setCart_items(null);
//			cart_Service.update(cart.getId(), cart, usersBean.getId());

            Order_Details order_Details = order_Details_Service.create(order, usersBean.getId());

            orderService.createOrderItems(listOI, order_Details.getId(), order_Details.getCode(), usersBean.getId());

            Optional<StandardCheckoutPayResponse> checkoutPayResponseOptional = phonePeUtility.createOrder(order_Details.getId(), totalSum);
            
            if (checkoutPayResponseOptional.isEmpty()) {
                log.error("pay-now:: Failed to create PhonePe order for order ID: {}", order_Details.getId());
                throw new CustomIllegalArgumentsException(ResponseCode.PG_ORDER_GEN_FAILED);
            }
            
            StandardCheckoutPayResponse checkoutPayResponse = checkoutPayResponseOptional.get();
            String pgOrderId = checkoutPayResponse.getOrderId();
            order_Details.setPg_order_id(pgOrderId);
            order_Details_Service.update(order_Details.getId(), order_Details, usersBean.getId());

//            CheckoutReqbean checkoutPayload = razorpayUtility.createCheckoutPayload(rzrp_order);
            String redirectUrl = checkoutPayResponse.getRedirectUrl();
            String orderId = order_Details.getId();

            PayNowResponse payNowResponse = PayNowResponse.builder().redirectUrl(redirectUrl).orderId(orderId).build();
            orderDumpService.markSuccess(orderDump, order_Details.getId(), this.getClass().getSimpleName());
            return payNowResponse;
        } catch (CustomIllegalArgumentsException ex) {
            orderDumpService.markFailed(orderDump, ex.getResponseCode().getErrorMessage(), ex.getResponseCode().getCode(), this.getClass().getSimpleName());
            throw ex;
        } catch (Exception e) {
            log.error("/pay:: exception occurred");
            log.error("/pay:: {}", e.getMessage());
            orderDumpService.markFailed(orderDump, e.getMessage(), ResponseCode.ERR_0001.getCode(), this.getClass().getSimpleName());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }


    @NotNull
    private static Order_Item getOrderItem(Item item, Products product, LocalDateTime return_date) {
        Order_Item order_Item = new Order_Item();
        order_Item.setProduct_id(product.getId());
        order_Item.setProduct_code(product.getProduct_code());
        order_Item.setQuantity(item.getQuantity());
        order_Item.setSelling_price(product.getSelling_price());
        order_Item.setSeller_id(product.getSeller_id());
        order_Item.setSeller_code(product.getSeller_code());
        order_Item.setStatus(OrderStatus.ORDER_PLACED);
        order_Item.setStatus_id(OrderStatus.ORDER_PLACED.getId());
        order_Item.setTotal_cost(product.getSelling_price() * item.getQuantity());
        if (item.is_secure()) {
            order_Item.setType(PurchaseType.SECURE);
            order_Item.setReturn_date(return_date);
        } else {
            order_Item.setType(PurchaseType.BUY);
        }
        return order_Item;
    }

    @PostMapping("/saveResponse")
    public SEResponse saveResponse(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/saveResponse:: API started!");
            PGResponseBean req = request.getGenericRequestDataObject(PGResponseBean.class);
//			if (!StringUtils.hasText(req.getOrder_id())) {
//				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
//			}
            if (!StringUtils.hasText(req.getRazorpay_order_id()) || !StringUtils.hasText(req.getRazorpay_payment_id())
                    || !StringUtils.hasText(req.getRazorpay_signature())) {
                throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
            }
            SEFilter filterO = new SEFilter(SEFilterType.AND);
            filterO.addClause(WhereClause.eq(Order_Details.Fields.pg_order_id, req.getRazorpay_order_id()));
            filterO.addClause(WhereClause.eq(Order_Details.Fields.status, OrderStatus.ORDER_PLACED.name()));
            filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            Order_Details order_details = order_Details_Service.repoFindOne(filterO);
            if (order_details == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            filterOD.addClause(WhereClause.eq(Order_Item.Fields.order_id, order_details.getId()));
            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            List<Order_Item> items = order_Item_Service.repoFind(filterOD);
            if (CollectionUtils.isEmpty(items)) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            List<String> product_ids = items.stream().map(Order_Item::getProduct_id).toList();

            OrderStatus orderStatus;
            if (!StringUtils.hasText(req.getRazorpay_payment_id())) {
                orderStatus = OrderStatus.TRANSACTION_FAILED;
                // TODO: should we increase the product quantity
            } else {
                boolean verified = razorpayUtility.verifySignature(req.getRazorpay_order_id(),
                        req.getRazorpay_payment_id(), req.getRazorpay_signature());
                if (!verified) {
                    throw new CustomIllegalArgumentsException(ResponseCode.UNTRUSTED_RESPONSE);
                }

                SEFilter filterC = new SEFilter(SEFilterType.AND);
                filterC.addClause(WhereClause.eq(Cart.Fields.user_id, order_details.getUser_id()));
                filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                Cart cart = cart_Service.repoFindOne(filterC);
                if (cart == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
                }

                List<Item> collect = cart.getCart_items().stream().filter(e -> !product_ids.contains(e.getProduct_id()))
                        .toList();
                if (!CollectionUtils.isEmpty(items)) {
                    cart.setCart_items(collect);
                } else {
                    cart.setCart_items(null);
                }
                cart_Service.update(cart.getId(), cart, Defaults.SYSTEM_ADMIN);

                order_details.setTransaction_id(req.getRazorpay_payment_id());
                orderStatus = OrderStatus.TRANSACTION_PROCESSED;
            }

            final OrderStatus finalOrderStatus = orderStatus;

            items.forEach(e -> {
                e.setStatus(finalOrderStatus, Defaults.SYSTEM_ADMIN);
                order_Item_Service.update(e.getId(), e, Defaults.SYSTEM_ADMIN);
            });
            order_details.setStatus(finalOrderStatus, Defaults.SYSTEM_ADMIN);
            order_Details_Service.update(order_details.getId(), order_details, Defaults.SYSTEM_ADMIN);

            log.info("/saveResponse:: API ended!");
            return SEResponse.getEmptySuccessResponse(ResponseCode.ORDER_PLACED);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/saveResponse:: exception occurred");
            log.error("/saveResponse:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
