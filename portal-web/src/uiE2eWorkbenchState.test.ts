import { describe, expect, it } from 'vitest';
import {
  blankUiE2eSceneDraft,
  buildUiE2eRunDiagnosis,
  buildUiE2eWorkbenchOverview,
  buildUiE2eFlakyPayload,
  buildUiE2eRunPayload,
  buildUiE2eScenePayload,
  buildUiE2eSceneUpdatePayload,
  explainUiE2eArtifactCaptureBlockedReason,
  explainUiE2eFailureBucket,
  extractUiE2eArtifactCaptureBlockedReason,
  initialUiE2eSceneDraft,
  isUiE2eRunActiveStatus,
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

  it('explains failure buckets and artifact block reasons', () => {
    expect(explainUiE2eFailureBucket('LOCATOR')).toContain('定位器');
    expect(extractUiE2eArtifactCaptureBlockedReason({ captureBlockedReason: 'runnerDisabled' })).toBe('runnerDisabled');
    expect(explainUiE2eArtifactCaptureBlockedReason('runnerDisabled')).toContain('runner 默认关闭');
    expect(explainUiE2eArtifactCaptureBlockedReason(undefined)).toContain('artifact capture 被阻断');
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
