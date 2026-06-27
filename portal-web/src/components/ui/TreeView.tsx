import { ChevronDown, ChevronRight } from 'lucide-react';
import { useMemo, useState, type CSSProperties } from 'react';
import { translate } from '../../platform/i18n';

export interface TreeNode {
  children?: TreeNode[];
  disabled?: boolean;
  id: string;
  label: string;
  meta?: string;
}

export interface TreeViewProps {
  ariaLabel?: string;
  defaultExpandedIds?: readonly string[];
  emptyLabel?: string;
  nodes: readonly TreeNode[];
  onSelect?: (node: TreeNode) => void;
  selectedId?: string;
}

export function TreeView({
  ariaLabel,
  defaultExpandedIds = [],
  emptyLabel = translate('auto.k0328'),
  nodes,
  onSelect,
  selectedId
}: TreeViewProps) {
  const [expandedIds, setExpandedIds] = useState(() => new Set(defaultExpandedIds));
  const flattened = useMemo(() => flattenNodes(nodes, expandedIds), [expandedIds, nodes]);

  function toggle(nodeId: string) {
    setExpandedIds((current) => {
      const next = new Set(current);
      if (next.has(nodeId)) next.delete(nodeId);
      else next.add(nodeId);
      return next;
    });
  }

  if (!nodes.length) {
    return <div className="ui-tree-empty">{emptyLabel}</div>;
  }

  return (
    <div aria-label={ariaLabel} className="ui-tree" role="tree">
      {flattened.map(({ depth, node }) => {
        const hasChildren = Boolean(node.children?.length);
        const expanded = expandedIds.has(node.id);
        return (
          <div
            aria-disabled={node.disabled || undefined}
            aria-expanded={hasChildren ? expanded : undefined}
            aria-level={depth + 1}
            aria-selected={selectedId === node.id}
            className={`ui-tree-node${selectedId === node.id ? ' selected' : ''}${node.disabled ? ' disabled' : ''}`}
            key={node.id}
            role="treeitem"
            style={{ '--tree-depth': depth } as CSSProperties}
          >
            {hasChildren ? (
              <button
                aria-label={translate(expanded ? 'auto.k2609' : 'auto.k2608', { value0: node.label })}
                className="ui-tree-toggle"
                type="button"
                onClick={() => toggle(node.id)}
              >
                {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              </button>
            ) : (
              <span aria-hidden="true" className="ui-tree-toggle ui-tree-spacer" />
            )}
            <button
              className="ui-tree-label"
              disabled={node.disabled}
              type="button"
              onClick={() => onSelect?.(node)}
            >
              <span>{node.label}</span>
              {node.meta ? <small>{node.meta}</small> : null}
            </button>
          </div>
        );
      })}
    </div>
  );
}

function flattenNodes(nodes: readonly TreeNode[], expandedIds: Set<string>, depth = 0): Array<{ depth: number; node: TreeNode }> {
  return nodes.flatMap((node) => {
    const current = [{ depth, node }];
    if (!expandedIds.has(node.id) || !node.children?.length) {
      return current;
    }
    return current.concat(flattenNodes(node.children, expandedIds, depth + 1));
  });
}
