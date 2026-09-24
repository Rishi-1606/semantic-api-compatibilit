'use strict';

/**
 * COMBINE operation — Phase 10 executor.
 *
 * Multiple source fields → one target field (concatenated with separator).
 * Parameters (from rule.parameters JSON):
 *   { "separator": " ", "order": ["firstName", "lastName"] }
 *
 * All source fields MUST be present — any missing field causes a fatal error
 * (fail-closed: partial data would produce a semantically incorrect target).
 *
 * Reverse: COMBINE is NOT automatically reversible.
 * A combined string cannot be deterministically split back into its original
 * source fields (the original individual values are lost). The response
 * transformer preserves the combined V2 value as-is and logs a warning.
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

/**
 * Execute a COMBINE transformation.
 *
 * @param {Object} body - Request body (mutated in place)
 * @param {Object} rule - {
 *   sourcePaths: '["firstName","lastName"]',  // JSON array string
 *   targetPath:  'fullName',
 *   parameters:  '{"separator":" ","order":["firstName","lastName"]}'
 * }
 * @returns {Object} The mutated body
 * @throws {Error} If sourcePaths is missing/malformed or a required source field is absent
 */
function executeCombine(body, rule) {
  // Parse sourcePaths JSON array
  let sourcePaths;
  try {
    sourcePaths = JSON.parse(rule.sourcePaths);
  } catch (e) {
    throw new Error(`COMBINE: sourcePaths is not valid JSON: ${rule.sourcePaths}`);
  }
  if (!Array.isArray(sourcePaths) || sourcePaths.length === 0) {
    throw new Error('COMBINE: sourcePaths must be a non-empty JSON array');
  }

  // Parse parameters
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    throw new Error(`COMBINE: parameters JSON is malformed: ${e.message}`);
  }

  const separator = params.separator !== undefined ? params.separator : ' ';
  const order     = Array.isArray(params.order) ? params.order : sourcePaths;

  // Collect values in the specified order — ALL must be present
  const values = [];
  for (const sp of order) {
    const val = getPath(body, sp);
    if (val === undefined) {
      throw new Error(`COMBINE: required source field '${sp}' is missing from body`);
    }
    values.push(String(val));
  }

  const combined = values.join(separator);

  // Delete all source paths
  for (const sp of sourcePaths) {
    deletePath(body, sp);
  }

  setPath(body, rule.targetPath, combined);
  return body;
}

/**
 * Reverse COMBINE — not automatically reversible.
 *
 * Returns null to signal to the caller that this rule cannot be safely
 * inverted. The caller must preserve the V2 value as-is.
 *
 * @returns {null}
 */
function reverseCombine() {
  return null;
}

module.exports = { executeCombine, reverseCombine };
