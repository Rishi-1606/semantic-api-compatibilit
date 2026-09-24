'use strict';

const { getPath, setPath, deletePath, deepClone, isNested } = require('../../src/executor/pathUtils');

describe('pathUtils', () => {

  // ── getPath ──────────────────────────────────────────────────────
  describe('getPath', () => {
    test('reads a top-level field', () => {
      expect(getPath({ name: 'Rishi' }, 'name')).toBe('Rishi');
    });

    test('reads a nested field (2 levels)', () => {
      expect(getPath({ contact: { phone: '999' } }, 'contact.phone')).toBe('999');
    });

    test('reads a deeply nested field (3 levels)', () => {
      expect(getPath({ a: { b: { c: 42 } } }, 'a.b.c')).toBe(42);
    });

    test('returns undefined for a missing top-level field', () => {
      expect(getPath({ name: 'Rishi' }, 'missing')).toBeUndefined();
    });

    test('returns undefined for a missing nested field', () => {
      expect(getPath({ contact: {} }, 'contact.phone')).toBeUndefined();
    });

    test('returns undefined when intermediate segment is not an object', () => {
      expect(getPath({ contact: 'string' }, 'contact.phone')).toBeUndefined();
    });

    test('returns undefined for null obj', () => {
      expect(getPath(null, 'name')).toBeUndefined();
    });

    test('returns undefined for empty path', () => {
      expect(getPath({ name: 'Rishi' }, '')).toBeUndefined();
    });

    test('reads null value at a path', () => {
      expect(getPath({ name: null }, 'name')).toBeNull();
    });

    test('reads numeric zero', () => {
      expect(getPath({ count: 0 }, 'count')).toBe(0);
    });

    test('reads boolean false', () => {
      expect(getPath({ active: false }, 'active')).toBe(false);
    });
  });

  // ── setPath ──────────────────────────────────────────────────────
  describe('setPath', () => {
    test('sets a top-level field', () => {
      const obj = { name: 'old' };
      setPath(obj, 'name', 'new');
      expect(obj.name).toBe('new');
    });

    test('creates a nested field', () => {
      const obj = {};
      setPath(obj, 'contact.phone', '999');
      expect(obj.contact.phone).toBe('999');
    });

    test('creates deeply nested fields', () => {
      const obj = {};
      setPath(obj, 'a.b.c', 42);
      expect(obj.a.b.c).toBe(42);
    });

    test('overwrites an existing nested field', () => {
      const obj = { contact: { phone: 'old' } };
      setPath(obj, 'contact.phone', 'new');
      expect(obj.contact.phone).toBe('new');
    });

    test('throws when intermediate is not a plain object (string)', () => {
      const obj = { contact: 'not-an-object' };
      expect(() => setPath(obj, 'contact.phone', '999')).toThrow();
    });

    test('throws when intermediate is an array', () => {
      const obj = { items: [] };
      expect(() => setPath(obj, 'items.phone', '999')).toThrow();
    });

    test('throws for empty path', () => {
      expect(() => setPath({}, '', 'val')).toThrow();
    });
  });

  // ── deletePath ───────────────────────────────────────────────────
  describe('deletePath', () => {
    test('deletes a top-level field', () => {
      const obj = { name: 'Rishi', other: 1 };
      expect(deletePath(obj, 'name')).toBe(true);
      expect(obj.name).toBeUndefined();
      expect(obj.other).toBe(1);
    });

    test('deletes a nested field', () => {
      const obj = { contact: { phone: '999', email: 'a@b.com' } };
      deletePath(obj, 'contact.phone');
      expect(obj.contact.phone).toBeUndefined();
      expect(obj.contact.email).toBe('a@b.com');
    });

    test('cleans up empty parent objects', () => {
      const obj = { contact: { phone: '999' } };
      deletePath(obj, 'contact.phone');
      expect(obj.contact).toBeUndefined();
    });

    test('does not clean up non-empty parent objects', () => {
      const obj = { contact: { phone: '999', email: 'a@b.com' } };
      deletePath(obj, 'contact.phone');
      expect(obj.contact).toBeDefined();
      expect(obj.contact.email).toBe('a@b.com');
    });

    test('returns false for a missing field', () => {
      expect(deletePath({ name: 'Rishi' }, 'missing')).toBe(false);
    });

    test('returns false for empty path', () => {
      expect(deletePath({}, '')).toBe(false);
    });

    test('cleans up multiple levels of empty parents', () => {
      const obj = { a: { b: { c: 42 } } };
      deletePath(obj, 'a.b.c');
      expect(obj.a).toBeUndefined();
    });
  });

  // ── deepClone ────────────────────────────────────────────────────
  describe('deepClone', () => {
    test('returns a separate object', () => {
      const original = { name: 'Rishi', nested: { phone: '999' } };
      const clone = deepClone(original);
      clone.name = 'changed';
      clone.nested.phone = 'changed';
      expect(original.name).toBe('Rishi');
      expect(original.nested.phone).toBe('999');
    });

    test('preserves values', () => {
      const original = { a: 1, b: 'str', c: true, d: null, e: { f: [1, 2] } };
      expect(deepClone(original)).toEqual(original);
    });
  });

  // ── isNested ─────────────────────────────────────────────────────
  describe('isNested', () => {
    test('returns true for a dotted path', () => {
      expect(isNested('contact.phone')).toBe(true);
    });

    test('returns false for a flat path', () => {
      expect(isNested('name')).toBe(false);
    });

    test('returns false for null', () => {
      expect(isNested(null)).toBe(false);
    });
  });
});
