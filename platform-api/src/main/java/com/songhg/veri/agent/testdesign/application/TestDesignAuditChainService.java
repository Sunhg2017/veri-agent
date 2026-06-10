package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditSummaryResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the WP5 task-level cross-WP audit-chain dashboard skeleton.
 *
 * <p>The service joins only aggregate counters from WP1 audit logs, WP2 invocation logs/jobs and WP3 published assets.
 * It intentionally does not return audit rows, outbox payloads, trace ids, invocation ids, sourceRef values, candidate
 * ids or asset ids; those remain in their owning domains and require separate scoped drill-down work.
 */
@Service
public class TestDesignAuditChainService {

    private static final String TONE_SUCCESS = "success";
    private static final String TONE_INFO = "info";
    private static final String TONE_WARNING = "warning";
    private static final String TONE_NEUTRAL = "neutral";

    private final TestDesignRepository repository;
    private final TestDesignTaskReportService taskReportService;

    public TestDesignAuditChainService(
            TestDesignRepository repository,
            TestDesignTaskReportService taskReportService
    ) {
        this.repository = repository;
        this.taskReportService = taskReportService;
    }

    /**
     * Returns an aggregate-only cross-WP audit-chain snapshot for one WP5 task.
     */
    @Transactional(readOnly = true)
    public TestDesignAuditChainResponse auditChain(UUID taskId) {
        TestDesignTask task = repository.task(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试设计任务不存在: " + taskId));
        TestDesignAuditSummaryResponse domainSummary = taskReportService.auditSummary(taskId);
        TestDesignAuditChainAggregate aggregate = repository.auditChainAggregate(taskId);
        TestDesignAuditChainPolicyResponse policy = TestDesignAuditChainPolicy.response();
        return new TestDesignAuditChainResponse(
                task.id(),
                task.projectId(),
                task.status(),
                policy.policyVersion(),
                policy.chainMode(),
                policy.eventSource(),
                aggregate.wp1AuditEventCount() > 0,
                aggregate.wp2InvocationCount() > 0,
                aggregate.wp3PublishedCaseCount() > 0,
                domainSummary.eventCount() > 0,
                policy.projectScopeRequired(),
                aggregate.wp2TraceSignalCount() > 0,
                policy.auditEventDetailExported(),
                policy.candidateIdentifierListExported(),
                policy.platformAuditIdentifierExported(),
                policy.traceIdValueExported(),
                policy.modelInvocationIdValueExported(),
                policy.publishIdentifierValueExported(),
                true,
                policy.crossWpAuditDashboardReady(),
                policy.auditOutboxReplayDashboardReady(),
                policy.aggregateOnly(),
                domainSummary.eventCount(),
                domainSummary.reviewRecordCount(),
                domainSummary.publishRecordCount(),
                domainSummary.issueCount(),
                domainSummary.noteCoverageCount(),
                metrics(domainSummary, aggregate),
                readiness(domainSummary, aggregate),
                Instant.now()
        );
    }

    private static List<TestDesignAuditChainMetricResponse> metrics(
            TestDesignAuditSummaryResponse domainSummary,
            TestDesignAuditChainAggregate aggregate
    ) {
        List<TestDesignAuditChainMetricResponse> metrics = new ArrayList<>();
        metrics.add(metric("wp5DomainEvents", "WP5 本域事件", domainSummary.eventCount(),
                domainSummary.eventCount() > 0 ? TONE_SUCCESS : TONE_WARNING));
        metrics.add(metric("wp1AuditEvents", "WP1 审计事件", aggregate.wp1AuditEventCount(),
                aggregate.wp1AuditEventCount() > 0 ? TONE_SUCCESS : TONE_WARNING));
        metrics.add(metric("wp1AuditFailures", "WP1 失败拒绝", aggregate.wp1AuditFailureCount()
                + aggregate.wp1AuditDeniedCount(), aggregate.wp1AuditFailureCount()
                + aggregate.wp1AuditDeniedCount() > 0 ? TONE_WARNING : TONE_SUCCESS));
        metrics.add(metric("wp2Invocations", "WP2 模型调用", aggregate.wp2InvocationCount(),
                aggregate.wp2InvocationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("wp2Fallbacks", "WP2 fallback", aggregate.wp2FallbackCount(),
                aggregate.wp2FallbackCount() > 0 ? TONE_WARNING : TONE_NEUTRAL));
        metrics.add(metric("wp2TraceSignals", "WP2 trace 信号", aggregate.wp2TraceSignalCount(),
                aggregate.wp2TraceSignalCount() > 0 ? TONE_SUCCESS : TONE_NEUTRAL));
        metrics.add(metric("wp2TokenTotal", "WP2 token 总量", aggregate.wp2InputTokenTotal()
                + aggregate.wp2OutputTokenTotal(), aggregate.wp2InvocationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("wp2LatencyMsTotal", "WP2 延迟总量", aggregate.wp2LatencyMsTotal(),
                aggregate.wp2InvocationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("wp2CostCents", "WP2 成本分", costCents(aggregate.wp2TotalCostText()),
                aggregate.wp2InvocationCount() > 0 ? TONE_INFO : TONE_NEUTRAL));
        metrics.add(metric("wp3PublishedCases", "WP3 发布用例", aggregate.wp3PublishedCaseCount(),
                aggregate.wp3PublishedCaseCount() > 0 ? TONE_SUCCESS : TONE_NEUTRAL));
        metrics.add(metric("wp3TraceLinks", "WP3 追踪链接", aggregate.wp3TraceLinkCount(),
                aggregate.wp3TraceLinkCount() > 0 ? TONE_SUCCESS : TONE_NEUTRAL));
        metrics.add(metric("auditOutboxOpen", "Audit outbox 待处理", aggregate.auditOutboxPendingCount()
                + aggregate.auditOutboxFailedCount() + aggregate.auditOutboxDeadCount(),
                aggregate.auditOutboxFailedCount() + aggregate.auditOutboxDeadCount() > 0 ? TONE_WARNING : TONE_NEUTRAL));
        return metrics;
    }

    private static List<TestDesignAuditChainReadinessResponse> readiness(
            TestDesignAuditSummaryResponse domainSummary,
            TestDesignAuditChainAggregate aggregate
    ) {
        TestDesignAuditChainPolicyResponse policy = TestDesignAuditChainPolicy.response();
        return List.of(
                readiness("wp5DomainEventsTracked", "WP5 本域事件聚合", domainSummary.eventCount() > 0,
                        "任务、评审和发布记录已纳入本域聚合摘要"),
                readiness("wp1AuditAggregateReady", "WP1 审计聚合", aggregate.wp1AuditEventCount() > 0,
                        "只读取 WP1 audit_log 计数和结果分布，不读取审计事件明细"),
                readiness("wp2InvocationAggregateReady", "WP2 调用引用聚合", aggregate.wp2InvocationCount() > 0,
                        "只读取 WP2 调用状态、token、成本、延迟和 trace 存在性"),
                readiness("wp3PublishAggregateReady", "WP3 发布引用聚合", aggregate.wp3PublishedCaseCount() > 0,
                        "只读取 WP3 AI 生成用例和需求追踪链接计数"),
                readiness("auditOutboxReplayDashboardReady", "Audit outbox 重放看板",
                        policy.auditOutboxReplayDashboardReady(),
                        "项目级运营台支持按项目 scope 将 FAILED/DEAD outbox 受限重新排队"),
                readiness("detailIdentifiersRedacted", "明细标识不导出", true,
                        "候选 ID、资产 ID、traceId、模型调用 ID、sourceRef 和审计 ID 原值不导出")
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

    private static long costCents(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return new BigDecimal(value.trim()).movePointRight(2).longValue();
    }
}
