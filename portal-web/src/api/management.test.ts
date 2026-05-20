import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  auditLogExportPath,
  exportAuditLogsCsv,
  fetchManagementData
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
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-audit',
      data: { items: [{ action: '登录', actor: 'tester', result: '成功', target: 'portal', time: '2026-05-20 10:00' }] }
    });

    const response = await fetchManagementData(['audit:read']);

    expect(requestJsonMock).toHaveBeenCalledTimes(1);
    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/management/audit-logs');
    expect(response.traceId).toBe('trace-audit');
    expect(response.data.auditLogs).toHaveLength(1);
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
});
