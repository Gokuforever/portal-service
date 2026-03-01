# Secure Buy/Return System - Implementation Summary

## ✅ Implementation Complete

A dedicated table structure has been created to handle secure buy/return functionality, completely separated from the main `Order_Details` and `Order_Item` tables.

---

## 📊 What Was Created

### **1. Enums (4 files)**

#### `AppraisalGrade.java`
- **Grade A**: 50% refund (Excellent condition)
- **Grade B**: 30% refund (Good condition)  
- **Grade C**: 0% refund (Poor/Damaged)
- Automatic refund calculation based on `selling_price_after_discount`

#### `SecureReturnStatus.java`
- 13 lifecycle states from SCHEDULED to REFUND_COMPLETED
- Helper methods: `isTerminal()`, `canInitiateRefund()`, `canReschedule()`

#### `SecureItemStatus.java`
- Item-level status tracking
- Maps to appraisal grades (A/B/C)

#### `RefundStatus.java`
- Refund lifecycle: NOT_INITIATED → PENDING → PROCESSING → COMPLETED
- Supports retry logic for failed refunds

---

### **2. Beans (2 files)**

#### `Secure_Status_History.java`
- Tracks all status changes with timestamp, user, and remarks
- Complete audit trail

#### `Secure_Return_Item.java`
- Individual item details within a secure return
- **Key Features**:
  - Product reference (order_item_id, product_id, code, name, image)
  - Quantity and pricing
  - **Appraisal data**: grade, remarks, images, timestamp, appraiser
  - **Refund calculation**: estimated vs actual amounts
  - Item status tracking

---

### **3. Main Entity**

#### `Secure_Return.java`
- **Collection**: `secure_returns`
- **Key Features**:
  - Complete lifecycle management (scheduling → pickup → appraisal → refund)
  - Status history tracking
  - Reschedule support (max 2 times)
  - Delivery partner integration (Porter)
  - Financial tracking (estimated vs actual refunds)
  - Image storage for returned items
  - Optimistic locking with `@Version`

**Helper Methods**:
- `setStatus()` - Automatic history tracking
- `calculateTotalEstimatedRefund()` - Sum all items
- `calculateTotalActualRefund()` - After appraisal
- `areAllItemsAppraised()` - Check completion
- `canReschedule()` - Validate reschedule eligibility

---

### **4. Data Access Layer**

#### `Secure_Return_Repository.java`
- Find by: order_id, user_id, seller_id, secure_order_code, dp_order_id
- Find by scheduled_pickup_date and status_id
- Existence checks

#### `Secure_Return_Service.java`
- Extends `GenericEntityServiceImpl` (follows codebase pattern)
- Provides validation hooks: `validateBeforeCreate`, `validateBeforeUpdate`, `validateBeforeDelete`
- Inherits CRUD operations: `create`, `update`, `repoFind`, `repoFindOne`, `countByFilter`
- Automatic repository injection via Spring
- Logging and validation

---

### **5. DTOs (3 files)**

#### `SecureReturnDTO.java`
- Complete secure return response
- Includes computed fields: `can_reschedule`, `total_items_count`
- Status descriptions for UI display

#### `SecureReturnItemDTO.java`
- Item-level response
- Includes grade and status descriptions

#### `AppraisalRequestDTO.java`
- Seller appraisal input
- Supports multiple items with grades, remarks, and images

---

### **6. Utilities**

#### `SecureReturnMapper.java`
- Entity → DTO conversion
- Handles null checks and collections
- Enriches DTOs with descriptions

---

## 🎯 Key Features

### **1. A/B/C Grading System**

```java
// Example: Product worth ₹2000 after discount
AppraisalGrade.A.calculateRefund(2000L) → ₹1000 (50%)
AppraisalGrade.B.calculateRefund(2000L) → ₹600 (30%)
AppraisalGrade.C.calculateRefund(2000L) → ₹0 (0%)
```

### **2. Image Storage**

Each item can have multiple images:
```java
item.setReturned_item_image_urls(Arrays.asList(
    "https://s3.../item-front.jpg",
    "https://s3.../item-back.jpg",
    "https://s3.../item-damage.jpg"
));
```

### **3. Appraisal Tracking**

Complete appraisal details:
- Grade (A/B/C)
- Remarks (seller's detailed comments)
- Images (photos of returned items)
- Timestamp (when appraised)
- Appraiser (seller user ID)

### **4. Automatic Refund Calculation**

```java
item.applyAppraisal(AppraisalGrade.B, "Minor scratches", "seller123");
// Automatically sets:
// - appraisal_grade = B
// - refund_percentage = 30.0
// - actual_refund_amount = total_item_cost * 30%
// - item_status = APPROVED_GRADE_B
```

### **5. Status History**

Every status change is tracked:
```java
secureReturn.setStatus(SecureReturnStatus.PICKED_UP, "porter-webhook", "Picked up successfully");
// Creates history entry with timestamp and user
```

### **6. Reschedule Management**

```java
if (secureReturn.canReschedule()) {
    // Update date and time slot
    secureReturn.incrementRescheduleCount();
    secureReturn.setStatus(SecureReturnStatus.RESCHEDULED, userId);
}
```

---

## 📁 File Structure

```
common-libs/
├── src/main/java/com/sorted/common/
│   ├── enums/
│   │   ├── AppraisalGrade.java ✅
│   │   ├── SecureReturnStatus.java ✅
│   │   ├── SecureItemStatus.java ✅
│   │   └── RefundStatus.java ✅
│   ├── beans/
│   │   ├── Secure_Status_History.java ✅
│   │   └── Secure_Return_Item.java ✅
│   ├── entity/
│   │   ├── mongo/
│   │   │   └── Secure_Return.java ✅
│   │   └── service/
│   │       └── Secure_Return_Service.java ✅
│   └── repository/
│       └── Secure_Return_Repository.java ✅

portal-service/
├── src/main/java/com/sorted/portal/
│   ├── request/beans/
│   │   └── AppraisalRequestDTO.java ✅
│   ├── response/beans/
│   │   ├── SecureReturnDTO.java ✅
│   │   └── SecureReturnItemDTO.java ✅
│   └── service/secure/
│       └── SecureReturnMapper.java ✅
├── SECURE_BUY_IMPLEMENTATION.md ✅
└── SECURE_BUY_SUMMARY.md ✅
```

---

## 🔄 Typical Flow

### **Customer Initiates Return**
1. Customer selects delivered order items
2. Chooses pickup address, date, and time slot
3. System creates `Secure_Return` with status `SCHEDULED`
4. Calculates estimated refund (50% for all items)
5. Updates order items to `SECURE_RETURN_SCHEDULED`

### **Pickup Process**
1. Cron/webhook triggers Porter order creation
2. Status → `PICKUP_ASSIGNED`
3. Porter picks up items
4. Status → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED_TO_SELLER`

### **Seller Appraises Items**
1. Seller reviews each item
2. Assigns grade (A/B/C) with remarks and photos
3. System calculates actual refund based on grades
4. Status → `APPRAISAL_COMPLETED`

### **Refund Processing**
1. System initiates PhonePe/Razorpay refund
2. Status → `REFUND_PENDING`
3. On success → `REFUND_COMPLETED`
4. Customer receives money

---

## 🗄️ Database Indexes (Recommended)

```javascript
db.secure_returns.createIndex({ "order_id": 1 }, { unique: true })
db.secure_returns.createIndex({ "user_id": 1 })
db.secure_returns.createIndex({ "seller_id": 1 })
db.secure_returns.createIndex({ "secure_order_code": 1 }, { unique: true })
db.secure_returns.createIndex({ "status_id": 1 })
db.secure_returns.createIndex({ "scheduled_pickup_date": 1 })
db.secure_returns.createIndex({ "dp_order_id": 1 })
```

---

## ✅ Build Status

**All files compiled successfully!**

```bash
mvn clean install -DskipTests
# BUILD SUCCESS ✅
```

---

## 📋 Next Steps

### **Phase 1: Update Business Logic** ⏳
- Update `SecureReturnService.java` to use new `Secure_Return` table
- Update `SecureReturnDataService.java` for queries
- Modify `InitiateSecureBean.java`, `AppraiseSecureReturn.java`, `RescheduleSecureBean.java`

### **Phase 2: Create/Update Controllers** ⏳
- Create endpoints for:
  - `POST /api/secure-return/initiate`
  - `POST /api/secure-return/appraise`
  - `POST /api/secure-return/reschedule`
  - `GET /api/secure-return/{id}`
  - `GET /api/secure-return/user/{userId}`
  - `GET /api/secure-return/seller/{sellerId}`

### **Phase 3: Update Crons** ⏳
- Update `SecurePickupReminderCron.java` to query new table
- Create cron for automatic Porter order creation
- Create cron for refund processing

### **Phase 4: Testing** ⏳
- Unit tests for all services
- Integration tests for complete flow
- Test reschedule logic
- Test appraisal and refund calculation

### **Phase 5: Cleanup** ⏳
- Remove secure-related fields from `Order_Details`:
  - `secured_time_slot`, `secured_date`, `max_secured_reschedule_count`
  - `secure_pickup_address`, `secure_delivery_address`
  - `secure_dp_order_id`, `secure_order_id`
  - `secure_return_failure_reason`, `secure_return_initiated`
- Remove from `Order_Item`:
  - `estimated_secure_amount`, `actual_secure_amount`
  - `secure_item_rating`, `item_review_remarks`
- Keep `type` field in `Order_Item` for BUY vs SECURE filtering

---

## 🎉 Benefits Achieved

1. ✅ **Clean Separation**: Secure returns have their own table
2. ✅ **No Migration Needed**: Fresh implementation (no existing data)
3. ✅ **Clear Grading**: A/B/C system with automatic refund calculation
4. ✅ **Image Storage**: Multiple photos per item for evidence
5. ✅ **Complete Audit Trail**: Status history with timestamps
6. ✅ **Reschedule Support**: Built-in with max limit
7. ✅ **Financial Tracking**: Estimated vs actual refunds
8. ✅ **Scalable**: Easy to add features without touching order tables

---

**Created**: 2026-03-01  
**Status**: Core Implementation Complete ✅  
**Build**: SUCCESS ✅  
**Files Created**: 14  
**Lines of Code**: ~1,500+
