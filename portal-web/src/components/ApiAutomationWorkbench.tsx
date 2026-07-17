import {
  AlertTriangle,
  CheckCircle2,
  ClipboardCheck,
  FileText,
  Download,
  Square,
  Play,
  ListChecks,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Send,
  Upload
} from 'lucide-react';
import { Drawer, Tabs } from 'antd';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/auth';
import {
  cancelApiAutomationRun,
  createApiAutomationGenerationTask,
  createApiAutomationRun,
  createApiAutomationSpec,
  exportApiAutomationRun,
  approveApiAutomationScriptBundle,
  fetchApiAutomationDiff,
  fetchApiAutomationGenerationTask,
  fetchApiAutomationGenerationTasks,
  fetchApiAutomationHealth,
  fetchApiAutomationSpec,
  fetchApiAutomationSpecs,
  generateApiAutomationScriptBundle,
  parseApiAutomationSpec,
  rejectApiAutomationScriptBundle,
  submitApiAutomationScriptBundleReview,
  syncApiAutomationSpec,
  type ApiAutomationEndpointSnapshot,
  type ApiAutomationGenerationTask,
  type ApiAutomationGenerationTaskDetail,
  type ApiAutomationHealth,
  type ApiAutomationRunDetail,
  type ApiAutomationRunExport,
  type ApiAutomationScriptBundle,
  type ApiAutomationSpec,
  type ApiAutomationSpecDetail,
  type ApiAutomationSyncResponse
} from '../api/apiAutomation';
import { canUseButton, hasPermission } from '../permissions';
import { dictionaryLabel, displayValueLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { PageHeader } from './PageHeader';
import { CheckboxControl, InputControl, SelectControl, TextAreaControl } from './ui';

const DIFF_STATUS_OPTIONS = ['ALL', 'NEW', 'CHANGED', 'MATCHED', 'CONFLICT', 'SKIPPED', 'UNKNOWN'] as const;

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
};

type SpecDraft = {
  projectId: string;
  name: string;
  versionLabel: string;
  sourceRef: string;
  content: string;
};

const initialDraft: SpecDraft = {
  projectId: '',
  name: '',
  versionLabel: '',
  sourceRef: '',
  content: ''
};

/**
 * API 自动化子页面：用例管理 / 套件编排 / 执行记录，
 * 通过顶部 Tabs + 嵌套路由切换，状态仍由本组件统一持有。
 */
const apiAutomationSubPages = [
  { key: 'cases', label: translate('nav.apiCases') },
  { key: 'suites', label: translate('nav.apiSuites') },
  { key: 'runs', label: translate('nav.apiRuns') }
] as const;

type ApiAutomationSubPage = (typeof apiAutomationSubPages)[number]['key'];

function resolveApiAutomationSubPage(pathname: string): ApiAutomationSubPage {
  const segment = pathname.replace(/^\/+/, '').split('/')[1] ?? '';
  return (apiAutomationSubPages.some((page) => page.key === segment) ? segment : 'cases') as ApiAutomationSubPage;
}

export function ApiAutomationWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'apiAutomation:read');
  const canImport = canUseButton(props.currentUser, 'apiAutomation:import');
  const canGenerate = canUseButton(props.currentUser, 'apiAutomation:generate');
  const canReview = canUseButton(props.currentUser, 'apiAutomation:review');
  const canExecute = canUseButton(props.currentUser, 'apiAutomation:execute');
  const canExport = canUseButton(props.currentUser, 'apiAutomation:export');

  const location = useLocation();
  const navigate = useNavigate();
  const activeSubPage = resolveApiAutomationSubPage(location.pathname);
  const subPageTabs = useMemo(() => apiAutomationSubPages.map((page) => ({ key: page.key, label: page.label })), []);

  const [health, setHealth] = useState<ApiAutomationHealth | null>(null);
  const [specs, setSpecs] = useState<ApiAutomationSpec[]>([]);
  const [selectedSpecId, setSelectedSpecId] = useState('');
  const [detail, setDetail] = useState<ApiAutomationSpecDetail | null>(null);
  const [draft, setDraft] = useState<SpecDraft>(initialDraft);
  const [importDrawerOpen, setImportDrawerOpen] = useState(false);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [importState, setImportState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [parseState, setParseState] = useState<WorkState>({ loading: false });
  const [diffState, setDiffState] = useState<WorkState>({ loading: false });
  const [syncState, setSyncState] = useState<WorkState>({ loading: false });
  const [generationState, setGenerationState] = useState<WorkState>({ loading: false });
  const [scriptBundleState, setScriptBundleState] = useState<WorkState>({ loading: false });
  const [runState, setRunState] = useState<WorkState>({ loading: false });
  const [runExportState, setRunExportState] = useState<WorkState>({ loading: false });
  const [lastSync, setLastSync] = useState<ApiAutomationSyncResponse | null>(null);
  const [lastGeneration, setLastGeneration] = useState<ApiAutomationGenerationTaskDetail | null>(null);
  const [generationHistory, setGenerationHistory] = useState<ApiAutomationGenerationTask[]>([]);
  const [lastRun, setLastRun] = useState<ApiAutomationRunDetail | null>(null);
  const [lastRunExport, setLastRunExport] = useState<ApiAutomationRunExport | null>(null);
  const [generationAssetTestCaseIds, setGenerationAssetTestCaseIds] = useState('');
  const [generationMode, setGenerationMode] = useState<'MODEL_WITH_FALLBACK' | 'FALLBACK_ONLY'>('MODEL_WITH_FALLBACK');
  const [diffStatusFilter, setDiffStatusFilter] = useState<(typeof DIFF_STATUS_OPTIONS)[number]>('ALL');
  const [selectedAssetApiIds, setSelectedAssetApiIds] = useState<string[]>([]);
  const [reviewNote, setReviewNote] = useState('');
  const [runBaseUrl, setRunBaseUrl] = useState('');
  const [runEnvironmentId, setRunEnvironmentId] = useState('');
  const [runCaseIds, setRunCaseIds] = useState('');
  const [runSecretRefs, setRunSecretRefs] = useState('');

  const summary = useMemo(() => {
    const parsed = specs.filter((spec) => spec.status === 'PARSED').length;
    const failed = specs.filter((spec) => spec.status === 'PARSE_FAILED').length;
    const endpoints = specs.reduce((total, spec) => total + spec.endpointCount, 0);
    return { parsed, failed, endpoints };
  }, [specs]);

  const refreshSpecs = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setSpecs([]);
      setDetail(null);
      setSelectedSpecId('');
      return;
    }
    setLoadState({ loading: true });
    try {
      const [healthResult, specsResult] = await Promise.all([
        fetchApiAutomationHealth(),
        fetchApiAutomationSpecs({ size: 50 })
      ]);
      setHealth(healthResult.data);
      setSpecs(specsResult.data.items);
      setLoadState({ loading: false });
    } catch (error: unknown) {
      setLoadState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0049') });
    }
  }, [canRead, props.signedIn]);

  const refreshDetail = useCallback(async (id: string) => {
    if (!id || !canRead) {
      setDetail(null);
      return;
    }
    setDetailState({ loading: true });
    try {
      const result = await fetchApiAutomationSpec(id);
      setDetail(result.data);
      setDetailState({ loading: false });
    } catch (error: unknown) {
      setDetailState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0049') });
    }
  }, [canRead]);

  const refreshGenerationHistory = useCallback(async (projectId: string, specId: string) => {
    if (!projectId || !specId || !canRead) {
      setGenerationHistory([]);
      return;
    }
    try {
      const result = await fetchApiAutomationGenerationTasks({ projectId, specId, size: 8 });
      setGenerationHistory(result.data.items);
    } catch {
      setGenerationHistory([]);
    }
  }, [canRead]);

  useEffect(() => {
    void refreshSpecs();
  }, [refreshSpecs]);

  useEffect(() => {
    if (selectedSpecId) {
      void refreshDetail(selectedSpecId);
    }
  }, [refreshDetail, selectedSpecId]);

  useEffect(() => {
    setDiffStatusFilter('ALL');
    setSelectedAssetApiIds([]);
  }, [selectedSpecId]);

  useEffect(() => {
    if (detail) {
      void refreshGenerationHistory(detail.spec.projectId, detail.spec.id);
    } else {
      setGenerationHistory([]);
    }
  }, [detail, refreshGenerationHistory]);

  useEffect(() => {
    const available = new Set((detail?.endpoints ?? []).map((endpoint) => endpoint.assetApiId).filter(Boolean) as string[]);
    setSelectedAssetApiIds((current) => current.filter((id) => available.has(id)));
  }, [detail?.endpoints]);

  async function onImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canImport) return;
    if (!draft.projectId.trim() || !draft.name.trim() || !draft.content.trim()) {
      setImportState({ loading: false, error: translate('auto.k0142') });
      return;
    }
    setImportState({ loading: true });
    try {
      const result = await createApiAutomationSpec({
        projectId: draft.projectId.trim(),
        sourceType: 'TEXT',
        name: draft.name.trim(),
        versionLabel: optionalText(draft.versionLabel),
        sourceRef: optionalText(draft.sourceRef),
        content: draft.content
      });
      setDraft(initialDraft);
      setImportDrawerOpen(false);
      setSelectedSpecId(result.data.spec.id);
      setDetail(result.data);
      setImportState({ loading: false, success: translate('auto.k0143') });
      await refreshSpecs();
    } catch (error: unknown) {
      setImportState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0144') });
    }
  }

  async function onReparse() {
    if (!selectedSpecId || !canImport) return;
    setParseState({ loading: true });
    try {
      const result = await parseApiAutomationSpec(selectedSpecId);
      setDetail(result.data);
      setParseState({ loading: false, success: translate('auto.k0145') });
      setLastSync(null);
      setLastGeneration(null);
      setLastRun(null);
      setLastRunExport(null);
      await refreshSpecs();
    } catch (error: unknown) {
      setParseState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0146') });
    }
  }

  async function onRefreshDiff() {
    if (!selectedSpecId || !canRead) return;
    setDiffState({ loading: true });
    try {
      const result = await fetchApiAutomationDiff(selectedSpecId);
      setDetail((current) => current ? { ...current, endpoints: result.data.endpoints } : current);
      setDiffState({ loading: false, success: diffSummaryText(result.data.counts) });
    } catch (error: unknown) {
      setDiffState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0147') });
    }
  }

  async function onSync() {
    if (!selectedSpecId || !canImport) return;
    setSyncState({ loading: true });
    try {
      const result = await syncApiAutomationSpec(selectedSpecId);
      setLastSync(result.data);
      setLastGeneration(null);
      setLastRun(null);
      setLastRunExport(null);
      setDetail((current) => current ? { ...current, endpoints: result.data.endpoints } : current);
      setSyncState({ loading: false, success: syncSummaryText(result.data.counts) });
      await refreshSpecs();
    } catch (error: unknown) {
      setSyncState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0148') });
    }
  }

  async function onGenerateCases() {
    if (!selectedSpecId || !detail || !canGenerate) return;
    setGenerationState({ loading: true });
    try {
      const assetTestCaseIds = parseIdList(generationAssetTestCaseIds);
      const modelReady = Boolean(health?.policy?.modelGenerationReady);
      const effectiveGenerationMode = generationMode === 'MODEL_WITH_FALLBACK' && modelReady
        ? 'MODEL_WITH_FALLBACK'
        : 'FALLBACK_ONLY';
      const result = await createApiAutomationGenerationTask({
        projectId: detail.spec.projectId,
        specId: selectedSpecId,
        assetApiIds: selectedAssetApiIds.length ? selectedAssetApiIds : undefined,
        assetTestCaseIds: assetTestCaseIds.length ? assetTestCaseIds : undefined,
        coverageTypes: ['SMOKE', 'EXCEPTION'],
        generationMode: effectiveGenerationMode,
        caseCountPerApi: 2
      });
      setLastGeneration(result.data);
      setLastRun(null);
      setLastRunExport(null);
      setReviewNote('');
      setGenerationState({ loading: false, success: generationSummaryText(result.data) });
      await refreshGenerationHistory(detail.spec.projectId, selectedSpecId);
    } catch (error: unknown) {
      setGenerationState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0149') });
    }
  }

  async function onLoadGenerationTask(taskId: string) {
    if (!taskId || !canRead) return;
    setGenerationState({ loading: true });
    try {
      const result = await fetchApiAutomationGenerationTask(taskId);
      setLastGeneration(result.data);
      setLastRun(null);
      setLastRunExport(null);
      setReviewNote('');
      setGenerationState({ loading: false, success: generationSummaryText(result.data) });
    } catch (error: unknown) {
      setGenerationState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0150') });
    }
  }

  async function onGenerateScriptBundle() {
    if (!lastGeneration || !canGenerate) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await generateApiAutomationScriptBundle(lastGeneration.task.id);
      mergeScriptBundle(result.data, translate('auto.k0151'));
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0152') });
    }
  }

  async function onSubmitBundleReview(bundle: ApiAutomationScriptBundle) {
    if (!canReview) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await submitApiAutomationScriptBundleReview(bundle.id, { note: optionalText(reviewNote) });
      mergeScriptBundle(result.data, translate('auto.k0153'));
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0154') });
    }
  }

  async function onApproveBundle(bundle: ApiAutomationScriptBundle) {
    if (!canReview) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await approveApiAutomationScriptBundle(bundle.id, { note: optionalText(reviewNote) });
      mergeScriptBundle(result.data, translate('auto.k0155'));
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0156') });
    }
  }

  async function onRejectBundle(bundle: ApiAutomationScriptBundle) {
    if (!canReview || !reviewNote.trim()) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await rejectApiAutomationScriptBundle(bundle.id, { note: reviewNote.trim() });
      mergeScriptBundle(result.data, translate('auto.k0157'));
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0158') });
    }
  }

  async function onCreateRun(bundle: ApiAutomationScriptBundle) {
    if (!canExecute) return;
    if (!runBaseUrl.trim()) {
      setRunState({ loading: false, error: translate('auto.k0159') });
      return;
    }
    setRunState({ loading: true });
    try {
      const caseIds = parseIdList(runCaseIds);
      const secretRefs = parseIdList(runSecretRefs);
      const result = await createApiAutomationRun({
        bundleId: bundle.id,
        environmentId: optionalText(runEnvironmentId),
        baseUrl: runBaseUrl.trim(),
        caseIds: caseIds.length ? caseIds : undefined,
        secretRefs: secretRefs.length ? secretRefs : undefined
      });
      setLastRun(result.data);
      setLastRunExport(null);
      setRunState({ loading: false, success: runSummaryText(result.data) });
    } catch (error: unknown) {
      setRunState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0160') });
    }
  }

  async function onExportRun(run: ApiAutomationRunDetail) {
    if (!canExport) return;
    setRunExportState({ loading: true });
    try {
      const result = await exportApiAutomationRun(run.run.id);
      setLastRunExport(result.data);
      setRunExportState({ loading: false, success: translate('auto.k0161', { value0: result.data.schemaVersion, value1: result.data.results.length }) });
    } catch (error: unknown) {
      setRunExportState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0062') });
    }
  }

  async function onCancelRun(run: ApiAutomationRunDetail) {
    if (!canExecute || !activeRunStatus(run.run.status)) return;
    setRunState({ loading: true });
    try {
      const result = await cancelApiAutomationRun(run.run.id);
      setLastRun(result.data);
      setRunState({ loading: false, success: runSummaryText(result.data) });
    } catch (error: unknown) {
      setRunState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0162') });
    }
  }

  if (!props.signedIn || !canRead) {
    return (
      <section className="panel">
        <div className="empty-state">
          <ShieldCheck className="empty-state-icon" />
          <strong>{translate('auto.k0163')}</strong>
          <span>{translate('auto.k0164')}</span>
        </div>
      </section>
    );
  }

  return (
    <>
      <PageHeader title={translate('auto.k0009')} description={translate('auto.k0010')} />
      <div className="module-tabs-card">
        <Tabs
          activeKey={activeSubPage}
          items={subPageTabs}
          onChange={(key) => navigate(`/api-automation/${key}`)}
        />
      </div>
    <section className="api-automation-console">
      <div className="metric-grid">
        <MetricCard label={translate('auto.k0165')} value={String(specs.length)} icon={<FileText size={18} />} />
        <MetricCard label={translate('auto.k0166')} value={String(summary.parsed)} icon={<CheckCircle2 size={18} />} />
        <MetricCard label={fieldLabel('endpoint')} value={String(summary.endpoints)} icon={<ListChecks size={18} />} />
        <MetricCard label={translate('auto.k0146')} value={String(summary.failed)} icon={<AlertTriangle size={18} />} />
      </div>

      {activeSubPage === 'cases' && (
      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">{translate('auto.k0167')}</div>
            <div className="panel-desc">
              {health ? `${health.service} · ${health.status}` : loadState.loading ? translate('auto.k0168') : translate('auto.k0169')}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshSpecs()} disabled={loadState.loading}>
            <RefreshCw size={15} />
            {translate('auto.k0170')}</button>
        </div>
        <div className="panel-body compact">
          {loadState.error && <div className="document-state-line error">{loadState.error}</div>}
          <div className="api-automation-policy-grid">
            <PolicyItem label="OpenAPI" value={health?.supportedOpenApiVersions.join(', ') || '-'} />
            <PolicyItem label={translate('auto.k0171')} value={health ? `${Math.round(health.specMaxBytes / 1024)} KB` : '-'} />
            <PolicyItem label={translate('auto.k0172')} value={String(health?.endpointMaxCount ?? '-')} />
            <PolicyItem label="runner" value={health?.runnerEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="promptKey" value={health?.promptKey ?? '-'} />
            <PolicyItem label={translate('auto.k0173')} value={health?.policy?.['urlFetchEnabled'] ? 'ENABLED' : 'DISABLED'} />
          </div>
        </div>
      </section>
      )}

      {activeSubPage === 'cases' && (
      <section className="api-automation-layout">
        <Drawer
          className="api-automation-import-drawer"
          destroyOnHidden
          maskClosable={!importState.loading}
          open={importDrawerOpen}
          placement="right"
          title={translate('auto.k0174')}
          width={760}
          onClose={() => {
            if (!importState.loading) {
              setImportDrawerOpen(false);
            }
          }}
        >
          <form className="document-form document-drawer-form" onSubmit={onImport}>
            <div className="form-grid">
              <Field label={translate('auto.k0176')}>
                <InputControl value={draft.projectId} onChange={(event) => setDraftValue('projectId', event.target.value)} />
              </Field>
              <Field label={translate('auto.k0177')}>
                <InputControl value={draft.name} onChange={(event) => setDraftValue('name', event.target.value)} />
              </Field>
              <Field label={translate('auto.k0178')}>
                <InputControl value={draft.versionLabel} onChange={(event) => setDraftValue('versionLabel', event.target.value)} />
              </Field>
              <Field label={translate('auto.k0179')}>
                <InputControl value={draft.sourceRef} onChange={(event) => setDraftValue('sourceRef', event.target.value)} />
              </Field>
            </div>
            <Field label="openApi">
              <TextAreaControl
                className="api-automation-spec-textarea"
                value={draft.content}
                onChange={(event) => setDraftValue('content', event.target.value)}
              />
            </Field>
            {importState.error && <div className="document-state-line error">{importState.error}</div>}
            {importState.success && <div className="document-state-line success">{importState.success}</div>}
            <div className="document-actions">
              <button className="btn btn-primary" type="submit" disabled={!canImport || importState.loading}>
                <Upload size={16} />
                {translate('auto.k0175')}</button>
              <button className="btn btn-secondary" type="button" disabled={importState.loading} onClick={() => setImportDrawerOpen(false)}>
                {translate('actions.cancel')}</button>
            </div>
          </form>
        </Drawer>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">{translate('auto.k0180')}</div>
              <div className="panel-desc">{specs.length} {translate('auto.k0181')}</div>
            </div>
            <button className="btn btn-primary btn-sm" type="button" disabled={!canImport || importState.loading} onClick={() => setImportDrawerOpen(true)}>
              <Upload size={15} />
              {translate('auto.k0175')}
            </button>
          </div>
          <div className="panel-body compact">
            {importState.error && <div className="document-state-line error">{importState.error}</div>}
            {importState.success && <div className="document-state-line success">{importState.success}</div>}
            <div className="table-wrap api-automation-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{translate('auto.k0177')}</th>
                    <th>{translate('auto.k0176')}</th>
                    <th>{translate('auto.k0182')}</th>
                    <th>{fieldLabel('endpoint')}</th>
                  </tr>
                </thead>
                <tbody>
                  {specs.length ? specs.map((spec) => (
                    <tr
                      key={spec.id}
                      className={selectedSpecId === spec.id ? 'selected-row' : undefined}
                      onClick={() => setSelectedSpecId(spec.id)}
                    >
                      <td>
                        <span className="table-primary">{spec.name}</span>
                        <span className="table-secondary">{spec.versionLabel ?? spec.sourceType}</span>
                      </td>
                      <td><span className="table-secondary">{spec.projectId}</span></td>
                      <td><StatusBadge status={spec.status} /></td>
                      <td>{spec.endpointCount}</td>
                    </tr>
                  )) : (
                    <tr><td className="table-empty" colSpan={4}>{loadState.loading ? translate('auto.k0168') : translate('auto.k0183')}</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </section>
      )}

      {activeSubPage === 'cases' && (
      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">端点快照</div>
            <div className="panel-desc">{detail ? translate('auto.k0184', { value0: detail.spec.name, value1: detail.endpoints.length }) : translate('auto.k0185')}</div>
          </div>
          <div className="api-automation-panel-actions">
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onRefreshDiff()} disabled={!selectedSpecId || diffState.loading}>
              <RefreshCw size={15} />
              差异
            </button>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onSync()} disabled={!selectedSpecId || !canImport || syncState.loading}>
              <Upload size={15} />
              {translate('auto.k0186')}</button>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onGenerateCases()} disabled={!selectedSpecId || !canGenerate || generationState.loading}>
              <ListChecks size={15} />
              {translate('auto.k0187')}</button>
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onReparse()} disabled={!selectedSpecId || !canImport || parseState.loading}>
              <RotateCcw size={15} />
              {translate('auto.k0188')}</button>
          </div>
        </div>
        <div className="panel-body compact">
          <div className="form-grid">
            <Field label={translate('auto.k0189')}>
              <SelectControl
                value={Boolean(health?.policy?.modelGenerationReady) ? generationMode : 'FALLBACK_ONLY'}
                onChange={(event) => setGenerationMode(event.target.value as 'MODEL_WITH_FALLBACK' | 'FALLBACK_ONLY')}
                disabled={!canGenerate || generationState.loading || !health}
              >
                {Boolean(health?.policy?.modelGenerationReady) && <option value="MODEL_WITH_FALLBACK">{translate('auto.k0190')}</option>}
                <option value="FALLBACK_ONLY">{translate('auto.k0191')}</option>
              </SelectControl>
            </Field>
            <Field label={translate('auto.k0192')}>
              <InputControl
                value={generationAssetTestCaseIds}
                onChange={(event) => setGenerationAssetTestCaseIds(event.target.value)}
                placeholder={translate('auto.k0193')}
                disabled={!canGenerate || generationState.loading}
              />
            </Field>
            <Field label={translate('auto.k0194')}>
              <SelectControl
                value={diffStatusFilter}
                onChange={(event) => setDiffStatusFilter(event.target.value as (typeof DIFF_STATUS_OPTIONS)[number])}
                disabled={!detail || detailState.loading}
              >
                {DIFF_STATUS_OPTIONS.map((status) => (
                  <option value={status} key={status}>{status === 'ALL' ? translate('auto.k0195') : dictionaryLabel(status)}</option>
                ))}
              </SelectControl>
            </Field>
          </div>
          <GenerationScopeSummary
            endpoints={detail?.endpoints ?? []}
            selectedAssetApiIds={selectedAssetApiIds}
            onSelectAll={() => setSelectedAssetApiIds(selectableAssetApiIds(detail?.endpoints ?? []))}
            onClear={() => setSelectedAssetApiIds([])}
          />
          {detailState.error && <div className="document-state-line error">{detailState.error}</div>}
          {parseState.error && <div className="document-state-line error">{parseState.error}</div>}
          {parseState.success && <div className="document-state-line success">{parseState.success}</div>}
          {diffState.error && <div className="document-state-line error">{diffState.error}</div>}
          {diffState.success && <div className="document-state-line success">{diffState.success}</div>}
          {syncState.error && <div className="document-state-line error">{syncState.error}</div>}
          {syncState.success && <div className="document-state-line success">{syncState.success}</div>}
          {generationState.error && <div className="document-state-line error">{generationState.error}</div>}
          {generationState.success && <div className="document-state-line success">{generationState.success}</div>}
          {scriptBundleState.error && <div className="document-state-line error">{scriptBundleState.error}</div>}
          {scriptBundleState.success && <div className="document-state-line success">{scriptBundleState.success}</div>}
          {runState.error && <div className="document-state-line error">{runState.error}</div>}
          {runState.success && <div className="document-state-line success">{runState.success}</div>}
          {runExportState.error && <div className="document-state-line error">{runExportState.error}</div>}
          {runExportState.success && <div className="document-state-line success">{runExportState.success}</div>}
          {lastSync && <SyncSummary sync={lastSync} />}
          <EndpointTable
            endpoints={filteredEndpoints(detail?.endpoints ?? [], diffStatusFilter)}
            loading={detailState.loading}
            selectedAssetApiIds={selectedAssetApiIds}
            onToggleAssetApiId={toggleSelectedAssetApiId}
          />
        </div>
      </section>
      )}

      {activeSubPage === 'suites' && (
      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">{translate('nav.apiSuites')}</div>
            <div className="panel-desc">{translate('auto.k0210')}</div>
          </div>
        </div>
        <div className="panel-body compact">
          {generationState.error && <div className="document-state-line error">{generationState.error}</div>}
          {generationState.success && <div className="document-state-line success">{generationState.success}</div>}
          {scriptBundleState.error && <div className="document-state-line error">{scriptBundleState.error}</div>}
          {scriptBundleState.success && <div className="document-state-line success">{scriptBundleState.success}</div>}
          {runState.error && <div className="document-state-line error">{runState.error}</div>}
          {runState.success && <div className="document-state-line success">{runState.success}</div>}
          {runExportState.error && <div className="document-state-line error">{runExportState.error}</div>}
          {runExportState.success && <div className="document-state-line success">{runExportState.success}</div>}
          <GenerationHistory
            tasks={generationHistory}
            selectedTaskId={lastGeneration?.task.id}
            loading={generationState.loading}
            onLoad={(taskId) => void onLoadGenerationTask(taskId)}
          />
          {lastGeneration ? (
            <GenerationSummary
              generation={lastGeneration}
              health={health}
              canGenerate={canGenerate}
              canReview={canReview}
              canExecute={canExecute}
              canExport={canExport}
              loading={scriptBundleState.loading}
              runLoading={runState.loading}
              runExportLoading={runExportState.loading}
              reviewNote={reviewNote}
              runBaseUrl={runBaseUrl}
              runEnvironmentId={runEnvironmentId}
              runCaseIds={runCaseIds}
              runSecretRefs={runSecretRefs}
              lastRun={lastRun}
              lastRunExport={lastRunExport}
              onReviewNoteChange={setReviewNote}
              onRunBaseUrlChange={setRunBaseUrl}
              onRunEnvironmentIdChange={setRunEnvironmentId}
              onRunCaseIdsChange={setRunCaseIds}
              onRunSecretRefsChange={setRunSecretRefs}
              onGenerateBundle={() => void onGenerateScriptBundle()}
              onSubmitReview={(bundle) => void onSubmitBundleReview(bundle)}
              onApprove={(bundle) => void onApproveBundle(bundle)}
              onReject={(bundle) => void onRejectBundle(bundle)}
              onCreateRun={(bundle) => void onCreateRun(bundle)}
              onCancelRun={(run) => void onCancelRun(run)}
              onExportRun={(run) => void onExportRun(run)}
            />
          ) : (
            <div className="empty-state compact">
              <ListChecks size={20} />
              <div>
                <strong>{translate('nav.apiSuites')}</strong>
                <span>{translate('auto.k0185')}</span>
              </div>
            </div>
          )}
        </div>
      </section>
      )}

      {activeSubPage === 'runs' && (
      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">{translate('nav.apiRuns')}</div>
            <div className="panel-desc">{lastRun ? lastRun.run.id : '-'}</div>
          </div>
        </div>
        <div className="panel-body compact">
          {runExportState.error && <div className="document-state-line error">{runExportState.error}</div>}
          {runExportState.success && <div className="document-state-line success">{runExportState.success}</div>}
          {lastRun ? (
            <RunSummary
              run={lastRun}
              runExport={lastRunExport}
              canExport={canExport}
              canCancel={canExecute}
              exportLoading={runExportState.loading}
              runLoading={runState.loading}
              onCancel={() => void onCancelRun(lastRun)}
              onExport={() => void onExportRun(lastRun)}
            />
          ) : (
            <div className="empty-state compact">
              <Play size={20} />
              <div>
                <strong>{translate('nav.apiRuns')}</strong>
                <span>{translate('auto.k0185')}</span>
              </div>
            </div>
          )}
        </div>
      </section>
      )}
    </section>
    </>
  );

  function setDraftValue(key: keyof SpecDraft, value: string) {
    setDraft((current) => ({ ...current, [key]: value }));
    setImportState({ loading: false });
  }

  function mergeScriptBundle(bundle: ApiAutomationScriptBundle, success: string) {
    setLastGeneration((current) => {
      if (!current) return current;
      return {
        ...current,
        scriptBundles: [
          bundle,
          ...current.scriptBundles.filter((item) => item.id !== bundle.id)
        ]
      };
    });
    setScriptBundleState({ loading: false, success });
  }

  function toggleSelectedAssetApiId(assetApiId: string) {
    setSelectedAssetApiIds((current) => current.includes(assetApiId)
      ? current.filter((id) => id !== assetApiId)
      : [...current, assetApiId]);
  }
}

function EndpointTable(props: {
  endpoints: ApiAutomationEndpointSnapshot[];
  loading: boolean;
  selectedAssetApiIds: string[];
  onToggleAssetApiId: (assetApiId: string) => void;
}) {
  return (
    <div className="table-wrap api-automation-table-wrap">
      <table>
        <thead>
          <tr>
            <th>{translate('auto.k0196')}</th>
            <th>{translate('auto.k0197')}</th>
            <th>{fieldLabel('path')}</th>
            <th>操作</th>
            <th>资产</th>
            <th>{translate('auto.k0198')}</th>
            <th>{translate('auto.k0199')}</th>
            <th>差异</th>
          </tr>
        </thead>
        <tbody>
          {props.endpoints.length ? props.endpoints.map((endpoint) => (
            <tr key={endpoint.id}>
              <td>
                <CheckboxControl
                  aria-label={translate('auto.k0200', { value0: endpoint.httpMethod, value1: endpoint.path })}

                  checked={Boolean(endpoint.assetApiId && props.selectedAssetApiIds.includes(endpoint.assetApiId))}
                  disabled={!endpoint.assetApiId}
                  onChange={() => endpoint.assetApiId && props.onToggleAssetApiId(endpoint.assetApiId)}
                />
              </td>
              <td><span className="method-pill">{endpoint.httpMethod}</span></td>
              <td>
                <span className="table-primary api-path">{endpoint.path}</span>
                <span className="table-secondary">{endpoint.summary ?? '-'}</span>
              </td>
              <td><span className="table-secondary">{endpoint.operationId ?? '-'}</span></td>
              <td><span className="table-secondary">{shortId(endpoint.assetApiId)}</span></td>
              <td>{endpoint.parameterCount}{endpoint.requestBodyPresent ? ' · body' : ''}</td>
              <td><span className="table-secondary">{endpoint.responseStatuses ?? '-'}</span></td>
              <td>
                <StatusBadge status={endpoint.diffStatus} />
                <span className="api-automation-diff-reason">{diffReason(endpoint)}</span>
                {endpoint.syncErrorSummary && <span className="api-automation-diff-reason error">{endpoint.syncErrorSummary}</span>}
              </td>
            </tr>
          )) : (
            <tr><td className="table-empty" colSpan={8}>{props.loading ? translate('auto.k0168') : translate('auto.k0201')}</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function SyncSummary(props: { sync: ApiAutomationSyncResponse }) {
  const text = syncSummaryText(props.sync.counts);
  return (
    <div className="api-automation-sync-summary">
      <span>{text}</span>
      <span>{props.sync.items.length} {translate('auto.k0202')}</span>
    </div>
  );
}

function GenerationScopeSummary(props: {
  endpoints: ApiAutomationEndpointSnapshot[];
  selectedAssetApiIds: string[];
  onSelectAll: () => void;
  onClear: () => void;
}) {
  const selectableCount = selectableAssetApiIds(props.endpoints).length;
  return (
    <div className="api-automation-scope-bar">
      <span>{translate('auto.k0203')}{props.selectedAssetApiIds.length ? `${props.selectedAssetApiIds.length}/${selectableCount}` : translate('auto.k0204', { value0: selectableCount })}</span>
      <button className="btn btn-ghost btn-sm" type="button" onClick={props.onSelectAll} disabled={!selectableCount}>
        {translate('auto.k0205')}</button>
      <button className="btn btn-ghost btn-sm" type="button" onClick={props.onClear} disabled={!props.selectedAssetApiIds.length}>
        {translate('auto.k0206')}</button>
    </div>
  );
}

function GenerationHistory(props: {
  tasks: ApiAutomationGenerationTask[];
  selectedTaskId?: string;
  loading: boolean;
  onLoad: (taskId: string) => void;
}) {
  if (!props.tasks.length) return null;
  return (
    <div className="api-automation-history">
      <div className="api-automation-history-head">
        <span>{translate('auto.k0207')}</span>
        <em>{props.tasks.length} {translate('auto.k0208')}</em>
      </div>
      <div className="api-automation-history-list">
        {props.tasks.map((task) => (
          <button
            className={task.id === props.selectedTaskId ? 'api-automation-history-item active' : 'api-automation-history-item'}
            type="button"
            key={task.id}
            onClick={() => props.onLoad(task.id)}
            disabled={props.loading}
          >
            <span>
              <strong>{displayValueLabel(task.status)}</strong>
              <em>{displayValueLabel(task.generationMode)} · API {task.apiCount} · 用例 {task.caseCount}</em>
            </span>
            <small>{task.createdAt ? formatDateTime(task.createdAt) : shortId(task.id)}</small>
          </button>
        ))}
      </div>
    </div>
  );
}

function GenerationSummary(props: {
  generation: ApiAutomationGenerationTaskDetail;
  health: ApiAutomationHealth | null;
  canGenerate: boolean;
  canReview: boolean;
  canExecute: boolean;
  canExport: boolean;
  loading: boolean;
  runLoading: boolean;
  runExportLoading: boolean;
  reviewNote: string;
  runBaseUrl: string;
  runEnvironmentId: string;
  runCaseIds: string;
  runSecretRefs: string;
  lastRun: ApiAutomationRunDetail | null;
  lastRunExport: ApiAutomationRunExport | null;
  onReviewNoteChange: (value: string) => void;
  onRunBaseUrlChange: (value: string) => void;
  onRunEnvironmentIdChange: (value: string) => void;
  onRunCaseIdsChange: (value: string) => void;
  onRunSecretRefsChange: (value: string) => void;
  onGenerateBundle: () => void;
  onSubmitReview: (bundle: ApiAutomationScriptBundle) => void;
  onApprove: (bundle: ApiAutomationScriptBundle) => void;
  onReject: (bundle: ApiAutomationScriptBundle) => void;
  onCreateRun: (bundle: ApiAutomationScriptBundle) => void;
  onCancelRun: (run: ApiAutomationRunDetail) => void;
  onExportRun: (run: ApiAutomationRunDetail) => void;
}) {
  const bundle = props.generation.scriptBundles[0];
  const runnerReady = props.health?.runnerEnabled ?? false;
  return (
    <div className="api-automation-generation-summary">
      <div className="api-automation-sync-summary">
        <span>{generationSummaryText(props.generation)}</span>
        <span>{displayValueLabel(props.generation.task.generationMode)} · {props.generation.task.modelInvocationId ? shortId(props.generation.task.modelInvocationId) : '未调用模型'}</span>
        <span>{props.generation.cases.length} {translate('auto.k0209')}</span>
      </div>
      {bundle ? (
        <div className="api-automation-script-bundle">
          <div className="api-automation-script-bundle-head">
            <div>
              <span className="table-primary">{translate('auto.k0210')}</span>
              <span className="table-secondary">{shortId(bundle.bundleDigest)} · {bundle.fileCount} 个文件</span>
            </div>
            <div className="api-automation-panel-actions">
              <StatusBadge status={bundle.status} />
              <StatusBadge status={bundle.staticCheckStatus} />
            </div>
          </div>
          <div className="api-automation-bundle-files">
            {scriptBundleFiles(bundle).map((file) => (
              <span key={file.path}>{file.path}</span>
            ))}
          </div>
          <div className="form-grid">
            <Field label={translate('auto.k0211')}>
              <InputControl
                value={props.reviewNote}
                onChange={(event) => props.onReviewNoteChange(event.target.value)}
                disabled={!props.canReview || props.loading}
              />
            </Field>
          </div>
          <div className="api-automation-panel-actions">
            {(bundle.status === 'DRAFT' || bundle.status === 'REJECTED') && (
              <button
                className="btn btn-secondary btn-sm"
                type="button"
                onClick={() => props.onSubmitReview(bundle)}
                disabled={!props.canReview || props.loading || bundle.staticCheckStatus !== 'PASSED'}
              >
                <Send size={15} />
                {translate('auto.k0212')}</button>
            )}
            {bundle.status === 'REVIEWING' && (
              <>
                <button
                  className="btn btn-primary btn-sm"
                  type="button"
                  onClick={() => props.onApprove(bundle)}
                  disabled={!props.canReview || props.loading}
                >
                  <ClipboardCheck size={15} />
                  {translate('auto.k0213')}</button>
                <button
                  className="btn btn-secondary btn-sm"
                  type="button"
                  onClick={() => props.onReject(bundle)}
                  disabled={!props.canReview || props.loading || !props.reviewNote.trim()}
                >
                  <AlertTriangle size={15} />
                  {translate('auto.k0214')}</button>
              </>
            )}
          </div>
          {bundle.status === 'APPROVED' && (
            <div className="api-automation-run-panel">
              <div className="api-automation-sync-summary">
                <span>{fieldLabel('runner')} {displayValueLabel(runnerReady ? 'ENABLED' : 'DISABLED')}</span>
                <span>{fieldLabel('timeout')} {props.health?.runnerTimeoutSeconds ?? '-'}s</span>
                <span>{fieldLabel('limit')} {props.health?.runnerMaxCases ?? '-'}</span>
              </div>
              <div className="form-grid">
                <Field label="baseUrl">
                  <InputControl
                    value={props.runBaseUrl}
                    onChange={(event) => props.onRunBaseUrlChange(event.target.value)}
                    placeholder="https://api.example.test"
                    disabled={!props.canExecute || props.runLoading}
                  />
                </Field>
                <Field label={translate('auto.k0215')}>
                  <InputControl
                    value={props.runEnvironmentId}
                    onChange={(event) => props.onRunEnvironmentIdChange(event.target.value)}
                    disabled={!props.canExecute || props.runLoading}
                  />
                </Field>
                <Field label="caseIds">
                  <InputControl
                    value={props.runCaseIds}
                    onChange={(event) => props.onRunCaseIdsChange(event.target.value)}
                    placeholder={translate('auto.k0216')}
                    disabled={!props.canExecute || props.runLoading}
                  />
                </Field>
                <Field label="secretRefs">
                  <InputControl
                    value={props.runSecretRefs}
                    onChange={(event) => props.onRunSecretRefsChange(event.target.value)}
                    placeholder="secret://wp6/token"
                    disabled={!props.canExecute || props.runLoading}
                  />
                </Field>
              </div>
              <div className="api-automation-panel-actions">
                <button
                  className="btn btn-primary btn-sm"
                  type="button"
                  onClick={() => props.onCreateRun(bundle)}
                  disabled={!props.canExecute || props.runLoading || !props.runBaseUrl.trim()}
                >
                  <Play size={15} />
                  {translate('auto.k0217')}</button>
              </div>
              {props.lastRun && (
                <RunSummary
                  run={props.lastRun}
                  runExport={props.lastRunExport}
                  canExport={props.canExport}
                  canCancel={props.canExecute}
                  exportLoading={props.runExportLoading}
                  runLoading={props.runLoading}
                  onCancel={() => props.onCancelRun(props.lastRun!)}
                  onExport={() => props.onExportRun(props.lastRun!)}
                />
              )}
            </div>
          )}
        </div>
      ) : (
        <button
          className="btn btn-secondary btn-sm"
          type="button"
          onClick={props.onGenerateBundle}
          disabled={!props.canGenerate || props.loading}
        >
          <ListChecks size={15} />
          {translate('auto.k0218')}</button>
      )}
    </div>
  );
}

function RunSummary(props: {
  run: ApiAutomationRunDetail;
  runExport: ApiAutomationRunExport | null;
  canExport: boolean;
  canCancel: boolean;
  exportLoading: boolean;
  runLoading: boolean;
  onCancel: () => void;
  onExport: () => void;
}) {
  const counts = props.run.results.reduce<Record<string, number>>((acc, result) => {
    acc[result.status] = (acc[result.status] ?? 0) + 1;
    return acc;
  }, {});
  return (
    <div className="api-automation-run-result">
      <div className="api-automation-script-bundle-head">
        <div>
          <span className="table-primary">{translate('auto.k0219')}</span>
          <span className="table-secondary">{props.run.run.baseUrlHost ?? '-'} · {shortId(props.run.run.baseUrlDigest)}</span>
        </div>
        <div className="api-automation-panel-actions">
          <StatusBadge status={props.run.run.status} />
          <StatusBadge status={props.run.run.runnerMode} />
        </div>
      </div>
      <div className="api-automation-sync-summary">
        <span>{runSummaryText(props.run)}</span>
        <span>{dictionaryLabel('BLOCKED')} {counts.BLOCKED ?? 0}</span>
        <span>{dictionaryLabel('FAILED')} {counts.FAILED ?? 0}</span>
        <span>{dictionaryLabel('PASSED')} {counts.PASSED ?? 0}</span>
      </div>
      <div className="api-automation-panel-actions">
        {activeRunStatus(props.run.run.status) && (
          <button
            className="btn btn-secondary btn-sm"
            type="button"
            disabled={!props.canCancel || props.runLoading}
            onClick={props.onCancel}
          >
            <Square size={15} />
            {translate('auto.k0220')}</button>
        )}
        <button
          className="btn btn-secondary btn-sm"
          type="button"
          disabled={!props.canExport || props.exportLoading}
          onClick={props.onExport}
        >
          <Download size={15} />
          {translate('auto.k0221')}</button>
      </div>
      {props.run.run.errorCode && (
        <div className="api-automation-diff-reason error">
          {displayValueLabel(props.run.run.errorCode)}{props.run.run.errorSummary ? ` · ${props.run.run.errorSummary}` : ''}
        </div>
      )}
      {props.runExport && (
        <div className="api-automation-export-summary">
          <span>{props.runExport.schemaVersion}</span>
          <span>{fieldLabel('exported')} {props.runExport.exportedAt ? formatDateTime(props.runExport.exportedAt) : '-'}</span>
          <span>{fieldLabel('rawUrl')} {displayValueLabel(props.runExport.redactionPolicy.rawBaseUrlExported ? 'ON' : 'OFF')}</span>
          <span>{fieldLabel('requestResponse')} {displayValueLabel(props.runExport.redactionPolicy.rawRequestResponseExported ? 'ON' : 'OFF')}</span>
        </div>
      )}
    </div>
  );
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
    <div className="api-automation-policy-item">
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
  const tone = status.includes('FAILED') || status === 'CONFLICT' || status === 'SKIPPED' || status === 'BLOCKED' || status === 'ERROR' ? 'danger'
    : status === 'PARSED' || status === 'MATCHED' || status === 'CREATED' || status === 'UPDATED' || status === 'APPROVED' || status === 'PASSED' || status === 'MANAGED' ? 'success'
      : 'neutral';
  return <span className={`status-badge ${tone}`} title={status}>{dictionaryLabel(status)}</span>;
}

function scriptBundleFiles(bundle: ApiAutomationScriptBundle) {
  const files = bundle.fileTreeSummary.files;
  if (!Array.isArray(files)) return [];
  return files
    .map((file) => file && typeof file === 'object' ? file as Record<string, unknown> : {})
    .map((file) => ({ path: typeof file.path === 'string' ? file.path : '' }))
    .filter((file) => file.path)
    .slice(0, 6);
}

function diffReason(endpoint: ApiAutomationEndpointSnapshot) {
  const reason = endpoint.diffSummary?.reason;
  return typeof reason === 'string' ? displayValueLabel(reason) : '';
}

function diffSummaryText(counts: Record<string, number>) {
  return `差异：${dictionaryLabel('NEW')} ${counts.NEW ?? 0} · ${dictionaryLabel('CHANGED')} ${counts.CHANGED ?? 0} · ${dictionaryLabel('MATCHED')} ${counts.MATCHED ?? 0}`;
}

function syncSummaryText(counts: Record<string, number>) {
  return `同步：${dictionaryLabel('CREATED')} ${counts.CREATED ?? 0} · ${dictionaryLabel('UPDATED')} ${counts.UPDATED ?? 0} · ${dictionaryLabel('FAILED')} ${counts.FAILED ?? 0}`;
}

function generationSummaryText(generation: ApiAutomationGenerationTaskDetail) {
  return `生成：${displayValueLabel(generation.task.status)} · API ${generation.task.apiCount} · 用例 ${generation.task.caseCount}`;
}

function runSummaryText(run: ApiAutomationRunDetail) {
  return `运行：${displayValueLabel(run.run.status)} · 用例 ${run.run.caseCount} · ${displayValueLabel(run.run.errorCode ?? 'OK')}`;
}

function activeRunStatus(status: string) {
  return status === 'QUEUED' || status === 'RUNNING';
}

function shortId(value?: string) {
  return value ? value.slice(0, 8) : '-';
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').replace('Z', '');
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function parseIdList(value: string) {
  return value.split(/[\s,，]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function selectableAssetApiIds(endpoints: ApiAutomationEndpointSnapshot[]) {
  return Array.from(new Set(endpoints.map((endpoint) => endpoint.assetApiId).filter(Boolean) as string[]));
}

function filteredEndpoints(
  endpoints: ApiAutomationEndpointSnapshot[],
  diffStatusFilter: (typeof DIFF_STATUS_OPTIONS)[number]
) {
  return diffStatusFilter === 'ALL'
    ? endpoints
    : endpoints.filter((endpoint) => endpoint.diffStatus === diffStatusFilter);
}
