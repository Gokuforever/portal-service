# Order Filter Builder Updates

## Changes Required

Replace the `getByCustomerStatus` method in `OrderFilterBuilder.java` (lines 275-301) with:

```java
/**
 * Get OrderStatus list from customer status string
 */
private List<OrderStatus> getByCustomerStatus(String customerStatus) {
    log.debug("Getting order statuses for customer status: {}", customerStatus);

    return switch (customerStatus.toUpperCase()) {
        case "PENDING" -> List.of(
                OrderStatus.TRANSACTION_PROCESSED,
                OrderStatus.ORDER_ACCEPTED
        );
        case "PROCESSING" -> List.of(
                OrderStatus.READY_FOR_PICK_UP,
                OrderStatus.RIDER_ASSIGNED,
                OrderStatus.OUT_FOR_DELIVERY,
                // Secure return pickup in progress
                OrderStatus.SECURE_RETURN_SCHEDULED,
                OrderStatus.SECURE_RETURN_INITIATED,
                OrderStatus.ORDER_CANCELLED_FOR_SECURE_RETURN,
                OrderStatus.RIDER_ASSIGNED_FOR_SECURE_RETURN,
                OrderStatus.ITEMS_PICKED_UP_FOR_SECURE_RETURN
        );
        case "DELIVERED" -> List.of(
                OrderStatus.DELIVERED,
                OrderStatus.ITEM_SECURED  // SecuRe buy items secured
        );
        case "CANCELLED" -> List.of(
                OrderStatus.ORDER_REJECTED,
                OrderStatus.PENDING_REFUND,
                OrderStatus.REFUND_REQUESTED,
                OrderStatus.REFUND_FAILED,
                OrderStatus.FULLY_REFUNDED
        );
        case "SECURE_RETURN" -> List.of(
                // Secure return completed and refund processing
                OrderStatus.SECURE_RETURN_COMPLETED,
                OrderStatus.SECURE_RETURN_APPRAISED,
                OrderStatus.SECURE_REFUND_PENDING,
                OrderStatus.SECURE_BUY_REFUNDED,
                OrderStatus.SECURE_RETURN_FAILED
        );
        default -> {
            log.warn("Unknown customer status: {}", customerStatus);
            yield List.of();
        }
    };
}
```

## Summary of Changes

### 1. New Order Status Added
- **SECURE_BUY_REFUNDED (28)**: For secure buy orders that received partial refund

### 2. Seller Allowed Statuses Updated
Added all secure-related statuses to `SELLER_ALLOWED_STATUS`:
- SECURE_RETURN_SCHEDULED
- SECURE_RETURN_INITIATED
- ORDER_CANCELLED_FOR_SECURE_RETURN
- RIDER_ASSIGNED_FOR_SECURE_RETURN
- ITEMS_PICKED_UP_FOR_SECURE_RETURN
- SECURE_RETURN_COMPLETED
- SECURE_RETURN_APPRAISED
- SECURE_REFUND_PENDING
- SECURE_BUY_REFUNDED
- ITEM_SECURED

### 3. Customer Status Mapping Updated

#### PROCESSING
Now includes secure return pickup statuses:
- SECURE_RETURN_SCHEDULED
- SECURE_RETURN_INITIATED
- ORDER_CANCELLED_FOR_SECURE_RETURN
- RIDER_ASSIGNED_FOR_SECURE_RETURN
- ITEMS_PICKED_UP_FOR_SECURE_RETURN

#### DELIVERED
Now includes:
- ITEM_SECURED (for SecuRe buy items)

#### New Category: SECURE_RETURN
For completed secure returns and refund processing:
- SECURE_RETURN_COMPLETED
- SECURE_RETURN_APPRAISED
- SECURE_REFUND_PENDING
- SECURE_BUY_REFUNDED
- SECURE_RETURN_FAILED

### 4. SecureRefundStatusCron Updated
- Uses `SECURE_BUY_REFUNDED` status instead of `PARTIALLY_REFUNDED`
- Sets `has_secure_return_impact = true` for settlement tracking

## API Usage

### For Customers

**Get all orders:**
```
POST /order/store/find
{
  "order_status": null  // All orders
}
```

**Get delivered orders (including secured items):**
```
POST /order/store/find
{
  "order_status": "DELIVERED"
}
```
Returns: DELIVERED + ITEM_SECURED

**Get orders in processing (including secure return pickups):**
```
POST /order/store/find
{
  "order_status": "PROCESSING"
}
```
Returns: READY_FOR_PICK_UP, RIDER_ASSIGNED, OUT_FOR_DELIVERY, SECURE_RETURN_SCHEDULED, etc.

**Get secure return orders:**
```
POST /order/store/find
{
  "order_status": "SECURE_RETURN"
}
```
Returns: SECURE_RETURN_COMPLETED, SECURE_RETURN_APPRAISED, SECURE_REFUND_PENDING, SECURE_BUY_REFUNDED, SECURE_RETURN_FAILED

### For Sellers

**Get all orders:**
```
POST /order/find
{
  "order_status": null
}
```
Returns all orders in SELLER_ALLOWED_STATUS (now includes all secure statuses)

**Get specific status:**
```
POST /order/find
{
  "order_status": "SecuRe Buy Refunded"
}
```
Returns only SECURE_BUY_REFUNDED orders

## Weekly Settlement Impact

Orders with status `SECURE_BUY_REFUNDED` will be:
1. Identified by `has_secure_return_impact = true`
2. Included in weekly settlement calculation
3. Refund amount deducted from seller's payout
4. Listed in "SecuRe Return Deductions" section of settlement report

## Testing

Test the following scenarios:

1. **Customer View**:
   - [ ] DELIVERED status shows both DELIVERED and ITEM_SECURED orders
   - [ ] PROCESSING status shows secure return pickup orders
   - [ ] SECURE_RETURN status shows refund processing orders
   - [ ] All secure statuses are visible to customers

2. **Seller View**:
   - [ ] All secure statuses are visible in order list
   - [ ] Can filter by specific secure statuses
   - [ ] SECURE_BUY_REFUNDED orders appear correctly

3. **Settlement**:
   - [ ] SECURE_BUY_REFUNDED orders are identified for settlement
   - [ ] Refund amounts are correctly deducted
   - [ ] Weekly report shows secure return deductions

4. **Status Flow**:
   - [ ] Secure return flow works end-to-end
   - [ ] Status transitions are correct
   - [ ] Refund cron updates status to SECURE_BUY_REFUNDED
