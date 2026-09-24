'use strict';

const { executeSplit, reverseSplit } = require('../../../src/executor/operations/split');

const rule = (overrides = {}) => ({
  id: 5, ruleStatus: 'APPROVED',
  sourcePath: 'fullName',
  targetPaths: '["firstName","lastName"]',
  parameters: '{"separator":" ","targets":["firstName","lastName"]}',
  ...overrides,
});

describe('SPLIT operation', () => {

  describe('executeSplit', () => {
    test('splits value into two targets', () => {
      const body = { fullName: 'Rishi Kumar' };
      const result = executeSplit(body, rule());
      expect(result.firstName).toBe('Rishi');
      expect(result.lastName).toBe('Kumar');
      expect(result.fullName).toBeUndefined();
    });

    test('last target gets remainder for 3-word value', () => {
      const body = { fullName: 'Rishi Kumar Singh' };
      const result = executeSplit(body, rule());
      expect(result.firstName).toBe('Rishi');
      expect(result.lastName).toBe('Kumar Singh'); // remainder joined
    });

    test('assigns empty string when more targets than parts', () => {
      const body = { fullName: 'Rishi' };
      const r = rule({
        targetPaths: '["firstName","lastName","suffix"]',
        parameters: '{"separator":" "}',
      });
      const result = executeSplit(body, r);
      expect(result.firstName).toBe('Rishi');
      expect(result.lastName).toBe('');
      expect(result.suffix).toBe('');
    });

    test('assigns whole value to first target when no separator found', () => {
      const body = { fullName: 'MonoWord' };
      const result = executeSplit(body, rule());
      expect(result.firstName).toBe('MonoWord');
      expect(result.lastName).toBe('');
    });

    test('throws when source field is missing', () => {
      const body = { other: 'val' };
      expect(() => executeSplit(body, rule())).toThrow(/fullName.*missing/i);
    });

    test('throws when targetPaths is invalid JSON', () => {
      const body = { fullName: 'Rishi Kumar' };
      expect(() => executeSplit(body, rule({ targetPaths: 'not-json' }))).toThrow(
        /targetPaths.*valid JSON/i
      );
    });

    test('throws when targetPaths is empty array', () => {
      const body = { fullName: 'Rishi Kumar' };
      expect(() => executeSplit(body, rule({ targetPaths: '[]' }))).toThrow();
    });

    test('handles custom separator (comma)', () => {
      const body = { csv: 'a,b,c' };
      const r = rule({
        sourcePath: 'csv',
        targetPaths: '["x","y","z"]',
        parameters: '{"separator":","}',
      });
      const result = executeSplit(body, r);
      expect(result.x).toBe('a');
      expect(result.y).toBe('b');
      expect(result.z).toBe('c');
    });
  });

  describe('reverseSplit', () => {
    test('joins target fields back into source', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      const result = reverseSplit(body, rule());
      expect(result.fullName).toBe('Rishi Kumar');
      expect(result.firstName).toBeUndefined();
      expect(result.lastName).toBeUndefined();
    });

    test('returns null when a target field is missing', () => {
      const body = { firstName: 'Rishi' }; // lastName missing
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      const result = reverseSplit(body, rule());
      expect(result).toBeNull();
      warnSpy.mockRestore();
    });

    test('returns null when targetPaths is invalid JSON', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      expect(reverseSplit(body, rule({ targetPaths: 'bad' }))).toBeNull();
    });

    test('uses separator from parameters', () => {
      const body = { first: 'a', last: 'b' };
      const r = rule({
        sourcePath: 'combined',
        targetPaths: '["first","last"]',
        parameters: '{"separator":"-"}',
      });
      const result = reverseSplit(body, r);
      expect(result.combined).toBe('a-b');
    });
  });
});
