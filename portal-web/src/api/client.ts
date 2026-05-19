export interface ApiResponse<T> {
  code: string;
  message: string;
  trace_id: string;
  traceId?: string;
  data: T;
}

export interface FieldErrorItem {
  field: string;
  reason: string;
}

export interface ApiErrorDetail {
  field_errors?: FieldErrorItem[];
}

type ApiEnvelope<T> = Omit<ApiResponse<T>, 'trace_id'> & {
  trace_id?: string;
  traceId?: string;
};

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
const REFRESH_RUNNING_KEY = 'veri-agent.refresh-running';

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

export function getSessionId() {
  return window.localStorage.getItem(SESSION_ID_STORAGE_KEY);
}

export function setSessionId(sessionId: string) {
  window.localStorage.setItem(SESSION_ID_STORAGE_KEY, sessionId);
}

export function clearAuthToken() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  window.localStorage.removeItem(SESSION_ID_STORAGE_KEY);
}

/**
 * Attempt a token refresh.
 * Uses a localStorage flag to prevent concurrent refresh calls from
 * multiple requests that got 401 at the same time.
 */
let refreshPromise: Promise<boolean> | null = null;

async function attemptRefresh(): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise;
  }

  // Prevent concurrent refresh attempts
  if (window.localStorage.getItem(REFRESH_RUNNING_KEY)) {
    // Wait briefly for the other in-flight refresh to finish
    await new Promise((resolve) => setTimeout(resolve, 200));
    return getAuthToken() !== null;
  }

  window.localStorage.setItem(REFRESH_RUNNING_KEY, '1');
  refreshPromise = (async () => {
    try {
      const { refreshToken } = await import('./auth');
      const ok = await refreshToken();
      if (!ok) {
        clearAuthToken();
      }
      return ok;
    } catch {
      clearAuthToken();
      return false;
    } finally {
      window.localStorage.removeItem(REFRESH_RUNNING_KEY);
      refreshPromise = null;
    }
  })();

  return refreshPromise;
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

  // On 401, attempt token refresh once and retry
  if (response.status === 401 && token) {
    const refreshed = await attemptRefresh();
    if (refreshed) {
      const newToken = getAuthToken();
      if (newToken) {
        headers.set('Authorization', `Bearer ${newToken}`);
        const retryResponse = await fetch(path, {
          ...init,
          headers
        });
        const retryBody = (await retryResponse.json()) as ApiEnvelope<T | ApiErrorDetail>;
        const retryTraceId = retryBody.trace_id ?? retryBody.traceId ?? retryResponse.headers.get('X-Trace-Id') ?? '';
        if (retryResponse.ok && retryBody.code === 'OK') {
          return { ...retryBody, trace_id: retryTraceId } as ApiResponse<T>;
        }
        throw new ApiError(
          retryBody.message || '请求失败',
          retryBody.code || `HTTP_${retryResponse.status}`,
          retryTraceId,
          retryResponse.status,
          retryBody.data as ApiErrorDetail
        );
      }
    }
    // If refresh failed, throw a clear "session expired" error
    throw new ApiError('登录已过期，请重新登录', 'SESSION_EXPIRED', '', 401);
  }

  const body = (await response.json()) as ApiEnvelope<T | ApiErrorDetail>;
  const traceId = body.trace_id ?? body.traceId ?? response.headers.get('X-Trace-Id') ?? '';
  if (!response.ok || body.code !== 'OK') {
    throw new ApiError(
      body.message || '请求失败',
      body.code || `HTTP_${response.status}`,
      traceId,
      response.status,
      body.data as ApiErrorDetail
    );
  }
  return { ...body, trace_id: traceId } as ApiResponse<T>;
}
