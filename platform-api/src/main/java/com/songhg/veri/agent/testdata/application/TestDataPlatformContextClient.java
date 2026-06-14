package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import com.songhg.veri.agent.integration.application.command.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TestDataPlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public TestDataPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
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
                "wp8-test-data-service",
                action,
                resourceType,
                resourceId,
                "PROJECT",
                projectId,
                result,
                "WP8 test data control-plane operation",
                afterJson
        ));
    }
}
