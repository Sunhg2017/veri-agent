import { expect, test, type Page, type Route } from '@playwright/test';

const wp8Permissions = [
  'testData:read',
  'testData:manage',
  'testData:lease',
  'testData:cleanup',
  'testData:export'
];

const rawSecretRef = 'secret://wp8/ui-smoke-admin';
const rawReleaseReason = 'browser smoke done';
const rawHealthSummary = 'Bearer raw health token';

const smokeViewports = [
  { name: 'desktop', width: 1280, height: 900, assertResponsive: false },
  { name: 'mobile', width: 390, height: 844, assertResponsive: true }
] as const;

for (const viewport of smokeViewports) {
  test(`WP8 test data browser smoke covers main flow on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await runWp8MainFlow(page, viewport.assertResponsive);
  });
}

async function runWp8MainFlow(page: Page, assertResponsive: boolean) {
  const mock = new Wp8TestDataMock();
  await mock.install(page);

  await page.addInitScript(() => {
    window.localStorage.setItem('veri-agent.access-token', 'wp8-ui-smoke-token');
  });

  await page.goto('/#test-data');

  await expect(page.getByRole('heading', { name: '测试数据' })).toBeVisible();
  await expect(page.getByTestId('test-data-workbench')).toBeVisible();
  await expect(page.locator('.test-data-policy-item').filter({ hasText: '脱敏导出' }).getByText('ENABLED')).toBeVisible();
  await expect(page.getByRole('tab', { name: '数据集' })).toBeVisible();
  await expect(page.getByRole('tab', { name: '账号池' })).toBeVisible();
  await expect(page.getByRole('tab', { name: '租借' })).toBeVisible();
  await expect(page.getByRole('tab', { name: '清理任务' })).toBeVisible();
  await expect(page.locator('body')).not.toContainText(rawSecretRef);

  const dataSetForm = page.locator('form.panel').filter({ hasText: /新建数据集|数据集表单/ }).first();
  await dataSetForm.getByLabel('projectId').fill('project-wp8-ui-smoke');
  await dataSetForm.getByLabel('code').fill('login-users-ui');
  await dataSetForm.getByLabel('名称').fill('WP8 UI smoke data set');
  await dataSetForm.getByLabel('schema JSON').fill('{"fields":[{"name":"username","type":"string"}]}');
  await dataSetForm.getByLabel('cleanupPolicy JSON').fill('{"mode":"MANUAL","ttlSeconds":3600}');
  await dataSetForm.getByRole('button', { name: '创建' }).click();
  await expect(page.getByRole('row', { name: /WP8 UI smoke data set/ })).toBeVisible();
  expect(mock.createDataSetPayload).toMatchObject({
    projectId: 'project-wp8-ui-smoke',
    code: 'login-users-ui',
    name: 'WP8 UI smoke data set',
    cleanupPolicy: { mode: 'MANUAL', ttlSeconds: 3600 }
  });

  const recordForm = page.locator('form.test-data-subform').filter({ hasText: '导入记录摘要' }).first();
  await recordForm.getByLabel('recordKey').fill('admin-record');
  await recordForm.getByLabel('recordDigest').fill('sha256:record-ui');
  await recordForm.getByLabel('maskedSummary JSON').fill('{"username":"admin","password":"must-not-render"}');
  await recordForm.getByRole('button', { name: '导入' }).click();
  await expect(page.locator('.test-data-record-card').filter({ hasText: 'admin-record' }).first()).toBeVisible();
  expect(JSON.stringify(mock.importRecordsPayload)).not.toContain('must-not-render');

  const exportPanel = page.getByTestId('test-data-export-panel');
  await exportPanel.getByRole('button', { name: '导出摘要' }).click();
  await expect(exportPanel.getByText('wp8-data-set-export-v1')).toBeVisible();
  await expect(exportPanel.getByText('maskedSummaryValuesExported')).toBeVisible();
  await expect(exportPanel.getByText('keys username')).toBeVisible();
  expect(mock.exportSeen).toBe(true);
  const dataSetDownload = page.waitForEvent('download');
  await exportPanel.getByRole('button', { name: '下载文件' }).click();
  await dataSetDownload;
  expect(mock.exportDownloadSeen).toBe(true);
  await expect(page.locator('body')).not.toContainText('secret://');
  await expect(page.locator('body')).not.toContainText('must-not-render');

  await page.getByRole('tab', { name: '账号池' }).click();
  const poolForm = page.locator('form.panel').filter({ hasText: '账号池表单' }).first();
  await poolForm.getByLabel('projectId').fill('project-wp8-ui-smoke');
  await poolForm.getByLabel('code').fill('admin-pool-ui');
  await poolForm.getByLabel('名称').fill('WP8 UI smoke account pool');
  await poolForm.getByLabel('leasePolicy JSON').fill('{"maxConcurrentLeases":1,"sharing":"EXCLUSIVE"}');
  await poolForm.getByRole('button', { name: '创建' }).click();
  await expect(page.locator('.test-data-list-item').filter({ hasText: 'WP8 UI smoke account pool' }).first()).toBeVisible();

  const accountForm = page.locator('form.test-data-subform').filter({ hasText: /新增账号摘要|编辑账号摘要/ }).first();
  await accountForm.getByLabel('accountKey').fill('admin-ui-01');
  await accountForm.getByLabel('displayName').fill('Admin UI 01');
  await accountForm.getByLabel('roleTags').fill('ADMIN, APPROVER');
  await accountForm.getByLabel('secretRef').fill(rawSecretRef);
  await accountForm.getByLabel('scopeSummary JSON').fill('{"tenant":"alpha","token":"must-not-render"}');
  await accountForm.getByRole('button', { name: '保存账号' }).click();
  await expect(page.locator('.test-data-account-card').filter({ hasText: 'Admin UI 01' }).first()).toBeVisible();
  await expect(page.getByText('secret digest sha256:sec')).toBeVisible();
  await expect(page.getByLabel('secretRef')).toHaveValue('');
  expect(mock.accountPayload).toMatchObject({
    accountKey: 'admin-ui-01',
    secretRef: rawSecretRef,
    scopeSummary: { tenant: 'alpha' }
  });
  expect(await page.locator('body').innerText()).not.toContain(rawSecretRef);
  expect(await page.locator('body').innerText()).not.toContain('must-not-render');

  await page.getByRole('tab', { name: '租借' }).click();
  const leaseForm = page.locator('form.panel').filter({ hasText: '申请租借' }).first();
  await leaseForm.getByLabel('projectId').fill('project-wp8-ui-smoke');
  await leaseForm.getByLabel('poolId').fill('pool-ui-created');
  await leaseForm.getByLabel('holderType').fill('EXECUTION_RUN');
  await leaseForm.getByLabel('holderRef').fill('run-ui-smoke');
  await leaseForm.getByLabel('requestKey').fill('lease-run-ui-smoke');
  await leaseForm.getByLabel('ttlSeconds').fill('900');
  await leaseForm.getByRole('button', { name: '申请' }).click();
  await expect(page.locator('.test-data-list-item').filter({ hasText: 'run-ui-smoke' }).first()).toBeVisible();
  await expect(page.getByText('secretDigest')).toBeVisible();
  expect(mock.leasePayload).toMatchObject({
    projectId: 'project-wp8-ui-smoke',
    poolId: 'pool-ui-created',
    holderType: 'EXECUTION_RUN',
    holderRef: 'run-ui-smoke',
    requestKey: 'lease-run-ui-smoke',
    ttlSeconds: 900
  });

  const leaseRecordPanel = page.locator('section.panel').filter({ hasText: '租借记录' }).first();
  await leaseRecordPanel.getByLabel('续租 TTL').fill('1200');
  await leaseRecordPanel.getByRole('button', { name: '续租' }).click();
  await expect(page.locator('.test-data-list-item').filter({ hasText: '2026-06-15 01:20:00' }).first()).toBeVisible();
  await leaseRecordPanel.getByLabel('释放原因').fill(rawReleaseReason);
  await leaseRecordPanel.getByRole('button', { name: '释放' }).click();
  await expect(page.locator('.test-data-list-item').filter({ hasText: 'RELEASED' }).first()).toBeVisible();
  expect(mock.releasePayload).toMatchObject({
    releaseReason: rawReleaseReason,
    accountStatus: 'AVAILABLE'
  });

  const leaseExportPanel = page.getByTestId('test-lease-export-panel');
  await leaseExportPanel.getByRole('button', { name: '导出摘要' }).click();
  await expect(leaseExportPanel.getByText('wp8-account-lease-export-v1')).toBeVisible();
  await expect(leaseExportPanel.getByText('leaseTokenPlaintextExported')).toBeVisible();
  await expect(leaseExportPanel.getByText('scopeKeys')).toBeVisible();
  await expect(leaseExportPanel.getByText('tenant')).toBeVisible();
  await expect(leaseExportPanel.getByText('healthSummary')).toBeVisible();
  await expect(leaseExportPanel.getByText('digest only')).toBeVisible();
  expect(mock.leaseExportSeen).toBe(true);
  const leaseDownload = page.waitForEvent('download');
  await leaseExportPanel.getByRole('button', { name: '下载文件' }).click();
  await leaseDownload;
  expect(mock.leaseExportDownloadSeen).toBe(true);
  await expect(leaseExportPanel).not.toContainText(rawReleaseReason);
  await expect(leaseExportPanel).not.toContainText(rawHealthSummary);

  await page.getByRole('tab', { name: '清理任务' }).click();
  await expect(page.getByText('cleanupEnabled=false')).toBeVisible();
  const taskForm = page.locator('form.panel').filter({ hasText: '清理任务' }).first();
  await taskForm.getByLabel('projectId').fill('project-wp8-ui-smoke');
  await taskForm.getByLabel('dataSetId').fill('ds-ui-created');
  await taskForm.getByLabel('requestKey').fill('cleanup-ui-smoke');
  await taskForm.getByLabel('targetRef').fill('dataset:ds-ui-created');
  await taskForm.getByLabel('resultSummary JSON').fill('{"deleted":0,"authorization":"Bearer raw"}');
  await taskForm.getByRole('button', { name: '创建' }).click();
  await expect(page.locator('.test-data-list-item').filter({ hasText: 'cleanup-ui-smoke' }).first()).toBeVisible();
  await expect(page.getByText('trace-task-detail')).toBeVisible();
  expect(mock.taskPayload).toMatchObject({
    projectId: 'project-wp8-ui-smoke',
    dataSetId: 'ds-ui-created',
    taskType: 'CLEANUP',
    requestKey: 'cleanup-ui-smoke',
    resultSummary: { deleted: 0 }
  });

  const taskListPanel = page.locator('section.panel').filter({ hasText: '任务列表' }).first();
  await taskListPanel.getByLabel('retry requestKey').fill('cleanup-ui-smoke-retry');
  await taskListPanel.getByRole('button', { name: '重试' }).click();
  await expect(page.locator('.test-data-list-item').filter({ hasText: 'cleanup-ui-smoke-retry' }).first()).toBeVisible();
  expect(mock.retryPayload).toMatchObject({
    requestKey: 'cleanup-ui-smoke-retry'
  });

  await assertNoRawSecret(page);

  if (assertResponsive) {
    await expectNoHorizontalOverflow(page, '[data-testid="test-data-workbench"]');
    await expect(page.getByRole('tab', { name: '清理任务' })).toBeVisible();
    await expect(page.locator('.test-data-list-item').filter({ hasText: 'cleanup-ui-smoke-retry' }).first()).toBeVisible();
  }
}

async function assertNoRawSecret(page: Page) {
  const bodyText = await page.locator('body').innerText();
  expect(bodyText).not.toContain(rawSecretRef);
  expect(bodyText).not.toContain('secret://');
  expect(bodyText).not.toContain('must-not-render');
}

async function expectNoHorizontalOverflow(page: Page, selector: string) {
  const overflow = await page.locator(selector).evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return rect.left < -1 || rect.right > window.innerWidth + 1 || document.documentElement.scrollWidth > window.innerWidth + 1;
  });
  expect(overflow).toBe(false);
}

class Wp8TestDataMock {
  createDataSetPayload: Record<string, unknown> | undefined;
  importRecordsPayload: Record<string, unknown> | undefined;
  createPoolPayload: Record<string, unknown> | undefined;
  accountPayload: Record<string, unknown> | undefined;
  leasePayload: Record<string, unknown> | undefined;
  releasePayload: Record<string, unknown> | undefined;
  taskPayload: Record<string, unknown> | undefined;
  retryPayload: Record<string, unknown> | undefined;
  exportSeen = false;
  exportDownloadSeen = false;
  leaseExportSeen = false;
  leaseExportDownloadSeen = false;

  private dataSets: Array<Record<string, unknown>> = [this.dataSetSummary('ds-ui-1', 'Baseline WP8 smoke data set', 'baseline-users')];
  private dataSetDetails = new Map<string, Record<string, unknown>>([
    ['ds-ui-1', this.dataSetDetail('ds-ui-1', 'Baseline WP8 smoke data set', 'baseline-users')]
  ]);
  private accountPools: Array<Record<string, unknown>> = [this.poolSummary('pool-ui-1', 'Baseline WP8 smoke pool', 'baseline-pool')];
  private poolDetails = new Map<string, Record<string, unknown>>([
    ['pool-ui-1', this.poolDetail('pool-ui-1', 'Baseline WP8 smoke pool', 'baseline-pool')]
  ]);
  private leases: Array<Record<string, unknown>> = [this.leaseDetail('lease-ui-1', 'ACTIVE')];
  private leaseDetails = new Map<string, Record<string, unknown>>([
    ['lease-ui-1', this.leaseDetail('lease-ui-1', 'ACTIVE')]
  ]);
  private tasks: Array<Record<string, unknown>> = [this.taskDetail('task-ui-1', 'FAILED')];
  private taskDetails = new Map<string, Record<string, unknown>>([
    ['task-ui-1', this.taskDetail('task-ui-1', 'FAILED')]
  ]);

  async install(page: Page) {
    await page.route('**/api/v1/**', (route) => this.handle(route));
  }

  private async handle(route: Route) {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/v1/health') {
      return this.fulfill(route, {
        service: 'platform-api',
        status: 'UP',
        timestamp: '2026-06-15T00:00:00Z'
      }, 'trace-platform-health');
    }

    if (path === '/api/v1/auth/me') {
      return this.fulfill(route, {
        user_id: 'user-wp8-ui-smoke',
        username: 'wp8-ui-smoke',
        display_name: 'WP8 UI Smoke',
        must_change_password: false,
        roles: ['TestDataOwner'],
        permissions: wp8Permissions
      }, 'trace-auth-me');
    }

    if (path === '/api/v1/test-data/health') {
      return this.fulfill(route, {
        service: 'test-data',
        status: 'UP',
        enabled: true,
        cleanupEnabled: false,
        exportEnabled: true,
        recordMaxCount: 1000,
        recordSummaryMaxBytes: 2048,
        defaultLeaseTtlSeconds: 1800,
        maxLeaseTtlSeconds: 7200,
        policy: {
          secretRefPlaintextReturned: false,
          rawRecordExported: false,
          cleanupWorkerEnabled: false
        }
      }, 'trace-test-data-health');
    }

    if (path === '/api/v1/test-data/data-sets' && method === 'GET') {
      return this.fulfill(route, this.page(this.dataSets), 'trace-data-set-list');
    }

    if (path === '/api/v1/test-data/data-sets' && method === 'POST') {
      this.createDataSetPayload = request.postDataJSON() as Record<string, unknown>;
      const detail = this.dataSetFromPayload('ds-ui-created', this.createDataSetPayload);
      this.dataSets = [this.dataSetSummaryFromDetail(detail), ...this.dataSets.filter((item) => item.id !== detail.id)];
      this.dataSetDetails.set(String(detail.id), detail);
      return this.fulfill(route, detail, 'trace-data-set-create');
    }

    const dataSetDetailMatch = path.match(/^\/api\/v1\/test-data\/data-sets\/([^/]+)$/);
    if (dataSetDetailMatch && method === 'GET') {
      return this.fulfill(route, this.dataSetDetails.get(dataSetDetailMatch[1]) ?? this.dataSetDetail(dataSetDetailMatch[1], 'Baseline WP8 smoke data set', 'baseline-users'), 'trace-data-set-detail');
    }

    if (dataSetDetailMatch && method === 'PATCH') {
      const payload = request.postDataJSON() as Record<string, unknown>;
      const current = this.dataSetDetails.get(dataSetDetailMatch[1]) ?? this.dataSetDetail(dataSetDetailMatch[1], 'Baseline WP8 smoke data set', 'baseline-users');
      const detail = { ...current, ...payload, updatedAt: '2026-06-15T00:05:00Z' };
      this.dataSetDetails.set(dataSetDetailMatch[1], detail);
      this.dataSets = this.dataSets.map((item) => item.id === detail.id ? this.dataSetSummaryFromDetail(detail) : item);
      return this.fulfill(route, detail, 'trace-data-set-update');
    }

    const dataSetArchiveMatch = path.match(/^\/api\/v1\/test-data\/data-sets\/([^/]+)\/archive$/);
    if (dataSetArchiveMatch && method === 'POST') {
      const current = this.dataSetDetails.get(dataSetArchiveMatch[1]) ?? this.dataSetDetail(dataSetArchiveMatch[1], 'Baseline WP8 smoke data set', 'baseline-users');
      const detail = { ...current, status: 'ARCHIVED', archivedAt: '2026-06-15T00:06:00Z' };
      this.dataSetDetails.set(dataSetArchiveMatch[1], detail);
      this.dataSets = this.dataSets.map((item) => item.id === detail.id ? this.dataSetSummaryFromDetail(detail) : item);
      return this.fulfill(route, detail, 'trace-data-set-archive');
    }

    const recordsMatch = path.match(/^\/api\/v1\/test-data\/data-sets\/([^/]+)\/records$/);
    if (recordsMatch && method === 'POST') {
      this.importRecordsPayload = request.postDataJSON() as Record<string, unknown>;
      const record = this.record('rec-ui-created', recordsMatch[1], objectValue(arrayValue(this.importRecordsPayload.records)[0]));
      const current = this.dataSetDetails.get(recordsMatch[1]) ?? this.dataSetDetail(recordsMatch[1], 'WP8 UI smoke data set', 'login-users-ui');
      const detail = {
        ...current,
        recordCount: numberValue(current.recordCount) + 1,
        records: [record, ...arrayValue(current.records)]
      };
      this.dataSetDetails.set(recordsMatch[1], detail);
      this.dataSets = this.dataSets.map((item) => item.id === detail.id ? this.dataSetSummaryFromDetail(detail) : item);
      return this.fulfill(route, {
        dataSetId: recordsMatch[1],
        importedCount: 1,
        records: [record],
        policy: { rawPayloadStored: false }
      }, 'trace-record-import');
    }

    const dataSetExportMatch = path.match(/^\/api\/v1\/test-data\/data-sets\/([^/]+)\/export$/);
    if (dataSetExportMatch && method === 'GET') {
      this.exportSeen = true;
      return this.fulfill(route, this.dataSetExport(dataSetExportMatch[1]), 'trace-data-set-export');
    }

    const dataSetExportDownloadMatch = path.match(/^\/api\/v1\/test-data\/data-sets\/([^/]+)\/export\/download$/);
    if (dataSetExportDownloadMatch && method === 'GET') {
      this.exportDownloadSeen = true;
      return this.fulfillDownload(
        route,
        this.dataSetExport(dataSetExportDownloadMatch[1]),
        'wp8-data-set-export.json',
        'trace-data-set-export-download'
      );
    }

    if (path === '/api/v1/test-data/account-pools' && method === 'GET') {
      return this.fulfill(route, this.page(this.accountPools), 'trace-pool-list');
    }

    if (path === '/api/v1/test-data/account-pools' && method === 'POST') {
      this.createPoolPayload = request.postDataJSON() as Record<string, unknown>;
      const detail = this.poolFromPayload('pool-ui-created', this.createPoolPayload);
      this.accountPools = [this.poolSummaryFromDetail(detail), ...this.accountPools.filter((item) => item.id !== detail.id)];
      this.poolDetails.set(String(detail.id), detail);
      return this.fulfill(route, detail, 'trace-pool-create');
    }

    const poolDetailMatch = path.match(/^\/api\/v1\/test-data\/account-pools\/([^/]+)$/);
    if (poolDetailMatch && method === 'GET') {
      return this.fulfill(route, this.poolDetails.get(poolDetailMatch[1]) ?? this.poolDetail(poolDetailMatch[1], 'Baseline WP8 smoke pool', 'baseline-pool'), 'trace-pool-detail');
    }

    if (poolDetailMatch && method === 'PATCH') {
      const payload = request.postDataJSON() as Record<string, unknown>;
      const current = this.poolDetails.get(poolDetailMatch[1]) ?? this.poolDetail(poolDetailMatch[1], 'Baseline WP8 smoke pool', 'baseline-pool');
      const detail = { ...current, ...payload, updatedAt: '2026-06-15T00:07:00Z' };
      this.poolDetails.set(poolDetailMatch[1], detail);
      this.accountPools = this.accountPools.map((item) => item.id === detail.id ? this.poolSummaryFromDetail(detail) : item);
      return this.fulfill(route, detail, 'trace-pool-update');
    }

    const poolDisableMatch = path.match(/^\/api\/v1\/test-data\/account-pools\/([^/]+)\/disable$/);
    if (poolDisableMatch && method === 'POST') {
      return this.updatePoolStatus(route, poolDisableMatch[1], 'DISABLED', 'trace-pool-disable');
    }

    const poolArchiveMatch = path.match(/^\/api\/v1\/test-data\/account-pools\/([^/]+)\/archive$/);
    if (poolArchiveMatch && method === 'POST') {
      return this.updatePoolStatus(route, poolArchiveMatch[1], 'ARCHIVED', 'trace-pool-archive');
    }

    const addAccountMatch = path.match(/^\/api\/v1\/test-data\/account-pools\/([^/]+)\/accounts$/);
    if (addAccountMatch && method === 'POST') {
      this.accountPayload = request.postDataJSON() as Record<string, unknown>;
      const current = this.poolDetails.get(addAccountMatch[1]) ?? this.poolDetail(addAccountMatch[1], 'WP8 UI smoke account pool', 'admin-pool-ui');
      const account = this.accountFromPayload('acc-ui-created', addAccountMatch[1], this.accountPayload);
      const detail = {
        ...current,
        accountCount: numberValue(current.accountCount) + 1,
        availableAccountCount: numberValue(current.availableAccountCount) + 1,
        accounts: [account, ...arrayValue(current.accounts)]
      };
      this.poolDetails.set(addAccountMatch[1], detail);
      this.accountPools = this.accountPools.map((item) => item.id === detail.id ? this.poolSummaryFromDetail(detail) : item);
      return this.fulfill(route, account, 'trace-account-save');
    }

    const updateAccountMatch = path.match(/^\/api\/v1\/test-data\/accounts\/([^/]+)$/);
    if (updateAccountMatch && method === 'PATCH') {
      this.accountPayload = request.postDataJSON() as Record<string, unknown>;
      const account = this.accountFromPayload(updateAccountMatch[1], 'pool-ui-created', this.accountPayload);
      return this.fulfill(route, account, 'trace-account-update');
    }

    if (path === '/api/v1/test-data/leases' && method === 'GET') {
      return this.fulfill(route, this.page(this.leases), 'trace-lease-list');
    }

    if (path === '/api/v1/test-data/leases' && method === 'POST') {
      this.leasePayload = request.postDataJSON() as Record<string, unknown>;
      const lease = this.leaseFromPayload('lease-ui-created', 'ACTIVE', this.leasePayload);
      this.leases = [lease, ...this.leases.filter((item) => item.id !== lease.id)];
      this.leaseDetails.set(String(lease.id), lease);
      return this.fulfill(route, lease, 'trace-lease-create');
    }

    const leaseDetailMatch = path.match(/^\/api\/v1\/test-data\/leases\/([^/]+)$/);
    if (leaseDetailMatch && method === 'GET') {
      return this.fulfill(route, this.leaseDetails.get(leaseDetailMatch[1]) ?? this.leaseDetail(leaseDetailMatch[1], 'ACTIVE'), 'trace-lease-detail');
    }

    const leaseExportMatch = path.match(/^\/api\/v1\/test-data\/leases\/([^/]+)\/export$/);
    if (leaseExportMatch && method === 'GET') {
      this.leaseExportSeen = true;
      return this.fulfill(route, this.leaseExport(leaseExportMatch[1]), 'trace-lease-export');
    }

    const leaseExportDownloadMatch = path.match(/^\/api\/v1\/test-data\/leases\/([^/]+)\/export\/download$/);
    if (leaseExportDownloadMatch && method === 'GET') {
      this.leaseExportDownloadSeen = true;
      return this.fulfillDownload(
        route,
        this.leaseExport(leaseExportDownloadMatch[1]),
        'wp8-account-lease-export.json',
        'trace-lease-export-download'
      );
    }

    const renewMatch = path.match(/^\/api\/v1\/test-data\/leases\/([^/]+)\/renew$/);
    if (renewMatch && method === 'POST') {
      const current = this.leaseDetails.get(renewMatch[1]) ?? this.leaseDetail(renewMatch[1], 'ACTIVE');
      const lease = { ...current, expiresAt: '2026-06-15T01:20:00Z', updatedAt: '2026-06-15T00:08:00Z' };
      this.leaseDetails.set(renewMatch[1], lease);
      this.leases = this.leases.map((item) => item.id === lease.id ? lease : item);
      return this.fulfill(route, lease, 'trace-lease-renew');
    }

    const releaseMatch = path.match(/^\/api\/v1\/test-data\/leases\/([^/]+)\/release$/);
    if (releaseMatch && method === 'POST') {
      this.releasePayload = request.postDataJSON() as Record<string, unknown>;
      const current = this.leaseDetails.get(releaseMatch[1]) ?? this.leaseDetail(releaseMatch[1], 'ACTIVE');
      const lease = {
        ...current,
        status: 'RELEASED',
        releasedAt: '2026-06-15T00:09:00Z',
        releaseReason: stringValue(this.releasePayload.releaseReason, rawReleaseReason),
        updatedAt: '2026-06-15T00:09:00Z'
      };
      this.leaseDetails.set(releaseMatch[1], lease);
      this.leases = this.leases.map((item) => item.id === lease.id ? lease : item);
      return this.fulfill(route, lease, 'trace-lease-release');
    }

    if (path === '/api/v1/test-data/data-tasks' && method === 'GET') {
      return this.fulfill(route, this.page(this.tasks), 'trace-task-list');
    }

    if (path === '/api/v1/test-data/data-tasks' && method === 'POST') {
      this.taskPayload = request.postDataJSON() as Record<string, unknown>;
      const task = this.taskFromPayload('task-ui-created', 'PENDING', this.taskPayload, 'trace-cleanup-create');
      this.tasks = [task, ...this.tasks.filter((item) => item.id !== task.id)];
      this.taskDetails.set(String(task.id), task);
      return this.fulfill(route, task, 'trace-cleanup-create');
    }

    const taskDetailMatch = path.match(/^\/api\/v1\/test-data\/data-tasks\/([^/]+)$/);
    if (taskDetailMatch && method === 'GET') {
      return this.fulfill(route, this.taskDetails.get(taskDetailMatch[1]) ?? this.taskDetail(taskDetailMatch[1], 'FAILED'), 'trace-task-detail');
    }

    const taskRetryMatch = path.match(/^\/api\/v1\/test-data\/data-tasks\/([^/]+)\/retry$/);
    if (taskRetryMatch && method === 'POST') {
      this.retryPayload = request.postDataJSON() as Record<string, unknown>;
      const current = this.taskDetails.get(taskRetryMatch[1]) ?? this.taskDetail(taskRetryMatch[1], 'FAILED');
      const task = {
        ...current,
        status: 'PENDING',
        requestKey: stringValue(this.retryPayload.requestKey, 'cleanup-ui-smoke-retry'),
        attempt: numberValue(current.attempt) + 1,
        traceId: 'trace-cleanup-retry',
        errorCode: undefined,
        errorSummary: undefined,
        updatedAt: '2026-06-15T00:10:00Z'
      };
      this.taskDetails.set(taskRetryMatch[1], task);
      this.tasks = this.tasks.map((item) => item.id === task.id ? task : item);
      return this.fulfill(route, task, 'trace-cleanup-retry');
    }

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'NOT_FOUND',
        message: `Unhandled WP8 smoke mock route: ${method} ${path}`,
        trace_id: 'trace-unhandled',
        data: {}
      })
    });
  }

  private updatePoolStatus(route: Route, id: string, status: string, traceId: string) {
    const current = this.poolDetails.get(id) ?? this.poolDetail(id, 'Baseline WP8 smoke pool', 'baseline-pool');
    const detail = { ...current, status, updatedAt: '2026-06-15T00:07:00Z' };
    this.poolDetails.set(id, detail);
    this.accountPools = this.accountPools.map((item) => item.id === detail.id ? this.poolSummaryFromDetail(detail) : item);
    return this.fulfill(route, detail, traceId);
  }

  private fulfillDownload(route: Route, data: Record<string, unknown>, filename: string, traceId: string) {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'X-Trace-Id': traceId,
        'Content-Disposition': `attachment; filename="${filename}"`
      },
      body: JSON.stringify(data)
    });
  }

  private dataSetSummary(id: string, name: string, code: string) {
    return this.dataSetSummaryFromDetail(this.dataSetDetail(id, name, code));
  }

  private dataSetDetail(id: string, name: string, code: string) {
    return {
      id,
      projectId: 'project-wp8-ui-smoke',
      applicationId: 'app-ui',
      environmentId: 'staging',
      code,
      name,
      status: 'ACTIVE',
      sensitivityLevel: 'INTERNAL',
      sourceType: 'MANUAL',
      sourceRefDigest: 'sha256:source-ui',
      recordCount: 1,
      cleanupPolicy: { mode: 'MANUAL' },
      schema: { fields: [{ name: 'username', type: 'string' }] },
      records: [this.record('rec-ui-1', id, {
        recordKey: 'baseline-admin',
        recordDigest: 'sha256:record-baseline',
        maskedSummary: { username: 'admin' },
        tags: ['baseline']
      })],
      policy: { rawPayloadStored: false },
      createdAt: '2026-06-15T00:00:00Z',
      updatedAt: '2026-06-15T00:00:00Z'
    };
  }

  private dataSetFromPayload(id: string, payload: Record<string, unknown>) {
    return {
      id,
      projectId: stringValue(payload.projectId),
      applicationId: optionalString(payload.applicationId),
      environmentId: optionalString(payload.environmentId),
      code: stringValue(payload.code),
      name: stringValue(payload.name),
      status: stringValue(payload.status, 'ACTIVE'),
      sensitivityLevel: stringValue(payload.sensitivityLevel, 'INTERNAL'),
      sourceType: stringValue(payload.sourceType, 'MANUAL'),
      sourceRefDigest: optionalString(payload.sourceRefDigest),
      recordCount: 0,
      cleanupPolicy: objectValue(payload.cleanupPolicy),
      schema: objectValue(payload.schema),
      records: [],
      policy: { rawPayloadStored: false },
      createdAt: '2026-06-15T00:04:00Z',
      updatedAt: '2026-06-15T00:04:00Z'
    };
  }

  private dataSetSummaryFromDetail(detail: Record<string, unknown>) {
    return {
      id: detail.id,
      projectId: detail.projectId,
      applicationId: detail.applicationId,
      environmentId: detail.environmentId,
      code: detail.code,
      name: detail.name,
      status: detail.status,
      sensitivityLevel: detail.sensitivityLevel,
      sourceType: detail.sourceType,
      sourceRefDigest: detail.sourceRefDigest,
      recordCount: detail.recordCount,
      cleanupPolicy: detail.cleanupPolicy,
      archivedAt: detail.archivedAt,
      createdAt: detail.createdAt,
      updatedAt: detail.updatedAt
    };
  }

  private dataSetExport(id: string) {
    const detail = this.dataSetDetails.get(id) ?? this.dataSetDetail(id, 'Baseline WP8 smoke data set', 'baseline-users');
    const records = arrayValue(detail.records).map((item) => {
      const record = objectValue(item);
      return {
        recordKey: stringValue(record.recordKey),
        recordDigest: stringValue(record.recordDigest),
        externalRefDigest: optionalString(record.externalRefDigest),
        tags: stringArray(record.tags),
        maskedSummaryKeys: Object.keys(objectValue(record.maskedSummary)),
        createdAt: optionalString(record.createdAt),
        updatedAt: optionalString(record.updatedAt)
      };
    });
    return {
      schemaVersion: 'wp8-data-set-export-v1',
      exportedAt: '2026-06-15T00:11:00Z',
      dataSet: this.dataSetSummaryFromDetail(detail),
      recordCount: records.length,
      schemaFieldCount: arrayValue(objectValue(detail.schema).fields).length,
      sensitiveFieldCount: 0,
      records,
      redactionPolicy: {
        rawRecordPayloadExported: false,
        maskedSummaryValuesExported: false,
        secretRefPlaintextExported: false,
        authorizationHeaderExported: false,
        recordDigestExported: true,
        tagValuesExported: true
      }
    };
  }

  private record(id: string, dataSetId: string, payload: Record<string, unknown>) {
    return {
      id,
      dataSetId,
      projectId: 'project-wp8-ui-smoke',
      recordKey: stringValue(payload.recordKey, 'admin-record'),
      status: 'ACTIVE',
      recordDigest: stringValue(payload.recordDigest, 'sha256:record-ui'),
      maskedSummary: objectValue(payload.maskedSummary),
      externalRefDigest: optionalString(payload.externalRefDigest),
      tags: stringArray(payload.tags),
      createdAt: '2026-06-15T00:05:00Z',
      updatedAt: '2026-06-15T00:05:00Z'
    };
  }

  private poolSummary(id: string, name: string, code: string) {
    return this.poolSummaryFromDetail(this.poolDetail(id, name, code));
  }

  private poolDetail(id: string, name: string, code: string) {
    return {
      id,
      projectId: 'project-wp8-ui-smoke',
      applicationId: 'app-ui',
      environmentId: 'staging',
      code,
      name,
      status: 'ACTIVE',
      leasePolicy: { maxConcurrentLeases: 1 },
      defaultTtlSeconds: 1800,
      accountCount: 1,
      availableAccountCount: 1,
      lockedAccountCount: 0,
      disabledAccountCount: 0,
      accounts: [this.accountFromPayload('acc-ui-1', id, {
        accountKey: 'baseline-admin',
        displayName: 'Baseline Admin',
        status: 'AVAILABLE',
        roleTags: ['ADMIN'],
        scopeSummary: { tenant: 'alpha' }
      })],
      policy: { secretRefPlaintextReturned: false },
      createdAt: '2026-06-15T00:00:00Z',
      updatedAt: '2026-06-15T00:00:00Z'
    };
  }

  private poolFromPayload(id: string, payload: Record<string, unknown>) {
    return {
      id,
      projectId: stringValue(payload.projectId),
      applicationId: optionalString(payload.applicationId),
      environmentId: optionalString(payload.environmentId),
      code: stringValue(payload.code),
      name: stringValue(payload.name),
      status: stringValue(payload.status, 'ACTIVE'),
      leasePolicy: objectValue(payload.leasePolicy),
      defaultTtlSeconds: numberValue(payload.defaultTtlSeconds, 1800),
      accountCount: 0,
      availableAccountCount: 0,
      lockedAccountCount: 0,
      disabledAccountCount: 0,
      accounts: [],
      policy: { secretRefPlaintextReturned: false },
      createdAt: '2026-06-15T00:06:00Z',
      updatedAt: '2026-06-15T00:06:00Z'
    };
  }

  private poolSummaryFromDetail(detail: Record<string, unknown>) {
    return {
      id: detail.id,
      projectId: detail.projectId,
      applicationId: detail.applicationId,
      environmentId: detail.environmentId,
      code: detail.code,
      name: detail.name,
      status: detail.status,
      leasePolicy: detail.leasePolicy,
      defaultTtlSeconds: detail.defaultTtlSeconds,
      accountCount: detail.accountCount,
      availableAccountCount: detail.availableAccountCount,
      lockedAccountCount: detail.lockedAccountCount,
      disabledAccountCount: detail.disabledAccountCount,
      archivedAt: detail.archivedAt,
      createdAt: detail.createdAt,
      updatedAt: detail.updatedAt
    };
  }

  private accountFromPayload(id: string, poolId: string, payload: Record<string, unknown>) {
    return {
      id,
      poolId,
      projectId: 'project-wp8-ui-smoke',
      accountKey: stringValue(payload.accountKey, 'admin-ui-01'),
      displayName: optionalString(payload.displayName) ?? stringValue(payload.accountKey, 'admin-ui-01'),
      status: stringValue(payload.status, 'AVAILABLE'),
      roleTags: stringArray(payload.roleTags),
      scopeSummary: objectValue(payload.scopeSummary),
      secretRefDigest: 'sha256:secret-ui-digest',
      lastHealthStatus: optionalString(payload.lastHealthStatus) ?? 'HEALTHY',
      lastHealthSummary: optionalString(payload.lastHealthSummary) ?? 'browser smoke ready',
      createdAt: '2026-06-15T00:07:00Z',
      updatedAt: '2026-06-15T00:07:00Z'
    };
  }

  private leaseDetail(id: string, status: string) {
    return {
      id,
      poolId: 'pool-ui-created',
      accountId: 'acc-ui-created',
      projectId: 'project-wp8-ui-smoke',
      status,
      holderType: 'EXECUTION_RUN',
      holderRef: 'run-ui-baseline',
      requestKey: `request-${id}`,
      leaseTokenDigest: 'sha256:lease-token-ui',
      expiresAt: '2026-06-15T01:00:00Z',
      releasedAt: status === 'RELEASED' ? '2026-06-15T00:09:00Z' : undefined,
      releaseReason: status === 'RELEASED' ? 'browser smoke done' : undefined,
      account: this.accountFromPayload('acc-ui-created', 'pool-ui-created', {
        accountKey: 'admin-ui-01',
        displayName: 'Admin UI 01',
        status: 'LEASED',
        roleTags: ['ADMIN'],
        scopeSummary: { tenant: 'alpha', token: 'must-not-render' },
        lastHealthSummary: rawHealthSummary
      }),
      policy: { leaseTokenReturned: false },
      createdAt: '2026-06-15T00:00:00Z',
      updatedAt: '2026-06-15T00:00:00Z'
    };
  }

  private leaseFromPayload(id: string, status: string, payload: Record<string, unknown>) {
    return {
      ...this.leaseDetail(id, status),
      poolId: stringValue(payload.poolId, 'pool-ui-created'),
      projectId: stringValue(payload.projectId, 'project-wp8-ui-smoke'),
      holderType: stringValue(payload.holderType, 'EXECUTION_RUN'),
      holderRef: stringValue(payload.holderRef, 'run-ui-smoke'),
      requestKey: stringValue(payload.requestKey, 'lease-run-ui-smoke'),
      expiresAt: '2026-06-15T01:15:00Z',
      createdAt: '2026-06-15T00:08:00Z',
      updatedAt: '2026-06-15T00:08:00Z'
    };
  }

  private leaseExport(id: string) {
    const lease = this.leaseDetails.get(id) ?? this.leaseDetail(id, 'ACTIVE');
    const account = objectValue(lease.account);
    return {
      schemaVersion: 'wp8-account-lease-export-v1',
      exportedAt: '2026-06-15T00:12:00Z',
      lease: {
        id: lease.id,
        poolId: lease.poolId,
        accountId: lease.accountId,
        projectId: lease.projectId,
        status: lease.status,
        holderType: lease.holderType,
        holderRef: lease.holderRef,
        requestKey: lease.requestKey,
        requestDigest: 'sha256:request-ui-digest',
        leaseTokenDigest: lease.leaseTokenDigest,
        expiresAt: lease.expiresAt,
        releasedAt: lease.releasedAt,
        releaseReasonPresent: Boolean(lease.releaseReason),
        releaseReasonDigest: lease.releaseReason ? 'sha256:release-reason-ui' : undefined,
        createdAt: lease.createdAt,
        updatedAt: lease.updatedAt
      },
      pool: {
        id: lease.poolId,
        projectId: lease.projectId,
        applicationId: 'app-ui',
        environmentId: 'staging',
        code: 'admin-pool-ui',
        name: 'WP8 UI smoke account pool',
        status: 'READY',
        defaultTtlSeconds: 1800,
        leasePolicyKeys: ['sharing', 'token'],
        createdAt: '2026-06-15T00:06:00Z',
        updatedAt: '2026-06-15T00:06:00Z'
      },
      account: {
        id: account.id,
        poolId: account.poolId,
        projectId: account.projectId,
        accountKey: account.accountKey,
        displayName: account.displayName,
        status: account.status,
        roleTags: account.roleTags,
        scopeSummaryKeys: ['tenant', 'token'],
        secretRefDigest: account.secretRefDigest,
        lastHealthStatus: account.lastHealthStatus,
        lastHealthSummaryPresent: true,
        lastHealthSummaryDigest: 'sha256:health-summary-ui',
        createdAt: account.createdAt,
        updatedAt: account.updatedAt
      },
      lifecycleSummary: {
        released: Boolean(lease.releasedAt),
        releaseReasonPresent: Boolean(lease.releaseReason)
      },
      redactionPolicy: {
        secretRefPlaintextExported: false,
        leaseTokenPlaintextExported: false,
        leaseTokenDigestExported: true,
        requestDigestExported: true,
        freeTextValuesExported: false,
        scopeSummaryValuesExported: false,
        leasePolicyValuesExported: false,
        destructiveCleanupTriggered: false
      }
    };
  }

  private taskDetail(id: string, status: string) {
    const failed = status === 'FAILED';
    return {
      id,
      projectId: 'project-wp8-ui-smoke',
      dataSetId: 'ds-ui-created',
      taskType: 'CLEANUP',
      status,
      requestKey: `request-${id}`,
      targetRef: 'dataset:ds-ui-created',
      attempt: 1,
      resultSummary: { deleted: 0 },
      errorCode: failed ? 'CLEANUP_DISABLED' : undefined,
      errorSummary: failed ? 'cleanup worker disabled by smoke policy' : undefined,
      traceId: failed ? 'trace-cleanup-failed' : 'trace-cleanup-create',
      policy: { destructiveCleanupEnabled: false },
      startedAt: failed ? '2026-06-15T00:00:00Z' : undefined,
      finishedAt: failed ? '2026-06-15T00:01:00Z' : undefined,
      createdAt: '2026-06-15T00:00:00Z',
      updatedAt: '2026-06-15T00:00:00Z'
    };
  }

  private taskFromPayload(id: string, status: string, payload: Record<string, unknown>, traceId: string) {
    return {
      ...this.taskDetail(id, status),
      projectId: stringValue(payload.projectId, 'project-wp8-ui-smoke'),
      dataSetId: optionalString(payload.dataSetId),
      taskType: stringValue(payload.taskType, 'CLEANUP'),
      requestKey: stringValue(payload.requestKey, 'cleanup-ui-smoke'),
      targetRef: optionalString(payload.targetRef),
      resultSummary: objectValue(payload.resultSummary),
      traceId,
      createdAt: '2026-06-15T00:10:00Z',
      updatedAt: '2026-06-15T00:10:00Z'
    };
  }

  private page(items: Array<Record<string, unknown>>) {
    return {
      items,
      index: 0,
      size: 50,
      total: items.length
    };
  }

  private async fulfill(route: Route, data: unknown, traceId: string) {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: { 'X-Trace-Id': traceId },
      body: JSON.stringify({
        code: 'OK',
        message: 'OK',
        trace_id: traceId,
        data
      })
    });
  }
}

function objectValue(input: unknown): Record<string, unknown> {
  return input && typeof input === 'object' && !Array.isArray(input) ? input as Record<string, unknown> : {};
}

function arrayValue(input: unknown): unknown[] {
  return Array.isArray(input) ? input : [];
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

function numberValue(input: unknown, fallback = 0) {
  if (typeof input === 'number' && Number.isFinite(input)) return input;
  if (typeof input === 'string' && input.trim() !== '') {
    const parsed = Number(input);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}
