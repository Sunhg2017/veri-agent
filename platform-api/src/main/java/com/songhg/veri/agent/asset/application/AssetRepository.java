package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {

    List<AssetRequirement> requirements(String projectId);

    Optional<AssetRequirement> requirement(UUID id);

    AssetRequirement saveRequirement(AssetRequirement requirement);

    List<AssetApi> apis(String projectId);

    Optional<AssetApi> api(UUID id);

    AssetApi saveApi(AssetApi api);

    List<AssetPage> pages(String projectId);

    Optional<AssetPage> page(UUID id);

    AssetPage savePage(AssetPage page);

    List<AssetBusinessFlow> businessFlows(String projectId);

    Optional<AssetBusinessFlow> businessFlow(UUID id);

    AssetBusinessFlow saveBusinessFlow(AssetBusinessFlow flow);

    List<TestCaseRecord> testCases(String projectId);

    Optional<TestCaseRecord> testCase(UUID id);

    TestCaseRecord saveTestCase(TestCaseRecord testCase);

    List<TestCaseStep> testCaseSteps(UUID caseId);

    void replaceTestCaseSteps(UUID caseId, List<TestCaseStep> steps);

    List<TraceLink> traceLinks(UUID requirementId, UUID apiId, UUID caseId);

    TraceLink saveTraceLink(TraceLink link);
}
