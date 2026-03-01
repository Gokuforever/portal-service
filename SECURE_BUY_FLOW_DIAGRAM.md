# Secure Buy/Return - Flow Diagrams

## 1. Complete Lifecycle Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SECURE BUY/RETURN LIFECYCLE                      │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────┐
│   CUSTOMER   │
│  (Initiates) │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. INITIATE RETURN                                              │
│    - Select delivered order items                               │
│    - Choose pickup address, date, time slot                     │
│    - System validates and creates Secure_Return                 │
│    - Status: SCHEDULED                                          │
│    - Estimated Refund: 50% of all items (Grade A assumption)    │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. PICKUP SCHEDULING                                            │
│    - Cron job triggers on scheduled_pickup_date                 │
│    - Create Porter order                                        │
│    - Status: PICKUP_PENDING → PICKUP_ASSIGNED                   │
│    - Store dp_order_id                                          │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. PICKUP & DELIVERY                                            │
│    - Porter picks up items from customer                        │
│    - Status: PICKED_UP → IN_TRANSIT → DELIVERED_TO_SELLER       │
│    - Porter webhook updates status                              │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. SELLER APPRAISAL                                             │
│    - Seller reviews each item                                   │
│    - Assigns grade: A (50%), B (30%), or C (0%)                 │
│    - Adds remarks and uploads photos                            │
│    - System calculates actual_refund_amount per item            │
│    - Status: UNDER_APPRAISAL → APPRAISAL_COMPLETED              │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. REFUND PROCESSING                                            │
│    - Calculate total_actual_refund (sum of all items)           │
│    - Initiate PhonePe/Razorpay refund                           │
│    - Status: REFUND_PENDING → REFUND_COMPLETED                  │
│    - Store refund_transaction_id                                │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────┐
│  ✅ CUSTOMER RECEIVES REFUND          │
│     Process Complete                 │
└──────────────────────────────────────┘
```

---

## 2. Appraisal Grading System

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         APPRAISAL GRADE SYSTEM                           │
└─────────────────────────────────────────────────────────────────────────┘

Product: ₹2,000 (selling_price_after_discount)

┌──────────────┬─────────────────────────┬──────────┬─────────────────┐
│    GRADE     │      CONDITION          │  REFUND  │  ACTUAL AMOUNT  │
├──────────────┼─────────────────────────┼──────────┼─────────────────┤
│      A       │  Excellent              │   50%    │    ₹1,000       │
│              │  - Original packaging   │          │                 │
│              │  - No damage            │          │                 │
│              │  - All accessories      │          │                 │
├──────────────┼─────────────────────────┼──────────┼─────────────────┤
│      B       │  Good                   │   30%    │    ₹600         │
│              │  - Minor scratches      │          │                 │
│              │  - No packaging         │          │                 │
│              │  - Functional           │          │                 │
├──────────────┼─────────────────────────┼──────────┼─────────────────┤
│      C       │  Poor/Damaged           │   0%     │    ₹0           │
│              │  - Broken/damaged       │          │                 │
│              │  - Not functional       │          │                 │
│              │  - Missing parts        │          │                 │
└──────────────┴─────────────────────────┴──────────┴─────────────────┘

Example with Multiple Items:
┌────────────┬──────────┬────────┬───────────┬─────────────────┐
│  Product   │  Price   │ Grade  │  Refund % │  Refund Amount  │
├────────────┼──────────┼────────┼───────────┼─────────────────┤
│  Item 1    │  ₹2,000  │   A    │    50%    │     ₹1,000      │
│  Item 2    │  ₹1,500  │   B    │    30%    │     ₹450        │
│  Item 3    │  ₹3,000  │   C    │    0%     │     ₹0          │
├────────────┴──────────┴────────┴───────────┼─────────────────┤
│                        TOTAL REFUND         │     ₹1,450      │
└─────────────────────────────────────────────┴─────────────────┘
```

---

## 3. Data Model Relationships

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATABASE RELATIONSHIPS                           │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  Order_Details   │
│  (Main Order)    │
│                  │
│  - id            │◄──────────┐
│  - code          │           │
│  - user_id       │           │ References
│  - seller_id     │           │
│  - status        │           │
│  - total_amount  │           │
└──────────────────┘           │
                               │
                               │
┌──────────────────┐           │
│   Order_Item     │           │
│  (Order Items)   │           │
│                  │           │
│  - id            │◄──────┐   │
│  - order_id      │       │   │
│  - product_id    │       │   │
│  - type (BUY/    │       │   │
│    SECURE)       │       │   │
│  - quantity      │       │   │
│  - selling_price_│       │   │
│    after_discount│       │   │
└──────────────────┘       │   │
                           │   │
                           │   │
┌──────────────────────────┼───┼──────────────────────────────┐
│      Secure_Return       │   │                              │
│   (Secure Buy/Return)    │   │                              │
│                          │   │                              │
│  - id                    │   │                              │
│  - order_id ─────────────┘   │                              │
│  - secure_order_code         │                              │
│  - user_id                   │                              │
│  - seller_id                 │                              │
│  - status                    │                              │
│  - scheduled_pickup_date     │                              │
│  - scheduled_time_slot       │                              │
│  - pickup_address            │                              │
│  - delivery_address          │                              │
│  - dp_order_id               │                              │
│  - total_estimated_refund    │                              │
│  - total_actual_refund       │                              │
│  - refund_transaction_id     │                              │
│  - refund_status             │                              │
│                              │                              │
│  ┌────────────────────────┐  │                              │
│  │ items: List<           │  │                              │
│  │  Secure_Return_Item>   │  │                              │
│  │                        │  │                              │
│  │  - order_item_id ──────┼──┘                              │
│  │  - product_id          │                                 │
│  │  - product_name        │                                 │
│  │  - quantity            │                                 │
│  │  - selling_price_      │                                 │
│  │    after_discount      │                                 │
│  │  - appraisal_grade     │  (A/B/C)                        │
│  │  - appraisal_remarks   │                                 │
│  │  - returned_item_      │                                 │
│  │    image_urls          │  [url1, url2, url3]             │
│  │  - estimated_refund_   │                                 │
│  │    amount              │                                 │
│  │  - actual_refund_      │                                 │
│  │    amount              │                                 │
│  │  - item_status         │                                 │
│  └────────────────────────┘                                 │
│                                                             │
│  ┌────────────────────────┐                                 │
│  │ status_history: List<  │                                 │
│  │  Secure_Status_History>│                                 │
│  │                        │                                 │
│  │  - status              │                                 │
│  │  - changed_at          │                                 │
│  │  - changed_by          │                                 │
│  │  - remarks             │                                 │
│  └────────────────────────┘                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Status Transition Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      STATUS TRANSITION FLOW                              │
└─────────────────────────────────────────────────────────────────────────┘

                    ┌──────────────┐
                    │  SCHEDULED   │ ◄─── Customer initiates
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
         ┌─────────►│PICKUP_PENDING│
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │PICKUP_       │ ◄─── Porter assigned
         │          │ASSIGNED      │
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │  PICKED_UP   │ ◄─── Items collected
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │  IN_TRANSIT  │
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │DELIVERED_TO_ │ ◄─── Reached seller
         │          │SELLER        │
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │UNDER_        │ ◄─── Seller reviewing
         │          │APPRAISAL     │
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │APPRAISAL_    │ ◄─── Grading complete
         │          │COMPLETED     │
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │REFUND_       │ ◄─── Payment processing
         │          │PENDING       │
         │          └──────┬───────┘
         │                 │
         │                 ▼
         │          ┌──────────────┐
         │          │REFUND_       │ ◄─── ✅ Success
         │          │COMPLETED     │
         │          └──────────────┘
         │
         │
         │          ┌──────────────┐
         └──────────│ RESCHEDULED  │ ◄─── Customer reschedules
                    └──────────────┘      (max 2 times)


         ┌──────────────┐
         │   FAILED     │ ◄─── ❌ Any failure
         └──────────────┘

         ┌──────────────┐
         │  CANCELLED   │ ◄─── ❌ User/system cancels
         └──────────────┘
```

---

## 5. API Request/Response Examples

### **Initiate Secure Return**

```json
POST /api/secure-return/initiate

Request:
{
  "order_id": "65f1a2b3c4d5e6f7g8h9i0j1",
  "order_item_ids": ["item123", "item456"],
  "address_id": "addr789",
  "return_date": "2026-03-05",
  "time_slot": "MORNING",
  "req_user_id": "user123"
}

Response:
{
  "id": "sec_ret_001",
  "secure_order_code": "SEC-ORD-MAR2026-123456",
  "status": "SCHEDULED",
  "scheduled_pickup_date": "2026-03-05",
  "scheduled_time_slot": "MORNING",
  "total_estimated_refund": 1500,
  "items": [
    {
      "order_item_id": "item123",
      "product_name": "Product A",
      "quantity": 1,
      "selling_price_after_discount": 2000,
      "estimated_refund_amount": 1000,
      "item_status": "PENDING_APPRAISAL"
    },
    {
      "order_item_id": "item456",
      "product_name": "Product B",
      "quantity": 1,
      "selling_price_after_discount": 1000,
      "estimated_refund_amount": 500,
      "item_status": "PENDING_APPRAISAL"
    }
  ]
}
```

---

### **Appraise Items**

```json
POST /api/secure-return/appraise

Request:
{
  "secure_return_id": "sec_ret_001",
  "req_user_id": "seller123",
  "item_appraisals": [
    {
      "order_item_id": "item123",
      "grade": "A",
      "remarks": "Excellent condition, original packaging intact",
      "image_urls": [
        "https://s3.../item123-front.jpg",
        "https://s3.../item123-back.jpg"
      ]
    },
    {
      "order_item_id": "item456",
      "grade": "B",
      "remarks": "Good condition, minor scratches on surface",
      "image_urls": [
        "https://s3.../item456.jpg"
      ]
    }
  ]
}

Response:
{
  "id": "sec_ret_001",
  "status": "APPRAISAL_COMPLETED",
  "total_estimated_refund": 1500,
  "total_actual_refund": 1300,
  "items": [
    {
      "order_item_id": "item123",
      "appraisal_grade": "A",
      "appraisal_grade_description": "Grade A - Excellent Condition",
      "refund_percentage": 50.0,
      "estimated_refund_amount": 1000,
      "actual_refund_amount": 1000,
      "item_status": "APPROVED_GRADE_A",
      "returned_item_image_urls": [
        "https://s3.../item123-front.jpg",
        "https://s3.../item123-back.jpg"
      ]
    },
    {
      "order_item_id": "item456",
      "appraisal_grade": "B",
      "appraisal_grade_description": "Grade B - Good Condition",
      "refund_percentage": 30.0,
      "estimated_refund_amount": 500,
      "actual_refund_amount": 300,
      "item_status": "APPROVED_GRADE_B",
      "returned_item_image_urls": [
        "https://s3.../item456.jpg"
      ]
    }
  ]
}
```

---

## 6. Refund Calculation Logic

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      REFUND CALCULATION FLOW                             │
└─────────────────────────────────────────────────────────────────────────┘

For each item:

┌──────────────────────────────────────────────────────────────┐
│ Step 1: Get Base Amount                                      │
│                                                               │
│ base_amount = selling_price_after_discount × quantity        │
│                                                               │
│ Example: ₹2,000 × 1 = ₹2,000                                 │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│ Step 2: Apply Appraisal Grade                                │
│                                                               │
│ Grade A → refund = base_amount × 50%                         │
│ Grade B → refund = base_amount × 30%                         │
│ Grade C → refund = base_amount × 0%                          │
│                                                               │
│ Example (Grade A): ₹2,000 × 50% = ₹1,000                     │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│ Step 3: Sum All Items                                        │
│                                                               │
│ total_actual_refund = Σ(all item refunds)                    │
│                                                               │
│ Example:                                                      │
│   Item 1 (Grade A): ₹1,000                                   │
│   Item 2 (Grade B): ₹300                                     │
│   Item 3 (Grade C): ₹0                                       │
│   ─────────────────────────                                  │
│   Total: ₹1,300                                              │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│ Step 4: Initiate Refund                                      │
│                                                               │
│ IF total_actual_refund > 0:                                  │
│   - Call PhonePe/Razorpay API                                │
│   - Store refund_transaction_id                              │
│   - Set refund_status = PENDING                              │
│ ELSE:                                                         │
│   - Mark as COMPLETED (no refund needed)                     │
└──────────────────────────────────────────────────────────────┘
```

---

**Created**: 2026-03-01  
**Purpose**: Visual guide for secure buy/return system  
**Status**: Complete ✅
