import {
  Archive,
  CheckCircle2,
  ClipboardList,
  Eye,
  FilePlus2,
  FileText,
  GitBranch,
  Link2,
  Pencil,
  RefreshCw,
  Save,
  Search,
  Send,
  XCircle
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_REQUIREMENT_SOURCES,
  ASSET_REQUIREMENT_STATUSES,
  createAssetRequirement,
  fetchAssetHealth,
  fetchAssetRequirement,
  fetchAssetRequirements,
  fetchRequirementTraceLinks,
  updateAssetRequirement,
  type AssetHealth,
  type AssetRequirementFilters,
  type AssetRequirementPayload,
  type AssetRequirementView,
  type TraceLinkView
} from '../api/assets';
import { hasPermission } from '../permissions';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type RequirementDraft = {
  projectId: string;
  title: string;
  description: string;
  source: string;
  sourceRef: string;
  sourceUrl: string;
  acceptanceCriteria: string;
  status: string;
  priority: string;
  tags: string;
};

type RequirementFilters = {
  projectId: string;
  status: string;
  source: string;
  sourceRef: string;
  keyword: string;
};

const initialRequirementDraft: RequirementDraft = {
  projectId: '',
  title: '',
  description: '',
  source: 'MANUAL',
  sourceRef: '',
  sourceUrl: '',
  acceptanceCriteria: '',
  status: 'DRAFT',
  priority: 'MEDIUM',
  tags: ''
};

const initialFilters: RequirementFilters = {
  projectId: '',
  status: '',
  source: '',
  sourceRef: '',
  keyword: ''
};

const assetTabs = [
  { key: 'requirements', label: '需求', icon: FileText, enabled: true },
  { key: 'apis', label: 'API', icon: Link2, enabled: false },
  { key: 'pages', label: '页面', icon: ClipboardList, enabled: false },
  { key: 'flows', label: '业务流', icon: GitBranch, enabled: false },
  { key: 'cases', label: '用例', icon: CheckCircle2, enabled: false },
  { key: 'trace', label: '追踪矩阵', icon: GitBranch, enabled: false }
] as const;

const statusTransitionMap: Record<string, string[]> = {
  DRAFT: ['REVIEWING'],
  REVIEWING: ['DRAFT', 'APPROVED'],
  APPROVED: ['DEPRECATED'],
  DEPRECATED: ['DRAFT']
};

const statusActionLabel: Record<string, string> = {
  DRAFT: '退回草稿',
  REVIEWING: '提交评审',
  APPROVED: '批准',
  DEPRECATED: '废弃'
};

export function AssetWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canReadAssets = hasPermission(props.currentUser, 'asset:read');
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const canReviewAssets = hasPermission(props.currentUser, 'asset:review');
  const [health, setHealth] = useState<AssetHealth | null>(null);
  const [requirements, setRequirements] = useState<AssetRequirementView[]>([]);
  const [filters, setFilters] = useState<RequirementFilters>(initialFilters);
  const [selectedRequirementId, setSelectedRequirementId] = useState(requirementIdFromHash());
  const [selectedRequirement, setSelectedRequirement] = useState<AssetRequirementView | null>(null);
  const [traceLinks, setTraceLinks] = useState<TraceLinkView[]>([]);
  const [createDraft, setCreateDraft] = useState<RequirementDraft>(initialRequirementDraft);
  const [editDraft, setEditDraft] = useState<RequirementDraft>(initialRequirementDraft);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [createState, setCreateState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });

  const refreshRequirements = useCallback(async () => {
    if (!props.signedIn || !canReadAssets) {
      setHealth(null);
      setRequirements([]);
      setSelectedRequirement(null);
      setTraceLinks([]);
      setLoadState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, requirementResult] = await Promise.allSettled([
      fetchAssetHealth(),
      fetchAssetRequirements(buildRequirementFilters(filters))
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, '资产服务健康检查失败'));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(errorMessage(requirementResult.reason, '需求资产加载失败'));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canReadAssets, filters, props.signedIn]);

  useEffect(() => {
    void refreshRequirements();
  }, [refreshRequirements]);

  useEffect(() => {
    function syncRequirementFromHash() {
      const requirementId = requirementIdFromHash();
      if (requirementId) {
        setSelectedRequirementId(requirementId);
      }
    }

    window.addEventListener('hashchange', syncRequirementFromHash);
    return () => window.removeEventListener('hashchange', syncRequirementFromHash);
  }, []);

  useEffect(() => {
    if (!selectedRequirementId && requirements[0]?.id) {
      setSelectedRequirementId(requirements[0].id);
    }
  }, [requirements, selectedRequirementId]);

  const reloadRequirementDetail = useCallback(async () => {
    if (!props.signedIn || !canReadAssets || !selectedRequirementId) {
      setSelectedRequirement(null);
      setTraceLinks([]);
      setDetailState({ loading: false });
      return;
    }

    setDetailState({ loading: true });
    const [detailResult, linkResult] = await Promise.allSettled([
      fetchAssetRequirement(selectedRequirementId),
      fetchRequirementTraceLinks(selectedRequirementId)
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (detailResult.status === 'fulfilled') {
      setSelectedRequirement(detailResult.value.data);
      setEditDraft(requirementDraftFromView(detailResult.value.data));
      upsertRequirement(setRequirements, detailResult.value.data);
      traceIds.push(detailResult.value.trace_id);
    } else {
      setSelectedRequirement(null);
      errors.push(errorMessage(detailResult.reason, '需求详情加载失败'));
    }

    if (linkResult.status === 'fulfilled') {
      setTraceLinks(linkResult.value.data.items);
      traceIds.push(linkResult.value.trace_id);
    } else {
      setTraceLinks([]);
      errors.push(errorMessage(linkResult.reason, '追踪链接加载失败'));
    }

    setDetailState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canReadAssets, props.signedIn, selectedRequirementId]);

  useEffect(() => {
    void reloadRequirementDetail();
  }, [reloadRequirementDetail]);

  const visibleRequirements = useMemo(() => filterRequirements(requirements, filters), [filters, requirements]);
  const statusCounts = useMemo(() => countRequirementsByStatus(requirements), [requirements]);
  const disabled = !props.signedIn || loadState.loading;
  const createDisabled = disabled || createState.loading || !canManageAssets;
  const editDisabled = disabled || mutationState.loading || !canManageAssets || !selectedRequirement;
  const reviewDisabled = disabled || mutationState.loading || !canReviewAssets || !selectedRequirement;

  function selectRequirement(requirementId: string) {
    if (!requirementId) {
      return;
    }
    setSelectedRequirementId(requirementId);
    const targetHash = `#asset-library/requirements/${encodeURIComponent(requirementId)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setCreateState({ loading: false, error: '请先登录后再创建需求资产' });
      return;
    }
    if (!canManageAssets) {
      setCreateState({ loading: false, error: '缺少 asset:manage 权限' });
      return;
    }
    if (!createDraft.projectId.trim() || !createDraft.title.trim()) {
      setCreateState({ loading: false, error: 'projectId 和标题必填' });
      return;
    }

    setCreateState({ loading: true });
    try {
      const response = await createAssetRequirement(draftToCreatePayload(createDraft));
      setCreateDraft(initialRequirementDraft);
      setSelectedRequirement(response.data);
      setSelectedRequirementId(response.data.id);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setCreateState({ loading: false, success: '需求资产已创建', traceId: response.trace_id });
      if (response.data.id) {
        selectRequirement(response.data.id);
      }
    } catch (error: unknown) {
      setCreateState({ loading: false, error: errorMessage(error, '需求资产创建失败') });
    }
  }

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedRequirement) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: '请先登录后再保存需求资产' });
      return;
    }
    if (!canManageAssets) {
      setMutationState({ loading: false, error: '缺少 asset:manage 权限' });
      return;
    }
    if (!editDraft.title.trim()) {
      setMutationState({ loading: false, error: '标题不能为空' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateAssetRequirement(selectedRequirement.id, draftToUpdatePayload(selectedRequirement, editDraft));
      setSelectedRequirement(response.data);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setMutationState({ loading: false, success: '需求资产已保存', traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, '需求资产保存失败') });
    }
  }

  async function changeStatus(nextStatus: string) {
    if (!selectedRequirement) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: '请先登录后再流转状态' });
      return;
    }
    if (!canReviewAssets) {
      setMutationState({ loading: false, error: '缺少 asset:review 权限' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateAssetRequirement(
        selectedRequirement.id,
        draftToUpdatePayload(selectedRequirement, requirementDraftFromView(selectedRequirement), nextStatus)
      );
      setSelectedRequirement(response.data);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setMutationState({ loading: false, success: `状态已流转为 ${response.data.status}`, traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, '状态流转失败') });
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
            <button className="secondary-button" type="button" disabled={!props.signedIn || loadState.loading} onClick={refreshRequirements}>
              <RefreshCw size={16} />
              刷新
            </button>
          </div>

          <div className="asset-tab-strip" aria-label="资产类型">
            {assetTabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  className={`asset-tab ${tab.enabled ? 'active' : ''}`}
                  type="button"
                  key={tab.key}
                  disabled={!tab.enabled}
                  title={tab.label}
                >
                  <Icon size={15} />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>

          <form className="asset-filter-bar" onSubmit={(event) => event.preventDefault()}>
            <label className="field" htmlFor="asset-filter-project">
              <span>projectId</span>
              <input
                id="asset-filter-project"
                value={filters.projectId}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))}
                placeholder="proj-payments"
              />
            </label>
            <label className="field" htmlFor="asset-filter-status">
              <span>status</span>
              <select
                id="asset-filter-status"
                value={filters.status}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">全部状态</option>
                {ASSET_REQUIREMENT_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-filter-source">
              <span>source</span>
              <select
                id="asset-filter-source"
                value={filters.source}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, source: event.target.value }))}
              >
                <option value="">全部来源</option>
                {ASSET_REQUIREMENT_SOURCES.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-filter-source-ref">
              <span>sourceRef</span>
              <input
                id="asset-filter-source-ref"
                value={filters.sourceRef}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, sourceRef: event.target.value }))}
                placeholder="PRD-2026-001"
              />
            </label>
            <label className="field" htmlFor="asset-filter-keyword">
              <span>keyword</span>
              <input
                id="asset-filter-keyword"
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder="标题 / 描述 / 标签"
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={refreshRequirements}>
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
                  <th>需求</th>
                  <th>项目</th>
                  <th>状态</th>
                  <th>优先级</th>
                  <th>来源</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {visibleRequirements.length > 0 ? (
                  visibleRequirements.map((requirement) => (
                    <tr
                      className={selectedRequirementId === requirement.id ? 'selected-row' : ''}
                      key={requirement.id || requirement.title}
                    >
                      <td>
                        <strong className="table-primary">{requirement.title}</strong>
                        <span className="table-secondary">{requirement.id || '-'}</span>
                      </td>
                      <td>{requirement.projectId ?? '-'}</td>
                      <td>
                        <AssetStatusPill value={requirement.status} />
                      </td>
                      <td>{requirement.priority}</td>
                      <td>
                        <div className="asset-source-cell">
                          <span>{requirement.source}</span>
                          <em>{requirement.sourceRef ?? '-'}</em>
                        </div>
                      </td>
                      <td>{formatDate(requirement.updatedAt ?? requirement.createdAt)}</td>
                      <td>
                        <button className="mini-button" type="button" onClick={() => selectRequirement(requirement.id)} disabled={!requirement.id}>
                          <Eye size={14} />
                          详情
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={7}>
                      {props.signedIn ? (loadState.loading ? '加载中' : '暂无需求资产') : '请先登录'}
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
              <h2>新建需求资产</h2>
            </div>
          </div>

          <form className="asset-form" onSubmit={submitCreate}>
            <div className="asset-form-grid">
              <label className="field" htmlFor="asset-create-project">
                <span>projectId<b>*</b></span>
                <input
                  id="asset-create-project"
                  value={createDraft.projectId}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, projectId: event.target.value }))}
                  placeholder="proj-payments"
                />
              </label>
              <label className="field" htmlFor="asset-create-title">
                <span>标题<b>*</b></span>
                <input
                  id="asset-create-title"
                  value={createDraft.title}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, title: event.target.value }))}
                  placeholder="支持用户登录"
                />
              </label>
              <label className="field" htmlFor="asset-create-priority">
                <span>priority</span>
                <select
                  id="asset-create-priority"
                  value={createDraft.priority}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, priority: event.target.value }))}
                >
                  {ASSET_REQUIREMENT_PRIORITIES.map((priority) => (
                    <option key={priority} value={priority}>
                      {priority}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field" htmlFor="asset-create-source">
                <span>source</span>
                <select
                  id="asset-create-source"
                  value={createDraft.source}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, source: event.target.value }))}
                >
                  {ASSET_REQUIREMENT_SOURCES.map((source) => (
                    <option key={source} value={source}>
                      {source}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field" htmlFor="asset-create-source-ref">
                <span>sourceRef</span>
                <input
                  id="asset-create-source-ref"
                  value={createDraft.sourceRef}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, sourceRef: event.target.value }))}
                  placeholder="MANUAL-1"
                />
              </label>
              <label className="field" htmlFor="asset-create-source-url">
                <span>sourceUrl</span>
                <input
                  id="asset-create-source-url"
                  value={createDraft.sourceUrl}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, sourceUrl: event.target.value }))}
                  placeholder="https://docs.example.test/prd"
                />
              </label>
            </div>
            <label className="field" htmlFor="asset-create-description">
              <span>描述</span>
              <textarea
                id="asset-create-description"
                className="compact-textarea"
                value={createDraft.description}
                disabled={createDisabled}
                onChange={(event) => setCreateDraft((current) => ({ ...current, description: event.target.value }))}
              />
            </label>
            <label className="field" htmlFor="asset-create-acceptance">
              <span>验收标准</span>
              <textarea
                id="asset-create-acceptance"
                className="compact-textarea"
                value={createDraft.acceptanceCriteria}
                disabled={createDisabled}
                onChange={(event) => setCreateDraft((current) => ({ ...current, acceptanceCriteria: event.target.value }))}
              />
            </label>
            <label className="field" htmlFor="asset-create-tags">
              <span>tags</span>
              <input
                id="asset-create-tags"
                value={createDraft.tags}
                disabled={createDisabled}
                onChange={(event) => setCreateDraft((current) => ({ ...current, tags: event.target.value }))}
                placeholder="auth, mobile"
              />
            </label>
            <div className="document-actions">
              <button
                className="primary-button"
                type="submit"
                disabled={createDisabled || !createDraft.projectId.trim() || !createDraft.title.trim()}
              >
                <Save size={16} />
                创建需求
              </button>
              <StateLine state={createState} />
            </div>
          </form>
        </section>
      </div>

      <aside className="side-stack asset-side-stack">
        <section className="panel insight-panel">
          <h2>WP3 接口状态</h2>
          <div className="document-health-grid">
            <StatusMetric label="服务" value={health?.service ?? 'asset-service'} />
            <StatusMetric label="状态" value={health?.status ?? (props.signedIn ? '等待响应' : '等待登录')} pill />
            <StatusMetric label="需求资产" value={String(requirements.length)} />
            <StatusMetric label="DRAFT" value={String(statusCounts.DRAFT ?? 0)} />
            <StatusMetric label="REVIEWING" value={String(statusCounts.REVIEWING ?? 0)} />
            <StatusMetric label="APPROVED" value={String(statusCounts.APPROVED ?? 0)} />
          </div>
          {loadState.error && (
            <div className="inline-error">
              <strong>同步失败</strong>
              <span>{loadState.error}</span>
            </div>
          )}
        </section>

        <section className="panel insight-panel asset-detail-panel">
          <div className="panel-title-row">
            <h2>需求详情</h2>
            {selectedRequirement && <AssetStatusPill value={selectedRequirement.status} />}
          </div>

          {selectedRequirement ? (
            <div className="asset-detail-stack">
              <div className="resource-summary">
                <strong>{selectedRequirement.title}</strong>
                <div>
                  <span>projectId</span>
                  <em>{selectedRequirement.projectId ?? '-'}</em>
                </div>
                <div>
                  <span>priority</span>
                  <em>{selectedRequirement.priority}</em>
                </div>
                <div>
                  <span>id</span>
                  <em>{selectedRequirement.id}</em>
                </div>
                <div>
                  <span>createdAt</span>
                  <em>{formatDate(selectedRequirement.createdAt)}</em>
                </div>
              </div>

              <div className="asset-source-trace">
                <strong>来源追踪</strong>
                <div>
                  <span>source</span>
                  <em>{selectedRequirement.source}</em>
                </div>
                <div>
                  <span>sourceRef</span>
                  <em>{selectedRequirement.sourceRef ?? '-'}</em>
                </div>
                <div>
                  <span>sourceUrl</span>
                  {selectedRequirement.sourceUrl ? (
                    <a href={selectedRequirement.sourceUrl} target="_blank" rel="noreferrer">
                      {selectedRequirement.sourceUrl}
                    </a>
                  ) : (
                    <em>-</em>
                  )}
                </div>
                <div>
                  <span>acceptanceCriteria</span>
                  <em>{selectedRequirement.acceptanceCriteria ?? '-'}</em>
                </div>
              </div>

              <form className="resource-edit-form asset-edit-form" onSubmit={submitEdit}>
                <label>
                  <span>标题</span>
                  <input
                    value={editDraft.title}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, title: event.target.value }))}
                  />
                </label>
                <label>
                  <span>priority</span>
                  <select
                    value={editDraft.priority}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, priority: event.target.value }))}
                  >
                    {ASSET_REQUIREMENT_PRIORITIES.map((priority) => (
                      <option key={priority} value={priority}>
                        {priority}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>描述</span>
                  <textarea
                    className="compact-textarea"
                    value={editDraft.description}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, description: event.target.value }))}
                  />
                </label>
                <label>
                  <span>tags</span>
                  <input
                    value={editDraft.tags}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, tags: event.target.value }))}
                  />
                </label>
                {canManageAssets && (
                  <button className="mini-button" type="submit" disabled={editDisabled || !editDraft.title.trim()}>
                    <Save size={14} />
                    保存详情
                  </button>
                )}
              </form>

              {canReviewAssets && (
                <div className="asset-status-flow">
                  {nextStatuses(selectedRequirement.status).map((status) => (
                    <button
                      className="mini-button"
                      type="button"
                      key={status}
                      disabled={reviewDisabled}
                      onClick={() => changeStatus(status)}
                    >
                      <Send size={14} />
                      {statusActionLabel[status] ?? status}
                    </button>
                  ))}
                </div>
              )}

              <div className="asset-trace-links">
                <div className="panel-title-row">
                  <strong>追踪链接</strong>
                  <span className="document-count-badge">{traceLinks.length}</span>
                </div>
                {traceLinks.length > 0 ? (
                  traceLinks.map((link) => (
                    <div className="trace-link-row" key={link.id || `${link.apiId}-${link.caseId}`}>
                      <span>
                        <strong>{link.apiId ?? 'API -'}</strong>
                        <em>{link.caseId ?? 'Case -'}</em>
                      </span>
                      <em>{formatDate(link.createdAt)}</em>
                    </div>
                  ))
                ) : (
                  <div className="empty-state compact">
                    <Link2 size={20} />
                  <div>
                    <strong>{detailState.loading ? '正在加载追踪链接' : '暂无追踪链接'}</strong>
                    <span>{detailState.error ?? '暂无关联 API 或用例'}</span>
                  </div>
                </div>
                )}
              </div>

              <StateLine state={mutationState} />
              <StateLine state={detailState} />
            </div>
          ) : (
            <div className="empty-state compact">
              <Pencil size={20} />
              <div>
                <strong>{detailState.loading ? '正在加载详情' : props.signedIn ? '未选择需求' : '等待登录'}</strong>
                <span>{detailState.error ?? '从列表中选择需求资产'}</span>
              </div>
            </div>
          )}
        </section>
      </aside>
    </section>
  );
}

function requirementIdFromHash() {
  const parts = window.location.hash.replace(/^#\/?/, '').split('/');
  if (parts[0] !== 'asset-library' || parts[1] !== 'requirements') {
    return '';
  }
  return parts[2] ? decodeURIComponent(parts[2]) : '';
}

function buildRequirementFilters(filters: RequirementFilters): AssetRequirementFilters {
  return {
    size: 50,
    projectId: filters.projectId,
    status: filters.status,
    keyword: filters.keyword,
    source: filters.source,
    sourceRef: filters.sourceRef
  };
}

function filterRequirements(requirements: AssetRequirementView[], filters: RequirementFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return requirements.filter((requirement) => {
    if (filters.projectId.trim() && requirement.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && requirement.status !== filters.status.trim()) {
      return false;
    }
    if (filters.source.trim() && requirement.source !== filters.source.trim()) {
      return false;
    }
    if (filters.sourceRef.trim() && !(requirement.sourceRef ?? '').includes(filters.sourceRef.trim())) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      requirement.title,
      requirement.description,
      requirement.acceptanceCriteria,
      requirement.sourceRef,
      requirement.projectId,
      requirement.tags.join(',')
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function countRequirementsByStatus(requirements: AssetRequirementView[]) {
  return requirements.reduce<Record<string, number>>((counts, requirement) => {
    counts[requirement.status] = (counts[requirement.status] ?? 0) + 1;
    return counts;
  }, {});
}

function requirementDraftFromView(requirement: AssetRequirementView): RequirementDraft {
  return {
    projectId: requirement.projectId ?? '',
    title: requirement.title,
    description: requirement.description ?? '',
    source: requirement.source,
    sourceRef: requirement.sourceRef ?? '',
    sourceUrl: requirement.sourceUrl ?? '',
    acceptanceCriteria: requirement.acceptanceCriteria ?? '',
    status: requirement.status,
    priority: requirement.priority,
    tags: requirement.tags.join(', ')
  };
}

function draftToCreatePayload(draft: RequirementDraft): AssetRequirementPayload {
  return {
    projectId: draft.projectId,
    title: draft.title,
    description: draft.description,
    source: draft.source,
    sourceRef: draft.sourceRef,
    sourceUrl: draft.sourceUrl,
    acceptanceCriteria: draft.acceptanceCriteria,
    status: draft.status,
    priority: draft.priority,
    tags: tagsFromText(draft.tags)
  };
}

function draftToUpdatePayload(
  current: AssetRequirementView,
  draft: RequirementDraft,
  status = draft.status || current.status
): AssetRequirementPayload {
  return {
    title: draft.title || current.title,
    description: draft.description,
    status,
    priority: draft.priority || current.priority,
    tags: tagsFromText(draft.tags)
  };
}

function tagsFromText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function nextStatuses(status: string) {
  return statusTransitionMap[status] ?? ['REVIEWING'];
}

function upsertRequirement(
  setter: (updater: (current: AssetRequirementView[]) => AssetRequirementView[]) => void,
  requirement: AssetRequirementView
) {
  setter((current) => {
    const existing = current.findIndex((item) => item.id === requirement.id);
    if (existing < 0) {
      return [requirement, ...current];
    }
    return current.map((item) => (item.id === requirement.id ? requirement : item));
  });
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
      : ['DEPRECATED', 'FAILED', 'DOWN', 'OFF', 'ERROR'].includes(normalized)
        ? 'negative'
        : 'neutral';
  return <span className={`status-pill ${tone}`}>{value}</span>;
}
