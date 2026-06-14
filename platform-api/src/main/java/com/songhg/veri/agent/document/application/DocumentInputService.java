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
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentInputService {

    private final DocumentInputRepository repository;
    private final DocumentSourceManagementService sourceManagementService;
    private final DocumentImportService importService;
    private final DocumentInputActorResolver actorResolver;
    private final DocumentCandidateWorkflowService candidateWorkflowService;
    private final DocumentRequirementPublishService publishService;
    private final DocumentInputPlatformContextClient contextClient;
    private final DocumentContentExtractor contentExtractor;
    private final DocumentInputConfiguration configuration;
    private final DocumentInputProperties properties;
    private final DocumentInputMetrics metrics;
    private final DocumentWebhookSecretResolver webhookSecretResolver;
    private final DocumentWebhookIngressGuard webhookIngressGuard;
    private final DocumentWebhookSupport webhookSupport;
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
            DocumentWebhookSupport webhookSupport,
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
        this.properties = properties;
        this.metrics = metrics;
        this.webhookSecretResolver = webhookSecretResolver;
        this.webhookIngressGuard = webhookIngressGuard;
        this.webhookSupport = webhookSupport;
        this.responseMapper = new DocumentInputResponseMapper(repository, objectMapper);
        this.eventPublisher = eventPublisher;
    }

    public int supportedSourceTypeCount() {
        return webhookSupport.supportedSourceTypeCount();
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
                webhookSupport.maxWebhookPayloadBytes(),
                webhookSupport.maxImportContentBytes(),
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
                webhookSupport.batchActionLimit(),
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
        DocumentSourceConfig source = repository.sourceByCode(webhookSupport.normalizeSourceCode(sourceCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, DocumentInputMessages.SOURCE_NOT_FOUND.formatted(sourceCode)));
        webhookSupport.ensureExecutableSource(source.sourceType(), source.status());
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
        String payloadDigest = webhookSupport.sha256(rawPayload);
        WebhookSignatureStatus signatureStatus = webhookSupport.validateSignature(
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
                        webhookSupport.webhookSignatureFailureMessage(signatureStatus),
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
            throw new BusinessException(ErrorCode.FORBIDDEN, webhookSupport.webhookSignatureFailureMessage(signatureStatus));
        }
        if (webhookSupport.payloadSize(rawPayload) > webhookSupport.maxWebhookPayloadBytes()) {
            String payloadLimitMessage = webhookSupport.webhookPayloadLimitMessage();
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
        String eventType = webhookSupport.eventTypeOrDefault(rawPayload);
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
                webhookSupport.sha256(rawPayload),
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
        DocumentSourceConfig source = repository.sourceByCode(webhookSupport.normalizeSourceCode(sourceCode))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        DocumentInputMessages.SOURCE_NOT_FOUND.formatted(sourceCode)
                ));
        String eventType = null;
        String eventVersion = null;
        String projectId = source.defaultProjectId();
        try {
            JsonNode payload = webhookSupport.parsePayload(event.rawPayload());
            eventType = webhookSupport.firstText(
                    webhookSupport.textAt(payload, "eventType"),
                    webhookSupport.textAt(payload, "type"),
                    "requirement.created"
            );
            eventVersion = webhookSupport.firstText(
                    event.eventVersion(),
                    webhookSupport.textAt(payload, "eventVersion"),
                    webhookSupport.textAt(payload, "version")
            );
            webhookSupport.ensureSupportedWebhookEventType(eventType);
            webhookSupport.ensureSupportedWebhookEventVersion(eventVersion);
            webhookSupport.ensureSourceWebhookEventVersion(source, eventVersion);
            projectId = webhookSupport.firstText(webhookSupport.textAt(payload, "projectId"), source.defaultProjectId());
            if (!StringUtils.hasText(projectId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_MISSING_PROJECT_ID);
            }
            event = ensureWebhookImportQueued(event, source);
            DocumentImportRecord imported = importService.processQueuedImport(event.importId());
            if (imported.status() == DocumentImportStatus.FAILED) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        webhookSupport.firstText(imported.errorMessage(), "webhook 导入解析失败")
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
            WebhookEventStatus failedStatus = event.retryCount() >= webhookSupport.maxReplayAttempts()
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
        JsonNode payload = webhookSupport.parsePayloadOrNull(rawPayload);
        String projectId = webhookSupport.firstText(webhookSupport.textAt(payload, "projectId"), source.defaultProjectId());
        if (!StringUtils.hasText(projectId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_MISSING_PROJECT_ID);
        }
        String title = webhookSupport.firstText(
                webhookSupport.textAt(payload, "title"),
                webhookSupport.textAt(payload, "name"),
                source.name()
        );
        String sourceRef = webhookSupport.firstText(
                webhookSupport.textAt(payload, "sourceRef"),
                webhookSupport.textAt(payload, "id"),
                source.sourceCode()
        );
        String sourceUrl = webhookSupport.firstText(
                webhookSupport.textAt(payload, "sourceUrl"),
                webhookSupport.textAt(payload, "url")
        );
        return importService.queueWebhookImport(
                source,
                projectId,
                title,
                sourceRef,
                sourceUrl,
                rawPayload
        );
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
