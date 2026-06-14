import type { AssetRequirementView } from './api/assets';
import type {
  SaveTestDesignEvaluationSamplePayload,
  TestDesignCandidateBatchActionResult,
  TestDesignCandidateBatchActionType,
  TestDesignCandidateView,
  TestDesignEvaluationSampleView,
  TestDesignPublishRecordView,
  TestDesignStepView,
  TestDesignTemplateView,
  TestDesignTaskView
} from './api/testDesign';
import type { TestDesignConfirmationSummary } from './testDesignConfirmation';
import type {
  AuditOutboxRequeueDraft,
  CrossWpOperationsFilters,
  PublishCompensationRunDraft,
  QueueAlertSubscriptionDraft,
  QueuedEventReplayDraft
} from './components/TestDesignCrossWpOperationsPanel';
import type {
  CalibrationRunDraft,
  EvaluationSampleDraft,
  EvaluationSampleFilters
} from './components/TestDesignEvaluationCorpusPanel';

export type RequirementFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

export type TaskFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

export type CandidateFilters = {
  status: string;
  coverageType: string;
  keyword: string;
};

export type ReleaseReadinessApprovalDraft = {
  exceptionReasonCode: string;
  approvalReasonCode: string;
  exceptionSummary: string;
  riskMitigation: string;
  workOrderKey: string;
  workOrderTitle: string;
  workOrderUrl: string;
  workOrderStatus: string;
  requestNote: string;
  reviewNote: string;
  noteType: 'COMMENT' | 'WORK_ORDER';
  noteText: string;
};

export type ReportArchiveApprovalDraft = {
  approvalType: 'ARCHIVE' | 'EXTERNAL_SHARE';
  reasonCode: string;
  approvalReasonCode: string;
  requestSummary: string;
  workOrderKey: string;
  workOrderTitle: string;
  workOrderUrl: string;
  workOrderStatus: string;
  requestNote: string;
  reviewNote: string;
  noteType: 'COMMENT' | 'WORK_ORDER';
  noteText: string;
};

export type GenerationDraft = {
  projectId: string;
  templateId: string;
  title: string;
  environmentKey: string;
  promptKey: string;
  promptVersion: string;
  generationStrategy: string;
  coverageStrategy: string;
  caseCountPerRequirement: string;
  coverageTypes: string[];
  contextApiIds: string;
  contextPageIds: string;
  contextFlowIds: string;
};

export type TestDesignStepDraft = {
  id: string;
  action: string;
  expectedResult: string;
  selected: boolean;
};

export type CandidateDraft = {
  title: string;
  description: string;
  apiId: string;
  coverageType: string;
  priority: string;
  preconditions: string;
  steps: TestDesignStepDraft[];
  expectedResult: string;
  tags: string;
};

export type TemplateDraft = {
  projectId: string;
  name: string;
  description: string;
  promptKey: string;
  promptVersion: string;
  generationStrategy: string;
  coverageStrategy: string;
  caseCountPerRequirement: string;
  coverageTypes: string[];
  environmentKey: string;
  contextApiIds: string;
  contextPageIds: string;
  contextFlowIds: string;
  enabled: boolean;
};

export type BatchEditResult = {
  total: number;
  succeededCount: number;
  failedCount: number;
  items: Array<{
    candidateId: string;
    result: 'SUCCEEDED' | 'FAILED';
    candidate?: TestDesignCandidateView;
    errorMessage?: string;
  }>;
};

export type ConflictResolutionDraft = {
  reason: string;
  comment: string;
};

export type ConflictOperationFilters = {
  projectId: string;
  taskId: string;
  resolutionStatus: 'OPEN' | 'RESOLVED' | 'ALL';
  candidateStatus: string;
  action: string;
  result: string;
  keyword: string;
};

export type ConflictResolutionCandidate = Pick<TestDesignCandidateView, 'id' | 'title' | 'status' | 'version'>;
export type ConflictResolutionItem = {
  candidate: ConflictResolutionCandidate;
  record: TestDesignPublishRecordView;
};

export type PendingConfirmation =
  | { kind: 'batchReview'; action: TestDesignCandidateBatchActionType; summary: TestDesignConfirmationSummary }
  | { kind: 'batchEdit'; summary: TestDesignConfirmationSummary }
  | { kind: 'batchResolveConflict'; items: ConflictResolutionItem[]; summary: TestDesignConfirmationSummary }
  | { kind: 'resolveConflict'; candidate: ConflictResolutionCandidate; record: TestDesignPublishRecordView; summary: TestDesignConfirmationSummary }
  | { kind: 'publish'; dryRun: boolean; summary: TestDesignConfirmationSummary };

export const initialFilters: RequirementFilters = {
  projectId: '',
  status: 'APPROVED',
  keyword: ''
};

export const initialTaskFilters: TaskFilters = {
  projectId: '',
  status: '',
  keyword: ''
};

export const initialCandidateFilters: CandidateFilters = {
  status: '',
  coverageType: '',
  keyword: ''
};

export const releaseReadinessReasonCodes = [
  'BUSINESS_CRITICAL_RELEASE',
  'FALSE_POSITIVE_QUALITY_GATE',
  'LOW_RISK_ACCEPTANCE',
  'TIME_BOXED_EXCEPTION',
  'SMOKE_VALIDATION'
] as const;

export const releaseReadinessWorkOrderStatuses = ['OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED'] as const;
export const reportArchiveReasonCodes = [
  'RETENTION_POLICY',
  'COMPLIANCE_AUDIT',
  'CUSTOMER_REQUEST',
  'REGULATED_EXPORT',
  'SMOKE_VALIDATION'
] as const;
export const reportArchiveApprovalTypes = ['ARCHIVE', 'EXTERNAL_SHARE'] as const;
export const reportArchiveWorkOrderStatuses = ['OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED'] as const;
export const initialReleaseReadinessDraft: ReleaseReadinessApprovalDraft = {
  exceptionReasonCode: 'SMOKE_VALIDATION',
  approvalReasonCode: 'SMOKE_VALIDATION',
  exceptionSummary: '',
  riskMitigation: '',
  workOrderKey: '',
  workOrderTitle: '',
  workOrderUrl: '',
  workOrderStatus: '',
  requestNote: '',
  reviewNote: '',
  noteType: 'COMMENT',
  noteText: ''
};

export const initialReportArchiveDraft: ReportArchiveApprovalDraft = {
  approvalType: 'ARCHIVE',
  reasonCode: 'RETENTION_POLICY',
  approvalReasonCode: 'RETENTION_POLICY',
  requestSummary: '',
  workOrderKey: '',
  workOrderTitle: '',
  workOrderUrl: '',
  workOrderStatus: '',
  requestNote: '',
  reviewNote: '',
  noteType: 'COMMENT',
  noteText: ''
};

export const initialGenerationDraft: GenerationDraft = {
  projectId: '',
  templateId: '',
  title: '',
  environmentKey: '',
  promptKey: '',
  promptVersion: '',
  generationStrategy: 'BALANCED',
  coverageStrategy: 'DEFAULT_ORDER',
  caseCountPerRequirement: '2',
  coverageTypes: ['SMOKE', 'FUNCTIONAL', 'EXCEPTION'],
  contextApiIds: '',
  contextPageIds: '',
  contextFlowIds: ''
};

export const initialTemplateDraft: TemplateDraft = {
  projectId: '',
  name: '',
  description: '',
  promptKey: '',
  promptVersion: '',
  generationStrategy: 'BALANCED',
  coverageStrategy: 'DEFAULT_ORDER',
  caseCountPerRequirement: '2',
  coverageTypes: ['SMOKE', 'FUNCTIONAL', 'EXCEPTION'],
  environmentKey: '',
  contextApiIds: '',
  contextPageIds: '',
  contextFlowIds: '',
  enabled: true
};

export const initialConflictResolutionDraft: ConflictResolutionDraft = {
  reason: '人工确认复用既有用例',
  comment: ''
};

export const initialConflictOperationFilters: ConflictOperationFilters = {
  projectId: '',
  taskId: '',
  resolutionStatus: 'OPEN',
  candidateStatus: '',
  action: '',
  result: '',
  keyword: ''
};

export const initialEvaluationSampleFilters: EvaluationSampleFilters = {
  projectId: '',
  promptKey: '',
  promptVersion: '',
  status: '',
  coverageType: '',
  baselineVersion: '',
  keyword: ''
};

export const initialEvaluationSampleDraft: EvaluationSampleDraft = {
  projectId: '',
  sampleKey: '',
  title: '',
  sourceType: 'MANUAL',
  promptKey: '',
  promptVersion: '',
  coverageType: 'FUNCTIONAL',
  priority: 'MEDIUM',
  status: 'CANDIDATE',
  baselineVersion: '',
  requirementSummary: '',
  expectedCaseOutline: '',
  assertionNotes: '',
  tags: '',
  maintenanceNote: ''
};

export const initialCalibrationRunDraft: CalibrationRunDraft = {
  projectId: '',
  promptKey: '',
  promptVersion: '',
  baselineVersion: '',
  runMode: 'MANUAL',
  notes: ''
};

export const initialCrossWpOperationsFilters: CrossWpOperationsFilters = {
  projectId: '',
  promptKey: ''
};

export const initialAuditOutboxRequeueDraft: AuditOutboxRequeueDraft = {
  projectId: '',
  status: 'FAILED_OR_DEAD',
  maxItems: '20',
  reason: ''
};

export const initialQueueAlertSubscriptionDraft: QueueAlertSubscriptionDraft = {
  projectId: '',
  promptKey: '',
  alertType: 'GENERATION_QUEUE_LAG',
  channel: 'OPS_CONSOLE',
  targetRef: 'ops-console:wp5-cross-wp',
  thresholdSeconds: '',
  enabled: true
};

export const initialQueuedEventReplayDraft: QueuedEventReplayDraft = {
  projectId: '',
  promptKey: '',
  replayType: 'ALL',
  maxItems: '20',
  reason: ''
};

export const initialPublishCompensationRunDraft: PublishCompensationRunDraft = {
  projectId: '',
  promptKey: '',
  maxItems: '20',
  reason: ''
};

export const ASYNC_TASK_STATUSES = new Set(['QUEUED', 'RUNNING', 'PUBLISH_QUEUED', 'PUBLISHING']);
export const RETRYABLE_TASK_STATUSES = new Set(['FAILED', 'PARTIAL_SUCCESS', 'CANCELLED']);
export const CANCELLABLE_TASK_STATUSES = new Set(['DRAFT', 'QUEUED', 'RUNNING', 'PARTIAL_SUCCESS', 'FAILED']);
export const TEST_DESIGN_CONFLICT_OPERATION_PAGE_SIZE = 8;

export function releaseReadinessReasonCodeValue(value: string | undefined, fallback: string) {
  return releaseReadinessReasonCodes.includes(value as (typeof releaseReadinessReasonCodes)[number])
    ? value ?? fallback
    : fallback;
}

export function reportArchiveReasonCodeValue(value: string | undefined, fallback: string) {
  return reportArchiveReasonCodes.includes(value as (typeof reportArchiveReasonCodes)[number])
    ? value ?? fallback
    : fallback;
}

export function filterRequirements(requirements: AssetRequirementView[], filters: RequirementFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return requirements.filter((requirement) => {
    if (filters.projectId.trim() && requirement.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && requirement.status !== filters.status.trim()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [requirement.title, requirement.description, requirement.acceptanceCriteria, requirement.sourceRef, requirement.tags.join(',')]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

export function filterCandidates(candidates: TestDesignCandidateView[], filters: CandidateFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return candidates.filter((candidate) => {
    if (filters.status && candidate.status !== filters.status) {
      return false;
    }
    if (filters.coverageType && candidate.coverageType !== filters.coverageType) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      candidate.title,
      candidate.description,
      candidate.requirementId,
      candidate.apiId,
      candidate.errorMessage,
      candidate.tags.join(',')
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

export function isPublishIssueRecord(record: TestDesignPublishRecordView) {
  return ['CONFLICT', 'FAILED', 'DUPLICATE_REVIEW_REQUIRED'].includes(record.result) || Boolean(record.errorMessage);
}

export function conflictResolutionCandidate(
  record: TestDesignPublishRecordView,
  candidateById: Map<string, TestDesignCandidateView>
): ConflictResolutionCandidate | undefined {
  if (!record.candidateId) {
    return undefined;
  }
  const cached = candidateById.get(record.candidateId);
  if (cached) {
    return cached;
  }
  if (record.candidateVersion === undefined) {
    return undefined;
  }
  return {
    id: record.candidateId,
    title: record.title ?? record.candidateId,
    status: record.candidateStatus ?? 'CONFIRMED',
    version: record.candidateVersion
  };
}

export function isResolvableConflictRecord(record: TestDesignPublishRecordView) {
  const conflictSignal = record.action === 'DUPLICATE_REVIEW_REQUIRED'
    || record.result === 'CONFLICT'
    || record.result === 'DUPLICATE_REVIEW_REQUIRED';
  return Boolean(conflictSignal && record.candidateId);
}

export function conflictResolutionTargetCaseId(
  record: TestDesignPublishRecordView,
  selectedCaseIds: Record<string, string>
) {
  if (!record.candidateId) {
    return record.assetCaseId ?? '';
  }
  const selectedCaseId = selectedCaseIds[record.candidateId];
  return selectedCaseId === undefined ? record.assetCaseId ?? '' : selectedCaseId;
}

export function applyConflictResolutionRecord(
  records: TestDesignPublishRecordView[],
  resolution: TestDesignPublishRecordView
) {
  if (resolution.result !== 'SUCCEEDED') {
    return [resolution, ...records];
  }

  let replaced = false;
  const nextRecords = records.map((record) => {
    if (
      !replaced
      && isResolvableConflictRecord(record)
      && record.candidateId === resolution.candidateId
    ) {
      replaced = true;
      return resolution;
    }
    return record;
  });
  return replaced ? nextRecords : [resolution, ...records];
}

export function countByStatus(candidates: TestDesignCandidateView[]) {
  return candidates.reduce<Record<string, number>>((counts, candidate) => {
    counts[candidate.status] = (counts[candidate.status] ?? 0) + 1;
    return counts;
  }, {});
}

export function upsertTask(current: TestDesignTaskView[], task: TestDesignTaskView) {
  const exists = current.some((item) => item.id === task.id);
  if (!exists) {
    return [task, ...current];
  }
  return current.map((item) => (item.id === task.id ? task : item));
}

export function upsertTemplate(current: TestDesignTemplateView[], template: TestDesignTemplateView) {
  const exists = current.some((item) => item.id === template.id);
  if (!exists) {
    return [template, ...current];
  }
  return current.map((item) => (item.id === template.id ? template : item));
}

export function upsertEvaluationSample(
  current: TestDesignEvaluationSampleView[],
  sample: TestDesignEvaluationSampleView
) {
  const exists = current.some((item) => item.id === sample.id);
  if (!exists) {
    return [sample, ...current];
  }
  return current.map((item) => (item.id === sample.id ? sample : item));
}

export function mergeBatchCandidates(current: TestDesignCandidateView[], result: TestDesignCandidateBatchActionResult) {
  const candidateById = new Map(
    result.items
      .map((item) => item.candidate)
      .filter((candidate): candidate is TestDesignCandidateView => Boolean(candidate))
      .map((candidate) => [candidate.id, candidate])
  );
  return current.map((candidate) => candidateById.get(candidate.id) ?? candidate);
}

export function mergeUpdatedCandidates(current: TestDesignCandidateView[], updatedCandidates: readonly TestDesignCandidateView[]) {
  if (!updatedCandidates.length) {
    return current;
  }
  const candidateById = new Map(updatedCandidates.map((candidate) => [candidate.id, candidate]));
  return current.map((candidate) => candidateById.get(candidate.id) ?? candidate);
}

export function mergeCandidateCache(
  current: Record<string, TestDesignCandidateView>,
  nextCandidates: readonly TestDesignCandidateView[]
) {
  const next = { ...current };
  for (const candidate of nextCandidates) {
    next[candidate.id] = candidate;
  }
  return next;
}

export function draftFromCandidate(candidate: TestDesignCandidateView): CandidateDraft {
  return {
    title: candidate.title,
    description: candidate.description ?? '',
    apiId: candidate.apiId ?? '',
    coverageType: candidate.coverageType,
    priority: candidate.priority,
    preconditions: candidate.preconditions ?? '',
    steps: candidate.steps.length ? candidate.steps.map(stepDraftFromView) : [emptyStepDraft(), emptyStepDraft()],
    expectedResult: candidate.expectedResult ?? '',
    tags: candidate.tags.join(', ')
  };
}

export function evaluationSampleDraftFromView(sample: TestDesignEvaluationSampleView): EvaluationSampleDraft {
  return {
    projectId: sample.projectId ?? '',
    sampleKey: sample.sampleKey,
    title: sample.title,
    sourceType: sample.sourceType || 'MANUAL',
    promptKey: sample.promptKey ?? '',
    promptVersion: sample.promptVersion ?? '',
    coverageType: sample.coverageType || 'FUNCTIONAL',
    priority: sample.priority || 'MEDIUM',
    status: sample.status || 'CANDIDATE',
    baselineVersion: sample.baselineVersion ?? '',
    requirementSummary: sample.requirementSummary ?? '',
    expectedCaseOutline: sample.expectedCaseOutline ?? '',
    assertionNotes: sample.assertionNotes ?? '',
    tags: sample.tags ?? '',
    maintenanceNote: sample.maintenanceNote ?? ''
  };
}

export function evaluationSamplePayload(draft: EvaluationSampleDraft): SaveTestDesignEvaluationSamplePayload {
  return {
    projectId: draft.projectId.trim(),
    sampleKey: draft.sampleKey.trim(),
    title: draft.title.trim(),
    sourceType: draft.sourceType,
    promptKey: draft.promptKey.trim(),
    promptVersion: draft.promptVersion.trim(),
    coverageType: draft.coverageType,
    priority: draft.priority,
    status: draft.status,
    baselineVersion: draft.baselineVersion.trim(),
    requirementSummary: draft.requirementSummary.trim(),
    expectedCaseOutline: draft.expectedCaseOutline.trim(),
    assertionNotes: draft.assertionNotes.trim(),
    tags: draft.tags.trim(),
    maintenanceNote: draft.maintenanceNote.trim()
  };
}

export function stepDraftFromView(step: TestDesignStepView): TestDesignStepDraft {
  return {
    id: `step-${step.stepOrder}-${Math.random().toString(36).slice(2)}`,
    action: step.action ?? '',
    expectedResult: step.expectedResult ?? '',
    selected: false
  };
}

export function emptyStepDraft(action = '', expectedResult = ''): TestDesignStepDraft {
  return {
    id: `step-new-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    action,
    expectedResult,
    selected: false
  };
}

export function stepsToQualityText(steps: TestDesignStepDraft[]) {
  return steps
    .map((step) => `${step.action.trim()} => ${step.expectedResult.trim()}`.trim())
    .filter((line) => line !== '=>')
    .join('\n');
}

export function stepsFromDraft(steps: TestDesignStepDraft[]) {
  return steps
    .map((step) => ({
      action: step.action.trim(),
      expectedResult: step.expectedResult.trim()
    }))
    .filter((step) => step.action || step.expectedResult);
}

export function templateDraftFromView(template: TestDesignTemplateView): TemplateDraft {
  const defaults = template.contextDefaults;
  return {
    projectId: template.projectId ?? '',
    name: template.name,
    description: template.description ?? '',
    promptKey: template.promptKey,
    promptVersion: template.promptVersion,
    generationStrategy: template.generationStrategy,
    coverageStrategy: template.coverageStrategy,
    caseCountPerRequirement: String(template.caseCountPerRequirement || 1),
    coverageTypes: template.coverageTypes,
    environmentKey: stringDefault(defaults.environmentKey),
    contextApiIds: templateContextIds(defaults.contextApiIds),
    contextPageIds: templateContextIds(defaults.contextPageIds),
    contextFlowIds: templateContextIds(defaults.contextFlowIds),
    enabled: template.enabled
  };
}

export function templatePayload(draft: TemplateDraft, includeProjectId: boolean) {
  const contextDefaults = {
    environmentKey: draft.environmentKey,
    contextApiIds: parseContextAssetIds(draft.contextApiIds),
    contextPageIds: parseContextAssetIds(draft.contextPageIds),
    contextFlowIds: parseContextAssetIds(draft.contextFlowIds)
  };
  return {
    ...(includeProjectId ? { projectId: draft.projectId } : {}),
    name: draft.name,
    description: draft.description,
    promptKey: draft.promptKey,
    promptVersion: draft.promptVersion,
    coverageTypes: draft.coverageTypes,
    generationStrategy: draft.generationStrategy,
    coverageStrategy: draft.coverageStrategy,
    caseCountPerRequirement: Number(draft.caseCountPerRequirement) || undefined,
    contextDefaults: compactContextDefaults(contextDefaults),
    enabled: draft.enabled
  };
}

export function compactContextDefaults(defaults: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(defaults).flatMap(([key, value]) => {
      if (Array.isArray(value)) {
        return value.length ? [[key, value]] : [];
      }
      if (typeof value === 'string') {
        const normalized = value.trim();
        return normalized ? [[key, normalized]] : [];
      }
      return value === undefined || value === null ? [] : [[key, value]];
    })
  );
}

export function templateContextIds(value: unknown) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean).join(', ');
  }
  if (typeof value === 'string') {
    return value.trim();
  }
  return '';
}

export function stringDefault(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

export function tagsFromText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

export function parseContextAssetIds(value: string) {
  return value
    .split(/[\n,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function downloadText(text: string, filename: string, contentType: string) {
  const blob = new Blob([text], { type: contentType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
