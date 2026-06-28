import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { TreeView, type TreeNode } from './TreeView';

const nodes: TreeNode[] = [
  {
    id: 'wp3',
    label: 'WP3 资产库',
    meta: '6 个模块',
    children: [
      { id: 'requirements', label: '需求', meta: '128' },
      { id: 'apis', label: '接口契约', meta: '42' },
      { id: 'flows', label: '业务流', meta: '17' }
    ]
  },
  {
    id: 'wp5',
    label: 'WP5 测试设计',
    meta: '4 个模块',
    children: [
      { id: 'generation', label: '生成任务', meta: '23' },
      { id: 'review', label: '候选评审', meta: '9' },
      { disabled: true, id: 'archive', label: '已归档报告', meta: '锁定' }
    ]
  }
];

const meta = {
  title: 'Components/TreeView',
  component: TreeView,
  parameters: {
    docs: {
      description: {
        component: '用于紧凑导航、筛选和资产大纲的层级树组件。'
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
        ariaLabel="工作台模块树"
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
    emptyLabel: '暂无可用导航节点。',
    nodes: []
  }
};
