package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;
import com.songhg.veri.agent.documentinput.domain.WebhookSignatureStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

class DocumentWebhookAutoRetryServiceTest {

    @Test
    void scheduledRetryHonorsFeatureFlag() {
        DocumentInputRepository repository = mock(DocumentInputRepository.class);
        DocumentInputService documentInputService = mock(DocumentInputService.class);
        DocumentWebhookAutoRetryService service = new DocumentWebhookAutoRetryService(
                repository,
                documentInputService,
                properties(false, 3, 2),
                new DocumentInputMetrics(new SimpleMeterRegistry())
        );

        service.retryBySchedule();

        verify(repository, never()).retryableWebhookEvents(3, 2);
    }

    @Test
    void retriesConfiguredBatchAndContinuesAfterFailures() {
        DocumentInputRepository repository = mock(DocumentInputRepository.class);
        DocumentInputService documentInputService = mock(DocumentInputService.class);
        DocumentWebhookEvent succeeded = failedEvent("event-succeeded", 0);
        DocumentWebhookEvent failed = failedEvent("event-failed", 1);
        when(repository.retryableWebhookEvents(3, 2)).thenReturn(List.of(succeeded, failed));
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook payload 不是合法 JSON"))
                .when(documentInputService)
                .replayWebhookEvent(failed.id());
        DocumentWebhookAutoRetryService service = new DocumentWebhookAutoRetryService(
                repository,
                documentInputService,
                properties(true, 3, 2),
                new DocumentInputMetrics(new SimpleMeterRegistry())
        );

        DocumentWebhookAutoRetryService.AutoRetryResult result = service.retryNow();

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(documentInputService).replayWebhookEvent(succeeded.id());
        verify(documentInputService).replayWebhookEvent(failed.id());
    }

    private DocumentWebhookEvent failedEvent(String eventId, int retryCount) {
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        return new DocumentWebhookEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "custom-reqs",
                eventId,
                "idem-" + eventId,
                "requirement.created",
                "1.0",
                WebhookSignatureStatus.VALID,
                WebhookEventStatus.FAILED,
                "digest",
                "{\"projectId\":\"project-wp4\"}",
                "previous failure",
                retryCount,
                null,
                null,
                null,
                now,
                now
        );
    }

    private DocumentInputProperties properties(boolean autoRetryEnabled, int maxReplayAttempts, int batchSize) {
        return new DocumentInputProperties(
                "service-token",
                "default-secret",
                300,
                true,
                true,
                false,
                "wp4-document-requirement-parse",
                "INTERNAL",
                false,
                8000,
                16777216,
                10485760,
                "",
                30,
                20000,
                2,
                true,
                262144,
                100,
                maxReplayAttempts,
                autoRetryEnabled,
                batchSize,
                60,
                300,
                Map.of(),
                "",
                Map.of(),
                "",
                0,
                60,
                true,
                0,
                0,
                "LOCAL_COMMAND",
                "",
                15,
                2,
                2000,
                false,
                90,
                90
        );
    }
}
