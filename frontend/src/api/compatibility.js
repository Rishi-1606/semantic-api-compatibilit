/**
 * compatibility.js
 * API client functions for Phase 6 (Semantic Matching) and Phase 7 (Compatibility Engine).
 *
 * Shapes (from real Spring Boot DTOs):
 *   MatchSessionResponse: {
 *     id, sourceVersionId, sourceVersionName, targetVersionId, targetVersionName,
 *     status, compatibilityStatus, overallScore, embeddingProvider, embeddingModel,
 *     fieldPairCount, errorMessage, createdAt, completedAt,
 *     candidates: MatchCandidateResponse[]
 *   }
 *   MatchCandidateResponse: {
 *     id, sessionId, sourceFieldId, sourcePath, sourceOriginalName, sourceNormalized,
 *     sourceDataType, sourceSchemaType, targetFieldId, targetPath, targetOriginalName,
 *     targetNormalized, targetDataType, targetSchemaType, cosineSimilarity, typeFactor,
 *     pathFactor, confidence, approvalStatus, notes, createdAt
 *   }
 *   CompatibilityReportResponse: {
 *     id, matchSessionId, sourceVersionId, sourceVersionName, targetVersionId, targetVersionName,
 *     compatibilityStatus, overallScore, totalSourceFields, matchedFields,
 *     unmatchedRequiredFields, autoApprovedCount, requiresReviewCount, lowConfidenceCount,
 *     explanation, createdAt, endpointResults: EndpointCompatibilityResultResponse[]
 *   }
 *   EndpointCompatibilityResultResponse: {
 *     id, reportId, sourceEndpointId, sourceEndpointPath, targetEndpointId, targetEndpointPath,
 *     compatibilityStatus, compatibilityScore, totalSourceFields, matchedRequiredFields,
 *     unmatchedRequiredFields, matchedOptionalFields, unmatchedOptionalFields,
 *     matchedFieldPaths, unmatchedSourcePaths, unmatchedTargetPaths, explanation, createdAt
 *   }
 */

import { apiFetch } from './client.js';

// -- Phase 6 — Semantic Matching -----------------------------------------------

/**
 * POST /api/compatibility/analyze
 * Starts a semantic matching session.
 * @param {number} sourceVersionId
 * @param {number} targetVersionId
 * @returns {Promise<MatchSessionResponse>}
 */
export function runSemanticAnalysis(sourceVersionId, targetVersionId) {
  return apiFetch('/api/compatibility/analyze', {
    method: 'POST',
    body: JSON.stringify({ sourceVersionId, targetVersionId }),
  });
}

/**
 * GET /api/compatibility/{sessionId}
 * @returns {Promise<MatchSessionResponse>}
 */
export function getMatchSession(sessionId) {
  return apiFetch(`/api/compatibility/${sessionId}`);
}

/**
 * GET /api/compatibility/version/{versionId}
 * @returns {Promise<MatchSessionResponse[]>}
 */
export function getSessionsForVersion(versionId) {
  return apiFetch(`/api/compatibility/version/${versionId}`);
}

// -- Phase 7 — Compatibility Engine -------------------------------------------

/**
 * POST /api/reports/analyze
 * Runs Phase 7 compatibility analysis on a completed Phase 6 match session.
 * @param {number} matchSessionId
 * @returns {Promise<CompatibilityReportResponse>}
 */
export function runCompatibilityReport(matchSessionId) {
  return apiFetch('/api/reports/analyze', {
    method: 'POST',
    body: JSON.stringify({ matchSessionId }),
  });
}

/**
 * GET /api/reports/{reportId}
 * Returns full report including per-endpoint breakdown.
 * @returns {Promise<CompatibilityReportResponse>}
 */
export function getReport(reportId) {
  return apiFetch(`/api/reports/${reportId}`);
}

/**
 * GET /api/reports/{reportId}/endpoints
 * Returns only the endpoint-level results.
 * @returns {Promise<EndpointCompatibilityResultResponse[]>}
 */
export function getReportEndpoints(reportId) {
  return apiFetch(`/api/reports/${reportId}/endpoints`);
}

/**
 * GET /api/reports/version/{versionId}
 * List all reports involving a specific API version (summary, no endpoint breakdown).
 * @returns {Promise<CompatibilityReportResponse[]>}
 */
export function getReportsForVersion(versionId) {
  return apiFetch(`/api/reports/version/${versionId}`);
}

/**
 * GET /api/reports/session/{sessionId}
 * @returns {Promise<CompatibilityReportResponse[]>}
 */
export function getReportsForSession(sessionId) {
  return apiFetch(`/api/reports/session/${sessionId}`);
}
