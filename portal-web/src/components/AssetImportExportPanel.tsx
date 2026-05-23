import { Download, Upload } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import {
  exportAssetsText,
  importAssets,
  type AssetImportExportFormat,
  type AssetImportExportType,
  type AssetImportResult
} from '../api/assets';
import type { CurrentUser } from '../api/auth';
import { hasPermission } from '../permissions';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type Draft = {
  assetType: AssetImportExportType;
  format: AssetImportExportFormat;
  projectId: string;
  dryRun: boolean;
  content: string;
};

const initialDraft: Draft = {
  assetType: 'REQUIREMENT',
  format: 'CSV',
  projectId: '',
  dryRun: true,
  content: ''
};

export function AssetImportExportPanel(props: {
  currentUser: CurrentUser | null;
  onImported?: () => void;
  signedIn: boolean;
}) {
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const canExportAssets = hasPermission(props.currentUser, 'asset:export');
  const [draft, setDraft] = useState<Draft>(initialDraft);
  const [state, setState] = useState<WorkState>({ loading: false });
  const [lastResult, setLastResult] = useState<AssetImportResult | null>(null);

  const importDisabled = !props.signedIn || !canManageAssets || state.loading;
  const exportDisabled = !props.signedIn || !canExportAssets || state.loading;
  const formatOptions: AssetImportExportFormat[] = draft.assetType === 'API' ? ['CSV', 'JSON', 'OPENAPI'] : ['CSV', 'JSON'];

  async function submitImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft.projectId.trim() || !draft.content.trim()) {
      setState({ loading: false, error: 'projectId 和导入内容必填' });
      return;
    }
    setState({ loading: true });
    try {
      const response = await importAssets(draft);
      setLastResult(response.data);
      setState({
        loading: false,
        success: `${draft.dryRun ? '预检' : '导入'}完成：${response.data.created} 创建，${response.data.updated} 更新，${response.data.failed} 失败`,
        traceId: response.trace_id
      });
      if (!draft.dryRun && response.data.failed === 0) {
        props.onImported?.();
      }
    } catch (error: unknown) {
      setState({ loading: false, error: errorMessage(error, '导入失败') });
    }
  }

  async function submitExport() {
    if (!draft.projectId.trim()) {
      setState({ loading: false, error: 'projectId 必填' });
      return;
    }
    setState({ loading: true });
    try {
      const response = await exportAssetsText({
        assetType: draft.assetType,
        format: draft.format,
        projectId: draft.projectId
      });
      downloadText(response.text, response.filename ?? fallbackFileName(draft.assetType, draft.format), response.contentType);
      setState({ loading: false, success: '导出已生成', traceId: response.traceId });
    } catch (error: unknown) {
      setState({ loading: false, error: errorMessage(error, '导出失败') });
    }
  }

  function updateAssetType(assetType: AssetImportExportType) {
    const nextFormat = assetType === 'API' ? draft.format : draft.format === 'OPENAPI' ? 'CSV' : draft.format;
    setDraft((current) => ({ ...current, assetType, format: nextFormat }));
  }

  return (
    <section className="panel insight-panel asset-import-export-panel">
      <h2>导入导出</h2>
      <form className="asset-form" onSubmit={submitImport}>
        <div className="asset-form-grid">
          <label className="field" htmlFor="asset-io-type">
            <span>assetType</span>
            <select
              id="asset-io-type"
              value={draft.assetType}
              disabled={state.loading}
              onChange={(event) => updateAssetType(event.target.value as AssetImportExportType)}
            >
              <option value="REQUIREMENT">REQUIREMENT</option>
              <option value="API">API</option>
              <option value="TEST_CASE">TEST_CASE</option>
            </select>
          </label>
          <label className="field" htmlFor="asset-io-format">
            <span>format</span>
            <select
              id="asset-io-format"
              value={draft.format}
              disabled={state.loading}
              onChange={(event) => setDraft((current) => ({ ...current, format: event.target.value as AssetImportExportFormat }))}
            >
              {formatOptions.map((format) => (
                <option key={format} value={format}>{format}</option>
              ))}
            </select>
          </label>
          <label className="field" htmlFor="asset-io-project">
            <span>projectId</span>
            <input
              id="asset-io-project"
              value={draft.projectId}
              disabled={state.loading}
              onChange={(event) => setDraft((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="proj-payments"
            />
          </label>
          <label className="toggle-field" htmlFor="asset-io-dry-run">
            <input
              id="asset-io-dry-run"
              type="checkbox"
              checked={draft.dryRun}
              disabled={state.loading}
              onChange={(event) => setDraft((current) => ({ ...current, dryRun: event.target.checked }))}
            />
            <span>dryRun</span>
          </label>
        </div>
        <label className="field" htmlFor="asset-io-content">
          <span>content</span>
          <textarea
            id="asset-io-content"
            className="compact-textarea schema-textarea"
            value={draft.content}
            disabled={state.loading}
            onChange={(event) => setDraft((current) => ({ ...current, content: event.target.value }))}
          />
        </label>
        <div className="document-actions">
          <button className="primary-button" type="submit" disabled={importDisabled || !draft.projectId.trim() || !draft.content.trim()}>
            <Upload size={16} />
            {draft.dryRun ? '预检' : '导入'}
          </button>
          <button className="secondary-button" type="button" disabled={exportDisabled || !draft.projectId.trim()} onClick={submitExport}>
            <Download size={16} />
            导出
          </button>
        </div>
      </form>
      {lastResult && (
        <div className="asset-import-result">
          <strong>{lastResult.assetType} · {lastResult.format}</strong>
          <span>{lastResult.totalRows} 行 · {lastResult.created} 创建 · {lastResult.updated} 更新 · {lastResult.skipped} 跳过 · {lastResult.failed} 失败</span>
        </div>
      )}
      <StateLine state={state} />
    </section>
  );
}

function downloadText(text: string, filename: string, contentType: string) {
  const blob = new Blob([text], { type: contentType || 'text/plain;charset=UTF-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function fallbackFileName(assetType: string, format: string) {
  const extension = format === 'OPENAPI' ? 'json' : format.toLowerCase();
  return `wp3-${assetType.toLowerCase().replace('_', '-')}.${extension}`;
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">处理中</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return (
      <span className="document-state-line success">
        {props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}
      </span>
    );
  }
  return null;
}
