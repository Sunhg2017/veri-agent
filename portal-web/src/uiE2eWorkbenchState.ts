import type {
  CreateUiE2eRunPayload,
  CreateUiE2eScenePayload,
  UiE2eBundleSummary,
  UiE2eFlakyMark,
  UiE2eHealth,
  UiE2eRunDetail,
  UiE2eRunSummary,
  UiE2eRunStepResult,
  UiE2eSceneSummary,
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

export type UiE2eWorkbenchTone = 'success' | 'info' | 'warning' | 'danger';
export type UiE2eWorkbenchNoticeTone = 'success' | 'info' | 'warning' | 'error';

export type UiE2eWorkbenchNotice = {
  tone: UiE2eWorkbenchNoticeTone;
  message: string;
};

export type UiE2eWorkbenchOverview = {
  approvedScenes: number;
  reviewingBundles: number;
  activeRuns: number;
  recentFailures: number;
  blockedRuns: number;
  confirmedFlaky: number;
  runnerLabel: string;
  runnerTone: UiE2eWorkbenchTone;
  allowlistLabel: string;
  allowlistTone: UiE2eWorkbenchTone;
  notices: UiE2eWorkbenchNotice[];
};

export type UiE2eRunDiagnosis = {
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  primaryFailureBucket?: string;
  blockedArtifactCount: number;
  rawArtifactDownloadReady: boolean;
  signals: string[];
  nextActions: string[];
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

export function isUiE2eRunActiveStatus(status?: string) {
  return status === 'QUEUED' || status === 'RUNNING';
}

export function buildUiE2eWorkbenchOverview(
  health: UiE2eHealth | null,
  scenes: UiE2eSceneSummary[],
  bundles: UiE2eBundleSummary[],
  runs: UiE2eRunSummary[],
  flakyMarks: UiE2eFlakyMark[]
): UiE2eWorkbenchOverview {
  const approvedScenes = scenes.filter((scene) => scene.status === 'APPROVED').length;
  const reviewingBundles = bundles.filter((bundle) => bundle.status === 'REVIEWING').length;
  const activeRuns = runs.filter((run) => isUiE2eRunActiveStatus(run.status)).length;
  const recentFailures = runs.filter((run) => ['FAILED', 'TIMEOUT'].includes(run.status)).length;
  const blockedRuns = runs.filter((run) => run.status === 'BLOCKED').length;
  const confirmedFlaky = flakyMarks.filter((mark) => mark.status === 'CONFIRMED_FLAKY').length;

  const notices: UiE2eWorkbenchNotice[] = [];
  if (health?.status && health.status !== 'UP') {
    notices.push({ tone: 'warning', message: `健康状态为 ${health.status}，请先确认控制面服务可用性。` });
  }
  if (health && !health.runnerEnabled) {
    notices.push({ tone: 'warning', message: '当前 runner 默认关闭，手动创建运行会返回 BLOCKED 摘要，用于验证控制面与权限链路。' });
  }
  if (health && !health.allowlistEnabled) {
    notices.push({ tone: 'warning', message: 'baseUrl allowlist 当前关闭，发布前应确认受控目标范围已经收口。' });
  }
  if (recentFailures > 0) {
    notices.push({ tone: 'warning', message: `最近列表中有 ${recentFailures} 条 FAILED/TIMEOUT 运行，建议优先查看 failureCode 和 traceId。` });
  }
  if (blockedRuns > 0) {
    notices.push({ tone: 'info', message: `最近列表中有 ${blockedRuns} 条 BLOCKED 运行，通常需要复核 runner、租借或审批状态。` });
  }
  if (confirmedFlaky > 0) {
    notices.push({ tone: 'info', message: `当前共有 ${confirmedFlaky} 条 CONFIRMED_FLAKY 标记，可作为后续诊断和治理输入。` });
  }

  const runnerLabel = health ? `${health.runnerEnabled ? 'ON' : 'OFF'} · ${health.runnerMode || 'UNKNOWN'}` : '等待加载';
  const runnerTone: UiE2eWorkbenchTone = !health
    ? 'info'
    : health.status !== 'UP'
      ? 'danger'
      : health.runnerEnabled
        ? 'success'
        : 'warning';
  const allowlistLabel = health ? (health.allowlistEnabled ? `ON (${health.allowlistHostCount})` : 'OFF') : '等待加载';
  const allowlistTone: UiE2eWorkbenchTone = !health ? 'info' : health.allowlistEnabled ? 'success' : 'warning';

  return {
    approvedScenes,
    reviewingBundles,
    activeRuns,
    recentFailures,
    blockedRuns,
    confirmedFlaky,
    runnerLabel,
    runnerTone,
    allowlistLabel,
    allowlistTone,
    notices
  };
}

export function buildUiE2eRunDiagnosis(detail: UiE2eRunDetail): UiE2eRunDiagnosis {
  const executionSummary = detail.executionSummary || {};
  const failureBucketCounts = numberRecord(executionSummary.failureBucketCounts);
  const primaryFailureBucketEntry = sortedCountEntries(failureBucketCounts)[0];
  const primaryFailureBucket = primaryFailureBucketEntry ? `${primaryFailureBucketEntry[0]} x${primaryFailureBucketEntry[1]}` : undefined;
  const blockedArtifacts = detail.artifacts.filter((artifact) => artifact.captureStatus === 'BLOCKED');
  const blockedArtifactReasons = uniqueStrings(
    blockedArtifacts.map((artifact) => extractUiE2eArtifactCaptureBlockedReason(artifact.redactionFlags))
  );
  const rawArtifactDownloadReady = booleanFromUnknown(executionSummary.rawArtifactDownloadReady);
  const signals: string[] = [];
  const nextActions: string[] = [];
  const flakyStatus = detail.flakyMark?.status || detail.flakyStatus;
  const failureCodeDiagnosis = buildFailureCodeDiagnosis(detail.failureCode);
  const stepResultCount = numberFromUnknown(executionSummary.stepResultCount, detail.stepResults.length);

  if (detail.failureCode) {
    pushUnique(signals, `failureCode=${detail.failureCode}`);
  }
  if (detail.failureSummary) {
    pushUnique(signals, `failureSummary=${detail.failureSummary}`);
  }
  if (primaryFailureBucketEntry) {
    pushUnique(
      signals,
      `failureBucketCounts=${sortedCountEntries(failureBucketCounts).map(([bucket, count]) => `${bucket} x${count}`).join(', ')}`
    );
  }
  if (blockedArtifacts.length) {
    pushUnique(
      signals,
      `artifactCaptureBlocked=${blockedArtifacts.length}${blockedArtifactReasons.length ? ` (${blockedArtifactReasons.join(', ')})` : ''}`
    );
  }
  if (flakyStatus === 'CONFIRMED_FLAKY') {
    pushUnique(signals, 'flakyStatus=CONFIRMED_FLAKY');
  }
  if (booleanFromUnknown(executionSummary.aggregateOnly)) {
    pushUnique(signals, 'aggregateOnly=true，当前详情不包含 secretRef 明文与原始 artifact 正文');
  }
  if (!rawArtifactDownloadReady && detail.artifacts.length > 0) {
    pushUnique(signals, 'rawArtifactDownloadReady=false，artifact 仅提供 manifest 摘要');
  }
  if (stepResultCount === 0 && !isUiE2eRunActiveStatus(detail.status)) {
    pushUnique(signals, 'stepResultCount=0，阻断发生在实际步骤执行之前');
  }

  let tone: UiE2eWorkbenchNoticeTone;
  let label: string;
  let summary: string;

  if (isUiE2eRunActiveStatus(detail.status)) {
    tone = 'info';
    label = detail.status;
    summary = `运行仍在进行中，当前已聚合 ${stepResultCount} 个步骤结果和 ${detail.artifacts.length} 个 artifact manifest。`;
    pushUnique(nextActions, '继续观察工作台自动刷新，并结合 traceId 对照 runner/控制面日志。');
  } else if (failureCodeDiagnosis) {
    tone = failureCodeDiagnosis.tone;
    label = failureCodeDiagnosis.label;
    summary = failureCodeDiagnosis.summary;
    failureCodeDiagnosis.actions.forEach((item) => pushUnique(nextActions, item));
  } else if (detail.status === 'FAILED') {
    tone = flakyStatus === 'CONFIRMED_FLAKY' ? 'warning' : 'error';
    label = flakyStatus === 'CONFIRMED_FLAKY' ? 'CONFIRMED_FLAKY' : 'FAILED';
    summary = primaryFailureBucketEntry
      ? `运行失败，主要失败桶为 ${primaryFailureBucketEntry[0]}。`
      : '运行失败，请优先查看失败步骤摘要、errorCode 和 traceId。';
  } else if (detail.status === 'TIMEOUT') {
    tone = 'error';
    label = 'TIMEOUT';
    summary = '运行超时，通常需要先排查环境等待、账号状态或 runner 执行耗时。';
  } else if (detail.status === 'BLOCKED') {
    tone = 'warning';
    label = 'BLOCKED';
    summary = primaryFailureBucketEntry?.[0] === 'RUNNER'
      ? '运行被控制面阻断，优先检查 runner 开关、执行节点可用性和审批链路。'
      : '运行在执行前被阻断，建议优先复核租借、审批和目标范围。';
  } else if (detail.status === 'CANCELED') {
    tone = 'warning';
    label = 'CANCELED';
    summary = '运行已取消，当前摘要可用于确认取消链路已经回写到控制面。';
  } else if (detail.status === 'SUCCEEDED') {
    tone = blockedArtifacts.length ? 'warning' : 'success';
    label = blockedArtifacts.length ? 'SUCCEEDED_WITH_WARNINGS' : 'SUCCEEDED';
    summary = blockedArtifacts.length
      ? `运行已成功，但有 ${blockedArtifacts.length} 个 artifact 未形成可下载引用。`
      : '运行已成功，步骤与 artifact 摘要已完成聚合。';
  } else {
    tone = 'info';
    label = detail.status || 'UNKNOWN';
    summary = '运行摘要已返回，可结合步骤结果、artifact manifest 和 traceId 继续诊断。';
  }

  collectFailureBucketActions(detail.stepResults, failureBucketCounts).forEach((item) => pushUnique(nextActions, item));
  blockedArtifactReasons
    .map((reason) => artifactBlockedReasonAction(reason))
    .forEach((item) => pushUnique(nextActions, item));

  if (detail.status === 'CANCELED' || detail.failureCode === 'UI_E2E_RUNNER_CANCELED') {
    pushUnique(nextActions, '确认外部 runner 侧任务是否同步停止，再决定是否重新发起 run。');
  }
  if (flakyStatus === 'CONFIRMED_FLAKY') {
    pushUnique(nextActions, '当前运行已标记为 CONFIRMED_FLAKY，可优先按不稳定场景治理而不是直接回归 blocker。');
  }
  if (!nextActions.length) {
    pushUnique(nextActions, '保留 traceId，并结合 scene、bundle 和 runner 配置继续排查。');
  }

  return {
    tone,
    label,
    summary,
    primaryFailureBucket,
    blockedArtifactCount: blockedArtifacts.length,
    rawArtifactDownloadReady,
    signals,
    nextActions
  };
}

export function explainUiE2eFailureBucket(bucket?: string) {
  switch ((bucket || '').trim().toUpperCase()) {
    case 'LOCATOR':
      return '定位器未命中目标元素，通常需要核对 scene/bundle 中 locator 策略与页面版本是否一致。';
    case 'AUTHORIZATION':
      return '权限或登录态不足，建议复核账号租借角色、会话状态和目标环境授权。';
    case 'ENVIRONMENT_TIMEOUT':
      return '环境等待超时，优先检查页面可达性、等待策略和 runner 超时配置。';
    case 'ACCOUNT':
      return '账号上下文异常，通常需要确认租借是否仍有效或是否被其他流程回收。';
    case 'TEST_DATA':
      return '前置数据或幂等键不满足预期，建议先复核测试数据准备与清理链路。';
    case 'RUNNER':
      return '问题集中在执行器链路，优先确认 runner 开关、节点可用性和回写状态。';
    case 'ASSERTION':
      return '断言未满足预期，需要对照步骤 summary 判断是产品变更还是用例漂移。';
    case 'UNKNOWN':
      return '失败已被归入 UNKNOWN，建议保留 traceId 并补看 runner/控制面日志。';
    default:
      return bucket ? `failureBucket=${bucket}` : '当前步骤没有失败桶摘要。';
  }
}

export function extractUiE2eArtifactCaptureBlockedReason(redactionFlags: Record<string, unknown>) {
  const value = redactionFlags.captureBlockedReason;
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

export function explainUiE2eArtifactCaptureBlockedReason(reason?: string) {
  switch ((reason || '').trim()) {
    case 'runnerDisabled':
      return 'runner 默认关闭，控制面不会生成真实截图、HAR 或日志正文。';
    case 'artifactRefIncomplete':
      return 'runner 回传了 CAPTURED，但 storageRef 或 digest 缺失，manifest 被降级为 BLOCKED。';
    default:
      return reason ? `captureBlockedReason=${reason}` : 'artifact capture 被阻断，请结合 redactionFlags 和 runner 回传排查。';
  }
}

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

function buildFailureCodeDiagnosis(failureCode?: string) {
  switch (failureCode) {
    case 'UI_E2E_RUNNER_DISABLED':
      return {
        tone: 'warning' as const,
        label: 'RUNNER_DISABLED',
        summary: '当前环境 runner 默认关闭，本次运行被控制面安全地标记为 BLOCKED。',
        actions: [
          '如需真实浏览器执行，请先切换到 runner 已启用的环境或打开对应开关。',
          '继续核对审批、租借和 aggregate-only 导出链路是否按预期工作。'
        ]
      };
    case 'UI_E2E_RUNNER_CANCELED':
      return {
        tone: 'warning' as const,
        label: 'RUNNER_CANCELED',
        summary: '运行已收到取消结果，控制面摘要已经转入终态。',
        actions: [
          '确认取消请求是否同步回送到外部 runner，并检查是否还有残留执行实例。'
        ]
      };
    case 'UI_E2E_ACCOUNT_LEASE_INVALID':
      return {
        tone: 'error' as const,
        label: 'ACCOUNT_LEASE_INVALID',
        summary: '账号租借失效或作用域不匹配，控制面拒绝继续执行。',
        actions: [
          '重新申请有效 lease，并确认 project/environment 与 run 请求保持同域。'
        ]
      };
    case 'UI_E2E_BASE_URL_NOT_ALLOWED':
      return {
        tone: 'error' as const,
        label: 'BASE_URL_NOT_ALLOWED',
        summary: 'baseUrlRef 不在 allowlist 内，运行在执行前被安全拒绝。',
        actions: [
          '确认 baseUrlRef 对应主机是否应进入 allowlist，并复核环境映射。'
        ]
      };
    case 'UI_E2E_RESOURCE_SCOPE_DENIED':
      return {
        tone: 'error' as const,
        label: 'RESOURCE_SCOPE_DENIED',
        summary: 'project、scene、bundle 或环境作用域不一致，控制面拒绝执行。',
        actions: [
          '检查 projectId、sceneId、bundleId、environmentId 是否来自同一受控范围。'
        ]
      };
    case 'UI_E2E_SCENE_NOT_READY':
      return {
        tone: 'warning' as const,
        label: 'SCENE_NOT_READY',
        summary: 'scene 尚未达到 APPROVED，当前不允许发起运行。',
        actions: [
          '先完成 scene 审批，再重新创建 bundle/run。'
        ]
      };
    case 'UI_E2E_BUNDLE_NOT_READY':
      return {
        tone: 'warning' as const,
        label: 'BUNDLE_NOT_READY',
        summary: 'bundle 未通过 APPROVED 或与 scene 不匹配，运行被拦截。',
        actions: [
          '先确认 bundle 审批状态与 scene 关联，再重新发起运行。'
        ]
      };
    case 'UI_E2E_EXPORT_DISABLED':
      return {
        tone: 'warning' as const,
        label: 'EXPORT_DISABLED',
        summary: '当前环境禁用了运行摘要导出，只能在控制面内查看聚合结果。',
        actions: [
          '如需对外共享摘要，请先确认 export 开关是否允许在该环境打开。'
        ]
      };
    default:
      return undefined;
  }
}

function collectFailureBucketActions(stepResults: UiE2eRunStepResult[], failureBucketCounts: Record<string, number>) {
  const actions: string[] = [];
  const buckets = uniqueStrings([
    ...Object.keys(failureBucketCounts),
    ...stepResults.map((step) => step.failureBucket)
  ]);
  buckets.forEach((bucket) => {
    switch ((bucket || '').trim().toUpperCase()) {
      case 'LOCATOR':
        pushUnique(actions, '核对 scene/bundle 中 locator 策略是否与当前页面结构一致。');
        break;
      case 'AUTHORIZATION':
        pushUnique(actions, '复核账号权限、登录态和环境角色配置是否满足场景要求。');
        break;
      case 'ENVIRONMENT_TIMEOUT':
        pushUnique(actions, '检查环境可达性、页面等待策略和 runner 超时配置。');
        break;
      case 'ACCOUNT':
        pushUnique(actions, '确认账号租借仍有效，必要时重新申请 lease 后再试。');
        break;
      case 'TEST_DATA':
        pushUnique(actions, '核对测试数据准备、幂等键和清理链路是否满足前置条件。');
        break;
      case 'RUNNER':
        pushUnique(actions, '优先确认 runner 开关、执行节点可用性和回写链路状态。');
        break;
      case 'ASSERTION':
        pushUnique(actions, '对照步骤 summary 与断言预期，判断是产品变更还是用例漂移。');
        break;
      case 'UNKNOWN':
        pushUnique(actions, '保留 traceId，并对照 runner/控制面日志补齐失败归类。');
        break;
      default:
        break;
    }
  });
  return actions;
}

function artifactBlockedReasonAction(reason?: string) {
  switch ((reason || '').trim()) {
    case 'runnerDisabled':
      return 'runner 默认关闭时不会生成真实 artifact，可继续用 aggregate-only 摘要验证控制面链路。';
    case 'artifactRefIncomplete':
      return '检查 runner 回传的 artifact storageRef 与 digest 是否完整。';
    default:
      return reason ? `复核 artifact captureBlockedReason=${reason} 对应的 runner 回传逻辑。` : undefined;
  }
}

function numberRecord(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {} as Record<string, number>;
  }
  return Object.entries(value).reduce<Record<string, number>>((result, [key, item]) => {
    const count = numberFromUnknown(item);
    if (count > 0) {
      result[key] = count;
    }
    return result;
  }, {});
}

function sortedCountEntries(counts: Record<string, number>) {
  return Object.entries(counts).sort((left, right) => {
    if (right[1] !== left[1]) {
      return right[1] - left[1];
    }
    return left[0].localeCompare(right[0]);
  });
}

function pushUnique(items: string[], value?: string) {
  if (!value || items.includes(value)) {
    return;
  }
  items.push(value);
}

function uniqueStrings(values: Array<string | undefined>) {
  return values.filter((value, index, array): value is string => Boolean(value) && array.indexOf(value) === index);
}

function numberFromUnknown(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function booleanFromUnknown(value: unknown, fallback = false) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  return fallback;
}
