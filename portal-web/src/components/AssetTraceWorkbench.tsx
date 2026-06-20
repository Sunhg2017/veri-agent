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
  covered: '全覆盖',
  partial: '部分覆盖',
  uncovered: '未覆盖'
};

const impactLabels: Record<ImpactLevel, string> = {
  low: '低',
  medium: '中',
  high: '高'
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
      errors.push(errorMessage(healthResult.reason, '资产服务健康检查失败'));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(errorMessage(requirementResult.reason, '需求资产加载失败'));
    }

    if (apiResult.status === 'fulfilled') {
      setApis(apiResult.value.data.items);
      traceIds.push(apiResult.value.trace_id);
    } else {
      setApis([]);
      errors.push(errorMessage(apiResult.reason, 'API 资产加载失败'));
    }

    if (pageResult.status === 'fulfilled') {
      setPages(pageResult.value.data.items);
      traceIds.push(pageResult.value.trace_id);
    } else {
      setPages([]);
      errors.push(errorMessage(pageResult.reason, '页面资产加载失败'));
    }

    if (flowResult.status === 'fulfilled') {
      setFlows(flowResult.value.data.items);
      traceIds.push(flowResult.value.trace_id);
    } else {
      setFlows([]);
      errors.push(errorMessage(flowResult.reason, '业务流资产加载失败'));
    }

    if (caseResult.status === 'fulfilled') {
      setCases(caseResult.value.data.items);
      traceIds.push(caseResult.value.trace_id);
    } else {
      setCases([]);
      errors.push(errorMessage(caseResult.reason, '测试用例加载失败'));
    }

    if (linkResult.status === 'fulfilled') {
      setLinks(linkResult.value.data.items);
      traceIds.push(linkResult.value.trace_id);
    } else {
      setLinks([]);
      errors.push(errorMessage(linkResult.reason, '追踪链接加载失败'));
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
                <h2>资产追踪矩阵</h2>
              </div>
            </div>
            <button
              className="secondary-button"
              type="button"
              disabled={disabled}
              onClick={refreshTrace}
            >
              <RefreshCw size={16} />
              刷新
            </button>
          </div>

          <div className="asset-tab-strip" aria-label="资产类型">
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
              <span>需求状态</span>
              <select
                id="asset-trace-filter-requirement-status"
                value={filters.requirementStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, requirementStatus: event.target.value }))}
              >
                <option value="">全部</option>
                {ASSET_REQUIREMENT_STATUSES.map((status) => (
                  <option key={status} value={status}>{status}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-trace-filter-api-status">
              <span>API 状态</span>
              <select
                id="asset-trace-filter-api-status"
                value={filters.apiStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, apiStatus: event.target.value }))}
              >
                <option value="">全部</option>
                {ASSET_API_STATUSES.map((status) => (
                  <option key={status} value={status}>{status}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-trace-filter-page-status">
              <span>页面状态</span>
              <select
                id="asset-trace-filter-page-status"
                value={filters.pageStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, pageStatus: event.target.value }))}
              >
                <option value="">全部</option>
                {ASSET_PAGE_STATUSES.map((status) => (
                  <option key={status} value={status}>{status}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-trace-filter-flow-status">
              <span>业务流状态</span>
              <select
                id="asset-trace-filter-flow-status"
                value={filters.flowStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, flowStatus: event.target.value }))}
              >
                <option value="">全部</option>
                {ASSET_FLOW_STATUSES.map((status) => (
                  <option key={status} value={status}>{status}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-trace-filter-case-status">
              <span>用例状态</span>
              <select
                id="asset-trace-filter-case-status"
                value={filters.caseStatus}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, caseStatus: event.target.value }))}
              >
                <option value="">全部</option>
                {ASSET_TEST_CASE_STATUSES.map((status) => (
                  <option key={status} value={status}>{status}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-trace-filter-coverage">
              <span>覆盖</span>
              <select
                id="asset-trace-filter-coverage"
                value={filters.coverage}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, coverage: event.target.value }))}
              >
                <option value="">全部</option>
                <option value="covered">全覆盖</option>
                <option value="partial">部分覆盖</option>
                <option value="uncovered">未覆盖</option>
              </select>
            </label>
            <label className="field" htmlFor="asset-trace-filter-keyword">
              <span>keyword</span>
              <input
                id="asset-trace-filter-keyword"
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder="需求 / API / 页面 / 业务流 / 用例"
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={submitFilters}>
                <Search size={14} />
                查询
              </button>
              <button className="mini-button" type="button" disabled={disabled} onClick={resetFilters}>
                <XCircle size={14} />
                清空
              </button>
            </div>
          </form>

          <div className="table-wrap asset-table-wrap trace-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>需求</th>
                  <th>覆盖状态</th>
                  <th>关联 API</th>
                  <th>页面 / 业务流</th>
                  <th>关联用例</th>
                  <th>缺口</th>
                  <th>更新时间</th>
                  <th>操作</th>
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
                        <span className={`impact-pill ${row.impactLevel}`}>影响 {impactLabels[row.impactLevel]}</span>
                      </td>
                      <td>
                        <LinkedList
                          items={row.apis}
                          empty="无关联 API"
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
                          empty="无页面 / 业务流"
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
                          empty="无关联用例"
                          render={(item) => (
                            <>
                              <strong>{item.title}</strong>
                              <em>{item.status}</em>
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
                          拓扑
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={8}>
                      {props.signedIn ? (loadState.loading ? '加载中' : '暂无追踪矩阵数据') : '请先登录'}
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
                <h2>缺口与反向影响</h2>
              </div>
            </div>
          </div>
          <div className="trace-gap-grid">
            <GapBucket
              title="无需求关联 API"
              items={orphanApis}
              empty="API 均已关联需求"
              render={(api) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'api', id: api.id })}>
                  <strong>{api.httpMethod} {api.path}</strong>
                  <span>{api.summary}</span>
                </button>
              )}
            />
            <GapBucket
              title="无用例覆盖 API"
              items={apisWithoutCases}
              empty="API 均有用例覆盖"
              render={(api) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'api', id: api.id })}>
                  <strong>{api.httpMethod} {api.path}</strong>
                  <span>{api.summary}</span>
                </button>
              )}
            />
            <GapBucket
              title="无需求关联页面"
              items={orphanPages}
              empty="页面均已关联需求"
              render={(page) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'page', id: page.id })}>
                  <strong>{page.name}</strong>
                  <span>{page.urlPattern ?? page.status}</span>
                </button>
              )}
            />
            <GapBucket
              title="无需求关联业务流"
              items={orphanFlows}
              empty="业务流均已关联需求"
              render={(flow) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'flow', id: flow.id })}>
                  <strong>{flow.name}</strong>
                  <span>{flow.status}</span>
                </button>
              )}
            />
            <GapBucket
              title="无需求关联用例"
              items={orphanCases}
              empty="用例均已关联需求"
              render={(item) => (
                <button className="trace-chip" type="button" onClick={() => selectSubject({ type: 'case', id: item.id })}>
                  <strong>{item.title}</strong>
                  <span>{item.status}</span>
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
                <h2>资产关系拓扑图</h2>
              </div>
            </div>
            {selectedSubject && <SubjectPill subject={selectedSubject} />}
          </div>
          <div className="trace-topology-heading">
            <strong>{topologyFocusLabel}</strong>
            <span>基于需求、API、页面、业务流、用例与追踪链接构建</span>
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
          <h2>矩阵统计</h2>
          <div className="document-health-grid">
            <StatusMetric label="服务" value={health?.service ?? 'asset-service'} />
            <StatusMetric label="状态" value={health?.status ?? (props.signedIn ? '等待响应' : '等待登录')} pill />
            <StatusMetric label="需求" value={String(requirements.length)} />
            <StatusMetric label="API" value={String(apis.length)} />
            <StatusMetric label="页面" value={String(pages.length)} />
            <StatusMetric label="业务流" value={String(flows.length)} />
            <StatusMetric label="用例" value={String(cases.length)} />
            <StatusMetric label="全覆盖" value={String(stats.covered)} />
            <StatusMetric label="部分覆盖" value={String(stats.partial)} />
            <StatusMetric label="未覆盖" value={String(stats.uncovered)} />
          </div>
          <div className="trace-summary-strip">
            <span>孤立 API {orphanApis.length}</span>
            <span>孤立页面 {orphanPages.length}</span>
            <span>孤立业务流 {orphanFlows.length}</span>
            <span>孤立用例 {orphanCases.length}</span>
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
            <h2>焦点资产详情</h2>
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
    gaps.push('缺 API 覆盖');
  }
  if (pageCount === 0) {
    gaps.push('缺页面覆盖');
  }
  if (flowCount === 0) {
    gaps.push('缺业务流覆盖');
  }
  if (caseCount === 0) {
    gaps.push('缺用例覆盖');
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
          <strong>暂无焦点资产</strong>
          <span>从矩阵、缺口清单或拓扑图中选择资产</span>
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
          <div><span>priority</span><em>{row.requirement.priority}</em></div>
          <div><span>status</span><em>{row.requirement.status}</em></div>
          <div><span>id</span><em>{row.requirement.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>影响说明</strong>
          <div><span>覆盖</span><em>{coverageLabels[row.coverage]}</em></div>
          <div><span>影响等级</span><em>{impactLabels[row.impactLevel]}</em></div>
          <div><span>缺口</span><em>{row.gaps.length ? row.gaps.join('，') : '暂无缺口'}</em></div>
          <div><span>口径</span><em>基于直连追踪链接的一跳分析</em></div>
        </div>
        <ImpactAssetList
          title="关联 API"
          items={row.apis}
          empty="暂无关联 API"
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
          title="关联页面"
          items={row.pages}
          empty="暂无关联页面"
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
          title="关联业务流"
          items={row.flows}
          empty="暂无关联业务流"
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
          title="关联用例"
          items={row.cases}
          empty="暂无关联用例"
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
          打开需求详情
        </button>
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
          <div><span>status</span><em>{api.status}</em></div>
          <div><span>id</span><em>{api.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>影响说明</strong>
          <div><span>需求</span><em>{requirements.length}</em></div>
          <div><span>用例</span><em>{cases.length}</em></div>
          <div><span>缺口</span><em>{requirements.length && cases.length ? '暂无缺口' : apiGaps(requirements.length, cases.length).join('，')}</em></div>
          <div><span>口径</span><em>基于 API 直连需求和用例的一跳反查</em></div>
        </div>
        <ImpactAssetList
          title="关联需求"
          items={requirements}
          empty="暂无需求关联"
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
          title="关联用例"
          items={cases}
          empty="暂无用例覆盖"
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
          打开 API 详情
        </button>
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
          <div><span>status</span><em>{page.status}</em></div>
          <div><span>id</span><em>{page.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>影响说明</strong>
          <div><span>需求</span><em>{requirements.length}</em></div>
          <div><span>来源</span><em>{page.source}</em></div>
          <div><span>缺口</span><em>{requirements.length ? '暂无需求缺口' : '缺需求关联'}</em></div>
          <div><span>口径</span><em>基于页面直连需求的一跳反查</em></div>
        </div>
        <ImpactAssetList
          title="关联需求"
          items={requirements}
          empty="暂无需求关联"
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
          打开页面详情
        </button>
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
          <div><span>priority</span><em>{flow.priority}</em></div>
          <div><span>status</span><em>{flow.status}</em></div>
          <div><span>id</span><em>{flow.id}</em></div>
        </div>
        <div className="asset-source-trace">
          <strong>影响说明</strong>
          <div><span>需求</span><em>{requirements.length}</em></div>
          <div><span>说明</span><em>{flow.description ?? '-'}</em></div>
          <div><span>缺口</span><em>{requirements.length ? '暂无需求缺口' : '缺需求关联'}</em></div>
          <div><span>口径</span><em>基于业务流直连需求的一跳反查</em></div>
        </div>
        <ImpactAssetList
          title="关联需求"
          items={requirements}
          empty="暂无需求关联"
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
          打开业务流详情
        </button>
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
        <div><span>priority</span><em>{testCase.priority}</em></div>
        <div><span>status</span><em>{testCase.status}</em></div>
        <div><span>id</span><em>{testCase.id}</em></div>
      </div>
      <div className="asset-source-trace">
        <strong>影响说明</strong>
        <div><span>需求</span><em>{requirements.length}</em></div>
        <div><span>API</span><em>{apis.length}</em></div>
        <div><span>缺口</span><em>{requirements.length ? '暂无需求缺口' : '缺需求关联'}</em></div>
        <div><span>口径</span><em>基于用例直连需求和 API 的一跳反查</em></div>
      </div>
      <ImpactAssetList
        title="验证需求"
        items={requirements}
        empty="暂无需求关联"
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
        title="覆盖 API"
        items={apis}
        empty="暂无 API 关联"
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
        打开用例详情
      </button>
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
    return <span className="trace-list-empty">暂无缺口</span>;
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
      <button className="mini-button icon-only" type="button" onClick={props.onOpen} title="打开详情">
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
        <strong>资产未在当前筛选结果中</strong>
        <span>调整筛选条件或刷新矩阵后再查看影响范围</span>
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
    return <span className="document-state-line">加载中...</span>;
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
    gaps.push('缺需求关联');
  }
  if (caseCount === 0) {
    gaps.push('缺用例覆盖');
  }
  return gaps;
}
