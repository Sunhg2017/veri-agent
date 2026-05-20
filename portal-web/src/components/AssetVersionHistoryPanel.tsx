import { History, RefreshCw } from 'lucide-react';
import type { AssetVersionHistoryView } from '../api/assets';

type VersionHistoryState = {
  loading: boolean;
  error?: string;
  traceId?: string;
};

type AssetVersionHistoryPanelProps = {
  disabled?: boolean;
  items: AssetVersionHistoryView[];
  onRefresh: () => void;
  state: VersionHistoryState;
};

const HISTORY_JSON_LIMIT = 5000;

export function AssetVersionHistoryPanel(props: AssetVersionHistoryPanelProps) {
  return (
    <div className="asset-version-history">
      <div className="panel-title-row">
        <strong>版本历史</strong>
        <button className="mini-button" type="button" disabled={props.disabled || props.state.loading} onClick={props.onRefresh}>
          <RefreshCw size={14} />
          刷新
        </button>
      </div>

      {props.state.loading ? (
        <div className="empty-state compact">
          <History size={20} />
          <div>
            <strong>正在加载历史</strong>
            <span>读取最新版本链路</span>
          </div>
        </div>
      ) : props.items.length > 0 ? (
        <div className="asset-version-list">
          {props.items.map((item, index) => (
            <details className="asset-version-row" key={item.id || `${item.assetType}-${item.assetId}-${item.version}`} open={index === 0}>
              <summary>
                <span>
                  <strong>v{item.version || '-'}</strong>
                  <em>{item.changeType}</em>
                </span>
                <small>{formatDate(item.createdAt)}</small>
              </summary>
              <div className="asset-version-meta">
                <div>
                  <span>actor</span>
                  <em>{item.actor ?? '-'}</em>
                </div>
                <div>
                  <span>traceId</span>
                  <em>{item.traceId ?? '-'}</em>
                </div>
                <div>
                  <span>changedFields</span>
                  <em>{item.changedFields.length ? item.changedFields.join(', ') : '-'}</em>
                </div>
              </div>
              <div className="asset-version-json-grid">
                <div>
                  <strong>diff</strong>
                  <pre>{formatHistoryJson(item.diff)}</pre>
                </div>
                <div>
                  <strong>snapshot</strong>
                  <pre>{formatHistoryJson(item.snapshot)}</pre>
                </div>
              </div>
            </details>
          ))}
        </div>
      ) : (
        <div className="empty-state compact">
          <History size={20} />
          <div>
            <strong>暂无版本历史</strong>
            <span>{props.state.error ?? '当前资产尚未生成历史记录'}</span>
          </div>
        </div>
      )}

      {props.state.error && props.items.length > 0 && <span className="document-state-line error">{props.state.error}</span>}
      {props.state.traceId && !props.state.error && <span className="document-state-line">Trace ID：{props.state.traceId}</span>}
    </div>
  );
}

function formatHistoryJson(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const formatted = typeof value === 'string' ? value : JSON.stringify(value, null, 2) ?? String(value);
  return formatted.length > HISTORY_JSON_LIMIT
    ? `${formatted.slice(0, HISTORY_JSON_LIMIT)}\n... 已截断`
    : formatted;
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
