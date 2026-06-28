import { expect, test, type Page, type Route } from '@playwright/test';

const wp4Permissions = [
  'requirementInput:read',
  'requirementInput:manage',
  'requirementInput:import',
  'requirementInput:candidate_review',
  'requirementInput:publish',
  'requirementInput:webhook_replay'
];

test('WP4 document input browser smoke covers upload, candidate review, publish preview and event replay', async ({ page }) => {
  const mock = new Wp4DocumentInputMock();
  await mock.install(page);

  await page.addInitScript(() => {
    window.localStorage.setItem('veri-agent.access-token', 'wp4-ui-smoke-token');
  });

  await page.goto('/#document-input');

  await expect(page.getByRole('heading', { name: '文本 / Word / PDF / OCR 导入' })).toBeVisible();
  await expect(page.getByText('WP4 smoke source')).toBeVisible();

  await page.getByRole('button', { name: '发起导入' }).click();
  const importDrawer = page.getByRole('dialog', { name: '文本 / Word / PDF / OCR 导入' });
  await expect(importDrawer).toBeVisible();
  await importDrawer.locator('#import-project-id').fill('project-wp4-ui-smoke');
  await importDrawer.locator('#import-title').fill('WP4 UI smoke upload');
  await selectAntdFieldOption(page, importDrawer, '来源类型', 'Word');
  await importDrawer.locator('#import-source-ref').fill('UI-SMOKE-DOC-1');
  await importDrawer.locator('#import-file').setInputFiles({
    name: 'wp4-smoke-upload.docx',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    buffer: Buffer.from('wp4 document input browser smoke')
  });
  await importDrawer.getByRole('button', { name: '发起导入' }).click();

  await expect(page.getByText('导入任务已提交')).toBeVisible();
  await expect(page.getByText('wp4-smoke-upload.docx')).toBeVisible();
  await expect(page.getByText('上传文件候选')).toBeVisible();
  expect(mock.multipartUploadSeen).toBe(true);

  const candidateCard = page.locator('.document-candidate-card').filter({
    has: page.locator('#candidate-title-cand-ui-1')
  });
  await candidateCard.locator('#candidate-title-cand-ui-1').fill('上传文件候选 - 已编辑');
  await candidateCard.locator('#candidate-priority-cand-ui-1').fill('HIGH');
  await candidateCard.locator('#candidate-acceptance-cand-ui-1').fill('Given 用户上传文档 When 解析完成 Then 候选可审核');
  await candidateCard.locator('#candidate-tags-cand-ui-1').fill('wp4, ui-smoke');
  await candidateCard.getByRole('button', { name: '保存候选需求' }).click();

  await expect(page.getByText('候选需求已保存')).toBeVisible();
  await expect(candidateCard.locator('#candidate-title-cand-ui-1')).toHaveValue('上传文件候选 - 已编辑');
  expect(mock.savedCandidatePayload).toMatchObject({
    title: '上传文件候选 - 已编辑',
    priority: 'HIGH',
    acceptanceCriteria: 'Given 用户上传文档 When 解析完成 Then 候选可审核',
    tags: ['wp4', 'ui-smoke']
  });

  await candidateCard.getByRole('button', { name: '确认' }).click();
  await expect(page.getByText('候选需求已确认')).toBeVisible();
  await page.locator('#candidate-select-cand-ui-1').check();

  await page.getByRole('button', { name: '试运行' }).click();
  await expect(page.getByText('发布预检', { exact: true })).toBeVisible();
  await expect(page.getByText(/已按\s*1 个候选项过滤/)).toBeVisible();
  await expect(page.getByText('CREATE · cand-ui-1')).toBeVisible();
  expect(mock.publishDryRunSeen).toBe(true);

  await page.getByRole('button', { name: /requirement\.changed/ }).click();
  await expect(page.getByText('evt-ui-1')).toBeVisible();
  await page.getByRole('button', { name: '重放事件' }).click();

  await expect(page.getByText('事件已提交重放')).toBeVisible();
  await expect(page.getByText('trace-ui-replay')).toBeVisible();
  await expect(page.getByText('ui-smoke-replay')).toBeVisible();
  expect(mock.replaySeen).toBe(true);
});

async function selectAntdFieldOption(page: Page, panel: ReturnType<Page['getByRole']>, fieldLabel: string, optionName: string) {
  await panel.getByRole('combobox', { name: new RegExp(fieldLabel) }).click();
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content', { hasText: optionName }).first().click();
}

class Wp4DocumentInputMock {
  multipartUploadSeen = false;
  publishDryRunSeen = false;
  replaySeen = false;
  savedCandidatePayload: Record<string, unknown> | undefined;

  private imports = [
    {
      id: 'imp-existing',
      projectId: 'project-wp4-ui-smoke',
      title: '已有 Markdown 导入',
      sourceType: 'MARKDOWN',
      sourceRef: 'UI-SMOKE-OLD',
      status: 'SUCCEEDED',
      createdRequirements: 0,
      requirementCount: 0,
      createdAt: '2026-05-22T00:00:00Z'
    }
  ];

  private candidates = new Map<string, Array<Record<string, unknown>>>();

  private events = [
    {
      id: 'evt-ui-1',
      sourceId: 'src-ui-1',
      importId: 'imp-existing',
      sourceCode: 'wp4-ui-smoke',
      eventId: 'evt-ui-1',
      idempotencyKey: 'idem-ui-1',
      eventType: 'requirement.changed',
      eventVersion: '1.0',
      signatureStatus: 'VALID',
      status: 'FAILED',
      payloadDigest: 'sha256:ui-smoke',
      errorMessage: 'mapping failed before replay',
      retryCount: 1,
      receivedAt: '2026-05-22T01:00:00Z'
    }
  ];

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
        timestamp: '2026-05-22T01:00:00Z'
      }, 'trace-platform-health');
    }

    if (path === '/api/v1/auth/me') {
      return this.fulfill(route, {
        user_id: 'user-wp4-ui-smoke',
        username: 'wp4-ui-smoke',
        display_name: 'WP4 UI Smoke',
        must_change_password: false,
        roles: ['RequirementInputOwner'],
        permissions: wp4Permissions
      }, 'trace-auth-me');
    }

    if (path === '/api/v1/document-input/health') {
      return this.fulfill(route, {
        service: 'document-input',
        status: 'UP',
        inputEnabled: true,
        webhookEnabled: true,
        modelParseEnabled: false,
        webhookMaxPayloadBytes: 1048576,
        importMaxContentBytes: 1048576,
        documentBinaryMaxBytes: 2097152,
        ocrConfigured: false,
        ocrWorkerMode: 'HTTP_WORKER',
        ocrRemoteWorkerConfigured: true,
        ocrWorkerTokenConfigured: true,
        ocrLocalCommandFallbackEnabled: false,
        ocrLocalCommandExecutionAllowed: false,
        webhookSecretCacheEnabled: true,
        webhookSecretCacheTtlSeconds: 60,
        webhookSecretRotationOverlapSeconds: 300,
        webhookSecretCacheSize: 1,
        batchActionLimit: 20
      }, 'trace-document-health');
    }

    if (path === '/api/v1/document-input/sources') {
      return this.fulfill(route, {
        items: [{
          id: 'src-ui-1',
          sourceCode: 'wp4-ui-smoke',
          name: 'WP4 smoke source',
          sourceType: 'CUSTOM_API',
          status: 'ENABLED',
          defaultProjectId: 'project-wp4-ui-smoke',
          endpointUrl: 'https://docs.example.test/wp4-ui-smoke',
          secretRef: 'secret://wp4/ui-smoke',
          eventVersion: '1.0',
          mappingVersion: 'default',
          dataFlowSupported: true
        }]
      }, 'trace-sources');
    }

    if (path === '/api/v1/document-input/field-mapping') {
      return this.fulfill(route, {
        titlePath: 'title',
        descriptionPath: 'description',
        priorityPath: 'priority',
        acceptanceCriteriaPath: 'acceptanceCriteria',
        tagsPath: 'tags'
      }, 'trace-mapping');
    }

    if (path === '/api/v1/document-input/imports' && method === 'GET') {
      return this.fulfill(route, this.page(this.imports), 'trace-import-list');
    }

    if (path === '/api/v1/document-input/imports/multipart' && method === 'POST') {
      const body = (await request.postDataBuffer()).toString('utf8');
      this.multipartUploadSeen = body.includes('name="file"; filename="wp4-smoke-upload.docx"') &&
        body.includes('name="sourceType"') &&
        body.includes('WORD');

      const uploadedImport = {
        id: 'imp-ui-1',
        projectId: 'project-wp4-ui-smoke',
        title: 'WP4 UI smoke upload',
        sourceType: 'WORD',
        sourceRef: 'UI-SMOKE-DOC-1',
        sourceUrl: 'https://docs.example.test/wp4-ui-smoke-upload',
        status: 'SUCCEEDED',
        createdRequirements: 0,
        requirementCount: 1,
        createdAt: '2026-05-22T01:01:00Z',
        requirements: [{
          id: 'preview-ui-1',
          title: '上传文件候选',
          parseSource: 'RULE'
        }]
      };
      this.imports = [uploadedImport, ...this.imports];
      this.candidates.set('imp-ui-1', [this.baseCandidate()]);
      return this.fulfill(route, uploadedImport, 'trace-ui-upload');
    }

    const importMatch = path.match(/^\/api\/v1\/document-input\/imports\/([^/]+)$/);
    if (importMatch && method === 'GET') {
      const importId = decodeURIComponent(importMatch[1]);
      return this.fulfill(route, this.imports.find((item) => item.id === importId) ?? this.imports[0], 'trace-import-detail');
    }

    const candidatesMatch = path.match(/^\/api\/v1\/document-input\/imports\/([^/]+)\/candidates$/);
    if (candidatesMatch && method === 'GET') {
      const importId = decodeURIComponent(candidatesMatch[1]);
      const items = this.candidates.get(importId) ?? [];
      return this.fulfill(route, this.page(items), 'trace-candidates');
    }

    const recordsMatch = path.match(/^\/api\/v1\/document-input\/imports\/([^/]+)\/publish-records$/);
    if (recordsMatch && method === 'GET') {
      return this.fulfill(route, this.page([]), 'trace-publish-records');
    }

    const candidateUpdateMatch = path.match(/^\/api\/v1\/document-input\/candidates\/([^/]+)$/);
    if (candidateUpdateMatch && method === 'PUT') {
      const candidateId = decodeURIComponent(candidateUpdateMatch[1]);
      const payload = request.postDataJSON() as Record<string, unknown>;
      this.savedCandidatePayload = payload;
      const updated = this.updateCandidate(candidateId, {
        ...payload,
        status: 'PENDING',
        version: 2,
        updatedAt: '2026-05-22T01:02:00Z'
      });
      return this.fulfill(route, updated, 'trace-candidate-save');
    }

    const candidateConfirmMatch = path.match(/^\/api\/v1\/document-input\/candidates\/([^/]+)\/confirm$/);
    if (candidateConfirmMatch && method === 'POST') {
      const candidateId = decodeURIComponent(candidateConfirmMatch[1]);
      const confirmed = this.updateCandidate(candidateId, {
        status: 'CONFIRMED',
        confirmedBy: 'user-wp4-ui-smoke',
        confirmedAt: '2026-05-22T01:03:00Z',
        version: 3
      });
      return this.fulfill(route, confirmed, 'trace-candidate-confirm');
    }

    const publishMatch = path.match(/^\/api\/v1\/document-input\/imports\/([^/]+)\/publish$/);
    if (publishMatch && method === 'POST') {
      const importId = decodeURIComponent(publishMatch[1]);
      const payload = request.postDataJSON() as { dryRun?: boolean; candidateIds?: string[] };
      this.publishDryRunSeen = payload.dryRun === true && payload.candidateIds?.includes('cand-ui-1') === true;
      return this.fulfill(route, {
        id: 'pub-ui-1',
        importId,
        projectId: 'project-wp4-ui-smoke',
        sourceId: 'src-ui-1',
        sourceCode: 'wp4-ui-smoke',
        sourceType: 'WORD',
        sourceRef: 'UI-SMOKE-DOC-1',
        title: 'WP4 UI smoke upload',
        status: 'DRY_RUN',
        dryRun: true,
        totalParsed: 1,
        totalCreated: 0,
        createdRequirementIds: [],
        pendingCount: 0,
        confirmedCount: 1,
        publishedCount: 0,
        failedCount: 0,
        plannedCreateCount: 1,
        plannedUpdateCount: 0,
        linkedExistingCount: 0,
        conflictCount: 0,
        skippedCount: 0,
        publishFailedCount: 0,
        records: [{
          candidateId: 'cand-ui-1',
          title: '上传文件候选 - 已编辑',
          candidateStatus: 'CONFIRMED',
          action: 'CREATE',
          result: 'PLANNED',
          projectId: 'project-wp4-ui-smoke',
          externalRequirementId: 'UI-SMOKE-DOC-1#1',
          sourceRef: 'UI-SMOKE-DOC-1',
          version: 1
        }]
      }, 'trace-publish-dry-run');
    }

    if (path === '/api/v1/document-input/webhook-events' && method === 'GET') {
      return this.fulfill(route, this.page(this.events), 'trace-events');
    }

    const eventMatch = path.match(/^\/api\/v1\/document-input\/webhook-events\/([^/]+)$/);
    if (eventMatch && method === 'GET') {
      const eventId = decodeURIComponent(eventMatch[1]);
      return this.fulfill(route, this.events.find((item) => item.id === eventId) ?? this.events[0], 'trace-event-detail');
    }

    const replayMatch = path.match(/^\/api\/v1\/document-input\/webhook-events\/([^/]+)\/replay$/);
    if (replayMatch && method === 'POST') {
      const eventId = decodeURIComponent(replayMatch[1]);
      this.replaySeen = eventId === 'evt-ui-1';
      this.events = this.events.map((event) => event.id === eventId
        ? {
            ...event,
            status: 'REPLAYED',
            replayBy: 'user-wp4-ui-smoke',
            replayAt: '2026-05-22T01:04:00Z',
            replayTraceId: 'ui-smoke-replay',
            errorMessage: undefined,
            retryCount: 2
          }
        : event);
      return this.fulfill(route, this.events.find((item) => item.id === eventId), 'trace-ui-replay');
    }

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'NOT_FOUND',
        message: `Unhandled smoke mock route: ${method} ${path}`,
        trace_id: 'trace-unhandled',
        data: {}
      })
    });
  }

  private baseCandidate() {
    return {
      id: 'cand-ui-1',
      importId: 'imp-ui-1',
      projectId: 'project-wp4-ui-smoke',
      title: '上传文件候选',
      description: '浏览器 smoke 解析出的候选需求',
      priority: 'MEDIUM',
      acceptanceCriteria: 'Given 上传文件 When 解析完成 Then 生成候选',
      tags: ['wp4', 'upload'],
      status: 'PENDING',
      sourceRef: 'UI-SMOKE-DOC-1',
      sourceFragment: '文件上传片段',
      externalRequirementId: 'UI-SMOKE-DOC-1#1',
      confidence: 0.93,
      parseSource: 'RULE',
      version: 1,
      createdAt: '2026-05-22T01:01:30Z'
    };
  }

  private updateCandidate(candidateId: string, patch: Record<string, unknown>) {
    for (const [importId, items] of this.candidates.entries()) {
      const nextItems = items.map((item) => item.id === candidateId ? { ...item, ...patch } : item);
      this.candidates.set(importId, nextItems);
      const updated = nextItems.find((item) => item.id === candidateId);
      if (updated) {
        return updated;
      }
    }
    return { ...this.baseCandidate(), ...patch };
  }

  private page<T>(items: T[]) {
    return {
      items,
      content: items,
      total: items.length,
      page: 1,
      page_size: 20
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
