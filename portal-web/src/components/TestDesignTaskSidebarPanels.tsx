import {
  Repeat2,
  RotateCcw,
  Search,
  Sparkles,
  XCircle
} from 'lucide-react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import {
  TEST_DESIGN_COVERAGE_TYPES,
  type TestDesignHealth,
  type TestDesignTaskView,
  type TestDesignTemplateView
} from '../api/testDesign';
import type { TestDesignTaskDiagnosticItem } from '../testDesignTaskDiagnostics';
import {
  CANCELLABLE_TASK_STATUSES,
  RETRYABLE_TASK_STATUSES,
  type GenerationDraft,
  type TaskFilters
} from '../testDesignWorkbenchState';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import { GenerationSourceBadge } from './TestDesignWorkbenchShared';
import { taskGenerationSource } from '../testDesignGenerationSource';
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { NativeSelect } from './ui';

export function TestDesignGenerationConfigPanel(props: {
  canGenerate: boolean;
  mutationState: WorkState;
  templateState: WorkState;
  health: TestDesignHealth | null;
  templates: TestDesignTemplateView[];
  selectedRequirementCount: number;
  generationDraft: GenerationDraft;
  selectedGenerationTemplate: TestDesignTemplateView | null;
  explicitContextAssetLimit: number;
  onCreateTask: (event: FormEvent<HTMLFormElement>) => void;
  onSelectGenerationTemplate: (templateId: string) => void;
  onGenerationDraftChange: Dispatch<SetStateAction<GenerationDraft>>;
  onToggleCoverage: (coverageType: string) => void;
}) {
  const draft = props.generationDraft;

  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1600')}</h2>
          <p className="panel-desc">{translate('auto.k1601')}{props.selectedRequirementCount} {translate('auto.k1602')}</p>
        </div>
      </div>
      <div className="panel-body compact">
        <form className="main-stack" onSubmit={props.onCreateTask}>
          <label className="field">
            <span className="field-label">{translate('auto.k1603')}</span>
            <NativeSelect value={draft.templateId} onChange={(event) => props.onSelectGenerationTemplate(event.target.value)} disabled={!props.canGenerate || props.mutationState.loading || props.templateState.loading}>
              <option value="">{translate('auto.k1604')}</option>
              {props.templates.filter((template) => template.enabled).map((template) => (
                <option key={template.id} value={template.id}>
                  {template.projectId ? translate('auto.k0176') : translate('auto.k1605')} · {template.name}
                </option>
              ))}
            </NativeSelect>
            <span className="field-hint">
              {props.selectedGenerationTemplate
                ? `${props.selectedGenerationTemplate.promptKey}@${props.selectedGenerationTemplate.promptVersion} · ${props.selectedGenerationTemplate.generationStrategy}/${props.selectedGenerationTemplate.coverageStrategy}`
                : translate('auto.k1606')}
            </span>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1389')}</span>
            <input value={draft.projectId} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1607')}</span>
            <input value={draft.title} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, title: event.target.value }))} placeholder={translate('auto.k1608')} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <div className="test-design-template-inline-grid">
            <label className="field">
              <span className="field-label">Prompt Key</span>
              <input value={draft.promptKey} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, promptKey: event.target.value }))} placeholder={props.health?.promptKey ?? translate('auto.k1609')} disabled={!props.canGenerate || props.mutationState.loading} />
            </label>
            <label className="field">
              <span className="field-label">Prompt Version</span>
              <input value={draft.promptVersion} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, promptVersion: event.target.value }))} placeholder={props.health?.promptVersion ?? translate('auto.k1609')} disabled={!props.canGenerate || props.mutationState.loading} />
            </label>
          </div>
          <label className="field">
            <span className="field-label">{translate('auto.k1610')}</span>
            <input value={draft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1611')}</span>
            <input value={draft.environmentKey} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, environmentKey: event.target.value }))} placeholder="qa / staging" disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1612')}</span>
            <input value={draft.contextApiIds} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, contextApiIds: event.target.value }))} placeholder={translate('auto.k1613', { value0: props.explicitContextAssetLimit })} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1614')}</span>
            <input value={draft.contextPageIds} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, contextPageIds: event.target.value }))} placeholder={translate('auto.k1613', { value0: props.explicitContextAssetLimit })} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1615')}</span>
            <input value={draft.contextFlowIds} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, contextFlowIds: event.target.value }))} placeholder={translate('auto.k1613', { value0: props.explicitContextAssetLimit })} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <div className="field">
            <span className="field-label">{translate('auto.k1315')}</span>
            <div className="test-design-checks">
              {TEST_DESIGN_COVERAGE_TYPES.map((type) => (
                <label key={type}>
                  <input type="checkbox" checked={draft.coverageTypes.includes(type)} onChange={() => props.onToggleCoverage(type)} disabled={!props.canGenerate || props.mutationState.loading} />
                  <span>{dictionaryLabel(type)}</span>
                </label>
              ))}
            </div>
          </div>
          <button className="btn btn-primary" type="submit" disabled={!props.canGenerate || props.mutationState.loading || !props.selectedRequirementCount}>
            <Sparkles size={16} />
            {translate('auto.k1616')}</button>
          <StateLine state={props.mutationState} />
        </form>
      </div>
    </section>
  );
}

export function TestDesignTaskListPanel(props: {
  disabled: boolean;
  canGenerate: boolean;
  loadState: WorkState;
  taskState: WorkState;
  tasks: TestDesignTaskView[];
  selectedTaskId: string;
  taskFilters: TaskFilters;
  initialTaskFilters: TaskFilters;
  onTaskFiltersChange: Dispatch<SetStateAction<TaskFilters>>;
  onSelectTask: (taskId: string) => void;
  onRetryTask: (task: TestDesignTaskView) => void;
  onReplayQueuedTaskEvent: (task: TestDesignTaskView) => void;
  onCancelTask: (task: TestDesignTaskView) => void;
}) {
  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1617')}</h2>
          <p className="panel-desc">{translate('auto.k1618')}{props.tasks.length} {translate('auto.k1619')}</p>
        </div>
      </div>
      <div className="panel-body compact">
        <div className="asset-filter-bar test-design-side-filter">
          <label className="field">
            <span className="field-label">{translate('auto.k0176')}</span>
            <input value={props.taskFilters.projectId} onChange={(event) => props.onTaskFiltersChange((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={props.disabled || props.loadState.loading} />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k0182')}</span>
            <NativeSelect value={props.taskFilters.status} onChange={(event) => props.onTaskFiltersChange((current) => ({ ...current, status: event.target.value }))} disabled={props.disabled || props.loadState.loading}>
              <option value="">{translate('auto.k0195')}</option>
              <option value="DRAFT">{dictionaryLabel('DRAFT')}</option>
              <option value="QUEUED">{dictionaryLabel('QUEUED')}</option>
              <option value="RUNNING">{dictionaryLabel('RUNNING')}</option>
              <option value="SUCCEEDED">{dictionaryLabel('SUCCEEDED')}</option>
              <option value="PARTIAL_SUCCESS">{dictionaryLabel('PARTIAL_SUCCESS')}</option>
              <option value="FAILED">{dictionaryLabel('FAILED')}</option>
              <option value="CANCELLED">{dictionaryLabel('CANCELLED')}</option>
              <option value="PUBLISH_QUEUED">{dictionaryLabel('PUBLISH_QUEUED')}</option>
              <option value="PUBLISHING">{dictionaryLabel('PUBLISHING')}</option>
              <option value="PUBLISHED">{dictionaryLabel('PUBLISHED')}</option>
            </NativeSelect>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1316')}</span>
            <input value={props.taskFilters.keyword} onChange={(event) => props.onTaskFiltersChange((current) => ({ ...current, keyword: event.target.value }))} placeholder={translate('auto.k1607')} disabled={props.disabled || props.loadState.loading} />
          </label>
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.disabled} onClick={() => props.onTaskFiltersChange(props.initialTaskFilters)}>
            <Search size={15} />
            {translate('auto.k0254')}</button>
        </div>
        <div className="quick-actions">
          {props.tasks.length ? props.tasks.map((task) => (
            <div className={task.id === props.selectedTaskId ? 'quick-action-row active' : 'quick-action-row'} key={task.id}>
              <button type="button" className="quick-action-main" onClick={() => props.onSelectTask(task.id)}>
                <span>
                  <strong>{task.title}</strong>
                  <em>{dictionaryLabel(task.status)} · {task.generatedCount} / {task.confirmedCount}</em>
                  <GenerationSourceBadge source={taskGenerationSource(task)} compact />
                </span>
              </button>
              <div className="quick-action-controls">
                {RETRYABLE_TASK_STATUSES.has(task.status) && (
                  <button
                    aria-label={translate('auto.k1620', { value0: task.title })}
                    className="btn btn-secondary btn-xs"
                    disabled={!props.canGenerate || props.taskState.loading}
                    title={translate('auto.k1621')}
                    type="button"
                    onClick={() => props.onRetryTask(task)}
                  >
                    <RotateCcw size={14} />
                  </button>
                )}
                {task.status === 'QUEUED' && (
                  <button
                    aria-label={translate('auto.k1622', { value0: task.title })}
                    className="btn btn-secondary btn-xs"
                    disabled={!props.canGenerate || props.taskState.loading}
                    title={translate('auto.k1623')}
                    type="button"
                    onClick={() => props.onReplayQueuedTaskEvent(task)}
                  >
                    <Repeat2 size={14} />
                  </button>
                )}
                {CANCELLABLE_TASK_STATUSES.has(task.status) && (
                  <button
                    aria-label={translate('auto.k1624', { value0: task.title })}
                    className="btn btn-secondary btn-xs"
                    disabled={!props.canGenerate || props.taskState.loading}
                    title={translate('auto.k1034')}
                    type="button"
                    onClick={() => props.onCancelTask(task)}
                  >
                    <XCircle size={14} />
                  </button>
                )}
              </div>
            </div>
          )) : (
            <div className="notice info">{translate('auto.k1625')}</div>
          )}
        </div>
      </div>
    </section>
  );
}

export function TestDesignTaskDiagnosticsPanel(props: {
  selectedTask: TestDesignTaskView | null;
  taskDiagnostics: TestDesignTaskDiagnosticItem[];
}) {
  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1626')}</h2>
          <p className="panel-desc">{props.selectedTask ? translate('auto.k1627', { value0: props.selectedTask.status }) : translate('auto.k1628')}</p>
        </div>
      </div>
      <div className="panel-body compact">
        {props.selectedTask ? (
          <div className="test-design-task-diagnostics">
            {props.taskDiagnostics.map((item) => (
              <div className={`test-design-task-diagnostic${item.tone ? ` ${item.tone}` : ''}`} key={item.label}>
                <span>{item.label}</span>
                <em>{item.value}</em>
              </div>
            ))}
          </div>
        ) : (
          <div className="notice info">{translate('auto.k1538')}</div>
        )}
      </div>
    </section>
  );
}
