import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Download,
  FileText,
  PauseCircle,
  Play,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Square,
  Trash2,
  Webhook
} from 'lucide-react';
import { Drawer } from 'antd';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import { ApiError } from '../api/client';
import {
  archiveExecutionPlan,
  cancelExecutionRun,
  createExecutionPlan,
  createExecutionTrigger,
  downloadExecutionArtifact,
  dryRunExecutionPlan,
  dryRunExecutionTrigger,
  exportExecutionRun,
  fetchExecutionHealth,
  fetchExecutionPlan,
  fetchExecutionPlans,
  fetchExecutionRun,
  fetchExecutionRunLogs,
  fetchExecutionRuns,
  fetchExecutionTriggerEvents,
  fetchExecutionTriggers,
  retryExecutionRun,
  subscribeExecutionRunStream,
  triggerExecutionRun,
  updateExecutionPlan,
  updateExecutionTrigger,
  type ExecutionDryRun,
  type ExecutionHealth,
  type ExecutionPlanDetail,
  type ExecutionPlanSummary,
  type ExecutionRunDetail,
  type ExecutionRunExport,
  type ExecutionRunLogHistory,
  type ExecutionRunStreamEvent,
  type ExecutionRunSummary,
  type ExecutionTrigger,
  type ExecutionTriggerDryRun,
  type ExecutionTriggerEvent
} from '../api/execution';
import {
  blankExecutionNodeDraft,
  buildExecutionPlanPayload,
  buildExecutionPlanUpdatePayload,
  executionPlanDraftFromDetail,
  initialExecutionPlanDraft,
  summarizeDraftNode,
  validateExecutionPlanDraft,
  type ExecutionDagNodeDraft,
  type ExecutionPlanDraft
} from '../executionDagEditor';
import { canUseButton, hasPermission } from '../permissions';
import { dictionaryLabel, displayValueLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { NativeSelect } from './ui';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
};

type ExecutionLogEntry = {
  key: string;
  id?: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS';
  stage?: string;
  message: string;
  nodeKey?: string;
  timestamp?: string;
  metadata: Record<string, unknown>;
};

type RunLogHistoryState = {
  loading: boolean;
  loadingMore: boolean;
  index: number;
  size: number;
  total: number;
  loaded: boolean;
  error?: string;
};

type TriggerDraft = {
  triggerType: 'WEBHOOK' | 'CRON';
  status: 'DISABLED' | 'ENABLED' | 'PAUSED';
  source: string;
  eventType: string;
  cron: string;
  timezone: string;
  secretRef: string;
};

const initialTriggerDraft: TriggerDraft = {
  triggerType: 'WEBHOOK',
  status: 'DISABLED',
  source: 'ci',
  eventType: 'deployment',
  cron: '0 */30 * * * *',
  timezone: 'Asia/Shanghai',
  secretRef: ''
};

export function ExecutionWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'execution:read');
  const canManage = canUseButton(props.currentUser, 'execution:manage');
  const canTrigger = canUseButton(props.currentUser, 'execution:trigger');
  const canExport = canUseButton(props.currentUser, 'execution:export');
  const [health, setHealth] = useState<ExecutionHealth | null>(null);
  const [plans, setPlans] = useState<ExecutionPlanSummary[]>([]);
  const [runs, setRuns] = useState<ExecutionRunSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState('');
  const [selectedRunId, setSelectedRunId] = useState('');
  const [selectedTriggerId, setSelectedTriggerId] = useState('');
  const [planDetail, setPlanDetail] = useState<ExecutionPlanDetail | null>(null);
  const [runDetail, setRunDetail] = useState<ExecutionRunDetail | null>(null);
  const [triggers, setTriggers] = useState<ExecutionTrigger[]>([]);
  const [triggerEvents, setTriggerEvents] = useState<ExecutionTriggerEvent[]>([]);
  const [planDraft, setPlanDraft] = useState<ExecutionPlanDraft>(initialExecutionPlanDraft);
  const [planDraftMode, setPlanDraftMode] = useState<'create' | 'edit'>('create');
  const [planDrawerOpen, setPlanDrawerOpen] = useState(false);
  const [triggerDraft, setTriggerDraft] = useState<TriggerDraft>(initialTriggerDraft);
  const [triggerDrawerOpen, setTriggerDrawerOpen] = useState(false);
  const [manualReason, setManualReason] = useState('');
  const [manualRequestKey, setManualRequestKey] = useState('');
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [planActionState, setPlanActionState] = useState<WorkState>({ loading: false });
  const [runActionState, setRunActionState] = useState<WorkState>({ loading: false });
  const [triggerActionState, setTriggerActionState] = useState<WorkState>({ loading: false });
  const [lastDryRun, setLastDryRun] = useState<ExecutionDryRun | null>(null);
  const [lastRunExport, setLastRunExport] = useState<ExecutionRunExport | null>(null);
  const [lastTriggerDryRun, setLastTriggerDryRun] = useState<ExecutionTriggerDryRun | null>(null);
  const [runLogs, setRunLogs] = useState<ExecutionLogEntry[]>([]);
  const [runStreamState, setRunStreamState] = useState<WorkState>({ loading: false });
  const [runLogHistoryState, setRunLogHistoryState] = useState<RunLogHistoryState>({
    loading: false,
    loadingMore: false,
    index: 0,
    size: 20,
    total: 0,
    loaded: false
  });

  const summary = useMemo(() => {
    const ready = plans.filter((plan) => plan.status === 'READY').length;
    const running = runs.filter((run) => run.status === 'RUNNING' || run.status === 'QUEUED').length;
    const failed = runs.filter((run) => ['FAILED', 'TIMEOUT', 'PARTIAL_SUCCESS'].includes(run.status)).length;
    const enabledTriggers = triggers.filter((trigger) => trigger.status === 'ENABLED').length;
    return { ready, running, failed, enabledTriggers };
  }, [plans, runs, triggers]);

  const refreshWorkbench = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setPlans([]);
      setRuns([]);
      setPlanDetail(null);
      setRunDetail(null);
      setLastRunExport(null);
      setRunLogs([]);
      setRunStreamState({ loading: false });
      setRunLogHistoryState({
        loading: false,
        loadingMore: false,
        index: 0,
        size: 20,
        total: 0,
        loaded: false
      });
      setTriggers([]);
      setTriggerEvents([]);
      setSelectedTriggerId('');
      return;
    }
    setLoadState({ loading: true });
    try {
      const [healthResult, planResult, runResult] = await Promise.all([
        fetchExecutionHealth(),
        fetchExecutionPlans({ size: 50 }),
        fetchExecutionRuns({ size: 50 })
      ]);
      setHealth(healthResult.data);
      setPlans(planResult.data.items);
      setRuns(runResult.data.items);
      setLoadState({ loading: false });
      setSelectedPlanId((current) => current || planResult.data.items[0]?.id || '');
      setSelectedRunId((current) => current || runResult.data.items[0]?.id || '');
    } catch (error: unknown) {
      setLoadState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0049') });
    }
  }, [canRead, props.signedIn]);

  const refreshPlanDetail = useCallback(async (planId: string) => {
    if (!planId || !canRead) {
      setPlanDetail(null);
      setTriggers([]);
      setTriggerEvents([]);
      setSelectedTriggerId('');
      return;
    }
    try {
      const [planResult, triggerResult] = await Promise.all([
        fetchExecutionPlan(planId),
        fetchExecutionTriggers(planId, { size: 20 })
      ]);
      setPlanDetail(planResult.data);
      setPlanDraft(executionPlanDraftFromDetail(planResult.data));
      setPlanDraftMode('edit');
      setTriggers(triggerResult.data.items);
      const firstTriggerId = triggerResult.data.items[0]?.id;
      if (firstTriggerId) {
        setSelectedTriggerId((current) => current || firstTriggerId);
        const eventsResult = await fetchExecutionTriggerEvents(firstTriggerId, { size: 5 });
        setTriggerEvents(eventsResult.data.items);
      } else {
        setSelectedTriggerId('');
        setTriggerEvents([]);
      }
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0821') });
    }
  }, [canRead]);

  const refreshRunDetail = useCallback(async (runId: string) => {
    if (!runId || !canRead) {
      setRunDetail(null);
      setLastRunExport(null);
      setRunLogs([]);
      setRunStreamState({ loading: false });
      setRunLogHistoryState({
        loading: false,
        loadingMore: false,
        index: 0,
        size: 20,
        total: 0,
        loaded: false
      });
      return;
    }
    try {
      const result = await fetchExecutionRun(runId);
      setRunDetail(result.data);
      setLastRunExport(null);
      setRunLogs([]);
      setRunStreamState({ loading: false });
      setRunLogHistoryState({
        loading: false,
        loadingMore: false,
        index: 0,
        size: 20,
        total: 0,
        loaded: false
      });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0822') });
    }
  }, [canRead]);

  const loadRunLogHistory = useCallback(async (runId: string, index = 0, append = false) => {
    if (!runId || !canRead) {
      return;
    }
    setRunLogHistoryState((current) => ({
      ...current,
      loading: !append,
      loadingMore: append,
      error: undefined
    }));
    try {
      const result = await fetchExecutionRunLogs(runId, { index, size: 20 });
      setRunLogs((current) => mergeExecutionLogs(
        append ? current : [],
        result.data.items.map(toExecutionLogEntry)
      ));
      setRunLogHistoryState({
        loading: false,
        loadingMore: false,
        index: result.data.index,
        size: result.data.size,
        total: result.data.total,
        loaded: true
      });
    } catch (error: unknown) {
      setRunLogHistoryState((current) => ({
        ...current,
        loading: false,
        loadingMore: false,
        loaded: true,
        error: error instanceof Error ? error.message : translate('auto.k0823')
      }));
    }
  }, [canRead]);

  useEffect(() => {
    void refreshWorkbench();
  }, [refreshWorkbench]);

  useEffect(() => {
    void refreshPlanDetail(selectedPlanId);
  }, [refreshPlanDetail, selectedPlanId]);

  useEffect(() => {
    void refreshRunDetail(selectedRunId);
  }, [refreshRunDetail, selectedRunId]);

  useEffect(() => {
    if (!selectedRunId || !canRead) {
      return;
    }
    void loadRunLogHistory(selectedRunId);
  }, [canRead, loadRunLogHistory, selectedRunId]);

  useEffect(() => {
    if (!selectedRunId || !canRead) {
      setRunStreamState({ loading: false });
      return undefined;
    }
    let disposed = false;
    let controller: AbortController | null = null;
    let retryTimer: number | null = null;

    const connect = () => {
      if (disposed) {
        return;
      }
      controller = new AbortController();
      setRunStreamState({ loading: true });
      void subscribeExecutionRunStream(
        selectedRunId,
        (event) => {
          if (!disposed) {
            applyRunStreamEvent(event);
          }
        },
        controller.signal
      )
        .then(() => {
          if (disposed || controller?.signal.aborted) {
            return;
          }
          setRunStreamState({ loading: false });
          retryTimer = window.setTimeout(connect, 1000);
        })
        .catch((error: unknown) => {
          if (disposed || controller?.signal.aborted) {
            return;
          }
          if (error instanceof ApiError && error.code === 'SESSION_EXPIRED') {
            setRunStreamState({ loading: false, error: error.message });
            return;
          }
          setRunStreamState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0824') });
          retryTimer = window.setTimeout(connect, 3000);
        });
    };

    connect();
    return () => {
      disposed = true;
      controller?.abort();
      if (retryTimer !== null) {
        window.clearTimeout(retryTimer);
      }
    };
  }, [canRead, selectedRunId]);

  useEffect(() => {
    if (selectedTriggerId) {
      void refreshTriggerEvents(selectedTriggerId);
    }
  }, [selectedTriggerId]);

  if (!props.signedIn) {
    return <div className="notice warning">{translate('auto.k0825')}</div>;
  }

  if (!canRead) {
    return <div className="notice error">{translate('auto.k0826')}</div>;
  }

  async function onCreatePlan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManage) return;
    const issues = validateExecutionPlanDraft(planDraft);
    if (issues.length) {
      setPlanActionState({ loading: false, error: issues.map((issue) => issue.message).join('；') });
      return;
    }
    setPlanActionState({ loading: true });
    try {
      const result = await createExecutionPlan(buildExecutionPlanPayload(planDraft));
      setPlans((current) => [result.data, ...current.filter((plan) => plan.id !== result.data.id)]);
      setSelectedPlanId(result.data.id);
      setPlanDetail(result.data);
      setPlanDraft(executionPlanDraftFromDetail(result.data));
      setPlanDraftMode('edit');
      setPlanDrawerOpen(false);
      setPlanActionState({ loading: false, success: translate('auto.k0827') });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0828') });
    }
  }

  async function onUpdatePlan() {
    if (!selectedPlanId || !canManage) return;
    const issues = validateExecutionPlanDraft(planDraft);
    if (issues.length) {
      setPlanActionState({ loading: false, error: issues.map((issue) => issue.message).join('；') });
      return;
    }
    setPlanActionState({ loading: true });
    try {
      const result = await updateExecutionPlan(selectedPlanId, buildExecutionPlanUpdatePayload(planDraft));
      setPlanDetail(result.data);
      setPlanDraft(executionPlanDraftFromDetail(result.data));
      setPlans((current) => current.map((plan) => plan.id === result.data.id ? result.data : plan));
      setPlanDrawerOpen(false);
      setPlanActionState({ loading: false, success: translate('auto.k0829') });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0830') });
    }
  }

  async function submitUpdatePlan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onUpdatePlan();
  }

  async function onArchivePlan() {
    if (!selectedPlanId || !canManage) return;
    setPlanActionState({ loading: true });
    try {
      const result = await archiveExecutionPlan(selectedPlanId);
      setPlanDetail(result.data);
      setPlans((current) => current.map((plan) => plan.id === result.data.id ? result.data : plan));
      setPlanActionState({ loading: false, success: translate('auto.k0831') });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0832') });
    }
  }

  async function onDryRunPlan() {
    if (!selectedPlanId) return;
    setPlanActionState({ loading: true });
    try {
      const result = await dryRunExecutionPlan(selectedPlanId);
      setLastDryRun(result.data);
      setPlanActionState({ loading: false, success: result.data.valid ? translate('auto.k0833') : translate('auto.k0834') });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0835') });
    }
  }

  async function onTriggerRun() {
    if (!selectedPlanId || !canTrigger) return;
    setRunActionState({ loading: true });
    try {
      const result = await triggerExecutionRun(selectedPlanId, {
        requestKey: optionalText(manualRequestKey),
        reason: optionalText(manualReason)
      });
      setRunDetail(result.data);
      setSelectedRunId(result.data.id);
      setRuns((current) => [result.data, ...current.filter((run) => run.id !== result.data.id)]);
      setRunActionState({ loading: false, success: result.data.idempotentReplay ? translate('auto.k0836') : translate('auto.k0837') });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0838') });
    }
  }

  async function onCancelRun() {
    if (!runDetail || !canTrigger) return;
    setRunActionState({ loading: true });
    try {
      const result = await cancelExecutionRun(runDetail.id);
      setRunDetail(result.data);
      setLastRunExport(null);
      mergeRun(result.data);
      setRunActionState({ loading: false, success: translate('auto.k0839') });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0162') });
    }
  }

  async function onRetryRun() {
    if (!runDetail || !canTrigger) return;
    setRunActionState({ loading: true });
    try {
      const result = await retryExecutionRun(runDetail.id);
      setRunDetail(result.data);
      setLastRunExport(null);
      mergeRun(result.data);
      setRunActionState({ loading: false, success: translate('auto.k0840') });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0841') });
    }
  }

  async function onExportRun() {
    if (!runDetail || !canExport) return;
    setRunActionState({ loading: true });
    try {
      const result = await exportExecutionRun(runDetail.id);
      setLastRunExport(result.data);
      setRunActionState({ loading: false, success: translate('auto.k0842') });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0062') });
    }
  }

  async function onDownloadArtifact(artifact: ExecutionRunDetail['artifacts'][number]) {
    if (!runDetail || !canExport || !artifact.downloadReady) return;
    setRunActionState({ loading: true });
    try {
      const response = await downloadExecutionArtifact(runDetail.id, artifact.id);
      const blob = response.blob.type
        ? response.blob
        : new Blob([response.blob], { type: response.contentType || 'application/octet-stream' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = response.filename ?? `${artifact.artifactType.toLowerCase()}-${artifact.id}`;
      link.click();
      URL.revokeObjectURL(url);
      setRunActionState({ loading: false, success: translate('auto.k0843', { value0: artifact.artifactType }) });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0844') });
    }
  }

  async function onCreateTrigger(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPlanId || !canManage) return;
    setTriggerActionState({ loading: true });
    try {
      const config = triggerDraft.triggerType === 'WEBHOOK'
        ? { source: triggerDraft.source.trim() || 'ci', eventType: triggerDraft.eventType.trim() || 'deployment' }
        : { cron: triggerDraft.cron.trim(), timezone: triggerDraft.timezone.trim() || 'Asia/Shanghai' };
      const result = await createExecutionTrigger(selectedPlanId, {
        triggerType: triggerDraft.triggerType,
        status: triggerDraft.status,
        config,
        secretRef: optionalText(triggerDraft.secretRef)
      });
      setTriggers((current) => [result.data, ...current.filter((trigger) => trigger.id !== result.data.id)]);
      setSelectedTriggerId(result.data.id);
      setTriggerDrawerOpen(false);
      setTriggerDraft(initialTriggerDraft);
      setTriggerActionState({ loading: false, success: translate('auto.k0845') });
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0846') });
    }
  }

  async function onToggleTrigger(trigger: ExecutionTrigger) {
    if (!canManage) return;
    const nextStatus = trigger.status === 'ENABLED' ? 'PAUSED' : 'ENABLED';
    setTriggerActionState({ loading: true });
    try {
      const result = await updateExecutionTrigger(trigger.id, { status: nextStatus });
      setTriggers((current) => current.map((item) => item.id === result.data.id ? result.data : item));
      setTriggerActionState({ loading: false, success: translate('auto.k0847', { value0: result.data.status }) });
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0848') });
    }
  }

  async function onDryRunTrigger(trigger: ExecutionTrigger) {
    setTriggerActionState({ loading: true });
    try {
      const result = await dryRunExecutionTrigger(trigger.id);
      setLastTriggerDryRun(result.data);
      setTriggerActionState({ loading: false, success: result.data.valid ? translate('auto.k0849') : translate('auto.k0850') });
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0851') });
    }
  }

  async function refreshTriggerEvents(triggerId: string) {
    if (!triggerId || !canRead) {
      setTriggerEvents([]);
      return;
    }
    try {
      const result = await fetchExecutionTriggerEvents(triggerId, { size: 5 });
      setTriggerEvents(result.data.items);
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0852') });
    }
  }

  function mergeRun(run: ExecutionRunDetail) {
    setRuns((current) => current.map((item) => item.id === run.id ? run : item));
  }

  function applyRunStreamEvent(event: ExecutionRunStreamEvent) {
    if (event.type === 'snapshot') {
      setRunDetail((current) => current && current.id === event.run.id ? event.run : current);
      setRuns((current) => current.map((item) => item.id === event.run.id ? event.run : item));
      setRunStreamState({ loading: false });
      return;
    }
    if (event.type === 'log') {
      setRunLogs((current) => mergeExecutionLogs(current, [toExecutionLogEntryFromStream(event)]));
      setRunStreamState({ loading: false });
      return;
    }
    if (event.type === 'connected') {
      setRunStreamState({ loading: false, success: translate('auto.k0853', { value0: event.status }) });
      return;
    }
    setRunStreamState((current) => ({ ...current, loading: false }));
  }

  async function onLoadMoreLogs() {
    if (!selectedRunId || runLogHistoryState.loadingMore || runLogs.length >= runLogHistoryState.total) {
      return;
    }
    await loadRunLogHistory(selectedRunId, runLogHistoryState.index + 1, true);
  }

  return (
    <section className="execution-workbench" data-testid="execution-workbench">
      <div className="metric-grid execution-metric-grid">
        <MetricCard label={translate('auto.k0854')} value={String(summary.ready)} icon={<CheckCircle2 size={18} />} />
        <MetricCard label={translate('auto.k0855')} value={String(summary.running)} icon={<Clock3 size={18} />} />
        <MetricCard label={translate('auto.k0856')} value={String(summary.failed)} icon={<AlertTriangle size={18} />} />
        <MetricCard label={translate('auto.k0857')} value={String(summary.enabledTriggers)} icon={<Webhook size={18} />} />
      </div>

      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">{translate('auto.k0858')}</div>
            <div className="panel-desc">{health ? `${health.service} · ${health.status}` : loadState.loading ? translate('auto.k0168') : translate('auto.k0169')}</div>
          </div>
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshWorkbench()} disabled={loadState.loading}>
            <RefreshCw size={15} />
            {translate('auto.k0170')}</button>
        </div>
        <div className="panel-body compact">
          {loadState.error && <div className="document-state-line error">{loadState.error}</div>}
          <div className="execution-policy-grid">
            <PolicyItem label="Scheduler" value={health?.schedulerEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Webhook" value={health?.webhookEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Cron" value={health?.cronEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Cron scanner" value={health?.policy?.cronScannerReady ? 'READY' : 'NOT_READY'} />
            <PolicyItem label="WP6 dispatch" value={health?.policy?.wp6DispatchReady ? 'READY' : 'NOT_READY'} />
            <PolicyItem label="Recovery" value={`${health?.recoveryBatchSize ?? 0}`} />
          </div>
        </div>
      </section>

      <section className="execution-layout">
        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">{translate('auto.k0860')}</div>
              <div className="panel-desc">{translate('auto.k0861')}</div>
            </div>
            <div className="execution-panel-actions">
              <button className="btn btn-primary btn-sm" type="button" onClick={openCreatePlanDrawer} disabled={!canManage || planActionState.loading}>
                <FileText size={15} />
                {translate('auto.k0860')}
              </button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={openEditPlanDrawer} disabled={!canManage || !planDetail || planActionState.loading}>
                <ShieldCheck size={15} />
                {translate('auto.k0859')}
              </button>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onArchivePlan()} disabled={!canManage || !selectedPlanId || planActionState.loading || planDetail?.status === 'ARCHIVED'}>
                <Trash2 size={15} />
                {translate('auto.k0871')}
              </button>
            </div>
          </div>
          <div className="panel-body compact">
            {planActionState.error && <div className="document-state-line error">{planActionState.error}</div>}
            {planActionState.success && <div className="document-state-line success">{planActionState.success}</div>}
          </div>
        </section>

        <Drawer
          className="execution-plan-drawer"
          destroyOnHidden
          maskClosable={!planActionState.loading}
          open={planDrawerOpen}
          placement="right"
          title={planDraftMode === 'edit' ? translate('auto.k0859') : translate('auto.k0860')}
          width={900}
          onClose={() => {
            if (!planActionState.loading) {
              setPlanDrawerOpen(false);
            }
          }}
        >
        <form className="document-form document-drawer-form" onSubmit={planDraftMode === 'edit' ? submitUpdatePlan : onCreatePlan}>
          <div className="panel-header execution-drawer-header">
            <div>
              <div className="panel-title">{planDraftMode === 'edit' ? translate('auto.k0859') : translate('auto.k0860')}</div>
              <div className="panel-desc">{translate('auto.k0861')}</div>
            </div>
            <div className="execution-panel-actions">
              <button className="btn btn-ghost btn-sm" type="button" onClick={resetPlanDraft}>
                <RefreshCw size={15} />
                {translate('auto.k0489')}</button>
              <button className="btn btn-primary btn-sm" type="submit" disabled={!canManage || planActionState.loading}>
                <FileText size={15} />
                {planDraftMode === 'edit' ? translate('auto.k0870') : translate('auto.k0860')}</button>
            </div>
          </div>
          <div className="panel-body">
            <div className="form-grid">
              <Field label={translate('auto.k0176')}>
                <input value={planDraft.projectId} onChange={(event) => setPlanDraftValue('projectId', event.target.value)} />
              </Field>
              <Field label={translate('auto.k0177')}>
                <input value={planDraft.name} onChange={(event) => setPlanDraftValue('name', event.target.value)} />
              </Field>
              <Field label={translate('auto.k0215')}>
                <input value={planDraft.environmentKey} onChange={(event) => setPlanDraftValue('environmentKey', event.target.value)} />
              </Field>
              <Field label={translate('auto.k0182')}>
                <NativeSelect value={planDraft.status} onChange={(event) => setPlanDraftValue('status', event.target.value as ExecutionPlanDraft['status'])}>
                  <option value="DRAFT">{dictionaryLabel('DRAFT')}</option>
                  <option value="READY">{dictionaryLabel('READY')}</option>
                  <option value="DISABLED">{dictionaryLabel('DISABLED')}</option>
                </NativeSelect>
              </Field>
            </div>
            <Field label={translate('auto.k0443')}>
              <input value={planDraft.description} onChange={(event) => setPlanDraftValue('description', event.target.value)} />
            </Field>
            <div className="execution-dag-editor">
              <div className="execution-subheader">
                <strong>{translate('auto.k0863')}</strong>
                <button className="btn btn-secondary btn-sm" type="button" onClick={addPlanNode} disabled={!canManage}>
                  <FileText size={15} />
                  {translate('auto.k0864')}</button>
              </div>
              {planDraft.nodes.map((node, index) => (
                <div className="execution-node-editor" key={`${node.key}-${index}`}>
                  <div className="execution-node-editor-head">
                    <span className="mono">{node.key || `node-${index + 1}`}</span>
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => removePlanNode(index)} disabled={planDraft.nodes.length <= 1 || !canManage}>
                      <Trash2 size={15} />
                      {translate('auto.k0451')}</button>
                  </div>
                  <div className="form-grid">
                    <Field label="node key">
                      <input value={node.key} onChange={(event) => setPlanNodeDraftValue(index, 'key', event.target.value)} />
                    </Field>
                    <Field label={translate('auto.k0865')}>
                      <NativeSelect value={node.type} onChange={(event) => setPlanNodeDraftValue(index, 'type', event.target.value as ExecutionDagNodeDraft['type'])}>
                        <option value="API_TEST">{dictionaryLabel('API_TEST')}</option>
                        <option value="REPORT_HANDOFF">{dictionaryLabel('REPORT_HANDOFF')}</option>
                      </NativeSelect>
                    </Field>
                    <Field label={translate('auto.k0866')}>
                      <input value={node.dependenciesText} onChange={(event) => setPlanNodeDraftValue(index, 'dependenciesText', event.target.value)} />
                    </Field>
                    <Field label="bundleId">
                      <input value={node.apiAutomationBundleId} onChange={(event) => setPlanNodeDraftValue(index, 'apiAutomationBundleId', event.target.value)} />
                    </Field>
                    <Field label="baseUrlRef">
                      <input value={node.baseUrlRef} onChange={(event) => setPlanNodeDraftValue(index, 'baseUrlRef', event.target.value)} />
                    </Field>
                    <Field label="caseIds">
                      <input value={node.caseIdsText} onChange={(event) => setPlanNodeDraftValue(index, 'caseIdsText', event.target.value)} />
                    </Field>
                    <Field label="secretRefs">
                      <input value={node.runtimeSecretRefsText} onChange={(event) => setPlanNodeDraftValue(index, 'runtimeSecretRefsText', event.target.value)} />
                    </Field>
                    {node.type === 'API_TEST' && (
                      <>
                        <Field label="accountPoolRef">
                          <input value={node.accountPoolRef} onChange={(event) => setPlanNodeDraftValue(index, 'accountPoolRef', event.target.value)} />
                        </Field>
                        <Field label="lease app">
                          <input value={node.accountLeaseApplicationId} onChange={(event) => setPlanNodeDraftValue(index, 'accountLeaseApplicationId', event.target.value)} />
                        </Field>
                        <Field label="lease env">
                          <input value={node.accountLeaseEnvironmentId} onChange={(event) => setPlanNodeDraftValue(index, 'accountLeaseEnvironmentId', event.target.value)} />
                        </Field>
                        <Field label="lease roles">
                          <input value={node.accountLeaseRoleTagsText} onChange={(event) => setPlanNodeDraftValue(index, 'accountLeaseRoleTagsText', event.target.value)} />
                        </Field>
                        <Field label="lease TTL">
                          <input type="number" min={0} max={604800} value={node.accountLeaseTtlSeconds} onChange={(event) => setPlanNodeDraftValue(index, 'accountLeaseTtlSeconds', Number(event.target.value))} />
                        </Field>
                        <Field label="lease key">
                          <input value={node.accountLeaseRequestKey} onChange={(event) => setPlanNodeDraftValue(index, 'accountLeaseRequestKey', event.target.value)} />
                        </Field>
                      </>
                    )}
                    <Field label={translate('auto.k0867')}>
                      <input type="number" min={1} max={86400} value={node.timeoutSeconds} onChange={(event) => setPlanNodeDraftValue(index, 'timeoutSeconds', Number(event.target.value))} />
                    </Field>
                    <Field label={translate('auto.k0868')}>
                      <NativeSelect value={node.failurePolicy} onChange={(event) => setPlanNodeDraftValue(index, 'failurePolicy', event.target.value as ExecutionDagNodeDraft['failurePolicy'])}>
                        <option value="FAIL_FAST">{dictionaryLabel('FAIL_FAST')}</option>
                        <option value="CONTINUE">{dictionaryLabel('CONTINUE')}</option>
                        <option value="BLOCK_DOWNSTREAM">{dictionaryLabel('BLOCK_DOWNSTREAM')}</option>
                      </NativeSelect>
                    </Field>
                    <Field label={translate('auto.k0869')}>
                      <input type="number" min={0} max={5} value={node.maxAttempts} onChange={(event) => setPlanNodeDraftValue(index, 'maxAttempts', Number(event.target.value))} />
                    </Field>
                  </div>
                  <div className="execution-digest-line">{summarizeDraftNode(node)}</div>
                </div>
              ))}
            </div>
            <div className="execution-panel-actions execution-form-actions">
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onArchivePlan()} disabled={!canManage || !selectedPlanId || planActionState.loading || planDetail?.status === 'ARCHIVED'}>
                <Trash2 size={15} />
                {translate('auto.k0871')}</button>
            </div>
            {planActionState.error && <div className="document-state-line error">{planActionState.error}</div>}
            {planActionState.success && <div className="document-state-line success">{planActionState.success}</div>}
          </div>
        </form>
        </Drawer>

        <section className="panel" data-testid="execution-plan-list">
          <div className="panel-header">
            <div>
              <div className="panel-title">{translate('auto.k0872')}</div>
              <div className="panel-desc">{plans.length} {translate('auto.k0181')}</div>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="table-wrap execution-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{translate('auto.k0873')}</th>
                    <th>{translate('auto.k0176')}</th>
                    <th>{translate('auto.k0182')}</th>
                    <th>{translate('auto.k0874')}</th>
                  </tr>
                </thead>
                <tbody>
                  {plans.length ? plans.map((plan) => (
                    <tr
                      key={plan.id}
                      className={selectedPlanId === plan.id ? 'selected-row' : undefined}
                      onClick={() => {
                        setSelectedPlanId(plan.id);
                        setSelectedTriggerId('');
                      }}
                    >
                      <td>
                        <span className="table-primary">{plan.name}</span>
                        <span className="table-secondary mono">{shortId(plan.dagDigest)}</span>
                      </td>
                      <td><span className="table-secondary">{plan.projectId}</span></td>
                      <td><StatusBadge status={plan.status} /></td>
                      <td>{plan.nodeCount}</td>
                    </tr>
                  )) : (
                    <tr><td className="table-empty" colSpan={4}>{loadState.loading ? translate('auto.k0168') : translate('auto.k0875')}</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </section>

      <section className="panel" data-testid="execution-dag-preview">
        <div className="panel-header">
          <div>
            <div className="panel-title">{translate('auto.k0876')}</div>
            <div className="panel-desc">{planDetail ? `${planDetail.name} · ${planDetail.environmentKey}` : translate('auto.k0877')}</div>
          </div>
          <div className="execution-panel-actions">
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onDryRunPlan()} disabled={!selectedPlanId || planActionState.loading}>
              <ShieldCheck size={15} />
              Dry run
            </button>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onTriggerRun()} disabled={!selectedPlanId || !canTrigger || runActionState.loading}>
              <Play size={15} />
              {translate('auto.k0217')}</button>
          </div>
        </div>
        <div className="panel-body compact">
          <div className="form-grid">
            <Field label="requestKey">
              <input value={manualRequestKey} onChange={(event) => setManualRequestKey(event.target.value)} />
            </Field>
            <Field label={translate('auto.k0878')}>
              <input value={manualReason} onChange={(event) => setManualReason(event.target.value)} />
            </Field>
          </div>
          {lastDryRun && (
            <div className="execution-sync-summary">
              <span>{lastDryRun.valid ? 'VALID' : 'INVALID'}</span>
              <span className="mono">{shortId(lastDryRun.dagDigest)}</span>
              <span>issues {lastDryRun.issues.length}</span>
            </div>
          )}
          {lastDryRun?.issues.length ? (
            <div className="execution-issue-list">
              {lastDryRun.issues.map((issue, index) => (
                <span key={`${issue.code}-${index}`}>
                  {issue.severity} · {issue.nodeKey ?? '-'} · {issue.code}
                </span>
              ))}
            </div>
          ) : null}
          <div className="execution-node-grid">
            {planDetail?.nodes.length ? planDetail.nodes.map((node) => (
              <div className="execution-node-card" key={node.key}>
                <div className="execution-node-card-head">
                  <strong>{node.key}</strong>
                  <StatusBadge status={node.type} />
                </div>
                <div className="execution-node-meta">
                  <span>{node.failurePolicy}</span>
                  <span>{node.timeoutSeconds}s</span>
                  <span>{node.dependencies.length ? node.dependencies.join(', ') : 'root'}</span>
                </div>
                <div className="execution-digest-line">{summaryText(node.inputSummary)}</div>
              </div>
            )) : (
              <div className="table-empty">{translate('auto.k0879')}</div>
            )}
          </div>
          {runActionState.error && <div className="document-state-line error">{runActionState.error}</div>}
          {runActionState.success && <div className="document-state-line success">{runActionState.success}</div>}
        </div>
      </section>

      <section className="execution-layout">
        <section className="panel" data-testid="execution-run-detail">
          <div className="panel-header">
            <div>
              <div className="panel-title">{translate('auto.k0880')}</div>
              <div className="panel-desc">{runDetail ? `${runDetail.status} · ${runDetail.triggerType}` : translate('auto.k0881')}</div>
            </div>
            <div className="execution-panel-actions">
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshRunDetail(selectedRunId)} disabled={!selectedRunId}>
                <RefreshCw size={15} />
                {translate('auto.k0170')}</button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onCancelRun()} disabled={!runDetail || !activeRunStatus(runDetail.status) || !canTrigger}>
                <Square size={15} />
                {translate('auto.k0220')}</button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onRetryRun()} disabled={!runDetail || !retryableRunStatus(runDetail.status) || !canTrigger}>
                <RotateCcw size={15} />
                {translate('auto.k0227')}</button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onExportRun()} disabled={!runDetail || !canExport || runActionState.loading}>
                <Download size={15} />
                {translate('auto.k0221')}</button>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="execution-sync-summary">
              <span>{runDetail?.id ? shortId(runDetail.id) : '-'}</span>
              <span>{runDetail?.traceId ?? '-'}</span>
              <span>{runDetail?.sourceEventId ?? runDetail?.requestKey ?? '-'}</span>
              <span>{fieldLabel('export')} {displayValueLabel(canExport ? 'ALLOWED' : 'BLOCKED')}</span>
            </div>
            {lastRunExport && (
              <div className="execution-sync-summary">
                <span>{lastRunExport.schemaVersion}</span>
                <span>{lastRunExport.exportedAt ? formatDateTime(lastRunExport.exportedAt) : '-'}</span>
                <span>{summaryText(lastRunExport.nodeStatusCounts)}</span>
                <span>{fieldLabel('secretRefs')} {displayValueLabel(lastRunExport.redactionPolicy.secretRefsExported ? 'YES' : 'BLOCKED')}</span>
              </div>
            )}
            {runDetail?.errorCode && (
              <div className="document-state-line error">
                {runDetail.errorCode}{runDetail.errorSummary ? ` · ${runDetail.errorSummary}` : ''}
              </div>
            )}
            {runStreamState.error && <div className="document-state-line error">{runStreamState.error}</div>}
            {runStreamState.success && <div className="document-state-line success">{runStreamState.success}</div>}
            <div className="execution-run-list">
              {runs.length ? runs.map((run) => (
                <button
                  key={run.id}
                  className={selectedRunId === run.id ? 'execution-run-item active' : 'execution-run-item'}
                  type="button"
                  onClick={() => setSelectedRunId(run.id)}
                >
                  <span>
                    <strong>{shortId(run.id)}</strong>
                    <small>{run.triggerType} · {formatDateTime(run.createdAt)}</small>
                  </span>
                  <StatusBadge status={run.status} />
                </button>
              )) : (
                <div className="table-empty">{translate('auto.k0882')}</div>
              )}
            </div>
            <div className="execution-node-grid">
              {runDetail?.nodes.length ? runDetail.nodes.map((node) => (
                <div className="execution-node-card" key={node.id}>
                  <div className="execution-node-card-head">
                    <strong>{node.nodeKey}</strong>
                    <StatusBadge status={node.status} />
                  </div>
                  <div className="execution-node-meta">
                    <span>{node.runnerType}</span>
                    <span>attempt {node.attempt}</span>
                    <span>{node.externalRunId ? shortId(node.externalRunId) : 'internal'}</span>
                  </div>
                  {node.errorCode && <div className="execution-digest-line error">{node.errorCode} · {node.errorSummary ?? ''}</div>}
                </div>
              )) : null}
            </div>
            <div className="execution-run-log-panel">
              <div className="execution-subheader">
                <strong>{translate('auto.k0883')}</strong>
                <span>{runDetail?.artifacts.length ?? 0}</span>
              </div>
              {runDetail?.artifacts.length ? (
                <div className="execution-node-grid">
                  {runDetail.artifacts.map((artifact) => (
                    <div className="execution-node-card" key={artifact.id}>
                      <div className="execution-node-card-head">
                        <strong>{artifact.artifactType}</strong>
                        <StatusBadge status={artifact.captureStatus} />
                      </div>
                      <div className="execution-node-meta">
                        <span>{artifact.sourceType}</span>
                        <span>{artifact.nodeKey || 'run'}</span>
                        <span>{artifact.sizeBytes} B</span>
                      </div>
                      <div className="execution-panel-actions">
                        <button
                          className="btn btn-ghost btn-sm"
                          type="button"
                          onClick={() => void onDownloadArtifact(artifact)}
                          disabled={!canExport || !artifact.downloadReady || runActionState.loading}
                        >
                          <Download size={15} />
                          {translate('auto.k0884')}</button>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="table-empty">{translate('auto.k0885')}</div>
              )}
            </div>
            <div className="execution-run-log-panel">
              <div className="execution-subheader">
                <strong>{translate('auto.k0886')}</strong>
                <span>{runStreamState.loading ? translate('auto.k0887') : `${runLogs.length}/${runLogHistoryState.total || runLogs.length}`}</span>
              </div>
              {runLogHistoryState.error ? <div className="document-state-line error">{runLogHistoryState.error}</div> : null}
              {runLogs.length ? (
                <div className="execution-run-log-list">
                  {runLogs.map((entry) => (
                    <div className={`execution-run-log-item tone-${logTone(entry.level)}`} key={entry.key}>
                      <strong>{entry.stage ?? entry.level}</strong>
                      <span>{entry.timestamp ? formatDateTime(entry.timestamp) : '-'}</span>
                      <em>{entry.nodeKey ?? 'run'}</em>
                      <span>{entry.message}</span>
                      {Object.keys(entry.metadata).length ? <small>{summaryText(entry.metadata)}</small> : null}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="table-empty">{runLogHistoryState.loading ? translate('auto.k0888') : translate('auto.k0889')}</div>
              )}
              <div className="execution-panel-actions">
                <button
                  className="btn btn-ghost btn-sm"
                  type="button"
                  onClick={() => void loadRunLogHistory(selectedRunId)}
                  disabled={!selectedRunId || runLogHistoryState.loading || runLogHistoryState.loadingMore}
                >
                  <RefreshCw size={15} />
                  {translate('auto.k0890')}</button>
                <button
                  className="btn btn-ghost btn-sm"
                  type="button"
                  onClick={() => void onLoadMoreLogs()}
                  disabled={!selectedRunId || runLogHistoryState.loadingMore || runLogs.length >= runLogHistoryState.total}
                >
                  <Clock3 size={15} />
                  {translate('auto.k0891')}</button>
              </div>
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">{translate('auto.k0892')}</div>
              <div className="panel-desc">{triggers.length} {translate('auto.k0893')}</div>
            </div>
            <button
              className="btn btn-primary btn-sm"
              type="button"
              onClick={openCreateTriggerDrawer}
              disabled={!canManage || !selectedPlanId || triggerActionState.loading}
            >
              <Webhook size={15} />
              {translate('auto.k0894')}</button>
          </div>
          <div className="panel-body compact">
            <Drawer
              className="execution-trigger-drawer"
              destroyOnHidden
              maskClosable={!triggerActionState.loading}
              open={triggerDrawerOpen}
              placement="right"
              title={translate('auto.k0894')}
              width={560}
              onClose={() => {
                if (!triggerActionState.loading) {
                  setTriggerDrawerOpen(false);
                }
              }}
            >
            <form className="execution-trigger-form document-drawer-form" onSubmit={onCreateTrigger}>
              <Field label={translate('auto.k0286')}>
                <NativeSelect value={triggerDraft.triggerType} onChange={(event) => setTriggerDraftValue('triggerType', event.target.value as TriggerDraft['triggerType'])}>
                  <option value="WEBHOOK">{dictionaryLabel('WEBHOOK')}</option>
                  <option value="CRON">{dictionaryLabel('CRON')}</option>
                </NativeSelect>
              </Field>
              <Field label={translate('auto.k0182')}>
                <NativeSelect value={triggerDraft.status} onChange={(event) => setTriggerDraftValue('status', event.target.value as TriggerDraft['status'])}>
                  <option value="DISABLED">{dictionaryLabel('DISABLED')}</option>
                  <option value="ENABLED">{dictionaryLabel('ENABLED')}</option>
                  <option value="PAUSED">{dictionaryLabel('PAUSED')}</option>
                </NativeSelect>
              </Field>
              <Field label={triggerDraft.triggerType === 'WEBHOOK' ? 'source' : 'cron'}>
                <input
                  value={triggerDraft.triggerType === 'WEBHOOK' ? triggerDraft.source : triggerDraft.cron}
                  onChange={(event) => triggerDraft.triggerType === 'WEBHOOK'
                    ? setTriggerDraftValue('source', event.target.value)
                    : setTriggerDraftValue('cron', event.target.value)}
                />
              </Field>
              <Field label={triggerDraft.triggerType === 'WEBHOOK' ? 'eventType' : 'timezone'}>
                <input
                  value={triggerDraft.triggerType === 'WEBHOOK' ? triggerDraft.eventType : triggerDraft.timezone}
                  onChange={(event) => triggerDraft.triggerType === 'WEBHOOK'
                    ? setTriggerDraftValue('eventType', event.target.value)
                    : setTriggerDraftValue('timezone', event.target.value)}
                />
              </Field>
              <Field label="secretRef">
                <input value={triggerDraft.secretRef} onChange={(event) => setTriggerDraftValue('secretRef', event.target.value)} />
              </Field>
              <div className="document-actions">
                <button className="btn btn-primary" type="submit" disabled={!canManage || !selectedPlanId || triggerActionState.loading}>
                  <Webhook size={16} />
                  {translate('auto.k0894')}</button>
                <button className="btn btn-secondary" type="button" disabled={triggerActionState.loading} onClick={() => setTriggerDrawerOpen(false)}>
                  {translate('actions.cancel')}</button>
              </div>
              {triggerActionState.error && <div className="document-state-line error">{triggerActionState.error}</div>}
              {triggerActionState.success && <div className="document-state-line success">{triggerActionState.success}</div>}
            </form>
            </Drawer>
            {triggerActionState.error && <div className="document-state-line error">{triggerActionState.error}</div>}
            {triggerActionState.success && <div className="document-state-line success">{triggerActionState.success}</div>}
            {lastTriggerDryRun && (
              <div className="execution-sync-summary">
                <span>{displayValueLabel(lastTriggerDryRun.valid ? 'VALID' : 'INVALID')}</span>
                <span>{fieldLabel('globalEnabled')} {displayValueLabel(lastTriggerDryRun.globalEnabled ? 'ON' : 'OFF')}</span>
                <span>{fieldLabel('runCreated')} {displayValueLabel(lastTriggerDryRun.runCreated ? 'YES' : 'NO')}</span>
              </div>
            )}
            <div className="execution-trigger-list">
              {triggers.length ? triggers.map((trigger) => (
                <div className="execution-trigger-item" key={trigger.id}>
                  <div>
                    <strong>{trigger.triggerType}</strong>
                    <span>{dictionaryLabel(trigger.status)} · {fieldLabel('secretRef')} {displayValueLabel(trigger.secretRefConfigured ? 'SET' : 'OFF')}</span>
                    <small className="mono">{shortId(trigger.secretRefDigest ?? trigger.configDigest)}</small>
                  </div>
                  <div className="execution-panel-actions">
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => {
                      if (selectedTriggerId === trigger.id) {
                        void refreshTriggerEvents(trigger.id);
                      } else {
                        setSelectedTriggerId(trigger.id);
                      }
                    }}>
                      <RefreshCw size={15} />
                      {translate('auto.k0895')}</button>
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onDryRunTrigger(trigger)}>
                      <ShieldCheck size={15} />
                      Dry run
                    </button>
                    <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onToggleTrigger(trigger)} disabled={!canManage}>
                      {trigger.status === 'ENABLED' ? <PauseCircle size={15} /> : <Play size={15} />}
                      {trigger.status === 'ENABLED' ? translate('auto.k0896') : translate('auto.k0251')}
                    </button>
                  </div>
                </div>
              )) : (
                <div className="table-empty">{translate('auto.k0897')}</div>
              )}
            </div>
            <div className="execution-event-list">
              {triggerEvents.length ? triggerEvents.map((event) => (
                <div className="execution-event-item" key={event.id}>
                  <span>{event.status} · {event.sourceEventId}</span>
                  <small className="mono">{event.traceId ?? shortId(event.requestDigest)}{event.runId ? ` · ${shortId(event.runId)}` : ''}</small>
                </div>
              )) : <div className="table-empty">{translate('auto.k0898')}</div>}
            </div>
          </div>
        </section>
      </section>
    </section>
  );

  function setPlanDraftValue<K extends keyof ExecutionPlanDraft>(key: K, value: ExecutionPlanDraft[K]) {
    setPlanDraft((current) => ({ ...current, [key]: value }));
    setPlanActionState({ loading: false });
  }

  function setPlanNodeDraftValue<K extends keyof ExecutionDagNodeDraft>(
    index: number,
    key: K,
    value: ExecutionDagNodeDraft[K]
  ) {
    setPlanDraft((current) => ({
      ...current,
      nodes: current.nodes.map((node, nodeIndex) => {
        if (nodeIndex !== index) return node;
        const updated = { ...node, [key]: value };
        if (key === 'type' && value !== 'API_TEST') {
          return {
            ...updated,
            accountPoolRef: '',
            accountLeaseApplicationId: '',
            accountLeaseEnvironmentId: '',
            accountLeaseRoleTagsText: '',
            accountLeaseTtlSeconds: 0,
            accountLeaseRequestKey: ''
          };
        }
        return updated;
      })
    }));
    setPlanActionState({ loading: false });
  }

  function addPlanNode() {
    setPlanDraft((current) => ({ ...current, nodes: [...current.nodes, blankExecutionNodeDraft(current.nodes.length + 1)] }));
    setPlanActionState({ loading: false });
  }

  function removePlanNode(index: number) {
    setPlanDraft((current) => ({
      ...current,
      nodes: current.nodes.length <= 1 ? current.nodes : current.nodes.filter((_, nodeIndex) => nodeIndex !== index)
    }));
    setPlanActionState({ loading: false });
  }

  function openCreatePlanDrawer() {
    resetPlanDraft();
    setPlanDrawerOpen(true);
  }

  function openEditPlanDrawer() {
    if (!planDetail) {
      return;
    }
    setPlanDraft(executionPlanDraftFromDetail(planDetail));
    setPlanDraftMode('edit');
    setPlanActionState({ loading: false });
    setPlanDrawerOpen(true);
  }

  function openCreateTriggerDrawer() {
    setTriggerDraft(initialTriggerDraft);
    setTriggerActionState({ loading: false });
    setTriggerDrawerOpen(true);
  }

  function resetPlanDraft() {
    setPlanDraft({
      ...initialExecutionPlanDraft,
      nodes: [blankExecutionNodeDraft(1)]
    });
    setPlanDraftMode('create');
    setSelectedPlanId('');
    setSelectedTriggerId('');
    setPlanDetail(null);
    setTriggers([]);
    setTriggerEvents([]);
    setLastDryRun(null);
    setPlanActionState({ loading: false });
  }

  function setTriggerDraftValue<K extends keyof TriggerDraft>(key: K, value: TriggerDraft[K]) {
    setTriggerDraft((current) => ({ ...current, [key]: value }));
    setTriggerActionState({ loading: false });
  }
}

function MetricCard(props: { label: string; value: string; icon: ReactNode }) {
  return (
    <div className="metric-card">
      <div className="metric-icon">{props.icon}</div>
      <div className="metric-body">
        <span className="metric-value">{props.value}</span>
        <span className="metric-label">{fieldLabel(props.label)}</span>
      </div>
    </div>
  );
}

function PolicyItem(props: { label: string; value: string }) {
  return (
    <div className="execution-policy-item">
      <span>{fieldLabel(props.label)}</span>
      <strong>{displayValueLabel(props.value)}</strong>
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

function StatusBadge(props: { status: string }) {
  const status = props.status || 'UNKNOWN';
  const tone = ['FAILED', 'TIMEOUT', 'BLOCKED', 'CANCELED', 'REJECTED'].includes(status) ? 'danger'
    : ['READY', 'RUNNING', 'QUEUED', 'ENABLED', 'ACCEPTED', 'SUCCEEDED', 'API_TEST'].includes(status) ? 'success'
      : status === 'PARTIAL_SUCCESS' || status === 'PAUSED' || status === 'REPORT_HANDOFF' ? 'warning'
        : 'neutral';
  return <span className={`status-badge ${tone}`} title={status}>{dictionaryLabel(status)}</span>;
}

function activeRunStatus(status: string) {
  return status === 'QUEUED' || status === 'RUNNING';
}

function retryableRunStatus(status: string) {
  return status === 'FAILED' || status === 'TIMEOUT' || status === 'PARTIAL_SUCCESS';
}

function logTone(level: ExecutionLogEntry['level']) {
  if (level === 'ERROR') return 'danger';
  if (level === 'WARN') return 'warning';
  if (level === 'SUCCESS') return 'success';
  return 'info';
}

function toExecutionLogEntry(event: ExecutionRunLogHistory['items'][number]): ExecutionLogEntry {
  return {
    key: event.id,
    id: event.id,
    level: event.level,
    stage: event.stage,
    message: event.message,
    nodeKey: event.nodeKey,
    timestamp: event.eventAt ?? event.createdAt,
    metadata: event.metadata
  };
}

function toExecutionLogEntryFromStream(event: Extract<ExecutionRunStreamEvent, { type: 'log' }>): ExecutionLogEntry {
  return {
    key: event.logId ?? `${event.timestamp ?? ''}:${event.stage ?? ''}:${event.message}:${event.nodeKey ?? 'run'}`,
    id: event.logId,
    level: event.level,
    stage: event.stage,
    message: event.message,
    nodeKey: event.nodeKey,
    timestamp: event.timestamp,
    metadata: event.metadata
  };
}

function mergeExecutionLogs(current: ExecutionLogEntry[], incoming: ExecutionLogEntry[]) {
  const merged = new Map<string, ExecutionLogEntry>();
  [...incoming, ...current].forEach((entry) => {
    merged.set(entry.id ?? entry.key, entry);
  });
  return Array.from(merged.values()).sort((left, right) => {
    const leftTime = left.timestamp ? Date.parse(left.timestamp) : 0;
    const rightTime = right.timestamp ? Date.parse(right.timestamp) : 0;
    return rightTime - leftTime;
  });
}

function shortId(value?: string) {
  return value ? value.slice(0, 8) : '-';
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').replace('Z', '') : '-';
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function summaryText(value: Record<string, unknown>) {
  const entries = Object.entries(value).slice(0, 4);
  if (!entries.length) return fieldLabel('summary');
  return entries.map(([key, entryValue]) => `${fieldLabel(key)}=${displayValueLabel(entryValue)}`).join(' · ');
}
