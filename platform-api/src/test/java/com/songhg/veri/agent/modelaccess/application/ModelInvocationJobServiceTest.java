package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventProperties;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobResult;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ModelInvocationJobServiceTest {

    private final InMemoryModelInvocationJobRepository repository = new InMemoryModelInvocationJobRepository();
    private final PlatformEventPublisher eventPublisher = mock(PlatformEventPublisher.class);
    private final AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
    private final ModelInvocationJobService service = new ModelInvocationJobService(
            properties(),
            repository,
            new ObjectMapper(),
            eventPublisher,
            new PlatformEventProperties(1, null),
            notificationService
    );

    @Test
    void cancelQueuedJobPublishesInAppNotification() {
        UUID userId = UUID.randomUUID();
        ModelInvocationJobRecord job = queuedJob(userId.toString());
        repository.save(job);

        ModelInvocationJobResult result = service.cancel(job.jobId());

        assertThat(result.status()).isEqualTo(ModelInvocationJobStatus.CANCELLED);
        verify(notificationService).notifyModelInvocationJobCancelled(
                repository.job(job.jobId()).orElseThrow()
        );
    }

    @Test
    void cancelRunningJobOnlyMarksCancelRequested() {
        UUID userId = UUID.randomUUID();
        ModelInvocationJobRecord job = new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.RUNNING,
                "{\"projectId\":\"project-alpha\"}",
                "portal-web",
                userId.toString(),
                "modelAccess:manage",
                "trc_model",
                Instant.now().minusSeconds(30),
                Instant.now().minusSeconds(10),
                null,
                null,
                null,
                null,
                null
        );
        repository.save(job);

        ModelInvocationJobResult result = service.cancel(job.jobId());

        assertThat(result.status()).isEqualTo(ModelInvocationJobStatus.RUNNING);
        assertThat(result.errorCode()).isEqualTo("CANCEL_REQUESTED");
        verify(notificationService, never()).notifyModelInvocationJobCancelled(job);
    }

    @Test
    void recoverPersistedJobsPublishesNotificationForStaleRunningJobs() {
        UUID userId = UUID.randomUUID();
        ModelInvocationJobRecord running = new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.RUNNING,
                "{\"projectId\":\"project-alpha\"}",
                "portal-web",
                userId.toString(),
                "modelAccess:manage",
                "trc_model_recovery",
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(7200),
                null,
                null,
                null,
                null,
                null
        );
        repository.save(running);

        service.recoverPersistedJobs();

        ModelInvocationJobRecord recovered = repository.job(running.jobId()).orElseThrow();
        assertThat(recovered.status()).isEqualTo(ModelInvocationJobStatus.FAILED);
        assertThat(recovered.errorCode()).isEqualTo("WORKER_RESTARTED");
        verify(notificationService).notifyModelInvocationJobFailed(recovered);
    }

    @Test
    void submitPersistsPrincipalRoleSnapshot() {
        ServicePrincipal principal = new ServicePrincipal("portal-web", UUID.randomUUID().toString(), List.of(
                "modelAccess:manage",
                "modelAccess:manage",
                "report:read"
        ));
        try (TraceContext.TraceScope ignored = TraceContext.open("trc_job_submit")) {
            ModelInvocationJobResult result = service.submit(new ModelInvocationCommand(
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
            ), principal);

            ModelInvocationJobRecord saved = repository.job(result.jobId()).orElseThrow();
            assertThat(saved.principalRoles()).isEqualTo("modelAccess:manage,report:read");
        }
    }

    private ModelInvocationJobRecord queuedJob(String delegatedUserId) {
        return new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.QUEUED,
                "{\"projectId\":\"project-alpha\",\"promptKey\":\"wp2.prompt\"}",
                "portal-web",
                delegatedUserId,
                "modelAccess:manage",
                "trc_model",
                Instant.now().minusSeconds(5),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private ModelAccessProperties properties() {
        return new ModelAccessProperties(
                "test-model-token",
                "test-local-model",
                4000,
                null,
                new BigDecimal("10.00"),
                256,
                "UTC",
                10000,
                1,
                1,
                1000,
                1000,
                new BigDecimal("0.8"),
                0,
                1,
                0,
                1,
                0,
                3_600_000,
                new BigDecimal("10.00"),
                "BLOCK",
                List.of()
        );
    }
}
