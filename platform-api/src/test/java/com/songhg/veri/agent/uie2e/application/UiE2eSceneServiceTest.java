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
import com.songhg.veri.agent.uie2e.application.command.ImportUiE2eSceneCommand;
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

    @Test
    void importsSeleniumIdeIntoEditableSceneDraft() {
        Fixture fixture = fixture(true);
        UiE2eSceneImportService importService = importService(fixture);

        var imported = importService.importScene(new ImportUiE2eSceneCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "SELENIUM_IDE",
                """
                        {
                          "id": "project-1",
                          "name": "Portal Login",
                          "tests": [
                            {
                              "id": "test-1",
                              "name": "Portal admin login",
                              "commands": [
                                { "command": "open", "target": "/login", "value": "" },
                                { "command": "type", "target": "id=username", "value": "admin@example.com" },
                                { "command": "type", "target": "id=password", "value": "${PASSWORD}" },
                                { "command": "click", "target": "css=button[type='submit']", "value": "" },
                                { "command": "assertText", "target": "css=h1", "value": "Dashboard" },
                                { "command": "pause", "target": "", "value": "1500" }
                              ]
                            }
                          ]
                        }
                        """,
                "",
                "",
                List.of("smoke")
        ));

        assertThat(imported.projectId()).isEqualTo("project-alpha");
        assertThat(imported.code()).isEqualTo("portal-admin-login");
        assertThat(imported.name()).isEqualTo("Portal admin login");
        assertThat(imported.tags()).contains("smoke", "imported", "selenium-ide");
        assertThat(imported.steps()).extracting(item -> item.stepType())
                .containsExactly("NAVIGATE", "LOGIN", "ASSERT", "WAIT");
        assertThat(imported.steps().get(1).actionSummary()).containsEntry("principalField", "#username");
        assertThat(imported.steps().get(1).actionSummary()).containsEntry("credentialField", "#password");
        assertThat(imported.steps().get(3).waitPolicy()).containsEntry("timeoutSeconds", 2);
        assertThat(imported.sourceSummary()).containsEntry("sourceType", "SELENIUM_IDE");
        assertThat(imported.importSummary()).containsEntry("editableDraft", true);
    }

    @Test
    void importsPlaywrightCodegenIntoEditableSceneDraft() {
        Fixture fixture = fixture(true);
        UiE2eSceneImportService importService = importService(fixture);

        var imported = importService.importScene(new ImportUiE2eSceneCommand(
                "project-alpha",
                null,
                null,
                "PLAYWRIGHT_CODEGEN",
                """
                        import { test, expect } from '@playwright/test';

                        test('Portal smoke', async ({ page }) => {
                          await page.goto('https://example.test/login');
                          await page.getByLabel('Email').fill('admin@example.com');
                          await page.getByLabel('Password').fill(process.env.LOGIN_PASSWORD);
                          await page.getByRole('button', { name: 'Sign in' }).click();
                          await expect(page).toHaveURL(/dashboard/);
                          await expect(page.getByText('Dashboard')).toHaveText('Dashboard');
                        });
                        """,
                "portal-smoke-import",
                "Portal smoke import",
                List.of()
        ));

        assertThat(imported.code()).isEqualTo("portal-smoke-import");
        assertThat(imported.name()).isEqualTo("Portal smoke import");
        assertThat(imported.tags()).contains("imported", "playwright-codegen");
        assertThat(imported.steps()).extracting(item -> item.stepType())
                .containsExactly("NAVIGATE", "LOGIN", "ASSERT", "ASSERT");
        assertThat(imported.steps().get(0).actionSummary()).containsEntry("targetPath", "/login");
        assertThat(imported.steps().get(1).locatorStrategy()).containsEntry("usernameSelector", "label=Email");
        assertThat(imported.steps().get(1).locatorStrategy()).containsEntry("passwordSelector", "label=Password");
        assertThat(imported.steps().get(2).assertionSummary()).containsEntry("urlContains", "dashboard");
        assertThat(imported.steps().get(3).assertionSummary()).containsEntry("expectedText", "Dashboard");
        assertThat(imported.warnings()).isEmpty();
    }

    private UiE2eSceneService service(boolean enabled) {
        return fixture(enabled).service();
    }

    private UiE2eSceneImportService importService(Fixture fixture) {
        return new UiE2eSceneImportService(
                fixture.contextClient(),
                fixture.properties(),
                fixture.objectMapper()
        );
    }

    static Fixture fixture(boolean enabled) {
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
        UiE2eProperties properties = new UiE2eProperties(
                enabled,
                false,
                "disabled",
                300,
                1800,
                1,
                20 * 1024 * 1024L,
                20,
                2,
                List.of(),
                true,
                false,
                false,
                true,
                false,
                true,
                "node",
                "../portal-web/node_modules",
                ""
        );
        ObjectMapper objectMapper = new ObjectMapper();
        return new Fixture(new UiE2eSceneService(
                repository,
                contextClient,
                actorResolver,
                crossWpReferenceService,
                properties,
                objectMapper
        ), repository, assetRepository, contextClient, actorResolver, properties, objectMapper);
    }

    static void seedWp3Refs(InMemoryAssetRepository repository, UUID pageRef, UUID flowRef, UUID caseRef, String projectId) {
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

    static CreateUiE2eSceneCommand.SceneStepPayload step(String stepType) {
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

    static record Fixture(
            UiE2eSceneService service,
            InMemoryUiE2eRepository repository,
            InMemoryAssetRepository assetRepository,
            UiE2ePlatformContextClient contextClient,
            UiE2eActorResolver actorResolver,
            UiE2eProperties properties,
            ObjectMapper objectMapper
    ) {
    }
}
