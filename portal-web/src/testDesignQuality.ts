import { TEST_DESIGN_COVERAGE_TYPES } from './api/testDesign';

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
  title: '标题',
  description: '描述',
  coverageType: '覆盖类型',
  priority: '优先级',
  preconditions: '前置条件',
  steps: '步骤',
  expectedResult: '预期结果',
  tags: '标签'
};

export function validateTestDesignCandidateDraft(
  draft: TestDesignCandidateDraftQualityInput,
  options: TestDesignCandidateDraftQualityOptions = {}
): TestDesignCandidateDraftQualityIssue[] {
  const issues: TestDesignCandidateDraftQualityIssue[] = [];

  requireText(draft.title, 'title', '标题不能为空', MAX_TITLE_LENGTH, issues);
  if (draft.coverageType && !TEST_DESIGN_COVERAGE_TYPES.includes(draft.coverageType as (typeof TEST_DESIGN_COVERAGE_TYPES)[number])) {
    issues.push({ field: 'coverageType', severity: 'error', message: `覆盖类型不支持：${draft.coverageType}` });
  }
  if (draft.priority && !SUPPORTED_PRIORITIES.includes(draft.priority as (typeof SUPPORTED_PRIORITIES)[number])) {
    issues.push({ field: 'priority', severity: 'error', message: `优先级不支持：${draft.priority}` });
  }

  validateOptionalText(draft.description, 'description', '描述', MAX_TEXT_LENGTH, issues);
  validateOptionalText(draft.preconditions, 'preconditions', '前置条件', MAX_TEXT_LENGTH, issues);
  validateOptionalText(draft.tags, 'tags', '标签', MAX_TEXT_LENGTH, issues);
  requireText(draft.expectedResult, 'expectedResult', '预期结果不能为空', MAX_TEXT_LENGTH, issues);
  validateSteps(draft.steps, issues);
  validateDuplicateTitle(draft, options, issues);

  return issues;
}

function validateSteps(value: string, issues: TestDesignCandidateDraftQualityIssue[]) {
  const steps = parseDraftSteps(value);
  if (steps.length < MIN_STEP_COUNT) {
    issues.push({ field: 'steps', severity: 'error', message: `步骤至少需要 ${MIN_STEP_COUNT} 步` });
  }
  if (steps.length > MAX_STEP_COUNT) {
    issues.push({ field: 'steps', severity: 'error', message: `步骤最多支持 ${MAX_STEP_COUNT} 步` });
  }
  steps.forEach((step, index) => {
    const label = `第 ${index + 1} 步`;
    requireText(step.action, 'steps', `${label}缺少操作`, MAX_TEXT_LENGTH, issues);
    requireText(step.expectedResult, 'steps', `${label}缺少预期结果，请使用“操作 => 期望”格式`, MAX_TEXT_LENGTH, issues);
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
    issues.push({ field: 'title', severity: 'error', message: '同需求同覆盖类型下已存在相同候选标题' });
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
    issues.push({ field, severity: 'error', message: `${label}长度不能超过 ${maxLength}` });
  }
  if (SENSITIVE_TEXT_PATTERN.test(value)) {
    issues.push({ field, severity: 'error', message: `${label}包含疑似敏感信息` });
  }
}

function normalizeIdentity(value: string | undefined) {
  return (value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '');
}
