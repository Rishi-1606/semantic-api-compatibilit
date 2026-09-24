'use strict';

// RuleCache is a singleton — we need to get a fresh instance for each test.
// Import the class (not the singleton) by re-requiring after jest.resetModules().

describe('RuleCache', () => {

  let RuleCache;

  beforeEach(() => {
    jest.resetModules();
    // Load the module fresh — re-evaluate so we get a new singleton per test
    // We test through the singleton exported by the module.
    RuleCache = require('../../src/cache/ruleCache');
    RuleCache.clear();
  });

  test('cache miss returns null for unknown key', () => {
    expect(RuleCache.get('unknown')).toBeNull();
  });

  test('set and get returns the stored value', () => {
    const value = { rules: [{ id: 1 }], endpointPair: { srcEndpointId: 1 } };
    RuleCache.set('key1', value);
    expect(RuleCache.get('key1')).toEqual(value);
  });

  test('different keys are independent', () => {
    RuleCache.set('a', { v: 1 });
    RuleCache.set('b', { v: 2 });
    expect(RuleCache.get('a').v).toBe(1);
    expect(RuleCache.get('b').v).toBe(2);
  });

  test('returns null after TTL has expired', async () => {
    jest.resetModules();
    // Create a cache with a very short TTL via env var
    process.env.RULE_CACHE_TTL_MS = '50'; // 50 ms
    const ShortTTLCache = require('../../src/cache/ruleCache');
    ShortTTLCache.set('expiring', { data: 'val' });
    expect(ShortTTLCache.get('expiring')).not.toBeNull();
    await new Promise(r => setTimeout(r, 80)); // wait 80 ms
    expect(ShortTTLCache.get('expiring')).toBeNull();
    delete process.env.RULE_CACHE_TTL_MS;
  });

  test('delete removes an entry', () => {
    RuleCache.set('key1', { data: 1 });
    RuleCache.delete('key1');
    expect(RuleCache.get('key1')).toBeNull();
  });

  test('clear removes all entries', () => {
    RuleCache.set('a', 1);
    RuleCache.set('b', 2);
    RuleCache.clear();
    expect(RuleCache.get('a')).toBeNull();
    expect(RuleCache.get('b')).toBeNull();
  });

  test('size reflects number of entries (including possibly-expired)', () => {
    RuleCache.clear();
    expect(RuleCache.size()).toBe(0);
    RuleCache.set('x', 1);
    RuleCache.set('y', 2);
    expect(RuleCache.size()).toBe(2);
  });

  test('get on expired entry cleans it from the store', async () => {
    jest.resetModules();
    process.env.RULE_CACHE_TTL_MS = '50';
    const ShortCache = require('../../src/cache/ruleCache');
    ShortCache.clear();
    ShortCache.set('will-expire', 'data');
    expect(ShortCache.size()).toBe(1);
    await new Promise(r => setTimeout(r, 80));
    ShortCache.get('will-expire'); // triggers removal
    expect(ShortCache.size()).toBe(0);
    delete process.env.RULE_CACHE_TTL_MS;
  });

  test('overwriting an existing key resets TTL', async () => {
    jest.resetModules();
    process.env.RULE_CACHE_TTL_MS = '100';
    const cache = require('../../src/cache/ruleCache');
    cache.set('k', 'first');
    await new Promise(r => setTimeout(r, 60));
    cache.set('k', 'second'); // refresh
    await new Promise(r => setTimeout(r, 60));
    // 60 + 60 = 120ms total, but the second set happened at 60ms so TTL resets
    const val = cache.get('k');
    expect(val).toBe('second');
    delete process.env.RULE_CACHE_TTL_MS;
  });
});
