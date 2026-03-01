# Secure Return Appraisal and Refund Flow

## Overview
Complete flow for appraising secure returns and processing partial refunds through PhonePe.

## Order Status Flow

```
SECURE_RETURN_COMPLETED
         ↓
    [Seller Appraises]
         ↓
SECURE_RETURN_APPRAISED
         ↓
  [PhonePe Refund Initiated]
         ↓
SECURE_REFUND_PENDING
         ↓
  [PhonePe Processes Refund]
         ↓
PARTIALLY_REFUNDED (Success)
    or
REFUND_FAILED (Failure)
```

## New Order Statuses

### SECURE_RETURN_APPRAISED (26)
- **When**: After seller appraises the returned product
- **Description**: "SecuRe Return Appraised"
- **Next Steps**: Initiate PhonePe refund

### SECURE_REFUND_PENDING (27)
- **When**: After PhonePe refund is successfully initiated
- **Description**: "SecuRe Refund Pending"
- **Next Steps**: Wait for PhonePe to process refund (check via cron)

## API Flow

### 1. Seller Appraises Return

**Endpoint**: `POST /secure/appraise`

**Request**:
```json
{
  "order_id": "ORDER_ID",
  "rating": 4,
  "amount": 1500.50,
  "remark": "Good condition"
}
```

**Process**:
1. Validate seller and order status (must be `SECURE_RETURN_COMPLETED`)
2. Validate rating (1-5) - **MANDATORY**
3. Calculate refund amount:
   - If `amount` provided: Convert rupees to paise using `CommonUtils.rupeeToPaise()`
   - Else: Calculate from rating: `(totalPaise * rating * 10) / 100`
4. Update order status to `SECURE_RETURN_APPRAISED`
5. Update order items with rating and status
6. Initiate PhonePe refund

### 2. PhonePe Refund Initiation

**Method**: `PhonePeUtility.partialRefund()`

**Parameters**:
- `merchantRefundId`: Unique refund transaction ID (format: `SECURE-REFUND-{orderId}-{timestamp}`)
- `originalMerchantOrderId`: Original order ID
- `refundAmount`: Amount in paise (Long)

**Success Flow**:
1. PhonePe returns `RefundResponse`
2. Store `refund_transaction_id` in order
3. Update order status to `SECURE_REFUND_PENDING`
4. Log success

**Failure Flow**:
1. Log error
2. Keep order in `SECURE_RETURN_APPRAISED` status
3. Manual intervention required

## Rating System

| Rating | Condition | Refund % | Example (₹2000) |
|--------|-----------|----------|-----------------|
| 5 | Excellent | 50% | ₹1000 |
| 4 | Good | 40% | ₹800 |
| 3 | Fair | 30% | ₹600 |
| 2 | Poor | 20% | ₹400 |
| 1 | Very Poor | 10% | ₹200 |

## Amount Calculation

### With Rating Only
```java
// Sum all item prices (in paise)
Long totalPaise = items.stream()
    .map(Order_Item::getSelling_price_after_discount)
    .reduce(0L, Long::sum);

// Calculate refund (rating * 10 = percentage)
Long refundPaise = (totalPaise * rating * 10) / 100;
```

**Example**:
- Item 1: 100000 paise (₹1000)
- Item 2: 50000 paise (₹500)
- Total: 150000 paise (₹1500)
- Rating: 4 (40%)
- **Refund: 60000 paise (₹600)**

### With Custom Amount (Override)
```java
// Convert rupees to paise
Long refundPaise = CommonUtils.rupeeToPaise(amount);
```

**Example**:
- Amount: ₹750.50
- **Refund: 75050 paise**

## Database Updates

### Order_Details
```java
order.setStatus(OrderStatus.SECURE_RETURN_APPRAISED, sellerId);
// After PhonePe success:
order.setRefund_transaction_id(refundTxnId);
order.setStatus(OrderStatus.SECURE_REFUND_PENDING, sellerId);
```

### Order_Item
```java
item.setSecure_item_rating(rating);
item.setStatus(OrderStatus.SECURE_RETURN_APPRAISED, sellerId);
```

## Error Handling

### Validation Errors

**Missing Rating**:
```json
{
  "code": "SE_0278",
  "error": "Either rating or amount must be provided.",
  "message": "Please provide either a rating (1-5) or a refund amount."
}
```

**Invalid Rating**:
```json
{
  "code": "SE_0279",
  "error": "Rating must be between 1 and 5.",
  "message": "Rating must be between 1 and 5."
}
```

**Invalid Order Status**:
```json
{
  "code": "SE_0212",
  "error": "Invalid order state for return.",
  "message": "Something went wrong, please contact customer care."
}
```

### PhonePe Refund Errors

**Refund Initiation Failed**:
- Order remains in `SECURE_RETURN_APPRAISED` status
- Error logged for manual intervention
- Can be retried via admin panel or cron job

**Network/Timeout Errors**:
- Exception caught and logged
- Order remains in `SECURE_RETURN_APPRAISED` status
- Requires manual retry

## Refund Status Monitoring

### Cron Job (To Be Implemented)
Monitor orders in `SECURE_REFUND_PENDING` status and check refund status with PhonePe:

```java
@Scheduled(cron = "0 0 */2 * * *") // Every 2 hours
public void checkSecureRefundStatus() {
    // Find orders with SECURE_REFUND_PENDING status
    List<Order_Details> orders = findOrdersByStatus(OrderStatus.SECURE_REFUND_PENDING);
    
    for (Order_Details order : orders) {
        // Check refund status with PhonePe
        Optional<RefundStatusResponse> status = 
            phonePeUtility.refundStatus(order.getRefund_transaction_id());
        
        if (status.isPresent()) {
            if (status.get().isSuccess()) {
                // Update to PARTIALLY_REFUNDED
                order.setStatus(OrderStatus.PARTIALLY_REFUNDED, "REFUND_STATUS_CRON");
            } else if (status.get().isFailed()) {
                // Update to REFUND_FAILED
                order.setStatus(OrderStatus.REFUND_FAILED, "REFUND_STATUS_CRON");
            }
            // else: Still pending, check again later
        }
    }
}
```

## Complete Example

### Scenario: Customer returns 2 items, seller rates as Good (4)

**Step 1: Items Returned**
- Order status: `SECURE_RETURN_COMPLETED`
- Item 1: ₹1200 (120000 paise)
- Item 2: ₹800 (80000 paise)
- Total: ₹2000 (200000 paise)

**Step 2: Seller Appraises**
```bash
curl -X POST http://localhost:8080/secure/appraise \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SELLER_TOKEN" \
  -d '{
    "order_id": "ORDER123",
    "rating": 4,
    "remark": "Good condition, minor usage"
  }'
```

**Step 3: System Calculates Refund**
- Rating: 4 → 40%
- Refund: 200000 × 40% = 80000 paise (₹800)

**Step 4: Order Updated**
- Status: `SECURE_RETURN_APPRAISED`
- Items rating: 4
- Items status: `SECURE_RETURN_APPRAISED`

**Step 5: PhonePe Refund Initiated**
- Refund ID: `SECURE-REFUND-ORDER123-1709134200000`
- Amount: 80000 paise
- Status: `SECURE_REFUND_PENDING`

**Step 6: PhonePe Processes (via cron)**
- Cron checks status every 2 hours
- Once successful: Status → `PARTIALLY_REFUNDED`
- Customer receives ₹800 refund

## Testing Checklist

- [ ] Appraise with rating 5 (50%)
- [ ] Appraise with rating 1 (10%)
- [ ] Appraise with custom amount
- [ ] Appraise with rating + custom amount (amount should override)
- [ ] Invalid rating (0, 6, -1)
- [ ] Missing rating
- [ ] Invalid order status
- [ ] PhonePe refund success
- [ ] PhonePe refund failure
- [ ] Network timeout during refund
- [ ] Multiple items calculation
- [ ] Amount conversion (rupees to paise)

## Dependencies

### Services
- `SecureReturnService` - Main appraisal logic
- `PhonePeUtility` - PhonePe integration
- `Order_Details_Service` - Order updates
- `Order_Item_Service` - Item updates

### Utilities
- `CommonUtils.rupeeToPaise()` - Convert rupees to paise

### Enums
- `OrderStatus.SECURE_RETURN_APPRAISED`
- `OrderStatus.SECURE_REFUND_PENDING`
- `ResponseCode.MISSING_RATING_OR_AMOUNT`
- `ResponseCode.INVALID_RATING_RANGE`

## Future Enhancements

1. **Add Order Fields**:
   - `secure_refund_amount` (Long) - Store refund amount in paise
   - `secure_appraisal_rating` (Integer) - Store rating
   - `secure_appraisal_remark` (String) - Store remarks

2. **Retry Mechanism**:
   - Auto-retry failed refunds
   - Track retry count
   - Alert after max retries

3. **Customer Notifications**:
   - Email when appraised
   - Email when refund initiated
   - Email when refund completed

4. **Admin Dashboard**:
   - View pending appraisals
   - View failed refunds
   - Manual refund retry

5. **Analytics**:
   - Average rating by seller
   - Refund success rate
   - Time to refund completion
