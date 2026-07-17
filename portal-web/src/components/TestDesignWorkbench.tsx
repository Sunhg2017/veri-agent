import {
  ClipboardCheck,
  FileText,
  Sparkles
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent
} from 'react';
import { Tabs } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/auth';
import {
  fetchAssetRequirements,
  fetchAssetTestCases,
  type AssetRequirementView,
  type AssetTestCaseView
} from '../api/assets';
import {
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
import { TestDesignCandidateReviewPanel } from './TestDesignCandidateReviewPanel';
import { TestDesignConflictOperationsPanel } from './TestDesignConflictOperationsPanel';
import { TestDesignContextPolicyPanel } from './TestDesignContextPolicyPanel';
import {
  EvaluationCorpusOperationsPanel,
  type CalibrationRunDraft,
  type EvaluationSampleDraft,
  type EvaluationSampleFilters
} from './TestDesignEvaluationCorpusPanel';
import { TestDesignRequirementSelectionPanel } from './TestDesignRequirementSelectionPanel';
import { TestDesignReviewHistoryPanel } from './TestDesignReviewHistoryPanel';
import { TestDesignScopePanel } from './TestDesignScopePanel';
import { TestDesignPublishPanel } from './TestDesignPublishPanel';
import {
  TestDesignGenerationConfigPanel,
  TestDesignTaskDiagnosticsPanel,
  TestDesignTaskListPanel
} from './TestDesignTaskSidebarPanels';
import { TestDesignTemplateManagementPanel } from './TestDesignTemplateManagementPanel';
import {
  ConfirmationDialog,
  assetCaseTraceHref,
  calibrationStatusTone,
  publishRecordKey,
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
import { translate } from '../platform/i18n';
import { PageHeader } from './PageHeader';

/**
 * 测试设计子页面：将原单一堆叠页拆分为 6 个功能子页，
 * 通过顶部 Tabs + 嵌套路由切换，状态仍由本组件统一持有。
 */
const testDesignSubPages = [
  { key: 'tasks', label: translate('nav.tdTasks') },
  { key: 'candidates', label: translate('nav.tdCandidates') },
  { key: 'publish', label: translate('nav.tdPublish') },
  { key: 'quality', label: translate('nav.tdQuality') },
  { key: 'policies', label: translate('nav.tdPolicies') },
  { key: 'operations', label: translate('nav.tdOperations') }
] as const;

type TestDesignSubPage = (typeof testDesignSubPages)[number]['key'];

function resolveTestDesignSubPage(pathname: string): TestDesignSubPage {
  const segment = pathname.replace(/^\/+/, '').split('/')[1] ?? '';
  return (testDesignSubPages.some((page) => page.key === segment) ? segment : 'tasks') as TestDesignSubPage;
}

export function TestDesignWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'testDesign:read');
  const canGenerate = canUseButton(props.currentUser, 'testDesign:generate');
  const canReview = canUseButton(props.currentUser, 'testDesign:review');
  const canPublish = canUseButton(props.currentUser, 'testDesign:publish');
  const canExport = canUseButton(props.currentUser, 'testDesign:export');
  const canPolicyManage = canUseButton(props.currentUser, 'testDesign:policy_manage');

  const location = useLocation();
  const navigate = useNavigate();
  const activeSubPage = resolveTestDesignSubPage(location.pathname);
  const subPageTabs = useMemo(() => testDesignSubPages.map((page) => ({ key: page.key, label: page.label })), []);

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
      ? translate('auto.k1642', { value0: reviewRecordPage.start, value1: reviewRecordPage.end, value2: reviewRecordPage.total })
      : translate('auto.k1643', { value0: reviewRecordPage.total })
    : translate('auto.k1538');
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
      ? translate('auto.k1644', { value0: taskQualitySummary.total })
      : candidatePage.items.length
        ? translate('auto.k1645', { value0: candidatePage.start, value1: candidatePage.end, value2: candidatePage.total })
        : translate('auto.k1646', { value0: candidatePage.total })
    : translate('auto.k1538');
  const publishScopeLabel = selectedCandidateIds.length
    ? translate('auto.k1647', { value0: selectedPublishableCandidates.length, value1: selectedCandidateIds.length })
    : translate('auto.k1648', { value0: estimatedPublishableCandidateCount ? translate('auto.k2604', { value0: estimatedPublishableCandidateCount }) : '' });
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
      setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1649')) });
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
        setPromptTrendState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1650')) });
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
        success: translate('auto.k1651', { value0: sampleResponse.data.items.length, value1: sampleResponse.data.total, value2: calibrationResponse.data.total }),
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
        setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1652')) });
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
        success: translate('auto.k1653', { value0: dashboardResponse.data.taskCount, value1: dashboardResponse.data.auditOutbox?.replayEligibleCount ?? 0, value2: detailReportResponse.data.rowCount }),
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
        setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1654')) });
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
        setConflictOperationState({ loading: false, error: translate('auto.k1386') });
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
        success: translate('auto.k1655', { value0: response.data.items.length, value1: response.data.total }),
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      if (!silent) {
        setConflictOperations([]);
        setConflictOperationPageTotal(0);
        setConflictOperationSummary(null);
        setConflictOperationState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1656')) });
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
        setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1657')) });
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
        setTaskAuditState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1658')) });
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
          success: translate('auto.k1659', { value0: overridesResponse.data.length }),
          traceId: effectiveResponse.trace_id || overridesResponse.trace_id
        });
      }
    } catch (error: unknown) {
      setContextPolicyOverrides([]);
      setContextPolicyEffective(null);
      if (!silent) {
        setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1660')) });
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
          success: translate('auto.k1661', { value0: approvals.length }),
          traceId: response.trace_id
        });
      }
    } catch (error: unknown) {
      setReleaseReadinessApprovals([]);
      setSelectedReleaseReadinessApprovalId('');
      setReleaseReadinessNotes([]);
      if (!silent) {
        setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1662')) });
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
      setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1663')) });
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
          success: translate('auto.k1664', { value0: archives.length }),
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
        setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1665')) });
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
          success: translate('auto.k1666', { value0: approvals.length }),
          traceId: integrityResponse.trace_id || approvalsResponse.trace_id
        });
      }
    } catch (error: unknown) {
      setReportArchiveIntegrity(null);
      setReportArchiveApprovals([]);
      setSelectedReportArchiveApprovalId('');
      setReportArchiveNotes([]);
      if (!silent) {
        setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1667')) });
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
      setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1668')) });
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
        setTemplateState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1669')) });
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
      setReviewRecordState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1670')) });
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
      errors.push(testDesignErrorMessage(healthResult.reason, translate('auto.k1671')));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(testDesignErrorMessage(requirementResult.reason, translate('auto.k1672')));
    }

    if (taskResult.status === 'fulfilled') {
      setTasks(taskResult.value.data.items);
      traceIds.push(taskResult.value.trace_id);
      setSelectedTaskId((current) => taskResult.value.data.items.some((task) => task.id === current) ? current : taskResult.value.data.items[0]?.id || '');
    } else {
      setTasks([]);
      errors.push(testDesignErrorMessage(taskResult.reason, translate('auto.k1673')));
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
        emptyStepDraft(translate('auto.k1674'), translate('auto.k1675')),
        emptyStepDraft(translate('auto.k1676'), translate('auto.k1677')),
        emptyStepDraft(translate('auto.k1678'), translate('auto.k1679'))
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
      setMutationState({ loading: false, error: translate('auto.k1680') });
      return;
    }
    if (!canGenerate) {
      setMutationState({ loading: false, error: translate('auto.k1681') });
      return;
    }
    if (!generationDraft.projectId.trim()) {
      setMutationState({ loading: false, error: translate('auto.k1682') });
      return;
    }
    if (!selectedRequirementIds.length) {
      setMutationState({ loading: false, error: translate('auto.k1683') });
      return;
    }
    if (!generationDraft.coverageTypes.length) {
      setMutationState({ loading: false, error: translate('auto.k1684') });
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
        success: ASYNC_TASK_STATUSES.has(response.data.task.status) ? translate('auto.k1685') : translate('auto.k1686'),
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1687')) });
    }
  }

  async function saveTemplate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setTemplateState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!templateDraft.name.trim()) {
      setTemplateState({ loading: false, error: translate('auto.k1689') });
      return;
    }
    if (!templateDraft.coverageTypes.length) {
      setTemplateState({ loading: false, error: translate('auto.k1690') });
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
        success: selectedManagedTemplate ? translate('auto.k1691') : translate('auto.k1692'),
        traceId: response.trace_id
      });
      void refreshTemplates({ silent: true });
    } catch (error: unknown) {
      setTemplateState({ loading: false, error: testDesignErrorMessage(error, selectedManagedTemplate ? translate('auto.k1693') : translate('auto.k1694')) });
    }
  }

  async function disableTemplate() {
    if (!selectedManagedTemplate) {
      setTemplateState({ loading: false, error: translate('auto.k1695') });
      return;
    }
    if (!canPolicyManage) {
      setTemplateState({ loading: false, error: translate('auto.k1688') });
      return;
    }

    setTemplateState({ loading: true });
    try {
      const response = await deleteTestDesignTemplate(selectedManagedTemplate.id);
      setTemplates((current) => upsertTemplate(current, response.data));
      setGenerationDraft((current) => current.templateId === response.data.id ? { ...current, templateId: '' } : current);
      setTemplateState({ loading: false, success: translate('auto.k1696'), traceId: response.trace_id });
      void refreshTemplates({ silent: true });
    } catch (error: unknown) {
      setTemplateState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1697')) });
    }
  }

  async function saveEvaluationSample(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!evaluationSampleDraft.projectId.trim()) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1698') });
      return;
    }
    if (!evaluationSampleDraft.title.trim() || !evaluationSampleDraft.requirementSummary.trim()
        || !evaluationSampleDraft.expectedCaseOutline.trim()) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1699') });
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
        success: selectedEvaluationSample ? translate('auto.k1700') : translate('auto.k1701'),
        traceId: response.trace_id
      });
      void refreshEvaluationCorpusOperations({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1702')) });
    }
  }

  async function transitionEvaluationSample(status: string) {
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!selectedEvaluationSample) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1703') });
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
      setEvaluationCorpusState({ loading: false, success: translate('auto.k1704', { value0: status }), traceId: response.trace_id });
      void refreshEvaluationCorpusOperations({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1705')) });
    }
  }

  async function extractEvaluationSampleFromCandidate() {
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!selectedCandidateId) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1706') });
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
      setEvaluationCorpusState({ loading: false, success: translate('auto.k1707'), traceId: response.trace_id });
      void refreshEvaluationCorpusOperations({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1708')) });
    }
  }

  async function runCalibration() {
    if (!canPolicyManage) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!calibrationRunDraft.projectId.trim()) {
      setEvaluationCorpusState({ loading: false, error: translate('auto.k1709') });
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
      setEvaluationCorpusState({ loading: false, success: translate('auto.k1710', { value0: response.data.status }), traceId: response.trace_id });
      void refreshEvaluationCorpusOperations({ silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setEvaluationCorpusState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1711')) });
    }
  }

  async function requeueAuditOutbox(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    const projectId = auditOutboxRequeueDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1712') });
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
        success: translate('auto.k1713', { value0: response.data.requeuedCount }),
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1714')) });
    }
  }

  async function saveQueueAlertSubscription(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    const projectId = queueAlertSubscriptionDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1715') });
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
        success: translate('auto.k1716', { value0: response.data.alertType }),
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1717')) });
    }
  }

  async function replayQueuedEvents(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    const projectId = queuedEventReplayDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1718') });
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
        success: translate('auto.k1719', { value0: response.data.generationTaskEvents, value1: response.data.publishCandidateEvents }),
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1720')) });
    }
  }

  async function runPublishCompensation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    const projectId = publishCompensationRunDraft.projectId.trim() || crossWpOperationsProjectId.trim();
    if (!projectId) {
      setCrossWpOperationsState({ loading: false, error: translate('auto.k1721') });
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
        success: translate('auto.k1722', { value0: response.data.scannedCandidates, value1: response.data.succeededCandidates }),
        traceId: response.trace_id
      });
      void refreshCrossWpOperations({ silent: true });
    } catch (error: unknown) {
      setCrossWpOperationsState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1723')) });
    }
  }

  async function retryTask(task: TestDesignTaskView) {
    if (!canGenerate) {
      setTaskState({ loading: false, error: translate('auto.k1681') });
      return;
    }
    if (!RETRYABLE_TASK_STATUSES.has(task.status)) {
      setTaskState({ loading: false, error: translate('auto.k1724', { value0: task.status }) });
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
      setTaskState({ loading: false, success: translate('auto.k1725'), traceId: response.trace_id });
      void refreshReviewRecords(task.id, { silent: true });
      void refreshTaskQualitySummary(task.id, { silent: true });
      void refreshTaskAuditSummary(task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1726')) });
    }
  }

  async function replayQueuedTaskEvent(task: TestDesignTaskView) {
    if (!canGenerate) {
      setTaskState({ loading: false, error: translate('auto.k1681') });
      return;
    }
    if (task.status !== 'QUEUED') {
      setTaskState({ loading: false, error: translate('auto.k1727', { value0: task.status }) });
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
      setTaskState({ loading: false, success: translate('auto.k1728'), traceId: response.trace_id });
      void refreshReviewRecords(task.id, { silent: true });
      void refreshTaskQualitySummary(task.id, { silent: true });
      void refreshTaskAuditSummary(task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1729')) });
    }
  }

  async function cancelTask(task: TestDesignTaskView) {
    if (!canGenerate) {
      setTaskState({ loading: false, error: translate('auto.k1681') });
      return;
    }
    if (!CANCELLABLE_TASK_STATUSES.has(task.status)) {
      setTaskState({ loading: false, error: translate('auto.k1730', { value0: task.status }) });
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
      setTaskState({ loading: false, success: translate('auto.k1731'), traceId: response.trace_id });
      void refreshReviewRecords(task.id, { silent: true });
      void refreshTaskQualitySummary(task.id, { silent: true });
      void refreshTaskAuditSummary(task.id, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1732')) });
    }
  }

  async function requestContextPolicyOverride(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canPolicyManage) {
      setContextPolicyState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (contextPolicySubmitBlocked) {
      setContextPolicyState({ loading: false, error: translate('auto.k1733', { value0: contextPolicySubmitIssues[0]?.message ?? translate('auto.k2601') }) });
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
        success: selectedPendingContextPolicyOverride ? translate('auto.k1734') : translate('auto.k1735'),
        traceId: response.trace_id
      });
      void loadContextPolicyNotes(response.data.id, { silent: true });
      void refreshContextPolicy({ silent: true });
    } catch (error: unknown) {
      setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1736')) });
    }
  }

  async function reviewContextPolicyOverride(overrideId: string, action: 'approve' | 'reject') {
    if (!canPolicyManage) {
      setContextPolicyState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!contextPolicyDraft.approvalReasonCode) {
      setContextPolicyState({ loading: false, error: translate('auto.k1737') });
      return;
    }
    const reviewIssue = contextPolicyIssues.find((issue) => ['approvalReasonCode', 'reviewNote', 'workOrderStatus'].includes(issue.field));
    if (reviewIssue) {
      setContextPolicyState({ loading: false, error: translate('auto.k1738', { value0: reviewIssue.message }) });
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
        success: action === 'approve' ? translate('auto.k1739') : translate('auto.k1740'),
        traceId: response.trace_id
      });
      void loadContextPolicyNotes(response.data.id, { silent: true });
      void refreshContextPolicy({ silent: true });
    } catch (error: unknown) {
      setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, action === 'approve' ? translate('auto.k1741') : translate('auto.k1742')) });
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
        setContextPolicyState({ loading: false, success: translate('auto.k1743', { value0: response.data.length }), traceId: response.trace_id });
      }
    } catch (error: unknown) {
      setContextPolicyNotes([]);
      if (!silent) {
        setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1744')) });
      }
    }
  }

  async function addContextPolicyNote() {
    if (!canPolicyManage) {
      setContextPolicyState({ loading: false, error: translate('auto.k1688') });
      return;
    }
    if (!selectedContextPolicyOverrideId) {
      setContextPolicyState({ loading: false, error: translate('auto.k1745') });
      return;
    }
    const noteIssue = contextPolicyIssues.find((issue) => issue.field === 'noteText');
    if (noteIssue || !contextPolicyDraft.noteText.trim()) {
      setContextPolicyState({ loading: false, error: noteIssue?.message ?? translate('auto.k1746') });
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
      setContextPolicyState({ loading: false, success: translate('auto.k1747'), traceId: response.trace_id });
      void refreshContextPolicy({ silent: true });
    } catch (error: unknown) {
      setContextPolicyState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1748')) });
    }
  }

  async function requestReleaseReadinessApproval(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedTaskId) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1538') });
      return;
    }
    if (!canPublish) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!releaseReadinessDraft.exceptionSummary.trim() || !releaseReadinessDraft.riskMitigation.trim()) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1750') });
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
        success: selectedReleaseReadinessApproval?.status === 'PENDING' ? translate('auto.k1751') : translate('auto.k1752'),
        traceId: response.trace_id
      });
      void refreshReleaseReadinessApprovals(selectedTaskId, { silent: true });
      void refreshReleaseReadinessNotes(response.data.id);
    } catch (error: unknown) {
      setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1753')) });
    }
  }

  async function reviewReleaseReadinessApproval(approvalId: string, action: 'approve' | 'reject') {
    if (!canPublish) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!releaseReadinessDraft.approvalReasonCode) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1737') });
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
        success: action === 'approve' ? translate('auto.k1754') : translate('auto.k1755'),
        traceId: response.trace_id
      });
      void refreshReleaseReadinessNotes(response.data.id);
    } catch (error: unknown) {
      setReleaseReadinessState({
        loading: false,
        error: testDesignErrorMessage(error, action === 'approve' ? translate('auto.k1756') : translate('auto.k1757'))
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
      setReleaseReadinessState({ loading: false, error: translate('auto.k1758') });
      return;
    }
    if (!canPublish) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!releaseReadinessDraft.noteText.trim()) {
      setReleaseReadinessState({ loading: false, error: translate('auto.k1746') });
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
      setReleaseReadinessState({ loading: false, success: translate('auto.k1759'), traceId: response.trace_id });
    } catch (error: unknown) {
      setReleaseReadinessState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1760')) });
    }
  }

  async function requestReportArchiveApproval(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedReportArchiveId) {
      setReportArchiveState({ loading: false, error: translate('auto.k1761') });
      return;
    }
    if (!canExport) {
      setReportArchiveState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (!reportArchiveDraft.requestSummary.trim()) {
      setReportArchiveState({ loading: false, error: translate('auto.k1763') });
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
      setReportArchiveState({ loading: false, success: translate('auto.k1764'), traceId: response.trace_id });
      void refreshReportArchiveDetail(selectedReportArchiveId, { silent: true });
      void refreshReportArchiveNotes(response.data.id);
    } catch (error: unknown) {
      setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1765')) });
    }
  }

  async function reviewReportArchiveApproval(approvalId: string, action: 'approve' | 'reject') {
    if (!canExport) {
      setReportArchiveState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (!reportArchiveDraft.approvalReasonCode) {
      setReportArchiveState({ loading: false, error: translate('auto.k1737') });
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
        success: action === 'approve' ? translate('auto.k1766') : translate('auto.k1767'),
        traceId: response.trace_id
      });
      void refreshReportArchives(selectedTaskId, { silent: true });
      void refreshReportArchiveDetail(response.data.archiveId, { silent: true });
      void refreshReportArchiveNotes(response.data.id);
    } catch (error: unknown) {
      setReportArchiveState({
        loading: false,
        error: testDesignErrorMessage(error, action === 'approve' ? translate('auto.k1768') : translate('auto.k1769'))
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
      setReportArchiveState({ loading: false, error: translate('auto.k1770') });
      return;
    }
    if (!canExport) {
      setReportArchiveState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (!reportArchiveDraft.noteText.trim()) {
      setReportArchiveState({ loading: false, error: translate('auto.k1746') });
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
      setReportArchiveState({ loading: false, success: translate('auto.k1771'), traceId: response.trace_id });
    } catch (error: unknown) {
      setReportArchiveState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1772')) });
    }
  }

  async function saveCandidate() {
    if (!selectedCandidate || !candidateDraft) {
      return;
    }
    if (!canReview) {
      setMutationState({ loading: false, error: translate('auto.k1773') });
      return;
    }
    if (candidateSaveBlocked) {
      setMutationState({ loading: false, error: translate('auto.k1774', { value0: candidateQualityIssues[0]?.message ?? translate('auto.k2602') }) });
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
      setMutationState({ loading: false, success: translate('auto.k1775'), traceId: response.trace_id });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
      void refreshTaskAuditSummary(selectedTaskId, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1776')) });
    }
  }

  async function reviewCandidate(action: 'confirm' | 'reject' | 'ignore') {
    if (!selectedCandidate) {
      return;
    }
    if (!canReview) {
      setMutationState({ loading: false, error: translate('auto.k1773') });
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
      setMutationState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1777')) });
    }
  }

  function requestBatchReviewCandidates(action: TestDesignCandidateBatchActionType) {
    if (!canReview) {
      setMutationState({ loading: false, error: translate('auto.k1773') });
      return;
    }
    if (!selectedReviewCandidates.length) {
      setMutationState({ loading: false, error: translate('auto.k1778') });
      return;
    }
    if ((action === 'REJECT' || action === 'IGNORE') && !reviewComment.trim()) {
      setMutationState({ loading: false, error: translate('auto.k1779') });
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
      setMutationState({ loading: false, error: translate('auto.k1773') });
      return;
    }
    if (!selectedReviewCandidates.length) {
      setMutationState({ loading: false, error: translate('auto.k1778') });
      return;
    }
    if ((action === 'REJECT' || action === 'IGNORE') && !reviewComment.trim()) {
      setMutationState({ loading: false, error: translate('auto.k1779') });
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
        success: translate('auto.k1780', { value0: testDesignBatchActionLabel(action), value1: response.data.succeededCount, value2: response.data.failedCount }),
        traceId: response.trace_id
      });
      void refreshCandidatePage(selectedTaskId, { silent: true });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
      void refreshTaskAuditSummary(selectedTaskId, { silent: true });
      void refreshPromptTrend({ silent: true });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1781', { value0: testDesignBatchActionLabel(action) })) });
    }
  }

  function requestBatchEditCandidates() {
    if (!canReview) {
      setMutationState({ loading: false, error: translate('auto.k1773') });
      return;
    }
    if (!selectedBatchEditableCandidates.length) {
      setMutationState({ loading: false, error: translate('auto.k1782') });
      return;
    }
    if (!batchEditHasChanges) {
      setMutationState({ loading: false, error: translate('auto.k1783') });
      return;
    }
    if (batchEditIssues.length) {
      setMutationState({ loading: false, error: translate('auto.k1784', { value0: batchEditIssues[0].message }) });
      return;
    }

    setPendingConfirmation({
      kind: 'batchEdit',
      summary: buildTestDesignBatchEditConfirmation(selectedBatchEditableCandidates, batchEditFieldLabels)
    });
  }

  async function executeBatchEditCandidates() {
    if (!canReview) {
      setMutationState({ loading: false, error: translate('auto.k1773') });
      return;
    }
    if (batchEditBlocked) {
      setMutationState({
        loading: false,
        error: batchEditIssues[0]?.message ?? translate('auto.k1785')
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
          errorMessage: testDesignErrorMessage(error, translate('auto.k1786'))
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
      success: translate('auto.k1787', { value0: result.succeededCount, value1: result.failedCount }),
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
      setPublishState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!canPublishCurrentScope) {
      setPublishState({ loading: false, error: translate('auto.k1788') });
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
      setPublishState({ loading: false, error: translate('auto.k1749') });
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
        success: dryRun ? translate('auto.k1789') : queued ? translate('auto.k1790') : translate('auto.k1791'),
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
      setPublishState({ loading: false, error: testDesignErrorMessage(error, dryRun ? translate('auto.k1792') : translate('auto.k1793')) });
    }
  }

  async function searchConflictCases() {
    if (!canRead) {
      setPublishState({ loading: false, error: translate('auto.k1794') });
      return;
    }
    if (!conflictCaseSearchProjectId) {
      setPublishState({ loading: false, error: translate('auto.k1795') });
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
        success: translate('auto.k1796', { value0: response.data.items.length, value1: response.data.total }),
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1797')) });
    }
  }

  function requestResolveConflict(record: TestDesignPublishRecordView) {
    if (!canPublish) {
      setPublishState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!record.candidateId) {
      setPublishState({ loading: false, error: translate('auto.k1798') });
      return;
    }
    const targetCaseId = conflictResolutionTargetCaseId(record, selectedConflictCaseIds);
    if (!targetCaseId) {
      setPublishState({ loading: false, error: translate('auto.k1799') });
      return;
    }
    const candidate = conflictResolutionCandidate(record, conflictCandidateById);
    if (!candidate) {
      setPublishState({ loading: false, error: translate('auto.k1800') });
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
      setPublishState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!batchResolvableConflictItems.length) {
      setPublishState({ loading: false, error: translate('auto.k1801') });
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
      setPublishState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!batchResolvableConflictOperationItems.length) {
      setPublishState({ loading: false, error: translate('auto.k1802') });
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
      setPublishState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!record.assetCaseId) {
      setPublishState({ loading: false, error: translate('auto.k1803') });
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
        setPublishState({ loading: false, success: translate('auto.k1804'), traceId: response.trace_id });
        return;
      }
      setPublishState({
        loading: false,
        error: response.data.errorMessage ?? translate('auto.k1805')
      });
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1806')) });
    }
  }

  async function executeBatchResolveConflicts(items: ConflictResolutionItem[]) {
    if (!canPublish) {
      setPublishState({ loading: false, error: translate('auto.k1749') });
      return;
    }
    if (!items.length) {
      setPublishState({ loading: false, error: translate('auto.k1801') });
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
      setPublishState({ loading: false, error: translate('auto.k1803') });
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
        errorMessage: item.errorMessage ?? item.record?.errorMessage ?? (item.result === 'SUCCEEDED' ? undefined : translate('auto.k1806'))
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
          success: translate('auto.k1807', { value0: succeededIds.size, value1: items.length }),
          traceId: response.trace_id
        });
        return;
      }
      setPublishState({
        loading: false,
        error: translate('auto.k1808', { value0: succeededIds.size, value1: failedItems.length, value2: failedItems[0]?.errorMessage ?? translate('auto.k2603') })
      });
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1809')) });
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
      setTaskState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (scope === 'page') {
      if (!selectedTaskId) {
        setTaskState({ loading: false, error: translate('auto.k1810') });
        return;
      }
      if (!candidatePage.total) {
        setTaskState({ loading: false, error: translate('auto.k1811') });
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
        setTaskState({ loading: false, success: translate('auto.k1812'), traceId: response.traceId });
      } catch (error: unknown) {
        setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1813')) });
      }
      return;
    }

    const exportCandidates = selectedCandidates;
    if (!exportCandidates.length) {
      setTaskState({ loading: false, error: translate('auto.k1814') });
      return;
    }

    const generatedAt = new Date().toISOString();
    const scopeLabel = translate('auto.k1815', { value0: exportCandidates.length });
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
    setTaskState({ loading: false, success: translate('auto.k1816') });
  }

  function exportPublishResult() {
    if (!canExport) {
      setPublishState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (!publishResult) {
      setPublishState({ loading: false, error: translate('auto.k1817') });
      return;
    }

    const generatedAt = new Date().toISOString();
    const csv = buildTestDesignPublishResultCsv({ task: selectedTask, publishResult, generatedAt });
    downloadText(
      csv,
      buildTestDesignExportFilename(publishResult.dryRun ? 'publish-dry-run' : 'publish-result', publishResult.taskId, generatedAt),
      TEST_DESIGN_EXPORT_CONTENT_TYPE
    );
    setPublishState({ loading: false, success: translate('auto.k1818') });
  }

  async function exportTaskReport() {
    if (!canExport) {
      setTaskState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (!selectedTask) {
      setTaskState({ loading: false, error: translate('auto.k1819') });
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
      setTaskState({ loading: false, success: translate('auto.k1820'), traceId: response.traceId });
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1821')) });
    }
  }

  async function exportReviewRecords() {
    if (!canExport) {
      setReviewRecordState({ loading: false, error: translate('auto.k1762') });
      return;
    }
    if (!selectedTaskId) {
      setReviewRecordState({ loading: false, error: translate('auto.k1822') });
      return;
    }
    if (!reviewRecordPageTotal) {
      setReviewRecordState({ loading: false, error: translate('auto.k1823') });
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
      setReviewRecordState({ loading: false, success: translate('auto.k1824'), traceId: response.traceId });
    } catch (error: unknown) {
      setReviewRecordState({ loading: false, error: testDesignErrorMessage(error, translate('auto.k1825')) });
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
      <PageHeader title={translate('auto.k2817')} description={translate('auto.k0008')} />
      <div className="module-tabs-card">
        <Tabs
          activeKey={activeSubPage}
          items={subPageTabs}
          onChange={(key) => navigate(`/test-design/${key}`)}
        />
      </div>
      <div className={activeSubPage === 'tasks' || activeSubPage === 'publish' ? 'module-layout' : undefined}>
      <div className="main-stack">
        {activeSubPage === 'tasks' && (
        <div className="metrics-grid">
          <Metric icon={<Sparkles size={20} />} label={translate('auto.k1826')} value={health?.status ?? '-'} desc={selectedTask ? generationSourceText(selectedTaskSource) : health?.generationMode ?? translate('auto.k0169')} />
          <Metric icon={<FileText size={20} />} label={translate('auto.k1827')} value={String(candidates.length)} desc={translate('auto.k1828', { value0: statusCounts.CONFIRMED ?? 0, value1: statusCounts.FAILED ?? 0 })} />
          <Metric icon={<ClipboardCheck size={20} />} label={translate('auto.k1829')} value={String(selectedTask?.publishedCount ?? 0)} desc={selectedTask?.status ?? '-'} />
        </div>
        )}

        {activeSubPage === 'quality' && (
        <QualitySummaryPanel
          scopeLabel={qualitySummaryScope}
          selectedTaskId={selectedTaskId}
          summary={qualitySummary}
        />
        )}

        {activeSubPage === 'quality' && (
        <PromptTrendPanel
          state={promptTrendState}
          summary={promptTrendSummary}
          onRefresh={() => void refreshPromptTrend()}
        />
        )}

        {activeSubPage === 'operations' && (
        <>
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
        </>
        )}

        {activeSubPage === 'quality' && (
        <AuditSummaryPanel
          state={taskAuditState}
          summary={auditSummary}
          selectedTaskId={selectedTaskId}
          onRefresh={() => void refreshTaskAuditSummary(selectedTaskId)}
        />
        )}

        {activeSubPage === 'tasks' && (
        <TestDesignRequirementSelectionPanel
          signedIn={props.signedIn}
          canRead={canRead}
          disabled={disabled}
          loadState={loadState}
          filters={filters}
          initialFilters={initialFilters}
          filteredRequirements={filteredRequirements}
          selectedRequirementIds={selectedRequirementIds}
          onRefresh={() => void refreshAll()}
          onFiltersChange={setFilters}
          onSelectedRequirementIdsChange={setSelectedRequirementIds}
          onToggleRequirement={toggleRequirement}
        />
        )}

        {activeSubPage === 'policies' && (
        <TestDesignContextPolicyPanel
          disabled={disabled}
          canRead={canRead}
          canPolicyManage={canPolicyManage}
          state={contextPolicyState}
          summary={contextPolicySummary}
          draft={contextPolicyDraft}
          submitBlocked={contextPolicySubmitBlocked}
          overrides={contextPolicyOverrides}
          selectedOverrideId={selectedContextPolicyOverrideId}
          selectedOverride={selectedContextPolicyOverride}
          selectedPendingOverride={selectedPendingContextPolicyOverride}
          notes={contextPolicyNotes}
          onRefresh={() => void refreshContextPolicy()}
          onNewDraft={newContextPolicyOverrideDraft}
          onDraftChange={setContextPolicyDraft}
          onSubmit={requestContextPolicyOverride}
          onSelectOverride={(override) => void selectContextPolicyOverride(override)}
          onReviewOverride={(overrideId, action) => void reviewContextPolicyOverride(overrideId, action)}
          onAddNote={() => void addContextPolicyNote()}
        />
        )}

        {activeSubPage === 'publish' && (
        <TestDesignConflictOperationsPanel
          canRead={canRead}
          canPublish={canPublish}
          state={conflictOperationState}
          publishState={publishState}
          summary={conflictOperationSummary}
          operations={conflictOperations}
          page={conflictOperationPage}
          projectId={conflictOperationProjectId}
          selectedTaskId={selectedTaskId}
          filters={conflictOperationFilters}
          conflictResolutionDraft={conflictResolutionDraft}
          conflictCaseKeyword={conflictCaseKeyword}
          conflictCaseSearchProjectId={conflictCaseSearchProjectId}
          conflictCaseResults={conflictCaseResults}
          selectedConflictCaseIds={selectedConflictCaseIds}
          conflictCandidateById={conflictCandidateById}
          batchResolvableCount={batchResolvableConflictOperationItems.length}
          onBatchResolve={requestBatchResolveConflictOperations}
          onRefresh={(pageIndex) => void refreshConflictOperations(pageIndex)}
          onFiltersChange={setConflictOperationFilters}
          onConflictResolutionDraftChange={setConflictResolutionDraft}
          onConflictCaseKeywordChange={setConflictCaseKeyword}
          onSelectedConflictCaseIdsChange={setSelectedConflictCaseIds}
          onSearchConflictCases={() => void searchConflictCases()}
          onResolveConflict={requestResolveConflict}
        />
        )}

        {activeSubPage === 'policies' && (
        <TestDesignTemplateManagementPanel
          canRead={canRead}
          canPolicyManage={canPolicyManage}
          state={templateState}
          health={health}
          templates={templates}
          templatePageTotal={templatePageTotal}
          selectedTemplateManageId={selectedTemplateManageId}
          selectedManagedTemplate={selectedManagedTemplate}
          templateDraft={templateDraft}
          templateProjectId={templateProjectId}
          onRefresh={() => void refreshTemplates()}
          onSave={saveTemplate}
          onSelectedTemplateManageIdChange={setSelectedTemplateManageId}
          onTemplateDraftChange={setTemplateDraft}
          onToggleCoverage={toggleTemplateCoverage}
          onDisableTemplate={() => void disableTemplate()}
        />
        )}

        {activeSubPage === 'candidates' && (
        <>
        <TestDesignReviewHistoryPanel
          canExport={canExport}
          state={reviewRecordState}
          reviewRecordPageTotal={reviewRecordPageTotal}
          reviewRecordPage={reviewRecordPage}
          reviewSummaryScope={reviewSummaryScope}
          selectedTaskId={selectedTaskId}
          reviewSummary={reviewSummary}
          onExport={() => void exportReviewRecords()}
          onReviewRecordPageIndexChange={setReviewRecordPageIndex}
        />

        <TestDesignCandidateReviewPanel
          canExport={canExport}
          canReview={canReview}
          selectedTask={selectedTask}
          selectedTaskId={selectedTaskId}
          taskState={taskState}
          mutationState={mutationState}
          candidateFilters={candidateFilters}
          candidatePage={candidatePage}
          candidatePageSize={candidatePageSize}
          selectedCandidates={selectedCandidates}
          selectedCandidateIds={selectedCandidateIds}
          selectedCandidateId={selectedCandidateId}
          selectedCandidate={selectedCandidate}
          candidateDraft={candidateDraft}
          selectedCandidateSource={selectedCandidateSource}
          candidateQualityIssues={candidateQualityIssues}
          candidateSaveBlocked={candidateSaveBlocked}
          reviewComment={reviewComment}
          currentPageSelectableCount={currentPageSelectableCandidates.length}
          selectedReviewCandidates={selectedReviewCandidates}
          selectedBatchEditableCandidates={selectedBatchEditableCandidates}
          batchActionResult={batchActionResult}
          batchEditResult={batchEditResult}
          batchEditDraft={batchEditDraft}
          batchEditIssues={batchEditIssues}
          batchEditBlocked={batchEditBlocked}
          batchEditFieldLabels={batchEditFieldLabels}
          draggingStepId={draggingStepId}
          onCandidateFiltersChange={setCandidateFilters}
          onCandidatePageIndexChange={setCandidatePageIndex}
          onCandidatePageSizeChange={setCandidatePageSize}
          onSelectedCandidateIdsChange={setSelectedCandidateIds}
          onSelectedCandidateIdChange={setSelectedCandidateId}
          onCandidateDraftChange={setCandidateDraft}
          onBatchEditDraftChange={setBatchEditDraft}
          onReviewCommentChange={setReviewComment}
          onDraggingStepIdChange={setDraggingStepId}
          onSelectCurrentPageCandidates={selectCurrentPageCandidates}
          onToggleCandidateSelection={toggleCandidateSelection}
          onExportCandidateReview={(scope) => void exportCandidateReview(scope)}
          onExportTaskReport={() => void exportTaskReport()}
          onBatchReviewCandidates={requestBatchReviewCandidates}
          onBatchEditCandidates={requestBatchEditCandidates}
          onSaveCandidate={() => void saveCandidate()}
          onReviewCandidate={(action) => void reviewCandidate(action)}
          onInsertPresetSteps={insertPresetSteps}
          onAddStepDraft={addStepDraft}
          onDeleteSelectedSteps={deleteSelectedSteps}
          onUpdateStepDraft={updateStepDraft}
          onDropStepDraft={dropStepDraft}
          onMoveStepDraft={moveStepDraft}
          onInsertStepDraftAfter={insertStepDraftAfter}
          onRemoveStepDraft={removeStepDraft}
        />
        </>
        )}
      </div>

      {(activeSubPage === 'tasks' || activeSubPage === 'publish') && (
      <aside className="side-stack">
        {activeSubPage === 'tasks' && (
        <>
        <TestDesignGenerationConfigPanel
          canGenerate={canGenerate}
          mutationState={mutationState}
          templateState={templateState}
          health={health}
          templates={templates}
          selectedRequirementCount={selectedRequirementIds.length}
          generationDraft={generationDraft}
          selectedGenerationTemplate={selectedGenerationTemplate}
          explicitContextAssetLimit={explicitContextAssetLimit}
          onCreateTask={createTask}
          onSelectGenerationTemplate={selectGenerationTemplate}
          onGenerationDraftChange={setGenerationDraft}
          onToggleCoverage={toggleCoverage}
        />

        <TestDesignTaskListPanel
          disabled={disabled}
          canGenerate={canGenerate}
          loadState={loadState}
          taskState={taskState}
          tasks={tasks}
          selectedTaskId={selectedTaskId}
          taskFilters={taskFilters}
          initialTaskFilters={initialTaskFilters}
          onTaskFiltersChange={setTaskFilters}
          onSelectTask={setSelectedTaskId}
          onRetryTask={(task) => void retryTask(task)}
          onReplayQueuedTaskEvent={(task) => void replayQueuedTaskEvent(task)}
          onCancelTask={(task) => void cancelTask(task)}
        />

        <TestDesignTaskDiagnosticsPanel
          selectedTask={selectedTask}
          taskDiagnostics={taskDiagnostics}
        />
        </>
        )}

        {activeSubPage === 'publish' && (
        <TestDesignPublishPanel
          canRead={canRead}
          canExport={canExport}
          canPublish={canPublish}
          selectedTaskId={selectedTaskId}
          taskState={taskState}
          publishState={publishState}
          releaseReadinessState={releaseReadinessState}
          reportArchiveState={reportArchiveState}
          publishScopeLabel={publishScopeLabel}
          selectedCandidateCount={selectedCandidateIds.length}
          selectedPublishableCount={selectedPublishableCandidates.length}
          canPublishCurrentScope={canPublishCurrentScope}
          currentReleaseReadiness={currentReleaseReadiness}
          releaseReadinessApprovals={releaseReadinessApprovals}
          selectedReleaseReadinessApprovalId={selectedReleaseReadinessApprovalId}
          selectedReleaseReadinessApproval={selectedReleaseReadinessApproval}
          selectedPendingReleaseReadinessApproval={selectedPendingReleaseReadinessApproval}
          releaseReadinessDraft={releaseReadinessDraft}
          releaseReadinessSubmitBlocked={releaseReadinessSubmitBlocked}
          releaseReadinessNotes={releaseReadinessNotes}
          releaseReadinessReasonCodes={releaseReadinessReasonCodes}
          releaseReadinessWorkOrderStatuses={releaseReadinessWorkOrderStatuses}
          reportArchives={reportArchives}
          selectedReportArchiveId={selectedReportArchiveId}
          selectedReportArchive={selectedReportArchive}
          reportArchiveIntegrity={reportArchiveIntegrity}
          reportArchiveApprovals={reportArchiveApprovals}
          selectedReportArchiveApprovalId={selectedReportArchiveApprovalId}
          selectedReportArchiveApproval={selectedReportArchiveApproval}
          selectedPendingReportArchiveApproval={selectedPendingReportArchiveApproval}
          reportArchiveDraft={reportArchiveDraft}
          reportArchiveNotes={reportArchiveNotes}
          reportArchiveApprovalTypes={reportArchiveApprovalTypes}
          reportArchiveReasonCodes={reportArchiveReasonCodes}
          reportArchiveWorkOrderStatuses={reportArchiveWorkOrderStatuses}
          publishResult={publishResult}
          publishIssueRecords={publishIssueRecords}
          resolvableConflictRecords={resolvableConflictRecords}
          batchResolvableConflictCount={batchResolvableConflictItems.length}
          conflictResolutionDraft={conflictResolutionDraft}
          conflictCaseKeyword={conflictCaseKeyword}
          conflictCaseSearchProjectId={conflictCaseSearchProjectId}
          conflictCaseResults={conflictCaseResults}
          selectedConflictCaseIds={selectedConflictCaseIds}
          conflictCandidateById={conflictCandidateById}
          onPublish={(dryRun) => void requestPublishTask(dryRun)}
          onRefreshReleaseReadiness={(taskId) => void refreshReleaseReadinessApprovals(taskId)}
          onReleaseReadinessDraftChange={setReleaseReadinessDraft}
          onRequestReleaseReadinessApproval={requestReleaseReadinessApproval}
          onReviewReleaseReadinessApproval={(approvalId, action) => void reviewReleaseReadinessApproval(approvalId, action)}
          onSelectReleaseReadinessApproval={selectReleaseReadinessApproval}
          onAddReleaseReadinessNote={() => void addReleaseReadinessNote()}
          onRefreshReportArchives={(taskId) => void refreshReportArchives(taskId)}
          onReportArchiveDraftChange={setReportArchiveDraft}
          onRequestReportArchiveApproval={requestReportArchiveApproval}
          onReviewReportArchiveApproval={(approvalId, action) => void reviewReportArchiveApproval(approvalId, action)}
          onSelectReportArchive={selectReportArchive}
          onSelectReportArchiveApproval={selectReportArchiveApproval}
          onAddReportArchiveNote={() => void addReportArchiveNote()}
          onExportPublishResult={exportPublishResult}
          onRequestBatchResolveConflicts={requestBatchResolveConflicts}
          onConflictResolutionDraftChange={setConflictResolutionDraft}
          onConflictCaseKeywordChange={setConflictCaseKeyword}
          onSearchConflictCases={() => void searchConflictCases()}
          onSelectedConflictCaseIdsChange={setSelectedConflictCaseIds}
          onResolveConflict={requestResolveConflict}
        />
        )}

        {activeSubPage === 'tasks' && (
        <TestDesignScopePanel selectedRequirementTitles={selectedRequirementTitles} />
        )}
      </aside>
      )}
      </div>
    </>
  );
}
