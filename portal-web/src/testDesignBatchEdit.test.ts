import { describe, expect, it } from 'vitest';
import {
  buildTestDesignBatchEditPayload,
  hasTestDesignBatchEditChanges,
  initialTestDesignBatchEditDraft,
  selectedTestDesignBatchEditableCandidates,
  testDesignBatchEditFieldLabels,
  validateTestDesignBatchEditDraft
} from './testDesignBatchEdit';
import type { TestDesignCandidateView } from './api/testDesign';

const candidate: TestDesignCandidateView = {
  id: 'candidate-1',
  taskId: 'task-1',
  projectId: 'project-1',
  requirementId: 'requirement-1',
  title: '验证登录成功',
  description: '标准登录链路',
  apiId: '11111111-1111-1111-1111-111111111111',
  coverageType: 'SMOKE',
  priority: 'HIGH',
  status: 'GENERATED',
  preconditions: '账号已激活',
  steps: [
    { stepOrder: 1, action: '输入账号密码', expectedResult: '登录按钮可点击' },
    { stepOrder: 2, action: '点击登录', expectedResult: '进入首页' }
  ],
  expectedResult: '用户登录成功',
  tags: ['login', 'smoke'],
  version: 7
};

describe('WP5 batch candidate field edit helpers', () => {
  it('selects only chosen candidates that are still editable for review', () => {
    const selected = selectedTestDesignBatchEditableCandidates([
      candidate,
      { ...candidate, id: 'candidate-2', status: 'EDITED' },
      { ...candidate, id: 'candidate-3', status: 'CONFIRMED' }
    ], ['candidate-1', 'candidate-2', 'candidate-3']);

    expect(selected.map((item) => item.id)).toEqual(['candidate-1', 'candidate-2']);
  });

  it('builds full update payloads while appending tags without duplicates', () => {
    const payload = buildTestDesignBatchEditPayload(candidate, {
      coverageType: 'BOUNDARY',
      priority: 'LOW',
      tags: 'regression, Login',
      tagMode: 'append'
    });

    expect(payload).toMatchObject({
      title: '验证登录成功',
      coverageType: 'BOUNDARY',
      priority: 'LOW',
      preconditions: '账号已激活',
      expectedResult: '用户登录成功',
      tags: ['login', 'smoke', 'regression'],
      version: 7
    });
    expect(payload.steps).toEqual([
      { action: '输入账号密码', expectedResult: '登录按钮可点击' },
      { action: '点击登录', expectedResult: '进入首页' }
    ]);
  });

  it('supports replacing tags and describing changed fields', () => {
    const draft = { ...initialTestDesignBatchEditDraft, priority: 'MEDIUM', tags: 'wp5\nreview', tagMode: 'replace' as const };

    expect(hasTestDesignBatchEditChanges(draft)).toBe(true);
    expect(buildTestDesignBatchEditPayload(candidate, draft).tags).toEqual(['wp5', 'review']);
    expect(testDesignBatchEditFieldLabels(draft)).toEqual(['优先级=MEDIUM', '替换标签=wp5, review']);
  });

  it('validates enum and sensitive tag input before applying changes', () => {
    const issues = validateTestDesignBatchEditDraft({
      coverageType: 'UNKNOWN',
      priority: 'URGENT',
      tags: 'token=secret-value',
      tagMode: 'append'
    });

    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      '覆盖类型不支持：UNKNOWN',
      '优先级不支持：URGENT',
      '标签包含疑似敏感信息'
    ]));
  });
});
