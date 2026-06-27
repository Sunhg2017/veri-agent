import { History, RefreshCw, RotateCcw } from 'lucide-react';
import type { AssetVersionHistoryView } from '../api/assets';
import { assetVersionDiffRows, formatAssetVersionDiffValue } from '../assetVersionDiff';
import { translate } from '../platform/i18n';

type VersionHistoryState = {
  loading: boolean;
  error?: string;
  traceId?: string;
};

type AssetVersionHistoryPanelProps = {
  currentVersion?: number;
  disabled?: boolean;
  items: AssetVersionHistoryView[];
  onRollback?: (version: number) => void;
  onRefresh: () => void;
  state: VersionHistoryState;
};

const HISTORY_JSON_LIMIT = 5000;

export function AssetVersionHistoryPanel(props: AssetVersionHistoryPanelProps) {
  return (
    <div className="asset-version-history">
      <div className="panel-title-row">
        <strong>{translate('auto.k0607')}</strong>
        <button className="mini-button" type="button" disabled={props.disabled || props.state.loading} onClick={props.onRefresh}>
          <RefreshCw size={14} />
          {translate('auto.k0170')}</button>
      </div>

      {props.state.loading ? (
        <div className="empty-state compact">
          <History size={20} />
          <div>
            <strong>{translate('auto.k0608')}</strong>
            <span>{translate('auto.k0609')}</span>
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
              {props.onRollback && item.version !== props.currentVersion && (
                <div className="document-actions compact-actions">
                  <button
                    className="mini-button"
                    type="button"
                    disabled={props.disabled || props.state.loading}
                    onClick={() => props.onRollback?.(item.version)}
                  >
                    <RotateCcw size={14} />
                    {translate('auto.k0610')}{item.version}
                  </button>
                </div>
              )}
              <AssetVersionDiffViewer diff={item.diff} snapshot={item.snapshot} />
            </details>
          ))}
        </div>
      ) : (
        <div className="empty-state compact">
          <History size={20} />
          <div>
            <strong>{translate('auto.k0611')}</strong>
            <span>{props.state.error ?? translate('auto.k0612')}</span>
          </div>
        </div>
      )}

      {props.state.error && props.items.length > 0 && <span className="document-state-line error">{props.state.error}</span>}
      {props.state.traceId && !props.state.error && <span className="document-state-line">Trace ID：{props.state.traceId}</span>}
    </div>
  );
}

function AssetVersionDiffViewer(props: { diff: unknown; snapshot: unknown }) {
  const rows = assetVersionDiffRows(props.diff);
  return (
    <div className="asset-version-diff-viewer">
      <div className="asset-version-diff-heading">
        <strong>{translate('auto.k0613')}</strong>
        <span>{rows.length ? translate('auto.k0614', { value0: rows.length }) : translate('auto.k0615')}</span>
      </div>
      {rows.length ? (
        <div className="asset-version-diff-list">
          {rows.map((row) => (
            <div className={`asset-version-diff-row ${row.tone}`} key={row.path}>
              <strong>{row.path}</strong>
              <del>{formatAssetVersionDiffValue(row.before)}</del>
              <ins>{formatAssetVersionDiffValue(row.after)}</ins>
            </div>
          ))}
        </div>
      ) : (
        <pre>{formatHistoryJson(props.diff)}</pre>
      )}
      <details className="asset-version-raw-json">
        <summary>{translate('auto.k0616')}</summary>
        <div className="asset-version-json-grid">
          <div>
            <strong>diff_json</strong>
            <pre>{formatHistoryJson(props.diff)}</pre>
          </div>
          <div>
            <strong>snapshot</strong>
            <pre>{formatHistoryJson(props.snapshot)}</pre>
          </div>
        </div>
      </details>
    </div>
  );
}

function formatHistoryJson(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const formatted = typeof value === 'string' ? value : JSON.stringify(value, null, 2) ?? String(value);
  return formatted.length > HISTORY_JSON_LIMIT
    ? translate('auto.k0617', { value0: formatted.slice(0, HISTORY_JSON_LIMIT) })
    : formatted;
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
