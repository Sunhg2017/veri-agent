import type {
  RequestTestDesignContextPolicyOverridePayload,
  TestDesignContextPolicyEffectiveView,
  TestDesignContextPolicyOverrideView
} from './api/testDesign';

export const TEST_DESIGN_CONTEXT_POLICY_REASON_CODES = [
  'QUALITY_BASELINE',
  'PROJECT_COMPLEXITY',
  'REGULATED_CONTEXT',
  'PROMPT_BUDGET',
  'SMOKE_VALIDATION'
] as const;

export type TestDesignContextPolicyReasonCode = (typeof TEST_DESIGN_CONTEXT_POLICY_REASON_CODES)[number];

export type TestDesignContextPolicyDraft = {
  projectId: string;
  environmentKey: string;
  scopeType: 'PROJECT' | 'ENVIRONMENT';
  linkedAssetsPerRequirement: string;
  explicitAssetsPerType: string;
  existingCasesPerRequirement: string;
  requirementDescriptionChars: string;
  acceptanceCriteriaChars: string;
  assetSchemaChars: string;
  changeReasonCode: TestDesignContextPolicyReasonCode;
  approvalReasonCode: TestDesignContextPolicyReasonCode;
};

export type TestDesignContextPolicyIssue = {
  field: keyof TestDesignContextPolicyDraft;
  message: string;
};

export type TestDesignContextPolicySummary = {
  scopeLabel: string;
  limitSummary: string;
  statusSummary: string;
  redLineSummary: string;
};

type TestDesignContextPolicyNumericPayloadKey = Exclude<
  keyof RequestTestDesignContextPolicyOverridePayload,
  'changeReasonCode'
>;

export const initialTestDesignContextPolicyDraft: TestDesignContextPolicyDraft = {
  projectId: '',
  environmentKey: '',
  scopeType: 'PROJECT',
  linkedAssetsPerRequirement: '',
  explicitAssetsPerType: '',
  existingCasesPerRequirement: '',
  requirementDescriptionChars: '',
  acceptanceCriteriaChars: '',
  assetSchemaChars: '',
  changeReasonCode: 'QUALITY_BASELINE',
  approvalReasonCode: 'SMOKE_VALIDATION'
};

const itemFields = new Set<keyof TestDesignContextPolicyDraft>([
  'linkedAssetsPerRequirement',
  'explicitAssetsPerType',
  'existingCasesPerRequirement'
]);

const numericFields: Array<{
  field: keyof TestDesignContextPolicyDraft;
  payloadKey: TestDesignContextPolicyNumericPayloadKey;
  label: string;
}> = [
  {
    field: 'linkedAssetsPerRequirement',
    payloadKey: 'contextLinkedAssetsPerRequirement',
    label: '关联资产'
  },
  {
    field: 'explicitAssetsPerType',
    payloadKey: 'contextExplicitAssetsPerType',
    label: '显式资产'
  },
  {
    field: 'existingCasesPerRequirement',
    payloadKey: 'contextExistingCasesPerRequirement',
    label: '历史用例'
  },
  {
    field: 'requirementDescriptionChars',
    payloadKey: 'contextRequirementDescriptionChars',
    label: '需求摘要'
  },
  {
    field: 'acceptanceCriteriaChars',
    payloadKey: 'contextAcceptanceCriteriaChars',
    label: '验收摘要'
  },
  {
    field: 'assetSchemaChars',
    payloadKey: 'contextAssetSchemaChars',
    label: '资产摘要'
  }
];

const limitLabels: Record<string, string> = {
  linkedAssetsPerRequirement: '关联资产',
  explicitAssetsPerType: '显式资产',
  existingCasesPerRequirement: '历史用例',
  requirementDescriptionChars: '需求摘要',
  acceptanceCriteriaChars: '验收摘要',
  linkedAssetSchemaChars: '资产摘要'
};

export function validateTestDesignContextPolicyDraft(
  draft: TestDesignContextPolicyDraft
): TestDesignContextPolicyIssue[] {
  const issues: TestDesignContextPolicyIssue[] = [];
  if (!draft.projectId.trim()) {
    issues.push({ field: 'projectId', message: '请输入项目 ID' });
  }
  if (draft.scopeType === 'ENVIRONMENT' && !draft.environmentKey.trim()) {
    issues.push({ field: 'environmentKey', message: '环境级覆盖需要环境键' });
  }
  let hasLimit = false;
  for (const config of numericFields) {
    const raw = String(draft[config.field]).trim();
    if (!raw) {
      continue;
    }
    hasLimit = true;
    const value = Number(raw);
    const max = itemFields.has(config.field) ? 50 : 2000;
    if (!Number.isInteger(value) || value < 1 || value > max) {
      issues.push({ field: config.field, message: `${config.label}必须为 1..${max} 的整数` });
    }
  }
  if (!hasLimit) {
    issues.push({ field: 'linkedAssetsPerRequirement', message: '至少填写一个上下文裁剪上限' });
  }
  if (!TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.includes(draft.changeReasonCode)) {
    issues.push({ field: 'changeReasonCode', message: '请选择允许的变更原因编码' });
  }
  if (!TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.includes(draft.approvalReasonCode)) {
    issues.push({ field: 'approvalReasonCode', message: '请选择允许的审批原因编码' });
  }
  return issues;
}

export function buildTestDesignContextPolicyPayload(
  draft: TestDesignContextPolicyDraft
): RequestTestDesignContextPolicyOverridePayload {
  const payload: RequestTestDesignContextPolicyOverridePayload = {
    changeReasonCode: draft.changeReasonCode
  };
  for (const config of numericFields) {
    const raw = String(draft[config.field]).trim();
    if (!raw) {
      continue;
    }
    const value = Number(raw);
    if (Number.isInteger(value)) {
      payload[config.payloadKey] = value;
    }
  }
  return payload;
}

export function buildTestDesignContextPolicySummary(
  effective: TestDesignContextPolicyEffectiveView | null,
  overrides: readonly TestDesignContextPolicyOverrideView[]
): TestDesignContextPolicySummary {
  const applied = effective?.appliedOverrideScopes.length
    ? effective.appliedOverrideScopes.join(' -> ')
    : 'PLATFORM_DEFAULT';
  const limits = effective?.contextLimits ?? {};
  const limitSummary = Object.entries(limits)
    .map(([key, value]) => `${limitLabels[key] ?? key} ${value}`)
    .join(' · ') || '-';
  const statusCounts = effective?.overrideStatusCounts ?? countOverrideStatuses(overrides);
  const statusSummary = ['PENDING', 'APPROVED', 'REJECTED']
    .map((status) => `${status} ${statusCounts[status] ?? 0}`)
    .join(' · ');
  const redLineSummary = effective
    ? `aggregateOnly=${String(effective.aggregateOnly ?? false)} · body=${String(Boolean(effective.policyBodyExported))} · diff=${String(Boolean(effective.policyDiffPreviewExported))} · notes=${String(Boolean(effective.approvalNotesExported))} · ticket=${String(Boolean(effective.ticketUrlExported))}`
    : 'aggregateOnly=true · body=false · diff=false · notes=false · ticket=false';
  return {
    scopeLabel: `${effective?.projectId ?? '-'}${effective?.environmentKey ? ` / ${effective.environmentKey}` : ''} · ${applied}`,
    limitSummary,
    statusSummary,
    redLineSummary
  };
}

function countOverrideStatuses(overrides: readonly TestDesignContextPolicyOverrideView[]) {
  return overrides.reduce<Record<string, number>>((counts, item) => {
    counts[item.status] = (counts[item.status] ?? 0) + 1;
    return counts;
  }, {});
}
