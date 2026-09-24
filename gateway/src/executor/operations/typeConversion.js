'use strict';

/**
 * TYPE_CONVERSION operation — Phase 10 executor.
 *
 * Converts a field value from one JSON type to another.
 * Only conversions in the SAFE_CONVERSIONS matrix (identical to Phase 8) are allowed.
 * Object and array types are never converted (unsafe by design).
 *
 * Supported pairs:
 *   string  → integer, number, boolean
 *   integer → string, number
 *   number  → string, integer
 *   boolean → string
 *
 * Reverse: safe if the inverse pair is also in SAFE_CONVERSIONS.
 * Returns null if the reverse conversion is not safe.
 *
 * Parameters: { "from": "string", "to": "integer" }
 */

const { getPath, setPath, deletePath } = require('../pathUtils');

// Matches Phase 8's SAFE_TYPE_CONVERSIONS map exactly
const SAFE_CONVERSIONS = {
  string:  ['integer', 'number', 'boolean'],
  integer: ['string',  'number'],
  number:  ['string',  'integer'],
  boolean: ['string'],
};

/**
 * Check if a conversion pair is in the safe matrix.
 *
 * @param {string} from - Source type (case-insensitive)
 * @param {string} to   - Target type (case-insensitive)
 * @returns {boolean}
 */
function isSafeConversion(from, to) {
  if (!from || !to) return false;
  const safeTargets = SAFE_CONVERSIONS[from.toLowerCase()];
  return Array.isArray(safeTargets) && safeTargets.includes(to.toLowerCase());
}

/**
 * Convert a value from one type to another.
 *
 * @param {*}      value - Input value
 * @param {string} from  - Source type label
 * @param {string} to    - Target type label
 * @returns {*} Converted value
 * @throws {Error} If conversion is unsafe or the value cannot be converted
 */
function convertValue(value, from, to) {
  const fromL = (from || '').toLowerCase();
  const toL   = (to   || '').toLowerCase();

  if (!isSafeConversion(fromL, toL)) {
    throw new Error(`TYPE_CONVERSION: unsupported conversion '${fromL}' → '${toL}'`);
  }

  if (fromL === 'string') {
    const s = String(value);
    if (toL === 'integer') {
      const n = parseInt(s, 10);
      if (isNaN(n)) throw new Error(`TYPE_CONVERSION: cannot convert '${value}' from string to integer (NaN)`);
      return n;
    }
    if (toL === 'number') {
      const n = parseFloat(s);
      if (isNaN(n)) throw new Error(`TYPE_CONVERSION: cannot convert '${value}' from string to number (NaN)`);
      return n;
    }
    if (toL === 'boolean') {
      const low = s.toLowerCase().trim();
      if (low === 'true'  || low === '1') return true;
      if (low === 'false' || low === '0') return false;
      throw new Error(`TYPE_CONVERSION: cannot convert '${value}' from string to boolean (expected true/false/1/0)`);
    }
  }

  if (fromL === 'integer') {
    if (toL === 'string') return String(value);
    if (toL === 'number') return Number(value);
  }

  if (fromL === 'number') {
    if (toL === 'string')  return String(value);
    if (toL === 'integer') return Math.trunc(value);
  }

  if (fromL === 'boolean') {
    if (toL === 'string') return String(value);
  }

  throw new Error(`TYPE_CONVERSION: unhandled conversion '${fromL}' → '${toL}'`);
}

/**
 * Execute a TYPE_CONVERSION transformation.
 *
 * @param {Object} body - Request/response body (mutated in place)
 * @param {Object} rule - { sourcePath, targetPath, parameters: '{"from":"string","to":"integer"}' }
 * @returns {Object} The mutated body
 * @throws {Error} On malformed parameters or unsafe/failed conversion
 */
function executeTypeConversion(body, rule) {
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    throw new Error(`TYPE_CONVERSION: parameters JSON is malformed: ${e.message}`);
  }

  const { from, to } = params;
  if (!from || !to) {
    throw new Error("TYPE_CONVERSION: parameters must include 'from' and 'to' type strings");
  }

  const value = getPath(body, rule.sourcePath);
  if (value === undefined) {
    console.warn(`[TYPE_CONVERSION] Source field '${rule.sourcePath}' not found — skipping`);
    return body;
  }

  const converted = convertValue(value, from, to);

  // If sourcePath ≠ targetPath: delete source first, then write target
  if (rule.sourcePath !== rule.targetPath) {
    deletePath(body, rule.sourcePath);
  }
  setPath(body, rule.targetPath, converted);
  return body;
}

/**
 * Reverse TYPE_CONVERSION — swaps from/to if the inverse is also safe.
 *
 * @param {Object} body - Response body
 * @param {Object} rule - Original rule
 * @returns {Object|null} Mutated body, or null if reverse is not safe
 */
function reverseTypeConversion(body, rule) {
  let params = {};
  try {
    if (rule.parameters && rule.parameters.trim() !== '{}') {
      params = JSON.parse(rule.parameters);
    }
  } catch (e) {
    return null;
  }

  const { from, to } = params;
  if (!from || !to) return null;

  if (!isSafeConversion(to, from)) {
    console.warn(
      `[TYPE_CONVERSION reverse] Inverse '${to}' → '${from}' is not in safe matrix — preserving V2 value`
    );
    return null;
  }

  return executeTypeConversion(body, {
    sourcePath: rule.targetPath,
    targetPath: rule.sourcePath,
    parameters: JSON.stringify({ from: to, to: from }),
  });
}

module.exports = { executeTypeConversion, reverseTypeConversion, convertValue, isSafeConversion };
