import type { CreateTestDesignTaskPayload } from './api/testDesign';

export type TestDesignTaskIdempotencyState = {
  signature: string;
  key: string;
};

type IdempotencyPayloadInput = Pick<
  CreateTestDesignTaskPayload,
  'projectId' | 'title' | 'requirementIds' | 'coverageTypes' | 'caseCountPerRequirement'
>;

export function buildTestDesignTaskIdempotencySignature(input: IdempotencyPayloadInput) {
  return JSON.stringify({
    projectId: normalizedText(input.projectId),
    title: normalizedOptionalText(input.title),
    requirementIds: input.requirementIds.map(normalizedText).filter(Boolean),
    coverageTypes: (input.coverageTypes ?? []).map((type) => normalizedText(type).toUpperCase()).filter(Boolean),
    caseCountPerRequirement: normalizedCaseCount(input.caseCountPerRequirement)
  });
}

export function resolveTestDesignTaskIdempotency(
  current: TestDesignTaskIdempotencyState | null,
  signature: string,
  createKey: () => string = createTestDesignTaskIdempotencyKey
): TestDesignTaskIdempotencyState {
  if (current?.signature === signature) {
    return current;
  }
  return { signature, key: createKey() };
}

export function createTestDesignTaskIdempotencyKey(randomSource: () => string = defaultRandomSource) {
  const randomPart = sanitizeIdempotencyKeyPart(randomSource()).slice(0, 64) || 'request';
  return `wp5:create:${Date.now().toString(36)}:${randomPart}`.slice(0, 128);
}

function normalizedText(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizedOptionalText(value: unknown) {
  const normalized = normalizedText(value);
  return normalized || null;
}

function normalizedCaseCount(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }
  return null;
}

function defaultRandomSource() {
  return globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2);
}

function sanitizeIdempotencyKeyPart(value: string) {
  return value
    .trim()
    .replace(/[^A-Za-z0-9.:_-]+/g, '-')
    .replace(/\b(api[-_]?key|access[-_]?key|secret|token|password|passwd|pwd|cookie|private[-_]?key)\b/gi, 'x')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}
