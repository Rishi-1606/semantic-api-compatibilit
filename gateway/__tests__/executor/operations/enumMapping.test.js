'use strict';

const { executeEnumMapping, reverseEnumMapping } = require('../../../src/executor/operations/enumMapping');

const rule = (overrides = {}) => ({
  id: 7, ruleStatus: 'APPROVED',
  sourcePath: 'gender', targetPath: 'gender',
  parameters: '{"mappings":{"male":"M","female":"F"}}',
  ...overrides,
});

describe('ENUM_MAPPING operation', () => {

  describe('executeEnumMapping', () => {
    test('maps "male" → "M"', () => {
      const body = { gender: 'male' };
      const result = executeEnumMapping(body, rule());
      expect(result.gender).toBe('M');
    });

    test('maps "female" → "F"', () => {
      const body = { gender: 'female' };
      const result = executeEnumMapping(body, rule());
      expect(result.gender).toBe('F');
    });

    test('maps to different target path', () => {
      const body = { status: 'active' };
      const r = rule({
        sourcePath: 'status', targetPath: 'statusCode',
        parameters: '{"mappings":{"active":"A","inactive":"I"}}',
      });
      const result = executeEnumMapping(body, r);
      expect(result.statusCode).toBe('A');
      expect(result.status).toBeUndefined();
    });

    test('skips with warning when source field is missing', () => {
      const body = { name: 'Rishi' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      const result = executeEnumMapping(body, rule());
      expect(result.gender).toBeUndefined();
      warnSpy.mockRestore();
    });

    test('throws on unmapped value', () => {
      const body = { gender: 'unknown' };
      expect(() => executeEnumMapping(body, rule())).toThrow(
        /no mapping for value 'unknown'/i
      );
    });

    test('throws on empty mappings object', () => {
      const body = { gender: 'male' };
      const r = rule({ parameters: '{"mappings":{}}' });
      expect(() => executeEnumMapping(body, r)).toThrow(/no valid 'mappings'/i);
    });

    test('throws on malformed parameters JSON', () => {
      const body = { gender: 'male' };
      const r = rule({ parameters: '{bad}' });
      expect(() => executeEnumMapping(body, r)).toThrow(/malformed/i);
    });

    test('throws when mappings key is missing', () => {
      const body = { gender: 'male' };
      const r = rule({ parameters: '{}' });
      expect(() => executeEnumMapping(body, r)).toThrow();
    });

    test('converts source value to string for lookup', () => {
      // numeric 1 should be stringified to "1" for lookup
      const body = { code: 1 };
      const r = rule({
        sourcePath: 'code', targetPath: 'code',
        parameters: '{"mappings":{"1":"one","2":"two"}}',
      });
      const result = executeEnumMapping(body, r);
      expect(result.code).toBe('one');
    });
  });

  describe('reverseEnumMapping', () => {
    test('inverts the mapping: "M" → "male"', () => {
      const body = { gender: 'M' };
      const result = reverseEnumMapping(body, rule());
      expect(result.gender).toBe('male');
    });

    test('inverts the mapping: "F" → "female"', () => {
      const body = { gender: 'F' };
      const result = reverseEnumMapping(body, rule());
      expect(result.gender).toBe('female');
    });

    test('swaps source and target paths for reverse', () => {
      const body = { statusCode: 'A' };
      const r = rule({
        sourcePath: 'status', targetPath: 'statusCode',
        parameters: '{"mappings":{"active":"A","inactive":"I"}}',
      });
      const result = reverseEnumMapping(body, r);
      expect(result.status).toBe('active');
      expect(result.statusCode).toBeUndefined();
    });

    test('returns null when parameters is malformed', () => {
      const result = reverseEnumMapping({ gender: 'M' }, rule({ parameters: 'bad' }));
      expect(result).toBeNull();
    });

    test('returns null when mappings is empty', () => {
      const result = reverseEnumMapping({ gender: 'M' }, rule({ parameters: '{"mappings":{}}' }));
      expect(result).toBeNull();
    });

    test('throws on unmapped reverse value', () => {
      const body = { gender: 'X' }; // X not in reverse map {M→male, F→female}
      expect(() => reverseEnumMapping(body, rule())).toThrow(/no mapping for value 'X'/i);
    });
  });
});
