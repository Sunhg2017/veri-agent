package com.songhg.veri.agent.notification.application;

import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.document.application.view.DocumentPublishResponse;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
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

    public void notifyDocumentImportSucceeded(DocumentImportRecord record) {
        targetUserId(record.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "ASYNC_TASK_COMPLETED",
                "文档导入已完成",
                "异步文档解析已完成，可前往文档导入工作台查看候选需求。",
                "#document-input",
                Map.of(
                        "importId", record.id(),
                        "projectId", record.projectId(),
                        "status", record.status().name(),
                        "sourceType", record.sourceType().name()
                )
        ));
    }

    public void notifyDocumentImportFailed(DocumentImportRecord record) {
        targetUserId(record.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "ASYNC_TASK_FAILED",
                "文档导入失败",
                asyncFailureBody("异步文档解析未完成，请在文档导入工作台查看失败详情。", record.errorMessage()),
                "#document-input",
                Map.of(
                        "importId", record.id(),
                        "projectId", record.projectId(),
                        "status", record.status().name(),
                        "sourceType", record.sourceType().name(),
                        "errorMessage", safeText(record.errorMessage())
                )
        ));
    }

    public void notifyDocumentPublishFinished(DocumentImportRecord record, DocumentPublishResponse response) {
        targetUserId(record.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                response.publishFailedCount() > 0 ? "ASYNC_TASK_FAILED" : "ASYNC_TASK_COMPLETED",
                response.publishFailedCount() > 0 ? "需求发布部分失败" : "需求发布已完成",
                response.publishFailedCount() > 0
                        ? asyncFailureBody("异步需求发布存在失败项，请在文档导入工作台查看发布记录。", record.errorMessage())
                        : "异步需求发布已完成，可前往文档导入工作台查看已落库需求。",
                "#document-input",
                Map.of(
                        "importId", record.id(),
                        "projectId", record.projectId(),
                        "status", record.status().name(),
                        "publishedCount", response.publishedCount(),
                        "publishFailedCount", response.publishFailedCount()
                )
        ));
    }

    public void notifyTestDesignGenerationSucceeded(TestDesignTask task) {
        targetUserId(task.requestedBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "ASYNC_TASK_COMPLETED",
                "用例生成已完成",
                "异步用例生成已完成，可前往用例设计工作台查看生成结果。",
                "#test-design",
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "status", task.status(),
                        "generatedCount", task.generatedCount()
                )
        ));
    }

    public void notifyTestDesignGenerationFailed(TestDesignTask task) {
        targetUserId(task.requestedBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "ASYNC_TASK_FAILED",
                "用例生成失败",
                asyncFailureBody("异步用例生成未完成，请在用例设计工作台查看失败详情。", task.errorMessage()),
                "#test-design",
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "status", task.status(),
                        "errorMessage", safeText(task.errorMessage())
                )
        ));
    }

    public void notifyTestDesignGenerationCancelled(TestDesignTask task) {
        targetUserId(task.requestedBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "SYSTEM_INFO",
                "用例生成已取消",
                "异步用例生成已取消，可前往用例设计工作台确认当前任务状态。",
                "#test-design",
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "status", task.status()
                )
        ));
    }

    public void notifyTestDesignPublishFinished(TestDesignTask task, TestDesignPublishResponse response) {
        publishTestDesignPublishNotification(
                task,
                response.failed() > 0,
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "createdCount", response.created(),
                        "failedCount", response.failed(),
                        "skippedCount", response.skipped()
                )
        );
    }

    /**
     * Recovery closes transient publish tasks after stale workers die, so the notification must be reconstructed from
     * the durable task/candidate snapshot instead of a single request-scoped publish response.
     */
    public void notifyTestDesignPublishRecoveryFinished(TestDesignTask task, int publishedCount, int failedCount) {
        publishTestDesignPublishNotification(
                task,
                failedCount > 0,
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "publishedCount", publishedCount,
                        "failedCount", failedCount,
                        "recovered", true
                )
        );
    }

    public void notifyApiAutomationGenerationTaskFinished(ApiAutomationGenerationTask task) {
        targetUserId(task.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                apiAutomationGenerationSuccess(task.status()) ? "ASYNC_TASK_COMPLETED" : "ASYNC_TASK_FAILED",
                apiAutomationGenerationSuccess(task.status()) ? "接口自动化生成已完成" : "接口自动化生成失败",
                apiAutomationGenerationSuccess(task.status())
                        ? "接口自动化生成已完成，可前往接口自动化工作台查看生成结果与脚本包。"
                        : asyncFailureBody("接口自动化生成未完成，请在接口自动化工作台查看失败详情。", task.errorSummary()),
                "#api-automation",
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "specId", task.specId(),
                        "status", task.status(),
                        "generationMode", safeText(task.generationMode()),
                        "apiCount", task.apiCount(),
                        "caseCount", task.caseCount(),
                        "modelInvocationId", safeText(task.modelInvocationId())
                )
        ));
    }

    public void notifyApiAutomationRunFinished(ApiAutomationRun run) {
        targetUserId(run.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                apiAutomationRunCanceled(run.status()) ? "SYSTEM_INFO"
                        : apiAutomationRunSuccess(run.status()) ? "ASYNC_TASK_COMPLETED" : "ASYNC_TASK_FAILED",
                apiAutomationRunCanceled(run.status()) ? "接口自动化运行已取消"
                        : apiAutomationRunSuccess(run.status()) ? "接口自动化运行已完成" : "接口自动化运行结束但存在异常",
                apiAutomationRunCanceled(run.status())
                        ? "接口自动化运行已取消，可前往接口自动化工作台确认当前运行状态。"
                        : apiAutomationRunSuccess(run.status())
                        ? "接口自动化运行已完成，可前往接口自动化工作台查看运行详情。"
                        : asyncFailureBody("接口自动化运行已结束，但存在失败、超时或阻断情况，请在接口自动化工作台查看详情。", run.errorSummary()),
                "#api-automation",
                Map.of(
                        "runId", run.id(),
                        "projectId", run.projectId(),
                        "bundleId", run.bundleId(),
                        "status", run.status(),
                        "runnerMode", safeText(run.runnerMode()),
                        "errorCode", safeText(run.errorCode())
                )
        ));
    }

    public void notifyTestDataTaskFinished(TestDataTask task) {
        targetUserId(task.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "SUCCEEDED".equals(task.status()) ? "ASYNC_TASK_COMPLETED" : "ASYNC_TASK_FAILED",
                "SUCCEEDED".equals(task.status()) ? "测试数据任务已完成" : "测试数据任务失败",
                "SUCCEEDED".equals(task.status())
                        ? "异步测试数据任务已完成，可前往测试数据工作台查看结果。"
                        : asyncFailureBody("异步测试数据任务未完成，请在测试数据工作台查看失败详情。", task.errorSummary()),
                "#test-data",
                Map.of(
                        "taskId", task.id(),
                        "projectId", task.projectId(),
                        "status", task.status(),
                        "taskType", task.taskType(),
                        "errorCode", safeText(task.errorCode())
                )
        ));
    }

    public void notifyExecutionRunFinished(ExecutionRun run) {
        targetUserId(run.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                executionSuccess(run.status()) ? "ASYNC_TASK_COMPLETED" : "ASYNC_TASK_FAILED",
                executionSuccess(run.status()) ? "执行运行已完成" : "执行运行结束但存在异常",
                executionSuccess(run.status())
                        ? "异步执行运行已完成，可前往执行工作台查看运行详情。"
                        : asyncFailureBody("异步执行运行已结束，但存在失败、超时或取消情况，请在执行工作台查看详情。", run.errorSummary()),
                "#execution",
                Map.of(
                        "runId", run.id(),
                        "projectId", run.projectId(),
                        "status", run.status(),
                        "planId", run.planId(),
                        "errorCode", safeText(run.errorCode())
                )
        ));
    }

    /**
     * UI/E2E runs are currently aggregate-only control-plane snapshots, so notifications point users back to the
     * workbench instead of embedding any runner-originated payload.
     */
    public void notifyUiE2eRunFinished(UiE2eRun run) {
        targetUserId(run.createdBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                uiE2eCanceled(run.status()) ? "SYSTEM_INFO" : executionSuccess(run.status()) ? "ASYNC_TASK_COMPLETED" : "ASYNC_TASK_FAILED",
                uiE2eCanceled(run.status()) ? "UI E2E 运行已取消" : executionSuccess(run.status()) ? "UI E2E 运行已完成" : "UI E2E 运行结束但存在异常",
                uiE2eCanceled(run.status())
                        ? "UI E2E 运行已取消，可前往 UI E2E 工作台确认当前运行状态。"
                        : executionSuccess(run.status())
                        ? "UI E2E 运行已完成，可前往 UI E2E 工作台查看运行详情。"
                        : asyncFailureBody("UI E2E 运行已结束，但存在失败、超时或阻断情况，请在 UI E2E 工作台查看详情。", run.failureSummary()),
                "#ui-e2e",
                Map.of(
                        "runId", run.id(),
                        "projectId", run.projectId(),
                        "sceneId", run.sceneId(),
                        "bundleId", run.bundleId(),
                        "status", run.status(),
                        "runnerMode", safeText(run.runnerMode()),
                        "failureCode", safeText(run.failureCode())
                )
        ));
    }

    public void notifyModelInvocationJobSucceeded(ModelInvocationJobRecord job) {
        targetUserId(job.delegatedUserId()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "ASYNC_TASK_COMPLETED",
                "模型调用已完成",
                "异步模型调用已完成，可前往模型接入工作台查看调用日志与成本。",
                "#model-access",
                Map.of(
                        "jobId", job.jobId(),
                        "status", job.status().name(),
                        "invocationId", job.invocationId() == null ? "" : job.invocationId(),
                        "actorService", safeText(job.actorService())
                )
        ));
    }

    public void notifyModelInvocationJobFailed(ModelInvocationJobRecord job) {
        targetUserId(job.delegatedUserId()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "ASYNC_TASK_FAILED",
                "模型调用失败",
                asyncFailureBody("异步模型调用未完成，请在模型接入工作台查看失败详情。", job.errorMessage()),
                "#model-access",
                Map.of(
                        "jobId", job.jobId(),
                        "status", job.status().name(),
                        "errorCode", safeText(job.errorCode()),
                        "actorService", safeText(job.actorService())
                )
        ));
    }

    public void notifyModelInvocationJobCancelled(ModelInvocationJobRecord job) {
        targetUserId(job.delegatedUserId()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                "SYSTEM_INFO",
                "模型调用已取消",
                "异步模型调用已取消，可前往模型接入工作台确认当前任务状态。",
                "#model-access",
                Map.of(
                        "jobId", job.jobId(),
                        "status", job.status().name(),
                        "errorCode", safeText(job.errorCode()),
                        "actorService", safeText(job.actorService())
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

    private String asyncFailureBody(String prefix, String detail) {
        if (!StringUtils.hasText(detail)) {
            return prefix;
        }
        return prefix + " 原因：" + detail.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean executionSuccess(String status) {
        return "SUCCEEDED".equals(status);
    }

    private boolean uiE2eCanceled(String status) {
        return "CANCELED".equals(status);
    }

    private void publishTestDesignPublishNotification(
            TestDesignTask task,
            boolean failed,
            Map<String, Object> metadata
    ) {
        targetUserId(task.requestedBy()).ifPresent(userId -> notificationPublisher.publishToUser(
                userId,
                failed ? "ASYNC_TASK_FAILED" : "ASYNC_TASK_COMPLETED",
                failed ? "用例发布部分失败" : "用例发布已完成",
                failed
                        ? "异步用例发布存在失败项，请在用例设计工作台查看发布记录。"
                        : "异步用例发布已完成，可前往用例设计工作台查看已发布结果。",
                "#test-design",
                metadata
        ));
    }

    private boolean apiAutomationGenerationSuccess(String status) {
        return "COMPLETED".equals(status);
    }

    private boolean apiAutomationRunSuccess(String status) {
        return "PASSED".equals(status);
    }

    private boolean apiAutomationRunCanceled(String status) {
        return "CANCELED".equals(status);
    }
}
