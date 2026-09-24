'use strict';

/**
 * ENUM_MAPPING operation — Phase 10 executor.
 *
 * Maps a source enum value to its corresponding target enum value
 * using an explicitly stored mapping table from Phase 8.
 *
 * Parameters: { "mappings": { "male": "M", "female": "F" } }
 *
 * A source value that is NOT in the mappings table causes a fatal error
 * (fail-closed — silently passing through an unmapped value would corrupt data).
 *
 * If the source field is missing from the body, skips with a warning (non-fatal).
 *
 * Reverse: Build the inverse mapping table { "M": "male", "F": "female" }.
 * This is always safe when the original mapping is 1-to-1 (which Phase 8 guarantees).
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

/**
 * Execute an ENUM_MAPPING transformation.
 *
 * @param {Object} body - Request/response body (mutated in place)
 * @param {Object} rule - { sourcePath, targetPath, parameters: '{"mappings":{...}}' }
 * @returns {Object} The mutated body
 * @throws {Error} On malformed parameters or unmapped enum value
 */
function executeEnumMapping(body, rule) {
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    throw new Error(`ENUM_MAPPING: parameters JSON is malformed: ${e.message}`);
  }

  const mappings = params.mappings;
  if (!mappings || typeof mappings !== 'object' || Array.isArray(mappings) ||
      Object.keys(mappings).length === 0) {
    throw new Error("ENUM_MAPPING: rule has no valid 'mappings' in parameters");
  }

  const value = getPath(body, rule.sourcePath);
  if (value === undefined) {
    console.warn(`[ENUM_MAPPING] Source field '${rule.sourcePath}' not found — skipping`);
    return body;
  }

  const strValue = String(value);
  if (!(strValue in mappings)) {
    throw new Error(
      `ENUM_MAPPING: no mapping for value '${strValue}' at '${rule.sourcePath}'. ` +
      `Known values: ${Object.keys(mappings).join(', ')}`
    );
  }

  const mapped = mappings[strValue];

  if (rule.sourcePath !== rule.targetPath) {
    deletePath(body, rule.sourcePath);
  }
  setPath(body, rule.targetPath, mapped);
  return body;
}

/**
 * Reverse ENUM_MAPPING — invert the mappings object.
 *
 * Phase 8 guarantees the mapping is 1-to-1, so inversion is always possible.
 * Returns the mutated body, or null if the mappings cannot be parsed.
 *
 * @param {Object} body - Response body (mutated in place)
 * @param {Object} rule - Original rule
 * @returns {Object|null} Mutated body, or null if not safely reversible
 */
function reverseEnumMapping(body, rule) {
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    return null;
  }

  const mappings = params.mappings;
  if (!mappings || typeof mappings !== 'object' || Array.isArray(mappings) ||
      Object.keys(mappings).length === 0) {
    return null;
  }

  // Build inverse mapping
  const reverseMappings = {};
  for (const [k, v] of Object.entries(mappings)) {
    reverseMappings[String(v)] = k;
  }

  return executeEnumMapping(body, {
    sourcePath: rule.targetPath,
    targetPath: rule.sourcePath,
    parameters: JSON.stringify({ mappings: reverseMappings }),
  });
}

module.exports = { executeEnumMapping, reverseEnumMapping };
