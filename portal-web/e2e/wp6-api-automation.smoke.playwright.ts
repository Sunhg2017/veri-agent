import { expect, test, type Page, type Route } from '@playwright/test';

const wp6Permissions = [
  'apiAutomation:read',
  'apiAutomation:import',
  'apiAutomation:generate',
  'apiAutomation:review',
  'apiAutomation:execute',
  'apiAutomation:export'
];

const openApiDocument = JSON.stringify({
  openapi: '3.0.3',
  info: { title: 'WP6 UI smoke', version: '1.0.0' },
  paths: {
    '/v1/orders/{id}': {
      get: {
        operationId: 'getOrder',
        summary: 'Get order',
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string' } }],
        responses: { '200': { description: 'ok' }, '404': { description: 'missing' } }
      }
    },
    '/v1/orders': {
      post: {
        operationId: 'createOrder',
        summary: 'Create order',
        requestBody: { required: true, content: { 'application/json': { schema: { type: 'object' } } } },
        responses: { '201': { description: 'created' }, '400': { description: 'invalid' } }
      }
    }
  }
}, null, 2);

const smokeViewports = [
  { name: 'desktop', width: 1280, height: 900, assertResponsive: false },
  { name: 'mobile', width: 390, height: 844, assertResponsive: true }
] as const;

for (const viewport of smokeViewports) {
  test(`WP6 API automation browser smoke covers main flow on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await runWp6MainFlow(page, viewport.assertResponsive);
  });
}

async function runWp6MainFlow(page: Page, assertResponsive: boolean) {
  const mock = new Wp6ApiAutomationMock();
  await mock.install(page);

  await page.addInitScript(() => {
    window.localStorage.setItem('veri-agent.access-token', 'wp6-ui-smoke-token');
  });

  await page.goto('/#api-automation');

  await expect(page.getByRole('heading', { name: '接口自动化' })).toBeVisible();
  await expect(page.locator('.api-automation-policy-item').filter({ hasText: 'Runner' }).getByText('ENABLED')).toBeVisible();

  await page.getByLabel('项目').fill('project-wp6-ui-smoke');
  await page.getByLabel('名称').fill('WP6 UI smoke spec');
  await page.getByLabel('版本').fill('2026.06');
  await page.getByLabel('来源').fill('ui-smoke-openapi.yaml');
  await page.getByLabel('OpenAPI').fill(openApiDocument);
  await page.getByRole('button', { name: '导入' }).click();

  await expect(page.getByText('OpenAPI 规格已导入')).toBeVisible();
  await expect(page.getByText('WP6 UI smoke spec', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('/v1/orders/{id}')).toBeVisible();
  expect(mock.importPayload).toMatchObject({
    projectId: 'project-wp6-ui-smoke',
    sourceType: 'TEXT',
    name: 'WP6 UI smoke spec',
    versionLabel: '2026.06',
    sourceRef: 'ui-smoke-openapi.yaml'
  });
  expect(String(mock.importPayload?.content)).toContain('/v1/orders/{id}');

  await page.getByRole('button', { name: 'Diff' }).click();
  await expect(page.getByText('Diff：NEW 1 · CHANGED 1 · MATCHED 0')).toBeVisible();
  await expect(page.getByText('SCHEMA_DIGEST_CHANGED')).toBeVisible();
  await selectAntdOption(page, page.getByLabel('Diff 筛选'), '变更');
  await expect(page.getByText('SCHEMA_DIGEST_CHANGED')).toBeVisible();
  await expect(page.getByText('NO_MATCHING_WP3_API')).toHaveCount(0);
  await selectAntdOption(page, page.getByLabel('Diff 筛选'), '全部');

  await page.getByRole('button', { name: '同步' }).click();
  await expect(page.getByText('同步：CREATED 1 · UPDATED 1 · FAILED 0').first()).toBeVisible();
  await expect(page.getByText('2 条同步明细')).toBeVisible();

  await page.getByLabel('选择 GET /v1/orders/{id}').check();
  await expect(page.getByText('API 范围 1/2')).toBeVisible();
  await selectAntdOption(page, page.getByLabel('生成模式'), '仅兜底');
  await page.getByLabel('WP3 用例 ID').fill('asset-case-smoke-1, asset-case-smoke-2');
  await page.getByRole('button', { name: '生成用例' }).click();
  await expect(page.getByText('生成：COMPLETED · API 2 · CASE 4').first()).toBeVisible();
  await expect(page.getByText('4 条草稿')).toBeVisible();
  await expect(page.getByText('2 条最近记录')).toBeVisible();
  await expect(page.getByText('MODEL_WITH_FALLBACK · API 1 · CASE 2')).toBeVisible();
  expect(mock.generationPayload).toMatchObject({
    projectId: 'project-wp6-ui-smoke',
    specId: 'spec-ui-1',
    assetApiIds: ['asset-api-ui-1'],
    assetTestCaseIds: ['asset-case-smoke-1', 'asset-case-smoke-2'],
    coverageTypes: ['SMOKE', 'EXCEPTION'],
    generationMode: 'FALLBACK_ONLY',
    caseCountPerApi: 2
  });

  await page.getByRole('button', { name: /MODEL_WITH_FALLBACK · API 1 · CASE 2/ }).click();
  await expect(page.getByText('生成：COMPLETED · API 1 · CASE 2').first()).toBeVisible();
  await expect(page.getByText('2 条草稿')).toBeVisible();

  await page.getByRole('button', { name: '生成脚本包' }).click();
  await expect(page.getByText('脚本包已生成')).toBeVisible();
  await expect(page.getByText('tests/test_generated_api.py')).toBeVisible();

  await page.getByLabel('备注').fill('ready for wp6 ui smoke');
  await page.getByRole('button', { name: '提交评审' }).click();
  await expect(page.getByText('脚本包已提交评审')).toBeVisible();
  await page.getByRole('button', { name: '审批' }).click();
  await expect(page.getByText('脚本包已审批通过')).toBeVisible();
  await expect(page.getByText('Runner ENABLED')).toBeVisible();
  expect(mock.reviewNotes).toEqual(['ready for wp6 ui smoke', 'ready for wp6 ui smoke']);

  await page.getByLabel('baseUrl').fill('https://api.wp6-smoke.example.test/service?token=should-not-render');
  await page.getByLabel('环境').fill('staging');
  await page.getByLabel('Case IDs').fill('case-ui-1');
  await page.getByLabel('secretRefs').fill('secret://wp6/ui-smoke-token');
  await page.getByRole('button', { name: '运行' }).click();

  await expect(page.getByText('运行：RUNNING · CASE 1 · OK').first()).toBeVisible();
  await expect(page.getByText('api.wp6-smoke.example.test')).toBeVisible();
  expect(await page.locator('body').innerText()).not.toContain('should-not-render');
  expect(mock.runPayload).toMatchObject({
    bundleId: 'bundle-ui-1',
    environmentId: 'staging',
    baseUrl: 'https://api.wp6-smoke.example.test/service?token=should-not-render',
    caseIds: ['case-ui-1'],
    secretRefs: ['secret://wp6/ui-smoke-token']
  });

  await page.getByRole('button', { name: '取消' }).click();
  await expect(page.getByText('运行：CANCELED · CASE 1 · RUNNER_CANCELED').first()).toBeVisible();
  await expect(page.getByText('RUNNER_CANCELED · canceled by ui smoke')).toBeVisible();
  expect(mock.cancelSeen).toBe(true);

  await page.getByRole('button', { name: '导出摘要' }).click();
  await expect(page.getByText('导出 wp6-run-export-v1 · 1 条结果')).toBeVisible();
  await expect(page.getByText('raw URL off')).toBeVisible();
  await expect(page.getByText('request/response off')).toBeVisible();
  expect(mock.exportSeen).toBe(true);

  if (assertResponsive) {
    await expectNoHorizontalOverflow(page, '.api-automation-console');
    await expect(page.locator('.api-automation-panel-actions').first()).toBeVisible();
    await expect(page.locator('.api-path').filter({ hasText: '/v1/orders/{id}' }).first()).toBeVisible();
    await expect(page.locator('.api-automation-history-item').first()).toBeVisible();
  }
}

async function expectNoHorizontalOverflow(page: Page, selector: string) {
  const overflow = await page.locator(selector).evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return rect.left < -1 || rect.right > window.innerWidth + 1 || document.documentElement.scrollWidth > window.innerWidth + 1;
  });
  expect(overflow).toBe(false);
}

async function selectAntdOption(page: Page, control: ReturnType<Page['getByLabel']>, optionName: string) {
  await control.locator('xpath=./ancestor-or-self::*[contains(concat(" ", normalize-space(@class), " "), " ant-select ")][1]').locator('.ant-select-selector').click();
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content', { hasText: optionName }).first().click();
}

class Wp6ApiAutomationMock {
  importPayload: Record<string, unknown> | undefined;
  generationPayload: Record<string, unknown> | undefined;
  runPayload: Record<string, unknown> | undefined;
  reviewNotes: string[] = [];
  cancelSeen = false;
  exportSeen = false;

  private specs: Array<Record<string, unknown>> = [];
  private detail: Record<string, unknown> | undefined;
  private bundleStatus = 'DRAFT';
  private lastRun = this.runDetail('RUNNING');

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
        timestamp: '2026-06-13T00:00:00Z'
      }, 'trace-platform-health');
    }

    if (path === '/api/v1/auth/me') {
      return this.fulfill(route, {
        user_id: 'user-wp6-ui-smoke',
        username: 'wp6-ui-smoke',
        display_name: 'WP6 UI Smoke',
        must_change_password: false,
        roles: ['ApiAutomationOwner'],
        permissions: wp6Permissions
      }, 'trace-auth-me');
    }

    if (path === '/api/v1/api-automation/health') {
      return this.fulfill(route, {
        service: 'api-automation',
        status: 'UP',
        supportedOpenApiVersions: ['3.x'],
        specMaxBytes: 1048576,
        endpointMaxCount: 500,
        runnerEnabled: true,
        runnerMode: 'MANAGED',
        runnerTimeoutSeconds: 120,
        runnerMaxCases: 100,
        promptKey: 'wp6-api-automation-v1',
        modelFallbackEnabled: true,
        policy: {
          urlFetchEnabled: false,
          modelGenerationReady: false,
          rawBaseUrlExported: false,
          rawRequestResponseExported: false
        }
      }, 'trace-api-automation-health');
    }

    if (path === '/api/v1/api-automation/specs' && method === 'GET') {
      return this.fulfill(route, this.page(this.specs), 'trace-spec-list');
    }

    if (path === '/api/v1/api-automation/specs' && method === 'POST') {
      this.importPayload = request.postDataJSON() as Record<string, unknown>;
      const spec = this.spec();
      this.specs = [spec];
      this.detail = { spec, parseSummary: { endpointCount: 2 }, endpoints: this.baseEndpoints('UNKNOWN') };
      return this.fulfill(route, this.detail, 'trace-spec-import');
    }

    const specDetailMatch = path.match(/^\/api\/v1\/api-automation\/specs\/([^/]+)$/);
    if (specDetailMatch && method === 'GET') {
      return this.fulfill(route, this.detail ?? {
        spec: this.spec(),
        parseSummary: { endpointCount: 2 },
        endpoints: this.baseEndpoints('UNKNOWN')
      }, 'trace-spec-detail');
    }

    const parseMatch = path.match(/^\/api\/v1\/api-automation\/specs\/([^/]+)\/parse$/);
    if (parseMatch && method === 'POST') {
      this.detail = { spec: this.spec(), parseSummary: { endpointCount: 2 }, endpoints: this.baseEndpoints('UNKNOWN') };
      return this.fulfill(route, this.detail, 'trace-spec-parse');
    }

    const diffMatch = path.match(/^\/api\/v1\/api-automation\/specs\/([^/]+)\/diff$/);
    if (diffMatch && method === 'GET') {
      const endpoints = this.baseEndpoints('DIFF');
      this.detail = { spec: this.spec(), parseSummary: { endpointCount: 2 }, endpoints };
      return this.fulfill(route, {
        specId: 'spec-ui-1',
        counts: { NEW: 1, CHANGED: 1, MATCHED: 0 },
        endpoints
      }, 'trace-spec-diff');
    }

    const syncMatch = path.match(/^\/api\/v1\/api-automation\/specs\/([^/]+)\/sync$/);
    if (syncMatch && method === 'POST') {
      const endpoints = this.baseEndpoints('SYNCED');
      this.detail = { spec: this.spec(), parseSummary: { endpointCount: 2 }, endpoints };
      return this.fulfill(route, {
        specId: 'spec-ui-1',
        counts: { CREATED: 1, UPDATED: 1, FAILED: 0 },
        items: [
          {
            endpointId: 'endpoint-ui-1',
            assetApiId: 'asset-api-ui-1',
            httpMethod: 'GET',
            path: '/v1/orders/{id}',
            beforeStatus: 'NEW',
            result: 'CREATED'
          },
          {
            endpointId: 'endpoint-ui-2',
            assetApiId: 'asset-api-ui-2',
            httpMethod: 'POST',
            path: '/v1/orders',
            beforeStatus: 'CHANGED',
            result: 'UPDATED'
          }
        ],
        endpoints
      }, 'trace-spec-sync');
    }

    if (path === '/api/v1/api-automation/generation-tasks' && method === 'POST') {
      this.generationPayload = request.postDataJSON() as Record<string, unknown>;
      return this.fulfill(route, this.generationDetail([], 'task-ui-1'), 'trace-generation-create');
    }

    if (path === '/api/v1/api-automation/generation-tasks' && method === 'GET') {
      return this.fulfill(route, this.page([
        this.generationTask('task-ui-1', 'FALLBACK_ONLY', 2, 4, '2026-06-13T00:02:00Z'),
        this.generationTask('task-ui-history-1', 'MODEL_WITH_FALLBACK', 1, 2, '2026-06-13T00:01:30Z')
      ]), 'trace-generation-list');
    }

    const generationDetailMatch = path.match(/^\/api\/v1\/api-automation\/generation-tasks\/([^/]+)$/);
    if (generationDetailMatch && method === 'GET') {
      const taskId = generationDetailMatch[1];
      return this.fulfill(route, this.generationDetail([], taskId), 'trace-generation-detail');
    }

    const scriptBundleMatch = path.match(/^\/api\/v1\/api-automation\/generation-tasks\/([^/]+)\/script-bundles$/);
    if (scriptBundleMatch && method === 'POST') {
      this.bundleStatus = 'DRAFT';
      return this.fulfill(route, this.scriptBundle(), 'trace-script-bundle-create');
    }

    const submitReviewMatch = path.match(/^\/api\/v1\/api-automation\/script-bundles\/([^/]+)\/submit-review$/);
    if (submitReviewMatch && method === 'POST') {
      const payload = request.postDataJSON() as { note?: string };
      this.reviewNotes.push(payload.note ?? '');
      this.bundleStatus = 'REVIEWING';
      return this.fulfill(route, this.scriptBundle(), 'trace-script-bundle-submit');
    }

    const approveMatch = path.match(/^\/api\/v1\/api-automation\/script-bundles\/([^/]+)\/approve$/);
    if (approveMatch && method === 'POST') {
      const payload = request.postDataJSON() as { note?: string };
      this.reviewNotes.push(payload.note ?? '');
      this.bundleStatus = 'APPROVED';
      return this.fulfill(route, this.scriptBundle(), 'trace-script-bundle-approve');
    }

    if (path === '/api/v1/api-automation/runs' && method === 'POST') {
      this.runPayload = request.postDataJSON() as Record<string, unknown>;
      this.lastRun = this.runDetail('RUNNING');
      return this.fulfill(route, this.lastRun, 'trace-run-create');
    }

    const cancelMatch = path.match(/^\/api\/v1\/api-automation\/runs\/([^/]+)\/cancel$/);
    if (cancelMatch && method === 'POST') {
      this.cancelSeen = true;
      this.lastRun = this.runDetail('CANCELED');
      return this.fulfill(route, this.lastRun, 'trace-run-cancel');
    }

    const exportMatch = path.match(/^\/api\/v1\/api-automation\/runs\/([^/]+)\/export$/);
    if (exportMatch && method === 'GET') {
      this.exportSeen = true;
      return this.fulfill(route, {
        schemaVersion: 'wp6-run-export-v1',
        exportedAt: '2026-06-13T00:06:00Z',
        run: this.lastRun.run,
        results: this.lastRun.results,
        resultCounts: { CANCELED: 1 },
        redactionPolicy: {
          rawBaseUrlExported: false,
          rawRequestResponseExported: false,
          stdoutStderrExported: false,
          secretValuesExported: false
        }
      }, 'trace-run-export');
    }

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'NOT_FOUND',
        message: `Unhandled WP6 smoke mock route: ${method} ${path}`,
        trace_id: 'trace-unhandled',
        data: {}
      })
    });
  }

  private spec() {
    return {
      id: 'spec-ui-1',
      projectId: 'project-wp6-ui-smoke',
      sourceType: 'TEXT',
      sourceRef: 'ui-smoke-openapi.yaml',
      name: 'WP6 UI smoke spec',
      versionLabel: '2026.06',
      specDigest: 'sha256:spec-ui',
      contentSizeBytes: 1024,
      status: 'PARSED',
      parserVersion: 'wp6-openapi-parser-v1',
      endpointCount: 2,
      parsedAt: '2026-06-13T00:01:00Z',
      createdAt: '2026-06-13T00:01:00Z',
      updatedAt: '2026-06-13T00:01:00Z'
    };
  }

  private baseEndpoints(mode: 'UNKNOWN' | 'DIFF' | 'SYNCED') {
    const firstStatus = mode === 'DIFF' ? 'NEW' : mode === 'SYNCED' ? 'MATCHED' : 'UNKNOWN';
    const secondStatus = mode === 'DIFF' ? 'CHANGED' : mode === 'SYNCED' ? 'MATCHED' : 'UNKNOWN';
    return [
      {
        id: 'endpoint-ui-1',
        serviceName: 'orders',
        operationId: 'getOrder',
        httpMethod: 'GET',
        path: '/v1/orders/{id}',
        summary: 'Get order',
        tags: 'orders',
        parameterCount: 1,
        requestBodyPresent: false,
        responseStatuses: '200,404',
        schemaDigest: 'sha256:endpoint-1',
        diffStatus: firstStatus,
        assetApiId: mode === 'SYNCED' ? 'asset-api-ui-1' : undefined,
        diffSummary: mode === 'DIFF' ? { reason: 'NO_MATCHING_WP3_API' } : { reason: 'SYNCED' }
      },
      {
        id: 'endpoint-ui-2',
        serviceName: 'orders',
        operationId: 'createOrder',
        httpMethod: 'POST',
        path: '/v1/orders',
        summary: 'Create order',
        tags: 'orders',
        parameterCount: 0,
        requestBodyPresent: true,
        responseStatuses: '201,400',
        schemaDigest: 'sha256:endpoint-2',
        diffStatus: secondStatus,
        assetApiId: mode === 'UNKNOWN' ? undefined : 'asset-api-ui-2',
        diffSummary: mode === 'DIFF' ? { reason: 'SCHEMA_DIGEST_CHANGED' } : { reason: 'SYNCED' }
      }
    ];
  }

  private generationDetail(scriptBundles: Array<Record<string, unknown>>, taskId: string) {
    const historyTask = taskId === 'task-ui-history-1';
    return {
      task: {
        ...this.generationTask(
          taskId,
          historyTask ? 'MODEL_WITH_FALLBACK' : 'FALLBACK_ONLY',
          historyTask ? 1 : 2,
          historyTask ? 2 : 4,
          historyTask ? '2026-06-13T00:01:30Z' : '2026-06-13T00:02:00Z'
        ),
        projectId: 'project-wp6-ui-smoke',
        specId: 'spec-ui-1',
        requestKey: 'wp6-fallback_only-spec-ui-1',
        requestDigest: 'sha256:generation-ui',
        promptKey: 'wp6-api-automation-v1',
        promptVersion: 'v1',
        fallbackUsed: true,
        inputSummary: { aggregateOnly: true }
      },
      cases: historyTask
        ? [
          this.case('case-ui-1', 'endpoint-ui-1', 'asset-api-ui-1', 'GET', '/v1/orders/{id}', 'SMOKE', 200),
          this.case('case-ui-2', 'endpoint-ui-1', 'asset-api-ui-1', 'GET', '/v1/orders/{id}', 'EXCEPTION', 404)
        ]
        : [
          this.case('case-ui-1', 'endpoint-ui-1', 'asset-api-ui-1', 'GET', '/v1/orders/{id}', 'SMOKE', 200),
          this.case('case-ui-2', 'endpoint-ui-1', 'asset-api-ui-1', 'GET', '/v1/orders/{id}', 'EXCEPTION', 404),
          this.case('case-ui-3', 'endpoint-ui-2', 'asset-api-ui-2', 'POST', '/v1/orders', 'SMOKE', 201),
          this.case('case-ui-4', 'endpoint-ui-2', 'asset-api-ui-2', 'POST', '/v1/orders', 'EXCEPTION', 400)
        ],
      scriptBundles
    };
  }

  private generationTask(id: string, generationMode: string, apiCount: number, caseCount: number, createdAt: string) {
    return {
      id,
      projectId: 'project-wp6-ui-smoke',
      specId: 'spec-ui-1',
      generationMode,
      coverageTypes: ['SMOKE', 'EXCEPTION'],
      status: 'COMPLETED',
      apiCount,
      caseCount,
      inputSummary: { aggregateOnly: true },
      createdAt,
      updatedAt: createdAt
    };
  }

  private case(
    id: string,
    endpointSnapshotId: string,
    assetApiId: string,
    httpMethod: string,
    path: string,
    coverageType: string,
    expectedStatus: number
  ) {
    return {
      id,
      endpointSnapshotId,
      assetApiId,
      assetTestCaseId: 'asset-case-smoke-1',
      title: `${coverageType} ${httpMethod} ${path}`,
      httpMethod,
      path,
      coverageType,
      expectedStatus,
      assertionSummary: { expectedStatus, aggregateOnly: true },
      requestTemplate: { aggregateOnly: true },
      source: 'FALLBACK',
      status: 'DRAFT',
      createdAt: '2026-06-13T00:02:00Z',
      updatedAt: '2026-06-13T00:02:00Z'
    };
  }

  private scriptBundle() {
    return {
      id: 'bundle-ui-1',
      projectId: 'project-wp6-ui-smoke',
      taskId: 'task-ui-1',
      status: this.bundleStatus,
      bundleDigest: 'sha256:bundle-ui',
      fileCount: 3,
      fileTreeSummary: {
        files: [
          { path: 'tests/test_generated_api.py', digest: 'sha256:file-test' },
          { path: 'tests/conftest.py', digest: 'sha256:file-conftest' },
          { path: 'README.md', digest: 'sha256:file-readme' }
        ]
      },
      dependencySummary: { dependencies: [{ name: 'pytest' }, { name: 'httpx' }] },
      staticCheckStatus: 'PASSED',
      staticCheckSummary: { pythonSyntax: 'PASSED', secretPattern: 'PASSED' },
      reviewNote: this.reviewNotes.at(-1),
      submittedBy: this.bundleStatus === 'DRAFT' ? undefined : 'user-wp6-ui-smoke',
      approvedBy: this.bundleStatus === 'APPROVED' ? 'user-wp6-ui-smoke' : undefined,
      submittedAt: this.bundleStatus === 'DRAFT' ? undefined : '2026-06-13T00:03:00Z',
      approvedAt: this.bundleStatus === 'APPROVED' ? '2026-06-13T00:04:00Z' : undefined,
      createdAt: '2026-06-13T00:03:00Z',
      updatedAt: '2026-06-13T00:04:00Z'
    };
  }

  private runDetail(status: 'RUNNING' | 'CANCELED') {
    const canceled = status === 'CANCELED';
    return {
      run: {
        id: 'run-ui-1',
        projectId: 'project-wp6-ui-smoke',
        bundleId: 'bundle-ui-1',
        environmentId: 'staging',
        baseUrlDigest: 'sha256:base-url-ui',
        baseUrlHost: 'api.wp6-smoke.example.test',
        status,
        timeoutSeconds: 120,
        caseCount: 1,
        traceId: canceled ? 'trace-run-cancel' : 'trace-run-create',
        runnerMode: 'MANAGED',
        errorCode: canceled ? 'RUNNER_CANCELED' : undefined,
        errorSummary: canceled ? 'canceled by ui smoke' : undefined,
        startedAt: '2026-06-13T00:05:00Z',
        completedAt: canceled ? '2026-06-13T00:05:30Z' : undefined,
        createdAt: '2026-06-13T00:05:00Z',
        updatedAt: '2026-06-13T00:05:30Z'
      },
      results: [{
        id: 'result-ui-1',
        runId: 'run-ui-1',
        caseId: 'case-ui-1',
        status: canceled ? 'CANCELED' : 'RUNNING',
        durationMs: canceled ? 300 : 0,
        assertionSummary: { aggregateOnly: true, expectedStatus: 200 },
        errorCode: canceled ? 'RUNNER_CANCELED' : undefined,
        errorSummary: canceled ? 'canceled by ui smoke' : undefined,
        createdAt: '2026-06-13T00:05:00Z',
        updatedAt: '2026-06-13T00:05:30Z'
      }]
    };
  }

  private page<T>(items: T[]) {
    return {
      items,
      content: items,
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
