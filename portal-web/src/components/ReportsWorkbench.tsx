import {
  AlertTriangle,
  Archive,
  Bug,
  CheckCircle2,
  Download,
  FileCode,
  FileJson,
  FileSpreadsheet,
  FileText,
  Package,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
import { Drawer } from 'antd';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  archiveReport,
  batchExportReports,
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
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

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
  const [selectedReportIds, setSelectedReportIds] = useState<string[]>([]);
  const [generateDraft, setGenerateDraft] = useState<GenerateDraft>(initialGenerateDraft);
  const [generateDrawerOpen, setGenerateDrawerOpen] = useState(false);
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

  const selectedReports = useMemo(
    () => reports.filter((report) => selectedReportIds.includes(report.id)),
    [reports, selectedReportIds]
  );

  const refreshReports = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setReports([]);
      setSelectedReportId('');
      setDetail(null);
      setLatestExport(null);
      setCompareResult(null);
      setBaselineReportId('');
      setSelectedReportIds([]);
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
      setSelectedReportIds((current) => current.filter((id) => reportResult.data.items.some((report) => report.id === id)));
      setLoadState({ loading: false, traceId: reportResult.trace_id });
    } catch (error: unknown) {
      setLoadState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1091') });
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
      setDetailState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1092') });
    }
  }, [canRead]);

  useEffect(() => {
    void refreshReports();
  }, [refreshReports]);

  useEffect(() => {
    void refreshDetail(selectedReportId);
  }, [refreshDetail, selectedReportId]);

  if (!props.signedIn) {
    return <div className="notice warning">{translate('auto.k1093')}</div>;
  }

  if (!canRead) {
    return <div className="notice error">{translate('auto.k1094')}</div>;
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
      setSelectedReportIds((current) => current.includes(result.data.id) ? current : [result.data.id, ...current]);
      setReports((current) => [summaryFromDetail(result.data), ...current.filter((report) => report.id !== result.data.id)]);
      setGenerateState({
        loading: false,
        success: result.data.idempotentReplay ? translate('auto.k1095') : translate('auto.k1096'),
        traceId: result.trace_id
      });
      setGenerateDrawerOpen(false);
      setGenerateDraft((current) => ({ ...current, requestKey: '', reason: '' }));
    } catch (error: unknown) {
      setGenerateState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1097') });
    }
  }

  async function onRetryReport() {
    if (!detail || !canGenerate) return;
    setGenerateState({ loading: true });
    try {
      const result = await retryReport(detail.id);
      applyDetail(result.data);
      setGenerateState({ loading: false, success: translate('auto.k1098'), traceId: result.trace_id });
    } catch (error: unknown) {
      setGenerateState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0841') });
    }
  }

  async function onArchiveReport() {
    if (!detail || !canManage) return;
    setDetailState({ loading: true });
    try {
      const result = await archiveReport(detail.id);
      applyDetail(result.data);
      setDetailState({ loading: false, success: translate('auto.k1099'), traceId: result.trace_id });
    } catch (error: unknown) {
      setDetailState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1100') });
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
      setDiagnosisState({ loading: false, success: translate('auto.k1101'), traceId: result.trace_id });
    } catch (error: unknown) {
      setDiagnosisState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1102') });
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
      setDefectState({ loading: false, success: translate('auto.k1103'), traceId: result.trace_id });
    } catch (error: unknown) {
      setDefectState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1104') });
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
      setDefectState({ loading: false, success: translate('auto.k1105', { value0: result.data.status }), traceId: result.trace_id });
    } catch (error: unknown) {
      setDefectState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1106') });
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
        success: result.data.downloadReady ? translate('auto.k1107', { value0: exportType }) : translate('auto.k1108', { value0: exportType }),
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setExportState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k0062') });
    }
  }

  async function onBatchExport(exportType: ReportExportType) {
    if (!canExport || !selectedReportIds.length) {
      return;
    }
    setExportState({ loading: true });
    try {
      const response = await batchExportReports({
        reportIds: selectedReportIds,
        exportType
      });
      const fallbackProject = selectedReports[0]?.projectId || 'reports';
      downloadBlob(
        response.blob,
        response.filename || fallbackBatchExportFileName(fallbackProject, exportType),
        response.contentType || 'application/zip'
      );
      setExportState({
        loading: false,
        success: translate('auto.k1109', { value0: selectedReportIds.length, value1: exportType }),
        traceId: response.traceId
      });
    } catch (error: unknown) {
      setExportState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1110') });
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
      setExportState({ loading: false, success: translate('auto.k1111', { value0: latestExport.exportType }), traceId: response.traceId });
    } catch (error: unknown) {
      setExportState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1112') });
    }
  }

  async function onCompareReport() {
    if (!detail) return;
    if (!baselineReportId) {
      setCompareState({ loading: false, error: translate('auto.k1113') });
      return;
    }
    setCompareState({ loading: true });
    try {
      const result = await compareReport(detail.id, baselineReportId);
      setCompareResult(result.data);
      setCompareState({
        loading: false,
        success: result.data.unchanged ? translate('auto.k1114') : translate('auto.k1115', { value0: result.data.changedFields.length }),
        traceId: result.trace_id
      });
    } catch (error: unknown) {
      setCompareResult(null);
      setCompareState({ loading: false, error: error instanceof Error ? error.message : translate('auto.k1116') });
    }
  }

  function applyDetail(next: ReportDetail) {
    setDetail(next);
    setCompareResult(null);
    setReports((current) => current.map((report) => report.id === next.id ? summaryFromDetail(next) : report));
  }

  function toggleReportSelection(reportId: string) {
    setSelectedReportIds((current) => current.includes(reportId)
      ? current.filter((id) => id !== reportId)
      : [...current, reportId]);
  }

  function toggleReadySelection(checked: boolean) {
    const readyIds = reports.filter((report) => report.status === 'READY').map((report) => report.id);
    setSelectedReportIds(checked ? readyIds : []);
  }

  const readyReportCount = reports.filter((report) => report.status === 'READY').length;
  const selectedReadyCount = selectedReports.filter((report) => report.status === 'READY').length;
  const readySelectionFull = readyReportCount > 0 && selectedReadyCount === readyReportCount;

  return (
    <div className="reports-workbench" data-testid="reports-workbench">
      <section className="metrics-grid reports-metrics">
        <Metric icon={<CheckCircle2 size={20} />} label={translate('auto.k1117')} value={String(summary.ready)} desc={health?.schemaVersion || translate('auto.k1118')} tone="success" />
        <Metric icon={<RefreshCw size={20} />} label={translate('auto.k1119')} value={String(summary.generating)} desc={health?.generateEnabled ? translate('auto.k1120') : translate('auto.k1121')} tone="info" />
        <Metric icon={<AlertTriangle size={20} />} label={translate('auto.k1122')} value={String(summary.failed)} desc={health?.diagnosisEnabled ? translate('auto.k1123') : translate('auto.k1124')} tone="warning" />
        <Metric icon={<Bug size={20} />} label={translate('auto.k1125')} value={String(summary.drafts)} desc={health?.defectDraftEnabled ? translate('auto.k1126') : translate('auto.k1127')} tone="danger" />
      </section>

      <div className="reports-layout">
        <section className="reports-list-column">
          <Panel
            title={translate('auto.k1128')}
            desc={health ? `${health.service} · ${health.status} · fieldSet ${health.fieldSetVersion}` : translate('auto.k1129')}
            action={(
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void refreshReports()} disabled={loadState.loading}>
                <RefreshCw size={15} />{translate('auto.k0170')}</button>
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
                  <option value="">{translate('auto.k0195')}</option>
                  <option value="READY">{dictionaryLabel('READY')}</option>
                  <option value="FAILED">{dictionaryLabel('FAILED')}</option>
                  <option value="QUEUED">{dictionaryLabel('QUEUED')}</option>
                  <option value="GENERATING">{dictionaryLabel('GENERATING')}</option>
                  <option value="ARCHIVED">{dictionaryLabel('ARCHIVED')}</option>
                </select>
              </Field>
              <div className="report-filter-actions">
                <button className="btn btn-secondary" type="submit" disabled={loadState.loading}>
                  <Search size={16} />{translate('auto.k1130')}</button>
              </div>
            </form>
            <StateLine state={loadState} />
          </Panel>

          <Panel
            title={translate('auto.k1131')}
            desc={translate('auto.k1132')}
            testId="report-generate-panel"
            action={(
              <button className="btn btn-primary btn-sm" type="button" disabled={!canGenerate || generateState.loading} onClick={() => setGenerateDrawerOpen(true)}>
                <FileText size={15} />{translate('auto.k1131')}
              </button>
            )}
          >
            <StateLine state={generateState} />
            {detail?.status === 'FAILED' && (
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onRetryReport()} disabled={!canGenerate || generateState.loading}>
                <RefreshCw size={15} />{translate('auto.k1135')}</button>
            )}
            <Drawer
              className="report-generate-drawer"
              destroyOnHidden
              maskClosable={!generateState.loading}
              open={generateDrawerOpen}
              placement="right"
              title={translate('auto.k1131')}
              width={640}
              onClose={() => {
                if (!generateState.loading) {
                  setGenerateDrawerOpen(false);
                }
              }}
            >
            <form className="report-generate-form document-drawer-form" onSubmit={onGenerateReport}>
              <div className="form-grid">
                <Field label="projectId">
                  <input value={generateDraft.projectId} onChange={(event) => setGenerateDraftValue('projectId', event.target.value)} placeholder="project-alpha" disabled={!canGenerate || generateState.loading} />
                </Field>
                <Field label="executionRunId">
                  <input value={generateDraft.executionRunId} onChange={(event) => setGenerateDraftValue('executionRunId', event.target.value)} placeholder="UUID" disabled={!canGenerate || generateState.loading} />
                </Field>
                <Field label="requestKey">
                  <input value={generateDraft.requestKey} onChange={(event) => setGenerateDraftValue('requestKey', event.target.value)} placeholder={translate('auto.k1133')} disabled={!canGenerate || generateState.loading} />
                </Field>
                <Field label="reason">
                  <input value={generateDraft.reason} onChange={(event) => setGenerateDraftValue('reason', event.target.value)} placeholder={translate('auto.k1134')} disabled={!canGenerate || generateState.loading} />
                </Field>
              </div>
              <div className="report-actions-row">
                <button className="btn btn-primary" type="submit" disabled={!canGenerate || generateState.loading}>
                  <FileText size={16} />{translate('auto.k1131')}</button>
                <button className="btn btn-secondary" type="button" disabled={generateState.loading} onClick={() => setGenerateDrawerOpen(false)}>
                  {translate('actions.cancel')}</button>
              </div>
              <StateLine state={generateState} />
            </form>
            </Drawer>
          </Panel>

          <Panel
            title={translate('auto.k1136')}
            desc={translate('auto.k1137', { value0: reports.length })}
            testId="reports-list"
            action={reports.length ? (
              <label className="field field-inline report-list-toggle">
                <span className="field-inline-main">
                  <input
                    type="checkbox"
                    checked={readySelectionFull}
                    onChange={(event) => toggleReadySelection(event.target.checked)}
                    disabled={loadState.loading || readyReportCount === 0}
                  />
                  <span>{translate('auto.k1138')}</span>
                </span>
                <small>{translate('auto.k1139')}</small>
              </label>
            ) : undefined}
          >
            {selectedReportIds.length ? (
              <div className="report-selection-toolbar">
                <div className="report-selection-summary">
                  <strong>{translate('auto.k0799')}{selectedReportIds.length} {translate('auto.k0181')}</strong>
                  <span>{selectedReadyCount === selectedReportIds.length ? translate('auto.k1140') : translate('auto.k1141')}</span>
                </div>
                <div className="report-actions-row compact">
                  <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onBatchExport('HTML')} disabled={!canExport || exportState.loading}>
                    <FileCode size={15} />{translate('auto.k1142')}</button>
                  <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onBatchExport('EXCEL')} disabled={!canExport || exportState.loading}>
                    <FileSpreadsheet size={15} />{translate('auto.k1143')}</button>
                  <button className="btn btn-secondary btn-sm" type="button" onClick={() => void setSelectedReportIds([])} disabled={exportState.loading}>
                    <Archive size={15} />{translate('auto.k1144')}</button>
                </div>
              </div>
            ) : null}
            <div className="reports-list">
              {loadState.loading && reports.length === 0 ? (
                <ListSkeleton />
              ) : reports.length ? (
                reports.map((report) => (
                  <div
                    key={report.id}
                    className={`report-list-item${selectedReportId === report.id ? ' active' : ''}${selectedReportIds.includes(report.id) ? ' selected' : ''}`}
                  >
                    <label className="report-list-selector" aria-label={translate('auto.k1145', { value0: report.id })}>
                      <input
                        type="checkbox"
                        checked={selectedReportIds.includes(report.id)}
                        onChange={() => toggleReportSelection(report.id)}
                        disabled={loadState.loading}
                      />
                    </label>
                    <button
                      type="button"
                      className="report-list-button"
                      onClick={() => setSelectedReportId(report.id)}
                    >
                      <span className={`badge badge-${statusTone(report.status)}`} title={report.status}>{dictionaryLabel(report.status)}</span>
                      <strong>{shortId(report.executionRunId)}</strong>
                      <span>{stringFrom(report.summary.runStatus, '-')} · {stringFrom(report.summary.diagnosisPrimaryCategory, 'UNKNOWN')}</span>
                      <small>{report.generatedAt ? formatDateTime(report.generatedAt) : report.createdAt ? formatDateTime(report.createdAt) : report.id}</small>
                    </button>
                  </div>
                ))
              ) : (
                <div className="empty-state">
                  <FileText className="empty-state-icon" size={30} />
                  <strong>{translate('auto.k1146')}</strong>
                  <span>{translate('auto.k1147')}</span>
                </div>
              )}
            </div>
          </Panel>
        </section>

        <section className="reports-detail-column">
          {!detail ? (
            <Panel title={translate('auto.k1148')} desc={translate('auto.k1149')} testId="report-detail">
              <div className="empty-state">
                <FileText className="empty-state-icon" size={30} />
                <strong>{detailState.loading ? translate('auto.k1150') : translate('auto.k1151')}</strong>
                <span>{translate('auto.k1152')}</span>
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
      title={translate('auto.k1148')}
      desc={`${props.detail.schemaVersion} · ${props.detail.projectId}`}
      testId="report-detail"
      action={props.detail.status !== 'ARCHIVED' ? (
        <button className="btn btn-secondary btn-sm" type="button" onClick={props.onArchive} disabled={!props.canManage || props.state.loading}>
          <Archive size={15} />{translate('auto.k0871')}</button>
      ) : <span className="badge badge-neutral">{translate('auto.k1153')}</span>}
    >
      <div className="report-detail-header">
        <span className={`badge badge-${statusTone(props.detail.status)}`} title={props.detail.status}>{dictionaryLabel(props.detail.status)}</span>
        <span className="report-mono">{props.detail.id}</span>
      </div>
      <div className="report-summary-grid">
        <SummaryTile label="runStatus" value={stringFrom(summary.runStatus, props.detail.status)} />
        <SummaryTile label="triggerType" value={stringFrom(summary.triggerType, '-')} />
        <SummaryTile label="nodeCount" value={stringFrom(summary.nodeCount, '0')} />
        <SummaryTile label="evidence" value={stringFrom(summary.evidenceManifestCount, String(props.detail.evidenceManifests.length))} />
      </div>
      <div className="report-section-grid">
        <InfoBlock title={translate('auto.k1154')} value={formatRecord(summary.nodeStatusCounts)} />
        <InfoBlock title={translate('auto.k1155')} value={formatRecord(summary.failureBucketCounts)} />
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
      title={translate('auto.k1156')}
      desc={translate('auto.k1157')}
      testId="report-diagnosis"
      action={(
        <button className="btn btn-primary btn-sm" type="button" onClick={props.onDiagnose} disabled={disabled}>
          <Sparkles size={15} />{translate('auto.k1158')}</button>
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
        <div className="notice info">{translate('auto.k1159')}</div>
      )}
      {props.diagnosis?.errorCode && (
        <div className="notice warning">{translate('auto.k1160')}{props.diagnosis.errorCode}</div>
      )}
      {candidates.length > 0 && (
        <div className="report-card-list">
          {candidates.slice(0, 4).map((candidate, index) => (
            <div className="report-mini-card" key={index}>
              <strong>{stringFrom(recordValue(candidate, 'category'), translate('auto.k1161', { value0: index + 1 }))}</strong>
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
      title={translate('auto.k1162')}
      desc={translate('auto.k1163')}
      testId="report-compare-panel"
      action={(
        <div className="report-actions-row compact">
          <button
            className="btn btn-secondary btn-sm"
            type="button"
            onClick={props.onCompare}
            disabled={props.state.loading || !props.baselineReportId}
          >
            <RefreshCw size={15} />{translate('auto.k1164')}</button>
        </div>
      )}
    >
      <div className="report-compare-controls">
        <Field label="baseline report">
          <select
            value={props.baselineReportId}
            onChange={(event) => props.onBaselineChange(event.target.value)}
          >
            <option value="">{translate('auto.k1165')}</option>
            {props.reports.map((report) => (
              <option key={report.id} value={report.id}>
                {report.status} · {shortId(report.executionRunId)} · {report.generatedAt ? formatDateTime(report.generatedAt) : shortId(report.id)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      {props.reports.length === 0 && (
        <div className="notice info">{translate('auto.k1166')}</div>
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
            <div className="notice success">{translate('auto.k1167')}</div>
          ) : (
            <>
              <CompareDiffList title={translate('auto.k1168')} diffs={props.compareResult.metadataDiffs} />
              <CompareDiffList title={translate('auto.k1169')} diffs={props.compareResult.summaryDiffs} />
              <CompareDiffList title={translate('auto.k1170')} diffs={props.compareResult.diagnosisDiffs} />
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
        <div className="notice info">{translate('auto.k1171')}</div>
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
      title={translate('auto.k1125')}
      desc={translate('auto.k1172')}
      testId="report-defect-drafts"
      action={(
        <button className="btn btn-primary btn-sm" type="button" onClick={props.onCreateDraft} disabled={!props.canGenerate || props.state.loading || props.detail.status !== 'READY'}>
          <Bug size={15} />{translate('auto.k1173')}</button>
      )}
    >
      {props.detail.defectDrafts.length ? (
        <div className="report-card-list">
          {props.detail.defectDrafts.map((draft) => (
            <div className="report-mini-card" key={draft.id}>
              <div className="report-card-heading">
                <strong>{draft.title || translate('auto.k1174')}</strong>
                <span className={`badge badge-${statusTone(draft.status)}`} title={draft.status}>{dictionaryLabel(draft.status)}</span>
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
                  <CheckCircle2 size={15} />{translate('auto.k1175')}</button>
                <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canManage || props.state.loading || draft.status === 'DISMISSED'} onClick={() => props.onReviewDraft(draft, 'DISMISSED')}>
                  <Archive size={15} />{translate('auto.k1176')}</button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="notice info">{translate('auto.k1177')}</div>
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
      title={translate('auto.k1178')}
      desc={translate('auto.k1179')}
      testId="report-export-panel"
      action={(
        <div className="report-actions-row compact">
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('JSON')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileJson size={15} />{translate('auto.k1180')}</button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('MARKDOWN')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileText size={15} />{translate('auto.k1181')}</button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('HTML')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileCode size={15} />{translate('auto.k1182')}</button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('PDF')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <Download size={15} />{translate('auto.k1183')}</button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('WORD')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileText size={15} />{translate('auto.k1184')}</button>
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => props.onExport('EXCEL')} disabled={!props.canExport || props.state.loading || props.detail.status !== 'READY'}>
            <FileSpreadsheet size={15} />{translate('auto.k1185')}</button>
          {props.latestExport?.downloadReady ? (
            <button className="btn btn-secondary btn-sm" type="button" onClick={props.onDownloadExport} disabled={!props.canExport || props.state.loading}>
              <Package size={15} />{translate('auto.k1186')}</button>
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
        <div className="notice info">{translate('auto.k1187')}</div>
      )}
      <PolicySummary policy={props.latestExport?.redactionPolicy ?? { aggregateOnly: true }} />
      <StateLine state={props.state} />
    </Panel>
  );
}

function EvidenceList(props: { manifests: ReportDetail['evidenceManifests'] }) {
  if (!props.manifests.length) {
    return <div className="notice info">{translate('auto.k1188')}</div>;
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
      <div className="report-policy-title"><ShieldCheck size={15} />{translate('auto.k1189')}</div>
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
    return <span className="document-state-line">{translate('auto.k1062')}</span>;
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
  if (!draft.projectId.trim()) issues.push(translate('auto.k1190'));
  if (!uuidPattern.test(draft.executionRunId.trim())) issues.push(translate('auto.k1191'));
  if (draft.requestKey.trim() && !requestKeyPattern.test(draft.requestKey.trim())) {
    issues.push(translate('auto.k1192'));
  }
  if (draft.reason.length > 200) issues.push(translate('auto.k1193'));
  if (hasSensitiveText(draft.reason)) issues.push(translate('auto.k1194'));
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
    : exportType === 'HTML'
      ? 'html'
    : exportType === 'PDF'
      ? 'pdf'
      : exportType === 'WORD'
        ? 'docx'
        : exportType === 'EXCEL'
          ? 'xlsx'
        : 'json';
  return `report-${reportId}.${suffix}`;
}

function fallbackBatchExportFileName(projectId: string, exportType: string) {
  const normalizedProjectId = projectId.trim().replace(/[^A-Za-z0-9_.-]+/g, '-').replace(/^-+|-+$/g, '') || 'reports';
  return `report-batch-${normalizedProjectId}-${exportType.toLowerCase()}.zip`;
}
