package com.songhg.veri.agent.integration.api.controller;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.security.TokenSecurity;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.api.request.AuditEventRequest;
import com.songhg.veri.agent.integration.api.response.PlatformContextResponse;
import com.songhg.veri.agent.integration.application.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.PlatformContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationProperties;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final PlatformIntegrationService service;

    public PlatformIntegrationController(
            PlatformIntegrationProperties properties,
            PlatformIntegrationService service
    ) {
        this.properties = properties;
        this.service = service;
    }

    @GetMapping("/contexts/projects/{projectId}")
    public PlatformContextResponse projectContext(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "") String include,
            HttpServletRequest request
    ) {
        requireServiceToken(request);
        return toResponse(service.projectContext(projectId, include));
    }

    @GetMapping("/contexts/applications/{applicationId}")
    public PlatformContextResponse applicationContext(
            @PathVariable String applicationId,
            @RequestParam(defaultValue = "") String include,
            HttpServletRequest request
    ) {
        requireServiceToken(request);
        return toResponse(service.applicationContext(applicationId, include));
    }

    @PostMapping("/audit/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void writeAuditEvent(
            @Valid @RequestBody AuditEventRequest body,
            HttpServletRequest request
    ) {
        requireServiceToken(request);
        service.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                request.getHeader("X-Caller-Service"),
                body.action(),
                body.resourceType(),
                body.resourceId(),
                StringUtils.hasText(body.scopeType()) ? body.scopeType().trim() : "PLATFORM",
                body.scopeId(),
                body.result(),
                body.reason(),
                body.afterJson()
        ));
    }

    private PlatformContextResponse toResponse(PlatformContext context) {
        return new PlatformContextResponse(
                context.resourceType(),
                context.resourceId(),
                context.status(),
                context.sensitivityLevel(),
                context.allowPublicModel(),
                context.include(),
                context.validatedAt()
        );
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
}
