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
  activatePromptVersion,
  approvePromptVersion,
  cancelModelInvocationJob,
  checkModelProvider,
  createModelProvider,
  createPromptVersion,
  disableModelProvider,
  enableModelProvider,
  exportInvocationsCsv,
  fetchEffectiveModelAccessPolicy,
  fetchModelInvocationJob,
  fetchModelQualityEvaluationSummary,
  fetchCostAlerts,
  fetchCostReport,
  fetchInvocationSummary,
  fetchInvocations,
  fetchModelAccessHealth,
  fetchModelAccessPolicies,
  fetchModelProviders,
  fetchPrompts,
  fetchProviderResilience,
  invokeModel,
  invokeModelStream,
  invocationExportPath,
  rejectPromptVersion,
  resetProviderCircuit,
  submitModelInvocationJob,
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
  type ModelInvocationJob,
  type ModelQualityEvaluationSummary,
  type ModelProviderConfig,
  type ModelProviderPayload,
  type ModelStreamEvent,
  type PromptTemplate,
  type ProviderCheckResponse,
  type ProviderResilienceResponse,
  type InvokeModelResponse
} from '../api/modelAccess';
import { canUseButton, hasPermission } from '../permissions';
import { translate } from '../platform/i18n';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type TabKey = 'providers' | 'prompts' | 'playground' | 'quality' | 'policies' | 'logs';

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

type PlaygroundMessageDraft = {
  id: string;
  role: string;
  content: string;
};

type PlaygroundDraft = {
  projectId: string;
  applicationId: string;
  environmentId: string;
  promptKey: string;
  promptVariablesText: string;
  providerId: string;
  modelName: string;
  allowPublicModel: boolean;
  sensitivityLevel: string;
  capability: string;
  messages: PlaygroundMessageDraft[];
};

type PlaygroundRunMode = 'sync' | 'stream' | 'async';

type PlaygroundResult = {
  mode?: PlaygroundRunMode;
  response?: InvokeModelResponse;
  streamEvents: ModelStreamEvent[];
  streamContent: string;
  job?: ModelInvocationJob | null;
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

const initialPlaygroundDraft: PlaygroundDraft = {
  projectId: '',
  applicationId: '',
  environmentId: '',
  promptKey: '',
  promptVariablesText: '{\n  "context": ""\n}',
  providerId: '',
  modelName: '',
  allowPublicModel: false,
  sensitivityLevel: 'INTERNAL',
  capability: 'CHAT',
  messages: [createPlaygroundMessage('user', '')]
};

const initialPlaygroundResult: PlaygroundResult = {
  streamEvents: [],
  streamContent: '',
  job: null
};

const qualityTaskTypeOptions = ['ALL', 'case-design', 'defect-triage', 'requirement-summary'] as const;

const tabs: Array<{ key: TabKey; label: string; icon: LucideIcon }> = [
  { key: 'providers', label: translate('auto.k0905'), icon: ServerCog },
  { key: 'prompts', label: translate('auto.k2610'), icon: FileDiff },
  { key: 'playground', label: translate('auto.k2611'), icon: PlayCircle },
  { key: 'quality', label: translate('auto.k0906'), icon: Eye },
  { key: 'policies', label: translate('auto.k0907'), icon: SlidersHorizontal },
  { key: 'logs', label: translate('auto.k0908'), icon: Activity }
];

export function ModelAccessConsole(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'modelAccess:read');
  const canManageInvocations = hasPermission(props.currentUser, 'modelAccess:manage');
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
  const [qualitySummary, setQualitySummary] = useState<ModelQualityEvaluationSummary | null>(null);
  const [costReport, setCostReport] = useState<CostReport>({ rows: [] });
  const [alerts, setAlerts] = useState<CostAlert[]>([]);
  const [providerDraft, setProviderDraft] = useState<ProviderDraft>(initialProviderDraft);
  const [editingProviderId, setEditingProviderId] = useState<string | null>(null);
  const [promptDraft, setPromptDraft] = useState<PromptDraft>(initialPromptDraft);
  const [playgroundDraft, setPlaygroundDraft] = useState<PlaygroundDraft>(initialPlaygroundDraft);
  const [playgroundResult, setPlaygroundResult] = useState<PlaygroundResult>(initialPlaygroundResult);
  const [policyDraft, setPolicyDraft] = useState<PolicyDraft>(initialPolicyDraft);
  const [policyPreviewDraft, setPolicyPreviewDraft] = useState<PolicyPreviewDraft>(initialPolicyPreviewDraft);
  const [promptFilter, setPromptFilter] = useState('');
  const [qualityTaskType, setQualityTaskType] = useState<(typeof qualityTaskTypeOptions)[number]>('ALL');
  const [leftPromptId, setLeftPromptId] = useState('');
  const [rightPromptId, setRightPromptId] = useState('');
  const [logFilters, setLogFilters] = useState<LogFilterDraft>(initialLogFilters);
  const [costFilters, setCostFilters] = useState<CostFilterDraft>(initialCostFilters);
  const [providerChecks, setProviderChecks] = useState<Record<string, ProviderCheckResponse>>({});
  const [providerResilience, setProviderResilience] = useState<Record<string, ProviderResilienceResponse>>({});
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [providerState, setProviderState] = useState<WorkState>({ loading: false });
  const [promptState, setPromptState] = useState<WorkState>({ loading: false });
  const [playgroundState, setPlaygroundState] = useState<WorkState>({ loading: false });
  const [qualityState, setQualityState] = useState<WorkState>({ loading: false });
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

  const refreshQualitySummary = useCallback(async (taskType = qualityTaskType) => {
    const response = await fetchModelQualityEvaluationSummary(taskType === 'ALL' ? undefined : taskType);
    setQualitySummary(response.data);
    return response.trace_id;
  }, [qualityTaskType]);

  const refreshAll = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setProviders([]);
      setPrompts([]);
      setPolicies([]);
      setEffectivePolicy(null);
      setInvocations({ items: [], total: 0 });
      setSummary(null);
      setQualitySummary(null);
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
      errors.push(errorMessage(healthResult.reason, translate('auto.k0909')));
    }

    if (providersResult.status === 'fulfilled') {
      setProviders(providersResult.value.data);
      traceIds.push(providersResult.value.trace_id);
    } else {
      errors.push(errorMessage(providersResult.reason, translate('auto.k0910')));
    }

    if (promptsResult.status === 'fulfilled') {
      traceIds.push(promptsResult.value);
    } else {
      errors.push(errorMessage(promptsResult.reason, translate('auto.k0911')));
    }

    if (policiesResult.status === 'fulfilled') {
      traceIds.push(policiesResult.value);
    } else {
      errors.push(errorMessage(policiesResult.reason, translate('auto.k0912')));
    }

    if (logsResult.status === 'fulfilled') {
      traceIds.push(logsResult.value);
    } else {
      errors.push(errorMessage(logsResult.reason, translate('auto.k0913')));
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

  useEffect(() => {
    if (!props.signedIn || !canRead) {
      return;
    }
    void refreshQualitySummary();
  }, [canRead, props.signedIn, refreshQualitySummary]);

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

  useEffect(() => {
    if (!playgroundDraft.promptKey && selectedPromptKey) {
      setPlaygroundDraft((current) => ({ ...current, promptKey: selectedPromptKey }));
    }
  }, [playgroundDraft.promptKey, selectedPromptKey]);

  async function onSubmitProvider(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManageProviders) {
      setProviderState({ loading: false, error: translate('auto.k0914') });
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
        success: editingProviderId ? translate('auto.k0915') : translate('auto.k0916'),
        traceId: providersResponse.trace_id || response.trace_id
      });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, translate('auto.k0917')), traceId: traceId(error) });
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
      setProviderState({ loading: false, success: translate('auto.k0918', { value0: response.data.status === 'ENABLED' ? translate('auto.k0251') : translate('auto.k0253') }), traceId: response.trace_id });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, translate('auto.k0919')), traceId: traceId(error) });
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
      setProviderState({ loading: false, success: translate('auto.k0920', { value0: provider.name }), traceId: checkResponse.trace_id });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, translate('auto.k0921')), traceId: traceId(error) });
    }
  }

  async function onResetCircuit(provider: ModelProviderConfig) {
    if (!canManageProviders) return;
    setProviderState({ loading: true });
    try {
      const response = await resetProviderCircuit(provider.id);
      setProviderResilience((current) => ({ ...current, [provider.id]: response.data }));
      setProviderState({ loading: false, success: translate('auto.k0922', { value0: provider.name }), traceId: response.trace_id });
    } catch (error: unknown) {
      setProviderState({ loading: false, error: errorMessage(error, translate('auto.k0923')), traceId: traceId(error) });
    }
  }

  async function onSubmitPrompt(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManagePrompts) {
      setPromptState({ loading: false, error: translate('auto.k0924') });
      return;
    }
    if (!promptDraft.promptKey.trim() || !promptDraft.name.trim() || !promptDraft.content.trim()) {
      setPromptState({ loading: false, error: translate('auto.k0925') });
      return;
    }
    if (promptDraft.content.length > 12000) {
      setPromptState({ loading: false, error: translate('auto.k0926') });
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
      setPromptState({ loading: false, success: translate('auto.k0927', { value0: response.data.version }), traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, translate('auto.k0928')), traceId: traceId(error) });
    }
  }

  async function onActivatePrompt(prompt: PromptTemplate) {
    if (!canManagePrompts) return;
    setPromptState({ loading: true });
    try {
      const response = await activatePromptVersion(prompt.id);
      const traceId = await refreshPrompts();
      setPromptState({ loading: false, success: translate('auto.k0929', { value0: prompt.promptKey, value1: prompt.version }), traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, translate('auto.k0930')), traceId: traceId(error) });
    }
  }

  async function onApprovePrompt(prompt: PromptTemplate) {
    if (!canManagePrompts) return;
    setPromptState({ loading: true });
    try {
      const response = await approvePromptVersion(prompt.id, { reviewNote: prompt.changeNote });
      const traceId = await refreshPrompts();
      setPromptState({ loading: false, success: translate('auto.k0931', { value0: prompt.promptKey, value1: prompt.version }), traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, translate('auto.k0932')), traceId: traceId(error) });
    }
  }

  async function onRejectPrompt(prompt: PromptTemplate) {
    if (!canManagePrompts) return;
    setPromptState({ loading: true });
    try {
      const response = await rejectPromptVersion(prompt.id, { reviewNote: prompt.changeNote });
      const traceId = await refreshPrompts();
      setPromptState({ loading: false, success: translate('auto.k0933', { value0: prompt.promptKey, value1: prompt.version }), traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPromptState({ loading: false, error: errorMessage(error, translate('auto.k0934')), traceId: traceId(error) });
    }
  }

  async function onSubmitPolicy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManagePolicies) {
      setPolicyState({ loading: false, error: translate('auto.k0935') });
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
      setPolicyState({ loading: false, success: translate('auto.k0936', { value0: response.data.scopeType, value1: response.data.scopeKey }), traceId: traceId || response.trace_id });
    } catch (error: unknown) {
      setPolicyState({ loading: false, error: errorMessage(error, translate('auto.k0937')), traceId: traceId(error) });
    }
  }

  async function onRunPlayground(mode: PlaygroundRunMode) {
    if (!canManageInvocations) {
      setPlaygroundState({ loading: false, error: translate('auto.k0938') });
      return;
    }
    const payload = buildPlaygroundPayload(playgroundDraft);
    if ('error' in payload) {
      setPlaygroundState({ loading: false, error: payload.error });
      return;
    }

    setPlaygroundState({ loading: true });
    setPlaygroundResult(initialPlaygroundResult);
    try {
      if (mode === 'sync') {
        const response = await invokeModel(payload.value);
        setPlaygroundResult({
          mode,
          response: response.data,
          streamEvents: [],
          streamContent: response.data.content,
          job: null
        });
        setPlaygroundState({ loading: false, success: translate('auto.k0939'), traceId: response.trace_id });
        return;
      }
      if (mode === 'stream') {
        const streamEvents: ModelStreamEvent[] = [];
        const events = await invokeModelStream(payload.value, (event) => {
          streamEvents.push(event);
          setPlaygroundResult((current) => ({
            ...current,
            mode,
            streamEvents: [...streamEvents],
            streamContent: streamEvents
              .filter((item) => item.type === 'delta')
              .map((item) => item.content)
              .join('')
          }));
        });
        const metadata = events.find((event) => event.type === 'metadata');
        setPlaygroundResult((current) => ({
          ...current,
          mode,
          streamEvents: events,
          streamContent: events.filter((event) => event.type === 'delta').map((event) => event.content).join(''),
          response: metadata ? {
            invocationId: metadata.invocationId,
            providerId: metadata.providerId,
            providerName: metadata.providerName,
            modelName: metadata.modelName,
            fallbackUsed: metadata.fallbackUsed,
            content: events.filter((event) => event.type === 'delta').map((event) => event.content).join(''),
            inputTokens: metadata.inputTokens,
            outputTokens: metadata.outputTokens,
            totalCost: metadata.totalCost
          } : undefined
        }));
        setPlaygroundState({
          loading: false,
          success: translate('auto.k0940'),
          traceId: metadata?.traceId
        });
        return;
      }

      const response = await submitModelInvocationJob(payload.value);
      setPlaygroundResult({
        mode,
        streamEvents: [],
        streamContent: '',
        job: response.data
      });
      setPlaygroundState({ loading: false, success: translate('auto.k0941'), traceId: response.trace_id || response.data.traceId });
    } catch (error: unknown) {
      setPlaygroundState({ loading: false, error: errorMessage(error, translate('auto.k0942')), traceId: traceId(error) });
    }
  }

  async function onRefreshPlaygroundJob() {
    const jobId = playgroundResult.job?.jobId;
    if (!jobId) {
      setPlaygroundState({ loading: false, error: translate('auto.k0943') });
      return;
    }
    setPlaygroundState({ loading: true });
    try {
      const response = await fetchModelInvocationJob(jobId);
      setPlaygroundResult((current) => ({
        ...current,
        mode: 'async',
        job: response.data,
        response: response.data.response ?? current.response
      }));
      setPlaygroundState({ loading: false, success: translate('auto.k0944'), traceId: response.trace_id || response.data.traceId });
    } catch (error: unknown) {
      setPlaygroundState({ loading: false, error: errorMessage(error, translate('auto.k0945')), traceId: traceId(error) });
    }
  }

  async function onCancelPlaygroundJob() {
    const jobId = playgroundResult.job?.jobId;
    if (!jobId) {
      setPlaygroundState({ loading: false, error: translate('auto.k0946') });
      return;
    }
    setPlaygroundState({ loading: true });
    try {
      const response = await cancelModelInvocationJob(jobId);
      setPlaygroundResult((current) => ({
        ...current,
        mode: 'async',
        job: response.data,
        response: response.data.response ?? current.response
      }));
      setPlaygroundState({ loading: false, success: translate('auto.k0947'), traceId: response.trace_id || response.data.traceId });
    } catch (error: unknown) {
      setPlaygroundState({ loading: false, error: errorMessage(error, translate('auto.k0948')), traceId: traceId(error) });
    }
  }

  async function onRefreshQuality(taskType = qualityTaskType) {
    setQualityState({ loading: true });
    try {
      const traceId = await refreshQualitySummary(taskType);
      setQualityState({ loading: false, success: translate('auto.k0949'), traceId });
    } catch (error: unknown) {
      setQualityState({ loading: false, error: errorMessage(error, translate('auto.k0950')), traceId: traceId(error) });
    }
  }

  async function onRefreshEffectivePolicy(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setPolicyState({ loading: true });
    try {
      const response = await fetchEffectiveModelAccessPolicy(compactPolicyPreviewFilters(policyPreviewDraft));
      setEffectivePolicy(response.data);
      setPolicyState({ loading: false, success: translate('auto.k0951'), traceId: response.trace_id });
    } catch (error: unknown) {
      setPolicyState({ loading: false, error: errorMessage(error, translate('auto.k0952')), traceId: traceId(error) });
    }
  }

  async function onApplyLogFilters(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setLogState({ loading: true });
    try {
      const traceId = await refreshLogs();
      setLogState({ loading: false, success: translate('auto.k0953'), traceId });
    } catch (error: unknown) {
      setLogState({ loading: false, error: errorMessage(error, translate('auto.k0954')), traceId: traceId(error) });
    }
  }

  async function onExportInvocations() {
    if (!canExport) {
      setExportState({ loading: false, error: translate('auto.k0955') });
      return;
    }
    setExportState({ loading: true });
    try {
      const response = await exportInvocationsCsv(invocationFilters);
      downloadText(response.text, response.filename ?? 'wp2-invocations.csv', response.contentType || 'text/csv;charset=UTF-8');
      setExportState({ loading: false, success: translate('auto.k0956', { value0: invocationExportPath(invocationFilters) }), traceId: response.traceId });
    } catch (error: unknown) {
      setExportState({ loading: false, error: errorMessage(error, translate('auto.k0957')), traceId: traceId(error) });
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
            <strong>{translate('auto.k0958')}</strong>
            <span>{translate('auto.k0959')}</span>
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
                <h2>{translate('auto.k0960')}</h2>
              </div>
            </div>
            <div className="panel-toolbar-actions">
              <button className="icon-button" type="button" onClick={() => void refreshAll()} disabled={loadState.loading} title={translate('auto.k0170')}>
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
                  setPromptState({ loading: false, error: errorMessage(error, translate('auto.k0961')), traceId: traceId(error) });
                }
              }}
              onLeftPromptChange={setLeftPromptId}
              onReject={onRejectPrompt}
              onRightPromptChange={setRightPromptId}
              onSubmit={onSubmitPrompt}
            />
          )}

          {activeTab === 'playground' && (
            <PlaygroundTab
              canManage={canManageInvocations}
              draft={playgroundDraft}
              prompts={selectedPromptVersions.length ? selectedPromptVersions : prompts}
              providers={providers}
              result={playgroundResult}
              state={playgroundState}
              onAddMessage={() => {
                setPlaygroundDraft((current) => ({
                  ...current,
                  messages: [...current.messages, createPlaygroundMessage('user', '')]
                }));
                setPlaygroundState({ loading: false });
              }}
              onChangeDraft={(key, value) => {
                setPlaygroundDraft((current) => ({ ...current, [key]: value }));
                setPlaygroundState({ loading: false });
              }}
              onChangeMessage={(id, key, value) => {
                setPlaygroundDraft((current) => ({
                  ...current,
                  messages: current.messages.map((item) => item.id === id ? { ...item, [key]: value } : item)
                }));
                setPlaygroundState({ loading: false });
              }}
              onRemoveMessage={(id) => {
                setPlaygroundDraft((current) => {
                  const nextMessages = current.messages.filter((item) => item.id !== id);
                  return { ...current, messages: nextMessages.length ? nextMessages : [createPlaygroundMessage('user', '')] };
                });
                setPlaygroundState({ loading: false });
              }}
              onReset={() => {
                setPlaygroundDraft({
                  ...initialPlaygroundDraft,
                  projectId: playgroundDraft.projectId,
                  promptKey: selectedPromptKey || initialPlaygroundDraft.promptKey
                });
                setPlaygroundResult(initialPlaygroundResult);
                setPlaygroundState({ loading: false });
              }}
              onRun={onRunPlayground}
              onRefreshJob={onRefreshPlaygroundJob}
              onCancelJob={onCancelPlaygroundJob}
            />
          )}

          {activeTab === 'quality' && (
            <QualityTab
              selectedTaskType={qualityTaskType}
              summary={qualitySummary}
              state={qualityState}
              onChangeTaskType={(value) => {
                setQualityTaskType(value);
                setQualityState({ loading: false });
              }}
              onRefresh={() => void onRefreshQuality()}
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
            <h2>{translate('auto.k0384')}</h2>
            <Activity size={16} />
          </div>
          <div className="document-health-grid">
            <StatusMetric label={translate('auto.k0427')} value={statusText(health?.status ?? 'UNKNOWN')} tone={health?.status === 'UP' ? 'positive' : 'negative'} />
            <StatusMetric label={translate('auto.k0962')} value={health?.enabledProviders ?? providers.filter((item) => item.status === 'ENABLED').length} />
            <StatusMetric label={translate('auto.k2612')} value={health?.activePrompts ?? prompts.filter((item) => item.status === 'ACTIVE').length} />
            <StatusMetric label={translate('auto.k0963')} value={health?.openCircuitProviders ?? 0} tone={(health?.openCircuitProviders ?? 0) > 0 ? 'negative' : 'positive'} />
            <StatusMetric label={translate('auto.k0964')} value={health?.providerRateLimitEnabled ? health.providerRateLimitMaxRequests : translate('auto.k2616')} />
            <StatusMetric label={translate('auto.k0965')} value={health?.providerConcurrencyLimitEnabled ? health.providerMaxConcurrentRequests : translate('auto.k2616')} />
          </div>
        </div>

        <div className="panel detail-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0966')}</h2>
            <KeyRound size={16} />
          </div>
          <div className="model-access-permission-list">
            <PermissionFlag label={translate('auto.k0967')} enabled={canRead} />
            <PermissionFlag label={translate('auto.k0968')} enabled={canManageProviders} />
            <PermissionFlag label={translate('auto.k0969')} enabled={canManagePrompts} />
            <PermissionFlag label={translate('auto.k0970')} enabled={canManagePolicies} />
            <PermissionFlag label={translate('auto.k0971')} enabled={canExport} />
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
            <span>{translate('auto.k0177')}<b>*</b></span>
            <input value={props.draft.name} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('name', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k0286')}<b>*</b></span>
            <select value={props.draft.providerType} disabled={!props.canManage || Boolean(props.editingProviderId)} onChange={(event) => props.onChangeDraft('providerType', event.target.value)}>
              {MODEL_PROVIDER_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="field">
            <span>{translate('auto.k0972')}</span>
            <input value={props.draft.routingGroup} placeholder="default" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('routingGroup', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k0973')}</span>
            <input value={props.draft.capabilities} placeholder="CHAT,TEXT,JSON" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('capabilities', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k2613')}</span>
            <input value={props.draft.baseUrl} placeholder="https://api.example.com" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('baseUrl', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k2614')}</span>
            <input value={props.draft.apiKeyRef} placeholder="env:MODEL_API_KEY" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('apiKeyRef', event.target.value)} />
            <small>{translate('auto.k0974')}</small>
          </label>
          <label className="field">
            <span>{translate('auto.k0419')}</span>
            <input type="number" min="0" value={props.draft.priority} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('priority', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k0975')}</span>
            <input type="number" min="100" value={props.draft.timeoutMs} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('timeoutMs', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k0976')}</span>
            <input type="number" min="0" step="0.0001" value={props.draft.inputCostPer1kTokens} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('inputCostPer1kTokens', event.target.value)} />
          </label>
          <label className="field">
            <span>{translate('auto.k0977')}</span>
            <input type="number" min="0" step="0.0001" value={props.draft.outputCostPer1kTokens} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('outputCostPer1kTokens', event.target.value)} />
          </label>
        </div>
        <div className="document-actions">
          <button className="primary-button" type="submit" disabled={!props.canManage || props.state.loading}>
            {props.editingProviderId ? <Save size={16} /> : <Plus size={16} />}
            {props.editingProviderId ? translate('auto.k0978') : translate('auto.k0979')}
          </button>
          {props.editingProviderId && (
            <button className="secondary-button" type="button" onClick={props.onCancelEdit}>{translate('auto.k0739')}</button>
          )}
          <StateLine state={props.state} />
        </div>
      </form>

      <div className="table-wrap model-access-table-wrap">
        <table className="model-access-provider-table">
          <thead>
            <tr>
              <th>{translate('auto.k0905')}</th>
              <th>{translate('auto.k0182')}</th>
              <th>{translate('auto.k0980')}</th>
              <th>{translate('auto.k2614')}</th>
              <th>{translate('auto.k0981')}</th>
              <th>{translate('auto.k0982')}</th>
              <th>{translate('auto.k0983')}</th>
              <th>{translate('auto.k0249')}</th>
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
                        <span className="table-secondary">{check.cached ? translate('auto.k2615') : `${check.latencyMs}ms`}</span>
                      </>
                    ) : <span className="table-secondary">-</span>}
                  </td>
                  <td>
                    {resilience ? (
                      <>
                        <StatusPill value={resilience.circuitOpen ? 'OPEN' : 'CLOSED'} />
                        <span className="table-secondary">{translate('auto.k2617', { value0: resilience.consecutiveFailures })}</span>
                      </>
                    ) : <span className="table-secondary">-</span>}
                  </td>
                  <td>
                    <span className="table-primary">{formatMoney(provider.inputCostPer1kTokens)} / {formatMoney(provider.outputCostPer1kTokens)}</span>
                    <span className="table-secondary">{translate('auto.k2618')}</span>
                  </td>
                  <td>
                    <div className="model-access-row-actions">
                      <button className="mini-button icon-only" type="button" title={translate('auto.k0746')} disabled={!props.canManage} onClick={() => props.onEdit(provider)}><Save size={14} /></button>
                      <button className="mini-button icon-only" type="button" title={provider.status === 'ENABLED' ? translate('auto.k0253') : translate('auto.k0251')} disabled={!props.canManage} onClick={() => void props.onToggle(provider)}>
                        {provider.status === 'ENABLED' ? <ToggleLeft size={14} /> : <ToggleRight size={14} />}
                      </button>
                      <button className="mini-button icon-only" type="button" title={translate('auto.k0984')} disabled={!props.canManage} onClick={() => void props.onCheck(provider)}><PlayCircle size={14} /></button>
                      <button className="mini-button icon-only" type="button" title={translate('auto.k0985')} disabled={!props.canManage} onClick={() => void props.onResetCircuit(provider)}><RotateCcw size={14} /></button>
                    </div>
                  </td>
                </tr>
              );
            }) : (
              <tr><td className="table-empty" colSpan={8}>{translate('auto.k0986')}</td></tr>
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
          <div className="model-access-policy-entry">
            <div>
              <strong>{translate('auto.k0987')}</strong>
              <span>{translate('auto.k0988')}</span>
            </div>
            <a className="btn btn-secondary btn-sm" href="#test-design">
              <SlidersHorizontal size={15} /> {translate('auto.k0989')}</a>
          </div>
          <div className="document-form-grid model-access-policy-form-grid">
            <label className="field">
              <span>{translate('auto.k0263')}<b>*</b></span>
              <select value={props.draft.scopeType} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('scopeType', event.target.value)}>
                {MODEL_POLICY_SCOPE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
              </select>
            </label>
            <label className="field">
              <span>{translate('auto.k2619')}<b>*</b></span>
              <input value={props.draft.scopeKey} disabled={!props.canManage || props.draft.scopeType === 'PLATFORM'} onChange={(event) => props.onChangeDraft('scopeKey', event.target.value)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>{translate('auto.k0990')}</span>
              <input type="checkbox" checked={props.draft.enabled} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('enabled', event.target.checked)} />
            </label>
            <label className="field">
              <span>{translate('auto.k0991')}</span>
              <select value={props.draft.modelInvocationEnabled} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('modelInvocationEnabled', event.target.value)}>
                <option value="INHERIT">{translate('auto.k0992')}</option>
                <option value="ENABLED">{translate('auto.k0993')}</option>
                <option value="DISABLED">{translate('auto.k0994')}</option>
              </select>
            </label>
            <label className="field">
              <span>{translate('auto.k0995')}</span>
              <select value={props.draft.publicModelAllowed} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('publicModelAllowed', event.target.value)}>
                <option value="INHERIT">{translate('auto.k0992')}</option>
                <option value="ENABLED">{translate('auto.k0993')}</option>
                <option value="DISABLED">{translate('auto.k0996')}</option>
              </select>
            </label>
            <label className="field">
              <span>{translate('auto.k0997')}</span>
              <input type="number" min="0" step="0.00000001" value={props.draft.dailyBudgetLimit} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('dailyBudgetLimit', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k0998')}</span>
              <input type="number" min="0.01" max="1" step="0.01" value={props.draft.costAlertWarningRatio} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('costAlertWarningRatio', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k0999')}</span>
              <select value={props.draft.budgetOverrunAction} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('budgetOverrunAction', event.target.value)}>
                <option value="">{translate('auto.k0992')}</option>
                <option value="BLOCK">{translate('auto.k1000')}</option>
                <option value="FALLBACK">{translate('auto.k1001')}</option>
              </select>
            </label>
            <label className="field">
              <span>{translate('auto.k0972')}</span>
              <input value={props.draft.routingGroup} placeholder="default/private" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('routingGroup', event.target.value)} />
            </label>
            <label className="field model-access-policy-reason">
              <span>{translate('auto.k0211')}</span>
              <input value={props.draft.reason} maxLength={300} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('reason', event.target.value)} />
            </label>
          </div>
          <div className="document-actions">
            <button className="primary-button" type="submit" disabled={!props.canManage || props.state.loading}>
              <Save size={16} /> {translate('auto.k1002')}</button>
            <button className="secondary-button" type="button" disabled={!props.canManage} onClick={props.onResetDraft}>{translate('auto.k0254')}</button>
            <StateLine state={props.state} />
          </div>
        </form>

        <form className="model-access-effective-panel" onSubmit={(event) => void props.onPreview(event)}>
          <div className="panel-title-row">
            <h2>{translate('auto.k1003')}</h2>
            <Eye size={16} />
          </div>
          <div className="document-form-grid model-access-effective-grid">
            <label className="field"><span>{translate('auto.k2620')}</span><input value={props.previewDraft.projectId} onChange={(event) => props.onChangePreview('projectId', event.target.value)} /></label>
            <label className="field"><span>{translate('auto.k2621')}</span><input value={props.previewDraft.environmentId} onChange={(event) => props.onChangePreview('environmentId', event.target.value)} /></label>
            <label className="field"><span>{translate('auto.k0247')}</span><input value={props.previewDraft.roles} placeholder="SuperAdmin,Auditor" onChange={(event) => props.onChangePreview('roles', event.target.value)} /></label>
          </div>
          <div className="document-actions">
            <button className="secondary-button" type="submit" disabled={props.state.loading}><RefreshCw size={15} /> {translate('auto.k1004')}</button>
          </div>
          <div className="model-access-summary-grid">
            <StatusMetric label={translate('auto.k1005')} value={switchText(props.effectivePolicy?.modelInvocationEnabled)} tone={props.effectivePolicy?.modelInvocationEnabled ? 'positive' : 'negative'} />
            <StatusMetric label={translate('auto.k0995')} value={switchText(props.effectivePolicy?.publicModelAllowed)} tone={props.effectivePolicy?.publicModelAllowed ? 'positive' : 'pending'} />
            <StatusMetric label={translate('auto.k1006')} value={props.effectivePolicy?.dailyBudgetLimit === undefined ? '-' : formatMoney(props.effectivePolicy.dailyBudgetLimit)} />
            <StatusMetric label={translate('auto.k0363')} value={props.effectivePolicy?.budgetOverrunAction ?? '-'} />
            <StatusMetric label={translate('auto.k0972')} value={props.effectivePolicy?.routingGroup ?? '-'} />
            <StatusMetric label={translate('auto.k0247')} value={props.effectivePolicy?.roleScope ?? '-'} />
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
              <th>{translate('auto.k0263')}</th>
              <th>{translate('auto.k1007')}</th>
              <th>{translate('auto.k1006')}</th>
              <th>{translate('auto.k0980')}</th>
              <th>{translate('auto.k0211')}</th>
              <th>{translate('auto.k1008')}</th>
              <th>{translate('auto.k0249')}</th>
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
                  <span className="table-secondary">{translate('auto.k1005')}{formatOptionalBoolean(policy.modelInvocationEnabled)} {translate('auto.k1009')}{formatOptionalBoolean(policy.publicModelAllowed)}</span>
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
                    <Save size={14} /> {translate('auto.k0746')}</button>
                </td>
              </tr>
            )) : (
              <tr><td className="table-empty" colSpan={7}>{translate('auto.k1010')}</td></tr>
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
              <span>{translate('auto.k2622')}<b>*</b></span>
              <input value={props.draft.promptKey} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('promptKey', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k0177')}<b>*</b></span>
              <input value={props.draft.name} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('name', event.target.value)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>{translate('auto.k1011')}</span>
              <input type="checkbox" checked={props.draft.activate} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('activate', event.target.checked)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>{translate('auto.k1012')}</span>
              <input type="checkbox" checked={props.draft.highRisk} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('highRisk', event.target.checked)} />
            </label>
          </div>
          <label className="field document-content-field">
            <span>{translate('auto.k1013')}<b>*</b></span>
            <textarea value={props.draft.content} maxLength={12000} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('content', event.target.value)} />
            <small>{props.draft.content.length} / 12000</small>
          </label>
          <label className="field">
            <span>{translate('auto.k1014')}</span>
            <input value={props.draft.changeNote} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('changeNote', event.target.value)} />
          </label>
          <div className="document-actions">
            <button className="primary-button" type="submit" disabled={!props.canManage || props.state.loading}>
              <Plus size={16} /> {translate('auto.k1015')}</button>
            <StateLine state={props.state} />
          </div>
        </form>

        <div className="model-access-diff-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k1016')}</h2>
            <FileDiff size={16} />
          </div>
          <div className="model-access-diff-selectors">
            <select value={props.leftPromptId} onChange={(event) => props.onLeftPromptChange(event.target.value)}>
              <option value="">{translate('auto.k1017')}</option>
              {props.selectedPromptVersions.map((prompt) => <option key={prompt.id} value={prompt.id}>{prompt.promptKey} v{prompt.version}</option>)}
            </select>
            <select value={props.rightPromptId} onChange={(event) => props.onRightPromptChange(event.target.value)}>
              <option value="">{translate('auto.k1018')}</option>
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
            )) : <span className="table-secondary">{translate('auto.k1019')}</span>}
          </div>
        </div>
      </div>

      <form className="asset-filter-bar model-access-prompt-filter" onSubmit={(event) => {
        event.preventDefault();
        void props.onFilter(props.promptFilter);
      }}>
        <label className="field">
          <span>{translate('auto.k2622')}</span>
          <input value={props.promptFilter} list="model-access-prompt-keys" onChange={(event) => void props.onFilter(event.target.value)} />
          <datalist id="model-access-prompt-keys">
            {props.promptKeys.map((key) => <option key={key} value={key} />)}
          </datalist>
        </label>
        <div className="asset-filter-actions">
          <button className="secondary-button" type="submit"><Search size={15} /> {translate('auto.k0372')}</button>
        </div>
      </form>

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>{translate('auto.k2610')}</th>
              <th>{translate('auto.k0178')}</th>
              <th>{translate('auto.k0182')}</th>
              <th>{translate('auto.k0213')}</th>
              <th>{translate('auto.k1020')}</th>
              <th>{translate('auto.k0421')}</th>
              <th>{translate('auto.k0249')}</th>
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
                    <span className="table-secondary">{prompt.approvedBy ?? (prompt.highRisk ? translate('auto.k1021') : '-')}</span>
                  </td>
                  <td><span className="table-secondary">{prompt.changeNote ?? '-'}</span></td>
                  <td><span className="table-secondary">{formatDateTime(prompt.updatedAt)}</span></td>
                  <td>
                    <button className="mini-button" type="button" disabled={!canApprovePrompt} onClick={() => void props.onApprove(prompt)}>
                      <CheckCircle2 size={14} /> {translate('auto.k1022')}</button>
                    <button className="mini-button" type="button" disabled={!canRejectPrompt} onClick={() => void props.onReject(prompt)}>
                      <XCircle size={14} /> {translate('auto.k0214')}</button>
                    <button className="mini-button" type="button" disabled={!canActivatePrompt} onClick={() => void props.onActivate(prompt)}>
                      <CheckCircle2 size={14} /> {translate('auto.k1011')}</button>
                  </td>
                </tr>
              );
            }) : (
              <tr><td className="table-empty" colSpan={7}>{translate('auto.k1023')}</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function PlaygroundTab(props: {
  canManage: boolean;
  draft: PlaygroundDraft;
  prompts: PromptTemplate[];
  providers: ModelProviderConfig[];
  result: PlaygroundResult;
  state: WorkState;
  onAddMessage: () => void;
  onChangeDraft: (key: keyof PlaygroundDraft, value: string | boolean | PlaygroundMessageDraft[]) => void;
  onChangeMessage: (id: string, key: 'role' | 'content', value: string) => void;
  onRemoveMessage: (id: string) => void;
  onReset: () => void;
  onRun: (mode: PlaygroundRunMode) => Promise<void>;
  onRefreshJob: () => Promise<void>;
  onCancelJob: () => Promise<void>;
}) {
  const metadataEvent = props.result.streamEvents.find((event): event is Extract<ModelStreamEvent, { type: 'metadata' }> => event.type === 'metadata');
  const latestTraceId = metadataEvent?.traceId ?? props.result.job?.traceId;

  return (
    <div className="model-access-section">
      <div className="model-access-playground-grid">
        <form className="model-access-form" onSubmit={(event) => event.preventDefault()}>
          <div className="document-form-grid model-access-playground-form-grid">
            <label className="field">
              <span>{translate('auto.k2620')}<b>*</b></span>
              <input value={props.draft.projectId} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('projectId', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k2623')}</span>
              <input value={props.draft.applicationId} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('applicationId', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k2621')}</span>
              <input value={props.draft.environmentId} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('environmentId', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k2622')}</span>
              <input value={props.draft.promptKey} list="model-access-playground-prompt-keys" disabled={!props.canManage} onChange={(event) => props.onChangeDraft('promptKey', event.target.value)} />
              <datalist id="model-access-playground-prompt-keys">
                {Array.from(new Set(props.prompts.map((item) => item.promptKey))).map((key) => <option key={key} value={key} />)}
              </datalist>
            </label>
            <label className="field">
              <span>{translate('auto.k0905')}</span>
              <select value={props.draft.providerId} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('providerId', event.target.value)}>
                <option value="">{translate('auto.k1024')}</option>
                {props.providers.map((provider) => <option key={provider.id} value={provider.id}>{provider.name}</option>)}
              </select>
            </label>
            <label className="field">
              <span>{translate('auto.k2624')}</span>
              <input value={props.draft.modelName} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('modelName', event.target.value)} />
            </label>
            <label className="field">
              <span>{translate('auto.k0276')}</span>
              <select value={props.draft.sensitivityLevel} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('sensitivityLevel', event.target.value)}>
                <option value="PUBLIC">PUBLIC</option>
                <option value="INTERNAL">INTERNAL</option>
                <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                <option value="RESTRICTED">RESTRICTED</option>
              </select>
            </label>
            <label className="field">
              <span>{translate('auto.k0973')}</span>
              <input value={props.draft.capability} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('capability', event.target.value)} />
            </label>
            <label className="field model-access-checkbox-field">
              <span>{translate('auto.k1025')}</span>
              <input type="checkbox" checked={props.draft.allowPublicModel} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('allowPublicModel', event.target.checked)} />
            </label>
          </div>
          <label className="field document-content-field">
            <span>{translate('auto.k2625')}</span>
            <textarea value={props.draft.promptVariablesText} disabled={!props.canManage} onChange={(event) => props.onChangeDraft('promptVariablesText', event.target.value)} />
          </label>

          <div className="model-access-message-editor">
            <div className="panel-title-row">
              <h2>{translate('auto.k2626')}</h2>
              <button className="mini-button" type="button" disabled={!props.canManage} onClick={props.onAddMessage}>
                <Plus size={14} /> {translate('auto.k1026')}</button>
            </div>
            {props.draft.messages.map((message, index) => (
              <div className="model-access-message-row" key={message.id}>
                <label className="field">
                  <span>{translate('auto.k0247')}{index + 1}</span>
                  <select value={message.role} disabled={!props.canManage} onChange={(event) => props.onChangeMessage(message.id, 'role', event.target.value)}>
                    <option value="system">system</option>
                    <option value="user">user</option>
                    <option value="assistant">assistant</option>
                  </select>
                </label>
                <label className="field document-content-field">
                  <span>{translate('auto.k1013')}</span>
                  <textarea value={message.content} disabled={!props.canManage} onChange={(event) => props.onChangeMessage(message.id, 'content', event.target.value)} />
                </label>
                <button className="mini-button icon-only" type="button" title={translate('auto.k1027')} disabled={!props.canManage || props.draft.messages.length === 1} onClick={() => props.onRemoveMessage(message.id)}>
                  <XCircle size={14} />
                </button>
              </div>
            ))}
          </div>

          <div className="document-actions">
            <button className="primary-button" type="button" disabled={!props.canManage || props.state.loading} onClick={() => void props.onRun('sync')}>
              <PlayCircle size={16} /> {translate('auto.k1028')}</button>
            <button className="secondary-button" type="button" disabled={!props.canManage || props.state.loading} onClick={() => void props.onRun('stream')}>
              <Activity size={16} /> {translate('auto.k1029')}</button>
            <button className="secondary-button" type="button" disabled={!props.canManage || props.state.loading} onClick={() => void props.onRun('async')}>
              <RefreshCw size={16} /> {translate('auto.k1030')}</button>
            <button className="secondary-button" type="button" onClick={props.onReset}>{translate('auto.k0254')}</button>
            <StateLine state={props.state} />
          </div>
        </form>

        <div className="model-access-playground-result">
          <div className="panel-title-row">
            <h2>{translate('auto.k0219')}</h2>
            <span className="table-secondary">{props.result.mode ? translate('auto.k1031', { value0: props.result.mode }) : translate('auto.k1032')}</span>
          </div>
          <div className="model-access-summary-grid">
            <StatusMetric label={translate('auto.k2627')} value={props.result.response?.invocationId ?? props.result.job?.invocationId ?? '-'} />
            <StatusMetric label={translate('auto.k0905')} value={props.result.response?.providerName ?? '-'} />
            <StatusMetric label={translate('auto.k2624')} value={props.result.response?.modelName ?? '-'} />
            <StatusMetric label={translate('auto.k2628')} value={props.result.response?.fallbackUsed ? translate('auto.k2630') : translate('auto.k2631')} tone={props.result.response?.fallbackUsed ? 'pending' : 'neutral'} />
            <StatusMetric label={translate('auto.k2629')} value={props.result.response ? `${props.result.response.inputTokens}/${props.result.response.outputTokens}` : '-'} />
            <StatusMetric label={translate('auto.k0983')} value={props.result.response ? formatMoney(props.result.response.totalCost) : '-'} />
          </div>

          {props.result.job && (
            <div className="model-access-async-job">
              <div className="panel-title-row">
                <h2>{translate('auto.k1030')}</h2>
                <StatusPill value={props.result.job.status} />
              </div>
              <div className="document-actions">
                <button className="secondary-button" type="button" disabled={props.state.loading} onClick={() => void props.onRefreshJob()}>
                  <RefreshCw size={15} /> {translate('auto.k1033')}</button>
                <button
                  className="secondary-button"
                  type="button"
                  disabled={props.state.loading || !['QUEUED', 'RUNNING'].includes(String(props.result.job.status))}
                  onClick={() => void props.onCancelJob()}
                >
                  <XCircle size={15} /> {translate('auto.k1034')}</button>
              </div>
              <div className="model-access-job-meta">
                <span>jobId: {props.result.job.jobId}</span>
                <span>traceId: {props.result.job.traceId ?? '-'}</span>
                <span>finishedAt: {formatDateTime(props.result.job.finishedAt)}</span>
              </div>
              {props.result.job.errorCode && (
                <div className="document-state-line error">{props.result.job.errorCode}: {props.result.job.errorMessage ?? '-'}</div>
              )}
            </div>
          )}

          <label className="field document-content-field">
            <span>{translate('auto.k1013')}</span>
            <textarea value={props.result.streamContent || props.result.response?.content || ''} readOnly />
          </label>

          <div className="model-access-stream-events">
            <div className="panel-title-row">
              <h2>{translate('auto.k1035')}</h2>
              <span className="table-secondary">{latestTraceId ? `Trace ID：${latestTraceId}` : '-'}</span>
            </div>
            <div className="table-wrap model-access-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{translate('auto.k0895')}</th>
                    <th>{translate('auto.k1013')}</th>
                  </tr>
                </thead>
                <tbody>
                  {props.result.streamEvents.length ? props.result.streamEvents.map((event, index) => (
                    <tr key={`${event.type}-${index}`}>
                      <td><StatusPill value={event.type.toUpperCase()} /></td>
                      <td><code className="inline-code-block">{JSON.stringify(event)}</code></td>
                    </tr>
                  )) : (
                    <tr><td className="table-empty" colSpan={2}>{translate('auto.k1036')}</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function QualityTab(props: {
  selectedTaskType: (typeof qualityTaskTypeOptions)[number];
  summary: ModelQualityEvaluationSummary | null;
  state: WorkState;
  onChangeTaskType: (value: (typeof qualityTaskTypeOptions)[number]) => void;
  onRefresh: () => void;
}) {
  return (
    <div className="model-access-section">
      <form className="asset-filter-bar model-access-quality-filter" onSubmit={(event) => {
        event.preventDefault();
        props.onRefresh();
      }}>
        <label className="field">
          <span>{translate('auto.k1037')}</span>
          <select value={props.selectedTaskType} onChange={(event) => props.onChangeTaskType(event.target.value as (typeof qualityTaskTypeOptions)[number])}>
            {qualityTaskTypeOptions.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
        <div className="asset-filter-actions">
          <button className="secondary-button" type="submit"><RefreshCw size={15} /> {translate('auto.k0170')}</button>
        </div>
      </form>

      <div className="model-access-summary-grid">
        <StatusMetric label={translate('auto.k1038')} value={props.summary?.corpusVersion ?? '-'} />
        <StatusMetric label={translate('auto.k1039')} value={props.summary?.scenarioCount ?? 0} />
        <StatusMetric label={translate('auto.k1040')} value={formatPercent(props.summary?.totalStats.scenarioPassRate)} tone={props.summary?.totalStats.passed ? 'positive' : 'negative'} />
        <StatusMetric label={translate('auto.k2632')} value={formatPercent(props.summary?.totalStats.requiredTermRecall)} tone={props.summary?.totalStats.passed ? 'positive' : 'pending'} />
        <StatusMetric label={translate('auto.k2633')} value={formatPercent(props.summary?.totalStats.forbiddenTermCleanRate)} tone={props.summary?.totalStats.passed ? 'positive' : 'negative'} />
        <StatusMetric label={translate('auto.k1041')} value={props.summary?.totalStats.passed ? 'PASS' : 'FAIL'} tone={props.summary?.totalStats.passed ? 'positive' : 'negative'} />
      </div>

      <StateLine state={props.state} />

      <div className="model-access-quality-meta">
        <div className="model-access-quality-chip-list">
          <strong>{translate('auto.k1042')}</strong>
          {(props.summary?.promptBindings ?? []).map((item) => <span className="status-pill neutral" key={item}>{item}</span>)}
        </div>
        <div className="model-access-quality-chip-list">
          <strong>{translate('auto.k2634')}</strong>
          {(props.summary?.providerGroups ?? []).map((item) => <span className="status-pill neutral" key={item}>{item}</span>)}
        </div>
      </div>

      <div className="model-access-quality-card-grid">
        {(props.summary?.taskStats ?? []).map((task) => (
          <div className="model-access-quality-card" key={task.taskType}>
            <div className="panel-title-row">
              <h2>{task.taskType}</h2>
              <StatusPill value={task.passed ? 'PASS' : 'FAIL'} />
            </div>
            <div className="model-access-summary-grid">
              <StatusMetric label={translate('auto.k1043')} value={`${task.passedScenarios}/${task.scenarioCount}`} tone={task.passed ? 'positive' : 'pending'} />
              <StatusMetric label={translate('auto.k1044')} value={formatPercent(task.scenarioPassRate)} />
              <StatusMetric label={translate('auto.k2632')} value={formatPercent(task.requiredTermRecall)} />
              <StatusMetric label={translate('auto.k2635')} value={formatPercent(task.forbiddenTermCleanRate)} />
              <StatusMetric label={translate('auto.k2636')} value={`${task.requiredTermMatches}/${task.requiredTermCount}`} />
              <StatusMetric label={translate('auto.k2637')} value={`${task.forbiddenTermMatches}/${task.forbiddenTermCount}`} />
            </div>
            {task.failures.length > 0 && (
              <div className="model-access-failure-list">
                {task.failures.slice(0, 6).map((failure) => <div className="model-access-failure-item" key={failure}>{failure}</div>)}
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>{translate('auto.k1045')}</th>
              <th>{translate('auto.k1046')}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{translate('auto.k1047')}</td>
              <td>{formatPercent(props.summary?.thresholds.minScenarioPassRate)}</td>
            </tr>
            <tr>
              <td>{translate('auto.k2638')}</td>
              <td>{formatPercent(props.summary?.thresholds.minRequiredTermRecall)}</td>
            </tr>
            <tr>
              <td>{translate('auto.k2639')}</td>
              <td>{formatPercent(props.summary?.thresholds.minForbiddenTermCleanRate)}</td>
            </tr>
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
        <label className="field"><span>{translate('auto.k2620')}</span><input value={props.filters.projectId} onChange={(event) => props.onChangeFilter('projectId', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k2623')}</span><input value={props.filters.applicationId} onChange={(event) => props.onChangeFilter('applicationId', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k2621')}</span><input value={props.filters.environmentId} onChange={(event) => props.onChangeFilter('environmentId', event.target.value)} /></label>
        <label className="field">
          <span>{translate('auto.k0276')}</span>
          <select value={props.filters.sensitivityLevel} onChange={(event) => props.onChangeFilter('sensitivityLevel', event.target.value)}>
            <option value="">{translate('auto.k0195')}</option>
            <option value="PUBLIC">PUBLIC</option>
            <option value="INTERNAL">INTERNAL</option>
            <option value="CONFIDENTIAL">CONFIDENTIAL</option>
            <option value="RESTRICTED">RESTRICTED</option>
          </select>
        </label>
        <label className="field">
          <span>{translate('auto.k0182')}</span>
          <select value={props.filters.status} onChange={(event) => props.onChangeFilter('status', event.target.value)}>
            <option value="">{translate('auto.k0195')}</option>
            {INVOCATION_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
          </select>
        </label>
        <label className="field">
          <span>{translate('auto.k0905')}</span>
          <select value={props.filters.providerId} onChange={(event) => props.onChangeFilter('providerId', event.target.value)}>
            <option value="">{translate('auto.k0195')}</option>
            {props.providers.map((provider) => <option key={provider.id} value={provider.id}>{provider.name}</option>)}
          </select>
        </label>
        <label className="field"><span>{translate('auto.k2640')}</span><input value={props.filters.actorService} onChange={(event) => props.onChangeFilter('actorService', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k2641')}</span><input value={props.filters.roleScope} onChange={(event) => props.onChangeFilter('roleScope', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k1048')}</span><input value={props.costFilters.projectId} onChange={(event) => props.onChangeCostFilter('projectId', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k1049')}</span><input value={props.costFilters.actorService} onChange={(event) => props.onChangeCostFilter('actorService', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k1050')}</span><input type="datetime-local" value={props.filters.startTime} onChange={(event) => props.onChangeFilter('startTime', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k1051')}</span><input type="datetime-local" value={props.filters.endTime} onChange={(event) => props.onChangeFilter('endTime', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k1052')}</span><input type="date" value={props.costFilters.startDate} onChange={(event) => props.onChangeCostFilter('startDate', event.target.value)} /></label>
        <label className="field"><span>{translate('auto.k1053')}</span><input type="date" value={props.costFilters.endDate} onChange={(event) => props.onChangeCostFilter('endDate', event.target.value)} /></label>
        <div className="asset-filter-actions">
          <button className="secondary-button" type="submit" disabled={props.state.loading}><Search size={15} /> {translate('auto.k0372')}</button>
          <button className="secondary-button" type="button" disabled={!props.canExport || props.exportState.loading} onClick={() => void props.onExport()}><Download size={15} /> CSV</button>
        </div>
      </form>

      <div className="model-access-summary-grid">
        <StatusMetric label={translate('auto.k1054')} value={props.summary?.total ?? 0} />
        <StatusMetric label={translate('auto.k0368')} value={props.summary?.succeeded ?? 0} tone="positive" />
        <StatusMetric label={translate('auto.k0369')} value={props.summary?.failed ?? 0} tone={(props.summary?.failed ?? 0) > 0 ? 'negative' : 'neutral'} />
        <StatusMetric label={translate('auto.k1000')} value={props.summary?.blocked ?? 0} tone={(props.summary?.blocked ?? 0) > 0 ? 'pending' : 'neutral'} />
        <StatusMetric label={translate('auto.k2629')} value={`${props.summary?.inputTokens ?? 0}/${props.summary?.outputTokens ?? 0}`} />
        <StatusMetric label={translate('auto.k0983')} value={formatMoney(props.summary?.totalCost ?? 0)} />
      </div>
      <StateLine state={props.state} />
      <StateLine state={props.exportState} />

      <div className="table-wrap model-access-table-wrap">
        <table>
          <thead>
            <tr>
              <th>{translate('auto.k1005')}</th>
              <th>{translate('auto.k0182')}</th>
              <th>{translate('auto.k0905')}</th>
              <th>{translate('auto.k2610')}</th>
              <th>{translate('auto.k1201')}</th>
              <th>{translate('auto.k0983')}</th>
              <th>{translate('auto.k0780')}</th>
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
                  <span className="table-secondary">{item.modelName ?? '-'} · {item.routingGroup ?? '-'} · {item.modelCapability ?? 'CHAT'}{item.fallbackUsed ? ` · ${translate('auto.k2628')}` : ''}</span>
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
                  <span className="table-secondary">{item.inputTokens}/{item.outputTokens} {translate('auto.k2629')} · {item.latencyMs}ms</span>
                </td>
                <td><span className="table-secondary">{item.errorCode ? `${item.errorCode}: ${item.errorMessage ?? ''}` : '-'}</span></td>
              </tr>
            )) : (
              <tr><td className="table-empty" colSpan={7}>{translate('auto.k1055')}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="model-access-cost-grid">
        <div className="model-access-cost-block">
          <div className="panel-title-row"><h2>{translate('auto.k1056')}</h2><Activity size={16} /></div>
          <div className="table-wrap model-access-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{translate('auto.k1057')}</th>
                  <th>{translate('auto.k0176')}</th>
                  <th>{translate('auto.k0285')}</th>
                  <th>{translate('auto.k0182')}</th>
                  <th>{translate('auto.k2629')}</th>
                  <th>{translate('auto.k0983')}</th>
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
                  <tr><td className="table-empty" colSpan={6}>{translate('auto.k1058')}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
        <div className="model-access-cost-block">
          <div className="panel-title-row"><h2>{translate('auto.k1059')}</h2><AlertTriangle size={16} /></div>
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
                <div><strong>{translate('auto.k1060')}</strong><span>{translate('auto.k1061')}</span></div>
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
  return <span className={`status-pill ${tone}`} title={value}>{statusText(value)}</span>;
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
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function validateProviderDraft(draft: ProviderDraft) {
  if (!draft.name.trim()) {
    return translate('auto.k1063');
  }
  if (!MODEL_PROVIDER_TYPES.includes(draft.providerType as (typeof MODEL_PROVIDER_TYPES)[number])) {
    return translate('auto.k1064');
  }
  if (draft.routingGroup.trim() && !/^[A-Za-z0-9_.:-]+$/.test(draft.routingGroup.trim())) {
    return translate('auto.k1065');
  }
  if (numberOrUndefined(draft.priority) === undefined || Number(draft.priority) < 0) {
    return translate('auto.k1066');
  }
  if (numberOrUndefined(draft.timeoutMs) === undefined || Number(draft.timeoutMs) < 100) {
    return translate('auto.k1067');
  }
  if ((numberOrUndefined(draft.inputCostPer1kTokens) ?? 0) < 0 || (numberOrUndefined(draft.outputCostPer1kTokens) ?? 0) < 0) {
    return translate('auto.k1068');
  }
  if (draft.providerType === 'OPENAI_COMPATIBLE') {
    if (!/^https?:\/\/.+/i.test(draft.baseUrl.trim())) {
      return translate('auto.k1069');
    }
    if (!/^env:[A-Z0-9_]+$/i.test(draft.apiKeyRef.trim())) {
      return translate('auto.k1070');
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
    return translate('auto.k1071');
  }
  if (scopeType !== 'PLATFORM' && !draft.scopeKey.trim()) {
    return translate('auto.k1072');
  }
  if (draft.scopeKey.trim() && !/^[A-Za-z0-9_.:@-]{1,128}$/.test(draft.scopeKey.trim())) {
    return translate('auto.k1073');
  }
  const dailyBudgetLimit = numberOrUndefined(draft.dailyBudgetLimit);
  if (dailyBudgetLimit !== undefined && dailyBudgetLimit < 0) {
    return translate('auto.k1074');
  }
  const warningRatio = numberOrUndefined(draft.costAlertWarningRatio);
  if (warningRatio !== undefined && (warningRatio <= 0 || warningRatio > 1)) {
    return translate('auto.k1075');
  }
  if (draft.routingGroup.trim() && !/^[A-Za-z0-9_.:-]{1,64}$/.test(draft.routingGroup.trim())) {
    return translate('auto.k1065');
  }
  if (draft.reason.length > 300) {
    return translate('auto.k1076');
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

function buildPlaygroundPayload(draft: PlaygroundDraft): { value: Parameters<typeof invokeModel>[0] } | { error: string } {
  if (!draft.projectId.trim()) {
    return { error: translate('auto.k1077') };
  }
  const messages = draft.messages
    .map((message) => ({ role: message.role.trim(), content: message.content.trim() }))
    .filter((message) => message.role && message.content);
  if (!messages.length) {
    return { error: translate('auto.k1078') };
  }

  let promptVariables: Record<string, string> | undefined;
  if (draft.promptVariablesText.trim()) {
    try {
      const raw = JSON.parse(draft.promptVariablesText);
      if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
        return { error: translate('auto.k1079') };
      }
      promptVariables = Object.fromEntries(
        Object.entries(raw as Record<string, unknown>).map(([key, value]) => [key, value == null ? '' : String(value)])
      );
    } catch {
      return { error: translate('auto.k1080') };
    }
  }

  return {
    value: {
      projectId: draft.projectId.trim(),
      applicationId: draft.applicationId.trim() || undefined,
      environmentId: draft.environmentId.trim() || undefined,
      promptKey: draft.promptKey.trim() || undefined,
      promptVariables,
      messages,
      providerId: draft.providerId || undefined,
      modelName: draft.modelName.trim() || undefined,
      allowPublicModel: draft.allowPublicModel,
      sensitivityLevel: draft.sensitivityLevel.trim() || undefined,
      capability: draft.capability.trim() || undefined
    }
  };
}

function createPlaygroundMessage(role = 'user', content = ''): PlaygroundMessageDraft {
  return {
    id: `msg-${Math.random().toString(36).slice(2, 10)}`,
    role,
    content
  };
}

function switchText(value?: boolean) {
  if (value === undefined) return '-';
  return value ? translate('auto.k0993') : translate('auto.k2616');
}

function statusText(value?: string) {
  const normalized = String(value ?? 'UNKNOWN').toUpperCase();
  const mapping: Record<string, string> = {
    ACTIVE: translate('auto.k1011'),
    APPROVED: translate('auto.k1022'),
    BLOCKED: translate('auto.k1000'),
    CLOSED: translate('auto.k2642'),
    CRITICAL: translate('auto.k0369'),
    DISABLED: translate('auto.k0994'),
    DOWN: translate('auto.k0094'),
    DRAFT: translate('auto.k2345'),
    ENABLED: translate('auto.k0993'),
    ERROR: translate('auto.k0094'),
    FAILED: translate('auto.k0369'),
    FAIL: translate('auto.k0369'),
    INFO: translate('auto.k2643'),
    OK: translate('auto.k0095'),
    OPEN: translate('auto.k2644'),
    PASS: translate('auto.k1022'),
    QUEUED: translate('auto.k2646'),
    REJECTED: translate('auto.k2647'),
    RUNNING: translate('auto.k2648'),
    SUCCEEDED: translate('auto.k0368'),
    UNKNOWN: translate('auto.k0903'),
    UP: translate('auto.k0095'),
    WARN: translate('auto.k2645'),
    WARNING: translate('auto.k2645')
  };
  return mapping[normalized] ?? value ?? 'UNKNOWN';
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

function formatPercent(value?: number) {
  return `${((value ?? 0) * 100).toFixed(1)}%`;
}

function formatOptionalBoolean(value?: boolean) {
  if (value === true) {
    return translate('auto.k0993');
  }
  if (value === false) {
    return translate('auto.k0994');
  }
  return translate('auto.k0992');
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
