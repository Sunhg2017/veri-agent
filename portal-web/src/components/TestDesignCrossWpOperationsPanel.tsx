import {
  RefreshCw,
  Repeat2,
  Save,
  Send,
  Sparkles
} from 'lucide-react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import type {
  TestDesignAuditOutboxRequeueResult,
  TestDesignAuditReportTemplateView,
  TestDesignCrossWpDetailAuditReportView,
  TestDesignCrossWpOperationsDashboardView,
  TestDesignModelObservationDrilldownView,
  TestDesignPublishCompensationRunResult,
  TestDesignQueueAlertSubscriptionView,
  TestDesignQueuedEventReplayResult
} from '../api/testDesign';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

export type CrossWpOperationsFilters = {
  projectId: string;
  promptKey: string;
};

export type AuditOutboxRequeueDraft = {
  projectId: string;
  status: string;
  maxItems: string;
  reason: string;
};

export type QueueAlertSubscriptionDraft = {
  projectId: string;
  promptKey: string;
  alertType: string;
  channel: string;
  targetRef: string;
  thresholdSeconds: string;
  enabled: boolean;
};

export type QueuedEventReplayDraft = {
  projectId: string;
  promptKey: string;
  replayType: string;
  maxItems: string;
  reason: string;
};

export type PublishCompensationRunDraft = {
  projectId: string;
  promptKey: string;
  maxItems: string;
  reason: string;
};

const auditOutboxReplayStatuses = ['FAILED_OR_DEAD', 'FAILED', 'DEAD'] as const;
const queueAlertTypes = [
  'GENERATION_QUEUE_LAG',
  'GENERATION_TIMEOUT',
  'PUBLISH_QUEUE_LAG',
  'PUBLISH_TIMEOUT',
  'COMPENSATION_FAILURE',
  'AUDIT_OUTBOX_REPLAY_ELIGIBLE'
] as const;
const queueAlertChannels = ['OPS_CONSOLE', 'EMAIL', 'WEBHOOK'] as const;
const queuedEventReplayTypes = ['GENERATION', 'PUBLISH', 'ALL'] as const;

export function CrossWpOperationsPanel(props: {
  state: WorkState;
  canPolicyManage: boolean;
  dashboard: TestDesignCrossWpOperationsDashboardView | null;
  auditReportTemplate: TestDesignAuditReportTemplateView | null;
  modelObservationDrilldown: TestDesignModelObservationDrilldownView | null;
  crossWpDetailAuditReport: TestDesignCrossWpDetailAuditReportView | null;
  filters: CrossWpOperationsFilters;
  requeueDraft: AuditOutboxRequeueDraft;
  requeueResult: TestDesignAuditOutboxRequeueResult | null;
  queueAlertSubscriptions: TestDesignQueueAlertSubscriptionView[];
  queueAlertSubscriptionDraft: QueueAlertSubscriptionDraft;
  queueAlertSubscriptionResult: TestDesignQueueAlertSubscriptionView | null;
  queuedEventReplayDraft: QueuedEventReplayDraft;
  queuedEventReplayResult: TestDesignQueuedEventReplayResult | null;
  publishCompensationRunDraft: PublishCompensationRunDraft;
  publishCompensationRunResult: TestDesignPublishCompensationRunResult | null;
  onFiltersChange: Dispatch<SetStateAction<CrossWpOperationsFilters>>;
  onRequeueDraftChange: Dispatch<SetStateAction<AuditOutboxRequeueDraft>>;
  onQueueAlertSubscriptionDraftChange: Dispatch<SetStateAction<QueueAlertSubscriptionDraft>>;
  onQueuedEventReplayDraftChange: Dispatch<SetStateAction<QueuedEventReplayDraft>>;
  onPublishCompensationRunDraftChange: Dispatch<SetStateAction<PublishCompensationRunDraft>>;
  onRefresh: () => void;
  onRequeue: (event: FormEvent<HTMLFormElement>) => void;
  onQueueAlertSubscriptionSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onQueuedEventReplaySubmit: (event: FormEvent<HTMLFormElement>) => void;
  onPublishCompensationRunSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const dashboard = props.dashboard;
  const auditDashboard = dashboard?.auditDashboard;
  const auditOutbox = dashboard?.auditOutbox;
  const queueAlerts = dashboard?.queueAlerts;
  const runbook = dashboard?.compensationRunbook;
  const auditReport = dashboard?.operationsAuditReport;
  const auditTemplate = props.auditReportTemplate ?? dashboard?.auditReportTemplate;
  const modelDrilldown = props.modelObservationDrilldown ?? dashboard?.modelObservationDrilldown;
  const detailAuditReport = props.crossWpDetailAuditReport ?? dashboard?.crossWpDetailAuditReport;
  const projectId = props.filters.projectId;
  const promptKey = props.filters.promptKey;
  const canRequeue = props.canPolicyManage && !props.state.loading && Boolean(props.requeueDraft.projectId.trim() || projectId.trim());
  const canQueueAlertSave = props.canPolicyManage && !props.state.loading && Boolean(props.queueAlertSubscriptionDraft.projectId.trim() || projectId.trim());
  const canReplayQueuedEvents = props.canPolicyManage && !props.state.loading && Boolean(props.queuedEventReplayDraft.projectId.trim() || projectId.trim());
  const canRunCompensation = props.canPolicyManage && !props.state.loading && Boolean(props.publishCompensationRunDraft.projectId.trim() || projectId.trim());

  return (
    <section className="panel test-design-cross-wp-operations">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1424')}</h2>
          <p className="panel-desc">
            {dashboard?.projectId || projectId || translate('auto.k1425')}
            {' · '}
            {dashboard?.promptKey || promptKey || translate('auto.k1426')}
          </p>
        </div>
        <div className="toolbar-actions">
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.state.loading} onClick={props.onRefresh}>
            <RefreshCw size={15} />
            {translate('auto.k0170')}</button>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        <StateLine state={props.state} />

        <div className="form-grid test-design-cross-wp-filter">
          <label className="field">
            <span className="field-label">{translate('auto.k1389')}</span>
            <input
              value={projectId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="project UUID"
            />
          </label>
          <label className="field">
            <span className="field-label">Prompt</span>
            <input
              value={promptKey}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, promptKey: event.target.value }))}
              placeholder="prompt key"
            />
          </label>
        </div>

        {dashboard ? (
          <>
            <div className="test-design-quality-metrics test-design-cross-wp-metrics">
              <div className="test-design-quality-metric tone-info">
                <span>{translate('auto.k1427')}</span>
                <strong>{dashboard.taskCount}</strong>
                <small>{translate('auto.k1428')}{dashboard.candidateCount} {translate('auto.k1429')}{dashboard.publishRecordCount}</small>
              </div>
              <div className={`test-design-quality-metric tone-${dashboard.candidateScopeMismatchCount + dashboard.publishScopeMismatchCount > 0 ? 'warning' : 'success'}`}>
                <span>{translate('auto.k1430')}</span>
                <strong>{formatPercent(dashboard.candidateScopeCoveragePercent)}</strong>
                <small>{translate('auto.k0779')}{formatPercent(dashboard.publishScopeCoveragePercent)}</small>
              </div>
              <div className="test-design-quality-metric tone-info">
                <span>{translate('auto.k1431')}</span>
                <strong>{auditDashboard?.wp1AuditEventCount ?? 0}</strong>
                <small>{translate('auto.k0368')}{auditDashboard?.wp1AuditSuccessCount ?? 0} {translate('auto.k1432')}{auditDashboard?.wp1AuditDeniedCount ?? 0}</small>
              </div>
              <div className={`test-design-quality-metric tone-${(auditOutbox?.replayEligibleCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                <span>{translate('auto.k1433')}</span>
                <strong>{auditOutbox?.replayEligibleCount ?? 0}</strong>
                <small>{translate('auto.k0369')}{auditOutbox?.failedCount ?? 0} {translate('auto.k1434')}{auditOutbox?.deadCount ?? 0}</small>
              </div>
              <div className={`test-design-quality-metric tone-${(queueAlerts?.activeWarningCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                <span>{translate('auto.k1435')}</span>
                <strong>{queueAlerts?.activeWarningCount ?? 0}</strong>
                <small>{translate('auto.k1436')}{queueAlerts?.enabledSubscriptionCount ?? 0}/{queueAlerts?.subscriptionCount ?? 0}</small>
              </div>
              <div className={`test-design-quality-metric tone-${(runbook?.eligibleCandidateCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                <span>{translate('auto.k1437')}</span>
                <strong>{runbook?.eligibleCandidateCount ?? 0}</strong>
                <small>{translate('auto.k1348')}{runbook?.effectiveBatchSize ?? 0} {translate('auto.k1438')}{runbook?.manualRunSupported ? dictionaryLabel('READY') : dictionaryLabel('BLOCKED')}</small>
              </div>
              <div className="test-design-quality-metric tone-info">
                <span>{translate('auto.k1439')}</span>
                <strong>{modelDrilldown?.totalInvocationCount ?? 0}</strong>
                <small>{translate('auto.k1440')}{modelDrilldown?.buckets.length ?? 0} · fallback {modelDrilldown?.fallbackCount ?? 0}</small>
              </div>
              <div className="test-design-quality-metric tone-info">
                <span>{translate('auto.k1441')}</span>
                <strong>{detailAuditReport?.rowCount ?? 0}</strong>
                <small>{translate('auto.k1442')}{auditTemplate?.sections.length ?? 0} {translate('auto.k1443')}</small>
              </div>
            </div>

            <div className="test-design-cross-wp-readiness">
              {dashboard.readiness.map((item) => (
                <span className={`test-design-quality-chip tone-${item.tone}`} key={item.code}>
                  {item.label} {item.ready ? dictionaryLabel('READY') : dictionaryLabel('BLOCKED')}
                </span>
              ))}
              <span className={`test-design-quality-chip tone-${dashboard.aggregateOnly && !dashboard.detailIdentifiersExported ? 'success' : 'warning'}`}>
                aggregate-only {dashboard.aggregateOnly && !dashboard.detailIdentifiersExported ? dictionaryLabel('ENABLED') : translate('auto.k0093')}
              </span>
            </div>

            <div className="test-design-cross-wp-grid">
              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1444')}</strong>
                  <span>{dashboard.generatedAt ?? '-'}</span>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>{translate('auto.k1445')}</span>
                  <strong>{auditDashboard?.wp2InvocationCount ?? 0}</strong>
                  <small>{translate('auto.k0368')}{auditDashboard?.wp2InvocationSucceededCount ?? 0} · fallback {auditDashboard?.wp2FallbackCount ?? 0}</small>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>{translate('auto.k1446')}</span>
                  <strong>{auditDashboard?.wp3PublishedCaseCount ?? 0}</strong>
                  <small>trace link {auditDashboard?.wp3TraceLinkCount ?? 0}</small>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>{translate('auto.k1447')}</span>
                  <strong>{dashboard.detailIdentifiersExported ? dictionaryLabel('ENABLED') : dictionaryLabel('DISABLED')}</strong>
                  <small>{translate('auto.k1448')}</small>
                </div>
              </div>

              <form className="test-design-cross-wp-group" onSubmit={props.onRequeue}>
                <div className="test-design-evaluation-list-heading">
                  <strong>Audit outbox</strong>
                  <span>{translate('auto.k1449')}{auditOutbox?.totalCount ?? 0}</span>
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">{translate('auto.k1389')}</span>
                    <input
                      value={props.requeueDraft.projectId}
                      onChange={(event) => props.onRequeueDraftChange((current) => ({ ...current, projectId: event.target.value }))}
                      placeholder="project UUID"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k0182')}</span>
                    <select
                      value={props.requeueDraft.status}
                      onChange={(event) => props.onRequeueDraftChange((current) => ({ ...current, status: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {auditOutboxReplayStatuses.map((status) => (
                        <option key={status} value={status}>{dictionaryLabel(status)}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k1450')}</span>
                    <input
                      type="number"
                      min={1}
                      max={100}
                      value={props.requeueDraft.maxItems}
                      onChange={(event) => props.onRequeueDraftChange((current) => ({ ...current, maxItems: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">{translate('auto.k0878')}</span>
                  <textarea
                    value={props.requeueDraft.reason}
                    onChange={(event) => props.onRequeueDraftChange((current) => ({ ...current, reason: event.target.value }))}
                    rows={2}
                    disabled={!props.canPolicyManage}
                  />
                </label>
                <div className="toolbar-actions test-design-cross-wp-actions">
                  <button className="btn btn-primary btn-sm" type="submit" disabled={!canRequeue}>
                    <Repeat2 size={15} />
                    {translate('auto.k1451')}</button>
                  {props.requeueResult && (
                    <span className="test-design-cross-wp-result">
                      {props.requeueResult.requestedStatus} · {props.requeueResult.requeuedCount}/{props.requeueResult.requestedLimit}
                    </span>
                  )}
                </div>
              </form>
            </div>

            <div className="test-design-cross-wp-grid">
              <form className="test-design-cross-wp-group" onSubmit={props.onQueueAlertSubscriptionSubmit}>
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1452')}</strong>
                  <span>{queueAlerts?.subscriptionCount ?? props.queueAlertSubscriptions.length}</span>
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">{translate('auto.k1389')}</span>
                    <input
                      value={props.queueAlertSubscriptionDraft.projectId}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, projectId: event.target.value }))}
                      placeholder="project UUID"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">Prompt</span>
                    <input
                      value={props.queueAlertSubscriptionDraft.promptKey}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, promptKey: event.target.value }))}
                      placeholder={translate('auto.k1453')}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k0286')}</span>
                    <select
                      value={props.queueAlertSubscriptionDraft.alertType}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, alertType: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {queueAlertTypes.map((type) => (
                        <option key={type} value={type}>{dictionaryLabel(type)}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k1454')}</span>
                    <select
                      value={props.queueAlertSubscriptionDraft.channel}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, channel: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {queueAlertChannels.map((channel) => (
                        <option key={channel} value={channel}>{dictionaryLabel(channel)}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k1455')}</span>
                    <input
                      type="number"
                      min={0}
                      max={86400}
                      value={props.queueAlertSubscriptionDraft.thresholdSeconds}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, thresholdSeconds: event.target.value }))}
                      placeholder={translate('auto.k1456')}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k0251')}</span>
                    <input
                      type="checkbox"
                      checked={props.queueAlertSubscriptionDraft.enabled}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, enabled: event.target.checked }))}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">{translate('auto.k1457')}</span>
                  <input
                    value={props.queueAlertSubscriptionDraft.targetRef}
                    onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, targetRef: event.target.value }))}
                    disabled={!props.canPolicyManage}
                  />
                </label>
                <div className="toolbar-actions test-design-cross-wp-actions">
                  <button className="btn btn-primary btn-sm" type="submit" disabled={!canQueueAlertSave}>
                    <Save size={15} />
                    {translate('auto.k1458')}</button>
                  {props.queueAlertSubscriptionResult && (
                    <span className="test-design-cross-wp-result">
                      {dictionaryLabel(props.queueAlertSubscriptionResult.alertType)} · {dictionaryLabel(props.queueAlertSubscriptionResult.channel)}
                    </span>
                  )}
                </div>
              </form>

              <form className="test-design-cross-wp-group" onSubmit={props.onQueuedEventReplaySubmit}>
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1459')}</strong>
                  <span>{queueAlerts?.queuedTaskCount ?? 0} / {queueAlerts?.publishQueuedCandidateCount ?? 0}</span>
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">{translate('auto.k1389')}</span>
                    <input
                      value={props.queuedEventReplayDraft.projectId}
                      onChange={(event) => props.onQueuedEventReplayDraftChange((current) => ({ ...current, projectId: event.target.value }))}
                      placeholder="project UUID"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">Prompt</span>
                    <input
                      value={props.queuedEventReplayDraft.promptKey}
                      onChange={(event) => props.onQueuedEventReplayDraftChange((current) => ({ ...current, promptKey: event.target.value }))}
                      placeholder={translate('auto.k1453')}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k0286')}</span>
                    <select
                      value={props.queuedEventReplayDraft.replayType}
                      onChange={(event) => props.onQueuedEventReplayDraftChange((current) => ({ ...current, replayType: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {queuedEventReplayTypes.map((type) => (
                        <option key={type} value={type}>{dictionaryLabel(type)}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k1450')}</span>
                    <input
                      type="number"
                      min={1}
                      max={100}
                      value={props.queuedEventReplayDraft.maxItems}
                      onChange={(event) => props.onQueuedEventReplayDraftChange((current) => ({ ...current, maxItems: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">{translate('auto.k0878')}</span>
                  <textarea
                    value={props.queuedEventReplayDraft.reason}
                    onChange={(event) => props.onQueuedEventReplayDraftChange((current) => ({ ...current, reason: event.target.value }))}
                    rows={2}
                    disabled={!props.canPolicyManage}
                  />
                </label>
                <div className="toolbar-actions test-design-cross-wp-actions">
                  <button className="btn btn-primary btn-sm" type="submit" disabled={!canReplayQueuedEvents}>
                    <Send size={15} />
                    {translate('auto.k1460')}</button>
                  {props.queuedEventReplayResult && (
                    <span className="test-design-cross-wp-result">
                      {props.queuedEventReplayResult.replayType} · {props.queuedEventReplayResult.generationTaskEvents}/{props.queuedEventReplayResult.publishCandidateEvents}
                    </span>
                  )}
                </div>
              </form>
            </div>

            <div className="test-design-cross-wp-grid">
              <form className="test-design-cross-wp-group" onSubmit={props.onPublishCompensationRunSubmit}>
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1461')}</strong>
                  <span>{runbook?.effectiveBatchSize ?? 0} · {runbook?.eligibleCandidateCount ?? 0}</span>
                </div>
                <div className="test-design-cross-wp-readiness">
                  {(runbook?.steps ?? []).map((step) => (
                    <span className={`test-design-quality-chip tone-${step.tone}`} key={step.code}>
                      {step.label} {step.ready ? dictionaryLabel('READY') : dictionaryLabel('BLOCKED')}
                    </span>
                  ))}
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">{translate('auto.k1389')}</span>
                    <input
                      value={props.publishCompensationRunDraft.projectId}
                      onChange={(event) => props.onPublishCompensationRunDraftChange((current) => ({ ...current, projectId: event.target.value }))}
                      placeholder="project UUID"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">Prompt</span>
                    <input
                      value={props.publishCompensationRunDraft.promptKey}
                      onChange={(event) => props.onPublishCompensationRunDraftChange((current) => ({ ...current, promptKey: event.target.value }))}
                      placeholder={translate('auto.k1453')}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">{translate('auto.k1450')}</span>
                    <input
                      type="number"
                      min={1}
                      max={100}
                      value={props.publishCompensationRunDraft.maxItems}
                      onChange={(event) => props.onPublishCompensationRunDraftChange((current) => ({ ...current, maxItems: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">{translate('auto.k0878')}</span>
                  <textarea
                    value={props.publishCompensationRunDraft.reason}
                    onChange={(event) => props.onPublishCompensationRunDraftChange((current) => ({ ...current, reason: event.target.value }))}
                    rows={2}
                    disabled={!props.canPolicyManage}
                  />
                </label>
                <div className="toolbar-actions test-design-cross-wp-actions">
                  <button className="btn btn-primary btn-sm" type="submit" disabled={!canRunCompensation}>
                    <Sparkles size={15} />
                    {translate('auto.k1462')}</button>
                  {props.publishCompensationRunResult && (
                    <span className="test-design-cross-wp-result">
                      {props.publishCompensationRunResult.scannedCandidates} · {props.publishCompensationRunResult.succeededCandidates}
                    </span>
                  )}
                </div>
              </form>

              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1463')}</strong>
                  <span>{auditReport?.totalOperationCount ?? 0}</span>
                </div>
                <div className="test-design-quality-metrics">
                  <div className="test-design-quality-metric tone-info">
                    <span>{translate('auto.k0368')}</span>
                    <strong>{auditReport?.successCount ?? 0}</strong>
                    <small>{translate('auto.k0369')}{auditReport?.failedCount ?? 0} {translate('auto.k1432')}{auditReport?.deniedCount ?? 0}</small>
                  </div>
                  <div className="test-design-quality-metric tone-info">
                    <span>{translate('auto.k1464')}</span>
                    <strong>{auditReport?.queueAlertSubscriptionMutationCount ?? 0}</strong>
                    <small>queued replay {auditReport?.queuedEventReplayCount ?? 0}</small>
                  </div>
                  <div className="test-design-quality-metric tone-info">
                    <span>{translate('auto.k1465')}</span>
                    <strong>{auditReport?.publishCompensationRunCount ?? 0}</strong>
                    <small>{translate('auto.k1466')}{auditReport?.auditOutboxRequeueCount ?? 0}</small>
                  </div>
                </div>
                <div className="test-design-cross-wp-readiness">
                  <span className={`test-design-quality-chip tone-${auditReport?.aggregateOnly && !auditReport?.detailRowsExported ? 'success' : 'warning'}`}>
                    aggregate-only {auditReport?.aggregateOnly && !auditReport?.detailRowsExported ? dictionaryLabel('ENABLED') : translate('auto.k0093')}
                  </span>
                  <span className={`test-design-quality-chip tone-${auditReport?.exportSupported ? 'success' : 'warning'}`}>
                    export {auditReport?.exportSupported ? dictionaryLabel('READY') : dictionaryLabel('BLOCKED')}
                  </span>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>{translate('auto.k1467')}</span>
                  <strong>{auditReport?.latestOperationAt ?? '-'}</strong>
                  <small>{translate('auto.k1468')}</small>
                </div>
              </div>
            </div>

            <div className="test-design-cross-wp-grid">
              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1469')}</strong>
                  <span>{auditTemplate?.templateVersion ?? '-'}</span>
                </div>
                <div className="test-design-cross-wp-readiness">
                  <span className={`test-design-quality-chip tone-${auditTemplate?.aggregateOnly && !auditTemplate?.identifierValuesExported ? 'success' : 'warning'}`}>
                    aggregate-only {auditTemplate?.aggregateOnly ? dictionaryLabel('ENABLED') : translate('auto.k0093')}
                  </span>
                  <span className={`test-design-quality-chip tone-${auditTemplate?.modelObservationDrilldownSupported ? 'success' : 'warning'}`}>
                    model drilldown {auditTemplate?.modelObservationDrilldownSupported ? dictionaryLabel('READY') : dictionaryLabel('BLOCKED')}
                  </span>
                  <span className={`test-design-quality-chip tone-${auditTemplate?.crossWpDetailReportSupported ? 'success' : 'warning'}`}>
                    detail report {auditTemplate?.crossWpDetailReportSupported ? dictionaryLabel('READY') : dictionaryLabel('BLOCKED')}
                  </span>
                </div>
                <div className="test-design-cross-wp-list">
                  {(auditTemplate?.sections ?? []).map((section) => (
                    <div className="test-design-cross-wp-row" key={section.code}>
                      <span>
                        <strong>{section.label}</strong>
                        <em>{section.description ?? '-'}</em>
                      </span>
                      <span className="badge badge-info">{section.fields.length} fields</span>
                    </div>
                  ))}
                  {!auditTemplate?.sections.length && <div className="notice info">{translate('auto.k1470')}</div>}
                </div>
              </div>

              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1471')}</strong>
                  <span>{modelDrilldown?.totalInvocationCount ?? 0}</span>
                </div>
                <div className="test-design-quality-metrics">
                  <div className="test-design-quality-metric tone-info">
                    <span>token</span>
                    <strong>{(modelDrilldown?.inputTokenTotal ?? 0) + (modelDrilldown?.outputTokenTotal ?? 0)}</strong>
                    <small>{translate('auto.k1472')}{modelDrilldown?.inputTokenTotal ?? 0} {translate('auto.k1473')}{modelDrilldown?.outputTokenTotal ?? 0}</small>
                  </div>
                  <div className="test-design-quality-metric tone-info">
                    <span>{translate('auto.k1474')}</span>
                    <strong>{modelDrilldown?.averageLatencyMs ?? 0}ms</strong>
                    <small>cost {modelDrilldown?.totalCostText ?? '0'}</small>
                  </div>
                  <div className={`test-design-quality-metric tone-${(modelDrilldown?.failedCount ?? 0) + (modelDrilldown?.blockedCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                    <span>{translate('auto.k1475')}</span>
                    <strong>{(modelDrilldown?.failedCount ?? 0) + (modelDrilldown?.blockedCount ?? 0)}</strong>
                    <small>{translate('auto.k1476')}{modelDrilldown?.traceSignalCount ?? 0} · job {modelDrilldown?.jobSignalCount ?? 0}</small>
                  </div>
                </div>
                <div className="test-design-cross-wp-list">
                  {(modelDrilldown?.buckets ?? []).slice(0, 8).map((bucket) => (
                    <div className="test-design-cross-wp-row" key={`${bucket.dimension}-${bucket.bucketKey}`}>
                      <span>
                        <strong>{bucket.dimension} · {bucket.bucketLabel}</strong>
                        <em>{translate('auto.k0368')}{bucket.succeededCount} {translate('auto.k1477')}{bucket.failedCount} {translate('auto.k1478')}{bucket.blockedCount}</em>
                        <small>token {bucket.inputTokenTotal + bucket.outputTokenTotal} · avg {bucket.averageLatencyMs}ms</small>
                      </span>
                      <span className="badge badge-info">{bucket.invocationCount}</span>
                    </div>
                  ))}
                  {!modelDrilldown?.buckets.length && <div className="notice info">{translate('auto.k1479')}</div>}
                </div>
              </div>
            </div>

            <div className="test-design-cross-wp-group">
              <div className="test-design-evaluation-list-heading">
                <strong>{translate('auto.k1480')}</strong>
                <span>{detailAuditReport?.rowCount ?? 0}</span>
              </div>
              <div className="test-design-cross-wp-readiness">
                <span className={`test-design-quality-chip tone-${detailAuditReport?.aggregateOnly && !detailAuditReport?.identifierValuesExported ? 'success' : 'warning'}`}>
                  redacted rows {detailAuditReport?.aggregateOnly ? 'on' : 'check'}
                </span>
                <span className={`test-design-quality-chip tone-${detailAuditReport?.rawAuditEventExported ? 'warning' : 'success'}`}>
                  raw audit {detailAuditReport?.rawAuditEventExported ? 'exported' : 'off'}
                </span>
                <span className={`test-design-quality-chip tone-${detailAuditReport?.payloadExported ? 'warning' : 'success'}`}>
                  payload {detailAuditReport?.payloadExported ? 'exported' : 'off'}
                </span>
              </div>
              <div className="test-design-cross-wp-list">
                {(detailAuditReport?.rows ?? []).slice(0, 10).map((row) => (
                  <div className="test-design-cross-wp-row" key={`${row.section}-${row.category}-${row.status}`}>
                    <span>
                      <strong>{row.section} · {row.category}</strong>
                      <em>{dictionaryLabel(row.status)} {translate('auto.k1481')}{row.successCount} {translate('auto.k1477')}{row.failedCount} {translate('auto.k1482')}{row.warningCount}</em>
                      <small>{row.latestEventAt ?? '-'}</small>
                    </span>
                    <span className="badge badge-info">{row.eventCount}</span>
                  </div>
                ))}
                {!detailAuditReport?.rows.length && <div className="notice info">{translate('auto.k1483')}</div>}
              </div>
            </div>

            {queueAlerts && (
              <div className="test-design-cross-wp-grid">
                <div className="test-design-cross-wp-group">
                  <div className="test-design-evaluation-list-heading">
                    <strong>{translate('auto.k1484')}</strong>
                    <span>{queueAlerts.generatedAt ?? '-'}</span>
                  </div>
                  <div className="test-design-cross-wp-row">
                    <span>{translate('auto.k1485')}</span>
                    <strong>{queueAlerts.queuedTaskCount}</strong>
                    <small>{translate('auto.k1486')}{queueAlerts.oldestGenerationQueuedAgeSeconds}{translate('auto.k1487')}{queueAlerts.generationQueueLagWarningSeconds}s</small>
                  </div>
                  <div className="test-design-cross-wp-row">
                    <span>{translate('auto.k1488')}</span>
                    <strong>{queueAlerts.publishQueuedCandidateCount}</strong>
                    <small>{translate('auto.k1486')}{queueAlerts.oldestPublishQueuedAgeSeconds}{translate('auto.k1487')}{queueAlerts.publishQueueLagWarningSeconds}s</small>
                  </div>
                  <div className="test-design-cross-wp-row">
                    <span>{translate('auto.k1489')}</span>
                    <strong>{queueAlerts.compensationEligibleCandidateCount}</strong>
                    <small>{translate('auto.k1490')}{queueAlerts.staleRunningTaskCount} {translate('auto.k1491')}{queueAlerts.stalePublishingCandidateCount}</small>
                  </div>
                </div>
                <div className="test-design-cross-wp-group">
                  <div className="test-design-evaluation-list-heading">
                    <strong>{translate('auto.k1492')}</strong>
                    <span>{props.queueAlertSubscriptions.length}</span>
                  </div>
                  <div className="test-design-cross-wp-list">
                    {props.queueAlertSubscriptions.length ? (
                      props.queueAlertSubscriptions.map((subscription) => (
                        <div className="test-design-cross-wp-row" key={subscription.id}>
                          <span>
                            <strong title={subscription.alertType}>{dictionaryLabel(subscription.alertType)}</strong>
                            <em>{dictionaryLabel(subscription.channel)} · {subscription.targetRef}</em>
                            <small>{subscription.promptKey || translate('auto.k1493')}</small>
                          </span>
                          <span className={`badge badge-${subscription.enabled ? 'success' : 'neutral'}`}>
                            {subscription.enabled ? 'ENABLED' : 'DISABLED'}
                          </span>
                        </div>
                      ))
                    ) : (
                      <div className="notice info">{translate('auto.k1494')}</div>
                    )}
                  </div>
                </div>
              </div>
            )}

            {props.publishCompensationRunResult && (
              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>{translate('auto.k1495')}</strong>
                  <span>{props.publishCompensationRunResult.runAt ?? '-'}</span>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>{translate('auto.k1496')}</span>
                  <strong>{props.publishCompensationRunResult.scannedCandidates}</strong>
                  <small>{props.publishCompensationRunResult.succeededCandidates} {translate('auto.k1497')}{props.publishCompensationRunResult.failedCandidates} {translate('auto.k1498')}{props.publishCompensationRunResult.skippedCandidates} {translate('auto.k0789')}</small>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="notice info">{translate('auto.k1499')}</div>
        )}
      </div>
    </section>
  );
}

function formatPercent(value?: number) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '0%';
  }
  return `${Math.round(value * 10) / 10}%`;
}
