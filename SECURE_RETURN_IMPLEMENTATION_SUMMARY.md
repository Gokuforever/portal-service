# Secure Return & Refund Implementation - Complete Summary

## 🎯 Overview

This document summarizes all changes made to implement the complete secure return, appraisal, refund, and weekly settlement system.

---

## ✅ Completed Changes

### 1. Order Status Enhancements

#### New Statuses Added
| ID | Status | Customer Sees | Internal Name | Purpose |
|----|--------|---------------|---------------|---------|
| 26 | Return Under Review | SecuRe Return Appraised | After seller appraises returned items |
| 27 | Refund Processing | SecuRe Refund Pending | PhonePe refund initiated |
| 28 | Refund Completed | SecuRe Buy Refunded | PhonePe refund completed |

#### Updated Customer-Facing Messages
All secure statuses now have user-friendly customer messages:
- `SECURE_RETURN_SCHEDULED` → "Return Pickup Scheduled"
- `SECURE_RETURN_INITIATED` → "Return Pickup Initiated"
- `RIDER_ASSIGNED_FOR_SECURE_RETURN` → "Pickup Partner Assigned"
- `ITEMS_PICKED_UP_FOR_SECURE_RETURN` → "Items Picked Up"
- `SECURE_RETURN_COMPLETED` → "Return Completed"
- `SECURE_RETURN_APPRAISED` → "Return Under Review"
- `SECURE_REFUND_PENDING` → "Refund Processing"
- `SECURE_BUY_REFUNDED` → "Refund Completed"
- `SECURE_RETURN_FAILED` → "Return Pickup Failed"
- `ITEM_SECURED` → "Items Secured"

**File**: `common-libs/src/main/java/com/sorted/common/enums/OrderStatus.java`

---

### 2. Secure Return Appraisal System

#### Features Implemented
- ✅ Mandatory rating system (1-5)
- ✅ Optional custom refund amount override
- ✅ Automatic refund calculation based on rating
- ✅ Rating-to-percentage mapping (5=50%, 4=40%, 3=30%, 2=20%, 1=10%)
- ✅ Amount conversion (rupees → paise)
- ✅ Seller authorization validation
- ✅ Order status validation

#### API Endpoint
```
POST /secure/appraise
{
  "order_id": "ORDER_ID",
  "rating": 4,              // Required (1-5)
  "amount": 1500.50,        // Optional (rupees)
  "remark": "Good condition" // Optional
}
```

#### Calculation Logic
```java
// With rating only
Long refundPaise = (totalItemPricePaise * rating * 10) / 100;

// With custom amount
Long refundPaise = CommonUtils.rupeeToPaise(amount);
```

**Files Modified**:
- `portal-service/src/main/java/com/sorted/portal/service/secure/SecureReturnService.java`
- `portal-service/src/main/java/com/sorted/portal/request/beans/AppraiseSecureReturn.java`

---

### 3. PhonePe Refund Integration

#### New Method: `partialRefund()`
```java
public Optional<RefundResponse> partialRefund(
    String merchantRefundId,      // Unique refund ID
    String originalMerchantOrderId, // Original order ID
    Long refundAmount              // Amount in paise
)
```

#### Refund Flow
1. Generate unique refund transaction ID: `SECURE-REFUND-{orderId}-{timestamp}`
2. Call PhonePe `partialRefund()` API
3. Handle response state:
   - **COMPLETED** → `SECURE_BUY_REFUNDED`
   - **PENDING** → `SECURE_REFUND_PENDING`
   - **FAILED** → `REFUND_FAILED` + error email
4. Update order with refund transaction ID
5. Set `has_secure_return_impact = true` for settlement tracking

**File**: `portal-service/src/main/java/com/sorted/portal/PhonePe/PhonePeUtility.java`

---

### 4. Refund Status Monitoring Cron

#### Cron Job: `SecureRefundStatusCron`
- **Schedule**: Every 2 hours (`@Scheduled(cron = "0 0 */2 * * *")`)
- **Purpose**: Check pending refunds and update status

#### Process Flow
```
1. Find orders with SECURE_REFUND_PENDING status
2. For each order:
   - Check refund status with PhonePe
   - If COMPLETED → Update to SECURE_BUY_REFUNDED
   - If FAILED → Update to REFUND_FAILED + send error email
   - If PENDING → Keep checking
3. Log summary (success/failed/pending counts)
```

#### Email Notifications
Sends error emails when refunds fail with details:
- Order ID and code
- Refund transaction ID
- Refund amount
- Requires manual intervention

**File**: `portal-service/src/main/java/com/sorted/portal/crons/SecureRefundStatusCron.java`

---

### 5. Order Filter Updates

#### Seller View
Added all secure statuses to `SELLER_ALLOWED_STATUS`:
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

Sellers can now see and filter all secure return orders.

**File**: `portal-service/src/main/java/com/sorted/portal/service/order/OrderFilterBuilder.java`

---

### 6. Customer Order Visibility

#### Design Decision
✅ **Show actual statuses** instead of grouping under "SECURE_RETURN"

**Rationale**:
- Transparency: Customers know exactly what's happening
- Better UX: Step-by-step progress tracking
- Reduced support queries: Clear status messages
- Trust building: Detailed information builds confidence

#### Customer Status Mapping (To Be Implemented)
```java
case "PROCESSING" -> Include secure return pickup statuses
case "DELIVERED" -> Include ITEM_SECURED
// No SECURE_RETURN grouping - show individual statuses
```

**Status**: ⚠️ Requires manual update to `getByCustomerStatus` method

---

## 📋 Weekly Seller Settlement System (Planned)

### Database Schema

#### New Fields in `Order_Details`
```java
private String settlement_week_id;        // "2026-W09"
private LocalDate settlement_week_start;
private LocalDate settlement_week_end;
private Boolean included_in_settlement;
private LocalDateTime settlement_email_sent_date;
private Long secure_return_deduction;     // Refund amount (paise)
private Boolean has_secure_return_impact;
```

#### New Entity: `Weekly_Settlement_Report`
```java
- seller_id, seller_name, seller_email
- week_id, week_start, week_end
- total_orders_amount, studeaze_share, seller_share
- secure_return_deductions
- net_payout_amount
- status (PENDING/EMAIL_SENT/PAID/DISPUTED)
- order_ids, secure_return_order_ids
```

### Calculation Logic

```
Regular Orders (Delivered last week):
  Total: ₹30,000
  Platform Fee (10%): ₹3,000
  Seller Share (90%): ₹27,000

SecuRe Return Deductions (Refunds completed last week):
  Refund 1: ₹2,000
  Refund 2: ₹1,500
  Total: ₹3,500

Net Payout = ₹27,000 - ₹3,500 = ₹23,500
```

### Weekly Cron Job (To Be Implemented)
- **Schedule**: Every Wednesday at 9 AM
- **Process**:
  1. Calculate previous week (Monday-Sunday)
  2. Find delivered orders per seller
  3. Calculate seller share (90%)
  4. Find completed secure return refunds
  5. Calculate net payout (share - refunds)
  6. Create `Weekly_Settlement_Report`
  7. Generate Excel report
  8. Send email to seller
  9. Update order flags

### Email Template
```
Subject: Weekly Settlement Report - Week 9, 2026 | Net Payout: ₹23,500

Hi John,

Your Weekly Settlement Report

Settlement Period: Mar 3 - Mar 9, 2026 (Week 2026-W09)

📊 Order Summary
- Total Orders Delivered: 3
- Total Order Value: ₹30,000
- Platform Fee (10%): ₹3,000
- Your Share (90%): ₹27,000

🔄 SecuRe Return Adjustments
- SecuRe Returns Processed: 2
- Refund Amount (Deducted): -₹3,500
Note: This amount is deducted as you retain the returned items.

💰 Net Settlement Amount
Amount to be Paid: ₹23,500
Expected Payment Date: Mar 16, 2026

📋 Detailed Order List
Please find attached Excel report with complete order details.
```

---

## 📁 Files Created/Modified

### Created Files
1. ✅ `SecureRefundStatusCron.java` - Refund status monitoring
2. ✅ `SECURE_APPRAISAL_API.md` - API documentation
3. ✅ `SECURE_API_CURL_EXAMPLES.md` - cURL examples
4. ✅ `SECURE_RESCHEDULE_API.md` - Reschedule API docs
5. ✅ `SECURE_APPRAISAL_REFUND_FLOW.md` - Complete flow documentation
6. ✅ `EMAIL_TEMPLATE_NOTES.md` - Email template guidelines
7. ✅ `WEEKLY_SELLER_SETTLEMENT_PLAN.md` - Settlement system plan
8. ✅ `ORDER_FILTER_UPDATES.md` - Filter update reference
9. ✅ `CUSTOMER_ORDER_STATUS_GUIDE.md` - Customer status guide
10. ✅ `SECURE_RETURN_IMPLEMENTATION_SUMMARY.md` - This file

### Modified Files
1. ✅ `OrderStatus.java` - Added 3 new statuses, updated customer messages
2. ✅ `ResponseCode.java` - Added validation error codes
3. ✅ `PhonePeUtility.java` - Added `partialRefund()` method
4. ✅ `SecureReturnService.java` - Complete appraisal & refund logic
5. ✅ `AppraiseSecureReturn.java` - Request bean
6. ✅ `OrderFilterBuilder.java` - Added secure statuses to seller view
7. ✅ `SecurePickupReminderCron.java` - Fixed email template usage

---

## 🔄 Complete Order Status Flow

### Regular Order
```
ORDER_PLACED → TRANSACTION_PROCESSED → ORDER_ACCEPTED → 
READY_FOR_PICK_UP → RIDER_ASSIGNED → OUT_FOR_DELIVERY → 
DELIVERED
```

### SecuRe Buy Order
```
ORDER_PLACED → TRANSACTION_PROCESSED → ORDER_ACCEPTED → 
READY_FOR_PICK_UP → RIDER_ASSIGNED → OUT_FOR_DELIVERY → 
DELIVERED → ITEM_SECURED
```

### SecuRe Return Flow
```
ITEM_SECURED → SECURE_RETURN_SCHEDULED → 
SECURE_RETURN_INITIATED → ORDER_CANCELLED_FOR_SECURE_RETURN → 
RIDER_ASSIGNED_FOR_SECURE_RETURN → 
ITEMS_PICKED_UP_FOR_SECURE_RETURN → 
SECURE_RETURN_COMPLETED → 
SECURE_RETURN_APPRAISED → 
SECURE_REFUND_PENDING → 
SECURE_BUY_REFUNDED
```

### Failure Paths
```
SECURE_RETURN_SCHEDULED → SECURE_RETURN_FAILED
SECURE_REFUND_PENDING → REFUND_FAILED
```

---

## 🧪 Testing Checklist

### Appraisal System
- [ ] Appraise with rating 5 (50% refund)
- [ ] Appraise with rating 1 (10% refund)
- [ ] Appraise with custom amount
- [ ] Invalid rating (0, 6, -1)
- [ ] Missing rating
- [ ] Invalid order status
- [ ] Unauthorized seller

### PhonePe Integration
- [ ] Refund returns COMPLETED immediately
- [ ] Refund returns PENDING
- [ ] Refund returns FAILED
- [ ] Network timeout
- [ ] Empty response

### Refund Status Cron
- [ ] Cron runs every 2 hours
- [ ] Finds SECURE_REFUND_PENDING orders
- [ ] Updates to SECURE_BUY_REFUNDED on success
- [ ] Updates to REFUND_FAILED on failure
- [ ] Sends error email on failure
- [ ] Logs summary correctly

### Order Filters
- [ ] Seller sees all secure statuses
- [ ] Customer sees individual statuses
- [ ] Filter by specific status works
- [ ] Status messages are user-friendly

### Settlement (When Implemented)
- [ ] Weekly cron runs on Wednesday
- [ ] Calculates seller share correctly
- [ ] Identifies SECURE_BUY_REFUNDED orders
- [ ] Deducts refund amounts
- [ ] Generates Excel report
- [ ] Sends email to seller
- [ ] Updates order flags

---

## 🚀 Next Steps

### Immediate (This Sprint)
1. ⚠️ **Manual Update Required**: Update `getByCustomerStatus` in `OrderFilterBuilder.java`
   - Add secure statuses to PROCESSING case
   - Add ITEM_SECURED to DELIVERED case
   - Do NOT create SECURE_RETURN grouping
   - Reference: `ORDER_FILTER_UPDATES.md`

2. 📧 **Create Email Template**: `secure_pickup_reminder.html`
   - Template variables: `customerName|orderCode|itemCount|pickupDate|timeSlot`
   - Reference: `EMAIL_TEMPLATE_NOTES.md`

3. 🧪 **Testing**: Test complete secure return flow end-to-end

### Short Term (Next Sprint)
1. 📊 **Weekly Settlement Cron**: Implement `WeeklySettlementCron`
2. 🗄️ **Database Migration**: Add new fields to `Order_Details`
3. 📧 **Settlement Email Template**: Create weekly settlement email template
4. 📑 **Excel Report Generator**: Implement settlement report generation
5. 🔌 **Settlement APIs**: Implement `/settlement/weekly/*` endpoints

### Medium Term
1. 📱 **Seller Portal**: Add settlement dashboard
2. 📱 **Customer App**: Update to show new statuses
3. 🔔 **Push Notifications**: Add for status changes
4. 📊 **Analytics**: Settlement analytics dashboard

### Long Term
1. 🔧 **Dispute Management**: Seller dispute system
2. 💳 **Automated Payments**: Integrate payment gateway for settlements
3. 📈 **Advanced Analytics**: Seller performance metrics
4. 🤖 **ML-Based Appraisal**: Suggest refund amounts based on history

---

## 📊 Key Metrics to Track

### Operational
- Secure return completion rate
- Average appraisal rating
- Refund success rate
- Refund processing time
- Settlement accuracy

### Financial
- Total refunds processed
- Average refund amount
- Seller payout amounts
- Platform fee collection
- Refund failure rate

### Customer Experience
- Return request to refund time
- Customer satisfaction with refund amount
- Support tickets related to refunds
- Refund dispute rate

---

## 🔒 Security Considerations

1. **Seller Authorization**: Only order seller can appraise
2. **Order Status Validation**: Strict status checks
3. **Amount Validation**: Positive amounts only
4. **Refund Transaction ID**: Unique and traceable
5. **Settlement Data**: Encrypted and audited
6. **Payment Details**: Secure storage
7. **API Rate Limiting**: Prevent abuse
8. **Audit Trail**: All actions logged

---

## 📞 Support & Troubleshooting

### Common Issues

**Refund stuck in PENDING**:
- Check PhonePe dashboard
- Verify refund transaction ID
- Check cron job logs
- Manual intervention may be required

**Settlement amount mismatch**:
- Verify order delivery dates
- Check secure return deductions
- Verify fee calculation (10%)
- Review Excel report

**Email not sent**:
- Check seller email address
- Verify email service status
- Check cron job logs
- Resend manually if needed

---

## 📝 Notes

- All amounts stored in paise (Long) for precision
- API accepts amounts in rupees (BigDecimal) for user-friendliness
- Conversion uses `CommonUtils.rupeeToPaise()` and `paiseToRupee()`
- Settlement week uses ISO week standard (Monday-Sunday)
- Refund status checked every 2 hours
- Settlement reports generated every Wednesday
- Customer sees user-friendly status messages
- Internal logs use technical status names

---

**Last Updated**: 2026-02-28  
**Version**: 1.0  
**Status**: Implementation In Progress
