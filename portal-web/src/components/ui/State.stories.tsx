import type { Meta, StoryObj } from '@storybook/react-vite';
import { RefreshCw } from 'lucide-react';
import { EmptyState, SkeletonBlock, Spinner } from './State';

const meta = {
  title: 'Components/State',
  parameters: {
    docs: {
      description: {
        component: 'Shared status primitives for loading, skeleton and empty content states.'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

export const LoadingSpinner: Story = {
  render: () => <Spinner label="Refreshing workbench data" />
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
          Refresh
        </button>
      )}
      description="Adjust filters or refresh the source to load records."
      title="No records found"
    />
  )
};
