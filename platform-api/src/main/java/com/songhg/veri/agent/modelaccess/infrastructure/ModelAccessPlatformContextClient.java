package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.PlatformContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.application.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.PlatformInvocationPolicy;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ModelAccessPlatformContextClient implements PlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public ModelAccessPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
        this.platformIntegrationService = platformIntegrationService;
    }

    @Override
    public PlatformInvocationPolicy verifyInvocationContext(InvokeModelRequest request, ServicePrincipal principal) {
        PlatformInvocationPolicy projectPolicy = readPolicy(
                platformIntegrationService.projectContext(request.projectId(), "apps,environments,configs")
        );
        if (StringUtils.hasText(request.applicationId())) {
            PlatformInvocationPolicy applicationPolicy = readPolicy(
                    platformIntegrationService.applicationContext(request.applicationId(), "environments,configs,permissions")
            );
            return mergePolicy(projectPolicy, applicationPolicy);
        }
        return projectPolicy;
    }

    @Override
    public void writeInvocationAudit(InvocationRecord record) {
        platformIntegrationService.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                StringUtils.hasText(record.actorService()) ? record.actorService() : "model-access",
                "MODEL_INVOKE",
                "MODEL_INVOCATION",
                record.id().toString(),
                "PROJECT",
                record.projectId(),
                auditResult(record.status()),
                record.errorMessage() == null ? "WP2 model invocation" : record.errorMessage(),
                Map.ofEntries(
                        Map.entry("providerName", record.providerName() == null ? "" : record.providerName()),
                        Map.entry("modelName", record.modelName()),
                        Map.entry("routingRuleName", record.routingRuleName() == null ? "" : record.routingRuleName()),
                        Map.entry("routingGroup", record.routingGroup() == null ? "" : record.routingGroup()),
                        Map.entry("modelCapability", record.modelCapability() == null ? "" : record.modelCapability()),
                        Map.entry("sensitivityLevel", record.sensitivityLevel()),
                        Map.entry("fallbackUsed", record.fallbackUsed()),
                        Map.entry("inputTokens", record.inputTokens()),
                        Map.entry("outputTokens", record.outputTokens()),
                        Map.entry("totalCost", record.totalCost()),
                        Map.entry("promptDigest", record.promptDigest())
                )
        ));
    }

    private PlatformInvocationPolicy readPolicy(PlatformContext context) {
        return new PlatformInvocationPolicy(context.sensitivityLevel(), context.allowPublicModel());
    }

    private String auditResult(InvocationStatus status) {
        return status == InvocationStatus.SUCCEEDED ? "SUCCESS" : status.name();
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
