package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import com.songhg.veri.agent.integration.application.command.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApiAutomationPlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public ApiAutomationPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
        this.platformIntegrationService = platformIntegrationService;
    }

    public PlatformContext projectContext(String projectId) {
        return platformIntegrationService.projectContext(projectId, "apps,environments,configs");
    }

    public void writeAuditEvent(
            String action,
            String resourceType,
            String resourceId,
            String projectId,
            String result,
            Map<String, Object> afterJson
    ) {
        platformIntegrationService.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                "api-automation",
                action,
                resourceType,
                resourceId,
                "PROJECT",
                projectId,
                result,
                "WP6 API automation operation",
                afterJson
        ));
    }
}
