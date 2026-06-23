import { requestBinary, requestJson, type ApiResponse, type BinaryResponse } from './client';

const REPORTS_BASE = '/api/v1/reports';

export interface ReportingHealth {
  service: string;
  status: string;
  enabled: boolean;
  generateEnabled: boolean;
  diagnosisEnabled: boolean;
  defectDraftEnabled: boolean;
  exportEnabled: boolean;
  maxEvidenceItems: number;
  maxDiagnosisContextChars: number;
  maxExportMarkdownChars: number;
  schemaVersion: string;
  fieldSetVersion: string;
  policy: Record<string, unknown>;
}

export interface ReportSummary {
  id: string;
  projectId: string;
  executionRunId: string;
  requestKey?: string;
  status: string;
  schemaVersion: string;
  sourceRunDigest?: string;
  summary: Record<string, unknown>;
  idempotentReplay: boolean;
  generatedBy?: string;
  generatedAt?: string;
  failedCode?: string;
  failureSummary?: string;
  traceId?: string;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReportEvidenceManifest {
  id: string;
  reportId: string;
  sourceWp: string;
  sourceType: string;
  sourceRefDigest?: string;
  schemaVersion: string;
  summaryKeys: string[];
  manifestDigest?: string;
  redactionFlags: Record<string, unknown>;
  createdAt?: string;
}

export interface ReportDiagnosis {
  id: string;
  reportId: string;
  status: string;
  classification: Record<string, unknown>;
  rootCauseCandidates: unknown[];
  confidence: number;
  manualReviewRequired: boolean;
  modelInvocationDigest?: string;
  errorCode?: string;
  aiDiagnosisReady: boolean;
  modelInvoked: boolean;
  classificationOnly: boolean;
  redactionPolicy: Record<string, unknown>;
  diagnosisContext: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReportDefectDraft {
  id: string;
  reportId: string;
  diagnosisId?: string;
  status: string;
  title: string;
  reproductionSummary: string;
  impactSummary: string;
  prioritySuggestion: string;
  evidenceRefs: string[];
  payloadPreview: Record<string, unknown>;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReportExport {
  id: string;
  reportId: string;
  exportType: string;
  status: string;
  schemaVersion: string;
  fieldSetVersion: string;
  contentDigest?: string;
  aggregateOnly: boolean;
  exportedBy?: string;
  exportedAt?: string;
  blockReason?: string;
  redactionPolicy: Record<string, unknown>;
  manifest: Record<string, unknown>;
  downloadReady: boolean;
  downloadFileName?: string;
  downloadContentType?: string;
  content: unknown;
  createdAt?: string;
}

export type ReportExportType = 'JSON' | 'MARKDOWN' | 'HTML' | 'PDF' | 'WORD' | 'EXCEL';

export interface ReportDetail extends ReportSummary {
  redactionPolicy: Record<string, unknown>;
  evidenceManifests: ReportEvidenceManifest[];
  latestDiagnosis?: ReportDiagnosis;
  defectDrafts: ReportDefectDraft[];
}

export interface ReportCompareFieldDiff {
  field: string;
  baselineValue: unknown;
  currentValue: unknown;
}

export interface ReportCompareEvidenceDiff {
  changed: boolean;
  baselineCount: number;
  currentCount: number;
  addedManifestKeys: string[];
  removedManifestKeys: string[];
  baselineSourceWpCounts: Record<string, number>;
  currentSourceWpCounts: Record<string, number>;
  baselineSourceTypeCounts: Record<string, number>;
  currentSourceTypeCounts: Record<string, number>;
}

export interface ReportCompareDefectDraftDiff {
  changed: boolean;
  baselineCount: number;
  currentCount: number;
  baselineStatusCounts: Record<string, number>;
  currentStatusCounts: Record<string, number>;
}

export interface ReportCompare {
  reportId: string;
  baselineReportId: string;
  projectId: string;
  unchanged: boolean;
  changedFields: string[];
  metadataDiffs: ReportCompareFieldDiff[];
  summaryDiffs: ReportCompareFieldDiff[];
  diagnosisDiffs: ReportCompareFieldDiff[];
  evidenceDiff: ReportCompareEvidenceDiff;
  defectDraftDiff: ReportCompareDefectDraftDiff;
}

export interface ReportList {
  items: ReportSummary[];
  index: number;
  size: number;
  total: number;
}

export interface ReportFilters {
  projectId?: string;
  executionRunId?: string;
  status?: string;
  index?: number;
  size?: number;
}

export interface GenerateReportPayload {
  projectId: string;
  executionRunId: string;
  requestKey?: string;
  reason?: string;
}

export interface BatchReportExportPayload {
  reportIds: string[];
  exportType: ReportExportType;
}

export async function fetchReportingHealth(): Promise<ApiResponse<ReportingHealth>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/health`);
  return { ...response, data: normalizeReportingHealth(response.data) };
}

export async function fetchReports(filters: ReportFilters = {}): Promise<ApiResponse<ReportList>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}${queryString(filters)}`);
  return { ...response, data: normalizeReportList(response.data) };
}

export async function fetchReport(id: string): Promise<ApiResponse<ReportDetail>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeReportDetail(response.data) };
}

export async function compareReport(id: string, baselineReportId: string): Promise<ApiResponse<ReportCompare>> {
  const response = await requestJson<unknown>(
    `${REPORTS_BASE}/${encodeURIComponent(id)}/compare?baselineReportId=${encodeURIComponent(baselineReportId)}`
  );
  return { ...response, data: normalizeReportCompare(response.data) };
}

export async function generateReport(payload: GenerateReportPayload): Promise<ApiResponse<ReportDetail>> {
  const response = await requestJson<unknown>(REPORTS_BASE, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeReportDetail(response.data) };
}

export async function retryReport(id: string): Promise<ApiResponse<ReportDetail>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/${encodeURIComponent(id)}/retry`, {
    method: 'POST'
  });
  return { ...response, data: normalizeReportDetail(response.data) };
}

export async function archiveReport(id: string): Promise<ApiResponse<ReportDetail>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/${encodeURIComponent(id)}/archive`, {
    method: 'POST'
  });
  return { ...response, data: normalizeReportDetail(response.data) };
}

export async function diagnoseReport(id: string): Promise<ApiResponse<ReportDiagnosis>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/${encodeURIComponent(id)}/diagnoses`, {
    method: 'POST'
  });
  return { ...response, data: normalizeReportDiagnosis(response.data) };
}

export async function fetchLatestReportDiagnosis(id: string): Promise<ApiResponse<ReportDiagnosis>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/${encodeURIComponent(id)}/diagnoses/latest`);
  return { ...response, data: normalizeReportDiagnosis(response.data) };
}

export async function createDefectDraft(id: string): Promise<ApiResponse<ReportDefectDraft>> {
  const response = await requestJson<unknown>(`${REPORTS_BASE}/${encodeURIComponent(id)}/defect-drafts`, {
    method: 'POST'
  });
  return { ...response, data: normalizeDefectDraft(response.data) };
}

export async function reviewDefectDraft(
  reportId: string,
  draftId: string,
  status: 'DRAFT' | 'REVIEWED' | 'DISMISSED'
): Promise<ApiResponse<ReportDefectDraft>> {
  const response = await requestJson<unknown>(
    `${REPORTS_BASE}/${encodeURIComponent(reportId)}/defect-drafts/${encodeURIComponent(draftId)}`,
    {
      method: 'PATCH',
      body: JSON.stringify({ status })
    }
  );
  return { ...response, data: normalizeDefectDraft(response.data) };
}

export async function exportReport(
  id: string,
  exportType: ReportExportType = 'JSON'
): Promise<ApiResponse<ReportExport>> {
  const response = await requestJson<unknown>(
    `${REPORTS_BASE}/${encodeURIComponent(id)}/export?exportType=${encodeURIComponent(exportType)}`
  );
  return { ...response, data: normalizeReportExport(response.data) };
}

export async function downloadReportExport(reportId: string, exportId: string): Promise<BinaryResponse> {
  return requestBinary(
    `${REPORTS_BASE}/${encodeURIComponent(reportId)}/exports/${encodeURIComponent(exportId)}/download`
  );
}

export async function batchExportReports(payload: BatchReportExportPayload): Promise<BinaryResponse> {
  return requestBinary(`${REPORTS_BASE}/exports/batch`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });
}

export function normalizeReportingHealth(input: unknown): ReportingHealth {
  const value = objectValue(input);
  return {
    service: stringValue(read(value, 'service'), 'reporting'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    enabled: booleanValue(read(value, 'enabled'), false),
    generateEnabled: booleanValue(read(value, 'generateEnabled', 'generate_enabled'), false),
    diagnosisEnabled: booleanValue(read(value, 'diagnosisEnabled', 'diagnosis_enabled'), false),
    defectDraftEnabled: booleanValue(read(value, 'defectDraftEnabled', 'defect_draft_enabled'), false),
    exportEnabled: booleanValue(read(value, 'exportEnabled', 'export_enabled'), false),
    maxEvidenceItems: numberValue(read(value, 'maxEvidenceItems', 'max_evidence_items'), 0),
    maxDiagnosisContextChars: numberValue(read(value, 'maxDiagnosisContextChars', 'max_diagnosis_context_chars'), 0),
    maxExportMarkdownChars: numberValue(read(value, 'maxExportMarkdownChars', 'max_export_markdown_chars'), 0),
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version')),
    fieldSetVersion: stringValue(read(value, 'fieldSetVersion', 'field_set_version')),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeReportList(input: unknown): ReportList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeReportSummary),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeReportSummary(input: unknown): ReportSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    executionRunId: stringValue(read(value, 'executionRunId', 'execution_run_id')),
    requestKey: optionalString(read(value, 'requestKey', 'request_key')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version')),
    sourceRunDigest: optionalString(read(value, 'sourceRunDigest', 'source_run_digest')),
    summary: objectValue(read(value, 'summary')),
    idempotentReplay: booleanValue(read(value, 'idempotentReplay', 'idempotent_replay'), false),
    generatedBy: optionalString(read(value, 'generatedBy', 'generated_by')),
    generatedAt: optionalString(read(value, 'generatedAt', 'generated_at')),
    failedCode: optionalString(read(value, 'failedCode', 'failed_code')),
    failureSummary: optionalString(read(value, 'failureSummary', 'failure_summary')),
    traceId: optionalString(read(value, 'traceId', 'trace_id')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeReportDetail(input: unknown): ReportDetail {
  const value = objectValue(input);
  return {
    ...normalizeReportSummary(value),
    redactionPolicy: objectValue(read(value, 'redactionPolicy', 'redaction_policy')),
    evidenceManifests: arrayValue(read(value, 'evidenceManifests', 'evidence_manifests')).map(normalizeEvidenceManifest),
    latestDiagnosis: normalizeOptionalDiagnosis(read(value, 'latestDiagnosis', 'latest_diagnosis')),
    defectDrafts: arrayValue(read(value, 'defectDrafts', 'defect_drafts')).map(normalizeDefectDraft)
  };
}

export function normalizeEvidenceManifest(input: unknown): ReportEvidenceManifest {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    reportId: stringValue(read(value, 'reportId', 'report_id')),
    sourceWp: stringValue(read(value, 'sourceWp', 'source_wp')),
    sourceType: stringValue(read(value, 'sourceType', 'source_type')),
    sourceRefDigest: optionalString(read(value, 'sourceRefDigest', 'source_ref_digest')),
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version')),
    summaryKeys: stringArray(read(value, 'summaryKeys', 'summary_keys')),
    manifestDigest: optionalString(read(value, 'manifestDigest', 'manifest_digest')),
    redactionFlags: objectValue(read(value, 'redactionFlags', 'redaction_flags')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at'))
  };
}

export function normalizeReportDiagnosis(input: unknown): ReportDiagnosis {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    reportId: stringValue(read(value, 'reportId', 'report_id')),
    status: stringValue(read(value, 'status'), 'NOT_REQUESTED'),
    classification: objectValue(read(value, 'classification')),
    rootCauseCandidates: arrayValue(read(value, 'rootCauseCandidates', 'root_cause_candidates')),
    confidence: numberValue(read(value, 'confidence'), 0),
    manualReviewRequired: booleanValue(read(value, 'manualReviewRequired', 'manual_review_required'), true),
    modelInvocationDigest: optionalString(read(value, 'modelInvocationDigest', 'model_invocation_digest')),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    aiDiagnosisReady: booleanValue(read(value, 'aiDiagnosisReady', 'ai_diagnosis_ready'), false),
    modelInvoked: booleanValue(read(value, 'modelInvoked', 'model_invoked'), false),
    classificationOnly: booleanValue(read(value, 'classificationOnly', 'classification_only'), false),
    redactionPolicy: objectValue(read(value, 'redactionPolicy', 'redaction_policy')),
    diagnosisContext: objectValue(read(value, 'diagnosisContext', 'diagnosis_context')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeDefectDraft(input: unknown): ReportDefectDraft {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    reportId: stringValue(read(value, 'reportId', 'report_id')),
    diagnosisId: optionalString(read(value, 'diagnosisId', 'diagnosis_id')),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    title: stringValue(read(value, 'title')),
    reproductionSummary: stringValue(read(value, 'reproductionSummary', 'reproduction_summary')),
    impactSummary: stringValue(read(value, 'impactSummary', 'impact_summary')),
    prioritySuggestion: stringValue(read(value, 'prioritySuggestion', 'priority_suggestion')),
    evidenceRefs: stringArray(read(value, 'evidenceRefs', 'evidence_refs')),
    payloadPreview: objectValue(read(value, 'payloadPreview', 'payload_preview')),
    createdBy: optionalString(read(value, 'createdBy', 'created_by')),
    updatedBy: optionalString(read(value, 'updatedBy', 'updated_by')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeReportExport(input: unknown): ReportExport {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    reportId: stringValue(read(value, 'reportId', 'report_id')),
    exportType: stringValue(read(value, 'exportType', 'export_type'), 'JSON'),
    status: stringValue(read(value, 'status'), 'CREATED'),
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version')),
    fieldSetVersion: stringValue(read(value, 'fieldSetVersion', 'field_set_version')),
    contentDigest: optionalString(read(value, 'contentDigest', 'content_digest')),
    aggregateOnly: booleanValue(read(value, 'aggregateOnly', 'aggregate_only'), true),
    exportedBy: optionalString(read(value, 'exportedBy', 'exported_by')),
    exportedAt: optionalString(read(value, 'exportedAt', 'exported_at')),
    blockReason: optionalString(read(value, 'blockReason', 'block_reason')),
    redactionPolicy: objectValue(read(value, 'redactionPolicy', 'redaction_policy')),
    manifest: objectValue(read(value, 'manifest')),
    downloadReady: booleanValue(read(value, 'downloadReady', 'download_ready'), false),
    downloadFileName: optionalString(read(value, 'downloadFileName', 'download_file_name')),
    downloadContentType: optionalString(read(value, 'downloadContentType', 'download_content_type')),
    content: read(value, 'content'),
    createdAt: optionalString(read(value, 'createdAt', 'created_at'))
  };
}

export function normalizeReportCompare(input: unknown): ReportCompare {
  const value = objectValue(input);
  return {
    reportId: stringValue(read(value, 'reportId', 'report_id')),
    baselineReportId: stringValue(read(value, 'baselineReportId', 'baseline_report_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    unchanged: booleanValue(read(value, 'unchanged'), true),
    changedFields: stringArray(read(value, 'changedFields', 'changed_fields')),
    metadataDiffs: arrayValue(read(value, 'metadataDiffs', 'metadata_diffs')).map(normalizeReportCompareFieldDiff),
    summaryDiffs: arrayValue(read(value, 'summaryDiffs', 'summary_diffs')).map(normalizeReportCompareFieldDiff),
    diagnosisDiffs: arrayValue(read(value, 'diagnosisDiffs', 'diagnosis_diffs')).map(normalizeReportCompareFieldDiff),
    evidenceDiff: normalizeReportCompareEvidenceDiff(read(value, 'evidenceDiff', 'evidence_diff')),
    defectDraftDiff: normalizeReportCompareDefectDraftDiff(read(value, 'defectDraftDiff', 'defect_draft_diff'))
  };
}

function normalizeOptionalDiagnosis(input: unknown): ReportDiagnosis | undefined {
  if (!input || Object.keys(objectValue(input)).length === 0) {
    return undefined;
  }
  return normalizeReportDiagnosis(input);
}

function normalizeReportCompareFieldDiff(input: unknown): ReportCompareFieldDiff {
  const value = objectValue(input);
  return {
    field: stringValue(read(value, 'field')),
    baselineValue: read(value, 'baselineValue', 'baseline_value'),
    currentValue: read(value, 'currentValue', 'current_value')
  };
}

function normalizeReportCompareEvidenceDiff(input: unknown): ReportCompareEvidenceDiff {
  const value = objectValue(input);
  return {
    changed: booleanValue(read(value, 'changed'), false),
    baselineCount: numberValue(read(value, 'baselineCount', 'baseline_count'), 0),
    currentCount: numberValue(read(value, 'currentCount', 'current_count'), 0),
    addedManifestKeys: stringArray(read(value, 'addedManifestKeys', 'added_manifest_keys')),
    removedManifestKeys: stringArray(read(value, 'removedManifestKeys', 'removed_manifest_keys')),
    baselineSourceWpCounts: numberRecord(read(value, 'baselineSourceWpCounts', 'baseline_source_wp_counts')),
    currentSourceWpCounts: numberRecord(read(value, 'currentSourceWpCounts', 'current_source_wp_counts')),
    baselineSourceTypeCounts: numberRecord(read(value, 'baselineSourceTypeCounts', 'baseline_source_type_counts')),
    currentSourceTypeCounts: numberRecord(read(value, 'currentSourceTypeCounts', 'current_source_type_counts'))
  };
}

function normalizeReportCompareDefectDraftDiff(input: unknown): ReportCompareDefectDraftDiff {
  const value = objectValue(input);
  return {
    changed: booleanValue(read(value, 'changed'), false),
    baselineCount: numberValue(read(value, 'baselineCount', 'baseline_count'), 0),
    currentCount: numberValue(read(value, 'currentCount', 'current_count'), 0),
    baselineStatusCounts: numberRecord(read(value, 'baselineStatusCounts', 'baseline_status_counts')),
    currentStatusCounts: numberRecord(read(value, 'currentStatusCounts', 'current_status_counts'))
  };
}

function queryString(filters: object) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

function objectValue(input: unknown): Record<string, unknown> {
  return input && typeof input === 'object' && !Array.isArray(input) ? input as Record<string, unknown> : {};
}

function arrayValue(input: unknown): unknown[] {
  return Array.isArray(input) ? input : [];
}

function read(value: Record<string, unknown>, primary: string, fallback?: string) {
  return value[primary] ?? (fallback ? value[fallback] : undefined);
}

function stringValue(input: unknown, fallback = '') {
  return typeof input === 'string' ? input : input == null ? fallback : String(input);
}

function optionalString(input: unknown) {
  const value = stringValue(input).trim();
  return value ? value : undefined;
}

function stringArray(input: unknown) {
  return Array.isArray(input) ? input.map((value) => stringValue(value)).filter(Boolean) : [];
}

function numberValue(input: unknown, fallback = 0) {
  const value = typeof input === 'number' ? input : Number(input);
  return Number.isFinite(value) ? value : fallback;
}

function booleanValue(input: unknown, fallback: boolean) {
  if (typeof input === 'boolean') return input;
  if (input === 'true') return true;
  if (input === 'false') return false;
  return fallback;
}

function numberRecord(input: unknown) {
  const value = objectValue(input);
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, numberValue(item, 0)]));
}
