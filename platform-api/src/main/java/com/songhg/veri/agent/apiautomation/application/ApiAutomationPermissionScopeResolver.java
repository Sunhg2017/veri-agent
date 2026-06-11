package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiAutomationPermissionScopeResolver {

    private final ApiAutomationPlatformContextClient contextClient;
    private final ApiAutomationScopeService scopeService;

    public ApiAutomationPermissionScopeResolver(
            ApiAutomationPlatformContextClient contextClient,
            ApiAutomationScopeService scopeService
    ) {
        this.contextClient = contextClient;
        this.scopeService = scopeService;
    }

    public ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }

    public ResourceScope specRequest(CreateApiAutomationSpecCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope specList(ApiAutomationSpecPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope spec(UUID id) {
        return ResourceScope.project(scopeService.specProjectScopeId(id));
    }

    public ResourceScope generationRequest(CreateApiAutomationGenerationTaskCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope generationTask(UUID id) {
        return ResourceScope.project(scopeService.generationTaskProjectScopeId(id));
    }
}
