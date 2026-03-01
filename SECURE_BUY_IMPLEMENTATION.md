# Secure Buy/Return System - Implementation Guide

## Overview
This document describes the new dedicated table structure for handling secure buy/return functionality, separating it from the main `Order_Details` and `Order_Item` tables.

## Architecture

### New Database Collection: `secure_returns`

**Purpose**: Manage the complete lifecycle of secure buy/return operations independently from regular orders.

---

## Data Model

### 1. Main Entity: `Secure_Return`

**Collection**: `secure_returns`

**Key Features**:
- Independent lifecycle management
- Complete audit trail with status history
- Financial tracking with refund management
- Delivery partner integration
- Reschedule support

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Primary key |
| `order_id` | String | Reference to Order_Details |
| `order_code` | String | Display code |
| `user_id` | String | Customer ID |
| `seller_id` | String | Seller ID |
| `secure_order_code` | String | Unique code (e.g., SEC-ORD-MAR2026-123456) |
| `status` | SecureReturnStatus | Current status |
| `status_id` | Integer | Status ID |
| `status_history` | List<Secure_Status_History> | Complete audit trail |
| `scheduled_pickup_date` | LocalDate | Scheduled date |
| `scheduled_time_slot` | TimeSlot | Morning/Afternoon/Evening |
| `actual_pickup_time` | LocalDateTime | Actual pickup timestamp |
| `reschedule_count` | Integer | Number of reschedules |
| `max_reschedule_allowed` | Integer | Max allowed (default: 2) |
| `pickup_address` | AddressDTO | Customer address |
| `delivery_address` | AddressDTO | Seller address |
| `dp_order_id` | String | Porter order ID |
| `dp_tracking_url` | String | Tracking URL |
| `estimated_delivery_charges` | Long | Estimated charges |
| `actual_delivery_charges` | Long | Actual charges |
| `items` | List<Secure_Return_Item> | Returned items |
| `total_estimated_refund` | Long | Optimistic total (50% of all items) |
| `total_actual_refund` | Long | After appraisal |
| `refund_transaction_id` | String | PhonePe/Razorpay ID |
| `refund_status` | RefundStatus | Refund lifecycle |
| `refund_initiated_at` | LocalDateTime | Refund start time |
| `refund_completed_at` | LocalDateTime | Refund completion time |
| `failure_reason` | String | Failure details |
| `rejection_remarks` | String | Rejection reason |
| `version` | Long | Optimistic locking |

---

### 2. Embedded Class: `Secure_Return_Item`

**Purpose**: Track individual items within a secure return.

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `order_item_id` | String | Link to Order_Item |
| `product_id` | String | Product reference |
| `product_code` | String | Product code |
| `product_name` | String | Product name |
| `product_image_url` | String | Original product image |
| `quantity` | Long | Quantity returned |
| `selling_price_after_discount` | Long | Per unit price |
| `total_item_cost` | Long | quantity × price |
| `appraisal_grade` | AppraisalGrade | A, B, or C |
| `appraisal_remarks` | String | Seller's comments |
| `returned_item_image_urls` | List<String> | Photos of returned items |
| `appraised_at` | LocalDateTime | Appraisal timestamp |
| `appraised_by` | String | Seller user ID |
| `estimated_refund_amount` | Long | 50% of total_item_cost |
| `actual_refund_amount` | Long | Based on grade |
| `refund_percentage` | Double | 50%, 30%, or 0% |
| `item_status` | SecureItemStatus | Item status |

---

## Enums

### 1. AppraisalGrade

**Purpose**: Seller's assessment of returned item condition.

| Grade | Description | Refund % | Calculation |
|-------|-------------|----------|-------------|
| **A** | Excellent Condition | 50% | `selling_price_after_discount × 50%` |
| **B** | Good Condition | 30% | `selling_price_after_discount × 30%` |
| **C** | Poor/Damaged | 0% | No refund |

**Example**:
```
Product: ₹1000 (after discount), Quantity: 2
Total: ₹2000

Grade A → ₹1000 refund (50%)
Grade B → ₹600 refund (30%)
Grade C → ₹0 refund (0%)
```

---

### 2. SecureReturnStatus

**Lifecycle States**:

1. `SCHEDULED` - Initial state after scheduling
2. `PICKUP_PENDING` - Awaiting pickup
3. `PICKUP_ASSIGNED` - Delivery partner assigned
4. `PICKED_UP` - Items collected
5. `IN_TRANSIT` - On the way to seller
6. `DELIVERED_TO_SELLER` - Reached seller
7. `UNDER_APPRAISAL` - Seller reviewing items
8. `APPRAISAL_COMPLETED` - Review done
9. `REFUND_PENDING` - Refund processing
10. `REFUND_COMPLETED` - Money returned ✅
11. `FAILED` - Process failed ❌
12. `CANCELLED` - User/system cancelled ❌
13. `RESCHEDULED` - Pickup rescheduled

---

### 3. SecureItemStatus

**Item-Level States**:

- `PENDING_APPRAISAL` - Awaiting seller review
- `APPROVED_GRADE_A` - Grade A (50% refund)
- `APPROVED_GRADE_B` - Grade B (30% refund)
- `REJECTED_GRADE_C` - Grade C (No refund)
- `REFUND_PROCESSED` - Refund completed

---

### 4. RefundStatus

**Refund Lifecycle**:

- `NOT_INITIATED` - Not started
- `PENDING` - Initiated
- `PROCESSING` - Payment gateway processing
- `COMPLETED` - Done ✅
- `FAILED` - Failed ❌
- `PARTIAL` - Partial refund

---

## API Flow

### 1. Initiate Secure Return (Customer)

**Endpoint**: `POST /api/secure-return/initiate`

**Request**:
```json
{
  "order_id": "65f1a2b3c4d5e6f7g8h9i0j1",
  "order_item_ids": ["item1", "item2"],
  "address_id": "addr123",
  "return_date": "2026-03-05",
  "time_slot": "MORNING",
  "req_user_id": "user123"
}
```

**Process**:
1. Validate customer and order
2. Check order is DELIVERED
3. Validate items belong to order
4. Check seller business hours
5. Get delivery quote from Porter
6. Create `Secure_Return` record
7. Set status to `SCHEDULED`
8. Calculate estimated refunds (50% for all items)
9. Update order items to `SECURE_RETURN_SCHEDULED`

---

### 2. Process Pickup (Cron/Webhook)

**Trigger**: Scheduled date/time or Porter webhook

**Process**:
1. Create Porter order
2. Update `dp_order_id`
3. Set status to `PICKUP_ASSIGNED`
4. Send notification to customer

---

### 3. Appraise Items (Seller)

**Endpoint**: `POST /api/secure-return/appraise`

**Request**:
```json
{
  "secure_return_id": "sec123",
  "req_user_id": "seller123",
  "item_appraisals": [
    {
      "order_item_id": "item1",
      "grade": "A",
      "remarks": "Excellent condition, original packaging intact",
      "image_urls": [
        "https://s3.../item1-front.jpg",
        "https://s3.../item1-back.jpg"
      ]
    },
    {
      "order_item_id": "item2",
      "grade": "B",
      "remarks": "Good condition, minor scratches",
      "image_urls": ["https://s3.../item2.jpg"]
    }
  ]
}
```

**Process**:
1. Validate seller owns the return
2. Validate all items are present
3. Apply appraisal to each item:
   - Set `appraisal_grade`
   - Set `appraisal_remarks`
   - Store `returned_item_image_urls`
   - Calculate `actual_refund_amount`
   - Update `item_status`
4. Calculate `total_actual_refund`
5. Set status to `APPRAISAL_COMPLETED`

---

### 4. Process Refund (System)

**Trigger**: After appraisal completion

**Process**:
1. Check `total_actual_refund > 0`
2. Initiate PhonePe/Razorpay refund
3. Set `refund_status` to `PENDING`
4. Store `refund_transaction_id`
5. Set status to `REFUND_PENDING`
6. On success:
   - Set `refund_status` to `COMPLETED`
   - Set status to `REFUND_COMPLETED`
   - Record `refund_completed_at`

---

### 5. Reschedule Pickup (Customer)

**Endpoint**: `POST /api/secure-return/reschedule`

**Request**:
```json
{
  "secure_return_id": "sec123",
  "new_pickup_date": "2026-03-07",
  "new_time_slot": "AFTERNOON",
  "req_user_id": "user123"
}
```

**Process**:
1. Check `canReschedule()` (max 2 times)
2. Validate new date/time
3. Update `scheduled_pickup_date` and `scheduled_time_slot`
4. Increment `reschedule_count`
5. Set status to `RESCHEDULED`
6. Cancel existing Porter order (if any)

---

## Database Indexes

**Recommended Indexes**:

```javascript
db.secure_returns.createIndex({ "order_id": 1 }, { unique: true })
db.secure_returns.createIndex({ "user_id": 1 })
db.secure_returns.createIndex({ "seller_id": 1 })
db.secure_returns.createIndex({ "secure_order_code": 1 }, { unique: true })
db.secure_returns.createIndex({ "status_id": 1 })
db.secure_returns.createIndex({ "scheduled_pickup_date": 1 })
db.secure_returns.createIndex({ "dp_order_id": 1 })
db.secure_returns.createIndex({ "refund_status": 1 })
```

---

## Files Created

### Domain Model (common-libs)
- ✅ `AppraisalGrade.java` - A/B/C rating enum
- ✅ `SecureReturnStatus.java` - Lifecycle status enum
- ✅ `SecureItemStatus.java` - Item status enum
- ✅ `RefundStatus.java` - Refund status enum
- ✅ `Secure_Status_History.java` - Status history bean
- ✅ `Secure_Return_Item.java` - Item bean
- ✅ `Secure_Return.java` - Main entity
- ✅ `Secure_Return_Repository.java` - Repository
- ✅ `Secure_Return_Service.java` - CRUD service

### Portal Service
- ✅ `SecureReturnDTO.java` - Response DTO
- ✅ `SecureReturnItemDTO.java` - Item DTO
- ✅ `AppraisalRequestDTO.java` - Appraisal request
- ✅ `SecureReturnMapper.java` - Entity to DTO mapper

### To Be Updated
- ⏳ `SecureReturnService.java` - Business logic (use new table)
- ⏳ `SecureReturnDataService.java` - Data service
- ⏳ `InitiateSecureBean.java` - Request bean
- ⏳ `AppraiseSecureReturn.java` - Appraisal bean
- ⏳ `RescheduleSecureBean.java` - Reschedule bean

---

## Benefits

1. ✅ **Separation of Concerns**: Orders and secure returns are independent
2. ✅ **Clean Schema**: No nullable secure fields in order tables
3. ✅ **Better Queries**: Direct queries instead of filtering by type
4. ✅ **Scalability**: Easy to add features without bloating order tables
5. ✅ **Audit Trail**: Dedicated status history
6. ✅ **Image Storage**: Multiple photos per item
7. ✅ **Clear Grading**: A/B/C system with automatic refund calculation
8. ✅ **Financial Tracking**: Complete refund lifecycle management

---

## Next Steps

1. ✅ Create all entities, enums, and beans
2. ✅ Create repository and service layers
3. ✅ Create DTOs and mappers
4. ⏳ Update business logic services
5. ⏳ Update controllers
6. ⏳ Create database indexes
7. ⏳ Test end-to-end flow
8. ⏳ Deploy and monitor

---

## Migration Notes

**No migration needed** - This is a fresh implementation as there's no existing secure buy data.

The old fields in `Order_Details` and `Order_Item` can be removed in a future cleanup:
- Remove from `Order_Details`: All `secure_*` and `secured_*` fields
- Remove from `Order_Item`: `estimated_secure_amount`, `actual_secure_amount`, `secure_item_rating`
- Keep `type` field in `Order_Item` for filtering BUY vs SECURE items

---

**Created**: 2026-03-01
**Version**: 1.0
**Status**: Implementation Complete ✅
