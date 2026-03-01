# Secure Return Appraisal API

## Overview
API endpoint for sellers to appraise returned secure products and calculate refund amounts based on product condition rating.

## Endpoint
**POST** `/secure/appraise`

## Rating System
The appraisal uses a 1-5 rating system based on product condition:

| Rating | Condition | Refund Percentage |
|--------|-----------|-------------------|
| 5 | Excellent | 50% |
| 4 | Good | 40% |
| 3 | Fair | 30% |
| 2 | Poor | 20% |
| 1 | Very Poor | 10% |

The refund percentage is calculated on the `selling_price_after_discount` of the returned items.

## Request Body

### Option 1: Using Rating (Recommended)
```json
{
  "order_id": "string",
  "rating": 1-5,
  "remark": "string (optional)"
}
```

### Option 2: Using Custom Amount
```json
{
  "order_id": "string",
  "amount": 1234.56,
  "remark": "string (optional)"
}
```

### Option 3: Using Both (Amount takes precedence)
```json
{
  "order_id": "string",
  "rating": 4,
  "amount": 1500.00,
  "remark": "string (optional)"
}
```

## Parameters

### Required (at least one)
- **order_id** (required): The order ID to appraise
- **rating** (optional): Product condition rating from 1 to 5
- **amount** (optional): Custom refund amount

### Optional
- **remark**: Seller's comments about the product condition

## Business Rules

### 1. Rating or Amount Required
- Either `rating` OR `amount` must be provided
- If both provided, `amount` takes precedence
- Rating must be between 1 and 5

### 2. Order Status Validation
- Order must be in `SECURE_RETURN_COMPLETED` status
- Only sellers can appraise returns

### 3. Refund Calculation
- **With Rating**: Refund = Sum(selling_price_after_discount) × (rating × 10%)
- **With Amount**: Uses the provided amount directly

### 4. Item Updates
- If rating provided: Updates `secure_item_rating` for all returned items
- Order status changes to `SECURE_RETURN_APPRAISED`
- Item status changes to `SECURE_RETURN_APPRAISED`

## Examples

### Example 1: Appraise with Rating 5 (Excellent - 50%)
```bash
curl -X POST http://localhost:8080/secure/appraise \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SELLER_JWT_TOKEN" \
  -d '{
    "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
    "rating": 5,
    "remark": "Product returned in excellent condition with all original packaging"
  }'
```

**Calculation Example:**
- Item 1: ₹1000
- Item 2: ₹500
- Total: ₹1500
- Rating 5 = 50%
- **Refund: ₹750**

### Example 2: Appraise with Rating 3 (Fair - 30%)
```bash
curl -X POST http://localhost:8080/secure/appraise \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SELLER_JWT_TOKEN" \
  -d '{
    "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
    "rating": 3,
    "remark": "Minor wear and tear, packaging damaged"
  }'
```

**Calculation Example:**
- Total item price: ₹1500
- Rating 3 = 30%
- **Refund: ₹450**

### Example 3: Appraise with Custom Amount
```bash
curl -X POST http://localhost:8080/secure/appraise \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SELLER_JWT_TOKEN" \
  -d '{
    "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
    "amount": 600.00,
    "remark": "Custom refund amount negotiated with customer"
  }'
```

### Example 4: Minimal Request (Rating Only)
```bash
curl -X POST http://localhost:8080/secure/appraise \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SELLER_JWT_TOKEN" \
  -d '{
    "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
    "rating": 4
  }'
```

## Response

### Success Response
```
Status: 200 OK
(No response body)
```

## Error Responses

### 400 Bad Request - Missing Order ID
```json
{
  "error": "Missing required field: order_id"
}
```

### 400 Bad Request - No Rating or Amount
```json
{
  "error": "Either rating or amount must be provided"
}
```

### 400 Bad Request - Invalid Rating
```json
{
  "error": "Rating must be between 1 and 5"
}
```

### 400 Bad Request - Invalid Amount
```json
{
  "error": "Invalid amount"
}
```

### 400 Bad Request - Invalid Order Status
```json
{
  "error": "Invalid order state for appraisal"
}
```

### 403 Forbidden - Not a Seller
```json
{
  "error": "Access denied. Only sellers can appraise returns"
}
```

### 404 Not Found - Order Not Found
```json
{
  "error": "Order not found"
}
```

### 404 Not Found - No Returned Items
```json
{
  "error": "No returned items found for this order"
}
```

## Flow Diagram

```
1. Seller receives returned product
2. Seller inspects product condition
3. Seller assigns rating (1-5) or custom amount
4. Seller calls /secure/appraise API
5. System validates seller and order status
6. System calculates refund amount (if rating provided)
7. System updates order and items with appraisal
8. Order status → SECURE_RETURN_APPRAISED
9. Item status → SECURE_RETURN_APPRAISED
10. Item rating → Updated (if rating provided)
```

## Calculation Logic

### Rating-Based Calculation
```
Refund Amount = Σ(selling_price_after_discount) × (rating × 10) / 100

Examples:
- Rating 5: Total × 50% = Total × 0.5
- Rating 4: Total × 40% = Total × 0.4
- Rating 3: Total × 30% = Total × 0.3
- Rating 2: Total × 20% = Total × 0.2
- Rating 1: Total × 10% = Total × 0.1
```

### Example Calculation
```
Order Items:
- Item 1: ₹2000 (selling_price_after_discount)
- Item 2: ₹1500 (selling_price_after_discount)
- Item 3: ₹500 (selling_price_after_discount)

Total: ₹4000

Rating 5 (Excellent):
Refund = ₹4000 × 50% = ₹2000

Rating 4 (Good):
Refund = ₹4000 × 40% = ₹1600

Rating 3 (Fair):
Refund = ₹4000 × 30% = ₹1200

Rating 2 (Poor):
Refund = ₹4000 × 20% = ₹800

Rating 1 (Very Poor):
Refund = ₹4000 × 10% = ₹400
```

## Database Updates

### Order_Details
- `status` → `SECURE_RETURN_APPRAISED`
- (Future fields to add):
  - `secure_refund_amount` → Calculated/provided refund amount
  - `secure_appraisal_rating` → Rating given by seller
  - `secure_appraisal_remark` → Seller's remarks

### Order_Item
- `status` → `SECURE_RETURN_APPRAISED`
- `secure_item_rating` → Rating (if provided)

## Testing Scenarios

### Test Case 1: Rating 5 (Best Case)
```json
{
  "order_id": "ORDER_ID",
  "rating": 5,
  "remark": "Perfect condition"
}
```
Expected: 50% refund

### Test Case 2: Rating 1 (Worst Case)
```json
{
  "order_id": "ORDER_ID",
  "rating": 1,
  "remark": "Heavily damaged"
}
```
Expected: 10% refund

### Test Case 3: Custom Amount
```json
{
  "order_id": "ORDER_ID",
  "amount": 999.99,
  "remark": "Special case"
}
```
Expected: ₹999.99 refund

### Test Case 4: Invalid Rating
```json
{
  "order_id": "ORDER_ID",
  "rating": 6
}
```
Expected: 400 Bad Request

### Test Case 5: No Rating or Amount
```json
{
  "order_id": "ORDER_ID",
  "remark": "Just a remark"
}
```
Expected: 400 Bad Request

## Notes

1. **Seller Only**: Only sellers can appraise returns
2. **Order Status**: Order must be in `SECURE_RETURN_COMPLETED` status
3. **Rounding**: Refund amounts are rounded to 2 decimal places
4. **Flexibility**: Sellers can use rating for standard cases or custom amount for special cases
5. **Remarks Optional**: Remarks are optional but recommended for record-keeping
6. **Rating Storage**: Rating is stored in `secure_item_rating` field of Order_Item
7. **Amount Priority**: If both rating and amount provided, amount takes precedence

## Future Enhancements

1. Add fields to Order_Details:
   - `secure_refund_amount`
   - `secure_appraisal_rating`
   - `secure_appraisal_remark`

2. Add refund processing workflow
3. Add customer notification after appraisal
4. Add appraisal history tracking
5. Add photo upload for product condition documentation
