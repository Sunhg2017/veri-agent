import type { ExecutionDagNodePayload, ExecutionPlanDetail } from './api/execution';
import { translate } from './platform/i18n';

export type ExecutionNodeType = 'API_TEST' | 'REPORT_HANDOFF';
export type ExecutionFailurePolicy = 'FAIL_FAST' | 'CONTINUE' | 'BLOCK_DOWNSTREAM';

export type ExecutionDagNodeDraft = {
  key: string;
  type: ExecutionNodeType;
  dependenciesText: string;
  apiAutomationBundleId: string;
  baseUrlRef: string;
  caseIdsText: string;
  runtimeSecretRefsText: string;
  accountPoolRef: string;
  accountLeaseApplicationId: string;
  accountLeaseEnvironmentId: string;
  accountLeaseRoleTagsText: string;
  accountLeaseTtlSeconds: number;
  accountLeaseRequestKey: string;
  timeoutSeconds: number;
  failurePolicy: ExecutionFailurePolicy;
  maxAttempts: number;
};

export type ExecutionPlanDraft = {
  projectId: string;
  name: string;
  environmentKey: string;
  status: 'DRAFT' | 'READY' | 'DISABLED';
  description: string;
  nodes: ExecutionDagNodeDraft[];
};

export type ExecutionDraftIssue = {
  field: string;
  message: string;
};

export const initialExecutionNodeDraft: ExecutionDagNodeDraft = {
  key: 'api-smoke',
  type: 'API_TEST',
  dependenciesText: '',
  apiAutomationBundleId: '',
  baseUrlRef: '',
  caseIdsText: '',
  runtimeSecretRefsText: '',
  accountPoolRef: '',
  accountLeaseApplicationId: '',
  accountLeaseEnvironmentId: '',
  accountLeaseRoleTagsText: '',
  accountLeaseTtlSeconds: 0,
  accountLeaseRequestKey: '',
  timeoutSeconds: 180,
  failurePolicy: 'FAIL_FAST',
  maxAttempts: 1
};

export const initialExecutionPlanDraft: ExecutionPlanDraft = {
  projectId: '',
  name: '',
  environmentKey: '',
  status: 'DRAFT',
  description: '',
  nodes: [initialExecutionNodeDraft]
};

const NODE_KEY_PATTERN = /^[A-Za-z0-9_-]+$/;

export function blankExecutionNodeDraft(index: number): ExecutionDagNodeDraft {
  return {
    ...initialExecutionNodeDraft,
    key: index <= 1 ? 'api-smoke' : `node-${index}`
  };
}

export function executionPlanDraftFromDetail(plan: ExecutionPlanDetail): ExecutionPlanDraft {
  return {
    projectId: plan.projectId,
    name: plan.name,
    environmentKey: plan.environmentKey,
    status: normalizePlanDraftStatus(plan.status),
    description: plan.description ?? '',
    nodes: plan.nodes.length
      ? plan.nodes.map((node) => ({
        key: node.key,
        type: normalizeNodeType(node.type),
        dependenciesText: node.dependencies.join(', '),
        apiAutomationBundleId: stringValue(node.inputSummary.apiAutomationBundleId),
        baseUrlRef: stringValue(node.inputSummary.baseUrlRef),
        caseIdsText: stringListValue(node.inputSummary.caseIds).join(', '),
        runtimeSecretRefsText: runtimeSecretRefsText(node.inputSummary.runtimeSecretRefs),
        accountPoolRef: accountLeaseString(node.inputSummary.accountLease, 'accountPoolRef'),
        accountLeaseApplicationId: accountLeaseString(node.inputSummary.accountLease, 'applicationId'),
        accountLeaseEnvironmentId: accountLeaseString(node.inputSummary.accountLease, 'environmentId'),
        accountLeaseRoleTagsText: stringListValue(accountLeaseValue(node.inputSummary.accountLease, 'roleTags')).join(', '),
        accountLeaseTtlSeconds: numberValue(accountLeaseValue(node.inputSummary.accountLease, 'ttlSeconds'), 0),
        accountLeaseRequestKey: accountLeaseString(node.inputSummary.accountLease, 'requestKey'),
        timeoutSeconds: node.timeoutSeconds,
        failurePolicy: normalizeFailurePolicy(node.failurePolicy),
        maxAttempts: numberValue(node.retryPolicy.maxAttempts, 1)
      }))
      : [initialExecutionNodeDraft]
  };
}

export function validateExecutionPlanDraft(draft: ExecutionPlanDraft): ExecutionDraftIssue[] {
  const issues: ExecutionDraftIssue[] = [];
  if (!draft.projectId.trim()) issues.push({ field: 'projectId', message: translate('auto.k1994') });
  if (!draft.name.trim()) issues.push({ field: 'name', message: translate('auto.k1995') });
  if (!draft.environmentKey.trim()) issues.push({ field: 'environmentKey', message: translate('auto.k1996') });
  if (!draft.nodes.length) issues.push({ field: 'nodes', message: translate('auto.k1997') });

  const seenKeys = new Set<string>();
  const duplicateKeys = new Set<string>();
  draft.nodes.forEach((node, index) => {
    const label = `nodes.${index}`;
    const key = node.key.trim();
    if (!key) {
      issues.push({ field: `${label}.key`, message: translate('auto.k1998') });
    } else if (!NODE_KEY_PATTERN.test(key)) {
      issues.push({ field: `${label}.key`, message: translate('auto.k1999', { value0: key }) });
    }
    if (seenKeys.has(key)) duplicateKeys.add(key);
    seenKeys.add(key);
    if (!Number.isFinite(node.timeoutSeconds) || node.timeoutSeconds < 1 || node.timeoutSeconds > 86400) {
      issues.push({ field: `${label}.timeoutSeconds`, message: translate('auto.k2000', { value0: key || index + 1 }) });
    }
    if (!Number.isFinite(node.maxAttempts) || node.maxAttempts < 0 || node.maxAttempts > 5) {
      issues.push({ field: `${label}.maxAttempts`, message: translate('auto.k2001', { value0: key || index + 1 }) });
    }
    if (node.type === 'API_TEST' && node.accountPoolRef.trim()
      && (!Number.isFinite(node.accountLeaseTtlSeconds) || node.accountLeaseTtlSeconds < 0 || node.accountLeaseTtlSeconds > 604800)) {
      issues.push({ field: `${label}.accountLeaseTtlSeconds`, message: translate('auto.k2002', { value0: key || index + 1 }) });
    }
  });
  duplicateKeys.forEach((key) => issues.push({ field: 'nodes.key', message: translate('auto.k2003', { value0: key }) }));

  const keys = new Set(draft.nodes.map((node) => node.key.trim()).filter(Boolean));
  draft.nodes.forEach((node) => {
    parseCommaSeparated(node.dependenciesText).forEach((dependency) => {
      if (!keys.has(dependency)) {
        issues.push({ field: `${node.key}.dependencies`, message: translate('auto.k2004', { value0: node.key || '-', value1: dependency }) });
      }
      if (dependency === node.key.trim()) {
        issues.push({ field: `${node.key}.dependencies`, message: translate('auto.k2005', { value0: node.key }) });
      }
    });
  });

  findCycle(draft.nodes)?.forEach((key) => {
    issues.push({ field: 'nodes.dependencies', message: translate('auto.k2006', { value0: key }) });
  });

  return issues;
}

export function buildExecutionPlanPayload(draft: ExecutionPlanDraft) {
  return {
    projectId: draft.projectId.trim(),
    name: draft.name.trim(),
    environmentKey: draft.environmentKey.trim(),
    status: draft.status,
    description: optionalText(draft.description),
    triggerPolicy: { manualEnabled: true, webhookEnabled: false, cronEnabled: false },
    dag: {
      nodes: draft.nodes.map(buildExecutionNodePayload)
    }
  };
}

export function buildExecutionPlanUpdatePayload(draft: ExecutionPlanDraft) {
  const payload = buildExecutionPlanPayload(draft);
  return {
    name: payload.name,
    environmentKey: payload.environmentKey,
    status: payload.status,
    description: payload.description,
    triggerPolicy: payload.triggerPolicy,
    dag: payload.dag
  };
}

export function buildExecutionNodePayload(node: ExecutionDagNodeDraft): ExecutionDagNodePayload {
  const input: Record<string, unknown> = {
    uiCreated: true,
    rawBaseUrlStored: false,
    secretRefsStored: false
  };
  const apiAutomationBundleId = node.apiAutomationBundleId.trim();
  const baseUrlRef = node.baseUrlRef.trim();
  const caseIds = parseCommaSeparated(node.caseIdsText);
  const runtimeSecretRefs = parseCommaSeparated(node.runtimeSecretRefsText);
  const accountLease = node.type === 'API_TEST' ? accountLeasePayload(node) : null;
  if (apiAutomationBundleId) input.apiAutomationBundleId = apiAutomationBundleId;
  if (baseUrlRef) input.baseUrlRef = baseUrlRef;
  if (caseIds.length) input.caseIds = caseIds;
  if (runtimeSecretRefs.length) input.runtimeSecretRefs = runtimeSecretRefs;
  if (accountLease) input.accountLease = accountLease;
  return {
    key: node.key.trim(),
    type: node.type,
    dependencies: parseCommaSeparated(node.dependenciesText),
    input,
    timeoutSeconds: boundedNumber(node.timeoutSeconds, 180),
    failurePolicy: node.failurePolicy,
    retryPolicy: { maxAttempts: boundedNumber(node.maxAttempts, 1) }
  };
}

export function summarizeDraftNode(node: ExecutionDagNodeDraft) {
  const dependencies = parseCommaSeparated(node.dependenciesText);
  const fragments = [
    node.type,
    dependencies.length ? `deps ${dependencies.join(',')}` : 'root',
    `${node.timeoutSeconds || 0}s`,
    `retry ${node.maxAttempts || 0}`
  ];
  if (node.apiAutomationBundleId.trim()) fragments.push(`bundle ${shortValue(node.apiAutomationBundleId)}`);
  if (node.baseUrlRef.trim()) fragments.push(`baseUrlRef ${shortValue(node.baseUrlRef)}`);
  if (node.type === 'API_TEST' && node.accountPoolRef.trim()) fragments.push(`lease ${shortValue(node.accountPoolRef)}`);
  return fragments.join(' · ');
}

export function parseCommaSeparated(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function findCycle(nodes: ExecutionDagNodeDraft[]) {
  const graph = new Map<string, string[]>();
  nodes.forEach((node) => {
    const key = node.key.trim();
    if (key) graph.set(key, parseCommaSeparated(node.dependenciesText));
  });
  const visiting = new Set<string>();
  const visited = new Set<string>();

  function visit(key: string): string[] | null {
    if (visiting.has(key)) return [key];
    if (visited.has(key)) return null;
    visiting.add(key);
    for (const dependency of graph.get(key) ?? []) {
      if (!graph.has(dependency)) continue;
      const cycle = visit(dependency);
      if (cycle) return [key, ...cycle];
    }
    visiting.delete(key);
    visited.add(key);
    return null;
  }

  for (const key of graph.keys()) {
    const cycle = visit(key);
    if (cycle) return cycle;
  }
  return null;
}

function normalizePlanDraftStatus(status: string): ExecutionPlanDraft['status'] {
  return status === 'READY' || status === 'DISABLED' ? status : 'DRAFT';
}

function normalizeNodeType(type: string): ExecutionNodeType {
  return type === 'REPORT_HANDOFF' ? 'REPORT_HANDOFF' : 'API_TEST';
}

function normalizeFailurePolicy(policy: string): ExecutionFailurePolicy {
  return policy === 'CONTINUE' || policy === 'BLOCK_DOWNSTREAM' ? policy : 'FAIL_FAST';
}

function runtimeSecretRefsText(value: unknown) {
  if (Array.isArray(value)) {
    return stringListValue(value).filter((item) => item.startsWith('secret://')).join(', ');
  }
  return '';
}

function accountLeasePayload(node: ExecutionDagNodeDraft) {
  const accountPoolRef = node.accountPoolRef.trim();
  if (!accountPoolRef) return null;
  const payload: Record<string, unknown> = { accountPoolRef };
  const applicationId = node.accountLeaseApplicationId.trim();
  const environmentId = node.accountLeaseEnvironmentId.trim();
  const roleTags = parseCommaSeparated(node.accountLeaseRoleTagsText);
  const requestKey = node.accountLeaseRequestKey.trim();
  if (applicationId) payload.applicationId = applicationId;
  if (environmentId) payload.environmentId = environmentId;
  if (roleTags.length) payload.roleTags = roleTags;
  if (Number.isFinite(node.accountLeaseTtlSeconds) && node.accountLeaseTtlSeconds > 0) payload.ttlSeconds = node.accountLeaseTtlSeconds;
  if (requestKey) payload.requestKey = requestKey;
  return payload;
}

function accountLeaseValue(value: unknown, key: string): unknown {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  return (value as Record<string, unknown>)[key];
}

function accountLeaseString(value: unknown, key: string) {
  return stringValue(accountLeaseValue(value, key));
}

function stringListValue(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => stringValue(item)).filter(Boolean) : [];
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : value == null ? '' : String(value);
}

function numberValue(value: unknown, fallback: number) {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function boundedNumber(value: number, fallback: number) {
  return Number.isFinite(value) ? value : fallback;
}

function shortValue(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 12 ? `${trimmed.slice(0, 12)}...` : trimmed;
}
