import { Layers3, Plus, RefreshCw, Trash2 } from 'lucide-react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import {
  TEST_DESIGN_COVERAGE_STRATEGIES,
  TEST_DESIGN_COVERAGE_TYPES,
  TEST_DESIGN_GENERATION_STRATEGIES,
  type TestDesignHealth,
  type TestDesignTemplateView
} from '../api/testDesign';
import {
  initialTemplateDraft,
  type TemplateDraft
} from '../testDesignWorkbenchState';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { NativeSelect } from './ui';

export function TestDesignTemplateManagementPanel(props: {
  canRead: boolean;
  canPolicyManage: boolean;
  state: WorkState;
  health: TestDesignHealth | null;
  templates: TestDesignTemplateView[];
  templatePageTotal: number;
  selectedTemplateManageId: string;
  selectedManagedTemplate: TestDesignTemplateView | null;
  templateDraft: TemplateDraft;
  templateProjectId: string;
  onRefresh: () => void;
  onSave: (event: FormEvent<HTMLFormElement>) => void;
  onSelectedTemplateManageIdChange: Dispatch<SetStateAction<string>>;
  onTemplateDraftChange: Dispatch<SetStateAction<TemplateDraft>>;
  onToggleCoverage: (coverageType: string) => void;
  onDisableTemplate: () => void;
}) {
  const draft = props.templateDraft;

  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1629')}</h2>
          <p className="panel-desc">{props.templatePageTotal ? translate('auto.k1630', { value0: props.templatePageTotal }) : translate('auto.k1631')}</p>
        </div>
        <button className="btn btn-secondary btn-xs" type="button" disabled={!props.canRead || props.state.loading} title={translate('auto.k1632')} onClick={props.onRefresh}>
          <RefreshCw size={14} />
        </button>
      </div>
      <div className="panel-body compact">
        <form className="main-stack" onSubmit={props.onSave}>
          <div className="test-design-template-toolbar">
            <label className="field">
              <span className="field-label">{translate('auto.k1633')}</span>
              <NativeSelect value={props.selectedTemplateManageId} onChange={(event) => props.onSelectedTemplateManageIdChange(event.target.value)} disabled={props.state.loading}>
                <option value="">{translate('auto.k1634')}</option>
                {props.templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.enabled ? '' : translate('auto.k1635')}{template.projectId ? translate('auto.k0176') : translate('auto.k1605')} · {template.name}
                  </option>
                ))}
              </NativeSelect>
            </label>
            <button
              className="btn btn-secondary btn-icon btn-sm"
              type="button"
              title={translate('auto.k1634')}
              disabled={props.state.loading}
              onClick={() => {
                props.onSelectedTemplateManageIdChange('');
                props.onTemplateDraftChange({ ...initialTemplateDraft, projectId: props.templateProjectId });
              }}
            >
              <Plus size={15} />
            </button>
          </div>
          <label className="field">
            <span className="field-label">{translate('auto.k0177')}</span>
            <input value={draft.name} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, name: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1636')}</span>
            <input value={draft.projectId} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, projectId: event.target.value }))} placeholder={translate('auto.k1637')} disabled={!props.canPolicyManage || props.state.loading || Boolean(props.selectedManagedTemplate)} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k0264')}</span>
            <input value={draft.description} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, description: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
          </label>
          <div className="test-design-template-inline-grid">
            <label className="field">
              <span className="field-label">Prompt Key</span>
              <input value={draft.promptKey} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, promptKey: event.target.value }))} placeholder={props.health?.promptKey ?? translate('auto.k1638')} disabled={!props.canPolicyManage || props.state.loading} />
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k0178')}</span>
              <input value={draft.promptVersion} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, promptVersion: event.target.value }))} placeholder={props.health?.promptVersion ?? translate('auto.k1638')} disabled={!props.canPolicyManage || props.state.loading} />
            </label>
          </div>
          <div className="test-design-template-inline-grid">
            <label className="field">
              <span className="field-label">{translate('auto.k1639')}</span>
              <input value={draft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k1611')}</span>
              <input value={draft.environmentKey} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, environmentKey: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
            </label>
          </div>
          <div className="test-design-template-inline-grid">
            <label className="field">
              <span className="field-label">{translate('auto.k1640')}</span>
              <NativeSelect value={draft.generationStrategy} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, generationStrategy: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading}>
                {TEST_DESIGN_GENERATION_STRATEGIES.map((strategy) => (
                  <option key={strategy} value={strategy}>{dictionaryLabel(strategy)}</option>
                ))}
              </NativeSelect>
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k1641')}</span>
              <NativeSelect value={draft.coverageStrategy} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, coverageStrategy: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading}>
                {TEST_DESIGN_COVERAGE_STRATEGIES.map((strategy) => (
                  <option key={strategy} value={strategy}>{dictionaryLabel(strategy)}</option>
                ))}
              </NativeSelect>
            </label>
          </div>
          <label className="field">
            <span className="field-label">{translate('auto.k1612')}</span>
            <input value={draft.contextApiIds} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, contextApiIds: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1614')}</span>
            <input value={draft.contextPageIds} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, contextPageIds: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1615')}</span>
            <input value={draft.contextFlowIds} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, contextFlowIds: event.target.value }))} disabled={!props.canPolicyManage || props.state.loading} />
          </label>
          <div className="field">
            <span className="field-label">{translate('auto.k1315')}</span>
            <div className="test-design-checks">
              {TEST_DESIGN_COVERAGE_TYPES.map((type) => (
                <label key={type}>
                  <input type="checkbox" checked={draft.coverageTypes.includes(type)} onChange={() => props.onToggleCoverage(type)} disabled={!props.canPolicyManage || props.state.loading} />
                  <span>{dictionaryLabel(type)}</span>
                </label>
              ))}
            </div>
          </div>
          <label className="test-design-template-enabled">
            <input type="checkbox" checked={draft.enabled} onChange={(event) => props.onTemplateDraftChange((current) => ({ ...current, enabled: event.target.checked }))} disabled={!props.canPolicyManage || props.state.loading} />
            <span>{translate('auto.k0251')}</span>
          </label>
          <div className="toolbar-actions">
            <button className="btn btn-secondary btn-sm" type="submit" disabled={!props.canPolicyManage || props.state.loading || !draft.name.trim()}>
              <Layers3 size={15} />
              {props.selectedManagedTemplate ? translate('auto.k1008') : translate('auto.k0862')}
            </button>
            <button className="btn btn-ghost btn-sm" type="button" disabled={!props.canPolicyManage || props.state.loading || !props.selectedManagedTemplate || !props.selectedManagedTemplate.enabled} onClick={props.onDisableTemplate}>
              <Trash2 size={15} />
              {translate('auto.k1272')}</button>
          </div>
          <StateLine state={props.state} />
        </form>
      </div>
    </section>
  );
}
