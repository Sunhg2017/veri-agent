import { describe, expect, it } from 'vitest';
import type { TestDesignTaskView } from './api/testDesign';
import {
  buildTestDesignTaskDiagnostics,
  compactTestDesignDigest,
  summarizeTestDesignTaskContext
} from './testDesignTaskDiagnostics';

const baseTask: TestDesignTaskView = {
  id: 'task-2026-0001-abcdef1234567890',
  projectId: 'project-ops-diagnostics-123456',
  title: '支付链路生成',
  status: 'FAILED',
  requirementIds: ['req-1', 'req-2'],
  coverageTypes: ['SMOKE', 'BOUNDARY'],
  promptKey: 'wp5.payment.generate',
  promptVersion: 'v2026.05.28',
  modelInvocationId: 'invoke-abcdef1234567890xyz',
  modelProviderName: 'openai',
  modelName: 'gpt-5-mini',
  totalRequirements: 2,
  generatedCount: 8,
  confirmedCount: 3,
  publishedCount: 1,
  errorMessage: 'provider token=secret-value Bearer abc.def.ghi timeout after 30s',
  requestedBy: 'qa.lead',
  idempotencyKey: 'wp5:create:ops:diagnostics-abcdefghijklmnopqrstuvwxyz',
  inputDigest: '9c6f4c3ef8d1b6a2b90a4e11f9cd8e72bb4f9cb6e0b7a2f3',
  contextSummary: {
    contextVersion: 'ctx-v3',
    requirements: [{ id: 'req-1' }, { id: 'req-2' }],
    documentSources: { count: 3 },
    historicalCases: { total: 4 },
    apis: 2,
    pages: [{ id: 'page-1' }],
    secretToken: 'should-not-appear'
  },
  createdAt: '2026-05-28T10:00:00Z',
  updatedAt: '2026-05-28T11:30:00Z'
};

describe('WP5 task diagnostics helpers', () => {
  it('builds compact and redacted task diagnostics for the workbench sidebar', () => {
    const diagnostics = buildTestDesignTaskDiagnostics(baseTask);

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ label: 'Prompt', value: 'wp5.payment.generate@v2026.05.28' }),
        expect.objectContaining({ label: '模型', value: 'openai / gpt-5-mini' }),
        expect.objectContaining({ label: '模型调用', value: expect.stringContaining('invoke-abcde') }),
        expect.objectContaining({ label: '输入摘要', value: expect.stringContaining('9c6f4c3ef8d1') }),
        expect.objectContaining({ label: '幂等键', value: expect.stringContaining('wp5:create:ops') }),
        expect.objectContaining({
          label: '错误',
          tone: 'danger',
          value: 'provider token=[REDACTED] Bearer [REDACTED] timeout after 30s'
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('secret-value');
    expect(JSON.stringify(diagnostics)).not.toContain('abc.def.ghi');
    expect(JSON.stringify(diagnostics)).not.toContain('should-not-appear');
  });

  it('summarizes context with counts and safe key previews only', () => {
    const summary = summarizeTestDesignTaskContext(baseTask.contextSummary);

    expect(summary).toContain('version:ctx-v3');
    expect(summary).toContain('requirements:2');
    expect(summary).toContain('sources:3');
    expect(summary).toContain('history:4');
    expect(summary).toContain('apis:2');
    expect(summary).toContain('pages:1');
    expect(summary).toContain('keys:contextVersion, requirements, documentSources, historicalCases, apis +1');
    expect(summary).not.toContain('secretToken');
    expect(summary).not.toContain('should-not-appear');
  });

  it('compacts digests and handles empty tasks safely', () => {
    expect(compactTestDesignDigest('short-value', 8, 4)).toBe('short-value');
    expect(compactTestDesignDigest('abcdefghijklmnopqrstuvwxyz', 6, 4)).toBe('abcdef...wxyz');
    expect(buildTestDesignTaskDiagnostics(null)).toEqual([]);
  });
});
