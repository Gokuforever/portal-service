package com.sorted.portal.service.secure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.common.beans.AddressDTO;
import com.sorted.common.beans.BusinessHours;
import com.sorted.common.beans.Spoc_Details;
import com.sorted.common.beans.UsersBean;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.*;
import com.sorted.common.enums.*;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.exceptions.DeliveryNotAvailableException;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.porter.req.beans.CreateOrderBean;
import com.sorted.common.porter.req.beans.GetQuoteRequest;
import com.sorted.common.porter.res.beans.CreateOrderResBean;
import com.sorted.common.porter.res.beans.GetQuoteResponse;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.PorterUtility;
import com.sorted.common.utils.Preconditions;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.request.beans.AppraiseSecureReturn;
import com.sorted.portal.request.beans.InitiateSecureBean;
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
import java.util.Objects;
import java.util.Set;

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

    @Value("${se.secure.max-return-days:180}")
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
        GetQuoteRequest getQuoteRequest = porterUtility.buildGetQuoteRequest(pickUpAddress, deliveryAddress, user.getMobile_no(), user.getFirst_name());
        GetQuoteResponse deliveryQuote = porterUtility.getDeliveryQuote(getQuoteRequest);
        Preconditions.check(Objects.nonNull(deliveryQuote), new DeliveryNotAvailableException());
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

        boolean storeOperational = storeActivityService.isStoreOperational(seller.getId());
        if (!storeOperational) {
            log.error("Store is not operational. Seller ID: {}", seller.getId());
            return;
        }

        String secureOrderId = generateSecureOrderId();
        order.setSecure_order_id(secureOrderId);
        orderDetailsService.update(order.getId(), order, Defaults.INITIATE_SECURE_RETURN_CRON);

        CreateOrderBean createOrderRequest = buildCreateOrderRequest(order, items, seller, user, secureOrderId);
        CreateOrderResBean response = porterUtility.createOrderForPickup(createOrderRequest);

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

    /**
     * Appraises a secure return and calculates refund amount
     * Rating system: 5 = 50%, 4 = 40%, 3 = 30%, 2 = 20%, 1 = 10% of selling_price_after_discount
     * 
     * @param appraisal The appraisal details including rating or amount
     */
    public void appraiseSecureReturn(AppraiseSecureReturn appraisal) {
        log.info("Appraising secure return for order: {}", appraisal.getOrderId());
        
        // Validate seller
        UsersBean seller = validateSeller(appraisal.getReq_user_id());
        
        // Validate appraisal request
        validateAppraiseSecureRequest(appraisal);
        
        // Get order and validate status
        Order_Details order = validateAndGetOrder(appraisal.getOrderId(), seller.getId(), OrderStatus.SECURE_RETURN_COMPLETED);
        
        // Get order items that were returned
        AggregationFilter.SEFilter itemFilter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        itemFilter.addClause(AggregationFilter.WhereClause.eq(Order_Item.Fields.order_id, order.getId()));
        itemFilter.addClause(AggregationFilter.WhereClause.eq(Order_Item.Fields.status_id, OrderStatus.SECURE_RETURN_COMPLETED.getId()));
        
        List<Order_Item> returnedItems = orderItemService.repoFind(itemFilter);
        
        if (CollectionUtils.isEmpty(returnedItems)) {
            log.error("No returned items found for order: {}", order.getId());
            throw new CustomIllegalArgumentsException("No returned items found for this order");
        }
        
        // Calculate refund amount in paise (Long)
        Long refundAmountPaise;
        if (appraisal.getAmount() != null && appraisal.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Use provided amount - convert rupees to paise
            refundAmountPaise = CommonUtils.rupeeToPaise(appraisal.getAmount());
            log.info("Using provided refund amount: ₹{} ({} paise)", appraisal.getAmount(), refundAmountPaise);
        } else {
            // Calculate based on rating (rating is mandatory)
            refundAmountPaise = calculateRefundFromRating(returnedItems, appraisal.getRating());
            log.info("Calculated refund amount from rating {}: {} paise (₹{})", 
                    appraisal.getRating(), refundAmountPaise, refundAmountPaise / 100.0);
        }
        
        // Update order with appraisal details and set status to APPRAISED
        updateOrderWithAppraisal(order, returnedItems, refundAmountPaise, appraisal, seller.getId());
        
        // Initiate refund with PhonePe
        initiateSecureRefund(order, refundAmountPaise, seller.getId());
        
        log.info("Successfully appraised secure return for order: {}. Refund amount: {} paise (₹{})", 
                order.getId(), refundAmountPaise, refundAmountPaise / 100.0);
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
        Preconditions.check(StringUtils.hasText(appraisal.getOrderId()), ResponseCode.MISSING_ORDER_ID);
        
        // Rating is mandatory
        Preconditions.check(appraisal.getRating() != null, ResponseCode.MISSING_RATING_OR_AMOUNT);
        
        // Validate rating range (1-5)
        Preconditions.check(appraisal.getRating() >= 1 && appraisal.getRating() <= 5, 
                ResponseCode.INVALID_RATING_RANGE);
        
        // Validate amount if provided (optional - used to override calculated refund)
        if (appraisal.getAmount() != null) {
            Preconditions.check(appraisal.getAmount().compareTo(BigDecimal.ZERO) > 0, ResponseCode.INVALID_AMOUNT);
        }
    }
    
    /**
     * Calculates refund amount based on rating
     * Rating 5 = 50%, 4 = 40%, 3 = 30%, 2 = 20%, 1 = 10%
     * All amounts are in paise
     */
    private Long calculateRefundFromRating(List<Order_Item> items, Integer rating) {
        log.debug("Calculating refund for {} items with rating {}", items.size(), rating);
        
        // Sum up all item prices (in paise)
        Long totalItemPricePaise = items.stream()
                .map(Order_Item::getSelling_price_after_discount)
                .filter(Objects::nonNull)
                .reduce(0L, Long::sum);
        
        // Calculate refund amount based on rating
        // rating * 10 gives percentage (5->50%, 4->40%, etc.)
        Long refundAmountPaise = (totalItemPricePaise * rating * 10) / 100;

        log.debug("Total item price: {} paise (₹{}), Rating: {}, Percentage: {}%, Refund amount: {} paise (₹{})", 
                totalItemPricePaise, totalItemPricePaise / 100.0, rating, rating * 10, 
                refundAmountPaise, refundAmountPaise / 100.0);
        
        return refundAmountPaise;
    }
    
    /**
     * Updates order and items with appraisal details
     */
    private void updateOrderWithAppraisal(Order_Details order, List<Order_Item> items, 
                                          Long refundAmountPaise, AppraiseSecureReturn appraisal, String sellerId) {
        log.info("Updating order {} with appraisal. Refund: {} paise (₹{}), Rating: {}", 
                order.getId(), refundAmountPaise, refundAmountPaise / 100.0, appraisal.getRating());
        
        // Update order status to SECURE_RETURN_APPRAISED
        order.setStatus(OrderStatus.SECURE_RETURN_APPRAISED, sellerId);
        
        // Store appraisal details in order (you may need to add these fields to Order_Details)
        // order.setSecure_refund_amount(refundAmount);
        // order.setSecure_appraisal_rating(appraisal.getRating());
        // order.setSecure_appraisal_remark(appraisal.getRemark());
        
        orderDetailsService.update(order.getId(), order, sellerId);
        
        // Update items with rating
        items.forEach(item -> {
            item.setSecure_item_rating(appraisal.getRating());
            item.setStatus(OrderStatus.SECURE_RETURN_APPRAISED, sellerId);
            orderItemService.update(item.getId(), item, sellerId);
        });
        
        log.info("Order and items updated successfully with appraisal");
    }

    /**
     * Initiates refund with PhonePe for the appraised secure return
     */
    private void initiateSecureRefund(Order_Details order, Long refundAmountPaise, String sellerId) {
        log.info("Initiating PhonePe refund for order: {}, Amount: {} paise (₹{})",
                order.getId(), refundAmountPaise, refundAmountPaise / 100.0);
        
        // Generate unique refund transaction ID
        String refundTxnId = generateRefundTransactionId(order.getId());
        
        // Call PhonePe partial refund API
        var refundResponse = phonePeUtility.partialRefund(
                refundTxnId,
                order.getId(),
                refundAmountPaise
        );
        
        if (refundResponse.isEmpty()) {
            log.error("Empty response from PhonePe refund API for order: {}", order.getId());
            // Keep status as APPRAISED for manual intervention
            return;
        }
        
        // Process refund response based on state
        var response = refundResponse.get();
        String state = response.getState();
        
        log.info("PhonePe refund response state: {} for order: {}", state, order.getId());
        
        OrderStatus orderStatus = switch (state) {
            case "COMPLETED" -> {
                log.info("Refund completed immediately for order: {}", order.getId());
                yield OrderStatus.PARTIALLY_REFUNDED;
            }
            case "FAILED" -> {
                log.error("Refund failed immediately for order: {}", order.getId());
                yield OrderStatus.REFUND_FAILED;
            }
            default -> {
                log.info("Refund pending for order: {}. State: {}", order.getId(), state);
                yield OrderStatus.SECURE_REFUND_PENDING;
            }
        };
        
        // Update order with refund details and status
        order.setRefund_transaction_id(refundTxnId);
        order.setStatus(orderStatus, sellerId);
        orderDetailsService.update(order.getId(), order, sellerId);
        
        log.info("Order {} status updated to {}", order.getId(), orderStatus);
        
        // Send error notification if refund failed
        if (orderStatus == OrderStatus.REFUND_FAILED) {
            String subject = "Secure Return Refund Failed - Order: " + order.getCode();
            String message = String.format(
                    "PhonePe refund failed for secure return.%n" +
                    "Order ID: %s%n" +
                    "Order Code: %s%n" +
                    "Refund Transaction ID: %s%n" +
                    "Refund Amount: %d paise (₹%.2f)%n" +
                    "Please investigate and process refund manually.",
                    order.getId(), 
                    order.getCode(), 
                    refundTxnId,
                    refundAmountPaise,
                    refundAmountPaise / 100.0
            );
            // Note: Add InternalMailService dependency if not already present
            // internalMailService.sendMailOnError(subject, message, null);
            log.error("Refund failed notification: {}", message);
        }
    }

    /**
     * Generates a unique refund transaction ID
     */
    private String generateRefundTransactionId(String orderId) {
        return "SECURE-REFUND-" + orderId + "-" + System.currentTimeMillis();
    }

    /**
     * Reschedules a secure return pickup
     * Maximum 2 reschedules allowed per order
     *
     * @param rescheduleBean The reschedule request details
     */
    public void rescheduleSecureReturn(com.sorted.portal.request.beans.RescheduleSecureBean rescheduleBean) {
        log.info("Rescheduling secure return for user: {}", rescheduleBean.getReq_user_id());

        // Validate customer
        UsersBean user = validateCustomer(rescheduleBean.getReq_user_id());
        
        // Validate reschedule request
        validateRescheduleRequest(rescheduleBean);
        
        // Parse new pickup date
        LocalDate newPickupDate = parseReturnDate(rescheduleBean.getNewPickupDate());
        
        // Get and validate order - must be in SECURE_RETURN_SCHEDULED status
        Order_Details order = validateAndGetOrder(rescheduleBean.getOrderId(), user.getId(), OrderStatus.SECURE_RETURN_SCHEDULED);
        
        // Check reschedule count - maximum 2 reschedules allowed
        if (order.getMax_secured_reschedule_count() >= 2) {
            log.error("Maximum reschedule limit reached for order: {}", order.getId());
            throw new CustomIllegalArgumentsException("Maximum reschedule limit (2) reached. Cannot reschedule further.");
        }
        
        // Validate seller business hours for new date
        Seller seller = validateSellerBusinessHours(order, newPickupDate);
        
        // If address is being changed, validate it
        Address pickUpAddress;
        if (StringUtils.hasText(rescheduleBean.getAddressId())) {
            pickUpAddress = validateAndGetCustomerAddressForSecureReturn(rescheduleBean.getAddressId(), user.getId());
        } else {
            // Use existing pickup address from order
            pickUpAddress = addressService.findById(order.getSecure_pickup_address().getId())
                    .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND));
        }
        
        // Get delivery address
        Address deliveryAddress = validateAndGetSellerAddressForSecureReturn(seller.getAddress_id(), order.getSeller_id());
        
        // Check delivery availability for new date
        GetQuoteRequest getQuoteRequest = porterUtility.buildGetQuoteRequest(pickUpAddress, deliveryAddress, user.getMobile_no(), user.getFirst_name());
        GetQuoteResponse deliveryQuote = porterUtility.getDeliveryQuote(getQuoteRequest);
        Preconditions.check(Objects.nonNull(deliveryQuote), new DeliveryNotAvailableException());
        
        // Update order with new schedule
        updateOrderForReschedule(order, rescheduleBean, newPickupDate, user.getId(), pickUpAddress);
        
        log.info("Successfully rescheduled secure return for order ID: {}", order.getId());
    }

    /**
     * Validates the reschedule request
     */
    private void validateRescheduleRequest(com.sorted.portal.request.beans.RescheduleSecureBean rescheduleBean) {
        Preconditions.check(StringUtils.hasText(rescheduleBean.getOrderId()), ResponseCode.MISSING_ORDER_ID);
        Preconditions.check(StringUtils.hasText(rescheduleBean.getNewPickupDate()), ResponseCode.MISSING_RETURN_DATE);
        Preconditions.check(rescheduleBean.getNewTimeSlot() != null, ResponseCode.MISSING_TIME_SLOT);
    }

    /**
     * Updates order details for reschedule
     */
    private void updateOrderForReschedule(Order_Details order, com.sorted.portal.request.beans.RescheduleSecureBean rescheduleBean,
                                          LocalDate newPickupDate, String userId, Address pickUpAddress) {
        log.info("Updating order for reschedule. Order ID: {}, New Date: {}, New Time Slot: {}",
                order.getId(), newPickupDate, rescheduleBean.getNewTimeSlot());

        // Update pickup schedule
        order.setSecured_date(newPickupDate);
        order.setSecured_time_slot(rescheduleBean.getNewTimeSlot());
        
        // Increment reschedule count
        order.setMax_secured_reschedule_count(order.getMax_secured_reschedule_count() + 1);
        
        // Update pickup address if changed
        if (StringUtils.hasText(rescheduleBean.getAddressId())) {
            order.setSecure_pickup_address(createAddressDTOFromAddress(pickUpAddress));
        }
        
        // Save order
        orderDetailsService.update(order.getId(), order, userId);
        
        log.info("Order rescheduled successfully. Reschedule count: {}", order.getMax_secured_reschedule_count());
    }

}



