package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {

    List<AssetRequirement> requirements(String projectId);

    List<AssetRequirement> requirements(AssetListQuery query);

    long countRequirements(AssetListQuery query);

    Optional<AssetRequirement> requirement(UUID id);

    Optional<AssetRequirement> requirementIncludingInactive(UUID id);

    Optional<AssetRequirement> requirementBySourceRef(String projectId, String source, String sourceRef);

    boolean hasActiveRequirementCodeConflict(String projectId, String code, UUID excludeId);

    boolean hasActiveRequirementSourceRefConflict(String projectId, String source, String sourceRef, UUID excludeId);

    AssetRequirement saveRequirement(AssetRequirement requirement);

    List<AssetVersionHistory> assetVersionHistory(String assetType, UUID assetId);

    AssetVersionHistory saveVersionHistory(AssetVersionHistory history);

    List<AssetApi> apis(String projectId);

    List<AssetApi> apis(AssetListQuery query);

    long countApis(AssetListQuery query);

    Optional<AssetApi> api(UUID id);

    Optional<AssetApi> apiIncludingInactive(UUID id);

    Optional<AssetApi> apiByPath(String projectId, String path, String httpMethod);

    AssetApi saveApi(AssetApi api);

    boolean hasActiveApiPathConflict(String projectId, String path, String httpMethod, UUID excludeId);

    List<AssetPage> pages(String projectId);

    List<AssetPage> pages(AssetListQuery query);

    long countPages(AssetListQuery query);

    Optional<AssetPage> page(UUID id);

    Optional<AssetPage> pageIncludingInactive(UUID id);

    Optional<AssetPage> pageBySourceRef(String projectId, String source, String sourceRef);

    AssetPage savePage(AssetPage page);

    boolean hasActivePageCodeConflict(String projectId, String code, UUID excludeId);

    List<AssetBusinessFlow> businessFlows(String projectId);

    List<AssetBusinessFlow> businessFlows(AssetListQuery query);

    long countBusinessFlows(AssetListQuery query);

    Optional<AssetBusinessFlow> businessFlow(UUID id);

    Optional<AssetBusinessFlow> businessFlowIncludingInactive(UUID id);

    AssetBusinessFlow saveBusinessFlow(AssetBusinessFlow flow);

    boolean hasActiveBusinessFlowCodeConflict(String projectId, String code, UUID excludeId);

    List<TestCaseRecord> testCases(String projectId);

    List<TestCaseRecord> testCases(AssetListQuery query);

    long countTestCases(AssetListQuery query);

    Optional<TestCaseRecord> testCase(UUID id);

    Optional<TestCaseRecord> testCaseIncludingInactive(UUID id);

    TestCaseRecord saveTestCase(TestCaseRecord testCase);

    boolean hasActiveTestCaseCodeConflict(String projectId, String code, UUID excludeId);

    List<TestCaseStep> testCaseSteps(UUID caseId);

    void replaceTestCaseSteps(UUID caseId, List<TestCaseStep> steps);

    List<TraceLink> traceLinks(UUID requirementId, UUID apiId, UUID pageId, UUID flowId, UUID caseId);

    TraceLink saveTraceLink(TraceLink link);
}
