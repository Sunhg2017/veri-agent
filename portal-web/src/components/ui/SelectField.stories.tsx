import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { SelectField, type SelectOption } from './SelectField';

const options: SelectOption[] = [
  { label: 'Production', value: 'prod' },
  { label: 'Staging', value: 'stage' },
  { label: 'QA sandbox', value: 'qa' },
  { disabled: true, label: 'Archived environment', value: 'archive' }
];

const meta = {
  title: 'Components/SelectField',
  component: SelectField,
  parameters: {
    docs: {
      description: {
        component: 'Form select primitive with label, placeholder, hint, error and disabled states.'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta<typeof SelectField>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: 'Environment',
    onChange: () => undefined,
    options,
    value: 'stage'
  },
  render: function DefaultStory() {
    const [value, setValue] = useState('stage');
    return (
      <SelectField
        hint="Used to scope API mocks and execution plans."
        label="Environment"
        onChange={setValue}
        options={options}
        value={value}
      />
    );
  }
};

export const RequiredWithError: Story = {
  args: {
    label: 'Environment',
    onChange: () => undefined,
    options,
    required: true,
    value: ''
  },
  render: function RequiredWithErrorStory() {
    const [value, setValue] = useState('');
    return (
      <SelectField
        error="Environment is required before scheduling a run."
        label="Environment"
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
    hint: 'Locked while the current approval is pending.',
    label: 'Environment',
    onChange: () => undefined,
    options,
    value: 'prod'
  }
};
