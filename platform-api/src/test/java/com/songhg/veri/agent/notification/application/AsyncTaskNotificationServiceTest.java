package com.songhg.veri.agent.notification.application;

import com.songhg.veri.agent.document.application.view.DocumentPublishResponse;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AsyncTaskNotificationServiceTest {

    private final NotificationPublisher publisher = mock(NotificationPublisher.class);
    private final AsyncTaskNotificationService service = new AsyncTaskNotificationService(publisher);

    @Test
    void publishesExecutionCompletionNotificationForTerminalRun() {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        ExecutionRun run = new ExecutionRun(
                runId,
                planId,
                "project-alpha",
                "FAILED",
                "MANUAL",
                "request-key",
                null,
                1,
                "trc_run",
                "{}",
                "EXECUTION_RUN_FAILED",
                "timeout",
                userId.toString(),
                Instant.now().minusSeconds(60),
                Instant.now(),
                Instant.now().minusSeconds(120),
                Instant.now()
        );

        service.notifyExecutionRunFinished(run);

        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_FAILED"),
                eq("执行运行结束但存在异常"),
                eq("异步执行运行已结束，但存在失败、超时或取消情况，请在执行工作台查看详情。 原因：timeout"),
                eq("#execution"),
                argThat(metadata -> runId.equals(metadata.get("runId")) && "FAILED".equals(metadata.get("status")))
        );
    }

    @Test
    void skipsNotificationWhenAsyncTaskActorIsNotUuid() {
        TestDataTask task = new TestDataTask(
                UUID.randomUUID(),
                "project-alpha",
                null,
                "CLEANUP",
                "FAILED",
                "cleanup-001",
                "lease:run-001",
                1,
                "{}",
                "CLEANUP_TASK_NOT_ALLOWED",
                "cleanup disabled",
                "trc_td",
                "system",
                Instant.now().minusSeconds(10),
                Instant.now(),
                Instant.now().minusSeconds(20),
                Instant.now()
        );

        service.notifyTestDataTaskFinished(task);

        verifyNoInteractions(publisher);
    }

    @Test
    void publishesDocumentAndTestDesignNotifications() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        DocumentImportRecord record = new DocumentImportRecord(
                UUID.randomUUID(),
                "project-doc",
                null,
                "doc-source",
                DocumentSourceType.MARKDOWN,
                "REQ-1",
                null,
                "需求导入",
                DocumentImportStatus.SUCCEEDED,
                3,
                0,
                "[]",
                null,
                "digest",
                userId.toString(),
                now.minusSeconds(30),
                now
        );
        DocumentPublishResponse publishResponse = new DocumentPublishResponse(
                UUID.randomUUID(),
                record.id(),
                record.projectId(),
                null,
                record.sourceCode(),
                record.sourceType(),
                record.sourceRef(),
                record.sourceUrl(),
                record.title(),
                DocumentImportStatus.SUCCEEDED,
                false,
                3,
                2,
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                0,
                0,
                2,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                null,
                now.minusSeconds(30),
                now
        );
        TestDesignTask task = new TestDesignTask(
                UUID.randomUUID(),
                "project-td",
                "task",
                "SUCCEEDED",
                UUID.randomUUID().toString(),
                "SMOKE",
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                "RULE_TEMPLATE",
                1,
                2,
                1,
                0,
                null,
                userId.toString(),
                null,
                null,
                "digest",
                "{}",
                now.minusSeconds(60),
                now
        );
        TestDesignPublishResponse testDesignPublishResponse = new TestDesignPublishResponse(
                task.id(),
                task.projectId(),
                false,
                2,
                2,
                0,
                0,
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                List.of()
        );

        service.notifyDocumentImportSucceeded(record);
        service.notifyDocumentPublishFinished(record, publishResponse);
        service.notifyTestDesignGenerationSucceeded(task);
        service.notifyTestDesignPublishFinished(task, testDesignPublishResponse);

        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_COMPLETED"),
                eq("文档导入已完成"),
                eq("异步文档解析已完成，可前往文档导入工作台查看候选需求。"),
                eq("#document-input"),
                argThat(metadata -> record.id().equals(metadata.get("importId")))
        );
        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_COMPLETED"),
                eq("需求发布已完成"),
                eq("异步需求发布已完成，可前往文档导入工作台查看已落库需求。"),
                eq("#document-input"),
                argThat(metadata -> Long.valueOf(2).equals(metadata.get("publishedCount")))
        );
        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_COMPLETED"),
                eq("用例生成已完成"),
                eq("异步用例生成已完成，可前往用例设计工作台查看生成结果。"),
                eq("#test-design"),
                argThat(metadata -> Integer.valueOf(2).equals(metadata.get("generatedCount")))
        );
        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_COMPLETED"),
                eq("用例发布已完成"),
                eq("异步用例发布已完成，可前往用例设计工作台查看已发布结果。"),
                eq("#test-design"),
                argThat(metadata -> Integer.valueOf(2).equals(metadata.get("createdCount")))
        );
    }

    @Test
    void publishesModelInvocationJobTerminalNotifications() {
        UUID userId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        ModelInvocationJobRecord succeeded = new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.SUCCEEDED,
                "{}",
                "portal-web",
                userId.toString(),
                "modelAccess:manage",
                "trc_model",
                Instant.now().minusSeconds(20),
                Instant.now().minusSeconds(10),
                Instant.now(),
                invocationId,
                null,
                null,
                "{}"
        );
        ModelInvocationJobRecord failed = new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.FAILED,
                "{}",
                "portal-web",
                userId.toString(),
                "modelAccess:manage",
                "trc_model_failed",
                Instant.now().minusSeconds(20),
                Instant.now().minusSeconds(10),
                Instant.now(),
                null,
                "MODEL_PROVIDER_UNAVAILABLE",
                "provider down",
                null
        );
        ModelInvocationJobRecord cancelled = new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.CANCELLED,
                "{}",
                "portal-web",
                userId.toString(),
                "modelAccess:manage",
                "trc_model_cancel",
                Instant.now().minusSeconds(20),
                null,
                Instant.now(),
                null,
                "CANCELLED",
                "异步模型调用已取消",
                null
        );

        service.notifyModelInvocationJobSucceeded(succeeded);
        service.notifyModelInvocationJobFailed(failed);
        service.notifyModelInvocationJobCancelled(cancelled);

        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_COMPLETED"),
                eq("模型调用已完成"),
                eq("异步模型调用已完成，可前往模型接入工作台查看调用日志与成本。"),
                eq("#model-access"),
                argThat(metadata -> invocationId.equals(metadata.get("invocationId")))
        );
        verify(publisher).publishToUser(
                eq(userId),
                eq("ASYNC_TASK_FAILED"),
                eq("模型调用失败"),
                eq("异步模型调用未完成，请在模型接入工作台查看失败详情。 原因：provider down"),
                eq("#model-access"),
                argThat(metadata -> "MODEL_PROVIDER_UNAVAILABLE".equals(metadata.get("errorCode")))
        );
        verify(publisher).publishToUser(
                eq(userId),
                eq("SYSTEM_INFO"),
                eq("模型调用已取消"),
                eq("异步模型调用已取消，可前往模型接入工作台确认当前任务状态。"),
                eq("#model-access"),
                argThat(metadata -> "CANCELLED".equals(metadata.get("errorCode")))
        );
    }
}
