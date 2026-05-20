import { requestJson, type ApiResponse } from './client';

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
  caseId?: string;
  createdAt?: string;
}

export interface TraceLinkList {
  items: TraceLinkView[];
  total: number;
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

export async function fetchRequirementTraceLinks(requirementId: string): Promise<ApiResponse<TraceLinkList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/links?requirementId=${encodeURIComponent(requirementId)}`);
  return { ...response, data: normalizeTraceLinkList(response.data) };
}
