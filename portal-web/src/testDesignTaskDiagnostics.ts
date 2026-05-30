import type { TestDesignTaskView } from './api/testDesign';
import { sanitizeTestDesignExportText } from './testDesignExport';
import { generationSourceText, taskGenerationSource } from './testDesignGenerationSource';

export type TestDesignTaskDiagnosticTone = 'neutral' | 'warning' | 'danger';

export type TestDesignTaskDiagnosticItem = {
  label: string;
  value: string;
  tone?: TestDesignTaskDiagnosticTone;
};

const SENSITIVE_KEY_PATTERN =
  /(api[-_]?key|access[-_]?key|secret|token|password|passwd|pwd|cookie|private[-_]?key|authorization|credential)/i;

export function buildTestDesignTaskDiagnostics(task: TestDesignTaskView | null | undefined): TestDesignTaskDiagnosticItem[] {
  if (!task) {
    return [];
  }
  const generationSource = taskGenerationSource(task);

  return [
    { label: '任务 ID', value: compactTestDesignDigest(task.id, 10, 6) },
    { label: '项目', value: compactTestDesignDigest(task.projectId, 10, 6) },
    { label: '状态', value: displayDiagnosticText(task.status) },
    { label: '需求数', value: String(task.totalRequirements ?? task.requirementIds.length) },
    { label: '覆盖', value: formatList(task.coverageTypes) },
    { label: '产出', value: `${task.generatedCount} 生成 / ${task.confirmedCount} 确认 / ${task.publishedCount} 发布` },
    {
      label: '生成来源',
      value: generationSourceText(generationSource),
      tone: generationSource.tone === 'warning' ? 'warning' : 'neutral'
    },
    { label: 'Prompt', value: formatPrompt(task) },
    { label: '模型', value: formatModel(task) },
    { label: '模型调用', value: compactTestDesignDigest(task.modelInvocationId, 12, 8) },
    {
      label: '调用观测',
      value: formatModelObservation(task),
      tone: modelObservationTone(task)
    },
    { label: '调用链路', value: compactTestDesignDigest(task.modelObservation?.traceId, 12, 8) },
    { label: '调用任务', value: compactTestDesignDigest(task.modelObservation?.jobId, 12, 8) },
    { label: '输入摘要', value: compactTestDesignDigest(task.inputDigest, 12, 8) },
    { label: '幂等键', value: compactTestDesignDigest(task.idempotencyKey, 14, 8) },
    { label: '上下文', value: summarizeTestDesignTaskContext(task.contextSummary) },
    { label: '请求人', value: displayDiagnosticText(task.requestedBy) },
    { label: '创建', value: formatDateTime(task.createdAt) },
    { label: '更新', value: formatDateTime(task.updatedAt) },
    {
      label: '错误',
      value: displayDiagnosticText(task.errorMessage, 96),
      tone: task.errorMessage ? 'danger' : 'neutral'
    }
  ];
}

export function compactTestDesignDigest(value?: string, prefix = 12, suffix = 8): string {
  const text = sanitizeDiagnosticText(value);
  if (!text) {
    return '-';
  }
  if (text.length <= prefix + suffix + 3) {
    return text;
  }
  return `${text.slice(0, prefix)}...${text.slice(-suffix)}`;
}

export function summarizeTestDesignTaskContext(contextSummary: Record<string, unknown> | null | undefined): string {
  if (!contextSummary || typeof contextSummary !== 'object') {
    return '-';
  }

  const safeKeys = Object.keys(contextSummary).filter((key) => !SENSITIVE_KEY_PATTERN.test(key));
  if (!safeKeys.length) {
    return '-';
  }

  const parts: string[] = [];
  const version = firstSafeScalar(contextSummary, ['contextVersion', 'version', 'schemaVersion']);
  if (version) {
    parts.push(`version:${displayDiagnosticText(version, 24)}`);
  }

  appendContextCount(parts, contextSummary, 'requirements', ['requirements', 'requirementSummaries']);
  appendContextCount(parts, contextSummary, 'sources', ['documentSources', 'sources', 'sourceRefs']);
  appendContextCount(parts, contextSummary, 'history', ['historicalCases', 'existingCases', 'cases']);
  appendContextCount(parts, contextSummary, 'apis', ['apis', 'apiSummaries', 'interfaces']);
  appendContextCount(parts, contextSummary, 'pages', ['pages', 'pageSummaries']);
  appendContextCount(parts, contextSummary, 'flows', ['flows', 'businessFlows']);
  appendExplicitAssetCounts(parts, contextSummary);

  const keyPreview = safeKeys.slice(0, 5).join(', ');
  parts.push(`keys:${keyPreview}${safeKeys.length > 5 ? ` +${safeKeys.length - 5}` : ''}`);

  return parts.join(' · ');
}

function appendExplicitAssetCounts(parts: string[], contextSummary: Record<string, unknown>) {
  if (!('explicitAssets' in contextSummary) || SENSITIVE_KEY_PATTERN.test('explicitAssets')) {
    return;
  }
  const explicitAssets = contextSummary.explicitAssets;
  if (!explicitAssets || typeof explicitAssets !== 'object' || Array.isArray(explicitAssets)) {
    return;
  }
  const record = explicitAssets as Record<string, unknown>;
  appendNumericCount(parts, 'explicitApis', record.apiCount);
  appendNumericCount(parts, 'explicitPages', record.pageCount);
  appendNumericCount(parts, 'explicitFlows', record.flowCount);
}

function appendNumericCount(parts: string[], label: string, value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    parts.push(`${label}:${Math.floor(value)}`);
  }
}

function appendContextCount(
  parts: string[],
  contextSummary: Record<string, unknown>,
  label: string,
  keys: string[]
) {
  const count = firstCount(contextSummary, keys);
  if (count !== null) {
    parts.push(`${label}:${count}`);
  }
}

function firstCount(contextSummary: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    if (SENSITIVE_KEY_PATTERN.test(key) || !(key in contextSummary)) {
      continue;
    }
    const count = countContextValue(contextSummary[key]);
    if (count !== null) {
      return count;
    }
  }
  return null;
}

function countContextValue(value: unknown): number | null {
  if (Array.isArray(value)) {
    return value.length;
  }
  if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
    return Math.floor(value);
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    for (const key of ['count', 'total', 'size']) {
      const count = record[key];
      if (typeof count === 'number' && Number.isFinite(count) && count >= 0) {
        return Math.floor(count);
      }
    }
  }
  return null;
}

function firstSafeScalar(contextSummary: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    if (SENSITIVE_KEY_PATTERN.test(key) || !(key in contextSummary)) {
      continue;
    }
    const value = contextSummary[key];
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
  }
  return '';
}

function formatPrompt(task: TestDesignTaskView) {
  const promptKey = displayDiagnosticText(task.promptKey, 48);
  const promptVersion = displayDiagnosticText(task.promptVersion, 24);
  if (promptKey === '-' && promptVersion === '-') {
    return '-';
  }
  if (promptVersion === '-') {
    return promptKey;
  }
  if (promptKey === '-') {
    return promptVersion;
  }
  return `${promptKey}@${promptVersion}`;
}

function formatModel(task: TestDesignTaskView) {
  const provider = displayDiagnosticText(task.modelProviderName, 32);
  const model = displayDiagnosticText(task.modelName, 48);
  if (provider === '-' && model === '-') {
    return '-';
  }
  if (provider === '-') {
    return model;
  }
  if (model === '-') {
    return provider;
  }
  return `${provider} / ${model}`;
}

function formatModelObservation(task: TestDesignTaskView) {
  const observation = task.modelObservation;
  if (!observation) {
    return task.modelInvocationId ? '仅记录调用 ID' : '-';
  }
  const parts: string[] = [];
  parts.push(displayDiagnosticText(observation.status, 24));
  parts.push(`${formatNumber(observation.inputTokens)}/${formatNumber(observation.outputTokens)} tokens`);
  parts.push(`${formatNumber(observation.latencyMs)}ms`);
  parts.push(`cost:${formatCost(observation.totalCost)}`);
  if (observation.fallbackUsed) {
    parts.push('fallback');
  }
  if (!observation.available) {
    parts.push('日志暂不可用');
  }
  const error = displayDiagnosticText(observation.errorCode || observation.errorMessage, 48);
  if (error !== '-') {
    parts.push(error);
  }
  return parts.filter((part) => part && part !== '-').join(' · ') || '-';
}

function modelObservationTone(task: TestDesignTaskView): TestDesignTaskDiagnosticTone {
  const observation = task.modelObservation;
  if (!observation) {
    return task.modelInvocationId ? 'warning' : 'neutral';
  }
  if (!observation.available) {
    return 'warning';
  }
  return observation.status === 'FAILED' || observation.status === 'BLOCKED' ? 'danger' : 'neutral';
}

function formatList(values: string[]) {
  const safeValues = values.map((value) => displayDiagnosticText(value, 24)).filter((value) => value !== '-');
  return safeValues.length ? safeValues.join(', ') : '-';
}

function formatNumber(value?: number) {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : '-';
}

function formatCost(value?: number) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '-';
  }
  return value === 0 ? '0' : value.toFixed(8).replace(/0+$/, '').replace(/\.$/, '');
}

function formatDateTime(value?: string) {
  const text = sanitizeDiagnosticText(value);
  if (!text) {
    return '-';
  }
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) {
    return displayDiagnosticText(text, 40);
  }
  return date.toISOString().replace('.000Z', 'Z');
}

function displayDiagnosticText(value: unknown, maxLength = 80) {
  const text = sanitizeDiagnosticText(value);
  if (!text) {
    return '-';
  }
  if (text.length <= maxLength) {
    return text;
  }
  const headLength = Math.max(1, maxLength - 10);
  return `${text.slice(0, headLength)}...${text.slice(-6)}`;
}

function sanitizeDiagnosticText(value: unknown) {
  if (value === null || value === undefined) {
    return '';
  }
  return sanitizeTestDesignExportText(String(value));
}
