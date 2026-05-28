import { requestJson, requestText, type ApiResponse, type TextResponse } from './client';

export const TEST_DESIGN_COVERAGE_TYPES = ['SMOKE', 'FUNCTIONAL', 'EXCEPTION', 'BOUNDARY', 'PERMISSION', 'REGRESSION'] as const;
export const TEST_DESIGN_CANDIDATE_STATUSES = ['GENERATED', 'EDITED', 'CONFIRMED', 'REJECTED', 'IGNORED', 'PUBLISHED', 'FAILED'] as const;

export type TestDesignCoverageType = (typeof TEST_DESIGN_COVERAGE_TYPES)[number];
export type TestDesignCandidateStatus = (typeof TEST_DESIGN_CANDIDATE_STATUSES)[number];

export interface TestDesignHealth {
  service: string;
  status: string;
  generationEnabled: boolean;
  generationMode?: string;
  promptKey?: string;
  promptVersion?: string;
  maxRequirementsPerTask?: number;
  maxCasesPerRequirement?: number;
  supportedCoverageTypes: string[];
}

export interface TestDesignTaskView {
  id: string;
  projectId?: string;
  title: string;
  status: string;
  requirementIds: string[];
  coverageTypes: string[];
  promptKey?: string;
  promptVersion?: string;
  modelInvocationId?: string;
  modelProviderName?: string;
  modelName?: string;
  totalRequirements: number;
  generatedCount: number;
  confirmedCount: number;
  publishedCount: number;
  errorMessage?: string;
  requestedBy?: string;
  idempotencyKey?: string;
  inputDigest?: string;
  contextSummary: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignStepView {
  stepOrder: number;
  action?: string;
  expectedResult?: string;
}

export interface TestDesignCandidateView {
  id: string;
  taskId?: string;
  projectId?: string;
  requirementId?: string;
  apiId?: string;
  title: string;
  description?: string;
  coverageType: string;
  priority: string;
  status: string;
  preconditions?: string;
  steps: TestDesignStepView[];
  expectedResult?: string;
  tags: string[];
  duplicateKey?: string;
  confidence?: number;
  promptKey?: string;
  promptVersion?: string;
  modelInvocationId?: string;
  modelProviderName?: string;
  modelName?: string;
  assetCaseId?: string;
  reviewComment?: string;
  rejectedReason?: string;
  ignoredReason?: string;
  errorMessage?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignPublishRecordView {
  id?: string;
  taskId?: string;
  candidateId?: string;
  title?: string;
  projectId?: string;
  requirementId?: string;
  assetCaseId?: string;
  dryRun: boolean;
  action: string;
  result: string;
  errorMessage?: string;
  publishedBy?: string;
  createdAt?: string;
}

export interface TestDesignReviewRecordView {
  id: string;
  taskId?: string;
  candidateId?: string;
  title?: string;
  projectId?: string;
  action: string;
  beforeStatus?: string;
  afterStatus?: string;
  reviewer?: string;
  hasComment: boolean;
  commentPreview?: string;
  changedFields: string[];
  versionBefore?: number;
  versionAfter?: number;
  createdAt?: string;
}

export interface TestDesignTaskDetail {
  task: TestDesignTaskView;
  candidates: TestDesignCandidateView[];
  publishRecords: TestDesignPublishRecordView[];
}

export interface TestDesignTaskList {
  items: TestDesignTaskView[];
  total: number;
  index?: number;
  size?: number;
}

export interface TestDesignCandidateList {
  items: TestDesignCandidateView[];
  total: number;
  index?: number;
  size?: number;
}

export interface TestDesignReviewRecordList {
  items: TestDesignReviewRecordView[];
  total: number;
  index?: number;
  size?: number;
}

export interface CreateTestDesignTaskPayload {
  projectId: string;
  title?: string;
  requirementIds: string[];
  coverageTypes?: string[];
  caseCountPerRequirement?: number;
  idempotencyKey?: string;
}

export interface UpdateTestDesignCandidatePayload {
  title: string;
  description?: string;
  apiId?: string;
  coverageType?: string;
  priority?: string;
  preconditions?: string;
  steps?: Array<{ action?: string; expectedResult?: string }>;
  expectedResult?: string;
  tags?: string[];
  version?: number;
}

export interface TestDesignCandidateActionPayload {
  version?: number;
  reason?: string;
  comment?: string;
}

export type TestDesignCandidateBatchActionType = 'CONFIRM' | 'REJECT' | 'IGNORE';

export interface TestDesignCandidateBatchActionPayload {
  action: TestDesignCandidateBatchActionType;
  candidates?: Array<{ id: string; version?: number }>;
  candidateIds?: string[];
  reason?: string;
  comment?: string;
}

export interface TestDesignCandidateBatchActionItem {
  candidateId: string;
  result: string;
  candidate?: TestDesignCandidateView;
  errorCode?: string;
  errorMessage?: string;
}

export interface TestDesignCandidateBatchActionResult {
  action: string;
  total: number;
  succeededCount: number;
  failedCount: number;
  items: TestDesignCandidateBatchActionItem[];
}

export interface TestDesignPublishPayload {
  candidateIds?: string[];
  dryRun?: boolean;
}

export interface TestDesignPublishResult {
  taskId: string;
  projectId?: string;
  dryRun: boolean;
  total: number;
  created: number;
  skipped: number;
  failed: number;
  createdCaseIds: string[];
  records: TestDesignPublishRecordView[];
}

export interface TestDesignTaskFilters {
  index?: number;
  size?: number;
  projectId?: string;
  status?: string;
  keyword?: string;
}

export interface TestDesignCandidateFilters {
  index?: number;
  size?: number;
  taskId?: string;
  projectId?: string;
  requirementId?: string;
  status?: string;
  coverageType?: string;
  keyword?: string;
}

export interface TestDesignReviewRecordFilters {
  index?: number;
  size?: number;
}

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function optionalString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function numberValue(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function optionalNumber(value: unknown) {
  if (value === undefined || value === null || value === '') return undefined;
  return numberValue(value, 0);
}

function stringArrayValue(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean);
  }
  if (typeof value === 'string') {
    return value.split(',').map((item) => item.trim()).filter(Boolean);
  }
  return [];
}

function recordValue(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {};
}

function listItems(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (!isRecord(value)) return [];
  if (Array.isArray(value.items)) return value.items;
  if (Array.isArray(value.content)) return value.content;
  if (Array.isArray(value.records)) return value.records;
  if (Array.isArray(value.candidates)) return value.candidates;
  if (Array.isArray(value.data)) return value.data;
  return [];
}

function pageTotal(value: unknown, fallback: number) {
  return isRecord(value) ? numberValue(value.total ?? value.totalElements ?? value.total_elements ?? value.count, fallback) : fallback;
}

function compactPayload(payload: object) {
  return Object.fromEntries(
    Object.entries(payload as Record<string, unknown>).flatMap(([key, value]) => {
      if (Array.isArray(value)) return value.length ? [[key, value]] : [];
      if (typeof value === 'string') {
        const normalized = value.trim();
        return normalized ? [[key, normalized]] : [];
      }
      return value === undefined || value === null ? [] : [[key, value]];
    })
  );
}

function queryString(filters: Record<string, unknown>) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (typeof value === 'number') params.set(key, String(value));
    if (typeof value === 'string' && value.trim()) params.set(key, value.trim());
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

export function normalizeTestDesignHealth(raw: unknown): TestDesignHealth {
  const item = isRecord(raw) ? raw : {};
  return {
    service: stringValue(item.service, 'test-design'),
    status: stringValue(item.status, 'UNKNOWN'),
    generationEnabled: Boolean(item.generationEnabled ?? item.generation_enabled),
    generationMode: optionalString(item.generationMode) ?? optionalString(item.generation_mode),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    promptVersion: optionalString(item.promptVersion) ?? optionalString(item.prompt_version),
    maxRequirementsPerTask: numberValue(item.maxRequirementsPerTask ?? item.max_requirements_per_task, 0),
    maxCasesPerRequirement: numberValue(item.maxCasesPerRequirement ?? item.max_cases_per_requirement, 0),
    supportedCoverageTypes: stringArrayValue(item.supportedCoverageTypes ?? item.supported_coverage_types)
  };
}

export function normalizeTestDesignTask(raw: unknown): TestDesignTaskView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.taskId ?? item.task_id));
  return {
    id,
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    title: stringValue(item.title, id || '未命名生成任务'),
    status: stringValue(item.status, 'UNKNOWN'),
    requirementIds: stringArrayValue(item.requirementIds ?? item.requirement_ids),
    coverageTypes: stringArrayValue(item.coverageTypes ?? item.coverage_types),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    promptVersion: optionalString(item.promptVersion) ?? optionalString(item.prompt_version),
    modelInvocationId: optionalString(item.modelInvocationId) ?? optionalString(item.model_invocation_id),
    modelProviderName: optionalString(item.modelProviderName) ?? optionalString(item.model_provider_name),
    modelName: optionalString(item.modelName) ?? optionalString(item.model_name),
    totalRequirements: numberValue(item.totalRequirements ?? item.total_requirements, 0),
    generatedCount: numberValue(item.generatedCount ?? item.generated_count, 0),
    confirmedCount: numberValue(item.confirmedCount ?? item.confirmed_count, 0),
    publishedCount: numberValue(item.publishedCount ?? item.published_count, 0),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message),
    requestedBy: optionalString(item.requestedBy) ?? optionalString(item.requested_by),
    idempotencyKey: optionalString(item.idempotencyKey) ?? optionalString(item.idempotency_key),
    inputDigest: optionalString(item.inputDigest) ?? optionalString(item.input_digest),
    contextSummary: recordValue(item.contextSummary ?? item.context_summary),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignStep(raw: unknown): TestDesignStepView {
  const item = isRecord(raw) ? raw : {};
  return {
    stepOrder: numberValue(item.stepOrder ?? item.step_order ?? item.order, 0),
    action: optionalString(item.action),
    expectedResult: optionalString(item.expectedResult) ?? optionalString(item.expected_result)
  };
}

export function normalizeTestDesignCandidate(raw: unknown): TestDesignCandidateView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.candidateId ?? item.candidate_id));
  return {
    id,
    taskId: optionalString(item.taskId) ?? optionalString(item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    requirementId: optionalString(item.requirementId) ?? optionalString(item.requirement_id),
    apiId: optionalString(item.apiId) ?? optionalString(item.api_id),
    title: stringValue(item.title, id || '未命名候选用例'),
    description: optionalString(item.description),
    coverageType: stringValue(item.coverageType ?? item.coverage_type, 'FUNCTIONAL'),
    priority: stringValue(item.priority, 'MEDIUM'),
    status: stringValue(item.status, 'GENERATED'),
    preconditions: optionalString(item.preconditions),
    steps: listItems(item.steps).map(normalizeTestDesignStep).sort((left, right) => left.stepOrder - right.stepOrder),
    expectedResult: optionalString(item.expectedResult) ?? optionalString(item.expected_result),
    tags: stringArrayValue(item.tags),
    duplicateKey: optionalString(item.duplicateKey) ?? optionalString(item.duplicate_key),
    confidence: numberValue(item.confidence, 0),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    promptVersion: optionalString(item.promptVersion) ?? optionalString(item.prompt_version),
    modelInvocationId: optionalString(item.modelInvocationId) ?? optionalString(item.model_invocation_id),
    modelProviderName: optionalString(item.modelProviderName) ?? optionalString(item.model_provider_name),
    modelName: optionalString(item.modelName) ?? optionalString(item.model_name),
    assetCaseId: optionalString(item.assetCaseId) ?? optionalString(item.asset_case_id),
    reviewComment: optionalString(item.reviewComment) ?? optionalString(item.review_comment),
    rejectedReason: optionalString(item.rejectedReason) ?? optionalString(item.rejected_reason),
    ignoredReason: optionalString(item.ignoredReason) ?? optionalString(item.ignored_reason),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message),
    confirmedBy: optionalString(item.confirmedBy) ?? optionalString(item.confirmed_by),
    confirmedAt: optionalString(item.confirmedAt) ?? optionalString(item.confirmed_at),
    version: numberValue(item.version, 0),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignPublishRecord(raw: unknown): TestDesignPublishRecordView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: optionalString(item.id),
    taskId: optionalString(item.taskId) ?? optionalString(item.task_id),
    candidateId: optionalString(item.candidateId) ?? optionalString(item.candidate_id),
    title: optionalString(item.title),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    requirementId: optionalString(item.requirementId) ?? optionalString(item.requirement_id),
    assetCaseId: optionalString(item.assetCaseId) ?? optionalString(item.asset_case_id),
    dryRun: Boolean(item.dryRun ?? item.dry_run),
    action: stringValue(item.action, 'UNKNOWN'),
    result: stringValue(item.result, 'UNKNOWN'),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message),
    publishedBy: optionalString(item.publishedBy) ?? optionalString(item.published_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignReviewRecord(raw: unknown): TestDesignReviewRecordView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    taskId: optionalString(item.taskId) ?? optionalString(item.task_id),
    candidateId: optionalString(item.candidateId) ?? optionalString(item.candidate_id),
    title: optionalString(item.title),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    action: stringValue(item.action, 'UNKNOWN'),
    beforeStatus: optionalString(item.beforeStatus) ?? optionalString(item.before_status),
    afterStatus: optionalString(item.afterStatus) ?? optionalString(item.after_status),
    reviewer: optionalString(item.reviewer),
    hasComment: Boolean(item.hasComment ?? item.has_comment),
    commentPreview: optionalString(item.commentPreview) ?? optionalString(item.comment_preview),
    changedFields: stringArrayValue(item.changedFields ?? item.changed_fields),
    versionBefore: optionalNumber(item.versionBefore ?? item.version_before),
    versionAfter: optionalNumber(item.versionAfter ?? item.version_after),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignCandidateBatchActionItem(raw: unknown): TestDesignCandidateBatchActionItem {
  const item = isRecord(raw) ? raw : {};
  return {
    candidateId: stringValue(item.candidateId ?? item.candidate_id),
    result: stringValue(item.result, 'UNKNOWN'),
    candidate: item.candidate ? normalizeTestDesignCandidate(item.candidate) : undefined,
    errorCode: optionalString(item.errorCode) ?? optionalString(item.error_code),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message)
  };
}

export function normalizeTestDesignCandidateBatchActionResult(raw: unknown): TestDesignCandidateBatchActionResult {
  const item = isRecord(raw) ? raw : {};
  return {
    action: stringValue(item.action, 'UNKNOWN'),
    total: numberValue(item.total, 0),
    succeededCount: numberValue(item.succeededCount ?? item.succeeded_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    items: listItems(item.items).map(normalizeTestDesignCandidateBatchActionItem)
  };
}

export function normalizeTestDesignTaskDetail(raw: unknown): TestDesignTaskDetail {
  const item = isRecord(raw) ? raw : {};
  return {
    task: normalizeTestDesignTask(item.task),
    candidates: listItems(item.candidates).map(normalizeTestDesignCandidate),
    publishRecords: listItems(item.publishRecords ?? item.publish_records).map(normalizeTestDesignPublishRecord)
  };
}

export function normalizeTestDesignTaskList(raw: unknown): TestDesignTaskList {
  const items = listItems(raw).map(normalizeTestDesignTask);
  return {
    items,
    total: pageTotal(raw, items.length),
    index: isRecord(raw) ? numberValue(raw.index, 0) : undefined,
    size: isRecord(raw) ? numberValue(raw.size, items.length) : undefined
  };
}

export function normalizeTestDesignCandidateList(raw: unknown): TestDesignCandidateList {
  const items = listItems(raw).map(normalizeTestDesignCandidate);
  return {
    items,
    total: pageTotal(raw, items.length),
    index: isRecord(raw) ? numberValue(raw.index, 0) : undefined,
    size: isRecord(raw) ? numberValue(raw.size, items.length) : undefined
  };
}

export function normalizeTestDesignReviewRecordList(raw: unknown): TestDesignReviewRecordList {
  const items = listItems(raw).map(normalizeTestDesignReviewRecord);
  return {
    items,
    total: pageTotal(raw, items.length),
    index: isRecord(raw) ? numberValue(raw.index, 0) : undefined,
    size: isRecord(raw) ? numberValue(raw.size, items.length) : undefined
  };
}

export function normalizeTestDesignPublishResult(raw: unknown): TestDesignPublishResult {
  const item = isRecord(raw) ? raw : {};
  return {
    taskId: stringValue(item.taskId ?? item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    dryRun: Boolean(item.dryRun ?? item.dry_run),
    total: numberValue(item.total, 0),
    created: numberValue(item.created, 0),
    skipped: numberValue(item.skipped, 0),
    failed: numberValue(item.failed, 0),
    createdCaseIds: stringArrayValue(item.createdCaseIds ?? item.created_case_ids),
    records: listItems(item.records).map(normalizeTestDesignPublishRecord)
  };
}

export async function fetchTestDesignHealth(): Promise<ApiResponse<TestDesignHealth>> {
  const response = await requestJson<unknown>('/api/v1/test-design/health');
  return { ...response, data: normalizeTestDesignHealth(response.data) };
}

export async function fetchTestDesignTasks(filters: TestDesignTaskFilters = {}): Promise<ApiResponse<TestDesignTaskList>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks${queryString(filters as Record<string, unknown>)}`);
  return { ...response, data: normalizeTestDesignTaskList(response.data) };
}

export async function createTestDesignTask(payload: CreateTestDesignTaskPayload): Promise<ApiResponse<TestDesignTaskDetail>> {
  const response = await requestJson<unknown>('/api/v1/test-design/tasks', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignTaskDetail(response.data) };
}

export async function fetchTestDesignTask(taskId: string): Promise<ApiResponse<TestDesignTaskDetail>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}`);
  return { ...response, data: normalizeTestDesignTaskDetail(response.data) };
}

export async function fetchTestDesignTaskSummary(taskId: string): Promise<ApiResponse<TestDesignTaskView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/summary`);
  return { ...response, data: normalizeTestDesignTask(response.data) };
}

export async function retryTestDesignTask(taskId: string): Promise<ApiResponse<TestDesignTaskDetail>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/retry`, {
    method: 'POST'
  });
  return { ...response, data: normalizeTestDesignTaskDetail(response.data) };
}

export async function cancelTestDesignTask(taskId: string): Promise<ApiResponse<TestDesignTaskDetail>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/cancel`, {
    method: 'POST'
  });
  return { ...response, data: normalizeTestDesignTaskDetail(response.data) };
}

export async function fetchTestDesignCandidates(filters: TestDesignCandidateFilters = {}): Promise<ApiResponse<TestDesignCandidateList>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/candidates${queryString(filters as Record<string, unknown>)}`);
  return { ...response, data: normalizeTestDesignCandidateList(response.data) };
}

export function testDesignCandidateExportPath(filters: TestDesignCandidateFilters = {}) {
  const exportFilters: TestDesignCandidateFilters = { ...filters };
  delete exportFilters.index;
  delete exportFilters.size;
  return `/api/v1/test-design/candidates/export${queryString(exportFilters as Record<string, unknown>)}`;
}

export async function exportTestDesignCandidatesCsv(filters: TestDesignCandidateFilters = {}): Promise<TextResponse> {
  return requestText(testDesignCandidateExportPath(filters));
}

export async function fetchTaskTestDesignCandidates(
  taskId: string,
  filters: TestDesignCandidateFilters = {}
): Promise<ApiResponse<TestDesignCandidateList>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/candidates${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignCandidateList(response.data) };
}

export async function updateTestDesignCandidate(
  candidateId: string,
  payload: UpdateTestDesignCandidatePayload
): Promise<ApiResponse<TestDesignCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/candidates/${encodeURIComponent(candidateId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignCandidate(response.data) };
}

export async function confirmTestDesignCandidate(
  candidateId: string,
  payload: TestDesignCandidateActionPayload = {}
): Promise<ApiResponse<TestDesignCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/candidates/${encodeURIComponent(candidateId)}/confirm`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignCandidate(response.data) };
}

export async function rejectTestDesignCandidate(
  candidateId: string,
  payload: TestDesignCandidateActionPayload
): Promise<ApiResponse<TestDesignCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/candidates/${encodeURIComponent(candidateId)}/reject`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignCandidate(response.data) };
}

export async function ignoreTestDesignCandidate(
  candidateId: string,
  payload: TestDesignCandidateActionPayload
): Promise<ApiResponse<TestDesignCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/candidates/${encodeURIComponent(candidateId)}/ignore`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignCandidate(response.data) };
}

export async function batchActionTestDesignCandidates(
  payload: TestDesignCandidateBatchActionPayload
): Promise<ApiResponse<TestDesignCandidateBatchActionResult>> {
  const response = await requestJson<unknown>('/api/v1/test-design/candidates/batch-action', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignCandidateBatchActionResult(response.data) };
}

export async function publishTestDesignDryRun(
  taskId: string,
  payload: TestDesignPublishPayload = {}
): Promise<ApiResponse<TestDesignPublishResult>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/publish-dry-run`, {
    method: 'POST',
    body: JSON.stringify(compactPayload({ ...payload, dryRun: true }))
  });
  return { ...response, data: normalizeTestDesignPublishResult(response.data) };
}

export async function publishTestDesignTask(
  taskId: string,
  payload: TestDesignPublishPayload = {}
): Promise<ApiResponse<TestDesignPublishResult>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/publish`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignPublishResult(response.data) };
}

export async function fetchTestDesignPublishRecords(taskId: string): Promise<ApiResponse<TestDesignPublishRecordView[]>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/publish-records`);
  return { ...response, data: listItems(response.data).map(normalizeTestDesignPublishRecord) };
}

export async function fetchTestDesignReviewRecords(
  taskId: string,
  filters: TestDesignReviewRecordFilters = {}
): Promise<ApiResponse<TestDesignReviewRecordList>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/review-records${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignReviewRecordList(response.data) };
}

export function testDesignReviewRecordExportPath(taskId: string) {
  return `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/review-records/export`;
}

export async function exportTestDesignReviewRecordsCsv(taskId: string): Promise<TextResponse> {
  return requestText(testDesignReviewRecordExportPath(taskId));
}

export function testDesignErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
