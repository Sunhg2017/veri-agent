import {
  Archive,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  Eye,
  FilePlus2,
  GripVertical,
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
import { Drawer } from 'antd';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/auth';
import {
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_TEST_CASE_STATUSES,
  createAssetTestCase,
  fetchAssetHealth,
  fetchAssetTestCase,
  fetchAssetTestCases,
  fetchAssetTestCaseSteps,
  fetchAssetTestCaseVersions,
  rollbackAssetTestCaseVersion,
  updateAssetTestCase,
  updateAssetTestCaseSteps,
  type AssetHealth,
  type AssetTestCaseFilters,
  type AssetTestCasePayload,
  type AssetTestCaseStepPayload,
  type AssetTestCaseStepView,
  type AssetTestCaseView,
  type AssetVersionHistoryView
} from '../api/assets';
import { hasPermission } from '../permissions';
import {
  applyStepRichTextMarkup,
  moveItemByKey,
  type StepRichTextStyle
} from '../stepRichText';
import { AssetImportExportPanel } from './AssetImportExportPanel';
import { AssetNavigationTabs } from './AssetNavigationTabs';
import type { AssetNavigationKey } from './AssetStructuredWorkbench';
import { AssetVersionHistoryPanel } from './AssetVersionHistoryPanel';
import { StepRichTextField } from './StepRichTextField';
import { dictionaryLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { InputControl, SelectControl, TextAreaControl } from './ui';

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

type CaseDrawer = 'create' | 'edit' | 'steps' | null;

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
  const location = useLocation();
  const navigate = useNavigate();
  const canReadAssets = hasPermission(props.currentUser, 'asset:read');
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const canReviewAssets = hasPermission(props.currentUser, 'asset:review');
  const [health, setHealth] = useState<AssetHealth | null>(null);
  const [items, setItems] = useState<AssetTestCaseView[]>([]);
  const [filters, setFilters] = useState<CaseFilters>(initialFilters);
  const [selectedId, setSelectedId] = useState(() => caseIdFromPathname(location.pathname));
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
  const [versions, setVersions] = useState<AssetVersionHistoryView[]>([]);
  const [versionState, setVersionState] = useState<WorkState>({ loading: false });
  const [draggingStepKey, setDraggingStepKey] = useState('');
  const [openDrawer, setOpenDrawer] = useState<CaseDrawer>(null);

  useEffect(() => {
    const parts = location.pathname.replace(/^\/+/, '').split('/');
    if (parts[0] === 'asset-library' && parts[1] === 'cases') {
      setSelectedId(parts[2] ? decodeURIComponent(parts[2]) : '');
    }
  }, [location.pathname]);

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
      errors.push(errorMessage(healthResult.reason, translate('auto.k0392')));
    }

    if (listResult.status === 'fulfilled') {
      setItems(listResult.value.data.items);
      traceIds.push(listResult.value.trace_id);
    } else {
      setItems([]);
      errors.push(errorMessage(listResult.reason, translate('auto.k0393')));
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
      setVersions([]);
      setVersionState({ loading: false });
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
      setDetailState({ loading: false, error: errorMessage(detailResult.reason, translate('auto.k0394')) });
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
        : { loading: false, error: errorMessage(stepsResult.reason, translate('auto.k0395')) }
    );
  }, [canReadAssets, props.signedIn, selectedId]);

  useEffect(() => {
    void reloadDetail();
  }, [reloadDetail]);

  const reloadVersions = useCallback(
    async (targetId = selectedId) => {
      if (!props.signedIn || !canReadAssets || !targetId) {
        setVersions([]);
        setVersionState({ loading: false });
        return;
      }

      setVersionState({ loading: true });
      try {
        const response = await fetchAssetTestCaseVersions(targetId);
        setVersions(response.data);
        setVersionState({ loading: false, traceId: response.trace_id });
      } catch (error: unknown) {
        setVersions([]);
        setVersionState({ loading: false, error: errorMessage(error, translate('auto.k0396')) });
      }
    },
    [canReadAssets, props.signedIn, selectedId]
  );

  useEffect(() => {
    void reloadVersions();
  }, [reloadVersions]);

  async function rollbackTestCase(version: number) {
    if (!selected) {
      return;
    }
    if (!props.signedIn) {
      setVersionState({ loading: false, error: translate('auto.k0397') });
      return;
    }
    if (!canManageAssets) {
      setVersionState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    setVersionState({ loading: true });
    try {
      const response = await rollbackAssetTestCaseVersion(selected.id, version, translate('auto.k0399', { value0: version }));
      setSelected(response.data);
      setEditDraft(draftFromCase(response.data));
      setStepDrafts(stepsToDrafts(response.data.steps));
      upsertCase(setItems, response.data);
      setVersionState({ loading: false, traceId: response.trace_id });
      void reloadVersions(response.data.id);
    } catch (error: unknown) {
      setVersionState({ loading: false, error: errorMessage(error, translate('auto.k0400')) });
    }
  }

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
    const targetPath = `/asset-library/cases/${encodeURIComponent(itemId)}`;
    if (location.pathname !== targetPath) {
      navigate(targetPath);
    }
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setCreateState({ loading: false, error: translate('auto.k0401') });
      return;
    }
    if (!canManageAssets) {
      setCreateState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    if (!createDraft.projectId.trim() || !createDraft.title.trim()) {
      setCreateState({ loading: false, error: translate('auto.k0402') });
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
      setOpenDrawer(null);
      setCreateState({ loading: false, success: translate('auto.k0403'), traceId: response.trace_id });
      void reloadVersions(response.data.id);
      selectItem(response.data.id);
    } catch (error: unknown) {
      setCreateState({ loading: false, error: errorMessage(error, translate('auto.k0404')) });
    }
  }

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: translate('auto.k0405') });
      return;
    }
    if (!canManageAssets) {
      setMutationState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    if (!editDraft.title.trim()) {
      setMutationState({ loading: false, error: translate('auto.k0406') });
      return;
    }
    if (editDraft.status !== selected.status && !canReviewAssets) {
      setMutationState({ loading: false, error: translate('auto.k0407') });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateAssetTestCase(selected.id, caseUpdatePayload(editDraft));
      const nextCase = { ...response.data, steps: selected.steps };
      setSelected(nextCase);
      setEditDraft(draftFromCase(nextCase));
      upsertCase(setItems, nextCase);
      setOpenDrawer(null);
      setMutationState({ loading: false, success: translate('auto.k0408'), traceId: response.trace_id });
      void reloadVersions(response.data.id);
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, translate('auto.k0409')) });
    }
  }

  async function submitSteps(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    if (!props.signedIn) {
      setStepsState({ loading: false, error: translate('auto.k0410') });
      return;
    }
    if (!canManageAssets) {
      setStepsState({ loading: false, error: translate('auto.k0398') });
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
      setOpenDrawer(null);
      setStepsState({ loading: false, success: translate('auto.k0411'), traceId: response.trace_id });
      void reloadVersions(selected.id);
    } catch (error: unknown) {
      setStepsState({ loading: false, error: errorMessage(error, translate('auto.k0412')) });
    }
  }

  function openLinkedAsset(tabKey: 'requirements' | 'apis', itemId?: string) {
    if (!itemId) {
      return;
    }
    window.location.hash = `#asset-library/${tabKey}/${encodeURIComponent(itemId)}`;
  }

  function openCreateDrawer() {
    setCreateDraft(initialCaseDraft);
    setCreateSteps([emptyStep()]);
    setCreateState({ loading: false });
    setOpenDrawer('create');
  }

  function openEditDrawer() {
    if (!selected) {
      return;
    }
    setEditDraft(draftFromCase(selected));
    setMutationState({ loading: false });
    setOpenDrawer('edit');
  }

  function openStepsDrawer() {
    if (!selected) {
      return;
    }
    setStepDrafts(stepsToDrafts(selected.steps));
    setStepsState({ loading: false });
    setOpenDrawer('steps');
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
                <span className="eyebrow">{translate('auto.k0039')}</span>
                <h2>{translate('auto.k0005')}</h2>
              </div>
            </div>
            <div className="panel-toolbar-actions">
              <button className="secondary-button" type="button" disabled={disabled} onClick={refreshCases}>
                <RefreshCw size={16} />
                {translate('auto.k0170')}
              </button>
              <button className="primary-button" type="button" disabled={createDisabled} onClick={openCreateDrawer}>
                <FilePlus2 size={16} />
                {translate('auto.k0424')}
              </button>
            </div>
          </div>

          <AssetNavigationTabs activeKey="cases" ariaLabel={translate('auto.k0413')} tabs={props.tabs} onSelectTab={props.onSelectTab} />

          <form className="asset-filter-bar" onSubmit={(event) => event.preventDefault()}>
            <label className="field" htmlFor="asset-cases-filter-project">
              <span>{fieldLabel('projectId')}</span>
              <InputControl
                id="asset-cases-filter-project"
                value={filters.projectId}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))}
                placeholder="proj-payments"
              />
            </label>
            <label className="field" htmlFor="asset-cases-filter-status">
              <span>{fieldLabel('status')}</span>
              <SelectControl
                id="asset-cases-filter-status"
                value={filters.status}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">{translate('auto.k0367')}</option>
                {ASSET_TEST_CASE_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </SelectControl>
            </label>
            <label className="field" htmlFor="asset-cases-filter-source">
              <span>{fieldLabel('source')}</span>
              <SelectControl
                id="asset-cases-filter-source"
                value={filters.source}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, source: event.target.value }))}
              >
                <option value="">{translate('auto.k0414')}</option>
                {caseSources.map((source) => (
                  <option key={source} value={source}>{dictionaryLabel(source)}</option>
                ))}
              </SelectControl>
            </label>
            <label className="field" htmlFor="asset-cases-filter-keyword">
              <span>{fieldLabel('keyword')}</span>
              <InputControl
                id="asset-cases-filter-keyword"
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder={translate('auto.k0415')}
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={refreshCases}>
                <Search size={14} />
                {translate('auto.k0372')}</button>
              <button className="mini-button" type="button" disabled={disabled} onClick={() => setFilters(initialFilters)}>
                <XCircle size={14} />
                {translate('auto.k0416')}</button>
            </div>
          </form>

          <div className="table-wrap asset-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{translate('auto.k0417')}</th>
                  <th>{translate('auto.k0176')}</th>
                  <th>{translate('auto.k0418')}</th>
                  <th>{translate('auto.k0419')}</th>
                  <th>{translate('auto.k0420')}</th>
                  <th>{translate('auto.k0421')}</th>
                  <th>{translate('auto.k0249')}</th>
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
                          <span>{translate('auto.k0133')}{item.requirementId ?? '-'}</span>
                          <em>API {item.apiId ?? '-'}</em>
                        </div>
                      </td>
                      <td>{dictionaryLabel(item.priority)}</td>
                      <td>
                        <div className="asset-source-cell">
                          <AssetStatusPill value={item.status} />
                          <em>{item.steps.length} {translate('auto.k0422')}</em>
                        </div>
                      </td>
                      <td>{formatDate(item.updatedAt ?? item.createdAt)}</td>
                      <td>
                        <button className="mini-button" type="button" onClick={() => selectItem(item.id)} disabled={!item.id}>
                          <Eye size={14} />
                          {translate('auto.k0333')}</button>
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

        <div className="asset-drawer-host">
          <Drawer
            className="asset-form-drawer"
            destroyOnHidden
            maskClosable={!createState.loading}
            open={openDrawer === 'create'}
            placement="right"
            title={translate('auto.k0423')}
            width={800}
            onClose={() => {
              if (!createState.loading) {
                setOpenDrawer(null);
              }
            }}
          >
            <CaseForm
              className="document-drawer-form"
              draft={createDraft}
              disabled={createDisabled}
              onChange={setCreateDraft}
              onSubmit={submitCreate}
              submitLabel={translate('auto.k0424')}
            >
              <StepEditor
                disabled={createDisabled}
                draggingStepKey={draggingStepKey}
                steps={createSteps}
                onChange={setCreateSteps}
                onDraggingStepKeyChange={setDraggingStepKey}
                title={translate('auto.k0425')}
              />
              <StateLine state={createState} />
            </CaseForm>
          </Drawer>
        </div>
      </div>

      <aside className="side-stack asset-side-stack">
        <section className="panel insight-panel">
          <h2>{translate('auto.k0426')}</h2>
          <div className="document-health-grid">
            <StatusMetric label={translate('auto.k0427')} value={health?.service ?? 'asset-service'} />
            <StatusMetric label={translate('auto.k0182')} value={health?.status ?? (props.signedIn ? translate('auto.k0428') : translate('auto.k0429'))} pill />
            <StatusMetric label={translate('auto.k0136')} value={String(items.length)} />
            {ASSET_TEST_CASE_STATUSES.map((status) => (
              <StatusMetric key={status} label={dictionaryLabel(status)} value={String(statusCounts[status] ?? 0)} />
            ))}
          </div>
          {loadState.error && (
            <div className="inline-error">
              <strong>{translate('auto.k0148')}</strong>
              <span>{loadState.error}</span>
            </div>
          )}
        </section>

        <AssetImportExportPanel
          assetTypes={['TEST_CASE']}
          currentUser={props.currentUser}
          onImported={refreshCases}
          signedIn={props.signedIn}
        />

        <section className="panel insight-panel asset-detail-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0430')}</h2>
            {selected && <AssetStatusPill value={selected.status} />}
          </div>

          {selected ? (
            <div className="asset-detail-stack">
              <div className="resource-summary">
                <strong>{selected.title}</strong>
                <div>
                  <span>{fieldLabel('projectId')}</span>
                  <em>{selected.projectId ?? '-'}</em>
                </div>
                <div>
                  <span>{fieldLabel('code')}</span>
                  <em>{selected.code ?? '-'}</em>
                </div>
                <div>
                  <span>{fieldLabel('id')}</span>
                  <em>{selected.id}</em>
                </div>
                <div>
                  <span>{fieldLabel('priority')}</span>
                  <em>{dictionaryLabel(selected.priority)}</em>
                </div>
                <div>
                  <span>{fieldLabel('version')}</span>
                  <em>v{selected.version || '-'}</em>
                </div>
                <div>
                  <span>{fieldLabel('createdAt')}</span>
                  <em>{formatDate(selected.createdAt)}</em>
                </div>
              </div>

              <div className="asset-source-trace">
                <strong>{translate('auto.k0431')}</strong>
                <div>
                  <span>{fieldLabel('requirementId')}</span>
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
                  <span>{fieldLabel('apiId')}</span>
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
                  <span>{fieldLabel('source')}</span>
                  <em>{dictionaryLabel(selected.source)}</em>
                </div>
                <div>
                  <span>{fieldLabel('sourceRef')}</span>
                  <em>{selected.sourceRef ?? '-'}</em>
                </div>
              </div>

              <div className="asset-schema-preview">
                <strong>{translate('auto.k0432')}</strong>
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
                  <pre>{translate('auto.k0433')}</pre>
                )}
              </div>

              <AssetVersionHistoryPanel
                currentVersion={selected.version}
                disabled={disabled}
                items={versions}
                onRollback={(version) => void rollbackTestCase(version)}
                onRefresh={() => void reloadVersions(selected.id)}
                state={versionState}
              />

              <div className="document-actions">
                <button className="mini-button" type="button" disabled={editDisabled} onClick={openEditDrawer}>
                  <Pencil size={14} />
                  {translate('auto.k0434')}</button>
                <button className="mini-button" type="button" disabled={stepsDisabled} onClick={openStepsDrawer}>
                  <Save size={14} />
                  {translate('auto.k0436')}</button>
              </div>
              <Drawer
                className="asset-form-drawer"
                destroyOnHidden
                maskClosable={!mutationState.loading}
                open={openDrawer === 'edit'}
                placement="right"
                title={translate('auto.k0434')}
                width={700}
                onClose={() => {
                  if (!mutationState.loading) {
                    setOpenDrawer(null);
                  }
                }}
              >
                <CaseForm
                  className="document-drawer-form"
                  compact
                  draft={editDraft}
                  disabled={editDisabled}
                  onChange={setEditDraft}
                  onSubmit={submitEdit}
                  selectedStatus={selected.status}
                  statusDisabled={!canReviewAssets}
                  submitLabel={translate('auto.k0434')}
                >
                  <StateLine state={mutationState} />
                </CaseForm>
              </Drawer>
              <StateLine state={mutationState} />

              <Drawer
                className="asset-form-drawer"
                destroyOnHidden
                maskClosable={!stepsState.loading}
                open={openDrawer === 'steps'}
                placement="right"
                title={translate('auto.k0435')}
                width={760}
                onClose={() => {
                  if (!stepsState.loading) {
                    setOpenDrawer(null);
                  }
                }}
              >
              <form className="asset-form document-drawer-form" onSubmit={submitSteps}>
                <StepEditor
                  disabled={stepsDisabled}
                  draggingStepKey={draggingStepKey}
                  steps={stepDrafts}
                  onChange={setStepDrafts}
                  onDraggingStepKeyChange={setDraggingStepKey}
                  title={translate('auto.k0435')}
                />
                <div className="document-actions">
                  <button className="primary-button" type="submit" disabled={stepsDisabled}>
                    <Save size={16} />
                    {translate('auto.k0436')}</button>
                </div>
                <StateLine state={stepsState} />
              </form>
              </Drawer>
              <StateLine state={stepsState} />
              <StateLine state={detailState} />
            </div>
          ) : (
            <div className="empty-state compact">
              <Pencil size={20} />
              <div>
                <strong>{detailState.loading ? translate('auto.k0437') : props.signedIn ? translate('auto.k0438') : translate('auto.k0429')}</strong>
                <span>{detailState.error ?? translate('auto.k0439')}</span>
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
  className?: string;
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
    <form className={props.className ? `asset-form ${props.className}` : 'asset-form'} onSubmit={props.onSubmit}>
      <div className="asset-form-grid">
        {!props.compact && (
          <label className="field" htmlFor="asset-case-project">
            <span>{fieldLabel('projectId')}<b>*</b></span>
            <InputControl
              id="asset-case-project"
              value={props.draft.projectId}
              disabled={props.disabled}
              onChange={(event) => props.onChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="proj-payments"
            />
          </label>
        )}
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}title`}>
          <span>{translate('auto.k0440')}<b>*</b></span>
          <InputControl
            id={`asset-case-${props.compact ? 'edit-' : ''}title`}
            value={props.draft.title}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, title: event.target.value }))}
            placeholder={translate('auto.k0441')}
          />
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}requirement`}>
          <span>{fieldLabel('requirementId')}</span>
          <InputControl
            id={`asset-case-${props.compact ? 'edit-' : ''}requirement`}
            value={props.draft.requirementId}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, requirementId: event.target.value }))}
            placeholder={translate('auto.k0442')}
          />
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}api`}>
          <span>{fieldLabel('apiId')}</span>
          <InputControl
            id={`asset-case-${props.compact ? 'edit-' : ''}api`}
            value={props.draft.apiId}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, apiId: event.target.value }))}
            placeholder="API UUID"
          />
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}priority`}>
          <span>{fieldLabel('priority')}</span>
          <SelectControl
            id={`asset-case-${props.compact ? 'edit-' : ''}priority`}
            value={props.draft.priority}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, priority: event.target.value }))}
          >
            {ASSET_REQUIREMENT_PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>{dictionaryLabel(priority)}</option>
            ))}
          </SelectControl>
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}status`}>
          <span>{fieldLabel('status')}</span>
          <SelectControl
            id={`asset-case-${props.compact ? 'edit-' : ''}status`}
            value={props.draft.status}
            disabled={props.disabled || props.statusDisabled}
            onChange={(event) => props.onChange((current) => ({ ...current, status: event.target.value }))}
          >
            {statusOptions.map((status) => (
              <option key={status} value={status}>{dictionaryLabel(status)}</option>
            ))}
          </SelectControl>
        </label>
        <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}tags`}>
          <span>{fieldLabel('tags')}</span>
          <InputControl
            id={`asset-case-${props.compact ? 'edit-' : ''}tags`}
            value={props.draft.tags}
            disabled={props.disabled}
            onChange={(event) => props.onChange((current) => ({ ...current, tags: event.target.value }))}
            placeholder="smoke, login"
          />
        </label>
      </div>
      <label className="field" htmlFor={`asset-case-${props.compact ? 'edit-' : ''}description`}>
        <span>{translate('auto.k0443')}</span>
        <TextAreaControl
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
  draggingStepKey: string;
  onChange: (updater: (current: StepDraft[]) => StepDraft[]) => void;
  onDraggingStepKeyChange: (stepKey: string) => void;
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

  function applyMarkup(index: number, field: 'action' | 'expectedResult', style: StepRichTextStyle) {
    const target = document.getElementById(`asset-step-${field}-${props.steps[index]?.key}`) as HTMLTextAreaElement | null;
    const value = props.steps[index]?.[field] ?? '';
    const edit = applyStepRichTextMarkup(value, style, target?.selectionStart ?? value.length, target?.selectionEnd ?? value.length);
    updateStep(index, { [field]: edit.value });
    window.requestAnimationFrame(() => {
      target?.focus();
      target?.setSelectionRange(edit.selectionStart, edit.selectionEnd);
    });
  }

  function dropStep(targetKey: string) {
    if (!props.draggingStepKey || props.draggingStepKey === targetKey) {
      return;
    }
    props.onChange((current) => moveItemByKey(current, (step) => step.key, props.draggingStepKey, targetKey));
    props.onDraggingStepKeyChange('');
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
          {translate('auto.k0444')}</button>
      </div>
      {props.steps.map((step, index) => (
        <div
          className={props.draggingStepKey === step.key ? 'asset-step-row dragging' : 'asset-step-row'}
          draggable={!props.disabled}
          key={step.key}
          onDragStart={() => props.onDraggingStepKeyChange(step.key)}
          onDragEnd={() => props.onDraggingStepKeyChange('')}
          onDragOver={(event) => event.preventDefault()}
          onDrop={() => dropStep(step.key)}
        >
          <button className="mini-button icon-only asset-step-drag" type="button" title={translate('auto.k0445')} disabled={props.disabled}>
            <GripVertical size={14} />
          </button>
          <div className="asset-step-index">{index + 1}</div>
          <StepRichTextField
            disabled={props.disabled}
            id={`asset-step-action-${step.key}`}
            label={translate('auto.k0363')}
            onChange={(value) => updateStep(index, { action: value })}
            onFormat={(style) => applyMarkup(index, 'action', style)}
            placeholder={translate('auto.k0446')}
            value={step.action}
          />
          <StepRichTextField
            disabled={props.disabled}
            id={`asset-step-expectedResult-${step.key}`}
            label={translate('auto.k0447')}
            onChange={(value) => updateStep(index, { expectedResult: value })}
            onFormat={(style) => applyMarkup(index, 'expectedResult', style)}
            placeholder={translate('auto.k0448')}
            value={step.expectedResult}
          />
          <div className="asset-step-actions">
            <button className="mini-button icon-only" type="button" title={translate('auto.k0449')} disabled={props.disabled || index === 0} onClick={() => moveStep(index, -1)}>
              <ArrowUp size={14} />
            </button>
            <button
              className="mini-button icon-only"
              type="button"
              title={translate('auto.k0450')}
              disabled={props.disabled || index === props.steps.length - 1}
              onClick={() => moveStep(index, 1)}
            >
              <ArrowDown size={14} />
            </button>
            <button
              className="mini-button icon-only"
              type="button"
              title={translate('auto.k0451')}
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

function caseIdFromPathname(pathname: string) {
  const parts = pathname.replace(/^\/+/, '').split('/');
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
    return requireAtLeastOne ? { ok: false, error: translate('auto.k0452') } : { ok: true, value: undefined };
  }
  const steps: AssetTestCaseStepPayload[] = [];
  for (const [index, step] of drafts.entries()) {
    const action = step.action.trim();
    const expectedResult = step.expectedResult.trim();
    if (!action || !expectedResult) {
      return { ok: false, error: translate('auto.k0453', { value0: index + 1 }) };
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
    return translate('auto.k0454');
  }
  if (!canReadAssets) {
    return translate('auto.k0455');
  }
  if (loading) {
    return translate('auto.k0168');
  }
  const hasFilters = filters.projectId.trim() || filters.status.trim() || filters.source.trim() || filters.keyword.trim();
  return hasFilters ? translate('auto.k0456') : translate('auto.k0457');
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
    return <span className="document-state-line">{translate('auto.k0458')}</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">{fieldLabel('traceId')}：{props.state.traceId}</span>;
  }
  return null;
}

function StatusMetric(props: { label: string; pill?: boolean; value: string }) {
  return (
    <div>
      <span>{fieldLabel(props.label)}</span>
      {props.pill ? <AssetStatusPill value={props.value} /> : <strong>{props.value}</strong>}
    </div>
  );
}

function AssetStatusPill(props: { value: string }) {
  return (
    <span className={`status-pill ${props.value.toLowerCase()}`}>
      <CheckCircle2 size={12} />
      <span title={props.value}>{dictionaryLabel(props.value)}</span>
    </span>
  );
}
