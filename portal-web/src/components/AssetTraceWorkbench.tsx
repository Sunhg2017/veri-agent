import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  Eye,
  GitBranch,
  Link2,
  RefreshCw,
  Search,
  XCircle,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  buildAssetTraceTopologyGraph,
  labelAssetTraceSubjectType,
  type AssetTraceSubject
} from '../assetTraceTopology';
import {
  ASSET_API_STATUSES,
  ASSET_FLOW_STATUSES,
  ASSET_PAGE_STATUSES,
  ASSET_REQUIREMENT_STATUSES,
  ASSET_TEST_CASE_STATUSES,
  fetchAssetApis,
  fetchAssetBusinessFlows,
  fetchAssetHealth,
  fetchAssetPages,
  fetchAssetRequirements,
  fetchAssetTestCases,
  fetchAssetTraceLinks,
  type AssetApiView,
  type AssetBusinessFlowView,
  type AssetHealth,
  type AssetPageView,
  type AssetRequirementView,
  type AssetTestCaseView,
  type TraceLinkView
} from '../api/assets';
import { hasPermission } from '../permissions';
import type { AssetNavigationKey } from './AssetStructuredWorkbench';
import { AssetTraceTopologyPanel, describeTopologyFocus } from './AssetTraceTopologyPanel';
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { NativeSelect } from './ui';

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

type TraceFilters = {
  projectId: string;
  requirementStatus: string;
  apiStatus: string;
  pageStatus: string;
  flowStatus: string;
  caseStatus: string;
  coverage: string;
  keyword: string;
};

type CoverageStatus = 'covered' | 'partial' | 'uncovered';
type ImpactLevel = 'low' | 'medium' | 'high';
type SelectedSubject = AssetTraceSubject;

type MatrixRow = {
  requirement: AssetRequirementView;
  apis: AssetApiView[];
  pages: AssetPageView[];
  flows: AssetBusinessFlowView[];
  cases: AssetTestCaseView[];
  coverage: CoverageStatus;
  impactLevel: ImpactLevel;
  gaps: string[];
  updatedAt?: string;
};

type TraceRelations = {
  requirementToApi: Map<string, Set<string>>;
  requirementToPage: Map<string, Set<string>>;
  requirementToFlow: Map<string, Set<string>>;
  requirementToCase: Map<string, Set<string>>;
  apiToRequirement: Map<string, Set<string>>;
  pageToRequirement: Map<string, Set<string>>;
  flowToRequirement: Map<string, Set<string>>;
  apiToCase: Map<string, Set<string>>;
  caseToRequirement: Map<string, Set<string>>;
  caseToApi: Map<string, Set<string>>;
};

type TraceContextItem = {
  primary: string;
  secondary?: string;
};

const initialFilters: TraceFilters = {
  projectId: '',
  requirementStatus: '',
  apiStatus: '',
  pageStatus: '',
  flowStatus: '',
  caseStatus: '',
  coverage: '',
  keyword: ''
};

const coverageLabels: Record<CoverageStatus, string> = {
  covered: translate('auto.k0521'),
  partial: translate('auto.k0522'),
  uncovered: translate('auto.k0523')
};

const impactLabels: Record<ImpactLevel, string> = {
  low: translate('auto.k0524'),
  medium: translate('auto.k0525'),
  high: translate('auto.k0526')
};

export function AssetTraceWorkbench(props: {
  currentUser: CurrentUser | null;
  onSelectTab: (tabKey: AssetNavigationKey) => void;
  signedIn: boolean;
  tabs: readonly AssetNavigationTab[];
}) {
  const canReadAssets = hasPermission(props.currentUser, 'asset:read');
  const [health, setHealth] = useState<AssetHealth | null>(null);
  const [requirements, setRequirements] = useState<AssetRequirementView[]>([]);
  const [apis, setApis] = useState<AssetApiView[]>([]);
  const [pages, setPages] = useState<AssetPageView[]>([]);
  const [flows, setFlows] = useState<AssetBusinessFlowView[]>([]);
  const [cases, setCases] = useState<AssetTestCaseView[]>([]);
  const [links, setLinks] = useState<TraceLinkView[]>([]);
  const [filters, setFilters] = useState<TraceFilters>(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState<TraceFilters>(initialFilters);
  const [selectedSubject, setSelectedSubject] = useState<SelectedSubject | null>(() => subjectFromHash());
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });

  useEffect(() => {
    function syncFromHash() {
      const nextSubject = subjectFromHash();
      if (nextSubject) {
        setSelectedSubject(nextSubject);
      }
    }

    window.addEventListener('hashchange', syncFromHash);
    return () => window.removeEventListener('hashchange', syncFromHash);
  }, []);

  const refreshTrace = useCallback(async () => {
    if (!props.signedIn || !canReadAssets) {
      setHealth(null);
      setRequirements([]);
      setApis([]);
      setPages([]);
      setFlows([]);
      setCases([]);
      setLinks([]);
      setLoadState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const projectId = appliedFilters.projectId.trim();
    const [healthResult, requirementResult, apiResult, pageResult, flowResult, caseResult, linkResult] = await Promise.allSettled([
      fetchAssetHealth(),
      fetchAssetRequirements({
        size: 200,
        projectId,
        status: appliedFilters.requirementStatus
      }),
      fetchAssetApis({
        size: 200,
        projectId,
        status: appliedFilters.apiStatus
      }),
      fetchAssetPages({
        size: 200,
        projectId,
        status: appliedFilters.pageStatus
      }),
      fetchAssetBusinessFlows({
        size: 200,
        projectId,
        status: appliedFilters.flowStatus
      }),
      fetchAssetTestCases({
        size: 200,
        projectId,
        status: appliedFilters.caseStatus
      }),
      fetchAssetTraceLinks({ size: 1000 })
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, translate('auto.k0392')));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(errorMessage(requirementResult.reason, translate('auto.k0527')));
    }

    if (apiResult.status === 'fulfilled') {
      setApis(apiResult.value.data.items);
      traceIds.push(apiResult.value.trace_id);
    } else {
      setApis([]);
      errors.push(errorMessage(apiResult.reason, translate('auto.k0528')));
    }

    if (pageResult.status === 'fulfilled') {
      setPages(pageResult.value.data.items);
      traceIds.push(pageResult.value.trace_id);
    } else {
      setPages([]);
      errors.push(errorMessage(pageResult.reason, translate('auto.k0529')));
    }

    if (flowResult.status === 'fulfilled') {
      setFlows(flowResult.value.data.items);
      traceIds.push(flowResult.value.trace_id);
    } else {
      setFlows([]);
      errors.push(errorMessage(flowResult.reason, translate('auto.k0530')));
    }

    if (caseResult.status === 'fulfilled') {
      setCases(caseResult.value.data.items);
      traceIds.push(caseResult.value.trace_id);
    } else {
      setCases([]);
      errors.push(errorMessage(caseResult.reason, translate('auto.k0393')));
    }

    if (linkResult.status === 'fulfilled') {
      setLinks(linkResult.value.data.items);
      traceIds.push(linkResult.value.trace_id);
    } else {
      setLinks([]);
      errors.push(errorMessage(linkResult.reason, translate('auto.k0531')));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [appliedFilters, canReadAssets, props.signedIn]);

  useEffect(() => {
    void refreshTrace();
  }, [refreshTrace]);

  const requirementById = useMemo(() => mapById(requirements), [requirements]);
  const apiById = useMemo(() => mapById(apis), [apis]);
  const pageById = useMemo(() => mapById(pages), [pages]);
  const flowById = useMemo(() => mapById(flows), [flows]);
  const caseById = useMemo(() => mapById(cases), [cases]);
  const relations = useMemo(() => buildRelations(links, cases), [cases, links]);
  const matrixRows = useMemo(
    () => buildMatrixRows(requirements, apiById, pageById, flowById, caseById, relations),
    [apiById, caseById, flowById, pageById, relations, requirements]
  );
  const visibleRows = useMemo(
    () => filterRows(matrixRows, appliedFilters),
    [appliedFilters, matrixRows]
  );
  const orphanApis = useMemo(
    () => apis.filter((api) => !hasResolvedRelation(relations.apiToRequirement.get(api.id), requirementById)),
    [apis, relations, requirementById]
  );
  const apisWithoutCases = useMemo(
    () => apis.filter((api) => !hasResolvedRelation(relations.apiToCase.get(api.id), caseById)),
    [apis, caseById, relations]
  );
  const orphanPages = useMemo(
    () => pages.filter((page) => !hasResolvedRelation(relations.pageToRequirement.get(page.id), requirementById)),
    [pages, relations, requirementById]
  );
  const orphanFlows = useMemo(
    () => flows.filter((flow) => !hasResolvedRelation(relations.flowToRequirement.get(flow.id), requirementById)),
    [flows, relations, requirementById]
  );
  const orphanCases = useMemo(
    () => cases.filter((item) => !hasResolvedRelation(relations.caseToRequirement.get(item.id), requirementById)),
    [cases, relations, requirementById]
  );
  const stats = useMemo(
    () => traceStats(matrixRows),
    [matrixRows]
  );
  const topologyGraph = useMemo(
    () => buildAssetTraceTopologyGraph({
      focus: selectedSubject,
      requirements,
      apis,
      pages,
      flows,
      cases,
      links
    }),
    [apis, cases, flows, links, pages, requirements, selectedSubject]
  );
  const topologyFocusLabel = useMemo(
    () => describeTopologyFocus(selectedSubject, topologyGraph?.nodes ?? []),
    [selectedSubject, topologyGraph]
  );

  useEffect(() => {
    if (selectedSubject && subjectExists(selectedSubject, requirementById, apiById, pageById, flowById, caseById)) {
      return;
    }
    if (visibleRows[0]?.requirement.id) {
      setSelectedSubject({ type: 'requirement', id: visibleRows[0].requirement.id });
      return;
    }
    if (orphanApis[0]?.id) {
      setSelectedSubject({ type: 'api', id: orphanApis[0].id });
      return;
    }
    if (orphanPages[0]?.id) {
      setSelectedSubject({ type: 'page', id: orphanPages[0].id });
      return;
    }
    if (orphanFlows[0]?.id) {
      setSelectedSubject({ type: 'flow', id: orphanFlows[0].id });
      return;
    }
    if (orphanCases[0]?.id) {
      setSelectedSubject({ type: 'case', id: orphanCases[0].id });
      return;
    }
    setSelectedSubject(null);
  }, [apiById, caseById, flowById, orphanApis, orphanCases, orphanFlows, orphanPages, pageById, requirementById, selectedSubject, visibleRows]);

  const disabled = !props.signedIn || !canReadAssets || loadState.loading;

  function submitFilters() {
    setAppliedFilters(filters);
  }

  function resetFilters() {
    setFilters(initialFilters);
    setAppliedFilters(initialFilters);
  }

  function selectSubject(subject: SelectedSubject) {
    setSelectedSubject(subject);
    const targetHash = `#asset-library/trace/${subject.type}/${encodeURIComponent(subject.id)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  function openAsset(tab: AssetNavigationKey, id: string) {
    const targetHash = `#asset-library/${tab}/${encodeURIComponent(id)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
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
                <span className="eyebrow">Trace Matrix</span>
                <h2>{translate('auto.k0532')}</h2>
              </div>
            </div>
            <button
              className="secondary-button"
              type="button"
              disabled={disabled}
              onClick={refreshTrace}
            >
              <RefreshCw size={16} />
              {translate('auto.k0170')}</button>
          </div>

          <div className="asset-tab-strip" aria-label={translate('auto.k0413')}>
            {props.tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  className={`asset-tab ${tab.key === 'trace' ? 'active' : ''}`}
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

          <form className="asset-filter-bar trace-filter-bar" onSubmit={(event) => event.preventDefault()}>
            <label className="field" htmlFor="asset-trace-filter-project">
              <span>projectId</span>
              <input
                id="asset-trace-filter-project"
                value={filters.projectId}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))}
                placeholder="proj-payments"
              />
            </label>
            <label className="field" htmlFor="asset-trace-filter-requirement-status">
              <span>{translate('auto.k0533')}</span>
              <NativeSelect
                id="asset-trace-filter-requirement-status"
                value={filters.requirementStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, requirementStatus: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                {ASSET_REQUIREMENT_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </NativeSelect>
            </label>
            <label className="field" htmlFor="asset-trace-filter-api-status">
              <span>{translate('auto.k0534')}</span>
              <NativeSelect
                id="asset-trace-filter-api-status"
                value={filters.apiStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, apiStatus: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                {ASSET_API_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </NativeSelect>
            </label>
            <label className="field" htmlFor="asset-trace-filter-page-status">
              <span>{translate('auto.k0535')}</span>
              <NativeSelect
                id="asset-trace-filter-page-status"
                value={filters.pageStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, pageStatus: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                {ASSET_PAGE_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </NativeSelect>
            </label>
            <label className="field" htmlFor="asset-trace-filter-flow-status">
              <span>{translate('auto.k0536')}</span>
              <NativeSelect
                id="asset-trace-filter-flow-status"
                value={filters.flowStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, flowStatus: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                {ASSET_FLOW_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </NativeSelect>
            </label>
            <label className="field" htmlFor="asset-trace-filter-case-status">
              <span>{translate('auto.k0537')}</span>
              <NativeSelect
                id="asset-trace-filter-case-status"
                value={filters.caseStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, caseStatus: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                {ASSET_TEST_CASE_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </NativeSelect>
            </label>
            <label className="field" htmlFor="asset-trace-filter-coverage">
              <span>{translate('auto.k0538')}</span>
              <NativeSelect
                id="asset-trace-filter-coverage"
                value={filters.coverage}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, coverage: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                <option value="covered">{translate('auto.k0521')}</option>
                <option value="partial">{translate('auto.k0522')}</option>
                <option value="uncovered">{translate('auto.k0523')}</option>
              </NativeSelect>
            </label>
            <label className="field" htmlFor="asset-trace-filter-keyword">
              <span>keyword</span>
              <input
                id="asset-trace-filter-keyword"
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder={translate('auto.k0539')}
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={submitFilters}>
                <Search size={14} />
                {translate('auto.k0372')}</button>
              <button className="mini-button" type="button" disabled={disabled} onClick={resetFilters}>
                <XCircle size={14} />
                {translate('auto.k0416')}</button>
            </div>
          </form>

          <div className="table-wrap asset-table-wrap trace-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{translate('auto.k0133')}</th>
                  <th>{translate('auto.k0540')}</th>
                  <th>{translate('auto.k0541')}</th>
                  <th>{translate('auto.k0542')}</th>
                  <th>{translate('auto.k0543')}</th>
                  <th>{translate('auto.k0544')}</th>
                  <th>{translate('auto.k0421')}</th>
                  <th>{translate('auto.k0249')}</th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.length > 0 ? (
                  visibleRows.map((row) => (
                    <tr
                      className={selectedSubject?.type === 'requirement' && selectedSubject.id === row.requirement.id ? 'selected-row' : ''}
                      key={row.requirement.id || row.requirement.title}
                    >
                      <td>
                        <strong className="table-primary">{row.requirement.title}</strong>
                        <span className="table-secondary">{row.requirement.id || '-'}</span>
                      </td>
                      <td>
                        <CoveragePill status={row.coverage} />
                        <span className={`impact-pill ${row.impactLevel}`}>{translate('auto.k0545')}{impactLabels[row.impactLevel]}</span>
                      </td>
                      <td>
                        <LinkedList
                          items={row.apis}
                          empty={translate('auto.k0546')}
                          render={(api) => (
                            <>
                              <strong>{api.httpMethod} {api.path}</strong>
                              <em>{api.summary}</em>
                            </>
                          )}
                        />
                      </td>
                      <td>
                        <LinkedList
                          items={requirementContextItems(row.pages, row.flows)}
                          empty={translate('auto.k0547')}
                          render={(item) => (
                            <>
                              <strong>{item.primary}</strong>
                              <em>{item.secondary ?? '-'}</em>
                            </>
                          )}
                        />
                      </td>
                      <td>
                        <LinkedList
                          items={row.cases}
                          empty={translate('auto.k0548')}
                          render={(item) => (
                            <>
                              <strong>{item.title}</strong>
                              <em>{dictionaryLabel(item.status)}</em>
                            </>
                          )}
                        />
                      </td>
                      <td>
                        <GapList gaps={row.gaps} />
                      </td>
                      <td>{formatDate(row.updatedAt)}</td>
                      <td>
                        <button
                          className="mini-button"
                          type="button"
                          disabled={!row.requirement.id}
                          onClick={() => selectSubject({ type: 'requirement', id: row.requirement.id })}
                        >
                          <Eye size={14} />
                          {translate('auto.k0549')}</button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={8}>
                      {props.signedIn ? (loadState.loading ? translate('auto.k0168') : translate('auto.k0550')) : translate('auto.k0454')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <StateLine state={loadState} />
        </section>

        <section className="panel module-panel asset-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <AlertTriangle size={20} />
              </div>
              <div>
                <span className="eyebrow">Trace Gaps</span>
                <h2>{translate('auto.k0551')}</h2>
              </div>
            </div>
          </div>
          <div className="trace-gap-grid">
            <GapBucket
              title={translate('auto.k0552')}
              items={orphanApis}
              empty={translate('auto.k0553')}
              render={(api) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'api', id: api.id })}>
                  <strong>{api.httpMethod} {api.path}</strong>
                  <span>{api.summary}</span>
                </button>
              )}
            />
            <GapBucket
              title={translate('auto.k0554')}
              items={apisWithoutCases}
              empty={translate('auto.k0555')}
              render={(api) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'api', id: api.id })}>
                  <strong>{api.httpMethod} {api.path}</strong>
                  <span>{api.summary}</span>
                </button>
              )}
            />
            <GapBucket
              title={translate('auto.k0556')}
              items={orphanPages}
              empty={translate('auto.k0557')}
              render={(page) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'page', id: page.id })}>
                  <strong>{page.name}</strong>
                  <span>{page.urlPattern ?? dictionaryLabel(page.status)}</span>
                </button>
              )}
            />
            <GapBucket
              title={translate('auto.k0558')}
              items={orphanFlows}
              empty={translate('auto.k0559')}
              render={(flow) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'flow', id: flow.id })}>
                  <strong>{flow.name}</strong>
                  <span>{dictionaryLabel(flow.status)}</span>
                </button>
              )}
            />
            <GapBucket
              title={translate('auto.k0560')}
              items={orphanCases}
              empty={translate('auto.k0561')}
              render={(item) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'case', id: item.id })}>
                  <strong>{item.title}</strong>
                  <span>{dictionaryLabel(item.status)}</span>
                </button>
              )}
            />
          </div>
        </section>

        <section className="panel module-panel asset-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <GitBranch size={20} />
              </div>
              <div>
                <span className="eyebrow">Asset Topology</span>
                <h2>{translate('auto.k0562')}</h2>
              </div>
            </div>
            {selectedSubject && <SubjectPill subject={selectedSubject} />}
          </div>
          <div className="trace-topology-heading">
            <strong>{topologyFocusLabel}</strong>
            <span>{translate('auto.k0563')}</span>
          </div>
          <AssetTraceTopologyPanel
            graph={topologyGraph}
            onOpenAsset={openAsset}
            onSelectSubject={selectSubject}
          />
        </section>
      </div>

      <aside className="side-stack asset-side-stack">
        <section className="panel insight-panel">
          <h2>{translate('auto.k0564')}</h2>
          <div className="document-health-grid">
            <StatusMetric label={translate('auto.k0427')} value={health?.service ?? 'asset-service'} />
            <StatusMetric label={translate('auto.k0182')} value={health?.status ?? (props.signedIn ? translate('auto.k0428') : translate('auto.k0429'))} pill />
            <StatusMetric label={translate('auto.k0133')} value={String(requirements.length)} />
            <StatusMetric label="API" value={String(apis.length)} />
            <StatusMetric label={translate('auto.k0134')} value={String(pages.length)} />
            <StatusMetric label={translate('auto.k0135')} value={String(flows.length)} />
            <StatusMetric label={translate('auto.k0136')} value={String(cases.length)} />
            <StatusMetric label={translate('auto.k0521')} value={String(stats.covered)} />
            <StatusMetric label={translate('auto.k0522')} value={String(stats.partial)} />
            <StatusMetric label={translate('auto.k0523')} value={String(stats.uncovered)} />
          </div>
          <div className="trace-summary-strip">
            <span>{translate('auto.k0565')}{orphanApis.length}</span>
            <span>{translate('auto.k0566')}{orphanPages.length}</span>
            <span>{translate('auto.k0567')}{orphanFlows.length}</span>
            <span>{translate('auto.k0568')}{orphanCases.length}</span>
          </div>
          {loadState.error && (
            <div className="inline-error">
              <strong>{translate('auto.k0148')}</strong>
              <span>{loadState.error}</span>
            </div>
          )}
        </section>

        <section className="panel insight-panel asset-detail-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0569')}</h2>
            {selectedSubject && <SubjectPill subject={selectedSubject} />}
          </div>
          <ImpactPanel
            apiById={apiById}
            caseById={caseById}
            flowById={flowById}
            onOpenAsset={openAsset}
            onSelectSubject={selectSubject}
            pageById={pageById}
            relations={relations}
            requirementById={requirementById}
            rows={matrixRows}
            subject={selectedSubject}
          />
        </section>
      </aside>
    </section>
  );
}

function buildRelations(links: TraceLinkView[], cases: AssetTestCaseView[]): TraceRelations {
  const relations: TraceRelations = {
    requirementToApi: new Map(),
    requirementToPage: new Map(),
    requirementToFlow: new Map(),
    requirementToCase: new Map(),
    apiToRequirement: new Map(),
    pageToRequirement: new Map(),
    flowToRequirement: new Map(),
    apiToCase: new Map(),
    caseToRequirement: new Map(),
    caseToApi: new Map()
  };

  links.forEach((link) => {
    addTraceEdge(relations, link.requirementId, link.apiId, link.pageId, link.flowId, link.caseId);
  });
  cases.forEach((item) => {
    addTraceEdge(relations, item.requirementId, item.apiId, undefined, undefined, item.id);
  });

  return relations;
}

function addTraceEdge(
  relations: TraceRelations,
  requirementId?: string,
  apiId?: string,
  pageId?: string,
  flowId?: string,
  caseId?: string
) {
  if (requirementId && apiId) {
    addToSet(relations.requirementToApi, requirementId, apiId);
    addToSet(relations.apiToRequirement, apiId, requirementId);
  }
  if (requirementId && pageId) {
    addToSet(relations.requirementToPage, requirementId, pageId);
    addToSet(relations.pageToRequirement, pageId, requirementId);
  }
  if (requirementId && flowId) {
    addToSet(relations.requirementToFlow, requirementId, flowId);
    addToSet(relations.flowToRequirement, flowId, requirementId);
  }
  if (requirementId && caseId) {
    addToSet(relations.requirementToCase, requirementId, caseId);
    addToSet(relations.caseToRequirement, caseId, requirementId);
  }
  if (apiId && caseId) {
    addToSet(relations.apiToCase, apiId, caseId);
    addToSet(relations.caseToApi, caseId, apiId);
  }
}

function buildMatrixRows(
  requirements: AssetRequirementView[],
  apiById: Map<string, AssetApiView>,
  pageById: Map<string, AssetPageView>,
  flowById: Map<string, AssetBusinessFlowView>,
  caseById: Map<string, AssetTestCaseView>,
  relations: TraceRelations
): MatrixRow[] {
  return requirements.map((requirement) => {
    const apis = resolvedItems(relations.requirementToApi.get(requirement.id), apiById);
    const pages = resolvedItems(relations.requirementToPage.get(requirement.id), pageById);
    const flows = resolvedItems(relations.requirementToFlow.get(requirement.id), flowById);
    const cases = resolvedItems(relations.requirementToCase.get(requirement.id), caseById);
    const coverage = coverageFor(apis.length, pages.length, flows.length, cases.length);
    const gaps = gapsFor(apis.length, pages.length, flows.length, cases.length);
    return {
      requirement,
      apis,
      pages,
      flows,
      cases,
      coverage,
      impactLevel: impactLevelFor(requirement, gaps.length),
      gaps,
      updatedAt: [
        requirement.updatedAt,
        ...apis.map((item) => item.updatedAt),
        ...pages.map((item) => item.updatedAt),
        ...flows.map((item) => item.updatedAt),
        ...cases.map((item) => item.updatedAt)
      ]
        .filter(Boolean)
        .sort()
        .at(-1)
    };
  });
}

function filterRows(rows: MatrixRow[], filters: TraceFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return rows.filter((row) => {
    if (filters.coverage.trim() && row.coverage !== filters.coverage.trim()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      row.requirement.title,
      row.requirement.description,
      row.requirement.sourceRef,
      row.requirement.projectId,
      row.requirement.tags.join(','),
      ...row.apis.flatMap((api) => [api.summary, api.path, api.code, api.sourceRef]),
      ...row.pages.flatMap((page) => [page.name, page.urlPattern, page.code, page.sourceRef]),
      ...row.flows.flatMap((flow) => [flow.name, flow.description, flow.code]),
      ...row.cases.flatMap((item) => [item.title, item.code, item.description, item.tags.join(',')])
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function coverageFor(apiCount: number, pageCount: number, flowCount: number, caseCount: number): CoverageStatus {
  if (apiCount > 0 && pageCount > 0 && flowCount > 0 && caseCount > 0) {
    return 'covered';
  }
  if (apiCount > 0 || pageCount > 0 || flowCount > 0 || caseCount > 0) {
    return 'partial';
  }
  return 'uncovered';
}

function gapsFor(apiCount: number, pageCount: number, flowCount: number, caseCount: number) {
  const gaps: string[] = [];
  if (apiCount === 0) {
    gaps.push(translate('auto.k0570'));
  }
  if (pageCount === 0) {
    gaps.push(translate('auto.k0571'));
  }
  if (flowCount === 0) {
    gaps.push(translate('auto.k0572'));
  }
  if (caseCount === 0) {
    gaps.push(translate('auto.k0573'));
  }
  return gaps;
}

function impactLevelFor(requirement: AssetRequirementView, gapCount: number): ImpactLevel {
  if (gapCount === 0) {
    return 'low';
  }
  if (gapCount >= 3 && ['CRITICAL', 'HIGH'].includes(String(requirement.priority))) {
    return 'high';
  }
  return 'medium';
}

function traceStats(rows: MatrixRow[]) {
  return rows.reduce(
    (stats, row) => {
      stats[row.coverage] += 1;
      return stats;
    },
    {
      covered: 0,
      partial: 0,
      uncovered: 0
    }
  );
}

function mapById<T extends { id: string }>(items: T[]) {
  return new Map(items.filter((item) => item.id).map((item) => [item.id, item]));
}

function addToSet(map: Map<string, Set<string>>, key: string, value: string) {
  const next = map.get(key) ?? new Set<string>();
  next.add(value);
  map.set(key, next);
}

function resolvedItems<T>(ids: Set<string> | undefined, itemById: Map<string, T>): T[] {
  return Array.from(ids ?? [])
    .map((id) => itemById.get(id))
    .filter((item): item is T => Boolean(item));
}

function hasResolvedRelation<T>(ids: Set<string> | undefined, itemById: Map<string, T>) {
  return resolvedItems(ids, itemById).length > 0;
}

function subjectExists(
  subject: SelectedSubject,
  requirementById: Map<string, AssetRequirementView>,
  apiById: Map<string, AssetApiView>,
  pageById: Map<string, AssetPageView>,
  flowById: Map<string, AssetBusinessFlowView>,
  caseById: Map<string, AssetTestCaseView>
) {
  switch (subject.type) {
    case 'requirement':
      return requirementById.has(subject.id);
    case 'api':
      return apiById.has(subject.id);
    case 'page':
      return pageById.has(subject.id);
    case 'flow':
      return flowById.has(subject.id);
    case 'case':
      return caseById.has(subject.id);
    default:
      return false;
  }
}

function subjectFromHash(): SelectedSubject | null {
  const parts = window.location.hash.replace(/^#\/?/, '').split('/');
  if (parts[0] !== 'asset-library' || parts[1] !== 'trace') {
    return null;
  }
  if (parts[2] === 'api' && parts[3]) {
    return { type: 'api', id: decodeURIComponent(parts[3]) };
  }
  if (parts[2] === 'page' && parts[3]) {
    return { type: 'page', id: decodeURIComponent(parts[3]) };
  }
  if (parts[2] === 'flow' && parts[3]) {
    return { type: 'flow', id: decodeURIComponent(parts[3]) };
  }
  if (parts[2] === 'case' && parts[3]) {
    return { type: 'case', id: decodeURIComponent(parts[3]) };
  }
  if (parts[2] === 'requirement' && parts[3]) {
    return { type: 'requirement', id: decodeURIComponent(parts[3]) };
  }
  if (parts[2]) {
    return { type: 'requirement', id: decodeURIComponent(parts[2]) };
  }
  return null;
}

function ImpactPanel(props: {
  apiById: Map<string, AssetApiView>;
  caseById: Map<string, AssetTestCaseView>;
  flowById: Map<string, AssetBusinessFlowView>;
  onOpenAsset: (tab: AssetNavigationKey, id: string) => void;
  onSelectSubject: (subject: SelectedSubject) => void;
  pageById: Map<string, AssetPageView>;
  relations: TraceRelations;
  requirementById: Map<string, AssetRequirementView>;
  rows: MatrixRow[];
  subject: SelectedSubject | null;
}) {
  if (!props.subject) {
    return (
      <div className="empty-state compact">
        <GitBranch size={20} />
        <div>
          <strong>{translate('auto.k0574')}</strong>
          <span>{translate('auto.k0575')}</span>
        </div>
      </div>
    );
  }

  if (props.subject.type === 'requirement') {
    const row = props.rows.find((item) => item.requirement.id === props.subject?.id);
    if (!row) {
      return <MissingSubject />;
    }
    return (
      <div className="asset-detail-stack">
        <div className="resource-summary">
          <strong>{row.requirement.title}</strong>
          <div><span>projectId</span><em>{row.requirement.projectId ?? '-'}</em></div>
          <div><span>priority</span><em>{dictionaryLabel(row.requirement.priority)}</em></div>
          <div><span>status</span><em>{dictionaryLabel(row.requirement.status)}</em></div>
          <div><span>id</span><em>{row.requirement.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>{translate('auto.k0576')}</strong>
          <div><span>{translate('auto.k0538')}</span><em>{coverageLabels[row.coverage]}</em></div>
          <div><span>{translate('auto.k0577')}</span><em>{impactLabels[row.impactLevel]}</em></div>
          <div><span>{translate('auto.k0544')}</span><em>{row.gaps.length ? row.gaps.join('，') : translate('auto.k0578')}</em></div>
          <div><span>{translate('auto.k0579')}</span><em>{translate('auto.k0580')}</em></div>
        </div>
        <ImpactAssetList
          title={translate('auto.k0541')}
          items={row.apis}
          empty={translate('auto.k0581')}
          render={(api) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'api', id: api.id })}
              onOpen={() => props.onOpenAsset('apis', api.id)}
              primary={`${api.httpMethod} ${api.path}`}
              secondary={api.summary}
            />
          )}
        />
        <ImpactAssetList
          title={translate('auto.k0582')}
          items={row.pages}
          empty={translate('auto.k0583')}
          render={(page) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'page', id: page.id })}
              onOpen={() => props.onOpenAsset('pages', page.id)}
              primary={page.name}
              secondary={page.urlPattern ?? page.status}
            />
          )}
        />
        <ImpactAssetList
          title={translate('auto.k0584')}
          items={row.flows}
          empty={translate('auto.k0585')}
          render={(flow) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'flow', id: flow.id })}
              onOpen={() => props.onOpenAsset('flows', flow.id)}
              primary={flow.name}
              secondary={flow.status}
            />
          )}
        />
        <ImpactAssetList
          title={translate('auto.k0543')}
          items={row.cases}
          empty={translate('auto.k0586')}
          render={(item) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'case', id: item.id })}
              onOpen={() => props.onOpenAsset('cases', item.id)}
              primary={item.title}
              secondary={item.status}
            />
          )}
        />
        <button className="mini-button" type="button" onClick={() => props.onOpenAsset('requirements', row.requirement.id)}>
          <Eye size={14} />
          {translate('auto.k0587')}</button>
      </div>
    );
  }

  if (props.subject.type === 'api') {
    const api = props.apiById.get(props.subject.id);
    if (!api) {
      return <MissingSubject />;
    }
    const requirements = resolvedItems(props.relations.apiToRequirement.get(api.id), props.requirementById);
    const cases = resolvedItems(props.relations.apiToCase.get(api.id), props.caseById);
    return (
      <div className="asset-detail-stack">
        <div className="resource-summary">
          <strong>{api.summary}</strong>
          <div><span>method</span><em>{api.httpMethod}</em></div>
          <div><span>path</span><em>{api.path}</em></div>
          <div><span>status</span><em>{dictionaryLabel(api.status)}</em></div>
          <div><span>id</span><em>{api.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>{translate('auto.k0576')}</strong>
          <div><span>{translate('auto.k0133')}</span><em>{requirements.length}</em></div>
          <div><span>{translate('auto.k0136')}</span><em>{cases.length}</em></div>
          <div><span>{translate('auto.k0544')}</span><em>{requirements.length && cases.length ? translate('auto.k0578') : apiGaps(requirements.length, cases.length).join('，')}</em></div>
          <div><span>{translate('auto.k0579')}</span><em>{translate('auto.k0588')}</em></div>
        </div>
        <ImpactAssetList
          title={translate('auto.k0589')}
          items={requirements}
          empty={translate('auto.k0590')}
          render={(requirement) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'requirement', id: requirement.id })}
              onOpen={() => props.onOpenAsset('requirements', requirement.id)}
              primary={requirement.title}
              secondary={requirement.status}
            />
          )}
        />
        <ImpactAssetList
          title={translate('auto.k0543')}
          items={cases}
          empty={translate('auto.k0591')}
          render={(item) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'case', id: item.id })}
              onOpen={() => props.onOpenAsset('cases', item.id)}
              primary={item.title}
              secondary={item.status}
            />
          )}
        />
        <button className="mini-button" type="button" onClick={() => props.onOpenAsset('apis', api.id)}>
          <Eye size={14} />
          {translate('auto.k0592')}</button>
      </div>
    );
  }

  if (props.subject.type === 'page') {
    const page = props.pageById.get(props.subject.id);
    if (!page) {
      return <MissingSubject />;
    }
    const requirements = resolvedItems(props.relations.pageToRequirement.get(page.id), props.requirementById);
    return (
      <div className="asset-detail-stack">
        <div className="resource-summary">
          <strong>{page.name}</strong>
          <div><span>projectId</span><em>{page.projectId ?? '-'}</em></div>
          <div><span>urlPattern</span><em>{page.urlPattern ?? '-'}</em></div>
          <div><span>status</span><em>{dictionaryLabel(page.status)}</em></div>
          <div><span>id</span><em>{page.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>{translate('auto.k0576')}</strong>
          <div><span>{translate('auto.k0133')}</span><em>{requirements.length}</em></div>
          <div><span>{translate('auto.k0179')}</span><em>{page.source}</em></div>
          <div><span>{translate('auto.k0544')}</span><em>{requirements.length ? translate('auto.k0593') : translate('auto.k0594')}</em></div>
          <div><span>{translate('auto.k0579')}</span><em>{translate('auto.k0595')}</em></div>
        </div>
        <ImpactAssetList
          title={translate('auto.k0589')}
          items={requirements}
          empty={translate('auto.k0590')}
          render={(requirement) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'requirement', id: requirement.id })}
              onOpen={() => props.onOpenAsset('requirements', requirement.id)}
              primary={requirement.title}
              secondary={requirement.status}
            />
          )}
        />
        <button className="mini-button" type="button" onClick={() => props.onOpenAsset('pages', page.id)}>
          <Eye size={14} />
          {translate('auto.k0596')}</button>
      </div>
    );
  }

  if (props.subject.type === 'flow') {
    const flow = props.flowById.get(props.subject.id);
    if (!flow) {
      return <MissingSubject />;
    }
    const requirements = resolvedItems(props.relations.flowToRequirement.get(flow.id), props.requirementById);
    return (
      <div className="asset-detail-stack">
        <div className="resource-summary">
          <strong>{flow.name}</strong>
          <div><span>projectId</span><em>{flow.projectId ?? '-'}</em></div>
          <div><span>priority</span><em>{dictionaryLabel(flow.priority)}</em></div>
          <div><span>status</span><em>{dictionaryLabel(flow.status)}</em></div>
          <div><span>id</span><em>{flow.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>{translate('auto.k0576')}</strong>
          <div><span>{translate('auto.k0133')}</span><em>{requirements.length}</em></div>
          <div><span>{translate('auto.k0264')}</span><em>{flow.description ?? '-'}</em></div>
          <div><span>{translate('auto.k0544')}</span><em>{requirements.length ? translate('auto.k0593') : translate('auto.k0594')}</em></div>
          <div><span>{translate('auto.k0579')}</span><em>{translate('auto.k0597')}</em></div>
        </div>
        <ImpactAssetList
          title={translate('auto.k0589')}
          items={requirements}
          empty={translate('auto.k0590')}
          render={(requirement) => (
            <TraceAssetButton
              onImpact={() => props.onSelectSubject({ type: 'requirement', id: requirement.id })}
              onOpen={() => props.onOpenAsset('requirements', requirement.id)}
              primary={requirement.title}
              secondary={requirement.status}
            />
          )}
        />
        <button className="mini-button" type="button" onClick={() => props.onOpenAsset('flows', flow.id)}>
          <Eye size={14} />
          {translate('auto.k0598')}</button>
      </div>
    );
  }

  const testCase = props.caseById.get(props.subject.id);
  if (!testCase) {
    return <MissingSubject />;
  }
  const requirements = resolvedItems(props.relations.caseToRequirement.get(testCase.id), props.requirementById);
  const apis = resolvedItems(props.relations.caseToApi.get(testCase.id), props.apiById);
  return (
    <div className="asset-detail-stack">
      <div className="resource-summary">
        <strong>{testCase.title}</strong>
        <div><span>projectId</span><em>{testCase.projectId ?? '-'}</em></div>
        <div><span>priority</span><em>{dictionaryLabel(testCase.priority)}</em></div>
        <div><span>status</span><em>{dictionaryLabel(testCase.status)}</em></div>
        <div><span>id</span><em>{testCase.id}</em></div>
      </div>
      <div className="asset-source-trace">
        <strong>{translate('auto.k0576')}</strong>
        <div><span>{translate('auto.k0133')}</span><em>{requirements.length}</em></div>
        <div><span>API</span><em>{apis.length}</em></div>
        <div><span>{translate('auto.k0544')}</span><em>{requirements.length ? translate('auto.k0593') : translate('auto.k0594')}</em></div>
        <div><span>{translate('auto.k0579')}</span><em>{translate('auto.k0599')}</em></div>
      </div>
      <ImpactAssetList
        title={translate('auto.k0600')}
        items={requirements}
        empty={translate('auto.k0590')}
        render={(requirement) => (
          <TraceAssetButton
            onImpact={() => props.onSelectSubject({ type: 'requirement', id: requirement.id })}
            onOpen={() => props.onOpenAsset('requirements', requirement.id)}
            primary={requirement.title}
            secondary={requirement.status}
          />
        )}
      />
      <ImpactAssetList
        title={translate('auto.k0601')}
        items={apis}
        empty={translate('auto.k0602')}
        render={(api) => (
          <TraceAssetButton
            onImpact={() => props.onSelectSubject({ type: 'api', id: api.id })}
            onOpen={() => props.onOpenAsset('apis', api.id)}
            primary={`${api.httpMethod} ${api.path}`}
            secondary={api.summary}
          />
        )}
      />
      <button className="mini-button" type="button" onClick={() => props.onOpenAsset('cases', testCase.id)}>
        <Eye size={14} />
        {translate('auto.k0603')}</button>
    </div>
  );
}

function CoveragePill(props: { status: CoverageStatus }) {
  const Icon = props.status === 'covered' ? CheckCircle2 : props.status === 'partial' ? GitBranch : AlertTriangle;
  return (
    <span className={`coverage-pill ${props.status}`}>
      <Icon size={13} />
      {coverageLabels[props.status]}
    </span>
  );
}

function SubjectPill(props: { subject: SelectedSubject }) {
  return <span className="coverage-pill neutral">{labelAssetTraceSubjectType(props.subject.type)}</span>;
}

function LinkedList<T>(props: { empty: string; items: T[]; render: (item: T) => ReactNode }) {
  if (!props.items.length) {
    return <span className="trace-list-empty">{props.empty}</span>;
  }
  return (
    <div className="trace-linked-list">
      {props.items.slice(0, 3).map((item, index) => (
        <span key={index}>{props.render(item)}</span>
      ))}
      {props.items.length > 3 && <em>+{props.items.length - 3}</em>}
    </div>
  );
}

function GapList(props: { gaps: string[] }) {
  if (!props.gaps.length) {
    return <span className="trace-list-empty">{translate('auto.k0578')}</span>;
  }
  return (
    <div className="trace-gap-list">
      {props.gaps.map((gap) => (
        <span key={gap}>{gap}</span>
      ))}
    </div>
  );
}

function GapBucket<T>(props: { empty: string; items: T[]; render: (item: T) => ReactNode; title: string }) {
  return (
    <div className="trace-gap-bucket">
      <div className="panel-title-row">
        <strong>{props.title}</strong>
        <span className="document-count-badge">{props.items.length}</span>
      </div>
      <div className="trace-chip-list">
        {props.items.length ? props.items.slice(0, 8).map((item, index) => <div key={index}>{props.render(item)}</div>) : (
          <span className="trace-list-empty">{props.empty}</span>
        )}
      </div>
    </div>
  );
}

function ImpactAssetList<T>(props: { empty: string; items: T[]; render: (item: T) => ReactNode; title: string }) {
  return (
    <div className="asset-trace-links">
      <div className="panel-title-row">
        <strong>{props.title}</strong>
        <span className="document-count-badge">{props.items.length}</span>
      </div>
      {props.items.length ? props.items.map((item, index) => <div key={index}>{props.render(item)}</div>) : (
        <span className="trace-list-empty">{props.empty}</span>
      )}
    </div>
  );
}

function TraceAssetButton(props: { onImpact: () => void; onOpen: () => void; primary: string; secondary?: string }) {
  return (
    <div className="trace-asset-action">
      <button className="trace-chip" type="button" onClick={props.onImpact}>
        <strong>{props.primary}</strong>
        <span>{props.secondary ?? '-'}</span>
      </button>
      <button className="mini-button icon-only" type="button" onClick={props.onOpen} title={translate('auto.k0604')}>
        <Eye size={14} />
      </button>
    </div>
  );
}

function MissingSubject() {
  return (
    <div className="empty-state compact">
      <Link2 size={20} />
      <div>
        <strong>{translate('auto.k0605')}</strong>
        <span>{translate('auto.k0606')}</span>
      </div>
    </div>
  );
}

function StatusMetric(props: { label: string; value: string; pill?: boolean }) {
  return (
    <div className={`status-item ${props.pill ? 'status-pill' : ''}`}>
      <span>{props.label}</span>
      <strong>{props.value}</strong>
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">{translate('auto.k0371')}</span>;
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

function requirementContextItems(pages: AssetPageView[], flows: AssetBusinessFlowView[]): TraceContextItem[] {
  return [
    ...pages.map((page) => ({
      primary: page.name,
      secondary: page.urlPattern ?? page.status
    })),
    ...flows.map((flow) => ({
      primary: flow.name,
      secondary: flow.status
    }))
  ];
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
}

function apiGaps(requirementCount: number, caseCount: number) {
  const gaps: string[] = [];
  if (requirementCount === 0) {
    gaps.push(translate('auto.k0594'));
  }
  if (caseCount === 0) {
    gaps.push(translate('auto.k0573'));
  }
  return gaps;
}
