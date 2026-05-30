import { describe, expect, it } from 'vitest';
import type { TestDesignAuditSummaryView } from './api/testDesign';
import { buildTestDesignAuditSummary } from './testDesignAuditSummary';

describe('WP5 task audit summary', () => {
  it('maps task-local audit events into redacted dashboard signals', () => {
    const summary: TestDesignAuditSummaryView = {
      taskId: 'task-audit-1234567890',
      projectId: 'project-pay',
      taskStatus: 'SUCCEEDED',
      requestedBy: 'owner token=secret-value',
      eventCount: 5,
      reviewRecordCount: 2,
      publishRecordCount: 1,
      dryRunRecordCount: 1,
      issueCount: 1,
      noteCoverageCount: 2,
      metrics: [
        { code: 'issues', label: '失败冲突', count: 1, tone: 'warning' }
      ],
      recentEvents: [
        {
          source: 'REVIEW',
          action: 'UPDATE',
          result: 'GENERATED->EDITED',
          candidateId: 'candidate-abcdefghijklmnopqrstuvwxyz',
          actor: 'reviewer token=secret-value',
          hasNote: true,
          createdAt: '2026-05-30T10:00:00Z'
        },
        {
          source: 'PUBLISH',
          action: 'CREATE',
          result: 'FAILED',
          candidateId: 'candidate-failed',
          hasNote: false,
          createdAt: '2026-05-30T10:01:00Z'
        }
      ],
      generatedAt: '2026-05-30T10:02:00Z'
    };

    const dashboard = buildTestDesignAuditSummary(summary);

    expect(dashboard.scopeLabel).toBe('项目 project-pay · 任务 SUCCEEDED · 本域事件 5');
    expect(dashboard.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '本域事件', value: 5, desc: 'SUCCEEDED' }),
      expect.objectContaining({ label: '评审记录', value: 2, desc: '2/5', tone: 'success' }),
      expect.objectContaining({ label: '发布记录', value: 1, desc: '预演 1' }),
      expect.objectContaining({ label: '失败冲突', value: 1, tone: 'warning' })
    ]));
    expect(dashboard.timeline[0]).toMatchObject({
      label: 'REVIEW · UPDATE',
      metaText: 'GENERATED->EDITED · by reviewer token=[REDACTED] · 有说明 · candidat...r...',
      tone: 'info'
    });
    expect(dashboard.timeline[1]).toMatchObject({
      label: 'PUBLISH · CREATE',
      tone: 'warning'
    });
    expect(dashboard.warnings).toEqual([
      { label: '存在失败或冲突', count: 1, tone: 'warning' },
      { label: '最近事件缺说明', count: 1, tone: 'warning' }
    ]);
    expect(JSON.stringify(dashboard)).not.toContain('secret-value');
  });

  it('handles empty summary data', () => {
    const summary = buildTestDesignAuditSummary(null);

    expect(summary.scopeLabel).toBe('审计链摘要未加载');
    expect(summary.metrics.map((metric) => metric.value)).toEqual([0, 0, 0, 0, 0]);
    expect(summary.timeline).toEqual([]);
    expect(summary.warnings).toEqual([]);
  });
});
