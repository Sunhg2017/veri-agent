import { TEST_DESIGN_COVERAGE_TYPES } from './api/testDesign';
import { translate } from './platform/i18n';

export type TestDesignCandidateDraftQualityField =
  | 'title'
  | 'description'
  | 'coverageType'
  | 'priority'
  | 'preconditions'
  | 'steps'
  | 'expectedResult'
  | 'tags';

export type TestDesignCandidateDraftQualityIssue = {
  field: TestDesignCandidateDraftQualityField;
  message: string;
  severity: 'error';
};

export type TestDesignCandidateDraftQualityInput = {
  title: string;
  description?: string;
  coverageType: string;
  priority: string;
  preconditions?: string;
  steps: string;
  expectedResult?: string;
  tags?: string;
};

export type TestDesignCandidateDraftQualityPeer = {
  id?: string;
  requirementId?: string;
  coverageType?: string;
  title?: string;
};

export type TestDesignCandidateDraftQualityOptions = {
  currentCandidateId?: string;
  currentRequirementId?: string;
  peerCandidates?: TestDesignCandidateDraftQualityPeer[];
};

const SUPPORTED_PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
const MIN_STEP_COUNT = 2;
const MAX_STEP_COUNT = 12;
const MAX_TITLE_LENGTH = 160;
const MAX_TEXT_LENGTH = 2000;
const SENSITIVE_TEXT_PATTERN = /\b(api[_-]?key|secret|token|password|passwd|authorization)\s*[:=]\s*[^\s,;，；]+|\bbearer\s+[a-z0-9._-]+/i;
const FIELD_LABELS: Record<TestDesignCandidateDraftQualityField, string> = {
  title: translate('auto.k0440'),
  description: translate('auto.k0443'),
  coverageType: translate('auto.k1315'),
  priority: translate('auto.k0419'),
  preconditions: translate('auto.k1345'),
  steps: translate('auto.k1346'),
  expectedResult: translate('auto.k0447'),
  tags: translate('auto.k0803')
};

export function validateTestDesignCandidateDraft(
  draft: TestDesignCandidateDraftQualityInput,
  options: TestDesignCandidateDraftQualityOptions = {}
): TestDesignCandidateDraftQualityIssue[] {
  const issues: TestDesignCandidateDraftQualityIssue[] = [];

  requireText(draft.title, 'title', translate('auto.k0406'), MAX_TITLE_LENGTH, issues);
  if (draft.coverageType && !TEST_DESIGN_COVERAGE_TYPES.includes(draft.coverageType as (typeof TEST_DESIGN_COVERAGE_TYPES)[number])) {
    issues.push({ field: 'coverageType', severity: 'error', message: translate('auto.k2037', { value0: draft.coverageType }) });
  }
  if (draft.priority && !SUPPORTED_PRIORITIES.includes(draft.priority as (typeof SUPPORTED_PRIORITIES)[number])) {
    issues.push({ field: 'priority', severity: 'error', message: translate('auto.k2038', { value0: draft.priority }) });
  }

  validateOptionalText(draft.description, 'description', translate('auto.k0443'), MAX_TEXT_LENGTH, issues);
  validateOptionalText(draft.preconditions, 'preconditions', translate('auto.k1345'), MAX_TEXT_LENGTH, issues);
  validateOptionalText(draft.tags, 'tags', translate('auto.k0803'), MAX_TEXT_LENGTH, issues);
  requireText(draft.expectedResult, 'expectedResult', translate('auto.k2136'), MAX_TEXT_LENGTH, issues);
  validateSteps(draft.steps, issues);
  validateDuplicateTitle(draft, options, issues);

  return issues;
}

function validateSteps(value: string, issues: TestDesignCandidateDraftQualityIssue[]) {
  const steps = parseDraftSteps(value);
  if (steps.length < MIN_STEP_COUNT) {
    issues.push({ field: 'steps', severity: 'error', message: translate('auto.k2137', { value0: MIN_STEP_COUNT }) });
  }
  if (steps.length > MAX_STEP_COUNT) {
    issues.push({ field: 'steps', severity: 'error', message: translate('auto.k2138', { value0: MAX_STEP_COUNT }) });
  }
  steps.forEach((step, index) => {
    const label = translate('auto.k2139', { value0: index + 1 });
    requireText(step.action, 'steps', translate('auto.k2140', { value0: label }), MAX_TEXT_LENGTH, issues);
    requireText(step.expectedResult, 'steps', translate('auto.k2141', { value0: label }), MAX_TEXT_LENGTH, issues);
  });
}

function parseDraftSteps(value: string) {
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [action, expectedResult] = line.split(/\s*=>\s*/, 2);
      return {
        action: action?.trim() ?? '',
        expectedResult: expectedResult?.trim() ?? ''
      };
    });
}

function validateDuplicateTitle(
  draft: TestDesignCandidateDraftQualityInput,
  options: TestDesignCandidateDraftQualityOptions,
  issues: TestDesignCandidateDraftQualityIssue[]
) {
  const titleKey = normalizeIdentity(draft.title);
  const requirementId = options.currentRequirementId;
  if (!titleKey || !requirementId || !draft.coverageType) {
    return;
  }
  const duplicate = options.peerCandidates?.some((candidate) => (
    candidate.id !== options.currentCandidateId
    && candidate.requirementId === requirementId
    && candidate.coverageType === draft.coverageType
    && normalizeIdentity(candidate.title) === titleKey
  ));
  if (duplicate) {
    issues.push({ field: 'title', severity: 'error', message: translate('auto.k2142') });
  }
}

function requireText(
  value: string | undefined,
  field: TestDesignCandidateDraftQualityField,
  emptyMessage: string,
  maxLength: number,
  issues: TestDesignCandidateDraftQualityIssue[]
) {
  if (!value?.trim()) {
    issues.push({ field, severity: 'error', message: emptyMessage });
    return;
  }
  validateOptionalText(value, field, FIELD_LABELS[field], maxLength, issues);
}

function validateOptionalText(
  value: string | undefined,
  field: TestDesignCandidateDraftQualityField,
  label: string,
  maxLength: number,
  issues: TestDesignCandidateDraftQualityIssue[]
) {
  if (!value?.trim()) {
    return;
  }
  if (value.length > maxLength) {
    issues.push({ field, severity: 'error', message: translate('auto.k2143', { value0: label, value1: maxLength }) });
  }
  if (SENSITIVE_TEXT_PATTERN.test(value)) {
    issues.push({ field, severity: 'error', message: translate('auto.k2144', { value0: label }) });
  }
}

function normalizeIdentity(value: string | undefined) {
  return (value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '');
}
