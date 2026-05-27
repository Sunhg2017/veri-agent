import type {
  TestDesignCandidateView,
  TestDesignPublishResult,
  TestDesignTaskView
} from './api/testDesign';

export const TEST_DESIGN_EXPORT_CONTENT_TYPE = 'text/csv;charset=UTF-8';

type CsvValue = string | number | boolean | null | undefined;

type TaskExportSummary = Pick<
  TestDesignTaskView,
  'id' | 'title' | 'status' | 'projectId' | 'generatedCount' | 'confirmedCount' | 'publishedCount'
>;

export type TestDesignCandidateReviewExportInput = {
  task?: TaskExportSummary | null;
  candidates: readonly TestDesignCandidateView[];
  scopeLabel: string;
  generatedAt?: string;
};

export type TestDesignPublishResultExportInput = {
  task?: TaskExportSummary | null;
  publishResult: TestDesignPublishResult;
  generatedAt?: string;
};

const CANDIDATE_EXPORT_HEADER = [
  'recordType',
  'metric',
  'value',
  'taskId',
  'taskTitle',
  'taskStatus',
  'projectId',
  'scope',
  'candidateId',
  'requirementId',
  'apiId',
  'title',
  'coverageType',
  'priority',
  'status',
  'version',
  'tags',
  'stepsCount',
  'hasExpectedResult',
  'hasReviewNote',
  'reviewNote',
  'assetCaseId',
  'qualityFlags',
  'errorMessage',
  'createdAt',
  'updatedAt'
] as const;

const PUBLISH_EXPORT_HEADER = [
  'recordType',
  'metric',
  'value',
  'taskId',
  'taskTitle',
  'projectId',
  'dryRun',
  'candidateId',
  'title',
  'requirementId',
  'assetCaseId',
  'action',
  'result',
  'errorMessage',
  'createdAt'
] as const;

const SENSITIVE_ASSIGNMENT_PATTERN =
  /\b(api[_-]?key|access[_-]?key|secret|token|password|passwd|pwd|cookie|private[_-]?key)\b\s*[:=]\s*[^,;\s]+/gi;
const BEARER_PATTERN = /\bbearer\s+[A-Za-z0-9._~+/=-]+/gi;

export function buildTestDesignCandidateReviewCsv(input: TestDesignCandidateReviewExportInput) {
  const generatedAt = input.generatedAt ?? new Date().toISOString();
  const candidates = [...input.candidates];
  const rows: CsvValue[][] = [CANDIDATE_EXPORT_HEADER as unknown as string[]];

  rows.push(candidateMetadataRow(input.task, input.scopeLabel, 'reportType', 'WP5_CANDIDATE_REVIEW'));
  rows.push(candidateMetadataRow(input.task, input.scopeLabel, 'generatedAt', generatedAt));
  rows.push(candidateMetadataRow(input.task, input.scopeLabel, 'candidateTotal', candidates.length));
  rows.push(candidateMetadataRow(input.task, input.scopeLabel, 'taskGeneratedCount', input.task?.generatedCount ?? ''));
  rows.push(candidateMetadataRow(input.task, input.scopeLabel, 'taskConfirmedCount', input.task?.confirmedCount ?? ''));
  rows.push(candidateMetadataRow(input.task, input.scopeLabel, 'taskPublishedCount', input.task?.publishedCount ?? ''));

  for (const [status, count] of countBy(candidates, (candidate) => candidate.status)) {
    rows.push(candidateSummaryRow(input.task, input.scopeLabel, `status:${status}`, count));
  }
  for (const [coverageType, count] of countBy(candidates, (candidate) => candidate.coverageType)) {
    rows.push(candidateSummaryRow(input.task, input.scopeLabel, `coverage:${coverageType}`, count));
  }
  for (const [priority, count] of countBy(candidates, (candidate) => candidate.priority)) {
    rows.push(candidateSummaryRow(input.task, input.scopeLabel, `priority:${priority}`, count));
  }
  for (const [flag, count] of countQualityFlags(candidates)) {
    rows.push(candidateSummaryRow(input.task, input.scopeLabel, `quality:${flag}`, count));
  }

  for (const candidate of candidates) {
    rows.push([
      'candidate',
      '',
      '',
      candidate.taskId ?? input.task?.id ?? '',
      input.task?.title ?? '',
      input.task?.status ?? '',
      candidate.projectId ?? input.task?.projectId ?? '',
      input.scopeLabel,
      candidate.id,
      candidate.requirementId ?? '',
      candidate.apiId ?? '',
      candidate.title,
      candidate.coverageType,
      candidate.priority,
      candidate.status,
      candidate.version,
      candidate.tags.join('|'),
      candidate.steps.length,
      Boolean(candidate.expectedResult?.trim()),
      Boolean(reviewNote(candidate)),
      reviewNote(candidate),
      candidate.assetCaseId ?? '',
      candidateQualityFlags(candidate).join('|'),
      candidate.errorMessage ?? '',
      candidate.createdAt ?? '',
      candidate.updatedAt ?? ''
    ]);
  }

  return toCsv(rows);
}

export function buildTestDesignPublishResultCsv(input: TestDesignPublishResultExportInput) {
  const generatedAt = input.generatedAt ?? new Date().toISOString();
  const result = input.publishResult;
  const rows: CsvValue[][] = [PUBLISH_EXPORT_HEADER as unknown as string[]];

  rows.push(publishMetadataRow(input.task, result, 'reportType', 'WP5_PUBLISH_RESULT'));
  rows.push(publishMetadataRow(input.task, result, 'generatedAt', generatedAt));
  rows.push(publishMetadataRow(input.task, result, 'total', result.total));
  rows.push(publishMetadataRow(input.task, result, 'created', result.created));
  rows.push(publishMetadataRow(input.task, result, 'skipped', result.skipped));
  rows.push(publishMetadataRow(input.task, result, 'failed', result.failed));

  for (const [recordResult, count] of countBy(result.records, (record) => record.result)) {
    rows.push(publishSummaryRow(input.task, result, `result:${recordResult}`, count));
  }
  for (const [action, count] of countBy(result.records, (record) => record.action)) {
    rows.push(publishSummaryRow(input.task, result, `action:${action}`, count));
  }

  for (const record of result.records) {
    rows.push([
      'publishRecord',
      '',
      '',
      record.taskId ?? result.taskId,
      input.task?.title ?? '',
      record.projectId ?? result.projectId ?? input.task?.projectId ?? '',
      result.dryRun,
      record.candidateId ?? '',
      record.title ?? '',
      record.requirementId ?? '',
      record.assetCaseId ?? '',
      record.action,
      record.result,
      record.errorMessage ?? '',
      record.createdAt ?? ''
    ]);
  }

  return toCsv(rows);
}

export function buildTestDesignExportFilename(kind: string, taskId?: string, generatedAt = new Date().toISOString()) {
  const safeKind = sanitizeFilePart(kind) || 'report';
  const safeTask = sanitizeFilePart(taskId || 'task');
  const safeStamp = generatedAt.replace(/[:.]/g, '-').replace(/[^0-9A-Za-zTZ-]/g, '').slice(0, 20).replace(/-+$/g, '');
  return `wp5-${safeKind}-${safeTask}-${safeStamp}.csv`;
}

export function sanitizeTestDesignExportText(value: string) {
  return value
    .replace(SENSITIVE_ASSIGNMENT_PATTERN, (_match, key: string) => `${key}=[REDACTED]`)
    .replace(BEARER_PATTERN, 'Bearer [REDACTED]')
    .replace(/\s+/g, ' ')
    .trim();
}

function candidateMetadataRow(task: TaskExportSummary | null | undefined, scopeLabel: string, metric: string, value: CsvValue): CsvValue[] {
  return ['metadata', metric, value, task?.id ?? '', task?.title ?? '', task?.status ?? '', task?.projectId ?? '', scopeLabel];
}

function candidateSummaryRow(task: TaskExportSummary | null | undefined, scopeLabel: string, metric: string, value: CsvValue): CsvValue[] {
  return ['summary', metric, value, task?.id ?? '', task?.title ?? '', task?.status ?? '', task?.projectId ?? '', scopeLabel];
}

function publishMetadataRow(
  task: TaskExportSummary | null | undefined,
  result: TestDesignPublishResult,
  metric: string,
  value: CsvValue
): CsvValue[] {
  return ['metadata', metric, value, result.taskId, task?.title ?? '', result.projectId ?? task?.projectId ?? '', result.dryRun];
}

function publishSummaryRow(
  task: TaskExportSummary | null | undefined,
  result: TestDesignPublishResult,
  metric: string,
  value: CsvValue
): CsvValue[] {
  return ['summary', metric, value, result.taskId, task?.title ?? '', result.projectId ?? task?.projectId ?? '', result.dryRun];
}

function countBy<T>(items: readonly T[], getKey: (item: T) => string | undefined) {
  const counts = new Map<string, number>();
  for (const item of items) {
    const key = getKey(item)?.trim() || 'UNKNOWN';
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return Array.from(counts.entries()).sort(([left], [right]) => left.localeCompare(right));
}

function countQualityFlags(candidates: readonly TestDesignCandidateView[]) {
  const counts = new Map<string, number>();
  for (const candidate of candidates) {
    for (const flag of candidateQualityFlags(candidate)) {
      counts.set(flag, (counts.get(flag) ?? 0) + 1);
    }
  }
  return Array.from(counts.entries()).sort(([left], [right]) => left.localeCompare(right));
}

function candidateQualityFlags(candidate: TestDesignCandidateView) {
  const flags: string[] = [];
  if (!candidate.requirementId?.trim()) flags.push('MISSING_REQUIREMENT');
  if (!candidate.title.trim()) flags.push('MISSING_TITLE');
  if (!candidate.steps.length) flags.push('NO_STEPS');
  if (!candidate.expectedResult?.trim()) flags.push('MISSING_EXPECTED_RESULT');
  if (candidate.errorMessage?.trim()) flags.push('HAS_ERROR');
  if (candidate.status === 'CONFIRMED' || candidate.status === 'FAILED') flags.push('PUBLISHABLE');
  return flags;
}

function reviewNote(candidate: TestDesignCandidateView) {
  return candidate.reviewComment ?? candidate.rejectedReason ?? candidate.ignoredReason ?? '';
}

function toCsv(rows: readonly (readonly CsvValue[])[]) {
  return rows.map((row) => row.map(csvCell).join(',')).join('\n');
}

function csvCell(value: CsvValue) {
  if (value === null || value === undefined) {
    return '';
  }
  const raw = typeof value === 'string' ? sanitizeTestDesignExportText(value) : String(value);
  if (/[",\n]/.test(raw)) {
    return `"${raw.replace(/"/g, '""')}"`;
  }
  return raw;
}

function sanitizeFilePart(value: string) {
  return value.replace(/[^0-9A-Za-z_-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
}
