import {
  CheckCircle2,
  Download,
  Eye,
  FileText,
  Link2,
  Plus,
  RefreshCw,
  Save,
  Search,
  Send,
  XCircle
} from 'lucide-react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import type { AssetTestCaseView } from '../api/assets';
import type {
  TestDesignCandidateView,
  TestDesignPublishRecordView,
  TestDesignPublishResult,
  TestDesignReleaseReadinessApprovalView,
  TestDesignReleaseReadinessNoteView,
  TestDesignReportArchiveApprovalView,
  TestDesignReportArchiveIntegrityView,
  TestDesignReportArchiveNoteView,
  TestDesignReportArchiveView
} from '../api/testDesign';
import {
  conflictResolutionCandidate,
  conflictResolutionTargetCaseId,
  type ConflictResolutionDraft,
  type ReleaseReadinessApprovalDraft,
  type ReportArchiveApprovalDraft
} from '../testDesignWorkbenchState';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import {
  Detail,
  PublishRecordRow,
  publishRecordKey,
  releaseReadinessDigestText,
  releaseReadinessStatusTone,
  reportArchiveStatusTone,
  shortIdentifier
} from './TestDesignWorkbenchShared';

export function TestDesignPublishPanel(props: {
  canRead: boolean;
  canExport: boolean;
  canPublish: boolean;
  selectedTaskId: string;
  taskState: WorkState;
  publishState: WorkState;
  releaseReadinessState: WorkState;
  reportArchiveState: WorkState;
  publishScopeLabel: string;
  selectedCandidateCount: number;
  selectedPublishableCount: number;
  canPublishCurrentScope: boolean;
  currentReleaseReadiness: ReleaseReadinessSnapshot | null;
  releaseReadinessApprovals: TestDesignReleaseReadinessApprovalView[];
  selectedReleaseReadinessApprovalId: string;
  selectedReleaseReadinessApproval: TestDesignReleaseReadinessApprovalView | null;
  selectedPendingReleaseReadinessApproval: TestDesignReleaseReadinessApprovalView | null;
  releaseReadinessDraft: ReleaseReadinessApprovalDraft;
  releaseReadinessSubmitBlocked: boolean;
  releaseReadinessNotes: TestDesignReleaseReadinessNoteView[];
  releaseReadinessReasonCodes: readonly string[];
  releaseReadinessWorkOrderStatuses: readonly string[];
  reportArchives: TestDesignReportArchiveView[];
  selectedReportArchiveId: string;
  selectedReportArchive: TestDesignReportArchiveView | null;
  reportArchiveIntegrity: TestDesignReportArchiveIntegrityView | null;
  reportArchiveApprovals: TestDesignReportArchiveApprovalView[];
  selectedReportArchiveApprovalId: string;
  selectedReportArchiveApproval: TestDesignReportArchiveApprovalView | null;
  selectedPendingReportArchiveApproval: TestDesignReportArchiveApprovalView | null;
  reportArchiveDraft: ReportArchiveApprovalDraft;
  reportArchiveNotes: TestDesignReportArchiveNoteView[];
  reportArchiveApprovalTypes: readonly string[];
  reportArchiveReasonCodes: readonly string[];
  reportArchiveWorkOrderStatuses: readonly string[];
  publishResult: TestDesignPublishResult | null;
  publishIssueRecords: TestDesignPublishRecordView[];
  resolvableConflictRecords: TestDesignPublishRecordView[];
  batchResolvableConflictCount: number;
  conflictResolutionDraft: ConflictResolutionDraft;
  conflictCaseKeyword: string;
  conflictCaseSearchProjectId: string;
  conflictCaseResults: AssetTestCaseView[];
  selectedConflictCaseIds: Record<string, string>;
  conflictCandidateById: Map<string, TestDesignCandidateView>;
  onPublish: (dryRun: boolean) => void;
  onRefreshReleaseReadiness: (taskId: string) => void;
  onReleaseReadinessDraftChange: Dispatch<SetStateAction<ReleaseReadinessApprovalDraft>>;
  onRequestReleaseReadinessApproval: (event: FormEvent<HTMLFormElement>) => void;
  onReviewReleaseReadinessApproval: (approvalId: string, action: 'approve' | 'reject') => void;
  onSelectReleaseReadinessApproval: (approval: TestDesignReleaseReadinessApprovalView) => void;
  onAddReleaseReadinessNote: () => void;
  onRefreshReportArchives: (taskId: string) => void;
  onReportArchiveDraftChange: Dispatch<SetStateAction<ReportArchiveApprovalDraft>>;
  onRequestReportArchiveApproval: (event: FormEvent<HTMLFormElement>) => void;
  onReviewReportArchiveApproval: (approvalId: string, action: 'approve' | 'reject') => void;
  onSelectReportArchive: (archive: TestDesignReportArchiveView) => void;
  onSelectReportArchiveApproval: (approval: TestDesignReportArchiveApprovalView) => void;
  onAddReportArchiveNote: () => void;
  onExportPublishResult: () => void;
  onRequestBatchResolveConflicts: () => void;
  onConflictResolutionDraftChange: Dispatch<SetStateAction<ConflictResolutionDraft>>;
  onConflictCaseKeywordChange: Dispatch<SetStateAction<string>>;
  onSearchConflictCases: () => void;
  onSelectedConflictCaseIdsChange: Dispatch<SetStateAction<Record<string, string>>>;
  onResolveConflict: (record: TestDesignPublishRecordView) => void;
}) {
  const releaseDraft = props.releaseReadinessDraft;
  const archiveDraft = props.reportArchiveDraft;
  const publishResult = props.publishResult;

  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">发布</h2>
          <p className="panel-desc">发布范围 {props.publishScopeLabel}。</p>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        {props.selectedCandidateCount > 0 ? (
          <div className="notice info">已按勾选候选中的可发布项发布：{props.selectedPublishableCount} / {props.selectedCandidateCount}。</div>
        ) : (
          <div className="notice info">当前将覆盖全部可发布候选。</div>
        )}
        <button className="btn btn-secondary" type="button" disabled={!props.canPublish || props.taskState.loading || props.publishState.loading || !props.canPublishCurrentScope} onClick={() => props.onPublish(true)}>
          <Eye size={16} />
          预发布
        </button>
        <button className="btn btn-primary" type="button" disabled={!props.canPublish || props.taskState.loading || props.publishState.loading || !props.canPublishCurrentScope} onClick={() => props.onPublish(false)}>
          <Send size={16} />
          发布到资产库
        </button>
        <StateLine state={props.publishState} />
        <div className="test-design-release-readiness-panel">
          <div className="test-design-release-readiness-heading">
            <span>发布准出审批</span>
            <div className="toolbar-actions">
              {props.currentReleaseReadiness && (
                <span className={`badge badge-${releaseReadinessStatusTone(props.currentReleaseReadiness.status)}`}>
                  {props.currentReleaseReadiness.status}
                </span>
              )}
              <button
                className="btn btn-secondary btn-xs"
                type="button"
                disabled={!props.canRead || props.releaseReadinessState.loading || !props.selectedTaskId}
                onClick={() => props.onRefreshReleaseReadiness(props.selectedTaskId)}
              >
                <RefreshCw size={14} />
                刷新
              </button>
            </div>
          </div>
          <div className="detail-grid">
            <Detail label="当前阻断" value={props.currentReleaseReadiness?.blockingCount ?? '-'} />
            <Detail label="当前风险" value={props.currentReleaseReadiness?.warningCount ?? '-'} />
            <Detail label="审批记录" value={props.releaseReadinessApprovals.length} />
            <Detail label="当前摘要" value={releaseReadinessDigestText(props.selectedReleaseReadinessApproval?.readinessDigest)} />
          </div>
          <form className="test-design-release-readiness-form" onSubmit={props.onRequestReleaseReadinessApproval}>
            <label className="field">
              <span className="field-label">例外原因</span>
              <select
                value={releaseDraft.exceptionReasonCode}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, exceptionReasonCode: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              >
                {props.releaseReadinessReasonCodes.map((code) => (
                  <option key={code} value={code}>{code}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="field-label">工单编号</span>
              <input
                value={releaseDraft.workOrderKey}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, workOrderKey: event.target.value }))}
                placeholder="WP5-RR-..."
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              />
            </label>
            <label className="field">
              <span className="field-label">工单标题</span>
              <input
                value={releaseDraft.workOrderTitle}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, workOrderTitle: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              />
            </label>
            <label className="field">
              <span className="field-label">工单 URL</span>
              <input
                value={releaseDraft.workOrderUrl}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, workOrderUrl: event.target.value }))}
                placeholder="https://..."
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              />
            </label>
            <label className="field test-design-release-readiness-wide">
              <span className="field-label">例外摘要</span>
              <textarea
                value={releaseDraft.exceptionSummary}
                maxLength={1000}
                rows={3}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, exceptionSummary: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              />
            </label>
            <label className="field test-design-release-readiness-wide">
              <span className="field-label">风险缓释</span>
              <textarea
                value={releaseDraft.riskMitigation}
                maxLength={1000}
                rows={3}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, riskMitigation: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              />
            </label>
            <label className="field test-design-release-readiness-wide">
              <span className="field-label">申请备注</span>
              <textarea
                value={releaseDraft.requestNote}
                maxLength={1000}
                rows={2}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, requestNote: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading}
              />
            </label>
            <button
              className="btn btn-secondary btn-sm test-design-release-readiness-submit"
              type="submit"
              disabled={!props.canPublish || props.releaseReadinessSubmitBlocked}
            >
              <Save size={15} />
              {props.selectedPendingReleaseReadinessApproval ? '更新例外' : '提交例外'}
            </button>
          </form>
          <div className="test-design-release-readiness-review-grid">
            <label className="field">
              <span className="field-label">审批原因</span>
              <select
                value={releaseDraft.approvalReasonCode}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, approvalReasonCode: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedPendingReleaseReadinessApproval}
              >
                {props.releaseReadinessReasonCodes.map((code) => (
                  <option key={code} value={code}>{code}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="field-label">工单状态</span>
              <select
                value={releaseDraft.workOrderStatus}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, workOrderStatus: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedPendingReleaseReadinessApproval}
              >
                <option value="">跟随审批</option>
                {props.releaseReadinessWorkOrderStatuses.map((status) => (
                  <option key={status} value={status}>{status}</option>
                ))}
              </select>
            </label>
            <label className="field test-design-release-readiness-wide">
              <span className="field-label">审批备注</span>
              <textarea
                value={releaseDraft.reviewNote}
                maxLength={1000}
                rows={2}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, reviewNote: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedPendingReleaseReadinessApproval}
              />
            </label>
            <div className="toolbar-actions test-design-release-readiness-submit">
              <button
                className="btn btn-secondary btn-sm"
                type="button"
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedPendingReleaseReadinessApproval}
                onClick={() => props.selectedPendingReleaseReadinessApproval && props.onReviewReleaseReadinessApproval(props.selectedPendingReleaseReadinessApproval.id, 'approve')}
              >
                <CheckCircle2 size={15} />
                通过
              </button>
              <button
                className="btn btn-ghost btn-sm"
                type="button"
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedPendingReleaseReadinessApproval}
                onClick={() => props.selectedPendingReleaseReadinessApproval && props.onReviewReleaseReadinessApproval(props.selectedPendingReleaseReadinessApproval.id, 'reject')}
              >
                <XCircle size={15} />
                驳回
              </button>
            </div>
          </div>
          <div className="test-design-release-readiness-approvals">
            {props.releaseReadinessApprovals.length ? props.releaseReadinessApprovals.slice(0, 6).map((approval) => (
              <div className={`test-design-release-readiness-approval${props.selectedReleaseReadinessApprovalId === approval.id ? ' selected' : ''}`} key={approval.id}>
                <div>
                  <strong>{approval.workOrderKey ?? approval.id}</strong>
                  <em>{approval.qualityGateStatus} · 阻断 {approval.blockingCount} · 风险 {approval.warningCount}</em>
                  <small>{releaseReadinessDigestText(approval.readinessDigest)} · 备注 {approval.noteCount ?? 0}</small>
                  <small>{approval.requestedBy ?? '-'} · {approval.createdAt ?? '-'}</small>
                  {approval.latestNotePreview ? <small>最新备注：{approval.latestNotePreview}</small> : null}
                </div>
                <div className="test-design-release-readiness-approval-actions">
                  <span className={`badge badge-${releaseReadinessStatusTone(approval.status)}`}>{approval.status}</span>
                  <button
                    className="btn btn-secondary btn-xs"
                    type="button"
                    disabled={!props.canRead || props.releaseReadinessState.loading}
                    onClick={() => props.onSelectReleaseReadinessApproval(approval)}
                  >
                    <FileText size={14} />
                    {approval.status === 'PENDING' ? '编辑' : '流转'}
                  </button>
                </div>
              </div>
            )) : (
              <div className="notice info">暂无发布准出审批记录</div>
            )}
          </div>
          <div className="test-design-release-readiness-note-form">
            <label className="field">
              <span className="field-label">备注类型</span>
              <select
                value={releaseDraft.noteType}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, noteType: event.target.value === 'WORK_ORDER' ? 'WORK_ORDER' : 'COMMENT' }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedReleaseReadinessApprovalId}
              >
                <option value="COMMENT">COMMENT</option>
                <option value="WORK_ORDER">WORK_ORDER</option>
              </select>
            </label>
            <label className="field test-design-release-readiness-wide">
              <span className="field-label">流转备注</span>
              <textarea
                value={releaseDraft.noteText}
                maxLength={1000}
                rows={2}
                onChange={(event) => props.onReleaseReadinessDraftChange((current) => ({ ...current, noteText: event.target.value }))}
                disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedReleaseReadinessApprovalId}
              />
            </label>
            <button
              className="btn btn-secondary btn-sm test-design-release-readiness-submit"
              type="button"
              disabled={!props.canPublish || props.releaseReadinessState.loading || !props.selectedReleaseReadinessApprovalId || !releaseDraft.noteText.trim()}
              onClick={props.onAddReleaseReadinessNote}
            >
              <Plus size={15} />
              追加备注
            </button>
          </div>
          <div className="test-design-release-readiness-notes">
            <strong>备注流转 · {props.selectedReleaseReadinessApproval?.workOrderKey ?? (props.selectedReleaseReadinessApprovalId || '-')}</strong>
            {props.selectedReleaseReadinessApprovalId ? (
              props.releaseReadinessNotes.length ? props.releaseReadinessNotes.slice(-6).map((note) => (
                <div className="test-design-release-readiness-note" key={note.id}>
                  <span className="badge badge-neutral">{note.noteType}</span>
                  <em>{note.noteText}</em>
                  <small>{note.createdBy ?? '-'} · {note.createdAt ?? '-'}</small>
                </div>
              )) : (
                <div className="notice info">暂无备注流转记录</div>
              )
            ) : (
              <div className="notice info">未选择发布准出审批记录</div>
            )}
          </div>
          <StateLine state={props.releaseReadinessState} />
        </div>

        <ReportArchivePanel
          canRead={props.canRead}
          canExport={props.canExport}
          selectedTaskId={props.selectedTaskId}
          state={props.reportArchiveState}
          archives={props.reportArchives}
          selectedArchiveId={props.selectedReportArchiveId}
          selectedArchive={props.selectedReportArchive}
          archiveIntegrity={props.reportArchiveIntegrity}
          approvals={props.reportArchiveApprovals}
          selectedApprovalId={props.selectedReportArchiveApprovalId}
          selectedApproval={props.selectedReportArchiveApproval}
          selectedPendingApproval={props.selectedPendingReportArchiveApproval}
          draft={archiveDraft}
          notes={props.reportArchiveNotes}
          approvalTypes={props.reportArchiveApprovalTypes}
          reasonCodes={props.reportArchiveReasonCodes}
          workOrderStatuses={props.reportArchiveWorkOrderStatuses}
          onRefresh={props.onRefreshReportArchives}
          onDraftChange={props.onReportArchiveDraftChange}
          onRequestApproval={props.onRequestReportArchiveApproval}
          onReviewApproval={props.onReviewReportArchiveApproval}
          onSelectArchive={props.onSelectReportArchive}
          onSelectApproval={props.onSelectReportArchiveApproval}
          onAddNote={props.onAddReportArchiveNote}
        />

        {publishResult && (
          <>
            <div className="toolbar-actions test-design-export-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canExport} onClick={props.onExportPublishResult}>
                <Download size={15} />
                导出发布摘要
              </button>
            </div>
            <div className="detail-grid">
              <Detail label="总数" value={publishResult.total} />
              <Detail label="创建" value={publishResult.created} />
              <Detail label="跳过" value={publishResult.skipped} />
              <Detail label="失败" value={publishResult.failed} />
              <Detail label="用例" value={publishResult.createdCaseIds.join(', ') || '-'} />
            </div>
            {props.publishIssueRecords.length > 0 && (
              <div className="notice warning test-design-publish-issues">
                {props.publishIssueRecords.slice(0, 4).map((record) => (
                  <span key={`${record.candidateId}-${record.result}-${record.errorMessage ?? ''}`}>
                    {record.title ?? record.candidateId ?? '-'}：{record.result}{record.errorMessage ? ` · ${record.errorMessage}` : ''}
                  </span>
                ))}
              </div>
            )}
            {props.resolvableConflictRecords.length > 0 && (
              <PublishConflictPanel
                canRead={props.canRead}
                canPublish={props.canPublish}
                publishState={props.publishState}
                records={props.resolvableConflictRecords}
                batchResolvableCount={props.batchResolvableConflictCount}
                conflictResolutionDraft={props.conflictResolutionDraft}
                conflictCaseKeyword={props.conflictCaseKeyword}
                conflictCaseSearchProjectId={props.conflictCaseSearchProjectId}
                conflictCaseResults={props.conflictCaseResults}
                selectedConflictCaseIds={props.selectedConflictCaseIds}
                conflictCandidateById={props.conflictCandidateById}
                onBatchResolve={props.onRequestBatchResolveConflicts}
                onConflictResolutionDraftChange={props.onConflictResolutionDraftChange}
                onConflictCaseKeywordChange={props.onConflictCaseKeywordChange}
                onSearchConflictCases={props.onSearchConflictCases}
                onSelectedConflictCaseIdsChange={props.onSelectedConflictCaseIdsChange}
                onResolveConflict={props.onResolveConflict}
              />
            )}
            {publishResult.records.length > 0 && (
              <div className="test-design-publish-records">
                {publishResult.records.slice(0, 6).map((record) => (
                  <PublishRecordRow key={publishRecordKey(record)} record={record} />
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}

type ReleaseReadinessSnapshot = {
  status: string;
  blockingCount: number;
  warningCount: number;
};

function ReportArchivePanel(props: {
  canRead: boolean;
  canExport: boolean;
  selectedTaskId: string;
  state: WorkState;
  archives: TestDesignReportArchiveView[];
  selectedArchiveId: string;
  selectedArchive: TestDesignReportArchiveView | null;
  archiveIntegrity: TestDesignReportArchiveIntegrityView | null;
  approvals: TestDesignReportArchiveApprovalView[];
  selectedApprovalId: string;
  selectedApproval: TestDesignReportArchiveApprovalView | null;
  selectedPendingApproval: TestDesignReportArchiveApprovalView | null;
  draft: ReportArchiveApprovalDraft;
  notes: TestDesignReportArchiveNoteView[];
  approvalTypes: readonly string[];
  reasonCodes: readonly string[];
  workOrderStatuses: readonly string[];
  onRefresh: (taskId: string) => void;
  onDraftChange: Dispatch<SetStateAction<ReportArchiveApprovalDraft>>;
  onRequestApproval: (event: FormEvent<HTMLFormElement>) => void;
  onReviewApproval: (approvalId: string, action: 'approve' | 'reject') => void;
  onSelectArchive: (archive: TestDesignReportArchiveView) => void;
  onSelectApproval: (approval: TestDesignReportArchiveApprovalView) => void;
  onAddNote: () => void;
}) {
  return (
    <div className="test-design-release-readiness-panel">
      <div className="test-design-release-readiness-heading">
        <span>报告归档</span>
        <div className="toolbar-actions">
          {props.selectedArchive && (
            <span className={`badge badge-${reportArchiveStatusTone(props.selectedArchive.status)}`}>
              {props.selectedArchive.status}
            </span>
          )}
          <button
            className="btn btn-secondary btn-xs"
            type="button"
            disabled={!props.canRead || props.state.loading || !props.selectedTaskId}
            onClick={() => props.onRefresh(props.selectedTaskId)}
          >
            <RefreshCw size={14} />
            刷新
          </button>
        </div>
      </div>
      <div className="detail-grid">
        <Detail label="归档记录" value={props.archives.length} />
        <Detail label="归档审批" value={props.selectedArchive?.archiveApprovalStatus ?? '-'} />
        <Detail label="外发审批" value={props.selectedArchive?.externalApprovalStatus ?? '-'} />
        <Detail label="完整性索引" value={props.archiveIntegrity ? `${props.archiveIntegrity.indexedRowCount}/${props.archiveIntegrity.reportRowCount}` : '-'} />
      </div>
      <div className="test-design-release-readiness-approvals">
        {props.archives.length ? props.archives.slice(0, 5).map((archive) => (
          <div className={`test-design-release-readiness-approval${props.selectedArchiveId === archive.id ? ' selected' : ''}`} key={archive.id}>
            <div>
              <strong>{archive.storageBackend ?? 'DATABASE'} · {archive.contentSizeBytes} bytes</strong>
              <em>行 {archive.reportRowCount} · 索引 {archive.lineIntegrityCount} · 保留至 {archive.retentionUntil ?? '-'}</em>
              <small>{archive.contentDigest ? `sha256:${archive.contentDigest.slice(0, 12)}` : '-'} · 内容存储 {archive.archiveContentStored ? 'ready' : 'pending'}</small>
            </div>
            <div className="test-design-release-readiness-approval-actions">
              <span className={`badge badge-${reportArchiveStatusTone(archive.status)}`}>{archive.status}</span>
              <button
                className="btn btn-secondary btn-xs"
                type="button"
                disabled={!props.canRead || props.state.loading}
                onClick={() => props.onSelectArchive(archive)}
              >
                <FileText size={14} />
                查看
              </button>
            </div>
          </div>
        )) : (
          <div className="notice info">暂无报告归档记录</div>
        )}
      </div>
      <form className="test-design-release-readiness-form" onSubmit={props.onRequestApproval}>
        <label className="field">
          <span className="field-label">审批类型</span>
          <select
            value={props.draft.approvalType}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, approvalType: event.target.value === 'EXTERNAL_SHARE' ? 'EXTERNAL_SHARE' : 'ARCHIVE' }))}
            disabled={!props.canExport || props.state.loading || !props.selectedArchiveId}
          >
            {props.approvalTypes.map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">申请原因</span>
          <select
            value={props.draft.reasonCode}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, reasonCode: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedArchiveId}
          >
            {props.reasonCodes.map((code) => (
              <option key={code} value={code}>{code}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">工单编号</span>
          <input
            value={props.draft.workOrderKey}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderKey: event.target.value }))}
            placeholder="WP5-ARCH-..."
            disabled={!props.canExport || props.state.loading || !props.selectedArchiveId}
          />
        </label>
        <label className="field">
          <span className="field-label">工单 URL</span>
          <input
            value={props.draft.workOrderUrl}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderUrl: event.target.value }))}
            placeholder="https://..."
            disabled={!props.canExport || props.state.loading || !props.selectedArchiveId}
          />
        </label>
        <label className="field test-design-release-readiness-wide">
          <span className="field-label">申请摘要</span>
          <textarea
            value={props.draft.requestSummary}
            maxLength={1000}
            rows={3}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, requestSummary: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedArchiveId}
          />
        </label>
        <label className="field test-design-release-readiness-wide">
          <span className="field-label">申请备注</span>
          <textarea
            value={props.draft.requestNote}
            maxLength={1000}
            rows={2}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, requestNote: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedArchiveId}
          />
        </label>
        <button
          className="btn btn-secondary btn-sm test-design-release-readiness-submit"
          type="submit"
          disabled={!props.canExport || props.state.loading || !props.selectedArchiveId || !props.draft.requestSummary.trim()}
        >
          <Save size={15} />
          提交审批
        </button>
      </form>
      <div className="test-design-release-readiness-review-grid">
        <label className="field">
          <span className="field-label">审批原因</span>
          <select
            value={props.draft.approvalReasonCode}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, approvalReasonCode: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedPendingApproval}
          >
            {props.reasonCodes.map((code) => (
              <option key={code} value={code}>{code}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">工单状态</span>
          <select
            value={props.draft.workOrderStatus}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderStatus: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedPendingApproval}
          >
            <option value="">跟随审批</option>
            {props.workOrderStatuses.map((status) => (
              <option key={status} value={status}>{status}</option>
            ))}
          </select>
        </label>
        <label className="field test-design-release-readiness-wide">
          <span className="field-label">审批备注</span>
          <textarea
            value={props.draft.reviewNote}
            maxLength={1000}
            rows={2}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, reviewNote: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedPendingApproval}
          />
        </label>
        <div className="toolbar-actions test-design-release-readiness-submit">
          <button
            className="btn btn-secondary btn-sm"
            type="button"
            disabled={!props.canExport || props.state.loading || !props.selectedPendingApproval}
            onClick={() => props.selectedPendingApproval && props.onReviewApproval(props.selectedPendingApproval.id, 'approve')}
          >
            <CheckCircle2 size={15} />
            通过
          </button>
          <button
            className="btn btn-ghost btn-sm"
            type="button"
            disabled={!props.canExport || props.state.loading || !props.selectedPendingApproval}
            onClick={() => props.selectedPendingApproval && props.onReviewApproval(props.selectedPendingApproval.id, 'reject')}
          >
            <XCircle size={15} />
            驳回
          </button>
        </div>
      </div>
      <div className="test-design-release-readiness-approvals">
        {props.approvals.length ? props.approvals.slice(0, 6).map((approval) => (
          <div className={`test-design-release-readiness-approval${props.selectedApprovalId === approval.id ? ' selected' : ''}`} key={approval.id}>
            <div>
              <strong>{approval.workOrderKey ?? approval.id}</strong>
              <em>{approval.approvalType} · {approval.workOrderStatus ?? '-'}</em>
              <small>{approval.requestSummaryDigest ? `sha256:${approval.requestSummaryDigest.slice(0, 12)}` : '-'} · 备注 {approval.noteCount ?? 0}</small>
              {approval.latestNotePreview ? <small>最新备注：{approval.latestNotePreview}</small> : null}
            </div>
            <div className="test-design-release-readiness-approval-actions">
              <span className={`badge badge-${reportArchiveStatusTone(approval.status)}`}>{approval.status}</span>
              <button
                className="btn btn-secondary btn-xs"
                type="button"
                disabled={!props.canRead || props.state.loading}
                onClick={() => props.onSelectApproval(approval)}
              >
                <FileText size={14} />
                流转
              </button>
            </div>
          </div>
        )) : (
          <div className="notice info">暂无归档审批工单</div>
        )}
      </div>
      <div className="test-design-release-readiness-note-form">
        <label className="field">
          <span className="field-label">备注类型</span>
          <select
            value={props.draft.noteType}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, noteType: event.target.value === 'WORK_ORDER' ? 'WORK_ORDER' : 'COMMENT' }))}
            disabled={!props.canExport || props.state.loading || !props.selectedApprovalId}
          >
            <option value="COMMENT">COMMENT</option>
            <option value="WORK_ORDER">WORK_ORDER</option>
          </select>
        </label>
        <label className="field test-design-release-readiness-wide">
          <span className="field-label">流转备注</span>
          <textarea
            value={props.draft.noteText}
            maxLength={1000}
            rows={2}
            onChange={(event) => props.onDraftChange((current) => ({ ...current, noteText: event.target.value }))}
            disabled={!props.canExport || props.state.loading || !props.selectedApprovalId}
          />
        </label>
        <button
          className="btn btn-secondary btn-sm test-design-release-readiness-submit"
          type="button"
          disabled={!props.canExport || props.state.loading || !props.selectedApprovalId || !props.draft.noteText.trim()}
          onClick={props.onAddNote}
        >
          <Plus size={15} />
          追加备注
        </button>
      </div>
      <div className="test-design-release-readiness-notes">
        <strong>备注流转 · {props.selectedApproval?.workOrderKey ?? (props.selectedApprovalId || '-')}</strong>
        {props.selectedApprovalId ? (
          props.notes.length ? props.notes.slice(-6).map((note) => (
            <div className="test-design-release-readiness-note" key={note.id}>
              <span className="badge badge-neutral">{note.noteType}</span>
              <em>{note.noteText}</em>
              <small>{note.createdBy ?? '-'} · {note.createdAt ?? '-'}</small>
            </div>
          )) : (
            <div className="notice info">暂无归档备注</div>
          )
        ) : (
          <div className="notice info">未选择归档审批工单</div>
        )}
      </div>
      <StateLine state={props.state} />
    </div>
  );
}

function PublishConflictPanel(props: {
  canRead: boolean;
  canPublish: boolean;
  publishState: WorkState;
  records: TestDesignPublishRecordView[];
  batchResolvableCount: number;
  conflictResolutionDraft: ConflictResolutionDraft;
  conflictCaseKeyword: string;
  conflictCaseSearchProjectId: string;
  conflictCaseResults: AssetTestCaseView[];
  selectedConflictCaseIds: Record<string, string>;
  conflictCandidateById: Map<string, TestDesignCandidateView>;
  onBatchResolve: () => void;
  onConflictResolutionDraftChange: Dispatch<SetStateAction<ConflictResolutionDraft>>;
  onConflictCaseKeywordChange: Dispatch<SetStateAction<string>>;
  onSearchConflictCases: () => void;
  onSelectedConflictCaseIdsChange: Dispatch<SetStateAction<Record<string, string>>>;
  onResolveConflict: (record: TestDesignPublishRecordView) => void;
}) {
  return (
    <div className="test-design-conflict-panel">
      <div className="test-design-conflict-heading">
        <span>冲突处理 {props.records.length} 条</span>
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
          <span className="badge badge-warning">需人工确认</span>
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
      <div className="test-design-conflict-list">
        {props.records.map((record) => {
          const candidate = conflictResolutionCandidate(record, props.conflictCandidateById);
          const targetCaseId = conflictResolutionTargetCaseId(record, props.selectedConflictCaseIds);
          return (
            <div className="test-design-conflict-row" key={publishRecordKey(record)}>
              <span>
                <strong>{record.title ?? record.candidateId ?? '-'}</strong>
                <em>{targetCaseId ? `目标用例 ${targetCaseId}` : '目标用例 -'}</em>
                {candidate && <em>候选 {candidate.status}@v{candidate.version}</em>}
                {record.errorMessage && <small>{record.errorMessage}</small>}
                {!candidate && <small>发布记录缺少候选版本，重新预发布后可处理。</small>}
              </span>
              <div className="test-design-conflict-controls">
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
                  disabled={!props.canPublish || props.publishState.loading}
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
                  disabled={!props.canPublish || props.publishState.loading || !candidate || !targetCaseId}
                  onClick={() => props.onResolveConflict(record)}
                >
                  <Link2 size={14} />
                  复用
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
