package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.execution.application.command.CreateExecutionPlanCommand;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanPageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExecutionPermissionScopeResolver {

    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionPlanService planService;

    public ExecutionPermissionScopeResolver(
            ExecutionPlatformContextClient contextClient,
            ExecutionPlanService planService
    ) {
        this.contextClient = contextClient;
        this.planService = planService;
    }

    public ResourceScope planRequest(CreateExecutionPlanCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope planList(ExecutionPlanPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope plan(UUID id) {
        return ResourceScope.project(planService.planProjectScopeId(id));
    }

    private ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }
}
