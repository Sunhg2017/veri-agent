import { beforeEach, describe, expect, it, vi } from 'vitest';
import { refreshToken } from './auth';
import { getAuthToken, requestBinary, requestJson } from './client';
import {
  archiveExecutionPlan,
  cancelExecutionRun,
  createExecutionPlan,
  createExecutionTrigger,
  downloadExecutionArtifact,
  dryRunExecutionPlan,
  dryRunExecutionTrigger,
  exportExecutionRun,
  fetchExecutionHealth,
  fetchExecutionPlan,
  fetchExecutionPlans,
  fetchExecutionRun,
  fetchExecutionRuns,
  fetchExecutionTriggerEvents,
  fetchExecutionTriggers,
  normalizeExecutionDryRun,
  normalizeExecutionHealth,
  normalizeExecutionPlanDetail,
  normalizeExecutionPlanList,
  normalizeExecutionRunDetail,
  normalizeExecutionRunExport,
  normalizeExecutionRunList,
  parseExecutionRunStreamEvents,
  normalizeExecutionTrigger,
  normalizeExecutionTriggerEventList,
  normalizeExecutionTriggerList,
  retryExecutionRun,
  subscribeExecutionRunStream,
  triggerExecutionRun,
  updateExecutionPlan,
  updateExecutionTrigger
} from './execution';

vi.mock('./client', () => ({
  ApiError: class ApiError extends Error {
    readonly code: string;
    readonly traceId: string;
    readonly status: number;

    constructor(message: string, code: string, traceId: string, status: number) {
      super(message);
      this.name = 'ApiError';
      this.code = code;
      this.traceId = traceId;
      this.status = status;
    }
  },
  getAuthToken: vi.fn(),
  requestBinary: vi.fn(),
  requestJson: vi.fn()
}));

vi.mock('./auth', () => ({
  refreshToken: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestBinaryMock = vi.mocked(requestBinary);
const getAuthTokenMock = vi.mocked(getAuthToken);
const refreshTokenMock = vi.mocked(refreshToken);

describe('WP9 execution API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestBinaryMock.mockReset();
    getAuthTokenMock.mockReset();
    refreshTokenMock.mockReset();
    vi.unstubAllGlobals();
    requestBinaryMock.mockResolvedValue({
      blob: new Blob(['artifact-bytes'], { type: 'application/zip' }),
      traceId: 'trc-artifact',
      contentType: 'application/zip',
      filename: 'trace.zip'
    });
  });

  it('normalizes health, plans, dry-run, runs and trigger evidence', () => {
    expect(normalizeExecutionHealth({
      service: 'execution',
      scheduler_enabled: true,
      webhook_enabled: false,
      cron_enabled: false,
      scheduler_interval_ms: '1000',
      scheduler_tick_batch_size: '4',
      max_concurrent_runs_per_project: '2',
      max_concurrent_nodes_per_run: '3',
      node_heartbeat_timeout_seconds: '120',
      default_run_timeout_seconds: '3600',
      recovery_batch_size: '20',
      policy: { schedulerLoopReady: true, cronScannerReady: true }
    })).toMatchObject({
      service: 'execution',
      schedulerEnabled: true,
      webhookEnabled: false,
      cronEnabled: false,
      schedulerIntervalMs: 1000,
      schedulerTickBatchSize: 4,
      policy: { schedulerLoopReady: true, cronScannerReady: true }
    });

    expect(normalizeExecutionPlanList({
      items: [{
        id: 'plan-1',
        project_id: 'project-alpha',
        name: 'Release smoke',
        status: 'READY',
        environment_key: 'staging',
        node_count: '2'
      }],
      index: '0',
      size: '20',
      total: '1'
    })).toMatchObject({
      total: 1,
      items: [{ id: 'plan-1', projectId: 'project-alpha', environmentKey: 'staging', nodeCount: 2 }]
    });

    expect(normalizeExecutionPlanDetail({
      id: 'plan-1',
      project_id: 'project-alpha',
      name: 'Release smoke',
      status: 'READY',
      environment_key: 'staging',
      trigger_policy: { manualEnabled: true },
      nodes: [{
        id: 'node-1',
        key: 'api-smoke',
        type: 'API_TEST',
        dependencies: [],
        input_summary: { rawBaseUrlStored: false },
        timeout_seconds: '180',
        failure_policy: 'FAIL_FAST',
        retry_policy: { maxAttempts: 1 }
      }]
    })).toMatchObject({
      triggerPolicy: { manualEnabled: true },
      nodes: [{ key: 'api-smoke', timeoutSeconds: 180, inputSummary: { rawBaseUrlStored: false } }]
    });

    expect(normalizeExecutionDryRun({
      plan_id: 'plan-1',
      valid: true,
      dag_digest: 'abc',
      nodes: [{ key: 'api-smoke', runner_type: 'WP6_API', timeout_seconds: '180' }],
      issues: [{ code: 'OK', node_key: 'api-smoke', severity: 'INFO' }],
      policy: { runCreated: false }
    })).toMatchObject({
      planId: 'plan-1',
      valid: true,
      nodes: [{ runnerType: 'WP6_API', timeoutSeconds: 180 }],
      issues: [{ nodeKey: 'api-smoke' }]
    });

    expect(normalizeExecutionRunDetail({
      id: 'run-1',
      plan_id: 'plan-1',
      project_id: 'project-alpha',
      status: 'RUNNING',
      trigger_type: 'WEBHOOK',
      source_event_id: 'evt-1',
      trace_id: 'trc-1',
      result_summary: { rawOutputStored: false },
      node_count: '2',
      idempotent_replay: true,
      artifacts: [{
        id: 'artifact-1',
        node_run_id: 'node-run-1',
        plan_node_id: 'node-1',
        node_key: 'api-smoke',
        node_type: 'UI_TEST',
        runner_type: 'WP7_UI',
        source_type: 'WP7_UI_E2E',
        artifact_type: 'LOG',
        artifact_digest: 'sha256:artifact',
        size_bytes: '256',
        capture_status: 'CAPTURED',
        download_ready: true,
        redaction_flags: { aggregateOnly: true }
      }],
      nodes: [{
        id: 'node-run-1',
        plan_node_id: 'node-1',
        node_key: 'api-smoke',
        node_type: 'API_TEST',
        status: 'RUNNING',
        runner_type: 'WP6_API',
        result_summary: { requestResponseStored: false }
      }]
    })).toMatchObject({
      triggerType: 'WEBHOOK',
      sourceEventId: 'evt-1',
      idempotentReplay: true,
      artifacts: [{ artifactType: 'LOG', downloadReady: true, sourceType: 'WP7_UI_E2E' }],
      nodes: [{ nodeKey: 'api-smoke', runnerType: 'WP6_API' }]
    });

    expect(normalizeExecutionRunExport({
      schema_version: 'wp9-run-export-v1',
      exported_at: '2026-06-14T01:00:00Z',
      run: {
        id: 'run-1',
        plan_id: 'plan-1',
        project_id: 'project-alpha',
        status: 'FAILED',
        trigger_type: 'CRON',
        source_event_id: 'cron:trigger-1:2026-06-14T01:00:00Z',
        node_count: '2',
        nodes: [{ id: 'node-run-1', plan_node_id: 'node-1', status: 'FAILED' }]
      },
      node_status_counts: { FAILED: '1', PENDING: '1' },
      redaction_policy: { secretRefsExported: false, rawOutputExported: false }
    })).toMatchObject({
      schemaVersion: 'wp9-run-export-v1',
      exportedAt: '2026-06-14T01:00:00Z',
      run: { triggerType: 'CRON', sourceEventId: 'cron:trigger-1:2026-06-14T01:00:00Z' },
      nodeStatusCounts: { FAILED: 1, PENDING: 1 },
      redactionPolicy: { secretRefsExported: false, rawOutputExported: false }
    });

    expect(normalizeExecutionRunList({
      items: [{ id: 'run-2', plan_id: 'plan-1', project_id: 'project-alpha', status: 'QUEUED', node_count: 0 }],
      total: '1'
    })).toMatchObject({ items: [{ id: 'run-2', status: 'QUEUED' }], total: 1 });

    expect(normalizeExecutionTrigger({
      id: 'trigger-1',
      plan_id: 'plan-1',
      trigger_type: 'WEBHOOK',
      status: 'ENABLED',
      config_summary: { rawPayloadStored: false },
      secret_ref_configured: true,
      secret_ref_digest: 'digest'
    })).toMatchObject({
      triggerType: 'WEBHOOK',
      secretRefConfigured: true,
      secretRefDigest: 'digest',
      configSummary: { rawPayloadStored: false }
    });

    expect(normalizeExecutionTriggerList({
      items: [{ id: 'trigger-2', plan_id: 'plan-1', trigger_type: 'CRON', status: 'DISABLED' }],
      total: '1'
    })).toMatchObject({ items: [{ triggerType: 'CRON' }], total: 1 });

    expect(normalizeExecutionTriggerEventList({
      items: [{
        id: 'event-1',
        trigger_id: 'trigger-1',
        source_event_id: 'evt-1',
        request_digest: 'digest',
        status: 'ACCEPTED',
        run_id: 'run-1',
        trace_id: 'trc-1'
      }],
      total: '1'
    })).toMatchObject({
      items: [{ sourceEventId: 'evt-1', requestDigest: 'digest', runId: 'run-1' }],
      total: 1
    });
  });

  it('calls execution endpoints with normalized paths and payloads', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: { items: [] } });
    await fetchExecutionPlans({ projectId: 'project-alpha', status: 'READY', keyword: 'smoke', size: 10 });
    await fetchExecutionRuns({ projectId: 'project-alpha', planId: 'plan-1', status: 'RUNNING' });
    await fetchExecutionTriggers('plan-1', { triggerType: 'WEBHOOK', status: 'ENABLED' });
    await fetchExecutionTriggerEvents('trigger-1', { status: 'ACCEPTED', size: 5 });

    expect(requestJsonMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/execution/plans?projectId=project-alpha&status=READY&keyword=smoke&size=10'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/execution/runs?projectId=project-alpha&planId=plan-1&status=RUNNING'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/execution/plans/plan-1/triggers?triggerType=WEBHOOK&status=ENABLED'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      4,
      '/api/v1/execution/triggers/trigger-1/events?status=ACCEPTED&size=5'
    );
  });

  it('wraps mutating execution actions', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: {} });
    await fetchExecutionHealth();
    await fetchExecutionPlan('plan-1');
    await createExecutionPlan({
      projectId: 'project-alpha',
      name: 'Release smoke',
      environmentKey: 'staging',
      dag: { nodes: [{ key: 'api-smoke', type: 'API_TEST' }] }
    });
    await updateExecutionPlan('plan-1', { status: 'READY' });
    await dryRunExecutionPlan('plan-1');
    await archiveExecutionPlan('plan-1');
    await triggerExecutionRun('plan-1', { requestKey: 'rk-1', reason: 'manual' });
    await fetchExecutionRun('run-1');
    await exportExecutionRun('run-1');
    await downloadExecutionArtifact('run-1', 'artifact-1');
    await cancelExecutionRun('run-1');
    await retryExecutionRun('run-1');
    await createExecutionTrigger('plan-1', {
      triggerType: 'WEBHOOK',
      status: 'DISABLED',
      config: { source: 'ci' },
      secretRef: 'secret://wp9/webhook'
    });
    await updateExecutionTrigger('trigger-1', { status: 'ENABLED' });
    await dryRunExecutionTrigger('trigger-1');

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/execution/health');
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, '/api/v1/execution/plans/plan-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/execution/plans', {
      method: 'POST',
      body: expect.stringContaining('Release smoke')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/execution/plans/plan-1', {
      method: 'PATCH',
      body: JSON.stringify({ status: 'READY' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, '/api/v1/execution/plans/plan-1/dry-run', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, '/api/v1/execution/plans/plan-1/archive', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, '/api/v1/execution/plans/plan-1/runs', {
      method: 'POST',
      body: JSON.stringify({ requestKey: 'rk-1', reason: 'manual' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(8, '/api/v1/execution/runs/run-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(9, '/api/v1/execution/runs/run-1/export');
    expect(requestBinaryMock).toHaveBeenNthCalledWith(1, '/api/v1/execution/runs/run-1/artifacts/artifact-1/download');
    expect(requestJsonMock).toHaveBeenNthCalledWith(10, '/api/v1/execution/runs/run-1/cancel', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(11, '/api/v1/execution/runs/run-1/retry', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(12, '/api/v1/execution/plans/plan-1/triggers', {
      method: 'POST',
      body: expect.stringContaining('secret://wp9/webhook')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(13, '/api/v1/execution/triggers/trigger-1', {
      method: 'PATCH',
      body: JSON.stringify({ status: 'ENABLED' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(14, '/api/v1/execution/triggers/trigger-1/dry-run', {
      method: 'POST'
    });
  });

  it('parses execution SSE events', () => {
    expect(parseExecutionRunStreamEvents(`
event: connected
data: {"runId":"run-1","status":"RUNNING","timestamp":"2026-06-21T01:00:00Z"}

event: snapshot
data: {"run":{"id":"run-1","plan_id":"plan-1","project_id":"project-alpha","status":"RUNNING","trigger_type":"MANUAL","node_count":1,"nodes":[],"idempotent_replay":false}}

event: log
data: {"runId":"run-1","status":"RUNNING","level":"INFO","stage":"queue.claimed","message":"Execution node claimed","nodeRunId":"node-run-1","nodeKey":"api-smoke","metadata":{"workerId":"worker-1"}}

event: heartbeat
data: {"timestamp":"2026-06-21T01:00:05Z"}
`)).toEqual([
      {
        type: 'connected',
        runId: 'run-1',
        status: 'RUNNING',
        timestamp: '2026-06-21T01:00:00Z'
      },
      {
        type: 'snapshot',
        run: expect.objectContaining({
          id: 'run-1',
          status: 'RUNNING',
          triggerType: 'MANUAL'
        })
      },
      {
        type: 'log',
        runId: 'run-1',
        status: 'RUNNING',
        level: 'INFO',
        stage: 'queue.claimed',
        message: 'Execution node claimed',
        nodeRunId: 'node-run-1',
        nodeKey: 'api-smoke',
        timestamp: undefined,
        metadata: { workerId: 'worker-1' }
      },
      {
        type: 'heartbeat',
        timestamp: '2026-06-21T01:00:05Z'
      }
    ]);
  });

  it('subscribes execution stream with bearer auth and retries once on 401', async () => {
    getAuthTokenMock.mockReturnValue('run-stream-token');
    refreshTokenMock.mockResolvedValueOnce(true);
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: log\ndata: {"runId":"run-1","status":"RUNNING","level":"INFO",'));
        controller.enqueue(encoder.encode('"stage":"run.created","message":"Execution run created","metadata":{}}\n\n'));
        controller.close();
      }
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response(stream, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    const events: Array<ReturnType<typeof parseExecutionRunStreamEvents>[number]> = [];
    await subscribeExecutionRunStream('run-1', (event) => events.push(event));

    expect(refreshTokenMock).toHaveBeenCalledTimes(1);
    const [path, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(path).toBe('/api/v1/execution/runs/run-1/stream');
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('Authorization')).toBe('Bearer run-stream-token');
    expect(events).toEqual([
      {
        type: 'log',
        runId: 'run-1',
        status: 'RUNNING',
        level: 'INFO',
        stage: 'run.created',
        message: 'Execution run created',
        nodeRunId: undefined,
        nodeKey: undefined,
        timestamp: undefined,
        metadata: {}
      }
    ]);
  });
});
