import type {
  AssetApiView,
  AssetBusinessFlowView,
  AssetPageView,
  AssetRequirementView,
  AssetTestCaseView,
  TraceLinkView
} from './api/assets';
import { translate } from './platform/i18n';

export type AssetTraceSubjectType = 'requirement' | 'api' | 'page' | 'flow' | 'case';

export type AssetTraceSubject = {
  type: AssetTraceSubjectType;
  id: string;
};

export type AssetTraceTopologyNode = {
  key: string;
  assetType: AssetTraceSubjectType;
  assetId: string;
  title: string;
  meta: string;
  projectId?: string;
  status?: string;
  distance: number;
  emphasis: 'focus' | 'neighbor' | 'context';
};

export type AssetTraceTopologyEdge = {
  key: string;
  sourceKey: string;
  targetKey: string;
  relation: string;
  emphasis: boolean;
};

export type AssetTraceTopologyGraph = {
  focus: AssetTraceSubject;
  columns: Record<AssetTraceSubjectType, AssetTraceTopologyNode[]>;
  nodes: AssetTraceTopologyNode[];
  edges: AssetTraceTopologyEdge[];
  hiddenNodeCount: number;
  hiddenEdgeCount: number;
  maxDepth: number;
};

type RegistryNode = Omit<AssetTraceTopologyNode, 'distance' | 'emphasis'>;

type RegistryEdge = {
  key: string;
  sourceKey: string;
  targetKey: string;
  relation: string;
};

type AssetRegistry = {
  nodes: Map<string, RegistryNode>;
  edges: RegistryEdge[];
  adjacency: Map<string, Set<string>>;
};

const DEFAULT_MAX_DEPTH = 2;
const TOPOLOGY_NODE_LIMITS: Record<AssetTraceSubjectType, number> = {
  requirement: 6,
  api: 8,
  page: 6,
  flow: 6,
  case: 10
};

export const ASSET_TRACE_TOPOLOGY_COLUMNS = ['requirement', 'api', 'page', 'flow', 'case'] as const satisfies readonly AssetTraceSubjectType[];

export function assetTraceSubjectKey(subject: AssetTraceSubject) {
  return `${subject.type}:${subject.id}`;
}

export function labelAssetTraceSubjectType(type: AssetTraceSubjectType) {
  switch (type) {
    case 'requirement':
      return translate('auto.k0133');
    case 'api':
      return 'API';
    case 'page':
      return translate('auto.k0134');
    case 'flow':
      return translate('auto.k0135');
    case 'case':
      return translate('auto.k0136');
    default:
      return type;
  }
}

export function buildAssetTraceTopologyGraph(input: {
  apis: AssetApiView[];
  cases: AssetTestCaseView[];
  flows: AssetBusinessFlowView[];
  focus: AssetTraceSubject | null;
  links: TraceLinkView[];
  maxDepth?: number;
  pages: AssetPageView[];
  requirements: AssetRequirementView[];
}): AssetTraceTopologyGraph | null {
  if (!input.focus?.id) {
    return null;
  }

  const registry = buildRegistry(input);
  const focusKey = assetTraceSubjectKey(input.focus);
  if (!registry.nodes.has(focusKey)) {
    return null;
  }

  const maxDepth = typeof input.maxDepth === 'number' && input.maxDepth > 0 ? input.maxDepth : DEFAULT_MAX_DEPTH;
  const distances = breadthFirstDistances(registry.adjacency, focusKey, maxDepth);
  const candidateNodes = Array.from(distances.entries())
    .map(([key, distance]) => registry.nodes.get(key) && toTopologyNode(registry.nodes.get(key) as RegistryNode, distance))
    .filter((node): node is AssetTraceTopologyNode => Boolean(node));
  const candidateNodeMap = new Map(candidateNodes.map((node) => [node.key, node]));
  const candidateEdges = registry.edges.filter((edge) => candidateNodeMap.has(edge.sourceKey) && candidateNodeMap.has(edge.targetKey));

  const columns = emptyColumns();
  const retainedKeys = new Set<string>();

  ASSET_TRACE_TOPOLOGY_COLUMNS.forEach((type) => {
    const sorted = candidateNodes
      .filter((node) => node.assetType === type)
      .sort(compareTopologyNodes);
    const limited = limitNodesForColumn(sorted, type, focusKey);
    columns[type] = limited;
    limited.forEach((node) => retainedKeys.add(node.key));
  });

  const nodes = ASSET_TRACE_TOPOLOGY_COLUMNS.flatMap((type) => columns[type]);
  const nodeByKey = new Map(nodes.map((node) => [node.key, node]));
  const edges = candidateEdges
    .filter((edge) => retainedKeys.has(edge.sourceKey) && retainedKeys.has(edge.targetKey))
    .map((edge) => ({
      ...edge,
      emphasis: edgeTouchesFocusOrNeighbor(edge, nodeByKey)
    }))
    .sort((left, right) => left.key.localeCompare(right.key, 'zh-CN'));

  return {
    focus: input.focus,
    columns,
    nodes,
    edges,
    hiddenNodeCount: Math.max(candidateNodes.length - nodes.length, 0),
    hiddenEdgeCount: Math.max(candidateEdges.length - edges.length, 0),
    maxDepth
  };
}

function buildRegistry(input: {
  apis: AssetApiView[];
  cases: AssetTestCaseView[];
  flows: AssetBusinessFlowView[];
  links: TraceLinkView[];
  pages: AssetPageView[];
  requirements: AssetRequirementView[];
}): AssetRegistry {
  const nodes = new Map<string, RegistryNode>();
  const edges = new Map<string, RegistryEdge>();
  const adjacency = new Map<string, Set<string>>();

  input.requirements.forEach((item) => {
    registerNode(nodes, {
      key: assetTraceSubjectKey({ type: 'requirement', id: item.id }),
      assetType: 'requirement',
      assetId: item.id,
      title: item.title,
      meta: compactMeta(item.status, item.priority, item.projectId),
      projectId: item.projectId,
      status: item.status
    });
  });

  input.apis.forEach((item) => {
    registerNode(nodes, {
      key: assetTraceSubjectKey({ type: 'api', id: item.id }),
      assetType: 'api',
      assetId: item.id,
      title: `${item.httpMethod} ${item.path}`,
      meta: compactMeta(item.summary, item.status),
      projectId: item.projectId,
      status: item.status
    });
  });

  input.pages.forEach((item) => {
    registerNode(nodes, {
      key: assetTraceSubjectKey({ type: 'page', id: item.id }),
      assetType: 'page',
      assetId: item.id,
      title: item.name,
      meta: compactMeta(item.urlPattern, item.status),
      projectId: item.projectId,
      status: item.status
    });
  });

  input.flows.forEach((item) => {
    registerNode(nodes, {
      key: assetTraceSubjectKey({ type: 'flow', id: item.id }),
      assetType: 'flow',
      assetId: item.id,
      title: item.name,
      meta: compactMeta(item.status, item.priority, item.projectId),
      projectId: item.projectId,
      status: item.status
    });
  });

  input.cases.forEach((item) => {
    registerNode(nodes, {
      key: assetTraceSubjectKey({ type: 'case', id: item.id }),
      assetType: 'case',
      assetId: item.id,
      title: item.title,
      meta: compactMeta(item.status, item.priority, item.projectId),
      projectId: item.projectId,
      status: item.status
    });
  });

  input.links.forEach((link) => {
    addEdge(nodes, edges, adjacency, 'requirement', link.requirementId, 'api', link.apiId, translate('auto.k0137'));
    addEdge(nodes, edges, adjacency, 'requirement', link.requirementId, 'page', link.pageId, translate('auto.k0138'));
    addEdge(nodes, edges, adjacency, 'requirement', link.requirementId, 'flow', link.flowId, translate('auto.k0139'));
    addEdge(nodes, edges, adjacency, 'requirement', link.requirementId, 'case', link.caseId, translate('auto.k0140'));
    addEdge(nodes, edges, adjacency, 'api', link.apiId, 'case', link.caseId, translate('auto.k0141'));
  });

  input.cases.forEach((item) => {
    addEdge(nodes, edges, adjacency, 'requirement', item.requirementId, 'case', item.id, translate('auto.k0140'));
    addEdge(nodes, edges, adjacency, 'api', item.apiId, 'case', item.id, translate('auto.k0141'));
  });

  return {
    nodes,
    edges: Array.from(edges.values()),
    adjacency
  };
}

function registerNode(map: Map<string, RegistryNode>, node: RegistryNode) {
  if (!node.assetId) {
    return;
  }
  map.set(node.key, node);
}

function addEdge(
  nodes: Map<string, RegistryNode>,
  edges: Map<string, RegistryEdge>,
  adjacency: Map<string, Set<string>>,
  sourceType: AssetTraceSubjectType,
  sourceId: string | undefined,
  targetType: AssetTraceSubjectType,
  targetId: string | undefined,
  relation: string
) {
  if (!sourceId || !targetId) {
    return;
  }
  const sourceKey = assetTraceSubjectKey({ type: sourceType, id: sourceId });
  const targetKey = assetTraceSubjectKey({ type: targetType, id: targetId });
  if (sourceKey === targetKey || !nodes.has(sourceKey) || !nodes.has(targetKey)) {
    return;
  }
  const [left, right] = [sourceKey, targetKey].sort();
  const edgeKey = `${left}<->${right}`;
  if (!edges.has(edgeKey)) {
    edges.set(edgeKey, {
      key: edgeKey,
      sourceKey,
      targetKey,
      relation
    });
  }
  addNeighbor(adjacency, sourceKey, targetKey);
  addNeighbor(adjacency, targetKey, sourceKey);
}

function addNeighbor(adjacency: Map<string, Set<string>>, key: string, neighbor: string) {
  const next = adjacency.get(key) ?? new Set<string>();
  next.add(neighbor);
  adjacency.set(key, next);
}

function breadthFirstDistances(adjacency: Map<string, Set<string>>, focusKey: string, maxDepth: number) {
  const distances = new Map<string, number>([[focusKey, 0]]);
  const queue: Array<{ depth: number; key: string }> = [{ key: focusKey, depth: 0 }];

  while (queue.length > 0) {
    const current = queue.shift();
    if (!current) {
      break;
    }
    if (current.depth >= maxDepth) {
      continue;
    }
    for (const neighbor of adjacency.get(current.key) ?? []) {
      if (distances.has(neighbor)) {
        continue;
      }
      const nextDepth = current.depth + 1;
      distances.set(neighbor, nextDepth);
      queue.push({ key: neighbor, depth: nextDepth });
    }
  }

  return distances;
}

function toTopologyNode(node: RegistryNode, distance: number): AssetTraceTopologyNode {
  return {
    ...node,
    distance,
    emphasis: distance === 0 ? 'focus' : distance === 1 ? 'neighbor' : 'context'
  };
}

function emptyColumns(): Record<AssetTraceSubjectType, AssetTraceTopologyNode[]> {
  return {
    requirement: [],
    api: [],
    page: [],
    flow: [],
    case: []
  };
}

function limitNodesForColumn(nodes: AssetTraceTopologyNode[], type: AssetTraceSubjectType, focusKey: string) {
  const limit = TOPOLOGY_NODE_LIMITS[type];
  if (nodes.length <= limit) {
    return nodes;
  }
  if (!nodes.some((node) => node.key === focusKey)) {
    return nodes.slice(0, limit);
  }
  const focusNode = nodes.find((node) => node.key === focusKey);
  const remainder = nodes.filter((node) => node.key !== focusKey).slice(0, Math.max(limit - 1, 0));
  return focusNode ? [focusNode, ...remainder] : remainder;
}

function compareTopologyNodes(left: AssetTraceTopologyNode, right: AssetTraceTopologyNode) {
  if (left.distance !== right.distance) {
    return left.distance - right.distance;
  }
  return left.title.localeCompare(right.title, 'zh-CN');
}

function edgeTouchesFocusOrNeighbor(edge: RegistryEdge, nodeByKey: Map<string, AssetTraceTopologyNode>) {
  const source = nodeByKey.get(edge.sourceKey);
  const target = nodeByKey.get(edge.targetKey);
  if (!source || !target) {
    return false;
  }
  return source.distance === 0
    || target.distance === 0
    || (source.distance <= 1 && target.distance <= 1);
}

function compactMeta(...parts: Array<string | undefined>) {
  return parts
    .map((part) => (typeof part === 'string' ? part.trim() : ''))
    .filter(Boolean)
    .join(' · ');
}
