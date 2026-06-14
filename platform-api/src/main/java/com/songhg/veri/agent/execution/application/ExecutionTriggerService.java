package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.secret.SecretProvider;
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
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ExecutionTriggerService {

    private static final Set<String> TRIGGER_TYPES = Set.of("WEBHOOK", "CRON");
    private static final Set<String> TRIGGER_STATUSES = Set.of("DISABLED", "ENABLED", "PAUSED");
    private static final Set<String> EVENT_STATUSES = Set.of("RECEIVED", "ACCEPTED", "REJECTED", "DUPLICATE", "FAILED");

    private final ExecutionRepository repository;
    private final ExecutionRunService runService;
    private final ExecutionActorResolver actorResolver;
    private final ExecutionPlatformContextClient contextClient;
    private final List<SecretProvider> secretProviders;
    private final ExecutionTriggerConfigSupport configSupport;
    private final ExecutionTriggerEventSupport eventSupport;
    private final ExecutionTriggerSignatureSupport signatureSupport;
    private final ExecutionRunJsonSupport jsonSupport;
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
        this.configSupport = new ExecutionTriggerConfigSupport();
        this.eventSupport = new ExecutionTriggerEventSupport();
        this.signatureSupport = new ExecutionTriggerSignatureSupport();
        this.jsonSupport = new ExecutionRunJsonSupport(objectMapper);
        this.properties = properties;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionTriggerResponse createTrigger(UUID planId, CreateExecutionTriggerCommand command) {
        ExecutionPlan plan = requirePlan(planId);
        String triggerType = configSupport.normalizeTriggerType(command == null ? null : command.triggerType());
        String status = configSupport.normalizeStatus(command == null ? null : command.status(), "DISABLED");
        Map<String, Object> configSummary = configSupport.sanitizedConfig(command == null ? null : command.config(), triggerType);
        String secretRef = configSupport.normalizedSecretRef(command == null ? null : command.secretRef(), triggerType, status);
        ensureTriggerGloballyAllowed(triggerType, status);
        Instant now = Instant.now();
        Instant nextFireAt = configSupport.initialNextFireAt(triggerType, configSummary, command == null ? null : command.nextFireAt(), now);
        String actor = actorResolver.currentActor();
        ExecutionTrigger trigger = new ExecutionTrigger(
                UUID.randomUUID(),
                plan.id(),
                triggerType,
                status,
                signatureSupport.sha256(jsonSupport.json(configSummary)),
                jsonSupport.json(configSummary),
                secretRef,
                StringUtils.hasText(secretRef) ? signatureSupport.sha256(secretRef) : null,
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
        String status = configSupport.normalizeStatus(command == null ? null : command.status(), existing.status());
        Map<String, Object> configSummary = command == null || command.config() == null
                ? jsonSupport.readMap(existing.configSummaryJson())
                : configSupport.sanitizedConfig(command.config(), existing.triggerType());
        String secretRef = command == null || command.secretRef() == null
                ? existing.secretRef()
                : configSupport.normalizedSecretRef(command.secretRef(), existing.triggerType(), status);
        configSupport.ensureRequiredSecretRef(existing.triggerType(), status, secretRef);
        ensureTriggerGloballyAllowed(existing.triggerType(), status);
        Instant now = Instant.now();
        Instant nextFireAt = configSupport.updatedNextFireAt(
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
                signatureSupport.sha256(jsonSupport.json(configSummary)),
                jsonSupport.json(configSummary),
                secretRef,
                StringUtils.hasText(secretRef) ? signatureSupport.sha256(secretRef) : null,
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
                .map(eventSupport::toEventResponse)
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
        String eventId = eventSupport.boundedRequiredText(sourceEventId, 256, "EXECUTION_TRIGGER_SOURCE_EVENT_REQUIRED");
        String requestDigest = signatureSupport.requestDigest(timestamp, eventId, rawPayload);
        Instant now = Instant.now();
        Optional<ExecutionTriggerEvent> existing = repository.triggerEventBySource(trigger.id(), eventId);
        if (existing.isPresent() && existing.get().runId() != null) {
            ExecutionTriggerEvent duplicate = eventSupport.duplicateEvent(existing.get(), requestDigest, now);
            repository.updateTriggerEvent(duplicate);
            return new ExecutionWebhookTriggerResponse(eventSupport.toEventResponse(duplicate), duplicate.runId(), true);
        }
        // A rejected or failed event with no run is evidence of a previous rejected attempt, not a completed trigger.
        // Reusing that row lets a correctly signed retry recover while preserving one row per source event id.
        ExecutionTriggerEvent received = existing
                .map(event -> eventSupport.retryReceivedEvent(event, requestDigest, now))
                .orElseGet(() -> eventSupport.receivedEvent(trigger.id(), eventId, requestDigest, now));
        if (existing.isPresent()) {
            repository.updateTriggerEvent(received);
        } else if (!repository.insertTriggerEvent(received)) {
            ExecutionTriggerEvent replay = repository.triggerEventBySource(trigger.id(), eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "EXECUTION_DUPLICATE_TRIGGER"));
            if (replay.runId() != null) {
                return new ExecutionWebhookTriggerResponse(eventSupport.toEventResponse(replay), replay.runId(), true);
            }
            received = eventSupport.retryReceivedEvent(replay, requestDigest, now);
            repository.updateTriggerEvent(received);
        }
        if (!properties.webhookEnabled() || !"ENABLED".equals(trigger.status())) {
            ExecutionTriggerEvent rejected = eventSupport.rejectedEvent(received, "EXECUTION_TRIGGER_DISABLED", "Webhook trigger is disabled");
            repository.updateTriggerEvent(rejected);
            auditTriggerEvent(trigger, rejected, "execution.trigger.rejected", "REJECTED");
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_TRIGGER_DISABLED");
        }
        if (!"WEBHOOK".equals(trigger.triggerType())) {
            ExecutionTriggerEvent rejected = eventSupport.rejectedEvent(received, "EXECUTION_TRIGGER_TYPE_INVALID", "Trigger is not webhook");
            repository.updateTriggerEvent(rejected);
            auditTriggerEvent(trigger, rejected, "execution.trigger.rejected", "REJECTED");
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        try {
            if (!validWebhookSignature(trigger, rawPayload, timestamp, signature, eventId)) {
                ExecutionTriggerEvent rejected = eventSupport.rejectedEvent(
                        received,
                        "EXECUTION_TRIGGER_SIGNATURE_INVALID",
                        "Webhook signature is missing, expired, or invalid"
                );
                repository.updateTriggerEvent(rejected);
                auditTriggerEvent(trigger, rejected, "execution.trigger.rejected", "REJECTED");
                throw new BusinessException(ErrorCode.FORBIDDEN, "EXECUTION_TRIGGER_SIGNATURE_INVALID");
            }
            String runRequestKey = signatureSupport.webhookRequestKey(trigger.id(), eventId);
            ExecutionRunDetailResponse run = runService.triggerExternalRun(
                    trigger.planId(),
                    "WEBHOOK",
                    runRequestKey,
                    eventId,
                    Map.of(
                            "triggerId", trigger.id().toString(),
                            "triggerEventSource", "WEBHOOK",
                            "sourceEventDigest", signatureSupport.sha256(eventId),
                            "requestDigest", requestDigest,
                            "webhookPayloadStored", false,
                            "webhookSignatureStored", false
                    )
            );
            ExecutionTriggerEvent accepted = eventSupport.acceptedEvent(received, run.id());
            repository.updateTriggerEvent(accepted);
            markTriggerFired(trigger, now);
            auditTriggerEvent(trigger, accepted, "execution.trigger.fired", "SUCCESS");
            return new ExecutionWebhookTriggerResponse(eventSupport.toEventResponse(accepted), run.id(), run.idempotentReplay());
        } catch (BusinessException exception) {
            if ("EXECUTION_TRIGGER_SIGNATURE_INVALID".equals(exception.getMessage())) {
                throw exception;
            }
            ExecutionTriggerEvent failed = eventSupport.failedEvent(received, exception.getMessage(), "Webhook trigger failed");
            repository.updateTriggerEvent(failed);
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
        String sourceEventId = configSupport.cronSourceEventId(trigger.id(), fireAt);
        String requestDigest = signatureSupport.cronRequestDigest(trigger, fireAt);
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
            nextFireAt = configSupport.nextCronFireAt(jsonSupport.readMap(trigger.configSummaryJson()), scanTime);
            run = runService.triggerExternalRun(
                    trigger.planId(),
                    "CRON",
                    signatureSupport.cronRequestKey(trigger.id(), sourceEventId),
                    sourceEventId,
                    Map.of(
                            "triggerId", trigger.id().toString(),
                            "triggerEventSource", "CRON",
                            "sourceEventDigest", signatureSupport.sha256(sourceEventId),
                            "scheduledFireAt", fireAt.toString(),
                            "configDigest", trigger.configDigest(),
                            "cronPayloadStored", false
                    )
            );
        } catch (RuntimeException exception) {
            ExecutionTriggerEvent failed = eventSupport.failedEvent(
                    received,
                    eventSupport.cronErrorCode(exception),
                    eventSupport.sanitizedTriggerErrorSummary(exception.getMessage())
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
        ExecutionTriggerEvent accepted = eventSupport.acceptedEvent(received, run.id());
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
                .map(event -> eventSupport.retryReceivedEvent(event, requestDigest, now))
                .orElseGet(() -> eventSupport.receivedEvent(trigger.id(), sourceEventId, requestDigest, now));
        if (existing.isPresent()) {
            repository.updateTriggerEvent(received);
        } else if (!repository.insertTriggerEvent(received)) {
            ExecutionTriggerEvent replay = repository.triggerEventBySource(trigger.id(), sourceEventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "EXECUTION_DUPLICATE_TRIGGER"));
            if (replay.runId() != null) {
                return replay;
            }
            received = eventSupport.retryReceivedEvent(replay, requestDigest, now);
            repository.updateTriggerEvent(received);
        }
        return received;
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
            return configSupport.nextCronFireAt(jsonSupport.readMap(trigger.configSummaryJson()), scanTime);
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

    private boolean validWebhookSignature(
            ExecutionTrigger trigger,
            String rawPayload,
            String timestamp,
            String signature,
            String sourceEventId
    ) {
        return signatureSupport.validWebhookSignature(
                () -> resolveWebhookSecret(trigger),
                rawPayload,
                timestamp,
                signature,
                sourceEventId,
                properties.effectiveWebhookClockSkewSeconds()
        );
    }

    private String resolveWebhookSecret(ExecutionTrigger trigger) {
        if (!StringUtils.hasText(trigger.secretRef())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_TRIGGER_SECRET_REQUIRED");
        }
        String cacheKey = trigger.id() + ":" + signatureSupport.stringOrEmpty(trigger.secretRefDigest());
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

    private ExecutionTriggerResponse toResponse(ExecutionTrigger trigger) {
        return new ExecutionTriggerResponse(
                trigger.id(),
                trigger.planId(),
                trigger.triggerType(),
                trigger.status(),
                trigger.configDigest(),
                jsonSupport.readMap(trigger.configSummaryJson()),
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

    private ExecutionPlan requirePlan(UUID id) {
        return repository.plan(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划不存在"));
    }

    private ExecutionTrigger requireTrigger(UUID id) {
        return repository.trigger(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行触发器不存在"));
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
                "sourceEventDigest", signatureSupport.sha256(event.sourceEventId()),
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

    private record CronTriggerOutcome(boolean triggered, boolean failed) {
    }

    private record CachedSecret(String value, Instant expiresAt) {
    }
}
