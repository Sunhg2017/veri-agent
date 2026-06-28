import { RefreshCw } from 'lucide-react';
import type { ReactNode } from 'react';
import type { TestDesignAuditSummary } from '../testDesignAuditSummary';
import type { TestDesignPromptTrendSummary } from '../testDesignPromptTrend';
import type { TestDesignQualitySummary } from '../testDesignQualitySummary';
import type { TestDesignReviewSummary } from '../testDesignReviewSummary';
import { fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

export type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

export function Metric(props: { icon: ReactNode; label: string; value: string; desc: string }) {
  return (
    <div className="metric-card">
      <div className="metric-icon info">{props.icon}</div>
      <div className="metric-body">
        <span className="metric-label">{props.label}</span>
        <strong className="metric-value">{props.value}</strong>
        <span className="metric-desc">{props.desc}</span>
      </div>
    </div>
  );
}

export function QualitySummaryPanel(props: {
  scopeLabel: string;
  selectedTaskId: string;
  summary: TestDesignQualitySummary;
}) {
  return (
    <section className="panel test-design-quality-dashboard">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1534')}</h2>
          <p className="panel-desc">{props.scopeLabel}</p>
        </div>
        {props.summary.readiness ? (
          <span className={`badge badge-${badgeTone(props.summary.readiness.tone)}`}>
            {props.summary.readiness.label}
          </span>
        ) : props.summary.warnings.length > 0 && (
          <span className="badge badge-warning">{translate('auto.k0366')}{props.summary.warnings.length}</span>
        )}
      </div>
      <div className="panel-body compact">
        {props.selectedTaskId ? (
          <>
            {props.summary.readiness && (
              <div className={`test-design-readiness tone-${props.summary.readiness.tone}`}>
                <strong>{props.summary.readiness.label}</strong>
                <span>{translate('auto.k1000')}{props.summary.readiness.blockingCount} {translate('auto.k1535')}{props.summary.readiness.warningCount}</span>
              </div>
            )}
            <div className="test-design-quality-metrics">
              {props.summary.metrics.map((metric) => (
                <div className={`test-design-quality-metric tone-${metric.tone}`} key={metric.label}>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  <small>{metric.desc}</small>
                </div>
              ))}
            </div>
            {props.summary.readiness && (
              <div className="test-design-quality-distribution">
                <span className="test-design-quality-distribution-label">{translate('auto.k1536')}</span>
                <div className="test-design-quality-distribution-items">
                  {props.summary.readiness.checks.map((check) => (
                    <span className={`test-design-quality-chip tone-${check.tone}`} key={check.code} title={check.desc}>
                      {check.label} {check.desc}
                    </span>
                  ))}
                </div>
              </div>
            )}
            <div className="test-design-quality-distributions">
              {props.summary.distributions.map((group) => (
                <div className="test-design-quality-distribution" key={group.label}>
                  <span className="test-design-quality-distribution-label">{group.label}</span>
                  <div className="test-design-quality-distribution-items">
                    {group.items.length ? (
                      group.items.map((item) => (
                        <span className={`test-design-quality-chip tone-${item.tone}`} key={item.label}>
                          {item.label} {item.count} · {item.percent}%
                        </span>
                      ))
                    ) : (
                      <span className="field-hint">{translate('auto.k1537')}</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
            {props.summary.warnings.length > 0 && (
              <div className="test-design-quality-warnings">
                {props.summary.warnings.map((warning) => (
                  <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                    {warning.label} {warning.count}
                  </span>
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="notice info">{translate('auto.k1538')}</div>
        )}
      </div>
    </section>
  );
}

export function PromptTrendPanel(props: {
  state: WorkState;
  summary: TestDesignPromptTrendSummary;
  onRefresh: () => void;
}) {
  return (
    <section className="panel test-design-prompt-trend">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1539')}</h2>
          <p className="panel-desc">{props.summary.scopeLabel}</p>
        </div>
        <button className="btn btn-secondary btn-sm" type="button" disabled={props.state.loading} onClick={props.onRefresh}>
          <RefreshCw size={15} />
          {translate('auto.k0170')}</button>
      </div>
      <div className="panel-body compact">
        <StateLine state={props.state} />
        {props.summary.buckets.length ? (
          <>
            <div className="test-design-quality-metrics">
              {props.summary.metrics.map((metric) => (
                <div className={`test-design-quality-metric tone-${metric.tone}`} key={metric.label}>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  <small>{metric.desc}</small>
                </div>
              ))}
            </div>
            {props.summary.readinessDistribution.length > 0 && (
              <div className="test-design-prompt-readiness-summary">
                {props.summary.readinessDistribution.map((item) => (
                  <span className={`test-design-quality-chip tone-${item.tone}`} key={item.label}>
                    {item.label} {item.count} · {item.percent}%
                  </span>
                ))}
              </div>
            )}
            <div className="test-design-prompt-trend-list">
              {props.summary.buckets.map((bucket) => (
                <div className={`test-design-prompt-trend-row tone-${bucket.tone}`} key={bucket.label}>
                  <div>
                    <strong>{bucket.label}</strong>
                    <span>{bucket.taskCount} {translate('auto.k1540')}{bucket.candidateCount} {translate('auto.k1428')}</span>
                    <span className={`test-design-prompt-readiness tone-${bucket.readinessTone}`}>
                      {bucket.readinessLabel} · {bucket.readinessText}
                    </span>
                  </div>
                  <div className="test-design-prompt-trend-row-metrics">
                    <span>{bucket.qualityText}</span>
                    <span>{bucket.feedbackText}</span>
                    <span>{bucket.riskText}</span>
                  </div>
                </div>
              ))}
            </div>
            {props.summary.warnings.length > 0 && (
              <div className="test-design-quality-warnings">
                {props.summary.warnings.map((warning) => (
                  <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                    {warning.label} {warning.count}
                  </span>
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="notice info">{translate('auto.k1541')}</div>
        )}
      </div>
    </section>
  );
}

export function AuditSummaryPanel(props: {
  state: WorkState;
  summary: TestDesignAuditSummary;
  selectedTaskId: string;
  onRefresh: () => void;
}) {
  return (
    <section className="panel test-design-audit-summary">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1542')}</h2>
          <p className="panel-desc">{props.selectedTaskId ? props.summary.scopeLabel : translate('auto.k1538')}</p>
        </div>
        <button className="btn btn-secondary btn-sm" type="button" disabled={!props.selectedTaskId || props.state.loading} onClick={props.onRefresh}>
          <RefreshCw size={15} />
          {translate('auto.k0170')}</button>
      </div>
      <div className="panel-body compact">
        <StateLine state={props.state} />
        {props.selectedTaskId ? (
          <>
            <div className="test-design-quality-metrics test-design-audit-metrics">
              {props.summary.metrics.map((metric) => (
                <div className={`test-design-quality-metric tone-${metric.tone}`} key={metric.label}>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  <small>{metric.desc}</small>
                </div>
              ))}
            </div>
            {props.summary.timeline.length ? (
              <div className="test-design-audit-timeline">
                {props.summary.timeline.map((item, index) => (
                  <div className={`test-design-audit-event tone-${item.tone}`} key={`${item.source}-${item.action}-${item.createdAt ?? index}`}>
                    <strong>{item.label}</strong>
                    <span>{item.metaText}</span>
                    <em>{item.createdAt ?? '-'}</em>
                  </div>
                ))}
              </div>
            ) : (
              <div className="notice info">{translate('auto.k1543')}</div>
            )}
            {props.summary.warnings.length > 0 && (
              <div className="test-design-quality-warnings">
                {props.summary.warnings.map((warning) => (
                  <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                    {warning.label} {warning.count}
                  </span>
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="notice info">{translate('auto.k1538')}</div>
        )}
      </div>
    </section>
  );
}

export function ReviewSummaryPanel(props: {
  scopeLabel: string;
  selectedTaskId: string;
  summary: TestDesignReviewSummary;
}) {
  const warningCount = props.summary.warnings.length + props.summary.feedbackLoop.warnings.length;
  return (
    <div className="test-design-review-summary">
      <div className="test-design-review-summary-heading">
        <span>{props.scopeLabel}</span>
        {warningCount > 0 && (
          <span className="badge badge-warning">{translate('auto.k1544')}{warningCount}</span>
        )}
      </div>
      {props.selectedTaskId ? (
        <>
          <div className="test-design-quality-metrics test-design-review-summary-metrics">
            {props.summary.metrics.map((metric) => (
              <div className={`test-design-quality-metric tone-${metric.tone}`} key={metric.label}>
                <span>{metric.label}</span>
                <strong>{metric.value}</strong>
                <small>{metric.desc}</small>
              </div>
            ))}
          </div>
          <div className="test-design-quality-distributions">
            {props.summary.groups.map((group) => (
              <div className="test-design-quality-distribution" key={group.label}>
                <span className="test-design-quality-distribution-label">{group.label}</span>
                <div className="test-design-quality-distribution-items">
                  {group.items.length ? (
                    group.items.map((item) => (
                      <span className={`test-design-quality-chip tone-${item.tone}`} key={item.label}>
                        {item.label} {item.count} · {item.percent}%
                      </span>
                    ))
                  ) : (
                    <span className="field-hint">{translate('auto.k1537')}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
          <div className="test-design-feedback-loop">
            <div className="test-design-feedback-loop-heading">
              <strong>{translate('auto.k1545')}</strong>
              <span className={`badge badge-${badgeTone(props.summary.feedbackLoop.tone)}`}>
                {props.summary.feedbackLoop.promptTuningSignalCount ? translate('auto.k1546') : translate('auto.k1547')}
              </span>
            </div>
            <div className="test-design-quality-distribution-items">
              {props.summary.feedbackLoop.items.map((item) => (
                <span className={`test-design-quality-chip tone-${item.tone}`} key={item.label}>
                  {item.label} {item.count} · {item.percent}%
                </span>
              ))}
            </div>
            {props.summary.feedbackLoop.warnings.length > 0 && (
              <div className="test-design-quality-warnings test-design-feedback-loop-warnings">
                {props.summary.feedbackLoop.warnings.map((warning) => (
                  <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                    {warning.label} {warning.count}
                  </span>
                ))}
              </div>
            )}
          </div>
          {props.summary.warnings.length > 0 && (
            <div className="test-design-quality-warnings">
              {props.summary.warnings.map((warning) => (
                <span className={`test-design-quality-chip tone-${warning.tone}`} key={warning.label}>
                  {warning.label} {warning.count}
                </span>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="notice info">{translate('auto.k1538')}</div>
      )}
    </div>
  );
}

export function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">{translate('auto.k0458')}</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">{fieldLabel('traceId')}：{props.state.traceId}</span>;
  }
  return null;
}

function badgeTone(tone: string) {
  if (tone === 'success' || tone === 'warning' || tone === 'danger' || tone === 'info') {
    return tone;
  }
  return 'neutral';
}
