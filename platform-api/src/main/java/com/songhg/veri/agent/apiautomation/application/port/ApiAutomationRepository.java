package com.songhg.veri.agent.apiautomation.application.port;

import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationGenerationTaskQuery;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiAutomationRepository {

    void insertSpec(ApiAutomationSpec spec);

    void updateSpecParseResult(ApiAutomationSpec spec);

    void archiveSpec(ApiAutomationSpec spec);

    void deleteEndpointSnapshots(UUID specId);

    void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot);

    void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot);

    void insertGenerationTask(ApiAutomationGenerationTask task);

    void insertAutomationCase(ApiAutomationCase automationCase);

    void insertScriptBundle(ApiAutomationScriptBundle bundle);

    void updateScriptBundleReview(ApiAutomationScriptBundle bundle);

    void insertRun(ApiAutomationRun run);

    void updateRunCancel(ApiAutomationRun run);

    void insertRunResult(ApiAutomationRunResult result);

    Optional<ApiAutomationSpec> spec(UUID id);

    Optional<ApiAutomationGenerationTask> generationTask(UUID id);

    Optional<ApiAutomationScriptBundle> scriptBundle(UUID id);

    Optional<ApiAutomationRun> run(UUID id);

    Optional<ApiAutomationGenerationTask> generationTaskByProjectAndDigest(String projectId, String requestDigest);

    Optional<ApiAutomationGenerationTask> generationTaskByProjectAndKey(String projectId, String requestKey);

    Optional<ApiAutomationSpec> activeSpecByProjectAndDigest(String projectId, String specDigest);

    List<ApiAutomationSpec> specs(ApiAutomationSpecQuery query);

    long countSpecs(ApiAutomationSpecQuery query);

    List<ApiAutomationGenerationTask> generationTasks(ApiAutomationGenerationTaskQuery query);

    long countGenerationTasks(ApiAutomationGenerationTaskQuery query);

    List<ApiAutomationEndpointSnapshot> endpointSnapshots(UUID specId);

    List<ApiAutomationCase> automationCases(UUID taskId);

    List<ApiAutomationScriptBundle> scriptBundles(UUID taskId);

    List<ApiAutomationRunResult> runResults(UUID runId);

    Optional<String> specProjectScopeId(UUID id);

    Optional<String> generationTaskProjectScopeId(UUID id);

    Optional<String> scriptBundleProjectScopeId(UUID id);

    Optional<String> runProjectScopeId(UUID id);
}
