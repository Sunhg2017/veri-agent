package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.UpsertUiE2eFlakyMarkCommand;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.query.UiE2eBundlePageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eFlakyMarkPageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunPageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eScenePageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UiE2ePermissionScopeResolver {

    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eSceneService sceneService;
    private final UiE2eBundleService bundleService;
    private final UiE2eRunService runService;
    private final UiE2eFlakyMarkService flakyMarkService;

    public UiE2ePermissionScopeResolver(
            UiE2ePlatformContextClient contextClient,
            UiE2eSceneService sceneService,
            UiE2eBundleService bundleService,
            UiE2eRunService runService,
            UiE2eFlakyMarkService flakyMarkService
    ) {
        this.contextClient = contextClient;
        this.sceneService = sceneService;
        this.bundleService = bundleService;
        this.runService = runService;
        this.flakyMarkService = flakyMarkService;
    }

    public ResourceScope sceneRequest(CreateUiE2eSceneCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope sceneList(UiE2eScenePageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope scene(UUID id) {
        return ResourceScope.project(sceneService.sceneProjectScopeId(id));
    }

    public ResourceScope bundleRequest(CreateUiE2eBundleCommand command) {
        if (command != null && command.sceneId() != null) {
            return ResourceScope.project(sceneService.sceneProjectScopeId(command.sceneId()));
        }
        return ResourceScope.platform();
    }

    public ResourceScope bundleList(UiE2eBundlePageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope bundle(UUID id) {
        return ResourceScope.project(bundleService.bundleProjectScopeId(id));
    }

    public ResourceScope runRequest(CreateUiE2eRunCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope runList(UiE2eRunPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope run(UUID id) {
        return ResourceScope.project(runService.runProjectScopeId(id));
    }

    public ResourceScope flakyRequest(UpsertUiE2eFlakyMarkCommand command) {
        if (command != null) {
            if (command.sceneId() == null && command.runId() == null && StringUtils.hasText(command.projectId())) {
                return project(command.projectId());
            }
            return ResourceScope.project(flakyMarkService.flakyProjectScopeId(command.sceneId(), command.runId()));
        }
        return ResourceScope.platform();
    }

    public ResourceScope flakyList(UiE2eFlakyMarkPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope flaky(UUID id) {
        return ResourceScope.project(flakyMarkService.flakyMarkProjectScopeId(id));
    }

    private ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }
}
