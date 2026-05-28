import type { TestDesignCandidateView, TestDesignQualitySummaryView } from './api/testDesign';
import { canPublishTestDesignCandidate, canReviewTestDesignCandidate } from './testDesignSelection';

export type TestDesignQualitySummaryTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

export type TestDesignQualitySummaryMetric = {
  label: string;
  value: number;
  desc: string;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignQualityDistributionItem = {
  label: string;
  count: number;
  percent: number;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignQualityDistribution = {
  label: string;
  items: TestDesignQualityDistributionItem[];
};

export type TestDesignQualityWarning = {
  label: string;
  count: number;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignQualitySummary = {
  total: number;
  pageTotal: number;
  reviewableCount: number;
  publishableCount: number;
  failedCount: number;
  confirmedCount: number;
  publishedCount: number;
  stepCompleteCount: number;
  expectedCompleteCount: number;
  lowConfidenceCount: number;
  errorCount: number;
  metrics: TestDesignQualitySummaryMetric[];
  distributions: TestDesignQualityDistribution[];
  warnings: TestDesignQualityWarning[];
};

const LOW_CONFIDENCE_THRESHOLD = 0.6;
const STATUS_ORDER = ['GENERATED', 'EDITED', 'CONFIRMED', 'FAILED', 'REJECTED', 'IGNORED', 'PUBLISHED'];
const COVERAGE_ORDER = ['SMOKE', 'FUNCTIONAL', 'EXCEPTION', 'BOUNDARY', 'PERMISSION', 'REGRESSION'];
const PRIORITY_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export function buildTestDesignQualitySummary(
  candidates: readonly TestDesignCandidateView[],
  totalCandidates = candidates.length
): TestDesignQualitySummary {
  const pageTotal = candidates.length;
  const total = Math.max(normalizeCount(totalCandidates), pageTotal);
  const reviewableCount = candidates.filter(canReviewTestDesignCandidate).length;
  const publishableCount = candidates.filter(canPublishTestDesignCandidate).length;
  const failedCount = candidates.filter((candidate) => candidate.status === 'FAILED').length;
  const confirmedCount = candidates.filter((candidate) => candidate.status === 'CONFIRMED').length;
  const publishedCount = candidates.filter((candidate) => candidate.status === 'PUBLISHED').length;
  const stepCompleteCount = candidates.filter(hasCompleteSteps).length;
  const expectedCompleteCount = candidates.filter((candidate) => hasText(candidate.expectedResult)).length;
  const lowConfidenceCount = candidates.filter(hasLowConfidence).length;
  const errorCount = candidates.filter((candidate) => hasText(candidate.errorMessage)).length;
  const stepIncompleteCount = Math.max(pageTotal - stepCompleteCount, 0);
  const missingExpectedCount = Math.max(pageTotal - expectedCompleteCount, 0);

  return {
    total,
    pageTotal,
    reviewableCount,
    publishableCount,
    failedCount,
    confirmedCount,
    publishedCount,
    stepCompleteCount,
    expectedCompleteCount,
    lowConfidenceCount,
    errorCount,
    metrics: [
      {
        label: '可评审',
        value: reviewableCount,
        desc: formatRatio(reviewableCount, pageTotal),
        tone: reviewableCount > 0 ? 'info' : 'neutral'
      },
      {
        label: '可发布',
        value: publishableCount,
        desc: formatRatio(publishableCount, pageTotal),
        tone: publishableCount > 0 ? 'success' : 'neutral'
      },
      {
        label: '步骤完整',
        value: stepCompleteCount,
        desc: formatRatio(stepCompleteCount, pageTotal),
        tone: stepIncompleteCount > 0 ? 'warning' : 'success'
      },
      {
        label: '预期完整',
        value: expectedCompleteCount,
        desc: formatRatio(expectedCompleteCount, pageTotal),
        tone: missingExpectedCount > 0 ? 'warning' : 'success'
      }
    ],
    distributions: [
      {
        label: '状态',
        items: buildDistribution(candidates, (candidate) => candidate.status, STATUS_ORDER, pageTotal, statusTone)
      },
      {
        label: '覆盖',
        items: buildDistribution(candidates, (candidate) => candidate.coverageType, COVERAGE_ORDER, pageTotal)
      },
      {
        label: '优先级',
        items: buildDistribution(candidates, (candidate) => candidate.priority, PRIORITY_ORDER, pageTotal, priorityTone)
      }
    ],
    warnings: buildWarnings({
      failedCount,
      stepIncompleteCount,
      missingExpectedCount,
      lowConfidenceCount,
      errorCount
    })
  };
}

export function qualitySummaryFromServer(summary: TestDesignQualitySummaryView | null): TestDesignQualitySummary {
  if (!summary) {
    return buildTestDesignQualitySummary([], 0);
  }
  const total = normalizeCount(summary.total);
  const stepIncompleteCount = Math.max(total - summary.stepCompleteCount, 0);
  const missingExpectedCount = Math.max(total - summary.expectedCompleteCount, 0);
  const warnings = buildWarnings({
    failedCount: summary.failedCount,
    stepIncompleteCount,
    missingExpectedCount,
    lowConfidenceCount: summary.lowConfidenceCount,
    errorCount: summary.errorCount
  });
  if (summary.missingRequirementCount > 0) {
    warnings.push({ label: '缺少需求关联', count: summary.missingRequirementCount, tone: 'warning' });
  }
  if (summary.missingTitleCount > 0) {
    warnings.push({ label: '缺少标题', count: summary.missingTitleCount, tone: 'warning' });
  }
  if (summary.duplicateKeyCollisionCount > 0) {
    warnings.push({ label: '重复键冲突', count: summary.duplicateKeyCollisionCount, tone: 'danger' });
  }

  return {
    total,
    pageTotal: total,
    reviewableCount: summary.reviewableCount,
    publishableCount: summary.publishableCount,
    failedCount: summary.failedCount,
    confirmedCount: summary.confirmedCount,
    publishedCount: summary.publishedCount,
    stepCompleteCount: summary.stepCompleteCount,
    expectedCompleteCount: summary.expectedCompleteCount,
    lowConfidenceCount: summary.lowConfidenceCount,
    errorCount: summary.errorCount,
    metrics: [
      {
        label: '可评审',
        value: summary.reviewableCount,
        desc: formatFullTaskRatio(summary.reviewableCount, total),
        tone: summary.reviewableCount > 0 ? 'info' : 'neutral'
      },
      {
        label: '可发布',
        value: summary.publishableCount,
        desc: formatFullTaskRatio(summary.publishableCount, total),
        tone: summary.publishableCount > 0 ? 'success' : 'neutral'
      },
      {
        label: '步骤完整',
        value: summary.stepCompleteCount,
        desc: formatFullTaskRatio(summary.stepCompleteCount, total),
        tone: stepIncompleteCount > 0 ? 'warning' : 'success'
      },
      {
        label: '预期完整',
        value: summary.expectedCompleteCount,
        desc: formatFullTaskRatio(summary.expectedCompleteCount, total),
        tone: missingExpectedCount > 0 ? 'warning' : 'success'
      }
    ],
    distributions: [
      {
        label: '状态',
        items: mapServerDistribution(summary.distributions.status, statusTone)
      },
      {
        label: '覆盖',
        items: mapServerDistribution(summary.distributions.coverageType)
      },
      {
        label: '优先级',
        items: mapServerDistribution(summary.distributions.priority, priorityTone)
      }
    ],
    warnings
  };
}

function hasCompleteSteps(candidate: TestDesignCandidateView) {
  return candidate.steps.length > 0 && candidate.steps.every((step) => hasText(step.action) && hasText(step.expectedResult));
}

function hasLowConfidence(candidate: TestDesignCandidateView) {
  return typeof candidate.confidence === 'number'
    && Number.isFinite(candidate.confidence)
    && candidate.confidence < LOW_CONFIDENCE_THRESHOLD;
}

function hasText(value?: string) {
  return Boolean(value?.trim());
}

function normalizeCount(value: number) {
  return Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
}

function formatRatio(count: number, total: number) {
  if (!total) {
    return '当前页 0';
  }
  return `当前页 ${count}/${total}`;
}

function formatFullTaskRatio(count: number, total: number) {
  if (!total) {
    return '任务全量 0';
  }
  return `任务全量 ${count}/${total}`;
}

function mapServerDistribution(
  items: readonly { label: string; count: number; percent: number }[] | undefined,
  toneOf: (label: string) => TestDesignQualitySummaryTone = () => 'neutral'
): TestDesignQualityDistributionItem[] {
  return (items ?? []).map((item) => ({
    label: item.label,
    count: item.count,
    percent: Math.round(item.percent),
    tone: toneOf(item.label)
  }));
}

function buildDistribution(
  candidates: readonly TestDesignCandidateView[],
  valueOf: (candidate: TestDesignCandidateView) => string | undefined,
  preferredOrder: readonly string[],
  pageTotal: number,
  toneOf: (label: string) => TestDesignQualitySummaryTone = () => 'neutral'
): TestDesignQualityDistributionItem[] {
  const counts = candidates.reduce<Record<string, number>>((current, candidate) => {
    const label = valueOf(candidate)?.trim() || '未填写';
    current[label] = (current[label] ?? 0) + 1;
    return current;
  }, {});

  return Object.entries(counts)
    .sort(([left], [right]) => compareByPreferredOrder(left, right, preferredOrder))
    .map(([label, count]) => ({
      label,
      count,
      percent: pageTotal ? Math.round((count / pageTotal) * 100) : 0,
      tone: toneOf(label)
    }));
}

function compareByPreferredOrder(left: string, right: string, preferredOrder: readonly string[]) {
  const leftIndex = preferredOrder.indexOf(left);
  const rightIndex = preferredOrder.indexOf(right);
  const normalizedLeftIndex = leftIndex === -1 ? Number.MAX_SAFE_INTEGER : leftIndex;
  const normalizedRightIndex = rightIndex === -1 ? Number.MAX_SAFE_INTEGER : rightIndex;
  if (normalizedLeftIndex !== normalizedRightIndex) {
    return normalizedLeftIndex - normalizedRightIndex;
  }
  return left.localeCompare(right);
}

function statusTone(status: string): TestDesignQualitySummaryTone {
  if (status === 'CONFIRMED' || status === 'PUBLISHED') {
    return 'success';
  }
  if (status === 'FAILED' || status === 'REJECTED') {
    return 'danger';
  }
  if (status === 'GENERATED' || status === 'EDITED') {
    return 'warning';
  }
  return 'neutral';
}

function priorityTone(priority: string): TestDesignQualitySummaryTone {
  if (priority === 'CRITICAL' || priority === 'HIGH') {
    return 'danger';
  }
  if (priority === 'MEDIUM') {
    return 'warning';
  }
  return 'neutral';
}

function buildWarnings(counts: {
  failedCount: number;
  stepIncompleteCount: number;
  missingExpectedCount: number;
  lowConfidenceCount: number;
  errorCount: number;
}): TestDesignQualityWarning[] {
  const warnings: TestDesignQualityWarning[] = [
    { label: '失败候选', count: counts.failedCount, tone: 'danger' },
    { label: '步骤不完整', count: counts.stepIncompleteCount, tone: 'warning' },
    { label: '缺少最终预期', count: counts.missingExpectedCount, tone: 'warning' },
    { label: '低置信度', count: counts.lowConfidenceCount, tone: 'warning' },
    { label: '错误摘要', count: counts.errorCount, tone: 'danger' }
  ];
  return warnings.filter((warning) => warning.count > 0);
}
