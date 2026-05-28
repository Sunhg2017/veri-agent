import type { TestDesignReviewRecordView } from './api/testDesign';
import type { TestDesignQualitySummaryTone } from './testDesignQualitySummary';

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
  const fieldChangeCount = records.filter((record) => record.changedFields.length > 0).length;
  const versionChangeCount = records.filter(hasVersionChange).length;
  const reviewerCount = countDistinct(records, (record) => normalizeLabel(record.reviewer));

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
        label: '历史记录',
        value: pageTotal,
        desc: total === pageTotal ? `当前页 ${pageTotal}` : `当前页 ${pageTotal} / 全量 ${total}`,
        tone: pageTotal > 0 ? 'info' : 'neutral'
      },
      {
        label: '状态流转',
        value: statusChangeCount,
        desc: formatRatio(statusChangeCount, pageTotal),
        tone: statusChangeCount > 0 ? 'success' : 'neutral'
      },
      {
        label: '字段变更',
        value: fieldChangeCount,
        desc: formatRatio(fieldChangeCount, pageTotal),
        tone: fieldChangeCount > 0 ? 'warning' : 'neutral'
      },
      {
        label: '评审说明',
        value: commentCount,
        desc: formatRatio(commentCount, pageTotal),
        tone: commentCount > 0 ? 'info' : 'neutral'
      }
    ],
    groups: [
      {
        label: '动作',
        items: buildDistribution(records, (record) => normalizeLabel(record.action), ACTION_ORDER, pageTotal, actionTone)
      },
      {
        label: '评审人',
        items: buildDistribution(records, (record) => normalizeLabel(record.reviewer), [], pageTotal)
      },
      {
        label: '字段',
        items: buildFieldDistribution(records, pageTotal)
      }
    ],
    warnings: buildWarnings({
      pageTotal,
      commentCount,
      fieldChangeCount,
      versionChangeCount
    })
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
  return value?.trim() || '未记录';
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
    const fields = Array.from(new Set(record.changedFields.map(normalizeLabel)));
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
      label: '无评审说明',
      count: Math.max(counts.pageTotal - counts.commentCount, 0),
      tone: 'warning'
    },
    {
      label: '无字段摘要',
      count: Math.max(counts.pageTotal - counts.fieldChangeCount, 0),
      tone: 'warning'
    },
    {
      label: '版本流转',
      count: counts.versionChangeCount,
      tone: 'info'
    }
  ];
  return warnings.filter((warning) => warning.count > 0);
}
