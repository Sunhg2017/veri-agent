package com.songhg.veri.agent.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.security.TokenSecurity;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlatformIntegrationController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PlatformIntegrationProperties properties;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;

    public PlatformIntegrationController(
            PlatformIntegrationProperties properties,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/contexts/projects/{projectId}")
    public PlatformContextResponse projectContext(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "") String include,
            HttpServletRequest request
    ) {
        requireServiceToken(request);
        requireText(projectId, "project_id");
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            return projectContextFromDatabase(jdbcTemplate, projectId, include)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目上下文不存在"));
        }
        return new PlatformContextResponse(
                "PROJECT",
                projectId.trim(),
                "ACTIVE",
                "INTERNAL",
                false,
                include(include),
                Instant.now()
        );
    }

    @GetMapping("/contexts/applications/{applicationId}")
    public PlatformContextResponse applicationContext(
            @PathVariable String applicationId,
            @RequestParam(defaultValue = "") String include,
            HttpServletRequest request
    ) {
        requireServiceToken(request);
        requireText(applicationId, "application_id");
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            return applicationContextFromDatabase(jdbcTemplate, applicationId, include)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用上下文不存在"));
        }
        return new PlatformContextResponse(
                "APPLICATION",
                applicationId.trim(),
                "ACTIVE",
                "INTERNAL",
                false,
                include(include),
                Instant.now()
        );
    }

    @PostMapping("/audit/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void writeAuditEvent(
            @Valid @RequestBody AuditEventRequest body,
            HttpServletRequest request
    ) {
        requireServiceToken(request);
        jdbcTemplateProvider.ifAvailable(jdbcTemplate -> insertAuditEvent(jdbcTemplate, body, request));
    }

    private void insertAuditEvent(
            JdbcTemplate jdbcTemplate,
            AuditEventRequest body,
            HttpServletRequest request
    ) {
        jdbcTemplate.update("""
                insert into audit_log (
                    trace_id, actor_type, actor_service, action, resource_type, resource_id,
                    scope_type, scope_id, result, after_json, reason
                ) values (?, 'SERVICE', ?, ?, ?, ?, ?, null, ?, cast(? as jsonb), ?)
                """,
                TraceContext.getTraceId(),
                request.getHeader("X-Caller-Service"),
                body.action(),
                body.resourceType(),
                body.resourceId(),
                StringUtils.hasText(body.scopeType()) ? body.scopeType().trim() : "PLATFORM",
                auditResult(body.result()),
                afterJson(body),
                body.reason()
        );
    }

    private String afterJson(AuditEventRequest body) {
        try {
            return objectMapper.writeValueAsString(body.afterJson() == null ? java.util.Map.of() : body.afterJson());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审计 after_json 无法序列化");
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

    private Optional<PlatformContextResponse> projectContextFromDatabase(
            JdbcTemplate jdbcTemplate,
            String projectId,
            String include
    ) {
        List<PlatformContextResponse> rows = jdbcTemplate.query("""
                select id::text as resource_id, status, sensitivity_level, allow_public_model
                from base_project
                where deleted_at is null
                  and %s
                limit 1
                """.formatted(uuid(projectId).isPresent() ? "id = ?" : "code = ?"),
                (rs, rowNum) -> new PlatformContextResponse(
                        "PROJECT",
                        rs.getString("resource_id"),
                        rs.getString("status"),
                        normalizeSensitivityLevel(rs.getString("sensitivity_level")),
                        rs.getBoolean("allow_public_model"),
                        include(include),
                        Instant.now()
                ),
                uuid(projectId).<Object>map(value -> value).orElse(projectId.trim())
        );
        return rows.stream().findFirst();
    }

    private Optional<PlatformContextResponse> applicationContextFromDatabase(
            JdbcTemplate jdbcTemplate,
            String applicationId,
            String include
    ) {
        List<PlatformContextResponse> rows = jdbcTemplate.query("""
                select id::text as resource_id, status, sensitivity_level, allow_public_model
                from base_application
                where deleted_at is null
                  and %s
                limit 1
                """.formatted(uuid(applicationId).isPresent() ? "id = ?" : "code = ?"),
                (rs, rowNum) -> new PlatformContextResponse(
                        "APPLICATION",
                        rs.getString("resource_id"),
                        rs.getString("status"),
                        normalizeSensitivityLevel(rs.getString("sensitivity_level")),
                        rs.getBoolean("allow_public_model"),
                        include(include),
                        Instant.now()
                ),
                uuid(applicationId).<Object>map(value -> value).orElse(applicationId.trim())
        );
        return rows.stream().findFirst();
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

    private void requireServiceToken(HttpServletRequest request) {
        if (!StringUtils.hasText(properties.serviceToken())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP1 内部服务令牌未配置");
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : "";
        if (!TokenSecurity.constantTimeEquals(properties.serviceToken(), token)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内部服务令牌无效");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
    }
}
