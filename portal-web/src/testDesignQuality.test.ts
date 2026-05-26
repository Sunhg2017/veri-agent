import { describe, expect, it } from 'vitest';
import { validateTestDesignCandidateDraft } from './testDesignQuality';

const validDraft = {
  title: '验证账号密码登录成功',
  description: '覆盖标准登录链路',
  coverageType: 'SMOKE',
  priority: 'HIGH',
  preconditions: '账号已激活',
  steps: '输入有效账号和密码 => 登录按钮可点击\n点击登录 => 进入系统概览',
  expectedResult: '用户进入系统概览',
  tags: 'login, smoke'
};

describe('WP5 candidate draft quality validation', () => {
  it('accepts a complete candidate draft', () => {
    expect(validateTestDesignCandidateDraft(validDraft)).toEqual([]);
  });

  it('reports blocking field-level issues before save', () => {
    const issues = validateTestDesignCandidateDraft({
      ...validDraft,
      title: ' ',
      steps: '点击登录',
      expectedResult: ''
    });

    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      '标题不能为空',
      '预期结果不能为空',
      '步骤至少需要 2 步',
      '第 1 步缺少预期结果，请使用“操作 => 期望”格式'
    ]));
    expect(issues.every((issue) => issue.severity === 'error')).toBe(true);
  });

  it('matches backend quality gate limits and enums', () => {
    const issues = validateTestDesignCandidateDraft({
      ...validDraft,
      title: 'x'.repeat(161),
      coverageType: 'UNKNOWN',
      priority: 'URGENT',
      steps: Array.from({ length: 13 }, (_, index) => `操作${index} => 期望${index}`).join('\n')
    });

    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      '标题长度不能超过 160',
      '覆盖类型不支持：UNKNOWN',
      '优先级不支持：URGENT',
      '步骤最多支持 12 步'
    ]));
  });

  it('flags obvious sensitive text in visible candidate fields', () => {
    const issues = validateTestDesignCandidateDraft({
      ...validDraft,
      expectedResult: '不得回显 token=wp5_secret_123456789',
      steps: '输入账号 => 请求不包含 Authorization: Bearer abc.def\n点击登录 => 返回成功'
    });

    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      '预期结果包含疑似敏感信息',
      '步骤包含疑似敏感信息'
    ]));
  });

  it('flags duplicate titles under the same requirement and coverage type', () => {
    const issues = validateTestDesignCandidateDraft(validDraft, {
      currentCandidateId: 'candidate-1',
      currentRequirementId: 'requirement-1',
      peerCandidates: [
        { id: 'candidate-1', requirementId: 'requirement-1', coverageType: 'SMOKE', title: validDraft.title },
        { id: 'candidate-2', requirementId: 'requirement-1', coverageType: 'SMOKE', title: '验证账号 密码 登录成功' }
      ]
    });

    expect(issues).toContainEqual({
      field: 'title',
      severity: 'error',
      message: '同需求同覆盖类型下已存在相同候选标题'
    });
  });
});
