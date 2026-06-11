import {
  AlertTriangle,
  CheckCircle2,
  FileText,
  ListChecks,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Upload
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  createApiAutomationSpec,
  fetchApiAutomationHealth,
  fetchApiAutomationSpec,
  fetchApiAutomationSpecs,
  parseApiAutomationSpec,
  type ApiAutomationEndpointSnapshot,
  type ApiAutomationHealth,
  type ApiAutomationSpec,
  type ApiAutomationSpecDetail
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
  const [health, setHealth] = useState<ApiAutomationHealth | null>(null);
  const [specs, setSpecs] = useState<ApiAutomationSpec[]>([]);
  const [selectedSpecId, setSelectedSpecId] = useState('');
  const [detail, setDetail] = useState<ApiAutomationSpecDetail | null>(null);
  const [draft, setDraft] = useState<SpecDraft>(initialDraft);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [importState, setImportState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [parseState, setParseState] = useState<WorkState>({ loading: false });

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
      await refreshSpecs();
    } catch (error: unknown) {
      setParseState({ loading: false, error: error instanceof Error ? error.message : '解析失败' });
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
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onReparse()} disabled={!selectedSpecId || !canImport || parseState.loading}>
            <RotateCcw size={15} />
            重解析
          </button>
        </div>
        <div className="panel-body compact">
          {detailState.error && <div className="document-state-line error">{detailState.error}</div>}
          {parseState.error && <div className="document-state-line error">{parseState.error}</div>}
          {parseState.success && <div className="document-state-line success">{parseState.success}</div>}
          <EndpointTable endpoints={detail?.endpoints ?? []} loading={detailState.loading} />
        </div>
      </section>
    </section>
  );

  function setDraftValue(key: keyof SpecDraft, value: string) {
    setDraft((current) => ({ ...current, [key]: value }));
    setImportState({ loading: false });
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
              <td>{endpoint.parameterCount}{endpoint.requestBodyPresent ? ' · body' : ''}</td>
              <td><span className="table-secondary">{endpoint.responseStatuses ?? '-'}</span></td>
              <td><StatusBadge status={endpoint.diffStatus} /></td>
            </tr>
          )) : (
            <tr><td className="table-empty" colSpan={6}>{props.loading ? '加载中' : '暂无 endpoint'}</td></tr>
          )}
        </tbody>
      </table>
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
  const tone = status.includes('FAILED') || status === 'CONFLICT' ? 'danger'
    : status === 'PARSED' || status === 'MATCHED' ? 'success'
      : 'neutral';
  return <span className={`status-badge ${tone}`}>{status}</span>;
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}
