package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import com.songhg.veri.agent.integration.application.command.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TestDesignPlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public TestDesignPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
        this.platformIntegrationService = platformIntegrationService;
    }

    public PlatformContext projectContext(String projectId) {
        return platformIntegrationService.projectContext(projectId, "configs");
    }

    public void writeAuditEvent(
            String action,
            String resourceType,
            String resourceId,
            String scopeId,
            String result,
            Map<String, Object> afterJson
    ) {
        platformIntegrationService.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                "test-design",
                action,
                resourceType,
                resourceId,
                "PROJECT",
                scopeId,
                result,
                "WP5 test design operation",
                afterJson
        ));
    }

    public void writePlatformAuditEvent(
            String action,
            String resourceType,
            String resourceId,
            String result,
            Map<String, Object> afterJson
    ) {
        platformIntegrationService.writeAuditEvent(new InternalAuditEvent(
                TraceContext.getTraceId(),
                "test-design",
                action,
                resourceType,
                resourceId,
                "PLATFORM",
                null,
                result,
                "WP5 test design operation",
                afterJson
        ));
    }
}
