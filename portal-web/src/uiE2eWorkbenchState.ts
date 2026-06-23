import type {
  BatchCreateUiE2eRunPayload,
  BackfillUiE2eRunSummaryPayload,
  CreateUiE2eRunPayload,
  CreateUiE2eScenePayload,
  UiE2eArtifactManifest,
  UiE2eBatchRun,
  UiE2eBundleSummary,
  UiE2eFlakyMark,
  UiE2eHealth,
  UiE2eRunSummaryBackfill,
  UiE2eSceneImport,
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
  dataBindingText: string;
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
  browsersText: string;
  visualRegressionEnabled: boolean;
  baselineRunId: string;
  visualMismatchThreshold: string;
};

export type UiE2eBatchRunDraft = {
  projectId: string;
  sceneIdsText: string;
  environmentId: string;
  baseUrlRef: string;
  accountLeaseRef: string;
  requestKeyPrefix: string;
  reason: string;
  browsersText: string;
  visualRegressionEnabled: boolean;
  baselineRunId: string;
  visualMismatchThreshold: string;
};

export type UiE2eFlakyDraft = {
  projectId: string;
  sceneId: string;
  runId: string;
  status: string;
  reasonCode: string;
  reasonSummary: string;
};

export type UiE2eRunBackfillDraft = {
  projectId: string;
  runIdsText: string;
  limit: string;
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

export type UiE2eSceneFocusMode = 'all' | 'approved' | 'reviewing' | 'draft' | 'highRisk' | 'disabled';

export type UiE2eSceneFocusOption = {
  mode: Exclude<UiE2eSceneFocusMode, 'all'>;
  label: string;
  desc: string;
  count: number;
  tone: UiE2eWorkbenchTone;
};

export type UiE2eSceneQueueOverview = {
  focusOptions: UiE2eSceneFocusOption[];
};

export type UiE2eSceneListSummary = {
  headline: string;
  detail: string;
  signals: string[];
};

export type UiE2eSceneActivitySummary = {
  bundleCount: number;
  runCount: number;
  latestBundle?: UiE2eBundleSummary;
  latestRun?: UiE2eRunSummary;
};

export type UiE2eBundleFocusMode = 'all' | 'reviewing' | 'submittable' | 'approved' | 'staticFailed' | 'rejected';

export type UiE2eBundleFocusOption = {
  mode: Exclude<UiE2eBundleFocusMode, 'all'>;
  label: string;
  desc: string;
  count: number;
  tone: UiE2eWorkbenchTone;
};

export type UiE2eBundleQueueOverview = {
  focusOptions: UiE2eBundleFocusOption[];
};

export type UiE2eRunFocusMode = 'all' | 'active' | 'failures' | 'blocked' | 'flaky' | 'runnerDisabled';

export type UiE2eRunFocusOption = {
  mode: Exclude<UiE2eRunFocusMode, 'all'>;
  label: string;
  desc: string;
  count: number;
  tone: UiE2eWorkbenchTone;
};

export type UiE2eRunQueueOverview = {
  focusOptions: UiE2eRunFocusOption[];
};

export type UiE2eFlakyFocusMode = 'all' | 'candidates' | 'confirmed' | 'waived' | 'runLinked' | 'sceneOnly';

export type UiE2eFlakyFocusOption = {
  mode: Exclude<UiE2eFlakyFocusMode, 'all'>;
  label: string;
  desc: string;
  count: number;
  tone: UiE2eWorkbenchTone;
};

export type UiE2eFlakyQueueOverview = {
  focusOptions: UiE2eFlakyFocusOption[];
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

export type UiE2eArtifactDownloadState = {
  canDownload: boolean;
  downloadReady: boolean;
  tone: UiE2eWorkbenchNoticeTone;
  summary: string;
};

export type UiE2eBundleListSummary = {
  headline: string;
  detail: string;
  signals: string[];
};

export type UiE2eRunListSummary = {
  headline: string;
  detail: string;
  signals: string[];
};

export type UiE2eRunCreationReadiness = {
  ready: boolean;
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  checks: string[];
};

export type UiE2eRunBackfillReadiness = {
  ready: boolean;
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  checks: string[];
};

export type UiE2eRunBackfillSummary = {
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  signals: string[];
  failedItems: string[];
};

export type UiE2eBatchRunReadiness = {
  ready: boolean;
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  checks: string[];
};

export type UiE2eBatchRunSummary = {
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  signals: string[];
  failedItems: string[];
};

export type UiE2eRunFlakyPreset = {
  status: 'FLAKY_CANDIDATE' | 'CONFIRMED_FLAKY' | 'WAIVED';
  label: string;
  tone: UiE2eWorkbenchTone;
  reasonCode: string;
  reasonSummary: string;
};

export type UiE2eRunFlakyGuidance = {
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  presets: UiE2eRunFlakyPreset[];
};

export type UiE2eRunAuditTimelineItem = {
  id: string;
  kindLabel: 'RUN' | 'STEP' | 'ARTIFACT' | 'FLAKY';
  title: string;
  detail: string;
  occurredAt?: string;
  tone: UiE2eWorkbenchTone;
};

export type UiE2eFlakyListSummary = {
  headline: string;
  detail: string;
  signals: string[];
};

export type UiE2eFlakyDetailInsight = {
  tone: UiE2eWorkbenchNoticeTone;
  label: string;
  summary: string;
  signals: string[];
};

export const initialUiE2eSceneStepDraft: UiE2eSceneStepDraft = {
  stepType: 'LOGIN',
  actionSummaryText: '{"submitAction":"click"}',
  locatorStrategyText: '{"preferred":"testId"}',
  assertionSummaryText: '{"successSignal":"url contains /dashboard"}',
  waitPolicyText: '{"timeoutSeconds":5}',
  dataBindingText: '{}'
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
  reason: '',
  browsersText: 'CHROMIUM',
  visualRegressionEnabled: false,
  baselineRunId: '',
  visualMismatchThreshold: ''
};

export const initialUiE2eBatchRunDraft: UiE2eBatchRunDraft = {
  projectId: '',
  sceneIdsText: '',
  environmentId: '',
  baseUrlRef: 'env:staging',
  accountLeaseRef: '',
  requestKeyPrefix: '',
  reason: '',
  browsersText: 'CHROMIUM',
  visualRegressionEnabled: false,
  baselineRunId: '',
  visualMismatchThreshold: ''
};

export const initialUiE2eFlakyDraft: UiE2eFlakyDraft = {
  projectId: '',
  sceneId: '',
  runId: '',
  status: 'FLAKY_CANDIDATE',
  reasonCode: '',
  reasonSummary: ''
};

export const initialUiE2eRunBackfillDraft: UiE2eRunBackfillDraft = {
  projectId: '',
  runIdsText: '',
  limit: '20'
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
  if (health) {
    const queuedTasks = numberFromUnknown(health.runnerCapacity?.queuedTasks);
    const saturated = booleanFromUnknown(health.runnerCapacity?.saturated);
    const backfillReady = booleanFromUnknown(health.runnerCapacity?.summaryBackfillReady, true);
    if (saturated) {
      notices.push({ tone: 'warning', message: '共享浏览器池当前已饱和，新的浏览器尝试可能进入排队。' });
    }
    if (queuedTasks > 0) {
      notices.push({ tone: 'info', message: `当前 runner 队列中仍有 ${queuedTasks} 个待处理任务，批量运行可能需要等待空闲槽位。` });
    }
    if (!backfillReady) {
      notices.push({ tone: 'info', message: '运行摘要 backfill 当前未就绪，执行前请先确认控制面版本和健康状态。' });
    }
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

export function buildUiE2eSceneQueueOverview(scenes: UiE2eSceneSummary[]): UiE2eSceneQueueOverview {
  return {
    focusOptions: [
      {
        mode: 'approved',
        label: '已批准',
        desc: '聚焦 APPROVED，优先定位可继续生成 bundle 的场景。',
        count: filterUiE2eScenesByFocusMode(scenes, 'approved').length,
        tone: 'success'
      },
      {
        mode: 'reviewing',
        label: '待评审',
        desc: '聚焦 REVIEWING，优先确认是否已达到通过条件。',
        count: filterUiE2eScenesByFocusMode(scenes, 'reviewing').length,
        tone: 'info'
      },
      {
        mode: 'draft',
        label: '草稿',
        desc: '聚焦 DRAFT，优先补全步骤模板、定位和断言摘要。',
        count: filterUiE2eScenesByFocusMode(scenes, 'draft').length,
        tone: 'warning'
      },
      {
        mode: 'highRisk',
        label: '高风险',
        desc: '聚焦 HIGH/CRITICAL，优先复核关键路径覆盖和审批状态。',
        count: filterUiE2eScenesByFocusMode(scenes, 'highRisk').length,
        tone: 'danger'
      },
      {
        mode: 'disabled',
        label: '已停用',
        desc: '聚焦 DISABLED，确认是否需要恢复或保持退出运行链路。',
        count: filterUiE2eScenesByFocusMode(scenes, 'disabled').length,
        tone: 'warning'
      }
    ]
  };
}

export function filterUiE2eScenesByFocusMode(scenes: UiE2eSceneSummary[], mode: UiE2eSceneFocusMode) {
  switch (mode) {
    case 'approved':
      return scenes.filter((scene) => scene.status === 'APPROVED');
    case 'reviewing':
      return scenes.filter((scene) => scene.status === 'REVIEWING');
    case 'draft':
      return scenes.filter((scene) => scene.status === 'DRAFT');
    case 'highRisk':
      return scenes.filter((scene) => scene.riskLevel === 'HIGH' || scene.riskLevel === 'CRITICAL');
    case 'disabled':
      return scenes.filter((scene) => scene.status === 'DISABLED');
    case 'all':
    default:
      return scenes;
  }
}

export function labelUiE2eSceneFocusMode(mode: UiE2eSceneFocusMode) {
  switch (mode) {
    case 'approved':
      return '已批准';
    case 'reviewing':
      return '待评审';
    case 'draft':
      return '草稿';
    case 'highRisk':
      return '高风险';
    case 'disabled':
      return '已停用';
    case 'all':
    default:
      return '全部场景';
  }
}

export function buildUiE2eSceneListSummary(scene: UiE2eSceneSummary): UiE2eSceneListSummary {
  const signals: string[] = [
    `risk=${scene.riskLevel}`,
    `steps=${scene.stepCount}`
  ];
  if (scene.environmentId) {
    pushUnique(signals, `env=${scene.environmentId}`);
  }
  if (scene.tags.length) {
    pushUnique(signals, `tags=${scene.tags.length}`);
  }
  const sourceType = extractUiE2eSceneSourceType(scene.sourceSummary);
  if (sourceType) {
    pushUnique(signals, `source=${sourceType}`);
  }

  let detail: string;
  if (scene.status === 'APPROVED') {
    detail = '场景已批准，可继续生成 bundle 或串联运行链路。';
  } else if (scene.status === 'REVIEWING') {
    detail = '场景已进入评审，建议先核对步骤模板、来源摘要和风险等级。';
  } else if (scene.status === 'DRAFT') {
    detail = '场景仍在草稿态，可继续补全步骤模板、定位策略和断言摘要。';
  } else if (scene.status === 'DISABLED') {
    detail = '场景已停用，恢复前不会进入 bundle/运行链路。';
  } else if (scene.status === 'ARCHIVED') {
    detail = '场景已归档，不再参与新的 bundle 生成。';
  } else {
    detail = '场景摘要已生成，可继续查看步骤模板和来源摘要。';
  }

  return {
    headline: scene.status === 'APPROVED'
      ? '可生成 bundle'
      : scene.status === 'REVIEWING'
        ? '待场景评审'
        : scene.status === 'DRAFT'
          ? '草稿待补全'
          : scene.status === 'DISABLED'
            ? '已停用'
            : scene.status === 'ARCHIVED'
              ? '已归档'
              : `risk=${scene.riskLevel}`,
    detail,
    signals
  };
}

export function buildUiE2eSceneActivitySummary(
  sceneId: string,
  bundles: UiE2eBundleSummary[],
  runs: UiE2eRunSummary[]
): UiE2eSceneActivitySummary {
  const relatedBundles = bundles.filter((bundle) => bundle.sceneId === sceneId);
  const relatedRuns = runs.filter((run) => run.sceneId === sceneId);

  return {
    bundleCount: relatedBundles.length,
    runCount: relatedRuns.length,
    latestBundle: latestUiE2eEntity(
      relatedBundles,
      (bundle) => firstUiE2eTimestamp(bundle.updatedAt, bundle.approvedAt, bundle.rejectedAt, bundle.submittedAt, bundle.createdAt)
    ),
    latestRun: latestUiE2eEntity(
      relatedRuns,
      (run) => firstUiE2eTimestamp(run.finishedAt, run.updatedAt, run.startedAt, run.createdAt)
    )
  };
}

export function buildUiE2eBundleQueueOverview(bundles: UiE2eBundleSummary[]): UiE2eBundleQueueOverview {
  return {
    focusOptions: [
      {
        mode: 'reviewing',
        label: '待审批',
        desc: '聚焦 REVIEWING，方便快速处理批准或驳回决策。',
        count: filterUiE2eBundlesByFocusMode(bundles, 'reviewing').length,
        tone: 'info'
      },
      {
        mode: 'submittable',
        label: '待送审',
        desc: '聚焦 DRAFT/REJECTED/STATIC_CHECK_FAILED，确认是否已满足再次送审条件。',
        count: filterUiE2eBundlesByFocusMode(bundles, 'submittable').length,
        tone: 'warning'
      },
      {
        mode: 'approved',
        label: '可执行',
        desc: '聚焦 APPROVED，优先挑选可直接进入运行链路的 bundle。',
        count: filterUiE2eBundlesByFocusMode(bundles, 'approved').length,
        tone: 'success'
      },
      {
        mode: 'staticFailed',
        label: '静态校验失败',
        desc: '聚焦静态校验未通过的 bundle，优先复核摘要中的失败项。',
        count: filterUiE2eBundlesByFocusMode(bundles, 'staticFailed').length,
        tone: 'danger'
      },
      {
        mode: 'rejected',
        label: '已驳回',
        desc: '聚焦 REJECTED，回看评审意见后决定是否修正重提。',
        count: filterUiE2eBundlesByFocusMode(bundles, 'rejected').length,
        tone: 'danger'
      }
    ]
  };
}

export function filterUiE2eBundlesByFocusMode(bundles: UiE2eBundleSummary[], mode: UiE2eBundleFocusMode) {
  switch (mode) {
    case 'reviewing':
      return bundles.filter((bundle) => bundle.status === 'REVIEWING');
    case 'submittable':
      return bundles.filter((bundle) => isUiE2eBundleSubmittableStatus(bundle.status));
    case 'approved':
      return bundles.filter((bundle) => bundle.status === 'APPROVED');
    case 'staticFailed':
      return bundles.filter((bundle) => isUiE2eBundleStaticFailed(bundle));
    case 'rejected':
      return bundles.filter((bundle) => bundle.status === 'REJECTED');
    case 'all':
    default:
      return bundles;
  }
}

export function labelUiE2eBundleFocusMode(mode: UiE2eBundleFocusMode) {
  switch (mode) {
    case 'reviewing':
      return '待审批';
    case 'submittable':
      return '待送审';
    case 'approved':
      return '可执行';
    case 'staticFailed':
      return '静态校验失败';
    case 'rejected':
      return '已驳回';
    case 'all':
    default:
      return '全部脚本包';
  }
}

export function buildUiE2eBundleListSummary(bundle: UiE2eBundleSummary): UiE2eBundleListSummary {
  const staticCheckLabel = compactUiE2eStaticCheckStatus(bundle.staticCheckStatus);
  const signals: string[] = [];
  if (staticCheckLabel) {
    pushUnique(signals, `static=${staticCheckLabel}`);
  }
  if (bundle.sceneStatus) {
    pushUnique(signals, `scene=${bundle.sceneStatus}`);
  }
  if (bundle.bundleDigest) {
    pushUnique(signals, 'digest-ready');
  }
  if (bundle.status === 'REVIEWING') {
    pushUnique(signals, 'review-pending');
  }
  if (bundle.status === 'APPROVED') {
    pushUnique(signals, 'run-ready');
  }
  if (bundle.status === 'REJECTED') {
    pushUnique(signals, 'needs-resubmit');
  }
  if (bundle.status === 'DRAFT') {
    pushUnique(signals, 'draft');
  }

  let detail: string;
  if (isUiE2eBundleStaticFailed(bundle)) {
    detail = '静态校验未通过，建议先处理摘要中的失败项后再送审。';
  } else if (bundle.status === 'REVIEWING') {
    detail = '脚本包已送审，待 review 决定是否允许进入运行链路。';
  } else if (bundle.status === 'APPROVED') {
    detail = '脚本包已批准，可继续用于创建 UI 运行。';
  } else if (bundle.status === 'REJECTED') {
    detail = '脚本包已驳回，修正后可再次送审。';
  } else if (bundle.status === 'ARCHIVED') {
    detail = '脚本包已归档，不再参与新的运行申请。';
  } else {
    detail = '脚本包已生成，可先查看静态校验摘要与场景状态。';
  }

  let headline: string;
  if (isUiE2eBundleStaticFailed(bundle)) {
    headline = staticCheckLabel || 'STATIC_CHECK_FAILED';
  } else if (bundle.status === 'REVIEWING') {
    headline = '等待审批';
  } else if (bundle.status === 'APPROVED') {
    headline = '可创建运行';
  } else if (bundle.status === 'REJECTED') {
    headline = '需要修正后重提';
  } else {
    headline = staticCheckLabel ? `static=${staticCheckLabel}` : bundle.status;
  }

  return {
    headline,
    detail,
    signals
  };
}

export function buildUiE2eRunQueueOverview(runs: UiE2eRunSummary[]): UiE2eRunQueueOverview {
  return {
    focusOptions: [
      {
        mode: 'active',
        label: '活跃运行',
        desc: '聚焦 QUEUED/RUNNING，便于盯住自动刷新中的 run。',
        count: filterUiE2eRunsByFocusMode(runs, 'active').length,
        tone: 'info'
      },
      {
        mode: 'failures',
        label: '失败/超时',
        desc: '聚焦 FAILED/TIMEOUT，优先排查 failureCode 与 traceId。',
        count: filterUiE2eRunsByFocusMode(runs, 'failures').length,
        tone: 'danger'
      },
      {
        mode: 'blocked',
        label: '阻断运行',
        desc: '聚焦 BLOCKED，优先复核 runner、租借、审批与 allowlist。',
        count: filterUiE2eRunsByFocusMode(runs, 'blocked').length,
        tone: 'warning'
      },
      {
        mode: 'flaky',
        label: 'Confirmed Flaky',
        desc: '聚焦已标记 CONFIRMED_FLAKY 的运行，方便做抖动治理。',
        count: filterUiE2eRunsByFocusMode(runs, 'flaky').length,
        tone: 'warning'
      },
      {
        mode: 'runnerDisabled',
        label: 'Runner Off',
        desc: '聚焦 UI_E2E_RUNNER_DISABLED，确认 aggregate-only 控制面链路。',
        count: filterUiE2eRunsByFocusMode(runs, 'runnerDisabled').length,
        tone: 'warning'
      }
    ]
  };
}

export function filterUiE2eRunsByFocusMode(runs: UiE2eRunSummary[], mode: UiE2eRunFocusMode) {
  switch (mode) {
    case 'active':
      return runs.filter((run) => isUiE2eRunActiveStatus(run.status));
    case 'failures':
      return runs.filter((run) => isUiE2eRunFailureStatus(run.status));
    case 'blocked':
      return runs.filter((run) => run.status === 'BLOCKED');
    case 'flaky':
      return runs.filter((run) => run.flakyStatus === 'CONFIRMED_FLAKY');
    case 'runnerDisabled':
      return runs.filter((run) => run.failureCode === 'UI_E2E_RUNNER_DISABLED');
    case 'all':
    default:
      return runs;
  }
}

export function labelUiE2eRunFocusMode(mode: UiE2eRunFocusMode) {
  switch (mode) {
    case 'active':
      return '活跃运行';
    case 'failures':
      return '失败/超时';
    case 'blocked':
      return '阻断运行';
    case 'flaky':
      return 'Confirmed Flaky';
    case 'runnerDisabled':
      return 'Runner Off';
    case 'all':
    default:
      return '全部运行';
  }
}

export function buildUiE2eRunListSummary(run: UiE2eRunSummary): UiE2eRunListSummary {
  const failureLabel = compactUiE2eFailureCode(run.failureCode);
  const signals: string[] = [];
  const accountSummary = run.accountSummary || {};
  const browserTypes = stringArrayFromUnknown(accountSummary.browserTypes);
  const visualRegressionEnabled = booleanFromUnknown(accountSummary.visualRegressionEnabled);
  if (failureLabel) {
    pushUnique(signals, `failure=${failureLabel}`);
  }
  if (run.flakyStatus && run.flakyStatus !== 'NONE') {
    pushUnique(signals, `flaky=${run.flakyStatus}`);
  }
  if (run.status === 'BLOCKED' && run.failureCode === 'UI_E2E_RUNNER_DISABLED') {
    pushUnique(signals, 'aggregate-only');
  }
  if (isUiE2eRunActiveStatus(run.status)) {
    pushUnique(signals, 'auto-refresh');
  }
  if (browserTypes.length > 1) {
    pushUnique(signals, `browsers=${browserTypes.join('/')}`);
  }
  if (visualRegressionEnabled) {
    pushUnique(signals, 'visual-regression');
  }

  let detail: string;
  if (run.failureCode === 'UI_E2E_RUNNER_DISABLED') {
    detail = 'runner 默认关闭，控制面返回 BLOCKED 摘要。';
  } else if (run.status === 'BLOCKED') {
    detail = '运行在执行前被阻断，建议优先复核审批、租借与 allowlist。';
  } else if (isUiE2eRunFailureStatus(run.status)) {
    detail = '建议优先查看 failureCode、traceId 和运行详情诊断。';
  } else if (run.status === 'CANCELED') {
    detail = '运行已取消，可继续确认外部 runner 是否同步停止。';
  } else if (isUiE2eRunActiveStatus(run.status)) {
    detail = '运行进行中，详情面板会自动刷新最新快照。';
  } else if (run.flakyStatus === 'CONFIRMED_FLAKY') {
    detail = '运行已完成，但当前已标记为 CONFIRMED_FLAKY。';
  } else {
    detail = '运行已完成，可继续查看步骤结果和 artifact 摘要。';
  }

  return {
    headline: failureLabel || `runner=${run.runnerMode}`,
    detail,
    signals
  };
}

export function buildUiE2eRunCreationReadiness(input: {
  health: UiE2eHealth | null;
  draft: Pick<UiE2eRunDraft, 'projectId' | 'sceneId' | 'bundleId' | 'baseUrlRef' | 'accountLeaseRef' | 'browsersText' | 'visualRegressionEnabled' | 'baselineRunId'>;
  scene?: Pick<UiE2eSceneSummary, 'code' | 'status'> | null;
  bundle?: Pick<UiE2eBundleSummary, 'status' | 'sceneCode' | 'sceneStatus'> | null;
}): UiE2eRunCreationReadiness {
  const { health, draft, scene, bundle } = input;
  const checks: string[] = [];
  const missingFields: string[] = [];

  if (!draft.projectId.trim()) {
    missingFields.push('projectId');
  }
  if (!draft.sceneId.trim()) {
    missingFields.push('sceneId');
  }
  if (!draft.bundleId.trim()) {
    missingFields.push('bundleId');
  }
  if (!draft.baseUrlRef.trim()) {
    missingFields.push('baseUrlRef');
  }
  if (!draft.accountLeaseRef.trim()) {
    missingFields.push('accountLeaseRef');
  }
  const browsers = splitTags(draft.browsersText || '').map((item) => item.toUpperCase());
  if (!browsers.length) {
    missingFields.push('browsers');
  }
  if (draft.visualRegressionEnabled && !draft.baselineRunId.trim()) {
    pushUnique(checks, 'visualBaseline=auto-latest-success');
  }

  if (health) {
    pushUnique(checks, `runner=${health.runnerEnabled ? 'ON' : 'OFF'}:${health.runnerMode || 'UNKNOWN'}`);
  } else {
    pushUnique(checks, 'health=pending');
  }
  if (scene?.status) {
    pushUnique(checks, `scene=${scene.status}`);
  } else if (draft.sceneId.trim()) {
    pushUnique(checks, 'scene=list-miss');
  }
  if (bundle?.status) {
    pushUnique(checks, `bundle=${bundle.status}`);
  } else if (draft.bundleId.trim()) {
    pushUnique(checks, 'bundle=list-miss');
  }
  if (missingFields.length) {
    pushUnique(checks, `missing=${missingFields.join(',')}`);
  }

  if (missingFields.length) {
    return {
      ready: false,
      tone: 'info',
      label: '填写运行参数',
      summary: `请先补全 ${missingFields.join(' / ')}，再触发单次 UI 运行。`,
      checks
    };
  }

  if (health && !health.runnerEnabled) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Runner Disabled',
      summary: '当前环境 runner 默认关闭，工作台只验证控制面链路，不会创建真实浏览器运行。',
      checks
    };
  }

  if (scene && scene.status !== 'APPROVED') {
    return {
      ready: false,
      tone: 'warning',
      label: 'Scene Not Ready',
      summary: `当前场景 ${scene.code || draft.sceneId} 状态为 ${scene.status}，需先到 APPROVED 才能进入运行链路。`,
      checks
    };
  }

  if (bundle && bundle.status !== 'APPROVED') {
    return {
      ready: false,
      tone: 'warning',
      label: 'Bundle Not Ready',
      summary: `当前脚本包 ${bundle.sceneCode || draft.bundleId} 状态为 ${bundle.status}，需先批准后再创建运行。`,
      checks
    };
  }

  if (bundle?.sceneStatus && bundle.sceneStatus !== 'APPROVED') {
    return {
      ready: false,
      tone: 'warning',
      label: 'Scene Bundle Mismatch',
      summary: `当前 bundle 关联的场景状态为 ${bundle.sceneStatus}，建议先确认场景仍处于 APPROVED。`,
      checks
    };
  }

  return {
    ready: true,
    tone: 'success',
    label: 'Ready To Run',
    summary: '当前参数已满足前端已知准入条件；提交后仍会由后端继续校验 allowlist、账号租借和幂等约束。',
    checks
  };
}

export function buildUiE2eBatchRunPayload(draft: UiE2eBatchRunDraft): { payload?: BatchCreateUiE2eRunPayload; issues: string[] } {
  const issues: string[] = [];
  const projectId = draft.projectId.trim();
  const sceneIds = uniqueOrderedRunIds(draft.sceneIdsText);
  const browsers = splitTags(draft.browsersText || '').map((item) => item.toUpperCase());

  if (!projectId) issues.push('请填写 batch projectId');
  if (!sceneIds.length) issues.push('请至少填写一个 sceneId');
  if (sceneIds.length > 100) issues.push('sceneIds 最多支持 100 个');
  if (sceneIds.length) {
    const invalidSceneId = sceneIds.find((sceneId) => !uuidPattern.test(sceneId));
    if (invalidSceneId) {
      issues.push(`sceneId 需要是 UUID：${invalidSceneId}`);
    }
  }
  if (!draft.baseUrlRef.trim()) issues.push('请填写 batch baseUrlRef');
  if (!uuidPattern.test(draft.accountLeaseRef.trim())) issues.push('accountLeaseRef 需要是 UUID');
  if (!browsers.length) issues.push('至少指定一个浏览器（CHROMIUM/FIREFOX/WEBKIT）');
  if (browsers.some((item) => !['CHROMIUM', 'FIREFOX', 'WEBKIT'].includes(item))) {
    issues.push('浏览器仅支持 CHROMIUM / FIREFOX / WEBKIT');
  }
  if (draft.visualRegressionEnabled && draft.baselineRunId.trim() && !uuidPattern.test(draft.baselineRunId.trim())) {
    issues.push('baselineRunId 需要是 UUID');
  }
  if (draft.visualMismatchThreshold.trim()) {
    const threshold = Number(draft.visualMismatchThreshold.trim());
    if (Number.isNaN(threshold) || threshold < 0 || threshold > 1) {
      issues.push('visualMismatchThreshold 需要在 0 到 1 之间');
    }
  }
  if (draft.requestKeyPrefix.trim() && !requestKeyPattern.test(draft.requestKeyPrefix.trim())) {
    issues.push('requestKeyPrefix 只能包含字母、数字、-、_、.、:，且不超过 128 字符');
  }
  if (draft.reason.length > 512) issues.push('reason 最多 512 字符');

  if (issues.length) {
    return { issues };
  }

  return {
    issues,
    payload: {
      projectId,
      sceneIds,
      environmentId: optionalText(draft.environmentId),
      baseUrlRef: draft.baseUrlRef.trim(),
      accountLeaseRef: draft.accountLeaseRef.trim(),
      requestKeyPrefix: optionalText(draft.requestKeyPrefix),
      reason: optionalText(draft.reason),
      browsers,
      visualRegressionEnabled: draft.visualRegressionEnabled,
      baselineRunId: optionalText(draft.baselineRunId),
      visualMismatchThreshold: draft.visualMismatchThreshold.trim() ? Number(draft.visualMismatchThreshold.trim()) : undefined
    }
  };
}

export function buildUiE2eBatchRunReadiness(input: {
  health: UiE2eHealth | null;
  draft: UiE2eBatchRunDraft;
  scenes: UiE2eSceneSummary[];
}): UiE2eBatchRunReadiness {
  const { health, draft, scenes } = input;
  const { payload, issues } = buildUiE2eBatchRunPayload(draft);
  const checks: string[] = [];
  const sceneIds = uniqueOrderedRunIds(draft.sceneIdsText);
  const matchedScenes = sceneIds.length ? scenes.filter((scene) => sceneIds.includes(scene.id)) : [];
  const nonApprovedScenes = matchedScenes.filter((scene) => scene.status !== 'APPROVED');

  if (health) {
    pushUnique(checks, `runner=${health.runnerEnabled ? 'ON' : 'OFF'}:${health.runnerMode || 'UNKNOWN'}`);
    pushUnique(checks, `batch=${booleanFromUnknown(health.runnerCapacity?.batchRunReady, true) ? 'READY' : 'PENDING'}`);
    pushUnique(checks, `maxScenes=${health.maxScenesPerRun}`);
  } else {
    pushUnique(checks, 'health=pending');
  }
  if (sceneIds.length) {
    pushUnique(checks, `sceneIds=${sceneIds.length}`);
  }
  if (matchedScenes.length) {
    pushUnique(checks, `sceneMatched=${matchedScenes.length}`);
  }
  if (nonApprovedScenes.length) {
    pushUnique(checks, `sceneNotApproved=${nonApprovedScenes.length}`);
  }
  if (issues.length) {
    pushUnique(checks, `issues=${issues.length}`);
  }

  if (issues.length || !payload) {
    return {
      ready: false,
      tone: 'info',
      label: '填写 Batch 参数',
      summary: issues[0] || '请先补全批量运行参数。',
      checks
    };
  }

  if (health && payload.sceneIds.length > health.maxScenesPerRun) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Batch Size Exceeded',
      summary: `当前控制面单次批量最多支持 ${health.maxScenesPerRun} 个场景，请缩小本次 sceneId 范围。`,
      checks
    };
  }

  if (health && !health.runnerEnabled) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Runner Disabled',
      summary: '当前环境 runner 默认关闭，批量运行不会创建真实浏览器执行。',
      checks
    };
  }

  if (health && !booleanFromUnknown(health.runnerCapacity?.batchRunReady, true)) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Batch Pending',
      summary: '当前控制面尚未声明 batch run ready，建议先确认健康状态和部署版本。',
      checks
    };
  }

  if (nonApprovedScenes.length) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Scene Not Ready',
      summary: `当前列表中有 ${nonApprovedScenes.length} 个场景不是 APPROVED，建议先处理后再批量运行。`,
      checks
    };
  }

  return {
    ready: true,
    tone: 'success',
    label: 'Batch Ready',
    summary: `将按 ${payload.sceneIds.length} 个场景展开独立运行请求，逐条复用后端既有准入校验。`,
    checks
  };
}

export function buildUiE2eRunBackfillPayload(draft: UiE2eRunBackfillDraft): { payload?: BackfillUiE2eRunSummaryPayload; issues: string[] } {
  const issues: string[] = [];
  const projectId = draft.projectId.trim();
  const runIds = uniqueOrderedRunIds(draft.runIdsText);
  const limit = optionalPositiveInteger(draft.limit);

  if (!projectId) {
    issues.push('请填写 backfill projectId');
  }
  if (!runIds.length && limit == null) {
    issues.push('请填写 runIds 或 limit');
  }
  if (runIds.length > 200) {
    issues.push('runIds 最多支持 200 个');
  }
  if (draft.limit.trim() && limit == null) {
    issues.push('limit 需要是 1 到 200 的整数');
  }
  if (runIds.length) {
    const invalidRunId = runIds.find((runId) => !uuidPattern.test(runId));
    if (invalidRunId) {
      issues.push(`runId 需要是 UUID：${invalidRunId}`);
    }
  }

  if (issues.length) {
    return { issues };
  }

  return {
    issues,
    payload: {
      projectId,
      runIds: runIds.length ? runIds : undefined,
      limit: runIds.length ? undefined : limit ?? undefined
    }
  };
}

export function buildUiE2eRunBackfillReadiness(input: {
  health: UiE2eHealth | null;
  draft: UiE2eRunBackfillDraft;
}): UiE2eRunBackfillReadiness {
  const { health, draft } = input;
  const { payload, issues } = buildUiE2eRunBackfillPayload(draft);
  const checks: string[] = [];
  if (health) {
    pushUnique(checks, `backfill=${booleanFromUnknown(health.runnerCapacity?.summaryBackfillReady) ? 'READY' : 'PENDING'}`);
  } else {
    pushUnique(checks, 'health=pending');
  }
  const runIdCount = uniqueOrderedRunIds(draft.runIdsText).length;
  if (runIdCount) {
    pushUnique(checks, `runIds=${runIdCount}`);
  }
  if (draft.limit.trim()) {
    pushUnique(checks, `limit=${draft.limit.trim()}`);
  }

  if (issues.length || !payload) {
    return {
      ready: false,
      tone: 'info',
      label: '填写 Backfill 参数',
      summary: issues[0] || '请先填写 runIds 或 limit，再执行运行摘要回填。',
      checks
    };
  }

  if (health && !booleanFromUnknown(health.runnerCapacity?.summaryBackfillReady)) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Backfill Pending',
      summary: '当前控制面尚未声明 backfill ready，建议先确认健康状态和部署版本。',
      checks
    };
  }

  return {
    ready: true,
    tone: 'success',
    label: 'Backfill Ready',
    summary: payload.runIds?.length
      ? `将按指定的 ${payload.runIds.length} 个 runId 重算聚合摘要。`
      : `将按项目最近 ${payload.limit || 0} 条运行重算聚合摘要。`,
    checks
  };
}

export function buildUiE2eRunBackfillSummary(result: UiE2eRunSummaryBackfill): UiE2eRunBackfillSummary {
  const failedItems = result.items
    .filter((item) => item.errorCode)
    .map((item) => `${compactUiE2eFailureCode(item.errorCode)} · ${item.runId}${item.errorMessage ? ` · ${compactUiE2eText(item.errorMessage, 48)}` : ''}`);
  const updatedItems = result.items.filter((item) => item.updated).length;
  const unchangedItems = result.items.filter((item) => !item.updated && !item.errorCode).length;
  const signals: string[] = [
    `requested=${result.requestedCount}`,
    `updated=${result.updatedCount}`,
    `unchanged=${result.unchangedCount}`,
    `failed=${result.failedCount}`
  ];
  if (updatedItems !== result.updatedCount) {
    pushUnique(signals, `updatedItems=${updatedItems}`);
  }
  if (unchangedItems !== result.unchangedCount) {
    pushUnique(signals, `unchangedItems=${unchangedItems}`);
  }

  if (result.failedCount > 0) {
    return {
      tone: result.updatedCount > 0 ? 'warning' : 'error',
      label: result.updatedCount > 0 ? 'Backfill Partially Failed' : 'Backfill Failed',
      summary: result.updatedCount > 0
        ? `本次回填已更新 ${result.updatedCount} 条运行摘要，但仍有 ${result.failedCount} 条失败。`
        : `本次回填失败 ${result.failedCount} 条，请优先排查错误项。`,
      signals,
      failedItems
    };
  }

  if (result.updatedCount > 0) {
    return {
      tone: 'success',
      label: 'Backfill Updated',
      summary: `本次回填已更新 ${result.updatedCount} 条运行摘要，其余 ${result.unchangedCount} 条保持不变。`,
      signals,
      failedItems
    };
  }

  return {
    tone: 'info',
    label: 'Backfill Unchanged',
    summary: `本次回填未发现需要重写的摘要，共检查 ${result.requestedCount} 条运行。`,
    signals,
    failedItems
  };
}

export function buildUiE2eBatchRunSummary(result: UiE2eBatchRun): UiE2eBatchRunSummary {
  const failedItems = result.items
    .filter((item) => item.outcome === 'FAILED')
    .map((item) => `${item.sceneCode || item.sceneId} · ${compactUiE2eFailureCode(item.errorCode)}${item.errorMessage ? ` · ${compactUiE2eText(item.errorMessage, 48)}` : ''}`);
  const signals: string[] = [
    `requested=${result.requestedCount}`,
    `created=${result.createdCount}`,
    `replayed=${result.replayedCount}`,
    `failed=${result.failedCount}`
  ];

  if (result.failedCount > 0) {
    return {
      tone: result.createdCount > 0 || result.replayedCount > 0 ? 'warning' : 'error',
      label: result.createdCount > 0 || result.replayedCount > 0 ? 'Batch Partially Failed' : 'Batch Failed',
      summary: result.createdCount > 0 || result.replayedCount > 0
        ? `本次批量运行已创建 ${result.createdCount} 条、回放 ${result.replayedCount} 条，但仍有 ${result.failedCount} 条失败。`
        : `本次批量运行失败 ${result.failedCount} 条，请先排查失败项。`,
      signals,
      failedItems
    };
  }

  if (result.replayedCount > 0) {
    return {
      tone: 'success',
      label: 'Batch Replayed',
      summary: `本次批量运行全部成功，其中新建 ${result.createdCount} 条、回放既有运行 ${result.replayedCount} 条。`,
      signals,
      failedItems
    };
  }

  return {
    tone: 'success',
    label: 'Batch Created',
    summary: `本次批量运行已成功创建 ${result.createdCount} 条运行请求。`,
    signals,
    failedItems
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
  const browserTypes = stringArrayFromUnknown(executionSummary.browserTypes);
  const browserCount = numberFromUnknown(executionSummary.browserCount, browserTypes.length || 1);
  const visualRegressionEnabled = booleanFromUnknown(executionSummary.visualRegressionEnabled);
  const visualComparisonCount = numberFromUnknown(executionSummary.visualComparisonCount);
  const visualMismatchCount = numberFromUnknown(executionSummary.visualMismatchCount);
  const visualMismatchBrowsers = stringArrayFromUnknown(executionSummary.visualMismatchBrowsers);
  const visualBaselineRunId = stringFromUnknown(executionSummary.visualBaselineRunId);
  const visualThreshold = numberOrUndefined(executionSummary.visualMismatchThreshold);
  const parallelExecutionEnabled = booleanFromUnknown(executionSummary.parallelExecutionEnabled, browserCount > 1);

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
  if (browserTypes.length) {
    pushUnique(signals, `browserTypes=${browserTypes.join(',')}`);
  }
  if (parallelExecutionEnabled) {
    pushUnique(signals, `parallelExecutionEnabled=true（${browserCount} browsers）`);
  }
  if (visualRegressionEnabled) {
    pushUnique(
      signals,
      visualThreshold == null
        ? 'visualRegressionEnabled=true（threshold=exact-match）'
        : `visualRegressionEnabled=true（threshold=${visualThreshold}）`
    );
    if (visualBaselineRunId) {
      pushUnique(signals, `visualBaselineRunId=${visualBaselineRunId}`);
    }
  }
  if (visualComparisonCount > 0 || visualMismatchCount > 0) {
    pushUnique(
      signals,
      `visualComparison=${visualComparisonCount} compared / ${visualMismatchCount} mismatched`
    );
  }
  if (visualMismatchBrowsers.length) {
    pushUnique(signals, `visualMismatchBrowsers=${visualMismatchBrowsers.join(',')}`);
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
  if (visualRegressionEnabled && visualComparisonCount === 0) {
    pushUnique(nextActions, '确认基线运行是否具备同浏览器截图产物，避免视觉回归仅打开但没有实际对比样本。');
  }
  if (visualMismatchBrowsers.length) {
    pushUnique(nextActions, `优先复核 ${visualMismatchBrowsers.join(' / ')} 浏览器上的布局、样式和截图基线是否仍匹配。`);
  }

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

export function buildUiE2eRunFlakyGuidance(detail: UiE2eRunDetail): UiE2eRunFlakyGuidance {
  const failureBucketCounts = numberRecord(detail.executionSummary?.failureBucketCounts);
  const primaryFailureBucket = sortedCountEntries(failureBucketCounts)[0]?.[0];
  const currentMark = detail.flakyMark;
  const currentStatus = currentMark?.status || detail.flakyStatus || 'NONE';
  const reasonCode = currentMark?.reasonCode || runFlakyReasonCode(detail.failureCode, primaryFailureBucket);
  const reasonLead = currentMark?.reasonSummary || runFlakyReasonLead(detail, primaryFailureBucket);

  if (isUiE2eRunActiveStatus(detail.status)) {
    return {
      tone: 'info',
      label: '等待终态',
      summary: '运行仍在进行中，建议待终态后再决定是否标记为 Flaky。',
      presets: []
    };
  }

  if (currentStatus === 'CONFIRMED_FLAKY') {
    return {
      tone: 'warning',
      label: '已确认抖动',
      summary: '当前运行已经进入 CONFIRMED_FLAKY 治理池，可继续豁免或回退为候选观察。',
      presets: [
        {
          status: 'WAIVED',
          label: '标记豁免',
          tone: 'info',
          reasonCode,
          reasonSummary: `${reasonLead} 人工复核后暂时豁免，不作为当前回归阻断依据。`
        },
        {
          status: 'FLAKY_CANDIDATE',
          label: '回退候选',
          tone: 'info',
          reasonCode,
          reasonSummary: `${reasonLead} 当前先回退为候选抖动，继续观察后续运行表现。`
        }
      ]
    };
  }

  if (currentStatus === 'FLAKY_CANDIDATE') {
    return {
      tone: 'info',
      label: '候选已记录',
      summary: '当前运行已被记录为 FLAKY_CANDIDATE，可继续升级为确认抖动或豁免。',
      presets: [
        {
          status: 'CONFIRMED_FLAKY',
          label: '确认抖动',
          tone: 'warning',
          reasonCode,
          reasonSummary: `${reasonLead} 经人工复核后确认属于稳定复现的抖动样本。`
        },
        {
          status: 'WAIVED',
          label: '标记豁免',
          tone: 'info',
          reasonCode,
          reasonSummary: `${reasonLead} 当前先豁免，不作为版本回归阻断项。`
        }
      ]
    };
  }

  if (currentStatus === 'WAIVED') {
    return {
      tone: 'info',
      label: '当前已豁免',
      summary: '该运行已经被豁免；如果环境或场景再次波动，可以重新回到候选或确认状态。',
      presets: [
        {
          status: 'FLAKY_CANDIDATE',
          label: '重新候选',
          tone: 'info',
          reasonCode,
          reasonSummary: `${reasonLead} 当前重新纳入候选抖动观察范围。`
        },
        {
          status: 'CONFIRMED_FLAKY',
          label: '恢复确认',
          tone: 'warning',
          reasonCode,
          reasonSummary: `${reasonLead} 当前恢复为已确认抖动，继续纳入治理池。`
        }
      ]
    };
  }

  if (detail.status === 'FAILED' || detail.status === 'TIMEOUT') {
    return {
      tone: 'warning',
      label: '建议记录候选',
      summary: primaryFailureBucket
        ? `当前运行落在 ${primaryFailureBucket} 失败桶，可先记录为候选抖动并持续观察。`
        : '当前运行已失败/超时，可先记录为候选抖动，再结合 traceId 和失败摘要复核。',
      presets: [
        {
          status: 'FLAKY_CANDIDATE',
          label: '标记候选',
          tone: 'info',
          reasonCode,
          reasonSummary: `${reasonLead} 建议先作为候选抖动继续观察。`
        },
        {
          status: 'CONFIRMED_FLAKY',
          label: '直接确认',
          tone: 'warning',
          reasonCode,
          reasonSummary: `${reasonLead} 结合当前运行信号，人工判断已可直接确认抖动。`
        }
      ]
    };
  }

  if (detail.status === 'SUCCEEDED') {
    return {
      tone: 'success',
      label: '当前运行稳定',
      summary: '本次运行已成功，暂无额外 Flaky 标记建议；如属于长期波动场景，可按场景维度继续治理。',
      presets: []
    };
  }

  return {
    tone: 'info',
    label: '暂不建议标记',
    summary: '当前运行更适合先处理控制面阻断、取消或上下文问题，再决定是否进入 Flaky 治理。',
    presets: []
  };
}

export function buildUiE2eRunAuditTimeline(detail: UiE2eRunDetail): UiE2eRunAuditTimelineItem[] {
  const events: Array<UiE2eRunAuditTimelineItem & { sortOrder: number }> = [];

  pushUiE2eRunAuditTimelineEvent(events, {
    id: `${detail.id}:created`,
    kindLabel: 'RUN',
    title: '运行创建',
    detail: detail.requestKey
      ? `控制面已接收 requestKey=${detail.requestKey} 的运行请求。`
      : '控制面已接收运行请求并开始写入聚合摘要。',
    occurredAt: detail.createdAt,
    tone: 'info',
    sortOrder: 10
  });

  if (detail.idempotentReplay) {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:replay`,
      kindLabel: 'RUN',
      title: '幂等回放',
      detail: '当前请求命中已有运行摘要，本次未重复创建新的外部执行。',
      occurredAt: undefined,
      tone: 'info',
      sortOrder: 450
    });
  }

  if (detail.startedAt) {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:started`,
      kindLabel: 'RUN',
      title: '执行开始',
      detail: `runner=${detail.runnerMode}，运行已进入 ${detail.status} 链路。`,
      occurredAt: detail.startedAt,
      tone: isUiE2eRunActiveStatus(detail.status) ? 'info' : 'success',
      sortOrder: 30
    });
  }

  detail.stepResults.forEach((step, index) => {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:step:${step.id || index}`,
      kindLabel: 'STEP',
      title: `步骤 ${step.stepOrder} · ${step.status}`,
      detail: buildUiE2eRunStepAuditDetail(step),
      occurredAt: step.updatedAt || step.createdAt,
      tone: uiE2eToneFromStatus(step.status),
      sortOrder: 100 + index
    });
  });

  detail.artifacts.forEach((artifact, index) => {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:artifact:${artifact.id || index}`,
      kindLabel: 'ARTIFACT',
      title: `Artifact · ${artifact.artifactType} · ${artifact.captureStatus}`,
      detail: buildUiE2eArtifactAuditDetail(artifact),
      occurredAt: artifact.updatedAt || artifact.createdAt,
      tone: artifact.captureStatus === 'BLOCKED' ? 'warning' : uiE2eToneFromStatus(artifact.captureStatus),
      sortOrder: 200 + index
    });
  });

  if (detail.flakyMark) {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:flaky:${detail.flakyMark.id}`,
      kindLabel: 'FLAKY',
      title: `Flaky 标记 · ${detail.flakyMark.status}`,
      detail: buildUiE2eFlakyAuditDetail(detail.flakyMark),
      occurredAt: detail.flakyMark.updatedAt || detail.flakyMark.createdAt,
      tone: detail.flakyMark.status === 'CONFIRMED_FLAKY' ? 'warning' : uiE2eToneFromStatus(detail.flakyMark.status),
      sortOrder: 300
    });
  }

  if (!isUiE2eRunActiveStatus(detail.status)) {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:finished`,
      kindLabel: 'RUN',
      title: `运行终态 · ${detail.status}`,
      detail: buildUiE2eRunTerminalAuditDetail(detail),
      occurredAt: detail.finishedAt || detail.updatedAt,
      tone: uiE2eToneFromStatus(detail.status),
      sortOrder: 400
    });
  }

  return events.sort(compareUiE2eRunAuditTimelineEventOrder).map(({ sortOrder: _sortOrder, ...item }) => item);
}

export function buildUiE2eFlakyQueueOverview(flakyMarks: UiE2eFlakyMark[]): UiE2eFlakyQueueOverview {
  return {
    focusOptions: [
      {
        mode: 'candidates',
        label: '待确认',
        desc: '聚焦 FLAKY_CANDIDATE，优先复核是否需要升级为确认抖动。',
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'candidates').length,
        tone: 'info'
      },
      {
        mode: 'confirmed',
        label: '已确认',
        desc: '聚焦 CONFIRMED_FLAKY，方便沉淀治理池和回归观察名单。',
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'confirmed').length,
        tone: 'warning'
      },
      {
        mode: 'waived',
        label: '已豁免',
        desc: '聚焦 WAIVED，快速回看豁免原因与审计信息。',
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'waived').length,
        tone: 'info'
      },
      {
        mode: 'runLinked',
        label: '关联运行',
        desc: '聚焦带 runId 的标记，优先结合具体运行结果做定位。',
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'runLinked').length,
        tone: 'info'
      },
      {
        mode: 'sceneOnly',
        label: '仅场景',
        desc: '聚焦未绑定 runId 的场景级标记，方便做长期治理跟踪。',
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'sceneOnly').length,
        tone: 'warning'
      }
    ]
  };
}

export function filterUiE2eFlakyMarksByFocusMode(flakyMarks: UiE2eFlakyMark[], mode: UiE2eFlakyFocusMode) {
  switch (mode) {
    case 'candidates':
      return flakyMarks.filter((mark) => mark.status === 'FLAKY_CANDIDATE');
    case 'confirmed':
      return flakyMarks.filter((mark) => mark.status === 'CONFIRMED_FLAKY');
    case 'waived':
      return flakyMarks.filter((mark) => mark.status === 'WAIVED');
    case 'runLinked':
      return flakyMarks.filter((mark) => Boolean(mark.runId));
    case 'sceneOnly':
      return flakyMarks.filter((mark) => Boolean(mark.sceneId) && !mark.runId);
    case 'all':
    default:
      return flakyMarks;
  }
}

export function labelUiE2eFlakyFocusMode(mode: UiE2eFlakyFocusMode) {
  switch (mode) {
    case 'candidates':
      return '待确认';
    case 'confirmed':
      return '已确认';
    case 'waived':
      return '已豁免';
    case 'runLinked':
      return '关联运行';
    case 'sceneOnly':
      return '仅场景';
    case 'all':
    default:
      return '全部 Flaky';
  }
}

export function buildUiE2eFlakyListSummary(mark: UiE2eFlakyMark): UiE2eFlakyListSummary {
  const signals: string[] = [];
  if (mark.reasonCode) {
    pushUnique(signals, `reason=${mark.reasonCode}`);
  }
  if (mark.runStatus) {
    pushUnique(signals, `run=${mark.runStatus}`);
  }
  if (mark.runId) {
    pushUnique(signals, 'scope=run');
  } else if (mark.sceneId) {
    pushUnique(signals, 'scope=scene');
  }
  const actor = mark.updatedBy || mark.createdBy;
  if (actor) {
    pushUnique(signals, `by=${actor}`);
  }

  let detail = compactUiE2eText(mark.reasonSummary);
  if (!detail) {
    if (mark.status === 'CONFIRMED_FLAKY') {
      detail = '该标记已进入治理池，可结合失败分类和运行证据持续跟踪。';
    } else if (mark.status === 'FLAKY_CANDIDATE') {
      detail = '该记录仍是候选抖动，建议先复核失败分类与运行摘要。';
    } else if (mark.status === 'WAIVED') {
      detail = '该记录已豁免，保留原因用于审计和后续复盘。';
    } else if (mark.status === 'NONE') {
      detail = '当前记录已回落为 NONE，可继续观察后续运行是否再次波动。';
    } else {
      detail = 'Flaky 标记已生成，可继续查看关联运行和原因摘要。';
    }
  }

  let headline: string;
  if (mark.status === 'CONFIRMED_FLAKY') {
    headline = '已确认抖动';
  } else if (mark.status === 'FLAKY_CANDIDATE') {
    headline = '待人工确认';
  } else if (mark.status === 'WAIVED') {
    headline = '已豁免';
  } else if (mark.status === 'NONE') {
    headline = '已回落为 NONE';
  } else {
    headline = mark.status;
  }

  return {
    headline,
    detail,
    signals
  };
}

export function buildUiE2eFlakyDetailInsight(mark: UiE2eFlakyMark): UiE2eFlakyDetailInsight {
  const signals: string[] = [];
  if (mark.sceneRiskLevel) {
    pushUnique(signals, `risk=${mark.sceneRiskLevel}`);
  }
  if (mark.linkedRunCount > 0) {
    pushUnique(signals, `runs=${mark.linkedRunCount}`);
  }
  if (mark.latestFailureBucket) {
    pushUnique(signals, `latestFailureBucket=${mark.latestFailureBucket}`);
  }
  if (mark.runStatus) {
    pushUnique(signals, `runStatus=${mark.runStatus}`);
  }

  if (mark.status === 'CONFIRMED_FLAKY') {
    return {
      tone: mark.sceneRiskLevel === 'HIGH' || mark.sceneRiskLevel === 'CRITICAL' ? 'warning' : 'info',
      label: '治理池',
      summary: mark.linkedRunCount > 1
        ? `该场景已有 ${mark.linkedRunCount} 次关联运行，建议结合最近失败桶继续做稳定性治理。`
        : '该记录已进入 CONFIRMED_FLAKY 治理池，建议结合失败分类和审计信息持续跟踪。',
      signals
    };
  }

  if (mark.status === 'FLAKY_CANDIDATE') {
    return {
      tone: 'info',
      label: '待复核',
      summary: mark.latestFailureBucket
        ? `最近失败信号集中在 ${mark.latestFailureBucket}，建议人工复核后决定是否升级为确认抖动。`
        : '该记录仍处于候选阶段，建议优先补看关联运行和失败分类。',
      signals
    };
  }

  if (mark.status === 'WAIVED') {
    return {
      tone: 'info',
      label: '已豁免',
      summary: '该记录当前处于豁免状态，建议保留原因摘要与审计字段用于后续复盘。',
      signals
    };
  }

  return {
    tone: mark.sceneRiskLevel === 'CRITICAL' ? 'warning' : 'info',
    label: '观察中',
    summary: '当前 Flaky 详情已返回，可结合风险级别、关联运行和失败信号继续观察。',
    signals
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

export function buildUiE2eArtifactDownloadState(artifact: UiE2eArtifactManifest): UiE2eArtifactDownloadState {
  const captureStatus = (artifact.captureStatus || '').trim().toUpperCase();
  const blockedReason = extractUiE2eArtifactCaptureBlockedReason(artifact.redactionFlags);
  const downloadReady = booleanFromUnknown(artifact.redactionFlags?.rawArtifactDownloadReady)
    || (isUiE2eArtifactCapturedStatus(captureStatus) && (artifact.storageRef || '').startsWith('artifact://ui-e2e/'));

  if (captureStatus === 'BLOCKED') {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'warning',
      summary: explainUiE2eArtifactCaptureBlockedReason(blockedReason)
    };
  }

  if (captureStatus === 'FAILED') {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'error',
      summary: 'artifact 捕获失败，当前没有可下载的原始文件。'
    };
  }

  if (captureStatus === 'SKIPPED') {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'info',
      summary: 'artifact 本次未采集，因此只保留 manifest 摘要。'
    };
  }

  if (captureStatus === 'PENDING') {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'info',
      summary: 'artifact 仍在等待采集或回写，稍后刷新运行详情查看。'
    };
  }

  if (downloadReady) {
    return {
      canDownload: true,
      downloadReady: true,
      tone: 'success',
      summary: captureStatus === 'REDACTED'
        ? 'artifact 已完成脱敏落盘，可按权限下载当前产物。'
        : 'artifact 已入受控存储，可按权限下载原始产物。'
    };
  }

  if (isUiE2eArtifactCapturedStatus(captureStatus)) {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'warning',
      summary: 'artifact 已采集，但当前仅提供 manifest 摘要，原始下载未就绪。'
    };
  }

  return {
    canDownload: false,
    downloadReady: false,
    tone: 'info',
    summary: `artifact 当前处于 ${captureStatus || 'UNKNOWN'} 状态，请结合 manifest 摘要继续排查。`
  };
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
          waitPolicyText: prettyJson(step.waitPolicy),
          dataBindingText: prettyJson(step.dataBinding)
        }))
      : [{ ...initialUiE2eSceneStepDraft }]
  };
}

export function sceneDraftFromImport(detail: Pick<
  UiE2eSceneImport,
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
          waitPolicyText: prettyJson(step.waitPolicy),
          dataBindingText: prettyJson(step.dataBinding)
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
  const browsers = splitTags(draft.browsersText || '').map((item) => item.toUpperCase());
  if (!draft.projectId.trim()) issues.push('请填写 run projectId');
  if (!uuidPattern.test(draft.sceneId.trim())) issues.push('sceneId 需要是 UUID');
  if (!uuidPattern.test(draft.bundleId.trim())) issues.push('bundleId 需要是 UUID');
  if (!draft.baseUrlRef.trim()) issues.push('请填写 baseUrlRef');
  if (!uuidPattern.test(draft.accountLeaseRef.trim())) issues.push('accountLeaseRef 需要是 UUID');
  if (!browsers.length) issues.push('至少指定一个浏览器（CHROMIUM/FIREFOX/WEBKIT）');
  if (browsers.some((item) => !['CHROMIUM', 'FIREFOX', 'WEBKIT'].includes(item))) {
    issues.push('浏览器仅支持 CHROMIUM / FIREFOX / WEBKIT');
  }
  if (draft.visualRegressionEnabled && draft.baselineRunId.trim() && !uuidPattern.test(draft.baselineRunId.trim())) {
    issues.push('baselineRunId 需要是 UUID');
  }
  if (draft.visualMismatchThreshold.trim()) {
    const threshold = Number(draft.visualMismatchThreshold.trim());
    if (Number.isNaN(threshold) || threshold < 0 || threshold > 1) {
      issues.push('visualMismatchThreshold 需要在 0 到 1 之间');
    }
  }
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
      reason: optionalText(draft.reason),
      browsers,
      visualRegressionEnabled: draft.visualRegressionEnabled,
      baselineRunId: optionalText(draft.baselineRunId),
      visualMismatchThreshold: draft.visualMismatchThreshold.trim() ? Number(draft.visualMismatchThreshold.trim()) : undefined
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
  if (draft.status.trim() !== 'NONE' && !draft.reasonSummary.trim()) issues.push('请填写 flaky reasonSummary');
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

function uniqueOrderedRunIds(input: string) {
  const values = input
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean);
  return values.filter((item, index) => values.indexOf(item) === index);
}

function optionalPositiveInteger(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 200) {
    return undefined;
  }
  return parsed;
}

function buildUiE2eSceneStepPayload(
  step: UiE2eSceneStepDraft,
  index: number,
  issues: string[]
): UiE2eSceneStepPayload | undefined {
  const actionSummary = parseObjectText(step.actionSummaryText, `steps[${index}].actionSummary`, issues);
  const locatorStrategy = parseObjectText(step.locatorStrategyText, `steps[${index}].locatorStrategy`, issues);
  const assertionSummary = parseObjectText(step.assertionSummaryText, `steps[${index}].assertionSummary`, issues);
  const waitPolicy = parseObjectText(step.waitPolicyText, `steps[${index}].waitPolicy`, issues);
  const dataBinding = parseObjectText(step.dataBindingText, `steps[${index}].dataBinding`, issues);
  if (!step.stepType.trim()) {
    issues.push(`步骤 ${index + 1} 缺少 stepType`);
    return undefined;
  }
  return {
    stepType: step.stepType.trim(),
    actionSummary,
    locatorStrategy,
    assertionSummary,
    waitPolicy,
    dataBinding
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
    case 'UI_E2E_VISUAL_REGRESSION_FAILED':
      return {
        tone: 'error' as const,
        label: 'VISUAL_REGRESSION_FAILED',
        summary: '截图 Diff 已完成，但至少一个浏览器的像素差异超过阈值，运行被判定为失败。',
        actions: [
          '优先查看 DIFF/BASELINE/ACTUAL 三类截图产物，确认是预期 UI 变更还是样式回归。',
          '如属于预期改版，请更新基线运行；如属于噪声波动，再评估是否需要放宽 mismatch threshold。'
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

function isUiE2eArtifactCapturedStatus(status: string) {
  return status === 'CAPTURED' || status === 'REDACTED';
}

function runFlakyReasonCode(failureCode?: string, failureBucket?: string) {
  switch ((failureBucket || '').trim().toUpperCase()) {
    case 'LOCATOR':
      return 'locator-drift';
    case 'ENVIRONMENT_TIMEOUT':
      return 'env-timeout';
    case 'ASSERTION':
      return 'assertion-variance';
    case 'AUTHORIZATION':
      return 'permission-variance';
    case 'ACCOUNT':
      return 'account-instability';
    case 'TEST_DATA':
      return 'data-precondition';
    case 'RUNNER':
      return 'runner-variance';
    case 'UNKNOWN':
      return 'unknown-instability';
    default:
      break;
  }
  switch ((failureCode || '').trim()) {
    case 'UI_E2E_RUNNER_DISABLED':
      return 'runner-disabled';
    case 'UI_E2E_RUNNER_CANCELED':
      return 'runner-canceled';
    case 'UI_E2E_ACCOUNT_LEASE_INVALID':
      return 'account-lease-invalid';
    case 'UI_E2E_BASE_URL_NOT_ALLOWED':
      return 'base-url-not-allowed';
    case 'UI_E2E_RESOURCE_SCOPE_DENIED':
      return 'resource-scope-denied';
    case 'UI_E2E_SCENE_NOT_READY':
      return 'scene-not-ready';
    case 'UI_E2E_BUNDLE_NOT_READY':
      return 'bundle-not-ready';
    default:
      return 'run-summary';
  }
}

function runFlakyReasonLead(detail: UiE2eRunDetail, failureBucket?: string) {
  if (failureBucket) {
    return `运行在 ${failureBucket} 失败桶出现波动。`;
  }
  if (detail.failureCode) {
    return `运行返回 ${detail.failureCode} 失败信号。`;
  }
  return `运行当前状态为 ${detail.status}，建议结合 traceId 和步骤摘要继续观察。`;
}

function buildUiE2eRunStepAuditDetail(step: UiE2eRunStepResult) {
  const parts: string[] = [];
  if (step.failureBucket) {
    parts.push(`failureBucket=${step.failureBucket}`);
  }
  if (step.errorCode) {
    parts.push(`errorCode=${step.errorCode}`);
  }
  if (step.durationMs > 0) {
    parts.push(`duration=${step.durationMs}ms`);
  }
  return parts.length ? parts.join(' · ') : '步骤结果已回写到控制面摘要。';
}

function buildUiE2eArtifactAuditDetail(artifact: UiE2eArtifactManifest) {
  if (artifact.captureStatus === 'BLOCKED') {
    return explainUiE2eArtifactCaptureBlockedReason(
      extractUiE2eArtifactCaptureBlockedReason(artifact.redactionFlags)
    );
  }
  const parts: string[] = [];
  if (artifact.artifactDigest) {
    parts.push(`digest=${artifact.artifactDigest}`);
  }
  if (artifact.storageRef) {
    parts.push('storageRef=ready');
  }
  if (artifact.sizeBytes > 0) {
    parts.push(`size=${artifact.sizeBytes}B`);
  }
  return parts.length ? parts.join(' · ') : 'artifact manifest 已记录，可继续查看脱敏摘要。';
}

function buildUiE2eFlakyAuditDetail(mark: UiE2eFlakyMark) {
  const parts: string[] = [];
  if (mark.reasonCode) {
    parts.push(`reason=${mark.reasonCode}`);
  }
  const summary = compactUiE2eText(mark.reasonSummary, 96);
  if (summary) {
    parts.push(summary);
  }
  return parts.length ? parts.join(' · ') : 'Flaky 标记已写入治理记录。';
}

function buildUiE2eRunTerminalAuditDetail(detail: UiE2eRunDetail) {
  const parts: string[] = [];
  if (detail.failureCode) {
    parts.push(`failure=${compactUiE2eFailureCode(detail.failureCode)}`);
  }
  const failureSummary = compactUiE2eText(detail.failureSummary, 96);
  if (failureSummary) {
    parts.push(failureSummary);
  }
  if (detail.traceId) {
    parts.push(`traceId=${detail.traceId}`);
  }
  return parts.length ? parts.join(' · ') : '运行已进入终态，聚合摘要已稳定。';
}

function pushUiE2eRunAuditTimelineEvent(
  events: Array<UiE2eRunAuditTimelineItem & { sortOrder: number }>,
  event: UiE2eRunAuditTimelineItem & { sortOrder: number }
) {
  events.push(event);
}

function compareUiE2eRunAuditTimelineEventOrder(
  left: UiE2eRunAuditTimelineItem & { sortOrder: number },
  right: UiE2eRunAuditTimelineItem & { sortOrder: number }
) {
  const leftTime = uiE2eAuditEventTime(left.occurredAt);
  const rightTime = uiE2eAuditEventTime(right.occurredAt);

  if (leftTime != null && rightTime != null && leftTime !== rightTime) {
    return leftTime - rightTime;
  }
  if (leftTime != null && rightTime == null) {
    return -1;
  }
  if (leftTime == null && rightTime != null) {
    return 1;
  }
  return left.sortOrder - right.sortOrder;
}

function uiE2eAuditEventTime(value?: string) {
  if (!value) {
    return undefined;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function latestUiE2eEntity<T>(items: T[], timestampSelector: (item: T) => string | undefined) {
  return [...items].sort((left, right) => {
    const leftTime = uiE2eAuditEventTime(timestampSelector(left)) ?? 0;
    const rightTime = uiE2eAuditEventTime(timestampSelector(right)) ?? 0;
    return rightTime - leftTime;
  })[0];
}

function firstUiE2eTimestamp(...values: Array<string | undefined>) {
  return values.find((value) => Boolean(value));
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

function numberOrUndefined(value: unknown) {
  const parsed = numberFromUnknown(value, Number.NaN);
  return Number.isFinite(parsed) ? parsed : undefined;
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

function stringFromUnknown(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function stringArrayFromUnknown(value: unknown) {
  if (Array.isArray(value)) {
    return value
      .map((item) => stringFromUnknown(item))
      .filter((item): item is string => Boolean(item));
  }
  return [];
}

function isUiE2eRunFailureStatus(status?: string) {
  return status === 'FAILED' || status === 'TIMEOUT';
}

function isUiE2eBundleSubmittableStatus(status?: string) {
  return status === 'DRAFT' || status === 'REJECTED' || status === 'STATIC_CHECK_FAILED';
}

function isUiE2eBundleStaticFailed(bundle: UiE2eBundleSummary) {
  return bundle.status === 'STATIC_CHECK_FAILED' || bundle.staticCheckStatus === 'SCRIPT_STATIC_CHECK_FAILED';
}

function compactUiE2eStaticCheckStatus(staticCheckStatus?: string) {
  if (!staticCheckStatus) {
    return undefined;
  }
  return staticCheckStatus.startsWith('SCRIPT_') ? staticCheckStatus.slice('SCRIPT_'.length) : staticCheckStatus;
}

function compactUiE2eFailureCode(failureCode?: string) {
  if (!failureCode) {
    return undefined;
  }
  return failureCode.startsWith('UI_E2E_') ? failureCode.slice('UI_E2E_'.length) : failureCode;
}

function compactUiE2eText(value?: string, max = 72) {
  if (!value) {
    return undefined;
  }
  const compact = value.trim().replace(/\s+/g, ' ');
  if (!compact) {
    return undefined;
  }
  return compact.length > max ? `${compact.slice(0, max - 3)}...` : compact;
}

function uiE2eToneFromStatus(status?: string): UiE2eWorkbenchTone {
  if (!status) {
    return 'info';
  }
  if (['APPROVED', 'SUCCEEDED', 'CAPTURED', 'READY', 'CONFIRMED_FLAKY'].includes(status)) {
    return 'success';
  }
  if (['DRAFT', 'REVIEWING', 'QUEUED', 'RUNNING', 'PENDING', 'FLAKY_CANDIDATE'].includes(status)) {
    return 'info';
  }
  if (['FAILED', 'BLOCKED', 'REJECTED', 'CANCELED', 'TIMEOUT'].includes(status)) {
    return 'danger';
  }
  if (['ARCHIVED', 'NONE', 'WAIVED', 'SKIPPED', 'DISABLED'].includes(status)) {
    return 'info';
  }
  return 'warning';
}

function extractUiE2eSceneSourceType(sourceSummary: Record<string, unknown>) {
  const value = sourceSummary.sourceType;
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}
