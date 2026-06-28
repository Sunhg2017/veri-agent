import { ApiError, requestJson, requestMultipart, type ApiResponse } from './client';
import { dictionaryLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

export const DOCUMENT_SOURCE_TYPES = [
  'TEXT',
  'MARKDOWN',
  'CUSTOM_API',
  'WORD',
  'PDF',
  'OCR',
  'CONFLUENCE',
  'FEISHU',
  'DINGTALK',
  'YUQUE'
] as const;

export type DocumentSourceType = (typeof DOCUMENT_SOURCE_TYPES)[number];

export const documentSourceTypeOptions: Array<{
  value: DocumentSourceType;
  label: string;
  reserved: boolean;
}> = [
  { value: 'TEXT', label: dictionaryLabel('TEXT'), reserved: false },
  { value: 'MARKDOWN', label: dictionaryLabel('MARKDOWN'), reserved: false },
  { value: 'CUSTOM_API', label: dictionaryLabel('CUSTOM_API'), reserved: false },
  { value: 'WORD', label: dictionaryLabel('WORD'), reserved: false },
  { value: 'PDF', label: dictionaryLabel('PDF'), reserved: false },
  { value: 'OCR', label: dictionaryLabel('OCR'), reserved: false },
  { value: 'CONFLUENCE', label: dictionaryLabel('CONFLUENCE'), reserved: true },
  { value: 'FEISHU', label: translate('auto.k0102'), reserved: true },
  { value: 'DINGTALK', label: translate('auto.k0103'), reserved: true },
  { value: 'YUQUE', label: translate('auto.k0104'), reserved: true }
];

export interface DocumentInputHealth {
  service?: string;
  status: string;
  timestamp?: string;
  supportedSourceTypes?: number;
  enabledSourceTypes?: DocumentSourceType[];
  inputEnabled?: boolean;
  webhookEnabled?: boolean;
  modelParseEnabled?: boolean;
  webhookMaxPayloadBytes?: number;
  importMaxContentBytes?: number;
  documentBinaryMaxBytes?: number;
  ocrConfigured?: boolean;
  ocrTimeoutSeconds?: number;
  ocrMaxOutputChars?: number;
  ocrMaxConcurrentProcesses?: number;
  ocrAvailablePermits?: number;
  ocrWorkerMode?: string;
  ocrRemoteWorkerConfigured?: boolean;
  ocrWorkerTokenConfigured?: boolean;
  ocrLocalCommandFallbackEnabled?: boolean;
  ocrLocalCommandExecutionAllowed?: boolean;
  webhookSecretCacheEnabled?: boolean;
  webhookSecretCacheTtlSeconds?: number;
  webhookSecretRotationOverlapSeconds?: number;
  webhookSecretCacheSize?: number;
  externalSecretProvider?: {
    providerCode?: string;
    providerType?: string;
    configured?: boolean;
    status?: string;
    timeoutSeconds?: number;
    maxAttempts?: number;
    checkedAt?: string;
    lastErrorMessage?: string;
  };
  batchActionLimit?: number;
}

export interface DocumentSourceView {
  id: string;
  sourceCode?: string;
  projectId?: string;
  title: string;
  sourceType: DocumentSourceType;
  sourceRef?: string;
  sourceUrl?: string;
  status?: string;
  mappingId?: string;
  secretRef?: string;
  eventVersion?: string;
  mappingVersion?: string;
  description?: string;
  enabled?: boolean;
  dataFlowSupported?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DocumentSourceHealthView {
  sourceId: string;
  sourceCode?: string;
  sourceType: DocumentSourceType;
  sourceStatus?: string;
  dataFlowSupported: boolean;
  ready: boolean;
  message?: string;
  webhookPath?: string;
  signatureAlgorithm?: string;
  secretRefConfigured?: boolean;
  eventVersion?: string;
  mappingVersion?: string;
  checkedAt?: string;
  lastEventAt?: string;
  lastEventStatus?: string;
  lastSignatureStatus?: string;
  lastErrorMessage?: string;
}

export interface DocumentSourcePayload {
  sourceCode: string;
  name: string;
  sourceType: DocumentSourceType;
  status?: string;
  endpointUrl?: string;
  defaultProjectId?: string;
  mappingId?: string;
  secretRef?: string;
  eventVersion?: string;
  mappingVersion?: string;
  description?: string;
}

export interface DocumentImportPayload {
  projectId: string;
  title?: string;
  sourceType: DocumentSourceType;
  sourceRef?: string;
  sourceUrl?: string;
  sourceId?: string;
  mappingId?: string;
  content?: string;
}

export interface DocumentImportFilePayload {
  projectId: string;
  title?: string;
  sourceType: DocumentSourceType;
  sourceRef?: string;
  sourceUrl?: string;
  sourceId?: string;
  mappingId?: string;
  file: File;
}

export interface DocumentRequirementPreview {
  id?: string;
  title?: string;
  description?: string;
  status?: string;
  priority?: string;
  acceptanceCriteria?: string;
  tags: string[];
  assetRequirementId?: string;
  parseSource?: string;
  modelInvocationId?: string;
  modelProviderName?: string;
  modelName?: string;
}

export interface DocumentImportView {
  id: string;
  projectId?: string;
  title: string;
  sourceType: DocumentSourceType;
  sourceRef?: string;
  sourceUrl?: string;
  status: string;
  createdRequirements: number;
  requirementCount: number;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
  requirements?: DocumentRequirementPreview[];
}

export interface DocumentImportList {
  items: DocumentImportView[];
}

export interface DocumentCandidateView {
  id: string;
  importId?: string;
  projectId?: string;
  title: string;
  description?: string;
  priority?: string;
  acceptanceCriteria?: string;
  tags: string[];
  status: string;
  sourceRef?: string;
  sourceFragment?: string;
  externalRequirementId?: string;
  confidence?: number;
  parseSource?: string;
  modelInvocationId?: string;
  modelProviderName?: string;
  modelName?: string;
  assetRequirementId?: string;
  errorMessage?: string;
  ignoredReason?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DocumentCandidatePayload {
  title: string;
  description?: string;
  priority?: string;
  acceptanceCriteria?: string;
  tags?: string[];
  version?: number;
}

export interface DocumentCandidateList {
  items: DocumentCandidateView[];
}

export type DocumentCandidateBatchAction = 'CONFIRM' | 'IGNORE';

export interface DocumentCandidateBatchTarget {
  id: string;
  version?: number;
}

export interface DocumentCandidateBatchActionItem {
  candidateId: string;
  result: string;
  candidate?: DocumentCandidateView;
  errorCode?: string;
  errorMessage?: string;
}

export interface DocumentCandidateBatchActionResponse {
  action: DocumentCandidateBatchAction;
  total: number;
  succeededCount: number;
  failedCount: number;
  items: DocumentCandidateBatchActionItem[];
}

export interface DocumentPublishRecordView {
  candidateId: string;
  title: string;
  candidateStatus: string;
  action: string;
  result: string;
  projectId?: string;
  externalRequirementId?: string;
  sourceRef?: string;
  sourceFragment?: string;
  assetRequirementId?: string;
  existingRequirementId?: string;
  diffSummary?: string;
  errorMessage?: string;
  version: number;
}

export interface DocumentPublishView {
  id: string;
  importId: string;
  projectId?: string;
  sourceId?: string;
  sourceCode?: string;
  sourceType: DocumentSourceType;
  sourceRef?: string;
  sourceUrl?: string;
  title: string;
  status: string;
  dryRun: boolean;
  totalParsed: number;
  totalCreated: number;
  createdRequirementIds: string[];
  pendingCount: number;
  confirmedCount: number;
  publishedCount: number;
  failedCount: number;
  plannedCreateCount: number;
  plannedUpdateCount: number;
  linkedExistingCount: number;
  conflictCount: number;
  skippedCount: number;
  publishFailedCount: number;
  records: DocumentPublishRecordView[];
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DocumentPublishOptions {
  dryRun?: boolean;
  candidateIds?: string[];
}

export interface DocumentPublishRecordList {
  items: DocumentPublishRecordView[];
}

export interface DocumentCandidateFilters {
  index?: number;
  size?: number;
  status?: string;
  sourceRef?: string;
  keyword?: string;
}

export interface WebhookEventView {
  id: string;
  sourceId?: string;
  importId?: string;
  sourceCode?: string;
  eventId?: string;
  idempotencyKey?: string;
  eventType?: string;
  eventVersion?: string;
  signatureStatus?: string;
  status: string;
  payloadDigest?: string;
  errorMessage?: string;
  retryCount: number;
  replayBy?: string;
  replayAt?: string;
  replayTraceId?: string;
  receivedAt?: string;
  processedAt?: string;
}

export interface WebhookEventList {
  items: WebhookEventView[];
}

export interface WebhookEventFilters {
  index?: number;
  size?: number;
  sourceId?: string;
  sourceCode?: string;
  eventType?: string;
  status?: string;
  receivedFrom?: string;
  receivedTo?: string;
}

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function optionalString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function numberValue(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function optionalNumber(value: unknown) {
  const parsed = numberValue(value, Number.NaN);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function confidenceValue(value: unknown) {
  const parsed = optionalNumber(value);
  if (typeof parsed !== 'number') {
    return undefined;
  }
  return parsed > 1 ? parsed / 100 : parsed;
}

function booleanValue(value: unknown, fallback = false) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  return fallback;
}

function sourceTypeValue(value: unknown): DocumentSourceType {
  return DOCUMENT_SOURCE_TYPES.includes(value as DocumentSourceType) ? (value as DocumentSourceType) : 'TEXT';
}

function requirementPreviews(value: unknown): DocumentRequirementPreview[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  return value.filter(isRecord).map((item) => ({
    id: optionalString(item.id),
    title: optionalString(item.title) ?? optionalString(item.name),
    description: optionalString(item.description),
    status: optionalString(item.status),
    priority: optionalString(item.priority),
    acceptanceCriteria: optionalString(item.acceptanceCriteria) ?? optionalString(item.acceptance_criteria),
    tags: stringArrayValue(item.tags),
    assetRequirementId: optionalString(item.assetRequirementId) ?? optionalString(item.asset_requirement_id),
    parseSource: optionalString(item.parseSource) ?? optionalString(item.parse_source),
    modelInvocationId: optionalString(item.modelInvocationId) ?? optionalString(item.model_invocation_id),
    modelProviderName: optionalString(item.modelProviderName) ?? optionalString(item.model_provider_name),
    modelName: optionalString(item.modelName) ?? optionalString(item.model_name)
  }));
}

function listItems(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (!isRecord(value)) {
    return [];
  }
  if (Array.isArray(value.items)) {
    return value.items;
  }
  if (Array.isArray(value.content)) {
    return value.content;
  }
  if (Array.isArray(value.records)) {
    return value.records;
  }
  if (Array.isArray(value.data)) {
    return value.data;
  }
  return [];
}

function stringArrayValue(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => (typeof item === 'string' ? item.trim() : ''))
      .filter(Boolean);
  }
  if (typeof value === 'string') {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
}

export function isReservedSourceType(type: DocumentSourceType) {
  return documentSourceTypeOptions.find((option) => option.value === type)?.reserved ?? true;
}

export function sourceTypeLabel(type: DocumentSourceType) {
  return documentSourceTypeOptions.find((option) => option.value === type)?.label ?? type;
}

export function documentInputErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) {
    return fallback;
  }
  const message = error.message || fallback;
  const actionable = actionableDocumentInputHint(message);
  const parts = [message];
  if (actionable && !message.includes(actionable) && !message.includes(translate('auto.k0105')) && !message.includes(translate('auto.k0106'))) {
    parts.push(actionable);
  }
  if (error instanceof ApiError) {
    if (error.code) {
      parts.push(translate('auto.k0107', { value0: error.code }));
    }
    if (error.traceId) {
      parts.push(`${fieldLabel('traceId')}：${error.traceId}`);
    }
  }
  return parts.join(' · ');
}

function actionableDocumentInputHint(message: string) {
  const normalized = message.toLowerCase();
  if (message.includes('OCR') || message.includes(translate('auto.k0108')) || message.includes(translate('auto.k0109')) || message.includes(translate('auto.k0110'))) {
    return translate('auto.k0111');
  }
  if (message.includes('PDF') && (message.includes(translate('auto.k0112')) || message.includes(translate('auto.k0113')))) {
    return translate('auto.k0114');
  }
  if (message.includes(translate('auto.k0115')) || normalized.includes('payload too large')) {
    return translate('auto.k0116');
  }
  if (message.includes('webhook') && (message.includes(translate('auto.k0117')) || message.includes('X-VA-'))) {
    return translate('auto.k0118');
  }
  return '';
}

export function normalizeDocumentSourceView(raw: unknown): DocumentSourceView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.sourceId, stringValue(item.source_id, stringValue(item.sourceCode, stringValue(item.code)))));
  return {
    id,
    sourceCode: optionalString(item.sourceCode) ?? optionalString(item.source_code) ?? optionalString(item.code),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id) ?? optionalString(item.defaultProjectId) ?? optionalString(item.default_project_id),
    title: stringValue(item.title, stringValue(item.name, id || translate('auto.k0119'))),
    sourceType: sourceTypeValue(item.sourceType ?? item.source_type),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    sourceUrl: optionalString(item.sourceUrl) ?? optionalString(item.source_url) ?? optionalString(item.endpointUrl) ?? optionalString(item.endpoint_url),
    status: optionalString(item.status),
    mappingId: optionalString(item.mappingId) ?? optionalString(item.mapping_id),
    secretRef: optionalString(item.secretRef) ?? optionalString(item.secret_ref),
    eventVersion: optionalString(item.eventVersion) ?? optionalString(item.event_version),
    mappingVersion: optionalString(item.mappingVersion) ?? optionalString(item.mapping_version),
    description: optionalString(item.description),
    enabled: typeof item.enabled === 'boolean'
      ? item.enabled
      : typeof item.dataFlowSupported === 'boolean'
        ? item.dataFlowSupported
        : typeof item.data_flow_supported === 'boolean'
          ? item.data_flow_supported
          : undefined,
    dataFlowSupported: typeof item.dataFlowSupported === 'boolean'
      ? item.dataFlowSupported
      : typeof item.data_flow_supported === 'boolean'
        ? item.data_flow_supported
        : undefined,
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeDocumentSourceHealthView(raw: unknown): DocumentSourceHealthView {
  const item = isRecord(raw) ? raw : {};
  const sourceId = stringValue(item.sourceId, stringValue(item.source_id, stringValue(item.id)));
  return {
    sourceId,
    sourceCode: optionalString(item.sourceCode) ?? optionalString(item.source_code),
    sourceType: sourceTypeValue(item.sourceType ?? item.source_type),
    sourceStatus: optionalString(item.sourceStatus) ?? optionalString(item.source_status) ?? optionalString(item.status),
    dataFlowSupported: booleanValue(item.dataFlowSupported ?? item.data_flow_supported),
    ready: booleanValue(item.ready),
    message: optionalString(item.message),
    webhookPath: optionalString(item.webhookPath) ?? optionalString(item.webhook_path),
    signatureAlgorithm: optionalString(item.signatureAlgorithm) ?? optionalString(item.signature_algorithm),
    secretRefConfigured: booleanValue(item.secretRefConfigured ?? item.secret_ref_configured),
    eventVersion: optionalString(item.eventVersion) ?? optionalString(item.event_version),
    mappingVersion: optionalString(item.mappingVersion) ?? optionalString(item.mapping_version),
    checkedAt: optionalString(item.checkedAt) ?? optionalString(item.checked_at),
    lastEventAt: optionalString(item.lastEventAt) ?? optionalString(item.last_event_at),
    lastEventStatus: optionalString(item.lastEventStatus) ?? optionalString(item.last_event_status),
    lastSignatureStatus: optionalString(item.lastSignatureStatus) ?? optionalString(item.last_signature_status),
    lastErrorMessage: optionalString(item.lastErrorMessage) ?? optionalString(item.last_error_message)
  };
}

export function normalizeDocumentImportView(raw: unknown): DocumentImportView {
  const item = isRecord(raw) ? raw : {};
  const requirements = requirementPreviews(item.requirements);
  const createdRequirements = numberValue(
    item.createdRequirements ?? item.created_requirements ?? item.createdRequirementCount ?? item.created_requirement_count ?? item.totalCreated ?? item.total_created,
    requirements?.length ?? 0
  );
  const requirementCount = numberValue(
    item.requirementCount ?? item.requirement_count ?? item.totalRequirements ?? item.total_requirements ?? item.totalParsed ?? item.total_parsed,
    createdRequirements
  );
  const id = stringValue(item.id, stringValue(item.importId, stringValue(item.import_id)));
  return {
    id,
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    title: stringValue(item.title, stringValue(item.sourceRef ?? item.source_ref, id || translate('auto.k0120'))),
    sourceType: sourceTypeValue(item.sourceType ?? item.source_type),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    sourceUrl: optionalString(item.sourceUrl) ?? optionalString(item.source_url),
    status: stringValue(item.status, 'UNKNOWN'),
    createdRequirements,
    requirementCount,
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message) ?? optionalString(item.error),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at),
    requirements
  };
}

export function normalizeDocumentCandidateView(raw: unknown): DocumentCandidateView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.candidateId, stringValue(item.candidate_id)));
  return {
    id,
    importId: optionalString(item.importId) ?? optionalString(item.import_id),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    title: stringValue(item.title, id || translate('auto.k0121')),
    description: optionalString(item.description),
    priority: optionalString(item.priority),
    acceptanceCriteria: optionalString(item.acceptanceCriteria) ?? optionalString(item.acceptance_criteria),
    tags: stringArrayValue(item.tags),
    status: stringValue(item.status, 'DRAFT'),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    sourceFragment: optionalString(item.sourceFragment) ?? optionalString(item.source_fragment),
    externalRequirementId: optionalString(item.externalRequirementId) ?? optionalString(item.external_requirement_id),
    confidence: confidenceValue(item.confidence),
    parseSource: optionalString(item.parseSource) ?? optionalString(item.parse_source),
    modelInvocationId: optionalString(item.modelInvocationId) ?? optionalString(item.model_invocation_id),
    modelProviderName: optionalString(item.modelProviderName) ?? optionalString(item.model_provider_name),
    modelName: optionalString(item.modelName) ?? optionalString(item.model_name),
    assetRequirementId: optionalString(item.assetRequirementId) ?? optionalString(item.asset_requirement_id),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message) ?? optionalString(item.error),
    ignoredReason: optionalString(item.ignoredReason) ?? optionalString(item.ignored_reason),
    confirmedBy: optionalString(item.confirmedBy) ?? optionalString(item.confirmed_by),
    confirmedAt: optionalString(item.confirmedAt) ?? optionalString(item.confirmed_at),
    version: optionalNumber(item.version),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeWebhookEventView(raw: unknown): WebhookEventView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.eventId, stringValue(item.event_id)));
  return {
    id,
    sourceId: optionalString(item.sourceId) ?? optionalString(item.source_id),
    importId: optionalString(item.importId) ?? optionalString(item.import_id),
    sourceCode: optionalString(item.sourceCode) ?? optionalString(item.source_code),
    eventId: optionalString(item.eventId) ?? optionalString(item.event_id),
    idempotencyKey: optionalString(item.idempotencyKey) ?? optionalString(item.idempotency_key),
    eventType: optionalString(item.eventType) ?? optionalString(item.event_type),
    eventVersion: optionalString(item.eventVersion) ?? optionalString(item.event_version),
    signatureStatus: optionalString(item.signatureStatus) ?? optionalString(item.signature_status),
    status: stringValue(item.status, 'UNKNOWN'),
    payloadDigest: optionalString(item.payloadDigest) ?? optionalString(item.payload_digest),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message) ?? optionalString(item.error),
    retryCount: numberValue(item.retryCount ?? item.retry_count, 0),
    replayBy: optionalString(item.replayBy) ?? optionalString(item.replay_by),
    replayAt: optionalString(item.replayAt) ?? optionalString(item.replay_at),
    replayTraceId: optionalString(item.replayTraceId) ?? optionalString(item.replay_trace_id),
    receivedAt: optionalString(item.receivedAt) ?? optionalString(item.received_at),
    processedAt: optionalString(item.processedAt) ?? optionalString(item.processed_at)
  };
}

export function normalizeDocumentCandidateBatchActionResponse(raw: unknown): DocumentCandidateBatchActionResponse {
  const item = isRecord(raw) ? raw : {};
  return {
    action: stringValue(item.action, 'CONFIRM').toUpperCase() === 'IGNORE' ? 'IGNORE' : 'CONFIRM',
    total: numberValue(item.total, listItems(item.items).length),
    succeededCount: numberValue(item.succeededCount ?? item.succeeded_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    items: listItems(item.items).filter(isRecord).map((entry) => ({
      candidateId: stringValue(entry.candidateId, stringValue(entry.candidate_id)),
      result: stringValue(entry.result, 'UNKNOWN'),
      candidate: entry.candidate ? normalizeDocumentCandidateView(entry.candidate) : undefined,
      errorCode: optionalString(entry.errorCode) ?? optionalString(entry.error_code),
      errorMessage: optionalString(entry.errorMessage) ?? optionalString(entry.error_message)
    }))
  };
}

export function normalizeDocumentPublishRecordView(raw: unknown): DocumentPublishRecordView {
  const item = isRecord(raw) ? raw : {};
  const candidateId = stringValue(item.candidateId, stringValue(item.candidate_id));
  return {
    candidateId,
    title: stringValue(item.title, candidateId || translate('auto.k0122')),
    candidateStatus: stringValue(item.candidateStatus, stringValue(item.candidate_status, 'UNKNOWN')),
    action: stringValue(item.action, 'SKIP'),
    result: stringValue(item.result, 'UNKNOWN'),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    externalRequirementId: optionalString(item.externalRequirementId) ?? optionalString(item.external_requirement_id),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    sourceFragment: optionalString(item.sourceFragment) ?? optionalString(item.source_fragment),
    assetRequirementId: optionalString(item.assetRequirementId) ?? optionalString(item.asset_requirement_id),
    existingRequirementId: optionalString(item.existingRequirementId) ?? optionalString(item.existing_requirement_id),
    diffSummary: optionalString(item.diffSummary) ?? optionalString(item.diff_summary),
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message) ?? optionalString(item.error),
    version: numberValue(item.version, 0)
  };
}

export function normalizeDocumentPublishView(raw: unknown): DocumentPublishView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.importId, stringValue(item.import_id)));
  const records = listItems(item.records).map(normalizeDocumentPublishRecordView);
  return {
    id,
    importId: stringValue(item.importId, stringValue(item.import_id, id)),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    sourceId: optionalString(item.sourceId) ?? optionalString(item.source_id),
    sourceCode: optionalString(item.sourceCode) ?? optionalString(item.source_code),
    sourceType: sourceTypeValue(item.sourceType ?? item.source_type),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    sourceUrl: optionalString(item.sourceUrl) ?? optionalString(item.source_url),
    title: stringValue(item.title, id || translate('auto.k0123')),
    status: stringValue(item.status, 'UNKNOWN'),
    dryRun: booleanValue(item.dryRun ?? item.dry_run),
    totalParsed: numberValue(item.totalParsed ?? item.total_parsed, records.length),
    totalCreated: numberValue(item.totalCreated ?? item.total_created, 0),
    createdRequirementIds: stringArrayValue(item.createdRequirementIds ?? item.created_requirement_ids),
    pendingCount: numberValue(item.pendingCount ?? item.pending_count, 0),
    confirmedCount: numberValue(item.confirmedCount ?? item.confirmed_count, 0),
    publishedCount: numberValue(item.publishedCount ?? item.published_count, 0),
    failedCount: numberValue(item.failedCount ?? item.failed_count, 0),
    plannedCreateCount: numberValue(item.plannedCreateCount ?? item.planned_create_count, 0),
    plannedUpdateCount: numberValue(item.plannedUpdateCount ?? item.planned_update_count, 0),
    linkedExistingCount: numberValue(item.linkedExistingCount ?? item.linked_existing_count, 0),
    conflictCount: numberValue(item.conflictCount ?? item.conflict_count, 0),
    skippedCount: numberValue(item.skippedCount ?? item.skipped_count, 0),
    publishFailedCount: numberValue(item.publishFailedCount ?? item.publish_failed_count, 0),
    records,
    errorMessage: optionalString(item.errorMessage) ?? optionalString(item.error_message) ?? optionalString(item.error),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function documentSourceItems(data: unknown): DocumentSourceView[] {
  return listItems(data).map(normalizeDocumentSourceView);
}

export function documentImportItems(data: unknown): DocumentImportView[] {
  return listItems(data).map(normalizeDocumentImportView);
}

export function documentCandidateItems(data: unknown): DocumentCandidateView[] {
  return listItems(data).map(normalizeDocumentCandidateView);
}

export function webhookEventItems(data: unknown): WebhookEventView[] {
  return listItems(data).map(normalizeWebhookEventView);
}

export function documentPublishRecordItems(data: unknown): DocumentPublishRecordView[] {
  return listItems(data).map(normalizeDocumentPublishRecordView);
}

export function compactDocumentPayload<T extends object>(payload: T) {
  return Object.fromEntries(
    Object.entries(payload).filter(([, value]) => {
      if (typeof value === 'string') {
        return value.trim().length > 0;
      }
      return value !== undefined && value !== null;
    })
  ) as Partial<T>;
}

export async function fetchDocumentInputHealth() {
  return requestJson<DocumentInputHealth>('/api/v1/document-input/health');
}

export async function fetchDocumentSources(): Promise<ApiResponse<DocumentSourceView[]>> {
  const response = await requestJson<unknown>('/api/v1/document-input/sources');
  return { ...response, data: documentSourceItems(response.data) };
}

export async function fetchDocumentSourceHealth(sourceId: string): Promise<ApiResponse<DocumentSourceHealthView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/sources/${encodeURIComponent(sourceId)}/health`);
  return { ...response, data: normalizeDocumentSourceHealthView(response.data) };
}

export async function createDocumentSource(payload: DocumentSourcePayload): Promise<ApiResponse<DocumentSourceView>> {
  const response = await requestJson<unknown>('/api/v1/document-input/sources', {
    method: 'POST',
    body: JSON.stringify(compactDocumentPayload(payload))
  });
  return { ...response, data: normalizeDocumentSourceView(response.data) };
}

export async function updateDocumentSource(sourceId: string, payload: DocumentSourcePayload): Promise<ApiResponse<DocumentSourceView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/sources/${encodeURIComponent(sourceId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactDocumentPayload(payload))
  });
  return { ...response, data: normalizeDocumentSourceView(response.data) };
}

export function fetchDocumentFieldMapping() {
  return requestJson<unknown>('/api/v1/document-input/field-mapping');
}

export function updateDocumentFieldMapping(mapping: unknown) {
  return requestJson<unknown>('/api/v1/document-input/field-mapping', {
    method: 'PUT',
    body: JSON.stringify(mapping)
  });
}

export async function createDocumentImport(payload: DocumentImportPayload): Promise<ApiResponse<DocumentImportView>> {
  const response = await requestJson<unknown>('/api/v1/document-input/imports', {
    method: 'POST',
    body: JSON.stringify(compactDocumentPayload(payload))
  });
  return { ...response, data: normalizeDocumentImportView(response.data) };
}

export async function createDocumentImportFile(payload: DocumentImportFilePayload): Promise<ApiResponse<DocumentImportView>> {
  const formData = new FormData();
  Object.entries(compactDocumentPayload({
    projectId: payload.projectId,
    title: payload.title,
    sourceType: payload.sourceType,
    sourceRef: payload.sourceRef,
    sourceUrl: payload.sourceUrl,
    sourceId: payload.sourceId,
    mappingId: payload.mappingId
  })).forEach(([key, value]) => {
    formData.append(key, String(value));
  });
  formData.append('file', payload.file);
  const response = await requestMultipart<unknown>('/api/v1/document-input/imports/multipart', formData);
  return { ...response, data: normalizeDocumentImportView(response.data) };
}

export async function fetchDocumentImports(): Promise<ApiResponse<DocumentImportList>> {
  const response = await requestJson<unknown>('/api/v1/document-input/imports');
  return { ...response, data: { items: documentImportItems(response.data) } };
}

export async function fetchDocumentImport(importId: string): Promise<ApiResponse<DocumentImportView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/imports/${encodeURIComponent(importId)}`);
  return { ...response, data: normalizeDocumentImportView(response.data) };
}

export async function fetchDocumentCandidates(importId: string, filters: DocumentCandidateFilters = {}): Promise<ApiResponse<DocumentCandidateList>> {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.sourceRef?.trim()) params.set('sourceRef', filters.sourceRef.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  const query = params.toString();
  const response = await requestJson<unknown>(`/api/v1/document-input/imports/${encodeURIComponent(importId)}/candidates${query ? `?${query}` : ''}`);
  return { ...response, data: { items: documentCandidateItems(response.data) } };
}

export async function updateDocumentCandidate(candidateId: string, payload: DocumentCandidatePayload): Promise<ApiResponse<DocumentCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/candidates/${encodeURIComponent(candidateId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactDocumentPayload(payload))
  });
  return { ...response, data: normalizeDocumentCandidateView(response.data) };
}

export async function confirmDocumentCandidate(candidateId: string, version?: number): Promise<ApiResponse<DocumentCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/candidates/${encodeURIComponent(candidateId)}/confirm`, {
    method: 'POST',
    body: JSON.stringify(compactDocumentPayload({ version }))
  });
  return { ...response, data: normalizeDocumentCandidateView(response.data) };
}

export async function ignoreDocumentCandidate(candidateId: string, reason: string, version?: number): Promise<ApiResponse<DocumentCandidateView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/candidates/${encodeURIComponent(candidateId)}/ignore`, {
    method: 'POST',
    body: JSON.stringify(compactDocumentPayload({ reason, version }))
  });
  return { ...response, data: normalizeDocumentCandidateView(response.data) };
}

export async function batchDocumentCandidateAction(
  action: DocumentCandidateBatchAction,
  candidates: string[] | DocumentCandidateBatchTarget[],
  reason?: string
): Promise<ApiResponse<DocumentCandidateBatchActionResponse>> {
  const usesVersionedTargets = candidates.some((candidate) => typeof candidate !== 'string');
  const response = await requestJson<unknown>('/api/v1/document-input/candidates/batch-action', {
    method: 'POST',
    body: JSON.stringify(compactDocumentPayload({
      action,
      candidateIds: usesVersionedTargets ? undefined : candidates,
      candidates: usesVersionedTargets ? candidates : undefined,
      reason: reason?.trim() || undefined
    }))
  });
  return { ...response, data: normalizeDocumentCandidateBatchActionResponse(response.data) };
}

export async function publishDocumentImport(
  importId: string,
  options: DocumentPublishOptions = {}
): Promise<ApiResponse<DocumentPublishView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/imports/${encodeURIComponent(importId)}/publish`, {
    method: 'POST',
    body: JSON.stringify(compactDocumentPayload({
      dryRun: options.dryRun,
      candidateIds: options.candidateIds?.length ? options.candidateIds : undefined
    }))
  });
  return { ...response, data: normalizeDocumentPublishView(response.data) };
}

export async function fetchDocumentPublishRecords(importId: string): Promise<ApiResponse<DocumentPublishRecordList>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/imports/${encodeURIComponent(importId)}/publish-records`);
  return { ...response, data: { items: documentPublishRecordItems(response.data) } };
}

export async function fetchWebhookEvents(filters: WebhookEventFilters = {}): Promise<ApiResponse<WebhookEventList>> {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.sourceId?.trim()) params.set('sourceId', filters.sourceId.trim());
  if (filters.sourceCode?.trim()) params.set('sourceCode', filters.sourceCode.trim());
  if (filters.eventType?.trim()) params.set('eventType', filters.eventType.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.receivedFrom?.trim()) params.set('receivedFrom', filters.receivedFrom.trim());
  if (filters.receivedTo?.trim()) params.set('receivedTo', filters.receivedTo.trim());
  const query = params.toString();
  const response = await requestJson<unknown>(`/api/v1/document-input/webhook-events${query ? `?${query}` : ''}`);
  return { ...response, data: { items: webhookEventItems(response.data) } };
}

export async function fetchWebhookEvent(eventId: string): Promise<ApiResponse<WebhookEventView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/webhook-events/${encodeURIComponent(eventId)}`);
  return { ...response, data: normalizeWebhookEventView(response.data) };
}

export async function replayWebhookEvent(eventId: string): Promise<ApiResponse<WebhookEventView>> {
  const response = await requestJson<unknown>(`/api/v1/document-input/webhook-events/${encodeURIComponent(eventId)}/replay`, {
    method: 'POST'
  });
  return { ...response, data: normalizeWebhookEventView(response.data) };
}

export function postDocumentWebhook(sourceCode: string, payload: unknown) {
  return requestJson<unknown>(`/api/v1/document-input/webhooks/${encodeURIComponent(sourceCode)}`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}
