# Secure Refund Retry System

## Overview

Automatic retry mechanism for failed secure return refunds with PhonePe integration.

---

## New Order Status

### SECURE_REFUND_FAILED (29)
- **Customer Message**: "Refund Failed"
- **Internal Name**: "SecuRe Refund Failed"
- **Purpose**: Temporary failure status that triggers automatic retry
- **Difference from REFUND_FAILED**: 
  - `SECURE_REFUND_FAILED` → Will be retried automatically
  - `REFUND_FAILED` → Permanent failure, requires manual intervention

---

## New Database Fields in Order_Details

```java
private Integer refund_retry_count;          // Number of retry attempts made
private LocalDateTime last_refund_retry_date; // Timestamp of last retry
private String refund_failure_reason;         // Reason for refund failure
```

### Field Usage

| Field | Type | Purpose | Example |
|-------|------|---------|---------|
| `refund_retry_count` | Integer | Tracks retry attempts | 0, 1, 2, 3 |
| `last_refund_retry_date` | LocalDateTime | Last retry timestamp | 2026-02-28T20:00:00 |
| `refund_failure_reason` | String | Failure reason | "PhonePe refund failed" |

---

## Retry Configuration

### Constants
```java
MAX_RETRY_ATTEMPTS = 3           // Maximum retry attempts
RETRY_INTERVAL_HOURS = 6         // Hours between retries
```

### Retry Schedule
- **Cron Expression**: `0 0 */6 * * *`
- **Frequency**: Every 6 hours
- **Times**: 00:00, 06:00, 12:00, 18:00

---

## Complete Flow

### 1. Initial Refund Failure

```
Secure Return Appraised →
PhonePe Refund Initiated →
PhonePe Returns FAILED →
Status: SECURE_REFUND_FAILED
Retry Count: 0
Last Retry: Current Time
```

**Actions**:
- ✅ Status updated to `SECURE_REFUND_FAILED`
- ✅ `refund_retry_count` set to 0
- ✅ `last_refund_retry_date` set to current time
- ✅ `refund_failure_reason` set to "PhonePe refund failed"
- ✅ Error email sent to admin

### 2. First Retry (After 6 hours)

```
Retry Cron Runs →
Finds SECURE_REFUND_FAILED orders →
Checks retry count (0 < 3) ✓ →
Checks time since last retry (>= 6 hours) ✓ →
Attempts refund retry
```

**Possible Outcomes**:

#### A. Retry Successful
```
PhonePe Status: COMPLETED →
Status: SECURE_BUY_REFUNDED
Success email sent
```

#### B. Still Pending
```
PhonePe Status: PENDING →
Status: SECURE_REFUND_PENDING
Retry Count: 1
Last Retry: Updated
```

#### C. Still Failing
```
PhonePe Status: FAILED →
Status: SECURE_REFUND_FAILED (remains)
Retry Count: 1
Last Retry: Updated
Will retry again in 6 hours
```

### 3. Subsequent Retries

Retries continue every 6 hours until:
- ✅ Refund succeeds → `SECURE_BUY_REFUNDED`
- ✅ Max retries (3) reached → `REFUND_FAILED` (permanent)

### 4. Max Retries Reached

```
After 3 failed attempts →
Status: REFUND_FAILED (permanent)
Failure Reason: "Max retry attempts (3) reached"
CRITICAL email sent to admin
```

**Actions**:
- ⚠️ Status changed to permanent `REFUND_FAILED`
- ⚠️ Critical notification sent
- ⚠️ Manual intervention required

---

## Retry Logic

### Eligibility Check

```java
// Order must meet ALL conditions:
1. Status = SECURE_REFUND_FAILED
2. retry_count < 3
3. Time since last_refund_retry_date >= 6 hours
4. Order not deleted
```

### Retry Process

```
1. Check original refund status with PhonePe
   ├─ If COMPLETED → Update to SECURE_BUY_REFUNDED
   ├─ If PENDING → Update to SECURE_REFUND_PENDING
   └─ If FAILED → Increment retry count, stay in SECURE_REFUND_FAILED

2. Update retry metadata
   ├─ Increment refund_retry_count
   └─ Update last_refund_retry_date

3. Send notification based on outcome
```

---

## Email Notifications

### Initial Failure
```
Subject: Secure Return Refund Failed - Order: ORD123

PhonePe refund failed for secure return.
Order ID: 65f8a1b2c3d4e5f6g7h8i9j0
Order Code: ORD123
Refund Transaction ID: SECURE-REFUND-65f8a1b2-1709145600000
Retry Count: 0
Status: SECURE_REFUND_FAILED (will be retried by cron)
Next retry will be attempted automatically.
```

### Retry Success
```
Subject: Secure Refund Retry Successful - Order: ORD123

Refund retry successful after 2 attempts.
Order ID: 65f8a1b2c3d4e5f6g7h8i9j0
Order Code: ORD123
Refund Transaction ID: SECURE-REFUND-65f8a1b2-1709145600000
Status: SECURE_BUY_REFUNDED
```

### Max Retries Reached
```
Subject: CRITICAL: Secure Refund Failed After Max Retries - Order: ORD123

Refund failed after 3 retry attempts. Manual intervention required.
Order ID: 65f8a1b2c3d4e5f6g7h8i9j0
Order Code: ORD123
Refund Transaction ID: SECURE-REFUND-65f8a1b2-1709145600000
Last Failure Reason: PhonePe refund failed
Status: REFUND_FAILED (permanent)

ACTION REQUIRED: Please process refund manually.
```

---

## Status Transitions

### Normal Flow (Success)
```
SECURE_RETURN_APPRAISED →
SECURE_REFUND_PENDING →
SECURE_BUY_REFUNDED ✅
```

### Failure with Retry (Success on Retry 2)
```
SECURE_RETURN_APPRAISED →
SECURE_REFUND_PENDING →
SECURE_REFUND_FAILED (Retry 0) →
[6 hours] →
SECURE_REFUND_FAILED (Retry 1) →
[6 hours] →
SECURE_BUY_REFUNDED ✅
```

### Failure with Max Retries
```
SECURE_RETURN_APPRAISED →
SECURE_REFUND_PENDING →
SECURE_REFUND_FAILED (Retry 0) →
[6 hours] →
SECURE_REFUND_FAILED (Retry 1) →
[6 hours] →
SECURE_REFUND_FAILED (Retry 2) →
[6 hours] →
REFUND_FAILED (permanent) ⚠️
```

---

## Cron Jobs

### SecureRefundStatusCron
- **Schedule**: Every 2 hours
- **Purpose**: Check pending refunds
- **Statuses**: `SECURE_REFUND_PENDING`
- **Actions**:
  - Check PhonePe refund status
  - Update to `SECURE_BUY_REFUNDED` on success
  - Update to `SECURE_REFUND_FAILED` on failure (triggers retry)

### SecureRefundRetryCron (NEW)
- **Schedule**: Every 6 hours
- **Purpose**: Retry failed refunds
- **Statuses**: `SECURE_REFUND_FAILED`
- **Actions**:
  - Check retry eligibility
  - Attempt refund retry
  - Update status based on outcome
  - Send notifications

---

## Implementation Files

### Modified Files
1. ✅ `OrderStatus.java` - Added `SECURE_REFUND_FAILED(29)`
2. ✅ `Order_Details.java` - Added retry tracking fields
3. ✅ `SecureRefundStatusCron.java` - Updated to use `SECURE_REFUND_FAILED`
4. ✅ `Defaults.java` - Added `PHONEPE_REFUND_RETRY_CRON`
5. ✅ `OrderFilterBuilder.java` - Added `SECURE_REFUND_FAILED` to seller view

### New Files
1. ✅ `SecureRefundRetryCron.java` - Retry mechanism implementation

---

## Testing Checklist

### Unit Tests
- [ ] Retry eligibility check (count < 3)
- [ ] Retry eligibility check (time >= 6 hours)
- [ ] Retry count increment
- [ ] Max retries handling
- [ ] Status transitions

### Integration Tests
- [ ] Initial failure → SECURE_REFUND_FAILED
- [ ] Retry after 6 hours
- [ ] Successful retry → SECURE_BUY_REFUNDED
- [ ] Failed retry → stays in SECURE_REFUND_FAILED
- [ ] Max retries → REFUND_FAILED
- [ ] Email notifications sent correctly

### Edge Cases
- [ ] Order with null retry_count
- [ ] Order with null last_refund_retry_date
- [ ] PhonePe API timeout during retry
- [ ] PhonePe returns empty response
- [ ] Multiple orders retrying simultaneously
- [ ] Cron runs while retry in progress

---

## Monitoring & Alerts

### Key Metrics
- Failed refunds count
- Retry success rate
- Average retries to success
- Orders reaching max retries
- Retry cron execution time

### Alerts to Set Up
- Max retries reached (critical)
- Retry success rate < 50%
- Retry cron not running
- High number of SECURE_REFUND_FAILED orders

### Logs to Monitor
```bash
# Retry cron execution
grep "Starting secure refund retry cron" logs/portal-service.log

# Retry attempts
grep "Retrying refund for order" logs/portal-service.log

# Retry success
grep "Refund retry successful" logs/portal-service.log

# Max retries reached
grep "Max retry attempts reached" logs/portal-service.log

# Errors
grep "Error retrying refund" logs/portal-service.log
```

---

## Manual Intervention

### When Required
- Order reaches max retries (3 attempts)
- Status becomes `REFUND_FAILED` (permanent)
- Critical email received

### Steps
1. Check PhonePe dashboard for refund status
2. Verify refund transaction ID
3. Check order details and refund amount
4. Process refund manually if needed
5. Update order status to `SECURE_BUY_REFUNDED`
6. Notify customer

### Admin API (Future Enhancement)
```
POST /admin/refund/manual-process
{
  "order_id": "ORDER_ID",
  "refund_transaction_id": "REFUND_TXN_ID",
  "status": "COMPLETED",
  "notes": "Manually processed after max retries"
}
```

---

## Configuration

### Adjustable Parameters

```properties
# In application.properties or environment variables
secure.refund.retry.max-attempts=3
secure.refund.retry.interval-hours=6
secure.refund.retry.cron=0 0 */6 * * *
```

### Recommended Settings

| Environment | Max Attempts | Interval | Cron |
|-------------|--------------|----------|------|
| Development | 2 | 1 hour | `0 0 * * * *` |
| Staging | 3 | 3 hours | `0 0 */3 * * *` |
| Production | 3 | 6 hours | `0 0 */6 * * *` |

---

## Performance Considerations

### Database Queries
- Index on `status_id` for fast filtering
- Index on `last_refund_retry_date` for time-based filtering
- Batch processing for multiple orders

### API Calls
- PhonePe API rate limits
- Timeout handling
- Retry backoff strategy

### Scalability
- Parallel processing of retries
- Distributed cron execution
- Queue-based retry system (future)

---

## Future Enhancements

### Short Term
1. Configurable retry parameters
2. Exponential backoff (1h, 3h, 6h, 12h)
3. Admin dashboard for failed refunds
4. Manual retry trigger API

### Medium Term
1. Webhook integration with PhonePe
2. Real-time refund status updates
3. Customer notification on retry success
4. Analytics dashboard

### Long Term
1. ML-based failure prediction
2. Automated refund amount adjustment
3. Multi-gateway fallback
4. Blockchain-based refund tracking

---

## Summary

### What's New
✅ `SECURE_REFUND_FAILED` status for temporary failures  
✅ Automatic retry mechanism (3 attempts, 6-hour interval)  
✅ Retry tracking fields in database  
✅ `SecureRefundRetryCron` for automated retries  
✅ Comprehensive email notifications  
✅ Max retries handling with manual intervention  

### Benefits
- 🔄 Automatic recovery from transient failures
- 📧 Reduced manual intervention
- 📊 Better visibility into refund issues
- ⚡ Faster refund completion
- 🛡️ Robust error handling

### Impact
- **Customer**: Faster refunds, better experience
- **Seller**: Automated process, less disputes
- **Admin**: Reduced manual work, better tracking
- **System**: More reliable, self-healing

---

**Status**: ✅ **IMPLEMENTED**  
**Version**: 1.0  
**Last Updated**: 2026-02-28
