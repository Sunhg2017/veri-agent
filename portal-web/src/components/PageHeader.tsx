import type { ReactNode } from 'react';

/**
 * 标准页头：标题 + 描述 + 右侧操作区。
 * 所有功能页面统一使用，保证企业级系统的视觉一致性。
 */
export function PageHeader(props: { title: ReactNode; description?: ReactNode; extra?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1 className="page-header-title">{props.title}</h1>
        {props.description ? <p className="page-header-desc">{props.description}</p> : null}
      </div>
      {props.extra ? <div className="page-header-actions">{props.extra}</div> : null}
    </div>
  );
}
