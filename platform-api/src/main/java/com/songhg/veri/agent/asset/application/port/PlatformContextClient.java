package com.songhg.veri.agent.asset.application.port;

public interface PlatformContextClient {

    ProjectContext getProjectContext(String projectId);

    void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result);

    record ProjectContext(
            String projectId,
            String status,
            String sensitivityLevel,
            boolean allowPublicModel
    ) {
    }
}
