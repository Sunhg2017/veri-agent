package com.songhg.veri.agent.asset.infrastructure.mapper;

import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetMapper {

    // ---- Requirements ----

    List<AssetRequirement> listRequirements(@Param("projectId") String projectId);

    AssetRequirement getRequirement(@Param("id") UUID id);

    AssetRequirement getRequirementBySourceRef(
            @Param("projectId") String projectId,
            @Param("source") String source,
            @Param("sourceRef") String sourceRef
    );

    void insertRequirement(AssetRequirement requirement);

    void updateRequirement(AssetRequirement requirement);

    List<AssetVersionHistory> listAssetVersionHistory(
            @Param("assetType") String assetType,
            @Param("assetId") UUID assetId
    );

    void insertAssetVersionHistory(AssetVersionHistory history);

    // ---- APIs ----

    List<AssetApi> listApis(@Param("projectId") String projectId);

    AssetApi getApi(@Param("id") UUID id);

    void insertApi(AssetApi api);

    void updateApi(AssetApi api);

    // ---- Pages ----

    List<AssetPage> listPages(@Param("projectId") String projectId);

    AssetPage getPage(@Param("id") UUID id);

    void insertPage(AssetPage page);

    void updatePage(AssetPage page);

    // ---- Business Flows ----

    List<AssetBusinessFlow> listBusinessFlows(@Param("projectId") String projectId);

    AssetBusinessFlow getBusinessFlow(@Param("id") UUID id);

    void insertBusinessFlow(AssetBusinessFlow flow);

    void updateBusinessFlow(AssetBusinessFlow flow);

    // ---- Test Cases ----

    List<TestCaseRecord> listTestCases(@Param("projectId") String projectId);

    TestCaseRecord getTestCase(@Param("id") UUID id);

    void insertTestCase(TestCaseRecord testCase);

    void updateTestCase(TestCaseRecord testCase);

    // ---- Test Case Steps ----

    List<TestCaseStep> listTestCaseSteps(@Param("caseId") UUID caseId);

    void insertTestCaseStep(TestCaseStep step);

    void deleteTestCaseSteps(@Param("caseId") UUID caseId);

    // ---- Trace Links ----

    List<TraceLink> listTraceLinks(
            @Param("requirementId") UUID requirementId,
            @Param("apiId") UUID apiId,
            @Param("caseId") UUID caseId
    );

    void insertTraceLink(TraceLink link);
}
