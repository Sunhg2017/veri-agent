import {
  CheckCircle2,
  ClipboardCheck,
  Plus,
  RefreshCw,
  Repeat2,
  Save,
  Sparkles,
  XCircle
} from 'lucide-react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import {
  TEST_DESIGN_COVERAGE_TYPES,
  type TestDesignCalibrationRunView,
  type TestDesignCalibrationSummaryView,
  type TestDesignEvaluationCorpusSummaryView,
  type TestDesignEvaluationSampleSummaryView,
  type TestDesignEvaluationSampleView
} from '../api/testDesign';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import {
  calibrationStatusTone,
  sampleStatusTone,
  shortIdentifier
} from './TestDesignWorkbenchShared';
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { NativeSelect } from './ui';

export type EvaluationSampleFilters = {
  projectId: string;
  promptKey: string;
  promptVersion: string;
  status: string;
  coverageType: string;
  baselineVersion: string;
  keyword: string;
};

export type EvaluationSampleDraft = {
  projectId: string;
  sampleKey: string;
  title: string;
  sourceType: string;
  promptKey: string;
  promptVersion: string;
  coverageType: string;
  priority: string;
  status: string;
  baselineVersion: string;
  requirementSummary: string;
  expectedCaseOutline: string;
  assertionNotes: string;
  tags: string;
  maintenanceNote: string;
};

export type CalibrationRunDraft = {
  projectId: string;
  promptKey: string;
  promptVersion: string;
  baselineVersion: string;
  runMode: string;
  notes: string;
};

const evaluationSampleStatuses = ['CANDIDATE', 'GOLDEN', 'FROZEN', 'DEPRECATED'] as const;
const evaluationSampleSourceTypes = ['MANUAL', 'REVIEW_FEEDBACK', 'PUBLISHED_CASE', 'IMPORTED'] as const;
const calibrationRunModes = ['MANUAL', 'PROMPT_CHANGE', 'SCHEDULED', 'BASELINE_FREEZE'] as const;

export function EvaluationCorpusOperationsPanel(props: {
  state: WorkState;
  canPolicyManage: boolean;
  samples: TestDesignEvaluationSampleView[];
  sampleSummary: TestDesignEvaluationSampleSummaryView | null;
  evaluationSummary: TestDesignEvaluationCorpusSummaryView | null;
  sampleTotal: number;
  selectedSampleId: string;
  sampleDraft: EvaluationSampleDraft;
  calibrationDraft: CalibrationRunDraft;
  calibrationRuns: TestDesignCalibrationRunView[];
  calibrationSummary: TestDesignCalibrationSummaryView | null;
  filters: EvaluationSampleFilters;
  selectedCandidateId: string;
  onRefresh: () => void;
  onSelectSample: (sampleId: string) => void;
  onNewSample: () => void;
  onSampleDraftChange: Dispatch<SetStateAction<EvaluationSampleDraft>>;
  onCalibrationDraftChange: Dispatch<SetStateAction<CalibrationRunDraft>>;
  onFiltersChange: Dispatch<SetStateAction<EvaluationSampleFilters>>;
  onSaveSample: (event: FormEvent<HTMLFormElement>) => void;
  onTransitionSample: (status: string) => void;
  onExtractFromCandidate: () => void;
  onRunCalibration: () => void;
}) {
  const selectedSample = props.samples.find((sample) => sample.id === props.selectedSampleId) ?? null;
  const latestCalibrationStatus = props.calibrationSummary?.latestStatus
    ?? props.evaluationSummary?.latestCalibrationStatus
    ?? '-';
  const canMutate = props.canPolicyManage && !props.state.loading;

  return (
    <section className="panel test-design-evaluation-corpus">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k1500')}</h2>
          <p className="panel-desc">
            {props.evaluationSummary?.projectId || props.filters.projectId || translate('auto.k1501')}
            {' · '}
            {props.evaluationSummary?.promptKey || props.filters.promptKey || translate('auto.k1502')}
          </p>
        </div>
        <div className="toolbar-actions">
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.state.loading} onClick={props.onRefresh}>
            <RefreshCw size={15} />
            {translate('auto.k0170')}</button>
          <button className="btn btn-secondary btn-sm" type="button" disabled={!props.canPolicyManage} onClick={props.onNewSample}>
            <Plus size={15} />
            {translate('auto.k1503')}</button>
        </div>
      </div>
      <div className="panel-body compact main-stack">
        <StateLine state={props.state} />
        <div className="test-design-quality-metrics test-design-evaluation-metrics">
          <div className="test-design-quality-metric tone-info">
            <span>{translate('auto.k1504')}</span>
            <strong>{props.sampleSummary?.totalCount ?? props.evaluationSummary?.maintainedSampleCount ?? 0}</strong>
            <small>{translate('auto.k1505')}{props.sampleTotal}</small>
          </div>
          <div className="test-design-quality-metric tone-success">
            <span>Golden</span>
            <strong>{props.sampleSummary?.goldenCount ?? props.evaluationSummary?.goldenSampleCount ?? 0}</strong>
            <small>{translate('auto.k1506')}{props.sampleSummary?.baselineVersionCount ?? props.evaluationSummary?.baselineVersionCount ?? 0}</small>
          </div>
          <div className="test-design-quality-metric tone-warning">
            <span>{translate('auto.k1507')}</span>
            <strong>{(props.sampleSummary?.frozenCount ?? props.evaluationSummary?.frozenSampleCount ?? 0)
              + (props.sampleSummary?.deprecatedCount ?? props.evaluationSummary?.deprecatedSampleCount ?? 0)}</strong>
            <small>{translate('auto.k1508')}{props.sampleSummary?.frozenCount ?? props.evaluationSummary?.frozenSampleCount ?? 0}</small>
          </div>
          <div className={`test-design-quality-metric tone-${calibrationStatusTone(latestCalibrationStatus)}`}>
            <span>{translate('auto.k1509')}</span>
            <strong>{props.calibrationSummary?.totalRunCount ?? props.evaluationSummary?.calibrationRunCount ?? 0}</strong>
            <small>{latestCalibrationStatus}</small>
          </div>
        </div>

        <div className="form-grid test-design-evaluation-filter">
          <label className="field">
            <span className="field-label">{translate('auto.k1389')}</span>
            <input
              value={props.filters.projectId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="project UUID"
            />
          </label>
          <label className="field">
            <span className="field-label">Prompt</span>
            <input
              value={props.filters.promptKey}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, promptKey: event.target.value }))}
              placeholder="prompt key"
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k0178')}</span>
            <input
              value={props.filters.promptVersion}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, promptVersion: event.target.value }))}
              placeholder="prompt version"
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k0182')}</span>
            <NativeSelect
              value={props.filters.status}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, status: event.target.value }))}
            >
              <option value="">{translate('auto.k0195')}</option>
              {evaluationSampleStatuses.map((status) => (
                <option key={status} value={status}>{dictionaryLabel(status)}</option>
              ))}
            </NativeSelect>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1315')}</span>
            <NativeSelect
              value={props.filters.coverageType}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, coverageType: event.target.value }))}
            >
              <option value="">{translate('auto.k0195')}</option>
              {TEST_DESIGN_COVERAGE_TYPES.map((coverageType) => (
                <option key={coverageType} value={coverageType}>{dictionaryLabel(coverageType)}</option>
              ))}
            </NativeSelect>
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1506')}</span>
            <input
              value={props.filters.baselineVersion}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, baselineVersion: event.target.value }))}
              placeholder="baseline"
            />
          </label>
          <label className="field">
            <span className="field-label">{translate('auto.k1316')}</span>
            <input
              value={props.filters.keyword}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, keyword: event.target.value }))}
              placeholder={translate('auto.k1510')}
            />
          </label>
        </div>

        <div className="test-design-evaluation-grid">
          <form className="test-design-evaluation-form" onSubmit={props.onSaveSample}>
            <div className="test-design-evaluation-form-heading">
              <strong>{selectedSample ? translate('auto.k1511') : translate('auto.k1503')}</strong>
              {selectedSample && (
                <span className={`badge badge-${sampleStatusTone(selectedSample.status)}`} title={selectedSample.status}>{dictionaryLabel(selectedSample.status)}</span>
              )}
            </div>
            <div className="form-grid">
              <label className="field">
                <span className="field-label">{translate('auto.k1512')}</span>
                <input
                  value={props.sampleDraft.sampleKey}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, sampleKey: event.target.value }))}
                  placeholder={translate('auto.k1513')}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0440')}</span>
                <input
                  value={props.sampleDraft.title}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, title: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0179')}</span>
                <NativeSelect
                  value={props.sampleDraft.sourceType}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, sourceType: event.target.value }))}
                  disabled={!props.canPolicyManage}
                >
                  {evaluationSampleSourceTypes.map((sourceType) => (
                    <option key={sourceType} value={sourceType}>{dictionaryLabel(sourceType)}</option>
                  ))}
                </NativeSelect>
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1315')}</span>
                <NativeSelect
                  value={props.sampleDraft.coverageType}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, coverageType: event.target.value }))}
                  disabled={!props.canPolicyManage}
                >
                  {TEST_DESIGN_COVERAGE_TYPES.map((coverageType) => (
                    <option key={coverageType} value={coverageType}>{dictionaryLabel(coverageType)}</option>
                  ))}
                </NativeSelect>
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0419')}</span>
                <NativeSelect
                  value={props.sampleDraft.priority}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, priority: event.target.value }))}
                  disabled={!props.canPolicyManage}
                >
                  <option value="HIGH">{dictionaryLabel('HIGH')}</option>
                  <option value="MEDIUM">{dictionaryLabel('MEDIUM')}</option>
                  <option value="LOW">{dictionaryLabel('LOW')}</option>
                </NativeSelect>
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0182')}</span>
                <NativeSelect
                  value={props.sampleDraft.status}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, status: event.target.value }))}
                  disabled={!props.canPolicyManage}
                >
                  {evaluationSampleStatuses.map((status) => (
                    <option key={status} value={status}>{dictionaryLabel(status)}</option>
                  ))}
                </NativeSelect>
              </label>
              <label className="field">
                <span className="field-label">Prompt</span>
                <input
                  value={props.sampleDraft.promptKey}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, promptKey: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1514')}</span>
                <input
                  value={props.sampleDraft.promptVersion}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, promptVersion: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1515')}</span>
                <input
                  value={props.sampleDraft.baselineVersion}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, baselineVersion: event.target.value }))}
                  placeholder="baseline-v1"
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k0803')}</span>
                <input
                  value={props.sampleDraft.tags}
                  onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, tags: event.target.value }))}
                  placeholder={translate('auto.k1516')}
                  disabled={!props.canPolicyManage}
                />
              </label>
            </div>
            <label className="field">
              <span className="field-label">{translate('auto.k1395')}</span>
              <textarea
                value={props.sampleDraft.requirementSummary}
                onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, requirementSummary: event.target.value }))}
                rows={3}
                disabled={!props.canPolicyManage}
              />
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k1517')}</span>
              <textarea
                value={props.sampleDraft.expectedCaseOutline}
                onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, expectedCaseOutline: event.target.value }))}
                rows={4}
                disabled={!props.canPolicyManage}
              />
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k1518')}</span>
              <textarea
                value={props.sampleDraft.assertionNotes}
                onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, assertionNotes: event.target.value }))}
                rows={2}
                disabled={!props.canPolicyManage}
              />
            </label>
            <label className="field">
              <span className="field-label">{translate('auto.k1519')}</span>
              <textarea
                value={props.sampleDraft.maintenanceNote}
                onChange={(event) => props.onSampleDraftChange((current) => ({ ...current, maintenanceNote: event.target.value }))}
                rows={2}
                disabled={!props.canPolicyManage}
              />
            </label>
            <div className="toolbar-actions test-design-evaluation-actions">
              <button className="btn btn-primary btn-sm" type="submit" disabled={!canMutate}>
                <Save size={15} />
                {translate('auto.k1520')}</button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!canMutate || !props.selectedCandidateId} onClick={props.onExtractFromCandidate}>
                <ClipboardCheck size={15} />
                {translate('auto.k1521')}</button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!canMutate || !selectedSample} onClick={() => props.onTransitionSample('GOLDEN')}>
                <CheckCircle2 size={15} />
                {translate('auto.k1522')}</button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!canMutate || !selectedSample} onClick={() => props.onTransitionSample('FROZEN')}>
                <Repeat2 size={15} />
                {translate('auto.k1508')}</button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={!canMutate || !selectedSample} onClick={() => props.onTransitionSample('DEPRECATED')}>
                <XCircle size={15} />
                {translate('auto.k0621')}</button>
            </div>
          </form>

          <div className="test-design-evaluation-side">
            <div className="test-design-evaluation-list-heading">
              <strong>{translate('auto.k1523')}</strong>
              <span>{props.samples.length} / {props.sampleTotal}</span>
            </div>
            <div className="test-design-evaluation-list">
              {props.samples.length ? (
                props.samples.map((sample) => (
                  <button
                    className={`test-design-evaluation-row${sample.id === props.selectedSampleId ? ' selected' : ''}`}
                    key={sample.id}
                    type="button"
                    onClick={() => props.onSelectSample(sample.id)}
                  >
                    <span>
                      <strong>{sample.title || sample.sampleKey}</strong>
                      <em>{sample.sampleKey} · {sample.coverageType} · {sample.promptVersion || '-'}</em>
                      <small>
                        {sample.baselineVersion || translate('auto.k1524')}
                        {sample.sampleDigest ? ` · ${shortIdentifier(sample.sampleDigest)}` : ''}
                      </small>
                    </span>
                    <span className={`badge badge-${sampleStatusTone(sample.status)}`} title={sample.status}>{dictionaryLabel(sample.status)}</span>
                  </button>
                ))
              ) : (
                <div className="notice info">{translate('auto.k1525')}</div>
              )}
            </div>
          </div>
        </div>

        <div className="test-design-calibration-grid">
          <div className="test-design-calibration-form">
            <div className="test-design-evaluation-form-heading">
              <strong>{translate('auto.k1509')}</strong>
              <span className={`badge badge-${calibrationStatusTone(latestCalibrationStatus)}`} title={latestCalibrationStatus}>{dictionaryLabel(latestCalibrationStatus)}</span>
            </div>
            <div className="form-grid">
              <label className="field">
                <span className="field-label">{translate('auto.k1389')}</span>
                <input
                  value={props.calibrationDraft.projectId}
                  onChange={(event) => props.onCalibrationDraftChange((current) => ({ ...current, projectId: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">Prompt</span>
                <input
                  value={props.calibrationDraft.promptKey}
                  onChange={(event) => props.onCalibrationDraftChange((current) => ({ ...current, promptKey: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1514')}</span>
                <input
                  value={props.calibrationDraft.promptVersion}
                  onChange={(event) => props.onCalibrationDraftChange((current) => ({ ...current, promptVersion: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1515')}</span>
                <input
                  value={props.calibrationDraft.baselineVersion}
                  onChange={(event) => props.onCalibrationDraftChange((current) => ({ ...current, baselineVersion: event.target.value }))}
                  disabled={!props.canPolicyManage}
                />
              </label>
              <label className="field">
                <span className="field-label">{translate('auto.k1526')}</span>
                <NativeSelect
                  value={props.calibrationDraft.runMode}
                  onChange={(event) => props.onCalibrationDraftChange((current) => ({ ...current, runMode: event.target.value }))}
                  disabled={!props.canPolicyManage}
                >
                  {calibrationRunModes.map((mode) => (
                    <option key={mode} value={mode}>{dictionaryLabel(mode)}</option>
                  ))}
                </NativeSelect>
              </label>
            </div>
            <label className="field">
              <span className="field-label">{translate('auto.k1527')}</span>
              <textarea
                value={props.calibrationDraft.notes}
                onChange={(event) => props.onCalibrationDraftChange((current) => ({ ...current, notes: event.target.value }))}
                rows={2}
                disabled={!props.canPolicyManage}
              />
            </label>
            <div className="toolbar-actions">
              <button className="btn btn-primary btn-sm" type="button" disabled={!canMutate} onClick={props.onRunCalibration}>
                <Sparkles size={15} />
                {translate('auto.k1528')}</button>
            </div>
          </div>
          <div className="test-design-calibration-list">
            <div className="test-design-evaluation-list-heading">
              <strong>{translate('auto.k1529')}</strong>
              <span>{props.calibrationRuns.length}</span>
            </div>
            {props.calibrationRuns.length ? (
              props.calibrationRuns.map((run) => (
                <div className="test-design-calibration-row" key={run.id}>
                  <span>
                    <strong>{run.promptVersion || '-'} · {run.baselineVersion || translate('auto.k1524')}</strong>
                    <em>{dictionaryLabel(run.runMode)} {translate('auto.k1530')}{run.sampleCount} {translate('auto.k1531')}{run.candidateCount}</em>
                    <small>{translate('auto.k1532')}{run.regressionCount} · {run.createdAt ?? '-'}</small>
                  </span>
                  <span className={`badge badge-${calibrationStatusTone(run.status)}`} title={run.status}>{dictionaryLabel(run.status)}</span>
                </div>
              ))
            ) : (
              <div className="notice info">{translate('auto.k1533')}</div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
