import { ScrollText } from 'lucide-react';
import { type AuditLogView, type AuditOutboxView } from '../../api/management';
import { canUseButton, hasPermission } from '../../permissions';
import { fieldLabel } from '../../platform/dictionaries';
import { translate } from '../../platform/i18n';
import { InputControl, SelectControl } from '../../components/ui';
import { StatusBadge, type ManagementPageProps } from '../../components/management/shared';

/** 审计页：审计日志列表 + 导出 + Outbox 事件侧栏 */
export function AuditPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser, auditExportState, onAuditExport } = props;

  const canExport = canUseButton(currentUser, 'audit:export');
  const canViewOutbox = hasPermission(currentUser, 'audit:read');

  return (
    <div className="content-grid">
      <div className="panel">
        <div className="panel-header">
          <div>
            <div className="management-section-heading">
              <div className="section-icon management-section-icon"><ScrollText size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0356')}</div>
                <h2 className="panel-title">{translate('auto.k0035')}</h2>
              </div>
            </div>
          </div>
          <div className="toolbar-actions">
            <button className="btn btn-secondary btn-sm" onClick={onAuditExport} disabled={!signedIn || auditExportState.loading || !canExport}>
              {auditExportState.loading ? translate('auto.k0357') : translate('auto.k0358')}
            </button>
            <button className="btn btn-secondary btn-sm" onClick={props.onRefresh} disabled={loadState.loading}>{translate('auto.k0170')}</button>
          </div>
        </div>
        <div className="panel-body">
          {loadState.error && <div className="notice error management-notice">{loadState.error}</div>}
          {data.auditLogs.length === 0 && !loadState.loading ? (
            <div className="empty-state">
              <div className="empty-state-icon"><ScrollText size={32} opacity={0.4} /></div>
              <strong>{translate('auto.k0359')}</strong>
              <span>{translate('auto.k0360')}</span>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{translate('auto.k0361')}</th>
                    <th>{translate('auto.k0362')}</th>
                    <th>{translate('auto.k0363')}</th>
                    <th>{translate('auto.k0364')}</th>
                    <th>{translate('auto.k0365')}</th>
                    <th>{translate('auto.k0333')}</th>
                  </tr>
                </thead>
                <tbody>
                  {loadState.loading ? (
                    <tr><td colSpan={6}><div className="skeleton skeleton-text management-skeleton-row" /></td></tr>
                  ) : (
                    data.auditLogs.map((log: AuditLogView, idx: number) => (
                      <tr key={idx}>
                        <td className="text-sm">{log.time}</td>
                        <td>{log.actor}</td>
                        <td>{log.action}</td>
                        <td>
                          <div className="management-primary-text">{log.target}</div>
                        </td>
                        <td><StatusBadge status={log.result} /></td>
                        <td className="text-sm text-secondary management-ellipsis-cell">{log.target || '-'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <div className="side-stack">
        {canViewOutbox && <AuditOutboxPanel {...props} />}
      </div>
    </div>
  );
}

/* ===================== Audit Outbox Panel ===================== */

function AuditOutboxPanel(props: ManagementPageProps) {
  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0366')}</div>
          <h3 className="panel-title management-side-title">{translate('auto.k2946')}</h3>
        </div>

        <div className="management-outbox-filter-grid">
          <SelectControl
            value={props.auditOutboxFilters.status}
            onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, status: e.target.value })}
            className="management-compact-control"
          >
            <option value="">{translate('auto.k0367')}</option>
            <option value="PENDING">{translate('auto.k0366')}</option>
            <option value="SUCCESS">{translate('auto.k0368')}</option>
            <option value="FAILED">{translate('auto.k0369')}</option>
          </SelectControl>
          <InputControl
            type="text" placeholder={fieldLabel('traceId')}
            value={props.auditOutboxFilters.traceId}
            onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, traceId: e.target.value })}
            className="management-compact-control"
          />
        </div>
        <InputControl
          type="text" placeholder={translate('auto.k0370')}
          value={props.auditOutboxFilters.search}
          onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, search: e.target.value })}
          className="management-compact-control management-search-control"
        />

        <button className="btn btn-secondary btn-sm management-full-action" onClick={() => props.onAuditOutboxRefresh()} disabled={props.auditOutboxLoad.loading}>
          {props.auditOutboxLoad.loading ? translate('auto.k0371') : translate('auto.k0372')}
        </button>

        {props.auditOutboxLoad.error && (
          <div className="notice error management-notice-sm">{props.auditOutboxLoad.error}</div>
        )}

        <div className="management-item-list">
          {props.data.auditOutbox.length === 0 && !props.auditOutboxLoad.loading ? (
            <div className="text-tertiary text-sm">{translate('auto.k0373')}</div>
          ) : (
            props.data.auditOutbox.map((item: AuditOutboxView, idx: number) => (
              <div key={idx} className="management-outbox-item">
                <div className="management-result-head">
                  <strong className="management-outbox-title">{item.eventAction}</strong>
                  <StatusBadge status={item.status} />
                </div>
                <div className="text-tertiary text-xs management-meta-line">
                  {item.resourceType}/{item.resourceId}
                </div>
                {item.lastError && (
                  <div className="text-xs management-error-line">{item.lastError}</div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
