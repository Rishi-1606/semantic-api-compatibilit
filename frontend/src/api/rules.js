/**
 * rules.js
 * API client functions for Transformation Rules (Phase 8 + 9).
 *
 * Shapes (from real Spring Boot DTOs):
 *   TransformationRuleResponse: {
 *     id, reportId, matchCandidateId, sourceEndpointId, targetEndpointId,
 *     sourceEndpointPath, targetEndpointPath, sourcePath, targetPath,
 *     sourcePaths, targetPaths, operation, sourceType, targetType,
 *     confidence, candidateStatus, ruleStatus, parameters,
 *     rejectionReason, validationErrors, createdAt, updatedAt,
 *     approvedAt, rejectedAt, approvalNote
 *   }
 *   TransformationRuleGenerationResult: {
 *     reportId, totalCandidatesProcessed, rulesGenerated, rulesInvalid,
 *     skippedNoOp, skippedLowConfidence, skippedRejected, rules: TransformationRuleResponse[]
 *   }
 *   AutoApprovalResult: {
 *     reportId, totalGenerated, autoApproved, requiresHumanReview,
 *     skippedLowConfidence, skippedRequiresReview, skippedInvalidOrFailed,
 *     approvedRules: TransformationRuleResponse[]
 *   }
 *
 * ruleStatus values: GENERATED | APPROVED | REJECTED | INVALID
 * operation values:  RENAME | MOVE | FLATTEN | TYPE_CONVERSION | ENUM_MAPPING | COMBINE | SPLIT
 */

import { apiFetch } from './client.js';

// -- Phase 8 — Rule Generation -------------------------------------------------

/**
 * POST /api/transformation-rules/generate
 * Generate transformation rules for a compatibility report.
 * @param {number} reportId
 * @returns {Promise<TransformationRuleGenerationResult>}
 */
export function generateRules(reportId) {
  return apiFetch('/api/transformation-rules/generate', {
    method: 'POST',
    body: JSON.stringify({ reportId }),
  });
}

/**
 * GET /api/transformation-rules/report/{reportId}
 * List all rules for a report (all statuses), ordered by confidence desc.
 * @returns {Promise<TransformationRuleResponse[]>}
 */
export function getRulesForReport(reportId) {
  return apiFetch(`/api/transformation-rules/report/${reportId}`);
}

/**
 * GET /api/transformation-rules/{ruleId}
 * @returns {Promise<TransformationRuleResponse>}
 */
export function getRuleById(ruleId) {
  return apiFetch(`/api/transformation-rules/${ruleId}`);
}

// -- Phase 9 — Approval --------------------------------------------------------

/**
 * POST /api/transformation-rules/{ruleId}/approve
 * Approve a GENERATED rule. Enforces confidence policy + safety re-validation.
 * @param {number} ruleId
 * @param {string|null} reason  Optional human note
 * @returns {Promise<TransformationRuleResponse>}
 */
export function approveRule(ruleId, reason) {
  return apiFetch(`/api/transformation-rules/${ruleId}/approve`, {
    method: 'POST',
    body: JSON.stringify(reason ? { reason } : {}),
  });
}

/**
 * POST /api/transformation-rules/{ruleId}/reject
 * Reject a GENERATED or APPROVED rule.
 * @param {number} ruleId
 * @param {string|null} reason  Optional human note
 * @returns {Promise<TransformationRuleResponse>}
 */
export function rejectRule(ruleId, reason) {
  return apiFetch(`/api/transformation-rules/${ruleId}/reject`, {
    method: 'POST',
    body: JSON.stringify(reason ? { reason } : {}),
  });
}

/**
 * POST /api/approval/reports/{reportId}/auto-approve
 * Batch auto-approve all eligible GENERATED rules for a report.
 * Eligible = AUTO_APPROVED candidate + confidence >= 0.90 + safety valid.
 * @returns {Promise<AutoApprovalResult>}
 */
export function autoApproveForReport(reportId) {
  return apiFetch(`/api/approval/reports/${reportId}/auto-approve`, { method: 'POST' });
}
