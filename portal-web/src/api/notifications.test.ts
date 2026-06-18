import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  normalizeBatchReadResult,
  normalizeNotification,
  normalizeNotificationList,
  normalizeUnreadCount
} from './notifications';

vi.mock('./client', () => ({
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);

describe('notification API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
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
});
