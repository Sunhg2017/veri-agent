package com.songhg.veri.agent.notification.application;

import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Composes user-facing in-app notifications for managed async tasks.
 *
 * <p>The service is intentionally small and aggregate-only: it receives already-sanitized control-plane summaries,
 * resolves the target user from persisted actor snapshots, and delegates durable delivery to the generic
 * {@link NotificationPublisher}. Additional workers can reuse the same entry points or add siblings here without
 * changing notification storage or HTTP contracts.</p>
 */
@Service
public class AsyncTaskNotificationService {

    private final NotificationPublisher notificationPublisher;

    public AsyncTaskNotificationService(NotificationPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }

    public void notifyReportReady(ReportExecutionReport report) {
        targetUserId(report.generatedBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "REPORT_READY",
                "报告已生成",
                "异步报告生成已完成，可前往报告诊断查看最新快照。",
                "#reports",
                Map.of(
                        "reportId", report.id(),
                        "projectId", report.projectId(),
                        "executionRunId", report.executionRunId(),
                        "status", report.status()
                )
        ));
    }

    public void notifyReportFailed(ReportExecutionReport report) {
        targetUserId(report.generatedBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "REPORT_FAILED",
                "报告生成失败",
                failureBody(report.failedCode()),
                "#reports",
                Map.of(
                        "reportId", report.id(),
                        "projectId", report.projectId(),
                        "executionRunId", report.executionRunId(),
                        "status", report.status(),
                        "failedCode", report.failedCode() == null ? "" : report.failedCode()
                )
        ));
    }

    private Optional<UUID> targetUserId(String generatedBy) {
        if (!StringUtils.hasText(generatedBy)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(generatedBy.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String failureBody(String failedCode) {
        if (!StringUtils.hasText(failedCode)) {
            return "异步报告生成未完成，请在报告诊断工作台查看失败详情。";
        }
        return "异步报告生成未完成，请在报告诊断工作台查看失败详情。错误码：" + failedCode.trim();
    }
}
