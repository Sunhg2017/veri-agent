import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TreeView, type TreeNode } from './TreeView';

const nodes: TreeNode[] = [
  {
    id: 'root',
    label: 'Workspace',
    meta: '2',
    children: [
      { id: 'app', label: 'Application' },
      { id: 'env', label: 'Environment', disabled: true }
    ]
  }
];

describe('TreeView', () => {
  it('expands nested nodes and selects enabled items', () => {
    const onSelect = vi.fn();
    render(<TreeView ariaLabel="Resource tree" nodes={nodes} onSelect={onSelect} />);

    expect(screen.getByRole('tree', { name: 'Resource tree' })).toBeInTheDocument();
    expect(screen.queryByText('Application')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '展开Workspace' }));
    fireEvent.click(screen.getByRole('button', { name: 'Application' }));

    expect(onSelect).toHaveBeenCalledWith(nodes[0].children![0]);
    expect(screen.getByRole('treeitem', { name: /Workspace/ })).toHaveAttribute('aria-expanded', 'true');
  });

  it('renders selected and empty states', () => {
    const { rerender } = render(<TreeView defaultExpandedIds={['root']} nodes={nodes} selectedId="app" />);
    expect(screen.getByRole('treeitem', { name: /Application/ })).toHaveAttribute('aria-selected', 'true');

    rerender(<TreeView emptyLabel="No nodes" nodes={[]} />);
    expect(screen.getByText('No nodes')).toBeInTheDocument();
  });
});
