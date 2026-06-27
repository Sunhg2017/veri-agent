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
import { translate } from '../platform/i18n';

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
          <h2 className="panel-title">{translate('auto.k1312')}</h2>
          <p className="panel-desc">{translate('auto.k1313')}</p>
        </div>
        <StateLine state={props.taskState} />
      </div>
      <div className="panel-body">
        <div className="asset-filter-bar test-design-candidate-filter">
          <label className="field">
            <span className="field-label">{translate('auto.k1314')}</span>
            <select value={props.candidateFilters.status} onChange={(event) => props.onCandidateFiltersChange((current) => ({ ...current, status: event.target.value }))} disabled={props.taskState.loading || !props.selectedTaskId}>
              <option value="">{translate('auto.k0195')}</option>
              {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1315')}</span>
            <select value={props.candidateFilters.coverageType} onChange={(event) => props.onCandidateFiltersChange((current) => ({ ...current, coverageType: event.target.value }))} disabled={props.taskState.loading || !props.selectedTaskId}>
              <option value="">{translate('auto.k0195')}</option>
              {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1316')}</span>
            <input value={props.candidateFilters.keyword} onChange={(event) => props.onCandidateFiltersChange((current) => ({ ...current, keyword: event.target.value }))} placeholder={translate('auto.k1317')} disabled={props.taskState.loading || !props.selectedTaskId} />
          </label>
          <div className="filter-actions">
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.selectedTaskId} onClick={() => props.onCandidateFiltersChange(initialCandidateFilters)}>
              <Search size={15} />
              {translate('auto.k0254')}</button>
            <button className="btn btn-ghost btn-sm" type="button" disabled={!props.currentPageSelectableCount} onClick={props.onSelectCurrentPageCandidates}>
              {translate('auto.k1318')}</button>
            <button className="btn btn-ghost btn-sm" type="button" disabled={!props.selectedCandidateIds.length} onClick={() => props.onSelectedCandidateIdsChange([])}>
              {translate('auto.k1144')}</button>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || !props.selectedTaskId || !props.candidatePage.total} onClick={() => props.onExportCandidateReview('page')}>
              <Download size={15} />
              {translate('auto.k1319')}</button>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || !props.selectedCandidates.length} onClick={() => props.onExportCandidateReview('selected')}>
              <Download size={15} />
              {translate('auto.k1320')}</button>
            <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || !props.selectedTask || props.taskState.loading} onClick={props.onExportTaskReport}>
              <Download size={15} />
              {translate('auto.k1178')}</button>
          </div>
        </div>
        {props.candidatePage.total > 0 && (
          <div className="test-design-pagination" aria-label={translate('auto.k1321')}>
            <span>
              {props.candidatePage.start}-{props.candidatePage.end} / {props.candidatePage.total}
              {props.selectedCandidates.length ? translate('auto.k1322', { value0: props.selectedCandidates.length }) : ''}
            </span>
            <label>
              <span>{translate('auto.k1323')}</span>
              <select value={props.candidatePageSize} onChange={(event) => props.onCandidatePageSizeChange(Number(event.target.value))} disabled={props.taskState.loading}>
                {TEST_DESIGN_CANDIDATE_PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
              </select>
            </label>
            <div className="toolbar-actions">
              <button
                aria-label={translate('auto.k1324')}
                className="btn btn-secondary btn-xs"
                disabled={!props.candidatePage.hasPrevious}
                title={translate('auto.k1325')}
                type="button"
                onClick={() => props.onCandidatePageIndexChange((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft size={14} />
              </button>
              <span className="field-hint">{props.candidatePage.index + 1} / {props.candidatePage.totalPages}</span>
              <button
                aria-label={translate('auto.k1326')}
                className="btn btn-secondary btn-xs"
                disabled={!props.candidatePage.hasNext}
                title={translate('auto.k1327')}
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
            <span>{translate('auto.k1328')}{props.selectedReviewCandidates.length} {translate('auto.k1329')}</span>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onBatchReviewCandidates('CONFIRM')}>
                {translate('auto.k0801')}</button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || !props.reviewComment.trim()} onClick={() => props.onBatchReviewCandidates('REJECT')}>
                {translate('auto.k1330')}</button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || !props.reviewComment.trim()} onClick={() => props.onBatchReviewCandidates('IGNORE')}>
                {translate('auto.k0802')}</button>
            </div>
          </div>
        )}
        {props.selectedCandidateIds.length > 0 && (
          <div className="test-design-batch-editor">
            <div className="test-design-batch-editor-heading">
              <span>{translate('auto.k1331')}{props.selectedBatchEditableCandidates.length} / {props.selectedCandidateIds.length} {translate('auto.k1332')}</span>
              <button className="btn btn-ghost btn-xs" type="button" disabled={props.mutationState.loading} onClick={() => props.onBatchEditDraftChange({
                coverageType: '',
                priority: '',
                tags: '',
                tagMode: 'append'
              })}>
                {translate('auto.k0254')}</button>
            </div>
            <div className="test-design-batch-editor-grid">
              <label className="field">
                <span className="field-label">{translate('auto.k1315')}</span>
                <select value={props.batchEditDraft.coverageType} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, coverageType: event.target.value }))} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="">{translate('auto.k1333')}</option>
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0419')}</span>
                <select value={props.batchEditDraft.priority} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, priority: event.target.value }))} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="">{translate('auto.k1333')}</option>
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1334')}</span>
                <select value={props.batchEditDraft.tagMode} onChange={(event) => props.onBatchEditDraftChange((current) => ({ ...current, tagMode: event.target.value === 'replace' ? 'replace' : 'append' }))} disabled={!props.canReview || props.mutationState.loading}>
                  <option value="append">{translate('auto.k1335')}</option>
                  <option value="replace">{translate('auto.k1336')}</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0803')}</span>
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
                {translate('auto.k1337')}</button>
              {props.batchEditFieldLabels.length > 0 && <span className="field-hint">{props.batchEditFieldLabels.join('；')}</span>}
            </div>
          </div>
        )}
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th className="table-check-column"></th>
                <th>{translate('auto.k0440')}</th>
                <th>{translate('auto.k0538')}</th>
                <th>{translate('auto.k0419')}</th>
                <th>{translate('auto.k0182')}</th>
                <th>{translate('auto.k0179')}</th>
                <th>{translate('auto.k0249')}</th>
              </tr>
            </thead>
            <tbody>
              {props.candidatePage.items.length ? (
                props.candidatePage.items.map((candidate) => (
                  <tr className={candidate.id === props.selectedCandidateId ? 'selected-row' : ''} key={candidate.id}>
                    <td>
                      <input
                        aria-label={translate('auto.k1338', { value0: candidate.title })}
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
                        {translate('auto.k1089')}</button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="table-empty" colSpan={7}>{props.selectedTaskId ? translate('auto.k1339') : translate('auto.k1340')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {candidateDraft && props.selectedCandidate && (
          <div className="test-design-editor">
            <div className="test-design-source-summary">
              <span>{translate('auto.k1341')}</span>
              <GenerationSourceBadge source={props.selectedCandidateSource} />
              <em>{generationSourceText(props.selectedCandidateSource)}</em>
            </div>
            {props.candidateQualityIssues.length > 0 && (
              <div className="notice warning test-design-quality-summary">
                <strong>{translate('auto.k1342')}</strong>
                <span>{translate('auto.k1343')}{props.candidateQualityIssues.length} {translate('auto.k1344')}</span>
                <ul className="test-design-quality-list">
                  {props.candidateQualityIssues.slice(0, 6).map((issue, index) => (
                    <li key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</li>
                  ))}
                </ul>
              </div>
            )}
            <div className="asset-form-grid">
              <label className="field">
                <span className="field-label">{translate('auto.k0440')}</span>
                <input value={candidateDraft.title} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, title: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
                <QualityFieldMessages field="title" issues={props.candidateQualityIssues} />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1315')}</span>
                <select value={candidateDraft.coverageType} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, coverageType: event.target.value })} disabled={!props.canReview || props.mutationState.loading}>
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
                <QualityFieldMessages field="coverageType" issues={props.candidateQualityIssues} />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0419')}</span>
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
                <span className="field-label">{translate('auto.k1345')}</span>
                <input value={candidateDraft.preconditions} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, preconditions: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
                <QualityFieldMessages field="preconditions" issues={props.candidateQualityIssues} />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0803')}</span>
                <input value={candidateDraft.tags} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, tags: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
                <QualityFieldMessages field="tags" issues={props.candidateQualityIssues} />
              </label>
            </div>
            <label className="field">
              <span className="field-label">{translate('auto.k0443')}</span>
              <textarea value={candidateDraft.description} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, description: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
              <QualityFieldMessages field="description" issues={props.candidateQualityIssues} />
            </label>
            <div className="field test-design-steps-editor">
              <div className="test-design-steps-heading">
                <span className="field-label">{translate('auto.k1346')}</span>
                <div className="toolbar-actions">
                  <button className="btn btn-secondary btn-xs" type="button" title={translate('auto.k1347')} disabled={!props.canReview || props.mutationState.loading} onClick={props.onInsertPresetSteps}>
                    <Plus size={14} />
                    {translate('auto.k1348')}</button>
                  <button className="btn btn-secondary btn-icon btn-xs" type="button" title={translate('auto.k1349')} disabled={!props.canReview || props.mutationState.loading} onClick={props.onAddStepDraft}>
                    <Plus size={14} />
                  </button>
                  <button className="btn btn-ghost btn-icon btn-xs" type="button" title={translate('auto.k1350')} disabled={!props.canReview || props.mutationState.loading || !candidateDraft.steps.some((step) => step.selected)} onClick={props.onDeleteSelectedSteps}>
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
                    <label className="test-design-step-select" title={translate('auto.k1351')}>
                      <input type="checkbox" checked={step.selected} onChange={(event) => props.onUpdateStepDraft(step.id, { selected: event.target.checked })} disabled={!props.canReview || props.mutationState.loading} />
                    </label>
                    <button className="btn btn-ghost btn-icon btn-xs test-design-step-drag" type="button" title={translate('auto.k0445')} disabled={!props.canReview || props.mutationState.loading}>
                      <GripVertical size={14} />
                    </button>
                    <span className="asset-step-index">{index + 1}</span>
                    <StepRichTextField
                      disabled={!props.canReview || props.mutationState.loading}
                      id={`test-design-step-action-${step.id}`}
                      label={translate('auto.k0249')}
                      onChange={(value) => props.onUpdateStepDraft(step.id, { action: value })}
                      onFormat={(style) => applyStepMarkup(step, 'action', style)}
                      value={step.action}
                    />
                    <StepRichTextField
                      disabled={!props.canReview || props.mutationState.loading}
                      id={`test-design-step-expectedResult-${step.id}`}
                      label={translate('auto.k1352')}
                      onChange={(value) => props.onUpdateStepDraft(step.id, { expectedResult: value })}
                      onFormat={(style) => applyStepMarkup(step, 'expectedResult', style)}
                      value={step.expectedResult}
                    />
                    <div className="test-design-step-actions">
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title={translate('auto.k0449')} disabled={!props.canReview || props.mutationState.loading || index === 0} onClick={() => props.onMoveStepDraft(step.id, -1)}>
                        <ArrowUp size={14} />
                      </button>
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title={translate('auto.k0450')} disabled={!props.canReview || props.mutationState.loading || index === candidateDraft.steps.length - 1} onClick={() => props.onMoveStepDraft(step.id, 1)}>
                        <ArrowDown size={14} />
                      </button>
                      <button className="btn btn-secondary btn-icon btn-xs" type="button" title={translate('auto.k1353')} disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onInsertStepDraftAfter(step.id)}>
                        <Plus size={14} />
                      </button>
                      <button className="btn btn-ghost btn-icon btn-xs" type="button" title={translate('auto.k1354')} disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onRemoveStepDraft(step.id)}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
              <span className="field-hint">{candidateDraft.steps.length} {translate('auto.k1355')}{candidateDraft.steps.filter((step) => step.selected).length} {translate('auto.k1356')}</span>
              <QualityFieldMessages field="steps" issues={props.candidateQualityIssues} />
            </div>
            <label className="field">
              <span className="field-label">{translate('auto.k0447')}</span>
              <textarea value={candidateDraft.expectedResult} onChange={(event) => props.onCandidateDraftChange({ ...candidateDraft, expectedResult: event.target.value })} disabled={!props.canReview || props.mutationState.loading} />
              <QualityFieldMessages field="expectedResult" issues={props.candidateQualityIssues} />
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k1357')}</span>
              <input value={props.reviewComment} onChange={(event) => props.onReviewCommentChange(event.target.value)} disabled={!props.canReview || props.mutationState.loading} />
            </label>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading || !candidateDraft.title.trim() || props.candidateSaveBlocked} onClick={props.onSaveCandidate}>
                <Save size={15} />
                {translate('auto.k0806')}</button>
              <button className="btn btn-primary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onReviewCandidate('confirm')}>
                <CheckCircle2 size={15} />
                {translate('auto.k0807')}</button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onReviewCandidate('reject')}>
                <XCircle size={15} />
                {translate('auto.k0214')}</button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={!props.canReview || props.mutationState.loading} onClick={() => props.onReviewCandidate('ignore')}>
                {translate('auto.k0808')}</button>
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
