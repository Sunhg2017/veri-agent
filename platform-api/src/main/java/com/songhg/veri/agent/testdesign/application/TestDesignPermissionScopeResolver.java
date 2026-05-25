package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidatePageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskPageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDesignPermissionScopeResolver {

    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignService service;

    public TestDesignPermissionScopeResolver(
            TestDesignPlatformContextClient contextClient,
            TestDesignService service
    ) {
        this.contextClient = contextClient;
        this.service = service;
    }

    public ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }

    public ResourceScope taskList(TestDesignTaskPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope candidateList(TestDesignCandidatePageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        if (request != null && request.getTaskId() != null) {
            return task(request.getTaskId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope task(UUID id) {
        return ResourceScope.project(service.taskProjectScopeId(id));
    }

    public ResourceScope candidate(UUID id) {
        return ResourceScope.project(service.candidateProjectScopeId(id));
    }
}
