# Secure Return API - cURL Examples

## 1. Initiate Secure Return

### Endpoint
**POST** `/secure/initiate`

### cURL Command
```bash
curl -X POST http://localhost:8080/secure/initiate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
    "order_item_ids": [
      "64f5a1b2c3d4e5f6g7h8i9j1",
      "64f5a1b2c3d4e5f6g7h8i9j2"
    ],
    "return_date": "2026-03-10",
    "time_slot": "AFTERNOON",
    "address_id": "64f5a1b2c3d4e5f6g7h8i9j3"
  }'
```

### Request Body Parameters
- **order_id** (required): The order ID containing secure items
- **order_item_ids** (required): Array of order item IDs to return
- **return_date** (required): Pickup date in YYYY-MM-DD format
- **time_slot** (required): Time slot - MORNING, AFTERNOON, EVENING, or NIGHT
- **address_id** (required): Customer's pickup address ID

### Example Response
```
Status: 200 OK
(No response body)
```

---

## 2. Reschedule Secure Return

### Endpoint
**POST** `/secure/reschedule`

### cURL Command
```bash
curl -X POST http://localhost:8080/secure/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
    "new_pickup_date": "2026-03-15",
    "new_time_slot": "MORNING",
    "address_id": "64f5a1b2c3d4e5f6g7h8i9j3"
  }'
```

### Request Body Parameters
- **order_id** (required): The order ID to reschedule
- **new_pickup_date** (required): New pickup date in YYYY-MM-DD format
- **new_time_slot** (required): New time slot - MORNING, AFTERNOON, EVENING, or NIGHT
- **address_id** (optional): New pickup address ID (if changing address)

### Example Response
```
Status: 200 OK
(No response body)
```

---

## Time Slot Values

| Value | Time Range |
|-------|------------|
| MORNING | 09:00-12:00 |
| AFTERNOON | 12:00-15:00 |
| EVENING | 15:00-18:00 |
| NIGHT | 18:00-21:00 |

---

## Complete Examples with All Headers

### 1. Initiate Secure Return (Full Example)
```bash
curl -X POST http://localhost:8080/secure/initiate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "User-Agent: Mozilla/5.0" \
  -H "Accept: application/json" \
  -d '{
    "order_id": "507f1f77bcf86cd799439011",
    "order_item_ids": [
      "507f1f77bcf86cd799439012",
      "507f1f77bcf86cd799439013"
    ],
    "return_date": "2026-03-10",
    "time_slot": "AFTERNOON",
    "address_id": "507f1f77bcf86cd799439014"
  }'
```

### 2. Reschedule Secure Return (Full Example)
```bash
curl -X POST http://localhost:8080/secure/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "User-Agent: Mozilla/5.0" \
  -H "Accept: application/json" \
  -d '{
    "order_id": "507f1f77bcf86cd799439011",
    "new_pickup_date": "2026-03-15",
    "new_time_slot": "MORNING",
    "address_id": "507f1f77bcf86cd799439014"
  }'
```

### 3. Reschedule Without Changing Address
```bash
curl -X POST http://localhost:8080/secure/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "507f1f77bcf86cd799439011",
    "new_pickup_date": "2026-03-15",
    "new_time_slot": "EVENING"
  }'
```

---

## Testing with Postman

### 1. Initiate Secure Return

**Method:** POST  
**URL:** `http://localhost:8080/secure/initiate`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN
```

**Body (raw JSON):**
```json
{
  "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
  "order_item_ids": [
    "64f5a1b2c3d4e5f6g7h8i9j1",
    "64f5a1b2c3d4e5f6g7h8i9j2"
  ],
  "return_date": "2026-03-10",
  "time_slot": "AFTERNOON",
  "address_id": "64f5a1b2c3d4e5f6g7h8i9j3"
}
```

### 2. Reschedule Secure Return

**Method:** POST  
**URL:** `http://localhost:8080/secure/reschedule`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN
```

**Body (raw JSON):**
```json
{
  "order_id": "64f5a1b2c3d4e5f6g7h8i9j0",
  "new_pickup_date": "2026-03-15",
  "new_time_slot": "MORNING",
  "address_id": "64f5a1b2c3d4e5f6g7h8i9j3"
}
```

---

## Error Responses

### 400 Bad Request - Missing Required Field
```json
{
  "error": "Missing required field: order_id"
}
```

### 400 Bad Request - Maximum Reschedules Exceeded
```json
{
  "error": "Maximum reschedule limit (2) reached. Cannot reschedule further."
}
```

### 400 Bad Request - Invalid Order Status
```json
{
  "error": "Invalid order state for return."
}
```

### 400 Bad Request - Seller Not Operational
```json
{
  "error": "Not Operational for Secure Return, please select a different date."
}
```

### 401 Unauthorized
```json
{
  "error": "Unauthorized"
}
```

### 404 Not Found - Order Not Found
```json
{
  "error": "Order not found"
}
```

---

## Notes

1. **Base URL**: Replace `http://localhost:8080` with your actual server URL
2. **JWT Token**: Replace `YOUR_JWT_TOKEN` with a valid JWT token obtained from login
3. **IDs**: Replace example IDs with actual MongoDB ObjectIds from your database
4. **Dates**: Use future dates in YYYY-MM-DD format
5. **Time Slots**: Must be one of: MORNING, AFTERNOON, EVENING, NIGHT
6. **Reschedule Limit**: Maximum 2 reschedules per order
7. **Order Status**: 
   - For initiate: Order must be in DELIVERED status
   - For reschedule: Order must be in SECURE_RETURN_SCHEDULED status

---

## Testing Workflow

### Complete Flow Test
```bash
# Step 1: Initiate secure return
curl -X POST http://localhost:8080/secure/initiate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "ORDER_ID",
    "order_item_ids": ["ITEM_ID_1", "ITEM_ID_2"],
    "return_date": "2026-03-10",
    "time_slot": "AFTERNOON",
    "address_id": "ADDRESS_ID"
  }'

# Step 2: First reschedule (count = 1)
curl -X POST http://localhost:8080/secure/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "ORDER_ID",
    "new_pickup_date": "2026-03-12",
    "new_time_slot": "MORNING"
  }'

# Step 3: Second reschedule (count = 2)
curl -X POST http://localhost:8080/secure/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "ORDER_ID",
    "new_pickup_date": "2026-03-15",
    "new_time_slot": "EVENING"
  }'

# Step 4: Third reschedule attempt (should fail - max limit reached)
curl -X POST http://localhost:8080/secure/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "order_id": "ORDER_ID",
    "new_pickup_date": "2026-03-20",
    "new_time_slot": "AFTERNOON"
  }'
# Expected: 400 Bad Request - Maximum reschedule limit reached
```
