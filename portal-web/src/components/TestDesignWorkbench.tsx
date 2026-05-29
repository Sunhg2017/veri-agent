import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Download,
  Eye,
  FileText,
  Link2,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Send,
  Sparkles,
  XCircle
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  fetchAssetRequirements,
  fetchAssetTestCases,
  type AssetRequirementView,
  type AssetTestCaseView
} from '../api/assets';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  batchActionTestDesignCandidates,
  batchResolveTestDesignConflicts,
  cancelTestDesignTask,
  confirmTestDesignCandidate,
  createTestDesignTask,
  exportTestDesignCandidatesCsv,
  exportTestDesignReviewRecordsCsv,
  exportTestDesignTaskReportCsv,
  fetchTaskTestDesignCandidates,
  fetchTestDesignHealth,
  fetchTestDesignReviewRecords,
  fetchTestDesignTaskQualitySummary,
  fetchTestDesignTaskSummary,
  fetchTestDesignTasks,
  ignoreTestDesignCandidate,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  resolveTestDesignConflict,
  retryTestDesignTask,
  testDesignErrorMessage,
  updateTestDesignCandidate,
  type TestDesignCandidateBatchActionResult,
  type TestDesignCandidateBatchActionType,
  type TestDesignCandidateView,
  type TestDesignHealth,
  type TestDesignPublishRecordView,
  type TestDesignPublishResult,
  type TestDesignQualitySummaryView,
  type TestDesignReviewRecordView,
  type TestDesignTaskView
} from '../api/testDesign';
import { canUseButton, hasPermission } from '../permissions';
import {
  validateTestDesignCandidateDraft,
  type TestDesignCandidateDraftQualityIssue
} from '../testDesignQuality';
import {
  buildTestDesignQualitySummary,
  qualitySummaryFromServer,
  type TestDesignQualitySummary
} from '../testDesignQualitySummary';
import {
  buildTestDesignReviewSummary,
  type TestDesignReviewSummary
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
  buildTestDesignTaskIdempotencySignature,
  resolveTestDesignTaskIdempotency,
  type TestDesignTaskIdempotencyState
} from '../testDesignIdempotency';
import { buildTestDesignTaskDiagnostics } from '../testDesignTaskDiagnostics';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type RequirementFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

type TaskFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

type CandidateFilters = {
  status: string;
  coverageType: string;
  keyword: string;
};

type GenerationDraft = {
  projectId: string;
  title: string;
  caseCountPerRequirement: string;
  coverageTypes: string[];
};

type CandidateDraft = {
  title: string;
  description: string;
  apiId: string;
  coverageType: string;
  priority: string;
  preconditions: string;
  steps: string;
  expectedResult: string;
  tags: string;
};

type BatchEditResult = {
  total: number;
  succeededCount: number;
  failedCount: number;
  items: Array<{
    candidateId: string;
    result: 'SUCCEEDED' | 'FAILED';
    candidate?: TestDesignCandidateView;
    errorMessage?: string;
  }>;
};

type ConflictResolutionDraft = {
  reason: string;
  comment: string;
};

type ConflictResolutionCandidate = Pick<TestDesignCandidateView, 'id' | 'title' | 'status' | 'version'>;
type ConflictResolutionItem = {
  candidate: ConflictResolutionCandidate;
  record: TestDesignPublishRecordView;
};

type PendingConfirmation =
  | { kind: 'batchReview'; action: TestDesignCandidateBatchActionType; summary: TestDesignConfirmationSummary }
  | { kind: 'batchEdit'; summary: TestDesignConfirmationSummary }
  | { kind: 'batchResolveConflict'; items: ConflictResolutionItem[]; summary: TestDesignConfirmationSummary }
  | { kind: 'resolveConflict'; candidate: ConflictResolutionCandidate; record: TestDesignPublishRecordView; summary: TestDesignConfirmationSummary }
  | { kind: 'publish'; dryRun: boolean; summary: TestDesignConfirmationSummary };

const initialFilters: RequirementFilters = {
  projectId: '',
  status: 'APPROVED',
  keyword: ''
};

const initialTaskFilters: TaskFilters = {
  projectId: '',
  status: '',
  keyword: ''
};

const initialCandidateFilters: CandidateFilters = {
  status: '',
  coverageType: '',
  keyword: ''
};

const initialGenerationDraft: GenerationDraft = {
  projectId: '',
  title: '',
  caseCountPerRequirement: '2',
  coverageTypes: ['SMOKE', 'FUNCTIONAL', 'EXCEPTION']
};

const initialConflictResolutionDraft: ConflictResolutionDraft = {
  reason: '人工确认复用既有用例',
  comment: ''
};

const ASYNC_TASK_STATUSES = new Set(['QUEUED', 'RUNNING']);
const RETRYABLE_TASK_STATUSES = new Set(['FAILED', 'PARTIAL_SUCCESS', 'CANCELLED']);
const CANCELLABLE_TASK_STATUSES = new Set(['DRAFT', 'QUEUED', 'RUNNING', 'PARTIAL_SUCCESS', 'FAILED']);

export function TestDesignWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'testDesign:read');
  const canGenerate = canUseButton(props.currentUser, 'testDesign:generate');
  const canReview = canUseButton(props.currentUser, 'testDesign:review');
  const canPublish = canUseButton(props.currentUser, 'testDesign:publish');
  const canExport = canUseButton(props.currentUser, 'testDesign:export');

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
  const generationIdempotencyRef = useRef<TestDesignTaskIdempotencyState | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  const [batchEditDraft, setBatchEditDraft] = useState<TestDesignBatchEditDraft>(initialTestDesignBatchEditDraft);
  const [publishResult, setPublishResult] = useState<TestDesignPublishResult | null>(null);
  const [reviewRecords, setReviewRecords] = useState<TestDesignReviewRecordView[]>([]);
  const [reviewRecordPageTotal, setReviewRecordPageTotal] = useState(0);
  const [reviewRecordPageIndex, setReviewRecordPageIndex] = useState(0);
  const [taskQualitySummary, setTaskQualitySummary] = useState<TestDesignQualitySummaryView | null>(null);
  const [batchActionResult, setBatchActionResult] = useState<TestDesignCandidateBatchActionResult | null>(null);
  const [batchEditResult, setBatchEditResult] = useState<BatchEditResult | null>(null);
  const [selectedCandidateIds, setSelectedCandidateIds] = useState<string[]>([]);
  const [conflictResolutionDraft, setConflictResolutionDraft] = useState<ConflictResolutionDraft>(initialConflictResolutionDraft);
  const [conflictCaseKeyword, setConflictCaseKeyword] = useState('');
  const [conflictCaseResults, setConflictCaseResults] = useState<AssetTestCaseView[]>([]);
  const [selectedConflictCaseIds, setSelectedConflictCaseIds] = useState<Record<string, string>>({});
  const [pendingConfirmation, setPendingConfirmation] = useState<PendingConfirmation | null>(null);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [taskState, setTaskState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });
  const [publishState, setPublishState] = useState<WorkState>({ loading: false });
  const [reviewRecordState, setReviewRecordState] = useState<WorkState>({ loading: false });

  const disabled = !props.signedIn || !canRead;
  const selectedTask = tasks.find((task) => task.id === selectedTaskId) ?? null;
  const selectedTaskGenerating = selectedTask ? ASYNC_TASK_STATUSES.has(selectedTask.status) : false;
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId) ?? null;
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
  const conflictCaseSearchProjectId = publishResult?.projectId ?? selectedTask?.projectId ?? '';
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
  const candidateQualityIssues = useMemo(
    () => candidateDraft && selectedCandidate
      ? validateTestDesignCandidateDraft(candidateDraft, {
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
  const taskDiagnostics = useMemo(
    () => buildTestDesignTaskDiagnostics(selectedTask),
    [selectedTask]
  );

  const refreshCandidatePage = useCallback(async (taskId: string, options?: { silent?: boolean }) => {
    if (!props.signedIn || !canRead || !taskId) {
      setCandidates([]);
      setCandidatePageTotal(0);
      setReviewRecords([]);
      setReviewRecordPageTotal(0);
      setTaskQualitySummary(null);
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
      setLoadState({ loading: false });
      setTaskState({ loading: false });
      setReviewRecordState({ loading: false });
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
  }, [selectedTaskId]);

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
    if (!selectedTaskId || !selectedTaskGenerating) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void refreshCandidatePage(selectedTaskId, { silent: true });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
    }, 2000);
    return () => window.clearInterval(timer);
  }, [refreshCandidatePage, refreshReviewRecords, refreshTaskQualitySummary, selectedTaskGenerating, selectedTaskId]);

  useEffect(() => {
    const nextCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId) ?? null;
    setCandidateDraft(nextCandidate ? draftFromCandidate(nextCandidate) : null);
    setReviewComment(nextCandidate?.reviewComment ?? nextCandidate?.rejectedReason ?? nextCandidate?.ignoredReason ?? '');
  }, [candidates, selectedCandidateId]);

  useEffect(() => {
    if (!generationDraft.projectId && filters.projectId) {
      setGenerationDraft((current) => ({ ...current, projectId: filters.projectId }));
    }
  }, [filters.projectId, generationDraft.projectId]);

  useEffect(() => {
    if (!taskFilters.projectId && filters.projectId) {
      setTaskFilters((current) => ({ ...current, projectId: filters.projectId }));
    }
  }, [filters.projectId, taskFilters.projectId]);

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
      title: generationDraft.title,
      requirementIds: selectedRequirementIds,
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
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '生成任务重试失败') });
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
    } catch (error: unknown) {
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '生成任务取消失败') });
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
        steps: stepsFromText(candidateDraft.steps),
        expectedResult: candidateDraft.expectedResult,
        tags: tagsFromText(candidateDraft.tags),
        version: selectedCandidate.version
      });
      updateCandidateInState(response.data);
      setMutationState({ loading: false, success: '候选用例已保存', traceId: response.trace_id });
      void refreshReviewRecords(selectedTaskId, { silent: true });
      void refreshTaskQualitySummary(selectedTaskId, { silent: true });
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
      setSelectedConflictCaseIds({});
      setConflictCaseResults([]);
      setPublishState({
        loading: false,
        success: dryRun ? '预发布检查已完成' : '已发布到资产库测试用例',
        traceId: response.trace_id
      });
      if (!dryRun) {
        await refreshCandidatePage(selectedTaskId);
        void refreshTaskQualitySummary(selectedTaskId, { silent: true });
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
      }
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
      }

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
          <Metric icon={<Sparkles size={20} />} label="服务状态" value={health?.status ?? '-'} desc={health?.generationMode ?? '未加载'} />
          <Metric icon={<FileText size={20} />} label="候选用例" value={String(candidates.length)} desc={`确认 ${statusCounts.CONFIRMED ?? 0} · 待重试 ${statusCounts.FAILED ?? 0}`} />
          <Metric icon={<ClipboardCheck size={20} />} label="已发布" value={String(selectedTask?.publishedCount ?? 0)} desc={selectedTask?.status ?? '-'} />
        </div>

        <QualitySummaryPanel
          scopeLabel={qualitySummaryScope}
          selectedTaskId={selectedTaskId}
          summary={qualitySummary}
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
                      <td className="table-empty" colSpan={6}>{selectedTaskId ? '暂无匹配候选用例' : '请先生成或选择任务'}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {candidateDraft && selectedCandidate && (
              <div className="test-design-editor">
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
                <label className="field">
                  <span className="field-label">步骤</span>
                  <textarea value={candidateDraft.steps} onChange={(event) => setCandidateDraft({ ...candidateDraft, steps: event.target.value })} disabled={!canReview || mutationState.loading} />
                  <span className="field-hint">每行一个步骤，可用“操作 =&gt; 期望”格式。</span>
                  <QualityFieldMessages field="steps" issues={candidateQualityIssues} />
                </label>
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
                <span className="field-label">项目 ID</span>
                <input value={generationDraft.projectId} onChange={(event) => setGenerationDraft((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">任务标题</span>
                <input value={generationDraft.title} onChange={(event) => setGenerationDraft((current) => ({ ...current, title: event.target.value }))} placeholder="登录模块用例生成" disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">每需求用例数</span>
                <input value={generationDraft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => setGenerationDraft((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!canGenerate || mutationState.loading} />
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

function Metric(props: { icon: ReactNode; label: string; value: string; desc: string }) {
  return (
    <div className="metric-card">
      <div className="metric-icon info">{props.icon}</div>
      <div className="metric-body">
        <span className="metric-label">{props.label}</span>
        <strong className="metric-value">{props.value}</strong>
        <span className="metric-desc">{props.desc}</span>
      </div>
    </div>
  );
}

function QualitySummaryPanel(props: {
  scopeLabel: string;
  selectedTaskId: string;
  summary: TestDesignQualitySummary;
}) {
  return (
    <section className="panel test-design-quality-dashboard">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">质量摘要</h2>
          <p className="panel-desc">{props.scopeLabel}</p>
        </div>
        {props.summary.readiness ? (
          <span className={`badge badge-${badgeTone(props.summary.readiness.tone)}`}>
            {props.summary.readiness.label}
          </span>
        ) : props.summary.warnings.length > 0 && (
          <span className="badge badge-warning">待处理 {props.summary.warnings.length}</span>
        )}
      </div>
      <div className="panel-body compact">
        {props.selectedTaskId ? (
          <>
            {props.summary.readiness && (
              <div className={`test-design-readiness tone-${props.summary.readiness.tone}`}>
                <strong>{props.summary.readiness.label}</strong>
                <span>阻断 {props.summary.readiness.blockingCount} · 风险 {props.summary.readiness.warningCount}</span>
              </div>
            )}
            <div className="test-design-quality-metrics">
              {props.summary.metrics.map((metric) => (
                <div className={`test-design-quality-metric tone-${metric.tone}`} key={metric.label}>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  <small>{metric.desc}</small>
                </div>
              ))}
            </div>
            {props.summary.readiness && (
              <div className="test-design-quality-distribution">
                <span className="test-design-quality-distribution-label">准出</span>
                <div className="test-design-quality-distribution-items">
                  {props.summary.readiness.checks.map((check) => (
                    <span className={`test-design-quality-chip tone-${check.tone}`} key={check.code} title={check.desc}>
                      {check.label} {check.desc}
                    </span>
                  ))}
                </div>
              </div>
            )}
            <div className="test-design-quality-distributions">
              {props.summary.distributions.map((group) => (
                <div className="test-design-quality-distribution" key={group.label}>
                  <span className="test-design-quality-distribution-label">{group.label}</span>
                  <div className="test-design-quality-distribution-items">
                    {group.items.length ? (
                      group.items.map((item) => (
                        <span className={`test-design-quality-chip tone-${item.tone}`} key={item.label}>
                          {item.label} {item.count} · {item.percent}%
                        </span>
                      ))
                    ) : (
                      <span className="field-hint">暂无</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
            {props.summary.warnings.length > 0 && (
              <div className="test-design-quality-warnings">
                {props.summary.warnings.map((warning) => (
                  <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                    {warning.label} {warning.count}
                  </span>
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="notice info">请先选择任务</div>
        )}
      </div>
    </section>
  );
}

function badgeTone(tone: string) {
  if (tone === 'success' || tone === 'warning' || tone === 'danger' || tone === 'info') {
    return tone;
  }
  return 'neutral';
}

function ReviewSummaryPanel(props: {
  scopeLabel: string;
  selectedTaskId: string;
  summary: TestDesignReviewSummary;
}) {
  return (
    <div className="test-design-review-summary">
      <div className="test-design-review-summary-heading">
        <span>{props.scopeLabel}</span>
        {props.summary.warnings.length > 0 && (
          <span className="badge badge-warning">提示 {props.summary.warnings.length}</span>
        )}
      </div>
      {props.selectedTaskId ? (
        <>
          <div className="test-design-quality-metrics test-design-review-summary-metrics">
            {props.summary.metrics.map((metric) => (
              <div className={`test-design-quality-metric tone-${metric.tone}`} key={metric.label}>
                <span>{metric.label}</span>
                <strong>{metric.value}</strong>
                <small>{metric.desc}</small>
              </div>
            ))}
          </div>
          <div className="test-design-quality-distributions">
            {props.summary.groups.map((group) => (
              <div className="test-design-quality-distribution" key={group.label}>
                <span className="test-design-quality-distribution-label">{group.label}</span>
                <div className="test-design-quality-distribution-items">
                  {group.items.length ? (
                    group.items.map((item) => (
                      <span className={`test-design-quality-chip tone-${item.tone}`} key={item.label}>
                        {item.label} {item.count} · {item.percent}%
                      </span>
                    ))
                  ) : (
                    <span className="field-hint">暂无</span>
                  )}
                </div>
              </div>
            ))}
          </div>
          {props.summary.warnings.length > 0 && (
            <div className="test-design-quality-warnings">
              {props.summary.warnings.map((warning) => (
                <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                  {warning.label} {warning.count}
                </span>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="notice info">请先选择任务</div>
      )}
    </div>
  );
}

function Detail(props: { label: string; value: string | number }) {
  return (
    <div className="detail-row">
      <span className="detail-label">{props.label}</span>
      <span className="detail-value">{props.value}</span>
    </div>
  );
}

function CandidateStatus(props: { value: string }) {
  const value = props.value;
  const className = value === 'CONFIRMED' || value === 'PUBLISHED'
    ? 'badge badge-success'
    : value === 'REJECTED' || value === 'FAILED'
      ? 'badge badge-danger'
      : value === 'IGNORED'
        ? 'badge badge-neutral'
        : 'badge badge-warning';
  return <span className={className}>{value}</span>;
}

function PublishRecordRow(props: { record: TestDesignPublishRecordView }) {
  const assetCaseHref = props.record.assetCaseId ? assetCaseTraceHref(props.record.assetCaseId) : '';
  return (
    <div className="test-design-publish-record">
      <span>
        <strong>{props.record.title ?? props.record.candidateId ?? '-'}</strong>
        {assetCaseHref ? (
          <a className="test-design-asset-link" href={assetCaseHref}>
            <Link2 size={13} />
            {props.record.action} · {props.record.assetCaseId}
          </a>
        ) : (
          <em>{props.record.action} · {props.record.requirementId ?? '-'}</em>
        )}
        {props.record.errorMessage && <small>{props.record.errorMessage}</small>}
      </span>
      <PublishResultBadge value={props.record.result} />
    </div>
  );
}

function PublishResultBadge(props: { value: string }) {
  const value = props.value;
  const className = value === 'SUCCEEDED' || value === 'PLANNED' || value === 'READY'
    ? 'badge badge-success'
    : value === 'CONFLICT' || value === 'FAILED' || value === 'DUPLICATE_REVIEW_REQUIRED'
      ? 'badge badge-danger'
      : value === 'SKIPPED' || value === 'LINK_EXISTING'
        ? 'badge badge-neutral'
        : 'badge badge-warning';
  return <span className={className}>{value}</span>;
}

function ReviewRecordRow(props: { record: TestDesignReviewRecordView }) {
  const statusChange = [props.record.beforeStatus, props.record.afterStatus].filter(Boolean).join(' -> ') || '-';
  const versionChange = props.record.versionBefore !== undefined || props.record.versionAfter !== undefined
    ? `${props.record.versionBefore ?? '-'} -> ${props.record.versionAfter ?? '-'}`
    : '-';
  return (
    <div className="test-design-review-record">
      <div>
        <strong>{props.record.title ?? props.record.candidateId ?? '-'}</strong>
        <em>{props.record.action} · {statusChange} · v{versionChange}</em>
        <small>{props.record.changedFields.length ? props.record.changedFields.join(', ') : '无字段变更摘要'}</small>
        {props.record.hasComment && <small>{props.record.commentPreview ?? '包含评审说明'}</small>}
      </div>
      <div className="test-design-review-record-meta">
        <span>{props.record.reviewer ?? '-'}</span>
        <time>{props.record.createdAt ?? '-'}</time>
      </div>
    </div>
  );
}

function BatchActionSummary(props: { result: TestDesignCandidateBatchActionResult }) {
  const failedItems = props.result.items.filter((item) => item.result !== 'SUCCEEDED');
  return (
    <div className={failedItems.length ? 'notice warning test-design-batch-summary' : 'notice success test-design-batch-summary'}>
      <strong>{testDesignBatchActionLabel(props.result.action)}结果</strong>
      <span>成功 {props.result.succeededCount} / {props.result.total}，失败 {props.result.failedCount}</span>
      {failedItems.length > 0 && (
        <div className="test-design-batch-failures">
          {failedItems.slice(0, 4).map((item) => (
            <span key={`${item.candidateId}-${item.errorCode ?? ''}`}>
              {item.candidateId}：{item.errorMessage ?? item.errorCode ?? item.result}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function BatchEditSummary(props: { result: BatchEditResult }) {
  const failedItems = props.result.items.filter((item) => item.result !== 'SUCCEEDED');
  return (
    <div className={failedItems.length ? 'notice warning test-design-batch-summary' : 'notice success test-design-batch-summary'}>
      <strong>批量字段编辑结果</strong>
      <span>成功 {props.result.succeededCount} / {props.result.total}，失败 {props.result.failedCount}</span>
      {failedItems.length > 0 && (
        <div className="test-design-batch-failures">
          {failedItems.slice(0, 4).map((item) => (
            <span key={`${item.candidateId}-${item.errorMessage ?? ''}`}>
              {item.candidateId}：{item.errorMessage ?? item.result}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function ConfirmationDialog(props: {
  summary: TestDesignConfirmationSummary;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="modal-backdrop" onClick={props.onCancel}>
      <div
        aria-labelledby="test-design-confirmation-title"
        aria-modal="true"
        className="modal-panel test-design-confirmation-modal"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-heading">
          <div>
            <h2 id="test-design-confirmation-title">{props.summary.title}</h2>
            <p className="panel-desc">请确认范围和影响后再继续。</p>
          </div>
        </div>
        <div className="modal-body">
          <div className="detail-grid">
            {props.summary.details.map((detail) => (
              <Detail key={detail.label} label={detail.label} value={detail.value} />
            ))}
          </div>
          <div className={props.summary.tone === 'warning' ? 'notice warning' : 'notice info'}>
            {props.summary.warnings.map((warning) => (
              <span key={warning}>{warning}</span>
            ))}
          </div>
          {props.summary.candidateTitles.length > 0 && (
            <div className="test-design-confirmation-candidates">
              <strong>候选预览</strong>
              <ul>
                {props.summary.candidateTitles.map((title, index) => (
                  <li key={`${title}-${index}`}>{title}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary btn-sm" type="button" onClick={props.onCancel}>
            取消
          </button>
          <button className="btn btn-primary btn-sm" type="button" onClick={props.onConfirm}>
            {props.summary.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">处理中</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function QualityFieldMessages(props: {
  field: TestDesignCandidateDraftQualityIssue['field'];
  issues: TestDesignCandidateDraftQualityIssue[];
}) {
  const fieldIssues = props.issues.filter((issue) => issue.field === props.field);
  if (!fieldIssues.length) {
    return null;
  }
  return (
    <>
      {fieldIssues.map((issue, index) => (
        <span className="field-error" key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</span>
      ))}
    </>
  );
}

function filterRequirements(requirements: AssetRequirementView[], filters: RequirementFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return requirements.filter((requirement) => {
    if (filters.projectId.trim() && requirement.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && requirement.status !== filters.status.trim()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [requirement.title, requirement.description, requirement.acceptanceCriteria, requirement.sourceRef, requirement.tags.join(',')]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function filterCandidates(candidates: TestDesignCandidateView[], filters: CandidateFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return candidates.filter((candidate) => {
    if (filters.status && candidate.status !== filters.status) {
      return false;
    }
    if (filters.coverageType && candidate.coverageType !== filters.coverageType) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      candidate.title,
      candidate.description,
      candidate.requirementId,
      candidate.apiId,
      candidate.errorMessage,
      candidate.tags.join(',')
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function isPublishIssueRecord(record: TestDesignPublishRecordView) {
  return ['CONFLICT', 'FAILED', 'DUPLICATE_REVIEW_REQUIRED'].includes(record.result) || Boolean(record.errorMessage);
}

function conflictResolutionCandidate(
  record: TestDesignPublishRecordView,
  candidateById: Map<string, TestDesignCandidateView>
): ConflictResolutionCandidate | undefined {
  if (!record.candidateId) {
    return undefined;
  }
  const cached = candidateById.get(record.candidateId);
  if (cached) {
    return cached;
  }
  if (record.candidateVersion === undefined) {
    return undefined;
  }
  return {
    id: record.candidateId,
    title: record.title ?? record.candidateId,
    status: record.candidateStatus ?? 'CONFIRMED',
    version: record.candidateVersion
  };
}

function isResolvableConflictRecord(record: TestDesignPublishRecordView) {
  const conflictSignal = record.action === 'DUPLICATE_REVIEW_REQUIRED'
    || record.result === 'CONFLICT'
    || record.result === 'DUPLICATE_REVIEW_REQUIRED';
  return Boolean(conflictSignal && record.candidateId);
}

function conflictResolutionTargetCaseId(
  record: TestDesignPublishRecordView,
  selectedCaseIds: Record<string, string>
) {
  if (!record.candidateId) {
    return record.assetCaseId ?? '';
  }
  const selectedCaseId = selectedCaseIds[record.candidateId];
  return selectedCaseId === undefined ? record.assetCaseId ?? '' : selectedCaseId;
}

function applyConflictResolutionRecord(
  records: TestDesignPublishRecordView[],
  resolution: TestDesignPublishRecordView
) {
  if (resolution.result !== 'SUCCEEDED') {
    return [resolution, ...records];
  }

  let replaced = false;
  const nextRecords = records.map((record) => {
    if (
      !replaced
      && isResolvableConflictRecord(record)
      && record.candidateId === resolution.candidateId
    ) {
      replaced = true;
      return resolution;
    }
    return record;
  });
  return replaced ? nextRecords : [resolution, ...records];
}

function publishRecordKey(record: TestDesignPublishRecordView) {
  return [
    record.id,
    record.candidateId,
    record.action,
    record.result,
    record.assetCaseId,
    record.createdAt
  ].filter(Boolean).join('-') || `${record.action}-${record.result}`;
}

function assetCaseTraceHref(assetCaseId: string) {
  return `#asset-library/trace/case/${encodeURIComponent(assetCaseId)}`;
}

function shortIdentifier(value: string) {
  if (value.length <= 14) {
    return value;
  }
  return `${value.slice(0, 8)}...${value.slice(-4)}`;
}

function countByStatus(candidates: TestDesignCandidateView[]) {
  return candidates.reduce<Record<string, number>>((counts, candidate) => {
    counts[candidate.status] = (counts[candidate.status] ?? 0) + 1;
    return counts;
  }, {});
}

function upsertTask(current: TestDesignTaskView[], task: TestDesignTaskView) {
  const exists = current.some((item) => item.id === task.id);
  if (!exists) {
    return [task, ...current];
  }
  return current.map((item) => (item.id === task.id ? task : item));
}

function mergeBatchCandidates(current: TestDesignCandidateView[], result: TestDesignCandidateBatchActionResult) {
  const candidateById = new Map(
    result.items
      .map((item) => item.candidate)
      .filter((candidate): candidate is TestDesignCandidateView => Boolean(candidate))
      .map((candidate) => [candidate.id, candidate])
  );
  return current.map((candidate) => candidateById.get(candidate.id) ?? candidate);
}

function mergeUpdatedCandidates(current: TestDesignCandidateView[], updatedCandidates: readonly TestDesignCandidateView[]) {
  if (!updatedCandidates.length) {
    return current;
  }
  const candidateById = new Map(updatedCandidates.map((candidate) => [candidate.id, candidate]));
  return current.map((candidate) => candidateById.get(candidate.id) ?? candidate);
}

function mergeCandidateCache(
  current: Record<string, TestDesignCandidateView>,
  nextCandidates: readonly TestDesignCandidateView[]
) {
  const next = { ...current };
  for (const candidate of nextCandidates) {
    next[candidate.id] = candidate;
  }
  return next;
}

function draftFromCandidate(candidate: TestDesignCandidateView): CandidateDraft {
  return {
    title: candidate.title,
    description: candidate.description ?? '',
    apiId: candidate.apiId ?? '',
    coverageType: candidate.coverageType,
    priority: candidate.priority,
    preconditions: candidate.preconditions ?? '',
    steps: candidate.steps.map((step) => `${step.action ?? ''} => ${step.expectedResult ?? ''}`.trim()).join('\n'),
    expectedResult: candidate.expectedResult ?? '',
    tags: candidate.tags.join(', ')
  };
}

function stepsFromText(value: string) {
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [action, expectedResult] = line.split(/\s*=>\s*/, 2);
      return {
        action: action?.trim(),
        expectedResult: expectedResult?.trim()
      };
    });
}

function tagsFromText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function downloadText(text: string, filename: string, contentType: string) {
  const blob = new Blob([text], { type: contentType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function reviewSuccessText(action: 'confirm' | 'reject' | 'ignore') {
  if (action === 'confirm') {
    return '候选用例已确认';
  }
  if (action === 'reject') {
    return '候选用例已驳回';
  }
  return '候选用例已忽略';
}

function emptyRequirementText(signedIn: boolean, canRead: boolean, loading: boolean) {
  if (!signedIn) {
    return '请先登录';
  }
  if (!canRead) {
    return '缺少 testDesign:read 权限';
  }
  return loading ? '加载中' : '暂无匹配需求';
}
