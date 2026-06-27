import { Inbox, LoaderCircle } from 'lucide-react';
import type { ReactNode } from 'react';
import { translate } from '../../platform/i18n';

export function Spinner({ label = translate('auto.k0168') }: { label?: string }) {
  return (
    <span className="ui-spinner" role="status" aria-live="polite">
      <LoaderCircle size={16} className="spin" />
      <span>{label}</span>
    </span>
  );
}

export function SkeletonBlock({ rows = 3 }: { rows?: number }) {
  return (
    <div className="ui-skeleton-block" aria-hidden="true">
      {Array.from({ length: rows }, (_, index) => (
        <span className="ui-skeleton-line" key={index} />
      ))}
    </div>
  );
}

export function EmptyState({
  action,
  description,
  title
}: {
  action?: ReactNode;
  description?: string;
  title: string;
}) {
  return (
    <div className="empty-state ui-empty-state">
      <div className="empty-state-icon">
        <Inbox size={20} />
      </div>
      <strong>{title}</strong>
      {description ? <span>{description}</span> : null}
      {action ? <div className="ui-empty-action">{action}</div> : null}
    </div>
  );
}
