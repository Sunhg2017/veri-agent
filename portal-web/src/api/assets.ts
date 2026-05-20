import { requestJson, type ApiResponse } from './client';

export const ASSET_REQUIREMENT_STATUSES = ['DRAFT', 'REVIEWING', 'APPROVED', 'DEPRECATED'] as const;
export const ASSET_REQUIREMENT_PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
export const ASSET_REQUIREMENT_SOURCES = ['MANUAL', 'IMPORT'] as const;
export const ASSET_API_STATUSES = ['ACTIVE', 'DEPRECATED', 'REMOVED'] as const;
export const ASSET_API_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const;

export type AssetRequirementStatus = (typeof ASSET_REQUIREMENT_STATUSES)[number];
export type AssetRequirementPriority = (typeof ASSET_REQUIREMENT_PRIORITIES)[number];
export type AssetRequirementSource = (typeof ASSET_REQUIREMENT_SOURCES)[number];
export type AssetApiStatus = (typeof ASSET_API_STATUSES)[number];
export type AssetApiMethod = (typeof ASSET_API_METHODS)[number];

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
        const normalized = value.map((item) => String(item).trim()).filter(Boolean).join(',');
        return normalized ? [[key, normalized]] : [];
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

export async function fetchRequirementTraceLinks(requirementId: string): Promise<ApiResponse<TraceLinkList>> {
  const response = await requestJson<unknown>(`/api/v1/asset/links?requirementId=${encodeURIComponent(requirementId)}`);
  return { ...response, data: normalizeTraceLinkList(response.data) };
}
