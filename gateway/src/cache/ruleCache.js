'use strict';

/**
 * Phase 10 — In-process TTL cache for approved transformation rules.
 *
 * Cache key:   string (e.g. "customer:v1:v2:POST")
 * Cache value: { endpointPair, approvedRules }
 * TTL:         Configurable via RULE_CACHE_TTL_MS env var (default 60 seconds)
 *
 * Invalidation strategy: TTL-based only. When rules are approved or rejected,
 * the cache expires within one TTL window. No real-time invalidation hook is
 * needed for the current scope.
 *
 * Implementation: in-process Map with per-entry expiry timestamps.
 * No external infrastructure (Redis, Memcached) required.
 */

class RuleCache {

  /**
   * @param {number} [ttlMs] - TTL in milliseconds. Defaults to RULE_CACHE_TTL_MS env var
   *                            or 60 000 ms (60 seconds).
   */
  constructor(ttlMs) {
    this._ttlMs = ttlMs != null
      ? ttlMs
      : parseInt(process.env.RULE_CACHE_TTL_MS || '60000', 10);
    this._store = new Map(); // key → { value, expiresAt }
  }

  /**
   * Retrieve a cached entry.
   *
   * @param {string} key
   * @returns {*} The cached value, or null if not found / expired
   */
  get(key) {
    const entry = this._store.get(key);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this._store.delete(key);
      return null;
    }
    return entry.value;
  }

  /**
   * Store an entry in the cache.
   *
   * @param {string} key
   * @param {*}      value
   */
  set(key, value) {
    this._store.set(key, {
      value,
      expiresAt: Date.now() + this._ttlMs,
    });
  }

  /**
   * Remove a specific entry from the cache.
   *
   * @param {string} key
   */
  delete(key) {
    this._store.delete(key);
  }

  /**
   * Remove all entries.
   */
  clear() {
    this._store.clear();
  }

  /**
   * Current number of cached entries (including possibly-expired ones).
   * For testing / diagnostics only.
   *
   * @returns {number}
   */
  size() {
    return this._store.size;
  }
}

// Export a singleton so all callers share the same cache
module.exports = new RuleCache();
