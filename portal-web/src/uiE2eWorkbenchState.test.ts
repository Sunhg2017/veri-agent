import { describe, expect, it } from 'vitest';
import {
  blankUiE2eSceneDraft,
  buildUiE2eBundleListSummary,
  buildUiE2eArtifactDownloadState,
  buildUiE2eBatchRunPayload,
  buildUiE2eBatchRunReadiness,
  buildUiE2eBatchRunSummary,
  buildUiE2eFlakyDetailInsight,
  buildUiE2eBundleQueueOverview,
  buildUiE2eFlakyListSummary,
  buildUiE2eFlakyQueueOverview,
  buildUiE2eRunBackfillPayload,
  buildUiE2eRunBackfillReadiness,
  buildUiE2eRunBackfillSummary,
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
  sceneDraftFromImport,
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
        waitPolicyText: '{"timeoutSeconds":5}',
        dataBindingText: '{"dataSetCode":"checkout-users","recordKey":"user-001","bindingAlias":"user"}'
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
        waitPolicy: { timeoutSeconds: 5 },
        dataBinding: { dataSetCode: 'checkout-users', recordKey: 'user-001', bindingAlias: 'user' }
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
        waitPolicyText: '{}',
        dataBindingText: '[]'
      }]
    });

    expect(result.payload).toBeUndefined();
    expect(result.issues).toContain('请填写 场景项目 ID');
    expect(result.issues).toContain('请填写 场景编码');
    expect(result.issues).toContain('请填写 场景名称');
    expect(result.issues).toContain('sourceSummary 不是合法 JSON');
    expect(result.issues).toContain('步骤 1 缺少 步骤类型');
    expect(result.issues).toContain('steps[0].dataBinding 必须是 JSON 对象');
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
        waitPolicyText: '{"timeoutSeconds":8}',
        dataBindingText: '{"dataSetCode":"checkout-users","recordKey":"user-002"}'
      }]
    });

    expect(result.issues).toEqual([]);
    expect(result.payload).toMatchObject({
      name: 'Portal login updated',
      status: 'DISABLED',
      riskLevel: 'LOW',
      sourceSummary: { sourceType: 'WP3', assetId: 'asset-2' },
      steps: [{
        stepType: 'ASSERT',
        waitPolicy: { timeoutSeconds: 8 },
        dataBinding: { dataSetCode: 'checkout-users', recordKey: 'user-002' }
      }]
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
      reason: 'manual smoke',
      browsersText: 'chromium firefoX',
      visualRegressionEnabled: true,
      baselineRunId: '44444444-4444-4444-8444-444444444444',
      visualMismatchThreshold: '0.05'
    })).toMatchObject({
      issues: [],
      payload: {
        projectId: 'project-alpha',
        sceneId: '11111111-1111-4111-8111-111111111111',
        bundleId: '22222222-2222-4222-8222-222222222222',
        accountLeaseRef: '33333333-3333-4333-8333-333333333333',
        requestKey: 'wp7.run-1',
        browsers: ['CHROMIUM', 'FIREFOX'],
        visualRegressionEnabled: true,
        baselineRunId: '44444444-4444-4444-8444-444444444444',
        visualMismatchThreshold: 0.05
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
      reason: '',
      browsersText: 'chromium safari',
      visualRegressionEnabled: true,
      baselineRunId: 'bad-baseline',
      visualMismatchThreshold: '1.5'
    });
    expect(invalid.payload).toBeUndefined();
    expect(invalid.issues).toContain('请填写 运行项目 ID');
    expect(invalid.issues).toContain('场景 ID 需要是 UUID');
    expect(invalid.issues).toContain('脚本包 ID 需要是 UUID');
    expect(invalid.issues).toContain('请填写 基础地址引用');
    expect(invalid.issues).toContain('账号租约 需要是 UUID');
    expect(invalid.issues).toContain('浏览器仅支持 CHROMIUM / FIREFOX / WEBKIT');
    expect(invalid.issues).toContain('基线运行 ID 需要是 UUID');
    expect(invalid.issues).toContain('视觉不匹配阈值 需要在 0 到 1 之间');
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
    expect(invalid.issues).toContain('请填写不稳定标记项目 ID');
    expect(invalid.issues).toContain('场景 ID 和 runId 至少填写一个');
    expect(invalid.issues).toContain('请选择不稳定标记状态');

    const missingReason = buildUiE2eFlakyPayload({
      projectId: 'project-alpha',
      sceneId: '55555555-5555-4555-8555-555555555555',
      runId: '',
      status: 'FLAKY_CANDIDATE',
      reasonCode: 'locator-drift',
      reasonSummary: ''
    });
    expect(missingReason.payload).toBeUndefined();
    expect(missingReason.issues).toContain('请填写不稳定标记原因说明');
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
        runnerCapacity: {
          queuedTasks: 2,
          saturated: true,
          summaryBackfillReady: false
        },
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
      runnerLabel: '关闭 · 已禁用',
      runnerTone: 'warning',
      allowlistLabel: '关闭',
      allowlistTone: 'warning'
    });
    expect(overview.notices.map((item) => item.message)).toEqual(expect.arrayContaining([
      '当前运行器默认关闭，手动创建运行会返回已阻断摘要，用于验证控制面与权限链路。',
      '基础地址白名单当前关闭，发布前应确认受控目标范围已经收口。',
      '共享浏览器池当前已饱和，新的浏览器尝试可能进入排队。',
      '当前运行器队列中仍有 2 个待处理任务，批量运行可能需要等待空闲槽位。',
      '运行摘要回填当前未就绪，执行前请先确认控制面版本和健康状态。',
      '最近列表中有 1 条失败/超时运行，建议优先查看失败码和追踪 ID。',
      '最近列表中有 1 条已阻断运行，通常需要复核运行器、租借或审批状态。',
      '当前共有 1 条已确认不稳定标记，可作为后续诊断和治理输入。'
    ]));
  });

  it('builds backfill payloads, readiness, and summaries', () => {
    expect(buildUiE2eRunBackfillPayload({
      projectId: 'project-alpha',
      runIdsText: '11111111-1111-4111-8111-111111111111, 22222222-2222-4222-8222-222222222222 11111111-1111-4111-8111-111111111111',
      limit: ''
    })).toMatchObject({
      issues: [],
      payload: {
        projectId: 'project-alpha',
        runIds: [
          '11111111-1111-4111-8111-111111111111',
          '22222222-2222-4222-8222-222222222222'
        ]
      }
    });

    const invalid = buildUiE2eRunBackfillPayload({
      projectId: '',
      runIdsText: 'bad-run-id',
      limit: '300'
    });
    expect(invalid.payload).toBeUndefined();
    expect(invalid.issues).toContain('请填写 回填项目 ID');
    expect(invalid.issues).toContain('数量上限需要是 1 到 200 的整数');
    expect(invalid.issues).toContain('runId 需要是 UUID：bad-run-id');

    expect(buildUiE2eRunBackfillReadiness({
      health: {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: true,
        runnerMode: 'MANAGED',
        defaultTimeoutSeconds: 60,
        maxTimeoutSeconds: 300,
        maxScenesPerRun: 5,
        maxConcurrency: 2,
        allowlistEnabled: true,
        allowlistHostCount: 1,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        runnerCapacity: { summaryBackfillReady: false },
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        runIdsText: '',
        limit: '10'
      }
    })).toMatchObject({
      ready: false,
      tone: 'warning',
      label: '摘要回填待就绪',
      checks: expect.arrayContaining(['摘要回填=待处理', '数量上限=10'])
    });

    expect(buildUiE2eRunBackfillReadiness({
      health: {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: true,
        runnerMode: 'MANAGED',
        defaultTimeoutSeconds: 60,
        maxTimeoutSeconds: 300,
        maxScenesPerRun: 5,
        maxConcurrency: 2,
        allowlistEnabled: true,
        allowlistHostCount: 1,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        runnerCapacity: { summaryBackfillReady: true },
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        runIdsText: '11111111-1111-4111-8111-111111111111',
        limit: '10'
      }
    })).toMatchObject({
      ready: true,
      tone: 'success',
      label: '摘要回填已就绪',
      checks: expect.arrayContaining(['摘要回填=就绪', '运行 ID=1', '数量上限=10'])
    });

    expect(buildUiE2eRunBackfillSummary({
      projectId: 'project-alpha',
      requestedCount: 3,
      updatedCount: 1,
      unchangedCount: 1,
      failedCount: 1,
      items: [
        {
          runId: 'run-1',
          sceneId: 'scene-1',
          status: 'SUCCEEDED',
          updated: true,
          stepResultCount: 2,
          artifactCount: 2
        },
        {
          runId: 'run-2',
          sceneId: 'scene-2',
          status: 'SUCCEEDED',
          updated: false,
          stepResultCount: 1,
          artifactCount: 1
        },
        {
          runId: 'run-3',
          sceneId: 'scene-3',
          status: 'FAILED',
          updated: false,
          stepResultCount: 0,
          artifactCount: 0,
          errorCode: 'UI_E2E_RUN_NOT_FOUND',
          errorMessage: 'run missing from repository snapshot'
        }
      ]
    })).toMatchObject({
      tone: 'warning',
      label: '摘要回填部分失败',
      signals: expect.arrayContaining(['请求数=3', '更新数=1', '未变更数=1', '失败数=1']),
      failedItems: ['RUN_NOT_FOUND · run-3 · run missing from repository snapshot']
    });
  });

  it('builds batch run payloads, readiness, and summaries', () => {
    expect(buildUiE2eBatchRunPayload({
      projectId: 'project-alpha',
      sceneIdsText: '11111111-1111-4111-8111-111111111111, 22222222-2222-4222-8222-222222222222 11111111-1111-4111-8111-111111111111',
      environmentId: 'staging',
      baseUrlRef: 'env:staging',
      accountLeaseRef: '33333333-3333-4333-8333-333333333333',
      requestKeyPrefix: 'nightly-smoke',
      reason: 'batch smoke',
      browsersText: 'CHROMIUM FIREFOX',
      visualRegressionEnabled: true,
      baselineRunId: '',
      visualMismatchThreshold: '0.02'
    })).toMatchObject({
      issues: [],
      payload: {
        projectId: 'project-alpha',
        sceneIds: [
          '11111111-1111-4111-8111-111111111111',
          '22222222-2222-4222-8222-222222222222'
        ],
        environmentId: 'staging',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '33333333-3333-4333-8333-333333333333',
        requestKeyPrefix: 'nightly-smoke',
        reason: 'batch smoke',
        browsers: ['CHROMIUM', 'FIREFOX'],
        visualRegressionEnabled: true,
        visualMismatchThreshold: 0.02
      }
    });

    const invalid = buildUiE2eBatchRunPayload({
      projectId: '',
      sceneIdsText: 'bad-scene-id',
      environmentId: '',
      baseUrlRef: '',
      accountLeaseRef: 'bad-lease',
      requestKeyPrefix: 'bad key',
      reason: 'x'.repeat(513),
      browsersText: 'SAFARI',
      visualRegressionEnabled: true,
      baselineRunId: 'bad-baseline',
      visualMismatchThreshold: '2'
    });
    expect(invalid.payload).toBeUndefined();
    expect(invalid.issues).toContain('请填写 批量项目 ID');
    expect(invalid.issues).toContain('场景 ID 需要是 UUID：bad-scene-id');
    expect(invalid.issues).toContain('请填写 批量基础地址引用');
    expect(invalid.issues).toContain('账号租约 需要是 UUID');
    expect(invalid.issues).toContain('浏览器仅支持 CHROMIUM / FIREFOX / WEBKIT');
    expect(invalid.issues).toContain('基线运行 ID 需要是 UUID');
    expect(invalid.issues).toContain('视觉不匹配阈值 需要在 0 到 1 之间');

    expect(buildUiE2eBatchRunReadiness({
      health: {
        service: 'ui-e2e',
        status: 'UP',
        enabled: true,
        runnerEnabled: true,
        runnerMode: 'MANAGED',
        defaultTimeoutSeconds: 60,
        maxTimeoutSeconds: 300,
        maxScenesPerRun: 2,
        maxConcurrency: 2,
        allowlistEnabled: true,
        allowlistHostCount: 1,
        exportEnabled: true,
        supportedNodeTypes: ['UI_TEST'],
        credentialPolicy: {},
        artifactPolicy: {},
        runnerCapacity: { batchRunReady: true },
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneIdsText: '11111111-1111-4111-8111-111111111111 22222222-2222-4222-8222-222222222222',
        environmentId: 'staging',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '33333333-3333-4333-8333-333333333333',
        requestKeyPrefix: '',
        reason: '',
        browsersText: 'CHROMIUM FIREFOX',
        visualRegressionEnabled: false,
        baselineRunId: '',
        visualMismatchThreshold: ''
      },
      scenes: [
        { id: '11111111-1111-4111-8111-111111111111', projectId: 'project-alpha', code: 'portal-login', name: 'Portal login', status: 'APPROVED', riskLevel: 'HIGH', tags: [], sourceSummary: {}, stepCount: 2 },
        { id: '22222222-2222-4222-8222-222222222222', projectId: 'project-alpha', code: 'portal-dashboard', name: 'Portal dashboard', status: 'APPROVED', riskLevel: 'MEDIUM', tags: [], sourceSummary: {}, stepCount: 3 }
      ]
    })).toMatchObject({
      ready: true,
      tone: 'success',
      label: '批量运行已就绪',
      checks: expect.arrayContaining(['运行器=开启 · 已托管', '批量运行=就绪', '单次最大场景数=2', '场景 ID=2', '已匹配场景=2'])
    });

    expect(buildUiE2eBatchRunSummary({
      projectId: 'project-alpha',
      requestedCount: 3,
      createdCount: 1,
      replayedCount: 1,
      failedCount: 1,
      items: [
        {
          sceneId: 'scene-1',
          sceneCode: 'portal-login',
          bundleId: 'bundle-1',
          outcome: 'CREATED',
          run: {
            id: 'run-1',
            projectId: 'project-alpha',
            sceneId: 'scene-1',
            bundleId: 'bundle-1',
            status: 'QUEUED',
            runnerMode: 'MANAGED',
            accountSummary: {},
            executionSummary: {},
            stepResults: [],
            artifacts: [],
            idempotentReplay: false
          }
        },
        {
          sceneId: 'scene-2',
          sceneCode: 'portal-dashboard',
          bundleId: 'bundle-2',
          outcome: 'REPLAYED'
        },
        {
          sceneId: 'scene-3',
          sceneCode: 'portal-report',
          bundleId: 'bundle-3',
          outcome: 'FAILED',
          errorCode: 'UI_E2E_BUNDLE_NOT_READY',
          errorMessage: '未找到 APPROVED 脚本包'
        }
      ]
    })).toMatchObject({
      tone: 'warning',
      label: '批量运行部分失败',
      signals: expect.arrayContaining(['请求数=3', '创建数=1', '回放数=1', '失败数=1']),
      failedItems: ['portal-report · BUNDLE_NOT_READY · 未找到 APPROVED 脚本包']
    });
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
      label: 'UI E2E 运行器已禁用',
      blockedArtifactCount: 0,
      rawArtifactDownloadReady: false
    });
    expect(diagnosis.summary).toContain('运行器默认关闭');
    expect(diagnosis.signals).toEqual(expect.arrayContaining([
      '失败码=UI E2E 运行器已禁用',
      '仅聚合=true，当前详情不包含密钥引用明文与原始产物正文',
      '步骤结果数=0，阻断发生在实际步骤执行之前'
    ]));
    expect(diagnosis.nextActions).toEqual(expect.arrayContaining([
      '如需真实浏览器执行，请先切换到运行器已启用的环境或打开对应开关。',
      '继续核对审批、租借和 仅聚合 导出链路是否按预期工作。'
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
        accountLeaseRef: '',
        browsersText: '',
        visualRegressionEnabled: false,
        baselineRunId: ''
      }
    })).toMatchObject({
      ready: false,
      tone: 'info',
      label: '填写运行参数',
      summary: '请先补全 projectId / sceneId / bundleId / baseUrlRef / accountLeaseRef / browsers，再触发单次 UI 运行。',
      checks: expect.arrayContaining(['健康摘要=待处理', '请检查字段=项目 ID、场景 ID、场景包 ID、基础 URL 引用、账号租约、浏览器'])
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
        runnerCapacity: {},
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '11111111-1111-4111-8111-111111111111',
        browsersText: 'CHROMIUM',
        visualRegressionEnabled: false,
        baselineRunId: ''
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
      label: '运行器已禁用',
      checks: expect.arrayContaining(['运行器=关闭 · 已禁用', '场景状态=已批准', '场景包 ID=已批准'])
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
        runnerCapacity: {},
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '11111111-1111-4111-8111-111111111111',
        browsersText: 'CHROMIUM',
        visualRegressionEnabled: false,
        baselineRunId: ''
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
      label: '场景未就绪'
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
        runnerCapacity: {},
        policy: {}
      },
      draft: {
        projectId: 'project-alpha',
        sceneId: 'scene-1',
        bundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        accountLeaseRef: '11111111-1111-4111-8111-111111111111',
        browsersText: 'CHROMIUM FIREFOX',
        visualRegressionEnabled: true,
        baselineRunId: ''
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
      label: '已满足运行条件',
      checks: expect.arrayContaining(['运行器=开启 · 已托管', '场景状态=已批准', '场景包 ID=已批准', '基线运行 ID=节点 key 必填'])
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
      label: '已确认不稳定',
      primaryFailureBucket: 'ASSERTION x1',
      blockedArtifactCount: 1,
      rawArtifactDownloadReady: false
    });
    expect(diagnosis.signals).toEqual(expect.arrayContaining([
      '失败分类=ASSERTION x1, LOCATOR x1',
      '产物采集阻断=1 (产物引用不完整)',
      '不稳定标记=已确认不稳定',
      '原始产物下载未就绪，产物仅提供清单摘要'
    ]));
    expect(diagnosis.nextActions).toEqual(expect.arrayContaining([
      '对照步骤摘要与断言预期，判断是产品变更还是用例漂移。',
      '核对场景/脚本包中的定位策略是否与当前页面结构一致。',
      '检查运行器回传的产物存储引用与摘要是否完整。',
      '当前运行已标记为 已确认不稳定，可优先按不稳定场景治理而不是直接回归阻断项。'
    ]));
  });

  it('builds run diagnosis for visual regression mismatches across browsers', () => {
    const diagnosis = buildUiE2eRunDiagnosis({
      id: 'run-visual-1',
      projectId: 'project-alpha',
      sceneId: 'scene-1',
      bundleId: 'bundle-1',
      status: 'FAILED',
      runnerMode: 'PLAYWRIGHT_SUBPROCESS',
      failureCode: 'UI_E2E_VISUAL_REGRESSION_FAILED',
      failureSummary: 'visual mismatch exceeded threshold on FIREFOX',
      accountSummary: {},
      executionSummary: {
        aggregateOnly: true,
        rawArtifactDownloadReady: true,
        browserTypes: ['CHROMIUM', 'FIREFOX'],
        browserCount: 2,
        parallelExecutionEnabled: true,
        visualRegressionEnabled: true,
        visualBaselineRunId: 'baseline-run-1',
        visualMismatchThreshold: 0.03,
        visualComparisonCount: 2,
        visualMismatchCount: 1,
        visualMismatchBrowsers: ['FIREFOX'],
        stepResultCount: 2
      },
      stepResults: [],
      artifacts: [],
      idempotentReplay: false
    });

    expect(diagnosis).toMatchObject({
      tone: 'error',
      label: 'UI E2E 视觉回归失败',
      rawArtifactDownloadReady: true
    });
    expect(diagnosis.signals).toEqual(expect.arrayContaining([
      '失败码=UI E2E 视觉回归失败',
      '浏览器=Chromium,Firefox',
      '并发执行=开启（2 个浏览器）',
      '视觉回归=开启（阈值=0.03）',
      '基线运行 ID=baseline-run-1',
      '视觉比对=2 次比对 / 1 次不匹配',
      '视觉不匹配浏览器=Firefox'
    ]));
    expect(diagnosis.nextActions).toEqual(expect.arrayContaining([
      '优先查看 DIFF/BASELINE/ACTUAL 三类截图产物，确认是预期 UI 变更还是样式回归。',
      '如属于预期改版，请更新基线运行；如属于噪声波动，再评估是否需要放宽 不匹配阈值。',
      '优先复核 FIREFOX 浏览器上的布局、样式和截图基线是否仍匹配。'
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
    }).summary).toContain('存储引用或摘要缺失');

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
      '步骤 1 · 成功',
      '步骤 2 · 失败',
      '产物数 · SCREENSHOT · 已阻断',
      '不稳定标记 · 疑似不稳定',
      '运行终态 · FAILED',
      '幂等回放'
    ]);
    expect(timeline).toEqual(expect.arrayContaining([
      expect.objectContaining({
        kindLabel: '运行',
        title: '运行创建',
        detail: '控制面已接收 requestKey=wp7.run-1 的运行请求。'
      }),
      expect.objectContaining({
        kindLabel: '步骤',
        title: '步骤 2 · 失败',
        detail: '失败分类=ASSERTION · 错误码=Assertion Mismatch · 耗时=1600ms',
        tone: 'danger'
      }),
      expect.objectContaining({
        kindLabel: '产物',
        title: '产物数 · SCREENSHOT · 已阻断',
        detail: expect.stringContaining('存储引用或摘要缺失'),
        tone: 'warning'
      }),
      expect.objectContaining({
        kindLabel: '不稳定标记',
        title: '不稳定标记 · 疑似不稳定',
        detail: expect.stringContaining('原因码=assertion-variance')
      }),
      expect.objectContaining({
        kindLabel: '运行',
        title: '运行终态 · FAILED',
        detail: expect.stringContaining('追踪 ID=trc-run-1'),
        tone: 'danger'
      })
    ]));
  });

  it('explains failure buckets and artifact block reasons', () => {
    expect(explainUiE2eFailureBucket('LOCATOR')).toContain('定位器');
    expect(extractUiE2eArtifactCaptureBlockedReason({ captureBlockedReason: 'runnerDisabled' })).toBe('runnerDisabled');
    expect(explainUiE2eArtifactCaptureBlockedReason('runnerDisabled')).toContain('运行器默认关闭');
    expect(explainUiE2eArtifactCaptureBlockedReason(undefined)).toContain('产物采集被阻断');
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
      headline: '可生成脚本包',
      signals: ['风险等级=高', '步骤数=3', '环境 ID=staging', '标签=1', '来源类型=WP3'],
      detail: '场景已批准，可继续生成脚本包 或串联运行链路。'
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
      signals: ['风险等级=严重', '步骤数=5', '标签=2', '来源类型=WP5'],
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
      signals: ['静态检查=通过', '场景状态=已批准', '摘要=就绪', '评审=待处理'],
      detail: '脚本包已送审，待评审决定是否允许进入运行链路。'
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
      headline: '静态检查失败',
      signals: ['静态检查=静态检查失败', '场景状态=草稿', '摘要=就绪'],
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
      headline: 'UI E2E 运行器已禁用',
      signals: ['失败码=UI E2E 运行器已禁用', '聚合摘要'],
      detail: '运行器默认关闭，控制面返回已阻断摘要。'
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
      headline: '运行模式=已托管',
      signals: ['不稳定标记=已确认不稳定', '自动刷新'],
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
        waitPolicy: { timeoutSeconds: 5 },
        dataBinding: {}
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

    expect(sceneDraftFromImport({
      projectId: 'project-alpha',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      code: 'portal-import',
      name: 'Portal import',
      status: 'DRAFT',
      riskLevel: 'MEDIUM',
      tags: ['imported', 'smoke'],
      sourceSummary: { sourceType: 'PLAYWRIGHT_CODEGEN', importedFrom: 'Playwright codegen' },
      steps: [{
        stepOrder: 1,
        stepType: 'NAVIGATE',
        actionSummary: { targetPath: '/login' },
        locatorStrategy: { preferred: 'path' },
        assertionSummary: {},
        waitPolicy: { timeoutSeconds: 5 },
        dataBinding: {}
      }]
    })).toMatchObject({
      projectId: 'project-alpha',
      code: 'portal-import',
      name: 'Portal import',
      tagsText: 'imported smoke',
      sourceSummaryText: '{\n  "sourceType": "PLAYWRIGHT_CODEGEN",\n  "importedFrom": "Playwright codegen"\n}',
      steps: [{
        stepType: 'NAVIGATE',
        actionSummaryText: '{\n  "targetPath": "/login"\n}'
      }]
    });
  });
});
