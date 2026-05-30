import type { TestDesignTaskView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import { generationSourceText, taskGenerationSource } from './testDesignGenerationSource';

export type TestDesignTaskDiagnosticTone = 'neutral' | 'warning' | 'danger';

export type TestDesignTaskDiagnosticItem = {
  label: string;
  value: string;
  tone?: TestDesignTaskDiagnosticTone;
};

const SENSITIVE_KEY_PATTERN =
  /(api[-_]?key|access[-_]?key|secret|token|password|passwd|pwd|cookie|private[-_]?key|authorization|credential)/i;

export function buildTestDesignTaskDiagnostics(task: TestDesignTaskView | null | undefined): TestDesignTaskDiagnosticItem[] {
  if (!task) {
    return [];
  }
  const generationSource = taskGenerationSource(task);

  return [
    { label: '任务 ID', value: compactTestDesignDigest(task.id, 10, 6) },
    { label: '项目', value: compactTestDesignDigest(task.projectId, 10, 6) },
    { label: '状态', value: displayDiagnosticText(task.status) },
    { label: '需求数', value: String(task.totalRequirements ?? task.requirementIds.length) },
    { label: '覆盖', value: formatList(task.coverageTypes) },
    { label: '产出', value: `${task.generatedCount} 生成 / ${task.confirmedCount} 确认 / ${task.publishedCount} 发布` },
    {
      label: '生成来源',
      value: generationSourceText(generationSource),
      tone: generationSource.tone === 'warning' ? 'warning' : 'neutral'
    },
    { label: 'Prompt', value: formatPrompt(task) },
    { label: '模型', value: formatModel(task) },
    { label: '模型调用', value: compactTestDesignDigest(task.modelInvocationId, 12, 8) },
    {
      label: '调用观测',
      value: formatModelObservation(task),
      tone: modelObservationTone(task)
    },
    { label: '调用链路', value: compactTestDesignDigest(task.modelObservation?.traceId, 12, 8) },
    { label: '调用任务', value: compactTestDesignDigest(task.modelObservation?.jobId, 12, 8) },
    { label: '输入摘要', value: compactTestDesignDigest(task.inputDigest, 12, 8) },
    { label: '幂等键', value: compactTestDesignDigest(task.idempotencyKey, 14, 8) },
    { label: '上下文', value: summarizeTestDesignTaskContext(task.contextSummary) },
    { label: '上下文策略', value: summarizeTestDesignContextPolicy(task.contextSummary) },
    {
      label: '装配策略',
      value: summarizeTestDesignContextAssemblyPolicy(task),
      tone: contextAssemblyPolicyTone(task)
    },
    {
      label: '策略治理',
      value: summarizeTestDesignContextPolicyGovernance(task),
      tone: contextPolicyGovernanceTone(task)
    },
    {
      label: '策略运营',
      value: summarizeTestDesignContextPolicyOperations(task),
      tone: contextPolicyOperationsTone(task)
    },
    {
      label: '作用域策略',
      value: summarizeTestDesignScopePolicy(task),
      tone: scopePolicyTone(task)
    },
    {
      label: '评测语料',
      value: summarizeTestDesignEvaluationCorpusPolicy(task),
      tone: evaluationCorpusPolicyTone(task)
    },
    {
      label: '发布准出',
      value: summarizeTestDesignReleaseReadinessPolicy(task),
      tone: releaseReadinessPolicyTone(task)
    },
    {
      label: '审计链',
      value: summarizeTestDesignAuditChainPolicy(task),
      tone: auditChainPolicyTone(task)
    },
    { label: '请求人', value: displayDiagnosticText(task.requestedBy) },
    { label: '创建', value: formatDateTime(task.createdAt) },
    { label: '更新', value: formatDateTime(task.updatedAt) },
    {
      label: '错误',
      value: displayDiagnosticText(task.errorMessage, 96),
      tone: task.errorMessage ? 'danger' : 'neutral'
    }
  ];
}

export function compactTestDesignDigest(value?: string, prefix = 12, suffix = 8): string {
  const text = sanitizeDiagnosticText(value);
  if (!text) {
    return '-';
  }
  if (text.length <= prefix + suffix + 3) {
    return text;
  }
  return `${text.slice(0, prefix)}...${text.slice(-suffix)}`;
}

export function summarizeTestDesignTaskContext(contextSummary: Record<string, unknown> | null | undefined): string {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return '-';
  }

  const safeKeys = Object.keys(contextSummary).filter((key) => !SENSITIVE_KEY_PATTERN.test(key));
  if (!safeKeys.length) {
    return '-';
  }

  const parts: string[] = [];
  const version = firstSafeScalar(contextSummary, ['contextVersion', 'version', 'schemaVersion']);
  if (version) {
    parts.push(`version:${displayDiagnosticText(version, 24)}`);
  }

  appendContextCount(parts, contextSummary, 'requirements', ['requirements', 'requirementSummaries']);
  appendContextCount(parts, contextSummary, 'sources', ['documentSources', 'sources', 'sourceRefs']);
  appendContextCount(parts, contextSummary, 'history', ['historicalCases', 'existingCases', 'cases']);
  appendContextCount(parts, contextSummary, 'apis', ['apis', 'apiSummaries', 'interfaces']);
  appendContextCount(parts, contextSummary, 'pages', ['pages', 'pageSummaries']);
  appendContextCount(parts, contextSummary, 'flows', ['flows', 'businessFlows']);
  appendExplicitAssetCounts(parts, contextSummary);

  const keyPreview = safeKeys.slice(0, 5).join(', ');
  parts.push(`keys:${keyPreview}${safeKeys.length > 5 ? ` +${safeKeys.length - 5}` : ''}`);

  return parts.join(' · ');
}

export function summarizeTestDesignContextPolicy(contextSummary: Record<string, unknown> | null | undefined): string {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return '-';
  }
  const limits = contextSummary.limits;
  if (!limits || typeof limits !== 'object' || Array.isArray(limits)) {
    return '-';
  }

  const record = limits as Record<string, unknown>;
  const parts = [
    contextPolicyPart(record, 'linkedAssetsPerRequirement', '关联资产'),
    contextPolicyPart(record, 'explicitAssetsPerType', '显式资产'),
    contextPolicyPart(record, 'existingCasesPerRequirement', '历史用例'),
    contextPolicyPart(record, 'requirementDescriptionChars', '需求描述'),
    contextPolicyPart(record, 'acceptanceCriteriaChars', '验收标准'),
    contextPolicyPart(record, 'linkedAssetSchemaChars', '资产摘要')
  ].filter(Boolean);

  return parts.length ? parts.join(' · ') : '-';
}

export function summarizeTestDesignContextAssemblyPolicy(
  task: TestDesignTaskView | null | undefined
): string {
  const policy = task?.contextAssemblyPolicy ?? assemblyPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const mode = displayDiagnosticText(policy.assemblyMode, 40);
  const digest = displayDiagnosticText(policy.digestStrategy, 40);
  const digestRequired = policy.inputDigestRequired === true ? '摘要:required' : '摘要:optional';
  const summaryOnly = policy.persistedContextSummaryOnly === true ? '仅摘要:yes' : '仅摘要:no';
  const wp3Boundary = policy.wp3ApplicationServiceOnly === true ? 'WP3应用服务:yes' : 'WP3应用服务:no';
  const rawStored = policy.rawContextBodyStored === true ? '原文持久化:on' : '原文持久化:off';
  const modelPayload = policy.modelPayloadStored === true ? '模型载荷持久化:on' : '模型载荷持久化:off';
  const detailExport = anyAssemblyDetailExported(policy) ? '细节导出:on' : '细节导出:off';
  return [mode, digest, digestRequired, summaryOnly, wp3Boundary, rawStored, modelPayload, detailExport]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignContextPolicyGovernance(
  task: TestDesignTaskView | null | undefined
): string {
  const governance = task?.contextPolicyGovernance ?? governanceFromContextSummary(task?.contextSummary);
  if (!governance) {
    return '-';
  }

  const source = displayDiagnosticText(governance.policySource, 32);
  const status = displayDiagnosticText(governance.governanceStatus, 40);
  const mode = displayDiagnosticText(governance.changeMode, 32);
  const projectOverride = governance.projectOverrideSupported === true ? '项目覆盖:on' : '项目覆盖:off';
  const envOverride = governance.environmentOverrideSupported === true ? '环境覆盖:on' : '环境覆盖:off';
  const approval = governance.changeApprovalWorkflowReady === true ? '审批流:ready' : '审批流:pending';
  return [source, status, mode, projectOverride, envOverride, approval]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignContextPolicyOperations(
  task: TestDesignTaskView | null | undefined
): string {
  const operations = task?.contextPolicyOperations ?? operationsFromContextSummary(task?.contextSummary);
  if (!operations) {
    return '-';
  }

  const mode = displayDiagnosticText(operations.operationMode, 32);
  const resolution = displayDiagnosticText(operations.policyResolutionOrder, 40);
  const fallback = displayDiagnosticText(operations.policyFallbackBehavior, 40);
  const approvalStatus = displayDiagnosticText(operations.approvalStatus, 32);
  const projectStore = operations.projectOverrideStoreReady === true ? '项目覆盖存储:ready' : '项目覆盖存储:pending';
  const envStore = operations.environmentOverrideStoreReady === true ? '环境覆盖存储:ready' : '环境覆盖存储:pending';
  const approval = operations.changeApprovalWorkflowReady === true ? '审批流:ready' : '审批流:pending';
  return [mode, resolution, fallback, approvalStatus, projectStore, envStore, approval]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignScopePolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.scopePolicy ?? scopePolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const model = displayDiagnosticText(policy.scopeModel, 40);
  const listScope = displayDiagnosticText(policy.listFallbackScope, 44);
  const taskScope = policy.taskProjectScopeRequired === true ? '任务:project' : '任务:platform';
  const candidateScope = policy.candidateProjectScopeRequired === true ? '候选:project' : '候选:platform';
  const batchScope = policy.batchCandidateProjectScopeRequired === true ? '批量:project-set' : '批量:platform';
  const publishScope = policy.publishProjectScopeRequired === true ? '发布:project' : '发布:platform';
  const asyncScope = policy.asyncTaskProjectScopeRecovered === true ? '异步:task-project' : '异步:unknown';
  const evalScope = policy.evaluationCorpusProjectIsolated === true ? '评测语料:project' : '评测语料:shared';
  return [model, listScope, taskScope, candidateScope, batchScope, publishScope, asyncScope, evalScope]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignEvaluationCorpusPolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.evaluationCorpusPolicy ?? evaluationCorpusPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const corpusMode = displayDiagnosticText(policy.corpusMode, 40);
  const gateMode = displayDiagnosticText(policy.qualityGateMode, 40);
  const threshold = displayDiagnosticText(policy.thresholdSource, 32);
  const projectScope = policy.projectScopeRequired === true ? '项目作用域:required' : '项目作用域:optional';
  const goldenSet = policy.goldenSetBaselineRequired === true ? 'golden set:required' : 'golden set:optional';
  const aiEval = policy.qualityEvalScriptReady === true ? 'AI评测脚本:ready' : 'AI评测脚本:pending';
  const gate = policy.qualityGateIntegrated === true ? '质量门禁:integrated' : '质量门禁:manual';
  const readiness = policy.readinessDistributionTracked === true ? '准出分布:tracked' : '准出分布:missing';
  const promptVersion = policy.promptVersionTracked === true ? 'Prompt版本:tracked' : 'Prompt版本:missing';
  const operations = policy.operationsConsoleReady === true ? '运营后台:ready' : '运营后台:pending';
  return [corpusMode, gateMode, threshold, projectScope, goldenSet, aiEval, gate, readiness, promptVersion, operations]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignReleaseReadinessPolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.releaseReadinessPolicy ?? releaseReadinessPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const decision = displayDiagnosticText(policy.decisionMode, 40);
  const threshold = displayDiagnosticText(policy.thresholdSource, 32);
  const quality = policy.qualityThresholdEvaluated === true ? '质量阈值:checked' : '质量阈值:pending';
  const advisory = policy.advisoryOnly === true ? '建议模式:on' : '建议模式:off';
  const blocking = policy.publishBlockingEnabled === true ? '发布阻断:on' : '发布阻断:off';
  const approval = policy.approvalWorkflowReady === true ? '审批流:ready' : '审批流:pending';
  const manual = policy.manualApprovalRequired === true ? '人工准出:required' : '人工准出:optional';
  const autoPublish = policy.autoPublishAllowed === true ? '自动发布:on' : '自动发布:off';
  const confirmed = policy.confirmedCandidateRequired === true ? '候选确认:required' : '候选确认:optional';
  return [decision, threshold, quality, advisory, blocking, approval, manual, autoPublish, confirmed]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignAuditChainPolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.auditChainPolicy ?? auditChainPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const mode = displayDiagnosticText(policy.chainMode, 44);
  const source = displayDiagnosticText(policy.eventSource, 44);
  const wp1 = policy.wp1AuditEventWritten === true ? 'WP1审计:written' : 'WP1审计:missing';
  const wp2 = policy.wp2InvocationReferenceTracked === true ? 'WP2调用:tracked' : 'WP2调用:missing';
  const wp3 = policy.wp3PublishReferenceTracked === true ? 'WP3发布:tracked' : 'WP3发布:missing';
  const wp5 = policy.wp5DomainEventsTracked === true ? 'WP5本域:tracked' : 'WP5本域:missing';
  const scope = policy.projectScopeRequired === true ? '项目作用域:required' : '项目作用域:optional';
  const trace = policy.traceSignalTracked === true ? 'trace信号:tracked' : 'trace信号:missing';
  const dashboard = policy.crossWpAuditDashboardReady === true ? '跨WP看板:ready' : '跨WP看板:pending';
  const outbox = policy.auditOutboxReplayDashboardReady === true ? 'outbox看板:ready' : 'outbox看板:pending';
  return [mode, source, wp1, wp2, wp3, wp5, scope, trace, dashboard, outbox]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

function contextAssemblyPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.contextAssemblyPolicy ?? assemblyPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.inputDigestRequired === false ||
    policy?.persistedContextSummaryOnly === false ||
    policy?.wp3ApplicationServiceOnly === false ||
    policy?.rawContextBodyStored === true ||
    policy?.modelPayloadStored === true ||
    anyAssemblyDetailExported(policy)
  ) {
    return 'danger';
  }
  return 'neutral';
}

function contextPolicyGovernanceTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const governance = task.contextPolicyGovernance ?? governanceFromContextSummary(task.contextSummary);
  if (governance?.changeApprovalWorkflowReady === false || governance?.projectOverrideSupported === false) {
    return 'warning';
  }
  return 'neutral';
}

function contextPolicyOperationsTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const operations = task.contextPolicyOperations ?? operationsFromContextSummary(task.contextSummary);
  if (
    operations?.projectOverrideStoreReady === false ||
    operations?.environmentOverrideStoreReady === false ||
    operations?.changeApprovalWorkflowReady === false ||
    operations?.approvalStatus === 'WORKFLOW_NOT_READY'
  ) {
    return 'warning';
  }
  return 'neutral';
}

function scopePolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.scopePolicy ?? scopePolicyFromContextSummary(task.contextSummary);
  if (
    policy?.taskProjectScopeRequired === false ||
    policy?.candidateProjectScopeRequired === false ||
    policy?.batchCandidateProjectScopeRequired === false ||
    policy?.publishProjectScopeRequired === false ||
    policy?.asyncTaskProjectScopeRecovered === false ||
    policy?.smokeProjectScopeRequired === false ||
    policy?.evaluationCorpusProjectIsolated === false ||
    policy?.candidateIdentifierListExported === true ||
    policy?.roleRuleDetailExported === true ||
    policy?.serviceTokenValueExported === true
  ) {
    return 'danger';
  }
  if (policy?.evaluationCorpusOperationsReady === false || policy?.crossWpScopeDashboardReady === false) {
    return 'warning';
  }
  return 'neutral';
}

function evaluationCorpusPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.evaluationCorpusPolicy ?? evaluationCorpusPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.projectScopeRequired === false ||
    policy?.goldenSetBaselineRequired === false ||
    policy?.qualityEvalScriptReady === false ||
    policy?.qualityGateIntegrated === false ||
    policy?.readinessDistributionTracked === false ||
    policy?.promptVersionTracked === false ||
    policy?.evaluationCorpusProjectIsolated === false ||
    policy?.corpusRowExported === true ||
    policy?.candidateBodyExported === true ||
    policy?.reviewCommentExported === true ||
    policy?.promptBodyExported === true
  ) {
    return 'danger';
  }
  if (
    policy?.sampleMaintenanceReady === false ||
    policy?.longTermCalibrationReady === false ||
    policy?.operationsConsoleReady === false ||
    policy?.qualityGateMode === 'MANUAL_OPT_IN_AI_EVAL'
  ) {
    return 'warning';
  }
  return 'neutral';
}

function releaseReadinessPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.releaseReadinessPolicy ?? releaseReadinessPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.autoPublishAllowed === true ||
    policy?.confirmedCandidateRequired === false ||
    policy?.candidateEvidenceExported === true ||
    policy?.approvalNotesExported === true ||
    policy?.thresholdRuleDetailExported === true
  ) {
    return 'danger';
  }
  if (
    policy?.advisoryOnly === true ||
    policy?.publishBlockingEnabled === false ||
    policy?.approvalWorkflowReady === false ||
    policy?.qualityGateOverrideSupported === true
  ) {
    return 'warning';
  }
  return 'neutral';
}

function auditChainPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.auditChainPolicy ?? auditChainPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.wp1AuditEventWritten === false ||
    policy?.wp2InvocationReferenceTracked === false ||
    policy?.wp3PublishReferenceTracked === false ||
    policy?.wp5DomainEventsTracked === false ||
    policy?.projectScopeRequired === false ||
    policy?.traceSignalTracked === false ||
    policy?.auditEventDetailExported === true ||
    policy?.candidateIdentifierListExported === true ||
    policy?.platformAuditIdentifierExported === true ||
    policy?.traceIdValueExported === true ||
    policy?.modelInvocationIdValueExported === true ||
    policy?.publishIdentifierValueExported === true
  ) {
    return 'danger';
  }
  if (policy?.crossWpAuditDashboardReady === false || policy?.auditOutboxReplayDashboardReady === false) {
    return 'warning';
  }
  return 'neutral';
}

function assemblyPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.assemblyPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    assemblyMode: safeOptionalString(record.assemblyMode),
    digestStrategy: safeOptionalString(record.digestStrategy),
    inputDigestRequired: safeOptionalBoolean(record.inputDigestRequired),
    persistedContextSummaryOnly: safeOptionalBoolean(record.persistedContextSummaryOnly),
    wp3ApplicationServiceOnly: safeOptionalBoolean(record.wp3ApplicationServiceOnly),
    rawContextBodyStored: safeOptionalBoolean(record.rawContextBodyStored),
    modelPayloadStored: safeOptionalBoolean(record.modelPayloadStored),
    digestValueExported: safeOptionalBoolean(record.digestValueExported),
    requirementBodyExported: safeOptionalBoolean(record.requirementBodyExported),
    assetSchemaExported: safeOptionalBoolean(record.assetSchemaExported),
    pageTreeExported: safeOptionalBoolean(record.pageTreeExported),
    flowJsonExported: safeOptionalBoolean(record.flowJsonExported),
    explicitAssetIdentifierListExported: safeOptionalBoolean(record.explicitAssetIdentifierListExported),
    historicalCaseStepExported: safeOptionalBoolean(record.historicalCaseStepExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function scopePolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.scopePolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    scopeModel: safeOptionalString(record.scopeModel),
    listFallbackScope: safeOptionalString(record.listFallbackScope),
    taskProjectScopeRequired: safeOptionalBoolean(record.taskProjectScopeRequired),
    candidateProjectScopeRequired: safeOptionalBoolean(record.candidateProjectScopeRequired),
    batchCandidateProjectScopeRequired: safeOptionalBoolean(record.batchCandidateProjectScopeRequired),
    publishProjectScopeRequired: safeOptionalBoolean(record.publishProjectScopeRequired),
    asyncTaskProjectScopeRecovered: safeOptionalBoolean(record.asyncTaskProjectScopeRecovered),
    smokeProjectScopeRequired: safeOptionalBoolean(record.smokeProjectScopeRequired),
    evaluationCorpusProjectIsolated: safeOptionalBoolean(record.evaluationCorpusProjectIsolated),
    evaluationCorpusOperationsReady: safeOptionalBoolean(record.evaluationCorpusOperationsReady),
    crossWpScopeDashboardReady: safeOptionalBoolean(record.crossWpScopeDashboardReady),
    candidateIdentifierListExported: safeOptionalBoolean(record.candidateIdentifierListExported),
    roleRuleDetailExported: safeOptionalBoolean(record.roleRuleDetailExported),
    serviceTokenValueExported: safeOptionalBoolean(record.serviceTokenValueExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function evaluationCorpusPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.evaluationCorpusPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    corpusMode: safeOptionalString(record.corpusMode),
    qualityGateMode: safeOptionalString(record.qualityGateMode),
    thresholdSource: safeOptionalString(record.thresholdSource),
    projectScopeRequired: safeOptionalBoolean(record.projectScopeRequired),
    goldenSetBaselineRequired: safeOptionalBoolean(record.goldenSetBaselineRequired),
    qualityEvalScriptReady: safeOptionalBoolean(record.qualityEvalScriptReady),
    qualityGateIntegrated: safeOptionalBoolean(record.qualityGateIntegrated),
    readinessDistributionTracked: safeOptionalBoolean(record.readinessDistributionTracked),
    promptVersionTracked: safeOptionalBoolean(record.promptVersionTracked),
    evaluationCorpusProjectIsolated: safeOptionalBoolean(record.evaluationCorpusProjectIsolated),
    sampleMaintenanceReady: safeOptionalBoolean(record.sampleMaintenanceReady),
    longTermCalibrationReady: safeOptionalBoolean(record.longTermCalibrationReady),
    operationsConsoleReady: safeOptionalBoolean(record.operationsConsoleReady),
    corpusRowExported: safeOptionalBoolean(record.corpusRowExported),
    candidateBodyExported: safeOptionalBoolean(record.candidateBodyExported),
    reviewCommentExported: safeOptionalBoolean(record.reviewCommentExported),
    promptBodyExported: safeOptionalBoolean(record.promptBodyExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function releaseReadinessPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.releaseReadinessPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    decisionMode: safeOptionalString(record.decisionMode),
    thresholdSource: safeOptionalString(record.thresholdSource),
    qualityThresholdEvaluated: safeOptionalBoolean(record.qualityThresholdEvaluated),
    advisoryOnly: safeOptionalBoolean(record.advisoryOnly),
    publishBlockingEnabled: safeOptionalBoolean(record.publishBlockingEnabled),
    manualApprovalRequired: safeOptionalBoolean(record.manualApprovalRequired),
    approvalWorkflowReady: safeOptionalBoolean(record.approvalWorkflowReady),
    autoPublishAllowed: safeOptionalBoolean(record.autoPublishAllowed),
    confirmedCandidateRequired: safeOptionalBoolean(record.confirmedCandidateRequired),
    qualityGateOverrideSupported: safeOptionalBoolean(record.qualityGateOverrideSupported),
    candidateEvidenceExported: safeOptionalBoolean(record.candidateEvidenceExported),
    approvalNotesExported: safeOptionalBoolean(record.approvalNotesExported),
    thresholdRuleDetailExported: safeOptionalBoolean(record.thresholdRuleDetailExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function auditChainPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.auditChainPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    chainMode: safeOptionalString(record.chainMode),
    eventSource: safeOptionalString(record.eventSource),
    wp1AuditEventWritten: safeOptionalBoolean(record.wp1AuditEventWritten),
    wp2InvocationReferenceTracked: safeOptionalBoolean(record.wp2InvocationReferenceTracked),
    wp3PublishReferenceTracked: safeOptionalBoolean(record.wp3PublishReferenceTracked),
    wp5DomainEventsTracked: safeOptionalBoolean(record.wp5DomainEventsTracked),
    projectScopeRequired: safeOptionalBoolean(record.projectScopeRequired),
    traceSignalTracked: safeOptionalBoolean(record.traceSignalTracked),
    auditEventDetailExported: safeOptionalBoolean(record.auditEventDetailExported),
    candidateIdentifierListExported: safeOptionalBoolean(record.candidateIdentifierListExported),
    platformAuditIdentifierExported: safeOptionalBoolean(record.platformAuditIdentifierExported),
    traceIdValueExported: safeOptionalBoolean(record.traceIdValueExported),
    modelInvocationIdValueExported: safeOptionalBoolean(record.modelInvocationIdValueExported),
    publishIdentifierValueExported: safeOptionalBoolean(record.publishIdentifierValueExported),
    crossWpAuditDashboardReady: safeOptionalBoolean(record.crossWpAuditDashboardReady),
    auditOutboxReplayDashboardReady: safeOptionalBoolean(record.auditOutboxReplayDashboardReady),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function governanceFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.policyGovernance;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    policySource: safeOptionalString(record.policySource),
    governanceStatus: safeOptionalString(record.governanceStatus),
    changeMode: safeOptionalString(record.changeMode),
    projectOverrideSupported: safeOptionalBoolean(record.projectOverrideSupported),
    environmentOverrideSupported: safeOptionalBoolean(record.environmentOverrideSupported),
    changeApprovalWorkflowReady: safeOptionalBoolean(record.changeApprovalWorkflowReady)
  };
}

function operationsFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.policyOperations;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    operationMode: safeOptionalString(record.operationMode),
    policyResolutionOrder: safeOptionalString(record.policyResolutionOrder),
    policyFallbackBehavior: safeOptionalString(record.policyFallbackBehavior),
    approvalStatus: safeOptionalString(record.approvalStatus),
    projectOverrideStoreReady: safeOptionalBoolean(record.projectOverrideStoreReady),
    environmentOverrideStoreReady: safeOptionalBoolean(record.environmentOverrideStoreReady),
    changeApprovalWorkflowReady: safeOptionalBoolean(record.changeApprovalWorkflowReady),
    effectivePolicySnapshotMaterialized: safeOptionalBoolean(record.effectivePolicySnapshotMaterialized),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function anyAssemblyDetailExported(policy: {
  digestValueExported?: boolean;
  requirementBodyExported?: boolean;
  assetSchemaExported?: boolean;
  pageTreeExported?: boolean;
  flowJsonExported?: boolean;
  explicitAssetIdentifierListExported?: boolean;
  historicalCaseStepExported?: boolean;
} | null | undefined) {
  if (!policy) {
    return false;
  }
  return policy.digestValueExported === true ||
    policy.requirementBodyExported === true ||
    policy.assetSchemaExported === true ||
    policy.pageTreeExported === true ||
    policy.flowJsonExported === true ||
    policy.explicitAssetIdentifierListExported === true ||
    policy.historicalCaseStepExported === true;
}

function contextPolicyPart(record: Record<string, unknown>, key: string, label: string) {
  if (SENSITIVE_KEY_PATTERN.test(key)) {
    return '';
  }
  const count = countContextValue(record[key]);
  return count === null ? '' : `${label}:${count}`;
}

function appendExplicitAssetCounts(parts: string[], contextSummary: Record<string, unknown>) {
  if (!('explicitAssets' in contextSummary) || SENSITIVE_KEY_PATTERN.test('explicitAssets')) {
    return;
  }
  const explicitAssets = contextSummary.explicitAssets;
  if (!explicitAssets || typeof explicitAssets !== 'object' || Array.isArray(explicitAssets)) {
    return;
  }
  const record = explicitAssets as Record<string, unknown>;
  appendNumericCount(parts, 'explicitApis', record.apiCount);
  appendNumericCount(parts, 'explicitPages', record.pageCount);
  appendNumericCount(parts, 'explicitFlows', record.flowCount);
}

function appendNumericCount(parts: string[], label: string, value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    parts.push(`${label}:${Math.floor(value)}`);
  }
}

function appendContextCount(
  parts: string[],
  contextSummary: Record<string, unknown>,
  label: string,
  keys: string[]
) {
  const count = firstCount(contextSummary, keys);
  if (count !== null) {
    parts.push(`${label}:${count}`);
  }
}

function firstCount(contextSummary: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    if (SENSITIVE_KEY_PATTERN.test(key) || !(key in contextSummary)) {
      continue;
    }
    const count = countContextValue(contextSummary[key]);
    if (count !== null) {
      return count;
    }
  }
  return null;
}

function countContextValue(value: unknown): number | null {
  if (Array.isArray(value)) {
    return value.length;
  }
  if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
    return Math.floor(value);
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    for (const key of ['count', 'total', 'size']) {
      const count = record[key];
      if (typeof count === 'number' && Number.isFinite(count) && count >= 0) {
        return Math.floor(count);
      }
    }
  }
  return null;
}

function firstSafeScalar(contextSummary: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    if (SENSITIVE_KEY_PATTERN.test(key) || !(key in contextSummary)) {
      continue;
    }
    const value = contextSummary[key];
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
  }
  return '';
}

function safeOptionalString(value: unknown) {
  if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'boolean') {
    return undefined;
  }
  const text = String(value);
  return SENSITIVE_KEY_PATTERN.test(text) ? undefined : sanitizeDiagnosticText(text);
}

function safeOptionalBoolean(value: unknown) {
  if (typeof value === 'boolean') {
    return value;
  }
  return undefined;
}

function formatPrompt(task: TestDesignTaskView) {
  const promptKey = displayDiagnosticText(task.promptKey, 48);
  const promptVersion = displayDiagnosticText(task.promptVersion, 24);
  if (promptKey === '-' && promptVersion === '-') {
    return '-';
  }
  if (promptVersion === '-') {
    return promptKey;
  }
  if (promptKey === '-') {
    return promptVersion;
  }
  return `${promptKey}@${promptVersion}`;
}

function formatModel(task: TestDesignTaskView) {
  const provider = displayDiagnosticText(task.modelProviderName, 32);
  const model = displayDiagnosticText(task.modelName, 48);
  if (provider === '-' && model === '-') {
    return '-';
  }
  if (provider === '-') {
    return model;
  }
  if (model === '-') {
    return provider;
  }
  return `${provider} / ${model}`;
}

function formatModelObservation(task: TestDesignTaskView) {
  const observation = task.modelObservation;
  if (!observation) {
    return task.modelInvocationId ? '仅记录调用 ID' : '-';
  }
  const parts: string[] = [];
  parts.push(displayDiagnosticText(observation.status, 24));
  parts.push(`${formatNumber(observation.inputTokens)}/${formatNumber(observation.outputTokens)} tokens`);
  parts.push(`${formatNumber(observation.latencyMs)}ms`);
  parts.push(`cost:${formatCost(observation.totalCost)}`);
  if (observation.fallbackUsed) {
    parts.push('fallback');
  }
  if (!observation.available) {
    parts.push('日志暂不可用');
  }
  const error = displayDiagnosticText(observation.errorCode || observation.errorMessage, 48);
  if (error !== '-') {
    parts.push(error);
  }
  return parts.filter((part) => part && part !== '-').join(' · ') || '-';
}

function modelObservationTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const observation = task.modelObservation;
  if (!observation) {
    return task.modelInvocationId ? 'warning' : 'neutral';
  }
  if (!observation.available) {
    return 'warning';
  }
  return observation.status === 'FAILED' || observation.status === 'BLOCKED' ? 'danger' : 'neutral';
}

function formatList(values: string[]) {
  const safeValues = values.map((value) => displayDiagnosticText(value, 24)).filter((value) => value !== '-');
  return safeValues.length ? safeValues.join(', ') : '-';
}

function formatNumber(value?: number) {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : '-';
}

function formatCost(value?: number) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '-';
  }
  return value === 0 ? '0' : value.toFixed(8).replace(/0+$/, '').replace(/\.$/, '');
}

function formatDateTime(value?: string) {
  const text = sanitizeDiagnosticText(value);
  if (!text) {
    return '-';
  }
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) {
    return displayDiagnosticText(text, 40);
  }
  return date.toISOString().replace('.000Z', 'Z');
}

function displayDiagnosticText(value: unknown, maxLength = 80) {
  const text = sanitizeDiagnosticText(value);
  if (!text) {
    return '-';
  }
  if (text.length <= maxLength) {
    return text;
  }
  const headLength = Math.max(1, maxLength - 10);
  return `${text.slice(0, headLength)}...${text.slice(-6)}`;
}

function sanitizeDiagnosticText(value: unknown) {
  if (value === null || value === undefined) {
    return '';
  }
  return sanitizeTestDesignExportText(String(value));
}
