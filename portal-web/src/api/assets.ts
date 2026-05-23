import { requestJson, requestText, type ApiResponse, type TextResponse } from './client';

export const ASSET_REQUIREMENT_STATUSES = ['DRAFT', 'REVIEWING', 'APPROVED', 'DEPRECATED'] as const;
export const ASSET_REQUIREMENT_PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
export const ASSET_REQUIREMENT_SOURCES = ['MANUAL', 'IMPORT'] as const;
export const ASSET_API_STATUSES = ['ACTIVE', 'DEPRECATED', 'REMOVED'] as const;
export const ASSET_API_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const;
export const ASSET_PAGE_STATUSES = ['ACTIVE', 'DEPRECATED'] as const;
export const ASSET_PAGE_SOURCES = ['MANUAL', 'FIGMA', 'LANHU', 'AXURE'] as const;
export const ASSET_FLOW_STATUSES = ['DRAFT', 'ACTIVE', 'ARCHIVED'] as const;
export const ASSET_TEST_CASE_STATUSES = ['DRAFT', 'REVIEWING', 'APPROVED', 'DEPRECATED'] as const;

export type AssetRequirementStatus = (typeof ASSET_REQUIREMENT_STATUSES)[number];
export type AssetRequirementPriority = (typeof ASSET_REQUIREMENT_PRIORITIES)[number];
export type AssetRequirementSource = (typeof ASSET_REQUIREMENT_SOURCES)[number];
export type AssetApiStatus = (typeof ASSET_API_STATUSES)[number];
export type AssetApiMethod = (typeof ASSET_API_METHODS)[number];
export type AssetPageStatus = (typeof ASSET_PAGE_STATUSES)[number];
export type AssetPageSource = (typeof ASSET_PAGE_SOURCES)[number];
export type AssetFlowStatus = (typeof ASSET_FLOW_STATUSES)[number];
export type AssetTestCaseStatus = (typeof ASSET_TEST_CASE_STATUSES)[number];

export interface AssetHealth {
  service: string;
  status: string;
}

export interface AssetRequirementView {
  id: string;
  title: string;
  description?: string;
  source: AssetRequirementSource | string;
  sourceRef?: string;
  sourceUrl?: string;
  acceptanceCriteria?: string;
  status: AssetRequirementStatus | string;
  priority: AssetRequirementPriority | string;
  projectId?: string;
  tags: string[];
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AssetRequirementList {
  items: AssetRequirementView[];
  page?: number;
  pageSize?: number;
  total: number;
}

export interface AssetRequirementFilters {
  index?: number;
  size?: number;
  projectId?: string;
  status?: string;
  keyword?: string;
  source?: string;
  sourceRef?: string;
}

export interface AssetRequirementPayload {
  title: string;
  projectId?: string;
  description?: string;
  source?: string;
  sourceRef?: string;
  sourceUrl?: string;
  acceptanceCriteria?: string;
  status?: string;
  priority?: string;
  tags?: string[] | string;
}

export interface AssetApiView {
  id: string;
  code?: string;
  summary: string;
  description?: string;
  httpMethod: AssetApiMethod | string;
  path: string;
  source?: string;
  sourceRef?: string;
  version?: string;
  requestSchema?: string;
  responseSchema?: string;
  projectId?: string;
  status: AssetApiStatus | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AssetApiList {
  items: AssetApiView[];
  page?: number;
  pageSize?: number;
  total: number;
}

export interface AssetApiFilters {
  index?: number;
  size?: number;
  projectId?: string;
  status?: string;
  keyword?: string;
  source?: string;
}

export interface AssetApiPayload {
  summary: string;
  projectId?: string;
  description?: string;
  httpMethod: string;
  path: string;
  version?: string;
  requestSchema?: string;
  responseSchema?: string;
  status?: string;
}

export interface AssetPageView {
  id: string;
  code?: string;
  name: string;
  urlPattern?: string;
  source: AssetPageSource | string;
  sourceRef?: string;
  sourceVersion?: string;
  componentTree?: string;
  screenshotUrl?: string;
  projectId?: string;
  status: AssetPageStatus | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AssetPageList {
  items: AssetPageView[];
  page?: number;
  pageSize?: number;
  total: number;
}

export interface AssetPageFilters {
  index?: number;
  size?: number;
  projectId?: string;
  status?: string;
  keyword?: string;
  source?: string;
}

export interface AssetPagePayload {
  name: string;
  projectId?: string;
  urlPattern?: string;
  source?: string;
  sourceRef?: string;
  sourceVersion?: string;
  componentTree?: unknown;
  screenshotUrl?: string;
  status?: string;
}

export interface AssetBusinessFlowView {
  id: string;
  code?: string;
  name: string;
  description?: string;
  flowJson?: string;
  priority: AssetRequirementPriority | string;
  projectId?: string;
  status: AssetFlowStatus | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AssetBusinessFlowList {
  items: AssetBusinessFlowView[];
  page?: number;
  pageSize?: number;
  total: number;
}

export interface AssetBusinessFlowFilters {
  index?: number;
  size?: number;
  projectId?: string;
  status?: string;
  keyword?: string;
}

export interface AssetBusinessFlowPayload {
  name: string;
  projectId?: string;
  description?: string;
  flowJson?: unknown;
  priority?: string;
  status?: string;
}

export interface AssetTestCaseStepView {
  stepOrder: number;
  action?: string;
  expectedResult?: string;
}

export interface AssetTestCaseView {
  id: string;
  code?: string;
  title: string;
  description?: string;
  requirementId?: string;
  apiId?: string;
  source?: string;
  sourceRef?: string;
  projectId?: string;
  status: AssetTestCaseStatus | string;
  priority: AssetRequirementPriority | string;
  tags: string[];
  steps: AssetTestCaseStepView[];
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AssetTestCaseList {
  items: AssetTestCaseView[];
  page?: number;
  pageSize?: number;
  total: number;
}

export interface AssetTestCaseFilters {
  index?: number;
  size?: number;
  projectId?: string;
  status?: string;
  keyword?: string;
  source?: string;
}

export interface AssetTestCaseStepPayload {
  action?: string;
  expectedResult?: string;
}

export interface AssetTestCasePayload {
  title: string;
  projectId?: string;
  description?: string;
  requirementId?: string;
  apiId?: string;
  status?: string;
  priority?: string;
  tags?: string[] | string;
  steps?: AssetTestCaseStepPayload[];
}

export interface AssetTestCaseStepsPayload {
  steps: AssetTestCaseStepPayload[];
}

export interface TraceLinkView {
  id: string;
  requirementId?: string;
  apiId?: string;
  pageId?: string;
  flowId?: string;
  caseId?: string;
  createdAt?: string;
}

export interface TraceLinkList {
  items: TraceLinkView[];
  total: number;
}

export interface TraceLinkFilters {
  index?: number;
  size?: number;
  requirementId?: string;
  apiId?: string;
  pageId?: string;
  flowId?: string;
  caseId?: string;
}

export type AssetImportExportType = 'REQUIREMENT' | 'API' | 'TEST_CASE';
export type AssetImportExportFormat = 'CSV' | 'JSON' | 'OPENAPI';

export interface AssetImportPayload {
  assetType: AssetImportExportType | string;
  format: AssetImportExportFormat | string;
  projectId: string;
  dryRun?: boolean;
  content: string;
}

export interface AssetImportItemView {
  row: number;
  action: string;
  id?: string;
  code?: string;
  status: string;
  message?: string;
  errors: string[];
}

export interface AssetImportResult {
  assetType: string;
  format: string;
  dryRun: boolean;
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  items: AssetImportItemView[];
}

export interface AssetExportFilters {
  assetType: AssetImportExportType | string;
  format?: AssetImportExportFormat | string;
  projectId?: string;
  status?: string;
  keyword?: string;
  source?: string;
  index?: number;
  size?: number;
}

export interface AssetImpactNodeView {
  assetType: string;
  id: string;
  code?: string;
  title: string;
  projectId?: string;
  status?: string;
  lifecycleStatus?: string;
  updatedAt?: string;
}

export interface AssetImpactAnalysisView {
  projectId: string;
  subjectType?: string;
  subjectId?: string;
  requirementCount: number;
  apiCount: number;
  pageCount: number;
  flowCount: number;
  caseCount: number;
  requirements: AssetImpactNodeView[];
  apis: AssetImpactNodeView[];
  pages: AssetImpactNodeView[];
  flows: AssetImpactNodeView[];
  testCases: AssetImpactNodeView[];
  gaps: string[];
  generatedAt?: string;
}

export interface AssetPrototypeSyncPagePayload {
  name: string;
  urlPattern?: string;
  sourceRef?: string;
  sourceVersion?: string;
  componentTree?: unknown;
  screenshotUrl?: string;
  status?: string;
}

export interface AssetPrototypeSyncPayload {
  projectId: string;
  source: string;
  connectorRef?: string;
  sourceVersion?: string;
  dryRun?: boolean;
  pages: AssetPrototypeSyncPagePayload[];
}

export interface AssetPrototypeSyncResult {
  source: string;
  dryRun: boolean;
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  items: AssetImportItemView[];
}

export interface AssetVersionHistoryView {
  id: string;
  assetType: string;
  assetId: string;
  projectId?: string;
  version: number;
  changeType: string;
  actor?: string;
  changedFields: string[];
  diff?: unknown;
  snapshot?: unknown;
  traceId?: string;
  createdAt?: string;
}

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function optionalString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function optionalJsonString(value: unknown) {
  if (typeof value === 'string') {
    return value.trim() ? value.trim() : undefined;
  }
  if (value === undefined || value === null) {
    return undefined;
  }
  try {
    return JSON.stringify(value);
  } catch {
    return undefined;
  }
}

function optionalJsonValue(value: unknown) {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value !== 'string') {
    return value;
  }
  const normalized = value.trim();
  if (!normalized) {
    return undefined;
  }
  try {
    return JSON.parse(normalized) as unknown;
  } catch {
    return normalized;
  }
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

function enumString<T extends readonly string[]>(value: unknown, allowed: T, fallback: T[number]): T[number] | string {
  if (typeof value !== 'string' || !value.trim()) {
    return fallback;
  }
  const normalized = value.trim().toUpperCase();
  return allowed.includes(normalized as T[number]) ? (normalized as T[number]) : value.trim();
}

function pageTotal(value: unknown, fallback: number) {
  if (!isRecord(value)) {
    return fallback;
  }
  return numberValue(value.total ?? value.totalElements ?? value.total_elements ?? value.count, fallback);
}

function compactAssetPayload(payload: object) {
  return Object.fromEntries(
    Object.entries(payload as Record<string, unknown>).flatMap(([key, value]) => {
      if (Array.isArray(value)) {
        if (key === 'tags') {
          const normalized = value.map((item) => String(item).trim()).filter(Boolean).join(',');
          return normalized ? [[key, normalized]] : [];
        }
        return value.length ? [[key, value]] : [];
      }
      if (typeof value === 'string') {
        const normalized = value.trim();
        return normalized ? [[key, normalized]] : [];
      }
      return value === undefined || value === null ? [] : [[key, value]];
    })
  );
}

function assetQuery(filters: AssetRequirementFilters) {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.projectId?.trim()) params.set('projectId', filters.projectId.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.source?.trim()) params.set('source', filters.source.trim());
  const query = params.toString();
  return query ? `?${query}` : '';
}

function apiQuery(filters: AssetApiFilters) {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.projectId?.trim()) params.set('projectId', filters.projectId.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.source?.trim()) params.set('source', filters.source.trim());
  const query = params.toString();
  return query ? `?${query}` : '';
}

function pageQuery(filters: AssetPageFilters) {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.projectId?.trim()) params.set('projectId', filters.projectId.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.source?.trim()) params.set('source', filters.source.trim());
  const query = params.toString();
  return query ? `?${query}` : '';
}

function businessFlowQuery(filters: AssetBusinessFlowFilters) {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.projectId?.trim()) params.set('projectId', filters.projectId.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  const query = params.toString();
  return query ? `?${query}` : '';
}

function testCaseQuery(filters: AssetTestCaseFilters) {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.projectId?.trim()) params.set('projectId', filters.projectId.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.source?.trim()) params.set('source', filters.source.trim());
  const query = params.toString();
  return query ? `?${query}` : '';
}

function traceLinkQuery(filters: TraceLinkFilters) {
  const params = new URLSearchParams();
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.requirementId?.trim()) params.set('requirementId', filters.requirementId.trim());
  if (filters.apiId?.trim()) params.set('apiId', filters.apiId.trim());
  if (filters.pageId?.trim()) params.set('pageId', filters.pageId.trim());
  if (filters.flowId?.trim()) params.set('flowId', filters.flowId.trim());
  if (filters.caseId?.trim()) params.set('caseId', filters.caseId.trim());
  const query = params.toString();
  return query ? `?${query}` : '';
}

function assetExportQuery(filters: AssetExportFilters) {
  const params = new URLSearchParams();
  params.set('assetType', filters.assetType);
  if (filters.format?.trim()) params.set('format', filters.format.trim());
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  if (filters.projectId?.trim()) params.set('projectId', filters.projectId.trim());
  if (filters.status?.trim()) params.set('status', filters.status.trim());
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.source?.trim()) params.set('source', filters.source.trim());
  return `?${params.toString()}`;
}

export function normalizeAssetHealth(raw: unknown): AssetHealth {
  const item = isRecord(raw) ? raw : {};
  return {
    service: stringValue(item.service, 'asset-service'),
    status: stringValue(item.status, 'UNKNOWN')
  };
}

export function normalizeAssetRequirementView(raw: unknown): AssetRequirementView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.requirementId, stringValue(item.requirement_id)));
  return {
    id,
    title: stringValue(item.title, stringValue(item.name, id || '未命名需求')),
    description: optionalString(item.description),
    source: enumString(item.source, ASSET_REQUIREMENT_SOURCES, 'MANUAL'),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref) ?? optionalString(item.externalRequirementId) ?? optionalString(item.external_requirement_id),
    sourceUrl: optionalString(item.sourceUrl) ?? optionalString(item.source_url),
    acceptanceCriteria: optionalString(item.acceptanceCriteria) ?? optionalString(item.acceptance_criteria),
    status: enumString(item.status, ASSET_REQUIREMENT_STATUSES, 'DRAFT'),
    priority: enumString(item.priority, ASSET_REQUIREMENT_PRIORITIES, 'MEDIUM'),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    tags: stringArrayValue(item.tags ?? item.tagList ?? item.tag_list),
    version: numberValue(item.version, 0),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function assetRequirementItems(data: unknown): AssetRequirementView[] {
  return listItems(data).map(normalizeAssetRequirementView);
}

export function normalizeAssetRequirementList(raw: unknown): AssetRequirementList {
  const items = assetRequirementItems(raw);
  return {
    items,
    page: isRecord(raw) ? optionalNumber(raw.page ?? raw.number ?? raw.index) : undefined,
    pageSize: isRecord(raw) ? optionalNumber(raw.pageSize ?? raw.page_size ?? raw.size) : undefined,
    total: pageTotal(raw, items.length)
  };
}

export function normalizeAssetApiView(raw: unknown): AssetApiView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.apiId, stringValue(item.api_id)));
  return {
    id,
    code: optionalString(item.code),
    summary: stringValue(item.summary, stringValue(item.name, id || '未命名 API')),
    description: optionalString(item.description),
    httpMethod: enumString(item.httpMethod ?? item.http_method, ASSET_API_METHODS, 'GET'),
    path: stringValue(item.path, '/'),
    source: optionalString(item.source),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    version: optionalString(item.version),
    requestSchema: optionalString(item.requestSchema) ?? optionalString(item.request_schema),
    responseSchema: optionalString(item.responseSchema) ?? optionalString(item.response_schema),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    status: enumString(item.status, ASSET_API_STATUSES, 'ACTIVE'),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function assetApiItems(data: unknown): AssetApiView[] {
  return listItems(data).map(normalizeAssetApiView);
}

export function normalizeAssetApiList(raw: unknown): AssetApiList {
  const items = assetApiItems(raw);
  return {
    items,
    page: isRecord(raw) ? optionalNumber(raw.page ?? raw.number ?? raw.index) : undefined,
    pageSize: isRecord(raw) ? optionalNumber(raw.pageSize ?? raw.page_size ?? raw.size) : undefined,
    total: pageTotal(raw, items.length)
  };
}

export function normalizeAssetPageView(raw: unknown): AssetPageView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.pageId, stringValue(item.page_id)));
  return {
    id,
    code: optionalString(item.code),
    name: stringValue(item.name, stringValue(item.title, id || '未命名页面')),
    urlPattern: optionalString(item.urlPattern) ?? optionalString(item.url_pattern),
    source: enumString(item.source, ASSET_PAGE_SOURCES, 'MANUAL'),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    sourceVersion: optionalString(item.sourceVersion) ?? optionalString(item.source_version),
    componentTree: optionalJsonString(item.componentTree) ?? optionalJsonString(item.component_tree),
    screenshotUrl: optionalString(item.screenshotUrl) ?? optionalString(item.screenshot_url),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    status: enumString(item.status, ASSET_PAGE_STATUSES, 'ACTIVE'),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function assetPageItems(data: unknown): AssetPageView[] {
  return listItems(data).map(normalizeAssetPageView);
}

export function normalizeAssetPageList(raw: unknown): AssetPageList {
  const items = assetPageItems(raw);
  return {
    items,
    page: isRecord(raw) ? optionalNumber(raw.page ?? raw.number ?? raw.index) : undefined,
    pageSize: isRecord(raw) ? optionalNumber(raw.pageSize ?? raw.page_size ?? raw.size) : undefined,
    total: pageTotal(raw, items.length)
  };
}

export function normalizeAssetBusinessFlowView(raw: unknown): AssetBusinessFlowView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.flowId, stringValue(item.flow_id)));
  return {
    id,
    code: optionalString(item.code),
    name: stringValue(item.name, id || '未命名业务流'),
    description: optionalString(item.description),
    flowJson: optionalJsonString(item.flowJson) ?? optionalJsonString(item.flow_json),
    priority: enumString(item.priority, ASSET_REQUIREMENT_PRIORITIES, 'MEDIUM'),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    status: enumString(item.status, ASSET_FLOW_STATUSES, 'DRAFT'),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function assetBusinessFlowItems(data: unknown): AssetBusinessFlowView[] {
  return listItems(data).map(normalizeAssetBusinessFlowView);
}

export function normalizeAssetBusinessFlowList(raw: unknown): AssetBusinessFlowList {
  const items = assetBusinessFlowItems(raw);
  return {
    items,
    page: isRecord(raw) ? optionalNumber(raw.page ?? raw.number ?? raw.index) : undefined,
    pageSize: isRecord(raw) ? optionalNumber(raw.pageSize ?? raw.page_size ?? raw.size) : undefined,
    total: pageTotal(raw, items.length)
  };
}

export function normalizeAssetTestCaseStep(raw: unknown): AssetTestCaseStepView {
  const item = isRecord(raw) ? raw : {};
  return {
    stepOrder: numberValue(item.stepOrder ?? item.step_order ?? item.order ?? item.index, 0),
    action: optionalString(item.action),
    expectedResult: optionalString(item.expectedResult) ?? optionalString(item.expected_result)
  };
}

export function assetTestCaseStepItems(data: unknown): AssetTestCaseStepView[] {
  return listItems(data)
    .map(normalizeAssetTestCaseStep)
    .sort((left, right) => left.stepOrder - right.stepOrder);
}

export function normalizeAssetTestCaseView(raw: unknown): AssetTestCaseView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(
    item.id,
    stringValue(item.testCaseId, stringValue(item.test_case_id, stringValue(item.caseId, stringValue(item.case_id))))
  );
  return {
    id,
    code: optionalString(item.code),
    title: stringValue(item.title, stringValue(item.name, id || '未命名用例')),
    description: optionalString(item.description),
    requirementId: optionalString(item.requirementId) ?? optionalString(item.requirement_id),
    apiId: optionalString(item.apiId) ?? optionalString(item.api_id),
    source: optionalString(item.source),
    sourceRef: optionalString(item.sourceRef) ?? optionalString(item.source_ref),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    status: enumString(item.status, ASSET_TEST_CASE_STATUSES, 'DRAFT'),
    priority: enumString(item.priority, ASSET_REQUIREMENT_PRIORITIES, 'MEDIUM'),
    tags: stringArrayValue(item.tags ?? item.tagList ?? item.tag_list),
    steps: assetTestCaseStepItems(item.steps),
    version: numberValue(item.version, 0),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function assetTestCaseItems(data: unknown): AssetTestCaseView[] {
  return listItems(data).map(normalizeAssetTestCaseView);
}

export function normalizeAssetTestCaseList(raw: unknown): AssetTestCaseList {
  const items = assetTestCaseItems(raw);
  return {
    items,
    page: isRecord(raw) ? optionalNumber(raw.page ?? raw.number ?? raw.index) : undefined,
    pageSize: isRecord(raw) ? optionalNumber(raw.pageSize ?? raw.page_size ?? raw.size) : undefined,
    total: pageTotal(raw, items.length)
  };
}

export function normalizeTraceLinkView(raw: unknown): TraceLinkView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.linkId, stringValue(item.link_id)));
  return {
    id,
    requirementId: optionalString(item.requirementId) ?? optionalString(item.requirement_id),
    apiId: optionalString(item.apiId) ?? optionalString(item.api_id),
    pageId: optionalString(item.pageId) ?? optionalString(item.page_id),
    flowId: optionalString(item.flowId) ?? optionalString(item.flow_id),
    caseId: optionalString(item.caseId) ?? optionalString(item.case_id) ?? optionalString(item.testCaseId) ?? optionalString(item.test_case_id),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function traceLinkItems(data: unknown): TraceLinkView[] {
  return listItems(data).map(normalizeTraceLinkView);
}

export function normalizeTraceLinkList(raw: unknown): TraceLinkList {
  const items = traceLinkItems(raw);
  return {
    items,
    total: pageTotal(raw, items.length)
  };
}

export function normalizeAssetVersionHistoryView(raw: unknown): AssetVersionHistoryView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id, stringValue(item.historyId, stringValue(item.history_id)));
  return {
    id,
    assetType: stringValue(item.assetType, stringValue(item.asset_type, '')),
    assetId: stringValue(item.assetId, stringValue(item.asset_id, '')),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    version: numberValue(item.version, 0),
    changeType: stringValue(item.changeType, stringValue(item.change_type, 'UNKNOWN')),
    actor: optionalString(item.actor),
    changedFields: stringArrayValue(item.changedFields ?? item.changed_fields),
    diff: optionalJsonValue(item.diff ?? item.diffJson ?? item.diff_json),
    snapshot: optionalJsonValue(item.snapshot ?? item.snapshotJson ?? item.snapshot_json),
    traceId: optionalString(item.traceId) ?? optionalString(item.trace_id),
    createdAt: optionalString(item.createdAt) ?? optionalString(item.created_at)
  };
}

export function assetVersionHistoryItems(data: unknown): AssetVersionHistoryView[] {
  return listItems(data).map(normalizeAssetVersionHistoryView);
}

export function normalizeAssetImportItem(raw: unknown): AssetImportItemView {
  const item = isRecord(raw) ? raw : {};
  return {
    row: numberValue(item.row, 0),
    action: stringValue(item.action, 'UNKNOWN'),
    id: optionalString(item.id),
    code: optionalString(item.code),
    status: stringValue(item.status, 'UNKNOWN'),
    message: optionalString(item.message),
    errors: stringArrayValue(item.errors)
  };
}

export function normalizeAssetImportResult(raw: unknown): AssetImportResult {
  const item = isRecord(raw) ? raw : {};
  return {
    assetType: stringValue(item.assetType ?? item.asset_type, ''),
    format: stringValue(item.format, ''),
    dryRun: Boolean(item.dryRun ?? item.dry_run),
    totalRows: numberValue(item.totalRows ?? item.total_rows, 0),
    created: numberValue(item.created, 0),
    updated: numberValue(item.updated, 0),
    skipped: numberValue(item.skipped, 0),
    failed: numberValue(item.failed, 0),
    items: listItems(item.items).map(normalizeAssetImportItem)
  };
}

export function normalizeAssetImpactNode(raw: unknown): AssetImpactNodeView {
  const item = isRecord(raw) ? raw : {};
  const id = stringValue(item.id);
  return {
    assetType: stringValue(item.assetType ?? item.asset_type, ''),
    id,
    code: optionalString(item.code),
    title: stringValue(item.title, id || '-'),
    projectId: optionalString(item.projectId) ?? optionalString(item.project_id),
    status: optionalString(item.status),
    lifecycleStatus: optionalString(item.lifecycleStatus) ?? optionalString(item.lifecycle_status),
    updatedAt: optionalString(item.updatedAt) ?? optionalString(item.updated_at)
  };
}

export function normalizeAssetImpactAnalysis(raw: unknown): AssetImpactAnalysisView {
  const item = isRecord(raw) ? raw : {};
  return {
    projectId: stringValue(item.projectId ?? item.project_id, ''),
    subjectType: optionalString(item.subjectType) ?? optionalString(item.subject_type),
    subjectId: optionalString(item.subjectId) ?? optionalString(item.subject_id),
    requirementCount: numberValue(item.requirementCount ?? item.requirement_count, 0),
    apiCount: numberValue(item.apiCount ?? item.api_count, 0),
    pageCount: numberValue(item.pageCount ?? item.page_count, 0),
    flowCount: numberValue(item.flowCount ?? item.flow_count, 0),
    caseCount: numberValue(item.caseCount ?? item.case_count, 0),
    requirements: listItems(item.requirements).map(normalizeAssetImpactNode),
    apis: listItems(item.apis).map(normalizeAssetImpactNode),
    pages: listItems(item.pages).map(normalizeAssetImpactNode),
    flows: listItems(item.flows).map(normalizeAssetImpactNode),
    testCases: listItems(item.testCases ?? item.test_cases).map(normalizeAssetImpactNode),
    gaps: stringArrayValue(item.gaps),
    generatedAt: optionalString(item.generatedAt) ?? optionalString(item.generated_at)
  };
}

export function normalizeAssetPrototypeSyncResult(raw: unknown): AssetPrototypeSyncResult {
  const item = isRecord(raw) ? raw : {};
  return {
    source: stringValue(item.source, ''),
    dryRun: Boolean(item.dryRun ?? item.dry_run),
    totalRows: numberValue(item.totalRows ?? item.total_rows, 0),
    created: numberValue(item.created, 0),
    updated: numberValue(item.updated, 0),
    skipped: numberValue(item.skipped, 0),
    failed: numberValue(item.failed, 0),
    items: listItems(item.items).map(normalizeAssetImportItem)
  };
}

export async function fetchAssetHealth(): Promise<ApiResponse<AssetHealth>> {
  const response = await requestJson<unknown>('/api/v1/asset/health');
  return { ...response, data: normalizeAssetHealth(response.data) };
}

export async function fetchAssetRequirements(filters: AssetRequirementFilters = {}): Promise<ApiResponse<AssetRequirementList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/requirements${assetQuery(filters)}`);
  return { ...response, data: normalizeAssetRequirementList(response.data) };
}

export async function fetchAssetRequirement(requirementId: string): Promise<ApiResponse<AssetRequirementView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/requirements/${encodeURIComponent(requirementId)}`);
  return { ...response, data: normalizeAssetRequirementView(response.data) };
}

export async function fetchAssetRequirementVersions(requirementId: string): Promise<ApiResponse<AssetVersionHistoryView[]>> {
  const response = await requestJson<unknown>(`/api/v1/asset/requirements/${encodeURIComponent(requirementId)}/versions`);
  return { ...response, data: assetVersionHistoryItems(response.data) };
}

export async function rollbackAssetRequirementVersion(
  requirementId: string,
  version: number,
  reason?: string
): Promise<ApiResponse<AssetRequirementView>> {
  const response = await requestJson<unknown>(
    `/api/v1/asset/requirements/${encodeURIComponent(requirementId)}/versions/${version}/rollback`,
    {
      method: 'POST',
      body: JSON.stringify(compactAssetPayload({ reason }))
    }
  );
  return { ...response, data: normalizeAssetRequirementView(response.data) };
}

export async function createAssetRequirement(payload: AssetRequirementPayload): Promise<ApiResponse<AssetRequirementView>> {
  const response = await requestJson<unknown>('/api/v1/asset/requirements', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetRequirementView(response.data) };
}

export async function updateAssetRequirement(
  requirementId: string,
  payload: AssetRequirementPayload
): Promise<ApiResponse<AssetRequirementView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/requirements/${encodeURIComponent(requirementId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetRequirementView(response.data) };
}

export async function fetchAssetApis(filters: AssetApiFilters = {}): Promise<ApiResponse<AssetApiList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/apis${apiQuery(filters)}`);
  return { ...response, data: normalizeAssetApiList(response.data) };
}

export async function fetchAssetApi(apiId: string): Promise<ApiResponse<AssetApiView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/apis/${encodeURIComponent(apiId)}`);
  return { ...response, data: normalizeAssetApiView(response.data) };
}

export async function createAssetApi(payload: AssetApiPayload): Promise<ApiResponse<AssetApiView>> {
  const response = await requestJson<unknown>('/api/v1/asset/apis', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetApiView(response.data) };
}

export async function updateAssetApi(apiId: string, payload: AssetApiPayload): Promise<ApiResponse<AssetApiView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/apis/${encodeURIComponent(apiId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetApiView(response.data) };
}

export async function fetchAssetPages(filters: AssetPageFilters = {}): Promise<ApiResponse<AssetPageList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/pages${pageQuery(filters)}`);
  return { ...response, data: normalizeAssetPageList(response.data) };
}

export async function fetchAssetPage(pageId: string): Promise<ApiResponse<AssetPageView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/pages/${encodeURIComponent(pageId)}`);
  return { ...response, data: normalizeAssetPageView(response.data) };
}

export async function createAssetPage(payload: AssetPagePayload): Promise<ApiResponse<AssetPageView>> {
  const response = await requestJson<unknown>('/api/v1/asset/pages', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetPageView(response.data) };
}

export async function updateAssetPage(pageId: string, payload: AssetPagePayload): Promise<ApiResponse<AssetPageView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/pages/${encodeURIComponent(pageId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetPageView(response.data) };
}

export async function fetchAssetBusinessFlows(
  filters: AssetBusinessFlowFilters = {}
): Promise<ApiResponse<AssetBusinessFlowList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/business-flows${businessFlowQuery(filters)}`);
  return { ...response, data: normalizeAssetBusinessFlowList(response.data) };
}

export async function fetchAssetBusinessFlow(flowId: string): Promise<ApiResponse<AssetBusinessFlowView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/business-flows/${encodeURIComponent(flowId)}`);
  return { ...response, data: normalizeAssetBusinessFlowView(response.data) };
}

export async function createAssetBusinessFlow(
  payload: AssetBusinessFlowPayload
): Promise<ApiResponse<AssetBusinessFlowView>> {
  const response = await requestJson<unknown>('/api/v1/asset/business-flows', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetBusinessFlowView(response.data) };
}

export async function updateAssetBusinessFlow(
  flowId: string,
  payload: AssetBusinessFlowPayload
): Promise<ApiResponse<AssetBusinessFlowView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/business-flows/${encodeURIComponent(flowId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetBusinessFlowView(response.data) };
}

export async function fetchAssetTestCases(filters: AssetTestCaseFilters = {}): Promise<ApiResponse<AssetTestCaseList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/test-cases${testCaseQuery(filters)}`);
  return { ...response, data: normalizeAssetTestCaseList(response.data) };
}

export async function fetchAssetTestCase(testCaseId: string): Promise<ApiResponse<AssetTestCaseView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/test-cases/${encodeURIComponent(testCaseId)}`);
  return { ...response, data: normalizeAssetTestCaseView(response.data) };
}

export async function fetchAssetTestCaseVersions(testCaseId: string): Promise<ApiResponse<AssetVersionHistoryView[]>> {
  const response = await requestJson<unknown>(`/api/v1/asset/test-cases/${encodeURIComponent(testCaseId)}/versions`);
  return { ...response, data: assetVersionHistoryItems(response.data) };
}

export async function rollbackAssetTestCaseVersion(
  testCaseId: string,
  version: number,
  reason?: string
): Promise<ApiResponse<AssetTestCaseView>> {
  const response = await requestJson<unknown>(
    `/api/v1/asset/test-cases/${encodeURIComponent(testCaseId)}/versions/${version}/rollback`,
    {
      method: 'POST',
      body: JSON.stringify(compactAssetPayload({ reason }))
    }
  );
  return { ...response, data: normalizeAssetTestCaseView(response.data) };
}

export async function createAssetTestCase(payload: AssetTestCasePayload): Promise<ApiResponse<AssetTestCaseView>> {
  const response = await requestJson<unknown>('/api/v1/asset/test-cases', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetTestCaseView(response.data) };
}

export async function updateAssetTestCase(
  testCaseId: string,
  payload: AssetTestCasePayload
): Promise<ApiResponse<AssetTestCaseView>> {
  const response = await requestJson<unknown>(`/api/v1/asset/test-cases/${encodeURIComponent(testCaseId)}`, {
    method: 'PUT',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetTestCaseView(response.data) };
}

export async function fetchAssetTestCaseSteps(testCaseId: string): Promise<ApiResponse<AssetTestCaseStepView[]>> {
  const response = await requestJson<unknown>(`/api/v1/asset/test-cases/${encodeURIComponent(testCaseId)}/steps`);
  return { ...response, data: assetTestCaseStepItems(response.data) };
}

export async function updateAssetTestCaseSteps(
  testCaseId: string,
  payload: AssetTestCaseStepsPayload
): Promise<ApiResponse<AssetTestCaseStepView[]>> {
  const response = await requestJson<unknown>(`/api/v1/asset/test-cases/${encodeURIComponent(testCaseId)}/steps`, {
    method: 'PUT',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: assetTestCaseStepItems(response.data) };
}

export async function fetchAssetTraceLinks(filters: TraceLinkFilters = {}): Promise<ApiResponse<TraceLinkList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/links${traceLinkQuery(filters)}`);
  return { ...response, data: normalizeTraceLinkList(response.data) };
}

export async function fetchRequirementTraceLinks(requirementId: string): Promise<ApiResponse<TraceLinkList>> {
  return fetchAssetTraceLinks({ requirementId });
}

export async function importAssets(payload: AssetImportPayload): Promise<ApiResponse<AssetImportResult>> {
  const response = await requestJson<unknown>('/api/v1/asset/imports', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetImportResult(response.data) };
}

export function assetExportPath(filters: AssetExportFilters) {
  return `/api/v1/asset/exports${assetExportQuery(filters)}`;
}

export async function exportAssetsText(filters: AssetExportFilters): Promise<TextResponse> {
  return requestText(assetExportPath(filters));
}

export async function fetchAssetImpactAnalysis(filters: {
  projectId: string;
  assetType?: string;
  assetId?: string;
}): Promise<ApiResponse<AssetImpactAnalysisView>> {
  const params = new URLSearchParams();
  params.set('projectId', filters.projectId.trim());
  if (filters.assetType?.trim()) params.set('assetType', filters.assetType.trim());
  if (filters.assetId?.trim()) params.set('assetId', filters.assetId.trim());
  const response = await requestJson<unknown>(`/api/v1/asset/impact?${params.toString()}`);
  return { ...response, data: normalizeAssetImpactAnalysis(response.data) };
}

export async function syncPrototypePages(payload: AssetPrototypeSyncPayload): Promise<ApiResponse<AssetPrototypeSyncResult>> {
  const response = await requestJson<unknown>('/api/v1/asset/prototype-sync', {
    method: 'POST',
    body: JSON.stringify(compactAssetPayload(payload))
  });
  return { ...response, data: normalizeAssetPrototypeSyncResult(response.data) };
}
