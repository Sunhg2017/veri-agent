import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  ASSET_API_METHODS,
  ASSET_API_STATUSES,
  ASSET_FLOW_STATUSES,
  ASSET_PAGE_SOURCES,
  ASSET_PAGE_STATUSES,
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_REQUIREMENT_SOURCES,
  ASSET_REQUIREMENT_STATUSES,
  assetApiItems,
  assetBusinessFlowItems,
  assetPageItems,
  assetRequirementItems,
  createAssetApi,
  createAssetBusinessFlow,
  createAssetPage,
  createAssetRequirement,
  fetchAssetApi,
  fetchAssetApis,
  fetchAssetBusinessFlow,
  fetchAssetBusinessFlows,
  fetchAssetPage,
  fetchAssetPages,
  fetchAssetRequirement,
  fetchAssetRequirements,
  fetchRequirementTraceLinks,
  normalizeAssetApiList,
  normalizeAssetApiView,
  normalizeAssetBusinessFlowList,
  normalizeAssetBusinessFlowView,
  normalizeAssetHealth,
  normalizeAssetPageList,
  normalizeAssetPageView,
  normalizeAssetRequirementList,
  normalizeAssetRequirementView,
  normalizeTraceLinkList,
  updateAssetApi,
  updateAssetBusinessFlow,
  updateAssetPage,
  updateAssetRequirement
} from './assets';

vi.mock('./client', () => ({
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);

describe('asset API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
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
      tags: ['auth', 'mobile']
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
        requestSchema: '{"type":"object"}',
        status: 'ACTIVE'
      })
    });

    await updateAssetApi('api 1', {
      summary: '查询订单',
      description: '按订单号查询',
      httpMethod: ' GET ',
      path: ' /api/orders/{id} ',
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
        responseSchema: '{"type":"object","properties":{"id":{"type":"string"}}}',
        status: 'DEPRECATED'
      })
    });
  });

  it('normalizes page assets with JSON fields and paged responses', () => {
    const page = normalizeAssetPageView({
      page_id: 'page-1',
      code: 'PAGE-0001',
      name: '结算页',
      url_pattern: '/checkout/**',
      source: 'figma',
      source_ref: 'figma-node-1',
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
        componentTree: { type: 'page' },
        screenshotUrl: 'https://cdn.example.test/checkout.png',
        status: 'ACTIVE'
      })
    });

    await updateAssetPage('page 1', {
      name: '结算页',
      componentTree: ['header', 'submit'],
      status: 'DEPRECATED'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/pages/page%201', {
      method: 'PUT',
      body: JSON.stringify({
        name: '结算页',
        componentTree: ['header', 'submit'],
        status: 'DEPRECATED'
      })
    });
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

  it('normalizes trace links and calls the requirement link endpoint', async () => {
    const links = normalizeTraceLinkList({
      items: [{ link_id: 'link-1', requirement_id: 'req-1', api_id: 'api-1', test_case_id: 'case-1' }],
      total: '1'
    });
    expect(links).toMatchObject({
      total: 1,
      items: [{ id: 'link-1', requirementId: 'req-1', apiId: 'api-1', caseId: 'case-1' }]
    });

    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-3', data: { items: [] } });
    await fetchRequirementTraceLinks('req 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/asset/links?requirementId=req%201');
  });
});
