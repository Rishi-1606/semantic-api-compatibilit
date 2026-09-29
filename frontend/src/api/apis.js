/**
 * apis.js
 * API client functions for the API Registry (Phases 3 + 4 + 5).
 *
 * Shapes (from real Spring Boot DTOs):
 *   ApiResponse:         { id, name, description, versionCount, createdAt, updatedAt }
 *   ApiVersionResponse:  { id, apiId, apiName, version, baseUrl, description, isActive, endpointCount, createdAt, updatedAt }
 *   EndpointResponse:    { id, apiVersionId, apiVersionLabel, apiName, httpMethod, path, description, requestSchema, responseSchema, createdAt, updatedAt }
 *   SchemaFieldResponse: { id, endpointId, schemaType, fieldName, fieldPath, fieldType, required, parentPath, description, createdAt }
 */

import { apiFetch } from './client.js';

// -- API Registry -------------------------------------------------------------

export function listApis() {
  return apiFetch('/api/apis');
}

export function getApi(apiId) {
  return apiFetch(`/api/apis/${apiId}`);
}

export function createApi(data) {
  // data: { name, description }
  return apiFetch('/api/apis', { method: 'POST', body: JSON.stringify(data) });
}

// -- Versions -----------------------------------------------------------------

export function getVersionsForApi(apiId) {
  return apiFetch(`/api/apis/${apiId}/versions`);
}

export function getVersion(versionId) {
  return apiFetch(`/api/versions/${versionId}`);
}

export function createVersion(data) {
  // data: { apiId, version, baseUrl, description }
  return apiFetch('/api/versions', { method: 'POST', body: JSON.stringify(data) });
}

// -- Endpoints ----------------------------------------------------------------

export function getEndpointsForVersion(versionId) {
  return apiFetch(`/api/versions/${versionId}/endpoints`);
}

export function getEndpoint(endpointId) {
  return apiFetch(`/api/endpoints/${endpointId}`);
}

export function createEndpoint(data) {
  // data: { apiVersionId, httpMethod, path, description, requestSchema, responseSchema }
  return apiFetch('/api/endpoints', { method: 'POST', body: JSON.stringify(data) });
}

// -- Schema Analysis (Phase 4) ------------------------------------------------

export function analyzeEndpointSchema(endpointId) {
  // Returns: { endpointId, totalFields, requestFields, responseFields }
  return apiFetch(`/api/endpoints/${endpointId}/analyze`, { method: 'POST' });
}

export function getEndpointSchema(endpointId) {
  // Returns: SchemaFieldResponse[]
  return apiFetch(`/api/endpoints/${endpointId}/schema`);
}

// -- NLP Analysis (Phase 5) ---------------------------------------------------

export function runNlpOnEndpoint(endpointId) {
  // Returns: { endpointId, processedFields, results }
  return apiFetch(`/api/endpoints/${endpointId}/nlp`, { method: 'POST' });
}

export function getNlpResults(endpointId) {
  // Returns: NlpFieldResultResponse[]
  return apiFetch(`/api/endpoints/${endpointId}/nlp`);
}
