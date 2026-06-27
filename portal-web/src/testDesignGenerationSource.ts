import type { TestDesignCandidateView, TestDesignTaskView } from './api/testDesign';
import { translate } from './platform/i18n';

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

const FALLBACK_TEMPLATE_PATTERN = /(\u5df2\u964d\u7ea7\u89c4\u5219\u6a21\u677f|\u964d\u7ea7\u89c4\u5219\u6a21\u677f|rule[-_\s]?template\s+fallback|fallback\s+to\s+rule[-_\s]?template)/i;
const MODEL_MODES = new Set(['MODEL', 'MODEL_WITH_FALLBACK']);

export function taskGenerationSource(task: TestDesignTaskView | null | undefined): TestDesignGenerationSource {
  if (!task) {
    return unknownSource();
  }
  if (isRuleTemplateFallback(task.errorMessage)) {
    return {
      kind: 'MODEL_FALLBACK_TEMPLATE',
      label: translate('auto.k2098'),
      detail: translate('auto.k2099'),
      tone: 'warning'
    };
  }

  const provider = firstText(task.modelProviderName, task.modelObservation?.providerName);
  const model = firstText(task.modelName, task.modelObservation?.modelName);
  const invocationId = firstText(task.modelInvocationId, task.modelObservation?.invocationId);
  if (invocationId || (provider && !isGenerationMode(provider)) || (model && !isGenerationMode(model))) {
    return {
      kind: 'MODEL_OUTPUT',
      label: translate('auto.k2100'),
      detail: formatModelDetail(provider, model),
      tone: 'success'
    };
  }

  const mode = generationMode(task.modelName);
  if (mode === 'RULE_TEMPLATE') {
    return {
      kind: 'RULE_TEMPLATE',
      label: translate('auto.k2101'),
      detail: translate('auto.k2102'),
      tone: 'neutral'
    };
  }
  if (mode && MODEL_MODES.has(mode)) {
    return {
      kind: 'MODEL_PENDING',
      label: translate('auto.k2103'),
      detail: mode === 'MODEL_WITH_FALLBACK' ? translate('auto.k2104') : translate('auto.k2105'),
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
      label: translate('auto.k2098'),
      detail: translate('auto.k2099'),
      tone: 'warning'
    };
  }

  const provider = firstText(candidate.modelProviderName);
  const model = firstText(candidate.modelName);
  const invocationId = firstText(candidate.modelInvocationId);
  if (invocationId || (provider && !isGenerationMode(provider)) || (model && !isGenerationMode(model))) {
    return {
      kind: 'MODEL_OUTPUT',
      label: translate('auto.k2100'),
      detail: formatModelDetail(provider, model),
      tone: 'success'
    };
  }

  const mode = generationMode(candidate.modelName) ?? generationMode(task?.modelName);
  if (mode === 'RULE_TEMPLATE') {
    return {
      kind: 'RULE_TEMPLATE',
      label: translate('auto.k2101'),
      detail: translate('auto.k2102'),
      tone: 'neutral'
    };
  }
  if (mode === 'MODEL_WITH_FALLBACK') {
    return {
      kind: 'MODEL_FALLBACK_TEMPLATE',
      label: translate('auto.k2098'),
      detail: translate('auto.k2106'),
      tone: 'warning'
    };
  }
  if (mode === 'MODEL') {
    return {
      kind: 'MODEL_PENDING',
      label: translate('auto.k2103'),
      detail: translate('auto.k2107'),
      tone: 'warning'
    };
  }

  return {
    kind: 'RULE_TEMPLATE',
    label: translate('auto.k2101'),
    detail: translate('auto.k2108'),
    tone: 'neutral'
  };
}

export function generationSourceText(source: TestDesignGenerationSource): string {
  return source.detail ? `${source.label} · ${source.detail}` : source.label;
}

function unknownSource(): TestDesignGenerationSource {
  return {
    kind: 'UNKNOWN',
    label: translate('auto.k2109'),
    detail: translate('auto.k2110'),
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
  return provider ?? model ?? translate('auto.k2111');
}
