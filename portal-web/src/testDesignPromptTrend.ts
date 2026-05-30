import type { TestDesignPromptTrendBucketView, TestDesignPromptTrendView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import {
  readinessStatusLabel,
  readinessTone,
  type TestDesignQualitySummaryTone
} from './testDesignQualitySummary';

export type TestDesignPromptTrendMetric = {
  label: string;
  value: number;
  desc: string;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignPromptTrendBucket = TestDesignPromptTrendBucketView & {
  label: string;
  tone: TestDesignQualitySummaryTone;
  qualityText: string;
  feedbackText: string;
  riskText: string;
  readinessLabel: string;
  readinessText: string;
  readinessTone: TestDesignQualitySummaryTone;
};

export type TestDesignPromptTrendSummary = {
  scopeLabel: string;
  totalTasks: number;
  totalCandidates: number;
  latestVersion?: string;
  metrics: TestDesignPromptTrendMetric[];
  readinessDistribution: Array<{ label: string; count: number; percent: number; tone: TestDesignQualitySummaryTone }>;
  buckets: TestDesignPromptTrendBucket[];
  warnings: Array<{ label: string; count: number; tone: TestDesignQualitySummaryTone }>;
};

export function buildTestDesignPromptTrendSummary(
  trend: TestDesignPromptTrendView | null | undefined
): TestDesignPromptTrendSummary {
  const buckets = (trend?.buckets ?? []).map(toBucket);
  const totalTasks = normalizeCount(trend?.taskCount);
  const totalCandidates = normalizeCount(trend?.candidateCount);
  const latestBucket = buckets.find((bucket) => bucket.latestTaskCreatedAt) ?? buckets[0];
  const feedbackBucketCount = buckets.filter((bucket) => bucket.correctionCount + bucket.rejectedCount + bucket.ignoredCount > 0).length;
  const readinessDistribution = promptReadinessDistribution(trend, buckets);
  const blockedVersionCount = readinessDistribution.find((item) => item.label === '准出阻断')?.count ?? 0;
  const warningVersionCount = readinessDistribution.find((item) => item.label === '准出风险')?.count ?? 0;
  return {
    scopeLabel: scopeLabel(trend),
    totalTasks,
    totalCandidates,
    latestVersion: latestBucket?.promptVersion,
    metrics: [
      {
        label: '版本数',
        value: buckets.length,
        desc: totalTasks ? `${totalTasks} 个任务` : '暂无任务',
        tone: buckets.length > 0 ? 'info' : 'neutral'
      },
      {
        label: '候选数',
        value: totalCandidates,
        desc: totalCandidates ? `${totalCandidates} 个候选` : '暂无候选',
        tone: totalCandidates > 0 ? 'success' : 'neutral'
      },
      {
        label: '有反馈版本',
        value: feedbackBucketCount,
        desc: buckets.length ? `${feedbackBucketCount}/${buckets.length}` : '暂无版本',
        tone: feedbackBucketCount > 0 ? 'info' : 'neutral'
      },
      {
        label: '阻断版本',
        value: blockedVersionCount,
        desc: buckets.length ? `${blockedVersionCount}/${buckets.length}` : '暂无版本',
        tone: blockedVersionCount > 0 ? 'danger' : 'success'
      },
      {
        label: '风险版本',
        value: warningVersionCount,
        desc: buckets.length ? `${warningVersionCount}/${buckets.length}` : '暂无版本',
        tone: warningVersionCount > 0 ? 'warning' : 'success'
      }
    ],
    readinessDistribution,
    buckets,
    warnings: buildWarnings(buckets)
  };
}

function toBucket(bucket: TestDesignPromptTrendBucketView): TestDesignPromptTrendBucket {
  const promptKey = sanitize(bucket.promptKey || 'UNKNOWN', 48);
  const promptVersion = sanitize(bucket.promptVersion || 'UNKNOWN', 32);
  const riskCount = bucket.errorCount + bucket.duplicateKeyCollisionCount;
  const bucketReadinessTone = readinessTone(bucket.readiness?.status ?? '');
  return {
    ...bucket,
    promptKey,
    promptVersion,
    label: `${promptKey}@${promptVersion}`,
    tone: bucketReadinessTone !== 'neutral'
      ? bucketReadinessTone
      : riskCount > 0
        ? 'warning'
        : bucket.stepCompletePercent >= 100 && bucket.expectedCompletePercent >= 100 ? 'success' : 'info',
    qualityText: `步骤 ${formatPercent(bucket.stepCompletePercent)} · 预期 ${formatPercent(bucket.expectedCompletePercent)}`,
    feedbackText: `反馈 ${formatPercent(bucket.feedbackSignalPercent)} · 修正 ${bucket.correctionCount} · 驳回 ${bucket.rejectedCount} · 忽略 ${bucket.ignoredCount}`,
    riskText: `低置信 ${formatPercent(bucket.lowConfidencePercent)} · 错误 ${formatPercent(bucket.errorPercent)} · 重复 ${bucket.duplicateKeyCollisionCount}`,
    readinessLabel: readinessStatusLabel(bucket.readiness?.status ?? ''),
    readinessText: bucket.readiness
      ? `阻断 ${bucket.readiness.blockingCount} · 风险 ${bucket.readiness.warningCount}`
      : '准出未计算',
    readinessTone: bucketReadinessTone
  };
}

function buildWarnings(buckets: TestDesignPromptTrendBucket[]) {
  const warnings: TestDesignPromptTrendSummary['warnings'] = [];
  const blockedBuckets = buckets.filter((bucket) => bucket.readiness?.status === 'BLOCKED').length;
  const warningBuckets = buckets.filter((bucket) => bucket.readiness?.status === 'WARNING').length;
  const errorBuckets = buckets.filter((bucket) => bucket.errorCount > 0).length;
  const duplicateBuckets = buckets.filter((bucket) => bucket.duplicateKeyCollisionCount > 0).length;
  const lowStepBuckets = buckets.filter((bucket) => bucket.candidateCount > 0 && bucket.stepCompletePercent < 100).length;
  if (blockedBuckets > 0) {
    warnings.push({ label: '准出阻断版本', count: blockedBuckets, tone: 'danger' });
  }
  if (warningBuckets > 0) {
    warnings.push({ label: '准出风险版本', count: warningBuckets, tone: 'warning' });
  }
  if (errorBuckets > 0) {
    warnings.push({ label: '错误版本', count: errorBuckets, tone: 'danger' });
  }
  if (duplicateBuckets > 0) {
    warnings.push({ label: '重复冲突版本', count: duplicateBuckets, tone: 'danger' });
  }
  if (lowStepBuckets > 0) {
    warnings.push({ label: '步骤未满版本', count: lowStepBuckets, tone: 'warning' });
  }
  return warnings;
}

function promptReadinessDistribution(
  trend: TestDesignPromptTrendView | null | undefined,
  buckets: TestDesignPromptTrendBucket[]
): TestDesignPromptTrendSummary['readinessDistribution'] {
  const source = trend?.readinessDistribution?.length
    ? trend.readinessDistribution.map((item) => ({
      label: readinessStatusLabel(item.label),
      count: normalizeCount(item.count),
      percent: Number.isFinite(item.percent) ? item.percent : 0,
      tone: readinessTone(item.label)
    }))
    : fallbackReadinessDistribution(buckets);
  return source.sort((left, right) => readinessOrder(left.label) - readinessOrder(right.label));
}

function fallbackReadinessDistribution(
  buckets: TestDesignPromptTrendBucket[]
): TestDesignPromptTrendSummary['readinessDistribution'] {
  const total = buckets.length;
  const counts = new Map<string, number>();
  buckets.forEach((bucket) => {
    const status = bucket.readiness?.status || 'UNKNOWN';
    counts.set(status, (counts.get(status) ?? 0) + 1);
  });
  return Array.from(counts.entries()).map(([status, count]) => ({
    label: readinessStatusLabel(status),
    count,
    percent: total ? Math.round(count * 10_000 / total) / 100 : 0,
    tone: readinessTone(status)
  }));
}

function readinessOrder(label: string) {
  if (label === '准出阻断') {
    return 0;
  }
  if (label === '准出风险') {
    return 1;
  }
  if (label === '准出通过') {
    return 2;
  }
  return 3;
}

function scopeLabel(trend: TestDesignPromptTrendView | null | undefined) {
  if (!trend) {
    return 'Prompt 版本趋势未加载';
  }
  const parts: string[] = [];
  if (trend.projectId) {
    parts.push(`项目 ${sanitize(trend.projectId, 32)}`);
  }
  if (trend.promptKey) {
    parts.push(`Prompt ${sanitize(trend.promptKey, 48)}`);
  }
  parts.push(`最近 ${normalizeCount(trend.taskCount)} 个任务`);
  return parts.join(' · ');
}

function formatPercent(value: number) {
  if (!Number.isFinite(value)) {
    return '0%';
  }
  return `${Number(value.toFixed(2))}%`;
}

function normalizeCount(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
}

function sanitize(value: string, maxLength: number) {
  const text = sanitizeTestDesignExportText(value);
  if (text.length <= maxLength) {
    return text || 'UNKNOWN';
  }
  return `${text.slice(0, Math.max(1, maxLength - 4))}...`;
}
