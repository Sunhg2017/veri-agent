package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.integration.application.InternalAuditEvent;
import com.songhg.veri.agent.integration.application.PlatformContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentInputPlatformContextClient {

    private final PlatformIntegrationService platformIntegrationService;

    public DocumentInputPlatformContextClient(PlatformIntegrationService platformIntegrationService) {
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
                "document-input",
                action,
                resourceType,
                resourceId,
                "PROJECT",
                scopeId,
                result,
                "WP4 document input operation",
                afterJson
        ));
    }
}
