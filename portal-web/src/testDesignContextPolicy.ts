import type {
  RequestTestDesignContextPolicyOverridePayload,
  TestDesignContextPolicyEffectiveView,
  TestDesignContextPolicyOverrideView
} from './api/testDesign';
import { translate } from './platform/i18n';

export const TEST_DESIGN_CONTEXT_POLICY_REASON_CODES = [
  'QUALITY_BASELINE',
  'PROJECT_COMPLEXITY',
  'REGULATED_CONTEXT',
  'PROMPT_BUDGET',
  'SMOKE_VALIDATION'
] as const;

export type TestDesignContextPolicyReasonCode = (typeof TEST_DESIGN_CONTEXT_POLICY_REASON_CODES)[number];

export const TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES = [
  'OPEN',
  'IN_REVIEW',
  'APPROVED',
  'REJECTED',
  'CANCELLED'
] as const;

export type TestDesignContextPolicyWorkOrderStatus = (typeof TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES)[number];

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
  policyBody: string;
  policyDiffSummary: string;
  workOrderKey: string;
  workOrderTitle: string;
  workOrderUrl: string;
  workOrderStatus: '' | TestDesignContextPolicyWorkOrderStatus;
  requestNote: string;
  reviewNote: string;
  noteType: 'COMMENT' | 'WORK_ORDER';
  noteText: string;
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
  'changeReasonCode' | 'policyBody' | 'policyDiffSummary' | 'workOrderKey' | 'workOrderTitle' | 'workOrderUrl' | 'requestNote'
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
  approvalReasonCode: 'SMOKE_VALIDATION',
  policyBody: '',
  policyDiffSummary: '',
  workOrderKey: '',
  workOrderTitle: '',
  workOrderUrl: '',
  workOrderStatus: '',
  requestNote: '',
  reviewNote: '',
  noteType: 'COMMENT',
  noteText: ''
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
    label: translate('auto.k0431')
  },
  {
    field: 'explicitAssetsPerType',
    payloadKey: 'contextExplicitAssetsPerType',
    label: translate('auto.k1393')
  },
  {
    field: 'existingCasesPerRequirement',
    payloadKey: 'contextExistingCasesPerRequirement',
    label: translate('auto.k1394')
  },
  {
    field: 'requirementDescriptionChars',
    payloadKey: 'contextRequirementDescriptionChars',
    label: translate('auto.k1395')
  },
  {
    field: 'acceptanceCriteriaChars',
    payloadKey: 'contextAcceptanceCriteriaChars',
    label: translate('auto.k1396')
  },
  {
    field: 'assetSchemaChars',
    payloadKey: 'contextAssetSchemaChars',
    label: translate('auto.k1397')
  }
];

const limitLabels: Record<string, string> = {
  linkedAssetsPerRequirement: translate('auto.k0431'),
  explicitAssetsPerType: translate('auto.k1393'),
  existingCasesPerRequirement: translate('auto.k1394'),
  requirementDescriptionChars: translate('auto.k1395'),
  acceptanceCriteriaChars: translate('auto.k1396'),
  linkedAssetSchemaChars: translate('auto.k1397')
};

export function validateTestDesignContextPolicyDraft(
  draft: TestDesignContextPolicyDraft
): TestDesignContextPolicyIssue[] {
  const issues: TestDesignContextPolicyIssue[] = [];
  if (!draft.projectId.trim()) {
    issues.push({ field: 'projectId', message: translate('auto.k1682') });
  }
  if (draft.scopeType === 'ENVIRONMENT' && !draft.environmentKey.trim()) {
    issues.push({ field: 'environmentKey', message: translate('auto.k2087') });
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
      issues.push({ field: config.field, message: translate('auto.k2088', { value0: config.label, value1: max }) });
    }
  }
  if (!hasLimit) {
    issues.push({ field: 'linkedAssetsPerRequirement', message: translate('auto.k2089') });
  }
  if (!TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.includes(draft.changeReasonCode)) {
    issues.push({ field: 'changeReasonCode', message: translate('auto.k2090') });
  }
  if (!TEST_DESIGN_CONTEXT_POLICY_REASON_CODES.includes(draft.approvalReasonCode)) {
    issues.push({ field: 'approvalReasonCode', message: translate('auto.k2091') });
  }
  validateTextField(issues, draft.policyBody, 'policyBody', translate('auto.k1401'), 4000);
  validateTextField(issues, draft.policyDiffSummary, 'policyDiffSummary', translate('auto.k1402'), 1000);
  validateTextField(issues, draft.workOrderKey, 'workOrderKey', translate('auto.k1398'), 128, /^[A-Za-z0-9_.:-]+$/);
  validateTextField(issues, draft.workOrderTitle, 'workOrderTitle', translate('auto.k1399'), 256);
  validateTextField(issues, draft.workOrderUrl, 'workOrderUrl', translate('auto.k1400'), 512, /^https?:\/\/\S+$/);
  if (draft.workOrderStatus && !TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES.includes(draft.workOrderStatus as TestDesignContextPolicyWorkOrderStatus)) {
    issues.push({ field: 'workOrderStatus', message: translate('auto.k2092') });
  }
  validateTextField(issues, draft.requestNote, 'requestNote', translate('auto.k1403'), 1000);
  validateTextField(issues, draft.reviewNote, 'reviewNote', translate('auto.k1412'), 1000);
  validateTextField(issues, draft.noteText, 'noteText', translate('auto.k1414'), 1000);
  return issues;
}

export function contextPolicyDraftFromOverride(
  override: TestDesignContextPolicyOverrideView,
  current: TestDesignContextPolicyDraft = initialTestDesignContextPolicyDraft
): TestDesignContextPolicyDraft {
  const limits = override.overrideLimits ?? {};
  return {
    ...current,
    projectId: override.projectId ?? current.projectId,
    environmentKey: override.environmentKey ?? '',
    scopeType: override.scopeType === 'ENVIRONMENT' ? 'ENVIRONMENT' : 'PROJECT',
    linkedAssetsPerRequirement: valueText(limits.linkedAssetsPerRequirement),
    explicitAssetsPerType: valueText(limits.explicitAssetsPerType),
    existingCasesPerRequirement: valueText(limits.existingCasesPerRequirement),
    requirementDescriptionChars: valueText(limits.requirementDescriptionChars),
    acceptanceCriteriaChars: valueText(limits.acceptanceCriteriaChars),
    assetSchemaChars: valueText(limits.linkedAssetSchemaChars ?? limits.assetSchemaChars),
    policyBody: override.policyBody ?? '',
    policyDiffSummary: override.policyDiffSummary ?? '',
    workOrderKey: override.workOrderKey ?? '',
    workOrderTitle: override.workOrderTitle ?? '',
    workOrderUrl: override.workOrderUrl ?? '',
    workOrderStatus: override.status === 'PENDING' ? '' : normalizeWorkOrderStatus(override.workOrderStatus),
    requestNote: override.requestNote ?? '',
    reviewNote: override.reviewNote ?? '',
    noteText: ''
  };
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
  assignText(payload, 'policyBody', draft.policyBody);
  assignText(payload, 'policyDiffSummary', draft.policyDiffSummary);
  assignText(payload, 'workOrderKey', draft.workOrderKey);
  assignText(payload, 'workOrderTitle', draft.workOrderTitle);
  assignText(payload, 'workOrderUrl', draft.workOrderUrl);
  assignText(payload, 'requestNote', draft.requestNote);
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

function validateTextField(
  issues: TestDesignContextPolicyIssue[],
  value: string,
  field: keyof TestDesignContextPolicyDraft,
  label: string,
  maxLength: number,
  pattern?: RegExp
) {
  const text = value.trim();
  if (!text) {
    return;
  }
  if (text.length > maxLength) {
    issues.push({ field, message: translate('auto.k2093', { value0: label, value1: maxLength }) });
  }
  if (pattern && !pattern.test(text)) {
    issues.push({ field, message: translate('auto.k2094', { value0: label }) });
  }
}

function assignText(
  payload: RequestTestDesignContextPolicyOverridePayload,
  key: keyof RequestTestDesignContextPolicyOverridePayload,
  value: string
) {
  const text = value.trim();
  if (text) {
    payload[key] = text as never;
  }
}

function valueText(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : '';
}

function normalizeWorkOrderStatus(value: unknown): TestDesignContextPolicyDraft['workOrderStatus'] {
  return typeof value === 'string' && TEST_DESIGN_CONTEXT_POLICY_WORK_ORDER_STATUSES.includes(value as TestDesignContextPolicyWorkOrderStatus)
    ? value as TestDesignContextPolicyWorkOrderStatus
    : '';
}

function countOverrideStatuses(overrides: readonly TestDesignContextPolicyOverrideView[]) {
  return overrides.reduce<Record<string, number>>((counts, item) => {
    counts[item.status] = (counts[item.status] ?? 0) + 1;
    return counts;
  }, {});
}
