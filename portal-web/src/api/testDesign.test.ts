import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  batchActionTestDesignCandidates,
  confirmTestDesignCandidate,
  createTestDesignTask,
  exportTestDesignCandidatesCsv,
  exportTestDesignReviewRecordsCsv,
  fetchTaskTestDesignCandidates,
  fetchTestDesignCandidates,
  fetchTestDesignHealth,
  fetchTestDesignReviewRecords,
  fetchTestDesignTask,
  fetchTestDesignTaskSummary,
  fetchTestDesignTasks,
  normalizeTestDesignCandidate,
  normalizeTestDesignCandidateBatchActionResult,
  normalizeTestDesignCandidateList,
  normalizeTestDesignHealth,
  normalizeTestDesignPublishResult,
  normalizeTestDesignReviewRecord,
  normalizeTestDesignReviewRecordList,
  normalizeTestDesignTask,
  normalizeTestDesignTaskDetail,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  testDesignCandidateExportPath,
  testDesignReviewRecordExportPath,
  updateTestDesignCandidate
} from './testDesign';

vi.mock('./client', () => ({
  requestJson: vi.fn(),
  requestText: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const requestTextMock = vi.mocked(requestText);

describe('WP5 test design API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestTextMock.mockReset();
  });

  it('exposes test design enums used by the workbench', () => {
    expect(TEST_DESIGN_COVERAGE_TYPES).toEqual(['SMOKE', 'FUNCTIONAL', 'EXCEPTION', 'BOUNDARY', 'PERMISSION', 'REGRESSION']);
    expect(TEST_DESIGN_CANDIDATE_STATUSES).toEqual(['GENERATED', 'EDITED', 'CONFIRMED', 'REJECTED', 'IGNORED', 'PUBLISHED', 'FAILED']);
  });

  it('normalizes health, tasks, candidates and task detail responses', () => {
    expect(normalizeTestDesignHealth({
      status: 'UP',
      generation_mode: 'RULE_BASED',
      prompt_key: 'wp5.case.generate',
      prompt_version: 'v1',
      max_requirements_per_task: '20',
      max_cases_per_requirement: '4',
      supported_coverage_types: 'SMOKE,FUNCTIONAL'
    })).toMatchObject({
      service: 'test-design',
      status: 'UP',
      generationMode: 'RULE_BASED',
      promptKey: 'wp5.case.generate',
      promptVersion: 'v1',
      maxRequirementsPerTask: 20,
      maxCasesPerRequirement: 4,
      supportedCoverageTypes: ['SMOKE', 'FUNCTIONAL']
    });

    const task = normalizeTestDesignTask({
      task_id: 'task-1',
      project_id: 'project-1',
      requirement_ids: 'req-1, req-2',
      coverage_types: ['SMOKE', 'EXCEPTION'],
      total_requirements: '2',
      generated_count: '4',
      confirmed_count: '1',
      published_count: '0',
      idempotency_key: 'wp5-create-001',
      input_digest: 'a'.repeat(64),
      context_summary: {
        contextVersion: 'wp5-context-v1',
        requirements: [{ id: 'req-1', title: '登录需求' }]
      }
    });
    expect(task).toMatchObject({
      id: 'task-1',
      projectId: 'project-1',
      requirementIds: ['req-1', 'req-2'],
      coverageTypes: ['SMOKE', 'EXCEPTION'],
      totalRequirements: 2,
      generatedCount: 4,
      confirmedCount: 1,
      publishedCount: 0,
      idempotencyKey: 'wp5-create-001',
      inputDigest: 'a'.repeat(64)
    });
    expect(task.contextSummary.contextVersion).toBe('wp5-context-v1');

    const candidate = normalizeTestDesignCandidate({
      candidate_id: 'cand-1',
      task_id: 'task-1',
      requirement_id: 'req-1',
      coverage_type: 'boundary',
      priority: 'HIGH',
      steps: [{ step_order: '2', action: '提交', expected_result: '成功' }, { step_order: '1', action: '输入', expected_result: '通过校验' }],
      tags: 'auth, smoke',
      asset_case_id: 'case-1',
      confirmed_at: '2026-05-25T01:00:00Z',
      version: '3'
    });
    expect(candidate).toMatchObject({
      id: 'cand-1',
      taskId: 'task-1',
      requirementId: 'req-1',
      coverageType: 'boundary',
      priority: 'HIGH',
      tags: ['auth', 'smoke'],
      assetCaseId: 'case-1',
      version: 3
    });
    expect(candidate.steps.map((step) => step.stepOrder)).toEqual([1, 2]);

    const detail = normalizeTestDesignTaskDetail({
      task,
      candidates: [candidate],
      publish_records: [{ candidate_id: 'cand-1', dry_run: true, action: 'CREATE', result: 'SKIPPED' }]
    });
    expect(detail.task.id).toBe('task-1');
    expect(detail.candidates).toHaveLength(1);
    expect(detail.publishRecords[0]).toMatchObject({ candidateId: 'cand-1', dryRun: true });
    expect(normalizeTestDesignCandidateList({ content: [{ id: 'cand-2' }], total_elements: '8' }).total).toBe(8);

    const reviewRecord = normalizeTestDesignReviewRecord({
      id: 'review-1',
      task_id: 'task-1',
      candidate_id: 'cand-1',
      action: 'UPDATE',
      before_status: 'GENERATED',
      after_status: 'EDITED',
      has_comment: true,
      comment_preview: 'token=[REDACTED]',
      changed_fields: ['title', 'status'],
      version_before: '1',
      version_after: '2'
    });
    expect(reviewRecord).toMatchObject({
      id: 'review-1',
      taskId: 'task-1',
      candidateId: 'cand-1',
      action: 'UPDATE',
      beforeStatus: 'GENERATED',
      afterStatus: 'EDITED',
      hasComment: true,
      changedFields: ['title', 'status'],
      versionBefore: 1,
      versionAfter: 2
    });
    expect(normalizeTestDesignReviewRecordList({ items: [reviewRecord], total: '3', index: '0', size: '10' })).toMatchObject({
      total: 3,
      index: 0,
      size: 10
    });
  });

  it('calls task and candidate list endpoints with encoded filters', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-list', data: { items: [] } });

    await fetchTestDesignTasks({ index: 1, size: 20, projectId: 'proj pay', status: 'GENERATED', keyword: '登录' });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/tasks?index=1&size=20&projectId=proj+pay&status=GENERATED&keyword=%E7%99%BB%E5%BD%95'
    );

    await fetchTestDesignCandidates({ taskId: 'task 1', projectId: 'proj pay', requirementId: 'req 1', status: 'CONFIRMED', coverageType: 'SMOKE' });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/candidates?taskId=task+1&projectId=proj+pay&requirementId=req+1&status=CONFIRMED&coverageType=SMOKE'
    );

    await fetchTaskTestDesignCandidates('task 1', { index: 2, size: 10, status: 'GENERATED', keyword: '边界' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/candidates?index=2&size=10&status=GENERATED&keyword=%E8%BE%B9%E7%95%8C');

    await fetchTestDesignReviewRecords('task 1', { index: 1, size: 10 });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/review-records?index=1&size=10');

    expect(testDesignCandidateExportPath({
      index: 3,
      size: 50,
      taskId: 'task 1',
      status: 'FAILED',
      keyword: 'token secret'
    })).toBe('/api/v1/test-design/candidates/export?taskId=task+1&status=FAILED&keyword=token+secret');

    expect(testDesignReviewRecordExportPath('task 1')).toBe('/api/v1/test-design/tasks/task%201/review-records/export');

    await fetchTestDesignTask('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201');

    await fetchTestDesignTaskSummary('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/summary');
  });

  it('compacts create and update payloads for the WP5 contract', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-task',
      data: { task: { id: 'task-1' }, candidates: [] }
    });

    await createTestDesignTask({
      projectId: ' project-1 ',
      title: '',
      requirementIds: ['req-1'],
      coverageTypes: ['SMOKE'],
      caseCountPerRequirement: 2,
      idempotencyKey: ' wp5-create-001 '
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-1',
        requirementIds: ['req-1'],
        coverageTypes: ['SMOKE'],
        caseCountPerRequirement: 2,
        idempotencyKey: 'wp5-create-001'
      })
    });

    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-candidate',
      data: { id: 'cand-1', title: '登录成功', version: 2 }
    });

    await updateTestDesignCandidate('cand 1', {
      title: ' 登录成功 ',
      description: '',
      apiId: ' api-1 ',
      steps: [{ action: '输入账号', expectedResult: '校验通过' }],
      tags: [],
      version: 1
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/candidates/cand%201', {
      method: 'PUT',
      body: JSON.stringify({
        title: '登录成功',
        apiId: 'api-1',
        steps: [{ action: '输入账号', expectedResult: '校验通过' }],
        version: 1
      })
    });
  });

  it('calls review and publish endpoints with explicit dry-run semantics', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-candidate',
      data: { id: 'cand-1', status: 'CONFIRMED' }
    });

    await confirmTestDesignCandidate('cand 1', { version: 1, comment: 'ok' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/candidates/cand%201/confirm', {
      method: 'POST',
      body: JSON.stringify({ version: 1, comment: 'ok' })
    });

    await rejectTestDesignCandidate('cand 1', { version: 2, reason: '缺少边界条件' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/candidates/cand%201/reject', {
      method: 'POST',
      body: JSON.stringify({ version: 2, reason: '缺少边界条件' })
    });

    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-publish',
      data: {
        task_id: 'task-1',
        project_id: 'project-1',
        dry_run: true,
        total: '2',
        created: '0',
        skipped: '2',
        failed: '0',
        created_case_ids: 'case-1, case-2',
        records: [{ candidate_id: 'cand-1', dry_run: true, action: 'CREATE', result: 'READY' }]
      }
    });

    const dryRun = await publishTestDesignDryRun('task 1', { candidateIds: ['cand-1'] });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/publish-dry-run', {
      method: 'POST',
      body: JSON.stringify({ candidateIds: ['cand-1'], dryRun: true })
    });
    expect(dryRun.data).toMatchObject({ taskId: 'task-1', dryRun: true, total: 2, skipped: 2 });
    expect(dryRun.data.createdCaseIds).toEqual(['case-1', 'case-2']);

    await publishTestDesignTask('task 1', { candidateIds: ['cand-1'] });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/publish', {
      method: 'POST',
      body: JSON.stringify({ candidateIds: ['cand-1'] })
    });

    expect(normalizeTestDesignPublishResult({ records: [{ result: 'READY' }] }).records[0].result).toBe('READY');
  });

  it('calls batch review endpoint and normalizes partial results', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-batch',
      data: {
        action: 'CONFIRM',
        total: '2',
        succeeded_count: '1',
        failed_count: '1',
        items: [
          { candidate_id: 'cand-1', result: 'SUCCEEDED', candidate: { id: 'cand-1', status: 'CONFIRMED', version: '2' } },
          { candidate_id: 'cand-2', result: 'FAILED', error_code: 'VERSION_CONFLICT', error_message: '候选版本已变更' }
        ]
      }
    });

    const response = await batchActionTestDesignCandidates({
      action: 'CONFIRM',
      candidates: [{ id: 'cand-1', version: 1 }, { id: 'cand-2', version: 1 }],
      comment: '批量确认'
    });

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/candidates/batch-action', {
      method: 'POST',
      body: JSON.stringify({
        action: 'CONFIRM',
        candidates: [{ id: 'cand-1', version: 1 }, { id: 'cand-2', version: 1 }],
        comment: '批量确认'
      })
    });
    expect(response.data).toMatchObject({ action: 'CONFIRM', total: 2, succeededCount: 1, failedCount: 1 });
    expect(response.data.items[0].candidate?.status).toBe('CONFIRMED');
    expect(response.data.items[1]).toMatchObject({ candidateId: 'cand-2', result: 'FAILED', errorCode: 'VERSION_CONFLICT' });
    expect(normalizeTestDesignCandidateBatchActionResult({ items: [{ candidate_id: 'cand-3', result: 'FAILED' }] }).items[0].candidateId).toBe('cand-3');
  });

  it('exports candidate CSV with server-side filters', async () => {
    requestTextMock.mockResolvedValue({
      text: 'recordType,metric,value\nsummary,totalMatched,1\n',
      traceId: 'trace-export',
      contentType: 'text/csv',
      filename: 'wp5-candidates.csv'
    });

    const response = await exportTestDesignCandidatesCsv({
      index: 1,
      size: 20,
      taskId: 'task 1',
      projectId: 'project pay',
      status: 'FAILED',
      coverageType: 'SMOKE',
      keyword: '登录'
    });

    expect(requestTextMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/candidates/export?taskId=task+1&projectId=project+pay&status=FAILED&coverageType=SMOKE&keyword=%E7%99%BB%E5%BD%95'
    );
    expect(response.filename).toBe('wp5-candidates.csv');
  });

  it('exports review record CSV from the task-scoped server report', async () => {
    requestTextMock.mockResolvedValue({
      text: 'recordType,metric,value\nsummary,totalMatched,2\n',
      traceId: 'trace-review-export',
      contentType: 'text/csv',
      filename: 'wp5-review-records.csv'
    });

    const response = await exportTestDesignReviewRecordsCsv('task 1');

    expect(requestTextMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/review-records/export');
    expect(response.filename).toBe('wp5-review-records.csv');
  });

  it('loads health endpoint without auth-specific payload assumptions', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-health', data: { status: 'UP' } });

    const response = await fetchTestDesignHealth();

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/health');
    expect(response.data.status).toBe('UP');
  });
});
