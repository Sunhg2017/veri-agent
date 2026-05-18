package com.songhg.veri.agent.asset.infrastructure;

import com.songhg.veri.agent.asset.application.PlatformContextClient;
import com.songhg.veri.agent.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("local")
@Component
public class InMemoryPlatformContextClient implements PlatformContextClient {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPlatformContextClient.class);

    @Override
    public ProjectContext getProjectContext(String projectId) {
        log.info("InMemory getProjectContext called for projectId={}, trace_id={}", projectId, TraceContext.getTraceId());
        return new ProjectContext(
                projectId,
                "Mock Project: " + projectId,
                "INTERNAL",
                false
        );
    }

    @Override
    public void writeAuditEvent(String action, String resourceType, String resourceId, String result) {
        log.info("InMemory writeAuditEvent: action={}, resourceType={}, resourceId={}, result={}, trace_id={}",
                action, resourceType, resourceId, result, TraceContext.getTraceId());
    }
}
