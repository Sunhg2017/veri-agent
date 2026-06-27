import { Activity, AlertCircle, CheckCircle2, RefreshCw, Server } from 'lucide-react';
import { useEffect, useState } from 'react';
import { ApiResult, HealthResponse, fetchHealth } from '../lib/api';
import { translate } from '../platform/i18n';

function formatHealthStatus(result: ApiResult<HealthResponse> | null): string {
  if (!result) {
    return translate('auto.k0744');
  }
  if (!result.ok) {
    return result.status === 0 ? translate('auto.k0899') : translate('auto.k0900', { value0: result.status });
  }
  return result.data.status ? String(result.data.status) : translate('auto.k0095');
}

export default function HealthCheck() {
  const [result, setResult] = useState<ApiResult<HealthResponse> | null>(null);
  const [loading, setLoading] = useState(false);

  async function loadHealth() {
    setLoading(true);
    const nextResult = await fetchHealth();
    setResult(nextResult);
    setLoading(false);
  }

  useEffect(() => {
    void loadHealth();
  }, []);

  const isHealthy = result?.ok;

  return (
    <section className="panel health-panel" aria-labelledby="health-title">
      <div className="panel-header">
        <div>
          <p className="eyebrow">{translate('auto.k0384')}</p>
          <h2 id="health-title">{translate('auto.k0901')}</h2>
        </div>
        <button className="icon-button" type="button" onClick={loadHealth} disabled={loading} title={translate('auto.k0902')}>
          <RefreshCw size={17} className={loading ? 'spin' : undefined} />
        </button>
      </div>

      <div className="health-body">
        <div className={isHealthy ? 'status-mark success' : 'status-mark muted'}>
          {isHealthy ? <CheckCircle2 size={24} /> : <Activity size={24} />}
        </div>
        <div>
          <div className="metric-label">GET /api/v1/health</div>
          <div className="metric-value">{loading ? translate('auto.k0093') : formatHealthStatus(result)}</div>
        </div>
      </div>

      {result && !result.ok && (
        <div className="inline-alert error">
          <AlertCircle size={16} />
          <span>{result.message}</span>
        </div>
      )}

      {result?.ok && (
        <dl className="details-grid">
          <div>
            <dt>HTTP</dt>
            <dd>{result.status}</dd>
          </div>
          <div>
            <dt>Trace ID</dt>
            <dd>{result.traceId ?? result.data.trace_id ?? translate('auto.k0903')}</dd>
          </div>
        </dl>
      )}

      <div className="api-chip">
        <Server size={15} />
        <span>{translate('auto.k0904')}</span>
      </div>
    </section>
  );
}
