import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAuthToken, requestJson, requestText } from './client';
import {
  activatePromptVersion,
  approvePromptVersion,
  cancelModelInvocationJob,
  checkModelProvider,
  createModelProvider,
  createPromptVersion,
  exportInvocationsCsv,
  fetchCostAlerts,
  fetchCostReport,
  fetchEffectiveModelAccessPolicy,
  fetchModelInvocationJob,
  fetchInvocationSummary,
  fetchInvocations,
  fetchModelAccessPolicies,
  fetchModelProviders,
  fetchPrompts,
  invokeModelStream,
  invocationExportPath,
  invocationJobPath,
  invocationStreamPath,
  modelAccessQueryPath,
  normalizeCostAlert,
  normalizeInvocationRecord,
  normalizeModelAccessEffectivePolicy,
  normalizeModelAccessPolicy,
  normalizeModelInvocationJob,
  normalizeModelProvider,
  normalizePromptTemplate,
  parseModelStreamEvents,
  rejectPromptVersion,
  submitModelInvocationJob,
  upsertModelAccessPolicy,
  updateModelProvider
} from './modelAccess';

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
  requestJson: vi.fn(),
  requestText: vi.fn()
}));

const getAuthTokenMock = vi.mocked(getAuthToken);
const requestJsonMock = vi.mocked(requestJson);
const requestTextMock = vi.mocked(requestText);

describe('model access API helpers', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    getAuthTokenMock.mockReset();
    requestJsonMock.mockReset();
    requestTextMock.mockReset();
  });

  it('normalizes provider, prompt, and sanitized invocation fields', () => {
    expect(normalizeModelProvider({
      id: 'provider-1',
      provider_type: 'openai_compatible',
      name: 'Public provider',
      routing_group: 'public',
      capabilities: 'CHAT,JSON',
      base_url: 'https://api.example.test',
      api_key_ref: 'env:MODEL_API_KEY',
      priority: '5',
      timeout_ms: '2500',
      input_cost_per_1k_tokens: '0.01',
      output_cost_per_1k_tokens: '0.03',
      status: 'enabled'
    })).toMatchObject({
      id: 'provider-1',
      providerType: 'OPENAI_COMPATIBLE',
      routingGroup: 'public',
      capabilities: 'CHAT,JSON',
      apiKeyRef: 'env:MODEL_API_KEY',
      priority: 5,
      timeoutMs: 2500,
      inputCostPer1kTokens: 0.01,
      outputCostPer1kTokens: 0.03,
      status: 'ENABLED'
    });

    expect(normalizePromptTemplate({
      id: 'prompt-1',
      prompt_key: 'case-design',
      version: '2',
      content: 'Prompt body',
      status: 'active',
      high_risk: true,
      approval_status: 'approved',
      approved_by: 'admin_user',
      approval_note: 'reviewed'
    })).toMatchObject({
      id: 'prompt-1',
      promptKey: 'case-design',
      version: 2,
      content: 'Prompt body',
      status: 'ACTIVE',
      highRisk: true,
      approvalStatus: 'APPROVED',
      approvedBy: 'admin_user',
      approvalNote: 'reviewed'
    });

    const invocation = normalizeInvocationRecord({
      invocation_id: 'inv-1',
      project_id: 'project-1',
      status: 'succeeded',
      routing_rule_name: 'wp4-private-low-cost',
      routing_group: 'private',
      model_capability: 'REQUIREMENT_PARSE',
      prompt_digest: 'sha256:abc',
      request_preview: 'user: password=***',
      input_tokens: '12',
      output_tokens: '8',
      total_cost: '0.0004'
    });
    expect(invocation).toMatchObject({
      id: 'inv-1',
      projectId: 'project-1',
      status: 'SUCCEEDED',
      routingRuleName: 'wp4-private-low-cost',
      routingGroup: 'private',
      modelCapability: 'REQUIREMENT_PARSE',
      roleScope: undefined,
      promptDigest: 'sha256:abc',
      requestPreview: 'user: password=***',
      inputTokens: 12,
      outputTokens: 8,
      totalCost: 0.0004
    });
    expect(invocation).not.toHaveProperty('promptPlaintext');

    expect(normalizeCostAlert({
      scope: 'CALLER_SERVICE',
      actor_service: 'wp4-document-input',
      spent_cost: '0.0002',
      budget_limit: '0.001',
      usage_ratio: '0.2',
      level: 'WARNING'
    })).toMatchObject({
      scope: 'CALLER_SERVICE',
      actorService: 'wp4-document-input',
      spentCost: 0.0002,
      budgetLimit: 0.001,
      usageRatio: 0.2,
      level: 'WARNING'
    });

    expect(normalizeModelAccessPolicy({
      id: 'policy-1',
      scope_type: 'project',
      scope_key: 'project-1',
      enabled: true,
      model_invocation_enabled: false,
      public_model_allowed: true,
      daily_budget_limit: '3.5',
      cost_alert_warning_ratio: '0.75',
      budget_overrun_action: 'fallback',
      routing_group: 'private',
      updated_by: 'admin_user'
    })).toMatchObject({
      id: 'policy-1',
      scopeType: 'PROJECT',
      scopeKey: 'project-1',
      modelInvocationEnabled: false,
      publicModelAllowed: true,
      dailyBudgetLimit: 3.5,
      costAlertWarningRatio: 0.75,
      budgetOverrunAction: 'FALLBACK',
      routingGroup: 'private',
      updatedBy: 'admin_user'
    });

    expect(normalizeModelAccessEffectivePolicy({
      model_invocation_enabled: true,
      public_model_allowed: false,
      daily_budget_limit: '1.25',
      budget_scope_type: 'ROLE',
      budget_scope_key: 'Auditor',
      role_scope: 'Auditor',
      matched_scopes: ['PLATFORM:GLOBAL', 'ROLE:Auditor']
    })).toMatchObject({
      modelInvocationEnabled: true,
      publicModelAllowed: false,
      dailyBudgetLimit: 1.25,
      budgetScopeType: 'ROLE',
      roleScope: 'Auditor',
      matchedScopes: ['PLATFORM:GLOBAL', 'ROLE:Auditor']
    });

    expect(normalizeModelInvocationJob({
      job_id: 'job-1',
      status: 'succeeded',
      created_at: '2026-05-23T00:00:00Z',
      invocation_id: 'inv-1',
      trace_id: 'trc_job',
      response: {
        invocation_id: 'inv-1',
        provider_name: 'local-echo-primary',
        fallback_used: false,
        content: 'ok',
        input_tokens: '3',
        output_tokens: '4',
        total_cost: '0.0001'
      }
    })).toMatchObject({
      jobId: 'job-1',
      status: 'SUCCEEDED',
      invocationId: 'inv-1',
      traceId: 'trc_job',
      response: {
        invocationId: 'inv-1',
        providerName: 'local-echo-primary',
        fallbackUsed: false,
        content: 'ok',
        inputTokens: 3,
        outputTokens: 4,
        totalCost: 0.0001
      }
    });
  });

  it('builds encoded filter paths for logs, summary, cost, and CSV export', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-list', data: { items: [], total: 0 } });
    requestTextMock.mockResolvedValue({ text: 'invocationId\n', traceId: 'trace-export', contentType: 'text/csv', filename: 'wp2-invocations.csv' });

    expect(modelAccessQueryPath('/api/v1/model-access/invocations', {
      projectId: 'project pay',
      status: 'SUCCEEDED',
      actorService: 'wp4 parser',
      startTime: '2026-05-20T00:00:00Z',
      empty: ''
    })).toBe('/api/v1/model-access/invocations?projectId=project+pay&status=SUCCEEDED&actorService=wp4+parser&startTime=2026-05-20T00%3A00%3A00Z');

    await fetchInvocations({ projectId: 'project pay', status: 'SUCCEEDED', index: 1, size: 20 });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations?projectId=project+pay&status=SUCCEEDED&index=1&size=20');

    await fetchInvocationSummary({ providerId: 'provider 1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations/summary?providerId=provider+1');

    await fetchCostReport({ startDate: '2026-05-20', endDate: '2026-05-21', projectId: 'project pay' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/cost/report?startDate=2026-05-20&endDate=2026-05-21&projectId=project+pay');

    await fetchCostAlerts({ projectId: 'project pay', actorService: 'wp4 parser' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/cost/alerts?projectId=project+pay&actorService=wp4+parser');

    await fetchModelAccessPolicies({ scopeType: 'PROJECT', scopeKey: 'project pay' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/policies?scopeType=PROJECT&scopeKey=project+pay');

    await fetchEffectiveModelAccessPolicy({ projectId: 'project pay', environmentId: 'env prod', roles: 'Auditor,QA' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/policies/effective?projectId=project+pay&environmentId=env+prod&roles=Auditor%2CQA');

    await upsertModelAccessPolicy({
      scopeType: 'PROJECT',
      scopeKey: 'project pay',
      enabled: true,
      modelInvocationEnabled: false,
      publicModelAllowed: true,
      dailyBudgetLimit: 1,
      budgetOverrunAction: 'BLOCK',
      routingGroup: ''
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/policies', {
      method: 'PUT',
      body: JSON.stringify({
        scopeType: 'PROJECT',
        scopeKey: 'project pay',
        enabled: true,
        modelInvocationEnabled: false,
        publicModelAllowed: true,
        dailyBudgetLimit: 1,
        budgetOverrunAction: 'BLOCK'
      })
    });

    expect(invocationExportPath({ projectId: 'project pay', status: 'BLOCKED', index: 2, size: 10 })).toBe('/api/v1/model-access/invocations/export?projectId=project+pay&status=BLOCKED');
    await exportInvocationsCsv({ projectId: 'project pay', status: 'BLOCKED' });
    expect(requestTextMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations/export?projectId=project+pay&status=BLOCKED');

    expect(invocationJobPath()).toBe('/api/v1/model-access/invocations/jobs');
    expect(invocationJobPath('job 1')).toBe('/api/v1/model-access/invocations/jobs/job%201');
  });

  it('submits, fetches, and cancels async invocation jobs', async () => {
    requestJsonMock
      .mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        trace_id: 'trace-submit',
        data: {
          jobId: 'job-1',
          status: 'QUEUED',
          createdAt: '2026-05-23T00:00:00Z'
        }
      })
      .mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        trace_id: 'trace-fetch',
        data: {
          jobId: 'job-1',
          status: 'SUCCEEDED',
          invocationId: 'inv-1',
          response: {
            invocationId: 'inv-1',
            providerName: 'local-echo-primary',
            fallbackUsed: false,
            content: 'done',
            inputTokens: 1,
            outputTokens: 2,
            totalCost: 0
          }
        }
      })
      .mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        trace_id: 'trace-cancel',
        data: {
          jobId: 'job 1',
          status: 'CANCELLED',
          errorCode: 'CANCELLED',
          errorMessage: '异步模型调用已取消'
        }
      });

    await submitModelInvocationJob({
      projectId: 'project-async',
      promptKey: '',
      messages: [{ role: 'user', content: 'hello' }],
      allowPublicModel: false
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations/jobs', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-async',
        messages: [{ role: 'user', content: 'hello' }],
        allowPublicModel: false
      })
    });

    const fetched = await fetchModelInvocationJob('job 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations/jobs/job%201');
    expect(fetched.data).toMatchObject({
      jobId: 'job-1',
      status: 'SUCCEEDED',
      invocationId: 'inv-1',
      response: {
        content: 'done'
      }
    });

    const cancelled = await cancelModelInvocationJob('job 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations/jobs/job%201/cancel', {
      method: 'POST'
    });
    expect(cancelled.data).toMatchObject({
      jobId: 'job 1',
      status: 'CANCELLED',
      errorCode: 'CANCELLED'
    });
  });

  it('parses server-sent model invocation events', () => {
    expect(parseModelStreamEvents(`
event: metadata
data: {"invocationId":"inv-1","providerName":"local-echo-primary","fallbackUsed":false,"inputTokens":1,"outputTokens":2,"totalCost":0,"traceId":"trc_1"}

event: delta
data: {"index":0,"content":"hello"}

event: done
data: {"invocationId":"inv-1","finishReason":"stop"}

event: unknown
data: {"ignored":true}

event: delta
data: {invalid-json}
`)).toEqual([
      {
        type: 'metadata',
        invocationId: 'inv-1',
        providerId: undefined,
        providerName: 'local-echo-primary',
        modelName: undefined,
        fallbackUsed: false,
        inputTokens: 1,
        outputTokens: 2,
        totalCost: 0,
        traceId: 'trc_1'
      },
      {
        type: 'delta',
        index: 0,
        content: 'hello'
      },
      {
        type: 'done',
        invocationId: 'inv-1',
        finishReason: 'stop'
      }
    ]);
  });

  it('posts streaming invocations with bearer auth and emits parsed events', async () => {
    getAuthTokenMock.mockReturnValue('user-token');
    const sseText = [
      'event: metadata',
      'data: {"invocationId":"inv-2","providerId":"provider-1","providerName":"local-echo-primary","modelName":"local-echo","fallbackUsed":false,"inputTokens":3,"outputTokens":4,"totalCost":0,"traceId":"trc_2"}',
      '',
      'event: delta',
      'data: {"index":0,"content":"partial answer"}',
      '',
      'event: done',
      'data: {"invocationId":"inv-2","finishReason":"stop"}',
      '',
      ''
    ].join('\n');
    const fetchMock = vi.fn().mockResolvedValue(new Response(sseText, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    }));
    vi.stubGlobal('fetch', fetchMock);

    const observed: ReturnType<typeof parseModelStreamEvents> = [];
    const events = await invokeModelStream({
      projectId: 'project-stream',
      promptKey: '',
      messages: [{ role: 'user', content: 'hello' }],
      allowPublicModel: false
    }, (event) => observed.push(event));

    expect(fetchMock).toHaveBeenCalledOnce();
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(path).toBe(invocationStreamPath());
    expect(init.method).toBe('POST');
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('Authorization')).toBe('Bearer user-token');
    expect(JSON.parse(init.body as string)).toEqual({
      projectId: 'project-stream',
      messages: [{ role: 'user', content: 'hello' }],
      allowPublicModel: false
    });
    expect(events).toEqual(observed);
    expect(events.map((event) => event.type)).toEqual(['metadata', 'delta', 'done']);
  });

  it('buffers split streaming chunks before emitting events', async () => {
    getAuthTokenMock.mockReturnValue('user-token');
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: delta\ndata: {"index":'));
        controller.enqueue(encoder.encode('0,"content":"partial"}\n\n'));
        controller.enqueue(encoder.encode('event: done\ndata: {"invocationId":"inv-3","finishReason":"stop"}\n\n'));
        controller.close();
      }
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })));

    const observed: ReturnType<typeof parseModelStreamEvents> = [];
    const events = await invokeModelStream({
      projectId: 'project-stream',
      messages: [{ role: 'user', content: 'hello' }]
    }, (event) => observed.push(event));

    expect(observed).toEqual(events);
    expect(events).toEqual([
      { type: 'delta', index: 0, content: 'partial' },
      { type: 'done', invocationId: 'inv-3', finishReason: 'stop' }
    ]);
  });

  it('normalizes streaming API errors', async () => {
    getAuthTokenMock.mockReturnValue('user-token');
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'MODEL_POLICY_VIOLATION',
        message: 'policy blocked',
        trace_id: 'trace-json'
      }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response('plain failure', {
        status: 500,
        headers: { 'X-Trace-Id': 'trace-text' }
      }))
      .mockResolvedValueOnce(new Response('', {
        status: 401
      }))
      .mockResolvedValueOnce(new Response('{}', {
        status: 200,
        headers: { 'Content-Type': 'application/json', 'X-Trace-Id': 'trace-content-type' }
      })));

    await expect(invokeModelStream({
      projectId: 'project-stream',
      messages: [{ role: 'user', content: 'hello' }]
    })).rejects.toMatchObject({
      code: 'MODEL_POLICY_VIOLATION',
      message: 'policy blocked',
      traceId: 'trace-json',
      status: 403
    });

    await expect(invokeModelStream({
      projectId: 'project-stream',
      messages: [{ role: 'user', content: 'hello' }]
    })).rejects.toMatchObject({
      code: 'HTTP_500',
      message: 'plain failure',
      traceId: 'trace-text',
      status: 500
    });

    await expect(invokeModelStream({
      projectId: 'project-stream',
      messages: [{ role: 'user', content: 'hello' }]
    })).rejects.toMatchObject({
      code: 'SESSION_EXPIRED',
      message: '登录已过期，请重新登录',
      status: 401
    });

    await expect(invokeModelStream({
      projectId: 'project-stream',
      messages: [{ role: 'user', content: 'hello' }]
    })).rejects.toMatchObject({
      code: 'INVALID_STREAM_RESPONSE',
      traceId: 'trace-content-type',
      status: 200
    });
  });

  it('calls provider and prompt management endpoints without service-token headers', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-ok', data: [] });
    await fetchModelProviders();
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/providers');

    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-provider', data: { id: 'provider-1', name: 'Local' } });
    await createModelProvider({ name: 'Local', providerType: 'LOCAL_ECHO', priority: 10, timeoutMs: 1000 });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/providers', {
      method: 'POST',
      body: JSON.stringify({ name: 'Local', providerType: 'LOCAL_ECHO', priority: 10, timeoutMs: 1000 })
    });

    await updateModelProvider('provider 1', { name: 'Local v2', apiKeyRef: '' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/providers/provider%201', {
      method: 'PUT',
      body: JSON.stringify({ name: 'Local v2' })
    });

    await checkModelProvider('provider 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/providers/provider%201/check', { method: 'POST' });

    await fetchPrompts('case design');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/prompts?promptKey=case+design');

    await createPromptVersion({ promptKey: 'case-design', name: 'Case design', content: 'Prompt', highRisk: true, activate: true });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/prompts', {
      method: 'POST',
      body: JSON.stringify({ promptKey: 'case-design', name: 'Case design', content: 'Prompt', highRisk: true, activate: true })
    });

    await activatePromptVersion('prompt 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/prompts/prompt%201/activate', { method: 'POST' });

    await approvePromptVersion('prompt 1', { reviewNote: 'approved' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/prompts/prompt%201/approve', {
      method: 'POST',
      body: JSON.stringify({ reviewNote: 'approved' })
    });

    await rejectPromptVersion('prompt 1', { reviewNote: 'needs changes' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/model-access/prompts/prompt%201/reject', {
      method: 'POST',
      body: JSON.stringify({ reviewNote: 'needs changes' })
    });
  });
});
