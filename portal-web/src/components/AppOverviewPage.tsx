import { Building2, ClipboardList, DatabaseZap, Link2 } from 'lucide-react';
import type { HealthResult } from '../api/health';
import type { ManagementData } from '../api/management';

/* ===================== Overview Page ===================== */

export function OverviewPage(props: {
  health: { loading: boolean; data?: HealthResult; error?: string };
  managementData: ManagementData;
}) {
  return (
    <div>
      <section className="metrics-grid">
        <div className="metric-card">
          <div className="metric-icon info"><Building2 size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">部 门</span>
            <strong className="metric-value">{props.managementData.departments.length}</strong>
            <div className="metric-desc">{countByStatus(props.managementData.departments, '同步正常')} 个同步正常</div>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon success"><DatabaseZap size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">项目空间</span>
            <strong className="metric-value">{props.managementData.projects.length}</strong>
            <div className="metric-desc">{countByStatus(props.managementData.projects, '进行中')} 个进行中</div>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon warning"><Link2 size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">集 成</span>
            <strong className="metric-value">{props.managementData.integrations.length}</strong>
            <div className="metric-desc">{countByStatus(props.managementData.integrations, '已连接')} 个已连接</div>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon"><ClipboardList size={20} /></div>
          <div className="metric-body">
            <span className="metric-label">审计事件</span>
            <strong className="metric-value">{props.managementData.auditLogs.length}</strong>
            <div className="metric-desc">当前工作区</div>
          </div>
        </div>
      </section>

      <div className="content-grid">
        <div className="panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">运行状态</h2>
              <p className="panel-desc">平台 API、资源摘要和审计入口当前状态。</p>
            </div>
          </div>
          <div className="panel-body">
            <div style={{ display: 'grid', gap: 12 }}>
              <DetailItem label="后端健康" value={props.health.data?.status ?? (props.health.loading ? '检查中...' : '不可用')} />
              <DetailItem label="服务名称" value={props.health.data?.service ?? 'platform-api'} />
            </div>
          </div>
        </div>

        <div className="side-stack">
          <div className="panel">
            <div className="panel-body">
              <div style={{ display: 'grid', gap: 8 }}>
                <div className="badge badge-primary" style={{ width: 'max-content' }}>WP1 · 平台管理</div>
                <p className="text-secondary text-sm" style={{ lineHeight: 1.6 }}>
                  组织、用户、角色权限治理 · 项目、应用、环境基础配置 · 外部集成、审计日志、系统设置
                </p>
              </div>
            </div>
          </div>
          {props.health.error && (
            <div className="notice error">
              <strong>健康检查异常</strong>
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
    <div style={{ display: 'flex', gap: 8, alignItems: 'baseline', padding: '4px 0', borderBottom: '1px solid var(--border-light)' }}>
      <span className="text-tertiary text-sm" style={{ flex: '0 0 80px' }}>{label}</span>
      <span style={{ fontSize: 14, overflowWrap: 'anywhere' }}>{value}</span>
    </div>
  );
}

function countByStatus(items: Array<{ status: string }>, status: string): number {
  return items.filter((item) => item.status === status).length;
}
