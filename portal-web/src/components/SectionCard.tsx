import type { ReactNode } from 'react';

/**
 * 内容区块卡片：统一的白色卡片容器，带可选标题栏。
 * 用于将页面内容划分为清晰的区块。
 */
export function SectionCard(props: {
  title?: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  bodyPadding?: boolean;
}) {
  return (
    <section className="section-card">
      {props.title || props.extra ? (
        <header className="section-card-header">
          <h2 className="section-card-title">{props.title}</h2>
          {props.extra ? <div className="section-card-extra">{props.extra}</div> : null}
        </header>
      ) : null}
      <div className="section-card-body" style={props.bodyPadding === false ? { padding: 0 } : undefined}>
        {props.children}
      </div>
    </section>
  );
}
