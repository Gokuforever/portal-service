# Weekly Seller Settlement System - Comprehensive Plan

## Current System Analysis

### Existing Settlement Flow
1. **Fee Structure**: 10% platform fee (Studeaze share)
2. **Seller Share**: 90% of total order amount
3. **Settlement Tracking**: 
   - `is_payout_done` (Boolean) - Marks if settlement is completed
   - `settlement_details` (SettlementDetails) - Stores payment transaction details
   - `seller_share` (Long) - Seller's share in paise
   - `studeaze_share` (Long) - Platform's share in paise

### Current Settlement Process
- **Manual Settlement**: Admin settles orders via `/settle` API
- **Eligible Orders**: DELIVERED, OUT_FOR_DELIVERY, READY_FOR_PICK_UP, RIDER_ASSIGNED, ORDER_ACCEPTED
- **Payout Timeline**: Expected 7 days after ORDER_ACCEPTED
- **Payment Modes**: UPI, IMPS, NEFT, RTGS, CHEQUE

### Calculation Formula
```java
totalAmountPaise = order.getTotal_amount()
studeazeShare = (totalAmountPaise * 10) / 100  // 10% platform fee
sellerShare = totalAmountPaise - studeazeShare  // 90% to seller
```

---

## New Requirements

### 1. Weekly Settlement Email System
- **Schedule**: Every Wednesday
- **Recipients**: All sellers with pending settlements
- **Content**: Weekly earnings report with order details

### 2. Secure Return Adjustments
- **Deduction**: Subtract refund amount from seller's weekly earnings
- **Reason**: Seller keeps the returned items (used products)
- **Impact**: Reduces seller's payout for that week

### 3. Settlement Visibility
- Track which orders are settled vs pending
- Settlement week tracking
- Dispute tracking (future)

---

## Proposed Solution

### Phase 1: Database Schema Enhancements

#### 1.1 Add New Fields to `Order_Details`

```java
// Settlement tracking fields
private String settlement_week_id;        // Format: "2026-W09" (ISO week)
private LocalDate settlement_week_start;  // Monday of settlement week
private LocalDate settlement_week_end;    // Sunday of settlement week
private Boolean included_in_settlement;   // Included in weekly report
private LocalDateTime settlement_email_sent_date;

// Secure return impact
private Long secure_return_deduction;     // Amount deducted due to secure return (paise)
private Boolean has_secure_return_impact; // Flag for secure return orders
```

#### 1.2 Create New Entity: `Weekly_Settlement_Report`

```java
@Document(collection = "weekly_settlement_reports")
public class Weekly_Settlement_Report extends BaseMongoEntity<String> {
    
    private String seller_id;
    private String seller_name;
    private String seller_email;
    
    // Week identification
    private String week_id;              // "2026-W09"
    private LocalDate week_start;        // Monday
    private LocalDate week_end;          // Sunday
    private Integer year;
    private Integer week_number;
    
    // Financial summary
    private Long total_orders_amount;    // Total order amount (paise)
    private Long studeaze_share_total;   // Platform fees (paise)
    private Long seller_share_total;     // Seller's 90% (paise)
    private Long secure_return_deductions; // Refunds deducted (paise)
    private Long net_payout_amount;      // Final amount to pay (paise)
    
    // Order tracking
    private Integer total_orders_count;
    private Integer delivered_orders_count;
    private Integer secure_return_orders_count;
    private List<String> order_ids;      // All order IDs in this settlement
    private List<String> secure_return_order_ids;
    
    // Settlement status
    private SettlementStatus status;     // PENDING, EMAIL_SENT, PAID, DISPUTED
    private LocalDateTime email_sent_date;
    private LocalDateTime settlement_date;
    private SettlementDetails settlement_details;
    
    // Dispute tracking (future)
    private Boolean has_dispute;
    private String dispute_reason;
    private LocalDateTime dispute_raised_date;
    private String dispute_resolution;
    
    // Audit
    private String settled_by;           // Admin user ID who settled
    private String remarks;
}
```

#### 1.3 Create Enum: `SettlementStatus`

```java
public enum SettlementStatus {
    PENDING(0, "Pending", "Settlement pending"),
    EMAIL_SENT(1, "Email Sent", "Weekly report email sent"),
    PAID(2, "Paid", "Settlement completed"),
    DISPUTED(3, "Disputed", "Seller raised dispute"),
    RESOLVED(4, "Resolved", "Dispute resolved");
    
    private final int id;
    private final String displayName;
    private final String description;
}
```

---

### Phase 2: Weekly Settlement Cron Job

#### 2.1 Cron Schedule
```java
@Scheduled(cron = "0 0 9 * * WED") // Every Wednesday at 9 AM
public void generateWeeklySettlementReports()
```

#### 2.2 Process Flow

```
1. Calculate Previous Week Range
   └─ Monday to Sunday of last week

2. Find Eligible Orders
   ├─ Status: DELIVERED
   ├─ Delivered date: Within last week
   ├─ is_payout_done: false
   ├─ included_in_settlement: false or null
   └─ Group by seller_id

3. For Each Seller:
   ├─ Calculate Regular Orders
   │  ├─ Total amount
   │  ├─ Studeaze share (10%)
   │  └─ Seller share (90%)
   │
   ├─ Find Secure Return Orders
   │  ├─ Status: PARTIALLY_REFUNDED
   │  ├─ Refund completed: Within last week
   │  └─ Calculate deduction amount
   │
   ├─ Calculate Net Payout
   │  └─ Net = Seller Share - Secure Return Deductions
   │
   ├─ Create Weekly_Settlement_Report
   │  └─ Save to database
   │
   ├─ Update Orders
   │  ├─ Set settlement_week_id
   │  ├─ Set included_in_settlement = true
   │  └─ Set settlement_week_start/end
   │
   └─ Send Email to Seller
      └─ Attach detailed report

4. Log Summary
   └─ Total sellers, total amount, emails sent
```

#### 2.3 Calculation Logic

```java
// Regular orders (delivered last week)
List<Order_Details> deliveredOrders = findDeliveredOrdersForWeek(sellerId, weekStart, weekEnd);

Long totalOrdersAmount = 0L;
Long studeazeShareTotal = 0L;
Long sellerShareTotal = 0L;

for (Order_Details order : deliveredOrders) {
    Long orderAmount = order.getTotal_amount();
    FeeResult fees = CommonUtils.calculateFees(orderAmount, 10.0);
    
    totalOrdersAmount += orderAmount;
    studeazeShareTotal += fees.revenueInPaise();  // 10%
    sellerShareTotal += fees.costInPaise();        // 90%
    
    // Update order
    order.setSeller_share(fees.costInPaise());
    order.setStudeaze_share(fees.revenueInPaise());
}

// Secure return deductions (refunds completed last week)
List<Order_Details> secureReturnOrders = findSecureReturnOrdersForWeek(sellerId, weekStart, weekEnd);

Long secureReturnDeductions = 0L;

for (Order_Details order : secureReturnOrders) {
    // Get refund amount from refund transaction
    Long refundAmount = getRefundAmount(order.getRefund_transaction_id());
    
    secureReturnDeductions += refundAmount;
    
    // Update order
    order.setSecure_return_deduction(refundAmount);
    order.setHas_secure_return_impact(true);
}

// Net payout
Long netPayoutAmount = sellerShareTotal - secureReturnDeductions;
```

---

### Phase 3: Email Template

#### 3.1 Email Subject
```
Weekly Settlement Report - Week {week_number}, {year} | Net Payout: ₹{amount}
```

#### 3.2 Email Content (Template Variables)

```
sellerName|weekId|weekStart|weekEnd|
totalOrders|deliveredCount|totalAmount|
studeazeShare|sellerShare|
secureReturnCount|secureReturnDeduction|
netPayout|expectedPaymentDate
```

#### 3.3 Email Template Structure

```html
<h2>Hi {{sellerName}},</h2>
<h3>Your Weekly Settlement Report</h3>

<p><strong>Settlement Period:</strong> {{weekStart}} to {{weekEnd}} (Week {{weekId}})</p>

<h4>📊 Order Summary</h4>
<table>
  <tr>
    <td>Total Orders Delivered:</td>
    <td>{{deliveredCount}}</td>
  </tr>
  <tr>
    <td>Total Order Value:</td>
    <td>₹{{totalAmount}}</td>
  </tr>
  <tr>
    <td>Platform Fee (10%):</td>
    <td>₹{{studeazeShare}}</td>
  </tr>
  <tr>
    <td>Your Share (90%):</td>
    <td><strong>₹{{sellerShare}}</strong></td>
  </tr>
</table>

{{#if secureReturnCount > 0}}
<h4>🔄 SecuRe Return Adjustments</h4>
<table>
  <tr>
    <td>SecuRe Returns Processed:</td>
    <td>{{secureReturnCount}}</td>
  </tr>
  <tr>
    <td>Refund Amount (Deducted):</td>
    <td style="color: red;">-₹{{secureReturnDeduction}}</td>
  </tr>
  <tr>
    <td colspan="2">
      <small>Note: This amount is deducted as you retain the returned items.</small>
    </td>
  </tr>
</table>
{{/if}}

<h4>💰 Net Settlement Amount</h4>
<table style="background: #f0f0f0;">
  <tr>
    <td><strong>Amount to be Paid:</strong></td>
    <td><strong style="font-size: 20px; color: green;">₹{{netPayout}}</strong></td>
  </tr>
  <tr>
    <td>Expected Payment Date:</td>
    <td>{{expectedPaymentDate}}</td>
  </tr>
</table>

<h4>📋 Detailed Order List</h4>
<p>Please find attached Excel report with complete order details.</p>

<p><strong>Next Steps:</strong></p>
<ul>
  <li>Review the attached detailed report</li>
  <li>Settlement will be processed offline</li>
  <li>Payment expected by {{expectedPaymentDate}}</li>
  <li>For any discrepancies, please contact support within 48 hours</li>
</ul>

<p>Thank you for partnering with Studeaze!</p>
```

---

### Phase 4: Excel Report Generation

#### 4.1 Report Structure

**Sheet 1: Summary**
| Field | Value |
|-------|-------|
| Seller Name | John's Store |
| Settlement Week | 2026-W09 |
| Period | 03-03-2026 to 09-03-2026 |
| Total Orders | 25 |
| Total Order Value | ₹50,000 |
| Platform Fee (10%) | ₹5,000 |
| Seller Share (90%) | ₹45,000 |
| SecuRe Returns | 2 |
| Refund Deduction | -₹2,000 |
| **Net Payout** | **₹43,000** |

**Sheet 2: Delivered Orders**
| Order ID | Order Date | Delivery Date | Items | Order Amount | Platform Fee | Your Share |
|----------|------------|---------------|-------|--------------|--------------|------------|
| ORD001 | 03-03-2026 | 05-03-2026 | 3 | ₹2,000 | ₹200 | ₹1,800 |
| ORD002 | 04-03-2026 | 06-03-2026 | 5 | ₹3,500 | ₹350 | ₹3,150 |

**Sheet 3: SecuRe Return Deductions**
| Order ID | Original Amount | Refund Date | Refund Amount | Reason |
|----------|-----------------|-------------|---------------|--------|
| ORD050 | ₹2,500 | 07-03-2026 | ₹1,000 | Partial refund (Rating: 4) |
| ORD051 | ₹1,500 | 08-03-2026 | ₹1,000 | Partial refund (Rating: 5) |

---

### Phase 5: API Enhancements

#### 5.1 New APIs

**1. Get Weekly Settlement Reports**
```
POST /settlement/weekly/find
Request: {
  "week_id": "2026-W09",      // Optional
  "status": "PENDING",         // Optional
  "seller_id": "SELLER123"     // Optional (admin can view all)
}
Response: List<Weekly_Settlement_Report>
```

**2. Get Settlement Details**
```
POST /settlement/weekly/details
Request: {
  "report_id": "REPORT_ID"
}
Response: {
  "report": Weekly_Settlement_Report,
  "orders": List<Order_Details>,
  "secure_returns": List<Order_Details>
}
```

**3. Mark Settlement as Paid**
```
POST /settlement/weekly/settle
Request: {
  "report_id": "REPORT_ID",
  "settlement_details": SettlementDetails
}
```

**4. Raise Dispute (Future)**
```
POST /settlement/weekly/dispute
Request: {
  "report_id": "REPORT_ID",
  "reason": "Amount mismatch",
  "details": "..."
}
```

#### 5.2 Update Existing `/settlement/find` API
- Add filter for `settlement_week_id`
- Show settlement week in response
- Show secure return impact

---

### Phase 6: Secure Return Integration

#### 6.1 Update Secure Refund Cron

When refund status becomes `PARTIALLY_REFUNDED`:

```java
// In SecureRefundStatusCron.handleRefundSuccess()
private void handleRefundSuccess(Order_Details order) {
    log.info("Refund completed successfully for order: {}", order.getId());
    
    order.setStatus(OrderStatus.PARTIALLY_REFUNDED, Defaults.PHONEPE_REFUND_CRON);
    
    // NEW: Mark for settlement deduction
    order.setHas_secure_return_impact(true);
    // Deduction amount will be calculated in weekly settlement
    
    orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_CRON);
    
    log.info("Order {} status updated to PARTIALLY_REFUNDED", order.getId());
}
```

#### 6.2 Get Refund Amount

```java
private Long getRefundAmountForOrder(Order_Details order) {
    String refundTxnId = order.getRefund_transaction_id();
    
    // Check refund status to get amount
    Optional<RefundStatusResponse> refundStatus = phonePeUtility.refundStatus(refundTxnId);
    
    if (refundStatus.isPresent()) {
        return refundStatus.get().getAmount(); // Amount in paise
    }
    
    // Fallback: Calculate from order items rating
    // (if refund amount not available from PhonePe)
    return calculateRefundFromOrderItems(order);
}
```

---

### Phase 7: Settlement Visibility Dashboard

#### 7.1 Seller Portal View

**My Settlements**
- List of all weekly settlement reports
- Status: Pending / Email Sent / Paid
- Net payout amount
- Download detailed Excel report
- Raise dispute (future)

**Settlement Details**
- Week summary
- Order list with amounts
- SecuRe return deductions
- Net payout calculation
- Payment status

#### 7.2 Admin Portal View

**All Settlements**
- Filter by week, seller, status
- Bulk settlement processing
- Export reports
- Dispute management (future)

**Settlement Analytics**
- Total pending settlements
- Weekly payout trends
- SecuRe return impact analysis
- Seller-wise breakdown

---

## Implementation Plan

### Week 1: Database & Models
- [ ] Add new fields to `Order_Details`
- [ ] Create `Weekly_Settlement_Report` entity
- [ ] Create `SettlementStatus` enum
- [ ] Create database migration scripts
- [ ] Add indexes for performance

### Week 2: Core Logic
- [ ] Implement weekly settlement calculation logic
- [ ] Implement secure return deduction logic
- [ ] Create helper methods for fee calculation
- [ ] Add order grouping by seller
- [ ] Add week range calculation utilities

### Week 3: Cron Job
- [ ] Create `WeeklySettlementCron` class
- [ ] Implement report generation logic
- [ ] Implement order update logic
- [ ] Add comprehensive logging
- [ ] Add error handling and retry logic

### Week 4: Email System
- [ ] Create email template HTML
- [ ] Implement email content builder
- [ ] Integrate with email service
- [ ] Add Excel report generation
- [ ] Test email delivery

### Week 5: APIs
- [ ] Implement `/settlement/weekly/find`
- [ ] Implement `/settlement/weekly/details`
- [ ] Implement `/settlement/weekly/settle`
- [ ] Update existing settlement APIs
- [ ] Add authorization checks

### Week 6: Testing & Deployment
- [ ] Unit tests for calculation logic
- [ ] Integration tests for cron job
- [ ] Test with sample data
- [ ] UAT with sellers
- [ ] Production deployment

### Future Enhancements
- [ ] Dispute management system
- [ ] Automated payment integration
- [ ] Real-time settlement tracking
- [ ] Analytics dashboard
- [ ] Mobile app integration

---

## Key Considerations

### 1. **Week Calculation**
- Use ISO week standard (Monday to Sunday)
- Handle year transitions correctly
- Store week_id in format "YYYY-Www" (e.g., "2026-W09")

### 2. **Secure Return Timing**
- Only include refunds COMPLETED in the settlement week
- Don't include pending refunds
- Track refund completion date, not initiation date

### 3. **Order Eligibility**
- Only DELIVERED orders
- Delivery date within settlement week
- Not already included in previous settlement
- Not already paid out manually

### 4. **Error Handling**
- Handle missing seller email
- Handle calculation errors
- Handle email send failures
- Retry mechanism for failed operations
- Admin notifications for failures

### 5. **Data Integrity**
- Atomic updates for order settlement flags
- Transaction support for batch updates
- Audit trail for all changes
- Prevent duplicate settlements

### 6. **Performance**
- Batch process orders
- Optimize database queries
- Use indexes on settlement fields
- Async email sending
- Progress tracking for large datasets

### 7. **Security**
- Seller can only view own settlements
- Admin can view all settlements
- Secure payment details
- Audit all settlement actions

---

## Example Calculation

### Scenario:
**Seller**: John's Store  
**Week**: 2026-W09 (03-03-2026 to 09-03-2026)

**Delivered Orders:**
1. Order A: ₹10,000 (Delivered 05-03-2026)
2. Order B: ₹15,000 (Delivered 07-03-2026)
3. Order C: ₹5,000 (Delivered 08-03-2026)

**SecuRe Returns:**
1. Order X: Refund ₹2,000 (Completed 06-03-2026, Rating: 4)
2. Order Y: Refund ₹1,500 (Completed 09-03-2026, Rating: 5)

### Calculation:

```
Total Order Amount = ₹10,000 + ₹15,000 + ₹5,000 = ₹30,000
Platform Fee (10%) = ₹30,000 × 10% = ₹3,000
Seller Share (90%) = ₹30,000 × 90% = ₹27,000

SecuRe Return Deductions = ₹2,000 + ₹1,500 = ₹3,500

Net Payout = ₹27,000 - ₹3,500 = ₹23,500
```

### Email Summary:
```
Total Orders: 3
Total Order Value: ₹30,000
Platform Fee: ₹3,000
Your Share: ₹27,000
SecuRe Returns: 2
Refund Deduction: -₹3,500
━━━━━━━━━━━━━━━━━━━━━━━
Net Payout: ₹23,500
```

---

## Testing Checklist

- [ ] Week range calculation (including year boundaries)
- [ ] Order filtering (status, date, flags)
- [ ] Fee calculation (10% platform, 90% seller)
- [ ] Secure return deduction calculation
- [ ] Net payout calculation
- [ ] Multiple sellers in same week
- [ ] Seller with no orders
- [ ] Seller with only secure returns
- [ ] Email template rendering
- [ ] Excel report generation
- [ ] Database updates (atomic)
- [ ] Duplicate prevention
- [ ] Error handling
- [ ] Cron job execution
- [ ] API authorization
- [ ] Settlement marking

---

This comprehensive plan provides a complete roadmap for implementing the weekly seller settlement system with secure return adjustments and proper visibility tracking.
