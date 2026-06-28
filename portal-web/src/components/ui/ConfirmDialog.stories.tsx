import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { ConfirmDialogProvider, useConfirmDialog } from './ConfirmDialog';

function ConfirmDialogDemo({ tone }: { tone: 'danger' | 'default' }) {
  const confirm = useConfirmDialog();
  const [result, setResult] = useState('尚未选择');

  async function openDialog() {
    const confirmed = await confirm({
      confirmLabel: tone === 'danger' ? '归档' : '继续',
      description: tone === 'danger'
        ? '归档后的资产会从活跃工作台隐藏，但仍可用于审计。'
        : '选中的操作会进入队列，可在执行面板继续跟踪。',
      title: tone === 'danger' ? '归档选中资产？' : '将此操作加入队列？',
      tone
    });
    setResult(confirmed ? '已确认' : '已取消');
  }

  return (
    <div className="storybook-stack">
      <button className={tone === 'danger' ? 'btn btn-danger' : 'btn btn-primary'} type="button" onClick={() => void openDialog()}>
        打开弹窗
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
        component: '用于危险操作和流程拦截的 Promise 确认弹窗。'
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
