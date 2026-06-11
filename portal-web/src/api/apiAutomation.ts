import { requestJson, type ApiResponse } from './client';

const API_AUTOMATION_BASE = '/api/v1/api-automation';

export interface ApiAutomationHealth {
  service: string;
  status: string;
  supportedOpenApiVersions: string[];
  specMaxBytes: number;
  endpointMaxCount: number;
  runnerEnabled: boolean;
  runnerTimeoutSeconds: number;
  runnerMaxCases: number;
  promptKey?: string;
  modelFallbackEnabled: boolean;
  policy: Record<string, unknown>;
}

export interface ApiAutomationSpec {
  id: string;
  projectId: string;
  sourceType: string;
  sourceRef?: string;
  name: string;
  versionLabel?: string;
  specDigest?: string;
  contentSizeBytes: number;
  status: string;
  parserVersion?: string;
  endpointCount: number;
  parseErrorSummary?: string;
  parsedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiAutomationEndpointSnapshot {
  id: string;
  serviceName?: string;
  operationId?: string;
  httpMethod: string;
  path: string;
  summary?: string;
  tags?: string;
  parameterCount: number;
  requestBodyPresent: boolean;
  responseStatuses?: string;
  schemaDigest?: string;
  diffStatus: string;
  assetApiId?: string;
  diffSummary: Record<string, unknown>;
  lastDiffAt?: string;
  syncedAt?: string;
  syncErrorSummary?: string;
}

export interface ApiAutomationSpecDetail {
  spec: ApiAutomationSpec;
  parseSummary: Record<string, unknown>;
  endpoints: ApiAutomationEndpointSnapshot[];
}

export interface ApiAutomationSpecList {
  items: ApiAutomationSpec[];
  index: number;
  size: number;
  total: number;
}

export interface ApiAutomationSpecPayload {
  projectId: string;
  sourceType: 'TEXT' | 'UPLOAD' | 'URL';
  name: string;
  versionLabel?: string;
  sourceRef?: string;
  content: string;
}

export interface ApiAutomationSpecFilters {
  projectId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export interface ApiAutomationDiffResponse {
  specId: string;
  counts: Record<string, number>;
  endpoints: ApiAutomationEndpointSnapshot[];
}

export interface ApiAutomationSyncPayload {
  endpointIds?: string[];
  includeChanged?: boolean;
}

export interface ApiAutomationSyncItem {
  endpointId: string;
  assetApiId?: string;
  httpMethod: string;
  path: string;
  beforeStatus: string;
  result: string;
  message?: string;
}

export interface ApiAutomationSyncResponse {
  specId: string;
  counts: Record<string, number>;
  items: ApiAutomationSyncItem[];
  endpoints: ApiAutomationEndpointSnapshot[];
}

export interface ApiAutomationGenerationTaskPayload {
  projectId: string;
  specId: string;
  assetApiIds?: string[];
  assetTestCaseIds?: string[];
  coverageTypes?: string[];
  generationMode?: string;
  caseCountPerApi?: number;
  requestKey?: string;
}

export interface ApiAutomationCase {
  id: string;
  endpointSnapshotId: string;
  assetApiId?: string;
  assetTestCaseId?: string;
  title: string;
  httpMethod: string;
  path: string;
  coverageType: string;
  expectedStatus: number;
  assertionSummary: Record<string, unknown>;
  requestTemplate: Record<string, unknown>;
  source: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiAutomationGenerationTask {
  id: string;
  projectId: string;
  specId: string;
  requestKey?: string;
  requestDigest?: string;
  generationMode: string;
  coverageTypes: string[];
  status: string;
  promptKey?: string;
  promptVersion?: string;
  modelInvocationId?: string;
  fallbackUsed: boolean;
  apiCount: number;
  caseCount: number;
  inputSummary: Record<string, unknown>;
  errorSummary?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiAutomationGenerationTaskDetail {
  task: ApiAutomationGenerationTask;
  cases: ApiAutomationCase[];
  scriptBundles: ApiAutomationScriptBundle[];
}

export interface ApiAutomationScriptBundle {
  id: string;
  projectId: string;
  taskId: string;
  status: string;
  bundleDigest?: string;
  fileCount: number;
  fileTreeSummary: Record<string, unknown>;
  dependencySummary: Record<string, unknown>;
  staticCheckStatus: string;
  staticCheckSummary: Record<string, unknown>;
  reviewNote?: string;
  submittedBy?: string;
  approvedBy?: string;
  submittedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiAutomationScriptBundleReviewPayload {
  note?: string;
}

export async function fetchApiAutomationHealth(): Promise<ApiResponse<ApiAutomationHealth>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/health`);
  return { ...response, data: normalizeApiAutomationHealth(response.data) };
}

export async function createApiAutomationSpec(
  payload: ApiAutomationSpecPayload
): Promise<ApiResponse<ApiAutomationSpecDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationSpecDetail(response.data) };
}

export async function fetchApiAutomationSpecs(
  filters: ApiAutomationSpecFilters = {}
): Promise<ApiResponse<ApiAutomationSpecList>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs${queryString(filters)}`);
  return { ...response, data: normalizeApiAutomationSpecList(response.data) };
}

export async function fetchApiAutomationSpec(id: string): Promise<ApiResponse<ApiAutomationSpecDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeApiAutomationSpecDetail(response.data) };
}

export async function parseApiAutomationSpec(id: string): Promise<ApiResponse<ApiAutomationSpecDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs/${encodeURIComponent(id)}/parse`, {
    method: 'POST'
  });
  return { ...response, data: normalizeApiAutomationSpecDetail(response.data) };
}

export async function fetchApiAutomationDiff(id: string): Promise<ApiResponse<ApiAutomationDiffResponse>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs/${encodeURIComponent(id)}/diff`);
  return { ...response, data: normalizeApiAutomationDiffResponse(response.data) };
}

export async function syncApiAutomationSpec(
  id: string,
  payload: ApiAutomationSyncPayload = {}
): Promise<ApiResponse<ApiAutomationSyncResponse>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs/${encodeURIComponent(id)}/sync`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationSyncResponse(response.data) };
}

export async function createApiAutomationGenerationTask(
  payload: ApiAutomationGenerationTaskPayload
): Promise<ApiResponse<ApiAutomationGenerationTaskDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/generation-tasks`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationGenerationTaskDetail(response.data) };
}

export async function fetchApiAutomationGenerationTask(id: string): Promise<ApiResponse<ApiAutomationGenerationTaskDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/generation-tasks/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeApiAutomationGenerationTaskDetail(response.data) };
}

export async function generateApiAutomationScriptBundle(taskId: string): Promise<ApiResponse<ApiAutomationScriptBundle>> {
  const response = await requestJson<unknown>(
    `${API_AUTOMATION_BASE}/generation-tasks/${encodeURIComponent(taskId)}/script-bundles`,
    { method: 'POST' }
  );
  return { ...response, data: normalizeApiAutomationScriptBundle(response.data) };
}

export async function submitApiAutomationScriptBundleReview(
  id: string,
  payload: ApiAutomationScriptBundleReviewPayload = {}
): Promise<ApiResponse<ApiAutomationScriptBundle>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/script-bundles/${encodeURIComponent(id)}/submit-review`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationScriptBundle(response.data) };
}

export async function approveApiAutomationScriptBundle(
  id: string,
  payload: ApiAutomationScriptBundleReviewPayload = {}
): Promise<ApiResponse<ApiAutomationScriptBundle>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/script-bundles/${encodeURIComponent(id)}/approve`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationScriptBundle(response.data) };
}

export async function rejectApiAutomationScriptBundle(
  id: string,
  payload: ApiAutomationScriptBundleReviewPayload
): Promise<ApiResponse<ApiAutomationScriptBundle>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/script-bundles/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationScriptBundle(response.data) };
}

export function normalizeApiAutomationHealth(input: unknown): ApiAutomationHealth {
  const value = objectValue(input);
  return {
    service: stringValue(read(value, 'service'), 'api-automation'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    supportedOpenApiVersions: stringArray(read(value, 'supportedOpenApiVersions', 'supported_open_api_versions')),
    specMaxBytes: numberValue(read(value, 'specMaxBytes', 'spec_max_bytes'), 0),
    endpointMaxCount: numberValue(read(value, 'endpointMaxCount', 'endpoint_max_count'), 0),
    runnerEnabled: booleanValue(read(value, 'runnerEnabled', 'runner_enabled'), false),
    runnerTimeoutSeconds: numberValue(read(value, 'runnerTimeoutSeconds', 'runner_timeout_seconds'), 0),
    runnerMaxCases: numberValue(read(value, 'runnerMaxCases', 'runner_max_cases'), 0),
    promptKey: optionalString(read(value, 'promptKey', 'prompt_key')),
    modelFallbackEnabled: booleanValue(read(value, 'modelFallbackEnabled', 'model_fallback_enabled'), false),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeApiAutomationSpecList(input: unknown): ApiAutomationSpecList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeApiAutomationSpec),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeApiAutomationSpecDetail(input: unknown): ApiAutomationSpecDetail {
  const value = objectValue(input);
  return {
    spec: normalizeApiAutomationSpec(read(value, 'spec')),
    parseSummary: objectValue(read(value, 'parseSummary', 'parse_summary')),
    endpoints: arrayValue(read(value, 'endpoints')).map(normalizeApiAutomationEndpointSnapshot)
  };
}

export function normalizeApiAutomationSpec(input: unknown): ApiAutomationSpec {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    sourceType: stringValue(read(value, 'sourceType', 'source_type'), 'TEXT'),
    sourceRef: optionalString(read(value, 'sourceRef', 'source_ref')),
    name: stringValue(read(value, 'name')),
    versionLabel: optionalString(read(value, 'versionLabel', 'version_label')),
    specDigest: optionalString(read(value, 'specDigest', 'spec_digest')),
    contentSizeBytes: numberValue(read(value, 'contentSizeBytes', 'content_size_bytes'), 0),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    parserVersion: optionalString(read(value, 'parserVersion', 'parser_version')),
    endpointCount: numberValue(read(value, 'endpointCount', 'endpoint_count'), 0),
    parseErrorSummary: optionalString(read(value, 'parseErrorSummary', 'parse_error_summary')),
    parsedAt: optionalString(read(value, 'parsedAt', 'parsed_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeApiAutomationEndpointSnapshot(input: unknown): ApiAutomationEndpointSnapshot {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    serviceName: optionalString(read(value, 'serviceName', 'service_name')),
    operationId: optionalString(read(value, 'operationId', 'operation_id')),
    httpMethod: stringValue(read(value, 'httpMethod', 'http_method'), 'GET'),
    path: stringValue(read(value, 'path')),
    summary: optionalString(read(value, 'summary')),
    tags: optionalString(read(value, 'tags')),
    parameterCount: numberValue(read(value, 'parameterCount', 'parameter_count'), 0),
    requestBodyPresent: booleanValue(read(value, 'requestBodyPresent', 'request_body_present'), false),
    responseStatuses: optionalString(read(value, 'responseStatuses', 'response_statuses')),
    schemaDigest: optionalString(read(value, 'schemaDigest', 'schema_digest')),
    diffStatus: stringValue(read(value, 'diffStatus', 'diff_status'), 'UNKNOWN'),
    assetApiId: optionalString(read(value, 'assetApiId', 'asset_api_id')),
    diffSummary: objectValue(read(value, 'diffSummary', 'diff_summary')),
    lastDiffAt: optionalString(read(value, 'lastDiffAt', 'last_diff_at')),
    syncedAt: optionalString(read(value, 'syncedAt', 'synced_at')),
    syncErrorSummary: optionalString(read(value, 'syncErrorSummary', 'sync_error_summary'))
  };
}

export function normalizeApiAutomationDiffResponse(input: unknown): ApiAutomationDiffResponse {
  const value = objectValue(input);
  return {
    specId: stringValue(read(value, 'specId', 'spec_id')),
    counts: numberRecord(read(value, 'counts')),
    endpoints: arrayValue(read(value, 'endpoints')).map(normalizeApiAutomationEndpointSnapshot)
  };
}

export function normalizeApiAutomationSyncResponse(input: unknown): ApiAutomationSyncResponse {
  const value = objectValue(input);
  return {
    specId: stringValue(read(value, 'specId', 'spec_id')),
    counts: numberRecord(read(value, 'counts')),
    items: arrayValue(read(value, 'items')).map(normalizeApiAutomationSyncItem),
    endpoints: arrayValue(read(value, 'endpoints')).map(normalizeApiAutomationEndpointSnapshot)
  };
}

export function normalizeApiAutomationSyncItem(input: unknown): ApiAutomationSyncItem {
  const value = objectValue(input);
  return {
    endpointId: stringValue(read(value, 'endpointId', 'endpoint_id')),
    assetApiId: optionalString(read(value, 'assetApiId', 'asset_api_id')),
    httpMethod: stringValue(read(value, 'httpMethod', 'http_method'), 'GET'),
    path: stringValue(read(value, 'path')),
    beforeStatus: stringValue(read(value, 'beforeStatus', 'before_status'), 'UNKNOWN'),
    result: stringValue(read(value, 'result'), 'UNKNOWN'),
    message: optionalString(read(value, 'message'))
  };
}

export function normalizeApiAutomationGenerationTaskDetail(input: unknown): ApiAutomationGenerationTaskDetail {
  const value = objectValue(input);
  return {
    task: normalizeApiAutomationGenerationTask(read(value, 'task')),
    cases: arrayValue(read(value, 'cases')).map(normalizeApiAutomationCase),
    scriptBundles: arrayValue(read(value, 'scriptBundles', 'script_bundles')).map(normalizeApiAutomationScriptBundle)
  };
}

export function normalizeApiAutomationGenerationTask(input: unknown): ApiAutomationGenerationTask {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    specId: stringValue(read(value, 'specId', 'spec_id')),
    requestKey: optionalString(read(value, 'requestKey', 'request_key')),
    requestDigest: optionalString(read(value, 'requestDigest', 'request_digest')),
    generationMode: stringValue(read(value, 'generationMode', 'generation_mode'), 'FALLBACK_ONLY'),
    coverageTypes: stringArray(read(value, 'coverageTypes', 'coverage_types')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    promptKey: optionalString(read(value, 'promptKey', 'prompt_key')),
    promptVersion: optionalString(read(value, 'promptVersion', 'prompt_version')),
    modelInvocationId: optionalString(read(value, 'modelInvocationId', 'model_invocation_id')),
    fallbackUsed: booleanValue(read(value, 'fallbackUsed', 'fallback_used'), false),
    apiCount: numberValue(read(value, 'apiCount', 'api_count'), 0),
    caseCount: numberValue(read(value, 'caseCount', 'case_count'), 0),
    inputSummary: objectValue(read(value, 'inputSummary', 'input_summary')),
    errorSummary: optionalString(read(value, 'errorSummary', 'error_summary')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeApiAutomationCase(input: unknown): ApiAutomationCase {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    endpointSnapshotId: stringValue(read(value, 'endpointSnapshotId', 'endpoint_snapshot_id')),
    assetApiId: optionalString(read(value, 'assetApiId', 'asset_api_id')),
    assetTestCaseId: optionalString(read(value, 'assetTestCaseId', 'asset_test_case_id')),
    title: stringValue(read(value, 'title')),
    httpMethod: stringValue(read(value, 'httpMethod', 'http_method'), 'GET'),
    path: stringValue(read(value, 'path')),
    coverageType: stringValue(read(value, 'coverageType', 'coverage_type'), 'SMOKE'),
    expectedStatus: numberValue(read(value, 'expectedStatus', 'expected_status'), 200),
    assertionSummary: objectValue(read(value, 'assertionSummary', 'assertion_summary')),
    requestTemplate: objectValue(read(value, 'requestTemplate', 'request_template')),
    source: stringValue(read(value, 'source'), 'FALLBACK'),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeApiAutomationScriptBundle(input: unknown): ApiAutomationScriptBundle {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    taskId: stringValue(read(value, 'taskId', 'task_id')),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    bundleDigest: optionalString(read(value, 'bundleDigest', 'bundle_digest')),
    fileCount: numberValue(read(value, 'fileCount', 'file_count'), 0),
    fileTreeSummary: objectValue(read(value, 'fileTreeSummary', 'file_tree_summary')),
    dependencySummary: objectValue(read(value, 'dependencySummary', 'dependency_summary')),
    staticCheckStatus: stringValue(read(value, 'staticCheckStatus', 'static_check_status'), 'PENDING'),
    staticCheckSummary: objectValue(read(value, 'staticCheckSummary', 'static_check_summary')),
    reviewNote: optionalString(read(value, 'reviewNote', 'review_note')),
    submittedBy: optionalString(read(value, 'submittedBy', 'submitted_by')),
    approvedBy: optionalString(read(value, 'approvedBy', 'approved_by')),
    submittedAt: optionalString(read(value, 'submittedAt', 'submitted_at')),
    approvedAt: optionalString(read(value, 'approvedAt', 'approved_at')),
    rejectedAt: optionalString(read(value, 'rejectedAt', 'rejected_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

function queryString(filters: ApiAutomationSpecFilters) {
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

function numberValue(input: unknown, fallback: number) {
  const value = typeof input === 'number' ? input : Number(input);
  return Number.isFinite(value) ? value : fallback;
}

function booleanValue(input: unknown, fallback: boolean) {
  if (typeof input === 'boolean') return input;
  if (input === 'true') return true;
  if (input === 'false') return false;
  return fallback;
}

function stringArray(input: unknown) {
  return Array.isArray(input) ? input.map((item) => stringValue(item)).filter(Boolean) : [];
}

function numberRecord(input: unknown) {
  const value = objectValue(input);
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, numberValue(item, 0)]));
}
