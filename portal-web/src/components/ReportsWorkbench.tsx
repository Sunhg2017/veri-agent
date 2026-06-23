import {
  AlertTriangle,
  Archive,
  Bug,
  CheckCircle2,
  Download,
  FileJson,
  FileText,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  archiveReport,
  compareReport,
  createDefectDraft,
  diagnoseReport,
  downloadReportExport,
  exportReport,
  fetchReport,
  fetchReportingHealth,
  fetchReports,
  generateReport,
  retryReport,
  reviewDefectDraft,
  type ReportCompare,
  type ReportDefectDraft,
  type ReportDetail,
  type ReportDiagnosis,
  type ReportExport,
  type ReportExportType,
  type ReportingHealth,
  type ReportSummary
} from '../api/reports';
import { canUseButton, hasPermission } from '../permissions';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type ReportFilters = {
  projectId: string;
  executionRunId: string;
  status: string;
};

type GenerateDraft = {
  projectId: string;
  executionRunId: string;
  requestKey: string;
  reason: string;
};

const initialFilters: ReportFilters = { projectId: '', executionRunId: '', status: '' };
const initialGenerateDraft: GenerateDraft = {
  projectId: '',
  executionRunId: '',
  requestKey: '',
  reason: ''
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const requestKeyPattern = /^[A-Za-z0-9_.:-]{1,128}$/;
const blockedDomTerms = [
  'authorization',
  'cookie',
  'lease token',
  'raw prompt',
  'raw response',
  'runner stdout',
  'runner stderr',
  'secret://',
  'token='
];

export function ReportsWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'report:read');
  const canGenerate = canUseButton(props.currentUser, 'report:generate');
  const canDiagnose = canUseButton(props.currentUser, 'report:diagnose');
  const canExport = canUseButton(props.currentUser, 'report:export');
  const canManage = canUseButton(props.currentUser, 'report:manage');

  const [health, setHealth] = useState<ReportingHealth | null>(null);
  const [reports, setReports] = useState<ReportSummary[]>([]);
  const [filters, setFilters] = useState<ReportFilters>(initialFilters);
  const [selectedReportId, setSelectedReportId] = useState('');
  const [detail, setDetail] = useState<ReportDetail | null>(null);
  const [latestExport, setLatestExport] = useState<ReportExport | null>(null);
  const [compareResult, setCompareResult] = useState<ReportCompare | null>(null);
  const [baselineReportId, setBaselineReportId] = useState('');
  const [generateDraft, setGenerateDraft] = useState<GenerateDraft>(initialGenerateDraft);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [generateState, setGenerateState] = useState<WorkState>({ loading: false });
  const [diagnosisState, setDiagnosisState] = useState<WorkState>({ loading: false });
  const [defectState, setDefectState] = useState<WorkState>({ loading: false });
  const [exportState, setExportState] = useState<WorkState>({ loading: false });
  const [compareState, setCompareState] = useState<WorkState>({ loading: false });

  const summary = useMemo(() => {
    const ready = reports.filter((report) => report.status === 'READY').length;
    const generating = reports.filter((report) => ['QUEUED', 'GENERATING'].includes(report.status)).length;
    const failed = reports.filter((report) => report.status === 'FAILED').length;
    const drafts = reports.reduce((count, report) => count + numberFrom(report.summary.defectDraftCount), 0);
    return { ready, generating, failed, drafts };
  }, [reports]);

  const baselineCandidates = useMemo(() => {
    if (!detail) return [];
    return reports.filter((report) => report.id !== detail.id && report.projectId === detail.projectId);
  }, [detail, reports]);

  const refreshReports = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setReports([]);
      setSelectedReportId('');
      setDetail(null);
      setLatestExport(null);
      setCompareResult(null);
      setBaselineReportId('');
      return;
    }
    setLoadState({ loading: true });
    try {
      const [healthResult, reportResult] = await Promise.all([
        fetchReportingHealth(),
        fetchReports({
          projectId: optionalText(filters.projectId),
          executionRunId: optionalText(filters.executionRunId),
          status: optionalText(filters.status),
          size: 50
        })
      ]);
      setHealth(healthResult.data);
      setReports(reportResult.data.items);
      setSelectedReportId((current) => current || reportResult.data.items[0]?.id || '');
      setLoadState({ loading: false, traceId: reportResult.trace_id });
    } catch (error: unknown) {
      setLoadState({ loading: false, error: error instanceof Error ? error.message : '加载报告失败' });
    }
  }, [canRead, filters.executionRunId, filters.projectId, filters.status, props.signedIn]);

  const refreshDetail = useCallback(async (reportId: string) => {
    if (!reportId || !canRead) {
      setDetail(null);
      setLatestExport(null);
      setCompareResult(null);
      setBaselineReportId('');
      return;
    }
    setDetailState({ loading: true });
    try {
      const result = await fetchReport(reportId);
      setDetail(result.data);
      setLatestExport(null);
      setCompareResult(null);
      setBaselineReportId('');
      setCompareState({ loading: false });
      setDetailState({ loading: false, traceId: result.trace_id });
    } catch (error: unknown) {
      setDetailState({ loading: false, error: error instanceof Error ? error.message : '加载报告详情失败' });
    }
  }, [canRead]);

  useEffect(() => {
    void refreshReports();
  }, [refreshReports]);

  useEffect(() => {
    void refreshDetail(selectedReportId);
  }, [refreshDetail, selectedReportId]);

  if (!props.signedIn) {
    return <div className="notice warning">请先登录后查看报告诊断。</div>;
  }

  if (!canRead) {
    return <div className="notice error">当前账号缺少 report:read 权限。</div>;
  }

  async function onGenerateReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canGenerate) return;
    const issues = validateGenerateDraft(generateDraft);
    if (issues.length) {
      setGenerateState({ loading: false, error: issues.join('；') });
      return;
    }
    setGenerateState({ loading: true });
    try {
      const result = await generateReport({
        projectId: generateDraft.projectId.trim(),
        executionRunId: generateDraft.executionRunId.trim(),
        requestKey: optionalText(generateDraft.requestKey),
        reason: optionalText(generateDraft.reason)
      });
      setDetail(result.data);
      setCompareResult(null);
      setBaselineReportId('');
      setSelectedReportId(result.data.id);
      setReports((current) => [summaryFromDetail(result.data), ...current.filter((report) => report.id !== result.data.id)]);
      setGenerateState({
        loading: false,
        success: result.data.idempotentReplay ? '已回放既有报告' : '报告快照已生成',
        traceId: result.trace_id
      });
      setGenerateDraft((current) => ({ ...current, requestKey: '', reason: '' }));
    } catch (error: unknown) {
      setGenerateState({ loading: false, error: error instanceof Error ? error.message : '生成报告失败' });
    }
  }

  async function onRetryReport() {
    if (!detail || !canGenerate) return;
    setGenerateState({ loading: true });
    try {
      const result = await retryReport(detail.id);
      applyDetail(result.data);
      setGenerateState({ loading: false, success: '报告已重试生成', traceId: result.trace_id });
    } catch (error: unknown) {
      setGenerateState({ loading: false, error: error instanceof Error ? error.message : '重试失败' });
    }
  }

  async function onArchiveReport() {
    if (!detail || !canManage) return;
    setDetailState({ loading: true });
    try {
      const result = await archiveReport(detail.id);
      applyDetail(result.data);
      setDetailState({ loading: false, success: '报告已归档', traceId: result.trace_id });
    } catch (error: unknown) {
      setDetailState({ loading: false, error: error instanceof Error ? error.message : '归档失败' });
    }
  }

  async function onDiagnoseReport() {
    if (!detail || !canDiagnose) return;
    setDiagnosisState({ loading: true });
    try {
      const result = await diagnoseReport(detail.id);
      setDetail((current) => current ? { ...current, latestDiagnosis: result.data } : current);
      setCompareResult(null);
      setReports((current) => current.map((report) => report.id === detail.id
        ? {
          ...report,
          summary: {
            ...report.summary,
            diagnosisStatus: result.data.status,
            diagnosisPrimaryCategory: stringFrom(result.data.classification.primaryCategory, 'UNKNOWN'),
            diagnosisManualReviewRequired: result.data.manualReviewRequired
          }
        }
        : report));
      setDiagnosisState({ loading: false, success: '失败诊断已更新', traceId: result.trace_id });
    } catch (error: unknown) {
      setDiagnosisState({ loading: false, error: error instanceof Error ? error.message : '诊断失败' });
    }
  }

  async function onCreateDefectDraft() {
    if (!detail || !canGenerate) return;
    setDefectState({ loading: true });
    try {
      const result = await createDefectDraft(detail.id);
      setDetail((current) => current ? {
        ...current,
        defectDrafts: [result.data, ...current.defectDrafts.filter((draft) => draft.id !== result.data.id)],
        summary: {
          ...current.summary,
          defectDraftCount: current.defectDrafts.filter((draft) => draft.id !== result.data.id).length + 1
        }
      } : current);
      setCompareResult(null);
      setReports((current) => current.map((report) => report.id === detail.id
        ? { ...report, summary: { ...report.summary, defectDraftCount: numberFrom(report.summary.defectDraftCount) + 1 } }
        : report));
      setDefectState({ loading: false, success: '缺陷草稿已生成', traceId: result.trace_id });
    } catch (error: unknown) {
      setDefectState({ loading: false, error: error instanceof Error ? error.message : '生成草稿失败' });
    }
  }

  async function onReviewDraft(draft: ReportDefectDraft, status: 'DRAFT' | 'REVIEWED' | 'DISMISSED') {
    if (!detail || !canManage) return;
    setDefectState({ loading: true });
    try {
      const result = await reviewDefectDraft(detail.id, draft.id, status);
      setDetail((current) => current ? {
        ...current,
        defectDrafts: current.defectDrafts.map((item) => item.id === result.data.id ? result.data : item)
      } : current);
      setCompareResult(null);
      setDefectState({ loading: false, success: `草稿状态已更新为 ${result.data.status}`, traceId: result.trace_id });
    } catch (error: unknown) {
      setDefectState({ loading: false, error: error instanceof Error ? error.message : '更新草稿失败' });
    }
  }

  async function onExportReport(exportType: ReportExportType) {
    if (!detail || !canExport) return;
    setExportState({ loading: true });
    try {
      const result = await exportReport(detail.id, exportType);
      setLatestExport(result.data);
      setExportState({
        loading: false,
        success: result.data.downloadReady ? `${exportType} 报告已生成，可下载文件` : `${exportType} 报告已生成`,
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setExportState({ loading: false, error: error instanceof Error ? error.message : '导出失败' });
    }
  }

  async function onDownloadLatestExport() {
    if (!detail || !latestExport?.downloadReady) return;
    setExportState({ loading: true });
    try {
      const response = await downloadReportExport(detail.id, latestExport.id);
      downloadBlob(
        response.blob,
        response.filename || latestExport.downloadFileName || fallbackExportFileName(detail.id, latestExport.exportType),
        response.contentType || latestExport.downloadContentType || 'application/octet-stream'
      );
      setExportState({ loading: false, success: `${latestExport.exportType} 文件已下载`, traceId: response.traceId });
    } catch (error: unknown) {
      setExportState({ loading: false, error: error instanceof Error ? error.message : '下载导出文件失败' });
    }
  }

  async function onCompareReport() {
    if (!detail) return;
    if (!baselineReportId) {
      setCompareState({ loading: false, error: '请选择一个 baseline 报告' });
      return;
    }
    setCompareState({ loading: true });
    try {
      const result = await compareReport(detail.id, baselineReportId);
      setCompareResult(result.data);
      setCompareState({
        loading: false,
        success: result.data.unchanged ? '两次报告没有聚合差异' : `发现 ${result.data.changedFields.length} 项聚合差异`,
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setCompareResult(null);
      setCompareState({ loading: false, error: error instanceof Error ? error.message : '加载报告对比失败' });
    }
  }

  function applyDetail(next: ReportDetail) {
    setDetail(next);
    setCompareResult(null);
    setReports((current) => current.map((report) => report.id === next.id ? summaryFromDetail(next) : report));
  }

  return (
    <div className="reports-workbench" data-testid="reports-workbench">
      <section className="metrics-grid reports-metrics">
        <Metric icon={<CheckCircle2 size={20} />} label="READY 报告" value={String(summary.ready)} desc={health?.schemaVersion || '等待加载'} tone="success" />
        <Metric icon={<RefreshCw size={20} />} label="生成中" value={String(summary.generating)} desc={health?.generateEnabled ? '生成开关 ON' : '生成开关 OFF'} tone="info" />
        <Metric icon={<AlertTriangle size={20} />} label="失败报告" value={String(summary.failed)} desc={health?.diagnosisEnabled ? '诊断开关 ON' : '诊断开关 OFF'} tone="warning" />
        <Metric icon={<Bug size={20} />} label="缺陷草稿" value={String(summary.drafts)} desc={health?.defectDraftEnabled ? '草稿开关 ON' : '草稿开关 OFF'} tone="danger" />
      </section>

      <div className="reports-layout">
        <section className="reports-list-column">
          <Panel
            title="报告筛选"
            desc={health ? `${health.service} · ${health.status} · fieldSet ${health.fieldSetVersion}` : '加载报告健康摘要'}
            action={(
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void refreshReports()} disabled={loadState.loading}>
                <RefreshCw size={15} />刷新
              </button>
            )}
          >
            <form className="report-filter-grid" onSubmit={(event) => { event.preventDefault(); void refreshReports(); }}>
              <Field label="projectId">
                <input value={filters.projectId} onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project-alpha" />
              </Field>
              <Field label="executionRunId">
                <input value={filters.executionRunId} onChange={(event) => setFilters((current) => ({ ...current, executionRunId: event.target.value }))} placeholder="UUID" />
              </Field>
              <Field label="status">
                <select value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}>
                  <option value="">全部</option>
                  <option value="READY">READY</option>
                  <option value="FAILED">FAILED</option>
                  <option value="QUEUED">QUEUED</option>
                  <option value="GENERATING">GENERATING</option>
                  <option value="ARCHIVED">ARCHIVED</option>
                </select>
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />筛选
                </button>
              </div>
            </form>
            <StateLine state={loadState} />
          </Panel>

          <Panel title="生成报告" desc="基于 WP9 execution run 创建 aggregate-only 报告快照。" testId="report-generate-panel">
            <form className="report-generate-form" onSubmit={onGenerateReport}>
              <div className="form-grid">
                <Field label="projectId">
                  <input value={generateDraft.projectId} onChange={(event) => setGenerateDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canGenerate || generateState.loading} />
                </Field>
                <Field label="executionRunId">
                  <input value={generateDraft.executionRunId} onChange={(event) => setGenerateDraftValue('executionRunId', event.target.value)} placeholder="UUID" disabled={!canGenerate || generateState.loading} />
                </Field>
                <Field label="requestKey">
                  <input value={generateDraft.requestKey} onChange={(event) => setGenerateDraftValue('requestKey', event.target.value)} placeholder="可选幂等键" disabled={!canGenerate || generateState.loading} />
                </Field>
                <Field label="reason">
                  <input value={generateDraft.reason} onChange={(event) => setGenerateDraftValue('reason', event.target.value)} placeholder="可选生成原因" disabled={!canGenerate || generateState.loading} />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canGenerate || generateState.loading}>
                  <FileText size={16} />生成报告
                </button>
                {detail?.status === 'FAILED' && (
                  <button className="btn btn-secondary" type="button" onClick={() => void onRetryReport()} disabled={!canGenerate || generateState.loading}>
                    <RefreshCw size={16} />重试生成
                  </button>
                )}
              </div>
              <StateLine state={generateState} />
            </form>
          </Panel>

          <Panel title="报告列表" desc={`当前页 ${reports.length} 条`} testId="reports-list">
            <div className="reports-list">
              {loadState.loading && reports.length === 0 ? (
                <ListSkeleton />
              ) : reports.length ? (
                reports.map((report) => (
                  <button
                    key={report.id}
                    type="button"
                    className={`report-list-item${selectedReportId === report.id ? ' active' : ''}`}
                    onClick={() => setSelectedReportId(report.id)}
                  >
                    <span className={`badge badge-${statusTone(report.status)}`}>{report.status}</span>
                    <strong>{shortId(report.executionRunId)}</strong>
                    <span>{stringFrom(report.summary.runStatus, '-')} · {stringFrom(report.summary.diagnosisPrimaryCategory, 'UNKNOWN')}</span>
                    <small>{report.generatedAt ? formatDateTime(report.generatedAt) : report.createdAt ? formatDateTime(report.createdAt) : report.id}</small>
                  </button>
                ))
              ) : (
                <div className="empty-state">
                  <FileText className="empty-state-icon" size={30} />
                  <strong>暂无报告</strong>
                  <span>输入 executionRunId 后生成第一份 WP10 报告快照。</span>
                </div>
              )}
            </div>
          </Panel>
        </section>

        <section className="reports-detail-column">
          {!detail ? (
            <Panel title="报告详情" desc="请选择或生成报告。" testId="report-detail">
              <div className="empty-state">
                <FileText className="empty-state-icon" size={30} />
                <strong>{detailState.loading ? '加载详情中' : '未选择报告'}</strong>
                <span>列表中选择报告后会展示 run summary、证据 manifest、诊断、草稿和导出。</span>
              </div>
            </Panel>
          ) : (
            <>
              <ReportDetailPanel
                detail={detail}
                state={detailState}
                canManage={canManage}
                onArchive={() => void onArchiveReport()}
              />
              <ComparePanel
                detail={detail}
                reports={baselineCandidates}
                baselineReportId={baselineReportId}
                compareResult={compareResult}
                state={compareState}
                onBaselineChange={(value) => {
                  setBaselineReportId(value);
                  setCompareState({ loading: false });
                }}
                onCompare={() => void onCompareReport()}
              />
              <DiagnosisPanel
                detail={detail}
                state={diagnosisState}
                canDiagnose={canDiagnose}
                diagnosis={detail.latestDiagnosis}
                onDiagnose={() => void onDiagnoseReport()}
              />
              <DefectDraftPanel
                detail={detail}
                state={defectState}
                canGenerate={canGenerate}
                canManage={canManage}
                onCreateDraft={() => void onCreateDefectDraft()}
                onReviewDraft={(draft, status) => void onReviewDraft(draft, status)}
              />
              <ExportPanel
                detail={detail}
                latestExport={latestExport}
                state={exportState}
                canExport={canExport}
                onExport={(exportType) => void onExportReport(exportType)}
                onDownloadExport={() => void onDownloadLatestExport()}
              />
            </>
          )}
        </section>
      </div>
    </div>
  );

  function setGenerateDraftValue(key: keyof GenerateDraft, value: string) {
    setGenerateDraft((current) => ({ ...current, [key]: value }));
    setGenerateState({ loading: false });
  }
}

function ReportDetailPanel(props: {
  detail: ReportDetail;
  state: WorkState;
  canManage: boolean;
  onArchive: () => void;
}) {
  const summary = props.detail.summary;
  return (
    <Panel
      title="报告详情"
      desc={`${props.detail.schemaVersion} · ${props.detail.projectId}`}
      testId="report-detail"
      action={props.detail.status !== 'ARCHIVED' ? (
        <button className="btn btn-secondary btn-sm" type="button" onClick={props.onArchive} disabled={!props.canManage || props.state.loading}>
          <Archive size={15} />归档
        </button>
      ) : <span className="badge badge-neutral">只读</span>}
    >
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`}>{props.detail.status}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="runStatus" value={stringFrom(summary.runStatus, props.detail.status)} />
        <SummaryTile label="triggerType" value={stringFrom(summary.triggerType, '-')} />
        <SummaryTile label="nodeCount" value={stringFrom(summary.nodeCount, '0')} />
        <SummaryTile label="evidence" value={stringFrom(summary.evidenceManifestCount, String(props.detail.evidenceManifests.length))} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title="节点状态" value={formatRecord(summary.nodeStatusCounts)} />
        <InfoBlock title="失败桶" value={formatRecord(summary.failureBucketCounts)} />
        <InfoBlock title="sourceRunDigest" value={props.detail.sourceRunDigest || '-'} />
        <InfoBlock title="traceId" value={props.detail.traceId || props.state.traceId || '-'} />
      </div>
      <EvidenceList manifests={props.detail.evidenceManifests} />
      <PolicySummary policy={props.detail.redactionPolicy} />
      <StateLine state={props.state} />
    </Panel>
  );
}

function DiagnosisPanel(props: {
  detail: ReportDetail;
  diagnosis?: ReportDiagnosis;
  state: WorkState;
  canDiagnose: boolean;
  onDiagnose: () => void;
}) {
  const classification = props.diagnosis?.classification ?? {};
  const candidates = props.diagnosis?.rootCauseCandidates ?? [];
  const disabled = !props.canDiagnose || props.state.loading || props.detail.status !== 'READY';
  return (
    <Panel
      title="失败诊断"
      desc="AI 结果只作为建议，最终判断需要人工确认。"
      testId="report-diagnosis"
      action={(
        <button className="btn btn-primary btn-sm" type="button" onClick={props.onDiagnose} disabled={disabled}>
          <Sparkles size={15} />触发诊断
        </button>
      )}
    >
      {props.diagnosis ? (
        <div className="report-diagnosis-grid">
          <SummaryTile label="status" value={props.diagnosis.status} tone={statusTone(props.diagnosis.status)} />
          <SummaryTile label="primaryCategory" value={stringFrom(classification.primaryCategory, 'UNKNOWN')} />
          <SummaryTile label="confidence" value={formatPercent(props.diagnosis.confidence)} />
          <SummaryTile label="manualReview" value={props.diagnosis.manualReviewRequired ? 'required' : 'optional'} tone="warning" />
        </div>
      ) : (
        <div className="notice info">尚无诊断快照，READY 报告可触发诊断。</div>
      )}
      {props.diagnosis?.errorCode && (
        <div className="notice warning">诊断降级：{props.diagnosis.errorCode}</div>
      )}
      {candidates.length > 0 && (
        <div className="report-card-list">
          {candidates.slice(0, 4).map((candidate, index) => (
            <div className="report-mini-card" key={index}>
              <strong>{stringFrom(recordValue(candidate, 'category'), `候选 ${index + 1}`)}</strong>
              <span>{stringFrom(recordValue(candidate, 'summary'), '-')}</span>
              <small>{formatRecord(recordValue(candidate, 'nextActions'))}</small>
            </div>
          ))}
        </div>
      )}
      <PolicySummary policy={props.diagnosis?.redactionPolicy ?? { rawPromptStored: false, rawResponseStored: false, aggregateOnly: true }} />
      <StateLine state={props.state} />
    </Panel>
  );
}

function ComparePanel(props: {
  detail: ReportDetail;
  reports: ReportSummary[];
  baselineReportId: string;
  compareResult: ReportCompare | null;
  state: WorkState;
  onBaselineChange: (value: string) => void;
  onCompare: () => void;
}) {
  return (
    <Panel
      title="报告对比"
      desc="选择同项目历史快照，对比两次运行的聚合差异。"
      testId="report-compare-panel"
      action={(
        <div className="report-actions-row compact">
          <button
            className="btn btn-secondary btn-sm"
            type="button"
            onClick={props.onCompare}
            disabled={props.state.loading || !props.baselineReportId}
          >
            <RefreshCw size={15} />开始对比
          </button>
        </div>
      )}
    >
      <div className="report-compare-controls">
        <Field label="baseline report">
          <select
            value={props.baselineReportId}
            onChange={(event) => props.onBaselineChange(event.target.value)}
          >
            <option value="">请选择历史报告</option>
            {props.reports.map((report) => (
              <option key={report.id} value={report.id}>
                {report.status} · {shortId(report.executionRunId)} · {report.generatedAt ? formatDateTime(report.generatedAt) : shortId(report.id)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      {props.reports.length === 0 && (
        <div className="notice info">当前项目还没有其他可作为 baseline 的报告。</div>
      )}
      {props.compareResult ? (
        <>
          <div className="report-summary-grid">
            <SummaryTile label="projectId" value={props.compareResult.projectId} />
            <SummaryTile label="changedFields" value={String(props.compareResult.changedFields.length)} tone={props.compareResult.unchanged ? 'success' : 'warning'} />
            <SummaryTile label="baseline" value={shortId(props.compareResult.baselineReportId)} />
            <SummaryTile label="current" value={shortId(props.compareResult.reportId)} />
          </div>
          {props.compareResult.unchanged ? (
            <div className="notice success">当前报告与所选 baseline 在聚合摘要上没有差异。</div>
          ) : (
            <>
              <CompareDiffList title="元数据差异" diffs={props.compareResult.metadataDiffs} />
              <CompareDiffList title="摘要差异" diffs={props.compareResult.summaryDiffs} />
              <CompareDiffList title="诊断差异" diffs={props.compareResult.diagnosisDiffs} />
              <div className="report-section-grid">
                <InfoBlock
                  title="evidence count"
                  value={`${props.compareResult.evidenceDiff.baselineCount} -> ${props.compareResult.evidenceDiff.currentCount}`}
                />
                <InfoBlock
                  title="draft count"
                  value={`${props.compareResult.defectDraftDiff.baselineCount} -> ${props.compareResult.defectDraftDiff.currentCount}`}
                />
                <InfoBlock
                  title="added manifests"
                  value={props.compareResult.evidenceDiff.addedManifestKeys.join(', ') || '-'}
                />
                <InfoBlock
                  title="removed manifests"
                  value={props.compareResult.evidenceDiff.removedManifestKeys.join(', ') || '-'}
                />
              </div>
              <div className="report-section-grid">
                <InfoBlock
                  title="sourceWp"
                  value={`${formatRecord(props.compareResult.evidenceDiff.baselineSourceWpCounts)} -> ${formatRecord(props.compareResult.evidenceDiff.currentSourceWpCounts)}`}
                />
                <InfoBlock
                  title="sourceType"
                  value={`${formatRecord(props.compareResult.evidenceDiff.baselineSourceTypeCounts)} -> ${formatRecord(props.compareResult.evidenceDiff.currentSourceTypeCounts)}`}
                />
                <InfoBlock
                  title="draft statuses"
                  value={`${formatRecord(props.compareResult.defectDraftDiff.baselineStatusCounts)} -> ${formatRecord(props.compareResult.defectDraftDiff.currentStatusCounts)}`}
                />
                <InfoBlock
                  title="changed fields"
                  value={props.compareResult.changedFields.join(', ') || '-'}
                />
              </div>
            </>
          )}
        </>
      ) : (
        <div className="notice info">选择 baseline 后可查看 run summary、诊断、evidence 与草稿的聚合差异。</div>
      )}
      <StateLine state={props.state} />
    </Panel>
  );
}

function DefectDraftPanel(props: {
  detail: ReportDetail;
  state: WorkState;
  canGenerate: boolean;
  canManage: boolean;
  onCreateDraft: () => void;
  onReviewDraft: (draft: ReportDefectDraft, status: 'DRAFT' | 'REVIEWED' | 'DISMISSED') => void;
}) {
  return (
    <Panel
      title="缺陷草稿"
      desc="平台内草稿，不会自动写入外部缺陷系统。"
      testId="report-defect-drafts"
      action={(
        <button className="btn btn-primary btn-sm" type="button" onClick={props.onCreateDraft} disabled={!props.canGenerate || props.state.loading || props.detail.status !== 'READY'}>
          <Bug size={15} />生成缺陷草稿
        </button>
      )}
    >
      {props.detail.defectDrafts.length ? (
        <div className="report-card-list">
          {props.detail.defectDrafts.map((draft) => (
            <div className="report-mini-card" key={draft.id}>
              <div className="report-card-heading">
                <strong>{draft.title || '未命名草稿'}</strong>
                <span className={`badge badge-${statusTone(draft.status)}`}>{draft.status}</span>
              </div>
              <span>{draft.reproductionSummary || '-'}</span>
              <small>{draft.impactSummary || '-'}</small>
              <div className="report-section-grid">
                <InfoBlock title="priority" value={draft.prioritySuggestion || '-'} />
                <InfoBlock title="evidenceRefs" value={draft.evidenceRefs.join(', ') || '-'} />
                <InfoBlock title="payloadPreview" value={formatRecord(safePreview(draft.payloadPreview))} />
              </div>
              <div className="report-actions-row">
                <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canManage || props.state.loading || draft.status === 'REVIEWED'} onClick={() => props.onReviewDraft(draft, 'REVIEWED')}>
                  <CheckCircle2 size={15} />审阅草稿
                </button>
                <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canManage || props.state.loading || draft.status === 'DISMISSED'} onClick={() => props.onReviewDraft(draft, 'DISMISSED')}>
                  <Archive size={15} />驳回草稿
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="notice info">暂无缺陷草稿。生成后可标记 REVIEWED 或 DISMISSED。</div>
      )}
      <StateLine state={props.state} />
    </Panel>
  );
}

function CompareDiffList(props: { title: string; diffs: Array<{ field: string; baselineValue: unknown; currentValue: unknown }> }) {
  if (!props.diffs.length) {
    return null;
  }
  return (
    <div className="report-card-list">
      <div className="report-mini-card report-mini-card-muted">
        <strong>{props.title}</strong>
        <div className="report-compare-diff-list">
          {props.diffs.map((diff) => (
            <div className="report-compare-diff-item" key={`${props.title}-${diff.field}`}>
              <strong>{diff.field}</strong>
              <span>{formatRecord(diff.baselineValue)} {'->'} {formatRecord(diff.currentValue)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function ExportPanel(props: {
  detail: ReportDetail;
  latestExport: ReportExport | null;
  state: WorkState;
  canExport: boolean;
  onExport: (exportType: ReportExportType) => void;
  onDownloadExport: () => void;
}) {
  const domClean = props.latestExport ? domSensitiveScan(props.latestExport) : true;
  return (
    <Panel
      title="导出报告"
      desc="导出 aggregate-only 脱敏报告，支持 JSON、Markdown、PDF 和 Word。"
      testId="report-export-panel"
      action={(
        <div className="report-actions-row compact">
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('JSON')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileJson size={15} />导出 JSON
          </button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('MARKDOWN')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileText size={15} />导出 Markdown
          </button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('PDF')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <Download size={15} />导出 PDF
          </button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('WORD')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileText size={15} />导出 Word
          </button>
          {props.latestExport?.downloadReady ? (
            <button className="btn btn-secondary btn-sm" type="button" onClick={props.onDownloadExport} disabled={!props.canExport || props.state.loading}>
              <Download size={15} />下载文件
            </button>
          ) : null}
        </div>
      )}
    >
      {props.latestExport ? (
        <div className="report-section-grid">
          <InfoBlock title="status" value={props.latestExport.status} />
          <InfoBlock title="fieldSetVersion" value={props.latestExport.fieldSetVersion || '-'} />
          <InfoBlock title="contentDigest" value={props.latestExport.contentDigest || '-'} />
          <InfoBlock title="aggregateOnly" value={props.latestExport.aggregateOnly ? 'true' : 'false'} />
          <InfoBlock title="downloadReady" value={props.latestExport.downloadReady ? 'true' : 'false'} />
          <InfoBlock title="fileName" value={props.latestExport.downloadFileName || '-'} />
          <InfoBlock title="manifest" value={formatRecord(props.latestExport.manifest)} />
          <InfoBlock title="DOM scan" value={domClean ? 'clean' : 'blocked term detected'} />
        </div>
      ) : (
        <div className="notice info">选择导出格式后展示 manifest、digest 和脱敏策略。</div>
      )}
      <PolicySummary policy={props.latestExport?.redactionPolicy ?? { aggregateOnly: true }} />
      <StateLine state={props.state} />
    </Panel>
  );
}

function EvidenceList(props: { manifests: ReportDetail['evidenceManifests'] }) {
  if (!props.manifests.length) {
    return <div className="notice info">暂无 evidence manifest。</div>;
  }
  return (
    <div className="report-card-list">
      {props.manifests.map((manifest) => (
        <div className="report-mini-card" key={manifest.id || `${manifest.sourceWp}-${manifest.manifestDigest}`}>
          <div className="report-card-heading">
            <strong>{manifest.sourceWp || 'UNKNOWN'} · {manifest.sourceType || '-'}</strong>
            <span className="badge badge-neutral">{manifest.schemaVersion || '-'}</span>
          </div>
          <span>{manifest.summaryKeys.join(', ') || '-'}</span>
          <small>{manifest.manifestDigest || manifest.sourceRefDigest || '-'}</small>
        </div>
      ))}
    </div>
  );
}

function PolicySummary(props: { policy: Record<string, unknown> }) {
  const entries = Object.entries(props.policy).slice(0, 8);
  if (!entries.length) return null;
  return (
    <div className="report-policy-list">
      <div className="report-policy-title"><ShieldCheck size={15} />脱敏策略</div>
      {entries.map(([key, value]) => (
        <span key={key}>{key}={formatRecord(value)}</span>
      ))}
    </div>
  );
}

function Panel(props: { title: string; desc?: string; action?: ReactNode; children: ReactNode; testId?: string }) {
  return (
    <section className="panel" data-testid={props.testId}>
      <div className="panel-header">
        <div>
          <h2 className="panel-title">{props.title}</h2>
          {props.desc && <p className="panel-desc">{props.desc}</p>}
        </div>
        {props.action && <div className="toolbar-actions">{props.action}</div>}
      </div>
      <div className="panel-body">
        {props.children}
      </div>
    </section>
  );
}

function Metric(props: { icon: ReactNode; label: string; value: string; desc: string; tone: 'success' | 'info' | 'warning' | 'danger' }) {
  return (
    <div className="metric-card">
      <div className={`metric-icon ${props.tone}`}>{props.icon}</div>
      <div className="metric-body">
        <span className="metric-label">{props.label}</span>
        <strong className="metric-value">{props.value}</strong>
        <span className="metric-desc">{props.desc}</span>
      </div>
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

function SummaryTile(props: { label: string; value: string; tone?: string }) {
  return (
    <div className="report-summary-tile">
      <span>{props.label}</span>
      <strong className={props.tone ? `tone-${props.tone}` : undefined}>{props.value}</strong>
    </div>
  );
}

function InfoBlock(props: { title: string; value: string }) {
  return (
    <div className="report-info-block">
      <span>{props.title}</span>
      <strong>{props.value}</strong>
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">处理中...</span>;
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

function ListSkeleton() {
  return (
    <div className="reports-list">
      {[0, 1, 2].map((item) => (
        <div className="report-list-item skeleton-report-item" key={item}>
          <div className="skeleton skeleton-title" />
          <div className="skeleton skeleton-text" />
          <div className="skeleton skeleton-text" />
        </div>
      ))}
    </div>
  );
}

function validateGenerateDraft(draft: GenerateDraft) {
  const issues: string[] = [];
  if (!draft.projectId.trim()) issues.push('请填写 projectId');
  if (!uuidPattern.test(draft.executionRunId.trim())) issues.push('executionRunId 需要是 UUID');
  if (draft.requestKey.trim() && !requestKeyPattern.test(draft.requestKey.trim())) {
    issues.push('requestKey 只能包含字母、数字、-、_、.、:，且不超过 128 字符');
  }
  if (draft.reason.length > 200) issues.push('reason 最多 200 字符');
  if (hasSensitiveText(draft.reason)) issues.push('reason 不能包含明显敏感字段');
  return issues;
}

function summaryFromDetail(detail: ReportDetail): ReportSummary {
  return {
    id: detail.id,
    projectId: detail.projectId,
    executionRunId: detail.executionRunId,
    requestKey: detail.requestKey,
    status: detail.status,
    schemaVersion: detail.schemaVersion,
    sourceRunDigest: detail.sourceRunDigest,
    summary: detail.summary,
    idempotentReplay: detail.idempotentReplay,
    generatedBy: detail.generatedBy,
    generatedAt: detail.generatedAt,
    failedCode: detail.failedCode,
    failureSummary: detail.failureSummary,
    traceId: detail.traceId,
    archivedAt: detail.archivedAt,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt
  };
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function statusTone(status: string) {
  if (['READY', 'AI_READY', 'REVIEWED', 'CREATED', 'SUCCEEDED'].includes(status)) return 'success';
  if (['QUEUED', 'GENERATING', 'AI_RUNNING', 'DRAFT'].includes(status)) return 'info';
  if (['FAILED', 'AI_FAILED', 'DISMISSED', 'BLOCKED'].includes(status)) return 'danger';
  if (['ARCHIVED'].includes(status)) return 'neutral';
  return 'warning';
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function shortId(value?: string) {
  if (!value) return '-';
  return value.length > 14 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

function stringFrom(value: unknown, fallback = '') {
  if (value == null || value === '') return fallback;
  return typeof value === 'string' ? value : String(value);
}

function numberFrom(value: unknown) {
  const numberValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numberValue) ? numberValue : 0;
}

function recordValue(input: unknown, key: string) {
  return input && typeof input === 'object' && !Array.isArray(input)
    ? (input as Record<string, unknown>)[key]
    : undefined;
}

function formatRecord(input: unknown): string {
  if (input == null || input === '') return '-';
  if (Array.isArray(input)) return input.map((item) => formatRecord(item)).join(', ');
  if (typeof input === 'object') {
    const entries = Object.entries(input as Record<string, unknown>);
    if (!entries.length) return '-';
    return entries.slice(0, 8).map(([key, value]) => `${key}:${formatRecord(value)}`).join(' · ');
  }
  return String(input);
}

function safePreview(input: Record<string, unknown>) {
  const result: Record<string, unknown> = {};
  for (const key of ['schemaVersion', 'externalSystem', 'fieldMappingVersion', 'masked', 'aggregateOnly', 'externalSystemWriteAttempted']) {
    if (key in input) result[key] = input[key];
  }
  return result;
}

function formatPercent(value: number) {
  if (!Number.isFinite(value)) return '-';
  return `${Math.round(value * 100)}%`;
}

function hasSensitiveText(value: string) {
  const normalized = value.toLowerCase();
  return blockedDomTerms.some((term) => normalized.includes(term));
}

function domSensitiveScan(value: unknown) {
  return !hasSensitiveText(JSON.stringify(value).toLowerCase());
}

function downloadBlob(blob: Blob, filename: string, contentType: string) {
  const normalizedBlob = blob.type ? blob : new Blob([blob], { type: contentType || 'application/octet-stream' });
  const objectUrl = URL.createObjectURL(normalizedBlob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

function fallbackExportFileName(reportId: string, exportType: string) {
  const suffix = exportType === 'MARKDOWN'
    ? 'md'
    : exportType === 'PDF'
      ? 'pdf'
      : exportType === 'WORD'
        ? 'docx'
        : 'json';
  return `report-${reportId}.${suffix}`;
}
