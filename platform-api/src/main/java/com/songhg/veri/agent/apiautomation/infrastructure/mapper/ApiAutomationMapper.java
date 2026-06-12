package com.songhg.veri.agent.apiautomation.infrastructure.mapper;

import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiAutomationMapper {

    void insertSpec(ApiAutomationSpec spec);

    void updateSpecParseResult(ApiAutomationSpec spec);

    ApiAutomationSpec spec(@Param("id") UUID id);

    ApiAutomationSpec activeSpecByProjectAndDigest(
            @Param("projectId") String projectId,
            @Param("specDigest") String specDigest
    );

    List<ApiAutomationSpec> specs(@Param("query") ApiAutomationSpecQuery query);

    long countSpecs(@Param("query") ApiAutomationSpecQuery query);

    String specProjectScopeId(@Param("id") UUID id);

    String generationTaskProjectScopeId(@Param("id") UUID id);

    String scriptBundleProjectScopeId(@Param("id") UUID id);

    String runProjectScopeId(@Param("id") UUID id);

    void deleteEndpointSnapshots(@Param("specId") UUID specId);

    void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot);

    void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot);

    List<ApiAutomationEndpointSnapshot> endpointSnapshots(@Param("specId") UUID specId);

    void insertGenerationTask(ApiAutomationGenerationTask task);

    void insertAutomationCase(ApiAutomationCase automationCase);

    void insertScriptBundle(ApiAutomationScriptBundle bundle);

    void updateScriptBundleReview(ApiAutomationScriptBundle bundle);

    void insertRun(ApiAutomationRun run);

    int updateRunCancel(ApiAutomationRun run);

    void insertRunResult(ApiAutomationRunResult result);

    ApiAutomationGenerationTask generationTask(@Param("id") UUID id);

    ApiAutomationScriptBundle scriptBundle(@Param("id") UUID id);

    ApiAutomationRun run(@Param("id") UUID id);

    ApiAutomationGenerationTask generationTaskByProjectAndDigest(
            @Param("projectId") String projectId,
            @Param("requestDigest") String requestDigest
    );

    ApiAutomationGenerationTask generationTaskByProjectAndKey(
            @Param("projectId") String projectId,
            @Param("requestKey") String requestKey
    );

    List<ApiAutomationCase> automationCases(@Param("taskId") UUID taskId);

    List<ApiAutomationScriptBundle> scriptBundles(@Param("taskId") UUID taskId);

    List<ApiAutomationRunResult> runResults(@Param("runId") UUID runId);
}
