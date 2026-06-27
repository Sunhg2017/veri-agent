import { describe, expect, it } from 'vitest';
import { dictionaryLabel, dictionaryOption, dictionaryOptions, humanizeDictionaryValue } from './dictionaries';

describe('frontend dictionaries', () => {
  it('keeps submitted enum values while exposing localized labels', () => {
    expect(dictionaryLabel('PUBLISH_QUEUED')).toBe('发布排队');
    expect(dictionaryLabel('BUSINESS_FLOW')).toBe('业务流');
    expect(dictionaryLabel('CONFIRMED_FLAKY')).toBe('确认不稳定');

    expect(dictionaryOption('FAILED')).toEqual({
      disabled: false,
      label: '失败',
      value: 'FAILED'
    });
    expect(dictionaryOptions(['DRAFT', 'APPROVED'])).toEqual([
      { disabled: false, label: '草稿', value: 'DRAFT' },
      { disabled: false, label: '已批准', value: 'APPROVED' }
    ]);
  });

  it('formats unknown server enum values without leaking raw underscores', () => {
    expect(humanizeDictionaryValue('NEW_BACKEND_STATUS')).toBe('NEW Backend Status');
    expect(dictionaryLabel('NEW_BACKEND_STATUS')).toBe('NEW Backend Status');
  });
});
