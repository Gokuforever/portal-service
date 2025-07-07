package com.sorted.portal.service.secure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.BusinessHours;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.porter.req.beans.CreateOrderBean;
import com.sorted.commons.porter.res.beans.CreateOrderResBean;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.InitiateSecureBean;
import com.sorted.portal.request.beans.AppraiseSecureReturn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.sorted.commons.enums.UserType.CUSTOMER;
import static com.sorted.commons.enums.UserType.SELLER;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecureReturnService {

    private final Users_Service usersService;
    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Seller_Service sellerService;
    private final Address_Service addressService;
    private final PorterUtility porterUtility;

    @Value("${se.secure.max-return-days:150}")
    private Integer maxReturnDays;

    /**
     * Initiates a secure return process for the given request
     *
     * @param secureBean The secure return request details
     */
    public void initiateSecureReturn(InitiateSecureBean secureBean) {
        log.info("Initiating secure return process for user: {}", secureBean.getReq_user_id());

        UsersBean user = validateCustomer(secureBean.getReq_user_id());
        validateSecureInitiateRequest(secureBean);
        LocalDate returnDate = parseReturnDate(secureBean.getReturnDate());
        Order_Details order = validateAndGetOrder(secureBean.getOrderId(), user.getId(), OrderStatus.DELIVERED);
        validateOrderItems(secureBean, order);
        Seller seller = validateSellerBusinessHours(order, returnDate);
        Address pickUpAddress = validateAndGetCustomerAddressForSecureReturn(secureBean.getAddressId(), user.getId());
        Address deliveryAddress = validateAndGetSellerAddressForSecureReturn(seller.getAddress_id(), order.getSeller_id());
        updateOrderAndItems(order, secureBean, returnDate, user.getId(), pickUpAddress, deliveryAddress);

        log.info("Successfully scheduled secure return for order ID: {}", order.getId());
    }

    private void validateSecureInitiateRequest(InitiateSecureBean secureBean) {
        Preconditions.check(StringUtils.hasText(secureBean.getOrderId()), ResponseCode.MISSING_ORDER_ID);
        Preconditions.check(StringUtils.hasText(secureBean.getReturnDate()), ResponseCode.MISSING_RETURN_DATE);
        Preconditions.check(CollectionUtils.isNotEmpty(secureBean.getOrderItemIds()), ResponseCode.MISSING_RETURN_ITEMS);
        Preconditions.check(secureBean.getTimeSlot() != null, ResponseCode.MISSING_TIME_SLOT);
        Preconditions.check(StringUtils.hasText(secureBean.getAddressId()), ResponseCode.MISSING_PICKUP_ADD);
    }

    private LocalDate parseReturnDate(String returnDateStr) {
        try {
            LocalDate returnDate = LocalDate.parse(returnDateStr);
            log.debug("Parsed return date: {}", returnDate);
            Preconditions.check(returnDate.isAfter(LocalDate.now()), ResponseCode.INVALID_RETURN_DATE);
            return returnDate;
        } catch (Exception e) {
            log.error("Invalid return date format: {}", returnDateStr, e);
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_RETURN_DATE);
        }
    }

    private UsersBean validateCustomer(String userId) {
        log.debug("Validating user for secure return activity. User ID: {}", userId);
        UsersBean user = usersService.validateUserForActivity(userId, Activity.SECURE_RETURN);
        Preconditions.check(user.getRole().getUser_type() == CUSTOMER, ResponseCode.ACCESS_DENIED);
        log.debug("User validation successful. User role: {}", user.getRole().getUser_type());
        return user;
    }

    private Order_Details validateAndGetOrder(String orderId, String userId, OrderStatus orderStatus) {
        log.debug("Fetching order details for order ID: {}", orderId);
        Order_Details order = orderDetailsService.findById(orderId)
                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND));
        Preconditions.check(order.getUser_id().equals(userId), ResponseCode.ORDER_NOT_FOUND);
        log.debug("Order found with status: {}", order.getStatus());
        Preconditions.check(order.getStatus() == orderStatus,
                ResponseCode.INVALID_STATUS_FOR_SECURE_RETURN);

        LocalDate orderDate = order.getCreation_date().toLocalDate();
        LocalDate maxAllowedReturnDate = orderDate.plusDays(maxReturnDays);
        LocalDate returnDate = LocalDate.now();

        if (returnDate.isAfter(maxAllowedReturnDate)) {
            String errorMessage = String.format("Return date cannot be more than %d days from order date.", maxReturnDays);
            log.error(errorMessage);
            throw new CustomIllegalArgumentsException(errorMessage);
        }

        return order;
    }

    private Address validateAndGetCustomerAddressForSecureReturn(String pickUpAddressId, String userId) {
        Address pickUpAddress = addressService.findById(pickUpAddressId)
                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND));
        Preconditions.check(pickUpAddress.getEntity_id().equals(userId), ResponseCode.ADDRESS_NOT_FOUND);
        Preconditions.check(pickUpAddress.getUser_type().equals(CUSTOMER), ResponseCode.ADDRESS_NOT_FOUND);
        return pickUpAddress;
    }

    private Address validateAndGetSellerAddressForSecureReturn(String deliveryAddressId, String sellerId) {
        Address deliveryAddress = addressService.findById(deliveryAddressId)
                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND));
        Preconditions.check(deliveryAddress.getEntity_id().equals(sellerId), ResponseCode.ADDRESS_NOT_FOUND);
        Preconditions.check(deliveryAddress.getUser_type().equals(UserType.SELLER), ResponseCode.ADDRESS_NOT_FOUND);
        return deliveryAddress;
    }

    private void validateOrderItems(InitiateSecureBean secureBean, Order_Details order) {
        Set<String> orderItemIds = new HashSet<>(secureBean.getOrderItemIds());
        orderItemIds.remove(null);
        Preconditions.check(CollectionUtils.isNotEmpty(orderItemIds), ResponseCode.MISSING_RETURN_ITEMS);

        List<Order_Item> orderItems = findOrderItems(order.getId(), orderItemIds);
        log.debug("Found {} order items for return processing", orderItems.size());

        Preconditions.check(orderItemIds.size() == orderItems.size(), ResponseCode.INVALID_RETURN_ITEMS);

        boolean directPurchasedItem = orderItems.stream()
                .anyMatch(item -> item.getType() == PurchaseType.BUY);
        Preconditions.check(!directPurchasedItem, ResponseCode.NOT_SECURED_ITEM);

        boolean invalidItemStatus = orderItems.stream()
                .anyMatch(item -> item.getStatus() != OrderStatus.DELIVERED);
        Preconditions.check(!invalidItemStatus, ResponseCode.INVALID_ITEM_STATUS_FOR_SECURE_RETURN);
    }

    private List<Order_Item> findOrderItems(String orderId, Set<String> orderItemIds) {
        AggregationFilter.SEFilter filterOI = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterOI.addClause(AggregationFilter.WhereClause.eq(Order_Item.Fields.order_id, orderId));
        filterOI.addClause(AggregationFilter.WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(orderItemIds)));
        filterOI.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
        if (CollectionUtils.isEmpty(orderItems)) {
            log.error("No order items found for order ID: {} with item IDs: {}", orderId, orderItemIds);
            throw new CustomIllegalArgumentsException(ResponseCode.ITEM_NOT_FOUND);
        }
        return orderItems;
    }

    private Seller validateSellerBusinessHours(Order_Details order, LocalDate returnDate) {
        Seller seller = sellerService.findById(order.getSeller_id())
                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND_FOR_SECURE_RETURN));

        BusinessHours businessHours = seller.getBusiness_hours();
        if (businessHours != null && CollectionUtils.isNotEmpty(businessHours.getFixed_off_days())) {
            DayOfWeek dayOfWeek = returnDate.getDayOfWeek();
            for (WeekDay day : businessHours.getFixed_off_days()) {
                if (day.name().equals(dayOfWeek.name())) {
                    throw new CustomIllegalArgumentsException(ResponseCode.NOT_OPERATIONAL_FOR_SECURE_RETURN);
                }
            }
        }
        return seller;
    }

    private void updateOrderAndItems(Order_Details order, InitiateSecureBean secureBean,
                                     LocalDate returnDate, String userId, Address pickUpAddress, Address deliveryAddress) {

        log.info("Updating order and items to SECURE_RETURN_SCHEDULED status. Order ID: {}, User ID: {}",
                order.getId(), userId);

        order.setSecured_time_slot(secureBean.getTimeSlot());
        order.setSecured_date(returnDate);
        order.setSecure_pickup_address(createAddressDTOFromAddress(pickUpAddress));
        order.setSecure_delivery_address(createAddressDTOFromAddress(deliveryAddress));
        order.setStatus(OrderStatus.SECURE_RETURN_SCHEDULED, userId);

        // Update order items
        AggregationFilter.SEFilter filterOI = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterOI.addClause(AggregationFilter.WhereClause.eq(Order_Item.Fields.order_id, order.getId()));
        filterOI.addClause(AggregationFilter.WhereClause.in(BaseMongoEntity.Fields.id, secureBean.getOrderItemIds()));

        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
        orderItems.forEach(item -> item.setStatus(OrderStatus.SECURE_RETURN_SCHEDULED, userId));

        // Save updates
        orderDetailsService.update(order.getId(), order, userId);
        orderItems.forEach(item -> orderItemService.update(item.getId(), item, userId));
    }

    private AddressDTO createAddressDTOFromAddress(Address address) {
        AddressDTO addressDTO = new AddressDTO();
        if (StringUtils.hasText(address.getStreet_1())) {
            addressDTO.setStreet_1(address.getStreet_1());
        }
        if (StringUtils.hasText(address.getStreet_2())) {
            addressDTO.setStreet_2(address.getStreet_2());
        }
        if (StringUtils.hasText(address.getLandmark())) {
            addressDTO.setLandmark(address.getLandmark());
        }
        if (StringUtils.hasText(address.getCity())) {
            addressDTO.setCity(address.getCity());
        }
        if (StringUtils.hasText(address.getState())) {
            addressDTO.setState(address.getState());
        }
        if (StringUtils.hasText(address.getPincode())) {
            addressDTO.setPincode(address.getPincode());
        }
        if (address.getAddress_type() != null) {
            addressDTO.setAddress_type(address.getAddress_type().name());
        }
        if (StringUtils.hasText(address.getAddress_type_desc())) {
            addressDTO.setAddress_type_desc(address.getAddress_type_desc());
        }
        addressDTO.setLat(address.getLat());
        addressDTO.setLng(address.getLng());

        return addressDTO;
    }

    public void process(Order_Details order, List<Order_Item> items, Seller seller, Users user) throws JsonProcessingException {
        if (CollectionUtils.isEmpty(items) || seller == null || user == null) {
            String reason = String.format("OrderItems: %s, Seller: %s, Users: %s",
                    CollectionUtils.isEmpty(items), seller == null, user == null);
            markFailure(order, items, reason);
            return;
        }

        String secureOrderId = generateSecureOrderId();
        order.setSecure_order_id(secureOrderId);
        orderDetailsService.update(order.getId(), order, Defaults.INITIATE_SECURE_RETURN_CRON);

        CreateOrderBean createOrderRequest = buildCreateOrderRequest(order, items, seller, user, secureOrderId);
        CreateOrderResBean response = porterUtility.createOrder(createOrderRequest);

        order.setSecure_dp_order_id(response.getOrder_id());
        order.setStatus(OrderStatus.SECURE_RETURN_INITIATED, Defaults.INITIATE_SECURE_RETURN_CRON);
        orderDetailsService.update(order.getId(), order, Defaults.INITIATE_SECURE_RETURN_CRON);
    }

    private void markFailure(Order_Details order, List<Order_Item> items, String reason) {
        order.setSecure_return_failure_reason(reason);
        order.setStatus(OrderStatus.SECURE_RETURN_FAILED, Defaults.INITIATE_SECURE_RETURN_CRON);
        orderDetailsService.update(order.getId(), order, Defaults.INITIATE_SECURE_RETURN_CRON);

        if (items != null) {
            for (Order_Item item : items) {
                item.setStatus(OrderStatus.SECURE_RETURN_FAILED, Defaults.INITIATE_SECURE_RETURN_CRON);
                orderItemService.update(item.getId(), item, Defaults.INITIATE_SECURE_RETURN_CRON);
            }
        }
    }

    private String generateSecureOrderId() {
        return "SEC-ORD-" + LocalDate.now().getMonth() + Year.now() + CommonUtils.getNanoseconds();
    }

    private CreateOrderBean buildCreateOrderRequest(Order_Details order, List<Order_Item> items, Seller seller, Users user, String orderId) {
        int count = items.size();
        String message = "Please verify no of items: " + count + ".";

        CreateOrderBean.Delivery_Instructions instruction = CreateOrderBean.Delivery_Instructions.builder()
                .type("text").description(message).build();
        CreateOrderBean.Instruction_List instructionList = CreateOrderBean.Instruction_List.builder()
                .instructions_list(List.of(instruction)).build();

        Spoc_Details spoc = seller.getSpoc_details().stream()
                .filter(Spoc_Details::isPrimary).findFirst()
                .orElseThrow(() -> new RuntimeException("Missing primary SPOC"));

        return CreateOrderBean.builder()
                .request_id(orderId)
                .delivery_instructions(instructionList)
                .pickup_details(CreateOrderBean.Pickup_Details.builder()
                        .address(buildAddress(order.getSecure_pickup_address(), user.getFirst_name() + " " + user.getLast_name(), user.getMobile_no()))
                        .build())
                .drop_details(CreateOrderBean.Drop_Details.builder()
                        .address(buildAddress(order.getSecure_delivery_address(), spoc.getFirst_name() + " " + spoc.getLast_name(), spoc.getMobile_no()))
                        .build())
                .build();
    }

    private CreateOrderBean.Address buildAddress(AddressDTO addressDTO, String contactName, String contactPhone) {
        return CreateOrderBean.Address.builder()
                .street_address1(addressDTO.getStreet_1())
                .street_address2(addressDTO.getStreet_2())
                .landmark(addressDTO.getLandmark())
                .city(addressDTO.getCity())
                .state(addressDTO.getState())
                .pincode(addressDTO.getPincode())
                .country("India")
                .lat(addressDTO.getLat())
                .lng(addressDTO.getLng())
                .contact_details(CreateOrderBean.Contact_Details.builder()
                        .name(contactName).phone_number("+91" + contactPhone).build())
                .build();
    }

    public void appraiseSecureReturn(AppraiseSecureReturn acceptReject) {
        UsersBean user = validateSeller(acceptReject.getReq_user_id());
        validateAppraiseSecureRequest(acceptReject);
        validateAndGetOrder(acceptReject.getOrderId(), user.getId(), OrderStatus.SECURE_RETURN_COMPLETED);
    }

    private UsersBean validateSeller(String userId) {
        log.debug("Validating user for secure return activity. User ID: {}", userId);
        UsersBean user = usersService.validateUserForActivity(userId, Activity.APPRAISE_SECURE_RETURN);
        Preconditions.check(user.getRole().getUser_type() == SELLER, ResponseCode.ACCESS_DENIED);
        log.debug("User validation successful. User role: {}", user.getRole().getUser_type());
        return user;
    }

    private void validateAppraiseSecureRequest(AppraiseSecureReturn acceptReject) {
        Preconditions.check(StringUtils.hasText(acceptReject.getOrderId()), ResponseCode.MISSING_ORDER_ID);
//        Preconditions.check(acceptReject.getAmount() != null, ResponseCode.MISSING_AMOUNT);
        Preconditions.check(acceptReject.getAmount().compareTo(BigDecimal.ZERO) > 0, ResponseCode.INVALID_AMOUNT);
    }


}



