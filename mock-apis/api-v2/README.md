# Customer API V2 — Mock

A minimal Node.js/Express mock for **Customer API Version 2**.

This API uses the **updated naming convention** — it is the *target* API that a V1 client must be transformed to reach.

## Schema Difference (the core demo)

| Concept | V1 Field | V2 Field |
|---------|----------|----------|
| Full name | `name` | `fullName` |
| Phone | `mobile` | `phoneNumber` |
| Email | `email` | `emailAddress` |
| Record ID | `id` | `customerId` |

V2 **rejects** V1 field names with a `400` error, making the incompatibility visible.

## Port

**8082**

## Running

```bash
npm install
npm start        # production
npm run dev      # with nodemon
```

## Endpoints

### `POST /v2/customer` — Create a customer

**Request:**
```json
{
  "fullName": "Rishi",
  "phoneNumber": "9876543210",
  "emailAddress": "rishi@example.com"
}
```

**Response `201`:**
```json
{
  "customerId": 1001,
  "fullName": "Rishi",
  "phoneNumber": "9876543210",
  "emailAddress": "rishi@example.com"
}
```

**If V1 fields sent without transformation — `400`:**
```json
{
  "status": "ERROR",
  "message": "Validation failed",
  "errors": [
    "Unknown field \"name\". V2 uses \"fullName\". This request appears to be in V1 format — transform it first."
  ]
}
```

### `GET /v2/customers` — List all customers

### `GET /v2/customer/:id` — Get a customer by customerId

### `GET /health` — Health check

```bash
curl http://localhost:8082/health
```
