import { translate } from '../platform/i18n';
export type ApiResult<T> =
  | {
      ok: true;
      status: number;
      data: T;
      traceId?: string;
    }
  | {
      ok: false;
      status: number;
      message: string;
      data?: unknown;
      traceId?: string;
    };

export interface HealthResponse {
  status?: string;
  trace_id?: string;
  [key: string]: unknown;
}

function getTraceId(headers: Headers, body: unknown): string | undefined {
  const bodyTraceId =
    body && typeof body === 'object' && 'trace_id' in body
      ? String((body as { trace_id?: unknown }).trace_id ?? '')
      : '';

  return (
    headers.get('x-trace-id') ??
    headers.get('trace-id') ??
    (bodyTraceId.length > 0 ? bodyTraceId : undefined)
  );
}

function getErrorMessage(body: unknown, fallback: string): string {
  if (!body || typeof body !== 'object') {
    return fallback;
  }

  const candidate =
    'message' in body
      ? (body as { message?: unknown }).message
      : 'error' in body
        ? (body as { error?: unknown }).error
        : undefined;

  return typeof candidate === 'string' && candidate.length > 0 ? candidate : fallback;
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return {};
  }

  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

export async function requestJson<T>(
  path: string,
  init?: RequestInit,
  fallbackError = translate('auto.k2007')
): Promise<ApiResult<T>> {
  try {
    const response = await fetch(path, {
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {})
      },
      ...init
    });
    const body = await readBody(response);
    const traceId = getTraceId(response.headers, body);

    if (!response.ok) {
      return {
        ok: false,
        status: response.status,
        message: getErrorMessage(body, fallbackError),
        data: body,
        traceId
      };
    }

    return {
      ok: true,
      status: response.status,
      data: body as T,
      traceId
    };
  } catch (error) {
    return {
      ok: false,
      status: 0,
      message: error instanceof Error ? error.message : fallbackError
    };
  }
}

export function fetchHealth(): Promise<ApiResult<HealthResponse>> {
  return requestJson<HealthResponse>('/api/v1/health', undefined, translate('auto.k2008'));
}
