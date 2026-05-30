import type { TestDesignPromptTrendBucketView, TestDesignPromptTrendView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import type { TestDesignQualitySummaryTone } from './testDesignQualitySummary';

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
};

export type TestDesignPromptTrendSummary = {
  scopeLabel: string;
  totalTasks: number;
  totalCandidates: number;
  latestVersion?: string;
  metrics: TestDesignPromptTrendMetric[];
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
  const riskBucketCount = buckets.filter((bucket) => bucket.errorCount > 0 || bucket.duplicateKeyCollisionCount > 0).length;
  const feedbackBucketCount = buckets.filter((bucket) => bucket.correctionCount + bucket.rejectedCount + bucket.ignoredCount > 0).length;
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
        label: '有风险版本',
        value: riskBucketCount,
        desc: buckets.length ? `${riskBucketCount}/${buckets.length}` : '暂无版本',
        tone: riskBucketCount > 0 ? 'warning' : 'success'
      }
    ],
    buckets,
    warnings: buildWarnings(buckets)
  };
}

function toBucket(bucket: TestDesignPromptTrendBucketView): TestDesignPromptTrendBucket {
  const promptKey = sanitize(bucket.promptKey || 'UNKNOWN', 48);
  const promptVersion = sanitize(bucket.promptVersion || 'UNKNOWN', 32);
  const riskCount = bucket.errorCount + bucket.duplicateKeyCollisionCount;
  return {
    ...bucket,
    promptKey,
    promptVersion,
    label: `${promptKey}@${promptVersion}`,
    tone: riskCount > 0 ? 'warning' : bucket.stepCompletePercent >= 100 && bucket.expectedCompletePercent >= 100 ? 'success' : 'info',
    qualityText: `步骤 ${formatPercent(bucket.stepCompletePercent)} · 预期 ${formatPercent(bucket.expectedCompletePercent)}`,
    feedbackText: `反馈 ${formatPercent(bucket.feedbackSignalPercent)} · 修正 ${bucket.correctionCount} · 驳回 ${bucket.rejectedCount} · 忽略 ${bucket.ignoredCount}`,
    riskText: `低置信 ${formatPercent(bucket.lowConfidencePercent)} · 错误 ${formatPercent(bucket.errorPercent)} · 重复 ${bucket.duplicateKeyCollisionCount}`
  };
}

function buildWarnings(buckets: TestDesignPromptTrendBucket[]) {
  const warnings: TestDesignPromptTrendSummary['warnings'] = [];
  const errorBuckets = buckets.filter((bucket) => bucket.errorCount > 0).length;
  const duplicateBuckets = buckets.filter((bucket) => bucket.duplicateKeyCollisionCount > 0).length;
  const lowStepBuckets = buckets.filter((bucket) => bucket.candidateCount > 0 && bucket.stepCompletePercent < 100).length;
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
