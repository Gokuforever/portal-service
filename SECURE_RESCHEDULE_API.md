# Secure Return Reschedule API

## Overview
API endpoint to reschedule a secure return pickup with a maximum of 2 reschedules per order.

## Endpoint
**POST** `/secure/reschedule`

## Request Body
```json
{
  "order_id": "string",
  "new_pickup_date": "YYYY-MM-DD",
  "new_time_slot": "MORNING|AFTERNOON|EVENING|NIGHT",
  "address_id": "string (optional)"
}
```

### Parameters
- **order_id** (required): The order ID for which to reschedule the pickup
- **new_pickup_date** (required): New pickup date in YYYY-MM-DD format
- **new_time_slot** (required): New time slot (MORNING, AFTERNOON, EVENING, NIGHT)
- **address_id** (optional): New pickup address ID. If not provided, uses existing address

## Business Rules

### 1. Maximum Reschedules
- Maximum **2 reschedules** allowed per order
- Tracked via `max_secured_reschedule_count` field in Order_Details
- Counter increments with each reschedule

### 2. Order Status Validation
- Order must be in `SECURE_RETURN_SCHEDULED` status
- Cannot reschedule if pickup already initiated or completed

### 3. Date Validation
- New pickup date must be in the future
- Must be within 180 days from original order date
- Seller must be operational on the selected date

### 4. Time Slot Validation
- Must match seller's business hours for the selected day
- Delivery partner availability is checked via Porter API

### 5. Address Validation
- If changing address, new address must belong to the customer
- Address must be valid for delivery

## Flow

### 1. Validation
- Validate customer (must be CUSTOMER role)
- Validate reschedule request (all required fields present)
- Parse and validate new pickup date
- Get order and verify status is SECURE_RETURN_SCHEDULED
- **Check reschedule count ≤ 2**

### 2. Business Checks
- Validate seller business hours for new date
- Validate pickup address (new or existing)
- Get delivery address (seller's address)
- Check Porter delivery availability

### 3. Update Order
- Update `secured_date` with new date
- Update `secured_time_slot` with new time slot
- Increment `max_secured_reschedule_count`
- Update `secure_pickup_address` if address changed
- Save order

## Error Scenarios

### 1. Maximum Reschedules Exceeded
```
Status: 400 Bad Request
Message: "Maximum reschedule limit (2) reached. Cannot reschedule further."
```

### 2. Invalid Order Status
```
Status: 400 Bad Request
Message: "Invalid order state for return."
```

### 3. Seller Not Operational
```
Status: 400 Bad Request
Message: "Not Operational for Secure Return, please select a different date."
```

### 4. Delivery Not Available
```
Status: 400 Bad Request
Message: "Delivery service not available for the selected date/location."
```

### 5. Invalid Address
```
Status: 400 Bad Request
Message: "Address not found or does not belong to user."
```

## Example Request
```json
{
  "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
  "new_pickup_date": "2026-03-05",
  "new_time_slot": "AFTERNOON",
  "address_id": "64f5a1b2c3d4e5f6g7h8i9j1"
}
```

## Example Response
```
Status: 200 OK
(No response body - success indicated by status code)
```

## Files Created/Modified

### New Files
1. **RescheduleSecureBean.java** - Request bean for reschedule API
   - Location: `portal-service/src/main/java/com/sorted/portal/request/beans/`

### Modified Files
1. **ManageSecure_BLService.java** - Added `/secure/reschedule` endpoint
2. **SecureReturnService.java** - Added reschedule business logic:
   - `rescheduleSecureReturn()` - Main reschedule method
   - `validateRescheduleRequest()` - Request validation
   - `updateOrderForReschedule()` - Order update logic

## Database Changes
Uses existing `Order_Details` fields:
- `secured_date` - Updated with new date
- `secured_time_slot` - Updated with new time slot
- `max_secured_reschedule_count` - Incremented (max value: 2)
- `secure_pickup_address` - Updated if address changed

## Integration Points
- **Porter API**: Checks delivery availability for new date
- **Seller Service**: Validates business hours
- **Address Service**: Validates pickup address
- **User Service**: Validates customer

## Testing Checklist
- [ ] Successful reschedule with new date and time
- [ ] Successful reschedule with address change
- [ ] Reschedule count increments correctly
- [ ] Maximum reschedule limit (2) enforced
- [ ] Invalid order status rejected
- [ ] Invalid date rejected
- [ ] Seller non-operational date rejected
- [ ] Invalid address rejected
- [ ] Porter delivery unavailable handled
- [ ] Customer-only access enforced
