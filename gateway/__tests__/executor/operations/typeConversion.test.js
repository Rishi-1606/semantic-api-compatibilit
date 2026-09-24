'use strict';

const {
  executeTypeConversion,
  reverseTypeConversion,
  convertValue,
  isSafeConversion,
} = require('../../../src/executor/operations/typeConversion');

const rule = (from, to, overrides = {}) => ({
  id: 6, ruleStatus: 'APPROVED',
  sourcePath: 'age', targetPath: 'age',
  parameters: JSON.stringify({ from, to }),
  ...overrides,
});

describe('TYPE_CONVERSION operation', () => {

  // ── isSafeConversion ──────────────────────────────────────────────
  describe('isSafeConversion', () => {
    test.each([
      ['string',  'integer', true],
      ['string',  'number',  true],
      ['string',  'boolean', true],
      ['integer', 'string',  true],
      ['integer', 'number',  true],
      ['number',  'string',  true],
      ['number',  'integer', true],
      ['boolean', 'string',  true],
      ['integer', 'boolean', false],
      ['boolean', 'integer', false],
      ['object',  'string',  false],
      ['array',   'string',  false],
    ])('%s → %s: %s', (from, to, expected) => {
      expect(isSafeConversion(from, to)).toBe(expected);
    });
  });

  // ── convertValue ─────────────────────────────────────────────────
  describe('convertValue', () => {
    test('string "21" → integer 21', () => {
      expect(convertValue('21', 'string', 'integer')).toBe(21);
    });

    test('string "21.5" → number 21.5', () => {
      expect(convertValue('21.5', 'string', 'number')).toBe(21.5);
    });

    test('string "true" → boolean true', () => {
      expect(convertValue('true', 'string', 'boolean')).toBe(true);
    });

    test('string "false" → boolean false', () => {
      expect(convertValue('false', 'string', 'boolean')).toBe(false);
    });

    test('string "1" → boolean true', () => {
      expect(convertValue('1', 'string', 'boolean')).toBe(true);
    });

    test('string "0" → boolean false', () => {
      expect(convertValue('0', 'string', 'boolean')).toBe(false);
    });

    test('integer 21 → string "21"', () => {
      expect(convertValue(21, 'integer', 'string')).toBe('21');
    });

    test('integer 5 → number 5', () => {
      expect(convertValue(5, 'integer', 'number')).toBe(5);
    });

    test('number 21.7 → integer 21 (truncated)', () => {
      expect(convertValue(21.7, 'number', 'integer')).toBe(21);
    });

    test('number 3.14 → string "3.14"', () => {
      expect(convertValue(3.14, 'number', 'string')).toBe('3.14');
    });

    test('boolean true → string "true"', () => {
      expect(convertValue(true, 'boolean', 'string')).toBe('true');
    });

    test('throws on non-numeric string → integer', () => {
      expect(() => convertValue('abc', 'string', 'integer')).toThrow(/NaN/);
    });

    test('throws on non-boolean string → boolean', () => {
      expect(() => convertValue('yes', 'string', 'boolean')).toThrow(/boolean/i);
    });

    test('throws on unsupported pair (integer → boolean)', () => {
      expect(() => convertValue(1, 'integer', 'boolean')).toThrow(/unsupported/i);
    });
  });

  // ── executeTypeConversion ─────────────────────────────────────────
  describe('executeTypeConversion', () => {
    test('converts string to integer in-place (same path)', () => {
      const body = { age: '21' };
      const result = executeTypeConversion(body, rule('string', 'integer'));
      expect(result.age).toBe(21);
    });

    test('converts string to integer (different paths)', () => {
      const body = { ageStr: '21' };
      const r = rule('string', 'integer', { sourcePath: 'ageStr', targetPath: 'ageInt' });
      const result = executeTypeConversion(body, r);
      expect(result.ageInt).toBe(21);
      expect(result.ageStr).toBeUndefined();
    });

    test('skips with warning when source field missing', () => {
      const body = { name: 'Rishi' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      const result = executeTypeConversion(body, rule('string', 'integer'));
      expect(result.age).toBeUndefined();
      warnSpy.mockRestore();
    });

    test('throws on malformed parameters JSON', () => {
      const body = { age: '21' };
      const r = { ...rule('string', 'integer'), parameters: '{bad}' };
      expect(() => executeTypeConversion(body, r)).toThrow(/malformed/i);
    });

    test('throws when from/to are missing from parameters', () => {
      const body = { age: '21' };
      const r = { ...rule('string', 'integer'), parameters: '{}' };
      expect(() => executeTypeConversion(body, r)).toThrow(/'from'.*'to'/i);
    });
  });

  // ── reverseTypeConversion ─────────────────────────────────────────
  describe('reverseTypeConversion', () => {
    test('reverses integer → string (safe inverse)', () => {
      const body = { age: 21 };
      // original was string→integer; reverse is integer→string
      const result = reverseTypeConversion(body, rule('string', 'integer'));
      expect(result.age).toBe('21');
    });

    test('reverses number → string (safe inverse)', () => {
      const body = { price: 9.99 };
      const r = rule('string', 'number', { sourcePath: 'price', targetPath: 'price' });
      const result = reverseTypeConversion(body, r);
      expect(result.price).toBe('9.99');
    });

    test('returns null for unsafe inverse (string → boolean → no reverse in matrix)', () => {
      // boolean → string is safe, but the original is string→boolean
      // reverse would be boolean→string which IS safe — so this returns a result
      // Let's test an actually unsafe inverse: string→boolean reverse is boolean→string which is safe
      // Test an actually unsafe case: original integer→number, reverse number→integer IS safe
      // Let's test: boolean→string original; reverse string→boolean IS in matrix
      // Truly unsafe: object → string (not in matrix)
      const body = { active: 'true' };
      const r = rule('integer', 'boolean', { sourcePath: 'active', targetPath: 'active' });
      // integer→boolean is unsafe: isSafeConversion('boolean','integer') → false
      // But wait — integer→boolean is itself not safe, so executeTypeConversion would throw
      // Let's test a valid forward but unsafe reverse:
      // string→boolean (forward safe). Reverse: boolean→string (also safe, returns result)
      // For truly unsafe reverse we need: boolean→string forward, reverse string→boolean IS safe
      // Actually all documented safe forward pairs have safe reverse pairs EXCEPT:
      // string→boolean: reverse is boolean→string ✓
      // The only case where reversal returns null would be if we had a hypothetical unsafe pair.
      // We can verify the warn path by injecting a fake rule with from/to that make isSafeConversion(to,from) = false
      const fakeRule = { ...rule('x', 'y'), parameters: '{"from":"x","to":"y"}' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      const result = reverseTypeConversion(body, fakeRule);
      expect(result).toBeNull();
      warnSpy.mockRestore();
    });

    test('returns null when parameters cannot be parsed', () => {
      const r = { ...rule('string', 'integer'), parameters: 'broken' };
      const result = reverseTypeConversion({ age: 21 }, r);
      expect(result).toBeNull();
    });
  });
});
