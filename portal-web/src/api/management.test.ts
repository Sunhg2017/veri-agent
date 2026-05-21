import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  auditLogExportPath,
  auditOutboxPath,
  exportAuditLogsCsv,
  fetchManagementData,
  fetchEnvironmentConnectivityCheck,
  runEnvironmentConnectivityCheck
} from './management';

vi.mock('./client', () => ({
  requestJson: vi.fn(),
  requestText: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestTextMock = vi.mocked(requestText);

describe('management API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestTextMock.mockReset();
  });

  it('skips unreadable management pages based on permissions', async () => {
    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-audit',
      data: { items: [{ action: '登录', actor: 'tester', result: '成功', target: 'portal', time: '2026-05-20 10:00' }] }
    }).mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-outbox',
      data: { items: [{ id: 'outbox-1', traceId: 'trc_1', status: 'FAILED', retryCount: 2, eventAction: '登录' }] }
    });

    const response = await fetchManagementData(['audit:read']);

    expect(requestJsonMock).toHaveBeenCalledTimes(2);
    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/management/audit-logs');
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, '/api/v1/management/audit-outbox');
    expect(response.traceId).toBe('trace-audit');
    expect(response.data.auditLogs).toHaveLength(1);
    expect(response.data.auditOutbox).toHaveLength(1);
    expect(response.data.users).toEqual([]);
  });

  it('builds filtered audit CSV export paths and calls the text endpoint', async () => {
    requestTextMock.mockResolvedValue({
      text: 'time,actor,action,target,result\n',
      traceId: 'trace-export',
      contentType: 'text/csv;charset=UTF-8',
      filename: 'wp1-audit-logs.csv'
    });

    expect(auditLogExportPath({
      search: ' 登录 ',
      actor: 'tester',
      action: '',
      resourceType: 'iam_user',
      result: 'SUCCESS',
      startTime: '2026-05-20T00:00:00Z'
    })).toBe('/api/v1/management/audit-logs/export?search=%E7%99%BB%E5%BD%95&actor=tester&resourceType=iam_user&result=SUCCESS&startTime=2026-05-20T00%3A00%3A00Z');

    const response = await exportAuditLogsCsv({ actor: 'tester', result: 'SUCCESS' });

    expect(requestTextMock).toHaveBeenLastCalledWith('/api/v1/management/audit-logs/export?actor=tester&result=SUCCESS');
    expect(response.filename).toBe('wp1-audit-logs.csv');
  });

  it('builds filtered audit outbox paths', () => {
    expect(auditOutboxPath({
      status: ' FAILED ',
      traceId: ' trc_1 ',
      search: ' timeout '
    })).toBe('/api/v1/management/audit-outbox?search=timeout&status=FAILED&traceId=trc_1');
  });

  it('calls environment connectivity check endpoints', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-env',
      data: {
        environment: 'staging',
        status: 'UP',
        checkedAt: '2026-05-21T10:00:00Z',
        message: '全部环境地址已响应',
        traceId: 'trace-env',
        endpoints: []
      }
    });

    await fetchEnvironmentConnectivityCheck('staging env');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/environments/staging%20env/connectivity-check');

    await runEnvironmentConnectivityCheck('staging env');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/environments/staging%20env/connectivity-check', {
      method: 'POST'
    });
  });
});
