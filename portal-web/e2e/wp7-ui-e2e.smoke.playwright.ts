import { expect, test, type Locator, type Page, type Route } from '@playwright/test';

const wp7Permissions = [
  'uiE2e:read',
  'uiE2e:manage',
  'uiE2e:review',
  'uiE2e:execute',
  'uiE2e:export',
  'uiE2e:flaky'
];

const smokeViewports = [
  { name: 'desktop', width: 1280, height: 900, assertResponsive: false },
  { name: 'mobile', width: 390, height: 844, assertResponsive: true }
] as const;

const existingSceneId = '11111111-1111-4111-8111-111111111111';
const existingBundleId = '22222222-2222-4222-8222-222222222222';
const existingRunId = '33333333-3333-4333-8333-333333333333';
const blockedRunId = '33333333-3333-4333-8333-333333333334';
const existingFlakyId = '44444444-4444-4444-8444-444444444444';
const sensitiveSamples = [
  'secret://wp8/accounts/admin-01',
  'Authorization: Bearer ui-secret',
  'lease token',
  'cookie=ui-secret'
];

for (const viewport of smokeViewports) {
  test(`WP7 UI E2E browser smoke covers main flow on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await runWp7MainFlow(page, viewport.assertResponsive);
  });
}

async function runWp7MainFlow(page: Page, assertResponsive: boolean) {
  const mock = new Wp7UiE2eMock();
  await mock.install(page);

  await page.addInitScript(() => {
    window.localStorage.setItem('veri-agent.access-token', 'wp7-ui-smoke-token');
  });

  await page.goto('/#ui-e2e');

  await expect(page.getByRole('heading', { name: 'UI E2E' })).toBeVisible();
  await expect(page.getByTestId('ui-e2e-workbench')).toBeVisible();
  await expect(page.locator('.metric-card').filter({ hasText: 'APPROVED 场景' }).getByText('1')).toBeVisible();
  await expect(page.locator('.metric-card').filter({ hasText: '待评审脚本包' }).getByText('1')).toBeVisible();
  await expect(page.locator('.metric-card').filter({ hasText: '活跃运行' }).getByText('1')).toBeVisible();
  await expect(page.locator('.metric-card').filter({ hasText: 'allowlist' }).getByText('ON (2)')).toBeVisible();
  await expect(page.locator('.notice.warning').filter({ hasText: 'runner 默认关闭' })).toBeVisible();

  const scenePanel = page.locator('section.panel').filter({ has: page.getByRole('heading', { name: '场景筛选与创建' }) });
  const sceneForm = scenePanel.locator('form.ui-e2e-form').first();
  await fieldControl(sceneForm, 'projectId', 'input').fill('project-wp7-ui-smoke');
  await fieldControl(sceneForm, 'code', 'input').fill('portal-ui-smoke-created');
  await fieldControl(sceneForm, 'name', 'input').fill('后台管理员登录并进入首页');
  await fieldControl(sceneForm, 'applicationId', 'input').fill('app-alpha');
  await fieldControl(sceneForm, 'environmentId', 'input').fill('staging');
  await selectAntdOption(page, fieldControl(sceneForm, 'status', 'select'), '已批准');
  await selectAntdOption(page, fieldControl(sceneForm, 'riskLevel', 'select'), '高');
  await fieldControl(sceneForm, 'tags', 'input').fill('login smoke');
  await fieldControl(sceneForm, 'sourceSummary', 'textarea').fill('{"pageRefs":["page-ui-1"],"flowRefs":["flow-ui-1"],"testCaseRefs":["case-ui-1"]}');
  await sceneForm.getByRole('button', { name: '创建场景' }).click();
  await expect(sceneForm.locator('.document-state-line.success')).toContainText('场景已创建');
  expect(mock.createScenePayload).toMatchObject({
    projectId: 'project-wp7-ui-smoke',
    code: 'portal-ui-smoke-created',
    status: 'APPROVED',
    riskLevel: 'HIGH',
    tags: ['login', 'smoke']
  });

  const createdSceneItem = page.locator('.ui-e2e-list-item').filter({ hasText: 'portal-ui-smoke-created' }).first();
  await expect(createdSceneItem).toBeVisible();
  await createdSceneItem.click();
  await expect(page.locator('section.panel').filter({ has: page.getByRole('heading', { name: '场景详情' }) }).getByText('步骤 1 · LOGIN')).toBeVisible();

  const bundlePanel = page.locator('section.panel').filter({ has: page.getByRole('heading', { name: '脚本包评审' }) });
  const bundleForm = bundlePanel.locator('form.ui-e2e-form').first();
  await bundleForm.getByRole('button', { name: '生成脚本包' }).click();
  await expect(bundleForm.locator('.document-state-line.success')).toContainText('脚本包已生成');
  expect(mock.createBundlePayload).toMatchObject({
    sceneId: mock.createdSceneId
  });
  await bundleForm.getByRole('button', { name: '送审' }).click();
  await expect(bundleForm.locator('.document-state-line.success')).toContainText('脚本包已送审');
  await bundleForm.getByRole('button', { name: '批准' }).click();
  await expect(bundleForm.locator('.document-state-line.success')).toContainText('脚本包已批准');

  const runPanel = page.locator('section.panel').filter({ has: page.getByRole('heading', { name: '运行主链路' }) });
  const runForm = runPanel.locator('form.ui-e2e-form').first();
  const runFilterForm = runPanel.locator('form.ui-e2e-filter-grid').first();
  const runCreationNotice = runForm.locator('.notice').first();
  await expect(runCreationNotice).toContainText('请先补全 accountLeaseRef');
  await fieldControl(runForm, 'accountLeaseRef', 'input').fill('55555555-5555-4555-8555-555555555555');
  await fieldControl(runForm, 'requestKey', 'input').fill('wp7-ui-request-1');
  await fieldControl(runForm, 'reason', 'input').fill('browser smoke run');
  await expect(runCreationNotice).toContainText('当前环境 runner 默认关闭');
  await expect(runForm.getByRole('button', { name: '创建运行' })).toBeDisabled();

  await selectAntdOption(page, fieldControl(runFilterForm, 'status', 'select'), '已阻断');
  await runFilterForm.getByRole('button', { name: '筛选' }).click();
  const blockedRunItem = runPanel.locator('.ui-e2e-list-item').first();
  await expect(blockedRunItem).toContainText('RUNNER_DISABLED');
  await blockedRunItem.click();
  await expect(page.locator('section.panel').filter({ has: page.getByRole('heading', { name: '运行详情' }) }).getByText(blockedRunId)).toBeVisible();
  await runForm.getByRole('button', { name: '导出摘要' }).click();
  await expect(runForm.locator('.document-state-line.success')).toContainText('运行脱敏摘要已导出');
  expect(mock.exportRunSeen).toBe(true);

  const flakyPanel = page.locator('section.panel').filter({ has: page.getByRole('heading', { name: 'Flaky 治理' }) });
  const flakyForm = flakyPanel.locator('form.ui-e2e-form').first();
  await fieldControl(flakyForm, 'projectId', 'input').fill('project-wp7-ui-smoke');
  await fieldControl(flakyForm, 'sceneId', 'input').fill(mock.createdSceneId);
  await fieldControl(flakyForm, 'runId', 'input').fill(blockedRunId);
  await selectAntdOption(page, fieldControl(flakyForm, 'status', 'select'), '已确认 Flaky');
  await fieldControl(flakyForm, 'reasonCode', 'input').fill('locator-drift');
  await fieldControl(flakyForm, 'reasonSummary', 'input').fill('定位器偶发漂移');
  await flakyForm.getByRole('button', { name: '保存 Flaky 标记' }).click();
  await expect(flakyForm.locator('.document-state-line.success')).toContainText('Flaky 标记已更新');
  expect(mock.upsertFlakyPayload).toMatchObject({
    projectId: 'project-wp7-ui-smoke',
    sceneId: mock.createdSceneId,
    runId: blockedRunId,
    status: 'CONFIRMED_FLAKY'
  });

  await assertNoSensitiveSamples(page);

  if (assertResponsive) {
    await expectNoHorizontalOverflow(page, '[data-testid="ui-e2e-workbench"]');
    await expect(page.locator('.ui-e2e-layout')).toBeVisible();
    await expect(page.locator('.ui-e2e-list-column')).toBeVisible();
    await expect(page.locator('.ui-e2e-detail-column')).toBeVisible();
  }
}

function fieldControl(form: Locator, label: string, controlSelector: 'input' | 'select' | 'textarea') {
  const targetSelector = controlSelector === 'select' ? '.ui-native-select' : controlSelector;
  return form.locator(`label.field:has(.field-label:text-is("${label}")) ${targetSelector}`).first();
}

async function selectAntdOption(page: Page, control: Locator, optionName: string) {
  await control.locator('.ant-select-selector').click();
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content', { hasText: optionName }).first().click();
}

async function assertNoSensitiveSamples(page: Page) {
  const bodyText = await page.locator('body').innerText();
  for (const sample of sensitiveSamples) {
    expect(bodyText.toLowerCase()).not.toContain(sample.toLowerCase());
  }
}

async function expectNoHorizontalOverflow(page: Page, selector: string) {
  const overflow = await page.locator(selector).evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return rect.left < -1 || rect.right > window.innerWidth + 1 || document.documentElement.scrollWidth > window.innerWidth + 1;
  });
  expect(overflow).toBe(false);
}

class Wp7UiE2eMock {
  createScenePayload: Record<string, unknown> | undefined;
  createBundlePayload: Record<string, unknown> | undefined;
  upsertFlakyPayload: Record<string, unknown> | undefined;
  exportRunSeen = false;
  createdSceneId = '55555555-5555-4555-8555-555555555551';
  createdBundleId = '66666666-6666-4666-8666-666666666666';

  private health = {
    service: 'ui-e2e',
    status: 'UP',
    enabled: true,
    runnerEnabled: false,
    runnerMode: 'managed',
    defaultTimeoutSeconds: 300,
    maxTimeoutSeconds: 1800,
    maxScenesPerRun: 1,
    maxConcurrency: 2,
    allowlistEnabled: true,
    allowlistHostCount: 2,
    exportEnabled: true,
    supportedNodeTypes: ['UI_TEST'],
    credentialPolicy: {
      accountLeaseRefRequired: true,
      runnerAccountContractReady: true,
      secretRefDigestReturned: true,
      credentialInjectionAdapterReady: false,
      credentialInjectionPreviewOnly: false,
      plaintextCredentialStored: false,
      plaintextCredentialExported: false
    },
    artifactPolicy: {
      captureScreenshotEnabled: true,
      captureVideoEnabled: false,
      captureTraceEnabled: true,
      maxArtifactCount: 20,
      maxArtifactSizeBytes: 20971520,
      redactionScanRequired: true,
      rawArtifactDownloadReady: false
    },
    policy: {
      runControlPlaneReady: true,
      bundleReviewReady: true,
      artifactManifestReady: true,
      flakyMarkReady: true,
      managedPreviewRunnerReady: false,
      runnerDefaultDisabled: true
    }
  };

  private scenes: Array<Record<string, unknown>> = [
    this.sceneSummary(existingSceneId, 'portal-login-approved', 'Portal login approved', 'APPROVED', 'HIGH')
  ];
  private sceneDetails = new Map<string, Record<string, unknown>>([
    [existingSceneId, this.sceneDetail(existingSceneId, 'portal-login-approved', 'Portal login approved', 'APPROVED', 'HIGH')]
  ]);
  private bundles: Array<Record<string, unknown>> = [
    this.bundleSummary(existingBundleId, existingSceneId, 'portal-login-approved', 'REVIEWING')
  ];
  private bundleDetails = new Map<string, Record<string, unknown>>([
    [existingBundleId, this.bundleDetail(existingBundleId, existingSceneId, 'portal-login-approved', 'REVIEWING')]
  ]);
  private runs: Array<Record<string, unknown>> = [
    this.runSummary(existingRunId, existingSceneId, existingBundleId, 'portal-login-approved', 'RUNNING', null, null),
    this.runSummary(blockedRunId, existingSceneId, existingBundleId, 'portal-login-approved', 'BLOCKED', 'UI_E2E_RUNNER_DISABLED', 'runner disabled')
  ];
  private runDetails = new Map<string, Record<string, unknown>>([
    [existingRunId, this.runDetail(existingRunId, existingSceneId, existingBundleId, 'portal-login-approved', 'RUNNING', null, null)],
    [blockedRunId, this.runDetail(
      blockedRunId,
      existingSceneId,
      existingBundleId,
      'portal-login-approved',
      'BLOCKED',
      'UI_E2E_RUNNER_DISABLED',
      'runner disabled'
    )]
  ]);
  private flakyMarks: Array<Record<string, unknown>> = [
    this.flakyMark(existingFlakyId, existingSceneId, existingRunId, 'FLAKY_CANDIDATE')
  ];

  async install(page: Page) {
    await page.route('**/api/v1/notifications/stream', (route) => route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'event: connected\ndata: {"unreadCount":0}\n\n'
    }));
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
        timestamp: '2026-06-19T00:00:00Z'
      }, 'trace-platform-health');
    }

    if (path === '/api/v1/auth/me') {
      return this.fulfill(route, {
        user_id: 'user-wp7-ui-smoke',
        username: 'wp7-ui-smoke',
        display_name: 'WP7 UI Smoke',
        must_change_password: false,
        roles: ['UiE2eOwner'],
        permissions: wp7Permissions
      }, 'trace-auth-me');
    }

    if (path === '/api/v1/notifications' && method === 'GET') {
      return this.fulfill(route, this.page([]), 'trace-notifications');
    }

    if (path === '/api/v1/notifications/unread-count' && method === 'GET') {
      return this.fulfill(route, { unreadCount: 0 }, 'trace-notification-unread');
    }

    if (path.startsWith('/api/v1/management/') && method === 'GET') {
      return this.fulfill(route, this.page([]), 'trace-management-empty');
    }

    if (path === '/api/v1/ui-e2e/health' && method === 'GET') {
      return this.fulfill(route, this.health, 'trace-ui-e2e-health');
    }

    if (path === '/api/v1/ui-e2e/scenes' && method === 'GET') {
      const projectId = url.searchParams.get('projectId') ?? '';
      const status = url.searchParams.get('status') ?? '';
      const keyword = url.searchParams.get('keyword') ?? '';
      const filtered = this.scenes.filter((scene) => this.matchScene(scene, projectId, status, keyword));
      return this.fulfill(route, this.page(filtered), 'trace-scene-list');
    }

    if (path === '/api/v1/ui-e2e/scenes' && method === 'POST') {
      this.createScenePayload = request.postDataJSON() as Record<string, unknown>;
      const detail = this.sceneFromPayload(this.createdSceneId, this.createScenePayload);
      this.sceneDetails.set(this.createdSceneId, detail);
      this.scenes = [this.sceneSummaryFromDetail(detail), ...this.scenes.filter((item) => item.id !== detail.id)];
      return this.fulfill(route, detail, 'trace-scene-create');
    }

    const sceneDetailMatch = path.match(/^\/api\/v1\/ui-e2e\/scenes\/([^/]+)$/);
    if (sceneDetailMatch && method === 'GET') {
      return this.fulfill(route, this.sceneDetails.get(sceneDetailMatch[1]) ?? this.sceneDetail(sceneDetailMatch[1], 'scene', 'scene', 'DRAFT', 'MEDIUM'), 'trace-scene-detail');
    }

    const sceneArchiveMatch = path.match(/^\/api\/v1\/ui-e2e\/scenes\/([^/]+)\/archive$/);
    if (sceneArchiveMatch && method === 'POST') {
      const current = this.sceneDetails.get(sceneArchiveMatch[1]) ?? this.sceneDetail(sceneArchiveMatch[1], 'scene', 'scene', 'APPROVED', 'MEDIUM');
      const detail = { ...current, status: 'ARCHIVED', archivedAt: '2026-06-19T00:10:00Z' };
      this.sceneDetails.set(sceneArchiveMatch[1], detail);
      this.scenes = this.scenes.map((item) => item.id === sceneArchiveMatch[1] ? this.sceneSummaryFromDetail(detail) : item);
      return this.fulfill(route, detail, 'trace-scene-archive');
    }

    if (path === '/api/v1/ui-e2e/bundles' && method === 'GET') {
      const projectId = url.searchParams.get('projectId') ?? '';
      const status = url.searchParams.get('status') ?? '';
      const keyword = url.searchParams.get('keyword') ?? '';
      const filtered = this.bundles.filter((bundle) => this.matchBundle(bundle, projectId, status, keyword));
      return this.fulfill(route, this.page(filtered), 'trace-bundle-list');
    }

    if (path === '/api/v1/ui-e2e/bundles' && method === 'POST') {
      this.createBundlePayload = request.postDataJSON() as Record<string, unknown>;
      const sceneId = stringValue(this.createBundlePayload.sceneId, this.createdSceneId);
      const scene = this.sceneDetails.get(sceneId) ?? this.sceneDetail(sceneId, 'scene', 'scene', 'APPROVED', 'MEDIUM');
      const detail = this.bundleDetail(this.createdBundleId, sceneId, stringValue(scene.code), 'DRAFT');
      this.bundleDetails.set(this.createdBundleId, detail);
      this.bundles = [this.bundleSummaryFromDetail(detail), ...this.bundles.filter((item) => item.id !== detail.id)];
      return this.fulfill(route, detail, 'trace-bundle-create');
    }

    const bundleDetailMatch = path.match(/^\/api\/v1\/ui-e2e\/bundles\/([^/]+)$/);
    if (bundleDetailMatch && method === 'GET') {
      return this.fulfill(route, this.bundleDetails.get(bundleDetailMatch[1]) ?? this.bundleDetail(bundleDetailMatch[1], existingSceneId, 'portal-login-approved', 'DRAFT'), 'trace-bundle-detail');
    }

    const bundleActionMatch = path.match(/^\/api\/v1\/ui-e2e\/bundles\/([^/]+)\/(submit-review|approve|reject)$/);
    if (bundleActionMatch && method === 'POST') {
      const action = bundleActionMatch[2];
      const current = this.bundleDetails.get(bundleActionMatch[1]) ?? this.bundleDetail(bundleActionMatch[1], existingSceneId, 'portal-login-approved', 'DRAFT');
      const nextStatus = action === 'submit-review' ? 'REVIEWING' : action === 'approve' ? 'APPROVED' : 'REJECTED';
      const note = stringValue((request.postDataJSON() as Record<string, unknown> | null)?.note);
      const reviews = arrayValue(current.reviews);
      const nextReview = {
        id: `${bundleActionMatch[1]}-${action}`,
        reviewStatus: nextStatus,
        reviewComment: note,
        reviewedBy: 'wp7-ui-smoke',
        reviewedAt: '2026-06-19T00:12:00Z',
        createdAt: '2026-06-19T00:12:00Z'
      };
      const detail = { ...current, status: nextStatus, reviews: [nextReview, ...reviews], updatedAt: '2026-06-19T00:12:00Z' };
      this.bundleDetails.set(bundleActionMatch[1], detail);
      this.bundles = this.bundles.map((item) => item.id === bundleActionMatch[1] ? this.bundleSummaryFromDetail(detail) : item);
      return this.fulfill(route, detail, `trace-bundle-${action}`);
    }

    if (path === '/api/v1/ui-e2e/runs' && method === 'GET') {
      const projectId = url.searchParams.get('projectId') ?? '';
      const status = url.searchParams.get('status') ?? '';
      const keyword = url.searchParams.get('keyword') ?? '';
      const filtered = this.runs.filter((run) => this.matchRun(run, projectId, status, keyword));
      return this.fulfill(route, this.page(filtered), 'trace-run-list');
    }

    const runDetailMatch = path.match(/^\/api\/v1\/ui-e2e\/runs\/([^/]+)$/);
    if (runDetailMatch && method === 'GET') {
      return this.fulfill(route, this.runDetails.get(runDetailMatch[1]) ?? this.runDetail(runDetailMatch[1], existingSceneId, existingBundleId, 'portal-login-approved', 'BLOCKED', 'UI_E2E_RUNNER_DISABLED', 'runner disabled'), 'trace-run-detail');
    }

    const runExportMatch = path.match(/^\/api\/v1\/ui-e2e\/runs\/([^/]+)\/export$/);
    if (runExportMatch && method === 'GET') {
      this.exportRunSeen = true;
      return this.fulfill(route, {
        schemaVersion: 'wp7-run-export-v1',
        exportedAt: '2026-06-19T00:15:00Z',
        run: this.runDetails.get(runExportMatch[1]) ?? this.runDetail(runExportMatch[1], existingSceneId, existingBundleId, 'portal-login-approved', 'BLOCKED', 'UI_E2E_RUNNER_DISABLED', 'runner disabled'),
        redactionPolicy: {
          aggregateOnly: true,
          artifactDownloadReady: false,
          runnerOutputExported: false
        }
      }, 'trace-run-export');
    }

    if (path === '/api/v1/ui-e2e/flaky-marks' && method === 'GET') {
      const projectId = url.searchParams.get('projectId') ?? '';
      const status = url.searchParams.get('status') ?? '';
      const keyword = url.searchParams.get('keyword') ?? '';
      const filtered = this.flakyMarks.filter((item) => this.matchFlaky(item, projectId, status, keyword));
      return this.fulfill(route, this.page(filtered), 'trace-flaky-list');
    }

    if (path === '/api/v1/ui-e2e/flaky-marks' && method === 'POST') {
      this.upsertFlakyPayload = request.postDataJSON() as Record<string, unknown>;
      const detail = this.flakyFromPayload(existingFlakyId, this.upsertFlakyPayload);
      this.flakyMarks = [detail, ...this.flakyMarks.filter((item) => item.id !== detail.id)];
      this.runs = this.runs.map((run) => run.id === detail.runId ? { ...run, flakyStatus: detail.status } : run);
      const runDetail = this.runDetails.get(detail.runId);
      if (runDetail) {
        this.runDetails.set(detail.runId, { ...runDetail, flakyMark: detail, updatedAt: '2026-06-19T00:16:00Z' });
      }
      return this.fulfill(route, detail, 'trace-flaky-upsert');
    }

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'NOT_FOUND',
        message: `Unhandled WP7 smoke mock route: ${method} ${path}`,
        trace_id: 'trace-unhandled',
        data: {}
      })
    });
  }

  private sceneSummary(id: string, code: string, name: string, status: string, riskLevel: string) {
    return {
      id,
      projectId: 'project-wp7-ui-smoke',
      applicationId: 'app-alpha',
      environmentId: 'staging',
      code,
      name,
      status,
      riskLevel,
      tags: ['smoke'],
      sourceSummary: { sourceType: 'WP3' },
      stepCount: 1,
      createdAt: '2026-06-19T00:00:00Z',
      updatedAt: '2026-06-19T00:00:00Z'
    };
  }

  private sceneDetail(id: string, code: string, name: string, status: string, riskLevel: string) {
    return {
      ...this.sceneSummary(id, code, name, status, riskLevel),
      policy: {
        mutable: status !== 'ARCHIVED',
        executable: status === 'APPROVED',
        rawDomSnapshotStored: false,
        crossWpDirectTableReadAllowed: false
      },
      steps: [
        {
          id: `${id}-step-1`,
          stepOrder: 1,
          stepType: 'LOGIN',
          actionSummary: { submitAction: 'click' },
          locatorStrategy: { preferred: 'testId' },
          assertionSummary: { successSignal: 'url contains /dashboard' },
          waitPolicy: { timeoutSeconds: 5 },
          createdAt: '2026-06-19T00:00:00Z',
          updatedAt: '2026-06-19T00:00:00Z'
        }
      ]
    };
  }

  private sceneFromPayload(id: string, payload: Record<string, unknown>) {
    return {
      id,
      projectId: stringValue(payload.projectId, 'project-wp7-ui-smoke'),
      applicationId: stringValue(payload.applicationId, 'app-alpha'),
      environmentId: stringValue(payload.environmentId, 'staging'),
      code: stringValue(payload.code, 'created-scene'),
      name: stringValue(payload.name, 'created-scene'),
      status: stringValue(payload.status, 'DRAFT'),
      riskLevel: stringValue(payload.riskLevel, 'MEDIUM'),
      tags: arrayValue(payload.tags).map((item) => stringValue(item)).filter(Boolean),
      sourceSummary: objectValue(payload.sourceSummary),
      policy: {
        mutable: true,
        executable: stringValue(payload.status, 'DRAFT') === 'APPROVED',
        rawDomSnapshotStored: false,
        crossWpDirectTableReadAllowed: false
      },
      steps: [
        {
          id: `${id}-step-1`,
          stepOrder: 1,
          stepType: 'LOGIN',
          actionSummary: { submitAction: 'click' },
          locatorStrategy: { preferred: 'testId' },
          assertionSummary: { successSignal: 'url contains /dashboard' },
          waitPolicy: { timeoutSeconds: 5 }
        }
      ],
      createdAt: '2026-06-19T00:05:00Z',
      updatedAt: '2026-06-19T00:05:00Z'
    };
  }

  private sceneSummaryFromDetail(detail: Record<string, unknown>) {
    return {
      id: stringValue(detail.id),
      projectId: stringValue(detail.projectId),
      applicationId: stringValue(detail.applicationId),
      environmentId: stringValue(detail.environmentId),
      code: stringValue(detail.code),
      name: stringValue(detail.name),
      status: stringValue(detail.status),
      riskLevel: stringValue(detail.riskLevel),
      tags: arrayValue(detail.tags),
      sourceSummary: objectValue(detail.sourceSummary),
      stepCount: arrayValue(detail.steps).length,
      createdAt: stringValue(detail.createdAt),
      updatedAt: stringValue(detail.updatedAt)
    };
  }

  private bundleSummary(id: string, sceneId: string, sceneCode: string, status: string) {
    return {
      id,
      projectId: 'project-wp7-ui-smoke',
      sceneId,
      sceneCode,
      sceneStatus: 'APPROVED',
      status,
      bundleDigest: `sha256:${id}`,
      staticCheckStatus: 'PASSED',
      riskLevel: 'HIGH',
      tags: ['smoke'],
      updatedAt: '2026-06-19T00:00:00Z'
    };
  }

  private bundleDetail(id: string, sceneId: string, sceneCode: string, status: string) {
    return {
      ...this.bundleSummary(id, sceneId, sceneCode, status),
      specSummary: { templateVersion: 'wp7-playwright-summary-v1', aggregateOnly: true },
      fixtureSummary: { networkAccessMode: 'ALLOWLIST_ONLY', credentialMode: 'LEASE_INJECTION_ONLY' },
      staticCheckSummary: { status: 'PASSED', findingCount: 0, warningCount: 0 },
      policy: { approvable: status === 'REVIEWING', aggregateOnly: true },
      reviews: []
    };
  }

  private bundleSummaryFromDetail(detail: Record<string, unknown>) {
    return {
      id: stringValue(detail.id),
      projectId: stringValue(detail.projectId),
      sceneId: stringValue(detail.sceneId),
      sceneCode: stringValue(detail.sceneCode),
      sceneStatus: stringValue(detail.sceneStatus),
      status: stringValue(detail.status),
      bundleDigest: stringValue(detail.bundleDigest),
      staticCheckStatus: stringValue(detail.staticCheckStatus),
      riskLevel: stringValue(detail.riskLevel),
      tags: arrayValue(detail.tags),
      updatedAt: stringValue(detail.updatedAt)
    };
  }

  private runSummary(
    id: string,
    sceneId: string,
    bundleId: string,
    sceneCode: string,
    status: string,
    failureCode: string | null,
    failureSummary: string | null
  ) {
    return {
      id,
      projectId: 'project-wp7-ui-smoke',
      sceneId,
      sceneCode,
      bundleId,
      status,
      requestKey: `request-${id.slice(0, 8)}`,
      runnerMode: 'DISABLED',
      failureCode,
      failureSummary,
      traceId: `trace-${id.slice(0, 8)}`,
      accountSummary: {
        accountLeaseRef: '55555555-5555-4555-8555-555555555555',
        status: 'ACTIVE',
        secretRefDigest: 'sha256:lease-secret',
        secretPlaintextReturned: false
      },
      flakyStatus: failureCode ? 'NONE' : 'FLAKY_CANDIDATE',
      createdAt: '2026-06-19T00:00:00Z',
      updatedAt: '2026-06-19T00:00:00Z'
    };
  }

  private runDetail(
    id: string,
    sceneId: string,
    bundleId: string,
    sceneCode: string,
    status: string,
    failureCode: string | null,
    failureSummary: string | null
  ) {
    const blocked = status === 'BLOCKED';
    return {
      ...this.runSummary(id, sceneId, bundleId, sceneCode, status, failureCode, failureSummary),
      sceneStatus: 'APPROVED',
      bundleStatus: 'APPROVED',
      accountSummary: {
        accountLeaseRef: '55555555-5555-4555-8555-555555555555',
        status: 'ACTIVE',
        accountKey: 'qa-admin-01',
        displayName: 'QA Admin 01',
        roleTags: ['ADMIN'],
        secretRefDigest: 'sha256:lease-secret',
        secretPlaintextReturned: false
      },
      executionSummary: {
        aggregateOnly: true,
        runnerReady: true,
        stepResultCount: blocked ? 1 : 2,
        artifactManifestCount: blocked ? 1 : 2,
        rawArtifactDownloadReady: false,
        secretRefPlaintextReturned: false,
        baseUrlDigest: 'sha256:base-url',
        accountScopeSummaryKeys: ['applicationId'],
        runnerDefaultDisabled: true,
        cancelSupported: true,
        stepStatusCounts: blocked
          ? { BLOCKED: 1, RUNNING: 0, FAILED: 0, SUCCEEDED: 0, QUEUED: 0, PENDING: 0, SKIPPED: 0, TIMEOUT: 0, CANCELED: 0 }
          : { BLOCKED: 0, RUNNING: 1, FAILED: 0, SUCCEEDED: 1, QUEUED: 0, PENDING: 0, SKIPPED: 0, TIMEOUT: 0, CANCELED: 0 },
        failureBucketCounts: blocked
          ? { RUNNER: 1, ACCOUNT: 0, ASSERTION: 0, AUTHORIZATION: 0, ENVIRONMENT_TIMEOUT: 0, LOCATOR: 0, TEST_DATA: 0, UNKNOWN: 0 }
          : { RUNNER: 0, ACCOUNT: 0, ASSERTION: 0, AUTHORIZATION: 0, ENVIRONMENT_TIMEOUT: 0, LOCATOR: 0, TEST_DATA: 0, UNKNOWN: 0 }
      },
      stepResults: blocked
        ? [
          {
            id: `${id}-step-result-1`,
            sceneStepId: `${sceneId}-step-1`,
            stepOrder: 1,
            status: 'BLOCKED',
            durationMs: 0,
            failureBucket: 'RUNNER',
            errorCode: 'UI_E2E_RUNNER_DISABLED',
            summary: {
              aggregateOnly: true,
              rawDomStored: false,
              previewOnly: true
            }
          }
        ]
        : [
          {
            id: `${id}-step-result-1`,
            sceneStepId: `${sceneId}-step-1`,
            stepOrder: 1,
            status: 'SUCCEEDED',
            durationMs: 120,
            failureBucket: null,
            errorCode: null,
            summary: { aggregateOnly: true }
          },
          {
            id: `${id}-step-result-2`,
            sceneStepId: `${sceneId}-step-2`,
            stepOrder: 2,
            status: 'RUNNING',
            durationMs: 0,
            failureBucket: null,
            errorCode: null,
            summary: { aggregateOnly: true }
          }
        ],
      artifacts: [
        {
          id: `${id}-artifact-1`,
          artifactType: 'LOG',
          storageRef: blocked ? 'summary://ui-e2e/blocked' : 'summary://ui-e2e/running',
          artifactDigest: 'sha256:artifact-1',
          sizeBytes: 128,
          redactionFlags: {
            aggregateOnly: true,
            rawArtifactStored: false,
            rawArtifactDownloadReady: false
          },
          captureStatus: 'CAPTURED'
        }
      ],
      flakyMark: blocked ? null : this.flakyMark(existingFlakyId, sceneId, id, 'FLAKY_CANDIDATE'),
      startedAt: '2026-06-19T00:00:00Z',
      finishedAt: blocked ? '2026-06-19T00:00:01Z' : null,
      idempotentReplay: false
    };
  }

  private flakyMark(id: string, sceneId: string, runId: string, status: string) {
    return {
      id,
      projectId: 'project-wp7-ui-smoke',
      sceneId,
      sceneCode: 'portal-login-approved',
      sceneName: 'Portal login approved',
      runId,
      runStatus: 'RUNNING',
      status,
      reasonCode: 'locator-drift',
      reasonSummary: '定位器偶发漂移',
      createdBy: 'wp7-ui-smoke',
      updatedBy: 'wp7-ui-smoke',
      createdAt: '2026-06-19T00:00:00Z',
      updatedAt: '2026-06-19T00:00:00Z'
    };
  }

  private flakyFromPayload(id: string, payload: Record<string, unknown>) {
    return {
      id,
      projectId: stringValue(payload.projectId, 'project-wp7-ui-smoke'),
      sceneId: stringValue(payload.sceneId, this.createdSceneId),
      sceneCode: 'portal-ui-smoke-created',
      sceneName: '后台管理员登录并进入首页',
      runId: stringValue(payload.runId, existingRunId),
      runStatus: 'BLOCKED',
      status: stringValue(payload.status, 'FLAKY_CANDIDATE'),
      reasonCode: stringValue(payload.reasonCode, 'locator-drift'),
      reasonSummary: stringValue(payload.reasonSummary, '定位器偶发漂移'),
      createdBy: 'wp7-ui-smoke',
      updatedBy: 'wp7-ui-smoke',
      createdAt: '2026-06-19T00:16:00Z',
      updatedAt: '2026-06-19T00:16:00Z'
    };
  }

  private matchScene(scene: Record<string, unknown>, projectId: string, status: string, keyword: string) {
    return this.matchCommon(scene, projectId, status, keyword, ['code', 'name']);
  }

  private matchBundle(bundle: Record<string, unknown>, projectId: string, status: string, keyword: string) {
    return this.matchCommon(bundle, projectId, status, keyword, ['sceneCode', 'bundleDigest']);
  }

  private matchRun(run: Record<string, unknown>, projectId: string, status: string, keyword: string) {
    return this.matchCommon(run, projectId, status, keyword, ['sceneCode', 'requestKey', 'failureCode']);
  }

  private matchFlaky(item: Record<string, unknown>, projectId: string, status: string, keyword: string) {
    return this.matchCommon(item, projectId, status, keyword, ['sceneCode', 'reasonCode', 'reasonSummary']);
  }

  private matchCommon(
    value: Record<string, unknown>,
    projectId: string,
    status: string,
    keyword: string,
    textKeys: string[]
  ) {
    if (projectId && stringValue(value.projectId) !== projectId) {
      return false;
    }
    if (status && stringValue(value.status) !== status) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    const normalizedKeyword = keyword.toLowerCase();
    return textKeys.some((key) => stringValue(value[key]).toLowerCase().includes(normalizedKeyword));
  }

  private page(items: unknown[]) {
    return {
      items,
      index: 0,
      size: 20,
      total: items.length
    };
  }

  private fulfill(route: Route, data: unknown, traceId: string) {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'ok',
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
