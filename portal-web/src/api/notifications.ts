import { requestJson, type ApiResponse } from './client';

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
