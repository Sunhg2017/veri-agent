package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.testdesign.application.command.ResolveTestDesignConflictBatchCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateBatchActionCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidatePageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationCorpusSummaryRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignPromptTrendRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignScopeSummaryRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskPageRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDesignPermissionScopeResolver {

    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignScopeService scopeService;

    public TestDesignPermissionScopeResolver(
            TestDesignPlatformContextClient contextClient,
            TestDesignScopeService scopeService
    ) {
        this.contextClient = contextClient;
        this.scopeService = scopeService;
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

    public ResourceScope promptTrend(TestDesignPromptTrendRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope evaluationCorpusSummary(TestDesignEvaluationCorpusSummaryRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        // Without a project filter the endpoint returns only platform-level aggregate readiness signals.
        return ResourceScope.platform();
    }

    public ResourceScope scopeSummary(TestDesignScopeSummaryRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        // Platform fallback is aggregate-only and never returns task, candidate or role identifiers.
        return ResourceScope.platform();
    }

    public ResourceScope candidateList(TestDesignCandidatePageRequest request) {
        if (request != null && request.getTaskId() != null) {
            return task(request.getTaskId());
        }
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope task(UUID id) {
        return ResourceScope.project(scopeService.taskProjectScopeId(id));
    }

    public ResourceScope candidate(UUID id) {
        return ResourceScope.project(scopeService.candidateProjectScopeId(id));
    }

    public ResourceScope contextPolicyOverride(UUID id) {
        return ResourceScope.project(scopeService.contextPolicyOverrideProjectScopeId(id));
    }

    public List<ResourceScope> candidateBatch(TestDesignCandidateBatchActionCommand command) {
        LinkedHashSet<String> projectIds = new LinkedHashSet<>();
        for (UUID candidateId : batchCandidateIds(command)) {
            projectIds.add(scopeService.candidateProjectScopeId(candidateId));
        }
        if (projectIds.isEmpty()) {
            return List.of(ResourceScope.platform());
        }
        return projectIds.stream()
                .map(ResourceScope::project)
                .toList();
    }

    public List<ResourceScope> candidateBatch(ResolveTestDesignConflictBatchCommand command) {
        LinkedHashSet<String> projectIds = new LinkedHashSet<>();
        for (UUID candidateId : batchCandidateIds(command)) {
            projectIds.add(scopeService.candidateProjectScopeId(candidateId));
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

    private static List<UUID> batchCandidateIds(ResolveTestDesignConflictBatchCommand command) {
        if (command == null || command.items() == null) {
            return List.of();
        }
        return command.items().stream()
                .filter(item -> item != null && item.candidateId() != null)
                .map(ResolveTestDesignConflictBatchCommand.Item::candidateId)
                .distinct()
                .toList();
    }
}
