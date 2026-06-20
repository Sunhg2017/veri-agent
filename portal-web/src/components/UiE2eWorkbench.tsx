import {
  AlertTriangle,
  Archive,
  Bug,
  CheckCircle2,
  Download,
  FileText,
  Play,
  RefreshCw,
  Search,
  ShieldCheck,
  Square
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  archiveUiE2eScene,
  approveUiE2eBundle,
  archiveUiE2eBundle,
  cancelUiE2eRun,
  createUiE2eBundle,
  createUiE2eRun,
  createUiE2eScene,
  exportUiE2eBundle,
  exportUiE2eRun,
  fetchUiE2eBundle,
  fetchUiE2eBundles,
  fetchUiE2eFlakyMark,
  fetchUiE2eFlakyMarks,
  fetchUiE2eHealth,
  fetchUiE2eRun,
  fetchUiE2eRuns,
  fetchUiE2eScene,
  fetchUiE2eScenes,
  downloadUiE2eArtifact,
  rejectUiE2eBundle,
  submitUiE2eBundleReview,
  updateUiE2eScene,
  upsertUiE2eFlakyMark,
  type UiE2eArtifactManifest,
  type UiE2eBundleDetail,
  type UiE2eBundleExport,
  type UiE2eBundleSummary,
  type UiE2eFlakyMark,
  type UiE2eHealth,
  type UiE2eRunDetail,
  type UiE2eRunExport,
  type UiE2eRunSummary,
  type UiE2eRunStepResult,
  type UiE2eSceneDetail,
  type UiE2eSceneSummary
} from '../api/uiE2e';
import { canUseButton, hasPermission } from '../permissions';
import {
  blankUiE2eSceneDraft,
  buildUiE2eBundleListSummary,
  buildUiE2eArtifactDownloadState,
  buildUiE2eFlakyDetailInsight,
  buildUiE2eBundleQueueOverview,
  buildUiE2eFlakyListSummary,
  buildUiE2eRunFlakyGuidance,
  buildUiE2eFlakyQueueOverview,
  buildUiE2eRunCreationReadiness,
  buildUiE2eRunDiagnosis,
  buildUiE2eRunListSummary,
  buildUiE2eRunAuditTimeline,
  buildUiE2eRunQueueOverview,
  buildUiE2eSceneListSummary,
  buildUiE2eSceneActivitySummary,
  buildUiE2eSceneQueueOverview,
  buildUiE2eWorkbenchOverview,
  buildUiE2eFlakyPayload,
  buildUiE2eRunPayload,
  buildUiE2eScenePayload,
  buildUiE2eSceneUpdatePayload,
  explainUiE2eArtifactCaptureBlockedReason,
  explainUiE2eFailureBucket,
  extractUiE2eArtifactCaptureBlockedReason,
  filterUiE2eBundlesByFocusMode,
  filterUiE2eFlakyMarksByFocusMode,
  filterUiE2eRunsByFocusMode,
  filterUiE2eScenesByFocusMode,
  initialUiE2eFlakyDraft,
  initialUiE2eRunDraft,
  initialUiE2eSceneDraft,
  initialUiE2eSceneStepDraft,
  isUiE2eRunActiveStatus,
  labelUiE2eBundleFocusMode,
  labelUiE2eFlakyFocusMode,
  labelUiE2eRunFocusMode,
  labelUiE2eSceneFocusMode,
  prettyJson,
  sceneDraftFromDetail,
  type UiE2eBundleFocusMode,
  type UiE2eFlakyDraft,
  type UiE2eFlakyFocusMode,
  type UiE2eRunFocusMode,
  type UiE2eRunDraft,
  type UiE2eSceneDraft,
  type UiE2eSceneFocusMode,
  type UiE2eSceneStepDraft
} from '../uiE2eWorkbenchState';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type SimpleFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

type SceneFilters = SimpleFilters & {
  applicationId: string;
  environmentId: string;
  riskLevel: string;
  tag: string;
};

const initialFilters: SimpleFilters = { projectId: '', status: '', keyword: '' };
const initialSceneFilters: SceneFilters = {
  ...initialFilters,
  applicationId: '',
  environmentId: '',
  riskLevel: '',
  tag: ''
};

export function UiE2eWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'uiE2e:read');
  const canManage = canUseButton(props.currentUser, 'uiE2e:manage');
  const canReview = canUseButton(props.currentUser, 'uiE2e:review');
  const canExecute = canUseButton(props.currentUser, 'uiE2e:execute');
  const canExport = canUseButton(props.currentUser, 'uiE2e:export');
  const canFlaky = canUseButton(props.currentUser, 'uiE2e:flaky');

  const [health, setHealth] = useState<UiE2eHealth | null>(null);
  const [scenes, setScenes] = useState<UiE2eSceneSummary[]>([]);
  const [bundles, setBundles] = useState<UiE2eBundleSummary[]>([]);
  const [runs, setRuns] = useState<UiE2eRunSummary[]>([]);
  const [flakyMarks, setFlakyMarks] = useState<UiE2eFlakyMark[]>([]);

  const [sceneFilters, setSceneFilters] = useState<SceneFilters>(initialSceneFilters);
  const [bundleFilters, setBundleFilters] = useState<SimpleFilters>(initialFilters);
  const [runFilters, setRunFilters] = useState<SimpleFilters>(initialFilters);
  const [flakyFilters, setFlakyFilters] = useState<SimpleFilters>(initialFilters);
  const [sceneFocusMode, setSceneFocusMode] = useState<UiE2eSceneFocusMode>('all');
  const [bundleFocusMode, setBundleFocusMode] = useState<UiE2eBundleFocusMode>('all');
  const [runFocusMode, setRunFocusMode] = useState<UiE2eRunFocusMode>('all');
  const [flakyFocusMode, setFlakyFocusMode] = useState<UiE2eFlakyFocusMode>('all');

  const [selectedSceneId, setSelectedSceneId] = useState('');
  const [selectedBundleId, setSelectedBundleId] = useState('');
  const [selectedRunId, setSelectedRunId] = useState('');
  const [selectedFlakyId, setSelectedFlakyId] = useState('');
  const [editingSceneId, setEditingSceneId] = useState('');

  const [sceneDetail, setSceneDetail] = useState<UiE2eSceneDetail | null>(null);
  const [bundleDetail, setBundleDetail] = useState<UiE2eBundleDetail | null>(null);
  const [bundleExport, setBundleExport] = useState<UiE2eBundleExport | null>(null);
  const [runDetail, setRunDetail] = useState<UiE2eRunDetail | null>(null);
  const [runExport, setRunExport] = useState<UiE2eRunExport | null>(null);
  const [flakyDetail, setFlakyDetail] = useState<UiE2eFlakyMark | null>(null);

  const [sceneDraft, setSceneDraft] = useState<UiE2eSceneDraft>(initialUiE2eSceneDraft);
  const [bundleSceneId, setBundleSceneId] = useState('');
  const [reviewNote, setReviewNote] = useState('');
  const [runDraft, setRunDraft] = useState<UiE2eRunDraft>(initialUiE2eRunDraft);
  const [flakyDraft, setFlakyDraft] = useState<UiE2eFlakyDraft>(initialUiE2eFlakyDraft);

  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [sceneActionState, setSceneActionState] = useState<WorkState>({ loading: false });
  const [bundleActionState, setBundleActionState] = useState<WorkState>({ loading: false });
  const [runActionState, setRunActionState] = useState<WorkState>({ loading: false });
  const [flakyActionState, setFlakyActionState] = useState<WorkState>({ loading: false });

  const overview = useMemo(
    () => buildUiE2eWorkbenchOverview(health, scenes, bundles, runs, flakyMarks),
    [health, scenes, bundles, runs, flakyMarks]
  );
  const sceneQueueOverview = useMemo(() => buildUiE2eSceneQueueOverview(scenes), [scenes]);
  const visibleScenes = useMemo(() => filterUiE2eScenesByFocusMode(scenes, sceneFocusMode), [scenes, sceneFocusMode]);
  const bundleQueueOverview = useMemo(() => buildUiE2eBundleQueueOverview(bundles), [bundles]);
  const visibleBundles = useMemo(() => filterUiE2eBundlesByFocusMode(bundles, bundleFocusMode), [bundles, bundleFocusMode]);
  const runQueueOverview = useMemo(() => buildUiE2eRunQueueOverview(runs), [runs]);
  const visibleRuns = useMemo(() => filterUiE2eRunsByFocusMode(runs, runFocusMode), [runs, runFocusMode]);
  const flakyQueueOverview = useMemo(() => buildUiE2eFlakyQueueOverview(flakyMarks), [flakyMarks]);
  const visibleFlakyMarks = useMemo(
    () => filterUiE2eFlakyMarksByFocusMode(flakyMarks, flakyFocusMode),
    [flakyFocusMode, flakyMarks]
  );
  const selectedSceneSummary = useMemo(
    () => scenes.find((scene) => scene.id === runDraft.sceneId) ?? null,
    [runDraft.sceneId, scenes]
  );
  const selectedBundleSummary = useMemo(
    () => bundles.find((bundle) => bundle.id === runDraft.bundleId) ?? null,
    [bundles, runDraft.bundleId]
  );
  const selectedSceneActivity = useMemo(
    () => sceneDetail ? buildUiE2eSceneActivitySummary(sceneDetail.id, bundles, runs) : null,
    [bundles, runs, sceneDetail]
  );
  const runCreationReadiness = useMemo(
    () => buildUiE2eRunCreationReadiness({
      health,
      draft: runDraft,
      scene: selectedSceneSummary,
      bundle: selectedBundleSummary
    }),
    [health, runDraft, selectedBundleSummary, selectedSceneSummary]
  );
  const runCreateDisabled = !canExecute || runActionState.loading || !runCreationReadiness.ready;
  const runCreateButtonTitle = !canExecute
    ? undefined
    : runActionState.loading
      ? '运行请求处理中，请稍候。'
      : !runCreationReadiness.ready
        ? runCreationReadiness.summary
        : undefined;

  const refreshWorkbench = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setScenes([]);
      setBundles([]);
      setRuns([]);
      setFlakyMarks([]);
      setSceneDetail(null);
      setBundleDetail(null);
      setBundleExport(null);
      setRunDetail(null);
      setRunExport(null);
      setFlakyDetail(null);
      setSelectedSceneId('');
      setSelectedBundleId('');
      setSelectedRunId('');
      setSelectedFlakyId('');
      setEditingSceneId('');
      return;
    }
    setLoadState({ loading: true });
    try {
      const [healthResult, sceneResult, bundleResult, runResult, flakyResult] = await Promise.all([
        fetchUiE2eHealth(),
        fetchUiE2eScenes({ ...compactSceneFilters(sceneFilters), size: 20 }),
        fetchUiE2eBundles({ ...compactFilters(bundleFilters), size: 20 }),
        fetchUiE2eRuns({ ...compactFilters(runFilters), size: 20 }),
        fetchUiE2eFlakyMarks({ ...compactFilters(flakyFilters), size: 20 })
      ]);
      setHealth(healthResult.data);
      setScenes(sceneResult.data.items);
      setBundles(bundleResult.data.items);
      setRuns(runResult.data.items);
      setFlakyMarks(flakyResult.data.items);
      setSelectedSceneId((current) => retainSelection(current, sceneResult.data.items));
      setSelectedBundleId((current) => retainSelection(current, bundleResult.data.items));
      setSelectedRunId((current) => retainSelection(current, runResult.data.items));
      setSelectedFlakyId((current) => retainSelection(current, flakyResult.data.items));
      setLoadState({ loading: false, traceId: runResult.trace_id || sceneResult.trace_id || flakyResult.trace_id });
    } catch (error: unknown) {
      setLoadState({ loading: false, error: error instanceof Error ? error.message : '加载 UI E2E 工作台失败' });
    }
  }, [bundleFilters, canRead, flakyFilters, props.signedIn, runFilters, sceneFilters]);

  const refreshSceneDetail = useCallback(async (sceneId: string) => {
    if (!sceneId || !canRead) {
      setSceneDetail(null);
      return;
    }
    try {
      const result = await fetchUiE2eScene(sceneId);
      setSceneDetail(result.data);
    } catch (error: unknown) {
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : '加载场景详情失败' });
    }
  }, [canRead]);

  const refreshBundleDetail = useCallback(async (bundleId: string) => {
    if (!bundleId || !canRead) {
      setBundleDetail(null);
      setBundleExport(null);
      return;
    }
    try {
      const result = await fetchUiE2eBundle(bundleId);
      setBundleDetail(result.data);
      setBundleExport(null);
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : '加载脚本包详情失败' });
    }
  }, [canRead]);

  const refreshRunDetail = useCallback(async (runId: string) => {
    if (!runId || !canRead) {
      setRunDetail(null);
      setRunExport(null);
      return;
    }
    try {
      const result = await fetchUiE2eRun(runId);
      setRunDetail(result.data);
      setRunExport(null);
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '加载运行详情失败' });
    }
  }, [canRead]);

  const refreshActiveRunSnapshot = useCallback(async (runId: string) => {
    if (!runId || !props.signedIn || !canRead) {
      return;
    }
    try {
      const [runResult, runListResult] = await Promise.all([
        fetchUiE2eRun(runId),
        fetchUiE2eRuns({ ...compactFilters(runFilters), size: 20 })
      ]);
      setRunDetail(runResult.data);
      setRuns(runListResult.data.items);
      setSelectedRunId((current) => retainSelection(current, runListResult.data.items));
    } catch {
      // Keep the latest successful snapshot on screen and let manual refresh recover if needed.
    }
  }, [canRead, props.signedIn, runFilters]);

  const refreshFlakyDetail = useCallback(async (flakyId: string) => {
    if (!flakyId || !canRead) {
      setFlakyDetail(null);
      return;
    }
    try {
      const result = await fetchUiE2eFlakyMark(flakyId);
      setFlakyDetail(result.data);
    } catch (error: unknown) {
      setFlakyActionState({ loading: false, error: error instanceof Error ? error.message : '加载 Flaky 详情失败' });
    }
  }, [canRead]);

  useEffect(() => {
    void refreshWorkbench();
  }, [refreshWorkbench]);

  useEffect(() => {
    void refreshSceneDetail(selectedSceneId);
  }, [refreshSceneDetail, selectedSceneId]);

  useEffect(() => {
    if (visibleScenes.some((scene) => scene.id === selectedSceneId)) {
      return;
    }
    if (!visibleScenes.length) {
      setSelectedSceneId('');
      setSceneDetail(null);
      return;
    }
    setSelectedSceneId(visibleScenes[0].id);
    applySceneDefaults(visibleScenes[0]);
  }, [selectedSceneId, visibleScenes]);

  useEffect(() => {
    void refreshBundleDetail(selectedBundleId);
  }, [refreshBundleDetail, selectedBundleId]);

  useEffect(() => {
    if (visibleBundles.some((bundle) => bundle.id === selectedBundleId)) {
      return;
    }
    if (!visibleBundles.length) {
      setSelectedBundleId('');
      setBundleDetail(null);
      setBundleExport(null);
      return;
    }
    setSelectedBundleId(visibleBundles[0].id);
    applyBundleDefaults(visibleBundles[0]);
  }, [selectedBundleId, visibleBundles]);

  useEffect(() => {
    void refreshRunDetail(selectedRunId);
  }, [refreshRunDetail, selectedRunId]);

  useEffect(() => {
    if (visibleRuns.some((run) => run.id === selectedRunId)) {
      return;
    }
    if (!visibleRuns.length) {
      setSelectedRunId('');
      setRunDetail(null);
      setRunExport(null);
      return;
    }
    setSelectedRunId(visibleRuns[0].id);
    applyRunDefaults(visibleRuns[0]);
  }, [selectedRunId, visibleRuns]);

  useEffect(() => {
    if (!selectedRunId || !isUiE2eRunActiveStatus(runDetail?.status)) {
      return;
    }
    const timer = window.setTimeout(() => {
      void refreshActiveRunSnapshot(selectedRunId);
    }, 3000);
    return () => window.clearTimeout(timer);
  }, [refreshActiveRunSnapshot, runDetail?.status, selectedRunId]);

  useEffect(() => {
    if (visibleFlakyMarks.some((item) => item.id === selectedFlakyId)) {
      return;
    }
    if (!visibleFlakyMarks.length) {
      setSelectedFlakyId('');
      return;
    }
    setSelectedFlakyId(visibleFlakyMarks[0].id);
    applyFlakyDefaults(visibleFlakyMarks[0]);
  }, [selectedFlakyId, visibleFlakyMarks]);

  useEffect(() => {
    void refreshFlakyDetail(selectedFlakyId);
  }, [refreshFlakyDetail, selectedFlakyId]);

  if (!props.signedIn) {
    return <div className="notice warning">请先登录后查看 UI E2E 工作台。</div>;
  }

  if (!canRead) {
    return <div className="notice error">当前账号缺少 uiE2e:read 权限。</div>;
  }

  async function onSubmitScene(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManage) return;
    if (editingSceneId) {
      const { payload, issues } = buildUiE2eSceneUpdatePayload(sceneDraft);
      if (!payload || issues.length) {
        setSceneActionState({ loading: false, error: issues.join('；') });
        return;
      }
      setSceneActionState({ loading: true });
      try {
        const result = await updateUiE2eScene(editingSceneId, payload);
        setSceneDetail(result.data);
        setSelectedSceneId(result.data.id);
        setScenes((current) => [summaryFromSceneDetail(result.data), ...current.filter((scene) => scene.id !== result.data.id)]);
        setSceneDraft(sceneDraftFromDetail(result.data));
        setEditingSceneId(result.data.id);
        applySceneDefaults(result.data);
        setSceneActionState({ loading: false, success: '场景已更新', traceId: result.trace_id });
      } catch (error: unknown) {
        setSceneActionState({ loading: false, error: error instanceof Error ? error.message : '更新场景失败' });
      }
      return;
    }

    const { payload, issues } = buildUiE2eScenePayload(sceneDraft);
    if (!payload || issues.length) {
      setSceneActionState({ loading: false, error: issues.join('；') });
      return;
    }
    setSceneActionState({ loading: true });
    try {
      const result = await createUiE2eScene(payload);
      setSceneDetail(result.data);
      setSelectedSceneId(result.data.id);
      setScenes((current) => [summaryFromSceneDetail(result.data), ...current.filter((scene) => scene.id !== result.data.id)]);
      setSceneDraft(blankUiE2eSceneDraft({
        projectId: sceneDraft.projectId,
        applicationId: sceneDraft.applicationId,
        environmentId: sceneDraft.environmentId
      }));
      setEditingSceneId('');
      applySceneDefaults(result.data);
      setSceneActionState({ loading: false, success: '场景已创建', traceId: result.trace_id });
    } catch (error: unknown) {
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : '创建场景失败' });
    }
  }

  async function onArchiveScene() {
    if (!sceneDetail || !canManage) return;
    setSceneActionState({ loading: true });
    try {
      const result = await archiveUiE2eScene(sceneDetail.id);
      setSceneDetail(result.data);
      setScenes((current) => current.map((scene) => scene.id === result.data.id ? summaryFromSceneDetail(result.data) : scene));
      if (editingSceneId === result.data.id) {
        setEditingSceneId('');
        setSceneDraft(blankUiE2eSceneDraft({
          projectId: result.data.projectId,
          applicationId: result.data.applicationId || '',
          environmentId: result.data.environmentId || ''
        }));
      }
      setSceneActionState({ loading: false, success: '场景已归档', traceId: result.trace_id });
    } catch (error: unknown) {
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : '归档场景失败' });
    }
  }

  async function onCreateBundle() {
    if (!canManage) return;
    const sceneId = bundleSceneId.trim() || selectedSceneId;
    if (!sceneId) {
      setBundleActionState({ loading: false, error: '请选择或填写 sceneId 再生成脚本包' });
      return;
    }
    setBundleActionState({ loading: true });
    try {
      const result = await createUiE2eBundle({ sceneId });
      setBundleDetail(result.data);
      setBundleExport(null);
      setSelectedBundleId(result.data.id);
      setBundles((current) => [summaryFromBundleDetail(result.data), ...current.filter((bundle) => bundle.id !== result.data.id)]);
      applyBundleDefaults(result.data);
      setBundleActionState({ loading: false, success: '脚本包已生成', traceId: result.trace_id });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : '生成脚本包失败' });
    }
  }

  async function onReviewBundle(action: 'submit' | 'approve' | 'reject') {
    if (!bundleDetail || !canReview) return;
    setBundleActionState({ loading: true });
    try {
      const response = action === 'submit'
        ? await submitUiE2eBundleReview(bundleDetail.id, { note: reviewNote })
        : action === 'approve'
          ? await approveUiE2eBundle(bundleDetail.id, { note: reviewNote })
          : await rejectUiE2eBundle(bundleDetail.id, { note: reviewNote });
      setBundleDetail(response.data);
      setBundleExport(null);
      setBundles((current) => current.map((bundle) => bundle.id === response.data.id ? summaryFromBundleDetail(response.data) : bundle));
      if (action !== 'reject') {
        applyBundleDefaults(response.data);
      }
      setReviewNote('');
      setBundleActionState({
        loading: false,
        success: action === 'submit' ? '脚本包已送审' : action === 'approve' ? '脚本包已批准' : '脚本包已驳回',
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : '更新脚本包评审失败' });
    }
  }

  async function onArchiveBundle() {
    if (!bundleDetail || !canManage || bundleDetail.status === 'ARCHIVED') return;
    setBundleActionState({ loading: true });
    try {
      const result = await archiveUiE2eBundle(bundleDetail.id);
      setBundleDetail(result.data);
      setBundleExport(null);
      setBundles((current) => current.map((bundle) => bundle.id === result.data.id ? summaryFromBundleDetail(result.data) : bundle));
      setBundleActionState({ loading: false, success: '脚本包已归档', traceId: result.trace_id });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : '归档脚本包失败' });
    }
  }

  async function onExportBundle() {
    if (!bundleDetail || !canExport) return;
    setBundleActionState({ loading: true });
    try {
      const result = await exportUiE2eBundle(bundleDetail.id);
      setBundleExport(result.data);
      setBundleActionState({ loading: false, success: '脚本包脱敏摘要已导出', traceId: result.trace_id });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : '导出脚本包摘要失败' });
    }
  }

  async function onCreateRun(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canExecute) return;
    const { payload, issues } = buildUiE2eRunPayload(runDraft);
    if (!payload || issues.length) {
      setRunActionState({ loading: false, error: issues.join('；') });
      return;
    }
    setRunActionState({ loading: true });
    try {
      const result = await createUiE2eRun(payload);
      setRunDetail(result.data);
      setSelectedRunId(result.data.id);
      setRuns((current) => [summaryFromRunDetail(result.data), ...current.filter((run) => run.id !== result.data.id)]);
      applyRunDefaults(result.data);
      setRunActionState({
        loading: false,
        success: result.data.idempotentReplay ? '已回放既有运行摘要' : '运行已创建',
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '创建运行失败' });
    }
  }

  async function onCancelRun() {
    if (!runDetail || !canExecute || !isUiE2eRunActiveStatus(runDetail.status)) return;
    setRunActionState({ loading: true });
    try {
      const result = await cancelUiE2eRun(runDetail.id, { reason: runDraft.reason });
      setRunDetail(result.data);
      setRuns((current) => current.map((run) => run.id === result.data.id ? summaryFromRunDetail(result.data) : run));
      setRunActionState({ loading: false, success: `运行状态：${result.data.status}`, traceId: result.trace_id });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '取消运行失败' });
    }
  }

  async function onExportRun() {
    if (!runDetail || !canExport) return;
    setRunActionState({ loading: true });
    try {
      const result = await exportUiE2eRun(runDetail.id);
      setRunExport(result.data);
      setRunActionState({ loading: false, success: '运行脱敏摘要已导出', traceId: result.trace_id });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '导出运行摘要失败' });
    }
  }

  async function onDownloadArtifact(artifact: UiE2eArtifactManifest) {
    if (!runDetail || !canExport) {
      return;
    }
    const downloadState = buildUiE2eArtifactDownloadState(artifact);
    if (!downloadState.canDownload) {
      setRunActionState({ loading: false, error: downloadState.summary });
      return;
    }

    setRunActionState({ loading: true });
    try {
      const response = await downloadUiE2eArtifact(runDetail.id, artifact.id);
      downloadBlob(response.blob, response.filename || fallbackArtifactFileName(artifact), response.contentType);
      setRunActionState({
        loading: false,
        success: `${artifact.artifactType} 已下载`,
        traceId: response.traceId
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '下载 artifact 失败' });
    }
  }

  async function onUpsertFlaky(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await persistFlakyDraft(flakyDraft);
  }

  async function persistFlakyDraft(draft: UiE2eFlakyDraft) {
    if (!canFlaky) return;
    const { payload, issues } = buildUiE2eFlakyPayload(draft);
    if (!payload || issues.length) {
      setFlakyActionState({ loading: false, error: issues.join('；') });
      return;
    }
    setFlakyActionState({ loading: true });
    try {
      const result = await upsertUiE2eFlakyMark(payload);
      setSelectedFlakyId(result.data.id);
      setFlakyMarks((current) => [result.data, ...current.filter((item) => item.id !== result.data.id)]);
      setFlakyDetail(result.data);
      setRuns((current) => current.map((run) => run.id === result.data.runId ? { ...run, flakyStatus: result.data.status } : run));
      setRunDetail((current) => current && current.id === result.data.runId ? { ...current, flakyMark: result.data } : current);
      setFlakyActionState({ loading: false, success: 'Flaky 标记已更新', traceId: result.trace_id });
    } catch (error: unknown) {
      setFlakyActionState({ loading: false, error: error instanceof Error ? error.message : '更新 Flaky 标记失败' });
    }
  }

  async function onApplyRunFlakyPreset(status: UiE2eFlakyDraft['status'], reasonCode: string, reasonSummary: string) {
    if (!runDetail) {
      return;
    }
    const nextDraft: UiE2eFlakyDraft = {
      projectId: runDetail.projectId,
      sceneId: runDetail.sceneId,
      runId: runDetail.id,
      status,
      reasonCode,
      reasonSummary
    };
    setFlakyDraft(nextDraft);
    await persistFlakyDraft(nextDraft);
  }

  return (
    <div className="ui-e2e-workbench" data-testid="ui-e2e-workbench">
      <section className="metrics-grid">
        <Metric icon={<CheckCircle2 size={20} />} label="APPROVED 场景" value={String(overview.approvedScenes)} desc={health?.runnerMode || '等待加载'} tone="success" />
        <Metric icon={<FileText size={20} />} label="待评审脚本包" value={String(overview.reviewingBundles)} desc={health?.artifactPolicy ? 'artifact policy ready' : '等待加载'} tone="info" />
        <Metric icon={<Play size={20} />} label="活跃运行" value={String(overview.activeRuns)} desc={overview.runnerLabel} tone={overview.runnerTone} />
        <Metric icon={<AlertTriangle size={20} />} label="最近失败" value={String(overview.recentFailures)} desc={overview.blockedRuns ? `blocked=${overview.blockedRuns}` : '无阻断运行'} tone={overview.recentFailures ? 'danger' : overview.blockedRuns ? 'warning' : 'success'} />
        <Metric icon={<ShieldCheck size={20} />} label="allowlist" value={overview.allowlistLabel} desc={health ? `${health.allowlistHostCount} hosts` : '等待加载'} tone={overview.allowlistTone} />
        <Metric icon={<Bug size={20} />} label="CONFIRMED_FLAKY" value={String(overview.confirmedFlaky)} desc={health?.exportEnabled ? 'export ON' : 'export OFF'} tone={overview.confirmedFlaky ? 'warning' : 'info'} />
      </section>

      <div className="ui-e2e-layout">
        <section className="ui-e2e-list-column">
          <Panel
            title="控制面健康"
            desc={health ? `${health.service} · ${health.status}` : '加载健康状态'}
            action={(
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void refreshWorkbench()} disabled={loadState.loading}>
                <RefreshCw size={15} />刷新
              </button>
            )}
          >
            {health ? (
              <>
                <div className="ui-e2e-health-grid">
                  <SummaryTile label="runnerMode" value={health.runnerMode || '-'} />
                  <SummaryTile label="runnerEnabled" value={health.runnerEnabled ? 'ON' : 'OFF'} tone={health.runnerEnabled ? 'success' : 'warning'} />
                  <SummaryTile label="allowlist" value={health.allowlistEnabled ? `ON (${health.allowlistHostCount})` : 'OFF'} />
                  <SummaryTile label="export" value={health.exportEnabled ? 'ON' : 'OFF'} />
                </div>
                <div className="report-section-grid">
                  <InfoBlock title="supportedNodeTypes" value={health.supportedNodeTypes.join(', ') || '-'} />
                  <InfoBlock title="maxConcurrency" value={String(health.maxConcurrency)} />
                  <InfoBlock title="defaultTimeout" value={`${health.defaultTimeoutSeconds}s`} />
                  <InfoBlock title="maxScenesPerRun" value={String(health.maxScenesPerRun)} />
                  <InfoBlock title="recentFailures" value={String(overview.recentFailures)} />
                  <InfoBlock title="blockedRuns" value={String(overview.blockedRuns)} />
                </div>
                <PolicySummary policy={{ ...health.credentialPolicy, ...health.artifactPolicy, ...health.policy }} />
              </>
            ) : (
              <div className="notice info">等待加载 UI E2E 健康摘要。</div>
            )}
            {overview.notices.map((notice) => (
              <div className={`notice ${notice.tone}`} key={notice.message}>{notice.message}</div>
            ))}
            <StateLine state={loadState} />
          </Panel>

          <Panel title="场景筛选与创建" desc="管理项目级 UI 场景、步骤模板和状态。">
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <input value={sceneFilters.projectId} onChange={(event) => setSceneFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <select value={sceneFilters.status} onChange={(event) => setSceneFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">全部</option>
                  <option value="DRAFT">DRAFT</option>
                  <option value="REVIEWING">REVIEWING</option>
                  <option value="APPROVED">APPROVED</option>
                  <option value="DISABLED">DISABLED</option>
                  <option value="ARCHIVED">ARCHIVED</option>
                </select>
              </Field>
              <Field label="applicationId">
                <input
                  value={sceneFilters.applicationId}
                  onChange={(event) => setSceneFilters((current) => ({ ...current, applicationId: event.target.value }))}
                  placeholder="app-alpha"
                />
              </Field>
              <Field label="environmentId">
                <input
                  value={sceneFilters.environmentId}
                  onChange={(event) => setSceneFilters((current) => ({ ...current, environmentId: event.target.value }))}
                  placeholder="staging"
                />
              </Field>
              <Field label="riskLevel">
                <select value={sceneFilters.riskLevel} onChange={(event) => setSceneFilters((current) => ({ ...current, riskLevel: event.target.value }))}>
                  <option value="">全部</option>
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                  <option value="CRITICAL">CRITICAL</option>
                </select>
              </Field>
              <Field label="tag">
                <input value={sceneFilters.tag} onChange={(event) => setSceneFilters((current) => ({ ...current, tag: event.target.value }))} placeholder="smoke" />
              </Field>
              <Field label="keyword">
                <input value={sceneFilters.keyword} onChange={(event) => setSceneFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="code / name / tag" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />筛选
                </button>
                <button
                  className="btn btn-secondary"
                  type="button"
                  disabled={loadState.loading}
                  onClick={() => setSceneFilters(initialSceneFilters)}
                >
                  重置
                </button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${sceneFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setSceneFocusMode('all')}
              >
                全部 {scenes.length}
              </button>
              {sceneQueueOverview.focusOptions.map((option) => (
                <button
                  className={`btn ${sceneFocusMode === option.mode ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                  key={option.mode}
                  type="button"
                  onClick={() => setSceneFocusMode(option.mode)}
                  title={option.desc}
                >
                  {option.label} {option.count}
                </button>
              ))}
            </div>
            {sceneFocusMode !== 'all' && (
              <div className="notice info">
                当前聚焦 {labelUiE2eSceneFocusMode(sceneFocusMode)}，共 {visibleScenes.length} 条；详情区会跟随当前可见列表自动保持选中项。
              </div>
            )}
            <form className="ui-e2e-form" onSubmit={onSubmitScene}>
              <div className="form-grid">
                <Field label="projectId">
                  <input value={sceneDraft.projectId} onChange={(event) => setSceneDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canManage || sceneActionState.loading || Boolean(editingSceneId)} />
                </Field>
                <Field label="applicationId">
                  <input value={sceneDraft.applicationId} onChange={(event) => setSceneDraftValue('applicationId', event.target.value)} placeholder="app-alpha" disabled={!canManage || sceneActionState.loading} />
                </Field>
                <Field label="environmentId">
                  <input value={sceneDraft.environmentId} onChange={(event) => setSceneDraftValue('environmentId', event.target.value)} placeholder="staging" disabled={!canManage || sceneActionState.loading} />
                </Field>
                <Field label="code">
                  <input value={sceneDraft.code} onChange={(event) => setSceneDraftValue('code', event.target.value)} placeholder="portal-login-smoke" disabled={!canManage || sceneActionState.loading || Boolean(editingSceneId)} />
                </Field>
                <Field label="name">
                  <input value={sceneDraft.name} onChange={(event) => setSceneDraftValue('name', event.target.value)} placeholder="后台管理员登录并进入首页" disabled={!canManage || sceneActionState.loading} />
                </Field>
                <Field label="status">
                  <select value={sceneDraft.status} onChange={(event) => setSceneDraftValue('status', event.target.value)} disabled={!canManage || sceneActionState.loading}>
                    <option value="DRAFT">DRAFT</option>
                    <option value="REVIEWING">REVIEWING</option>
                    <option value="APPROVED">APPROVED</option>
                    <option value="DISABLED">DISABLED</option>
                  </select>
                </Field>
                <Field label="riskLevel">
                  <select value={sceneDraft.riskLevel} onChange={(event) => setSceneDraftValue('riskLevel', event.target.value)} disabled={!canManage || sceneActionState.loading}>
                    <option value="LOW">LOW</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="HIGH">HIGH</option>
                    <option value="CRITICAL">CRITICAL</option>
                  </select>
                </Field>
                <Field label="tags">
                  <input value={sceneDraft.tagsText} onChange={(event) => setSceneDraftValue('tagsText', event.target.value)} placeholder="login smoke admin" disabled={!canManage || sceneActionState.loading} />
                </Field>
              </div>
              <Field label="sourceSummary">
                <textarea value={sceneDraft.sourceSummaryText} onChange={(event) => setSceneDraftValue('sourceSummaryText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
              </Field>
              {editingSceneId && (
                <div className="notice info">当前正在编辑所选场景；`projectId` 和 `code` 为后端只读键，前端不会允许改写。</div>
              )}
              <div className="ui-e2e-step-editor">
                <div className="ui-e2e-section-heading">
                  <strong>步骤模板</strong>
                  <button className="btn btn-secondary btn-sm" type="button" onClick={addSceneStep} disabled={!canManage || sceneActionState.loading}>
                    <FileText size={15} />添加步骤
                  </button>
                </div>
                {sceneDraft.steps.map((step, index) => (
                  <div className="ui-e2e-step-card" key={`scene-step-${index}`}>
                    <div className="ui-e2e-step-card-header">
                      <strong>步骤 {index + 1}</strong>
                      <button className="btn btn-ghost btn-sm" type="button" onClick={() => removeSceneStep(index)} disabled={sceneDraft.steps.length <= 1 || !canManage || sceneActionState.loading}>
                        删除
                      </button>
                    </div>
                    <div className="ui-e2e-step-grid">
                      <Field label="stepType">
                        <input value={step.stepType} onChange={(event) => updateSceneStep(index, 'stepType', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="actionSummary">
                        <textarea value={step.actionSummaryText} onChange={(event) => updateSceneStep(index, 'actionSummaryText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="locatorStrategy">
                        <textarea value={step.locatorStrategyText} onChange={(event) => updateSceneStep(index, 'locatorStrategyText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="assertionSummary">
                        <textarea value={step.assertionSummaryText} onChange={(event) => updateSceneStep(index, 'assertionSummaryText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="waitPolicy">
                        <textarea value={step.waitPolicyText} onChange={(event) => updateSceneStep(index, 'waitPolicyText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                    </div>
                  </div>
                ))}
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canManage || sceneActionState.loading}>
                  <FileText size={16} />{editingSceneId ? '保存场景' : '创建场景'}
                </button>
                {sceneDetail && sceneDetail.status !== 'ARCHIVED' && (
                  <button className="btn btn-secondary" type="button" onClick={loadSelectedSceneIntoDraft} disabled={!canManage || sceneActionState.loading}>
                    <FileText size={16} />{editingSceneId === sceneDetail.id ? '重新载入所选场景' : '编辑所选场景'}
                  </button>
                )}
                {editingSceneId && (
                  <button className="btn btn-secondary" type="button" onClick={cancelSceneEditing} disabled={!canManage || sceneActionState.loading}>
                    取消编辑
                  </button>
                )}
                {sceneDetail && sceneDetail.status !== 'ARCHIVED' && (
                  <button className="btn btn-secondary" type="button" onClick={() => void onArchiveScene()} disabled={!canManage || sceneActionState.loading}>
                    <Archive size={16} />归档所选场景
                  </button>
                )}
              </div>
              <StateLine state={sceneActionState} />
            </form>
            <ListPanel
              items={visibleScenes}
              selectedId={selectedSceneId}
              emptyTitle="暂无场景"
              emptyDesc={sceneFocusMode === 'all'
                ? '创建第一条 UI 场景后，可继续生成脚本包并触发运行。'
                : `当前筛选条件下没有 ${labelUiE2eSceneFocusMode(sceneFocusMode)}。`}
              onSelect={(scene) => {
                setSelectedSceneId(scene.id);
                applySceneDefaults(scene);
                if (editingSceneId) {
                  setEditingSceneId('');
                  setSceneDraft(blankUiE2eSceneDraft({
                    projectId: scene.projectId,
                    applicationId: scene.applicationId || '',
                    environmentId: scene.environmentId || ''
                  }));
                }
              }}
              renderItem={(scene) => {
                const summary = buildUiE2eSceneListSummary(scene);
                return (
                  <>
                    <span className={`badge badge-${statusTone(scene.status)}`}>{scene.status}</span>
                    <strong>{scene.code}</strong>
                    <span>{summary.headline}</span>
                    <small>{summary.detail}</small>
                    <small>
                      {summary.signals.length ? `${summary.signals.join(' · ')} · ` : ''}
                      {scene.updatedAt ? formatDateTime(scene.updatedAt) : scene.id}
                    </small>
                  </>
                );
              }}
            />
          </Panel>

          <Panel title="脚本包评审" desc="从 APPROVED 场景生成 bundle，并通过送审/批准控制运行准入。">
            <form className="ui-e2e-form" onSubmit={(event) => { event.preventDefault(); void onCreateBundle(); }}>
              <div className="form-grid">
                <Field label="sceneId">
                  <input value={bundleSceneId} onChange={(event) => setBundleSceneId(event.target.value)} placeholder={selectedSceneId || '优先使用已选场景'} disabled={!canManage || bundleActionState.loading} />
                </Field>
                <Field label="review note">
                  <input value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder="评审说明 / 驳回原因" disabled={bundleActionState.loading} />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canManage || bundleActionState.loading}>
                  <FileText size={16} />生成脚本包
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onReviewBundle('submit')} disabled={!canReview || bundleActionState.loading || !bundleDetail || !['DRAFT', 'REJECTED', 'STATIC_CHECK_FAILED'].includes(bundleDetail.status)}>
                  <RefreshCw size={16} />送审
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onReviewBundle('approve')} disabled={!canReview || bundleActionState.loading || bundleDetail?.status !== 'REVIEWING'}>
                  <CheckCircle2 size={16} />批准
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onReviewBundle('reject')} disabled={!canReview || bundleActionState.loading || bundleDetail?.status !== 'REVIEWING'}>
                  <AlertTriangle size={16} />驳回
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onArchiveBundle()} disabled={!canManage || bundleActionState.loading || !bundleDetail || bundleDetail.status === 'ARCHIVED'}>
                  <Archive size={16} />归档
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onExportBundle()} disabled={!canExport || bundleActionState.loading || !bundleDetail}>
                  <Download size={16} />导出摘要
                </button>
              </div>
              <StateLine state={bundleActionState} />
            </form>
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <input value={bundleFilters.projectId} onChange={(event) => setBundleFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <select value={bundleFilters.status} onChange={(event) => setBundleFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">全部</option>
                  <option value="DRAFT">DRAFT</option>
                  <option value="REVIEWING">REVIEWING</option>
                  <option value="APPROVED">APPROVED</option>
                  <option value="REJECTED">REJECTED</option>
                  <option value="ARCHIVED">ARCHIVED</option>
                </select>
              </Field>
              <Field label="keyword">
                <input value={bundleFilters.keyword} onChange={(event) => setBundleFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="scene / digest" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />筛选
                </button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${bundleFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setBundleFocusMode('all')}
              >
                全部 {bundles.length}
              </button>
              {bundleQueueOverview.focusOptions.map((option) => (
                <button
                  className={`btn ${bundleFocusMode === option.mode ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                  key={option.mode}
                  type="button"
                  onClick={() => setBundleFocusMode(option.mode)}
                  title={option.desc}
                >
                  {option.label} {option.count}
                </button>
              ))}
            </div>
            {bundleFocusMode !== 'all' && (
              <div className="notice info">
                当前聚焦 {labelUiE2eBundleFocusMode(bundleFocusMode)}，共 {visibleBundles.length} 条；详情区会跟随当前可见列表自动保持选中项。
              </div>
            )}
            <ListPanel
              items={visibleBundles}
              selectedId={selectedBundleId}
              emptyTitle="暂无脚本包"
              emptyDesc={bundleFocusMode === 'all'
                ? '选择 APPROVED 场景后生成 bundle，并通过评审后用于运行。'
                : `当前筛选条件下没有 ${labelUiE2eBundleFocusMode(bundleFocusMode)}。`}
              onSelect={(bundle) => {
                setSelectedBundleId(bundle.id);
                applyBundleDefaults(bundle);
              }}
              renderItem={(bundle) => {
                const summary = buildUiE2eBundleListSummary(bundle);
                return (
                  <>
                    <span className={`badge badge-${statusTone(bundle.status)}`}>{bundle.status}</span>
                    <strong>{bundle.sceneCode || shortId(bundle.sceneId)}</strong>
                    <span>{summary.headline}</span>
                    <small>{summary.detail}</small>
                    <small>
                      {summary.signals.length ? `${summary.signals.join(' · ')} · ` : ''}
                      {bundle.updatedAt ? formatDateTime(bundle.updatedAt) : bundle.id}
                    </small>
                  </>
                );
              }}
            />
          </Panel>

          <Panel title="运行主链路" desc="输入脱敏 accountLeaseRef 和 env baseUrlRef，触发单次 UI 运行。">
            <form className="ui-e2e-form" onSubmit={onCreateRun}>
              <div className="form-grid">
                <Field label="projectId">
                  <input value={runDraft.projectId} onChange={(event) => setRunDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="sceneId">
                  <input value={runDraft.sceneId} onChange={(event) => setRunDraftValue('sceneId', event.target.value)} placeholder="UUID" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="bundleId">
                  <input value={runDraft.bundleId} onChange={(event) => setRunDraftValue('bundleId', event.target.value)} placeholder="UUID" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="environmentId">
                  <input value={runDraft.environmentId} onChange={(event) => setRunDraftValue('environmentId', event.target.value)} placeholder="staging" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="baseUrlRef">
                  <input value={runDraft.baseUrlRef} onChange={(event) => setRunDraftValue('baseUrlRef', event.target.value)} placeholder="env:staging" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="accountLeaseRef">
                  <input value={runDraft.accountLeaseRef} onChange={(event) => setRunDraftValue('accountLeaseRef', event.target.value)} placeholder="UUID" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="requestKey">
                  <input value={runDraft.requestKey} onChange={(event) => setRunDraftValue('requestKey', event.target.value)} placeholder="可选幂等键" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="reason">
                  <input value={runDraft.reason} onChange={(event) => setRunDraftValue('reason', event.target.value)} placeholder="可选触发原因" disabled={!canExecute || runActionState.loading} />
                </Field>
              </div>
              <div className={`notice ${runCreationReadiness.tone}`}>
                <strong>{runCreationReadiness.label}</strong>
                <span>{runCreationReadiness.summary}</span>
                {runCreationReadiness.checks.length ? (
                  <span>{runCreationReadiness.checks.join(' · ')}</span>
                ) : null}
              </div>
              <div className="report-actions-row">
                {canExecute && (
                  <>
                    <button className="btn btn-primary" type="submit" disabled={runCreateDisabled} title={runCreateButtonTitle}>
                      <Play size={16} />创建运行
                    </button>
                    <button className="btn btn-secondary" type="button" onClick={() => void onCancelRun()} disabled={runActionState.loading || !runDetail || !isUiE2eRunActiveStatus(runDetail.status)}>
                      <Square size={16} />取消运行
                    </button>
                  </>
                )}
                <button className="btn btn-secondary" type="button" onClick={() => void onExportRun()} disabled={!canExport || runActionState.loading || !runDetail}>
                  <Download size={16} />导出摘要
                </button>
              </div>
              {!canExecute && (
                <div className="notice info">当前账号缺少 `uiE2e:execute` 权限，因此运行与取消按钮不会开放。</div>
              )}
              <StateLine state={runActionState} />
            </form>
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <input value={runFilters.projectId} onChange={(event) => setRunFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <select value={runFilters.status} onChange={(event) => setRunFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">全部</option>
                  <option value="QUEUED">QUEUED</option>
                  <option value="RUNNING">RUNNING</option>
                  <option value="SUCCEEDED">SUCCEEDED</option>
                  <option value="FAILED">FAILED</option>
                  <option value="TIMEOUT">TIMEOUT</option>
                  <option value="CANCELED">CANCELED</option>
                  <option value="BLOCKED">BLOCKED</option>
                </select>
              </Field>
              <Field label="keyword">
                <input value={runFilters.keyword} onChange={(event) => setRunFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="requestKey / scene" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />筛选
                </button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${runFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setRunFocusMode('all')}
              >
                全部 {runs.length}
              </button>
              {runQueueOverview.focusOptions.map((option) => (
                <button
                  className={`btn ${runFocusMode === option.mode ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                  key={option.mode}
                  type="button"
                  onClick={() => setRunFocusMode(option.mode)}
                  title={option.desc}
                >
                  {option.label} {option.count}
                </button>
              ))}
            </div>
            {runFocusMode !== 'all' && (
              <div className="notice info">
                当前聚焦 {labelUiE2eRunFocusMode(runFocusMode)}，共 {visibleRuns.length} 条；详情区会跟随当前可见列表自动保持选中项。
              </div>
            )}
            <ListPanel
              items={visibleRuns}
              selectedId={selectedRunId}
              emptyTitle="暂无运行"
              emptyDesc={runFocusMode === 'all'
                ? '选中 APPROVED bundle 后触发单次运行，可查看步骤摘要、artifact manifest 和失败分类。'
                : `当前筛选条件下没有 ${labelUiE2eRunFocusMode(runFocusMode)}。`}
              onSelect={(run) => {
                setSelectedRunId(run.id);
                applyRunDefaults(run);
              }}
              renderItem={(run) => {
                const summary = buildUiE2eRunListSummary(run);
                return (
                  <>
                    <span className={`badge badge-${statusTone(run.status)}`}>{run.status}</span>
                    <strong>{run.sceneCode || shortId(run.sceneId)}</strong>
                    <span>{summary.headline}</span>
                    <small>{summary.detail}</small>
                    <small>
                      {summary.signals.length ? `${summary.signals.join(' · ')} · ` : ''}
                      {run.createdAt ? formatDateTime(run.createdAt) : run.id}
                    </small>
                  </>
                );
              }}
            />
          </Panel>

          <Panel title="Flaky 治理" desc="按运行或场景标记 FLAKY_CANDIDATE / CONFIRMED_FLAKY / WAIVED。">
            <form className="ui-e2e-form" onSubmit={onUpsertFlaky}>
              <div className="form-grid">
                <Field label="projectId">
                  <input value={flakyDraft.projectId} onChange={(event) => setFlakyDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="sceneId">
                  <input value={flakyDraft.sceneId} onChange={(event) => setFlakyDraftValue('sceneId', event.target.value)} placeholder="可选 UUID" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="runId">
                  <input value={flakyDraft.runId} onChange={(event) => setFlakyDraftValue('runId', event.target.value)} placeholder="可选 UUID" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="status">
                  <select value={flakyDraft.status} onChange={(event) => setFlakyDraftValue('status', event.target.value)} disabled={!canFlaky || flakyActionState.loading}>
                    <option value="NONE">NONE</option>
                    <option value="FLAKY_CANDIDATE">FLAKY_CANDIDATE</option>
                    <option value="CONFIRMED_FLAKY">CONFIRMED_FLAKY</option>
                    <option value="WAIVED">WAIVED</option>
                  </select>
                </Field>
                <Field label="reasonCode">
                  <input value={flakyDraft.reasonCode} onChange={(event) => setFlakyDraftValue('reasonCode', event.target.value)} placeholder="locator-drift" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="reasonSummary">
                  <input value={flakyDraft.reasonSummary} onChange={(event) => setFlakyDraftValue('reasonSummary', event.target.value)} placeholder="偶发定位漂移 / 环境抖动" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canFlaky || flakyActionState.loading}>
                  <Bug size={16} />保存 Flaky 标记
                </button>
              </div>
              <StateLine state={flakyActionState} />
            </form>
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <input value={flakyFilters.projectId} onChange={(event) => setFlakyFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <select value={flakyFilters.status} onChange={(event) => setFlakyFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">全部</option>
                  <option value="NONE">NONE</option>
                  <option value="FLAKY_CANDIDATE">FLAKY_CANDIDATE</option>
                  <option value="CONFIRMED_FLAKY">CONFIRMED_FLAKY</option>
                  <option value="WAIVED">WAIVED</option>
                </select>
              </Field>
              <Field label="keyword">
                <input value={flakyFilters.keyword} onChange={(event) => setFlakyFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="reason / scene / run" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />筛选
                </button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${flakyFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setFlakyFocusMode('all')}
              >
                全部 {flakyMarks.length}
              </button>
              {flakyQueueOverview.focusOptions.map((option) => (
                <button
                  className={`btn ${flakyFocusMode === option.mode ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                  key={option.mode}
                  type="button"
                  onClick={() => setFlakyFocusMode(option.mode)}
                  title={option.desc}
                >
                  {option.label} {option.count}
                </button>
              ))}
            </div>
            {flakyFocusMode !== 'all' && (
              <div className="notice info">
                当前聚焦 {labelUiE2eFlakyFocusMode(flakyFocusMode)}，共 {visibleFlakyMarks.length} 条；详情区会跟随当前可见列表自动保持选中项。
              </div>
            )}
            <ListPanel
              items={visibleFlakyMarks}
              selectedId={selectedFlakyId}
              emptyTitle="暂无 Flaky 标记"
              emptyDesc={flakyFocusMode === 'all'
                ? '可按运行或场景标记失败抖动，便于 WP10 消费聚合状态。'
                : `当前筛选条件下没有 ${labelUiE2eFlakyFocusMode(flakyFocusMode)}。`}
              onSelect={(item) => {
                setSelectedFlakyId(item.id);
                applyFlakyDefaults(item);
              }}
              renderItem={(item) => {
                const summary = buildUiE2eFlakyListSummary(item);
                return (
                  <>
                    <span className={`badge badge-${statusTone(item.status)}`}>{item.status}</span>
                    <strong>{item.sceneCode || shortId(item.sceneId || item.runId)}</strong>
                    <span>{summary.headline}</span>
                    <small>{summary.detail}</small>
                    <small>
                      {summary.signals.length ? `${summary.signals.join(' · ')} · ` : ''}
                      {item.updatedAt ? formatDateTime(item.updatedAt) : item.id}
                    </small>
                  </>
                );
              }}
            />
          </Panel>
        </section>

        <section className="ui-e2e-detail-column">
          <SceneDetailPanel detail={sceneDetail} activity={selectedSceneActivity} state={sceneActionState} />
          <BundleDetailPanel detail={bundleDetail} exported={bundleExport} state={bundleActionState} />
          <RunDetailPanel
            detail={runDetail}
            exported={runExport}
            state={runActionState}
            canExport={canExport}
            canFlaky={canFlaky}
            flakyState={flakyActionState}
            onDownloadArtifact={(artifact) => void onDownloadArtifact(artifact)}
            onApplyFlakyPreset={(status, reasonCode, reasonSummary) => void onApplyRunFlakyPreset(status, reasonCode, reasonSummary)}
          />
          <FlakyDetailPanel item={flakyDetail} state={flakyActionState} />
        </section>
      </div>
    </div>
  );

  function addSceneStep() {
    setSceneDraft((current) => ({
      ...current,
      steps: [...current.steps, { ...initialUiE2eSceneStepDraft }]
    }));
    setSceneActionState({ loading: false });
  }

  function removeSceneStep(index: number) {
    setSceneDraft((current) => ({
      ...current,
      steps: current.steps.filter((_, itemIndex) => itemIndex !== index)
    }));
    setSceneActionState({ loading: false });
  }

  function updateSceneStep(index: number, key: keyof UiE2eSceneStepDraft, value: string) {
    setSceneDraft((current) => ({
      ...current,
      steps: current.steps.map((step, itemIndex) => itemIndex === index ? { ...step, [key]: value } : step)
    }));
    setSceneActionState({ loading: false });
  }

  function setSceneDraftValue(key: keyof UiE2eSceneDraft, value: string) {
    setSceneDraft((current) => ({ ...current, [key]: value }));
    setSceneActionState({ loading: false });
  }

  function setRunDraftValue(key: keyof UiE2eRunDraft, value: string) {
    setRunDraft((current) => ({ ...current, [key]: value }));
    setRunActionState({ loading: false });
  }

  function setFlakyDraftValue(key: keyof UiE2eFlakyDraft, value: string) {
    setFlakyDraft((current) => ({ ...current, [key]: value }));
    setFlakyActionState({ loading: false });
  }

  function applySceneDefaults(scene: Pick<UiE2eSceneSummary, 'id' | 'projectId' | 'applicationId' | 'environmentId'>) {
    setBundleSceneId(scene.id);
    setRunDraft((current) => ({
      ...current,
      projectId: scene.projectId,
      sceneId: scene.id,
      environmentId: scene.environmentId || current.environmentId
    }));
    setFlakyDraft((current) => ({
      ...current,
      projectId: scene.projectId,
      sceneId: scene.id
    }));
  }

  function applyBundleDefaults(bundle: Pick<UiE2eBundleSummary, 'id' | 'projectId' | 'sceneId'> & { environmentId?: string }) {
    setRunDraft((current) => ({
      ...current,
      projectId: bundle.projectId,
      sceneId: bundle.sceneId,
      bundleId: bundle.id,
      environmentId: bundle.environmentId || current.environmentId
    }));
  }

  function applyRunDefaults(run: Pick<UiE2eRunSummary, 'id' | 'projectId' | 'sceneId' | 'bundleId'>) {
    setFlakyDraft((current) => ({
      ...current,
      projectId: run.projectId,
      sceneId: run.sceneId,
      runId: run.id
    }));
    setRunDraft((current) => ({
      ...current,
      projectId: run.projectId,
      sceneId: run.sceneId,
      bundleId: run.bundleId
    }));
  }

  function applyFlakyDefaults(item: Pick<UiE2eFlakyMark, 'projectId' | 'sceneId' | 'runId' | 'status' | 'reasonCode' | 'reasonSummary'>) {
    setFlakyDraft((current) => ({
      ...current,
      projectId: item.projectId,
      sceneId: item.sceneId || '',
      runId: item.runId || '',
      status: item.status,
      reasonCode: item.reasonCode || '',
      reasonSummary: item.reasonSummary || ''
    }));
  }

  function loadSelectedSceneIntoDraft() {
    if (!sceneDetail) {
      return;
    }
    setEditingSceneId(sceneDetail.id);
    setSceneDraft(sceneDraftFromDetail(sceneDetail));
    setSceneActionState({ loading: false });
  }

  function cancelSceneEditing() {
    setEditingSceneId('');
    setSceneDraft(blankUiE2eSceneDraft(sceneDetail ? {
      projectId: sceneDetail.projectId,
      applicationId: sceneDetail.applicationId || '',
      environmentId: sceneDetail.environmentId || ''
    } : {
      projectId: sceneDraft.projectId,
      applicationId: sceneDraft.applicationId,
      environmentId: sceneDraft.environmentId
    }));
    setSceneActionState({ loading: false });
  }
}

function SceneDetailPanel(props: {
  detail: UiE2eSceneDetail | null;
  activity: ReturnType<typeof buildUiE2eSceneActivitySummary> | null;
  state: WorkState;
}) {
  if (!props.detail) {
    return <EmptyPanel title="场景详情" desc="选择场景后查看步骤模板、策略和来源摘要。" />;
  }
  const latestBundleSummary = props.activity?.latestBundle ? buildUiE2eBundleListSummary(props.activity.latestBundle) : null;
  const latestRunSummary = props.activity?.latestRun ? buildUiE2eRunListSummary(props.activity.latestRun) : null;
  return (
    <Panel title="场景详情" desc={`${props.detail.projectId} · ${props.detail.code}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`}>{props.detail.status}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="riskLevel" value={props.detail.riskLevel} />
        <SummaryTile label="stepCount" value={String(props.detail.steps.length)} />
        <SummaryTile label="application" value={props.detail.applicationId || '-'} />
        <SummaryTile label="environment" value={props.detail.environmentId || '-'} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title="tags" value={props.detail.tags.join(', ') || '-'} />
        <InfoBlock title="policy" value={formatRecord(props.detail.policy)} />
        <InfoBlock title="sourceSummary" value={formatRecord(props.detail.sourceSummary)} />
        <InfoBlock title="updatedAt" value={props.detail.updatedAt ? formatDateTime(props.detail.updatedAt) : '-'} />
      </div>
      <div className="report-card-list">
        <div className="report-mini-card report-mini-card-muted">
          <div className="report-card-heading">
            <strong>最近 Bundle</strong>
            <span className="badge badge-neutral">{props.activity?.bundleCount ?? 0}</span>
          </div>
          {props.activity?.latestBundle && latestBundleSummary ? (
            <>
              <span>{props.activity.latestBundle.sceneCode || shortId(props.activity.latestBundle.id)} · {latestBundleSummary.headline}</span>
              <small>{latestBundleSummary.detail}</small>
              <small>
                {latestBundleSummary.signals.length ? `${latestBundleSummary.signals.join(' · ')} · ` : ''}
                {props.activity.latestBundle.updatedAt ? formatDateTime(props.activity.latestBundle.updatedAt) : props.activity.latestBundle.id}
              </small>
            </>
          ) : (
            <span>当前场景还没有关联的脚本包摘要。</span>
          )}
        </div>
        <div className="report-mini-card report-mini-card-muted">
          <div className="report-card-heading">
            <strong>最近 Run</strong>
            <span className="badge badge-neutral">{props.activity?.runCount ?? 0}</span>
          </div>
          {props.activity?.latestRun && latestRunSummary ? (
            <>
              <span>{props.activity.latestRun.sceneCode || shortId(props.activity.latestRun.id)} · {latestRunSummary.headline}</span>
              <small>{latestRunSummary.detail}</small>
              <small>
                {latestRunSummary.signals.length ? `${latestRunSummary.signals.join(' · ')} · ` : ''}
                {props.activity.latestRun.createdAt ? formatDateTime(props.activity.latestRun.createdAt) : props.activity.latestRun.id}
              </small>
            </>
          ) : (
            <span>当前场景还没有关联的运行摘要。</span>
          )}
        </div>
      </div>
      {props.detail.steps.length ? (
        <div className="ui-e2e-card-list">
          {props.detail.steps.map((step) => (
            <div className="report-mini-card" key={step.id}>
              <div className="report-card-heading">
                <strong>步骤 {step.stepOrder} · {step.stepType}</strong>
                <span className="badge badge-neutral">{shortId(step.id)}</span>
              </div>
              <div className="report-section-grid">
                <InfoBlock title="action" value={formatRecord(step.actionSummary)} />
                <InfoBlock title="locator" value={formatRecord(step.locatorStrategy)} />
                <InfoBlock title="assertion" value={formatRecord(step.assertionSummary)} />
                <InfoBlock title="waitPolicy" value={formatRecord(step.waitPolicy)} />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="notice info">当前场景还没有步骤模板。</div>
      )}
      <StateLine state={props.state} />
    </Panel>
  );
}

function BundleDetailPanel(props: { detail: UiE2eBundleDetail | null; exported: UiE2eBundleExport | null; state: WorkState }) {
  if (!props.detail) {
    return <EmptyPanel title="脚本包详情" desc="选择 bundle 后查看静态校验、评审流和运行前状态。" />;
  }
  return (
    <Panel title="脚本包详情" desc={`${props.detail.projectId} · ${props.detail.sceneCode || props.detail.sceneId}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`}>{props.detail.status}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="staticCheck" value={props.detail.staticCheckStatus || '-'} tone={statusTone(props.detail.staticCheckStatus || 'UNKNOWN')} />
        <SummaryTile label="sceneStatus" value={props.detail.sceneStatus || '-'} />
        <SummaryTile label="riskLevel" value={props.detail.riskLevel || '-'} />
        <SummaryTile label="reviews" value={String(props.detail.reviews.length)} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title="bundleDigest" value={props.detail.bundleDigest || '-'} />
        <InfoBlock title="tags" value={props.detail.tags.join(', ') || '-'} />
        <InfoBlock title="specSummary" value={formatRecord(props.detail.specSummary)} />
        <InfoBlock title="fixtureSummary" value={formatRecord(props.detail.fixtureSummary)} />
      </div>
      <PolicySummary policy={{ ...props.detail.staticCheckSummary, ...props.detail.policy }} />
      {props.detail.reviews.length ? (
        <div className="ui-e2e-card-list">
          {props.detail.reviews.map((review) => (
            <div className="report-mini-card" key={review.id}>
              <div className="report-card-heading">
                <strong>{review.reviewStatus}</strong>
                <span className="badge badge-neutral">{review.reviewedBy || '-'}</span>
              </div>
              <span>{review.reviewComment || '无备注'}</span>
              <small>{review.reviewedAt ? formatDateTime(review.reviewedAt) : review.createdAt ? formatDateTime(review.createdAt) : review.id}</small>
            </div>
          ))}
        </div>
      ) : (
        <div className="notice info">暂无评审记录。</div>
      )}
      {props.exported ? (
        <div className="report-card-list">
          <div className="report-mini-card">
            <div className="report-card-heading">
              <strong>导出摘要</strong>
              <span className="badge badge-neutral">{props.exported.schemaVersion}</span>
            </div>
            <div className="report-section-grid">
              <InfoBlock title="exportedAt" value={props.exported.exportedAt ? formatDateTime(props.exported.exportedAt) : '-'} />
              <InfoBlock title="reviewSummary" value={formatRecord(props.exported.reviewSummary)} />
              <InfoBlock title="redactionPolicy" value={formatRecord(props.exported.redactionPolicy)} />
              <InfoBlock title="exportPolicy" value={formatRecord(props.exported.bundle.policy)} />
            </div>
          </div>
        </div>
      ) : (
        <div className="notice info">可导出 aggregate-only 脚本包摘要，不包含评审备注原文、审阅人身份或任何原始脚本内容。</div>
      )}
      <StateLine state={props.state} />
    </Panel>
  );
}

function RunDetailPanel(props: {
  detail: UiE2eRunDetail | null;
  exported: UiE2eRunExport | null;
  state: WorkState;
  canExport: boolean;
  canFlaky: boolean;
  flakyState: WorkState;
  onDownloadArtifact: (artifact: UiE2eArtifactManifest) => void;
  onApplyFlakyPreset: (status: UiE2eFlakyDraft['status'], reasonCode: string, reasonSummary: string) => void;
}) {
  if (!props.detail) {
    return <EmptyPanel title="运行详情" desc="选择 run 后查看步骤结果、失败分类、artifact manifest 和导出摘要。" />;
  }
  const executionSummary = props.detail.executionSummary;
  const diagnosis = buildUiE2eRunDiagnosis(props.detail);
  const flakyGuidance = buildUiE2eRunFlakyGuidance(props.detail);
  const auditTimeline = buildUiE2eRunAuditTimeline(props.detail);
  return (
    <Panel title="运行详情" desc={`${props.detail.projectId} · ${props.detail.sceneCode || props.detail.sceneId}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`}>{props.detail.status}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="runnerMode" value={props.detail.runnerMode} />
        <SummaryTile label="flaky" value={props.detail.flakyMark?.status || props.detail.flakyStatus || 'NONE'} tone={statusTone(props.detail.flakyMark?.status || props.detail.flakyStatus || 'NONE')} />
        <SummaryTile label="steps" value={String(props.detail.stepResults.length)} />
        <SummaryTile label="artifacts" value={String(props.detail.artifacts.length)} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title="failureCode" value={props.detail.failureCode || '-'} />
        <InfoBlock title="traceId" value={props.detail.traceId || props.state.traceId || '-'} />
        <InfoBlock title="accountSummary" value={formatRecord(props.detail.accountSummary)} />
        <InfoBlock title="executionSummary" value={formatRecord(executionSummary)} />
      </div>
      <div className={`notice ${diagnosis.tone}`}>
        <strong>诊断 · {diagnosis.label}</strong>
        <span>{diagnosis.summary}</span>
        {diagnosis.primaryFailureBucket ? <span>主要失败桶：{diagnosis.primaryFailureBucket}</span> : null}
        {diagnosis.blockedArtifactCount > 0 ? <span>受阻 artifact：{diagnosis.blockedArtifactCount}</span> : null}
        {!diagnosis.rawArtifactDownloadReady && props.detail.artifacts.length ? <span>artifact 当前仅提供 manifest 摘要，不提供原始下载。</span> : null}
      </div>
      {diagnosis.signals.length ? (
        <div className="report-policy-list">
          <div className="report-policy-title">诊断信号</div>
          {diagnosis.signals.map((item) => <span key={item}>{item}</span>)}
        </div>
      ) : null}
      {diagnosis.nextActions.length ? (
        <div className="report-policy-list">
          <div className="report-policy-title">建议动作</div>
          {diagnosis.nextActions.map((item) => <span key={item}>{item}</span>)}
        </div>
      ) : null}
      <RunAuditTimeline timeline={auditTimeline} />
      <div className={`notice ${flakyGuidance.tone}`}>
        <strong>Flaky 治理 · {flakyGuidance.label}</strong>
        <span>{flakyGuidance.summary}</span>
      </div>
      {props.canFlaky ? (
        flakyGuidance.presets.length ? (
          <div className="report-actions-row">
            {flakyGuidance.presets.map((preset) => (
              <button
                className={`btn ${preset.status === 'CONFIRMED_FLAKY' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                key={`${preset.status}-${preset.reasonCode}`}
                type="button"
                title={preset.reasonSummary}
                disabled={props.flakyState.loading}
                onClick={() => props.onApplyFlakyPreset(preset.status, preset.reasonCode, preset.reasonSummary)}
              >
                <Bug size={15} />{preset.label}
              </button>
            ))}
          </div>
        ) : (
          <div className="notice info">当前运行暂无快捷 Flaky 动作，仍可在下方 Flaky 面板按场景或运行手动治理。</div>
        )
      ) : (
        <div className="notice info">当前账号缺少 `uiE2e:flaky` 权限，因此这里只展示治理建议，不开放快捷标记。</div>
      )}
      <StateLine state={props.flakyState} />
      {props.detail.failureCode === 'UI_E2E_EXPORT_DISABLED' && (
        <div className="notice warning">当前环境禁用了 run export；仍可在工作台内继续查看聚合详情与 traceId。</div>
      )}
      <StepResultsList steps={props.detail.stepResults} />
      <ArtifactList
        artifacts={props.detail.artifacts}
        canExport={props.canExport}
        downloading={props.state.loading}
        onDownloadArtifact={props.onDownloadArtifact}
      />
      {props.exported ? (
        <div className="report-card-list">
          <div className="report-mini-card">
            <div className="report-card-heading">
              <strong>导出摘要</strong>
              <span className="badge badge-neutral">{props.exported.schemaVersion}</span>
            </div>
            <div className="report-section-grid">
              <InfoBlock title="exportedAt" value={props.exported.exportedAt ? formatDateTime(props.exported.exportedAt) : '-'} />
              <InfoBlock title="redactionPolicy" value={formatRecord(props.exported.redactionPolicy)} />
            </div>
          </div>
        </div>
      ) : (
        <div className="notice info">可导出 aggregate-only 运行摘要，不包含 secretRef 明文、原始 DOM 或 artifact 正文。</div>
      )}
      {props.state.error || props.state.success || props.state.traceId || props.state.loading ? <StateLine state={props.state} /> : null}
    </Panel>
  );
}

function RunAuditTimeline(props: {
  timeline: Array<{
    id: string;
    kindLabel: string;
    title: string;
    detail: string;
    occurredAt?: string;
    tone: 'success' | 'info' | 'warning' | 'danger';
  }>;
}) {
  if (!props.timeline.length) {
    return <div className="notice info">当前运行还没有可聚合的审计时间线摘要。</div>;
  }
  return (
    <div className="ui-e2e-run-audit-timeline">
      <div className="report-policy-title">运行审计时间线</div>
      {props.timeline.map((item) => (
        <div className={`ui-e2e-run-audit-event tone-${item.tone}`} key={item.id}>
          <strong>{item.title}</strong>
          <span>{item.detail}</span>
          <em>{item.kindLabel}{item.occurredAt ? ` · ${formatDateTime(item.occurredAt)}` : ''}</em>
        </div>
      ))}
    </div>
  );
}

function FlakyDetailPanel(props: { item: UiE2eFlakyMark | null; state: WorkState }) {
  if (!props.item) {
    return <EmptyPanel title="Flaky 详情" desc="选择 Flaky 标记后查看原因、场景和运行关联。" />;
  }
  const insight = buildUiE2eFlakyDetailInsight(props.item);
  return (
    <Panel title="Flaky 详情" desc={`${props.item.projectId} · ${props.item.sceneCode || props.item.sceneId || '-'}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.item.status)}`}>{props.item.status}</span>
        <span className="report-mono">{props.item.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="scene" value={props.item.sceneCode || shortId(props.item.sceneId)} />
        <SummaryTile label="riskLevel" value={props.item.sceneRiskLevel || '-'} tone={statusTone(props.item.sceneRiskLevel || 'UNKNOWN')} />
        <SummaryTile label="linkedRuns" value={String(props.item.linkedRunCount)} />
        <SummaryTile label="runStatus" value={props.item.runStatus || '-'} />
        <SummaryTile label="latestFailure" value={props.item.latestFailureBucket || '-'} tone={statusTone(props.item.runStatus || props.item.status)} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title="reasonCode" value={props.item.reasonCode || '-'} />
        <InfoBlock title="reasonSummary" value={props.item.reasonSummary || '-'} />
        <InfoBlock title="runId" value={props.item.runId || '-'} />
        <InfoBlock title="sceneName" value={props.item.sceneName || '-'} />
        <InfoBlock title="createdBy" value={props.item.createdBy || '-'} />
        <InfoBlock title="updatedBy" value={props.item.updatedBy || '-'} />
        <InfoBlock title="createdAt" value={props.item.createdAt ? formatDateTime(props.item.createdAt) : '-'} />
        <InfoBlock title="updatedAt" value={props.item.updatedAt ? formatDateTime(props.item.updatedAt) : '-'} />
      </div>
      <div className={`notice ${insight.tone}`}>
        <strong>治理提示 · {insight.label}</strong>
        <span>{insight.summary}</span>
        {insight.signals.length ? <span>{insight.signals.join(' · ')}</span> : null}
      </div>
      <div className="notice info">
        <strong>审计可见性</strong>
        <span>该视图展示创建人与更新人、时间戳、关联场景/运行和原因摘要，便于后续治理复盘。</span>
      </div>
      <StateLine state={props.state} />
    </Panel>
  );
}

function StepResultsList(props: { steps: UiE2eRunStepResult[] }) {
  if (!props.steps.length) {
    return <div className="notice info">暂无步骤结果摘要。</div>;
  }
  return (
    <div className="report-card-list">
      {props.steps.map((step) => (
        <div className="report-mini-card" key={step.id}>
          <div className="report-card-heading">
            <strong>步骤 {step.stepOrder} · {step.status}</strong>
            <span className={`badge badge-${statusTone(step.status)}`}>{step.failureBucket || 'NO_FAILURE'}</span>
          </div>
          <div className="report-section-grid">
            <InfoBlock title="durationMs" value={String(step.durationMs)} />
            <InfoBlock title="errorCode" value={step.errorCode || '-'} />
            <InfoBlock title="summary" value={formatRecord(step.summary)} />
            <InfoBlock title="sceneStepId" value={step.sceneStepId || '-'} />
          </div>
          {step.failureBucket ? <small>{explainUiE2eFailureBucket(step.failureBucket)}</small> : null}
        </div>
      ))}
    </div>
  );
}

function ArtifactList(props: {
  artifacts: UiE2eArtifactManifest[];
  canExport: boolean;
  downloading: boolean;
  onDownloadArtifact: (artifact: UiE2eArtifactManifest) => void;
}) {
  if (!props.artifacts.length) {
    return <div className="notice info">暂无 artifact manifest。</div>;
  }
  return (
    <div className="report-card-list">
      {props.artifacts.map((artifact) => (
        <ArtifactCard
          key={artifact.id}
          artifact={artifact}
          canExport={props.canExport}
          downloading={props.downloading}
          onDownloadArtifact={props.onDownloadArtifact}
        />
      ))}
    </div>
  );
}

function ArtifactCard(props: {
  artifact: UiE2eArtifactManifest;
  canExport: boolean;
  downloading: boolean;
  onDownloadArtifact: (artifact: UiE2eArtifactManifest) => void;
}) {
  const downloadState = buildUiE2eArtifactDownloadState(props.artifact);
  return (
    <div className="report-mini-card">
          <div className="report-card-heading">
        <strong>{props.artifact.artifactType}</strong>
        <span className={`badge badge-${statusTone(props.artifact.captureStatus)}`}>{props.artifact.captureStatus}</span>
          </div>
          <div className="report-section-grid">
        <InfoBlock title="digest" value={props.artifact.artifactDigest || '-'} />
        <InfoBlock title="storageRef" value={props.artifact.storageRef || '-'} />
        <InfoBlock title="sizeBytes" value={String(props.artifact.sizeBytes)} />
        <InfoBlock title="redactionFlags" value={formatRecord(props.artifact.redactionFlags)} />
          </div>
          <div className="report-actions-row">
            <button
              className="btn btn-secondary btn-sm"
              type="button"
          disabled={!props.canExport || props.downloading || !downloadState.canDownload}
          title={!props.canExport ? '当前账号缺少 uiE2e:export 权限。' : downloadState.summary}
          onClick={() => props.onDownloadArtifact(props.artifact)}
            >
              <Download size={15} />下载产物
            </button>
          </div>
          <div className={`notice ${downloadState.tone}`}>
            <span>{downloadState.summary}</span>
          </div>
      {props.artifact.captureStatus === 'BLOCKED' ? (
        <small>{explainUiE2eArtifactCaptureBlockedReason(extractUiE2eArtifactCaptureBlockedReason(props.artifact.redactionFlags))}</small>
          ) : null}
    </div>
  );
}

function ListPanel<T extends { id: string }>(props: {
  items: T[];
  selectedId: string;
  emptyTitle: string;
  emptyDesc: string;
  onSelect: (item: T) => void;
  renderItem: (item: T) => ReactNode;
}) {
  return (
    <div className="ui-e2e-list">
      {props.items.length ? (
        props.items.map((item) => (
          <button
            className={`ui-e2e-list-item${props.selectedId === item.id ? ' active' : ''}`}
            key={item.id}
            type="button"
            onClick={() => props.onSelect(item)}
          >
            {props.renderItem(item)}
          </button>
        ))
      ) : (
        <div className="empty-state">
          <MonitorPlaceholder />
          <strong>{props.emptyTitle}</strong>
          <span>{props.emptyDesc}</span>
        </div>
      )}
    </div>
  );
}

function EmptyPanel(props: { title: string; desc: string }) {
  return (
    <Panel title={props.title} desc={props.desc}>
      <div className="empty-state">
        <MonitorPlaceholder />
        <strong>等待选择</strong>
        <span>{props.desc}</span>
      </div>
    </Panel>
  );
}

function Panel(props: { title: string; desc?: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2 className="panel-title">{props.title}</h2>
          {props.desc ? <p className="panel-desc">{props.desc}</p> : null}
        </div>
        {props.action ? <div className="toolbar-actions">{props.action}</div> : null}
      </div>
      <div className="panel-body">
        {props.children}
      </div>
    </section>
  );
}

function Metric(props: { icon: ReactNode; label: string; value: string; desc: string; tone: 'success' | 'info' | 'warning' | 'danger' }) {
  return (
    <div className="metric-card">
      <div className={`metric-icon ${props.tone}`}>{props.icon}</div>
      <div className="metric-body">
        <span className="metric-label">{props.label}</span>
        <strong className="metric-value">{props.value}</strong>
        <span className="metric-desc">{props.desc}</span>
      </div>
    </div>
  );
}

function Field(props: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{props.label}</span>
      {props.children}
    </label>
  );
}

function SummaryTile(props: { label: string; value: string; tone?: string }) {
  return (
    <div className="report-summary-tile">
      <span>{props.label}</span>
      <strong className={props.tone ? `tone-${props.tone}` : undefined}>{props.value}</strong>
    </div>
  );
}

function InfoBlock(props: { title: string; value: string }) {
  return (
    <div className="report-info-block">
      <span>{props.title}</span>
      <strong>{props.value}</strong>
    </div>
  );
}

function PolicySummary(props: { policy: Record<string, unknown> }) {
  const entries = Object.entries(props.policy).slice(0, 10);
  if (!entries.length) return null;
  return (
    <div className="report-policy-list">
      <div className="report-policy-title"><ShieldCheck size={15} />策略摘要</div>
      {entries.map(([key, value]) => (
        <span key={key}>{key}={formatRecord(value)}</span>
      ))}
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">处理中...</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function MonitorPlaceholder() {
  return <FileText className="empty-state-icon" size={30} />;
}

function compactFilters(filters: SimpleFilters) {
  return {
    projectId: optionalText(filters.projectId),
    status: optionalText(filters.status),
    keyword: optionalText(filters.keyword)
  };
}

function compactSceneFilters(filters: SceneFilters) {
  return {
    projectId: optionalText(filters.projectId),
    applicationId: optionalText(filters.applicationId),
    environmentId: optionalText(filters.environmentId),
    status: optionalText(filters.status),
    riskLevel: optionalText(filters.riskLevel),
    tag: optionalText(filters.tag),
    keyword: optionalText(filters.keyword)
  };
}

function retainSelection<T extends { id: string }>(current: string, items: T[]) {
  return items.some((item) => item.id === current) ? current : items[0]?.id || '';
}

function summaryFromSceneDetail(detail: UiE2eSceneDetail): UiE2eSceneSummary {
  return {
    id: detail.id,
    projectId: detail.projectId,
    applicationId: detail.applicationId,
    environmentId: detail.environmentId,
    code: detail.code,
    name: detail.name,
    status: detail.status,
    riskLevel: detail.riskLevel,
    tags: detail.tags,
    sourceSummary: detail.sourceSummary,
    stepCount: detail.steps.length,
    archivedAt: detail.archivedAt,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt
  };
}

function summaryFromBundleDetail(detail: UiE2eBundleDetail): UiE2eBundleSummary {
  return {
    id: detail.id,
    projectId: detail.projectId,
    sceneId: detail.sceneId,
    sceneCode: detail.sceneCode,
    sceneName: detail.sceneName,
    sceneStatus: detail.sceneStatus,
    status: detail.status,
    bundleDigest: detail.bundleDigest,
    staticCheckStatus: detail.staticCheckStatus,
    staticCheckSummary: detail.staticCheckSummary,
    submittedAt: detail.submittedAt,
    approvedAt: detail.approvedAt,
    rejectedAt: detail.rejectedAt,
    archivedAt: detail.archivedAt,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt
  };
}

function summaryFromRunDetail(detail: UiE2eRunDetail): UiE2eRunSummary {
  return {
    id: detail.id,
    projectId: detail.projectId,
    sceneId: detail.sceneId,
    sceneCode: detail.sceneCode,
    sceneName: detail.sceneName,
    bundleId: detail.bundleId,
    status: detail.status,
    requestKey: detail.requestKey,
    runnerMode: detail.runnerMode,
    failureCode: detail.failureCode,
    failureSummary: detail.failureSummary,
    traceId: detail.traceId,
    accountSummary: detail.accountSummary,
    flakyStatus: detail.flakyMark?.status || detail.flakyStatus,
    startedAt: detail.startedAt,
    finishedAt: detail.finishedAt,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt
  };
}

function statusTone(status: string) {
  if (['APPROVED', 'SUCCEEDED', 'CAPTURED', 'READY', 'CONFIRMED_FLAKY'].includes(status)) return 'success';
  if (['DRAFT', 'REVIEWING', 'QUEUED', 'RUNNING', 'PENDING', 'FLAKY_CANDIDATE'].includes(status)) return 'info';
  if (['FAILED', 'BLOCKED', 'REJECTED', 'CANCELED'].includes(status)) return 'danger';
  if (['ARCHIVED', 'NONE', 'WAIVED', 'SKIPPED', 'DISABLED'].includes(status)) return 'neutral';
  return 'warning';
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function shortId(value?: string) {
  if (!value) return '-';
  return value.length > 14 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

function formatRecord(input: unknown): string {
  if (input == null || input === '') return '-';
  if (Array.isArray(input)) return input.map((item) => formatRecord(item)).join(', ');
  if (typeof input === 'object') {
    const text = prettyJson(input);
    return text.length > 180 ? `${text.slice(0, 177)}...` : text;
  }
  return String(input);
}

function downloadBlob(blob: Blob, filename: string, contentType: string) {
  const downloadBlobValue = blob.type ? blob : new Blob([blob], { type: contentType || 'application/octet-stream' });
  const url = URL.createObjectURL(downloadBlobValue);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function fallbackArtifactFileName(artifact: UiE2eArtifactManifest) {
  return `${artifact.artifactType.toLowerCase()}-${shortId(artifact.id).replace(/\.+/g, '-')}${artifactFileExtension(artifact)}`;
}

function artifactFileExtension(artifact: UiE2eArtifactManifest) {
  const loweredRef = (artifact.storageRef || '').toLowerCase();
  if (loweredRef.endsWith('.png')) return '.png';
  if (loweredRef.endsWith('.zip')) return '.zip';
  if (loweredRef.endsWith('.webm')) return '.webm';
  if (loweredRef.endsWith('.xml')) return '.xml';
  if (loweredRef.endsWith('.har')) return '.har';

  switch (artifact.artifactType) {
    case 'SCREENSHOT':
      return '.png';
    case 'TRACE':
      return '.zip';
    case 'VIDEO':
      return '.webm';
    case 'JUNIT_XML':
      return '.xml';
    case 'HAR':
      return '.har';
    default:
      return '.log';
  }
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}
