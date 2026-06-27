import {
  CheckCircle2,
  FileText,
  Plus,
  RefreshCw,
  Save,
  XCircle
} from 'lucide-react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import type {
  TestDesignContextPolicyNoteView,
  TestDesignContextPolicyOverrideView
} from '../api/testDesign';
import {
  TEST_DESIGN_CONTEXT_POLICY_REASON_CODES,
  TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES,
  type TestDesignContextPolicyDraft,
  type TestDesignContextPolicySummary
} from '../testDesignContextPolicy';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import {
  Detail,
  contextPolicyDigestText,
  contextPolicyOverrideLimitText,
  contextPolicyStatusTone
} from './TestDesignWorkbenchShared';
import { translate } from '../platform/i18n';

export function TestDesignContextPolicyPanel(props: {
  disabled: boolean;
  canRead: boolean;
  canPolicyManage: boolean;
  state: WorkState;
  summary: TestDesignContextPolicySummary;
  draft: TestDesignContextPolicyDraft;
  submitBlocked: boolean;
  overrides: TestDesignContextPolicyOverrideView[];
  selectedOverrideId: string;
  selectedOverride: TestDesignContextPolicyOverrideView | null;
  selectedPendingOverride: TestDesignContextPolicyOverrideView | null;
  notes: TestDesignContextPolicyNoteView[];
  onRefresh: () => void;
  onNewDraft: () => void;
  onDraftChange: Dispatch<SetStateAction<TestDesignContextPolicyDraft>>;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onSelectOverride: (override: TestDesignContextPolicyOverrideView) => void;
  onReviewOverride: (overrideId: string, action: 'approve' | 'reject') => void;
  onAddNote: () => void;
}) {
  const draft = props.draft;
  const state = props.state;

  return (
    <section className="panel test-design-context-policy-panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1388')}</h2>
          <p className="panel-desc">{props.summary.scopeLabel}</p>
        </div>
        <div className="toolbar-actions">
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.disabled || state.loading} onClick={props.onRefresh}>
            <RefreshCw size={15} />
            {translate('auto.k0372')}</button>
          <button className="btn btn-ghost btn-sm" type="button" disabled={!props.canPolicyManage || state.loading} onClick={props.onNewDraft}>
            <Plus size={15} />
            {translate('auto.k0489')}</button>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        <form className="test-design-context-policy-form" onSubmit={props.onSubmit}>
          <label className="field">
            <span className="field-label">{translate('auto.k1389')}</span>
            <input
              value={draft.projectId}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="project UUID"
              disabled={!props.canRead || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1390')}</span>
            <input
              value={draft.environmentKey}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, environmentKey: event.target.value }))}
              placeholder="qa"
              disabled={!props.canRead || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1391')}</span>
            <select
              value={draft.scopeType}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, scopeType: event.target.value === 'ENVIRONMENT' ? 'ENVIRONMENT' : 'PROJECT' }))}
              disabled={!props.canPolicyManage || state.loading}
            >
              <option value="PROJECT">PROJECT</option>
              <option value="ENVIRONMENT">ENVIRONMENT</option>
            </select>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1392')}</span>
            <select
              value={draft.changeReasonCode}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, changeReasonCode: event.target.value as TestDesignContextPolicyDraft['changeReasonCode'] }))}
              disabled={!props.canPolicyManage || state.loading}
            >
              {TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.map((code) => (
                <option key={code} value={code}>{code}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k0431')}</span>
            <input
              value={draft.linkedAssetsPerRequirement}
              type="number"
              min="1"
              max="50"
              onChange={(event) => props.onDraftChange((current) => ({ ...current, linkedAssetsPerRequirement: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1393')}</span>
            <input
              value={draft.explicitAssetsPerType}
              type="number"
              min="1"
              max="50"
              onChange={(event) => props.onDraftChange((current) => ({ ...current, explicitAssetsPerType: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1394')}</span>
            <input
              value={draft.existingCasesPerRequirement}
              type="number"
              min="1"
              max="50"
              onChange={(event) => props.onDraftChange((current) => ({ ...current, existingCasesPerRequirement: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1395')}</span>
            <input
              value={draft.requirementDescriptionChars}
              type="number"
              min="1"
              max="2000"
              onChange={(event) => props.onDraftChange((current) => ({ ...current, requirementDescriptionChars: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1396')}</span>
            <input
              value={draft.acceptanceCriteriaChars}
              type="number"
              min="1"
              max="2000"
              onChange={(event) => props.onDraftChange((current) => ({ ...current, acceptanceCriteriaChars: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1397')}</span>
            <input
              value={draft.assetSchemaChars}
              type="number"
              min="1"
              max="2000"
              onChange={(event) => props.onDraftChange((current) => ({ ...current, assetSchemaChars: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1398')}</span>
            <input
              value={draft.workOrderKey}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderKey: event.target.value }))}
              placeholder="WP5-CTX-..."
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1399')}</span>
            <input
              value={draft.workOrderTitle}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderTitle: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field test-design-context-policy-wide">
            <span className="field-label">{translate('auto.k1400')}</span>
            <input
              value={draft.workOrderUrl}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderUrl: event.target.value }))}
              placeholder="https://..."
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field test-design-context-policy-wide">
            <span className="field-label">{translate('auto.k1401')}</span>
            <textarea
              value={draft.policyBody}
              maxLength={4000}
              rows={4}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, policyBody: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field test-design-context-policy-wide">
            <span className="field-label">{translate('auto.k1402')}</span>
            <textarea
              value={draft.policyDiffSummary}
              maxLength={1000}
              rows={3}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, policyDiffSummary: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <label className="field test-design-context-policy-wide">
            <span className="field-label">{translate('auto.k1403')}</span>
            <textarea
              value={draft.requestNote}
              maxLength={1000}
              rows={3}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, requestNote: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
          <button className="btn btn-primary btn-sm test-design-context-policy-submit" type="submit" disabled={!props.canPolicyManage || state.loading || props.submitBlocked}>
            <Save size={15} />
            {props.selectedPendingOverride ? translate('auto.k1404') : translate('auto.k1405')}
          </button>
        </form>
        <div className="test-design-context-policy-summary">
          <Detail label={translate('auto.k1406')} value={props.summary.limitSummary} />
          <Detail label={translate('auto.k1407')} value={props.summary.statusSummary} />
          <Detail label={translate('auto.k1408')} value={props.summary.redLineSummary} />
        </div>
        <div className="test-design-context-policy-review-grid">
          <label className="field">
            <span className="field-label">{translate('auto.k1409')}</span>
            <select
              value={draft.approvalReasonCode}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, approvalReasonCode: event.target.value as TestDesignContextPolicyDraft['approvalReasonCode'] }))}
              disabled={!props.canPolicyManage || state.loading}
            >
              {TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.map((code) => (
                <option key={code} value={code}>{code}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1410')}</span>
            <select
              value={draft.workOrderStatus}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, workOrderStatus: event.target.value as TestDesignContextPolicyDraft['workOrderStatus'] }))}
              disabled={!props.canPolicyManage || state.loading}
            >
              <option value="">{translate('auto.k1411')}</option>
              {TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES.map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
          </label>
          <label className="field test-design-context-policy-wide">
            <span className="field-label">{translate('auto.k1412')}</span>
            <textarea
              value={draft.reviewNote}
              maxLength={1000}
              rows={3}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, reviewNote: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading}
            />
          </label>
        </div>
        <div className="test-design-context-policy-note-form">
          <label className="field">
            <span className="field-label">{translate('auto.k1413')}</span>
            <select
              value={draft.noteType}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, noteType: event.target.value === 'WORK_ORDER' ? 'WORK_ORDER' : 'COMMENT' }))}
              disabled={!props.canPolicyManage || state.loading || !props.selectedOverrideId}
            >
              <option value="COMMENT">COMMENT</option>
              <option value="WORK_ORDER">WORK_ORDER</option>
            </select>
          </label>
          <label className="field test-design-context-policy-wide">
            <span className="field-label">{translate('auto.k1414')}</span>
            <textarea
              value={draft.noteText}
              maxLength={1000}
              rows={3}
              onChange={(event) => props.onDraftChange((current) => ({ ...current, noteText: event.target.value }))}
              disabled={!props.canPolicyManage || state.loading || !props.selectedOverrideId}
            />
          </label>
          <button
            className="btn btn-secondary btn-sm test-design-context-policy-submit"
            type="button"
            disabled={!props.canPolicyManage || state.loading || !props.selectedOverrideId || !draft.noteText.trim()}
            onClick={props.onAddNote}
          >
            <Plus size={15} />
            {translate('auto.k1415')}</button>
        </div>
        <div className="test-design-context-policy-overrides">
          {props.overrides.length ? props.overrides.slice(0, 6).map((override) => (
            <div className={`test-design-context-policy-override${props.selectedOverrideId === override.id ? ' selected' : ''}`} key={override.id}>
              <div>
                <strong>{override.scopeType}{override.environmentKey ? ` · ${override.environmentKey}` : ''}</strong>
                <em>{contextPolicyOverrideLimitText(override.overrideLimits)}</em>
                <small>{override.workOrderKey ?? '-'} · {override.workOrderStatus ?? '-'}</small>
                <small>{translate('auto.k1416')}{override.policyBodyVersion ?? '-'} · {contextPolicyDigestText(override.policyBodyDigest)} {translate('auto.k1417')}{override.noteCount ?? 0}</small>
                <small>{override.requestedBy ?? '-'} · {override.createdAt ?? '-'}</small>
                {override.latestNotePreview ? <small>{translate('auto.k1418')}{override.latestNotePreview}</small> : null}
              </div>
              <div className="test-design-context-policy-override-actions">
                <span className={`badge badge-${contextPolicyStatusTone(override.status)}`}>{override.status}</span>
                <button
                  className="btn btn-secondary btn-xs"
                  type="button"
                  disabled={!props.canPolicyManage || state.loading}
                  onClick={() => props.onSelectOverride(override)}
                >
                  <FileText size={14} />
                  {override.status === 'PENDING' ? translate('auto.k0746') : translate('auto.k1419')}
                </button>
                {override.status === 'PENDING' && (
                  <>
                    <button
                      className="btn btn-secondary btn-xs"
                      type="button"
                      disabled={!props.canPolicyManage || state.loading}
                      onClick={() => props.onReviewOverride(override.id, 'approve')}
                    >
                      <CheckCircle2 size={14} />
                      {translate('auto.k1022')}</button>
                    <button
                      className="btn btn-ghost btn-xs"
                      type="button"
                      disabled={!props.canPolicyManage || state.loading}
                      onClick={() => props.onReviewOverride(override.id, 'reject')}
                    >
                      <XCircle size={14} />
                      {translate('auto.k0214')}</button>
                  </>
                )}
              </div>
            </div>
          )) : (
            <div className="notice info">{translate('auto.k1420')}</div>
          )}
        </div>
        <div className="test-design-context-policy-notes">
          <strong>{translate('auto.k1421')}{props.selectedOverride?.workOrderKey ?? (props.selectedOverrideId || '-')}</strong>
          {props.selectedOverrideId ? (
            props.notes.length ? props.notes.slice(-6).map((note) => (
              <div className="test-design-context-policy-note" key={note.id}>
                <span className="badge badge-neutral">{note.noteType}</span>
                <em>{note.noteText}</em>
                <small>{note.createdBy ?? '-'} · {note.createdAt ?? '-'}</small>
              </div>
            )) : (
              <div className="notice info">{translate('auto.k1422')}</div>
            )
          ) : (
            <div className="notice info">{translate('auto.k1423')}</div>
          )}
        </div>
        <StateLine state={state} />
      </div>
    </section>
  );
}
