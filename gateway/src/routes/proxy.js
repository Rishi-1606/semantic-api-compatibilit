'use strict';

/**
 * Phase 10 — Runtime transformation proxy.
 *
 * Intercepts API requests in legacy format, transforms them to the target
 * API format using ONLY APPROVED transformation rules, forwards to the target
 * API, reverse-transforms the response, and returns the result to the client.
 *
 * Route: ALL /proxy/:endpoint/:version[/*]
 *
 * Target version selection:
 *   - If the X-Target-Version request header is present, use its value.
 *   - Otherwise default to V2 (the current demo scenario: V1 → V2).
 *   - The framework does not claim a complete multi-version routing strategy.
 *
 * Current limitations (Phase 10 scope):
 *   - GET request body transformation is out of scope (GET bodies are non-standard).
 *   - Query-parameter and path-parameter transformation are out of scope.
 *   - Only JSON request/response bodies are transformed.
 *
 * Safety guarantees:
 *   - Only APPROVED rules are executed (checked by executor + backend filter).
 *   - No eval(), no new Function(), no dynamic code.
 *   - Fail-closed on transformation errors (502 returned, request not forwarded).
 *   - Endpoint-pair isolation enforced via explicit srcEndpointId/tgtEndpointId lookup.
 */

const express  = require('express');
const axios    = require('axios');
const router   = express.Router();

const backendClient = require('../clients/backendClient');
const ruleCache     = require('../cache/ruleCache');
const executor      = require('../executor/transformationExecutor');

const TARGET_TIMEOUT_MS = parseInt(process.env.TARGET_TIMEOUT_MS || '10000', 10);

// ── Helpers ───────────────────────────────────────────────────────────

/**
 * Resolve the base URL for a target API version.
 *
 * Priority:
 *   1. ApiVersion.baseUrl from the backend registry (set when the API was registered)
 *   2. Environment variable fallback (MOCK_API_V1_URL, MOCK_API_V2_URL, etc.)
 *
 * @param {string|null} registryBaseUrl  - baseUrl from the ApiVersion record (may be null)
 * @param {string}      tgtVersionLabel  - e.g. "V2"
 * @returns {string|null}
 */
function resolveTargetBaseUrl(registryBaseUrl, tgtVersionLabel) {
  if (registryBaseUrl) return registryBaseUrl;
  const label = (tgtVersionLabel || '').toLowerCase();
  if (label === 'v1') return process.env.MOCK_API_V1_URL || 'http://localhost:8081';
  if (label === 'v2') return process.env.MOCK_API_V2_URL || 'http://localhost:8082';
  return null;
}

/**
 * Build the cache key for a given proxy request.
 *
 * @param {string} apiName
 * @param {string} srcVersion
 * @param {string} tgtVersion
 * @param {string} method
 * @returns {string}
 */
function cacheKey(apiName, srcVersion, tgtVersion, method) {
  return [
    apiName.toLowerCase(),
    srcVersion.toLowerCase(),
    tgtVersion.toLowerCase(),
    method.toUpperCase(),
  ].join(':');
}

// ── Route handler ─────────────────────────────────────────────────────

/**
 * ALL /proxy/:endpoint/:version[/*]
 *
 * :endpoint — logical API name (must match a registered Api.name, case-insensitive)
 * :version  — source/client version (e.g. "v1")
 *
 * Optional header: X-Target-Version (default "V2")
 */
router.all('/:endpoint/:version*', async (req, res) => {
  const { endpoint, version } = req.params;
  const method = req.method.toUpperCase();

  // Determine target version from header or default
  const xTargetVersion  = req.headers['x-target-version'];
  const targetVersion   = xTargetVersion || 'V2';
  const sourceVersion   = version;

  console.log(
    `[Gateway] ${method} /proxy/${endpoint}/${version} ` +
    `→ target: ${targetVersion} (${xTargetVersion ? 'header' : 'default'})`
  );

  // ── Step 1: Resolve endpoint pair (cached) ────────────────────────
  const key = cacheKey(endpoint, sourceVersion, targetVersion, method);
  let cached = ruleCache.get(key);
  let endpointPair, approvedRules;

  if (cached) {
    console.log(`[Gateway] Cache HIT — key: ${key}`);
    endpointPair  = cached.endpointPair;
    approvedRules = cached.approvedRules;
  } else {
    console.log(`[Gateway] Cache MISS — key: ${key} — querying backend`);

    try {
      endpointPair = await backendClient.resolveEndpointPair(
        endpoint, sourceVersion, targetVersion, method
      );
    } catch (err) {
      console.error('[Gateway] Backend unreachable (endpoint resolution):', err.message);
      return res.status(503).json({
        status:    'BACKEND_ERROR',
        message:   'Could not retrieve endpoint information from backend: ' + err.message,
        timestamp: new Date().toISOString(),
      });
    }

    if (!endpointPair) {
      return res.status(404).json({
        status:    'ENDPOINT_NOT_FOUND',
        message:   `No registered ${method} endpoint pair found for ` +
                   `'${endpoint}' ${sourceVersion} → ${targetVersion}. ` +
                   'Ensure both API versions are registered in the backend.',
        timestamp: new Date().toISOString(),
      });
    }

    // ── Step 2: Fetch APPROVED rules ──────────────────────────────
    try {
      approvedRules = await backendClient.getApprovedRules(
        endpointPair.srcEndpointId,
        endpointPair.tgtEndpointId
      );
    } catch (err) {
      console.error('[Gateway] Backend unreachable (rule fetch):', err.message);
      return res.status(503).json({
        status:    'BACKEND_ERROR',
        message:   'Could not retrieve transformation rules from backend: ' + err.message,
        timestamp: new Date().toISOString(),
      });
    }

    // Cache the pair and rules together
    ruleCache.set(key, { endpointPair, approvedRules });
    console.log(
      `[Gateway] Cached ${approvedRules.length} APPROVED rules for key: ${key} ` +
      `(TTL ${process.env.RULE_CACHE_TTL_MS || 60000}ms)`
    );
  }

  // ── Step 3: Verify at least one APPROVED rule exists ──────────────
  if (!approvedRules || approvedRules.length === 0) {
    return res.status(502).json({
      status:        'NO_RULES',
      message:       `No approved transformation rules found for ` +
                     `${method} /${endpoint}/${sourceVersion} → ${targetVersion}. ` +
                     'Generate and approve rules via Phase 8–9 before using the gateway.',
      srcEndpointId: endpointPair.srcEndpointId,
      tgtEndpointId: endpointPair.tgtEndpointId,
      timestamp:     new Date().toISOString(),
    });
  }

  // ── Step 4: Transform request body ────────────────────────────────
  // Scope limitation: GET and DELETE bodies are not transformed (non-standard usage).
  // Query-parameter and path-parameter transformation are also out of scope.
  const hasBody = req.body && typeof req.body === 'object' &&
                  Object.keys(req.body).length > 0;
  const shouldTransformRequest = hasBody &&
                                 method !== 'GET' &&
                                 method !== 'DELETE';

  let transformedBody = req.body || {};
  if (shouldTransformRequest) {
    try {
      transformedBody = executor.transformRequest(req.body, approvedRules);
      console.log(
        `[Gateway] Request transformed: ` +
        `${JSON.stringify(req.body)} → ${JSON.stringify(transformedBody)}`
      );
    } catch (err) {
      console.error('[Gateway] Request transformation failed:', err.message);
      return res.status(502).json({
        status:    'TRANSFORM_ERROR',
        message:   err.message,
        phase:     'request',
        timestamp: new Date().toISOString(),
      });
    }
  }

  // ── Step 5: Resolve target URL ─────────────────────────────────────
  const targetBaseUrl = resolveTargetBaseUrl(
    endpointPair.targetBaseUrl,
    endpointPair.tgtVersionLabel
  );

  if (!targetBaseUrl) {
    return res.status(502).json({
      status:    'CONFIG_ERROR',
      message:   `Cannot resolve base URL for target version '${targetVersion}'. ` +
                 'Set MOCK_API_V2_URL in the gateway .env or register a baseUrl for the API version.',
      timestamp: new Date().toISOString(),
    });
  }

  // Build full target URL: base + registered endpoint path + any extra path segments
  const registeredTargetPath = endpointPair.tgtEndpointPath || '';
  const extraPath            = req.params[0] || ''; // wildcard capture after /:version
  const queryString          = req.url.includes('?')
    ? req.url.slice(req.url.indexOf('?'))
    : '';
  const fullTargetUrl = `${targetBaseUrl}${registeredTargetPath}${extraPath}${queryString}`;

  console.log(`[Gateway] Forwarding ${method} → ${fullTargetUrl}`);

  // ── Step 6: Forward to target API ─────────────────────────────────
  let targetResponse;
  try {
    targetResponse = await axios({
      method:  method.toLowerCase(),
      url:     fullTargetUrl,
      data:    (method !== 'GET' && method !== 'DELETE') ? transformedBody : undefined,
      headers: {
        'Content-Type':    'application/json',
        'X-Forwarded-By':  'semantic-api-gateway/phase10',
      },
      timeout:        TARGET_TIMEOUT_MS,
      validateStatus: () => true, // never throw on HTTP errors — forward status as-is
    });
  } catch (err) {
    if (err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT') {
      console.error('[Gateway] Target API timeout:', err.message);
      return res.status(504).json({
        status:     'TIMEOUT',
        message:    `Target API did not respond within ${TARGET_TIMEOUT_MS}ms`,
        targetUrl:  fullTargetUrl,
        timestamp:  new Date().toISOString(),
      });
    }
    console.error('[Gateway] Target API unreachable:', err.message);
    return res.status(502).json({
      status:    'TARGET_ERROR',
      message:   `Target API unavailable: ${err.message}`,
      targetUrl: fullTargetUrl,
      timestamp: new Date().toISOString(),
    });
  }

  const tgtStatus = targetResponse.status;
  const tgtBody   = targetResponse.data;

  console.log(`[Gateway] Target responded: HTTP ${tgtStatus}`);

  // ── Step 7: Transform response body ───────────────────────────────
  // Only reverse-transform on 2xx success responses from the target API.
  // Error responses (4xx, 5xx) are forwarded as-is: they reflect the
  // target API's own error logic, not a transformation concern.
  // Array responses are forwarded as-is (Phase 10 scope: object bodies only).
  const isSuccessResponse  = tgtStatus >= 200 && tgtStatus < 300;
  const isObjectResponse   = tgtBody && typeof tgtBody === 'object' && !Array.isArray(tgtBody);
  const shouldTransformResponse = isSuccessResponse && isObjectResponse;

  let responseBody = tgtBody;
  if (shouldTransformResponse) {
    try {
      responseBody = executor.transformResponse(tgtBody, approvedRules);
      console.log(
        `[Gateway] Response transformed: ` +
        `${JSON.stringify(tgtBody)} → ${JSON.stringify(responseBody)}`
      );
    } catch (err) {
      // Fail-safe for response: log the error but return the V2 response rather
      // than crashing — the client receives the raw target response with a warning header.
      console.error(
        '[Gateway] Response transformation failed — returning V2 response as-is:', err.message
      );
      res.setHeader('X-Gateway-Warning', 'Response transformation failed: ' + err.message);
      responseBody = tgtBody;
    }
  }

  // ── Step 8: Return to client ───────────────────────────────────────
  return res.status(tgtStatus).json(responseBody);
});

module.exports = router;
