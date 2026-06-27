import { translate } from '../platform/i18n';
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

export interface TextResponse {
  text: string;
  traceId: string;
  contentType: string;
  filename?: string;
}

export interface BinaryResponse {
  blob: Blob;
  traceId: string;
  contentType: string;
  filename?: string;
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
  return request<T>(path, init, true);
}

export async function requestMultipart<T>(path: string, formData: FormData, init?: RequestInit): Promise<ApiResponse<T>> {
  return request<T>(path, {
    ...init,
    method: init?.method ?? 'POST',
    body: formData
  }, false);
}

export async function requestText(path: string, init?: RequestInit): Promise<TextResponse> {
  const token = getAuthToken();
  const headers = new Headers(init?.headers);
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...init,
    headers
  });

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
        return textResponse(retryResponse);
      }
    }
    throw new ApiError(translate('auto.k0048'), 'SESSION_EXPIRED', '', 401);
  }

  return textResponse(response);
}

export async function requestBinary(path: string, init?: RequestInit): Promise<BinaryResponse> {
  const token = getAuthToken();
  const headers = new Headers(init?.headers);
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...init,
    headers
  });

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
        return binaryResponse(retryResponse);
      }
    }
    throw new ApiError(translate('auto.k0048'), 'SESSION_EXPIRED', '', 401);
  }

  return binaryResponse(response);
}

async function request<T>(path: string, init: RequestInit | undefined, jsonBody: boolean): Promise<ApiResponse<T>> {
  const token = getAuthToken();
  const headers = new Headers(init?.headers);
  if (jsonBody) {
    headers.set('Content-Type', 'application/json');
  }
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
          retryBody.message || translate('auto.k0101'),
          retryBody.code || `HTTP_${retryResponse.status}`,
          retryTraceId,
          retryResponse.status,
          retryBody.data as ApiErrorDetail
        );
      }
    }
    // If refresh failed, throw a clear "session expired" error
    throw new ApiError(translate('auto.k0048'), 'SESSION_EXPIRED', '', 401);
  }

  const body = (await response.json()) as ApiEnvelope<T | ApiErrorDetail>;
  const traceId = body.trace_id ?? body.traceId ?? response.headers.get('X-Trace-Id') ?? '';
  if (!response.ok || body.code !== 'OK') {
    throw new ApiError(
      body.message || translate('auto.k0101'),
      body.code || `HTTP_${response.status}`,
      traceId,
      response.status,
      body.data as ApiErrorDetail
    );
  }
  return { ...body, trace_id: traceId } as ApiResponse<T>;
}

async function textResponse(response: Response): Promise<TextResponse> {
  const traceId = response.headers.get('X-Trace-Id') ?? '';
  const contentType = response.headers.get('Content-Type') ?? '';
  const contentDisposition = response.headers.get('Content-Disposition') ?? '';
  const text = await response.text();

  if (!response.ok) {
    throw errorFromTextResponse(text, response.status, traceId);
  }

  return {
    text,
    traceId,
    contentType,
    filename: filenameFromContentDisposition(contentDisposition)
  };
}

async function binaryResponse(response: Response): Promise<BinaryResponse> {
  const traceId = response.headers.get('X-Trace-Id') ?? '';
  const contentType = response.headers.get('Content-Type') ?? '';
  const contentDisposition = response.headers.get('Content-Disposition') ?? '';

  if (!response.ok) {
    const text = await response.text();
    throw errorFromTextResponse(text, response.status, traceId);
  }

  return {
    blob: await response.blob(),
    traceId,
    contentType,
    filename: filenameFromContentDisposition(contentDisposition)
  };
}

function errorFromTextResponse(text: string, status: number, traceId: string) {
  try {
    const body = JSON.parse(text) as ApiEnvelope<ApiErrorDetail>;
    const bodyTraceId = body.trace_id ?? body.traceId ?? traceId;
    return new ApiError(
      body.message || translate('auto.k0101'),
      body.code || `HTTP_${status}`,
      bodyTraceId,
      status,
      body.data
    );
  } catch {
    return new ApiError(text || translate('auto.k0101'), `HTTP_${status}`, traceId, status);
  }
}

function filenameFromContentDisposition(value: string) {
  const encoded = value.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    try {
      return decodeURIComponent(encoded);
    } catch {
      return encoded;
    }
  }
  return value.match(/filename="?([^";]+)"?/i)?.[1];
}
