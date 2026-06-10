import { requestJson, requestText, type ApiResponse, type TextResponse } from './client';

export const TEST_DESIGN_COVERAGE_TYPES = ['SMOKE', 'FUNCTIONAL', 'EXCEPTION', 'BOUNDARY', 'PERMISSION', 'REGRESSION'] as const;
export const TEST_DESIGN_GENERATION_STRATEGIES = ['BALANCED', 'RISK_FIRST', 'COMPLIANCE', 'EXPLORATORY'] as const;
export const TEST_DESIGN_COVERAGE_STRATEGIES = ['DEFAULT_ORDER', 'SMOKE_FIRST', 'RISK_FIRST', 'REGRESSION_HEAVY', 'SECURITY_PERMISSION'] as const;
export const TEST_DESIGN_CANDIDATE_STATUSES = [
  'GENERATED',
  'EDITED',
  'CONFIRMED',
  'REJECTED',
  'IGNORED',
  'PUBLISH_QUEUED',
  'PUBLISHING',
  'PUBLISHED',
  'FAILED'
] as const;

export type TestDesignCoverageType = (typeof TEST_DESIGN_COVERAGE_TYPES)[number];
export type TestDesignGenerationStrategy = (typeof TEST_DESIGN_GENERATION_STRATEGIES)[number];
export type TestDesignCoverageStrategy = (typeof TEST_DESIGN_COVERAGE_STRATEGIES)[number];
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
  modelObservationPolicy?: TestDesignModelObservationPolicyView;
  generationOrchestrationPolicy?: TestDesignGenerationOrchestrationPolicyView;
  archivePolicy?: TestDesignArchivePolicyView;
  reportManifestPolicy?: TestDesignReportManifestPolicyView;
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
  modelObservationPolicy?: TestDesignModelObservationPolicyView;
  generationOrchestrationPolicy?: TestDesignGenerationOrchestrationPolicyView;
  archivePolicy?: TestDesignArchivePolicyView;
  reportManifestPolicy?: TestDesignReportManifestPolicyView;
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

export interface TestDesignContextPolicyOverrideView {
  id: string;
  scopeType: string;
  projectId?: string;
  environmentKey?: string;
  status: string;
  overrideLimits: Record<string, number>;
  changeReasonCodeCaptured: boolean;
  approvalReasonCodeCaptured: boolean;
  workOrderKey?: string;
  workOrderTitle?: string;
  workOrderUrl?: string;
  workOrderStatus?: string;
  policyBody?: string;
  policyBodyDigest?: string;
  policyBodyVersion?: number;
  policyDiffSummary?: string;
  requestNote?: string;
  reviewNote?: string;
  noteCount?: number;
  latestNotePreview?: string;
  requestedBy?: string;
  approvedBy?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignContextPolicyNoteView {
  id: string;
  overrideId: string;
  noteType: string;
  noteText: string;
  createdBy?: string;
  createdAt?: string;
}

export interface TestDesignReleaseReadinessApprovalView {
  id: string;
  taskId: string;
  projectId?: string;
  status: string;
  qualityGateStatus: string;
  blockingCount: number;
  warningCount: number;
  readinessDigest?: string;
  exceptionReasonCodeCaptured: boolean;
  exceptionReasonCode?: string;
  approvalReasonCodeCaptured: boolean;
  approvalReasonCode?: string;
  workOrderKey?: string;
  workOrderTitle?: string;
  workOrderUrl?: string;
  workOrderStatus?: string;
  exceptionSummary?: string;
  exceptionSummaryDigest?: string;
  riskMitigation?: string;
  requestNote?: string;
  reviewNote?: string;
  noteCount?: number;
  latestNotePreview?: string;
  requestedBy?: string;
  approvedBy?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignReleaseReadinessNoteView {
  id: string;
  approvalId: string;
  noteType: string;
  noteText: string;
  createdBy?: string;
  createdAt?: string;
}

export interface TestDesignReportArchiveView {
  id: string;
  manifestId: string;
  taskId: string;
  projectId?: string;
  storageBackend?: string;
  contentDigest?: string;
  contentSizeBytes: number;
  reportRowCount: number;
  lineIntegrityCount: number;
  status: string;
  archiveApprovalStatus: string;
  externalApprovalStatus: string;
  retentionUntil?: string;
  archiveContentStored?: boolean;
  lineIntegrityIndexReady?: boolean;
  archiveContentExported?: boolean;
  storageKeyExported?: boolean;
  aggregateOnly?: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignReportArchiveIntegrityView {
  archiveId: string;
  reportRowCount: number;
  indexedRowCount: number;
  digestAlgorithm?: string;
  chainIntegrityStored?: boolean;
  rowIntegrityValueExported?: boolean;
  rowContentSummaryExported?: boolean;
  archiveContentExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignReportArchiveApprovalView {
  id: string;
  archiveId: string;
  taskId: string;
  projectId?: string;
  approvalType: string;
  status: string;
  reasonCodeCaptured: boolean;
  reasonCode?: string;
  approvalReasonCodeCaptured: boolean;
  approvalReasonCode?: string;
  workOrderKey?: string;
  workOrderTitle?: string;
  workOrderUrl?: string;
  workOrderStatus?: string;
  requestSummary?: string;
  requestSummaryDigest?: string;
  requestNote?: string;
  reviewNote?: string;
  noteCount?: number;
  latestNotePreview?: string;
  requestedBy?: string;
  approvedBy?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignReportArchiveNoteView {
  id: string;
  approvalId: string;
  noteType: string;
  noteText: string;
  createdBy?: string;
  createdAt?: string;
}

export interface TestDesignContextPolicyEffectiveView {
  projectId?: string;
  environmentKey?: string;
  contextLimits: Record<string, number>;
  appliedOverrideScopes: string[];
  overrideStatusCounts: Record<string, number>;
  contextAssemblyPolicy?: TestDesignContextAssemblyPolicyView;
  contextPolicyGovernance?: TestDesignContextPolicyGovernanceView;
  contextPolicyOperations?: TestDesignContextPolicyOperationsView;
  policyBodyExported?: boolean;
  policyDiffPreviewExported?: boolean;
  approvalNotesExported?: boolean;
  ticketUrlExported?: boolean;
  aggregateOnly?: boolean;
  generatedAt?: string;
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
  externalShareApprovalWorkflowReady?: boolean;
  workOrderWorkflowReady?: boolean;
  externalSharingAllowed?: boolean;
  retentionPolicyTracked?: boolean;
  archiveStorageReady?: boolean;
  archiveContentStored?: boolean;
  lineIntegrityIndexReady?: boolean;
  archiveContentExported?: boolean;
  archivePathExported?: boolean;
  archiveNotesExported?: boolean;
  approvalNotesExported?: boolean;
  ticketUrlExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignReportManifestPolicyView {
  policyVersion?: string;
  schemaVersion?: string;
  fieldSetVersion?: string;
  manifestMode?: string;
  rowCountTracked?: boolean;
  completionStatusTracked?: boolean;
  archiveReconciliationReady?: boolean;
  rowIntegrityStored?: boolean;
  rowIntegrityIndexReady?: boolean;
  detailRowsExported?: boolean;
  rowIntegrityValueExported?: boolean;
  rowContentSummaryExported?: boolean;
  candidateIdentifierListExported?: boolean;
  traceIdentifierListExported?: boolean;
  auditIdentifierListExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignModelObservationPolicyView {
  policyVersion?: string;
  observationMode?: string;
  wp2InvocationReferenceTracked?: boolean;
  traceIdTracked?: boolean;
  jobIdTracked?: boolean;
  routingMetadataTracked?: boolean;
  tokenUsageTracked?: boolean;
  latencyTracked?: boolean;
  costTracked?: boolean;
  fallbackTracked?: boolean;
  promptPayloadStored?: boolean;
  payloadPreviewExported?: boolean;
  traceIdValueExported?: boolean;
  jobIdValueExported?: boolean;
  invocationIdValueExported?: boolean;
  providerErrorTextExported?: boolean;
  actorServiceExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignGenerationOrchestrationPolicyView {
  policyVersion?: string;
  orchestrationMode?: string;
  asyncGenerationEnabled?: boolean;
  conditionalRunClaimSupported?: boolean;
  idempotentCreateReplaySupported?: boolean;
  duplicateEventReplaySafe?: boolean;
  eventRecoveryEnabled?: boolean;
  queuedEventReplaySupported?: boolean;
  runningTimeoutRecoveryEnabled?: boolean;
  explicitRetryRequiredAfterTimeout?: boolean;
  manualTaskRetrySupported?: boolean;
  manualQueuedEventReplayReady?: boolean;
  queueLagMetricReady?: boolean;
  timeoutAlertReady?: boolean;
  multiInstanceLoadTestEvidenceReady?: boolean;
  eventPayloadExported?: boolean;
  eventIdentifierListExported?: boolean;
  queueMessageBodyExported?: boolean;
  recoveryDetailRowsExported?: boolean;
  effectiveRecoveryBatchSize?: number;
  runningTimeoutSeconds?: number;
  queueLagWarningSeconds?: number;
  queuedTaskCount?: number;
  runningTaskCount?: number;
  oldestQueuedAgeSeconds?: number;
  staleRunningTaskCount?: number;
  queueLagWarning?: boolean;
  timeoutWarning?: boolean;
  queuedStatusSignal?: number;
  runningStatusSignal?: number;
  timeoutFailureSignal?: number;
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
  diffItems: TestDesignReviewDiffItemView[];
  createdAt?: string;
}

export interface TestDesignReviewDiffItemView {
  field: string;
  before?: string;
  after?: string;
}

export interface TestDesignTemplateView {
  id: string;
  projectId?: string;
  name: string;
  description?: string;
  promptKey: string;
  promptVersion: string;
  coverageTypes: string[];
  generationStrategy: string;
  coverageStrategy: string;
  caseCountPerRequirement: number;
  contextDefaults: Record<string, unknown>;
  enabled: boolean;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
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

export interface TestDesignEvaluationCorpusSummaryView {
  projectId?: string;
  promptKey?: string;
  policy?: TestDesignEvaluationCorpusPolicyView;
  taskCount: number;
  candidateCount: number;
  promptVersionCount: number;
  readinessDistribution: TestDesignQualityDistributionItemView[];
  feedbackSignalCount: number;
  sampleCandidateCount: number;
  sampleExplanationCount: number;
  sampleExplanationCoveragePercent: number;
  maintainedSampleCount: number;
  goldenSampleCount: number;
  frozenSampleCount: number;
  deprecatedSampleCount: number;
  baselineVersionCount: number;
  calibrationRunCount: number;
  latestCalibrationStatus?: string;
  latestCalibrationAt?: string;
  sampleMaintenanceReady?: boolean;
  longTermCalibrationReady?: boolean;
  operationsConsoleReady?: boolean;
  aggregateOnly?: boolean;
  corpusRowExported?: boolean;
  candidateBodyExported?: boolean;
  reviewCommentExported?: boolean;
  promptBodyExported?: boolean;
  generatedAt?: string;
}

export interface TestDesignEvaluationSampleView {
  id: string;
  projectId?: string;
  sampleKey: string;
  title: string;
  sourceType: string;
  sourceTaskId?: string;
  sourceCandidateId?: string;
  promptKey?: string;
  promptVersion?: string;
  coverageType: string;
  priority: string;
  status: string;
  baselineVersion?: string;
  requirementSummary?: string;
  expectedCaseOutline?: string;
  assertionNotes?: string;
  tags?: string;
  maintenanceNote?: string;
  sampleDigest?: string;
  sensitiveScanStatus?: string;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignEvaluationSampleList {
  items: TestDesignEvaluationSampleView[];
  index: number;
  size: number;
  total: number;
}

export interface TestDesignEvaluationSampleSummaryView {
  totalCount: number;
  candidateCount: number;
  goldenCount: number;
  frozenCount: number;
  deprecatedCount: number;
  baselineVersionCount: number;
  latestUpdatedAt?: string;
  sampleMaintenanceReady?: boolean;
  baselineReady?: boolean;
}

export interface TestDesignCalibrationRunView {
  id: string;
  projectId?: string;
  promptKey?: string;
  promptVersion?: string;
  baselineVersion?: string;
  runMode: string;
  status: string;
  sampleCount: number;
  goldenSampleCount: number;
  taskCount: number;
  candidateCount: number;
  stepCompletePercent: number;
  expectedCompletePercent: number;
  lowConfidencePercent: number;
  errorPercent: number;
  duplicateKeyCollisionCount: number;
  feedbackSignalCount: number;
  readinessStatus?: string;
  readinessBlockingCount: number;
  readinessWarningCount: number;
  regressionCount: number;
  baselineDigest?: string;
  resultDigest?: string;
  notes?: string;
  runBy?: string;
  createdAt?: string;
}

export interface TestDesignCalibrationSummaryView {
  totalRunCount: number;
  passedRunCount: number;
  warningRunCount: number;
  blockedRunCount: number;
  latestStatus?: string;
  latestRunAt?: string;
  longTermCalibrationReady?: boolean;
  baselineReady?: boolean;
}

export interface TestDesignCalibrationRunList {
  items: TestDesignCalibrationRunView[];
  index: number;
  size: number;
  total: number;
  summary?: TestDesignCalibrationSummaryView;
}

export interface TestDesignScopeSummaryMetricView {
  code: string;
  label: string;
  count: number;
  tone: string;
}

export interface TestDesignScopeSummaryReadinessView {
  code: string;
  label: string;
  ready: boolean;
  tone: string;
  description?: string;
}

export interface TestDesignScopeSummaryView {
  projectId?: string;
  promptKey?: string;
  policy?: TestDesignScopePolicyView;
  taskCount: number;
  candidateCount: number;
  publishRecordCount: number;
  projectBucketCount: number;
  candidateScopeMismatchCount: number;
  publishScopeMismatchCount: number;
  modelInvocationReferenceCount: number;
  publishProjectScopeRecordCount: number;
  candidateScopeCoveragePercent: number;
  publishScopeCoveragePercent: number;
  metrics: TestDesignScopeSummaryMetricView[];
  readiness: TestDesignScopeSummaryReadinessView[];
  aggregateOnly?: boolean;
  candidateIdentifierListExported?: boolean;
  roleRuleDetailExported?: boolean;
  serviceTokenValueExported?: boolean;
  generatedAt?: string;
}

export interface TestDesignCrossWpAuditDashboardView {
  wp1AuditEventCount: number;
  wp1AuditSuccessCount: number;
  wp1AuditFailureCount: number;
  wp1AuditDeniedCount: number;
  wp2InvocationCount: number;
  wp2InvocationSucceededCount: number;
  wp2InvocationFailedCount: number;
  wp2InvocationBlockedCount: number;
  wp2FallbackCount: number;
  wp2TraceSignalCount: number;
  wp3PublishedCaseCount: number;
  wp3TraceLinkCount: number;
  crossWpAuditDashboardReady?: boolean;
  auditEventDetailExported?: boolean;
  traceIdValueExported?: boolean;
  modelInvocationIdValueExported?: boolean;
  publishIdentifierValueExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignAuditOutboxOperationsView {
  totalCount: number;
  pendingCount: number;
  processingCount: number;
  doneCount: number;
  failedCount: number;
  deadCount: number;
  replayEligibleCount: number;
  replaySupported?: boolean;
  payloadExported?: boolean;
  traceIdValueExported?: boolean;
  lastErrorTextExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignQueueAlertSubscriptionView {
  id: string;
  projectId: string;
  promptKey?: string;
  alertType: string;
  channel: string;
  targetRef: string;
  thresholdSeconds?: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDesignQueueAlertOperationsView {
  policyVersion?: string;
  subscriptionCount: number;
  enabledSubscriptionCount: number;
  disabledSubscriptionCount: number;
  queuedTaskCount: number;
  staleRunningTaskCount: number;
  publishQueuedCandidateCount: number;
  stalePublishingCandidateCount: number;
  compensationEligibleCandidateCount: number;
  oldestGenerationQueuedAgeSeconds: number;
  oldestPublishQueuedAgeSeconds: number;
  generationQueueLagWarningSeconds: number;
  publishQueueLagWarningSeconds: number;
  generationQueueLagWarning?: boolean;
  generationTimeoutWarning?: boolean;
  publishQueueLagWarning?: boolean;
  publishTimeoutWarning?: boolean;
  compensationFailureWarning?: boolean;
  activeWarningCount: number;
  subscriptionConfigReady?: boolean;
  manualReplaySupported?: boolean;
  aggregateOnly?: boolean;
  eventPayloadExported?: boolean;
  detailIdentifiersExported?: boolean;
  generatedAt?: string;
}

export interface TestDesignQueuedEventReplayResult {
  projectId?: string;
  promptKey?: string;
  replayType: string;
  requestedLimit: number;
  generationTaskEvents: number;
  publishTaskEvents: number;
  publishCandidateEvents: number;
  replaySupported?: boolean;
  eventPayloadExported?: boolean;
  eventIdentifierListExported?: boolean;
  candidateIdentifierListExported?: boolean;
  aggregateOnly?: boolean;
  replayedAt?: string;
}

export interface TestDesignCompensationRunbookView {
  policyVersion?: string;
  projectId?: string;
  promptKey?: string;
  compensationEnabled?: boolean;
  automaticScheduleReady?: boolean;
  manualRunSupported?: boolean;
  scopedRunSupported?: boolean;
  effectiveBatchSize: number;
  eligibleCandidateCount: number;
  autoFirstCreateAllowed?: boolean;
  autoConflictResolveAllowed?: boolean;
  assetCaseIdentifierExported?: boolean;
  sourceRefExported?: boolean;
  errorDetailExported?: boolean;
  aggregateOnly?: boolean;
  steps: TestDesignScopeSummaryReadinessView[];
  generatedAt?: string;
}

export interface TestDesignPublishCompensationRunResult {
  projectId?: string;
  promptKey?: string;
  trigger: string;
  requestedLimit: number;
  scannedCandidates: number;
  succeededCandidates: number;
  failedCandidates: number;
  skippedCandidates: number;
  compensationEnabled?: boolean;
  manualRunSupported?: boolean;
  aggregateOnly?: boolean;
  assetCaseIdentifierExported?: boolean;
  candidateIdentifierListExported?: boolean;
  errorDetailExported?: boolean;
  runAt?: string;
}

export interface TestDesignOperationsAuditReportView {
  projectId?: string;
  promptKey?: string;
  totalOperationCount: number;
  successCount: number;
  failedCount: number;
  deniedCount: number;
  queueAlertSubscriptionMutationCount: number;
  queuedEventReplayCount: number;
  publishCompensationRunCount: number;
  auditOutboxRequeueCount: number;
  latestOperationAt?: string;
  exportSupported?: boolean;
  detailRowsExported?: boolean;
  actorIdentifierExported?: boolean;
  traceIdValueExported?: boolean;
  aggregateOnly?: boolean;
  generatedAt?: string;
}

export interface TestDesignAuditReportTemplateFieldView {
  code: string;
  label: string;
  source: string;
  exportMode: string;
  required?: boolean;
  identifierValueExported?: boolean;
  payloadExported?: boolean;
}

export interface TestDesignAuditReportTemplateSectionView {
  code: string;
  label: string;
  description?: string;
  fields: TestDesignAuditReportTemplateFieldView[];
}

export interface TestDesignAuditReportTemplateView {
  projectId?: string;
  promptKey?: string;
  templateVersion?: string;
  fieldSetVersion?: string;
  sections: TestDesignAuditReportTemplateSectionView[];
  exportSupported?: boolean;
  crossWpDetailReportSupported?: boolean;
  modelObservationDrilldownSupported?: boolean;
  identifierValuesExported?: boolean;
  payloadExported?: boolean;
  actorIdentifierExported?: boolean;
  aggregateOnly?: boolean;
  generatedAt?: string;
}

export interface TestDesignModelObservationBucketView {
  dimension: string;
  bucketKey: string;
  bucketLabel: string;
  invocationCount: number;
  succeededCount: number;
  failedCount: number;
  blockedCount: number;
  fallbackCount: number;
  inputTokenTotal: number;
  outputTokenTotal: number;
  latencyMsTotal: number;
  averageLatencyMs: number;
  totalCostText: string;
  traceSignalCount: number;
  jobSignalCount: number;
  latestInvocationAt?: string;
}

export interface TestDesignModelObservationDrilldownView {
  projectId?: string;
  promptKey?: string;
  totalInvocationCount: number;
  succeededCount: number;
  failedCount: number;
  blockedCount: number;
  fallbackCount: number;
  inputTokenTotal: number;
  outputTokenTotal: number;
  latencyMsTotal: number;
  averageLatencyMs: number;
  totalCostText: string;
  traceSignalCount: number;
  jobSignalCount: number;
  buckets: TestDesignModelObservationBucketView[];
  drilldownSupported?: boolean;
  traceIdValueExported?: boolean;
  jobIdValueExported?: boolean;
  invocationIdValueExported?: boolean;
  payloadPreviewExported?: boolean;
  providerErrorTextExported?: boolean;
  aggregateOnly?: boolean;
  generatedAt?: string;
}

export interface TestDesignCrossWpAuditDetailRowView {
  section: string;
  category: string;
  status: string;
  eventCount: number;
  successCount: number;
  failedCount: number;
  warningCount: number;
  latestEventAt?: string;
  identifierValuesExported?: boolean;
  payloadExported?: boolean;
  actorIdentifierExported?: boolean;
  aggregateOnly?: boolean;
}

export interface TestDesignCrossWpDetailAuditReportView {
  projectId?: string;
  promptKey?: string;
  templateVersion?: string;
  rowCount: number;
  rows: TestDesignCrossWpAuditDetailRowView[];
  detailReportSupported?: boolean;
  rawAuditEventExported?: boolean;
  identifierValuesExported?: boolean;
  traceIdValueExported?: boolean;
  modelInvocationIdValueExported?: boolean;
  publishIdentifierValueExported?: boolean;
  payloadExported?: boolean;
  actorIdentifierExported?: boolean;
  aggregateOnly?: boolean;
  generatedAt?: string;
}

export interface TestDesignCrossWpOperationsDashboardView {
  projectId?: string;
  promptKey?: string;
  scopePolicy?: TestDesignScopePolicyView;
  auditChainPolicy?: TestDesignAuditChainPolicyView;
  taskCount: number;
  candidateCount: number;
  publishRecordCount: number;
  projectBucketCount: number;
  candidateScopeMismatchCount: number;
  publishScopeMismatchCount: number;
  modelInvocationReferenceCount: number;
  publishProjectScopeRecordCount: number;
  candidateScopeCoveragePercent: number;
  publishScopeCoveragePercent: number;
  auditDashboard?: TestDesignCrossWpAuditDashboardView;
  auditOutbox?: TestDesignAuditOutboxOperationsView;
  metrics: TestDesignScopeSummaryMetricView[];
  readiness: TestDesignScopeSummaryReadinessView[];
  aggregateOnly?: boolean;
  detailIdentifiersExported?: boolean;
  generatedAt?: string;
  queueAlerts?: TestDesignQueueAlertOperationsView;
  compensationRunbook?: TestDesignCompensationRunbookView;
  operationsAuditReport?: TestDesignOperationsAuditReportView;
  auditReportTemplate?: TestDesignAuditReportTemplateView;
  modelObservationDrilldown?: TestDesignModelObservationDrilldownView;
  crossWpDetailAuditReport?: TestDesignCrossWpDetailAuditReportView;
}

export interface TestDesignAuditOutboxRequeueResult {
  projectId?: string;
  requestedStatus: string;
  requestedLimit: number;
  requeuedCount: number;
  replaySupported?: boolean;
  payloadExported?: boolean;
  detailIdentifiersExported?: boolean;
  generatedAt?: string;
}

export interface UpsertTestDesignQueueAlertSubscriptionPayload {
  projectId: string;
  promptKey?: string;
  alertType: string;
  channel: string;
  targetRef: string;
  thresholdSeconds?: number;
  enabled?: boolean;
}

export interface ReplayTestDesignQueuedEventsPayload {
  projectId: string;
  promptKey?: string;
  replayType: string;
  maxItems?: number;
  reason?: string;
}

export interface RunTestDesignPublishCompensationPayload {
  projectId: string;
  promptKey?: string;
  maxItems?: number;
  reason?: string;
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

export interface TestDesignTemplateList {
  items: TestDesignTemplateView[];
  total: number;
  index?: number;
  size?: number;
}

export interface CreateTestDesignTaskPayload {
  projectId: string;
  templateId?: string;
  title?: string;
  requirementIds: string[];
  contextApiIds?: string[];
  contextPageIds?: string[];
  contextFlowIds?: string[];
  environmentKey?: string;
  promptKey?: string;
  promptVersion?: string;
  coverageTypes?: string[];
  caseCountPerRequirement?: number;
  idempotencyKey?: string;
}

export interface TestDesignTemplateFilters {
  index?: number;
  size?: number;
  projectId?: string;
  enabled?: boolean;
  keyword?: string;
  includeGlobal?: boolean;
}

export interface SaveTestDesignTemplatePayload {
  projectId?: string;
  name: string;
  description?: string;
  promptKey?: string;
  promptVersion?: string;
  coverageTypes?: string[];
  generationStrategy?: string;
  coverageStrategy?: string;
  caseCountPerRequirement?: number;
  contextDefaults?: Record<string, unknown>;
  enabled?: boolean;
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

export interface TestDesignConflictOperationFilters {
  index?: number;
  size?: number;
  projectId: string;
  taskId?: string;
  action?: string;
  result?: string;
  candidateStatus?: string;
  resolutionStatus?: 'OPEN' | 'RESOLVED' | 'ALL';
  keyword?: string;
}

export interface TestDesignConflictOperationsSummary {
  totalCount: number;
  openCount: number;
  resolvedCount: number;
  duplicateReviewCount: number;
  latestConflictAt?: string;
}

export interface TestDesignConflictOperationItem {
  taskId?: string;
  taskTitle?: string;
  taskStatus?: string;
  candidateId?: string;
  candidateTitle?: string;
  candidateStatus?: string;
  candidateVersion: number;
  projectId?: string;
  requirementId?: string;
  recommendedCaseId?: string;
  record: TestDesignPublishRecordView;
  resolved: boolean;
  resolvable: boolean;
  conflictAt?: string;
}

export interface TestDesignConflictOperationsResult {
  items: TestDesignConflictOperationItem[];
  total: number;
  index: number;
  size: number;
  summary: TestDesignConflictOperationsSummary;
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

export interface TestDesignEvaluationCorpusSummaryFilters {
  index?: number;
  size?: number;
  projectId?: string;
  promptKey?: string;
}

export interface TestDesignEvaluationSampleFilters {
  index?: number;
  size?: number;
  projectId?: string;
  promptKey?: string;
  promptVersion?: string;
  status?: string;
  coverageType?: string;
  baselineVersion?: string;
  keyword?: string;
}

export interface SaveTestDesignEvaluationSamplePayload {
  projectId: string;
  sampleKey?: string;
  title: string;
  sourceType?: string;
  sourceTaskId?: string;
  sourceCandidateId?: string;
  promptKey?: string;
  promptVersion?: string;
  coverageType?: string;
  priority?: string;
  status?: string;
  baselineVersion?: string;
  requirementSummary?: string;
  expectedCaseOutline?: string;
  assertionNotes?: string;
  tags?: string;
  maintenanceNote?: string;
}

export interface TransitionTestDesignEvaluationSamplePayload {
  status: string;
  baselineVersion?: string;
  maintenanceNote?: string;
}

export interface CreateTestDesignEvaluationSampleFromCandidatePayload {
  candidateId: string;
  sampleKey?: string;
  status?: string;
  baselineVersion?: string;
  maintenanceNote?: string;
}

export interface TestDesignCalibrationRunFilters {
  index?: number;
  size?: number;
  projectId?: string;
  promptKey?: string;
  promptVersion?: string;
  baselineVersion?: string;
  status?: string;
}

export interface RequestTestDesignCalibrationRunPayload {
  projectId: string;
  promptKey?: string;
  promptVersion?: string;
  baselineVersion?: string;
  runMode?: string;
  notes?: string;
}

export interface TestDesignScopeSummaryFilters {
  index?: number;
  size?: number;
  projectId?: string;
  promptKey?: string;
}

export interface TestDesignCrossWpOperationsFilters {
  projectId?: string;
  promptKey?: string;
}

export interface RequeueTestDesignAuditOutboxPayload {
  projectId: string;
  status?: string;
  maxItems?: number;
  reason?: string;
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

export interface TestDesignContextPolicyFilters {
  environmentKey?: string;
}

export interface RequestTestDesignContextPolicyOverridePayload {
  contextLinkedAssetsPerRequirement?: number;
  contextExplicitAssetsPerType?: number;
  contextExistingCasesPerRequirement?: number;
  contextRequirementDescriptionChars?: number;
  contextAcceptanceCriteriaChars?: number;
  contextAssetSchemaChars?: number;
  changeReasonCode?: string;
  policyBody?: string;
  policyDiffSummary?: string;
  workOrderKey?: string;
  workOrderTitle?: string;
  workOrderUrl?: string;
  requestNote?: string;
}

export interface ReviewTestDesignContextPolicyOverridePayload {
  approvalReasonCode?: string;
  reviewNote?: string;
  workOrderStatus?: string;
}

export interface AddTestDesignContextPolicyNotePayload {
  noteType?: string;
  noteText?: string;
}

export interface RequestTestDesignReleaseReadinessApprovalPayload {
  exceptionReasonCode?: string;
  exceptionSummary?: string;
  riskMitigation?: string;
  workOrderKey?: string;
  workOrderTitle?: string;
  workOrderUrl?: string;
  requestNote?: string;
}

export interface ReviewTestDesignReleaseReadinessApprovalPayload {
  approvalReasonCode?: string;
  reviewNote?: string;
  workOrderStatus?: string;
}

export interface AddTestDesignReleaseReadinessNotePayload {
  noteType?: string;
  noteText?: string;
}

export interface RequestTestDesignReportArchiveApprovalPayload {
  reasonCode?: string;
  requestSummary?: string;
  workOrderKey?: string;
  workOrderTitle?: string;
  workOrderUrl?: string;
  requestNote?: string;
}

export interface ReviewTestDesignReportArchiveApprovalPayload {
  approvalReasonCode?: string;
  reviewNote?: string;
  workOrderStatus?: string;
}

export interface AddTestDesignReportArchiveNotePayload {
  noteType?: string;
  noteText?: string;
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
    if (typeof value === 'boolean') params.set(key, String(value));
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
    modelObservationPolicy: normalizeTestDesignModelObservationPolicy(
      item.modelObservationPolicy ?? item.model_observation_policy
    ),
    generationOrchestrationPolicy: normalizeTestDesignGenerationOrchestrationPolicy(
      item.generationOrchestrationPolicy ?? item.generation_orchestration_policy
    ),
    archivePolicy: normalizeTestDesignArchivePolicy(item.archivePolicy ?? item.archive_policy),
    reportManifestPolicy: normalizeTestDesignReportManifestPolicy(
      item.reportManifestPolicy ?? item.report_manifest_policy
    ),
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
    modelObservationPolicy: normalizeTestDesignModelObservationPolicy(
      item.modelObservationPolicy ?? item.model_observation_policy
    ),
    generationOrchestrationPolicy: normalizeTestDesignGenerationOrchestrationPolicy(
      item.generationOrchestrationPolicy ?? item.generation_orchestration_policy
    ),
    archivePolicy: normalizeTestDesignArchivePolicy(item.archivePolicy ?? item.archive_policy),
    reportManifestPolicy: normalizeTestDesignReportManifestPolicy(
      item.reportManifestPolicy ?? item.report_manifest_policy
    ),
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
    externalShareApprovalWorkflowReady: optionalBoolean(
      raw.externalShareApprovalWorkflowReady ?? raw.external_share_approval_workflow_ready
    ),
    workOrderWorkflowReady: optionalBoolean(raw.workOrderWorkflowReady ?? raw.work_order_workflow_ready),
    externalSharingAllowed: optionalBoolean(raw.externalSharingAllowed ?? raw.external_sharing_allowed),
    retentionPolicyTracked: optionalBoolean(raw.retentionPolicyTracked ?? raw.retention_policy_tracked),
    archiveStorageReady: optionalBoolean(raw.archiveStorageReady ?? raw.archive_storage_ready),
    archiveContentStored: optionalBoolean(raw.archiveContentStored ?? raw.archive_content_stored),
    lineIntegrityIndexReady: optionalBoolean(raw.lineIntegrityIndexReady ?? raw.line_integrity_index_ready),
    archiveContentExported: optionalBoolean(raw.archiveContentExported ?? raw.archive_content_exported),
    archivePathExported: optionalBoolean(raw.archivePathExported ?? raw.archive_path_exported),
    archiveNotesExported: optionalBoolean(raw.archiveNotesExported ?? raw.archive_notes_exported),
    approvalNotesExported: optionalBoolean(raw.approvalNotesExported ?? raw.approval_notes_exported),
    ticketUrlExported: optionalBoolean(raw.ticketUrlExported ?? raw.ticket_url_exported),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignReportManifestPolicy(
  raw: unknown
): TestDesignReportManifestPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    schemaVersion: optionalString(raw.schemaVersion) ?? optionalString(raw.schema_version),
    fieldSetVersion: optionalString(raw.fieldSetVersion) ?? optionalString(raw.field_set_version),
    manifestMode: optionalString(raw.manifestMode) ?? optionalString(raw.manifest_mode),
    rowCountTracked: optionalBoolean(raw.rowCountTracked ?? raw.row_count_tracked),
    completionStatusTracked: optionalBoolean(
      raw.completionStatusTracked ?? raw.completion_status_tracked
    ),
    archiveReconciliationReady: optionalBoolean(
      raw.archiveReconciliationReady ?? raw.archive_reconciliation_ready
    ),
    rowIntegrityStored: optionalBoolean(raw.rowIntegrityStored ?? raw.row_integrity_stored),
    rowIntegrityIndexReady: optionalBoolean(raw.rowIntegrityIndexReady ?? raw.row_integrity_index_ready),
    detailRowsExported: optionalBoolean(raw.detailRowsExported ?? raw.detail_rows_exported),
    rowIntegrityValueExported: optionalBoolean(
      raw.rowIntegrityValueExported ?? raw.row_integrity_value_exported
    ),
    rowContentSummaryExported: optionalBoolean(
      raw.rowContentSummaryExported ?? raw.row_content_summary_exported
    ),
    candidateIdentifierListExported: optionalBoolean(
      raw.candidateIdentifierListExported ?? raw.candidate_identifier_list_exported
    ),
    traceIdentifierListExported: optionalBoolean(
      raw.traceIdentifierListExported ?? raw.trace_identifier_list_exported
    ),
    auditIdentifierListExported: optionalBoolean(
      raw.auditIdentifierListExported ?? raw.audit_identifier_list_exported
    ),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignModelObservationPolicy(
  raw: unknown
): TestDesignModelObservationPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    observationMode: optionalString(raw.observationMode) ?? optionalString(raw.observation_mode),
    wp2InvocationReferenceTracked: optionalBoolean(
      raw.wp2InvocationReferenceTracked ?? raw.wp2_invocation_reference_tracked
    ),
    traceIdTracked: optionalBoolean(raw.traceIdTracked ?? raw.trace_id_tracked),
    jobIdTracked: optionalBoolean(raw.jobIdTracked ?? raw.job_id_tracked),
    routingMetadataTracked: optionalBoolean(
      raw.routingMetadataTracked ?? raw.routing_metadata_tracked
    ),
    tokenUsageTracked: optionalBoolean(raw.tokenUsageTracked ?? raw.token_usage_tracked),
    latencyTracked: optionalBoolean(raw.latencyTracked ?? raw.latency_tracked),
    costTracked: optionalBoolean(raw.costTracked ?? raw.cost_tracked),
    fallbackTracked: optionalBoolean(raw.fallbackTracked ?? raw.fallback_tracked),
    promptPayloadStored: optionalBoolean(raw.promptPayloadStored ?? raw.prompt_payload_stored),
    payloadPreviewExported: optionalBoolean(raw.payloadPreviewExported ?? raw.payload_preview_exported),
    traceIdValueExported: optionalBoolean(raw.traceIdValueExported ?? raw.trace_id_value_exported),
    jobIdValueExported: optionalBoolean(raw.jobIdValueExported ?? raw.job_id_value_exported),
    invocationIdValueExported: optionalBoolean(
      raw.invocationIdValueExported ?? raw.invocation_id_value_exported
    ),
    providerErrorTextExported: optionalBoolean(
      raw.providerErrorTextExported ?? raw.provider_error_text_exported
    ),
    actorServiceExported: optionalBoolean(raw.actorServiceExported ?? raw.actor_service_exported),
    aggregateOnly: optionalBoolean(raw.aggregateOnly ?? raw.aggregate_only)
  };
}

export function normalizeTestDesignGenerationOrchestrationPolicy(
  raw: unknown
): TestDesignGenerationOrchestrationPolicyView | undefined {
  if (!isRecord(raw)) {
    return undefined;
  }
  return {
    policyVersion: optionalString(raw.policyVersion) ?? optionalString(raw.policy_version),
    orchestrationMode: optionalString(raw.orchestrationMode) ?? optionalString(raw.orchestration_mode),
    asyncGenerationEnabled: optionalBoolean(raw.asyncGenerationEnabled ?? raw.async_generation_enabled),
    conditionalRunClaimSupported: optionalBoolean(
      raw.conditionalRunClaimSupported ?? raw.conditional_run_claim_supported
    ),
    idempotentCreateReplaySupported: optionalBoolean(
      raw.idempotentCreateReplaySupported ?? raw.idempotent_create_replay_supported
    ),
    duplicateEventReplaySafe: optionalBoolean(
      raw.duplicateEventReplaySafe ?? raw.duplicate_event_replay_safe
    ),
    eventRecoveryEnabled: optionalBoolean(raw.eventRecoveryEnabled ?? raw.event_recovery_enabled),
    queuedEventReplaySupported: optionalBoolean(
      raw.queuedEventReplaySupported ?? raw.queued_event_replay_supported
    ),
    runningTimeoutRecoveryEnabled: optionalBoolean(
      raw.runningTimeoutRecoveryEnabled ?? raw.running_timeout_recovery_enabled
    ),
    explicitRetryRequiredAfterTimeout: optionalBoolean(
      raw.explicitRetryRequiredAfterTimeout ?? raw.explicit_retry_required_after_timeout
    ),
    manualTaskRetrySupported: optionalBoolean(raw.manualTaskRetrySupported ?? raw.manual_task_retry_supported),
    manualQueuedEventReplayReady: optionalBoolean(
      raw.manualQueuedEventReplayReady ?? raw.manual_queued_event_replay_ready
    ),
    queueLagMetricReady: optionalBoolean(raw.queueLagMetricReady ?? raw.queue_lag_metric_ready),
    timeoutAlertReady: optionalBoolean(raw.timeoutAlertReady ?? raw.timeout_alert_ready),
    multiInstanceLoadTestEvidenceReady: optionalBoolean(
      raw.multiInstanceLoadTestEvidenceReady ?? raw.multi_instance_load_test_evidence_ready
    ),
    eventPayloadExported: optionalBoolean(raw.eventPayloadExported ?? raw.event_payload_exported),
    eventIdentifierListExported: optionalBoolean(
      raw.eventIdentifierListExported ?? raw.event_identifier_list_exported
    ),
    queueMessageBodyExported: optionalBoolean(raw.queueMessageBodyExported ?? raw.queue_message_body_exported),
    recoveryDetailRowsExported: optionalBoolean(
      raw.recoveryDetailRowsExported ?? raw.recovery_detail_rows_exported
    ),
    effectiveRecoveryBatchSize: optionalNumber(
      raw.effectiveRecoveryBatchSize ?? raw.effective_recovery_batch_size
    ),
    runningTimeoutSeconds: optionalNumber(raw.runningTimeoutSeconds ?? raw.running_timeout_seconds),
    queueLagWarningSeconds: optionalNumber(raw.queueLagWarningSeconds ?? raw.queue_lag_warning_seconds),
    queuedTaskCount: optionalNumber(raw.queuedTaskCount ?? raw.queued_task_count),
    runningTaskCount: optionalNumber(raw.runningTaskCount ?? raw.running_task_count),
    oldestQueuedAgeSeconds: optionalNumber(raw.oldestQueuedAgeSeconds ?? raw.oldest_queued_age_seconds),
    staleRunningTaskCount: optionalNumber(raw.staleRunningTaskCount ?? raw.stale_running_task_count),
    queueLagWarning: optionalBoolean(raw.queueLagWarning ?? raw.queue_lag_warning),
    timeoutWarning: optionalBoolean(raw.timeoutWarning ?? raw.timeout_warning),
    queuedStatusSignal: optionalNumber(raw.queuedStatusSignal ?? raw.queued_status_signal),
    runningStatusSignal: optionalNumber(raw.runningStatusSignal ?? raw.running_status_signal),
    timeoutFailureSignal: optionalNumber(raw.timeoutFailureSignal ?? raw.timeout_failure_signal),
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

export function normalizeTestDesignReviewDiffItem(raw: unknown): TestDesignReviewDiffItemView {
  const item = isRecord(raw) ? raw : {};
  return {
    field: stringValue(item.field),
    before: optionalString(item.before),
    after: optionalString(item.after)
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
    diffItems: listItems(item.diffItems ?? item.diff_items).map(normalizeTestDesignReviewDiffItem),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignTemplate(raw: unknown): TestDesignTemplateView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    name: stringValue(item.name, '未命名模板'),
    description: optionalString(item.description),
    promptKey: stringValue(item.promptKey ?? item.prompt_key),
    promptVersion: stringValue(item.promptVersion ?? item.prompt_version),
    coverageTypes: stringArrayValue(item.coverageTypes ?? item.coverage_types),
    generationStrategy: stringValue(item.generationStrategy ?? item.generation_strategy, 'BALANCED'),
    coverageStrategy: stringValue(item.coverageStrategy ?? item.coverage_strategy, 'DEFAULT_ORDER'),
    caseCountPerRequirement: numberValue(item.caseCountPerRequirement ?? item.case_count_per_requirement, 1),
    contextDefaults: recordValue(item.contextDefaults ?? item.context_defaults ?? item.context_defaults_json),
    enabled: optionalBoolean(item.enabled) ?? true,
    createdBy: optionalString(item.createdBy) ?? optionalString(item.created_by),
    updatedBy: optionalString(item.updatedBy) ?? optionalString(item.updated_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
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

export function normalizeTestDesignEvaluationCorpusSummary(raw: unknown): TestDesignEvaluationCorpusSummaryView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    policy: normalizeTestDesignEvaluationCorpusPolicy(item.policy),
    taskCount: numberValue(item.taskCount ?? item.task_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    promptVersionCount: numberValue(item.promptVersionCount ?? item.prompt_version_count, 0),
    readinessDistribution: listItems(item.readinessDistribution ?? item.readiness_distribution)
      .map(normalizeTestDesignQualityDistributionItem),
    feedbackSignalCount: numberValue(item.feedbackSignalCount ?? item.feedback_signal_count, 0),
    sampleCandidateCount: numberValue(item.sampleCandidateCount ?? item.sample_candidate_count, 0),
    sampleExplanationCount: numberValue(item.sampleExplanationCount ?? item.sample_explanation_count, 0),
    sampleExplanationCoveragePercent: numberValue(
      item.sampleExplanationCoveragePercent ?? item.sample_explanation_coverage_percent,
      0
    ),
    maintainedSampleCount: numberValue(item.maintainedSampleCount ?? item.maintained_sample_count, 0),
    goldenSampleCount: numberValue(item.goldenSampleCount ?? item.golden_sample_count, 0),
    frozenSampleCount: numberValue(item.frozenSampleCount ?? item.frozen_sample_count, 0),
    deprecatedSampleCount: numberValue(item.deprecatedSampleCount ?? item.deprecated_sample_count, 0),
    baselineVersionCount: numberValue(item.baselineVersionCount ?? item.baseline_version_count, 0),
    calibrationRunCount: numberValue(item.calibrationRunCount ?? item.calibration_run_count, 0),
    latestCalibrationStatus: optionalString(item.latestCalibrationStatus)
      ?? optionalString(item.latest_calibration_status),
    latestCalibrationAt: optionalString(item.latestCalibrationAt) ?? optionalString(item.latest_calibration_at),
    sampleMaintenanceReady: optionalBoolean(item.sampleMaintenanceReady ?? item.sample_maintenance_ready),
    longTermCalibrationReady: optionalBoolean(
      item.longTermCalibrationReady ?? item.long_term_calibration_ready
    ),
    operationsConsoleReady: optionalBoolean(item.operationsConsoleReady ?? item.operations_console_ready),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    corpusRowExported: optionalBoolean(item.corpusRowExported ?? item.corpus_row_exported),
    candidateBodyExported: optionalBoolean(item.candidateBodyExported ?? item.candidate_body_exported),
    reviewCommentExported: optionalBoolean(item.reviewCommentExported ?? item.review_comment_exported),
    promptBodyExported: optionalBoolean(item.promptBodyExported ?? item.prompt_body_exported),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
  };
}

export function normalizeTestDesignEvaluationSample(raw: unknown): TestDesignEvaluationSampleView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    sampleKey: stringValue(item.sampleKey ?? item.sample_key),
    title: stringValue(item.title),
    sourceType: stringValue(item.sourceType ?? item.source_type, 'MANUAL'),
    sourceTaskId: optionalString(item.sourceTaskId) ?? optionalString(item.source_task_id),
    sourceCandidateId: optionalString(item.sourceCandidateId) ?? optionalString(item.source_candidate_id),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    promptVersion: optionalString(item.promptVersion) ?? optionalString(item.prompt_version),
    coverageType: stringValue(item.coverageType ?? item.coverage_type, 'FUNCTIONAL'),
    priority: stringValue(item.priority, 'MEDIUM'),
    status: stringValue(item.status, 'CANDIDATE'),
    baselineVersion: optionalString(item.baselineVersion) ?? optionalString(item.baseline_version),
    requirementSummary: optionalString(item.requirementSummary) ?? optionalString(item.requirement_summary),
    expectedCaseOutline: optionalString(item.expectedCaseOutline) ?? optionalString(item.expected_case_outline),
    assertionNotes: optionalString(item.assertionNotes) ?? optionalString(item.assertion_notes),
    tags: optionalString(item.tags),
    maintenanceNote: optionalString(item.maintenanceNote) ?? optionalString(item.maintenance_note),
    sampleDigest: optionalString(item.sampleDigest) ?? optionalString(item.sample_digest),
    sensitiveScanStatus: optionalString(item.sensitiveScanStatus) ?? optionalString(item.sensitive_scan_status),
    createdBy: optionalString(item.createdBy) ?? optionalString(item.created_by),
    updatedBy: optionalString(item.updatedBy) ?? optionalString(item.updated_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignEvaluationSampleList(raw: unknown): TestDesignEvaluationSampleList {
  const item = isRecord(raw) ? raw : {};
  return {
    items: listItems(item.items).map(normalizeTestDesignEvaluationSample),
    index: numberValue(item.index, 0),
    size: numberValue(item.size, 20),
    total: numberValue(item.total, listItems(item.items).length)
  };
}

export function normalizeTestDesignEvaluationSampleSummary(raw: unknown): TestDesignEvaluationSampleSummaryView {
  const item = isRecord(raw) ? raw : {};
  return {
    totalCount: numberValue(item.totalCount ?? item.total_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    goldenCount: numberValue(item.goldenCount ?? item.golden_count, 0),
    frozenCount: numberValue(item.frozenCount ?? item.frozen_count, 0),
    deprecatedCount: numberValue(item.deprecatedCount ?? item.deprecated_count, 0),
    baselineVersionCount: numberValue(item.baselineVersionCount ?? item.baseline_version_count, 0),
    latestUpdatedAt: optionalString(item.latestUpdatedAt) ?? optionalString(item.latest_updated_at),
    sampleMaintenanceReady: optionalBoolean(item.sampleMaintenanceReady ?? item.sample_maintenance_ready),
    baselineReady: optionalBoolean(item.baselineReady ?? item.baseline_ready)
  };
}

export function normalizeTestDesignCalibrationRun(raw: unknown): TestDesignCalibrationRunView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    promptVersion: optionalString(item.promptVersion) ?? optionalString(item.prompt_version),
    baselineVersion: optionalString(item.baselineVersion) ?? optionalString(item.baseline_version),
    runMode: stringValue(item.runMode ?? item.run_mode, 'MANUAL'),
    status: stringValue(item.status, 'UNKNOWN'),
    sampleCount: numberValue(item.sampleCount ?? item.sample_count, 0),
    goldenSampleCount: numberValue(item.goldenSampleCount ?? item.golden_sample_count, 0),
    taskCount: numberValue(item.taskCount ?? item.task_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    stepCompletePercent: numberValue(item.stepCompletePercent ?? item.step_complete_percent, 0),
    expectedCompletePercent: numberValue(item.expectedCompletePercent ?? item.expected_complete_percent, 0),
    lowConfidencePercent: numberValue(item.lowConfidencePercent ?? item.low_confidence_percent, 0),
    errorPercent: numberValue(item.errorPercent ?? item.error_percent, 0),
    duplicateKeyCollisionCount: numberValue(
      item.duplicateKeyCollisionCount ?? item.duplicate_key_collision_count,
      0
    ),
    feedbackSignalCount: numberValue(item.feedbackSignalCount ?? item.feedback_signal_count, 0),
    readinessStatus: optionalString(item.readinessStatus) ?? optionalString(item.readiness_status),
    readinessBlockingCount: numberValue(item.readinessBlockingCount ?? item.readiness_blocking_count, 0),
    readinessWarningCount: numberValue(item.readinessWarningCount ?? item.readiness_warning_count, 0),
    regressionCount: numberValue(item.regressionCount ?? item.regression_count, 0),
    baselineDigest: optionalString(item.baselineDigest) ?? optionalString(item.baseline_digest),
    resultDigest: optionalString(item.resultDigest) ?? optionalString(item.result_digest),
    notes: optionalString(item.notes),
    runBy: optionalString(item.runBy) ?? optionalString(item.run_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignCalibrationSummary(raw: unknown): TestDesignCalibrationSummaryView {
  const item = isRecord(raw) ? raw : {};
  return {
    totalRunCount: numberValue(item.totalRunCount ?? item.total_run_count, 0),
    passedRunCount: numberValue(item.passedRunCount ?? item.passed_run_count, 0),
    warningRunCount: numberValue(item.warningRunCount ?? item.warning_run_count, 0),
    blockedRunCount: numberValue(item.blockedRunCount ?? item.blocked_run_count, 0),
    latestStatus: optionalString(item.latestStatus) ?? optionalString(item.latest_status),
    latestRunAt: optionalString(item.latestRunAt) ?? optionalString(item.latest_run_at),
    longTermCalibrationReady: optionalBoolean(
      item.longTermCalibrationReady ?? item.long_term_calibration_ready
    ),
    baselineReady: optionalBoolean(item.baselineReady ?? item.baseline_ready)
  };
}

export function normalizeTestDesignCalibrationRunList(raw: unknown): TestDesignCalibrationRunList {
  const item = isRecord(raw) ? raw : {};
  return {
    items: listItems(item.items).map(normalizeTestDesignCalibrationRun),
    index: numberValue(item.index, 0),
    size: numberValue(item.size, 20),
    total: numberValue(item.total, listItems(item.items).length),
    summary: normalizeTestDesignCalibrationSummary(item.summary)
  };
}

export function normalizeTestDesignScopeSummaryMetric(raw: unknown): TestDesignScopeSummaryMetricView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    label: stringValue(item.label, stringValue(item.code, 'UNKNOWN')),
    count: numberValue(item.count, 0),
    tone: stringValue(item.tone, 'neutral')
  };
}

export function normalizeTestDesignScopeSummaryReadiness(raw: unknown): TestDesignScopeSummaryReadinessView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    label: stringValue(item.label, stringValue(item.code, 'UNKNOWN')),
    ready: Boolean(item.ready),
    tone: stringValue(item.tone, 'neutral'),
    description: optionalString(item.description)
  };
}

export function normalizeTestDesignScopeSummary(raw: unknown): TestDesignScopeSummaryView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    policy: normalizeTestDesignScopePolicy(item.policy),
    taskCount: numberValue(item.taskCount ?? item.task_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    publishRecordCount: numberValue(item.publishRecordCount ?? item.publish_record_count, 0),
    projectBucketCount: numberValue(item.projectBucketCount ?? item.project_bucket_count, 0),
    candidateScopeMismatchCount: numberValue(
      item.candidateScopeMismatchCount ?? item.candidate_scope_mismatch_count,
      0
    ),
    publishScopeMismatchCount: numberValue(
      item.publishScopeMismatchCount ?? item.publish_scope_mismatch_count,
      0
    ),
    modelInvocationReferenceCount: numberValue(
      item.modelInvocationReferenceCount ?? item.model_invocation_reference_count,
      0
    ),
    publishProjectScopeRecordCount: numberValue(
      item.publishProjectScopeRecordCount ?? item.publish_project_scope_record_count,
      0
    ),
    candidateScopeCoveragePercent: numberValue(
      item.candidateScopeCoveragePercent ?? item.candidate_scope_coverage_percent,
      0
    ),
    publishScopeCoveragePercent: numberValue(
      item.publishScopeCoveragePercent ?? item.publish_scope_coverage_percent,
      0
    ),
    metrics: listItems(item.metrics).map(normalizeTestDesignScopeSummaryMetric),
    readiness: listItems(item.readiness).map(normalizeTestDesignScopeSummaryReadiness),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    candidateIdentifierListExported: optionalBoolean(
      item.candidateIdentifierListExported ?? item.candidate_identifier_list_exported
    ),
    roleRuleDetailExported: optionalBoolean(item.roleRuleDetailExported ?? item.role_rule_detail_exported),
    serviceTokenValueExported: optionalBoolean(
      item.serviceTokenValueExported ?? item.service_token_value_exported
    ),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
  };
}

export function normalizeTestDesignCrossWpAuditDashboard(raw: unknown): TestDesignCrossWpAuditDashboardView {
  const item = isRecord(raw) ? raw : {};
  return {
    wp1AuditEventCount: numberValue(item.wp1AuditEventCount ?? item.wp1_audit_event_count, 0),
    wp1AuditSuccessCount: numberValue(item.wp1AuditSuccessCount ?? item.wp1_audit_success_count, 0),
    wp1AuditFailureCount: numberValue(item.wp1AuditFailureCount ?? item.wp1_audit_failure_count, 0),
    wp1AuditDeniedCount: numberValue(item.wp1AuditDeniedCount ?? item.wp1_audit_denied_count, 0),
    wp2InvocationCount: numberValue(item.wp2InvocationCount ?? item.wp2_invocation_count, 0),
    wp2InvocationSucceededCount: numberValue(
      item.wp2InvocationSucceededCount ?? item.wp2_invocation_succeeded_count,
      0
    ),
    wp2InvocationFailedCount: numberValue(item.wp2InvocationFailedCount ?? item.wp2_invocation_failed_count, 0),
    wp2InvocationBlockedCount: numberValue(
      item.wp2InvocationBlockedCount ?? item.wp2_invocation_blocked_count,
      0
    ),
    wp2FallbackCount: numberValue(item.wp2FallbackCount ?? item.wp2_fallback_count, 0),
    wp2TraceSignalCount: numberValue(item.wp2TraceSignalCount ?? item.wp2_trace_signal_count, 0),
    wp3PublishedCaseCount: numberValue(item.wp3PublishedCaseCount ?? item.wp3_published_case_count, 0),
    wp3TraceLinkCount: numberValue(item.wp3TraceLinkCount ?? item.wp3_trace_link_count, 0),
    crossWpAuditDashboardReady: optionalBoolean(
      item.crossWpAuditDashboardReady ?? item.cross_wp_audit_dashboard_ready
    ),
    auditEventDetailExported: optionalBoolean(
      item.auditEventDetailExported ?? item.audit_event_detail_exported
    ),
    traceIdValueExported: optionalBoolean(item.traceIdValueExported ?? item.trace_id_value_exported),
    modelInvocationIdValueExported: optionalBoolean(
      item.modelInvocationIdValueExported ?? item.model_invocation_id_value_exported
    ),
    publishIdentifierValueExported: optionalBoolean(
      item.publishIdentifierValueExported ?? item.publish_identifier_value_exported
    ),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only)
  };
}

export function normalizeTestDesignAuditOutboxOperations(raw: unknown): TestDesignAuditOutboxOperationsView {
  const item = isRecord(raw) ? raw : {};
  return {
    totalCount: numberValue(item.totalCount ?? item.total_count, 0),
    pendingCount: numberValue(item.pendingCount ?? item.pending_count, 0),
    processingCount: numberValue(item.processingCount ?? item.processing_count, 0),
    doneCount: numberValue(item.doneCount ?? item.done_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    deadCount: numberValue(item.deadCount ?? item.dead_count, 0),
    replayEligibleCount: numberValue(item.replayEligibleCount ?? item.replay_eligible_count, 0),
    replaySupported: optionalBoolean(item.replaySupported ?? item.replay_supported),
    payloadExported: optionalBoolean(item.payloadExported ?? item.payload_exported),
    traceIdValueExported: optionalBoolean(item.traceIdValueExported ?? item.trace_id_value_exported),
    lastErrorTextExported: optionalBoolean(item.lastErrorTextExported ?? item.last_error_text_exported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only)
  };
}

export function normalizeTestDesignQueueAlertSubscription(
  raw: unknown
): TestDesignQueueAlertSubscriptionView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    projectId: stringValue(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    alertType: stringValue(item.alertType ?? item.alert_type),
    channel: stringValue(item.channel),
    targetRef: stringValue(item.targetRef ?? item.target_ref),
    thresholdSeconds: optionalNumber(item.thresholdSeconds ?? item.threshold_seconds),
    enabled: optionalBoolean(item.enabled) ?? true,
    createdAt: optionalString(item.createdAt ?? item.created_at),
    updatedAt: optionalString(item.updatedAt ?? item.updated_at)
  };
}

export function normalizeTestDesignQueueAlertOperations(
  raw: unknown
): TestDesignQueueAlertOperationsView {
  const item = isRecord(raw) ? raw : {};
  return {
    policyVersion: optionalString(item.policyVersion ?? item.policy_version),
    subscriptionCount: numberValue(item.subscriptionCount ?? item.subscription_count, 0),
    enabledSubscriptionCount: numberValue(
      item.enabledSubscriptionCount ?? item.enabled_subscription_count,
      0
    ),
    disabledSubscriptionCount: numberValue(
      item.disabledSubscriptionCount ?? item.disabled_subscription_count,
      0
    ),
    queuedTaskCount: numberValue(item.queuedTaskCount ?? item.queued_task_count, 0),
    staleRunningTaskCount: numberValue(item.staleRunningTaskCount ?? item.stale_running_task_count, 0),
    publishQueuedCandidateCount: numberValue(
      item.publishQueuedCandidateCount ?? item.publish_queued_candidate_count,
      0
    ),
    stalePublishingCandidateCount: numberValue(
      item.stalePublishingCandidateCount ?? item.stale_publishing_candidate_count,
      0
    ),
    compensationEligibleCandidateCount: numberValue(
      item.compensationEligibleCandidateCount ?? item.compensation_eligible_candidate_count,
      0
    ),
    oldestGenerationQueuedAgeSeconds: numberValue(
      item.oldestGenerationQueuedAgeSeconds ?? item.oldest_generation_queued_age_seconds,
      0
    ),
    oldestPublishQueuedAgeSeconds: numberValue(
      item.oldestPublishQueuedAgeSeconds ?? item.oldest_publish_queued_age_seconds,
      0
    ),
    generationQueueLagWarningSeconds: numberValue(
      item.generationQueueLagWarningSeconds ?? item.generation_queue_lag_warning_seconds,
      0
    ),
    publishQueueLagWarningSeconds: numberValue(
      item.publishQueueLagWarningSeconds ?? item.publish_queue_lag_warning_seconds,
      0
    ),
    generationQueueLagWarning: optionalBoolean(
      item.generationQueueLagWarning ?? item.generation_queue_lag_warning
    ),
    generationTimeoutWarning: optionalBoolean(
      item.generationTimeoutWarning ?? item.generation_timeout_warning
    ),
    publishQueueLagWarning: optionalBoolean(item.publishQueueLagWarning ?? item.publish_queue_lag_warning),
    publishTimeoutWarning: optionalBoolean(item.publishTimeoutWarning ?? item.publish_timeout_warning),
    compensationFailureWarning: optionalBoolean(
      item.compensationFailureWarning ?? item.compensation_failure_warning
    ),
    activeWarningCount: numberValue(item.activeWarningCount ?? item.active_warning_count, 0),
    subscriptionConfigReady: optionalBoolean(
      item.subscriptionConfigReady ?? item.subscription_config_ready
    ),
    manualReplaySupported: optionalBoolean(item.manualReplaySupported ?? item.manual_replay_supported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    eventPayloadExported: optionalBoolean(item.eventPayloadExported ?? item.event_payload_exported),
    detailIdentifiersExported: optionalBoolean(
      item.detailIdentifiersExported ?? item.detail_identifiers_exported
    ),
    generatedAt: optionalString(item.generatedAt ?? item.generated_at)
  };
}

export function normalizeTestDesignQueuedEventReplayResult(
  raw: unknown
): TestDesignQueuedEventReplayResult {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    replayType: stringValue(item.replayType ?? item.replay_type, 'ALL'),
    requestedLimit: numberValue(item.requestedLimit ?? item.requested_limit, 0),
    generationTaskEvents: numberValue(item.generationTaskEvents ?? item.generation_task_events, 0),
    publishTaskEvents: numberValue(item.publishTaskEvents ?? item.publish_task_events, 0),
    publishCandidateEvents: numberValue(item.publishCandidateEvents ?? item.publish_candidate_events, 0),
    replaySupported: optionalBoolean(item.replaySupported ?? item.replay_supported),
    eventPayloadExported: optionalBoolean(item.eventPayloadExported ?? item.event_payload_exported),
    eventIdentifierListExported: optionalBoolean(
      item.eventIdentifierListExported ?? item.event_identifier_list_exported
    ),
    candidateIdentifierListExported: optionalBoolean(
      item.candidateIdentifierListExported ?? item.candidate_identifier_list_exported
    ),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    replayedAt: optionalString(item.replayedAt ?? item.replayed_at)
  };
}

export function normalizeTestDesignCompensationRunbook(
  raw: unknown
): TestDesignCompensationRunbookView {
  const item = isRecord(raw) ? raw : {};
  return {
    policyVersion: optionalString(item.policyVersion ?? item.policy_version),
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    compensationEnabled: optionalBoolean(item.compensationEnabled ?? item.compensation_enabled),
    automaticScheduleReady: optionalBoolean(
      item.automaticScheduleReady ?? item.automatic_schedule_ready
    ),
    manualRunSupported: optionalBoolean(item.manualRunSupported ?? item.manual_run_supported),
    scopedRunSupported: optionalBoolean(item.scopedRunSupported ?? item.scoped_run_supported),
    effectiveBatchSize: numberValue(item.effectiveBatchSize ?? item.effective_batch_size, 0),
    eligibleCandidateCount: numberValue(item.eligibleCandidateCount ?? item.eligible_candidate_count, 0),
    autoFirstCreateAllowed: optionalBoolean(item.autoFirstCreateAllowed ?? item.auto_first_create_allowed),
    autoConflictResolveAllowed: optionalBoolean(
      item.autoConflictResolveAllowed ?? item.auto_conflict_resolve_allowed
    ),
    assetCaseIdentifierExported: optionalBoolean(
      item.assetCaseIdentifierExported ?? item.asset_case_identifier_exported
    ),
    sourceRefExported: optionalBoolean(item.sourceRefExported ?? item.source_ref_exported),
    errorDetailExported: optionalBoolean(item.errorDetailExported ?? item.error_detail_exported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    steps: listItems(item.steps).map(normalizeTestDesignScopeSummaryReadiness),
    generatedAt: optionalString(item.generatedAt ?? item.generated_at)
  };
}

export function normalizeTestDesignPublishCompensationRunResult(
  raw: unknown
): TestDesignPublishCompensationRunResult {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    trigger: stringValue(item.trigger, 'manual'),
    requestedLimit: numberValue(item.requestedLimit ?? item.requested_limit, 0),
    scannedCandidates: numberValue(item.scannedCandidates ?? item.scanned_candidates, 0),
    succeededCandidates: numberValue(item.succeededCandidates ?? item.succeeded_candidates, 0),
    failedCandidates: numberValue(item.failedCandidates ?? item.failed_candidates, 0),
    skippedCandidates: numberValue(item.skippedCandidates ?? item.skipped_candidates, 0),
    compensationEnabled: optionalBoolean(item.compensationEnabled ?? item.compensation_enabled),
    manualRunSupported: optionalBoolean(item.manualRunSupported ?? item.manual_run_supported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    assetCaseIdentifierExported: optionalBoolean(
      item.assetCaseIdentifierExported ?? item.asset_case_identifier_exported
    ),
    candidateIdentifierListExported: optionalBoolean(
      item.candidateIdentifierListExported ?? item.candidate_identifier_list_exported
    ),
    errorDetailExported: optionalBoolean(item.errorDetailExported ?? item.error_detail_exported),
    runAt: optionalString(item.runAt ?? item.run_at)
  };
}

export function normalizeTestDesignOperationsAuditReport(
  raw: unknown
): TestDesignOperationsAuditReportView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    totalOperationCount: numberValue(item.totalOperationCount ?? item.total_operation_count, 0),
    successCount: numberValue(item.successCount ?? item.success_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    deniedCount: numberValue(item.deniedCount ?? item.denied_count, 0),
    queueAlertSubscriptionMutationCount: numberValue(
      item.queueAlertSubscriptionMutationCount ?? item.queue_alert_subscription_mutation_count,
      0
    ),
    queuedEventReplayCount: numberValue(item.queuedEventReplayCount ?? item.queued_event_replay_count, 0),
    publishCompensationRunCount: numberValue(
      item.publishCompensationRunCount ?? item.publish_compensation_run_count,
      0
    ),
    auditOutboxRequeueCount: numberValue(item.auditOutboxRequeueCount ?? item.audit_outbox_requeue_count, 0),
    latestOperationAt: optionalString(item.latestOperationAt ?? item.latest_operation_at),
    exportSupported: optionalBoolean(item.exportSupported ?? item.export_supported),
    detailRowsExported: optionalBoolean(item.detailRowsExported ?? item.detail_rows_exported),
    actorIdentifierExported: optionalBoolean(
      item.actorIdentifierExported ?? item.actor_identifier_exported
    ),
    traceIdValueExported: optionalBoolean(item.traceIdValueExported ?? item.trace_id_value_exported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    generatedAt: optionalString(item.generatedAt ?? item.generated_at)
  };
}

export function normalizeTestDesignAuditReportTemplateField(
  raw: unknown
): TestDesignAuditReportTemplateFieldView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    label: stringValue(item.label),
    source: stringValue(item.source),
    exportMode: stringValue(item.exportMode ?? item.export_mode),
    required: optionalBoolean(item.required),
    identifierValueExported: optionalBoolean(
      item.identifierValueExported ?? item.identifier_value_exported
    ),
    payloadExported: optionalBoolean(item.payloadExported ?? item.payload_exported)
  };
}

export function normalizeTestDesignAuditReportTemplateSection(
  raw: unknown
): TestDesignAuditReportTemplateSectionView {
  const item = isRecord(raw) ? raw : {};
  return {
    code: stringValue(item.code),
    label: stringValue(item.label),
    description: optionalString(item.description),
    fields: listItems(item.fields).map(normalizeTestDesignAuditReportTemplateField)
  };
}

export function normalizeTestDesignAuditReportTemplate(
  raw: unknown
): TestDesignAuditReportTemplateView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    templateVersion: optionalString(item.templateVersion ?? item.template_version),
    fieldSetVersion: optionalString(item.fieldSetVersion ?? item.field_set_version),
    sections: listItems(item.sections).map(normalizeTestDesignAuditReportTemplateSection),
    exportSupported: optionalBoolean(item.exportSupported ?? item.export_supported),
    crossWpDetailReportSupported: optionalBoolean(
      item.crossWpDetailReportSupported ?? item.cross_wp_detail_report_supported
    ),
    modelObservationDrilldownSupported: optionalBoolean(
      item.modelObservationDrilldownSupported ?? item.model_observation_drilldown_supported
    ),
    identifierValuesExported: optionalBoolean(
      item.identifierValuesExported ?? item.identifier_values_exported
    ),
    payloadExported: optionalBoolean(item.payloadExported ?? item.payload_exported),
    actorIdentifierExported: optionalBoolean(
      item.actorIdentifierExported ?? item.actor_identifier_exported
    ),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    generatedAt: optionalString(item.generatedAt ?? item.generated_at)
  };
}

export function normalizeTestDesignModelObservationBucket(
  raw: unknown
): TestDesignModelObservationBucketView {
  const item = isRecord(raw) ? raw : {};
  return {
    dimension: stringValue(item.dimension),
    bucketKey: stringValue(item.bucketKey ?? item.bucket_key),
    bucketLabel: stringValue(item.bucketLabel ?? item.bucket_label),
    invocationCount: numberValue(item.invocationCount ?? item.invocation_count, 0),
    succeededCount: numberValue(item.succeededCount ?? item.succeeded_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    blockedCount: numberValue(item.blockedCount ?? item.blocked_count, 0),
    fallbackCount: numberValue(item.fallbackCount ?? item.fallback_count, 0),
    inputTokenTotal: numberValue(item.inputTokenTotal ?? item.input_token_total, 0),
    outputTokenTotal: numberValue(item.outputTokenTotal ?? item.output_token_total, 0),
    latencyMsTotal: numberValue(item.latencyMsTotal ?? item.latency_ms_total, 0),
    averageLatencyMs: numberValue(item.averageLatencyMs ?? item.average_latency_ms, 0),
    totalCostText: stringValue(item.totalCostText ?? item.total_cost_text, '0'),
    traceSignalCount: numberValue(item.traceSignalCount ?? item.trace_signal_count, 0),
    jobSignalCount: numberValue(item.jobSignalCount ?? item.job_signal_count, 0),
    latestInvocationAt: optionalString(item.latestInvocationAt ?? item.latest_invocation_at)
  };
}

export function normalizeTestDesignModelObservationDrilldown(
  raw: unknown
): TestDesignModelObservationDrilldownView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    totalInvocationCount: numberValue(item.totalInvocationCount ?? item.total_invocation_count, 0),
    succeededCount: numberValue(item.succeededCount ?? item.succeeded_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    blockedCount: numberValue(item.blockedCount ?? item.blocked_count, 0),
    fallbackCount: numberValue(item.fallbackCount ?? item.fallback_count, 0),
    inputTokenTotal: numberValue(item.inputTokenTotal ?? item.input_token_total, 0),
    outputTokenTotal: numberValue(item.outputTokenTotal ?? item.output_token_total, 0),
    latencyMsTotal: numberValue(item.latencyMsTotal ?? item.latency_ms_total, 0),
    averageLatencyMs: numberValue(item.averageLatencyMs ?? item.average_latency_ms, 0),
    totalCostText: stringValue(item.totalCostText ?? item.total_cost_text, '0'),
    traceSignalCount: numberValue(item.traceSignalCount ?? item.trace_signal_count, 0),
    jobSignalCount: numberValue(item.jobSignalCount ?? item.job_signal_count, 0),
    buckets: listItems(item.buckets).map(normalizeTestDesignModelObservationBucket),
    drilldownSupported: optionalBoolean(item.drilldownSupported ?? item.drilldown_supported),
    traceIdValueExported: optionalBoolean(item.traceIdValueExported ?? item.trace_id_value_exported),
    jobIdValueExported: optionalBoolean(item.jobIdValueExported ?? item.job_id_value_exported),
    invocationIdValueExported: optionalBoolean(
      item.invocationIdValueExported ?? item.invocation_id_value_exported
    ),
    payloadPreviewExported: optionalBoolean(item.payloadPreviewExported ?? item.payload_preview_exported),
    providerErrorTextExported: optionalBoolean(
      item.providerErrorTextExported ?? item.provider_error_text_exported
    ),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    generatedAt: optionalString(item.generatedAt ?? item.generated_at)
  };
}

export function normalizeTestDesignCrossWpAuditDetailRow(
  raw: unknown
): TestDesignCrossWpAuditDetailRowView {
  const item = isRecord(raw) ? raw : {};
  return {
    section: stringValue(item.section),
    category: stringValue(item.category),
    status: stringValue(item.status),
    eventCount: numberValue(item.eventCount ?? item.event_count, 0),
    successCount: numberValue(item.successCount ?? item.success_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    warningCount: numberValue(item.warningCount ?? item.warning_count, 0),
    latestEventAt: optionalString(item.latestEventAt ?? item.latest_event_at),
    identifierValuesExported: optionalBoolean(
      item.identifierValuesExported ?? item.identifier_values_exported
    ),
    payloadExported: optionalBoolean(item.payloadExported ?? item.payload_exported),
    actorIdentifierExported: optionalBoolean(
      item.actorIdentifierExported ?? item.actor_identifier_exported
    ),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only)
  };
}

export function normalizeTestDesignCrossWpDetailAuditReport(
  raw: unknown
): TestDesignCrossWpDetailAuditReportView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId ?? item.project_id),
    promptKey: optionalString(item.promptKey ?? item.prompt_key),
    templateVersion: optionalString(item.templateVersion ?? item.template_version),
    rowCount: numberValue(item.rowCount ?? item.row_count, 0),
    rows: listItems(item.rows).map(normalizeTestDesignCrossWpAuditDetailRow),
    detailReportSupported: optionalBoolean(item.detailReportSupported ?? item.detail_report_supported),
    rawAuditEventExported: optionalBoolean(item.rawAuditEventExported ?? item.raw_audit_event_exported),
    identifierValuesExported: optionalBoolean(
      item.identifierValuesExported ?? item.identifier_values_exported
    ),
    traceIdValueExported: optionalBoolean(item.traceIdValueExported ?? item.trace_id_value_exported),
    modelInvocationIdValueExported: optionalBoolean(
      item.modelInvocationIdValueExported ?? item.model_invocation_id_value_exported
    ),
    publishIdentifierValueExported: optionalBoolean(
      item.publishIdentifierValueExported ?? item.publish_identifier_value_exported
    ),
    payloadExported: optionalBoolean(item.payloadExported ?? item.payload_exported),
    actorIdentifierExported: optionalBoolean(
      item.actorIdentifierExported ?? item.actor_identifier_exported
    ),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    generatedAt: optionalString(item.generatedAt ?? item.generated_at)
  };
}

export function normalizeTestDesignCrossWpOperationsDashboard(
  raw: unknown
): TestDesignCrossWpOperationsDashboardView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    promptKey: optionalString(item.promptKey) ?? optionalString(item.prompt_key),
    scopePolicy: normalizeTestDesignScopePolicy(item.scopePolicy ?? item.scope_policy),
    auditChainPolicy: normalizeTestDesignAuditChainPolicy(item.auditChainPolicy ?? item.audit_chain_policy),
    taskCount: numberValue(item.taskCount ?? item.task_count, 0),
    candidateCount: numberValue(item.candidateCount ?? item.candidate_count, 0),
    publishRecordCount: numberValue(item.publishRecordCount ?? item.publish_record_count, 0),
    projectBucketCount: numberValue(item.projectBucketCount ?? item.project_bucket_count, 0),
    candidateScopeMismatchCount: numberValue(
      item.candidateScopeMismatchCount ?? item.candidate_scope_mismatch_count,
      0
    ),
    publishScopeMismatchCount: numberValue(
      item.publishScopeMismatchCount ?? item.publish_scope_mismatch_count,
      0
    ),
    modelInvocationReferenceCount: numberValue(
      item.modelInvocationReferenceCount ?? item.model_invocation_reference_count,
      0
    ),
    publishProjectScopeRecordCount: numberValue(
      item.publishProjectScopeRecordCount ?? item.publish_project_scope_record_count,
      0
    ),
    candidateScopeCoveragePercent: numberValue(
      item.candidateScopeCoveragePercent ?? item.candidate_scope_coverage_percent,
      0
    ),
    publishScopeCoveragePercent: numberValue(
      item.publishScopeCoveragePercent ?? item.publish_scope_coverage_percent,
      0
    ),
    auditDashboard: normalizeTestDesignCrossWpAuditDashboard(item.auditDashboard ?? item.audit_dashboard),
    auditOutbox: normalizeTestDesignAuditOutboxOperations(item.auditOutbox ?? item.audit_outbox),
    queueAlerts: normalizeTestDesignQueueAlertOperations(item.queueAlerts ?? item.queue_alerts),
    compensationRunbook: normalizeTestDesignCompensationRunbook(
      item.compensationRunbook ?? item.compensation_runbook
    ),
    operationsAuditReport: normalizeTestDesignOperationsAuditReport(
      item.operationsAuditReport ?? item.operations_audit_report
    ),
    auditReportTemplate: normalizeTestDesignAuditReportTemplate(
      item.auditReportTemplate ?? item.audit_report_template
    ),
    modelObservationDrilldown: normalizeTestDesignModelObservationDrilldown(
      item.modelObservationDrilldown ?? item.model_observation_drilldown
    ),
    crossWpDetailAuditReport: normalizeTestDesignCrossWpDetailAuditReport(
      item.crossWpDetailAuditReport ?? item.cross_wp_detail_audit_report
    ),
    metrics: listItems(item.metrics).map(normalizeTestDesignScopeSummaryMetric),
    readiness: listItems(item.readiness).map(normalizeTestDesignScopeSummaryReadiness),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    detailIdentifiersExported: optionalBoolean(
      item.detailIdentifiersExported ?? item.detail_identifiers_exported
    ),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
  };
}

export function normalizeTestDesignAuditOutboxRequeueResult(
  raw: unknown
): TestDesignAuditOutboxRequeueResult {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    requestedStatus: stringValue(item.requestedStatus ?? item.requested_status, 'FAILED_OR_DEAD'),
    requestedLimit: numberValue(item.requestedLimit ?? item.requested_limit, 0),
    requeuedCount: numberValue(item.requeuedCount ?? item.requeued_count, 0),
    replaySupported: optionalBoolean(item.replaySupported ?? item.replay_supported),
    payloadExported: optionalBoolean(item.payloadExported ?? item.payload_exported),
    detailIdentifiersExported: optionalBoolean(
      item.detailIdentifiersExported ?? item.detail_identifiers_exported
    ),
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

export function normalizeTestDesignConflictOperationsSummary(raw: unknown): TestDesignConflictOperationsSummary {
  const item = isRecord(raw) ? raw : {};
  return {
    totalCount: numberValue(item.totalCount ?? item.total_count, 0),
    openCount: numberValue(item.openCount ?? item.open_count, 0),
    resolvedCount: numberValue(item.resolvedCount ?? item.resolved_count, 0),
    duplicateReviewCount: numberValue(item.duplicateReviewCount ?? item.duplicate_review_count, 0),
    latestConflictAt: optionalString(item.latestConflictAt) ?? optionalString(item.latest_conflict_at)
  };
}

export function normalizeTestDesignConflictOperationItem(raw: unknown): TestDesignConflictOperationItem {
  const item = isRecord(raw) ? raw : {};
  return {
    taskId: optionalString(item.taskId) ?? optionalString(item.task_id),
    taskTitle: optionalString(item.taskTitle) ?? optionalString(item.task_title),
    taskStatus: optionalString(item.taskStatus) ?? optionalString(item.task_status),
    candidateId: optionalString(item.candidateId) ?? optionalString(item.candidate_id),
    candidateTitle: optionalString(item.candidateTitle) ?? optionalString(item.candidate_title),
    candidateStatus: optionalString(item.candidateStatus) ?? optionalString(item.candidate_status),
    candidateVersion: numberValue(item.candidateVersion ?? item.candidate_version, 0),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    requirementId: optionalString(item.requirementId) ?? optionalString(item.requirement_id),
    recommendedCaseId: optionalString(item.recommendedCaseId) ?? optionalString(item.recommended_case_id),
    record: normalizeTestDesignPublishRecord(item.record),
    resolved: Boolean(item.resolved),
    resolvable: Boolean(item.resolvable),
    conflictAt: optionalString(item.conflictAt) ?? optionalString(item.conflict_at)
  };
}

export function normalizeTestDesignConflictOperationsResult(raw: unknown): TestDesignConflictOperationsResult {
  const item = isRecord(raw) ? raw : {};
  const items = listItems(item.items).map(normalizeTestDesignConflictOperationItem);
  return {
    items,
    total: pageTotal(raw, items.length),
    index: numberValue(item.index, 0),
    size: numberValue(item.size, items.length || 20),
    summary: normalizeTestDesignConflictOperationsSummary(item.summary)
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

export function normalizeTestDesignTemplateList(raw: unknown): TestDesignTemplateList {
  const items = listItems(raw).map(normalizeTestDesignTemplate);
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

export function normalizeTestDesignContextPolicyOverride(raw: unknown): TestDesignContextPolicyOverrideView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    scopeType: stringValue(item.scopeType ?? item.scope_type, 'UNKNOWN'),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    environmentKey: optionalString(item.environmentKey) ?? optionalString(item.environment_key),
    status: stringValue(item.status, 'UNKNOWN'),
    overrideLimits: numberRecordValue(item.overrideLimits ?? item.override_limits),
    changeReasonCodeCaptured: Boolean(item.changeReasonCodeCaptured ?? item.change_reason_code_captured),
    approvalReasonCodeCaptured: Boolean(item.approvalReasonCodeCaptured ?? item.approval_reason_code_captured),
    workOrderKey: optionalString(item.workOrderKey) ?? optionalString(item.work_order_key),
    workOrderTitle: optionalString(item.workOrderTitle) ?? optionalString(item.work_order_title),
    workOrderUrl: optionalString(item.workOrderUrl) ?? optionalString(item.work_order_url),
    workOrderStatus: optionalString(item.workOrderStatus) ?? optionalString(item.work_order_status),
    policyBody: optionalString(item.policyBody) ?? optionalString(item.policy_body),
    policyBodyDigest: optionalString(item.policyBodyDigest) ?? optionalString(item.policy_body_digest),
    policyBodyVersion: optionalNumber(item.policyBodyVersion ?? item.policy_body_version),
    policyDiffSummary: optionalString(item.policyDiffSummary) ?? optionalString(item.policy_diff_summary),
    requestNote: optionalString(item.requestNote) ?? optionalString(item.request_note),
    reviewNote: optionalString(item.reviewNote) ?? optionalString(item.review_note),
    noteCount: optionalNumber(item.noteCount ?? item.note_count),
    latestNotePreview: optionalString(item.latestNotePreview) ?? optionalString(item.latest_note_preview),
    requestedBy: optionalString(item.requestedBy) ?? optionalString(item.requested_by),
    approvedBy: optionalString(item.approvedBy) ?? optionalString(item.approved_by),
    reviewedAt: optionalString(item.reviewedAt) ?? optionalString(item.reviewed_at),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignContextPolicyNote(raw: unknown): TestDesignContextPolicyNoteView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    overrideId: stringValue(item.overrideId ?? item.override_id),
    noteType: stringValue(item.noteType ?? item.note_type, 'COMMENT'),
    noteText: stringValue(item.noteText ?? item.note_text),
    createdBy: optionalString(item.createdBy) ?? optionalString(item.created_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignReleaseReadinessApproval(
  raw: unknown
): TestDesignReleaseReadinessApprovalView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    taskId: stringValue(item.taskId ?? item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    status: stringValue(item.status, 'UNKNOWN'),
    qualityGateStatus: stringValue(item.qualityGateStatus ?? item.quality_gate_status, 'UNKNOWN'),
    blockingCount: numberValue(item.blockingCount ?? item.blocking_count, 0),
    warningCount: numberValue(item.warningCount ?? item.warning_count, 0),
    readinessDigest: optionalString(item.readinessDigest) ?? optionalString(item.readiness_digest),
    exceptionReasonCodeCaptured: Boolean(
      item.exceptionReasonCodeCaptured ?? item.exception_reason_code_captured
    ),
    exceptionReasonCode: optionalString(item.exceptionReasonCode)
      ?? optionalString(item.exception_reason_code),
    approvalReasonCodeCaptured: Boolean(
      item.approvalReasonCodeCaptured ?? item.approval_reason_code_captured
    ),
    approvalReasonCode: optionalString(item.approvalReasonCode)
      ?? optionalString(item.approval_reason_code),
    workOrderKey: optionalString(item.workOrderKey) ?? optionalString(item.work_order_key),
    workOrderTitle: optionalString(item.workOrderTitle) ?? optionalString(item.work_order_title),
    workOrderUrl: optionalString(item.workOrderUrl) ?? optionalString(item.work_order_url),
    workOrderStatus: optionalString(item.workOrderStatus) ?? optionalString(item.work_order_status),
    exceptionSummary: optionalString(item.exceptionSummary) ?? optionalString(item.exception_summary),
    exceptionSummaryDigest: optionalString(item.exceptionSummaryDigest)
      ?? optionalString(item.exception_summary_digest),
    riskMitigation: optionalString(item.riskMitigation) ?? optionalString(item.risk_mitigation),
    requestNote: optionalString(item.requestNote) ?? optionalString(item.request_note),
    reviewNote: optionalString(item.reviewNote) ?? optionalString(item.review_note),
    noteCount: optionalNumber(item.noteCount ?? item.note_count),
    latestNotePreview: optionalString(item.latestNotePreview) ?? optionalString(item.latest_note_preview),
    requestedBy: optionalString(item.requestedBy) ?? optionalString(item.requested_by),
    approvedBy: optionalString(item.approvedBy) ?? optionalString(item.approved_by),
    reviewedAt: optionalString(item.reviewedAt) ?? optionalString(item.reviewed_at),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignReleaseReadinessNote(raw: unknown): TestDesignReleaseReadinessNoteView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    approvalId: stringValue(item.approvalId ?? item.approval_id),
    noteType: stringValue(item.noteType ?? item.note_type, 'COMMENT'),
    noteText: stringValue(item.noteText ?? item.note_text),
    createdBy: optionalString(item.createdBy) ?? optionalString(item.created_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignReportArchive(raw: unknown): TestDesignReportArchiveView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    manifestId: stringValue(item.manifestId ?? item.manifest_id),
    taskId: stringValue(item.taskId ?? item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    storageBackend: optionalString(item.storageBackend) ?? optionalString(item.storage_backend),
    contentDigest: optionalString(item.contentDigest) ?? optionalString(item.content_digest),
    contentSizeBytes: numberValue(item.contentSizeBytes ?? item.content_size_bytes, 0),
    reportRowCount: numberValue(item.reportRowCount ?? item.report_row_count, 0),
    lineIntegrityCount: numberValue(item.lineIntegrityCount ?? item.line_integrity_count, 0),
    status: stringValue(item.status, 'UNKNOWN'),
    archiveApprovalStatus: stringValue(item.archiveApprovalStatus ?? item.archive_approval_status, 'UNKNOWN'),
    externalApprovalStatus: stringValue(item.externalApprovalStatus ?? item.external_approval_status, 'UNKNOWN'),
    retentionUntil: optionalString(item.retentionUntil) ?? optionalString(item.retention_until),
    archiveContentStored: optionalBoolean(item.archiveContentStored ?? item.archive_content_stored),
    lineIntegrityIndexReady: optionalBoolean(item.lineIntegrityIndexReady ?? item.line_integrity_index_ready),
    archiveContentExported: optionalBoolean(item.archiveContentExported ?? item.archive_content_exported),
    storageKeyExported: optionalBoolean(item.storageKeyExported ?? item.storage_key_exported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    createdBy: optionalString(item.createdBy) ?? optionalString(item.created_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignReportArchiveIntegrity(
  raw: unknown
): TestDesignReportArchiveIntegrityView {
  const item = isRecord(raw) ? raw : {};
  return {
    archiveId: stringValue(item.archiveId ?? item.archive_id),
    reportRowCount: numberValue(item.reportRowCount ?? item.report_row_count, 0),
    indexedRowCount: numberValue(item.indexedRowCount ?? item.indexed_row_count, 0),
    digestAlgorithm: optionalString(item.digestAlgorithm) ?? optionalString(item.digest_algorithm),
    chainIntegrityStored: optionalBoolean(item.chainIntegrityStored ?? item.chain_integrity_stored),
    rowIntegrityValueExported: optionalBoolean(
      item.rowIntegrityValueExported ?? item.row_integrity_value_exported
    ),
    rowContentSummaryExported: optionalBoolean(
      item.rowContentSummaryExported ?? item.row_content_summary_exported
    ),
    archiveContentExported: optionalBoolean(item.archiveContentExported ?? item.archive_content_exported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only)
  };
}

export function normalizeTestDesignReportArchiveApproval(
  raw: unknown
): TestDesignReportArchiveApprovalView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    archiveId: stringValue(item.archiveId ?? item.archive_id),
    taskId: stringValue(item.taskId ?? item.task_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    approvalType: stringValue(item.approvalType ?? item.approval_type, 'ARCHIVE'),
    status: stringValue(item.status, 'UNKNOWN'),
    reasonCodeCaptured: Boolean(item.reasonCodeCaptured ?? item.reason_code_captured),
    reasonCode: optionalString(item.reasonCode) ?? optionalString(item.reason_code),
    approvalReasonCodeCaptured: Boolean(
      item.approvalReasonCodeCaptured ?? item.approval_reason_code_captured
    ),
    approvalReasonCode: optionalString(item.approvalReasonCode)
      ?? optionalString(item.approval_reason_code),
    workOrderKey: optionalString(item.workOrderKey) ?? optionalString(item.work_order_key),
    workOrderTitle: optionalString(item.workOrderTitle) ?? optionalString(item.work_order_title),
    workOrderUrl: optionalString(item.workOrderUrl) ?? optionalString(item.work_order_url),
    workOrderStatus: optionalString(item.workOrderStatus) ?? optionalString(item.work_order_status),
    requestSummary: optionalString(item.requestSummary) ?? optionalString(item.request_summary),
    requestSummaryDigest: optionalString(item.requestSummaryDigest)
      ?? optionalString(item.request_summary_digest),
    requestNote: optionalString(item.requestNote) ?? optionalString(item.request_note),
    reviewNote: optionalString(item.reviewNote) ?? optionalString(item.review_note),
    noteCount: optionalNumber(item.noteCount ?? item.note_count),
    latestNotePreview: optionalString(item.latestNotePreview) ?? optionalString(item.latest_note_preview),
    requestedBy: optionalString(item.requestedBy) ?? optionalString(item.requested_by),
    approvedBy: optionalString(item.approvedBy) ?? optionalString(item.approved_by),
    reviewedAt: optionalString(item.reviewedAt) ?? optionalString(item.reviewed_at),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeTestDesignReportArchiveNote(raw: unknown): TestDesignReportArchiveNoteView {
  const item = isRecord(raw) ? raw : {};
  return {
    id: stringValue(item.id),
    approvalId: stringValue(item.approvalId ?? item.approval_id),
    noteType: stringValue(item.noteType ?? item.note_type, 'COMMENT'),
    noteText: stringValue(item.noteText ?? item.note_text),
    createdBy: optionalString(item.createdBy) ?? optionalString(item.created_by),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function normalizeTestDesignContextPolicyEffective(raw: unknown): TestDesignContextPolicyEffectiveView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    environmentKey: optionalString(item.environmentKey) ?? optionalString(item.environment_key),
    contextLimits: numberRecordValue(item.contextLimits ?? item.context_limits),
    appliedOverrideScopes: stringArrayValue(item.appliedOverrideScopes ?? item.applied_override_scopes),
    overrideStatusCounts: numberRecordValue(item.overrideStatusCounts ?? item.override_status_counts),
    contextAssemblyPolicy: normalizeTestDesignContextAssemblyPolicy(
      item.contextAssemblyPolicy ?? item.context_assembly_policy
    ),
    contextPolicyGovernance: normalizeTestDesignContextPolicyGovernance(
      item.contextPolicyGovernance ?? item.context_policy_governance
    ),
    contextPolicyOperations: normalizeTestDesignContextPolicyOperations(
      item.contextPolicyOperations ?? item.context_policy_operations
    ),
    policyBodyExported: optionalBoolean(item.policyBodyExported ?? item.policy_body_exported),
    policyDiffPreviewExported: optionalBoolean(
      item.policyDiffPreviewExported ?? item.policy_diff_preview_exported
    ),
    approvalNotesExported: optionalBoolean(item.approvalNotesExported ?? item.approval_notes_exported),
    ticketUrlExported: optionalBoolean(item.ticketUrlExported ?? item.ticket_url_exported),
    aggregateOnly: optionalBoolean(item.aggregateOnly ?? item.aggregate_only),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
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

export async function fetchTestDesignTemplates(
  filters: TestDesignTemplateFilters = {}
): Promise<ApiResponse<TestDesignTemplateList>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/templates${queryString(filters as Record<string, unknown>)}`);
  return { ...response, data: normalizeTestDesignTemplateList(response.data) };
}

export async function createTestDesignTemplate(
  payload: SaveTestDesignTemplatePayload
): Promise<ApiResponse<TestDesignTemplateView>> {
  const response = await requestJson<unknown>('/api/v1/test-design/templates', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignTemplate(response.data) };
}

export async function updateTestDesignTemplate(
  templateId: string,
  payload: SaveTestDesignTemplatePayload
): Promise<ApiResponse<TestDesignTemplateView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/templates/${encodeURIComponent(templateId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignTemplate(response.data) };
}

export async function deleteTestDesignTemplate(templateId: string): Promise<ApiResponse<TestDesignTemplateView>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/templates/${encodeURIComponent(templateId)}`, {
    method: 'DELETE'
  });
  return { ...response, data: normalizeTestDesignTemplate(response.data) };
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

export async function fetchTestDesignEvaluationCorpusSummary(
  filters: TestDesignEvaluationCorpusSummaryFilters = {}
): Promise<ApiResponse<TestDesignEvaluationCorpusSummaryView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/evaluation-corpus-summary${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignEvaluationCorpusSummary(response.data) };
}

export async function fetchTestDesignEvaluationSamples(
  filters: TestDesignEvaluationSampleFilters = {}
): Promise<ApiResponse<TestDesignEvaluationSampleList>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/evaluation-samples${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignEvaluationSampleList(response.data) };
}

export async function fetchTestDesignEvaluationSampleSummary(
  filters: TestDesignEvaluationSampleFilters = {}
): Promise<ApiResponse<TestDesignEvaluationSampleSummaryView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/evaluation-samples/summary${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignEvaluationSampleSummary(response.data) };
}

export async function createTestDesignEvaluationSample(
  payload: SaveTestDesignEvaluationSamplePayload
): Promise<ApiResponse<TestDesignEvaluationSampleView>> {
  const response = await requestJson<unknown>('/api/v1/test-design/quality/evaluation-samples', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignEvaluationSample(response.data) };
}

export async function updateTestDesignEvaluationSample(
  sampleId: string,
  payload: SaveTestDesignEvaluationSamplePayload
): Promise<ApiResponse<TestDesignEvaluationSampleView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/evaluation-samples/${encodeURIComponent(sampleId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignEvaluationSample(response.data) };
}

export async function transitionTestDesignEvaluationSample(
  sampleId: string,
  payload: TransitionTestDesignEvaluationSamplePayload
): Promise<ApiResponse<TestDesignEvaluationSampleView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/evaluation-samples/${encodeURIComponent(sampleId)}/status`,
    {
      method: 'PATCH',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignEvaluationSample(response.data) };
}

export async function createTestDesignEvaluationSampleFromCandidate(
  payload: CreateTestDesignEvaluationSampleFromCandidatePayload
): Promise<ApiResponse<TestDesignEvaluationSampleView>> {
  const response = await requestJson<unknown>('/api/v1/test-design/quality/evaluation-samples/from-candidate', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignEvaluationSample(response.data) };
}

export async function fetchTestDesignCalibrationRuns(
  filters: TestDesignCalibrationRunFilters = {}
): Promise<ApiResponse<TestDesignCalibrationRunList>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/calibration-runs${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignCalibrationRunList(response.data) };
}

export async function requestTestDesignCalibrationRun(
  payload: RequestTestDesignCalibrationRunPayload
): Promise<ApiResponse<TestDesignCalibrationRunView>> {
  const response = await requestJson<unknown>('/api/v1/test-design/quality/calibration-runs', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignCalibrationRun(response.data) };
}

export async function fetchTestDesignScopeSummary(
  filters: TestDesignScopeSummaryFilters = {}
): Promise<ApiResponse<TestDesignScopeSummaryView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/quality/scope-summary${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignScopeSummary(response.data) };
}

export async function fetchTestDesignCrossWpOperationsDashboard(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignCrossWpOperationsDashboardView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/cross-wp-dashboard${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignCrossWpOperationsDashboard(response.data) };
}

export async function fetchTestDesignQueueAlertSubscriptions(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignQueueAlertSubscriptionView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/queue-alert-subscriptions${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignQueueAlertSubscription) };
}

export async function upsertTestDesignQueueAlertSubscription(
  payload: UpsertTestDesignQueueAlertSubscriptionPayload
): Promise<ApiResponse<TestDesignQueueAlertSubscriptionView>> {
  const response = await requestJson<unknown>('/api/v1/test-design/operations/queue-alert-subscriptions', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignQueueAlertSubscription(response.data) };
}

export async function replayTestDesignQueuedEvents(
  payload: ReplayTestDesignQueuedEventsPayload
): Promise<ApiResponse<TestDesignQueuedEventReplayResult>> {
  const response = await requestJson<unknown>('/api/v1/test-design/operations/queued-events/replay', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignQueuedEventReplayResult(response.data) };
}

export async function fetchTestDesignCompensationRunbook(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignCompensationRunbookView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/compensation-runbook${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignCompensationRunbook(response.data) };
}

export async function runTestDesignPublishCompensation(
  payload: RunTestDesignPublishCompensationPayload
): Promise<ApiResponse<TestDesignPublishCompensationRunResult>> {
  const response = await requestJson<unknown>('/api/v1/test-design/operations/publish-compensation/run', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignPublishCompensationRunResult(response.data) };
}

export async function fetchTestDesignOperationsAuditReport(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignOperationsAuditReportView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/audit-report${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignOperationsAuditReport(response.data) };
}

export async function fetchTestDesignAuditReportTemplate(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignAuditReportTemplateView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/audit-report-template${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignAuditReportTemplate(response.data) };
}

export async function fetchTestDesignModelObservationDrilldown(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignModelObservationDrilldownView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/model-observation-drilldown${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignModelObservationDrilldown(response.data) };
}

export async function fetchTestDesignCrossWpDetailAuditReport(
  filters: TestDesignCrossWpOperationsFilters = {}
): Promise<ApiResponse<TestDesignCrossWpDetailAuditReportView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/operations/cross-wp-detail-audit-report${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignCrossWpDetailAuditReport(response.data) };
}

export async function requeueTestDesignAuditOutbox(
  payload: RequeueTestDesignAuditOutboxPayload
): Promise<ApiResponse<TestDesignAuditOutboxRequeueResult>> {
  const response = await requestJson<unknown>('/api/v1/test-design/operations/audit-outbox/requeue', {
    method: 'POST',
    body: JSON.stringify(compactPayload(payload))
  });
  return { ...response, data: normalizeTestDesignAuditOutboxRequeueResult(response.data) };
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

export async function replayQueuedTestDesignTaskEvent(taskId: string): Promise<ApiResponse<TestDesignTaskDetail>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/replay-queued-event`, {
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

export async function fetchTestDesignConflictOperations(
  filters: TestDesignConflictOperationFilters
): Promise<ApiResponse<TestDesignConflictOperationsResult>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/conflicts${queryString(filters as unknown as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignConflictOperationsResult(response.data) };
}

export async function fetchTestDesignPublishRecords(taskId: string): Promise<ApiResponse<TestDesignPublishRecordView[]>> {
  const response = await requestJson<unknown>(`/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/publish-records`);
  return { ...response, data: listItems(response.data).map(normalizeTestDesignPublishRecord) };
}

export async function fetchTestDesignReleaseReadinessApprovals(
  taskId: string
): Promise<ApiResponse<TestDesignReleaseReadinessApprovalView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/release-readiness/approvals`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignReleaseReadinessApproval) };
}

export async function requestTestDesignReleaseReadinessApproval(
  taskId: string,
  payload: RequestTestDesignReleaseReadinessApprovalPayload
): Promise<ApiResponse<TestDesignReleaseReadinessApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/release-readiness/approvals`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReleaseReadinessApproval(response.data) };
}

export async function updateTestDesignReleaseReadinessApproval(
  approvalId: string,
  payload: RequestTestDesignReleaseReadinessApprovalPayload
): Promise<ApiResponse<TestDesignReleaseReadinessApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/release-readiness/approvals/${encodeURIComponent(approvalId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReleaseReadinessApproval(response.data) };
}

export async function approveTestDesignReleaseReadinessApproval(
  approvalId: string,
  payload: ReviewTestDesignReleaseReadinessApprovalPayload = {}
): Promise<ApiResponse<TestDesignReleaseReadinessApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/release-readiness/approvals/${encodeURIComponent(approvalId)}/approve`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReleaseReadinessApproval(response.data) };
}

export async function rejectTestDesignReleaseReadinessApproval(
  approvalId: string,
  payload: ReviewTestDesignReleaseReadinessApprovalPayload = {}
): Promise<ApiResponse<TestDesignReleaseReadinessApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/release-readiness/approvals/${encodeURIComponent(approvalId)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReleaseReadinessApproval(response.data) };
}

export async function fetchTestDesignReleaseReadinessNotes(
  approvalId: string
): Promise<ApiResponse<TestDesignReleaseReadinessNoteView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/release-readiness/approvals/${encodeURIComponent(approvalId)}/notes`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignReleaseReadinessNote) };
}

export async function addTestDesignReleaseReadinessNote(
  approvalId: string,
  payload: AddTestDesignReleaseReadinessNotePayload
): Promise<ApiResponse<TestDesignReleaseReadinessNoteView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/release-readiness/approvals/${encodeURIComponent(approvalId)}/notes`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReleaseReadinessNote(response.data) };
}

export async function fetchTestDesignReportArchives(
  taskId: string
): Promise<ApiResponse<TestDesignReportArchiveView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/tasks/${encodeURIComponent(taskId)}/report/archives`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignReportArchive) };
}

export async function fetchTestDesignReportArchiveIntegrity(
  archiveId: string
): Promise<ApiResponse<TestDesignReportArchiveIntegrityView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archives/${encodeURIComponent(archiveId)}/integrity`
  );
  return { ...response, data: normalizeTestDesignReportArchiveIntegrity(response.data) };
}

export async function fetchTestDesignReportArchiveApprovals(
  archiveId: string
): Promise<ApiResponse<TestDesignReportArchiveApprovalView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archives/${encodeURIComponent(archiveId)}/approvals`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignReportArchiveApproval) };
}

export async function requestTestDesignReportArchiveApproval(
  archiveId: string,
  payload: RequestTestDesignReportArchiveApprovalPayload
): Promise<ApiResponse<TestDesignReportArchiveApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archives/${encodeURIComponent(archiveId)}/archive-approvals`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReportArchiveApproval(response.data) };
}

export async function requestTestDesignReportArchiveExternalApproval(
  archiveId: string,
  payload: RequestTestDesignReportArchiveApprovalPayload
): Promise<ApiResponse<TestDesignReportArchiveApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archives/${encodeURIComponent(archiveId)}/external-approvals`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReportArchiveApproval(response.data) };
}

export async function approveTestDesignReportArchiveApproval(
  approvalId: string,
  payload: ReviewTestDesignReportArchiveApprovalPayload = {}
): Promise<ApiResponse<TestDesignReportArchiveApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archive-approvals/${encodeURIComponent(approvalId)}/approve`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReportArchiveApproval(response.data) };
}

export async function rejectTestDesignReportArchiveApproval(
  approvalId: string,
  payload: ReviewTestDesignReportArchiveApprovalPayload = {}
): Promise<ApiResponse<TestDesignReportArchiveApprovalView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archive-approvals/${encodeURIComponent(approvalId)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReportArchiveApproval(response.data) };
}

export async function fetchTestDesignReportArchiveNotes(
  approvalId: string
): Promise<ApiResponse<TestDesignReportArchiveNoteView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archive-approvals/${encodeURIComponent(approvalId)}/notes`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignReportArchiveNote) };
}

export async function addTestDesignReportArchiveNote(
  approvalId: string,
  payload: AddTestDesignReportArchiveNotePayload
): Promise<ApiResponse<TestDesignReportArchiveNoteView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/report-archive-approvals/${encodeURIComponent(approvalId)}/notes`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignReportArchiveNote(response.data) };
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

export async function fetchTestDesignContextPolicyOverrides(
  projectId: string,
  filters: TestDesignContextPolicyFilters = {}
): Promise<ApiResponse<TestDesignContextPolicyOverrideView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/projects/${encodeURIComponent(projectId)}/overrides${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignContextPolicyOverride) };
}

export async function fetchTestDesignContextPolicyEffective(
  projectId: string,
  filters: TestDesignContextPolicyFilters = {}
): Promise<ApiResponse<TestDesignContextPolicyEffectiveView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/projects/${encodeURIComponent(projectId)}/effective${queryString(filters as Record<string, unknown>)}`
  );
  return { ...response, data: normalizeTestDesignContextPolicyEffective(response.data) };
}

export async function requestTestDesignProjectContextPolicyOverride(
  projectId: string,
  payload: RequestTestDesignContextPolicyOverridePayload
): Promise<ApiResponse<TestDesignContextPolicyOverrideView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/projects/${encodeURIComponent(projectId)}/overrides`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignContextPolicyOverride(response.data) };
}

export async function requestTestDesignEnvironmentContextPolicyOverride(
  projectId: string,
  environmentKey: string,
  payload: RequestTestDesignContextPolicyOverridePayload
): Promise<ApiResponse<TestDesignContextPolicyOverrideView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/projects/${encodeURIComponent(projectId)}/environments/${encodeURIComponent(environmentKey)}/overrides`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignContextPolicyOverride(response.data) };
}

export async function updateTestDesignContextPolicyOverride(
  overrideId: string,
  payload: RequestTestDesignContextPolicyOverridePayload
): Promise<ApiResponse<TestDesignContextPolicyOverrideView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/overrides/${encodeURIComponent(overrideId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignContextPolicyOverride(response.data) };
}

export async function approveTestDesignContextPolicyOverride(
  overrideId: string,
  payload: ReviewTestDesignContextPolicyOverridePayload = {}
): Promise<ApiResponse<TestDesignContextPolicyOverrideView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/overrides/${encodeURIComponent(overrideId)}/approve`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignContextPolicyOverride(response.data) };
}

export async function rejectTestDesignContextPolicyOverride(
  overrideId: string,
  payload: ReviewTestDesignContextPolicyOverridePayload = {}
): Promise<ApiResponse<TestDesignContextPolicyOverrideView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/overrides/${encodeURIComponent(overrideId)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignContextPolicyOverride(response.data) };
}

export async function fetchTestDesignContextPolicyNotes(
  overrideId: string
): Promise<ApiResponse<TestDesignContextPolicyNoteView[]>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/overrides/${encodeURIComponent(overrideId)}/notes`
  );
  return { ...response, data: listItems(response.data).map(normalizeTestDesignContextPolicyNote) };
}

export async function addTestDesignContextPolicyNote(
  overrideId: string,
  payload: AddTestDesignContextPolicyNotePayload
): Promise<ApiResponse<TestDesignContextPolicyNoteView>> {
  const response = await requestJson<unknown>(
    `/api/v1/test-design/context-policies/overrides/${encodeURIComponent(overrideId)}/notes`,
    {
      method: 'POST',
      body: JSON.stringify(compactPayload(payload))
    }
  );
  return { ...response, data: normalizeTestDesignContextPolicyNote(response.data) };
}

export function testDesignErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
