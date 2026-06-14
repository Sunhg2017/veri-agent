package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import com.songhg.veri.agent.integration.application.command.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public ExecutionPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
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
                "wp9-execution-service",
                action,
                resourceType,
                resourceId,
                "PROJECT",
                projectId,
                result,
                "WP9 execution orchestration operation",
                afterJson
        ));
    }
}
