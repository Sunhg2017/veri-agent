package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.AssetImportRequest;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.AssetImportResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



class AssetServiceCoreTest {

    private static final String PROJECT_ID = "project-core";

    private InMemoryAssetRepository repository;
    private RecordingPlatformContextClient contextClient;
    private AssetService service;
    private AssetImportExportService importExportService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        contextClient = new RecordingPlatformContextClient();
        service = new AssetService(repository, contextClient);
        importExportService = new AssetImportExportService(
                repository,
                new AssetProjectAuditService(contextClient),
                new ObjectMapper().findAndRegisterModules(),
                service
        );
    }

    @Test
    void rejectsInvalidRequirementStatusRegressionAndLeavesExistingVersionUntouched() {
        RequirementResponse requirement = service.createRequirement(requirementRequest("结算审批", "DRAFT", "HIGH", null, null));
        RequirementResponse approved = service.updateRequirement(requirement.id(),
                new UpdateRequirementRequest("结算审批已批准", "ready", "APPROVED", "HIGH", "billing"));

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.version()).isEqualTo(2);

        assertThatThrownBy(() -> service.updateRequirement(requirement.id(),
                new UpdateRequirementRequest("结算审批回草稿", "regression", "DRAFT", "HIGH", "billing")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("状态不允许从 APPROVED 变更为 DRAFT");
                });

        RequirementResponse afterFailure = service.getRequirement(requirement.id());
        assertThat(afterFailure.title()).isEqualTo("结算审批已批准");
        assertThat(afterFailure.status()).isEqualTo("APPROVED");
        assertThat(afterFailure.version()).isEqualTo(2);
        assertThat(contextClient.auditEvents)
                .anySatisfy(event -> {
                    assertThat(event.action()).isEqualTo("STATUS_CHANGE_DENIED");
                    assertThat(event.result()).isEqualTo("DENIED");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                });
    }

    @Test
    void importUpdatesDraftRequirementButRequiresManualReviewAfterApproval() {
        AssetImportResponse created = importExportService.importAssets(importRequest("""
                title,description,status,priority,sourceRef,tags,acceptanceCriteria
                登录需求,初始说明,DRAFT,HIGH,REQ-IMPORT-1,auth,初始验收
                """));

        assertThat(created.created()).isEqualTo(1);
        RequirementResponse first = service.findImportedRequirement(PROJECT_ID, "REQ-IMPORT-1").orElseThrow();
        assertThat(first.title()).isEqualTo("登录需求");
        assertThat(first.version()).isEqualTo(1);

        AssetImportResponse updated = importExportService.importAssets(importRequest("""
                title,description,status,priority,sourceRef,tags,acceptanceCriteria
                登录需求V2,更新说明,DRAFT,CRITICAL,REQ-IMPORT-1,auth,更新验收
                """));

        assertThat(updated.updated()).isEqualTo(1);
        RequirementResponse draftAfterMerge = service.findImportedRequirement(PROJECT_ID, "REQ-IMPORT-1").orElseThrow();
        assertThat(draftAfterMerge.title()).isEqualTo("登录需求V2");
        assertThat(draftAfterMerge.description()).isEqualTo("更新说明");
        assertThat(draftAfterMerge.priority()).isEqualTo("CRITICAL");
        assertThat(draftAfterMerge.version()).isEqualTo(2);

        service.updateRequirement(draftAfterMerge.id(),
                new UpdateRequirementRequest("登录需求V2", "更新说明", "APPROVED", "CRITICAL", "auth"));

        AssetImportResponse blocked = importExportService.importAssets(importRequest("""
                title,description,status,priority,sourceRef,tags,acceptanceCriteria
                登录需求V3,审批后变更,DRAFT,LOW,REQ-IMPORT-1,auth,审批后验收
                """));

        assertThat(blocked.failed()).isEqualTo(1);
        assertThat(blocked.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo("CONFLICT_REVIEW_REQUIRED");
            assertThat(item.status()).isEqualTo("FAILED");
            assertThat(item.message()).contains("人工处理差异");
        });
        RequirementResponse approvedAfterBlockedImport = service.findImportedRequirement(PROJECT_ID, "REQ-IMPORT-1").orElseThrow();
        assertThat(approvedAfterBlockedImport.title()).isEqualTo("登录需求V2");
        assertThat(approvedAfterBlockedImport.status()).isEqualTo("APPROVED");
        assertThat(approvedAfterBlockedImport.version()).isEqualTo(3);
    }

    @Test
    void rejectsOpenApiImportFormatForNonApiAssets() {
        assertThatThrownBy(() -> importExportService.importAssets(new AssetImportRequest(
                "REQUIREMENT",
                "OPENAPI",
                PROJECT_ID,
                true,
                "{}"
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("OpenAPI 导入仅支持 API 资产");
                });
    }

    @Test
    void rollbackRestoresHistoricalSnapshotAndLifecycleState() {
        RequirementResponse requirement = service.createRequirement(requirementRequest("回滚需求V1", "DRAFT", "HIGH", "v1", "baseline"));
        RequirementResponse updated = service.updateRequirement(requirement.id(),
                new UpdateRequirementRequest("回滚需求V2", "updated", "REVIEWING", "MEDIUM", "v2"));
        RequirementResponse archived = service.updateRequirementLifecycle(updated.id(),
                new UpdateAssetLifecycleRequest("ARCHIVED", "review paused"));

        assertThat(archived.lifecycleStatus()).isEqualTo("ARCHIVED");
        assertThat(service.listRequirements(new AssetListRequest()).total()).isZero();

        RequirementResponse rollback = service.rollbackRequirementVersion(requirement.id(), 1,
                new RollbackAssetVersionRequest("restore baseline"));

        assertThat(rollback.title()).isEqualTo("回滚需求V1");
        assertThat(rollback.description()).isEqualTo("baseline");
        assertThat(rollback.status()).isEqualTo("DRAFT");
        assertThat(rollback.priority()).isEqualTo("HIGH");
        assertThat(rollback.lifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(rollback.archivedAt()).isNull();
        assertThat(rollback.deletedAt()).isNull();
        assertThat(rollback.version()).isEqualTo(4);
        assertThat(service.listRequirements(new AssetListRequest()).total()).isEqualTo(1);
        assertThat(service.requirementVersions(requirement.id()).getFirst().changeType()).isEqualTo("ROLLBACK");
    }

    private static CreateRequirementRequest requirementRequest(
            String title,
            String status,
            String priority,
            String tags,
            String description
    ) {
        return new CreateRequirementRequest(
                title,
                description == null ? "service core test" : description,
                status,
                priority,
                PROJECT_ID,
                tags,
                null,
                null,
                null,
                null
        );
    }

    private static AssetImportRequest importRequest(String content) {
        return new AssetImportRequest("REQUIREMENT", "CSV", PROJECT_ID, false, content);
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
