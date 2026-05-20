import {
  Archive,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  Eye,
  FilePlus2,
  Link2,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  XCircle,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_TEST_CASE_STATUSES,
  createAssetTestCase,
  fetchAssetHealth,
  fetchAssetTestCase,
  fetchAssetTestCases,
  fetchAssetTestCaseSteps,
  updateAssetTestCase,
  updateAssetTestCaseSteps,
  type AssetHealth,
  type AssetTestCaseFilters,
  type AssetTestCasePayload,
  type AssetTestCaseStepPayload,
  type AssetTestCaseStepView,
  type AssetTestCaseView
} from '../api/assets';
import { hasPermission } from '../permissions';
import type { AssetNavigationKey } from './AssetStructuredWorkbench';

type AssetNavigationTab = {
  key: AssetNavigationKey;
  label: string;
  icon: LucideIcon;
  enabled: boolean;
};

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type CaseFilters = {
  projectId: string;
  status: string;
  source: string;
  keyword: string;
};

type CaseDraft = {
  projectId: string;
  title: string;
  description: string;
  requirementId: string;
  apiId: string;
  priority: string;
  status: string;
  tags: string;
};

type StepDraft = {
  key: string;
  action: string;
  expectedResult: string;
};

const initialFilters: CaseFilters = {
  projectId: '',
  status: '',
  source: '',
  keyword: ''
};

const initialCaseDraft: CaseDraft = {
  projectId: '',
  title: '',
  description: '',
  requirementId: '',
  apiId: '',
  priority: 'MEDIUM',
  status: 'DRAFT',
  tags: ''
};

const testCaseStatusTransitions: Record<string, string[]> = {
  DRAFT: ['DRAFT', 'REVIEWING'],
  REVIEWING: ['DRAFT', 'REVIEWING', 'APPROVED'],
  APPROVED: ['APPROVED', 'DEPRECATED'],
  DEPRECATED: ['DEPRECATED']
};

const caseSources = ['MANUAL', 'AI_GENERATED', 'IMPORT'] as const;

export function AssetCaseWorkbench(props: {
  currentUser: CurrentUser | null;
  onSelectTab: (tabKey: AssetNavigationKey) => void;
  signedIn: boolean;
  tabs: readonly AssetNavigationTab[];
}) {
  const canReadAssets = hasPermission(props.currentUser, 'asset:read');
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const canReviewAssets = hasPermission(props.currentUser, 'asset:review');
  const [health, setHealth] = useState<AssetHealth | null>(null);
  const [items, setItems] = useState<AssetTestCaseView[]>([]);
  const [filters, setFilters] = useState<CaseFilters>(initialFilters);
  const [selectedId, setSelectedId] = useState(caseIdFromHash);
  const [selected, setSelected] = useState<AssetTestCaseView | null>(null);
  const [createDraft, setCreateDraft] = useState<CaseDraft>(initialCaseDraft);
  const [createSteps, setCreateSteps] = useState<StepDraft[]>(() => [emptyStep()]);
  const [editDraft, setEditDraft] = useState<CaseDraft>(initialCaseDraft);
  const [stepDrafts, setStepDrafts] = useState<StepDraft[]>(() => [emptyStep()]);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [createState, setCreateState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });
  const [stepsState, setStepsState] = useState<WorkState>({ loading: false });

  useEffect(() => {
    function syncFromHash() {
      const parts = window.location.hash.replace(/^#\/?/, '').split('/');
      if (parts[0] === 'asset-library' && parts[1] === 'cases') {
        setSelectedId(parts[2] ? decodeURIComponent(parts[2]) : '');
      }
    }

    window.addEventListener('hashchange', syncFromHash);
    return () => window.removeEventListener('hashchange', syncFromHash);
  }, []);

  const refreshCases = useCallback(async () => {
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
      fetchAssetTestCases(buildCaseFilters(filters))
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
      setItems(listResult.value.data.items);
      traceIds.push(listResult.value.trace_id);
    } else {
      setItems([]);
      errors.push(errorMessage(listResult.reason, '测试用例加载失败'));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canReadAssets, filters, props.signedIn]);

  useEffect(() => {
    void refreshCases();
  }, [refreshCases]);

  useEffect(() => {
    if (!selectedId && items[0]?.id) {
      setSelectedId(items[0].id);
    }
  }, [items, selectedId]);

  const reloadDetail = useCallback(async () => {
    if (!props.signedIn || !canReadAssets || !selectedId) {
      setSelected(null);
      setDetailState({ loading: false });
      setStepsState({ loading: false });
      return;
    }

    setDetailState({ loading: true });
    setStepsState({ loading: true });
    const [detailResult, stepsResult] = await Promise.allSettled([
      fetchAssetTestCase(selectedId),
      fetchAssetTestCaseSteps(selectedId)
    ]);

    if (detailResult.status === 'rejected') {
      setSelected(null);
      setDetailState({ loading: false, error: errorMessage(detailResult.reason, '测试用例详情加载失败') });
      setStepsState({ loading: false });
      return;
    }

    const nextCase = detailResult.value.data;
    const nextSteps = stepsResult.status === 'fulfilled' ? stepsResult.value.data : nextCase.steps;
    const withSteps = { ...nextCase, steps: nextSteps };
    setSelected(withSteps);
    setEditDraft(draftFromCase(withSteps));
    setStepDrafts(stepsToDrafts(nextSteps));
    upsertCase(setItems, withSteps);
    setDetailState({ loading: false, traceId: detailResult.value.trace_id });
    setStepsState(
      stepsResult.status === 'fulfilled'
        ? { loading: false, traceId: stepsResult.value.trace_id }
        : { loading: false, error: errorMessage(stepsResult.reason, '测试步骤加载失败') }
    );
  }, [canReadAssets, props.signedIn, selectedId]);

  useEffect(() => {
    void reloadDetail();
  }, [reloadDetail]);

  const visibleItems = useMemo(() => filterCases(items, filters), [filters, items]);
  const statusCounts = useMemo(() => countByStatus(items), [items]);
  const disabled = !props.signedIn || !canReadAssets || loadState.loading;
  const createDisabled = disabled || createState.loading || !canManageAssets;
  const editDisabled = disabled || mutationState.loading || !canManageAssets || !selected;
  const stepsDisabled = disabled || stepsState.loading || !canManageAssets || !selected;

  function selectItem(itemId: string) {
    if (!itemId) {
      return;
    }
    setSelectedId(itemId);
    const targetHash = `#asset-library/cases/${encodeURIComponent(itemId)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setCreateState({ loading: false, error: '请先登录后再创建测试用例' });
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

    const stepsResult = stepsPayloadFromDrafts(createSteps, false);
    if (!stepsResult.ok) {
      setCreateState({ loading: false, error: stepsResult.error });
      return;
    }

    setCreateState({ loading: true });
    try {
      const response = await createAssetTestCase(caseCreatePayload(createDraft, stepsResult.value));
      setCreateDraft(initialCaseDraft);
      setCreateSteps([emptyStep()]);
      setSelected(response.data);
      setSelectedId(response.data.id);
      setEditDraft(draftFromCase(response.data));
      setStepDrafts(stepsToDrafts(response.data.steps));
      upsertCase(setItems, response.data);
      setCreateState({ loading: false, success: '测试用例已创建', traceId: response.trace_id });
      selectItem(response.data.id);
    } catch (error: unknown) {
      setCreateState({ loading: false, error: errorMessage(error, '测试用例创建失败') });
    }
  }

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: '请先登录后再保存测试用例' });
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
    if (editDraft.status !== selected.status && !canReviewAssets) {
      setMutationState({ loading: false, error: '缺少 asset:review 权限' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateAssetTestCase(selected.id, caseUpdatePayload(editDraft));
      const nextCase = { ...response.data, steps: selected.steps };
      setSelected(nextCase);
      setEditDraft(draftFromCase(nextCase));
      upsertCase(setItems, nextCase);
      setMutationState({ loading: false, success: '测试用例已保存', traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, '测试用例保存失败') });
    }
  }

  async function submitSteps(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    if (!props.signedIn) {
      setStepsState({ loading: false, error: '请先登录后再保存测试步骤' });
      return;
    }
    if (!canManageAssets) {
      setStepsState({ loading: false, error: '缺少 asset:manage 权限' });
      return;
    }

    const stepsResult = stepsPayloadFromDrafts(stepDrafts, true);
    if (!stepsResult.ok) {
      setStepsState({ loading: false, error: stepsResult.error });
      return;
    }

    setStepsState({ loading: true });
    try {
      const response = await updateAssetTestCaseSteps(selected.id, { steps: stepsResult.value ?? [] });
      const nextCase = { ...selected, steps: response.data, updatedAt: new Date().toISOString() };
      setSelected(nextCase);
      setStepDrafts(stepsToDrafts(response.data));
      upsertCase(setItems, nextCase);
      setStepsState({ loading: false, success: '测试步骤已保存', traceId: response.trace_id });
    } catch (error: unknown) {
      setStepsState({ loading: false, error: errorMessage(error, '测试步骤保存失败') });
    }
  }

  function openLinkedAsset(tabKey: 'requirements' | 'apis', itemId?: string) {
    if (!itemId) {
      return;
    }
    window.location.hash = `#asset-library/${tabKey}/${encodeURIComponent(itemId)}`;
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
            <button className="secondary-button" type="button" disabled={disabled} onClick={refreshCases}>
              <RefreshCw size={16} />
              刷新
            </button>
          </div>

          <div className="asset-tab-strip" aria-label="资产类型">
            {props.tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  className={`asset-tab ${tab.key === 'cases' ? 'active' : ''}`}
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
            <label className="field" htmlFor="asset-cases-filter-project">
              <span>projectId</span>
              <input
                id="asset-cases-filter-project"
                value={filters.projectId}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))}
                placeholder="proj-payments"
              />
            </label>
            <label className="field" htmlFor="asset-cases-filter-status">
              <span>status</span>
              <select
                id="asset-cases-filter-status"
                value={filters.status}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">全部状态</option>
                {ASSET_TEST_CASE_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-cases-filter-source">
              <span>source</span>
              <select
                id="asset-cases-filter-source"
                value={filters.source}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, source: event.target.value }))}
              >
                <option value="">全部来源</option>
                {caseSources.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-cases-filter-keyword">
              <span>keyword</span>
              <input
                id="asset-cases-filter-keyword"
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder="标题 / 描述 / 标签"
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={refreshCases}>
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
                  <th>测试用例</th>
                  <th>项目</th>
                  <th>关联</th>
                  <th>优先级</th>
                  <th>状态 / 步骤</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.length > 0 ? (
                  visibleItems.map((item) => (
                    <tr className={selectedId === item.id ? 'selected-row' : ''} key={item.id || item.title}>
                      <td>
                        <strong className="table-primary">{item.title}</strong>
                        <span className="table-secondary">{item.code ?? item.id ?? '-'}</span>
                      </td>
                      <td>{item.projectId ?? '-'}</td>
                      <td>
                        <div className="asset-source-cell">
                          <span>需求 {item.requirementId ?? '-'}</span>
                          <em>API {item.apiId ?? '-'}</em>
                        </div>
                      </td>
                      <td>{item.priority}</td>
                      <td>
                        <div className="asset-source-cell">
                          <AssetStatusPill value={item.status} />
                          <em>{item.steps.length} 步</em>
                        </div>
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
                    <td className="table-empty" colSpan={7}>
                      {emptyListText(props.signedIn, canReadAssets, loadState.loading, filters)}
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
              <h2>新建测试用例</h2>
            </div>
          </div>
          <CaseForm
            draft={createDraft}
            disabled={createDisabled}
            onChange={setCreateDraft}
            onSubmit={submitCreate}
            submitLabel="创建用例"
          >
            <StepEditor
              disabled={createDisabled}
              steps={createSteps}
              onChange={setCreateSteps}
              title="初始步骤"
            />
          </CaseForm>
          <StateLine state={createState} />
        </section>
      </div>

      <aside className="side-stack asset-side-stack">
        <section className="panel insight-panel">
          <h2>WP3 接口状态</h2>
          <div className="document-health-grid">
            <StatusMetric label="服务" value={health?.service ?? 'asset-service'} />
            <StatusMetric label="状态" value={health?.status ?? (props.signedIn ? '等待响应' : '等待登录')} pill />
            <StatusMetric label="用例" value={String(items.length)} />
            {ASSET_TEST_CASE_STATUSES.map((status) => (
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

        <section className="panel insight-panel asset-detail-panel">
          <div className="panel-title-row">
            <h2>测试用例详情</h2>
            {selected && <AssetStatusPill value={selected.status} />}
          </div>

          {selected ? (
            <div className="asset-detail-stack">
              <div className="resource-summary">
                <strong>{selected.title}</strong>
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
                  <span>priority</span>
                  <em>{selected.priority}</em>
                </div>
                <div>
                  <span>createdAt</span>
                  <em>{formatDate(selected.createdAt)}</em>
                </div>
              </div>

              <div className="asset-source-trace">
                <strong>关联资产</strong>
                <div>
                  <span>requirementId</span>
                  {selected.requirementId ? (
                    <button className="mini-button" type="button" onClick={() => openLinkedAsset('requirements', selected.requirementId)}>
                      <Link2 size={13} />
                      {selected.requirementId}
                    </button>
                  ) : (
                    <em>-</em>
                  )}
                </div>
                <div>
                  <span>apiId</span>
                  {selected.apiId ? (
                    <button className="mini-button" type="button" onClick={() => openLinkedAsset('apis', selected.apiId)}>
                      <Link2 size={13} />
                      {selected.apiId}
                    </button>
                  ) : (
                    <em>-</em>
                  )}
                </div>
                <div>
                  <span>source</span>
                  <em>{selected.source ?? '-'}</em>
                </div>
                <div>
                  <span>sourceRef</span>
                  <em>{selected.sourceRef ?? '-'}</em>
                </div>
              </div>

              <div className="asset-schema-preview">
                <strong>测试步骤</strong>
                {selected.steps.length > 0 ? (
                  <ol className="asset-step-list">
                    {selected.steps.map((step) => (
                      <li key={`${step.stepOrder}-${step.action ?? ''}`}>
                        <strong>{step.action ?? '-'}</strong>
                        <span>{step.expectedResult ?? '-'}</span>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <pre>暂无步骤，可添加第一步</pre>
                )}
              </div>

              <CaseForm
                compact
                draft={editDraft}
                disabled={editDisabled}
                onChange={setEditDraft}
                onSubmit={submitEdit}
                selectedStatus={selected.status}
                statusDisabled={!canReviewAssets}
                submitLabel="保存用例"
              />
              <StateLine state={mutationState} />

              <form className="asset-form" onSubmit={submitSteps}>
                <StepEditor disabled={stepsDisabled} steps={stepDrafts} onChange={setStepDrafts} title="编辑步骤" />
                <div className="document-actions">
                  <button className="primary-button" type="submit" disabled={stepsDisabled}>
                    <Save size={16} />
                    保存步骤
                  </button>
                </div>
              </form>
              <StateLine state={stepsState} />
              <StateLine state={detailState} />
            </div>
          ) : (
            <div className="empty-state compact">
              <Pencil size={20} />
              <div>
                <strong>{detailState.loading ? '正在加载详情' : props.signedIn ? '未选择测试用例' : '等待登录'}</strong>
                <span>{detailState.error ?? '从列表中选择测试用例'}</span>
              </div>
            </div>
          )}
        </section>
      </aside>
    </section>
  );
}

function CaseForm(props: {
  children?: ReactNode;
  compact?: boolean;
  disabled: boolean;
  draft: CaseDraft;
  onChange: (updater: (current: CaseDraft) => CaseDraft) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  selectedStatus?: string;
  statusDisabled?: boolean;
  submitLabel: string;
}) {
  const statusOptions = props.selectedStatus ? statusOptionsFor(props.selectedStatus) : ASSET_TEST_CASE_STATUSES;

  return (
    <form className="asset-form" onSubmit={props.onSubmit}>
      <div className="asset-form-grid">
        {!props.compact && (
          <label className="field" htmlFor="asset-case-project">
            <span>projectId<b>*</b></span>
            <input
              id="asset-case-project"
              value={props.draft.projectId}
              disabled={props.disabled}
              onChange={(event) => props.onChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="proj-payments"
            />
          </label>
        )}
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}title`}>
          <span>标题<b>*</b></span>
          <input
            id={`asset-case-${props.compact ? 'edit-' : ''}title`}
            value={props.draft.title}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, title: event.target.value }))}
            placeholder="登录冒烟用例"
          />
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}requirement`}>
          <span>requirementId</span>
          <input
            id={`asset-case-${props.compact ? 'edit-' : ''}requirement`}
            value={props.draft.requirementId}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, requirementId: event.target.value }))}
            placeholder="需求 UUID"
          />
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}api`}>
          <span>apiId</span>
          <input
            id={`asset-case-${props.compact ? 'edit-' : ''}api`}
            value={props.draft.apiId}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, apiId: event.target.value }))}
            placeholder="API UUID"
          />
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}priority`}>
          <span>priority</span>
          <select
            id={`asset-case-${props.compact ? 'edit-' : ''}priority`}
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
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}status`}>
          <span>status</span>
          <select
            id={`asset-case-${props.compact ? 'edit-' : ''}status`}
            value={props.draft.status}
            disabled={props.disabled || props.statusDisabled}
            onChange={(event) => props.onChange((current) => ({ ...current, status: event.target.value }))}
          >
            {statusOptions.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}tags`}>
          <span>tags</span>
          <input
            id={`asset-case-${props.compact ? 'edit-' : ''}tags`}
            value={props.draft.tags}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, tags: event.target.value }))}
            placeholder="smoke, login"
          />
        </label>
      </div>
      <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}description`}>
        <span>描述</span>
        <textarea
          id={`asset-case-${props.compact ? 'edit-' : ''}description`}
          className="compact-textarea"
          value={props.draft.description}
          disabled={props.disabled}
          onChange={(event) => props.onChange((current) => ({ ...current, description: event.target.value }))}
        />
      </label>
      {props.children}
      <div className="document-actions">
        <button className="primary-button" type="submit" disabled={props.disabled || !props.draft.title.trim()}>
          <Save size={16} />
          {props.submitLabel}
        </button>
      </div>
    </form>
  );
}

function StepEditor(props: {
  disabled: boolean;
  onChange: (updater: (current: StepDraft[]) => StepDraft[]) => void;
  steps: StepDraft[];
  title: string;
}) {
  function updateStep(index: number, patch: Partial<StepDraft>) {
    props.onChange((current) => current.map((step, stepIndex) => (stepIndex === index ? { ...step, ...patch } : step)));
  }

  function moveStep(index: number, direction: -1 | 1) {
    props.onChange((current) => {
      const nextIndex = index + direction;
      if (nextIndex < 0 || nextIndex >= current.length) {
        return current;
      }
      const next = [...current];
      const [target] = next.splice(index, 1);
      next.splice(nextIndex, 0, target);
      return next;
    });
  }

  function removeStep(index: number) {
    props.onChange((current) => (current.length <= 1 ? current : current.filter((_, stepIndex) => stepIndex !== index)));
  }

  return (
    <div className="asset-step-editor">
      <div className="panel-title-row">
        <h3>{props.title}</h3>
        <button
          className="mini-button"
          type="button"
          disabled={props.disabled}
          onClick={() => props.onChange((current) => [...current, emptyStep()])}
        >
          <Plus size={14} />
          新增步骤
        </button>
      </div>
      {props.steps.map((step, index) => (
        <div className="asset-step-row" key={step.key}>
          <div className="asset-step-index">{index + 1}</div>
          <label className="field" htmlFor={`asset-step-action-${step.key}`}>
            <span>动作</span>
            <textarea
              id={`asset-step-action-${step.key}`}
              className="compact-textarea"
              value={step.action}
              disabled={props.disabled}
              onChange={(event) => updateStep(index, { action: event.target.value })}
              placeholder="输入账号密码并点击登录"
            />
          </label>
          <label className="field" htmlFor={`asset-step-expected-${step.key}`}>
            <span>预期结果</span>
            <textarea
              id={`asset-step-expected-${step.key}`}
              className="compact-textarea"
              value={step.expectedResult}
              disabled={props.disabled}
              onChange={(event) => updateStep(index, { expectedResult: event.target.value })}
              placeholder="进入工作台首页"
            />
          </label>
          <div className="asset-step-actions">
            <button className="mini-button icon-only" type="button" title="上移" disabled={props.disabled || index === 0} onClick={() => moveStep(index, -1)}>
              <ArrowUp size={14} />
            </button>
            <button
              className="mini-button icon-only"
              type="button"
              title="下移"
              disabled={props.disabled || index === props.steps.length - 1}
              onClick={() => moveStep(index, 1)}
            >
              <ArrowDown size={14} />
            </button>
            <button
              className="mini-button icon-only"
              type="button"
              title="删除"
              disabled={props.disabled || props.steps.length <= 1}
              onClick={() => removeStep(index)}
            >
              <Trash2 size={14} />
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

function caseIdFromHash() {
  const parts = window.location.hash.replace(/^#\/?/, '').split('/');
  if (parts[0] !== 'asset-library' || parts[1] !== 'cases') {
    return '';
  }
  return parts[2] ? decodeURIComponent(parts[2]) : '';
}

function buildCaseFilters(filters: CaseFilters): AssetTestCaseFilters {
  return {
    size: 50,
    projectId: filters.projectId,
    status: filters.status,
    keyword: filters.keyword,
    source: filters.source
  };
}

function draftFromCase(testCase: AssetTestCaseView): CaseDraft {
  return {
    projectId: testCase.projectId ?? '',
    title: testCase.title,
    description: testCase.description ?? '',
    requirementId: testCase.requirementId ?? '',
    apiId: testCase.apiId ?? '',
    priority: testCase.priority ?? 'MEDIUM',
    status: testCase.status || 'DRAFT',
    tags: testCase.tags.join(', ')
  };
}

function caseCreatePayload(draft: CaseDraft, steps?: AssetTestCaseStepPayload[]): AssetTestCasePayload {
  return {
    projectId: draft.projectId,
    title: draft.title,
    description: draft.description,
    requirementId: draft.requirementId,
    apiId: draft.apiId,
    priority: draft.priority,
    status: draft.status,
    tags: draft.tags,
    steps
  };
}

function caseUpdatePayload(draft: CaseDraft): AssetTestCasePayload {
  return {
    title: draft.title,
    description: draft.description,
    requirementId: draft.requirementId,
    apiId: draft.apiId,
    priority: draft.priority,
    status: draft.status,
    tags: draft.tags
  };
}

function stepsToDrafts(steps: AssetTestCaseStepView[]): StepDraft[] {
  if (!steps.length) {
    return [emptyStep()];
  }
  return steps
    .slice()
    .sort((left, right) => left.stepOrder - right.stepOrder)
    .map((step, index) => ({
      key: `step-${Date.now()}-${index}`,
      action: step.action ?? '',
      expectedResult: step.expectedResult ?? ''
    }));
}

function stepsPayloadFromDrafts(
  drafts: StepDraft[],
  requireAtLeastOne: boolean
): { ok: true; value?: AssetTestCaseStepPayload[] } | { error: string; ok: false } {
  const hasAnyContent = drafts.some((step) => step.action.trim() || step.expectedResult.trim());
  if (!hasAnyContent) {
    return requireAtLeastOne ? { ok: false, error: '至少保留一个测试步骤' } : { ok: true, value: undefined };
  }
  const steps: AssetTestCaseStepPayload[] = [];
  for (const [index, step] of drafts.entries()) {
    const action = step.action.trim();
    const expectedResult = step.expectedResult.trim();
    if (!action || !expectedResult) {
      return { ok: false, error: `第 ${index + 1} 步动作和预期结果必填` };
    }
    steps.push({ action, expectedResult });
  }
  return { ok: true, value: steps };
}

function emptyStep(): StepDraft {
  return {
    key: `step-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    action: '',
    expectedResult: ''
  };
}

function filterCases(items: AssetTestCaseView[], filters: CaseFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return items.filter((item) => {
    if (filters.projectId.trim() && item.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && item.status !== filters.status.trim()) {
      return false;
    }
    if (filters.source.trim() && item.source !== filters.source.trim()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      item.title,
      item.description,
      item.code,
      item.sourceRef,
      item.projectId,
      item.requirementId,
      item.apiId,
      item.tags.join(',')
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function countByStatus(items: AssetTestCaseView[]) {
  return items.reduce<Record<string, number>>((counts, item) => {
    counts[item.status] = (counts[item.status] ?? 0) + 1;
    return counts;
  }, {});
}

function statusOptionsFor(status: string) {
  return testCaseStatusTransitions[status] ?? ASSET_TEST_CASE_STATUSES;
}

function upsertCase(
  setter: (updater: (current: AssetTestCaseView[]) => AssetTestCaseView[]) => void,
  item: AssetTestCaseView
) {
  setter((current) => {
    const existing = current.findIndex((value) => value.id === item.id);
    if (existing < 0) {
      return [item, ...current];
    }
    return current.map((value) => (value.id === item.id ? item : value));
  });
}

function emptyListText(signedIn: boolean, canReadAssets: boolean, loading: boolean, filters: CaseFilters) {
  if (!signedIn) {
    return '请先登录';
  }
  if (!canReadAssets) {
    return '缺少 asset:read 权限';
  }
  if (loading) {
    return '加载中';
  }
  const hasFilters = filters.projectId.trim() || filters.status.trim() || filters.source.trim() || filters.keyword.trim();
  return hasFilters ? '没有匹配的测试用例' : '暂无测试用例';
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
    return <span className="document-state-line success">{props.state.success}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">traceId: {props.state.traceId}</span>;
  }
  return null;
}

function StatusMetric(props: { label: string; pill?: boolean; value: string }) {
  return (
    <div>
      <span>{props.label}</span>
      {props.pill ? <AssetStatusPill value={props.value} /> : <strong>{props.value}</strong>}
    </div>
  );
}

function AssetStatusPill(props: { value: string }) {
  return (
    <span className={`status-pill ${props.value.toLowerCase()}`}>
      <CheckCircle2 size={12} />
      {props.value}
    </span>
  );
}
