import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  auditLogExportPath,
  auditOutboxPath,
  changeRoleStatus,
  createRole,
  createSecretReference,
  disableSecretReference,
  exportAuditLogsCsv,
  fetchPermissions,
  fetchRole,
  fetchManagementData,
  fetchEnvironmentConnectivityCheck,
  listSecrets,
  rotateSecretReference,
  runEnvironmentConnectivityCheck,
  updateRole
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

  it('calls role definition and permission catalog endpoints', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-role',
      data: { code: 'QaReviewer', name: 'QA 评审员', scopeType: 'PROJECT', permissionCodes: ['role:read'] }
    });

    await fetchPermissions();
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/permissions?size=100');

    await fetchRole('QA Role');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/roles/QA%20Role');

    await createRole({
      code: 'QaReviewer',
      name: 'QA 评审员',
      scopeType: 'PROJECT',
      description: '项目内评审',
      permissionCodes: ['role:read', 'audit:read']
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/roles', {
      method: 'POST',
      body: JSON.stringify({
        code: 'QaReviewer',
        name: 'QA 评审员',
        scopeType: 'PROJECT',
        description: '项目内评审',
        permissionCodes: ['role:read', 'audit:read']
      })
    });

    await updateRole('QaReviewer', {
      name: 'QA 审计评审员',
      scopeType: 'PROJECT',
      description: '',
      permissionCodes: ['role:read', 'audit:read', 'audit:export']
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/roles/QaReviewer', {
      method: 'PATCH',
      body: JSON.stringify({
        name: 'QA 审计评审员',
        scopeType: 'PROJECT',
        description: '',
        permissionCodes: ['role:read', 'audit:read', 'audit:export']
      })
    });

    await changeRoleStatus('QaReviewer', 'DISABLED');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/roles/QaReviewer/status', {
      method: 'PATCH',
      body: JSON.stringify({ status: 'DISABLED' })
    });
  });

  it('calls secret reference management endpoints without putting refs in path variables', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-secret',
      data: { secretRef: 'secret://wp1/example', maskedValue: '********', status: 'ACTIVE' }
    });

    await createSecretReference({
      secret_ref: 'secret://wp1/example',
      provider_code: 'local',
      purpose: 'WEBHOOK_SIGNING',
      scope_type: 'CONFIG',
      scope_id: '00000000-0000-0000-0000-000000000001',
      secret_value: 'PlainSecret123',
      secret_version: 'v1'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/secrets', {
      method: 'POST',
      body: JSON.stringify({
        secretRef: 'secret://wp1/example',
        providerCode: 'local',
        purpose: 'WEBHOOK_SIGNING',
        scopeType: 'CONFIG',
        scopeId: '00000000-0000-0000-0000-000000000001',
        value: 'PlainSecret123',
        secretVersion: 'v1',
        expiresAt: undefined
      })
    });

    await rotateSecretReference({
      secret_ref: 'secret://wp1/example',
      secret_value: 'RotatedSecret456',
      secret_version: 'v2'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/secrets/rotate', {
      method: 'POST',
      body: JSON.stringify({
        secretRef: 'secret://wp1/example',
        value: 'RotatedSecret456',
        secretVersion: 'v2',
        expiresAt: undefined
      })
    });

    await disableSecretReference('secret://wp1/example');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/secrets/disable', {
      method: 'POST',
      body: JSON.stringify({ secretRef: 'secret://wp1/example' })
    });
  });

  it('builds secret list query string with pagination and search', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-secret-list',
      data: { items: [], page: 1, page_size: 10, total: 0 }
    });

    await listSecrets({ index: 1, size: 10, search: 'model' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/secrets?index=1&size=10&search=model');

    await listSecrets();
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/management/secrets');
  });
});
