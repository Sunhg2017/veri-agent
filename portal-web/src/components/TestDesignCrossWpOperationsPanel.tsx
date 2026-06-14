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
          <h2 className="panel-title">跨 WP 统一运营</h2>
          <p className="panel-desc">
            {dashboard?.projectId || projectId || '平台聚合'}
            {' · '}
            {dashboard?.promptKey || promptKey || '全部 Prompt'}
          </p>
        </div>
        <div className="toolbar-actions">
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.state.loading} onClick={props.onRefresh}>
            <RefreshCw size={15} />
            刷新
          </button>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        <StateLine state={props.state} />

        <div className="form-grid test-design-cross-wp-filter">
          <label className="field">
            <span className="field-label">项目 ID</span>
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
                <span>任务/候选</span>
                <strong>{dashboard.taskCount}</strong>
                <small>候选 {dashboard.candidateCount} · 发布 {dashboard.publishRecordCount}</small>
              </div>
              <div className={`test-design-quality-metric tone-${dashboard.candidateScopeMismatchCount + dashboard.publishScopeMismatchCount > 0 ? 'warning' : 'success'}`}>
                <span>Scope 覆盖</span>
                <strong>{formatPercent(dashboard.candidateScopeCoveragePercent)}</strong>
                <small>发布 {formatPercent(dashboard.publishScopeCoveragePercent)}</small>
              </div>
              <div className="test-design-quality-metric tone-info">
                <span>WP1 审计</span>
                <strong>{auditDashboard?.wp1AuditEventCount ?? 0}</strong>
                <small>成功 {auditDashboard?.wp1AuditSuccessCount ?? 0} · 拒绝 {auditDashboard?.wp1AuditDeniedCount ?? 0}</small>
              </div>
              <div className={`test-design-quality-metric tone-${(auditOutbox?.replayEligibleCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                <span>Outbox 可重放</span>
                <strong>{auditOutbox?.replayEligibleCount ?? 0}</strong>
                <small>失败 {auditOutbox?.failedCount ?? 0} · 死信 {auditOutbox?.deadCount ?? 0}</small>
              </div>
              <div className={`test-design-quality-metric tone-${(queueAlerts?.activeWarningCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                <span>队列告警</span>
                <strong>{queueAlerts?.activeWarningCount ?? 0}</strong>
                <small>订阅 {queueAlerts?.enabledSubscriptionCount ?? 0}/{queueAlerts?.subscriptionCount ?? 0}</small>
              </div>
              <div className={`test-design-quality-metric tone-${(runbook?.eligibleCandidateCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                <span>补偿候选</span>
                <strong>{runbook?.eligibleCandidateCount ?? 0}</strong>
                <small>批量 {runbook?.effectiveBatchSize ?? 0} · 手工 {runbook?.manualRunSupported ? 'ready' : 'blocked'}</small>
              </div>
              <div className="test-design-quality-metric tone-info">
                <span>模型观测</span>
                <strong>{modelDrilldown?.totalInvocationCount ?? 0}</strong>
                <small>桶 {modelDrilldown?.buckets.length ?? 0} · fallback {modelDrilldown?.fallbackCount ?? 0}</small>
              </div>
              <div className="test-design-quality-metric tone-info">
                <span>明细审计</span>
                <strong>{detailAuditReport?.rowCount ?? 0}</strong>
                <small>模板 {auditTemplate?.sections.length ?? 0} 分区</small>
              </div>
            </div>

            <div className="test-design-cross-wp-readiness">
              {dashboard.readiness.map((item) => (
                <span className={`test-design-quality-chip tone-${item.tone}`} key={item.code}>
                  {item.label} {item.ready ? 'ready' : 'blocked'}
                </span>
              ))}
              <span className={`test-design-quality-chip tone-${dashboard.aggregateOnly && !dashboard.detailIdentifiersExported ? 'success' : 'warning'}`}>
                aggregate-only {dashboard.aggregateOnly && !dashboard.detailIdentifiersExported ? 'on' : 'check'}
              </span>
            </div>

            <div className="test-design-cross-wp-grid">
              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>审计链聚合</strong>
                  <span>{dashboard.generatedAt ?? '-'}</span>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>WP2 调用</span>
                  <strong>{auditDashboard?.wp2InvocationCount ?? 0}</strong>
                  <small>成功 {auditDashboard?.wp2InvocationSucceededCount ?? 0} · fallback {auditDashboard?.wp2FallbackCount ?? 0}</small>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>WP3 发布</span>
                  <strong>{auditDashboard?.wp3PublishedCaseCount ?? 0}</strong>
                  <small>trace link {auditDashboard?.wp3TraceLinkCount ?? 0}</small>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>标识导出</span>
                  <strong>{dashboard.detailIdentifiersExported ? 'ON' : 'OFF'}</strong>
                  <small>trace/model/sourceRef 均为聚合信号</small>
                </div>
              </div>

              <form className="test-design-cross-wp-group" onSubmit={props.onRequeue}>
                <div className="test-design-evaluation-list-heading">
                  <strong>Audit outbox</strong>
                  <span>总数 {auditOutbox?.totalCount ?? 0}</span>
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">项目 ID</span>
                    <input
                      value={props.requeueDraft.projectId}
                      onChange={(event) => props.onRequeueDraftChange((current) => ({ ...current, projectId: event.target.value }))}
                      placeholder="project UUID"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">状态</span>
                    <select
                      value={props.requeueDraft.status}
                      onChange={(event) => props.onRequeueDraftChange((current) => ({ ...current, status: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {auditOutboxReplayStatuses.map((status) => (
                        <option key={status} value={status}>{status}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">上限</span>
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
                  <span className="field-label">原因</span>
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
                    重新排队
                  </button>
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
                  <strong>队列告警订阅</strong>
                  <span>{queueAlerts?.subscriptionCount ?? props.queueAlertSubscriptions.length}</span>
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">项目 ID</span>
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
                      placeholder="可为空"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">类型</span>
                    <select
                      value={props.queueAlertSubscriptionDraft.alertType}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, alertType: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {queueAlertTypes.map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">渠道</span>
                    <select
                      value={props.queueAlertSubscriptionDraft.channel}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, channel: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {queueAlertChannels.map((channel) => (
                        <option key={channel} value={channel}>{channel}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">阈值秒数</span>
                    <input
                      type="number"
                      min={0}
                      max={86400}
                      value={props.queueAlertSubscriptionDraft.thresholdSeconds}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, thresholdSeconds: event.target.value }))}
                      placeholder="使用系统阈值"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">启用</span>
                    <input
                      type="checkbox"
                      checked={props.queueAlertSubscriptionDraft.enabled}
                      onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, enabled: event.target.checked }))}
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">目标引用</span>
                  <input
                    value={props.queueAlertSubscriptionDraft.targetRef}
                    onChange={(event) => props.onQueueAlertSubscriptionDraftChange((current) => ({ ...current, targetRef: event.target.value }))}
                    disabled={!props.canPolicyManage}
                  />
                </label>
                <div className="toolbar-actions test-design-cross-wp-actions">
                  <button className="btn btn-primary btn-sm" type="submit" disabled={!canQueueAlertSave}>
                    <Save size={15} />
                    保存订阅
                  </button>
                  {props.queueAlertSubscriptionResult && (
                    <span className="test-design-cross-wp-result">
                      {props.queueAlertSubscriptionResult.alertType} · {props.queueAlertSubscriptionResult.channel}
                    </span>
                  )}
                </div>
              </form>

              <form className="test-design-cross-wp-group" onSubmit={props.onQueuedEventReplaySubmit}>
                <div className="test-design-evaluation-list-heading">
                  <strong>人工重放</strong>
                  <span>{queueAlerts?.queuedTaskCount ?? 0} / {queueAlerts?.publishQueuedCandidateCount ?? 0}</span>
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">项目 ID</span>
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
                      placeholder="可为空"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">类型</span>
                    <select
                      value={props.queuedEventReplayDraft.replayType}
                      onChange={(event) => props.onQueuedEventReplayDraftChange((current) => ({ ...current, replayType: event.target.value }))}
                      disabled={!props.canPolicyManage}
                    >
                      {queuedEventReplayTypes.map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">上限</span>
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
                  <span className="field-label">原因</span>
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
                    重放队列
                  </button>
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
                  <strong>补偿运行手册</strong>
                  <span>{runbook?.effectiveBatchSize ?? 0} · {runbook?.eligibleCandidateCount ?? 0}</span>
                </div>
                <div className="test-design-cross-wp-readiness">
                  {(runbook?.steps ?? []).map((step) => (
                    <span className={`test-design-quality-chip tone-${step.tone}`} key={step.code}>
                      {step.label} {step.ready ? 'ready' : 'blocked'}
                    </span>
                  ))}
                </div>
                <div className="form-grid test-design-cross-wp-requeue-grid">
                  <label className="field">
                    <span className="field-label">项目 ID</span>
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
                      placeholder="可为空"
                      disabled={!props.canPolicyManage}
                    />
                  </label>
                  <label className="field">
                    <span className="field-label">上限</span>
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
                  <span className="field-label">原因</span>
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
                    运行补偿
                  </button>
                  {props.publishCompensationRunResult && (
                    <span className="test-design-cross-wp-result">
                      {props.publishCompensationRunResult.scannedCandidates} · {props.publishCompensationRunResult.succeededCandidates}
                    </span>
                  )}
                </div>
              </form>

              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>运营审计报表</strong>
                  <span>{auditReport?.totalOperationCount ?? 0}</span>
                </div>
                <div className="test-design-quality-metrics">
                  <div className="test-design-quality-metric tone-info">
                    <span>成功</span>
                    <strong>{auditReport?.successCount ?? 0}</strong>
                    <small>失败 {auditReport?.failedCount ?? 0} · 拒绝 {auditReport?.deniedCount ?? 0}</small>
                  </div>
                  <div className="test-design-quality-metric tone-info">
                    <span>订阅变更</span>
                    <strong>{auditReport?.queueAlertSubscriptionMutationCount ?? 0}</strong>
                    <small>queued replay {auditReport?.queuedEventReplayCount ?? 0}</small>
                  </div>
                  <div className="test-design-quality-metric tone-info">
                    <span>补偿运行</span>
                    <strong>{auditReport?.publishCompensationRunCount ?? 0}</strong>
                    <small>outbox 重排 {auditReport?.auditOutboxRequeueCount ?? 0}</small>
                  </div>
                </div>
                <div className="test-design-cross-wp-readiness">
                  <span className={`test-design-quality-chip tone-${auditReport?.aggregateOnly && !auditReport?.detailRowsExported ? 'success' : 'warning'}`}>
                    aggregate-only {auditReport?.aggregateOnly && !auditReport?.detailRowsExported ? 'on' : 'check'}
                  </span>
                  <span className={`test-design-quality-chip tone-${auditReport?.exportSupported ? 'success' : 'warning'}`}>
                    export {auditReport?.exportSupported ? 'ready' : 'blocked'}
                  </span>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>最近操作</span>
                  <strong>{auditReport?.latestOperationAt ?? '-'}</strong>
                  <small>trace/actor/detail 保持聚合</small>
                </div>
              </div>
            </div>

            <div className="test-design-cross-wp-grid">
              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>审计报表模板</strong>
                  <span>{auditTemplate?.templateVersion ?? '-'}</span>
                </div>
                <div className="test-design-cross-wp-readiness">
                  <span className={`test-design-quality-chip tone-${auditTemplate?.aggregateOnly && !auditTemplate?.identifierValuesExported ? 'success' : 'warning'}`}>
                    aggregate-only {auditTemplate?.aggregateOnly ? 'on' : 'check'}
                  </span>
                  <span className={`test-design-quality-chip tone-${auditTemplate?.modelObservationDrilldownSupported ? 'success' : 'warning'}`}>
                    model drilldown {auditTemplate?.modelObservationDrilldownSupported ? 'ready' : 'blocked'}
                  </span>
                  <span className={`test-design-quality-chip tone-${auditTemplate?.crossWpDetailReportSupported ? 'success' : 'warning'}`}>
                    detail report {auditTemplate?.crossWpDetailReportSupported ? 'ready' : 'blocked'}
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
                  {!auditTemplate?.sections.length && <div className="notice info">暂无模板字段</div>}
                </div>
              </div>

              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>模型观测聚合钻取</strong>
                  <span>{modelDrilldown?.totalInvocationCount ?? 0}</span>
                </div>
                <div className="test-design-quality-metrics">
                  <div className="test-design-quality-metric tone-info">
                    <span>token</span>
                    <strong>{(modelDrilldown?.inputTokenTotal ?? 0) + (modelDrilldown?.outputTokenTotal ?? 0)}</strong>
                    <small>输入 {modelDrilldown?.inputTokenTotal ?? 0} · 输出 {modelDrilldown?.outputTokenTotal ?? 0}</small>
                  </div>
                  <div className="test-design-quality-metric tone-info">
                    <span>耗时/成本</span>
                    <strong>{modelDrilldown?.averageLatencyMs ?? 0}ms</strong>
                    <small>cost {modelDrilldown?.totalCostText ?? '0'}</small>
                  </div>
                  <div className={`test-design-quality-metric tone-${(modelDrilldown?.failedCount ?? 0) + (modelDrilldown?.blockedCount ?? 0) > 0 ? 'warning' : 'success'}`}>
                    <span>失败/阻断</span>
                    <strong>{(modelDrilldown?.failedCount ?? 0) + (modelDrilldown?.blockedCount ?? 0)}</strong>
                    <small>trace 信号 {modelDrilldown?.traceSignalCount ?? 0} · job {modelDrilldown?.jobSignalCount ?? 0}</small>
                  </div>
                </div>
                <div className="test-design-cross-wp-list">
                  {(modelDrilldown?.buckets ?? []).slice(0, 8).map((bucket) => (
                    <div className="test-design-cross-wp-row" key={`${bucket.dimension}-${bucket.bucketKey}`}>
                      <span>
                        <strong>{bucket.dimension} · {bucket.bucketLabel}</strong>
                        <em>成功 {bucket.succeededCount} · 失败 {bucket.failedCount} · 阻断 {bucket.blockedCount}</em>
                        <small>token {bucket.inputTokenTotal + bucket.outputTokenTotal} · avg {bucket.averageLatencyMs}ms</small>
                      </span>
                      <span className="badge badge-info">{bucket.invocationCount}</span>
                    </div>
                  ))}
                  {!modelDrilldown?.buckets.length && <div className="notice info">暂无模型观测聚合</div>}
                </div>
              </div>
            </div>

            <div className="test-design-cross-wp-group">
              <div className="test-design-evaluation-list-heading">
                <strong>跨 WP 明细审计报表</strong>
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
                      <em>{row.status} · 成功 {row.successCount} · 失败 {row.failedCount} · 告警 {row.warningCount}</em>
                      <small>{row.latestEventAt ?? '-'}</small>
                    </span>
                    <span className="badge badge-info">{row.eventCount}</span>
                  </div>
                ))}
                {!detailAuditReport?.rows.length && <div className="notice info">暂无跨 WP 明细审计行</div>}
              </div>
            </div>

            {queueAlerts && (
              <div className="test-design-cross-wp-grid">
                <div className="test-design-cross-wp-group">
                  <div className="test-design-evaluation-list-heading">
                    <strong>队列聚合</strong>
                    <span>{queueAlerts.generatedAt ?? '-'}</span>
                  </div>
                  <div className="test-design-cross-wp-row">
                    <span>生成排队</span>
                    <strong>{queueAlerts.queuedTaskCount}</strong>
                    <small>最老 {queueAlerts.oldestGenerationQueuedAgeSeconds}s · 阈值 {queueAlerts.generationQueueLagWarningSeconds}s</small>
                  </div>
                  <div className="test-design-cross-wp-row">
                    <span>发布排队</span>
                    <strong>{queueAlerts.publishQueuedCandidateCount}</strong>
                    <small>最老 {queueAlerts.oldestPublishQueuedAgeSeconds}s · 阈值 {queueAlerts.publishQueueLagWarningSeconds}s</small>
                  </div>
                  <div className="test-design-cross-wp-row">
                    <span>补偿待处理</span>
                    <strong>{queueAlerts.compensationEligibleCandidateCount}</strong>
                    <small>运行超时 {queueAlerts.staleRunningTaskCount} · 发布超时 {queueAlerts.stalePublishingCandidateCount}</small>
                  </div>
                </div>
                <div className="test-design-cross-wp-group">
                  <div className="test-design-evaluation-list-heading">
                    <strong>订阅清单</strong>
                    <span>{props.queueAlertSubscriptions.length}</span>
                  </div>
                  <div className="test-design-cross-wp-list">
                    {props.queueAlertSubscriptions.length ? (
                      props.queueAlertSubscriptions.map((subscription) => (
                        <div className="test-design-cross-wp-row" key={subscription.id}>
                          <span>
                            <strong>{subscription.alertType}</strong>
                            <em>{subscription.channel} · {subscription.targetRef}</em>
                            <small>{subscription.promptKey || '项目级'}</small>
                          </span>
                          <span className={`badge badge-${subscription.enabled ? 'success' : 'neutral'}`}>
                            {subscription.enabled ? 'ENABLED' : 'DISABLED'}
                          </span>
                        </div>
                      ))
                    ) : (
                      <div className="notice info">暂无队列告警订阅</div>
                    )}
                  </div>
                </div>
              </div>
            )}

            {props.publishCompensationRunResult && (
              <div className="test-design-cross-wp-group">
                <div className="test-design-evaluation-list-heading">
                  <strong>补偿运行结果</strong>
                  <span>{props.publishCompensationRunResult.runAt ?? '-'}</span>
                </div>
                <div className="test-design-cross-wp-row">
                  <span>扫描/成功</span>
                  <strong>{props.publishCompensationRunResult.scannedCandidates}</strong>
                  <small>{props.publishCompensationRunResult.succeededCandidates} 成功 · {props.publishCompensationRunResult.failedCandidates} 失败 · {props.publishCompensationRunResult.skippedCandidates} 跳过</small>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="notice info">暂无跨 WP 运营数据</div>
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
