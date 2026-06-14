import { ChevronLeft, ChevronRight, Download } from 'lucide-react';
import type { Dispatch, SetStateAction } from 'react';
import type { TestDesignReviewRecordView } from '../api/testDesign';
import type { PaginatedItems } from '../testDesignPagination';
import type { TestDesignReviewSummary } from '../testDesignReviewSummary';
import {
  ReviewSummaryPanel,
  StateLine,
  type WorkState
} from './TestDesignOverviewPanels';
import { ReviewRecordRow } from './TestDesignWorkbenchShared';

export function TestDesignReviewHistoryPanel(props: {
  canExport: boolean;
  state: WorkState;
  reviewRecordPageTotal: number;
  reviewRecordPage: PaginatedItems<TestDesignReviewRecordView>;
  reviewSummaryScope: string;
  selectedTaskId: string;
  reviewSummary: TestDesignReviewSummary;
  onExport: () => void;
  onReviewRecordPageIndexChange: Dispatch<SetStateAction<number>>;
}) {
  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">评审历史</h2>
          <p className="panel-desc">{props.reviewRecordPageTotal ? `${props.reviewRecordPageTotal} 条候选编辑和评审记录` : '候选编辑和评审审计摘要。'}</p>
        </div>
        <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport || props.state.loading || !props.reviewRecordPageTotal} onClick={props.onExport}>
          <Download size={15} />
          导出
        </button>
      </div>
      <div className="panel-body compact main-stack">
        <StateLine state={props.state} />
        <ReviewSummaryPanel
          scopeLabel={props.reviewSummaryScope}
          selectedTaskId={props.selectedTaskId}
          summary={props.reviewSummary}
        />
        {props.reviewRecordPage.total > 0 && (
          <div className="test-design-pagination" aria-label="评审历史分页">
            <span>{props.reviewRecordPage.start}-{props.reviewRecordPage.end} / {props.reviewRecordPage.total}</span>
            <div className="toolbar-actions">
              <button
                aria-label="上一页评审历史"
                className="btn btn-secondary btn-xs"
                disabled={!props.reviewRecordPage.hasPrevious || props.state.loading}
                title="上一页"
                type="button"
                onClick={() => props.onReviewRecordPageIndexChange((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft size={14} />
              </button>
              <span className="field-hint">{props.reviewRecordPage.index + 1} / {props.reviewRecordPage.totalPages}</span>
              <button
                aria-label="下一页评审历史"
                className="btn btn-secondary btn-xs"
                disabled={!props.reviewRecordPage.hasNext || props.state.loading}
                title="下一页"
                type="button"
                onClick={() => props.onReviewRecordPageIndexChange((current) => current + 1)}
              >
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        )}
        {props.reviewRecordPage.items.length ? (
          <div className="test-design-review-records">
            {props.reviewRecordPage.items.map((record) => (
              <ReviewRecordRow key={record.id} record={record} />
            ))}
          </div>
        ) : (
          <div className="notice info">{props.selectedTaskId ? '暂无评审历史' : '请先选择任务'}</div>
        )}
      </div>
    </section>
  );
}
