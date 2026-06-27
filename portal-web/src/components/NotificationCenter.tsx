import { Bell, CheckCheck, LoaderCircle } from 'lucide-react';
import dayjs from 'dayjs';
import { useEffect, useMemo, useRef } from 'react';
import type { UserNotification } from '../api/notifications';
import { translate } from '../platform/i18n';

export interface NotificationCenterProps {
  open: boolean;
  loading: boolean;
  unreadCount: number;
  items: UserNotification[];
  onToggle: () => void;
  onClose: () => void;
  onMarkRead: (id: string) => void;
  onMarkAllRead: () => void;
}

function notificationTimeLabel(value?: string) {
  if (!value) return '';
  const date = dayjs(value);
  if (!date.isValid()) return '';
  return date.format('MM/DD HH:mm');
}

export function NotificationCenter(props: NotificationCenterProps) {
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!props.open) return undefined;
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        props.onClose();
      }
    }
    function onEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        props.onClose();
      }
    }
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onEscape);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onEscape);
    };
  }, [props]);

  const unreadLabel = useMemo(() => {
    if (props.unreadCount <= 0) return '';
    return props.unreadCount > 99 ? '99+' : String(props.unreadCount);
  }, [props.unreadCount]);

  return (
    <div className="notification-center" ref={rootRef}>
      <button
        className="btn btn-ghost btn-sm btn-icon notification-trigger"
        type="button"
        onClick={props.onToggle}
        aria-label={translate('auto.k1081')}
        aria-expanded={props.open}
      >
        <Bell size={16} />
        {unreadLabel ? <span className="notification-badge">{unreadLabel}</span> : null}
      </button>

      {props.open ? (
        <div className="notification-panel">
          <div className="notification-panel-header">
            <div>
              <strong>{translate('auto.k1081')}</strong>
              <span>{props.unreadCount > 0 ? translate('auto.k1082', { value0: props.unreadCount }) : translate('auto.k1083')}</span>
            </div>
            <button
              className="btn btn-ghost btn-xs"
              type="button"
              onClick={props.onMarkAllRead}
              disabled={props.loading || props.unreadCount === 0}
            >
              <CheckCheck size={14} />
              {translate('auto.k1084')}</button>
          </div>

          {props.loading ? (
            <div className="notification-empty">
              <LoaderCircle size={16} className="spin" />
              <span>{translate('auto.k1085')}</span>
            </div>
          ) : props.items.length === 0 ? (
            <div className="notification-empty">
              <span>{translate('auto.k1086')}</span>
            </div>
          ) : (
            <div className="notification-list">
              {props.items.map((item) => (
                <article
                  key={item.id}
                  className={`notification-item${item.unread ? ' unread' : ''}`}
                >
                  <div className="notification-item-head">
                    <strong>{item.title}</strong>
                    <span>{notificationTimeLabel(item.createdAt)}</span>
                  </div>
                  <p>{item.body}</p>
                  <div className="notification-item-foot">
                    <span className={`status-badge ${item.unread ? 'danger' : 'neutral'}`}>
                      {item.unread ? translate('auto.k1087') : translate('auto.k1088')}
                    </span>
                    <div className="notification-item-actions">
                      {item.link ? (
                        <a className="btn btn-ghost btn-xs" href={item.link}>
                          {translate('auto.k1089')}</a>
                      ) : null}
                      {item.unread ? (
                        <button
                          className="btn btn-ghost btn-xs"
                          type="button"
                          onClick={() => props.onMarkRead(item.id)}
                        >
                          {translate('auto.k1090')}</button>
                      ) : null}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
