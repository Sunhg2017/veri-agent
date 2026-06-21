import { refreshToken } from './auth';
import { ApiError, getAuthToken, requestBinary, requestJson, type ApiResponse, type BinaryResponse } from './client';

const EXECUTION_BASE = '/api/v1/execution';

export interface ExecutionHealth {
  service: string;
  status: string;
  schedulerEnabled: boolean;
  webhookEnabled: boolean;
  cronEnabled: boolean;
  schedulerIntervalMs: number;
  schedulerInitialDelayMs: number;
  schedulerWorkerId?: string;
  schedulerTickBatchSize: number;
  maxConcurrentRunsPerProject: number;
  maxConcurrentNodesPerRun: number;
  nodeHeartbeatTimeoutSeconds: number;
  defaultRunTimeoutSeconds: number;
  recoveryBatchSize: number;
  policy: Record<string, unknown>;
}

export interface ExecutionPlanSummary {
  id: string;
  projectId: string;
  name: string;
  status: string;
  environmentKey: string;
  description?: string;
  dagDigest?: string;
  nodeCount: number;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecutionPlanNode {
  id?: string;
  key: string;
  type: string;
  dependencies: string[];
  inputSummary: Record<string, unknown>;
  failurePolicy: string;
  timeoutSeconds: number;
  retryPolicy: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecutionPlanDetail extends ExecutionPlanSummary {
  triggerPolicy: Record<string, unknown>;
  nodes: ExecutionPlanNode[];
  createdBy?: string;
  updatedBy?: string;
}

export interface ExecutionPlanList {
  items: ExecutionPlanSummary[];
  index: number;
  size: number;
  total: number;
}

export interface ExecutionDagNodePayload {
  key: string;
  type: string;
  dependencies?: string[];
  input?: Record<string, unknown>;
  timeoutSeconds?: number;
  failurePolicy?: string;
  retryPolicy?: Record<string, unknown>;
}

export interface ExecutionPlanPayload {
  projectId: string;
  name: string;
  environmentKey: string;
  description?: string;
  status?: string;
  triggerPolicy?: Record<string, unknown>;
  dag: {
    nodes: ExecutionDagNodePayload[];
  };
}

export interface ExecutionPlanFilters {
  projectId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export interface ExecutionValidationIssue {
  code: string;
  nodeKey?: string;
  severity: string;
  message?: string;
}

export interface ExecutionNodePolicy {
  key: string;
  type: string;
  dependencies: string[];
  failurePolicy: string;
  timeoutSeconds: number;
  retryPolicy: Record<string, unknown>;
  inputSummary: Record<string, unknown>;
  runnerType: string;
}

export interface ExecutionDryRun {
  planId: string;
  valid: boolean;
  dagDigest?: string;
  nodes: ExecutionNodePolicy[];
  issues: ExecutionValidationIssue[];
  policy: Record<string, unknown>;
}

export interface ExecutionRunSummary {
  id: string;
  planId: string;
  projectId: string;
  status: string;
  triggerType: string;
  requestKey?: string;
  sourceEventId?: string;
  attempt: number;
  traceId?: string;
  resultSummary: Record<string, unknown>;
  nodeCount: number;
  createdBy?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecutionNodeRun {
  id: string;
  planNodeId: string;
  nodeKey: string;
  nodeType: string;
  status: string;
  attempt: number;
  runnerType: string;
  externalRunId?: string;
  errorCode?: string;
  errorSummary?: string;
  resultSummary: Record<string, unknown>;
  heartbeatAt?: string;
  queuedAt?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecutionRunArtifact {
  id: string;
  nodeRunId?: string;
  planNodeId?: string;
  nodeKey?: string;
  nodeType?: string;
  runnerType?: string;
  sourceType: string;
  artifactType: string;
  artifactDigest?: string;
  sizeBytes: number;
  captureStatus: string;
  downloadReady: boolean;
  redactionFlags: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecutionRunDetail extends ExecutionRunSummary {
  errorCode?: string;
  errorSummary?: string;
  nodes: ExecutionNodeRun[];
  artifacts: ExecutionRunArtifact[];
  idempotentReplay: boolean;
}

export type ExecutionRunStreamEvent =
  | { type: 'connected'; runId: string; status: string; timestamp?: string }
  | { type: 'heartbeat'; timestamp?: string }
  | { type: 'snapshot'; run: ExecutionRunDetail }
  | {
    type: 'log';
    runId: string;
    status: string;
    level: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS';
    stage?: string;
    message: string;
    nodeRunId?: string;
    nodeKey?: string;
    timestamp?: string;
    metadata: Record<string, unknown>;
  };

export interface ExecutionRunList {
  items: ExecutionRunSummary[];
  index: number;
  size: number;
  total: number;
}

export interface ExecutionRunExport {
  schemaVersion: string;
  exportedAt?: string;
  run: ExecutionRunDetail;
  nodeStatusCounts: Record<string, number>;
  redactionPolicy: Record<string, unknown>;
}

export interface ExecutionRunFilters {
  projectId?: string;
  planId?: string;
  status?: string;
  index?: number;
  size?: number;
}

export interface TriggerExecutionRunPayload {
  requestKey?: string;
  reason?: string;
  variables?: Record<string, unknown>;
}

export interface ExecutionTrigger {
  id: string;
  planId: string;
  triggerType: string;
  status: string;
  configDigest?: string;
  configSummary: Record<string, unknown>;
  secretRefConfigured: boolean;
  secretRefDigest?: string;
  nextFireAt?: string;
  lastFireAt?: string;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecutionTriggerList {
  items: ExecutionTrigger[];
  index: number;
  size: number;
  total: number;
}

export interface ExecutionTriggerFilters {
  triggerType?: string;
  status?: string;
  index?: number;
  size?: number;
}

export interface ExecutionTriggerPayload {
  triggerType?: string;
  status?: string;
  config?: Record<string, unknown>;
  secretRef?: string;
  nextFireAt?: string;
}

export interface ExecutionTriggerDryRun {
  id: string;
  triggerType: string;
  valid: boolean;
  globalEnabled: boolean;
  runCreated: boolean;
  policy: Record<string, unknown>;
}

export interface ExecutionTriggerEvent {
  id: string;
  triggerId: string;
  sourceEventId: string;
  requestDigest?: string;
  status: string;
  runId?: string;
  receivedAt?: string;
  errorCode?: string;
  errorSummary?: string;
  traceId?: string;
}

export interface ExecutionTriggerEventList {
  items: ExecutionTriggerEvent[];
  index: number;
  size: number;
  total: number;
}

export async function fetchExecutionHealth(): Promise<ApiResponse<ExecutionHealth>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/health`);
  return { ...response, data: normalizeExecutionHealth(response.data) };
}

export async function fetchExecutionPlans(filters: ExecutionPlanFilters = {}): Promise<ApiResponse<ExecutionPlanList>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans${queryString(filters)}`);
  return { ...response, data: normalizeExecutionPlanList(response.data) };
}

export async function fetchExecutionPlan(id: string): Promise<ApiResponse<ExecutionPlanDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeExecutionPlanDetail(response.data) };
}

export async function createExecutionPlan(payload: ExecutionPlanPayload): Promise<ApiResponse<ExecutionPlanDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeExecutionPlanDetail(response.data) };
}

export async function updateExecutionPlan(
  id: string,
  payload: Partial<ExecutionPlanPayload>
): Promise<ApiResponse<ExecutionPlanDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeExecutionPlanDetail(response.data) };
}

export async function dryRunExecutionPlan(id: string): Promise<ApiResponse<ExecutionDryRun>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans/${encodeURIComponent(id)}/dry-run`, {
    method: 'POST'
  });
  return { ...response, data: normalizeExecutionDryRun(response.data) };
}

export async function archiveExecutionPlan(id: string): Promise<ApiResponse<ExecutionPlanDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans/${encodeURIComponent(id)}/archive`, {
    method: 'POST'
  });
  return { ...response, data: normalizeExecutionPlanDetail(response.data) };
}

export async function triggerExecutionRun(
  planId: string,
  payload: TriggerExecutionRunPayload = {}
): Promise<ApiResponse<ExecutionRunDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans/${encodeURIComponent(planId)}/runs`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeExecutionRunDetail(response.data) };
}

export async function fetchExecutionRuns(filters: ExecutionRunFilters = {}): Promise<ApiResponse<ExecutionRunList>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/runs${queryString(filters)}`);
  return { ...response, data: normalizeExecutionRunList(response.data) };
}

export async function fetchExecutionRun(id: string): Promise<ApiResponse<ExecutionRunDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/runs/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeExecutionRunDetail(response.data) };
}

export async function subscribeExecutionRunStream(
  id: string,
  onEvent: (event: ExecutionRunStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetchExecutionRunStream(id, signal, true);
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType && !contentType.toLowerCase().includes('text/event-stream')) {
    throw new ApiError(
      '执行日志流返回类型异常',
      'INVALID_STREAM_RESPONSE',
      response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  }

  const pushEvents = (chunk: string) => {
    parseExecutionRunStreamEvents(chunk).forEach(onEvent);
  };

  if (!response.body) {
    pushEvents(await response.text());
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (signal?.aborted) {
      return;
    }
    buffer += decoder.decode(value, { stream: !done });
    const parts = buffer.split(/\r?\n\r?\n/);
    buffer = parts.pop() ?? '';
    parts.filter(Boolean).forEach(pushEvents);
    if (done) {
      break;
    }
  }
  if (buffer.trim()) {
    pushEvents(buffer);
  }
}

export async function exportExecutionRun(id: string): Promise<ApiResponse<ExecutionRunExport>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/runs/${encodeURIComponent(id)}/export`);
  return { ...response, data: normalizeExecutionRunExport(response.data) };
}

export async function downloadExecutionArtifact(id: string, artifactId: string): Promise<BinaryResponse> {
  return requestBinary(`${EXECUTION_BASE}/runs/${encodeURIComponent(id)}/artifacts/${encodeURIComponent(artifactId)}/download`);
}

export async function cancelExecutionRun(id: string): Promise<ApiResponse<ExecutionRunDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/runs/${encodeURIComponent(id)}/cancel`, {
    method: 'POST'
  });
  return { ...response, data: normalizeExecutionRunDetail(response.data) };
}

export async function retryExecutionRun(id: string): Promise<ApiResponse<ExecutionRunDetail>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/runs/${encodeURIComponent(id)}/retry`, {
    method: 'POST'
  });
  return { ...response, data: normalizeExecutionRunDetail(response.data) };
}

export async function fetchExecutionTriggers(
  planId: string,
  filters: ExecutionTriggerFilters = {}
): Promise<ApiResponse<ExecutionTriggerList>> {
  const response = await requestJson<unknown>(
    `${EXECUTION_BASE}/plans/${encodeURIComponent(planId)}/triggers${queryString(filters)}`
  );
  return { ...response, data: normalizeExecutionTriggerList(response.data) };
}

export async function createExecutionTrigger(
  planId: string,
  payload: ExecutionTriggerPayload
): Promise<ApiResponse<ExecutionTrigger>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/plans/${encodeURIComponent(planId)}/triggers`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeExecutionTrigger(response.data) };
}

export async function updateExecutionTrigger(
  id: string,
  payload: Omit<ExecutionTriggerPayload, 'triggerType'>
): Promise<ApiResponse<ExecutionTrigger>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/triggers/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeExecutionTrigger(response.data) };
}

export async function dryRunExecutionTrigger(id: string): Promise<ApiResponse<ExecutionTriggerDryRun>> {
  const response = await requestJson<unknown>(`${EXECUTION_BASE}/triggers/${encodeURIComponent(id)}/dry-run`, {
    method: 'POST'
  });
  return { ...response, data: normalizeExecutionTriggerDryRun(response.data) };
}

export async function fetchExecutionTriggerEvents(
  triggerId: string,
  filters: { status?: string; index?: number; size?: number } = {}
): Promise<ApiResponse<ExecutionTriggerEventList>> {
  const response = await requestJson<unknown>(
    `${EXECUTION_BASE}/triggers/${encodeURIComponent(triggerId)}/events${queryString(filters)}`
  );
  return { ...response, data: normalizeExecutionTriggerEventList(response.data) };
}

export function normalizeExecutionHealth(input: unknown): ExecutionHealth {
  const value = objectValue(input);
  return {
    service: stringValue(read(value, 'service'), 'execution'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    schedulerEnabled: booleanValue(read(value, 'schedulerEnabled', 'scheduler_enabled'), false),
    webhookEnabled: booleanValue(read(value, 'webhookEnabled', 'webhook_enabled'), false),
    cronEnabled: booleanValue(read(value, 'cronEnabled', 'cron_enabled'), false),
    schedulerIntervalMs: numberValue(read(value, 'schedulerIntervalMs', 'scheduler_interval_ms'), 0),
    schedulerInitialDelayMs: numberValue(read(value, 'schedulerInitialDelayMs', 'scheduler_initial_delay_ms'), 0),
    schedulerWorkerId: optionalString(read(value, 'schedulerWorkerId', 'scheduler_worker_id')),
    schedulerTickBatchSize: numberValue(read(value, 'schedulerTickBatchSize', 'scheduler_tick_batch_size'), 0),
    maxConcurrentRunsPerProject: numberValue(read(value, 'maxConcurrentRunsPerProject', 'max_concurrent_runs_per_project'), 0),
    maxConcurrentNodesPerRun: numberValue(read(value, 'maxConcurrentNodesPerRun', 'max_concurrent_nodes_per_run'), 0),
    nodeHeartbeatTimeoutSeconds: numberValue(read(value, 'nodeHeartbeatTimeoutSeconds', 'node_heartbeat_timeout_seconds'), 0),
    defaultRunTimeoutSeconds: numberValue(read(value, 'defaultRunTimeoutSeconds', 'default_run_timeout_seconds'), 0),
    recoveryBatchSize: numberValue(read(value, 'recoveryBatchSize', 'recovery_batch_size'), 0),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeExecutionPlanList(input: unknown): ExecutionPlanList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeExecutionPlanSummary),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeExecutionPlanSummary(input: unknown): ExecutionPlanSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    name: stringValue(read(value, 'name')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    environmentKey: stringValue(read(value, 'environmentKey', 'environment_key')),
    description: optionalString(read(value, 'description')),
    dagDigest: optionalString(read(value, 'dagDigest', 'dag_digest')),
    nodeCount: numberValue(read(value, 'nodeCount', 'node_count'), 0),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeExecutionPlanDetail(input: unknown): ExecutionPlanDetail {
  const value = objectValue(input);
  return {
    ...normalizeExecutionPlanSummary(value),
    triggerPolicy: objectValue(read(value, 'triggerPolicy', 'trigger_policy')),
    nodes: arrayValue(read(value, 'nodes')).map(normalizeExecutionPlanNode),
    createdBy: optionalString(read(value, 'createdBy', 'created_by')),
    updatedBy: optionalString(read(value, 'updatedBy', 'updated_by'))
  };
}

export function normalizeExecutionPlanNode(input: unknown): ExecutionPlanNode {
  const value = objectValue(input);
  return {
    id: optionalString(read(value, 'id')),
    key: stringValue(read(value, 'key')),
    type: stringValue(read(value, 'type'), 'API_TEST'),
    dependencies: stringArray(read(value, 'dependencies')),
    inputSummary: objectValue(read(value, 'inputSummary', 'input_summary')),
    failurePolicy: stringValue(read(value, 'failurePolicy', 'failure_policy'), 'FAIL_FAST'),
    timeoutSeconds: numberValue(read(value, 'timeoutSeconds', 'timeout_seconds'), 300),
    retryPolicy: objectValue(read(value, 'retryPolicy', 'retry_policy')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeExecutionDryRun(input: unknown): ExecutionDryRun {
  const value = objectValue(input);
  return {
    planId: stringValue(read(value, 'planId', 'plan_id')),
    valid: booleanValue(read(value, 'valid'), false),
    dagDigest: optionalString(read(value, 'dagDigest', 'dag_digest')),
    nodes: arrayValue(read(value, 'nodes')).map(normalizeExecutionNodePolicy),
    issues: arrayValue(read(value, 'issues')).map(normalizeExecutionValidationIssue),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeExecutionNodePolicy(input: unknown): ExecutionNodePolicy {
  const value = objectValue(input);
  return {
    key: stringValue(read(value, 'key')),
    type: stringValue(read(value, 'type'), 'API_TEST'),
    dependencies: stringArray(read(value, 'dependencies')),
    failurePolicy: stringValue(read(value, 'failurePolicy', 'failure_policy'), 'FAIL_FAST'),
    timeoutSeconds: numberValue(read(value, 'timeoutSeconds', 'timeout_seconds'), 300),
    retryPolicy: objectValue(read(value, 'retryPolicy', 'retry_policy')),
    inputSummary: objectValue(read(value, 'inputSummary', 'input_summary')),
    runnerType: stringValue(read(value, 'runnerType', 'runner_type'), 'CONTROL')
  };
}

export function normalizeExecutionValidationIssue(input: unknown): ExecutionValidationIssue {
  const value = objectValue(input);
  return {
    code: stringValue(read(value, 'code'), 'UNKNOWN'),
    nodeKey: optionalString(read(value, 'nodeKey', 'node_key')),
    severity: stringValue(read(value, 'severity'), 'ERROR'),
    message: optionalString(read(value, 'message'))
  };
}

export function normalizeExecutionRunList(input: unknown): ExecutionRunList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeExecutionRunSummary),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeExecutionRunSummary(input: unknown): ExecutionRunSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    planId: stringValue(read(value, 'planId', 'plan_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    triggerType: stringValue(read(value, 'triggerType', 'trigger_type'), 'MANUAL'),
    requestKey: optionalString(read(value, 'requestKey', 'request_key')),
    sourceEventId: optionalString(read(value, 'sourceEventId', 'source_event_id')),
    attempt: numberValue(read(value, 'attempt'), 1),
    traceId: optionalString(read(value, 'traceId', 'trace_id')),
    resultSummary: objectValue(read(value, 'resultSummary', 'result_summary')),
    nodeCount: numberValue(read(value, 'nodeCount', 'node_count'), 0),
    createdBy: optionalString(read(value, 'createdBy', 'created_by')),
    startedAt: optionalString(read(value, 'startedAt', 'started_at')),
    finishedAt: optionalString(read(value, 'finishedAt', 'finished_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeExecutionRunDetail(input: unknown): ExecutionRunDetail {
  const value = objectValue(input);
  return {
    ...normalizeExecutionRunSummary(value),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    errorSummary: optionalString(read(value, 'errorSummary', 'error_summary')),
    nodes: arrayValue(read(value, 'nodes')).map(normalizeExecutionNodeRun),
    artifacts: arrayValue(read(value, 'artifacts')).map(normalizeExecutionRunArtifact),
    idempotentReplay: booleanValue(read(value, 'idempotentReplay', 'idempotent_replay'), false)
  };
}

export function parseExecutionRunStreamEvents(text: string): ExecutionRunStreamEvent[] {
  return text
    .split(/\r?\n\r?\n/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map(parseExecutionRunStreamEvent)
    .filter((event): event is ExecutionRunStreamEvent => event !== undefined);
}

export function normalizeExecutionRunExport(input: unknown): ExecutionRunExport {
  const value = objectValue(input);
  return {
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version'), 'wp9-run-export-v1'),
    exportedAt: optionalString(read(value, 'exportedAt', 'exported_at')),
    run: normalizeExecutionRunDetail(read(value, 'run')),
    nodeStatusCounts: numberRecord(read(value, 'nodeStatusCounts', 'node_status_counts')),
    redactionPolicy: objectValue(read(value, 'redactionPolicy', 'redaction_policy'))
  };
}

export function normalizeExecutionNodeRun(input: unknown): ExecutionNodeRun {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    planNodeId: stringValue(read(value, 'planNodeId', 'plan_node_id')),
    nodeKey: stringValue(read(value, 'nodeKey', 'node_key')),
    nodeType: stringValue(read(value, 'nodeType', 'node_type'), 'API_TEST'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    attempt: numberValue(read(value, 'attempt'), 1),
    runnerType: stringValue(read(value, 'runnerType', 'runner_type'), 'CONTROL'),
    externalRunId: optionalString(read(value, 'externalRunId', 'external_run_id')),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    errorSummary: optionalString(read(value, 'errorSummary', 'error_summary')),
    resultSummary: objectValue(read(value, 'resultSummary', 'result_summary')),
    heartbeatAt: optionalString(read(value, 'heartbeatAt', 'heartbeat_at')),
    queuedAt: optionalString(read(value, 'queuedAt', 'queued_at')),
    startedAt: optionalString(read(value, 'startedAt', 'started_at')),
    finishedAt: optionalString(read(value, 'finishedAt', 'finished_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeExecutionRunArtifact(input: unknown): ExecutionRunArtifact {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    nodeRunId: optionalString(read(value, 'nodeRunId', 'node_run_id')),
    planNodeId: optionalString(read(value, 'planNodeId', 'plan_node_id')),
    nodeKey: optionalString(read(value, 'nodeKey', 'node_key')),
    nodeType: optionalString(read(value, 'nodeType', 'node_type')),
    runnerType: optionalString(read(value, 'runnerType', 'runner_type')),
    sourceType: stringValue(read(value, 'sourceType', 'source_type')),
    artifactType: stringValue(read(value, 'artifactType', 'artifact_type')),
    artifactDigest: optionalString(read(value, 'artifactDigest', 'artifact_digest')),
    sizeBytes: numberValue(read(value, 'sizeBytes', 'size_bytes'), 0),
    captureStatus: stringValue(read(value, 'captureStatus', 'capture_status'), 'UNKNOWN'),
    downloadReady: booleanValue(read(value, 'downloadReady', 'download_ready'), false),
    redactionFlags: objectValue(read(value, 'redactionFlags', 'redaction_flags')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeExecutionTriggerList(input: unknown): ExecutionTriggerList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeExecutionTrigger),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeExecutionTrigger(input: unknown): ExecutionTrigger {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    planId: stringValue(read(value, 'planId', 'plan_id')),
    triggerType: stringValue(read(value, 'triggerType', 'trigger_type'), 'WEBHOOK'),
    status: stringValue(read(value, 'status'), 'DISABLED'),
    configDigest: optionalString(read(value, 'configDigest', 'config_digest')),
    configSummary: objectValue(read(value, 'configSummary', 'config_summary')),
    secretRefConfigured: booleanValue(read(value, 'secretRefConfigured', 'secret_ref_configured'), false),
    secretRefDigest: optionalString(read(value, 'secretRefDigest', 'secret_ref_digest')),
    nextFireAt: optionalString(read(value, 'nextFireAt', 'next_fire_at')),
    lastFireAt: optionalString(read(value, 'lastFireAt', 'last_fire_at')),
    createdBy: optionalString(read(value, 'createdBy', 'created_by')),
    updatedBy: optionalString(read(value, 'updatedBy', 'updated_by')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeExecutionTriggerDryRun(input: unknown): ExecutionTriggerDryRun {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    triggerType: stringValue(read(value, 'triggerType', 'trigger_type'), 'WEBHOOK'),
    valid: booleanValue(read(value, 'valid'), false),
    globalEnabled: booleanValue(read(value, 'globalEnabled', 'global_enabled'), false),
    runCreated: booleanValue(read(value, 'runCreated', 'run_created'), false),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeExecutionTriggerEventList(input: unknown): ExecutionTriggerEventList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeExecutionTriggerEvent),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeExecutionTriggerEvent(input: unknown): ExecutionTriggerEvent {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    triggerId: stringValue(read(value, 'triggerId', 'trigger_id')),
    sourceEventId: stringValue(read(value, 'sourceEventId', 'source_event_id')),
    requestDigest: optionalString(read(value, 'requestDigest', 'request_digest')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    runId: optionalString(read(value, 'runId', 'run_id')),
    receivedAt: optionalString(read(value, 'receivedAt', 'received_at')),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    errorSummary: optionalString(read(value, 'errorSummary', 'error_summary')),
    traceId: optionalString(read(value, 'traceId', 'trace_id'))
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

function numberRecord(input: unknown): Record<string, number> {
  const value = objectValue(input);
  return Object.fromEntries(
    Object.entries(value)
      .map(([key, item]) => [key, numberValue(item, 0)])
      .filter(([key]) => Boolean(key))
  );
}

async function fetchExecutionRunStream(id: string, signal: AbortSignal | undefined, retryOnUnauthorized: boolean) {
  const headers = new Headers({ Accept: 'text/event-stream' });
  const token = getAuthToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(`${EXECUTION_BASE}/runs/${encodeURIComponent(id)}/stream`, {
    method: 'GET',
    headers,
    signal
  });
  if (response.status === 401 && retryOnUnauthorized && token) {
    const refreshed = await refreshToken();
    if (refreshed) {
      return fetchExecutionRunStream(id, signal, false);
    }
    throw new ApiError('登录已过期，请重新登录', 'SESSION_EXPIRED', '', 401);
  }
  if (!response.ok) {
    throw await streamApiError(response, '执行日志流连接失败');
  }
  return response;
}

function parseExecutionRunStreamEvent(block: string): ExecutionRunStreamEvent | undefined {
  let type = '';
  const dataLines: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      type = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart());
    }
  }
  if (!type || dataLines.length === 0) {
    return undefined;
  }
  let data: Record<string, unknown>;
  try {
    data = objectValue(JSON.parse(dataLines.join('\n')));
  } catch {
    return undefined;
  }
  if (type === 'connected') {
    return {
      type,
      runId: stringValue(read(data, 'runId', 'run_id')),
      status: stringValue(read(data, 'status'), 'UNKNOWN'),
      timestamp: optionalString(read(data, 'timestamp'))
    };
  }
  if (type === 'heartbeat') {
    return {
      type,
      timestamp: optionalString(read(data, 'timestamp'))
    };
  }
  if (type === 'snapshot') {
    return {
      type,
      run: normalizeExecutionRunDetail(read(data, 'run'))
    };
  }
  if (type === 'log') {
    return {
      type,
      runId: stringValue(read(data, 'runId', 'run_id')),
      status: stringValue(read(data, 'status'), 'UNKNOWN'),
      level: normalizeExecutionRunStreamLevel(read(data, 'level')),
      stage: optionalString(read(data, 'stage')),
      message: stringValue(read(data, 'message')),
      nodeRunId: optionalString(read(data, 'nodeRunId', 'node_run_id')),
      nodeKey: optionalString(read(data, 'nodeKey', 'node_key')),
      timestamp: optionalString(read(data, 'timestamp')),
      metadata: objectValue(read(data, 'metadata'))
    };
  }
  return undefined;
}

function normalizeExecutionRunStreamLevel(input: unknown): 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS' {
  const value = stringValue(input, 'INFO').toUpperCase();
  return value === 'WARN' || value === 'ERROR' || value === 'SUCCESS' ? value : 'INFO';
}

async function streamApiError(response: Response, fallbackMessage: string) {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.toLowerCase().includes('application/json')) {
    try {
      const body = objectValue(await response.json());
      throw new ApiError(
        stringValue(read(body, 'message'), fallbackMessage),
        stringValue(read(body, 'code'), `HTTP_${response.status}`),
        stringValue(read(body, 'traceId', 'trace_id'), response.headers.get('X-Trace-Id') ?? ''),
        response.status
      );
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
    }
  }
  throw new ApiError(
    fallbackMessage,
    `HTTP_${response.status}`,
    response.headers.get('X-Trace-Id') ?? '',
    response.status
  );
}
