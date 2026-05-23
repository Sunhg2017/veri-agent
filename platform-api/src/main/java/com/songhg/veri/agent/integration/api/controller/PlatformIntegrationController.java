package com.songhg.veri.agent.integration.api.controller;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.security.ServiceCallerProperties;
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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ServiceCallerProperties serviceCallerProperties;
    private final PlatformIntegrationService service;

    public PlatformIntegrationController(
            PlatformIntegrationProperties properties,
            ServiceCallerProperties serviceCallerProperties,
            PlatformIntegrationService service
    ) {
        this.properties = properties;
        this.serviceCallerProperties = serviceCallerProperties;
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
        String callerService = requireServiceToken(request);
        service.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                callerService,
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

    private String requireServiceToken(HttpServletRequest request) {
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
        String callerService = request.getHeader("X-Caller-Service");
        if (!StringUtils.hasText(callerService)) {
            return "platform-integration";
        }
        String normalized = normalize(callerService);
        if (!trustedCallerServices().contains(normalized)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "服务调用方不可信");
        }
        return callerService.trim();
    }

    private Set<String> trustedCallerServices() {
        Set<String> trusted = serviceCallerProperties.safeModelAccessTrustedServices().stream()
                .filter(StringUtils::hasText)
                .map(PlatformIntegrationController::normalize)
                .collect(Collectors.toSet());
        serviceCallerProperties.safeAssetTrustedServices().stream()
                .filter(StringUtils::hasText)
                .map(PlatformIntegrationController::normalize)
                .forEach(trusted::add);
        serviceCallerProperties.safeDocumentInputTrustedServices().stream()
                .filter(StringUtils::hasText)
                .map(PlatformIntegrationController::normalize)
                .forEach(trusted::add);
        trusted.add("platform-integration");
        return trusted;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
