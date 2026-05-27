import { describe, expect, it } from 'vitest';
import {
  buildTestDesignBatchEditConfirmation,
  buildTestDesignBatchReviewConfirmation,
  buildTestDesignPublishConfirmation,
  testDesignBatchActionLabel
} from './testDesignConfirmation';

describe('WP5 confirmation summaries', () => {
  it('summarizes batch review actions with review comment and candidate versions', () => {
    const summary = buildTestDesignBatchReviewConfirmation('REJECT', [
      { id: 'cand-1', title: '登录成功', status: 'GENERATED', version: 1 },
      { id: 'cand-2', title: '登录失败', status: 'EDITED', version: 3 }
    ], '缺少边界断言');

    expect(summary).toMatchObject({
      title: '批量驳回候选',
      confirmLabel: '确认批量驳回',
      tone: 'warning',
      candidateTitles: ['登录成功', '登录失败']
    });
    expect(summary.details).toEqual(expect.arrayContaining([
      { label: '候选数', value: 2 },
      { label: '评审意见', value: '缺少边界断言' },
      { label: '版本', value: 'cand-1@v1, cand-2@v3' }
    ]));
    expect(summary.warnings).toContain('驳回后候选不会进入发布池，后续需要重新编辑或重新生成。');
  });

  it('marks non-reviewable candidates in batch review summaries', () => {
    const summary = buildTestDesignBatchReviewConfirmation('CONFIRM', [
      { id: 'cand-1', title: '已发布候选', status: 'PUBLISHED', version: 2 }
    ], '');

    expect(testDesignBatchActionLabel('CONFIRM')).toBe('确认');
    expect(summary.tone).toBe('info');
    expect(summary.warnings).toEqual(expect.arrayContaining([
      '包含 1 个当前不可评审候选，提交前请刷新选择。',
      '确认后候选会进入发布池，请确认标题、步骤和预期结果已完成评审。'
    ]));
  });

  it('summarizes batch field edits with changed fields and optimistic versions', () => {
    const summary = buildTestDesignBatchEditConfirmation([
      { id: 'cand-1', title: '登录成功', status: 'GENERATED', version: 1 },
      { id: 'cand-2', title: '登录失败', status: 'EDITED', version: 3 }
    ], ['覆盖类型=BOUNDARY', '追加标签=regression']);

    expect(summary).toMatchObject({
      title: '确认批量编辑候选',
      confirmLabel: '确认批量编辑',
      tone: 'warning',
      candidateTitles: ['登录成功', '登录失败']
    });
    expect(summary.details).toEqual(expect.arrayContaining([
      { label: '候选数', value: 2 },
      { label: '变更字段', value: '覆盖类型=BOUNDARY；追加标签=regression' },
      { label: '版本', value: 'cand-1@v1, cand-2@v3' }
    ]));
    expect(summary.warnings).toContain('批量编辑会逐条保存候选，并将成功项置为 EDITED。');
  });

  it('summarizes publish scope and warns before writing to WP3', () => {
    const summary = buildTestDesignPublishConfirmation(false, [
      { id: 'cand-1', title: '确认候选', status: 'CONFIRMED', version: 4 },
      { id: 'cand-2', title: '失败重试候选', status: 'FAILED', version: 5 }
    ], 6, 3);

    expect(summary).toMatchObject({
      title: '确认发布到资产库',
      confirmLabel: '确认发布',
      tone: 'warning',
      candidateTitles: ['确认候选', '失败重试候选']
    });
    expect(summary.details).toEqual(expect.arrayContaining([
      { label: '发布范围', value: '2 / 3 个已选候选可发布' },
      { label: '可发布候选', value: 6 },
      { label: '待重试候选', value: 1 }
    ]));
    expect(summary.warnings).toEqual(expect.arrayContaining([
      '正式发布会写入 WP3 测试用例并创建需求追踪关系。',
      '包含 1 个 FAILED 候选，将作为失败重试范围重新发布。'
    ]));
  });

  it('keeps dry-run summaries informational when no failed candidates are included', () => {
    const summary = buildTestDesignPublishConfirmation(true, [
      { id: 'cand-1', title: '确认候选', status: 'CONFIRMED', version: 1 }
    ], 1, 0);

    expect(summary.tone).toBe('info');
    expect(summary.details).toContainEqual({ label: '发布范围', value: '全部可发布候选' });
    expect(summary.warnings).toContain('预发布只做 dryRun 检查，不会写入 WP3 资产库。');
  });
});
