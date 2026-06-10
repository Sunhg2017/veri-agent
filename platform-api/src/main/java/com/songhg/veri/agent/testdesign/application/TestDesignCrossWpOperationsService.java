package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.RequeueTestDesignAuditOutboxCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCrossWpOperationsRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditOutboxOperationsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditOutboxRequeueResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpAuditDashboardResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpOperationsDashboardResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignScopePolicyResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpOperationsAggregate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Builds the unified WP5 cross-WP operations cockpit and performs bounded audit outbox requeue actions.
 *
 * <p>The cockpit is deliberately aggregate-only. It can prove WP1/WP2/WP3/WP5 links, scope coverage and replay
 * readiness, but it never exports audit rows, outbox payloads, trace ids, model invocation ids, sourceRef values or
 * asset identifiers.
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

    private final TestDesignRepository repository;
    private final TestDesignActorResolver actorResolver;
    private final AuthorizationService authorizationService;
    private final AuditLogWriter auditLogWriter;

    public TestDesignCrossWpOperationsService(
            TestDesignRepository repository,
            TestDesignActorResolver actorResolver,
            AuthorizationService authorizationService,
            AuditLogWriter auditLogWriter
    ) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.authorizationService = authorizationService;
        this.auditLogWriter = auditLogWriter;
    }

    /**
     * Returns a project-scoped or platform aggregate cross-WP operations snapshot.
     */
    @Transactional(readOnly = true)
    public TestDesignCrossWpOperationsDashboardResponse dashboard(TestDesignCrossWpOperationsRequest request) {
        String projectId = trimToNull(request == null ? null : request.getProjectId());
        String promptKey = trimToNull(request == null ? null : request.getPromptKey());
        TestDesignCrossWpOperationsAggregate aggregate = repository.crossWpOperationsAggregate(projectId, promptKey);
        TestDesignScopePolicyResponse scopePolicy = TestDesignScopePolicy.response();
        TestDesignAuditChainPolicyResponse auditPolicy = TestDesignAuditChainPolicy.response();
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
                metrics(aggregate),
                readiness(aggregate, scopePolicy, auditPolicy),
                scopePolicy.aggregateOnly() && auditPolicy.aggregateOnly(),
                false,
                Instant.now()
        );
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
                "WP5_CROSS_WP_AUDIT_OUTBOX_REQUEUE",
                "TEST_DESIGN_CROSS_WP_OPERATIONS",
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

    private static List<TestDesignAuditChainMetricResponse> metrics(TestDesignCrossWpOperationsAggregate aggregate) {
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
        return metrics;
    }

    private static List<TestDesignAuditChainReadinessResponse> readiness(
            TestDesignCrossWpOperationsAggregate aggregate,
            TestDesignScopePolicyResponse scopePolicy,
            TestDesignAuditChainPolicyResponse auditPolicy
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
                readiness("scopeMismatchClear", "作用域一致性",
                        aggregate.scopeMismatchCount() == 0,
                        "候选和发布记录项目 scope 必须与任务项目保持一致"),
                readiness("detailIdentifiersRedacted", "明细标识不导出",
                        scopePolicy.aggregateOnly()
                                && auditPolicy.aggregateOnly()
                                && !scopePolicy.candidateIdentifierListExported()
                                && !auditPolicy.traceIdValueExported()
                                && !auditPolicy.modelInvocationIdValueExported()
                                && !auditPolicy.publishIdentifierValueExported(),
                        "候选 ID、资产 ID、traceId、模型调用 ID、sourceRef、outbox payload 均不进入看板")
        );
    }

    private static TestDesignAuditChainMetricResponse metric(String code, String label, long count, String tone) {
        return new TestDesignAuditChainMetricResponse(code, label, count, tone);
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
        String normalized = value.trim().toUpperCase();
        if (STATUS_FAILED.equals(normalized) || STATUS_DEAD.equals(normalized)
                || STATUS_FAILED_OR_DEAD.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "audit outbox 重放状态不合法");
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
