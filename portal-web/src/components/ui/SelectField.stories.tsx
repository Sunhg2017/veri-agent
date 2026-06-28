import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { SelectField, type SelectOption } from './SelectField';

const options: SelectOption[] = [
  { label: '生产环境', value: 'prod' },
  { label: '预发环境', value: 'stage' },
  { label: '测试沙箱', value: 'qa' },
  { disabled: true, label: '已归档环境', value: 'archive' }
];

const meta = {
  title: 'Components/SelectField',
  component: SelectField,
  parameters: {
    docs: {
      description: {
        component: '带标签、占位、提示、错误和禁用状态的表单下拉组件。'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta<typeof SelectField>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: '环境',
    onChange: () => undefined,
    options,
    value: 'stage'
  },
  render: function DefaultStory() {
    const [value, setValue] = useState('stage');
    return (
      <SelectField
        hint="用于限定 API Mock 和执行计划范围。"
        label="环境"
        onChange={setValue}
        options={options}
        value={value}
      />
    );
  }
};

export const RequiredWithError: Story = {
  args: {
    label: '环境',
    onChange: () => undefined,
    options,
    required: true,
    value: ''
  },
  render: function RequiredWithErrorStory() {
    const [value, setValue] = useState('');
    return (
      <SelectField
        error="调度运行前必须选择环境。"
        label="环境"
        onChange={setValue}
        options={options}
        required
        value={value}
      />
    );
  }
};

export const Disabled: Story = {
  args: {
    disabled: true,
    hint: '当前审批待处理时锁定。',
    label: '环境',
    onChange: () => undefined,
    options,
    value: 'prod'
  }
};
