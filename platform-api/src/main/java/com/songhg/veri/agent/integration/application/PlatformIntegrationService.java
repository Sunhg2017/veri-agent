package com.songhg.veri.agent.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.command.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.integration.infrastructure.PlatformContextRow;
import com.songhg.veri.agent.integration.infrastructure.mapper.PlatformIntegrationMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlatformIntegrationService {

    private final Optional<PlatformIntegrationMapper> mapper;
    private final ObjectMapper objectMapper;

    public PlatformIntegrationService(Optional<PlatformIntegrationMapper> mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public PlatformContext projectContext(String projectId, String include) {
        requireText(projectId, "projectId");
        if (mapper.isPresent()) {
            return projectContextFromDatabase(projectId, include)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目上下文不存在"));
        }
        return localContext("PROJECT", projectId, include);
    }

    public PlatformContext applicationContext(String applicationId, String include) {
        requireText(applicationId, "applicationId");
        if (mapper.isPresent()) {
            return applicationContextFromDatabase(applicationId, include)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用上下文不存在"));
        }
        return localContext("APPLICATION", applicationId, include);
    }

    public void writeAuditEvent(InternalAuditEvent event) {
        requireText(event.action(), "action");
        requireText(event.resourceType(), "resourceType");
        requireText(event.resourceId(), "resourceId");
        requireText(event.result(), "result");
        mapper.ifPresent(value -> value.insertServiceAuditEvent(
                event.traceId(),
                event.actorService(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                StringUtils.hasText(event.scopeType()) ? event.scopeType().trim() : "PLATFORM",
                auditScopeId(event.scopeId()),
                auditResult(event.result()),
                afterJson(event.afterJson()),
                event.reason()
        ));
    }

    private PlatformContext localContext(String resourceType, String resourceId, String include) {
        return new PlatformContext(
                resourceType,
                resourceId.trim(),
                "ACTIVE",
                "INTERNAL",
                false,
                include(include),
                Instant.now()
        );
    }

    private Optional<PlatformContext> projectContextFromDatabase(String projectId, String include) {
        PlatformContextRow row = uuid(projectId)
                .map(value -> mapper.orElseThrow().projectContextById(value))
                .orElseGet(() -> mapper.orElseThrow().projectContextByCode(projectId.trim()));
        return Optional.ofNullable(row).map(value -> toContext("PROJECT", value, include));
    }

    private Optional<PlatformContext> applicationContextFromDatabase(String applicationId, String include) {
        PlatformContextRow row = uuid(applicationId)
                .map(value -> mapper.orElseThrow().applicationContextById(value))
                .orElseGet(() -> mapper.orElseThrow().applicationContextByCode(applicationId.trim()));
        return Optional.ofNullable(row).map(value -> toContext("APPLICATION", value, include));
    }

    private PlatformContext toContext(String resourceType, PlatformContextRow row, String include) {
        return new PlatformContext(
                resourceType,
                row.resourceId(),
                row.status(),
                normalizeSensitivityLevel(row.sensitivityLevel()),
                row.allowPublicModel(),
                include(include),
                Instant.now()
        );
    }

    private UUID auditScopeId(String scopeId) {
        if (!StringUtils.hasText(scopeId)) {
            return null;
        }
        return uuid(scopeId).orElse(null);
    }

    private String afterJson(Map<String, Object> afterJson) {
        try {
            return objectMapper.writeValueAsString(afterJson == null ? Map.of() : afterJson);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审计 afterJson 无法序列化");
        }
    }

    private String auditResult(String result) {
        return switch (result.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED" -> "SUCCESS";
            case "DENIED", "BLOCKED" -> "DENIED";
            default -> "FAILED";
        };
    }

    private List<String> include(String include) {
        if (!StringUtils.hasText(include)) {
            return List.of();
        }
        return Arrays.stream(include.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String normalizeSensitivityLevel(String value) {
        if (!StringUtils.hasText(value)) {
            return "INTERNAL";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "STRICT".equals(normalized) ? "RESTRICTED" : normalized;
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
    }
}
