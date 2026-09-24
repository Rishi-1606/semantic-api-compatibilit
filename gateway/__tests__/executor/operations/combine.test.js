'use strict';

const { executeCombine, reverseCombine } = require('../../../src/executor/operations/combine');

const rule = (overrides = {}) => ({
  id: 4, ruleStatus: 'APPROVED',
  sourcePaths: '["firstName","lastName"]',
  targetPath: 'fullName',
  parameters: '{"separator":" ","order":["firstName","lastName"]}',
  ...overrides,
});

describe('COMBINE operation', () => {

  describe('executeCombine', () => {
    test('combines two fields with space separator', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      const result = executeCombine(body, rule());
      expect(result.fullName).toBe('Rishi Kumar');
      expect(result.firstName).toBeUndefined();
      expect(result.lastName).toBeUndefined();
    });

    test('uses custom separator', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      const r = rule({ parameters: '{"separator":", ","order":["firstName","lastName"]}' });
      const result = executeCombine(body, r);
      expect(result.fullName).toBe('Rishi, Kumar');
    });

    test('combines three fields', () => {
      const body = { a: 'Hello', b: 'World', c: '!' };
      const r = rule({
        sourcePaths: '["a","b","c"]',
        parameters: '{"separator":" ","order":["a","b","c"]}',
        targetPath: 'combined',
      });
      const result = executeCombine(body, r);
      expect(result.combined).toBe('Hello World !');
    });

    test('throws when a required source field is missing', () => {
      const body = { firstName: 'Rishi' }; // lastName missing
      expect(() => executeCombine(body, rule())).toThrow(
        /COMBINE.*lastName.*missing/i
      );
    });

    test('throws when sourcePaths is not valid JSON', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      const r = rule({ sourcePaths: 'not-json' });
      expect(() => executeCombine(body, r)).toThrow(/sourcePaths.*valid JSON/i);
    });

    test('throws when sourcePaths is an empty array', () => {
      const body = { name: 'Rishi' };
      expect(() => executeCombine(body, rule({ sourcePaths: '[]' }))).toThrow();
    });

    test('uses sourcePaths order when parameters.order is missing', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      const r = rule({ parameters: '{"separator":"-"}' });
      const result = executeCombine(body, r);
      expect(result.fullName).toBe('Rishi-Kumar');
    });

    test('uses default space separator when parameters is empty', () => {
      const body = { firstName: 'Rishi', lastName: 'Kumar' };
      const r = rule({ parameters: '{}' });
      const result = executeCombine(body, r);
      expect(result.fullName).toBe('Rishi Kumar');
    });

    test('converts numeric values to string before combining', () => {
      const body = { code: 42, name: 'widget' };
      const r = rule({
        sourcePaths: '["code","name"]',
        targetPath: 'label',
        parameters: '{"separator":":","order":["code","name"]}',
      });
      const result = executeCombine(body, r);
      expect(result.label).toBe('42:widget');
    });
  });

  describe('reverseCombine', () => {
    test('returns null (not reversible)', () => {
      expect(reverseCombine()).toBeNull();
    });
  });
});
