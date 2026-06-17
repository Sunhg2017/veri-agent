import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  archiveReport,
  compareReport,
  createDefectDraft,
  diagnoseReport,
  exportReport,
  fetchReport,
  fetchLatestReportDiagnosis,
  fetchReportingHealth,
  fetchReports,
  generateReport,
  normalizeDefectDraft,
  normalizeReportCompare,
  normalizeReportDetail,
  normalizeReportDiagnosis,
  normalizeReportExport,
  normalizeReportList,
  normalizeReportingHealth,
  retryReport,
  reviewDefectDraft
} from './reports';

vi.mock('./client', () => ({
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);

describe('WP10 report API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('normalizes health, report detail, diagnosis, defect draft and export responses', () => {
    expect(normalizeReportingHealth({
      service: 'reporting',
      status: 'UP',
      enabled: true,
      generate_enabled: true,
      diagnosis_enabled: true,
      defect_draft_enabled: true,
      export_enabled: true,
      max_evidence_items: '80',
      max_diagnosis_context_chars: '12000',
      max_export_markdown_chars: '40000',
      schema_version: 'wp10-report-v1',
      field_set_version: 'wp10-export-fields-v1',
      policy: { aggregateOnly: true }
    })).toMatchObject({
      enabled: true,
      generateEnabled: true,
      defectDraftEnabled: true,
      maxEvidenceItems: 80,
      fieldSetVersion: 'wp10-export-fields-v1'
    });

    expect(normalizeReportList({
      items: [{
        id: 'report-1',
        project_id: 'project-alpha',
        execution_run_id: 'run-1',
        status: 'READY',
        schema_version: 'wp10-report-v1',
        summary: { defectDraftCount: '2' },
        idempotent_replay: true
      }],
      total: '1'
    })).toMatchObject({
      total: 1,
      items: [{ projectId: 'project-alpha', executionRunId: 'run-1', idempotentReplay: true }]
    });

    expect(normalizeReportDiagnosis({
      id: 'diagnosis-1',
      report_id: 'report-1',
      status: 'AI_READY',
      classification: { primaryCategory: 'RUNNER_FAILURE' },
      root_cause_candidates: [{ category: 'RUNNER_FAILURE' }],
      confidence: '0.71',
      manual_review_required: true,
      model_invocation_digest: 'sha256:model',
      ai_diagnosis_ready: true,
      model_invoked: true,
      classification_only: false,
      redaction_policy: { rawPromptStored: false },
      diagnosis_context: { contextDigest: 'sha256:ctx' }
    })).toMatchObject({
      reportId: 'report-1',
      confidence: 0.71,
      aiDiagnosisReady: true,
      modelInvoked: true,
      classificationOnly: false
    });

    expect(normalizeDefectDraft({
      id: 'draft-1',
      report_id: 'report-1',
      diagnosis_id: 'diagnosis-1',
      status: 'DRAFT',
      reproduction_summary: 'run failed',
      impact_summary: 'release blocked',
      priority_suggestion: 'P1',
      evidence_refs: ['sha256:evidence'],
      payload_preview: { masked: true, externalSystemWriteAttempted: false }
    })).toMatchObject({
      reportId: 'report-1',
      reproductionSummary: 'run failed',
      prioritySuggestion: 'P1',
      evidenceRefs: ['sha256:evidence'],
      payloadPreview: { masked: true, externalSystemWriteAttempted: false }
    });

    expect(normalizeReportDetail({
      id: 'report-1',
      project_id: 'project-alpha',
      execution_run_id: 'run-1',
      status: 'READY',
      schema_version: 'wp10-report-v1',
      summary: { runStatus: 'FAILED' },
      redaction_policy: { aggregateOnly: true },
      evidence_manifests: [{
        id: 'evidence-1',
        report_id: 'report-1',
        source_wp: 'WP9',
        source_type: 'RUN_NODE',
        summary_keys: ['status'],
        redaction_flags: { rawRunnerArtifactStored: false }
      }],
      latest_diagnosis: { id: 'diagnosis-1', report_id: 'report-1', status: 'RULE_READY' },
      defect_drafts: [{ id: 'draft-1', report_id: 'report-1', status: 'DRAFT' }]
    })).toMatchObject({
      projectId: 'project-alpha',
      evidenceManifests: [{ sourceWp: 'WP9', summaryKeys: ['status'] }],
      latestDiagnosis: { status: 'RULE_READY' },
      defectDrafts: [{ id: 'draft-1' }]
    });

    expect(normalizeReportExport({
      id: 'export-1',
      report_id: 'report-1',
      export_type: 'MARKDOWN',
      status: 'CREATED',
      schema_version: 'wp10-report-export-v1',
      field_set_version: 'wp10-export-fields-v1',
      content_digest: 'sha256:content',
      aggregate_only: true,
      redaction_policy: { rawResponseStored: false },
      manifest: { digest: 'sha256:manifest' },
      content: '# report'
    })).toMatchObject({
      reportId: 'report-1',
      exportType: 'MARKDOWN',
      aggregateOnly: true,
      contentDigest: 'sha256:content'
    });

    expect(normalizeReportCompare({
      report_id: 'report-1',
      baseline_report_id: 'report-0',
      project_id: 'project-alpha',
      unchanged: false,
      changed_fields: ['summary.runStatus'],
      metadata_diffs: [{ field: 'status', baseline_value: 'READY', current_value: 'FAILED' }],
      summary_diffs: [{ field: 'runStatus', baseline_value: 'SUCCEEDED', current_value: 'FAILED' }],
      diagnosis_diffs: [{ field: 'primaryCategory', baseline_value: 'NO_FAILURE', current_value: 'RUNNER_FAILURE' }],
      evidence_diff: {
        changed: true,
        baseline_count: 2,
        current_count: 3,
        added_manifest_keys: ['WP8:ACCOUNT_LEASE:sha256:lease'],
        removed_manifest_keys: [],
        baseline_source_wp_counts: { WP9: 2 },
        current_source_wp_counts: { WP9: 2, WP8: 1 },
        baseline_source_type_counts: { RUN_NODE: 2 },
        current_source_type_counts: { RUN_NODE: 2, ACCOUNT_LEASE: 1 }
      },
      defect_draft_diff: {
        changed: true,
        baseline_count: 0,
        current_count: 1,
        baseline_status_counts: {},
        current_status_counts: { DRAFT: 1 }
      }
    })).toMatchObject({
      reportId: 'report-1',
      baselineReportId: 'report-0',
      changedFields: ['summary.runStatus'],
      metadataDiffs: [{ field: 'status', baselineValue: 'READY', currentValue: 'FAILED' }],
      evidenceDiff: { currentCount: 3, currentSourceWpCounts: { WP8: 1 } },
      defectDraftDiff: { currentStatusCounts: { DRAFT: 1 } }
    });
  });

  it('calls report endpoints with normalized paths and payloads', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: {} });
    await fetchReportingHealth();
    await fetchReports({ projectId: 'project alpha', executionRunId: 'run-1', status: 'READY', size: 10 });
    await fetchReport('report-1');
    await compareReport('report-1', 'report-0');
    await generateReport({ projectId: 'project-alpha', executionRunId: 'run-1', requestKey: 'rk-1', reason: 'manual' });
    await retryReport('report-1');
    await archiveReport('report-1');
    await diagnoseReport('report-1');
    await fetchLatestReportDiagnosis('report-1');
    await createDefectDraft('report-1');
    await reviewDefectDraft('report-1', 'draft-1', 'REVIEWED');
    await exportReport('report-1', 'MARKDOWN');

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/reports/health');
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/reports?projectId=project+alpha&executionRunId=run-1&status=READY&size=10'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/reports/report-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/reports/report-1/compare?baselineReportId=report-0');
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, '/api/v1/reports', {
      method: 'POST',
      body: JSON.stringify({ projectId: 'project-alpha', executionRunId: 'run-1', requestKey: 'rk-1', reason: 'manual' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, '/api/v1/reports/report-1/retry', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, '/api/v1/reports/report-1/archive', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(8, '/api/v1/reports/report-1/diagnoses', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(9, '/api/v1/reports/report-1/diagnoses/latest');
    expect(requestJsonMock).toHaveBeenNthCalledWith(10, '/api/v1/reports/report-1/defect-drafts', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(11, '/api/v1/reports/report-1/defect-drafts/draft-1', {
      method: 'PATCH',
      body: JSON.stringify({ status: 'REVIEWED' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(12, '/api/v1/reports/report-1/export?exportType=MARKDOWN');
  });
});
