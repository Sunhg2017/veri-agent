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
import { Drawer } from 'antd';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  archiveUiE2eScene,
  approveUiE2eBundle,
  archiveUiE2eBundle,
  backfillUiE2eRunSummary,
  cancelUiE2eRun,
  createUiE2eBatchRun,
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
  importUiE2eScene,
  downloadUiE2eArtifact,
  rejectUiE2eBundle,
  submitUiE2eBundleReview,
  updateUiE2eScene,
  upsertUiE2eFlakyMark,
  type UiE2eArtifactManifest,
  type UiE2eBatchRun,
  type UiE2eBundleDetail,
  type UiE2eBundleExport,
  type UiE2eBundleSummary,
  type UiE2eFlakyMark,
  type UiE2eHealth,
  type UiE2eRunDetail,
  type UiE2eRunExport,
  type UiE2eRunSummaryBackfill,
  type UiE2eRunSummary,
  type UiE2eRunStepResult,
  type UiE2eSceneDetail,
  type UiE2eSceneImportSourceType,
  type UiE2eSceneSummary
} from '../api/uiE2e';
import { canUseButton, hasPermission } from '../permissions';
import { dictionaryLabel, displayValueLabel, fieldLabel } from '../platform/dictionaries';
import {
  blankUiE2eSceneDraft,
  buildUiE2eBundleListSummary,
  buildUiE2eArtifactDownloadState,
  buildUiE2eBatchRunPayload,
  buildUiE2eBatchRunReadiness,
  buildUiE2eBatchRunSummary,
  buildUiE2eFlakyDetailInsight,
  buildUiE2eBundleQueueOverview,
  buildUiE2eFlakyListSummary,
  buildUiE2eRunFlakyGuidance,
  buildUiE2eFlakyQueueOverview,
  buildUiE2eRunBackfillPayload,
  buildUiE2eRunBackfillReadiness,
  buildUiE2eRunBackfillSummary,
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
  initialUiE2eBatchRunDraft,
  initialUiE2eFlakyDraft,
  initialUiE2eRunBackfillDraft,
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
  sceneDraftFromImport,
  splitTags,
  type UiE2eBatchRunDraft,
  type UiE2eBundleFocusMode,
  type UiE2eFlakyDraft,
  type UiE2eFlakyFocusMode,
  type UiE2eRunBackfillDraft,
  type UiE2eRunFocusMode,
  type UiE2eRunDraft,
  type UiE2eSceneDraft,
  type UiE2eSceneFocusMode,
  type UiE2eSceneStepDraft
} from '../uiE2eWorkbenchState';
import { translate } from '../platform/i18n';
import { CheckboxControl, InputControl, SelectControl, TextAreaControl } from './ui';

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

type UiE2eDrawer = 'scene' | 'bundle' | 'run' | 'batchRun' | 'backfill' | 'flaky' | null;

type SceneFilters = SimpleFilters & {
  applicationId: string;
  environmentId: string;
  riskLevel: string;
  tag: string;
};

type UiE2eSceneImportDraft = {
  sourceType: UiE2eSceneImportSourceType;
  codeHint: string;
  nameHint: string;
  tagsText: string;
  content: string;
};

const initialFilters: SimpleFilters = { projectId: '', status: '', keyword: '' };
const initialSceneFilters: SceneFilters = {
  ...initialFilters,
  applicationId: '',
  environmentId: '',
  riskLevel: '',
  tag: ''
};

const initialSceneImportDraft: UiE2eSceneImportDraft = {
  sourceType: 'PLAYWRIGHT_CODEGEN',
  codeHint: '',
  nameHint: '',
  tagsText: '',
  content: ''
};

const browserOptions = ['CHROMIUM', 'FIREFOX', 'WEBKIT'].map((value) => ({
  label: dictionaryLabel(value),
  searchLabel: `${value} ${dictionaryLabel(value)}`,
  value
}));

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
  const [sceneImportDraft, setSceneImportDraft] = useState<UiE2eSceneImportDraft>(initialSceneImportDraft);
  const [bundleSceneId, setBundleSceneId] = useState('');
  const [reviewNote, setReviewNote] = useState('');
  const [runDraft, setRunDraft] = useState<UiE2eRunDraft>(initialUiE2eRunDraft);
  const [batchRunDraft, setBatchRunDraft] = useState<UiE2eBatchRunDraft>(initialUiE2eBatchRunDraft);
  const [batchRunResult, setBatchRunResult] = useState<UiE2eBatchRun | null>(null);
  const [runBackfillDraft, setRunBackfillDraft] = useState<UiE2eRunBackfillDraft>(initialUiE2eRunBackfillDraft);
  const [runBackfillResult, setRunBackfillResult] = useState<UiE2eRunSummaryBackfill | null>(null);
  const [flakyDraft, setFlakyDraft] = useState<UiE2eFlakyDraft>(initialUiE2eFlakyDraft);
  const [openDrawer, setOpenDrawer] = useState<UiE2eDrawer>(null);

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
      ? translate('auto.k1841')
      : !runCreationReadiness.ready
        ? runCreationReadiness.summary
        : undefined;
  const runBatchReadiness = useMemo(
    () => buildUiE2eBatchRunReadiness({ health, draft: batchRunDraft, scenes }),
    [batchRunDraft, health, scenes]
  );
  const runBatchSummary = useMemo(
    () => batchRunResult ? buildUiE2eBatchRunSummary(batchRunResult) : null,
    [batchRunResult]
  );
  const runBatchDisabled = !canExecute || runActionState.loading || !runBatchReadiness.ready;
  const runBackfillReadiness = useMemo(
    () => buildUiE2eRunBackfillReadiness({ health, draft: runBackfillDraft }),
    [health, runBackfillDraft]
  );
  const runBackfillSummary = useMemo(
    () => runBackfillResult ? buildUiE2eRunBackfillSummary(runBackfillResult) : null,
    [runBackfillResult]
  );
  const runBackfillDisabled = !canManage || runActionState.loading || !runBackfillReadiness.ready;

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
      setLoadState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1842') });
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
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1843') });
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
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1844') });
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
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0822') });
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
      setFlakyActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1845') });
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
    return <div className="notice warning">{translate('auto.k1846')}</div>;
  }

  if (!canRead) {
    return <div className="notice error">{translate('auto.k1847')}</div>;
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
        setOpenDrawer(null);
        setSceneActionState({ loading: false, success: translate('auto.k1848'), traceId: result.trace_id });
      } catch (error: unknown) {
        setSceneActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1849') });
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
      setOpenDrawer(null);
      setSceneActionState({ loading: false, success: translate('auto.k1850'), traceId: result.trace_id });
    } catch (error: unknown) {
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1851') });
    }
  }

  async function onImportScene() {
    if (!canManage) return;
    if (!sceneDraft.projectId.trim()) {
      setSceneActionState({ loading: false, error: translate('auto.k1852') });
      return;
    }
    if (!sceneImportDraft.content.trim()) {
      setSceneActionState({ loading: false, error: translate('auto.k1853') });
      return;
    }
    setSceneActionState({ loading: true });
    try {
      const result = await importUiE2eScene({
        projectId: sceneDraft.projectId.trim(),
        applicationId: sceneDraft.applicationId.trim() || undefined,
        environmentId: sceneDraft.environmentId.trim() || undefined,
        sourceType: sceneImportDraft.sourceType,
        content: sceneImportDraft.content,
        codeHint: sceneImportDraft.codeHint.trim() || undefined,
        nameHint: sceneImportDraft.nameHint.trim() || undefined,
        tags: splitTags(sceneImportDraft.tagsText)
      });
      setEditingSceneId('');
      setSceneDraft(sceneDraftFromImport(result.data));
      setSceneImportDraft((current) => ({
        ...current,
        codeHint: result.data.code,
        nameHint: result.data.name
      }));
      setSceneActionState({
        loading: false,
        success: result.data.warnings.length
          ? translate('auto.k1854', { value0: result.data.steps.length, value1: result.data.warnings.length })
          : translate('auto.k1855', { value0: result.data.steps.length }),
        traceId: result.traceId
      });
    } catch (error: unknown) {
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1856') });
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
      setSceneActionState({ loading: false, success: translate('auto.k1857'), traceId: result.trace_id });
    } catch (error: unknown) {
      setSceneActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1858') });
    }
  }

  async function onCreateBundle() {
    if (!canManage) return;
    const sceneId = bundleSceneId.trim() || selectedSceneId;
    if (!sceneId) {
      setBundleActionState({ loading: false, error: translate('auto.k1859') });
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
      setOpenDrawer(null);
      setBundleActionState({ loading: false, success: translate('auto.k0151'), traceId: result.trace_id });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1860') });
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
        success: action === 'submit' ? translate('auto.k1861') : action === 'approve' ? translate('auto.k1862') : translate('auto.k0157'),
        traceId: response.trace_id
      });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1863') });
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
      setBundleActionState({ loading: false, success: translate('auto.k1864'), traceId: result.trace_id });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1865') });
    }
  }

  async function onExportBundle() {
    if (!bundleDetail || !canExport) return;
    setBundleActionState({ loading: true });
    try {
      const result = await exportUiE2eBundle(bundleDetail.id);
      setBundleExport(result.data);
      setBundleActionState({ loading: false, success: translate('auto.k1866'), traceId: result.trace_id });
    } catch (error: unknown) {
      setBundleActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1867') });
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
      setOpenDrawer(null);
      setRunActionState({
        loading: false,
        success: result.data.idempotentReplay ? translate('auto.k1868') : translate('auto.k1869'),
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1870') });
    }
  }

  async function onCreateBatchRun() {
    if (!canExecute) return;
    const { payload, issues } = buildUiE2eBatchRunPayload(batchRunDraft);
    if (!payload || issues.length) {
      setRunActionState({ loading: false, error: issues.join('；') });
      return;
    }
    setRunActionState({ loading: true });
    try {
      const result = await createUiE2eBatchRun(payload);
      setBatchRunResult(result.data);
      const createdRuns = result.data.items
        .map((item) => item.run)
        .filter((item): item is UiE2eRunDetail => Boolean(item));
      if (createdRuns.length) {
        setRuns((current) => mergeRunSummaries(createdRuns, current));
        setSelectedRunId(createdRuns[0].id);
        setRunDetail(createdRuns[0]);
        applyRunDefaults(createdRuns[0]);
      }
      await refreshWorkbench();
      if (createdRuns.length) {
        await refreshRunDetail(createdRuns[0].id);
        setSelectedRunId(createdRuns[0].id);
      }
      setRunActionState({
        loading: false,
        success: translate('auto.k1871', { value0: result.data.createdCount, value1: result.data.replayedCount, value2: result.data.failedCount }),
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1872') });
    }
  }

  async function onCancelRun() {
    if (!runDetail || !canExecute || !isUiE2eRunActiveStatus(runDetail.status)) return;
    setRunActionState({ loading: true });
    try {
      const result = await cancelUiE2eRun(runDetail.id, { reason: runDraft.reason });
      setRunDetail(result.data);
      setRuns((current) => current.map((run) => run.id === result.data.id ? summaryFromRunDetail(result.data) : run));
      setRunActionState({ loading: false, success: translate('auto.k1873', { value0: result.data.status }), traceId: result.trace_id });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1874') });
    }
  }

  async function onExportRun() {
    if (!runDetail || !canExport) return;
    setRunActionState({ loading: true });
    try {
      const result = await exportUiE2eRun(runDetail.id);
      setRunExport(result.data);
      setRunActionState({ loading: false, success: translate('auto.k1875'), traceId: result.trace_id });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1876') });
    }
  }

  async function onBackfillRunSummary() {
    if (!canManage) return;
    const { payload, issues } = buildUiE2eRunBackfillPayload(runBackfillDraft);
    if (!payload || issues.length) {
      setRunActionState({ loading: false, error: issues.join('；') });
      return;
    }
    setRunActionState({ loading: true });
    try {
      const result = await backfillUiE2eRunSummary(payload);
      setRunBackfillResult(result.data);
      await refreshWorkbench();
      if (selectedRunId) {
        await refreshRunDetail(selectedRunId);
      }
      setRunActionState({
        loading: false,
        success: translate('auto.k1877', { value0: result.data.updatedCount, value1: result.data.unchangedCount, value2: result.data.failedCount }),
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1878') });
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
        success: translate('auto.k0843', { value0: displayValueLabel(artifact.artifactType) }),
        traceId: response.traceId
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1879') });
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
      setOpenDrawer(null);
      setFlakyActionState({ loading: false, success: translate('auto.k1880'), traceId: result.trace_id });
    } catch (error: unknown) {
      setFlakyActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1881') });
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

  const runnerCapacity = health?.runnerCapacity ?? {};
  const runnerActiveWorkers = recordNumber(runnerCapacity.activeWorkers);
  const runnerAvailableWorkers = recordNumber(runnerCapacity.availableWorkers, health?.maxConcurrency ?? 0);
  const runnerQueuedTasks = recordNumber(runnerCapacity.queuedTasks);
  const runnerCompletedTasks = recordNumber(runnerCapacity.completedTaskCount);
  const runnerPoolReady = recordBoolean(runnerCapacity.sharedBrowserPoolReady);
  const batchRunReady = recordBoolean(runnerCapacity.batchRunReady);
  const summaryBackfillReady = recordBoolean(runnerCapacity.summaryBackfillReady);
  const runnerSaturated = recordBoolean(runnerCapacity.saturated);

  return (
    <div className="ui-e2e-workbench" data-testid="ui-e2e-workbench">
      <section className="metrics-grid">
        <Metric icon={<CheckCircle2 size={20} />} label={translate('auto.k1882')} value={String(overview.approvedScenes)} desc={displayValueLabel(health?.runnerMode || translate('auto.k1118'))} tone="success" />
        <Metric icon={<FileText size={20} />} label={translate('auto.k1883')} value={String(overview.reviewingBundles)} desc={health?.artifactPolicy ? '产物策略已就绪' : translate('auto.k1118')} tone="info" />
        <Metric icon={<Play size={20} />} label={translate('auto.k1884')} value={String(overview.activeRuns)} desc={overview.runnerLabel} tone={overview.runnerTone} />
        <Metric icon={<AlertTriangle size={20} />} label={translate('auto.k1885')} value={String(overview.recentFailures)} desc={overview.blockedRuns ? `blocked=${overview.blockedRuns}` : translate('auto.k1886')} tone={overview.recentFailures ? 'danger' : overview.blockedRuns ? 'warning' : 'success'} />
        <Metric icon={<ShieldCheck size={20} />} label={fieldLabel('allowlist')} value={overview.allowlistLabel} desc={health ? `${fieldLabel('allowlist')} ${health.allowlistHostCount}` : translate('auto.k1118')} tone={overview.allowlistTone} />
        <Metric icon={<Bug size={20} />} label={dictionaryLabel('CONFIRMED_FLAKY')} value={String(overview.confirmedFlaky)} desc={`${fieldLabel('export')} ${displayValueLabel(health?.exportEnabled ? 'ON' : 'OFF')}`} tone={overview.confirmedFlaky ? 'warning' : 'info'} />
      </section>

      <div className="ui-e2e-layout">
        <section className="ui-e2e-list-column">
          <Panel
            title={translate('auto.k1887')}
            desc={health ? `${health.service} · ${health.status}` : translate('auto.k1888')}
            action={(
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void refreshWorkbench()} disabled={loadState.loading}>
                <RefreshCw size={15} />{translate('auto.k0170')}</button>
            )}
          >
            {health ? (
              <>
                <div className="ui-e2e-health-grid">
                  <SummaryTile label={fieldLabel('runnerMode')} value={displayValueLabel(health.runnerMode || '-')} />
                  <SummaryTile label={fieldLabel('runnerEnabled')} value={displayValueLabel(health.runnerEnabled ? 'ON' : 'OFF')} tone={health.runnerEnabled ? 'success' : 'warning'} />
                  <SummaryTile label={fieldLabel('allowlist')} value={health.allowlistEnabled ? `${displayValueLabel('ON')} (${health.allowlistHostCount})` : displayValueLabel('OFF')} />
                  <SummaryTile label={fieldLabel('export')} value={displayValueLabel(health.exportEnabled ? 'ON' : 'OFF')} />
                  <SummaryTile label={fieldLabel('browserPool')} value={dictionaryLabel(runnerPoolReady ? 'READY' : 'PENDING')} tone={runnerPoolReady ? 'success' : 'warning'} />
                  <SummaryTile label={fieldLabel('batchRun')} value={dictionaryLabel(batchRunReady ? 'READY' : 'PENDING')} tone={batchRunReady ? 'success' : 'warning'} />
                  <SummaryTile label={fieldLabel('backfill')} value={dictionaryLabel(summaryBackfillReady ? 'READY' : 'PENDING')} tone={summaryBackfillReady ? 'success' : 'warning'} />
                  <SummaryTile label={fieldLabel('saturated')} value={displayValueLabel(runnerSaturated ? 'YES' : 'NO')} tone={runnerSaturated ? 'warning' : 'success'} />
                </div>
                <div className="report-section-grid">
                  <InfoBlock title={fieldLabel('supportedNodeTypes')} value={health.supportedNodeTypes.map((type) => dictionaryLabel(type)).join('、') || '-'} />
                  <InfoBlock title={fieldLabel('maxConcurrency')} value={String(health.maxConcurrency)} />
                  <InfoBlock title={fieldLabel('activeWorkers')} value={String(runnerActiveWorkers)} />
                  <InfoBlock title={fieldLabel('availableWorkers')} value={String(runnerAvailableWorkers)} />
                  <InfoBlock title={fieldLabel('queuedTasks')} value={String(runnerQueuedTasks)} />
                  <InfoBlock title={fieldLabel('completedTasks')} value={String(runnerCompletedTasks)} />
                  <InfoBlock title={fieldLabel('defaultTimeout')} value={`${health.defaultTimeoutSeconds}s`} />
                  <InfoBlock title={fieldLabel('maxScenesPerRun')} value={String(health.maxScenesPerRun)} />
                  <InfoBlock title={fieldLabel('recentFailures')} value={String(overview.recentFailures)} />
                  <InfoBlock title={fieldLabel('blockedRuns')} value={String(overview.blockedRuns)} />
                </div>
                <PolicySummary policy={{ ...health.credentialPolicy, ...health.artifactPolicy, ...health.policy }} />
              </>
            ) : (
              <div className="notice info">{translate('auto.k1889')}</div>
            )}
            {overview.notices.map((notice) => (
              <div className={`notice ${notice.tone}`} key={notice.message}>{notice.message}</div>
            ))}
            <StateLine state={loadState} />
          </Panel>

          <Panel
            title={translate('auto.k1890')}
            desc={translate('auto.k1891')}
            action={(
              <div className="report-actions-row compact">
                <button className="btn btn-primary btn-sm" type="button" onClick={openCreateSceneDrawer} disabled={!canManage || sceneActionState.loading}>
                  <FileText size={15} />{translate('auto.k1903')}</button>
                <button className="btn btn-secondary btn-sm" type="button" onClick={openEditSceneDrawer} disabled={!canManage || sceneActionState.loading || !sceneDetail || sceneDetail.status === 'ARCHIVED'}>
                  <FileText size={15} />{translate('auto.k1905')}</button>
              </div>
            )}
          >
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <InputControl value={sceneFilters.projectId} onChange={(event) => setSceneFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <SelectControl value={sceneFilters.status} onChange={(event) => setSceneFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">{translate('auto.k0195')}</option>
                  <option value="DRAFT">{dictionaryLabel('DRAFT')}</option>
                  <option value="REVIEWING">{dictionaryLabel('REVIEWING')}</option>
                  <option value="APPROVED">{dictionaryLabel('APPROVED')}</option>
                  <option value="DISABLED">{dictionaryLabel('DISABLED')}</option>
                  <option value="ARCHIVED">{dictionaryLabel('ARCHIVED')}</option>
                </SelectControl>
              </Field>
              <Field label="applicationId">
                <InputControl
                  value={sceneFilters.applicationId}
                  onChange={(event) => setSceneFilters((current) => ({ ...current, applicationId: event.target.value }))}
                  placeholder="app-alpha"
                />
              </Field>
              <Field label="environmentId">
                <InputControl
                  value={sceneFilters.environmentId}
                  onChange={(event) => setSceneFilters((current) => ({ ...current, environmentId: event.target.value }))}
                  placeholder="staging"
                />
              </Field>
              <Field label={fieldLabel('riskLevel')}>
                <SelectControl value={sceneFilters.riskLevel} onChange={(event) => setSceneFilters((current) => ({ ...current, riskLevel: event.target.value }))}>
                  <option value="">{translate('auto.k0195')}</option>
                  <option value="LOW">{dictionaryLabel('LOW')}</option>
                  <option value="MEDIUM">{dictionaryLabel('MEDIUM')}</option>
                  <option value="HIGH">{dictionaryLabel('HIGH')}</option>
                  <option value="CRITICAL">{dictionaryLabel('CRITICAL')}</option>
                </SelectControl>
              </Field>
              <Field label="tag">
                <InputControl value={sceneFilters.tag} onChange={(event) => setSceneFilters((current) => ({ ...current, tag: event.target.value }))} placeholder="smoke" />
              </Field>
              <Field label="keyword">
                <InputControl value={sceneFilters.keyword} onChange={(event) => setSceneFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="code / name / tag" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />{translate('auto.k1130')}</button>
                <button
                  className="btn btn-secondary"
                  type="button"
                  disabled={loadState.loading}
                  onClick={() => setSceneFilters(initialSceneFilters)}
                >
                  {translate('auto.k0254')}</button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${sceneFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setSceneFocusMode('all')}
              >
                {translate('auto.k0195')}{scenes.length}
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
                {translate('auto.k1892')}{labelUiE2eSceneFocusMode(sceneFocusMode)}{translate('auto.k1893')}{visibleScenes.length} {translate('auto.k1894')}</div>
            )}
            <Drawer
              className="ui-e2e-drawer"
              destroyOnHidden
              maskClosable={!sceneActionState.loading}
              open={openDrawer === 'scene'}
              placement="right"
              title={editingSceneId ? translate('auto.k1902') : translate('auto.k1903')}
              width={900}
              onClose={() => {
                if (!sceneActionState.loading) {
                  setOpenDrawer(null);
                }
              }}
            >
            <form className="ui-e2e-form document-drawer-form" onSubmit={onSubmitScene}>
              <div className="form-grid">
                <Field label="projectId">
                  <InputControl value={sceneDraft.projectId} onChange={(event) => setSceneDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canManage || sceneActionState.loading || Boolean(editingSceneId)} />
                </Field>
                <Field label="applicationId">
                  <InputControl value={sceneDraft.applicationId} onChange={(event) => setSceneDraftValue('applicationId', event.target.value)} placeholder="app-alpha" disabled={!canManage || sceneActionState.loading} />
                </Field>
                <Field label="environmentId">
                  <InputControl value={sceneDraft.environmentId} onChange={(event) => setSceneDraftValue('environmentId', event.target.value)} placeholder="staging" disabled={!canManage || sceneActionState.loading} />
                </Field>
                <Field label="code">
                  <InputControl value={sceneDraft.code} onChange={(event) => setSceneDraftValue('code', event.target.value)} placeholder="portal-login-smoke" disabled={!canManage || sceneActionState.loading || Boolean(editingSceneId)} />
                </Field>
                <Field label="name">
                  <InputControl value={sceneDraft.name} onChange={(event) => setSceneDraftValue('name', event.target.value)} placeholder={translate('auto.k1895')} disabled={!canManage || sceneActionState.loading} />
                </Field>
                <Field label="status">
                  <SelectControl value={sceneDraft.status} onChange={(event) => setSceneDraftValue('status', event.target.value)} disabled={!canManage || sceneActionState.loading}>
                    <option value="DRAFT">{dictionaryLabel('DRAFT')}</option>
                    <option value="REVIEWING">{dictionaryLabel('REVIEWING')}</option>
                    <option value="APPROVED">{dictionaryLabel('APPROVED')}</option>
                    <option value="DISABLED">{dictionaryLabel('DISABLED')}</option>
                  </SelectControl>
                </Field>
                <Field label={fieldLabel('riskLevel')}>
                  <SelectControl value={sceneDraft.riskLevel} onChange={(event) => setSceneDraftValue('riskLevel', event.target.value)} disabled={!canManage || sceneActionState.loading}>
                    <option value="LOW">{dictionaryLabel('LOW')}</option>
                    <option value="MEDIUM">{dictionaryLabel('MEDIUM')}</option>
                    <option value="HIGH">{dictionaryLabel('HIGH')}</option>
                    <option value="CRITICAL">{dictionaryLabel('CRITICAL')}</option>
                  </SelectControl>
                </Field>
                <Field label={fieldLabel('tags')}>
                  <InputControl value={sceneDraft.tagsText} onChange={(event) => setSceneDraftValue('tagsText', event.target.value)} placeholder="login smoke admin" disabled={!canManage || sceneActionState.loading} />
                </Field>
              </div>
              <Field label="sourceSummary">
                <TextAreaControl value={sceneDraft.sourceSummaryText} onChange={(event) => setSceneDraftValue('sourceSummaryText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
              </Field>
              <div className="report-card-list">
                <div className="report-mini-card report-mini-card-muted">
                  <div className="report-card-heading">
                    <strong>{translate('auto.k1896')}</strong>
                    <span className="badge badge-neutral">仅草稿</span>
                  </div>
                  <div className="form-grid">
                    <Field label={fieldLabel('sourceType')}>
                      <SelectControl
                        value={sceneImportDraft.sourceType}
                        onChange={(event) => setSceneImportDraft((current) => ({ ...current, sourceType: event.target.value as UiE2eSceneImportSourceType }))}
                        disabled={!canManage || sceneActionState.loading}
                      >
                        <option value="PLAYWRIGHT_CODEGEN">{dictionaryLabel('PLAYWRIGHT_CODEGEN')}</option>
                        <option value="SELENIUM_IDE">{dictionaryLabel('SELENIUM_IDE')}</option>
                      </SelectControl>
                    </Field>
                    <Field label="codeHint">
                      <InputControl
                        value={sceneImportDraft.codeHint}
                        onChange={(event) => setSceneImportDraft((current) => ({ ...current, codeHint: event.target.value }))}
                        placeholder="portal-login-import"
                        disabled={!canManage || sceneActionState.loading}
                      />
                    </Field>
                    <Field label="nameHint">
                      <InputControl
                        value={sceneImportDraft.nameHint}
                        onChange={(event) => setSceneImportDraft((current) => ({ ...current, nameHint: event.target.value }))}
                        placeholder={translate('auto.k1897')}
                        disabled={!canManage || sceneActionState.loading}
                      />
                    </Field>
                    <Field label="importTags">
                      <InputControl
                        value={sceneImportDraft.tagsText}
                        onChange={(event) => setSceneImportDraft((current) => ({ ...current, tagsText: event.target.value }))}
                        placeholder="import smoke"
                        disabled={!canManage || sceneActionState.loading}
                      />
                    </Field>
                  </div>
                  <Field label="content">
                    <TextAreaControl
                      value={sceneImportDraft.content}
                      onChange={(event) => setSceneImportDraft((current) => ({ ...current, content: event.target.value }))}
                      placeholder={translate('auto.k1898')}
                      disabled={!canManage || sceneActionState.loading}
                    />
                  </Field>
                  <div className="report-actions-row">
                    <button className="btn btn-secondary" type="button" onClick={() => void onImportScene()} disabled={!canManage || sceneActionState.loading}>
                      <RefreshCw size={16} />{translate('auto.k1899')}</button>
                  </div>
                </div>
              </div>
              {editingSceneId && (
                <div className="notice info">{translate('auto.k1900')}</div>
              )}
              <div className="ui-e2e-step-editor">
                <div className="ui-e2e-section-heading">
                  <strong>{translate('auto.k1901')}</strong>
                  <button className="btn btn-secondary btn-sm" type="button" onClick={addSceneStep} disabled={!canManage || sceneActionState.loading}>
                    <FileText size={15} />{translate('auto.k1349')}</button>
                </div>
                {sceneDraft.steps.map((step, index) => (
                  <div className="ui-e2e-step-card" key={`scene-step-${index}`}>
                    <div className="ui-e2e-step-card-header">
                      <strong>{translate('auto.k1346')}{index + 1}</strong>
                      <button className="btn btn-ghost btn-sm" type="button" onClick={() => removeSceneStep(index)} disabled={sceneDraft.steps.length <= 1 || !canManage || sceneActionState.loading}>
                        {translate('auto.k0451')}</button>
                    </div>
                    <div className="ui-e2e-step-grid">
                      <Field label="stepType">
                        <InputControl value={step.stepType} onChange={(event) => updateSceneStep(index, 'stepType', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="actionSummary">
                        <TextAreaControl value={step.actionSummaryText} onChange={(event) => updateSceneStep(index, 'actionSummaryText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="locatorStrategy">
                        <TextAreaControl value={step.locatorStrategyText} onChange={(event) => updateSceneStep(index, 'locatorStrategyText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="assertionSummary">
                        <TextAreaControl value={step.assertionSummaryText} onChange={(event) => updateSceneStep(index, 'assertionSummaryText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="waitPolicy">
                        <TextAreaControl value={step.waitPolicyText} onChange={(event) => updateSceneStep(index, 'waitPolicyText', event.target.value)} disabled={!canManage || sceneActionState.loading} />
                      </Field>
                      <Field label="dataBinding">
                        <TextAreaControl
                          value={step.dataBindingText}
                          onChange={(event) => updateSceneStep(index, 'dataBindingText', event.target.value)}
                          placeholder={'{"dataSetCode":"checkout-users","recordKey":"user-001","bindingAlias":"user"}'}
                          disabled={!canManage || sceneActionState.loading}
                        />
                      </Field>
                    </div>
                  </div>
                ))}
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canManage || sceneActionState.loading}>
                  <FileText size={16} />{editingSceneId ? translate('auto.k1902') : translate('auto.k1903')}
                </button>
                {sceneDetail && sceneDetail.status !== 'ARCHIVED' && (
                  <button className="btn btn-secondary" type="button" onClick={loadSelectedSceneIntoDraft} disabled={!canManage || sceneActionState.loading}>
                    <FileText size={16} />{editingSceneId === sceneDetail.id ? translate('auto.k1904') : translate('auto.k1905')}
                  </button>
                )}
                {editingSceneId && (
                  <button className="btn btn-secondary" type="button" onClick={cancelSceneEditing} disabled={!canManage || sceneActionState.loading}>
                    {translate('auto.k0739')}</button>
                )}
                {sceneDetail && sceneDetail.status !== 'ARCHIVED' && (
                  <button className="btn btn-secondary" type="button" onClick={() => void onArchiveScene()} disabled={!canManage || sceneActionState.loading}>
                    <Archive size={16} />{translate('auto.k1906')}</button>
                )}
              </div>
              <StateLine state={sceneActionState} />
            </form>
            </Drawer>
            <ListPanel
              items={visibleScenes}
              selectedId={selectedSceneId}
              emptyTitle={translate('auto.k1907')}
              emptyDesc={sceneFocusMode === 'all'
                ? translate('auto.k1908')
                : translate('auto.k1909', { value0: labelUiE2eSceneFocusMode(sceneFocusMode) })}
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
                    <span className={`badge badge-${statusTone(scene.status)}`} title={scene.status}>{dictionaryLabel(scene.status)}</span>
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

          <Panel
            title={translate('auto.k1910')}
            desc={translate('auto.k1911')}
            action={(
              <button className="btn btn-primary btn-sm" type="button" onClick={openCreateBundleDrawer} disabled={!canManage || bundleActionState.loading}>
                <FileText size={15} />{translate('auto.k0218')}</button>
            )}
          >
            <Drawer
              className="ui-e2e-drawer"
              destroyOnHidden
              maskClosable={!bundleActionState.loading}
              open={openDrawer === 'bundle'}
              placement="right"
              title={translate('auto.k0218')}
              width={560}
              onClose={() => {
                if (!bundleActionState.loading) {
                  setOpenDrawer(null);
                }
              }}
            >
            <form className="ui-e2e-form document-drawer-form" onSubmit={(event) => { event.preventDefault(); void onCreateBundle(); }}>
              <div className="form-grid">
                <Field label="sceneId">
                  <InputControl value={bundleSceneId} onChange={(event) => setBundleSceneId(event.target.value)} placeholder={selectedSceneId || translate('auto.k1912')} disabled={!canManage || bundleActionState.loading} />
                </Field>
                <Field label="reviewNote">
                  <InputControl value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder={translate('auto.k1913')} disabled={bundleActionState.loading} />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canManage || bundleActionState.loading}>
                  <FileText size={16} />{translate('auto.k0218')}</button>
                <button className="btn btn-secondary" type="button" disabled={bundleActionState.loading} onClick={() => setOpenDrawer(null)}>
                  {translate('actions.cancel')}</button>
              </div>
              <StateLine state={bundleActionState} />
            </form>
            </Drawer>
            <div className="report-actions-row compact">
                <button className="btn btn-secondary" type="button" onClick={() => void onReviewBundle('submit')} disabled={!canReview || bundleActionState.loading || !bundleDetail || !['DRAFT', 'REJECTED', 'STATIC_CHECK_FAILED'].includes(bundleDetail.status)}>
                  <RefreshCw size={16} />{translate('auto.k1914')}</button>
                <button className="btn btn-secondary" type="button" onClick={() => void onReviewBundle('approve')} disabled={!canReview || bundleActionState.loading || bundleDetail?.status !== 'REVIEWING'}>
                  <CheckCircle2 size={16} />{translate('auto.k0620')}</button>
                <button className="btn btn-secondary" type="button" onClick={() => void onReviewBundle('reject')} disabled={!canReview || bundleActionState.loading || bundleDetail?.status !== 'REVIEWING'}>
                  <AlertTriangle size={16} />{translate('auto.k0214')}</button>
                <button className="btn btn-secondary" type="button" onClick={() => void onArchiveBundle()} disabled={!canManage || bundleActionState.loading || !bundleDetail || bundleDetail.status === 'ARCHIVED'}>
                  <Archive size={16} />{translate('auto.k0871')}</button>
                <button className="btn btn-secondary" type="button" onClick={() => void onExportBundle()} disabled={!canExport || bundleActionState.loading || !bundleDetail}>
                  <Download size={16} />{translate('auto.k0221')}</button>
            </div>
            <StateLine state={bundleActionState} />
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <InputControl value={bundleFilters.projectId} onChange={(event) => setBundleFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <SelectControl value={bundleFilters.status} onChange={(event) => setBundleFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">{translate('auto.k0195')}</option>
                  <option value="DRAFT">{dictionaryLabel('DRAFT')}</option>
                  <option value="REVIEWING">{dictionaryLabel('REVIEWING')}</option>
                  <option value="APPROVED">{dictionaryLabel('APPROVED')}</option>
                  <option value="REJECTED">{dictionaryLabel('REJECTED')}</option>
                  <option value="ARCHIVED">{dictionaryLabel('ARCHIVED')}</option>
                </SelectControl>
              </Field>
              <Field label="keyword">
                <InputControl value={bundleFilters.keyword} onChange={(event) => setBundleFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="scene / digest" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />{translate('auto.k1130')}</button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${bundleFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setBundleFocusMode('all')}
              >
                {translate('auto.k0195')}{bundles.length}
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
                {translate('auto.k1892')}{labelUiE2eBundleFocusMode(bundleFocusMode)}{translate('auto.k1893')}{visibleBundles.length} {translate('auto.k1894')}</div>
            )}
            <ListPanel
              items={visibleBundles}
              selectedId={selectedBundleId}
              emptyTitle={translate('auto.k1915')}
              emptyDesc={bundleFocusMode === 'all'
                ? translate('auto.k1916')
                : translate('auto.k1909', { value0: labelUiE2eBundleFocusMode(bundleFocusMode) })}
              onSelect={(bundle) => {
                setSelectedBundleId(bundle.id);
                applyBundleDefaults(bundle);
              }}
              renderItem={(bundle) => {
                const summary = buildUiE2eBundleListSummary(bundle);
                return (
                  <>
                    <span className={`badge badge-${statusTone(bundle.status)}`} title={bundle.status}>{dictionaryLabel(bundle.status)}</span>
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

          <Panel
            title={translate('auto.k1917')}
            desc={translate('auto.k1918')}
            action={(
              <div className="report-actions-row compact">
                <button className="btn btn-primary btn-sm" type="button" onClick={openCreateRunDrawer} disabled={!canExecute || runActionState.loading}>
                  <Play size={15} />{translate('auto.k1924')}</button>
                <button className="btn btn-secondary btn-sm" type="button" onClick={openBatchRunDrawer} disabled={!canExecute || runActionState.loading}>
                  <Play size={15} />{translate('auto.k1926')}</button>
                <button className="btn btn-secondary btn-sm" type="button" onClick={openBackfillDrawer} disabled={!canManage || runActionState.loading}>
                  <RefreshCw size={15} />{translate('auto.k1927')}</button>
              </div>
            )}
          >
            <Drawer
              className="ui-e2e-drawer"
              destroyOnHidden
              maskClosable={!runActionState.loading}
              open={openDrawer === 'run'}
              placement="right"
              title={translate('auto.k1924')}
              width={900}
              onClose={() => {
                if (!runActionState.loading) {
                  setOpenDrawer(null);
                }
              }}
            >
            <form className="ui-e2e-form document-drawer-form" onSubmit={onCreateRun}>
              <div className="form-grid">
                <Field label="projectId">
                  <InputControl value={runDraft.projectId} onChange={(event) => setRunDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="sceneId">
                  <InputControl value={runDraft.sceneId} onChange={(event) => setRunDraftValue('sceneId', event.target.value)} placeholder="UUID" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="bundleId">
                  <InputControl value={runDraft.bundleId} onChange={(event) => setRunDraftValue('bundleId', event.target.value)} placeholder="UUID" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="environmentId">
                  <InputControl value={runDraft.environmentId} onChange={(event) => setRunDraftValue('environmentId', event.target.value)} placeholder="staging" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="baseUrlRef">
                  <InputControl value={runDraft.baseUrlRef} onChange={(event) => setRunDraftValue('baseUrlRef', event.target.value)} placeholder="env:staging" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="accountLeaseRef">
                  <InputControl value={runDraft.accountLeaseRef} onChange={(event) => setRunDraftValue('accountLeaseRef', event.target.value)} placeholder="UUID" disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="requestKey">
                  <InputControl value={runDraft.requestKey} onChange={(event) => setRunDraftValue('requestKey', event.target.value)} placeholder={translate('auto.k1133')} disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="reason">
                  <InputControl value={runDraft.reason} onChange={(event) => setRunDraftValue('reason', event.target.value)} placeholder={translate('auto.k1919')} disabled={!canExecute || runActionState.loading} />
                </Field>
                <Field label="browsers">
                  <SelectControl
                    mode="multiple"
                    options={browserOptions}
                    value={runDraft.browsersText}
                    onChange={(event) => setRunDraftValue('browsersText', event.target.value)}
                    placeholder={translate('auto.k3000')}
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="baselineRunId">
                  <InputControl
                    value={runDraft.baselineRunId}
                    onChange={(event) => setRunDraftValue('baselineRunId', event.target.value)}
                    placeholder={translate('auto.k1920')}
                    disabled={!canExecute || runActionState.loading || !runDraft.visualRegressionEnabled}
                  />
                </Field>
                <Field label="visualThreshold">
                  <InputControl
                    value={runDraft.visualMismatchThreshold}
                    onChange={(event) => setRunDraftValue('visualMismatchThreshold', event.target.value)}
                    placeholder={translate('auto.k1921')}
                    disabled={!canExecute || runActionState.loading || !runDraft.visualRegressionEnabled}
                  />
                </Field>
              </div>
              <label className="field field-inline">
                <span className="field-inline-main">
                  <CheckboxControl

                    checked={runDraft.visualRegressionEnabled}
                    onChange={(event) => setRunDraftBooleanValue('visualRegressionEnabled', event.target.checked)}
                    disabled={!canExecute || runActionState.loading}
                  />
                  <span>{translate('auto.k1922')}</span>
                </span>
                <small>{translate('auto.k1923')}</small>
              </label>
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
                  <Play size={16} />{translate('auto.k1924')}</button>
                  </>
                )}
                <button className="btn btn-secondary" type="button" disabled={runActionState.loading} onClick={() => setOpenDrawer(null)}>
                  {translate('actions.cancel')}</button>
              </div>
              {!canExecute && (
                <div className="notice info">{translate('auto.k1928')}</div>
              )}
              <StateLine state={runActionState} />
            </form>
            </Drawer>
            <Drawer
              className="ui-e2e-drawer"
              destroyOnHidden
              maskClosable={!runActionState.loading}
              open={openDrawer === 'batchRun'}
              placement="right"
              title={translate('auto.k1926')}
              width={760}
              onClose={() => {
                if (!runActionState.loading) {
                  setOpenDrawer(null);
                }
              }}
            >
            <form className="ui-e2e-form document-drawer-form" onSubmit={(event) => { event.preventDefault(); void onCreateBatchRun(); }}>
              <div className={`notice ${runBatchReadiness.tone}`}>
                <strong>{translate('auto.k1926')} · {runBatchReadiness.label}</strong>
                <span>{runBatchReadiness.summary}</span>
                {runBatchReadiness.checks.length ? <span>{runBatchReadiness.checks.join(' · ')}</span> : null}
              </div>
              <div className="form-grid">
                <Field label="batchProjectId">
                  <InputControl
                    value={batchRunDraft.projectId}
                    onChange={(event) => setBatchRunDraftValue('projectId', event.target.value)}
                    placeholder="project-alpha"
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="sceneIds">
                  <TextAreaControl
                    value={batchRunDraft.sceneIdsText}
                    onChange={(event) => setBatchRunDraftValue('sceneIdsText', event.target.value)}
                    placeholder={translate('auto.k1929')}
                    disabled={!canExecute || runActionState.loading}
                    rows={3}
                  />
                </Field>
                <Field label="batchEnvironmentId">
                  <InputControl
                    value={batchRunDraft.environmentId}
                    onChange={(event) => setBatchRunDraftValue('environmentId', event.target.value)}
                    placeholder="staging"
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="batchBaseUrlRef">
                  <InputControl
                    value={batchRunDraft.baseUrlRef}
                    onChange={(event) => setBatchRunDraftValue('baseUrlRef', event.target.value)}
                    placeholder="env:staging"
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="batchAccountLeaseRef">
                  <InputControl
                    value={batchRunDraft.accountLeaseRef}
                    onChange={(event) => setBatchRunDraftValue('accountLeaseRef', event.target.value)}
                    placeholder="UUID"
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="requestKeyPrefix">
                  <InputControl
                    value={batchRunDraft.requestKeyPrefix}
                    onChange={(event) => setBatchRunDraftValue('requestKeyPrefix', event.target.value)}
                    placeholder={translate('auto.k1930')}
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="batchReason">
                  <InputControl
                    value={batchRunDraft.reason}
                    onChange={(event) => setBatchRunDraftValue('reason', event.target.value)}
                    placeholder={translate('auto.k1931')}
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="batchBrowsers">
                  <SelectControl
                    mode="multiple"
                    options={browserOptions}
                    value={batchRunDraft.browsersText}
                    onChange={(event) => setBatchRunDraftValue('browsersText', event.target.value)}
                    placeholder={translate('auto.k3000')}
                    disabled={!canExecute || runActionState.loading}
                  />
                </Field>
                <Field label="batchBaselineRunId">
                  <InputControl
                    value={batchRunDraft.baselineRunId}
                    onChange={(event) => setBatchRunDraftValue('baselineRunId', event.target.value)}
                    placeholder={translate('auto.k1920')}
                    disabled={!canExecute || runActionState.loading || !batchRunDraft.visualRegressionEnabled}
                  />
                </Field>
                <Field label="batchVisualThreshold">
                  <InputControl
                    value={batchRunDraft.visualMismatchThreshold}
                    onChange={(event) => setBatchRunDraftValue('visualMismatchThreshold', event.target.value)}
                    placeholder={translate('auto.k1921')}
                    disabled={!canExecute || runActionState.loading || !batchRunDraft.visualRegressionEnabled}
                  />
                </Field>
              </div>
              <label className="field field-inline">
                <span className="field-inline-main">
                  <CheckboxControl

                    checked={batchRunDraft.visualRegressionEnabled}
                    onChange={(event) => setBatchRunDraftBooleanValue('visualRegressionEnabled', event.target.checked)}
                    disabled={!canExecute || runActionState.loading}
                  />
                  <span>{translate('auto.k1932')}</span>
                </span>
                <small>{translate('auto.k1933')}</small>
              </label>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={runBatchDisabled}>
                  <Play size={16} />{translate('auto.k1926')}</button>
                <button className="btn btn-secondary" type="button" disabled={runActionState.loading} onClick={() => setOpenDrawer(null)}>
                  {translate('actions.cancel')}</button>
              </div>
              {runBatchSummary ? (
                <div className="report-card-list">
                  <div className="report-mini-card report-mini-card-muted">
                    <div className="report-card-heading">
                      <strong>{runBatchSummary.label}</strong>
                      <span className={`badge badge-${runBatchSummary.tone === 'error' ? 'danger' : runBatchSummary.tone}`}>{batchRunResult?.projectId || '-'}</span>
                    </div>
                    <span>{runBatchSummary.summary}</span>
                    <small>{runBatchSummary.signals.join(' · ')}</small>
                    {runBatchSummary.failedItems.length ? (
                      <div className="report-policy-list">
                        <div className="report-policy-title">{translate('auto.k1934')}</div>
                        {runBatchSummary.failedItems.map((item) => <span key={item}>{item}</span>)}
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : null}
              <StateLine state={runActionState} />
            </form>
            </Drawer>
            <Drawer
              className="ui-e2e-drawer"
              destroyOnHidden
              maskClosable={!runActionState.loading}
              open={openDrawer === 'backfill'}
              placement="right"
              title={translate('auto.k1927')}
              width={640}
              onClose={() => {
                if (!runActionState.loading) {
                  setOpenDrawer(null);
                }
              }}
            >
            <form className="ui-e2e-form document-drawer-form" onSubmit={(event) => { event.preventDefault(); void onBackfillRunSummary(); }}>
              <div className={`notice ${runBackfillReadiness.tone}`}>
                <strong>{translate('auto.k1927')} · {runBackfillReadiness.label}</strong>
                <span>{runBackfillReadiness.summary}</span>
                {runBackfillReadiness.checks.length ? <span>{runBackfillReadiness.checks.join(' · ')}</span> : null}
              </div>
              <div className="form-grid">
                <Field label="backfillProjectId">
                  <InputControl
                    value={runBackfillDraft.projectId}
                    onChange={(event) => setRunBackfillDraft((current) => ({ ...current, projectId: event.target.value }))}
                    placeholder="project-alpha"
                    disabled={!canManage || runActionState.loading}
                  />
                </Field>
                <Field label="runIds">
                  <InputControl
                    value={runBackfillDraft.runIdsText}
                    onChange={(event) => setRunBackfillDraft((current) => ({ ...current, runIdsText: event.target.value }))}
                    placeholder={translate('auto.k1935')}
                    disabled={!canManage || runActionState.loading}
                  />
                </Field>
                <Field label="limit">
                  <InputControl
                    value={runBackfillDraft.limit}
                    onChange={(event) => setRunBackfillDraft((current) => ({ ...current, limit: event.target.value }))}
                    placeholder={translate('auto.k1936')}
                    disabled={!canManage || runActionState.loading}
                  />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={runBackfillDisabled}>
                  <RefreshCw size={16} />{translate('auto.k1927')}</button>
                <button className="btn btn-secondary" type="button" disabled={runActionState.loading} onClick={() => setOpenDrawer(null)}>
                  {translate('actions.cancel')}</button>
              </div>
              {runBackfillSummary ? (
                <div className="report-card-list">
                  <div className="report-mini-card report-mini-card-muted">
                    <div className="report-card-heading">
                      <strong>{runBackfillSummary.label}</strong>
                      <span className={`badge badge-${runBackfillSummary.tone === 'error' ? 'danger' : runBackfillSummary.tone}`}>{runBackfillResult?.projectId || '-'}</span>
                    </div>
                    <span>{runBackfillSummary.summary}</span>
                    <small>{runBackfillSummary.signals.join(' · ')}</small>
                    {runBackfillSummary.failedItems.length ? (
                      <div className="report-policy-list">
                        <div className="report-policy-title">{translate('auto.k1934')}</div>
                        {runBackfillSummary.failedItems.map((item) => <span key={item}>{item}</span>)}
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : null}
              <StateLine state={runActionState} />
            </form>
            </Drawer>
            <div className="report-actions-row compact">
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onCancelRun()} disabled={runActionState.loading || !runDetail || !isUiE2eRunActiveStatus(runDetail.status)}>
                <Square size={15} />{translate('auto.k1925')}</button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onExportRun()} disabled={!canExport || runActionState.loading || !runDetail}>
                <Download size={15} />{translate('auto.k0221')}</button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={openBatchRunDrawer} disabled={!canExecute || runActionState.loading}>
                <Play size={15} />{translate('auto.k1926')}</button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={openBackfillDrawer} disabled={!canManage || runActionState.loading}>
                <RefreshCw size={15} />{translate('auto.k1927')}</button>
            </div>
            <StateLine state={runActionState} />
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <InputControl value={runFilters.projectId} onChange={(event) => setRunFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <SelectControl value={runFilters.status} onChange={(event) => setRunFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">{translate('auto.k0195')}</option>
                  <option value="QUEUED">{dictionaryLabel('QUEUED')}</option>
                  <option value="RUNNING">{dictionaryLabel('RUNNING')}</option>
                  <option value="SUCCEEDED">{dictionaryLabel('SUCCEEDED')}</option>
                  <option value="FAILED">{dictionaryLabel('FAILED')}</option>
                  <option value="TIMEOUT">{dictionaryLabel('TIMEOUT')}</option>
                  <option value="CANCELED">{dictionaryLabel('CANCELED')}</option>
                  <option value="BLOCKED">{dictionaryLabel('BLOCKED')}</option>
                </SelectControl>
              </Field>
              <Field label="keyword">
                <InputControl value={runFilters.keyword} onChange={(event) => setRunFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="requestKey / scene" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />{translate('auto.k1130')}</button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${runFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setRunFocusMode('all')}
              >
                {translate('auto.k0195')}{runs.length}
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
                {translate('auto.k1892')}{labelUiE2eRunFocusMode(runFocusMode)}{translate('auto.k1893')}{visibleRuns.length} {translate('auto.k1894')}</div>
            )}
            <ListPanel
              items={visibleRuns}
              selectedId={selectedRunId}
              emptyTitle={translate('auto.k1937')}
              emptyDesc={runFocusMode === 'all'
                ? translate('auto.k1938')
                : translate('auto.k1909', { value0: labelUiE2eRunFocusMode(runFocusMode) })}
              onSelect={(run) => {
                setSelectedRunId(run.id);
                applyRunDefaults(run);
              }}
              renderItem={(run) => {
                const summary = buildUiE2eRunListSummary(run);
                return (
                  <>
                    <span className={`badge badge-${statusTone(run.status)}`} title={run.status}>{dictionaryLabel(run.status)}</span>
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

          <Panel
            title={translate('auto.k1939')}
            desc={translate('auto.k1940')}
            action={(
              <button className="btn btn-primary btn-sm" type="button" onClick={openFlakyDrawer} disabled={!canFlaky || flakyActionState.loading}>
                <Bug size={15} />{translate('auto.k1943')}</button>
            )}
          >
            <Drawer
              className="ui-e2e-drawer"
              destroyOnHidden
              maskClosable={!flakyActionState.loading}
              open={openDrawer === 'flaky'}
              placement="right"
              title={translate('auto.k1943')}
              width={640}
              onClose={() => {
                if (!flakyActionState.loading) {
                  setOpenDrawer(null);
                }
              }}
            >
            <form className="ui-e2e-form document-drawer-form" onSubmit={onUpsertFlaky}>
              <div className="form-grid">
                <Field label="projectId">
                  <InputControl value={flakyDraft.projectId} onChange={(event) => setFlakyDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="sceneId">
                  <InputControl value={flakyDraft.sceneId} onChange={(event) => setFlakyDraftValue('sceneId', event.target.value)} placeholder={translate('auto.k1941')} disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="runId">
                  <InputControl value={flakyDraft.runId} onChange={(event) => setFlakyDraftValue('runId', event.target.value)} placeholder={translate('auto.k1941')} disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="status">
                  <SelectControl value={flakyDraft.status} onChange={(event) => setFlakyDraftValue('status', event.target.value)} disabled={!canFlaky || flakyActionState.loading}>
                    <option value="NONE">{dictionaryLabel('NONE')}</option>
                    <option value="FLAKY_CANDIDATE">{dictionaryLabel('FLAKY_CANDIDATE')}</option>
                    <option value="CONFIRMED_FLAKY">{dictionaryLabel('CONFIRMED_FLAKY')}</option>
                    <option value="WAIVED">{dictionaryLabel('WAIVED')}</option>
                  </SelectControl>
                </Field>
                <Field label="reasonCode">
                  <InputControl value={flakyDraft.reasonCode} onChange={(event) => setFlakyDraftValue('reasonCode', event.target.value)} placeholder="locator-drift" disabled={!canFlaky || flakyActionState.loading} />
                </Field>
                <Field label="reasonSummary">
                  <InputControl value={flakyDraft.reasonSummary} onChange={(event) => setFlakyDraftValue('reasonSummary', event.target.value)} placeholder={translate('auto.k1942')} disabled={!canFlaky || flakyActionState.loading} />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canFlaky || flakyActionState.loading}>
                  <Bug size={16} />{translate('auto.k1943')}</button>
                <button className="btn btn-secondary" type="button" disabled={flakyActionState.loading} onClick={() => setOpenDrawer(null)}>
                  {translate('actions.cancel')}</button>
              </div>
              <StateLine state={flakyActionState} />
            </form>
            </Drawer>
            <StateLine state={flakyActionState} />
            <form className="ui-e2e-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshWorkbench(); }}>
              <Field label="projectId">
                <InputControl value={flakyFilters.projectId} onChange={(event) => setFlakyFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="status">
                <SelectControl value={flakyFilters.status} onChange={(event) => setFlakyFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">{translate('auto.k0195')}</option>
                  <option value="NONE">{dictionaryLabel('NONE')}</option>
                  <option value="FLAKY_CANDIDATE">{dictionaryLabel('FLAKY_CANDIDATE')}</option>
                  <option value="CONFIRMED_FLAKY">{dictionaryLabel('CONFIRMED_FLAKY')}</option>
                  <option value="WAIVED">{dictionaryLabel('WAIVED')}</option>
                </SelectControl>
              </Field>
              <Field label="keyword">
                <InputControl value={flakyFilters.keyword} onChange={(event) => setFlakyFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="reason / scene / run" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />{translate('auto.k1130')}</button>
              </div>
            </form>
            <div className="report-actions-row compact">
              <button
                className={`btn ${flakyFocusMode === 'all' ? 'btn-primary' : 'btn-secondary'} btn-sm`}
                type="button"
                onClick={() => setFlakyFocusMode('all')}
              >
                {translate('auto.k0195')}{flakyMarks.length}
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
                {translate('auto.k1892')}{labelUiE2eFlakyFocusMode(flakyFocusMode)}{translate('auto.k1893')}{visibleFlakyMarks.length} {translate('auto.k1894')}</div>
            )}
            <ListPanel
              items={visibleFlakyMarks}
              selectedId={selectedFlakyId}
              emptyTitle={translate('auto.k1944')}
              emptyDesc={flakyFocusMode === 'all'
                ? translate('auto.k1945')
                : translate('auto.k1909', { value0: labelUiE2eFlakyFocusMode(flakyFocusMode) })}
              onSelect={(item) => {
                setSelectedFlakyId(item.id);
                applyFlakyDefaults(item);
              }}
              renderItem={(item) => {
                const summary = buildUiE2eFlakyListSummary(item);
                return (
                  <>
                    <span className={`badge badge-${statusTone(item.status)}`} title={item.status}>{dictionaryLabel(item.status)}</span>
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

  function setRunDraftBooleanValue(key: keyof Pick<UiE2eRunDraft, 'visualRegressionEnabled'>, value: boolean) {
    setRunDraft((current) => ({ ...current, [key]: value }));
    setRunActionState({ loading: false });
  }

  function setBatchRunDraftValue(key: keyof UiE2eBatchRunDraft, value: string) {
    setBatchRunDraft((current) => ({ ...current, [key]: value }));
    setRunActionState({ loading: false });
  }

  function setBatchRunDraftBooleanValue(key: keyof Pick<UiE2eBatchRunDraft, 'visualRegressionEnabled'>, value: boolean) {
    setBatchRunDraft((current) => ({ ...current, [key]: value }));
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
    setBatchRunDraft((current) => ({
      ...current,
      projectId: scene.projectId,
      environmentId: scene.environmentId || current.environmentId,
      sceneIdsText: current.sceneIdsText || scene.id
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
    setBatchRunDraft((current) => ({
      ...current,
      projectId: bundle.projectId,
      environmentId: bundle.environmentId || current.environmentId,
      sceneIdsText: current.sceneIdsText || bundle.sceneId
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

  function openCreateSceneDrawer() {
    setEditingSceneId('');
    setSceneDraft(blankUiE2eSceneDraft(sceneDetail ? {
      projectId: sceneDetail.projectId,
      applicationId: sceneDetail.applicationId || '',
      environmentId: sceneDetail.environmentId || ''
    } : {
      projectId: sceneFilters.projectId,
      applicationId: sceneFilters.applicationId,
      environmentId: sceneFilters.environmentId
    }));
    setSceneImportDraft(initialSceneImportDraft);
    setSceneActionState({ loading: false });
    setOpenDrawer('scene');
  }

  function openEditSceneDrawer() {
    if (!sceneDetail) {
      return;
    }
    setEditingSceneId(sceneDetail.id);
    setSceneDraft(sceneDraftFromDetail(sceneDetail));
    setSceneActionState({ loading: false });
    setOpenDrawer('scene');
  }

  function openCreateBundleDrawer() {
    setBundleSceneId(selectedSceneId || bundleSceneId);
    setBundleActionState({ loading: false });
    setOpenDrawer('bundle');
  }

  function openCreateRunDrawer() {
    setRunActionState({ loading: false });
    setOpenDrawer('run');
  }

  function openBatchRunDrawer() {
    setRunActionState({ loading: false });
    setOpenDrawer('batchRun');
  }

  function openBackfillDrawer() {
    setRunActionState({ loading: false });
    setOpenDrawer('backfill');
  }

  function openFlakyDrawer() {
    setFlakyActionState({ loading: false });
    setOpenDrawer('flaky');
  }

  function loadSelectedSceneIntoDraft() {
    if (!sceneDetail) {
      return;
    }
    setEditingSceneId(sceneDetail.id);
    setSceneDraft(sceneDraftFromDetail(sceneDetail));
    setSceneActionState({ loading: false });
    setOpenDrawer('scene');
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
    return <EmptyPanel title={translate('auto.k1946')} desc={translate('auto.k1947')} />;
  }
  const latestBundleSummary = props.activity?.latestBundle ? buildUiE2eBundleListSummary(props.activity.latestBundle) : null;
  const latestRunSummary = props.activity?.latestRun ? buildUiE2eRunListSummary(props.activity.latestRun) : null;
  return (
    <Panel title={translate('auto.k1946')} desc={`${props.detail.projectId} · ${props.detail.code}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`} title={props.detail.status}>{dictionaryLabel(props.detail.status)}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label={fieldLabel('riskLevel')} value={dictionaryLabel(props.detail.riskLevel)} />
        <SummaryTile label={fieldLabel('stepCount')} value={String(props.detail.steps.length)} />
        <SummaryTile label={fieldLabel('application')} value={props.detail.applicationId || '-'} />
        <SummaryTile label={fieldLabel('environment')} value={props.detail.environmentId || '-'} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title={fieldLabel('tags')} value={props.detail.tags.join(', ') || '-'} />
        <InfoBlock title={fieldLabel('policy')} value={formatRecord(props.detail.policy)} />
        <InfoBlock title={fieldLabel('sourceSummary')} value={formatRecord(props.detail.sourceSummary)} />
        <InfoBlock title={fieldLabel('updatedAt')} value={props.detail.updatedAt ? formatDateTime(props.detail.updatedAt) : '-'} />
      </div>
      <div className="report-card-list">
        <div className="report-mini-card report-mini-card-muted">
          <div className="report-card-heading">
            <strong>{translate('auto.k1948')}</strong>
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
            <span>{translate('auto.k1949')}</span>
          )}
        </div>
        <div className="report-mini-card report-mini-card-muted">
          <div className="report-card-heading">
            <strong>{translate('auto.k1950')}</strong>
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
            <span>{translate('auto.k1951')}</span>
          )}
        </div>
      </div>
      {props.detail.steps.length ? (
        <div className="ui-e2e-card-list">
          {props.detail.steps.map((step) => (
            <div className="report-mini-card" key={step.id}>
              <div className="report-card-heading">
                <strong>{translate('auto.k1346')}{step.stepOrder} · {step.stepType}</strong>
                <span className="badge badge-neutral">{shortId(step.id)}</span>
              </div>
              <div className="report-section-grid">
                <InfoBlock title="action" value={formatRecord(step.actionSummary)} />
                <InfoBlock title="locator" value={formatRecord(step.locatorStrategy)} />
                <InfoBlock title="assertion" value={formatRecord(step.assertionSummary)} />
                <InfoBlock title="waitPolicy" value={formatRecord(step.waitPolicy)} />
                <InfoBlock title="dataBinding" value={formatRecord(step.dataBinding)} />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="notice info">{translate('auto.k1952')}</div>
      )}
      <StateLine state={props.state} />
    </Panel>
  );
}

function BundleDetailPanel(props: { detail: UiE2eBundleDetail | null; exported: UiE2eBundleExport | null; state: WorkState }) {
  if (!props.detail) {
    return <EmptyPanel title={translate('auto.k1953')} desc={translate('auto.k1954')} />;
  }
  return (
    <Panel title={translate('auto.k1953')} desc={`${props.detail.projectId} · ${props.detail.sceneCode || props.detail.sceneId}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`} title={props.detail.status}>{dictionaryLabel(props.detail.status)}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="staticCheck" value={props.detail.staticCheckStatus || '-'} tone={statusTone(props.detail.staticCheckStatus || 'UNKNOWN')} />
        <SummaryTile label={fieldLabel('sceneStatus')} value={props.detail.sceneStatus ? dictionaryLabel(props.detail.sceneStatus) : '-'} />
        <SummaryTile label={fieldLabel('riskLevel')} value={props.detail.riskLevel ? dictionaryLabel(props.detail.riskLevel) : '-'} />
        <SummaryTile label={fieldLabel('reviews')} value={String(props.detail.reviews.length)} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title={fieldLabel('bundleDigest')} value={props.detail.bundleDigest || '-'} />
        <InfoBlock title={fieldLabel('tags')} value={props.detail.tags.join(', ') || '-'} />
        <InfoBlock title={fieldLabel('specSummary')} value={formatRecord(props.detail.specSummary)} />
        <InfoBlock title={fieldLabel('fixtureSummary')} value={formatRecord(props.detail.fixtureSummary)} />
      </div>
      <PolicySummary policy={{ ...props.detail.staticCheckSummary, ...props.detail.policy }} />
      {props.detail.reviews.length ? (
        <div className="ui-e2e-card-list">
          {props.detail.reviews.map((review) => (
            <div className="report-mini-card" key={review.id}>
              <div className="report-card-heading">
                <strong>{displayValueLabel(review.reviewStatus)}</strong>
                <span className="badge badge-neutral">{review.reviewedBy || '-'}</span>
              </div>
              <span>{review.reviewComment || translate('auto.k1955')}</span>
              <small>{review.reviewedAt ? formatDateTime(review.reviewedAt) : review.createdAt ? formatDateTime(review.createdAt) : review.id}</small>
            </div>
          ))}
        </div>
      ) : (
        <div className="notice info">{translate('auto.k1956')}</div>
      )}
      {props.exported ? (
        <div className="report-card-list">
          <div className="report-mini-card">
            <div className="report-card-heading">
              <strong>{translate('auto.k0221')}</strong>
              <span className="badge badge-neutral">{props.exported.schemaVersion}</span>
            </div>
            <div className="report-section-grid">
              <InfoBlock title={fieldLabel('exportedAt')} value={props.exported.exportedAt ? formatDateTime(props.exported.exportedAt) : '-'} />
              <InfoBlock title={fieldLabel('reviewSummary')} value={formatRecord(props.exported.reviewSummary)} />
              <InfoBlock title={fieldLabel('redactionPolicy')} value={formatRecord(props.exported.redactionPolicy)} />
              <InfoBlock title={fieldLabel('exportPolicy')} value={formatRecord(props.exported.bundle.policy)} />
            </div>
          </div>
        </div>
      ) : (
        <div className="notice info">{translate('auto.k1957')}</div>
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
    return <EmptyPanel title={translate('auto.k0880')} desc={translate('auto.k1958')} />;
  }
  const executionSummary = props.detail.executionSummary;
  const diagnosis = buildUiE2eRunDiagnosis(props.detail);
  const flakyGuidance = buildUiE2eRunFlakyGuidance(props.detail);
  const auditTimeline = buildUiE2eRunAuditTimeline(props.detail);
  const browserTypes = recordStringArray(executionSummary.browserTypes);
  const browserCount = recordNumber(executionSummary.browserCount, browserTypes.length || 1);
  const parallelExecutionEnabled = recordBoolean(executionSummary.parallelExecutionEnabled, browserCount > 1);
  const visualRegressionEnabled = recordBoolean(executionSummary.visualRegressionEnabled);
  const visualBaselineRunId = recordText(executionSummary.visualBaselineRunId);
  const visualThreshold = recordNumberOrText(executionSummary.visualMismatchThreshold);
  const visualComparisonCount = recordNumber(executionSummary.visualComparisonCount);
  const visualMismatchCount = recordNumber(executionSummary.visualMismatchCount);
  const visualDiffArtifactCount = recordNumber(executionSummary.visualDiffArtifactCount);
  const visualMismatchBrowsers = recordStringArray(executionSummary.visualMismatchBrowsers);
  const browserRuns = recordObjectArray(executionSummary.browserRuns);
  return (
    <Panel title={translate('auto.k0880')} desc={`${props.detail.projectId} · ${props.detail.sceneCode || props.detail.sceneId}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`} title={props.detail.status}>{dictionaryLabel(props.detail.status)}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label={fieldLabel('runnerMode')} value={displayValueLabel(props.detail.runnerMode)} />
        <SummaryTile label={fieldLabel('flaky')} value={dictionaryLabel(props.detail.flakyMark?.status || props.detail.flakyStatus || 'NONE')} tone={statusTone(props.detail.flakyMark?.status || props.detail.flakyStatus || 'NONE')} />
        <SummaryTile label={fieldLabel('steps')} value={String(props.detail.stepResults.length)} />
        <SummaryTile label={fieldLabel('artifacts')} value={String(props.detail.artifacts.length)} />
      </div>
      <div className="report-summary-grid">
        <SummaryTile label={fieldLabel('browsers')} value={browserTypes.map((browser) => displayValueLabel(browser)).join(' / ') || displayValueLabel('CHROMIUM')} tone={parallelExecutionEnabled ? 'info' : undefined} />
        <SummaryTile label={fieldLabel('parallel')} value={parallelExecutionEnabled ? `${displayValueLabel('ON')} (${browserCount})` : displayValueLabel('OFF')} tone={parallelExecutionEnabled ? 'info' : undefined} />
        <SummaryTile label={fieldLabel('visual')} value={displayValueLabel(visualRegressionEnabled ? 'ON' : 'OFF')} tone={visualRegressionEnabled ? 'warning' : undefined} />
        <SummaryTile label={fieldLabel('diffs')} value={visualRegressionEnabled ? `${visualMismatchCount}/${visualComparisonCount}` : '-'} tone={visualMismatchCount > 0 ? 'danger' : visualComparisonCount > 0 ? 'success' : undefined} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title={fieldLabel('failureCode')} value={props.detail.failureCode ? displayValueLabel(props.detail.failureCode) : '-'} />
        <InfoBlock title={fieldLabel('traceId')} value={props.detail.traceId || props.state.traceId || '-'} />
        <InfoBlock title={fieldLabel('accountSummary')} value={formatRecord(props.detail.accountSummary)} />
        <InfoBlock title={fieldLabel('executionSummary')} value={formatRecord(executionSummary)} />
      </div>
      {(browserRuns.length || visualRegressionEnabled) ? (
        <div className="report-card-list">
          <div className="report-mini-card report-mini-card-muted">
            <div className="report-card-heading">
              <strong>{translate('auto.k1959')}</strong>
              <span className="badge badge-neutral">{browserCount} 个浏览器</span>
            </div>
            <span>{browserTypes.map((browser) => displayValueLabel(browser)).join(' / ') || displayValueLabel('CHROMIUM')}</span>
            <small>{parallelExecutionEnabled ? translate('auto.k1960') : translate('auto.k1961')}</small>
            {browserRuns.length ? (
              <div className="report-policy-list">
                <div className="report-policy-title">{translate('auto.k1962')}</div>
                {browserRuns.map((item, index) => (
                  <span key={`${recordText(item.browserType) || 'browser'}-${index}`}>
                    {displayValueLabel(recordText(item.browserType) || 'UNKNOWN')}={displayValueLabel(recordText(item.status) || 'UNKNOWN')}
                    {recordText(item.failureCode) ? ` (${displayValueLabel(recordText(item.failureCode))})` : ''}
                  </span>
                ))}
              </div>
            ) : null}
          </div>
          {visualRegressionEnabled ? (
            <div className="report-mini-card report-mini-card-muted">
              <div className="report-card-heading">
                <strong>{translate('auto.k1963')}</strong>
                <span className={`badge badge-${visualMismatchCount > 0 ? 'danger' : visualComparisonCount > 0 ? 'success' : 'neutral'}`}>
                  {visualMismatchCount > 0 ? displayValueLabel('MISMATCH') : visualComparisonCount > 0 ? displayValueLabel('MATCHED') : displayValueLabel('PENDING')}
                </span>
              </div>
              <span>
                {fieldLabel('baselineRunId')}={visualBaselineRunId || '自动选择最近成功运行'}
                {visualThreshold !== '-' ? ` · ${fieldLabel('visualThreshold')}=${visualThreshold}` : ` · ${fieldLabel('visualThreshold')}=精确匹配`}
              </span>
              <small>
                {translate('auto.k1964')}{visualComparisonCount} {translate('auto.k1965')}{visualMismatchCount} {translate('auto.k1966')}{visualDiffArtifactCount} {translate('auto.k1356')}</small>
              {visualMismatchBrowsers.length ? (
                <div className="notice warning">
                  <span>{translate('auto.k1967')}{visualMismatchBrowsers.map((browser) => displayValueLabel(browser)).join(' / ')}</span>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
      <div className={`notice ${diagnosis.tone}`}>
        <strong>{translate('auto.k1968')}{diagnosis.label}</strong>
        <span>{diagnosis.summary}</span>
        {diagnosis.primaryFailureBucket ? <span>{translate('auto.k1969')}{diagnosis.primaryFailureBucket}</span> : null}
        {diagnosis.blockedArtifactCount > 0 ? <span>{translate('auto.k1970')}{diagnosis.blockedArtifactCount}</span> : null}
        {!diagnosis.rawArtifactDownloadReady && props.detail.artifacts.length ? <span>{translate('auto.k1971')}</span> : null}
      </div>
      {diagnosis.signals.length ? (
        <div className="report-policy-list">
          <div className="report-policy-title">{translate('auto.k1972')}</div>
          {diagnosis.signals.map((item) => <span key={item}>{item}</span>)}
        </div>
      ) : null}
      {diagnosis.nextActions.length ? (
        <div className="report-policy-list">
          <div className="report-policy-title">{translate('auto.k1973')}</div>
          {diagnosis.nextActions.map((item) => <span key={item}>{item}</span>)}
        </div>
      ) : null}
      <RunAuditTimeline timeline={auditTimeline} />
      <div className={`notice ${flakyGuidance.tone}`}>
        <strong>{translate('auto.k1974')}{flakyGuidance.label}</strong>
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
          <div className="notice info">{translate('auto.k1975')}</div>
        )
      ) : (
        <div className="notice info">{translate('auto.k1976')}</div>
      )}
      <StateLine state={props.flakyState} />
      {props.detail.failureCode === 'UI_E2E_EXPORT_DISABLED' && (
        <div className="notice warning">{translate('auto.k1977')}</div>
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
              <strong>{translate('auto.k0221')}</strong>
              <span className="badge badge-neutral">{props.exported.schemaVersion}</span>
            </div>
            <div className="report-section-grid">
              <InfoBlock title={fieldLabel('exportedAt')} value={props.exported.exportedAt ? formatDateTime(props.exported.exportedAt) : '-'} />
              <InfoBlock title={fieldLabel('redactionPolicy')} value={formatRecord(props.exported.redactionPolicy)} />
            </div>
          </div>
        </div>
      ) : (
        <div className="notice info">{translate('auto.k1978')}</div>
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
    return <div className="notice info">{translate('auto.k1979')}</div>;
  }
  return (
    <div className="ui-e2e-run-audit-timeline">
      <div className="report-policy-title">{translate('auto.k1980')}</div>
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
    return <EmptyPanel title={translate('auto.k1981')} desc={translate('auto.k1982')} />;
  }
  const insight = buildUiE2eFlakyDetailInsight(props.item);
  return (
    <Panel title={translate('auto.k1981')} desc={`${props.item.projectId} · ${props.item.sceneCode || props.item.sceneId || '-'}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.item.status)}`} title={props.item.status}>{dictionaryLabel(props.item.status)}</span>
        <span className="report-mono">{props.item.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label={fieldLabel('scene')} value={props.item.sceneCode || shortId(props.item.sceneId)} />
        <SummaryTile label={fieldLabel('riskLevel')} value={props.item.sceneRiskLevel ? dictionaryLabel(props.item.sceneRiskLevel) : '-'} tone={statusTone(props.item.sceneRiskLevel || 'UNKNOWN')} />
        <SummaryTile label={fieldLabel('linkedRuns')} value={String(props.item.linkedRunCount)} />
        <SummaryTile label={fieldLabel('runStatus')} value={props.item.runStatus ? dictionaryLabel(props.item.runStatus) : '-'} />
        <SummaryTile label={fieldLabel('latestFailure')} value={props.item.latestFailureBucket ? displayValueLabel(props.item.latestFailureBucket) : '-'} tone={statusTone(props.item.runStatus || props.item.status)} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title={fieldLabel('reasonCode')} value={props.item.reasonCode ? displayValueLabel(props.item.reasonCode) : '-'} />
        <InfoBlock title={fieldLabel('reasonSummary')} value={props.item.reasonSummary || '-'} />
        <InfoBlock title={fieldLabel('runId')} value={props.item.runId || '-'} />
        <InfoBlock title={fieldLabel('sceneName')} value={props.item.sceneName || '-'} />
        <InfoBlock title={fieldLabel('createdBy')} value={props.item.createdBy || '-'} />
        <InfoBlock title={fieldLabel('updatedBy')} value={props.item.updatedBy || '-'} />
        <InfoBlock title={fieldLabel('createdAt')} value={props.item.createdAt ? formatDateTime(props.item.createdAt) : '-'} />
        <InfoBlock title={fieldLabel('updatedAt')} value={props.item.updatedAt ? formatDateTime(props.item.updatedAt) : '-'} />
      </div>
      <div className={`notice ${insight.tone}`}>
        <strong>{translate('auto.k1983')}{insight.label}</strong>
        <span>{insight.summary}</span>
        {insight.signals.length ? <span>{insight.signals.join(' · ')}</span> : null}
      </div>
      <div className="notice info">
        <strong>{translate('auto.k1984')}</strong>
        <span>{translate('auto.k1985')}</span>
      </div>
      <StateLine state={props.state} />
    </Panel>
  );
}

function StepResultsList(props: { steps: UiE2eRunStepResult[] }) {
  if (!props.steps.length) {
    return <div className="notice info">{translate('auto.k1986')}</div>;
  }
  return (
    <div className="report-card-list">
      {props.steps.map((step) => (
        <div className="report-mini-card" key={step.id}>
            <div className="report-card-heading">
            <strong>{translate('auto.k1346')}{step.stepOrder} · {displayValueLabel(step.status)}</strong>
            <span className={`badge badge-${statusTone(step.status)}`}>{displayValueLabel(step.failureBucket || 'NO_FAILURE')}</span>
          </div>
          <div className="report-section-grid">
            <InfoBlock title="durationMs" value={String(step.durationMs)} />
            <InfoBlock title="errorCode" value={step.errorCode || '-'} />
            <InfoBlock title="summary" value={formatRecord(step.summary)} />
            <InfoBlock title="sceneStepId" value={step.sceneStepId || '-'} />
          </div>
          {Array.isArray(step.summary?.browserResults) && step.summary.browserResults.length ? (
            <div className="report-policy-list">
              <div className="report-policy-title">{translate('auto.k1987')}</div>
              {(step.summary.browserResults as Array<Record<string, unknown>>).map((item, index) => (
                <span key={`${recordText(item.browserType) || 'browser'}-${index}`}>
                  {displayValueLabel(recordText(item.browserType) || 'UNKNOWN')}={displayValueLabel(recordText(item.status) || 'UNKNOWN')}
                  {recordText(item.errorCode) ? ` · ${displayValueLabel(recordText(item.errorCode))}` : ''}
                </span>
              ))}
            </div>
          ) : null}
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
    return <div className="notice info">{translate('auto.k1988')}</div>;
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
  const browserType = recordText(props.artifact.redactionFlags?.browserType);
  const visualRole = recordText(props.artifact.redactionFlags?.visualRole);
  const visualMismatchRatio = recordNumberOrText(props.artifact.redactionFlags?.visualMismatchRatio);
  const visualPassed = recordBooleanOrBlank(props.artifact.redactionFlags?.visualPassed);
  return (
    <div className="report-mini-card">
      <div className="report-card-heading">
        <strong>{displayValueLabel(props.artifact.artifactType)}</strong>
        <span className={`badge badge-${statusTone(props.artifact.captureStatus)}`}>{displayValueLabel(props.artifact.captureStatus)}</span>
      </div>
      {(browserType || visualRole) ? (
        <div className="report-policy-list">
          <div className="report-policy-title">{translate('auto.k1989')}</div>
          {browserType ? <span>{fieldLabel('browser')}={displayValueLabel(browserType)}</span> : null}
          {visualRole ? <span>{fieldLabel('visualRole')}={displayValueLabel(visualRole)}</span> : null}
          {visualMismatchRatio !== '-' ? <span>{fieldLabel('mismatchRatio')}={visualMismatchRatio}</span> : null}
          {visualPassed ? <span>{fieldLabel('visualPassed')}={displayValueLabel(visualPassed)}</span> : null}
        </div>
      ) : null}
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
          title={!props.canExport ? translate('auto.k1990') : downloadState.summary}
          onClick={() => props.onDownloadArtifact(props.artifact)}
        >
          <Download size={15} />{translate('auto.k1991')}</button>
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
        <strong>{translate('auto.k1992')}</strong>
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
      <span className="field-label">{fieldLabel(props.label)}</span>
      {props.children}
    </label>
  );
}

function SummaryTile(props: { label: string; value: string; tone?: string }) {
  return (
    <div className="report-summary-tile">
      <span>{fieldLabel(props.label)}</span>
      <strong className={props.tone ? `tone-${props.tone}` : undefined}>{displayValueLabel(props.value)}</strong>
    </div>
  );
}

function InfoBlock(props: { title: string; value: string }) {
  return (
    <div className="report-info-block">
      <span>{fieldLabel(props.title)}</span>
      <strong>{displayValueLabel(props.value)}</strong>
    </div>
  );
}

function PolicySummary(props: { policy: Record<string, unknown> }) {
  const entries = Object.entries(props.policy).slice(0, 10);
  if (!entries.length) return null;
  return (
    <div className="report-policy-list">
      <div className="report-policy-title"><ShieldCheck size={15} />{translate('auto.k1993')}</div>
      {entries.map(([key, value]) => (
        <span key={key}>{fieldLabel(key)}={formatRecord(value)}</span>
      ))}
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">{translate('auto.k1062')}</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">{fieldLabel('traceId')}：{props.state.traceId}</span>;
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

function mergeRunSummaries(details: UiE2eRunDetail[], current: UiE2eRunSummary[]) {
  const next = details.map(summaryFromRunDetail);
  const ids = new Set(next.map((item) => item.id));
  return [...next, ...current.filter((item) => !ids.has(item.id))];
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
  if (typeof input === 'boolean') return displayValueLabel(input ? 'YES' : 'NO');
  if (Array.isArray(input)) return input.map((item) => formatRecord(item)).join(', ');
  if (typeof input === 'object') {
    const text = prettyJson(input);
    return text.length > 180 ? `${text.slice(0, 177)}...` : text;
  }
  return displayValueLabel(input);
}

function recordText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function recordBoolean(value: unknown, fallback = false) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  return fallback;
}

function recordBooleanOrBlank(value: unknown) {
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'string' && value.trim()) return value.trim();
  return '';
}

function recordNumber(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function recordNumberOrText(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value);
  }
  if (typeof value === 'string' && value.trim()) {
    return value.trim();
  }
  return '-';
}

function recordStringArray(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => recordText(item))
    .filter((item): item is string => Boolean(item));
}

function recordObjectArray(value: unknown) {
  if (!Array.isArray(value)) return [] as Array<Record<string, unknown>>;
  return value.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object' && !Array.isArray(item));
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
