package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetCrossWpReportEvidenceService;
import com.songhg.veri.agent.asset.application.AssetProjectAuditService;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpReportEvidenceService;
import com.songhg.veri.agent.testdesign.application.TestDesignPlatformContextClient;
import com.songhg.veri.agent.testdesign.infrastructure.InMemoryTestDesignRepository;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.command.UpdateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.infrastructure.InMemoryUiE2eRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiE2eSceneServiceTest {

    @Test
    void rejectsBusinessApisWhenControlPlaneDisabled() {
        UiE2eSceneService service = service(false);

        assertThatThrownBy(() -> service.scenes(new com.songhg.veri.agent.uie2e.application.query.UiE2eScenePageRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void createsUpdatesAndArchivesSceneWithSourceValidation() {
        Fixture fixture = fixture(true);
        UiE2eSceneService service = fixture.service();
        UUID pageRef = UUID.randomUUID();
        UUID flowRef = UUID.randomUUID();
        UUID caseRef = UUID.randomUUID();
        seedWp3Refs(fixture.assetRepository(), pageRef, flowRef, caseRef, "project-alpha");

        var created = service.createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "portal-role-admin-login",
                "后台管理员登录并进入首页",
                "DRAFT",
                "HIGH",
                List.of("login", "rbac", "smoke"),
                Map.of(
                        "pageRefs", List.of(pageRef.toString()),
                        "flowRefs", List.of(flowRef.toString()),
                        "testCaseRefs", List.of(caseRef.toString())
                ),
                List.of(step("LOGIN"), step("ASSERT"))
        ));

        assertThat(created.projectId()).isEqualTo("project-alpha");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.riskLevel()).isEqualTo("HIGH");
        assertThat(created.tags()).containsExactly("login", "rbac", "smoke");
        assertThat(created.steps()).hasSize(2);
        assertThat(created.policy()).containsEntry("mutable", true);

        var updated = service.updateScene(created.id(), new UpdateUiE2eSceneCommand(
                null,
                null,
                "后台管理员登录并校验首页",
                "APPROVED",
                "CRITICAL",
                List.of("smoke", "critical"),
                Map.of("pageRefs", List.of(pageRef.toString())),
                List.of(step("LOGIN"))
        ));

        assertThat(updated.name()).isEqualTo("后台管理员登录并校验首页");
        assertThat(updated.status()).isEqualTo("APPROVED");
        assertThat(updated.riskLevel()).isEqualTo("CRITICAL");
        assertThat(updated.steps()).singleElement().extracting(item -> item.stepType()).isEqualTo("LOGIN");
        assertThat(updated.policy()).containsEntry("executable", true);

        var archived = service.archiveScene(created.id());
        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(archived.archivedAt()).isNotNull();

        assertThatThrownBy(() -> service.updateScene(created.id(), new UpdateUiE2eSceneCommand(
                null, null, "should fail", null, null, null, null, null
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void rejectsCrossProjectSourceSummaryBinding() {
        Fixture fixture = fixture(true);
        UiE2eSceneService service = fixture.service();
        UUID foreignPageRef = UUID.randomUUID();
        seedWp3Refs(fixture.assetRepository(), foreignPageRef, null, null, "project-beta");

        assertThatThrownBy(() -> service.createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                null,
                null,
                "cross-project-scene",
                "Cross project scene",
                "DRAFT",
                "MEDIUM",
                List.of("scope"),
                Map.of("pageRefs", List.of(foreignPageRef.toString())),
                List.of(step("LOGIN"))
        ))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
            assertThat(exception.getMessage()).isEqualTo("UI_E2E_RESOURCE_SCOPE_DENIED");
        });
    }

    @Test
    void rejectsArchivedStatusOnCreateAndPatch() {
        UiE2eSceneService service = service(true);

        assertThatThrownBy(() -> service.createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                null,
                null,
                "archived-create",
                "Archived create",
                "ARCHIVED",
                "MEDIUM",
                List.of(),
                Map.of(),
                List.of(step("LOGIN"))
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    private UiE2eSceneService service(boolean enabled) {
        return fixture(enabled).service();
    }

    private Fixture fixture(boolean enabled) {
        InMemoryUiE2eRepository repository = new InMemoryUiE2eRepository();
        InMemoryAssetRepository assetRepository = new InMemoryAssetRepository();
        InMemoryTestDesignRepository testDesignRepository = new InMemoryTestDesignRepository();
        UiE2ePlatformContextClient contextClient = mock(UiE2ePlatformContextClient.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        ));
        when(contextClient.projectContext("project-beta")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-beta",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        ));
        UiE2eActorResolver actorResolver = mock(UiE2eActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp7-tester");
        AssetCrossWpReportEvidenceService assetEvidenceService = new AssetCrossWpReportEvidenceService(
                assetRepository,
                new AssetProjectAuditService(new StaticAssetContextClient())
        );
        TestDesignPlatformContextClient testDesignContextClient = mock(TestDesignPlatformContextClient.class);
        when(testDesignContextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("configs"),
                Instant.now()
        ));
        when(testDesignContextClient.projectContext("project-beta")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-beta",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("configs"),
                Instant.now()
        ));
        TestDesignCrossWpReportEvidenceService testDesignEvidenceService = new TestDesignCrossWpReportEvidenceService(
                testDesignRepository,
                testDesignContextClient,
                new ObjectMapper()
        );
        UiE2eCrossWpReferenceService crossWpReferenceService = new UiE2eCrossWpReferenceService(
                assetEvidenceService,
                testDesignEvidenceService
        );
        return new Fixture(new UiE2eSceneService(
                repository,
                contextClient,
                actorResolver,
                crossWpReferenceService,
                new UiE2eProperties(enabled, false, "disabled", 300, 1800, 1, 20 * 1024 * 1024L, 20, 2, List.of(), true, false, true, true),
                new ObjectMapper()
        ), assetRepository);
    }

    private void seedWp3Refs(InMemoryAssetRepository repository, UUID pageRef, UUID flowRef, UUID caseRef, String projectId) {
        if (pageRef != null) {
            repository.savePage(new AssetPage(
                    pageRef,
                    "PAGE-" + pageRef.toString().substring(0, 8),
                    "Portal page",
                    "/portal",
                    "MANUAL",
                    null,
                    "v1",
                    "{}",
                    null,
                    projectId,
                    "APPROVED",
                    "ACTIVE",
                    null,
                    null,
                    Instant.EPOCH,
                    Instant.EPOCH
            ));
        }
        if (flowRef != null) {
            repository.saveBusinessFlow(new AssetBusinessFlow(
                    flowRef,
                    "FLOW-" + flowRef.toString().substring(0, 8),
                    "Portal flow",
                    "flow",
                    "{}",
                    "P1",
                    projectId,
                    "APPROVED",
                    "ACTIVE",
                    null,
                    null,
                    Instant.EPOCH,
                    Instant.EPOCH
            ));
        }
        if (caseRef != null) {
            repository.saveTestCase(new TestCaseRecord(
                    caseRef,
                    "CASE-" + caseRef.toString().substring(0, 8),
                    "Portal case",
                    "desc",
                    projectId,
                    null,
                    null,
                    "MANUAL",
                    null,
                    "APPROVED",
                    "P1",
                    "smoke",
                    List.of(),
                    1,
                    "ACTIVE",
                    null,
                    null,
                    Instant.EPOCH,
                Instant.EPOCH
            ));
        }
    }

    private CreateUiE2eSceneCommand.SceneStepPayload step(String stepType) {
        return new CreateUiE2eSceneCommand.SceneStepPayload(
                stepType,
                Map.of("action", stepType.toLowerCase()),
                Map.of("preferred", "testId"),
                Map.of("expected", "ok"),
                Map.of("timeoutSeconds", 5)
        );
    }

    private static class StaticAssetContextClient implements PlatformContextClient {

        @Override
        public ProjectContext getProjectContext(String projectId) {
            return new ProjectContext(projectId, "ACTIVE", "INTERNAL", false);
        }

        @Override
        public void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
        }
    }

    private record Fixture(
            UiE2eSceneService service,
            InMemoryAssetRepository assetRepository
    ) {
    }
}
