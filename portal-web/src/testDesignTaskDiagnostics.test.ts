import { describe, expect, it } from 'vitest';
import type { TestDesignTaskView } from './api/testDesign';
import {
  buildTestDesignTaskDiagnostics,
  compactTestDesignDigest,
  summarizeTestDesignContextPolicy,
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
  modelObservation: {
    invocationId: 'invoke-abcdef1234567890xyz',
    jobId: 'job-abcdef1234567890xyz',
    traceId: 'trc_wp5_model_observation_abcdef1234567890',
    available: true,
    status: 'FAILED',
    providerName: 'openai',
    modelName: 'gpt-5-mini',
    routingRuleName: 'wp5-cost-aware',
    routingGroup: 'default',
    modelCapability: 'JSON',
    fallbackUsed: true,
    inputTokens: 123,
    outputTokens: 45,
    totalCost: 0.00012345,
    latencyMs: 875,
    errorCode: 'MODEL_TIMEOUT',
    errorMessage: 'provider token=secret-value timed out',
    actorService: 'wp5-test-design',
    createdAt: '2026-05-28T10:59:00Z'
  },
  totalRequirements: 2,
  generatedCount: 8,
  confirmedCount: 3,
  publishedCount: 1,
  errorMessage: 'provider token=secret-value Bearer abc.def.ghi timeout after 30s',
  requestedBy: 'qa.lead',
  idempotencyKey: 'wp5:create:ops:diagnostics-abcdefghijklmnopqrstuvwxyz',
  inputDigest: '9c6f4c3ef8d1b6a2b90a4e11f9cd8e72bb4f9cb6e0b7a2f3',
  contextPolicyGovernance: {
    policyVersion: 'wp5-context-policy-v1',
    policySource: 'PLATFORM_DEFAULT',
    governanceStatus: 'PLATFORM_DEFAULT_ONLY',
    changeMode: 'DEPLOY_CONFIG_CHANGE',
    projectOverrideSupported: false,
    environmentOverrideSupported: false,
    changeApprovalRequired: true,
    changeApprovalWorkflowReady: false,
    effectiveAtTaskCreation: true,
    aggregateOnly: true
  },
  contextAssemblyPolicy: {
    policyVersion: 'wp5-context-assembly-policy-v2',
    assemblyMode: 'SNAPSHOT_DIGEST_ONLY',
    digestStrategy: 'SHA256_CONTEXT_SUMMARY',
    inputDigestRequired: true,
    persistedContextSummaryOnly: true,
    wp3ApplicationServiceOnly: true,
    rawContextBodyStored: false,
    modelPayloadStored: false,
    digestValueExported: false,
    requirementBodyExported: false,
    assetSchemaExported: false,
    pageTreeExported: false,
    flowJsonExported: false,
    explicitAssetIdentifierListExported: false,
    historicalCaseStepExported: false,
    aggregateOnly: true
  },
  contextPolicyOperations: {
    policyVersion: 'wp5-context-policy-operations-v2',
    operationMode: 'PLATFORM_DEFAULT_ONLY',
    policyResolutionOrder: 'PLATFORM_DEFAULT_ONLY',
    policyFallbackBehavior: 'DEPLOY_CONFIG_CHANGE_REQUIRED',
    approvalStatus: 'WORKFLOW_NOT_READY',
    projectOverrideStoreReady: false,
    environmentOverrideStoreReady: false,
    changeApprovalWorkflowReady: false,
    effectivePolicySnapshotMaterialized: true,
    aggregateOnly: true
  },
  scopePolicy: {
    policyVersion: 'wp5-scope-policy-v1',
    scopeModel: 'PROJECT_RESOURCE_SCOPE',
    listFallbackScope: 'PLATFORM_WHEN_PROJECT_FILTER_ABSENT',
    taskProjectScopeRequired: true,
    candidateProjectScopeRequired: true,
    batchCandidateProjectScopeRequired: true,
    publishProjectScopeRequired: true,
    asyncTaskProjectScopeRecovered: true,
    smokeProjectScopeRequired: true,
    evaluationCorpusProjectIsolated: true,
    evaluationCorpusOperationsReady: false,
    crossWpScopeDashboardReady: false,
    candidateIdentifierListExported: false,
    roleRuleDetailExported: false,
    serviceTokenValueExported: false,
    aggregateOnly: true
  },
  releaseReadinessPolicy: {
    policyVersion: 'wp5-release-readiness-policy-v1',
    decisionMode: 'ADVISORY_QUALITY_GATE',
    thresholdSource: 'DEPLOY_CONFIG',
    qualityThresholdEvaluated: true,
    advisoryOnly: true,
    publishBlockingEnabled: false,
    manualApprovalRequired: true,
    approvalWorkflowReady: false,
    autoPublishAllowed: false,
    confirmedCandidateRequired: true,
    qualityGateOverrideSupported: false,
    candidateEvidenceExported: false,
    approvalNotesExported: false,
    thresholdRuleDetailExported: false,
    aggregateOnly: true
  },
  contextSummary: {
    contextVersion: 'ctx-v3',
    requirements: [{ id: 'req-1' }, { id: 'req-2' }],
    documentSources: { count: 3 },
    historicalCases: { total: 4 },
    apis: 2,
    pages: [{ id: 'page-1' }],
    explicitAssets: {
      apiCount: 1,
      pageCount: 2,
      flowCount: 3
    },
    limits: {
      linkedAssetsPerRequirement: 2,
      explicitAssetsPerType: 3,
      existingCasesPerRequirement: 4,
      requirementDescriptionChars: 180,
      acceptanceCriteriaChars: 160,
      linkedAssetSchemaChars: 120,
      rawPromptStored: false
    },
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
        expect.objectContaining({
          label: '调用观测',
          tone: 'danger',
          value: 'FAILED · 123/45 tokens · 875ms · cost:0.00012345 · fallback · MODEL_TIMEOUT'
        }),
        expect.objectContaining({ label: '调用链路', value: expect.stringContaining('trc_wp5_mod') }),
        expect.objectContaining({ label: '调用任务', value: expect.stringContaining('job-abcdef1') }),
        expect.objectContaining({ label: '输入摘要', value: expect.stringContaining('9c6f4c3ef8d1') }),
        expect.objectContaining({ label: '幂等键', value: expect.stringContaining('wp5:create:ops') }),
        expect.objectContaining({
          label: '上下文策略',
          value: '关联资产:2 · 显式资产:3 · 历史用例:4 · 需求描述:180 · 验收标准:160 · 资产摘要:120'
        }),
        expect.objectContaining({
          label: '装配策略',
          tone: 'neutral',
          value: 'SNAPSHOT_DIGEST_ONLY · SHA256_CONTEXT_SUMMARY · 摘要:required · 仅摘要:yes · WP3应用服务:yes · 原文持久化:off · 模型载荷持久化:off · 细节导出:off'
        }),
        expect.objectContaining({
          label: '策略治理',
          tone: 'warning',
          value: 'PLATFORM_DEFAULT · PLATFORM_DEFAULT_ONLY · DEPLOY_CONFIG_CHANGE · 项目覆盖:off · 环境覆盖:off · 审批流:pending'
        }),
        expect.objectContaining({
          label: '策略运营',
          tone: 'warning',
          value: 'PLATFORM_DEFAULT_ONLY · PLATFORM_DEFAULT_ONLY · DEPLOY_CONFIG_CHANGE_REQUIRED · WORKFLOW_NOT_READY · 项目覆盖存储:pending · 环境覆盖存储:pending · 审批流:pending'
        }),
        expect.objectContaining({
          label: '作用域策略',
          tone: 'warning',
          value: 'PROJECT_RESOURCE_SCOPE · PLATFORM_WHEN_PROJECT_FILTER_ABSENT · 任务:project · 候选:project · 批量:project-set · 发布:project · 异步:task-project · 评测语料:project'
        }),
        expect.objectContaining({
          label: '发布准出',
          tone: 'warning',
          value: 'ADVISORY_QUALITY_GATE · DEPLOY_CONFIG · 质量阈值:checked · 建议模式:on · 发布阻断:off · 审批流:pending · 人工准出:required · 自动发布:off · 候选确认:required'
        }),
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
    expect(JSON.stringify(diagnostics)).not.toContain('https://ticket.example');
  });

  it('marks missing model observation as warning when an invocation id exists', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      modelObservation: undefined
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '调用观测',
          tone: 'warning',
          value: '仅记录调用 ID'
        })
      ])
    );
  });

  it('summarizes context with counts and safe key previews only', () => {
    const summary = summarizeTestDesignTaskContext(baseTask.contextSummary);

    expect(summary).toContain('version:ctx-v3');
    expect(summary).toContain('requirements:2');
    expect(summary).toContain('sources:3');
    expect(summary).toContain('history:4');
    expect(summary).toContain('apis:2');
    expect(summary).toContain('pages:1');
    expect(summary).toContain('explicitApis:1');
    expect(summary).toContain('explicitPages:2');
    expect(summary).toContain('explicitFlows:3');
    expect(summary).toContain('keys:contextVersion, requirements, documentSources, historicalCases, apis +3');
    expect(summary).not.toContain('secretToken');
    expect(summary).not.toContain('should-not-appear');
  });

  it('summarizes context packing policy without exposing raw context values', () => {
    expect(summarizeTestDesignContextPolicy(baseTask.contextSummary)).toBe(
      '关联资产:2 · 显式资产:3 · 历史用例:4 · 需求描述:180 · 验收标准:160 · 资产摘要:120'
    );
    expect(summarizeTestDesignContextPolicy({ limits: { secretToken: 10 } })).toBe('-');
    expect(summarizeTestDesignContextPolicy({})).toBe('-');
  });

  it('falls back to aggregate context policy governance from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      contextPolicyGovernance: undefined,
      contextSummary: {
        policyGovernance: {
          policySource: 'PLATFORM_DEFAULT',
          governanceStatus: 'PLATFORM_DEFAULT_ONLY',
          changeMode: 'DEPLOY_CONFIG_CHANGE',
          projectOverrideSupported: false,
          environmentOverrideSupported: false,
          changeApprovalWorkflowReady: false
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '策略治理',
          tone: 'warning',
          value: expect.stringContaining('审批流:pending')
        })
      ])
    );
  });

  it('falls back to aggregate context assembly policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      contextAssemblyPolicy: undefined,
      contextSummary: {
        assemblyPolicy: {
          assemblyMode: 'SNAPSHOT_DIGEST_ONLY',
          digestStrategy: 'SHA256_CONTEXT_SUMMARY',
          inputDigestRequired: true,
          persistedContextSummaryOnly: true,
          wp3ApplicationServiceOnly: true,
          rawContextBodyStored: false,
          modelPayloadStored: false,
          digestValueExported: false,
          explicitAssetIdentifierListExported: false,
          digestValue: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          explicitAssetIds: ['asset-secret-id'],
          requestSchemaPreview: 'schema should not appear'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '装配策略',
          tone: 'neutral',
          value: expect.stringContaining('SHA256_CONTEXT_SUMMARY')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('asset-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('schema should not appear');
  });

  it('marks unsafe context assembly policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      contextAssemblyPolicy: {
        ...baseTask.contextAssemblyPolicy,
        rawContextBodyStored: true,
        digestValueExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '装配策略',
          tone: 'danger',
          value: expect.stringContaining('细节导出:on')
        })
      ])
    );
  });

  it('falls back to aggregate context policy operations from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      contextPolicyOperations: undefined,
      contextSummary: {
        policyOperations: {
          operationMode: 'PLATFORM_DEFAULT_ONLY',
          policyResolutionOrder: 'PLATFORM_DEFAULT_ONLY',
          policyFallbackBehavior: 'DEPLOY_CONFIG_CHANGE_REQUIRED',
          approvalStatus: 'WORKFLOW_NOT_READY',
          projectOverrideStoreReady: false,
          environmentOverrideStoreReady: false,
          changeApprovalWorkflowReady: false,
          ticketUrl: 'https://ticket.example/secret-change',
          approvalNotes: 'approval-note-text',
          projectOverrideRuleBody: 'secret policy body'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '策略运营',
          tone: 'warning',
          value: expect.stringContaining('WORKFLOW_NOT_READY')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('https://ticket.example');
    expect(JSON.stringify(diagnostics)).not.toContain('approval-note-text');
    expect(JSON.stringify(diagnostics)).not.toContain('secret policy body');
  });

  it('falls back to aggregate scope policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      scopePolicy: undefined,
      contextSummary: {
        scopePolicy: {
          scopeModel: 'PROJECT_RESOURCE_SCOPE',
          listFallbackScope: 'PLATFORM_WHEN_PROJECT_FILTER_ABSENT',
          taskProjectScopeRequired: true,
          candidateProjectScopeRequired: true,
          batchCandidateProjectScopeRequired: true,
          publishProjectScopeRequired: true,
          asyncTaskProjectScopeRecovered: true,
          smokeProjectScopeRequired: true,
          evaluationCorpusProjectIsolated: true,
          evaluationCorpusOperationsReady: false,
          crossWpScopeDashboardReady: false,
          candidateIds: ['candidate-secret-id'],
          roleRuleDetails: 'role matrix should not appear',
          serviceTokenValue: 'token=secret-value'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '作用域策略',
          tone: 'warning',
          value: expect.stringContaining('批量:project-set')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('candidate-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('role matrix should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('secret-value');
  });

  it('marks unsafe scope policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      scopePolicy: {
        ...baseTask.scopePolicy,
        publishProjectScopeRequired: false,
        candidateIdentifierListExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '作用域策略',
          tone: 'danger',
          value: expect.stringContaining('发布:platform')
        })
      ])
    );
  });

  it('falls back to aggregate release readiness policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      releaseReadinessPolicy: undefined,
      contextSummary: {
        releaseReadinessPolicy: {
          decisionMode: 'ADVISORY_QUALITY_GATE',
          thresholdSource: 'DEPLOY_CONFIG',
          qualityThresholdEvaluated: true,
          advisoryOnly: true,
          publishBlockingEnabled: false,
          manualApprovalRequired: true,
          approvalWorkflowReady: false,
          autoPublishAllowed: false,
          confirmedCandidateRequired: true,
          candidateEvidence: ['candidate-secret-id'],
          approvalNotes: 'approval note should not appear',
          thresholdRuleDetails: 'threshold rule should not appear'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '发布准出',
          tone: 'warning',
          value: expect.stringContaining('发布阻断:off')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('candidate-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('approval note should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('threshold rule should not appear');
  });

  it('marks unsafe release readiness policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      releaseReadinessPolicy: {
        ...baseTask.releaseReadinessPolicy,
        autoPublishAllowed: true,
        confirmedCandidateRequired: false,
        approvalNotesExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '发布准出',
          tone: 'danger',
          value: expect.stringContaining('自动发布:on')
        })
      ])
    );
  });

  it('compacts digests and handles empty tasks safely', () => {
    expect(compactTestDesignDigest('short-value', 8, 4)).toBe('short-value');
    expect(compactTestDesignDigest('abcdefghijklmnopqrstuvwxyz', 6, 4)).toBe('abcdef...wxyz');
    expect(buildTestDesignTaskDiagnostics(null)).toEqual([]);
  });
});
