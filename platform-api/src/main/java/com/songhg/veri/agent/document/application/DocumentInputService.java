package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.document.application.command.CandidateBatchActionRequest;
import com.songhg.veri.agent.document.application.command.ConfirmDocumentCandidateRequest;
import com.songhg.veri.agent.document.application.command.CreateDocumentImportRequest;
import com.songhg.veri.agent.document.application.command.DocumentPublishRequest;
import com.songhg.veri.agent.document.application.command.IgnoreDocumentCandidateRequest;
import com.songhg.veri.agent.document.application.command.UpdateDocumentCandidateRequest;
import com.songhg.veri.agent.document.application.command.UpdateFieldMappingRequest;
import com.songhg.veri.agent.document.application.command.UpsertDocumentSourceRequest;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.query.DocumentCandidateQuery;
import com.songhg.veri.agent.document.application.query.DocumentImportQuery;
import com.songhg.veri.agent.document.application.query.DocumentParseFeedbackQuery;
import com.songhg.veri.agent.document.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.document.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.document.application.view.DocumentCandidateBatchActionResponse;
import com.songhg.veri.agent.document.application.view.DocumentCandidateResponse;
import com.songhg.veri.agent.document.application.view.DocumentImportResponse;
import com.songhg.veri.agent.document.application.view.DocumentInputHealthResponse;
import com.songhg.veri.agent.document.application.view.DocumentInputMetrics;
import com.songhg.veri.agent.document.application.view.DocumentParseFeedbackSampleResponse;
import com.songhg.veri.agent.document.application.view.DocumentPublishRecordResponse;
import com.songhg.veri.agent.document.application.view.DocumentPublishResponse;
import com.songhg.veri.agent.document.application.view.DocumentSecretProviderHealthResponse;
import com.songhg.veri.agent.document.application.view.DocumentSourceHealthResponse;
import com.songhg.veri.agent.document.application.view.DocumentSourceResponse;
import com.songhg.veri.agent.document.application.view.DocumentWebhookEventResponse;
import com.songhg.veri.agent.document.application.view.FieldMappingResponse;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;








@Service
public class DocumentInputService {

    private static final Set<DocumentSourceType> SUPPORTED_SOURCE_TYPES = Set.of(
            DocumentSourceType.TEXT,
            DocumentSourceType.MARKDOWN,
            DocumentSourceType.WORD,
            DocumentSourceType.PDF,
            DocumentSourceType.OCR,
            DocumentSourceType.CUSTOM_API
    );
    private static final Set<String> SUPPORTED_WEBHOOK_EVENT_VERSIONS = Set.of("1.0");
    private final DocumentInputRepository repository;
    private final DocumentSourceManagementService sourceManagementService;
    private final DocumentImportService importService;
    private final DocumentInputActorResolver actorResolver;
    private final DocumentCandidateWorkflowService candidateWorkflowService;
    private final DocumentRequirementPublishService publishService;
    private final DocumentInputPlatformContextClient contextClient;
    private final DocumentContentExtractor contentExtractor;
    private final DocumentInputConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final DocumentInputProperties properties;
    private final DocumentInputMetrics metrics;
    private final DocumentWebhookSecretResolver webhookSecretResolver;
    private final DocumentWebhookIngressGuard webhookIngressGuard;
    private final DocumentInputResponseMapper responseMapper;
    private final DocumentInputEventPublisher eventPublisher;

    public DocumentInputService(
            DocumentInputRepository repository,
            DocumentSourceManagementService sourceManagementService,
            DocumentImportService importService,
            DocumentInputActorResolver actorResolver,
            DocumentCandidateWorkflowService candidateWorkflowService,
            DocumentRequirementPublishService publishService,
            DocumentInputPlatformContextClient contextClient,
            DocumentContentExtractor contentExtractor,
            DocumentInputConfiguration configuration,
            ObjectMapper objectMapper,
            DocumentInputProperties properties,
            DocumentInputMetrics metrics,
            DocumentWebhookSecretResolver webhookSecretResolver,
            DocumentWebhookIngressGuard webhookIngressGuard,
            DocumentInputEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.sourceManagementService = sourceManagementService;
        this.importService = importService;
        this.actorResolver = actorResolver;
        this.candidateWorkflowService = candidateWorkflowService;
        this.publishService = publishService;
        this.contextClient = contextClient;
        this.contentExtractor = contentExtractor;
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.webhookSecretResolver = webhookSecretResolver;
        this.webhookIngressGuard = webhookIngressGuard;
        this.responseMapper = new DocumentInputResponseMapper(repository, objectMapper);
        this.eventPublisher = eventPublisher;
    }

    public int supportedSourceTypeCount() {
        return SUPPORTED_SOURCE_TYPES.size();
    }

    public DocumentInputHealthResponse health() {
        DocumentSecretProviderHealthResponse externalSecretProvider = externalSecretProviderHealth();
        return new DocumentInputHealthResponse(
                "document-input",
                properties.inputEnabled() ? "UP" : "DISABLED",
                supportedSourceTypeCount(),
                properties.inputEnabled(),
                properties.webhookEnabled(),
                properties.modelParseEnabled(),
                maxWebhookPayloadBytes(),
                maxImportContentBytes(),
                configuration.documentBinaryMaxBytes(),
                configuration.ocrConfigured(),
                configuration.ocrTimeoutSeconds(),
                configuration.ocrMaxOutputChars(),
                configuration.ocrMaxConcurrentProcesses(),
                contentExtractor.ocrAvailablePermits(),
                configuration.ocrWorkerMode(),
                configuration.ocrRemoteWorkerConfigured(),
                configuration.ocrWorkerTokenConfigured(),
                configuration.ocrLocalCommandFallbackEnabled(),
                contentExtractor.ocrLocalCommandExecutionAllowed(),
                batchActionLimit(),
                webhookIngressGuard.ipAllowlistConfigured(),
                webhookIngressGuard.trustedProxyCidrsConfigured(),
                webhookIngressGuard.rateLimitEnabled(),
                webhookIngressGuard.rateLimitMaxRequests(),
                webhookIngressGuard.rateLimitWindowSeconds(),
                configuration.binaryMimeValidationEnabled(),
                configuration.pdfMaxPages(),
                configuration.pdfMaxParseMillis(),
                configuration.malwareScanEnabled(),
                configuration.malwareScanTimeoutSeconds(),
                configuration.malwareScanMaxConcurrentProcesses(),
                contentExtractor.malwareScanAvailablePermits(),
                webhookSecretResolver.cacheEnabled(),
                webhookSecretResolver.cacheTtlSeconds(),
                webhookSecretResolver.rotationOverlapSeconds(),
                webhookSecretResolver.cacheSize(),
                externalSecretProvider
        );
    }

    private DocumentSecretProviderHealthResponse externalSecretProviderHealth() {
        var health = webhookSecretResolver.externalProviderHealth();
        metrics.recordSecretProviderHealth(health.providerType(), health.status());
        return new DocumentSecretProviderHealthResponse(
                health.providerCode(),
                health.providerType(),
                health.configured(),
                health.status(),
                health.timeoutSeconds(),
                health.maxAttempts(),
                health.checkedAt(),
                health.lastErrorMessage()
        );
    }

    public PageResponse<DocumentSourceResponse> sources(DocumentSourceQuery query) {
        ensureInputEnabled();
        return sourceManagementService.sources(query);
    }

    public DocumentSourceResponse createSource(UpsertDocumentSourceRequest request) {
        ensureInputEnabled();
        return sourceManagementService.createSource(request);
    }

    public DocumentSourceResponse updateSource(UUID id, UpsertDocumentSourceRequest request) {
        ensureInputEnabled();
        return sourceManagementService.updateSource(id, request);
    }

    public DocumentSourceHealthResponse sourceHealth(UUID id) {
        ensureInputEnabled();
        return sourceManagementService.sourceHealth(id);
    }

    public FieldMappingResponse fieldMapping() {
        ensureInputEnabled();
        return sourceManagementService.fieldMapping();
    }

    public FieldMappingResponse updateFieldMapping(UpdateFieldMappingRequest request) {
        ensureInputEnabled();
        return sourceManagementService.updateFieldMapping(request);
    }

    @Transactional
    public DocumentImportResponse importDocument(CreateDocumentImportRequest request) {
        ensureInputEnabled();
        return importService.importDocument(request);
    }

    @Transactional
    public DocumentImportResponse importMultipart(
            String projectId,
            DocumentSourceType sourceType,
            String title,
            String sourceRef,
            String sourceUrl,
            UUID mappingId,
            UUID sourceId,
            String filename,
            String contentType,
            byte[] fileBytes
    ) {
        ensureInputEnabled();
        return importService.importMultipart(
                projectId,
                sourceType,
                title,
                sourceRef,
                sourceUrl,
                mappingId,
                sourceId,
                filename,
                contentType,
                fileBytes
        );
    }

    public PageResponse<DocumentImportResponse> imports(DocumentImportQuery query) {
        ensureInputEnabled();
        return importService.imports(query);
    }

    public DocumentImportResponse importRecord(UUID id) {
        ensureInputEnabled();
        return importService.importRecord(id);
    }

    public PageResponse<DocumentCandidateResponse> candidates(DocumentCandidateQuery query) {
        ensureInputEnabled();
        UUID importId = query.importId();
        repository.importRecord(importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + importId));
        return PageResponse.of(
                repository.candidates(query).stream()
                        .map(responseMapper::toCandidateResponse)
                        .toList(),
                query.index(),
                query.size(),
                repository.countCandidates(query)
        );
    }

    public PageResponse<DocumentParseFeedbackSampleResponse> parseFeedbackSamples(DocumentParseFeedbackQuery query) {
        ensureInputEnabled();
        List<DocumentParseFeedbackSampleResponse> items = repository.parseFeedbackSamples(query).stream()
                .map(responseMapper::toParseFeedbackSampleResponse)
                .toList();
        return PageResponse.of(
                items,
                query.index(),
                query.size(),
                repository.countParseFeedbackSamples(query)
        );
    }

    public DocumentCandidateResponse updateCandidate(UUID id, UpdateDocumentCandidateRequest request) {
        ensureInputEnabled();
        return candidateWorkflowService.updateCandidate(id, request);
    }

    public DocumentCandidateResponse confirmCandidate(UUID id, ConfirmDocumentCandidateRequest request) {
        ensureInputEnabled();
        return candidateWorkflowService.confirmCandidate(id, request);
    }

    public DocumentCandidateResponse ignoreCandidate(UUID id, IgnoreDocumentCandidateRequest request) {
        ensureInputEnabled();
        return candidateWorkflowService.ignoreCandidate(id, request);
    }

    public DocumentCandidateBatchActionResponse batchCandidateAction(CandidateBatchActionRequest request) {
        ensureInputEnabled();
        return candidateWorkflowService.batchCandidateAction(request);
    }

    public DocumentPublishResponse publishImport(UUID importId, DocumentPublishRequest request) {
        ensureInputEnabled();
        return publishService.publishImport(importId, request);
    }

    public PageResponse<DocumentPublishRecordResponse> publishRecords(UUID importId) {
        ensureInputEnabled();
        return publishService.publishRecords(importId);
    }

    public PageResponse<DocumentWebhookEventResponse> webhookEvents(DocumentWebhookEventQuery query) {
        ensureInputEnabled();
        return PageResponse.of(
                repository.webhookEvents(query).stream()
                        .map(responseMapper::toWebhookEventResponse)
                        .toList(),
                query.index(),
                query.size(),
                repository.countWebhookEvents(query)
        );
    }

    public DocumentWebhookEventResponse webhookEvent(UUID id) {
        ensureInputEnabled();
        return repository.webhookEvent(id)
                .map(responseMapper::toWebhookEventResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, DocumentInputMessages.WEBHOOK_EVENT_NOT_FOUND.formatted(id)));
    }

    public DocumentWebhookEventResponse replayWebhookEvent(UUID id) {
        ensureInputEnabled();
        DocumentWebhookEvent event = repository.webhookEvent(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, DocumentInputMessages.WEBHOOK_EVENT_NOT_FOUND.formatted(id)));
        if (event.rawPayload() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.WEBHOOK_PAYLOAD_NOT_REPLAYABLE);
        }
        if (event.signatureStatus() != WebhookSignatureStatus.VALID) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.WEBHOOK_SIGNATURE_FAILED);
        }
        if (event.status() != WebhookEventStatus.FAILED && event.status() != WebhookEventStatus.DEAD_LETTER) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.WEBHOOK_ONLY_FAILED_OR_DEAD);
        }
        Instant replayAt = Instant.now();
        DocumentWebhookEvent replayQueued = new DocumentWebhookEvent(
                event.id(),
                event.sourceId(),
                null,
                event.sourceCode(),
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                event.eventVersion(),
                event.signatureStatus(),
                WebhookEventStatus.ACCEPTED,
                event.payloadDigest(),
                event.rawPayload(),
                null,
                event.retryCount() + 1,
                actorResolver.currentActor(),
                replayAt,
                TraceContext.getTraceId(),
                event.receivedAt(),
                null
        );
        repository.saveWebhookEvent(replayQueued);
        eventPublisher.publishWebhookAccepted(replayQueued.id());
        metrics.recordWebhook(replayQueued.signatureStatus(), replayQueued.status(), replayQueued.eventType());
        return responseMapper.toWebhookEventResponse(replayQueued);
    }

    public DocumentWebhookEventResponse handleWebhook(
            String sourceCode,
            String rawPayload,
            String timestamp,
            String signature,
            String eventId,
            String idempotencyKey,
            String eventVersion,
            String remoteAddress,
            String forwardedFor,
            String realIp
    ) {
        ensureInputEnabled();
        ensureWebhookEnabled();
        DocumentSourceConfig source = repository.sourceByCode(normalizeSourceCode(sourceCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, DocumentInputMessages.SOURCE_NOT_FOUND.formatted(sourceCode)));
        ensureExecutableSource(source.sourceType(), source.status());
        String clientIp = webhookIngressGuard.resolveClientIp(remoteAddress, forwardedFor, realIp);
        if (!webhookIngressGuard.isIpAllowed(source.sourceCode(), clientIp)) {
            rejectWebhookBeforeSignature(
                    source,
                    rawPayload,
                    eventId,
                    idempotencyKey,
                    eventVersion,
                    WebhookSignatureStatus.INVALID,
                    ErrorCode.FORBIDDEN,
                    DocumentInputMessages.WEBHOOK_IP_NOT_ALLOWED,
                    DocumentInputMessages.WEBHOOK_IP_NOT_ALLOWED.formatted(clientIp)
            );
        }
        ensureRequiredWebhookHeaders(timestamp, signature, eventId, idempotencyKey, eventVersion);
        DocumentWebhookIngressGuard.RateLimitDecision rateLimit =
                webhookIngressGuard.checkRateLimit(source.sourceCode(), clientIp, idempotencyKey);
        if (!rateLimit.allowed()) {
            rejectWebhookBeforeSignature(
                    source,
                    rawPayload,
                    eventId,
                    idempotencyKey,
                    eventVersion,
                    WebhookSignatureStatus.MISSING,
                    ErrorCode.BUDGET_EXCEEDED,
                    DocumentInputMessages.WEBHOOK_RATE_LIMITED,
                    DocumentInputMessages.WEBHOOK_RATE_LIMITED.formatted(
                            rateLimit.dimension(),
                            rateLimit.limit(),
                            rateLimit.windowSeconds(),
                            clientIp
                    )
            );
        }
        String payloadDigest = sha256(rawPayload);
        WebhookSignatureStatus signatureStatus = validateWebhookSignature(
                source,
                rawPayload,
                timestamp,
                signature,
                eventId,
                idempotencyKey
        );
        DocumentWebhookEvent duplicate = repository.webhookEventByIdentity(
                        source.sourceCode(),
                        trimToNull(eventId),
                        trimToNull(idempotencyKey)
                )
                .orElse(null);
        if (signatureStatus != WebhookSignatureStatus.VALID) {
            if (duplicate == null) {
                Instant rejectedAt = Instant.now();
                DocumentWebhookEvent rejected = new DocumentWebhookEvent(
                        UUID.randomUUID(),
                        source.id(),
                        null,
                        source.sourceCode(),
                        trimToNull(eventId),
                        trimToNull(idempotencyKey),
                        null,
                        trimToNull(eventVersion),
                        signatureStatus,
                        WebhookEventStatus.REJECTED,
                        payloadDigest,
                        null,
                        webhookSignatureFailureMessage(signatureStatus),
                        0,
                        null,
                        null,
                        null,
                        rejectedAt,
                        null
                );
                repository.saveWebhookEvent(rejected);
                writeAudit(
                        "WEBHOOK_REJECTED",
                        "DOCUMENT_WEBHOOK_EVENT",
                        rejected.id().toString(),
                        source.defaultProjectId(),
                        responseMapper.sanitizeWebhookEvent(rejected)
                );
                metrics.recordWebhook(signatureStatus, WebhookEventStatus.REJECTED, null);
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, webhookSignatureFailureMessage(signatureStatus));
        }
        if (payloadSize(rawPayload) > maxWebhookPayloadBytes()) {
            String payloadLimitMessage = webhookPayloadLimitMessage();
            Instant rejectedAt = Instant.now();
            DocumentWebhookEvent rejected = new DocumentWebhookEvent(
                    UUID.randomUUID(),
                    source.id(),
                    null,
                    source.sourceCode(),
                    trimToNull(eventId),
                    trimToNull(idempotencyKey),
                    null,
                    trimToNull(eventVersion),
                    signatureStatus,
                    WebhookEventStatus.REJECTED,
                    payloadDigest,
                    null,
                    payloadLimitMessage,
                    0,
                    null,
                    null,
                    null,
                    rejectedAt,
                    null
            );
            repository.saveWebhookEvent(rejected);
            writeAudit(
                    "WEBHOOK_REJECTED",
                    "DOCUMENT_WEBHOOK_EVENT",
                    rejected.id().toString(),
                    source.defaultProjectId(),
                    responseMapper.sanitizeWebhookEvent(rejected)
            );
            metrics.recordWebhook(signatureStatus, WebhookEventStatus.REJECTED, null);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, payloadLimitMessage);
        }
        if (duplicate != null) {
            return respondToDuplicateWebhookEvent(duplicate, payloadDigest);
        }
        String eventType = webhookEventType(rawPayload);
        Instant now = Instant.now();
        DocumentWebhookEvent accepted = new DocumentWebhookEvent(
                UUID.randomUUID(),
                source.id(),
                null,
                source.sourceCode(),
                trimToNull(eventId),
                trimToNull(idempotencyKey),
                eventType,
                trimToNull(eventVersion),
                signatureStatus,
                WebhookEventStatus.ACCEPTED,
                payloadDigest,
                rawPayload,
                null,
                0,
                null,
                null,
                null,
                now,
                null
        );
        DocumentWebhookEvent saved = repository.saveWebhookEvent(accepted);
        if (!saved.id().equals(accepted.id())) {
            return respondToDuplicateWebhookEvent(saved, payloadDigest);
        }
        eventPublisher.publishWebhookAccepted(saved.id());
        metrics.recordWebhook(saved.signatureStatus(), saved.status(), saved.eventType());
        return responseMapper.toWebhookEventResponse(saved);
    }

    private void rejectWebhookBeforeSignature(
            DocumentSourceConfig source,
            String rawPayload,
            String eventId,
            String idempotencyKey,
            String eventVersion,
            WebhookSignatureStatus signatureStatus,
            ErrorCode responseCode,
            String responseMessage,
            String eventMessage
    ) {
        Instant rejectedAt = Instant.now();
        DocumentWebhookEvent rejected = new DocumentWebhookEvent(
                UUID.randomUUID(),
                source.id(),
                null,
                source.sourceCode(),
                trimToNull(eventId),
                trimToNull(idempotencyKey),
                null,
                trimToNull(eventVersion),
                signatureStatus,
                WebhookEventStatus.REJECTED,
                sha256(rawPayload),
                null,
                eventMessage,
                0,
                null,
                null,
                null,
                rejectedAt,
                null
        );
        repository.saveWebhookEvent(rejected);
        writeAudit(
                "WEBHOOK_REJECTED",
                "DOCUMENT_WEBHOOK_EVENT",
                rejected.id().toString(),
                source.defaultProjectId(),
                responseMapper.sanitizeWebhookEvent(rejected)
        );
        metrics.recordWebhook(signatureStatus, WebhookEventStatus.REJECTED, null);
        throw new BusinessException(responseCode, responseMessage);
    }

    private DocumentWebhookEventResponse respondToDuplicateWebhookEvent(
            DocumentWebhookEvent duplicate,
            String payloadDigest
    ) {
        if (!payloadDigest.equals(duplicate.payloadDigest())) {
            throw new BusinessException(ErrorCode.CONFLICT, DocumentInputMessages.WEBHOOK_IDEMPOTENCY_KEY_CONFLICT);
        }
        return responseMapper.toWebhookEventResponse(duplicate);
    }

    /**
     * Processes one accepted webhook event after the ingress response has already returned.
     * The import batch is created inside the worker so webhook ingress only verifies security,
     * persists the event and publishes the accepted envelope.
     */
    @Transactional
    public DocumentWebhookEvent processWebhookEvent(UUID eventId) {
        if (!repository.markWebhookEventStatus(
                eventId,
                WebhookEventStatus.ACCEPTED,
                WebhookEventStatus.PROCESSING,
                Instant.now()
        )) {
            return repository.webhookEvent(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, DocumentInputMessages.WEBHOOK_EVENT_NOT_FOUND.formatted(eventId)));
        }
        DocumentWebhookEvent event = repository.webhookEvent(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, DocumentInputMessages.WEBHOOK_EVENT_NOT_FOUND.formatted(eventId)));
        String sourceCode = event.sourceCode();
        DocumentSourceConfig source = repository.sourceByCode(normalizeSourceCode(sourceCode))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        DocumentInputMessages.SOURCE_NOT_FOUND.formatted(sourceCode)
                ));
        String eventType = null;
        String eventVersion = null;
        String projectId = source.defaultProjectId();
        try {
            JsonNode payload = parsePayload(event.rawPayload());
            eventType = firstText(textAt(payload, "eventType"), textAt(payload, "type"), "requirement.created");
            eventVersion = firstText(event.eventVersion(), textAt(payload, "eventVersion"), textAt(payload, "version"));
            ensureSupportedWebhookEventType(eventType);
            ensureSupportedWebhookEventVersion(eventVersion);
            ensureSourceWebhookEventVersion(source, eventVersion);
            projectId = firstText(textAt(payload, "projectId"), source.defaultProjectId());
            if (!StringUtils.hasText(projectId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_MISSING_PROJECT_ID);
            }
            event = ensureWebhookImportQueued(event, source);
            DocumentImportRecord imported = importService.processQueuedImport(event.importId());
            if (imported.status() == DocumentImportStatus.FAILED) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        firstText(imported.errorMessage(), "webhook 导入解析失败")
                );
            }
            Instant processedAt = Instant.now();
            DocumentWebhookEvent processed = new DocumentWebhookEvent(
                    event.id(),
                    event.sourceId(),
                    imported.id(),
                    event.sourceCode(),
                    event.eventId(),
                    event.idempotencyKey(),
                    eventType,
                    eventVersion,
                    event.signatureStatus(),
                    event.replayAt() == null ? WebhookEventStatus.PROCESSED : WebhookEventStatus.REPLAYED,
                    event.payloadDigest(),
                    event.rawPayload(),
                    null,
                    event.retryCount(),
                    event.replayBy(),
                    event.replayAt(),
                    event.replayTraceId(),
                    event.receivedAt(),
                    processedAt
            );
            repository.saveWebhookEvent(processed);
            writeAudit(
                    processed.status() == WebhookEventStatus.REPLAYED ? "WEBHOOK_REPLAY" : "WEBHOOK_PROCESSED",
                    "DOCUMENT_WEBHOOK_EVENT",
                    processed.id().toString(),
                    projectId,
                    responseMapper.sanitizeWebhookEvent(processed)
            );
            metrics.recordWebhook(processed.signatureStatus(), processed.status(), processed.eventType());
            return processed;
        } catch (BusinessException exception) {
            if (event.importId() != null) {
                importService.failImport(event.importId(), exception.getMessage());
            }
            WebhookEventStatus failedStatus = event.retryCount() >= maxReplayAttempts()
                    ? WebhookEventStatus.DEAD_LETTER
                    : WebhookEventStatus.FAILED;
            Instant processedAt = Instant.now();
            DocumentWebhookEvent failed = new DocumentWebhookEvent(
                    event.id(),
                    event.sourceId(),
                    event.importId(),
                    event.sourceCode(),
                    event.eventId(),
                    event.idempotencyKey(),
                    eventType,
                    eventVersion,
                    event.signatureStatus(),
                    failedStatus,
                    event.payloadDigest(),
                    event.rawPayload(),
                    exception.getMessage(),
                    event.retryCount(),
                    event.replayBy(),
                    event.replayAt(),
                    event.replayTraceId(),
                    event.receivedAt(),
                    processedAt
            );
            repository.saveWebhookEvent(failed);
            writeAudit(
                    failed.status() == WebhookEventStatus.DEAD_LETTER
                            ? "WEBHOOK_DEAD_LETTER"
                            : "WEBHOOK_FAILED",
                    "DOCUMENT_WEBHOOK_EVENT",
                    failed.id().toString(),
                    projectId,
                    responseMapper.sanitizeWebhookEvent(failed)
            );
            metrics.recordWebhook(failed.signatureStatus(), failed.status(), failed.eventType());
            return failed;
        }
    }

    private DocumentWebhookEvent ensureWebhookImportQueued(DocumentWebhookEvent event, DocumentSourceConfig source) {
        if (event.importId() != null) {
            return event;
        }
        DocumentImportRecord queuedImport = queueWebhookImport(source, event.rawPayload());
        DocumentWebhookEvent updated = new DocumentWebhookEvent(
                event.id(),
                event.sourceId(),
                queuedImport.id(),
                event.sourceCode(),
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                event.eventVersion(),
                event.signatureStatus(),
                event.status(),
                event.payloadDigest(),
                event.rawPayload(),
                event.errorMessage(),
                event.retryCount(),
                event.replayBy(),
                event.replayAt(),
                event.replayTraceId(),
                event.receivedAt(),
                event.processedAt()
        );
        repository.saveWebhookEvent(updated);
        return updated;
    }

    private DocumentImportRecord queueWebhookImport(DocumentSourceConfig source, String rawPayload) {
        JsonNode payload = parsePayloadOrNull(rawPayload);
        String projectId = firstText(textAt(payload, "projectId"), source.defaultProjectId());
        if (!StringUtils.hasText(projectId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_MISSING_PROJECT_ID);
        }
        String title = firstText(textAt(payload, "title"), textAt(payload, "name"), source.name());
        String sourceRef = firstText(textAt(payload, "sourceRef"), textAt(payload, "id"), source.sourceCode());
        String sourceUrl = firstText(textAt(payload, "sourceUrl"), textAt(payload, "url"));
        return importService.queueWebhookImport(
                source,
                projectId,
                title,
                sourceRef,
                sourceUrl,
                rawPayload
        );
    }

    private String webhookEventType(String rawPayload) {
        JsonNode payload = parsePayloadOrNull(rawPayload);
        return firstText(textAt(payload, "eventType"), textAt(payload, "type"), "requirement.created");
    }

    private JsonNode parsePayloadOrNull(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception exception) {
            return null;
        }
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_INVALID_JSON);
        }
    }

    private void ensureRequiredWebhookHeaders(
            String timestamp,
            String signature,
            String eventId,
            String idempotencyKey,
            String eventVersion
    ) {
        if (!StringUtils.hasText(timestamp)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_MISSING_TIMESTAMP);
        }
        if (!StringUtils.hasText(signature)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_MISSING_SIGNATURE);
        }
        if (!StringUtils.hasText(eventId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_MISSING_EVENT_ID);
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_MISSING_IDEMPOTENCY_KEY);
        }
        if (!StringUtils.hasText(eventVersion)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_MISSING_EVENT_VERSION);
        }
    }

    private WebhookSignatureStatus validateWebhookSignature(
            DocumentSourceConfig source,
            String rawPayload,
            String timestamp,
            String signature,
            String eventId,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return WebhookSignatureStatus.MISSING;
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return WebhookSignatureStatus.INVALID;
        }
        long skew = properties.webhookClockSkewSeconds() <= 0 ? 300 : properties.webhookClockSkewSeconds();
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > skew) {
            return WebhookSignatureStatus.EXPIRED;
        }
        String expected = hmacSha256(webhookSigningSecret(source), String.join(".",
                timestamp.trim(),
                eventId.trim(),
                idempotencyKey.trim(),
                rawPayload == null ? "" : rawPayload
        ));
        return constantTimeEquals(expected, signature.trim())
                ? WebhookSignatureStatus.VALID
                : WebhookSignatureStatus.INVALID;
    }

    private String webhookSigningSecret(DocumentSourceConfig source) {
        return webhookSecretResolver.resolve(source);
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, DocumentInputMessages.WEBHOOK_SIGNATURE_VERIFICATION_FAILED);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private void ensureExecutableSource(DocumentSourceType sourceType, DocumentSourceStatus status) {
        if (!SUPPORTED_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.SOURCE_TYPE_NOT_IMPLEMENTED.formatted(sourceType));
        }
        if (status != DocumentSourceStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.SOURCE_NOT_ENABLED);
        }
    }

    private void ensureInputEnabled() {
        if (!properties.inputEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.INPUT_DISABLED);
        }
    }

    private void ensureWebhookEnabled() {
        if (!properties.webhookEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.WEBHOOK_INPUT_DISABLED);
        }
    }

    private void ensureSupportedWebhookEventType(String eventType) {
        if (!Set.of(
                "requirement.created",
                "requirement.updated",
                "requirement.statusChanged",
                "requirement.archived"
        ).contains(eventType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.UNSUPPORTED_EVENT_TYPE.formatted(eventType));
        }
    }

    private void ensureSupportedWebhookEventVersion(String eventVersion) {
        if (!StringUtils.hasText(eventVersion)
                || !SUPPORTED_WEBHOOK_EVENT_VERSIONS.contains(eventVersion.trim())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    DocumentInputMessages.UNSUPPORTED_EVENT_VERSION.formatted(eventVersion)
            );
        }
    }

    private void ensureSourceWebhookEventVersion(DocumentSourceConfig source, String eventVersion) {
        if (source.sourceType() != DocumentSourceType.CUSTOM_API) {
            return;
        }
        String configured = normalizeEventVersion(source.eventVersion());
        if (!configured.equals(eventVersion.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    DocumentInputMessages.WEBHOOK_EVENT_VERSION_MISMATCH.formatted(eventVersion));
        }
    }

    private long maxWebhookPayloadBytes() {
        return properties.webhookMaxPayloadBytes() <= 0 ? 262144 : properties.webhookMaxPayloadBytes();
    }

    private long maxImportContentBytes() {
        return properties.importMaxContentBytes() <= 0 ? 16777216 : properties.importMaxContentBytes();
    }

    private String webhookPayloadLimitMessage() {
        return DocumentInputMessages.WEBHOOK_PAYLOAD_EXCEEDS_LIMIT
                + maxWebhookPayloadBytes()
                + " bytes。下一步：缩减单次事件 payload 或联系管理员调整 "
                + "WP4_WEBHOOK_MAX_PAYLOAD_BYTES。";
    }

    private String webhookSignatureFailureMessage(WebhookSignatureStatus signatureStatus) {
        return switch (signatureStatus) {
            case MISSING -> DocumentInputMessages.WEBHOOK_SIGNATURE_MISSING_HINT
                    + "X-VA-Timestamp、X-VA-Signature、X-VA-Event-Id、X-VA-Idempotency-Key "
                    + "与 X-VA-Event-Version。";
            case EXPIRED -> DocumentInputMessages.WEBHOOK_SIGNATURE_EXPIRED_HINT
                    + "并确认请求在 WP4_WEBHOOK_CLOCK_SKEW_SECONDS 窗口内发送。";
            case INVALID -> DocumentInputMessages.WEBHOOK_SIGNATURE_INVALID_HINT
                    + "timestamp.eventId.idempotencyKey.rawBody 签名串和小写 hex 输出一致。";
            case VALID -> DocumentInputMessages.WEBHOOK_SIGNATURE_UNKNOWN_HINT
                    + "secretRef 配置。";
        };
    }

    private int batchActionLimit() {
        return properties.batchActionLimit() <= 0 ? 100 : properties.batchActionLimit();
    }

    private int maxReplayAttempts() {
        return properties.webhookMaxReplayAttempts() <= 0 ? 3 : properties.webhookMaxReplayAttempts();
    }

    private long payloadSize(String rawPayload) {
        return (rawPayload == null ? "" : rawPayload).getBytes(StandardCharsets.UTF_8).length;
    }

    private void writeAudit(
            String action,
            String resourceType,
            String resourceId,
            String scopeId,
            Object afterJson
    ) {
        contextClient.writeAuditEvent(
                action,
                resourceType,
                resourceId,
                scopeId,
                "SUCCEEDED",
                Map.of("resourceType", resourceType, "resourceId", resourceId, "after", afterJson)
        );
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导入内容摘要计算失败");
        }
    }

    private String textAt(JsonNode node, String path) {
        if (node == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            current = current.path(segment.trim());
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeSourceCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceCode 不能为空");
        }
        return value.trim();
    }

    private String normalizeEventVersion(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "1.0";
        ensureSupportedWebhookEventVersion(normalized);
        return normalized;
    }

}
