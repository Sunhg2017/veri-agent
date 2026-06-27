import { describe, expect, it } from 'vitest';
import { assetVersionDiffRows, formatAssetVersionDiffValue } from './assetVersionDiff';

describe('assetVersionDiff', () => {
  it('extracts field-level rows from diff_json before/after records', () => {
    const rows = assetVersionDiffRows({
      title: { before: '旧标题', after: '新标题' },
      steps: {
        before: [{ action: '输入', expectedResult: '通过' }],
        after: [
          { action: '输入', expectedResult: '通过' },
          { action: '提交', expectedResult: '成功' }
        ]
      }
    });

    expect(rows).toMatchObject([
      { path: 'title', before: '旧标题', after: '新标题', tone: 'changed' },
      { path: 'steps', tone: 'changed' }
    ]);
    expect(formatAssetVersionDiffValue(rows[1].after)).toContain('2. 提交 => 成功');
  });

  it('walks nested diff records and marks added fields', () => {
    const rows = assetVersionDiffRows({
      metadata: {
        tags: { after: ['smoke', 'login'] }
      }
    });

    expect(rows).toEqual([
      { path: 'metadata.tags', before: undefined, after: ['smoke', 'login'], tone: 'added' }
    ]);
  });
});
