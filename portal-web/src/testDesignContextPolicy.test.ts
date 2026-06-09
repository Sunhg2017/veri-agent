import { describe, expect, it } from 'vitest';
import {
  buildTestDesignContextPolicyPayload,
  buildTestDesignContextPolicySummary,
  contextPolicyDraftFromOverride,
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

  it('builds an override payload with bounded limits, work order metadata and policy body', () => {
    const payload = buildTestDesignContextPolicyPayload({
      ...initialTestDesignContextPolicyDraft,
      projectId: 'project-1',
      linkedAssetsPerRequirement: '4',
      explicitAssetsPerType: '',
      existingCasesPerRequirement: '2',
      requirementDescriptionChars: '160',
      acceptanceCriteriaChars: '',
      assetSchemaChars: '180',
      changeReasonCode: 'PROJECT_COMPLEXITY',
      policyBody: 'qa policy body',
      policyDiffSummary: 'raise baseline',
      workOrderKey: 'WP5-CTX-1',
      workOrderTitle: 'QA policy approval',
      workOrderUrl: 'https://ticket.example/wp5/ctx-1',
      requestNote: 'please review'
    });

    expect(payload).toEqual({
      contextLinkedAssetsPerRequirement: 4,
      contextExistingCasesPerRequirement: 2,
      contextRequirementDescriptionChars: 160,
      contextAssetSchemaChars: 180,
      changeReasonCode: 'PROJECT_COMPLEXITY',
      policyBody: 'qa policy body',
      policyDiffSummary: 'raise baseline',
      workOrderKey: 'WP5-CTX-1',
      workOrderTitle: 'QA policy approval',
      workOrderUrl: 'https://ticket.example/wp5/ctx-1',
      requestNote: 'please review'
    });
  });

  it('validates work order metadata and review note bounds', () => {
    expect(validateTestDesignContextPolicyDraft({
      ...initialTestDesignContextPolicyDraft,
      projectId: 'project-1',
      linkedAssetsPerRequirement: '4',
      workOrderKey: 'bad key',
      workOrderUrl: 'ftp://ticket.example/wp5/ctx-1',
      reviewNote: 'x'.repeat(1001)
    })).toEqual([
      { field: 'workOrderKey', message: '工单编号格式不正确' },
      { field: 'workOrderUrl', message: '工单 URL格式不正确' },
      { field: 'reviewNote', message: '审批备注不能超过 1000 字符' }
    ]);
  });

  it('loads an override back into the editable draft without carrying transient note text', () => {
    const draft = contextPolicyDraftFromOverride({
      id: 'override-1',
      scopeType: 'ENVIRONMENT',
      projectId: 'project-1',
      environmentKey: 'qa',
      status: 'PENDING',
      overrideLimits: {
        linkedAssetsPerRequirement: 4,
        explicitAssetsPerType: 2,
        linkedAssetSchemaChars: 180
      },
      changeReasonCodeCaptured: true,
      approvalReasonCodeCaptured: false,
      workOrderKey: 'WP5-CTX-1',
      workOrderTitle: 'QA policy approval',
      workOrderUrl: 'https://ticket.example/wp5/ctx-1',
      workOrderStatus: 'IN_REVIEW',
      policyBody: 'qa policy body',
      policyDiffSummary: 'raise baseline',
      requestNote: 'please review'
    }, {
      ...initialTestDesignContextPolicyDraft,
      noteText: 'stale note'
    });

    expect(draft).toMatchObject({
      projectId: 'project-1',
      environmentKey: 'qa',
      scopeType: 'ENVIRONMENT',
      linkedAssetsPerRequirement: '4',
      explicitAssetsPerType: '2',
      assetSchemaChars: '180',
      workOrderStatus: '',
      policyBody: 'qa policy body',
      policyDiffSummary: 'raise baseline',
      requestNote: 'please review',
      noteText: ''
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
