import {
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  GripVertical,
  Plus,
  Save,
  Search,
  Trash2,
  XCircle
} from 'lucide-react';
import type { Dispatch, SetStateAction } from 'react';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  type TestDesignCandidateBatchActionResult,
  type TestDesignCandidateBatchActionType,
  type TestDesignCandidateView,
  type TestDesignTaskView
} from '../api/testDesign';
import type {
  TestDesignBatchEditDraft,
  TestDesignBatchEditIssue
} from '../testDesignBatchEdit';
import {
  candidateGenerationSource,
  generationSourceText,
  type TestDesignGenerationSource
} from '../testDesignGenerationSource';
import {
  TEST_DESIGN_CANDIDATE_PAGE_SIZES,
  type PaginatedItems
} from '../testDesignPagination';
import type { TestDesignCandidateDraftQualityIssue } from '../testDesignQuality';
import { canSelectTestDesignCandidate } from '../testDesignSelection';
import {
  initialCandidateFilters,
  type CandidateDraft,
  type CandidateFilters,
  type TestDesignStepDraft
} from '../testDesignWorkbenchState';
import { applyStepRichTextMarkup, type StepRichTextStyle } from '../stepRichText';
import { StepRichTextField } from './StepRichTextField';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import {
  BatchActionSummary,
  BatchEditSummary,
  CandidateStatus,
  GenerationSourceBadge,
  QualityFieldMessages
} from './TestDesignWorkbenchShared';

export function TestDesignCandidateReviewPanel(props: {
  canExport: boolean;
  canReview: boolean;
  selectedTask: TestDesignTaskView | null;
  selectedTaskId: string;
  taskState: WorkState;
  mutationState: WorkState;
  candidateFilters: CandidateFilters;
  candidatePage: PaginatedItems<TestDesignCandidateView>;
  candidatePageSize: number;
  selectedCandidates: TestDesignCandidateView[];
  selectedCandidateIds: string[];
  selectedCandidateId: string;
  selectedCandidate: TestDesignCandidateView | null;
  candidateDraft: CandidateDraft | null;
  selectedCandidateSource: TestDesignGenerationSource;
  candidateQualityIssues: TestDesignCandidateDraftQualityIssue[];
  candidateSaveBlocked: boolean;
  reviewComment: string;
  currentPageSelectableCount: number;
  selectedReviewCandidates: TestDesignCandidateView[];
  selectedBatchEditableCandidates: TestDesignCandidateView[];
  batchActionResult: TestDesignCandidateBatchActionResult | null;
  batchEditResult: BatchEditSummaryResult | null;
  batchEditDraft: TestDesignBatchEditDraft;
  batchEditIssues: TestDesignBatchEditIssue[];
  batchEditBlocked: boolean;
  batchEditFieldLabels: string[];
  draggingStepId: string;
  onCandidateFiltersChange: Dispatch<SetStateAction<CandidateFilters>>;
  onCandidatePageIndexChange: Dispatch<SetStateAction<number>>;
  onCandidatePageSizeChange: Dispatch<SetStateAction<number>>;
  onSelectedCandidateIdsChange: Dispatch<SetStateAction<string[]>>;
  onSelectedCandidateIdChange: Dispatch<SetStateAction<string>>;
  onCandidateDraftChange: Dispatch<SetStateAction<CandidateDraft | null>>;
  onBatchEditDraftChange: Dispatch<SetStateAction<TestDesignBatchEditDraft>>;
  onReviewCommentChange: Dispatch<SetStateAction<string>>;
  onDraggingStepIdChange: Dispatch<SetStateAction<string>>;
  onSelectCurrentPageCandidates: () => void;
  onToggleCandidateSelection: (candidateId: string) => void;
  onExportCandidateReview: (scope: 'page' | 'selected') => void;
  onExportTaskReport: () => void;
  onBatchReviewCandidates: (action: TestDesignCandidateBatchActionType) => void;
  onBatchEditCandidates: () => void;
  onSaveCandidate: () => void;
  onReviewCandidate: (action: 'confirm' | 'reject' | 'ignore') => void;
  onInsertPresetSteps: () => void;
  onAddStepDraft: () => void;
  onDeleteSelectedSteps: () => void;
  onUpdateStepDraft: (stepId: string, patch: Partial<TestDesignStepDraft>) => void;
  onDropStepDraft: (stepId: string) => void;
  onMoveStepDraft: (stepId: string, direction: -1 | 1) => void;
  onInsertStepDraftAfter: (stepId: string) => void;
  onRemoveStepDraft: (stepId: string) => void;
}) {
  const candidateDraft = props.candidateDraft;

  function applyStepMarkup(step: TestDesignStepDraft, field: 'action' | 'expectedResult', style: StepRichTextStyle) {
    const target = document.getElementById(`test-design-step-${field}-${step.id}`) as HTMLTextAreaElement | null;
    const value = step[field] ?? '';
    const edit = applyStepRichTextMarkup(value, style, target?.selectionStart ?? value.length, target?.selectionEnd ?? value.length);
    props.onUpdateStepDraft(step.id, { [field]: edit.value });
    window.requestAnimationFrame(() => {
      target?.focus();
      target?.setSelectionRange(edit.selectionStart, edit.selectionEnd);
    });
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2 className="panel-title">候选评审</h2>
          <p className="panel-desc">编辑候选用例并确认，发布后会写入 WP3 测试用例和需求追踪关系。</p>
        </div>
        <StateLine state={props.taskState} />
      </div>
      <div className="panel-body">
        <div className="asset-filter-bar test-design-candidate-filter">
          <label className="field">
            <span className="field-label">候选状态</span>
            <select value={props.candidateFilters.status} onChange={(event) => props.onCandidateFiltersChange((current) => ({ ...current, status: event.target.value }))} disabled={props.taskState.loading || !props.selectedTaskId}>
              <option value="">全部</option>
              {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field-label">覆盖类型</span>
            <select value={props.candidateFilters.coverageType} onChange={(event) => props.onCandidateFiltersChange((current) => ({ ...current, coverageType: event.target.value }))} disabled={props.taskState.loading || !props.selectedTaskId}>
              <option value="">全部</option>
              {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field-label">关键词</span>
            <input value={props.candidateFilters.keyword} onChange={(event) => props.onCandidateFiltersChange((current) => ({ ...current, keyword: event.target.value }))} placeholder="标题 / 标签 / 错误" disabled={props.taskState.loading || !props.selectedTaskId} />
          </label>
          <div className="filter-actions">
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.selectedTaskId} onClick={() => props.onCandidateFiltersChange(initialCandidateFilters)}>
              <Search size={15} />
              重置
            </button>
            <button className="btn btn-ghost btn-sm" type="button" disabled={!props.currentPageSelectableCount} onClick={props.onSelectCurrentPageCandidates}>
              选中本页
            </button>
            <button className="btn btn-ghost btn-sm" type="button" disabled={!props.selectedCandidateIds.length} onClick={() => props.onSelectedCandidateIdsChange([])}>
              清空选择
            </button>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || !props.selectedTaskId || !props.candidatePage.total} onClick={() => props.onExportCandidateReview('page')}>
              <Download size={15} />
              导出筛选
            </button>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || !props.selectedCandidates.length} onClick={() => props.onExportCandidateReview('selected')}>
              <Download size={15} />
              导出已选
            </button>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || !props.selectedTask || props.taskState.loading} onClick={props.onExportTaskReport}>
              <Download size={15} />
              导出报告
            </button>
          </div>
        </div>
        {props.candidatePage.total > 0 && (
          <div className="test-design-pagination" aria-label="候选分页">
            <span>
              {props.candidatePage.start}-{props.candidatePage.end} / {props.candidatePage.total}
              {props.selectedCandidates.length ? ` · 已选 ${props.selectedCandidates.length}` : ''}
            </span>
            <label>
              <span>每页</span>
              <select value={props.candidatePageSize} onChange={(event) => props.onCandidatePageSizeChange(Number(event.target.value))} disabled={props.taskState.loading}>
                {TEST_DESIGN_CANDIDATE_PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
              </select>
            </label>
            <div className="toolbar-actions">
              <button
                aria-label="上一页候选"
                className="btn btn-secondary btn-xs"
                disabled={!props.candidatePage.hasPrevious}
                title="上一页"
                type="button"
                onClick={() => props.onCandidatePageIndexChange((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft size={14} />
              </button>
              <span className="field-hint">{props.candidatePage.index + 1} / {props.candidatePage.totalPages}</span>
              <button
                aria-label="下一页候选"
                className="btn btn-secondary btn-xs"
                disabled={!props.candidatePage.hasNext}
                title="下一页"
                type="button"
                onClick={() => props.onCandidatePageIndexChange((current) => current + 1)}
              >
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        )}
        {props.batchActionResult && <BatchActionSummary result={props.batchActionResult} />}
        {props.batchEditResult && <BatchEditSummary result={props.batchEditResult} />}
        {props.selectedReviewCandidates.length > 0 && (
          <div className="test-design-batch-toolbar">
            <span>批量评审 {props.selectedReviewCandidates.length} 个候选</span>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onBatchReviewCandidates('CONFIRM')}>
                批量确认
              </button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || !props.reviewComment.trim()} onClick={() => props.onBatchReviewCandidates('REJECT')}>
                批量驳回
              </button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || !props.reviewComment.trim()} onClick={() => props.onBatchReviewCandidates('IGNORE')}>
                批量忽略
              </button>
            </div>
          </div>
        )}
        {props.selectedCandidateIds.length > 0 && (
          <div className="test-design-batch-editor">
            <div className="test-design-batch-editor-heading">
              <span>批量字段编辑 {props.selectedBatchEditableCandidates.length} / {props.selectedCandidateIds.length} 个可编辑候选</span>
              <button className="btn btn-ghost btn-xs" type="button" disabled={props.mutationState.loading} onClick={() => props.onBatchEditDraftChange({
                coverageType: '',
                priority: '',
                tags: '',
                tagMode: 'append'
              })}>
                重置
              </button>
            </div>
            <div className="test-design-batch-editor-grid">
              <label className="field">
                <span className="field-label">覆盖类型</span>
                <select value={props.batchEditDraft.coverageType} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, coverageType: event.target.value }))} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="">不修改</option>
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="field-label">优先级</span>
                <select value={props.batchEditDraft.priority} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, priority: event.target.value }))} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="">不修改</option>
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">标签策略</span>
                <select value={props.batchEditDraft.tagMode} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, tagMode: event.target.value === 'replace' ? 'replace' : 'append' }))} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="append">追加标签</option>
                  <option value="replace">替换标签</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">标签</span>
                <input value={props.batchEditDraft.tags} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, tags: event.target.value }))} placeholder="regression, wp5" disabled={!props.canReview || props.mutationState.loading} />
              </label>
            </div>
            {props.batchEditIssues.length > 0 && (
              <div className="field-error-list">
                {props.batchEditIssues.map((issue, index) => <span key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</span>)}
              </div>
            )}
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || props.batchEditBlocked} onClick={props.onBatchEditCandidates}>
                <Save size={15} />
                批量应用字段
              </button>
              {props.batchEditFieldLabels.length > 0 && <span className="field-hint">{props.batchEditFieldLabels.join('；')}</span>}
            </div>
          </div>
        )}
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th className="table-check-column"></th>
                <th>标题</th>
                <th>覆盖</th>
                <th>优先级</th>
                <th>状态</th>
                <th>来源</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {props.candidatePage.items.length ? (
                props.candidatePage.items.map((candidate) => (
                  <tr className={candidate.id === props.selectedCandidateId ? 'selected-row' : ''} key={candidate.id}>
                    <td>
                      <input
                        aria-label={`选择候选 ${candidate.title}`}
                        type="checkbox"
                        checked={props.selectedCandidateIds.includes(candidate.id)}
                        onChange={() => props.onToggleCandidateSelection(candidate.id)}
                        disabled={!canSelectTestDesignCandidate(candidate)}
                      />
                    </td>
                    <td>
                      <strong>{candidate.title}</strong>
                      <div className="field-hint">{candidate.errorMessage ?? candidate.requirementId ?? '-'}</div>
                    </td>
                    <td>{candidate.coverageType}</td>
                    <td>{candidate.priority}</td>
                    <td><CandidateStatus value={candidate.status} /></td>
                    <td><GenerationSourceBadge source={candidateGenerationSource(candidate, props.selectedTask)} compact /></td>
                    <td>
                      <button className="btn btn-secondary btn-xs" type="button" onClick={() => props.onSelectedCandidateIdChange(candidate.id)}>
                        <Eye size={14} />
                        查看
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="table-empty" colSpan={7}>{props.selectedTaskId ? '暂无匹配候选用例' : '请先生成或选择任务'}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {candidateDraft && props.selectedCandidate && (
          <div className="test-design-editor">
            <div className="test-design-source-summary">
              <span>候选来源</span>
              <GenerationSourceBadge source={props.selectedCandidateSource} />
              <em>{generationSourceText(props.selectedCandidateSource)}</em>
            </div>
            {props.candidateQualityIssues.length > 0 && (
              <div className="notice warning test-design-quality-summary">
                <strong>质量提示</strong>
                <span>保存前需处理 {props.candidateQualityIssues.length} 项候选质量问题。</span>
                <ul className="test-design-quality-list">
                  {props.candidateQualityIssues.slice(0, 6).map((issue, index) => (
                    <li key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</li>
                  ))}
                </ul>
              </div>
            )}
            <div className="asset-form-grid">
              <label className="field">
                <span className="field-label">标题</span>
                <input value={candidateDraft.title} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, title: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
                <QualityFieldMessages field="title" issues={props.candidateQualityIssues} />
              </label>
              <label className="field">
                <span className="field-label">覆盖类型</span>
                <select value={candidateDraft.coverageType} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, coverageType: event.target.value })} disabled={!props.canReview || props.mutationState.loading}>
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
                <QualityFieldMessages field="coverageType" issues={props.candidateQualityIssues} />
              </label>
              <label className="field">
                <span className="field-label">优先级</span>
                <select value={candidateDraft.priority} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, priority: event.target.value })} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>
                <QualityFieldMessages field="priority" issues={props.candidateQualityIssues} />
              </label>
            </div>
            <div className="asset-form-grid">
              <label className="field">
                <span className="field-label">API ID</span>
                <input value={candidateDraft.apiId} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, apiId: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">前置条件</span>
                <input value={candidateDraft.preconditions} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, preconditions: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
                <QualityFieldMessages field="preconditions" issues={props.candidateQualityIssues} />
              </label>
              <label className="field">
                <span className="field-label">标签</span>
                <input value={candidateDraft.tags} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, tags: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
                <QualityFieldMessages field="tags" issues={props.candidateQualityIssues} />
              </label>
            </div>
            <label className="field">
              <span className="field-label">描述</span>
              <textarea value={candidateDraft.description} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, description: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
              <QualityFieldMessages field="description" issues={props.candidateQualityIssues} />
            </label>
            <div className="field test-design-steps-editor">
              <div className="test-design-steps-heading">
                <span className="field-label">步骤</span>
                <div className="toolbar-actions">
                  <button className="btn btn-secondary btn-xs" type="button" title="批量插入" disabled={!props.canReview || props.mutationState.loading} onClick={props.onInsertPresetSteps}>
                    <Plus size={14} />
                    批量
                  </button>
                  <button className="btn btn-secondary btn-icon btn-xs" type="button" title="添加步骤" disabled={!props.canReview || props.mutationState.loading} onClick={props.onAddStepDraft}>
                    <Plus size={14} />
                  </button>
                  <button className="btn btn-ghost btn-icon btn-xs" type="button" title="删除已选步骤" disabled={!props.canReview || props.mutationState.loading || !candidateDraft.steps.some((step) => step.selected)} onClick={props.onDeleteSelectedSteps}>
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
              <div className="test-design-step-list">
                {candidateDraft.steps.map((step, index) => (
                  <div
                    key={step.id}
                    className={props.draggingStepId === step.id ? 'test-design-step-row dragging' : 'test-design-step-row'}
                    draggable={props.canReview && !props.mutationState.loading}
                    onDragStart={() => props.onDraggingStepIdChange(step.id)}
                    onDragEnd={() => props.onDraggingStepIdChange('')}
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={() => props.onDropStepDraft(step.id)}
                  >
                    <label className="test-design-step-select" title="选择步骤">
                      <input type="checkbox" checked={step.selected} onChange={(event) => props.onUpdateStepDraft(step.id, { selected: event.target.checked })} disabled={!props.canReview || props.mutationState.loading} />
                    </label>
                    <button className="btn btn-ghost btn-icon btn-xs test-design-step-drag" type="button" title="拖拽排序" disabled={!props.canReview || props.mutationState.loading}>
                      <GripVertical size={14} />
                    </button>
                    <span className="asset-step-index">{index + 1}</span>
                    <StepRichTextField
                      disabled={!props.canReview || props.mutationState.loading}
                      id={`test-design-step-action-${step.id}`}
                      label="操作"
                      onChange={(value) => props.onUpdateStepDraft(step.id, { action: value })}
                      onFormat={(style) => applyStepMarkup(step, 'action', style)}
                      value={step.action}
                    />
                    <StepRichTextField
                      disabled={!props.canReview || props.mutationState.loading}
                      id={`test-design-step-expectedResult-${step.id}`}
                      label="预期"
                      onChange={(value) => props.onUpdateStepDraft(step.id, { expectedResult: value })}
                      onFormat={(style) => applyStepMarkup(step, 'expectedResult', style)}
                      value={step.expectedResult}
                    />
                    <div className="test-design-step-actions">
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title="上移" disabled={!props.canReview || props.mutationState.loading || index === 0} onClick={() => props.onMoveStepDraft(step.id, -1)}>
                        <ArrowUp size={14} />
                      </button>
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title="下移" disabled={!props.canReview || props.mutationState.loading || index === candidateDraft.steps.length - 1} onClick={() => props.onMoveStepDraft(step.id, 1)}>
                        <ArrowDown size={14} />
                      </button>
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title="插入下一步" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onInsertStepDraftAfter(step.id)}>
                        <Plus size={14} />
                      </button>
                      <button className="btn btn-ghost btn-icon btn-xs" type="button" title="删除步骤" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onRemoveStepDraft(step.id)}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
              <span className="field-hint">{candidateDraft.steps.length} 个步骤，已选 {candidateDraft.steps.filter((step) => step.selected).length} 个。</span>
              <QualityFieldMessages field="steps" issues={props.candidateQualityIssues} />
            </div>
            <label className="field">
              <span className="field-label">预期结果</span>
              <textarea value={candidateDraft.expectedResult} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, expectedResult: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
              <QualityFieldMessages field="expectedResult" issues={props.candidateQualityIssues} />
            </label>
            <label className="field">
              <span className="field-label">评审意见</span>
              <input value={props.reviewComment} onChange={(event) => props.onReviewCommentChange(event.target.value)} disabled={!props.canReview || props.mutationState.loading} />
            </label>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || !candidateDraft.title.trim() || props.candidateSaveBlocked} onClick={props.onSaveCandidate}>
                <Save size={15} />
                保存
              </button>
              <button className="btn btn-primary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onReviewCandidate('confirm')}>
                <CheckCircle2 size={15} />
                确认
              </button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onReviewCandidate('reject')}>
                <XCircle size={15} />
                驳回
              </button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onReviewCandidate('ignore')}>
                忽略
              </button>
            </div>
            <StateLine state={props.mutationState} />
          </div>
        )}
      </div>
    </section>
  );
}

type BatchEditSummaryResult = {
  total: number;
  succeededCount: number;
  failedCount: number;
  items: Array<{
    candidateId: string;
    result: 'SUCCEEDED' | 'FAILED';
    errorMessage?: string;
  }>;
};
