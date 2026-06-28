import { describe, expect, it } from 'vitest';
import { dictionaryLabel, dictionaryOption, dictionaryOptions, fieldLabel, humanizeDictionaryValue } from './dictionaries';

describe('frontend dictionaries', () => {
  it('keeps submitted enum values while exposing localized labels', () => {
    expect(dictionaryLabel('PUBLISH_QUEUED')).toBe('发布排队');
    expect(dictionaryLabel('BUSINESS_FLOW')).toBe('业务流');
    expect(dictionaryLabel('CONFIRMED_FLAKY')).toBe('已确认不稳定');

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

  it('maps backend field keys to user-facing Chinese labels', () => {
    expect(fieldLabel('defaultProjectId')).toBe('默认项目 ID');
    expect(fieldLabel('sourceType')).toBe('来源类型');
    expect(fieldLabel('mapping JSON')).toBe('字段映射 JSON');
    expect(fieldLabel('retry requestKey')).toBe('重试请求键');
    expect(fieldLabel('traceId')).toBe('追踪 ID');
    expect(fieldLabel('caseIds')).toBe('用例 ID');
    expect(fieldLabel('reviewNote')).toBe('评审备注');
    expect(fieldLabel('promptKey')).toBe('提示词键');
    expect(fieldLabel('cronScanner')).toBe('Cron 扫描器');
    expect(fieldLabel('maskedSummaryValuesExported')).toBe('脱敏摘要值导出');
    expect(fieldLabel('leaseTokenPlaintextExported')).toBe('租约令牌明文导出');
  });
});
