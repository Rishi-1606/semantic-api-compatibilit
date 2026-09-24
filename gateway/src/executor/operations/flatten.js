'use strict';

/**
 * FLATTEN operation — Phase 10 executor.
 *
 * Nested source → flat target (e.g. contact.phone → phoneNumber).
 * Empty parent objects are cleaned up after extraction.
 * If sourcePath is missing, skips with a warning.
 *
 * Reverse: flat source → nested target (= MOVE direction).
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

/**
 * Execute a FLATTEN transformation.
 *
 * @param {Object} body - Request/response body (mutated in place)
 * @param {Object} rule - { sourcePath, targetPath }
 * @returns {Object} The mutated body
 */
function executeFlatten(body, rule) {
  const value = getPath(body, rule.sourcePath);
  if (value === undefined) {
    console.warn(`[FLATTEN] Source field '${rule.sourcePath}' not found — skipping`);
    return body;
  }
  // deletePath cleans up empty parent objects automatically
  deletePath(body, rule.sourcePath);
  setPath(body, rule.targetPath, value);
  return body;
}

/**
 * Reverse FLATTEN = MOVE: read from flat targetPath, write to nested sourcePath.
 *
 * @param {Object} body - Body to reverse-transform (mutated in place)
 * @param {Object} rule - Original rule (paths are swapped for the inverse)
 * @returns {Object} The mutated body
 */
function reverseFlatten(body, rule) {
  return executeFlatten(body, { sourcePath: rule.targetPath, targetPath: rule.sourcePath });
}

module.exports = { executeFlatten, reverseFlatten };
