# Customer Order Status Guide

## Overview
Customers will see actual secure return statuses with user-friendly messages instead of grouped categories. This provides transparency while keeping the interface simple.

## Customer-Facing Status Messages

### Regular Order Flow
| Internal Status | Customer Sees | Description |
|----------------|---------------|-------------|
| ORDER_PLACED | Order Placed | Order has been placed |
| TRANSACTION_PROCESSED | Payment Success | Payment successful |
| ORDER_ACCEPTED | Order Confirmed | Seller confirmed the order |
| READY_FOR_PICK_UP | Ready For Pick Up | Order ready for pickup |
| RIDER_ASSIGNED | Delivery Partner Assigned | Delivery partner assigned |
| OUT_FOR_DELIVERY | Out For Delivery | Order is on the way |
| DELIVERED | Delivered | Order delivered successfully |

### SecuRe Buy Flow (Items Secured)
| Internal Status | Customer Sees | Description |
|----------------|---------------|-------------|
| ITEM_SECURED | Items Secured | Items are secured with seller |

### SecuRe Return Flow (Return Pickup)
| Internal Status | Customer Sees | Description |
|----------------|---------------|-------------|
| SECURE_RETURN_SCHEDULED | Return Pickup Scheduled | Return pickup has been scheduled |
| SECURE_RETURN_INITIATED | Return Pickup Initiated | Return pickup process started |
| ORDER_CANCELLED_FOR_SECURE_RETURN | Return Pickup Scheduled | Order cancelled for return |
| RIDER_ASSIGNED_FOR_SECURE_RETURN | Pickup Partner Assigned | Pickup partner assigned |
| ITEMS_PICKED_UP_FOR_SECURE_RETURN | Items Picked Up | Items picked up for return |

### SecuRe Return Flow (After Pickup)
| Internal Status | Customer Sees | Description |
|----------------|---------------|-------------|
| SECURE_RETURN_COMPLETED | Return Completed | Items returned to seller |
| SECURE_RETURN_APPRAISED | Return Under Review | Seller is reviewing returned items |
| SECURE_REFUND_PENDING | Refund Processing | Refund is being processed |
| SECURE_BUY_REFUNDED | Refund Completed | Refund has been completed |
| SECURE_RETURN_FAILED | Return Pickup Failed | Return pickup failed |

### Cancellation & Refund Flow
| Internal Status | Customer Sees | Description |
|----------------|---------------|-------------|
| ORDER_REJECTED | Order Rejected | Order was rejected |
| ORDER_CANCELLED | Order Cancelled | Order was cancelled |
| PENDING_REFUND | Refund Pending | Refund is pending |
| REFUND_REQUESTED | Refund Requested | Refund has been requested |
| REFUND_FAILED | Refund Failed | Refund failed |
| FULLY_REFUNDED | Fully Refunded | Full refund completed |
| PARTIALLY_REFUNDED | Partially Refunded | Partial refund completed |

## Customer Order Filter Categories

Instead of grouping secure statuses under one category, customers will see individual statuses. The filter logic should NOT combine them:

### Current Implementation (WRONG ❌)
```java
case "SECURE_RETURN" -> List.of(
    OrderStatus.SECURE_RETURN_COMPLETED,
    OrderStatus.SECURE_RETURN_APPRAISED,
    // ... all secure statuses
);
```

### Correct Implementation (RIGHT ✅)
Customers should be able to filter by individual statuses or see all orders. The `getByCustomerStatus` method should be removed or simplified to only handle high-level groupings like:
- PENDING (payment/confirmation)
- PROCESSING (in transit/pickup)
- DELIVERED (completed)
- CANCELLED (rejected/refunded)

For specific statuses, customers should use the actual status value.

## API Usage Examples

### Get All Orders
```bash
POST /order/store/find
{
  "order_status": null
}
```
Returns: All orders with their actual status

### Get Orders by Specific Status
```bash
POST /order/store/find
{
  "order_status": "Return Under Review"  # Customer-facing status
}
```
Returns: Only SECURE_RETURN_APPRAISED orders

### Get Orders by Category (Optional)
```bash
POST /order/store/find
{
  "order_status": "PROCESSING"
}
```
Returns: All orders in processing state (delivery + return pickup)

## UI/UX Recommendations

### Order List View
```
┌─────────────────────────────────────────┐
│ Your Orders                             │
├─────────────────────────────────────────┤
│ Order #ORD001                           │
│ Status: Items Secured                   │
│ 2 items • ₹2,500                        │
│ [View Details]                          │
├─────────────────────────────────────────┤
│ Order #ORD002                           │
│ Status: Refund Processing               │
│ 1 item • ₹1,200                         │
│ Refund: ₹600                            │
│ [View Details]                          │
├─────────────────────────────────────────┤
│ Order #ORD003                           │
│ Status: Return Pickup Scheduled         │
│ 3 items • ₹3,000                        │
│ Pickup: Mar 10, 09:00-12:00             │
│ [Reschedule]                            │
└─────────────────────────────────────────┘
```

### Order Detail View
```
┌─────────────────────────────────────────┐
│ Order #ORD002                           │
├─────────────────────────────────────────┤
│ Status: Refund Processing               │
│                                         │
│ Timeline:                               │
│ ✓ Items Secured (Mar 1)                │
│ ✓ Return Pickup Scheduled (Mar 5)      │
│ ✓ Items Picked Up (Mar 6)              │
│ ✓ Return Completed (Mar 6)             │
│ ✓ Return Under Review (Mar 7)          │
│ ● Refund Processing (Mar 8)            │
│   Expected completion: Mar 10           │
│                                         │
│ Refund Details:                         │
│ Original Amount: ₹1,200                 │
│ Refund Amount: ₹600 (50%)               │
│ Reason: Good condition (Rating: 5)      │
│                                         │
│ [Track Refund] [Contact Support]       │
└─────────────────────────────────────────┘
```

### Filter Options
```
┌─────────────────────────────────────────┐
│ Filter Orders                           │
├─────────────────────────────────────────┤
│ ○ All Orders                            │
│ ○ Pending (Payment/Confirmation)        │
│ ○ Processing (Delivery/Pickup)          │
│ ○ Delivered                             │
│ ○ Items Secured                         │
│ ○ Returns & Refunds                     │
│   ├─ Return Pickup Scheduled            │
│   ├─ Items Picked Up                    │
│   ├─ Return Under Review                │
│   ├─ Refund Processing                  │
│   └─ Refund Completed                   │
│ ○ Cancelled                             │
└─────────────────────────────────────────┘
```

## Status Color Coding

### Green (Success)
- Delivered
- Items Secured
- Refund Completed

### Blue (In Progress)
- Return Pickup Scheduled
- Return Pickup Initiated
- Pickup Partner Assigned
- Items Picked Up
- Return Completed
- Return Under Review
- Refund Processing

### Orange (Action Required)
- Return Pickup Scheduled (near pickup time)
- Return Pickup Failed

### Red (Failed/Cancelled)
- Order Rejected
- Order Cancelled
- Refund Failed

### Gray (Pending)
- Order Placed
- Payment Pending

## Status Grouping for Analytics

While customers see individual statuses, analytics can group them:

### Active Orders
- ORDER_PLACED → ORDER_ACCEPTED → READY_FOR_PICK_UP → RIDER_ASSIGNED → OUT_FOR_DELIVERY

### Completed Orders
- DELIVERED
- ITEM_SECURED
- REFUND_COMPLETED

### Return Process
- SECURE_RETURN_SCHEDULED → SECURE_RETURN_INITIATED → RIDER_ASSIGNED_FOR_SECURE_RETURN → ITEMS_PICKED_UP_FOR_SECURE_RETURN → SECURE_RETURN_COMPLETED → SECURE_RETURN_APPRAISED → SECURE_REFUND_PENDING → SECURE_BUY_REFUNDED

### Failed/Cancelled
- ORDER_REJECTED
- ORDER_CANCELLED
- SECURE_RETURN_FAILED
- REFUND_FAILED

## Benefits of Showing Actual Status

1. **Transparency**: Customers know exactly what's happening
2. **Reduced Support Queries**: Clear status reduces confusion
3. **Better UX**: Customers can track progress step-by-step
4. **Trust Building**: Detailed status builds confidence
5. **Actionable**: Customers know when action is needed (e.g., be available for pickup)

## Implementation Notes

### Backend
- Keep `getByCustomerStatus` for backward compatibility with high-level categories
- Add support for filtering by exact customer_status string
- Return actual status in API responses
- Include status timeline in order details

### Frontend
- Display customer_status field (user-friendly message)
- Show status timeline/progress bar
- Use color coding for visual clarity
- Group similar statuses in filters but show individual statuses in list
- Add tooltips for status explanations

### Mobile App
- Push notifications for status changes
- Rich notifications with status-specific actions
- Status-based quick actions (reschedule, track, contact)

## Testing Checklist

- [ ] All secure statuses display correct customer-facing messages
- [ ] Status timeline shows correct progression
- [ ] Filters work with individual statuses
- [ ] API returns correct status for each order
- [ ] Mobile app shows correct status
- [ ] Push notifications use customer-friendly messages
- [ ] Status colors are consistent across platforms
- [ ] Tooltips/help text are clear and accurate
