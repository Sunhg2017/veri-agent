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
import { translate } from './platform/i18n';

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
    notices.push({ tone: 'warning', message: translate('auto.k2332', { value0: health.status }) });
  }
  if (health && !health.runnerEnabled) {
    notices.push({ tone: 'warning', message: translate('auto.k2333') });
  }
  if (health && !health.allowlistEnabled) {
    notices.push({ tone: 'warning', message: translate('auto.k2334') });
  }
  if (health) {
    const queuedTasks = numberFromUnknown(health.runnerCapacity?.queuedTasks);
    const saturated = booleanFromUnknown(health.runnerCapacity?.saturated);
    const backfillReady = booleanFromUnknown(health.runnerCapacity?.summaryBackfillReady, true);
    if (saturated) {
      notices.push({ tone: 'warning', message: translate('auto.k2335') });
    }
    if (queuedTasks > 0) {
      notices.push({ tone: 'info', message: translate('auto.k2336', { value0: queuedTasks }) });
    }
    if (!backfillReady) {
      notices.push({ tone: 'info', message: translate('auto.k2337') });
    }
  }
  if (recentFailures > 0) {
    notices.push({ tone: 'warning', message: translate('auto.k2338', { value0: recentFailures }) });
  }
  if (blockedRuns > 0) {
    notices.push({ tone: 'info', message: translate('auto.k2339', { value0: blockedRuns }) });
  }
  if (confirmedFlaky > 0) {
    notices.push({ tone: 'info', message: translate('auto.k2340', { value0: confirmedFlaky }) });
  }

  const runnerLabel = health ? `${health.runnerEnabled ? 'ON' : 'OFF'} · ${health.runnerMode || 'UNKNOWN'}` : translate('auto.k1118');
  const runnerTone: UiE2eWorkbenchTone = !health
    ? 'info'
    : health.status !== 'UP'
      ? 'danger'
      : health.runnerEnabled
        ? 'success'
        : 'warning';
  const allowlistLabel = health ? (health.allowlistEnabled ? `ON (${health.allowlistHostCount})` : 'OFF') : translate('auto.k1118');
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
        label: translate('auto.k2341'),
        desc: translate('auto.k2342'),
        count: filterUiE2eScenesByFocusMode(scenes, 'approved').length,
        tone: 'success'
      },
      {
        mode: 'reviewing',
        label: translate('auto.k2343'),
        desc: translate('auto.k2344'),
        count: filterUiE2eScenesByFocusMode(scenes, 'reviewing').length,
        tone: 'info'
      },
      {
        mode: 'draft',
        label: translate('auto.k2345'),
        desc: translate('auto.k2346'),
        count: filterUiE2eScenesByFocusMode(scenes, 'draft').length,
        tone: 'warning'
      },
      {
        mode: 'highRisk',
        label: translate('auto.k1012'),
        desc: translate('auto.k2347'),
        count: filterUiE2eScenesByFocusMode(scenes, 'highRisk').length,
        tone: 'danger'
      },
      {
        mode: 'disabled',
        label: translate('auto.k0067'),
        desc: translate('auto.k2348'),
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
      return translate('auto.k2341');
    case 'reviewing':
      return translate('auto.k2343');
    case 'draft':
      return translate('auto.k2345');
    case 'highRisk':
      return translate('auto.k1012');
    case 'disabled':
      return translate('auto.k0067');
    case 'all':
    default:
      return translate('auto.k2349');
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
    detail = translate('auto.k2350');
  } else if (scene.status === 'REVIEWING') {
    detail = translate('auto.k2351');
  } else if (scene.status === 'DRAFT') {
    detail = translate('auto.k2352');
  } else if (scene.status === 'DISABLED') {
    detail = translate('auto.k2353');
  } else if (scene.status === 'ARCHIVED') {
    detail = translate('auto.k2354');
  } else {
    detail = translate('auto.k2355');
  }

  return {
    headline: scene.status === 'APPROVED'
      ? translate('auto.k2356')
      : scene.status === 'REVIEWING'
        ? translate('auto.k2357')
        : scene.status === 'DRAFT'
          ? translate('auto.k2358')
          : scene.status === 'DISABLED'
            ? translate('auto.k0067')
            : scene.status === 'ARCHIVED'
              ? translate('auto.k2359')
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
        label: translate('auto.k1021'),
        desc: translate('auto.k2360'),
        count: filterUiE2eBundlesByFocusMode(bundles, 'reviewing').length,
        tone: 'info'
      },
      {
        mode: 'submittable',
        label: translate('auto.k2361'),
        desc: translate('auto.k2362'),
        count: filterUiE2eBundlesByFocusMode(bundles, 'submittable').length,
        tone: 'warning'
      },
      {
        mode: 'approved',
        label: translate('auto.k2363'),
        desc: translate('auto.k2364'),
        count: filterUiE2eBundlesByFocusMode(bundles, 'approved').length,
        tone: 'success'
      },
      {
        mode: 'staticFailed',
        label: translate('auto.k2365'),
        desc: translate('auto.k2366'),
        count: filterUiE2eBundlesByFocusMode(bundles, 'staticFailed').length,
        tone: 'danger'
      },
      {
        mode: 'rejected',
        label: translate('auto.k2367'),
        desc: translate('auto.k2368'),
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
      return translate('auto.k1021');
    case 'submittable':
      return translate('auto.k2361');
    case 'approved':
      return translate('auto.k2363');
    case 'staticFailed':
      return translate('auto.k2365');
    case 'rejected':
      return translate('auto.k2367');
    case 'all':
    default:
      return translate('auto.k2369');
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
    detail = translate('auto.k2370');
  } else if (bundle.status === 'REVIEWING') {
    detail = translate('auto.k2371');
  } else if (bundle.status === 'APPROVED') {
    detail = translate('auto.k2372');
  } else if (bundle.status === 'REJECTED') {
    detail = translate('auto.k2373');
  } else if (bundle.status === 'ARCHIVED') {
    detail = translate('auto.k2374');
  } else {
    detail = translate('auto.k2375');
  }

  let headline: string;
  if (isUiE2eBundleStaticFailed(bundle)) {
    headline = staticCheckLabel || 'STATIC_CHECK_FAILED';
  } else if (bundle.status === 'REVIEWING') {
    headline = translate('auto.k2376');
  } else if (bundle.status === 'APPROVED') {
    headline = translate('auto.k2377');
  } else if (bundle.status === 'REJECTED') {
    headline = translate('auto.k2378');
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
        label: translate('auto.k1884'),
        desc: translate('auto.k2379'),
        count: filterUiE2eRunsByFocusMode(runs, 'active').length,
        tone: 'info'
      },
      {
        mode: 'failures',
        label: translate('auto.k0856'),
        desc: translate('auto.k2380'),
        count: filterUiE2eRunsByFocusMode(runs, 'failures').length,
        tone: 'danger'
      },
      {
        mode: 'blocked',
        label: translate('auto.k2381'),
        desc: translate('auto.k2382'),
        count: filterUiE2eRunsByFocusMode(runs, 'blocked').length,
        tone: 'warning'
      },
      {
        mode: 'flaky',
        label: 'Confirmed Flaky',
        desc: translate('auto.k2383'),
        count: filterUiE2eRunsByFocusMode(runs, 'flaky').length,
        tone: 'warning'
      },
      {
        mode: 'runnerDisabled',
        label: 'Runner Off',
        desc: translate('auto.k2384'),
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
      return translate('auto.k1884');
    case 'failures':
      return translate('auto.k0856');
    case 'blocked':
      return translate('auto.k2381');
    case 'flaky':
      return 'Confirmed Flaky';
    case 'runnerDisabled':
      return 'Runner Off';
    case 'all':
    default:
      return translate('auto.k2385');
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
    detail = translate('auto.k2386');
  } else if (run.status === 'BLOCKED') {
    detail = translate('auto.k2387');
  } else if (isUiE2eRunFailureStatus(run.status)) {
    detail = translate('auto.k2388');
  } else if (run.status === 'CANCELED') {
    detail = translate('auto.k2389');
  } else if (isUiE2eRunActiveStatus(run.status)) {
    detail = translate('auto.k2390');
  } else if (run.flakyStatus === 'CONFIRMED_FLAKY') {
    detail = translate('auto.k2391');
  } else {
    detail = translate('auto.k2392');
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
      label: translate('auto.k2393'),
      summary: translate('auto.k2394', { value0: missingFields.join(' / ') }),
      checks
    };
  }

  if (health && !health.runnerEnabled) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Runner Disabled',
      summary: translate('auto.k2395'),
      checks
    };
  }

  if (scene && scene.status !== 'APPROVED') {
    return {
      ready: false,
      tone: 'warning',
      label: 'Scene Not Ready',
      summary: translate('auto.k2396', { value0: scene.code || draft.sceneId, value1: scene.status }),
      checks
    };
  }

  if (bundle && bundle.status !== 'APPROVED') {
    return {
      ready: false,
      tone: 'warning',
      label: 'Bundle Not Ready',
      summary: translate('auto.k2397', { value0: bundle.sceneCode || draft.bundleId, value1: bundle.status }),
      checks
    };
  }

  if (bundle?.sceneStatus && bundle.sceneStatus !== 'APPROVED') {
    return {
      ready: false,
      tone: 'warning',
      label: 'Scene Bundle Mismatch',
      summary: translate('auto.k2398', { value0: bundle.sceneStatus }),
      checks
    };
  }

  return {
    ready: true,
    tone: 'success',
    label: 'Ready To Run',
    summary: translate('auto.k2399'),
    checks
  };
}

export function buildUiE2eBatchRunPayload(draft: UiE2eBatchRunDraft): { payload?: BatchCreateUiE2eRunPayload; issues: string[] } {
  const issues: string[] = [];
  const projectId = draft.projectId.trim();
  const sceneIds = uniqueOrderedRunIds(draft.sceneIdsText);
  const browsers = splitTags(draft.browsersText || '').map((item) => item.toUpperCase());

  if (!projectId) issues.push(translate('auto.k2400'));
  if (!sceneIds.length) issues.push(translate('auto.k2401'));
  if (sceneIds.length > 100) issues.push(translate('auto.k2402'));
  if (sceneIds.length) {
    const invalidSceneId = sceneIds.find((sceneId) => !uuidPattern.test(sceneId));
    if (invalidSceneId) {
      issues.push(translate('auto.k2403', { value0: invalidSceneId }));
    }
  }
  if (!draft.baseUrlRef.trim()) issues.push(translate('auto.k2404'));
  if (!uuidPattern.test(draft.accountLeaseRef.trim())) issues.push(translate('auto.k2405'));
  if (!browsers.length) issues.push(translate('auto.k2406'));
  if (browsers.some((item) => !['CHROMIUM', 'FIREFOX', 'WEBKIT'].includes(item))) {
    issues.push(translate('auto.k2407'));
  }
  if (draft.visualRegressionEnabled && draft.baselineRunId.trim() && !uuidPattern.test(draft.baselineRunId.trim())) {
    issues.push(translate('auto.k2408'));
  }
  if (draft.visualMismatchThreshold.trim()) {
    const threshold = Number(draft.visualMismatchThreshold.trim());
    if (Number.isNaN(threshold) || threshold < 0 || threshold > 1) {
      issues.push(translate('auto.k2409'));
    }
  }
  if (draft.requestKeyPrefix.trim() && !requestKeyPattern.test(draft.requestKeyPrefix.trim())) {
    issues.push(translate('auto.k2410'));
  }
  if (draft.reason.length > 512) issues.push(translate('auto.k2411'));

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
      label: translate('auto.k2412'),
      summary: issues[0] || translate('auto.k2413'),
      checks
    };
  }

  if (health && payload.sceneIds.length > health.maxScenesPerRun) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Batch Size Exceeded',
      summary: translate('auto.k2414', { value0: health.maxScenesPerRun }),
      checks
    };
  }

  if (health && !health.runnerEnabled) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Runner Disabled',
      summary: translate('auto.k2415'),
      checks
    };
  }

  if (health && !booleanFromUnknown(health.runnerCapacity?.batchRunReady, true)) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Batch Pending',
      summary: translate('auto.k2416'),
      checks
    };
  }

  if (nonApprovedScenes.length) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Scene Not Ready',
      summary: translate('auto.k2417', { value0: nonApprovedScenes.length }),
      checks
    };
  }

  return {
    ready: true,
    tone: 'success',
    label: 'Batch Ready',
    summary: translate('auto.k2418', { value0: payload.sceneIds.length }),
    checks
  };
}

export function buildUiE2eRunBackfillPayload(draft: UiE2eRunBackfillDraft): { payload?: BackfillUiE2eRunSummaryPayload; issues: string[] } {
  const issues: string[] = [];
  const projectId = draft.projectId.trim();
  const runIds = uniqueOrderedRunIds(draft.runIdsText);
  const limit = optionalPositiveInteger(draft.limit);

  if (!projectId) {
    issues.push(translate('auto.k2419'));
  }
  if (!runIds.length && limit == null) {
    issues.push(translate('auto.k2420'));
  }
  if (runIds.length > 200) {
    issues.push(translate('auto.k2421'));
  }
  if (draft.limit.trim() && limit == null) {
    issues.push(translate('auto.k2422'));
  }
  if (runIds.length) {
    const invalidRunId = runIds.find((runId) => !uuidPattern.test(runId));
    if (invalidRunId) {
      issues.push(translate('auto.k2423', { value0: invalidRunId }));
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
      label: translate('auto.k2424'),
      summary: issues[0] || translate('auto.k2425'),
      checks
    };
  }

  if (health && !booleanFromUnknown(health.runnerCapacity?.summaryBackfillReady)) {
    return {
      ready: false,
      tone: 'warning',
      label: 'Backfill Pending',
      summary: translate('auto.k2426'),
      checks
    };
  }

  return {
    ready: true,
    tone: 'success',
    label: 'Backfill Ready',
    summary: payload.runIds?.length
      ? translate('auto.k2427', { value0: payload.runIds.length })
      : translate('auto.k2428', { value0: payload.limit || 0 }),
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
        ? translate('auto.k2429', { value0: result.updatedCount, value1: result.failedCount })
        : translate('auto.k2430', { value0: result.failedCount }),
      signals,
      failedItems
    };
  }

  if (result.updatedCount > 0) {
    return {
      tone: 'success',
      label: 'Backfill Updated',
      summary: translate('auto.k2431', { value0: result.updatedCount, value1: result.unchangedCount }),
      signals,
      failedItems
    };
  }

  return {
    tone: 'info',
    label: 'Backfill Unchanged',
    summary: translate('auto.k2432', { value0: result.requestedCount }),
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
        ? translate('auto.k2433', { value0: result.createdCount, value1: result.replayedCount, value2: result.failedCount })
        : translate('auto.k2434', { value0: result.failedCount }),
      signals,
      failedItems
    };
  }

  if (result.replayedCount > 0) {
    return {
      tone: 'success',
      label: 'Batch Replayed',
      summary: translate('auto.k2435', { value0: result.createdCount, value1: result.replayedCount }),
      signals,
      failedItems
    };
  }

  return {
    tone: 'success',
    label: 'Batch Created',
    summary: translate('auto.k2436', { value0: result.createdCount }),
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
    pushUnique(signals, translate('auto.k2437'));
  }
  if (!rawArtifactDownloadReady && detail.artifacts.length > 0) {
    pushUnique(signals, translate('auto.k2438'));
  }
  if (stepResultCount === 0 && !isUiE2eRunActiveStatus(detail.status)) {
    pushUnique(signals, translate('auto.k2439'));
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
    summary = translate('auto.k2440', { value0: stepResultCount, value1: detail.artifacts.length });
    pushUnique(nextActions, translate('auto.k2441'));
  } else if (failureCodeDiagnosis) {
    tone = failureCodeDiagnosis.tone;
    label = failureCodeDiagnosis.label;
    summary = failureCodeDiagnosis.summary;
    failureCodeDiagnosis.actions.forEach((item) => pushUnique(nextActions, item));
  } else if (detail.status === 'FAILED') {
    tone = flakyStatus === 'CONFIRMED_FLAKY' ? 'warning' : 'error';
    label = flakyStatus === 'CONFIRMED_FLAKY' ? 'CONFIRMED_FLAKY' : 'FAILED';
    summary = primaryFailureBucketEntry
      ? translate('auto.k2442', { value0: primaryFailureBucketEntry[0] })
      : translate('auto.k2443');
  } else if (detail.status === 'TIMEOUT') {
    tone = 'error';
    label = 'TIMEOUT';
    summary = translate('auto.k2444');
  } else if (detail.status === 'BLOCKED') {
    tone = 'warning';
    label = 'BLOCKED';
    summary = primaryFailureBucketEntry?.[0] === 'RUNNER'
      ? translate('auto.k2445')
      : translate('auto.k2446');
  } else if (detail.status === 'CANCELED') {
    tone = 'warning';
    label = 'CANCELED';
    summary = translate('auto.k2447');
  } else if (detail.status === 'SUCCEEDED') {
    tone = blockedArtifacts.length ? 'warning' : 'success';
    label = blockedArtifacts.length ? 'SUCCEEDED_WITH_WARNINGS' : 'SUCCEEDED';
    summary = blockedArtifacts.length
      ? translate('auto.k2448', { value0: blockedArtifacts.length })
      : translate('auto.k2449');
  } else {
    tone = 'info';
    label = detail.status || 'UNKNOWN';
    summary = translate('auto.k2450');
  }

  collectFailureBucketActions(detail.stepResults, failureBucketCounts).forEach((item) => pushUnique(nextActions, item));
  blockedArtifactReasons
    .map((reason) => artifactBlockedReasonAction(reason))
    .forEach((item) => pushUnique(nextActions, item));
  if (visualRegressionEnabled && visualComparisonCount === 0) {
    pushUnique(nextActions, translate('auto.k2451'));
  }
  if (visualMismatchBrowsers.length) {
    pushUnique(nextActions, translate('auto.k2452', { value0: visualMismatchBrowsers.join(' / ') }));
  }

  if (detail.status === 'CANCELED' || detail.failureCode === 'UI_E2E_RUNNER_CANCELED') {
    pushUnique(nextActions, translate('auto.k2453'));
  }
  if (flakyStatus === 'CONFIRMED_FLAKY') {
    pushUnique(nextActions, translate('auto.k2454'));
  }
  if (!nextActions.length) {
    pushUnique(nextActions, translate('auto.k2455'));
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
      label: translate('auto.k2456'),
      summary: translate('auto.k2457'),
      presets: []
    };
  }

  if (currentStatus === 'CONFIRMED_FLAKY') {
    return {
      tone: 'warning',
      label: translate('auto.k2458'),
      summary: translate('auto.k2459'),
      presets: [
        {
          status: 'WAIVED',
          label: translate('auto.k2460'),
          tone: 'info',
          reasonCode,
          reasonSummary: translate('auto.k2461', { value0: reasonLead })
        },
        {
          status: 'FLAKY_CANDIDATE',
          label: translate('auto.k2462'),
          tone: 'info',
          reasonCode,
          reasonSummary: translate('auto.k2463', { value0: reasonLead })
        }
      ]
    };
  }

  if (currentStatus === 'FLAKY_CANDIDATE') {
    return {
      tone: 'info',
      label: translate('auto.k2464'),
      summary: translate('auto.k2465'),
      presets: [
        {
          status: 'CONFIRMED_FLAKY',
          label: translate('auto.k2466'),
          tone: 'warning',
          reasonCode,
          reasonSummary: translate('auto.k2467', { value0: reasonLead })
        },
        {
          status: 'WAIVED',
          label: translate('auto.k2460'),
          tone: 'info',
          reasonCode,
          reasonSummary: translate('auto.k2468', { value0: reasonLead })
        }
      ]
    };
  }

  if (currentStatus === 'WAIVED') {
    return {
      tone: 'info',
      label: translate('auto.k2469'),
      summary: translate('auto.k2470'),
      presets: [
        {
          status: 'FLAKY_CANDIDATE',
          label: translate('auto.k2471'),
          tone: 'info',
          reasonCode,
          reasonSummary: translate('auto.k2472', { value0: reasonLead })
        },
        {
          status: 'CONFIRMED_FLAKY',
          label: translate('auto.k2473'),
          tone: 'warning',
          reasonCode,
          reasonSummary: translate('auto.k2474', { value0: reasonLead })
        }
      ]
    };
  }

  if (detail.status === 'FAILED' || detail.status === 'TIMEOUT') {
    return {
      tone: 'warning',
      label: translate('auto.k2475'),
      summary: primaryFailureBucket
        ? translate('auto.k2476', { value0: primaryFailureBucket })
        : translate('auto.k2477'),
      presets: [
        {
          status: 'FLAKY_CANDIDATE',
          label: translate('auto.k2478'),
          tone: 'info',
          reasonCode,
          reasonSummary: translate('auto.k2479', { value0: reasonLead })
        },
        {
          status: 'CONFIRMED_FLAKY',
          label: translate('auto.k2480'),
          tone: 'warning',
          reasonCode,
          reasonSummary: translate('auto.k2481', { value0: reasonLead })
        }
      ]
    };
  }

  if (detail.status === 'SUCCEEDED') {
    return {
      tone: 'success',
      label: translate('auto.k2482'),
      summary: translate('auto.k2483'),
      presets: []
    };
  }

  return {
    tone: 'info',
    label: translate('auto.k2484'),
    summary: translate('auto.k2485'),
    presets: []
  };
}

export function buildUiE2eRunAuditTimeline(detail: UiE2eRunDetail): UiE2eRunAuditTimelineItem[] {
  const events: Array<UiE2eRunAuditTimelineItem & { sortOrder: number }> = [];

  pushUiE2eRunAuditTimelineEvent(events, {
    id: `${detail.id}:created`,
    kindLabel: 'RUN',
    title: translate('auto.k2486'),
    detail: detail.requestKey
      ? translate('auto.k2487', { value0: detail.requestKey })
      : translate('auto.k2488'),
    occurredAt: detail.createdAt,
    tone: 'info',
    sortOrder: 10
  });

  if (detail.idempotentReplay) {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:replay`,
      kindLabel: 'RUN',
      title: translate('auto.k2489'),
      detail: translate('auto.k2490'),
      occurredAt: undefined,
      tone: 'info',
      sortOrder: 450
    });
  }

  if (detail.startedAt) {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:started`,
      kindLabel: 'RUN',
      title: translate('auto.k2491'),
      detail: translate('auto.k2492', { value0: detail.runnerMode, value1: detail.status }),
      occurredAt: detail.startedAt,
      tone: isUiE2eRunActiveStatus(detail.status) ? 'info' : 'success',
      sortOrder: 30
    });
  }

  detail.stepResults.forEach((step, index) => {
    pushUiE2eRunAuditTimelineEvent(events, {
      id: `${detail.id}:step:${step.id || index}`,
      kindLabel: 'STEP',
      title: translate('auto.k2493', { value0: step.stepOrder, value1: step.status }),
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
      title: translate('auto.k2494', { value0: detail.flakyMark.status }),
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
      title: translate('auto.k2495', { value0: detail.status }),
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
        label: translate('auto.k2496'),
        desc: translate('auto.k2497'),
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'candidates').length,
        tone: 'info'
      },
      {
        mode: 'confirmed',
        label: translate('auto.k2498'),
        desc: translate('auto.k2499'),
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'confirmed').length,
        tone: 'warning'
      },
      {
        mode: 'waived',
        label: translate('auto.k2500'),
        desc: translate('auto.k2501'),
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'waived').length,
        tone: 'info'
      },
      {
        mode: 'runLinked',
        label: translate('auto.k2502'),
        desc: translate('auto.k2503'),
        count: filterUiE2eFlakyMarksByFocusMode(flakyMarks, 'runLinked').length,
        tone: 'info'
      },
      {
        mode: 'sceneOnly',
        label: translate('auto.k2504'),
        desc: translate('auto.k2505'),
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
      return translate('auto.k2496');
    case 'confirmed':
      return translate('auto.k2498');
    case 'waived':
      return translate('auto.k2500');
    case 'runLinked':
      return translate('auto.k2502');
    case 'sceneOnly':
      return translate('auto.k2504');
    case 'all':
    default:
      return translate('auto.k2506');
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
      detail = translate('auto.k2507');
    } else if (mark.status === 'FLAKY_CANDIDATE') {
      detail = translate('auto.k2508');
    } else if (mark.status === 'WAIVED') {
      detail = translate('auto.k2509');
    } else if (mark.status === 'NONE') {
      detail = translate('auto.k2510');
    } else {
      detail = translate('auto.k2511');
    }
  }

  let headline: string;
  if (mark.status === 'CONFIRMED_FLAKY') {
    headline = translate('auto.k2458');
  } else if (mark.status === 'FLAKY_CANDIDATE') {
    headline = translate('auto.k2512');
  } else if (mark.status === 'WAIVED') {
    headline = translate('auto.k2500');
  } else if (mark.status === 'NONE') {
    headline = translate('auto.k2513');
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
      label: translate('auto.k2514'),
      summary: mark.linkedRunCount > 1
        ? translate('auto.k2515', { value0: mark.linkedRunCount })
        : translate('auto.k2516'),
      signals
    };
  }

  if (mark.status === 'FLAKY_CANDIDATE') {
    return {
      tone: 'info',
      label: translate('auto.k2517'),
      summary: mark.latestFailureBucket
        ? translate('auto.k2518', { value0: mark.latestFailureBucket })
        : translate('auto.k2519'),
      signals
    };
  }

  if (mark.status === 'WAIVED') {
    return {
      tone: 'info',
      label: translate('auto.k2500'),
      summary: translate('auto.k2520'),
      signals
    };
  }

  return {
    tone: mark.sceneRiskLevel === 'CRITICAL' ? 'warning' : 'info',
    label: translate('auto.k2521'),
    summary: translate('auto.k2522'),
    signals
  };
}

export function explainUiE2eFailureBucket(bucket?: string) {
  switch ((bucket || '').trim().toUpperCase()) {
    case 'LOCATOR':
      return translate('auto.k2523');
    case 'AUTHORIZATION':
      return translate('auto.k2524');
    case 'ENVIRONMENT_TIMEOUT':
      return translate('auto.k2525');
    case 'ACCOUNT':
      return translate('auto.k2526');
    case 'TEST_DATA':
      return translate('auto.k2527');
    case 'RUNNER':
      return translate('auto.k2528');
    case 'ASSERTION':
      return translate('auto.k2529');
    case 'UNKNOWN':
      return translate('auto.k2530');
    default:
      return bucket ? `failureBucket=${bucket}` : translate('auto.k2531');
  }
}

export function extractUiE2eArtifactCaptureBlockedReason(redactionFlags: Record<string, unknown>) {
  const value = redactionFlags.captureBlockedReason;
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

export function explainUiE2eArtifactCaptureBlockedReason(reason?: string) {
  switch ((reason || '').trim()) {
    case 'runnerDisabled':
      return translate('auto.k2532');
    case 'artifactRefIncomplete':
      return translate('auto.k2533');
    default:
      return reason ? `captureBlockedReason=${reason}` : translate('auto.k2534');
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
      summary: translate('auto.k2535')
    };
  }

  if (captureStatus === 'SKIPPED') {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'info',
      summary: translate('auto.k2536')
    };
  }

  if (captureStatus === 'PENDING') {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'info',
      summary: translate('auto.k2537')
    };
  }

  if (downloadReady) {
    return {
      canDownload: true,
      downloadReady: true,
      tone: 'success',
      summary: captureStatus === 'REDACTED'
        ? translate('auto.k2538')
        : translate('auto.k2539')
    };
  }

  if (isUiE2eArtifactCapturedStatus(captureStatus)) {
    return {
      canDownload: false,
      downloadReady: false,
      tone: 'warning',
      summary: translate('auto.k2540')
    };
  }

  return {
    canDownload: false,
    downloadReady: false,
    tone: 'info',
    summary: translate('auto.k2541', { value0: captureStatus || 'UNKNOWN' })
  };
}

export function buildUiE2eScenePayload(draft: UiE2eSceneDraft): { payload?: CreateUiE2eScenePayload; issues: string[] } {
  const { payload: partialPayload, issues } = buildUiE2eScenePayloadBase(draft);
  if (!draft.projectId.trim()) issues.push(translate('auto.k2542'));
  if (!draft.code.trim()) issues.push(translate('auto.k2543'));

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
  if (!draft.name.trim()) issues.push(translate('auto.k2544'));
  if (!draft.steps.length) issues.push(translate('auto.k2545'));

  const sourceSummary = parseObjectText(draft.sourceSummaryText, 'sourceSummary', issues);
  const steps = draft.steps
    .map((step, index) => buildUiE2eSceneStepPayload(step, index, issues))
    .filter((value): value is UiE2eSceneStepPayload => Boolean(value));

  if (steps.length === 0) {
    issues.push(translate('auto.k2546'));
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
  if (!draft.projectId.trim()) issues.push(translate('auto.k2547'));
  if (!uuidPattern.test(draft.sceneId.trim())) issues.push(translate('auto.k2548'));
  if (!uuidPattern.test(draft.bundleId.trim())) issues.push(translate('auto.k2549'));
  if (!draft.baseUrlRef.trim()) issues.push(translate('auto.k2550'));
  if (!uuidPattern.test(draft.accountLeaseRef.trim())) issues.push(translate('auto.k2405'));
  if (!browsers.length) issues.push(translate('auto.k2406'));
  if (browsers.some((item) => !['CHROMIUM', 'FIREFOX', 'WEBKIT'].includes(item))) {
    issues.push(translate('auto.k2407'));
  }
  if (draft.visualRegressionEnabled && draft.baselineRunId.trim() && !uuidPattern.test(draft.baselineRunId.trim())) {
    issues.push(translate('auto.k2408'));
  }
  if (draft.visualMismatchThreshold.trim()) {
    const threshold = Number(draft.visualMismatchThreshold.trim());
    if (Number.isNaN(threshold) || threshold < 0 || threshold > 1) {
      issues.push(translate('auto.k2409'));
    }
  }
  if (draft.requestKey.trim() && !requestKeyPattern.test(draft.requestKey.trim())) {
    issues.push(translate('auto.k1192'));
  }
  if (draft.reason.length > 512) issues.push(translate('auto.k2411'));

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
  if (!draft.projectId.trim()) issues.push(translate('auto.k2551'));
  if (!draft.sceneId.trim() && !draft.runId.trim()) issues.push(translate('auto.k2552'));
  if (draft.sceneId.trim() && !uuidPattern.test(draft.sceneId.trim())) issues.push(translate('auto.k2548'));
  if (draft.runId.trim() && !uuidPattern.test(draft.runId.trim())) issues.push(translate('auto.k2553'));
  if (!draft.status.trim()) issues.push(translate('auto.k2554'));
  if (draft.status.trim() !== 'NONE' && !draft.reasonSummary.trim()) issues.push(translate('auto.k2555'));
  if (draft.reasonSummary.length > 512) issues.push(translate('auto.k2556'));

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
    issues.push(translate('auto.k2557', { value0: index + 1 }));
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
    issues.push(translate('auto.k2558', { value0: label }));
    return {};
  } catch {
    issues.push(translate('auto.k2559', { value0: label }));
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
        summary: translate('auto.k2560'),
        actions: [
          translate('auto.k2561'),
          translate('auto.k2562')
        ]
      };
    case 'UI_E2E_RUNNER_CANCELED':
      return {
        tone: 'warning' as const,
        label: 'RUNNER_CANCELED',
        summary: translate('auto.k2563'),
        actions: [
          translate('auto.k2564')
        ]
      };
    case 'UI_E2E_ACCOUNT_LEASE_INVALID':
      return {
        tone: 'error' as const,
        label: 'ACCOUNT_LEASE_INVALID',
        summary: translate('auto.k2565'),
        actions: [
          translate('auto.k2566')
        ]
      };
    case 'UI_E2E_BASE_URL_NOT_ALLOWED':
      return {
        tone: 'error' as const,
        label: 'BASE_URL_NOT_ALLOWED',
        summary: translate('auto.k2567'),
        actions: [
          translate('auto.k2568')
        ]
      };
    case 'UI_E2E_RESOURCE_SCOPE_DENIED':
      return {
        tone: 'error' as const,
        label: 'RESOURCE_SCOPE_DENIED',
        summary: translate('auto.k2569'),
        actions: [
          translate('auto.k2570')
        ]
      };
    case 'UI_E2E_SCENE_NOT_READY':
      return {
        tone: 'warning' as const,
        label: 'SCENE_NOT_READY',
        summary: translate('auto.k2571'),
        actions: [
          translate('auto.k2572')
        ]
      };
    case 'UI_E2E_BUNDLE_NOT_READY':
      return {
        tone: 'warning' as const,
        label: 'BUNDLE_NOT_READY',
        summary: translate('auto.k2573'),
        actions: [
          translate('auto.k2574')
        ]
      };
    case 'UI_E2E_EXPORT_DISABLED':
      return {
        tone: 'warning' as const,
        label: 'EXPORT_DISABLED',
        summary: translate('auto.k2575'),
        actions: [
          translate('auto.k2576')
        ]
      };
    case 'UI_E2E_VISUAL_REGRESSION_FAILED':
      return {
        tone: 'error' as const,
        label: 'VISUAL_REGRESSION_FAILED',
        summary: translate('auto.k2577'),
        actions: [
          translate('auto.k2578'),
          translate('auto.k2579')
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
        pushUnique(actions, translate('auto.k2580'));
        break;
      case 'AUTHORIZATION':
        pushUnique(actions, translate('auto.k2581'));
        break;
      case 'ENVIRONMENT_TIMEOUT':
        pushUnique(actions, translate('auto.k2582'));
        break;
      case 'ACCOUNT':
        pushUnique(actions, translate('auto.k2583'));
        break;
      case 'TEST_DATA':
        pushUnique(actions, translate('auto.k2584'));
        break;
      case 'RUNNER':
        pushUnique(actions, translate('auto.k2585'));
        break;
      case 'ASSERTION':
        pushUnique(actions, translate('auto.k2586'));
        break;
      case 'UNKNOWN':
        pushUnique(actions, translate('auto.k2587'));
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
      return translate('auto.k2588');
    case 'artifactRefIncomplete':
      return translate('auto.k2589');
    default:
      return reason ? translate('auto.k2590', { value0: reason }) : undefined;
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
    return translate('auto.k2591', { value0: failureBucket });
  }
  if (detail.failureCode) {
    return translate('auto.k2592', { value0: detail.failureCode });
  }
  return translate('auto.k2593', { value0: detail.status });
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
  return parts.length ? parts.join(' · ') : translate('auto.k2594');
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
  return parts.length ? parts.join(' · ') : translate('auto.k2595');
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
  return parts.length ? parts.join(' · ') : translate('auto.k2596');
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
  return parts.length ? parts.join(' · ') : translate('auto.k2597');
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
