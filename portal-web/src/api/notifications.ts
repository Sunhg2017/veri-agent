import { refreshToken } from './auth';
import { ApiError, getAuthToken, requestJson, type ApiResponse } from './client';

const NOTIFICATIONS_BASE = '/api/v1/notifications';

export interface UserNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  link?: string;
  metadata: Record<string, unknown>;
  unread: boolean;
  readAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface NotificationList {
  items: UserNotification[];
  index: number;
  size: number;
  total: number;
}

export interface NotificationUnreadCount {
  unreadCount: number;
}

export interface NotificationBatchReadResult {
  markedCount: number;
  unreadCount: number;
}

export type NotificationStreamEvent =
  | { type: 'connected'; unreadCount: number; timestamp?: string }
  | { type: 'heartbeat'; timestamp?: string }
  | { type: 'unread-count'; unreadCount: number }
  | { type: 'notification-created'; notification: UserNotification; unreadCount: number }
  | { type: 'notification-read'; notification: UserNotification; unreadCount: number }
  | { type: 'notification-read-all'; readAt?: string; unreadCount: number };

export interface NotificationFilters {
  status?: 'UNREAD' | 'READ';
  index?: number;
  size?: number;
}

type RawNotification = Partial<UserNotification> & {
  read_at?: string;
  readAt?: string;
  created_at?: string;
  createdAt?: string;
  updated_at?: string;
  updatedAt?: string;
};

type RawNotificationList = Partial<NotificationList> & {
  items?: unknown[];
};

type RawNotificationUnreadCount = Partial<NotificationUnreadCount> & {
  unread_count?: number | string;
};

type RawNotificationBatchReadResult = Partial<NotificationBatchReadResult> & {
  marked_count?: number | string;
  unread_count?: number | string;
};

type RawNotificationStreamEvent = {
  type?: string;
  unreadCount?: number | string;
  unread_count?: number | string;
  timestamp?: string;
  readAt?: string;
  read_at?: string;
  notification?: RawNotification;
};

function asNumber(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

export function normalizeNotification(raw: RawNotification): UserNotification {
  return {
    id: String(raw.id ?? ''),
    type: String(raw.type ?? ''),
    title: String(raw.title ?? ''),
    body: String(raw.body ?? ''),
    link: raw.link ? String(raw.link) : undefined,
    metadata: raw.metadata && typeof raw.metadata === 'object' ? raw.metadata as Record<string, unknown> : {},
    unread: Boolean(raw.unread),
    readAt: raw.read_at ?? raw.readAt,
    createdAt: raw.created_at ?? raw.createdAt,
    updatedAt: raw.updated_at ?? raw.updatedAt
  };
}

export function normalizeNotificationList(raw: unknown): NotificationList {
  const value = (raw ?? {}) as RawNotificationList;
  const items = Array.isArray(value.items) ? value.items.map((item) => normalizeNotification(item as RawNotification)) : [];
  return {
    items,
    index: asNumber(value.index, 0),
    size: asNumber(value.size, items.length || 20),
    total: asNumber(value.total, items.length)
  };
}

export function normalizeUnreadCount(raw: unknown): NotificationUnreadCount {
  const value = (raw ?? {}) as RawNotificationUnreadCount;
  return {
    unreadCount: asNumber(value.unreadCount ?? value.unread_count, 0)
  };
}

export function normalizeBatchReadResult(raw: unknown): NotificationBatchReadResult {
  const value = (raw ?? {}) as RawNotificationBatchReadResult;
  return {
    markedCount: asNumber(value.markedCount ?? value.marked_count, 0),
    unreadCount: asNumber(value.unreadCount ?? value.unread_count, 0)
  };
}

function queryString(filters: NotificationFilters) {
  const params = new URLSearchParams();
  if (filters.status) params.set('status', filters.status);
  if (typeof filters.index === 'number') params.set('index', String(filters.index));
  if (typeof filters.size === 'number') params.set('size', String(filters.size));
  const query = params.toString();
  return query ? `?${query}` : '';
}

export async function fetchNotifications(filters: NotificationFilters = {}): Promise<ApiResponse<NotificationList>> {
  const response = await requestJson<unknown>(`${NOTIFICATIONS_BASE}${queryString(filters)}`);
  return { ...response, data: normalizeNotificationList(response.data) };
}

export async function fetchUnreadNotificationCount(): Promise<ApiResponse<NotificationUnreadCount>> {
  const response = await requestJson<unknown>(`${NOTIFICATIONS_BASE}/unread-count`);
  return { ...response, data: normalizeUnreadCount(response.data) };
}

export async function markNotificationRead(id: string): Promise<ApiResponse<UserNotification>> {
  const response = await requestJson<unknown>(`${NOTIFICATIONS_BASE}/${encodeURIComponent(id)}/read`, {
    method: 'POST'
  });
  return { ...response, data: normalizeNotification(response.data as RawNotification) };
}

export async function markAllNotificationsRead(): Promise<ApiResponse<NotificationBatchReadResult>> {
  const response = await requestJson<unknown>(`${NOTIFICATIONS_BASE}/read-all`, {
    method: 'POST'
  });
  return { ...response, data: normalizeBatchReadResult(response.data) };
}

export function parseNotificationStreamEvents(text: string): NotificationStreamEvent[] {
  return text
    .split(/\r?\n\r?\n/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map(parseNotificationStreamEvent)
    .filter((event): event is NotificationStreamEvent => event !== undefined);
}

export async function subscribeNotificationStream(
  onEvent: (event: NotificationStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetchNotificationStream(signal, true);
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType && !contentType.toLowerCase().includes('text/event-stream')) {
    throw new ApiError(
      '通知流返回类型异常',
      'INVALID_STREAM_RESPONSE',
      response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  }

  const pushEvents = (chunk: string) => {
    parseNotificationStreamEvents(chunk).forEach(onEvent);
  };

  if (!response.body) {
    pushEvents(await response.text());
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (signal?.aborted) {
      return;
    }
    buffer += decoder.decode(value, { stream: !done });
    const parts = buffer.split(/\r?\n\r?\n/);
    buffer = parts.pop() ?? '';
    parts.filter(Boolean).forEach(pushEvents);
    if (done) {
      break;
    }
  }
  if (buffer.trim()) {
    pushEvents(buffer);
  }
}

function parseNotificationStreamEvent(block: string): NotificationStreamEvent | undefined {
  let type = '';
  const dataLines: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      type = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart());
    }
  }
  if (!type || dataLines.length === 0) {
    return undefined;
  }
  let data: RawNotificationStreamEvent;
  try {
    data = record(JSON.parse(dataLines.join('\n')));
  } catch {
    return undefined;
  }
  const unreadCount = asNumber(data.unreadCount ?? data.unread_count, 0);
  if (type === 'connected') {
    return { type, unreadCount, timestamp: optionalString(data.timestamp) };
  }
  if (type === 'heartbeat') {
    return { type, timestamp: optionalString(data.timestamp) };
  }
  if (type === 'unread-count') {
    return { type, unreadCount };
  }
  if (type === 'notification-created' || type === 'notification-read') {
    if (!data.notification) {
      return undefined;
    }
    return {
      type,
      notification: normalizeNotification(data.notification),
      unreadCount
    };
  }
  if (type === 'notification-read-all') {
    return {
      type,
      readAt: optionalString(data.readAt ?? data.read_at),
      unreadCount
    };
  }
  return undefined;
}

async function fetchNotificationStream(signal?: AbortSignal, allowRefresh = true): Promise<Response> {
  const token = getAuthToken();
  const headers = new Headers({ Accept: 'text/event-stream' });
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(`${NOTIFICATIONS_BASE}/stream`, {
    method: 'GET',
    headers,
    signal
  });
  if (response.status === 401 && token && allowRefresh) {
    const refreshed = await refreshToken();
    if (refreshed) {
      return fetchNotificationStream(signal, false);
    }
    throw new ApiError('登录已过期，请重新登录', 'SESSION_EXPIRED', '', 401);
  }
  if (!response.ok) {
    throw await streamApiError(response, '通知流连接失败');
  }
  return response;
}

async function streamApiError(response: Response, fallbackMessage: string) {
  const text = await response.text().catch(() => '');
  try {
    const body = JSON.parse(text) as { message?: string; code?: string; trace_id?: string; traceId?: string };
    return new ApiError(
      body.message || fallbackMessage,
      body.code || `HTTP_${response.status}`,
      body.trace_id ?? body.traceId ?? response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  } catch {
    return new ApiError(
      text || fallbackMessage,
      `HTTP_${response.status}`,
      response.headers.get('X-Trace-Id') ?? '',
      response.status
    );
  }
}

function record(value: unknown): RawNotificationStreamEvent {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as RawNotificationStreamEvent : {};
}

function optionalString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}
