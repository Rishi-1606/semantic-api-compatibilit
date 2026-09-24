'use strict';

/**
 * Proxy route integration tests — Phase 10.
 *
 * Strategy: use top-level jest.mock() for all three modules that need mocking
 * (backendClient, ruleCache, axios). A single express app is built once for
 * all proxy describe blocks. Each test configures its own mock return values
 * via beforeEach / per-test overrides.
 *
 * Why a single app? jest.resetModules() inside beforeAll() creates a new module
 * graph on each describe but the top-level jest.mock() calls become stale. The
 * safe pattern is: mock everything at the top level, build the app once, and
 * reset mock state between tests with jest.clearAllMocks() + per-test setup.
 */

// These three mocks MUST be declared before any require() of files that
// transitively require them.
jest.mock('../../src/clients/backendClient');
jest.mock('../../src/cache/ruleCache');
jest.mock('axios');

const request = require('supertest');
const express = require('express');
const axios   = require('axios');

const backendClient = require('../../src/clients/backendClient');
const ruleCache     = require('../../src/cache/ruleCache');

// Build a single express app for all tests
const app = express();
app.use(express.json());
app.use('/proxy', require('../../src/routes/proxy'));

// ── Test data helpers ─────────────────────────────────────────────────

const makeRule = (id, srcPath, tgtPath, operation = 'RENAME', extra = {}) => ({
  id,
  ruleStatus:   'APPROVED',
  operation,
  sourcePath:   srcPath,
  targetPath:   tgtPath,
  parameters:   '{}',
  confidence:   0.95,
  ...extra,
});

const CUSTOMER_RULES = [
  makeRule(1, 'name',   'fullName'),
  makeRule(2, 'mobile', 'phoneNumber'),
  makeRule(3, 'email',  'emailAddress'),
];

const ENDPOINT_PAIR = {
  srcEndpointId:   1,
  tgtEndpointId:   2,
  targetBaseUrl:   null,
  srcVersionLabel: 'V1',
  tgtVersionLabel: 'V2',
  srcEndpointPath: '/v1/customer',
  tgtEndpointPath: '/v2/customer',
};

// Default successful target-API mock response
const V2_RESPONSE = {
  customerId:   1001,
  fullName:     'Rishi',
  phoneNumber:  '9876543210',
  emailAddress: 'rishi@example.com',
};

// ── beforeEach: set up default "happy path" mock state ────────────────
// Individual tests override per-test as needed.
beforeEach(() => {
  jest.clearAllMocks();
  process.env.MOCK_API_V2_URL = 'http://mock-v2:8082';

  // Default: cache miss
  ruleCache.get.mockReturnValue(null);
  ruleCache.set.mockImplementation(() => {});

  // Default: backend resolves successfully
  backendClient.resolveEndpointPair.mockResolvedValue(ENDPOINT_PAIR);
  backendClient.getApprovedRules.mockResolvedValue(CUSTOMER_RULES);

  // Default: target API responds 201 with V2 body
  axios.mockResolvedValue({ status: 201, data: V2_RESPONSE });
});

// ── POST transformation — happy path ─────────────────────────────────
describe('POST /proxy/customer/v1 — full transformation round-trip', () => {

  test('transforms V1 request body → V2, then reverse-transforms V2 response → V1', async () => {
    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' });

    expect(res.status).toBe(201);

    // Response must be in V1 format (fullName/phoneNumber/emailAddress reversed)
    expect(res.body.name).toBe('Rishi');
    expect(res.body.mobile).toBe('9876543210');
    expect(res.body.email).toBe('rishi@example.com');

    // V2-specific fields must be removed from response
    expect(res.body.fullName).toBeUndefined();
    expect(res.body.phoneNumber).toBeUndefined();
    expect(res.body.emailAddress).toBeUndefined();

    // customerId has no reverse rule — preserved as-is (V2 extra field documented limitation)
    expect(res.body.customerId).toBe(1001);
  });

  test('forwards the transformed body to the target URL', async () => {
    await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' });

    expect(axios).toHaveBeenCalledTimes(1);
    const axiosCall = axios.mock.calls[0][0];
    expect(axiosCall.method).toBe('post');
    // The body sent to V2 must be in V2 format
    expect(axiosCall.data.fullName).toBe('Rishi');
    expect(axiosCall.data.phoneNumber).toBe('9876543210');
    expect(axiosCall.data.emailAddress).toBe('rishi@example.com');
    expect(axiosCall.data.name).toBeUndefined();
  });

  test('uses X-Target-Version header when provided', async () => {
    axios.mockResolvedValueOnce({ status: 200, data: { fullName: 'Rishi' } });

    const res = await request(app)
      .post('/proxy/customer/v1')
      .set('X-Target-Version', 'V2')
      .send({ name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' });

    expect(backendClient.resolveEndpointPair).toHaveBeenCalledWith(
      'customer', 'v1', 'V2', 'POST'
    );
    expect(res.status).toBe(200);
  });

  test('defaults to V2 when X-Target-Version is not provided', async () => {
    await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' });

    expect(backendClient.resolveEndpointPair).toHaveBeenCalledWith(
      'customer', 'v1', 'V2', 'POST'
    );
  });

  test('caches endpoint pair and rules after cache miss', async () => {
    await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' });

    expect(ruleCache.set).toHaveBeenCalledTimes(1);
    const cacheSetCall = ruleCache.set.mock.calls[0];
    expect(cacheSetCall[0]).toContain('customer'); // key includes api name
    expect(cacheSetCall[1].endpointPair).toEqual(ENDPOINT_PAIR);
    expect(cacheSetCall[1].approvedRules).toEqual(CUSTOMER_RULES);
  });
});

// ── Cache HIT path ────────────────────────────────────────────────────
describe('POST /proxy/customer/v1 — cache HIT', () => {

  test('does not call backend when cache is populated', async () => {
    ruleCache.get.mockReturnValue({ endpointPair: ENDPOINT_PAIR, approvedRules: CUSTOMER_RULES });

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' });

    expect(backendClient.resolveEndpointPair).not.toHaveBeenCalled();
    expect(backendClient.getApprovedRules).not.toHaveBeenCalled();
    expect(res.status).toBe(201);
    expect(res.body.name).toBe('Rishi');
  });
});

// ── Error paths ───────────────────────────────────────────────────────
describe('Proxy error handling', () => {

  test('returns 404 when endpoint pair not found in backend', async () => {
    backendClient.resolveEndpointPair.mockResolvedValueOnce(null);

    const res = await request(app)
      .post('/proxy/unknown/v1')
      .send({ name: 'Rishi' });

    expect(res.status).toBe(404);
    expect(res.body.status).toBe('ENDPOINT_NOT_FOUND');
  });

  test('returns 503 when backend is unreachable during endpoint resolution', async () => {
    backendClient.resolveEndpointPair.mockRejectedValueOnce(new Error('ECONNREFUSED'));

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi' });

    expect(res.status).toBe(503);
    expect(res.body.status).toBe('BACKEND_ERROR');
  });

  test('returns 503 when backend is unreachable during rule fetch', async () => {
    backendClient.getApprovedRules.mockRejectedValueOnce(new Error('ECONNREFUSED'));

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi' });

    expect(res.status).toBe(503);
    expect(res.body.status).toBe('BACKEND_ERROR');
  });

  test('returns 502 NO_RULES when no approved rules exist', async () => {
    backendClient.getApprovedRules.mockResolvedValueOnce([]);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi' });

    expect(res.status).toBe(502);
    expect(res.body.status).toBe('NO_RULES');
  });

  test('forwards 4xx error from target API as-is (no reverse transform)', async () => {
    axios.mockResolvedValueOnce({
      status: 400,
      data:   { error: 'Bad Request', details: 'Unknown field name. V2 uses fullName.' },
    });

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '999', email: 'a@b.com' });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('Bad Request');
    // No reverse transformation is applied to error responses
    expect(res.body.fullName).toBeUndefined();
  });

  test('returns 504 when target API times out', async () => {
    const timeoutErr = new Error('timeout');
    timeoutErr.code  = 'ECONNABORTED';
    axios.mockRejectedValueOnce(timeoutErr);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '999', email: 'a@b.com' });

    expect(res.status).toBe(504);
    expect(res.body.status).toBe('TIMEOUT');
  });

  test('returns 502 TARGET_ERROR when target API is unreachable (non-timeout)', async () => {
    const connErr = new Error('ECONNREFUSED');
    connErr.code  = 'ECONNREFUSED';
    axios.mockRejectedValueOnce(connErr);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '999', email: 'a@b.com' });

    expect(res.status).toBe(502);
    expect(res.body.status).toBe('TARGET_ERROR');
  });

  test('returns 502 TRANSFORM_ERROR when ENUM_MAPPING has no mapping for input value', async () => {
    const badRules = [
      makeRule(99, 'status', 'statusCode', 'ENUM_MAPPING', {
        parameters: '{"mappings":{"active":"A"}}',
      }),
    ];
    backendClient.getApprovedRules.mockResolvedValueOnce(badRules);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ status: 'unknown_value' });

    expect(res.status).toBe(502);
    expect(res.body.status).toBe('TRANSFORM_ERROR');
  });
});

// ── Security: APPROVED-only execution ────────────────────────────────
describe('Proxy security — APPROVED-only rule execution', () => {

  test('rejects GENERATED rule — executor security guard throws 502', async () => {
    const generatedRule = makeRule(10, 'name', 'fullName', 'RENAME', { ruleStatus: 'GENERATED' });
    backendClient.getApprovedRules.mockResolvedValueOnce([generatedRule]);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '999', email: 'a@b.com' });

    expect(res.status).toBe(502);
    expect(res.body.status).toBe('TRANSFORM_ERROR');
  });

  test('rejects REJECTED rule — executor security guard throws 502', async () => {
    const rejectedRule = makeRule(11, 'name', 'fullName', 'RENAME', { ruleStatus: 'REJECTED' });
    backendClient.getApprovedRules.mockResolvedValueOnce([rejectedRule]);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '999', email: 'a@b.com' });

    expect(res.status).toBe(502);
    expect(res.body.status).toBe('TRANSFORM_ERROR');
  });

  test('rejects INVALID rule — executor security guard throws 502', async () => {
    const invalidRule = makeRule(12, 'name', 'fullName', 'RENAME', { ruleStatus: 'INVALID' });
    backendClient.getApprovedRules.mockResolvedValueOnce([invalidRule]);

    const res = await request(app)
      .post('/proxy/customer/v1')
      .send({ name: 'Rishi', mobile: '999', email: 'a@b.com' });

    expect(res.status).toBe(502);
    expect(res.body.status).toBe('TRANSFORM_ERROR');
  });
});

// ── GET — no body transformation (Phase 10 limitation) ───────────────
describe('GET /proxy/customer/v1 — body not transformed', () => {

  test('GET request is forwarded and response is reverse-transformed', async () => {
    axios.mockResolvedValueOnce({
      status: 200,
      data:   { customerId: 1001, fullName: 'Rishi', phoneNumber: '999', emailAddress: 'a@b.com' },
    });

    const res = await request(app).get('/proxy/customer/v1');

    expect(res.status).toBe(200);
    // axios is called with GET method and no request body transformation
    expect(axios).toHaveBeenCalledTimes(1);
    const axiosCall = axios.mock.calls[0][0];
    expect(axiosCall.method).toBe('get');

    // Response IS reverse-transformed (success 2xx body)
    expect(res.body.name).toBe('Rishi');
    expect(res.body.mobile).toBe('999');
  });
});
