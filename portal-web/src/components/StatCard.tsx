import type { ReactNode } from 'react';

export type StatCardTone = 'primary' | 'success' | 'warning' | 'danger';

/**
 * 指标卡片：图标 + 标签 + 数值 + 辅助描述。
 * 配合 .stat-grid 容器使用，自动响应式换行。
 */
export function StatCard(props: {
  icon?: ReactNode;
  label: ReactNode;
  value: ReactNode;
  desc?: ReactNode;
  tone?: StatCardTone;
}) {
  const toneClass = props.tone && props.tone !== 'primary' ? ` ${props.tone}` : '';
  return (
    <div className="stat-card">
      {props.icon ? <div className={`stat-card-icon${toneClass}`}>{props.icon}</div> : null}
      <div className="stat-card-body">
        <div className="stat-card-label">{props.label}</div>
        <div className="stat-card-value">{props.value}</div>
        {props.desc ? <div className="stat-card-desc">{props.desc}</div> : null}
      </div>
    </div>
  );
}

export function StatGrid(props: { children: ReactNode }) {
  return <div className="stat-grid">{props.children}</div>;
}
