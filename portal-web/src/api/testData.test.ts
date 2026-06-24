import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestBinary, requestJson } from './client';
import {
  acquireTestAccountLease,
  addTestPooledAccount,
  archiveTestAccountPool,
  archiveTestDataSet,
  createTestAccountPool,
  createTestDataSet,
  createTestDataTask,
  disableTestAccountPool,
  downloadTestAccountLeaseExport,
  downloadTestDataSetExport,
  exportTestAccountLease,
  exportTestDataSet,
  fetchTestAccountLease,
  fetchTestAccountLeases,
  fetchTestAccountPool,
  fetchTestAccountPools,
  fetchTestDataHealth,
  fetchTestDataSet,
  fetchTestDataSets,
  fetchTestDataTask,
  fetchTestDataTasks,
  generateTestDataRecords,
  importTestDataRecords,
  normalizeTestAccountLease,
  normalizeTestAccountLeaseExport,
  normalizeTestAccountPoolDetail,
  normalizeTestDataRecordGeneration,
  normalizeTestDataSetDetail,
  normalizeTestDataSetExport,
  normalizeTestDataTask,
  releaseTestAccountLease,
  renewTestAccountLease,
  retryTestDataTask,
  sanitizeSensitiveObject,
  updateTestAccountPool,
  updateTestDataSet,
  updateTestPooledAccount
} from './testData';

vi.mock('./client', () => ({
  requestBinary: vi.fn(),
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestBinaryMock = vi.mocked(requestBinary);

describe('WP8 test data API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestBinaryMock.mockReset();
    requestBinaryMock.mockResolvedValue({
      blob: new Blob(['{}'], { type: 'application/json' }),
      traceId: 'trc-binary',
      contentType: 'application/json',
      filename: 'wp8-export.json'
    });
  });

  it('normalizes data set, account pool, lease and task responses without sensitive fields', () => {
    expect(normalizeTestDataSetDetail({
      id: 'ds-1',
      project_id: 'project-alpha',
      application_id: 'app-1',
      environment_id: 'staging',
      code: 'login-users',
      name: 'Login users',
      status: 'READY',
      sensitivity_level: 'CONFIDENTIAL',
      source_type: 'GENERATED',
      source_ref_digest: 'digest-source',
      record_count: '2',
      cleanup_policy: { password: 'raw', mode: 'MANUAL' },
      schema: { fields: ['username'], token: 'raw-token' },
      records: [{
        id: 'rec-1',
        data_set_id: 'ds-1',
        project_id: 'project-alpha',
        record_key: 'admin',
        status: 'ACTIVE',
        record_digest: 'digest-record',
        masked_summary: { username: 'admin', password: 'hidden', secretRef: 'secret://wp8/raw' },
        external_ref_digest: 'digest-external',
        tags: ['admin']
      }],
      policy: { exportEnabled: true, cookie: 'raw-cookie' }
    })).toMatchObject({
      projectId: 'project-alpha',
      applicationId: 'app-1',
      environmentId: 'staging',
      sensitivityLevel: 'CONFIDENTIAL',
      recordCount: 2,
      cleanupPolicy: { mode: 'MANUAL' },
      schema: { fields: ['username'] },
      records: [{ recordKey: 'admin', maskedSummary: { username: 'admin' } }],
      policy: { exportEnabled: true }
    });

    expect(normalizeTestAccountPoolDetail({
      id: 'pool-1',
      project_id: 'project-alpha',
      code: 'admin-pool',
      name: 'Admin pool',
      status: 'ACTIVE',
      default_ttl_seconds: '1800',
      account_count: '1',
      available_account_count: '1',
      locked_account_count: '0',
      disabled_account_count: '0',
      lease_policy: { maxConcurrentLeases: 1, credential: 'raw' },
      accounts: [{
        id: 'acc-1',
        pool_id: 'pool-1',
        project_id: 'project-alpha',
        account_key: 'admin-01',
        display_name: 'Admin 01',
        status: 'AVAILABLE',
        role_tags: ['admin'],
        scope_summary: { tenant: 'alpha', token: 'raw' },
        secret_ref_digest: 'digest-secret',
        last_health_status: 'OK'
      }],
      policy: { secretRefPlaintextReturned: false }
    })).toMatchObject({
      defaultTtlSeconds: 1800,
      availableAccountCount: 1,
      leasePolicy: { maxConcurrentLeases: 1 },
      accounts: [{
        accountKey: 'admin-01',
        scopeSummary: { tenant: 'alpha' },
        secretRefDigest: 'digest-secret'
      }]
    });

    expect(normalizeTestAccountLease({
      id: 'lease-1',
      pool_id: 'pool-1',
      account_id: 'acc-1',
      project_id: 'project-alpha',
      status: 'ACTIVE',
      holder_type: 'RUN',
      holder_ref: 'run-1',
      request_key: 'rk-1',
      lease_token_digest: 'digest-token',
      expires_at: '2026-06-15T01:00:00Z',
      account: {
        id: 'acc-1',
        pool_id: 'pool-1',
        project_id: 'project-alpha',
        account_key: 'admin-01',
        status: 'AVAILABLE',
        secret_ref_digest: 'digest-secret',
        scope_summary: { password: 'raw', tenant: 'alpha' }
      }
    })).toMatchObject({
      holderRef: 'run-1',
      leaseTokenDigest: 'digest-token',
      account: { accountKey: 'admin-01', scopeSummary: { tenant: 'alpha' } }
    });

    expect(normalizeTestDataTask({
      id: 'task-1',
      project_id: 'project-alpha',
      data_set_id: 'ds-1',
      task_type: 'CLEANUP',
      status: 'FAILED',
      request_key: 'cleanup-1',
      result_summary: { deleted: 0, authorization: 'Bearer raw' },
      error_code: 'CLEANUP_DISABLED',
      trace_id: 'trc-1'
    })).toMatchObject({
      dataSetId: 'ds-1',
      taskType: 'CLEANUP',
      resultSummary: { deleted: 0 },
      traceId: 'trc-1'
    });

    expect(normalizeTestDataSetExport({
      schema_version: 'wp8-data-set-export-v1',
      exported_at: '2026-06-15T00:00:00Z',
      data_set: {
        id: 'ds-1',
        project_id: 'project-alpha',
        code: 'login-users',
        name: 'Login users',
        status: 'READY',
        sensitivity_level: 'CONFIDENTIAL',
        source_type: 'EXTERNAL_REF',
        record_count: '1'
      },
      record_count: '1',
      schema_field_count: '2',
      sensitive_field_count: '1',
      records: [{
        record_key: 'admin',
        record_digest: 'digest-record',
        external_ref_digest: 'digest-external',
        tags: ['sanitized'],
        masked_summary_keys: ['username', 'customerEmail'],
        masked_summary: { customerEmail: 'c***@example.test', secretRef: 'secret://wp8/raw' }
      }],
      redaction_policy: {
        rawRecordPayloadExported: false,
        maskedSummaryValuesExported: false,
        secretRefPlaintextExported: false,
        authorization: 'Bearer raw'
      }
    })).toMatchObject({
      schemaVersion: 'wp8-data-set-export-v1',
      recordCount: 1,
      schemaFieldCount: 2,
      sensitiveFieldCount: 1,
      dataSet: { id: 'ds-1', recordCount: 1 },
      records: [{
        recordKey: 'admin',
        recordDigest: 'digest-record',
        maskedSummaryKeys: ['username', 'customerEmail']
      }],
      redactionPolicy: {
        rawRecordPayloadExported: false,
        maskedSummaryValuesExported: false,
        secretRefPlaintextExported: false
      }
    });

    expect(normalizeTestDataRecordGeneration({
      data_set_id: 'ds-1',
      generated_count: '2',
      records: [{
        id: 'rec-1',
        data_set_id: 'ds-1',
        project_id: 'project-alpha',
        record_key: 'generated-001',
        status: 'ACTIVE',
        record_digest: 'digest-record',
        masked_summary: { customerId: 'customer-1', riskScore: 'riskScore-masked-1' },
        tags: ['generated', 'synthetic']
      }],
      policy: { rawRecordPayloadStored: false, secret: 'raw' }
    })).toMatchObject({
      dataSetId: 'ds-1',
      generatedCount: 2,
      records: [{ recordKey: 'generated-001', tags: ['generated', 'synthetic'] }],
      policy: { rawRecordPayloadStored: false }
    });

    expect(normalizeTestAccountLeaseExport({
      schema_version: 'wp8-account-lease-export-v1',
      exported_at: '2026-06-15T00:00:00Z',
      lease: {
        id: 'lease-1',
        pool_id: 'pool-1',
        account_id: 'acc-1',
        project_id: 'project-alpha',
        status: 'RELEASED',
        holder_type: 'EXECUTION_RUN',
        holder_ref: 'run-1',
        request_key: 'lease-run-1',
        request_digest: 'digest-request',
        lease_token_digest: 'digest-lease-token',
        release_reason_present: true,
        release_reason_digest: 'digest-release'
      },
      pool: {
        id: 'pool-1',
        project_id: 'project-alpha',
        code: 'admin-pool',
        name: 'Admin pool',
        status: 'READY',
        default_ttl_seconds: '1800',
        lease_policy_keys: ['sharing', 'cookie', 'token']
      },
      account: {
        id: 'acc-1',
        pool_id: 'pool-1',
        project_id: 'project-alpha',
        account_key: 'admin-01',
        status: 'LEASED',
        role_tags: ['ADMIN'],
        scope_summary_keys: ['tenant', 'token', 'authorization'],
        secret_ref_digest: 'digest-secret',
        last_health_status: 'HEALTHY',
        last_health_summary_present: true,
        last_health_summary_digest: 'digest-health'
      },
      lifecycle_summary: {
        releaseReasonPresent: true,
        authorization: 'Bearer raw'
      },
      redaction_policy: {
        secretRefPlaintextExported: false,
        leaseTokenPlaintextExported: false,
        freeTextValuesExported: false,
        cookie: 'raw'
      }
    })).toMatchObject({
      schemaVersion: 'wp8-account-lease-export-v1',
      lease: {
        id: 'lease-1',
        releaseReasonPresent: true,
        releaseReasonDigest: 'digest-release'
      },
      pool: {
        id: 'pool-1',
        defaultTtlSeconds: 1800,
        leasePolicyKeys: ['sharing']
      },
      account: {
        accountKey: 'admin-01',
        scopeSummaryKeys: ['tenant'],
        secretRefDigest: 'digest-secret',
        lastHealthSummaryPresent: true,
        lastHealthSummaryDigest: 'digest-health'
      },
      lifecycleSummary: {
        releaseReasonPresent: true
      },
      redactionPolicy: {
        secretRefPlaintextExported: false,
        leaseTokenPlaintextExported: false,
        freeTextValuesExported: false
      }
    });
  });

  it('calls query endpoints with normalized paths', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: { items: [] } });

    await fetchTestDataHealth();
    await fetchTestDataSets({ projectId: 'project-alpha', status: 'ACTIVE', keyword: 'login', size: 10 });
    await fetchTestDataSet('ds-1');
    await exportTestDataSet('ds-1');
    await downloadTestDataSetExport('ds-1');
    await generateTestDataRecords('ds-1', { count: 2, recordKeyPrefix: 'generated', tags: ['smoke'] });
    await fetchTestAccountPools({ projectId: 'project-alpha', environmentId: 'staging' });
    await fetchTestAccountPool('pool-1');
    await fetchTestAccountLeases({ projectId: 'project-alpha', poolId: 'pool-1', status: 'ACTIVE' });
    await fetchTestAccountLease('lease-1');
    await exportTestAccountLease('lease-1');
    await downloadTestAccountLeaseExport('lease-1');
    await fetchTestDataTasks({ projectId: 'project-alpha', taskType: 'CLEANUP', status: 'FAILED' });
    await fetchTestDataTask('task-1');

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/test-data/health');
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/test-data/data-sets?projectId=project-alpha&status=ACTIVE&keyword=login&size=10'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/test-data/data-sets/ds-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/test-data/data-sets/ds-1/export');
    expect(requestBinaryMock).toHaveBeenNthCalledWith(1, '/api/v1/test-data/data-sets/ds-1/export/download');
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, '/api/v1/test-data/data-sets/ds-1/generate-records', {
      method: 'POST',
      body: JSON.stringify({ count: 2, recordKeyPrefix: 'generated', tags: ['smoke'] })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      6,
      '/api/v1/test-data/account-pools?projectId=project-alpha&environmentId=staging'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, '/api/v1/test-data/account-pools/pool-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      8,
      '/api/v1/test-data/leases?projectId=project-alpha&poolId=pool-1&status=ACTIVE'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(9, '/api/v1/test-data/leases/lease-1');
    expect(requestJsonMock).toHaveBeenNthCalledWith(10, '/api/v1/test-data/leases/lease-1/export');
    expect(requestBinaryMock).toHaveBeenNthCalledWith(2, '/api/v1/test-data/leases/lease-1/export/download');
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      11,
      '/api/v1/test-data/data-tasks?projectId=project-alpha&taskType=CLEANUP&status=FAILED'
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(12, '/api/v1/test-data/data-tasks/task-1');
  });

  it('wraps mutating endpoints and keeps secretRef write-only', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'OK', trace_id: 'trc', data: {} });

    await createTestDataSet({
      projectId: 'project-alpha',
      code: 'login-users',
      name: 'Login users',
      schema: { username: 'string', password: 'raw' },
      cleanupPolicy: { mode: 'MANUAL', token: 'raw-token' }
    });
    await updateTestDataSet('ds-1', { name: 'Login users v2' });
    await archiveTestDataSet('ds-1');
    await importTestDataRecords('ds-1', {
      records: [{
        recordKey: 'admin',
        recordDigest: 'digest-record',
        maskedSummary: { username: 'admin', secretRef: 'secret://wp8/raw' }
      }]
    });
    await generateTestDataRecords('ds-1', { count: 2, recordKeyPrefix: 'generated', tags: ['smoke'] });
    await createTestAccountPool({
      projectId: 'project-alpha',
      code: 'admin-pool',
      name: 'Admin pool',
      leasePolicy: { maxConcurrentLeases: 1, credential: 'raw' }
    });
    await updateTestAccountPool('pool-1', { name: 'Admin pool v2' });
    await disableTestAccountPool('pool-1');
    await archiveTestAccountPool('pool-1');
    await addTestPooledAccount('pool-1', {
      accountKey: 'admin-01',
      displayName: 'Admin 01',
      scopeSummary: { tenant: 'alpha', password: 'raw' },
      secretRef: 'secret://wp8/admin-01'
    });
    await updateTestPooledAccount('acc-1', {
      displayName: 'Admin 01 updated',
      scopeSummary: { tenant: 'alpha' }
    });
    await acquireTestAccountLease({
      projectId: 'project-alpha',
      poolId: 'pool-1',
      holderType: 'RUN',
      holderRef: 'run-1',
      requestKey: 'lease-run-1'
    });
    await renewTestAccountLease('lease-1', { ttlSeconds: 900 });
    await releaseTestAccountLease('lease-1', { releaseReason: 'done', accountStatus: 'AVAILABLE' });
    await createTestDataTask({
      projectId: 'project-alpha',
      taskType: 'CLEANUP',
      requestKey: 'cleanup-1',
      resultSummary: { deleted: 0, cookie: 'raw-cookie' }
    });
    await retryTestDataTask('task-1', { requestKey: 'cleanup-1-retry' });

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/test-data/data-sets', {
      method: 'POST',
      body: expect.stringContaining('login-users')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, '/api/v1/test-data/data-sets/ds-1', {
      method: 'PATCH',
      body: JSON.stringify({ name: 'Login users v2' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, '/api/v1/test-data/data-sets/ds-1/archive', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, '/api/v1/test-data/data-sets/ds-1/records', {
      method: 'POST',
      body: expect.stringContaining('digest-record')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, '/api/v1/test-data/data-sets/ds-1/generate-records', {
      method: 'POST',
      body: JSON.stringify({ count: 2, recordKeyPrefix: 'generated', tags: ['smoke'] })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, '/api/v1/test-data/account-pools', {
      method: 'POST',
      body: expect.stringContaining('admin-pool')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, '/api/v1/test-data/account-pools/pool-1', {
      method: 'PATCH',
      body: JSON.stringify({ name: 'Admin pool v2' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(8, '/api/v1/test-data/account-pools/pool-1/disable', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(9, '/api/v1/test-data/account-pools/pool-1/archive', { method: 'POST' });
    expect(requestJsonMock).toHaveBeenNthCalledWith(10, '/api/v1/test-data/account-pools/pool-1/accounts', {
      method: 'POST',
      body: expect.stringContaining('secret://wp8/admin-01')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(11, '/api/v1/test-data/accounts/acc-1', {
      method: 'PATCH',
      body: expect.not.stringContaining('secretRef')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(12, '/api/v1/test-data/leases', {
      method: 'POST',
      body: expect.stringContaining('lease-run-1')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(13, '/api/v1/test-data/leases/lease-1/renew', {
      method: 'POST',
      body: JSON.stringify({ ttlSeconds: 900 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(14, '/api/v1/test-data/leases/lease-1/release', {
      method: 'POST',
      body: JSON.stringify({ releaseReason: 'done', accountStatus: 'AVAILABLE' })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(15, '/api/v1/test-data/data-tasks', {
      method: 'POST',
      body: expect.stringContaining('cleanup-1')
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(16, '/api/v1/test-data/data-tasks/task-1/retry', {
      method: 'POST',
      body: JSON.stringify({ requestKey: 'cleanup-1-retry' })
    });

    const createDataSetBody = bodyAt(1);
    expect(createDataSetBody.schema).toEqual({ username: 'string' });
    expect(createDataSetBody.cleanupPolicy).toEqual({ mode: 'MANUAL' });
    expect(JSON.stringify(bodyAt(4))).not.toContain('secret://wp8/raw');
    expect(bodyAt(5)).toEqual({ count: 2, recordKeyPrefix: 'generated', tags: ['smoke'] });
    expect(JSON.stringify(bodyAt(6))).not.toContain('credential');
    expect(bodyAt(10).scopeSummary).toEqual({ tenant: 'alpha' });
    expect(JSON.stringify(bodyAt(15))).not.toContain('raw-cookie');
  });

  it('redacts nested sensitive values but preserves digests', () => {
    expect(sanitizeSensitiveObject({
      password: 'raw',
      secretRef: 'secret://wp8/raw',
      secretRefDigest: 'digest-secret',
      nested: {
        cookie: 'raw-cookie',
        value: 'prefix secret://wp8/raw suffix'
      }
    })).toEqual({
      secretRefDigest: 'digest-secret',
      nested: {
        value: 'prefix [redacted] suffix'
      }
    });
  });
});

function bodyAt(callNumber: number): Record<string, unknown> {
  const call = requestJsonMock.mock.calls[callNumber - 1];
  const init = call[1] as RequestInit;
  return JSON.parse(String(init.body)) as Record<string, unknown>;
}
