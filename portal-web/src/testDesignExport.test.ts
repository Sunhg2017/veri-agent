import { describe, expect, it } from 'vitest';
import type { TestDesignCandidateView, TestDesignPublishResult, TestDesignTaskView } from './api/testDesign';
import {
  buildTestDesignCandidateReviewCsv,
  buildTestDesignExportFilename,
  buildTestDesignPublishResultCsv,
  buildTestDesignTaskReportCsv,
  sanitizeTestDesignExportText
} from './testDesignExport';
import { buildTestDesignQualitySummary } from './testDesignQualitySummary';
import { buildTestDesignReviewSummary } from './testDesignReviewSummary';

const task: TestDesignTaskView = {
  id: 'task-1',
  projectId: 'project-1',
  title: '登录模块生成',
  status: 'SUCCEEDED',
  requirementIds: ['req-1'],
  coverageTypes: ['SMOKE', 'EXCEPTION'],
  totalRequirements: 1,
  generatedCount: 2,
  confirmedCount: 1,
  publishedCount: 0,
  contextSummary: {}
};

const baseCandidate: TestDesignCandidateView = {
  id: 'candidate-1',
  taskId: 'task-1',
  projectId: 'project-1',
  requirementId: 'req-1',
  apiId: 'api-1',
  title: '验证登录成功',
  description: '标准链路',
  coverageType: 'SMOKE',
  priority: 'HIGH',
  status: 'CONFIRMED',
  preconditions: '账号已激活',
  steps: [
    { stepOrder: 1, action: '输入账号密码', expectedResult: '登录按钮可点击' },
    { stepOrder: 2, action: '点击登录', expectedResult: '进入首页' }
  ],
  expectedResult: '用户登录成功',
  tags: ['login', 'smoke'],
  reviewComment: '已评审',
  version: 3,
  createdAt: '2026-05-28T10:00:00Z',
  updatedAt: '2026-05-28T10:30:00Z'
};

describe('WP5 test design exports', () => {
  it('exports candidate review summaries with whitelisted fields and quality counts', () => {
    const csv = buildTestDesignCandidateReviewCsv({
      task,
      candidates: [
        baseCandidate,
        {
          ...baseCandidate,
          id: 'candidate-2',
          title: '异常登录, 锁定账号',
          coverageType: 'EXCEPTION',
          priority: 'MEDIUM',
          status: 'FAILED',
          requirementId: '',
          expectedResult: '',
          reviewComment: '',
          errorMessage: 'provider token=secret-value timeout',
          tags: ['login', 'exception']
        }
      ],
      scopeLabel: '当前候选页 1-2 / 2',
      generatedAt: '2026-05-28T12:00:00Z'
    });

    expect(csv).toContain('recordType,metric,value,taskId,taskTitle');
    expect(csv).toContain('summary,status:CONFIRMED,1,task-1');
    expect(csv).toContain('summary,coverage:EXCEPTION,1,task-1');
    expect(csv).toContain('summary,quality:MISSING_REQUIREMENT,1,task-1');
    expect(csv).toContain('"异常登录, 锁定账号"');
    expect(csv).toContain('provider token=[REDACTED] timeout');
    expect(csv).not.toContain('secret-value');
    expect(csv).not.toContain('description');
    expect(csv).not.toContain('preconditions');
    expect(csv).not.toContain('输入账号密码');
  });

  it('exports publish result summaries without leaking sensitive errors', () => {
    const publishResult: TestDesignPublishResult = {
      taskId: 'task-1',
      projectId: 'project-1',
      dryRun: false,
      total: 2,
      created: 1,
      skipped: 0,
      failed: 1,
      createdCaseIds: ['case-1'],
      records: [
        {
          taskId: 'task-1',
          candidateId: 'candidate-1',
          title: '验证登录成功',
          projectId: 'project-1',
          requirementId: 'req-1',
          assetCaseId: 'case-1',
          dryRun: false,
          action: 'CREATE',
          result: 'SUCCEEDED',
          createdAt: '2026-05-28T12:00:00Z'
        },
        {
          taskId: 'task-1',
          candidateId: 'candidate-2',
          title: '异常登录',
          projectId: 'project-1',
          requirementId: 'req-1',
          dryRun: false,
          action: 'CREATE',
          result: 'FAILED',
          errorMessage: 'Bearer abc.def.ghi rejected',
          createdAt: '2026-05-28T12:01:00Z'
        }
      ]
    };

    const csv = buildTestDesignPublishResultCsv({ task, publishResult, generatedAt: '2026-05-28T12:05:00Z' });

    expect(csv).toContain('metadata,total,2,task-1');
    expect(csv).toContain('summary,result:FAILED,1,task-1');
    expect(csv).toContain('publishRecord,,,task-1,登录模块生成,project-1,false,candidate-1');
    expect(csv).toContain('Bearer [REDACTED] rejected');
    expect(csv).not.toContain('abc.def.ghi');
  });

  it('exports task report summaries without candidate body or review comments', () => {
    const qualitySummary = buildTestDesignQualitySummary([
      baseCandidate,
      {
        ...baseCandidate,
        id: 'candidate-2',
        title: '异常登录',
        coverageType: 'EXCEPTION',
        priority: 'MEDIUM',
        status: 'FAILED',
        description: 'apiKey=body-secret should not be exported',
        expectedResult: '',
        reviewComment: 'token=review-secret should not be exported',
        errorMessage: 'Bearer abc.def.ghi rejected'
      }
    ], 8);
    const reviewSummary = buildTestDesignReviewSummary([
      {
        id: 'review-1',
        taskId: 'task-1',
        candidateId: 'candidate-1',
        title: '验证登录成功',
        projectId: 'project-1',
        action: 'UPDATE',
        beforeStatus: 'GENERATED',
        afterStatus: 'EDITED',
        reviewer: 'qa.lead',
        hasComment: true,
        commentPreview: 'token=review-secret should not be exported',
        changedFields: ['title', 'status', 'version'],
        versionBefore: 1,
        versionAfter: 2
      }
    ], 4);

    const csv = buildTestDesignTaskReportCsv({
      task: {
        ...task,
        modelInvocationId: 'invoke-1',
        inputDigest: 'sha256:input',
        contextSummary: { requirementCount: 1, sourceRefs: ['REQ-1'] }
      },
      qualitySummary,
      qualityScopeLabel: '当前候选页 1-2 / 8',
      reviewSummary,
      reviewScopeLabel: '当前评审页 1-1 / 4',
      publishResult: {
        taskId: 'task-1',
        projectId: 'project-1',
        dryRun: true,
        total: 2,
        created: 1,
        skipped: 0,
        failed: 1,
        createdCaseIds: ['case-1'],
        records: [
          {
            taskId: 'task-1',
            candidateId: 'candidate-1',
            title: '验证登录成功',
            projectId: 'project-1',
            requirementId: 'req-1',
            assetCaseId: 'case-1',
            dryRun: true,
            action: 'CREATE',
            result: 'READY'
          },
          {
            taskId: 'task-1',
            candidateId: 'candidate-2',
            title: '异常登录',
            projectId: 'project-1',
            requirementId: 'req-1',
            dryRun: true,
            action: 'CREATE',
            result: 'FAILED',
            errorMessage: 'apiKey=publish-secret rejected'
          }
        ]
      },
      generatedAt: '2026-05-28T13:00:00Z'
    });

    expect(csv).toContain('recordType,section,metric,label,value,percent,tone,taskId');
    expect(csv).toContain('metadata,task,reportType,,WP5_TASK_REPORT');
    expect(csv).toContain('metadata,task,modelInvocationTracked,,true');
    expect(csv).toContain('metadata,task,inputDigestTracked,,true');
    expect(csv).toContain('metadata,task,contextSummaryKeyCount,,2');
    expect(csv).toContain('metadata,candidateQuality,scope,,当前候选页 1-2 / 8');
    expect(csv).toContain('summary,candidateQuality,metric,可发布,2,,success');
    expect(csv).toContain('summary,candidateQuality,distribution:状态,FAILED,1,50,danger');
    expect(csv).toContain('metadata,reviewHistory,scope,,当前评审页 1-1 / 4');
    expect(csv).toContain('summary,reviewHistory,distribution:动作,UPDATE,1,100,info');
    expect(csv).toContain('metadata,publish,total,,2');
    expect(csv).toContain('summary,publish,result:FAILED,,1');
    expect(csv).not.toContain('body-secret');
    expect(csv).not.toContain('review-secret');
    expect(csv).not.toContain('publish-secret');
    expect(csv).not.toContain('输入账号密码');
  });

  it('sanitizes filenames and common secret patterns', () => {
    expect(buildTestDesignExportFilename('候选/页', 'task:1', '2026-05-28T12:00:00.000Z')).toBe('wp5-report-task-1-2026-05-28T12-00-00.csv');
    expect(sanitizeTestDesignExportText('apiKey=live-secret cookie:session-value')).toBe('apiKey=[REDACTED] cookie=[REDACTED]');
  });
});
