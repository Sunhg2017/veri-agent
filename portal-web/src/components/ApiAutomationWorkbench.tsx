import {
  AlertTriangle,
  CheckCircle2,
  ClipboardCheck,
  FileText,
  Download,
  Play,
  ListChecks,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Send,
  Upload
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  createApiAutomationGenerationTask,
  createApiAutomationRun,
  createApiAutomationSpec,
  exportApiAutomationRun,
  approveApiAutomationScriptBundle,
  fetchApiAutomationDiff,
  fetchApiAutomationHealth,
  fetchApiAutomationSpec,
  fetchApiAutomationSpecs,
  generateApiAutomationScriptBundle,
  parseApiAutomationSpec,
  rejectApiAutomationScriptBundle,
  submitApiAutomationScriptBundleReview,
  syncApiAutomationSpec,
  type ApiAutomationEndpointSnapshot,
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

export function ApiAutomationWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'apiAutomation:read');
  const canImport = canUseButton(props.currentUser, 'apiAutomation:import');
  const canGenerate = canUseButton(props.currentUser, 'apiAutomation:generate');
  const canReview = canUseButton(props.currentUser, 'apiAutomation:review');
  const canExecute = canUseButton(props.currentUser, 'apiAutomation:execute');
  const canExport = canUseButton(props.currentUser, 'apiAutomation:export');
  const [health, setHealth] = useState<ApiAutomationHealth | null>(null);
  const [specs, setSpecs] = useState<ApiAutomationSpec[]>([]);
  const [selectedSpecId, setSelectedSpecId] = useState('');
  const [detail, setDetail] = useState<ApiAutomationSpecDetail | null>(null);
  const [draft, setDraft] = useState<SpecDraft>(initialDraft);
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
  const [lastRun, setLastRun] = useState<ApiAutomationRunDetail | null>(null);
  const [lastRunExport, setLastRunExport] = useState<ApiAutomationRunExport | null>(null);
  const [generationAssetTestCaseIds, setGenerationAssetTestCaseIds] = useState('');
  const [generationMode, setGenerationMode] = useState<'MODEL_WITH_FALLBACK' | 'FALLBACK_ONLY'>('MODEL_WITH_FALLBACK');
  const [reviewNote, setReviewNote] = useState('');
  const [runBaseUrl, setRunBaseUrl] = useState('');
  const [runEnvironmentId, setRunEnvironmentId] = useState('');
  const [runCaseIds, setRunCaseIds] = useState('');

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
      setLoadState({ loading: false, error: error instanceof Error ? error.message : '加载失败' });
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
      setDetailState({ loading: false, error: error instanceof Error ? error.message : '加载失败' });
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

  async function onImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canImport) return;
    if (!draft.projectId.trim() || !draft.name.trim() || !draft.content.trim()) {
      setImportState({ loading: false, error: '请填写项目、名称和 OpenAPI 内容' });
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
      setSelectedSpecId(result.data.spec.id);
      setDetail(result.data);
      setImportState({ loading: false, success: 'OpenAPI 规格已导入' });
      await refreshSpecs();
    } catch (error: unknown) {
      setImportState({ loading: false, error: error instanceof Error ? error.message : '导入失败' });
    }
  }

  async function onReparse() {
    if (!selectedSpecId || !canImport) return;
    setParseState({ loading: true });
    try {
      const result = await parseApiAutomationSpec(selectedSpecId);
      setDetail(result.data);
      setParseState({ loading: false, success: '解析已刷新' });
      setLastSync(null);
      setLastGeneration(null);
      setLastRun(null);
      setLastRunExport(null);
      await refreshSpecs();
    } catch (error: unknown) {
      setParseState({ loading: false, error: error instanceof Error ? error.message : '解析失败' });
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
      setDiffState({ loading: false, error: error instanceof Error ? error.message : 'Diff 失败' });
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
      setSyncState({ loading: false, error: error instanceof Error ? error.message : '同步失败' });
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
        assetTestCaseIds: assetTestCaseIds.length ? assetTestCaseIds : undefined,
        coverageTypes: ['SMOKE', 'EXCEPTION'],
        generationMode: effectiveGenerationMode,
        caseCountPerApi: 2,
        requestKey: `wp6-${effectiveGenerationMode.toLowerCase()}-${selectedSpecId}`
      });
      setLastGeneration(result.data);
      setLastRun(null);
      setLastRunExport(null);
      setReviewNote('');
      setGenerationState({ loading: false, success: generationSummaryText(result.data) });
    } catch (error: unknown) {
      setGenerationState({ loading: false, error: error instanceof Error ? error.message : '生成失败' });
    }
  }

  async function onGenerateScriptBundle() {
    if (!lastGeneration || !canGenerate) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await generateApiAutomationScriptBundle(lastGeneration.task.id);
      mergeScriptBundle(result.data, '脚本包已生成');
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : '脚本包生成失败' });
    }
  }

  async function onSubmitBundleReview(bundle: ApiAutomationScriptBundle) {
    if (!canReview) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await submitApiAutomationScriptBundleReview(bundle.id, { note: optionalText(reviewNote) });
      mergeScriptBundle(result.data, '脚本包已提交评审');
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : '提交评审失败' });
    }
  }

  async function onApproveBundle(bundle: ApiAutomationScriptBundle) {
    if (!canReview) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await approveApiAutomationScriptBundle(bundle.id, { note: optionalText(reviewNote) });
      mergeScriptBundle(result.data, '脚本包已审批通过');
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : '审批失败' });
    }
  }

  async function onRejectBundle(bundle: ApiAutomationScriptBundle) {
    if (!canReview || !reviewNote.trim()) return;
    setScriptBundleState({ loading: true });
    try {
      const result = await rejectApiAutomationScriptBundle(bundle.id, { note: reviewNote.trim() });
      mergeScriptBundle(result.data, '脚本包已驳回');
    } catch (error: unknown) {
      setScriptBundleState({ loading: false, error: error instanceof Error ? error.message : '驳回失败' });
    }
  }

  async function onCreateRun(bundle: ApiAutomationScriptBundle) {
    if (!canExecute) return;
    if (!runBaseUrl.trim()) {
      setRunState({ loading: false, error: '请填写 baseUrl' });
      return;
    }
    setRunState({ loading: true });
    try {
      const caseIds = parseIdList(runCaseIds);
      const result = await createApiAutomationRun({
        bundleId: bundle.id,
        environmentId: optionalText(runEnvironmentId),
        baseUrl: runBaseUrl.trim(),
        caseIds: caseIds.length ? caseIds : undefined
      });
      setLastRun(result.data);
      setLastRunExport(null);
      setRunState({ loading: false, success: runSummaryText(result.data) });
    } catch (error: unknown) {
      setRunState({ loading: false, error: error instanceof Error ? error.message : '运行失败' });
    }
  }

  async function onExportRun(run: ApiAutomationRunDetail) {
    if (!canExport) return;
    setRunExportState({ loading: true });
    try {
      const result = await exportApiAutomationRun(run.run.id);
      setLastRunExport(result.data);
      setRunExportState({ loading: false, success: `导出 ${result.data.schemaVersion} · ${result.data.results.length} 条结果` });
    } catch (error: unknown) {
      setRunExportState({ loading: false, error: error instanceof Error ? error.message : '导出失败' });
    }
  }

  if (!props.signedIn || !canRead) {
    return (
      <section className="panel">
        <div className="empty-state">
          <ShieldCheck className="empty-state-icon" />
          <strong>无权访问</strong>
          <span>需要 apiAutomation:read 权限。</span>
        </div>
      </section>
    );
  }

  return (
    <section className="api-automation-console">
      <div className="metric-grid">
        <MetricCard label="规格" value={String(specs.length)} icon={<FileText size={18} />} />
        <MetricCard label="已解析" value={String(summary.parsed)} icon={<CheckCircle2 size={18} />} />
        <MetricCard label="Endpoint" value={String(summary.endpoints)} icon={<ListChecks size={18} />} />
        <MetricCard label="解析失败" value={String(summary.failed)} icon={<AlertTriangle size={18} />} />
      </div>

      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">控制面策略</div>
            <div className="panel-desc">
              {health ? `${health.service} · ${health.status}` : loadState.loading ? '加载中' : '未加载'}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshSpecs()} disabled={loadState.loading}>
            <RefreshCw size={15} />
            刷新
          </button>
        </div>
        <div className="panel-body compact">
          {loadState.error && <div className="document-state-line error">{loadState.error}</div>}
          <div className="api-automation-policy-grid">
            <PolicyItem label="OpenAPI" value={health?.supportedOpenApiVersions.join(', ') || '-'} />
            <PolicyItem label="规格上限" value={health ? `${Math.round(health.specMaxBytes / 1024)} KB` : '-'} />
            <PolicyItem label="Endpoint 上限" value={String(health?.endpointMaxCount ?? '-')} />
            <PolicyItem label="Runner" value={health?.runnerEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="Prompt" value={health?.promptKey ?? '-'} />
            <PolicyItem label="URL 拉取" value={health?.policy?.['urlFetchEnabled'] ? 'ENABLED' : 'DISABLED'} />
          </div>
        </div>
      </section>

      <section className="api-automation-layout">
        <form className="panel" onSubmit={onImport}>
          <div className="panel-header">
            <div>
              <div className="panel-title">导入规格</div>
              <div className="panel-desc">TEXT · JSON/YAML</div>
            </div>
            <button className="btn btn-primary btn-sm" type="submit" disabled={!canImport || importState.loading}>
              <Upload size={15} />
              导入
            </button>
          </div>
          <div className="panel-body">
            <div className="form-grid">
              <Field label="项目">
                <input value={draft.projectId} onChange={(event) => setDraftValue('projectId', event.target.value)} />
              </Field>
              <Field label="名称">
                <input value={draft.name} onChange={(event) => setDraftValue('name', event.target.value)} />
              </Field>
              <Field label="版本">
                <input value={draft.versionLabel} onChange={(event) => setDraftValue('versionLabel', event.target.value)} />
              </Field>
              <Field label="来源">
                <input value={draft.sourceRef} onChange={(event) => setDraftValue('sourceRef', event.target.value)} />
              </Field>
            </div>
            <Field label="OpenAPI">
              <textarea
                className="api-automation-spec-textarea"
                value={draft.content}
                onChange={(event) => setDraftValue('content', event.target.value)}
              />
            </Field>
            {importState.error && <div className="document-state-line error">{importState.error}</div>}
            {importState.success && <div className="document-state-line success">{importState.success}</div>}
          </div>
        </form>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">规格列表</div>
              <div className="panel-desc">{specs.length} 条</div>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="table-wrap api-automation-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>名称</th>
                    <th>项目</th>
                    <th>状态</th>
                    <th>Endpoint</th>
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
                    <tr><td className="table-empty" colSpan={4}>{loadState.loading ? '加载中' : '暂无规格'}</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">Endpoint Snapshot</div>
            <div className="panel-desc">{detail ? `${detail.spec.name} · ${detail.endpoints.length} 条` : '未选择规格'}</div>
          </div>
          <div className="api-automation-panel-actions">
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onRefreshDiff()} disabled={!selectedSpecId || diffState.loading}>
              <RefreshCw size={15} />
              Diff
            </button>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onSync()} disabled={!selectedSpecId || !canImport || syncState.loading}>
              <Upload size={15} />
              同步
            </button>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onGenerateCases()} disabled={!selectedSpecId || !canGenerate || generationState.loading}>
              <ListChecks size={15} />
              生成用例
            </button>
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onReparse()} disabled={!selectedSpecId || !canImport || parseState.loading}>
              <RotateCcw size={15} />
              重解析
            </button>
          </div>
        </div>
        <div className="panel-body compact">
          <div className="form-grid">
            <Field label="生成模式">
              <select
                value={Boolean(health?.policy?.modelGenerationReady) ? generationMode : 'FALLBACK_ONLY'}
                onChange={(event) => setGenerationMode(event.target.value as 'MODEL_WITH_FALLBACK' | 'FALLBACK_ONLY')}
                disabled={!canGenerate || generationState.loading || !health}
              >
                {Boolean(health?.policy?.modelGenerationReady) && <option value="MODEL_WITH_FALLBACK">模型优先</option>}
                <option value="FALLBACK_ONLY">确定模板</option>
              </select>
            </Field>
            <Field label="WP3 用例 ID">
              <input
                value={generationAssetTestCaseIds}
                onChange={(event) => setGenerationAssetTestCaseIds(event.target.value)}
                placeholder="逗号或换行分隔"
                disabled={!canGenerate || generationState.loading}
              />
            </Field>
          </div>
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
          {lastGeneration && (
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
              lastRun={lastRun}
              lastRunExport={lastRunExport}
              onReviewNoteChange={setReviewNote}
              onRunBaseUrlChange={setRunBaseUrl}
              onRunEnvironmentIdChange={setRunEnvironmentId}
              onRunCaseIdsChange={setRunCaseIds}
              onGenerateBundle={() => void onGenerateScriptBundle()}
              onSubmitReview={(bundle) => void onSubmitBundleReview(bundle)}
              onApprove={(bundle) => void onApproveBundle(bundle)}
              onReject={(bundle) => void onRejectBundle(bundle)}
              onCreateRun={(bundle) => void onCreateRun(bundle)}
              onExportRun={(run) => void onExportRun(run)}
            />
          )}
          <EndpointTable endpoints={detail?.endpoints ?? []} loading={detailState.loading} />
        </div>
      </section>
    </section>
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
}

function EndpointTable(props: { endpoints: ApiAutomationEndpointSnapshot[]; loading: boolean }) {
  return (
    <div className="table-wrap api-automation-table-wrap">
      <table>
        <thead>
          <tr>
            <th>方法</th>
            <th>Path</th>
            <th>Operation</th>
            <th>Asset</th>
            <th>参数</th>
            <th>响应</th>
            <th>Diff</th>
          </tr>
        </thead>
        <tbody>
          {props.endpoints.length ? props.endpoints.map((endpoint) => (
            <tr key={endpoint.id}>
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
            <tr><td className="table-empty" colSpan={7}>{props.loading ? '加载中' : '暂无 endpoint'}</td></tr>
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
      <span>{props.sync.items.length} 条同步明细</span>
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
  lastRun: ApiAutomationRunDetail | null;
  lastRunExport: ApiAutomationRunExport | null;
  onReviewNoteChange: (value: string) => void;
  onRunBaseUrlChange: (value: string) => void;
  onRunEnvironmentIdChange: (value: string) => void;
  onRunCaseIdsChange: (value: string) => void;
  onGenerateBundle: () => void;
  onSubmitReview: (bundle: ApiAutomationScriptBundle) => void;
  onApprove: (bundle: ApiAutomationScriptBundle) => void;
  onReject: (bundle: ApiAutomationScriptBundle) => void;
  onCreateRun: (bundle: ApiAutomationScriptBundle) => void;
  onExportRun: (run: ApiAutomationRunDetail) => void;
}) {
  const bundle = props.generation.scriptBundles[0];
  const runnerReady = props.health?.runnerEnabled ?? false;
  return (
    <div className="api-automation-generation-summary">
      <div className="api-automation-sync-summary">
        <span>{generationSummaryText(props.generation)}</span>
        <span>{props.generation.task.generationMode} · {props.generation.task.modelInvocationId ? shortId(props.generation.task.modelInvocationId) : 'no-model'}</span>
        <span>{props.generation.cases.length} 条草稿</span>
      </div>
      {bundle ? (
        <div className="api-automation-script-bundle">
          <div className="api-automation-script-bundle-head">
            <div>
              <span className="table-primary">脚本包</span>
              <span className="table-secondary">{shortId(bundle.bundleDigest)} · {bundle.fileCount} files</span>
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
            <Field label="备注">
              <input
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
                提交评审
              </button>
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
                  审批
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  type="button"
                  onClick={() => props.onReject(bundle)}
                  disabled={!props.canReview || props.loading || !props.reviewNote.trim()}
                >
                  <AlertTriangle size={15} />
                  驳回
                </button>
              </>
            )}
          </div>
          {bundle.status === 'APPROVED' && (
            <div className="api-automation-run-panel">
              <div className="api-automation-sync-summary">
                <span>Runner {runnerReady ? 'ENABLED' : 'DISABLED'}</span>
                <span>timeout {props.health?.runnerTimeoutSeconds ?? '-'}s</span>
                <span>max {props.health?.runnerMaxCases ?? '-'} cases</span>
              </div>
              <div className="form-grid">
                <Field label="baseUrl">
                  <input
                    value={props.runBaseUrl}
                    onChange={(event) => props.onRunBaseUrlChange(event.target.value)}
                    placeholder="https://api.example.test"
                    disabled={!props.canExecute || props.runLoading}
                  />
                </Field>
                <Field label="环境">
                  <input
                    value={props.runEnvironmentId}
                    onChange={(event) => props.onRunEnvironmentIdChange(event.target.value)}
                    disabled={!props.canExecute || props.runLoading}
                  />
                </Field>
                <Field label="Case IDs">
                  <input
                    value={props.runCaseIds}
                    onChange={(event) => props.onRunCaseIdsChange(event.target.value)}
                    placeholder="为空则使用全部用例"
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
                  运行
                </button>
              </div>
              {props.lastRun && (
                <RunSummary
                  run={props.lastRun}
                  runExport={props.lastRunExport}
                  canExport={props.canExport}
                  exportLoading={props.runExportLoading}
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
          生成脚本包
        </button>
      )}
    </div>
  );
}

function RunSummary(props: {
  run: ApiAutomationRunDetail;
  runExport: ApiAutomationRunExport | null;
  canExport: boolean;
  exportLoading: boolean;
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
          <span className="table-primary">运行结果</span>
          <span className="table-secondary">{props.run.run.baseUrlHost ?? '-'} · {shortId(props.run.run.baseUrlDigest)}</span>
        </div>
        <div className="api-automation-panel-actions">
          <StatusBadge status={props.run.run.status} />
          <StatusBadge status={props.run.run.runnerMode} />
        </div>
      </div>
      <div className="api-automation-sync-summary">
        <span>{runSummaryText(props.run)}</span>
        <span>BLOCKED {counts.BLOCKED ?? 0}</span>
        <span>FAILED {counts.FAILED ?? 0}</span>
        <span>PASSED {counts.PASSED ?? 0}</span>
      </div>
      <div className="api-automation-panel-actions">
        <button
          className="btn btn-secondary btn-sm"
          type="button"
          disabled={!props.canExport || props.exportLoading}
          onClick={props.onExport}
        >
          <Download size={15} />
          导出摘要
        </button>
      </div>
      {props.run.run.errorCode && (
        <div className="api-automation-diff-reason error">
          {props.run.run.errorCode}{props.run.run.errorSummary ? ` · ${props.run.run.errorSummary}` : ''}
        </div>
      )}
      {props.runExport && (
        <div className="api-automation-export-summary">
          <span>{props.runExport.schemaVersion}</span>
          <span>exported {props.runExport.exportedAt ? formatDateTime(props.runExport.exportedAt) : '-'}</span>
          <span>raw URL {props.runExport.redactionPolicy.rawBaseUrlExported ? 'on' : 'off'}</span>
          <span>request/response {props.runExport.redactionPolicy.rawRequestResponseExported ? 'on' : 'off'}</span>
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
        <span className="metric-label">{props.label}</span>
      </div>
    </div>
  );
}

function PolicyItem(props: { label: string; value: string }) {
  return (
    <div className="api-automation-policy-item">
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
  const tone = status.includes('FAILED') || status === 'CONFLICT' || status === 'SKIPPED' || status === 'BLOCKED' || status === 'ERROR' ? 'danger'
    : status === 'PARSED' || status === 'MATCHED' || status === 'CREATED' || status === 'UPDATED' || status === 'APPROVED' || status === 'PASSED' || status === 'MANAGED' ? 'success'
      : 'neutral';
  return <span className={`status-badge ${tone}`}>{status}</span>;
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
  return typeof reason === 'string' ? reason : '';
}

function diffSummaryText(counts: Record<string, number>) {
  return `Diff：NEW ${counts.NEW ?? 0} · CHANGED ${counts.CHANGED ?? 0} · MATCHED ${counts.MATCHED ?? 0}`;
}

function syncSummaryText(counts: Record<string, number>) {
  return `同步：CREATED ${counts.CREATED ?? 0} · UPDATED ${counts.UPDATED ?? 0} · FAILED ${counts.FAILED ?? 0}`;
}

function generationSummaryText(generation: ApiAutomationGenerationTaskDetail) {
  return `生成：${generation.task.status} · API ${generation.task.apiCount} · CASE ${generation.task.caseCount}`;
}

function runSummaryText(run: ApiAutomationRunDetail) {
  return `运行：${run.run.status} · CASE ${run.run.caseCount} · ${run.run.errorCode ?? 'OK'}`;
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
