import { Eye, GitBranch, Link2 } from 'lucide-react';
import {
  ASSET_TRACE_TOPOLOGY_COLUMNS,
  assetTraceSubjectKey,
  labelAssetTraceSubjectType,
  type AssetTraceSubject,
  type AssetTraceSubjectType,
  type AssetTraceTopologyGraph,
  type AssetTraceTopologyNode
} from '../assetTraceTopology';
import type { AssetNavigationKey } from './AssetStructuredWorkbench';

const LANE_WIDTH = 188;
const NODE_WIDTH = 164;
const NODE_HEIGHT = 78;
const COLUMN_GAP = 28;
const ROW_GAP = 18;
const HEADER_HEIGHT = 40;
const GRAPH_PADDING = 12;

export function AssetTraceTopologyPanel(props: {
  graph: AssetTraceTopologyGraph | null;
  onOpenAsset: (tab: AssetNavigationKey, id: string) => void;
  onSelectSubject: (subject: AssetTraceSubject) => void;
}) {
  if (!props.graph || props.graph.nodes.length === 0) {
    return (
      <div className="empty-state compact">
        <GitBranch size={20} />
        <div>
          <strong>暂无拓扑节点</strong>
          <span>选择需求、API、页面、业务流或用例后展示关系拓扑</span>
        </div>
      </div>
    );
  }

  const graph = props.graph;
  const layout = buildGraphLayout(graph);

  return (
    <div className="trace-topology-panel">
      <div className="trace-topology-summary">
        <span>展示 {graph.nodes.length} 个节点 / {graph.edges.length} 条关系</span>
        <span>分析深度 {graph.maxDepth} 跳</span>
        {graph.hiddenNodeCount > 0 || graph.hiddenEdgeCount > 0 ? (
          <span>已折叠 {graph.hiddenNodeCount} 个节点、{graph.hiddenEdgeCount} 条关系</span>
        ) : null}
      </div>

      <div className="trace-topology-scroller">
        <div
          className="trace-topology-graph"
          style={{
            height: `${layout.height}px`,
            width: `${layout.width}px`
          }}
        >
          <svg
            aria-hidden="true"
            className="trace-topology-svg"
            viewBox={`0 0 ${layout.width} ${layout.height}`}
            preserveAspectRatio="xMidYMin meet"
          >
            {graph.edges.map((edge) => {
              const source = layout.nodePositions.get(edge.sourceKey);
              const target = layout.nodePositions.get(edge.targetKey);
              if (!source || !target) {
                return null;
              }
              const sourceCenterX = source.x + NODE_WIDTH / 2;
              const sourceCenterY = source.y + NODE_HEIGHT / 2;
              const targetCenterX = target.x + NODE_WIDTH / 2;
              const targetCenterY = target.y + NODE_HEIGHT / 2;
              const direction = targetCenterX >= sourceCenterX ? 1 : -1;
              const controlOffset = Math.max(Math.abs(targetCenterX - sourceCenterX) * 0.3, 36);
              const path = [
                `M ${sourceCenterX} ${sourceCenterY}`,
                `C ${sourceCenterX + controlOffset * direction} ${sourceCenterY},`,
                `${targetCenterX - controlOffset * direction} ${targetCenterY},`,
                `${targetCenterX} ${targetCenterY}`
              ].join(' ');
              return (
                <path
                  key={edge.key}
                  className={`trace-topology-edge ${edge.emphasis ? 'emphasis' : ''}`}
                  d={path}
                />
              );
            })}
          </svg>

          {ASSET_TRACE_TOPOLOGY_COLUMNS.map((type) => {
            const lane = layout.lanes.get(type);
            if (!lane) {
              return null;
            }
            return (
              <div
                className="trace-topology-lane"
                key={type}
                style={{
                  left: `${lane.x}px`,
                  top: `${GRAPH_PADDING}px`,
                  width: `${LANE_WIDTH}px`
                }}
              >
                <div className="trace-topology-lane-label">
                  <span>{labelAssetTraceSubjectType(type)}</span>
                  <strong>{graph.columns[type].length}</strong>
                </div>
              </div>
            );
          })}

          {graph.nodes.map((node) => {
            const position = layout.nodePositions.get(node.key);
            if (!position) {
              return null;
            }
            return (
              <div
                className="trace-topology-node-shell"
                key={node.key}
                style={{
                  left: `${position.x}px`,
                  top: `${position.y}px`,
                  width: `${NODE_WIDTH}px`
                }}
              >
                <button
                  className={`trace-topology-node ${node.emphasis} ${node.assetType}`}
                  type="button"
                  onClick={() => props.onSelectSubject({ type: node.assetType, id: node.assetId })}
                >
                  <span className="trace-topology-node-type">{labelAssetTraceSubjectType(node.assetType)}</span>
                  <strong>{node.title}</strong>
                  <em>{node.meta || node.assetId}</em>
                </button>
                <button
                  className="mini-button icon-only trace-topology-open"
                  type="button"
                  title={`打开${labelAssetTraceSubjectType(node.assetType)}详情`}
                  onClick={() => props.onOpenAsset(subjectTypeToTab(node.assetType), node.assetId)}
                >
                  <Eye size={14} />
                </button>
              </div>
            );
          })}
        </div>
      </div>

      <div className="trace-topology-footer">
        <div className="trace-topology-legend">
          <span className="focus">焦点</span>
          <span className="neighbor">一跳</span>
          <span className="context">二跳</span>
        </div>
        <div className="trace-topology-hint">
          <Link2 size={14} />
          <span>点击节点切换焦点，右上角图标可打开资产详情</span>
        </div>
      </div>
    </div>
  );
}

function buildGraphLayout(graph: AssetTraceTopologyGraph) {
  const lanes = new Map<AssetTraceSubjectType, { x: number }>();
  const nodePositions = new Map<string, { x: number; y: number }>();
  const rows = Math.max(...ASSET_TRACE_TOPOLOGY_COLUMNS.map((type) => graph.columns[type].length), 1);
  const width = GRAPH_PADDING * 2 + ASSET_TRACE_TOPOLOGY_COLUMNS.length * LANE_WIDTH + (ASSET_TRACE_TOPOLOGY_COLUMNS.length - 1) * COLUMN_GAP;
  const height = GRAPH_PADDING * 2 + HEADER_HEIGHT + rows * NODE_HEIGHT + Math.max(rows - 1, 0) * ROW_GAP;

  ASSET_TRACE_TOPOLOGY_COLUMNS.forEach((type, columnIndex) => {
    const laneX = GRAPH_PADDING + columnIndex * (LANE_WIDTH + COLUMN_GAP);
    lanes.set(type, { x: laneX });
    graph.columns[type].forEach((node, rowIndex) => {
      nodePositions.set(node.key, {
        x: laneX + (LANE_WIDTH - NODE_WIDTH) / 2,
        y: GRAPH_PADDING + HEADER_HEIGHT + rowIndex * (NODE_HEIGHT + ROW_GAP)
      });
    });
  });

  return {
    height,
    lanes,
    nodePositions,
    width
  };
}

function subjectTypeToTab(type: AssetTraceSubjectType): AssetNavigationKey {
  switch (type) {
    case 'requirement':
      return 'requirements';
    case 'api':
      return 'apis';
    case 'page':
      return 'pages';
    case 'flow':
      return 'flows';
    case 'case':
      return 'cases';
    default:
      return 'trace';
  }
}

export function describeTopologyFocus(subject: AssetTraceSubject | null, nodes: AssetTraceTopologyNode[]) {
  if (!subject) {
    return '未选择焦点资产';
  }
  const key = assetTraceSubjectKey(subject);
  const node = nodes.find((item) => item.key === key);
  if (!node) {
    return `${labelAssetTraceSubjectType(subject.type)}未出现在当前拓扑中`;
  }
  return `${labelAssetTraceSubjectType(subject.type)} · ${node.title}`;
}
