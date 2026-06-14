package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionWebhookTriggerResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionTrigger;
import com.songhg.veri.agent.execution.domain.ExecutionTriggerEvent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionTriggerServiceTest {

    private static final String WEBHOOK_SECRET_REF = "secret://wp9/webhook";
    private static final String WEBHOOK_SECRET = "wp9-webhook-signing-secret";

    private final ExecutionRepository repository = mock(ExecutionRepository.class);
    private final ExecutionRunService runService = mock(ExecutionRunService.class);
    private final ExecutionActorResolver actorResolver = mock(ExecutionActorResolver.class);
    private final ExecutionPlatformContextClient contextClient = mock(ExecutionPlatformContextClient.class);
    private final SecretProvider secretProvider = mock(SecretProvider.class);
    private final ExecutionTriggerService service = new ExecutionTriggerService(
            repository,
            runService,
            actorResolver,
            contextClient,
            new StaticObjectProvider<>(List.of(secretProvider)),
            new ObjectMapper(),
            new ExecutionProperties(
                    false,
                    true,
                    false,
                    300,
                    60,
                    5000,
                    30000,
                    "wp9-test-worker",
                    4,
                    2,
                    4,
                    180,
                    1800,
                    50
            )
    );

    @Test
    void signedWebhookCachesSecretAndPersistsOnlyDigestMetadata() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID triggerId = UUID.randomUUID();
        UUID firstRunId = UUID.randomUUID();
        UUID secondRunId = UUID.randomUUID();
        ExecutionTrigger trigger = webhookTrigger(triggerId, planId, "ENABLED");
        when(repository.trigger(triggerId)).thenReturn(Optional.of(trigger));
        when(repository.plan(planId)).thenReturn(Optional.of(plan(planId, "project-alpha")));
        when(repository.triggerEventBySource(eq(triggerId), any())).thenReturn(Optional.empty());
        when(repository.insertTriggerEvent(any())).thenReturn(true);
        when(secretProvider.resolve(eq(WEBHOOK_SECRET_REF), any(SecretResolveContext.class)))
                .thenReturn(Optional.of(new ResolvedSecret(WEBHOOK_SECRET_REF, WEBHOOK_SECRET, "test", "v1")));
        when(runService.triggerExternalRun(eq(planId), eq("WEBHOOK"), any(), eq("evt-cache-1"), any()))
                .thenReturn(runResponse(firstRunId, planId, "evt-cache-1"));
        when(runService.triggerExternalRun(eq(planId), eq("WEBHOOK"), any(), eq("evt-cache-2"), any()))
                .thenReturn(runResponse(secondRunId, planId, "evt-cache-2"));

        String firstPayload = "{\"build\":\"cache-1\",\"token\":\"must-not-store\"}";
        String firstTimestamp = String.valueOf(Instant.now().getEpochSecond());
        ExecutionWebhookTriggerResponse first = service.receiveWebhook(
                triggerId,
                firstPayload,
                firstTimestamp,
                signature(firstTimestamp, "evt-cache-1", firstPayload),
                "evt-cache-1"
        );
        String secondPayload = "{\"build\":\"cache-2\",\"signature\":\"must-not-store\"}";
        String secondTimestamp = String.valueOf(Instant.now().getEpochSecond());
        ExecutionWebhookTriggerResponse second = service.receiveWebhook(
                triggerId,
                secondPayload,
                secondTimestamp,
                signature(secondTimestamp, "evt-cache-2", secondPayload),
                "evt-cache-2"
        );

        assertThat(first.runId()).isEqualTo(firstRunId);
        assertThat(first.idempotentReplay()).isFalse();
        assertThat(second.runId()).isEqualTo(secondRunId);
        assertThat(second.idempotentReplay()).isFalse();
        verify(secretProvider).resolve(eq(WEBHOOK_SECRET_REF), any(SecretResolveContext.class));

        ArgumentCaptor<ExecutionTriggerEvent> eventCaptor = ArgumentCaptor.forClass(ExecutionTriggerEvent.class);
        verify(repository, org.mockito.Mockito.times(2)).updateTriggerEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .filteredOn(event -> "ACCEPTED".equals(event.status()))
                .allSatisfy(event -> {
                    assertThat(event.requestDigest()).hasSize(64);
                    assertThat(event.errorSummary()).isNull();
                    assertThat(event.toString())
                            .doesNotContain(firstPayload)
                            .doesNotContain(secondPayload)
                            .doesNotContain(WEBHOOK_SECRET)
                            .doesNotContain(WEBHOOK_SECRET_REF);
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> summaryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(runService, org.mockito.Mockito.times(2))
                .triggerExternalRun(eq(planId), eq("WEBHOOK"), any(), any(), summaryCaptor.capture());
        assertThat(summaryCaptor.getAllValues()).allSatisfy(summary -> {
            assertThat(summary).containsEntry("webhookPayloadStored", false);
            assertThat(summary).containsEntry("webhookSignatureStored", false);
            assertThat(summary.toString())
                    .doesNotContain(firstPayload)
                    .doesNotContain(secondPayload)
                    .doesNotContain(WEBHOOK_SECRET)
                    .doesNotContain(WEBHOOK_SECRET_REF);
        });
    }

    @Test
    void invalidSignatureRecordsRejectedEventWithoutResolvingSecretWhenSignatureMissing() {
        UUID planId = UUID.randomUUID();
        UUID triggerId = UUID.randomUUID();
        ExecutionTrigger trigger = webhookTrigger(triggerId, planId, "ENABLED");
        when(repository.trigger(triggerId)).thenReturn(Optional.of(trigger));
        when(repository.plan(planId)).thenReturn(Optional.of(plan(planId, "project-alpha")));
        when(repository.triggerEventBySource(triggerId, "evt-missing-signature")).thenReturn(Optional.empty());
        when(repository.insertTriggerEvent(any())).thenReturn(true);

        assertThatThrownBy(() -> service.receiveWebhook(
                triggerId,
                "{\"build\":\"bad\"}",
                String.valueOf(Instant.now().getEpochSecond()),
                "",
                "evt-missing-signature"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        ArgumentCaptor<ExecutionTriggerEvent> eventCaptor = ArgumentCaptor.forClass(ExecutionTriggerEvent.class);
        verify(repository).updateTriggerEvent(eventCaptor.capture());
        ExecutionTriggerEvent rejected = eventCaptor.getValue();
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.errorCode()).isEqualTo("EXECUTION_TRIGGER_SIGNATURE_INVALID");
        assertThat(rejected.errorSummary()).isEqualTo("Webhook signature is missing, expired, or invalid");
        assertThat(rejected.requestDigest()).hasSize(64);
        assertThat(rejected.toString())
                .doesNotContain("{\"build\":\"bad\"}")
                .doesNotContain(WEBHOOK_SECRET)
                .doesNotContain(WEBHOOK_SECRET_REF);
        verifyNoInteractions(secretProvider, runService);
        verify(contextClient).writeAuditEvent(
                eq("execution.trigger.rejected"),
                eq("EXECUTION_TRIGGER_EVENT"),
                eq(rejected.id().toString()),
                eq("project-alpha"),
                eq("REJECTED"),
                any()
        );
    }

    @Test
    void duplicateWebhookReplaysExistingRunWithoutCallingRunner() {
        UUID planId = UUID.randomUUID();
        UUID triggerId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ExecutionTrigger trigger = webhookTrigger(triggerId, planId, "ENABLED");
        ExecutionTriggerEvent accepted = new ExecutionTriggerEvent(
                UUID.randomUUID(),
                triggerId,
                "evt-duplicate",
                "0".repeat(64),
                "ACCEPTED",
                runId,
                Instant.now().minusSeconds(30),
                null,
                null,
                "trc_existing"
        );
        when(repository.trigger(triggerId)).thenReturn(Optional.of(trigger));
        when(repository.triggerEventBySource(triggerId, "evt-duplicate")).thenReturn(Optional.of(accepted));

        ExecutionWebhookTriggerResponse response = service.receiveWebhook(
                triggerId,
                "{\"build\":\"duplicate\"}",
                String.valueOf(Instant.now().getEpochSecond()),
                "not-used-for-duplicate",
                "evt-duplicate"
        );

        assertThat(response.runId()).isEqualTo(runId);
        assertThat(response.idempotentReplay()).isTrue();
        assertThat(response.event().status()).isEqualTo("DUPLICATE");
        verify(repository).updateTriggerEvent(any(ExecutionTriggerEvent.class));
        verify(repository, never()).insertTriggerEvent(any());
        verifyNoInteractions(secretProvider, runService, contextClient);
    }

    private ExecutionRunDetailResponse runResponse(UUID runId, UUID planId, String sourceEventId) {
        return new ExecutionRunDetailResponse(
                runId,
                planId,
                "project-alpha",
                "QUEUED",
                "WEBHOOK",
                "webhook-request",
                sourceEventId,
                1,
                "trc_run",
                Map.of(),
                null,
                null,
                List.of(),
                false,
                "system",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private ExecutionPlan plan(UUID id, String projectId) {
        Instant now = Instant.now();
        return new ExecutionPlan(
                id,
                projectId,
                "Release smoke",
                "READY",
                "staging",
                "{}",
                "dag-digest",
                "test plan",
                "tester",
                "tester",
                null,
                now,
                now
        );
    }

    private ExecutionTrigger webhookTrigger(UUID id, UUID planId, String status) {
        Instant now = Instant.now();
        return new ExecutionTrigger(
                id,
                planId,
                "WEBHOOK",
                status,
                "config-digest",
                "{\"source\":\"github-actions\",\"type\":\"WEBHOOK\",\"rawPayloadStored\":false,\"secretStored\":false}",
                WEBHOOK_SECRET_REF,
                "secret-ref-digest",
                null,
                null,
                "tester",
                "tester",
                now,
                now
        );
    }

    private String signature(String timestamp, String eventId, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(String.join(".", timestamp, eventId, payload)
                .getBytes(StandardCharsets.UTF_8)));
    }

    private record StaticObjectProvider<T>(List<T> values) implements ObjectProvider<T> {

        @Override
        public T getObject(Object... args) {
            return getObject();
        }

        @Override
        public T getIfAvailable() {
            return values.isEmpty() ? null : values.get(0);
        }

        @Override
        public T getIfUnique() {
            return getIfAvailable();
        }

        @Override
        public T getObject() {
            T value = getIfAvailable();
            if (value == null) {
                throw new IllegalStateException("No object available");
            }
            return value;
        }

        @Override
        public java.util.stream.Stream<T> stream() {
            return values.stream();
        }

        @Override
        public java.util.stream.Stream<T> orderedStream() {
            return values.stream();
        }
    }
}
