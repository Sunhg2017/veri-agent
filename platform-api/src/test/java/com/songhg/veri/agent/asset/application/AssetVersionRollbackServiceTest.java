package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.api.request.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
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
        return new AssetApi(
                id,
                "API-RB-1",
                "Rollback API",
                "Rollback test api",
                "GET",
                "/api/rollback",
                "MANUAL",
                null,
                "v1",
                null,
                null,
                projectId,
                "DRAFT",
                "ACTIVE",
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
