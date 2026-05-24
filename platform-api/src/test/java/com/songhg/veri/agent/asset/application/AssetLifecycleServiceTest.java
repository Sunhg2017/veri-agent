package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.RequirementResponse;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
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

class AssetLifecycleServiceTest {

    private static final String PROJECT_ID = "project-lifecycle";
    private static final UUID REQUIREMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CONFLICT_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    private InMemoryAssetRepository repository;
    private RecordingPlatformContextClient contextClient;
    private AssetVersionHistoryService versionHistoryService;
    private AssetLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        contextClient = new RecordingPlatformContextClient();
        AssetProjectAuditService projectAuditService = new AssetProjectAuditService(contextClient);
        versionHistoryService = new AssetVersionHistoryService(
                repository,
                new ObjectMapper().findAndRegisterModules()
        );
        service = new AssetLifecycleService(repository, projectAuditService, versionHistoryService);
    }

    @Test
    void archivesRequirementAndRecordsAuditAndVersionHistory() {
        repository.saveRequirement(requirement(REQUIREMENT_ID, "REQ-LC-1", 1, "ACTIVE", null, null));

        RequirementResponse response = service.updateRequirementLifecycle(
                REQUIREMENT_ID,
                new UpdateAssetLifecycleRequest("ARCHIVED", "pause review")
        );

        AssetRequirement stored = repository.requirementIncludingInactive(REQUIREMENT_ID).orElseThrow();
        assertThat(response.lifecycleStatus()).isEqualTo("ARCHIVED");
        assertThat(response.version()).isEqualTo(2);
        assertThat(stored.archivedAt()).isNotNull();
        assertThat(stored.deletedAt()).isNull();
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("ARCHIVE");
                    assertThat(event.resourceType()).isEqualTo("REQUIREMENT");
                    assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
                    assertThat(event.result()).isEqualTo("SUCCEEDED");
                });

        AssetVersionHistoryResponse history = versionHistoryService
                .responses("REQUIREMENT", REQUIREMENT_ID)
                .getFirst();
        assertThat(history.changeType()).isEqualTo("ARCHIVE");
        assertThat(history.changedFields()).contains("lifecycleStatus", "archivedAt");
        assertThat(history.snapshot().path("lifecycleStatus").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    void rejectsDeletedRequirementTransitionToArchivedAndRecordsDeniedAudit() {
        Instant deletedAt = Instant.parse("2026-05-24T08:00:00Z");
        repository.saveRequirement(requirement(REQUIREMENT_ID, "REQ-LC-1", 2, "DELETED", null, deletedAt));

        assertThatThrownBy(() -> service.updateRequirementLifecycle(
                REQUIREMENT_ID,
                new UpdateAssetLifecycleRequest("ARCHIVED", "invalid transition")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("生命周期不允许从 DELETED 变更为 ARCHIVED");
                });

        AssetRequirement stored = repository.requirementIncludingInactive(REQUIREMENT_ID).orElseThrow();
        assertThat(stored.deletedAt()).isEqualTo(deletedAt);
        assertThat(versionHistoryService.responses("REQUIREMENT", REQUIREMENT_ID)).isEmpty();
        assertThat(contextClient.auditEvents)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("LIFECYCLE_CHANGE_DENIED");
                    assertThat(event.resourceType()).isEqualTo("REQUIREMENT");
                    assertThat(event.result()).isEqualTo("DENIED");
                });
    }

    @Test
    void rejectsDeletedRequirementRestoreWhenActiveCodeConflicts() {
        repository.saveRequirement(requirement(REQUIREMENT_ID, "REQ-LC-1", 2, "DELETED", null, Instant.EPOCH));
        repository.saveRequirement(requirement(CONFLICT_ID, "REQ-LC-1", 1, "ACTIVE", null, null));

        assertThatThrownBy(() -> service.updateRequirementLifecycle(
                REQUIREMENT_ID,
                new UpdateAssetLifecycleRequest("ACTIVE", "restore")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).contains("需求编码已被其他资产占用");
                });

        AssetRequirement stored = repository.requirementIncludingInactive(REQUIREMENT_ID).orElseThrow();
        assertThat(stored.deletedAt()).isEqualTo(Instant.EPOCH);
        assertThat(versionHistoryService.responses("REQUIREMENT", REQUIREMENT_ID)).isEmpty();
        assertThat(contextClient.auditEvents).isEmpty();
    }

    private static AssetRequirement requirement(
            UUID id,
            String code,
            int version,
            String lifecycleStatus,
            Instant archivedAt,
            Instant deletedAt
    ) {
        return new AssetRequirement(
                id,
                code,
                "生命周期需求",
                "Lifecycle test requirement",
                "MANUAL",
                null,
                null,
                null,
                "DRAFT",
                "MEDIUM",
                PROJECT_ID,
                null,
                version,
                lifecycleStatus,
                archivedAt,
                deletedAt,
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
