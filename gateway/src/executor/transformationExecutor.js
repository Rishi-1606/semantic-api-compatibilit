'use strict';

/**
 * Phase 10 — Deterministic Transformation Executor.
 *
 * Executes ONLY APPROVED transformation rules produced by Phases 8–9.
 * No AI reasoning, no eval(), no new Function(), no dynamic code generation —
 * only the seven explicitly supported operations.
 *
 * Safety invariants:
 * - Every rule is checked for ruleStatus === 'APPROVED' before execution.
 * - Unknown operations throw immediately (fail-closed, no silent pass-through).
 * - Parameters are read from structured JSON — only named keys are accessed.
 * - The executor never calls the backend or any AI service.
 * - Endpoint-pair isolation is the caller's responsibility (proxy.js ensures it).
 *
 * Response transformation:
 * - Rules are applied in reverse order (last applied, first undone).
 * - Each operation's inverse is applied where explicitly and safely defined.
 * - When an inverse cannot be safely performed, the V2 field/value is preserved
 *   as-is and a warning is logged — no data is invented.
 */

const { deepClone } = require('./pathUtils');
const { executeRename,          reverseRename }          = require('./operations/rename');
const { executeMove,            reverseMove }            = require('./operations/move');
const { executeFlatten,         reverseFlatten }         = require('./operations/flatten');
const { executeCombine,         reverseCombine }         = require('./operations/combine');
const { executeSplit,           reverseSplit }           = require('./operations/split');
const { executeTypeConversion,  reverseTypeConversion }  = require('./operations/typeConversion');
const { executeEnumMapping,     reverseEnumMapping }     = require('./operations/enumMapping');

const APPROVED = 'APPROVED';

class TransformationExecutor {

  /**
   * Transform a request body from source (V1) format to target (V2) format.
   *
   * Rules are applied in the order supplied (backend returns them confidence-descending).
   * Each rule must have ruleStatus === 'APPROVED'; any non-APPROVED rule throws immediately.
   *
   * @param {Object} body  - Parsed request body (a deep clone is made internally)
   * @param {Array}  rules - Array of APPROVED rule objects from the backend API
   * @returns {Object} Transformed body
   * @throws {Error} If any rule is not APPROVED, or if a rule cannot be applied
   */
  transformRequest(body, rules) {
    let result = deepClone(body);
    for (const rule of rules) {
      this._assertApproved(rule);
      result = this._applyForward(result, rule);
    }
    return result;
  }

  /**
   * Transform a response body from target (V2) format back to source (V1) format.
   *
   * Rules are applied in REVERSE order (last forward rule is undone first).
   * When an inverse is not safely defined (COMBINE, unsafe TYPE_CONVERSION, etc.)
   * the V2 value is preserved as-is and a warning is logged.
   *
   * @param {Object} body  - Parsed response body from the target API (deep clone internally)
   * @param {Array}  rules - Same APPROVED rules used for the forward (request) transform
   * @returns {Object} Reverse-transformed body
   * @throws {Error} If any rule is not APPROVED, or an unknown operation is encountered
   */
  transformResponse(body, rules) {
    let result = deepClone(body);
    // Reverse order: undo the last-applied forward rule first
    const reversed = [...rules].reverse();
    for (const rule of reversed) {
      this._assertApproved(rule);
      result = this._applyInverse(result, rule);
    }
    return result;
  }

  // ── Private helpers ───────────────────────────────────────────────

  /**
   * Security guard: throw if the rule status is not APPROVED.
   * This is a defence-in-depth check — the proxy route already filters
   * to APPROVED, but the executor enforces it independently.
   */
  _assertApproved(rule) {
    if (rule.ruleStatus !== APPROVED) {
      throw new Error(
        `[Executor] Security violation: rule ${rule.id} has status '${rule.ruleStatus}'. ` +
        'Only APPROVED rules may be executed by Phase 10.'
      );
    }
  }

  /** Apply the forward (request) direction of a rule. */
  _applyForward(body, rule) {
    switch (rule.operation) {
      case 'RENAME':          return executeRename(body, rule);
      case 'MOVE':            return executeMove(body, rule);
      case 'FLATTEN':         return executeFlatten(body, rule);
      case 'COMBINE':         return executeCombine(body, rule);
      case 'SPLIT':           return executeSplit(body, rule);
      case 'TYPE_CONVERSION': return executeTypeConversion(body, rule);
      case 'ENUM_MAPPING':    return executeEnumMapping(body, rule);
      default:
        throw new Error(`[Executor] Unsupported operation '${rule.operation}' in rule ${rule.id}`);
    }
  }

  /**
   * Apply the inverse (response) direction of a rule.
   *
   * Operations that return null signal "not reversible" — in that case the body
   * is returned unchanged (V2 value preserved) and a warning is already logged
   * by the operation itself.
   */
  _applyInverse(body, rule) {
    switch (rule.operation) {
      case 'RENAME': {
        return reverseRename(body, rule);
      }
      case 'MOVE': {
        // MOVE forward: flat→nested; reverse: nested→flat (= FLATTEN direction)
        return reverseMove(body, rule);
      }
      case 'FLATTEN': {
        // FLATTEN forward: nested→flat; reverse: flat→nested (= MOVE direction)
        return reverseFlatten(body, rule);
      }
      case 'TYPE_CONVERSION': {
        const result = reverseTypeConversion(body, rule);
        if (result === null) {
          // reverseTypeConversion already logged a warning
          return body; // preserve V2 value
        }
        return result;
      }
      case 'ENUM_MAPPING': {
        const result = reverseEnumMapping(body, rule);
        if (result === null) {
          console.warn(`[Executor] ENUM_MAPPING rule ${rule.id}: reverse map unavailable — preserving V2 value`);
          return body;
        }
        return result;
      }
      case 'COMBINE': {
        // COMBINE is NOT automatically reversible — cannot split combined string back
        // reverseCombine always returns null (documented)
        reverseCombine(); // purely to satisfy linter — always null
        console.warn(
          `[Executor] COMBINE rule ${rule.id}: not reversible — ` +
          `preserving V2 value at '${rule.targetPath}'`
        );
        return body;
      }
      case 'SPLIT': {
        const result = reverseSplit(body, rule);
        if (result === null) {
          // reverseSplit already logged a warning
          return body;
        }
        return result;
      }
      default:
        throw new Error(`[Executor] Unsupported operation '${rule.operation}' in rule ${rule.id}`);
    }
  }
}

// Export a singleton instance — callers do not need to instantiate
module.exports = new TransformationExecutor();
