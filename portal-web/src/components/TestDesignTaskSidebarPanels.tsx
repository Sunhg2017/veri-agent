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
          <h2 className="panel-title">生成配置</h2>
          <p className="panel-desc">当前选择 {props.selectedRequirementCount} 个需求。</p>
        </div>
      </div>
      <div className="panel-body compact">
        <form className="main-stack" onSubmit={props.onCreateTask}>
          <label className="field">
            <span className="field-label">生成模板</span>
            <select value={draft.templateId} onChange={(event) => props.onSelectGenerationTemplate(event.target.value)} disabled={!props.canGenerate || props.mutationState.loading || props.templateState.loading}>
              <option value="">手动配置</option>
              {props.templates.filter((template) => template.enabled).map((template) => (
                <option key={template.id} value={template.id}>
                  {template.projectId ? '项目' : '全局'} · {template.name}
                </option>
              ))}
            </select>
            <span className="field-hint">
              {props.selectedGenerationTemplate
                ? `${props.selectedGenerationTemplate.promptKey}@${props.selectedGenerationTemplate.promptVersion} · ${props.selectedGenerationTemplate.generationStrategy}/${props.selectedGenerationTemplate.coverageStrategy}`
                : '不选择模板时使用手动参数或平台默认值。'}
            </span>
          </label>
          <label className="field">
            <span className="field-label">项目 ID</span>
            <input value={draft.projectId} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">任务标题</span>
            <input value={draft.title} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, title: event.target.value }))} placeholder="登录模块用例生成" disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <div className="test-design-template-inline-grid">
            <label className="field">
              <span className="field-label">Prompt Key</span>
              <input value={draft.promptKey} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, promptKey: event.target.value }))} placeholder={props.health?.promptKey ?? '平台默认'} disabled={!props.canGenerate || props.mutationState.loading} />
            </label>
            <label className="field">
              <span className="field-label">Prompt Version</span>
              <input value={draft.promptVersion} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, promptVersion: event.target.value }))} placeholder={props.health?.promptVersion ?? '平台默认'} disabled={!props.canGenerate || props.mutationState.loading} />
            </label>
          </div>
          <label className="field">
            <span className="field-label">每需求用例数</span>
            <input value={draft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">环境 Key</span>
            <input value={draft.environmentKey} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, environmentKey: event.target.value }))} placeholder="qa / staging" disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">上下文 API ID</span>
            <input value={draft.contextApiIds} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, contextApiIds: event.target.value }))} placeholder={`最多 ${props.explicitContextAssetLimit} 个，逗号或换行分隔`} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">上下文页面 ID</span>
            <input value={draft.contextPageIds} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, contextPageIds: event.target.value }))} placeholder={`最多 ${props.explicitContextAssetLimit} 个，逗号或换行分隔`} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <label className="field">
            <span className="field-label">上下文业务流 ID</span>
            <input value={draft.contextFlowIds} onChange={(event) => props.onGenerationDraftChange((current) => ({ ...current, contextFlowIds: event.target.value }))} placeholder={`最多 ${props.explicitContextAssetLimit} 个，逗号或换行分隔`} disabled={!props.canGenerate || props.mutationState.loading} />
          </label>
          <div className="field">
            <span className="field-label">覆盖类型</span>
            <div className="test-design-checks">
              {TEST_DESIGN_COVERAGE_TYPES.map((type) => (
                <label key={type}>
                  <input type="checkbox" checked={draft.coverageTypes.includes(type)} onChange={() => props.onToggleCoverage(type)} disabled={!props.canGenerate || props.mutationState.loading} />
                  <span>{type}</span>
                </label>
              ))}
            </div>
          </div>
          <button className="btn btn-primary" type="submit" disabled={!props.canGenerate || props.mutationState.loading || !props.selectedRequirementCount}>
            <Sparkles size={16} />
            生成候选
          </button>
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
          <h2 className="panel-title">任务</h2>
          <p className="panel-desc">最近 {props.tasks.length} 个生成任务。</p>
        </div>
      </div>
      <div className="panel-body compact">
        <div className="asset-filter-bar test-design-side-filter">
          <label className="field">
            <span className="field-label">项目</span>
            <input value={props.taskFilters.projectId} onChange={(event) => props.onTaskFiltersChange((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={props.disabled || props.loadState.loading} />
          </label>
          <label className="field">
            <span className="field-label">状态</span>
            <select value={props.taskFilters.status} onChange={(event) => props.onTaskFiltersChange((current) => ({ ...current, status: event.target.value }))} disabled={props.disabled || props.loadState.loading}>
              <option value="">全部</option>
              <option value="DRAFT">DRAFT</option>
              <option value="QUEUED">QUEUED</option>
              <option value="RUNNING">RUNNING</option>
              <option value="SUCCEEDED">SUCCEEDED</option>
              <option value="PARTIAL_SUCCESS">PARTIAL_SUCCESS</option>
              <option value="FAILED">FAILED</option>
              <option value="CANCELLED">CANCELLED</option>
              <option value="PUBLISH_QUEUED">PUBLISH_QUEUED</option>
              <option value="PUBLISHING">PUBLISHING</option>
              <option value="PUBLISHED">PUBLISHED</option>
            </select>
          </label>
          <label className="field">
            <span className="field-label">关键词</span>
            <input value={props.taskFilters.keyword} onChange={(event) => props.onTaskFiltersChange((current) => ({ ...current, keyword: event.target.value }))} placeholder="任务标题" disabled={props.disabled || props.loadState.loading} />
          </label>
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.disabled} onClick={() => props.onTaskFiltersChange(props.initialTaskFilters)}>
            <Search size={15} />
            重置
          </button>
        </div>
        <div className="quick-actions">
          {props.tasks.length ? props.tasks.map((task) => (
            <div className={task.id === props.selectedTaskId ? 'quick-action-row active' : 'quick-action-row'} key={task.id}>
              <button type="button" className="quick-action-main" onClick={() => props.onSelectTask(task.id)}>
                <span>
                  <strong>{task.title}</strong>
                  <em>{task.status} · {task.generatedCount} / {task.confirmedCount}</em>
                  <GenerationSourceBadge source={taskGenerationSource(task)} compact />
                </span>
              </button>
              <div className="quick-action-controls">
                {RETRYABLE_TASK_STATUSES.has(task.status) && (
                  <button
                    aria-label={`重试任务 ${task.title}`}
                    className="btn btn-secondary btn-xs"
                    disabled={!props.canGenerate || props.taskState.loading}
                    title="重试任务"
                    type="button"
                    onClick={() => props.onRetryTask(task)}
                  >
                    <RotateCcw size={14} />
                  </button>
                )}
                {task.status === 'QUEUED' && (
                  <button
                    aria-label={`重发排队事件 ${task.title}`}
                    className="btn btn-secondary btn-xs"
                    disabled={!props.canGenerate || props.taskState.loading}
                    title="重发排队事件"
                    type="button"
                    onClick={() => props.onReplayQueuedTaskEvent(task)}
                  >
                    <Repeat2 size={14} />
                  </button>
                )}
                {CANCELLABLE_TASK_STATUSES.has(task.status) && (
                  <button
                    aria-label={`取消任务 ${task.title}`}
                    className="btn btn-secondary btn-xs"
                    disabled={!props.canGenerate || props.taskState.loading}
                    title="取消任务"
                    type="button"
                    onClick={() => props.onCancelTask(task)}
                  >
                    <XCircle size={14} />
                  </button>
                )}
              </div>
            </div>
          )) : (
            <div className="notice info">暂无生成任务</div>
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
          <h2 className="panel-title">任务诊断</h2>
          <p className="panel-desc">{props.selectedTask ? `${props.selectedTask.status} · 诊断摘要已脱敏` : '定位模型调用、幂等回放和失败上下文摘要。'}</p>
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
          <div className="notice info">请先选择任务</div>
        )}
      </div>
    </section>
  );
}
