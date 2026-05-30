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
  contextLimits?: Record<string, number>;
  contextAssemblyPolicy?: TestDesignContextAssemblyPolicyView;
  contextPolicyGovernance?: TestDesignContextPolicyGovernanceView;
  contextPolicyOperations?: TestDesignContextPolicyOperationsView;
  scopePolicy?: TestDesignScopePolicyView;
  evaluationCorpusPolicy?: TestDesignEvaluationCorpusPolicyView;
  releaseReadinessPolicy?: TestDesignReleaseReadinessPolicyView;
  auditChainPolicy?: TestDesignAuditChainPolicyView;
  archivePolicy?: TestDesignArchivePolicyView;
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
  modelObservation?: TestDesignModelObservationView;
  contextAssemblyPolicy?: TestDesignContextAssemblyPolicyView;
  contextPolicyGovernance?: TestDesignContextPolicyGovernanceView;
  contextPolicyOperations?: TestDesignContextPolicyOperationsView;
  scopePolicy?: TestDesignScopePolicyView;
  evaluationCorpusPolicy?: TestDesignEvaluationCorpusPolicyView;
  releaseReadinessPolicy?: TestDesignReleaseReadinessPolicyView;
  auditChainPolicy?: TestDesignAuditChainPolicyView;
  archivePolicy?: TestDesignArchivePolicyView;
  contextSummary: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignContextPolicyGovernanceView {
  policyVersion?: string;
  policySource?: string;
  governanceStatus?: string;
  changeMode?: string;
  projectOverrideSupported?: boolean;
  environmentOverrideSupported?: boolean;
  changeApprovalRequired?: boolean;
  changeApprovalWorkflowReady?: boolean;
  effectiveAtTaskCreation?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignContextAssemblyPolicyView {
  policyVersion?: string;
  assemblyMode?: string;
  digestStrategy?: string;
  inputDigestRequired?: boolean;
  persistedContextSummaryOnly?: boolean;
  wp3ApplicationServiceOnly?: boolean;
  rawContextBodyStored?: boolean;
  modelPayloadStored?: boolean;
  digestValueExported?: boolean;
  requirementBodyExported?: boolean;
  assetSchemaExported?: boolean;
  pageTreeExported?: boolean;
  flowJsonExported?: boolean;
  explicitAssetIdentifierListExported?: boolean;
  historicalCaseStepExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignContextPolicyOperationsView {
  policyVersion?: string;
  operationMode?: string;
  policyResolutionOrder?: string;
  policyFallbackBehavior?: string;
  approvalStatus?: string;
  projectOverrideStoreReady?: boolean;
  environmentOverrideStoreReady?: boolean;
  changeApprovalWorkflowReady?: boolean;
  effectivePolicySnapshotMaterialized?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignScopePolicyView {
  policyVersion?: string;
  scopeModel?: string;
  listFallbackScope?: string;
  taskProjectScopeRequired?: boolean;
  candidateProjectScopeRequired?: boolean;
  batchCandidateProjectScopeRequired?: boolean;
  publishProjectScopeRequired?: boolean;
  asyncTaskProjectScopeRecovered?: boolean;
  smokeProjectScopeRequired?: boolean;
  evaluationCorpusProjectIsolated?: boolean;
  evaluationCorpusOperationsReady?: boolean;
  crossWpScopeDashboardReady?: boolean;
  candidateIdentifierListExported?: boolean;
  roleRuleDetailExported?: boolean;
  serviceTokenValueExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignEvaluationCorpusPolicyView {
  policyVersion?: string;
  corpusMode?: string;
  qualityGateMode?: string;
  thresholdSource?: string;
  projectScopeRequired?: boolean;
  goldenSetBaselineRequired?: boolean;
  qualityEvalScriptReady?: boolean;
  qualityGateIntegrated?: boolean;
  readinessDistributionTracked?: boolean;
  promptVersionTracked?: boolean;
  evaluationCorpusProjectIsolated?: boolean;
  sampleMaintenanceReady?: boolean;
  longTermCalibrationReady?: boolean;
  operationsConsoleReady?: boolean;
  corpusRowExported?: boolean;
  candidateBodyExported?: boolean;
  reviewCommentExported?: boolean;
  promptBodyExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignReleaseReadinessPolicyView {
  policyVersion?: string;
  decisionMode?: string;
  thresholdSource?: string;
  qualityThresholdEvaluated?: boolean;
  advisoryOnly?: boolean;
  publishBlockingEnabled?: boolean;
  manualApprovalRequired?: boolean;
  approvalWorkflowReady?: boolean;
  autoPublishAllowed?: boolean;
  confirmedCandidateRequired?: boolean;
  qualityGateOverrideSupported?: boolean;
  candidateEvidenceExported?: boolean;
  approvalNotesExported?: boolean;
  thresholdRuleDetailExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignAuditChainPolicyView {
  policyVersion?: string;
  chainMode?: string;
  eventSource?: string;
  wp1AuditEventWritten?: boolean;
  wp2InvocationReferenceTracked?: boolean;
  wp3PublishReferenceTracked?: boolean;
  wp5DomainEventsTracked?: boolean;
  projectScopeRequired?: boolean;
  traceSignalTracked?: boolean;
  auditEventDetailExported?: boolean;
  candidateIdentifierListExported?: boolean;
  platformAuditIdentifierExported?: boolean;
  traceIdValueExported?: boolean;
  modelInvocationIdValueExported?: boolean;
  publishIdentifierValueExported?: boolean;
  crossWpAuditDashboardReady?: boolean;
  auditOutboxReplayDashboardReady?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignArchivePolicyView {
  policyVersion?: string;
  retentionDays?: number;
  storagePolicy?: string;
  approvalRequired?: boolean;
  archiveApprovalWorkflowReady?: boolean;
  externalSharingAllowed?: boolean;
  retentionPolicyTracked?: boolean;
  archiveStorageReady?: boolean;
  archivePathExported?: boolean;
  archiveNotesExported?: boolean;
  approvalNotesExported?: boolean;
  ticketUrlExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignModelObservationView {
  invocationId?: string;
  jobId?: string;
  traceId?: string;
  available: boolean;
  status?: string;
  providerName?: string;
  modelName?: string;
  routingRuleName?: string;
  routingGroup?: string;
  modelCapability?: string;
  fallbackUsed?: boolean;
  inputTokens?: number;
  outputTokens?: number;
  totalCost?: number;
  latencyMs?: number;
  errorCode?: string;
  errorMessage?: string;
  actorService?: string;
  createdAt?: string;
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
  candidateStatus?: string;
  candidateVersion?: number;
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

export interface TestDesignQualityMetricView {
  code: string;
  count: number;
  percent: number;
}

export interface TestDesignQualityDistributionItemView {
  label: string;
  count: number;
  percent: number;
}

export interface TestDesignQualityReadinessCheckView {
  code: string;
  label: string;
  status: string;
  severity: string;
  currentValue: number;
  thresholdValue: number;
  unit: string;
  description?: string;
}

export interface TestDesignQualityReadinessView {
  status: string;
  blockingCount: number;
  warningCount: number;
  checks: TestDesignQualityReadinessCheckView[];
}

export interface TestDesignQualitySummaryView {
  taskId: string;
  projectId?: string;
  taskTitle?: string;
  taskStatus?: string;
  scope: string;
  total: number;
  reviewableCount: number;
  publishableCount: number;
  failedCount: number;
  confirmedCount: number;
  publishedCount: number;
  stepCompleteCount: number;
  expectedCompleteCount: number;
  lowConfidenceCount: number;
  errorCount: number;
  missingRequirementCount: number;
  missingTitleCount: number;
  duplicateKeyCollisionCount: number;
  readiness?: TestDesignQualityReadinessView;
  metrics: TestDesignQualityMetricView[];
  distributions: Record<string, TestDesignQualityDistributionItemView[]>;
  generatedAt?: string;
}

export interface TestDesignPromptTrendBucketView {
  promptKey: string;
  promptVersion: string;
  taskCount: number;
  candidateCount: number;
  confirmedCount: number;
  publishedCount: number;
  stepCompleteCount: number;
  expectedCompleteCount: number;
  lowConfidenceCount: number;
  errorCount: number;
  duplicateKeyCollisionCount: number;
  correctionCount: number;
  rejectedCount: number;
  ignoredCount: number;
  stepCompletePercent: number;
  expectedCompletePercent: number;
  lowConfidencePercent: number;
  errorPercent: number;
  feedbackSignalPercent: number;
  readiness?: TestDesignQualityReadinessView;
  latestTaskCreatedAt?: string;
}

export interface TestDesignPromptTrendView {
  projectId?: string;
  promptKey?: string;
  taskCount: number;
  candidateCount: number;
  readinessDistribution: TestDesignQualityDistributionItemView[];
  buckets: TestDesignPromptTrendBucketView[];
  generatedAt?: string;
}

export interface TestDesignAuditSummaryMetricView {
  code: string;
  label: string;
  count: number;
  tone: string;
}

export interface TestDesignAuditTimelineItemView {
  source: string;
  action: string;
  result: string;
  candidateId?: string;
  assetCaseId?: string;
  actor?: string;
  hasNote: boolean;
  createdAt?: string;
}

export interface TestDesignAuditSummaryView {
  taskId: string;
  projectId?: string;
  taskStatus?: string;
  requestedBy?: string;
  taskCreatedAt?: string;
  taskUpdatedAt?: string;
  eventCount: number;
  reviewRecordCount: number;
  publishRecordCount: number;
  dryRunRecordCount: number;
  issueCount: number;
  noteCoverageCount: number;
  recentEvents: TestDesignAuditTimelineItemView[];
  metrics: TestDesignAuditSummaryMetricView[];
  generatedAt?: string;
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
  contextApiIds?: string[];
  contextPageIds?: string[];
  contextFlowIds?: string[];
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

export interface ResolveTestDesignConflictPayload {
  version: number;
  caseId: string;
  reason?: string;
  comment?: string;
}

export interface ResolveTestDesignConflictBatchPayload {
  items: Array<{ candidateId: string; version: number; caseId: string }>;
  reason?: string;
  comment?: string;
}

export interface TestDesignConflictBatchResolveItem {
  candidateId: string;
  result: string;
  record?: TestDesignPublishRecordView;
  errorCode?: string;
  errorMessage?: string;
}

export interface TestDesignConflictBatchResolveResult {
  action: string;
  total: number;
  succeededCount: number;
  failedCount: number;
  items: TestDesignConflictBatchResolveItem[];
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
  promptKey?: string;
}

export interface TestDesignPromptTrendFilters {
  index?: number;
  size?: number;
  projectId?: string;
  promptKey?: string;
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

function optionalBoolean(value: unknown) {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  return Boolean(value);
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

function numberRecordValue(value: unknown): Record<string, number> {
  if (!isRecord(value)) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(value).flatMap(([key, item]) => {
      const normalized = numberValue(item, Number.NaN);
      return Number.isFinite(normalized) ? [[key, normalized]] : [];
    })
  );
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
    contextLimits: numberRecordValue(item.contextLimits ?? item.context_limits),
    contextAssemblyPolicy: normalizeTestDesignContextAssemblyPolicy(
      item.contextAssemblyPolicy ?? item.context_assembly_policy
    ),
    contextPolicyGovernance: normalizeTestDesignContextPolicyGovernance(
      item.contextPolicyGovernance ?? item.context_policy_governance
    ),
    contextPolicyOperations: normalizeTestDesignContextPolicyOperations(
      item.contextPolicyOperations ?? item.context_policy_operations
    ),
    scopePolicy: normalizeTestDesignScopePolicy(item.scopePolicy ?? item.scope_policy),
    evaluationCorpusPolicy: normalizeTestDesignEvaluationCorpusPolicy(
      item.evaluationCorpusPolicy ?? item.evaluation_corpus_policy
    ),
    releaseReadinessPolicy: normalizeTestDesignReleaseReadinessPolicy(
      item.releaseReadinessPolicy ?? item.release_readiness_policy
    ),
    auditChainPolicy: normalizeTestDesignAuditChainPolicy(item.auditChainPolicy ?? item.audit_chain_policy),
    archivePolicy: normalizeTestDesignArchivePolicy(item.archivePolicy ?? item.archive_policy),
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
    modelObservation: normalizeTestDesignModelObservation(item.modelObservation ?? item.model_observation),
    contextAssemblyPolicy: normalizeTestDesignContextAssemblyPolicy(
      item.contextAssemblyPolicy ?? item.context_assembly_policy
    ),
    contextPolicyGovernance: normalizeTestDesignContextPolicyGovernance(
      item.contextPolicyGovernance ?? item.context_policy_governance
    ),
    contextPolicyOperations: normalizeTestDesignContextPolicyOperations(
      item.contextPolicyOperations ?? item.context_policy_operations
    ),
    scopePolicy: normalizeTestDesignScopePolicy(item.scopePolicy ?? item.scope_policy),
    evaluationCorpusPolicy: normalizeTestDesignEvaluationCorpusPolicy(
      item.evaluationCorpusPolicy ?? item.evaluation_corpus_policy
    ),
    releaseReadinessPolicy: normalizeTestDesignReleaseReadinessPolicy(
      item.releaseReadinessPolicy ?? item.release_readiness_policy
    ),
    auditChainPolicy: normalizeTestDesignAuditChainPolicy(item.auditChainPolicy ?? item.audit_chain_policy),
    archivePolicy: normalizeTestDesignArchivePolicy(item.archivePolicy ?? item.archive_policy),
    contextSummary: recordValue(item.contextSummary ?? item.context_summary),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignContextAssemblyPolicy(
  raw: unknown
): TestDesignContextAssemblyPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    assemblyMode: optionalString(raw.assemblyMode) ?? optionalString(raw.assembly_mode),
    digestStrategy: optionalString(raw.digestStrategy) ?? optionalString(raw.digest_strategy),
    inputDigestRequired: optionalBoolean(raw.inputDigestRequired ?? raw.input_digest_required),
    persistedContextSummaryOnly: optionalBoolean(
      raw.persistedContextSummaryOnly ?? raw.persisted_context_summary_only
    ),
    wp3ApplicationServiceOnly: optionalBoolean(
      raw.wp3ApplicationServiceOnly ?? raw.wp3_application_service_only
    ),
    rawContextBodyStored: optionalBoolean(raw.rawContextBodyStored ?? raw.raw_context_body_stored),
    modelPayloadStored: optionalBoolean(raw.modelPayloadStored ?? raw.model_payload_stored),
    digestValueExported: optionalBoolean(raw.digestValueExported ?? raw.digest_value_exported),
    requirementBodyExported: optionalBoolean(raw.requirementBodyExported ?? raw.requirement_body_exported),
    assetSchemaExported: optionalBoolean(raw.assetSchemaExported ?? raw.asset_schema_exported),
    pageTreeExported: optionalBoolean(raw.pageTreeExported ?? raw.page_tree_exported),
    flowJsonExported: optionalBoolean(raw.flowJsonExported ?? raw.flow_json_exported),
    explicitAssetIdentifierListExported: optionalBoolean(
      raw.explicitAssetIdentifierListExported ?? raw.explicit_asset_identifier_list_exported
    ),
    historicalCaseStepExported: optionalBoolean(
      raw.historicalCaseStepExported ?? raw.historical_case_step_exported
    ),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignContextPolicyGovernance(
  raw: unknown
): TestDesignContextPolicyGovernanceView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    policySource: optionalString(raw.policySource) ?? optionalString(raw.policy_source),
    governanceStatus: optionalString(raw.governanceStatus) ?? optionalString(raw.governance_status),
    changeMode: optionalString(raw.changeMode) ?? optionalString(raw.change_mode),
    projectOverrideSupported: optionalBoolean(
      raw.projectOverrideSupported ?? raw.project_override_supported
    ),
    environmentOverrideSupported: optionalBoolean(
      raw.environmentOverrideSupported ?? raw.environment_override_supported
    ),
    changeApprovalRequired: optionalBoolean(raw.changeApprovalRequired ?? raw.change_approval_required),
    changeApprovalWorkflowReady: optionalBoolean(
      raw.changeApprovalWorkflowReady ?? raw.change_approval_workflow_ready
    ),
    effectiveAtTaskCreation: optionalBoolean(raw.effectiveAtTaskCreation ?? raw.effective_at_task_creation),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignContextPolicyOperations(
  raw: unknown
): TestDesignContextPolicyOperationsView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    operationMode: optionalString(raw.operationMode) ?? optionalString(raw.operation_mode),
    policyResolutionOrder: optionalString(raw.policyResolutionOrder) ?? optionalString(raw.policy_resolution_order),
    policyFallbackBehavior: optionalString(raw.policyFallbackBehavior) ?? optionalString(raw.policy_fallback_behavior),
    approvalStatus: optionalString(raw.approvalStatus) ?? optionalString(raw.approval_status),
    projectOverrideStoreReady: optionalBoolean(
      raw.projectOverrideStoreReady ?? raw.project_override_store_ready
    ),
    environmentOverrideStoreReady: optionalBoolean(
      raw.environmentOverrideStoreReady ?? raw.environment_override_store_ready
    ),
    changeApprovalWorkflowReady: optionalBoolean(
      raw.changeApprovalWorkflowReady ?? raw.change_approval_workflow_ready
    ),
    effectivePolicySnapshotMaterialized: optionalBoolean(
      raw.effectivePolicySnapshotMaterialized ?? raw.effective_policy_snapshot_materialized
    ),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignScopePolicy(raw: unknown): TestDesignScopePolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    scopeModel: optionalString(raw.scopeModel) ?? optionalString(raw.scope_model),
    listFallbackScope: optionalString(raw.listFallbackScope) ?? optionalString(raw.list_fallback_scope),
    taskProjectScopeRequired: optionalBoolean(
      raw.taskProjectScopeRequired ?? raw.task_project_scope_required
    ),
    candidateProjectScopeRequired: optionalBoolean(
      raw.candidateProjectScopeRequired ?? raw.candidate_project_scope_required
    ),
    batchCandidateProjectScopeRequired: optionalBoolean(
      raw.batchCandidateProjectScopeRequired ?? raw.batch_candidate_project_scope_required
    ),
    publishProjectScopeRequired: optionalBoolean(
      raw.publishProjectScopeRequired ?? raw.publish_project_scope_required
    ),
    asyncTaskProjectScopeRecovered: optionalBoolean(
      raw.asyncTaskProjectScopeRecovered ?? raw.async_task_project_scope_recovered
    ),
    smokeProjectScopeRequired: optionalBoolean(
      raw.smokeProjectScopeRequired ?? raw.smoke_project_scope_required
    ),
    evaluationCorpusProjectIsolated: optionalBoolean(
      raw.evaluationCorpusProjectIsolated ?? raw.evaluation_corpus_project_isolated
    ),
    evaluationCorpusOperationsReady: optionalBoolean(
      raw.evaluationCorpusOperationsReady ?? raw.evaluation_corpus_operations_ready
    ),
    crossWpScopeDashboardReady: optionalBoolean(
      raw.crossWpScopeDashboardReady ?? raw.cross_wp_scope_dashboard_ready
    ),
    candidateIdentifierListExported: optionalBoolean(
      raw.candidateIdentifierListExported ?? raw.candidate_identifier_list_exported
    ),
    roleRuleDetailExported: optionalBoolean(raw.roleRuleDetailExported ?? raw.role_rule_detail_exported),
    serviceTokenValueExported: optionalBoolean(
      raw.serviceTokenValueExported ?? raw.service_token_value_exported
    ),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignEvaluationCorpusPolicy(
  raw: unknown
): TestDesignEvaluationCorpusPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    corpusMode: optionalString(raw.corpusMode) ?? optionalString(raw.corpus_mode),
    qualityGateMode: optionalString(raw.qualityGateMode) ?? optionalString(raw.quality_gate_mode),
    thresholdSource: optionalString(raw.thresholdSource) ?? optionalString(raw.threshold_source),
    projectScopeRequired: optionalBoolean(raw.projectScopeRequired ?? raw.project_scope_required),
    goldenSetBaselineRequired: optionalBoolean(
      raw.goldenSetBaselineRequired ?? raw.golden_set_baseline_required
    ),
    qualityEvalScriptReady: optionalBoolean(raw.qualityEvalScriptReady ?? raw.quality_eval_script_ready),
    qualityGateIntegrated: optionalBoolean(raw.qualityGateIntegrated ?? raw.quality_gate_integrated),
    readinessDistributionTracked: optionalBoolean(
      raw.readinessDistributionTracked ?? raw.readiness_distribution_tracked
    ),
    promptVersionTracked: optionalBoolean(raw.promptVersionTracked ?? raw.prompt_version_tracked),
    evaluationCorpusProjectIsolated: optionalBoolean(
      raw.evaluationCorpusProjectIsolated ?? raw.evaluation_corpus_project_isolated
    ),
    sampleMaintenanceReady: optionalBoolean(raw.sampleMaintenanceReady ?? raw.sample_maintenance_ready),
    longTermCalibrationReady: optionalBoolean(
      raw.longTermCalibrationReady ?? raw.long_term_calibration_ready
    ),
    operationsConsoleReady: optionalBoolean(raw.operationsConsoleReady ?? raw.operations_console_ready),
    corpusRowExported: optionalBoolean(raw.corpusRowExported ?? raw.corpus_row_exported),
    candidateBodyExported: optionalBoolean(raw.candidateBodyExported ?? raw.candidate_body_exported),
    reviewCommentExported: optionalBoolean(raw.reviewCommentExported ?? raw.review_comment_exported),
    promptBodyExported: optionalBoolean(raw.promptBodyExported ?? raw.prompt_body_exported),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignReleaseReadinessPolicy(
  raw: unknown
): TestDesignReleaseReadinessPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    decisionMode: optionalString(raw.decisionMode) ?? optionalString(raw.decision_mode),
    thresholdSource: optionalString(raw.thresholdSource) ?? optionalString(raw.threshold_source),
    qualityThresholdEvaluated: optionalBoolean(
      raw.qualityThresholdEvaluated ?? raw.quality_threshold_evaluated
    ),
    advisoryOnly: optionalBoolean(raw.advisoryOnly ?? raw.advisory_only),
    publishBlockingEnabled: optionalBoolean(raw.publishBlockingEnabled ?? raw.publish_blocking_enabled),
    manualApprovalRequired: optionalBoolean(raw.manualApprovalRequired ?? raw.manual_approval_required),
    approvalWorkflowReady: optionalBoolean(raw.approvalWorkflowReady ?? raw.approval_workflow_ready),
    autoPublishAllowed: optionalBoolean(raw.autoPublishAllowed ?? raw.auto_publish_allowed),
    confirmedCandidateRequired: optionalBoolean(
      raw.confirmedCandidateRequired ?? raw.confirmed_candidate_required
    ),
    qualityGateOverrideSupported: optionalBoolean(
      raw.qualityGateOverrideSupported ?? raw.quality_gate_override_supported
    ),
    candidateEvidenceExported: optionalBoolean(
      raw.candidateEvidenceExported ?? raw.candidate_evidence_exported
    ),
    approvalNotesExported: optionalBoolean(raw.approvalNotesExported ?? raw.approval_notes_exported),
    thresholdRuleDetailExported: optionalBoolean(
      raw.thresholdRuleDetailExported ?? raw.threshold_rule_detail_exported
    ),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignAuditChainPolicy(
  raw: unknown
): TestDesignAuditChainPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    chainMode: optionalString(raw.chainMode) ?? optionalString(raw.chain_mode),
    eventSource: optionalString(raw.eventSource) ?? optionalString(raw.event_source),
    wp1AuditEventWritten: optionalBoolean(raw.wp1AuditEventWritten ?? raw.wp1_audit_event_written),
    wp2InvocationReferenceTracked: optionalBoolean(
      raw.wp2InvocationReferenceTracked ?? raw.wp2_invocation_reference_tracked
    ),
    wp3PublishReferenceTracked: optionalBoolean(
      raw.wp3PublishReferenceTracked ?? raw.wp3_publish_reference_tracked
    ),
    wp5DomainEventsTracked: optionalBoolean(raw.wp5DomainEventsTracked ?? raw.wp5_domain_events_tracked),
    projectScopeRequired: optionalBoolean(raw.projectScopeRequired ?? raw.project_scope_required),
    traceSignalTracked: optionalBoolean(raw.traceSignalTracked ?? raw.trace_signal_tracked),
    auditEventDetailExported: optionalBoolean(raw.auditEventDetailExported ?? raw.audit_event_detail_exported),
    candidateIdentifierListExported: optionalBoolean(
      raw.candidateIdentifierListExported ?? raw.candidate_identifier_list_exported
    ),
    platformAuditIdentifierExported: optionalBoolean(
      raw.platformAuditIdentifierExported ?? raw.platform_audit_identifier_exported
    ),
    traceIdValueExported: optionalBoolean(raw.traceIdValueExported ?? raw.trace_id_value_exported),
    modelInvocationIdValueExported: optionalBoolean(
      raw.modelInvocationIdValueExported ?? raw.model_invocation_id_value_exported
    ),
    publishIdentifierValueExported: optionalBoolean(
      raw.publishIdentifierValueExported ?? raw.publish_identifier_value_exported
    ),
    crossWpAuditDashboardReady: optionalBoolean(
      raw.crossWpAuditDashboardReady ?? raw.cross_wp_audit_dashboard_ready
    ),
    auditOutboxReplayDashboardReady: optionalBoolean(
      raw.auditOutboxReplayDashboardReady ?? raw.audit_outbox_replay_dashboard_ready
    ),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignArchivePolicy(raw: unknown): TestDesignArchivePolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    retentionDays: optionalNumber(raw.retentionDays ?? raw.retention_days),
    storagePolicy: optionalString(raw.storagePolicy) ?? optionalString(raw.storage_policy),
    approvalRequired: optionalBoolean(raw.approvalRequired ?? raw.approval_required),
    archiveApprovalWorkflowReady: optionalBoolean(
      raw.archiveApprovalWorkflowReady ?? raw.archive_approval_workflow_ready
    ),
    externalSharingAllowed: optionalBoolean(raw.externalSharingAllowed ?? raw.external_sharing_allowed),
    retentionPolicyTracked: optionalBoolean(raw.retentionPolicyTracked ?? raw.retention_policy_tracked),
    archiveStorageReady: optionalBoolean(raw.archiveStorageReady ?? raw.archive_storage_ready),
    archivePathExported: optionalBoolean(raw.archivePathExported ?? raw.archive_path_exported),
    archiveNotesExported: optionalBoolean(raw.archiveNotesExported ?? raw.archive_notes_exported),
    approvalNotesExported: optionalBoolean(raw.approvalNotesExported ?? raw.approval_notes_exported),
    ticketUrlExported: optionalBoolean(raw.ticketUrlExported ?? raw.ticket_url_exported),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignModelObservation(raw: unknown): TestDesignModelObservationView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    invocationId: optionalString(raw.invocationId) ?? optionalString(raw.invocation_id),
    jobId: optionalString(raw.jobId) ?? optionalString(raw.job_id),
    traceId: optionalString(raw.traceId) ?? optionalString(raw.trace_id),
    available: optionalBoolean(raw.available ?? raw.isAvailable ?? raw.is_available) ?? false,
    status: optionalString(raw.status),
    providerName: optionalString(raw.providerName) ?? optionalString(raw.provider_name),
    modelName: optionalString(raw.modelName) ?? optionalString(raw.model_name),
    routingRuleName: optionalString(raw.routingRuleName) ?? optionalString(raw.routing_rule_name),
    routingGroup: optionalString(raw.routingGroup) ?? optionalString(raw.routing_group),
    modelCapability: optionalString(raw.modelCapability) ?? optionalString(raw.model_capability),
    fallbackUsed: optionalBoolean(raw.fallbackUsed ?? raw.fallback_used),
    inputTokens: optionalNumber(raw.inputTokens ?? raw.input_tokens),
    outputTokens: optionalNumber(raw.outputTokens ?? raw.output_tokens),
    totalCost: optionalNumber(raw.totalCost ?? raw.total_cost),
    latencyMs: optionalNumber(raw.latencyMs ?? raw.latency_ms),
    errorCode: optionalString(raw.errorCode) ?? optionalString(raw.error_code),
    errorMessage: optionalString(raw.errorMessage) ?? optionalString(raw.error_message),
    actorService: optionalString(raw.actorService) ?? optionalString(raw.actor_service),
    createdAt: optionalString(raw.createdAt) ?? optionalString(raw.created_at)
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
    candidateStatus: optionalString(item.candidateStatus) ?? optionalString(item.candidate_status),
    candidateVersion: optionalNumber(item.candidateVersion ?? item.candidate_version),
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

export function normalizeTestDesignQualityMetric(raw: unknown): TestDesignQualityMetricView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    count: numberValue(item.count, 0),
    percent: numberValue(item.percent, 0)
  };
}

export function normalizeTestDesignQualityDistributionItem(raw: unknown): TestDesignQualityDistributionItemView {
  const item = isRecord(raw) ? raw : {};
  return {
    label: stringValue(item.label, 'UNKNOWN'),
    count: numberValue(item.count, 0),
    percent: numberValue(item.percent, 0)
  };
}

export function normalizeTestDesignQualityReadinessCheck(raw: unknown): TestDesignQualityReadinessCheckView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    label: stringValue(item.label, stringValue(item.code, 'UNKNOWN')),
    status: stringValue(item.status, 'UNKNOWN'),
    severity: stringValue(item.severity, 'WARNING'),
    currentValue: numberValue(item.currentValue ?? item.current_value, 0),
    thresholdValue: numberValue(item.thresholdValue ?? item.threshold_value, 0),
    unit: stringValue(item.unit, 'COUNT'),
    description: optionalString(item.description)
  };
}

export function normalizeTestDesignQualityReadiness(raw: unknown): TestDesignQualityReadinessView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    status: stringValue(raw.status, 'UNKNOWN'),
    blockingCount: numberValue(raw.blockingCount ?? raw.blocking_count, 0),
    warningCount: numberValue(raw.warningCount ?? raw.warning_count, 0),
    checks: listItems(raw.checks).map(normalizeTestDesignQualityReadinessCheck)
  };
}

export function normalizeTestDesignQualitySummary(raw: unknown): TestDesignQualitySummaryView {
  const item = isRecord(raw) ? raw : {};
  const distributionsRaw = recordValue(item.distributions);
  const distributions = Object.fromEntries(
    Object.entries(distributionsRaw).map(([key, value]) => [
      key,
      listItems(value).map(normalizeTestDesignQualityDistributionItem)
    ])
  );
  return {
    taskId: stringValue(item.taskId ?? item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    taskTitle: optionalString(item.taskTitle) ?? optionalString(item.task_title),
    taskStatus: optionalString(item.taskStatus) ?? optionalString(item.task_status),
    scope: stringValue(item.scope, 'fullTask'),
    total: numberValue(item.total, 0),
    reviewableCount: numberValue(item.reviewableCount ?? item.reviewable_count, 0),
    publishableCount: numberValue(item.publishableCount ?? item.publishable_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    confirmedCount: numberValue(item.confirmedCount ?? item.confirmed_count, 0),
    publishedCount: numberValue(item.publishedCount ?? item.published_count, 0),
    stepCompleteCount: numberValue(item.stepCompleteCount ?? item.step_complete_count, 0),
    expectedCompleteCount: numberValue(item.expectedCompleteCount ?? item.expected_complete_count, 0),
    lowConfidenceCount: numberValue(item.lowConfidenceCount ?? item.low_confidence_count, 0),
    errorCount: numberValue(item.errorCount ?? item.error_count, 0),
    missingRequirementCount: numberValue(item.missingRequirementCount ?? item.missing_requirement_count, 0),
    missingTitleCount: numberValue(item.missingTitleCount ?? item.missing_title_count, 0),
    duplicateKeyCollisionCount: numberValue(item.duplicateKeyCollisionCount ?? item.duplicate_key_collision_count, 0),
    readiness: normalizeTestDesignQualityReadiness(item.readiness),
    metrics: listItems(item.metrics).map(normalizeTestDesignQualityMetric),
    distributions,
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
  };
}

export function normalizeTestDesignPromptTrendBucket(raw: unknown): TestDesignPromptTrendBucketView {
  const item = isRecord(raw) ? raw : {};
  return {
    promptKey: stringValue(item.promptKey ?? item.prompt_key, 'UNKNOWN'),
    promptVersion: stringValue(item.promptVersion ?? item.prompt_version, 'UNKNOWN'),
    taskCount: numberValue(item.taskCount ?? item.task_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    confirmedCount: numberValue(item.confirmedCount ?? item.confirmed_count, 0),
    publishedCount: numberValue(item.publishedCount ?? item.published_count, 0),
    stepCompleteCount: numberValue(item.stepCompleteCount ?? item.step_complete_count, 0),
    expectedCompleteCount: numberValue(item.expectedCompleteCount ?? item.expected_complete_count, 0),
    lowConfidenceCount: numberValue(item.lowConfidenceCount ?? item.low_confidence_count, 0),
    errorCount: numberValue(item.errorCount ?? item.error_count, 0),
    duplicateKeyCollisionCount: numberValue(item.duplicateKeyCollisionCount ?? item.duplicate_key_collision_count, 0),
    correctionCount: numberValue(item.correctionCount ?? item.correction_count, 0),
    rejectedCount: numberValue(item.rejectedCount ?? item.rejected_count, 0),
    ignoredCount: numberValue(item.ignoredCount ?? item.ignored_count, 0),
    stepCompletePercent: numberValue(item.stepCompletePercent ?? item.step_complete_percent, 0),
    expectedCompletePercent: numberValue(item.expectedCompletePercent ?? item.expected_complete_percent, 0),
    lowConfidencePercent: numberValue(item.lowConfidencePercent ?? item.low_confidence_percent, 0),
    errorPercent: numberValue(item.errorPercent ?? item.error_percent, 0),
    feedbackSignalPercent: numberValue(item.feedbackSignalPercent ?? item.feedback_signal_percent, 0),
    readiness: normalizeTestDesignQualityReadiness(item.readiness),
    latestTaskCreatedAt: optionalString(item.latestTaskCreatedAt) ?? optionalString(item.latest_task_created_at)
  };
}

export function normalizeTestDesignPromptTrend(raw: unknown): TestDesignPromptTrendView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    taskCount: numberValue(item.taskCount ?? item.task_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    readinessDistribution: listItems(item.readinessDistribution ?? item.readiness_distribution)
      .map(normalizeTestDesignQualityDistributionItem),
    buckets: listItems(item.buckets).map(normalizeTestDesignPromptTrendBucket),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
  };
}

export function normalizeTestDesignAuditSummaryMetric(raw: unknown): TestDesignAuditSummaryMetricView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    label: stringValue(item.label, stringValue(item.code, 'UNKNOWN')),
    count: numberValue(item.count, 0),
    tone: stringValue(item.tone, 'neutral')
  };
}

export function normalizeTestDesignAuditTimelineItem(raw: unknown): TestDesignAuditTimelineItemView {
  const item = isRecord(raw) ? raw : {};
  return {
    source: stringValue(item.source, 'UNKNOWN'),
    action: stringValue(item.action, 'UNKNOWN'),
    result: stringValue(item.result, 'UNKNOWN'),
    candidateId: optionalString(item.candidateId) ?? optionalString(item.candidate_id),
    assetCaseId: optionalString(item.assetCaseId) ?? optionalString(item.asset_case_id),
    actor: optionalString(item.actor),
    hasNote: Boolean(item.hasNote ?? item.has_note),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignAuditSummary(raw: unknown): TestDesignAuditSummaryView {
  const item = isRecord(raw) ? raw : {};
  return {
    taskId: stringValue(item.taskId ?? item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    taskStatus: optionalString(item.taskStatus) ?? optionalString(item.task_status),
    requestedBy: optionalString(item.requestedBy) ?? optionalString(item.requested_by),
    taskCreatedAt: optionalString(item.taskCreatedAt) ?? optionalString(item.task_created_at),
    taskUpdatedAt: optionalString(item.taskUpdatedAt) ?? optionalString(item.task_updated_at),
    eventCount: numberValue(item.eventCount ?? item.event_count, 0),
    reviewRecordCount: numberValue(item.reviewRecordCount ?? item.review_record_count, 0),
    publishRecordCount: numberValue(item.publishRecordCount ?? item.publish_record_count, 0),
    dryRunRecordCount: numberValue(item.dryRunRecordCount ?? item.dry_run_record_count, 0),
    issueCount: numberValue(item.issueCount ?? item.issue_count, 0),
    noteCoverageCount: numberValue(item.noteCoverageCount ?? item.note_coverage_count, 0),
    recentEvents: listItems(item.recentEvents ?? item.recent_events).map(normalizeTestDesignAuditTimelineItem),
    metrics: listItems(item.metrics).map(normalizeTestDesignAuditSummaryMetric),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
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

export function normalizeTestDesignConflictBatchResolveItem(raw: unknown): TestDesignConflictBatchResolveItem {
  const item = isRecord(raw) ? raw : {};
  return {
    candidateId: stringValue(item.candidateId ?? item.candidate_id),
    result: stringValue(item.result, 'UNKNOWN'),
    record: item.record ? normalizeTestDesignPublishRecord(item.record) : undefined,
    errorCode: optionalString(item.errorCode) ?? optionalString(item.error_code),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message)
  };
}

export function normalizeTestDesignConflictBatchResolveResult(raw: unknown): TestDesignConflictBatchResolveResult {
  const item = isRecord(raw) ? raw : {};
  return {
    action: stringValue(item.action, 'UNKNOWN'),
    total: numberValue(item.total, 0),
    succeededCount: numberValue(item.succeededCount ?? item.succeeded_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    items: listItems(item.items).map(normalizeTestDesignConflictBatchResolveItem)
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

export async function fetchTestDesignTaskQualitySummary(taskId: string): Promise<ApiResponse<TestDesignQualitySummaryView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/quality/summary`);
  return { ...response, data: normalizeTestDesignQualitySummary(response.data) };
}

export async function fetchTestDesignPromptTrend(
  filters: TestDesignPromptTrendFilters = {}
): Promise<ApiResponse<TestDesignPromptTrendView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/quality/prompt-trend${queryString(filters as Record<string, unknown>)}`);
  return { ...response, data: normalizeTestDesignPromptTrend(response.data) };
}

export async function fetchTestDesignTaskAuditSummary(taskId: string): Promise<ApiResponse<TestDesignAuditSummaryView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/report/audit-summary`);
  return { ...response, data: normalizeTestDesignAuditSummary(response.data) };
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

export async function resolveTestDesignConflict(
  candidateId: string,
  payload: ResolveTestDesignConflictPayload
): Promise<ApiResponse<TestDesignPublishRecordView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/candidates/${encodeURIComponent(candidateId)}/resolve-conflict`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignPublishRecord(response.data) };
}

export async function batchResolveTestDesignConflicts(
  payload: ResolveTestDesignConflictBatchPayload
): Promise<ApiResponse<TestDesignConflictBatchResolveResult>> {
  const response = await requestJson<unknown>('/api/v1/test-design/candidates/batch-resolve-conflicts', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignConflictBatchResolveResult(response.data) };
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

export function testDesignTaskReportExportPath(taskId: string) {
  return `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/report/export`;
}

export async function exportTestDesignTaskReportCsv(taskId: string): Promise<TextResponse> {
  return requestText(testDesignTaskReportExportPath(taskId));
}

export function testDesignErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
