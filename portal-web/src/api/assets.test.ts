import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_REQUIREMENT_SOURCES,
  ASSET_REQUIREMENT_STATUSES,
  assetRequirementItems,
  createAssetRequirement,
  fetchAssetRequirement,
  fetchAssetRequirements,
  fetchRequirementTraceLinks,
  normalizeAssetHealth,
  normalizeAssetRequirementList,
  normalizeAssetRequirementView,
  normalizeTraceLinkList,
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
      '/api/v1/asset/requirements?index=1&size=20&projectId=proj+pay&status=DRAFT&keyword=%E7%99%BB%E5%BD%95&source=IMPORT&sourceRef=PRD-1'
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
