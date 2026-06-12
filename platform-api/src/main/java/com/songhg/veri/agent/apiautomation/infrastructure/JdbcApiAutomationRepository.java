package com.songhg.veri.agent.apiautomation.infrastructure;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationGenerationTaskQuery;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.mapper.ApiAutomationMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcApiAutomationRepository implements ApiAutomationRepository {

    private final ApiAutomationMapper mapper;

    public JdbcApiAutomationRepository(ApiAutomationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertSpec(ApiAutomationSpec spec) {
        mapper.insertSpec(spec);
    }

    @Override
    public void updateSpecParseResult(ApiAutomationSpec spec) {
        mapper.updateSpecParseResult(spec);
    }

    @Override
    public void deleteEndpointSnapshots(UUID specId) {
        mapper.deleteEndpointSnapshots(specId);
    }

    @Override
    public void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot) {
        mapper.insertEndpointSnapshot(snapshot);
    }

    @Override
    public void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot) {
        mapper.updateEndpointSnapshotDiff(snapshot);
    }

    @Override
    public void insertGenerationTask(ApiAutomationGenerationTask task) {
        mapper.insertGenerationTask(task);
    }

    @Override
    public void insertAutomationCase(ApiAutomationCase automationCase) {
        mapper.insertAutomationCase(automationCase);
    }

    @Override
    public void insertScriptBundle(ApiAutomationScriptBundle bundle) {
        mapper.insertScriptBundle(bundle);
    }

    @Override
    public void updateScriptBundleReview(ApiAutomationScriptBundle bundle) {
        mapper.updateScriptBundleReview(bundle);
    }

    @Override
    public void insertRun(ApiAutomationRun run) {
        mapper.insertRun(run);
    }

    @Override
    public void updateRunCancel(ApiAutomationRun run) {
        mapper.updateRunCancel(run);
    }

    @Override
    public void insertRunResult(ApiAutomationRunResult result) {
        mapper.insertRunResult(result);
    }

    @Override
    public Optional<ApiAutomationSpec> spec(UUID id) {
        return Optional.ofNullable(mapper.spec(id));
    }

    @Override
    public Optional<ApiAutomationGenerationTask> generationTask(UUID id) {
        return Optional.ofNullable(mapper.generationTask(id));
    }

    @Override
    public Optional<ApiAutomationScriptBundle> scriptBundle(UUID id) {
        return Optional.ofNullable(mapper.scriptBundle(id));
    }

    @Override
    public Optional<ApiAutomationRun> run(UUID id) {
        return Optional.ofNullable(mapper.run(id));
    }

    @Override
    public Optional<ApiAutomationGenerationTask> generationTaskByProjectAndDigest(String projectId, String requestDigest) {
        return Optional.ofNullable(mapper.generationTaskByProjectAndDigest(projectId, requestDigest));
    }

    @Override
    public Optional<ApiAutomationGenerationTask> generationTaskByProjectAndKey(String projectId, String requestKey) {
        return Optional.ofNullable(mapper.generationTaskByProjectAndKey(projectId, requestKey));
    }

    @Override
    public Optional<ApiAutomationSpec> activeSpecByProjectAndDigest(String projectId, String specDigest) {
        return Optional.ofNullable(mapper.activeSpecByProjectAndDigest(projectId, specDigest));
    }

    @Override
    public List<ApiAutomationSpec> specs(ApiAutomationSpecQuery query) {
        return mapper.specs(query);
    }

    @Override
    public long countSpecs(ApiAutomationSpecQuery query) {
        return mapper.countSpecs(query);
    }

    @Override
    public List<ApiAutomationGenerationTask> generationTasks(ApiAutomationGenerationTaskQuery query) {
        return mapper.generationTasks(query);
    }

    @Override
    public long countGenerationTasks(ApiAutomationGenerationTaskQuery query) {
        return mapper.countGenerationTasks(query);
    }

    @Override
    public List<ApiAutomationEndpointSnapshot> endpointSnapshots(UUID specId) {
        return mapper.endpointSnapshots(specId);
    }

    @Override
    public List<ApiAutomationCase> automationCases(UUID taskId) {
        return mapper.automationCases(taskId);
    }

    @Override
    public List<ApiAutomationScriptBundle> scriptBundles(UUID taskId) {
        return mapper.scriptBundles(taskId);
    }

    @Override
    public List<ApiAutomationRunResult> runResults(UUID runId) {
        return mapper.runResults(runId);
    }

    @Override
    public Optional<String> specProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.specProjectScopeId(id));
    }

    @Override
    public Optional<String> generationTaskProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.generationTaskProjectScopeId(id));
    }

    @Override
    public Optional<String> scriptBundleProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.scriptBundleProjectScopeId(id));
    }

    @Override
    public Optional<String> runProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.runProjectScopeId(id));
    }
}
