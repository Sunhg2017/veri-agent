import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, requestJson, requestMultipart } from './client';
import {
  DOCUMENT_SOURCE_TYPES,
  batchDocumentCandidateAction,
  confirmDocumentCandidate,
  createDocumentImport,
  createDocumentImportFile,
  documentCandidateItems,
  documentInputErrorMessage,
  documentImportItems,
  documentPublishRecordItems,
  documentSourceItems,
  fetchDocumentPublishRecords,
  fetchDocumentSourceHealth,
  fetchDocumentCandidates,
  fetchWebhookEvents,
  ignoreDocumentCandidate,
  isReservedSourceType,
  normalizeDocumentCandidateBatchActionResponse,
  normalizeDocumentCandidateView,
  normalizeDocumentPublishView,
  normalizeDocumentSourceHealthView,
  normalizeDocumentSourceView,
  normalizeDocumentImportView,
  normalizeWebhookEventView,
  publishDocumentImport,
  replayWebhookEvent,
  sourceTypeLabel,
  updateDocumentCandidate,
  updateDocumentSource
} from './documentInput';

vi.mock('./client', () => ({
  ApiError: class ApiError extends Error {
    readonly code: string;
    readonly traceId: string;
    readonly status: number;

    constructor(message: string, code: string, traceId: string, status: number) {
      super(message);
      this.name = 'ApiError';
      this.code = code;
      this.traceId = traceId;
      this.status = status;
    }
  },
  requestJson: vi.fn(),
  requestMultipart: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestMultipartMock = vi.mocked(requestMultipart);

describe('document input API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestMultipartMock.mockReset();
  });

  it('exposes every WP4 source type and marks reserved types', () => {
    expect(DOCUMENT_SOURCE_TYPES).toEqual([
      'TEXT',
      'MARKDOWN',
      'CUSTOM_API',
      'WORD',
      'PDF',
      'OCR',
      'CONFLUENCE',
      'FEISHU',
      'DINGTALK',
      'YUQUE'
    ]);

    expect(isReservedSourceType('TEXT')).toBe(false);
    expect(isReservedSourceType('MARKDOWN')).toBe(false);
    expect(isReservedSourceType('CUSTOM_API')).toBe(false);
    expect(isReservedSourceType('WORD')).toBe(false);
    expect(isReservedSourceType('PDF')).toBe(false);
    expect(isReservedSourceType('OCR')).toBe(false);
    expect(isReservedSourceType('CONFLUENCE')).toBe(true);
    expect(sourceTypeLabel('YUQUE')).toBe('语雀');
  });

  it('normalizes camelCase import counters from the WP4 contract', () => {
    const item = normalizeDocumentImportView({
      id: 'imp-1',
      projectId: 'proj-1',
      title: 'Markdown import',
      sourceType: 'MARKDOWN',
      status: 'SUCCESS',
      totalCreated: 3,
      totalParsed: 5
    });

    expect(item).toMatchObject({
      id: 'imp-1',
      projectId: 'proj-1',
      title: 'Markdown import',
      sourceType: 'MARKDOWN',
      status: 'SUCCESS',
      createdRequirements: 3,
      requirementCount: 5
    });
  });

  it('normalizes source fields from the WP4 backend contract', () => {
    const source = normalizeDocumentSourceView({
      id: 'src-1',
      sourceCode: 'payment-docs',
      name: '支付需求入口',
      sourceType: 'CUSTOM_API',
      status: 'ENABLED',
      endpointUrl: 'https://docs.example.test/spec',
      defaultProjectId: 'project-wp4',
      mappingId: '00000000-0000-0000-0000-000000000401',
      secretRef: 'secret://wp4/payment-docs',
      eventVersion: '1.0',
      mappingVersion: 'default',
      description: 'Webhook source',
      dataFlowSupported: true
    });

    expect(source).toMatchObject({
      id: 'src-1',
      sourceCode: 'payment-docs',
      projectId: 'project-wp4',
      title: '支付需求入口',
      sourceType: 'CUSTOM_API',
      sourceUrl: 'https://docs.example.test/spec',
      mappingId: '00000000-0000-0000-0000-000000000401',
      secretRef: 'secret://wp4/payment-docs',
      eventVersion: '1.0',
      mappingVersion: 'default',
      description: 'Webhook source',
      enabled: true,
      dataFlowSupported: true
    });
  });

  it('keeps source update fields that would otherwise be reset by the backend', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-source', data: { id: 'src-1' } });

    await updateDocumentSource('src 1', {
      sourceCode: 'payment-docs',
      name: '支付需求入口',
      sourceType: 'CUSTOM_API',
      status: 'DISABLED',
      endpointUrl: 'https://docs.example.test/spec',
      defaultProjectId: 'project-wp4',
      mappingId: '00000000-0000-0000-0000-000000000401',
      secretRef: 'secret://wp4/payment-docs',
      eventVersion: '1.0',
      mappingVersion: 'default',
      description: 'Webhook source'
    });

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/sources/src%201', {
      method: 'PUT',
      body: JSON.stringify({
        sourceCode: 'payment-docs',
        name: '支付需求入口',
        sourceType: 'CUSTOM_API',
        status: 'DISABLED',
        endpointUrl: 'https://docs.example.test/spec',
        defaultProjectId: 'project-wp4',
        mappingId: '00000000-0000-0000-0000-000000000401',
        secretRef: 'secret://wp4/payment-docs',
        eventVersion: '1.0',
        mappingVersion: 'default',
        description: 'Webhook source'
      })
    });
  });

  it('allows import creation without an optional title', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-import', data: { id: 'imp-1' } });

    await createDocumentImport({
      projectId: 'project-wp4',
      sourceType: 'MARKDOWN',
      sourceRef: 'PRD-1',
      sourceId: 'source-1',
      mappingId: 'mapping-1',
      content: '## 登录需求'
    });

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/imports', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-wp4',
        sourceType: 'MARKDOWN',
        sourceRef: 'PRD-1',
        sourceId: 'source-1',
        mappingId: 'mapping-1',
        content: '## 登录需求'
      })
    });
  });

  it('uploads real document files through the multipart contract', async () => {
    requestMultipartMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-upload', data: { id: 'imp-upload' } });
    const file = new File(['docx-bytes'], 'login.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    });

    await createDocumentImportFile({
      projectId: 'project-wp4',
      sourceType: 'WORD',
      title: 'Login upload',
      sourceRef: 'DOC-1',
      sourceUrl: 'https://docs.example.test/doc-1',
      sourceId: 'source-1',
      mappingId: 'mapping-1',
      file
    });

    expect(requestMultipartMock).toHaveBeenCalledTimes(1);
    const [path, formData] = requestMultipartMock.mock.calls[0];
    expect(path).toBe('/api/v1/document-input/imports/multipart');
    expect(formData.get('projectId')).toBe('project-wp4');
    expect(formData.get('sourceType')).toBe('WORD');
    expect(formData.get('title')).toBe('Login upload');
    expect(formData.get('sourceRef')).toBe('DOC-1');
    expect(formData.get('sourceUrl')).toBe('https://docs.example.test/doc-1');
    expect(formData.get('sourceId')).toBe('source-1');
    expect(formData.get('mappingId')).toBe('mapping-1');
    expect(formData.get('file')).toBe(file);
  });

  it('adds actionable failure hints with error code and traceId', () => {
    const message = documentInputErrorMessage(
      new ApiError('PDF 未抽取到文本，疑似扫描件', 'VALIDATION_ERROR', 'trc_pdf_empty', 400),
      '导入提交失败'
    );

    expect(message).toContain('PDF 未抽取到文本');
    expect(message).toContain('WP4_OCR_COMMAND');
    expect(message).toContain('错误码：VALIDATION_ERROR');
    expect(message).toContain('追踪 ID：trc_pdf_empty');
  });

  it('adds webhook signature troubleshooting hints', () => {
    const message = documentInputErrorMessage(
      new ApiError('webhook 签名无效', 'FORBIDDEN', 'trc_webhook_signature', 403),
      '事件接收失败'
    );

    expect(message).toContain('secretRef/WP4_WEBHOOK_SECRET');
    expect(message).toContain('raw body');
    expect(message).toContain('X-VA-* Header');
    expect(message).toContain('追踪 ID：trc_webhook_signature');
  });

  it('does not duplicate backend next-step guidance', () => {
    const message = documentInputErrorMessage(
      new ApiError('OCR 解析需要配置 WP4_OCR_COMMAND。下一步：请管理员配置 OCR provider。', 'INVALID_STATE', 'trc_ocr', 409),
      '导入提交失败'
    );

    expect(message.match(/建议/g)).toBeNull();
    expect(message).toContain('下一步：请管理员配置 OCR provider。');
    expect(message).toContain('追踪 ID：trc_ocr');
  });

  it('normalizes source health from the WP4 backend contract', () => {
    const health = normalizeDocumentSourceHealthView({
      sourceId: 'src-1',
      sourceCode: 'payment-docs',
      sourceType: 'CUSTOM_API',
      sourceStatus: 'ENABLED',
      dataFlowSupported: true,
      ready: false,
      message: '缺少签名密钥',
      webhookPath: '/api/v1/document-input/webhooks/payment-docs',
      signatureAlgorithm: 'HMAC-SHA256',
      secretRefConfigured: true,
      eventVersion: '1.0',
      mappingVersion: 'default',
      lastEventStatus: 'FAILED',
      lastSignatureStatus: 'VALID'
    });

    expect(health).toMatchObject({
      sourceId: 'src-1',
      sourceCode: 'payment-docs',
      sourceType: 'CUSTOM_API',
      sourceStatus: 'ENABLED',
      dataFlowSupported: true,
      ready: false,
      message: '缺少签名密钥',
      secretRefConfigured: true,
      eventVersion: '1.0',
      mappingVersion: 'default',
      lastEventStatus: 'FAILED',
      lastSignatureStatus: 'VALID'
    });
  });

  it('normalizes legacy snake_case counters and requirement previews', () => {
    const item = normalizeDocumentImportView({
      import_id: 'imp-2',
      project_id: 'proj-2',
      title: 'Text import',
      source_type: 'TEXT',
      status: 'COMPLETED',
      created_requirements: '2',
      requirements: [{ id: 'REQ-1', name: '登录需求' }, { id: 'REQ-2', title: '支付需求' }]
    });

    expect(item.id).toBe('imp-2');
    expect(item.createdRequirements).toBe(2);
    expect(item.requirementCount).toBe(2);
    expect(item.requirements?.map((requirement) => requirement.title)).toEqual(['登录需求', '支付需求']);
  });

  it('extracts list items from array and paged responses', () => {
    expect(documentImportItems([{ id: 'imp-1', title: 'A', sourceType: 'TEXT' }])).toHaveLength(1);
    expect(documentImportItems({ items: [{ id: 'imp-2', title: 'B', sourceType: 'PDF' }] })[0].sourceType).toBe('PDF');

    const sources = documentSourceItems({
      items: [{ source_id: 'source-1', name: 'PRD', source_type: 'CONFLUENCE', project_id: 'proj' }]
    });
    expect(sources[0]).toMatchObject({
      id: 'source-1',
      title: 'PRD',
      sourceType: 'CONFLUENCE',
      projectId: 'proj'
    });
  });

  it('normalizes candidate fields from the confirmation contract', () => {
    const candidate = normalizeDocumentCandidateView({
      id: 'cand-1',
      importId: 'imp-1',
      projectId: 'proj-1',
      title: '支持退款',
      description: '用户可以申请退款',
      priority: 'HIGH',
      acceptanceCriteria: 'Given paid order...',
      tags: ['payment', 'refund'],
      status: 'PENDING',
      sourceRef: 'PRD-1',
      sourceFragment: '## Refund',
      externalRequirementId: 'EXT-9',
      confidence: '92',
      parseSource: 'MODEL',
      modelInvocationId: 'inv-1',
      modelProviderName: 'local-echo-primary',
      modelName: 'local-echo',
      assetRequirementId: 'REQ-7',
      errorMessage: '',
      ignoredReason: 'duplicate',
      confirmedBy: 'u-1',
      confirmedAt: '2026-05-18T01:30:00Z',
      version: '3',
      createdAt: '2026-05-18T01:00:00Z'
    });

    expect(candidate).toMatchObject({
      id: 'cand-1',
      importId: 'imp-1',
      projectId: 'proj-1',
      title: '支持退款',
      priority: 'HIGH',
      acceptanceCriteria: 'Given paid order...',
      tags: ['payment', 'refund'],
      status: 'PENDING',
      confidence: 0.92,
      parseSource: 'MODEL',
      modelInvocationId: 'inv-1',
      modelProviderName: 'local-echo-primary',
      modelName: 'local-echo',
      ignoredReason: 'duplicate',
      confirmedBy: 'u-1',
      confirmedAt: '2026-05-18T01:30:00Z',
      version: 3
    });
  });

  it('normalizes candidate list from Spring page content', () => {
    expect(documentCandidateItems({
      content: [{ candidate_id: 'cand-2', title: '登录', acceptance_criteria: '可登录', tags: 'auth, user' }]
    })[0]).toMatchObject({
      id: 'cand-2',
      title: '登录',
      acceptanceCriteria: '可登录',
      tags: ['auth', 'user']
    });
  });

  it('normalizes batch action and publish records', () => {
    const batch = normalizeDocumentCandidateBatchActionResponse({
      action: 'IGNORE',
      total: 2,
      succeededCount: 1,
      failedCount: 1,
      items: [
        { candidateId: 'cand-1', result: 'SUCCEEDED', candidate: { id: 'cand-1', title: 'A', status: 'IGNORED' } },
        { candidateId: 'cand-2', result: 'FAILED', errorCode: 'INVALID_STATE', errorMessage: '已发布' }
      ]
    });

    expect(batch).toMatchObject({
      action: 'IGNORE',
      total: 2,
      succeededCount: 1,
      failedCount: 1
    });
    expect(batch.items[0].candidate?.status).toBe('IGNORED');
    expect(batch.items[1].errorCode).toBe('INVALID_STATE');

    const publish = normalizeDocumentPublishView({
      id: 'imp-1',
      importId: 'imp-1',
      title: 'Markdown import',
      sourceType: 'MARKDOWN',
      status: 'SUCCESS',
      dryRun: true,
      totalParsed: 3,
      totalCreated: 1,
      plannedCreateCount: 1,
      plannedUpdateCount: 1,
      conflictCount: 1,
      skippedCount: 2,
      records: [
        {
          candidateId: 'cand-1',
          title: 'A',
          candidateStatus: 'CONFIRMED',
          action: 'UPDATE',
          result: 'PLANNED',
          existingRequirementId: 'req-1',
          diffSummary: 'description,tags',
          version: 2
        }
      ]
    });

    expect(publish.dryRun).toBe(true);
    expect(publish.plannedUpdateCount).toBe(1);
    expect(publish.conflictCount).toBe(1);
    expect(publish.records[0]).toMatchObject({
      candidateId: 'cand-1',
      action: 'UPDATE',
      result: 'PLANNED',
      existingRequirementId: 'req-1',
      diffSummary: 'description,tags'
    });
    expect(documentPublishRecordItems({ content: publish.records })).toHaveLength(1);
  });

  it('normalizes webhook event fields', () => {
    const event = normalizeWebhookEventView({
      id: 'row-1',
      source_id: 'src-1',
      import_id: 'imp-1',
      source_code: 'payment-docs',
      event_id: 'evt-1',
      idempotency_key: 'idem-1',
      event_type: 'requirement.changed',
      event_version: 'v1',
      signature_status: 'VALID',
      status: 'FAILED',
      payload_digest: 'sha256:abc',
      error_message: 'mapping failed',
      retry_count: '2',
      replay_by: 'user-001',
      replay_at: '2026-05-18T02:03:00Z',
      replay_trace_id: 'trc_replay',
      received_at: '2026-05-18T02:00:00Z'
    });

    expect(event).toMatchObject({
      id: 'row-1',
      sourceId: 'src-1',
      importId: 'imp-1',
      sourceCode: 'payment-docs',
      eventId: 'evt-1',
      idempotencyKey: 'idem-1',
      eventType: 'requirement.changed',
      signatureStatus: 'VALID',
      status: 'FAILED',
      payloadDigest: 'sha256:abc',
      errorMessage: 'mapping failed',
      retryCount: 2,
      replayBy: 'user-001',
      replayTraceId: 'trc_replay'
    });
  });

  it('calls candidate endpoints with the WP4 paths and bodies', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-1', data: { id: 'cand 1', title: 'A' } });

    await fetchDocumentCandidates('imp 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/imports/imp%201/candidates');

    await fetchDocumentCandidates('imp 1', { status: 'CONFIRMED', sourceRef: 'REQ-1', keyword: '登录' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/imports/imp%201/candidates?status=CONFIRMED&sourceRef=REQ-1&keyword=%E7%99%BB%E5%BD%95');

    await updateDocumentCandidate('cand 1', {
      title: 'A',
      description: 'B',
      priority: 'HIGH',
      acceptanceCriteria: 'Given...',
      tags: ['payment'],
      version: 4
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/candidates/cand%201', {
      method: 'PUT',
      body: JSON.stringify({
        title: 'A',
        description: 'B',
        priority: 'HIGH',
        acceptanceCriteria: 'Given...',
        tags: ['payment'],
        version: 4
      })
    });

    await confirmDocumentCandidate('cand 1', 4);
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/candidates/cand%201/confirm', {
      method: 'POST',
      body: JSON.stringify({ version: 4 })
    });

    await ignoreDocumentCandidate('cand 1', 'duplicate', 5);
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/candidates/cand%201/ignore', {
      method: 'POST',
      body: JSON.stringify({ reason: 'duplicate', version: 5 })
    });

    await publishDocumentImport('imp 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/imports/imp%201/publish', {
      method: 'POST',
      body: '{}'
    });

    await batchDocumentCandidateAction('IGNORE', ['cand 1', 'cand 2'], 'duplicate');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/candidates/batch-action', {
      method: 'POST',
      body: JSON.stringify({ action: 'IGNORE', candidateIds: ['cand 1', 'cand 2'], reason: 'duplicate' })
    });

    await batchDocumentCandidateAction('CONFIRM', [{ id: 'cand 1', version: 2 }]);
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/candidates/batch-action', {
      method: 'POST',
      body: JSON.stringify({ action: 'CONFIRM', candidates: [{ id: 'cand 1', version: 2 }] })
    });

    await publishDocumentImport('imp 1', { dryRun: true, candidateIds: ['cand 1'] });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/imports/imp%201/publish', {
      method: 'POST',
      body: JSON.stringify({ dryRun: true, candidateIds: ['cand 1'] })
    });
  });

  it('calls source health and publish record endpoints', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-2', data: { items: [] } });

    await fetchDocumentSourceHealth('src 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/sources/src%201/health');

    await fetchDocumentPublishRecords('imp 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/imports/imp%201/publish-records');
  });

  it('calls webhook event endpoints with filters and replay', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-2', data: { items: [] } });

    await fetchWebhookEvents({
      index: 1,
      size: 20,
      sourceId: 'src-1',
      sourceCode: 'payment-docs',
      eventType: 'requirement.created',
      status: 'FAILED',
      receivedFrom: '2026-05-19T00:00:00Z',
      receivedTo: '2026-05-20T00:00:00Z'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/document-input/webhook-events?index=1&size=20&sourceId=src-1&sourceCode=payment-docs&eventType=requirement.created&status=FAILED&receivedFrom=2026-05-19T00%3A00%3A00Z&receivedTo=2026-05-20T00%3A00%3A00Z'
    );

    await replayWebhookEvent('evt 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/document-input/webhook-events/evt%201/replay', {
      method: 'POST'
    });
  });
});
