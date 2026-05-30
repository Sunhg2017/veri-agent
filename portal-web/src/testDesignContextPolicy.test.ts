import { describe, expect, it } from 'vitest';
import {
  buildTestDesignContextPolicyPayload,
  buildTestDesignContextPolicySummary,
  initialTestDesignContextPolicyDraft,
  validateTestDesignContextPolicyDraft
} from './testDesignContextPolicy';

describe('WP5 context policy operations helpers', () => {
  it('validates required scope and bounded numeric limits', () => {
    expect(validateTestDesignContextPolicyDraft(initialTestDesignContextPolicyDraft)).toEqual([
      { field: 'projectId', message: '请输入项目 ID' },
      { field: 'linkedAssetsPerRequirement', message: '至少填写一个上下文裁剪上限' }
    ]);

    const issues = validateTestDesignContextPolicyDraft({
      ...initialTestDesignContextPolicyDraft,
      projectId: 'project-1',
      scopeType: 'ENVIRONMENT',
      linkedAssetsPerRequirement: '51',
      requirementDescriptionChars: '2001'
    });

    expect(issues).toEqual([
      { field: 'environmentKey', message: '环境级覆盖需要环境键' },
      { field: 'linkedAssetsPerRequirement', message: '关联资产必须为 1..50 的整数' },
      { field: 'requirementDescriptionChars', message: '需求摘要必须为 1..2000 的整数' }
    ]);
  });

  it('builds a sanitized override payload with only bounded numeric values and reason code', () => {
    const payload = buildTestDesignContextPolicyPayload({
      ...initialTestDesignContextPolicyDraft,
      projectId: 'project-1',
      linkedAssetsPerRequirement: '4',
      explicitAssetsPerType: '',
      existingCasesPerRequirement: '2',
      requirementDescriptionChars: '160',
      acceptanceCriteriaChars: '',
      assetSchemaChars: '180',
      changeReasonCode: 'PROJECT_COMPLEXITY'
    });

    expect(payload).toEqual({
      contextLinkedAssetsPerRequirement: 4,
      contextExistingCasesPerRequirement: 2,
      contextRequirementDescriptionChars: 160,
      contextAssetSchemaChars: 180,
      changeReasonCode: 'PROJECT_COMPLEXITY'
    });
  });

  it('summarizes effective policy without exposing policy body, diff, notes or ticket fields', () => {
    const summary = buildTestDesignContextPolicySummary(
      {
        projectId: 'project-1',
        environmentKey: 'qa',
        contextLimits: {
          linkedAssetsPerRequirement: 4,
          explicitAssetsPerType: 2,
          linkedAssetSchemaChars: 180
        },
        appliedOverrideScopes: ['PLATFORM_DEFAULT', 'PROJECT', 'ENVIRONMENT'],
        overrideStatusCounts: { PENDING: 1, APPROVED: 2 },
        policyBodyExported: false,
        policyDiffPreviewExported: false,
        approvalNotesExported: false,
        ticketUrlExported: false,
        aggregateOnly: true
      },
      []
    );

    expect(summary).toEqual({
      scopeLabel: 'project-1 / qa · PLATFORM_DEFAULT -> PROJECT -> ENVIRONMENT',
      limitSummary: '关联资产 4 · 显式资产 2 · 资产摘要 180',
      statusSummary: 'PENDING 1 · APPROVED 2 · REJECTED 0',
      redLineSummary: 'aggregateOnly=true · body=false · diff=false · notes=false · ticket=false'
    });
  });
});
