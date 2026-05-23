package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.response.AssetImpactAnalysisResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetImpactAnalysisServiceTest {

    private static final String PROJECT_ID = "project-impact";
    private static final UUID REQUIREMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID PAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID FLOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");

    private InMemoryAssetRepository repository;
    private RecordingPlatformContextClient contextClient;
    private AssetImpactAnalysisService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        contextClient = new RecordingPlatformContextClient();
        service = new AssetImpactAnalysisService(repository, new AssetProjectAuditService(contextClient));
    }

    @Test
    void expandsImpactAcrossTraceLinksAndWritesAudit() {
        seedFullTraceGraph();

        AssetImpactAnalysisResponse response = service.analyzeImpact(PROJECT_ID, "API", API_ID);

        assertThat(response.subjectType()).isEqualTo("API");
        assertThat(response.subjectId()).isEqualTo(API_ID);
        assertThat(response.requirementCount()).isEqualTo(1);
        assertThat(response.apiCount()).isEqualTo(1);
        assertThat(response.pageCount()).isEqualTo(1);
        assertThat(response.flowCount()).isEqualTo(1);
        assertThat(response.caseCount()).isEqualTo(1);
        assertThat(response.gaps()).isEmpty();
        assertThat(response.requirements()).singleElement().satisfies(node -> {
            assertThat(node.assetType()).isEqualTo("REQUIREMENT");
            assertThat(node.id()).isEqualTo(REQUIREMENT_ID);
        });
        assertThat(contextClient.auditEvents).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("IMPACT_ANALYSIS");
            assertThat(event.resourceType()).isEqualTo("ASSET_IMPACT");
            assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
            assertThat(event.result()).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void acceptsLegacyBusinessFlowSubjectAliasAndKeepsGapMessages() {
        repository.saveRequirement(requirement());
        repository.saveBusinessFlow(flow());
        repository.saveTraceLink(new TraceLink(UUID.randomUUID(), REQUIREMENT_ID, null, null, FLOW_ID, null, Instant.EPOCH));

        AssetImpactAnalysisResponse response = service.analyzeImpact(PROJECT_ID, "business_flow", FLOW_ID);

        assertThat(response.subjectType()).isEqualTo("FLOW");
        assertThat(response.requirementCount()).isEqualTo(1);
        assertThat(response.flowCount()).isEqualTo(1);
        assertThat(response.gaps()).containsExactly(
                "需求 REQ-IMPACT 缺少 API 覆盖",
                "需求 REQ-IMPACT 缺少页面覆盖",
                "需求 REQ-IMPACT 缺少测试用例覆盖"
        );
    }

    @Test
    void rejectsMissingSubjectAsset() {
        repository.saveRequirement(requirement());

        assertThatThrownBy(() -> service.analyzeImpact(PROJECT_ID, "CASE", CASE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).contains("影响分析资产不存在: CASE/" + CASE_ID);
                });
    }

    private void seedFullTraceGraph() {
        repository.saveRequirement(requirement());
        repository.saveApi(api());
        repository.savePage(page());
        repository.saveBusinessFlow(flow());
        repository.saveTestCase(testCase());
        repository.saveTraceLink(new TraceLink(
                UUID.randomUUID(),
                REQUIREMENT_ID,
                API_ID,
                PAGE_ID,
                FLOW_ID,
                CASE_ID,
                Instant.EPOCH
        ));
    }

    private static AssetRequirement requirement() {
        return new AssetRequirement(
                REQUIREMENT_ID,
                "REQ-IMPACT",
                "影响分析需求",
                "Description",
                "MANUAL",
                null,
                null,
                null,
                "APPROVED",
                "HIGH",
                PROJECT_ID,
                null,
                1,
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetApi api() {
        return new AssetApi(
                API_ID,
                "API-IMPACT",
                "影响分析 API",
                "Description",
                "GET",
                "/impact",
                "MANUAL",
                null,
                "v1",
                "{}",
                "{}",
                PROJECT_ID,
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetPage page() {
        return new AssetPage(
                PAGE_ID,
                "PAGE-IMPACT",
                "影响分析页面",
                "/impact",
                "MANUAL",
                null,
                null,
                "{}",
                null,
                PROJECT_ID,
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetBusinessFlow flow() {
        return new AssetBusinessFlow(
                FLOW_ID,
                "FLOW-IMPACT",
                "影响分析业务流",
                "Description",
                "{}",
                "HIGH",
                PROJECT_ID,
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static TestCaseRecord testCase() {
        return new TestCaseRecord(
                CASE_ID,
                "TC-IMPACT",
                "影响分析用例",
                "Description",
                PROJECT_ID,
                REQUIREMENT_ID,
                API_ID,
                "MANUAL",
                null,
                "APPROVED",
                "HIGH",
                null,
                List.of(),
                1,
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static class RecordingPlatformContextClient implements PlatformContextClient {

        private final List<AuditEvent> auditEvents = new ArrayList<>();

        @Override
        public ProjectContext getProjectContext(String projectId) {
            return new ProjectContext(projectId, "ACTIVE", "INTERNAL", false);
        }

        @Override
        public void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
            auditEvents.add(new AuditEvent(action, resourceType, resourceId, scopeId, result));
        }
    }

    private record AuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
    }
}
