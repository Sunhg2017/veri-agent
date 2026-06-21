import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestBinary, requestJson } from './client';
import {
  approveUiE2eBundle,
  archiveUiE2eBundle,
  archiveUiE2eScene,
  cancelUiE2eRun,
  createUiE2eBundle,
  createUiE2eRun,
  createUiE2eScene,
  exportUiE2eBundle,
  exportUiE2eRun,
  fetchUiE2eBundle,
  fetchUiE2eBundles,
  fetchUiE2eFlakyMark,
  fetchUiE2eFlakyMarks,
  fetchUiE2eHealth,
  fetchUiE2eRun,
  fetchUiE2eRuns,
  fetchUiE2eScene,
  fetchUiE2eScenes,
  importUiE2eScene,
  downloadUiE2eArtifact,
  normalizeUiE2eBundleDetail,
  normalizeUiE2eBundleExport,
  normalizeUiE2eBundleSummary,
  normalizeUiE2eFlakyMark,
  normalizeUiE2eHealth,
  normalizeUiE2eList,
  normalizeUiE2eRunDetail,
  normalizeUiE2eRunExport,
  normalizeUiE2eSceneDetail,
  normalizeUiE2eSceneImport,
  normalizeUiE2eSceneSummary,
  rejectUiE2eBundle,
  submitUiE2eBundleReview,
  updateUiE2eScene,
  upsertUiE2eFlakyMark
} from './uiE2e';

vi.mock('./client', () => ({
  requestJson: vi.fn(),
  requestBinary: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestBinaryMock = vi.mocked(requestBinary);

describe('WP7 ui e2e API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestBinaryMock.mockReset();
    requestBinaryMock.mockResolvedValue({
      blob: new Blob(['artifact-bytes'], { type: 'application/zip' }),
      traceId: 'trc-artifact',
      contentType: 'application/zip',
      filename: 'trace.zip'
    });
  });

  it('normalizes health, scene, bundle, run, flaky and export responses', () => {
    expect(normalizeUiE2eHealth({
      service: 'ui-e2e',
      status: 'UP',
      enabled: true,
      runner_enabled: false,
      runner_mode: 'DISABLED',
      default_timeout_seconds: '120',
      max_timeout_seconds: '600',
      max_scenes_per_run: '3',
      max_concurrency: '2',
      allowlist_enabled: true,
      allowlist_host_count: '1',
      export_enabled: true,
      supported_node_types: ['UI_TEST'],
      credential_policy: { secretPlaintextReturned: false },
      artifact_policy: { aggregateOnly: true },
      runner_capacity: { active_workers: '1', available_workers: '3', queued_tasks: '0', saturated: false },
      policy: { supportedFlakyStatuses: ['NONE', 'CONFIRMED_FLAKY'] }
    })).toMatchObject({
      runnerEnabled: false,
      runnerMode: 'DISABLED',
      maxConcurrency: 2,
      supportedNodeTypes: ['UI_TEST'],
      credentialPolicy: { secretPlaintextReturned: false },
      runnerCapacity: { active_workers: '1', available_workers: '3', queued_tasks: '0', saturated: false }
    });

    expect(normalizeUiE2eSceneSummary({
      id: 'scene-1',
      project_id: 'project-alpha',
      application_id: 'app-alpha',
      environment_id: 'staging',
      code: 'portal-login',
      name: 'Portal login',
      status: 'APPROVED',
      risk_level: 'HIGH',
      tags: ['smoke'],
      source_summary: { sourceType: 'WP3' },
      step_count: '2'
    })).toMatchObject({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      stepCount: 2
    });

    expect(normalizeUiE2eSceneDetail({
      id: 'scene-1',
      project_id: 'project-alpha',
      code: 'portal-login',
      name: 'Portal login',
      status: 'APPROVED',
      risk_level: 'HIGH',
      tags: ['smoke'],
      steps: [{
        id: 'step-1',
        step_order: '1',
        step_type: 'LOGIN',
        action_summary: { submitAction: 'click' }
      }],
      policy: { executable: true }
    })).toMatchObject({
      steps: [{ stepOrder: 1, stepType: 'LOGIN', actionSummary: { submitAction: 'click' } }],
      policy: { executable: true }
    });

    expect(normalizeUiE2eSceneImport({
      project_id: 'project-alpha',
      application_id: 'app-alpha',
      environment_id: 'staging',
      code: 'portal-import',
      name: 'Portal import',
      status: 'DRAFT',
      risk_level: 'HIGH',
      tags: ['imported'],
      source_summary: { sourceType: 'PLAYWRIGHT_CODEGEN' },
      steps: [{
        step_order: '2',
        step_type: 'LOGIN',
        action_summary: { submitAction: 'click' }
      }],
      warnings: ['unsupported'],
      import_summary: { editableDraft: true }
    })).toMatchObject({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      steps: [{ stepOrder: 2, stepType: 'LOGIN', actionSummary: { submitAction: 'click' } }],
      warnings: ['unsupported'],
      importSummary: { editableDraft: true }
    });

    expect(normalizeUiE2eBundleSummary({
      id: 'bundle-1',
      project_id: 'project-alpha',
      scene_id: 'scene-1',
      scene_code: 'portal-login',
      status: 'REVIEWING',
      bundle_digest: 'sha256:bundle',
      static_check_status: 'PASSED'
    })).toMatchObject({
      sceneCode: 'portal-login',
      bundleDigest: 'sha256:bundle',
      staticCheckStatus: 'PASSED'
    });

    expect(normalizeUiE2eBundleDetail({
      id: 'bundle-1',
      project_id: 'project-alpha',
      scene_id: 'scene-1',
      scene_code: 'portal-login',
      status: 'APPROVED',
      tags: ['smoke', 'admin'],
      spec_summary: { playwright: true },
      fixture_summary: { leaseContractReady: true },
      policy: { approvable: true },
      reviews: [{ id: 'review-1', review_status: 'APPROVED', review_comment: 'ready' }]
    })).toMatchObject({
      tags: ['smoke', 'admin'],
      specSummary: { playwright: true },
      fixtureSummary: { leaseContractReady: true },
      reviews: [{ reviewStatus: 'APPROVED', reviewComment: 'ready' }]
    });

    expect(normalizeUiE2eBundleExport({
      schema_version: 'wp7-bundle-export-v1',
      exported_at: '2026-06-20T01:00:00Z',
      bundle: {
        id: 'bundle-1',
        project_id: 'project-alpha',
        scene_id: 'scene-1',
        status: 'APPROVED',
        tags: ['smoke'],
        spec_summary: { aggregateOnly: true },
        fixture_summary: { credentialMode: 'LEASE_INJECTION_ONLY' },
        static_check_summary: { status: 'PASSED' },
        policy: { reviewCommentExported: false }
      },
      review_summary: {
        review_count: '2',
        note_count: '1',
        review_statuses: ['APPROVED', 'SUBMITTED'],
        latest_review: { reviewStatus: 'APPROVED', commentPresent: true }
      },
      redaction_policy: { aggregateOnly: true, reviewCommentExported: false }
    })).toMatchObject({
      schemaVersion: 'wp7-bundle-export-v1',
      exportedAt: '2026-06-20T01:00:00Z',
      bundle: { specSummary: { aggregateOnly: true } },
      reviewSummary: { reviewCount: 2, noteCount: 1, reviewStatuses: ['APPROVED', 'SUBMITTED'] },
      redactionPolicy: { reviewCommentExported: false }
    });

    expect(normalizeUiE2eRunDetail({
      id: 'run-1',
      project_id: 'project-alpha',
      scene_id: 'scene-1',
      scene_code: 'portal-login',
      bundle_id: 'bundle-1',
      status: 'BLOCKED',
      runner_mode: 'DISABLED',
      failure_code: 'UI_E2E_RUNNER_DISABLED',
      account_summary: { secretPlaintextReturned: false },
      execution_summary: {
        aggregateOnly: true,
        stepResultCount: '1',
        browserTypes: ['CHROMIUM', 'FIREFOX'],
        visualRegressionEnabled: true,
        visualMismatchCount: '1'
      },
      step_results: [{
        id: 'step-result-1',
        step_order: '1',
        status: 'BLOCKED',
        duration_ms: '0',
        failure_bucket: 'RUNNER',
        summary: { aggregateOnly: true }
      }],
      artifacts: [{
        id: 'artifact-1',
        artifact_type: 'SCREENSHOT',
        capture_status: 'BLOCKED',
        size_bytes: '0',
        redaction_flags: { captureBlockedReason: 'runnerDisabled' }
      }],
      flaky_mark: {
        id: 'flaky-1',
        project_id: 'project-alpha',
        status: 'CONFIRMED_FLAKY'
      },
      idempotent_replay: true
    })).toMatchObject({
      failureCode: 'UI_E2E_RUNNER_DISABLED',
      accountSummary: { secretPlaintextReturned: false },
      executionSummary: {
        browserTypes: ['CHROMIUM', 'FIREFOX'],
        visualRegressionEnabled: true,
        visualMismatchCount: '1'
      },
      stepResults: [{ failureBucket: 'RUNNER', durationMs: 0 }],
      artifacts: [{ artifactType: 'SCREENSHOT', captureStatus: 'BLOCKED' }],
      flakyMark: { status: 'CONFIRMED_FLAKY' },
      idempotentReplay: true
    });

    expect(normalizeUiE2eFlakyMark({
      id: 'flaky-1',
      project_id: 'project-alpha',
      scene_id: 'scene-1',
      scene_risk_level: 'HIGH',
      run_id: 'run-1',
      linked_run_count: '3',
      run_status: 'BLOCKED',
      latest_failure_bucket: 'RUNNER',
      status: 'FLAKY_CANDIDATE',
      reason_code: 'LOCATOR_DRIFT',
      reason_summary: 'locator changes after deploy'
    })).toMatchObject({
      sceneId: 'scene-1',
      sceneRiskLevel: 'HIGH',
      runId: 'run-1',
      linkedRunCount: 3,
      runStatus: 'BLOCKED',
      latestFailureBucket: 'RUNNER',
      reasonCode: 'LOCATOR_DRIFT'
    });

    expect(normalizeUiE2eRunExport({
      schema_version: 'wp7-run-export-v1',
      exported_at: '2026-06-19T01:00:00Z',
      run: {
        id: 'run-1',
        project_id: 'project-alpha',
        scene_id: 'scene-1',
        bundle_id: 'bundle-1',
        status: 'BLOCKED',
        runner_mode: 'DISABLED',
        account_summary: {}
      },
      redaction_policy: { aggregateOnly: true, artifactDownloadReady: false }
    })).toMatchObject({
      schemaVersion: 'wp7-run-export-v1',
      exportedAt: '2026-06-19T01:00:00Z',
      redactionPolicy: { aggregateOnly: true, artifactDownloadReady: false }
    });

    expect(normalizeUiE2eList({
      items: [{ id: 'scene-1', project_id: 'project-alpha', code: 'portal-login', name: 'Portal login', status: 'APPROVED', risk_level: 'HIGH' }],
      total: '1'
    }, normalizeUiE2eSceneSummary)).toMatchObject({
      total: 1,
      items: [{ id: 'scene-1', projectId: 'project-alpha' }]
    });
  });

  it('calls wp7 endpoints with normalized paths and payloads', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: { items: [] } });

    await fetchUiE2eHealth();
    await fetchUiE2eScenes({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      status: 'APPROVED',
      riskLevel: 'HIGH',
      tag: 'smoke',
      keyword: 'login',
      size: 10
    });
    await fetchUiE2eScene('scene-1');
    await importUiE2eScene({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      sourceType: 'PLAYWRIGHT_CODEGEN',
      content: 'test()',
      codeHint: 'portal-import',
      nameHint: 'Portal import',
      tags: ['smoke']
    });
    await createUiE2eScene({
      projectId: 'project-alpha',
      code: 'portal-login',
      name: 'Portal login',
      steps: [{ stepType: 'LOGIN' }]
    });
    await updateUiE2eScene('scene-1', {
      name: 'Portal login v2',
      status: 'APPROVED',
      steps: [{ stepType: 'ASSERT' }]
    });
    await archiveUiE2eScene('scene-1');
    await fetchUiE2eBundles({ projectId: 'project-alpha', status: 'REVIEWING', keyword: 'portal' });
    await fetchUiE2eBundle('bundle-1');
    await createUiE2eBundle({ sceneId: 'scene-1' });
    await submitUiE2eBundleReview('bundle-1', { note: 'ready' });
    await approveUiE2eBundle('bundle-1', { note: 'approved' });
    await rejectUiE2eBundle('bundle-1', { note: 'needs fix' });
    await archiveUiE2eBundle('bundle-1');
    await exportUiE2eBundle('bundle-1');
    await fetchUiE2eRuns({ projectId: 'project-alpha', status: 'BLOCKED', keyword: 'rk-1' });
    await fetchUiE2eRun('run-1');
    await createUiE2eRun({
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      baseUrlRef: 'env:staging',
      accountLeaseRef: 'lease-1',
      browsers: ['CHROMIUM', 'FIREFOX'],
      visualRegressionEnabled: true,
      baselineRunId: 'baseline-run-1',
      visualMismatchThreshold: 0.02
    });
    await cancelUiE2eRun('run-1', { reason: 'cancel' });
    await exportUiE2eRun('run-1');
    await downloadUiE2eArtifact('run-1', 'artifact-1');
    await fetchUiE2eFlakyMarks({ projectId: 'project-alpha', status: 'CONFIRMED_FLAKY', keyword: 'locator' });
    await fetchUiE2eFlakyMark('flaky-1');
    await upsertUiE2eFlakyMark({
      projectId: 'project-alpha',
      runId: 'run-1',
      status: 'CONFIRMED_FLAKY',
      reasonCode: 'LOCATOR_DRIFT'
    });

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/ui-e2e/health');
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/ui-e2e/scenes?projectId=project-alpha&applicationId=app-alpha&environmentId=staging&status=APPROVED&riskLevel=HIGH&tag=smoke&keyword=login&size=10'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/ui-e2e/scenes/scene-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/ui-e2e/scenes/import', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-alpha',
        applicationId: 'app-alpha',
        environmentId: 'staging',
        sourceType: 'PLAYWRIGHT_CODEGEN',
        content: 'test()',
        codeHint: 'portal-import',
        nameHint: 'Portal import',
        tags: ['smoke']
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, '/api/v1/ui-e2e/scenes', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-alpha',
        code: 'portal-login',
        name: 'Portal login',
        steps: [{ stepType: 'LOGIN' }]
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, '/api/v1/ui-e2e/scenes/scene-1', {
      method: 'PATCH',
      body: JSON.stringify({
        name: 'Portal login v2',
        status: 'APPROVED',
        steps: [{ stepType: 'ASSERT' }]
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, '/api/v1/ui-e2e/scenes/scene-1/archive', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(8, '/api/v1/ui-e2e/bundles?projectId=project-alpha&status=REVIEWING&keyword=portal');
    expect(requestJsonMock).toHaveBeenNthCalledWith(9, '/api/v1/ui-e2e/bundles/bundle-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(10, '/api/v1/ui-e2e/bundles', {
      method: 'POST',
      body: JSON.stringify({ sceneId: 'scene-1' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(11, '/api/v1/ui-e2e/bundles/bundle-1/submit-review', {
      method: 'POST',
      body: JSON.stringify({ note: 'ready' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(12, '/api/v1/ui-e2e/bundles/bundle-1/approve', {
      method: 'POST',
      body: JSON.stringify({ note: 'approved' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(13, '/api/v1/ui-e2e/bundles/bundle-1/reject', {
      method: 'POST',
      body: JSON.stringify({ note: 'needs fix' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(14, '/api/v1/ui-e2e/bundles/bundle-1/archive', {
      method: 'POST'
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(15, '/api/v1/ui-e2e/bundles/bundle-1/export');
    expect(requestJsonMock).toHaveBeenNthCalledWith(16, '/api/v1/ui-e2e/runs?projectId=project-alpha&status=BLOCKED&keyword=rk-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(17, '/api/v1/ui-e2e/runs/run-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(18, '/api/v1/ui-e2e/runs', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: 'lease-1',
        browsers: ['CHROMIUM', 'FIREFOX'],
        visualRegressionEnabled: true,
        baselineRunId: 'baseline-run-1',
        visualMismatchThreshold: 0.02
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(19, '/api/v1/ui-e2e/runs/run-1/cancel', {
      method: 'POST',
      body: JSON.stringify({ reason: 'cancel' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(20, '/api/v1/ui-e2e/runs/run-1/export');
    expect(requestBinaryMock).toHaveBeenNthCalledWith(1, '/api/v1/ui-e2e/runs/run-1/artifacts/artifact-1/download');
    expect(requestJsonMock).toHaveBeenNthCalledWith(21, '/api/v1/ui-e2e/flaky-marks?projectId=project-alpha&status=CONFIRMED_FLAKY&keyword=locator');
    expect(requestJsonMock).toHaveBeenNthCalledWith(22, '/api/v1/ui-e2e/flaky-marks/flaky-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(23, '/api/v1/ui-e2e/flaky-marks', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-alpha',
        runId: 'run-1',
        status: 'CONFIRMED_FLAKY',
        reasonCode: 'LOCATOR_DRIFT'
      })
    });
  });
});
