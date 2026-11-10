import { describe, it, expect } from 'vitest';
import { parsePayloadSchema } from './RequestsPage';

describe('parsePayloadSchema resilience & validation', () => {
  it('parses valid JSON string schema correctly', () => {
    const raw = JSON.stringify({
      type: 'object',
      properties: {
        expenseType: { type: 'string', title: 'Expense Type' },
        estimatedAmount: { type: 'number', title: 'Amount ($)' },
      },
      required: ['expenseType', 'estimatedAmount'],
    });

    const parsed = parsePayloadSchema(raw);
    expect(parsed).not.toBeNull();
    expect(parsed?.properties).toBeDefined();
    expect(Object.keys(parsed!.properties!)).toContain('expenseType');
    expect(Object.keys(parsed!.properties!)).toContain('estimatedAmount');
    expect(parsed?.required).toEqual(['expenseType', 'estimatedAmount']);
  });

  it('handles double-stringified JSON gracefully', () => {
    const inner = JSON.stringify({
      type: 'object',
      properties: {
        deviceModel: { type: 'string' },
      },
    });
    const doubleEncoded = JSON.stringify(inner);

    const parsed = parsePayloadSchema(doubleEncoded);
    expect(parsed).not.toBeNull();
    expect(parsed?.properties?.deviceModel).toBeDefined();
  });

  it('handles parsed object directly', () => {
    const obj = {
      type: 'object',
      properties: {
        ramGb: { type: 'integer' },
      },
    };

    const parsed = parsePayloadSchema(obj);
    expect(parsed).not.toBeNull();
    expect(parsed?.properties?.ramGb).toBeDefined();
  });

  it('falls back to standard expense schema when raw schema is empty or null', () => {
    const parsed = parsePayloadSchema(null, 'EXPENSE_REIMBURSEMENT');
    expect(parsed).not.toBeNull();
    expect(parsed?.properties?.expenseType).toBeDefined();
    expect(parsed?.properties?.expenseDate).toBeDefined();
    expect(parsed?.properties?.estimatedAmount).toBeDefined();
    expect(parsed?.required).toContain('expenseDate');
  });

  it('falls back to standard software access schema when corrupted', () => {
    const parsed = parsePayloadSchema('not-json-at-all', 'SOFTWARE_ACCESS');
    expect(parsed).not.toBeNull();
    expect(parsed?.properties?.softwareName).toBeDefined();
    expect(parsed?.properties?.licenseType).toBeDefined();
    expect(parsed?.required).toContain('softwareName');
  });

  it('falls back to laptop schema for hardware categories', () => {
    const parsed = parsePayloadSchema('', 'DEVELOPER_LAPTOP');
    expect(parsed).not.toBeNull();
    expect(parsed?.properties?.deviceModel).toBeDefined();
    expect(parsed?.properties?.ramGb).toBeDefined();
    expect(parsed?.required).toContain('deviceModel');
  });
});
