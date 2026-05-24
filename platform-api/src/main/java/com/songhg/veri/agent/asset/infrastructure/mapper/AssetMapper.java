package com.songhg.veri.agent.asset.infrastructure.mapper;

import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.asset.application.query.AssetListQuery;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetMapper {

    // ---- Requirements ----

    List<AssetRequirement> listRequirements(@Param("projectId") String projectId);

    List<AssetRequirement> listRequirementsByQuery(@Param("query") AssetListQuery query);

    long countRequirementsByQuery(@Param("query") AssetListQuery query);

    AssetRequirement getRequirement(@Param("id") UUID id);

    AssetRequirement getRequirementIncludingInactive(@Param("id") UUID id);

    AssetRequirement getRequirementBySourceRef(
            @Param("projectId") String projectId,
            @Param("source") String source,
            @Param("sourceRef") String sourceRef
    );

    int countActiveRequirementCodeConflict(
            @Param("projectId") String projectId,
            @Param("code") String code,
            @Param("excludeId") UUID excludeId
    );

    int countActiveRequirementSourceRefConflict(
            @Param("projectId") String projectId,
            @Param("source") String source,
            @Param("sourceRef") String sourceRef,
            @Param("excludeId") UUID excludeId
    );

    void insertRequirement(AssetRequirement requirement);

    void updateRequirement(AssetRequirement requirement);

    void updateRequirementLifecycle(AssetRequirement requirement);

    List<AssetVersionHistory> listAssetVersionHistory(
            @Param("assetType") String assetType,
            @Param("assetId") UUID assetId
    );

    void insertAssetVersionHistory(AssetVersionHistory history);

    // ---- APIs ----

    List<AssetApi> listApis(@Param("projectId") String projectId);

    List<AssetApi> listApisByQuery(@Param("query") AssetListQuery query);

    long countApisByQuery(@Param("query") AssetListQuery query);

    AssetApi getApi(@Param("id") UUID id);

    AssetApi getApiIncludingInactive(@Param("id") UUID id);

    AssetApi getApiByPath(
            @Param("projectId") String projectId,
            @Param("path") String path,
            @Param("httpMethod") String httpMethod
    );

    void insertApi(AssetApi api);

    void updateApi(AssetApi api);

    void updateApiLifecycle(AssetApi api);

    int countActiveApiPathConflict(
            @Param("projectId") String projectId,
            @Param("path") String path,
            @Param("httpMethod") String httpMethod,
            @Param("excludeId") UUID excludeId
    );

    // ---- Pages ----

    List<AssetPage> listPages(@Param("projectId") String projectId);

    List<AssetPage> listPagesByQuery(@Param("query") AssetListQuery query);

    long countPagesByQuery(@Param("query") AssetListQuery query);

    AssetPage getPage(@Param("id") UUID id);

    AssetPage getPageIncludingInactive(@Param("id") UUID id);

    AssetPage getPageBySourceRef(
            @Param("projectId") String projectId,
            @Param("source") String source,
            @Param("sourceRef") String sourceRef
    );

    void insertPage(AssetPage page);

    void updatePage(AssetPage page);

    void updatePageLifecycle(AssetPage page);

    int countActivePageCodeConflict(
            @Param("projectId") String projectId,
            @Param("code") String code,
            @Param("excludeId") UUID excludeId
    );

    // ---- Business Flows ----

    List<AssetBusinessFlow> listBusinessFlows(@Param("projectId") String projectId);

    List<AssetBusinessFlow> listBusinessFlowsByQuery(@Param("query") AssetListQuery query);

    long countBusinessFlowsByQuery(@Param("query") AssetListQuery query);

    AssetBusinessFlow getBusinessFlow(@Param("id") UUID id);

    AssetBusinessFlow getBusinessFlowIncludingInactive(@Param("id") UUID id);

    void insertBusinessFlow(AssetBusinessFlow flow);

    void updateBusinessFlow(AssetBusinessFlow flow);

    void updateBusinessFlowLifecycle(AssetBusinessFlow flow);

    int countActiveBusinessFlowCodeConflict(
            @Param("projectId") String projectId,
            @Param("code") String code,
            @Param("excludeId") UUID excludeId
    );

    // ---- Test Cases ----

    List<TestCaseRecord> listTestCases(@Param("projectId") String projectId);

    List<TestCaseRecord> listTestCasesByQuery(@Param("query") AssetListQuery query);

    long countTestCasesByQuery(@Param("query") AssetListQuery query);

    TestCaseRecord getTestCase(@Param("id") UUID id);

    TestCaseRecord getTestCaseIncludingInactive(@Param("id") UUID id);

    void insertTestCase(TestCaseRecord testCase);

    void updateTestCase(TestCaseRecord testCase);

    void updateTestCaseLifecycle(TestCaseRecord testCase);

    int countActiveTestCaseCodeConflict(
            @Param("projectId") String projectId,
            @Param("code") String code,
            @Param("excludeId") UUID excludeId
    );

    // ---- Test Case Steps ----

    List<TestCaseStep> listTestCaseSteps(@Param("caseId") UUID caseId);

    void insertTestCaseStep(TestCaseStep step);

    void deleteTestCaseSteps(@Param("caseId") UUID caseId);

    // ---- Trace Links ----

    List<TraceLink> listTraceLinks(
            @Param("requirementId") UUID requirementId,
            @Param("apiId") UUID apiId,
            @Param("pageId") UUID pageId,
            @Param("flowId") UUID flowId,
            @Param("caseId") UUID caseId
    );

    void insertTraceLink(TraceLink link);
}
