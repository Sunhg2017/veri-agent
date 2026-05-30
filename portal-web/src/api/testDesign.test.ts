import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  batchActionTestDesignCandidates,
  batchResolveTestDesignConflicts,
  cancelTestDesignTask,
  confirmTestDesignCandidate,
  createTestDesignTask,
  exportTestDesignCandidatesCsv,
  exportTestDesignReviewRecordsCsv,
  exportTestDesignTaskReportCsv,
  fetchTaskTestDesignCandidates,
  fetchTestDesignCandidates,
  fetchTestDesignHealth,
  fetchTestDesignPromptTrend,
  fetchTestDesignReviewRecords,
  fetchTestDesignTaskAuditSummary,
  fetchTestDesignTask,
  fetchTestDesignTaskQualitySummary,
  fetchTestDesignTaskSummary,
  fetchTestDesignTasks,
  normalizeTestDesignCandidate,
  normalizeTestDesignCandidateBatchActionResult,
  normalizeTestDesignCandidateList,
  normalizeTestDesignConflictBatchResolveResult,
  normalizeTestDesignHealth,
  normalizeTestDesignAuditSummary,
  normalizeTestDesignAuditTimelineItem,
  normalizeTestDesignPromptTrend,
  normalizeTestDesignPromptTrendBucket,
  normalizeTestDesignPublishResult,
  normalizeTestDesignQualitySummary,
  normalizeTestDesignReviewRecord,
  normalizeTestDesignReviewRecordList,
  normalizeTestDesignTask,
  normalizeTestDesignTaskDetail,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  resolveTestDesignConflict,
  retryTestDesignTask,
  testDesignCandidateExportPath,
  testDesignReviewRecordExportPath,
  testDesignTaskReportExportPath,
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

    const qualitySummary = normalizeTestDesignQualitySummary({
      task_id: 'task-1',
      project_id: 'project-1',
      scope: 'fullTask',
      total: '4',
      reviewable_count: '1',
      publishable_count: '2',
      failed_count: '1',
      confirmed_count: '1',
      published_count: '1',
      step_complete_count: '3',
      expected_complete_count: '3',
      low_confidence_count: '1',
      error_count: '1',
      missing_requirement_count: '0',
      missing_title_count: '0',
      duplicate_key_collision_count: '0',
      readiness: {
        status: 'WARNING',
        blocking_count: '0',
        warning_count: '1',
        checks: [
          {
            code: 'lowConfidence',
            label: '低置信度占比',
            status: 'FAILED',
            severity: 'WARNING',
            current_value: '25.00',
            threshold_value: '20.00',
            unit: 'PERCENT',
            description: '低置信度候选占比不得高于阈值'
          }
        ]
      },
      metrics: [{ code: 'publishable', count: '2', percent: '50.00' }],
      distributions: {
        status: [{ label: 'CONFIRMED', count: '1', percent: '25.00' }]
      }
    });
    expect(qualitySummary).toMatchObject({
      taskId: 'task-1',
      projectId: 'project-1',
      total: 4,
      publishableCount: 2,
      readiness: {
        status: 'WARNING',
        blockingCount: 0,
        warningCount: 1,
        checks: [
          expect.objectContaining({
            code: 'lowConfidence',
            currentValue: 25,
            thresholdValue: 20,
            unit: 'PERCENT'
          })
        ]
      },
      metrics: [{ code: 'publishable', count: 2, percent: 50 }]
    });
    expect(qualitySummary.distributions.status[0]).toMatchObject({ label: 'CONFIRMED', count: 1, percent: 25 });

    const promptTrend = normalizeTestDesignPromptTrend({
      project_id: 'project-1',
      prompt_key: 'wp5-test-design-v1',
      task_count: '2',
      candidate_count: '6',
      readiness_distribution: [
        { label: 'WARNING', count: '1', percent: '100.00' }
      ],
      buckets: [
        {
          prompt_key: 'wp5-test-design-v1',
          prompt_version: '1.0.0',
          task_count: '2',
          candidate_count: '6',
          confirmed_count: '3',
          published_count: '1',
          step_complete_count: '5',
          expected_complete_count: '4',
          low_confidence_count: '1',
          error_count: '1',
          duplicate_key_collision_count: '0',
          correction_count: '2',
          rejected_count: '1',
          ignored_count: '0',
          step_complete_percent: '83.33',
          expected_complete_percent: '66.67',
          low_confidence_percent: '16.67',
          error_percent: '16.67',
          feedback_signal_percent: '50.00',
          readiness: {
            status: 'WARNING',
            blocking_count: '0',
            warning_count: '1',
            checks: [
              {
                code: 'lowConfidence',
                label: '低置信度占比',
                status: 'FAILED',
                severity: 'WARNING',
                current_value: '16.67',
                threshold_value: '20.00',
                unit: 'PERCENT'
              }
            ]
          },
          latest_task_created_at: '2026-05-30T10:00:00Z'
        }
      ],
      generated_at: '2026-05-30T10:01:00Z'
    });
    expect(promptTrend).toMatchObject({
      projectId: 'project-1',
      promptKey: 'wp5-test-design-v1',
      taskCount: 2,
      candidateCount: 6,
      readinessDistribution: [
        { label: 'WARNING', count: 1, percent: 100 }
      ],
      buckets: [
        expect.objectContaining({
          promptVersion: '1.0.0',
          candidateCount: 6,
          stepCompletePercent: 83.33,
          feedbackSignalPercent: 50,
          readiness: expect.objectContaining({
            status: 'WARNING',
            warningCount: 1,
            checks: [expect.objectContaining({ code: 'lowConfidence', status: 'FAILED' })]
          })
        })
      ]
    });
    expect(normalizeTestDesignPromptTrendBucket({ prompt_version: 'v2' })).toMatchObject({
      promptKey: 'UNKNOWN',
      promptVersion: 'v2',
      candidateCount: 0
    });

    const auditSummary = normalizeTestDesignAuditSummary({
      task_id: 'task-1',
      project_id: 'project-1',
      task_status: 'SUCCEEDED',
      event_count: '5',
      review_record_count: '2',
      publish_record_count: '1',
      dry_run_record_count: '1',
      issue_count: '1',
      note_coverage_count: '2',
      metrics: [{ code: 'issues', label: '失败冲突', count: '1', tone: 'warning' }],
      recent_events: [
        {
          source: 'REVIEW',
          action: 'UPDATE',
          result: 'GENERATED->EDITED',
          candidate_id: 'cand-1',
          actor: 'reviewer',
          has_note: true,
          created_at: '2026-05-30T10:02:00Z'
        }
      ],
      generated_at: '2026-05-30T10:03:00Z'
    });
    expect(auditSummary).toMatchObject({
      taskId: 'task-1',
      projectId: 'project-1',
      eventCount: 5,
      reviewRecordCount: 2,
      publishRecordCount: 1,
      issueCount: 1,
      noteCoverageCount: 2,
      metrics: [expect.objectContaining({ code: 'issues', count: 1, tone: 'warning' })],
      recentEvents: [expect.objectContaining({ source: 'REVIEW', candidateId: 'cand-1', hasNote: true })]
    });
    expect(normalizeTestDesignAuditTimelineItem({ action: 'CREATE' })).toMatchObject({
      source: 'UNKNOWN',
      action: 'CREATE',
      result: 'UNKNOWN',
      hasNote: false
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
    expect(testDesignTaskReportExportPath('task 1')).toBe('/api/v1/test-design/tasks/task%201/report/export');

    await fetchTestDesignTask('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201');

    await fetchTestDesignTaskSummary('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/summary');

    await fetchTestDesignTaskQualitySummary('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/quality/summary');

    await fetchTestDesignTaskAuditSummary('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/report/audit-summary');

    await fetchTestDesignPromptTrend({ index: 0, size: 10, projectId: 'proj pay', promptKey: 'wp5-test-design-v1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/quality/prompt-trend?index=0&size=10&projectId=proj+pay&promptKey=wp5-test-design-v1');
  });

  it('calls task lifecycle action endpoints with encoded task ids', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-task-action',
      data: { task: { id: 'task-1', status: 'SUCCEEDED' }, candidates: [] }
    });

    await retryTestDesignTask('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/retry', {
      method: 'POST'
    });

    await cancelTestDesignTask('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/cancel', {
      method: 'POST'
    });
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
        records: [{
          candidate_id: 'cand-1',
          candidate_status: 'CONFIRMED',
          candidate_version: '3',
          dry_run: true,
          action: 'CREATE',
          result: 'READY'
        }]
      }
    });

    const dryRun = await publishTestDesignDryRun('task 1', { candidateIds: ['cand-1'] });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/publish-dry-run', {
      method: 'POST',
      body: JSON.stringify({ candidateIds: ['cand-1'], dryRun: true })
    });
    expect(dryRun.data).toMatchObject({ taskId: 'task-1', dryRun: true, total: 2, skipped: 2 });
    expect(dryRun.data.createdCaseIds).toEqual(['case-1', 'case-2']);
    expect(dryRun.data.records[0]).toMatchObject({ candidateStatus: 'CONFIRMED', candidateVersion: 3 });

    await publishTestDesignTask('task 1', { candidateIds: ['cand-1'] });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/publish', {
      method: 'POST',
      body: JSON.stringify({ candidateIds: ['cand-1'] })
    });

    expect(normalizeTestDesignPublishResult({ records: [{ result: 'READY' }] }).records[0].result).toBe('READY');
  });

  it('calls conflict resolution endpoint with candidate version and target case', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-conflict',
      data: {
        candidate_id: 'cand-1',
        candidate_status: 'PUBLISHED',
        candidate_version: '4',
        asset_case_id: 'case-1',
        action: 'MANUAL_LINK_EXISTING',
        result: 'SUCCEEDED',
        dry_run: false
      }
    });

    const response = await resolveTestDesignConflict('cand 1', {
      version: 3,
      caseId: 'case-1',
      reason: ' 人工确认复用 ',
      comment: ''
    });

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/candidates/cand%201/resolve-conflict', {
      method: 'POST',
      body: JSON.stringify({
        version: 3,
        caseId: 'case-1',
        reason: '人工确认复用'
      })
    });
    expect(response.data).toMatchObject({
      candidateId: 'cand-1',
      candidateStatus: 'PUBLISHED',
      candidateVersion: 4,
      assetCaseId: 'case-1',
      action: 'MANUAL_LINK_EXISTING',
      result: 'SUCCEEDED',
      dryRun: false
    });
  });

  it('calls batch conflict resolution endpoint and normalizes partial results', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-batch-conflict',
      data: {
        action: 'MANUAL_LINK_EXISTING',
        total: '2',
        succeeded_count: '1',
        failed_count: '1',
        items: [
          {
            candidate_id: 'cand-1',
            result: 'SUCCEEDED',
            record: {
              candidate_id: 'cand-1',
              candidate_status: 'PUBLISHED',
              candidate_version: '5',
              asset_case_id: 'case-1',
              action: 'MANUAL_LINK_EXISTING',
              result: 'SUCCEEDED',
              dry_run: false
            }
          },
          { candidate_id: 'cand-2', result: 'FAILED', error_code: 'VERSION_CONFLICT', error_message: '候选版本已变更' }
        ]
      }
    });

    const response = await batchResolveTestDesignConflicts({
      items: [
        { candidateId: 'cand-1', version: 4, caseId: 'case-1' },
        { candidateId: 'cand-2', version: 8, caseId: 'case-2' }
      ],
      reason: ' 批量复用 ',
      comment: ''
    });

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/candidates/batch-resolve-conflicts', {
      method: 'POST',
      body: JSON.stringify({
        items: [
          { candidateId: 'cand-1', version: 4, caseId: 'case-1' },
          { candidateId: 'cand-2', version: 8, caseId: 'case-2' }
        ],
        reason: '批量复用'
      })
    });
    expect(response.data).toMatchObject({ action: 'MANUAL_LINK_EXISTING', total: 2, succeededCount: 1, failedCount: 1 });
    expect(response.data.items[0].record).toMatchObject({ candidateId: 'cand-1', candidateStatus: 'PUBLISHED', candidateVersion: 5 });
    expect(response.data.items[1]).toMatchObject({ candidateId: 'cand-2', result: 'FAILED', errorCode: 'VERSION_CONFLICT' });
    expect(normalizeTestDesignConflictBatchResolveResult({ items: [{ candidate_id: 'cand-3', result: 'FAILED' }] }).items[0].candidateId).toBe('cand-3');
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

  it('exports full task report CSV from the task-scoped server report', async () => {
    requestTextMock.mockResolvedValue({
      text: 'recordType,section,metric\nmetadata,task,reportType\n',
      traceId: 'trace-task-report-export',
      contentType: 'text/csv',
      filename: 'wp5-task-report.csv'
    });

    const response = await exportTestDesignTaskReportCsv('task 1');

    expect(requestTextMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/report/export');
    expect(response.filename).toBe('wp5-task-report.csv');
  });

  it('loads health endpoint without auth-specific payload assumptions', async () => {
    requestJsonMock.mockResolvedValue({ code: 'OK', message: 'ok', trace_id: 'trace-health', data: { status: 'UP' } });

    const response = await fetchTestDesignHealth();

    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/health');
    expect(response.data.status).toBe('UP');
  });
});
