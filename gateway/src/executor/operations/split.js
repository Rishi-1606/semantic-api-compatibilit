'use strict';

/**
 * SPLIT operation — Phase 10 executor.
 *
 * One source field → multiple target fields (split by separator).
 * Parameters (from rule.parameters JSON):
 *   { "separator": " ", "targets": ["firstName", "lastName"] }
 *
 * The source field MUST be present — its absence is a fatal error.
 *
 * Reverse: SPLIT is reversible when the stored parameters provide enough
 * information (separator and targets are present). The reverse re-joins
 * the target fields back into the source field using the same separator.
 * Returns null if reversal cannot be safely performed.
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

/**
 * Execute a SPLIT transformation.
 *
 * @param {Object} body - Request body (mutated in place)
 * @param {Object} rule - {
 *   sourcePath:  'fullName',
 *   targetPaths: '["firstName","lastName"]',  // JSON array string
 *   parameters:  '{"separator":" ","targets":["firstName","lastName"]}'
 * }
 * @returns {Object} The mutated body
 * @throws {Error} If targetPaths is missing/malformed or the source field is absent
 */
function executeSplit(body, rule) {
  // Parse targetPaths JSON array
  let targetPaths;
  try {
    targetPaths = JSON.parse(rule.targetPaths);
  } catch (e) {
    throw new Error(`SPLIT: targetPaths is not valid JSON: ${rule.targetPaths}`);
  }
  if (!Array.isArray(targetPaths) || targetPaths.length === 0) {
    throw new Error('SPLIT: targetPaths must be a non-empty JSON array');
  }

  // Parse parameters
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    throw new Error(`SPLIT: parameters JSON is malformed: ${e.message}`);
  }

  const separator = params.separator !== undefined ? params.separator : ' ';

  // Source value must exist
  const value = getPath(body, rule.sourcePath);
  if (value === undefined) {
    throw new Error(`SPLIT: required source field '${rule.sourcePath}' is missing from body`);
  }

  const strValue = String(value);
  const parts    = separator ? strValue.split(separator) : [strValue];

  // Assign parts to target paths.
  // Strategy:
  //   - Targets 0 .. n-2 receive parts[i] if it exists, else empty string
  //   - Target n-1 (last) receives all remaining parts joined back together,
  //     or empty string if no parts remain
  for (let i = 0; i < targetPaths.length; i++) {
    if (i < targetPaths.length - 1) {
      // Not the last target: take the i-th part (or empty if parts exhausted)
      setPath(body, targetPaths[i], i < parts.length ? parts[i] : '');
    } else {
      // Last target: join all remaining parts (handles 3-word → 2-field case)
      setPath(body, targetPaths[i], i < parts.length ? parts.slice(i).join(separator) : '');
    }
  }

  deletePath(body, rule.sourcePath);
  return body;
}

/**
 * Reverse SPLIT — re-joins target fields back into the source field.
 *
 * Returns the mutated body if safe to reverse, or null if the required
 * parameters are unavailable (in which case the caller preserves V2 values).
 *
 * @param {Object} body - Response body (mutated in place on success)
 * @param {Object} rule - Original rule
 * @returns {Object|null} Mutated body, or null if not safely reversible
 */
function reverseSplit(body, rule) {
  // Parse targetPaths
  let targetPaths;
  try {
    targetPaths = JSON.parse(rule.targetPaths);
  } catch (e) {
    return null;
  }
  if (!Array.isArray(targetPaths) || targetPaths.length === 0) return null;

  // Parse parameters
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    return null;
  }

  const separator = params.separator !== undefined ? params.separator : ' ';

  // Collect all target values (all must exist)
  const values = [];
  for (const tp of targetPaths) {
    const val = getPath(body, tp);
    if (val === undefined) {
      console.warn(`[SPLIT reverse] Target field '${tp}' not found — cannot reverse`);
      return null;
    }
    values.push(String(val));
  }

  const combined = values.join(separator);

  // Delete all target paths
  for (const tp of targetPaths) {
    deletePath(body, tp);
  }

  setPath(body, rule.sourcePath, combined);
  return body;
}

module.exports = { executeSplit, reverseSplit };
