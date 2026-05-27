import {
  TEST_DESIGN_COVERAGE_TYPES,
  type TestDesignCandidateView,
  type UpdateTestDesignCandidatePayload
} from './api/testDesign';
import { canReviewTestDesignCandidate } from './testDesignSelection';

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
    issues.push({ field: 'coverageType', message: `覆盖类型不支持：${draft.coverageType}` });
  }
  if (draft.priority && !SUPPORTED_PRIORITIES.includes(draft.priority as (typeof SUPPORTED_PRIORITIES)[number])) {
    issues.push({ field: 'priority', message: `优先级不支持：${draft.priority}` });
  }
  if (draft.tags.length > MAX_TAG_TEXT_LENGTH) {
    issues.push({ field: 'tags', message: `标签长度不能超过 ${MAX_TAG_TEXT_LENGTH}` });
  }
  if (SENSITIVE_TAG_PATTERN.test(draft.tags)) {
    issues.push({ field: 'tags', message: '标签包含疑似敏感信息' });
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
    labels.push(`覆盖类型=${draft.coverageType}`);
  }
  if (draft.priority) {
    labels.push(`优先级=${draft.priority}`);
  }
  if (draft.tags.trim()) {
    labels.push(`${draft.tagMode === 'replace' ? '替换' : '追加'}标签=${parseTags(draft.tags).join(', ')}`);
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
