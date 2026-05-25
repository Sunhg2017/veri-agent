package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateBatchActionCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidatePageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskPageRequest;
import java.util.LinkedHashSet;
import java.util.List;
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

    public List<ResourceScope> candidateBatch(TestDesignCandidateBatchActionCommand command) {
        LinkedHashSet<String> projectIds = new LinkedHashSet<>();
        for (UUID candidateId : batchCandidateIds(command)) {
            projectIds.add(service.candidateProjectScopeId(candidateId));
        }
        if (projectIds.isEmpty()) {
            return List.of(ResourceScope.platform());
        }
        return projectIds.stream()
                .map(ResourceScope::project)
                .toList();
    }

    private static List<UUID> batchCandidateIds(TestDesignCandidateBatchActionCommand command) {
        if (command == null) {
            return List.of();
        }
        if (command.candidates() != null && !command.candidates().isEmpty()) {
            return command.candidates().stream()
                    .filter(item -> item != null && item.id() != null)
                    .map(TestDesignCandidateBatchActionCommand.Target::id)
                    .distinct()
                    .toList();
        }
        if (command.candidateIds() == null) {
            return List.of();
        }
        return command.candidateIds().stream()
                .filter(candidateId -> candidateId != null)
                .distinct()
                .toList();
    }
}
