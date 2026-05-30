import type { TestDesignAuditSummaryView, TestDesignAuditTimelineItemView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import type { TestDesignQualitySummaryTone } from './testDesignQualitySummary';

export type TestDesignAuditMetric = {
  label: string;
  value: number;
  desc: string;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignAuditTimelineItem = TestDesignAuditTimelineItemView & {
  label: string;
  metaText: string;
  tone: TestDesignQualitySummaryTone;
};

export type TestDesignAuditSummary = {
  scopeLabel: string;
  metrics: TestDesignAuditMetric[];
  timeline: TestDesignAuditTimelineItem[];
  warnings: Array<{ label: string; count: number; tone: TestDesignQualitySummaryTone }>;
};

export function buildTestDesignAuditSummary(
  summary: TestDesignAuditSummaryView | null | undefined
): TestDesignAuditSummary {
  const eventCount = normalizeCount(summary?.eventCount);
  const reviewRecordCount = normalizeCount(summary?.reviewRecordCount);
  const publishRecordCount = normalizeCount(summary?.publishRecordCount);
  const dryRunRecordCount = normalizeCount(summary?.dryRunRecordCount);
  const issueCount = normalizeCount(summary?.issueCount);
  const noteCoverageCount = normalizeCount(summary?.noteCoverageCount);
  const timeline = (summary?.recentEvents ?? []).map(toTimelineItem);
  return {
    scopeLabel: scopeLabel(summary),
    metrics: [
      {
        label: '本域事件',
        value: eventCount,
        desc: summary?.taskStatus ? sanitize(summary.taskStatus, 24) : '未加载',
        tone: eventCount > 1 ? 'info' : 'neutral'
      },
      {
        label: '评审记录',
        value: reviewRecordCount,
        desc: formatRatio(reviewRecordCount, eventCount),
        tone: reviewRecordCount > 0 ? 'success' : 'neutral'
      },
      {
        label: '发布记录',
        value: publishRecordCount,
        desc: dryRunRecordCount ? `预演 ${dryRunRecordCount}` : '无预演',
        tone: publishRecordCount > 0 ? 'info' : 'neutral'
      },
      {
        label: '失败冲突',
        value: issueCount,
        desc: formatRatio(issueCount, eventCount),
        tone: issueCount > 0 ? 'warning' : 'success'
      },
      {
        label: '说明覆盖',
        value: noteCoverageCount,
        desc: formatRatio(noteCoverageCount, eventCount),
        tone: noteCoverageCount > 0 ? 'info' : 'neutral'
      }
    ],
    timeline,
    warnings: buildWarnings(issueCount, noteCoverageCount, timeline)
  };
}

function toTimelineItem(item: TestDesignAuditTimelineItemView): TestDesignAuditTimelineItem {
  const source = sanitize(item.source || 'UNKNOWN', 24);
  const action = sanitize(item.action || 'UNKNOWN', 36);
  const result = sanitize(item.result || 'UNKNOWN', 40);
  return {
    ...item,
    source,
    action,
    result,
    actor: item.actor ? sanitize(item.actor, 32) : undefined,
    label: `${source} · ${action}`,
    metaText: [
      result,
      item.actor ? `by ${sanitize(item.actor, 32)}` : '',
      item.hasNote ? '有说明' : '无说明',
      compactId(item.candidateId)
    ].filter(Boolean).join(' · '),
    tone: timelineTone(item)
  };
}

function timelineTone(item: TestDesignAuditTimelineItemView): TestDesignQualitySummaryTone {
  const result = (item.result || '').toUpperCase();
  if (result.includes('FAILED') || result.includes('CONFLICT')) {
    return 'warning';
  }
  if (result.includes('SUCCEEDED') || result.includes('PUBLISHED') || result.includes('CONFIRMED')) {
    return 'success';
  }
  if (item.source === 'REVIEW' || item.source === 'PUBLISH' || item.source === 'PUBLISH_DRY_RUN') {
    return 'info';
  }
  return 'neutral';
}

function buildWarnings(
  issueCount: number,
  noteCoverageCount: number,
  timeline: TestDesignAuditTimelineItem[]
) {
  const warnings: TestDesignAuditSummary['warnings'] = [];
  const missingNoteCount = timeline.filter((item) => !item.hasNote && item.source !== 'TASK').length;
  if (issueCount > 0) {
    warnings.push({ label: '存在失败或冲突', count: issueCount, tone: 'warning' });
  }
  if (missingNoteCount > 0) {
    warnings.push({ label: '最近事件缺说明', count: missingNoteCount, tone: 'warning' });
  }
  if (noteCoverageCount === 0 && timeline.length > 1) {
    warnings.push({ label: '无人工说明', count: timeline.length - 1, tone: 'warning' });
  }
  return warnings;
}

function scopeLabel(summary: TestDesignAuditSummaryView | null | undefined) {
  if (!summary) {
    return '审计链摘要未加载';
  }
  const parts: string[] = [];
  if (summary.projectId) {
    parts.push(`项目 ${sanitize(summary.projectId, 32)}`);
  }
  if (summary.taskStatus) {
    parts.push(`任务 ${sanitize(summary.taskStatus, 24)}`);
  }
  parts.push(`本域事件 ${normalizeCount(summary.eventCount)}`);
  return parts.join(' · ');
}

function formatRatio(count: number, total: number) {
  return total ? `${count}/${total}` : '0/0';
}

function compactId(value?: string) {
  const text = sanitize(value || '', 32);
  if (!text || text === '-') {
    return '';
  }
  return text.length <= 14 ? text : `${text.slice(0, 8)}...${text.slice(-4)}`;
}

function normalizeCount(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
}

function sanitize(value: string, maxLength: number) {
  const text = sanitizeTestDesignExportText(value).trim();
  if (!text) {
    return '-';
  }
  if (text.length <= maxLength) {
    return text;
  }
  return `${text.slice(0, Math.max(1, maxLength - 4))}...`;
}
