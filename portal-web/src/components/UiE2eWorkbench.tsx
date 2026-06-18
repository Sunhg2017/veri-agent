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
  cancelUiE2eRun,
  createUiE2eBundle,
  createUiE2eRun,
  createUiE2eScene,
  exportUiE2eRun,
  fetchUiE2eBundle,
  fetchUiE2eBundles,
  fetchUiE2eFlakyMarks,
  fetchUiE2eHealth,
  fetchUiE2eRun,
  fetchUiE2eRuns,
  fetchUiE2eScene,
  fetchUiE2eScenes,
  rejectUiE2eBundle,
  submitUiE2eBundleReview,
  updateUiE2eScene,
  upsertUiE2eFlakyMark,
  type UiE2eArtifactManifest,
  type UiE2eBundleDetail,
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
  buildUiE2eFlakyPayload,
  buildUiE2eRunPayload,
  buildUiE2eScenePayload,
  buildUiE2eSceneUpdatePayload,
  initialUiE2eFlakyDraft,
  initialUiE2eRunDraft,
  initialUiE2eSceneDraft,
  initialUiE2eSceneStepDraft,
  prettyJson,
  sceneDraftFromDetail,
  type UiE2eFlakyDraft,
  type UiE2eRunDraft,
  type UiE2eSceneDraft,
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

const initialFilters: SimpleFilters = { projectId: '', status: '', keyword: '' };

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

  const [sceneFilters, setSceneFilters] = useState<SimpleFilters>(initialFilters);
  const [bundleFilters, setBundleFilters] = useState<SimpleFilters>(initialFilters);
  const [runFilters, setRunFilters] = useState<SimpleFilters>(initialFilters);
  const [flakyFilters, setFlakyFilters] = useState<SimpleFilters>(initialFilters);

  const [selectedSceneId, setSelectedSceneId] = useState('');
  const [selectedBundleId, setSelectedBundleId] = useState('');
  const [selectedRunId, setSelectedRunId] = useState('');
  const [selectedFlakyId, setSelectedFlakyId] = useState('');
  const [editingSceneId, setEditingSceneId] = useState('');

  const [sceneDetail, setSceneDetail] = useState<UiE2eSceneDetail | null>(null);
  const [bundleDetail, setBundleDetail] = useState<UiE2eBundleDetail | null>(null);
  const [runDetail, setRunDetail] = useState<UiE2eRunDetail | null>(null);
  const [runExport, setRunExport] = useState<UiE2eRunExport | null>(null);

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

  const selectedFlaky = useMemo(
    () => flakyMarks.find((item) => item.id === selectedFlakyId) ?? null,
    [flakyMarks, selectedFlakyId]
  );

  const summary = useMemo(() => {
    const approvedScenes = scenes.filter((scene) => scene.status === 'APPROVED').length;
    const reviewingBundles = bundles.filter((bundle) => bundle.status === 'REVIEWING').length;
    const activeRuns = runs.filter((run) => run.status === 'QUEUED' || run.status === 'RUNNING').length;
    const confirmedFlaky = flakyMarks.filter((mark) => mark.status === 'CONFIRMED_FLAKY').length;
    return { approvedScenes, reviewingBundles, activeRuns, confirmedFlaky };
  }, [bundles, flakyMarks, runs, scenes]);

  const refreshWorkbench = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setScenes([]);
      setBundles([]);
      setRuns([]);
      setFlakyMarks([]);
      setSceneDetail(null);
      setBundleDetail(null);
      setRunDetail(null);
      setRunExport(null);
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
        fetchUiE2eScenes({ ...compactFilters(sceneFilters), size: 20 }),
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
      return;
    }
    try {
      const result = await fetchUiE2eBundle(bundleId);
      setBundleDetail(result.data);
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

  useEffect(() => {
    void refreshWorkbench();
  }, [refreshWorkbench]);

  useEffect(() => {
    void refreshSceneDetail(selectedSceneId);
  }, [refreshSceneDetail, selectedSceneId]);

  useEffect(() => {
    void refreshBundleDetail(selectedBundleId);
  }, [refreshBundleDetail, selectedBundleId]);

  useEffect(() => {
    void refreshRunDetail(selectedRunId);
  }, [refreshRunDetail, selectedRunId]);

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
    if (!runDetail || !canExecute) return;
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

  async function onUpsertFlaky(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canFlaky) return;
    const { payload, issues } = buildUiE2eFlakyPayload(flakyDraft);
    if (!payload || issues.length) {
      setFlakyActionState({ loading: false, error: issues.join('；') });
      return;
    }
    setFlakyActionState({ loading: true });
    try {
      const result = await upsertUiE2eFlakyMark(payload);
      setSelectedFlakyId(result.data.id);
      setFlakyMarks((current) => [result.data, ...current.filter((item) => item.id !== result.data.id)]);
      setRuns((current) => current.map((run) => run.id === result.data.runId ? { ...run, flakyStatus: result.data.status } : run));
      setRunDetail((current) => current && current.id === result.data.runId ? { ...current, flakyMark: result.data } : current);
      setFlakyActionState({ loading: false, success: 'Flaky 标记已更新', traceId: result.trace_id });
    } catch (error: unknown) {
      setFlakyActionState({ loading: false, error: error instanceof Error ? error.message : '更新 Flaky 标记失败' });
    }
  }

  return (
    <div className="ui-e2e-workbench" data-testid="ui-e2e-workbench">
      <section className="metrics-grid">
        <Metric icon={<CheckCircle2 size={20} />} label="APPROVED 场景" value={String(summary.approvedScenes)} desc={health?.runnerMode || '等待加载'} tone="success" />
        <Metric icon={<FileText size={20} />} label="待评审脚本包" value={String(summary.reviewingBundles)} desc={health?.artifactPolicy ? 'artifact policy ready' : '等待加载'} tone="info" />
        <Metric icon={<Play size={20} />} label="活跃运行" value={String(summary.activeRuns)} desc={health?.runnerEnabled ? 'runner ON' : 'runner OFF'} tone="warning" />
        <Metric icon={<Bug size={20} />} label="CONFIRMED_FLAKY" value={String(summary.confirmedFlaky)} desc={health?.exportEnabled ? 'export ON' : 'export OFF'} tone="danger" />
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
                </div>
                <PolicySummary policy={{ ...health.credentialPolicy, ...health.artifactPolicy, ...health.policy }} />
              </>
            ) : (
              <div className="notice info">等待加载 UI E2E 健康摘要。</div>
            )}
            {!health?.runnerEnabled && (
              <div className="notice warning">当前 runner 默认关闭，手动创建运行会返回 BLOCKED 摘要，用于验证控制面与权限链路。</div>
            )}
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
              <Field label="keyword">
                <input value={sceneFilters.keyword} onChange={(event) => setSceneFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="code / name / tag" />
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />筛选
                </button>
              </div>
            </form>
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
              items={scenes}
              selectedId={selectedSceneId}
              emptyTitle="暂无场景"
              emptyDesc="创建第一条 UI 场景后，可继续生成脚本包并触发运行。"
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
              renderItem={(scene) => (
                <>
                  <span className={`badge badge-${statusTone(scene.status)}`}>{scene.status}</span>
                  <strong>{scene.code}</strong>
                  <span>{scene.name} · {scene.riskLevel} · {scene.stepCount} steps</span>
                  <small>{scene.updatedAt ? formatDateTime(scene.updatedAt) : scene.id}</small>
                </>
              )}
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
            <ListPanel
              items={bundles}
              selectedId={selectedBundleId}
              emptyTitle="暂无脚本包"
              emptyDesc="选择 APPROVED 场景后生成 bundle，并通过评审后用于运行。"
              onSelect={(bundle) => {
                setSelectedBundleId(bundle.id);
                applyBundleDefaults(bundle);
              }}
              renderItem={(bundle) => (
                <>
                  <span className={`badge badge-${statusTone(bundle.status)}`}>{bundle.status}</span>
                  <strong>{bundle.sceneCode || shortId(bundle.sceneId)}</strong>
                  <span>{bundle.staticCheckStatus || 'STATIC_CHECK_PENDING'} · {bundle.bundleDigest || '-'}</span>
                  <small>{bundle.updatedAt ? formatDateTime(bundle.updatedAt) : bundle.id}</small>
                </>
              )}
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
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canExecute || runActionState.loading}>
                  <Play size={16} />创建运行
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onCancelRun()} disabled={!canExecute || runActionState.loading || !runDetail}>
                  <Square size={16} />取消运行
                </button>
                <button className="btn btn-secondary" type="button" onClick={() => void onExportRun()} disabled={!canExport || runActionState.loading || !runDetail}>
                  <Download size={16} />导出摘要
                </button>
              </div>
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
            <ListPanel
              items={runs}
              selectedId={selectedRunId}
              emptyTitle="暂无运行"
              emptyDesc="选中 APPROVED bundle 后触发单次运行，可查看步骤摘要、artifact manifest 和失败分类。"
              onSelect={(run) => {
                setSelectedRunId(run.id);
                applyRunDefaults(run);
              }}
              renderItem={(run) => (
                <>
                  <span className={`badge badge-${statusTone(run.status)}`}>{run.status}</span>
                  <strong>{run.sceneCode || shortId(run.sceneId)}</strong>
                  <span>{run.failureCode || run.runnerMode} · flaky={run.flakyStatus || 'NONE'}</span>
                  <small>{run.createdAt ? formatDateTime(run.createdAt) : run.id}</small>
                </>
              )}
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
            <ListPanel
              items={flakyMarks}
              selectedId={selectedFlakyId}
              emptyTitle="暂无 Flaky 标记"
              emptyDesc="可按运行或场景标记失败抖动，便于 WP10 消费聚合状态。"
              onSelect={(item) => {
                setSelectedFlakyId(item.id);
                setFlakyDraft((current) => ({
                  ...current,
                  projectId: item.projectId,
                  sceneId: item.sceneId || '',
                  runId: item.runId || '',
                  status: item.status,
                  reasonCode: item.reasonCode || '',
                  reasonSummary: item.reasonSummary || ''
                }));
              }}
              renderItem={(item) => (
                <>
                  <span className={`badge badge-${statusTone(item.status)}`}>{item.status}</span>
                  <strong>{item.sceneCode || shortId(item.sceneId)}</strong>
                  <span>{item.reasonCode || '-'} · run={item.runStatus || '-'}</span>
                  <small>{item.updatedAt ? formatDateTime(item.updatedAt) : item.id}</small>
                </>
              )}
            />
          </Panel>
        </section>

        <section className="ui-e2e-detail-column">
          <SceneDetailPanel detail={sceneDetail} state={sceneActionState} />
          <BundleDetailPanel detail={bundleDetail} state={bundleActionState} />
          <RunDetailPanel detail={runDetail} exported={runExport} state={runActionState} />
          <FlakyDetailPanel item={selectedFlaky} state={flakyActionState} />
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

function SceneDetailPanel(props: { detail: UiE2eSceneDetail | null; state: WorkState }) {
  if (!props.detail) {
    return <EmptyPanel title="场景详情" desc="选择场景后查看步骤模板、策略和来源摘要。" />;
  }
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

function BundleDetailPanel(props: { detail: UiE2eBundleDetail | null; state: WorkState }) {
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
      <StateLine state={props.state} />
    </Panel>
  );
}

function RunDetailPanel(props: { detail: UiE2eRunDetail | null; exported: UiE2eRunExport | null; state: WorkState }) {
  if (!props.detail) {
    return <EmptyPanel title="运行详情" desc="选择 run 后查看步骤结果、失败分类、artifact manifest 和导出摘要。" />;
  }
  const executionSummary = props.detail.executionSummary;
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
      {props.detail.failureCode === 'UI_E2E_RUNNER_DISABLED' && (
        <div className="notice warning">当前环境 runner 默认关闭，运行被控制面安全地标记为 BLOCKED，用于验证审批、租借和导出链路。</div>
      )}
      <StepResultsList steps={props.detail.stepResults} />
      <ArtifactList artifacts={props.detail.artifacts} />
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
      <StateLine state={props.state} />
    </Panel>
  );
}

function FlakyDetailPanel(props: { item: UiE2eFlakyMark | null; state: WorkState }) {
  if (!props.item) {
    return <EmptyPanel title="Flaky 详情" desc="选择 Flaky 标记后查看原因、场景和运行关联。" />;
  }
  return (
    <Panel title="Flaky 详情" desc={`${props.item.projectId} · ${props.item.sceneCode || props.item.sceneId || '-'}`}>
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.item.status)}`}>{props.item.status}</span>
        <span className="report-mono">{props.item.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="scene" value={props.item.sceneCode || shortId(props.item.sceneId)} />
        <SummaryTile label="runStatus" value={props.item.runStatus || '-'} />
        <SummaryTile label="reasonCode" value={props.item.reasonCode || '-'} />
        <SummaryTile label="updatedBy" value={props.item.updatedBy || '-'} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title="reasonSummary" value={props.item.reasonSummary || '-'} />
        <InfoBlock title="runId" value={props.item.runId || '-'} />
        <InfoBlock title="createdAt" value={props.item.createdAt ? formatDateTime(props.item.createdAt) : '-'} />
        <InfoBlock title="updatedAt" value={props.item.updatedAt ? formatDateTime(props.item.updatedAt) : '-'} />
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
        </div>
      ))}
    </div>
  );
}

function ArtifactList(props: { artifacts: UiE2eArtifactManifest[] }) {
  if (!props.artifacts.length) {
    return <div className="notice info">暂无 artifact manifest。</div>;
  }
  return (
    <div className="report-card-list">
      {props.artifacts.map((artifact) => (
        <div className="report-mini-card" key={artifact.id}>
          <div className="report-card-heading">
            <strong>{artifact.artifactType}</strong>
            <span className={`badge badge-${statusTone(artifact.captureStatus)}`}>{artifact.captureStatus}</span>
          </div>
          <div className="report-section-grid">
            <InfoBlock title="digest" value={artifact.artifactDigest || '-'} />
            <InfoBlock title="storageRef" value={artifact.storageRef || '-'} />
            <InfoBlock title="sizeBytes" value={String(artifact.sizeBytes)} />
            <InfoBlock title="redactionFlags" value={formatRecord(artifact.redactionFlags)} />
          </div>
        </div>
      ))}
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

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}
