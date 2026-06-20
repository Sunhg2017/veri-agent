import { describe, expect, it } from 'vitest';
import { buildAssetTraceTopologyGraph, type AssetTraceSubject } from './assetTraceTopology';
import type {
  AssetApiView,
  AssetBusinessFlowView,
  AssetPageView,
  AssetRequirementView,
  AssetTestCaseView,
  TraceLinkView
} from './api/assets';

const requirements: AssetRequirementView[] = [{
  id: 'req-1',
  title: '支付成功页展示优惠信息',
  source: 'MANUAL',
  status: 'APPROVED',
  priority: 'HIGH',
  projectId: 'proj-pay',
  tags: [],
  version: 1
 }];

const apis: AssetApiView[] = [{
  id: 'api-1',
  summary: '查询支付结果',
  description: 'query payment result',
  httpMethod: 'GET',
  path: '/payments/result',
  projectId: 'proj-pay',
  status: 'ACTIVE'
 }];

const pages: AssetPageView[] = [{
  id: 'page-1',
  name: '支付成功页',
  urlPattern: '/payments/success',
  source: 'FIGMA',
  projectId: 'proj-pay',
  status: 'ACTIVE'
 }];

const flows: AssetBusinessFlowView[] = [{
  id: 'flow-1',
  name: '支付结果查看流程',
  description: 'payment flow',
  priority: 'HIGH',
  projectId: 'proj-pay',
  status: 'ACTIVE'
 }];

const cases: AssetTestCaseView[] = [{
  id: 'case-1',
  title: '支付成功后显示优惠',
  description: 'case',
  requirementId: 'req-1',
  apiId: 'api-1',
  source: 'MANUAL',
  projectId: 'proj-pay',
  status: 'APPROVED',
  priority: 'HIGH',
  tags: [],
  steps: [],
  version: 1
 }];

const links: TraceLinkView[] = [{
  id: 'link-1',
  requirementId: 'req-1',
  apiId: 'api-1',
  pageId: 'page-1',
  flowId: 'flow-1',
  caseId: 'case-1'
 }];

describe('asset trace topology helpers', () => {
  it('builds a five-asset topology around requirement focus', () => {
    const focus: AssetTraceSubject = { type: 'requirement', id: 'req-1' };
    const graph = buildAssetTraceTopologyGraph({
      focus,
      requirements,
      apis,
      pages,
      flows,
      cases,
      links
    });

    expect(graph).not.toBeNull();
    expect(graph?.nodes.map((node) => node.assetType)).toEqual(expect.arrayContaining([
      'requirement',
      'api',
      'page',
      'flow',
      'case'
    ]));
    expect(graph?.columns.requirement).toHaveLength(1);
    expect(graph?.columns.api).toHaveLength(1);
    expect(graph?.columns.page).toHaveLength(1);
    expect(graph?.columns.flow).toHaveLength(1);
    expect(graph?.columns.case).toHaveLength(1);
    expect(graph?.edges.map((edge) => edge.relation)).toEqual(expect.arrayContaining([
      '需求/API',
      '需求/页面',
      '需求/业务流',
      '需求/用例',
      'API/用例'
    ]));
  });

  it('returns null when focus asset is absent', () => {
    const graph = buildAssetTraceTopologyGraph({
      focus: { type: 'api', id: 'missing' },
      requirements,
      apis,
      pages,
      flows,
      cases,
      links
    });

    expect(graph).toBeNull();
  });

  it('marks second-hop nodes as context when traversing from api', () => {
    const graph = buildAssetTraceTopologyGraph({
      focus: { type: 'api', id: 'api-1' },
      requirements,
      apis,
      pages,
      flows,
      cases,
      links
    });

    expect(graph).not.toBeNull();
    const requirementNode = graph?.nodes.find((node) => node.assetId === 'req-1');
    const pageNode = graph?.nodes.find((node) => node.assetId === 'page-1');
    const flowNode = graph?.nodes.find((node) => node.assetId === 'flow-1');

    expect(requirementNode?.emphasis).toBe('neighbor');
    expect(pageNode?.emphasis).toBe('context');
    expect(flowNode?.emphasis).toBe('context');
  });
});
