'use strict';

/**
 * Phase 10 — Backend API client.
 *
 * Provides typed access to the Spring Boot backend REST API for rule retrieval
 * and endpoint-pair resolution. Used exclusively at startup / cache-miss time;
 * NOT called on every request after the cache is populated.
 *
 * Base URL: BACKEND_URL env var (default http://localhost:8080)
 * Timeout:  TARGET_TIMEOUT_MS env var (default 10 000 ms)
 */

const axios = require('axios');

const BACKEND_URL   = process.env.BACKEND_URL   || 'http://localhost:8080';
const TIMEOUT_MS    = parseInt(process.env.TARGET_TIMEOUT_MS || '10000', 10);

const http = axios.create({
  baseURL: BACKEND_URL,
  timeout: TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
});

// ── API registry queries ──────────────────────────────────────────────

/**
 * Fetch all registered APIs.
 * Response: [{ id, name, ... }]
 */
async function getApis() {
  const { data } = await http.get('/api/apis');
  return Array.isArray(data) ? data : (data.apis || []);
}

/**
 * Fetch all versions for an API.
 * Response: [{ id, version, baseUrl, isActive, ... }]
 *
 * @param {number} apiId
 */
async function getVersionsForApi(apiId) {
  const { data } = await http.get(`/api/apis/${apiId}/versions`);
  return Array.isArray(data) ? data : (data.versions || []);
}

/**
 * Fetch all endpoints for a version.
 * Response: [{ id, httpMethod, path, apiVersionLabel, apiName, ... }]
 *
 * @param {number} versionId
 */
async function getEndpointsForVersion(versionId) {
  const { data } = await http.get(`/api/versions/${versionId}/endpoints`);
  return Array.isArray(data) ? data : (data.endpoints || []);
}

// ── Rule retrieval ────────────────────────────────────────────────────

/**
 * Fetch APPROVED transformation rules for a specific endpoint pair.
 * Uses the optional ?ruleStatus=APPROVED filter added in Phase 10.
 *
 * Response: [{ id, operation, sourcePath, targetPath, ruleStatus, parameters, ... }]
 *
 * @param {number} srcEndpointId
 * @param {number} tgtEndpointId
 * @returns {Promise<Array>} Array of rule objects (may be empty)
 */
async function getApprovedRules(srcEndpointId, tgtEndpointId) {
  const { data } = await http.get('/api/transformation-rules/endpoint-pair', {
    params: { srcEndpointId, tgtEndpointId, ruleStatus: 'APPROVED' },
  });
  return Array.isArray(data) ? data : [];
}

// ── Endpoint-pair resolution ──────────────────────────────────────────

/**
 * Resolve (srcEndpointId, tgtEndpointId, targetBaseUrl) for a given API name,
 * source version, target version, and HTTP method.
 *
 * Resolution strategy:
 *   1. Find the API by name (case-insensitive)
 *   2. Find the source and target version records
 *   3. Find the matching endpoint in each version by HTTP method
 *      (Phase 10 scope: one endpoint per method per version is assumed)
 *
 * @param {string} apiName       - e.g. "customer"
 * @param {string} sourceVersion - e.g. "v1"
 * @param {string} targetVersion - e.g. "v2"
 * @param {string} httpMethod    - e.g. "POST"
 * @returns {Promise<Object|null>} Resolved pair, or null if not found
 */
async function resolveEndpointPair(apiName, sourceVersion, targetVersion, httpMethod) {
  // Step 1: find API by name
  const apis = await getApis();
  const api = apis.find(a =>
    a.name && a.name.toLowerCase() === apiName.toLowerCase()
  );
  if (!api) {
    console.warn(`[BackendClient] No API found with name '${apiName}'`);
    return null;
  }

  // Step 2: find source and target version records
  const versions = await getVersionsForApi(api.id);
  const srcVersion = versions.find(v =>
    v.version && v.version.toLowerCase() === sourceVersion.toLowerCase()
  );
  const tgtVersion = versions.find(v =>
    v.version && v.version.toLowerCase() === targetVersion.toLowerCase()
  );

  if (!srcVersion) {
    console.warn(`[BackendClient] Source version '${sourceVersion}' not found in API '${apiName}'`);
    return null;
  }
  if (!tgtVersion) {
    console.warn(`[BackendClient] Target version '${targetVersion}' not found in API '${apiName}'`);
    return null;
  }

  // Step 3: find matching endpoint by HTTP method
  const methodUpper    = (httpMethod || 'POST').toUpperCase();
  const srcEndpoints   = await getEndpointsForVersion(srcVersion.id);
  const tgtEndpoints   = await getEndpointsForVersion(tgtVersion.id);

  const srcEndpoint = srcEndpoints.find(e =>
    e.httpMethod && e.httpMethod.toUpperCase() === methodUpper
  );
  const tgtEndpoint = tgtEndpoints.find(e =>
    e.httpMethod && e.httpMethod.toUpperCase() === methodUpper
  );

  if (!srcEndpoint) {
    console.warn(`[BackendClient] No ${methodUpper} endpoint found for '${apiName}' ${sourceVersion}`);
    return null;
  }
  if (!tgtEndpoint) {
    console.warn(`[BackendClient] No ${methodUpper} endpoint found for '${apiName}' ${targetVersion}`);
    return null;
  }

  return {
    srcEndpointId:   srcEndpoint.id,
    tgtEndpointId:   tgtEndpoint.id,
    targetBaseUrl:   tgtVersion.baseUrl || null,  // null → proxy.js falls back to env var
    srcVersionLabel: srcVersion.version,
    tgtVersionLabel: tgtVersion.version,
    srcEndpointPath: srcEndpoint.path,
    tgtEndpointPath: tgtEndpoint.path,
  };
}

module.exports = {
  getApis,
  getVersionsForApi,
  getEndpointsForVersion,
  getApprovedRules,
  resolveEndpointPair,
};
