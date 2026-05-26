package com.songhg.veri.agent.document.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.document.application.command.UpdateFieldMappingRequest;
import com.songhg.veri.agent.document.application.command.UpsertDocumentSourceRequest;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.document.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.document.application.view.DocumentSourceHealthResponse;
import com.songhg.veri.agent.document.application.view.DocumentSourceResponse;
import com.songhg.veri.agent.document.application.view.FieldMappingResponse;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentFieldMapping;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;




@Service
public class DocumentSourceManagementService {

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
    private final DocumentInputPlatformContextClient contextClient;
    private final DocumentContentExtractor contentExtractor;
    private final DocumentInputProperties properties;
    private final DocumentWebhookSecretResolver webhookSecretResolver;

    public DocumentSourceManagementService(
            DocumentInputRepository repository,
            DocumentInputPlatformContextClient contextClient,
            DocumentContentExtractor contentExtractor,
            DocumentInputProperties properties,
            DocumentWebhookSecretResolver webhookSecretResolver
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.contentExtractor = contentExtractor;
        this.properties = properties;
        this.webhookSecretResolver = webhookSecretResolver;
    }

    public PageResponse<DocumentSourceResponse> sources(DocumentSourceQuery query) {
        return PageResponse.of(
                repository.sources(query).stream().map(DocumentSourceManagementService::toSourceResponse).toList(),
                query.index(),
                query.size(),
                repository.countSources(query)
        );
    }

    public DocumentSourceResponse createSource(UpsertDocumentSourceRequest request) {
        repository.sourceByCode(normalizeSourceCode(request.sourceCode()))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "文档源编码已存在: " + existing.sourceCode());
                });
        DocumentFieldMapping mapping = mappingOrDefault(request.mappingId());
        validateProjectWhenProvided(request.defaultProjectId());
        Instant now = Instant.now();
        DocumentSourceConfig source = new DocumentSourceConfig(
                UUID.randomUUID(),
                normalizeSourceCode(request.sourceCode()),
                request.name().trim(),
                request.sourceType(),
                normalizedSourceStatus(request.sourceType(), request.status()),
                trimToNull(request.endpointUrl()),
                trimToNull(request.defaultProjectId()),
                mapping.id(),
                normalizeSecretRef(request.sourceType(), request.secretRef()),
                normalizeEventVersion(request.eventVersion()),
                normalizeMappingVersion(request.mappingVersion(), mapping),
                trimToNull(request.description()),
                now,
                now
        );
        repository.saveSource(source);
        webhookSecretResolver.invalidate(source);
        writeAudit("CREATE", "DOCUMENT_SOURCE", source.id().toString(), source.defaultProjectId(), source);
        return toSourceResponse(source);
    }

    public DocumentSourceResponse updateSource(UUID id, UpsertDocumentSourceRequest request) {
        DocumentSourceConfig existing = repository.source(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档源不存在: " + id));
        repository.sourceByCode(normalizeSourceCode(request.sourceCode()))
                .filter(source -> !source.id().equals(id))
                .ifPresent(source -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "文档源编码已存在: " + source.sourceCode());
                });
        DocumentFieldMapping mapping = mappingOrDefault(request.mappingId());
        validateProjectWhenProvided(request.defaultProjectId());
        DocumentSourceConfig updated = new DocumentSourceConfig(
                id,
                normalizeSourceCode(request.sourceCode()),
                request.name().trim(),
                request.sourceType(),
                normalizedSourceStatus(request.sourceType(), request.status()),
                trimToNull(request.endpointUrl()),
                trimToNull(request.defaultProjectId()),
                mapping.id(),
                normalizeSecretRef(request.sourceType(), request.secretRef()),
                normalizeEventVersion(request.eventVersion()),
                normalizeMappingVersion(request.mappingVersion(), mapping),
                trimToNull(request.description()),
                existing.createdAt(),
                Instant.now()
        );
        repository.saveSource(updated);
        webhookSecretResolver.invalidate(existing);
        webhookSecretResolver.invalidate(updated);
        writeAudit("UPDATE", "DOCUMENT_SOURCE", updated.id().toString(), updated.defaultProjectId(), updated);
        return toSourceResponse(updated);
    }

    public DocumentSourceHealthResponse sourceHealth(UUID id) {
        DocumentSourceConfig source = repository.source(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档源不存在: " + id));
        boolean supported = SUPPORTED_SOURCE_TYPES.contains(source.sourceType());
        boolean ready = supported && source.status() == DocumentSourceStatus.ENABLED;
        DocumentWebhookEvent lastEvent = null;
        if (source.sourceType() == DocumentSourceType.CUSTOM_API) {
            lastEvent = repository.webhookEvents(new DocumentWebhookEventQuery(
                    source.id(),
                    source.sourceCode(),
                    null,
                    null,
                    null,
                    null,
                    PageQuery.of(0, 1)
            )).stream().findFirst().orElse(null);
        }
        String message;
        if (!supported) {
            message = source.sourceType() + " connector is reserved for later enablement";
        } else if (source.status() != DocumentSourceStatus.ENABLED) {
            message = "文档源未启用";
        } else if (source.sourceType() == DocumentSourceType.OCR && !contentExtractor.ocrConfigured()) {
            ready = false;
            message = "OCR 命令未配置";
        } else if (source.sourceType() == DocumentSourceType.CUSTOM_API && !properties.webhookEnabled()) {
            ready = false;
            message = "webhook 输入已被 feature flag 关闭";
        } else if (source.sourceType() == DocumentSourceType.CUSTOM_API && !StringUtils.hasText(source.secretRef())) {
            ready = false;
            message = "webhook 密钥引用未配置";
        } else {
            message = "READY";
        }
        return new DocumentSourceHealthResponse(
                source.id(),
                source.sourceCode(),
                source.sourceType(),
                source.status(),
                supported,
                ready,
                message,
                source.sourceType() == DocumentSourceType.CUSTOM_API
                        ? "/api/v1/document-input/webhooks/" + source.sourceCode()
                        : null,
                source.sourceType() == DocumentSourceType.CUSTOM_API ? "HMAC-SHA256(timestamp.eventId.idempotencyKey.rawBody)" : null,
                StringUtils.hasText(source.secretRef()),
                source.eventVersion(),
                source.mappingVersion(),
                Instant.now(),
                lastEvent == null ? null : lastEvent.receivedAt(),
                lastEvent == null ? null : lastEvent.status(),
                lastEvent == null ? null : lastEvent.signatureStatus(),
                lastEvent == null ? null : lastEvent.errorMessage()
        );
    }

    public FieldMappingResponse fieldMapping() {
        return toFieldMappingResponse(repository.defaultFieldMapping());
    }

    public FieldMappingResponse updateFieldMapping(UpdateFieldMappingRequest request) {
        DocumentFieldMapping existing = repository.defaultFieldMapping();
        DocumentFieldMapping updated = new DocumentFieldMapping(
                existing.id(),
                existing.mappingCode(),
                StringUtils.hasText(request.name()) ? request.name().trim() : existing.name(),
                trimOrDefault(request.itemPath(), existing.itemPath()),
                request.titlePath().trim(),
                trimOrDefault(request.descriptionPath(), existing.descriptionPath()),
                trimOrDefault(request.priorityPath(), existing.priorityPath()),
                trimOrDefault(request.acceptanceCriteriaPath(), existing.acceptanceCriteriaPath()),
                trimOrDefault(request.tagsPath(), existing.tagsPath()),
                existing.createdAt(),
                Instant.now()
        );
        repository.saveFieldMapping(updated);
        writeAudit("UPDATE", "DOCUMENT_FIELD_MAPPING", updated.id().toString(), null, updated);
        return toFieldMappingResponse(updated);
    }

    private DocumentFieldMapping mappingOrDefault(UUID mappingId) {
        if (mappingId == null) {
            return repository.defaultFieldMapping();
        }
        return repository.fieldMapping(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "字段映射不存在: " + mappingId));
    }

    private DocumentSourceStatus normalizedSourceStatus(
            DocumentSourceType sourceType,
            DocumentSourceStatus requestedStatus
    ) {
        DocumentSourceStatus status = requestedStatus == null
                ? (SUPPORTED_SOURCE_TYPES.contains(sourceType) ? DocumentSourceStatus.ENABLED : DocumentSourceStatus.PLANNED)
                : requestedStatus;
        if (!SUPPORTED_SOURCE_TYPES.contains(sourceType) && status == DocumentSourceStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, sourceType + " 数据流尚未实现");
        }
        return status;
    }

    private void validateProjectWhenProvided(String projectId) {
        if (StringUtils.hasText(projectId)) {
            validateProject(projectId);
        }
    }

    private PlatformContext validateProject(String projectId) {
        PlatformContext context = contextClient.projectContext(projectId);
        if (!"ACTIVE".equals(context.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许导入需求: " + projectId);
        }
        return context;
    }

    private String normalizeSourceCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceCode 不能为空");
        }
        return value.trim();
    }

    private String normalizeSecretRef(DocumentSourceType sourceType, String value) {
        String normalized = trimToNull(value);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return sourceType == DocumentSourceType.CUSTOM_API ? DocumentWebhookSecretResolver.DEFAULT_WEBHOOK_SECRET_REF : null;
    }

    private String normalizeEventVersion(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "1.0";
        ensureSupportedWebhookEventVersion(normalized);
        return normalized;
    }

    private void ensureSupportedWebhookEventVersion(String eventVersion) {
        if (!StringUtils.hasText(eventVersion) || !SUPPORTED_WEBHOOK_EVENT_VERSIONS.contains(eventVersion.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 webhook eventVersion: " + eventVersion);
        }
    }

    private String normalizeMappingVersion(String value, DocumentFieldMapping mapping) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        if (mapping != null && StringUtils.hasText(mapping.mappingCode())) {
            return mapping.mappingCode();
        }
        return "default";
    }

    private String trimOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private static DocumentSourceResponse toSourceResponse(DocumentSourceConfig source) {
        return new DocumentSourceResponse(
                source.id(),
                source.sourceCode(),
                source.name(),
                source.sourceType(),
                source.status(),
                source.endpointUrl(),
                source.defaultProjectId(),
                source.mappingId(),
                source.secretRef(),
                source.eventVersion(),
                source.mappingVersion(),
                source.description(),
                SUPPORTED_SOURCE_TYPES.contains(source.sourceType()),
                source.createdAt(),
                source.updatedAt()
        );
    }

    private static FieldMappingResponse toFieldMappingResponse(DocumentFieldMapping mapping) {
        return new FieldMappingResponse(
                mapping.id(),
                mapping.mappingCode(),
                mapping.name(),
                mapping.itemPath(),
                mapping.titlePath(),
                mapping.descriptionPath(),
                mapping.priorityPath(),
                mapping.acceptanceCriteriaPath(),
                mapping.tagsPath(),
                mapping.createdAt(),
                mapping.updatedAt()
        );
    }
}
