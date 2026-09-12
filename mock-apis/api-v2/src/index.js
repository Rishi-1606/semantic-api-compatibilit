'use strict';

/**
 * Customer API — Version 2 (Mock)
 *
 * Schema V2 field names (deliberately different from V1):
 *   Request : { fullName, phoneNumber, emailAddress }
 *   Response: { customerId, fullName, phoneNumber, emailAddress }
 *
 * Mapping that the Semantic Compatibility Framework must discover:
 *   V1.name    → V2.fullName
 *   V1.mobile  → V2.phoneNumber
 *   V1.email   → V2.emailAddress
 *   V1.id      → V2.customerId
 *
 * This API REJECTS V1 field names — it will return 400 if you send
 * { name, mobile, email } without transformation.
 *
 * Port: 8082
 */

const express = require('express');
const morgan  = require('morgan');

const app  = express();
const PORT = process.env.PORT || 8082;

// ── Middleware ────────────────────────────────────────────────
app.use(morgan('combined'));
app.use(express.json());

// ── In-memory store ───────────────────────────────────────────
let nextId = 1001; // V2 uses a different ID range to make the difference obvious
const customers = [];

// ── Helpers ──────────────────────────────────────────────────

/**
 * Validate a V2 customer request body.
 *
 * V2 rejects V1 field names so the incompatibility is visible
 * and the transformation framework's value is clear.
 */
function validateV2Customer(body) {
  const errors   = [];
  const warnings = [];

  if (!body || typeof body !== 'object') {
    errors.push('Request body must be a JSON object');
    return { errors, warnings };
  }

  // Detect if caller accidentally sent V1 fields — helpful error message
  if (body.name !== undefined && body.fullName === undefined) {
    errors.push(
      'Unknown field "name". V2 uses "fullName". ' +
      'This request appears to be in V1 format — transform it first.'
    );
  }
  if (body.mobile !== undefined && body.phoneNumber === undefined) {
    errors.push(
      'Unknown field "mobile". V2 uses "phoneNumber". ' +
      'This request appears to be in V1 format — transform it first.'
    );
  }
  if (body.email !== undefined && body.emailAddress === undefined) {
    errors.push(
      'Unknown field "email". V2 uses "emailAddress". ' +
      'This request appears to be in V1 format — transform it first.'
    );
  }
  if (errors.length > 0) return { errors, warnings };

  // Validate required V2 fields
  if (!body.fullName || typeof body.fullName !== 'string' || body.fullName.trim() === '') {
    errors.push('"fullName" is required and must be a non-empty string');
  }
  if (!body.phoneNumber || typeof body.phoneNumber !== 'string' || body.phoneNumber.trim() === '') {
    errors.push('"phoneNumber" is required and must be a non-empty string');
  }
  if (!body.emailAddress || typeof body.emailAddress !== 'string' || !body.emailAddress.includes('@')) {
    errors.push('"emailAddress" is required and must be a valid email address');
  }

  return { errors, warnings };
}

// ── Routes ───────────────────────────────────────────────────

/**
 * GET /
 * Service info.
 */
app.get('/', (req, res) => {
  res.json({
    service:  'Customer API',
    version:  'V2',
    port:     PORT,
    schema:   'Updated — uses: fullName, phoneNumber, emailAddress, customerId',
    note:     'This API does NOT accept V1 field names (name, mobile, email).',
    endpoints: [
      'POST /v2/customer     — Create a customer (V2 schema)',
      'GET  /v2/customers    — List all customers',
      'GET  /v2/customer/:id — Get a customer by customerId',
      'GET  /health          — Health check',
    ],
  });
});

/**
 * GET /health
 * Health check endpoint.
 */
app.get('/health', (req, res) => {
  res.json({
    status:    'UP',
    service:   'Customer API V2',
    version:   'V2',
    port:      PORT,
    timestamp: new Date().toISOString(),
  });
});

/**
 * POST /v2/customer
 *
 * Create a new customer (V2 schema).
 *
 * Request body:
 *   { "fullName": "Rishi", "phoneNumber": "9876543210", "emailAddress": "rishi@example.com" }
 *
 * Response 201:
 *   { "customerId": 1001, "fullName": "Rishi", "phoneNumber": "9876543210", "emailAddress": "rishi@example.com" }
 *
 * Error 400 (if V1 fields sent without transformation):
 *   { "status": "ERROR", "message": "Validation failed", "errors": [...] }
 */
app.post('/v2/customer', (req, res) => {
  console.log(`[V2] POST /v2/customer — body:`, req.body);

  const { errors, warnings } = validateV2Customer(req.body);
  if (errors.length > 0) {
    return res.status(400).json({
      status:  'ERROR',
      message: 'Validation failed',
      errors,
      schema:  'Expected V2 fields: fullName (string), phoneNumber (string), emailAddress (string)',
    });
  }

  const customer = {
    customerId:   nextId++,
    fullName:     req.body.fullName.trim(),
    phoneNumber:  req.body.phoneNumber.trim(),
    emailAddress: req.body.emailAddress.trim(),
  };

  customers.push(customer);

  console.log(`[V2] Customer created:`, customer);

  return res.status(201).json(customer);
});

/**
 * GET /v2/customers
 * List all customers (V2 schema).
 */
app.get('/v2/customers', (req, res) => {
  res.json({
    count:     customers.length,
    customers,
  });
});

/**
 * GET /v2/customer/:id
 * Get a single customer by customerId (V2 schema).
 */
app.get('/v2/customer/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const customer = customers.find(c => c.customerId === id);
  if (!customer) {
    return res.status(404).json({
      status:  'ERROR',
      message: `Customer with customerId ${id} not found`,
    });
  }
  res.json(customer);
});

// ── 404 ──────────────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).json({
    status:  'ERROR',
    message: `${req.method} ${req.path} not found on Customer API V2`,
    hint:    'Use POST /v2/customer to create a customer',
  });
});

// ── Start ─────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`[Customer API V2] ✅  Running on port ${PORT}`);
  console.log(`[Customer API V2]    POST /v2/customer`);
  console.log(`[Customer API V2]    GET  /v2/customers`);
  console.log(`[Customer API V2]    GET  /health`);
  console.log(`[Customer API V2]    Schema: { fullName, phoneNumber, emailAddress } → { customerId, fullName, phoneNumber, emailAddress }`);
});

module.exports = app;
