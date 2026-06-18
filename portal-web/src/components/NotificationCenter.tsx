import { Bell, CheckCheck, LoaderCircle } from 'lucide-react';
import { useEffect, useMemo, useRef } from 'react';
import type { UserNotification } from '../api/notifications';

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
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
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
        aria-label="站内通知"
        aria-expanded={props.open}
      >
        <Bell size={16} />
        {unreadLabel ? <span className="notification-badge">{unreadLabel}</span> : null}
      </button>

      {props.open ? (
        <div className="notification-panel">
          <div className="notification-panel-header">
            <div>
              <strong>站内通知</strong>
              <span>{props.unreadCount > 0 ? `未读 ${props.unreadCount}` : '已全部读完'}</span>
            </div>
            <button
              className="btn btn-ghost btn-xs"
              type="button"
              onClick={props.onMarkAllRead}
              disabled={props.loading || props.unreadCount === 0}
            >
              <CheckCheck size={14} />
              全部已读
            </button>
          </div>

          {props.loading ? (
            <div className="notification-empty">
              <LoaderCircle size={16} className="spin" />
              <span>通知加载中...</span>
            </div>
          ) : props.items.length === 0 ? (
            <div className="notification-empty">
              <span>暂无站内通知</span>
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
                      {item.unread ? '未读' : '已读'}
                    </span>
                    <div className="notification-item-actions">
                      {item.link ? (
                        <a className="btn btn-ghost btn-xs" href={item.link}>
                          查看
                        </a>
                      ) : null}
                      {item.unread ? (
                        <button
                          className="btn btn-ghost btn-xs"
                          type="button"
                          onClick={() => props.onMarkRead(item.id)}
                        >
                          标记已读
                        </button>
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
