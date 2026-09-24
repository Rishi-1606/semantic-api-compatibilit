'use strict';

/**
 * Phase 10 — Safe dot-notation JSON path utilities.

/**
 * Read a value at a dot-notation path from an object.
 * Returns undefined if any segment is missing or the intermediate
 * value is not a plain object.
 *
 * @param {Object} obj  - Root object to read from
 * @param {string} path - Dot-notation path, e.g. "contact.phone"
 * @returns {*}
 */
function getPath(obj, path) {
  if (obj === null || obj === undefined || !path) return undefined;
  const segments = path.split('.');
  let current = obj;
  for (const seg of segments) {
    if (current === null || current === undefined ||
        typeof current !== 'object' || Array.isArray(current)) {
      return undefined;
    }
    current = current[seg];
  }
  return current;
}

/**
 * Write a value at a dot-notation path in an object.
 * Creates intermediate plain objects as needed.
 *
 * @param {Object} obj   - Root object to write into (mutated in place)
 * @param {string} path  - Dot-notation path
 * @param {*}      value - Value to set
 * @throws {Error} If an intermediate segment exists but is not a plain object
 */
function setPath(obj, path, value) {
  if (!path) throw new Error('setPath: path must not be empty');
  const segments = path.split('.');
  let current = obj;
  for (let i = 0; i < segments.length - 1; i++) {
    const seg = segments[i];
    if (current[seg] === undefined || current[seg] === null) {
      current[seg] = {};
    } else if (typeof current[seg] !== 'object' || Array.isArray(current[seg])) {
      throw new Error(
        `setPath: intermediate segment '${seg}' at path '${path}' ` +
        `is not a plain object (got ${Array.isArray(current[seg]) ? 'array' : typeof current[seg]})`
      );
    }
    current = current[seg];
  }
  current[segments[segments.length - 1]] = value;
}

/**
 * Delete a value at a dot-notation path from an object.
 * Recursively removes empty parent objects after deletion.
 *
 * @param {Object} obj  - Root object (mutated in place)
 * @param {string} path - Dot-notation path
 * @returns {boolean} true if the path existed and was deleted, false otherwise
 */
function deletePath(obj, path) {
  if (!path) return false;
  const segments = path.split('.');

  // Walk and collect the traversal chain so we can clean up parents
  const chain = [obj];
  let current = obj;
  for (let i = 0; i < segments.length - 1; i++) {
    const seg = segments[i];
    if (current === null || current === undefined || typeof current !== 'object') {
      return false;
    }
    current = current[seg];
    chain.push(current);
  }

  const lastSeg = segments[segments.length - 1];
  if (current === null || current === undefined || typeof current !== 'object') {
    return false;
  }
  if (!(lastSeg in current)) return false;

  delete current[lastSeg];

  // Walk back up the chain and remove empty parent plain objects
  for (let i = segments.length - 2; i >= 0; i--) {
    const parent = chain[i];
    const seg    = segments[i];
    if (
      parent != null &&
      typeof parent === 'object' &&
      parent[seg] != null &&
      typeof parent[seg] === 'object' &&
      !Array.isArray(parent[seg]) &&
      Object.keys(parent[seg]).length === 0
    ) {
      delete parent[seg];
    } else {
      break; // Parent is not empty — stop climbing
    }
  }

  return true;
}

/**
 * Deep-clone a JSON-serializable object.
 * Used by the executor to avoid mutating the original request/response body.
 *
 * @param {Object} obj - Object to clone
 * @returns {Object} Deep clone
 */
function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

/**
 * Returns true if a dot-notation path contains at least one '.' (i.e. is nested).
 *
 * @param {string|null} path
 * @returns {boolean}
 */
function isNested(path) {
  return path != null && path.includes('.');
}

module.exports = { getPath, setPath, deletePath, deepClone, isNested };
