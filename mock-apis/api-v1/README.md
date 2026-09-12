# Customer API V1 — Mock

A minimal Node.js/Express mock for **Customer API Version 1**.

This API deliberately uses the **old/legacy naming convention** so the Semantic API Compatibility Framework can demonstrate automatic field transformation to/from V2.

## Schema Difference (the core demo)

| Concept | V1 Field | V2 Field |
|---------|----------|----------|
| Full name | `name` | `fullName` |
| Phone | `mobile` | `phoneNumber` |
| Email | `email` | `emailAddress` |
| Record ID | `id` | `customerId` |

## Port

**8081**

## Running

```bash
npm install
npm start        # production
npm run dev      # with nodemon
```

## Endpoints

### `POST /v1/customer` — Create a customer

**Request:**
```json
{
  "name": "Rishi",
  "mobile": "9876543210",
  "email": "rishi@example.com"
}
```

**Response `201`:**
```json
{
  "id": 101,
  "name": "Rishi",
  "mobile": "9876543210",
  "email": "rishi@example.com"
}
```

**Validation errors `400`:**
```json
{
  "status": "ERROR",
  "message": "Validation failed",
  "errors": ["\"name\" is required and must be a non-empty string"]
}
```

### `GET /v1/customers` — List all customers

### `GET /v1/customer/:id` — Get a customer by ID

### `GET /health` — Health check

```bash
curl http://localhost:8081/health
```
