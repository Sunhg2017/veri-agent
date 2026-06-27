import type { TestDesignReviewRecordView } from './api/testDesign';
import type { TestDesignQualitySummaryTone } from './testDesignQualitySummary';
import { translate } from './platform/i18n';

export type TestDesignReviewSummaryMetric = {
  label: string;
  value: number;
  desc: string;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignReviewSummaryItem = {
  label: string;
  count: number;
  percent: number;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignReviewSummaryGroup = {
  label: string;
  items: TestDesignReviewSummaryItem[];
};

export type TestDesignReviewSummaryWarning = {
  label: string;
  count: number;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignReviewFeedbackLoop = {
  sampleCandidateCount: number;
  promptTuningSignalCount: number;
  correctionCount: number;
  rejectedCount: number;
  ignoredCount: number;
  commentCoverageCount: number;
  commentCoveragePercent: number;
  tone: TestDesignQualitySummaryTone;
  items: TestDesignReviewSummaryItem[];
  warnings: TestDesignReviewSummaryWarning[];
};

export type TestDesignReviewSummary = {
  total: number;
  pageTotal: number;
  commentCount: number;
  statusChangeCount: number;
  fieldChangeCount: number;
  versionChangeCount: number;
  reviewerCount: number;
  metrics: TestDesignReviewSummaryMetric[];
  groups: TestDesignReviewSummaryGroup[];
  warnings: TestDesignReviewSummaryWarning[];
  feedbackLoop: TestDesignReviewFeedbackLoop;
};

const ACTION_ORDER = ['UPDATE', 'CONFIRMED', 'REJECTED', 'IGNORED'];
const FIELD_ORDER = ['title', 'status', 'version', 'coverageType', 'priority', 'tags', 'steps', 'expectedResult'];

export function buildTestDesignReviewSummary(
  records: readonly TestDesignReviewRecordView[],
  totalRecords = records.length
): TestDesignReviewSummary {
  const pageTotal = records.length;
  const total = Math.max(normalizeCount(totalRecords), pageTotal);
  const commentCount = records.filter((record) => record.hasComment).length;
  const statusChangeCount = records.filter(hasStatusChange).length;
  const fieldChangeCount = records.filter(hasChangedField).length;
  const versionChangeCount = records.filter(hasVersionChange).length;
  const reviewerCount = countDistinct(records, (record) => normalizeLabel(record.reviewer));
  const feedbackLoop = buildFeedbackLoop(records, pageTotal);

  return {
    total,
    pageTotal,
    commentCount,
    statusChangeCount,
    fieldChangeCount,
    versionChangeCount,
    reviewerCount,
    metrics: [
      {
        label: translate('auto.k2163'),
        value: pageTotal,
        desc: total === pageTotal ? translate('auto.k2164', { value0: pageTotal }) : translate('auto.k2165', { value0: pageTotal, value1: total }),
        tone: pageTotal > 0 ? 'info' : 'neutral'
      },
      {
        label: translate('auto.k2166'),
        value: statusChangeCount,
        desc: formatRatio(statusChangeCount, pageTotal),
        tone: statusChangeCount > 0 ? 'success' : 'neutral'
      },
      {
        label: translate('auto.k2167'),
        value: fieldChangeCount,
        desc: formatRatio(fieldChangeCount, pageTotal),
        tone: fieldChangeCount > 0 ? 'warning' : 'neutral'
      },
      {
        label: translate('auto.k2168'),
        value: commentCount,
        desc: formatRatio(commentCount, pageTotal),
        tone: commentCount > 0 ? 'info' : 'neutral'
      }
    ],
    groups: [
      {
        label: translate('auto.k0363'),
        items: buildDistribution(records, (record) => normalizeLabel(record.action), ACTION_ORDER, pageTotal, actionTone)
      },
      {
        label: translate('auto.k2169'),
        items: buildDistribution(records, (record) => normalizeLabel(record.reviewer), [], pageTotal)
      },
      {
        label: translate('auto.k2020'),
        items: buildFieldDistribution(records, pageTotal)
      }
    ],
    warnings: buildWarnings({
      pageTotal,
      commentCount,
      fieldChangeCount,
      versionChangeCount
    }),
    feedbackLoop
  };
}

function hasStatusChange(record: TestDesignReviewRecordView) {
  return Boolean(record.beforeStatus && record.afterStatus && record.beforeStatus !== record.afterStatus);
}

function hasVersionChange(record: TestDesignReviewRecordView) {
  return record.versionBefore !== undefined
    && record.versionAfter !== undefined
    && record.versionBefore !== record.versionAfter;
}

function normalizeLabel(value?: string) {
  return value?.trim() || translate('auto.k2170');
}

function normalizeCount(value: number) {
  return Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
}

function formatRatio(count: number, total: number) {
  if (!total) {
    return translate('auto.k2152');
  }
  return translate('auto.k2153', { value0: count, value1: total });
}

function countDistinct(
  records: readonly TestDesignReviewRecordView[],
  valueOf: (record: TestDesignReviewRecordView) => string
) {
  return new Set(records.map(valueOf)).size;
}

function buildDistribution(
  records: readonly TestDesignReviewRecordView[],
  valueOf: (record: TestDesignReviewRecordView) => string,
  preferredOrder: readonly string[],
  pageTotal: number,
  toneOf: (label: string) => TestDesignQualitySummaryTone = () => 'neutral'
): TestDesignReviewSummaryItem[] {
  const counts = records.reduce<Record<string, number>>((current, record) => {
    const label = valueOf(record);
    current[label] = (current[label] ?? 0) + 1;
    return current;
  }, {});

  return entriesToItems(counts, preferredOrder, pageTotal, toneOf);
}

function buildFieldDistribution(records: readonly TestDesignReviewRecordView[], pageTotal: number) {
  const counts = records.reduce<Record<string, number>>((current, record) => {
    const fields = Array.from(new Set(record.changedFields.map((field) => field.trim()).filter(Boolean)));
    fields.forEach((field) => {
      current[field] = (current[field] ?? 0) + 1;
    });
    return current;
  }, {});
  return entriesToItems(counts, FIELD_ORDER, pageTotal, fieldTone);
}

function entriesToItems(
  counts: Record<string, number>,
  preferredOrder: readonly string[],
  pageTotal: number,
  toneOf: (label: string) => TestDesignQualitySummaryTone
): TestDesignReviewSummaryItem[] {
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

function actionTone(action: string): TestDesignQualitySummaryTone {
  if (action === 'CONFIRMED') {
    return 'success';
  }
  if (action === 'REJECTED' || action === 'IGNORED') {
    return 'warning';
  }
  if (action === 'UPDATE') {
    return 'info';
  }
  return 'neutral';
}

function fieldTone(field: string): TestDesignQualitySummaryTone {
  if (field === 'status') {
    return 'success';
  }
  if (field === 'version') {
    return 'info';
  }
  return 'warning';
}

function hasChangedField(record: TestDesignReviewRecordView) {
  return record.changedFields.some((field) => field.trim());
}

function buildWarnings(counts: {
  pageTotal: number;
  commentCount: number;
  fieldChangeCount: number;
  versionChangeCount: number;
}): TestDesignReviewSummaryWarning[] {
  if (!counts.pageTotal) {
    return [];
  }

  const warnings: TestDesignReviewSummaryWarning[] = [
    {
      label: translate('auto.k2171'),
      count: Math.max(counts.pageTotal - counts.commentCount, 0),
      tone: 'warning'
    },
    {
      label: translate('auto.k2172'),
      count: Math.max(counts.pageTotal - counts.fieldChangeCount, 0),
      tone: 'warning'
    },
    {
      label: translate('auto.k2173'),
      count: counts.versionChangeCount,
      tone: 'info'
    }
  ];
  return warnings.filter((warning) => warning.count > 0);
}

function buildFeedbackLoop(
  records: readonly TestDesignReviewRecordView[],
  pageTotal: number
): TestDesignReviewFeedbackLoop {
  const correctionRecords = records.filter(isCorrectionRecord);
  const rejectedRecords = records.filter((record) => normalizeLabel(record.action) === 'REJECTED');
  const ignoredRecords = records.filter((record) => normalizeLabel(record.action) === 'IGNORED');
  const promptSignalRecords = [
    ...correctionRecords,
    ...rejectedRecords,
    ...ignoredRecords
  ];
  const sampleCandidateCount = countDistinctPresent(promptSignalRecords, (record) => record.candidateId);
  const commentCoverageCount = promptSignalRecords.filter((record) => record.hasComment).length;
  const promptTuningSignalCount = promptSignalRecords.length;
  const commentCoveragePercent = promptTuningSignalCount
    ? Math.round((commentCoverageCount / promptTuningSignalCount) * 100)
    : 0;
  const tone = feedbackTone(promptTuningSignalCount, rejectedRecords.length, ignoredRecords.length, commentCoveragePercent);

  return {
    sampleCandidateCount,
    promptTuningSignalCount,
    correctionCount: correctionRecords.length,
    rejectedCount: rejectedRecords.length,
    ignoredCount: ignoredRecords.length,
    commentCoverageCount,
    commentCoveragePercent,
    tone,
    items: [
      {
        label: translate('auto.k2095'),
        count: promptTuningSignalCount,
        percent: formatPercent(promptTuningSignalCount, pageTotal),
        tone
      },
      {
        label: translate('auto.k2096'),
        count: sampleCandidateCount,
        percent: formatPercent(sampleCandidateCount, pageTotal),
        tone
      },
      {
        label: translate('auto.k2097'),
        count: correctionRecords.length,
        percent: formatPercent(correctionRecords.length, pageTotal),
        tone: correctionRecords.length > 0 ? 'info' : 'neutral'
      },
      {
        label: translate('auto.k0214'),
        count: rejectedRecords.length,
        percent: formatPercent(rejectedRecords.length, pageTotal),
        tone: rejectedRecords.length > 0 ? 'warning' : 'neutral'
      },
      {
        label: translate('auto.k0808'),
        count: ignoredRecords.length,
        percent: formatPercent(ignoredRecords.length, pageTotal),
        tone: ignoredRecords.length > 0 ? 'warning' : 'neutral'
      },
      {
        label: translate('auto.k2027'),
        count: commentCoverageCount,
        percent: commentCoveragePercent,
        tone: commentCoveragePercent >= 80 || promptTuningSignalCount === 0 ? 'success' : 'warning'
      }
    ],
    warnings: buildFeedbackWarnings(promptTuningSignalCount, commentCoverageCount)
  };
}

function isCorrectionRecord(record: TestDesignReviewRecordView) {
  return normalizeLabel(record.action) === 'UPDATE'
    && record.changedFields.some((field) => {
      const normalizedField = field.trim();
      return Boolean(normalizedField) && normalizedField !== 'status' && normalizedField !== 'version';
    });
}

function formatPercent(count: number, total: number) {
  return total ? Math.round((count / total) * 100) : 0;
}

function countDistinctPresent(
  records: readonly TestDesignReviewRecordView[],
  valueOf: (record: TestDesignReviewRecordView) => string | undefined
) {
  return new Set(records.map((record) => valueOf(record)?.trim()).filter(Boolean)).size;
}

function feedbackTone(
  promptTuningSignalCount: number,
  rejectedCount: number,
  ignoredCount: number,
  commentCoveragePercent: number
): TestDesignQualitySummaryTone {
  if (!promptTuningSignalCount) {
    return 'neutral';
  }
  if (commentCoveragePercent < 50) {
    return 'warning';
  }
  if (rejectedCount + ignoredCount > 0) {
    return 'info';
  }
  return 'success';
}

function buildFeedbackWarnings(
  promptTuningSignalCount: number,
  commentCoverageCount: number
): TestDesignReviewSummaryWarning[] {
  if (!promptTuningSignalCount) {
    return [];
  }

  const warnings: TestDesignReviewSummaryWarning[] = [
    {
      label: translate('auto.k2174'),
      count: Math.max(promptTuningSignalCount - commentCoverageCount, 0),
      tone: 'warning'
    }
  ];
  return warnings.filter((warning) => warning.count > 0);
}
