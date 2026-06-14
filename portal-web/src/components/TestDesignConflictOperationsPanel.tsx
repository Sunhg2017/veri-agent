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
          <h2 className="panel-title">资产冲突运营台</h2>
          <p className="panel-desc">
            {props.summary
              ? `未处理 ${props.summary.openCount} · 已处理 ${props.summary.resolvedCount}`
              : '正式发布冲突集中处理。'}
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
            批量复用 {props.batchResolvableCount}
          </button>
          <button
            className="btn btn-secondary btn-xs"
            type="button"
            disabled={!props.canRead || props.state.loading}
            onClick={() => props.onRefresh(0)}
          >
            <RefreshCw size={14} />
            刷新
          </button>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        <div className="asset-filter-bar test-design-conflict-operations-filter">
          <label className="field">
            <span className="field-label">项目</span>
            <input
              value={props.projectId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="project UUID"
              disabled={!props.canRead || props.state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">任务 ID</span>
            <input
              value={props.filters.taskId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, taskId: event.target.value }))}
              placeholder={props.selectedTaskId || '全部任务'}
              disabled={!props.canRead || props.state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">处理状态</span>
            <select
              value={props.filters.resolutionStatus}
              onChange={(event) => props.onFiltersChange((current) => ({
                ...current,
                resolutionStatus: event.target.value as ConflictOperationFilters['resolutionStatus']
              }))}
              disabled={!props.canRead || props.state.loading}
            >
              <option value="OPEN">OPEN</option>
              <option value="RESOLVED">RESOLVED</option>
              <option value="ALL">ALL</option>
            </select>
          </label>
          <label className="field">
            <span className="field-label">候选状态</span>
            <select
              value={props.filters.candidateStatus}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, candidateStatus: event.target.value }))}
              disabled={!props.canRead || props.state.loading}
            >
              <option value="">全部</option>
              {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => (
                <option value={status} key={status}>{status}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">关键词</span>
            <input
              value={props.filters.keyword}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, keyword: event.target.value }))}
              placeholder="候选 / 任务 / 用例"
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
              当前任务
            </button>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              disabled={!props.canRead || props.state.loading}
              onClick={() => props.onFiltersChange(initialConflictOperationFilters)}
            >
              <Search size={15} />
              重置
            </button>
          </div>
        </div>
        <div className="test-design-conflict-form">
          <label className="field">
            <span className="field-label">处理原因</span>
            <input
              value={props.conflictResolutionDraft.reason}
              onChange={(event) => props.onConflictResolutionDraftChange((current) => ({ ...current, reason: event.target.value }))}
              disabled={!props.canPublish || props.publishState.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">用例关键词</span>
            <input
              value={props.conflictCaseKeyword}
              onChange={(event) => props.onConflictCaseKeywordChange(event.target.value)}
              placeholder="标题 / 标签 / 编号"
              disabled={!props.canRead || props.publishState.loading || !props.conflictCaseSearchProjectId}
            />
          </label>
          <label className="field">
            <span className="field-label">补充说明</span>
            <input
              value={props.conflictResolutionDraft.comment}
              onChange={(event) => props.onConflictResolutionDraftChange((current) => ({ ...current, comment: event.target.value }))}
              placeholder="比对说明"
              disabled={!props.canPublish || props.publishState.loading}
            />
          </label>
          <div className="field test-design-conflict-search-action">
            <span className="field-label">既有用例</span>
            <button
              className="btn btn-secondary btn-sm"
              type="button"
              disabled={!props.canRead || props.publishState.loading || !props.conflictCaseSearchProjectId}
              onClick={props.onSearchConflictCases}
            >
              <Search size={15} />
              搜索
            </button>
          </div>
        </div>
        {props.summary && (
          <div className="detail-grid">
            <Detail label="冲突总数" value={props.summary.totalCount} />
            <Detail label="未处理" value={props.summary.openCount} />
            <Detail label="已处理" value={props.summary.resolvedCount} />
            <Detail label="人工复核" value={props.summary.duplicateReviewCount} />
            <Detail label="最近冲突" value={props.summary.latestConflictAt ?? '-'} />
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
                  <em>{targetCaseId ? `目标用例 ${targetCaseId}` : `推荐用例 ${item.recommendedCaseId ?? '-'}`}</em>
                  {record.errorMessage && <small>{record.errorMessage}</small>}
                </span>
                <div className="test-design-conflict-controls">
                  <PublishResultBadge value={item.resolved ? 'RESOLVED' : record.result} />
                  <select
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
                    <option value="">{record.assetCaseId ? '清空目标' : '选择目标用例'}</option>
                    {record.assetCaseId && (
                      <option value={record.assetCaseId}>推荐 {shortIdentifier(record.assetCaseId)}</option>
                    )}
                    {props.conflictCaseResults.filter((testCase) => testCase.id !== record.assetCaseId).map((testCase) => (
                      <option value={testCase.id} key={`${record.candidateId}-${testCase.id}`}>
                        {testCase.title || shortIdentifier(testCase.id)}
                      </option>
                    ))}
                  </select>
                  <button
                    className="btn btn-secondary btn-xs"
                    type="button"
                    disabled={!props.canPublish || props.publishState.loading || !item.resolvable || !candidate || !targetCaseId}
                    onClick={() => props.onResolveConflict(record)}
                  >
                    <Link2 size={14} />
                    复用
                  </button>
                </div>
              </div>
            );
          }) : (
            <div className="notice info">{props.projectId ? '暂无匹配冲突' : '请先填写项目 ID'}</div>
          )}
        </div>
        {props.page.total > TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE && (
          <div className="test-design-pagination" aria-label="资产冲突分页">
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
              上一页
            </button>
            <button
              className="btn btn-secondary btn-xs"
              type="button"
              disabled={(props.page.index + 1) * TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE >= props.page.total || props.state.loading}
              onClick={() => props.onRefresh(props.page.index + 1)}
            >
              下一页
              <ChevronRight size={14} />
            </button>
          </div>
        )}
        <StateLine state={props.state} />
      </div>
    </section>
  );
}
