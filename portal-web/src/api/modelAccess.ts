import { ApiError, getAuthToken, requestJson, requestText, type ApiResponse } from './client';

export const MODEL_PROVIDER_TYPES = ['LOCAL_ECHO', 'OPENAI_COMPATIBLE', 'MOCK_FAILURE'] as const;
export const MODEL_PROVIDER_STATUSES = ['ENABLED', 'DISABLED'] as const;
export const PROMPT_STATUSES = ['DRAFT', 'ACTIVE', 'ARCHIVED'] as const;
export const PROMPT_APPROVAL_STATUSES = ['NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED'] as const;
export const INVOCATION_STATUSES = ['SUCCEEDED', 'FAILED', 'BLOCKED'] as const;
export const MODEL_INVOCATION_JOB_STATUSES = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'] as const;
export const MODEL_POLICY_SCOPE_TYPES = ['PLATFORM', 'ROLE', 'PROJECT', 'ENVIRONMENT'] as const;
export const MODEL_POLICY_BUDGET_ACTIONS = ['BLOCK', 'FALLBACK'] as const;

export type ModelProviderType = (typeof MODEL_PROVIDER_TYPES)[number];
export type ModelProviderStatus = (typeof MODEL_PROVIDER_STATUSES)[number];
export type PromptStatus = (typeof PROMPT_STATUSES)[number];
export type PromptApprovalStatus = (typeof PROMPT_APPROVAL_STATUSES)[number];
export type InvocationStatus = (typeof INVOCATION_STATUSES)[number];
export type ModelInvocationJobStatus = (typeof MODEL_INVOCATION_JOB_STATUSES)[number];
export type ModelPolicyScopeType = (typeof MODEL_POLICY_SCOPE_TYPES)[number];
export type ModelPolicyBudgetAction = (typeof MODEL_POLICY_BUDGET_ACTIONS)[number];

export interface ModelAccessHealth {
  service: string;
  status: string;
  enabledProviders: number;
  activePrompts: number;
  providerRateLimitEnabled: boolean;
  providerRateLimitMaxRequests: number;
  providerRateLimitWindowSeconds: number;
  providerConcurrencyLimitEnabled: boolean;
  providerMaxConcurrentRequests: number;
  openCircuitProviders: number;
}

export interface ModelProviderConfig {
  id: string;
  name: string;
  providerType: ModelProviderType | string;
  routingGroup: string;
  capabilities: string;
  baseUrl?: string;
  apiKeyRef?: string;
  status: ModelProviderStatus | string;
  priority: number;
  timeoutMs: number;
  inputCostPer1kTokens: number;
  outputCostPer1kTokens: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ModelProviderPayload {
  name: string;
  providerType?: string;
  routingGroup?: string;
  capabilities?: string;
  baseUrl?: string;
  apiKeyRef?: string;
  priority?: number;
  timeoutMs?: number;
  inputCostPer1kTokens?: number;
  outputCostPer1kTokens?: number;
}

export interface ProviderCheckResponse {
  providerId: string;
  providerName: string;
  providerType: ModelProviderType | string;
  providerStatus: ModelProviderStatus | string;
  status: 'UP' | 'DOWN' | string;
  latencyMs: number;
  modelName?: string;
  errorCode?: string;
  errorMessage?: string;
  cached: boolean;
  checkedAt?: string;
}

export interface ProviderResilienceResponse {
  providerId: string;
  providerName: string;
  circuitOpen: boolean;
  consecutiveFailures: number;
  circuitOpenUntil?: string;
  rateLimitEnabled: boolean;
  rateLimitMaxRequests: number;
  rateLimitWindowSeconds: number;
  concurrencyLimitEnabled: boolean;
  maxConcurrentRequests: number;
  availableConcurrentPermits: number;
}

export interface PromptTemplate {
  id: string;
  promptKey: string;
  name: string;
  version: number;
  content: string;
  status: PromptStatus | string;
  changeNote?: string;
  highRisk: boolean;
  approvalStatus: PromptApprovalStatus | string;
  approvedBy?: string;
  approvedAt?: string;
  approvalNote?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PromptPayload {
  promptKey: string;
  name: string;
  content: string;
  changeNote?: string;
  highRisk?: boolean;
  activate?: boolean;
}

export interface PromptReviewPayload {
  reviewNote?: string;
}

export interface InvocationRecord {
  id: string;
  projectId?: string;
  applicationId?: string;
  environmentId?: string;
  sensitivityLevel?: string;
  promptKey?: string;
  promptVersion?: number;
  providerId?: string;
  providerName?: string;
  modelName?: string;
  routingRuleName?: string;
  routingGroup?: string;
  modelCapability?: string;
  status: InvocationStatus | string;
  fallbackUsed: boolean;
  promptDigest?: string;
  requestPreview?: string;
  responsePreview?: string;
  inputTokens: number;
  outputTokens: number;
  totalCost: number;
  errorCode?: string;
  errorMessage?: string;
  latencyMs: number;
  roleScope?: string;
  actorService?: string;
  delegatedUserId?: string;
  createdAt?: string;
}

export interface InvocationList {
  items: InvocationRecord[];
  total: number;
  page?: number;
  pageSize?: number;
}

export interface InvocationFilters {
  index?: number;
  size?: number;
  projectId?: string;
  applicationId?: string;
  environmentId?: string;
  sensitivityLevel?: string;
  status?: string;
  providerId?: string;
  actorService?: string;
  roleScope?: string;
  startTime?: string;
  endTime?: string;
}

export interface ModelAccessPolicy {
  id: string;
  scopeType: ModelPolicyScopeType | string;
  scopeKey: string;
  enabled: boolean;
  modelInvocationEnabled?: boolean;
  publicModelAllowed?: boolean;
  dailyBudgetLimit?: number;
  costAlertWarningRatio?: number;
  budgetOverrunAction?: ModelPolicyBudgetAction | string;
  routingGroup?: string;
  reason?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
  aggregateOnly?: boolean;
}

export interface ModelAccessPolicyPayload {
  scopeType: string;
  scopeKey?: string;
  enabled?: boolean;
  modelInvocationEnabled?: boolean;
  publicModelAllowed?: boolean;
  dailyBudgetLimit?: number;
  costAlertWarningRatio?: number;
  budgetOverrunAction?: string;
  routingGroup?: string;
  reason?: string;
}

export interface ModelAccessPolicyFilters {
  scopeType?: string;
  scopeKey?: string;
}

export interface ModelAccessEffectivePolicy {
  modelInvocationEnabled: boolean;
  publicModelAllowed: boolean;
  dailyBudgetLimit?: number;
  costAlertWarningRatio?: number;
  budgetOverrunAction?: ModelPolicyBudgetAction | string;
  routingGroup?: string;
  budgetScopeType?: string;
  budgetScopeKey?: string;
  roleScope?: string;
  matchedScopes: string[];
  aggregateOnly: boolean;
}

export interface ModelAccessEffectivePolicyFilters {
  projectId?: string;
  environmentId?: string;
  roles?: string;
}

export interface InvocationSummary {
  total: number;
  succeeded: number;
  failed: number;
  blocked: number;
  inputTokens: number;
  outputTokens: number;
  totalCost: number;
}

export interface InvokeModelPayload {
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  promptKey?: string;
  promptVariables?: Record<string, string>;
  messages: Array<{
    role: string;
    content: string;
  }>;
  providerId?: string;
  modelName?: string;
  allowPublicModel?: boolean;
  sensitivityLevel?: string;
  capability?: string;
}

export interface InvokeModelResponse {
  invocationId: string;
  providerId?: string;
  providerName?: string;
  modelName?: string;
  fallbackUsed: boolean;
  content: string;
  inputTokens: number;
  outputTokens: number;
  totalCost: number;
}

export interface ModelInvocationJob {
  jobId: string;
  status: ModelInvocationJobStatus | string;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
  invocationId?: string;
  errorCode?: string;
  errorMessage?: string;
  traceId?: string;
  response?: InvokeModelResponse;
}

export interface ModelQualityThresholds {
  minScenarioPassRate: number;
  minRequiredTermRecall: number;
  minForbiddenTermCleanRate: number;
}

export interface ModelQualityTaskStats {
  taskType: string;
  scenarioCount: number;
  passedScenarios: number;
  requiredTermCount: number;
  requiredTermMatches: number;
  forbiddenTermCount: number;
  forbiddenTermMatches: number;
  scenarioPassRate: number;
  requiredTermRecall: number;
  forbiddenTermCleanRate: number;
  passed: boolean;
  failures: string[];
}

export interface ModelQualityEvaluationSummary {
  corpusVersion: string;
  taskTypeFilter: string;
  scenarioCount: number;
  thresholds: ModelQualityThresholds;
  taskStats: ModelQualityTaskStats[];
  totalStats: ModelQualityTaskStats;
  promptBindings: string[];
  providerGroups: string[];
}

export type ModelStreamEvent =
  | {
      type: 'metadata';
      invocationId: string;
      providerId?: string;
      providerName?: string;
      modelName?: string;
      fallbackUsed: boolean;
      inputTokens: number;
      outputTokens: number;
      totalCost: number;
      traceId?: string;
    }
  | {
      type: 'delta';
      index: number;
      content: string;
    }
  | {
      type: 'done';
      invocationId: string;
      finishReason: string;
    };

export interface CostAlert {
  scope?: string;
  projectId?: string;
  actorService?: string;
  periodStart?: string;
  periodEnd?: string;
  spentCost: number;
  budgetLimit: number;
  usageRatio: number;
  level?: string;
  message?: string;
}

export interface CostReportRow {
  date?: string;
  projectId?: string;
  applicationId?: string;
  total: number;
  succeeded: number;
  failed: number;
  blocked: number;
  inputTokens: number;
  outputTokens: number;
  totalCost: number;
}

export interface CostReport {
  startDate?: string;
  endDate?: string;
  rows: CostReportRow[];
}

export interface CostReportFilters {
  startDate?: string;
  endDate?: string;
  projectId?: string;
}

export interface CostAlertFilters {
  projectId?: string;
  actorService?: string;
}

export function normalizeModelAccessHealth(raw: unknown): ModelAccessHealth {
  const value = record(raw);
  return {
    service: stringValue(value.service, 'model-access'),
    status: stringValue(value.status, 'UNKNOWN'),
    enabledProviders: numberValue(value.enabledProviders ?? value.enabled_providers),
    activePrompts: numberValue(value.activePrompts ?? value.active_prompts),
    providerRateLimitEnabled: booleanValue(value.providerRateLimitEnabled ?? value.provider_rate_limit_enabled),
    providerRateLimitMaxRequests: numberValue(value.providerRateLimitMaxRequests ?? value.provider_rate_limit_max_requests),
    providerRateLimitWindowSeconds: numberValue(value.providerRateLimitWindowSeconds ?? value.provider_rate_limit_window_seconds),
    providerConcurrencyLimitEnabled: booleanValue(value.providerConcurrencyLimitEnabled ?? value.provider_concurrency_limit_enabled),
    providerMaxConcurrentRequests: numberValue(value.providerMaxConcurrentRequests ?? value.provider_max_concurrent_requests),
    openCircuitProviders: numberValue(value.openCircuitProviders ?? value.open_circuit_providers)
  };
}

export function normalizeModelProvider(raw: unknown): ModelProviderConfig {
  const value = record(raw);
  return {
    id: stringValue(value.id ?? value.providerId ?? value.provider_id),
    name: stringValue(value.name ?? value.providerName ?? value.provider_name),
    providerType: enumValue(value.providerType ?? value.provider_type, MODEL_PROVIDER_TYPES, 'LOCAL_ECHO'),
    routingGroup: stringValue(value.routingGroup ?? value.routing_group, 'default'),
    capabilities: stringValue(value.capabilities, 'CHAT,TEXT,JSON,REQUIREMENT_PARSE'),
    baseUrl: optionalString(value.baseUrl ?? value.base_url),
    apiKeyRef: optionalString(value.apiKeyRef ?? value.api_key_ref),
    status: enumValue(value.status, MODEL_PROVIDER_STATUSES, 'DISABLED'),
    priority: numberValue(value.priority, 100),
    timeoutMs: numberValue(value.timeoutMs ?? value.timeout_ms, 10000),
    inputCostPer1kTokens: numberValue(value.inputCostPer1kTokens ?? value.input_cost_per_1k_tokens),
    outputCostPer1kTokens: numberValue(value.outputCostPer1kTokens ?? value.output_cost_per_1k_tokens),
    createdAt: optionalString(value.createdAt ?? value.created_at),
    updatedAt: optionalString(value.updatedAt ?? value.updated_at)
  };
}

export function normalizeProviderCheck(raw: unknown): ProviderCheckResponse {
  const value = record(raw);
  return {
    providerId: stringValue(value.providerId ?? value.provider_id),
    providerName: stringValue(value.providerName ?? value.provider_name),
    providerType: enumValue(value.providerType ?? value.provider_type, MODEL_PROVIDER_TYPES, 'LOCAL_ECHO'),
    providerStatus: enumValue(value.providerStatus ?? value.provider_status, MODEL_PROVIDER_STATUSES, 'DISABLED'),
    status: stringValue(value.status, 'UNKNOWN'),
    latencyMs: numberValue(value.latencyMs ?? value.latency_ms),
    modelName: optionalString(value.modelName ?? value.model_name),
    errorCode: optionalString(value.errorCode ?? value.error_code),
    errorMessage: optionalString(value.errorMessage ?? value.error_message),
    cached: booleanValue(value.cached),
    checkedAt: optionalString(value.checkedAt ?? value.checked_at)
  };
}

export function normalizeProviderResilience(raw: unknown): ProviderResilienceResponse {
  const value = record(raw);
  return {
    providerId: stringValue(value.providerId ?? value.provider_id),
    providerName: stringValue(value.providerName ?? value.provider_name),
    circuitOpen: booleanValue(value.circuitOpen ?? value.circuit_open),
    consecutiveFailures: numberValue(value.consecutiveFailures ?? value.consecutive_failures),
    circuitOpenUntil: optionalString(value.circuitOpenUntil ?? value.circuit_open_until),
    rateLimitEnabled: booleanValue(value.rateLimitEnabled ?? value.rate_limit_enabled),
    rateLimitMaxRequests: numberValue(value.rateLimitMaxRequests ?? value.rate_limit_max_requests),
    rateLimitWindowSeconds: numberValue(value.rateLimitWindowSeconds ?? value.rate_limit_window_seconds),
    concurrencyLimitEnabled: booleanValue(value.concurrencyLimitEnabled ?? value.concurrency_limit_enabled),
    maxConcurrentRequests: numberValue(value.maxConcurrentRequests ?? value.max_concurrent_requests),
    availableConcurrentPermits: numberValue(value.availableConcurrentPermits ?? value.available_concurrent_permits)
  };
}

export function normalizePromptTemplate(raw: unknown): PromptTemplate {
  const value = record(raw);
  return {
    id: stringValue(value.id),
    promptKey: stringValue(value.promptKey ?? value.prompt_key),
    name: stringValue(value.name),
    version: numberValue(value.version, 1),
    content: stringValue(value.content),
    status: enumValue(value.status, PROMPT_STATUSES, 'DRAFT'),
    changeNote: optionalString(value.changeNote ?? value.change_note),
    highRisk: booleanValue(value.highRisk ?? value.high_risk),
    approvalStatus: enumValue(value.approvalStatus ?? value.approval_status, PROMPT_APPROVAL_STATUSES, 'NOT_REQUIRED'),
    approvedBy: optionalString(value.approvedBy ?? value.approved_by),
    approvedAt: optionalString(value.approvedAt ?? value.approved_at),
    approvalNote: optionalString(value.approvalNote ?? value.approval_note),
    createdAt: optionalString(value.createdAt ?? value.created_at),
    updatedAt: optionalString(value.updatedAt ?? value.updated_at)
  };
}

export function normalizeInvocationRecord(raw: unknown): InvocationRecord {
  const value = record(raw);
  return {
    id: stringValue(value.id ?? value.invocationId ?? value.invocation_id),
    projectId: optionalString(value.projectId ?? value.project_id),
    applicationId: optionalString(value.applicationId ?? value.application_id),
    environmentId: optionalString(value.environmentId ?? value.environment_id),
    sensitivityLevel: optionalString(value.sensitivityLevel ?? value.sensitivity_level),
    promptKey: optionalString(value.promptKey ?? value.prompt_key),
    promptVersion: optionalNumber(value.promptVersion ?? value.prompt_version),
    providerId: optionalString(value.providerId ?? value.provider_id),
    providerName: optionalString(value.providerName ?? value.provider_name),
    modelName: optionalString(value.modelName ?? value.model_name),
    routingRuleName: optionalString(value.routingRuleName ?? value.routing_rule_name),
    routingGroup: optionalString(value.routingGroup ?? value.routing_group),
    modelCapability: optionalString(value.modelCapability ?? value.model_capability),
    status: enumValue(value.status, INVOCATION_STATUSES, 'FAILED'),
    fallbackUsed: booleanValue(value.fallbackUsed ?? value.fallback_used),
    promptDigest: optionalString(value.promptDigest ?? value.prompt_digest),
    requestPreview: optionalString(value.requestPreview ?? value.request_preview),
    responsePreview: optionalString(value.responsePreview ?? value.response_preview),
    inputTokens: numberValue(value.inputTokens ?? value.input_tokens),
    outputTokens: numberValue(value.outputTokens ?? value.output_tokens),
    totalCost: numberValue(value.totalCost ?? value.total_cost),
    errorCode: optionalString(value.errorCode ?? value.error_code),
    errorMessage: optionalString(value.errorMessage ?? value.error_message),
    latencyMs: numberValue(value.latencyMs ?? value.latency_ms),
    roleScope: optionalString(value.roleScope ?? value.role_scope),
    actorService: optionalString(value.actorService ?? value.actor_service),
    delegatedUserId: optionalString(value.delegatedUserId ?? value.delegated_user_id),
    createdAt: optionalString(value.createdAt ?? value.created_at)
  };
}

export function normalizeModelAccessPolicy(raw: unknown): ModelAccessPolicy {
  const value = record(raw);
  return {
    id: stringValue(value.id),
    scopeType: enumValue(value.scopeType ?? value.scope_type, MODEL_POLICY_SCOPE_TYPES, 'PLATFORM'),
    scopeKey: stringValue(value.scopeKey ?? value.scope_key),
    enabled: booleanValue(value.enabled ?? true),
    modelInvocationEnabled: optionalBoolean(value.modelInvocationEnabled ?? value.model_invocation_enabled),
    publicModelAllowed: optionalBoolean(value.publicModelAllowed ?? value.public_model_allowed),
    dailyBudgetLimit: optionalNumber(value.dailyBudgetLimit ?? value.daily_budget_limit),
    costAlertWarningRatio: optionalNumber(value.costAlertWarningRatio ?? value.cost_alert_warning_ratio),
    budgetOverrunAction: optionalEnumValue(value.budgetOverrunAction ?? value.budget_overrun_action, MODEL_POLICY_BUDGET_ACTIONS),
    routingGroup: optionalString(value.routingGroup ?? value.routing_group),
    reason: optionalString(value.reason),
    updatedBy: optionalString(value.updatedBy ?? value.updated_by),
    createdAt: optionalString(value.createdAt ?? value.created_at),
    updatedAt: optionalString(value.updatedAt ?? value.updated_at),
    aggregateOnly: booleanValue(value.aggregateOnly ?? value.aggregate_only)
  };
}

export function normalizeModelAccessEffectivePolicy(raw: unknown): ModelAccessEffectivePolicy {
  const value = record(raw);
  return {
    modelInvocationEnabled: booleanValue(value.modelInvocationEnabled ?? value.model_invocation_enabled ?? true),
    publicModelAllowed: booleanValue(value.publicModelAllowed ?? value.public_model_allowed ?? true),
    dailyBudgetLimit: optionalNumber(value.dailyBudgetLimit ?? value.daily_budget_limit),
    costAlertWarningRatio: optionalNumber(value.costAlertWarningRatio ?? value.cost_alert_warning_ratio),
    budgetOverrunAction: optionalEnumValue(value.budgetOverrunAction ?? value.budget_overrun_action, MODEL_POLICY_BUDGET_ACTIONS),
    routingGroup: optionalString(value.routingGroup ?? value.routing_group),
    budgetScopeType: optionalString(value.budgetScopeType ?? value.budget_scope_type),
    budgetScopeKey: optionalString(value.budgetScopeKey ?? value.budget_scope_key),
    roleScope: optionalString(value.roleScope ?? value.role_scope),
    matchedScopes: listItems(value.matchedScopes ?? value.matched_scopes).map((item) => stringValue(item)).filter(Boolean),
    aggregateOnly: booleanValue(value.aggregateOnly ?? value.aggregate_only)
  };
}

export function normalizeInvocationList(raw: unknown): InvocationList {
  const value = record(raw);
  const items = listItems(raw).map(normalizeInvocationRecord);
  return {
    items,
    total: numberValue(value.total ?? value.totalElements ?? value.total_elements, items.length),
    page: optionalNumber(value.page ?? value.index),
    pageSize: optionalNumber(value.pageSize ?? value.size)
  };
}

export function normalizeInvocationSummary(raw: unknown): InvocationSummary {
  const value = record(raw);
  return {
    total: numberValue(value.total),
    succeeded: numberValue(value.succeeded),
    failed: numberValue(value.failed),
    blocked: numberValue(value.blocked),
    inputTokens: numberValue(value.inputTokens ?? value.input_tokens),
    outputTokens: numberValue(value.outputTokens ?? value.output_tokens),
    totalCost: numberValue(value.totalCost ?? value.total_cost)
  };
}

export function normalizeInvokeModelResponse(raw: unknown): InvokeModelResponse {
  const value = record(raw);
  return {
    invocationId: stringValue(value.invocationId ?? value.invocation_id),
    providerId: optionalString(value.providerId ?? value.provider_id),
    providerName: optionalString(value.providerName ?? value.provider_name),
    modelName: optionalString(value.modelName ?? value.model_name),
    fallbackUsed: booleanValue(value.fallbackUsed ?? value.fallback_used),
    content: stringValue(value.content),
    inputTokens: numberValue(value.inputTokens ?? value.input_tokens),
    outputTokens: numberValue(value.outputTokens ?? value.output_tokens),
    totalCost: numberValue(value.totalCost ?? value.total_cost)
  };
}

export function normalizeModelInvocationJob(raw: unknown): ModelInvocationJob {
  const value = record(raw);
  const response = value.response === undefined || value.response === null
    ? undefined
    : normalizeInvokeModelResponse(value.response);
  return {
    jobId: stringValue(value.jobId ?? value.job_id),
    status: enumValue(value.status, MODEL_INVOCATION_JOB_STATUSES, 'FAILED'),
    createdAt: optionalString(value.createdAt ?? value.created_at),
    startedAt: optionalString(value.startedAt ?? value.started_at),
    finishedAt: optionalString(value.finishedAt ?? value.finished_at),
    invocationId: optionalString(value.invocationId ?? value.invocation_id),
    errorCode: optionalString(value.errorCode ?? value.error_code),
    errorMessage: optionalString(value.errorMessage ?? value.error_message),
    traceId: optionalString(value.traceId ?? value.trace_id),
    response
  };
}

export function normalizeModelQualityTaskStats(raw: unknown): ModelQualityTaskStats {
  const value = record(raw);
  return {
    taskType: stringValue(value.taskType ?? value.task_type, 'ALL'),
    scenarioCount: numberValue(value.scenarioCount ?? value.scenario_count),
    passedScenarios: numberValue(value.passedScenarios ?? value.passed_scenarios),
    requiredTermCount: numberValue(value.requiredTermCount ?? value.required_term_count),
    requiredTermMatches: numberValue(value.requiredTermMatches ?? value.required_term_matches),
    forbiddenTermCount: numberValue(value.forbiddenTermCount ?? value.forbidden_term_count),
    forbiddenTermMatches: numberValue(value.forbiddenTermMatches ?? value.forbidden_term_matches),
    scenarioPassRate: numberValue(value.scenarioPassRate ?? value.scenario_pass_rate),
    requiredTermRecall: numberValue(value.requiredTermRecall ?? value.required_term_recall),
    forbiddenTermCleanRate: numberValue(value.forbiddenTermCleanRate ?? value.forbidden_term_clean_rate),
    passed: booleanValue(value.passed),
    failures: listItems(value.failures).map((item) => stringValue(item)).filter(Boolean)
  };
}

export function normalizeModelQualityEvaluationSummary(raw: unknown): ModelQualityEvaluationSummary {
  const value = record(raw);
  return {
    corpusVersion: stringValue(value.corpusVersion ?? value.corpus_version),
    taskTypeFilter: stringValue(value.taskTypeFilter ?? value.task_type_filter, 'ALL'),
    scenarioCount: numberValue(value.scenarioCount ?? value.scenario_count),
    thresholds: {
      minScenarioPassRate: numberValue(record(value.thresholds).minScenarioPassRate ?? record(value.thresholds).min_scenario_pass_rate),
      minRequiredTermRecall: numberValue(record(value.thresholds).minRequiredTermRecall ?? record(value.thresholds).min_required_term_recall),
      minForbiddenTermCleanRate: numberValue(record(value.thresholds).minForbiddenTermCleanRate ?? record(value.thresholds).min_forbidden_term_clean_rate)
    },
    taskStats: listItems(value.taskStats ?? value.task_stats).map(normalizeModelQualityTaskStats),
    totalStats: normalizeModelQualityTaskStats(value.totalStats ?? value.total_stats),
    promptBindings: listItems(value.promptBindings ?? value.prompt_bindings).map((item) => stringValue(item)).filter(Boolean),
    providerGroups: listItems(value.providerGroups ?? value.provider_groups).map((item) => stringValue(item)).filter(Boolean)
  };
}

export function normalizeCostAlert(raw: unknown): CostAlert {
  const value = record(raw);
  return {
    scope: optionalString(value.scope),
    projectId: optionalString(value.projectId ?? value.project_id),
    actorService: optionalString(value.actorService ?? value.actor_service),
    periodStart: optionalString(value.periodStart ?? value.period_start),
    periodEnd: optionalString(value.periodEnd ?? value.period_end),
    spentCost: numberValue(value.spentCost ?? value.spent_cost),
    budgetLimit: numberValue(value.budgetLimit ?? value.budget_limit),
    usageRatio: numberValue(value.usageRatio ?? value.usage_ratio),
    level: optionalString(value.level),
    message: optionalString(value.message)
  };
}

export function normalizeCostReport(raw: unknown): CostReport {
  const value = record(raw);
  return {
    startDate: optionalString(value.startDate ?? value.start_date),
    endDate: optionalString(value.endDate ?? value.end_date),
    rows: listItems(value.rows).map((item) => {
      const row = record(item);
      return {
        date: optionalString(row.date),
        projectId: optionalString(row.projectId ?? row.project_id),
        applicationId: optionalString(row.applicationId ?? row.application_id),
        total: numberValue(row.total),
        succeeded: numberValue(row.succeeded),
        failed: numberValue(row.failed),
        blocked: numberValue(row.blocked),
        inputTokens: numberValue(row.inputTokens ?? row.input_tokens),
        outputTokens: numberValue(row.outputTokens ?? row.output_tokens),
        totalCost: numberValue(row.totalCost ?? row.total_cost)
      };
    })
  };
}

export function modelProviderItems(data: unknown): ModelProviderConfig[] {
  return listItems(data).map(normalizeModelProvider);
}

export function promptTemplateItems(data: unknown): PromptTemplate[] {
  return listItems(data).map(normalizePromptTemplate);
}

export function costAlertItems(data: unknown): CostAlert[] {
  return listItems(data).map(normalizeCostAlert);
}

export function modelAccessPolicyItems(data: unknown): ModelAccessPolicy[] {
  return listItems(data).map(normalizeModelAccessPolicy);
}

export function modelAccessQueryPath(base: string, filters: object = {}) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value === undefined || value === null) {
      continue;
    }
    const normalized = typeof value === 'string' ? value.trim() : String(value);
    if (normalized) {
      params.set(key, normalized);
    }
  }
  const query = params.toString();
  return query ? `${base}?${query}` : base;
}

export async function fetchModelAccessHealth(): Promise<ApiResponse<ModelAccessHealth>> {
  const response = await requestJson<unknown>('/api/v1/model-access/health');
  return { ...response, data: normalizeModelAccessHealth(response.data) };
}

export async function fetchModelProviders(): Promise<ApiResponse<ModelProviderConfig[]>> {
  const response = await requestJson<unknown>('/api/v1/model-access/providers');
  return { ...response, data: modelProviderItems(response.data) };
}

export async function createModelProvider(payload: ModelProviderPayload): Promise<ApiResponse<ModelProviderConfig>> {
  const response = await requestJson<unknown>('/api/v1/model-access/providers', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeModelProvider(response.data) };
}

export async function updateModelProvider(id: string, payload: ModelProviderPayload): Promise<ApiResponse<ModelProviderConfig>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/providers/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeModelProvider(response.data) };
}

export async function enableModelProvider(id: string): Promise<ApiResponse<ModelProviderConfig>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/providers/${encodeURIComponent(id)}/enable`, {
    method: 'POST'
  });
  return { ...response, data: normalizeModelProvider(response.data) };
}

export async function disableModelProvider(id: string): Promise<ApiResponse<ModelProviderConfig>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/providers/${encodeURIComponent(id)}/disable`, {
    method: 'POST'
  });
  return { ...response, data: normalizeModelProvider(response.data) };
}

export async function checkModelProvider(id: string): Promise<ApiResponse<ProviderCheckResponse>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/providers/${encodeURIComponent(id)}/check`, {
    method: 'POST'
  });
  return { ...response, data: normalizeProviderCheck(response.data) };
}

export async function fetchProviderResilience(id: string): Promise<ApiResponse<ProviderResilienceResponse>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/providers/${encodeURIComponent(id)}/resilience`);
  return { ...response, data: normalizeProviderResilience(response.data) };
}

export async function resetProviderCircuit(id: string): Promise<ApiResponse<ProviderResilienceResponse>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/providers/${encodeURIComponent(id)}/circuit/reset`, {
    method: 'POST'
  });
  return { ...response, data: normalizeProviderResilience(response.data) };
}

export async function fetchPrompts(promptKey?: string): Promise<ApiResponse<PromptTemplate[]>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/prompts', { promptKey }));
  return { ...response, data: promptTemplateItems(response.data) };
}

export async function createPromptVersion(payload: PromptPayload): Promise<ApiResponse<PromptTemplate>> {
  const response = await requestJson<unknown>('/api/v1/model-access/prompts', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizePromptTemplate(response.data) };
}

export async function activatePromptVersion(id: string): Promise<ApiResponse<PromptTemplate>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/prompts/${encodeURIComponent(id)}/activate`, {
    method: 'POST'
  });
  return { ...response, data: normalizePromptTemplate(response.data) };
}

export async function approvePromptVersion(id: string, payload: PromptReviewPayload = {}): Promise<ApiResponse<PromptTemplate>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/prompts/${encodeURIComponent(id)}/approve`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizePromptTemplate(response.data) };
}

export async function rejectPromptVersion(id: string, payload: PromptReviewPayload = {}): Promise<ApiResponse<PromptTemplate>> {
  const response = await requestJson<unknown>(`/api/v1/model-access/prompts/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizePromptTemplate(response.data) };
}

export async function fetchModelAccessPolicies(filters: ModelAccessPolicyFilters = {}): Promise<ApiResponse<ModelAccessPolicy[]>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/policies', filters));
  return { ...response, data: modelAccessPolicyItems(response.data) };
}

export async function upsertModelAccessPolicy(payload: ModelAccessPolicyPayload): Promise<ApiResponse<ModelAccessPolicy>> {
  const response = await requestJson<unknown>('/api/v1/model-access/policies', {
    method: 'PUT',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeModelAccessPolicy(response.data) };
}

export async function fetchEffectiveModelAccessPolicy(
  filters: ModelAccessEffectivePolicyFilters = {}
): Promise<ApiResponse<ModelAccessEffectivePolicy>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/policies/effective', filters));
  return { ...response, data: normalizeModelAccessEffectivePolicy(response.data) };
}

export async function fetchInvocations(filters: InvocationFilters = {}): Promise<ApiResponse<InvocationList>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/invocations', filters));
  return { ...response, data: normalizeInvocationList(response.data) };
}

export async function fetchInvocationSummary(filters: InvocationFilters = {}): Promise<ApiResponse<InvocationSummary>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/invocations/summary', filters));
  return { ...response, data: normalizeInvocationSummary(response.data) };
}

export async function invokeModel(payload: InvokeModelPayload): Promise<ApiResponse<InvokeModelResponse>> {
  const response = await requestJson<unknown>('/api/v1/model-access/invocations', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeInvokeModelResponse(response.data) };
}

export function invocationExportPath(filters: InvocationFilters = {}) {
  return modelAccessQueryPath('/api/v1/model-access/invocations/export', { ...filters, index: undefined, size: undefined });
}

export async function exportInvocationsCsv(filters: InvocationFilters = {}) {
  return requestText(invocationExportPath(filters));
}

export function invocationStreamPath() {
  return '/api/v1/model-access/invocations/stream';
}

export function invocationJobPath(jobId?: string) {
  if (!jobId) {
    return '/api/v1/model-access/invocations/jobs';
  }
  return `/api/v1/model-access/invocations/jobs/${encodeURIComponent(jobId)}`;
}

export async function submitModelInvocationJob(payload: InvokeModelPayload): Promise<ApiResponse<ModelInvocationJob>> {
  const response = await requestJson<unknown>(invocationJobPath(), {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeModelInvocationJob(response.data) };
}

export async function fetchModelInvocationJob(jobId: string): Promise<ApiResponse<ModelInvocationJob>> {
  const response = await requestJson<unknown>(invocationJobPath(jobId));
  return { ...response, data: normalizeModelInvocationJob(response.data) };
}

export async function cancelModelInvocationJob(jobId: string): Promise<ApiResponse<ModelInvocationJob>> {
  const response = await requestJson<unknown>(`${invocationJobPath(jobId)}/cancel`, {
    method: 'POST'
  });
  return { ...response, data: normalizeModelInvocationJob(response.data) };
}

export async function fetchModelQualityEvaluationSummary(
  taskType?: string
): Promise<ApiResponse<ModelQualityEvaluationSummary>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/quality/evaluation-summary', { taskType }));
  return { ...response, data: normalizeModelQualityEvaluationSummary(response.data) };
}

export function parseModelStreamEvents(text: string): ModelStreamEvent[] {
  return text
    .split(/\r?\n\r?\n/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map(parseModelStreamEvent)
    .filter((event): event is ModelStreamEvent => event !== undefined);
}

export async function invokeModelStream(
  payload: InvokeModelPayload,
  onEvent?: (event: ModelStreamEvent) => void
): Promise<ModelStreamEvent[]> {
  const headers = new Headers({ 'Content-Type': 'application/json', Accept: 'text/event-stream' });
  const token = getAuthToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(invocationStreamPath(), {
    method: 'POST',
    headers,
    body: JSON.stringify(compactPayload(payload))
  });
  if (response.status === 401 && token) {
    throw new ApiError('登录已过期，请重新登录', 'SESSION_EXPIRED', '', 401);
  }
  if (!response.ok) {
    throw await streamApiError(response);
  }
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType && !contentType.toLowerCase().includes('text/event-stream')) {
    throw new ApiError(
      '流式模型调用返回类型异常',
      'INVALID_STREAM_RESPONSE',
      response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  }

  const events: ModelStreamEvent[] = [];
  const pushEvents = (chunk: string) => {
    const parsed = parseModelStreamEvents(chunk);
    parsed.forEach((event) => {
      events.push(event);
      onEvent?.(event);
    });
  };

  if (!response.body) {
    pushEvents(await response.text());
    return events;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
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
  return events;
}

export async function fetchCostAlerts(filters: CostAlertFilters = {}): Promise<ApiResponse<CostAlert[]>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/cost/alerts', filters));
  return { ...response, data: costAlertItems(response.data) };
}

export async function fetchCostReport(filters: CostReportFilters = {}): Promise<ApiResponse<CostReport>> {
  const response = await requestJson<unknown>(modelAccessQueryPath('/api/v1/model-access/cost/report', filters));
  return { ...response, data: normalizeCostReport(response.data) };
}

function compactPayload<T extends object>(payload: T) {
  return Object.fromEntries(
    Object.entries(payload as Record<string, unknown>).filter(([, value]) => {
      if (value === undefined || value === null) {
        return false;
      }
      return typeof value !== 'string' || value.trim() !== '';
    })
  );
}

function parseModelStreamEvent(block: string): ModelStreamEvent | undefined {
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
    data = record(JSON.parse(dataLines.join('\n')));
  } catch {
    return undefined;
  }
  if (type === 'metadata') {
    return {
      type,
      invocationId: stringValue(data.invocationId ?? data.invocation_id),
      providerId: optionalString(data.providerId ?? data.provider_id),
      providerName: optionalString(data.providerName ?? data.provider_name),
      modelName: optionalString(data.modelName ?? data.model_name),
      fallbackUsed: booleanValue(data.fallbackUsed ?? data.fallback_used),
      inputTokens: numberValue(data.inputTokens ?? data.input_tokens),
      outputTokens: numberValue(data.outputTokens ?? data.output_tokens),
      totalCost: numberValue(data.totalCost ?? data.total_cost),
      traceId: optionalString(data.traceId ?? data.trace_id)
    };
  }
  if (type === 'delta') {
    return {
      type,
      index: numberValue(data.index),
      content: stringValue(data.content)
    };
  }
  if (type === 'done') {
    return {
      type,
      invocationId: stringValue(data.invocationId ?? data.invocation_id),
      finishReason: stringValue(data.finishReason ?? data.finish_reason)
    };
  }
  return undefined;
}

async function streamApiError(response: Response) {
  const text = await response.text().catch(() => '');
  try {
    const body = JSON.parse(text) as { message?: string; code?: string; trace_id?: string; traceId?: string; data?: unknown };
    return new ApiError(
      body.message || '流式模型调用失败',
      body.code || `HTTP_${response.status}`,
      body.trace_id ?? body.traceId ?? response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  } catch {
    return new ApiError(
      text || '流式模型调用失败',
      `HTTP_${response.status}`,
      response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  }
}

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function listItems(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  const data = record(value);
  if (Array.isArray(data.items)) {
    return data.items;
  }
  if (Array.isArray(data.content)) {
    return data.content;
  }
  if (Array.isArray(data.rows)) {
    return data.rows;
  }
  if (Array.isArray(data.data)) {
    return data.data;
  }
  return [];
}

function stringValue(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function optionalString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function numberValue(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function optionalNumber(value: unknown) {
  const parsed = numberValue(value, Number.NaN);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function booleanValue(value: unknown) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    return value.trim().toLowerCase() === 'true';
  }
  return false;
}

function optionalBoolean(value: unknown) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') {
      return true;
    }
    if (normalized === 'false') {
      return false;
    }
  }
  return undefined;
}

function enumValue<T extends readonly string[]>(value: unknown, allowed: T, fallback: T[number]): T[number] | string {
  if (typeof value !== 'string' || !value.trim()) {
    return fallback;
  }
  const normalized = value.trim().toUpperCase();
  return allowed.includes(normalized as T[number]) ? normalized as T[number] : value.trim();
}

function optionalEnumValue<T extends readonly string[]>(value: unknown, allowed: T): T[number] | string | undefined {
  if (typeof value !== 'string' || !value.trim()) {
    return undefined;
  }
  const normalized = value.trim().toUpperCase();
  return allowed.includes(normalized as T[number]) ? normalized as T[number] : value.trim();
}
