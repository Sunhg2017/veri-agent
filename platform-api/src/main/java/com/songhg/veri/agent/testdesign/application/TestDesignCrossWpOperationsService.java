package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.ReplayTestDesignQueuedEventsCommand;
import com.songhg.veri.agent.testdesign.application.command.RequeueTestDesignAuditOutboxCommand;
import com.songhg.veri.agent.testdesign.application.command.RunTestDesignPublishCompensationCommand;
import com.songhg.veri.agent.testdesign.application.command.UpsertTestDesignQueueAlertSubscriptionCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCrossWpOperationsRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditOutboxOperationsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditOutboxRequeueResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditReportTemplateFieldResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditReportTemplateResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditReportTemplateSectionResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCompensationRunbookResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpAuditDetailRowResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpAuditDashboardResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpDetailAuditReportResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpOperationsDashboardResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationBucketResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationDrilldownResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignOperationsAuditReportResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishCompensationRunResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQueueAlertOperationsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQueueAlertSubscriptionResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQueuedEventReplayResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignScopePolicyResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpAuditDetailBucket;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpOperationsAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignModelObservationBucket;
import com.songhg.veri.agent.testdesign.domain.TestDesignOperationsAuditAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignQueueAlertSubscription;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Builds the unified WP5 cross-WP operations cockpit and performs bounded replay/compensation actions.
 *
 * <p>The cockpit is deliberately aggregate-only. It can prove WP1/WP2/WP3/WP5 links, queue alert coverage, replay
 * readiness and compensation readiness, but it never exports audit rows, outbox payloads, trace ids, model invocation
 * ids, sourceRef values, task ids, candidate ids or asset identifiers.</p>
 */
@Service
public class TestDesignCrossWpOperationsService {

    private static final int DEFAULT_REQUEUE_LIMIT = 20;
    private static final int MAX_REQUEUE_LIMIT = 100;
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD = "DEAD";
    private static final String STATUS_FAILED_OR_DEAD = "FAILED_OR_DEAD";
    private static final String TONE_SUCCESS = "success";
    private static final String TONE_INFO = "info";
    private static final String TONE_WARNING = "warning";
    private static final String TONE_NEUTRAL = "neutral";
    private static final String RESOURCE_TYPE = "TEST_DESIGN_CROSS_WP_OPERATIONS";
    private static final String ACTION_QUEUE_ALERT_UPSERT = "WP5_QUEUE_ALERT_SUBSCRIPTION_UPSERT";
    private static final String ACTION_QUEUED_EVENT_REPLAY = "WP5_CROSS_WP_QUEUED_EVENT_REPLAY";
    private static final String ACTION_PUBLISH_COMPENSATION_RUN = "WP5_PUBLISH_COMPENSATION_RUN";
    private static final String ACTION_AUDIT_OUTBOX_REQUEUE = "WP5_CROSS_WP_AUDIT_OUTBOX_REQUEUE";
    private static final String AUDIT_REPORT_TEMPLATE_VERSION = "wp5-cross-wp-audit-report-template-v1";
    private static final String AUDIT_REPORT_FIELD_SET_VERSION = "wp5-cross-wp-audit-fieldset-v1";
    private static final String REPLAY_GENERATION = "GENERATION";
    private static final String REPLAY_PUBLISH = "PUBLISH";
    private static final String REPLAY_ALL = "ALL";
    private static final String ALERT_GENERATION_QUEUE_LAG = "GENERATION_QUEUE_LAG";
    private static final String ALERT_GENERATION_TIMEOUT = "GENERATION_TIMEOUT";
    private static final String ALERT_PUBLISH_QUEUE_LAG = "PUBLISH_QUEUE_LAG";
    private static final String ALERT_PUBLISH_TIMEOUT = "PUBLISH_TIMEOUT";
    private static final String ALERT_COMPENSATION_FAILURE = "COMPENSATION_FAILURE";
    private static final String ALERT_AUDIT_OUTBOX_REPLAY_ELIGIBLE = "AUDIT_OUTBOX_REPLAY_ELIGIBLE";
    private static final Set<String> ALERT_TYPES = Set.of(
            ALERT_GENERATION_QUEUE_LAG,
            ALERT_GENERATION_TIMEOUT,
            ALERT_PUBLISH_QUEUE_LAG,
            ALERT_PUBLISH_TIMEOUT,
            ALERT_COMPENSATION_FAILURE,
            ALERT_AUDIT_OUTBOX_REPLAY_ELIGIBLE
    );
    private static final Set<String> CHANNELS = Set.of("OPS_CONSOLE", "EMAIL", "WEBHOOK");
    private static final Pattern TARGET_REF_PATTERN = Pattern.compile("^[A-Za-z0-9@._:/#-]{1,180}$");

    private final TestDesignRepository repository;
    private final TestDesignActorResolver actorResolver;
    private final AuthorizationService authorizationService;
    private final AuditLogWriter auditLogWriter;
    private final TestDesignEventPublisher eventPublisher;
    private final TestDesignPublishCompensationService publishCompensationService;
    private final TestDesignProperties properties;

    public TestDesignCrossWpOperationsService(
            TestDesignRepository repository,
            TestDesignActorResolver actorResolver,
            AuthorizationService authorizationService,
            AuditLogWriter auditLogWriter,
            TestDesignEventPublisher eventPublisher,
            TestDesignPublishCompensationService publishCompensationService,
            TestDesignProperties properties
    ) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.authorizationService = authorizationService;
        this.auditLogWriter = auditLogWriter;
        this.eventPublisher = eventPublisher;
        this.publishCompensationService = publishCompensationService;
        this.properties = properties;
    }

    /**
     * Returns a project-scoped or platform aggregate cross-WP operations snapshot.
     */
    @Transactional(readOnly = true)
    public TestDesignCrossWpOperationsDashboardResponse dashboard(TestDesignCrossWpOperationsRequest request) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        Instant now = Instant.now();
        TestDesignCrossWpOperationsAggregate aggregate = repository.crossWpOperationsAggregate(projectId, promptKey);
        TestDesignScopePolicyResponse scopePolicy = TestDesignScopePolicy.response();
        TestDesignAuditChainPolicyResponse auditPolicy = TestDesignAuditChainPolicy.response();
        TestDesignQueueAlertOperationsResponse queueAlerts = queueAlerts(projectId, promptKey, aggregate, now);
        TestDesignCompensationRunbookResponse runbook = compensationRunbook(projectId, promptKey, now);
        TestDesignOperationsAuditReportResponse auditReport = operationsAuditReport(projectId, promptKey, now);
        TestDesignAuditReportTemplateResponse auditTemplate = auditReportTemplate(projectId, promptKey, now);
        TestDesignModelObservationDrilldownResponse modelDrilldown = modelObservationDrilldown(projectId, promptKey, now);
        TestDesignCrossWpDetailAuditReportResponse detailReport = crossWpDetailAuditReport(projectId, promptKey, now);
        return new TestDesignCrossWpOperationsDashboardResponse(
                projectId,
                promptKey,
                scopePolicy,
                auditPolicy,
                aggregate.taskCount(),
                aggregate.candidateCount(),
                aggregate.publishRecordCount(),
                aggregate.projectBucketCount(),
                aggregate.candidateScopeMismatchCount(),
                aggregate.publishScopeMismatchCount(),
                aggregate.modelInvocationReferenceCount(),
                aggregate.publishProjectScopeRecordCount(),
                percentValue(aggregate.candidateCount() - aggregate.candidateScopeMismatchCount(),
                        aggregate.candidateCount()),
                percentValue(aggregate.publishRecordCount() - aggregate.publishScopeMismatchCount(),
                        aggregate.publishRecordCount()),
                auditDashboard(aggregate, auditPolicy),
                auditOutbox(aggregate),
                queueAlerts,
                runbook,
                auditReport,
                auditTemplate,
                modelDrilldown,
                detailReport,
                metrics(aggregate, queueAlerts, runbook, auditReport, modelDrilldown, detailReport),
                readiness(aggregate, scopePolicy, auditPolicy, queueAlerts, runbook, auditReport, auditTemplate,
                        modelDrilldown, detailReport),
                scopePolicy.aggregateOnly()
                        && auditPolicy.aggregateOnly()
                        && queueAlerts.aggregateOnly()
                        && runbook.aggregateOnly()
                        && auditReport.aggregateOnly()
                        && auditTemplate.aggregateOnly()
                        && modelDrilldown.aggregateOnly()
                        && detailReport.aggregateOnly(),
                false,
                now
        );
    }

    /**
     * Lists bounded queue alert subscriptions for one project/prompt scope.
     */
    @Transactional(readOnly = true)
    public List<TestDesignQueueAlertSubscriptionResponse> queueAlertSubscriptions(
            TestDesignCrossWpOperationsRequest request
    ) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        return repository.queueAlertSubscriptions(projectId, promptKey).stream()
                .map(TestDesignCrossWpOperationsService::subscriptionResponse)
                .toList();
    }

    /**
     * Creates or updates one non-secret queue alert subscription.
     *
     * <p>Webhook URLs, tokens, event payload snippets and task/candidate identifiers are rejected at the boundary. The
     * saved targetRef is a bounded routing alias only, so the operations dashboard can safely display it.</p>
     */
    @Transactional
    public TestDesignQueueAlertSubscriptionResponse upsertQueueAlertSubscription(
            UpsertTestDesignQueueAlertSubscriptionCommand command
    ) {
        if (command == null || !StringUtils.hasText(command.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        String projectId = command.projectId().trim();
        String promptKey = trimToNull(command.promptKey());
        String alertType = normalizeAlertType(command.alertType());
        String channel = normalizeChannel(command.channel());
        String targetRef = normalizeTargetRef(command.targetRef());
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestDesignQueueAlertSubscription existing = repository.queueAlertSubscriptionByKey(
                projectId,
                promptKey,
                alertType,
                channel,
                targetRef
        ).orElse(null);
        TestDesignQueueAlertSubscription saved = repository.saveQueueAlertSubscription(new TestDesignQueueAlertSubscription(
                existing == null ? UUID.randomUUID() : existing.id(),
                projectId,
                promptKey,
                alertType,
                channel,
                targetRef,
                command.thresholdSeconds(),
                command.enabled() == null || command.enabled(),
                existing == null ? actor : existing.createdBy(),
                actor,
                existing == null ? now : existing.createdAt(),
                now
        ));
        auditLogWriter.record(AuditLogWriter.success(
                currentUser(),
                ACTION_QUEUE_ALERT_UPSERT,
                RESOURCE_TYPE,
                projectId,
                "WP5 queue alert subscription upsert"
        ));
        return subscriptionResponse(saved);
    }

    /**
     * Replays queued generation and publish events inside one project/prompt scope.
     *
     * <p>The method republishes platform events using repository-selected ids, but returns only aggregate counts. It
     * does not expose event payloads, task ids, candidate ids or queue message identifiers.</p>
     */
    @Transactional
    public TestDesignQueuedEventReplayResponse replayQueuedEvents(ReplayTestDesignQueuedEventsCommand command) {
        if (command == null || !StringUtils.hasText(command.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        String projectId = command.projectId().trim();
        String promptKey = trimToNull(command.promptKey());
        String replayType = normalizeReplayType(command.replayType());
        int limit = safeLimit(command.maxItems());
        String reason = sanitizeReason(command.reason());
        int remaining = limit;
        int generationEvents = 0;
        int publishTaskEvents = 0;
        int publishCandidateEvents = 0;
        if (REPLAY_GENERATION.equals(replayType) || REPLAY_ALL.equals(replayType)) {
            List<TestDesignTask> tasks = repository.queuedTasksForReplay(projectId, promptKey, remaining);
            tasks.forEach(task -> eventPublisher.publishGenerationRequested(task.id()));
            generationEvents = tasks.size();
            remaining = Math.max(0, remaining - generationEvents);
        }
        if ((REPLAY_PUBLISH.equals(replayType) || REPLAY_ALL.equals(replayType)) && remaining > 0) {
            List<TestDesignCandidate> candidates =
                    repository.publishQueuedCandidatesForReplay(projectId, promptKey, remaining);
            Map<UUID, List<UUID>> candidatesByTask = candidates.stream()
                    .collect(Collectors.groupingBy(
                            TestDesignCandidate::taskId,
                            LinkedHashMap::new,
                            Collectors.mapping(TestDesignCandidate::id, Collectors.toList())
                    ));
            candidatesByTask.forEach(eventPublisher::publishPublishRequested);
            publishTaskEvents = candidatesByTask.size();
            publishCandidateEvents = candidates.size();
        }
        Instant now = Instant.now();
        auditLogWriter.record(AuditLogWriter.success(
                currentUser(),
                ACTION_QUEUED_EVENT_REPLAY,
                RESOURCE_TYPE,
                projectId,
                reason == null ? "WP5 queued event replay" : "WP5 queued event replay: " + reason
        ));
        return new TestDesignQueuedEventReplayResponse(
                projectId,
                promptKey,
                replayType,
                limit,
                generationEvents,
                publishTaskEvents,
                publishCandidateEvents,
                true,
                false,
                false,
                false,
                true,
                now
        );
    }

    /**
     * Returns the compensation runbook for one project/prompt scope.
     */
    @Transactional(readOnly = true)
    public TestDesignCompensationRunbookResponse compensationRunbook(TestDesignCrossWpOperationsRequest request) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        return compensationRunbook(projectId, promptKey, Instant.now());
    }

    /**
     * Runs bounded publish compensation manually inside one project/prompt scope.
     */
    @Transactional
    public TestDesignPublishCompensationRunResponse runPublishCompensation(
            RunTestDesignPublishCompensationCommand command
    ) {
        if (command == null || !StringUtils.hasText(command.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        String projectId = command.projectId().trim();
        String promptKey = trimToNull(command.promptKey());
        int limit = safeLimit(command.maxItems());
        String reason = sanitizeReason(command.reason());
        TestDesignPublishCompensationService.CompensationResult result =
                publishCompensationService.compensateFailedLinkedCandidates("manual", projectId, promptKey, limit);
        Instant now = Instant.now();
        auditLogWriter.record(AuditLogWriter.success(
                currentUser(),
                ACTION_PUBLISH_COMPENSATION_RUN,
                RESOURCE_TYPE,
                projectId,
                reason == null ? "WP5 publish compensation run" : "WP5 publish compensation run: " + reason
        ));
        return new TestDesignPublishCompensationRunResponse(
                projectId,
                promptKey,
                result.trigger(),
                limit,
                result.scannedCandidates(),
                result.succeededCandidates(),
                result.failedCandidates(),
                result.skippedCandidates(),
                properties.publishCompensationEnabled(),
                true,
                true,
                false,
                false,
                false,
                now
        );
    }

    /**
     * Returns aggregate-only batch operation audit counts.
     */
    @Transactional(readOnly = true)
    public TestDesignOperationsAuditReportResponse operationsAuditReport(TestDesignCrossWpOperationsRequest request) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        return operationsAuditReport(projectId, promptKey, Instant.now());
    }

    /**
     * Returns the fixed audit report template and export guardrails for one operations scope.
     */
    @Transactional(readOnly = true)
    public TestDesignAuditReportTemplateResponse auditReportTemplate(TestDesignCrossWpOperationsRequest request) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        return auditReportTemplate(projectId, promptKey, Instant.now());
    }

    /**
     * Returns model observation drilldown buckets without exposing invocation, job or trace identifiers.
     */
    @Transactional(readOnly = true)
    public TestDesignModelObservationDrilldownResponse modelObservationDrilldown(
            TestDesignCrossWpOperationsRequest request
    ) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        return modelObservationDrilldown(projectId, promptKey, Instant.now());
    }

    /**
     * Returns redacted cross-WP audit detail rows grouped by source, category and status.
     */
    @Transactional(readOnly = true)
    public TestDesignCrossWpDetailAuditReportResponse crossWpDetailAuditReport(
            TestDesignCrossWpOperationsRequest request
    ) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        return crossWpDetailAuditReport(projectId, promptKey, Instant.now());
    }

    /**
     * Requeues failed/dead WP1 audit outbox events related to a WP5 project scope.
     */
    @Transactional
    public TestDesignAuditOutboxRequeueResponse requeueAuditOutbox(RequeueTestDesignAuditOutboxCommand command) {
        if (command == null || !StringUtils.hasText(command.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        String status = normalizeReplayStatus(command.status());
        int limit = safeLimit(command.maxItems());
        String reason = sanitizeReason(command.reason());
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        int requeued = repository.requeueAuditOutbox(
                command.projectId().trim(),
                status,
                limit,
                reason,
                actor,
                now
        );
        auditLogWriter.record(AuditLogWriter.success(
                currentUser(),
                ACTION_AUDIT_OUTBOX_REQUEUE,
                RESOURCE_TYPE,
                command.projectId().trim(),
                "WP5 cross-WP audit outbox requeue"
        ));
        return new TestDesignAuditOutboxRequeueResponse(
                command.projectId().trim(),
                status,
                limit,
                requeued,
                true,
                false,
                false,
                now
        );
    }

    private TestDesignQueueAlertOperationsResponse queueAlerts(
            String projectId,
            String promptKey,
            TestDesignCrossWpOperationsAggregate aggregate,
            Instant now
    ) {
        List<TestDesignQueueAlertSubscription> subscriptions = repository.queueAlertSubscriptions(projectId, promptKey);
        long enabledSubscriptionCount = subscriptions.stream().filter(TestDesignQueueAlertSubscription::enabled).count();
        long generationQueued = repository.countTasksByStatus(projectId, promptKey, TestDesignTaskStatus.QUEUED);
        long publishQueued = repository.countCandidatesByStatus(
                projectId,
                promptKey,
                TestDesignCandidateStatus.PUBLISH_QUEUED
        );
        long generationLagThreshold = alertThreshold(
                subscriptions,
                ALERT_GENERATION_QUEUE_LAG,
                properties.eventRecoveryQueueLagWarningSeconds()
        );
        long publishLagThreshold = alertThreshold(
                subscriptions,
                ALERT_PUBLISH_QUEUE_LAG,
                properties.publishEventRecoveryQueueLagWarningSeconds()
        );
        long oldestGenerationAge = ageSeconds(
                repository.oldestTaskUpdatedAtByStatus(projectId, promptKey, TestDesignTaskStatus.QUEUED),
                now
        );
        long oldestPublishAge = ageSeconds(
                repository.oldestCandidateUpdatedAtByStatus(
                        projectId,
                        promptKey,
                        TestDesignCandidateStatus.PUBLISH_QUEUED
                ),
                now
        );
        long staleRunning = properties.eventRecoveryRunningTimeoutSeconds() <= 0 ? 0
                : repository.countStaleRunningTasks(projectId, promptKey,
                        now.minusSeconds(properties.eventRecoveryRunningTimeoutSeconds()));
        long stalePublishing = properties.publishEventRecoveryRunningTimeoutSeconds() <= 0 ? 0
                : repository.countStalePublishingCandidates(projectId, promptKey,
                        now.minusSeconds(properties.publishEventRecoveryRunningTimeoutSeconds()));
        long compensationEligible = repository.countPublishCompensationCandidates(projectId, promptKey);
        boolean generationLagWarning = generationLagThreshold >= 0 && generationQueued > 0
                && oldestGenerationAge > generationLagThreshold;
        boolean publishLagWarning = publishLagThreshold >= 0 && publishQueued > 0
                && oldestPublishAge > publishLagThreshold;
        boolean generationTimeoutWarning = staleRunning > 0;
        boolean publishTimeoutWarning = stalePublishing > 0;
        boolean compensationFailureWarning = compensationEligible > 0;
        long activeWarningCount = activeWarningCount(
                subscriptions,
                generationLagWarning,
                generationTimeoutWarning,
                publishLagWarning,
                publishTimeoutWarning,
                compensationFailureWarning,
                aggregate.replayEligibleOutboxCount() > 0
        );
        return new TestDesignQueueAlertOperationsResponse(
                "wp5-queue-alert-operations-v1",
                subscriptions.size(),
                enabledSubscriptionCount,
                subscriptions.size() - enabledSubscriptionCount,
                generationQueued,
                staleRunning,
                publishQueued,
                stalePublishing,
                compensationEligible,
                oldestGenerationAge,
                oldestPublishAge,
                generationLagThreshold,
                publishLagThreshold,
                generationLagWarning,
                generationTimeoutWarning,
                publishLagWarning,
                publishTimeoutWarning,
                compensationFailureWarning,
                activeWarningCount,
                enabledSubscriptionCount > 0,
                true,
                true,
                false,
                false,
                now
        );
    }

    private TestDesignCompensationRunbookResponse compensationRunbook(
            String projectId,
            String promptKey,
            Instant now
    ) {
        long eligibleCount = repository.countPublishCompensationCandidates(projectId, promptKey);
        return new TestDesignCompensationRunbookResponse(
                "wp5-publish-compensation-runbook-v1",
                projectId,
                promptKey,
                properties.publishCompensationEnabled(),
                properties.publishCompensationEnabled(),
                true,
                true,
                properties.effectivePublishCompensationBatchSize(),
                eligibleCount,
                false,
                false,
                false,
                false,
                false,
                true,
                List.of(
                        readiness("compensationScopeLocked", "候选选择范围",
                                true,
                                "仅选择 FAILED 且已有 WP3 用例引用、无成功发布记录、无自动补偿记录的候选"),
                        readiness("manualRunSupported", "人工补偿运行",
                                true,
                                "支持按项目和 prompt 有界触发补偿"),
                        readiness("automaticScheduleReady", "自动调度",
                                properties.publishCompensationEnabled(),
                                "自动调度使用同一补偿候选选择策略"),
                        readiness("firstCreateBlocked", "禁止自动首次创建",
                                true,
                                "补偿后台只补链和恢复记录，不创建新的 WP3 用例资产"),
                        readiness("detailIdentifiersRedacted", "明细标识不导出",
                                true,
                                "运行手册和 dashboard 只返回聚合计数")
                ),
                now
        );
    }

    private TestDesignOperationsAuditReportResponse operationsAuditReport(
            String projectId,
            String promptKey,
            Instant now
    ) {
        TestDesignOperationsAuditAggregate aggregate = repository.operationsAuditAggregate(projectId, promptKey);
        return new TestDesignOperationsAuditReportResponse(
                projectId,
                promptKey,
                aggregate.totalOperationCount(),
                aggregate.successCount(),
                aggregate.failedCount(),
                aggregate.deniedCount(),
                aggregate.queueAlertSubscriptionMutationCount(),
                aggregate.queuedEventReplayCount(),
                aggregate.publishCompensationRunCount(),
                aggregate.auditOutboxRequeueCount(),
                aggregate.latestOperationAt(),
                true,
                false,
                false,
                false,
                true,
                now
        );
    }

    private static TestDesignAuditReportTemplateResponse auditReportTemplate(
            String projectId,
            String promptKey,
            Instant now
    ) {
        return new TestDesignAuditReportTemplateResponse(
                projectId,
                promptKey,
                AUDIT_REPORT_TEMPLATE_VERSION,
                AUDIT_REPORT_FIELD_SET_VERSION,
                List.of(
                        templateSection("operationsSummary", "运营审计汇总", "运营动作的结果计数和最近时间",
                                List.of(
                                        templateField("totalOperationCount", "操作总数", "audit_log", "AGGREGATE_COUNT"),
                                        templateField("successCount", "成功数", "audit_log", "AGGREGATE_COUNT"),
                                        templateField("failedCount", "失败数", "audit_log", "AGGREGATE_COUNT"),
                                        templateField("deniedCount", "拒绝数", "audit_log", "AGGREGATE_COUNT"),
                                        templateField("latestOperationAt", "最近操作时间", "audit_log", "TIMESTAMP")
                                )),
                        templateSection("queueAlertSubscriptions", "告警订阅", "队列告警配置和激活告警聚合",
                                List.of(
                                        templateField("subscriptionCount", "订阅数", "test_design_queue_alert_subscription",
                                                "AGGREGATE_COUNT"),
                                        templateField("activeWarningCount", "激活告警数", "runtime_queue_metrics",
                                                "AGGREGATE_COUNT"),
                                        templateField("generationQueueLagWarningSeconds", "生成队列阈值", "runtime_config",
                                                "AGGREGATE_NUMBER"),
                                        templateField("publishQueueLagWarningSeconds", "发布队列阈值", "runtime_config",
                                                "AGGREGATE_NUMBER")
                                )),
                        templateSection("modelObservationDrilldown", "模型观测聚合钻取", "WP2 调用路由、成本和延迟桶",
                                List.of(
                                        templateField("dimension", "钻取维度", "ma_invocation_log", "REDACTED_BUCKET"),
                                        templateField("bucketKey", "桶编码", "ma_invocation_log", "REDACTED_BUCKET"),
                                        templateField("invocationCount", "调用数", "ma_invocation_log", "AGGREGATE_COUNT"),
                                        templateField("tokenTotals", "token 总数", "ma_invocation_log", "AGGREGATE_NUMBER"),
                                        templateField("latencyAndCost", "耗时和成本", "ma_invocation_log",
                                                "AGGREGATE_NUMBER")
                                )),
                        templateSection("crossWpDetailAudit", "跨 WP 脱敏明细", "按 WP 来源、分类和状态聚合的明细行",
                                List.of(
                                        templateField("section", "WP 分区", "wp_aggregate_sources", "REDACTED_BUCKET"),
                                        templateField("category", "分类", "wp_aggregate_sources", "REDACTED_BUCKET"),
                                        templateField("status", "状态", "wp_aggregate_sources", "REDACTED_BUCKET"),
                                        templateField("eventCount", "事件数", "wp_aggregate_sources", "AGGREGATE_COUNT"),
                                        templateField("latestEventAt", "最近事件时间", "wp_aggregate_sources",
                                                "TIMESTAMP")
                                )),
                        templateSection("exportGuardrails", "导出红线", "固定禁止导出的敏感字段族",
                                List.of(
                                        templateField("identifierValuesExported", "标识原值导出", "policy",
                                                "BOOLEAN_GUARDRAIL"),
                                        templateField("payloadExported", "payload/正文导出", "policy",
                                                "BOOLEAN_GUARDRAIL"),
                                        templateField("actorIdentifierExported", "操作者标识导出", "policy",
                                                "BOOLEAN_GUARDRAIL"),
                                        templateField("aggregateOnly", "聚合输出", "policy", "BOOLEAN_GUARDRAIL")
                                ))
                ),
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                now
        );
    }

    private TestDesignModelObservationDrilldownResponse modelObservationDrilldown(
            String projectId,
            String promptKey,
            Instant now
    ) {
        List<TestDesignModelObservationBucket> buckets = repository.modelObservationBuckets(projectId, promptKey);
        List<TestDesignModelObservationBucket> totalBuckets = buckets.stream()
                .filter(bucket -> "STATUS".equals(bucket.dimension()))
                .toList();
        long totalInvocationCount = totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::invocationCount).sum();
        long latencyMsTotal = totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::latencyMsTotal).sum();
        return new TestDesignModelObservationDrilldownResponse(
                projectId,
                promptKey,
                totalInvocationCount,
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::succeededCount).sum(),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::failedCount).sum(),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::blockedCount).sum(),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::fallbackCount).sum(),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::inputTokenTotal).sum(),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::outputTokenTotal).sum(),
                latencyMsTotal,
                totalInvocationCount == 0 ? 0 : Math.round((double) latencyMsTotal / totalInvocationCount),
                sumCostText(totalBuckets),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::traceSignalCount).sum(),
                totalBuckets.stream().mapToLong(TestDesignModelObservationBucket::jobSignalCount).sum(),
                buckets.stream().map(TestDesignCrossWpOperationsService::modelBucketResponse).toList(),
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                now
        );
    }

    private TestDesignCrossWpDetailAuditReportResponse crossWpDetailAuditReport(
            String projectId,
            String promptKey,
            Instant now
    ) {
        List<TestDesignCrossWpAuditDetailBucket> buckets = repository.crossWpAuditDetailBuckets(projectId, promptKey);
        return new TestDesignCrossWpDetailAuditReportResponse(
                projectId,
                promptKey,
                AUDIT_REPORT_TEMPLATE_VERSION,
                buckets.size(),
                buckets.stream().map(TestDesignCrossWpOperationsService::detailRowResponse).toList(),
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                now
        );
    }

    private static TestDesignCrossWpAuditDashboardResponse auditDashboard(
            TestDesignCrossWpOperationsAggregate aggregate,
            TestDesignAuditChainPolicyResponse policy
    ) {
        return new TestDesignCrossWpAuditDashboardResponse(
                aggregate.wp1AuditEventCount(),
                aggregate.wp1AuditSuccessCount(),
                aggregate.wp1AuditFailureCount(),
                aggregate.wp1AuditDeniedCount(),
                aggregate.wp2InvocationCount(),
                aggregate.wp2InvocationSucceededCount(),
                aggregate.wp2InvocationFailedCount(),
                aggregate.wp2InvocationBlockedCount(),
                aggregate.wp2FallbackCount(),
                aggregate.wp2TraceSignalCount(),
                aggregate.wp3PublishedCaseCount(),
                aggregate.wp3TraceLinkCount(),
                policy.crossWpAuditDashboardReady(),
                policy.auditEventDetailExported(),
                policy.traceIdValueExported(),
                policy.modelInvocationIdValueExported(),
                policy.publishIdentifierValueExported(),
                policy.aggregateOnly()
        );
    }

    private static TestDesignAuditOutboxOperationsResponse auditOutbox(TestDesignCrossWpOperationsAggregate aggregate) {
        return new TestDesignAuditOutboxOperationsResponse(
                aggregate.auditOutboxTotalCount(),
                aggregate.auditOutboxPendingCount(),
                aggregate.auditOutboxProcessingCount(),
                aggregate.auditOutboxDoneCount(),
                aggregate.auditOutboxFailedCount(),
                aggregate.auditOutboxDeadCount(),
                aggregate.replayEligibleOutboxCount(),
                true,
                false,
                false,
                false,
                true
        );
    }

    private static List<TestDesignAuditChainMetricResponse> metrics(
            TestDesignCrossWpOperationsAggregate aggregate,
            TestDesignQueueAlertOperationsResponse queueAlerts,
            TestDesignCompensationRunbookResponse runbook,
            TestDesignOperationsAuditReportResponse auditReport,
            TestDesignModelObservationDrilldownResponse modelDrilldown,
            TestDesignCrossWpDetailAuditReportResponse detailReport
    ) {
        List<TestDesignAuditChainMetricResponse> metrics = new ArrayList<>();
        metrics.add(metric("taskProjectScopes", "任务项目作用域", aggregate.taskCount(),
                aggregate.taskCount() > 0 ? TONE_SUCCESS : TONE_INFO));
        metrics.add(metric("candidateProjectScopes", "候选项目作用域", aggregate.candidateCount(),
                aggregate.candidateScopeMismatchCount() == 0 ? TONE_SUCCESS : TONE_WARNING));
        metrics.add(metric("publishProjectScopes", "发布项目作用域", aggregate.publishRecordCount(),
                aggregate.publishScopeMismatchCount() == 0 ? TONE_SUCCESS : TONE_WARNING));
        metrics.add(metric("scopeMismatches", "作用域不一致", aggregate.scopeMismatchCount(),
                aggregate.scopeMismatchCount() == 0 ? TONE_SUCCESS : TONE_WARNING));
        metrics.add(metric("wp1AuditEvents", "WP1 审计事件", aggregate.wp1AuditEventCount(),
                aggregate.wp1AuditEventCount() > 0 ? TONE_SUCCESS : TONE_INFO));
        metrics.add(metric("wp2Invocations", "WP2 模型调用", aggregate.wp2InvocationCount(),
                aggregate.wp2InvocationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("wp3PublishedCases", "WP3 发布用例", aggregate.wp3PublishedCaseCount(),
                aggregate.wp3PublishedCaseCount() > 0 ? TONE_SUCCESS : TONE_NEUTRAL));
        metrics.add(metric("auditOutboxReplayEligible", "Audit outbox 可重放", aggregate.replayEligibleOutboxCount(),
                aggregate.replayEligibleOutboxCount() > 0 ? TONE_WARNING : TONE_SUCCESS));
        metrics.add(metric("queueAlertActiveWarnings", "队列告警激活", queueAlerts.activeWarningCount(),
                queueAlerts.activeWarningCount() > 0 ? TONE_WARNING : TONE_SUCCESS));
        metrics.add(metric("compensationEligible", "可补偿候选", runbook.eligibleCandidateCount(),
                runbook.eligibleCandidateCount() > 0 ? TONE_WARNING : TONE_SUCCESS));
        metrics.add(metric("operationsAuditEvents", "运营审计操作", auditReport.totalOperationCount(),
                auditReport.totalOperationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("modelObservationBuckets", "模型观测钻取桶", modelDrilldown.buckets().size(),
                modelDrilldown.totalInvocationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("crossWpDetailRows", "跨 WP 明细行", detailReport.rowCount(),
                detailReport.rowCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        return metrics;
    }

    private static List<TestDesignAuditChainReadinessResponse> readiness(
            TestDesignCrossWpOperationsAggregate aggregate,
            TestDesignScopePolicyResponse scopePolicy,
            TestDesignAuditChainPolicyResponse auditPolicy,
            TestDesignQueueAlertOperationsResponse queueAlerts,
            TestDesignCompensationRunbookResponse runbook,
            TestDesignOperationsAuditReportResponse auditReport,
            TestDesignAuditReportTemplateResponse auditTemplate,
            TestDesignModelObservationDrilldownResponse modelDrilldown,
            TestDesignCrossWpDetailAuditReportResponse detailReport
    ) {
        return List.of(
                readiness("crossWpScopeDashboardReady", "跨 WP 统一作用域看板",
                        scopePolicy.crossWpScopeDashboardReady(),
                        "项目/候选/发布记录 scope 覆盖率已在统一看板聚合"),
                readiness("crossWpAuditDashboardReady", "跨 WP 审计链看板",
                        auditPolicy.crossWpAuditDashboardReady(),
                        "WP1 审计、WP2 调用和 WP3 发布引用已按项目聚合"),
                readiness("auditOutboxReplayDashboardReady", "Audit outbox 重放看板",
                        auditPolicy.auditOutboxReplayDashboardReady(),
                        "支持按项目 scope 将 FAILED/DEAD outbox 受限重新排队"),
                readiness("queueAlertSubscriptionReady", "队列告警订阅",
                        queueAlerts.subscriptionConfigReady(),
                        "支持项目和 prompt 维度订阅聚合队列告警"),
                readiness("manualQueuedEventReplayReady", "人工队列重放",
                        queueAlerts.manualReplaySupported(),
                        "支持按项目和 prompt 有界重发 queued generation/publish 事件"),
                readiness("publishCompensationRunbookReady", "发布补偿运行手册",
                        runbook.manualRunSupported() && runbook.scopedRunSupported(),
                        "补偿运行手册和手工运行均返回聚合计数"),
                readiness("operationsAuditReportReady", "批量运营审计报表",
                        auditReport.exportSupported() && auditReport.aggregateOnly(),
                        "订阅、重放、补偿和 outbox 重排均进入聚合审计报表"),
                readiness("auditReportTemplateReady", "审计报表模板",
                        auditTemplate.exportSupported() && auditTemplate.aggregateOnly(),
                        "固定模板声明汇总、告警、模型观测、跨 WP 明细和导出红线字段"),
                readiness("modelObservationDrilldownReady", "模型观测聚合钻取",
                        modelDrilldown.drilldownSupported() && modelDrilldown.aggregateOnly(),
                        "按 status/provider/model/routing/prompt/fallback 聚合，不导出调用标识或载荷"),
                readiness("crossWpDetailAuditReportReady", "跨 WP 明细审计报表",
                        detailReport.detailReportSupported() && detailReport.aggregateOnly(),
                        "按 WP 来源、分类和状态输出脱敏明细桶"),
                readiness("scopeMismatchClear", "作用域一致性",
                        aggregate.scopeMismatchCount() == 0,
                        "候选和发布记录项目 scope 必须与任务项目保持一致"),
                readiness("detailIdentifiersRedacted", "明细标识不导出",
                        scopePolicy.aggregateOnly()
                                && auditPolicy.aggregateOnly()
                                && queueAlerts.aggregateOnly()
                                && runbook.aggregateOnly()
                                && auditReport.aggregateOnly()
                                && auditTemplate.aggregateOnly()
                                && modelDrilldown.aggregateOnly()
                                && detailReport.aggregateOnly()
                                && !scopePolicy.candidateIdentifierListExported()
                                && !auditPolicy.traceIdValueExported()
                                && !auditPolicy.modelInvocationIdValueExported()
                                && !auditPolicy.publishIdentifierValueExported()
                                && !queueAlerts.detailIdentifiersExported()
                                && !runbook.assetCaseIdentifierExported()
                                && !auditReport.detailRowsExported()
                                && !auditTemplate.identifierValuesExported()
                                && !modelDrilldown.invocationIdValueExported()
                                && !modelDrilldown.traceIdValueExported()
                                && !detailReport.identifierValuesExported()
                                && !detailReport.payloadExported(),
                        "候选 ID、资产 ID、traceId、模型调用 ID、sourceRef、outbox payload 均不进入看板")
        );
    }

    private static TestDesignAuditChainMetricResponse metric(String code, String label, long count, String tone) {
        return new TestDesignAuditChainMetricResponse(code, label, count, tone);
    }

    private static TestDesignAuditReportTemplateSectionResponse templateSection(
            String code,
            String label,
            String description,
            List<TestDesignAuditReportTemplateFieldResponse> fields
    ) {
        return new TestDesignAuditReportTemplateSectionResponse(code, label, description, fields);
    }

    private static TestDesignAuditReportTemplateFieldResponse templateField(
            String code,
            String label,
            String source,
            String exportMode
    ) {
        return new TestDesignAuditReportTemplateFieldResponse(code, label, source, exportMode, true, false, false);
    }

    private static TestDesignModelObservationBucketResponse modelBucketResponse(
            TestDesignModelObservationBucket bucket
    ) {
        return new TestDesignModelObservationBucketResponse(
                bucket.dimension(),
                bucket.bucketKey(),
                bucket.bucketLabel(),
                bucket.invocationCount(),
                bucket.succeededCount(),
                bucket.failedCount(),
                bucket.blockedCount(),
                bucket.fallbackCount(),
                bucket.inputTokenTotal(),
                bucket.outputTokenTotal(),
                bucket.latencyMsTotal(),
                bucket.averageLatencyMs(),
                bucket.totalCostText(),
                bucket.traceSignalCount(),
                bucket.jobSignalCount(),
                bucket.latestInvocationAt()
        );
    }

    private static TestDesignCrossWpAuditDetailRowResponse detailRowResponse(
            TestDesignCrossWpAuditDetailBucket bucket
    ) {
        return new TestDesignCrossWpAuditDetailRowResponse(
                bucket.section(),
                bucket.category(),
                bucket.status(),
                bucket.eventCount(),
                bucket.successCount(),
                bucket.failedCount(),
                bucket.warningCount(),
                bucket.latestEventAt(),
                false,
                false,
                false,
                true
        );
    }

    private static String sumCostText(List<TestDesignModelObservationBucket> buckets) {
        BigDecimal total = BigDecimal.ZERO;
        for (TestDesignModelObservationBucket bucket : buckets) {
            if (!StringUtils.hasText(bucket.totalCostText())) {
                continue;
            }
            total = total.add(new BigDecimal(bucket.totalCostText()));
        }
        return total.stripTrailingZeros().toPlainString();
    }

    private static TestDesignAuditChainReadinessResponse readiness(
            String code,
            String label,
            boolean ready,
            String description
    ) {
        return new TestDesignAuditChainReadinessResponse(
                code,
                label,
                ready,
                ready ? TONE_SUCCESS : TONE_WARNING,
                description
        );
    }

    private static TestDesignQueueAlertSubscriptionResponse subscriptionResponse(
            TestDesignQueueAlertSubscription subscription
    ) {
        return new TestDesignQueueAlertSubscriptionResponse(
                subscription.id(),
                subscription.projectId(),
                subscription.promptKey(),
                subscription.alertType(),
                subscription.channel(),
                subscription.targetRef(),
                subscription.thresholdSeconds(),
                subscription.enabled(),
                subscription.createdAt(),
                subscription.updatedAt()
        );
    }

    private static long activeWarningCount(
            List<TestDesignQueueAlertSubscription> subscriptions,
            boolean generationLagWarning,
            boolean generationTimeoutWarning,
            boolean publishLagWarning,
            boolean publishTimeoutWarning,
            boolean compensationFailureWarning,
            boolean auditOutboxReplayWarning
    ) {
        Map<String, Boolean> warnings = new LinkedHashMap<>();
        warnings.put(ALERT_GENERATION_QUEUE_LAG, generationLagWarning);
        warnings.put(ALERT_GENERATION_TIMEOUT, generationTimeoutWarning);
        warnings.put(ALERT_PUBLISH_QUEUE_LAG, publishLagWarning);
        warnings.put(ALERT_PUBLISH_TIMEOUT, publishTimeoutWarning);
        warnings.put(ALERT_COMPENSATION_FAILURE, compensationFailureWarning);
        warnings.put(ALERT_AUDIT_OUTBOX_REPLAY_ELIGIBLE, auditOutboxReplayWarning);
        return warnings.entrySet().stream()
                .filter(Map.Entry::getValue)
                .filter(entry -> subscriptions.stream()
                        .anyMatch(subscription -> subscription.enabled()
                                && entry.getKey().equals(subscription.alertType())))
                .count();
    }

    private static long alertThreshold(
            List<TestDesignQueueAlertSubscription> subscriptions,
            String alertType,
            long defaultThreshold
    ) {
        return subscriptions.stream()
                .filter(TestDesignQueueAlertSubscription::enabled)
                .filter(subscription -> alertType.equals(subscription.alertType()))
                .map(TestDesignQueueAlertSubscription::thresholdSeconds)
                .filter(value -> value != null && value >= 0)
                .mapToLong(Integer::longValue)
                .min()
                .orElse(Math.max(0L, defaultThreshold));
    }

    private static long ageSeconds(java.util.Optional<Instant> oldestAt, Instant now) {
        if (oldestAt.isEmpty() || oldestAt.get() == null || oldestAt.get().isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(oldestAt.get(), now).getSeconds());
    }

    private static double percentValue(long value, long total) {
        return total <= 0 ? 0D : Math.round(value * 10_000D / total) / 100D;
    }

    private static int safeLimit(Integer value) {
        if (value == null) {
            return DEFAULT_REQUEUE_LIMIT;
        }
        return Math.min(MAX_REQUEUE_LIMIT, Math.max(1, value));
    }

    private static String normalizeReplayStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return STATUS_FAILED_OR_DEAD;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (STATUS_FAILED.equals(normalized) || STATUS_DEAD.equals(normalized)
                || STATUS_FAILED_OR_DEAD.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "audit outbox 重放状态不合法");
    }

    private static String normalizeReplayType(String value) {
        if (!StringUtils.hasText(value)) {
            return REPLAY_ALL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (REPLAY_GENERATION.equals(normalized) || REPLAY_PUBLISH.equals(normalized)
                || REPLAY_ALL.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "queued event 重放类型不合法");
    }

    private static String normalizeAlertType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "alertType 不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (ALERT_TYPES.contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "队列告警类型不合法");
    }

    private static String normalizeChannel(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "channel 不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (CHANNELS.contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "队列告警渠道不合法");
    }

    private static String normalizeTargetRef(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetRef 不能为空");
        }
        String trimmed = value.trim();
        String redacted = TestDesignSensitiveText.redact(trimmed);
        if (!trimmed.equals(redacted) || !TARGET_REF_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetRef 只能保存非密钥的渠道目标引用");
        }
        return trimmed;
    }

    private static String sanitizeReason(String value) {
        String redacted = TestDesignSensitiveText.redact(value);
        if (!StringUtils.hasText(redacted)) {
            return null;
        }
        String trimmed = redacted.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AuthUserPrincipal currentUser() {
        return authorizationService.currentUserPrincipal();
    }
}
