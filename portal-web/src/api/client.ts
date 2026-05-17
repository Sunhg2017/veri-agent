export interface ApiResponse<T> {
  code: string;
  message: string;
  trace_id: string;
  data: T;
}

export interface FieldErrorItem {
  field: string;
  reason: string;
}

export interface ApiErrorDetail {
  field_errors?: FieldErrorItem[];
}

export class ApiError extends Error {
  readonly code: string;
  readonly traceId: string;
  readonly status: number;
  readonly detail?: ApiErrorDetail;

  constructor(message: string, code: string, traceId: string, status: number, detail?: ApiErrorDetail) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.traceId = traceId;
    this.status = status;
    this.detail = detail;
  }
}

const TOKEN_STORAGE_KEY = 'veri-agent.access-token';
const REFRESH_TOKEN_STORAGE_KEY = 'veri-agent.refresh-token';
const SESSION_ID_STORAGE_KEY = 'veri-agent.session-id';

export function getAuthToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setAuthToken(token: string) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function setRefreshToken(token: string) {
  window.localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, token);
}

export function getRefreshToken() {
  return window.localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
}

export function setSessionId(sessionId: string) {
  window.localStorage.setItem(SESSION_ID_STORAGE_KEY, sessionId);
}

export function clearAuthToken() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  window.localStorage.removeItem(SESSION_ID_STORAGE_KEY);
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
  const token = getAuthToken();
  const headers = new Headers(init?.headers);
  headers.set('Content-Type', 'application/json');
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...init,
    headers
  });
  const body = (await response.json()) as ApiResponse<T | ApiErrorDetail>;
  if (!response.ok || body.code !== 'OK') {
    throw new ApiError(
      body.message || '请求失败',
      body.code || `HTTP_${response.status}`,
      body.trace_id || response.headers.get('X-Trace-Id') || '',
      response.status,
      body.data as ApiErrorDetail
    );
  }
  return body as ApiResponse<T>;
}
