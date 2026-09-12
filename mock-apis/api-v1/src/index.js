'use strict';

/**
 * Customer API — Version 1 (Mock)
 *
 * Schema V1 field names:
 *   Request : { name, mobile, email }
 *   Response: { id, name, mobile, email }
 *
 * This API deliberately uses the *old* naming convention so that the
 * Semantic API Compatibility Framework can demonstrate automatic transformation
 * to/from V2 (which uses fullName, phoneNumber, emailAddress, customerId).
 *
 * Port: 8081
 */

const express = require('express');
const morgan  = require('morgan');

const app  = express();
const PORT = process.env.PORT || 8081;

// ── Middleware ────────────────────────────────────────────────
app.use(morgan('combined'));
app.use(express.json());

// ── In-memory store (id counter) ─────────────────────────────
let nextId = 101;
const customers = []; // kept for GET /v1/customers (bonus endpoint)

// ── Helpers ──────────────────────────────────────────────────

/**
 * Validate a V1 customer request body.
 * Returns an array of error strings; empty array = valid.
 */
function validateV1Customer(body) {
  const errors = [];
  if (!body || typeof body !== 'object') {
    errors.push('Request body must be a JSON object');
    return errors;
  }
  if (!body.name || typeof body.name !== 'string' || body.name.trim() === '') {
    errors.push('"name" is required and must be a non-empty string');
  }
  if (!body.mobile || typeof body.mobile !== 'string' || body.mobile.trim() === '') {
    errors.push('"mobile" is required and must be a non-empty string');
  }
  if (!body.email || typeof body.email !== 'string' || !body.email.includes('@')) {
    errors.push('"email" is required and must be a valid email address');
  }
  return errors;
}

// ── Routes ───────────────────────────────────────────────────

/**
 * GET /
 * Service info — useful for quick sanity checks.
 */
app.get('/', (req, res) => {
  res.json({
    service:     'Customer API',
    version:     'V1',
    port:        PORT,
    schema:      'Legacy — uses: name, mobile, email, id',
    endpoints: [
      'POST /v1/customer   — Create a customer (V1 schema)',
      'GET  /v1/customers  — List all customers',
      'GET  /v1/customer/:id — Get a customer by id',
      'GET  /health        — Health check',
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
    service:   'Customer API V1',
    version:   'V1',
    port:      PORT,
    timestamp: new Date().toISOString(),
  });
});

/**
 * POST /v1/customer
 *
 * Create a new customer (V1 schema).
 *
 * Request body:
 *   { "name": "Rishi", "mobile": "9876543210", "email": "rishi@example.com" }
 *
 * Response 201:
 *   { "id": 101, "name": "Rishi", "mobile": "9876543210", "email": "rishi@example.com" }
 */
app.post('/v1/customer', (req, res) => {
  console.log(`[V1] POST /v1/customer — body:`, req.body);

  // Validate
  const errors = validateV1Customer(req.body);
  if (errors.length > 0) {
    return res.status(400).json({
      status:  'ERROR',
      message: 'Validation failed',
      errors,
      schema:  'Expected V1 fields: name (string), mobile (string), email (string)',
    });
  }

  // Build the stored record
  const customer = {
    id:     nextId++,
    name:   req.body.name.trim(),
    mobile: req.body.mobile.trim(),
    email:  req.body.email.trim(),
  };

  customers.push(customer);

  console.log(`[V1] Customer created:`, customer);

  // Return V1 response — note field names: id, name, mobile, email
  return res.status(201).json(customer);
});

/**
 * GET /v1/customers
 * List all customers (V1 schema).
 */
app.get('/v1/customers', (req, res) => {
  res.json({
    count:     customers.length,
    customers,
  });
});

/**
 * GET /v1/customer/:id
 * Get a single customer by numeric id (V1 schema).
 */
app.get('/v1/customer/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const customer = customers.find(c => c.id === id);
  if (!customer) {
    return res.status(404).json({
      status:  'ERROR',
      message: `Customer with id ${id} not found`,
    });
  }
  res.json(customer);
});

// ── 404 ──────────────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).json({
    status:  'ERROR',
    message: `${req.method} ${req.path} not found on Customer API V1`,
    hint:    'Use POST /v1/customer to create a customer',
  });
});

// ── Start ─────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`[Customer API V1] ✅  Running on port ${PORT}`);
  console.log(`[Customer API V1]    POST /v1/customer`);
  console.log(`[Customer API V1]    GET  /v1/customers`);
  console.log(`[Customer API V1]    GET  /health`);
  console.log(`[Customer API V1]    Schema: { name, mobile, email } → { id, name, mobile, email }`);
});

module.exports = app;
