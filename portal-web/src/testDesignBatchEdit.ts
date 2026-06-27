import {
  TEST_DESIGN_COVERAGE_TYPES,
  type TestDesignCandidateView,
  type UpdateTestDesignCandidatePayload
} from './api/testDesign';
import { canReviewTestDesignCandidate } from './testDesignSelection';
import { translate } from './platform/i18n';

export type TestDesignBatchEditTagMode = 'append' | 'replace';

export type TestDesignBatchEditDraft = {
  coverageType: string;
  priority: string;
  tags: string;
  tagMode: TestDesignBatchEditTagMode;
};

export type TestDesignBatchEditIssue = {
  field: 'coverageType' | 'priority' | 'tags';
  message: string;
};

const SUPPORTED_PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
const MAX_TAG_TEXT_LENGTH = 2000;
const SENSITIVE_TAG_PATTERN = /\b(api[_-]?key|secret|token|password|passwd|authorization)\s*[:=]\s*[^\s,;，；]+|\bbearer\s+[a-z0-9._-]+/i;

export const initialTestDesignBatchEditDraft: TestDesignBatchEditDraft = {
  coverageType: '',
  priority: '',
  tags: '',
  tagMode: 'append'
};

export function selectedTestDesignBatchEditableCandidates<T extends Pick<TestDesignCandidateView, 'id' | 'status'>>(
  candidates: readonly T[],
  selectedCandidateIds: readonly string[]
) {
  return candidates.filter((candidate) => selectedCandidateIds.includes(candidate.id) && canReviewTestDesignCandidate(candidate));
}

export function hasTestDesignBatchEditChanges(draft: TestDesignBatchEditDraft) {
  return Boolean(draft.coverageType || draft.priority || draft.tags.trim());
}

export function validateTestDesignBatchEditDraft(draft: TestDesignBatchEditDraft): TestDesignBatchEditIssue[] {
  const issues: TestDesignBatchEditIssue[] = [];
  if (draft.coverageType && !TEST_DESIGN_COVERAGE_TYPES.includes(draft.coverageType as (typeof TEST_DESIGN_COVERAGE_TYPES)[number])) {
    issues.push({ field: 'coverageType', message: translate('auto.k2037', { value0: draft.coverageType }) });
  }
  if (draft.priority && !SUPPORTED_PRIORITIES.includes(draft.priority as (typeof SUPPORTED_PRIORITIES)[number])) {
    issues.push({ field: 'priority', message: translate('auto.k2038', { value0: draft.priority }) });
  }
  if (draft.tags.length > MAX_TAG_TEXT_LENGTH) {
    issues.push({ field: 'tags', message: translate('auto.k2039', { value0: MAX_TAG_TEXT_LENGTH }) });
  }
  if (SENSITIVE_TAG_PATTERN.test(draft.tags)) {
    issues.push({ field: 'tags', message: translate('auto.k2040') });
  }
  return issues;
}

export function buildTestDesignBatchEditPayload(
  candidate: TestDesignCandidateView,
  draft: TestDesignBatchEditDraft
): UpdateTestDesignCandidatePayload {
  return {
    title: candidate.title,
    description: candidate.description,
    apiId: candidate.apiId,
    coverageType: draft.coverageType || candidate.coverageType,
    priority: draft.priority || candidate.priority,
    preconditions: candidate.preconditions,
    steps: candidate.steps.map((step) => ({
      action: step.action,
      expectedResult: step.expectedResult
    })),
    expectedResult: candidate.expectedResult,
    tags: nextBatchEditTags(candidate.tags, draft),
    version: candidate.version
  };
}

export function testDesignBatchEditFieldLabels(draft: TestDesignBatchEditDraft) {
  const labels: string[] = [];
  if (draft.coverageType) {
    labels.push(translate('auto.k2041', { value0: draft.coverageType }));
  }
  if (draft.priority) {
    labels.push(translate('auto.k2042', { value0: draft.priority }));
  }
  if (draft.tags.trim()) {
    labels.push(translate('auto.k2043', { value0: draft.tagMode === 'replace' ? translate('auto.k2598') : translate('auto.k2599'), value1: parseTags(draft.tags).join(', ') }));
  }
  return labels;
}

function nextBatchEditTags(currentTags: readonly string[], draft: TestDesignBatchEditDraft) {
  const patchTags = parseTags(draft.tags);
  if (!patchTags.length) {
    return [...currentTags];
  }
  if (draft.tagMode === 'replace') {
    return patchTags;
  }
  return uniqueTags([...currentTags, ...patchTags]);
}

function parseTags(value: string) {
  return uniqueTags(value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean));
}

function uniqueTags(tags: readonly string[]) {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const tag of tags) {
    const key = tag.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      result.push(tag);
    }
  }
  return result;
}
