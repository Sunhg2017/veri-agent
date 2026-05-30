import { describe, expect, it } from 'vitest';
import type { TestDesignPromptTrendView } from './api/testDesign';
import { buildTestDesignPromptTrendSummary } from './testDesignPromptTrend';

describe('WP5 prompt trend summary', () => {
  it('maps prompt version buckets into aggregate-only dashboard signals', () => {
    const trend: TestDesignPromptTrendView = {
      projectId: 'project-pay',
      promptKey: 'wp5-test-design-v1',
      taskCount: 3,
      candidateCount: 10,
      generatedAt: '2026-05-30T10:00:00Z',
      readinessDistribution: [
        { label: 'PASSED', count: 1, percent: 50 },
        { label: 'BLOCKED', count: 1, percent: 50 }
      ],
      buckets: [
        {
          promptKey: 'wp5-test-design-v1',
          promptVersion: '1.0.0',
          taskCount: 2,
          candidateCount: 6,
          confirmedCount: 3,
          publishedCount: 1,
          stepCompleteCount: 6,
          expectedCompleteCount: 6,
          lowConfidenceCount: 0,
          errorCount: 0,
          duplicateKeyCollisionCount: 0,
          correctionCount: 1,
          rejectedCount: 0,
          ignoredCount: 0,
          stepCompletePercent: 100,
          expectedCompletePercent: 100,
          lowConfidencePercent: 0,
          errorPercent: 0,
          feedbackSignalPercent: 16.67,
          readiness: {
            status: 'PASSED',
            blockingCount: 0,
            warningCount: 0,
            checks: []
          },
          latestTaskCreatedAt: '2026-05-30T09:00:00Z'
        },
        {
          promptKey: 'wp5-test-design-v1',
          promptVersion: '1.1.0 token=secret-value',
          taskCount: 1,
          candidateCount: 4,
          confirmedCount: 1,
          publishedCount: 0,
          stepCompleteCount: 3,
          expectedCompleteCount: 2,
          lowConfidenceCount: 1,
          errorCount: 1,
          duplicateKeyCollisionCount: 1,
          correctionCount: 0,
          rejectedCount: 1,
          ignoredCount: 0,
          stepCompletePercent: 75,
          expectedCompletePercent: 50,
          lowConfidencePercent: 25,
          errorPercent: 25,
          feedbackSignalPercent: 25,
          readiness: {
            status: 'BLOCKED',
            blockingCount: 2,
            warningCount: 1,
            checks: [
              {
                code: 'stepComplete',
                label: '步骤完整率',
                status: 'FAILED',
                severity: 'BLOCKING',
                currentValue: 75,
                thresholdValue: 100,
                unit: 'PERCENT',
                description: '步骤动作和步骤预期均完整的候选占比不得低于阈值'
              }
            ]
          },
          latestTaskCreatedAt: '2026-05-30T08:00:00Z'
        }
      ]
    };

    const summary = buildTestDesignPromptTrendSummary(trend);

    expect(summary.scopeLabel).toBe('项目 project-pay · Prompt wp5-test-design-v1 · 最近 3 个任务');
    expect(summary.latestVersion).toBe('1.0.0');
    expect(summary.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '版本数', value: 2, desc: '3 个任务' }),
      expect.objectContaining({ label: '候选数', value: 10, desc: '10 个候选' }),
      expect.objectContaining({ label: '有反馈版本', value: 2, desc: '2/2' }),
      expect.objectContaining({ label: '阻断版本', value: 1, desc: '1/2', tone: 'danger' }),
      expect.objectContaining({ label: '风险版本', value: 0, desc: '0/2', tone: 'success' })
    ]));
    expect(summary.readinessDistribution).toEqual([
      { label: '准出阻断', count: 1, percent: 50, tone: 'danger' },
      { label: '准出通过', count: 1, percent: 50, tone: 'success' }
    ]);
    expect(summary.buckets[0]).toMatchObject({
      label: 'wp5-test-design-v1@1.0.0',
      tone: 'success',
      qualityText: '步骤 100% · 预期 100%',
      feedbackText: '反馈 16.67% · 修正 1 · 驳回 0 · 忽略 0',
      riskText: '低置信 0% · 错误 0% · 重复 0',
      readinessLabel: '准出通过',
      readinessText: '阻断 0 · 风险 0'
    });
    expect(summary.buckets[1].label).toBe('wp5-test-design-v1@1.1.0 token=[REDACTED]');
    expect(summary.buckets[1]).toMatchObject({
      tone: 'danger',
      readinessLabel: '准出阻断',
      readinessText: '阻断 2 · 风险 1'
    });
    expect(summary.warnings).toEqual([
      { label: '准出阻断版本', count: 1, tone: 'danger' },
      { label: '错误版本', count: 1, tone: 'danger' },
      { label: '重复冲突版本', count: 1, tone: 'danger' },
      { label: '步骤未满版本', count: 1, tone: 'warning' }
    ]);
    expect(JSON.stringify(summary)).not.toContain('secret-value');
  });

  it('handles empty or missing trend data', () => {
    const summary = buildTestDesignPromptTrendSummary(null);

    expect(summary.scopeLabel).toBe('Prompt 版本趋势未加载');
    expect(summary.metrics.map((metric) => metric.value)).toEqual([0, 0, 0, 0, 0]);
    expect(summary.readinessDistribution).toEqual([]);
    expect(summary.buckets).toEqual([]);
    expect(summary.warnings).toEqual([]);
  });
});
