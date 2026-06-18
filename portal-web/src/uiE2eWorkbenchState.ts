import type {
  CreateUiE2eRunPayload,
  CreateUiE2eScenePayload,
  UiE2eSceneDetail,
  UiE2eSceneStepPayload,
  UpdateUiE2eScenePayload,
  UpsertUiE2eFlakyMarkPayload
} from './api/uiE2e';

export type UiE2eSceneStepDraft = {
  stepType: string;
  actionSummaryText: string;
  locatorStrategyText: string;
  assertionSummaryText: string;
  waitPolicyText: string;
};

export type UiE2eSceneDraft = {
  projectId: string;
  applicationId: string;
  environmentId: string;
  code: string;
  name: string;
  status: string;
  riskLevel: string;
  tagsText: string;
  sourceSummaryText: string;
  steps: UiE2eSceneStepDraft[];
};

export type UiE2eRunDraft = {
  projectId: string;
  sceneId: string;
  bundleId: string;
  environmentId: string;
  baseUrlRef: string;
  accountLeaseRef: string;
  requestKey: string;
  reason: string;
};

export type UiE2eFlakyDraft = {
  projectId: string;
  sceneId: string;
  runId: string;
  status: string;
  reasonCode: string;
  reasonSummary: string;
};

export const initialUiE2eSceneStepDraft: UiE2eSceneStepDraft = {
  stepType: 'LOGIN',
  actionSummaryText: '{"submitAction":"click"}',
  locatorStrategyText: '{"preferred":"testId"}',
  assertionSummaryText: '{"successSignal":"url contains /dashboard"}',
  waitPolicyText: '{"timeoutSeconds":5}'
};

export const initialUiE2eSceneDraft: UiE2eSceneDraft = {
  projectId: '',
  applicationId: '',
  environmentId: '',
  code: '',
  name: '',
  status: 'DRAFT',
  riskLevel: 'MEDIUM',
  tagsText: '',
  sourceSummaryText: '{}',
  steps: [{ ...initialUiE2eSceneStepDraft }]
};

export function blankUiE2eSceneDraft(defaults: Partial<Pick<UiE2eSceneDraft, 'projectId' | 'applicationId' | 'environmentId'>> = {}): UiE2eSceneDraft {
  return {
    ...initialUiE2eSceneDraft,
    projectId: defaults.projectId || '',
    applicationId: defaults.applicationId || '',
    environmentId: defaults.environmentId || '',
    steps: [{ ...initialUiE2eSceneStepDraft }]
  };
}

export const initialUiE2eRunDraft: UiE2eRunDraft = {
  projectId: '',
  sceneId: '',
  bundleId: '',
  environmentId: '',
  baseUrlRef: 'env:staging',
  accountLeaseRef: '',
  requestKey: '',
  reason: ''
};

export const initialUiE2eFlakyDraft: UiE2eFlakyDraft = {
  projectId: '',
  sceneId: '',
  runId: '',
  status: 'FLAKY_CANDIDATE',
  reasonCode: '',
  reasonSummary: ''
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const requestKeyPattern = /^[A-Za-z0-9_.:-]{1,128}$/;

export function buildUiE2eScenePayload(draft: UiE2eSceneDraft): { payload?: CreateUiE2eScenePayload; issues: string[] } {
  const { payload: partialPayload, issues } = buildUiE2eScenePayloadBase(draft);
  if (!draft.projectId.trim()) issues.push('请填写 scene projectId');
  if (!draft.code.trim()) issues.push('请填写 scene code');

  if (!partialPayload || issues.length) {
    return { issues };
  }

  return {
    issues,
    payload: {
      projectId: draft.projectId.trim(),
      applicationId: partialPayload.applicationId,
      environmentId: partialPayload.environmentId,
      code: draft.code.trim(),
      name: partialPayload.name,
      status: partialPayload.status,
      riskLevel: partialPayload.riskLevel,
      tags: partialPayload.tags,
      sourceSummary: partialPayload.sourceSummary,
      steps: partialPayload.steps
    }
  };
}

export function buildUiE2eSceneUpdatePayload(draft: UiE2eSceneDraft): { payload?: UpdateUiE2eScenePayload; issues: string[] } {
  const { payload, issues } = buildUiE2eScenePayloadBase(draft);
  if (!payload || issues.length) {
    return { issues };
  }

  return { issues, payload };
}

export function sceneDraftFromDetail(detail: Pick<
  UiE2eSceneDetail,
  'projectId' | 'applicationId' | 'environmentId' | 'code' | 'name' | 'status' | 'riskLevel' | 'tags' | 'sourceSummary' | 'steps'
>): UiE2eSceneDraft {
  return {
    projectId: detail.projectId,
    applicationId: detail.applicationId || '',
    environmentId: detail.environmentId || '',
    code: detail.code,
    name: detail.name,
    status: detail.status,
    riskLevel: detail.riskLevel,
    tagsText: detail.tags.join(' '),
    sourceSummaryText: prettyJson(detail.sourceSummary),
    steps: detail.steps.length
      ? detail.steps.map((step) => ({
          stepType: step.stepType,
          actionSummaryText: prettyJson(step.actionSummary),
          locatorStrategyText: prettyJson(step.locatorStrategy),
          assertionSummaryText: prettyJson(step.assertionSummary),
          waitPolicyText: prettyJson(step.waitPolicy)
        }))
      : [{ ...initialUiE2eSceneStepDraft }]
  };
}

function buildUiE2eScenePayloadBase(
  draft: UiE2eSceneDraft
): { payload?: Omit<CreateUiE2eScenePayload, 'projectId' | 'code'>; issues: string[] } {
  const issues: string[] = [];
  if (!draft.name.trim()) issues.push('请填写 scene name');
  if (!draft.steps.length) issues.push('至少保留一个步骤');

  const sourceSummary = parseObjectText(draft.sourceSummaryText, 'sourceSummary', issues);
  const steps = draft.steps
    .map((step, index) => buildUiE2eSceneStepPayload(step, index, issues))
    .filter((value): value is UiE2eSceneStepPayload => Boolean(value));

  if (steps.length === 0) {
    issues.push('至少提供一个合法步骤');
  }

  if (issues.length) {
    return { issues };
  }

  return {
    issues,
    payload: {
      applicationId: optionalText(draft.applicationId),
      environmentId: optionalText(draft.environmentId),
      name: draft.name.trim(),
      status: optionalText(draft.status),
      riskLevel: optionalText(draft.riskLevel),
      tags: splitTags(draft.tagsText),
      sourceSummary,
      steps
    }
  };
}

export function buildUiE2eRunPayload(draft: UiE2eRunDraft): { payload?: CreateUiE2eRunPayload; issues: string[] } {
  const issues: string[] = [];
  if (!draft.projectId.trim()) issues.push('请填写 run projectId');
  if (!uuidPattern.test(draft.sceneId.trim())) issues.push('sceneId 需要是 UUID');
  if (!uuidPattern.test(draft.bundleId.trim())) issues.push('bundleId 需要是 UUID');
  if (!draft.baseUrlRef.trim()) issues.push('请填写 baseUrlRef');
  if (!uuidPattern.test(draft.accountLeaseRef.trim())) issues.push('accountLeaseRef 需要是 UUID');
  if (draft.requestKey.trim() && !requestKeyPattern.test(draft.requestKey.trim())) {
    issues.push('requestKey 只能包含字母、数字、-、_、.、:，且不超过 128 字符');
  }
  if (draft.reason.length > 512) issues.push('reason 最多 512 字符');

  if (issues.length) {
    return { issues };
  }

  return {
    issues,
    payload: {
      projectId: draft.projectId.trim(),
      sceneId: draft.sceneId.trim(),
      bundleId: draft.bundleId.trim(),
      environmentId: optionalText(draft.environmentId),
      baseUrlRef: draft.baseUrlRef.trim(),
      accountLeaseRef: draft.accountLeaseRef.trim(),
      requestKey: optionalText(draft.requestKey),
      reason: optionalText(draft.reason)
    }
  };
}

export function buildUiE2eFlakyPayload(draft: UiE2eFlakyDraft): { payload?: UpsertUiE2eFlakyMarkPayload; issues: string[] } {
  const issues: string[] = [];
  if (!draft.projectId.trim()) issues.push('请填写 flaky projectId');
  if (!draft.sceneId.trim() && !draft.runId.trim()) issues.push('sceneId 和 runId 至少填写一个');
  if (draft.sceneId.trim() && !uuidPattern.test(draft.sceneId.trim())) issues.push('sceneId 需要是 UUID');
  if (draft.runId.trim() && !uuidPattern.test(draft.runId.trim())) issues.push('runId 需要是 UUID');
  if (!draft.status.trim()) issues.push('请选择 flaky status');
  if (draft.reasonSummary.length > 512) issues.push('reasonSummary 最多 512 字符');

  if (issues.length) {
    return { issues };
  }

  return {
    issues,
    payload: {
      projectId: draft.projectId.trim(),
      sceneId: optionalText(draft.sceneId),
      runId: optionalText(draft.runId),
      status: draft.status.trim(),
      reasonCode: optionalText(draft.reasonCode),
      reasonSummary: optionalText(draft.reasonSummary)
    }
  };
}

export function splitTags(input: string) {
  return input
    .split(/[\s,，]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function prettyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

function buildUiE2eSceneStepPayload(
  step: UiE2eSceneStepDraft,
  index: number,
  issues: string[]
): UiE2eSceneStepPayload | undefined {
  if (!step.stepType.trim()) {
    issues.push(`步骤 ${index + 1} 缺少 stepType`);
    return undefined;
  }
  return {
    stepType: step.stepType.trim(),
    actionSummary: parseObjectText(step.actionSummaryText, `steps[${index}].actionSummary`, issues),
    locatorStrategy: parseObjectText(step.locatorStrategyText, `steps[${index}].locatorStrategy`, issues),
    assertionSummary: parseObjectText(step.assertionSummaryText, `steps[${index}].assertionSummary`, issues),
    waitPolicy: parseObjectText(step.waitPolicyText, `steps[${index}].waitPolicy`, issues)
  };
}

function parseObjectText(text: string, label: string, issues: string[]) {
  const value = text.trim();
  if (!value) {
    return {};
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    issues.push(`${label} 必须是 JSON object`);
    return {};
  } catch {
    issues.push(`${label} 不是合法 JSON`);
    return {};
  }
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}
