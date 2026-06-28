import type { Meta, StoryObj } from '@storybook/react-vite';
import { RefreshCw } from 'lucide-react';
import { EmptyState, SkeletonBlock, Spinner } from './State';

const meta = {
  title: 'Components/State',
  parameters: {
    docs: {
      description: {
        component: '用于加载、骨架屏和空内容状态的共享状态组件。'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

export const LoadingSpinner: Story = {
  render: () => <Spinner label="正在刷新工作台数据" />
};

export const Skeleton: Story = {
  render: () => <SkeletonBlock rows={4} />
};

export const Empty: Story = {
  render: () => (
    <EmptyState
      action={(
        <button className="btn btn-secondary btn-sm" type="button">
          <RefreshCw size={14} />
          刷新
        </button>
      )}
      description="调整筛选条件或刷新数据源后重新加载记录。"
      title="暂无记录"
    />
  )
};
