import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  ASSET_API_METHODS,
  ASSET_API_STATUSES,
  ASSET_FLOW_STATUSES,
  ASSET_PAGE_SOURCES,
  ASSET_PAGE_STATUSES,
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_REQUIREMENT_SOURCES,
  ASSET_REQUIREMENT_STATUSES,
  ASSET_TEST_CASE_STATUSES,
  assetApiItems,
  assetBusinessFlowItems,
  assetPageItems,
  assetRequirementItems,
  assetTestCaseItems,
  assetTestCaseStepItems,
  assetVersionHistoryItems,
  assetExportPath,
  createAssetApi,
  createAssetBusinessFlow,
  createAssetPage,
  createAssetRequirement,
  createAssetTestCase,
  exportAssetsText,
  fetchAssetImpactAnalysis,
  fetchAssetApi,
  fetchAssetApiVersions,
  fetchAssetApis,
  fetchAssetBusinessFlow,
  fetchAssetBusinessFlowVersions,
  fetchAssetBusinessFlows,
  fetchAssetPage,
  fetchAssetPageVersions,
  fetchAssetPages,
  fetchAssetRequirement,
  fetchAssetRequirements,
  fetchAssetRequirementVersions,
  fetchAssetTestCase,
  fetchAssetTestCases,
  fetchAssetTestCaseSteps,
  fetchAssetTestCaseVersions,
  fetchAssetTraceLinks,
  fetchRequirementTraceLinks,
  importAssets,
  normalizeAssetImpactAnalysis,
  normalizeAssetApiList,
  normalizeAssetApiView,
  normalizeAssetBusinessFlowList,
  normalizeAssetBusinessFlowView,
  normalizeAssetHealth,
  normalizeAssetPageList,
  normalizeAssetPageView,
  normalizeAssetRequirementList,
  normalizeAssetRequirementView,
  normalizeAssetTestCaseList,
  normalizeAssetTestCaseView,
  normalizeAssetVersionHistoryView,
  normalizeTraceLinkList,
  rollbackAssetApiVersion,
  rollbackAssetBusinessFlowVersion,
  rollbackAssetPageVersion,
  rollbackAssetRequirementVersion,
  rollbackAssetTestCaseVersion,
  syncPrototypePages,
  updateAssetApi,
  updateAssetBusinessFlow,
  updateAssetPage,
  updateAssetRequirement,
  updateAssetTestCase,
  updateAssetTestCaseSteps
} from './assets';

vi.mock('./client', () => ({
  requestJson: vi.fn(),
  requestText: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestTextMock = vi.mocked(requestText);

describe('asset API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestTextMock.mockReset();
  });

  it('exposes WP3 requirement enums used by the workbench', () => {
    expect(ASSET_REQUIREMENT_STATUSES).toEqual(['DRAFT', 'REVIEWING', 'APPROVED', 'DEPRECATED']);
    expect(ASSET_REQUIREMENT_PRIORITIES).toEqual(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']);
    expect(ASSET_REQUIREMENT_SOURCES).toEqual(['MANUAL', 'IMPORT']);
  });

  it('exposes WP3 API enums used by the workbench', () => {
    expect(ASSET_API_STATUSES).toEqual(['ACTIVE', 'DEPRECATED', 'REMOVED']);
    expect(ASSET_API_METHODS).toEqual(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']);
  });

  it('exposes WP3 page and business flow enums used by the workbench', () => {
    expect(ASSET_PAGE_STATUSES).toEqual(['ACTIVE', 'DEPRECATED']);
    expect(ASSET_PAGE_SOURCES).toEqual(['MANUAL', 'FIGMA', 'LANHU', 'AXURE']);
    expect(ASSET_FLOW_STATUSES).toEqual(['DRAFT', 'ACTIVE', 'ARCHIVED']);
    expect(ASSET_TEST_CASE_STATUSES).toEqual(['DRAFT', 'REVIEWING', 'APPROVED', 'DEPRECATED']);
  });

  it('normalizes health and camelCase requirement fields', () => {
    expect(normalizeAssetHealth({ service: 'asset-service', status: 'UP' })).toEqual({
      service: 'asset-service',
      status: 'UP'
    });

    const requirement = normalizeAssetRequirementView({
      id: 'req-1',
      title: '登录需求',
      description: '用户可以登录',
      source: 'IMPORT',
      sourceRef: 'PRD-1',
      sourceUrl: 'https://docs.example.test/prd-1',
      acceptanceCriteria: 'Given valid account',
      status: 'reviewing',
      priority: 'high',
      projectId: 'proj-payments',
      tags: ['auth', 'mobile'],
      version: '3',
      createdAt: '2026-05-20T01:00:00Z'
    });

    expect(requirement).toMatchObject({
      id: 'req-1',
      title: '登录需求',
      source: 'IMPORT',
      sourceRef: 'PRD-1',
      sourceUrl: 'https://docs.example.test/prd-1',
      acceptanceCriteria: 'Given valid account',
      status: 'REVIEWING',
      priority: 'HIGH',
      projectId: 'proj-payments',
      tags: ['auth', 'mobile'],
      version: 3
    });
  });

  it('normalizes snake_case requirement fields and paged responses', () => {
    const list = normalizeAssetRequirementList({
      content: [
        {
          requirement_id: 'req-2',
          name: '支付需求',
          source_ref: 'PAY-1',
          acceptance_criteria: '可支付',
          project_id: 'proj-payments',
          tags: 'payment, checkout'
        }
      ],
      total_elements: '8',
      page_size: '20',
      page: '1'
    });

    expect(list.total).toBe(8);
    expect(list.pageSize).toBe(20);
    expect(list.items[0]).toMatchObject({
      id: 'req-2',
      title: '支付需求',
      sourceRef: 'PAY-1',
      acceptanceCriteria: '可支付',
      projectId: 'proj-payments',
      tags: ['payment', 'checkout']
    });
    expect(assetRequirementItems([{ id: 'req-3', title: '退款' }])).toHaveLength(1);
  });

  it('calls requirement list and detail endpoints with encoded filters', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-1', data: { items: [] } });

    await fetchAssetRequirements({
      index: 1,
      size: 20,
      projectId: 'proj pay',
      status: 'DRAFT',
      keyword: '登录',
      source: 'IMPORT',
      sourceRef: 'PRD-1'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/asset/requirements?index=1&size=20&projectId=proj+pay&status=DRAFT&keyword=%E7%99%BB%E5%BD%95&source=IMPORT'
    );

    await fetchAssetRequirement('req 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/requirements/req%201');
  });

  it('normalizes requirement version history and calls the versions endpoint', async () => {
    const history = normalizeAssetVersionHistoryView({
      history_id: 'hist-1',
      asset_type: 'REQUIREMENT',
      asset_id: 'req-1',
      project_id: 'proj-payments',
      version: '2',
      change_type: 'UPDATE',
      actor: 'qa@example.test',
      changed_fields: 'title, description',
      diff_json: '{"title":{"before":"旧标题","after":"新标题"}}',
      snapshot_json: { title: '新标题', status: 'REVIEWING' },
      trace_id: 'trace-history',
      created_at: '2026-05-20T02:00:00Z'
    });

    expect(history).toMatchObject({
      id: 'hist-1',
      assetType: 'REQUIREMENT',
      assetId: 'req-1',
      projectId: 'proj-payments',
      version: 2,
      changeType: 'UPDATE',
      actor: 'qa@example.test',
      changedFields: ['title', 'description'],
      diff: { title: { before: '旧标题', after: '新标题' } },
      snapshot: { title: '新标题', status: 'REVIEWING' },
      traceId: 'trace-history'
    });

    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-versions',
      data: [
        {
          id: 'hist-2',
          assetType: 'REQUIREMENT',
          assetId: 'req-1',
          version: 1,
          changeType: 'CREATE',
          changedFields: ['title'],
          diff: {},
          snapshot: {}
        }
      ]
    });

    const response = await fetchAssetRequirementVersions('req 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/requirements/req%201/versions');
    expect(response.data).toHaveLength(1);
    expect(response.data[0]).toMatchObject({
      id: 'hist-2',
      assetType: 'REQUIREMENT',
      version: 1,
      changeType: 'CREATE',
      changedFields: ['title']
    });
    expect(assetVersionHistoryItems({ items: [{ id: 'hist-3', version: '5' }] })[0].version).toBe(5);
  });

  it('calls requirement rollback endpoint', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-rollback', data: { id: 'req-1', title: '旧需求' } });

    const response = await rollbackAssetRequirementVersion('req 1', 1, 'restore');

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/requirements/req%201/versions/1/rollback', {
      method: 'POST',
      body: JSON.stringify({ reason: 'restore' })
    });
    expect(response.data.title).toBe('旧需求');
  });

  it('compacts create and update payloads for the current WP3 contract', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-2', data: { id: 'req-1', title: 'A' } });

    await createAssetRequirement({
      projectId: ' proj-payments ',
      title: ' 登录需求 ',
      description: '',
      source: 'MANUAL',
      sourceRef: 'MAN-1',
      sourceUrl: '',
      acceptanceCriteria: '可登录',
      status: 'DRAFT',
      priority: 'HIGH',
      tags: ['auth', ' mobile ', '']
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/requirements', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'proj-payments',
        title: '登录需求',
        source: 'MANUAL',
        sourceRef: 'MAN-1',
        acceptanceCriteria: '可登录',
        status: 'DRAFT',
        priority: 'HIGH',
        tags: 'auth,mobile'
      })
    });

    await updateAssetRequirement('req 1', {
      title: '登录需求',
      description: '更新描述',
      status: 'REVIEWING',
      priority: 'MEDIUM',
      tags: 'auth,review'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/requirements/req%201', {
      method: 'PUT',
      body: JSON.stringify({
        title: '登录需求',
        description: '更新描述',
        status: 'REVIEWING',
        priority: 'MEDIUM',
        tags: 'auth,review'
      })
    });
  });

  it('normalizes API asset fields, enums and paged responses', () => {
    const api = normalizeAssetApiView({
      api_id: 'api-1',
      code: 'API-0001',
      name: '创建订单',
      description: '订单创建接口',
      http_method: 'post',
      path: '/api/orders',
      source_ref: 'openapi.yaml#/paths/~1api~1orders/post',
      version: '1.0.0',
      request_schema: '{"type":"object"}',
      response_schema: '{"type":"object","properties":{"id":{"type":"string"}}}',
      project_id: 'proj-payments',
      status: 'deprecated',
      created_at: '2026-05-20T01:00:00Z'
    });

    expect(api).toMatchObject({
      id: 'api-1',
      code: 'API-0001',
      summary: '创建订单',
      httpMethod: 'POST',
      path: '/api/orders',
      sourceRef: 'openapi.yaml#/paths/~1api~1orders/post',
      version: '1.0.0',
      requestSchema: '{"type":"object"}',
      responseSchema: '{"type":"object","properties":{"id":{"type":"string"}}}',
      projectId: 'proj-payments',
      status: 'DEPRECATED'
    });

    const list = normalizeAssetApiList({
      records: [
        {
          id: 'api-2',
          summary: '查询订单',
          httpMethod: 'get',
          path: '/api/orders/{id}',
          status: 'active',
          projectId: 'proj-payments'
        }
      ],
      totalElements: '6',
      pageSize: '20',
      page: '1'
    });

    expect(list.total).toBe(6);
    expect(list.pageSize).toBe(20);
    expect(list.items[0]).toMatchObject({
      id: 'api-2',
      summary: '查询订单',
      httpMethod: 'GET',
      status: 'ACTIVE'
    });
    expect(assetApiItems([{ id: 'api-3', summary: '退款', http_method: 'patch', path: '/refunds' }])).toHaveLength(1);
  });

  it('calls API list and detail endpoints with encoded filters', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-api-1', data: { items: [] } });

    await fetchAssetApis({
      index: 1,
      size: 20,
      projectId: 'proj pay',
      status: 'ACTIVE',
      keyword: '订单',
      source: 'OPENAPI'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/asset/apis?index=1&size=20&projectId=proj+pay&status=ACTIVE&keyword=%E8%AE%A2%E5%8D%95&source=OPENAPI'
    );

    await fetchAssetApi('api 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/apis/api%201');
  });

  it('compacts API create and update payloads for the current WP3 contract', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-api-2',
      data: { id: 'api-1', summary: '创建订单', httpMethod: 'POST', path: '/api/orders' }
    });

    await createAssetApi({
      projectId: ' proj-payments ',
      summary: ' 创建订单 ',
      description: '',
      httpMethod: ' POST ',
      path: ' /api/orders ',
      version: ' 1.0.0 ',
      requestSchema: ' {"type":"object"} ',
      responseSchema: '',
      status: 'ACTIVE'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/apis', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'proj-payments',
        summary: '创建订单',
        httpMethod: 'POST',
        path: '/api/orders',
        version: '1.0.0',
        requestSchema: '{"type":"object"}',
        status: 'ACTIVE'
      })
    });

    await updateAssetApi('api 1', {
      summary: '查询订单',
      description: '按订单号查询',
      httpMethod: ' GET ',
      path: ' /api/orders/{id} ',
      version: ' 1.1.0 ',
      requestSchema: '',
      responseSchema: ' {"type":"object","properties":{"id":{"type":"string"}}} ',
      status: 'DEPRECATED'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/apis/api%201', {
      method: 'PUT',
      body: JSON.stringify({
        summary: '查询订单',
        description: '按订单号查询',
        httpMethod: 'GET',
        path: '/api/orders/{id}',
        version: '1.1.0',
        responseSchema: '{"type":"object","properties":{"id":{"type":"string"}}}',
        status: 'DEPRECATED'
      })
    });
  });

  it('calls API versions and rollback endpoints', async () => {
    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-api-versions',
      data: [
        {
          history_id: 'hist-api-1',
          asset_type: 'API',
          asset_id: 'api-1',
          project_id: 'proj-payments',
          version: '2',
          change_type: 'UPSERT',
          changed_fields: 'summary, requestSchema',
          diff_json: '{"summary":{"before":"旧接口","after":"新接口"}}',
          snapshot_json: '{"summary":"新接口","requestSchema":{"type":"object"},"revision":2}',
          trace_id: 'trace-api-history'
        }
      ]
    });

    const versions = await fetchAssetApiVersions('api 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/apis/api%201/versions');
    expect(versions.data[0]).toMatchObject({
      id: 'hist-api-1',
      assetType: 'API',
      assetId: 'api-1',
      projectId: 'proj-payments',
      version: 2,
      changeType: 'UPSERT',
      changedFields: ['summary', 'requestSchema'],
      diff: { summary: { before: '旧接口', after: '新接口' } },
      snapshot: { summary: '新接口', requestSchema: { type: 'object' }, revision: 2 },
      traceId: 'trace-api-history'
    });

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-api-rollback',
      data: { id: 'api-1', summary: '旧接口', httpMethod: 'GET', path: '/api/orders' }
    });

    const rolledBack = await rollbackAssetApiVersion('api 1', 1, 'restore api');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/apis/api%201/versions/1/rollback', {
      method: 'POST',
      body: JSON.stringify({ reason: 'restore api' })
    });
    expect(rolledBack.data.summary).toBe('旧接口');
  });

  it('normalizes page assets with JSON fields and paged responses', () => {
    const page = normalizeAssetPageView({
      page_id: 'page-1',
      code: 'PAGE-0001',
      name: '结算页',
      url_pattern: '/checkout/**',
      source: 'figma',
      source_ref: 'figma-node-1',
      source_version: 'figma-v42',
      component_tree: { type: 'page', children: [{ role: 'button', text: '提交订单' }] },
      screenshot_url: 'https://cdn.example.test/checkout.png',
      project_id: 'proj-payments',
      status: 'deprecated'
    });

    expect(page).toMatchObject({
      id: 'page-1',
      code: 'PAGE-0001',
      name: '结算页',
      urlPattern: '/checkout/**',
      source: 'FIGMA',
      sourceRef: 'figma-node-1',
      sourceVersion: 'figma-v42',
      componentTree: '{"type":"page","children":[{"role":"button","text":"提交订单"}]}',
      screenshotUrl: 'https://cdn.example.test/checkout.png',
      projectId: 'proj-payments',
      status: 'DEPRECATED'
    });

    const list = normalizeAssetPageList({
      data: [{ id: 'page-2', title: '登录页', source: 'manual', status: 'active' }],
      total_elements: '3',
      page_size: '10',
      index: '0'
    });

    expect(list.total).toBe(3);
    expect(list.pageSize).toBe(10);
    expect(list.items[0]).toMatchObject({ id: 'page-2', name: '登录页', source: 'MANUAL', status: 'ACTIVE' });
    expect(assetPageItems([{ id: 'page-3', name: '列表页' }])).toHaveLength(1);
  });

  it('calls page endpoints and compacts page payloads', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-page-1',
      data: { id: 'page-1', name: '结算页' }
    });

    await fetchAssetPages({
      index: 1,
      size: 20,
      projectId: 'proj pay',
      status: 'ACTIVE',
      keyword: '结算',
      source: 'FIGMA'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/asset/pages?index=1&size=20&projectId=proj+pay&status=ACTIVE&keyword=%E7%BB%93%E7%AE%97&source=FIGMA'
    );

    await fetchAssetPage('page 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/pages/page%201');

    await createAssetPage({
      projectId: ' proj-payments ',
      name: ' 结算页 ',
      urlPattern: ' /checkout/** ',
      source: 'FIGMA',
      sourceRef: '',
      sourceVersion: ' figma-v42 ',
      componentTree: { type: 'page' },
      screenshotUrl: ' https://cdn.example.test/checkout.png ',
      status: 'ACTIVE'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/pages', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'proj-payments',
        name: '结算页',
        urlPattern: '/checkout/**',
        source: 'FIGMA',
        sourceVersion: 'figma-v42',
        componentTree: { type: 'page' },
        screenshotUrl: 'https://cdn.example.test/checkout.png',
        status: 'ACTIVE'
      })
    });

    await updateAssetPage('page 1', {
      name: '结算页',
      sourceVersion: ' figma-v43 ',
      componentTree: ['header', 'submit'],
      status: 'DEPRECATED'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/pages/page%201', {
      method: 'PUT',
      body: JSON.stringify({
        name: '结算页',
        sourceVersion: 'figma-v43',
        componentTree: ['header', 'submit'],
        status: 'DEPRECATED'
      })
    });
  });

  it('calls page versions and rollback endpoints', async () => {
    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-page-versions',
      data: [
        {
          id: 'hist-page-1',
          assetType: 'PAGE',
          assetId: 'page-1',
          version: 3,
          changeType: 'ROLLBACK',
          changedFields: ['name', 'componentTree'],
          diff: { name: { before: '结算页V2', after: '结算页' } },
          snapshot: { name: '结算页', componentTree: { type: 'page' }, revision: 3 }
        }
      ]
    });

    const versions = await fetchAssetPageVersions('page 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/pages/page%201/versions');
    expect(versions.data[0]).toMatchObject({
      id: 'hist-page-1',
      assetType: 'PAGE',
      assetId: 'page-1',
      version: 3,
      changeType: 'ROLLBACK',
      changedFields: ['name', 'componentTree'],
      snapshot: { name: '结算页', componentTree: { type: 'page' }, revision: 3 }
    });

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-page-rollback',
      data: { id: 'page-1', name: '结算页', urlPattern: '/checkout/**' }
    });

    const rolledBack = await rollbackAssetPageVersion('page 1', 2);
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/pages/page%201/versions/2/rollback', {
      method: 'POST',
      body: JSON.stringify({})
    });
    expect(rolledBack.data.name).toBe('结算页');
  });

  it('normalizes business flow assets with JSON fields and paged responses', () => {
    const flow = normalizeAssetBusinessFlowView({
      flow_id: 'flow-1',
      code: 'FLOW-0001',
      name: '下单主流程',
      description: '从购物车到支付',
      flow_json: { nodes: ['cart', 'pay'], edges: [['cart', 'pay']] },
      priority: 'high',
      project_id: 'proj-payments',
      status: 'active'
    });

    expect(flow).toMatchObject({
      id: 'flow-1',
      code: 'FLOW-0001',
      name: '下单主流程',
      description: '从购物车到支付',
      flowJson: '{"nodes":["cart","pay"],"edges":[["cart","pay"]]}',
      priority: 'HIGH',
      projectId: 'proj-payments',
      status: 'ACTIVE'
    });

    const list = normalizeAssetBusinessFlowList({
      content: [{ id: 'flow-2', name: '退款流程', priority: 'low', status: 'draft' }],
      totalElements: '4',
      size: '20',
      number: '0'
    });

    expect(list.total).toBe(4);
    expect(list.pageSize).toBe(20);
    expect(list.items[0]).toMatchObject({ id: 'flow-2', name: '退款流程', priority: 'LOW', status: 'DRAFT' });
    expect(assetBusinessFlowItems([{ id: 'flow-3', name: '风控流程' }])).toHaveLength(1);
  });

  it('calls business flow endpoints and compacts business flow payloads', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-flow-1',
      data: { id: 'flow-1', name: '下单主流程' }
    });

    await fetchAssetBusinessFlows({
      index: 1,
      size: 20,
      projectId: 'proj pay',
      status: 'ACTIVE',
      keyword: '下单'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/asset/business-flows?index=1&size=20&projectId=proj+pay&status=ACTIVE&keyword=%E4%B8%8B%E5%8D%95'
    );

    await fetchAssetBusinessFlow('flow 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/business-flows/flow%201');

    await createAssetBusinessFlow({
      projectId: ' proj-payments ',
      name: ' 下单主流程 ',
      description: '',
      flowJson: { nodes: ['cart', 'pay'] },
      priority: ' HIGH ',
      status: 'DRAFT'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/business-flows', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'proj-payments',
        name: '下单主流程',
        flowJson: { nodes: ['cart', 'pay'] },
        priority: 'HIGH',
        status: 'DRAFT'
      })
    });

    await updateAssetBusinessFlow('flow 1', {
      name: '下单主流程',
      description: '更新',
      flowJson: ['cart', 'pay'],
      priority: 'MEDIUM',
      status: 'ACTIVE'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/business-flows/flow%201', {
      method: 'PUT',
      body: JSON.stringify({
        name: '下单主流程',
        description: '更新',
        flowJson: ['cart', 'pay'],
        priority: 'MEDIUM',
        status: 'ACTIVE'
      })
    });
  });

  it('calls business flow versions and rollback endpoints', async () => {
    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-flow-versions',
      data: {
        items: [
          {
            history_id: 'hist-flow-1',
            asset_type: 'BUSINESS_FLOW',
            asset_id: 'flow-1',
            project_id: 'proj-payments',
            version: '4',
            change_type: 'UPDATE',
            changed_fields: ['priority', 'flowJson'],
            diff_json: '{"priority":{"before":"MEDIUM","after":"HIGH"}}',
            snapshot_json: '{"name":"下单主流程","priority":"HIGH","flowJson":{"nodes":["cart","pay"]},"revision":4}',
            trace_id: 'trace-flow-history'
          }
        ]
      }
    });

    const versions = await fetchAssetBusinessFlowVersions('flow 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/business-flows/flow%201/versions');
    expect(versions.data[0]).toMatchObject({
      id: 'hist-flow-1',
      assetType: 'BUSINESS_FLOW',
      assetId: 'flow-1',
      projectId: 'proj-payments',
      version: 4,
      changeType: 'UPDATE',
      changedFields: ['priority', 'flowJson'],
      diff: { priority: { before: 'MEDIUM', after: 'HIGH' } },
      snapshot: { name: '下单主流程', priority: 'HIGH', flowJson: { nodes: ['cart', 'pay'] }, revision: 4 },
      traceId: 'trace-flow-history'
    });

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-flow-rollback',
      data: { id: 'flow-1', name: '下单主流程', priority: 'HIGH' }
    });

    const rolledBack = await rollbackAssetBusinessFlowVersion('flow 1', 3, 'restore flow');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/business-flows/flow%201/versions/3/rollback', {
      method: 'POST',
      body: JSON.stringify({ reason: 'restore flow' })
    });
    expect(rolledBack.data.name).toBe('下单主流程');
  });

  it('normalizes test case assets with ordered steps and paged responses', () => {
    const testCase = normalizeAssetTestCaseView({
      test_case_id: 'case-1',
      code: 'TC-0001',
      name: '登录冒烟用例',
      description: '验证账号密码登录',
      requirement_id: 'req-1',
      api_id: 'api-1',
      source: 'manual',
      source_ref: 'MAN-1',
      project_id: 'proj-payments',
      status: 'reviewing',
      priority: 'critical',
      tags: 'smoke, login',
      version: 4,
      steps: [
        { step_order: 1, action: '点击登录', expected_result: '进入首页' },
        { step_order: 0, action: '输入账号密码', expected_result: '表单校验通过' }
      ]
    });

    expect(testCase).toMatchObject({
      id: 'case-1',
      code: 'TC-0001',
      title: '登录冒烟用例',
      description: '验证账号密码登录',
      requirementId: 'req-1',
      apiId: 'api-1',
      source: 'manual',
      sourceRef: 'MAN-1',
      projectId: 'proj-payments',
      status: 'REVIEWING',
      priority: 'CRITICAL',
      tags: ['smoke', 'login'],
      version: 4,
      steps: [
        { stepOrder: 0, action: '输入账号密码', expectedResult: '表单校验通过' },
        { stepOrder: 1, action: '点击登录', expectedResult: '进入首页' }
      ]
    });

    const list = normalizeAssetTestCaseList({
      records: [{ id: 'case-2', title: '退款用例', priority: 'low', status: 'draft' }],
      totalElements: '5',
      pageSize: '20',
      page: '1'
    });

    expect(list.total).toBe(5);
    expect(list.pageSize).toBe(20);
    expect(list.items[0]).toMatchObject({ id: 'case-2', title: '退款用例', priority: 'LOW', status: 'DRAFT' });
    expect(assetTestCaseItems([{ id: 'case-3', title: '风控用例' }])).toHaveLength(1);
    expect(assetTestCaseStepItems([{ step_order: 2, action: 'B' }, { step_order: 1, action: 'A' }])).toEqual([
      { stepOrder: 1, action: 'A', expectedResult: undefined },
      { stepOrder: 2, action: 'B', expectedResult: undefined }
    ]);
  });

  it('calls test case endpoints and preserves step arrays in payloads', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-case-1',
      data: { id: 'case-1', title: '登录冒烟用例' }
    });

    await fetchAssetTestCases({
      index: 1,
      size: 20,
      projectId: 'proj pay',
      status: 'DRAFT',
      keyword: '登录',
      source: 'MANUAL'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/asset/test-cases?index=1&size=20&projectId=proj+pay&status=DRAFT&keyword=%E7%99%BB%E5%BD%95&source=MANUAL'
    );

    await fetchAssetTestCase('case 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases/case%201');

    await createAssetTestCase({
      projectId: ' proj-payments ',
      title: ' 登录冒烟用例 ',
      description: '',
      requirementId: ' req-1 ',
      apiId: '',
      priority: ' HIGH ',
      status: 'DRAFT',
      tags: ['smoke', ' login ', ''],
      steps: [{ action: ' 输入账号密码 ', expectedResult: ' 登录成功 ' }]
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'proj-payments',
        title: '登录冒烟用例',
        requirementId: 'req-1',
        priority: 'HIGH',
        status: 'DRAFT',
        tags: 'smoke,login',
        steps: [{ action: ' 输入账号密码 ', expectedResult: ' 登录成功 ' }]
      })
    });

    await updateAssetTestCase('case 1', {
      title: '登录冒烟用例',
      description: '更新',
      status: 'REVIEWING',
      priority: 'MEDIUM',
      tags: 'smoke,review'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases/case%201', {
      method: 'PUT',
      body: JSON.stringify({
        title: '登录冒烟用例',
        description: '更新',
        status: 'REVIEWING',
        priority: 'MEDIUM',
        tags: 'smoke,review'
      })
    });

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-case-steps',
      data: [{ step_order: 0, action: '输入账号密码', expected_result: '登录成功' }]
    });
    await fetchAssetTestCaseSteps('case 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases/case%201/steps');

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-case-versions',
      data: {
        items: [
          {
            id: 'hist-case-1',
            asset_type: 'TEST_CASE',
            asset_id: 'case-1',
            version: '3',
            change_type: 'STEPS_UPDATE',
            changed_fields: ['steps'],
            diff: { steps: { before: 1, after: 2 } },
            snapshot: '{"title":"登录冒烟用例","steps":[{"action":"输入账号密码"}]}',
            trace_id: 'trace-case-history'
          }
        ]
      }
    });
    const versions = await fetchAssetTestCaseVersions('case 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases/case%201/versions');
    expect(versions.data[0]).toMatchObject({
      id: 'hist-case-1',
      assetType: 'TEST_CASE',
      assetId: 'case-1',
      version: 3,
      changeType: 'STEPS_UPDATE',
      changedFields: ['steps'],
      diff: { steps: { before: 1, after: 2 } },
      snapshot: { title: '登录冒烟用例', steps: [{ action: '输入账号密码' }] },
      traceId: 'trace-case-history'
    });

    await updateAssetTestCaseSteps('case 1', {
      steps: [
        { action: '输入账号密码', expectedResult: '登录成功' },
        { action: '查看首页', expectedResult: '展示资产库入口' }
      ]
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases/case%201/steps', {
      method: 'PUT',
      body: JSON.stringify({
        steps: [
          { action: '输入账号密码', expectedResult: '登录成功' },
          { action: '查看首页', expectedResult: '展示资产库入口' }
        ]
      })
    });
  });

  it('calls test case rollback endpoint', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-case-rollback', data: { id: 'case-1', title: '旧用例' } });

    const response = await rollbackAssetTestCaseVersion('case 1', 1);

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/test-cases/case%201/versions/1/rollback', {
      method: 'POST',
      body: JSON.stringify({})
    });
    expect(response.data.title).toBe('旧用例');
  });

  it('normalizes trace links and calls trace link endpoints', async () => {
    const links = normalizeTraceLinkList({
      items: [{ link_id: 'link-1', requirement_id: 'req-1', api_id: 'api-1', page_id: 'page-1', flow_id: 'flow-1', test_case_id: 'case-1' }],
      total: '1'
    });
    expect(links).toMatchObject({
      total: 1,
      items: [{ id: 'link-1', requirementId: 'req-1', apiId: 'api-1', pageId: 'page-1', flowId: 'flow-1', caseId: 'case-1' }]
    });

    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-3', data: { items: [] } });
    await fetchAssetTraceLinks({ size: 500 });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/links?size=500');

    await fetchAssetTraceLinks({ apiId: 'api 1', caseId: 'case 1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/links?apiId=api+1&caseId=case+1');

    await fetchAssetTraceLinks({ pageId: 'page 1', flowId: 'flow 1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/links?pageId=page+1&flowId=flow+1');

    await fetchRequirementTraceLinks('req 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/links?requirementId=req+1');
  });

  it('calls import/export, impact analysis and prototype sync endpoints', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-import',
      data: {
        assetType: 'REQUIREMENT',
        format: 'CSV',
        dryRun: true,
        totalRows: 1,
        created: 0,
        updated: 0,
        skipped: 1,
        failed: 0,
        items: [{ row: 1, action: 'LINK_EXISTING', status: 'PLANNED', errors: [] }]
      }
    });

    const imported = await importAssets({
      assetType: 'REQUIREMENT',
      format: 'CSV',
      projectId: 'proj pay',
      dryRun: true,
      content: 'title\nA'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/imports', {
      method: 'POST',
      body: JSON.stringify({
        assetType: 'REQUIREMENT',
        format: 'CSV',
        projectId: 'proj pay',
        dryRun: true,
        content: 'title\nA'
      })
    });
    expect(imported.data.items[0].action).toBe('LINK_EXISTING');

    requestTextMock.mockResolvedValue({ text: 'code,title\nREQ-1,A', traceId: 'trace-export', contentType: 'text/csv', filename: 'wp3-requirement.csv' });
    expect(assetExportPath({ assetType: 'API', format: 'OPENAPI', projectId: 'proj pay' })).toBe('/api/v1/asset/exports?assetType=API&format=OPENAPI&projectId=proj+pay');
    await exportAssetsText({ assetType: 'API', format: 'OPENAPI', projectId: 'proj pay' });
    expect(requestTextMock).toHaveBeenLastCalledWith('/api/v1/asset/exports?assetType=API&format=OPENAPI&projectId=proj+pay');

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-impact',
      data: {
        projectId: 'proj-pay',
        requirementCount: 1,
        apiCount: 1,
        pageCount: 1,
        flowCount: 1,
        caseCount: 1,
        requirements: [{ assetType: 'REQUIREMENT', id: 'req-1', title: '需求' }],
        gaps: ['需求 REQ-1 缺少页面覆盖']
      }
    });
    const impact = await fetchAssetImpactAnalysis({ projectId: 'proj pay', assetType: 'REQUIREMENT', assetId: 'req 1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/impact?projectId=proj+pay&assetType=REQUIREMENT&assetId=req+1');
    expect(impact.data.gaps[0]).toContain('缺少页面');
    expect(normalizeAssetImpactAnalysis({ project_id: 'p', test_cases: [{ id: 'case-1' }] }).testCases[0].id).toBe('case-1');

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-prototype',
      data: { source: 'FIGMA', dryRun: false, totalRows: 1, created: 1, updated: 0, skipped: 0, failed: 0, items: [] }
    });
    const sync = await syncPrototypePages({
      projectId: 'proj-pay',
      source: 'FIGMA',
      dryRun: false,
      pages: [{ name: '登录页', sourceRef: 'node-1' }]
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/prototype-sync', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'proj-pay',
        source: 'FIGMA',
        dryRun: false,
        pages: [{ name: '登录页', sourceRef: 'node-1' }]
      })
    });
    expect(sync.data.created).toBe(1);
  });
});
