import type { TestDesignPromptTrendBucketView, TestDesignPromptTrendView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import {
  readinessStatusLabel,
  readinessTone,
  type TestDesignQualitySummaryTone
} from './testDesignQualitySummary';
import { translate } from './platform/i18n';

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
  const blockedVersionCount = readinessDistribution.find((item) => item.label === translate('auto.k2112'))?.count ?? 0;
  const warningVersionCount = readinessDistribution.find((item) => item.label === translate('auto.k2113'))?.count ?? 0;
  return {
    scopeLabel: scopeLabel(trend),
    totalTasks,
    totalCandidates,
    latestVersion: latestBucket?.promptVersion,
    metrics: [
      {
        label: translate('auto.k2114'),
        value: buckets.length,
        desc: totalTasks ? translate('auto.k2115', { value0: totalTasks }) : translate('auto.k2116'),
        tone: buckets.length > 0 ? 'info' : 'neutral'
      },
      {
        label: translate('auto.k2046'),
        value: totalCandidates,
        desc: totalCandidates ? translate('auto.k2117', { value0: totalCandidates }) : translate('auto.k2118'),
        tone: totalCandidates > 0 ? 'success' : 'neutral'
      },
      {
        label: translate('auto.k2119'),
        value: feedbackBucketCount,
        desc: buckets.length ? `${feedbackBucketCount}/${buckets.length}` : translate('auto.k2120'),
        tone: feedbackBucketCount > 0 ? 'info' : 'neutral'
      },
      {
        label: translate('auto.k2121'),
        value: blockedVersionCount,
        desc: buckets.length ? `${blockedVersionCount}/${buckets.length}` : translate('auto.k2120'),
        tone: blockedVersionCount > 0 ? 'danger' : 'success'
      },
      {
        label: translate('auto.k2122'),
        value: warningVersionCount,
        desc: buckets.length ? `${warningVersionCount}/${buckets.length}` : translate('auto.k2120'),
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
    qualityText: translate('auto.k2123', { value0: formatPercent(bucket.stepCompletePercent), value1: formatPercent(bucket.expectedCompletePercent) }),
    feedbackText: translate('auto.k2124', { value0: formatPercent(bucket.feedbackSignalPercent), value1: bucket.correctionCount, value2: bucket.rejectedCount, value3: bucket.ignoredCount }),
    riskText: translate('auto.k2125', { value0: formatPercent(bucket.lowConfidencePercent), value1: formatPercent(bucket.errorPercent), value2: bucket.duplicateKeyCollisionCount }),
    readinessLabel: readinessStatusLabel(bucket.readiness?.status ?? ''),
    readinessText: bucket.readiness
      ? translate('auto.k2126', { value0: bucket.readiness.blockingCount, value1: bucket.readiness.warningCount })
      : translate('auto.k2127'),
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
    warnings.push({ label: translate('auto.k2128'), count: blockedBuckets, tone: 'danger' });
  }
  if (warningBuckets > 0) {
    warnings.push({ label: translate('auto.k2129'), count: warningBuckets, tone: 'warning' });
  }
  if (errorBuckets > 0) {
    warnings.push({ label: translate('auto.k2130'), count: errorBuckets, tone: 'danger' });
  }
  if (duplicateBuckets > 0) {
    warnings.push({ label: translate('auto.k2131'), count: duplicateBuckets, tone: 'danger' });
  }
  if (lowStepBuckets > 0) {
    warnings.push({ label: translate('auto.k2132'), count: lowStepBuckets, tone: 'warning' });
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
  if (label === translate('auto.k2112')) {
    return 0;
  }
  if (label === translate('auto.k2113')) {
    return 1;
  }
  if (label === translate('auto.k2133')) {
    return 2;
  }
  return 3;
}

function scopeLabel(trend: TestDesignPromptTrendView | null | undefined) {
  if (!trend) {
    return translate('auto.k2134');
  }
  const parts: string[] = [];
  if (trend.projectId) {
    parts.push(translate('auto.k2034', { value0: sanitize(trend.projectId, 32) }));
  }
  if (trend.promptKey) {
    parts.push(`Prompt ${sanitize(trend.promptKey, 48)}`);
  }
  parts.push(translate('auto.k2135', { value0: normalizeCount(trend.taskCount) }));
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
