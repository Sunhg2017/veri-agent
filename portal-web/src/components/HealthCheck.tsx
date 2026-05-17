import { Activity, AlertCircle, CheckCircle2, RefreshCw, Server } from 'lucide-react';
import { useEffect, useState } from 'react';
import { ApiResult, HealthResponse, fetchHealth } from '../lib/api';

function formatHealthStatus(result: ApiResult<HealthResponse> | null): string {
  if (!result) {
    return '未检查';
  }
  if (!result.ok) {
    return result.status === 0 ? '连接失败' : `异常 ${result.status}`;
  }
  return result.data.status ? String(result.data.status) : '正常';
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
          <p className="eyebrow">运行状态</p>
          <h2 id="health-title">后端健康检查</h2>
        </div>
        <button className="icon-button" type="button" onClick={loadHealth} disabled={loading} title="刷新健康检查">
          <RefreshCw size={17} className={loading ? 'spin' : undefined} />
        </button>
      </div>

      <div className="health-body">
        <div className={isHealthy ? 'status-mark success' : 'status-mark muted'}>
          {isHealthy ? <CheckCircle2 size={24} /> : <Activity size={24} />}
        </div>
        <div>
          <div className="metric-label">GET /api/v1/health</div>
          <div className="metric-value">{loading ? '检查中' : formatHealthStatus(result)}</div>
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
            <dd>{result.traceId ?? result.data.trace_id ?? '未返回'}</dd>
          </div>
        </dl>
      )}

      <div className="api-chip">
        <Server size={15} />
        <span>通过 Vite 代理访问后端</span>
      </div>
    </section>
  );
}
