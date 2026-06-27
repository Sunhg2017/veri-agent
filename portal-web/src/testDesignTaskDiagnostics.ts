import type { TestDesignTaskView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import { generationSourceText, taskGenerationSource } from './testDesignGenerationSource';
import { translate } from './platform/i18n';

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
    { label: translate('auto.k1362'), value: compactTestDesignDigest(task.id, 10, 6) },
    { label: translate('auto.k0176'), value: compactTestDesignDigest(task.projectId, 10, 6) },
    { label: translate('auto.k0182'), value: displayDiagnosticText(task.status) },
    { label: translate('auto.k2175'), value: String(task.totalRequirements ?? task.requirementIds.length) },
    { label: translate('auto.k0538'), value: formatList(task.coverageTypes) },
    { label: translate('auto.k2176'), value: translate('auto.k2177', { value0: task.generatedCount, value1: task.confirmedCount, value2: task.publishedCount }) },
    {
      label: translate('auto.k2178'),
      value: generationSourceText(generationSource),
      tone: generationSource.tone === 'warning' ? 'warning' : 'neutral'
    },
    { label: 'Prompt', value: formatPrompt(task) },
    { label: translate('auto.k2179'), value: formatModel(task) },
    { label: translate('auto.k0991'), value: compactTestDesignDigest(task.modelInvocationId, 12, 8) },
    {
      label: translate('auto.k2180'),
      value: formatModelObservation(task),
      tone: modelObservationTone(task)
    },
    {
      label: translate('auto.k2181'),
      value: summarizeTestDesignModelObservationPolicy(task),
      tone: modelObservationPolicyTone(task)
    },
    {
      label: translate('auto.k2182'),
      value: summarizeTestDesignGenerationOrchestrationPolicy(task),
      tone: generationOrchestrationPolicyTone(task)
    },
    { label: translate('auto.k2183'), value: compactTestDesignDigest(task.modelObservation?.traceId, 12, 8) },
    { label: translate('auto.k2184'), value: compactTestDesignDigest(task.modelObservation?.jobId, 12, 8) },
    { label: translate('auto.k2185'), value: compactTestDesignDigest(task.inputDigest, 12, 8) },
    { label: translate('auto.k2186'), value: compactTestDesignDigest(task.idempotencyKey, 14, 8) },
    { label: translate('auto.k2187'), value: summarizeTestDesignTaskContext(task.contextSummary) },
    { label: translate('auto.k1388'), value: summarizeTestDesignContextPolicy(task.contextSummary) },
    {
      label: translate('auto.k2188'),
      value: summarizeTestDesignContextAssemblyPolicy(task),
      tone: contextAssemblyPolicyTone(task)
    },
    {
      label: translate('auto.k2189'),
      value: summarizeTestDesignContextPolicyGovernance(task),
      tone: contextPolicyGovernanceTone(task)
    },
    {
      label: translate('auto.k2190'),
      value: summarizeTestDesignContextPolicyOperations(task),
      tone: contextPolicyOperationsTone(task)
    },
    {
      label: translate('auto.k2191'),
      value: summarizeTestDesignScopePolicy(task),
      tone: scopePolicyTone(task)
    },
    {
      label: translate('auto.k2192'),
      value: summarizeTestDesignEvaluationCorpusPolicy(task),
      tone: evaluationCorpusPolicyTone(task)
    },
    {
      label: translate('auto.k2193'),
      value: summarizeTestDesignReleaseReadinessPolicy(task),
      tone: releaseReadinessPolicyTone(task)
    },
    {
      label: translate('auto.k1542'),
      value: summarizeTestDesignAuditChainPolicy(task),
      tone: auditChainPolicyTone(task)
    },
    {
      label: translate('auto.k2194'),
      value: summarizeTestDesignArchivePolicy(task),
      tone: archivePolicyTone(task)
    },
    {
      label: translate('auto.k2195'),
      value: summarizeTestDesignReportManifestPolicy(task),
      tone: reportManifestPolicyTone(task)
    },
    { label: translate('auto.k2196'), value: displayDiagnosticText(task.requestedBy) },
    { label: translate('auto.k0862'), value: formatDateTime(task.createdAt) },
    { label: translate('auto.k1008'), value: formatDateTime(task.updatedAt) },
    {
      label: translate('auto.k0780'),
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
    contextPolicyPart(record, 'linkedAssetsPerRequirement', translate('auto.k0431')),
    contextPolicyPart(record, 'explicitAssetsPerType', translate('auto.k1393')),
    contextPolicyPart(record, 'existingCasesPerRequirement', translate('auto.k1394')),
    contextPolicyPart(record, 'requirementDescriptionChars', translate('auto.k2197')),
    contextPolicyPart(record, 'acceptanceCriteriaChars', translate('auto.k0650')),
    contextPolicyPart(record, 'linkedAssetSchemaChars', translate('auto.k1397'))
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
  const digestRequired = policy.inputDigestRequired === true ? translate('auto.k2198') : translate('auto.k2199');
  const summaryOnly = policy.persistedContextSummaryOnly === true ? translate('auto.k2200') : translate('auto.k2201');
  const wp3Boundary = policy.wp3ApplicationServiceOnly === true ? translate('auto.k2202') : translate('auto.k2203');
  const rawStored = policy.rawContextBodyStored === true ? translate('auto.k2204') : translate('auto.k2205');
  const modelPayload = policy.modelPayloadStored === true ? translate('auto.k2206') : translate('auto.k2207');
  const detailExport = anyAssemblyDetailExported(policy) ? translate('auto.k2208') : translate('auto.k2209');
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
  const projectOverride = governance.projectOverrideSupported === true ? translate('auto.k2210') : translate('auto.k2211');
  const envOverride = governance.environmentOverrideSupported === true ? translate('auto.k2212') : translate('auto.k2213');
  const approval = governance.changeApprovalWorkflowReady === true ? translate('auto.k2214') : translate('auto.k2215');
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
  const projectStore = operations.projectOverrideStoreReady === true ? translate('auto.k2216') : translate('auto.k2217');
  const envStore = operations.environmentOverrideStoreReady === true ? translate('auto.k2218') : translate('auto.k2219');
  const approval = operations.changeApprovalWorkflowReady === true ? translate('auto.k2214') : translate('auto.k2215');
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
  const taskScope = policy.taskProjectScopeRequired === true ? translate('auto.k2220') : translate('auto.k2221');
  const candidateScope = policy.candidateProjectScopeRequired === true ? translate('auto.k2222') : translate('auto.k2223');
  const batchScope = policy.batchCandidateProjectScopeRequired === true ? translate('auto.k2224') : translate('auto.k2225');
  const publishScope = policy.publishProjectScopeRequired === true ? translate('auto.k2226') : translate('auto.k2227');
  const asyncScope = policy.asyncTaskProjectScopeRecovered === true ? translate('auto.k2228') : translate('auto.k2229');
  const evalScope = policy.evaluationCorpusProjectIsolated === true ? translate('auto.k2230') : translate('auto.k2231');
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
  const projectScope = policy.projectScopeRequired === true ? translate('auto.k2232') : translate('auto.k2233');
  const goldenSet = policy.goldenSetBaselineRequired === true ? 'golden set:required' : 'golden set:optional';
  const aiEval = policy.qualityEvalScriptReady === true ? translate('auto.k2234') : translate('auto.k2235');
  const gate = policy.qualityGateIntegrated === true ? translate('auto.k2236') : translate('auto.k2237');
  const readiness = policy.readinessDistributionTracked === true ? translate('auto.k2238') : translate('auto.k2239');
  const promptVersion = policy.promptVersionTracked === true ? translate('auto.k2240') : translate('auto.k2241');
  const operations = policy.operationsConsoleReady === true ? translate('auto.k2242') : translate('auto.k2243');
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
  const quality = policy.qualityThresholdEvaluated === true ? translate('auto.k2244') : translate('auto.k2245');
  const advisory = policy.advisoryOnly === true ? translate('auto.k2246') : translate('auto.k2247');
  const blocking = policy.publishBlockingEnabled === true ? translate('auto.k2248') : translate('auto.k2249');
  const approval = policy.approvalWorkflowReady === true ? translate('auto.k2214') : translate('auto.k2215');
  const manual = policy.manualApprovalRequired === true ? translate('auto.k2250') : translate('auto.k2251');
  const autoPublish = policy.autoPublishAllowed === true ? translate('auto.k2252') : translate('auto.k2253');
  const confirmed = policy.confirmedCandidateRequired === true ? translate('auto.k2254') : translate('auto.k2255');
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
  const wp1 = policy.wp1AuditEventWritten === true ? translate('auto.k2256') : translate('auto.k2257');
  const wp2 = policy.wp2InvocationReferenceTracked === true ? translate('auto.k2258') : translate('auto.k2259');
  const wp3 = policy.wp3PublishReferenceTracked === true ? translate('auto.k2260') : translate('auto.k2261');
  const wp5 = policy.wp5DomainEventsTracked === true ? translate('auto.k2262') : translate('auto.k2263');
  const scope = policy.projectScopeRequired === true ? translate('auto.k2232') : translate('auto.k2233');
  const trace = policy.traceSignalTracked === true ? translate('auto.k2264') : translate('auto.k2265');
  const dashboard = policy.crossWpAuditDashboardReady === true ? translate('auto.k2266') : translate('auto.k2267');
  const outbox = policy.auditOutboxReplayDashboardReady === true ? translate('auto.k2268') : translate('auto.k2269');
  return [mode, source, wp1, wp2, wp3, wp5, scope, trace, dashboard, outbox]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignArchivePolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.archivePolicy ?? archivePolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const version = displayDiagnosticText(policy.policyVersion, 32);
  const storage = displayDiagnosticText(policy.storagePolicy, 32);
  const retention = typeof policy.retentionDays === 'number' && Number.isFinite(policy.retentionDays)
    ? translate('auto.k2270', { value0: Math.floor(policy.retentionDays) })
    : translate('auto.k2271');
  const approval = policy.approvalRequired === true ? translate('auto.k2272') : translate('auto.k2273');
  const workflow = policy.archiveApprovalWorkflowReady === true ? translate('auto.k2214') : translate('auto.k2215');
  const externalWorkflow = policy.externalShareApprovalWorkflowReady === true ? translate('auto.k2274') : translate('auto.k2275');
  const workOrder = policy.workOrderWorkflowReady === true ? translate('auto.k2276') : translate('auto.k2277');
  const storageReady = policy.archiveStorageReady === true ? translate('auto.k2278') : translate('auto.k2279');
  const contentStored = policy.archiveContentStored === true ? translate('auto.k2280') : translate('auto.k2281');
  const lineIndex = policy.lineIntegrityIndexReady === true ? translate('auto.k2282') : translate('auto.k2283');
  const sharing = policy.externalSharingAllowed === true ? translate('auto.k2284') : translate('auto.k2285');
  const retentionTracked = policy.retentionPolicyTracked === true ? translate('auto.k2286') : translate('auto.k2287');
  const detailExport = anyArchiveDetailExported(policy) ? translate('auto.k2208') : translate('auto.k2209');
  return [
    version,
    storage,
    retention,
    approval,
    workflow,
    externalWorkflow,
    workOrder,
    storageReady,
    contentStored,
    lineIndex,
    sharing,
    retentionTracked,
    detailExport
  ]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignReportManifestPolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.reportManifestPolicy ?? reportManifestPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const version = displayDiagnosticText(policy.policyVersion, 36);
  const schema = displayDiagnosticText(policy.schemaVersion, 32);
  const fieldSet = displayDiagnosticText(policy.fieldSetVersion, 32);
  const mode = displayDiagnosticText(policy.manifestMode, 36);
  const rowCount = policy.rowCountTracked === true ? translate('auto.k2288') : translate('auto.k2289');
  const completion = policy.completionStatusTracked === true ? translate('auto.k2290') : translate('auto.k2291');
  const reconciliation = policy.archiveReconciliationReady === true ? translate('auto.k2292') : translate('auto.k2293');
  const rowIntegrityStored = policy.rowIntegrityStored === true ? translate('auto.k2294') : translate('auto.k2295');
  const rowIntegrityIndex = policy.rowIntegrityIndexReady === true ? translate('auto.k2282') : translate('auto.k2283');
  const detailExport = anyReportManifestDetailExported(policy) ? translate('auto.k2208') : translate('auto.k2209');
  return [version, schema, fieldSet, mode, rowCount, completion, reconciliation, rowIntegrityStored, rowIntegrityIndex, detailExport]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignModelObservationPolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.modelObservationPolicy ?? modelObservationPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const version = displayDiagnosticText(policy.policyVersion, 36);
  const mode = displayDiagnosticText(policy.observationMode, 40);
  const wp2 = policy.wp2InvocationReferenceTracked === true ? translate('auto.k2258') : translate('auto.k2259');
  const trace = policy.traceIdTracked === true ? translate('auto.k2264') : translate('auto.k2265');
  const job = policy.jobIdTracked === true ? translate('auto.k2296') : translate('auto.k2297');
  const routing = policy.routingMetadataTracked === true ? translate('auto.k2298') : translate('auto.k2299');
  const token = policy.tokenUsageTracked === true ? 'token:tracked' : 'token:missing';
  const costLatency = policy.costTracked === true && policy.latencyTracked === true
    ? translate('auto.k2300')
    : translate('auto.k2301');
  const fallback = policy.fallbackTracked === true ? 'fallback:tracked' : 'fallback:missing';
  const payload = policy.promptPayloadStored === true ? translate('auto.k2302') : translate('auto.k2303');
  const detailExport = anyModelObservationDetailExported(policy) ? translate('auto.k2208') : translate('auto.k2209');
  return [version, mode, wp2, trace, job, routing, token, costLatency, fallback, payload, detailExport]
    .filter((part) => part !== '-')
    .join(' · ') || '-';
}

export function summarizeTestDesignGenerationOrchestrationPolicy(task: TestDesignTaskView | null | undefined): string {
  const policy = task?.generationOrchestrationPolicy ?? generationOrchestrationPolicyFromContextSummary(task?.contextSummary);
  if (!policy) {
    return '-';
  }

  const version = displayDiagnosticText(policy.policyVersion, 40);
  const mode = displayDiagnosticText(policy.orchestrationMode, 40);
  const claim = policy.conditionalRunClaimSupported === true ? translate('auto.k2304') : translate('auto.k2305');
  const idempotent = policy.idempotentCreateReplaySupported === true ? translate('auto.k2306') : translate('auto.k2307');
  const replay = policy.duplicateEventReplaySafe === true ? translate('auto.k2308') : translate('auto.k2309');
  const recovery = policy.eventRecoveryEnabled === true ? translate('auto.k2310') : translate('auto.k2311');
  const queueLag = policy.queueLagMetricReady === true ? translate('auto.k2312') : translate('auto.k2313');
  const timeout = policy.timeoutAlertReady === true ? translate('auto.k2314') : translate('auto.k2315');
  const manual = policy.asyncGenerationEnabled === false
    ? translate('auto.k2316')
    : policy.manualQueuedEventReplayReady === true ? translate('auto.k2317') : translate('auto.k2318');
  const multi = policy.asyncGenerationEnabled === false
    ? translate('auto.k2319')
    : policy.multiInstanceLoadTestEvidenceReady === true ? translate('auto.k2320') : translate('auto.k2321');
  const detailExport = anyGenerationOrchestrationDetailExported(policy) ? translate('auto.k2208') : translate('auto.k2209');
  const runtime = generationOrchestrationRuntimeSummary(policy);
  return [version, mode, claim, idempotent, replay, recovery, queueLag, timeout, manual, multi, detailExport, runtime]
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

function archivePolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.archivePolicy ?? archivePolicyFromContextSummary(task.contextSummary);
  if (
    typeof policy?.retentionDays === 'number' && policy.retentionDays <= 0 ||
    policy?.retentionPolicyTracked === false ||
    policy?.archiveContentExported === true ||
    policy?.archivePathExported === true ||
    policy?.archiveNotesExported === true ||
    policy?.approvalNotesExported === true ||
    policy?.ticketUrlExported === true ||
    policy?.aggregateOnly === false
  ) {
    return 'danger';
  }
  if (
    policy?.archiveStorageReady === false ||
    policy?.archiveApprovalWorkflowReady === false ||
    policy?.externalShareApprovalWorkflowReady === false ||
    policy?.workOrderWorkflowReady === false ||
    policy?.archiveContentStored === false ||
    policy?.lineIntegrityIndexReady === false ||
    policy?.externalSharingAllowed === true
  ) {
    return 'warning';
  }
  return 'neutral';
}

function reportManifestPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.reportManifestPolicy ?? reportManifestPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.rowCountTracked === false ||
    policy?.completionStatusTracked === false ||
    policy?.detailRowsExported === true ||
    policy?.rowIntegrityValueExported === true ||
    policy?.rowContentSummaryExported === true ||
    policy?.candidateIdentifierListExported === true ||
    policy?.traceIdentifierListExported === true ||
    policy?.auditIdentifierListExported === true ||
    policy?.aggregateOnly === false
  ) {
    return 'danger';
  }
  if (
    policy?.archiveReconciliationReady === false ||
    policy?.rowIntegrityStored === false ||
    policy?.rowIntegrityIndexReady === false
  ) {
    return 'warning';
  }
  return 'neutral';
}

function modelObservationPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.modelObservationPolicy ?? modelObservationPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.wp2InvocationReferenceTracked === false ||
    policy?.routingMetadataTracked === false ||
    policy?.tokenUsageTracked === false ||
    policy?.latencyTracked === false ||
    policy?.costTracked === false ||
    policy?.fallbackTracked === false ||
    policy?.promptPayloadStored === true ||
    anyModelObservationDetailExported(policy) ||
    policy?.aggregateOnly === false
  ) {
    return 'danger';
  }
  if (policy?.traceIdTracked === false || policy?.jobIdTracked === false) {
    return 'warning';
  }
  return 'neutral';
}

function generationOrchestrationPolicyTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const policy = task.generationOrchestrationPolicy ??
    generationOrchestrationPolicyFromContextSummary(task.contextSummary);
  if (
    policy?.conditionalRunClaimSupported === false ||
    policy?.idempotentCreateReplaySupported === false ||
    policy?.duplicateEventReplaySafe === false ||
    policy?.explicitRetryRequiredAfterTimeout === false ||
    policy?.manualTaskRetrySupported === false ||
    policy?.queueLagMetricReady === false ||
    policy?.timeoutAlertReady === false ||
    anyGenerationOrchestrationDetailExported(policy) ||
    policy?.aggregateOnly === false
  ) {
    return 'danger';
  }
  if (
    policy?.queueLagWarning === true ||
    policy?.timeoutWarning === true ||
    (policy?.asyncGenerationEnabled !== false && policy?.manualQueuedEventReplayReady === false) ||
    (policy?.asyncGenerationEnabled !== false && policy?.multiInstanceLoadTestEvidenceReady === false) ||
    policy?.eventRecoveryEnabled === false ||
    policy?.queuedEventReplaySupported === false
  ) {
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

function archivePolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.archivePolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    retentionDays: safeOptionalNumber(record.retentionDays),
    storagePolicy: safeOptionalString(record.storagePolicy),
    approvalRequired: safeOptionalBoolean(record.approvalRequired),
    archiveApprovalWorkflowReady: safeOptionalBoolean(record.archiveApprovalWorkflowReady),
    externalShareApprovalWorkflowReady: safeOptionalBoolean(record.externalShareApprovalWorkflowReady),
    workOrderWorkflowReady: safeOptionalBoolean(record.workOrderWorkflowReady),
    externalSharingAllowed: safeOptionalBoolean(record.externalSharingAllowed),
    retentionPolicyTracked: safeOptionalBoolean(record.retentionPolicyTracked),
    archiveStorageReady: safeOptionalBoolean(record.archiveStorageReady),
    archiveContentStored: safeOptionalBoolean(record.archiveContentStored),
    lineIntegrityIndexReady: safeOptionalBoolean(record.lineIntegrityIndexReady),
    archiveContentExported: safeOptionalBoolean(record.archiveContentExported),
    archivePathExported: safeOptionalBoolean(record.archivePathExported),
    archiveNotesExported: safeOptionalBoolean(record.archiveNotesExported),
    approvalNotesExported: safeOptionalBoolean(record.approvalNotesExported),
    ticketUrlExported: safeOptionalBoolean(record.ticketUrlExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function reportManifestPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.reportManifestPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    schemaVersion: safeOptionalString(record.schemaVersion),
    fieldSetVersion: safeOptionalString(record.fieldSetVersion),
    manifestMode: safeOptionalString(record.manifestMode),
    rowCountTracked: safeOptionalBoolean(record.rowCountTracked),
    completionStatusTracked: safeOptionalBoolean(record.completionStatusTracked),
    archiveReconciliationReady: safeOptionalBoolean(record.archiveReconciliationReady),
    rowIntegrityStored: safeOptionalBoolean(record.rowIntegrityStored),
    rowIntegrityIndexReady: safeOptionalBoolean(record.rowIntegrityIndexReady),
    detailRowsExported: safeOptionalBoolean(record.detailRowsExported),
    rowIntegrityValueExported: safeOptionalBoolean(record.rowIntegrityValueExported),
    rowContentSummaryExported: safeOptionalBoolean(record.rowContentSummaryExported),
    candidateIdentifierListExported: safeOptionalBoolean(record.candidateIdentifierListExported),
    traceIdentifierListExported: safeOptionalBoolean(record.traceIdentifierListExported),
    auditIdentifierListExported: safeOptionalBoolean(record.auditIdentifierListExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function modelObservationPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.modelObservationPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    observationMode: safeOptionalString(record.observationMode),
    wp2InvocationReferenceTracked: safeOptionalBoolean(record.wp2InvocationReferenceTracked),
    traceIdTracked: safeOptionalBoolean(record.traceIdTracked),
    jobIdTracked: safeOptionalBoolean(record.jobIdTracked),
    routingMetadataTracked: safeOptionalBoolean(record.routingMetadataTracked),
    tokenUsageTracked: safeOptionalBoolean(record.tokenUsageTracked),
    latencyTracked: safeOptionalBoolean(record.latencyTracked),
    costTracked: safeOptionalBoolean(record.costTracked),
    fallbackTracked: safeOptionalBoolean(record.fallbackTracked),
    promptPayloadStored: safeOptionalBoolean(record.promptPayloadStored),
    payloadPreviewExported: safeOptionalBoolean(record.payloadPreviewExported),
    traceIdValueExported: safeOptionalBoolean(record.traceIdValueExported),
    jobIdValueExported: safeOptionalBoolean(record.jobIdValueExported),
    invocationIdValueExported: safeOptionalBoolean(record.invocationIdValueExported),
    providerErrorTextExported: safeOptionalBoolean(record.providerErrorTextExported),
    actorServiceExported: safeOptionalBoolean(record.actorServiceExported),
    aggregateOnly: safeOptionalBoolean(record.aggregateOnly)
  };
}

function generationOrchestrationPolicyFromContextSummary(contextSummary: Record<string, unknown> | null | undefined) {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return undefined;
  }
  const raw = contextSummary.generationOrchestrationPolicy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  return {
    policyVersion: safeOptionalString(record.policyVersion),
    orchestrationMode: safeOptionalString(record.orchestrationMode),
    asyncGenerationEnabled: safeOptionalBoolean(record.asyncGenerationEnabled),
    conditionalRunClaimSupported: safeOptionalBoolean(record.conditionalRunClaimSupported),
    idempotentCreateReplaySupported: safeOptionalBoolean(record.idempotentCreateReplaySupported),
    duplicateEventReplaySafe: safeOptionalBoolean(record.duplicateEventReplaySafe),
    eventRecoveryEnabled: safeOptionalBoolean(record.eventRecoveryEnabled),
    queuedEventReplaySupported: safeOptionalBoolean(record.queuedEventReplaySupported),
    runningTimeoutRecoveryEnabled: safeOptionalBoolean(record.runningTimeoutRecoveryEnabled),
    explicitRetryRequiredAfterTimeout: safeOptionalBoolean(record.explicitRetryRequiredAfterTimeout),
    manualTaskRetrySupported: safeOptionalBoolean(record.manualTaskRetrySupported),
    manualQueuedEventReplayReady: safeOptionalBoolean(record.manualQueuedEventReplayReady),
    queueLagMetricReady: safeOptionalBoolean(record.queueLagMetricReady),
    timeoutAlertReady: safeOptionalBoolean(record.timeoutAlertReady),
    multiInstanceLoadTestEvidenceReady: safeOptionalBoolean(record.multiInstanceLoadTestEvidenceReady),
    eventPayloadExported: safeOptionalBoolean(record.eventPayloadExported),
    eventIdentifierListExported: safeOptionalBoolean(record.eventIdentifierListExported),
    queueMessageBodyExported: safeOptionalBoolean(record.queueMessageBodyExported),
    recoveryDetailRowsExported: safeOptionalBoolean(record.recoveryDetailRowsExported),
    effectiveRecoveryBatchSize: safeOptionalNumber(record.effectiveRecoveryBatchSize),
    runningTimeoutSeconds: safeOptionalNumber(record.runningTimeoutSeconds),
    queueLagWarningSeconds: safeOptionalNumber(record.queueLagWarningSeconds),
    queuedTaskCount: safeOptionalNumber(record.queuedTaskCount),
    runningTaskCount: safeOptionalNumber(record.runningTaskCount),
    oldestQueuedAgeSeconds: safeOptionalNumber(record.oldestQueuedAgeSeconds),
    staleRunningTaskCount: safeOptionalNumber(record.staleRunningTaskCount),
    queueLagWarning: safeOptionalBoolean(record.queueLagWarning),
    timeoutWarning: safeOptionalBoolean(record.timeoutWarning),
    queuedStatusSignal: safeOptionalNumber(record.queuedStatusSignal),
    runningStatusSignal: safeOptionalNumber(record.runningStatusSignal),
    timeoutFailureSignal: safeOptionalNumber(record.timeoutFailureSignal),
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

function anyArchiveDetailExported(policy: {
  archiveContentExported?: boolean;
  archivePathExported?: boolean;
  archiveNotesExported?: boolean;
  approvalNotesExported?: boolean;
  ticketUrlExported?: boolean;
} | null | undefined) {
  if (!policy) {
    return false;
  }
  return policy.archiveContentExported === true ||
    policy.archivePathExported === true ||
    policy.archiveNotesExported === true ||
    policy.approvalNotesExported === true ||
    policy.ticketUrlExported === true;
}

function anyReportManifestDetailExported(policy: {
  detailRowsExported?: boolean;
  rowIntegrityValueExported?: boolean;
  rowContentSummaryExported?: boolean;
  candidateIdentifierListExported?: boolean;
  traceIdentifierListExported?: boolean;
  auditIdentifierListExported?: boolean;
} | null | undefined) {
  if (!policy) {
    return false;
  }
  return policy.detailRowsExported === true ||
    policy.rowIntegrityValueExported === true ||
    policy.rowContentSummaryExported === true ||
    policy.candidateIdentifierListExported === true ||
    policy.traceIdentifierListExported === true ||
    policy.auditIdentifierListExported === true;
}

function anyModelObservationDetailExported(policy: {
  payloadPreviewExported?: boolean;
  traceIdValueExported?: boolean;
  jobIdValueExported?: boolean;
  invocationIdValueExported?: boolean;
  providerErrorTextExported?: boolean;
  actorServiceExported?: boolean;
} | null | undefined) {
  if (!policy) {
    return false;
  }
  return policy.payloadPreviewExported === true ||
    policy.traceIdValueExported === true ||
    policy.jobIdValueExported === true ||
    policy.invocationIdValueExported === true ||
    policy.providerErrorTextExported === true ||
    policy.actorServiceExported === true;
}

function anyGenerationOrchestrationDetailExported(policy: {
  eventPayloadExported?: boolean;
  eventIdentifierListExported?: boolean;
  queueMessageBodyExported?: boolean;
  recoveryDetailRowsExported?: boolean;
} | null | undefined) {
  if (!policy) {
    return false;
  }
  return policy.eventPayloadExported === true ||
    policy.eventIdentifierListExported === true ||
    policy.queueMessageBodyExported === true ||
    policy.recoveryDetailRowsExported === true;
}

function generationOrchestrationRuntimeSummary(policy: {
  queueLagWarningSeconds?: number;
  runningTimeoutSeconds?: number;
  queuedTaskCount?: number;
  runningTaskCount?: number;
  oldestQueuedAgeSeconds?: number;
  staleRunningTaskCount?: number;
  queueLagWarning?: boolean;
  timeoutWarning?: boolean;
}) {
  const parts: string[] = [];
  if (typeof policy.queueLagWarningSeconds === 'number') {
    parts.push(translate('auto.k2322', { value0: Math.floor(policy.queueLagWarningSeconds) }));
  }
  if (typeof policy.runningTimeoutSeconds === 'number') {
    parts.push(translate('auto.k2323', { value0: Math.floor(policy.runningTimeoutSeconds) }));
  }
  if (typeof policy.queuedTaskCount === 'number') {
    parts.push(translate('auto.k2324', { value0: Math.floor(policy.queuedTaskCount) }));
  }
  if (typeof policy.runningTaskCount === 'number') {
    parts.push(translate('auto.k2325', { value0: Math.floor(policy.runningTaskCount) }));
  }
  if (typeof policy.oldestQueuedAgeSeconds === 'number') {
    parts.push(translate('auto.k2326', { value0: Math.floor(policy.oldestQueuedAgeSeconds) }));
  }
  if (typeof policy.staleRunningTaskCount === 'number') {
    parts.push(translate('auto.k2327', { value0: Math.floor(policy.staleRunningTaskCount) }));
  }
  if (policy.queueLagWarning === true) {
    parts.push(translate('auto.k2328'));
  }
  if (policy.timeoutWarning === true) {
    parts.push(translate('auto.k2329'));
  }
  return parts.length ? parts.join(' / ') : '-';
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

function safeOptionalNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
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
    return task.modelInvocationId ? translate('auto.k2330') : '-';
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
    parts.push(translate('auto.k2331'));
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
