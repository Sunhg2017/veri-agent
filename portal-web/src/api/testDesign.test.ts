import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson, requestText } from './client';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  addTestDesignContextPolicyNote,
  approveTestDesignContextPolicyOverride,
  batchActionTestDesignCandidates,
  batchResolveTestDesignConflicts,
  cancelTestDesignTask,
  confirmTestDesignCandidate,
  createTestDesignTemplate,
  createTestDesignTask,
  deleteTestDesignTemplate,
  exportTestDesignCandidatesCsv,
  exportTestDesignReviewRecordsCsv,
  exportTestDesignTaskReportCsv,
  fetchTaskTestDesignCandidates,
  fetchTestDesignContextPolicyEffective,
  fetchTestDesignContextPolicyNotes,
  fetchTestDesignContextPolicyOverrides,
  fetchTestDesignCandidates,
  fetchTestDesignEvaluationCorpusSummary,
  fetchTestDesignHealth,
  fetchTestDesignPromptTrend,
  fetchTestDesignReviewRecords,
  fetchTestDesignScopeSummary,
  fetchTestDesignTemplates,
  fetchTestDesignTaskAuditSummary,
  fetchTestDesignTask,
  fetchTestDesignTaskQualitySummary,
  fetchTestDesignTaskSummary,
  fetchTestDesignTasks,
  normalizeTestDesignCandidate,
  normalizeTestDesignCandidateBatchActionResult,
  normalizeTestDesignCandidateList,
  normalizeTestDesignConflictBatchResolveResult,
  normalizeTestDesignContextPolicyEffective,
  normalizeTestDesignContextPolicyNote,
  normalizeTestDesignContextPolicyOverride,
  normalizeTestDesignEvaluationCorpusSummary,
  normalizeTestDesignHealth,
  normalizeTestDesignAuditSummary,
  normalizeTestDesignAuditTimelineItem,
  normalizeTestDesignPromptTrend,
  normalizeTestDesignPromptTrendBucket,
  normalizeTestDesignPublishResult,
  normalizeTestDesignQualitySummary,
  normalizeTestDesignReviewRecord,
  normalizeTestDesignReviewRecordList,
  normalizeTestDesignScopeSummary,
  normalizeTestDesignTemplate,
  normalizeTestDesignTemplateList,
  normalizeTestDesignTask,
  normalizeTestDesignTaskDetail,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  rejectTestDesignContextPolicyOverride,
  replayQueuedTestDesignTaskEvent,
  requestTestDesignEnvironmentContextPolicyOverride,
  requestTestDesignProjectContextPolicyOverride,
  resolveTestDesignConflict,
  retryTestDesignTask,
  testDesignCandidateExportPath,
  testDesignReviewRecordExportPath,
  testDesignTaskReportExportPath,
  updateTestDesignTemplate,
  updateTestDesignCandidate,
  updateTestDesignContextPolicyOverride
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
    expect(TEST_DESIGN_CANDIDATE_STATUSES).toEqual([
      'GENERATED',
      'EDITED',
      'CONFIRMED',
      'REJECTED',
      'IGNORED',
      'PUBLISH_QUEUED',
      'PUBLISHING',
      'PUBLISHED',
      'FAILED'
    ]);
  });

  it('normalizes health, tasks, candidates and task detail responses', () => {
    const health = normalizeTestDesignHealth({
      status: 'UP',
      generation_mode: 'RULE_BASED',
      prompt_key: 'wp5.case.generate',
      prompt_version: 'v1',
      max_requirements_per_task: '20',
      max_cases_per_requirement: '4',
      context_limits: {
        linked_assets_per_requirement: '3',
        explicit_assets_per_type: 4
      },
      context_policy_governance: {
        policy_source: 'PLATFORM_DEFAULT',
        governance_status: 'PLATFORM_DEFAULT_ONLY',
        project_override_supported: false,
        environment_override_supported: false,
        change_approval_workflow_ready: false,
        aggregate_only: true
      },
      context_assembly_policy: {
        policy_version: 'wp5-context-assembly-policy-v2',
        assembly_mode: 'SNAPSHOT_DIGEST_ONLY',
        digest_strategy: 'SHA256_CONTEXT_SUMMARY',
        input_digest_required: true,
        persisted_context_summary_only: true,
        wp3_application_service_only: true,
        raw_context_body_stored: false,
        model_payload_stored: false,
        digest_value_exported: false,
        explicit_asset_identifier_list_exported: false,
        aggregate_only: true
      },
      context_policy_operations: {
        policy_version: 'wp5-context-policy-operations-v2',
        operation_mode: 'PLATFORM_DEFAULT_ONLY',
        policy_resolution_order: 'PLATFORM_DEFAULT_ONLY',
        policy_fallback_behavior: 'DEPLOY_CONFIG_CHANGE_REQUIRED',
        approval_status: 'WORKFLOW_NOT_READY',
        project_override_store_ready: false,
        environment_override_store_ready: false,
        change_approval_workflow_ready: false,
        effective_policy_snapshot_materialized: true,
        aggregate_only: true
      },
      scope_policy: {
        policy_version: 'wp5-scope-policy-v1',
        scope_model: 'PROJECT_RESOURCE_SCOPE',
        list_fallback_scope: 'PLATFORM_WHEN_PROJECT_FILTER_ABSENT',
        task_project_scope_required: true,
        candidate_project_scope_required: true,
        batch_candidate_project_scope_required: true,
        publish_project_scope_required: true,
        async_task_project_scope_recovered: true,
        smoke_project_scope_required: true,
        evaluation_corpus_project_isolated: true,
        evaluation_corpus_operations_ready: false,
        cross_wp_scope_dashboard_ready: false,
        candidate_identifier_list_exported: false,
        role_rule_detail_exported: false,
        service_token_value_exported: false,
        aggregate_only: true
      },
      evaluation_corpus_policy: {
        policy_version: 'wp5-evaluation-corpus-policy-v1',
        corpus_mode: 'GOLDEN_SET_BASELINE',
        quality_gate_mode: 'MANUAL_OPT_IN_AI_EVAL',
        threshold_source: 'DEPLOY_CONFIG',
        project_scope_required: true,
        golden_set_baseline_required: true,
        quality_eval_script_ready: true,
        quality_gate_integrated: true,
        readiness_distribution_tracked: true,
        prompt_version_tracked: true,
        evaluation_corpus_project_isolated: true,
        sample_maintenance_ready: false,
        long_term_calibration_ready: false,
        operations_console_ready: false,
        corpus_row_exported: false,
        candidate_body_exported: false,
        review_comment_exported: false,
        prompt_body_exported: false,
        aggregate_only: true
      },
      release_readiness_policy: {
        policy_version: 'wp5-release-readiness-policy-v1',
        decision_mode: 'ADVISORY_QUALITY_GATE',
        threshold_source: 'DEPLOY_CONFIG',
        quality_threshold_evaluated: true,
        advisory_only: true,
        publish_blocking_enabled: false,
        manual_approval_required: true,
        approval_workflow_ready: false,
        auto_publish_allowed: false,
        confirmed_candidate_required: true,
        quality_gate_override_supported: false,
        candidate_evidence_exported: false,
        approval_notes_exported: false,
        threshold_rule_detail_exported: false,
        aggregate_only: true
      },
      audit_chain_policy: {
        policy_version: 'wp5-audit-chain-policy-v1',
        chain_mode: 'WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT',
        event_source: 'TASK_REVIEW_PUBLISH_MODEL_REFERENCES',
        wp1_audit_event_written: true,
        wp2_invocation_reference_tracked: true,
        wp3_publish_reference_tracked: true,
        wp5_domain_events_tracked: true,
        project_scope_required: true,
        trace_signal_tracked: true,
        audit_event_detail_exported: false,
        candidate_identifier_list_exported: false,
        platform_audit_identifier_exported: false,
        trace_id_value_exported: false,
        model_invocation_id_value_exported: false,
        publish_identifier_value_exported: false,
        cross_wp_audit_dashboard_ready: false,
        audit_outbox_replay_dashboard_ready: false,
        aggregate_only: true
      },
      model_observation_policy: {
        policy_version: 'wp5-model-observation-policy-v1',
        observation_mode: 'ROUTING_COST_LATENCY_AGGREGATE',
        wp2_invocation_reference_tracked: true,
        trace_id_tracked: true,
        job_id_tracked: true,
        routing_metadata_tracked: true,
        token_usage_tracked: true,
        latency_tracked: true,
        cost_tracked: true,
        fallback_tracked: true,
        prompt_payload_stored: false,
        payload_preview_exported: false,
        trace_id_value_exported: false,
        job_id_value_exported: false,
        invocation_id_value_exported: false,
        provider_error_text_exported: false,
        actor_service_exported: false,
        aggregate_only: true
      },
      generation_orchestration_policy: {
        policy_version: 'wp5-generation-orchestration-policy-v1',
        orchestration_mode: 'ASYNC_EVENT_CONDITIONAL_CLAIM',
        async_generation_enabled: true,
        conditional_run_claim_supported: true,
        idempotent_create_replay_supported: true,
        duplicate_event_replay_safe: true,
        event_recovery_enabled: true,
        queued_event_replay_supported: true,
        running_timeout_recovery_enabled: true,
        explicit_retry_required_after_timeout: true,
        manual_task_retry_supported: true,
        manual_queued_event_replay_ready: true,
        queue_lag_metric_ready: true,
        timeout_alert_ready: true,
        multi_instance_load_test_evidence_ready: true,
        event_payload_exported: false,
        event_identifier_list_exported: false,
        queue_message_body_exported: false,
        recovery_detail_rows_exported: false,
        effective_recovery_batch_size: '100',
        running_timeout_seconds: '600',
        queue_lag_warning_seconds: '120',
        queued_task_count: '2',
        running_task_count: 1,
        oldest_queued_age_seconds: 180,
        stale_running_task_count: '1',
        queue_lag_warning: true,
        timeout_warning: true,
        aggregate_only: true
      },
      archive_policy: {
        policy_version: 'wp5-archive-policy-v1',
        retention_days: 180,
        storage_policy: 'platformManaged',
        approval_required: true,
        archive_approval_workflow_ready: false,
        external_sharing_allowed: false,
        retention_policy_tracked: true,
        archive_storage_ready: false,
        archive_path_exported: false,
        archive_notes_exported: false,
        approval_notes_exported: false,
        ticket_url_exported: false,
        aggregate_only: true
      },
      report_manifest_policy: {
        policy_version: 'wp5-report-manifest-policy-v1',
        schema_version: 'wp5-task-report-v1',
        field_set_version: 'aggregate-only-v1',
        manifest_mode: 'AGGREGATE_RECONCILIATION',
        row_count_tracked: true,
        completion_status_tracked: true,
        archive_reconciliation_ready: true,
        detail_rows_exported: false,
        row_integrity_value_exported: false,
        row_content_summary_exported: false,
        candidate_identifier_list_exported: false,
        trace_identifier_list_exported: false,
        audit_identifier_list_exported: false,
        aggregate_only: true
      },
      supported_coverage_types: 'SMOKE,FUNCTIONAL'
    });
    expect(health).toMatchObject({
      service: 'test-design',
      status: 'UP',
      generationMode: 'RULE_BASED',
      promptKey: 'wp5.case.generate',
      promptVersion: 'v1',
      maxRequirementsPerTask: 20,
      maxCasesPerRequirement: 4,
      contextLimits: {
        linked_assets_per_requirement: 3,
        explicit_assets_per_type: 4
      },
      contextPolicyGovernance: {
        policySource: 'PLATFORM_DEFAULT',
        governanceStatus: 'PLATFORM_DEFAULT_ONLY',
        projectOverrideSupported: false,
        environmentOverrideSupported: false,
        changeApprovalWorkflowReady: false,
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
        explicitAssetIdentifierListExported: false,
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
        sampleMaintenanceReady: false,
        longTermCalibrationReady: false,
        operationsConsoleReady: false,
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
        approvalWorkflowReady: false,
        autoPublishAllowed: false,
        confirmedCandidateRequired: true,
        qualityGateOverrideSupported: false,
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
        crossWpAuditDashboardReady: false,
        auditOutboxReplayDashboardReady: false,
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
        manualQueuedEventReplayReady: true,
        queueLagMetricReady: true,
        timeoutAlertReady: true,
        multiInstanceLoadTestEvidenceReady: true,
        eventPayloadExported: false,
        eventIdentifierListExported: false,
        queueMessageBodyExported: false,
        effectiveRecoveryBatchSize: 100,
        runningTimeoutSeconds: 600,
        queueLagWarningSeconds: 120,
        queuedTaskCount: 2,
        runningTaskCount: 1,
        oldestQueuedAgeSeconds: 180,
        staleRunningTaskCount: 1,
        queueLagWarning: true,
        timeoutWarning: true,
        aggregateOnly: true
      },
      archivePolicy: {
        policyVersion: 'wp5-archive-policy-v1',
        retentionDays: 180,
        storagePolicy: 'platformManaged',
        approvalRequired: true,
        archiveApprovalWorkflowReady: false,
        externalSharingAllowed: false,
        retentionPolicyTracked: true,
        archiveStorageReady: false,
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
        detailRowsExported: false,
        rowIntegrityValueExported: false,
        rowContentSummaryExported: false,
        candidateIdentifierListExported: false,
        traceIdentifierListExported: false,
        auditIdentifierListExported: false,
        aggregateOnly: true
      },
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
      context_policy_governance: {
        policy_source: 'PLATFORM_DEFAULT',
        governance_status: 'PLATFORM_DEFAULT_ONLY',
        change_approval_workflow_ready: false
      },
      context_assembly_policy: {
        policy_version: 'wp5-context-assembly-policy-v2',
        assembly_mode: 'SNAPSHOT_DIGEST_ONLY',
        digest_strategy: 'SHA256_CONTEXT_SUMMARY',
        input_digest_required: true,
        persisted_context_summary_only: true,
        wp3_application_service_only: true,
        raw_context_body_stored: false,
        model_payload_stored: false,
        aggregate_only: true
      },
      context_policy_operations: {
        policy_version: 'wp5-context-policy-operations-v2',
        operation_mode: 'PLATFORM_DEFAULT_ONLY',
        policy_resolution_order: 'PLATFORM_DEFAULT_ONLY',
        policy_fallback_behavior: 'DEPLOY_CONFIG_CHANGE_REQUIRED',
        approval_status: 'WORKFLOW_NOT_READY',
        project_override_store_ready: false,
        aggregate_only: true
      },
      scope_policy: {
        policy_version: 'wp5-scope-policy-v1',
        scope_model: 'PROJECT_RESOURCE_SCOPE',
        list_fallback_scope: 'PLATFORM_WHEN_PROJECT_FILTER_ABSENT',
        task_project_scope_required: true,
        candidate_project_scope_required: true,
        batch_candidate_project_scope_required: true,
        publish_project_scope_required: true,
        async_task_project_scope_recovered: true,
        evaluation_corpus_project_isolated: true,
        evaluation_corpus_operations_ready: false,
        aggregate_only: true
      },
      evaluation_corpus_policy: {
        policy_version: 'wp5-evaluation-corpus-policy-v1',
        corpus_mode: 'GOLDEN_SET_BASELINE',
        quality_gate_mode: 'MANUAL_OPT_IN_AI_EVAL',
        threshold_source: 'DEPLOY_CONFIG',
        project_scope_required: true,
        golden_set_baseline_required: true,
        quality_eval_script_ready: true,
        quality_gate_integrated: true,
        readiness_distribution_tracked: true,
        prompt_version_tracked: true,
        evaluation_corpus_project_isolated: true,
        sample_maintenance_ready: false,
        long_term_calibration_ready: false,
        operations_console_ready: false,
        corpus_row_exported: false,
        candidate_body_exported: false,
        review_comment_exported: false,
        prompt_body_exported: false,
        aggregate_only: true
      },
      release_readiness_policy: {
        policy_version: 'wp5-release-readiness-policy-v1',
        decision_mode: 'ADVISORY_QUALITY_GATE',
        threshold_source: 'DEPLOY_CONFIG',
        quality_threshold_evaluated: true,
        advisory_only: true,
        publish_blocking_enabled: false,
        manual_approval_required: true,
        approval_workflow_ready: false,
        auto_publish_allowed: false,
        confirmed_candidate_required: true,
        quality_gate_override_supported: false,
        candidate_evidence_exported: false,
        approval_notes_exported: false,
        threshold_rule_detail_exported: false,
        aggregate_only: true
      },
      audit_chain_policy: {
        policy_version: 'wp5-audit-chain-policy-v1',
        chain_mode: 'WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT',
        event_source: 'TASK_REVIEW_PUBLISH_MODEL_REFERENCES',
        wp1_audit_event_written: true,
        wp2_invocation_reference_tracked: true,
        wp3_publish_reference_tracked: true,
        wp5_domain_events_tracked: true,
        project_scope_required: true,
        trace_signal_tracked: true,
        audit_event_detail_exported: false,
        candidate_identifier_list_exported: false,
        platform_audit_identifier_exported: false,
        trace_id_value_exported: false,
        model_invocation_id_value_exported: false,
        publish_identifier_value_exported: false,
        cross_wp_audit_dashboard_ready: false,
        audit_outbox_replay_dashboard_ready: false,
        aggregate_only: true
      },
      model_observation_policy: {
        policy_version: 'wp5-model-observation-policy-v1',
        observation_mode: 'ROUTING_COST_LATENCY_AGGREGATE',
        wp2_invocation_reference_tracked: true,
        trace_id_tracked: true,
        job_id_tracked: true,
        routing_metadata_tracked: true,
        token_usage_tracked: true,
        latency_tracked: true,
        cost_tracked: true,
        fallback_tracked: true,
        prompt_payload_stored: false,
        payload_preview_exported: false,
        trace_id_value_exported: false,
        job_id_value_exported: false,
        invocation_id_value_exported: false,
        provider_error_text_exported: false,
        actor_service_exported: false,
        aggregate_only: true
      },
      generation_orchestration_policy: {
        policy_version: 'wp5-generation-orchestration-policy-v1',
        orchestration_mode: 'SYNC_INLINE_GENERATION',
        async_generation_enabled: false,
        conditional_run_claim_supported: true,
        idempotent_create_replay_supported: true,
        duplicate_event_replay_safe: true,
        event_recovery_enabled: true,
        queued_event_replay_supported: false,
        running_timeout_recovery_enabled: true,
        explicit_retry_required_after_timeout: true,
        manual_task_retry_supported: true,
        manual_queued_event_replay_ready: false,
        queue_lag_metric_ready: true,
        timeout_alert_ready: true,
        multi_instance_load_test_evidence_ready: false,
        event_payload_exported: false,
        event_identifier_list_exported: false,
        queue_message_body_exported: false,
        recovery_detail_rows_exported: false,
        effective_recovery_batch_size: 100,
        running_timeout_seconds: 600,
        queue_lag_warning_seconds: 120,
        queued_status_signal: '0',
        running_status_signal: 0,
        timeout_failure_signal: 0,
        aggregate_only: true
      },
      archive_policy: {
        policy_version: 'wp5-archive-policy-v1',
        retention_days: '365',
        storage_policy: 'platformManaged',
        approval_required: true,
        archive_approval_workflow_ready: false,
        external_sharing_allowed: false,
        retention_policy_tracked: true,
        archive_storage_ready: false,
        archive_path_exported: false,
        archive_notes_exported: false,
        approval_notes_exported: false,
        ticket_url_exported: false,
        aggregate_only: true
      },
      report_manifest_policy: {
        policy_version: 'wp5-report-manifest-policy-v1',
        schema_version: 'wp5-task-report-v1',
        field_set_version: 'aggregate-only-v1',
        manifest_mode: 'AGGREGATE_RECONCILIATION',
        row_count_tracked: true,
        completion_status_tracked: true,
        archive_reconciliation_ready: true,
        detail_rows_exported: false,
        row_integrity_value_exported: false,
        row_content_summary_exported: false,
        candidate_identifier_list_exported: false,
        trace_identifier_list_exported: false,
        audit_identifier_list_exported: false,
        aggregate_only: true
      },
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
      inputDigest: 'a'.repeat(64),
      contextPolicyGovernance: {
        policySource: 'PLATFORM_DEFAULT',
        governanceStatus: 'PLATFORM_DEFAULT_ONLY',
        changeApprovalWorkflowReady: false
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
        aggregateOnly: true
      },
      contextPolicyOperations: {
        policyVersion: 'wp5-context-policy-operations-v2',
        operationMode: 'PLATFORM_DEFAULT_ONLY',
        policyResolutionOrder: 'PLATFORM_DEFAULT_ONLY',
        policyFallbackBehavior: 'DEPLOY_CONFIG_CHANGE_REQUIRED',
        approvalStatus: 'WORKFLOW_NOT_READY',
        projectOverrideStoreReady: false,
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
        evaluationCorpusProjectIsolated: true,
        evaluationCorpusOperationsReady: false,
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
        sampleMaintenanceReady: false,
        longTermCalibrationReady: false,
        operationsConsoleReady: false,
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
        approvalWorkflowReady: false,
        autoPublishAllowed: false,
        confirmedCandidateRequired: true,
        qualityGateOverrideSupported: false,
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
        crossWpAuditDashboardReady: false,
        auditOutboxReplayDashboardReady: false,
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
        orchestrationMode: 'SYNC_INLINE_GENERATION',
        asyncGenerationEnabled: false,
        conditionalRunClaimSupported: true,
        idempotentCreateReplaySupported: true,
        duplicateEventReplaySafe: true,
        eventRecoveryEnabled: true,
        queuedEventReplaySupported: false,
        runningTimeoutRecoveryEnabled: true,
        manualQueuedEventReplayReady: false,
        queueLagMetricReady: true,
        timeoutAlertReady: true,
        multiInstanceLoadTestEvidenceReady: false,
        eventIdentifierListExported: false,
        queueMessageBodyExported: false,
        queueLagWarningSeconds: 120,
        queuedStatusSignal: 0,
        runningStatusSignal: 0,
        timeoutFailureSignal: 0,
        aggregateOnly: true
      },
      archivePolicy: {
        policyVersion: 'wp5-archive-policy-v1',
        retentionDays: 365,
        storagePolicy: 'platformManaged',
        approvalRequired: true,
        archiveApprovalWorkflowReady: false,
        externalSharingAllowed: false,
        retentionPolicyTracked: true,
        archiveStorageReady: false,
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
        detailRowsExported: false,
        rowIntegrityValueExported: false,
        rowContentSummaryExported: false,
        candidateIdentifierListExported: false,
        traceIdentifierListExported: false,
        auditIdentifierListExported: false,
        aggregateOnly: true
      }
    });
    expect(task.contextSummary.contextVersion).toBe('wp5-context-v1');
    expect(health.archivePolicy).toMatchObject({
      policyVersion: 'wp5-archive-policy-v1',
      retentionDays: 180,
      storagePolicy: 'platformManaged',
      aggregateOnly: true
    });
    expect(health.reportManifestPolicy).toMatchObject({
      policyVersion: 'wp5-report-manifest-policy-v1',
      schemaVersion: 'wp5-task-report-v1',
      fieldSetVersion: 'aggregate-only-v1',
      manifestMode: 'AGGREGATE_RECONCILIATION',
      aggregateOnly: true
    });
    expect(health.modelObservationPolicy).toMatchObject({
      policyVersion: 'wp5-model-observation-policy-v1',
      observationMode: 'ROUTING_COST_LATENCY_AGGREGATE',
      wp2InvocationReferenceTracked: true,
      promptPayloadStored: false,
      payloadPreviewExported: false,
      traceIdValueExported: false,
      jobIdValueExported: false,
      invocationIdValueExported: false,
      providerErrorTextExported: false,
      actorServiceExported: false,
      aggregateOnly: true
    });
    expect(health.generationOrchestrationPolicy).toMatchObject({
      policyVersion: 'wp5-generation-orchestration-policy-v1',
      queueLagMetricReady: true,
      timeoutAlertReady: true,
      queuedTaskCount: 2,
      queueLagWarning: true,
      timeoutWarning: true,
      eventIdentifierListExported: false,
      queueMessageBodyExported: false,
      aggregateOnly: true
    });
    expect(task.archivePolicy).toMatchObject({
      policyVersion: 'wp5-archive-policy-v1',
      retentionDays: 365,
      storagePolicy: 'platformManaged',
      approvalRequired: true,
      archiveApprovalWorkflowReady: false,
      archiveStorageReady: false,
      aggregateOnly: true
    });
    expect(task.reportManifestPolicy).toMatchObject({
      policyVersion: 'wp5-report-manifest-policy-v1',
      rowCountTracked: true,
      completionStatusTracked: true,
      detailRowsExported: false,
      rowIntegrityValueExported: false,
      candidateIdentifierListExported: false,
      traceIdentifierListExported: false,
      auditIdentifierListExported: false,
      aggregateOnly: true
    });
    expect(task.modelObservationPolicy).toMatchObject({
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
    });
    expect(task.generationOrchestrationPolicy).toMatchObject({
      policyVersion: 'wp5-generation-orchestration-policy-v1',
      orchestrationMode: 'SYNC_INLINE_GENERATION',
      queuedEventReplaySupported: false,
      queueLagMetricReady: true,
      timeoutAlertReady: true,
      queuedStatusSignal: 0,
      timeoutFailureSignal: 0,
      aggregateOnly: true
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
      version_after: '2',
      diff_items: [
        { field: 'title', before: '旧标题', after: '新标题' },
        { field: 'status', before: 'GENERATED', after: 'EDITED' }
      ]
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
      versionAfter: 2,
      diffItems: [
        { field: 'title', before: '旧标题', after: '新标题' },
        { field: 'status', before: 'GENERATED', after: 'EDITED' }
      ]
    });
    expect(normalizeTestDesignReviewRecordList({ items: [reviewRecord], total: '3', index: '0', size: '10' })).toMatchObject({
      total: 3,
      index: 0,
      size: 10
    });

    const template = normalizeTestDesignTemplate({
      id: 'tpl-1',
      project_id: 'project-1',
      name: '登录模板',
      prompt_key: 'wp5-template-login',
      prompt_version: '2026.05',
      coverage_types: 'SMOKE,BOUNDARY',
      case_count_per_requirement: '2',
      context_defaults: {
        environmentKey: 'qa',
        contextApiIds: ['api-1']
      },
      enabled: 'true',
      updated_at: '2026-05-31T08:00:00Z'
    });
    expect(template).toMatchObject({
      id: 'tpl-1',
      projectId: 'project-1',
      promptKey: 'wp5-template-login',
      promptVersion: '2026.05',
      coverageTypes: ['SMOKE', 'BOUNDARY'],
      caseCountPerRequirement: 2,
      contextDefaults: {
        environmentKey: 'qa',
        contextApiIds: ['api-1']
      },
      enabled: true
    });
    expect(normalizeTestDesignTemplateList({ items: [template], total: '4', index: '0', size: '30' })).toMatchObject({
      total: 4,
      index: 0,
      size: 30
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

    const evaluationCorpusSummary = normalizeTestDesignEvaluationCorpusSummary({
      project_id: 'project-1',
      prompt_key: 'wp5-test-design-v1',
      policy: {
        policy_version: 'wp5-evaluation-corpus-policy-v1',
        corpus_mode: 'GOLDEN_SET_BASELINE',
        quality_gate_mode: 'MANUAL_OPT_IN_AI_EVAL',
        sample_maintenance_ready: false,
        corpus_row_exported: false
      },
      task_count: '2',
      candidate_count: '6',
      prompt_version_count: '1',
      readiness_distribution: [
        { label: 'WARNING', count: '1', percent: '100.00' }
      ],
      feedback_signal_count: '3',
      sample_candidate_count: '2',
      sample_explanation_count: '1',
      sample_explanation_coverage_percent: '33.33',
      aggregate_only: true,
      corpus_row_exported: false,
      candidate_body_exported: false,
      review_comment_exported: false,
      prompt_body_exported: false,
      generated_at: '2026-05-30T10:02:00Z'
    });
    expect(evaluationCorpusSummary).toMatchObject({
      projectId: 'project-1',
      promptKey: 'wp5-test-design-v1',
      taskCount: 2,
      candidateCount: 6,
      promptVersionCount: 1,
      readinessDistribution: [
        { label: 'WARNING', count: 1, percent: 100 }
      ],
      feedbackSignalCount: 3,
      sampleCandidateCount: 2,
      sampleExplanationCount: 1,
      sampleExplanationCoveragePercent: 33.33,
      aggregateOnly: true,
      corpusRowExported: false,
      candidateBodyExported: false,
      reviewCommentExported: false,
      promptBodyExported: false,
      policy: expect.objectContaining({
        policyVersion: 'wp5-evaluation-corpus-policy-v1',
        corpusMode: 'GOLDEN_SET_BASELINE',
        sampleMaintenanceReady: false
      })
    });

    const scopeSummary = normalizeTestDesignScopeSummary({
      project_id: 'project-1',
      prompt_key: 'wp5-test-design-v1',
      policy: {
        policy_version: 'wp5-scope-policy-v1',
        scope_model: 'PROJECT_RESOURCE_SCOPE',
        candidate_identifier_list_exported: false,
        role_rule_detail_exported: false,
        service_token_value_exported: false
      },
      task_count: '2',
      candidate_count: '4',
      publish_record_count: '2',
      project_bucket_count: '1',
      candidate_scope_mismatch_count: '0',
      publish_scope_mismatch_count: '0',
      model_invocation_reference_count: '1',
      publish_project_scope_record_count: '1',
      candidate_scope_coverage_percent: '100.00',
      publish_scope_coverage_percent: '100.00',
      metrics: [{ code: 'scopeMismatches', label: '作用域不一致', count: '0', tone: 'success' }],
      readiness: [
        {
          code: 'detailIdentifiersRedacted',
          label: '明细标识不导出',
          ready: true,
          tone: 'success',
          description: 'aggregate-only'
        }
      ],
      aggregate_only: true,
      candidate_identifier_list_exported: false,
      role_rule_detail_exported: false,
      service_token_value_exported: false,
      generated_at: '2026-05-30T10:03:00Z'
    });
    expect(scopeSummary).toMatchObject({
      projectId: 'project-1',
      promptKey: 'wp5-test-design-v1',
      taskCount: 2,
      candidateCount: 4,
      publishRecordCount: 2,
      projectBucketCount: 1,
      candidateScopeMismatchCount: 0,
      publishScopeMismatchCount: 0,
      modelInvocationReferenceCount: 1,
      publishProjectScopeRecordCount: 1,
      candidateScopeCoveragePercent: 100,
      publishScopeCoveragePercent: 100,
      metrics: [expect.objectContaining({ code: 'scopeMismatches', count: 0, tone: 'success' })],
      readiness: [expect.objectContaining({ code: 'detailIdentifiersRedacted', ready: true })],
      aggregateOnly: true,
      candidateIdentifierListExported: false,
      roleRuleDetailExported: false,
      serviceTokenValueExported: false,
      policy: expect.objectContaining({
        policyVersion: 'wp5-scope-policy-v1',
        scopeModel: 'PROJECT_RESOURCE_SCOPE'
      })
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

    await fetchTestDesignTemplates({ index: 0, size: 30, projectId: 'proj pay', enabled: true, includeGlobal: true, keyword: '冒烟' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/templates?index=0&size=30&projectId=proj+pay&enabled=true&includeGlobal=true&keyword=%E5%86%92%E7%83%9F');

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

    await fetchTestDesignEvaluationCorpusSummary({ index: 0, size: 10, projectId: 'proj pay', promptKey: 'wp5-test-design-v1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/quality/evaluation-corpus-summary?index=0&size=10&projectId=proj+pay&promptKey=wp5-test-design-v1');

    await fetchTestDesignScopeSummary({ index: 0, size: 10, projectId: 'proj pay', promptKey: 'wp5-test-design-v1' });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/quality/scope-summary?index=0&size=10&projectId=proj+pay&promptKey=wp5-test-design-v1');
  });

  it('calls context policy operations endpoints and normalizes sanitized metadata', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-policy-list',
      data: [
        {
          id: 'override-1',
          scope_type: 'ENVIRONMENT',
          project_id: 'proj pay',
          environment_key: 'qa env',
          status: 'PENDING',
          override_limits: {
            linked_assets_per_requirement: '99',
            linkedAssetsPerRequirement: '4',
            explicitAssetsPerType: 2
          },
          change_reason_code_captured: true,
          approval_reason_code_captured: false,
          work_order_key: 'WP5-CTX-1',
          work_order_title: 'QA policy approval',
          work_order_url: 'https://ticket.example/wp5/ctx-1',
          work_order_status: 'OPEN',
          policy_body: 'qa policy body',
          policy_body_digest: 'abcdef1234567890',
          policy_body_version: '2',
          policy_diff_summary: 'raise baseline',
          request_note: 'please review',
          note_count: '3',
          latest_note_preview: 'work order moved to review',
          requested_by: 'owner',
          created_at: '2026-05-31T10:00:00Z'
        }
      ]
    });

    const overrides = await fetchTestDesignContextPolicyOverrides('proj pay', { environmentKey: 'qa env' });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/projects/proj%20pay/overrides?environmentKey=qa+env'
    );
    expect(overrides.data[0]).toMatchObject({
      id: 'override-1',
      scopeType: 'ENVIRONMENT',
      projectId: 'proj pay',
      environmentKey: 'qa env',
      status: 'PENDING',
      overrideLimits: {
        linkedAssetsPerRequirement: 4,
        explicitAssetsPerType: 2
      },
      changeReasonCodeCaptured: true,
      approvalReasonCodeCaptured: false,
      workOrderKey: 'WP5-CTX-1',
      workOrderStatus: 'OPEN',
      policyBody: 'qa policy body',
      policyBodyDigest: 'abcdef1234567890',
      policyBodyVersion: 2,
      policyDiffSummary: 'raise baseline',
      requestNote: 'please review',
      noteCount: 3,
      latestNotePreview: 'work order moved to review'
    });

    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-policy-effective',
      data: {
        project_id: 'proj pay',
        environment_key: 'qa env',
        context_limits: {
          linkedAssetsPerRequirement: '4',
          explicitAssetsPerType: 2
        },
        applied_override_scopes: ['PLATFORM_DEFAULT', 'PROJECT', 'ENVIRONMENT'],
        override_status_counts: { PENDING: '1', APPROVED: 2 },
        policy_body_exported: false,
        policy_diff_preview_exported: false,
        approval_notes_exported: false,
        ticket_url_exported: false,
        aggregate_only: true
      }
    });

    const effective = await fetchTestDesignContextPolicyEffective('proj pay', { environmentKey: 'qa env' });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/projects/proj%20pay/effective?environmentKey=qa+env'
    );
    expect(effective.data).toMatchObject({
      projectId: 'proj pay',
      environmentKey: 'qa env',
      contextLimits: {
        linkedAssetsPerRequirement: 4,
        explicitAssetsPerType: 2
      },
      appliedOverrideScopes: ['PLATFORM_DEFAULT', 'PROJECT', 'ENVIRONMENT'],
      overrideStatusCounts: { PENDING: 1, APPROVED: 2 },
      policyBodyExported: false,
      policyDiffPreviewExported: false,
      approvalNotesExported: false,
      ticketUrlExported: false,
      aggregateOnly: true
    });

    expect(normalizeTestDesignContextPolicyOverride({
      id: 'override-2',
      override_limits: { linkedAssetSchemaChars: '180' },
      change_reason_code_captured: true,
      work_order_status: 'IN_REVIEW',
      note_count: '5'
    })).toMatchObject({
      id: 'override-2',
      overrideLimits: { linkedAssetSchemaChars: 180 },
      changeReasonCodeCaptured: true,
      workOrderStatus: 'IN_REVIEW',
      noteCount: 5
    });
    expect(normalizeTestDesignContextPolicyNote({
      id: 'note-1',
      override_id: 'override-2',
      note_type: 'WORK_ORDER',
      note_text: 'ticket linked',
      created_by: 'owner'
    })).toMatchObject({
      id: 'note-1',
      overrideId: 'override-2',
      noteType: 'WORK_ORDER',
      noteText: 'ticket linked',
      createdBy: 'owner'
    });
    expect(normalizeTestDesignContextPolicyEffective({
      context_limits: { linkedAssetsPerRequirement: '3' },
      applied_override_scopes: 'PLATFORM_DEFAULT,PROJECT'
    })).toMatchObject({
      contextLimits: { linkedAssetsPerRequirement: 3 },
      appliedOverrideScopes: ['PLATFORM_DEFAULT', 'PROJECT']
    });
  });

  it('calls context policy mutation endpoints with compact sanitized payloads', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-policy-mutation',
      data: { id: 'override-1', status: 'PENDING' }
    });

    await requestTestDesignProjectContextPolicyOverride('proj pay', {
      contextLinkedAssetsPerRequirement: 4,
      contextExplicitAssetsPerType: undefined,
      contextRequirementDescriptionChars: 180,
      changeReasonCode: ' QUALITY_BASELINE ',
      policyBody: ' qa policy body ',
      policyDiffSummary: ' raise baseline ',
      workOrderKey: ' WP5-CTX-1 ',
      workOrderTitle: ' QA policy approval ',
      workOrderUrl: ' https://ticket.example/wp5/ctx-1 ',
      requestNote: ' please review '
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/projects/proj%20pay/overrides',
      {
        method: 'POST',
        body: JSON.stringify({
          contextLinkedAssetsPerRequirement: 4,
          contextRequirementDescriptionChars: 180,
          changeReasonCode: 'QUALITY_BASELINE',
          policyBody: 'qa policy body',
          policyDiffSummary: 'raise baseline',
          workOrderKey: 'WP5-CTX-1',
          workOrderTitle: 'QA policy approval',
          workOrderUrl: 'https://ticket.example/wp5/ctx-1',
          requestNote: 'please review'
        })
      }
    );

    await requestTestDesignEnvironmentContextPolicyOverride('proj pay', 'qa env', {
      contextExplicitAssetsPerType: 2,
      changeReasonCode: 'SMOKE_VALIDATION'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/projects/proj%20pay/environments/qa%20env/overrides',
      {
        method: 'POST',
        body: JSON.stringify({
          contextExplicitAssetsPerType: 2,
          changeReasonCode: 'SMOKE_VALIDATION'
        })
      }
    );

    await updateTestDesignContextPolicyOverride('override 1', {
      contextLinkedAssetsPerRequirement: 5,
      policyBody: 'qa policy body v2',
      requestNote: 'body updated'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/overrides/override%201',
      {
        method: 'PUT',
        body: JSON.stringify({
          contextLinkedAssetsPerRequirement: 5,
          policyBody: 'qa policy body v2',
          requestNote: 'body updated'
        })
      }
    );

    await approveTestDesignContextPolicyOverride('override 1', {
      approvalReasonCode: ' SMOKE_VALIDATION ',
      reviewNote: ' approved by owner ',
      workOrderStatus: ' APPROVED '
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/overrides/override%201/approve',
      {
        method: 'POST',
        body: JSON.stringify({
          approvalReasonCode: 'SMOKE_VALIDATION',
          reviewNote: 'approved by owner',
          workOrderStatus: 'APPROVED'
        })
      }
    );

    await rejectTestDesignContextPolicyOverride('override 1', {
      approvalReasonCode: 'PROJECT_COMPLEXITY',
      reviewNote: 'needs changes',
      workOrderStatus: 'REJECTED'
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/overrides/override%201/reject',
      {
        method: 'POST',
        body: JSON.stringify({
          approvalReasonCode: 'PROJECT_COMPLEXITY',
          reviewNote: 'needs changes',
          workOrderStatus: 'REJECTED'
        })
      }
    );

    requestJsonMock.mockResolvedValueOnce({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-policy-notes',
      data: [{ id: 'note-1', override_id: 'override-1', note_type: 'COMMENT', note_text: 'body checked' }]
    });
    const notes = await fetchTestDesignContextPolicyNotes('override 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/context-policies/overrides/override%201/notes');
    expect(notes.data[0]).toMatchObject({ id: 'note-1', overrideId: 'override-1', noteText: 'body checked' });

    await addTestDesignContextPolicyNote('override 1', {
      noteType: ' WORK_ORDER ',
      noteText: ' ticket moved to review '
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith(
      '/api/v1/test-design/context-policies/overrides/override%201/notes',
      {
        method: 'POST',
        body: JSON.stringify({
          noteType: 'WORK_ORDER',
          noteText: 'ticket moved to review'
        })
      }
    );
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

    await replayQueuedTestDesignTaskEvent('task 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks/task%201/replay-queued-event', {
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
      templateId: ' tpl-1 ',
      title: '',
      requirementIds: ['req-1'],
      contextApiIds: ['api-1'],
      contextPageIds: ['page-1'],
      contextFlowIds: ['flow-1'],
      environmentKey: ' qa ',
      promptKey: ' wp5-template-login ',
      promptVersion: ' 2026.05 ',
      coverageTypes: ['SMOKE'],
      caseCountPerRequirement: 2,
      idempotencyKey: ' wp5-create-001 '
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/tasks', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-1',
        templateId: 'tpl-1',
        requirementIds: ['req-1'],
        contextApiIds: ['api-1'],
        contextPageIds: ['page-1'],
        contextFlowIds: ['flow-1'],
        environmentKey: 'qa',
        promptKey: 'wp5-template-login',
        promptVersion: '2026.05',
        coverageTypes: ['SMOKE'],
        caseCountPerRequirement: 2,
        idempotencyKey: 'wp5-create-001'
      })
    });

    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-template',
      data: { id: 'tpl-1', name: '登录模板', prompt_key: 'wp5-template-login', prompt_version: '2026.05' }
    });

    await createTestDesignTemplate({
      projectId: ' project-1 ',
      name: ' 登录模板 ',
      description: '',
      promptKey: ' wp5-template-login ',
      promptVersion: ' 2026.05 ',
      coverageTypes: ['SMOKE', 'BOUNDARY'],
      caseCountPerRequirement: 2,
      contextDefaults: {
        environmentKey: 'qa',
        contextApiIds: ['api-1']
      },
      enabled: true
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/templates', {
      method: 'POST',
      body: JSON.stringify({
        projectId: 'project-1',
        name: '登录模板',
        promptKey: 'wp5-template-login',
        promptVersion: '2026.05',
        coverageTypes: ['SMOKE', 'BOUNDARY'],
        caseCountPerRequirement: 2,
        contextDefaults: {
          environmentKey: 'qa',
          contextApiIds: ['api-1']
        },
        enabled: true
      })
    });

    await updateTestDesignTemplate('tpl 1', {
      name: ' 登录模板 v2 ',
      promptKey: ' wp5-template-login ',
      promptVersion: ' 2026.06 ',
      coverageTypes: ['REGRESSION'],
      caseCountPerRequirement: 1,
      contextDefaults: {},
      enabled: false
    });
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/templates/tpl%201', {
      method: 'PUT',
      body: JSON.stringify({
        name: '登录模板 v2',
        promptKey: 'wp5-template-login',
        promptVersion: '2026.06',
        coverageTypes: ['REGRESSION'],
        caseCountPerRequirement: 1,
        contextDefaults: {},
        enabled: false
      })
    });

    await deleteTestDesignTemplate('tpl 1');
    expect(requestJsonMock).toHaveBeenLastCalledWith('/api/v1/test-design/templates/tpl%201', {
      method: 'DELETE'
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
