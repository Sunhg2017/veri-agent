import { requestJson, type ApiResponse } from './client';

const API_AUTOMATION_BASE = '/api/v1/api-automation';

export interface ApiAutomationHealth {
  service: string;
  status: string;
  supportedOpenApiVersions: string[];
  specMaxBytes: number;
  endpointMaxCount: number;
  runnerEnabled: boolean;
  runnerTimeoutSeconds: number;
  runnerMaxCases: number;
  promptKey?: string;
  modelFallbackEnabled: boolean;
  policy: Record<string, unknown>;
}

export interface ApiAutomationSpec {
  id: string;
  projectId: string;
  sourceType: string;
  sourceRef?: string;
  name: string;
  versionLabel?: string;
  specDigest?: string;
  contentSizeBytes: number;
  status: string;
  parserVersion?: string;
  endpointCount: number;
  parseErrorSummary?: string;
  parsedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiAutomationEndpointSnapshot {
  id: string;
  serviceName?: string;
  operationId?: string;
  httpMethod: string;
  path: string;
  summary?: string;
  tags?: string;
  parameterCount: number;
  requestBodyPresent: boolean;
  responseStatuses?: string;
  schemaDigest?: string;
  diffStatus: string;
}

export interface ApiAutomationSpecDetail {
  spec: ApiAutomationSpec;
  parseSummary: Record<string, unknown>;
  endpoints: ApiAutomationEndpointSnapshot[];
}

export interface ApiAutomationSpecList {
  items: ApiAutomationSpec[];
  index: number;
  size: number;
  total: number;
}

export interface ApiAutomationSpecPayload {
  projectId: string;
  sourceType: 'TEXT' | 'UPLOAD' | 'URL';
  name: string;
  versionLabel?: string;
  sourceRef?: string;
  content: string;
}

export interface ApiAutomationSpecFilters {
  projectId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export async function fetchApiAutomationHealth(): Promise<ApiResponse<ApiAutomationHealth>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/health`);
  return { ...response, data: normalizeApiAutomationHealth(response.data) };
}

export async function createApiAutomationSpec(
  payload: ApiAutomationSpecPayload
): Promise<ApiResponse<ApiAutomationSpecDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeApiAutomationSpecDetail(response.data) };
}

export async function fetchApiAutomationSpecs(
  filters: ApiAutomationSpecFilters = {}
): Promise<ApiResponse<ApiAutomationSpecList>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs${queryString(filters)}`);
  return { ...response, data: normalizeApiAutomationSpecList(response.data) };
}

export async function fetchApiAutomationSpec(id: string): Promise<ApiResponse<ApiAutomationSpecDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeApiAutomationSpecDetail(response.data) };
}

export async function parseApiAutomationSpec(id: string): Promise<ApiResponse<ApiAutomationSpecDetail>> {
  const response = await requestJson<unknown>(`${API_AUTOMATION_BASE}/specs/${encodeURIComponent(id)}/parse`, {
    method: 'POST'
  });
  return { ...response, data: normalizeApiAutomationSpecDetail(response.data) };
}

export function normalizeApiAutomationHealth(input: unknown): ApiAutomationHealth {
  const value = objectValue(input);
  return {
    service: stringValue(read(value, 'service'), 'api-automation'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    supportedOpenApiVersions: stringArray(read(value, 'supportedOpenApiVersions', 'supported_open_api_versions')),
    specMaxBytes: numberValue(read(value, 'specMaxBytes', 'spec_max_bytes'), 0),
    endpointMaxCount: numberValue(read(value, 'endpointMaxCount', 'endpoint_max_count'), 0),
    runnerEnabled: booleanValue(read(value, 'runnerEnabled', 'runner_enabled'), false),
    runnerTimeoutSeconds: numberValue(read(value, 'runnerTimeoutSeconds', 'runner_timeout_seconds'), 0),
    runnerMaxCases: numberValue(read(value, 'runnerMaxCases', 'runner_max_cases'), 0),
    promptKey: optionalString(read(value, 'promptKey', 'prompt_key')),
    modelFallbackEnabled: booleanValue(read(value, 'modelFallbackEnabled', 'model_fallback_enabled'), false),
    policy: objectValue(read(value, 'policy'))
  };
}

export function normalizeApiAutomationSpecList(input: unknown): ApiAutomationSpecList {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeApiAutomationSpec),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

export function normalizeApiAutomationSpecDetail(input: unknown): ApiAutomationSpecDetail {
  const value = objectValue(input);
  return {
    spec: normalizeApiAutomationSpec(read(value, 'spec')),
    parseSummary: objectValue(read(value, 'parseSummary', 'parse_summary')),
    endpoints: arrayValue(read(value, 'endpoints')).map(normalizeApiAutomationEndpointSnapshot)
  };
}

export function normalizeApiAutomationSpec(input: unknown): ApiAutomationSpec {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    sourceType: stringValue(read(value, 'sourceType', 'source_type'), 'TEXT'),
    sourceRef: optionalString(read(value, 'sourceRef', 'source_ref')),
    name: stringValue(read(value, 'name')),
    versionLabel: optionalString(read(value, 'versionLabel', 'version_label')),
    specDigest: optionalString(read(value, 'specDigest', 'spec_digest')),
    contentSizeBytes: numberValue(read(value, 'contentSizeBytes', 'content_size_bytes'), 0),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    parserVersion: optionalString(read(value, 'parserVersion', 'parser_version')),
    endpointCount: numberValue(read(value, 'endpointCount', 'endpoint_count'), 0),
    parseErrorSummary: optionalString(read(value, 'parseErrorSummary', 'parse_error_summary')),
    parsedAt: optionalString(read(value, 'parsedAt', 'parsed_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeApiAutomationEndpointSnapshot(input: unknown): ApiAutomationEndpointSnapshot {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    serviceName: optionalString(read(value, 'serviceName', 'service_name')),
    operationId: optionalString(read(value, 'operationId', 'operation_id')),
    httpMethod: stringValue(read(value, 'httpMethod', 'http_method'), 'GET'),
    path: stringValue(read(value, 'path')),
    summary: optionalString(read(value, 'summary')),
    tags: optionalString(read(value, 'tags')),
    parameterCount: numberValue(read(value, 'parameterCount', 'parameter_count'), 0),
    requestBodyPresent: booleanValue(read(value, 'requestBodyPresent', 'request_body_present'), false),
    responseStatuses: optionalString(read(value, 'responseStatuses', 'response_statuses')),
    schemaDigest: optionalString(read(value, 'schemaDigest', 'schema_digest')),
    diffStatus: stringValue(read(value, 'diffStatus', 'diff_status'), 'UNKNOWN')
  };
}

function queryString(filters: ApiAutomationSpecFilters) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

function objectValue(input: unknown): Record<string, unknown> {
  return input && typeof input === 'object' && !Array.isArray(input) ? input as Record<string, unknown> : {};
}

function arrayValue(input: unknown): unknown[] {
  return Array.isArray(input) ? input : [];
}

function read(value: Record<string, unknown>, primary: string, fallback?: string) {
  return value[primary] ?? (fallback ? value[fallback] : undefined);
}

function stringValue(input: unknown, fallback = '') {
  return typeof input === 'string' ? input : input == null ? fallback : String(input);
}

function optionalString(input: unknown) {
  const value = stringValue(input).trim();
  return value ? value : undefined;
}

function numberValue(input: unknown, fallback: number) {
  const value = typeof input === 'number' ? input : Number(input);
  return Number.isFinite(value) ? value : fallback;
}

function booleanValue(input: unknown, fallback: boolean) {
  if (typeof input === 'boolean') return input;
  if (input === 'true') return true;
  if (input === 'false') return false;
  return fallback;
}

function stringArray(input: unknown) {
  return Array.isArray(input) ? input.map((item) => stringValue(item)).filter(Boolean) : [];
}
