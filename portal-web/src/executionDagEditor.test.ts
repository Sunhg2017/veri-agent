import { describe, expect, it } from 'vitest';
import {
  buildExecutionPlanPayload,
  buildExecutionPlanUpdatePayload,
  executionPlanDraftFromDetail,
  initialExecutionPlanDraft,
  parseCommaSeparated,
  summarizeDraftNode,
  validateExecutionPlanDraft,
  type ExecutionPlanDraft
} from './executionDagEditor';
import type { ExecutionPlanDetail } from './api/execution';

describe('WP9 execution DAG editor helpers', () => {
  it('builds sanitized multi-node plan payloads', () => {
    const draft: ExecutionPlanDraft = {
      projectId: ' project-alpha ',
      name: ' Release smoke ',
      environmentKey: ' staging ',
      status: 'READY',
      description: ' nightly ',
      nodes: [{
        key: 'api-smoke',
        type: 'API_TEST',
        dependenciesText: '',
        apiAutomationBundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        caseIdsText: 'case-1, case-2',
        runtimeSecretRefsText: 'secret://wp9/token',
        timeoutSeconds: 180,
        failurePolicy: 'FAIL_FAST',
        maxAttempts: 2
      }, {
        key: 'report',
        type: 'REPORT_HANDOFF',
        dependenciesText: 'api-smoke',
        apiAutomationBundleId: '',
        baseUrlRef: '',
        caseIdsText: '',
        runtimeSecretRefsText: '',
        timeoutSeconds: 60,
        failurePolicy: 'CONTINUE',
        maxAttempts: 0
      }]
    };

    expect(validateExecutionPlanDraft(draft)).toEqual([]);
    expect(buildExecutionPlanPayload(draft)).toMatchObject({
      projectId: 'project-alpha',
      name: 'Release smoke',
      environmentKey: 'staging',
      status: 'READY',
      description: 'nightly',
      dag: {
        nodes: [{
          key: 'api-smoke',
          dependencies: [],
          input: {
            apiAutomationBundleId: 'bundle-1',
            baseUrlRef: 'env:staging',
            caseIds: ['case-1', 'case-2'],
            runtimeSecretRefs: ['secret://wp9/token'],
            rawBaseUrlStored: false,
            secretRefsStored: false
          },
          retryPolicy: { maxAttempts: 2 }
        }, {
          key: 'report',
          dependencies: ['api-smoke'],
          failurePolicy: 'CONTINUE'
        }]
      }
    });
    expect(buildExecutionPlanUpdatePayload(draft)).not.toHaveProperty('projectId');
    expect(summarizeDraftNode(draft.nodes[0])).toContain('bundle bundle-1');
  });

  it('validates duplicate keys, missing dependencies and cycles', () => {
    const draft: ExecutionPlanDraft = {
      ...initialExecutionPlanDraft,
      projectId: 'project-alpha',
      name: 'Release smoke',
      environmentKey: 'staging',
      nodes: [{
        ...initialExecutionPlanDraft.nodes[0],
        key: 'a',
        dependenciesText: 'b',
        timeoutSeconds: 0
      }, {
        ...initialExecutionPlanDraft.nodes[0],
        key: 'b',
        dependenciesText: 'a',
        maxAttempts: 9
      }, {
        ...initialExecutionPlanDraft.nodes[0],
        key: 'c',
        dependenciesText: 'missing'
      }, {
        ...initialExecutionPlanDraft.nodes[0],
        key: 'c',
        dependenciesText: ''
      }]
    };

    expect(validateExecutionPlanDraft(draft).map((issue) => issue.message)).toEqual(expect.arrayContaining([
      '节点 a 超时秒必须在 1-86400',
      '节点 b 重试次数必须在 0-5',
      '节点 key 重复: c',
      '节点 c 依赖不存在: missing'
    ]));
    expect(validateExecutionPlanDraft(draft).some((issue) => issue.message.startsWith('DAG 依赖存在环'))).toBe(true);
  });

  it('loads existing plan detail without replaying masked runtime secret digests', () => {
    const detail: ExecutionPlanDetail = {
      id: 'plan-1',
      projectId: 'project-alpha',
      name: 'Release smoke',
      status: 'READY',
      environmentKey: 'staging',
      nodeCount: 1,
      triggerPolicy: { manualEnabled: true },
      nodes: [{
        key: 'api-smoke',
        type: 'API_TEST',
        dependencies: ['setup'],
        inputSummary: {
          apiAutomationBundleId: 'bundle-1',
          baseUrlRef: 'env:staging',
          caseIds: ['case-1'],
          runtimeSecretRefs: { count: 1, digests: ['sha256:secret-digest'] }
        },
        failurePolicy: 'BLOCK_DOWNSTREAM',
        timeoutSeconds: 120,
        retryPolicy: { maxAttempts: '3' }
      }]
    };

    expect(executionPlanDraftFromDetail(detail)).toMatchObject({
      status: 'READY',
      nodes: [{
        key: 'api-smoke',
        dependenciesText: 'setup',
        apiAutomationBundleId: 'bundle-1',
        baseUrlRef: 'env:staging',
        caseIdsText: 'case-1',
        runtimeSecretRefsText: '',
        failurePolicy: 'BLOCK_DOWNSTREAM',
        maxAttempts: 3
      }]
    });
  });

  it('parses comma separated chips defensively', () => {
    expect(parseCommaSeparated(' a, ,b ,, c ')).toEqual(['a', 'b', 'c']);
  });
});
