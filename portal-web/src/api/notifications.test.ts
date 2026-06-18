import { beforeEach, describe, expect, it, vi } from 'vitest';
import { refreshToken } from './auth';
import { ApiError, getAuthToken, requestJson } from './client';
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  normalizeBatchReadResult,
  normalizeNotification,
  normalizeNotificationList,
  normalizeUnreadCount,
  parseNotificationStreamEvents,
  subscribeNotificationStream
} from './notifications';

vi.mock('./auth', () => ({
  refreshToken: vi.fn()
}));

vi.mock('./client', () => ({
  ApiError: class ApiError extends Error {
    readonly code: string;
    readonly traceId: string;
    readonly status: number;

    constructor(message: string, code: string, traceId: string, status: number) {
      super(message);
      this.name = 'ApiError';
      this.code = code;
      this.traceId = traceId;
      this.status = status;
    }
  },
  getAuthToken: vi.fn(),
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const getAuthTokenMock = vi.mocked(getAuthToken);
const refreshTokenMock = vi.mocked(refreshToken);

describe('notification API helpers', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    requestJsonMock.mockReset();
    getAuthTokenMock.mockReset();
    refreshTokenMock.mockReset();
  });

  it('normalizes notification payloads and counters', () => {
    expect(normalizeNotification({
      id: 'notice-1',
      type: 'REPORT_READY',
      title: '报告已生成',
      body: '请查看最新快照',
      link: '#reports',
      metadata: { reportId: 'report-1' },
      unread: true,
      created_at: '2026-06-18T10:00:00Z'
    })).toMatchObject({
      id: 'notice-1',
      type: 'REPORT_READY',
      unread: true,
      createdAt: '2026-06-18T10:00:00Z'
    });

    expect(normalizeNotificationList({
      items: [{
        id: 'notice-1',
        type: 'SYSTEM_INFO',
        title: '平台消息',
        body: 'hello',
        unread: false
      }],
      total: '1',
      size: '10'
    })).toMatchObject({
      total: 1,
      size: 10,
      items: [{ type: 'SYSTEM_INFO', unread: false }]
    });

    expect(normalizeUnreadCount({ unread_count: '3' })).toEqual({ unreadCount: 3 });
    expect(normalizeBatchReadResult({ marked_count: '2', unreadCount: 1 })).toEqual({
      markedCount: 2,
      unreadCount: 1
    });
  });

  it('calls notification endpoints with expected paths', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: {} });

    await fetchNotifications({ status: 'UNREAD', size: 8 });
    await fetchUnreadNotificationCount();
    await markNotificationRead('notice-1');
    await markAllNotificationsRead();

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/notifications?status=UNREAD&size=8');
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, '/api/v1/notifications/unread-count');
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/notifications/notice-1/read', {
      method: 'POST'
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/notifications/read-all', {
      method: 'POST'
    });
  });

  it('parses notification SSE events', () => {
    expect(parseNotificationStreamEvents(`
event: connected
data: {"timestamp":"2026-06-18T10:00:00Z","unreadCount":2}

event: notification-created
data: {"notification":{"id":"notice-1","type":"REPORT_READY","title":"报告已生成","body":"请查看","unread":true},"unreadCount":2}

event: notification-read-all
data: {"readAt":"2026-06-18T10:01:00Z","unreadCount":0}

event: unknown
data: {"ignored":true}
`)).toEqual([
      {
        type: 'connected',
        unreadCount: 2,
        timestamp: '2026-06-18T10:00:00Z'
      },
      {
        type: 'notification-created',
        notification: {
          id: 'notice-1',
          type: 'REPORT_READY',
          title: '报告已生成',
          body: '请查看',
          metadata: {},
          unread: true,
          link: undefined,
          readAt: undefined,
          createdAt: undefined,
          updatedAt: undefined
        },
        unreadCount: 2
      },
      {
        type: 'notification-read-all',
        readAt: '2026-06-18T10:01:00Z',
        unreadCount: 0
      }
    ]);
  });

  it('subscribes notification stream with bearer auth and buffers split chunks', async () => {
    getAuthTokenMock.mockReturnValue('user-token');
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: notification-created\ndata: {"notification":{"id":"notice-1",'));
        controller.enqueue(encoder.encode('"type":"SYSTEM_INFO","title":"平台消息","body":"hello","unread":true},"unreadCount":1}\n\n'));
        controller.enqueue(encoder.encode('event: unread-count\ndata: {"unreadCount":1}\n\n'));
        controller.close();
      }
    });
    const fetchMock = vi.fn().mockResolvedValue(new Response(stream, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    }));
    vi.stubGlobal('fetch', fetchMock);

    const events: Array<ReturnType<typeof parseNotificationStreamEvents>[number]> = [];
    await subscribeNotificationStream((event) => events.push(event));

    expect(fetchMock).toHaveBeenCalledOnce();
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(path).toBe('/api/v1/notifications/stream');
    expect(init.method).toBe('GET');
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('Authorization')).toBe('Bearer user-token');
    expect(events).toEqual([
      {
        type: 'notification-created',
        notification: {
          id: 'notice-1',
          type: 'SYSTEM_INFO',
          title: '平台消息',
          body: 'hello',
          metadata: {},
          unread: true,
          link: undefined,
          readAt: undefined,
          createdAt: undefined,
          updatedAt: undefined
        },
        unreadCount: 1
      },
      {
        type: 'unread-count',
        unreadCount: 1
      }
    ]);
  });

  it('normalizes notification stream auth retry and errors', async () => {
    getAuthTokenMock.mockReturnValue('expired-token');
    refreshTokenMock.mockResolvedValueOnce(true).mockResolvedValueOnce(false);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('event: heartbeat\ndata: {"timestamp":"2026-06-18T10:00:00Z"}\n\n', {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' }
      }))
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('{}', {
        status: 200,
        headers: { 'Content-Type': 'application/json', 'X-Trace-Id': 'trace-content-type' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'FORBIDDEN',
        message: 'stream denied',
        trace_id: 'trace-json'
      }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    const events: Array<ReturnType<typeof parseNotificationStreamEvents>[number]> = [];
    await subscribeNotificationStream((event) => events.push(event));
    expect(refreshTokenMock).toHaveBeenCalledTimes(1);
    expect(events).toEqual([{ type: 'heartbeat', timestamp: '2026-06-18T10:00:00Z' }]);

    await expect(subscribeNotificationStream(() => undefined)).rejects.toMatchObject({
      code: 'SESSION_EXPIRED',
      message: '登录已过期，请重新登录',
      status: 401
    });

    getAuthTokenMock.mockReturnValue('fresh-token');
    await expect(subscribeNotificationStream(() => undefined)).rejects.toMatchObject({
      code: 'INVALID_STREAM_RESPONSE',
      traceId: 'trace-content-type',
      status: 200
    });

    await expect(subscribeNotificationStream(() => undefined)).rejects.toMatchObject({
      code: 'FORBIDDEN',
      message: 'stream denied',
      traceId: 'trace-json',
      status: 403
    });
  });
});
