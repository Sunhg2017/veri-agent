import { FileDiff, Link2 } from 'lucide-react';
import type {
  TestDesignCandidateBatchActionResult,
  TestDesignPublishRecordView,
  TestDesignReviewRecordView
} from '../api/testDesign';
import type { TestDesignConfirmationSummary } from '../testDesignConfirmation';
import { testDesignBatchActionLabel } from '../testDesignConfirmation';
import type { TestDesignGenerationSource } from '../testDesignGenerationSource';
import { generationSourceText } from '../testDesignGenerationSource';
import type { TestDesignCandidateDraftQualityIssue } from '../testDesignQuality';
import { translate } from '../platform/i18n';

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

export function Detail(props: { label: string; value: string | number }) {
  return (
    <div className="detail-row">
      <span className="detail-label">{props.label}</span>
      <span className="detail-value">{props.value}</span>
    </div>
  );
}

export function CandidateStatus(props: { value: string }) {
  const value = props.value;
  const className = value === 'CONFIRMED' || value === 'PUBLISHED'
    ? 'badge badge-success'
    : value === 'REJECTED' || value === 'FAILED'
      ? 'badge badge-danger'
      : value === 'IGNORED'
        ? 'badge badge-neutral'
        : value === 'PUBLISH_QUEUED' || value === 'PUBLISHING'
          ? 'badge badge-warning'
          : 'badge badge-warning';
  return <span className={className}>{value}</span>;
}

export function GenerationSourceBadge(props: { source: TestDesignGenerationSource; compact?: boolean }) {
  const toneClass = props.source.tone === 'success'
    ? 'badge-success'
    : props.source.tone === 'warning'
      ? 'badge-warning'
      : 'badge-neutral';
  return (
    <span
      className={`badge test-design-source-badge ${toneClass}${props.compact ? ' compact' : ''}`}
      title={generationSourceText(props.source)}
    >
      {props.source.label}
    </span>
  );
}

export function contextPolicyOverrideLimitText(limits: Record<string, number>) {
  const labels: Record<string, string> = {
    linkedAssetsPerRequirement: translate('auto.k0431'),
    explicitAssetsPerType: translate('auto.k1393'),
    existingCasesPerRequirement: translate('auto.k1394'),
    requirementDescriptionChars: translate('auto.k1395'),
    acceptanceCriteriaChars: translate('auto.k1396'),
    linkedAssetSchemaChars: translate('auto.k1397')
  };
  const parts = Object.entries(limits).map(([key, value]) => `${labels[key] ?? key} ${value}`);
  return parts.length ? parts.join(' · ') : '-';
}

export function contextPolicyStatusTone(status: string) {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'PENDING') return 'warning';
  return 'neutral';
}

export function contextPolicyDigestText(digest?: string) {
  return digest ? `sha256:${digest.slice(0, 12)}` : translate('auto.k1830');
}

export function releaseReadinessStatusTone(status?: string) {
  if (status === 'APPROVED' || status === 'READY') return 'success';
  if (status === 'REJECTED' || status === 'BLOCKED') return 'danger';
  if (status === 'PENDING' || status === 'WARNING') return 'warning';
  return 'neutral';
}

export function reportArchiveStatusTone(status?: string) {
  if (status === 'ARCHIVED' || status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'PENDING' || status === 'PENDING_APPROVAL') return 'warning';
  return 'neutral';
}

export function sampleStatusTone(status?: string) {
  if (status === 'GOLDEN') return 'success';
  if (status === 'FROZEN') return 'warning';
  if (status === 'DEPRECATED') return 'danger';
  if (status === 'CANDIDATE') return 'info';
  return 'neutral';
}

export function calibrationStatusTone(status?: string) {
  if (status === 'PASSED' || status === 'READY') return 'success';
  if (status === 'BLOCKED' || status === 'FAILED') return 'danger';
  if (status === 'WARNING' || status === 'RUNNING') return 'warning';
  if (status === 'PENDING') return 'info';
  return 'neutral';
}

export function releaseReadinessDigestText(digest?: string) {
  return digest ? `sha256:${digest.slice(0, 12)}` : '-';
}

export function PublishRecordRow(props: { record: TestDesignPublishRecordView }) {
  const assetCaseHref = props.record.assetCaseId ? assetCaseTraceHref(props.record.assetCaseId) : '';
  return (
    <div className="test-design-publish-record">
      <span>
        <strong>{props.record.title ?? props.record.candidateId ?? '-'}</strong>
        {assetCaseHref ? (
          <a className="test-design-asset-link" href={assetCaseHref}>
            <Link2 size={13} />
            {props.record.action} · {props.record.assetCaseId}
          </a>
        ) : (
          <em>{props.record.action} · {props.record.requirementId ?? '-'}</em>
        )}
        {props.record.errorMessage && <small>{props.record.errorMessage}</small>}
      </span>
      <PublishResultBadge value={props.record.result} />
    </div>
  );
}

export function PublishResultBadge(props: { value: string }) {
  const value = props.value;
  const className = value === 'SUCCEEDED' || value === 'PLANNED' || value === 'READY'
    ? 'badge badge-success'
    : value === 'CONFLICT' || value === 'FAILED' || value === 'DUPLICATE_REVIEW_REQUIRED'
      ? 'badge badge-danger'
      : value === 'SKIPPED' || value === 'LINK_EXISTING'
        ? 'badge badge-neutral'
        : value === 'QUEUED' || value === 'RUNNING'
          ? 'badge badge-warning'
          : 'badge badge-warning';
  return <span className={className}>{value}</span>;
}

export function ReviewRecordRow(props: { record: TestDesignReviewRecordView }) {
  const statusChange = [props.record.beforeStatus, props.record.afterStatus].filter(Boolean).join(' -> ') || '-';
  const versionChange = props.record.versionBefore !== undefined || props.record.versionAfter !== undefined
    ? `${props.record.versionBefore ?? '-'} -> ${props.record.versionAfter ?? '-'}`
    : '-';
  return (
    <div className="test-design-review-record">
      <div>
        <strong>{props.record.title ?? props.record.candidateId ?? '-'}</strong>
        <em>{props.record.action} · {statusChange} · v{versionChange}</em>
        <small>{props.record.changedFields.length ? props.record.changedFields.join(', ') : translate('auto.k1831')}</small>
        {props.record.hasComment && <small>{props.record.commentPreview ?? translate('auto.k1832')}</small>}
        {props.record.diffItems.length > 0 && (
          <div className="test-design-diff-items">
            {props.record.diffItems.slice(0, 8).map((item) => (
              <div className="test-design-diff-item" key={`${props.record.id}-${item.field}`}>
                <span>
                  <FileDiff size={13} />
                  {item.field}
                </span>
                <del>{item.before || '-'}</del>
                <ins>{item.after || '-'}</ins>
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="test-design-review-record-meta">
        <span>{props.record.reviewer ?? '-'}</span>
        <time>{props.record.createdAt ?? '-'}</time>
      </div>
    </div>
  );
}

export function BatchActionSummary(props: { result: TestDesignCandidateBatchActionResult }) {
  const failedItems = props.result.items.filter((item) => item.result !== 'SUCCEEDED');
  return (
    <div className={failedItems.length ? 'notice warning test-design-batch-summary' : 'notice success test-design-batch-summary'}>
      <strong>{testDesignBatchActionLabel(props.result.action)}{translate('auto.k0365')}</strong>
      <span>{translate('auto.k0368')}{props.result.succeededCount} / {props.result.total}{translate('auto.k1833')}{props.result.failedCount}</span>
      {failedItems.length > 0 && (
        <div className="test-design-batch-failures">
          {failedItems.slice(0, 4).map((item) => (
            <span key={`${item.candidateId}-${item.errorCode ?? ''}`}>
              {item.candidateId}：{item.errorMessage ?? item.errorCode ?? item.result}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export function BatchEditSummary(props: { result: BatchEditSummaryResult }) {
  const failedItems = props.result.items.filter((item) => item.result !== 'SUCCEEDED');
  return (
    <div className={failedItems.length ? 'notice warning test-design-batch-summary' : 'notice success test-design-batch-summary'}>
      <strong>{translate('auto.k1834')}</strong>
      <span>{translate('auto.k0368')}{props.result.succeededCount} / {props.result.total}{translate('auto.k1833')}{props.result.failedCount}</span>
      {failedItems.length > 0 && (
        <div className="test-design-batch-failures">
          {failedItems.slice(0, 4).map((item) => (
            <span key={`${item.candidateId}-${item.errorMessage ?? ''}`}>
              {item.candidateId}：{item.errorMessage ?? item.result}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export function ConfirmationDialog(props: {
  summary: TestDesignConfirmationSummary;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="modal-backdrop" onClick={props.onCancel}>
      <div
        aria-labelledby="test-design-confirmation-title"
        aria-modal="true"
        className="modal-panel test-design-confirmation-modal"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-heading">
          <div>
            <h2 id="test-design-confirmation-title">{props.summary.title}</h2>
            <p className="panel-desc">{translate('auto.k1835')}</p>
          </div>
        </div>
        <div className="modal-body">
          <div className="detail-grid">
            {props.summary.details.map((detail) => (
              <Detail key={detail.label} label={detail.label} value={detail.value} />
            ))}
          </div>
          <div className={props.summary.tone === 'warning' ? 'notice warning' : 'notice info'}>
            {props.summary.warnings.map((warning) => (
              <span key={warning}>{warning}</span>
            ))}
          </div>
          {props.summary.candidateTitles.length > 0 && (
            <div className="test-design-confirmation-candidates">
              <strong>{translate('auto.k1836')}</strong>
              <ul>
                {props.summary.candidateTitles.map((title, index) => (
                  <li key={`${title}-${index}`}>{title}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary btn-sm" type="button" onClick={props.onCancel}>
            {translate('auto.k0220')}</button>
          <button className="btn btn-primary btn-sm" type="button" onClick={props.onConfirm}>
            {props.summary.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

export function QualityFieldMessages(props: {
  field: TestDesignCandidateDraftQualityIssue['field'];
  issues: TestDesignCandidateDraftQualityIssue[];
}) {
  const fieldIssues = props.issues.filter((issue) => issue.field === props.field);
  if (!fieldIssues.length) {
    return null;
  }
  return (
    <>
      {fieldIssues.map((issue, index) => (
        <span className="field-error" key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</span>
      ))}
    </>
  );
}

export function publishRecordKey(record: TestDesignPublishRecordView) {
  return [
    record.id,
    record.candidateId,
    record.action,
    record.result,
    record.assetCaseId,
    record.createdAt
  ].filter(Boolean).join('-') || `${record.action}-${record.result}`;
}

export function assetCaseTraceHref(assetCaseId: string) {
  return `#asset-library/trace/case/${encodeURIComponent(assetCaseId)}`;
}

export function shortIdentifier(value: string) {
  if (value.length <= 14) {
    return value;
  }
  return `${value.slice(0, 8)}...${value.slice(-4)}`;
}

export function reviewSuccessText(action: 'confirm' | 'reject' | 'ignore') {
  if (action === 'confirm') {
    return translate('auto.k1837');
  }
  if (action === 'reject') {
    return translate('auto.k1838');
  }
  return translate('auto.k1839');
}

export function emptyRequirementText(signedIn: boolean, canRead: boolean, loading: boolean) {
  if (!signedIn) {
    return translate('auto.k0454');
  }
  if (!canRead) {
    return translate('auto.k1794');
  }
  return loading ? translate('auto.k0168') : translate('auto.k1840');
}
