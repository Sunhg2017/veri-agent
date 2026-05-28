import { describe, expect, it } from 'vitest';
import type { TestDesignReviewRecordView } from './api/testDesign';
import { buildTestDesignReviewSummary } from './testDesignReviewSummary';

const baseRecord: TestDesignReviewRecordView = {
  id: 'review-1',
  taskId: 'task-1',
  candidateId: 'candidate-1',
  title: '验证登录成功',
  projectId: 'project-1',
  action: 'UPDATE',
  beforeStatus: 'GENERATED',
  afterStatus: 'EDITED',
  reviewer: 'qa.lead',
  hasComment: false,
  commentPreview: 'token=secret-value should not appear in summary',
  changedFields: ['title', 'status', 'version'],
  versionBefore: 1,
  versionAfter: 2,
  createdAt: '2026-05-28T10:00:00Z'
};

describe('WP5 test design review summary', () => {
  it('aggregates current review-history page without exposing comments', () => {
    const summary = buildTestDesignReviewSummary([
      baseRecord,
      {
        ...baseRecord,
        id: 'review-2',
        candidateId: 'candidate-2',
        action: 'CONFIRMED',
        beforeStatus: 'EDITED',
        afterStatus: 'CONFIRMED',
        reviewer: 'qa.lead',
        hasComment: true,
        changedFields: ['status', 'version'],
        versionBefore: 2,
        versionAfter: 3
      },
      {
        ...baseRecord,
        id: 'review-3',
        candidateId: 'candidate-3',
        action: 'REJECTED',
        beforeStatus: 'GENERATED',
        afterStatus: 'REJECTED',
        reviewer: 'pm.owner',
        hasComment: true,
        changedFields: [],
        versionBefore: 1,
        versionAfter: 2
      }
    ], 9);

    expect(summary.total).toBe(9);
    expect(summary.pageTotal).toBe(3);
    expect(summary.commentCount).toBe(2);
    expect(summary.statusChangeCount).toBe(3);
    expect(summary.fieldChangeCount).toBe(2);
    expect(summary.versionChangeCount).toBe(3);
    expect(summary.reviewerCount).toBe(2);
    expect(summary.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '历史记录', value: 3, desc: '当前页 3 / 全量 9' }),
      expect.objectContaining({ label: '状态流转', value: 3, desc: '当前页 3/3' }),
      expect.objectContaining({ label: '字段变更', value: 2, desc: '当前页 2/3' }),
      expect.objectContaining({ label: '评审说明', value: 2, desc: '当前页 2/3' })
    ]));
    expect(summary.groups.find((group) => group.label === '动作')?.items).toEqual([
      expect.objectContaining({ label: 'UPDATE', count: 1, percent: 33 }),
      expect.objectContaining({ label: 'CONFIRMED', count: 1, percent: 33 }),
      expect.objectContaining({ label: 'REJECTED', count: 1, percent: 33 })
    ]);
    expect(summary.groups.find((group) => group.label === '评审人')?.items).toEqual([
      expect.objectContaining({ label: 'pm.owner', count: 1, percent: 33 }),
      expect.objectContaining({ label: 'qa.lead', count: 2, percent: 67 })
    ]);
    expect(summary.groups.find((group) => group.label === '字段')?.items).toEqual([
      expect.objectContaining({ label: 'title', count: 1, percent: 33 }),
      expect.objectContaining({ label: 'status', count: 2, percent: 67 }),
      expect.objectContaining({ label: 'version', count: 2, percent: 67 })
    ]);
    expect(summary.warnings).toEqual([
      { label: '无评审说明', count: 1, tone: 'warning' },
      { label: '无字段摘要', count: 1, tone: 'warning' },
      { label: '版本流转', count: 3, tone: 'info' }
    ]);
    expect(JSON.stringify(summary)).not.toContain('secret-value');
  });

  it('handles empty review-history pages', () => {
    const summary = buildTestDesignReviewSummary([], 0);

    expect(summary.total).toBe(0);
    expect(summary.pageTotal).toBe(0);
    expect(summary.metrics.map((metric) => metric.desc)).toEqual(['当前页 0', '当前页 0', '当前页 0', '当前页 0']);
    expect(summary.groups.every((group) => group.items.length === 0)).toBe(true);
    expect(summary.warnings).toEqual([]);
  });
});
