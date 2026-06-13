import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
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
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  archiveExecutionPlan,
  cancelExecutionRun,
  createExecutionPlan,
  createExecutionTrigger,
  dryRunExecutionPlan,
  dryRunExecutionTrigger,
  fetchExecutionHealth,
  fetchExecutionPlan,
  fetchExecutionPlans,
  fetchExecutionRun,
  fetchExecutionRuns,
  fetchExecutionTriggerEvents,
  fetchExecutionTriggers,
  retryExecutionRun,
  triggerExecutionRun,
  updateExecutionPlan,
  updateExecutionTrigger,
  type ExecutionDryRun,
  type ExecutionHealth,
  type ExecutionPlanDetail,
  type ExecutionPlanSummary,
  type ExecutionRunDetail,
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

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
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
  const [triggerDraft, setTriggerDraft] = useState<TriggerDraft>(initialTriggerDraft);
  const [manualReason, setManualReason] = useState('');
  const [manualRequestKey, setManualRequestKey] = useState('');
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [planActionState, setPlanActionState] = useState<WorkState>({ loading: false });
  const [runActionState, setRunActionState] = useState<WorkState>({ loading: false });
  const [triggerActionState, setTriggerActionState] = useState<WorkState>({ loading: false });
  const [lastDryRun, setLastDryRun] = useState<ExecutionDryRun | null>(null);
  const [lastTriggerDryRun, setLastTriggerDryRun] = useState<ExecutionTriggerDryRun | null>(null);

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
      const nextPlanId = selectedPlanId || planResult.data.items[0]?.id || '';
      if (nextPlanId) {
        setSelectedPlanId(nextPlanId);
      }
      const nextRunId = selectedRunId || runResult.data.items[0]?.id || '';
      if (nextRunId) {
        setSelectedRunId(nextRunId);
      }
    } catch (error: unknown) {
      setLoadState({ loading: false, error: error instanceof Error ? error.message : '加载失败' });
    }
  }, [canRead, props.signedIn, selectedPlanId, selectedRunId]);

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
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : '加载计划详情失败' });
    }
  }, [canRead]);

  const refreshRunDetail = useCallback(async (runId: string) => {
    if (!runId || !canRead) {
      setRunDetail(null);
      return;
    }
    try {
      const result = await fetchExecutionRun(runId);
      setRunDetail(result.data);
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '加载运行详情失败' });
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
    if (selectedTriggerId) {
      void refreshTriggerEvents(selectedTriggerId);
    }
  }, [selectedTriggerId]);

  if (!props.signedIn) {
    return <div className="notice warning">请先登录后查看执行编排。</div>;
  }

  if (!canRead) {
    return <div className="notice error">当前账号缺少 execution:read 权限。</div>;
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
      setPlanActionState({ loading: false, success: '执行计划已创建' });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : '创建计划失败' });
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
      setPlanActionState({ loading: false, success: '执行计划已更新' });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : '更新计划失败' });
    }
  }

  async function onArchivePlan() {
    if (!selectedPlanId || !canManage) return;
    setPlanActionState({ loading: true });
    try {
      const result = await archiveExecutionPlan(selectedPlanId);
      setPlanDetail(result.data);
      setPlans((current) => current.map((plan) => plan.id === result.data.id ? result.data : plan));
      setPlanActionState({ loading: false, success: '执行计划已归档' });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : '归档计划失败' });
    }
  }

  async function onDryRunPlan() {
    if (!selectedPlanId) return;
    setPlanActionState({ loading: true });
    try {
      const result = await dryRunExecutionPlan(selectedPlanId);
      setLastDryRun(result.data);
      setPlanActionState({ loading: false, success: result.data.valid ? 'DAG 校验通过' : 'DAG 校验未通过' });
    } catch (error: unknown) {
      setPlanActionState({ loading: false, error: error instanceof Error ? error.message : 'Dry run 失败' });
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
      setRunActionState({ loading: false, success: result.data.idempotentReplay ? '已回放既有运行' : '运行已触发' });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '触发失败' });
    }
  }

  async function onCancelRun() {
    if (!runDetail || !canTrigger) return;
    setRunActionState({ loading: true });
    try {
      const result = await cancelExecutionRun(runDetail.id);
      setRunDetail(result.data);
      mergeRun(result.data);
      setRunActionState({ loading: false, success: '运行已取消或保持终态' });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '取消失败' });
    }
  }

  async function onRetryRun() {
    if (!runDetail || !canTrigger) return;
    setRunActionState({ loading: true });
    try {
      const result = await retryExecutionRun(runDetail.id);
      setRunDetail(result.data);
      mergeRun(result.data);
      setRunActionState({ loading: false, success: '重试已提交' });
    } catch (error: unknown) {
      setRunActionState({ loading: false, error: error instanceof Error ? error.message : '重试失败' });
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
      setTriggerActionState({ loading: false, success: '触发配置已创建' });
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : '创建触发配置失败' });
    }
  }

  async function onToggleTrigger(trigger: ExecutionTrigger) {
    if (!canManage) return;
    const nextStatus = trigger.status === 'ENABLED' ? 'PAUSED' : 'ENABLED';
    setTriggerActionState({ loading: true });
    try {
      const result = await updateExecutionTrigger(trigger.id, { status: nextStatus });
      setTriggers((current) => current.map((item) => item.id === result.data.id ? result.data : item));
      setTriggerActionState({ loading: false, success: `触发配置已更新为 ${result.data.status}` });
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : '更新触发配置失败' });
    }
  }

  async function onDryRunTrigger(trigger: ExecutionTrigger) {
    setTriggerActionState({ loading: true });
    try {
      const result = await dryRunExecutionTrigger(trigger.id);
      setLastTriggerDryRun(result.data);
      setTriggerActionState({ loading: false, success: result.data.valid ? '触发配置校验通过' : '触发配置未就绪' });
    } catch (error: unknown) {
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : '触发配置 dry run 失败' });
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
      setTriggerActionState({ loading: false, error: error instanceof Error ? error.message : '加载触发事件失败' });
    }
  }

  function mergeRun(run: ExecutionRunDetail) {
    setRuns((current) => current.map((item) => item.id === run.id ? run : item));
  }

  return (
    <section className="execution-workbench" data-testid="execution-workbench">
      <div className="metric-grid execution-metric-grid">
        <MetricCard label="READY 计划" value={String(summary.ready)} icon={<CheckCircle2 size={18} />} />
        <MetricCard label="运行中" value={String(summary.running)} icon={<Clock3 size={18} />} />
        <MetricCard label="失败/超时" value={String(summary.failed)} icon={<AlertTriangle size={18} />} />
        <MetricCard label="启用触发" value={String(summary.enabledTriggers)} icon={<Webhook size={18} />} />
      </div>

      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">调度策略</div>
            <div className="panel-desc">{health ? `${health.service} · ${health.status}` : loadState.loading ? '加载中' : '未加载'}</div>
          </div>
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshWorkbench()} disabled={loadState.loading}>
            <RefreshCw size={15} />
            刷新
          </button>
        </div>
        <div className="panel-body compact">
          {loadState.error && <div className="document-state-line error">{loadState.error}</div>}
          <div className="execution-policy-grid">
            <PolicyItem label="Scheduler" value={health?.schedulerEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Webhook" value={health?.webhookEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Cron" value={health?.cronEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Cron scanner" value={health?.policy?.cronScannerReady ? 'READY' : 'NOT READY'} />
            <PolicyItem label="WP6 dispatch" value={health?.policy?.wp6DispatchReady ? 'READY' : 'NOT READY'} />
            <PolicyItem label="Recovery" value={`${health?.recoveryBatchSize ?? 0}`} />
          </div>
        </div>
      </section>

      <section className="execution-layout">
        <form className="panel" onSubmit={onCreatePlan}>
          <div className="panel-header">
            <div>
              <div className="panel-title">{planDraftMode === 'edit' ? '编辑计划' : '新建计划'}</div>
              <div className="panel-desc">多节点 DAG · digest safe</div>
            </div>
            <div className="execution-panel-actions">
              <button className="btn btn-ghost btn-sm" type="button" onClick={resetPlanDraft}>
                <RefreshCw size={15} />
                新建
              </button>
              <button className="btn btn-primary btn-sm" type="submit" disabled={!canManage || planActionState.loading}>
                <FileText size={15} />
                创建
              </button>
            </div>
          </div>
          <div className="panel-body">
            <div className="form-grid">
              <Field label="项目">
                <input value={planDraft.projectId} onChange={(event) => setPlanDraftValue('projectId', event.target.value)} />
              </Field>
              <Field label="名称">
                <input value={planDraft.name} onChange={(event) => setPlanDraftValue('name', event.target.value)} />
              </Field>
              <Field label="环境">
                <input value={planDraft.environmentKey} onChange={(event) => setPlanDraftValue('environmentKey', event.target.value)} />
              </Field>
              <Field label="状态">
                <select value={planDraft.status} onChange={(event) => setPlanDraftValue('status', event.target.value as ExecutionPlanDraft['status'])}>
                  <option value="DRAFT">DRAFT</option>
                  <option value="READY">READY</option>
                  <option value="DISABLED">DISABLED</option>
                </select>
              </Field>
            </div>
            <Field label="描述">
              <input value={planDraft.description} onChange={(event) => setPlanDraftValue('description', event.target.value)} />
            </Field>
            <div className="execution-dag-editor">
              <div className="execution-subheader">
                <strong>DAG 节点</strong>
                <button className="btn btn-secondary btn-sm" type="button" onClick={addPlanNode} disabled={!canManage}>
                  <FileText size={15} />
                  添加节点
                </button>
              </div>
              {planDraft.nodes.map((node, index) => (
                <div className="execution-node-editor" key={`${node.key}-${index}`}>
                  <div className="execution-node-editor-head">
                    <span className="mono">{node.key || `node-${index + 1}`}</span>
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => removePlanNode(index)} disabled={planDraft.nodes.length <= 1 || !canManage}>
                      <Trash2 size={15} />
                      删除
                    </button>
                  </div>
                  <div className="form-grid">
                    <Field label="node key">
                      <input value={node.key} onChange={(event) => setPlanNodeDraftValue(index, 'key', event.target.value)} />
                    </Field>
                    <Field label="节点类型">
                      <select value={node.type} onChange={(event) => setPlanNodeDraftValue(index, 'type', event.target.value as ExecutionDagNodeDraft['type'])}>
                        <option value="API_TEST">API_TEST</option>
                        <option value="REPORT_HANDOFF">REPORT_HANDOFF</option>
                      </select>
                    </Field>
                    <Field label="依赖">
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
                    <Field label="超时秒">
                      <input type="number" min={1} max={86400} value={node.timeoutSeconds} onChange={(event) => setPlanNodeDraftValue(index, 'timeoutSeconds', Number(event.target.value))} />
                    </Field>
                    <Field label="失败策略">
                      <select value={node.failurePolicy} onChange={(event) => setPlanNodeDraftValue(index, 'failurePolicy', event.target.value as ExecutionDagNodeDraft['failurePolicy'])}>
                        <option value="FAIL_FAST">FAIL_FAST</option>
                        <option value="CONTINUE">CONTINUE</option>
                        <option value="BLOCK_DOWNSTREAM">BLOCK_DOWNSTREAM</option>
                      </select>
                    </Field>
                    <Field label="重试次数">
                      <input type="number" min={0} max={5} value={node.maxAttempts} onChange={(event) => setPlanNodeDraftValue(index, 'maxAttempts', Number(event.target.value))} />
                    </Field>
                  </div>
                  <div className="execution-digest-line">{summarizeDraftNode(node)}</div>
                </div>
              ))}
            </div>
            <div className="execution-panel-actions execution-form-actions">
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onUpdatePlan()} disabled={!canManage || !selectedPlanId || planActionState.loading || planDraftMode !== 'edit'}>
                <ShieldCheck size={15} />
                保存更新
              </button>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onArchivePlan()} disabled={!canManage || !selectedPlanId || planActionState.loading || planDetail?.status === 'ARCHIVED'}>
                <Trash2 size={15} />
                归档
              </button>
            </div>
            {planActionState.error && <div className="document-state-line error">{planActionState.error}</div>}
            {planActionState.success && <div className="document-state-line success">{planActionState.success}</div>}
          </div>
        </form>

        <section className="panel" data-testid="execution-plan-list">
          <div className="panel-header">
            <div>
              <div className="panel-title">计划列表</div>
              <div className="panel-desc">{plans.length} 条</div>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="table-wrap execution-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>计划</th>
                    <th>项目</th>
                    <th>状态</th>
                    <th>节点</th>
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
                    <tr><td className="table-empty" colSpan={4}>{loadState.loading ? '加载中' : '暂无执行计划'}</td></tr>
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
            <div className="panel-title">DAG 与运行</div>
            <div className="panel-desc">{planDetail ? `${planDetail.name} · ${planDetail.environmentKey}` : '未选择计划'}</div>
          </div>
          <div className="execution-panel-actions">
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onDryRunPlan()} disabled={!selectedPlanId || planActionState.loading}>
              <ShieldCheck size={15} />
              Dry run
            </button>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onTriggerRun()} disabled={!selectedPlanId || !canTrigger || runActionState.loading}>
              <Play size={15} />
              运行
            </button>
          </div>
        </div>
        <div className="panel-body compact">
          <div className="form-grid">
            <Field label="requestKey">
              <input value={manualRequestKey} onChange={(event) => setManualRequestKey(event.target.value)} />
            </Field>
            <Field label="原因">
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
              <div className="table-empty">暂无 DAG 节点</div>
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
              <div className="panel-title">运行详情</div>
              <div className="panel-desc">{runDetail ? `${runDetail.status} · ${runDetail.triggerType}` : '未选择运行'}</div>
            </div>
            <div className="execution-panel-actions">
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshRunDetail(selectedRunId)} disabled={!selectedRunId}>
                <RefreshCw size={15} />
                刷新
              </button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onCancelRun()} disabled={!runDetail || !activeRunStatus(runDetail.status) || !canTrigger}>
                <Square size={15} />
                取消
              </button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onRetryRun()} disabled={!runDetail || !retryableRunStatus(runDetail.status) || !canTrigger}>
                <RotateCcw size={15} />
                重试
              </button>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="execution-sync-summary">
              <span>{runDetail?.id ? shortId(runDetail.id) : '-'}</span>
              <span>{runDetail?.traceId ?? '-'}</span>
              <span>{runDetail?.sourceEventId ?? runDetail?.requestKey ?? '-'}</span>
              <span>export {canExport ? 'allowed' : 'blocked'}</span>
            </div>
            {runDetail?.errorCode && (
              <div className="document-state-line error">
                {runDetail.errorCode}{runDetail.errorSummary ? ` · ${runDetail.errorSummary}` : ''}
              </div>
            )}
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
                <div className="table-empty">暂无运行记录</div>
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
          </div>
        </section>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">触发配置</div>
              <div className="panel-desc">{triggers.length} 条 · secret masked</div>
            </div>
          </div>
          <div className="panel-body compact">
            <form className="execution-trigger-form" onSubmit={onCreateTrigger}>
              <Field label="类型">
                <select value={triggerDraft.triggerType} onChange={(event) => setTriggerDraftValue('triggerType', event.target.value as TriggerDraft['triggerType'])}>
                  <option value="WEBHOOK">WEBHOOK</option>
                  <option value="CRON">CRON</option>
                </select>
              </Field>
              <Field label="状态">
                <select value={triggerDraft.status} onChange={(event) => setTriggerDraftValue('status', event.target.value as TriggerDraft['status'])}>
                  <option value="DISABLED">DISABLED</option>
                  <option value="ENABLED">ENABLED</option>
                  <option value="PAUSED">PAUSED</option>
                </select>
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
              <button className="btn btn-primary btn-sm" type="submit" disabled={!canManage || !selectedPlanId || triggerActionState.loading}>
                <Webhook size={15} />
                新增
              </button>
            </form>
            {triggerActionState.error && <div className="document-state-line error">{triggerActionState.error}</div>}
            {triggerActionState.success && <div className="document-state-line success">{triggerActionState.success}</div>}
            {lastTriggerDryRun && (
              <div className="execution-sync-summary">
                <span>{lastTriggerDryRun.valid ? 'VALID' : 'INVALID'}</span>
                <span>{lastTriggerDryRun.globalEnabled ? 'global on' : 'global off'}</span>
                <span>runCreated {lastTriggerDryRun.runCreated ? 'yes' : 'no'}</span>
              </div>
            )}
            <div className="execution-trigger-list">
              {triggers.length ? triggers.map((trigger) => (
                <div className="execution-trigger-item" key={trigger.id}>
                  <div>
                    <strong>{trigger.triggerType}</strong>
                    <span>{trigger.status} · secret {trigger.secretRefConfigured ? 'configured' : 'missing'}</span>
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
                      事件
                    </button>
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onDryRunTrigger(trigger)}>
                      <ShieldCheck size={15} />
                      Dry run
                    </button>
                    <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onToggleTrigger(trigger)} disabled={!canManage}>
                      {trigger.status === 'ENABLED' ? <PauseCircle size={15} /> : <Play size={15} />}
                      {trigger.status === 'ENABLED' ? '暂停' : '启用'}
                    </button>
                  </div>
                </div>
              )) : (
                <div className="table-empty">暂无触发配置</div>
              )}
            </div>
            <div className="execution-event-list">
              {triggerEvents.length ? triggerEvents.map((event) => (
                <div className="execution-event-item" key={event.id}>
                  <span>{event.status} · {event.sourceEventId}</span>
                  <small className="mono">{event.traceId ?? shortId(event.requestDigest)}{event.runId ? ` · ${shortId(event.runId)}` : ''}</small>
                </div>
              )) : <div className="table-empty">暂无触发事件</div>}
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
      nodes: current.nodes.map((node, nodeIndex) => nodeIndex === index ? { ...node, [key]: value } : node)
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

  function resetPlanDraft() {
    setPlanDraft(initialExecutionPlanDraft);
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
        <span className="metric-label">{props.label}</span>
      </div>
    </div>
  );
}

function PolicyItem(props: { label: string; value: string }) {
  return (
    <div className="execution-policy-item">
      <span>{props.label}</span>
      <strong>{props.value}</strong>
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

function StatusBadge(props: { status: string }) {
  const status = props.status || 'UNKNOWN';
  const tone = ['FAILED', 'TIMEOUT', 'BLOCKED', 'CANCELED', 'REJECTED'].includes(status) ? 'danger'
    : ['READY', 'RUNNING', 'QUEUED', 'ENABLED', 'ACCEPTED', 'SUCCEEDED', 'API_TEST'].includes(status) ? 'success'
      : status === 'PARTIAL_SUCCESS' || status === 'PAUSED' || status === 'REPORT_HANDOFF' ? 'warning'
        : 'neutral';
  return <span className={`status-badge ${tone}`}>{status}</span>;
}

function activeRunStatus(status: string) {
  return status === 'QUEUED' || status === 'RUNNING';
}

function retryableRunStatus(status: string) {
  return status === 'FAILED' || status === 'TIMEOUT' || status === 'PARTIAL_SUCCESS';
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
  if (!entries.length) return 'input summary empty';
  return entries.map(([key, entryValue]) => `${key}=${String(entryValue)}`).join(' · ');
}
