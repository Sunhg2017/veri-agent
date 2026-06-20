import { describe, expect, it } from 'vitest';
import {
  blankUiE2eSceneDraft,
  buildUiE2eBundleListSummary,
  buildUiE2eArtifactDownloadState,
  buildUiE2eFlakyDetailInsight,
  buildUiE2eBundleQueueOverview,
  buildUiE2eFlakyListSummary,
  buildUiE2eFlakyQueueOverview,
  buildUiE2eRunCreationReadiness,
  buildUiE2eRunDiagnosis,
  buildUiE2eRunFlakyGuidance,
  buildUiE2eRunAuditTimeline,
  buildUiE2eRunListSummary,
  buildUiE2eRunQueueOverview,
  buildUiE2eSceneActivitySummary,
  buildUiE2eSceneListSummary,
  buildUiE2eSceneQueueOverview,
  buildUiE2eWorkbenchOverview,
  buildUiE2eFlakyPayload,
  buildUiE2eRunPayload,
  buildUiE2eScenePayload,
  buildUiE2eSceneUpdatePayload,
  explainUiE2eArtifactCaptureBlockedReason,
  explainUiE2eFailureBucket,
  extractUiE2eArtifactCaptureBlockedReason,
  filterUiE2eBundlesByFocusMode,
  filterUiE2eFlakyMarksByFocusMode,
  filterUiE2eRunsByFocusMode,
  filterUiE2eScenesByFocusMode,
  initialUiE2eSceneDraft,
  isUiE2eRunActiveStatus,
  labelUiE2eBundleFocusMode,
  labelUiE2eFlakyFocusMode,
  labelUiE2eRunFocusMode,
  labelUiE2eSceneFocusMode,
  prettyJson,
  sceneDraftFromDetail,
  splitTags
} from './uiE2eWorkbenchState';

describe('ui e2e workbench state helpers', () => {
  it('builds scene payloads from drafts and parses json object fields', () => {
    const result = buildUiE2eScenePayload({
      ...initialUiE2eSceneDraft,
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      code: 'portal-login',
      name: 'Portal login',
      status: 'APPROVED',
      riskLevel: 'HIGH',
      tagsText: 'smoke admin，login',
      sourceSummaryText: '{"sourceType":"WP3","assetId":"asset-1"}',
      steps: [{
        stepType: 'LOGIN',
        actionSummaryText: '{"submitAction":"click"}',
        locatorStrategyText: '{"preferred":"testId"}',
        assertionSummaryText: '{"successSignal":"url contains /dashboard"}',
        waitPolicyText: '{"timeoutSeconds":5}'
      }]
    });

    expect(result.issues).toEqual([]);
    expect(result.payload).toMatchObject({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      code: 'portal-login',
      name: 'Portal login',
      status: 'APPROVED',
      riskLevel: 'HIGH',
      tags: ['smoke', 'admin', 'login'],
      sourceSummary: { sourceType: 'WP3', assetId: 'asset-1' },
      steps: [{
        stepType: 'LOGIN',
        locatorStrategy: { preferred: 'testId' },
        waitPolicy: { timeoutSeconds: 5 }
      }]
    });
  });

  it('validates malformed scene drafts before write', () => {
    const result = buildUiE2eScenePayload({
      ...initialUiE2eSceneDraft,
      sourceSummaryText: 'not-json',
      steps: [{
        stepType: '',
        actionSummaryText: '[]',
        locatorStrategyText: '{}',
        assertionSummaryText: '{}',
        waitPolicyText: '{}'
      }]
    });

    expect(result.payload).toBeUndefined();
    expect(result.issues).toContain('请填写 scene projectId');
    expect(result.issues).toContain('请填写 scene code');
    expect(result.issues).toContain('请填写 scene name');
    expect(result.issues).toContain('sourceSummary 不是合法 JSON');
    expect(result.issues).toContain('步骤 1 缺少 stepType');
  });

  it('builds scene update payloads without requiring immutable keys', () => {
    const result = buildUiE2eSceneUpdatePayload({
      ...initialUiE2eSceneDraft,
      name: 'Portal login updated',
      status: 'DISABLED',
      riskLevel: 'LOW',
      sourceSummaryText: '{"sourceType":"WP3","assetId":"asset-2"}',
      steps: [{
        stepType: 'ASSERT',
        actionSummaryText: '{"mode":"read"}',
        locatorStrategyText: '{"preferred":"text"}',
        assertionSummaryText: '{"successSignal":"toast visible"}',
        waitPolicyText: '{"timeoutSeconds":8}'
      }]
    });

    expect(result.issues).toEqual([]);
    expect(result.payload).toMatchObject({
      name: 'Portal login updated',
      status: 'DISABLED',
      riskLevel: 'LOW',
      sourceSummary: { sourceType: 'WP3', assetId: 'asset-2' },
      steps: [{ stepType: 'ASSERT', waitPolicy: { timeoutSeconds: 8 } }]
    });
  });

  it('builds run payloads and rejects invalid uuids or request keys', () => {
    expect(buildUiE2eRunPayload({
      projectId: 'project-alpha',
      sceneId: '11111111-1111-4111-8111-111111111111',
      bundleId: '22222222-2222-4222-8222-222222222222',
      environmentId: 'staging',
      baseUrlRef: 'env:staging',
      accountLeaseRef: '33333333-3333-4333-8333-333333333333',
      requestKey: 'wp7.run-1',
      reason: 'manual smoke'
    })).toMatchObject({
      issues: [],
      payload: {
        projectId: 'project-alpha',
        sceneId: '11111111-1111-4111-8111-111111111111',
        bundleId: '22222222-2222-4222-8222-222222222222',
        accountLeaseRef: '33333333-3333-4333-8333-333333333333',
        requestKey: 'wp7.run-1'
      }
    });

    const invalid = buildUiE2eRunPayload({
      projectId: '',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      environmentId: '',
      baseUrlRef: '',
      accountLeaseRef: 'lease-1',
      requestKey: 'bad key',
      reason: ''
    });
    expect(invalid.payload).toBeUndefined();
    expect(invalid.issues).toContain('请填写 run projectId');
    expect(invalid.issues).toContain('sceneId 需要是 UUID');
    expect(invalid.issues).toContain('bundleId 需要是 UUID');
    expect(invalid.issues).toContain('请填写 baseUrlRef');
    expect(invalid.issues).toContain('accountLeaseRef 需要是 UUID');
    expect(invalid.issues).toContain('requestKey 只能包含字母、数字、-、_、.、:，且不超过 128 字符');
  });

  it('builds flaky payloads and requires at least one scope ref', () => {
    expect(buildUiE2eFlakyPayload({
      projectId: 'project-alpha',
      sceneId: '',
      runId: '44444444-4444-4444-8444-444444444444',
      status: 'CONFIRMED_FLAKY',
      reasonCode: 'locator-drift',
      reasonSummary: 'locator changes after deploy'
    })).toMatchObject({
      issues: [],
      payload: {
        projectId: 'project-alpha',
        runId: '44444444-4444-4444-8444-444444444444',
        status: 'CONFIRMED_FLAKY',
        reasonCode: 'locator-drift'
      }
    });

    const invalid = buildUiE2eFlakyPayload({
      projectId: '',
      sceneId: '',
      runId: '',
      status: '',
      reasonCode: '',
      reasonSummary: ''
    });
    expect(invalid.payload).toBeUndefined();
    expect(invalid.issues).toContain('请填写 flaky projectId');
    expect(invalid.issues).toContain('sceneId 和 runId 至少填写一个');
    expect(invalid.issues).toContain('请选择 flaky status');

    const missingReason = buildUiE2eFlakyPayload({
      projectId: 'project-alpha',
      sceneId: '55555555-5555-4555-8555-555555555555',
      runId: '',
      status: 'FLAKY_CANDIDATE',
      reasonCode: 'locator-drift',
      reasonSummary: ''
    });
    expect(missingReason.payload).toBeUndefined();
    expect(missingReason.issues).toContain('请填写 flaky reasonSummary');
  });

  it('keeps tag splitting and pretty json deterministic', () => {
    expect(splitTags(' smoke, admin，portal  login ')).toEqual(['smoke', 'admin', 'portal', 'login']);
    expect(prettyJson({ aggregateOnly: true, count: 2 })).toBe('{\n  "aggregateOnly": true,\n  "count": 2\n}');
  });

  it('recognizes active run statuses for polling and action gating', () => {
    expect(isUiE2eRunActiveStatus('QUEUED')).toBe(true);
    expect(isUiE2eRunActiveStatus('RUNNING')).toBe(true);
    expect(isUiE2eRunActiveStatus('BLOCKED')).toBe(false);
    expect(isUiE2eRunActiveStatus('SUCCEEDED')).toBe(false);
    expect(isUiE2eRunActiveStatus(undefined)).toBe(false);
  });

  it('builds a control-plane overview with health and risk notices', () => {
    const overview = buildUiE2eWorkbenchOverview(
      {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: false,
        runnerMode: 'DISABLED',
        defaultTimeoutSeconds: 180,
        maxTimeoutSeconds: 600,
        maxScenesPerRun: 5,
        maxConcurrency: 2,
        allowlistEnabled: false,
        allowlistHostCount: 0,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        policy: {}
      },
      [
        {
          id: 'scene-1',
          projectId: 'project-alpha',
          code: 'portal-login',
          name: 'Portal login',
          status: 'APPROVED',
          riskLevel: 'HIGH',
          tags: [],
          sourceSummary: {},
          stepCount: 2
        },
        {
          id: 'scene-2',
          projectId: 'project-alpha',
          code: 'portal-search',
          name: 'Portal search',
          status: 'DRAFT',
          riskLevel: 'MEDIUM',
          tags: [],
          sourceSummary: {},
          stepCount: 1
        }
      ],
      [
        {
          id: 'bundle-1',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          status: 'REVIEWING',
          staticCheckSummary: {}
        }
      ],
      [
        {
          id: 'run-1',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          bundleId: 'bundle-1',
          status: 'RUNNING',
          runnerMode: 'MANAGED',
          accountSummary: {}
        },
        {
          id: 'run-2',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          bundleId: 'bundle-1',
          status: 'FAILED',
          runnerMode: 'MANAGED',
          accountSummary: {}
        },
        {
          id: 'run-3',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          bundleId: 'bundle-1',
          status: 'BLOCKED',
          runnerMode: 'DISABLED',
          accountSummary: {}
        }
      ],
      [
        {
          id: 'flaky-1',
          projectId: 'project-alpha',
          linkedRunCount: 0,
          status: 'CONFIRMED_FLAKY'
        }
      ]
    );

    expect(overview).toMatchObject({
      approvedScenes: 1,
      reviewingBundles: 1,
      activeRuns: 1,
      recentFailures: 1,
      blockedRuns: 1,
      confirmedFlaky: 1,
      runnerLabel: 'OFF · DISABLED',
      runnerTone: 'warning',
      allowlistLabel: 'OFF',
      allowlistTone: 'warning'
    });
    expect(overview.notices.map((item) => item.message)).toEqual(expect.arrayContaining([
      '当前 runner 默认关闭，手动创建运行会返回 BLOCKED 摘要，用于验证控制面与权限链路。',
      'baseUrl allowlist 当前关闭，发布前应确认受控目标范围已经收口。',
      '最近列表中有 1 条 FAILED/TIMEOUT 运行，建议优先查看 failureCode 和 traceId。',
      '最近列表中有 1 条 BLOCKED 运行，通常需要复核 runner、租借或审批状态。',
      '当前共有 1 条 CONFIRMED_FLAKY 标记，可作为后续诊断和治理输入。'
    ]));
  });

  it('builds run diagnosis for runner-disabled blocked runs', () => {
    const diagnosis = buildUiE2eRunDiagnosis({
      id: 'run-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'BLOCKED',
      runnerMode: 'DISABLED',
      failureCode: 'UI_E2E_RUNNER_DISABLED',
      accountSummary: { accountLeaseRef: 'lease-1' },
      executionSummary: {
        aggregateOnly: true,
        rawArtifactDownloadReady: false,
        runnerDefaultDisabled: true,
        stepResultCount: 0
      },
      stepResults: [],
      artifacts: [],
      idempotentReplay: false
    });

    expect(diagnosis).toMatchObject({
      tone: 'warning',
      label: 'RUNNER_DISABLED',
      blockedArtifactCount: 0,
      rawArtifactDownloadReady: false
    });
    expect(diagnosis.summary).toContain('runner 默认关闭');
    expect(diagnosis.signals).toEqual(expect.arrayContaining([
      'failureCode=UI_E2E_RUNNER_DISABLED',
      'aggregateOnly=true，当前详情不包含 secretRef 明文与原始 artifact 正文',
      'stepResultCount=0，阻断发生在实际步骤执行之前'
    ]));
    expect(diagnosis.nextActions).toEqual(expect.arrayContaining([
      '如需真实浏览器执行，请先切换到 runner 已启用的环境或打开对应开关。',
      '继续核对审批、租借和 aggregate-only 导出链路是否按预期工作。'
    ]));
  });

  it('builds run creation readiness for missing fields, runner disabled, and ready states', () => {
    expect(buildUiE2eRunCreationReadiness({
      health: null,
      draft: {
        projectId: '',
        sceneId: '',
        bundleId: '',
        baseUrlRef: '',
        accountLeaseRef: ''
      }
    })).toMatchObject({
      ready: false,
      tone: 'info',
      label: '填写运行参数',
      summary: '请先补全 projectId / sceneId / bundleId / baseUrlRef / accountLeaseRef，再触发单次 UI 运行。',
      checks: expect.arrayContaining(['health=pending', 'missing=projectId,sceneId,bundleId,baseUrlRef,accountLeaseRef'])
    });

    expect(buildUiE2eRunCreationReadiness({
      health: {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: false,
        runnerMode: 'DISABLED',
        defaultTimeoutSeconds: 60,
        maxTimeoutSeconds: 300,
        maxScenesPerRun: 1,
        maxConcurrency: 2,
        allowlistEnabled: true,
        allowlistHostCount: 2,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '11111111-1111-4111-8111-111111111111'
      },
      scene: {
        code: 'portal-login',
        status: 'APPROVED'
      },
      bundle: {
        status: 'APPROVED',
        sceneCode: 'portal-login',
        sceneStatus: 'APPROVED'
      }
    })).toMatchObject({
      ready: false,
      tone: 'warning',
      label: 'Runner Disabled',
      checks: expect.arrayContaining(['runner=OFF:DISABLED', 'scene=APPROVED', 'bundle=APPROVED'])
    });

    expect(buildUiE2eRunCreationReadiness({
      health: {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: true,
        runnerMode: 'MANAGED',
        defaultTimeoutSeconds: 60,
        maxTimeoutSeconds: 300,
        maxScenesPerRun: 1,
        maxConcurrency: 2,
        allowlistEnabled: true,
        allowlistHostCount: 2,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '11111111-1111-4111-8111-111111111111'
      },
      scene: {
        code: 'portal-login',
        status: 'DRAFT'
      },
      bundle: {
        status: 'REVIEWING',
        sceneCode: 'portal-login',
        sceneStatus: 'DRAFT'
      }
    })).toMatchObject({
      ready: false,
      tone: 'warning',
      label: 'Scene Not Ready'
    });

    expect(buildUiE2eRunCreationReadiness({
      health: {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: true,
        runnerMode: 'MANAGED',
        defaultTimeoutSeconds: 60,
        maxTimeoutSeconds: 300,
        maxScenesPerRun: 1,
        maxConcurrency: 2,
        allowlistEnabled: true,
        allowlistHostCount: 2,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '11111111-1111-4111-8111-111111111111'
      },
      scene: {
        code: 'portal-login',
        status: 'APPROVED'
      },
      bundle: {
        status: 'APPROVED',
        sceneCode: 'portal-login',
        sceneStatus: 'APPROVED'
      }
    })).toMatchObject({
      ready: true,
      tone: 'success',
      label: 'Ready To Run',
      checks: expect.arrayContaining(['runner=ON:MANAGED', 'scene=APPROVED', 'bundle=APPROVED'])
    });
  });

  it('builds run diagnosis for flaky failed runs with blocked artifacts', () => {
    const diagnosis = buildUiE2eRunDiagnosis({
      id: 'run-2',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'FAILED',
      runnerMode: 'MANAGED',
      accountSummary: {},
      flakyStatus: 'CONFIRMED_FLAKY',
      executionSummary: {
        aggregateOnly: true,
        rawArtifactDownloadReady: false,
        stepResultCount: 2,
        failureBucketCounts: {
          ASSERTION: 1,
          LOCATOR: 1
        }
      },
      stepResults: [
        {
          id: 'step-1',
          stepOrder: 1,
          status: 'FAILED',
          durationMs: 1200,
          failureBucket: 'ASSERTION',
          errorCode: 'ASSERTION_MISMATCH',
          summary: { expected: 'dashboard visible' }
        }
      ],
      artifacts: [
        {
          id: 'artifact-1',
          artifactType: 'SCREENSHOT',
          captureStatus: 'BLOCKED',
          sizeBytes: 0,
          redactionFlags: { captureBlockedReason: 'artifactRefIncomplete' }
        }
      ],
      flakyMark: {
        id: 'flaky-1',
        projectId: 'project-alpha',
        linkedRunCount: 1,
        status: 'CONFIRMED_FLAKY'
      },
      idempotentReplay: false
    });

    expect(diagnosis).toMatchObject({
      tone: 'warning',
      label: 'CONFIRMED_FLAKY',
      primaryFailureBucket: 'ASSERTION x1',
      blockedArtifactCount: 1,
      rawArtifactDownloadReady: false
    });
    expect(diagnosis.signals).toEqual(expect.arrayContaining([
      'failureBucketCounts=ASSERTION x1, LOCATOR x1',
      'artifactCaptureBlocked=1 (artifactRefIncomplete)',
      'flakyStatus=CONFIRMED_FLAKY',
      'rawArtifactDownloadReady=false，artifact 仅提供 manifest 摘要'
    ]));
    expect(diagnosis.nextActions).toEqual(expect.arrayContaining([
      '对照步骤 summary 与断言预期，判断是产品变更还是用例漂移。',
      '核对 scene/bundle 中 locator 策略是否与当前页面结构一致。',
      '检查 runner 回传的 artifact storageRef 与 digest 是否完整。',
      '当前运行已标记为 CONFIRMED_FLAKY，可优先按不稳定场景治理而不是直接回归 blocker。'
    ]));
  });

  it('builds artifact download state for ready, blocked, and pending manifests', () => {
    expect(buildUiE2eArtifactDownloadState({
      id: 'artifact-ready',
      artifactType: 'SCREENSHOT',
      storageRef: 'artifact://ui-e2e/run-1/screenshot-artifact-ready.png',
      sizeBytes: 128,
      redactionFlags: { rawArtifactDownloadReady: true },
      captureStatus: 'CAPTURED'
    })).toMatchObject({
      canDownload: true,
      downloadReady: true,
      tone: 'success'
    });

    expect(buildUiE2eArtifactDownloadState({
      id: 'artifact-blocked',
      artifactType: 'TRACE',
      sizeBytes: 0,
      redactionFlags: { captureBlockedReason: 'artifactRefIncomplete' },
      captureStatus: 'BLOCKED'
    }).summary).toContain('storageRef 或 digest 缺失');

    expect(buildUiE2eArtifactDownloadState({
      id: 'artifact-pending',
      artifactType: 'LOG',
      sizeBytes: 0,
      redactionFlags: {},
      captureStatus: 'PENDING'
    })).toMatchObject({
      canDownload: false,
      downloadReady: false,
      tone: 'info'
    });
  });

  it('builds run flaky guidance for failed, confirmed, and successful runs', () => {
    const failed = buildUiE2eRunFlakyGuidance({
      id: 'run-failed',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'FAILED',
      runnerMode: 'MANAGED',
      failureCode: 'UI_E2E_BASE_URL_NOT_ALLOWED',
      accountSummary: {},
      flakyStatus: 'NONE',
      executionSummary: {
        failureBucketCounts: {
          LOCATOR: 2
        }
      },
      stepResults: [],
      artifacts: [],
      idempotentReplay: false
    });
    expect(failed).toMatchObject({
      tone: 'warning',
      label: '建议记录候选'
    });
    expect(failed.presets).toEqual(expect.arrayContaining([
      expect.objectContaining({
        status: 'FLAKY_CANDIDATE',
        reasonCode: 'locator-drift'
      }),
      expect.objectContaining({
        status: 'CONFIRMED_FLAKY',
        reasonCode: 'locator-drift'
      })
    ]));

    const confirmed = buildUiE2eRunFlakyGuidance({
      id: 'run-confirmed',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'FAILED',
      runnerMode: 'MANAGED',
      accountSummary: {},
      flakyStatus: 'CONFIRMED_FLAKY',
      executionSummary: {},
      stepResults: [],
      artifacts: [],
      flakyMark: {
        id: 'flaky-1',
        projectId: 'project-alpha',
        runId: 'run-confirmed',
        linkedRunCount: 1,
        status: 'CONFIRMED_FLAKY',
        reasonCode: 'locator-drift',
        reasonSummary: '定位偶发漂移'
      },
      idempotentReplay: false
    });
    expect(confirmed).toMatchObject({
      tone: 'warning',
      label: '已确认抖动'
    });
    expect(confirmed.presets).toEqual(expect.arrayContaining([
      expect.objectContaining({ status: 'WAIVED' }),
      expect.objectContaining({ status: 'FLAKY_CANDIDATE' })
    ]));

    const succeeded = buildUiE2eRunFlakyGuidance({
      id: 'run-succeeded',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'SUCCEEDED',
      runnerMode: 'MANAGED',
      accountSummary: {},
      flakyStatus: 'NONE',
      executionSummary: {},
      stepResults: [],
      artifacts: [],
      idempotentReplay: false
    });
    expect(succeeded).toMatchObject({
      tone: 'success',
      label: '当前运行稳定',
      presets: []
    });
  });

  it('builds a run audit timeline from existing run detail snapshots', () => {
    const timeline = buildUiE2eRunAuditTimeline({
      id: 'run-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      sceneCode: 'portal-login',
      bundleId: 'bundle-1',
      status: 'FAILED',
      runnerMode: 'MANAGED',
      requestKey: 'wp7.run-1',
      failureCode: 'UI_E2E_BASE_URL_NOT_ALLOWED',
      failureSummary: 'baseUrl host blocked by allowlist',
      traceId: 'trc-run-1',
      accountSummary: {},
      executionSummary: {},
      stepResults: [
        {
          id: 'step-1',
          stepOrder: 1,
          status: 'SUCCEEDED',
          durationMs: 800,
          summary: {},
          createdAt: '2026-06-19T01:00:02Z',
          updatedAt: '2026-06-19T01:00:03Z'
        },
        {
          id: 'step-2',
          stepOrder: 2,
          status: 'FAILED',
          durationMs: 1600,
          failureBucket: 'ASSERTION',
          errorCode: 'ASSERTION_MISMATCH',
          summary: {},
          createdAt: '2026-06-19T01:00:04Z',
          updatedAt: '2026-06-19T01:00:05Z'
        }
      ],
      artifacts: [
        {
          id: 'artifact-1',
          artifactType: 'SCREENSHOT',
          captureStatus: 'BLOCKED',
          sizeBytes: 0,
          redactionFlags: { captureBlockedReason: 'artifactRefIncomplete' },
          createdAt: '2026-06-19T01:00:06Z',
          updatedAt: '2026-06-19T01:00:06Z'
        }
      ],
      flakyMark: {
        id: 'flaky-1',
        projectId: 'project-alpha',
        runId: 'run-1',
        linkedRunCount: 1,
        status: 'FLAKY_CANDIDATE',
        reasonCode: 'assertion-variance',
        reasonSummary: '断言偶发波动，继续观察',
        createdAt: '2026-06-19T01:00:07Z',
        updatedAt: '2026-06-19T01:00:08Z'
      },
      idempotentReplay: true,
      createdAt: '2026-06-19T01:00:00Z',
      startedAt: '2026-06-19T01:00:01Z',
      finishedAt: '2026-06-19T01:00:09Z',
      updatedAt: '2026-06-19T01:00:09Z'
    });

    expect(timeline.map((item) => item.title)).toEqual([
      '运行创建',
      '执行开始',
      '步骤 1 · SUCCEEDED',
      '步骤 2 · FAILED',
      'Artifact · SCREENSHOT · BLOCKED',
      'Flaky 标记 · FLAKY_CANDIDATE',
      '运行终态 · FAILED',
      '幂等回放'
    ]);
    expect(timeline).toEqual(expect.arrayContaining([
      expect.objectContaining({
        kindLabel: 'RUN',
        title: '运行创建',
        detail: '控制面已接收 requestKey=wp7.run-1 的运行请求。'
      }),
      expect.objectContaining({
        kindLabel: 'STEP',
        title: '步骤 2 · FAILED',
        detail: 'failureBucket=ASSERTION · errorCode=ASSERTION_MISMATCH · duration=1600ms',
        tone: 'danger'
      }),
      expect.objectContaining({
        kindLabel: 'ARTIFACT',
        title: 'Artifact · SCREENSHOT · BLOCKED',
        detail: expect.stringContaining('storageRef 或 digest 缺失'),
        tone: 'warning'
      }),
      expect.objectContaining({
        kindLabel: 'FLAKY',
        title: 'Flaky 标记 · FLAKY_CANDIDATE',
        detail: expect.stringContaining('reason=assertion-variance')
      }),
      expect.objectContaining({
        kindLabel: 'RUN',
        title: '运行终态 · FAILED',
        detail: expect.stringContaining('traceId=trc-run-1'),
        tone: 'danger'
      })
    ]));
  });

  it('explains failure buckets and artifact block reasons', () => {
    expect(explainUiE2eFailureBucket('LOCATOR')).toContain('定位器');
    expect(extractUiE2eArtifactCaptureBlockedReason({ captureBlockedReason: 'runnerDisabled' })).toBe('runnerDisabled');
    expect(explainUiE2eArtifactCaptureBlockedReason('runnerDisabled')).toContain('runner 默认关闭');
    expect(explainUiE2eArtifactCaptureBlockedReason(undefined)).toContain('artifact capture 被阻断');
  });

  it('builds scene queue focus counts and filters scenes by focus mode', () => {
    const scenes = [
      {
        id: 'scene-1',
        projectId: 'project-alpha',
        code: 'portal-login',
        name: 'Portal login',
        status: 'APPROVED',
        riskLevel: 'HIGH',
        tags: ['smoke'],
        sourceSummary: { sourceType: 'WP3' },
        stepCount: 3,
        environmentId: 'staging'
      },
      {
        id: 'scene-2',
        projectId: 'project-alpha',
        code: 'portal-search',
        name: 'Portal search',
        status: 'REVIEWING',
        riskLevel: 'MEDIUM',
        tags: [],
        sourceSummary: {},
        stepCount: 2
      },
      {
        id: 'scene-3',
        projectId: 'project-alpha',
        code: 'portal-order',
        name: 'Portal order',
        status: 'DRAFT',
        riskLevel: 'CRITICAL',
        tags: ['critical', 'core'],
        sourceSummary: { sourceType: 'WP5' },
        stepCount: 5
      },
      {
        id: 'scene-4',
        projectId: 'project-alpha',
        code: 'portal-legacy',
        name: 'Portal legacy',
        status: 'DISABLED',
        riskLevel: 'LOW',
        tags: [],
        sourceSummary: {},
        stepCount: 1
      }
    ];

    const overview = buildUiE2eSceneQueueOverview(scenes);
    expect(overview.focusOptions).toEqual(expect.arrayContaining([
      expect.objectContaining({ mode: 'approved', count: 1, tone: 'success' }),
      expect.objectContaining({ mode: 'reviewing', count: 1, tone: 'info' }),
      expect.objectContaining({ mode: 'draft', count: 1, tone: 'warning' }),
      expect.objectContaining({ mode: 'highRisk', count: 2, tone: 'danger' }),
      expect.objectContaining({ mode: 'disabled', count: 1, tone: 'warning' })
    ]));

    expect(filterUiE2eScenesByFocusMode(scenes, 'approved').map((scene) => scene.id)).toEqual(['scene-1']);
    expect(filterUiE2eScenesByFocusMode(scenes, 'reviewing').map((scene) => scene.id)).toEqual(['scene-2']);
    expect(filterUiE2eScenesByFocusMode(scenes, 'draft').map((scene) => scene.id)).toEqual(['scene-3']);
    expect(filterUiE2eScenesByFocusMode(scenes, 'highRisk').map((scene) => scene.id)).toEqual(['scene-1', 'scene-3']);
    expect(filterUiE2eScenesByFocusMode(scenes, 'disabled').map((scene) => scene.id)).toEqual(['scene-4']);
    expect(labelUiE2eSceneFocusMode('approved')).toBe('已批准');
  });

  it('builds a concise scene list summary for approved and draft scenes', () => {
    expect(buildUiE2eSceneListSummary({
      id: 'scene-1',
      projectId: 'project-alpha',
      code: 'portal-login',
      name: 'Portal login',
      status: 'APPROVED',
      riskLevel: 'HIGH',
      tags: ['smoke'],
      sourceSummary: { sourceType: 'WP3' },
      stepCount: 3,
      environmentId: 'staging'
    })).toMatchObject({
      headline: '可生成 bundle',
      signals: ['risk=HIGH', 'steps=3', 'env=staging', 'tags=1', 'source=WP3'],
      detail: '场景已批准，可继续生成 bundle 或串联运行链路。'
    });

    expect(buildUiE2eSceneListSummary({
      id: 'scene-2',
      projectId: 'project-alpha',
      code: 'portal-order',
      name: 'Portal order',
      status: 'DRAFT',
      riskLevel: 'CRITICAL',
      tags: ['critical', 'core'],
      sourceSummary: { sourceType: 'WP5' },
      stepCount: 5
    })).toMatchObject({
      headline: '草稿待补全',
      signals: ['risk=CRITICAL', 'steps=5', 'tags=2', 'source=WP5'],
      detail: '场景仍在草稿态，可继续补全步骤模板、定位策略和断言摘要。'
    });
  });

  it('builds scene activity summary from related bundles and runs', () => {
    const summary = buildUiE2eSceneActivitySummary(
      'scene-1',
      [
        {
          id: 'bundle-1',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          sceneCode: 'portal-login',
          status: 'REVIEWING',
          bundleDigest: 'digest-1',
          staticCheckStatus: 'PASSED',
          staticCheckSummary: {},
          updatedAt: '2026-06-19T01:00:00Z'
        },
        {
          id: 'bundle-2',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          sceneCode: 'portal-login',
          status: 'APPROVED',
          bundleDigest: 'digest-2',
          staticCheckStatus: 'PASSED',
          staticCheckSummary: {},
          approvedAt: '2026-06-19T02:00:00Z'
        },
        {
          id: 'bundle-3',
          projectId: 'project-alpha',
          sceneId: 'scene-2',
          sceneCode: 'portal-search',
          status: 'APPROVED',
          bundleDigest: 'digest-3',
          staticCheckStatus: 'PASSED',
          staticCheckSummary: {},
          approvedAt: '2026-06-19T03:00:00Z'
        }
      ],
      [
        {
          id: 'run-1',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          sceneCode: 'portal-login',
          bundleId: 'bundle-1',
          status: 'FAILED',
          runnerMode: 'MANAGED',
          failureCode: 'UI_E2E_BASE_URL_NOT_ALLOWED',
          accountSummary: {},
          finishedAt: '2026-06-19T04:00:00Z'
        },
        {
          id: 'run-2',
          projectId: 'project-alpha',
          sceneId: 'scene-1',
          sceneCode: 'portal-login',
          bundleId: 'bundle-2',
          status: 'RUNNING',
          runnerMode: 'MANAGED',
          accountSummary: {},
          startedAt: '2026-06-19T05:00:00Z'
        },
        {
          id: 'run-3',
          projectId: 'project-alpha',
          sceneId: 'scene-2',
          sceneCode: 'portal-search',
          bundleId: 'bundle-3',
          status: 'SUCCEEDED',
          runnerMode: 'MANAGED',
          accountSummary: {},
          finishedAt: '2026-06-19T06:00:00Z'
        }
      ]
    );

    expect(summary).toMatchObject({
      bundleCount: 2,
      runCount: 2,
      latestBundle: expect.objectContaining({
        id: 'bundle-2',
        status: 'APPROVED'
      }),
      latestRun: expect.objectContaining({
        id: 'run-2',
        status: 'RUNNING'
      })
    });
  });

  it('builds bundle queue focus counts and filters bundles by focus mode', () => {
    const bundles = [
      {
        id: 'bundle-1',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        status: 'REVIEWING',
        bundleDigest: 'digest-1',
        staticCheckStatus: 'PASSED',
        staticCheckSummary: {}
      },
      {
        id: 'bundle-2',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        status: 'STATIC_CHECK_FAILED',
        bundleDigest: 'digest-2',
        staticCheckStatus: 'SCRIPT_STATIC_CHECK_FAILED',
        staticCheckSummary: {}
      },
      {
        id: 'bundle-3',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        status: 'REJECTED',
        bundleDigest: 'digest-3',
        staticCheckStatus: 'PASSED',
        staticCheckSummary: {}
      },
      {
        id: 'bundle-4',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        status: 'APPROVED',
        bundleDigest: 'digest-4',
        staticCheckStatus: 'PASSED',
        staticCheckSummary: {}
      }
    ];

    const overview = buildUiE2eBundleQueueOverview(bundles);
    expect(overview.focusOptions).toEqual(expect.arrayContaining([
      expect.objectContaining({ mode: 'reviewing', count: 1, tone: 'info' }),
      expect.objectContaining({ mode: 'submittable', count: 2, tone: 'warning' }),
      expect.objectContaining({ mode: 'approved', count: 1, tone: 'success' }),
      expect.objectContaining({ mode: 'staticFailed', count: 1, tone: 'danger' }),
      expect.objectContaining({ mode: 'rejected', count: 1, tone: 'danger' })
    ]));

    expect(filterUiE2eBundlesByFocusMode(bundles, 'reviewing').map((bundle) => bundle.id)).toEqual(['bundle-1']);
    expect(filterUiE2eBundlesByFocusMode(bundles, 'submittable').map((bundle) => bundle.id)).toEqual(['bundle-2', 'bundle-3']);
    expect(filterUiE2eBundlesByFocusMode(bundles, 'approved').map((bundle) => bundle.id)).toEqual(['bundle-4']);
    expect(filterUiE2eBundlesByFocusMode(bundles, 'staticFailed').map((bundle) => bundle.id)).toEqual(['bundle-2']);
    expect(filterUiE2eBundlesByFocusMode(bundles, 'rejected').map((bundle) => bundle.id)).toEqual(['bundle-3']);
    expect(labelUiE2eBundleFocusMode('reviewing')).toBe('待审批');
  });

  it('builds a concise bundle list summary for reviewing and static-check-failed bundles', () => {
    expect(buildUiE2eBundleListSummary({
      id: 'bundle-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      sceneStatus: 'APPROVED',
      status: 'REVIEWING',
      bundleDigest: 'digest-1',
      staticCheckStatus: 'PASSED',
      staticCheckSummary: {}
    })).toMatchObject({
      headline: '等待审批',
      signals: ['static=PASSED', 'scene=APPROVED', 'digest-ready', 'review-pending'],
      detail: '脚本包已送审，待 review 决定是否允许进入运行链路。'
    });

    expect(buildUiE2eBundleListSummary({
      id: 'bundle-2',
      projectId: 'project-alpha',
      sceneId: 'scene-2',
      sceneStatus: 'DRAFT',
      status: 'STATIC_CHECK_FAILED',
      bundleDigest: 'digest-2',
      staticCheckStatus: 'SCRIPT_STATIC_CHECK_FAILED',
      staticCheckSummary: {}
    })).toMatchObject({
      headline: 'STATIC_CHECK_FAILED',
      signals: ['static=STATIC_CHECK_FAILED', 'scene=DRAFT', 'digest-ready'],
      detail: '静态校验未通过，建议先处理摘要中的失败项后再送审。'
    });
  });

  it('builds run queue focus counts and filters runs by focus mode', () => {
    const runs = [
      {
        id: 'run-1',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        status: 'RUNNING',
        runnerMode: 'MANAGED',
        accountSummary: {}
      },
      {
        id: 'run-2',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        status: 'FAILED',
        runnerMode: 'MANAGED',
        failureCode: 'UI_E2E_BASE_URL_NOT_ALLOWED',
        accountSummary: {}
      },
      {
        id: 'run-3',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        status: 'BLOCKED',
        runnerMode: 'DISABLED',
        failureCode: 'UI_E2E_RUNNER_DISABLED',
        accountSummary: {}
      },
      {
        id: 'run-4',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        status: 'SUCCEEDED',
        runnerMode: 'MANAGED',
        flakyStatus: 'CONFIRMED_FLAKY',
        accountSummary: {}
      }
    ];

    const overview = buildUiE2eRunQueueOverview(runs);
    expect(overview.focusOptions).toEqual(expect.arrayContaining([
      expect.objectContaining({ mode: 'active', count: 1, tone: 'info' }),
      expect.objectContaining({ mode: 'failures', count: 1, tone: 'danger' }),
      expect.objectContaining({ mode: 'blocked', count: 1, tone: 'warning' }),
      expect.objectContaining({ mode: 'flaky', count: 1, tone: 'warning' }),
      expect.objectContaining({ mode: 'runnerDisabled', count: 1, tone: 'warning' })
    ]));

    expect(filterUiE2eRunsByFocusMode(runs, 'active').map((run) => run.id)).toEqual(['run-1']);
    expect(filterUiE2eRunsByFocusMode(runs, 'failures').map((run) => run.id)).toEqual(['run-2']);
    expect(filterUiE2eRunsByFocusMode(runs, 'blocked').map((run) => run.id)).toEqual(['run-3']);
    expect(filterUiE2eRunsByFocusMode(runs, 'flaky').map((run) => run.id)).toEqual(['run-4']);
    expect(filterUiE2eRunsByFocusMode(runs, 'runnerDisabled').map((run) => run.id)).toEqual(['run-3']);
    expect(labelUiE2eRunFocusMode('failures')).toBe('失败/超时');
  });

  it('builds a concise run list summary for blocked and active runs', () => {
    expect(buildUiE2eRunListSummary({
      id: 'run-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'BLOCKED',
      runnerMode: 'DISABLED',
      failureCode: 'UI_E2E_RUNNER_DISABLED',
      accountSummary: {}
    })).toMatchObject({
      headline: 'RUNNER_DISABLED',
      signals: ['failure=RUNNER_DISABLED', 'aggregate-only'],
      detail: 'runner 默认关闭，控制面返回 BLOCKED 摘要。'
    });

    expect(buildUiE2eRunListSummary({
      id: 'run-2',
      projectId: 'project-alpha',
      sceneId: 'scene-2',
      bundleId: 'bundle-2',
      status: 'RUNNING',
      runnerMode: 'MANAGED',
      flakyStatus: 'CONFIRMED_FLAKY',
      accountSummary: {}
    })).toMatchObject({
      headline: 'runner=MANAGED',
      signals: ['flaky=CONFIRMED_FLAKY', 'auto-refresh'],
      detail: '运行进行中，详情面板会自动刷新最新快照。'
    });
  });

  it('builds flaky queue focus counts and filters flaky marks by focus mode', () => {
    const flakyMarks = [
      {
        id: 'flaky-1',
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        sceneCode: 'portal-login',
        runId: 'run-1',
        linkedRunCount: 2,
        runStatus: 'FAILED',
        status: 'FLAKY_CANDIDATE',
        reasonCode: 'locator-drift',
        reasonSummary: '登录页偶发定位漂移'
      },
      {
        id: 'flaky-2',
        projectId: 'project-alpha',
        sceneId: 'scene-2',
        sceneCode: 'portal-search',
        runId: 'run-2',
        linkedRunCount: 1,
        runStatus: 'SUCCEEDED',
        status: 'CONFIRMED_FLAKY',
        reasonCode: 'env-jitter',
        reasonSummary: '环境偶发抖动'
      },
      {
        id: 'flaky-3',
        projectId: 'project-alpha',
        sceneId: 'scene-3',
        sceneCode: 'portal-order',
        linkedRunCount: 0,
        status: 'WAIVED',
        reasonCode: 'legacy-known-issue',
        reasonSummary: '历史问题暂不阻断'
      }
    ];

    const overview = buildUiE2eFlakyQueueOverview(flakyMarks);
    expect(overview.focusOptions).toEqual(expect.arrayContaining([
      expect.objectContaining({ mode: 'candidates', count: 1, tone: 'info' }),
      expect.objectContaining({ mode: 'confirmed', count: 1, tone: 'warning' }),
      expect.objectContaining({ mode: 'waived', count: 1, tone: 'info' }),
      expect.objectContaining({ mode: 'runLinked', count: 2, tone: 'info' }),
      expect.objectContaining({ mode: 'sceneOnly', count: 1, tone: 'warning' })
    ]));

    expect(filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'candidates').map((item) => item.id)).toEqual(['flaky-1']);
    expect(filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'confirmed').map((item) => item.id)).toEqual(['flaky-2']);
    expect(filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'waived').map((item) => item.id)).toEqual(['flaky-3']);
    expect(filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'runLinked').map((item) => item.id)).toEqual(['flaky-1', 'flaky-2']);
    expect(filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'sceneOnly').map((item) => item.id)).toEqual(['flaky-3']);
    expect(labelUiE2eFlakyFocusMode('confirmed')).toBe('已确认');
  });

  it('builds a concise flaky list summary for candidate and waived marks', () => {
    expect(buildUiE2eFlakyListSummary({
      id: 'flaky-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      sceneCode: 'portal-login',
      sceneRiskLevel: 'HIGH',
      runId: 'run-1',
      linkedRunCount: 2,
      runStatus: 'FAILED',
      latestFailureBucket: 'LOCATOR',
      status: 'FLAKY_CANDIDATE',
      reasonCode: 'locator-drift',
      reasonSummary: '登录页偶发定位漂移，需要继续观察',
      updatedBy: 'qa-owner'
    })).toMatchObject({
      headline: '待人工确认',
      signals: ['reason=locator-drift', 'run=FAILED', 'scope=run', 'by=qa-owner']
    });

    expect(buildUiE2eFlakyListSummary({
      id: 'flaky-2',
      projectId: 'project-alpha',
      sceneId: 'scene-2',
      sceneCode: 'portal-search',
      linkedRunCount: 0,
      status: 'WAIVED',
      reasonCode: 'legacy-known-issue',
      reasonSummary: '',
      createdBy: 'pm-owner'
    })).toMatchObject({
      headline: '已豁免',
      detail: '该记录已豁免，保留原因用于审计和后续复盘。',
      signals: ['reason=legacy-known-issue', 'scope=scene', 'by=pm-owner']
    });
  });

  it('builds flaky detail insight with governance signals', () => {
    expect(buildUiE2eFlakyDetailInsight({
      id: 'flaky-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      sceneCode: 'portal-login',
      sceneName: 'Portal login',
      sceneRiskLevel: 'CRITICAL',
      runId: 'run-1',
      linkedRunCount: 3,
      runStatus: 'FAILED',
      latestFailureBucket: 'LOCATOR',
      status: 'CONFIRMED_FLAKY',
      reasonCode: 'locator-drift',
      reasonSummary: '多次部署后定位器漂移',
      updatedBy: 'qa-owner'
    })).toMatchObject({
      tone: 'warning',
      label: '治理池',
      signals: ['risk=CRITICAL', 'runs=3', 'latestFailureBucket=LOCATOR', 'runStatus=FAILED']
    });

    expect(buildUiE2eFlakyDetailInsight({
      id: 'flaky-2',
      projectId: 'project-alpha',
      sceneId: 'scene-2',
      sceneCode: 'portal-search',
      linkedRunCount: 1,
      status: 'FLAKY_CANDIDATE',
      latestFailureBucket: 'RUNNER'
    })).toMatchObject({
      tone: 'info',
      label: '待复核'
    });
  });

  it('hydrates and resets scene drafts predictably', () => {
    expect(blankUiE2eSceneDraft({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging'
    })).toMatchObject({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      code: '',
      name: '',
      steps: [{ stepType: 'LOGIN' }]
    });

    expect(sceneDraftFromDetail({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      code: 'portal-login',
      name: 'Portal login',
      status: 'APPROVED',
      riskLevel: 'HIGH',
      tags: ['smoke', 'admin'],
      sourceSummary: { sourceType: 'WP3', assetId: 'asset-1' },
      steps: [{
        id: 'step-1',
        stepOrder: 1,
        stepType: 'LOGIN',
        actionSummary: { submitAction: 'click' },
        locatorStrategy: { preferred: 'testId' },
        assertionSummary: { successSignal: 'dashboard visible' },
        waitPolicy: { timeoutSeconds: 5 }
      }]
    })).toMatchObject({
      projectId: 'project-alpha',
      code: 'portal-login',
      tagsText: 'smoke admin',
      sourceSummaryText: '{\n  "sourceType": "WP3",\n  "assetId": "asset-1"\n}',
      steps: [{
        stepType: 'LOGIN',
        actionSummaryText: '{\n  "submitAction": "click"\n}'
      }]
    });
  });
});
