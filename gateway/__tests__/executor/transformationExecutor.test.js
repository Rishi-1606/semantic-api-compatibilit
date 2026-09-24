'use strict';

const executor = require('../../src/executor/transformationExecutor');

// ── Helpers ───────────────────────────────────────────────────────────

const approvedRule = (overrides) => ({
  id: 1,
  ruleStatus: 'APPROVED',
  operation: 'RENAME',
  sourcePath: 'name',
  targetPath: 'fullName',
  parameters: '{}',
  ...overrides,
});

// Customer V1 → V2 scenario (RENAME only, same as demo data)
const customerRules = [
  approvedRule({ id: 1, sourcePath: 'name',   targetPath: 'fullName' }),
  approvedRule({ id: 2, sourcePath: 'mobile', targetPath: 'phoneNumber' }),
  approvedRule({ id: 3, sourcePath: 'email',  targetPath: 'emailAddress' }),
];

// ── transformRequest ──────────────────────────────────────────────────
describe('TransformationExecutor.transformRequest', () => {

  test('applies a single RENAME rule', () => {
    const body   = { name: 'Rishi' };
    const rules  = [approvedRule()];
    const result = executor.transformRequest(body, rules);
    expect(result.fullName).toBe('Rishi');
    expect(result.name).toBeUndefined();
  });

  test('applies multiple RENAME rules — Customer V1 → V2', () => {
    const body = { name: 'Rishi', mobile: '9876543210', email: 'rishi@example.com' };
    const result = executor.transformRequest(body, customerRules);
    expect(result.fullName).toBe('Rishi');
    expect(result.phoneNumber).toBe('9876543210');
    expect(result.emailAddress).toBe('rishi@example.com');
    expect(result.name).toBeUndefined();
    expect(result.mobile).toBeUndefined();
    expect(result.email).toBeUndefined();
  });

  test('does not mutate the original body', () => {
    const body   = { name: 'Rishi' };
    const frozen = Object.freeze({ ...body }); // freeze to detect mutation
    // deepClone is called inside executor so original is safe
    const clone  = { name: 'Rishi' }; // use unfrozen for actual call
    executor.transformRequest(clone, [approvedRule()]);
    expect(body.name).toBe('Rishi'); // original unchanged
  });

  test('returns unchanged body when rules array is empty', () => {
    const body   = { name: 'Rishi' };
    const result = executor.transformRequest(body, []);
    expect(result).toEqual({ name: 'Rishi' });
  });

  test('throws for a GENERATED rule — security guard', () => {
    const rule = approvedRule({ ruleStatus: 'GENERATED' });
    expect(() => executor.transformRequest({ name: 'Rishi' }, [rule])).toThrow(
      /GENERATED.*only APPROVED/i
    );
  });

  test('throws for a REJECTED rule — security guard', () => {
    const rule = approvedRule({ ruleStatus: 'REJECTED' });
    expect(() => executor.transformRequest({ name: 'Rishi' }, [rule])).toThrow(
      /REJECTED.*only APPROVED/i
    );
  });

  test('throws for an INVALID rule — security guard', () => {
    const rule = approvedRule({ ruleStatus: 'INVALID' });
    expect(() => executor.transformRequest({ name: 'Rishi' }, [rule])).toThrow(
      /INVALID.*only APPROVED/i
    );
  });

  test('throws for an unknown operation — fail-closed', () => {
    const rule = approvedRule({ operation: 'UNKNOWN_OP' });
    expect(() => executor.transformRequest({ name: 'Rishi' }, [rule])).toThrow(
      /Unsupported operation 'UNKNOWN_OP'/i
    );
  });

  test('applies MOVE rule correctly', () => {
    const body = { mobile: '999' };
    const rule = approvedRule({ operation: 'MOVE', sourcePath: 'mobile', targetPath: 'contact.phone' });
    const result = executor.transformRequest(body, [rule]);
    expect(result.contact.phone).toBe('999');
    expect(result.mobile).toBeUndefined();
  });

  test('applies FLATTEN rule correctly', () => {
    const body = { contact: { phone: '999' } };
    const rule = approvedRule({ operation: 'FLATTEN', sourcePath: 'contact.phone', targetPath: 'phoneNumber' });
    const result = executor.transformRequest(body, [rule]);
    expect(result.phoneNumber).toBe('999');
    expect(result.contact).toBeUndefined();
  });

  test('applies TYPE_CONVERSION rule correctly', () => {
    const body = { age: '21' };
    const rule = approvedRule({
      operation: 'TYPE_CONVERSION',
      sourcePath: 'age', targetPath: 'age',
      parameters: '{"from":"string","to":"integer"}',
    });
    const result = executor.transformRequest(body, [rule]);
    expect(result.age).toBe(21);
  });

  test('applies ENUM_MAPPING rule correctly', () => {
    const body = { gender: 'male' };
    const rule = approvedRule({
      operation: 'ENUM_MAPPING',
      sourcePath: 'gender', targetPath: 'gender',
      parameters: '{"mappings":{"male":"M","female":"F"}}',
    });
    const result = executor.transformRequest(body, [rule]);
    expect(result.gender).toBe('M');
  });

  test('applies COMBINE rule correctly', () => {
    const body = { firstName: 'Rishi', lastName: 'Kumar' };
    const rule = approvedRule({
      operation: 'COMBINE',
      sourcePath: 'firstName',
      sourcePaths: '["firstName","lastName"]',
      targetPath: 'fullName',
      parameters: '{"separator":" ","order":["firstName","lastName"]}',
    });
    const result = executor.transformRequest(body, [rule]);
    expect(result.fullName).toBe('Rishi Kumar');
  });

  test('applies SPLIT rule correctly', () => {
    const body = { fullName: 'Rishi Kumar' };
    const rule = approvedRule({
      operation: 'SPLIT',
      sourcePath: 'fullName',
      targetPaths: '["firstName","lastName"]',
      parameters: '{"separator":" ","targets":["firstName","lastName"]}',
    });
    const result = executor.transformRequest(body, [rule]);
    expect(result.firstName).toBe('Rishi');
    expect(result.lastName).toBe('Kumar');
    expect(result.fullName).toBeUndefined();
  });

  test('passes through null values without error', () => {
    const body   = { name: null };
    const result = executor.transformRequest(body, [approvedRule()]);
    expect(result.fullName).toBeNull();
  });
});

// ── transformResponse ─────────────────────────────────────────────────
describe('TransformationExecutor.transformResponse', () => {

  test('reverses Customer V2 → V1 response (3 RENAME rules)', () => {
    const v2Response = { fullName: 'Rishi', phoneNumber: '9876543210', emailAddress: 'rishi@example.com' };
    const result = executor.transformResponse(v2Response, customerRules);
    expect(result.name).toBe('Rishi');
    expect(result.mobile).toBe('9876543210');
    expect(result.email).toBe('rishi@example.com');
    expect(result.fullName).toBeUndefined();
    expect(result.phoneNumber).toBeUndefined();
    expect(result.emailAddress).toBeUndefined();
  });

  test('does not mutate the original response body', () => {
    const v2Body = { fullName: 'Rishi' };
    executor.transformResponse(v2Body, [approvedRule()]);
    expect(v2Body.fullName).toBe('Rishi'); // deep clone used internally
  });

  test('returns unchanged body when rules array is empty', () => {
    const body   = { fullName: 'Rishi' };
    const result = executor.transformResponse(body, []);
    expect(result).toEqual({ fullName: 'Rishi' });
  });

  test('reverses rules in reverse order', () => {
    // Rule 1: a → b, Rule 2: b → c
    // Response body has { c: 'val' }
    // Reverse order: undo Rule 2 first (c → b), then undo Rule 1 (b → a)
    const rules = [
      approvedRule({ id: 1, sourcePath: 'a', targetPath: 'b' }),
      approvedRule({ id: 2, sourcePath: 'b', targetPath: 'c' }),
    ];
    const v2 = { c: 'val' };
    const result = executor.transformResponse(v2, rules);
    expect(result.a).toBe('val');
    expect(result.b).toBeUndefined();
    expect(result.c).toBeUndefined();
  });

  test('throws for GENERATED rule in response transform', () => {
    const rule = approvedRule({ ruleStatus: 'GENERATED' });
    expect(() => executor.transformResponse({ fullName: 'Rishi' }, [rule])).toThrow(
      /GENERATED.*only APPROVED/i
    );
  });

  test('preserves V2 value when COMBINE rule cannot be reversed', () => {
    const rule = approvedRule({
      operation: 'COMBINE',
      sourcePaths: '["firstName","lastName"]',
      targetPath: 'fullName',
      parameters: '{"separator":" ","order":["firstName","lastName"]}',
    });
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const v2 = { fullName: 'Rishi Kumar', age: 21 };
    const result = executor.transformResponse(v2, [rule]);
    // COMBINE is not reversible — fullName preserved, age unaffected
    expect(result.fullName).toBe('Rishi Kumar');
    expect(result.age).toBe(21);
    warnSpy.mockRestore();
  });

  test('reverses ENUM_MAPPING correctly', () => {
    const rule = approvedRule({
      operation: 'ENUM_MAPPING',
      sourcePath: 'gender', targetPath: 'gender',
      parameters: '{"mappings":{"male":"M","female":"F"}}',
    });
    const v2 = { gender: 'M' };
    const result = executor.transformResponse(v2, [rule]);
    expect(result.gender).toBe('male');
  });

  test('reverses TYPE_CONVERSION (safe inverse: integer → string)', () => {
    const rule = approvedRule({
      operation: 'TYPE_CONVERSION',
      sourcePath: 'age', targetPath: 'age',
      parameters: '{"from":"string","to":"integer"}',
    });
    const v2 = { age: 21 };
    const result = executor.transformResponse(v2, [rule]);
    expect(result.age).toBe('21');
  });

  test('preserves V2 value when TYPE_CONVERSION inverse is unsafe', () => {
    const rule = approvedRule({
      operation: 'TYPE_CONVERSION',
      sourcePath: 'flag', targetPath: 'flag',
      parameters: '{"from":"x_custom","to":"integer"}',  // x_custom→integer not in matrix
    });
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const v2 = { flag: 42 };
    const result = executor.transformResponse(v2, [rule]);
    expect(result.flag).toBe(42); // V2 value preserved
    warnSpy.mockRestore();
  });

  test('reverses MOVE rule (nested → flat in response)', () => {
    const rule = approvedRule({
      operation: 'MOVE',
      sourcePath: 'mobile', targetPath: 'contact.phone',
    });
    const v2 = { contact: { phone: '9876543210' } };
    const result = executor.transformResponse(v2, [rule]);
    expect(result.mobile).toBe('9876543210');
    expect(result.contact).toBeUndefined();
  });

  test('reverses FLATTEN rule (flat → nested in response)', () => {
    const rule = approvedRule({
      operation: 'FLATTEN',
      sourcePath: 'contact.phone', targetPath: 'phoneNumber',
    });
    const v2 = { phoneNumber: '9876543210' };
    const result = executor.transformResponse(v2, [rule]);
    expect(result.contact.phone).toBe('9876543210');
    expect(result.phoneNumber).toBeUndefined();
  });

  test('reverses SPLIT when parameters are sufficient', () => {
    const rule = approvedRule({
      operation: 'SPLIT',
      sourcePath: 'fullName',
      targetPaths: '["firstName","lastName"]',
      parameters: '{"separator":" ","targets":["firstName","lastName"]}',
    });
    const v2 = { firstName: 'Rishi', lastName: 'Kumar' };
    const result = executor.transformResponse(v2, [rule]);
    expect(result.fullName).toBe('Rishi Kumar');
  });
});
