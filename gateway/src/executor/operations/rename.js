'use strict';

/**
 * RENAME operation — Phase 10 executor.
 *
 * Reads value at sourcePath, writes to targetPath, removes sourcePath.
 * If sourcePath is missing from body, skips with a warning (non-fatal for
 * optional fields); the caller may promote this to a fatal error if needed.
 *
 * Reverse: swap source and target paths (symmetric operation).
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

/**
 * Execute a RENAME transformation.
 *
 * @param {Object} body - Request/response body (mutated in place)
 * @param {Object} rule - TransformationRule: { sourcePath, targetPath }
 * @returns {Object} The mutated body
 */
function executeRename(body, rule) {
  const value = getPath(body, rule.sourcePath);
  if (value === undefined) {
    console.warn(`[RENAME] Source field '${rule.sourcePath}' not found — skipping`);
    return body;
  }
  deletePath(body, rule.sourcePath);
  setPath(body, rule.targetPath, value);
  return body;
}

/**
 * Reverse RENAME: targetPath → sourcePath.
 *
 * @param {Object} body - Body to reverse-transform (mutated in place)
 * @param {Object} rule - Original rule (sourcePath/targetPath are swapped)
 * @returns {Object} The mutated body
 */
function reverseRename(body, rule) {
  return executeRename(body, { sourcePath: rule.targetPath, targetPath: rule.sourcePath });
}

module.exports = { executeRename, reverseRename };
