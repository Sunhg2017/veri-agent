import { expect, test, type Page, type Route } from '@playwright/test';

const wp9Permissions = [
  'execution:read',
  'execution:manage',
  'execution:trigger',
  'execution:export'
];

const smokeViewports = [
  { name: 'desktop', width: 1280, height: 900, assertResponsive: false },
  { name: 'mobile', width: 390, height: 844, assertResponsive: true }
] as const;

for (const viewport of smokeViewports) {
  test(`WP9 execution browser smoke covers main flow on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await runWp9MainFlow(page, viewport.assertResponsive);
  });
}

async function runWp9MainFlow(page: Page, assertResponsive: boolean) {
  const mock = new Wp9ExecutionMock();
  await mock.install(page);

  await page.addInitScript(() => {
    window.localStorage.setItem('veri-agent.access-token', 'wp9-ui-smoke-token');
  });

  await page.goto('/#execution');

  await expect(page.getByRole('heading', { name: '执行编排' })).toBeVisible();
  await expect(page.locator('.execution-policy-item').filter({ hasText: 'Scheduler' }).getByText('ENABLED')).toBeVisible();
  await expect(page.getByTestId('execution-plan-list').getByText('Baseline WP9 smoke plan')).toBeVisible();
  await expect(page.getByTestId('execution-run-detail').getByText('EXECUTION_RUN_FAILED').first()).toBeVisible();

  const planForm = page.locator('form.panel').first();
  await expect(planForm.locator('.panel-title')).toHaveText('编辑计划');
  await expect(planForm.getByLabel('环境')).toHaveValue('staging');
  await planForm.getByRole('button', { name: '新建' }).click();
  await expect(planForm.locator('.panel-title')).toHaveText('新建计划');
  await expect(planForm.locator('.execution-node-editor')).toHaveCount(1);
  await planForm.getByLabel('项目').fill('project-wp9-ui-smoke');
  await planForm.getByLabel('名称').fill('WP9 UI smoke plan');
  await planForm.getByLabel('环境').fill('staging');
  await selectAntdOption(page, planForm.getByLabel('状态'), '就绪');
  await planForm.getByLabel('描述').fill('desktop and mobile browser smoke');

  await planForm.getByRole('button', { name: '添加节点' }).click();
  const nodeEditors = planForm.locator('.execution-node-editor');
  await nodeEditors.nth(0).getByLabel('node key').fill('api-smoke');
  await nodeEditors.nth(0).getByLabel('bundleId').fill('bundle-wp9-ui-smoke');
  await nodeEditors.nth(0).getByLabel('baseUrlRef').fill('env:staging');
  await nodeEditors.nth(0).getByLabel('caseIds').fill('case-ui-1, case-ui-2');
  await nodeEditors.nth(0).getByLabel('secretRefs').fill('secret://wp9/ui-runtime');
  await nodeEditors.nth(0).getByLabel('重试次数').fill('2');
  await nodeEditors.nth(1).getByLabel('node key').fill('report');
  await selectAntdOption(page, nodeEditors.nth(1).getByLabel('节点类型'), '报告交接');
  await nodeEditors.nth(1).getByLabel('依赖').fill('api-smoke');
  await selectAntdOption(page, nodeEditors.nth(1).getByLabel('失败策略'), '继续');
  await nodeEditors.nth(1).getByLabel('重试次数').fill('0');

  await planForm.getByRole('button', { name: '创建' }).click();
  await expect(page.getByText('执行计划已创建')).toBeVisible();

  const createdNodes = ((mock.createPlanPayload?.dag as { nodes?: Array<Record<string, unknown>> } | undefined)?.nodes ?? []);
  expect(createdNodes).toHaveLength(2);
  expect(createdNodes[0]).toMatchObject({
    key: 'api-smoke',
    type: 'API_TEST',
    input: {
      apiAutomationBundleId: 'bundle-wp9-ui-smoke',
      baseUrlRef: 'env:staging',
      caseIds: ['case-ui-1', 'case-ui-2'],
      runtimeSecretRefs: ['secret://wp9/ui-runtime'],
      rawBaseUrlStored: false,
      secretRefsStored: false
    }
  });
  expect(createdNodes[1]).toMatchObject({
    key: 'report',
    type: 'REPORT_HANDOFF',
    dependencies: ['api-smoke']
  });

  const editForm = page.locator('form.panel').first();
  await expect(editForm.locator('.panel-title')).toHaveText('编辑计划');
  await editForm.getByLabel('描述').fill('updated from wp9 browser smoke');
  await editForm.getByRole('button', { name: '保存更新' }).click();
  await expect(page.getByText('执行计划已更新')).toBeVisible();
  expect(((mock.updatePlanPayload?.dag as { nodes?: unknown[] } | undefined)?.nodes ?? [])).toHaveLength(2);

  const dagPanel = page.getByTestId('execution-dag-preview');
  await dagPanel.getByRole('button', { name: 'Dry run' }).click();
  await expect(dagPanel.getByText('VALID')).toBeVisible();
  await expect(dagPanel.getByText('issues 0')).toBeVisible();

  await dagPanel.getByLabel('requestKey').fill('wp9-ui-request-1');
  await dagPanel.getByLabel('原因').fill('browser smoke manual run');
  await dagPanel.getByRole('button', { name: '运行' }).click();
  await expect(page.getByText('运行已触发')).toBeVisible();
  expect(mock.runPayload).toMatchObject({
    requestKey: 'wp9-ui-request-1',
    reason: 'browser smoke manual run'
  });

  const runPanel = page.getByTestId('execution-run-detail');
  await expect(runPanel.getByText('trace-run-create')).toBeVisible();
  await expect(runPanel.getByText('RUNNING').first()).toBeVisible();
  await runPanel.getByRole('button', { name: '取消' }).click();
  await expect(page.getByText('运行已取消或保持终态')).toBeVisible();
  await expect(runPanel.getByText('CANCELED').first()).toBeVisible();
  await expect(runPanel.getByText('EXECUTION_RUN_CANCELED').first()).toBeVisible();
  expect(mock.cancelSeen).toBe(true);

  await runPanel.locator('.execution-run-item').filter({ hasText: 'FAILED' }).click();
  await expect(runPanel.getByText('EXECUTION_RUN_FAILED').first()).toBeVisible();
  await runPanel.getByRole('button', { name: '重试' }).click();
  await expect(page.getByText('重试已提交')).toBeVisible();
  await expect(runPanel.getByText('trace-run-retry')).toBeVisible();
  expect(mock.retrySeen).toBe(true);

  const triggerPanel = page.locator('section.panel').filter({ hasText: '触发配置' });
  await triggerPanel.getByLabel('source').fill('github');
  await triggerPanel.getByLabel('eventType').fill('deployment_status');
  await triggerPanel.getByLabel('secretRef').fill('secret://wp9/webhook-ui');
  await triggerPanel.getByRole('button', { name: '新增' }).click();
  await expect(page.getByText('触发配置已创建')).toBeVisible();
  expect(mock.triggerPayload).toMatchObject({
    triggerType: 'WEBHOOK',
    status: 'DISABLED',
    config: { source: 'github', eventType: 'deployment_status' },
    secretRef: 'secret://wp9/webhook-ui'
  });

  const createdTrigger = triggerPanel.locator('.execution-trigger-item').first();
  await expect(createdTrigger.getByText('secret configured')).toBeVisible();
  await createdTrigger.getByRole('button', { name: 'Dry run' }).click();
  await expect(triggerPanel.getByText('VALID')).toBeVisible();
  await expect(triggerPanel.getByText('global on')).toBeVisible();
  await createdTrigger.getByRole('button', { name: '事件' }).click();
  await expect(triggerPanel.getByText('evt-ui-created')).toBeVisible();
  await createdTrigger.getByRole('button', { name: '启用' }).click();
  await expect(page.getByText('触发配置已更新为 ENABLED')).toBeVisible();
  expect(await triggerPanel.innerText()).not.toContain('secret://wp9/webhook-ui');

  if (assertResponsive) {
    await expectNoHorizontalOverflow(page, '[data-testid="execution-workbench"]');
    await expect(page.locator('.execution-panel-actions').first()).toBeVisible();
    await expect(page.locator('.execution-node-card').filter({ hasText: 'api-smoke' }).first()).toBeVisible();
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

class Wp9ExecutionMock {
  createPlanPayload: Record<string, unknown> | undefined;
  updatePlanPayload: Record<string, unknown> | undefined;
  runPayload: Record<string, unknown> | undefined;
  triggerPayload: Record<string, unknown> | undefined;
  cancelSeen = false;
  retrySeen = false;

  private plans: Array<Record<string, unknown>> = [this.planDetail('plan-ui-1', 'Baseline WP9 smoke plan', 'READY')];
  private runs: Array<Record<string, unknown>> = [this.runDetail('run-failed-1', 'FAILED', 'plan-ui-1')];
  private runDetails = new Map<string, Record<string, unknown>>([
    ['run-failed-1', this.runDetail('run-failed-1', 'FAILED', 'plan-ui-1')]
  ]);
  private triggersByPlan = new Map<string, Array<Record<string, unknown>>>([
    ['plan-ui-1', [this.trigger('trigger-ui-1', 'plan-ui-1', 'ENABLED')]]
  ]);
  private triggerEventsByTrigger = new Map<string, Array<Record<string, unknown>>>([
    ['trigger-ui-1', [this.triggerEvent('event-ui-1', 'trigger-ui-1', 'evt-ui-baseline')]]
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
        timestamp: '2026-06-14T00:00:00Z'
      }, 'trace-platform-health');
    }

    if (path === '/api/v1/auth/me') {
      return this.fulfill(route, {
        user_id: 'user-wp9-ui-smoke',
        username: 'wp9-ui-smoke',
        display_name: 'WP9 UI Smoke',
        must_change_password: false,
        roles: ['ExecutionOwner'],
        permissions: wp9Permissions
      }, 'trace-auth-me');
    }

    if (path.startsWith('/api/v1/management/') && method === 'GET') {
      return this.fulfill(route, this.page([]), 'trace-management-skipped');
    }

    if (path === '/api/v1/execution/health') {
      return this.fulfill(route, {
        service: 'execution',
        status: 'UP',
        schedulerEnabled: true,
        webhookEnabled: true,
        cronEnabled: true,
        schedulerIntervalMs: 5000,
        schedulerInitialDelayMs: 1000,
        schedulerWorkerId: 'wp9-ui-smoke-worker',
        schedulerTickBatchSize: 10,
        maxConcurrentRunsPerProject: 2,
        maxConcurrentNodesPerRun: 4,
        nodeHeartbeatTimeoutSeconds: 60,
        defaultRunTimeoutSeconds: 1800,
        recoveryBatchSize: 20,
        policy: {
          schedulerLoopReady: true,
          wp6DispatchReady: true,
          cronScannerReady: true,
          rawBaseUrlExported: false,
          secretValuesExported: false
        }
      }, 'trace-execution-health');
    }

    if (path === '/api/v1/execution/plans' && method === 'GET') {
      return this.fulfill(route, this.page(this.plans), 'trace-plan-list');
    }

    if (path === '/api/v1/execution/plans' && method === 'POST') {
      this.createPlanPayload = request.postDataJSON() as Record<string, unknown>;
      const plan = this.planFromPayload('plan-ui-created', this.createPlanPayload, 'sha256:dag-created');
      this.plans = [plan, ...this.plans.filter((item) => item.id !== plan.id)];
      this.triggersByPlan.set(String(plan.id), []);
      return this.fulfill(route, plan, 'trace-plan-create');
    }

    const planDetailMatch = path.match(/^\/api\/v1\/execution\/plans\/([^/]+)$/);
    if (planDetailMatch && method === 'GET') {
      return this.fulfill(route, this.findPlan(planDetailMatch[1]), 'trace-plan-detail');
    }

    if (planDetailMatch && method === 'PATCH') {
      this.updatePlanPayload = request.postDataJSON() as Record<string, unknown>;
      const plan = this.updatePlanFromPayload(planDetailMatch[1], this.updatePlanPayload);
      this.plans = this.plans.map((item) => item.id === plan.id ? plan : item);
      return this.fulfill(route, plan, 'trace-plan-update');
    }

    const archiveMatch = path.match(/^\/api\/v1\/execution\/plans\/([^/]+)\/archive$/);
    if (archiveMatch && method === 'POST') {
      const plan = { ...this.findPlan(archiveMatch[1]), status: 'ARCHIVED', archivedAt: '2026-06-14T00:10:00Z' };
      this.plans = this.plans.map((item) => item.id === plan.id ? plan : item);
      return this.fulfill(route, plan, 'trace-plan-archive');
    }

    const planDryRunMatch = path.match(/^\/api\/v1\/execution\/plans\/([^/]+)\/dry-run$/);
    if (planDryRunMatch && method === 'POST') {
      return this.fulfill(route, this.dryRun(planDryRunMatch[1]), 'trace-plan-dry-run');
    }

    const planRunMatch = path.match(/^\/api\/v1\/execution\/plans\/([^/]+)\/runs$/);
    if (planRunMatch && method === 'POST') {
      this.runPayload = request.postDataJSON() as Record<string, unknown>;
      const run = this.runDetail('run-ui-created', 'RUNNING', planRunMatch[1], this.runPayload);
      this.runDetails.set(String(run.id), run);
      this.runs = [run, ...this.runs.filter((item) => item.id !== run.id)];
      return this.fulfill(route, run, 'trace-run-create');
    }

    if (path === '/api/v1/execution/runs' && method === 'GET') {
      return this.fulfill(route, this.page(this.runs), 'trace-run-list');
    }

    const runDetailMatch = path.match(/^\/api\/v1\/execution\/runs\/([^/]+)$/);
    if (runDetailMatch && method === 'GET') {
      return this.fulfill(route, this.runDetails.get(runDetailMatch[1]) ?? this.runDetail(runDetailMatch[1], 'RUNNING', 'plan-ui-1'), 'trace-run-detail');
    }

    const cancelMatch = path.match(/^\/api\/v1\/execution\/runs\/([^/]+)\/cancel$/);
    if (cancelMatch && method === 'POST') {
      this.cancelSeen = true;
      const current = this.runDetails.get(cancelMatch[1]) ?? this.runDetail(cancelMatch[1], 'RUNNING', 'plan-ui-created');
      const run = this.runDetail(cancelMatch[1], 'CANCELED', stringValue(current.planId), current);
      this.runDetails.set(cancelMatch[1], run);
      this.runs = this.runs.map((item) => item.id === run.id ? run : item);
      return this.fulfill(route, run, 'trace-run-cancel');
    }

    const retryMatch = path.match(/^\/api\/v1\/execution\/runs\/([^/]+)\/retry$/);
    if (retryMatch && method === 'POST') {
      this.retrySeen = true;
      const current = this.runDetails.get(retryMatch[1]) ?? this.runDetail(retryMatch[1], 'FAILED', 'plan-ui-1');
      const run = this.runDetail(retryMatch[1], 'QUEUED', stringValue(current.planId), current);
      this.runDetails.set(retryMatch[1], run);
      this.runs = this.runs.map((item) => item.id === run.id ? run : item);
      return this.fulfill(route, run, 'trace-run-retry');
    }

    const triggerListMatch = path.match(/^\/api\/v1\/execution\/plans\/([^/]+)\/triggers$/);
    if (triggerListMatch && method === 'GET') {
      return this.fulfill(route, this.page(this.triggersByPlan.get(triggerListMatch[1]) ?? []), 'trace-trigger-list');
    }

    if (triggerListMatch && method === 'POST') {
      this.triggerPayload = request.postDataJSON() as Record<string, unknown>;
      const trigger = this.trigger('trigger-ui-created', triggerListMatch[1], stringValue(this.triggerPayload.status, 'DISABLED'), this.triggerPayload);
      const current = this.triggersByPlan.get(triggerListMatch[1]) ?? [];
      this.triggersByPlan.set(triggerListMatch[1], [trigger, ...current.filter((item) => item.id !== trigger.id)]);
      this.triggerEventsByTrigger.set(String(trigger.id), [
        this.triggerEvent('event-ui-created', String(trigger.id), 'evt-ui-created')
      ]);
      return this.fulfill(route, trigger, 'trace-trigger-create');
    }

    const triggerPatchMatch = path.match(/^\/api\/v1\/execution\/triggers\/([^/]+)$/);
    if (triggerPatchMatch && method === 'PATCH') {
      const payload = request.postDataJSON() as Record<string, unknown>;
      const trigger = this.updateTrigger(triggerPatchMatch[1], stringValue(payload.status, 'ENABLED'));
      return this.fulfill(route, trigger, 'trace-trigger-update');
    }

    const triggerDryRunMatch = path.match(/^\/api\/v1\/execution\/triggers\/([^/]+)\/dry-run$/);
    if (triggerDryRunMatch && method === 'POST') {
      return this.fulfill(route, {
        id: triggerDryRunMatch[1],
        triggerType: 'WEBHOOK',
        valid: true,
        globalEnabled: true,
        runCreated: false,
        policy: { signatureRequired: true, sourceEventIdIdempotent: true }
      }, 'trace-trigger-dry-run');
    }

    const triggerEventsMatch = path.match(/^\/api\/v1\/execution\/triggers\/([^/]+)\/events$/);
    if (triggerEventsMatch && method === 'GET') {
      return this.fulfill(route, this.page(this.triggerEventsByTrigger.get(triggerEventsMatch[1]) ?? []), 'trace-trigger-events');
    }

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'NOT_FOUND',
        message: `Unhandled WP9 smoke mock route: ${method} ${path}`,
        trace_id: 'trace-unhandled',
        data: {}
      })
    });
  }

  private findPlan(id: string) {
    return this.plans.find((plan) => plan.id === id) ?? this.planDetail(id, 'Baseline WP9 smoke plan', 'READY');
  }

  private planDetail(id: string, name: string, status: string) {
    return {
      id,
      projectId: 'project-wp9-ui-smoke',
      name,
      status,
      environmentKey: 'staging',
      description: 'baseline plan for wp9 smoke',
      dagDigest: `sha256:dag-${id}`,
      nodeCount: 2,
      triggerPolicy: { manualEnabled: true, webhookEnabled: true, cronEnabled: false },
      nodes: [
        this.planNodeFromPayload({
          key: 'api-smoke',
          type: 'API_TEST',
          dependencies: [],
          input: {
            apiAutomationBundleId: 'bundle-ui-baseline',
            baseUrlRef: 'env:staging',
            caseIds: ['case-ui-1']
          },
          timeoutSeconds: 180,
          failurePolicy: 'FAIL_FAST',
          retryPolicy: { maxAttempts: 1 }
        }, 0),
        this.planNodeFromPayload({
          key: 'report',
          type: 'REPORT_HANDOFF',
          dependencies: ['api-smoke'],
          input: { reportProfile: 'wp10-summary' },
          timeoutSeconds: 120,
          failurePolicy: 'CONTINUE',
          retryPolicy: { maxAttempts: 0 }
        }, 1)
      ],
      createdBy: 'wp9-ui-smoke',
      updatedBy: 'wp9-ui-smoke',
      createdAt: '2026-06-14T00:00:00Z',
      updatedAt: '2026-06-14T00:01:00Z'
    };
  }

  private planFromPayload(id: string, payload: Record<string, unknown>, dagDigest: string) {
    const dag = objectValue(payload.dag);
    const nodes = arrayValue(dag.nodes);
    return {
      id,
      projectId: stringValue(payload.projectId),
      name: stringValue(payload.name),
      status: stringValue(payload.status, 'DRAFT'),
      environmentKey: stringValue(payload.environmentKey),
      description: optionalString(payload.description),
      dagDigest,
      nodeCount: nodes.length,
      triggerPolicy: objectValue(payload.triggerPolicy),
      nodes: nodes.map((node, index) => this.planNodeFromPayload(objectValue(node), index)),
      createdBy: 'wp9-ui-smoke',
      updatedBy: 'wp9-ui-smoke',
      createdAt: '2026-06-14T00:02:00Z',
      updatedAt: '2026-06-14T00:02:00Z'
    };
  }

  private updatePlanFromPayload(id: string, payload: Record<string, unknown>) {
    const current = this.findPlan(id);
    const merged = {
      ...payload,
      projectId: current.projectId,
      name: payload.name ?? current.name,
      status: payload.status ?? current.status,
      environmentKey: payload.environmentKey ?? current.environmentKey,
      triggerPolicy: payload.triggerPolicy ?? current.triggerPolicy,
      dag: payload.dag ?? { nodes: current.nodes }
    };
    return {
      ...this.planFromPayload(id, merged, 'sha256:dag-updated'),
      createdAt: current.createdAt,
      updatedAt: '2026-06-14T00:03:00Z'
    };
  }

  private planNodeFromPayload(payload: Record<string, unknown>, index: number) {
    const input = objectValue(payload.input);
    return {
      id: `plan-node-ui-${index + 1}`,
      key: stringValue(payload.key),
      type: stringValue(payload.type, 'API_TEST'),
      dependencies: stringArray(payload.dependencies),
      inputSummary: this.inputSummary(input),
      failurePolicy: stringValue(payload.failurePolicy, 'FAIL_FAST'),
      timeoutSeconds: numberValue(payload.timeoutSeconds, 180),
      retryPolicy: objectValue(payload.retryPolicy),
      createdAt: '2026-06-14T00:02:00Z',
      updatedAt: '2026-06-14T00:02:00Z'
    };
  }

  private inputSummary(input: Record<string, unknown>) {
    const runtimeSecretRefs = stringArray(input.runtimeSecretRefs);
    return {
      apiAutomationBundleId: optionalString(input.apiAutomationBundleId),
      baseUrlRef: optionalString(input.baseUrlRef),
      caseIds: stringArray(input.caseIds),
      runtimeSecretRefCount: runtimeSecretRefs.length,
      runtimeSecretRefDigest: runtimeSecretRefs.length ? 'sha256:runtime-secret-ui' : undefined,
      rawBaseUrlStored: false,
      secretRefsStored: false
    };
  }

  private dryRun(planId: string) {
    const plan = this.findPlan(planId);
    return {
      planId,
      valid: true,
      dagDigest: plan.dagDigest,
      nodes: arrayValue(plan.nodes).map((node) => ({
        key: stringValue(objectValue(node).key),
        type: stringValue(objectValue(node).type, 'API_TEST'),
        dependencies: stringArray(objectValue(node).dependencies),
        failurePolicy: stringValue(objectValue(node).failurePolicy, 'FAIL_FAST'),
        timeoutSeconds: numberValue(objectValue(node).timeoutSeconds, 180),
        retryPolicy: objectValue(objectValue(node).retryPolicy),
        inputSummary: objectValue(objectValue(node).inputSummary),
        runnerType: stringValue(objectValue(node).type) === 'REPORT_HANDOFF' ? 'CONTROL' : 'WP6_API'
      })),
      issues: [],
      policy: { wp6DispatchReady: true, rawSecretsExported: false }
    };
  }

  private runDetail(
    id: string,
    status: string,
    planId: string,
    source: Record<string, unknown> = {}
  ) {
    const failed = status === 'FAILED';
    const canceled = status === 'CANCELED';
    const queued = status === 'QUEUED';
    const requestKey = optionalString(source.requestKey) ?? optionalString(source.sourceEventId) ?? 'wp9-ui-request-baseline';
    return {
      id,
      planId,
      projectId: 'project-wp9-ui-smoke',
      status,
      triggerType: 'MANUAL',
      requestKey,
      attempt: queued ? 2 : 1,
      traceId: failed ? 'trace-run-failed' : canceled ? 'trace-run-cancel' : queued ? 'trace-run-retry' : 'trace-run-create',
      resultSummary: {
        aggregateOnly: true,
        rawBaseUrlStored: false,
        secretValuesStored: false
      },
      nodeCount: 2,
      errorCode: failed ? 'EXECUTION_RUN_FAILED' : canceled ? 'EXECUTION_RUN_CANCELED' : undefined,
      errorSummary: failed ? 'wp6 failed by ui smoke' : canceled ? 'canceled by ui smoke' : undefined,
      nodes: [
        this.nodeRun(id, 'api-smoke', failed ? 'FAILED' : canceled ? 'CANCELED' : queued ? 'QUEUED' : 'RUNNING', failed, canceled),
        this.nodeRun(id, 'report', failed ? 'BLOCKED' : canceled ? 'CANCELED' : queued ? 'PENDING' : 'QUEUED', false, canceled)
      ],
      idempotentReplay: false,
      createdBy: 'wp9-ui-smoke',
      createdAt: '2026-06-14T00:04:00Z',
      updatedAt: '2026-06-14T00:05:00Z'
    };
  }

  private nodeRun(runId: string, nodeKey: string, status: string, failed: boolean, canceled: boolean) {
    return {
      id: `${runId}-${nodeKey}`,
      planNodeId: `plan-node-${nodeKey}`,
      nodeKey,
      nodeType: nodeKey === 'report' ? 'REPORT_HANDOFF' : 'API_TEST',
      status,
      attempt: 1,
      runnerType: nodeKey === 'report' ? 'CONTROL' : 'WP6_API',
      externalRunId: nodeKey === 'report' ? undefined : 'wp6-run-ui-1',
      errorCode: failed ? 'EXECUTION_RUN_FAILED' : canceled ? 'EXECUTION_RUN_CANCELED' : undefined,
      errorSummary: failed ? 'wp6 failed by ui smoke' : canceled ? 'canceled by ui smoke' : undefined,
      resultSummary: { aggregateOnly: true, stdoutStored: false, requestResponseStored: false },
      queuedAt: '2026-06-14T00:04:00Z',
      startedAt: status === 'RUNNING' || status === 'FAILED' || status === 'CANCELED' ? '2026-06-14T00:04:10Z' : undefined,
      finishedAt: status === 'FAILED' || status === 'CANCELED' ? '2026-06-14T00:05:00Z' : undefined,
      createdAt: '2026-06-14T00:04:00Z',
      updatedAt: '2026-06-14T00:05:00Z'
    };
  }

  private trigger(id: string, planId: string, status: string, payload: Record<string, unknown> = {}) {
    return {
      id,
      planId,
      triggerType: stringValue(payload.triggerType, 'WEBHOOK'),
      status,
      configDigest: 'sha256:trigger-config-ui',
      configSummary: objectValue(payload.config).source
        ? { source: stringValue(objectValue(payload.config).source), eventType: stringValue(objectValue(payload.config).eventType) }
        : { source: 'ci', eventType: 'deployment' },
      secretRefConfigured: Boolean(optionalString(payload.secretRef) ?? true),
      secretRefDigest: 'sha256:trigger-secret-ui',
      nextFireAt: undefined,
      lastFireAt: '2026-06-14T00:06:00Z',
      createdBy: 'wp9-ui-smoke',
      updatedBy: 'wp9-ui-smoke',
      createdAt: '2026-06-14T00:06:00Z',
      updatedAt: '2026-06-14T00:06:00Z'
    };
  }

  private triggerEvent(id: string, triggerId: string, sourceEventId: string) {
    return {
      id,
      triggerId,
      sourceEventId,
      requestDigest: 'sha256:request-ui',
      status: 'ACCEPTED',
      runId: 'run-ui-created',
      receivedAt: '2026-06-14T00:06:30Z',
      traceId: 'trace-trigger-event'
    };
  }

  private updateTrigger(triggerId: string, status: string) {
    let updated: Record<string, unknown> | undefined;
    for (const [planId, triggers] of this.triggersByPlan.entries()) {
      const next = triggers.map((trigger) => {
        if (trigger.id !== triggerId) return trigger;
        updated = { ...trigger, status, updatedAt: '2026-06-14T00:07:00Z' };
        return updated;
      });
      this.triggersByPlan.set(planId, next);
    }
    return updated ?? this.trigger(triggerId, 'plan-ui-created', status);
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

function numberValue(input: unknown, fallback: number) {
  const value = typeof input === 'number' ? input : Number(input);
  return Number.isFinite(value) ? value : fallback;
}
