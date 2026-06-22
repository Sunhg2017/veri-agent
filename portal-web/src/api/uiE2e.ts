import { requestBinary, requestJson, type ApiResponse, type BinaryResponse } from './client';

const UI_E2E_BASE = '/api/v1/ui-e2e';

export interface UiE2eHealth {
  service: string;
  status: string;
  enabled: boolean;
  runnerEnabled: boolean;
  runnerMode: string;
  defaultTimeoutSeconds: number;
  maxTimeoutSeconds: number;
  maxScenesPerRun: number;
  maxConcurrency: number;
  allowlistEnabled: boolean;
  allowlistHostCount: number;
  exportEnabled: boolean;
  supportedNodeTypes: string[];
  credentialPolicy: Record<string, unknown>;
  artifactPolicy: Record<string, unknown>;
  runnerCapacity: Record<string, unknown>;
  policy: Record<string, unknown>;
}

export interface UiE2eSceneStep {
  id: string;
  stepOrder: number;
  stepType: string;
  actionSummary: Record<string, unknown>;
  locatorStrategy: Record<string, unknown>;
  assertionSummary: Record<string, unknown>;
  waitPolicy: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eSceneSummary {
  id: string;
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  code: string;
  name: string;
  status: string;
  riskLevel: string;
  tags: string[];
  sourceSummary: Record<string, unknown>;
  stepCount: number;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eSceneDetail extends UiE2eSceneSummary {
  steps: UiE2eSceneStep[];
  policy: Record<string, unknown>;
}

export interface UiE2eSceneImportStep {
  stepOrder: number;
  stepType: string;
  actionSummary: Record<string, unknown>;
  locatorStrategy: Record<string, unknown>;
  assertionSummary: Record<string, unknown>;
  waitPolicy: Record<string, unknown>;
}

export interface UiE2eSceneImport {
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  code: string;
  name: string;
  status: string;
  riskLevel: string;
  tags: string[];
  sourceSummary: Record<string, unknown>;
  steps: UiE2eSceneImportStep[];
  warnings: string[];
  importSummary: Record<string, unknown>;
}

export interface UiE2eBundleReview {
  id: string;
  reviewStatus: string;
  reviewComment?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eBundleSummary {
  id: string;
  projectId: string;
  sceneId: string;
  sceneCode?: string;
  sceneName?: string;
  sceneStatus?: string;
  status: string;
  bundleDigest?: string;
  staticCheckStatus?: string;
  staticCheckSummary: Record<string, unknown>;
  submittedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eBundleDetail extends UiE2eBundleSummary {
  applicationId?: string;
  environmentId?: string;
  riskLevel?: string;
  tags: string[];
  specSummary: Record<string, unknown>;
  fixtureSummary: Record<string, unknown>;
  policy: Record<string, unknown>;
  submittedBy?: string;
  approvedBy?: string;
  reviews: UiE2eBundleReview[];
}

export interface UiE2eBundleExportBundle {
  id: string;
  projectId: string;
  sceneId: string;
  sceneCode?: string;
  sceneName?: string;
  sceneStatus?: string;
  applicationId?: string;
  environmentId?: string;
  riskLevel?: string;
  tags: string[];
  status: string;
  bundleDigest?: string;
  staticCheckStatus?: string;
  specSummary: Record<string, unknown>;
  fixtureSummary: Record<string, unknown>;
  staticCheckSummary: Record<string, unknown>;
  policy: Record<string, unknown>;
  submittedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eBundleExportReviewSummary {
  reviewCount: number;
  noteCount: number;
  reviewStatuses: string[];
  latestReview: Record<string, unknown>;
}

export interface UiE2eBundleExport {
  schemaVersion: string;
  exportedAt?: string;
  bundle: UiE2eBundleExportBundle;
  reviewSummary: UiE2eBundleExportReviewSummary;
  redactionPolicy: Record<string, unknown>;
}

export interface UiE2eRunSummary {
  id: string;
  projectId: string;
  sceneId: string;
  sceneCode?: string;
  sceneName?: string;
  bundleId: string;
  status: string;
  requestKey?: string;
  runnerMode: string;
  failureCode?: string;
  failureSummary?: string;
  traceId?: string;
  accountSummary: Record<string, unknown>;
  flakyStatus?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eRunStepResult {
  id: string;
  sceneStepId?: string;
  stepOrder: number;
  status: string;
  durationMs: number;
  failureBucket?: string;
  errorCode?: string;
  summary: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eArtifactManifest {
  id: string;
  artifactType: string;
  storageRef?: string;
  artifactDigest?: string;
  sizeBytes: number;
  redactionFlags: Record<string, unknown>;
  captureStatus: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eFlakyMark {
  id: string;
  projectId: string;
  sceneId?: string;
  sceneCode?: string;
  sceneName?: string;
  sceneRiskLevel?: string;
  runId?: string;
  linkedRunCount: number;
  runStatus?: string;
  latestFailureBucket?: string;
  status: string;
  reasonCode?: string;
  reasonSummary?: string;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UiE2eRunDetail extends UiE2eRunSummary {
  sceneStatus?: string;
  bundleStatus?: string;
  executionSummary: Record<string, unknown>;
  stepResults: UiE2eRunStepResult[];
  artifacts: UiE2eArtifactManifest[];
  flakyMark?: UiE2eFlakyMark;
  idempotentReplay: boolean;
}

export interface UiE2eRunExport {
  schemaVersion: string;
  exportedAt?: string;
  run: UiE2eRunDetail;
  redactionPolicy: Record<string, unknown>;
}

export interface BackfillUiE2eRunSummaryPayload {
  projectId: string;
  runIds?: string[];
  limit?: number;
}

export interface BatchCreateUiE2eRunPayload {
  projectId: string;
  sceneIds: string[];
  environmentId?: string;
  baseUrlRef: string;
  accountLeaseRef: string;
  requestKeyPrefix?: string;
  reason?: string;
  browsers?: string[];
  visualRegressionEnabled?: boolean;
  baselineRunId?: string;
  visualMismatchThreshold?: number;
}

export interface UiE2eBatchRunItem {
  sceneId: string;
  sceneCode?: string;
  bundleId?: string;
  outcome: string;
  errorCode?: string;
  errorMessage?: string;
  run?: UiE2eRunDetail;
}

export interface UiE2eBatchRun {
  projectId: string;
  requestedCount: number;
  createdCount: number;
  replayedCount: number;
  failedCount: number;
  items: UiE2eBatchRunItem[];
}

export interface UiE2eRunSummaryBackfillItem {
  runId: string;
  sceneId?: string;
  status?: string;
  updated: boolean;
  stepResultCount: number;
  artifactCount: number;
  errorCode?: string;
  errorMessage?: string;
}

export interface UiE2eRunSummaryBackfill {
  projectId: string;
  requestedCount: number;
  updatedCount: number;
  unchangedCount: number;
  failedCount: number;
  items: UiE2eRunSummaryBackfillItem[];
}

export interface UiE2eList<T> {
  items: T[];
  index: number;
  size: number;
  total: number;
}

export interface UiE2eSceneFilters {
  projectId?: string;
  applicationId?: string;
  environmentId?: string;
  status?: string;
  riskLevel?: string;
  tag?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export interface UiE2eBundleFilters {
  projectId?: string;
  sceneId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export interface UiE2eRunFilters {
  projectId?: string;
  sceneId?: string;
  bundleId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export interface UiE2eFlakyFilters {
  projectId?: string;
  sceneId?: string;
  runId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export interface UiE2eSceneStepPayload {
  stepType: string;
  actionSummary?: Record<string, unknown>;
  locatorStrategy?: Record<string, unknown>;
  assertionSummary?: Record<string, unknown>;
  waitPolicy?: Record<string, unknown>;
}

export interface CreateUiE2eScenePayload {
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  code: string;
  name: string;
  status?: string;
  riskLevel?: string;
  tags?: string[];
  sourceSummary?: Record<string, unknown>;
  steps: UiE2eSceneStepPayload[];
}

export interface UpdateUiE2eScenePayload {
  applicationId?: string;
  environmentId?: string;
  name?: string;
  status?: string;
  riskLevel?: string;
  tags?: string[];
  sourceSummary?: Record<string, unknown>;
  steps?: UiE2eSceneStepPayload[];
}

export type UiE2eSceneImportSourceType = 'SELENIUM_IDE' | 'PLAYWRIGHT_CODEGEN';

export interface ImportUiE2eScenePayload {
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  sourceType: UiE2eSceneImportSourceType;
  content: string;
  codeHint?: string;
  nameHint?: string;
  tags?: string[];
}

export interface CreateUiE2eBundlePayload {
  sceneId: string;
}

export interface ReviewUiE2eBundlePayload {
  note?: string;
}

export interface CreateUiE2eRunPayload {
  projectId: string;
  sceneId: string;
  bundleId: string;
  environmentId?: string;
  baseUrlRef: string;
  accountLeaseRef: string;
  requestKey?: string;
  reason?: string;
  browsers?: string[];
  visualRegressionEnabled?: boolean;
  baselineRunId?: string;
  visualMismatchThreshold?: number;
}

export interface CancelUiE2eRunPayload {
  reason?: string;
}

export interface UpsertUiE2eFlakyMarkPayload {
  projectId: string;
  sceneId?: string;
  runId?: string;
  status: string;
  reasonCode?: string;
  reasonSummary?: string;
}

export async function fetchUiE2eHealth(): Promise<ApiResponse<UiE2eHealth>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/health`);
  return { ...response, data: normalizeUiE2eHealth(response.data) };
}

export async function fetchUiE2eScenes(filters: UiE2eSceneFilters = {}): Promise<ApiResponse<UiE2eList<UiE2eSceneSummary>>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/scenes${queryString(filters)}`);
  return { ...response, data: normalizeUiE2eList(response.data, normalizeUiE2eSceneSummary) };
}

export async function fetchUiE2eScene(id: string): Promise<ApiResponse<UiE2eSceneDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/scenes/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeUiE2eSceneDetail(response.data) };
}

export async function createUiE2eScene(payload: CreateUiE2eScenePayload): Promise<ApiResponse<UiE2eSceneDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/scenes`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eSceneDetail(response.data) };
}

export async function importUiE2eScene(payload: ImportUiE2eScenePayload): Promise<ApiResponse<UiE2eSceneImport>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/scenes/import`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eSceneImport(response.data) };
}

export async function updateUiE2eScene(id: string, payload: UpdateUiE2eScenePayload): Promise<ApiResponse<UiE2eSceneDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/scenes/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eSceneDetail(response.data) };
}

export async function archiveUiE2eScene(id: string): Promise<ApiResponse<UiE2eSceneDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/scenes/${encodeURIComponent(id)}/archive`, {
    method: 'POST'
  });
  return { ...response, data: normalizeUiE2eSceneDetail(response.data) };
}

export async function fetchUiE2eBundles(filters: UiE2eBundleFilters = {}): Promise<ApiResponse<UiE2eList<UiE2eBundleSummary>>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles${queryString(filters)}`);
  return { ...response, data: normalizeUiE2eList(response.data, normalizeUiE2eBundleSummary) };
}

export async function fetchUiE2eBundle(id: string): Promise<ApiResponse<UiE2eBundleDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeUiE2eBundleDetail(response.data) };
}

export async function createUiE2eBundle(payload: CreateUiE2eBundlePayload): Promise<ApiResponse<UiE2eBundleDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles`, {
    method: 'POST',
    body: JSON.stringify({ sceneId: payload.sceneId })
  });
  return { ...response, data: normalizeUiE2eBundleDetail(response.data) };
}

export async function submitUiE2eBundleReview(id: string, payload: ReviewUiE2eBundlePayload): Promise<ApiResponse<UiE2eBundleDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles/${encodeURIComponent(id)}/submit-review`, {
    method: 'POST',
    body: JSON.stringify(compactNotePayload(payload))
  });
  return { ...response, data: normalizeUiE2eBundleDetail(response.data) };
}

export async function approveUiE2eBundle(id: string, payload: ReviewUiE2eBundlePayload): Promise<ApiResponse<UiE2eBundleDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles/${encodeURIComponent(id)}/approve`, {
    method: 'POST',
    body: JSON.stringify(compactNotePayload(payload))
  });
  return { ...response, data: normalizeUiE2eBundleDetail(response.data) };
}

export async function rejectUiE2eBundle(id: string, payload: ReviewUiE2eBundlePayload): Promise<ApiResponse<UiE2eBundleDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify(compactNotePayload(payload))
  });
  return { ...response, data: normalizeUiE2eBundleDetail(response.data) };
}

export async function archiveUiE2eBundle(id: string): Promise<ApiResponse<UiE2eBundleDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles/${encodeURIComponent(id)}/archive`, {
    method: 'POST'
  });
  return { ...response, data: normalizeUiE2eBundleDetail(response.data) };
}

export async function exportUiE2eBundle(id: string): Promise<ApiResponse<UiE2eBundleExport>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/bundles/${encodeURIComponent(id)}/export`);
  return { ...response, data: normalizeUiE2eBundleExport(response.data) };
}

export async function fetchUiE2eRuns(filters: UiE2eRunFilters = {}): Promise<ApiResponse<UiE2eList<UiE2eRunSummary>>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs${queryString(filters)}`);
  return { ...response, data: normalizeUiE2eList(response.data, normalizeUiE2eRunSummary) };
}

export async function fetchUiE2eRun(id: string): Promise<ApiResponse<UiE2eRunDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeUiE2eRunDetail(response.data) };
}

export async function createUiE2eRun(payload: CreateUiE2eRunPayload): Promise<ApiResponse<UiE2eRunDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eRunDetail(response.data) };
}

export async function createUiE2eBatchRun(payload: BatchCreateUiE2eRunPayload): Promise<ApiResponse<UiE2eBatchRun>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs/batch`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eBatchRun(response.data) };
}

export async function cancelUiE2eRun(id: string, payload: CancelUiE2eRunPayload = {}): Promise<ApiResponse<UiE2eRunDetail>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
    body: JSON.stringify(compactNotePayload(payload))
  });
  return { ...response, data: normalizeUiE2eRunDetail(response.data) };
}

export async function exportUiE2eRun(id: string): Promise<ApiResponse<UiE2eRunExport>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs/${encodeURIComponent(id)}/export`);
  return { ...response, data: normalizeUiE2eRunExport(response.data) };
}

export async function backfillUiE2eRunSummary(payload: BackfillUiE2eRunSummaryPayload): Promise<ApiResponse<UiE2eRunSummaryBackfill>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/runs/backfill`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eRunSummaryBackfill(response.data) };
}

export async function downloadUiE2eArtifact(runId: string, artifactId: string): Promise<BinaryResponse> {
  return requestBinary(`${UI_E2E_BASE}/runs/${encodeURIComponent(runId)}/artifacts/${encodeURIComponent(artifactId)}/download`);
}

export async function fetchUiE2eFlakyMarks(filters: UiE2eFlakyFilters = {}): Promise<ApiResponse<UiE2eList<UiE2eFlakyMark>>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/flaky-marks${queryString(filters)}`);
  return { ...response, data: normalizeUiE2eList(response.data, normalizeUiE2eFlakyMark) };
}

export async function fetchUiE2eFlakyMark(id: string): Promise<ApiResponse<UiE2eFlakyMark>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/flaky-marks/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeUiE2eFlakyMark(response.data) };
}

export async function upsertUiE2eFlakyMark(payload: UpsertUiE2eFlakyMarkPayload): Promise<ApiResponse<UiE2eFlakyMark>> {
  const response = await requestJson<unknown>(`${UI_E2E_BASE}/flaky-marks`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeUiE2eFlakyMark(response.data) };
}

export function normalizeUiE2eHealth(input: unknown): UiE2eHealth {
  const value = objectValue(input);
  return {
    service: stringValue(read(value, 'service'), 'ui-e2e'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    enabled: booleanValue(read(value, 'enabled'), false),
    runnerEnabled: booleanValue(read(value, 'runnerEnabled', 'runner_enabled'), false),
    runnerMode: stringValue(read(value, 'runnerMode', 'runner_mode')),
    defaultTimeoutSeconds: numberValue(read(value, 'defaultTimeoutSeconds', 'default_timeout_seconds'), 0),
    maxTimeoutSeconds: numberValue(read(value, 'maxTimeoutSeconds', 'max_timeout_seconds'), 0),
    maxScenesPerRun: numberValue(read(value, 'maxScenesPerRun', 'max_scenes_per_run'), 0),
    maxConcurrency: numberValue(read(value, 'maxConcurrency', 'max_concurrency'), 0),
    allowlistEnabled: booleanValue(read(value, 'allowlistEnabled', 'allowlist_enabled'), false),
    allowlistHostCount: numberValue(read(value, 'allowlistHostCount', 'allowlist_host_count'), 0),
    exportEnabled: booleanValue(read(value, 'exportEnabled', 'export_enabled'), false),
    supportedNodeTypes: stringArray(read(value, 'supportedNodeTypes', 'supported_node_types')),
    credentialPolicy: objectValue(read(value, 'credentialPolicy', 'credential_policy')),
    artifactPolicy: objectValue(read(value, 'artifactPolicy', 'artifact_policy')),
    runnerCapacity: objectValue(read(value, 'runnerCapacity', 'runner_capacity')),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeUiE2eSceneSummary(input: unknown): UiE2eSceneSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    code: stringValue(read(value, 'code')),
    name: stringValue(read(value, 'name')),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    riskLevel: stringValue(read(value, 'riskLevel', 'risk_level'), 'MEDIUM'),
    tags: stringArray(read(value, 'tags')),
    sourceSummary: objectValue(read(value, 'sourceSummary', 'source_summary')),
    stepCount: numberValue(read(value, 'stepCount', 'step_count'), 0),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eSceneDetail(input: unknown): UiE2eSceneDetail {
  const value = objectValue(input);
  return {
    ...normalizeUiE2eSceneSummary(value),
    steps: arrayValue(read(value, 'steps')).map(normalizeUiE2eSceneStep),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeUiE2eSceneImport(input: unknown): UiE2eSceneImport {
  const value = objectValue(input);
  return {
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    code: stringValue(read(value, 'code')),
    name: stringValue(read(value, 'name')),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    riskLevel: stringValue(read(value, 'riskLevel', 'risk_level'), 'MEDIUM'),
    tags: stringArray(read(value, 'tags')),
    sourceSummary: objectValue(read(value, 'sourceSummary', 'source_summary')),
    steps: arrayValue(read(value, 'steps')).map(normalizeUiE2eSceneImportStep),
    warnings: stringArray(read(value, 'warnings')),
    importSummary: objectValue(read(value, 'importSummary', 'import_summary'))
  };
}

export function normalizeUiE2eSceneStep(input: unknown): UiE2eSceneStep {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    stepOrder: numberValue(read(value, 'stepOrder', 'step_order'), 0),
    stepType: stringValue(read(value, 'stepType', 'step_type')),
    actionSummary: objectValue(read(value, 'actionSummary', 'action_summary')),
    locatorStrategy: objectValue(read(value, 'locatorStrategy', 'locator_strategy')),
    assertionSummary: objectValue(read(value, 'assertionSummary', 'assertion_summary')),
    waitPolicy: objectValue(read(value, 'waitPolicy', 'wait_policy')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eSceneImportStep(input: unknown): UiE2eSceneImportStep {
  const value = objectValue(input);
  return {
    stepOrder: numberValue(read(value, 'stepOrder', 'step_order'), 0),
    stepType: stringValue(read(value, 'stepType', 'step_type')),
    actionSummary: objectValue(read(value, 'actionSummary', 'action_summary')),
    locatorStrategy: objectValue(read(value, 'locatorStrategy', 'locator_strategy')),
    assertionSummary: objectValue(read(value, 'assertionSummary', 'assertion_summary')),
    waitPolicy: objectValue(read(value, 'waitPolicy', 'wait_policy'))
  };
}

export function normalizeUiE2eBundleSummary(input: unknown): UiE2eBundleSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    sceneId: stringValue(read(value, 'sceneId', 'scene_id')),
    sceneCode: optionalString(read(value, 'sceneCode', 'scene_code')),
    sceneName: optionalString(read(value, 'sceneName', 'scene_name')),
    sceneStatus: optionalString(read(value, 'sceneStatus', 'scene_status')),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    bundleDigest: optionalString(read(value, 'bundleDigest', 'bundle_digest')),
    staticCheckStatus: optionalString(read(value, 'staticCheckStatus', 'static_check_status')),
    staticCheckSummary: objectValue(read(value, 'staticCheckSummary', 'static_check_summary')),
    submittedAt: optionalString(read(value, 'submittedAt', 'submitted_at')),
    approvedAt: optionalString(read(value, 'approvedAt', 'approved_at')),
    rejectedAt: optionalString(read(value, 'rejectedAt', 'rejected_at')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eBundleDetail(input: unknown): UiE2eBundleDetail {
  const value = objectValue(input);
  return {
    ...normalizeUiE2eBundleSummary(value),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    riskLevel: optionalString(read(value, 'riskLevel', 'risk_level')),
    tags: stringArray(read(value, 'tags')),
    specSummary: objectValue(read(value, 'specSummary', 'spec_summary')),
    fixtureSummary: objectValue(read(value, 'fixtureSummary', 'fixture_summary')),
    policy: objectValue(read(value, 'policy')),
    submittedBy: optionalString(read(value, 'submittedBy', 'submitted_by')),
    approvedBy: optionalString(read(value, 'approvedBy', 'approved_by')),
    reviews: arrayValue(read(value, 'reviews')).map(normalizeUiE2eBundleReview)
  };
}

export function normalizeUiE2eBundleReview(input: unknown): UiE2eBundleReview {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    reviewStatus: stringValue(read(value, 'reviewStatus', 'review_status')),
    reviewComment: optionalString(read(value, 'reviewComment', 'review_comment')),
    reviewedBy: optionalString(read(value, 'reviewedBy', 'reviewed_by')),
    reviewedAt: optionalString(read(value, 'reviewedAt', 'reviewed_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eBundleExport(input: unknown): UiE2eBundleExport {
  const value = objectValue(input);
  return {
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version')),
    exportedAt: optionalString(read(value, 'exportedAt', 'exported_at')),
    bundle: normalizeUiE2eBundleExportBundle(read(value, 'bundle')),
    reviewSummary: normalizeUiE2eBundleExportReviewSummary(read(value, 'reviewSummary', 'review_summary')),
    redactionPolicy: objectValue(read(value, 'redactionPolicy', 'redaction_policy'))
  };
}

export function normalizeUiE2eBundleExportBundle(input: unknown): UiE2eBundleExportBundle {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    sceneId: stringValue(read(value, 'sceneId', 'scene_id')),
    sceneCode: optionalString(read(value, 'sceneCode', 'scene_code')),
    sceneName: optionalString(read(value, 'sceneName', 'scene_name')),
    sceneStatus: optionalString(read(value, 'sceneStatus', 'scene_status')),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    riskLevel: optionalString(read(value, 'riskLevel', 'risk_level')),
    tags: stringArray(read(value, 'tags')),
    status: stringValue(read(value, 'status'), 'DRAFT'),
    bundleDigest: optionalString(read(value, 'bundleDigest', 'bundle_digest')),
    staticCheckStatus: optionalString(read(value, 'staticCheckStatus', 'static_check_status')),
    specSummary: objectValue(read(value, 'specSummary', 'spec_summary')),
    fixtureSummary: objectValue(read(value, 'fixtureSummary', 'fixture_summary')),
    staticCheckSummary: objectValue(read(value, 'staticCheckSummary', 'static_check_summary')),
    policy: objectValue(read(value, 'policy')),
    submittedAt: optionalString(read(value, 'submittedAt', 'submitted_at')),
    approvedAt: optionalString(read(value, 'approvedAt', 'approved_at')),
    rejectedAt: optionalString(read(value, 'rejectedAt', 'rejected_at')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eBundleExportReviewSummary(input: unknown): UiE2eBundleExportReviewSummary {
  const value = objectValue(input);
  return {
    reviewCount: numberValue(read(value, 'reviewCount', 'review_count'), 0),
    noteCount: numberValue(read(value, 'noteCount', 'note_count'), 0),
    reviewStatuses: stringArray(read(value, 'reviewStatuses', 'review_statuses')),
    latestReview: objectValue(read(value, 'latestReview', 'latest_review'))
  };
}

export function normalizeUiE2eRunSummary(input: unknown): UiE2eRunSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    sceneId: stringValue(read(value, 'sceneId', 'scene_id')),
    sceneCode: optionalString(read(value, 'sceneCode', 'scene_code')),
    sceneName: optionalString(read(value, 'sceneName', 'scene_name')),
    bundleId: stringValue(read(value, 'bundleId', 'bundle_id')),
    status: stringValue(read(value, 'status'), 'BLOCKED'),
    requestKey: optionalString(read(value, 'requestKey', 'request_key')),
    runnerMode: stringValue(read(value, 'runnerMode', 'runner_mode')),
    failureCode: optionalString(read(value, 'failureCode', 'failure_code')),
    failureSummary: optionalString(read(value, 'failureSummary', 'failure_summary')),
    traceId: optionalString(read(value, 'traceId', 'trace_id')),
    accountSummary: objectValue(read(value, 'accountSummary', 'account_summary')),
    flakyStatus: optionalString(read(value, 'flakyStatus', 'flaky_status')),
    startedAt: optionalString(read(value, 'startedAt', 'started_at')),
    finishedAt: optionalString(read(value, 'finishedAt', 'finished_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eRunDetail(input: unknown): UiE2eRunDetail {
  const value = objectValue(input);
  return {
    ...normalizeUiE2eRunSummary(value),
    sceneStatus: optionalString(read(value, 'sceneStatus', 'scene_status')),
    bundleStatus: optionalString(read(value, 'bundleStatus', 'bundle_status')),
    executionSummary: objectValue(read(value, 'executionSummary', 'execution_summary')),
    stepResults: arrayValue(read(value, 'stepResults', 'step_results')).map(normalizeUiE2eRunStepResult),
    artifacts: arrayValue(read(value, 'artifacts')).map(normalizeUiE2eArtifactManifest),
    flakyMark: normalizeOptionalUiE2eFlakyMark(read(value, 'flakyMark', 'flaky_mark')),
    idempotentReplay: booleanValue(read(value, 'idempotentReplay', 'idempotent_replay'), false)
  };
}

export function normalizeUiE2eRunStepResult(input: unknown): UiE2eRunStepResult {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    sceneStepId: optionalString(read(value, 'sceneStepId', 'scene_step_id')),
    stepOrder: numberValue(read(value, 'stepOrder', 'step_order'), 0),
    status: stringValue(read(value, 'status'), 'BLOCKED'),
    durationMs: numberValue(read(value, 'durationMs', 'duration_ms'), 0),
    failureBucket: optionalString(read(value, 'failureBucket', 'failure_bucket')),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    summary: objectValue(read(value, 'summary')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eArtifactManifest(input: unknown): UiE2eArtifactManifest {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    artifactType: stringValue(read(value, 'artifactType', 'artifact_type')),
    storageRef: optionalString(read(value, 'storageRef', 'storage_ref')),
    artifactDigest: optionalString(read(value, 'artifactDigest', 'artifact_digest')),
    sizeBytes: numberValue(read(value, 'sizeBytes', 'size_bytes'), 0),
    redactionFlags: objectValue(read(value, 'redactionFlags', 'redaction_flags')),
    captureStatus: stringValue(read(value, 'captureStatus', 'capture_status')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eFlakyMark(input: unknown): UiE2eFlakyMark {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    sceneId: optionalString(read(value, 'sceneId', 'scene_id')),
    sceneCode: optionalString(read(value, 'sceneCode', 'scene_code')),
    sceneName: optionalString(read(value, 'sceneName', 'scene_name')),
    sceneRiskLevel: optionalString(read(value, 'sceneRiskLevel', 'scene_risk_level')),
    runId: optionalString(read(value, 'runId', 'run_id')),
    linkedRunCount: numberValue(read(value, 'linkedRunCount', 'linked_run_count'), 0),
    runStatus: optionalString(read(value, 'runStatus', 'run_status')),
    latestFailureBucket: optionalString(read(value, 'latestFailureBucket', 'latest_failure_bucket')),
    status: stringValue(read(value, 'status'), 'NONE'),
    reasonCode: optionalString(read(value, 'reasonCode', 'reason_code')),
    reasonSummary: optionalString(read(value, 'reasonSummary', 'reason_summary')),
    createdBy: optionalString(read(value, 'createdBy', 'created_by')),
    updatedBy: optionalString(read(value, 'updatedBy', 'updated_by')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeUiE2eRunExport(input: unknown): UiE2eRunExport {
  const value = objectValue(input);
  return {
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version')),
    exportedAt: optionalString(read(value, 'exportedAt', 'exported_at')),
    run: normalizeUiE2eRunDetail(read(value, 'run')),
    redactionPolicy: objectValue(read(value, 'redactionPolicy', 'redaction_policy'))
  };
}

export function normalizeUiE2eBatchRun(input: unknown): UiE2eBatchRun {
  const value = objectValue(input);
  return {
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    requestedCount: numberValue(read(value, 'requestedCount', 'requested_count'), 0),
    createdCount: numberValue(read(value, 'createdCount', 'created_count'), 0),
    replayedCount: numberValue(read(value, 'replayedCount', 'replayed_count'), 0),
    failedCount: numberValue(read(value, 'failedCount', 'failed_count'), 0),
    items: arrayValue(read(value, 'items')).map(normalizeUiE2eBatchRunItem)
  };
}

export function normalizeUiE2eBatchRunItem(input: unknown): UiE2eBatchRunItem {
  const value = objectValue(input);
  return {
    sceneId: stringValue(read(value, 'sceneId', 'scene_id')),
    sceneCode: optionalString(read(value, 'sceneCode', 'scene_code')),
    bundleId: optionalString(read(value, 'bundleId', 'bundle_id')),
    outcome: stringValue(read(value, 'outcome'), 'FAILED'),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    errorMessage: optionalString(read(value, 'errorMessage', 'error_message')),
    run: normalizeOptionalUiE2eRunDetail(read(value, 'run'))
  };
}

export function normalizeUiE2eRunSummaryBackfill(input: unknown): UiE2eRunSummaryBackfill {
  const value = objectValue(input);
  return {
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    requestedCount: numberValue(read(value, 'requestedCount', 'requested_count'), 0),
    updatedCount: numberValue(read(value, 'updatedCount', 'updated_count'), 0),
    unchangedCount: numberValue(read(value, 'unchangedCount', 'unchanged_count'), 0),
    failedCount: numberValue(read(value, 'failedCount', 'failed_count'), 0),
    items: arrayValue(read(value, 'items')).map(normalizeUiE2eRunSummaryBackfillItem)
  };
}

export function normalizeUiE2eRunSummaryBackfillItem(input: unknown): UiE2eRunSummaryBackfillItem {
  const value = objectValue(input);
  return {
    runId: stringValue(read(value, 'runId', 'run_id')),
    sceneId: optionalString(read(value, 'sceneId', 'scene_id')),
    status: optionalString(read(value, 'status')),
    updated: booleanValue(read(value, 'updated'), false),
    stepResultCount: numberValue(read(value, 'stepResultCount', 'step_result_count'), 0),
    artifactCount: numberValue(read(value, 'artifactCount', 'artifact_count'), 0),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    errorMessage: optionalString(read(value, 'errorMessage', 'error_message'))
  };
}

export function normalizeUiE2eList<T>(input: unknown, itemNormalizer: (value: unknown) => T): UiE2eList<T> {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(itemNormalizer),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

function normalizeOptionalUiE2eFlakyMark(input: unknown): UiE2eFlakyMark | undefined {
  const value = objectValue(input);
  return Object.keys(value).length ? normalizeUiE2eFlakyMark(value) : undefined;
}

function normalizeOptionalUiE2eRunDetail(input: unknown): UiE2eRunDetail | undefined {
  const value = objectValue(input);
  return Object.keys(value).length ? normalizeUiE2eRunDetail(value) : undefined;
}

function compactNotePayload(payload: { reason?: string; note?: string }) {
  const result: Record<string, string> = {};
  const reason = optionalString(payload.reason);
  const note = optionalString(payload.note);
  if (reason) {
    result.reason = reason;
  }
  if (note) {
    result.note = note;
  }
  return result;
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
