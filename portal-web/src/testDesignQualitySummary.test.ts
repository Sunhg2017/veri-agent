import { describe, expect, it } from 'vitest';
import type { TestDesignCandidateView } from './api/testDesign';
import { buildTestDesignQualitySummary, qualitySummaryFromServer } from './testDesignQualitySummary';

const baseCandidate: TestDesignCandidateView = {
  id: 'candidate-1',
  taskId: 'task-1',
  projectId: 'project-1',
  requirementId: 'requirement-1',
  title: '验证登录成功',
  coverageType: 'SMOKE',
  priority: 'HIGH',
  status: 'GENERATED',
  steps: [
    { stepOrder: 1, action: '输入账号密码', expectedResult: '登录按钮可点击' },
    { stepOrder: 2, action: '点击登录', expectedResult: '进入首页' }
  ],
  expectedResult: '用户进入首页',
  tags: ['login'],
  confidence: 0.9,
  version: 1
};

describe('WP5 test design quality summary', () => {
  it('aggregates current candidate page readiness and quality signals', () => {
    const summary = buildTestDesignQualitySummary([
      baseCandidate,
      {
        ...baseCandidate,
        id: 'candidate-2',
        coverageType: 'BOUNDARY',
        priority: 'MEDIUM',
        status: 'CONFIRMED',
        confidence: 0.72
      },
      {
        ...baseCandidate,
        id: 'candidate-3',
        coverageType: 'EXCEPTION',
        priority: 'CRITICAL',
        status: 'FAILED',
        steps: [{ stepOrder: 1, action: '点击登录', expectedResult: '' }],
        expectedResult: '',
        confidence: 0.42,
        errorMessage: 'provider token=secret-value Bearer abc.def.ghi timeout'
      },
      {
        ...baseCandidate,
        id: 'candidate-4',
        coverageType: 'REGRESSION',
        priority: 'LOW',
        status: 'PUBLISHED',
        confidence: undefined
      }
    ], 12);

    expect(summary.total).toBe(12);
    expect(summary.pageTotal).toBe(4);
    expect(summary.reviewableCount).toBe(1);
    expect(summary.publishableCount).toBe(2);
    expect(summary.failedCount).toBe(1);
    expect(summary.confirmedCount).toBe(1);
    expect(summary.publishedCount).toBe(1);
    expect(summary.stepCompleteCount).toBe(3);
    expect(summary.expectedCompleteCount).toBe(3);
    expect(summary.lowConfidenceCount).toBe(1);
    expect(summary.errorCount).toBe(1);

    expect(summary.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '可评审', value: 1, desc: '当前页 1/4' }),
      expect.objectContaining({ label: '可发布', value: 2, desc: '当前页 2/4' }),
      expect.objectContaining({ label: '步骤完整', value: 3, desc: '当前页 3/4' }),
      expect.objectContaining({ label: '预期完整', value: 3, desc: '当前页 3/4' })
    ]));
    expect(summary.distributions.find((group) => group.label === '状态')?.items).toEqual([
      expect.objectContaining({ label: 'GENERATED', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'CONFIRMED', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'FAILED', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'PUBLISHED', count: 1, percent: 25 })
    ]);
    expect(summary.distributions.find((group) => group.label === '覆盖')?.items).toEqual([
      expect.objectContaining({ label: 'SMOKE', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'EXCEPTION', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'BOUNDARY', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'REGRESSION', count: 1, percent: 25 })
    ]);
    expect(summary.warnings).toEqual([
      { label: '失败候选', count: 1, tone: 'danger' },
      { label: '步骤不完整', count: 1, tone: 'warning' },
      { label: '缺少最终预期', count: 1, tone: 'warning' },
      { label: '低置信度', count: 1, tone: 'warning' },
      { label: '错误摘要', count: 1, tone: 'danger' }
    ]);
    expect(JSON.stringify(summary)).not.toContain('secret-value');
    expect(JSON.stringify(summary)).not.toContain('abc.def.ghi');
  });

  it('handles empty pages without producing warnings', () => {
    const summary = buildTestDesignQualitySummary([], 0);

    expect(summary.total).toBe(0);
    expect(summary.pageTotal).toBe(0);
    expect(summary.metrics.map((metric) => metric.desc)).toEqual(['当前页 0', '当前页 0', '当前页 0', '当前页 0']);
    expect(summary.distributions.every((group) => group.items.length === 0)).toBe(true);
    expect(summary.warnings).toEqual([]);
  });

  it('maps full-task server quality summary into dashboard metrics without raw text', () => {
    const summary = qualitySummaryFromServer({
      taskId: 'task-1',
      projectId: 'project-1',
      taskTitle: '任务质量摘要 token=secret-value',
      taskStatus: 'SUCCEEDED',
      scope: 'fullTask',
      total: 4,
      reviewableCount: 1,
      publishableCount: 2,
      failedCount: 1,
      confirmedCount: 1,
      publishedCount: 1,
      stepCompleteCount: 3,
      expectedCompleteCount: 3,
      lowConfidenceCount: 1,
      errorCount: 1,
      missingRequirementCount: 1,
      missingTitleCount: 0,
      duplicateKeyCollisionCount: 1,
      metrics: [{ code: 'publishable', count: 2, percent: 50 }],
      distributions: {
        status: [
          { label: 'CONFIRMED', count: 1, percent: 25 },
          { label: 'FAILED', count: 1, percent: 25 }
        ],
        coverageType: [{ label: 'SMOKE', count: 2, percent: 50 }],
        priority: [{ label: 'HIGH', count: 2, percent: 50 }]
      }
    });

    expect(summary.total).toBe(4);
    expect(summary.pageTotal).toBe(4);
    expect(summary.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '可发布', value: 2, desc: '任务全量 2/4' }),
      expect.objectContaining({ label: '步骤完整', value: 3, desc: '任务全量 3/4' })
    ]));
    expect(summary.distributions.find((group) => group.label === '状态')?.items).toEqual([
      expect.objectContaining({ label: 'CONFIRMED', count: 1, percent: 25 }),
      expect.objectContaining({ label: 'FAILED', count: 1, percent: 25 })
    ]);
    expect(summary.warnings).toEqual(expect.arrayContaining([
      { label: '失败候选', count: 1, tone: 'danger' },
      { label: '缺少需求关联', count: 1, tone: 'warning' },
      { label: '重复键冲突', count: 1, tone: 'danger' }
    ]));
    expect(JSON.stringify(summary)).not.toContain('secret-value');
  });
});
