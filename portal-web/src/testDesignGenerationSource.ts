import type { TestDesignCandidateView, TestDesignTaskView } from './api/testDesign';

export type TestDesignGenerationSourceKind =
  | 'MODEL_OUTPUT'
  | 'MODEL_FALLBACK_TEMPLATE'
  | 'RULE_TEMPLATE'
  | 'MODEL_PENDING'
  | 'UNKNOWN';

export type TestDesignGenerationSourceTone = 'neutral' | 'success' | 'warning';

export type TestDesignGenerationSource = {
  kind: TestDesignGenerationSourceKind;
  label: string;
  detail: string;
  tone: TestDesignGenerationSourceTone;
};

const FALLBACK_TEMPLATE_PATTERN = /(已降级规则模板|降级规则模板|rule[-_\s]?template\s+fallback|fallback\s+to\s+rule[-_\s]?template)/i;
const MODEL_MODES = new Set(['MODEL', 'MODEL_WITH_FALLBACK']);

export function taskGenerationSource(task: TestDesignTaskView | null | undefined): TestDesignGenerationSource {
  if (!task) {
    return unknownSource();
  }
  if (isRuleTemplateFallback(task.errorMessage)) {
    return {
      kind: 'MODEL_FALLBACK_TEMPLATE',
      label: '模型降级模板',
      detail: '模型失败后由规则模板生成',
      tone: 'warning'
    };
  }

  const provider = firstText(task.modelProviderName, task.modelObservation?.providerName);
  const model = firstText(task.modelName, task.modelObservation?.modelName);
  const invocationId = firstText(task.modelInvocationId, task.modelObservation?.invocationId);
  if (invocationId || (provider && !isGenerationMode(provider)) || (model && !isGenerationMode(model))) {
    return {
      kind: 'MODEL_OUTPUT',
      label: '模型输出',
      detail: formatModelDetail(provider, model),
      tone: 'success'
    };
  }

  const mode = generationMode(task.modelName);
  if (mode === 'RULE_TEMPLATE') {
    return {
      kind: 'RULE_TEMPLATE',
      label: '规则模板',
      detail: '未调用模型',
      tone: 'neutral'
    };
  }
  if (mode && MODEL_MODES.has(mode)) {
    return {
      kind: 'MODEL_PENDING',
      label: '模型待生成',
      detail: mode === 'MODEL_WITH_FALLBACK' ? '允许失败后降级模板' : '严格模型模式',
      tone: 'warning'
    };
  }

  return unknownSource();
}

export function candidateGenerationSource(
  candidate: TestDesignCandidateView | null | undefined,
  task?: TestDesignTaskView | null
): TestDesignGenerationSource {
  if (!candidate) {
    return unknownSource();
  }

  const taskSource = taskGenerationSource(task);
  if (taskSource.kind === 'MODEL_FALLBACK_TEMPLATE') {
    return taskSource;
  }
  if (isRuleTemplateFallback(candidate.errorMessage)) {
    return {
      kind: 'MODEL_FALLBACK_TEMPLATE',
      label: '模型降级模板',
      detail: '模型失败后由规则模板生成',
      tone: 'warning'
    };
  }

  const provider = firstText(candidate.modelProviderName);
  const model = firstText(candidate.modelName);
  const invocationId = firstText(candidate.modelInvocationId);
  if (invocationId || (provider && !isGenerationMode(provider)) || (model && !isGenerationMode(model))) {
    return {
      kind: 'MODEL_OUTPUT',
      label: '模型输出',
      detail: formatModelDetail(provider, model),
      tone: 'success'
    };
  }

  const mode = generationMode(candidate.modelName) ?? generationMode(task?.modelName);
  if (mode === 'RULE_TEMPLATE') {
    return {
      kind: 'RULE_TEMPLATE',
      label: '规则模板',
      detail: '未调用模型',
      tone: 'neutral'
    };
  }
  if (mode === 'MODEL_WITH_FALLBACK') {
    return {
      kind: 'MODEL_FALLBACK_TEMPLATE',
      label: '模型降级模板',
      detail: '未记录模型输出，按模板候选处理',
      tone: 'warning'
    };
  }
  if (mode === 'MODEL') {
    return {
      kind: 'MODEL_PENDING',
      label: '模型待生成',
      detail: '尚未记录模型调用',
      tone: 'warning'
    };
  }

  return {
    kind: 'RULE_TEMPLATE',
    label: '规则模板',
    detail: '未记录模型调用',
    tone: 'neutral'
  };
}

export function generationSourceText(source: TestDesignGenerationSource): string {
  return source.detail ? `${source.label} · ${source.detail}` : source.label;
}

function unknownSource(): TestDesignGenerationSource {
  return {
    kind: 'UNKNOWN',
    label: '来源未知',
    detail: '缺少模型或模板标识',
    tone: 'warning'
  };
}

function isRuleTemplateFallback(value?: string) {
  return Boolean(value && FALLBACK_TEMPLATE_PATTERN.test(value));
}

function generationMode(value?: string): string | undefined {
  const normalized = value?.trim().toUpperCase().replace(/[-\s]+/g, '_');
  if (!normalized) {
    return undefined;
  }
  if (normalized === 'RULE_BASED') {
    return 'RULE_TEMPLATE';
  }
  if (normalized === 'MODEL_FALLBACK') {
    return 'MODEL_WITH_FALLBACK';
  }
  if (normalized === 'RULE_TEMPLATE' || normalized === 'MODEL' || normalized === 'MODEL_WITH_FALLBACK') {
    return normalized;
  }
  return undefined;
}

function isGenerationMode(value: string) {
  return Boolean(generationMode(value));
}

function firstText(...values: Array<string | undefined>) {
  return values.find((value) => value?.trim())?.trim();
}

function formatModelDetail(provider?: string, model?: string) {
  if (provider && model) {
    return `${provider} / ${model}`;
  }
  return provider ?? model ?? '已记录模型调用';
}
