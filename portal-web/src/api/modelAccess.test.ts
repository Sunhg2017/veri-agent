import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  activatePromptVersion,
  approvePromptVersion,
  checkModelProvider,
  createModelProvider,
  createPromptVersion,
  exportInvocationsCsv,
  fetchCostAlerts,
  fetchCostReport,
  fetchInvocationSummary,
  fetchInvocations,
  fetchModelProviders,
  fetchPrompts,
  invocationExportPath,
  modelAccessQueryPath,
  normalizeCostAlert,
  normalizeInvocationRecord,
  normalizeModelProvider,
  normalizePromptTemplate,
  rejectPromptVersion,
  updateModelProvider
} from './modelAccess';

vi.mock('./client', () => ({
  requestJson: vi.fn(),
  requestText: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestTextMock = vi.mocked(requestText);

describe('model access API helpers', () => {
  beforeEach(() => {
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

    expect(invocationExportPath({ projectId: 'project pay', status: 'BLOCKED', index: 2, size: 10 })).toBe('/api/v1/model-access/invocations/export?projectId=project+pay&status=BLOCKED');
    await exportInvocationsCsv({ projectId: 'project pay', status: 'BLOCKED' });
    expect(requestTextMock).toHaveBeenLastCalledWith('/api/v1/model-access/invocations/export?projectId=project+pay&status=BLOCKED');
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
