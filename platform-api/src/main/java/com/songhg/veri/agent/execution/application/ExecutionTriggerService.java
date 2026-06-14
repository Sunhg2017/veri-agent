package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.command.CreateExecutionTriggerCommand;
import com.songhg.veri.agent.execution.application.command.UpdateExecutionTriggerCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerEventPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerEventQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerQuery;
import com.songhg.veri.agent.execution.application.view.ExecutionCronScanResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerDryRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerEventResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionWebhookTriggerResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionTrigger;
import com.songhg.veri.agent.execution.domain.ExecutionTriggerEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ExecutionTriggerService {

    private static final Set<String> TRIGGER_TYPES = Set.of("WEBHOOK", "CRON");
    private static final Set<String> TRIGGER_STATUSES = Set.of("DISABLED", "ENABLED", "PAUSED");
    private static final Set<String> EVENT_STATUSES = Set.of("RECEIVED", "ACCEPTED", "REJECTED", "DUPLICATE", "FAILED");
    private static final Set<String> SAFE_CONFIG_KEYS = Set.of(
            "source",
            "eventType",
            "eventVersion",
            "cron",
            "timezone",
            "description",
            "filters",
            "labels"
    );
    private static final Set<String> FORBIDDEN_CONFIG_KEY_PARTS = Set.of(
            "secret", "token", "password", "authorization", "payload", "body", "header", "cookie"
    );
    private static final Pattern SECRET_REF_PATTERN =
            Pattern.compile("^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]{1,247}$");
    private static final int MAX_CONFIG_TEXT_LENGTH = 256;
    private static final int MAX_CONFIG_LIST_ITEMS = 20;
    private static final String DEFAULT_CRON_TIMEZONE = "UTC";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ExecutionRepository repository;
    private final ExecutionRunService runService;
    private final ExecutionActorResolver actorResolver;
    private final ExecutionPlatformContextClient contextClient;
    private final List<SecretProvider> secretProviders;
    private final ObjectMapper objectMapper;
    private final ExecutionProperties properties;
    private final ConcurrentMap<String, CachedSecret> webhookSecretCache = new ConcurrentHashMap<>();

    public ExecutionTriggerService(
            ExecutionRepository repository,
            ExecutionRunService runService,
            ExecutionActorResolver actorResolver,
            ExecutionPlatformContextClient contextClient,
            ObjectProvider<SecretProvider> secretProviders,
            ObjectMapper objectMapper,
            ExecutionProperties properties
    ) {
        this.repository = repository;
        this.runService = runService;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.secretProviders = secretProviders == null ? List.of() : secretProviders.orderedStream().toList();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionTriggerResponse createTrigger(UUID planId, CreateExecutionTriggerCommand command) {
        ExecutionPlan plan = requirePlan(planId);
        String triggerType = normalizeTriggerType(command == null ? null : command.triggerType());
        String status = normalizeStatus(command == null ? null : command.status(), "DISABLED");
        Map<String, Object> configSummary = sanitizedConfig(command == null ? null : command.config(), triggerType);
        String secretRef = normalizedSecretRef(command == null ? null : command.secretRef(), triggerType, status);
        ensureTriggerGloballyAllowed(triggerType, status);
        Instant now = Instant.now();
        Instant nextFireAt = initialNextFireAt(triggerType, configSummary, command == null ? null : command.nextFireAt(), now);
        String actor = actorResolver.currentActor();
        ExecutionTrigger trigger = new ExecutionTrigger(
                UUID.randomUUID(),
                plan.id(),
                triggerType,
                status,
                sha256(json(configSummary)),
                json(configSummary),
                secretRef,
                StringUtils.hasText(secretRef) ? sha256(secretRef) : null,
                nextFireAt,
                null,
                actor,
                actor,
                now,
                now
        );
        repository.insertTrigger(trigger);
        auditTrigger(trigger, "execution.trigger.created", "SUCCESS", triggerAuditSummary(trigger));
        return toResponse(trigger);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionTriggerResponse> triggers(UUID planId, ExecutionTriggerPageRequest request) {
        requirePlan(planId);
        ExecutionTriggerQuery query = normalizeTriggerQuery(request.toQuery(planId));
        List<ExecutionTriggerResponse> items = repository.triggers(query)
                .stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countTriggers(query));
    }

    @Transactional(readOnly = true)
    public ExecutionTriggerResponse trigger(UUID id) {
        return toResponse(requireTrigger(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionTriggerResponse updateTrigger(UUID id, UpdateExecutionTriggerCommand command) {
        ExecutionTrigger existing = requireTrigger(id);
        String status = normalizeStatus(command == null ? null : command.status(), existing.status());
        Map<String, Object> configSummary = command == null || command.config() == null
                ? readMap(existing.configSummaryJson())
                : sanitizedConfig(command.config(), existing.triggerType());
        String secretRef = command == null || command.secretRef() == null
                ? existing.secretRef()
                : normalizedSecretRef(command.secretRef(), existing.triggerType(), status);
        ensureRequiredSecretRef(existing.triggerType(), status, secretRef);
        ensureTriggerGloballyAllowed(existing.triggerType(), status);
        Instant now = Instant.now();
        Instant nextFireAt = updatedNextFireAt(
                existing,
                configSummary,
                command == null ? null : command.nextFireAt(),
                command != null && command.config() != null,
                status,
                now
        );
        ExecutionTrigger updated = new ExecutionTrigger(
                existing.id(),
                existing.planId(),
                existing.triggerType(),
                status,
                sha256(json(configSummary)),
                json(configSummary),
                secretRef,
                StringUtils.hasText(secretRef) ? sha256(secretRef) : null,
                nextFireAt,
                existing.lastFireAt(),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.createdAt(),
                now
        );
        repository.updateTrigger(updated);
        auditTrigger(updated, "execution.trigger.updated", "SUCCESS", triggerAuditSummary(updated));
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public ExecutionTriggerDryRunResponse dryRun(UUID id) {
        ExecutionTrigger trigger = requireTrigger(id);
        boolean globalEnabled = globalEnabled(trigger.triggerType());
        boolean valid = "ENABLED".equals(trigger.status()) && globalEnabled
                && (!"WEBHOOK".equals(trigger.triggerType()) || StringUtils.hasText(trigger.secretRef()));
        return new ExecutionTriggerDryRunResponse(
                trigger.id(),
                trigger.triggerType(),
                valid,
                globalEnabled,
                false,
                Map.of(
                        "status", trigger.status(),
                        "secretRefConfigured", StringUtils.hasText(trigger.secretRef()),
                        "configDigest", trigger.configDigest(),
                        "webhookSignatureRequired", "WEBHOOK".equals(trigger.triggerType()),
                        "cronScannerEnabled", properties.cronEnabled()
                )
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionTriggerEventResponse> events(UUID triggerId, ExecutionTriggerEventPageRequest request) {
        requireTrigger(triggerId);
        ExecutionTriggerEventQuery query = normalizeEventQuery(request.toQuery(triggerId));
        List<ExecutionTriggerEventResponse> items = repository.triggerEvents(query)
                .stream()
                .map(this::toEventResponse)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countTriggerEvents(query));
    }

    /**
     * Materializes due CRON trigger metadata into execution runs using trigger-event and run idempotency keys.
     *
     * <p>The scanner intentionally does not perform missed-fire catch-up. Each due `nextFireAt` yields at most one
     * `sourceEventId`; after either an accepted run or a recorded failure, the trigger advances to the next fire after
     * the scan timestamp. This prevents one bad plan or invalid legacy cron row from being retried every scheduler tick
     * while keeping failed attempts observable in `execution_trigger_event`.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionCronScanResponse scanDueCronTriggers(int limit) {
        Instant now = Instant.now();
        if (!properties.cronEnabled()) {
            return new ExecutionCronScanResponse(false, 0, 0, 0, now);
        }
        int scanLimit = limit <= 0 ? properties.effectiveSchedulerTickBatchSize() : limit;
        int scannedTriggerCount = 0;
        int triggeredRunCount = 0;
        int failedTriggerCount = 0;
        for (ExecutionTrigger trigger : repository.dueCronTriggers(now, scanLimit)) {
            scannedTriggerCount++;
            CronTriggerOutcome outcome = fireDueCronTrigger(trigger, now);
            if (outcome.triggered()) {
                triggeredRunCount++;
            }
            if (outcome.failed()) {
                failedTriggerCount++;
            }
        }
        return new ExecutionCronScanResponse(true, scannedTriggerCount, triggeredRunCount, failedTriggerCount, now);
    }

    /**
     * Accepts a signed external webhook and creates exactly one execution run per source event.
     *
     * <p>Disabled triggers, failed signatures and duplicate events are persisted as trigger-event evidence with only
     * request digests and bounded error summaries. Raw payloads, signature values and secret references are never
     * copied into event rows or responses.</p>
     */
    public ExecutionWebhookTriggerResponse receiveWebhook(
            UUID triggerId,
            String rawPayload,
            String timestamp,
            String signature,
            String sourceEventId
    ) {
        ExecutionTrigger trigger = requireTrigger(triggerId);
        String eventId = boundedRequiredText(sourceEventId, 256, "EXECUTION_TRIGGER_SOURCE_EVENT_REQUIRED");
        String requestDigest = requestDigest(timestamp, eventId, rawPayload);
        Instant now = Instant.now();
        Optional<ExecutionTriggerEvent> existing = repository.triggerEventBySource(trigger.id(), eventId);
        if (existing.isPresent() && existing.get().runId() != null) {
            ExecutionTriggerEvent duplicate = duplicateEvent(existing.get(), requestDigest, now);
            repository.updateTriggerEvent(duplicate);
            return new ExecutionWebhookTriggerResponse(toEventResponse(duplicate), duplicate.runId(), true);
        }
        // A rejected or failed event with no run is evidence of a previous rejected attempt, not a completed trigger.
        // Reusing that row lets a correctly signed retry recover while preserving one row per source event id.
        ExecutionTriggerEvent received = existing
                .map(event -> retryReceivedEvent(event, requestDigest, now))
                .orElseGet(() -> new ExecutionTriggerEvent(
                        UUID.randomUUID(),
                        trigger.id(),
                        eventId,
                        requestDigest,
                        "RECEIVED",
                        null,
                        now,
                        null,
                        null,
                        TraceContext.getOrCreateTraceId()
                ));
        if (existing.isPresent()) {
            repository.updateTriggerEvent(received);
        } else if (!repository.insertTriggerEvent(received)) {
            ExecutionTriggerEvent replay = repository.triggerEventBySource(trigger.id(), eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "EXECUTION_DUPLICATE_TRIGGER"));
            if (replay.runId() != null) {
                return new ExecutionWebhookTriggerResponse(toEventResponse(replay), replay.runId(), true);
            }
            received = retryReceivedEvent(replay, requestDigest, now);
            repository.updateTriggerEvent(received);
        }
        if (!properties.webhookEnabled() || !"ENABLED".equals(trigger.status())) {
            ExecutionTriggerEvent rejected = rejectedEvent(received, "EXECUTION_TRIGGER_DISABLED", "Webhook trigger is disabled");
            repository.updateTriggerEvent(rejected);
            auditTriggerEvent(trigger, rejected, "execution.trigger.rejected", "REJECTED");
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_TRIGGER_DISABLED");
        }
        if (!"WEBHOOK".equals(trigger.triggerType())) {
            ExecutionTriggerEvent rejected = rejectedEvent(received, "EXECUTION_TRIGGER_TYPE_INVALID", "Trigger is not webhook");
            repository.updateTriggerEvent(rejected);
            auditTriggerEvent(trigger, rejected, "execution.trigger.rejected", "REJECTED");
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        try {
            if (!validWebhookSignature(trigger, rawPayload, timestamp, signature, eventId)) {
                ExecutionTriggerEvent rejected = rejectedEvent(
                        received,
                        "EXECUTION_TRIGGER_SIGNATURE_INVALID",
                        "Webhook signature is missing, expired, or invalid"
                );
                repository.updateTriggerEvent(rejected);
                auditTriggerEvent(trigger, rejected, "execution.trigger.rejected", "REJECTED");
                throw new BusinessException(ErrorCode.FORBIDDEN, "EXECUTION_TRIGGER_SIGNATURE_INVALID");
            }
            String runRequestKey = webhookRequestKey(trigger.id(), eventId);
            ExecutionRunDetailResponse run = runService.triggerExternalRun(
                    trigger.planId(),
                    "WEBHOOK",
                    runRequestKey,
                    eventId,
                    Map.of(
                            "triggerId", trigger.id().toString(),
                            "triggerEventSource", "WEBHOOK",
                            "sourceEventDigest", sha256(eventId),
                            "requestDigest", requestDigest,
                            "webhookPayloadStored", false,
                            "webhookSignatureStored", false
                    )
            );
            ExecutionTriggerEvent accepted = new ExecutionTriggerEvent(
                    received.id(),
                    received.triggerId(),
                    received.sourceEventId(),
                    received.requestDigest(),
                    "ACCEPTED",
                    run.id(),
                    received.receivedAt(),
                    null,
                    null,
                    received.traceId()
            );
            repository.updateTriggerEvent(accepted);
            markTriggerFired(trigger, now);
            auditTriggerEvent(trigger, accepted, "execution.trigger.fired", "SUCCESS");
            return new ExecutionWebhookTriggerResponse(toEventResponse(accepted), run.id(), run.idempotentReplay());
        } catch (BusinessException exception) {
            if ("EXECUTION_TRIGGER_SIGNATURE_INVALID".equals(exception.getMessage())) {
                throw exception;
            }
            ExecutionTriggerEvent failed = failedEvent(received, exception.getMessage(), "Webhook trigger failed");
            repository.updateTriggerEvent(new ExecutionTriggerEvent(
                    failed.id(),
                    failed.triggerId(),
                    failed.sourceEventId(),
                    failed.requestDigest(),
                    failed.status(),
                    null,
                    failed.receivedAt(),
                    failed.errorCode(),
                    failed.errorSummary(),
                    failed.traceId()
            ));
            auditTriggerEvent(trigger, failed, "execution.trigger.failed", "FAILED");
            throw exception;
        }
    }

    public String triggerProjectScopeId(UUID id) {
        return repository.triggerProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行触发器不存在"));
    }

    private void markTriggerFired(ExecutionTrigger trigger, Instant now) {
        repository.updateTrigger(new ExecutionTrigger(
                trigger.id(),
                trigger.planId(),
                trigger.triggerType(),
                trigger.status(),
                trigger.configDigest(),
                trigger.configSummaryJson(),
                trigger.secretRef(),
                trigger.secretRefDigest(),
                trigger.nextFireAt(),
                now,
                trigger.createdBy(),
                trigger.updatedBy(),
                trigger.createdAt(),
                now
        ));
    }

    private CronTriggerOutcome fireDueCronTrigger(ExecutionTrigger trigger, Instant scanTime) {
        Instant fireAt = trigger.nextFireAt() == null ? scanTime : trigger.nextFireAt();
        String sourceEventId = cronSourceEventId(trigger.id(), fireAt);
        String requestDigest = cronRequestDigest(trigger, fireAt);
        Optional<ExecutionTriggerEvent> replay = repository.triggerEventBySource(trigger.id(), sourceEventId);
        if (replay.isPresent() && replay.get().runId() != null) {
            Instant nextFireAt = safeNextCronFireAt(trigger, scanTime);
            if (nextFireAt == null) {
                pauseCronTrigger(trigger, scanTime);
                return new CronTriggerOutcome(false, true);
            }
            advanceCronTrigger(trigger, scanTime, false, nextFireAt);
            return new CronTriggerOutcome(true, false);
        }
        ExecutionTriggerEvent received = receivedCronEvent(trigger, replay, sourceEventId, requestDigest, scanTime);
        ExecutionRunDetailResponse run;
        Instant nextFireAt = null;
        try {
            // Freeze the next schedule before run creation; if a legacy row is unparseable, no orphan run is created.
            nextFireAt = nextCronFireAt(readMap(trigger.configSummaryJson()), scanTime);
            run = runService.triggerExternalRun(
                    trigger.planId(),
                    "CRON",
                    cronRequestKey(trigger.id(), sourceEventId),
                    sourceEventId,
                    Map.of(
                            "triggerId", trigger.id().toString(),
                            "triggerEventSource", "CRON",
                            "sourceEventDigest", sha256(sourceEventId),
                            "scheduledFireAt", fireAt.toString(),
                            "configDigest", trigger.configDigest(),
                            "cronPayloadStored", false
                    )
            );
        } catch (RuntimeException exception) {
            ExecutionTriggerEvent failed = failedEvent(
                    received,
                    cronErrorCode(exception),
                    sanitizedTriggerErrorSummary(exception.getMessage())
            );
            repository.updateTriggerEvent(failed);
            if (nextFireAt == null) {
                pauseCronTrigger(trigger, scanTime);
            } else {
                advanceCronTrigger(trigger, scanTime, false, nextFireAt);
            }
            auditTriggerEvent(trigger, failed, "execution.trigger.failed", "FAILED");
            return new CronTriggerOutcome(false, true);
        }
        ExecutionTriggerEvent accepted = acceptedEvent(received, run.id());
        repository.updateTriggerEvent(accepted);
        advanceCronTrigger(trigger, scanTime, true, nextFireAt);
        auditTriggerEvent(trigger, accepted, "execution.trigger.fired", "SUCCESS");
        return new CronTriggerOutcome(true, false);
    }

    private ExecutionTriggerEvent receivedCronEvent(
            ExecutionTrigger trigger,
            Optional<ExecutionTriggerEvent> existing,
            String sourceEventId,
            String requestDigest,
            Instant now
    ) {
        ExecutionTriggerEvent received = existing
                .map(event -> retryReceivedEvent(event, requestDigest, now))
                .orElseGet(() -> new ExecutionTriggerEvent(
                        UUID.randomUUID(),
                        trigger.id(),
                        sourceEventId,
                        requestDigest,
                        "RECEIVED",
                        null,
                        now,
                        null,
                        null,
                        TraceContext.getOrCreateTraceId()
                ));
        if (existing.isPresent()) {
            repository.updateTriggerEvent(received);
        } else if (!repository.insertTriggerEvent(received)) {
            ExecutionTriggerEvent replay = repository.triggerEventBySource(trigger.id(), sourceEventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "EXECUTION_DUPLICATE_TRIGGER"));
            if (replay.runId() != null) {
                return replay;
            }
            received = retryReceivedEvent(replay, requestDigest, now);
            repository.updateTriggerEvent(received);
        }
        return received;
    }

    private ExecutionTriggerEvent acceptedEvent(ExecutionTriggerEvent event, UUID runId) {
        return new ExecutionTriggerEvent(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                "ACCEPTED",
                runId,
                event.receivedAt(),
                null,
                null,
                event.traceId()
        );
    }

    private void advanceCronTrigger(ExecutionTrigger trigger, Instant scanTime, boolean fired, Instant nextFireAt) {
        repository.updateTrigger(new ExecutionTrigger(
                trigger.id(),
                trigger.planId(),
                trigger.triggerType(),
                trigger.status(),
                trigger.configDigest(),
                trigger.configSummaryJson(),
                trigger.secretRef(),
                trigger.secretRefDigest(),
                nextFireAt,
                fired ? scanTime : trigger.lastFireAt(),
                trigger.createdBy(),
                trigger.updatedBy(),
                trigger.createdAt(),
                scanTime
        ));
    }

    private Instant safeNextCronFireAt(ExecutionTrigger trigger, Instant scanTime) {
        try {
            return nextCronFireAt(readMap(trigger.configSummaryJson()), scanTime);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void pauseCronTrigger(ExecutionTrigger trigger, Instant scanTime) {
        repository.updateTrigger(new ExecutionTrigger(
                trigger.id(),
                trigger.planId(),
                trigger.triggerType(),
                "PAUSED",
                trigger.configDigest(),
                trigger.configSummaryJson(),
                trigger.secretRef(),
                trigger.secretRefDigest(),
                null,
                trigger.lastFireAt(),
                trigger.createdBy(),
                trigger.updatedBy(),
                trigger.createdAt(),
                scanTime
        ));
    }

    private ExecutionTriggerEvent duplicateEvent(ExecutionTriggerEvent existing, String requestDigest, Instant now) {
        if (!Objects.equals(existing.requestDigest(), requestDigest)) {
            return new ExecutionTriggerEvent(
                    existing.id(),
                    existing.triggerId(),
                    existing.sourceEventId(),
                    existing.requestDigest(),
                    "DUPLICATE",
                    existing.runId(),
                    existing.receivedAt(),
                    "EXECUTION_TRIGGER_DUPLICATE_PAYLOAD",
                    "Duplicate sourceEventId received with different request digest",
                    TraceContext.getOrCreateTraceId()
            );
        }
        return new ExecutionTriggerEvent(
                existing.id(),
                existing.triggerId(),
                existing.sourceEventId(),
                existing.requestDigest(),
                "DUPLICATE",
                existing.runId(),
                existing.receivedAt() == null ? now : existing.receivedAt(),
                existing.errorCode(),
                existing.errorSummary(),
                TraceContext.getOrCreateTraceId()
        );
    }

    private ExecutionTriggerEvent retryReceivedEvent(
            ExecutionTriggerEvent existing,
            String requestDigest,
            Instant now
    ) {
        return new ExecutionTriggerEvent(
                existing.id(),
                existing.triggerId(),
                existing.sourceEventId(),
                requestDigest,
                "RECEIVED",
                null,
                now,
                null,
                null,
                TraceContext.getOrCreateTraceId()
        );
    }

    private ExecutionTriggerEvent failedEvent(ExecutionTriggerEvent event, String errorCode, String errorSummary) {
        return new ExecutionTriggerEvent(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                "FAILED",
                event.runId(),
                event.receivedAt(),
                boundedNullableText(errorCode, 64),
                boundedNullableText(errorSummary, 512),
                event.traceId()
        );
    }

    private ExecutionTriggerEvent rejectedEvent(ExecutionTriggerEvent event, String errorCode, String errorSummary) {
        return new ExecutionTriggerEvent(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                "REJECTED",
                event.runId(),
                event.receivedAt(),
                boundedNullableText(errorCode, 64),
                boundedNullableText(errorSummary, 512),
                event.traceId()
        );
    }

    private boolean validWebhookSignature(
            ExecutionTrigger trigger,
            String rawPayload,
            String timestamp,
            String signature,
            String sourceEventId
    ) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return false;
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > properties.effectiveWebhookClockSkewSeconds()) {
            return false;
        }
        String secret = resolveWebhookSecret(trigger);
        String expected = hmacSha256(secret, String.join(".",
                timestamp.trim(),
                sourceEventId.trim(),
                rawPayload == null ? "" : rawPayload
        ));
        return constantTimeEquals(expected, signature.trim());
    }

    private String resolveWebhookSecret(ExecutionTrigger trigger) {
        if (!StringUtils.hasText(trigger.secretRef())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_TRIGGER_SECRET_REQUIRED");
        }
        String cacheKey = trigger.id() + ":" + stringOrEmpty(trigger.secretRefDigest());
        CachedSecret cached = webhookSecretCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }
        SecretResolveContext context = new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp9-execution-service",
                "PROJECT",
                requirePlan(trigger.planId()).projectId()
        );
        for (SecretProvider provider : secretProviders) {
            Optional<ResolvedSecret> resolved = provider.resolve(trigger.secretRef(), context);
            if (resolved.isPresent() && StringUtils.hasText(resolved.get().value())) {
                String value = resolved.get().value();
                cacheWebhookSecret(cacheKey, value, now);
                return value;
            }
        }
        throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "EXECUTION_TRIGGER_SECRET_UNRESOLVED");
    }

    private void cacheWebhookSecret(String cacheKey, String value, Instant now) {
        long ttlSeconds = properties.effectiveWebhookSecretCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            webhookSecretCache.remove(cacheKey);
            return;
        }
        webhookSecretCache.put(cacheKey, new CachedSecret(value, now.plusSeconds(ttlSeconds)));
    }

    private void ensureTriggerGloballyAllowed(String triggerType, String status) {
        if (!"ENABLED".equals(status)) {
            return;
        }
        if (!globalEnabled(triggerType)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_TRIGGER_DISABLED");
        }
    }

    private boolean globalEnabled(String triggerType) {
        return switch (triggerType) {
            case "WEBHOOK" -> properties.webhookEnabled();
            case "CRON" -> properties.cronEnabled();
            default -> false;
        };
    }

    private ExecutionTriggerQuery normalizeTriggerQuery(ExecutionTriggerQuery query) {
        if (StringUtils.hasText(query.triggerType()) && !TRIGGER_TYPES.contains(query.triggerType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        if (StringUtils.hasText(query.status()) && !TRIGGER_STATUSES.contains(query.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_STATUS_INVALID");
        }
        return query;
    }

    private ExecutionTriggerEventQuery normalizeEventQuery(ExecutionTriggerEventQuery query) {
        if (StringUtils.hasText(query.status()) && !EVENT_STATUSES.contains(query.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_EVENT_STATUS_INVALID");
        }
        return query;
    }

    private String normalizeTriggerType(String triggerType) {
        String normalized = boundedNullableText(triggerType, 32);
        if (normalized != null) {
            normalized = normalized.toUpperCase(Locale.ROOT);
        }
        if (!TRIGGER_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        return normalized;
    }

    private String normalizeStatus(String status, String defaultStatus) {
        String normalized = boundedNullableText(status, 32);
        normalized = normalized == null ? defaultStatus : normalized.toUpperCase(Locale.ROOT);
        if (!TRIGGER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_STATUS_INVALID");
        }
        return normalized;
    }

    private String normalizedSecretRef(String secretRef, String triggerType, String status) {
        String normalized = boundedNullableText(secretRef, 256);
        if (!StringUtils.hasText(normalized)) {
            ensureRequiredSecretRef(triggerType, status, null);
            return null;
        }
        if (!SECRET_REF_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "EXECUTION_TRIGGER_SECRET_REF_INVALID");
        }
        return normalized;
    }

    private void ensureRequiredSecretRef(String triggerType, String status, String secretRef) {
        if ("WEBHOOK".equals(triggerType) && "ENABLED".equals(status) && !StringUtils.hasText(secretRef)) {
            throw new BusinessException(ErrorCode.SECRET_REQUIRED, "EXECUTION_TRIGGER_SECRET_REQUIRED");
        }
    }

    private Map<String, Object> sanitizedConfig(Map<String, Object> config, String triggerType) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        Map<String, Object> input = config == null ? Map.of() : config;
        input.forEach((key, value) -> {
            String normalizedKey = boundedNullableText(key, 64);
            if (!StringUtils.hasText(normalizedKey)) {
                return;
            }
            String lowerKey = normalizedKey.toLowerCase(Locale.ROOT);
            if (forbiddenConfigKey(lowerKey)) {
                throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "EXECUTION_TRIGGER_CONFIG_SECRET_FIELD");
            }
            if (SAFE_CONFIG_KEYS.contains(normalizedKey) || SAFE_CONFIG_KEYS.contains(lowerKey)) {
                sanitized.put(normalizedKey, sanitizedConfigValue(value));
            }
        });
        sanitized.put("type", triggerType);
        sanitized.put("rawPayloadStored", false);
        sanitized.put("secretStored", false);
        if ("CRON".equals(triggerType)) {
            normalizeCronConfig(sanitized);
        }
        return sanitized;
    }

    private void normalizeCronConfig(Map<String, Object> sanitized) {
        String cron = sanitizedConfigText(sanitized.get("cron"));
        if (!StringUtils.hasText(cron)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_CRON_EXPRESSION_REQUIRED");
        }
        parseCron(cron);
        ZoneId zone = cronZone(sanitized);
        sanitized.put("cron", cron);
        sanitized.put("timezone", zone.getId());
        sanitized.put("cronPayloadStored", false);
    }

    private boolean forbiddenConfigKey(String key) {
        return FORBIDDEN_CONFIG_KEY_PARTS.stream().anyMatch(key::contains);
    }

    private Object sanitizedConfigValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return boundedNullableText(text, MAX_CONFIG_TEXT_LENGTH);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String normalizedKey = key == null ? null : boundedNullableText(String.valueOf(key), 64);
                if (StringUtils.hasText(normalizedKey) && !forbiddenConfigKey(normalizedKey.toLowerCase(Locale.ROOT))) {
                    sanitized.put(normalizedKey, sanitizedConfigValue(nestedValue));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> values = new java.util.ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count >= MAX_CONFIG_LIST_ITEMS) {
                    values.add("...");
                    break;
                }
                values.add(sanitizedConfigValue(item));
                count++;
            }
            return values;
        }
        return boundedNullableText(String.valueOf(value), MAX_CONFIG_TEXT_LENGTH);
    }

    private Instant initialNextFireAt(
            String triggerType,
            Map<String, Object> configSummary,
            Instant requestedNextFireAt,
            Instant now
    ) {
        if (!"CRON".equals(triggerType)) {
            return requestedNextFireAt;
        }
        return requestedNextFireAt == null ? nextCronFireAt(configSummary, now) : requestedNextFireAt;
    }

    private Instant updatedNextFireAt(
            ExecutionTrigger existing,
            Map<String, Object> configSummary,
            Instant requestedNextFireAt,
            boolean configChanged,
            String status,
            Instant now
    ) {
        if (!"CRON".equals(existing.triggerType())) {
            return requestedNextFireAt == null ? existing.nextFireAt() : requestedNextFireAt;
        }
        if (requestedNextFireAt != null) {
            return requestedNextFireAt;
        }
        if (configChanged || ("ENABLED".equals(status) && existing.nextFireAt() == null)) {
            return nextCronFireAt(configSummary, now);
        }
        return existing.nextFireAt();
    }

    private Instant nextCronFireAt(Map<String, Object> configSummary, Instant referenceTime) {
        String cron = sanitizedConfigText(configSummary.get("cron"));
        CronExpression expression = parseCron(cron);
        ZonedDateTime reference = ZonedDateTime.ofInstant(referenceTime, cronZone(configSummary));
        ZonedDateTime next = expression.next(reference);
        if (next == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_CRON_NEXT_FIRE_UNAVAILABLE");
        }
        return next.toInstant();
    }

    private CronExpression parseCron(String cron) {
        try {
            return CronExpression.parse(cron);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_CRON_EXPRESSION_INVALID");
        }
    }

    private ZoneId cronZone(Map<String, Object> configSummary) {
        String timezone = sanitizedConfigText(configSummary.get("timezone"));
        String zoneId = StringUtils.hasText(timezone) ? timezone : DEFAULT_CRON_TIMEZONE;
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_CRON_TIMEZONE_INVALID");
        }
    }

    private String sanitizedConfigText(Object value) {
        return value == null ? null : boundedNullableText(String.valueOf(value), MAX_CONFIG_TEXT_LENGTH);
    }

    private ExecutionTriggerResponse toResponse(ExecutionTrigger trigger) {
        return new ExecutionTriggerResponse(
                trigger.id(),
                trigger.planId(),
                trigger.triggerType(),
                trigger.status(),
                trigger.configDigest(),
                readMap(trigger.configSummaryJson()),
                StringUtils.hasText(trigger.secretRef()),
                trigger.secretRefDigest(),
                trigger.nextFireAt(),
                trigger.lastFireAt(),
                trigger.createdBy(),
                trigger.updatedBy(),
                trigger.createdAt(),
                trigger.updatedAt()
        );
    }

    private ExecutionTriggerEventResponse toEventResponse(ExecutionTriggerEvent event) {
        return new ExecutionTriggerEventResponse(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                event.status(),
                event.runId(),
                event.receivedAt(),
                event.errorCode(),
                event.errorSummary(),
                event.traceId()
        );
    }

    private ExecutionPlan requirePlan(UUID id) {
        return repository.plan(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划不存在"));
    }

    private ExecutionTrigger requireTrigger(UUID id) {
        return repository.trigger(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行触发器不存在"));
    }

    private String webhookRequestKey(UUID triggerId, String sourceEventId) {
        return "webhook:" + sha256(triggerId + ":" + sourceEventId).substring(0, 64);
    }

    private String cronRequestKey(UUID triggerId, String sourceEventId) {
        return "cron:" + sha256(triggerId + ":" + sourceEventId).substring(0, 64);
    }

    private String cronSourceEventId(UUID triggerId, Instant fireAt) {
        return boundedNullableText("cron:" + triggerId + ":" + fireAt, 256);
    }

    private String cronRequestDigest(ExecutionTrigger trigger, Instant fireAt) {
        return sha256(String.join(".",
                "cron",
                trigger.id().toString(),
                fireAt.toString(),
                trigger.configDigest()
        ));
    }

    private String requestDigest(String timestamp, String sourceEventId, String rawPayload) {
        return sha256(String.join(".",
                timestamp == null ? "" : timestamp.trim(),
                sourceEventId == null ? "" : sourceEventId.trim(),
                rawPayload == null ? "" : rawPayload
        ));
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "EXECUTION_TRIGGER_SIGNATURE_FAILED");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void auditTrigger(ExecutionTrigger trigger, String action, String result, Map<String, Object> afterJson) {
        ExecutionPlan plan = requirePlan(trigger.planId());
        contextClient.writeAuditEvent(action, "EXECUTION_TRIGGER", trigger.id().toString(), plan.projectId(), result, afterJson);
    }

    private void auditTriggerEvent(ExecutionTrigger trigger, ExecutionTriggerEvent event, String action, String result) {
        ExecutionPlan plan = requirePlan(trigger.planId());
        contextClient.writeAuditEvent(action, "EXECUTION_TRIGGER_EVENT", event.id().toString(), plan.projectId(), result, Map.of(
                "triggerId", trigger.id().toString(),
                "triggerType", trigger.triggerType(),
                "sourceEventDigest", sha256(event.sourceEventId()),
                "requestDigest", event.requestDigest(),
                "status", event.status(),
                "runId", event.runId() == null ? "" : event.runId().toString(),
                "errorCode", event.errorCode() == null ? "" : event.errorCode()
        ));
    }

    private Map<String, Object> triggerAuditSummary(ExecutionTrigger trigger) {
        return Map.of(
                "triggerType", trigger.triggerType(),
                "status", trigger.status(),
                "configDigest", trigger.configDigest(),
                "secretRefConfigured", StringUtils.hasText(trigger.secretRef()),
                "secretRefDigest", trigger.secretRefDigest() == null ? "" : trigger.secretRefDigest()
        );
    }

    private String cronErrorCode(RuntimeException exception) {
        if (StringUtils.hasText(exception.getMessage()) && exception.getMessage().matches("[A-Z0-9_.:-]{1,64}")) {
            return exception.getMessage();
        }
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return "EXECUTION_CRON_TRIGGER_FAILED";
    }

    private String sanitizedTriggerErrorSummary(String value) {
        String bounded = boundedNullableText(value, 512);
        return StringUtils.hasText(bounded) ? bounded : "Cron trigger failed";
    }

    private record CronTriggerOutcome(boolean triggered, boolean failed) {
    }

    private String boundedRequiredText(String value, int maxLength, String errorCode) {
        String bounded = boundedNullableText(value, maxLength);
        if (!StringUtils.hasText(bounded)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorCode);
        }
        return bounded;
    }

    private String boundedNullableText(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return SensitiveTextSanitizer.unreadableMap();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "EXECUTION_JSON_INVALID");
        }
    }

    private String sha256(String value) {
        return SensitiveTextSanitizer.sha256Hex(value);
    }

    private String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record CachedSecret(String value, Instant expiresAt) {
    }
}
