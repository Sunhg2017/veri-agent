import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  createApiAutomationSpec,
  fetchApiAutomationHealth,
  fetchApiAutomationSpec,
  fetchApiAutomationSpecs,
  normalizeApiAutomationEndpointSnapshot,
  normalizeApiAutomationHealth,
  normalizeApiAutomationSpecDetail,
  parseApiAutomationSpec
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
        diff_status: 'UNKNOWN'
      }]
    })).toMatchObject({
      spec: {
        id: 'spec-1',
        projectId: 'project-alpha',
        contentSizeBytes: 1234,
        endpointCount: 2
      },
      endpoints: [{ httpMethod: 'POST', path: '/v1/payments', parameterCount: 1 }]
    });

    expect(normalizeApiAutomationEndpointSnapshot({
      id: 'endpoint-2',
      httpMethod: 'GET',
      path: '/v1/customers',
      parameterCount: 0,
      requestBodyPresent: false,
      diffStatus: 'MATCHED'
    })).toMatchObject({ httpMethod: 'GET', diffStatus: 'MATCHED' });
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
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/api-automation/health');
  });
});
