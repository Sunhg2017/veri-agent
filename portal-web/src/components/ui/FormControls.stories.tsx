import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { dictionaryLabel, fieldLabel } from '../../platform/dictionaries';
import { translate } from '../../platform/i18n';
import { CheckboxControl } from './CheckboxControl';
import { InputControl } from './InputControl';
import { NumberControl } from './NumberControl';
import { SelectControl } from './SelectControl';
import { TextAreaControl } from './TextAreaControl';

function FormControlsDemo() {
  const [status, setStatus] = useState('READY');
  const [projectId, setProjectId] = useState('project-alpha');
  const [timeout, setTimeoutValue] = useState('300');
  const [summary, setSummary] = useState('运行摘要已就绪');
  const [enabled, setEnabled] = useState(true);

  return (
    <form className="document-form" onSubmit={(event) => event.preventDefault()}>
      <div className="form-grid">
        <label className="field" htmlFor="story-project-id">
          <span className="field-label">{fieldLabel('projectId')}</span>
          <InputControl
            id="story-project-id"
            value={projectId}
            onChange={(event) => setProjectId(event.target.value)}
          />
        </label>
        <label className="field" htmlFor="story-status">
          <span className="field-label">{fieldLabel('status')}</span>
          <SelectControl
            id="story-status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="READY">{dictionaryLabel('READY')}</option>
            <option value="RUNNING">{dictionaryLabel('RUNNING')}</option>
            <option value="BLOCKED">{dictionaryLabel('BLOCKED')}</option>
          </SelectControl>
        </label>
        <label className="field" htmlFor="story-timeout">
          <span className="field-label">{fieldLabel('defaultTimeout')}</span>
          <NumberControl
            id="story-timeout"
            min={1}
            max={1800}
            value={timeout}
            onChange={(event) => setTimeoutValue(event.target.value)}
          />
        </label>
        <label className="toggle-field" htmlFor="story-enabled">
          <CheckboxControl
            id="story-enabled"
            checked={enabled}
            onChange={(event) => setEnabled(event.target.checked)}
          />
          <span>{fieldLabel('runnerEnabled')}</span>
        </label>
      </div>
      <label className="field" htmlFor="story-summary">
        <span className="field-label">{fieldLabel('summary')}</span>
        <TextAreaControl
          id="story-summary"
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
        />
      </label>
    </form>
  );
}

const meta = {
  title: 'Components/FormControls',
  component: FormControlsDemo,
  parameters: {
    docs: {
      description: {
        component: '统一输入、下拉、数字、复选和多行文本控件，保持 Ant Design 交互、字典展示和暗黑主题一致。'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta<typeof FormControlsDemo>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => <FormControlsDemo />
};

export const Disabled: Story = {
  render: () => (
    <form className="document-form" onSubmit={(event) => event.preventDefault()}>
      <div className="form-grid">
        <label className="field" htmlFor="story-disabled-project">
          <span className="field-label">{fieldLabel('projectId')}</span>
          <InputControl id="story-disabled-project" disabled value="project-alpha" />
        </label>
        <label className="field" htmlFor="story-disabled-status">
          <span className="field-label">{fieldLabel('status')}</span>
          <SelectControl id="story-disabled-status" disabled value="READY">
            <option value="READY">{dictionaryLabel('READY')}</option>
          </SelectControl>
        </label>
      </div>
      <span className="document-state-line">{translate('auto.k1118')}</span>
    </form>
  )
};
