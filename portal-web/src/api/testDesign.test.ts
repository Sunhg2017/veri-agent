import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  confirmTestDesignCandidate,
  createTestDesignTask,
  fetchTaskTestDesignCandidates,
  fetchTestDesignCandidates,
  fetchTestDesignHealth,
  fetchTestDesignTask,
  fetchTestDesignTasks,
  normalizeTestDesignCandidate,
  normalizeTestDesignCandidateList,
  normalizeTestDesignHealth,
  normalizeTestDesignPublishResult,
  normalizeTestDesignTask,
  normalizeTestDesignTaskDetail,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  updateTestDesignCandidate
} from './testDesign';

vi.mock('./client', () => ({
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);

describe('WP5 test design API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
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
      idempotency_key: 'wp5-create-001'
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
      idempotencyKey: 'wp5-create-001'
    });

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

    await fetchTaskTestDesignCandidates('task 1', { status: 'GENERATED' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/candidates?status=GENERATED');

    await fetchTestDesignTask('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201');
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

  it('loads health endpoint without auth-specific payload assumptions', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-health', data: { status: 'UP' } });

    const response = await fetchTestDesignHealth();

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/health');
    expect(response.data.status).toBe('UP');
  });
});
