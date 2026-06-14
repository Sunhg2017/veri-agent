import {
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Download,
  Eye,
  FileText,
  GripVertical,
  Layers3,
  Link2,
  Plus,
  RefreshCw,
  Repeat2,
  RotateCcw,
  Save,
  Search,
  Send,
  Sparkles,
  Trash2,
  XCircle
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent
} from 'react';
import type { CurrentUser } from '../api/auth';
import {
  fetchAssetRequirements,
  fetchAssetTestCases,
  type AssetRequirementView,
  type AssetTestCaseView
} from '../api/assets';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_STRATEGIES,
  TEST_DESIGN_COVERAGE_TYPES,
  TEST_DESIGN_GENERATION_STRATEGIES,
  addTestDesignReportArchiveNote,
  addTestDesignContextPolicyNote,
  addTestDesignReleaseReadinessNote,
  approveTestDesignReportArchiveApproval,
  approveTestDesignReleaseReadinessApproval,
  approveTestDesignContextPolicyOverride,
  batchActionTestDesignCandidates,
  batchResolveTestDesignConflicts,
  cancelTestDesignTask,
  confirmTestDesignCandidate,
  createTestDesignEvaluationSample,
  createTestDesignEvaluationSampleFromCandidate,
  createTestDesignTemplate,
  createTestDesignTask,
  deleteTestDesignTemplate,
  exportTestDesignCandidatesCsv,
  exportTestDesignReviewRecordsCsv,
  exportTestDesignTaskReportCsv,
  fetchTaskTestDesignCandidates,
  fetchTestDesignConflictOperations,
  fetchTestDesignContextPolicyEffective,
  fetchTestDesignContextPolicyNotes,
  fetchTestDesignContextPolicyOverrides,
  fetchTestDesignAuditReportTemplate,
  fetchTestDesignCrossWpDetailAuditReport,
  fetchTestDesignCrossWpOperationsDashboard,
  fetchTestDesignModelObservationDrilldown,
  fetchTestDesignQueueAlertSubscriptions,
  fetchTestDesignCalibrationRuns,
  fetchTestDesignEvaluationCorpusSummary,
  fetchTestDesignEvaluationSamples,
  fetchTestDesignEvaluationSampleSummary,
  fetchTestDesignHealth,
  fetchTestDesignPromptTrend,
  fetchTestDesignReleaseReadinessApprovals,
  fetchTestDesignReleaseReadinessNotes,
  fetchTestDesignReportArchiveApprovals,
  fetchTestDesignReportArchiveIntegrity,
  fetchTestDesignReportArchiveNotes,
  fetchTestDesignReportArchives,
  fetchTestDesignReviewRecords,
  fetchTestDesignTemplates,
  fetchTestDesignTaskAuditSummary,
  fetchTestDesignTaskQualitySummary,
  fetchTestDesignTaskSummary,
  fetchTestDesignTasks,
  ignoreTestDesignCandidate,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  rejectTestDesignContextPolicyOverride,
  rejectTestDesignReportArchiveApproval,
  rejectTestDesignReleaseReadinessApproval,
  requeueTestDesignAuditOutbox,
  replayTestDesignQueuedEvents,
  runTestDesignPublishCompensation,
  replayQueuedTestDesignTaskEvent,
  requestTestDesignEnvironmentContextPolicyOverride,
  requestTestDesignCalibrationRun,
  requestTestDesignReportArchiveApproval,
  requestTestDesignReportArchiveExternalApproval,
  requestTestDesignProjectContextPolicyOverride,
  requestTestDesignReleaseReadinessApproval,
  resolveTestDesignConflict,
  retryTestDesignTask,
  testDesignErrorMessage,
  transitionTestDesignEvaluationSample,
  updateTestDesignTemplate,
  updateTestDesignCandidate,
  updateTestDesignContextPolicyOverride,
  updateTestDesignEvaluationSample,
  updateTestDesignReleaseReadinessApproval,
  upsertTestDesignQueueAlertSubscription,
  type TestDesignCandidateBatchActionResult,
  type TestDesignCandidateBatchActionType,
  type TestDesignCandidateView,
  type TestDesignConflictOperationItem,
  type TestDesignConflictOperationsSummary,
  type TestDesignCalibrationRunView,
  type TestDesignCalibrationSummaryView,
  type TestDesignContextPolicyEffectiveView,
  type TestDesignContextPolicyNoteView,
  type TestDesignContextPolicyOverrideView,
  type TestDesignCrossWpOperationsDashboardView,
  type TestDesignAuditReportTemplateView,
  type TestDesignAuditOutboxRequeueResult,
  type TestDesignCrossWpDetailAuditReportView,
  type TestDesignModelObservationDrilldownView,
  type TestDesignQueueAlertSubscriptionView,
  type TestDesignQueuedEventReplayResult,
  type TestDesignPublishCompensationRunResult,
  type TestDesignAuditSummaryView,
  type TestDesignHealth,
  type TestDesignEvaluationCorpusSummaryView,
  type TestDesignEvaluationSampleSummaryView,
  type TestDesignEvaluationSampleView,
  type SaveTestDesignEvaluationSamplePayload,
  type TestDesignPromptTrendView,
  type TestDesignPublishRecordView,
  type TestDesignPublishResult,
  type TestDesignQualitySummaryView,
  type TestDesignReleaseReadinessApprovalView,
  type TestDesignReleaseReadinessNoteView,
  type TestDesignReportArchiveApprovalView,
  type TestDesignReportArchiveIntegrityView,
  type TestDesignReportArchiveNoteView,
  type TestDesignReportArchiveView,
  type TestDesignReviewRecordView,
  type TestDesignStepView,
  type TestDesignTemplateView,
  type TestDesignTaskView
} from '../api/testDesign';
import { canUseButton, hasPermission } from '../permissions';
import {
  validateTestDesignCandidateDraft
} from '../testDesignQuality';
import {
  buildTestDesignQualitySummary,
  qualitySummaryFromServer
} from '../testDesignQualitySummary';
import {
  buildTestDesignPromptTrendSummary
} from '../testDesignPromptTrend';
import {
  buildTestDesignReviewSummary
} from '../testDesignReviewSummary';
import {
  DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE,
  TEST_DESIGN_CANDIDATE_PAGE_SIZES,
  pageFromServerItems
} from '../testDesignPagination';
import {
  canPublishTestDesignCandidate,
  canSelectTestDesignCandidate,
  selectedTestDesignPublishCandidates,
  selectedTestDesignReviewCandidates
} from '../testDesignSelection';
import {
  buildTestDesignBatchEditPayload,
  hasTestDesignBatchEditChanges,
  initialTestDesignBatchEditDraft,
  selectedTestDesignBatchEditableCandidates,
  testDesignBatchEditFieldLabels,
  validateTestDesignBatchEditDraft,
  type TestDesignBatchEditDraft
} from '../testDesignBatchEdit';
import {
  buildTestDesignBatchReviewConfirmation,
  buildTestDesignBatchConflictResolutionConfirmation,
  buildTestDesignBatchEditConfirmation,
  buildTestDesignConflictResolutionConfirmation,
  buildTestDesignPublishConfirmation,
  testDesignBatchActionLabel,
  type TestDesignConfirmationSummary
} from '../testDesignConfirmation';
import {
  TEST_DESIGN_EXPORT_CONTENT_TYPE,
  buildTestDesignCandidateReviewCsv,
  buildTestDesignExportFilename,
  buildTestDesignPublishResultCsv
} from '../testDesignExport';
import {
  candidateGenerationSource,
  generationSourceText,
  taskGenerationSource
} from '../testDesignGenerationSource';
import {
  buildTestDesignTaskIdempotencySignature,
  resolveTestDesignTaskIdempotency,
  type TestDesignTaskIdempotencyState
} from '../testDesignIdempotency';
import {
  buildTestDesignAuditSummary
} from '../testDesignAuditSummary';
import { buildTestDesignTaskDiagnostics } from '../testDesignTaskDiagnostics';
import {
  TEST_DESIGN_CONTEXT_POLICY_REASON_CODES,
  TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES,
  buildTestDesignContextPolicyPayload,
  buildTestDesignContextPolicySummary,
  contextPolicyDraftFromOverride,
  initialTestDesignContextPolicyDraft,
  validateTestDesignContextPolicyDraft,
  type TestDesignContextPolicyDraft
} from '../testDesignContextPolicy';
import {
  AuditSummaryPanel,
  Metric,
  PromptTrendPanel,
  QualitySummaryPanel,
  ReviewSummaryPanel,
  StateLine,
  type WorkState
} from './TestDesignOverviewPanels';
import {
  CrossWpOperationsPanel,
  type AuditOutboxRequeueDraft,
  type CrossWpOperationsFilters,
  type PublishCompensationRunDraft,
  type QueueAlertSubscriptionDraft,
  type QueuedEventReplayDraft
} from './TestDesignCrossWpOperationsPanel';
import {
  EvaluationCorpusOperationsPanel,
  type CalibrationRunDraft,
  type EvaluationSampleDraft,
  type EvaluationSampleFilters
} from './TestDesignEvaluationCorpusPanel';
import {
  BatchActionSummary,
  BatchEditSummary,
  CandidateStatus,
  ConfirmationDialog,
  Detail,
  GenerationSourceBadge,
  PublishRecordRow,
  PublishResultBadge,
  QualityFieldMessages,
  ReviewRecordRow,
  assetCaseTraceHref,
  calibrationStatusTone,
  contextPolicyDigestText,
  contextPolicyOverrideLimitText,
  contextPolicyStatusTone,
  emptyRequirementText,
  publishRecordKey,
  releaseReadinessDigestText,
  releaseReadinessStatusTone,
  reportArchiveStatusTone,
  reviewSuccessText,
  sampleStatusTone,
  shortIdentifier
} from './TestDesignWorkbenchShared';
import {
  ASYNC_TASK_STATUSES,
  CANCELLABLE_TASK_STATUSES,
  RETRYABLE_TASK_STATUSES,
  TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE,
  applyConflictResolutionRecord,
  conflictResolutionCandidate,
  conflictResolutionTargetCaseId,
  countByStatus,
  draftFromCandidate,
  downloadText,
  emptyStepDraft,
  evaluationSampleDraftFromView,
  evaluationSamplePayload,
  filterRequirements,
  initialAuditOutboxRequeueDraft,
  initialCalibrationRunDraft,
  initialCandidateFilters,
  initialConflictOperationFilters,
  initialConflictResolutionDraft,
  initialCrossWpOperationsFilters,
  initialEvaluationSampleDraft,
  initialEvaluationSampleFilters,
  initialFilters,
  initialGenerationDraft,
  initialPublishCompensationRunDraft,
  initialQueueAlertSubscriptionDraft,
  initialQueuedEventReplayDraft,
  initialReleaseReadinessDraft,
  initialReportArchiveDraft,
  initialTaskFilters,
  initialTemplateDraft,
  isPublishIssueRecord,
  isResolvableConflictRecord,
  mergeBatchCandidates,
  mergeCandidateCache,
  mergeUpdatedCandidates,
  parseContextAssetIds,
  releaseReadinessReasonCodeValue,
  releaseReadinessReasonCodes,
  releaseReadinessWorkOrderStatuses,
  reportArchiveApprovalTypes,
  reportArchiveReasonCodeValue,
  reportArchiveReasonCodes,
  reportArchiveWorkOrderStatuses,
  stepsFromDraft,
  stepsToQualityText,
  stringDefault,
  tagsFromText,
  templateContextIds,
  templateDraftFromView,
  templatePayload,
  upsertEvaluationSample,
  upsertTask,
  upsertTemplate,
  type BatchEditResult,
  type CandidateDraft,
  type CandidateFilters,
  type ConflictOperationFilters,
  type ConflictResolutionCandidate,
  type ConflictResolutionDraft,
  type ConflictResolutionItem,
  type GenerationDraft,
  type PendingConfirmation,
  type ReleaseReadinessApprovalDraft,
  type ReportArchiveApprovalDraft,
  type RequirementFilters,
  type TaskFilters,
  type TemplateDraft,
  type TestDesignStepDraft
} from '../testDesignWorkbenchState';

export function TestDesignWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'testDesign:read');
  const canGenerate = canUseButton(props.currentUser, 'testDesign:generate');
  const canReview = canUseButton(props.currentUser, 'testDesign:review');
  const canPublish = canUseButton(props.currentUser, 'testDesign:publish');
  const canExport = canUseButton(props.currentUser, 'testDesign:export');
  const canPolicyManage = canUseButton(props.currentUser, 'testDesign:policy_manage');

  const [health, setHealth] = useState<TestDesignHealth | null>(null);
  const [requirements, setRequirements] = useState<AssetRequirementView[]>([]);
  const [selectedRequirementIds, setSelectedRequirementIds] = useState<string[]>([]);
  const [tasks, setTasks] = useState<TestDesignTaskView[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState('');
  const [candidates, setCandidates] = useState<TestDesignCandidateView[]>([]);
  const [candidatePageTotal, setCandidatePageTotal] = useState(0);
  const [selectedCandidateId, setSelectedCandidateId] = useState('');
  const [selectedCandidateCache, setSelectedCandidateCache] = useState<Record<string, TestDesignCandidateView>>({});
  const [candidateDraft, setCandidateDraft] = useState<CandidateDraft | null>(null);
  const [filters, setFilters] = useState<RequirementFilters>(initialFilters);
  const [taskFilters, setTaskFilters] = useState<TaskFilters>(initialTaskFilters);
  const [candidateFilters, setCandidateFilters] = useState<CandidateFilters>(initialCandidateFilters);
  const [candidatePageIndex, setCandidatePageIndex] = useState(0);
  const [candidatePageSize, setCandidatePageSize] = useState(DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE);
  const [generationDraft, setGenerationDraft] = useState<GenerationDraft>(initialGenerationDraft);
  const [templates, setTemplates] = useState<TestDesignTemplateView[]>([]);
  const [templatePageTotal, setTemplatePageTotal] = useState(0);
  const [selectedTemplateManageId, setSelectedTemplateManageId] = useState('');
  const [templateDraft, setTemplateDraft] = useState<TemplateDraft>(initialTemplateDraft);
  const generationIdempotencyRef = useRef<TestDesignTaskIdempotencyState | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  const [batchEditDraft, setBatchEditDraft] = useState<TestDesignBatchEditDraft>(initialTestDesignBatchEditDraft);
  const [publishResult, setPublishResult] = useState<TestDesignPublishResult | null>(null);
  const [reviewRecords, setReviewRecords] = useState<TestDesignReviewRecordView[]>([]);
  const [reviewRecordPageTotal, setReviewRecordPageTotal] = useState(0);
  const [reviewRecordPageIndex, setReviewRecordPageIndex] = useState(0);
  const [taskQualitySummary, setTaskQualitySummary] = useState<TestDesignQualitySummaryView | null>(null);
  const [taskAuditSummary, setTaskAuditSummary] = useState<TestDesignAuditSummaryView | null>(null);
  const [promptTrend, setPromptTrend] = useState<TestDesignPromptTrendView | null>(null);
  const [evaluationCorpusSummary, setEvaluationCorpusSummary] = useState<TestDesignEvaluationCorpusSummaryView | null>(null);
  const [evaluationSamples, setEvaluationSamples] = useState<TestDesignEvaluationSampleView[]>([]);
  const [evaluationSampleSummary, setEvaluationSampleSummary] = useState<TestDesignEvaluationSampleSummaryView | null>(null);
  const [evaluationSamplePageTotal, setEvaluationSamplePageTotal] = useState(0);
  const [selectedEvaluationSampleId, setSelectedEvaluationSampleId] = useState('');
  const [evaluationSampleFilters, setEvaluationSampleFilters] = useState<EvaluationSampleFilters>(initialEvaluationSampleFilters);
  const [evaluationSampleDraft, setEvaluationSampleDraft] = useState<EvaluationSampleDraft>(initialEvaluationSampleDraft);
  const [calibrationRuns, setCalibrationRuns] = useState<TestDesignCalibrationRunView[]>([]);
  const [calibrationSummary, setCalibrationSummary] = useState<TestDesignCalibrationSummaryView | null>(null);
  const [calibrationRunDraft, setCalibrationRunDraft] = useState<CalibrationRunDraft>(initialCalibrationRunDraft);
  const [crossWpOperationsDashboard, setCrossWpOperationsDashboard] = useState<TestDesignCrossWpOperationsDashboardView | null>(null);
  const [auditReportTemplate, setAuditReportTemplate] = useState<TestDesignAuditReportTemplateView | null>(null);
  const [modelObservationDrilldown, setModelObservationDrilldown] = useState<TestDesignModelObservationDrilldownView | null>(null);
  const [crossWpDetailAuditReport, setCrossWpDetailAuditReport] = useState<TestDesignCrossWpDetailAuditReportView | null>(null);
  const [crossWpOperationsFilters, setCrossWpOperationsFilters] = useState<CrossWpOperationsFilters>(initialCrossWpOperationsFilters);
  const [auditOutboxRequeueDraft, setAuditOutboxRequeueDraft] = useState<AuditOutboxRequeueDraft>(initialAuditOutboxRequeueDraft);
  const [auditOutboxRequeueResult, setAuditOutboxRequeueResult] = useState<TestDesignAuditOutboxRequeueResult | null>(null);
  const [queueAlertSubscriptions, setQueueAlertSubscriptions] = useState<TestDesignQueueAlertSubscriptionView[]>([]);
  const [queueAlertSubscriptionDraft, setQueueAlertSubscriptionDraft] = useState<QueueAlertSubscriptionDraft>(initialQueueAlertSubscriptionDraft);
  const [queueAlertSubscriptionResult, setQueueAlertSubscriptionResult] = useState<TestDesignQueueAlertSubscriptionView | null>(null);
  const [queuedEventReplayDraft, setQueuedEventReplayDraft] = useState<QueuedEventReplayDraft>(initialQueuedEventReplayDraft);
  const [queuedEventReplayResult, setQueuedEventReplayResult] = useState<TestDesignQueuedEventReplayResult | null>(null);
  const [publishCompensationRunDraft, setPublishCompensationRunDraft] = useState<PublishCompensationRunDraft>(initialPublishCompensationRunDraft);
  const [publishCompensationRunResult, setPublishCompensationRunResult] = useState<TestDesignPublishCompensationRunResult | null>(null);
  const [contextPolicyDraft, setContextPolicyDraft] = useState<TestDesignContextPolicyDraft>(initialTestDesignContextPolicyDraft);
  const [contextPolicyOverrides, setContextPolicyOverrides] = useState<TestDesignContextPolicyOverrideView[]>([]);
  const [contextPolicyEffective, setContextPolicyEffective] = useState<TestDesignContextPolicyEffectiveView | null>(null);
  const [selectedContextPolicyOverrideId, setSelectedContextPolicyOverrideId] = useState('');
  const [contextPolicyNotes, setContextPolicyNotes] = useState<TestDesignContextPolicyNoteView[]>([]);
  const [releaseReadinessDraft, setReleaseReadinessDraft] = useState<ReleaseReadinessApprovalDraft>(initialReleaseReadinessDraft);
  const [releaseReadinessApprovals, setReleaseReadinessApprovals] = useState<TestDesignReleaseReadinessApprovalView[]>([]);
  const [selectedReleaseReadinessApprovalId, setSelectedReleaseReadinessApprovalId] = useState('');
  const [releaseReadinessNotes, setReleaseReadinessNotes] = useState<TestDesignReleaseReadinessNoteView[]>([]);
  const [reportArchiveDraft, setReportArchiveDraft] = useState<ReportArchiveApprovalDraft>(initialReportArchiveDraft);
  const [reportArchives, setReportArchives] = useState<TestDesignReportArchiveView[]>([]);
  const [selectedReportArchiveId, setSelectedReportArchiveId] = useState('');
  const [reportArchiveIntegrity, setReportArchiveIntegrity] = useState<TestDesignReportArchiveIntegrityView | null>(null);
  const [reportArchiveApprovals, setReportArchiveApprovals] = useState<TestDesignReportArchiveApprovalView[]>([]);
  const [selectedReportArchiveApprovalId, setSelectedReportArchiveApprovalId] = useState('');
  const [reportArchiveNotes, setReportArchiveNotes] = useState<TestDesignReportArchiveNoteView[]>([]);
  const [batchActionResult, setBatchActionResult] = useState<TestDesignCandidateBatchActionResult | null>(null);
  const [batchEditResult, setBatchEditResult] = useState<BatchEditResult | null>(null);
  const [selectedCandidateIds, setSelectedCandidateIds] = useState<string[]>([]);
  const [conflictResolutionDraft, setConflictResolutionDraft] = useState<ConflictResolutionDraft>(initialConflictResolutionDraft);
  const [conflictOperationFilters, setConflictOperationFilters] = useState<ConflictOperationFilters>(initialConflictOperationFilters);
  const [conflictOperations, setConflictOperations] = useState<TestDesignConflictOperationItem[]>([]);
  const [conflictOperationSummary, setConflictOperationSummary] = useState<TestDesignConflictOperationsSummary | null>(null);
  const [conflictOperationPageTotal, setConflictOperationPageTotal] = useState(0);
  const [conflictOperationPageIndex, setConflictOperationPageIndex] = useState(0);
  const [conflictCaseKeyword, setConflictCaseKeyword] = useState('');
  const [conflictCaseResults, setConflictCaseResults] = useState<AssetTestCaseView[]>([]);
  const [selectedConflictCaseIds, setSelectedConflictCaseIds] = useState<Record<string, string>>({});
  const [pendingConfirmation, setPendingConfirmation] = useState<PendingConfirmation | null>(null);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [taskState, setTaskState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });
  const [publishState, setPublishState] = useState<WorkState>({ loading: false });
  const [reviewRecordState, setReviewRecordState] = useState<WorkState>({ loading: false });
  const [taskAuditState, setTaskAuditState] = useState<WorkState>({ loading: false });
  const [promptTrendState, setPromptTrendState] = useState<WorkState>({ loading: false });
  const [evaluationCorpusState, setEvaluationCorpusState] = useState<WorkState>({ loading: false });
  const [crossWpOperationsState, setCrossWpOperationsState] = useState<WorkState>({ loading: false });
  const [contextPolicyState, setContextPolicyState] = useState<WorkState>({ loading: false });
  const [releaseReadinessState, setReleaseReadinessState] = useState<WorkState>({ loading: false });
  const [reportArchiveState, setReportArchiveState] = useState<WorkState>({ loading: false });
  const [conflictOperationState, setConflictOperationState] = useState<WorkState>({ loading: false });
  const [templateState, setTemplateState] = useState<WorkState>({ loading: false });
  const [draggingStepId, setDraggingStepId] = useState('');

  const disabled = !props.signedIn || !canRead;
  const selectedTask = tasks.find((task) => task.id === selectedTaskId) ?? null;
  const selectedTaskAsyncInFlight = selectedTask ? ASYNC_TASK_STATUSES.has(selectedTask.status) : false;
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId) ?? null;
  const selectedGenerationTemplate = templates.find((template) => template.id === generationDraft.templateId) ?? null;
  const selectedManagedTemplate = templates.find((template) => template.id === selectedTemplateManageId) ?? null;
  const templateProjectId = generationDraft.projectId || filters.projectId || taskFilters.projectId || selectedTask?.projectId || '';
  const filteredRequirements = useMemo(() => filterRequirements(requirements, filters), [requirements, filters]);
  const candidatePage = useMemo(
    () => pageFromServerItems(candidates, candidatePageIndex, candidatePageSize, candidatePageTotal),
    [candidatePageIndex, candidatePageSize, candidatePageTotal, candidates]
  );
  const reviewRecordPage = useMemo(
    () => pageFromServerItems(reviewRecords, reviewRecordPageIndex, 10, reviewRecordPageTotal),
    [reviewRecordPageIndex, reviewRecordPageTotal, reviewRecords]
  );
  const reviewSummary = useMemo(
    () => buildTestDesignReviewSummary(reviewRecordPage.items, reviewRecordPage.total),
    [reviewRecordPage.items, reviewRecordPage.total]
  );
  const reviewSummaryScope = selectedTaskId
    ? reviewRecordPage.items.length
      ? `当前评审页 ${reviewRecordPage.start}-${reviewRecordPage.end} / ${reviewRecordPage.total}`
      : `当前评审页 0 / ${reviewRecordPage.total}`
    : '请先选择任务';
  const currentPageSelectableCandidates = useMemo(
    () => candidatePage.items.filter(canSelectTestDesignCandidate),
    [candidatePage.items]
  );
  const currentPagePublishableCandidates = useMemo(
    () => candidates.filter(canPublishTestDesignCandidate),
    [candidates]
  );
  const selectedCandidates = useMemo(
    () => selectedCandidateIds
      .map((candidateId) => selectedCandidateCache[candidateId])
      .filter((candidate): candidate is TestDesignCandidateView => Boolean(candidate)),
    [selectedCandidateCache, selectedCandidateIds]
  );
  const selectedPublishableCandidates = useMemo(
    () => selectedTestDesignPublishCandidates(selectedCandidates, selectedCandidateIds),
    [selectedCandidates, selectedCandidateIds]
  );
  const selectedReviewCandidates = useMemo(
    () => selectedTestDesignReviewCandidates(selectedCandidates, selectedCandidateIds),
    [selectedCandidates, selectedCandidateIds]
  );
  const selectedBatchEditableCandidates = useMemo(
    () => selectedTestDesignBatchEditableCandidates(selectedCandidates, selectedCandidateIds),
    [selectedCandidates, selectedCandidateIds]
  );
  const batchEditIssues = useMemo(
    () => validateTestDesignBatchEditDraft(batchEditDraft),
    [batchEditDraft]
  );
  const batchEditFieldLabels = useMemo(
    () => testDesignBatchEditFieldLabels(batchEditDraft),
    [batchEditDraft]
  );
  const batchEditHasChanges = hasTestDesignBatchEditChanges(batchEditDraft);
  const batchEditBlocked = !selectedBatchEditableCandidates.length || !batchEditHasChanges || batchEditIssues.length > 0;
  const estimatedPublishableCandidateCount = selectedCandidateIds.length
    ? selectedPublishableCandidates.length
    : Math.max(selectedTask?.confirmedCount ?? 0, currentPagePublishableCandidates.length);
  const publishPreviewCandidates = selectedCandidateIds.length
    ? selectedPublishableCandidates
    : currentPagePublishableCandidates;
  const canPublishCurrentScope = selectedCandidateIds.length
    ? selectedPublishableCandidates.length > 0
    : Boolean(selectedTaskId && estimatedPublishableCandidateCount > 0);
  const statusCounts = useMemo(() => countByStatus(candidates), [candidates]);
  const pageQualitySummary = useMemo(
    () => buildTestDesignQualitySummary(candidatePage.items, candidatePage.total),
    [candidatePage.items, candidatePage.total]
  );
  const qualitySummary = useMemo(
    () => taskQualitySummary ? qualitySummaryFromServer(taskQualitySummary) : pageQualitySummary,
    [pageQualitySummary, taskQualitySummary]
  );
  const promptTrendSummary = useMemo(
    () => buildTestDesignPromptTrendSummary(promptTrend),
    [promptTrend]
  );
  const selectedEvaluationSample = useMemo(
    () => evaluationSamples.find((sample) => sample.id === selectedEvaluationSampleId) ?? null,
    [evaluationSamples, selectedEvaluationSampleId]
  );
  const evaluationCorpusProjectId = evaluationSampleFilters.projectId
    || selectedTask?.projectId
    || taskFilters.projectId
    || filters.projectId
    || generationDraft.projectId
    || '';
  const evaluationCorpusPromptKey = evaluationSampleFilters.promptKey
    || health?.promptKey
    || selectedTask?.promptKey
    || generationDraft.promptKey
    || '';
  const crossWpOperationsProjectId = crossWpOperationsFilters.projectId
    || selectedTask?.projectId
    || taskFilters.projectId
    || filters.projectId
    || generationDraft.projectId
    || '';
  const crossWpOperationsPromptKey = crossWpOperationsFilters.promptKey
    || health?.promptKey
    || selectedTask?.promptKey
    || generationDraft.promptKey
    || '';
  const crossWpOperationsEffectiveFilters = useMemo(() => ({
    projectId: crossWpOperationsProjectId,
    promptKey: crossWpOperationsPromptKey
  }), [crossWpOperationsProjectId, crossWpOperationsPromptKey]);
  const qualitySummaryScope = selectedTaskId
    ? taskQualitySummary
      ? `任务全量 ${taskQualitySummary.total} 个候选`
      : candidatePage.items.length
        ? `当前候选页 ${candidatePage.start}-${candidatePage.end} / ${candidatePage.total}`
        : `当前候选页 0 / ${candidatePage.total}`
    : '请先选择任务';
  const publishScopeLabel = selectedCandidateIds.length
    ? `${selectedPublishableCandidates.length} / ${selectedCandidateIds.length} 个已选候选`
    : `全部可发布候选${estimatedPublishableCandidateCount ? ` · 约 ${estimatedPublishableCandidateCount} 个` : ''}`;
  const publishIssueRecords = useMemo(
    () => publishResult?.records.filter(isPublishIssueRecord) ?? [],
    [publishResult]
  );
  const resolvableConflictRecords = useMemo(
    () => publishResult?.records.filter(isResolvableConflictRecord) ?? [],
    [publishResult]
  );
  const conflictCandidateById = useMemo(() => {
    const lookup = new Map<string, TestDesignCandidateView>();
    Object.values(selectedCandidateCache).forEach((candidate) => lookup.set(candidate.id, candidate));
    candidates.forEach((candidate) => lookup.set(candidate.id, candidate));
    return lookup;
  }, [candidates, selectedCandidateCache]);
  const conflictOperationProjectId = conflictOperationFilters.projectId
    || selectedTask?.projectId
    || taskFilters.projectId
    || filters.projectId
    || generationDraft.projectId
    || '';
  const conflictCaseSearchProjectId = publishResult?.projectId ?? conflictOperationProjectId;
  const conflictOperationPage = useMemo(
    () => pageFromServerItems(
      conflictOperations,
      conflictOperationPageIndex,
      TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE,
      conflictOperationPageTotal
    ),
    [conflictOperationPageIndex, conflictOperationPageTotal, conflictOperations]
  );
  const batchResolvableConflictItems = useMemo(
    () => {
      const items: ConflictResolutionItem[] = [];
      resolvableConflictRecords.forEach((record) => {
        const candidate = conflictResolutionCandidate(record, conflictCandidateById);
        const targetCaseId = conflictResolutionTargetCaseId(record, selectedConflictCaseIds);
        if (!candidate || !targetCaseId) {
          return;
        }
        items.push({ candidate, record: { ...record, assetCaseId: targetCaseId } });
      });
      return items;
    },
    [conflictCandidateById, resolvableConflictRecords, selectedConflictCaseIds]
  );
  const batchResolvableConflictOperationItems = useMemo(
    () => {
      const items: ConflictResolutionItem[] = [];
      conflictOperations.forEach((item) => {
        if (!item.resolvable) {
          return;
        }
        const record = item.record;
        const candidate = conflictResolutionCandidate(record, conflictCandidateById);
        const targetCaseId = conflictResolutionTargetCaseId(record, selectedConflictCaseIds);
        if (!candidate || !targetCaseId) {
          return;
        }
        items.push({ candidate, record: { ...record, assetCaseId: targetCaseId } });
      });
      return items;
    },
    [conflictCandidateById, conflictOperations, selectedConflictCaseIds]
  );
  const candidateQualityIssues = useMemo(
    () => candidateDraft && selectedCandidate
      ? validateTestDesignCandidateDraft({
        ...candidateDraft,
        steps: stepsToQualityText(candidateDraft.steps)
      }, {
        currentCandidateId: selectedCandidate.id,
        currentRequirementId: selectedCandidate.requirementId,
        peerCandidates: candidates
      })
      : [],
    [candidateDraft, candidates, selectedCandidate]
  );
  const candidateSaveBlocked = candidateQualityIssues.length > 0;
  const selectedRequirementTitles = useMemo(() => {
    const lookup = new Map(requirements.map((requirement) => [requirement.id, requirement.title]));
    return selectedRequirementIds.map((id) => lookup.get(id) ?? id);
  }, [requirements, selectedRequirementIds]);
  const explicitContextAssetLimit = useMemo(() => {
    const configured = health?.contextLimits?.explicitAssetsPerType ?? health?.contextLimits?.explicit_assets_per_type;
    return typeof configured === 'number' && Number.isFinite(configured) && configured > 0 ? Math.floor(configured) : 5;
  }, [health?.contextLimits]);
  const taskDiagnostics = useMemo(
    () => buildTestDesignTaskDiagnostics(selectedTask),
    [selectedTask]
  );
  const auditSummary = useMemo(
    () => buildTestDesignAuditSummary(taskAuditSummary),
    [taskAuditSummary]
  );
  const contextPolicyIssues = useMemo(
    () => validateTestDesignContextPolicyDraft(contextPolicyDraft),
    [contextPolicyDraft]
  );
  const contextPolicySubmitIssues = useMemo(
    () => contextPolicyIssues.filter((issue) => !['approvalReasonCode', 'reviewNote', 'workOrderStatus', 'noteText'].includes(issue.field)),
    [contextPolicyIssues]
  );
  const contextPolicySummary = useMemo(
    () => buildTestDesignContextPolicySummary(contextPolicyEffective, contextPolicyOverrides),
    [contextPolicyEffective, contextPolicyOverrides]
  );
  const contextPolicySubmitBlocked = contextPolicySubmitIssues.length > 0;
  const selectedContextPolicyOverride = useMemo(
    () => contextPolicyOverrides.find((override) => override.id === selectedContextPolicyOverrideId) ?? null,
    [contextPolicyOverrides, selectedContextPolicyOverrideId]
  );
  const selectedReleaseReadinessApproval = useMemo(
    () => releaseReadinessApprovals.find((approval) => approval.id === selectedReleaseReadinessApprovalId) ?? null,
    [releaseReadinessApprovals, selectedReleaseReadinessApprovalId]
  );
  const selectedPendingReleaseReadinessApproval = selectedReleaseReadinessApproval?.status === 'PENDING'
    ? selectedReleaseReadinessApproval
    : null;
  const selectedReportArchive = useMemo(
    () => reportArchives.find((archive) => archive.id === selectedReportArchiveId) ?? null,
    [reportArchives, selectedReportArchiveId]
  );
  const selectedReportArchiveApproval = useMemo(
    () => reportArchiveApprovals.find((approval) => approval.id === selectedReportArchiveApprovalId) ?? null,
    [reportArchiveApprovals, selectedReportArchiveApprovalId]
  );
  const selectedPendingReportArchiveApproval = selectedReportArchiveApproval?.status === 'PENDING'
    ? selectedReportArchiveApproval
    : null;
  const currentReleaseReadiness = taskQualitySummary?.readiness ?? null;
  const releaseReadinessSubmitBlocked = !selectedTaskId
    || releaseReadinessState.loading
    || !releaseReadinessDraft.exceptionSummary.trim()
    || !releaseReadinessDraft.riskMitigation.trim();
  const selectedPendingContextPolicyOverride = selectedContextPolicyOverride?.status === 'PENDING'
    ? selectedContextPolicyOverride
    : null;
  const selectedTaskSource = useMemo(() => taskGenerationSource(selectedTask), [selectedTask]);
  const selectedCandidateSource = useMemo(
    () => candidateGenerationSource(selectedCandidate, selectedTask),
    [selectedCandidate, selectedTask]
  );

  const refreshCandidatePage = useCallback(async (taskId: string, options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead || !taskId) {
      setCandidates([]);
      setCandidatePageTotal(0);
      setReviewRecords([]);
      setReviewRecordPageTotal(0);
      setTaskQualitySummary(null);
      setTaskAuditSummary(null);
      setPromptTrend(null);
      setSelectedCandidateId('');
      setCandidateDraft(null);
      setBatchActionResult(null);
      setBatchEditResult(null);
      setPendingConfirmation(null);
      setTaskState({ loading: false });
      return;
    }

    const silent = options?.silent === true;
    if (silent) {
      setTaskState((current) => ({ ...current, error: undefined }));
    } else {
      setTaskState({ loading: true });
    }
    try {
      const [taskResult, candidateResult] = await Promise.all([
        fetchTestDesignTaskSummary(taskId),
        fetchTaskTestDesignCandidates(taskId, {
          index: candidatePageIndex,
          size: candidatePageSize,
          status: candidateFilters.status,
          coverageType: candidateFilters.coverageType,
          keyword: candidateFilters.keyword
        })
      ]);
      const page = candidateResult.data;
      const normalizedPage = pageFromServerItems(page.items, page.index ?? candidatePageIndex, page.size ?? candidatePageSize, page.total);
      if (normalizedPage.index !== candidatePageIndex && page.total > 0 && !page.items.length) {
        setCandidatePageIndex(normalizedPage.index);
      }
      const pageCandidateById = new Map(page.items.map((candidate) => [candidate.id, candidate]));
      setTasks((current) => upsertTask(current, taskResult.data));
      setCandidates(page.items);
      setCandidatePageTotal(page.total);
      setSelectedCandidateCache((current) => mergeCandidateCache(current, page.items));
      setSelectedCandidateIds((current) => current.filter((id) => {
        const pageCandidate = pageCandidateById.get(id);
        return pageCandidate ? canSelectTestDesignCandidate(pageCandidate) : true;
      }));
      setSelectedCandidateId((current) => {
        if (current && page.items.some((candidate) => candidate.id === current)) {
          return current;
        }
        return page.items[0]?.id ?? '';
      });
      setTaskState({ loading: false, traceId: candidateResult.trace_id || taskResult.trace_id });
    } catch (error: unknown) {
      if (!silent) {
        setCandidates([]);
        setCandidatePageTotal(0);
        setSelectedCandidateId('');
        setCandidateDraft(null);
      }
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '候选用例列表加载失败') });
    }
  }, [
    canRead,
    candidateFilters.coverageType,
    candidateFilters.keyword,
    candidateFilters.status,
    candidatePageIndex,
    candidatePageSize,
    props.signedIn
  ]);

  const refreshPromptTrend = useCallback(async (options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead) {
      setPromptTrend(null);
      setPromptTrendState({ loading: false });
      return;
    }
    const projectId = taskFilters.projectId || filters.projectId || selectedTask?.projectId || '';
    if (!projectId) {
      setPromptTrend(null);
      setPromptTrendState({ loading: false });
      return;
    }

    const silent = options?.silent === true;
    if (silent) {
      setPromptTrendState((current) => ({ ...current, error: undefined }));
    } else {
      setPromptTrendState({ loading: true });
    }
    try {
      const response = await fetchTestDesignPromptTrend({
        size: 20,
        projectId,
        promptKey: health?.promptKey
      });
      setPromptTrend(response.data);
      setPromptTrendState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      setPromptTrend(null);
      if (!silent) {
        setPromptTrendState({ loading: false, error: testDesignErrorMessage(error, 'Prompt 版本趋势加载失败') });
      }
    }
  }, [
    canRead,
    filters.projectId,
    health?.promptKey,
    props.signedIn,
    selectedTask?.projectId,
    taskFilters.projectId
  ]);

  const refreshEvaluationCorpusOperations = useCallback(async (options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead) {
      setEvaluationCorpusSummary(null);
      setEvaluationSamples([]);
      setEvaluationSampleSummary(null);
      setEvaluationSamplePageTotal(0);
      setCalibrationRuns([]);
      setCalibrationSummary(null);
      setEvaluationCorpusState({ loading: false });
      return;
    }
    const projectId = evaluationCorpusProjectId.trim();
    if (!projectId) {
      setEvaluationCorpusSummary(null);
      setEvaluationSamples([]);
      setEvaluationSampleSummary(null);
      setEvaluationSamplePageTotal(0);
      setCalibrationRuns([]);
      setCalibrationSummary(null);
      setEvaluationCorpusState({ loading: false });
      return;
    }
    const promptKey = evaluationCorpusPromptKey.trim();
    const silent = options?.silent === true;
    if (silent) {
      setEvaluationCorpusState((current) => ({ ...current, error: undefined }));
    } else {
      setEvaluationCorpusState({ loading: true });
    }
    try {
      const [summaryResponse, sampleResponse, sampleSummaryResponse, calibrationResponse] = await Promise.all([
        fetchTestDesignEvaluationCorpusSummary({ projectId, promptKey, size: 20 }),
        fetchTestDesignEvaluationSamples({
          projectId,
          promptKey,
          promptVersion: evaluationSampleFilters.promptVersion,
          status: evaluationSampleFilters.status,
          coverageType: evaluationSampleFilters.coverageType,
          baselineVersion: evaluationSampleFilters.baselineVersion,
          keyword: evaluationSampleFilters.keyword,
          index: 0,
          size: 8
        }),
        fetchTestDesignEvaluationSampleSummary({ projectId, promptKey }),
        fetchTestDesignCalibrationRuns({
          projectId,
          promptKey,
          promptVersion: calibrationRunDraft.promptVersion,
          baselineVersion: calibrationRunDraft.baselineVersion,
          index: 0,
          size: 6
        })
      ]);
      setEvaluationCorpusSummary(summaryResponse.data);
      setEvaluationSamples(sampleResponse.data.items);
      setEvaluationSamplePageTotal(sampleResponse.data.total);
      setEvaluationSampleSummary(sampleSummaryResponse.data);
      setSelectedEvaluationSampleId((current) => (
        current && sampleResponse.data.items.some((sample) => sample.id === current)
          ? current
          : sampleResponse.data.items[0]?.id ?? ''
      ));
      setCalibrationRuns(calibrationResponse.data.items);
      setCalibrationSummary(calibrationResponse.data.summary ?? null);
      setEvaluationCorpusState({
        loading: false,
        success: `样本 ${sampleResponse.data.items.length} / ${sampleResponse.data.total} · 校准 ${calibrationResponse.data.total}`,
        traceId: sampleResponse.trace_id || calibrationResponse.trace_id || summaryResponse.trace_id
      });
    } catch (error: unknown) {
      if (!silent) {
        setEvaluationCorpusSummary(null);
        setEvaluationSamples([]);
        setEvaluationSampleSummary(null);
        setEvaluationSamplePageTotal(0);
        setCalibrationRuns([]);
        setCalibrationSummary(null);
        setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, '评测语料运营加载失败') });
      }
    }
  }, [
    calibrationRunDraft.baselineVersion,
    calibrationRunDraft.promptVersion,
    canRead,
    evaluationCorpusProjectId,
    evaluationCorpusPromptKey,
    evaluationSampleFilters.baselineVersion,
    evaluationSampleFilters.coverageType,
    evaluationSampleFilters.keyword,
    evaluationSampleFilters.promptVersion,
    evaluationSampleFilters.status,
    props.signedIn
  ]);

  const refreshCrossWpOperations = useCallback(async (options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead) {
      setCrossWpOperationsDashboard(null);
      setAuditReportTemplate(null);
      setModelObservationDrilldown(null);
      setCrossWpDetailAuditReport(null);
      setAuditOutboxRequeueResult(null);
      setQueueAlertSubscriptions([]);
      setQueueAlertSubscriptionResult(null);
      setQueuedEventReplayResult(null);
      setPublishCompensationRunResult(null);
      setCrossWpOperationsState({ loading: false });
      return;
    }
    const projectId = crossWpOperationsProjectId.trim();
    const promptKey = crossWpOperationsPromptKey.trim();
    const silent = options?.silent === true;
    if (silent) {
      setCrossWpOperationsState((current) => ({ ...current, error: undefined }));
    } else {
      setCrossWpOperationsState({ loading: true });
    }
    try {
      const [
        dashboardResponse,
        subscriptionsResponse,
        templateResponse,
        modelDrilldownResponse,
        detailReportResponse
      ] = await Promise.all([
        fetchTestDesignCrossWpOperationsDashboard({ projectId, promptKey }),
        fetchTestDesignQueueAlertSubscriptions({ projectId, promptKey }),
        fetchTestDesignAuditReportTemplate({ projectId, promptKey }),
        fetchTestDesignModelObservationDrilldown({ projectId, promptKey }),
        fetchTestDesignCrossWpDetailAuditReport({ projectId, promptKey })
      ]);
      setCrossWpOperationsDashboard(dashboardResponse.data);
      setQueueAlertSubscriptions(subscriptionsResponse.data);
      setAuditReportTemplate(templateResponse.data);
      setModelObservationDrilldown(modelDrilldownResponse.data);
      setCrossWpDetailAuditReport(detailReportResponse.data);
      setAuditOutboxRequeueDraft((current) => ({
        ...current,
        projectId: current.projectId || projectId,
        status: current.status || 'FAILED_OR_DEAD',
        maxItems: current.maxItems || '20'
      }));
      setQueueAlertSubscriptionDraft((current) => ({
        ...current,
        projectId: current.projectId || projectId,
        promptKey: current.promptKey || promptKey
      }));
      setQueuedEventReplayDraft((current) => ({
        ...current,
        projectId: current.projectId || projectId,
        promptKey: current.promptKey || promptKey
      }));
      setPublishCompensationRunDraft((current) => ({
        ...current,
        projectId: current.projectId || projectId,
        promptKey: current.promptKey || promptKey
      }));
      setCrossWpOperationsState({
        loading: false,
        success: `任务 ${dashboardResponse.data.taskCount} · outbox 可重放 ${dashboardResponse.data.auditOutbox?.replayEligibleCount ?? 0} · 明细 ${detailReportResponse.data.rowCount}`,
        traceId: dashboardResponse.trace_id
          || subscriptionsResponse.trace_id
          || templateResponse.trace_id
          || modelDrilldownResponse.trace_id
          || detailReportResponse.trace_id
      });
    } catch (error: unknown) {
      if (!silent) {
        setCrossWpOperationsDashboard(null);
        setAuditReportTemplate(null);
        setModelObservationDrilldown(null);
        setCrossWpDetailAuditReport(null);
        setQueueAlertSubscriptions([]);
        setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, '跨 WP 运营看板加载失败') });
      }
    }
  }, [
    canRead,
    crossWpOperationsProjectId,
    crossWpOperationsPromptKey,
    props.signedIn
  ]);

  const refreshConflictOperations = useCallback(async (pageIndex = conflictOperationPageIndex, options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!props.signedIn || !canRead) {
      setConflictOperations([]);
      setConflictOperationSummary(null);
      setConflictOperationPageTotal(0);
      setConflictOperationState({ loading: false });
      return;
    }
    const projectId = conflictOperationProjectId.trim();
    if (!projectId) {
      setConflictOperations([]);
      setConflictOperationSummary(null);
      setConflictOperationPageTotal(0);
      if (!silent) {
        setConflictOperationState({ loading: false, error: '请先填写项目 ID' });
      }
      return;
    }

    setConflictOperationPageIndex(pageIndex);
    if (silent) {
      setConflictOperationState((current) => ({ ...current, error: undefined }));
    } else {
      setConflictOperationState({ loading: true });
    }
    try {
      const response = await fetchTestDesignConflictOperations({
        projectId,
        taskId: conflictOperationFilters.taskId,
        resolutionStatus: conflictOperationFilters.resolutionStatus,
        candidateStatus: conflictOperationFilters.candidateStatus,
        action: conflictOperationFilters.action,
        result: conflictOperationFilters.result,
        keyword: conflictOperationFilters.keyword,
        index: pageIndex,
        size: TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE
      });
      setConflictOperations(response.data.items);
      setConflictOperationSummary(response.data.summary);
      setConflictOperationPageTotal(response.data.total);
      setConflictOperationState({
        loading: false,
        success: `已加载资产冲突 ${response.data.items.length} / ${response.data.total}`,
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      if (!silent) {
        setConflictOperations([]);
        setConflictOperationPageTotal(0);
        setConflictOperationSummary(null);
        setConflictOperationState({ loading: false, error: testDesignErrorMessage(error, '资产冲突加载失败') });
      }
    }
  }, [
    canRead,
    conflictOperationFilters.action,
    conflictOperationFilters.candidateStatus,
    conflictOperationFilters.keyword,
    conflictOperationFilters.resolutionStatus,
    conflictOperationFilters.result,
    conflictOperationFilters.taskId,
    conflictOperationPageIndex,
    conflictOperationProjectId,
    props.signedIn
  ]);

  const refreshTaskQualitySummary = useCallback(async (taskId: string, options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead || !taskId) {
      setTaskQualitySummary(null);
      return;
    }

    try {
      const response = await fetchTestDesignTaskQualitySummary(taskId);
      setTaskQualitySummary(response.data);
      if (!options?.silent) {
        setTaskState((current) => ({ ...current, traceId: response.trace_id }));
      }
    } catch (error: unknown) {
      setTaskQualitySummary(null);
      if (!options?.silent) {
        setTaskState({ loading: false, error: testDesignErrorMessage(error, '任务质量摘要加载失败') });
      }
    }
  }, [canRead, props.signedIn]);

  const refreshTaskAuditSummary = useCallback(async (taskId: string, options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead || !taskId) {
      setTaskAuditSummary(null);
      setTaskAuditState({ loading: false });
      return;
    }

    const silent = options?.silent === true;
    if (silent) {
      setTaskAuditState((current) => ({ ...current, error: undefined }));
    } else {
      setTaskAuditState({ loading: true });
    }
    try {
      const response = await fetchTestDesignTaskAuditSummary(taskId);
      setTaskAuditSummary(response.data);
      setTaskAuditState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      setTaskAuditSummary(null);
      if (!silent) {
        setTaskAuditState({ loading: false, error: testDesignErrorMessage(error, '审计链摘要加载失败') });
      }
    }
  }, [canRead, props.signedIn]);

  const refreshContextPolicy = useCallback(async (options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead) {
      setContextPolicyOverrides([]);
      setContextPolicyEffective(null);
      setSelectedContextPolicyOverrideId('');
      setContextPolicyNotes([]);
      return;
    }
    const projectId = contextPolicyDraft.projectId.trim() || selectedTask?.projectId || taskFilters.projectId || filters.projectId || '';
    if (!projectId) {
      setContextPolicyOverrides([]);
      setContextPolicyEffective(null);
      setSelectedContextPolicyOverrideId('');
      setContextPolicyNotes([]);
      return;
    }

    const environmentKey = contextPolicyDraft.environmentKey.trim();
    const requestFilters = environmentKey ? { environmentKey } : {};
    const silent = options?.silent === true;
    if (!silent) {
      setContextPolicyState({ loading: true });
    }
    try {
      const [overridesResponse, effectiveResponse] = await Promise.all([
        fetchTestDesignContextPolicyOverrides(projectId, requestFilters),
        fetchTestDesignContextPolicyEffective(projectId, requestFilters)
      ]);
      const refreshedOverrides = overridesResponse.data;
      setContextPolicyOverrides(refreshedOverrides);
      if (selectedContextPolicyOverrideId && !refreshedOverrides.some((override) => override.id === selectedContextPolicyOverrideId)) {
        setSelectedContextPolicyOverrideId('');
        setContextPolicyNotes([]);
      }
      setContextPolicyEffective(effectiveResponse.data);
      if (!silent) {
        setContextPolicyState({
          loading: false,
          success: `上下文策略已加载：${overridesResponse.data.length} 条覆盖`,
          traceId: effectiveResponse.trace_id || overridesResponse.trace_id
        });
      }
    } catch (error: unknown) {
      setContextPolicyOverrides([]);
      setContextPolicyEffective(null);
      if (!silent) {
        setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, '上下文策略加载失败') });
      }
    }
  }, [
    canRead,
    contextPolicyDraft.environmentKey,
    contextPolicyDraft.projectId,
    filters.projectId,
    props.signedIn,
    selectedContextPolicyOverrideId,
    selectedTask?.projectId,
    taskFilters.projectId
  ]);

  const refreshReleaseReadinessApprovals = useCallback(async (
    taskId: string = selectedTaskId,
    options?: { silent?: boolean }
  ) => {
    if (!props.signedIn || !canRead || !taskId) {
      setReleaseReadinessApprovals([]);
      setSelectedReleaseReadinessApprovalId('');
      setReleaseReadinessNotes([]);
      setReleaseReadinessState({ loading: false });
      return;
    }
    const silent = options?.silent === true;
    if (!silent) {
      setReleaseReadinessState({ loading: true });
    }
    try {
      const response = await fetchTestDesignReleaseReadinessApprovals(taskId);
      const approvals = response.data;
      setReleaseReadinessApprovals(approvals);
      setSelectedReleaseReadinessApprovalId((current) => (
        current && approvals.some((approval) => approval.id === current) ? current : approvals[0]?.id ?? ''
      ));
      if (!silent) {
        setReleaseReadinessState({
          loading: false,
          success: `发布准出审批已加载：${approvals.length} 条`,
          traceId: response.trace_id
        });
      }
    } catch (error: unknown) {
      setReleaseReadinessApprovals([]);
      setSelectedReleaseReadinessApprovalId('');
      setReleaseReadinessNotes([]);
      if (!silent) {
        setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, '发布准出审批加载失败') });
      }
    }
  }, [canRead, props.signedIn, selectedTaskId]);

  const refreshReleaseReadinessNotes = useCallback(async (approvalId: string = selectedReleaseReadinessApprovalId) => {
    if (!props.signedIn || !canRead || !approvalId) {
      setReleaseReadinessNotes([]);
      return;
    }
    try {
      const response = await fetchTestDesignReleaseReadinessNotes(approvalId);
      setReleaseReadinessNotes(response.data);
    } catch (error: unknown) {
      setReleaseReadinessNotes([]);
      setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, '发布准出备注加载失败') });
    }
  }, [canRead, props.signedIn, selectedReleaseReadinessApprovalId]);

  const refreshReportArchives = useCallback(async (
    taskId: string = selectedTaskId,
    options?: { silent?: boolean }
  ) => {
    if (!props.signedIn || !canRead || !taskId) {
      setReportArchives([]);
      setSelectedReportArchiveId('');
      setReportArchiveIntegrity(null);
      setReportArchiveApprovals([]);
      setSelectedReportArchiveApprovalId('');
      setReportArchiveNotes([]);
      setReportArchiveState({ loading: false });
      return;
    }
    const silent = options?.silent === true;
    if (!silent) {
      setReportArchiveState({ loading: true });
    }
    try {
      const response = await fetchTestDesignReportArchives(taskId);
      const archives = response.data;
      setReportArchives(archives);
      setSelectedReportArchiveId((current) => (
        current && archives.some((archive) => archive.id === current) ? current : archives[0]?.id ?? ''
      ));
      if (!archives.length) {
        setReportArchiveIntegrity(null);
        setReportArchiveApprovals([]);
        setSelectedReportArchiveApprovalId('');
        setReportArchiveNotes([]);
      }
      if (!silent) {
        setReportArchiveState({
          loading: false,
          success: `报告归档已加载：${archives.length} 条`,
          traceId: response.trace_id
        });
      }
    } catch (error: unknown) {
      setReportArchives([]);
      setSelectedReportArchiveId('');
      setReportArchiveIntegrity(null);
      setReportArchiveApprovals([]);
      setSelectedReportArchiveApprovalId('');
      setReportArchiveNotes([]);
      if (!silent) {
        setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, '报告归档加载失败') });
      }
    }
  }, [canRead, props.signedIn, selectedTaskId]);

  const refreshReportArchiveDetail = useCallback(async (
    archiveId: string = selectedReportArchiveId,
    options?: { silent?: boolean }
  ) => {
    if (!props.signedIn || !canRead || !archiveId) {
      setReportArchiveIntegrity(null);
      setReportArchiveApprovals([]);
      setSelectedReportArchiveApprovalId('');
      setReportArchiveNotes([]);
      return;
    }
    const silent = options?.silent === true;
    if (!silent) {
      setReportArchiveState({ loading: true });
    }
    try {
      const [integrityResponse, approvalsResponse] = await Promise.all([
        fetchTestDesignReportArchiveIntegrity(archiveId),
        fetchTestDesignReportArchiveApprovals(archiveId)
      ]);
      const approvals = approvalsResponse.data;
      setReportArchiveIntegrity(integrityResponse.data);
      setReportArchiveApprovals(approvals);
      setSelectedReportArchiveApprovalId((current) => (
        current && approvals.some((approval) => approval.id === current) ? current : approvals[0]?.id ?? ''
      ));
      if (!approvals.length) {
        setReportArchiveNotes([]);
      }
      if (!silent) {
        setReportArchiveState({
          loading: false,
          success: `归档明细已加载：审批 ${approvals.length} 条`,
          traceId: integrityResponse.trace_id || approvalsResponse.trace_id
        });
      }
    } catch (error: unknown) {
      setReportArchiveIntegrity(null);
      setReportArchiveApprovals([]);
      setSelectedReportArchiveApprovalId('');
      setReportArchiveNotes([]);
      if (!silent) {
        setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, '归档明细加载失败') });
      }
    }
  }, [canRead, props.signedIn, selectedReportArchiveId]);

  const refreshReportArchiveNotes = useCallback(async (
    approvalId: string = selectedReportArchiveApprovalId
  ) => {
    if (!props.signedIn || !canRead || !approvalId) {
      setReportArchiveNotes([]);
      return;
    }
    try {
      const response = await fetchTestDesignReportArchiveNotes(approvalId);
      setReportArchiveNotes(response.data);
    } catch (error: unknown) {
      setReportArchiveNotes([]);
      setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, '归档备注加载失败') });
    }
  }, [canRead, props.signedIn, selectedReportArchiveApprovalId]);

  const refreshTemplates = useCallback(async (options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead) {
      setTemplates([]);
      setTemplatePageTotal(0);
      setSelectedTemplateManageId('');
      setTemplateState({ loading: false });
      return;
    }

    const silent = options?.silent === true;
    if (silent) {
      setTemplateState((current) => ({ ...current, error: undefined }));
    } else {
      setTemplateState({ loading: true });
    }
    try {
      const response = await fetchTestDesignTemplates({
        size: 30,
        projectId: templateProjectId,
        includeGlobal: true
      });
      setTemplates(response.data.items);
      setTemplatePageTotal(response.data.total);
      setSelectedTemplateManageId((current) => (
        current && response.data.items.some((template) => template.id === current) ? current : response.data.items[0]?.id ?? ''
      ));
      setTemplateState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      setTemplates([]);
      setTemplatePageTotal(0);
      if (!silent) {
        setTemplateState({ loading: false, error: testDesignErrorMessage(error, '生成模板加载失败') });
      }
    }
  }, [
    canRead,
    props.signedIn,
    templateProjectId
  ]);

  const refreshReviewRecords = useCallback(async (taskId: string, options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead || !taskId) {
      setReviewRecords([]);
      setReviewRecordPageTotal(0);
      setTaskQualitySummary(null);
      setReviewRecordState({ loading: false });
      return;
    }

    const silent = options?.silent === true;
    if (silent) {
      setReviewRecordState((current) => ({ ...current, error: undefined }));
    } else {
      setReviewRecordState({ loading: true });
    }
    try {
      const response = await fetchTestDesignReviewRecords(taskId, {
        index: reviewRecordPageIndex,
        size: 10
      });
      const page = response.data;
      const normalizedPage = pageFromServerItems(page.items, page.index ?? reviewRecordPageIndex, page.size ?? 10, page.total);
      if (normalizedPage.index !== reviewRecordPageIndex && page.total > 0 && !page.items.length) {
        setReviewRecordPageIndex(normalizedPage.index);
      }
      setReviewRecords(page.items);
      setReviewRecordPageTotal(page.total);
      setReviewRecordState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      if (!silent) {
        setReviewRecords([]);
        setReviewRecordPageTotal(0);
      }
      setReviewRecordState({ loading: false, error: testDesignErrorMessage(error, '评审历史加载失败') });
    }
  }, [
    canRead,
    props.signedIn,
    reviewRecordPageIndex
  ]);

  const refreshAll = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setRequirements([]);
      setTasks([]);
      setCandidates([]);
      setCandidatePageTotal(0);
      setTemplates([]);
      setTemplatePageTotal(0);
      setSelectedTemplateManageId('');
      setTemplateDraft(initialTemplateDraft);
      setReviewRecords([]);
      setReviewRecordPageTotal(0);
      setSelectedRequirementIds([]);
      setSelectedTaskId('');
      setSelectedCandidateId('');
      setSelectedCandidateIds([]);
      setSelectedCandidateCache({});
      setCandidatePageIndex(0);
      setBatchActionResult(null);
      setBatchEditDraft(initialTestDesignBatchEditDraft);
      setBatchEditResult(null);
      setConflictResolutionDraft(initialConflictResolutionDraft);
      setConflictCaseKeyword('');
      setConflictCaseResults([]);
      setSelectedConflictCaseIds({});
      setPendingConfirmation(null);
      setTaskQualitySummary(null);
      setTaskAuditSummary(null);
      setPromptTrend(null);
      setEvaluationCorpusSummary(null);
      setEvaluationSamples([]);
      setEvaluationSampleSummary(null);
      setEvaluationSamplePageTotal(0);
      setSelectedEvaluationSampleId('');
      setEvaluationSampleFilters(initialEvaluationSampleFilters);
      setEvaluationSampleDraft(initialEvaluationSampleDraft);
      setCalibrationRuns([]);
      setCalibrationSummary(null);
      setCalibrationRunDraft(initialCalibrationRunDraft);
      setCrossWpOperationsDashboard(null);
      setAuditReportTemplate(null);
      setModelObservationDrilldown(null);
      setCrossWpDetailAuditReport(null);
      setCrossWpOperationsFilters(initialCrossWpOperationsFilters);
      setAuditOutboxRequeueDraft(initialAuditOutboxRequeueDraft);
      setAuditOutboxRequeueResult(null);
      setQueueAlertSubscriptions([]);
      setQueueAlertSubscriptionDraft(initialQueueAlertSubscriptionDraft);
      setQueueAlertSubscriptionResult(null);
      setQueuedEventReplayDraft(initialQueuedEventReplayDraft);
      setQueuedEventReplayResult(null);
      setPublishCompensationRunDraft(initialPublishCompensationRunDraft);
      setPublishCompensationRunResult(null);
      setContextPolicyDraft(initialTestDesignContextPolicyDraft);
      setContextPolicyOverrides([]);
      setContextPolicyEffective(null);
      setSelectedContextPolicyOverrideId('');
      setContextPolicyNotes([]);
      setReleaseReadinessDraft(initialReleaseReadinessDraft);
      setReleaseReadinessApprovals([]);
      setSelectedReleaseReadinessApprovalId('');
      setReleaseReadinessNotes([]);
      setLoadState({ loading: false });
      setTaskState({ loading: false });
      setReviewRecordState({ loading: false });
      setTaskAuditState({ loading: false });
      setPromptTrendState({ loading: false });
      setEvaluationCorpusState({ loading: false });
      setCrossWpOperationsState({ loading: false });
      setContextPolicyState({ loading: false });
      setTemplateState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, requirementResult, taskResult] = await Promise.allSettled([
      fetchTestDesignHealth(),
      fetchAssetRequirements({
        size: 80,
        projectId: filters.projectId,
        status: filters.status,
        keyword: filters.keyword
      }),
      fetchTestDesignTasks({
        size: 20,
        projectId: taskFilters.projectId || filters.projectId,
        status: taskFilters.status,
        keyword: taskFilters.keyword
      })
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];
    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(testDesignErrorMessage(healthResult.reason, '用例生成服务健康检查失败'));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(testDesignErrorMessage(requirementResult.reason, '需求列表加载失败'));
    }

    if (taskResult.status === 'fulfilled') {
      setTasks(taskResult.value.data.items);
      traceIds.push(taskResult.value.trace_id);
      setSelectedTaskId((current) => taskResult.value.data.items.some((task) => task.id === current) ? current : taskResult.value.data.items[0]?.id || '');
    } else {
      setTasks([]);
      errors.push(testDesignErrorMessage(taskResult.reason, '生成任务列表加载失败'));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canRead, filters.keyword, filters.projectId, filters.status, props.signedIn, taskFilters.keyword, taskFilters.projectId, taskFilters.status]);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  useEffect(() => {
    void refreshPromptTrend();
  }, [refreshPromptTrend]);

  useEffect(() => {
    void refreshEvaluationCorpusOperations();
  }, [refreshEvaluationCorpusOperations]);

  useEffect(() => {
    void refreshCrossWpOperations();
  }, [refreshCrossWpOperations]);

  useEffect(() => {
    void refreshTemplates();
  }, [refreshTemplates]);

  useEffect(() => {
    setCandidates([]);
    setCandidatePageTotal(0);
    setSelectedCandidateId('');
    setSelectedCandidateIds([]);
    setSelectedCandidateCache({});
    setCandidateDraft(null);
    setCandidatePageIndex(0);
    setReviewRecords([]);
    setReviewRecordPageTotal(0);
    setReviewRecordPageIndex(0);
    setTaskQualitySummary(null);
    setPublishResult(null);
    setBatchActionResult(null);
    setBatchEditDraft(initialTestDesignBatchEditDraft);
    setBatchEditResult(null);
    setConflictResolutionDraft(initialConflictResolutionDraft);
    setConflictCaseKeyword('');
    setConflictCaseResults([]);
    setSelectedConflictCaseIds({});
    setPendingConfirmation(null);
    setAuditOutboxRequeueResult(null);
    setReleaseReadinessDraft(initialReleaseReadinessDraft);
    setReleaseReadinessApprovals([]);
    setSelectedReleaseReadinessApprovalId('');
    setReleaseReadinessNotes([]);
    setReportArchiveDraft(initialReportArchiveDraft);
    setReportArchives([]);
    setSelectedReportArchiveId('');
    setReportArchiveIntegrity(null);
    setReportArchiveApprovals([]);
    setSelectedReportArchiveApprovalId('');
    setReportArchiveNotes([]);
  }, [selectedTaskId]);

  useEffect(() => {
    setEvaluationSampleDraft(selectedEvaluationSample ? evaluationSampleDraftFromView(selectedEvaluationSample) : {
      ...initialEvaluationSampleDraft,
      projectId: evaluationCorpusProjectId,
      promptKey: evaluationCorpusPromptKey,
      promptVersion: selectedTask?.promptVersion || '',
      baselineVersion: evaluationSampleFilters.baselineVersion
    });
  }, [
    evaluationCorpusProjectId,
    evaluationCorpusPromptKey,
    evaluationSampleFilters.baselineVersion,
    selectedEvaluationSample,
    selectedTask?.promptVersion
  ]);

  useEffect(() => {
    void refreshCandidatePage(selectedTaskId);
  }, [refreshCandidatePage, selectedTaskId]);

  useEffect(() => {
    void refreshReviewRecords(selectedTaskId);
  }, [refreshReviewRecords, selectedTaskId]);

  useEffect(() => {
    void refreshTaskQualitySummary(selectedTaskId);
  }, [refreshTaskQualitySummary, selectedTaskId]);

  useEffect(() => {
    void refreshTaskAuditSummary(selectedTaskId);
  }, [refreshTaskAuditSummary, selectedTaskId]);

  useEffect(() => {
    void refreshReportArchives(selectedTaskId, { silent: true });
  }, [refreshReportArchives, selectedTaskId]);

  useEffect(() => {
    void refreshReportArchiveDetail(selectedReportArchiveId, { silent: true });
  }, [refreshReportArchiveDetail, selectedReportArchiveId]);

  useEffect(() => {
    void refreshReportArchiveNotes(selectedReportArchiveApprovalId);
  }, [refreshReportArchiveNotes, selectedReportArchiveApprovalId]);

  useEffect(() => {
    if (!selectedTaskId || !selectedTaskAsyncInFlight) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void refreshCandidatePage(selectedTaskId, { silent: true });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
      void refreshTaskAuditSummary(selectedTaskId, { silent: true });
      void refreshPromptTrend({ silent: true });
      void refreshEvaluationCorpusOperations({ silent: true });
      void refreshCrossWpOperations({ silent: true });
    }, 2000);
    return () => window.clearInterval(timer);
  }, [
    refreshCandidatePage,
    refreshCrossWpOperations,
    refreshEvaluationCorpusOperations,
    refreshPromptTrend,
    refreshReviewRecords,
    refreshTaskAuditSummary,
    refreshTaskQualitySummary,
    selectedTaskAsyncInFlight,
    selectedTaskId
  ]);

  useEffect(() => {
    const nextCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId) ?? null;
    setCandidateDraft(nextCandidate ? draftFromCandidate(nextCandidate) : null);
    setReviewComment(nextCandidate?.reviewComment ?? nextCandidate?.rejectedReason ?? nextCandidate?.ignoredReason ?? '');
  }, [candidates, selectedCandidateId]);

  useEffect(() => {
    setTemplateDraft(selectedManagedTemplate ? templateDraftFromView(selectedManagedTemplate) : {
      ...initialTemplateDraft,
      projectId: templateProjectId
    });
  }, [selectedManagedTemplate, templateProjectId]);

  useEffect(() => {
    if (!generationDraft.projectId && filters.projectId) {
      setGenerationDraft((current) => ({ ...current, projectId: filters.projectId }));
    }
  }, [filters.projectId, generationDraft.projectId]);

  useEffect(() => {
    if (generationDraft.templateId && !templates.some((template) => template.id === generationDraft.templateId)) {
      setGenerationDraft((current) => ({ ...current, templateId: '' }));
    }
  }, [generationDraft.templateId, templates]);

  useEffect(() => {
    if (!taskFilters.projectId && filters.projectId) {
      setTaskFilters((current) => ({ ...current, projectId: filters.projectId }));
    }
  }, [filters.projectId, taskFilters.projectId]);

  useEffect(() => {
    if (!contextPolicyDraft.projectId && (selectedTask?.projectId || filters.projectId || taskFilters.projectId)) {
      setContextPolicyDraft((current) => ({
        ...current,
        projectId: selectedTask?.projectId || filters.projectId || taskFilters.projectId
      }));
    }
  }, [contextPolicyDraft.projectId, filters.projectId, selectedTask?.projectId, taskFilters.projectId]);

  useEffect(() => {
    const projectId = selectedTask?.projectId || taskFilters.projectId || filters.projectId || generationDraft.projectId || '';
    const promptKey = health?.promptKey || selectedTask?.promptKey || generationDraft.promptKey || '';
    setEvaluationSampleFilters((current) => ({
      ...current,
      projectId: current.projectId || projectId,
      promptKey: current.promptKey || promptKey
    }));
    setEvaluationSampleDraft((current) => ({
      ...current,
      projectId: current.projectId || projectId,
      promptKey: current.promptKey || promptKey,
      promptVersion: current.promptVersion || selectedTask?.promptVersion || ''
    }));
    setCalibrationRunDraft((current) => ({
      ...current,
      projectId: current.projectId || projectId,
      promptKey: current.promptKey || promptKey,
      promptVersion: current.promptVersion || selectedTask?.promptVersion || ''
    }));
  }, [
    filters.projectId,
    generationDraft.projectId,
    generationDraft.promptKey,
    health?.promptKey,
    selectedTask?.projectId,
    selectedTask?.promptKey,
    selectedTask?.promptVersion,
    taskFilters.projectId
  ]);

  useEffect(() => {
    void refreshReleaseReadinessApprovals(selectedTaskId, { silent: true });
  }, [refreshReleaseReadinessApprovals, selectedTaskId]);

  useEffect(() => {
    void refreshReleaseReadinessNotes(selectedReleaseReadinessApprovalId);
  }, [refreshReleaseReadinessNotes, selectedReleaseReadinessApprovalId]);

  useEffect(() => {
    setCandidatePageIndex(0);
  }, [candidateFilters.coverageType, candidateFilters.keyword, candidateFilters.status, candidatePageSize, selectedTaskId]);

  useEffect(() => {
    if (candidatePage.index !== candidatePageIndex) {
      setCandidatePageIndex(candidatePage.index);
    }
  }, [candidatePage.index, candidatePageIndex]);

  useEffect(() => {
    if (reviewRecordPage.index !== reviewRecordPageIndex) {
      setReviewRecordPageIndex(reviewRecordPage.index);
    }
  }, [reviewRecordPage.index, reviewRecordPageIndex]);

  useEffect(() => {
    if (selectedCandidateId && candidatePage.items.some((candidate) => candidate.id === selectedCandidateId)) {
      return;
    }
    setSelectedCandidateId(candidatePage.items[0]?.id ?? '');
  }, [candidatePage.items, selectedCandidateId]);

  function toggleRequirement(id: string) {
    setSelectedRequirementIds((current) => {
      if (current.includes(id)) {
        return current.filter((item) => item !== id);
      }
      return [...current, id];
    });
  }

  function toggleCoverage(type: string) {
    setGenerationDraft((current) => {
      const coverageTypes = current.coverageTypes.includes(type)
        ? current.coverageTypes.filter((item) => item !== type)
        : [...current.coverageTypes, type];
      return { ...current, coverageTypes };
    });
  }

  function toggleTemplateCoverage(type: string) {
    setTemplateDraft((current) => {
      const coverageTypes = current.coverageTypes.includes(type)
        ? current.coverageTypes.filter((item) => item !== type)
        : [...current.coverageTypes, type];
      return { ...current, coverageTypes };
    });
  }

  function selectGenerationTemplate(templateId: string) {
    const template = templates.find((item) => item.id === templateId);
    setGenerationDraft((current) => {
      if (!template) {
        return { ...current, templateId: '' };
      }
      const contextDefaults = template.contextDefaults;
      return {
        ...current,
        templateId: template.id,
        projectId: current.projectId || template.projectId || filters.projectId || '',
        promptKey: template.promptKey,
        promptVersion: template.promptVersion,
        generationStrategy: template.generationStrategy,
        coverageStrategy: template.coverageStrategy,
        environmentKey: stringDefault(contextDefaults.environmentKey),
        caseCountPerRequirement: String(template.caseCountPerRequirement || current.caseCountPerRequirement || 1),
        coverageTypes: template.coverageTypes.length ? template.coverageTypes : current.coverageTypes,
        contextApiIds: templateContextIds(contextDefaults.contextApiIds),
        contextPageIds: templateContextIds(contextDefaults.contextPageIds),
        contextFlowIds: templateContextIds(contextDefaults.contextFlowIds)
      };
    });
  }

  function updateStepDraft(stepId: string, patch: Partial<TestDesignStepDraft>) {
    setCandidateDraft((current) => current ? {
      ...current,
      steps: current.steps.map((step) => step.id === stepId ? { ...step, ...patch } : step)
    } : current);
  }

  function addStepDraft() {
    setCandidateDraft((current) => current ? {
      ...current,
      steps: [...current.steps, emptyStepDraft()]
    } : current);
  }

  function insertStepDraftAfter(stepId: string) {
    setCandidateDraft((current) => {
      if (!current) {
        return current;
      }
      const index = current.steps.findIndex((step) => step.id === stepId);
      const steps = [...current.steps];
      steps.splice(index >= 0 ? index + 1 : steps.length, 0, emptyStepDraft());
      return { ...current, steps };
    });
  }

  function removeStepDraft(stepId: string) {
    setCandidateDraft((current) => current ? {
      ...current,
      steps: current.steps.filter((step) => step.id !== stepId)
    } : current);
  }

  function moveStepDraft(stepId: string, direction: -1 | 1) {
    setCandidateDraft((current) => {
      if (!current) {
        return current;
      }
      const index = current.steps.findIndex((step) => step.id === stepId);
      const targetIndex = index + direction;
      if (index < 0 || targetIndex < 0 || targetIndex >= current.steps.length) {
        return current;
      }
      const steps = [...current.steps];
      const [step] = steps.splice(index, 1);
      steps.splice(targetIndex, 0, step);
      return { ...current, steps };
    });
  }

  function dropStepDraft(targetStepId: string) {
    if (!draggingStepId || draggingStepId === targetStepId) {
      return;
    }
    setCandidateDraft((current) => {
      if (!current) {
        return current;
      }
      const fromIndex = current.steps.findIndex((step) => step.id === draggingStepId);
      const toIndex = current.steps.findIndex((step) => step.id === targetStepId);
      if (fromIndex < 0 || toIndex < 0) {
        return current;
      }
      const steps = [...current.steps];
      const [step] = steps.splice(fromIndex, 1);
      steps.splice(toIndex, 0, step);
      return { ...current, steps };
    });
    setDraggingStepId('');
  }

  function deleteSelectedSteps() {
    setCandidateDraft((current) => current ? {
      ...current,
      steps: current.steps.filter((step) => !step.selected)
    } : current);
  }

  function insertPresetSteps() {
    setCandidateDraft((current) => current ? {
      ...current,
      steps: [
        ...current.steps,
        emptyStepDraft('准备测试数据', '测试数据满足前置条件'),
        emptyStepDraft('执行核心操作', '系统返回成功状态'),
        emptyStepDraft('核对结果状态', '页面、接口和数据状态一致')
      ]
    } : current);
  }

  function toggleCandidateSelection(candidateId: string) {
    setSelectedCandidateIds((current) => {
      if (current.includes(candidateId)) {
        return current.filter((item) => item !== candidateId);
      }
      return [...current, candidateId];
    });
  }

  function selectCurrentPageCandidates() {
    setSelectedCandidateIds((current) => {
      const next = new Set(current);
      currentPageSelectableCandidates.forEach((candidate) => next.add(candidate.id));
      return Array.from(next);
    });
  }

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setMutationState({ loading: false, error: '请先登录后再生成用例' });
      return;
    }
    if (!canGenerate) {
      setMutationState({ loading: false, error: '缺少 testDesign:generate 权限' });
      return;
    }
    if (!generationDraft.projectId.trim()) {
      setMutationState({ loading: false, error: '请输入项目 ID' });
      return;
    }
    if (!selectedRequirementIds.length) {
      setMutationState({ loading: false, error: '请至少选择一个需求' });
      return;
    }
    if (!generationDraft.coverageTypes.length) {
      setMutationState({ loading: false, error: '请至少选择一种覆盖类型' });
      return;
    }

    setMutationState({ loading: true });
    const createPayload = {
      projectId: generationDraft.projectId,
      templateId: generationDraft.templateId,
      title: generationDraft.title,
      requirementIds: selectedRequirementIds,
      contextApiIds: parseContextAssetIds(generationDraft.contextApiIds),
      contextPageIds: parseContextAssetIds(generationDraft.contextPageIds),
      contextFlowIds: parseContextAssetIds(generationDraft.contextFlowIds),
      environmentKey: generationDraft.environmentKey,
      promptKey: generationDraft.promptKey,
      promptVersion: generationDraft.promptVersion,
      coverageTypes: generationDraft.coverageTypes,
      caseCountPerRequirement: Number(generationDraft.caseCountPerRequirement) || undefined
    };
    const idempotency = resolveTestDesignTaskIdempotency(
      generationIdempotencyRef.current,
      buildTestDesignTaskIdempotencySignature(createPayload)
    );
    generationIdempotencyRef.current = idempotency;
    try {
      const response = await createTestDesignTask({
        ...createPayload,
        idempotencyKey: idempotency.key
      });
      setTasks((current) => upsertTask(current, response.data.task));
      setCandidates(response.data.candidates);
      setCandidatePageTotal(response.data.candidates.length);
      setSelectedCandidateCache(mergeCandidateCache({}, response.data.candidates));
      setSelectedTaskId(response.data.task.id);
      setSelectedCandidateId(response.data.candidates[0]?.id ?? '');
      setSelectedCandidateIds([]);
      setPublishResult(null);
      setBatchActionResult(null);
      setBatchEditDraft(initialTestDesignBatchEditDraft);
      setBatchEditResult(null);
      void refreshReviewRecords(response.data.task.id, { silent: true });
      void refreshTaskQualitySummary(response.data.task.id, { silent: true });
      void refreshTaskAuditSummary(response.data.task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
      setPendingConfirmation(null);
      generationIdempotencyRef.current = null;
      setMutationState({
        loading: false,
        success: ASYNC_TASK_STATUSES.has(response.data.task.status) ? '生成任务已提交，候选生成中' : '候选用例已生成',
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, '候选用例生成失败') });
    }
  }

  async function saveTemplate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setTemplateState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!templateDraft.name.trim()) {
      setTemplateState({ loading: false, error: '请输入模板名称' });
      return;
    }
    if (!templateDraft.coverageTypes.length) {
      setTemplateState({ loading: false, error: '请至少选择一种模板覆盖类型' });
      return;
    }

    setTemplateState({ loading: true });
    try {
      const payload = templatePayload(templateDraft, !selectedManagedTemplate);
      const response = selectedManagedTemplate
        ? await updateTestDesignTemplate(selectedManagedTemplate.id, payload)
        : await createTestDesignTemplate(payload);
      setTemplates((current) => upsertTemplate(current, response.data));
      setSelectedTemplateManageId(response.data.id);
      if (generationDraft.templateId === response.data.id) {
        selectGenerationTemplate(response.data.id);
      }
      setTemplateState({
        loading: false,
        success: selectedManagedTemplate ? '生成模板已更新' : '生成模板已创建',
        traceId: response.trace_id
      });
      void refreshTemplates({ silent: true });
    } catch (error: unknown) {
      setTemplateState({ loading: false, error: testDesignErrorMessage(error, selectedManagedTemplate ? '生成模板更新失败' : '生成模板创建失败') });
    }
  }

  async function disableTemplate() {
    if (!selectedManagedTemplate) {
      setTemplateState({ loading: false, error: '请先选择模板' });
      return;
    }
    if (!canPolicyManage) {
      setTemplateState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }

    setTemplateState({ loading: true });
    try {
      const response = await deleteTestDesignTemplate(selectedManagedTemplate.id);
      setTemplates((current) => upsertTemplate(current, response.data));
      setGenerationDraft((current) => current.templateId === response.data.id ? { ...current, templateId: '' } : current);
      setTemplateState({ loading: false, success: '生成模板已禁用', traceId: response.trace_id });
      void refreshTemplates({ silent: true });
    } catch (error: unknown) {
      setTemplateState({ loading: false, error: testDesignErrorMessage(error, '生成模板禁用失败') });
    }
  }

  async function saveEvaluationSample(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!evaluationSampleDraft.projectId.trim()) {
      setEvaluationCorpusState({ loading: false, error: '请输入样本项目 ID' });
      return;
    }
    if (!evaluationSampleDraft.title.trim() || !evaluationSampleDraft.requirementSummary.trim()
        || !evaluationSampleDraft.expectedCaseOutline.trim()) {
      setEvaluationCorpusState({ loading: false, error: '请填写样本标题、需求摘要和期望轮廓' });
      return;
    }
    setEvaluationCorpusState({ loading: true });
    try {
      const payload = evaluationSamplePayload(evaluationSampleDraft);
      const response = selectedEvaluationSample
        ? await updateTestDesignEvaluationSample(selectedEvaluationSample.id, payload)
        : await createTestDesignEvaluationSample(payload);
      setSelectedEvaluationSampleId(response.data.id);
      setEvaluationSamples((current) => upsertEvaluationSample(current, response.data));
      setEvaluationCorpusState({
        loading: false,
        success: selectedEvaluationSample ? '评测样本已更新' : '评测样本已创建',
        traceId: response.trace_id
      });
      void refreshEvaluationCorpusOperations({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, '评测样本保存失败') });
    }
  }

  async function transitionEvaluationSample(status: string) {
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!selectedEvaluationSample) {
      setEvaluationCorpusState({ loading: false, error: '请先选择评测样本' });
      return;
    }
    setEvaluationCorpusState({ loading: true });
    try {
      const response = await transitionTestDesignEvaluationSample(selectedEvaluationSample.id, {
        status,
        baselineVersion: evaluationSampleDraft.baselineVersion,
        maintenanceNote: evaluationSampleDraft.maintenanceNote
      });
      setEvaluationSamples((current) => upsertEvaluationSample(current, response.data));
      setEvaluationCorpusState({ loading: false, success: `样本已流转为 ${status}`, traceId: response.trace_id });
      void refreshEvaluationCorpusOperations({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, '样本状态流转失败') });
    }
  }

  async function extractEvaluationSampleFromCandidate() {
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!selectedCandidateId) {
      setEvaluationCorpusState({ loading: false, error: '请先选择候选用例' });
      return;
    }
    setEvaluationCorpusState({ loading: true });
    try {
      const response = await createTestDesignEvaluationSampleFromCandidate({
        candidateId: selectedCandidateId,
        sampleKey: evaluationSampleDraft.sampleKey,
        status: evaluationSampleDraft.status,
        baselineVersion: evaluationSampleDraft.baselineVersion,
        maintenanceNote: evaluationSampleDraft.maintenanceNote
      });
      setSelectedEvaluationSampleId(response.data.id);
      setEvaluationSamples((current) => upsertEvaluationSample(current, response.data));
      setEvaluationCorpusState({ loading: false, success: '已从候选提取评测样本', traceId: response.trace_id });
      void refreshEvaluationCorpusOperations({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, '候选样本提取失败') });
    }
  }

  async function runCalibration() {
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!calibrationRunDraft.projectId.trim()) {
      setEvaluationCorpusState({ loading: false, error: '请输入校准项目 ID' });
      return;
    }
    setEvaluationCorpusState({ loading: true });
    try {
      const response = await requestTestDesignCalibrationRun({
        projectId: calibrationRunDraft.projectId,
        promptKey: calibrationRunDraft.promptKey,
        promptVersion: calibrationRunDraft.promptVersion,
        baselineVersion: calibrationRunDraft.baselineVersion,
        runMode: calibrationRunDraft.runMode,
        notes: calibrationRunDraft.notes
      });
      setCalibrationRuns((current) => [response.data, ...current.filter((run) => run.id !== response.data.id)].slice(0, 6));
      setEvaluationCorpusState({ loading: false, success: `校准运行完成：${response.data.status}`, traceId: response.trace_id });
      void refreshEvaluationCorpusOperations({ silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, '校准运行失败') });
    }
  }

  async function requeueAuditOutbox(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    const projectId = auditOutboxRequeueDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: '请输入 outbox 重放项目 ID' });
      return;
    }
    const maxItems = Number.parseInt(auditOutboxRequeueDraft.maxItems, 10);
    setCrossWpOperationsState({ loading: true });
    try {
      const response = await requeueTestDesignAuditOutbox({
        projectId,
        status: auditOutboxRequeueDraft.status,
        maxItems: Number.isFinite(maxItems) ? maxItems : 20,
        reason: auditOutboxRequeueDraft.reason
      });
      setAuditOutboxRequeueResult(response.data);
      setAuditOutboxRequeueDraft((current) => ({ ...current, projectId, reason: '' }));
      setCrossWpOperationsState({
        loading: false,
        success: `已重新排队 ${response.data.requeuedCount} 条 outbox`,
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, 'Audit outbox 重新排队失败') });
    }
  }

  async function saveQueueAlertSubscription(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    const projectId = queueAlertSubscriptionDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: '请输入队列告警项目 ID' });
      return;
    }
    const promptKey = queueAlertSubscriptionDraft.promptKey.trim();
    const thresholdSeconds = Number.parseInt(queueAlertSubscriptionDraft.thresholdSeconds, 10);
    setCrossWpOperationsState({ loading: true });
    try {
      const response = await upsertTestDesignQueueAlertSubscription({
        projectId,
        promptKey: promptKey || undefined,
        alertType: queueAlertSubscriptionDraft.alertType,
        channel: queueAlertSubscriptionDraft.channel,
        targetRef: queueAlertSubscriptionDraft.targetRef,
        thresholdSeconds: Number.isFinite(thresholdSeconds) ? thresholdSeconds : undefined,
        enabled: queueAlertSubscriptionDraft.enabled
      });
      setQueueAlertSubscriptionResult(response.data);
      setQueueAlertSubscriptions((current) => {
        const next = current.filter((item) => item.id !== response.data.id);
        next.unshift(response.data);
        return next;
      });
      setQueueAlertSubscriptionDraft((current) => ({ ...current, projectId, promptKey }));
      setCrossWpOperationsState({
        loading: false,
        success: `队列告警订阅已保存：${response.data.alertType}`,
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, '队列告警订阅保存失败') });
    }
  }

  async function replayQueuedEvents(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    const projectId = queuedEventReplayDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: '请输入 queued event 重放项目 ID' });
      return;
    }
    const maxItems = Number.parseInt(queuedEventReplayDraft.maxItems, 10);
    setCrossWpOperationsState({ loading: true });
    try {
      const response = await replayTestDesignQueuedEvents({
        projectId,
        promptKey: queuedEventReplayDraft.promptKey.trim() || undefined,
        replayType: queuedEventReplayDraft.replayType,
        maxItems: Number.isFinite(maxItems) ? maxItems : 20,
        reason: queuedEventReplayDraft.reason
      });
      setQueuedEventReplayResult(response.data);
      setQueuedEventReplayDraft((current) => ({ ...current, projectId }));
      setCrossWpOperationsState({
        loading: false,
        success: `已重放生成 ${response.data.generationTaskEvents} · 发布 ${response.data.publishCandidateEvents}`,
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, 'queued event 重放失败') });
    }
  }

  async function runPublishCompensation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    const projectId = publishCompensationRunDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: '请输入补偿运行项目 ID' });
      return;
    }
    const maxItems = Number.parseInt(publishCompensationRunDraft.maxItems, 10);
    setCrossWpOperationsState({ loading: true });
    try {
      const response = await runTestDesignPublishCompensation({
        projectId,
        promptKey: publishCompensationRunDraft.promptKey.trim() || undefined,
        maxItems: Number.isFinite(maxItems) ? maxItems : 20,
        reason: publishCompensationRunDraft.reason
      });
      setPublishCompensationRunResult(response.data);
      setPublishCompensationRunDraft((current) => ({ ...current, projectId }));
      setCrossWpOperationsState({
        loading: false,
        success: `补偿扫描 ${response.data.scannedCandidates} · 成功 ${response.data.succeededCandidates}`,
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, '发布补偿运行失败') });
    }
  }

  async function retryTask(task: TestDesignTaskView) {
    if (!canGenerate) {
      setTaskState({ loading: false, error: '缺少 testDesign:generate 权限' });
      return;
    }
    if (!RETRYABLE_TASK_STATUSES.has(task.status)) {
      setTaskState({ loading: false, error: `当前任务状态不可重试：${task.status}` });
      return;
    }

    setSelectedTaskId(task.id);
    setTaskState({ loading: true });
    setPublishResult(null);
    try {
      const response = await retryTestDesignTask(task.id);
      setTasks((current) => upsertTask(current, response.data.task));
      setCandidates(response.data.candidates);
      setCandidatePageTotal(response.data.candidates.length);
      setSelectedCandidateCache(mergeCandidateCache({}, response.data.candidates));
      setSelectedCandidateId(response.data.candidates[0]?.id ?? '');
      setSelectedCandidateIds([]);
      setBatchActionResult(null);
      setBatchEditDraft(initialTestDesignBatchEditDraft);
      setBatchEditResult(null);
      setTaskState({ loading: false, success: '生成任务已重试', traceId: response.trace_id });
      void refreshReviewRecords(task.id, { silent: true });
      void refreshTaskQualitySummary(task.id, { silent: true });
      void refreshTaskAuditSummary(task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '生成任务重试失败') });
    }
  }

  async function replayQueuedTaskEvent(task: TestDesignTaskView) {
    if (!canGenerate) {
      setTaskState({ loading: false, error: '缺少 testDesign:generate 权限' });
      return;
    }
    if (task.status !== 'QUEUED') {
      setTaskState({ loading: false, error: `仅 QUEUED 任务支持排队事件重发：${task.status}` });
      return;
    }

    setSelectedTaskId(task.id);
    setTaskState({ loading: true });
    setPublishResult(null);
    try {
      const response = await replayQueuedTestDesignTaskEvent(task.id);
      setTasks((current) => upsertTask(current, response.data.task));
      setCandidates(response.data.candidates);
      setCandidatePageTotal(response.data.candidates.length);
      setSelectedCandidateCache(mergeCandidateCache({}, response.data.candidates));
      setSelectedCandidateId(response.data.candidates[0]?.id ?? '');
      setSelectedCandidateIds([]);
      setBatchActionResult(null);
      setBatchEditDraft(initialTestDesignBatchEditDraft);
      setBatchEditResult(null);
      setTaskState({ loading: false, success: '排队生成事件已重发', traceId: response.trace_id });
      void refreshReviewRecords(task.id, { silent: true });
      void refreshTaskQualitySummary(task.id, { silent: true });
      void refreshTaskAuditSummary(task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '排队生成事件重发失败') });
    }
  }

  async function cancelTask(task: TestDesignTaskView) {
    if (!canGenerate) {
      setTaskState({ loading: false, error: '缺少 testDesign:generate 权限' });
      return;
    }
    if (!CANCELLABLE_TASK_STATUSES.has(task.status)) {
      setTaskState({ loading: false, error: `当前任务状态不可取消：${task.status}` });
      return;
    }

    setSelectedTaskId(task.id);
    setTaskState({ loading: true });
    try {
      const response = await cancelTestDesignTask(task.id);
      setTasks((current) => upsertTask(current, response.data.task));
      setCandidates(response.data.candidates);
      setCandidatePageTotal(response.data.candidates.length);
      setSelectedCandidateCache(mergeCandidateCache({}, response.data.candidates));
      setSelectedCandidateId(response.data.candidates[0]?.id ?? '');
      setSelectedCandidateIds([]);
      setBatchActionResult(null);
      setBatchEditDraft(initialTestDesignBatchEditDraft);
      setBatchEditResult(null);
      setPublishResult(null);
      setTaskState({ loading: false, success: '生成任务已取消', traceId: response.trace_id });
      void refreshReviewRecords(task.id, { silent: true });
      void refreshTaskQualitySummary(task.id, { silent: true });
      void refreshTaskAuditSummary(task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '生成任务取消失败') });
    }
  }

  async function requestContextPolicyOverride(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setContextPolicyState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (contextPolicySubmitBlocked) {
      setContextPolicyState({ loading: false, error: `上下文策略校验不通过：${contextPolicySubmitIssues[0]?.message ?? '请检查字段'}` });
      return;
    }

    const projectId = contextPolicyDraft.projectId.trim();
    const environmentKey = contextPolicyDraft.environmentKey.trim();
    const payload = buildTestDesignContextPolicyPayload(contextPolicyDraft);
    setContextPolicyState({ loading: true });
    try {
      let response: Awaited<ReturnType<typeof updateTestDesignContextPolicyOverride>>;
      if (selectedPendingContextPolicyOverride) {
        response = await updateTestDesignContextPolicyOverride(selectedPendingContextPolicyOverride.id, payload);
      } else if (contextPolicyDraft.scopeType === 'ENVIRONMENT') {
        response = await requestTestDesignEnvironmentContextPolicyOverride(projectId, environmentKey, payload);
      } else {
        response = await requestTestDesignProjectContextPolicyOverride(projectId, payload);
      }
      setContextPolicyOverrides((current) => [response.data, ...current.filter((item) => item.id !== response.data.id)]);
      setSelectedContextPolicyOverrideId(response.data.id);
      setContextPolicyState({
        loading: false,
        success: selectedPendingContextPolicyOverride ? '上下文策略草稿已更新' : '上下文策略覆盖已提交审批',
        traceId: response.trace_id
      });
      void loadContextPolicyNotes(response.data.id, { silent: true });
      void refreshContextPolicy({ silent: true });
    } catch (error: unknown) {
      setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, '上下文策略覆盖提交失败') });
    }
  }

  async function reviewContextPolicyOverride(overrideId: string, action: 'approve' | 'reject') {
    if (!canPolicyManage) {
      setContextPolicyState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!contextPolicyDraft.approvalReasonCode) {
      setContextPolicyState({ loading: false, error: '请选择审批原因编码' });
      return;
    }
    const reviewIssue = contextPolicyIssues.find((issue) => ['approvalReasonCode', 'reviewNote', 'workOrderStatus'].includes(issue.field));
    if (reviewIssue) {
      setContextPolicyState({ loading: false, error: `上下文策略审批校验不通过：${reviewIssue.message}` });
      return;
    }

    setContextPolicyState({ loading: true });
    try {
      const response = action === 'approve'
        ? await approveTestDesignContextPolicyOverride(overrideId, {
          approvalReasonCode: contextPolicyDraft.approvalReasonCode,
          reviewNote: contextPolicyDraft.reviewNote,
          workOrderStatus: contextPolicyDraft.workOrderStatus || undefined
        })
        : await rejectTestDesignContextPolicyOverride(overrideId, {
          approvalReasonCode: contextPolicyDraft.approvalReasonCode,
          reviewNote: contextPolicyDraft.reviewNote,
          workOrderStatus: contextPolicyDraft.workOrderStatus || undefined
        });
      setContextPolicyOverrides((current) => current.map((item) => item.id === response.data.id ? response.data : item));
      setSelectedContextPolicyOverrideId(response.data.id);
      setContextPolicyDraft((current) => contextPolicyDraftFromOverride(response.data, {
        ...current,
        reviewNote: '',
        noteText: ''
      }));
      setContextPolicyState({
        loading: false,
        success: action === 'approve' ? '上下文策略覆盖已审批' : '上下文策略覆盖已驳回',
        traceId: response.trace_id
      });
      void loadContextPolicyNotes(response.data.id, { silent: true });
      void refreshContextPolicy({ silent: true });
    } catch (error: unknown) {
      setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, action === 'approve' ? '上下文策略审批失败' : '上下文策略驳回失败') });
    }
  }

  async function selectContextPolicyOverride(override: TestDesignContextPolicyOverrideView) {
    setSelectedContextPolicyOverrideId(override.id);
    setContextPolicyDraft((current) => contextPolicyDraftFromOverride(override, current));
    await loadContextPolicyNotes(override.id);
  }

  function newContextPolicyOverrideDraft() {
    setSelectedContextPolicyOverrideId('');
    setContextPolicyNotes([]);
    setContextPolicyDraft((current) => ({
      ...initialTestDesignContextPolicyDraft,
      projectId: current.projectId || selectedTask?.projectId || taskFilters.projectId || filters.projectId || '',
      environmentKey: current.environmentKey
    }));
    setContextPolicyState({ loading: false });
  }

  async function loadContextPolicyNotes(overrideId: string, options?: { silent?: boolean }) {
    if (!overrideId || !canPolicyManage) {
      setContextPolicyNotes([]);
      return;
    }
    const silent = options?.silent === true;
    if (!silent) {
      setContextPolicyState({ loading: true });
    }
    try {
      const response = await fetchTestDesignContextPolicyNotes(overrideId);
      setContextPolicyNotes(response.data);
      if (!silent) {
        setContextPolicyState({ loading: false, success: `备注流转已加载：${response.data.length} 条`, traceId: response.trace_id });
      }
    } catch (error: unknown) {
      setContextPolicyNotes([]);
      if (!silent) {
        setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, '上下文策略备注加载失败') });
      }
    }
  }

  async function addContextPolicyNote() {
    if (!canPolicyManage) {
      setContextPolicyState({ loading: false, error: '缺少 testDesign:policy_manage 权限' });
      return;
    }
    if (!selectedContextPolicyOverrideId) {
      setContextPolicyState({ loading: false, error: '请选择上下文策略覆盖记录' });
      return;
    }
    const noteIssue = contextPolicyIssues.find((issue) => issue.field === 'noteText');
    if (noteIssue || !contextPolicyDraft.noteText.trim()) {
      setContextPolicyState({ loading: false, error: noteIssue?.message ?? '请输入流转备注' });
      return;
    }
    setContextPolicyState({ loading: true });
    try {
      const response = await addTestDesignContextPolicyNote(selectedContextPolicyOverrideId, {
        noteType: contextPolicyDraft.noteType,
        noteText: contextPolicyDraft.noteText
      });
      setContextPolicyNotes((current) => [...current, response.data]);
      setContextPolicyOverrides((current) => current.map((override) => override.id === selectedContextPolicyOverrideId
        ? {
          ...override,
          noteCount: (override.noteCount ?? 0) + 1,
          latestNotePreview: response.data.noteText
        }
        : override));
      setContextPolicyDraft((current) => ({ ...current, noteText: '' }));
      setContextPolicyState({ loading: false, success: '上下文策略备注已追加', traceId: response.trace_id });
      void refreshContextPolicy({ silent: true });
    } catch (error: unknown) {
      setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, '上下文策略备注追加失败') });
    }
  }

  async function requestReleaseReadinessApproval(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedTaskId) {
      setReleaseReadinessState({ loading: false, error: '请先选择任务' });
      return;
    }
    if (!canPublish) {
      setReleaseReadinessState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!releaseReadinessDraft.exceptionSummary.trim() || !releaseReadinessDraft.riskMitigation.trim()) {
      setReleaseReadinessState({ loading: false, error: '请输入例外摘要和风险缓释说明' });
      return;
    }

    setReleaseReadinessState({ loading: true });
    try {
      const payload = {
        exceptionReasonCode: releaseReadinessDraft.exceptionReasonCode,
        exceptionSummary: releaseReadinessDraft.exceptionSummary,
        riskMitigation: releaseReadinessDraft.riskMitigation,
        workOrderKey: releaseReadinessDraft.workOrderKey,
        workOrderTitle: releaseReadinessDraft.workOrderTitle,
        workOrderUrl: releaseReadinessDraft.workOrderUrl,
        requestNote: releaseReadinessDraft.requestNote
      };
      const response = selectedReleaseReadinessApproval?.status === 'PENDING'
        ? await updateTestDesignReleaseReadinessApproval(selectedReleaseReadinessApproval.id, payload)
        : await requestTestDesignReleaseReadinessApproval(selectedTaskId, payload);
      setReleaseReadinessApprovals((current) => [response.data, ...current.filter((item) => item.id !== response.data.id)]);
      setSelectedReleaseReadinessApprovalId(response.data.id);
      setReleaseReadinessState({
        loading: false,
        success: selectedReleaseReadinessApproval?.status === 'PENDING' ? '发布准出审批草稿已更新' : '发布准出例外已提交审批',
        traceId: response.trace_id
      });
      void refreshReleaseReadinessApprovals(selectedTaskId, { silent: true });
      void refreshReleaseReadinessNotes(response.data.id);
    } catch (error: unknown) {
      setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, '发布准出例外提交失败') });
    }
  }

  async function reviewReleaseReadinessApproval(approvalId: string, action: 'approve' | 'reject') {
    if (!canPublish) {
      setReleaseReadinessState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!releaseReadinessDraft.approvalReasonCode) {
      setReleaseReadinessState({ loading: false, error: '请选择审批原因编码' });
      return;
    }
    setReleaseReadinessState({ loading: true });
    try {
      const payload = {
        approvalReasonCode: releaseReadinessDraft.approvalReasonCode,
        reviewNote: releaseReadinessDraft.reviewNote,
        workOrderStatus: releaseReadinessDraft.workOrderStatus || undefined
      };
      const response = action === 'approve'
        ? await approveTestDesignReleaseReadinessApproval(approvalId, payload)
        : await rejectTestDesignReleaseReadinessApproval(approvalId, payload);
      setReleaseReadinessApprovals((current) => current.map((item) => item.id === response.data.id ? response.data : item));
      setSelectedReleaseReadinessApprovalId(response.data.id);
      setReleaseReadinessDraft((current) => ({
        ...current,
        reviewNote: '',
        noteText: ''
      }));
      setReleaseReadinessState({
        loading: false,
        success: action === 'approve' ? '发布准出例外已审批' : '发布准出例外已驳回',
        traceId: response.trace_id
      });
      void refreshReleaseReadinessNotes(response.data.id);
    } catch (error: unknown) {
      setReleaseReadinessState({
        loading: false,
        error: testDesignErrorMessage(error, action === 'approve' ? '发布准出审批失败' : '发布准出驳回失败')
      });
    }
  }

  function selectReleaseReadinessApproval(approval: TestDesignReleaseReadinessApprovalView) {
    setSelectedReleaseReadinessApprovalId(approval.id);
    setReleaseReadinessDraft((current) => ({
      ...current,
      exceptionReasonCode: releaseReadinessReasonCodeValue(approval.exceptionReasonCode, current.exceptionReasonCode),
      approvalReasonCode: releaseReadinessReasonCodeValue(approval.approvalReasonCode, current.approvalReasonCode),
      exceptionSummary: approval.exceptionSummary ?? '',
      riskMitigation: approval.riskMitigation ?? '',
      workOrderKey: approval.workOrderKey ?? '',
      workOrderTitle: approval.workOrderTitle ?? '',
      workOrderUrl: approval.workOrderUrl ?? '',
      workOrderStatus: approval.status === 'PENDING' ? '' : approval.workOrderStatus ?? '',
      requestNote: approval.requestNote ?? '',
      reviewNote: approval.reviewNote ?? '',
      noteText: ''
    }));
    void refreshReleaseReadinessNotes(approval.id);
  }

  async function addReleaseReadinessNote() {
    if (!selectedReleaseReadinessApprovalId) {
      setReleaseReadinessState({ loading: false, error: '请选择发布准出审批记录' });
      return;
    }
    if (!canPublish) {
      setReleaseReadinessState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!releaseReadinessDraft.noteText.trim()) {
      setReleaseReadinessState({ loading: false, error: '请输入流转备注' });
      return;
    }
    setReleaseReadinessState({ loading: true });
    try {
      const response = await addTestDesignReleaseReadinessNote(selectedReleaseReadinessApprovalId, {
        noteType: releaseReadinessDraft.noteType,
        noteText: releaseReadinessDraft.noteText
      });
      setReleaseReadinessNotes((current) => [...current, response.data]);
      setReleaseReadinessApprovals((current) => current.map((approval) => approval.id === selectedReleaseReadinessApprovalId
        ? {
          ...approval,
          noteCount: (approval.noteCount ?? 0) + 1,
          latestNotePreview: response.data.noteText
        }
        : approval));
      setReleaseReadinessDraft((current) => ({ ...current, noteText: '' }));
      setReleaseReadinessState({ loading: false, success: '发布准出备注已追加', traceId: response.trace_id });
    } catch (error: unknown) {
      setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, '发布准出备注追加失败') });
    }
  }

  async function requestReportArchiveApproval(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedReportArchiveId) {
      setReportArchiveState({ loading: false, error: '请先选择报告归档' });
      return;
    }
    if (!canExport) {
      setReportArchiveState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (!reportArchiveDraft.requestSummary.trim()) {
      setReportArchiveState({ loading: false, error: '请输入归档审批申请摘要' });
      return;
    }

    setReportArchiveState({ loading: true });
    try {
      const payload = {
        reasonCode: reportArchiveDraft.reasonCode,
        requestSummary: reportArchiveDraft.requestSummary,
        workOrderKey: reportArchiveDraft.workOrderKey,
        workOrderTitle: reportArchiveDraft.workOrderTitle,
        workOrderUrl: reportArchiveDraft.workOrderUrl,
        requestNote: reportArchiveDraft.requestNote
      };
      const response = reportArchiveDraft.approvalType === 'EXTERNAL_SHARE'
        ? await requestTestDesignReportArchiveExternalApproval(selectedReportArchiveId, payload)
        : await requestTestDesignReportArchiveApproval(selectedReportArchiveId, payload);
      setReportArchiveApprovals((current) => [response.data, ...current.filter((item) => item.id !== response.data.id)]);
      setSelectedReportArchiveApprovalId(response.data.id);
      setReportArchiveState({ loading: false, success: '归档审批已提交', traceId: response.trace_id });
      void refreshReportArchiveDetail(selectedReportArchiveId, { silent: true });
      void refreshReportArchiveNotes(response.data.id);
    } catch (error: unknown) {
      setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, '归档审批提交失败') });
    }
  }

  async function reviewReportArchiveApproval(approvalId: string, action: 'approve' | 'reject') {
    if (!canExport) {
      setReportArchiveState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (!reportArchiveDraft.approvalReasonCode) {
      setReportArchiveState({ loading: false, error: '请选择审批原因编码' });
      return;
    }
    setReportArchiveState({ loading: true });
    try {
      const payload = {
        approvalReasonCode: reportArchiveDraft.approvalReasonCode,
        reviewNote: reportArchiveDraft.reviewNote,
        workOrderStatus: reportArchiveDraft.workOrderStatus || undefined
      };
      const response = action === 'approve'
        ? await approveTestDesignReportArchiveApproval(approvalId, payload)
        : await rejectTestDesignReportArchiveApproval(approvalId, payload);
      setReportArchiveApprovals((current) => current.map((item) => item.id === response.data.id ? response.data : item));
      setSelectedReportArchiveApprovalId(response.data.id);
      setReportArchiveDraft((current) => ({ ...current, reviewNote: '', noteText: '' }));
      setReportArchiveState({
        loading: false,
        success: action === 'approve' ? '归档审批已通过' : '归档审批已驳回',
        traceId: response.trace_id
      });
      void refreshReportArchives(selectedTaskId, { silent: true });
      void refreshReportArchiveDetail(response.data.archiveId, { silent: true });
      void refreshReportArchiveNotes(response.data.id);
    } catch (error: unknown) {
      setReportArchiveState({
        loading: false,
        error: testDesignErrorMessage(error, action === 'approve' ? '归档审批失败' : '归档驳回失败')
      });
    }
  }

  function selectReportArchive(archive: TestDesignReportArchiveView) {
    setSelectedReportArchiveId(archive.id);
    setReportArchiveDraft((current) => ({
      ...current,
      approvalType: archive.status === 'ARCHIVED' ? 'EXTERNAL_SHARE' : 'ARCHIVE',
      reasonCode: archive.status === 'ARCHIVED' ? 'CUSTOMER_REQUEST' : 'RETENTION_POLICY',
      requestSummary: archive.status === 'ARCHIVED'
        ? 'Request controlled external sharing for archived WP5 task report.'
        : 'Request final archive approval for WP5 task report.',
      noteText: ''
    }));
    void refreshReportArchiveDetail(archive.id);
  }

  function selectReportArchiveApproval(approval: TestDesignReportArchiveApprovalView) {
    setSelectedReportArchiveApprovalId(approval.id);
    setReportArchiveDraft((current) => ({
      ...current,
      approvalType: approval.approvalType === 'EXTERNAL_SHARE' ? 'EXTERNAL_SHARE' : 'ARCHIVE',
      reasonCode: reportArchiveReasonCodeValue(approval.reasonCode, current.reasonCode),
      approvalReasonCode: reportArchiveReasonCodeValue(approval.approvalReasonCode, current.approvalReasonCode),
      requestSummary: approval.requestSummary ?? '',
      workOrderKey: approval.workOrderKey ?? '',
      workOrderTitle: approval.workOrderTitle ?? '',
      workOrderUrl: approval.workOrderUrl ?? '',
      workOrderStatus: approval.status === 'PENDING' ? '' : approval.workOrderStatus ?? '',
      requestNote: approval.requestNote ?? '',
      reviewNote: approval.reviewNote ?? '',
      noteText: ''
    }));
    void refreshReportArchiveNotes(approval.id);
  }

  async function addReportArchiveNote() {
    if (!selectedReportArchiveApprovalId) {
      setReportArchiveState({ loading: false, error: '请选择归档审批记录' });
      return;
    }
    if (!canExport) {
      setReportArchiveState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (!reportArchiveDraft.noteText.trim()) {
      setReportArchiveState({ loading: false, error: '请输入流转备注' });
      return;
    }
    setReportArchiveState({ loading: true });
    try {
      const response = await addTestDesignReportArchiveNote(selectedReportArchiveApprovalId, {
        noteType: reportArchiveDraft.noteType,
        noteText: reportArchiveDraft.noteText
      });
      setReportArchiveNotes((current) => [...current, response.data]);
      setReportArchiveApprovals((current) => current.map((approval) => approval.id === selectedReportArchiveApprovalId
        ? {
          ...approval,
          noteCount: (approval.noteCount ?? 0) + 1,
          latestNotePreview: response.data.noteText
        }
        : approval));
      setReportArchiveDraft((current) => ({ ...current, noteText: '' }));
      setReportArchiveState({ loading: false, success: '归档备注已追加', traceId: response.trace_id });
    } catch (error: unknown) {
      setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, '归档备注追加失败') });
    }
  }

  async function saveCandidate() {
    if (!selectedCandidate || !candidateDraft) {
      return;
    }
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }
    if (candidateSaveBlocked) {
      setMutationState({ loading: false, error: `候选质量门禁不通过：${candidateQualityIssues[0]?.message ?? '请检查字段提示'}` });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateTestDesignCandidate(selectedCandidate.id, {
        title: candidateDraft.title,
        description: candidateDraft.description,
        apiId: candidateDraft.apiId,
        coverageType: candidateDraft.coverageType,
        priority: candidateDraft.priority,
        preconditions: candidateDraft.preconditions,
        steps: stepsFromDraft(candidateDraft.steps),
        expectedResult: candidateDraft.expectedResult,
        tags: tagsFromText(candidateDraft.tags),
        version: selectedCandidate.version
      });
      updateCandidateInState(response.data);
      setMutationState({ loading: false, success: '候选用例已保存', traceId: response.trace_id });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
      void refreshTaskAuditSummary(selectedTaskId, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, '候选用例保存失败') });
    }
  }

  async function reviewCandidate(action: 'confirm' | 'reject' | 'ignore') {
    if (!selectedCandidate) {
      return;
    }
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const payload = { version: selectedCandidate.version, comment: reviewComment, reason: reviewComment };
      const response = action === 'confirm'
        ? await confirmTestDesignCandidate(selectedCandidate.id, payload)
        : action === 'reject'
          ? await rejectTestDesignCandidate(selectedCandidate.id, payload)
          : await ignoreTestDesignCandidate(selectedCandidate.id, payload);
      updateCandidateInState(response.data);
      setMutationState({ loading: false, success: reviewSuccessText(action), traceId: response.trace_id });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
      void refreshTaskAuditSummary(selectedTaskId, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, '候选用例状态更新失败') });
    }
  }

  function requestBatchReviewCandidates(action: TestDesignCandidateBatchActionType) {
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }
    if (!selectedReviewCandidates.length) {
      setMutationState({ loading: false, error: '请先选择可评审候选' });
      return;
    }
    if ((action === 'REJECT' || action === 'IGNORE') && !reviewComment.trim()) {
      setMutationState({ loading: false, error: '批量驳回或忽略需要填写评审意见' });
      return;
    }

    setPendingConfirmation({
      kind: 'batchReview',
      action,
      summary: buildTestDesignBatchReviewConfirmation(action, selectedReviewCandidates, reviewComment)
    });
  }

  async function executeBatchReviewCandidates(action: TestDesignCandidateBatchActionType) {
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }
    if (!selectedReviewCandidates.length) {
      setMutationState({ loading: false, error: '请先选择可评审候选' });
      return;
    }
    if ((action === 'REJECT' || action === 'IGNORE') && !reviewComment.trim()) {
      setMutationState({ loading: false, error: '批量驳回或忽略需要填写评审意见' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await batchActionTestDesignCandidates({
        action,
        candidates: selectedReviewCandidates.map((candidate) => ({ id: candidate.id, version: candidate.version })),
        reason: action === 'REJECT' || action === 'IGNORE' ? reviewComment : undefined,
        comment: reviewComment
      });
      const nextCandidates = mergeBatchCandidates(candidates, response.data);
      const failedIds = new Set(response.data.items.filter((item) => item.result !== 'SUCCEEDED').map((item) => item.candidateId));
      setCandidates(nextCandidates);
      setSelectedCandidateCache((current) => mergeCandidateCache(current, nextCandidates));
      setSelectedCandidateIds((current) => current.filter((id) => failedIds.has(id)));
      setBatchActionResult(response.data);
      setMutationState({
        loading: false,
        success: `批量${testDesignBatchActionLabel(action)}完成：成功 ${response.data.succeededCount}，失败 ${response.data.failedCount}`,
        traceId: response.trace_id
      });
      void refreshCandidatePage(selectedTaskId, { silent: true });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
      void refreshTaskAuditSummary(selectedTaskId, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, `批量${testDesignBatchActionLabel(action)}失败`) });
    }
  }

  function requestBatchEditCandidates() {
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }
    if (!selectedBatchEditableCandidates.length) {
      setMutationState({ loading: false, error: '请先选择可编辑候选' });
      return;
    }
    if (!batchEditHasChanges) {
      setMutationState({ loading: false, error: '请至少选择一个要批量修改的字段' });
      return;
    }
    if (batchEditIssues.length) {
      setMutationState({ loading: false, error: `批量字段编辑校验不通过：${batchEditIssues[0].message}` });
      return;
    }

    setPendingConfirmation({
      kind: 'batchEdit',
      summary: buildTestDesignBatchEditConfirmation(selectedBatchEditableCandidates, batchEditFieldLabels)
    });
  }

  async function executeBatchEditCandidates() {
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }
    if (batchEditBlocked) {
      setMutationState({
        loading: false,
        error: batchEditIssues[0]?.message ?? '请先选择可编辑候选并填写批量字段'
      });
      return;
    }

    setMutationState({ loading: true });
    setBatchEditResult(null);
    const items = await Promise.all(selectedBatchEditableCandidates.map(async (candidate) => {
      try {
        const response = await updateTestDesignCandidate(
          candidate.id,
          buildTestDesignBatchEditPayload(candidate, batchEditDraft)
        );
        return {
          candidateId: candidate.id,
          result: 'SUCCEEDED' as const,
          candidate: response.data,
          traceId: response.trace_id
        };
      } catch (error: unknown) {
        return {
          candidateId: candidate.id,
          result: 'FAILED' as const,
          errorMessage: testDesignErrorMessage(error, '候选批量字段编辑失败')
        };
      }
    }));

    const succeededCandidates = items
      .map((item) => item.candidate)
      .filter((candidate): candidate is TestDesignCandidateView => Boolean(candidate));
    const succeededIds = new Set(succeededCandidates.map((candidate) => candidate.id));
    const resultItems = items.map((item) => ({
      candidateId: item.candidateId,
      result: item.result,
      candidate: item.candidate,
      errorMessage: item.errorMessage
    }));
    const result: BatchEditResult = {
      total: items.length,
      succeededCount: succeededCandidates.length,
      failedCount: items.length - succeededCandidates.length,
      items: resultItems
    };
    setCandidates((current) => mergeUpdatedCandidates(current, succeededCandidates));
    setSelectedCandidateCache((current) => mergeCandidateCache(current, succeededCandidates));
    setSelectedCandidateIds((current) => current.filter((id) => !succeededIds.has(id)));
    setBatchEditResult(result);
    if (!result.failedCount) {
      setBatchEditDraft(initialTestDesignBatchEditDraft);
    }
    setMutationState({
      loading: false,
      success: `批量字段编辑完成：成功 ${result.succeededCount}，失败 ${result.failedCount}`,
      traceId: items.find((item) => item.result === 'SUCCEEDED')?.traceId
    });
    void refreshCandidatePage(selectedTaskId, { silent: true });
    void refreshReviewRecords(selectedTaskId, { silent: true });
    void refreshTaskQualitySummary(selectedTaskId, { silent: true });
    void refreshTaskAuditSummary(selectedTaskId, { silent: true });
    void refreshPromptTrend({ silent: true });
  }

  function requestPublishTask(dryRun: boolean) {
    if (!selectedTaskId) {
      return;
    }
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!canPublishCurrentScope) {
      setPublishState({ loading: false, error: '当前没有可发布候选' });
      return;
    }

    setPendingConfirmation({
      kind: 'publish',
      dryRun,
      summary: buildTestDesignPublishConfirmation(
        dryRun,
        publishPreviewCandidates,
        estimatedPublishableCandidateCount,
        selectedCandidateIds.length
      )
    });
  }

  async function executePublishTask(dryRun: boolean) {
    if (!selectedTaskId) {
      return;
    }
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }

    setPublishState({ loading: true });
    try {
      const candidateIds = selectedCandidateIds.length
        ? selectedPublishableCandidates.map((candidate) => candidate.id)
        : undefined;
      const response = dryRun
        ? await publishTestDesignDryRun(selectedTaskId, { candidateIds })
        : await publishTestDesignTask(selectedTaskId, { candidateIds });
      setPublishResult(response.data);
      const queued = !dryRun && response.data.records.some((record) => record.result === 'QUEUED' || record.candidateStatus === 'PUBLISH_QUEUED');
      setSelectedConflictCaseIds({});
      setConflictCaseResults([]);
      setPublishState({
        loading: false,
        success: dryRun ? '预发布检查已完成' : queued ? '发布请求已排队，后台写入资产库' : '已发布到资产库测试用例',
        traceId: response.trace_id
      });
      if (!dryRun) {
        await refreshCandidatePage(selectedTaskId);
        void refreshTaskQualitySummary(selectedTaskId, { silent: true });
        void refreshTaskAuditSummary(selectedTaskId, { silent: true });
        void refreshPromptTrend({ silent: true });
        void refreshConflictOperations(0, { silent: true });
      }
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, dryRun ? '预发布检查失败' : '发布失败') });
    }
  }

  async function searchConflictCases() {
    if (!canRead) {
      setPublishState({ loading: false, error: '缺少 testDesign:read 权限' });
      return;
    }
    if (!conflictCaseSearchProjectId) {
      setPublishState({ loading: false, error: '缺少项目 ID，无法搜索既有用例' });
      return;
    }

    setPublishState({ loading: true });
    try {
      const response = await fetchAssetTestCases({
        projectId: conflictCaseSearchProjectId,
        keyword: conflictCaseKeyword,
        size: 8
      });
      setConflictCaseResults(response.data.items);
      setPublishState({
        loading: false,
        success: `已加载既有用例 ${response.data.items.length} / ${response.data.total}`,
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, '既有用例搜索失败') });
    }
  }

  function requestResolveConflict(record: TestDesignPublishRecordView) {
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!record.candidateId) {
      setPublishState({ loading: false, error: '冲突记录缺少候选 ID' });
      return;
    }
    const targetCaseId = conflictResolutionTargetCaseId(record, selectedConflictCaseIds);
    if (!targetCaseId) {
      setPublishState({ loading: false, error: '请选择目标用例后再处理冲突' });
      return;
    }
    const candidate = conflictResolutionCandidate(record, conflictCandidateById);
    if (!candidate) {
      setPublishState({ loading: false, error: '冲突记录缺少候选版本，请重新预发布后处理' });
      return;
    }

    const resolutionRecord = { ...record, assetCaseId: targetCaseId };
    setPendingConfirmation({
      kind: 'resolveConflict',
      candidate,
      record: resolutionRecord,
      summary: buildTestDesignConflictResolutionConfirmation(
        candidate,
        resolutionRecord,
        conflictResolutionDraft.reason,
        conflictResolutionDraft.comment
      )
    });
  }

  function requestBatchResolveConflicts() {
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!batchResolvableConflictItems.length) {
      setPublishState({ loading: false, error: '请先为至少一条冲突选择目标用例' });
      return;
    }

    setPendingConfirmation({
      kind: 'batchResolveConflict',
      items: batchResolvableConflictItems,
      summary: buildTestDesignBatchConflictResolutionConfirmation(
        batchResolvableConflictItems,
        conflictResolutionDraft.reason,
        conflictResolutionDraft.comment
      )
    });
  }

  function requestBatchResolveConflictOperations() {
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!batchResolvableConflictOperationItems.length) {
      setPublishState({ loading: false, error: '请先为至少一条运营台冲突选择目标用例' });
      return;
    }

    setPendingConfirmation({
      kind: 'batchResolveConflict',
      items: batchResolvableConflictOperationItems,
      summary: buildTestDesignBatchConflictResolutionConfirmation(
        batchResolvableConflictOperationItems,
        conflictResolutionDraft.reason,
        conflictResolutionDraft.comment
      )
    });
  }

  async function executeResolveConflict(candidate: ConflictResolutionCandidate, record: TestDesignPublishRecordView) {
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!record.assetCaseId) {
      setPublishState({ loading: false, error: '冲突记录缺少目标用例 ID' });
      return;
    }

    setPublishState({ loading: true });
    try {
      const response = await resolveTestDesignConflict(candidate.id, {
        version: candidate.version,
        caseId: record.assetCaseId,
        reason: conflictResolutionDraft.reason,
        comment: conflictResolutionDraft.comment
      });
      setPublishResult((current) => current
        ? { ...current, records: applyConflictResolutionRecord(current.records, response.data) }
        : current);
      if (selectedTaskId) {
        await refreshCandidatePage(selectedTaskId, { silent: true });
        void refreshTaskQualitySummary(selectedTaskId, { silent: true });
        void refreshReviewRecords(selectedTaskId, { silent: true });
        void refreshTaskAuditSummary(selectedTaskId, { silent: true });
        void refreshPromptTrend({ silent: true });
      }
      void refreshConflictOperations(conflictOperationPageIndex, { silent: true });
      if (response.data.result === 'SUCCEEDED') {
        setConflictResolutionDraft(initialConflictResolutionDraft);
        setSelectedConflictCaseIds((current) => {
          const next = { ...current };
          delete next[candidate.id];
          return next;
        });
        setPublishState({ loading: false, success: '冲突已链接既有用例', traceId: response.trace_id });
        return;
      }
      setPublishState({
        loading: false,
        error: response.data.errorMessage ?? '冲突链接失败，请检查目标用例和需求追踪关系'
      });
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, '冲突链接失败') });
    }
  }

  async function executeBatchResolveConflicts(items: ConflictResolutionItem[]) {
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }
    if (!items.length) {
      setPublishState({ loading: false, error: '请先为至少一条冲突选择目标用例' });
      return;
    }

    setPublishState({ loading: true });
    const requestItems = items.flatMap((item) => item.record.assetCaseId
      ? [{
        candidateId: item.candidate.id,
        version: item.candidate.version,
        caseId: item.record.assetCaseId
      }]
      : []);
    if (requestItems.length !== items.length) {
      setPublishState({ loading: false, error: '冲突记录缺少目标用例 ID' });
      return;
    }

    try {
      const response = await batchResolveTestDesignConflicts({
        items: requestItems,
        reason: conflictResolutionDraft.reason,
        comment: conflictResolutionDraft.comment
      });
      const results = response.data.items.map((item) => ({
        candidateId: item.candidateId,
        result: item.result === 'SUCCEEDED' && item.record?.result === 'SUCCEEDED' ? 'SUCCEEDED' as const : 'FAILED' as const,
        record: item.record,
        errorMessage: item.errorMessage ?? item.record?.errorMessage ?? (item.result === 'SUCCEEDED' ? undefined : '冲突链接失败')
      }));

      const succeededIds = new Set(results.filter((item) => item.result === 'SUCCEEDED').map((item) => item.candidateId));
      const failedItems = results.filter((item) => item.result !== 'SUCCEEDED');
      setPublishResult((current) => current
        ? {
          ...current,
          records: results.reduce(
            (records, item) => item.record ? applyConflictResolutionRecord(records, item.record) : records,
            current.records
          )
        }
        : current);
      setSelectedConflictCaseIds((current) => {
        const next = { ...current };
        succeededIds.forEach((candidateId) => delete next[candidateId]);
        return next;
      });
      if (selectedTaskId) {
        await refreshCandidatePage(selectedTaskId, { silent: true });
        void refreshTaskQualitySummary(selectedTaskId, { silent: true });
        void refreshReviewRecords(selectedTaskId, { silent: true });
        void refreshTaskAuditSummary(selectedTaskId, { silent: true });
        void refreshPromptTrend({ silent: true });
      }
      void refreshConflictOperations(conflictOperationPageIndex, { silent: true });

      if (!failedItems.length) {
        setConflictResolutionDraft(initialConflictResolutionDraft);
        setPublishState({
          loading: false,
          success: `批量冲突处理完成：成功 ${succeededIds.size} / ${items.length}`,
          traceId: response.trace_id
        });
        return;
      }
      setPublishState({
        loading: false,
        error: `批量冲突处理完成：成功 ${succeededIds.size}，失败 ${failedItems.length}；${failedItems[0]?.errorMessage ?? '请检查失败项'}`
      });
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, '批量冲突链接失败') });
    }
  }

  async function confirmPendingAction() {
    const confirmation = pendingConfirmation;
    if (!confirmation) {
      return;
    }
    setPendingConfirmation(null);
    if (confirmation.kind === 'batchReview') {
      await executeBatchReviewCandidates(confirmation.action);
      return;
    }
    if (confirmation.kind === 'batchEdit') {
      await executeBatchEditCandidates();
      return;
    }
    if (confirmation.kind === 'batchResolveConflict') {
      await executeBatchResolveConflicts(confirmation.items);
      return;
    }
    if (confirmation.kind === 'resolveConflict') {
      await executeResolveConflict(confirmation.candidate, confirmation.record);
      return;
    }
    await executePublishTask(confirmation.dryRun);
  }

  async function exportCandidateReview(scope: 'page' | 'selected') {
    if (!canExport) {
      setTaskState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (scope === 'page') {
      if (!selectedTaskId) {
        setTaskState({ loading: false, error: '请先选择任务后再导出' });
        return;
      }
      if (!candidatePage.total) {
        setTaskState({ loading: false, error: '当前筛选无可导出数据' });
        return;
      }
      setTaskState({ loading: true });
      try {
        const response = await exportTestDesignCandidatesCsv({
          taskId: selectedTaskId,
          status: candidateFilters.status,
          coverageType: candidateFilters.coverageType,
          keyword: candidateFilters.keyword
        });
        downloadText(
          response.text,
          response.filename ?? buildTestDesignExportFilename('candidate-filters', selectedTaskId, new Date().toISOString()),
          response.contentType || TEST_DESIGN_EXPORT_CONTENT_TYPE
        );
        setTaskState({ loading: false, success: '已导出当前筛选候选摘要', traceId: response.traceId });
      } catch (error: unknown) {
        setTaskState({ loading: false, error: testDesignErrorMessage(error, '候选筛选导出失败') });
      }
      return;
    }

    const exportCandidates = selectedCandidates;
    if (!exportCandidates.length) {
      setTaskState({ loading: false, error: '请先选择候选后再导出' });
      return;
    }

    const generatedAt = new Date().toISOString();
    const scopeLabel = `已选候选 ${exportCandidates.length} 个`;
    const csv = buildTestDesignCandidateReviewCsv({
      task: selectedTask,
      candidates: exportCandidates,
      scopeLabel,
      generatedAt
    });
    downloadText(
      csv,
      buildTestDesignExportFilename('selected-candidates', selectedTaskId, generatedAt),
      TEST_DESIGN_EXPORT_CONTENT_TYPE
    );
    setTaskState({ loading: false, success: '已导出已选候选摘要' });
  }

  function exportPublishResult() {
    if (!canExport) {
      setPublishState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (!publishResult) {
      setPublishState({ loading: false, error: '暂无发布结果可导出' });
      return;
    }

    const generatedAt = new Date().toISOString();
    const csv = buildTestDesignPublishResultCsv({ task: selectedTask, publishResult, generatedAt });
    downloadText(
      csv,
      buildTestDesignExportFilename(publishResult.dryRun ? 'publish-dry-run' : 'publish-result', publishResult.taskId, generatedAt),
      TEST_DESIGN_EXPORT_CONTENT_TYPE
    );
    setPublishState({ loading: false, success: '已导出发布结果摘要' });
  }

  async function exportTaskReport() {
    if (!canExport) {
      setTaskState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (!selectedTask) {
      setTaskState({ loading: false, error: '请先选择任务后再导出报告摘要' });
      return;
    }

    setTaskState({ loading: true });
    try {
      const response = await exportTestDesignTaskReportCsv(selectedTask.id);
      downloadText(
        response.text,
        response.filename ?? buildTestDesignExportFilename('task-report', selectedTask.id, new Date().toISOString()),
        response.contentType || TEST_DESIGN_EXPORT_CONTENT_TYPE
      );
      void refreshReportArchives(selectedTask.id, { silent: true });
      setTaskState({ loading: false, success: '已导出任务全量报告', traceId: response.traceId });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '任务报告导出失败') });
    }
  }

  async function exportReviewRecords() {
    if (!canExport) {
      setReviewRecordState({ loading: false, error: '缺少 testDesign:export 权限' });
      return;
    }
    if (!selectedTaskId) {
      setReviewRecordState({ loading: false, error: '请先选择任务后再导出评审历史' });
      return;
    }
    if (!reviewRecordPageTotal) {
      setReviewRecordState({ loading: false, error: '暂无评审历史可导出' });
      return;
    }

    setReviewRecordState({ loading: true });
    try {
      const response = await exportTestDesignReviewRecordsCsv(selectedTaskId);
      downloadText(
        response.text,
        response.filename ?? buildTestDesignExportFilename('review-records', selectedTaskId, new Date().toISOString()),
        response.contentType || TEST_DESIGN_EXPORT_CONTENT_TYPE
      );
      setReviewRecordState({ loading: false, success: '已导出评审历史', traceId: response.traceId });
    } catch (error: unknown) {
      setReviewRecordState({ loading: false, error: testDesignErrorMessage(error, '评审历史导出失败') });
    }
  }

  function updateCandidateInState(nextCandidate: TestDesignCandidateView) {
    setCandidates((current) => current.map((candidate) => (candidate.id === nextCandidate.id ? nextCandidate : candidate)));
    setSelectedCandidateCache((current) => mergeCandidateCache(current, [nextCandidate]));
    setSelectedCandidateId(nextCandidate.id);
  }

  return (
    <>
      {pendingConfirmation && (
        <ConfirmationDialog
          summary={pendingConfirmation.summary}
          onCancel={() => setPendingConfirmation(null)}
          onConfirm={() => void confirmPendingAction()}
        />
      )}
      <div className="module-layout">
      <div className="main-stack">
        <div className="metrics-grid">
          <Metric icon={<Sparkles size={20} />} label="服务状态" value={health?.status ?? '-'} desc={selectedTask ? generationSourceText(selectedTaskSource) : health?.generationMode ?? '未加载'} />
          <Metric icon={<FileText size={20} />} label="候选用例" value={String(candidates.length)} desc={`确认 ${statusCounts.CONFIRMED ?? 0} · 待重试 ${statusCounts.FAILED ?? 0}`} />
          <Metric icon={<ClipboardCheck size={20} />} label="已发布" value={String(selectedTask?.publishedCount ?? 0)} desc={selectedTask?.status ?? '-'} />
        </div>

        <QualitySummaryPanel
          scopeLabel={qualitySummaryScope}
          selectedTaskId={selectedTaskId}
          summary={qualitySummary}
        />

        <PromptTrendPanel
          state={promptTrendState}
          summary={promptTrendSummary}
          onRefresh={() => void refreshPromptTrend()}
        />

        <EvaluationCorpusOperationsPanel
          state={evaluationCorpusState}
          canPolicyManage={canPolicyManage}
          samples={evaluationSamples}
          sampleSummary={evaluationSampleSummary}
          evaluationSummary={evaluationCorpusSummary}
          sampleTotal={evaluationSamplePageTotal}
          selectedSampleId={selectedEvaluationSampleId}
          sampleDraft={evaluationSampleDraft}
          calibrationDraft={calibrationRunDraft}
          calibrationRuns={calibrationRuns}
          calibrationSummary={calibrationSummary}
          filters={evaluationSampleFilters}
          selectedCandidateId={selectedCandidateId}
          onRefresh={() => void refreshEvaluationCorpusOperations()}
          onSelectSample={setSelectedEvaluationSampleId}
          onNewSample={() => setSelectedEvaluationSampleId('')}
          onSampleDraftChange={setEvaluationSampleDraft}
          onCalibrationDraftChange={setCalibrationRunDraft}
          onFiltersChange={setEvaluationSampleFilters}
          onSaveSample={saveEvaluationSample}
          onTransitionSample={(status) => void transitionEvaluationSample(status)}
          onExtractFromCandidate={() => void extractEvaluationSampleFromCandidate()}
          onRunCalibration={() => void runCalibration()}
        />

        <CrossWpOperationsPanel
          state={crossWpOperationsState}
          canPolicyManage={canPolicyManage}
          dashboard={crossWpOperationsDashboard}
          auditReportTemplate={auditReportTemplate}
          modelObservationDrilldown={modelObservationDrilldown}
          crossWpDetailAuditReport={crossWpDetailAuditReport}
          filters={crossWpOperationsEffectiveFilters}
          requeueDraft={{
            ...auditOutboxRequeueDraft,
            projectId: auditOutboxRequeueDraft.projectId || crossWpOperationsProjectId
          }}
          requeueResult={auditOutboxRequeueResult}
          queueAlertSubscriptions={queueAlertSubscriptions}
          queueAlertSubscriptionDraft={{
            ...queueAlertSubscriptionDraft,
            projectId: queueAlertSubscriptionDraft.projectId || crossWpOperationsProjectId,
            promptKey: queueAlertSubscriptionDraft.promptKey || crossWpOperationsPromptKey
          }}
          queueAlertSubscriptionResult={queueAlertSubscriptionResult}
          queuedEventReplayDraft={{
            ...queuedEventReplayDraft,
            projectId: queuedEventReplayDraft.projectId || crossWpOperationsProjectId,
            promptKey: queuedEventReplayDraft.promptKey || crossWpOperationsPromptKey
          }}
          queuedEventReplayResult={queuedEventReplayResult}
          publishCompensationRunDraft={{
            ...publishCompensationRunDraft,
            projectId: publishCompensationRunDraft.projectId || crossWpOperationsProjectId,
            promptKey: publishCompensationRunDraft.promptKey || crossWpOperationsPromptKey
          }}
          publishCompensationRunResult={publishCompensationRunResult}
          onFiltersChange={setCrossWpOperationsFilters}
          onRequeueDraftChange={setAuditOutboxRequeueDraft}
          onQueueAlertSubscriptionDraftChange={setQueueAlertSubscriptionDraft}
          onQueuedEventReplayDraftChange={setQueuedEventReplayDraft}
          onPublishCompensationRunDraftChange={setPublishCompensationRunDraft}
          onRefresh={() => void refreshCrossWpOperations()}
          onRequeue={(event) => void requeueAuditOutbox(event)}
          onQueueAlertSubscriptionSubmit={(event) => void saveQueueAlertSubscription(event)}
          onQueuedEventReplaySubmit={(event) => void replayQueuedEvents(event)}
          onPublishCompensationRunSubmit={(event) => void runPublishCompensation(event)}
        />

        <AuditSummaryPanel
          state={taskAuditState}
          summary={auditSummary}
          selectedTaskId={selectedTaskId}
          onRefresh={() => void refreshTaskAuditSummary(selectedTaskId)}
        />

        <section className="panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">需求选择</h2>
              <p className="panel-desc">从 WP3 已入库需求中选择生成范围。</p>
            </div>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={disabled || loadState.loading} onClick={() => void refreshAll()}>
                <RefreshCw size={15} />
                刷新
              </button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={disabled || loadState.loading} onClick={() => setSelectedRequirementIds(filteredRequirements.map((item) => item.id).filter(Boolean))}>
                全选
              </button>
            </div>
          </div>
          <div className="panel-body">
            <div className="asset-filter-bar">
              <label className="field">
                <span className="field-label">项目 ID</span>
                <input value={filters.projectId} onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={disabled} />
              </label>
              <label className="field">
                <span className="field-label">状态</span>
                <select value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))} disabled={disabled}>
                  <option value="">全部</option>
                  <option value="APPROVED">APPROVED</option>
                  <option value="REVIEWING">REVIEWING</option>
                  <option value="DRAFT">DRAFT</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input value={filters.keyword} onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="标题 / 标签" disabled={disabled} />
              </label>
              <div className="filter-actions">
                <button className="btn btn-secondary btn-sm" type="button" disabled={disabled} onClick={() => setFilters(initialFilters)}>
                  <Search size={15} />
                  重置
                </button>
              </div>
            </div>

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 48 }}></th>
                    <th>需求</th>
                    <th>优先级</th>
                    <th>来源</th>
                    <th>标签</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRequirements.length ? (
                    filteredRequirements.map((requirement) => (
                      <tr className={selectedRequirementIds.includes(requirement.id) ? 'selected-row' : ''} key={requirement.id}>
                        <td>
                          <input
                            aria-label={`选择需求 ${requirement.title}`}
                            type="checkbox"
                            checked={selectedRequirementIds.includes(requirement.id)}
                            onChange={() => toggleRequirement(requirement.id)}
                            disabled={disabled || !requirement.id}
                          />
                        </td>
                        <td>
                          <strong>{requirement.title}</strong>
                          <div className="field-hint">{requirement.id}</div>
                        </td>
                        <td><span className="badge badge-neutral">{requirement.priority}</span></td>
                        <td>{requirement.sourceRef ?? requirement.source}</td>
                        <td>{requirement.tags.join(', ') || '-'}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td className="table-empty" colSpan={5}>{emptyRequirementText(props.signedIn, canRead, loadState.loading)}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <StateLine state={loadState} />
          </div>
        </section>

        <section className="panel test-design-context-policy-panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">上下文策略</h2>
              <p className="panel-desc">{contextPolicySummary.scopeLabel}</p>
            </div>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={disabled || contextPolicyState.loading} onClick={() => void refreshContextPolicy()}>
                <RefreshCw size={15} />
                查询
              </button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={!canPolicyManage || contextPolicyState.loading} onClick={newContextPolicyOverrideDraft}>
                <Plus size={15} />
                新建
              </button>
            </div>
          </div>
          <div className="panel-body compact main-stack">
            <form className="test-design-context-policy-form" onSubmit={requestContextPolicyOverride}>
              <label className="field">
                <span className="field-label">项目 ID</span>
                <input
                  value={contextPolicyDraft.projectId}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, projectId: event.target.value }))}
                  placeholder="project UUID"
                  disabled={!canRead || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">环境键</span>
                <input
                  value={contextPolicyDraft.environmentKey}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, environmentKey: event.target.value }))}
                  placeholder="qa"
                  disabled={!canRead || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">覆盖范围</span>
                <select
                  value={contextPolicyDraft.scopeType}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, scopeType: event.target.value === 'ENVIRONMENT' ? 'ENVIRONMENT' : 'PROJECT' }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                >
                  <option value="PROJECT">PROJECT</option>
                  <option value="ENVIRONMENT">ENVIRONMENT</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">变更原因</span>
                <select
                  value={contextPolicyDraft.changeReasonCode}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, changeReasonCode: event.target.value as TestDesignContextPolicyDraft['changeReasonCode'] }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                >
                  {TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.map((code) => (
                    <option key={code} value={code}>{code}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="field-label">关联资产</span>
                <input
                  value={contextPolicyDraft.linkedAssetsPerRequirement}
                  type="number"
                  min="1"
                  max="50"
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, linkedAssetsPerRequirement: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">显式资产</span>
                <input
                  value={contextPolicyDraft.explicitAssetsPerType}
                  type="number"
                  min="1"
                  max="50"
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, explicitAssetsPerType: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">历史用例</span>
                <input
                  value={contextPolicyDraft.existingCasesPerRequirement}
                  type="number"
                  min="1"
                  max="50"
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, existingCasesPerRequirement: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">需求摘要</span>
                <input
                  value={contextPolicyDraft.requirementDescriptionChars}
                  type="number"
                  min="1"
                  max="2000"
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, requirementDescriptionChars: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">验收摘要</span>
                <input
                  value={contextPolicyDraft.acceptanceCriteriaChars}
                  type="number"
                  min="1"
                  max="2000"
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, acceptanceCriteriaChars: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">资产摘要</span>
                <input
                  value={contextPolicyDraft.assetSchemaChars}
                  type="number"
                  min="1"
                  max="2000"
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, assetSchemaChars: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">工单编号</span>
                <input
                  value={contextPolicyDraft.workOrderKey}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, workOrderKey: event.target.value }))}
                  placeholder="WP5-CTX-..."
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">工单标题</span>
                <input
                  value={contextPolicyDraft.workOrderTitle}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, workOrderTitle: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field test-design-context-policy-wide">
                <span className="field-label">工单 URL</span>
                <input
                  value={contextPolicyDraft.workOrderUrl}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, workOrderUrl: event.target.value }))}
                  placeholder="https://..."
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field test-design-context-policy-wide">
                <span className="field-label">策略正文</span>
                <textarea
                  value={contextPolicyDraft.policyBody}
                  maxLength={4000}
                  rows={4}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, policyBody: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field test-design-context-policy-wide">
                <span className="field-label">策略 diff</span>
                <textarea
                  value={contextPolicyDraft.policyDiffSummary}
                  maxLength={1000}
                  rows={3}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, policyDiffSummary: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <label className="field test-design-context-policy-wide">
                <span className="field-label">申请备注</span>
                <textarea
                  value={contextPolicyDraft.requestNote}
                  maxLength={1000}
                  rows={3}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, requestNote: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
              <button className="btn btn-primary btn-sm test-design-context-policy-submit" type="submit" disabled={!canPolicyManage || contextPolicyState.loading || contextPolicySubmitBlocked}>
                <Save size={15} />
                {selectedPendingContextPolicyOverride ? '更新草稿' : '提交覆盖'}
              </button>
            </form>
            <div className="test-design-context-policy-summary">
              <Detail label="生效限制" value={contextPolicySummary.limitSummary} />
              <Detail label="状态分布" value={contextPolicySummary.statusSummary} />
              <Detail label="导出红线" value={contextPolicySummary.redLineSummary} />
            </div>
            <div className="test-design-context-policy-review-grid">
              <label className="field">
                <span className="field-label">审批原因</span>
                <select
                  value={contextPolicyDraft.approvalReasonCode}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, approvalReasonCode: event.target.value as TestDesignContextPolicyDraft['approvalReasonCode'] }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                >
                  {TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.map((code) => (
                    <option key={code} value={code}>{code}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="field-label">工单状态</span>
                <select
                  value={contextPolicyDraft.workOrderStatus}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, workOrderStatus: event.target.value as TestDesignContextPolicyDraft['workOrderStatus'] }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                >
                  <option value="">跟随审批</option>
                  {TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES.map((status) => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>
              </label>
              <label className="field test-design-context-policy-wide">
                <span className="field-label">审批备注</span>
                <textarea
                  value={contextPolicyDraft.reviewNote}
                  maxLength={1000}
                  rows={3}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, reviewNote: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading}
                />
              </label>
            </div>
            <div className="test-design-context-policy-note-form">
              <label className="field">
                <span className="field-label">备注类型</span>
                <select
                  value={contextPolicyDraft.noteType}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, noteType: event.target.value === 'WORK_ORDER' ? 'WORK_ORDER' : 'COMMENT' }))}
                  disabled={!canPolicyManage || contextPolicyState.loading || !selectedContextPolicyOverrideId}
                >
                  <option value="COMMENT">COMMENT</option>
                  <option value="WORK_ORDER">WORK_ORDER</option>
                </select>
              </label>
              <label className="field test-design-context-policy-wide">
                <span className="field-label">流转备注</span>
                <textarea
                  value={contextPolicyDraft.noteText}
                  maxLength={1000}
                  rows={3}
                  onChange={(event) => setContextPolicyDraft((current) => ({ ...current, noteText: event.target.value }))}
                  disabled={!canPolicyManage || contextPolicyState.loading || !selectedContextPolicyOverrideId}
                />
              </label>
              <button
                className="btn btn-secondary btn-sm test-design-context-policy-submit"
                type="button"
                disabled={!canPolicyManage || contextPolicyState.loading || !selectedContextPolicyOverrideId || !contextPolicyDraft.noteText.trim()}
                onClick={() => void addContextPolicyNote()}
              >
                <Plus size={15} />
                追加备注
              </button>
            </div>
            <div className="test-design-context-policy-overrides">
              {contextPolicyOverrides.length ? contextPolicyOverrides.slice(0, 6).map((override) => (
                <div className={`test-design-context-policy-override${selectedContextPolicyOverrideId === override.id ? ' selected' : ''}`} key={override.id}>
                  <div>
                    <strong>{override.scopeType}{override.environmentKey ? ` · ${override.environmentKey}` : ''}</strong>
                    <em>{contextPolicyOverrideLimitText(override.overrideLimits)}</em>
                    <small>{override.workOrderKey ?? '-'} · {override.workOrderStatus ?? '-'}</small>
                    <small>正文 v{override.policyBodyVersion ?? '-'} · {contextPolicyDigestText(override.policyBodyDigest)} · 备注 {override.noteCount ?? 0}</small>
                    <small>{override.requestedBy ?? '-'} · {override.createdAt ?? '-'}</small>
                    {override.latestNotePreview ? <small>最新备注：{override.latestNotePreview}</small> : null}
                  </div>
                  <div className="test-design-context-policy-override-actions">
                    <span className={`badge badge-${contextPolicyStatusTone(override.status)}`}>{override.status}</span>
                    <button
                      className="btn btn-secondary btn-xs"
                      type="button"
                      disabled={!canPolicyManage || contextPolicyState.loading}
                      onClick={() => void selectContextPolicyOverride(override)}
                    >
                      <FileText size={14} />
                      {override.status === 'PENDING' ? '编辑' : '流转'}
                    </button>
                    {override.status === 'PENDING' && (
                      <>
                        <button
                          className="btn btn-secondary btn-xs"
                          type="button"
                          disabled={!canPolicyManage || contextPolicyState.loading}
                          onClick={() => void reviewContextPolicyOverride(override.id, 'approve')}
                        >
                          <CheckCircle2 size={14} />
                          通过
                        </button>
                        <button
                          className="btn btn-ghost btn-xs"
                          type="button"
                          disabled={!canPolicyManage || contextPolicyState.loading}
                          onClick={() => void reviewContextPolicyOverride(override.id, 'reject')}
                        >
                          <XCircle size={14} />
                          驳回
                        </button>
                      </>
                    )}
                  </div>
                </div>
              )) : (
                <div className="notice info">暂无策略覆盖记录</div>
              )}
            </div>
            <div className="test-design-context-policy-notes">
              <strong>备注流转 · {selectedContextPolicyOverride?.workOrderKey ?? (selectedContextPolicyOverrideId || '-')}</strong>
              {selectedContextPolicyOverrideId ? (
                contextPolicyNotes.length ? contextPolicyNotes.slice(-6).map((note) => (
                  <div className="test-design-context-policy-note" key={note.id}>
                    <span className="badge badge-neutral">{note.noteType}</span>
                    <em>{note.noteText}</em>
                    <small>{note.createdBy ?? '-'} · {note.createdAt ?? '-'}</small>
                  </div>
                )) : (
                  <div className="notice info">暂无备注流转记录</div>
                )
              ) : (
                <div className="notice info">未选择策略覆盖记录</div>
              )}
            </div>
            <StateLine state={contextPolicyState} />
          </div>
        </section>

        <section className="panel test-design-conflict-operations-panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">资产冲突运营台</h2>
              <p className="panel-desc">
                {conflictOperationSummary
                  ? `未处理 ${conflictOperationSummary.openCount} · 已处理 ${conflictOperationSummary.resolvedCount}`
                  : '正式发布冲突集中处理。'}
              </p>
            </div>
            <div className="toolbar-actions">
              <button
                className="btn btn-secondary btn-xs"
                type="button"
                disabled={!canPublish || publishState.loading || !batchResolvableConflictOperationItems.length}
                onClick={requestBatchResolveConflictOperations}
              >
                <Link2 size={14} />
                批量复用 {batchResolvableConflictOperationItems.length}
              </button>
              <button
                className="btn btn-secondary btn-xs"
                type="button"
                disabled={!canRead || conflictOperationState.loading}
                onClick={() => void refreshConflictOperations(0)}
              >
                <RefreshCw size={14} />
                刷新
              </button>
            </div>
          </div>
          <div className="panel-body compact main-stack">
            <div className="asset-filter-bar test-design-conflict-operations-filter">
              <label className="field">
                <span className="field-label">项目</span>
                <input
                  value={conflictOperationProjectId}
                  onChange={(event) => setConflictOperationFilters((current) => ({ ...current, projectId: event.target.value }))}
                  placeholder="project UUID"
                  disabled={!canRead || conflictOperationState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">任务 ID</span>
                <input
                  value={conflictOperationFilters.taskId}
                  onChange={(event) => setConflictOperationFilters((current) => ({ ...current, taskId: event.target.value }))}
                  placeholder={selectedTaskId || '全部任务'}
                  disabled={!canRead || conflictOperationState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">处理状态</span>
                <select
                  value={conflictOperationFilters.resolutionStatus}
                  onChange={(event) => setConflictOperationFilters((current) => ({
                    ...current,
                    resolutionStatus: event.target.value as ConflictOperationFilters['resolutionStatus']
                  }))}
                  disabled={!canRead || conflictOperationState.loading}
                >
                  <option value="OPEN">OPEN</option>
                  <option value="RESOLVED">RESOLVED</option>
                  <option value="ALL">ALL</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">候选状态</span>
                <select
                  value={conflictOperationFilters.candidateStatus}
                  onChange={(event) => setConflictOperationFilters((current) => ({ ...current, candidateStatus: event.target.value }))}
                  disabled={!canRead || conflictOperationState.loading}
                >
                  <option value="">全部</option>
                  {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => (
                    <option value={status} key={status}>{status}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input
                  value={conflictOperationFilters.keyword}
                  onChange={(event) => setConflictOperationFilters((current) => ({ ...current, keyword: event.target.value }))}
                  placeholder="候选 / 任务 / 用例"
                  disabled={!canRead || conflictOperationState.loading}
                />
              </label>
              <div className="toolbar-actions test-design-conflict-operations-actions">
                <button
                  className="btn btn-secondary btn-sm"
                  type="button"
                  disabled={!canRead || conflictOperationState.loading || !selectedTaskId}
                  onClick={() => setConflictOperationFilters((current) => ({ ...current, taskId: selectedTaskId }))}
                >
                  <ClipboardCheck size={15} />
                  当前任务
                </button>
                <button
                  className="btn btn-ghost btn-sm"
                  type="button"
                  disabled={!canRead || conflictOperationState.loading}
                  onClick={() => setConflictOperationFilters(initialConflictOperationFilters)}
                >
                  <Search size={15} />
                  重置
                </button>
              </div>
            </div>
            <div className="test-design-conflict-form">
              <label className="field">
                <span className="field-label">处理原因</span>
                <input
                  value={conflictResolutionDraft.reason}
                  onChange={(event) => setConflictResolutionDraft((current) => ({ ...current, reason: event.target.value }))}
                  disabled={!canPublish || publishState.loading}
                />
              </label>
              <label className="field">
                <span className="field-label">用例关键词</span>
                <input
                  value={conflictCaseKeyword}
                  onChange={(event) => setConflictCaseKeyword(event.target.value)}
                  placeholder="标题 / 标签 / 编号"
                  disabled={!canRead || publishState.loading || !conflictCaseSearchProjectId}
                />
              </label>
              <label className="field">
                <span className="field-label">补充说明</span>
                <input
                  value={conflictResolutionDraft.comment}
                  onChange={(event) => setConflictResolutionDraft((current) => ({ ...current, comment: event.target.value }))}
                  placeholder="比对说明"
                  disabled={!canPublish || publishState.loading}
                />
              </label>
              <div className="field test-design-conflict-search-action">
                <span className="field-label">既有用例</span>
                <button
                  className="btn btn-secondary btn-sm"
                  type="button"
                  disabled={!canRead || publishState.loading || !conflictCaseSearchProjectId}
                  onClick={() => void searchConflictCases()}
                >
                  <Search size={15} />
                  搜索
                </button>
              </div>
            </div>
            {conflictOperationSummary && (
              <div className="detail-grid">
                <Detail label="冲突总数" value={conflictOperationSummary.totalCount} />
                <Detail label="未处理" value={conflictOperationSummary.openCount} />
                <Detail label="已处理" value={conflictOperationSummary.resolvedCount} />
                <Detail label="人工复核" value={conflictOperationSummary.duplicateReviewCount} />
                <Detail label="最近冲突" value={conflictOperationSummary.latestConflictAt ?? '-'} />
              </div>
            )}
            <div className="test-design-conflict-operations-list">
              {conflictOperations.length ? conflictOperations.map((item) => {
                const record = item.record;
                const candidate = conflictResolutionCandidate(record, conflictCandidateById);
                const targetCaseId = conflictResolutionTargetCaseId(record, selectedConflictCaseIds);
                return (
                  <div className={item.resolved ? 'test-design-conflict-operation-row resolved' : 'test-design-conflict-operation-row'} key={publishRecordKey(record)}>
                    <span>
                      <strong>{item.candidateTitle ?? record.title ?? item.candidateId ?? '-'}</strong>
                      <em>{item.taskTitle ?? item.taskId ?? '-'} · {item.candidateStatus ?? '-'}@v{item.candidateVersion}</em>
                      <em>{targetCaseId ? `目标用例 ${targetCaseId}` : `推荐用例 ${item.recommendedCaseId ?? '-'}`}</em>
                      {record.errorMessage && <small>{record.errorMessage}</small>}
                    </span>
                    <div className="test-design-conflict-controls">
                      <PublishResultBadge value={item.resolved ? 'RESOLVED' : record.result} />
                      <select
                        value={targetCaseId}
                        onChange={(event) => {
                          const nextCaseId = event.target.value;
                          const candidateId = record.candidateId;
                          if (candidateId) {
                            setSelectedConflictCaseIds((current) => ({
                              ...current,
                              [candidateId]: nextCaseId
                            }));
                          }
                        }}
                        disabled={!canPublish || publishState.loading || !item.resolvable}
                      >
                        <option value="">{record.assetCaseId ? '清空目标' : '选择目标用例'}</option>
                        {record.assetCaseId && (
                          <option value={record.assetCaseId}>推荐 {shortIdentifier(record.assetCaseId)}</option>
                        )}
                        {conflictCaseResults.filter((testCase) => testCase.id !== record.assetCaseId).map((testCase) => (
                          <option value={testCase.id} key={`${record.candidateId}-${testCase.id}`}>
                            {testCase.title || shortIdentifier(testCase.id)}
                          </option>
                        ))}
                      </select>
                      <button
                        className="btn btn-secondary btn-xs"
                        type="button"
                        disabled={!canPublish || publishState.loading || !item.resolvable || !candidate || !targetCaseId}
                        onClick={() => requestResolveConflict(record)}
                      >
                        <Link2 size={14} />
                        复用
                      </button>
                    </div>
                  </div>
                );
              }) : (
                <div className="notice info">{conflictOperationProjectId ? '暂无匹配冲突' : '请先填写项目 ID'}</div>
              )}
            </div>
            {conflictOperationPage.total > TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE && (
              <div className="test-design-pagination" aria-label="资产冲突分页">
                <span>
                  {conflictOperationPage.items.length
                    ? `${conflictOperationPage.start}-${conflictOperationPage.end} / ${conflictOperationPage.total}`
                    : `0 / ${conflictOperationPage.total}`}
                </span>
                <button
                  className="btn btn-secondary btn-xs"
                  type="button"
                  disabled={conflictOperationPage.index <= 0 || conflictOperationState.loading}
                  onClick={() => void refreshConflictOperations(Math.max(0, conflictOperationPage.index - 1))}
                >
                  <ChevronLeft size={14} />
                  上一页
                </button>
                <button
                  className="btn btn-secondary btn-xs"
                  type="button"
                  disabled={(conflictOperationPage.index + 1) * TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE >= conflictOperationPage.total || conflictOperationState.loading}
                  onClick={() => void refreshConflictOperations(conflictOperationPage.index + 1)}
                >
                  下一页
                  <ChevronRight size={14} />
                </button>
              </div>
            )}
            <StateLine state={conflictOperationState} />
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">模板管理</h2>
              <p className="panel-desc">{templatePageTotal ? `${templatePageTotal} 个可用模板` : '生成参数预配置。'}</p>
            </div>
            <button className="btn btn-secondary btn-xs" type="button" disabled={!canRead || templateState.loading} title="刷新模板" onClick={() => void refreshTemplates()}>
              <RefreshCw size={14} />
            </button>
          </div>
          <div className="panel-body compact">
            <form className="main-stack" onSubmit={saveTemplate}>
              <div className="test-design-template-toolbar">
                <label className="field">
                  <span className="field-label">当前模板</span>
                  <select value={selectedTemplateManageId} onChange={(event) => setSelectedTemplateManageId(event.target.value)} disabled={templateState.loading}>
                    <option value="">新建模板</option>
                    {templates.map((template) => (
                      <option key={template.id} value={template.id}>
                        {template.enabled ? '' : '禁用 · '}{template.projectId ? '项目' : '全局'} · {template.name}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  className="btn btn-secondary btn-icon btn-sm"
                  type="button"
                  title="新建模板"
                  disabled={templateState.loading}
                  onClick={() => {
                    setSelectedTemplateManageId('');
                    setTemplateDraft({ ...initialTemplateDraft, projectId: templateProjectId });
                  }}
                >
                  <Plus size={15} />
                </button>
              </div>
              <label className="field">
                <span className="field-label">名称</span>
                <input value={templateDraft.name} onChange={(event) => setTemplateDraft((current) => ({ ...current, name: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
              </label>
              <label className="field">
                <span className="field-label">作用域项目 ID</span>
                <input value={templateDraft.projectId} onChange={(event) => setTemplateDraft((current) => ({ ...current, projectId: event.target.value }))} placeholder="留空为全局模板" disabled={!canPolicyManage || templateState.loading || Boolean(selectedManagedTemplate)} />
              </label>
              <label className="field">
                <span className="field-label">说明</span>
                <input value={templateDraft.description} onChange={(event) => setTemplateDraft((current) => ({ ...current, description: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
              </label>
              <div className="test-design-template-inline-grid">
                <label className="field">
                  <span className="field-label">Prompt Key</span>
                  <input value={templateDraft.promptKey} onChange={(event) => setTemplateDraft((current) => ({ ...current, promptKey: event.target.value }))} placeholder={health?.promptKey ?? '默认'} disabled={!canPolicyManage || templateState.loading} />
                </label>
                <label className="field">
                  <span className="field-label">版本</span>
                  <input value={templateDraft.promptVersion} onChange={(event) => setTemplateDraft((current) => ({ ...current, promptVersion: event.target.value }))} placeholder={health?.promptVersion ?? '默认'} disabled={!canPolicyManage || templateState.loading} />
                </label>
              </div>
              <div className="test-design-template-inline-grid">
                <label className="field">
                  <span className="field-label">每需求数</span>
                  <input value={templateDraft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => setTemplateDraft((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
                </label>
                <label className="field">
                  <span className="field-label">环境 Key</span>
                  <input value={templateDraft.environmentKey} onChange={(event) => setTemplateDraft((current) => ({ ...current, environmentKey: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
                </label>
              </div>
              <div className="test-design-template-inline-grid">
                <label className="field">
                  <span className="field-label">生成策略</span>
                  <select value={templateDraft.generationStrategy} onChange={(event) => setTemplateDraft((current) => ({ ...current, generationStrategy: event.target.value }))} disabled={!canPolicyManage || templateState.loading}>
                    {TEST_DESIGN_GENERATION_STRATEGIES.map((strategy) => (
                      <option key={strategy} value={strategy}>{strategy}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">覆盖策略</span>
                  <select value={templateDraft.coverageStrategy} onChange={(event) => setTemplateDraft((current) => ({ ...current, coverageStrategy: event.target.value }))} disabled={!canPolicyManage || templateState.loading}>
                    {TEST_DESIGN_COVERAGE_STRATEGIES.map((strategy) => (
                      <option key={strategy} value={strategy}>{strategy}</option>
                    ))}
                  </select>
                </label>
              </div>
              <label className="field">
                <span className="field-label">上下文 API ID</span>
                <input value={templateDraft.contextApiIds} onChange={(event) => setTemplateDraft((current) => ({ ...current, contextApiIds: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
              </label>
              <label className="field">
                <span className="field-label">上下文页面 ID</span>
                <input value={templateDraft.contextPageIds} onChange={(event) => setTemplateDraft((current) => ({ ...current, contextPageIds: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
              </label>
              <label className="field">
                <span className="field-label">上下文业务流 ID</span>
                <input value={templateDraft.contextFlowIds} onChange={(event) => setTemplateDraft((current) => ({ ...current, contextFlowIds: event.target.value }))} disabled={!canPolicyManage || templateState.loading} />
              </label>
              <div className="field">
                <span className="field-label">覆盖类型</span>
                <div className="test-design-checks">
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => (
                    <label key={type}>
                      <input type="checkbox" checked={templateDraft.coverageTypes.includes(type)} onChange={() => toggleTemplateCoverage(type)} disabled={!canPolicyManage || templateState.loading} />
                      <span>{type}</span>
                    </label>
                  ))}
                </div>
              </div>
              <label className="test-design-template-enabled">
                <input type="checkbox" checked={templateDraft.enabled} onChange={(event) => setTemplateDraft((current) => ({ ...current, enabled: event.target.checked }))} disabled={!canPolicyManage || templateState.loading} />
                <span>启用</span>
              </label>
              <div className="toolbar-actions">
                <button className="btn btn-secondary btn-sm" type="submit" disabled={!canPolicyManage || templateState.loading || !templateDraft.name.trim()}>
                  <Layers3 size={15} />
                  {selectedManagedTemplate ? '更新' : '创建'}
                </button>
                <button className="btn btn-ghost btn-sm" type="button" disabled={!canPolicyManage || templateState.loading || !selectedManagedTemplate || !selectedManagedTemplate.enabled} onClick={() => void disableTemplate()}>
                  <Trash2 size={15} />
                  禁用
                </button>
              </div>
              <StateLine state={templateState} />
            </form>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">评审历史</h2>
              <p className="panel-desc">{reviewRecordPageTotal ? `${reviewRecordPageTotal} 条候选编辑和评审记录` : '候选编辑和评审审计摘要。'}</p>
            </div>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!canExport || reviewRecordState.loading || !reviewRecordPageTotal} onClick={() => void exportReviewRecords()}>
              <Download size={15} />
              导出
            </button>
          </div>
          <div className="panel-body compact main-stack">
            <StateLine state={reviewRecordState} />
            <ReviewSummaryPanel
              scopeLabel={reviewSummaryScope}
              selectedTaskId={selectedTaskId}
              summary={reviewSummary}
            />
            {reviewRecordPage.total > 0 && (
              <div className="test-design-pagination" aria-label="评审历史分页">
                <span>{reviewRecordPage.start}-{reviewRecordPage.end} / {reviewRecordPage.total}</span>
                <div className="toolbar-actions">
                  <button
                    aria-label="上一页评审历史"
                    className="btn btn-secondary btn-xs"
                    disabled={!reviewRecordPage.hasPrevious || reviewRecordState.loading}
                    title="上一页"
                    type="button"
                    onClick={() => setReviewRecordPageIndex((current) => Math.max(0, current - 1))}
                  >
                    <ChevronLeft size={14} />
                  </button>
                  <span className="field-hint">{reviewRecordPage.index + 1} / {reviewRecordPage.totalPages}</span>
                  <button
                    aria-label="下一页评审历史"
                    className="btn btn-secondary btn-xs"
                    disabled={!reviewRecordPage.hasNext || reviewRecordState.loading}
                    title="下一页"
                    type="button"
                    onClick={() => setReviewRecordPageIndex((current) => current + 1)}
                  >
                    <ChevronRight size={14} />
                  </button>
                </div>
              </div>
            )}
            {reviewRecordPage.items.length ? (
              <div className="test-design-review-records">
                {reviewRecordPage.items.map((record) => (
                  <ReviewRecordRow key={record.id} record={record} />
                ))}
              </div>
            ) : (
              <div className="notice info">{selectedTaskId ? '暂无评审历史' : '请先选择任务'}</div>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">候选评审</h2>
              <p className="panel-desc">编辑候选用例并确认，发布后会写入 WP3 测试用例和需求追踪关系。</p>
            </div>
            <StateLine state={taskState} />
          </div>
          <div className="panel-body">
            <div className="asset-filter-bar test-design-candidate-filter">
              <label className="field">
                <span className="field-label">候选状态</span>
                <select value={candidateFilters.status} onChange={(event) => setCandidateFilters((current) => ({ ...current, status: event.target.value }))} disabled={taskState.loading || !selectedTaskId}>
                  <option value="">全部</option>
                  {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="field-label">覆盖类型</span>
                <select value={candidateFilters.coverageType} onChange={(event) => setCandidateFilters((current) => ({ ...current, coverageType: event.target.value }))} disabled={taskState.loading || !selectedTaskId}>
                  <option value="">全部</option>
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input value={candidateFilters.keyword} onChange={(event) => setCandidateFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="标题 / 标签 / 错误" disabled={taskState.loading || !selectedTaskId} />
              </label>
              <div className="filter-actions">
                <button className="btn btn-secondary btn-sm" type="button" disabled={!selectedTaskId} onClick={() => setCandidateFilters(initialCandidateFilters)}>
                  <Search size={15} />
                  重置
                </button>
                <button className="btn btn-ghost btn-sm" type="button" disabled={!currentPageSelectableCandidates.length} onClick={selectCurrentPageCandidates}>
                  选中本页
                </button>
                <button className="btn btn-ghost btn-sm" type="button" disabled={!selectedCandidateIds.length} onClick={() => setSelectedCandidateIds([])}>
                  清空选择
                </button>
                <button className="btn btn-secondary btn-sm" type="button" disabled={!canExport || !selectedTaskId || !candidatePage.total} onClick={() => void exportCandidateReview('page')}>
                  <Download size={15} />
                  导出筛选
                </button>
                <button className="btn btn-secondary btn-sm" type="button" disabled={!canExport || !selectedCandidates.length} onClick={() => void exportCandidateReview('selected')}>
                  <Download size={15} />
                  导出已选
                </button>
                <button className="btn btn-secondary btn-sm" type="button" disabled={!canExport || !selectedTask || taskState.loading} onClick={() => void exportTaskReport()}>
                  <Download size={15} />
                  导出报告
                </button>
              </div>
            </div>
            {candidatePage.total > 0 && (
              <div className="test-design-pagination" aria-label="候选分页">
                <span>
                  {candidatePage.start}-{candidatePage.end} / {candidatePage.total}
                  {selectedCandidates.length ? ` · 已选 ${selectedCandidates.length}` : ''}
                </span>
                <label>
                  <span>每页</span>
                  <select value={candidatePageSize} onChange={(event) => setCandidatePageSize(Number(event.target.value))} disabled={taskState.loading}>
                    {TEST_DESIGN_CANDIDATE_PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
                  </select>
                </label>
                <div className="toolbar-actions">
                  <button
                    aria-label="上一页候选"
                    className="btn btn-secondary btn-xs"
                    disabled={!candidatePage.hasPrevious}
                    title="上一页"
                    type="button"
                    onClick={() => setCandidatePageIndex((current) => Math.max(0, current - 1))}
                  >
                    <ChevronLeft size={14} />
                  </button>
                  <span className="field-hint">{candidatePage.index + 1} / {candidatePage.totalPages}</span>
                  <button
                    aria-label="下一页候选"
                    className="btn btn-secondary btn-xs"
                    disabled={!candidatePage.hasNext}
                    title="下一页"
                    type="button"
                    onClick={() => setCandidatePageIndex((current) => current + 1)}
                  >
                    <ChevronRight size={14} />
                  </button>
                </div>
              </div>
            )}
            {batchActionResult && <BatchActionSummary result={batchActionResult} />}
            {batchEditResult && <BatchEditSummary result={batchEditResult} />}
            {selectedReviewCandidates.length > 0 && (
              <div className="test-design-batch-toolbar">
                <span>批量评审 {selectedReviewCandidates.length} 个候选</span>
                <div className="toolbar-actions">
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => requestBatchReviewCandidates('CONFIRM')}>
                    批量确认
                  </button>
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading || !reviewComment.trim()} onClick={() => requestBatchReviewCandidates('REJECT')}>
                    批量驳回
                  </button>
                  <button className="btn btn-ghost btn-sm" type="button" disabled={!canReview || mutationState.loading || !reviewComment.trim()} onClick={() => requestBatchReviewCandidates('IGNORE')}>
                    批量忽略
                  </button>
                </div>
              </div>
            )}
            {selectedCandidateIds.length > 0 && (
              <div className="test-design-batch-editor">
                <div className="test-design-batch-editor-heading">
                  <span>批量字段编辑 {selectedBatchEditableCandidates.length} / {selectedCandidateIds.length} 个可编辑候选</span>
                  <button className="btn btn-ghost btn-xs" type="button" disabled={mutationState.loading} onClick={() => setBatchEditDraft(initialTestDesignBatchEditDraft)}>
                    重置
                  </button>
                </div>
                <div className="test-design-batch-editor-grid">
                  <label className="field">
                    <span className="field-label">覆盖类型</span>
                    <select value={batchEditDraft.coverageType} onChange={(event) => setBatchEditDraft((current) => ({ ...current, coverageType: event.target.value }))} disabled={!canReview || mutationState.loading}>
                      <option value="">不修改</option>
                      {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">优先级</span>
                    <select value={batchEditDraft.priority} onChange={(event) => setBatchEditDraft((current) => ({ ...current, priority: event.target.value }))} disabled={!canReview || mutationState.loading}>
                      <option value="">不修改</option>
                      <option value="CRITICAL">CRITICAL</option>
                      <option value="HIGH">HIGH</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="LOW">LOW</option>
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">标签策略</span>
                    <select value={batchEditDraft.tagMode} onChange={(event) => setBatchEditDraft((current) => ({ ...current, tagMode: event.target.value === 'replace' ? 'replace' : 'append' }))} disabled={!canReview || mutationState.loading}>
                      <option value="append">追加标签</option>
                      <option value="replace">替换标签</option>
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">标签</span>
                    <input value={batchEditDraft.tags} onChange={(event) => setBatchEditDraft((current) => ({ ...current, tags: event.target.value }))} placeholder="regression, wp5" disabled={!canReview || mutationState.loading} />
                  </label>
                </div>
                {batchEditIssues.length > 0 && (
                  <div className="field-error-list">
                    {batchEditIssues.map((issue, index) => <span key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</span>)}
                  </div>
                )}
                <div className="toolbar-actions">
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading || batchEditBlocked} onClick={requestBatchEditCandidates}>
                    <Save size={15} />
                    批量应用字段
                  </button>
                  {batchEditFieldLabels.length > 0 && <span className="field-hint">{batchEditFieldLabels.join('；')}</span>}
                </div>
              </div>
            )}
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 48 }}></th>
                    <th>标题</th>
                    <th>覆盖</th>
                    <th>优先级</th>
                    <th>状态</th>
                    <th>来源</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {candidatePage.items.length ? (
                    candidatePage.items.map((candidate) => (
                      <tr className={candidate.id === selectedCandidateId ? 'selected-row' : ''} key={candidate.id}>
                        <td>
                          <input
                            aria-label={`选择候选 ${candidate.title}`}
                            type="checkbox"
                            checked={selectedCandidateIds.includes(candidate.id)}
                            onChange={() => toggleCandidateSelection(candidate.id)}
                            disabled={!canSelectTestDesignCandidate(candidate)}
                          />
                        </td>
                        <td>
                          <strong>{candidate.title}</strong>
                          <div className="field-hint">{candidate.errorMessage ?? candidate.requirementId ?? '-'}</div>
                        </td>
                        <td>{candidate.coverageType}</td>
                        <td>{candidate.priority}</td>
                        <td><CandidateStatus value={candidate.status} /></td>
                        <td><GenerationSourceBadge source={candidateGenerationSource(candidate, selectedTask)} compact /></td>
                        <td>
                          <button className="btn btn-secondary btn-xs" type="button" onClick={() => setSelectedCandidateId(candidate.id)}>
                            <Eye size={14} />
                            查看
                          </button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td className="table-empty" colSpan={7}>{selectedTaskId ? '暂无匹配候选用例' : '请先生成或选择任务'}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {candidateDraft && selectedCandidate && (
              <div className="test-design-editor">
                <div className="test-design-source-summary">
                  <span>候选来源</span>
                  <GenerationSourceBadge source={selectedCandidateSource} />
                  <em>{generationSourceText(selectedCandidateSource)}</em>
                </div>
                {candidateQualityIssues.length > 0 && (
                  <div className="notice warning test-design-quality-summary">
                    <strong>质量提示</strong>
                    <span>保存前需处理 {candidateQualityIssues.length} 项候选质量问题。</span>
                    <ul className="test-design-quality-list">
                      {candidateQualityIssues.slice(0, 6).map((issue, index) => (
                        <li key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="asset-form-grid">
                  <label className="field">
                    <span className="field-label">标题</span>
                    <input value={candidateDraft.title} onChange={(event) => setCandidateDraft({ ...candidateDraft, title: event.target.value })} disabled={!canReview || mutationState.loading} />
                    <QualityFieldMessages field="title" issues={candidateQualityIssues} />
                  </label>
                  <label className="field">
                    <span className="field-label">覆盖类型</span>
                    <select value={candidateDraft.coverageType} onChange={(event) => setCandidateDraft({ ...candidateDraft, coverageType: event.target.value })} disabled={!canReview || mutationState.loading}>
                      {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                    </select>
                    <QualityFieldMessages field="coverageType" issues={candidateQualityIssues} />
                  </label>
                  <label className="field">
                    <span className="field-label">优先级</span>
                    <select value={candidateDraft.priority} onChange={(event) => setCandidateDraft({ ...candidateDraft, priority: event.target.value })} disabled={!canReview || mutationState.loading}>
                      <option value="CRITICAL">CRITICAL</option>
                      <option value="HIGH">HIGH</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="LOW">LOW</option>
                    </select>
                    <QualityFieldMessages field="priority" issues={candidateQualityIssues} />
                  </label>
                </div>
                <div className="asset-form-grid">
                  <label className="field">
                    <span className="field-label">API ID</span>
                    <input value={candidateDraft.apiId} onChange={(event) => setCandidateDraft({ ...candidateDraft, apiId: event.target.value })} disabled={!canReview || mutationState.loading} />
                  </label>
                  <label className="field">
                    <span className="field-label">前置条件</span>
                    <input value={candidateDraft.preconditions} onChange={(event) => setCandidateDraft({ ...candidateDraft, preconditions: event.target.value })} disabled={!canReview || mutationState.loading} />
                    <QualityFieldMessages field="preconditions" issues={candidateQualityIssues} />
                  </label>
                  <label className="field">
                    <span className="field-label">标签</span>
                    <input value={candidateDraft.tags} onChange={(event) => setCandidateDraft({ ...candidateDraft, tags: event.target.value })} disabled={!canReview || mutationState.loading} />
                    <QualityFieldMessages field="tags" issues={candidateQualityIssues} />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">描述</span>
                  <textarea value={candidateDraft.description} onChange={(event) => setCandidateDraft({ ...candidateDraft, description: event.target.value })} disabled={!canReview || mutationState.loading} />
                  <QualityFieldMessages field="description" issues={candidateQualityIssues} />
                </label>
                <div className="field test-design-steps-editor">
                  <div className="test-design-steps-heading">
                    <span className="field-label">步骤</span>
                    <div className="toolbar-actions">
                      <button className="btn btn-secondary btn-xs" type="button" title="批量插入" disabled={!canReview || mutationState.loading} onClick={insertPresetSteps}>
                        <Plus size={14} />
                        批量
                      </button>
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title="添加步骤" disabled={!canReview || mutationState.loading} onClick={addStepDraft}>
                        <Plus size={14} />
                      </button>
                      <button className="btn btn-ghost btn-icon btn-xs" type="button" title="删除已选步骤" disabled={!canReview || mutationState.loading || !candidateDraft.steps.some((step) => step.selected)} onClick={deleteSelectedSteps}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                  <div className="test-design-step-list">
                    {candidateDraft.steps.map((step, index) => (
                      <div
                        key={step.id}
                        className={draggingStepId === step.id ? 'test-design-step-row dragging' : 'test-design-step-row'}
                        draggable={canReview && !mutationState.loading}
                        onDragStart={() => setDraggingStepId(step.id)}
                        onDragEnd={() => setDraggingStepId('')}
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={() => dropStepDraft(step.id)}
                      >
                        <label className="test-design-step-select" title="选择步骤">
                          <input type="checkbox" checked={step.selected} onChange={(event) => updateStepDraft(step.id, { selected: event.target.checked })} disabled={!canReview || mutationState.loading} />
                        </label>
                        <button className="btn btn-ghost btn-icon btn-xs test-design-step-drag" type="button" title="拖拽排序" disabled={!canReview || mutationState.loading}>
                          <GripVertical size={14} />
                        </button>
                        <span className="asset-step-index">{index + 1}</span>
                        <label className="field">
                          <span className="field-label">操作</span>
                          <textarea value={step.action} onChange={(event) => updateStepDraft(step.id, { action: event.target.value })} disabled={!canReview || mutationState.loading} />
                        </label>
                        <label className="field">
                          <span className="field-label">预期</span>
                          <textarea value={step.expectedResult} onChange={(event) => updateStepDraft(step.id, { expectedResult: event.target.value })} disabled={!canReview || mutationState.loading} />
                        </label>
                        <div className="test-design-step-actions">
                          <button className="btn btn-secondary btn-icon btn-xs" type="button" title="上移" disabled={!canReview || mutationState.loading || index === 0} onClick={() => moveStepDraft(step.id, -1)}>
                            <ArrowUp size={14} />
                          </button>
                          <button className="btn btn-secondary btn-icon btn-xs" type="button" title="下移" disabled={!canReview || mutationState.loading || index === candidateDraft.steps.length - 1} onClick={() => moveStepDraft(step.id, 1)}>
                            <ArrowDown size={14} />
                          </button>
                          <button className="btn btn-secondary btn-icon btn-xs" type="button" title="插入下一步" disabled={!canReview || mutationState.loading} onClick={() => insertStepDraftAfter(step.id)}>
                            <Plus size={14} />
                          </button>
                          <button className="btn btn-ghost btn-icon btn-xs" type="button" title="删除步骤" disabled={!canReview || mutationState.loading} onClick={() => removeStepDraft(step.id)}>
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                  <span className="field-hint">{candidateDraft.steps.length} 个步骤，已选 {candidateDraft.steps.filter((step) => step.selected).length} 个。</span>
                  <QualityFieldMessages field="steps" issues={candidateQualityIssues} />
                </div>
                <label className="field">
                  <span className="field-label">预期结果</span>
                  <textarea value={candidateDraft.expectedResult} onChange={(event) => setCandidateDraft({ ...candidateDraft, expectedResult: event.target.value })} disabled={!canReview || mutationState.loading} />
                  <QualityFieldMessages field="expectedResult" issues={candidateQualityIssues} />
                </label>
                <label className="field">
                  <span className="field-label">评审意见</span>
                  <input value={reviewComment} onChange={(event) => setReviewComment(event.target.value)} disabled={!canReview || mutationState.loading} />
                </label>
                <div className="toolbar-actions">
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading || !candidateDraft.title.trim() || candidateSaveBlocked} onClick={() => void saveCandidate()}>
                    <Save size={15} />
                    保存
                  </button>
                  <button className="btn btn-primary btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => void reviewCandidate('confirm')}>
                    <CheckCircle2 size={15} />
                    确认
                  </button>
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => void reviewCandidate('reject')}>
                    <XCircle size={15} />
                    驳回
                  </button>
                  <button className="btn btn-ghost btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => void reviewCandidate('ignore')}>
                    忽略
                  </button>
                </div>
                <StateLine state={mutationState} />
              </div>
            )}
          </div>
        </section>
      </div>

      <aside className="side-stack">
        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">生成配置</h2>
              <p className="panel-desc">当前选择 {selectedRequirementIds.length} 个需求。</p>
            </div>
          </div>
          <div className="panel-body compact">
            <form className="main-stack" onSubmit={createTask}>
              <label className="field">
                <span className="field-label">生成模板</span>
                <select value={generationDraft.templateId} onChange={(event) => selectGenerationTemplate(event.target.value)} disabled={!canGenerate || mutationState.loading || templateState.loading}>
                  <option value="">手动配置</option>
                  {templates.filter((template) => template.enabled).map((template) => (
                    <option key={template.id} value={template.id}>
                      {template.projectId ? '项目' : '全局'} · {template.name}
                    </option>
                  ))}
                </select>
                <span className="field-hint">
                  {selectedGenerationTemplate
                    ? `${selectedGenerationTemplate.promptKey}@${selectedGenerationTemplate.promptVersion} · ${selectedGenerationTemplate.generationStrategy}/${selectedGenerationTemplate.coverageStrategy}`
                    : '不选择模板时使用手动参数或平台默认值。'}
                </span>
              </label>
              <label className="field">
                <span className="field-label">项目 ID</span>
                <input value={generationDraft.projectId} onChange={(event) => setGenerationDraft((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">任务标题</span>
                <input value={generationDraft.title} onChange={(event) => setGenerationDraft((current) => ({ ...current, title: event.target.value }))} placeholder="登录模块用例生成" disabled={!canGenerate || mutationState.loading} />
              </label>
              <div className="test-design-template-inline-grid">
                <label className="field">
                  <span className="field-label">Prompt Key</span>
                  <input value={generationDraft.promptKey} onChange={(event) => setGenerationDraft((current) => ({ ...current, promptKey: event.target.value }))} placeholder={health?.promptKey ?? '平台默认'} disabled={!canGenerate || mutationState.loading} />
                </label>
                <label className="field">
                  <span className="field-label">Prompt Version</span>
                  <input value={generationDraft.promptVersion} onChange={(event) => setGenerationDraft((current) => ({ ...current, promptVersion: event.target.value }))} placeholder={health?.promptVersion ?? '平台默认'} disabled={!canGenerate || mutationState.loading} />
                </label>
              </div>
              <label className="field">
                <span className="field-label">每需求用例数</span>
                <input value={generationDraft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => setGenerationDraft((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">环境 Key</span>
                <input value={generationDraft.environmentKey} onChange={(event) => setGenerationDraft((current) => ({ ...current, environmentKey: event.target.value }))} placeholder="qa / staging" disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">上下文 API ID</span>
                <input value={generationDraft.contextApiIds} onChange={(event) => setGenerationDraft((current) => ({ ...current, contextApiIds: event.target.value }))} placeholder={`最多 ${explicitContextAssetLimit} 个，逗号或换行分隔`} disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">上下文页面 ID</span>
                <input value={generationDraft.contextPageIds} onChange={(event) => setGenerationDraft((current) => ({ ...current, contextPageIds: event.target.value }))} placeholder={`最多 ${explicitContextAssetLimit} 个，逗号或换行分隔`} disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">上下文业务流 ID</span>
                <input value={generationDraft.contextFlowIds} onChange={(event) => setGenerationDraft((current) => ({ ...current, contextFlowIds: event.target.value }))} placeholder={`最多 ${explicitContextAssetLimit} 个，逗号或换行分隔`} disabled={!canGenerate || mutationState.loading} />
              </label>
              <div className="field">
                <span className="field-label">覆盖类型</span>
                <div className="test-design-checks">
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => (
                    <label key={type}>
                      <input type="checkbox" checked={generationDraft.coverageTypes.includes(type)} onChange={() => toggleCoverage(type)} disabled={!canGenerate || mutationState.loading} />
                      <span>{type}</span>
                    </label>
                  ))}
                </div>
              </div>
              <button className="btn btn-primary" type="submit" disabled={!canGenerate || mutationState.loading || !selectedRequirementIds.length}>
                <Sparkles size={16} />
                生成候选
              </button>
              <StateLine state={mutationState} />
            </form>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">任务</h2>
              <p className="panel-desc">最近 {tasks.length} 个生成任务。</p>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="asset-filter-bar test-design-side-filter">
              <label className="field">
                <span className="field-label">项目</span>
                <input value={taskFilters.projectId} onChange={(event) => setTaskFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={disabled || loadState.loading} />
              </label>
              <label className="field">
                <span className="field-label">状态</span>
                <select value={taskFilters.status} onChange={(event) => setTaskFilters((current) => ({ ...current, status: event.target.value }))} disabled={disabled || loadState.loading}>
                  <option value="">全部</option>
                  <option value="DRAFT">DRAFT</option>
                  <option value="QUEUED">QUEUED</option>
                  <option value="RUNNING">RUNNING</option>
                  <option value="SUCCEEDED">SUCCEEDED</option>
                  <option value="PARTIAL_SUCCESS">PARTIAL_SUCCESS</option>
                  <option value="FAILED">FAILED</option>
                  <option value="CANCELLED">CANCELLED</option>
                  <option value="PUBLISH_QUEUED">PUBLISH_QUEUED</option>
                  <option value="PUBLISHING">PUBLISHING</option>
                  <option value="PUBLISHED">PUBLISHED</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input value={taskFilters.keyword} onChange={(event) => setTaskFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="任务标题" disabled={disabled || loadState.loading} />
              </label>
              <button className="btn btn-secondary btn-sm" type="button" disabled={disabled} onClick={() => setTaskFilters(initialTaskFilters)}>
                <Search size={15} />
                重置
              </button>
            </div>
            <div className="quick-actions">
              {tasks.length ? tasks.map((task) => (
                <div className={task.id === selectedTaskId ? 'quick-action-row active' : 'quick-action-row'} key={task.id}>
                  <button type="button" className="quick-action-main" onClick={() => setSelectedTaskId(task.id)}>
                    <span>
                      <strong>{task.title}</strong>
                      <em>{task.status} · {task.generatedCount} / {task.confirmedCount}</em>
                      <GenerationSourceBadge source={taskGenerationSource(task)} compact />
                    </span>
                  </button>
                  <div className="quick-action-controls">
                    {RETRYABLE_TASK_STATUSES.has(task.status) && (
                      <button
                        aria-label={`重试任务 ${task.title}`}
                        className="btn btn-secondary btn-xs"
                        disabled={!canGenerate || taskState.loading}
                        title="重试任务"
                        type="button"
                        onClick={() => void retryTask(task)}
                      >
                        <RotateCcw size={14} />
                      </button>
                    )}
                    {task.status === 'QUEUED' && (
                      <button
                        aria-label={`重发排队事件 ${task.title}`}
                        className="btn btn-secondary btn-xs"
                        disabled={!canGenerate || taskState.loading}
                        title="重发排队事件"
                        type="button"
                        onClick={() => void replayQueuedTaskEvent(task)}
                      >
                        <Repeat2 size={14} />
                      </button>
                    )}
                    {CANCELLABLE_TASK_STATUSES.has(task.status) && (
                      <button
                        aria-label={`取消任务 ${task.title}`}
                        className="btn btn-secondary btn-xs"
                        disabled={!canGenerate || taskState.loading}
                        title="取消任务"
                        type="button"
                        onClick={() => void cancelTask(task)}
                      >
                        <XCircle size={14} />
                      </button>
                    )}
                  </div>
                </div>
              )) : (
                <div className="notice info">暂无生成任务</div>
              )}
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">任务诊断</h2>
              <p className="panel-desc">{selectedTask ? `${selectedTask.status} · 诊断摘要已脱敏` : '定位模型调用、幂等回放和失败上下文摘要。'}</p>
            </div>
          </div>
          <div className="panel-body compact">
            {selectedTask ? (
              <div className="test-design-task-diagnostics">
                {taskDiagnostics.map((item) => (
                  <div className={`test-design-task-diagnostic${item.tone ? ` ${item.tone}` : ''}`} key={item.label}>
                    <span>{item.label}</span>
                    <em>{item.value}</em>
                  </div>
                ))}
              </div>
            ) : (
              <div className="notice info">请先选择任务</div>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">发布</h2>
              <p className="panel-desc">发布范围 {publishScopeLabel}。</p>
            </div>
          </div>
          <div className="panel-body compact main-stack">
            {selectedCandidateIds.length > 0 ? (
              <div className="notice info">已按勾选候选中的可发布项发布：{selectedPublishableCandidates.length} / {selectedCandidateIds.length}。</div>
            ) : (
              <div className="notice info">当前将覆盖全部可发布候选。</div>
            )}
            <button className="btn btn-secondary" type="button" disabled={!canPublish || taskState.loading || publishState.loading || !canPublishCurrentScope} onClick={() => requestPublishTask(true)}>
              <Eye size={16} />
              预发布
            </button>
            <button className="btn btn-primary" type="button" disabled={!canPublish || taskState.loading || publishState.loading || !canPublishCurrentScope} onClick={() => requestPublishTask(false)}>
              <Send size={16} />
              发布到资产库
            </button>
            <StateLine state={publishState} />
            <div className="test-design-release-readiness-panel">
              <div className="test-design-release-readiness-heading">
                <span>发布准出审批</span>
                <div className="toolbar-actions">
                  {currentReleaseReadiness && (
                    <span className={`badge badge-${releaseReadinessStatusTone(currentReleaseReadiness.status)}`}>
                      {currentReleaseReadiness.status}
                    </span>
                  )}
                  <button
                    className="btn btn-secondary btn-xs"
                    type="button"
                    disabled={!canRead || releaseReadinessState.loading || !selectedTaskId}
                    onClick={() => void refreshReleaseReadinessApprovals(selectedTaskId)}
                  >
                    <RefreshCw size={14} />
                    刷新
                  </button>
                </div>
              </div>
              <div className="detail-grid">
                <Detail label="当前阻断" value={currentReleaseReadiness?.blockingCount ?? '-'} />
                <Detail label="当前风险" value={currentReleaseReadiness?.warningCount ?? '-'} />
                <Detail label="审批记录" value={releaseReadinessApprovals.length} />
                <Detail label="当前摘要" value={releaseReadinessDigestText(selectedReleaseReadinessApproval?.readinessDigest)} />
              </div>
              <form className="test-design-release-readiness-form" onSubmit={requestReleaseReadinessApproval}>
                <label className="field">
                  <span className="field-label">例外原因</span>
                  <select
                    value={releaseReadinessDraft.exceptionReasonCode}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, exceptionReasonCode: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading}
                  >
                    {releaseReadinessReasonCodes.map((code) => (
                      <option key={code} value={code}>{code}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">工单编号</span>
                  <input
                    value={releaseReadinessDraft.workOrderKey}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, workOrderKey: event.target.value }))}
                    placeholder="WP5-RR-..."
                    disabled={!canPublish || releaseReadinessState.loading}
                  />
                </label>
                <label className="field">
                  <span className="field-label">工单标题</span>
                  <input
                    value={releaseReadinessDraft.workOrderTitle}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, workOrderTitle: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading}
                  />
                </label>
                <label className="field">
                  <span className="field-label">工单 URL</span>
                  <input
                    value={releaseReadinessDraft.workOrderUrl}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, workOrderUrl: event.target.value }))}
                    placeholder="https://..."
                    disabled={!canPublish || releaseReadinessState.loading}
                  />
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">例外摘要</span>
                  <textarea
                    value={releaseReadinessDraft.exceptionSummary}
                    maxLength={1000}
                    rows={3}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, exceptionSummary: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading}
                  />
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">风险缓释</span>
                  <textarea
                    value={releaseReadinessDraft.riskMitigation}
                    maxLength={1000}
                    rows={3}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, riskMitigation: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading}
                  />
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">申请备注</span>
                  <textarea
                    value={releaseReadinessDraft.requestNote}
                    maxLength={1000}
                    rows={2}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, requestNote: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading}
                  />
                </label>
                <button
                  className="btn btn-secondary btn-sm test-design-release-readiness-submit"
                  type="submit"
                  disabled={!canPublish || releaseReadinessSubmitBlocked}
                >
                  <Save size={15} />
                  {selectedPendingReleaseReadinessApproval ? '更新例外' : '提交例外'}
                </button>
              </form>
              <div className="test-design-release-readiness-review-grid">
                <label className="field">
                  <span className="field-label">审批原因</span>
                  <select
                    value={releaseReadinessDraft.approvalReasonCode}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, approvalReasonCode: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading || !selectedPendingReleaseReadinessApproval}
                  >
                    {releaseReadinessReasonCodes.map((code) => (
                      <option key={code} value={code}>{code}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">工单状态</span>
                  <select
                    value={releaseReadinessDraft.workOrderStatus}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, workOrderStatus: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading || !selectedPendingReleaseReadinessApproval}
                  >
                    <option value="">跟随审批</option>
                    {releaseReadinessWorkOrderStatuses.map((status) => (
                      <option key={status} value={status}>{status}</option>
                    ))}
                  </select>
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">审批备注</span>
                  <textarea
                    value={releaseReadinessDraft.reviewNote}
                    maxLength={1000}
                    rows={2}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, reviewNote: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading || !selectedPendingReleaseReadinessApproval}
                  />
                </label>
                <div className="toolbar-actions test-design-release-readiness-submit">
                  <button
                    className="btn btn-secondary btn-sm"
                    type="button"
                    disabled={!canPublish || releaseReadinessState.loading || !selectedPendingReleaseReadinessApproval}
                    onClick={() => selectedPendingReleaseReadinessApproval && void reviewReleaseReadinessApproval(selectedPendingReleaseReadinessApproval.id, 'approve')}
                  >
                    <CheckCircle2 size={15} />
                    通过
                  </button>
                  <button
                    className="btn btn-ghost btn-sm"
                    type="button"
                    disabled={!canPublish || releaseReadinessState.loading || !selectedPendingReleaseReadinessApproval}
                    onClick={() => selectedPendingReleaseReadinessApproval && void reviewReleaseReadinessApproval(selectedPendingReleaseReadinessApproval.id, 'reject')}
                  >
                    <XCircle size={15} />
                    驳回
                  </button>
                </div>
              </div>
              <div className="test-design-release-readiness-approvals">
                {releaseReadinessApprovals.length ? releaseReadinessApprovals.slice(0, 6).map((approval) => (
                  <div className={`test-design-release-readiness-approval${selectedReleaseReadinessApprovalId === approval.id ? ' selected' : ''}`} key={approval.id}>
                    <div>
                      <strong>{approval.workOrderKey ?? approval.id}</strong>
                      <em>{approval.qualityGateStatus} · 阻断 {approval.blockingCount} · 风险 {approval.warningCount}</em>
                      <small>{releaseReadinessDigestText(approval.readinessDigest)} · 备注 {approval.noteCount ?? 0}</small>
                      <small>{approval.requestedBy ?? '-'} · {approval.createdAt ?? '-'}</small>
                      {approval.latestNotePreview ? <small>最新备注：{approval.latestNotePreview}</small> : null}
                    </div>
                    <div className="test-design-release-readiness-approval-actions">
                      <span className={`badge badge-${releaseReadinessStatusTone(approval.status)}`}>{approval.status}</span>
                      <button
                        className="btn btn-secondary btn-xs"
                        type="button"
                        disabled={!canRead || releaseReadinessState.loading}
                        onClick={() => selectReleaseReadinessApproval(approval)}
                      >
                        <FileText size={14} />
                        {approval.status === 'PENDING' ? '编辑' : '流转'}
                      </button>
                    </div>
                  </div>
                )) : (
                  <div className="notice info">暂无发布准出审批记录</div>
                )}
              </div>
              <div className="test-design-release-readiness-note-form">
                <label className="field">
                  <span className="field-label">备注类型</span>
                  <select
                    value={releaseReadinessDraft.noteType}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, noteType: event.target.value === 'WORK_ORDER' ? 'WORK_ORDER' : 'COMMENT' }))}
                    disabled={!canPublish || releaseReadinessState.loading || !selectedReleaseReadinessApprovalId}
                  >
                    <option value="COMMENT">COMMENT</option>
                    <option value="WORK_ORDER">WORK_ORDER</option>
                  </select>
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">流转备注</span>
                  <textarea
                    value={releaseReadinessDraft.noteText}
                    maxLength={1000}
                    rows={2}
                    onChange={(event) => setReleaseReadinessDraft((current) => ({ ...current, noteText: event.target.value }))}
                    disabled={!canPublish || releaseReadinessState.loading || !selectedReleaseReadinessApprovalId}
                  />
                </label>
                <button
                  className="btn btn-secondary btn-sm test-design-release-readiness-submit"
                  type="button"
                  disabled={!canPublish || releaseReadinessState.loading || !selectedReleaseReadinessApprovalId || !releaseReadinessDraft.noteText.trim()}
                  onClick={() => void addReleaseReadinessNote()}
                >
                  <Plus size={15} />
                  追加备注
                </button>
              </div>
              <div className="test-design-release-readiness-notes">
                <strong>备注流转 · {selectedReleaseReadinessApproval?.workOrderKey ?? (selectedReleaseReadinessApprovalId || '-')}</strong>
                {selectedReleaseReadinessApprovalId ? (
                  releaseReadinessNotes.length ? releaseReadinessNotes.slice(-6).map((note) => (
                    <div className="test-design-release-readiness-note" key={note.id}>
                      <span className="badge badge-neutral">{note.noteType}</span>
                      <em>{note.noteText}</em>
                      <small>{note.createdBy ?? '-'} · {note.createdAt ?? '-'}</small>
                    </div>
                  )) : (
                    <div className="notice info">暂无备注流转记录</div>
                  )
                ) : (
                  <div className="notice info">未选择发布准出审批记录</div>
                )}
              </div>
              <StateLine state={releaseReadinessState} />
            </div>
            <div className="test-design-release-readiness-panel">
              <div className="test-design-release-readiness-heading">
                <span>报告归档</span>
                <div className="toolbar-actions">
                  {selectedReportArchive && (
                    <span className={`badge badge-${reportArchiveStatusTone(selectedReportArchive.status)}`}>
                      {selectedReportArchive.status}
                    </span>
                  )}
                  <button
                    className="btn btn-secondary btn-xs"
                    type="button"
                    disabled={!canRead || reportArchiveState.loading || !selectedTaskId}
                    onClick={() => void refreshReportArchives(selectedTaskId)}
                  >
                    <RefreshCw size={14} />
                    刷新
                  </button>
                </div>
              </div>
              <div className="detail-grid">
                <Detail label="归档记录" value={reportArchives.length} />
                <Detail label="归档审批" value={selectedReportArchive?.archiveApprovalStatus ?? '-'} />
                <Detail label="外发审批" value={selectedReportArchive?.externalApprovalStatus ?? '-'} />
                <Detail label="完整性索引" value={reportArchiveIntegrity ? `${reportArchiveIntegrity.indexedRowCount}/${reportArchiveIntegrity.reportRowCount}` : '-'} />
              </div>
              <div className="test-design-release-readiness-approvals">
                {reportArchives.length ? reportArchives.slice(0, 5).map((archive) => (
                  <div className={`test-design-release-readiness-approval${selectedReportArchiveId === archive.id ? ' selected' : ''}`} key={archive.id}>
                    <div>
                      <strong>{archive.storageBackend ?? 'DATABASE'} · {archive.contentSizeBytes} bytes</strong>
                      <em>行 {archive.reportRowCount} · 索引 {archive.lineIntegrityCount} · 保留至 {archive.retentionUntil ?? '-'}</em>
                      <small>{archive.contentDigest ? `sha256:${archive.contentDigest.slice(0, 12)}` : '-'} · 内容存储 {archive.archiveContentStored ? 'ready' : 'pending'}</small>
                    </div>
                    <div className="test-design-release-readiness-approval-actions">
                      <span className={`badge badge-${reportArchiveStatusTone(archive.status)}`}>{archive.status}</span>
                      <button
                        className="btn btn-secondary btn-xs"
                        type="button"
                        disabled={!canRead || reportArchiveState.loading}
                        onClick={() => selectReportArchive(archive)}
                      >
                        <FileText size={14} />
                        查看
                      </button>
                    </div>
                  </div>
                )) : (
                  <div className="notice info">暂无报告归档记录</div>
                )}
              </div>
              <form className="test-design-release-readiness-form" onSubmit={requestReportArchiveApproval}>
                <label className="field">
                  <span className="field-label">审批类型</span>
                  <select
                    value={reportArchiveDraft.approvalType}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, approvalType: event.target.value === 'EXTERNAL_SHARE' ? 'EXTERNAL_SHARE' : 'ARCHIVE' }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId}
                  >
                    {reportArchiveApprovalTypes.map((type) => (
                      <option key={type} value={type}>{type}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">申请原因</span>
                  <select
                    value={reportArchiveDraft.reasonCode}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, reasonCode: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId}
                  >
                    {reportArchiveReasonCodes.map((code) => (
                      <option key={code} value={code}>{code}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">工单编号</span>
                  <input
                    value={reportArchiveDraft.workOrderKey}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, workOrderKey: event.target.value }))}
                    placeholder="WP5-ARCH-..."
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId}
                  />
                </label>
                <label className="field">
                  <span className="field-label">工单 URL</span>
                  <input
                    value={reportArchiveDraft.workOrderUrl}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, workOrderUrl: event.target.value }))}
                    placeholder="https://..."
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId}
                  />
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">申请摘要</span>
                  <textarea
                    value={reportArchiveDraft.requestSummary}
                    maxLength={1000}
                    rows={3}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, requestSummary: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId}
                  />
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">申请备注</span>
                  <textarea
                    value={reportArchiveDraft.requestNote}
                    maxLength={1000}
                    rows={2}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, requestNote: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId}
                  />
                </label>
                <button
                  className="btn btn-secondary btn-sm test-design-release-readiness-submit"
                  type="submit"
                  disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveId || !reportArchiveDraft.requestSummary.trim()}
                >
                  <Save size={15} />
                  提交审批
                </button>
              </form>
              <div className="test-design-release-readiness-review-grid">
                <label className="field">
                  <span className="field-label">审批原因</span>
                  <select
                    value={reportArchiveDraft.approvalReasonCode}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, approvalReasonCode: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedPendingReportArchiveApproval}
                  >
                    {reportArchiveReasonCodes.map((code) => (
                      <option key={code} value={code}>{code}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">工单状态</span>
                  <select
                    value={reportArchiveDraft.workOrderStatus}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, workOrderStatus: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedPendingReportArchiveApproval}
                  >
                    <option value="">跟随审批</option>
                    {reportArchiveWorkOrderStatuses.map((status) => (
                      <option key={status} value={status}>{status}</option>
                    ))}
                  </select>
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">审批备注</span>
                  <textarea
                    value={reportArchiveDraft.reviewNote}
                    maxLength={1000}
                    rows={2}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, reviewNote: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedPendingReportArchiveApproval}
                  />
                </label>
                <div className="toolbar-actions test-design-release-readiness-submit">
                  <button
                    className="btn btn-secondary btn-sm"
                    type="button"
                    disabled={!canExport || reportArchiveState.loading || !selectedPendingReportArchiveApproval}
                    onClick={() => selectedPendingReportArchiveApproval && void reviewReportArchiveApproval(selectedPendingReportArchiveApproval.id, 'approve')}
                  >
                    <CheckCircle2 size={15} />
                    通过
                  </button>
                  <button
                    className="btn btn-ghost btn-sm"
                    type="button"
                    disabled={!canExport || reportArchiveState.loading || !selectedPendingReportArchiveApproval}
                    onClick={() => selectedPendingReportArchiveApproval && void reviewReportArchiveApproval(selectedPendingReportArchiveApproval.id, 'reject')}
                  >
                    <XCircle size={15} />
                    驳回
                  </button>
                </div>
              </div>
              <div className="test-design-release-readiness-approvals">
                {reportArchiveApprovals.length ? reportArchiveApprovals.slice(0, 6).map((approval) => (
                  <div className={`test-design-release-readiness-approval${selectedReportArchiveApprovalId === approval.id ? ' selected' : ''}`} key={approval.id}>
                    <div>
                      <strong>{approval.workOrderKey ?? approval.id}</strong>
                      <em>{approval.approvalType} · {approval.workOrderStatus ?? '-'}</em>
                      <small>{approval.requestSummaryDigest ? `sha256:${approval.requestSummaryDigest.slice(0, 12)}` : '-'} · 备注 {approval.noteCount ?? 0}</small>
                      {approval.latestNotePreview ? <small>最新备注：{approval.latestNotePreview}</small> : null}
                    </div>
                    <div className="test-design-release-readiness-approval-actions">
                      <span className={`badge badge-${reportArchiveStatusTone(approval.status)}`}>{approval.status}</span>
                      <button
                        className="btn btn-secondary btn-xs"
                        type="button"
                        disabled={!canRead || reportArchiveState.loading}
                        onClick={() => selectReportArchiveApproval(approval)}
                      >
                        <FileText size={14} />
                        流转
                      </button>
                    </div>
                  </div>
                )) : (
                  <div className="notice info">暂无归档审批工单</div>
                )}
              </div>
              <div className="test-design-release-readiness-note-form">
                <label className="field">
                  <span className="field-label">备注类型</span>
                  <select
                    value={reportArchiveDraft.noteType}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, noteType: event.target.value === 'WORK_ORDER' ? 'WORK_ORDER' : 'COMMENT' }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveApprovalId}
                  >
                    <option value="COMMENT">COMMENT</option>
                    <option value="WORK_ORDER">WORK_ORDER</option>
                  </select>
                </label>
                <label className="field test-design-release-readiness-wide">
                  <span className="field-label">流转备注</span>
                  <textarea
                    value={reportArchiveDraft.noteText}
                    maxLength={1000}
                    rows={2}
                    onChange={(event) => setReportArchiveDraft((current) => ({ ...current, noteText: event.target.value }))}
                    disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveApprovalId}
                  />
                </label>
                <button
                  className="btn btn-secondary btn-sm test-design-release-readiness-submit"
                  type="button"
                  disabled={!canExport || reportArchiveState.loading || !selectedReportArchiveApprovalId || !reportArchiveDraft.noteText.trim()}
                  onClick={() => void addReportArchiveNote()}
                >
                  <Plus size={15} />
                  追加备注
                </button>
              </div>
              <div className="test-design-release-readiness-notes">
                <strong>备注流转 · {selectedReportArchiveApproval?.workOrderKey ?? (selectedReportArchiveApprovalId || '-')}</strong>
                {selectedReportArchiveApprovalId ? (
                  reportArchiveNotes.length ? reportArchiveNotes.slice(-6).map((note) => (
                    <div className="test-design-release-readiness-note" key={note.id}>
                      <span className="badge badge-neutral">{note.noteType}</span>
                      <em>{note.noteText}</em>
                      <small>{note.createdBy ?? '-'} · {note.createdAt ?? '-'}</small>
                    </div>
                  )) : (
                    <div className="notice info">暂无归档备注</div>
                  )
                ) : (
                  <div className="notice info">未选择归档审批工单</div>
                )}
              </div>
              <StateLine state={reportArchiveState} />
            </div>
            {publishResult && (
              <>
                <div className="toolbar-actions test-design-export-actions">
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canExport} onClick={exportPublishResult}>
                    <Download size={15} />
                    导出发布摘要
                  </button>
                </div>
                <div className="detail-grid">
                  <Detail label="总数" value={publishResult.total} />
                  <Detail label="创建" value={publishResult.created} />
                  <Detail label="跳过" value={publishResult.skipped} />
                  <Detail label="失败" value={publishResult.failed} />
                  <Detail label="用例" value={publishResult.createdCaseIds.join(', ') || '-'} />
                </div>
                {publishIssueRecords.length > 0 && (
                  <div className="notice warning test-design-publish-issues">
                    {publishIssueRecords.slice(0, 4).map((record) => (
                      <span key={`${record.candidateId}-${record.result}-${record.errorMessage ?? ''}`}>
                        {record.title ?? record.candidateId ?? '-'}：{record.result}{record.errorMessage ? ` · ${record.errorMessage}` : ''}
                      </span>
                    ))}
                  </div>
                )}
                {resolvableConflictRecords.length > 0 && (
                  <div className="test-design-conflict-panel">
                    <div className="test-design-conflict-heading">
                      <span>冲突处理 {resolvableConflictRecords.length} 条</span>
                      <div className="toolbar-actions">
                        <button
                          className="btn btn-secondary btn-xs"
                          type="button"
                          disabled={!canPublish || publishState.loading || !batchResolvableConflictItems.length}
                          onClick={requestBatchResolveConflicts}
                        >
                          <Link2 size={14} />
                          批量复用 {batchResolvableConflictItems.length}
                        </button>
                        <span className="badge badge-warning">需人工确认</span>
                      </div>
                    </div>
                    <div className="test-design-conflict-form">
                      <label className="field">
                        <span className="field-label">处理原因</span>
                        <input
                          value={conflictResolutionDraft.reason}
                          onChange={(event) => setConflictResolutionDraft((current) => ({ ...current, reason: event.target.value }))}
                          disabled={!canPublish || publishState.loading}
                        />
                      </label>
                      <label className="field">
                        <span className="field-label">用例关键词</span>
                        <input
                          value={conflictCaseKeyword}
                          onChange={(event) => setConflictCaseKeyword(event.target.value)}
                          placeholder="标题 / 标签 / 编号"
                          disabled={!canRead || publishState.loading || !conflictCaseSearchProjectId}
                        />
                      </label>
                      <label className="field">
                        <span className="field-label">补充说明</span>
                        <input
                          value={conflictResolutionDraft.comment}
                          onChange={(event) => setConflictResolutionDraft((current) => ({ ...current, comment: event.target.value }))}
                          placeholder="比对说明"
                          disabled={!canPublish || publishState.loading}
                        />
                      </label>
                      <div className="field test-design-conflict-search-action">
                        <span className="field-label">既有用例</span>
                        <button
                          className="btn btn-secondary btn-sm"
                          type="button"
                          disabled={!canRead || publishState.loading || !conflictCaseSearchProjectId}
                          onClick={() => void searchConflictCases()}
                        >
                          <Search size={15} />
                          搜索
                        </button>
                      </div>
                    </div>
                    <div className="test-design-conflict-list">
                      {resolvableConflictRecords.map((record) => {
                        const candidate = conflictResolutionCandidate(record, conflictCandidateById);
                        const targetCaseId = conflictResolutionTargetCaseId(record, selectedConflictCaseIds);
                        return (
                          <div className="test-design-conflict-row" key={publishRecordKey(record)}>
                            <span>
                              <strong>{record.title ?? record.candidateId ?? '-'}</strong>
                              <em>{targetCaseId ? `目标用例 ${targetCaseId}` : '目标用例 -'}</em>
                              {candidate && <em>候选 {candidate.status}@v{candidate.version}</em>}
                              {record.errorMessage && <small>{record.errorMessage}</small>}
                              {!candidate && <small>发布记录缺少候选版本，重新预发布后可处理。</small>}
                            </span>
                            <div className="test-design-conflict-controls">
                              <select
                                value={targetCaseId}
                                onChange={(event) => {
                                  const nextCaseId = event.target.value;
                                  const candidateId = record.candidateId;
                                  if (candidateId) {
                                    setSelectedConflictCaseIds((current) => ({
                                      ...current,
                                      [candidateId]: nextCaseId
                                    }));
                                  }
                                }}
                                disabled={!canPublish || publishState.loading}
                              >
                                <option value="">{record.assetCaseId ? '清空目标' : '选择目标用例'}</option>
                                {record.assetCaseId && (
                                  <option value={record.assetCaseId}>推荐 {shortIdentifier(record.assetCaseId)}</option>
                                )}
                                {conflictCaseResults.filter((testCase) => testCase.id !== record.assetCaseId).map((testCase) => (
                                  <option value={testCase.id} key={`${record.candidateId}-${testCase.id}`}>
                                    {testCase.title || shortIdentifier(testCase.id)}
                                  </option>
                                ))}
                              </select>
                              <button
                                className="btn btn-secondary btn-xs"
                                type="button"
                                disabled={!canPublish || publishState.loading || !candidate || !targetCaseId}
                                onClick={() => requestResolveConflict(record)}
                              >
                                <Link2 size={14} />
                                复用
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
                {publishResult.records.length > 0 && (
                  <div className="test-design-publish-records">
                    {publishResult.records.slice(0, 6).map((record) => (
                      <PublishRecordRow key={publishRecordKey(record)} record={record} />
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">范围</h2>
              <p className="panel-desc">本次生成输入。</p>
            </div>
          </div>
          <div className="panel-body compact">
            {selectedRequirementTitles.length ? (
              <div className="test-design-scope">
                {selectedRequirementTitles.map((title) => <span className="badge badge-info" key={title}>{title}</span>)}
              </div>
            ) : (
              <div className="notice info">尚未选择需求</div>
            )}
          </div>
        </section>
      </aside>
      </div>
    </>
  );
}
