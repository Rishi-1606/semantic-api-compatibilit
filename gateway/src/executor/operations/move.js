'use strict';

/**
 * MOVE operation — Phase 10 executor.
 *
 * Flat source → nested target (e.g. mobile → contact.phone).
 * If sourcePath is missing, skips with a warning.
 *
 * Reverse: nested source → flat target (= FLATTEN direction).
 *
 * Implementation note: MOVE and FLATTEN are structurally identical operations —
 * both read from one path and write to another. The distinction (flat→nested vs
 * nested→flat) is conceptual and captured in the Phase 8 rule metadata, not in
 * the execution algorithm. Therefore MOVE reverse and FLATTEN forward use the
 * same path-swap algorithm.
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

/**
 * Execute a MOVE transformation.
 *
 * @param {Object} body - Request/response body (mutated in place)
 * @param {Object} rule - { sourcePath, targetPath }
 * @returns {Object} The mutated body
 */
function executeMove(body, rule) {
  const value = getPath(body, rule.sourcePath);
  if (value === undefined) {
    console.warn(`[MOVE] Source field '${rule.sourcePath}' not found — skipping`);
    return body;
  }
  deletePath(body, rule.sourcePath);
  setPath(body, rule.targetPath, value);
  return body;
}

/**
 * Reverse MOVE = FLATTEN: read from nested targetPath, write to flat sourcePath.
 *
 * @param {Object} body - Body to reverse-transform (mutated in place)
 * @param {Object} rule - Original rule (paths are swapped for the inverse)
 * @returns {Object} The mutated body
 */
function reverseMove(body, rule) {
  return executeMove(body, { sourcePath: rule.targetPath, targetPath: rule.sourcePath });
}

module.exports = { executeMove, reverseMove };
