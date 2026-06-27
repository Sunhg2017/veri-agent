import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { ConfirmDialogProvider, useConfirmDialog } from './ConfirmDialog';

function ConfirmDialogDemo({ tone }: { tone: 'danger' | 'default' }) {
  const confirm = useConfirmDialog();
  const [result, setResult] = useState('No decision yet');

  async function openDialog() {
    const confirmed = await confirm({
      confirmLabel: tone === 'danger' ? 'Archive' : 'Continue',
      description: tone === 'danger'
        ? 'Archived assets are hidden from active workbench views but remain available for audit.'
        : 'The selected operation will be queued and can be tracked from the execution panel.',
      title: tone === 'danger' ? 'Archive selected asset?' : 'Queue this operation?',
      tone
    });
    setResult(confirmed ? 'Confirmed' : 'Cancelled');
  }

  return (
    <div className="storybook-stack">
      <button className={tone === 'danger' ? 'btn btn-danger' : 'btn btn-primary'} type="button" onClick={() => void openDialog()}>
        Open dialog
      </button>
      <span className="table-secondary">{result}</span>
    </div>
  );
}

const meta = {
  title: 'Components/ConfirmDialog',
  component: ConfirmDialogDemo,
  decorators: [
    (Story) => (
      <ConfirmDialogProvider>
        <Story />
      </ConfirmDialogProvider>
    )
  ],
  parameters: {
    docs: {
      description: {
        component: 'Promise-based confirmation dialog for destructive and workflow-gating actions.'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta<typeof ConfirmDialogDemo>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    tone: 'default'
  },
  render: ({ tone }) => <ConfirmDialogDemo tone={tone} />
};

export const Danger: Story = {
  args: {
    tone: 'danger'
  },
  render: ({ tone }) => <ConfirmDialogDemo tone={tone} />
};
