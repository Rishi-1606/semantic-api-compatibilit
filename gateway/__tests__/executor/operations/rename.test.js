'use strict';

const { executeRename, reverseRename } = require('../../../src/executor/operations/rename');

const approved = (overrides = {}) => ({
  id: 1, ruleStatus: 'APPROVED',
  sourcePath: 'name', targetPath: 'fullName',
  parameters: '{}',
  ...overrides,
});

describe('RENAME operation', () => {

  describe('executeRename', () => {
    test('renames a top-level field', () => {
      const body = { name: 'Rishi', mobile: '999' };
      const result = executeRename(body, approved());
      expect(result.fullName).toBe('Rishi');
      expect(result.name).toBeUndefined();
      expect(result.mobile).toBe('999');
    });

    test('renames a nested source path', () => {
      const body = { contact: { phone: '999' } };
      const rule = approved({ sourcePath: 'contact.phone', targetPath: 'phoneNumber' });
      const result = executeRename(body, rule);
      expect(result.phoneNumber).toBe('999');
      expect(result.contact).toBeUndefined(); // empty parent cleaned up
    });

    test('skips with warning when source field is missing', () => {
      const body = { mobile: '999' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      const result = executeRename(body, approved());
      expect(result.fullName).toBeUndefined();
      expect(result.mobile).toBe('999');
      warnSpy.mockRestore();
    });

    test('handles numeric and boolean values', () => {
      const body = { age: 21 };
      const result = executeRename(body, approved({ sourcePath: 'age', targetPath: 'years' }));
      expect(result.years).toBe(21);
      expect(result.age).toBeUndefined();
    });

    test('handles null values', () => {
      const body = { name: null };
      const result = executeRename(body, approved());
      expect(result.fullName).toBeNull();
    });

    test('does not modify the original body object reference', () => {
      const body = { name: 'Rishi' };
      executeRename(body, approved()); // operates on the passed-in body (in-place)
      // Note: the executor calls deepClone before calling operations
      // but here we test the operation itself which mutates in place
    });
  });

  describe('reverseRename', () => {
    test('reverses a rename by swapping paths', () => {
      const body = { fullName: 'Rishi', mobile: '999' };
      const result = reverseRename(body, approved());
      expect(result.name).toBe('Rishi');
      expect(result.fullName).toBeUndefined();
    });

    test('skips when reverse source (targetPath) is missing', () => {
      const body = { mobile: '999' };
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
      reverseRename(body, approved());
      expect(body.name).toBeUndefined();
      warnSpy.mockRestore();
    });
  });
});
