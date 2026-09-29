/**
 * tester.js
 * API client function for the Transformation Tester (Phase 12 backend endpoint).
 *
 * POST /api/transform/test
 * Applies a single transformation rule to sample JSON in-process,
 * without forwarding the request to any external API.
 *
 * Request body:  { ruleId: number, inputJson: string }
 * Response body: { ruleId, operation, sourcePath, targetPath, inputJson, outputJson, success, errorMessage }
 */

import { apiFetch } from './client.js';

/**
 * Test a transformation rule against sample JSON.
 * @param {number} ruleId   The ID of an existing (any status) transformation rule
 * @param {string} inputJson Raw JSON string to transform
 * @returns {Promise<{ ruleId, operation, sourcePath, targetPath, inputJson, outputJson, success, errorMessage }>}
 */
export function testTransformation(ruleId, inputJson) {
  return apiFetch('/api/transform/test', {
    method: 'POST',
    body: JSON.stringify({ ruleId, inputJson }),
  });
}
