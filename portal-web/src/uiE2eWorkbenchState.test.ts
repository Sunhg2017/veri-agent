import { describe, expect, it } from 'vitest';
import {
  blankUiE2eSceneDraft,
  buildUiE2eFlakyPayload,
  buildUiE2eRunPayload,
  buildUiE2eScenePayload,
  buildUiE2eSceneUpdatePayload,
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
