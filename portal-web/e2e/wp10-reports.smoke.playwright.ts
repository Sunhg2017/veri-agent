import { expect, test, type Page, type Route } from '@playwright/test';

const wp10Permissions = [
  'report:read',
  'report:generate',
  'report:diagnose',
  'report:export',
  'report:manage'
];

const smokeViewports = [
  { name: 'desktop', width: 1280, height: 900, assertResponsive: false },
  { name: 'mobile', width: 390, height: 844, assertResponsive: true }
] as const;

const existingReportId = '11111111-1111-4111-8111-111111111111';
const generatedReportId = '22222222-2222-4222-8222-222222222222';
const executionRunId = '33333333-3333-4333-8333-333333333333';
const sensitiveSamples = [
  'secret://wp10/ui-smoke',
  'Authorization: Bearer ui-secret',
  'lease token',
  'raw prompt',
  'runner stdout'
];

for (const viewport of smokeViewports) {
  test(`WP10 reports browser smoke covers main flow on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await runWp10MainFlow(page, viewport.assertResponsive);
  });
}

async function runWp10MainFlow(page: Page, assertResponsive: boolean) {
  const mock = new Wp10ReportMock();
  await mock.install(page);

  await page.addInitScript(() => {
    window.localStorage.setItem('veri-agent.access-token', 'wp10-ui-smoke-token');
  });

  await page.goto('/#reports');

  await expect(page.getByRole('heading', { name: '报告诊断' })).toBeVisible();
  await expect(page.getByTestId('reports-workbench')).toBeVisible();
  await expect(page.getByTestId('reports-list').getByRole('button', { name: 'FAILED 33333333...3333 FAILED' })).toBeVisible();
  await expect(page.getByTestId('report-generate-panel')).toBeVisible();
  await expect(page.getByTestId('report-detail').getByText(existingReportId, { exact: true })).toBeVisible();
  await expect(page.getByTestId('report-diagnosis')
    .locator('.report-summary-tile').filter({ hasText: 'primaryCategory' })
    .getByText('RUNNER_FAILURE')).toBeVisible();

  const generatePanel = page.getByTestId('report-generate-panel');
  await generatePanel.getByLabel('projectId').fill('project-wp10-ui-smoke');
  await generatePanel.getByLabel('executionRunId').fill(executionRunId);
  await generatePanel.getByLabel('requestKey').fill('wp10-ui-request-1');
  await generatePanel.getByLabel('reason').fill('browser smoke report generation');
  await generatePanel.getByRole('button', { name: '生成报告' }).click();
  await expect(page.getByText('报告快照已生成')).toBeVisible();
  await expect(page.getByTestId('report-detail').getByText(generatedReportId, { exact: true })).toBeVisible();
  expect(mock.generatePayload).toMatchObject({
    projectId: 'project-wp10-ui-smoke',
    executionRunId,
    requestKey: 'wp10-ui-request-1',
    reason: 'browser smoke report generation'
  });

  const diagnosisPanel = page.getByTestId('report-diagnosis');
  await diagnosisPanel.getByRole('button', { name: '触发诊断' }).click();
  await expect(diagnosisPanel.locator('.report-summary-tile').filter({ hasText: 'status' }).getByText('AI_READY')).toBeVisible();
  await expect(diagnosisPanel.locator('.report-summary-tile').filter({ hasText: 'primaryCategory' }).getByText('DEPENDENCY_BLOCKED')).toBeVisible();
  await expect(diagnosisPanel.locator('.report-summary-tile').filter({ hasText: 'confidence' }).getByText('76%')).toBeVisible();
  expect(mock.diagnoseSeen).toBe(true);

  const defectPanel = page.getByTestId('report-defect-drafts');
  await defectPanel.getByRole('button', { name: '生成缺陷草稿' }).click();
  await expect(defectPanel.getByText('WP10 UI smoke 缺陷草稿')).toBeVisible();
  await expect(defectPanel.getByText('externalSystemWriteAttempted:false')).toBeVisible();
  expect(mock.createDraftSeen).toBe(true);
  await defectPanel.getByRole('button', { name: '审阅草稿' }).click();
  await expect(defectPanel.getByText('草稿状态已更新为 REVIEWED')).toBeVisible();
  expect(mock.reviewDraftStatus).toBe('REVIEWED');

  const comparePanel = page.getByTestId('report-compare-panel');
  await comparePanel.getByLabel('baseline report').selectOption(existingReportId);
  await comparePanel.getByRole('button', { name: '开始对比' }).click();
  await expect(comparePanel.getByText('发现 8 项聚合差异')).toBeVisible();
  await expect(comparePanel.getByText('summary.runStatus')).toBeVisible();
  await expectInfoBlock(page, comparePanel, 'evidence count', '2 -> 3');
  await expectInfoBlock(page, comparePanel, 'draft count', '0 -> 1');

  const exportPanel = page.getByTestId('report-export-panel');
  await exportPanel.getByRole('button', { name: '导出 JSON' }).click();
  await expectInfoBlock(page, exportPanel, 'fieldSetVersion', 'wp10-export-fields-v1');
  await expectInfoBlock(page, exportPanel, 'contentDigest', 'sha256:wp10-export-content');
  await expectInfoBlock(page, exportPanel, 'DOM scan', 'clean');
  expect(mock.exportTypes).toContain('JSON');
  await exportPanel.getByRole('button', { name: '导出 Markdown' }).click();
  await expect(exportPanel.getByText('MARKDOWN 报告已生成')).toBeVisible();
  expect(mock.exportTypes).toContain('MARKDOWN');

  await assertNoSensitiveSamples(page);

  if (assertResponsive) {
    await expectNoHorizontalOverflow(page, '[data-testid="reports-workbench"]');
    await expect(page.getByTestId('reports-list')).toBeVisible();
    await expect(page.getByTestId('report-export-panel')).toBeVisible();
  }
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

async function expectInfoBlock(page: Page, panel: ReturnType<Page['getByTestId']>, title: string, value: string) {
  const block = panel.locator('.report-info-block').filter({
    has: page.locator('span').filter({ hasText: new RegExp(`^${escapeRegExp(title)}$`) })
  });
  await expect(block.locator('strong')).toHaveText(value);
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

class Wp10ReportMock {
  generatePayload: Record<string, unknown> | undefined;
  diagnoseSeen = false;
  createDraftSeen = false;
  reviewDraftStatus = '';
  exportTypes: string[] = [];

  private reports: Array<Record<string, unknown>> = [this.reportSummary(existingReportId, 'FAILED')];
  private details = new Map<string, Record<string, unknown>>([
    [existingReportId, this.reportDetail(existingReportId, 'FAILED')]
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
        timestamp: '2026-06-17T00:00:00Z'
      }, 'trace-platform-health');
    }

    if (path === '/api/v1/auth/me') {
      return this.fulfill(route, {
        user_id: 'user-wp10-ui-smoke',
        username: 'wp10-ui-smoke',
        display_name: 'WP10 UI Smoke',
        must_change_password: false,
        roles: ['ReportOwner'],
        permissions: wp10Permissions
      }, 'trace-auth-me');
    }

    if (path.startsWith('/api/v1/management/') && method === 'GET') {
      return this.fulfill(route, this.page([]), 'trace-management-skipped');
    }

    if (path === '/api/v1/reports/health') {
      return this.fulfill(route, this.health(), 'trace-report-health');
    }

    if (path === '/api/v1/reports' && method === 'GET') {
      const status = url.searchParams.get('status') ?? '';
      const projectId = url.searchParams.get('projectId') ?? '';
      const executionRunFilter = url.searchParams.get('executionRunId') ?? '';
      const filtered = this.reports.filter((report) => {
        return (!status || report.status === status)
          && (!projectId || report.projectId === projectId)
          && (!executionRunFilter || report.executionRunId === executionRunFilter);
      });
      return this.fulfill(route, this.page(filtered), 'trace-report-list');
    }

    if (path === '/api/v1/reports' && method === 'POST') {
      this.generatePayload = request.postDataJSON() as Record<string, unknown>;
      const detail = this.reportDetail(generatedReportId, 'READY', this.generatePayload);
      this.details.set(generatedReportId, detail);
      this.reports = [this.summaryFromDetail(detail), ...this.reports.filter((report) => report.id !== generatedReportId)];
      return this.fulfill(route, detail, 'trace-report-generate');
    }

    const detailMatch = path.match(/^\/api\/v1\/reports\/([^/]+)$/);
    if (detailMatch && method === 'GET') {
      return this.fulfill(route, this.details.get(detailMatch[1]) ?? this.reportDetail(detailMatch[1], 'READY'), 'trace-report-detail');
    }

    const compareMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/compare$/);
    if (compareMatch && method === 'GET') {
      const reportId = compareMatch[1];
      const baselineReportId = url.searchParams.get('baselineReportId') ?? '';
      return this.fulfill(route, this.reportCompare(reportId, baselineReportId), 'trace-report-compare');
    }

    const retryMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/retry$/);
    if (retryMatch && method === 'POST') {
      const detail = this.reportDetail(retryMatch[1], 'READY');
      this.details.set(retryMatch[1], detail);
      this.reports = this.reports.map((report) => report.id === retryMatch[1] ? this.summaryFromDetail(detail) : report);
      return this.fulfill(route, detail, 'trace-report-retry');
    }

    const archiveMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/archive$/);
    if (archiveMatch && method === 'POST') {
      const current = this.details.get(archiveMatch[1]) ?? this.reportDetail(archiveMatch[1], 'READY');
      const detail = { ...current, status: 'ARCHIVED', archivedAt: '2026-06-17T00:20:00Z' };
      this.details.set(archiveMatch[1], detail);
      this.reports = this.reports.map((report) => report.id === archiveMatch[1] ? this.summaryFromDetail(detail) : report);
      return this.fulfill(route, detail, 'trace-report-archive');
    }

    const diagnosisMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/diagnoses$/);
    if (diagnosisMatch && method === 'POST') {
      this.diagnoseSeen = true;
      const diagnosis = this.diagnosis(diagnosisMatch[1], 'AI_READY');
      const current = this.details.get(diagnosisMatch[1]) ?? this.reportDetail(diagnosisMatch[1], 'READY');
      const detail = { ...current, latestDiagnosis: diagnosis, summary: { ...objectValue(current.summary), diagnosisStatus: 'AI_READY', diagnosisPrimaryCategory: 'DEPENDENCY_BLOCKED' } };
      this.details.set(diagnosisMatch[1], detail);
      this.reports = this.reports.map((report) => report.id === diagnosisMatch[1] ? this.summaryFromDetail(detail) : report);
      return this.fulfill(route, diagnosis, 'trace-report-diagnose');
    }

    const latestDiagnosisMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/diagnoses\/latest$/);
    if (latestDiagnosisMatch && method === 'GET') {
      return this.fulfill(route, this.diagnosis(latestDiagnosisMatch[1], 'AI_READY'), 'trace-report-diagnosis-latest');
    }

    const draftMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/defect-drafts$/);
    if (draftMatch && method === 'POST') {
      this.createDraftSeen = true;
      const draft = this.defectDraft(draftMatch[1], 'DRAFT');
      const current = this.details.get(draftMatch[1]) ?? this.reportDetail(draftMatch[1], 'READY');
      const detail = {
        ...current,
        defectDrafts: [draft, ...arrayValue(current.defectDrafts)],
        summary: { ...objectValue(current.summary), defectDraftCount: arrayValue(current.defectDrafts).length + 1 }
      };
      this.details.set(draftMatch[1], detail);
      this.reports = this.reports.map((report) => report.id === draftMatch[1] ? this.summaryFromDetail(detail) : report);
      return this.fulfill(route, draft, 'trace-report-draft-create');
    }

    const reviewDraftMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/defect-drafts\/([^/]+)$/);
    if (reviewDraftMatch && method === 'PATCH') {
      const payload = request.postDataJSON() as { status?: string };
      this.reviewDraftStatus = payload.status ?? '';
      const draft = this.defectDraft(reviewDraftMatch[1], payload.status ?? 'REVIEWED', reviewDraftMatch[2]);
      const current = this.details.get(reviewDraftMatch[1]) ?? this.reportDetail(reviewDraftMatch[1], 'READY');
      const detail = {
        ...current,
        defectDrafts: arrayValue(current.defectDrafts).map((item) => objectValue(item).id === draft.id ? draft : item)
      };
      this.details.set(reviewDraftMatch[1], detail);
      return this.fulfill(route, draft, 'trace-report-draft-review');
    }

    const exportMatch = path.match(/^\/api\/v1\/reports\/([^/]+)\/export$/);
    if (exportMatch && method === 'GET') {
      const exportType = url.searchParams.get('exportType') ?? 'JSON';
      this.exportTypes.push(exportType);
      return this.fulfill(route, this.reportExport(exportMatch[1], exportType), `trace-report-export-${exportType.toLowerCase()}`);
    }

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'NOT_FOUND',
        message: `Unhandled WP10 smoke mock route: ${method} ${path}`,
        trace_id: 'trace-unhandled',
        data: {}
      })
    });
  }

  private health() {
    return {
      service: 'reporting',
      status: 'UP',
      enabled: true,
      generateEnabled: true,
      diagnosisEnabled: true,
      defectDraftEnabled: true,
      exportEnabled: true,
      maxEvidenceItems: 80,
      maxDiagnosisContextChars: 12000,
      maxExportMarkdownChars: 40000,
      schemaVersion: 'wp10-report-v1',
      fieldSetVersion: 'wp10-export-fields-v1',
      policy: {
        aggregateOnly: true,
        rawPromptStored: false,
        rawResponseStored: false,
        externalSystemWriteEnabled: false
      }
    };
  }

  private reportSummary(id: string, status: string) {
    return this.summaryFromDetail(this.reportDetail(id, status));
  }

  private reportDetail(id: string, status: string, source: Record<string, unknown> = {}) {
    const runId = stringValue(source.executionRunId, executionRunId);
    const ready = status === 'READY';
    return {
      id,
      projectId: stringValue(source.projectId, 'project-wp10-ui-smoke'),
      executionRunId: runId,
      requestKey: optionalString(source.requestKey) ?? 'wp10-ui-baseline',
      status,
      schemaVersion: 'wp10-report-v1',
      sourceRunDigest: `sha256:source-${id}`,
      summary: {
        runStatus: ready ? 'FAILED' : status,
        triggerType: 'MANUAL',
        nodeCount: 3,
        evidenceManifestCount: 2,
        defectDraftCount: 0,
        diagnosisStatus: ready ? 'RULE_READY' : 'AI_FAILED',
        diagnosisPrimaryCategory: ready ? 'ASSERTION_FAILED' : 'RUNNER_FAILURE',
        nodeStatusCounts: { FAILED: 1, SUCCEEDED: 2 },
        failureBucketCounts: { ASSERTION_FAILED: 1 },
        sourceSensitiveProbe: sensitiveSamples[0]
      },
      idempotentReplay: false,
      generatedBy: 'wp10-ui-smoke',
      generatedAt: '2026-06-17T00:00:00Z',
      traceId: `trace-report-${id.slice(0, 8)}`,
      redactionPolicy: {
        aggregateOnly: true,
        rawEvidenceIncluded: false,
        rawPromptStored: false,
        rawResponseStored: false,
        providerPayloadStored: false
      },
      evidenceManifests: [
        this.evidence(id, 'WP9', 'RUN_NODE', 'sha256:wp9-manifest'),
        this.evidence(id, 'WP8', 'ACCOUNT_LEASE', 'sha256:wp8-manifest')
      ],
      latestDiagnosis: this.diagnosis(id, ready ? 'RULE_READY' : 'AI_FAILED'),
      defectDrafts: [],
      createdAt: '2026-06-17T00:00:00Z',
      updatedAt: '2026-06-17T00:01:00Z'
    };
  }

  private summaryFromDetail(detail: Record<string, unknown>) {
    return {
      id: stringValue(detail.id),
      projectId: stringValue(detail.projectId),
      executionRunId: stringValue(detail.executionRunId),
      requestKey: optionalString(detail.requestKey),
      status: stringValue(detail.status),
      schemaVersion: stringValue(detail.schemaVersion),
      sourceRunDigest: optionalString(detail.sourceRunDigest),
      summary: objectValue(detail.summary),
      idempotentReplay: Boolean(detail.idempotentReplay),
      generatedBy: optionalString(detail.generatedBy),
      generatedAt: optionalString(detail.generatedAt),
      traceId: optionalString(detail.traceId),
      archivedAt: optionalString(detail.archivedAt),
      createdAt: optionalString(detail.createdAt),
      updatedAt: optionalString(detail.updatedAt)
    };
  }

  private evidence(reportId: string, sourceWp: string, sourceType: string, digest: string) {
    return {
      id: `${reportId}-${sourceWp}`,
      reportId,
      sourceWp,
      sourceType,
      sourceRefDigest: digest,
      schemaVersion: 'wp10-evidence-v1',
      summaryKeys: ['status', 'nodeStatusCounts', 'digest'],
      manifestDigest: digest,
      redactionFlags: {
        aggregateOnly: true,
        secretRefsMasked: true,
        rawArtifactStored: false,
        sensitiveProbe: sensitiveSamples[1]
      },
      createdAt: '2026-06-17T00:00:00Z'
    };
  }

  private diagnosis(reportId: string, status: string) {
    const aiReady = status === 'AI_READY';
    return {
      id: `${reportId}-diagnosis`,
      reportId,
      status,
      classification: {
        primaryCategory: aiReady ? 'DEPENDENCY_BLOCKED' : 'RUNNER_FAILURE',
        source: aiReady ? 'AI' : 'RULE'
      },
      rootCauseCandidates: [
        {
          category: aiReady ? 'DEPENDENCY_BLOCKED' : 'RUNNER_FAILURE',
          summary: aiReady ? '上游节点失败导致报告节点阻断' : 'runner reported failure summary',
          evidenceRefs: ['sha256:wp9-manifest'],
          nextActions: ['检查上游执行节点', '复核失败断言']
        }
      ],
      confidence: aiReady ? 0.76 : 0.62,
      manualReviewRequired: true,
      modelInvocationDigest: aiReady ? 'sha256:model-invocation' : undefined,
      errorCode: aiReady ? undefined : 'REPORT_DIAGNOSIS_POLICY_BLOCKED',
      aiDiagnosisReady: aiReady,
      modelInvoked: aiReady,
      classificationOnly: !aiReady,
      redactionPolicy: {
        aggregateOnly: true,
        rawPromptStored: false,
        rawResponseStored: false
      },
      diagnosisContext: {
        contextDigest: 'sha256:diagnosis-context',
        bounded: true,
        rawPromptProbe: sensitiveSamples[3]
      },
      createdAt: '2026-06-17T00:02:00Z',
      updatedAt: '2026-06-17T00:02:00Z'
    };
  }

  private defectDraft(reportId: string, status: string, draftId = `${reportId}-draft`) {
    return {
      id: draftId,
      reportId,
      diagnosisId: `${reportId}-diagnosis`,
      status,
      title: 'WP10 UI smoke 缺陷草稿',
      reproductionSummary: '报告快照显示上游执行失败，需人工复核。',
      impactSummary: '阻断当前发布验证。',
      prioritySuggestion: 'P1',
      evidenceRefs: ['sha256:wp9-manifest', 'sha256:wp8-manifest'],
      payloadPreview: {
        schemaVersion: 'wp10-defect-preview-v1',
        externalSystem: 'JIRA',
        fieldMappingVersion: 'wp10-jira-v1',
        masked: true,
        aggregateOnly: true,
        externalSystemWriteAttempted: false,
        rawWebhookProbe: sensitiveSamples[1]
      },
      createdBy: 'wp10-ui-smoke',
      updatedBy: 'wp10-ui-smoke',
      createdAt: '2026-06-17T00:03:00Z',
      updatedAt: '2026-06-17T00:04:00Z'
    };
  }

  private reportExport(reportId: string, exportType: string) {
    return {
      id: `${reportId}-export-${exportType.toLowerCase()}`,
      reportId,
      exportType,
      status: 'CREATED',
      schemaVersion: 'wp10-report-export-v1',
      fieldSetVersion: 'wp10-export-fields-v1',
      contentDigest: 'sha256:wp10-export-content',
      aggregateOnly: true,
      exportedBy: 'wp10-ui-smoke',
      exportedAt: '2026-06-17T00:05:00Z',
      redactionPolicy: {
        aggregateOnly: true,
        rawEvidenceIncluded: false,
        rawPromptStored: false,
        rawResponseStored: false
      },
      manifest: {
        digest: 'sha256:wp10-export-manifest',
        fieldSetVersion: 'wp10-export-fields-v1',
        summaryOnly: true
      },
      content: {
        schemaVersion: 'wp10-export-content-v1',
        summaryOnly: true
      },
      createdAt: '2026-06-17T00:05:00Z'
    };
  }

  private reportCompare(reportId: string, baselineReportId: string) {
    return {
      reportId,
      baselineReportId,
      projectId: 'project-wp10-ui-smoke',
      unchanged: false,
      changedFields: [
        'metadata.executionRunId',
        'summary.runStatus',
        'summary.evidenceManifestCount',
        'summary.diagnosisPrimaryCategory',
        'summary.defectDraftCount',
        'diagnosis.status',
        'evidence.count',
        'defectDrafts.count'
      ],
      metadataDiffs: [
        { field: 'executionRunId', baselineValue: executionRunId, currentValue: generatedReportId }
      ],
      summaryDiffs: [
        { field: 'runStatus', baselineValue: 'FAILED', currentValue: 'FAILED' },
        { field: 'evidenceManifestCount', baselineValue: 2, currentValue: 3 }
      ],
      diagnosisDiffs: [
        { field: 'status', baselineValue: 'AI_FAILED', currentValue: 'AI_READY' }
      ],
      evidenceDiff: {
        changed: true,
        baselineCount: 2,
        currentCount: 3,
        addedManifestKeys: ['WP8:ACCOUNT_LEASE:sha256:wp8-manifest'],
        removedManifestKeys: [],
        baselineSourceWpCounts: { WP9: 2 },
        currentSourceWpCounts: { WP9: 2, WP8: 1 },
        baselineSourceTypeCounts: { RUN_NODE: 2 },
        currentSourceTypeCounts: { RUN_NODE: 2, ACCOUNT_LEASE: 1 }
      },
      defectDraftDiff: {
        changed: true,
        baselineCount: 0,
        currentCount: 1,
        baselineStatusCounts: {},
        currentStatusCounts: { REVIEWED: 1 }
      }
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
