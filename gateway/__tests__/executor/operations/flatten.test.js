'use strict';

const { executeFlatten, reverseFlatten } = require('../../../src/executor/operations/flatten');

const rule = (overrides = {}) => ({
  id: 3, ruleStatus: 'APPROVED',
  sourcePath: 'contact.phone', targetPath: 'phoneNumber',
  ...overrides,
});

describe('FLATTEN operation', () => {

  describe('executeFlatten', () => {
    test('flattens nested field to flat', () => {
      const body = { contact: { phone: '9876543210' }, name: 'Rishi' };
      const result = executeFlatten(body, rule());
      expect(result.phoneNumber).toBe('9876543210');
      expect(result.contact).toBeUndefined(); // empty parent removed
      expect(result.name).toBe('Rishi');
    });

    test('does not remove non-empty parent', () => {
      const body = { contact: { phone: '999', email: 'a@b.com' } };
      executeFlatten(body, rule());
      expect(body.contact).toBeDefined();
      expect(body.contact.email).toBe('a@b.com');
    });

    test('skips with warning when nested source is missing', () => {
      const body = { name: 'Rishi' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      executeFlatten(body, rule());
      expect(body.phoneNumber).toBeUndefined();
      warnSpy.mockRestore();
    });

    test('handles deeply nested source paths', () => {
      const body = { a: { b: { c: 'deep' } } };
      const result = executeFlatten(body, rule({ sourcePath: 'a.b.c', targetPath: 'flat' }));
      expect(result.flat).toBe('deep');
      expect(result.a).toBeUndefined();
    });

    test('preserves null values when flattening', () => {
      const body = { contact: { phone: null } };
      const result = executeFlatten(body, rule());
      expect(result.phoneNumber).toBeNull();
    });
  });

  describe('reverseFlatten (= MOVE direction)', () => {
    test('moves flat field back to nested', () => {
      const body = { phoneNumber: '9876543210', name: 'Rishi' };
      const result = reverseFlatten(body, rule());
      expect(result.contact.phone).toBe('9876543210');
      expect(result.phoneNumber).toBeUndefined();
    });

    test('skips when reverse source is missing', () => {
      const body = { name: 'Rishi' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      reverseFlatten(body, rule());
      expect(body.contact).toBeUndefined();
      warnSpy.mockRestore();
    });
  });
});
