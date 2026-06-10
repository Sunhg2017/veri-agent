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
    evaluationCorpusOperationsReady: true,
    crossWpScopeDashboardReady: true,
    candidateIdentifierListExported: false,
    roleRuleDetailExported: false,
    serviceTokenValueExported: false,
    aggregateOnly: true
  },
  evaluationCorpusPolicy: {
    policyVersion: 'wp5-evaluation-corpus-policy-v1',
    corpusMode: 'GOLDEN_SET_BASELINE',
    qualityGateMode: 'MANUAL_OPT_IN_AI_EVAL',
    thresholdSource: 'DEPLOY_CONFIG',
    projectScopeRequired: true,
    goldenSetBaselineRequired: true,
    qualityEvalScriptReady: true,
    qualityGateIntegrated: true,
    readinessDistributionTracked: true,
    promptVersionTracked: true,
    evaluationCorpusProjectIsolated: true,
    sampleMaintenanceReady: true,
    longTermCalibrationReady: true,
    operationsConsoleReady: true,
    corpusRowExported: false,
    candidateBodyExported: false,
    reviewCommentExported: false,
    promptBodyExported: false,
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
    approvalWorkflowReady: true,
    autoPublishAllowed: false,
    confirmedCandidateRequired: true,
    qualityGateOverrideSupported: true,
    candidateEvidenceExported: false,
    approvalNotesExported: false,
    thresholdRuleDetailExported: false,
    aggregateOnly: true
  },
  auditChainPolicy: {
    policyVersion: 'wp5-audit-chain-policy-v1',
    chainMode: 'WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT',
    eventSource: 'TASK_REVIEW_PUBLISH_MODEL_REFERENCES',
    wp1AuditEventWritten: true,
    wp2InvocationReferenceTracked: true,
    wp3PublishReferenceTracked: true,
    wp5DomainEventsTracked: true,
    projectScopeRequired: true,
    traceSignalTracked: true,
    auditEventDetailExported: false,
    candidateIdentifierListExported: false,
    platformAuditIdentifierExported: false,
    traceIdValueExported: false,
    modelInvocationIdValueExported: false,
    publishIdentifierValueExported: false,
    crossWpAuditDashboardReady: true,
    auditOutboxReplayDashboardReady: true,
    aggregateOnly: true
  },
  modelObservationPolicy: {
    policyVersion: 'wp5-model-observation-policy-v1',
    observationMode: 'ROUTING_COST_LATENCY_AGGREGATE',
    wp2InvocationReferenceTracked: true,
    traceIdTracked: true,
    jobIdTracked: true,
    routingMetadataTracked: true,
    tokenUsageTracked: true,
    latencyTracked: true,
    costTracked: true,
    fallbackTracked: true,
    promptPayloadStored: false,
    payloadPreviewExported: false,
    traceIdValueExported: false,
    jobIdValueExported: false,
    invocationIdValueExported: false,
    providerErrorTextExported: false,
    actorServiceExported: false,
    aggregateOnly: true
  },
  generationOrchestrationPolicy: {
    policyVersion: 'wp5-generation-orchestration-policy-v1',
    orchestrationMode: 'ASYNC_EVENT_CONDITIONAL_CLAIM',
    asyncGenerationEnabled: true,
    conditionalRunClaimSupported: true,
    idempotentCreateReplaySupported: true,
    duplicateEventReplaySafe: true,
    eventRecoveryEnabled: true,
    queuedEventReplaySupported: true,
    runningTimeoutRecoveryEnabled: true,
    explicitRetryRequiredAfterTimeout: true,
    manualTaskRetrySupported: true,
    manualQueuedEventReplayReady: true,
    queueLagMetricReady: true,
    timeoutAlertReady: true,
    multiInstanceLoadTestEvidenceReady: true,
    eventPayloadExported: false,
    eventIdentifierListExported: false,
    queueMessageBodyExported: false,
    recoveryDetailRowsExported: false,
    effectiveRecoveryBatchSize: 100,
    runningTimeoutSeconds: 600,
    queueLagWarningSeconds: 120,
    queuedTaskCount: 2,
    runningTaskCount: 1,
    oldestQueuedAgeSeconds: 95,
    staleRunningTaskCount: 0,
    queueLagWarning: false,
    timeoutWarning: false,
    aggregateOnly: true
  },
  archivePolicy: {
    policyVersion: 'wp5-archive-policy-v1',
    retentionDays: 180,
    storagePolicy: 'platformManaged',
    approvalRequired: true,
    archiveApprovalWorkflowReady: true,
    externalShareApprovalWorkflowReady: true,
    workOrderWorkflowReady: true,
    externalSharingAllowed: false,
    retentionPolicyTracked: true,
    archiveStorageReady: true,
    archiveContentStored: true,
    lineIntegrityIndexReady: true,
    archiveContentExported: false,
    archivePathExported: false,
    archiveNotesExported: false,
    approvalNotesExported: false,
    ticketUrlExported: false,
    aggregateOnly: true
  },
  reportManifestPolicy: {
    policyVersion: 'wp5-report-manifest-policy-v1',
    schemaVersion: 'wp5-task-report-v1',
    fieldSetVersion: 'aggregate-only-v1',
    manifestMode: 'AGGREGATE_RECONCILIATION',
    rowCountTracked: true,
    completionStatusTracked: true,
    archiveReconciliationReady: true,
    rowIntegrityStored: true,
    rowIntegrityIndexReady: true,
    detailRowsExported: false,
    rowIntegrityValueExported: false,
    rowContentSummaryExported: false,
    candidateIdentifierListExported: false,
    traceIdentifierListExported: false,
    auditIdentifierListExported: false,
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
        expect.objectContaining({
          label: '观测策略',
          tone: 'neutral',
          value: 'wp5-model-observation-policy-v1 · ROUTING_COST_LATENCY_AGGREGATE · WP2调用:tracked · trace信号:tracked · job信号:tracked · 路由:tracked · token:tracked · 成本耗时:tracked · fallback:tracked · Prompt载荷:off · 细节导出:off'
        }),
        expect.objectContaining({
          label: '编排策略',
          tone: 'neutral',
          value: 'wp5-generation-orchestration-policy-v1 · ASYNC_EVENT_CONDITIONAL_CLAIM · 条件认领:ready · 幂等回放:ready · 重复事件:safe · 恢复扫描:on · 队列lag:ready · 超时告警:ready · 人工重发:ready · 多实例证据:ready · 细节导出:off · lag阈值:120s / 超时阈值:600s / 排队:2 / 运行:1 / 最旧排队:95s / 超时运行:0'
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
          tone: 'neutral',
          value: 'PROJECT_RESOURCE_SCOPE · PLATFORM_WHEN_PROJECT_FILTER_ABSENT · 任务:project · 候选:project · 批量:project-set · 发布:project · 异步:task-project · 评测语料:project'
        }),
        expect.objectContaining({
          label: '评测语料',
          tone: 'warning',
          value: 'GOLDEN_SET_BASELINE · MANUAL_OPT_IN_AI_EVAL · DEPLOY_CONFIG · 项目作用域:required · golden set:required · AI评测脚本:ready · 质量门禁:integrated · 准出分布:tracked · Prompt版本:tracked · 运营后台:ready'
        }),
        expect.objectContaining({
          label: '发布准出',
          tone: 'warning',
          value: 'ADVISORY_QUALITY_GATE · DEPLOY_CONFIG · 质量阈值:checked · 建议模式:on · 发布阻断:off · 审批流:ready · 人工准出:required · 自动发布:off · 候选确认:required'
        }),
        expect.objectContaining({
          label: '审计链',
          tone: 'neutral',
          value: 'WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT · TASK_REVIEW_PUBLISH_MODEL_REFERENCES · WP1审计:written · WP2调用:tracked · WP3发布:tracked · WP5本域:tracked · 项目作用域:required · trace信号:tracked · 跨WP看板:ready · outbox看板:ready'
        }),
        expect.objectContaining({
          label: '归档策略',
          tone: 'neutral',
          value: 'wp5-archive-policy-v1 · platformManaged · 保留:180天 · 审批:required · 审批流:ready · 外发审批:ready · 工单流转:ready · 归档存储:ready · 归档正文:stored · 行级索引:ready · 外发:off · 保留策略:tracked · 细节导出:off'
        }),
        expect.objectContaining({
          label: '报告清单',
          tone: 'neutral',
          value: 'wp5-report-manifest-policy-v1 · wp5-task-report-v1 · aggregate-only-v1 · AGGREGATE_RECONCILIATION · 行数:tracked · 完成状态:tracked · 归档核验:ready · 行级完整性:stored · 行级索引:ready · 细节导出:off'
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
    expect(JSON.stringify(diagnostics)).not.toContain('row-hash-secret');
  });

  it('falls back to aggregate model observation policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      modelObservationPolicy: undefined,
      contextSummary: {
        modelObservationPolicy: {
          policyVersion: 'wp5-model-observation-policy-v1',
          observationMode: 'ROUTING_COST_LATENCY_AGGREGATE',
          wp2InvocationReferenceTracked: true,
          traceIdTracked: true,
          jobIdTracked: true,
          routingMetadataTracked: true,
          tokenUsageTracked: true,
          latencyTracked: true,
          costTracked: true,
          fallbackTracked: true,
          promptPayloadStored: false,
          payloadPreviewExported: false,
          traceIdValueExported: false,
          jobIdValueExported: false,
          invocationIdValueExported: false,
          providerErrorTextExported: false,
          actorServiceExported: false,
          aggregateOnly: true,
          invocationIds: ['invoke-secret-id'],
          jobIds: ['job-secret-id'],
          traceIds: ['trc_secret'],
          requestPreview: 'request preview should not appear',
          responsePreview: 'response preview should not appear',
          providerErrorBody: 'provider token=secret-value'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '观测策略',
          tone: 'neutral',
          value: expect.stringContaining('成本耗时:tracked')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('invoke-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('job-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('trc_secret');
    expect(JSON.stringify(diagnostics)).not.toContain('request preview should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('response preview should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('secret-value');
  });

  it('falls back to aggregate generation orchestration policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      generationOrchestrationPolicy: undefined,
      contextSummary: {
        generationOrchestrationPolicy: {
          policyVersion: 'wp5-generation-orchestration-policy-v1',
          orchestrationMode: 'ASYNC_EVENT_CONDITIONAL_CLAIM',
          asyncGenerationEnabled: true,
          conditionalRunClaimSupported: true,
          idempotentCreateReplaySupported: true,
          duplicateEventReplaySafe: true,
          eventRecoveryEnabled: true,
          queuedEventReplaySupported: true,
          runningTimeoutRecoveryEnabled: true,
          explicitRetryRequiredAfterTimeout: true,
          manualTaskRetrySupported: true,
          manualQueuedEventReplayReady: true,
          queueLagMetricReady: true,
          timeoutAlertReady: true,
          multiInstanceLoadTestEvidenceReady: true,
          eventPayloadExported: false,
          eventIdentifierListExported: false,
          queueMessageBodyExported: false,
          recoveryDetailRowsExported: false,
          queueLagWarningSeconds: 120,
          runningTimeoutSeconds: 600,
          queuedTaskCount: 3,
          runningTaskCount: 1,
          oldestQueuedAgeSeconds: 180,
          staleRunningTaskCount: 1,
          queueLagWarning: true,
          timeoutWarning: true,
          aggregateOnly: true,
          eventIds: ['evt-secret-id'],
          queuePayload: 'queue token=secret-value',
          idempotencyKeys: ['idempotency-secret']
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '编排策略',
          tone: 'warning',
          value: expect.stringContaining('lag告警:on')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('evt-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('queue token=secret-value');
    expect(JSON.stringify(diagnostics)).not.toContain('idempotency-secret');
  });

  it('marks unsafe generation orchestration policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      generationOrchestrationPolicy: {
        ...baseTask.generationOrchestrationPolicy,
        queueLagMetricReady: false,
        eventPayloadExported: true,
        queueMessageBodyExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '编排策略',
          tone: 'danger',
          value: expect.stringContaining('队列lag:pending')
        })
      ])
    );
  });

  it('marks unsafe model observation policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      modelObservationPolicy: {
        ...baseTask.modelObservationPolicy,
        promptPayloadStored: true,
        traceIdValueExported: true,
        actorServiceExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '观测策略',
          tone: 'danger',
          value: expect.stringContaining('Prompt载荷:on')
        })
      ])
    );
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
          evaluationCorpusOperationsReady: true,
          crossWpScopeDashboardReady: true,
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
          tone: 'neutral',
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

  it('falls back to aggregate evaluation corpus policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      evaluationCorpusPolicy: undefined,
      contextSummary: {
        evaluationCorpusPolicy: {
          corpusMode: 'GOLDEN_SET_BASELINE',
          qualityGateMode: 'MANUAL_OPT_IN_AI_EVAL',
          thresholdSource: 'DEPLOY_CONFIG',
          projectScopeRequired: true,
          goldenSetBaselineRequired: true,
          qualityEvalScriptReady: true,
          qualityGateIntegrated: true,
          readinessDistributionTracked: true,
          promptVersionTracked: true,
          evaluationCorpusProjectIsolated: true,
          sampleMaintenanceReady: true,
          longTermCalibrationReady: true,
          operationsConsoleReady: true,
          corpusRows: ['sample-row-secret'],
          candidateBody: '候选正文不应展示',
          reviewComment: 'review comment should not appear',
          promptPlaintext: 'prompt body should not appear'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '评测语料',
          tone: 'warning',
          value: expect.stringContaining('MANUAL_OPT_IN_AI_EVAL')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('sample-row-secret');
    expect(JSON.stringify(diagnostics)).not.toContain('候选正文不应展示');
    expect(JSON.stringify(diagnostics)).not.toContain('review comment should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('prompt body should not appear');
  });

  it('marks unsafe evaluation corpus policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      evaluationCorpusPolicy: {
        ...baseTask.evaluationCorpusPolicy,
        projectScopeRequired: false,
        corpusRowExported: true,
        promptBodyExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '评测语料',
          tone: 'danger',
          value: expect.stringContaining('项目作用域:optional')
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
          approvalWorkflowReady: true,
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

  it('falls back to aggregate audit chain policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      auditChainPolicy: undefined,
      contextSummary: {
        auditChainPolicy: {
          chainMode: 'WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT',
          eventSource: 'TASK_REVIEW_PUBLISH_MODEL_REFERENCES',
          wp1AuditEventWritten: true,
          wp2InvocationReferenceTracked: true,
          wp3PublishReferenceTracked: true,
          wp5DomainEventsTracked: true,
          projectScopeRequired: true,
          traceSignalTracked: true,
          crossWpAuditDashboardReady: true,
          auditOutboxReplayDashboardReady: true,
          auditLogIds: ['audit-log-secret'],
          candidateIds: ['candidate-secret-id'],
          traceIds: ['trc_secret'],
          sourceRef: 'wp5:secret-source-ref',
          assetCaseId: 'asset-secret-id'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '审计链',
          tone: 'neutral',
          value: expect.stringContaining('跨WP看板:ready')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('audit-log-secret');
    expect(JSON.stringify(diagnostics)).not.toContain('candidate-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('trc_secret');
    expect(JSON.stringify(diagnostics)).not.toContain('secret-source-ref');
    expect(JSON.stringify(diagnostics)).not.toContain('asset-secret-id');
  });

  it('marks unsafe audit chain policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      auditChainPolicy: {
        ...baseTask.auditChainPolicy,
        wp1AuditEventWritten: false,
        traceIdValueExported: true,
        publishIdentifierValueExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '审计链',
          tone: 'danger',
          value: expect.stringContaining('WP1审计:missing')
        })
      ])
    );
  });

  it('falls back to aggregate archive policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      archivePolicy: undefined,
      contextSummary: {
        archivePolicy: {
          policyVersion: 'wp5-archive-policy-v1',
          retentionDays: 365,
          storagePolicy: 'platformManaged',
          approvalRequired: true,
          archiveApprovalWorkflowReady: true,
          externalShareApprovalWorkflowReady: true,
          workOrderWorkflowReady: true,
          externalSharingAllowed: false,
          retentionPolicyTracked: true,
          archiveStorageReady: true,
          archiveContentStored: true,
          lineIntegrityIndexReady: true,
          archiveContentExported: false,
          archivePathExported: false,
          archiveNotesExported: false,
          approvalNotesExported: false,
          ticketUrlExported: false,
          aggregateOnly: true,
          archivePath: 's3://tenant-secret/archive.csv',
          archiveNotes: 'archive-note-text',
          approvalNotes: 'approval note should not appear',
          ticketUrl: 'https://ticket.example/archive-secret'
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '归档策略',
          tone: 'neutral',
          value: expect.stringContaining('保留:365天')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('tenant-secret');
    expect(JSON.stringify(diagnostics)).not.toContain('archive-note-text');
    expect(JSON.stringify(diagnostics)).not.toContain('approval note should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('https://ticket.example');
  });

  it('marks unsafe archive policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      archivePolicy: {
        ...baseTask.archivePolicy,
        retentionDays: 0,
        archivePathExported: true,
        approvalNotesExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '归档策略',
          tone: 'danger',
          value: expect.stringContaining('细节导出:on')
        })
      ])
    );
  });

  it('falls back to aggregate report manifest policy from the task summary', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      reportManifestPolicy: undefined,
      contextSummary: {
        reportManifestPolicy: {
          policyVersion: 'wp5-report-manifest-policy-v1',
          schemaVersion: 'wp5-task-report-v1',
          fieldSetVersion: 'aggregate-only-v1',
          manifestMode: 'AGGREGATE_RECONCILIATION',
          rowCountTracked: true,
          completionStatusTracked: true,
          archiveReconciliationReady: true,
          rowIntegrityStored: true,
          rowIntegrityIndexReady: true,
          detailRowsExported: false,
          rowIntegrityValueExported: false,
          rowContentSummaryExported: false,
          candidateIdentifierListExported: false,
          traceIdentifierListExported: false,
          auditIdentifierListExported: false,
          aggregateOnly: true,
          rowHashes: ['row-hash-secret'],
          rowSummaries: ['row summary should not appear'],
          candidateIds: ['candidate-secret-id'],
          traceIds: ['trc_secret'],
          auditLogIds: ['audit-secret-id']
        }
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '报告清单',
          tone: 'neutral',
          value: expect.stringContaining('归档核验:ready')
        })
      ])
    );
    expect(JSON.stringify(diagnostics)).not.toContain('row-hash-secret');
    expect(JSON.stringify(diagnostics)).not.toContain('row summary should not appear');
    expect(JSON.stringify(diagnostics)).not.toContain('candidate-secret-id');
    expect(JSON.stringify(diagnostics)).not.toContain('trc_secret');
    expect(JSON.stringify(diagnostics)).not.toContain('audit-secret-id');
  });

  it('marks unsafe report manifest policy as danger', () => {
    const diagnostics = buildTestDesignTaskDiagnostics({
      ...baseTask,
      reportManifestPolicy: {
        ...baseTask.reportManifestPolicy,
        rowCountTracked: false,
        rowIntegrityValueExported: true,
        candidateIdentifierListExported: true
      }
    });

    expect(diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: '报告清单',
          tone: 'danger',
          value: expect.stringContaining('细节导出:on')
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
