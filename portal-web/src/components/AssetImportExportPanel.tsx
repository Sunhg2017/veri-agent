import { Download, Upload } from 'lucide-react';
import { Drawer } from 'antd';
import { useEffect, useState, type FormEvent } from 'react';
import {
  exportAssetsText,
  importAssets,
  type AssetImportExportFormat,
  type AssetImportExportType,
  type AssetImportResult
} from '../api/assets';
import type { CurrentUser } from '../api/auth';
import { hasPermission } from '../permissions';
import { dictionaryLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

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

const allAssetTypes: AssetImportExportType[] = ['REQUIREMENT', 'API', 'PAGE', 'BUSINESS_FLOW', 'TEST_CASE'];

export function AssetImportExportPanel(props: {
  assetTypes?: AssetImportExportType[];
  currentUser: CurrentUser | null;
  onImported?: () => void;
  signedIn: boolean;
}) {
  const assetTypeOptions = props.assetTypes?.length ? props.assetTypes : allAssetTypes;
  const assetTypeKey = assetTypeOptions.join('|');
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const canExportAssets = hasPermission(props.currentUser, 'asset:export');
  const [draft, setDraft] = useState<Draft>(() => ({
    ...initialDraft,
    assetType: assetTypeOptions[0] ?? initialDraft.assetType
  }));
  const [importDrawerOpen, setImportDrawerOpen] = useState(false);
  const [state, setState] = useState<WorkState>({ loading: false });
  const [lastResult, setLastResult] = useState<AssetImportResult | null>(null);

  const importDisabled = !props.signedIn || !canManageAssets || state.loading;
  const exportDisabled = !props.signedIn || !canExportAssets || state.loading;
  const formatOptions = formatsForAssetType(draft.assetType);

  useEffect(() => {
    setDraft((current) => {
      if (assetTypeOptions.includes(current.assetType)) {
        return current;
      }
      const assetType = assetTypeOptions[0] ?? initialDraft.assetType;
      return { ...current, assetType, format: normalizeFormat(assetType, current.format) };
    });
    setLastResult(null);
  }, [assetTypeKey]);

  async function submitImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft.projectId.trim() || !draft.content.trim()) {
      setState({ loading: false, error: translate('auto.k0459') });
      return;
    }
    setState({ loading: true });
    try {
      const response = await importAssets(draft);
      setLastResult(response.data);
      setState({
        loading: false,
        success: translate('auto.k0460', { value0: draft.dryRun ? translate('auto.k0464') : translate('auto.k0175'), value1: response.data.created, value2: response.data.updated, value3: response.data.failed }),
        traceId: response.trace_id
      });
      if (!draft.dryRun || response.data.failed === 0) {
        setImportDrawerOpen(false);
      }
      if (!draft.dryRun && response.data.failed === 0) {
        props.onImported?.();
      }
    } catch (error: unknown) {
      setState({ loading: false, error: errorMessage(error, translate('auto.k0144')) });
    }
  }

  async function submitExport() {
    if (!draft.projectId.trim()) {
      setState({ loading: false, error: translate('auto.k0461') });
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
      setState({ loading: false, success: translate('auto.k0462'), traceId: response.traceId });
    } catch (error: unknown) {
      setState({ loading: false, error: errorMessage(error, translate('auto.k0062')) });
    }
  }

  function updateAssetType(assetType: AssetImportExportType) {
    const nextFormat = normalizeFormat(assetType, draft.format);
    setDraft((current) => ({ ...current, assetType, format: nextFormat }));
  }

  return (
    <section className="panel insight-panel asset-import-export-panel">
      <div className="panel-title-row">
        <h2>{translate('auto.k0463')}</h2>
        <div className="document-actions">
          <button className="mini-button" type="button" disabled={importDisabled} onClick={() => setImportDrawerOpen(true)}>
            <Upload size={14} />
            {translate('auto.k0175')}</button>
          <button className="mini-button" type="button" disabled={exportDisabled || !draft.projectId.trim()} onClick={submitExport}>
            <Download size={14} />
            {translate('auto.k0465')}</button>
        </div>
      </div>
      <Drawer
        className="asset-form-drawer"
        destroyOnHidden
        maskClosable={!state.loading}
        open={importDrawerOpen}
        placement="right"
        title={translate('auto.k0463')}
        width={720}
        onClose={() => {
          if (!state.loading) {
            setImportDrawerOpen(false);
          }
        }}
      >
      <form className="asset-form document-drawer-form" onSubmit={submitImport}>
        <div className="asset-form-grid">
          <label className="field" htmlFor="asset-io-type">
            <span>{fieldLabel('assetType')}</span>
            <select
              id="asset-io-type"
              value={draft.assetType}
              disabled={state.loading}
              onChange={(event) => updateAssetType(event.target.value as AssetImportExportType)}
            >
              {assetTypeOptions.map((assetType) => (
                <option key={assetType} value={assetType}>{dictionaryLabel(assetType)}</option>
              ))}
            </select>
          </label>
          <label className="field" htmlFor="asset-io-format">
            <span>{fieldLabel('format')}</span>
            <select
              id="asset-io-format"
              value={draft.format}
              disabled={state.loading}
              onChange={(event) => setDraft((current) => ({ ...current, format: event.target.value as AssetImportExportFormat }))}
            >
              {formatOptions.map((format) => (
                <option key={format} value={format}>{dictionaryLabel(format)}</option>
              ))}
            </select>
          </label>
          <label className="field" htmlFor="asset-io-project">
            <span>{fieldLabel('projectId')}</span>
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
            <span>{fieldLabel('dryRun')}</span>
          </label>
        </div>
        <label className="field" htmlFor="asset-io-content">
          <span>{fieldLabel('content')}</span>
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
            {draft.dryRun ? translate('auto.k0464') : translate('auto.k0175')}
          </button>
          <button className="secondary-button" type="button" disabled={state.loading} onClick={() => setImportDrawerOpen(false)}>
            {translate('actions.cancel')}</button>
        </div>
      </form>
      </Drawer>
      {lastResult && (
        <div className="asset-import-result">
          <strong>{lastResult.assetType} · {lastResult.format}</strong>
          <span>{lastResult.totalRows} {translate('auto.k0466')}{lastResult.created} {translate('auto.k0467')}{lastResult.updated} {translate('auto.k0468')}{lastResult.skipped} {translate('auto.k0469')}{lastResult.failed} {translate('auto.k0369')}</span>
        </div>
      )}
      <StateLine state={state} />
    </section>
  );
}

function formatsForAssetType(assetType: AssetImportExportType): AssetImportExportFormat[] {
  return assetType === 'API' ? ['CSV', 'JSON', 'OPENAPI'] : ['CSV', 'JSON'];
}

function normalizeFormat(assetType: AssetImportExportType, format: AssetImportExportFormat): AssetImportExportFormat {
  const formats = formatsForAssetType(assetType);
  return formats.includes(format) ? format : formats[0];
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
    return <span className="document-state-line">{translate('auto.k0458')}</span>;
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
