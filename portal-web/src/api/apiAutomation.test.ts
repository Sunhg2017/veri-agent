import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  createApiAutomationGenerationTask,
  createApiAutomationSpec,
  fetchApiAutomationDiff,
  fetchApiAutomationGenerationTask,
  fetchApiAutomationHealth,
  fetchApiAutomationSpec,
  fetchApiAutomationSpecs,
  normalizeApiAutomationEndpointSnapshot,
  normalizeApiAutomationDiffResponse,
  normalizeApiAutomationGenerationTaskDetail,
  normalizeApiAutomationHealth,
  normalizeApiAutomationSpecDetail,
  normalizeApiAutomationSyncResponse,
  parseApiAutomationSpec,
  syncApiAutomationSpec
} from './apiAutomation';

vi.mock('./client', () => ({
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);

describe('WP6 API automation helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('normalizes health, spec detail and endpoint snapshots', () => {
    expect(normalizeApiAutomationHealth({
      service: 'api-automation',
      supported_open_api_versions: ['3.x'],
      spec_max_bytes: '1048576',
      endpoint_max_count: 500,
      runner_enabled: false,
      runner_timeout_seconds: '120',
      runner_max_cases: 100,
      model_fallback_enabled: true,
      policy: { url_fetch_enabled: false }
    })).toMatchObject({
      service: 'api-automation',
      supportedOpenApiVersions: ['3.x'],
      specMaxBytes: 1048576,
      endpointMaxCount: 500,
      runnerEnabled: false,
      modelFallbackEnabled: true
    });

    expect(normalizeApiAutomationSpecDetail({
      spec: {
        id: 'spec-1',
        project_id: 'project-alpha',
        source_type: 'TEXT',
        content_size_bytes: '1234',
        endpoint_count: '2',
        status: 'PARSED'
      },
      parse_summary: { endpoint_count: 2 },
      endpoints: [{
        id: 'endpoint-1',
        http_method: 'POST',
        path: '/v1/payments',
        parameter_count: '1',
        request_body_present: true,
        diff_status: 'NEW',
        asset_api_id: 'asset-api-1',
        diff_summary: { reason: 'NO_MATCHING_WP3_API' },
        last_diff_at: '2026-06-11T00:00:00Z'
      }]
    })).toMatchObject({
      spec: {
        id: 'spec-1',
        projectId: 'project-alpha',
        contentSizeBytes: 1234,
        endpointCount: 2
      },
      endpoints: [{
        httpMethod: 'POST',
        path: '/v1/payments',
        parameterCount: 1,
        diffStatus: 'NEW',
        assetApiId: 'asset-api-1'
      }]
    });

    expect(normalizeApiAutomationEndpointSnapshot({
      id: 'endpoint-2',
      httpMethod: 'GET',
      path: '/v1/customers',
      parameterCount: 0,
      requestBodyPresent: false,
      diffStatus: 'MATCHED'
    })).toMatchObject({ httpMethod: 'GET', diffStatus: 'MATCHED' });

    expect(normalizeApiAutomationDiffResponse({
      spec_id: 'spec-1',
      counts: { NEW: '1', MATCHED: 2 },
      endpoints: [{ id: 'endpoint-1', http_method: 'GET', path: '/v1/customers' }]
    })).toMatchObject({
      specId: 'spec-1',
      counts: { NEW: 1, MATCHED: 2 },
      endpoints: [{ id: 'endpoint-1', httpMethod: 'GET' }]
    });

    expect(normalizeApiAutomationSyncResponse({
      specId: 'spec-1',
      counts: { CREATED: '1' },
      items: [{
        endpoint_id: 'endpoint-1',
        asset_api_id: 'asset-api-1',
        http_method: 'POST',
        path: '/v1/payments',
        before_status: 'NEW',
        result: 'CREATED'
      }],
      endpoints: []
    })).toMatchObject({
      counts: { CREATED: 1 },
      items: [{ endpointId: 'endpoint-1', assetApiId: 'asset-api-1', beforeStatus: 'NEW', result: 'CREATED' }]
    });

    expect(normalizeApiAutomationGenerationTaskDetail({
      task: {
        id: 'task-1',
        project_id: 'project-alpha',
        spec_id: 'spec-1',
        generation_mode: 'FALLBACK_ONLY',
        coverage_types: ['SMOKE', 'EXCEPTION'],
        status: 'COMPLETED',
        fallback_used: true,
        api_count: '2',
        case_count: '4',
        input_summary: { aggregateOnly: true }
      },
      cases: [{
        id: 'case-1',
        endpoint_snapshot_id: 'endpoint-1',
        asset_api_id: 'asset-api-1',
        asset_test_case_id: 'asset-case-1',
        http_method: 'GET',
        path: '/v1/customers',
        coverage_type: 'SMOKE',
        expected_status: '200',
        assertion_summary: { expectedStatus: 200 },
        request_template: { aggregateOnly: true },
        source: 'FALLBACK',
        status: 'DRAFT'
      }]
    })).toMatchObject({
      task: { id: 'task-1', fallbackUsed: true, apiCount: 2, caseCount: 4 },
      cases: [{ id: 'case-1', assetTestCaseId: 'asset-case-1', expectedStatus: 200, source: 'FALLBACK' }]
    });
  });

  it('calls WP6 endpoints with expected methods and query parameters', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'success',
      trace_id: 'trc_1',
      data: { items: [], index: 0, size: 20, total: 0 }
    });

    await fetchApiAutomationSpecs({ projectId: 'project-alpha', status: 'PARSED', keyword: 'billing' });

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/api-automation/specs?projectId=project-alpha&status=PARSED&keyword=billing'
    );
  });

  it('creates, fetches and reparses specs through the backend contract', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'success',
      trace_id: 'trc_1',
      data: {
        spec: {
          id: 'spec-1',
          projectId: 'project-alpha',
          sourceType: 'TEXT',
          name: 'billing',
          contentSizeBytes: 100,
          endpointCount: 1,
          status: 'PARSED'
        },
        parseSummary: {},
        endpoints: []
      }
    });

    await createApiAutomationSpec({
      projectId: 'project-alpha',
      sourceType: 'TEXT',
      name: 'billing',
      content: '{"openapi":"3.0.3"}'
    });
    await fetchApiAutomationSpec('spec-1');
    await parseApiAutomationSpec('spec-1');
    await fetchApiAutomationDiff('spec-1');
    await syncApiAutomationSpec('spec-1', { includeChanged: false });
    await createApiAutomationGenerationTask({
      projectId: 'project-alpha',
      specId: 'spec-1',
      assetTestCaseIds: ['asset-case-1'],
      coverageTypes: ['SMOKE'],
      generationMode: 'FALLBACK_ONLY'
    });
    await fetchApiAutomationGenerationTask('task-1');
    await fetchApiAutomationHealth();

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/api-automation/specs', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-alpha',
        sourceType: 'TEXT',
        name: 'billing',
        content: '{"openapi":"3.0.3"}'
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, '/api/v1/api-automation/specs/spec-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/api-automation/specs/spec-1/parse', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/api-automation/specs/spec-1/diff');
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, '/api/v1/api-automation/specs/spec-1/sync', {
      method: 'POST',
      body: JSON.stringify({ includeChanged: false })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, '/api/v1/api-automation/generation-tasks', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-alpha',
        specId: 'spec-1',
        assetTestCaseIds: ['asset-case-1'],
        coverageTypes: ['SMOKE'],
        generationMode: 'FALLBACK_ONLY'
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, '/api/v1/api-automation/generation-tasks/task-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(8, '/api/v1/api-automation/health');
  });
});
