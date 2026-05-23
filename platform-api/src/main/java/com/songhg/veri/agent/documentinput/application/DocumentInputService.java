package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.documentinput.api.request.CandidateBatchActionRequest;
import com.songhg.veri.agent.documentinput.api.request.ConfirmDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.api.request.CreateDocumentImportRequest;
import com.songhg.veri.agent.documentinput.api.request.DocumentPublishRequest;
import com.songhg.veri.agent.documentinput.api.request.IgnoreDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.api.request.UpdateFieldMappingRequest;
import com.songhg.veri.agent.documentinput.api.request.UpdateDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.api.request.UpsertDocumentSourceRequest;
import com.songhg.veri.agent.documentinput.api.response.DocumentCandidateBatchActionResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentCandidateResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentImportResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentInputHealthResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentParseFeedbackSampleResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentPublishRecordResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentPublishResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentSecretProviderHealthResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentSourceHealthResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentSourceResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentWebhookEventResponse;
import com.songhg.veri.agent.documentinput.api.response.FieldMappingResponse;
import com.songhg.veri.agent.documentinput.api.response.ParsedRequirementResponse;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentImportStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.documentinput.domain.ParsedRequirementDraft;
import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;
import com.songhg.veri.agent.documentinput.domain.WebhookSignatureStatus;
import com.songhg.veri.agent.integration.application.PlatformContext;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final DocumentRequirementParser parser;
    private final DocumentModelRequirementParser modelParser;
    private final DocumentCandidateWorkflowService candidateWorkflowService;
    private final DocumentRequirementPublishService publishService;
    private final DocumentInputPlatformContextClient contextClient;
    private final DocumentContentExtractor contentExtractor;
    private final ObjectMapper objectMapper;
    private final DocumentInputProperties properties;
    private final DocumentInputMetrics metrics;
    private final DocumentWebhookSecretResolver webhookSecretResolver;
    private final DocumentWebhookIngressGuard webhookIngressGuard;

    public DocumentInputService(
            DocumentInputRepository repository,
            DocumentSourceManagementService sourceManagementService,
            DocumentRequirementParser parser,
            DocumentModelRequirementParser modelParser,
            DocumentCandidateWorkflowService candidateWorkflowService,
            DocumentRequirementPublishService publishService,
            DocumentInputPlatformContextClient contextClient,
            DocumentContentExtractor contentExtractor,
            ObjectMapper objectMapper,
            DocumentInputProperties properties,
            DocumentInputMetrics metrics,
            DocumentWebhookSecretResolver webhookSecretResolver,
            DocumentWebhookIngressGuard webhookIngressGuard
    ) {
        this.repository = repository;
        this.sourceManagementService = sourceManagementService;
        this.parser = parser;
        this.modelParser = modelParser;
        this.candidateWorkflowService = candidateWorkflowService;
        this.publishService = publishService;
        this.contextClient = contextClient;
        this.contentExtractor = contentExtractor;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.webhookSecretResolver = webhookSecretResolver;
        this.webhookIngressGuard = webhookIngressGuard;
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
                contentExtractor.documentBinaryMaxBytes(),
                contentExtractor.ocrConfigured(),
                contentExtractor.ocrTimeoutSeconds(),
                contentExtractor.ocrMaxOutputChars(),
                contentExtractor.ocrMaxConcurrentProcesses(),
                contentExtractor.ocrAvailablePermits(),
                contentExtractor.ocrWorkerMode(),
                contentExtractor.ocrRemoteWorkerConfigured(),
                contentExtractor.ocrWorkerTokenConfigured(),
                contentExtractor.ocrLocalCommandFallbackEnabled(),
                contentExtractor.ocrLocalCommandExecutionAllowed(),
                batchActionLimit(),
                webhookIngressGuard.ipAllowlistConfigured(),
                webhookIngressGuard.trustedProxyCidrsConfigured(),
                webhookIngressGuard.rateLimitEnabled(),
                webhookIngressGuard.rateLimitMaxRequests(),
                webhookIngressGuard.rateLimitWindowSeconds(),
                contentExtractor.binaryMimeValidationEnabled(),
                contentExtractor.pdfMaxPages(),
                contentExtractor.pdfMaxParseMillis(),
                contentExtractor.malwareScanEnabled(),
                contentExtractor.malwareScanTimeoutSeconds(),
                contentExtractor.malwareScanMaxConcurrentProcesses(),
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
        return importContent(
                request.projectId(),
                request.sourceType(),
                request.title(),
                request.sourceRef(),
                request.sourceUrl(),
                request.content(),
                request.mappingId(),
                request.sourceId(),
                null
        );
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
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        ensureImportBinarySize(fileBytes.length);
        String dataUrl = "data:%s;base64,%s".formatted(
                StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream",
                Base64.getEncoder().encodeToString(fileBytes)
        );
        return importContent(
                projectId,
                sourceType,
                firstText(title, filename),
                null,
                sourceRef,
                sourceUrl,
                dataUrl,
                mappingId,
                sourceId,
                null
        );
    }

    public PageResponse<DocumentImportResponse> imports(DocumentImportQuery query) {
        ensureInputEnabled();
        return PageResponse.of(
                repository.imports(query).stream().map(record -> toImportResponse(record, List.of())).toList(),
                query.index(),
                query.size(),
                repository.countImports(query)
        );
    }

    public DocumentImportResponse importRecord(UUID id) {
        ensureInputEnabled();
        return repository.importRecord(id)
                .map(record -> toImportResponse(record, List.of()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + id));
    }

    public PageResponse<DocumentCandidateResponse> candidates(DocumentCandidateQuery query) {
        ensureInputEnabled();
        repository.importRecord(query.importId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + query.importId()));
        return PageResponse.of(
                repository.candidates(query).stream()
                        .map(DocumentInputService::toCandidateResponse)
                        .toList(),
                query.index(),
                query.size(),
                repository.countCandidates(query)
        );
    }

    public PageResponse<DocumentParseFeedbackSampleResponse> parseFeedbackSamples(DocumentParseFeedbackQuery query) {
        ensureInputEnabled();
        List<DocumentParseFeedbackSampleResponse> items = repository.parseFeedbackSamples(query).stream()
                .map(this::toParseFeedbackSampleResponse)
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
                repository.webhookEvents(query).stream().map(DocumentInputService::toWebhookEventResponse).toList(),
                query.index(),
                query.size(),
                repository.countWebhookEvents(query)
        );
    }

    public DocumentWebhookEventResponse webhookEvent(UUID id) {
        ensureInputEnabled();
        return repository.webhookEvent(id)
                .map(DocumentInputService::toWebhookEventResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "webhook 事件不存在: " + id));
    }

    public DocumentWebhookEventResponse replayWebhookEvent(UUID id) {
        ensureInputEnabled();
        DocumentWebhookEvent event = repository.webhookEvent(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "webhook 事件不存在: " + id));
        if (event.rawPayload() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 原始 payload 不可重放");
        }
        if (event.signatureStatus() != WebhookSignatureStatus.VALID) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "签名未通过的 webhook 事件不可重放");
        }
        if (event.status() != WebhookEventStatus.FAILED && event.status() != WebhookEventStatus.DEAD_LETTER) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅失败或死信 webhook 事件允许重放");
        }
        DocumentWebhookEvent replayed = processWebhookEvent(event, event.rawPayload(), true);
        return toWebhookEventResponse(replayed);
    }

    public DocumentImportResponse handleWebhook(
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档源不存在: " + sourceCode));
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
                    "webhook 来源 IP 不在白名单",
                    "webhook 来源 IP 不在白名单: " + clientIp
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
                    "webhook 请求过于频繁",
                    "webhook 请求过于频繁: dimension=%s, limit=%d, windowSeconds=%d, remoteIp=%s".formatted(
                            rateLimit.dimension(),
                            rateLimit.limit(),
                            rateLimit.windowSeconds(),
                            clientIp
                    )
            );
        }
        String payloadDigest = sha256(rawPayload);
        WebhookSignatureStatus signatureStatus = validateWebhookSignature(source, rawPayload, timestamp, signature, eventId, idempotencyKey);
        DocumentWebhookEvent duplicate = repository.webhookEventByIdentity(source.sourceCode(), trimToNull(eventId), trimToNull(idempotencyKey))
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
                writeAudit("WEBHOOK_REJECTED", "DOCUMENT_WEBHOOK_EVENT", rejected.id().toString(), source.defaultProjectId(), sanitizeWebhookEvent(rejected));
                metrics.recordWebhook(signatureStatus, WebhookEventStatus.REJECTED, null);
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, webhookSignatureFailureMessage(signatureStatus));
        }
        if (payloadSize(rawPayload) > maxWebhookPayloadBytes()) {
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
                    "webhook payload 超过上限: " + maxWebhookPayloadBytes() + " bytes。下一步：缩减单次事件 payload 或联系管理员调整 WP4_WEBHOOK_MAX_PAYLOAD_BYTES。",
                    0,
                    null,
                    null,
                    null,
                    rejectedAt,
                    null
            );
            repository.saveWebhookEvent(rejected);
            writeAudit("WEBHOOK_REJECTED", "DOCUMENT_WEBHOOK_EVENT", rejected.id().toString(), source.defaultProjectId(), sanitizeWebhookEvent(rejected));
            metrics.recordWebhook(signatureStatus, WebhookEventStatus.REJECTED, null);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "webhook payload 超过上限: " + maxWebhookPayloadBytes() + " bytes。下一步：缩减单次事件 payload 或联系管理员调整 WP4_WEBHOOK_MAX_PAYLOAD_BYTES。");
        }
        if (duplicate != null) {
            return respondToDuplicateWebhookEvent(duplicate, payloadDigest);
        }
        Instant now = Instant.now();
        DocumentWebhookEvent accepted = new DocumentWebhookEvent(
                UUID.randomUUID(),
                source.id(),
                null,
                source.sourceCode(),
                trimToNull(eventId),
                trimToNull(idempotencyKey),
                null,
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
        DocumentWebhookEvent processed = processWebhookEvent(saved, rawPayload, false);
        metrics.recordWebhook(processed.signatureStatus(), processed.status(), processed.eventType());
        return importRecord(processed.importId());
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
        writeAudit("WEBHOOK_REJECTED", "DOCUMENT_WEBHOOK_EVENT", rejected.id().toString(), source.defaultProjectId(), sanitizeWebhookEvent(rejected));
        metrics.recordWebhook(signatureStatus, WebhookEventStatus.REJECTED, null);
        throw new BusinessException(responseCode, responseMessage);
    }

    private DocumentImportResponse respondToDuplicateWebhookEvent(DocumentWebhookEvent duplicate, String payloadDigest) {
        if (!payloadDigest.equals(duplicate.payloadDigest())) {
            throw new BusinessException(ErrorCode.CONFLICT, "webhook 幂等键已使用但 payload 不一致");
        }
        if (duplicate.importId() != null) {
            return importRecord(duplicate.importId());
        }
        throw new BusinessException(ErrorCode.CONFLICT, "webhook 事件已接收但未成功处理");
    }

    private DocumentWebhookEvent processWebhookEvent(DocumentWebhookEvent event, String rawPayload, boolean replay) {
        DocumentSourceConfig source = repository.sourceByCode(normalizeSourceCode(event.sourceCode()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档源不存在: " + event.sourceCode()));
        String eventType = null;
        String eventVersion = null;
        String projectId = source.defaultProjectId();
        try {
            JsonNode payload = parsePayload(rawPayload);
            eventType = firstText(textAt(payload, "eventType"), textAt(payload, "type"), "requirement.created");
            eventVersion = firstText(event.eventVersion(), textAt(payload, "eventVersion"), textAt(payload, "version"));
            ensureSupportedWebhookEventType(eventType);
            ensureSupportedWebhookEventVersion(eventVersion);
            ensureSourceWebhookEventVersion(source, eventVersion);
            projectId = firstText(textAt(payload, "projectId"), source.defaultProjectId());
            if (!StringUtils.hasText(projectId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook payload 缺少 projectId");
            }
            String title = firstText(textAt(payload, "title"), textAt(payload, "name"), source.name());
            String sourceRef = firstText(textAt(payload, "sourceRef"), textAt(payload, "id"), source.sourceCode());
            String sourceUrl = firstText(textAt(payload, "sourceUrl"), textAt(payload, "url"));
            DocumentImportResponse imported = importContent(
                    projectId,
                    source.sourceType(),
                    title,
                    sourceRef,
                    sourceUrl,
                    payload.toString(),
                    source.mappingId(),
                    source.id(),
                    source.sourceCode()
            );
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
                    replay ? WebhookEventStatus.REPLAYED : WebhookEventStatus.PROCESSED,
                    event.payloadDigest(),
                    event.rawPayload(),
                    null,
                    replay ? event.retryCount() + 1 : event.retryCount(),
                    replay ? currentActor() : event.replayBy(),
                    replay ? processedAt : event.replayAt(),
                    replay ? TraceContext.getTraceId() : event.replayTraceId(),
                    event.receivedAt(),
                    processedAt
            );
            repository.saveWebhookEvent(processed);
            writeAudit(replay ? "WEBHOOK_REPLAY" : "WEBHOOK_PROCESSED", "DOCUMENT_WEBHOOK_EVENT", processed.id().toString(), projectId, sanitizeWebhookEvent(processed));
            return processed;
        } catch (BusinessException exception) {
            int retryCount = replay ? event.retryCount() + 1 : event.retryCount();
            WebhookEventStatus failedStatus = retryCount >= maxReplayAttempts()
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
                    retryCount,
                    replay ? currentActor() : event.replayBy(),
                    replay ? processedAt : event.replayAt(),
                    replay ? TraceContext.getTraceId() : event.replayTraceId(),
                    event.receivedAt(),
                    processedAt
            );
            repository.saveWebhookEvent(failed);
            writeAudit(failed.status() == WebhookEventStatus.DEAD_LETTER ? "WEBHOOK_DEAD_LETTER" : "WEBHOOK_FAILED",
                    "DOCUMENT_WEBHOOK_EVENT", failed.id().toString(), projectId, sanitizeWebhookEvent(failed));
            metrics.recordWebhook(failed.signatureStatus(), failed.status(), failed.eventType());
            throw exception;
        }
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook payload 不是合法 JSON");
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook 缺少 X-VA-Timestamp");
        }
        if (!StringUtils.hasText(signature)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook 缺少 X-VA-Signature");
        }
        if (!StringUtils.hasText(eventId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook 缺少 X-VA-Event-Id");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook 缺少 X-VA-Idempotency-Key");
        }
        if (!StringUtils.hasText(eventVersion)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook 缺少 X-VA-Event-Version");
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
        return constantTimeEquals(expected, signature.trim()) ? WebhookSignatureStatus.VALID : WebhookSignatureStatus.INVALID;
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "webhook 签名校验失败");
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

    private String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ServicePrincipal servicePrincipal && StringUtils.hasText(servicePrincipal.delegatedUserId())) {
            return servicePrincipal.delegatedUserId();
        }
        if (principal instanceof AuthUserPrincipal userPrincipal) {
            return userPrincipal.userId() == null ? userPrincipal.username() : userPrincipal.userId().toString();
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) ? name : "system";
    }

    private DocumentImportResponse importContent(
            String projectId,
            DocumentSourceType sourceType,
            String title,
            String sourceRef,
            String sourceUrl,
            String content,
            UUID mappingId,
            UUID sourceId,
            String sourceCode
    ) {
        return importContent(projectId, sourceType, title, title, sourceRef, sourceUrl, content, mappingId, sourceId, sourceCode);
    }

    private DocumentImportResponse importContent(
            String projectId,
            DocumentSourceType sourceType,
            String recordTitle,
            String parseFallbackTitle,
            String sourceRef,
            String sourceUrl,
            String content,
            UUID mappingId,
            UUID sourceId,
            String sourceCode
    ) {
        ensureInputEnabled();
        ensureExecutableSource(sourceType, DocumentSourceStatus.ENABLED);
        PlatformContext context = validateProject(projectId);
        DocumentSourceConfig source = sourceId == null ? null : repository.source(sourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档源不存在: " + sourceId));
        if (source != null) {
            ensureExecutableSource(source.sourceType(), source.status());
            if (source.sourceType() != sourceType) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceType 与文档源配置不一致");
            }
        }
        DocumentFieldMapping mapping = mappingOrDefault(firstNonNull(mappingId, source == null ? null : source.mappingId()));
        UUID importId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            ensureImportContentSize(content);
            DocumentContentExtractor.ExtractedDocumentContent extracted = contentExtractor.extract(sourceType, content);
            List<ParsedRequirementDraft> parsed = parseRequirements(
                    context.resourceId(),
                    sourceType,
                    parseFallbackTitle,
                    sourceRef,
                    sourceUrl,
                    extracted.text(),
                    mapping
            );
            if (parsed.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未解析到有效需求");
            }
            DocumentImportRecord record = new DocumentImportRecord(
                    importId,
                    context.resourceId(),
                    sourceId,
                    firstText(sourceCode, source == null ? null : source.sourceCode()),
                    sourceType,
                    trimToNull(sourceRef),
                    trimToNull(sourceUrl),
                    trimToNull(recordTitle),
                    DocumentImportStatus.SUCCEEDED,
                    parsed.size(),
                    0,
                    "[]",
                    null,
                    sha256(content),
                    now,
                    Instant.now()
            );
            repository.saveImport(record);
            for (int index = 0; index < parsed.size(); index++) {
                repository.saveCandidate(toCandidate(record, parsed.get(index), sourceRef, index, now));
            }
            writeAudit("IMPORT", "DOCUMENT_IMPORT", record.id().toString(), context.resourceId(), record);
            metrics.recordImport(sourceType, record.status(), parsed.size());
            return toImportResponse(record, parsed);
        } catch (BusinessException exception) {
            DocumentImportRecord failed = new DocumentImportRecord(
                    importId,
                    context.resourceId(),
                    sourceId,
                    firstText(sourceCode, source == null ? null : source.sourceCode()),
                    sourceType,
                    trimToNull(sourceRef),
                    trimToNull(sourceUrl),
                    trimToNull(recordTitle),
                    DocumentImportStatus.FAILED,
                    0,
                    0,
                    "[]",
                    exception.getMessage(),
                    sha256(content),
                    now,
                    Instant.now()
            );
            repository.saveImport(failed);
            writeAudit("IMPORT_FAILED", "DOCUMENT_IMPORT", failed.id().toString(), context.resourceId(), failed);
            metrics.recordImport(sourceType, failed.status(), 0);
            throw exception;
        }
    }

    private List<ParsedRequirementDraft> parseRequirements(
            String projectId,
            DocumentSourceType sourceType,
            String title,
            String sourceRef,
            String sourceUrl,
            String content,
            DocumentFieldMapping mapping
    ) {
        List<ParsedRequirementDraft> ruleParsed = List.of();
        BusinessException ruleFailure = null;
        try {
            ruleParsed = parser.parse(sourceType, title, content, mapping);
        } catch (BusinessException exception) {
            ruleFailure = exception;
        }

        DocumentModelParseResult modelResult = modelParser.parse(
                projectId,
                sourceType,
                title,
                sourceRef,
                sourceUrl,
                content,
                currentActor()
        );
        if (modelResult.succeeded()) {
            metrics.recordModelParse(ruleParsed.isEmpty() ? "MODEL_ONLY" : "MODEL_WITH_RULE_MERGE", modelResult.drafts().size());
            writeAudit("MODEL_PARSE", "DOCUMENT_IMPORT", "pending", projectId, Map.of(
                    "result", "SUCCEEDED",
                    "invocationId", stringOrEmpty(modelResult.invocationId()),
                    "providerName", stringOrEmpty(modelResult.providerName()),
                    "modelName", stringOrEmpty(modelResult.modelName()),
                    "candidateCount", modelResult.drafts().size()
            ));
            return mergeParsedRequirements(ruleParsed, modelResult.drafts());
        }

        if (modelResult.attempted()) {
            metrics.recordModelParse(ruleParsed.isEmpty() ? "FAILED" : "FALLBACK_RULE", 0);
            writeAudit("MODEL_PARSE", "DOCUMENT_IMPORT", "pending", projectId, Map.of(
                    "result", ruleParsed.isEmpty() ? "FAILED" : "FALLBACK_RULE",
                    "invocationId", stringOrEmpty(modelResult.invocationId()),
                    "providerName", stringOrEmpty(modelResult.providerName()),
                    "modelName", stringOrEmpty(modelResult.modelName()),
                    "errorCode", stringOrEmpty(modelResult.errorCode()),
                    "errorMessage", stringOrEmpty(modelResult.errorMessage())
            ));
        }

        if (!ruleParsed.isEmpty()) {
            return ruleParsed;
        }
        if (modelResult.attempted() && StringUtils.hasText(modelResult.errorMessage())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 文档解析失败且规则解析无结果: " + modelResult.errorMessage());
        }
        if (ruleFailure != null) {
            throw ruleFailure;
        }
        return List.of();
    }

    private List<ParsedRequirementDraft> mergeParsedRequirements(
            List<ParsedRequirementDraft> ruleParsed,
            List<ParsedRequirementDraft> modelParsed
    ) {
        Map<String, ParsedRequirementDraft> merged = new LinkedHashMap<>();
        modelParsed.forEach(draft -> addMergedDraft(merged, draft));
        ruleParsed.forEach(draft -> addMergedDraft(merged, draft));
        return new ArrayList<>(merged.values());
    }

    private void addMergedDraft(Map<String, ParsedRequirementDraft> merged, ParsedRequirementDraft draft) {
        if (draft == null || !StringUtils.hasText(draft.title())) {
            return;
        }
        String key = draft.title().trim().toLowerCase(Locale.ROOT);
        merged.putIfAbsent(key, draft);
    }

    private DocumentRequirementCandidate toCandidate(
            DocumentImportRecord record,
            ParsedRequirementDraft draft,
            String sourceRef,
            int index,
            Instant now
    ) {
        return new DocumentRequirementCandidate(
                UUID.randomUUID(),
                record.id(),
                record.projectId(),
                draft.title(),
                trimToNull(draft.description()),
                normalizePriority(draft.priority()),
                trimToNull(draft.acceptanceCriteria()),
                mergeTags(draft.tags(), record.sourceType().name().toLowerCase(Locale.ROOT)),
                DocumentCandidateStatus.PENDING,
                trimToNull(sourceRef),
                draft.description(),
                externalRequirementId(record, sourceRef, index),
                "MODEL".equalsIgnoreCase(draft.parseSource()) ? 0.86 : 0.72,
                StringUtils.hasText(draft.parseSource()) ? draft.parseSource() : "RULE",
                draft.modelInvocationId(),
                draft.modelProviderName(),
                draft.modelName(),
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now
        );
    }

    private String externalRequirementId(DocumentImportRecord record, String sourceRef, int index) {
        String base = firstText(sourceRef, record.sourceCode(), record.id().toString());
        return base + "#" + (index + 1);
    }

    private DocumentFieldMapping mappingOrDefault(UUID mappingId) {
        if (mappingId == null) {
            return repository.defaultFieldMapping();
        }
        return repository.fieldMapping(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "字段映射不存在: " + mappingId));
    }

    private void ensureExecutableSource(DocumentSourceType sourceType, DocumentSourceStatus status) {
        if (!SUPPORTED_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, sourceType + " 数据流尚未实现");
        }
        if (status != DocumentSourceStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "文档源未启用");
        }
    }

    private PlatformContext validateProject(String projectId) {
        PlatformContext context = contextClient.projectContext(projectId);
        if (!"ACTIVE".equals(context.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许导入需求: " + projectId);
        }
        return context;
    }

    private void ensureInputEnabled() {
        if (!properties.inputEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP4 文档输入已关闭");
        }
    }

    private void ensureWebhookEnabled() {
        if (!properties.webhookEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP4 webhook 输入已关闭");
        }
    }

    private void ensureSupportedWebhookEventType(String eventType) {
        if (!Set.of(
                "requirement.created",
                "requirement.updated",
                "requirement.statusChanged",
                "requirement.archived"
        ).contains(eventType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 webhook eventType: " + eventType);
        }
    }

    private void ensureSupportedWebhookEventVersion(String eventVersion) {
        if (!StringUtils.hasText(eventVersion) || !SUPPORTED_WEBHOOK_EVENT_VERSIONS.contains(eventVersion.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 webhook eventVersion: " + eventVersion);
        }
    }

    private void ensureSourceWebhookEventVersion(DocumentSourceConfig source, String eventVersion) {
        if (source.sourceType() != DocumentSourceType.CUSTOM_API) {
            return;
        }
        String configured = normalizeEventVersion(source.eventVersion());
        if (!configured.equals(eventVersion.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "webhook eventVersion 与文档源配置不一致: " + eventVersion);
        }
    }

    private long maxWebhookPayloadBytes() {
        return properties.webhookMaxPayloadBytes() <= 0 ? 262144 : properties.webhookMaxPayloadBytes();
    }

    private long maxImportContentBytes() {
        return properties.importMaxContentBytes() <= 0 ? 16777216 : properties.importMaxContentBytes();
    }

    private void ensureImportContentSize(String content) {
        long size = payloadSize(content);
        long limit = maxImportContentBytes();
        if (size > limit) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "导入内容超过上限: " + limit + " bytes。下一步：拆分文档或联系管理员调整 WP4_IMPORT_MAX_CONTENT_BYTES。");
        }
    }

    private void ensureImportBinarySize(long size) {
        long limit = Math.min(maxImportContentBytes(), contentExtractor.documentBinaryMaxBytes());
        if (size > limit) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "上传文件超过上限: " + limit + " bytes。下一步：压缩或拆分文件，或联系管理员调整 WP4_IMPORT_MAX_CONTENT_BYTES / WP4_DOCUMENT_BINARY_MAX_BYTES。");
        }
    }

    private String webhookSignatureFailureMessage(WebhookSignatureStatus signatureStatus) {
        return switch (signatureStatus) {
            case MISSING -> "webhook 签名缺失。下一步：确认外部系统和网关转发 X-VA-Timestamp、X-VA-Signature、X-VA-Event-Id、X-VA-Idempotency-Key 与 X-VA-Event-Version。";
            case EXPIRED -> "webhook 签名已过期。下一步：校准外部系统和平台服务器时间，并确认请求在 WP4_WEBHOOK_CLOCK_SKEW_SECONDS 窗口内发送。";
            case INVALID -> "webhook 签名无效。下一步：确认 secretRef/WP4_WEBHOOK_SECRET、raw body、timestamp.eventId.idempotencyKey.rawBody 签名串和小写 hex 输出一致。";
            case VALID -> "webhook 签名无效或已过期。下一步：检查签名串、时间窗口和 secretRef 配置。";
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

    private void writeAudit(String action, String resourceType, String resourceId, String scopeId, Object afterJson) {
        contextClient.writeAuditEvent(
                action,
                resourceType,
                resourceId,
                scopeId,
                "SUCCEEDED",
                Map.of("resourceType", resourceType, "resourceId", resourceId, "after", afterJson)
        );
    }

    private String requirementIdsJson(List<UUID> requirementIds) {
        try {
            return objectMapper.writeValueAsString(requirementIds);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导入记录无法序列化");
        }
    }

    private List<UUID> requirementIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(UUID.class).readValue(json);
        } catch (Exception exception) {
            return List.of();
        }
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

    private String sha256OrNull(String value) {
        return StringUtils.hasText(value) ? sha256(value.trim()) : null;
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

    private DocumentImportResponse toImportResponse(DocumentImportRecord record, List<ParsedRequirementDraft> requirements) {
        List<DocumentRequirementCandidate> candidates = repository.candidates(record.id(), 0, 10000);
        return new DocumentImportResponse(
                record.id(),
                record.projectId(),
                record.sourceId(),
                record.sourceCode(),
                record.sourceType(),
                record.sourceRef(),
                record.sourceUrl(),
                record.title(),
                record.status(),
                record.totalParsed(),
                record.totalCreated(),
                requirementIds(record.createdRequirementIds()),
                requirements.stream().map(DocumentInputService::toRequirementResponse).toList(),
                countCandidates(candidates, DocumentCandidateStatus.PENDING),
                countCandidates(candidates, DocumentCandidateStatus.CONFIRMED),
                countCandidates(candidates, DocumentCandidateStatus.PUBLISHED),
                countCandidates(candidates, DocumentCandidateStatus.PUBLISH_FAILED),
                record.errorMessage(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private static long countCandidates(List<DocumentRequirementCandidate> candidates, DocumentCandidateStatus status) {
        return candidates.stream().filter(candidate -> candidate.status() == status).count();
    }

    private static DocumentCandidateResponse toCandidateResponse(DocumentRequirementCandidate candidate) {
        return new DocumentCandidateResponse(
                candidate.id(),
                candidate.importId(),
                candidate.projectId(),
                candidate.title(),
                candidate.description(),
                candidate.priority(),
                candidate.acceptanceCriteria(),
                candidate.tags(),
                candidate.status(),
                candidate.sourceRef(),
                candidate.sourceFragment(),
                candidate.externalRequirementId(),
                candidate.confidence(),
                candidate.parseSource(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                candidate.assetRequirementId(),
                candidate.errorMessage(),
                candidate.ignoredReason(),
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }

    private DocumentParseFeedbackSampleResponse toParseFeedbackSampleResponse(DocumentParseFeedbackSample sample) {
        return new DocumentParseFeedbackSampleResponse(
                sample.id(),
                sample.candidateId(),
                sample.importId(),
                sample.projectId(),
                sample.sourceType(),
                sample.inputDigest(),
                sample.sourceRefDigest(),
                sample.sourceFragmentDigest(),
                sample.parseSource(),
                sample.modelInvocationId(),
                sample.modelProviderName(),
                sample.modelName(),
                sample.correctionType(),
                sample.changedFields(),
                feedbackSnapshotNode(sample.beforeSnapshotJson()),
                feedbackSnapshotNode(sample.afterSnapshotJson()),
                sample.curationStatus(),
                sample.createdBy(),
                sample.createdAt(),
                sample.updatedAt()
        );
    }

    private JsonNode feedbackSnapshotNode(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private static DocumentWebhookEventResponse toWebhookEventResponse(DocumentWebhookEvent event) {
        return new DocumentWebhookEventResponse(
                event.id(),
                event.sourceId(),
                event.importId(),
                event.sourceCode(),
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                event.eventVersion(),
                event.signatureStatus(),
                event.status(),
                event.payloadDigest(),
                event.errorMessage(),
                event.retryCount(),
                event.replayBy(),
                event.replayAt(),
                event.replayTraceId(),
                event.receivedAt(),
                event.processedAt()
        );
    }

    private static DocumentWebhookEvent sanitizeWebhookEvent(DocumentWebhookEvent event) {
        return new DocumentWebhookEvent(
                event.id(),
                event.sourceId(),
                event.importId(),
                event.sourceCode(),
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                event.eventVersion(),
                event.signatureStatus(),
                event.status(),
                event.payloadDigest(),
                null,
                event.errorMessage(),
                event.retryCount(),
                event.replayBy(),
                event.replayAt(),
                event.replayTraceId(),
                event.receivedAt(),
                event.processedAt()
        );
    }

    private static ParsedRequirementResponse toRequirementResponse(ParsedRequirementDraft draft) {
        return new ParsedRequirementResponse(
                draft.title(),
                draft.description(),
                draft.priority(),
                draft.acceptanceCriteria(),
                draft.tags(),
                draft.assetRequirementId(),
                draft.parseSource(),
                draft.modelInvocationId(),
                draft.modelProviderName(),
                draft.modelName()
        );
    }

    private UUID firstNonNull(UUID first, UUID second) {
        return first == null ? second : first;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private String normalizePriority(String value) {
        if (!StringUtils.hasText(value)) {
            return "MEDIUM";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "CRITICAL", "BLOCKER" -> "CRITICAL";
            case "P1", "HIGH" -> "HIGH";
            case "P3", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private String mergeTags(String existing, String... tags) {
        StringBuilder value = new StringBuilder(StringUtils.hasText(existing) ? existing.trim() : "");
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            if (!value.isEmpty()) {
                value.append(',');
            }
            value.append(tag.trim());
        }
        return value.toString();
    }
}
