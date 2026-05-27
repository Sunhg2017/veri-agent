package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.document.application.command.CreateDocumentImportRequest;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.query.DocumentImportQuery;
import com.songhg.veri.agent.document.application.view.DocumentImportResponse;
import com.songhg.veri.agent.document.application.view.DocumentInputMetrics;
import com.songhg.veri.agent.document.application.view.DocumentModelParseResult;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentFieldMapping;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportPayload;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.ParsedRequirementDraft;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns document import orchestration, parsing fallback and candidate persistence.
 */
@Service
public class DocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportService.class);

    private static final Set<DocumentSourceType> SUPPORTED_SOURCE_TYPES = Set.of(
            DocumentSourceType.TEXT,
            DocumentSourceType.MARKDOWN,
            DocumentSourceType.WORD,
            DocumentSourceType.PDF,
            DocumentSourceType.OCR,
            DocumentSourceType.CUSTOM_API
    );

    private final DocumentInputRepository repository;
    private final DocumentRequirementParser parser;
    private final DocumentModelRequirementParser modelParser;
    private final DocumentInputPlatformContextClient contextClient;
    private final DocumentContentExtractor contentExtractor;
    private final DocumentInputProperties properties;
    private final DocumentInputMetrics metrics;
    private final DocumentInputActorResolver actorResolver;
    private final DocumentInputResponseMapper responseMapper;
    private final DocumentInputEventPublisher eventPublisher;

    public DocumentImportService(
            DocumentInputRepository repository,
            DocumentRequirementParser parser,
            DocumentModelRequirementParser modelParser,
            DocumentInputPlatformContextClient contextClient,
            DocumentContentExtractor contentExtractor,
            ObjectMapper objectMapper,
            DocumentInputProperties properties,
            DocumentInputMetrics metrics,
            DocumentInputActorResolver actorResolver,
            DocumentInputEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.parser = parser;
        this.modelParser = modelParser;
        this.contextClient = contextClient;
        this.contentExtractor = contentExtractor;
        this.properties = properties;
        this.metrics = metrics;
        this.actorResolver = actorResolver;
        this.responseMapper = new DocumentInputResponseMapper(repository, objectMapper);
        this.eventPublisher = eventPublisher;
    }

    /**
     * Accepts text-like document content and emits an async parse event.
     */
    @Transactional
    public DocumentImportResponse importDocument(CreateDocumentImportRequest request) {
        return queueImportContent(
                request.projectId(),
                request.sourceType(),
                request.title(),
                request.title(),
                request.sourceRef(),
                request.sourceUrl(),
                request.content(),
                request.mappingId(),
                request.sourceId(),
                null,
                true
        );
    }

    /**
     * Converts uploaded binary content into a data URL before using the common import pipeline.
     */
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
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        ensureImportBinarySize(fileBytes.length);
        String dataUrl = "data:%s;base64,%s".formatted(
                StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream",
                Base64.getEncoder().encodeToString(fileBytes)
        );
        return queueImportContent(
                projectId,
                sourceType,
                firstText(title, filename),
                null,
                sourceRef,
                sourceUrl,
                dataUrl,
                mappingId,
                sourceId,
                null,
                true
        );
    }

    public PageResponse<DocumentImportResponse> imports(DocumentImportQuery query) {
        return PageResponse.of(
                repository.imports(query).stream()
                        .map(record -> responseMapper.toImportResponse(record, List.of()))
                        .toList(),
                query.index(),
                query.size(),
                repository.countImports(query)
        );
    }

    public DocumentImportResponse importRecord(UUID id) {
        return repository.importRecord(id)
                .map(record -> responseMapper.toImportResponse(record, List.of()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + id));
    }

    /**
     * Creates a queued import for a validated webhook event. The webhook worker owns event validation
     * and calls {@link #processQueuedImport(UUID)} so ingress does not block on parsing or WP2 calls.
     */
    @Transactional
    DocumentImportRecord queueWebhookImport(
            DocumentSourceConfig source,
            String projectId,
            String title,
            String sourceRef,
            String sourceUrl,
            String content
    ) {
        return queueImportRecord(
                projectId,
                source.sourceType(),
                firstText(title, source.name()),
                firstText(title, source.name()),
                sourceRef,
                sourceUrl,
                content,
                source.mappingId(),
                source.id(),
                source.sourceCode()
        );
    }

    /**
     * Executes an import event. The conditional status claim makes duplicate local/Kafka delivery idempotent.
     */
    @Transactional
    public DocumentImportRecord processQueuedImport(UUID importId) {
        Instant startedAt = Instant.now();
        if (!repository.markImportStatus(
                importId,
                DocumentImportStatus.MODEL_PARSE_QUEUED,
                DocumentImportStatus.MODEL_PARSE_RUNNING,
                startedAt
        )) {
            return repository.importRecord(importId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + importId));
        }
        DocumentImportRecord running = repository.importRecord(importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + importId));
        DocumentImportPayload payload = repository.importPayload(importId).orElse(null);
        if (payload == null) {
            return failImport(running, "导入原始内容不存在，无法异步解析");
        }
        try {
            ensureImportContentSize(payload.content());
            DocumentFieldMapping mapping = mappingOrDefault(payload.mappingId());
            DocumentContentExtractor.ExtractedDocumentContent extracted =
                    contentExtractor.extract(running.sourceType(), payload.content());
            List<ParsedRequirementDraft> parsed = parseRequirements(
                    running.id(),
                    running.projectId(),
                    running.sourceType(),
                    payload.parseFallbackTitle(),
                    running.sourceRef(),
                    running.sourceUrl(),
                    extracted.text(),
                    mapping
            );
            if (parsed.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未解析到有效需求");
            }
            Instant now = Instant.now();
            DocumentImportRecord succeeded = new DocumentImportRecord(
                    running.id(),
                    running.projectId(),
                    running.sourceId(),
                    running.sourceCode(),
                    running.sourceType(),
                    running.sourceRef(),
                    running.sourceUrl(),
                    running.title(),
                    DocumentImportStatus.SUCCEEDED,
                    parsed.size(),
                    running.totalCreated(),
                    running.createdRequirementIds(),
                    null,
                    running.rawDigest(),
                    running.createdAt(),
                    now
            );
            repository.saveImport(succeeded);
            for (int index = 0; index < parsed.size(); index++) {
                repository.saveCandidate(toCandidate(succeeded, parsed.get(index), succeeded.sourceRef(), index, now));
            }
            writeAudit("IMPORT", "DOCUMENT_IMPORT", succeeded.id().toString(), succeeded.projectId(), succeeded);
            metrics.recordImport(succeeded.sourceType(), succeeded.status(), parsed.size());
            log.info("Document import event processed, import_id={}, parsed_count={}", importId, parsed.size());
            return succeeded;
        } catch (BusinessException exception) {
            return failImport(running, exception.getMessage());
        } catch (RuntimeException exception) {
            return failImport(running, firstText(exception.getMessage(), "文档异步解析失败"));
        }
    }

    @Transactional
    DocumentImportRecord failImport(UUID importId, String errorMessage) {
        DocumentImportRecord record = repository.importRecord(importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + importId));
        return failImport(record, errorMessage);
    }

    private DocumentImportResponse queueImportContent(
            String projectId,
            DocumentSourceType sourceType,
            String recordTitle,
            String parseFallbackTitle,
            String sourceRef,
            String sourceUrl,
            String content,
            UUID mappingId,
            UUID sourceId,
            String sourceCode,
            boolean publishEvent
    ) {
        DocumentImportRecord record = queueImportRecord(
                projectId,
                sourceType,
                recordTitle,
                parseFallbackTitle,
                sourceRef,
                sourceUrl,
                content,
                mappingId,
                sourceId,
                sourceCode
        );
        if (publishEvent) {
            eventPublisher.publishImportRequested(record.id());
        }
        writeAudit("IMPORT_QUEUED", "DOCUMENT_IMPORT", record.id().toString(), record.projectId(), record);
        metrics.recordImport(sourceType, record.status(), 0);
        return responseMapper.toImportResponse(record, List.of());
    }

    private DocumentImportRecord queueImportRecord(
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
        DocumentFieldMapping mapping = mappingOrDefault(firstNonNull(
                mappingId,
                source == null ? null : source.mappingId()
        ));
        UUID importId = UUID.randomUUID();
        Instant now = Instant.now();
        ensureImportContentSize(content);
        DocumentImportRecord record = new DocumentImportRecord(
                importId,
                context.resourceId(),
                sourceId,
                firstText(sourceCode, source == null ? null : source.sourceCode()),
                sourceType,
                trimToNull(sourceRef),
                trimToNull(sourceUrl),
                trimToNull(recordTitle),
                DocumentImportStatus.MODEL_PARSE_QUEUED,
                0,
                0,
                "[]",
                null,
                sha256(content),
                now,
                now
        );
        repository.saveImport(record);
        repository.saveImportPayload(new DocumentImportPayload(
                record.id(),
                mapping.id(),
                trimToNull(parseFallbackTitle),
                content,
                now,
                now
        ));
        return record;
    }

    private DocumentImportRecord failImport(DocumentImportRecord record, String errorMessage) {
        DocumentImportRecord failed = new DocumentImportRecord(
                record.id(),
                record.projectId(),
                record.sourceId(),
                record.sourceCode(),
                record.sourceType(),
                record.sourceRef(),
                record.sourceUrl(),
                record.title(),
                DocumentImportStatus.FAILED,
                record.totalParsed(),
                record.totalCreated(),
                record.createdRequirementIds(),
                trimToNull(errorMessage),
                record.rawDigest(),
                record.createdAt(),
                Instant.now()
        );
        repository.saveImport(failed);
        writeAudit("IMPORT_FAILED", "DOCUMENT_IMPORT", failed.id().toString(), failed.projectId(), failed);
        metrics.recordImport(failed.sourceType(), failed.status(), failed.totalParsed());
        log.warn("Document import event failed, import_id={}, error={}", failed.id(), failed.errorMessage());
        return failed;
    }

    /**
     * Tries rule parsing first, then merges AI parsing output or falls back with explicit audit.
     */
    private List<ParsedRequirementDraft> parseRequirements(
            UUID importId,
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
                actorResolver.currentActor()
        );
        if (modelResult.succeeded()) {
            metrics.recordModelParse(
                    ruleParsed.isEmpty() ? "MODEL_ONLY" : "MODEL_WITH_RULE_MERGE",
                    modelResult.drafts().size()
            );
            writeAudit("MODEL_PARSE", "DOCUMENT_IMPORT", importId.toString(), projectId, Map.of(
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
            writeAudit("MODEL_PARSE", "DOCUMENT_IMPORT", importId.toString(), projectId, Map.of(
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
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "AI 文档解析失败且规则解析无结果: " + modelResult.errorMessage()
            );
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

    private long maxImportContentBytes() {
        return properties.importMaxContentBytes() <= 0 ? 16777216 : properties.importMaxContentBytes();
    }

    private void ensureImportContentSize(String content) {
        long size = payloadSize(content);
        long limit = maxImportContentBytes();
        if (size > limit) {
            String message = "导入内容超过上限: "
                    + limit
                    + " bytes。下一步：拆分文档或联系管理员调整 WP4_IMPORT_MAX_CONTENT_BYTES。";
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    message
            );
        }
    }

    private void ensureImportBinarySize(long size) {
        long limit = Math.min(maxImportContentBytes(), contentExtractor.documentBinaryMaxBytes());
        if (size > limit) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传文件超过上限: "
                            + limit
                            + " bytes。下一步：压缩或拆分文件，或联系管理员调整 "
                            + "WP4_IMPORT_MAX_CONTENT_BYTES / WP4_DOCUMENT_BINARY_MAX_BYTES。"
            );
        }
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导入内容摘要计算失败");
        }
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
