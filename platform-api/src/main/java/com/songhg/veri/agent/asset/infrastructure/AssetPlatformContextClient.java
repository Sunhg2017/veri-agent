package com.songhg.veri.agent.asset.infrastructure;

import com.songhg.veri.agent.asset.application.PlatformContextClient;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.PlatformContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AssetPlatformContextClient implements PlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public AssetPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
        this.platformIntegrationService = platformIntegrationService;
    }

    @Override
    public ProjectContext getProjectContext(String projectId) {
        PlatformContext context = platformIntegrationService.projectContext(projectId, "configs");
        return new ProjectContext(
                context.resourceId(),
                context.status(),
                context.sensitivityLevel(),
                context.allowPublicModel()
        );
    }

    @Override
    public void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
        platformIntegrationService.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                "asset",
                action,
                resourceType,
                resourceId,
                "PROJECT",
                scopeId,
                result,
                "WP3 asset operation",
                Map.of("resourceType", resourceType, "resourceId", resourceId)
        ));
    }
}
