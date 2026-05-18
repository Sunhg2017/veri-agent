package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.application.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.PlatformInvocationPolicy;
import com.songhg.veri.agent.modelaccess.application.ModelAccessMetrics;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class MockPlatformContextClient implements PlatformContextClient {

    private static final Logger log = LoggerFactory.getLogger(MockPlatformContextClient.class);

    private final ModelAccessProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ModelAccessMetrics metrics;

    public MockPlatformContextClient(
            ModelAccessProperties properties,
            RestClient.Builder restClientBuilder,
            ModelAccessMetrics metrics
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.metrics = metrics;
    }

    @Override
    public PlatformInvocationPolicy verifyInvocationContext(InvokeModelRequest request, ServicePrincipal principal) {
        if (!StringUtils.hasText(principal.callerService()) || !StringUtils.hasText(principal.delegatedUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "严格模式下必须携带调用服务和委托用户");
        }
        if (!properties.strictPlatformContextValidation()) {
            return PlatformInvocationPolicy.unrestricted();
        }
        if (!StringUtils.hasText(properties.platformApiServiceToken())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "严格模式下必须配置 WP2_PLATFORM_API_SERVICE_TOKEN");
        }

        RestClient client = platformClient(principal);
        PlatformInvocationPolicy projectPolicy = readPolicy(client.get()
                .uri("/api/v1/contexts/projects/{projectId}?include=apps,environments,configs", request.projectId())
                .retrieve()
                .body(JsonNode.class));
        if (StringUtils.hasText(request.applicationId())) {
            PlatformInvocationPolicy applicationPolicy = readPolicy(client.get()
                    .uri("/api/v1/contexts/applications/{appId}?include=environments,configs,permissions", request.applicationId())
                    .retrieve()
                    .body(JsonNode.class));
            return mergePolicy(projectPolicy, applicationPolicy);
        }
        return projectPolicy;
    }

    @Override
    public void writeInvocationAudit(InvocationRecord record) {
        if (!properties.platformAuditEnabled() || !StringUtils.hasText(properties.platformApiServiceToken())) {
            metrics.recordAuditEvent("skipped");
            return;
        }
        try {
            platformClient(new ServicePrincipal(record.actorService(), record.delegatedUserId()))
                    .post()
                    .uri("/api/v1/audit/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "action", "MODEL_INVOKE",
                            "resourceType", "MODEL_INVOCATION",
                            "resourceId", record.id().toString(),
                            "scopeType", "PROJECT",
                            "scopeId", record.projectId(),
                            "result", record.status().name(),
                            "reason", record.errorMessage() == null ? "WP2 model invocation" : record.errorMessage(),
                            "afterJson", Map.of(
                                    "providerName", record.providerName() == null ? "" : record.providerName(),
                                    "modelName", record.modelName(),
                                    "sensitivityLevel", record.sensitivityLevel(),
                                    "fallbackUsed", record.fallbackUsed(),
                                    "inputTokens", record.inputTokens(),
                                    "outputTokens", record.outputTokens(),
                                    "totalCost", record.totalCost(),
                                    "prompt_digest", record.promptDigest()
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
            metrics.recordAuditEvent("succeeded");
        } catch (RuntimeException exception) {
            metrics.recordAuditEvent("failed");
            log.warn("Failed to write WP1 audit event for invocationId={}, traceId={}",
                    record.id(), TraceContext.getTraceId(), exception);
        }
    }

    private RestClient platformClient(ServicePrincipal principal) {
        return restClientBuilder
                .baseUrl(properties.platformApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.platformApiServiceToken())
                .defaultHeader("X-Caller-Service", principal.callerService())
                .defaultHeader("X-Delegated-User-Id", principal.delegatedUserId())
                .defaultHeader(TraceContext.TRACE_ID_HEADER, TraceContext.getTraceId())
                .build();
    }

    private PlatformInvocationPolicy readPolicy(JsonNode envelope) {
        JsonNode data = envelope == null ? null : envelope.path("data");
        String sensitivityLevel = data == null || data.path("sensitivityLevel").isMissingNode()
                ? "INTERNAL"
                : data.path("sensitivityLevel").asText("INTERNAL");
        boolean allowPublicModel = data != null && data.path("allowPublicModel").asBoolean(false);
        return new PlatformInvocationPolicy(sensitivityLevel, allowPublicModel);
    }

    private PlatformInvocationPolicy mergePolicy(
            PlatformInvocationPolicy projectPolicy,
            PlatformInvocationPolicy applicationPolicy
    ) {
        String sensitivityLevel = sensitivityRank(applicationPolicy.sensitivityLevel())
                > sensitivityRank(projectPolicy.sensitivityLevel())
                ? applicationPolicy.sensitivityLevel()
                : projectPolicy.sensitivityLevel();
        return new PlatformInvocationPolicy(
                sensitivityLevel,
                projectPolicy.allowPublicModel() && applicationPolicy.allowPublicModel()
        );
    }

    private int sensitivityRank(String sensitivityLevel) {
        if (!StringUtils.hasText(sensitivityLevel)) {
            return 1;
        }
        return switch (sensitivityLevel.trim().toUpperCase()) {
            case "PUBLIC" -> 0;
            case "INTERNAL" -> 1;
            case "CONFIDENTIAL" -> 2;
            case "STRICT", "RESTRICTED" -> 3;
            default -> 1;
        };
    }
}
