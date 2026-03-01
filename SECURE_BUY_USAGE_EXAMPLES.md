# Secure Buy/Return - Usage Examples

## Service Layer Usage

The `Secure_Return_Service` extends `GenericEntityServiceImpl` and provides all standard CRUD operations.

---

## 1. Create a Secure Return

```java
@Autowired
private Secure_Return_Service secureReturnService;

@Autowired
private Order_Item_Service orderItemService;

public void initiateSecureReturn(InitiateSecureBean request, String userId) {
    // Build secure return entity
    Secure_Return secureReturn = new Secure_Return();
    secureReturn.setOrder_id(request.getOrderId());
    secureReturn.setOrder_code(orderCode);
    secureReturn.setUser_id(userId);
    secureReturn.setSeller_id(sellerId);
    secureReturn.setSecure_order_code(generateSecureOrderCode());
    
    // Set scheduling
    secureReturn.setScheduled_pickup_date(request.getReturnDate());
    secureReturn.setScheduled_time_slot(request.getTimeSlot());
    secureReturn.setReschedule_count(0);
    secureReturn.setMax_reschedule_allowed(2);
    
    // Set addresses
    secureReturn.setPickup_address(pickupAddressDTO);
    secureReturn.setDelivery_address(deliveryAddressDTO);
    
    // Build items list
    List<Secure_Return_Item> items = new ArrayList<>();
    for (String itemId : request.getOrderItemIds()) {
        Order_Item orderItem = orderItemService.findById(itemId);
        
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
    secureReturn.setStatus(SecureReturnStatus.SCHEDULED, userId, "Customer initiated secure return");
    
    // Set refund status
    secureReturn.setRefund_status(RefundStatus.NOT_INITIATED);
    
    // Create in database
    Secure_Return created = secureReturnService.create(secureReturn, userId);
    
    log.info("Created secure return: {}", created.getId());
}
```

---

## 2. Find Secure Returns

### By Order ID
```java
Optional<Secure_Return> secureReturn = secureReturnService.repoFindOne(
    new SEFilter(SEFilterType.AND)
        .addClause(WhereClause.eq(Secure_Return.Fields.order_id, orderId))
);
```

### By User ID
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.eq(Secure_Return.Fields.user_id, userId));
filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

List<Secure_Return> userReturns = secureReturnService.repoFind(filter);
```

### By Seller ID
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.eq(Secure_Return.Fields.seller_id, sellerId));
filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

List<Secure_Return> sellerReturns = secureReturnService.repoFind(filter);
```

### By Status
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.eq(Secure_Return.Fields.status_id, SecureReturnStatus.UNDER_APPRAISAL.getId()));

List<Secure_Return> pendingAppraisals = secureReturnService.repoFind(filter);
```

### By Scheduled Date
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.eq(Secure_Return.Fields.scheduled_pickup_date, LocalDate.now()));
filter.addClause(WhereClause.eq(Secure_Return.Fields.status_id, SecureReturnStatus.SCHEDULED.getId()));

List<Secure_Return> todaysPickups = secureReturnService.repoFind(filter);
```

---

## 3. Update Secure Return (Appraisal)

```java
public void appraiseItems(AppraisalRequestDTO request) {
    // Find secure return
    Secure_Return secureReturn = secureReturnService.findById(request.getSecure_return_id());
    
    // Apply appraisal to each item
    for (AppraisalRequestDTO.ItemAppraisal appraisal : request.getItem_appraisals()) {
        Secure_Return_Item item = secureReturn.getItems().stream()
                .filter(i -> i.getOrder_item_id().equals(appraisal.getOrder_item_id()))
                .findFirst()
                .orElseThrow();
        
        // Apply appraisal (automatically calculates refund)
        item.applyAppraisal(
            appraisal.getGrade(),
            appraisal.getRemarks(),
            request.getReq_user_id()
        );
        
        // Store images
        item.setReturned_item_image_urls(appraisal.getImage_urls());
    }
    
    // Recalculate total actual refund
    secureReturn.calculateTotalActualRefund();
    
    // Update status
    secureReturn.setStatus(
        SecureReturnStatus.APPRAISAL_COMPLETED,
        request.getReq_user_id(),
        "All items appraised"
    );
    
    // Save
    secureReturnService.update(secureReturn.getId(), secureReturn, request.getReq_user_id());
}
```

---

## 4. Reschedule Pickup

```java
public void reschedulePickup(RescheduleSecureBean request) {
    Secure_Return secureReturn = secureReturnService.findById(request.getSecure_return_id());
    
    // Validate can reschedule
    if (!secureReturn.canReschedule()) {
        throw new CustomIllegalArgumentsException("Maximum reschedule limit reached");
    }
    
    // Update schedule
    secureReturn.setScheduled_pickup_date(request.getNew_pickup_date());
    secureReturn.setScheduled_time_slot(request.getNew_time_slot());
    secureReturn.incrementRescheduleCount();
    
    // Update status
    secureReturn.setStatus(
        SecureReturnStatus.RESCHEDULED,
        request.getReq_user_id(),
        "Pickup rescheduled by customer"
    );
    
    // Save
    secureReturnService.update(secureReturn.getId(), secureReturn, request.getReq_user_id());
}
```

---

## 5. Process Refund

```java
public void processRefund(String secureReturnId) {
    Secure_Return secureReturn = secureReturnService.findById(secureReturnId);
    
    // Validate all items are appraised
    if (!secureReturn.areAllItemsAppraised()) {
        throw new CustomIllegalArgumentsException("Not all items have been appraised");
    }
    
    // Check if refund amount > 0
    if (secureReturn.getTotal_actual_refund() == null || secureReturn.getTotal_actual_refund() <= 0) {
        // No refund needed (all items Grade C)
        secureReturn.setRefund_status(RefundStatus.COMPLETED);
        secureReturn.setStatus(SecureReturnStatus.REFUND_COMPLETED, "SYSTEM", "No refund needed");
        secureReturnService.update(secureReturnId, secureReturn, "SYSTEM");
        return;
    }
    
    // Initiate refund via PhonePe/Razorpay
    String transactionId = phonePeUtility.initiateRefund(
        secureReturn.getUser_id(),
        secureReturn.getTotal_actual_refund()
    );
    
    // Update secure return
    secureReturn.setRefund_transaction_id(transactionId);
    secureReturn.setRefund_status(RefundStatus.PENDING);
    secureReturn.setRefund_initiated_at(LocalDateTime.now());
    secureReturn.setStatus(SecureReturnStatus.REFUND_PENDING, "SYSTEM", "Refund initiated");
    
    secureReturnService.update(secureReturnId, secureReturn, "SYSTEM");
}
```

---

## 6. Handle Porter Webhook

```java
public void handlePorterWebhook(PorterWebhookDTO webhook) {
    // Find secure return by dp_order_id
    SEFilter filter = new SEFilter(SEFilterType.AND);
    filter.addClause(WhereClause.eq(Secure_Return.Fields.dp_order_id, webhook.getOrder_id()));
    
    Secure_Return secureReturn = secureReturnService.repoFindOne(filter);
    
    if (secureReturn == null) {
        log.warn("Secure return not found for Porter order: {}", webhook.getOrder_id());
        return;
    }
    
    // Update based on Porter status
    switch (webhook.getStatus()) {
        case "PICKED_UP":
            secureReturn.setActual_pickup_time(LocalDateTime.now());
            secureReturn.setStatus(SecureReturnStatus.PICKED_UP, "PORTER_WEBHOOK", "Items picked up");
            break;
            
        case "IN_TRANSIT":
            secureReturn.setStatus(SecureReturnStatus.IN_TRANSIT, "PORTER_WEBHOOK", "In transit to seller");
            break;
            
        case "DELIVERED":
            secureReturn.setStatus(SecureReturnStatus.DELIVERED_TO_SELLER, "PORTER_WEBHOOK", "Delivered to seller");
            // Trigger appraisal notification to seller
            break;
            
        case "FAILED":
            secureReturn.setFailure_reason(webhook.getFailure_reason());
            secureReturn.setStatus(SecureReturnStatus.FAILED, "PORTER_WEBHOOK", "Delivery failed");
            break;
    }
    
    secureReturnService.update(secureReturn.getId(), secureReturn, "PORTER_WEBHOOK");
}
```

---

## 7. Cron Job - Process Scheduled Pickups

```java
@Scheduled(cron = "0 0 8 * * *") // Run at 8 AM daily
public void processScheduledPickups() {
    log.info("Processing scheduled secure pickups for today");
    
    SEFilter filter = new SEFilter(SEFilterType.AND);
    filter.addClause(WhereClause.eq(Secure_Return.Fields.scheduled_pickup_date, LocalDate.now()));
    filter.addClause(WhereClause.eq(Secure_Return.Fields.status_id, SecureReturnStatus.SCHEDULED.getId()));
    
    List<Secure_Return> todaysPickups = secureReturnService.repoFind(filter);
    
    for (Secure_Return secureReturn : todaysPickups) {
        try {
            // Create Porter order
            CreateOrderBean porterRequest = buildPorterRequest(secureReturn);
            CreateOrderResBean porterResponse = porterUtility.createOrderForPickup(porterRequest);
            
            // Update secure return
            secureReturn.setDp_order_id(porterResponse.getOrder_id());
            secureReturn.setDp_tracking_url(porterResponse.getTracking_url());
            secureReturn.setStatus(SecureReturnStatus.PICKUP_ASSIGNED, "CRON", "Porter order created");
            
            secureReturnService.update(secureReturn.getId(), secureReturn, "CRON");
            
            log.info("Created Porter order for secure return: {}", secureReturn.getId());
        } catch (Exception e) {
            log.error("Failed to create Porter order for secure return: {}", secureReturn.getId(), e);
            secureReturn.setFailure_reason(e.getMessage());
            secureReturn.setStatus(SecureReturnStatus.FAILED, "CRON", "Failed to create Porter order");
            secureReturnService.update(secureReturn.getId(), secureReturn, "CRON");
        }
    }
}
```

---

## 8. Convert to DTO

```java
@Autowired
private SecureReturnMapper mapper;

public SecureReturnDTO getSecureReturn(String id) {
    Secure_Return secureReturn = secureReturnService.findById(id);
    return mapper.toDTO(secureReturn);
}

public List<SecureReturnDTO> getUserSecureReturns(String userId) {
    SEFilter filter = new SEFilter(SEFilterType.AND);
    filter.addClause(WhereClause.eq(Secure_Return.Fields.user_id, userId));
    filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
    
    List<Secure_Return> secureReturns = secureReturnService.repoFind(filter);
    return mapper.toDTOList(secureReturns);
}
```

---

## 9. Refund Calculation Example

```java
// Example: Calculate refund for multiple items with different grades

Secure_Return secureReturn = new Secure_Return();
List<Secure_Return_Item> items = new ArrayList<>();

// Item 1: ₹2000, Grade A
Secure_Return_Item item1 = Secure_Return_Item.builder()
        .selling_price_after_discount(2000L)
        .quantity(1L)
        .total_item_cost(2000L)
        .build();
item1.applyAppraisal(AppraisalGrade.A, "Excellent condition", "seller123");
// Result: actual_refund_amount = ₹1000 (50%)

// Item 2: ₹1500, Grade B
Secure_Return_Item item2 = Secure_Return_Item.builder()
        .selling_price_after_discount(1500L)
        .quantity(1L)
        .total_item_cost(1500L)
        .build();
item2.applyAppraisal(AppraisalGrade.B, "Minor scratches", "seller123");
// Result: actual_refund_amount = ₹450 (30%)

// Item 3: ₹3000, Grade C
Secure_Return_Item item3 = Secure_Return_Item.builder()
        .selling_price_after_discount(3000L)
        .quantity(1L)
        .total_item_cost(3000L)
        .build();
item3.applyAppraisal(AppraisalGrade.C, "Damaged, not functional", "seller123");
// Result: actual_refund_amount = ₹0 (0%)

items.add(item1);
items.add(item2);
items.add(item3);

secureReturn.setItems(items);
secureReturn.calculateTotalActualRefund();

// Total refund: ₹1000 + ₹450 + ₹0 = ₹1450
log.info("Total refund: ₹{}", secureReturn.getTotal_actual_refund());
```

---

## Common Filters

### Find all pending appraisals for a seller
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.eq(Secure_Return.Fields.seller_id, sellerId));
filter.addClause(WhereClause.eq(Secure_Return.Fields.status_id, SecureReturnStatus.DELIVERED_TO_SELLER.getId()));
List<Secure_Return> pending = secureReturnService.repoFind(filter);
```

### Find all completed refunds
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.eq(Secure_Return.Fields.status_id, SecureReturnStatus.REFUND_COMPLETED.getId()));
List<Secure_Return> completed = secureReturnService.repoFind(filter);
```

### Count secure returns by date range
```java
SEFilter filter = new SEFilter(SEFilterType.AND);
filter.addClause(WhereClause.gte(Secure_Return.Fields.scheduled_pickup_date, startDate));
filter.addClause(WhereClause.lte(Secure_Return.Fields.scheduled_pickup_date, endDate));
long count = secureReturnService.countByFilter(filter);
```

---

**Created**: 2026-03-01  
**Purpose**: Code examples for using Secure_Return_Service  
**Status**: Complete ✅
