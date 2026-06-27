import { Building2, ClipboardList, DatabaseZap, Link2 } from 'lucide-react';
import type { HealthResult } from '../api/health';
import type { ManagementData } from '../api/management';
import { translate } from '../platform/i18n';

/* ===================== Overview Page ===================== */

export function OverviewPage(props: {
  health: { loading: boolean; data?: HealthResult; error?: string };
  managementData: ManagementData;
}) {
  return (
    <div className="overview-page">
      <section className="metrics-grid">
        <div className="metric-card">
          <div className="metric-icon info"><Building2 size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">{translate('auto.k0374')}</span>
            <strong className="metric-value">{props.managementData.departments.length}</strong>
            <div className="metric-desc">{countByStatus(props.managementData.departments, translate('auto.k0375'))} {translate('auto.k0376')}</div>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon success"><DatabaseZap size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">{translate('auto.k0027')}</span>
            <strong className="metric-value">{props.managementData.projects.length}</strong>
            <div className="metric-desc">{countByStatus(props.managementData.projects, translate('auto.k0377'))} {translate('auto.k0378')}</div>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon warning"><Link2 size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">{translate('auto.k0379')}</span>
            <strong className="metric-value">{props.managementData.integrations.length}</strong>
            <div className="metric-desc">{countByStatus(props.managementData.integrations, translate('auto.k0380'))} {translate('auto.k0381')}</div>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon"><ClipboardList size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">{translate('auto.k0382')}</span>
            <strong className="metric-value">{props.managementData.auditLogs.length}</strong>
            <div className="metric-desc">{translate('auto.k0383')}</div>
          </div>
        </div>
      </section>

      <div className="content-grid">
        <div className="panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">{translate('auto.k0384')}</h2>
              <p className="panel-desc">{translate('auto.k0385')}</p>
            </div>
          </div>
          <div className="panel-body">
            <div className="overview-status-list">
              <DetailItem label={translate('auto.k0386')} value={props.health.data?.status ?? (props.health.loading ? translate('auto.k0349') : translate('auto.k0387'))} />
              <DetailItem label={translate('auto.k0388')} value={props.health.data?.service ?? 'platform-api'} />
            </div>
          </div>
        </div>

        <div className="side-stack">
          <div className="panel">
            <div className="panel-body">
              <div className="overview-scope-card">
                <div className="badge badge-primary">{translate('auto.k0389')}</div>
                <p className="text-secondary text-sm">
                  {translate('auto.k0390')}</p>
              </div>
            </div>
          </div>
          {props.health.error && (
            <div className="notice error">
              <strong>{translate('auto.k0391')}</strong>
              <span>{props.health.error}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="overview-detail-item">
      <span className="text-tertiary text-sm">{label}</span>
      <span>{value}</span>
    </div>
  );
}

function countByStatus(items: Array<{ status: string }>, status: string): number {
  return items.filter((item) => item.status === status).length;
}
