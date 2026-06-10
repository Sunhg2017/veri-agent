import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Download,
  Eye,
  FileDiff,
  KeyRound,
  PlayCircle,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  ServerCog,
  ShieldCheck,
  SlidersHorizontal,
  ToggleLeft,
  ToggleRight,
  XCircle,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  INVOCATION_STATUSES,
  MODEL_POLICY_SCOPE_TYPES,
  MODEL_PROVIDER_TYPES,
  PROMPT_STATUSES,
  activatePromptVersion,
  approvePromptVersion,
  checkModelProvider,
  createModelProvider,
  createPromptVersion,
  disableModelProvider,
  enableModelProvider,
  exportInvocationsCsv,
  fetchEffectiveModelAccessPolicy,
  fetchCostAlerts,
  fetchCostReport,
  fetchInvocationSummary,
  fetchInvocations,
  fetchModelAccessHealth,
  fetchModelAccessPolicies,
  fetchModelProviders,
  fetchPrompts,
  fetchProviderResilience,
  invocationExportPath,
  rejectPromptVersion,
  resetProviderCircuit,
  upsertModelAccessPolicy,
  updateModelProvider,
  type CostAlert,
  type CostReport,
  type InvocationFilters,
  type InvocationList,
  type InvocationSummary,
  type ModelAccessEffectivePolicy,
  type ModelAccessHealth,
  type ModelAccessPolicy,
  type ModelAccessPolicyPayload,
  type ModelProviderConfig,
  type ModelProviderPayload,
  type PromptTemplate,
  type ProviderCheckResponse,
  type ProviderResilienceResponse
} from '../api/modelAccess';
import { canUseButton, hasPermission } from '../permissions';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type TabKey = 'providers' | 'prompts' | 'policies' | 'logs';

type ProviderDraft = {
  name: string;
  providerType: string;
  routingGroup: string;
  capabilities: string;
  baseUrl: string;
  apiKeyRef: string;
  priority: string;
  timeoutMs: string;
  inputCostPer1kTokens: string;
  outputCostPer1kTokens: string;
};

type PromptDraft = {
  promptKey: string;
  name: string;
  content: string;
  changeNote: string;
  highRisk: boolean;
  activate: boolean;
};

type LogFilterDraft = {
  projectId: string;
  applicationId: string;
  environmentId: string;
  sensitivityLevel: string;
  status: string;
  providerId: string;
  actorService: string;
  roleScope: string;
  startTime: string;
  endTime: string;
};

type CostFilterDraft = {
  projectId: string;
  actorService: string;
  startDate: string;
  endDate: string;
};

type PolicyDraft = {
  scopeType: string;
  scopeKey: string;
  enabled: boolean;
  modelInvocationEnabled: 'INHERIT' | 'ENABLED' | 'DISABLED';
  publicModelAllowed: 'INHERIT' | 'ENABLED' | 'DISABLED';
  dailyBudgetLimit: string;
  costAlertWarningRatio: string;
  budgetOverrunAction: '' | 'BLOCK' | 'FALLBACK';
  routingGroup: string;
  reason: string;
};

type PolicyPreviewDraft = {
  projectId: string;
  environmentId: string;
  roles: string;
};

type DiffRow = {
  type: 'same' | 'added' | 'removed' | 'changed';
  lineNo: number;
  left: string;
  right: string;
};

const initialProviderDraft: ProviderDraft = {
  name: '',
  providerType: 'LOCAL_ECHO',
  routingGroup: 'default',
  capabilities: 'CHAT,TEXT,JSON,REQUIREMENT_PARSE',
  baseUrl: '',
  apiKeyRef: '',
  priority: '100',
  timeoutMs: '10000',
  inputCostPer1kTokens: '0',
  outputCostPer1kTokens: '0'
};

const initialPromptDraft: PromptDraft = {
  promptKey: '',
  name: '',
  content: '',
  changeNote: '',
  highRisk: false,
  activate: false
};

const initialLogFilters: LogFilterDraft = {
  projectId: '',
  applicationId: '',
  environmentId: '',
  sensitivityLevel: '',
  status: '',
  providerId: '',
  actorService: '',
  roleScope: '',
  startTime: '',
  endTime: ''
};

const initialCostFilters: CostFilterDraft = {
  projectId: '',
  actorService: '',
  startDate: '',
  endDate: ''
};

const initialPolicyDraft: PolicyDraft = {
  scopeType: 'PLATFORM',
  scopeKey: 'GLOBAL',
  enabled: true,
  modelInvocationEnabled: 'INHERIT',
  publicModelAllowed: 'INHERIT',
  dailyBudgetLimit: '',
  costAlertWarningRatio: '',
  budgetOverrunAction: '',
  routingGroup: '',
  reason: ''
};

const initialPolicyPreviewDraft: PolicyPreviewDraft = {
  projectId: '',
  environmentId: '',
  roles: ''
};

const tabs: Array<{ key: TabKey; label: string; icon: LucideIcon }> = [
  { key: 'providers', label: '供应商', icon: ServerCog },
  { key: 'prompts', label: 'Prompt', icon: FileDiff },
  { key: 'policies', label: '策略', icon: SlidersHorizontal },
  { key: 'logs', label: '日志与成本', icon: Activity }
];

export function ModelAccessConsole(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'modelAccess:read');
  const canManageProviders = canUseButton(props.currentUser, 'modelAccess:provider_manage');
  const canManagePrompts = canUseButton(props.currentUser, 'modelAccess:prompt_manage');
  const canManagePolicies = canUseButton(props.currentUser, 'modelAccess:policy_manage');
  const canExport = canUseButton(props.currentUser, 'modelAccess:export');

  const [activeTab, setActiveTab] = useState<TabKey>('providers');
  const [health, setHealth] = useState<ModelAccessHealth | null>(null);
  const [providers, setProviders] = useState<ModelProviderConfig[]>([]);
  const [prompts, setPrompts] = useState<PromptTemplate[]>([]);
  const [policies, setPolicies] = useState<ModelAccessPolicy[]>([]);
  const [effectivePolicy, setEffectivePolicy] = useState<ModelAccessEffectivePolicy | null>(null);
  const [invocations, setInvocations] = useState<InvocationList>({ items: [], total: 0 });
  const [summary, setSummary] = useState<InvocationSummary | null>(null);
  const [costReport, setCostReport] = useState<CostReport>({ rows: [] });
  const [alerts, setAlerts] = useState<CostAlert[]>([]);
  const [providerDraft, setProviderDraft] = useState<ProviderDraft>(initialProviderDraft);
  const [editingProviderId, setEditingProviderId] = useState<string | null>(null);
  const [promptDraft, setPromptDraft] = useState<PromptDraft>(initialPromptDraft);
  const [policyDraft, setPolicyDraft] = useState<PolicyDraft>(initialPolicyDraft);
  const [policyPreviewDraft, setPolicyPreviewDraft] = useState<PolicyPreviewDraft>(initialPolicyPreviewDraft);
  const [promptFilter, setPromptFilter] = useState('');
  const [leftPromptId, setLeftPromptId] = useState('');
  const [rightPromptId, setRightPromptId] = useState('');
  const [logFilters, setLogFilters] = useState<LogFilterDraft>(initialLogFilters);
  const [costFilters, setCostFilters] = useState<CostFilterDraft>(initialCostFilters);
  const [providerChecks, setProviderChecks] = useState<Record<string, ProviderCheckResponse>>({});
  const [providerResilience, setProviderResilience] = useState<Record<string, ProviderResilienceResponse>>({});
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [providerState, setProviderState] = useState<WorkState>({ loading: false });
  const [promptState, setPromptState] = useState<WorkState>({ loading: false });
  const [policyState, setPolicyState] = useState<WorkState>({ loading: false });
  const [logState, setLogState] = useState<WorkState>({ loading: false });
  const [exportState, setExportState] = useState<WorkState>({ loading: false });

  const invocationFilters = useMemo(() => buildInvocationFilters(logFilters), [logFilters]);

  const refreshPrompts = useCallback(async () => {
    const response = await fetchPrompts(promptFilter);
    setPrompts(response.data);
    return response.trace_id;
  }, [promptFilter]);

  const refreshPolicies = useCallback(async () => {
    const [policiesResponse, effectiveResponse] = await Promise.all([
      fetchModelAccessPolicies(),
      fetchEffectiveModelAccessPolicy(compactPolicyPreviewFilters(policyPreviewDraft))
    ]);
    setPolicies(policiesResponse.data);
    setEffectivePolicy(effectiveResponse.data);
    return policiesResponse.trace_id || effectiveResponse.trace_id;
  }, [policyPreviewDraft]);

  const refreshLogs = useCallback(async () => {
    const [invocationResponse, summaryResponse, reportResponse, alertResponse] = await Promise.all([
      fetchInvocations({ ...invocationFilters, index: 0, size: 50 }),
      fetchInvocationSummary(invocationFilters),
      fetchCostReport(compactCostFilters(costFilters)),
      fetchCostAlerts(compactCostAlertFilters(costFilters))
    ]);
    setInvocations(invocationResponse.data);
    setSummary(summaryResponse.data);
    setCostReport(reportResponse.data);
    setAlerts(alertResponse.data);
    return invocationResponse.trace_id || summaryResponse.trace_id || reportResponse.trace_id || alertResponse.trace_id;
  }, [costFilters, invocationFilters]);

  const refreshAll = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setProviders([]);
      setPrompts([]);
      setPolicies([]);
      setEffectivePolicy(null);
      setInvocations({ items: [], total: 0 });
      setSummary(null);
      setCostReport({ rows: [] });
      setAlerts([]);
      setLoadState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, providersResult, promptsResult, policiesResult, logsResult] = await Promise.allSettled([
      fetchModelAccessHealth(),
      fetchModelProviders(),
      refreshPrompts(),
      refreshPolicies(),
      refreshLogs()
    ]);
    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, '模型接入健康检查失败'));
    }

    if (providersResult.status === 'fulfilled') {
      setProviders(providersResult.value.data);
      traceIds.push(providersResult.value.trace_id);
    } else {
      errors.push(errorMessage(providersResult.reason, '供应商列表加载失败'));
    }

    if (promptsResult.status === 'fulfilled') {
      traceIds.push(promptsResult.value);
    } else {
      errors.push(errorMessage(promptsResult.reason, 'Prompt 列表加载失败'));
    }

    if (policiesResult.status === 'fulfilled') {
      traceIds.push(policiesResult.value);
    } else {
      errors.push(errorMessage(policiesResult.reason, '策略列表加载失败'));
    }

    if (logsResult.status === 'fulfilled') {
      traceIds.push(logsResult.value);
    } else {
      errors.push(errorMessage(logsResult.reason, '调用日志加载失败'));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canRead, props.signedIn, refreshLogs, refreshPolicies, refreshPrompts]);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  const promptKeys = useMemo(() => {
    return Array.from(new Set(prompts.map((prompt) => prompt.promptKey).filter(Boolean))).sort();
  }, [prompts]);

  const selectedPromptKey = promptFilter || promptKeys[0] || '';
  const selectedPromptVersions = useMemo(() => {
    return prompts
      .filter((prompt) => !selectedPromptKey || prompt.promptKey === selectedPromptKey)
      .sort((a, b) => b.version - a.version);
  }, [prompts, selectedPromptKey]);

  const diffPrompts = useMemo(() => {
    const left = prompts.find((prompt) => prompt.id === leftPromptId) ?? selectedPromptVersions[1] ?? selectedPromptVersions[0];
    const right = prompts.find((prompt) => prompt.id === rightPromptId) ?? selectedPromptVersions[0];
    return { left, right, rows: buildDiffRows(left?.content ?? '', right?.content ?? '') };
  }, [leftPromptId, prompts, rightPromptId, selectedPromptVersions]);

  async function onSubmitProvider(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManageProviders) {
      setProviderState({ loading: false, error: '当前账号无模型供应商管理权限' });
      return;
    }
    const validation = validateProviderDraft(providerDraft);
    if (validation) {
      setProviderState({ loading: false, error: validation });
      return;
    }
    setProviderState({ loading: true });
    try {
      const payload = providerPayload(providerDraft, !editingProviderId);
      const response = editingProviderId
        ? await updateModelProvider(editingProviderId, payload)
        : await createModelProvider(payload);
      const providersResponse = await fetchModelProviders();
      setProviders(providersResponse.data);
      setProviderDraft(initialProviderDraft);
      setEditingProviderId(null);
      setProviderState({
        loading: false,
        success: editingProviderId ? '供应商已保存' : '供应商已创建',
        traceId: providersResponse.trace_id || response.trace_id
      });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, '供应商保存失败'), traceId: traceId(error) });
    }
  }

  async function onToggleProvider(provider: ModelProviderConfig) {
    if (!canManageProviders) return;
    setProviderState({ loading: true });
    try {
      const response = provider.status === 'ENABLED'
        ? await disableModelProvider(provider.id)
        : await enableModelProvider(provider.id);
      const providersResponse = await fetchModelProviders();
      setProviders(providersResponse.data);
      setProviderState({ loading: false, success: `供应商已${response.data.status === 'ENABLED' ? '启用' : '停用'}`, traceId: response.trace_id });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, '供应商状态切换失败'), traceId: traceId(error) });
    }
  }

  async function onCheckProvider(provider: ModelProviderConfig) {
    if (!canManageProviders) return;
    setProviderState({ loading: true });
    try {
      const [checkResponse, resilienceResponse] = await Promise.all([
        checkModelProvider(provider.id),
        fetchProviderResilience(provider.id)
      ]);
      setProviderChecks((current) => ({ ...current, [provider.id]: checkResponse.data }));
      setProviderResilience((current) => ({ ...current, [provider.id]: resilienceResponse.data }));
      setProviderState({ loading: false, success: `${provider.name} 就绪检查完成`, traceId: checkResponse.trace_id });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, '就绪检查失败'), traceId: traceId(error) });
    }
  }

  async function onResetCircuit(provider: ModelProviderConfig) {
    if (!canManageProviders) return;
    setProviderState({ loading: true });
    try {
      const response = await resetProviderCircuit(provider.id);
      setProviderResilience((current) => ({ ...current, [provider.id]: response.data }));
      setProviderState({ loading: false, success: `${provider.name} 熔断状态已恢复`, traceId: response.trace_id });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, '熔断恢复失败'), traceId: traceId(error) });
    }
  }

  async function onSubmitPrompt(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManagePrompts) {
      setPromptState({ loading: false, error: '当前账号无 Prompt 管理权限' });
      return;
    }
    if (!promptDraft.promptKey.trim() || !promptDraft.name.trim() || !promptDraft.content.trim()) {
      setPromptState({ loading: false, error: 'Prompt key、名称和内容必填' });
      return;
    }
    if (promptDraft.content.length > 12000) {
      setPromptState({ loading: false, error: 'Prompt 内容不能超过 12000 字符' });
      return;
    }
    setPromptState({ loading: true });
    try {
      const response = await createPromptVersion({
        promptKey: promptDraft.promptKey.trim(),
        name: promptDraft.name.trim(),
        content: promptDraft.content,
        changeNote: promptDraft.changeNote.trim(),
        highRisk: promptDraft.highRisk,
        activate: promptDraft.activate
      });
      const traceId = await refreshPrompts();
      setPromptDraft(initialPromptDraft);
      setPromptState({ loading: false, success: `Prompt v${response.data.version} 已创建`, traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, 'Prompt 创建失败'), traceId: traceId(error) });
    }
  }

  async function onActivatePrompt(prompt: PromptTemplate) {
    if (!canManagePrompts) return;
    setPromptState({ loading: true });
    try {
      const response = await activatePromptVersion(prompt.id);
      const traceId = await refreshPrompts();
      setPromptState({ loading: false, success: `${prompt.promptKey} v${prompt.version} 已激活`, traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, 'Prompt 激活失败'), traceId: traceId(error) });
    }
  }

  async function onApprovePrompt(prompt: PromptTemplate) {
    if (!canManagePrompts) return;
    setPromptState({ loading: true });
    try {
      const response = await approvePromptVersion(prompt.id, { reviewNote: prompt.changeNote });
      const traceId = await refreshPrompts();
      setPromptState({ loading: false, success: `${prompt.promptKey} v${prompt.version} 已审批通过`, traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, 'Prompt 审批失败'), traceId: traceId(error) });
    }
  }

  async function onRejectPrompt(prompt: PromptTemplate) {
    if (!canManagePrompts) return;
    setPromptState({ loading: true });
    try {
      const response = await rejectPromptVersion(prompt.id, { reviewNote: prompt.changeNote });
      const traceId = await refreshPrompts();
      setPromptState({ loading: false, success: `${prompt.promptKey} v${prompt.version} 已驳回`, traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, 'Prompt 驳回失败'), traceId: traceId(error) });
    }
  }

  async function onSubmitPolicy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManagePolicies) {
      setPolicyState({ loading: false, error: '当前账号无策略管理权限' });
      return;
    }
    const validation = validatePolicyDraft(policyDraft);
    if (validation) {
      setPolicyState({ loading: false, error: validation });
      return;
    }
    setPolicyState({ loading: true });
    try {
      const response = await upsertModelAccessPolicy(policyPayload(policyDraft));
      const traceId = await refreshPolicies();
      setPolicyState({ loading: false, success: `${response.data.scopeType}:${response.data.scopeKey} 已保存`, traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPolicyState({ loading: false, error: errorMessage(error, '策略保存失败'), traceId: traceId(error) });
    }
  }

  async function onRefreshEffectivePolicy(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setPolicyState({ loading: true });
    try {
      const response = await fetchEffectiveModelAccessPolicy(compactPolicyPreviewFilters(policyPreviewDraft));
      setEffectivePolicy(response.data);
      setPolicyState({ loading: false, success: '有效策略已刷新', traceId: response.trace_id });
    } catch (error: unknown) {
      setPolicyState({ loading: false, error: errorMessage(error, '有效策略刷新失败'), traceId: traceId(error) });
    }
  }

  async function onApplyLogFilters(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setLogState({ loading: true });
    try {
      const traceId = await refreshLogs();
      setLogState({ loading: false, success: '日志与成本已刷新', traceId });
    } catch (error: unknown) {
      setLogState({ loading: false, error: errorMessage(error, '日志与成本刷新失败'), traceId: traceId(error) });
    }
  }

  async function onExportInvocations() {
    if (!canExport) {
      setExportState({ loading: false, error: '当前账号无调用日志导出权限' });
      return;
    }
    setExportState({ loading: true });
    try {
      const response = await exportInvocationsCsv(invocationFilters);
      downloadText(response.text, response.filename ?? 'wp2-invocations.csv', response.contentType || 'text/csv;charset=UTF-8');
      setExportState({ loading: false, success: `CSV 已生成：${invocationExportPath(invocationFilters)}`, traceId: response.traceId });
    } catch (error: unknown) {
      setExportState({ loading: false, error: errorMessage(error, 'CSV 导出失败'), traceId: traceId(error) });
    }
  }

  function editProvider(provider: ModelProviderConfig) {
    setEditingProviderId(provider.id);
    setProviderDraft({
      name: provider.name,
      providerType: provider.providerType,
      routingGroup: provider.routingGroup,
      capabilities: provider.capabilities,
      baseUrl: provider.baseUrl ?? '',
      apiKeyRef: provider.apiKeyRef ?? '',
      priority: String(provider.priority),
      timeoutMs: String(provider.timeoutMs),
      inputCostPer1kTokens: String(provider.inputCostPer1kTokens),
      outputCostPer1kTokens: String(provider.outputCostPer1kTokens)
    });
    setActiveTab('providers');
  }

  function editPolicy(policy: ModelAccessPolicy) {
    setPolicyDraft({
      scopeType: String(policy.scopeType),
      scopeKey: policy.scopeKey,
      enabled: policy.enabled,
      modelInvocationEnabled: triStateFromBoolean(policy.modelInvocationEnabled),
      publicModelAllowed: triStateFromBoolean(policy.publicModelAllowed),
      dailyBudgetLimit: policy.dailyBudgetLimit === undefined ? '' : String(policy.dailyBudgetLimit),
      costAlertWarningRatio: policy.costAlertWarningRatio === undefined ? '' : String(policy.costAlertWarningRatio),
      budgetOverrunAction: policy.budgetOverrunAction === undefined ? '' : policy.budgetOverrunAction === 'FALLBACK' ? 'FALLBACK' : 'BLOCK',
      routingGroup: policy.routingGroup ?? '',
      reason: policy.reason ?? ''
    });
    setActiveTab('policies');
    setPolicyState({ loading: false });
  }

  if (!props.signedIn || !canRead) {
    return (
      <div className="panel module-panel">
        <div className="empty-state compact">
          <ShieldCheck size={18} />
          <div>
            <strong>暂无访问权限</strong>
            <span>需要 modelAccess:read。</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="model-access-layout">
      <section className="model-access-main">
        <div className="panel module-panel model-access-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <span className="section-icon"><ServerCog size={18} /></span>
              <div>
                <span className="eyebrow">WP2</span>
                <h2>模型接入管理</h2>
              </div>
            </div>
            <div className="panel-toolbar-actions">
              <button className="icon-button" type="button" onClick={() => void refreshAll()} disabled={loadState.loading} title="刷新">
                <RefreshCw size={16} />
              </button>
            </div>
          </div>

          <div className="asset-tab-strip">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  className={`asset-tab ${activeTab === tab.key ? 'active' : ''}`}
                  type="button"
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                >
                  <Icon size={14} />
                  {tab.label}
                </button>
              );
            })}
          </div>

          <StateLine state={loadState} />

          {activeTab === 'providers' && (
            <ProviderTab
              canManage={canManageProviders}
              checks={providerChecks}
              draft={providerDraft}
              editingProviderId={editingProviderId}
              providers={providers}
              resilience={providerResilience}
              state={providerState}
              onCancelEdit={() => {
                setEditingProviderId(null);
                setProviderDraft(initialProviderDraft);
                setProviderState({ loading: false });
              }}
              onChangeDraft={(key, value) => {
                setProviderDraft((current) => ({ ...current, [key]: value }));
                setProviderState({ loading: false });
              }}
              onCheck={onCheckProvider}
              onEdit={editProvider}
              onResetCircuit={onResetCircuit}
              onSubmit={onSubmitProvider}
              onToggle={onToggleProvider}
            />
          )}

          {activeTab === 'prompts' && (
            <PromptTab
              canManage={canManagePrompts}
              diffPrompts={diffPrompts}
              draft={promptDraft}
              leftPromptId={leftPromptId}
              promptFilter={promptFilter}
              promptKeys={promptKeys}
              prompts={prompts}
              rightPromptId={rightPromptId}
              selectedPromptKey={selectedPromptKey}
              selectedPromptVersions={selectedPromptVersions}
              state={promptState}
              onActivate={onActivatePrompt}
              onApprove={onApprovePrompt}
              onChangeDraft={(key, value) => {
                setPromptDraft((current) => ({ ...current, [key]: value }));
                setPromptState({ loading: false });
              }}
              onFilter={async (value) => {
                setPromptFilter(value);
                setLeftPromptId('');
                setRightPromptId('');
                setPromptState({ loading: true });
                try {
                  const response = await fetchPrompts(value);
                  setPrompts(response.data);
                  setPromptState({ loading: false, traceId: response.trace_id });
                } catch (error: unknown) {
                  setPromptState({ loading: false, error: errorMessage(error, 'Prompt 查询失败'), traceId: traceId(error) });
                }
              }}
              onLeftPromptChange={setLeftPromptId}
              onReject={onRejectPrompt}
              onRightPromptChange={setRightPromptId}
              onSubmit={onSubmitPrompt}
            />
          )}

          {activeTab === 'policies' && (
            <PolicyTab
              canManage={canManagePolicies}
              draft={policyDraft}
              effectivePolicy={effectivePolicy}
              policies={policies}
              previewDraft={policyPreviewDraft}
              state={policyState}
              onChangeDraft={(key, value) => {
                const patch: Partial<PolicyDraft> = { [key]: value } as Partial<PolicyDraft>;
                if (key === 'scopeType' && value === 'PLATFORM') {
                  patch.scopeKey = 'GLOBAL';
                }
                setPolicyDraft((current) => ({ ...current, ...patch }));
                setPolicyState({ loading: false });
              }}
              onChangePreview={(key, value) => {
                setPolicyPreviewDraft((current) => ({ ...current, [key]: value }));
                setPolicyState({ loading: false });
              }}
              onEdit={editPolicy}
              onPreview={onRefreshEffectivePolicy}
              onResetDraft={() => {
                setPolicyDraft(initialPolicyDraft);
                setPolicyState({ loading: false });
              }}
              onSubmit={onSubmitPolicy}
            />
          )}

          {activeTab === 'logs' && (
            <LogsTab
              alerts={alerts}
              canExport={canExport}
              costFilters={costFilters}
              costReport={costReport}
              exportState={exportState}
              filters={logFilters}
              invocations={invocations}
              providers={providers}
              state={logState}
              summary={summary}
              onApplyFilters={onApplyLogFilters}
              onChangeCostFilter={(key, value) => {
                setCostFilters((current) => ({ ...current, [key]: value }));
                setLogState({ loading: false });
              }}
              onChangeFilter={(key, value) => {
                setLogFilters((current) => ({ ...current, [key]: value }));
                setLogState({ loading: false });
              }}
              onExport={onExportInvocations}
            />
          )}
        </div>
      </section>

      <aside className="model-access-side">
        <div className="panel detail-panel">
          <div className="panel-title-row">
            <h2>运行状态</h2>
            <Activity size={16} />
          </div>
          <div className="document-health-grid">
            <StatusMetric label="服务" value={health?.status ?? 'UNKNOWN'} tone={health?.status === 'UP' ? 'positive' : 'negative'} />
            <StatusMetric label="启用供应商" value={health?.enabledProviders ?? providers.filter((item) => item.status === 'ENABLED').length} />
            <StatusMetric label="Active Prompt" value={health?.activePrompts ?? prompts.filter((item) => item.status === 'ACTIVE').length} />
            <StatusMetric label="打开熔断" value={health?.openCircuitProviders ?? 0} tone={(health?.openCircuitProviders ?? 0) > 0 ? 'negative' : 'positive'} />
            <StatusMetric label="限流" value={health?.providerRateLimitEnabled ? health.providerRateLimitMaxRequests : 'OFF'} />
            <StatusMetric label="并发" value={health?.providerConcurrencyLimitEnabled ? health.providerMaxConcurrentRequests : 'OFF'} />
          </div>
        </div>

        <div className="panel detail-panel">
          <div className="panel-title-row">
            <h2>权限</h2>
            <KeyRound size={16} />
          </div>
          <div className="model-access-permission-list">
            <PermissionFlag label="读取" enabled={canRead} />
            <PermissionFlag label="管理供应商" enabled={canManageProviders} />
            <PermissionFlag label="管理 Prompt" enabled={canManagePrompts} />
            <PermissionFlag label="管理策略" enabled={canManagePolicies} />
            <PermissionFlag label="导出日志" enabled={canExport} />
          </div>
        </div>
      </aside>
    </div>
  );
}

function ProviderTab(props: {
  canManage: boolean;
  checks: Record<string, ProviderCheckResponse>;
  draft: ProviderDraft;
  editingProviderId: string | null;
  providers: ModelProviderConfig[];
  resilience: Record<string, ProviderResilienceResponse>;
  state: WorkState;
  onCancelEdit: () => void;
  onChangeDraft: (key: keyof ProviderDraft, value: string) => void;
  onCheck: (provider: ModelProviderConfig) => Promise<void>;
  onEdit: (provider: ModelProviderConfig) => void;
  onResetCircuit: (provider: ModelProviderConfig) => Promise<void>;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onToggle: (provider: ModelProviderConfig) => Promise<void>;
}) {
  return (
    <div className="model-access-section">
      <form className="model-access-form" onSubmit={props.onSubmit}>
        <div className="document-form-grid model-access-provider-grid">
          <label className="field">
            <span>名称<b>*</b></span>
            <input value={props.draft.name} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('name', event.target.value)} />
          </label>
          <label className="field">
            <span>类型<b>*</b></span>
            <select value={props.draft.providerType} disabled={!props.canManage || Boolean(props.editingProviderId)} onChange={(event) => props.onChangeDraft('providerType', event.target.value)}>
              {MODEL_PROVIDER_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="field">
            <span>路由组</span>
            <input value={props.draft.routingGroup} placeholder="default" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('routingGroup', event.target.value)} />
          </label>
          <label className="field">
            <span>能力</span>
            <input value={props.draft.capabilities} placeholder="CHAT,TEXT,JSON" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('capabilities', event.target.value)} />
          </label>
          <label className="field">
            <span>Base URL</span>
            <input value={props.draft.baseUrl} placeholder="https://api.example.com" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('baseUrl', event.target.value)} />
          </label>
          <label className="field">
            <span>SecretRef / apiKeyRef</span>
            <input value={props.draft.apiKeyRef} placeholder="env:MODEL_API_KEY" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('apiKeyRef', event.target.value)} />
            <small>仅保存引用值。</small>
          </label>
          <label className="field">
            <span>优先级</span>
            <input type="number" min="0" value={props.draft.priority} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('priority', event.target.value)} />
          </label>
          <label className="field">
            <span>超时 ms</span>
            <input type="number" min="100" value={props.draft.timeoutMs} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('timeoutMs', event.target.value)} />
          </label>
          <label className="field">
            <span>输入成本 / 1k</span>
            <input type="number" min="0" step="0.0001" value={props.draft.inputCostPer1kTokens} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('inputCostPer1kTokens', event.target.value)} />
          </label>
          <label className="field">
            <span>输出成本 / 1k</span>
            <input type="number" min="0" step="0.0001" value={props.draft.outputCostPer1kTokens} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('outputCostPer1kTokens', event.target.value)} />
          </label>
        </div>
        <div className="document-actions">
          <button className="primary-button" type="submit" disabled={!props.canManage || props.state.loading}>
            {props.editingProviderId ? <Save size={16} /> : <Plus size={16} />}
            {props.editingProviderId ? '保存供应商' : '创建供应商'}
          </button>
          {props.editingProviderId && (
            <button className="secondary-button" type="button" onClick={props.onCancelEdit}>取消编辑</button>
          )}
          <StateLine state={props.state} />
        </div>
      </form>

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>供应商</th>
              <th>状态</th>
              <th>路由</th>
              <th>SecretRef</th>
              <th>就绪</th>
              <th>熔断</th>
              <th>成本</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {props.providers.length ? props.providers.map((provider) => {
              const check = props.checks[provider.id];
              const resilience = props.resilience[provider.id];
              return (
                <tr key={provider.id}>
                  <td>
                    <span className="table-primary">{provider.name}</span>
                    <span className="table-secondary">{provider.providerType}</span>
                  </td>
                  <td><StatusPill value={provider.status} /></td>
                  <td>
                    <span className="table-primary">P{provider.priority} / {provider.timeoutMs}ms</span>
                    <span className="table-secondary">{provider.routingGroup} · {provider.capabilities}</span>
                  </td>
                  <td><span className="table-secondary">{provider.apiKeyRef ?? '-'}</span></td>
                  <td>
                    {check ? (
                      <>
                        <StatusPill value={check.status} />
                        <span className="table-secondary">{check.cached ? 'cached' : `${check.latencyMs}ms`}</span>
                      </>
                    ) : <span className="table-secondary">-</span>}
                  </td>
                  <td>
                    {resilience ? (
                      <>
                        <StatusPill value={resilience.circuitOpen ? 'OPEN' : 'CLOSED'} />
                        <span className="table-secondary">fail {resilience.consecutiveFailures}</span>
                      </>
                    ) : <span className="table-secondary">-</span>}
                  </td>
                  <td>
                    <span className="table-primary">{formatMoney(provider.inputCostPer1kTokens)} / {formatMoney(provider.outputCostPer1kTokens)}</span>
                    <span className="table-secondary">input / output</span>
                  </td>
                  <td>
                    <div className="model-access-row-actions">
                      <button className="mini-button icon-only" type="button" title="编辑" disabled={!props.canManage} onClick={() => props.onEdit(provider)}><Save size={14} /></button>
                      <button className="mini-button icon-only" type="button" title={provider.status === 'ENABLED' ? '停用' : '启用'} disabled={!props.canManage} onClick={() => void props.onToggle(provider)}>
                        {provider.status === 'ENABLED' ? <ToggleLeft size={14} /> : <ToggleRight size={14} />}
                      </button>
                      <button className="mini-button icon-only" type="button" title="就绪检查" disabled={!props.canManage} onClick={() => void props.onCheck(provider)}><PlayCircle size={14} /></button>
                      <button className="mini-button icon-only" type="button" title="恢复熔断" disabled={!props.canManage} onClick={() => void props.onResetCircuit(provider)}><RotateCcw size={14} /></button>
                    </div>
                  </td>
                </tr>
              );
            }) : (
              <tr><td className="table-empty" colSpan={8}>暂无供应商</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function PolicyTab(props: {
  canManage: boolean;
  draft: PolicyDraft;
  effectivePolicy: ModelAccessEffectivePolicy | null;
  policies: ModelAccessPolicy[];
  previewDraft: PolicyPreviewDraft;
  state: WorkState;
  onChangeDraft: (key: keyof PolicyDraft, value: string | boolean) => void;
  onChangePreview: (key: keyof PolicyPreviewDraft, value: string) => void;
  onEdit: (policy: ModelAccessPolicy) => void;
  onPreview: (event?: FormEvent<HTMLFormElement>) => Promise<void>;
  onResetDraft: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div className="model-access-section">
      <div className="model-access-policy-grid">
        <form className="model-access-form" onSubmit={props.onSubmit}>
          <div className="document-form-grid model-access-policy-form-grid">
            <label className="field">
              <span>作用域<b>*</b></span>
              <select value={props.draft.scopeType} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('scopeType', event.target.value)}>
                {MODEL_POLICY_SCOPE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
              </select>
            </label>
            <label className="field">
              <span>Scope key<b>*</b></span>
              <input value={props.draft.scopeKey} disabled={!props.canManage || props.draft.scopeType === 'PLATFORM'} onChange={(event) => props.onChangeDraft('scopeKey', event.target.value)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>启用策略</span>
              <input type="checkbox" checked={props.draft.enabled} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('enabled', event.target.checked)} />
            </label>
            <label className="field">
              <span>模型调用</span>
              <select value={props.draft.modelInvocationEnabled} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('modelInvocationEnabled', event.target.value)}>
                <option value="INHERIT">继承</option>
                <option value="ENABLED">允许</option>
                <option value="DISABLED">关闭</option>
              </select>
            </label>
            <label className="field">
              <span>公开模型</span>
              <select value={props.draft.publicModelAllowed} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('publicModelAllowed', event.target.value)}>
                <option value="INHERIT">继承</option>
                <option value="ENABLED">允许</option>
                <option value="DISABLED">禁止</option>
              </select>
            </label>
            <label className="field">
              <span>日预算</span>
              <input type="number" min="0" step="0.00000001" value={props.draft.dailyBudgetLimit} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('dailyBudgetLimit', event.target.value)} />
            </label>
            <label className="field">
              <span>告警比例</span>
              <input type="number" min="0.01" max="1" step="0.01" value={props.draft.costAlertWarningRatio} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('costAlertWarningRatio', event.target.value)} />
            </label>
            <label className="field">
              <span>超预算</span>
              <select value={props.draft.budgetOverrunAction} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('budgetOverrunAction', event.target.value)}>
                <option value="">继承</option>
                <option value="BLOCK">阻断</option>
                <option value="FALLBACK">降级</option>
              </select>
            </label>
            <label className="field">
              <span>路由组</span>
              <input value={props.draft.routingGroup} placeholder="default/private" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('routingGroup', event.target.value)} />
            </label>
            <label className="field model-access-policy-reason">
              <span>备注</span>
              <input value={props.draft.reason} maxLength={300} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('reason', event.target.value)} />
            </label>
          </div>
          <div className="document-actions">
            <button className="primary-button" type="submit" disabled={!props.canManage || props.state.loading}>
              <Save size={16} /> 保存策略
            </button>
            <button className="secondary-button" type="button" disabled={!props.canManage} onClick={props.onResetDraft}>重置</button>
            <StateLine state={props.state} />
          </div>
        </form>

        <form className="model-access-effective-panel" onSubmit={(event) => void props.onPreview(event)}>
          <div className="panel-title-row">
            <h2>有效策略</h2>
            <Eye size={16} />
          </div>
          <div className="document-form-grid model-access-effective-grid">
            <label className="field"><span>Project ID</span><input value={props.previewDraft.projectId} onChange={(event) => props.onChangePreview('projectId', event.target.value)} /></label>
            <label className="field"><span>Environment ID</span><input value={props.previewDraft.environmentId} onChange={(event) => props.onChangePreview('environmentId', event.target.value)} /></label>
            <label className="field"><span>角色</span><input value={props.previewDraft.roles} placeholder="SuperAdmin,Auditor" onChange={(event) => props.onChangePreview('roles', event.target.value)} /></label>
          </div>
          <div className="document-actions">
            <button className="secondary-button" type="submit" disabled={props.state.loading}><RefreshCw size={15} /> 刷新预览</button>
          </div>
          <div className="model-access-summary-grid">
            <StatusMetric label="调用" value={props.effectivePolicy?.modelInvocationEnabled ? 'ON' : 'OFF'} tone={props.effectivePolicy?.modelInvocationEnabled ? 'positive' : 'negative'} />
            <StatusMetric label="公开模型" value={props.effectivePolicy?.publicModelAllowed ? 'ON' : 'OFF'} tone={props.effectivePolicy?.publicModelAllowed ? 'positive' : 'pending'} />
            <StatusMetric label="预算" value={props.effectivePolicy?.dailyBudgetLimit === undefined ? '-' : formatMoney(props.effectivePolicy.dailyBudgetLimit)} />
            <StatusMetric label="动作" value={props.effectivePolicy?.budgetOverrunAction ?? '-'} />
            <StatusMetric label="路由组" value={props.effectivePolicy?.routingGroup ?? '-'} />
            <StatusMetric label="角色" value={props.effectivePolicy?.roleScope ?? '-'} />
          </div>
          <div className="model-access-policy-match-list">
            {(props.effectivePolicy?.matchedScopes.length ?? 0) > 0
              ? props.effectivePolicy?.matchedScopes.map((scope) => <StatusPill key={scope} value={scope} />)
              : <span className="table-secondary">-</span>}
          </div>
        </form>
      </div>

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>作用域</th>
              <th>开关</th>
              <th>预算</th>
              <th>路由</th>
              <th>备注</th>
              <th>更新</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {props.policies.length ? props.policies.map((policy) => (
              <tr key={`${policy.scopeType}:${policy.scopeKey}`}>
                <td>
                  <span className="table-primary">{policy.scopeType}</span>
                  <span className="table-secondary">{policy.scopeKey}</span>
                </td>
                <td>
                  <StatusPill value={policy.enabled ? 'ENABLED' : 'DISABLED'} />
                  <span className="table-secondary">调用 {formatOptionalBoolean(policy.modelInvocationEnabled)} · 公开 {formatOptionalBoolean(policy.publicModelAllowed)}</span>
                </td>
                <td>
                  <span className="table-primary">{policy.dailyBudgetLimit === undefined ? '-' : formatMoney(policy.dailyBudgetLimit)}</span>
                  <span className="table-secondary">{policy.costAlertWarningRatio === undefined ? '-' : policy.costAlertWarningRatio} · {policy.budgetOverrunAction ?? '-'}</span>
                </td>
                <td><span className="table-secondary">{policy.routingGroup ?? '-'}</span></td>
                <td><span className="table-secondary">{policy.reason ?? '-'}</span></td>
                <td>
                  <span className="table-primary">{policy.updatedBy ?? '-'}</span>
                  <span className="table-secondary">{formatDateTime(policy.updatedAt)}</span>
                </td>
                <td>
                  <button className="mini-button" type="button" disabled={!props.canManage} onClick={() => props.onEdit(policy)}>
                    <Save size={14} /> 编辑
                  </button>
                </td>
              </tr>
            )) : (
              <tr><td className="table-empty" colSpan={7}>暂无策略</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function PromptTab(props: {
  canManage: boolean;
  diffPrompts: { left?: PromptTemplate; right?: PromptTemplate; rows: DiffRow[] };
  draft: PromptDraft;
  leftPromptId: string;
  promptFilter: string;
  promptKeys: string[];
  prompts: PromptTemplate[];
  rightPromptId: string;
  selectedPromptKey: string;
  selectedPromptVersions: PromptTemplate[];
  state: WorkState;
  onActivate: (prompt: PromptTemplate) => Promise<void>;
  onApprove: (prompt: PromptTemplate) => Promise<void>;
  onChangeDraft: (key: keyof PromptDraft, value: string | boolean) => void;
  onFilter: (value: string) => Promise<void>;
  onLeftPromptChange: (value: string) => void;
  onReject: (prompt: PromptTemplate) => Promise<void>;
  onRightPromptChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div className="model-access-section">
      <div className="model-access-prompt-grid">
        <form className="model-access-form" onSubmit={props.onSubmit}>
          <div className="document-form-grid model-access-prompt-form-grid">
            <label className="field">
              <span>Prompt key<b>*</b></span>
              <input value={props.draft.promptKey} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('promptKey', event.target.value)} />
            </label>
            <label className="field">
              <span>名称<b>*</b></span>
              <input value={props.draft.name} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('name', event.target.value)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>激活</span>
              <input type="checkbox" checked={props.draft.activate} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('activate', event.target.checked)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>高风险</span>
              <input type="checkbox" checked={props.draft.highRisk} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('highRisk', event.target.checked)} />
            </label>
          </div>
          <label className="field document-content-field">
            <span>内容<b>*</b></span>
            <textarea value={props.draft.content} maxLength={12000} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('content', event.target.value)} />
            <small>{props.draft.content.length} / 12000</small>
          </label>
          <label className="field">
            <span>变更说明</span>
            <input value={props.draft.changeNote} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('changeNote', event.target.value)} />
          </label>
          <div className="document-actions">
            <button className="primary-button" type="submit" disabled={!props.canManage || props.state.loading}>
              <Plus size={16} /> 新建版本
            </button>
            <StateLine state={props.state} />
          </div>
        </form>

        <div className="model-access-diff-panel">
          <div className="panel-title-row">
            <h2>版本 Diff</h2>
            <FileDiff size={16} />
          </div>
          <div className="model-access-diff-selectors">
            <select value={props.leftPromptId} onChange={(event) => props.onLeftPromptChange(event.target.value)}>
              <option value="">上一版本</option>
              {props.selectedPromptVersions.map((prompt) => <option key={prompt.id} value={prompt.id}>{prompt.promptKey} v{prompt.version}</option>)}
            </select>
            <select value={props.rightPromptId} onChange={(event) => props.onRightPromptChange(event.target.value)}>
              <option value="">最新版本</option>
              {props.selectedPromptVersions.map((prompt) => <option key={prompt.id} value={prompt.id}>{prompt.promptKey} v{prompt.version}</option>)}
            </select>
          </div>
          <div className="model-access-diff-meta">
            <span>{props.diffPrompts.left ? `v${props.diffPrompts.left.version}` : '-'}</span>
            <span>{props.diffPrompts.right ? `v${props.diffPrompts.right.version}` : '-'}</span>
          </div>
          <div className="model-access-diff-view">
            {props.diffPrompts.rows.length ? props.diffPrompts.rows.slice(0, 80).map((row) => (
              <div className={`model-access-diff-row ${row.type}`} key={row.lineNo}>
                <span>{row.lineNo}</span>
                <code>{row.left || row.right || ' '}</code>
              </div>
            )) : <span className="table-secondary">暂无可比较版本</span>}
          </div>
        </div>
      </div>

      <form className="asset-filter-bar model-access-prompt-filter" onSubmit={(event) => {
        event.preventDefault();
        void props.onFilter(props.promptFilter);
      }}>
        <label className="field">
          <span>Prompt key</span>
          <input value={props.promptFilter} list="model-access-prompt-keys" onChange={(event) => void props.onFilter(event.target.value)} />
          <datalist id="model-access-prompt-keys">
            {props.promptKeys.map((key) => <option key={key} value={key} />)}
          </datalist>
        </label>
        <div className="asset-filter-actions">
          <button className="secondary-button" type="submit"><Search size={15} /> 查询</button>
        </div>
      </form>

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>Prompt</th>
              <th>版本</th>
              <th>状态</th>
              <th>审批</th>
              <th>变更</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {props.prompts.length ? props.prompts.map((prompt) => {
              const canReviewPrompt = props.canManage && prompt.highRisk && prompt.status !== 'ACTIVE';
              const canApprovePrompt = canReviewPrompt && prompt.approvalStatus !== 'APPROVED';
              const canRejectPrompt = canReviewPrompt && prompt.approvalStatus !== 'REJECTED';
              const canActivatePrompt = props.canManage && prompt.status !== 'ACTIVE' && (!prompt.highRisk || prompt.approvalStatus === 'APPROVED');
              return (
                <tr key={prompt.id}>
                  <td>
                    <span className="table-primary">{prompt.promptKey}</span>
                    <span className="table-secondary">{prompt.name}</span>
                  </td>
                  <td>v{prompt.version}</td>
                  <td><StatusPill value={prompt.status} /></td>
                  <td>
                    <StatusPill value={prompt.approvalStatus ?? 'NOT_REQUIRED'} />
                    <span className="table-secondary">{prompt.approvedBy ?? (prompt.highRisk ? '待审批' : '-')}</span>
                  </td>
                  <td><span className="table-secondary">{prompt.changeNote ?? '-'}</span></td>
                  <td><span className="table-secondary">{formatDateTime(prompt.updatedAt)}</span></td>
                  <td>
                    <button className="mini-button" type="button" disabled={!canApprovePrompt} onClick={() => void props.onApprove(prompt)}>
                      <CheckCircle2 size={14} /> 通过
                    </button>
                    <button className="mini-button" type="button" disabled={!canRejectPrompt} onClick={() => void props.onReject(prompt)}>
                      <XCircle size={14} /> 驳回
                    </button>
                    <button className="mini-button" type="button" disabled={!canActivatePrompt} onClick={() => void props.onActivate(prompt)}>
                      <CheckCircle2 size={14} /> 激活
                    </button>
                  </td>
                </tr>
              );
            }) : (
              <tr><td className="table-empty" colSpan={7}>暂无 Prompt 版本</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function LogsTab(props: {
  alerts: CostAlert[];
  canExport: boolean;
  costFilters: CostFilterDraft;
  costReport: CostReport;
  exportState: WorkState;
  filters: LogFilterDraft;
  invocations: InvocationList;
  providers: ModelProviderConfig[];
  state: WorkState;
  summary: InvocationSummary | null;
  onApplyFilters: (event?: FormEvent<HTMLFormElement>) => Promise<void>;
  onChangeCostFilter: (key: keyof CostFilterDraft, value: string) => void;
  onChangeFilter: (key: keyof LogFilterDraft, value: string) => void;
  onExport: () => Promise<void>;
}) {
  return (
    <div className="model-access-section">
      <form className="asset-filter-bar model-access-log-filter" onSubmit={(event) => void props.onApplyFilters(event)}>
        <label className="field"><span>Project ID</span><input value={props.filters.projectId} onChange={(event) => props.onChangeFilter('projectId', event.target.value)} /></label>
        <label className="field"><span>Application ID</span><input value={props.filters.applicationId} onChange={(event) => props.onChangeFilter('applicationId', event.target.value)} /></label>
        <label className="field"><span>Environment ID</span><input value={props.filters.environmentId} onChange={(event) => props.onChangeFilter('environmentId', event.target.value)} /></label>
        <label className="field">
          <span>敏感级别</span>
          <select value={props.filters.sensitivityLevel} onChange={(event) => props.onChangeFilter('sensitivityLevel', event.target.value)}>
            <option value="">全部</option>
            <option value="PUBLIC">PUBLIC</option>
            <option value="INTERNAL">INTERNAL</option>
            <option value="CONFIDENTIAL">CONFIDENTIAL</option>
            <option value="RESTRICTED">RESTRICTED</option>
          </select>
        </label>
        <label className="field">
          <span>状态</span>
          <select value={props.filters.status} onChange={(event) => props.onChangeFilter('status', event.target.value)}>
            <option value="">全部</option>
            {INVOCATION_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
          </select>
        </label>
        <label className="field">
          <span>供应商</span>
          <select value={props.filters.providerId} onChange={(event) => props.onChangeFilter('providerId', event.target.value)}>
            <option value="">全部</option>
            {props.providers.map((provider) => <option key={provider.id} value={provider.id}>{provider.name}</option>)}
          </select>
        </label>
        <label className="field"><span>Actor service</span><input value={props.filters.actorService} onChange={(event) => props.onChangeFilter('actorService', event.target.value)} /></label>
        <label className="field"><span>Role scope</span><input value={props.filters.roleScope} onChange={(event) => props.onChangeFilter('roleScope', event.target.value)} /></label>
        <label className="field"><span>成本项目</span><input value={props.costFilters.projectId} onChange={(event) => props.onChangeCostFilter('projectId', event.target.value)} /></label>
        <label className="field"><span>成本服务</span><input value={props.costFilters.actorService} onChange={(event) => props.onChangeCostFilter('actorService', event.target.value)} /></label>
        <label className="field"><span>开始时间</span><input type="datetime-local" value={props.filters.startTime} onChange={(event) => props.onChangeFilter('startTime', event.target.value)} /></label>
        <label className="field"><span>结束时间</span><input type="datetime-local" value={props.filters.endTime} onChange={(event) => props.onChangeFilter('endTime', event.target.value)} /></label>
        <label className="field"><span>成本开始</span><input type="date" value={props.costFilters.startDate} onChange={(event) => props.onChangeCostFilter('startDate', event.target.value)} /></label>
        <label className="field"><span>成本结束</span><input type="date" value={props.costFilters.endDate} onChange={(event) => props.onChangeCostFilter('endDate', event.target.value)} /></label>
        <div className="asset-filter-actions">
          <button className="secondary-button" type="submit" disabled={props.state.loading}><Search size={15} /> 查询</button>
          <button className="secondary-button" type="button" disabled={!props.canExport || props.exportState.loading} onClick={() => void props.onExport()}><Download size={15} /> CSV</button>
        </div>
      </form>

      <div className="model-access-summary-grid">
        <StatusMetric label="总调用" value={props.summary?.total ?? 0} />
        <StatusMetric label="成功" value={props.summary?.succeeded ?? 0} tone="positive" />
        <StatusMetric label="失败" value={props.summary?.failed ?? 0} tone={(props.summary?.failed ?? 0) > 0 ? 'negative' : 'neutral'} />
        <StatusMetric label="阻断" value={props.summary?.blocked ?? 0} tone={(props.summary?.blocked ?? 0) > 0 ? 'pending' : 'neutral'} />
        <StatusMetric label="Token" value={`${props.summary?.inputTokens ?? 0}/${props.summary?.outputTokens ?? 0}`} />
        <StatusMetric label="成本" value={formatMoney(props.summary?.totalCost ?? 0)} />
      </div>
      <StateLine state={props.state} />
      <StateLine state={props.exportState} />

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>调用</th>
              <th>状态</th>
              <th>Provider</th>
              <th>Prompt</th>
              <th>Preview</th>
              <th>成本</th>
              <th>错误</th>
            </tr>
          </thead>
          <tbody>
            {props.invocations.items.length ? props.invocations.items.map((item) => (
              <tr key={item.id}>
                <td>
                  <span className="table-primary">{item.projectId ?? '-'}</span>
                  <span className="table-secondary">{formatDateTime(item.createdAt)} · {item.actorService ?? '-'} · {item.environmentId ?? '-'}</span>
                </td>
                <td><StatusPill value={item.status} /></td>
                <td>
                  <span className="table-primary">{item.providerName ?? '-'}</span>
                  <span className="table-secondary">{item.modelName ?? '-'} · {item.routingGroup ?? '-'} · {item.modelCapability ?? 'CHAT'}{item.fallbackUsed ? ' · fallback' : ''}</span>
                </td>
                <td>
                  <span className="table-primary">{item.promptKey ?? '-'}</span>
                  <span className="table-secondary">{item.promptVersion ? `v${item.promptVersion}` : '-'} · {item.sensitivityLevel ?? 'INTERNAL'} · {item.roleScope ?? '-'} · {item.routingRuleName ?? '-'}</span>
                </td>
                <td>
                  <span className="table-primary">{item.requestPreview ?? '-'}</span>
                  <span className="table-secondary">{item.promptDigest ?? '-'}</span>
                </td>
                <td>
                  <span className="table-primary">{formatMoney(item.totalCost)}</span>
                  <span className="table-secondary">{item.inputTokens}/{item.outputTokens} tokens · {item.latencyMs}ms</span>
                </td>
                <td><span className="table-secondary">{item.errorCode ? `${item.errorCode}: ${item.errorMessage ?? ''}` : '-'}</span></td>
              </tr>
            )) : (
              <tr><td className="table-empty" colSpan={7}>暂无调用日志</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="model-access-cost-grid">
        <div className="model-access-cost-block">
          <div className="panel-title-row"><h2>成本日报</h2><Activity size={16} /></div>
          <div className="table-wrap model-access-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>项目</th>
                  <th>应用</th>
                  <th>状态</th>
                  <th>Token</th>
                  <th>成本</th>
                </tr>
              </thead>
              <tbody>
                {props.costReport.rows.length ? props.costReport.rows.map((row, index) => (
                  <tr key={`${row.date}-${row.projectId}-${row.applicationId}-${index}`}>
                    <td>{row.date ?? '-'}</td>
                    <td>{row.projectId ?? '-'}</td>
                    <td>{row.applicationId ?? '-'}</td>
                    <td>{row.succeeded}/{row.failed}/{row.blocked}</td>
                    <td>{row.inputTokens}/{row.outputTokens}</td>
                    <td>{formatMoney(row.totalCost)}</td>
                  </tr>
                )) : (
                  <tr><td className="table-empty" colSpan={6}>暂无成本数据</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
        <div className="model-access-cost-block">
          <div className="panel-title-row"><h2>成本告警</h2><AlertTriangle size={16} /></div>
          <div className="model-access-alert-list">
            {props.alerts.length ? props.alerts.map((alert, index) => (
              <div className="model-access-alert" key={`${alert.projectId}-${alert.actorService}-${alert.periodStart}-${index}`}>
                <StatusPill value={alert.level ?? 'INFO'} />
                <strong>{alert.actorService ?? alert.projectId ?? alert.scope ?? '-'}</strong>
                <span>{alert.message ?? `${formatMoney(alert.spentCost)} / ${formatMoney(alert.budgetLimit)}`}</span>
              </div>
            )) : (
              <div className="empty-state compact">
                <CheckCircle2 size={18} />
                <div><strong>暂无告警</strong><span>当前筛选范围未返回成本告警。</span></div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function StatusMetric(props: { label: string; value: string | number; tone?: 'positive' | 'pending' | 'negative' | 'neutral' }) {
  return (
    <div className="status-item">
      <span>{props.label}</span>
      <strong className={props.tone ? `metric-${props.tone}` : undefined}>{props.value}</strong>
    </div>
  );
}

function PermissionFlag(props: { label: string; enabled: boolean }) {
  return (
    <div className="model-access-permission-flag">
      {props.enabled ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
      <span>{props.label}</span>
    </div>
  );
}

function StatusPill(props: { value?: string }) {
  const value = props.value ?? 'UNKNOWN';
  const positive = ['ENABLED', 'ACTIVE', 'UP', 'SUCCEEDED', 'CLOSED', 'OK', 'INFO'].includes(value);
  const negative = ['DISABLED', 'DOWN', 'FAILED', 'OPEN', 'ERROR', 'CRITICAL'].includes(value);
  const pending = ['DRAFT', 'BLOCKED', 'WARN', 'WARNING'].includes(value);
  const tone = positive ? 'positive' : negative ? 'negative' : pending ? 'pending' : 'neutral';
  return <span className={`status-pill ${tone}`}>{value}</span>;
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

function validateProviderDraft(draft: ProviderDraft) {
  if (!draft.name.trim()) {
    return '供应商名称必填';
  }
  if (!MODEL_PROVIDER_TYPES.includes(draft.providerType as (typeof MODEL_PROVIDER_TYPES)[number])) {
    return '供应商类型无效';
  }
  if (draft.routingGroup.trim() && !/^[A-Za-z0-9_.:-]+$/.test(draft.routingGroup.trim())) {
    return '路由组仅支持字母、数字、点、下划线、冒号和短横线';
  }
  if (numberOrUndefined(draft.priority) === undefined || Number(draft.priority) < 0) {
    return '优先级不能小于 0';
  }
  if (numberOrUndefined(draft.timeoutMs) === undefined || Number(draft.timeoutMs) < 100) {
    return '超时不能小于 100ms';
  }
  if ((numberOrUndefined(draft.inputCostPer1kTokens) ?? 0) < 0 || (numberOrUndefined(draft.outputCostPer1kTokens) ?? 0) < 0) {
    return 'Token 成本不能为负数';
  }
  if (draft.providerType === 'OPENAI_COMPATIBLE') {
    if (!/^https?:\/\/.+/i.test(draft.baseUrl.trim())) {
      return 'OpenAI-compatible 供应商需要 http/https Base URL';
    }
    if (!/^env:[A-Z0-9_]+$/i.test(draft.apiKeyRef.trim())) {
      return 'OpenAI-compatible 供应商 apiKeyRef 需使用 env:VARIABLE_NAME';
    }
  }
  return '';
}

function providerPayload(draft: ProviderDraft, includeType: boolean): ModelProviderPayload {
  return {
    name: draft.name.trim(),
    providerType: includeType ? draft.providerType : undefined,
    routingGroup: draft.routingGroup.trim(),
    capabilities: draft.capabilities.trim(),
    baseUrl: draft.baseUrl.trim(),
    apiKeyRef: draft.apiKeyRef.trim(),
    priority: numberOrUndefined(draft.priority),
    timeoutMs: numberOrUndefined(draft.timeoutMs),
    inputCostPer1kTokens: numberOrUndefined(draft.inputCostPer1kTokens),
    outputCostPer1kTokens: numberOrUndefined(draft.outputCostPer1kTokens)
  };
}

function validatePolicyDraft(draft: PolicyDraft) {
  const scopeType = draft.scopeType.trim().toUpperCase();
  if (!MODEL_POLICY_SCOPE_TYPES.includes(scopeType as (typeof MODEL_POLICY_SCOPE_TYPES)[number])) {
    return '策略作用域无效';
  }
  if (scopeType !== 'PLATFORM' && !draft.scopeKey.trim()) {
    return '非平台级策略必须填写 Scope key';
  }
  if (draft.scopeKey.trim() && !/^[A-Za-z0-9_.:@-]{1,128}$/.test(draft.scopeKey.trim())) {
    return 'Scope key 仅支持 128 位内字母、数字、点、下划线、冒号、@ 和短横线';
  }
  const dailyBudgetLimit = numberOrUndefined(draft.dailyBudgetLimit);
  if (dailyBudgetLimit !== undefined && dailyBudgetLimit < 0) {
    return '日预算不能为负数';
  }
  const warningRatio = numberOrUndefined(draft.costAlertWarningRatio);
  if (warningRatio !== undefined && (warningRatio <= 0 || warningRatio > 1)) {
    return '告警比例必须在 0 到 1 之间';
  }
  if (draft.routingGroup.trim() && !/^[A-Za-z0-9_.:-]{1,64}$/.test(draft.routingGroup.trim())) {
    return '路由组仅支持字母、数字、点、下划线、冒号和短横线';
  }
  if (draft.reason.length > 300) {
    return '备注不能超过 300 字符';
  }
  return '';
}

function policyPayload(draft: PolicyDraft): ModelAccessPolicyPayload {
  return {
    scopeType: draft.scopeType.trim(),
    scopeKey: draft.scopeType === 'PLATFORM' ? 'GLOBAL' : draft.scopeKey.trim(),
    enabled: draft.enabled,
    modelInvocationEnabled: triStateToBoolean(draft.modelInvocationEnabled),
    publicModelAllowed: triStateToBoolean(draft.publicModelAllowed),
    dailyBudgetLimit: numberOrUndefined(draft.dailyBudgetLimit),
    costAlertWarningRatio: numberOrUndefined(draft.costAlertWarningRatio),
    budgetOverrunAction: draft.budgetOverrunAction || undefined,
    routingGroup: draft.routingGroup.trim(),
    reason: draft.reason.trim()
  };
}

function triStateToBoolean(value: PolicyDraft['modelInvocationEnabled']) {
  if (value === 'ENABLED') {
    return true;
  }
  if (value === 'DISABLED') {
    return false;
  }
  return undefined;
}

function triStateFromBoolean(value?: boolean): PolicyDraft['modelInvocationEnabled'] {
  if (value === true) {
    return 'ENABLED';
  }
  if (value === false) {
    return 'DISABLED';
  }
  return 'INHERIT';
}

function numberOrUndefined(value: string) {
  if (!value.trim()) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function buildInvocationFilters(filters: LogFilterDraft): InvocationFilters {
  return {
    projectId: filters.projectId,
    applicationId: filters.applicationId,
    environmentId: filters.environmentId,
    sensitivityLevel: filters.sensitivityLevel,
    status: filters.status,
    providerId: filters.providerId,
    actorService: filters.actorService,
    roleScope: filters.roleScope,
    startTime: localDateTimeToInstant(filters.startTime),
    endTime: localDateTimeToInstant(filters.endTime)
  };
}

function compactPolicyPreviewFilters(filters: PolicyPreviewDraft) {
  return {
    projectId: filters.projectId,
    environmentId: filters.environmentId,
    roles: filters.roles
  };
}

function compactCostFilters(filters: CostFilterDraft) {
  return {
    projectId: filters.projectId,
    startDate: filters.startDate,
    endDate: filters.endDate
  };
}

function compactCostAlertFilters(filters: CostFilterDraft) {
  return {
    projectId: filters.projectId,
    actorService: filters.actorService
  };
}

function localDateTimeToInstant(value: string) {
  if (!value) {
    return undefined;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function buildDiffRows(leftContent: string, rightContent: string): DiffRow[] {
  const leftLines = leftContent.split(/\r?\n/);
  const rightLines = rightContent.split(/\r?\n/);
  const count = Math.max(leftLines.length, rightLines.length);
  const rows: DiffRow[] = [];
  for (let index = 0; index < count; index += 1) {
    const left = leftLines[index] ?? '';
    const right = rightLines[index] ?? '';
    if (left === right) {
      rows.push({ type: 'same', lineNo: index + 1, left, right });
    } else if (!left) {
      rows.push({ type: 'added', lineNo: index + 1, left, right });
    } else if (!right) {
      rows.push({ type: 'removed', lineNo: index + 1, left, right });
    } else {
      rows.push({ type: 'changed', lineNo: index + 1, left, right });
    }
  }
  return rows;
}

function downloadText(text: string, filename: string, contentType: string) {
  const blob = new Blob([text], { type: contentType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function traceId(error: unknown) {
  return error && typeof error === 'object' && 'traceId' in error && typeof error.traceId === 'string'
    ? error.traceId
    : '';
}

function formatMoney(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 4,
    maximumFractionDigits: 4
  });
}

function formatOptionalBoolean(value?: boolean) {
  if (value === true) {
    return '允许';
  }
  if (value === false) {
    return '关闭';
  }
  return '继承';
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}
