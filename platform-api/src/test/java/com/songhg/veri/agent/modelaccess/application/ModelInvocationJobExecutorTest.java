package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelInvocationJobExecutorTest {

    private final InMemoryModelInvocationJobRepository repository = new InMemoryModelInvocationJobRepository();
    private final ModelInvocationService invocationService = mock(ModelInvocationService.class);
    private final AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
    private final ModelInvocationJobExecutor executor = new ModelInvocationJobExecutor(
            invocationService,
            repository,
            new ObjectMapper(),
            notificationService
    );

    @Test
    void executePublishesSuccessNotificationAfterCompletingJob() {
        UUID invocationId = UUID.randomUUID();
        ModelInvocationJobRecord job = queuedJob();
        repository.save(job);
        when(invocationService.invoke(any(ModelInvocationCommand.class), any())).thenReturn(new ModelInvocationResult(
                invocationId,
                UUID.randomUUID(),
                "OpenAI Compatible",
                "gpt-4.1",
                3,
                false,
                "done",
                12,
                24,
                new BigDecimal("0.0123")
        ));

        executor.execute(job.jobId());

        ModelInvocationJobRecord completed = repository.job(job.jobId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(ModelInvocationJobStatus.SUCCEEDED);
        assertThat(completed.invocationId()).isEqualTo(invocationId);
        verify(notificationService).notifyModelInvocationJobSucceeded(completed);
    }

    @Test
    void executePublishesFailureNotificationAfterFailingJob() {
        ModelInvocationJobRecord job = queuedJob();
        repository.save(job);
        when(invocationService.invoke(any(ModelInvocationCommand.class), any()))
                .thenThrow(new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "provider down"));

        executor.execute(job.jobId());

        ModelInvocationJobRecord failed = repository.job(job.jobId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(ModelInvocationJobStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("MODEL_PROVIDER_UNAVAILABLE");
        verify(notificationService).notifyModelInvocationJobFailed(failed);
    }

    private ModelInvocationJobRecord queuedJob() {
        return new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.QUEUED,
                json(new ModelInvocationCommand(
                        "project-alpha",
                        "app-alpha",
                        "env-alpha",
                        "wp2.prompt",
                        Map.of("name", "demo"),
                        List.of(),
                        null,
                        "gpt-4.1",
                        false,
                        "INTERNAL",
                        "CHAT"
                )),
                "portal-web",
                UUID.randomUUID().toString(),
                "modelAccess:manage",
                "trc_model_job",
                Instant.now().minusSeconds(5),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String json(ModelInvocationCommand command) {
        try {
            return new ObjectMapper().writeValueAsString(command);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
