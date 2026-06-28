import {
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Link2,
  RefreshCw,
  Search
} from 'lucide-react';
import type { Dispatch, SetStateAction } from 'react';
import type { AssetTestCaseView } from '../api/assets';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  type TestDesignCandidateView,
  type TestDesignConflictOperationItem,
  type TestDesignConflictOperationsSummary,
  type TestDesignPublishRecordView
} from '../api/testDesign';
import type { PaginatedItems } from '../testDesignPagination';
import {
  TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE,
  conflictResolutionCandidate,
  conflictResolutionTargetCaseId,
  initialConflictOperationFilters,
  type ConflictOperationFilters,
  type ConflictResolutionDraft
} from '../testDesignWorkbenchState';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import {
  Detail,
  PublishResultBadge,
  publishRecordKey,
  shortIdentifier
} from './TestDesignWorkbenchShared';
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { InputControl, SelectControl } from './ui';

export function TestDesignConflictOperationsPanel(props: {
  canRead: boolean;
  canPublish: boolean;
  state: WorkState;
  publishState: WorkState;
  summary: TestDesignConflictOperationsSummary | null;
  operations: TestDesignConflictOperationItem[];
  page: PaginatedItems<TestDesignConflictOperationItem>;
  projectId: string;
  selectedTaskId: string;
  filters: ConflictOperationFilters;
  conflictResolutionDraft: ConflictResolutionDraft;
  conflictCaseKeyword: string;
  conflictCaseSearchProjectId: string;
  conflictCaseResults: AssetTestCaseView[];
  selectedConflictCaseIds: Record<string, string>;
  conflictCandidateById: Map<string, TestDesignCandidateView>;
  batchResolvableCount: number;
  onBatchResolve: () => void;
  onRefresh: (pageIndex: number) => void;
  onFiltersChange: Dispatch<SetStateAction<ConflictOperationFilters>>;
  onConflictResolutionDraftChange: Dispatch<SetStateAction<ConflictResolutionDraft>>;
  onConflictCaseKeywordChange: Dispatch<SetStateAction<string>>;
  onSelectedConflictCaseIdsChange: Dispatch<SetStateAction<Record<string, string>>>;
  onSearchConflictCases: () => void;
  onResolveConflict: (record: TestDesignPublishRecordView) => void;
}) {
  return (
    <section className="panel test-design-conflict-operations-panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1358')}</h2>
          <p className="panel-desc">
            {props.summary
              ? translate('auto.k1359', { value0: props.summary.openCount, value1: props.summary.resolvedCount })
              : translate('auto.k1360')}
          </p>
        </div>
        <div className="toolbar-actions">
          <button
            className="btn btn-secondary btn-xs"
            type="button"
            disabled={!props.canPublish || props.publishState.loading || !props.batchResolvableCount}
            onClick={props.onBatchResolve}
          >
            <Link2 size={14} />
            {translate('auto.k1361')}{props.batchResolvableCount}
          </button>
          <button
            className="btn btn-secondary btn-xs"
            type="button"
            disabled={!props.canRead || props.state.loading}
            onClick={() => props.onRefresh(0)}
          >
            <RefreshCw size={14} />
            {translate('auto.k0170')}</button>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        <div className="asset-filter-bar test-design-conflict-operations-filter">
          <label className="field">
            <span className="field-label">{translate('auto.k0176')}</span>
            <InputControl
              value={props.projectId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="project UUID"
              disabled={!props.canRead || props.state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1362')}</span>
            <InputControl
              value={props.filters.taskId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, taskId: event.target.value }))}
              placeholder={props.selectedTaskId || translate('auto.k1363')}
              disabled={!props.canRead || props.state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1364')}</span>
            <SelectControl
              value={props.filters.resolutionStatus}
              onChange={(event) => props.onFiltersChange((current) => ({
                ...current,
                resolutionStatus: event.target.value as ConflictOperationFilters['resolutionStatus']
              }))}
              disabled={!props.canRead || props.state.loading}
            >
              <option value="OPEN">{dictionaryLabel('OPEN')}</option>
              <option value="RESOLVED">{dictionaryLabel('RESOLVED')}</option>
              <option value="ALL">{dictionaryLabel('ALL')}</option>
            </SelectControl>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1314')}</span>
            <SelectControl
              value={props.filters.candidateStatus}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, candidateStatus: event.target.value }))}
              disabled={!props.canRead || props.state.loading}
            >
              <option value="">{translate('auto.k0195')}</option>
              {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => (
                <option value={status} key={status}>{dictionaryLabel(status)}</option>
              ))}
            </SelectControl>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1316')}</span>
            <InputControl
              value={props.filters.keyword}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, keyword: event.target.value }))}
              placeholder={translate('auto.k1365')}
              disabled={!props.canRead || props.state.loading}
            />
          </label>
          <div className="toolbar-actions test-design-conflict-operations-actions">
            <button
              className="btn btn-secondary btn-sm"
              type="button"
              disabled={!props.canRead || props.state.loading || !props.selectedTaskId}
              onClick={() => props.onFiltersChange((current) => ({ ...current, taskId: props.selectedTaskId }))}
            >
              <ClipboardCheck size={15} />
              {translate('auto.k1366')}</button>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              disabled={!props.canRead || props.state.loading}
              onClick={() => props.onFiltersChange(initialConflictOperationFilters)}
            >
              <Search size={15} />
              {translate('auto.k0254')}</button>
          </div>
        </div>
        <div className="test-design-conflict-form">
          <label className="field">
            <span className="field-label">{translate('auto.k1367')}</span>
            <InputControl
              value={props.conflictResolutionDraft.reason}
              onChange={(event) => props.onConflictResolutionDraftChange((current) => ({ ...current, reason: event.target.value }))}
              disabled={!props.canPublish || props.publishState.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1368')}</span>
            <InputControl
              value={props.conflictCaseKeyword}
              onChange={(event) => props.onConflictCaseKeywordChange(event.target.value)}
              placeholder={translate('auto.k1369')}
              disabled={!props.canRead || props.publishState.loading || !props.conflictCaseSearchProjectId}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1370')}</span>
            <InputControl
              value={props.conflictResolutionDraft.comment}
              onChange={(event) => props.onConflictResolutionDraftChange((current) => ({ ...current, comment: event.target.value }))}
              placeholder={translate('auto.k1371')}
              disabled={!props.canPublish || props.publishState.loading}
            />
          </label>
          <div className="field test-design-conflict-search-action">
            <span className="field-label">{translate('auto.k1372')}</span>
            <button
              className="btn btn-secondary btn-sm"
              type="button"
              disabled={!props.canRead || props.publishState.loading || !props.conflictCaseSearchProjectId}
              onClick={props.onSearchConflictCases}
            >
              <Search size={15} />
              {translate('auto.k1373')}</button>
          </div>
        </div>
        {props.summary && (
          <div className="detail-grid">
            <Detail label={translate('auto.k1374')} value={props.summary.totalCount} />
            <Detail label={translate('auto.k1375')} value={props.summary.openCount} />
            <Detail label={translate('auto.k1376')} value={props.summary.resolvedCount} />
            <Detail label={translate('auto.k1377')} value={props.summary.duplicateReviewCount} />
            <Detail label={translate('auto.k1378')} value={props.summary.latestConflictAt ?? '-'} />
          </div>
        )}
        <div className="test-design-conflict-operations-list">
          {props.operations.length ? props.operations.map((item) => {
            const record = item.record;
            const candidate = conflictResolutionCandidate(record, props.conflictCandidateById);
            const targetCaseId = conflictResolutionTargetCaseId(record, props.selectedConflictCaseIds);
            return (
              <div className={item.resolved ? 'test-design-conflict-operation-row resolved' : 'test-design-conflict-operation-row'} key={publishRecordKey(record)}>
                <span>
                  <strong>{item.candidateTitle ?? record.title ?? item.candidateId ?? '-'}</strong>
                  <em>{item.taskTitle ?? item.taskId ?? '-'} · {item.candidateStatus ?? '-'}@v{item.candidateVersion}</em>
                  <em>{targetCaseId ? translate('auto.k1379', { value0: targetCaseId }) : translate('auto.k1380', { value0: item.recommendedCaseId ?? '-' })}</em>
                  {record.errorMessage && <small>{record.errorMessage}</small>}
                </span>
                <div className="test-design-conflict-controls">
                  <PublishResultBadge value={item.resolved ? 'RESOLVED' : record.result} />
                  <SelectControl
                    value={targetCaseId}
                    onChange={(event) => {
                      const nextCaseId = event.target.value;
                      const candidateId = record.candidateId;
                      if (candidateId) {
                        props.onSelectedConflictCaseIdsChange((current) => ({
                          ...current,
                          [candidateId]: nextCaseId
                        }));
                      }
                    }}
                    disabled={!props.canPublish || props.publishState.loading || !item.resolvable}
                  >
                    <option value="">{record.assetCaseId ? translate('auto.k1381') : translate('auto.k1382')}</option>
                    {record.assetCaseId && (
                      <option value={record.assetCaseId}>{translate('auto.k1383')}{shortIdentifier(record.assetCaseId)}</option>
                    )}
                    {props.conflictCaseResults.filter((testCase) => testCase.id !== record.assetCaseId).map((testCase) => (
                      <option value={testCase.id} key={`${record.candidateId}-${testCase.id}`}>
                        {testCase.title || shortIdentifier(testCase.id)}
                      </option>
                    ))}
                  </SelectControl>
                  <button
                    className="btn btn-secondary btn-xs"
                    type="button"
                    disabled={!props.canPublish || props.publishState.loading || !item.resolvable || !candidate || !targetCaseId}
                    onClick={() => props.onResolveConflict(record)}
                  >
                    <Link2 size={14} />
                    {translate('auto.k1384')}</button>
                </div>
              </div>
            );
          }) : (
            <div className="notice info">{props.projectId ? translate('auto.k1385') : translate('auto.k1386')}</div>
          )}
        </div>
        {props.page.total > TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE && (
          <div className="test-design-pagination" aria-label={translate('auto.k1387')}>
            <span>
              {props.page.items.length
                ? `${props.page.start}-${props.page.end} / ${props.page.total}`
                : `0 / ${props.page.total}`}
            </span>
            <button
              className="btn btn-secondary btn-xs"
              type="button"
              disabled={props.page.index <= 0 || props.state.loading}
              onClick={() => props.onRefresh(Math.max(0, props.page.index - 1))}
            >
              <ChevronLeft size={14} />
              {translate('auto.k1325')}</button>
            <button
              className="btn btn-secondary btn-xs"
              type="button"
              disabled={(props.page.index + 1) * TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE >= props.page.total || props.state.loading}
              onClick={() => props.onRefresh(props.page.index + 1)}
            >
              {translate('auto.k1327')}<ChevronRight size={14} />
            </button>
          </div>
        )}
        <StateLine state={props.state} />
      </div>
    </section>
  );
}
