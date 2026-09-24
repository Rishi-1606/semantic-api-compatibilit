'use strict';

const { executeMove, reverseMove } = require('../../../src/executor/operations/move');

const rule = (overrides = {}) => ({
  id: 2, ruleStatus: 'APPROVED',
  sourcePath: 'mobile', targetPath: 'contact.phone',
  ...overrides,
});

describe('MOVE operation', () => {

  describe('executeMove', () => {
    test('moves flat field to nested path', () => {
      const body = { mobile: '9876543210', name: 'Rishi' };
      const result = executeMove(body, rule());
      expect(result.contact.phone).toBe('9876543210');
      expect(result.mobile).toBeUndefined();
      expect(result.name).toBe('Rishi');
    });

    test('creates intermediate objects', () => {
      const body = { mobile: '999' };
      const result = executeMove(body, rule({ targetPath: 'a.b.c' }));
      expect(result.a.b.c).toBe('999');
    });

    test('skips with warning when source field is missing', () => {
      const body = { name: 'Rishi' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      executeMove(body, rule());
      expect(body.contact).toBeUndefined();
      warnSpy.mockRestore();
    });

    test('throws when target intermediate is not a plain object', () => {
      const body = { mobile: '999', contact: 'string' };
      expect(() => executeMove(body, rule())).toThrow();
    });

    test('moves null values', () => {
      const body = { mobile: null };
      const result = executeMove(body, rule());
      expect(result.contact.phone).toBeNull();
    });
  });

  describe('reverseMove (= FLATTEN direction)', () => {
    test('flattens nested field back to flat', () => {
      const body = { contact: { phone: '9876543210' }, name: 'Rishi' };
      const result = reverseMove(body, rule());
      expect(result.mobile).toBe('9876543210');
      expect(result.contact).toBeUndefined(); // empty parent cleaned up
    });

    test('skips when reverse source (targetPath) is missing', () => {
      const body = { name: 'Rishi' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      reverseMove(body, rule());
      expect(body.mobile).toBeUndefined();
      warnSpy.mockRestore();
    });
  });
});
