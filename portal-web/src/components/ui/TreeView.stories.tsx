import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { TreeView, type TreeNode } from './TreeView';

const nodes: TreeNode[] = [
  {
    id: 'wp3',
    label: 'WP3 Asset Library',
    meta: '6 modules',
    children: [
      { id: 'requirements', label: 'Requirements', meta: '128' },
      { id: 'apis', label: 'API contracts', meta: '42' },
      { id: 'flows', label: 'Business flows', meta: '17' }
    ]
  },
  {
    id: 'wp5',
    label: 'WP5 Test Design',
    meta: '4 modules',
    children: [
      { id: 'generation', label: 'Generation tasks', meta: '23' },
      { id: 'review', label: 'Candidate review', meta: '9' },
      { disabled: true, id: 'archive', label: 'Archived reports', meta: 'locked' }
    ]
  }
];

const meta = {
  title: 'Components/TreeView',
  component: TreeView,
  parameters: {
    docs: {
      description: {
        component: 'Hierarchical tree primitive for compact navigation, filters and asset outlines.'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta<typeof TreeView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    nodes
  },
  render: function DefaultStory() {
    const [selectedId, setSelectedId] = useState('requirements');
    return (
      <TreeView
        ariaLabel="Workbench module tree"
        defaultExpandedIds={['wp3', 'wp5']}
        nodes={nodes}
        onSelect={(node) => setSelectedId(node.id)}
        selectedId={selectedId}
      />
    );
  }
};

export const Empty: Story = {
  args: {
    emptyLabel: 'No navigation nodes are available.',
    nodes: []
  }
};
