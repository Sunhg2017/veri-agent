package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.view.PageResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
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



class AssetVersionRollbackServiceTest {

    private static final String PROJECT_ID = "project-rollback";
    private static final String OTHER_PROJECT_ID = "project-other";
    private static final UUID REQUIREMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID CONFLICT_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID PAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000551");
    private static final UUID FLOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000571");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");

    private InMemoryAssetRepository repository;
    private RecordingPlatformContextClient contextClient;
    private AssetVersionHistoryService versionHistoryService;
    private AssetVersionRollbackService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        contextClient = new RecordingPlatformContextClient();
        versionHistoryService = new AssetVersionHistoryService(
                repository,
                new ObjectMapper().findAndRegisterModules()
        );
        service = new AssetVersionRollbackService(
                repository,
                new ObjectMapper().findAndRegisterModules(),
                versionHistoryService,
                new AssetProjectAuditService(contextClient)
        );
    }

    @Test
    void rollbacksRequirementSnapshotAndRecordsAuditAndHistory() {
        AssetRequirement created = requirement(
                REQUIREMENT_ID,
                "REQ-RB-1",
                1,
                "回滚基线",
                "DRAFT",
                "HIGH",
                "IMPORT",
                "SRC-RB-1",
                "ACTIVE",
                null,
                null
        );
        versionHistoryService.recordRequirementCreated(created);
        repository.saveRequirement(requirement(
                REQUIREMENT_ID,
                "REQ-RB-1",
                3,
                "已归档版本",
                "APPROVED",
                "MEDIUM",
                "IMPORT",
                "SRC-RB-1",
                "ARCHIVED",
                Instant.parse("2026-05-24T06:00:00Z"),
                null
        ));

        RequirementResponse response = service.rollbackRequirementVersion(
                REQUIREMENT_ID,
                1,
                new RollbackAssetVersionRequest("restore baseline")
        );

        AssetRequirement stored = repository.requirementIncludingInactive(REQUIREMENT_ID).orElseThrow();
        assertThat(response.title()).isEqualTo("回滚基线");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.lifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(response.archivedAt()).isNull();
        assertThat(response.version()).isEqualTo(4);
        assertThat(stored.updatedAt()).isAfter(stored.createdAt());
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("ROLLBACK");
                    assertThat(event.resourceType()).isEqualTo("REQUIREMENT");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                    assertThat(event.result()).isEqualTo("SUCCEEDED");
                });

        AssetVersionHistoryResponse history = versionHistoryService
                .responses("REQUIREMENT", REQUIREMENT_ID)
                .getFirst();
        assertThat(history.changeType()).isEqualTo("ROLLBACK");
        assertThat(history.changedFields()).contains("title", "status", "priority", "lifecycleStatus", "archivedAt");
        assertThat(history.snapshot().path("title").asText()).isEqualTo("回滚基线");
    }

    @Test
    void rejectsRequirementRollbackWhenSnapshotMissesRequiredTitle() {
        repository.saveRequirement(requirement(
                REQUIREMENT_ID,
                "REQ-RB-1",
                2,
                "当前版本",
                "APPROVED",
                "HIGH",
                "MANUAL",
                null,
                "ACTIVE",
                null,
                null
        ));
        repository.saveVersionHistory(history("REQUIREMENT", REQUIREMENT_ID, 1, """
                {
                  "status": "DRAFT",
                  "priority": "HIGH",
                  "lifecycleStatus": "ACTIVE"
                }
                """));

        assertThatThrownBy(() -> service.rollbackRequirementVersion(
                REQUIREMENT_ID,
                1,
                new RollbackAssetVersionRequest("broken snapshot")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("版本快照缺少字段: title");
                });

        AssetRequirement stored = repository.requirementIncludingInactive(REQUIREMENT_ID).orElseThrow();
        assertThat(stored.title()).isEqualTo("当前版本");
        assertThat(contextClient.auditEvents).isEmpty();
        assertThat(versionHistoryService.responses("REQUIREMENT", REQUIREMENT_ID)).hasSize(1);
    }

    @Test
    void rejectsDeletedRequirementRestoreWhenImportSourceRefConflicts() {
        repository.saveRequirement(requirement(
                REQUIREMENT_ID,
                "REQ-RB-1",
                2,
                "已删除版本",
                "APPROVED",
                "HIGH",
                "IMPORT",
                "SRC-RB-1",
                "DELETED",
                null,
                Instant.EPOCH
        ));
        repository.saveRequirement(requirement(
                CONFLICT_ID,
                "REQ-RB-OTHER",
                1,
                "占用导入来源",
                "DRAFT",
                "MEDIUM",
                "IMPORT",
                "SRC-RB-1",
                "ACTIVE",
                null,
                null
        ));
        repository.saveVersionHistory(history("REQUIREMENT", REQUIREMENT_ID, 1, """
                {
                  "title": "待恢复需求",
                  "description": "restore",
                  "sourceUrl": null,
                  "acceptanceCriteria": null,
                  "status": "DRAFT",
                  "priority": "HIGH",
                  "tags": "restore",
                  "lifecycleStatus": "ACTIVE",
                  "archivedAt": null,
                  "deletedAt": null
                }
                """));

        assertThatThrownBy(() -> service.rollbackRequirementVersion(
                REQUIREMENT_ID,
                1,
                new RollbackAssetVersionRequest("restore")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).contains("同项目下导入来源已被其他需求占用");
                });

        AssetRequirement stored = repository.requirementIncludingInactive(REQUIREMENT_ID).orElseThrow();
        assertThat(stored.deletedAt()).isEqualTo(Instant.EPOCH);
        assertThat(contextClient.auditEvents).isEmpty();
        assertThat(versionHistoryService.responses("REQUIREMENT", REQUIREMENT_ID)).hasSize(1);
    }

    @Test
    void rollbacksTestCaseSnapshotStepsAndValidatesProjectReferences() {
        repository.saveRequirement(requirement(
                REQUIREMENT_ID,
                "REQ-RB-1",
                1,
                "关联需求",
                "APPROVED",
                "HIGH",
                "MANUAL",
                null,
                "ACTIVE",
                null,
                null
        ));
        repository.saveApi(api(API_ID, PROJECT_ID));
        TestCaseRecord created = testCase(
                CASE_ID,
                "TC-RB-1",
                1,
                "回滚用例V1",
                REQUIREMENT_ID,
                API_ID,
                List.of(
                        new TestCaseStep(UUID.randomUUID(), CASE_ID, 0, "打开登录页", "展示登录表单"),
                        new TestCaseStep(UUID.randomUUID(), CASE_ID, 1, "提交凭据", "进入首页")
                )
        );
        versionHistoryService.recordTestCaseCreated(created);
        repository.saveTestCase(testCase(CASE_ID, "TC-RB-1", 2, "回滚用例V2", null, null, List.of()));

        TestCaseResponse response = service.rollbackTestCaseVersion(
                CASE_ID,
                1,
                new RollbackAssetVersionRequest("restore test case")
        );

        TestCaseRecord stored = repository.testCaseIncludingInactive(CASE_ID).orElseThrow();
        assertThat(response.title()).isEqualTo("回滚用例V1");
        assertThat(response.requirementId()).isEqualTo(REQUIREMENT_ID);
        assertThat(response.apiId()).isEqualTo(API_ID);
        assertThat(response.version()).isEqualTo(3);
        assertThat(stored.steps()).extracting(TestCaseStep::action)
                .containsExactly("打开登录页", "提交凭据");
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("ROLLBACK");
                    assertThat(event.resourceType()).isEqualTo("TEST_CASE");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                });
        assertThat(versionHistoryService.responses("TEST_CASE", CASE_ID).getFirst().changeType()).isEqualTo("ROLLBACK");
    }

    @Test
    void rollbacksApiSnapshotAndRecordsAuditAndHistory() {
        AssetApi created = api(
                API_ID,
                "API-RB-1",
                "创建订单",
                "/api/orders",
                "1.0.0",
                "{\"type\":\"object\"}",
                null,
                "ACTIVE",
                "ACTIVE"
        );
        versionHistoryService.recordApiCreated(created);
        repository.saveApi(api(
                API_ID,
                "API-RB-1",
                "创建订单V2",
                "/api/v2/orders",
                "2.0.0",
                "{\"type\":\"object\",\"required\":[\"id\"]}",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}",
                "DEPRECATED",
                "ACTIVE"
        ));

        ApiResponseDTO response = service.rollbackApiVersion(
                API_ID,
                1,
                new RollbackAssetVersionRequest("restore api baseline")
        );

        AssetApi stored = repository.apiIncludingInactive(API_ID).orElseThrow();
        assertThat(response.summary()).isEqualTo("创建订单");
        assertThat(response.path()).isEqualTo("/api/orders");
        assertThat(response.version()).isEqualTo("1.0.0");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(stored.requestSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("ROLLBACK");
                    assertThat(event.resourceType()).isEqualTo("API");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                });
        AssetVersionHistoryResponse history = versionHistoryService.responses("API", API_ID).getFirst();
        assertThat(history.changeType()).isEqualTo("ROLLBACK");
        assertThat(history.changedFields()).contains("summary", "path", "version", "requestSchema", "status");
        assertThat(history.snapshot().path("revision").asInt()).isEqualTo(2);
    }

    @Test
    void rollbacksPageSnapshotAndRecordsAuditAndHistory() {
        AssetPage created = page(
                PAGE_ID,
                "PAGE-RB-1",
                "结算页",
                "/checkout",
                "MANUAL",
                null,
                null,
                "{\"type\":\"page\"}",
                null,
                "ACTIVE",
                "ACTIVE"
        );
        versionHistoryService.recordPageCreated(created);
        repository.savePage(page(
                PAGE_ID,
                "PAGE-RB-1",
                "结算页V2",
                "/checkout/v2",
                "FIGMA",
                "figma-node-1",
                "figma-v42",
                "{\"type\":\"page\",\"children\":[\"submit\"]}",
                "https://cdn.example.test/page.png",
                "DEPRECATED",
                "ACTIVE"
        ));

        PageResponse response = service.rollbackPageVersion(
                PAGE_ID,
                1,
                new RollbackAssetVersionRequest("restore page baseline")
        );

        AssetPage stored = repository.pageIncludingInactive(PAGE_ID).orElseThrow();
        assertThat(response.name()).isEqualTo("结算页");
        assertThat(response.urlPattern()).isEqualTo("/checkout");
        assertThat(response.source()).isEqualTo("MANUAL");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(stored.componentTree()).isEqualTo("{\"type\":\"page\"}");
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("ROLLBACK");
                    assertThat(event.resourceType()).isEqualTo("PAGE");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                });
        AssetVersionHistoryResponse history = versionHistoryService.responses("PAGE", PAGE_ID).getFirst();
        assertThat(history.changeType()).isEqualTo("ROLLBACK");
        assertThat(history.changedFields()).contains("name", "urlPattern", "source", "componentTree", "status");
        assertThat(history.snapshot().path("revision").asInt()).isEqualTo(2);
    }

    @Test
    void rollbacksBusinessFlowSnapshotAndRecordsAuditAndHistory() {
        AssetBusinessFlow created = businessFlow(
                FLOW_ID,
                "FLOW-RB-1",
                "下单主流程",
                "购物车到支付",
                "{\"nodes\":[\"cart\",\"pay\"]}",
                "HIGH",
                "DRAFT",
                "ACTIVE"
        );
        versionHistoryService.recordBusinessFlowCreated(created);
        repository.saveBusinessFlow(businessFlow(
                FLOW_ID,
                "FLOW-RB-1",
                "下单主流程V2",
                "覆盖优惠券",
                "{\"nodes\":[\"cart\",\"coupon\",\"pay\"]}",
                "CRITICAL",
                "ACTIVE",
                "ACTIVE"
        ));

        BusinessFlowResponse response = service.rollbackBusinessFlowVersion(
                FLOW_ID,
                1,
                new RollbackAssetVersionRequest("restore flow baseline")
        );

        AssetBusinessFlow stored = repository.businessFlowIncludingInactive(FLOW_ID).orElseThrow();
        assertThat(response.name()).isEqualTo("下单主流程");
        assertThat(response.description()).isEqualTo("购物车到支付");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(stored.flowJson()).isEqualTo("{\"nodes\":[\"cart\",\"pay\"]}");
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("ROLLBACK");
                    assertThat(event.resourceType()).isEqualTo("BUSINESS_FLOW");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                });
        AssetVersionHistoryResponse history = versionHistoryService.responses("BUSINESS_FLOW", FLOW_ID).getFirst();
        assertThat(history.changeType()).isEqualTo("ROLLBACK");
        assertThat(history.changedFields()).contains("name", "description", "flowJson", "priority", "status");
        assertThat(history.snapshot().path("revision").asInt()).isEqualTo(2);
    }

    @Test
    void rejectsTestCaseRollbackWhenRequirementBelongsToOtherProject() {
        repository.saveRequirement(requirement(
                REQUIREMENT_ID,
                "REQ-RB-1",
                1,
                "其他项目需求",
                "APPROVED",
                "HIGH",
                "MANUAL",
                null,
                "ACTIVE",
                null,
                null,
                OTHER_PROJECT_ID
        ));
        repository.saveTestCase(testCase(CASE_ID, "TC-RB-1", 2, "当前用例", null, null, List.of()));
        repository.saveVersionHistory(history("TEST_CASE", CASE_ID, 1, """
                {
                  "title": "跨项目快照",
                  "description": "invalid relation",
                  "requirementId": "00000000-0000-0000-0000-000000000401",
                  "apiId": null,
                  "status": "DRAFT",
                  "priority": "HIGH",
                  "tags": "cross-project",
                  "steps": [],
                  "lifecycleStatus": "ACTIVE",
                  "archivedAt": null,
                  "deletedAt": null
                }
                """));

        assertThatThrownBy(() -> service.rollbackTestCaseVersion(
                CASE_ID,
                1,
                new RollbackAssetVersionRequest("invalid relation")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("需求不属于当前项目");
                });

        assertThat(repository.testCaseIncludingInactive(CASE_ID).orElseThrow().title()).isEqualTo("当前用例");
        assertThat(contextClient.auditEvents).isEmpty();
        assertThat(versionHistoryService.responses("TEST_CASE", CASE_ID)).hasSize(1);
    }

    private static AssetRequirement requirement(
            UUID id,
            String code,
            int version,
            String title,
            String status,
            String priority,
            String source,
            String sourceRef,
            String lifecycleStatus,
            Instant archivedAt,
            Instant deletedAt
    ) {
        return requirement(
                id,
                code,
                version,
                title,
                status,
                priority,
                source,
                sourceRef,
                lifecycleStatus,
                archivedAt,
                deletedAt,
                PROJECT_ID
        );
    }

    private static AssetRequirement requirement(
            UUID id,
            String code,
            int version,
            String title,
            String status,
            String priority,
            String source,
            String sourceRef,
            String lifecycleStatus,
            Instant archivedAt,
            Instant deletedAt,
            String projectId
    ) {
        return new AssetRequirement(
                id,
                code,
                title,
                "Rollback test requirement",
                source,
                sourceRef,
                null,
                "验收标准",
                status,
                priority,
                projectId,
                "rollback",
                version,
                lifecycleStatus,
                archivedAt,
                deletedAt,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static TestCaseRecord testCase(
            UUID id,
            String code,
            int version,
            String title,
            UUID requirementId,
            UUID apiId,
            List<TestCaseStep> steps
    ) {
        return new TestCaseRecord(
                id,
                code,
                title,
                "Rollback test case",
                PROJECT_ID,
                requirementId,
                apiId,
                "MANUAL",
                null,
                "DRAFT",
                "MEDIUM",
                "rollback",
                steps,
                version,
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetApi api(UUID id, String projectId) {
        return api(id, "API-RB-1", "Rollback API", "/api/rollback", "v1", null, null, "DRAFT", "ACTIVE", projectId);
    }

    private static AssetApi api(
            UUID id,
            String code,
            String summary,
            String path,
            String version,
            String requestSchema,
            String responseSchema,
            String status,
            String lifecycleStatus
    ) {
        return api(id, code, summary, path, version, requestSchema, responseSchema, status, lifecycleStatus, PROJECT_ID);
    }

    private static AssetApi api(
            UUID id,
            String code,
            String summary,
            String path,
            String version,
            String requestSchema,
            String responseSchema,
            String status,
            String lifecycleStatus,
            String projectId
    ) {
        return new AssetApi(
                id,
                code,
                summary,
                "Rollback test api",
                "GET",
                path,
                "MANUAL",
                null,
                version,
                requestSchema,
                responseSchema,
                projectId,
                status,
                lifecycleStatus,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetPage page(
            UUID id,
            String code,
            String name,
            String urlPattern,
            String source,
            String sourceRef,
            String sourceVersion,
            String componentTree,
            String screenshotUrl,
            String status,
            String lifecycleStatus
    ) {
        return new AssetPage(
                id,
                code,
                name,
                urlPattern,
                source,
                sourceRef,
                sourceVersion,
                componentTree,
                screenshotUrl,
                PROJECT_ID,
                status,
                lifecycleStatus,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetBusinessFlow businessFlow(
            UUID id,
            String code,
            String name,
            String description,
            String flowJson,
            String priority,
            String status,
            String lifecycleStatus
    ) {
        return new AssetBusinessFlow(
                id,
                code,
                name,
                description,
                flowJson,
                priority,
                PROJECT_ID,
                status,
                lifecycleStatus,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static AssetVersionHistory history(String assetType, UUID assetId, int version, String snapshotJson) {
        return new AssetVersionHistory(
                UUID.randomUUID(),
                assetType,
                assetId,
                PROJECT_ID,
                version,
                "CREATE",
                "system",
                "",
                "{}",
                snapshotJson,
                "trace-rollback",
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
        public void writeAuditEvent(
                String action,
                String resourceType,
                String resourceId,
                String scopeId,
                String result
        ) {
            auditEvents.add(new AuditEvent(action, resourceType, resourceId, scopeId, result));
        }
    }

    private record AuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
    }
}
