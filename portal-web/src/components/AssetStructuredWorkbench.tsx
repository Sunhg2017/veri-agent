import {
  Archive,
  ClipboardList,
  Eye,
  FilePlus2,
  GitBranch,
  Pencil,
  RefreshCw,
  Save,
  Search,
  XCircle,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  ASSET_FLOW_STATUSES,
  ASSET_PAGE_SOURCES,
  ASSET_PAGE_STATUSES,
  ASSET_REQUIREMENT_PRIORITIES,
  createAssetBusinessFlow,
  createAssetPage,
  fetchAssetBusinessFlow,
  fetchAssetBusinessFlows,
  fetchAssetHealth,
  fetchAssetPage,
  fetchAssetPages,
  syncPrototypePages,
  updateAssetBusinessFlow,
  updateAssetPage,
  type AssetBusinessFlowFilters,
  type AssetBusinessFlowPayload,
  type AssetBusinessFlowView,
  type AssetHealth,
  type AssetPageFilters,
  type AssetPagePayload,
  type AssetPageView,
  type AssetPrototypeSyncPagePayload,
  type AssetPrototypeSyncResult
} from '../api/assets';
import { hasPermission } from '../permissions';

export type AssetNavigationKey = 'requirements' | 'apis' | 'pages' | 'flows' | 'cases' | 'trace';

type StructuredTabKey = 'pages' | 'flows';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type AssetNavigationTab = {
  key: AssetNavigationKey;
  label: string;
  icon: LucideIcon;
  enabled: boolean;
};

type StructuredAssetView = {
  id: string;
  code?: string;
  name: string;
  description?: string;
  projectId?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  urlPattern?: string;
  source?: string;
  sourceRef?: string;
  sourceVersion?: string;
  screenshotUrl?: string;
  priority?: string;
  jsonText?: string;
};

type StructuredFilters = {
  projectId: string;
  status: string;
  source: string;
  keyword: string;
};

type StructuredDraft = {
  projectId: string;
  name: string;
  description: string;
  urlPattern: string;
  source: string;
  sourceRef: string;
  sourceVersion: string;
  screenshotUrl: string;
  priority: string;
  status: string;
  jsonText: string;
};

type PrototypeSyncDraft = {
  projectId: string;
  source: string;
  connectorRef: string;
  sourceVersion: string;
  dryRun: boolean;
  pagesJson: string;
};

const initialFilters: StructuredFilters = {
  projectId: '',
  status: '',
  source: '',
  keyword: ''
};

const initialPrototypeSyncDraft: PrototypeSyncDraft = {
  projectId: '',
  source: 'FIGMA',
  connectorRef: '',
  sourceVersion: '',
  dryRun: true,
  pagesJson: ''
};

const pageStatusTransitions: Record<string, string[]> = {
  ACTIVE: ['ACTIVE', 'DEPRECATED'],
  DEPRECATED: ['DEPRECATED']
};

const flowStatusTransitions: Record<string, string[]> = {
  DRAFT: ['DRAFT', 'ACTIVE', 'ARCHIVED'],
  ACTIVE: ['ACTIVE', 'ARCHIVED'],
  ARCHIVED: ['ARCHIVED']
};

export function AssetStructuredWorkbench(props: {
  activeTab: StructuredTabKey;
  currentUser: CurrentUser | null;
  onSelectTab: (tabKey: AssetNavigationKey) => void;
  signedIn: boolean;
  tabs: readonly AssetNavigationTab[];
}) {
  const canReadAssets = hasPermission(props.currentUser, 'asset:read');
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const [health, setHealth] = useState<AssetHealth | null>(null);
  const [items, setItems] = useState<StructuredAssetView[]>([]);
  const [filters, setFilters] = useState<StructuredFilters>(initialFilters);
  const [selectedId, setSelectedId] = useState(assetIdFromHash(props.activeTab));
  const [selected, setSelected] = useState<StructuredAssetView | null>(null);
  const [createDraft, setCreateDraft] = useState(() => initialDraft(props.activeTab));
  const [editDraft, setEditDraft] = useState(() => initialDraft(props.activeTab));
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [createState, setCreateState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });
  const [prototypeSyncDraft, setPrototypeSyncDraft] = useState<PrototypeSyncDraft>(initialPrototypeSyncDraft);
  const [prototypeSyncState, setPrototypeSyncState] = useState<WorkState>({ loading: false });
  const [prototypeSyncResult, setPrototypeSyncResult] = useState<AssetPrototypeSyncResult | null>(null);

  const meta = structuredMeta(props.activeTab);

  useEffect(() => {
    setItems([]);
    setSelected(null);
    setSelectedId(assetIdFromHash(props.activeTab));
    setFilters(initialFilters);
    setCreateDraft(initialDraft(props.activeTab));
    setEditDraft(initialDraft(props.activeTab));
    setLoadState({ loading: false });
    setDetailState({ loading: false });
    setCreateState({ loading: false });
    setMutationState({ loading: false });
    setPrototypeSyncDraft(initialPrototypeSyncDraft);
    setPrototypeSyncState({ loading: false });
    setPrototypeSyncResult(null);
  }, [props.activeTab]);

  useEffect(() => {
    function syncFromHash() {
      const parts = window.location.hash.replace(/^#\/?/, '').split('/');
      if (parts[0] === 'asset-library' && parts[1] === props.activeTab) {
        setSelectedId(parts[2] ? decodeURIComponent(parts[2]) : '');
      }
    }

    window.addEventListener('hashchange', syncFromHash);
    return () => window.removeEventListener('hashchange', syncFromHash);
  }, [props.activeTab]);

  const refreshAssets = useCallback(async () => {
    if (!props.signedIn || !canReadAssets) {
      setHealth(null);
      setItems([]);
      setSelected(null);
      setLoadState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, listResult] = await Promise.allSettled([
      fetchAssetHealth(),
      props.activeTab === 'pages'
        ? fetchAssetPages(buildPageFilters(filters))
        : fetchAssetBusinessFlows(buildFlowFilters(filters))
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, '资产服务健康检查失败'));
    }

    if (listResult.status === 'fulfilled') {
      const nextItems = listToViews(props.activeTab, listResult.value.data.items);
      setItems(nextItems);
      traceIds.push(listResult.value.trace_id);
    } else {
      setItems([]);
      errors.push(errorMessage(listResult.reason, `${meta.name}加载失败`));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canReadAssets, filters, meta.name, props.activeTab, props.signedIn]);

  useEffect(() => {
    void refreshAssets();
  }, [refreshAssets]);

  useEffect(() => {
    if (!selectedId && items[0]?.id) {
      setSelectedId(items[0].id);
    }
  }, [items, selectedId]);

  const reloadDetail = useCallback(async () => {
    if (!props.signedIn || !canReadAssets || !selectedId) {
      setSelected(null);
      setDetailState({ loading: false });
      return;
    }

    setDetailState({ loading: true });
    try {
      const response =
        props.activeTab === 'pages'
          ? await fetchAssetPage(selectedId)
          : await fetchAssetBusinessFlow(selectedId);
      const nextItem = responseToView(props.activeTab, response.data);
      setSelected(nextItem);
      setEditDraft(draftFromView(props.activeTab, nextItem));
      upsertItem(setItems, nextItem);
      setDetailState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      setSelected(null);
      setDetailState({ loading: false, error: errorMessage(error, `${meta.name}详情加载失败`) });
    }
  }, [canReadAssets, meta.name, props.activeTab, props.signedIn, selectedId]);

  useEffect(() => {
    void reloadDetail();
  }, [reloadDetail]);

  const visibleItems = useMemo(() => filterItems(items, filters, props.activeTab), [filters, items, props.activeTab]);
  const statusCounts = useMemo(() => countByStatus(items), [items]);
  const disabled = !props.signedIn || !canReadAssets || loadState.loading;
  const createDisabled = disabled || createState.loading || !canManageAssets;
  const editDisabled = disabled || mutationState.loading || !canManageAssets || !selected;

  function selectItem(itemId: string) {
    if (!itemId) {
      return;
    }
    setSelectedId(itemId);
    const targetHash = `#asset-library/${props.activeTab}/${encodeURIComponent(itemId)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setCreateState({ loading: false, error: `请先登录后再创建${meta.name}` });
      return;
    }
    if (!canManageAssets) {
      setCreateState({ loading: false, error: '缺少 asset:manage 权限' });
      return;
    }
    if (!createDraft.projectId.trim() || !createDraft.name.trim()) {
      setCreateState({ loading: false, error: 'projectId 和名称必填' });
      return;
    }

    const jsonResult = parseJsonDraft(createDraft.jsonText, meta.jsonLabel);
    if (!jsonResult.ok) {
      setCreateState({ loading: false, error: jsonResult.error });
      return;
    }

    setCreateState({ loading: true });
    try {
      const response =
        props.activeTab === 'pages'
          ? await createAssetPage(pageCreatePayload(createDraft, jsonResult.value))
          : await createAssetBusinessFlow(flowCreatePayload(createDraft, jsonResult.value));
      const nextItem = responseToView(props.activeTab, response.data);
      setCreateDraft(initialDraft(props.activeTab));
      setSelected(nextItem);
      setSelectedId(nextItem.id);
      setEditDraft(draftFromView(props.activeTab, nextItem));
      upsertItem(setItems, nextItem);
      setCreateState({ loading: false, success: `${meta.name}已创建`, traceId: response.trace_id });
      selectItem(nextItem.id);
    } catch (error: unknown) {
      setCreateState({ loading: false, error: errorMessage(error, `${meta.name}创建失败`) });
    }
  }

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: `请先登录后再保存${meta.name}` });
      return;
    }
    if (!canManageAssets) {
      setMutationState({ loading: false, error: '缺少 asset:manage 权限' });
      return;
    }
    if (!editDraft.name.trim()) {
      setMutationState({ loading: false, error: '名称不能为空' });
      return;
    }

    const jsonResult = parseJsonDraft(editDraft.jsonText, meta.jsonLabel);
    if (!jsonResult.ok) {
      setMutationState({ loading: false, error: jsonResult.error });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response =
        props.activeTab === 'pages'
          ? await updateAssetPage(selected.id, pageUpdatePayload(editDraft, jsonResult.value))
          : await updateAssetBusinessFlow(selected.id, flowUpdatePayload(editDraft, jsonResult.value));
      const nextItem = responseToView(props.activeTab, response.data);
      setSelected(nextItem);
      setEditDraft(draftFromView(props.activeTab, nextItem));
      upsertItem(setItems, nextItem);
      setMutationState({ loading: false, success: `${meta.name}已保存`, traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, `${meta.name}保存失败`) });
    }
  }

  async function submitPrototypeSync(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (props.activeTab !== 'pages') {
      return;
    }
    if (!props.signedIn) {
      setPrototypeSyncState({ loading: false, error: '请先登录后再同步原型' });
      return;
    }
    if (!canManageAssets) {
      setPrototypeSyncState({ loading: false, error: '缺少 asset:manage 权限' });
      return;
    }
    if (!prototypeSyncDraft.projectId.trim() || !prototypeSyncDraft.pagesJson.trim()) {
      setPrototypeSyncState({ loading: false, error: 'projectId 和 pages JSON 必填' });
      return;
    }
    const pagesResult = parsePrototypePages(prototypeSyncDraft.pagesJson);
    if (!pagesResult.ok) {
      setPrototypeSyncState({ loading: false, error: pagesResult.error });
      return;
    }
    setPrototypeSyncState({ loading: true });
    try {
      const response = await syncPrototypePages({
        projectId: prototypeSyncDraft.projectId,
        source: prototypeSyncDraft.source,
        connectorRef: prototypeSyncDraft.connectorRef,
        sourceVersion: prototypeSyncDraft.sourceVersion,
        dryRun: prototypeSyncDraft.dryRun,
        pages: pagesResult.value
      });
      setPrototypeSyncResult(response.data);
      setPrototypeSyncState({
        loading: false,
        success: `${prototypeSyncDraft.dryRun ? '预检' : '同步'}完成：${response.data.created} 创建，${response.data.updated} 更新，${response.data.failed} 失败`,
        traceId: response.trace_id
      });
      if (!prototypeSyncDraft.dryRun && response.data.failed === 0) {
        void refreshAssets();
      }
    } catch (error: unknown) {
      setPrototypeSyncState({ loading: false, error: errorMessage(error, '原型同步失败') });
    }
  }

  return (
    <section className="asset-workbench-layout">
      <div className="asset-main-stack">
        <section className="panel module-panel asset-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <Archive size={20} />
              </div>
              <div>
                <span className="eyebrow">Asset Library</span>
                <h2>资产库</h2>
              </div>
            </div>
            <button className="secondary-button" type="button" disabled={disabled} onClick={refreshAssets}>
              <RefreshCw size={16} />
              刷新
            </button>
          </div>

          <div className="asset-tab-strip" aria-label="资产类型">
            {props.tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  className={`asset-tab ${props.activeTab === tab.key ? 'active' : ''}`}
                  type="button"
                  key={tab.key}
                  disabled={!tab.enabled}
                  onClick={() => props.onSelectTab(tab.key)}
                  title={tab.label}
                >
                  <Icon size={15} />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>

          <form className="asset-filter-bar" onSubmit={(event) => event.preventDefault()}>
            <label className="field" htmlFor={`asset-${props.activeTab}-filter-project`}>
              <span>projectId</span>
              <input
                id={`asset-${props.activeTab}-filter-project`}
                value={filters.projectId}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))}
                placeholder="proj-payments"
              />
            </label>
            <label className="field" htmlFor={`asset-${props.activeTab}-filter-status`}>
              <span>status</span>
              <select
                id={`asset-${props.activeTab}-filter-status`}
                value={filters.status}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">全部状态</option>
                {meta.statuses.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
            {props.activeTab === 'pages' && (
              <label className="field" htmlFor="asset-page-filter-source">
                <span>source</span>
                <select
                  id="asset-page-filter-source"
                  value={filters.source}
                  disabled={disabled}
                  onChange={(event) => setFilters((current) => ({ ...current, source: event.target.value }))}
                >
                  <option value="">全部来源</option>
                  {ASSET_PAGE_SOURCES.map((source) => (
                    <option key={source} value={source}>
                      {source}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="field" htmlFor={`asset-${props.activeTab}-filter-keyword`}>
              <span>keyword</span>
              <input
                id={`asset-${props.activeTab}-filter-keyword`}
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder={props.activeTab === 'pages' ? '名称 / URL / 来源' : '名称 / 描述 / 编码'}
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={refreshAssets}>
                <Search size={14} />
                查询
              </button>
              <button className="mini-button" type="button" disabled={disabled} onClick={() => setFilters(initialFilters)}>
                <XCircle size={14} />
                清空
              </button>
            </div>
          </form>

          <div className="table-wrap asset-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{meta.tableTitle}</th>
                  <th>项目</th>
                  <th>{props.activeTab === 'pages' ? 'URL / 来源' : '优先级'}</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.length > 0 ? (
                  visibleItems.map((item) => (
                    <tr className={selectedId === item.id ? 'selected-row' : ''} key={item.id || item.name}>
                      <td>
                        <strong className="table-primary">{item.name}</strong>
                        <span className="table-secondary">{item.code ?? item.id ?? '-'}</span>
                      </td>
                      <td>{item.projectId ?? '-'}</td>
                      <td>
                        {props.activeTab === 'pages' ? (
                          <div className="asset-source-cell">
                            <span>{item.urlPattern ?? '-'}</span>
                            <em>{[item.sourceRef, item.sourceVersion].filter(Boolean).join(' · ') || item.source || '-'}</em>
                          </div>
                        ) : (
                          item.priority ?? '-'
                        )}
                      </td>
                      <td>
                        <AssetStatusPill value={item.status} />
                      </td>
                      <td>{formatDate(item.updatedAt ?? item.createdAt)}</td>
                      <td>
                        <button className="mini-button" type="button" onClick={() => selectItem(item.id)} disabled={!item.id}>
                          <Eye size={14} />
                          详情
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={6}>
                      {props.signedIn ? (loadState.loading ? '加载中' : `暂无${meta.name}`) : '请先登录'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <StateLine state={loadState} />
        </section>

        <section className="panel module-panel asset-panel">
          <div className="section-heading">
            <div className="section-icon">
              <FilePlus2 size={20} />
            </div>
            <div>
              <span className="eyebrow">Create</span>
              <h2>新建{meta.name}</h2>
            </div>
          </div>
          <StructuredAssetForm
            activeTab={props.activeTab}
            draft={createDraft}
            disabled={createDisabled}
            jsonLabel={meta.jsonLabel}
            onChange={setCreateDraft}
            onSubmit={submitCreate}
            submitLabel={`创建${meta.shortName}`}
          />
          <StateLine state={createState} />
        </section>
      </div>

      <aside className="side-stack asset-side-stack">
        <section className="panel insight-panel">
          <h2>WP3 接口状态</h2>
          <div className="document-health-grid">
            <StatusMetric label="服务" value={health?.service ?? 'asset-service'} />
            <StatusMetric label="状态" value={health?.status ?? (props.signedIn ? '等待响应' : '等待登录')} pill />
            <StatusMetric label={meta.shortName} value={String(items.length)} />
            {meta.statuses.map((status) => (
              <StatusMetric key={status} label={status} value={String(statusCounts[status] ?? 0)} />
            ))}
          </div>
          {loadState.error && (
            <div className="inline-error">
              <strong>同步失败</strong>
              <span>{loadState.error}</span>
            </div>
          )}
        </section>

        {props.activeTab === 'pages' && (
          <section className="panel insight-panel">
            <h2>原型同步</h2>
            <form className="asset-form" onSubmit={submitPrototypeSync}>
              <div className="asset-form-grid">
                <label className="field" htmlFor="asset-prototype-project">
                  <span>projectId</span>
                  <input
                    id="asset-prototype-project"
                    value={prototypeSyncDraft.projectId}
                    disabled={prototypeSyncState.loading}
                    onChange={(event) => setPrototypeSyncDraft((current) => ({ ...current, projectId: event.target.value }))}
                    placeholder="proj-payments"
                  />
                </label>
                <label className="field" htmlFor="asset-prototype-source">
                  <span>source</span>
                  <select
                    id="asset-prototype-source"
                    value={prototypeSyncDraft.source}
                    disabled={prototypeSyncState.loading}
                    onChange={(event) => setPrototypeSyncDraft((current) => ({ ...current, source: event.target.value }))}
                  >
                    <option value="FIGMA">FIGMA</option>
                    <option value="LANHU">LANHU</option>
                    <option value="AXURE">AXURE</option>
                  </select>
                </label>
                <label className="field" htmlFor="asset-prototype-version">
                  <span>sourceVersion</span>
                  <input
                    id="asset-prototype-version"
                    value={prototypeSyncDraft.sourceVersion}
                    disabled={prototypeSyncState.loading}
                    onChange={(event) => setPrototypeSyncDraft((current) => ({ ...current, sourceVersion: event.target.value }))}
                    placeholder="v42"
                  />
                </label>
                <label className="toggle-field" htmlFor="asset-prototype-dry-run">
                  <input
                    id="asset-prototype-dry-run"
                    type="checkbox"
                    checked={prototypeSyncDraft.dryRun}
                    disabled={prototypeSyncState.loading}
                    onChange={(event) => setPrototypeSyncDraft((current) => ({ ...current, dryRun: event.target.checked }))}
                  />
                  <span>dryRun</span>
                </label>
              </div>
              <label className="field" htmlFor="asset-prototype-pages">
                <span>pages</span>
                <textarea
                  id="asset-prototype-pages"
                  className="compact-textarea schema-textarea"
                  value={prototypeSyncDraft.pagesJson}
                  disabled={prototypeSyncState.loading}
                  onChange={(event) => setPrototypeSyncDraft((current) => ({ ...current, pagesJson: event.target.value }))}
                />
              </label>
              <div className="document-actions">
                <button
                  className="primary-button"
                  type="submit"
                  disabled={!props.signedIn || !canManageAssets || prototypeSyncState.loading}
                >
                  <Save size={16} />
                  {prototypeSyncDraft.dryRun ? '预检' : '同步'}
                </button>
              </div>
            </form>
            {prototypeSyncResult && (
              <div className="asset-import-result">
                <strong>{prototypeSyncResult.source}</strong>
                <span>{prototypeSyncResult.totalRows} 页 · {prototypeSyncResult.created} 创建 · {prototypeSyncResult.updated} 更新 · {prototypeSyncResult.skipped} 跳过 · {prototypeSyncResult.failed} 失败</span>
              </div>
            )}
            <StateLine state={prototypeSyncState} />
          </section>
        )}

        <section className="panel insight-panel asset-detail-panel">
          <div className="panel-title-row">
            <h2>{meta.name}详情</h2>
            {selected && <AssetStatusPill value={selected.status} />}
          </div>

          {selected ? (
            <div className="asset-detail-stack">
              <div className="resource-summary">
                <strong>{selected.name}</strong>
                <div>
                  <span>projectId</span>
                  <em>{selected.projectId ?? '-'}</em>
                </div>
                <div>
                  <span>code</span>
                  <em>{selected.code ?? '-'}</em>
                </div>
                <div>
                  <span>id</span>
                  <em>{selected.id}</em>
                </div>
                <div>
                  <span>{props.activeTab === 'pages' ? 'urlPattern' : 'priority'}</span>
                  <em>{props.activeTab === 'pages' ? selected.urlPattern ?? '-' : selected.priority ?? '-'}</em>
                </div>
                <div>
                  <span>createdAt</span>
                  <em>{formatDate(selected.createdAt)}</em>
                </div>
              </div>

              <div className="asset-source-trace">
                <strong>{props.activeTab === 'pages' ? '原型来源' : '流程摘要'}</strong>
                {props.activeTab === 'pages' ? (
                  <>
                    <div>
                      <span>source</span>
                      <em>{selected.source ?? '-'}</em>
                    </div>
                    <div>
                      <span>sourceRef</span>
                      <em>{selected.sourceRef ?? '-'}</em>
                    </div>
                    <div>
                      <span>sourceVersion</span>
                      <em>{selected.sourceVersion ?? '-'}</em>
                    </div>
                    <div>
                      <span>screenshotUrl</span>
                      {selected.screenshotUrl ? (
                        <a href={selected.screenshotUrl} target="_blank" rel="noreferrer">
                          {selected.screenshotUrl}
                        </a>
                      ) : (
                        <em>-</em>
                      )}
                    </div>
                  </>
                ) : (
                  <div>
                    <span>description</span>
                    <em>{selected.description ?? '-'}</em>
                  </div>
                )}
              </div>

              <div className="asset-schema-preview">
                <strong>{meta.jsonLabel}</strong>
                <pre>{formatJsonText(selected.jsonText)}</pre>
              </div>

              <StructuredAssetForm
                activeTab={props.activeTab}
                draft={editDraft}
                disabled={editDisabled}
                jsonLabel={meta.jsonLabel}
                onChange={setEditDraft}
                onSubmit={submitEdit}
                submitLabel={`保存${meta.shortName}`}
                compact
                selectedStatus={selected.status}
              />
              <StateLine state={mutationState} />
              <StateLine state={detailState} />
            </div>
          ) : (
            <div className="empty-state compact">
              <Pencil size={20} />
              <div>
                <strong>{detailState.loading ? '正在加载详情' : props.signedIn ? `未选择${meta.name}` : '等待登录'}</strong>
                <span>{detailState.error ?? `从列表中选择${meta.name}`}</span>
              </div>
            </div>
          )}
        </section>
      </aside>
    </section>
  );
}

function StructuredAssetForm(props: {
  activeTab: StructuredTabKey;
  compact?: boolean;
  disabled: boolean;
  draft: StructuredDraft;
  jsonLabel: string;
  onChange: (updater: (current: StructuredDraft) => StructuredDraft) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  selectedStatus?: string;
  submitLabel: string;
}) {
  const statusOptions = props.selectedStatus ? statusOptionsFor(props.activeTab, props.selectedStatus) : structuredMeta(props.activeTab).statuses;

  return (
    <form className="asset-form" onSubmit={props.onSubmit}>
      <div className="asset-form-grid">
        {!props.compact && (
          <label className="field" htmlFor={`asset-${props.activeTab}-project`}>
            <span>projectId<b>*</b></span>
            <input
              id={`asset-${props.activeTab}-project`}
              value={props.draft.projectId}
              disabled={props.disabled}
              onChange={(event) => props.onChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="proj-payments"
            />
          </label>
        )}
        <label className="field" htmlFor={`asset-${props.activeTab}-name`}>
          <span>名称<b>*</b></span>
          <input
            id={`asset-${props.activeTab}-name`}
            value={props.draft.name}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, name: event.target.value }))}
            placeholder={props.activeTab === 'pages' ? '结算页' : '下单主流程'}
          />
        </label>
        {props.activeTab === 'pages' ? (
          <>
            <label className="field" htmlFor={`asset-${props.activeTab}-url`}>
              <span>urlPattern</span>
              <input
                id={`asset-${props.activeTab}-url`}
                value={props.draft.urlPattern}
                disabled={props.disabled}
                onChange={(event) => props.onChange((current) => ({ ...current, urlPattern: event.target.value }))}
                placeholder="/checkout/**"
              />
            </label>
            <label className="field" htmlFor={`asset-${props.activeTab}-source`}>
              <span>source</span>
              <select
                id={`asset-${props.activeTab}-source`}
                value={props.draft.source}
                disabled={props.disabled}
                onChange={(event) => props.onChange((current) => ({ ...current, source: event.target.value }))}
              >
                {ASSET_PAGE_SOURCES.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor={`asset-${props.activeTab}-source-ref`}>
              <span>sourceRef</span>
              <input
                id={`asset-${props.activeTab}-source-ref`}
                value={props.draft.sourceRef}
                disabled={props.disabled}
                onChange={(event) => props.onChange((current) => ({ ...current, sourceRef: event.target.value }))}
                placeholder="figma-node-1"
              />
            </label>
            <label className="field" htmlFor={`asset-${props.activeTab}-source-version`}>
              <span>sourceVersion</span>
              <input
                id={`asset-${props.activeTab}-source-version`}
                value={props.draft.sourceVersion}
                disabled={props.disabled}
                onChange={(event) => props.onChange((current) => ({ ...current, sourceVersion: event.target.value }))}
                placeholder="figma-v42"
              />
            </label>
            <label className="field" htmlFor={`asset-${props.activeTab}-screenshot`}>
              <span>screenshotUrl</span>
              <input
                id={`asset-${props.activeTab}-screenshot`}
                value={props.draft.screenshotUrl}
                disabled={props.disabled}
                onChange={(event) => props.onChange((current) => ({ ...current, screenshotUrl: event.target.value }))}
                placeholder="https://cdn.example.test/page.png"
              />
            </label>
          </>
        ) : (
          <>
            <label className="field" htmlFor={`asset-${props.activeTab}-priority`}>
              <span>priority</span>
              <select
                id={`asset-${props.activeTab}-priority`}
                value={props.draft.priority}
                disabled={props.disabled}
                onChange={(event) => props.onChange((current) => ({ ...current, priority: event.target.value }))}
              >
                {ASSET_REQUIREMENT_PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {priority}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}
        <label className="field" htmlFor={`asset-${props.activeTab}-status`}>
          <span>status</span>
          <select
            id={`asset-${props.activeTab}-status`}
            value={props.draft.status}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, status: event.target.value }))}
          >
            {statusOptions.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </label>
      </div>
      {props.activeTab === 'flows' && (
        <label className="field" htmlFor={`asset-${props.activeTab}-description`}>
          <span>描述</span>
          <textarea
            id={`asset-${props.activeTab}-description`}
            className="compact-textarea"
            value={props.draft.description}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, description: event.target.value }))}
          />
        </label>
      )}
      <label className="field" htmlFor={`asset-${props.activeTab}-json`}>
        <span>{props.jsonLabel}</span>
        <textarea
          id={`asset-${props.activeTab}-json`}
          className="compact-textarea schema-textarea"
          value={props.draft.jsonText}
          disabled={props.disabled}
          onChange={(event) => props.onChange((current) => ({ ...current, jsonText: event.target.value }))}
        />
      </label>
      <div className="document-actions">
        <button className="primary-button" type="submit" disabled={props.disabled || !props.draft.name.trim()}>
          <Save size={16} />
          {props.submitLabel}
        </button>
      </div>
    </form>
  );
}

function assetIdFromHash(tab: StructuredTabKey) {
  const parts = window.location.hash.replace(/^#\/?/, '').split('/');
  if (parts[0] !== 'asset-library' || parts[1] !== tab) {
    return '';
  }
  return parts[2] ? decodeURIComponent(parts[2]) : '';
}

function structuredMeta(tab: StructuredTabKey) {
  if (tab === 'pages') {
    return {
      icon: ClipboardList,
      jsonLabel: 'componentTree',
      name: '页面资产',
      shortName: '页面',
      statuses: ASSET_PAGE_STATUSES,
      tableTitle: '页面'
    };
  }
  return {
    icon: GitBranch,
    jsonLabel: 'flowJson',
    name: '业务流资产',
    shortName: '业务流',
    statuses: ASSET_FLOW_STATUSES,
    tableTitle: '业务流'
  };
}

function initialDraft(tab: StructuredTabKey): StructuredDraft {
  return {
    projectId: '',
    name: '',
    description: '',
    urlPattern: '',
    source: 'MANUAL',
    sourceRef: '',
    sourceVersion: '',
    screenshotUrl: '',
    priority: 'MEDIUM',
    status: tab === 'pages' ? 'ACTIVE' : 'DRAFT',
    jsonText: ''
  };
}

function buildPageFilters(filters: StructuredFilters): AssetPageFilters {
  return {
    size: 50,
    projectId: filters.projectId,
    status: filters.status,
    keyword: filters.keyword,
    source: filters.source
  };
}

function buildFlowFilters(filters: StructuredFilters): AssetBusinessFlowFilters {
  return {
    size: 50,
    projectId: filters.projectId,
    status: filters.status,
    keyword: filters.keyword
  };
}

function pageToView(page: AssetPageView): StructuredAssetView {
  return {
    id: page.id,
    code: page.code,
    name: page.name,
    projectId: page.projectId,
    status: page.status,
    createdAt: page.createdAt,
    updatedAt: page.updatedAt,
    urlPattern: page.urlPattern,
    source: page.source,
    sourceRef: page.sourceRef,
    sourceVersion: page.sourceVersion,
    screenshotUrl: page.screenshotUrl,
    jsonText: page.componentTree
  };
}

function flowToView(flow: AssetBusinessFlowView): StructuredAssetView {
  return {
    id: flow.id,
    code: flow.code,
    name: flow.name,
    description: flow.description,
    projectId: flow.projectId,
    status: flow.status,
    createdAt: flow.createdAt,
    updatedAt: flow.updatedAt,
    priority: flow.priority,
    jsonText: flow.flowJson
  };
}

function listToViews(tab: StructuredTabKey, items: AssetPageView[] | AssetBusinessFlowView[]) {
  return tab === 'pages' ? (items as AssetPageView[]).map(pageToView) : (items as AssetBusinessFlowView[]).map(flowToView);
}

function responseToView(tab: StructuredTabKey, item: AssetPageView | AssetBusinessFlowView) {
  return tab === 'pages' ? pageToView(item as AssetPageView) : flowToView(item as AssetBusinessFlowView);
}

function draftFromView(tab: StructuredTabKey, asset: StructuredAssetView): StructuredDraft {
  return {
    projectId: asset.projectId ?? '',
    name: asset.name,
    description: asset.description ?? '',
    urlPattern: asset.urlPattern ?? '',
    source: asset.source ?? 'MANUAL',
    sourceRef: asset.sourceRef ?? '',
    sourceVersion: asset.sourceVersion ?? '',
    screenshotUrl: asset.screenshotUrl ?? '',
    priority: asset.priority ?? 'MEDIUM',
    status: asset.status || (tab === 'pages' ? 'ACTIVE' : 'DRAFT'),
    jsonText: formatJsonText(asset.jsonText, false)
  };
}

function pageCreatePayload(draft: StructuredDraft, componentTree: unknown): AssetPagePayload {
  return {
    projectId: draft.projectId,
    name: draft.name,
    urlPattern: draft.urlPattern,
    source: draft.source,
    sourceRef: draft.sourceRef,
    sourceVersion: draft.sourceVersion,
    componentTree,
    screenshotUrl: draft.screenshotUrl,
    status: draft.status
  };
}

function pageUpdatePayload(draft: StructuredDraft, componentTree: unknown): AssetPagePayload {
  return {
    name: draft.name,
    urlPattern: draft.urlPattern,
    source: draft.source,
    sourceRef: draft.sourceRef,
    sourceVersion: draft.sourceVersion,
    componentTree,
    screenshotUrl: draft.screenshotUrl,
    status: draft.status
  };
}

function flowCreatePayload(draft: StructuredDraft, flowJson: unknown): AssetBusinessFlowPayload {
  return {
    projectId: draft.projectId,
    name: draft.name,
    description: draft.description,
    flowJson,
    priority: draft.priority,
    status: draft.status
  };
}

function flowUpdatePayload(draft: StructuredDraft, flowJson: unknown): AssetBusinessFlowPayload {
  return {
    name: draft.name,
    description: draft.description,
    flowJson,
    priority: draft.priority,
    status: draft.status
  };
}

function filterItems(items: StructuredAssetView[], filters: StructuredFilters, tab: StructuredTabKey) {
  const keyword = filters.keyword.trim().toLowerCase();
  return items.filter((item) => {
    if (filters.projectId.trim() && item.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && item.status !== filters.status.trim()) {
      return false;
    }
    if (tab === 'pages' && filters.source.trim() && item.source !== filters.source.trim()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [item.name, item.description, item.code, item.urlPattern, item.sourceRef, item.sourceVersion, item.projectId]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function countByStatus(items: StructuredAssetView[]) {
  return items.reduce<Record<string, number>>((counts, item) => {
    counts[item.status] = (counts[item.status] ?? 0) + 1;
    return counts;
  }, {});
}

function statusOptionsFor(tab: StructuredTabKey, status: string) {
  const transitions = tab === 'pages' ? pageStatusTransitions : flowStatusTransitions;
  return transitions[status] ?? structuredMeta(tab).statuses;
}

function parseJsonDraft(value: string, label: string): { ok: true; value?: unknown } | { error: string; ok: false } {
  if (!value.trim()) {
    return { ok: true, value: undefined };
  }
  try {
    return { ok: true, value: JSON.parse(value) };
  } catch {
    return { ok: false, error: `${label} 必须是合法 JSON` };
  }
}

function parsePrototypePages(value: string): { ok: true; value: AssetPrototypeSyncPagePayload[] } | { error: string; ok: false } {
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return { ok: true, value: parsed as AssetPrototypeSyncPagePayload[] };
    }
    if (parsed && typeof parsed === 'object' && Array.isArray((parsed as { pages?: unknown }).pages)) {
      return { ok: true, value: (parsed as { pages: AssetPrototypeSyncPagePayload[] }).pages };
    }
    return { ok: false, error: 'pages 必须是数组或包含 pages 数组的 JSON' };
  } catch {
    return { ok: false, error: 'pages 必须是合法 JSON' };
  }
}

function upsertItem(
  setter: (updater: (current: StructuredAssetView[]) => StructuredAssetView[]) => void,
  item: StructuredAssetView
) {
  setter((current) => {
    const existing = current.findIndex((value) => value.id === item.id);
    if (existing < 0) {
      return [item, ...current];
    }
    return current.map((value) => (value.id === item.id ? item : value));
  });
}

function formatJsonText(value?: string, emptyFallback = true) {
  if (!value?.trim()) {
    return emptyFallback ? '-' : '';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">处理中</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return (
      <span className="document-state-line success">
        {props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}
      </span>
    );
  }
  if (props.state.traceId) {
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function StatusMetric(props: { label: string; value: string; pill?: boolean }) {
  return (
    <div className="status-item">
      <span>{props.label}</span>
      {props.pill ? <AssetStatusPill value={props.value} /> : <strong>{props.value}</strong>}
    </div>
  );
}

function AssetStatusPill(props: { value: string }) {
  const value = props.value || 'UNKNOWN';
  const normalized = value.toUpperCase();
  const tone = ['APPROVED', 'ACTIVE', 'UP', 'ON', 'IMPORT', 'MANUAL'].includes(normalized)
    ? 'positive'
    : ['DRAFT', 'REVIEWING', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(normalized)
      ? 'pending'
      : ['DEPRECATED', 'REMOVED', 'ARCHIVED', 'FAILED', 'DOWN', 'OFF', 'ERROR'].includes(normalized)
        ? 'negative'
        : 'neutral';
  return <span className={`status-pill ${tone}`}>{value}</span>;
}
