import { requestJson, type ApiResponse } from './client';

const TEST_DATA_BASE = '/api/v1/test-data';

export interface PageResponse<T> {
  items: T[];
  index: number;
  size: number;
  total: number;
}

export interface TestDataHealth {
  service: string;
  status: string;
  enabled: boolean;
  cleanupEnabled: boolean;
  exportEnabled: boolean;
  recordMaxCount: number;
  recordSummaryMaxBytes: number;
  defaultLeaseTtlSeconds: number;
  maxLeaseTtlSeconds: number;
  policy: Record<string, unknown>;
}

export interface TestDataRecord {
  id: string;
  dataSetId: string;
  projectId: string;
  recordKey: string;
  status: string;
  recordDigest: string;
  maskedSummary: Record<string, unknown>;
  externalRefDigest?: string;
  tags: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDataSetSummary {
  id: string;
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  code: string;
  name: string;
  status: string;
  sensitivityLevel: string;
  sourceType: string;
  sourceRefDigest?: string;
  recordCount: number;
  cleanupPolicy: Record<string, unknown>;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDataSetDetail extends TestDataSetSummary {
  schema: Record<string, unknown>;
  records: TestDataRecord[];
  policy: Record<string, unknown>;
}

export interface TestDataSetExportRecord {
  recordKey: string;
  recordDigest: string;
  externalRefDigest?: string;
  tags: string[];
  maskedSummaryKeys: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDataSetExport {
  schemaVersion: string;
  exportedAt?: string;
  dataSet: TestDataSetSummary;
  recordCount: number;
  schemaFieldCount: number;
  sensitiveFieldCount: number;
  records: TestDataSetExportRecord[];
  redactionPolicy: Record<string, unknown>;
}

export interface TestDataRecordImport {
  dataSetId: string;
  importedCount: number;
  records: TestDataRecord[];
  policy: Record<string, unknown>;
}

export interface TestAccountPoolSummary {
  id: string;
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  code: string;
  name: string;
  status: string;
  leasePolicy: Record<string, unknown>;
  defaultTtlSeconds: number;
  accountCount: number;
  availableAccountCount: number;
  lockedAccountCount: number;
  disabledAccountCount: number;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestPooledAccount {
  id: string;
  poolId: string;
  projectId: string;
  accountKey: string;
  displayName?: string;
  status: string;
  roleTags: string[];
  scopeSummary: Record<string, unknown>;
  secretRefDigest?: string;
  lastHealthStatus?: string;
  lastHealthSummary?: string;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestAccountPoolDetail extends TestAccountPoolSummary {
  accounts: TestPooledAccount[];
  policy: Record<string, unknown>;
}

export interface TestAccountLease {
  id: string;
  poolId: string;
  accountId: string;
  projectId: string;
  status: string;
  holderType: string;
  holderRef: string;
  requestKey: string;
  leaseTokenDigest?: string;
  expiresAt?: string;
  releasedAt?: string;
  releaseReason?: string;
  account?: TestPooledAccount;
  policy: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestAccountLeaseExportLease {
  id: string;
  poolId: string;
  accountId: string;
  projectId: string;
  status: string;
  holderType: string;
  holderRef: string;
  requestKey: string;
  requestDigest?: string;
  leaseTokenDigest?: string;
  expiresAt?: string;
  releasedAt?: string;
  releaseReasonPresent: boolean;
  releaseReasonDigest?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestAccountLeaseExportPool {
  id: string;
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  code: string;
  name: string;
  status: string;
  defaultTtlSeconds: number;
  leasePolicyKeys: string[];
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestAccountLeaseExportAccount {
  id: string;
  poolId: string;
  projectId: string;
  accountKey: string;
  displayName?: string;
  status: string;
  roleTags: string[];
  scopeSummaryKeys: string[];
  secretRefDigest?: string;
  lastHealthStatus?: string;
  lastHealthSummaryPresent: boolean;
  lastHealthSummaryDigest?: string;
  archivedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestAccountLeaseExport {
  schemaVersion: string;
  exportedAt?: string;
  lease: TestAccountLeaseExportLease;
  pool: TestAccountLeaseExportPool;
  account: TestAccountLeaseExportAccount;
  lifecycleSummary: Record<string, unknown>;
  redactionPolicy: Record<string, unknown>;
}

export interface TestDataTask {
  id: string;
  projectId: string;
  dataSetId?: string;
  taskType: string;
  status: string;
  requestKey: string;
  targetRef?: string;
  attempt: number;
  resultSummary: Record<string, unknown>;
  errorCode?: string;
  errorSummary?: string;
  traceId?: string;
  policy: Record<string, unknown>;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDataSetFilters {
  projectId?: string;
  applicationId?: string;
  environmentId?: string;
  status?: string;
  keyword?: string;
  index?: number;
  size?: number;
}

export type TestAccountPoolFilters = TestDataSetFilters;

export interface TestAccountLeaseFilters {
  projectId?: string;
  poolId?: string;
  accountId?: string;
  status?: string;
  holderRef?: string;
  index?: number;
  size?: number;
}

export interface TestDataTaskFilters {
  projectId?: string;
  dataSetId?: string;
  taskType?: string;
  status?: string;
  index?: number;
  size?: number;
}

export interface TestDataSetPayload {
  projectId?: string;
  applicationId?: string;
  environmentId?: string;
  code?: string;
  name?: string;
  status?: string;
  schema?: Record<string, unknown>;
  sensitivityLevel?: string;
  cleanupPolicy?: Record<string, unknown>;
  sourceType?: string;
  sourceRefDigest?: string;
}

export interface ImportTestDataRecordsPayload {
  records: Array<{
    recordKey: string;
    recordDigest: string;
    maskedSummary?: Record<string, unknown>;
    externalRefDigest?: string;
    tags?: string[];
  }>;
}

export interface TestAccountPoolPayload {
  projectId?: string;
  applicationId?: string;
  environmentId?: string;
  code?: string;
  name?: string;
  status?: string;
  leasePolicy?: Record<string, unknown>;
  defaultTtlSeconds?: number;
}

export interface TestPooledAccountPayload {
  accountKey?: string;
  displayName?: string;
  status?: string;
  roleTags?: string[];
  scopeSummary?: Record<string, unknown>;
  secretRef?: string;
  lastHealthStatus?: string;
  lastHealthSummary?: string;
}

export interface AcquireTestAccountLeasePayload {
  projectId: string;
  applicationId?: string;
  environmentId?: string;
  poolId: string;
  roleTags?: string[];
  holderType: string;
  holderRef: string;
  ttlSeconds?: number;
  requestKey: string;
}

export interface RenewTestAccountLeasePayload {
  ttlSeconds: number;
}

export interface ReleaseTestAccountLeasePayload {
  releaseReason?: string;
  accountStatus?: string;
}

export interface TestDataTaskPayload {
  projectId?: string;
  dataSetId?: string;
  taskType?: string;
  requestKey?: string;
  targetRef?: string;
  resultSummary?: Record<string, unknown>;
}

export interface RetryTestDataTaskPayload {
  requestKey?: string;
  resultSummary?: Record<string, unknown>;
}

export async function fetchTestDataHealth(): Promise<ApiResponse<TestDataHealth>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/health`);
  return { ...response, data: normalizeTestDataHealth(response.data) };
}

export async function fetchTestDataSets(filters: TestDataSetFilters = {}): Promise<ApiResponse<PageResponse<TestDataSetSummary>>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets${queryString(filters)}`);
  return { ...response, data: normalizePage(response.data, normalizeTestDataSetSummary) };
}

export async function fetchTestDataSet(id: string): Promise<ApiResponse<TestDataSetDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeTestDataSetDetail(response.data) };
}

export async function createTestDataSet(payload: TestDataSetPayload): Promise<ApiResponse<TestDataSetDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets`, {
    method: 'POST',
    body: JSON.stringify(sanitizeTestDataSetPayload(payload))
  });
  return { ...response, data: normalizeTestDataSetDetail(response.data) };
}

export async function updateTestDataSet(id: string, payload: Partial<TestDataSetPayload>): Promise<ApiResponse<TestDataSetDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(sanitizeTestDataSetPayload(payload))
  });
  return { ...response, data: normalizeTestDataSetDetail(response.data) };
}

export async function archiveTestDataSet(id: string): Promise<ApiResponse<TestDataSetDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets/${encodeURIComponent(id)}/archive`, {
    method: 'POST'
  });
  return { ...response, data: normalizeTestDataSetDetail(response.data) };
}

export async function exportTestDataSet(id: string): Promise<ApiResponse<TestDataSetExport>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets/${encodeURIComponent(id)}/export`);
  return { ...response, data: normalizeTestDataSetExport(response.data) };
}

export async function importTestDataRecords(
  dataSetId: string,
  payload: ImportTestDataRecordsPayload
): Promise<ApiResponse<TestDataRecordImport>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-sets/${encodeURIComponent(dataSetId)}/records`, {
    method: 'POST',
    body: JSON.stringify(sanitizeRecordImportPayload(payload))
  });
  return { ...response, data: normalizeTestDataRecordImport(response.data) };
}

export async function fetchTestAccountPools(filters: TestAccountPoolFilters = {}): Promise<ApiResponse<PageResponse<TestAccountPoolSummary>>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools${queryString(filters)}`);
  return { ...response, data: normalizePage(response.data, normalizeTestAccountPoolSummary) };
}

export async function fetchTestAccountPool(id: string): Promise<ApiResponse<TestAccountPoolDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeTestAccountPoolDetail(response.data) };
}

export async function createTestAccountPool(payload: TestAccountPoolPayload): Promise<ApiResponse<TestAccountPoolDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools`, {
    method: 'POST',
    body: JSON.stringify(sanitizeAccountPoolPayload(payload))
  });
  return { ...response, data: normalizeTestAccountPoolDetail(response.data) };
}

export async function updateTestAccountPool(
  id: string,
  payload: Partial<TestAccountPoolPayload>
): Promise<ApiResponse<TestAccountPoolDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(sanitizeAccountPoolPayload(payload))
  });
  return { ...response, data: normalizeTestAccountPoolDetail(response.data) };
}

export async function disableTestAccountPool(id: string): Promise<ApiResponse<TestAccountPoolDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools/${encodeURIComponent(id)}/disable`, {
    method: 'POST'
  });
  return { ...response, data: normalizeTestAccountPoolDetail(response.data) };
}

export async function archiveTestAccountPool(id: string): Promise<ApiResponse<TestAccountPoolDetail>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools/${encodeURIComponent(id)}/archive`, {
    method: 'POST'
  });
  return { ...response, data: normalizeTestAccountPoolDetail(response.data) };
}

export async function addTestPooledAccount(
  poolId: string,
  payload: TestPooledAccountPayload
): Promise<ApiResponse<TestPooledAccount>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/account-pools/${encodeURIComponent(poolId)}/accounts`, {
    method: 'POST',
    body: JSON.stringify(sanitizePooledAccountPayload(payload, true))
  });
  return { ...response, data: normalizeTestPooledAccount(response.data) };
}

export async function updateTestPooledAccount(
  accountId: string,
  payload: Partial<TestPooledAccountPayload>
): Promise<ApiResponse<TestPooledAccount>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/accounts/${encodeURIComponent(accountId)}`, {
    method: 'PATCH',
    body: JSON.stringify(sanitizePooledAccountPayload(payload, false))
  });
  return { ...response, data: normalizeTestPooledAccount(response.data) };
}

export async function fetchTestAccountLeases(filters: TestAccountLeaseFilters = {}): Promise<ApiResponse<PageResponse<TestAccountLease>>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/leases${queryString(filters)}`);
  return { ...response, data: normalizePage(response.data, normalizeTestAccountLease) };
}

export async function fetchTestAccountLease(id: string): Promise<ApiResponse<TestAccountLease>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/leases/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeTestAccountLease(response.data) };
}

export async function exportTestAccountLease(id: string): Promise<ApiResponse<TestAccountLeaseExport>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/leases/${encodeURIComponent(id)}/export`);
  return { ...response, data: normalizeTestAccountLeaseExport(response.data) };
}

export async function acquireTestAccountLease(payload: AcquireTestAccountLeasePayload): Promise<ApiResponse<TestAccountLease>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/leases`, {
    method: 'POST',
    body: JSON.stringify(sanitizeLeasePayload(payload))
  });
  return { ...response, data: normalizeTestAccountLease(response.data) };
}

export async function renewTestAccountLease(
  id: string,
  payload: RenewTestAccountLeasePayload
): Promise<ApiResponse<TestAccountLease>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/leases/${encodeURIComponent(id)}/renew`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  return { ...response, data: normalizeTestAccountLease(response.data) };
}

export async function releaseTestAccountLease(
  id: string,
  payload: ReleaseTestAccountLeasePayload = {}
): Promise<ApiResponse<TestAccountLease>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/leases/${encodeURIComponent(id)}/release`, {
    method: 'POST',
    body: JSON.stringify(cleanObject(payload))
  });
  return { ...response, data: normalizeTestAccountLease(response.data) };
}

export async function fetchTestDataTasks(filters: TestDataTaskFilters = {}): Promise<ApiResponse<PageResponse<TestDataTask>>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-tasks${queryString(filters)}`);
  return { ...response, data: normalizePage(response.data, normalizeTestDataTask) };
}

export async function fetchTestDataTask(id: string): Promise<ApiResponse<TestDataTask>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-tasks/${encodeURIComponent(id)}`);
  return { ...response, data: normalizeTestDataTask(response.data) };
}

export async function createTestDataTask(payload: TestDataTaskPayload): Promise<ApiResponse<TestDataTask>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-tasks`, {
    method: 'POST',
    body: JSON.stringify(sanitizeTaskPayload(payload))
  });
  return { ...response, data: normalizeTestDataTask(response.data) };
}

export async function retryTestDataTask(
  id: string,
  payload: RetryTestDataTaskPayload = {}
): Promise<ApiResponse<TestDataTask>> {
  const response = await requestJson<unknown>(`${TEST_DATA_BASE}/data-tasks/${encodeURIComponent(id)}/retry`, {
    method: 'POST',
    body: JSON.stringify(sanitizeTaskPayload(payload))
  });
  return { ...response, data: normalizeTestDataTask(response.data) };
}

export function normalizeTestDataHealth(input: unknown): TestDataHealth {
  const value = objectValue(input);
  return {
    service: stringValue(read(value, 'service'), 'test-data'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    enabled: booleanValue(read(value, 'enabled'), false),
    cleanupEnabled: booleanValue(read(value, 'cleanupEnabled', 'cleanup_enabled'), false),
    exportEnabled: booleanValue(read(value, 'exportEnabled', 'export_enabled'), false),
    recordMaxCount: numberValue(read(value, 'recordMaxCount', 'record_max_count'), 0),
    recordSummaryMaxBytes: numberValue(read(value, 'recordSummaryMaxBytes', 'record_summary_max_bytes'), 0),
    defaultLeaseTtlSeconds: numberValue(read(value, 'defaultLeaseTtlSeconds', 'default_lease_ttl_seconds'), 0),
    maxLeaseTtlSeconds: numberValue(read(value, 'maxLeaseTtlSeconds', 'max_lease_ttl_seconds'), 0),
    policy: sanitizeObject(read(value, 'policy'))
  };
}

export function normalizeTestDataSetSummary(input: unknown): TestDataSetSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    code: stringValue(read(value, 'code')),
    name: stringValue(read(value, 'name')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    sensitivityLevel: stringValue(read(value, 'sensitivityLevel', 'sensitivity_level'), 'INTERNAL'),
    sourceType: stringValue(read(value, 'sourceType', 'source_type'), 'MANUAL'),
    sourceRefDigest: optionalString(read(value, 'sourceRefDigest', 'source_ref_digest')),
    recordCount: numberValue(read(value, 'recordCount', 'record_count'), 0),
    cleanupPolicy: sanitizeObject(read(value, 'cleanupPolicy', 'cleanup_policy')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestDataSetDetail(input: unknown): TestDataSetDetail {
  const value = objectValue(input);
  return {
    ...normalizeTestDataSetSummary(value),
    schema: sanitizeObject(read(value, 'schema')),
    records: arrayValue(read(value, 'records')).map(normalizeTestDataRecord),
    policy: sanitizeObject(read(value, 'policy'))
  };
}

export function normalizeTestDataRecord(input: unknown): TestDataRecord {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    dataSetId: stringValue(read(value, 'dataSetId', 'data_set_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    recordKey: stringValue(read(value, 'recordKey', 'record_key')),
    status: stringValue(read(value, 'status'), 'ACTIVE'),
    recordDigest: stringValue(read(value, 'recordDigest', 'record_digest')),
    maskedSummary: sanitizeObject(read(value, 'maskedSummary', 'masked_summary')),
    externalRefDigest: optionalString(read(value, 'externalRefDigest', 'external_ref_digest')),
    tags: stringArray(read(value, 'tags')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestDataRecordImport(input: unknown): TestDataRecordImport {
  const value = objectValue(input);
  return {
    dataSetId: stringValue(read(value, 'dataSetId', 'data_set_id')),
    importedCount: numberValue(read(value, 'importedCount', 'imported_count'), 0),
    records: arrayValue(read(value, 'records')).map(normalizeTestDataRecord),
    policy: sanitizeObject(read(value, 'policy'))
  };
}

export function normalizeTestDataSetExport(input: unknown): TestDataSetExport {
  const value = objectValue(input);
  return {
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version'), 'wp8-data-set-export-v1'),
    exportedAt: optionalString(read(value, 'exportedAt', 'exported_at')),
    dataSet: normalizeTestDataSetSummary(read(value, 'dataSet', 'data_set')),
    recordCount: numberValue(read(value, 'recordCount', 'record_count'), 0),
    schemaFieldCount: numberValue(read(value, 'schemaFieldCount', 'schema_field_count'), 0),
    sensitiveFieldCount: numberValue(read(value, 'sensitiveFieldCount', 'sensitive_field_count'), 0),
    records: arrayValue(read(value, 'records')).map(normalizeTestDataSetExportRecord),
    redactionPolicy: sanitizePolicyObject(read(value, 'redactionPolicy', 'redaction_policy'))
  };
}

export function normalizeTestDataSetExportRecord(input: unknown): TestDataSetExportRecord {
  const value = objectValue(input);
  return {
    recordKey: stringValue(read(value, 'recordKey', 'record_key')),
    recordDigest: stringValue(read(value, 'recordDigest', 'record_digest')),
    externalRefDigest: optionalString(read(value, 'externalRefDigest', 'external_ref_digest')),
    tags: stringArray(read(value, 'tags')),
    maskedSummaryKeys: stringArray(read(value, 'maskedSummaryKeys', 'masked_summary_keys')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestAccountPoolSummary(input: unknown): TestAccountPoolSummary {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    code: stringValue(read(value, 'code')),
    name: stringValue(read(value, 'name')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    leasePolicy: sanitizeObject(read(value, 'leasePolicy', 'lease_policy')),
    defaultTtlSeconds: numberValue(read(value, 'defaultTtlSeconds', 'default_ttl_seconds'), 0),
    accountCount: numberValue(read(value, 'accountCount', 'account_count'), 0),
    availableAccountCount: numberValue(read(value, 'availableAccountCount', 'available_account_count'), 0),
    lockedAccountCount: numberValue(read(value, 'lockedAccountCount', 'locked_account_count'), 0),
    disabledAccountCount: numberValue(read(value, 'disabledAccountCount', 'disabled_account_count'), 0),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestAccountPoolDetail(input: unknown): TestAccountPoolDetail {
  const value = objectValue(input);
  return {
    ...normalizeTestAccountPoolSummary(value),
    accounts: arrayValue(read(value, 'accounts')).map(normalizeTestPooledAccount),
    policy: sanitizeObject(read(value, 'policy'))
  };
}

export function normalizeTestPooledAccount(input: unknown): TestPooledAccount {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    poolId: stringValue(read(value, 'poolId', 'pool_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    accountKey: stringValue(read(value, 'accountKey', 'account_key')),
    displayName: optionalString(read(value, 'displayName', 'display_name')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    roleTags: stringArray(read(value, 'roleTags', 'role_tags')),
    scopeSummary: sanitizeObject(read(value, 'scopeSummary', 'scope_summary')),
    secretRefDigest: optionalString(read(value, 'secretRefDigest', 'secret_ref_digest')),
    lastHealthStatus: optionalString(read(value, 'lastHealthStatus', 'last_health_status')),
    lastHealthSummary: optionalString(read(value, 'lastHealthSummary', 'last_health_summary')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestAccountLease(input: unknown): TestAccountLease {
  const value = objectValue(input);
  const account = read(value, 'account');
  return {
    id: stringValue(read(value, 'id')),
    poolId: stringValue(read(value, 'poolId', 'pool_id')),
    accountId: stringValue(read(value, 'accountId', 'account_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    holderType: stringValue(read(value, 'holderType', 'holder_type')),
    holderRef: stringValue(read(value, 'holderRef', 'holder_ref')),
    requestKey: stringValue(read(value, 'requestKey', 'request_key')),
    leaseTokenDigest: optionalString(read(value, 'leaseTokenDigest', 'lease_token_digest')),
    expiresAt: optionalString(read(value, 'expiresAt', 'expires_at')),
    releasedAt: optionalString(read(value, 'releasedAt', 'released_at')),
    releaseReason: optionalString(read(value, 'releaseReason', 'release_reason')),
    account: account ? normalizeTestPooledAccount(account) : undefined,
    policy: sanitizeObject(read(value, 'policy')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestAccountLeaseExport(input: unknown): TestAccountLeaseExport {
  const value = objectValue(input);
  return {
    schemaVersion: stringValue(read(value, 'schemaVersion', 'schema_version'), 'wp8-account-lease-export-v1'),
    exportedAt: optionalString(read(value, 'exportedAt', 'exported_at')),
    lease: normalizeTestAccountLeaseExportLease(read(value, 'lease')),
    pool: normalizeTestAccountLeaseExportPool(read(value, 'pool')),
    account: normalizeTestAccountLeaseExportAccount(read(value, 'account')),
    lifecycleSummary: sanitizePolicyObject(read(value, 'lifecycleSummary', 'lifecycle_summary')),
    redactionPolicy: sanitizePolicyObject(read(value, 'redactionPolicy', 'redaction_policy'))
  };
}

export function normalizeTestAccountLeaseExportLease(input: unknown): TestAccountLeaseExportLease {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    poolId: stringValue(read(value, 'poolId', 'pool_id')),
    accountId: stringValue(read(value, 'accountId', 'account_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    holderType: stringValue(read(value, 'holderType', 'holder_type')),
    holderRef: stringValue(read(value, 'holderRef', 'holder_ref')),
    requestKey: stringValue(read(value, 'requestKey', 'request_key')),
    requestDigest: optionalString(read(value, 'requestDigest', 'request_digest')),
    leaseTokenDigest: optionalString(read(value, 'leaseTokenDigest', 'lease_token_digest')),
    expiresAt: optionalString(read(value, 'expiresAt', 'expires_at')),
    releasedAt: optionalString(read(value, 'releasedAt', 'released_at')),
    releaseReasonPresent: booleanValue(read(value, 'releaseReasonPresent', 'release_reason_present'), false),
    releaseReasonDigest: optionalString(read(value, 'releaseReasonDigest', 'release_reason_digest')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestAccountLeaseExportPool(input: unknown): TestAccountLeaseExportPool {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    applicationId: optionalString(read(value, 'applicationId', 'application_id')),
    environmentId: optionalString(read(value, 'environmentId', 'environment_id')),
    code: stringValue(read(value, 'code')),
    name: stringValue(read(value, 'name')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    defaultTtlSeconds: numberValue(read(value, 'defaultTtlSeconds', 'default_ttl_seconds'), 0),
    leasePolicyKeys: safeKeyArray(read(value, 'leasePolicyKeys', 'lease_policy_keys')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestAccountLeaseExportAccount(input: unknown): TestAccountLeaseExportAccount {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    poolId: stringValue(read(value, 'poolId', 'pool_id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    accountKey: stringValue(read(value, 'accountKey', 'account_key')),
    displayName: optionalString(read(value, 'displayName', 'display_name')),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    roleTags: stringArray(read(value, 'roleTags', 'role_tags')),
    scopeSummaryKeys: safeKeyArray(read(value, 'scopeSummaryKeys', 'scope_summary_keys')),
    secretRefDigest: optionalString(read(value, 'secretRefDigest', 'secret_ref_digest')),
    lastHealthStatus: optionalString(read(value, 'lastHealthStatus', 'last_health_status')),
    lastHealthSummaryPresent: booleanValue(
      read(value, 'lastHealthSummaryPresent', 'last_health_summary_present'),
      false
    ),
    lastHealthSummaryDigest: optionalString(read(value, 'lastHealthSummaryDigest', 'last_health_summary_digest')),
    archivedAt: optionalString(read(value, 'archivedAt', 'archived_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function normalizeTestDataTask(input: unknown): TestDataTask {
  const value = objectValue(input);
  return {
    id: stringValue(read(value, 'id')),
    projectId: stringValue(read(value, 'projectId', 'project_id')),
    dataSetId: optionalString(read(value, 'dataSetId', 'data_set_id')),
    taskType: stringValue(read(value, 'taskType', 'task_type'), 'CLEANUP'),
    status: stringValue(read(value, 'status'), 'UNKNOWN'),
    requestKey: stringValue(read(value, 'requestKey', 'request_key')),
    targetRef: optionalString(read(value, 'targetRef', 'target_ref')),
    attempt: numberValue(read(value, 'attempt'), 0),
    resultSummary: sanitizeObject(read(value, 'resultSummary', 'result_summary')),
    errorCode: optionalString(read(value, 'errorCode', 'error_code')),
    errorSummary: optionalString(read(value, 'errorSummary', 'error_summary')),
    traceId: optionalString(read(value, 'traceId', 'trace_id')),
    policy: sanitizeObject(read(value, 'policy')),
    startedAt: optionalString(read(value, 'startedAt', 'started_at')),
    finishedAt: optionalString(read(value, 'finishedAt', 'finished_at')),
    createdAt: optionalString(read(value, 'createdAt', 'created_at')),
    updatedAt: optionalString(read(value, 'updatedAt', 'updated_at'))
  };
}

export function sanitizeSensitiveObject(input: unknown): Record<string, unknown> {
  return sanitizeObject(input);
}

function normalizePage<T>(input: unknown, normalizeItem: (item: unknown) => T): PageResponse<T> {
  const value = objectValue(input);
  return {
    items: arrayValue(read(value, 'items')).map(normalizeItem),
    index: numberValue(read(value, 'index'), 0),
    size: numberValue(read(value, 'size'), 20),
    total: numberValue(read(value, 'total'), 0)
  };
}

function sanitizeTestDataSetPayload(payload: Partial<TestDataSetPayload>) {
  const cleaned = cleanObject(payload);
  return {
    ...cleaned,
    schema: cleaned.schema ? sanitizeObject(cleaned.schema) : undefined,
    cleanupPolicy: cleaned.cleanupPolicy ? sanitizeObject(cleaned.cleanupPolicy) : undefined
  };
}

function sanitizeRecordImportPayload(payload: ImportTestDataRecordsPayload) {
  return {
    records: payload.records.map((record) => ({
      ...cleanObject(record),
      maskedSummary: record.maskedSummary ? sanitizeObject(record.maskedSummary) : undefined,
      tags: record.tags?.filter(Boolean)
    }))
  };
}

function sanitizeAccountPoolPayload(payload: Partial<TestAccountPoolPayload>) {
  const cleaned = cleanObject(payload);
  return {
    ...cleaned,
    leasePolicy: cleaned.leasePolicy ? sanitizeObject(cleaned.leasePolicy) : undefined
  };
}

function sanitizePooledAccountPayload(payload: Partial<TestPooledAccountPayload>, requireSecret: boolean) {
  const cleaned = cleanObject(payload);
  return {
    ...cleaned,
    roleTags: payload.roleTags?.filter(Boolean),
    scopeSummary: payload.scopeSummary ? sanitizeObject(payload.scopeSummary) : undefined,
    secretRef: requireSecret || payload.secretRef ? payload.secretRef : undefined
  };
}

function sanitizeLeasePayload(payload: AcquireTestAccountLeasePayload) {
  return {
    ...cleanObject(payload),
    roleTags: payload.roleTags?.filter(Boolean)
  };
}

function sanitizeTaskPayload(payload: Partial<TestDataTaskPayload | RetryTestDataTaskPayload>) {
  const cleaned = cleanObject(payload);
  return {
    ...cleaned,
    resultSummary: cleaned.resultSummary ? sanitizeObject(cleaned.resultSummary) : undefined
  };
}

function cleanObject(input: object): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(input).filter(([, value]) => value !== undefined && value !== null && value !== '')
  );
}

function sanitizeObject(input: unknown): Record<string, unknown> {
  const value = objectValue(input);
  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => !sensitiveKey(key))
      .map(([key, item]) => [key, sanitizeValue(item)])
  );
}

function sanitizePolicyObject(input: unknown): Record<string, unknown> {
  const value = objectValue(input);
  return Object.fromEntries(
    Object.entries(value)
      .filter(([key, item]) => !sensitiveKey(key) || typeof item === 'boolean' || typeof item === 'number')
      .map(([key, item]) => [key, sanitizeValue(item)])
  );
}

function sanitizeValue(input: unknown): unknown {
  if (Array.isArray(input)) {
    return input.map(sanitizeValue);
  }
  if (input && typeof input === 'object') {
    return sanitizeObject(input);
  }
  if (typeof input === 'string') {
    return input.replace(/secret:\/\/[^\s"'<>]+/gi, '[redacted]');
  }
  return input;
}

function sensitiveKey(key: string) {
  const normalized = key.toLowerCase();
  if (normalized.endsWith('digest') || normalized.includes('digest')) {
    return false;
  }
  return normalized.includes('password')
    || normalized.includes('passwd')
    || normalized.includes('token')
    || normalized.includes('cookie')
    || normalized.includes('credential')
    || normalized.includes('authorization')
    || normalized.includes('secret');
}

function queryString(filters: object) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

function objectValue(input: unknown): Record<string, unknown> {
  return input && typeof input === 'object' && !Array.isArray(input) ? input as Record<string, unknown> : {};
}

function arrayValue(input: unknown): unknown[] {
  return Array.isArray(input) ? input : [];
}

function read(value: Record<string, unknown>, primary: string, fallback?: string) {
  return value[primary] ?? (fallback ? value[fallback] : undefined);
}

function stringValue(input: unknown, fallback = '') {
  return typeof input === 'string' ? input : input == null ? fallback : String(input);
}

function optionalString(input: unknown) {
  const value = stringValue(input).trim();
  return value ? value : undefined;
}

function stringArray(input: unknown) {
  return Array.isArray(input) ? input.map((value) => stringValue(value)).filter(Boolean) : [];
}

function safeKeyArray(input: unknown) {
  return stringArray(input).filter((key) => !sensitiveKey(key));
}

function numberValue(input: unknown, fallback: number) {
  const value = typeof input === 'number' ? input : Number(input);
  return Number.isFinite(value) ? value : fallback;
}

function booleanValue(input: unknown, fallback: boolean) {
  if (typeof input === 'boolean') return input;
  if (input === 'true') return true;
  if (input === 'false') return false;
  return fallback;
}
