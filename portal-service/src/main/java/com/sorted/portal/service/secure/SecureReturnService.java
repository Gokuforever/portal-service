package com.sorted.portal.service.secure;

import com.sorted.common.beans.*;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.*;
import com.sorted.common.enums.*;
import com.sorted.common.exceptions.AccessDeniedException;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.exceptions.DeliveryNotAvailableException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.porter.req.beans.CreateOrderBean;
import com.sorted.common.porter.req.beans.GetQuoteRequest;
import com.sorted.common.porter.res.beans.CreateOrderResBean;
import com.sorted.common.porter.res.beans.FetchOrderRes;
import com.sorted.common.porter.res.beans.GetQuoteResponse;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.PorterUtility;
import com.sorted.common.utils.Preconditions;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.request.beans.AppraiseSecureReturn;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.InitiateSecureBean;
import com.sorted.portal.request.beans.SecureItemAppraisalDetails;
import com.sorted.portal.response.beans.SecureOrderDetailsBean;
import com.sorted.portal.response.beans.SecureOrderItemDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sorted.common.enums.UserType.CUSTOMER;
import static com.sorted.common.enums.UserType.SELLER;

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
    private final StoreActivityService storeActivityService;
    private final PhonePeUtility phonePeUtility;
    private final Secure_Return_Service secureReturnService;

    @Value("${se.secure.max-return-days:180}")
    private Integer maxReturnDays;

    public List<SecureOrderDetailsBean> findSecureOrders(FindOrderReqBean req, HttpServletRequest httpServletRequest) {
        CommonUtils.extractHeaders(httpServletRequest, req);
        // Validate user permissions
        UsersBean usersBean = usersService.validateUserForActivity(req.getReq_user_id(), Activity.SECURE_RETURN);
        switch (usersBean.getRole().getUser_type()) {
            case CUSTOMER, SELLER:
                break;
            default:
                throw new AccessDeniedException();
        }
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (usersBean.getRole().getUser_type() == CUSTOMER) {
            filter.addClause(WhereClause.eq(Secure_Return.Fields.user_id, usersBean.getId()));
        } else {
            filter.addClause(WhereClause.eq(Secure_Return.Fields.seller_id, usersBean.getId()));
        }
        if (req.getOrder_status() != null) {
            filter.addClause(WhereClause.eq(Secure_Return.Fields.status, req.getOrder_status()));
        }
        if (req.getCode() != null) {
            filter.addClause(WhereClause.eq(Secure_Return.Fields.order_code, req.getCode()));
        }
        if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
            LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
            LocalDateTime to = LocalDate.parse(req.getTo_date()).atTime(LocalTime.MAX);
            log.debug("Applying date range filter from {} to {}", from, to);
            filter.addClause(WhereClause.gte(BaseMongoEntity.Fields.creation_date, from));
            filter.addClause(WhereClause.lte(BaseMongoEntity.Fields.creation_date, to));
        }
        List<Secure_Return> secureReturns = secureReturnService.repoFind(filter);
        if (CollectionUtils.isEmpty(secureReturns)) {
            return Collections.emptyList();
        }

        List<String> orderIds = secureReturns.stream().map(Secure_Return::getOrder_id).distinct().toList();

        SEFilter orderFilter = new SEFilter(SEFilterType.AND);
        orderFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        orderFilter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, orderIds));
        List<Order_Details> orderDetails = orderDetailsService.repoFind(orderFilter);
        if (CollectionUtils.isEmpty(orderDetails)) {
            return Collections.emptyList();
        }

        Map<String, Order_Details> orderMap = orderDetails.stream().collect(Collectors.toMap(Order_Details::getId, Function.identity()));

        return secureReturns.stream().map(secureReturn -> buildResponse(secureReturn, orderMap)).toList();
    }

    private static SecureOrderDetailsBean buildResponse(Secure_Return secureReturn, Map<String, Order_Details> orderMap) {
        return SecureOrderDetailsBean.builder()
                .secureReturnId(secureReturn.getId())
                .orderId(secureReturn.getOrder_id())
                .orderCode(orderMap.get(secureReturn.getOrder_id()).getCode())
                .status(secureReturn.getStatus().name())
                .orderDate(orderMap.get(secureReturn.getOrder_id()).getCreation_date_str())
                .totalSellingPriceAfterDiscount(CommonUtils.paiseToRupee(orderMap.get(secureReturn.getOrder_id()).getTotal_amount()))
                .orderItems(secureReturn.getItems().stream()
                        .map(item -> SecureOrderItemDetail.builder()
                                .productId(item.getProduct_id())
                                .productName(item.getProduct_name())
                                .quantity(Math.toIntExact(item.getQuantity()))
                                .sellingPrice(CommonUtils.paiseToRupee(item.getSelling_price_after_discount()))
                                .maxExpectedSecureRefund(CommonUtils.paiseToRupee(item.getEstimated_refund_amount()))
                                .build())
                        .collect(Collectors.toList()))
                .maxExpectedSecureRefund(CommonUtils.paiseToRupee(secureReturn.getTotal_estimated_refund()))
                .scheduledReturnDate(secureReturn.getScheduled_pickup_date())
                .refundStatus(secureReturn.getRefund_status().getDescription())
                .refundTransactionId(secureReturn.getRefund_transaction_id())
                .build();
    }

    /**
     * Initiates a secure return process for the given request
     *
     * @param secureBean The secure return request details
     */
    public void initiateSecureReturn(InitiateSecureBean secureBean) {
        log.info("Initiating secure return process for user: {}", secureBean.getReq_user_id());

        UsersBean user = validateCustomer(secureBean.getReq_user_id());
        validateSecureInitiateRequest(secureBean);
        validateIfAlreadyScheduled(secureBean.getOrderId());
        LocalDate returnDate = parseReturnDate(secureBean.getReturnDate());
        Order_Details order = validateAndGetOrder(secureBean.getOrderId(), user.getId(), OrderStatus.DELIVERED);
        LocalDate orderDate = order.getCreation_date().toLocalDate();
        Preconditions.check(orderDate.plusDays(maxReturnDays + 1).isAfter(returnDate), ResponseCode.RETURN_DATE_RANGE_EXCEEDED);
        List<Order_Item> orderItems = validateOrderItems(secureBean, order);
        Seller seller = validateSellerBusinessHours(order.getSeller_id(), returnDate);
        Address pickUpAddress = validateAndGetCustomerAddressForSecureReturn(secureBean.getAddressId(), user.getId());
        Address deliveryAddress = validateAndGetSellerAddressForSecureReturn(seller.getAddress_id(), order.getSeller_id());
        GetQuoteRequest getQuoteRequest = porterUtility.buildGetQuoteRequest(pickUpAddress, deliveryAddress, user.getMobile_no(), user.getFirst_name());
        GetQuoteResponse deliveryQuote = porterUtility.getDeliveryQuote(getQuoteRequest);
        Preconditions.check(Objects.nonNull(deliveryQuote), new DeliveryNotAvailableException());
        registerSecureReturn(order, user, seller, orderItems, secureBean, returnDate, pickUpAddress, deliveryAddress, deliveryQuote);
        log.info("Successfully scheduled secure return for order ID: {}", order.getId());
    }

    /**
     * Appraises a secure return and calculates refund amount
     * Rating system: 5 = 50%, 4 = 40%, 3 = 30%, 2 = 20%, 1 = 10% of selling_price_after_discount
     *
     * @param appraisal The appraisal details including rating or amount
     */

    public void appraiseSecureReturn(AppraiseSecureReturn appraisal) {
        log.info("Appraising secure return: {}", appraisal.getSecureReturnId());
        // Validate seller
        UsersBean seller = validateSeller(appraisal.getReq_user_id());

        // Validate appraisal request
        validateAppraiseSecureRequest(appraisal);

        Optional<Secure_Return> optionalSecureReturn = secureReturnService.findById(appraisal.getSecureReturnId());
        Preconditions.check(optionalSecureReturn.isPresent(), ResponseCode.INVALID_SECURE_RETURN_ID);

        Secure_Return secureReturn = optionalSecureReturn.get();
        Preconditions.check(secureReturn.getSeller_id().equals(seller.getId()), ResponseCode.INVALID_SECURE_RETURN_ID);
        Preconditions.check(secureReturn.getStatus().equals(SecureReturnStatus.DELIVERED_TO_SELLER), ResponseCode.INVALID_SECURE_RETURN_STATUS);
        List<Secure_Return_Item> secureReturnItems = secureReturn.getItems();
        for (Secure_Return_Item item : secureReturnItems) {
            SecureItemAppraisalDetails itemAppraisalDetails = appraisal.getItems().stream().filter(appraisalItem -> appraisalItem.orderItemId().equals(item.getOrder_item_id())).findFirst().get();
            item.applyAppraisal(itemAppraisalDetails.grade(), itemAppraisalDetails.remarks(), appraisal.getReq_user_id(), itemAppraisalDetails.imageUrls());
        }
        secureReturn.setItems(secureReturnItems);
        secureReturn.calculateTotalActualRefund();
        secureReturn.setStatus(
                SecureReturnStatus.APPRAISAL_COMPLETED,
                appraisal.getReq_user_id(),
                "All items appraised by seller"
        );
        secureReturnService.update(secureReturn.getId(), secureReturn, appraisal.getReq_user_id());
        initiateRefundIfApplicable(secureReturn);
    }

    private void initiateRefundIfApplicable(Secure_Return secureReturn) {
        if (secureReturn.getTotal_actual_refund() == null || secureReturn.getTotal_actual_refund() <= 0) {
            // No refund needed (all items Grade C)
            secureReturn.setRefund_status(RefundStatus.NOT_APPLICABLE);
            secureReturn.setStatus(
                    SecureReturnStatus.REFUND_NOT_APPLICABLE,
                    Defaults.SYSTEM_ADMIN,
                    "No refund needed - all items rejected"
            );
            secureReturnService.update(secureReturn.getId(), secureReturn, Defaults.SYSTEM_ADMIN);
            log.info("No refund needed for secure return: {}", secureReturn.getId());
        } else {
            // Refund will be processed by a separate cron/service
            log.info("Refund of ₹{} needs to be processed for secure return: {}",
                    secureReturn.getTotal_actual_refund(), secureReturn.getId());
        }
    }

    private UsersBean validateCustomer(String userId) {
        log.debug("Validating user for secure return activity. User ID: {}", userId);
        UsersBean user = usersService.validateUserForActivity(userId, Activity.SECURE_RETURN);
        Preconditions.check(user.getRole().getUser_type() == CUSTOMER, ResponseCode.ACCESS_DENIED);
        log.debug("User validation successful. User role: {}", user.getRole().getUser_type());
        return user;
    }

    private void validateSecureInitiateRequest(InitiateSecureBean secureBean) {
        Preconditions.check(StringUtils.hasText(secureBean.getOrderId()), ResponseCode.MISSING_ORDER_ID);
        Preconditions.check(StringUtils.hasText(secureBean.getReturnDate()), ResponseCode.MISSING_RETURN_DATE);
        Preconditions.check(secureBean.getTimeSlot() != null, ResponseCode.MISSING_TIME_SLOT);
        Preconditions.check(StringUtils.hasText(secureBean.getAddressId()), ResponseCode.MISSING_PICKUP_ADD);
    }

    private void validateIfAlreadyScheduled(String orderId) {
        Secure_Return secureReturn = secureReturnService.findByOrderId(orderId);
        Preconditions.check(Objects.isNull(secureReturn), ResponseCode.SECURE_RETURN_ALREADY_SCHEDULED);
    }

    private Secure_Return validateScheduled(String secureReturnId) {
        Secure_Return secureReturn = secureReturnService.findById(secureReturnId).orElseThrow(
                () -> new CustomIllegalArgumentsException(ResponseCode.INVALID_SECURE_RETURN_ID)
        );
        Preconditions.check(secureReturn.getStatus().equals(SecureReturnStatus.SCHEDULED), ResponseCode.INVALID_SECURE_RETURN_STATUS);
        Preconditions.check(secureReturn.getReschedule_count() < secureReturn.getMax_reschedule_allowed(), ResponseCode.SECURE_RETURN_RESCHEDULE_LIMIT_EXCEEDED);
        return secureReturn;
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

    private Order_Details validateAndGetOrder(String orderId, String userId, OrderStatus orderStatus) {
        log.debug("Fetching order details for order ID: {}", orderId);
        Order_Details order = orderDetailsService.findById(orderId)
                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND));
        Preconditions.check(order.getUser_id().equals(userId), ResponseCode.ORDER_NOT_FOUND);
        log.debug("Order found with status: {}", order.getStatus());
        Preconditions.check(order.getStatus() == orderStatus,
                ResponseCode.INVALID_STATUS_FOR_SECURE_RETURN);

        return order;
    }

    private List<Order_Item> validateOrderItems(InitiateSecureBean secureBean, Order_Details order) {

        List<Order_Item> orderItems = findOrderItems(order.getId());
        log.debug("Found {} order items for return processing", orderItems.size());

        List<Order_Item> secureItems = orderItems.stream().filter(item -> item.getType().equals(PurchaseType.SECURE)).toList();

        if (CollectionUtils.isEmpty(secureItems)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NOT_SECURED_ITEM);
        }

        boolean invalidItemStatus = secureItems.stream()
                .anyMatch(item -> item.getStatus() != OrderStatus.DELIVERED);
        Preconditions.check(!invalidItemStatus, ResponseCode.INVALID_ITEM_STATUS_FOR_SECURE_RETURN);
        return secureItems;
    }


    private List<Order_Item> findOrderItems(String orderId) {
        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderId));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
        if (CollectionUtils.isEmpty(orderItems)) {
            log.error("No order items found for order ID: {}", orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.ITEM_NOT_FOUND);
        }
        return orderItems;
    }

    private Seller validateSellerBusinessHours(String sellerId, LocalDate returnDate) {
        Seller seller = sellerService.findById(sellerId)
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

    private UsersBean validateSeller(String userId) {
        log.debug("Validating seller user for secure return activity. User ID: {}", userId);
        UsersBean user = usersService.validateUserForActivity(userId, Activity.APPRAISE_SECURE_RETURN);
        Preconditions.check(user.getRole().getUser_type() == SELLER, ResponseCode.ACCESS_DENIED);
        log.debug("Seller user validation successful. User role: {}", user.getRole().getUser_type());
        return user;
    }

    /**
     * Validates the appraisal request
     * Rating is mandatory, amount is optional (used to override calculated refund)
     */
    private void validateAppraiseSecureRequest(AppraiseSecureReturn appraisal) {
        Preconditions.check(appraisal.getSecureReturnId() != null, ResponseCode.MISSING_SECURE_RETURN_ID);

        // Rating is mandatory
        Preconditions.check(CollectionUtils.isNotEmpty(appraisal.getItems()), ResponseCode.MISSING_SECURE_ITEMS);

        for (SecureItemAppraisalDetails item : appraisal.getItems()) {
            Preconditions.check(item.orderItemId() != null, ResponseCode.MISSING_ORDER_ITEM_ID);
            Preconditions.check(item.grade() != null, ResponseCode.MISSING_GRADE);
            if (!item.grade().equals(AppraisalGrade.A)) {
                Preconditions.check(StringUtils.hasText(item.remarks()), ResponseCode.MISSING_GRADE_REMARKS);
                Preconditions.check(CollectionUtils.isNotEmpty(item.imageUrls()), ResponseCode.MISSING_SECURE_ITEM_IMAGES);
            }
        }
    }


    /**
     * Reschedules a secure return pickup
     * Maximum 2 reschedules allowed per order
     *
     * @param rescheduleRequest The reschedule request details
     */
    public void rescheduleSecureReturn(InitiateSecureBean rescheduleRequest) {
        // Validate customer
        UsersBean user = validateCustomer(rescheduleRequest.getReq_user_id());
        validateRescheduleRequest(rescheduleRequest);
        Secure_Return secureReturn = validateScheduled(rescheduleRequest.getSecureReturnId());
        LocalDate returnDate = parseReturnDate(rescheduleRequest.getReturnDate());
        Order_Details orderDetails = orderDetailsService.findById(secureReturn.getOrder_id()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND));
        Preconditions.check(orderDetails.getCreation_date().toLocalDate().plusDays(maxReturnDays + 1).isBefore(returnDate), ResponseCode.RETURN_DATE_RANGE_EXCEEDED);
        validateSellerBusinessHours(secureReturn.getSeller_id(), returnDate);
        updateScheduledReturn(secureReturn, returnDate, rescheduleRequest.getTimeSlot(), user.getId());
        log.info("Successfully scheduled secure return for secure return ID: {}", secureReturn.getId());
    }

    private void updateScheduledReturn(Secure_Return secureReturn, LocalDate returnDate, TimeSlot timeSlot, String userId) {
        secureReturn.setScheduled_pickup_date(returnDate);
        secureReturn.setScheduled_time_slot(timeSlot);
        secureReturn.incrementRescheduleCount();
        secureReturnService.update(secureReturn.getId(), secureReturn, userId);
    }

    private void validateRescheduleRequest(InitiateSecureBean rescheduleRequest) {
        Preconditions.check(StringUtils.hasText(rescheduleRequest.getSecureReturnId()), ResponseCode.MISSING_SECURE_RETURN_ID);
        Preconditions.check(StringUtils.hasText(rescheduleRequest.getReturnDate()), ResponseCode.MISSING_RETURN_DATE);
        Preconditions.check(rescheduleRequest.getTimeSlot() != null, ResponseCode.MISSING_TIME_SLOT);
    }

    private void registerSecureReturn(
            Order_Details order,
            UsersBean user,
            Seller seller,
            List<Order_Item> orderItems,
            InitiateSecureBean request,
            LocalDate returnDate,
            Address pickupAddress,
            Address deliveryAddress,
            GetQuoteResponse deliveryQuote
    ) {
        Secure_Return secureReturn = new Secure_Return();

        // References
        secureReturn.setOrder_id(order.getId());
        secureReturn.setOrder_code(order.getCode());
        secureReturn.setUser_id(user.getId());
        secureReturn.setSeller_id(seller.getId());
        secureReturn.setSecure_order_code(generateSecureOrderCode());

        // Scheduling
        secureReturn.setScheduled_pickup_date(returnDate);
        secureReturn.setScheduled_time_slot(request.getTimeSlot());
        secureReturn.setReschedule_count(0);
        secureReturn.setMax_reschedule_allowed(2);

        // Addresses
        secureReturn.setPickup_address(createAddressDTOFromAddress(pickupAddress));
        secureReturn.setDelivery_address(createAddressDTOFromAddress(deliveryAddress));

        // Delivery charges
        if (deliveryQuote.getVehicle().getFare() != null) {
            secureReturn.setEstimated_delivery_charges(deliveryQuote.getVehicle().getFare().getMinor_amount());
        }

        // Build items
        List<Secure_Return_Item> items = new ArrayList<>();
        for (Order_Item orderItem : orderItems) {
            Secure_Return_Item item = Secure_Return_Item.builder()
                    .order_item_id(orderItem.getId())
                    .product_id(orderItem.getProduct_id())
                    .product_code(orderItem.getProduct_code())
                    .product_name(orderItem.getProduct_name())
                    .product_image_url(orderItem.getCdn_url())
                    .quantity(orderItem.getQuantity())
                    .selling_price_after_discount(orderItem.getSelling_price_after_discount())
                    .total_item_cost(orderItem.getSelling_price_after_discount() * orderItem.getQuantity())
                    .item_status(SecureItemStatus.PENDING_APPRAISAL)
                    .build();

            // Calculate estimated refund (assumes Grade A - 50%)
            item.calculateEstimatedRefund();
            items.add(item);
        }

        secureReturn.setItems(items);
        secureReturn.calculateTotalEstimatedRefund();

        // Set initial status
        secureReturn.setStatus(SecureReturnStatus.SCHEDULED, user.getId(), "Customer initiated secure return");

        // Set refund status
        secureReturn.setRefund_status(RefundStatus.NOT_INITIATED);

        secureReturnService.create(secureReturn, user.getId());
    }

    private String generateSecureOrderCode() {
        return "SEC-ORD-" + LocalDate.now().getMonth() + Year.now() + "-" + CommonUtils.getNanoseconds();
    }

    public void initiateSecurePickUp() {
        log.info("Initiating secure pick up");
        List<Secure_Return> secureReturns = secureReturnService.fetchScheduledSecureReturns();
        if (CollectionUtils.isEmpty(secureReturns)) {
            log.info("No secure returns to initiate");
            return;
        }

        List<String> sellerIds = secureReturns.stream().map(Secure_Return::getSeller_id).toList();
        List<String> userIds = secureReturns.stream().map(Secure_Return::getUser_id).toList();

        SEFilter sellerFilter = new SEFilter(SEFilterType.AND);
        sellerFilter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, sellerIds));
        sellerFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        SEFilter userFilter = new SEFilter(SEFilterType.AND);
        userFilter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
        userFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Seller> sellers = sellerService.repoFind(sellerFilter);
        List<Users> users = usersService.repoFind(userFilter);
        Map<String, Seller> sellerMap = sellers.stream().collect(Collectors.toMap(Seller::getId, Function.identity()));
        Map<String, Users> userMap = users.stream().collect(Collectors.toMap(Users::getId, Function.identity()));

        for (Secure_Return secureReturn : secureReturns) {
            try {
                Seller seller = sellerMap.get(secureReturn.getSeller_id());
                Users user = userMap.get(secureReturn.getUser_id());
                this.initiatePickUp(secureReturn, seller, user);
            } catch (Exception e) {
                log.error("Error initiating secure pick up for order: {}", secureReturn.getOrder_id(), e);
            }
        }
    }

    private void initiatePickUp(Secure_Return secureReturn, Seller seller, Users user) {
        boolean storeOperational = storeActivityService.isStoreOperational(secureReturn.getSeller_id());
        if (!storeOperational) {
            log.error("Store is not operational. Seller ID: {}", secureReturn.getSeller_id());
            return;
        }

        CreateOrderBean createOrderRequest = buildCreateOrderRequest(secureReturn, seller, user);
        CreateOrderResBean response = porterUtility.createOrderForPickup(createOrderRequest);

        secureReturn.setDp_order_id(response.getOrder_id());
        secureReturn.setDp_tracking_url(response.getTracking_url());
        secureReturn.setStatus(SecureReturnStatus.PICKUP_PENDING, user.getId(), "Pickup initiated");

        secureReturnService.update(secureReturn.getId(), secureReturn, user.getId());

        // TODO: notify customer
    }

    private CreateOrderBean buildCreateOrderRequest(Secure_Return secureReturn, Seller seller, Users user) {

        int count = secureReturn.getItems().size();
        String message = "Please verify no of items: " + count + ".";

        CreateOrderBean.Delivery_Instructions instruction = CreateOrderBean.Delivery_Instructions.builder()
                .type("text").description(message).build();
        CreateOrderBean.Instruction_List instructionList = CreateOrderBean.Instruction_List.builder()
                .instructions_list(List.of(instruction)).build();

        Spoc_Details spoc = seller.getSpoc_details().stream()
                .filter(Spoc_Details::isPrimary).findFirst()
                .orElseThrow(() -> new RuntimeException("Missing primary SPOC"));

        return CreateOrderBean.builder()
                .request_id(secureReturn.getSecure_order_code())
                .delivery_instructions(instructionList)
                .pickup_details(CreateOrderBean.Pickup_Details.builder()
                        .address(buildAddress(secureReturn.getPickup_address(), user.getFirst_name() + " " + user.getLast_name(), user.getMobile_no()))
                        .build())
                .drop_details(CreateOrderBean.Drop_Details.builder()
                        .address(buildAddress(secureReturn.getDelivery_address(), spoc.getFirst_name() + " " + spoc.getLast_name(), spoc.getMobile_no()))
                        .build())
                .build();
    }

    public void trackDelivery() {
        List<Secure_Return> secureReturns = secureReturnService.fetchInTransitOrders();
        if (CollectionUtils.isEmpty(secureReturns)) {
            return;
        }

        for (Secure_Return secureReturn : secureReturns) {
            try {
                FetchOrderRes fetchOrderRes = porterUtility.getOrderStatus(secureReturn.getDp_order_id());
                SecureReturnStatus currentSecureReturnStatus = getSecureReturnStatus(fetchOrderRes);
                if (currentSecureReturnStatus == secureReturn.getStatus()) {
                    continue;
                }
                secureReturn.setStatus(currentSecureReturnStatus, Defaults.TRACK_ORDER_CRON, "Delivery status updated");
                secureReturnService.update(secureReturn.getId(), secureReturn, Defaults.TRACK_ORDER_CRON);
                switch (currentSecureReturnStatus) {
                    case PICKUP_ASSIGNED, CANCELLED, DELIVERED_TO_SELLER, IN_TRANSIT -> {
                        // TODO: notify customer
                    }
                }
            } catch (Exception e) {
                log.error("Error tracking delivery for order: {}", secureReturn.getOrder_id(), e);
            }
        }
    }

    private static @NotNull SecureReturnStatus getSecureReturnStatus(FetchOrderRes fetchOrderRes) {
        FetchOrderRes.Status status = fetchOrderRes.getStatus();
        return switch (status) {
            case open -> SecureReturnStatus.PICKUP_PENDING;
            case accepted -> SecureReturnStatus.PICKUP_ASSIGNED;
            case live -> SecureReturnStatus.IN_TRANSIT;
            case ended, completed -> SecureReturnStatus.DELIVERED_TO_SELLER;
            case cancelled -> SecureReturnStatus.CANCELLED;
        };
    }
}



