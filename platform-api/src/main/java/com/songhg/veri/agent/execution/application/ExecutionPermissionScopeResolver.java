package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.execution.application.command.CreateExecutionPlanCommand;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionRunPageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExecutionPermissionScopeResolver {

    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionPlanService planService;
    private final ExecutionRunService runService;
    private final ExecutionTriggerService triggerService;

    public ExecutionPermissionScopeResolver(
            ExecutionPlatformContextClient contextClient,
            ExecutionPlanService planService,
            ExecutionRunService runService,
            ExecutionTriggerService triggerService
    ) {
        this.contextClient = contextClient;
        this.planService = planService;
        this.runService = runService;
        this.triggerService = triggerService;
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

    public ResourceScope runList(ExecutionRunPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        if (request != null && request.getPlanId() != null) {
            return plan(request.getPlanId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope run(UUID id) {
        return ResourceScope.project(runService.runProjectScopeId(id));
    }

    public ResourceScope trigger(UUID id) {
        return ResourceScope.project(triggerService.triggerProjectScopeId(id));
    }

    private ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }
}
