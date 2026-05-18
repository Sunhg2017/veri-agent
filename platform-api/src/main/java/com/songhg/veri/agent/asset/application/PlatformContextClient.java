package com.songhg.veri.agent.asset.application;

public interface PlatformContextClient {

    ProjectContext getProjectContext(String projectId);

    void writeAuditEvent(String action, String resourceType, String resourceId, String result);

    record ProjectContext(
            String projectId,
            String projectName,
            String sensitivityLevel,
            boolean allowPublicModel
    ) {
    }
}
