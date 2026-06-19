package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import com.songhg.veri.agent.testdata.application.TestAccountLeaseService;
import com.songhg.veri.agent.testdata.application.TestAccountPoolService;
import com.songhg.veri.agent.testdata.application.TestDataActorResolver;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.TestDataPlatformContextClient;
import com.songhg.veri.agent.testdata.application.TestDataRunnerCredentialResolver;
import com.songhg.veri.agent.testdata.application.command.AcquireExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import com.songhg.veri.agent.uie2e.application.command.CancelUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.command.ReviewUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunPageRequest;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunExportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunSummaryResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eFlakyMark;
import com.songhg.veri.agent.uie2e.infrastructure.InMemoryUiE2eRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiE2eRunServiceTest {

    private static final String PROJECT_ID = "8e78afe7-f4e6-441c-a2ba-0d1041e3844f";
    private static final UUID PROJECT_UUID = UUID.fromString(PROJECT_ID);
    private static final UUID ENVIRONMENT_UUID = UUID.fromString("2d5eb6af-0fda-4ec2-bc7f-c30f69c307e4");
    private static final String ENVIRONMENT_KEY = "staging";
    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";
    private static final List<String> FORBIDDEN_SAMPLES = List.of(
            SECRET_REF,
            "secret://",
            "Authorization: Bearer ui-secret-token-123456",
            "cookie=ui-session-secret",
            "cookie: ui-session-secret",
            "lease token",
            "password=RunnerSecret-001",
            "token=ui-secret-token-123456",
            "https://portal.example.test/private"
    );

    @Test
    void createsBlockedRunAndReplaysByRequestKeyWhenRunnerDisabled() {
        Fixture fixture = fixture(false, List.of("https://portal.example.test"));
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);

        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-001",
                "manual smoke"
        ));

        assertThat(created.status()).isEqualTo("BLOCKED");
        assertThat(created.runnerMode()).isEqualTo("DISABLED");
        assertThat(created.failureCode()).isEqualTo("UI_E2E_RUNNER_DISABLED");
        assertThat(created.idempotentReplay()).isFalse();
        assertThat(created.accountSummary()).containsEntry("accountLeaseRef", leaseRef.toString());
        assertThat(created.accountSummary()).containsEntry("secretPlaintextReturned", false);
        assertThat(created.accountSummary().toString()).doesNotContain(SECRET_REF, "secret://", "password", "token");
        assertThat(created.executionSummary()).containsEntry("aggregateOnly", true);
        assertThat(created.executionSummary()).containsEntry("runnerReady", true);
        assertThat(created.executionSummary()).containsEntry("runnerDefaultDisabled", true);
        assertThat(created.executionSummary()).containsEntry("rawArtifactDownloadReady", false);
        assertThat(created.executionSummary()).containsEntry("stepResultCount", 0);
        assertThat(created.stepResults()).isEmpty();
        assertThat(created.artifacts()).isEmpty();
        assertThat(created.flakyMark()).isNull();
        assertThat(created.finishedAt()).isNotNull();

        UiE2eRunDetailResponse replayed = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-001",
                "manual smoke again"
        ));

        assertThat(replayed.id()).isEqualTo(created.id());
        assertThat(replayed.idempotentReplay()).isTrue();
    }

    @Test
    void rejectsCrossProjectOrInvalidLease() {
        Fixture fixture = fixture(false, List.of("https://portal.example.test"));
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID foreignLease = acquireLease(fixture, "project-beta");

        assertThatThrownBy(() -> fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                foreignLease,
                "run-request-002",
                null
        ))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
            assertThat(exception.getMessage()).isEqualTo("UI_E2E_ACCOUNT_LEASE_INVALID");
        });
    }

    @Test
    void rejectsResolvedBaseUrlOutsideAllowlist() {
        Fixture fixture = fixture(false, List.of("https://admin.example.test"));
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);

        assertThatThrownBy(() -> fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-003",
                null
        ))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
            assertThat(exception.getMessage()).isEqualTo("UI_E2E_BASE_URL_NOT_ALLOWED");
        });
    }

    @Test
    void cancelsRunningRunAndListsStoredRuns() {
        Fixture fixture = fixture(
                true,
                List.of("https://portal.example.test"),
                new RunningRunnerStub()
        );
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);

        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-004",
                "start managed"
        ));

        assertThat(created.status()).isEqualTo("RUNNING");
        assertThat(created.finishedAt()).isNull();
        assertThat(created.stepResults()).hasSize(2);
        assertThat(created.artifacts()).hasSize(2);
        assertThat(created.executionSummary()).containsEntry("stepResultCount", 2);
        assertThat(((Map<?, ?>) created.executionSummary().get("stepStatusCounts")).get("RUNNING")).isEqualTo(1);
        assertThat(((Map<?, ?>) created.executionSummary().get("stepStatusCounts")).get("SUCCEEDED")).isEqualTo(1);

        UiE2eRunDetailResponse canceled = fixture.service().cancelRun(
                created.id(),
                new CancelUiE2eRunCommand("operator canceled")
        );

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.failureCode()).isEqualTo("UI_E2E_RUNNER_CANCELED");
        assertThat(canceled.finishedAt()).isNotNull();

        UiE2eRunPageRequest request = new UiE2eRunPageRequest();
        request.setProjectId(PROJECT_ID);
        request.setKeyword("run-request-004");
        PageResponse<UiE2eRunSummaryResponse> page = fixture.service().runs(request);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(created.id());
            assertThat(item.status()).isEqualTo("CANCELED");
        });

        UiE2eRunDetailResponse terminalCancel = fixture.service().cancelRun(
                created.id(),
                new CancelUiE2eRunCommand("cancel again")
        );
        assertThat(terminalCancel.status()).isEqualTo("CANCELED");
    }

    @Test
    void exposesFlakyFallbackInRunSummaryAndDetail() {
        Fixture fixture = fixture(
                true,
                List.of("https://portal.example.test"),
                new RunningRunnerStub()
        );
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);
        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-004b",
                "start managed"
        ));
        Instant now = Instant.now();
        fixture.repository().upsertFlakyMark(new UiE2eFlakyMark(
                UUID.randomUUID(),
                PROJECT_ID,
                refs.sceneId(),
                created.id(),
                "CONFIRMED_FLAKY",
                "LOCATOR_DRIFT",
                "locator occasionally changes",
                "qa-tester",
                "qa-tester",
                now,
                now
        ));

        UiE2eRunDetailResponse detail = fixture.service().run(created.id());
        assertThat(detail.flakyMark()).isNotNull();
        assertThat(detail.flakyMark().status()).isEqualTo("CONFIRMED_FLAKY");
        assertThat(detail.executionSummary()).containsEntry("flakyStatus", "CONFIRMED_FLAKY");

        UiE2eRunPageRequest request = new UiE2eRunPageRequest();
        request.setProjectId(PROJECT_ID);
        request.setKeyword("run-request-004b");
        PageResponse<UiE2eRunSummaryResponse> page = fixture.service().runs(request);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(created.id());
            assertThat(item.flakyStatus()).isEqualTo("CONFIRMED_FLAKY");
        });
    }

    @Test
    void exportsAggregateOnlyRunSummary() {
        Fixture fixture = fixture(false, List.of("https://portal.example.test"));
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);
        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-005",
                "export"
        ));

        UiE2eRunExportResponse exported = fixture.service().exportRun(created.id());
        assertThat(exported.schemaVersion()).isEqualTo("wp7-run-export-v1");
        assertThat(exported.run().id()).isEqualTo(created.id());
        assertThat(exported.redactionPolicy())
                .containsEntry("aggregateOnly", true)
                .containsEntry("artifactDownloadReady", false)
                .containsEntry("runnerOutputExported", false);
    }

    @Test
    void redactsSensitiveSamplesFromManagedRunDetailAndExport() {
        Fixture fixture = fixture(
                true,
                List.of("https://portal.example.test"),
                new SensitiveRunnerStub()
        );
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);

        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-redaction-001",
                "redaction regression"
        ));

        assertThat(created.status()).isEqualTo("FAILED");
        assertThat(created.runnerMode()).isEqualTo("MANAGED");
        assertThat(created.failureCode()).isEqualTo("UI_E2E_RUNNER_FAILED");
        assertRedactedText(created.failureSummary());

        assertThat(created.stepResults()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo("FAILED");
            assertThat(step.summary()).containsEntry("aggregateOnly", true);
            assertThat(step.summary()).containsEntry("rawDomStored", false);
            assertThat(step.summary()).containsEntry("rawRunnerOutputStored", false);
            assertThat(step.summary()).containsEntry("secretPlaintextStored", false);
            assertThat(step.summary()).containsEntry("unsafeSummaryKeysFiltered", true);
            assertThat(step.summary()).doesNotContainKeys("runnerStdout", "password");
            assertRedactedObject(step.summary());
        });

        assertThat(created.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.artifactType()).isEqualTo("LOG");
            assertThat(artifact.captureStatus()).isEqualTo("CAPTURED");
            assertThat(artifact.redactionFlags()).containsEntry("aggregateOnly", true);
            assertThat(artifact.redactionFlags()).containsEntry("rawArtifactStored", false);
            assertThat(artifact.redactionFlags()).containsEntry("rawArtifactDownloadReady", false);
            assertThat(artifact.redactionFlags()).containsEntry("secretPlaintextStored", false);
            assertThat(artifact.redactionFlags()).containsEntry("storageCredentialStored", false);
            assertThat(artifact.redactionFlags()).containsEntry("unsafeRedactionFlagKeysFiltered", true);
            assertThat(artifact.redactionFlags()).doesNotContainKeys("stdoutExcerpt", "cookieHeader");
            assertThat(artifact.storageRef()).contains("[REDACTED_URL]");
            assertThat(artifact.storageRef()).doesNotContain("portal.example.test/private");
            assertRedactedObject(artifact.redactionFlags());
        });

        assertRedactedObject(created.accountSummary());
        assertRedactedObject(created.executionSummary());

        UiE2eRunExportResponse exported = fixture.service().exportRun(created.id());
        assertThat(exported.schemaVersion()).isEqualTo("wp7-run-export-v1");
        assertThat(exported.redactionPolicy())
                .containsEntry("aggregateOnly", true)
                .containsEntry("artifactDownloadReady", false)
                .containsEntry("runnerOutputExported", false);
        assertRedactedObject(exported.run().accountSummary());
        assertRedactedText(exported.run().failureSummary());
        assertRedactedObject(exported.run().stepResults().getFirst().summary());
        assertRedactedObject(exported.run().artifacts().getFirst().redactionFlags());
        assertThat(exported.run().artifacts().getFirst().storageRef()).contains("[REDACTED_URL]");
    }

    @Test
    void healthSignalsRunControlPlaneReadyWhileRunnerStaysDefaultDisabled() {
        UiE2eHealthService service = new UiE2eHealthService(new UiE2eProperties(
                true,
                false,
                "managed",
                120,
                600,
                1,
                4096,
                3,
                4,
                List.of("https://portal.example.test"),
                true,
                false,
                true,
                true
        ));

        var health = service.health();
        assertThat(health.policy()).containsEntry("runControlPlaneReady", true);
        assertThat(health.policy()).containsEntry("runnerPortReady", true);
        assertThat(health.policy()).containsEntry("artifactManifestReady", true);
        assertThat(health.policy()).containsEntry("flakyMarkReady", true);
        assertThat(health.policy()).containsEntry("runnerDefaultDisabled", true);
        assertThat(health.policy()).containsEntry("managedPreviewRunnerReady", false);
    }

    @Test
    void healthShowsCredentialInjectionAdapterReadyWhenResolverIsAvailable() {
        TestDataCrossWpReferenceService referenceService = mock(TestDataCrossWpReferenceService.class);
        when(referenceService.runnerCredentialInjectionReady()).thenReturn(true);
        UiE2eHealthService service = new UiE2eHealthService(new UiE2eProperties(
                true,
                true,
                "managed",
                120,
                600,
                1,
                4096,
                3,
                4,
                List.of("https://portal.example.test"),
                true,
                false,
                true,
                true
        ), referenceService);

        var health = service.health();
        assertThat(health.credentialPolicy()).containsEntry("credentialInjectionAdapterReady", true);
        assertThat(health.credentialPolicy()).containsEntry("credentialInjectionPreviewOnly", false);
    }

    @Test
    void createsBlockedPreviewRunWhenRunnerIsEnabledButCredentialInjectionIsPending() {
        Fixture fixture = fixture(true, List.of("https://portal.example.test"), List.of(), null);
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);

        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-preview-001",
                "preview"
        ));

        assertThat(created.status()).isEqualTo("BLOCKED");
        assertThat(created.runnerMode()).isEqualTo("MANAGED");
        assertThat(created.failureCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
        assertThat(created.finishedAt()).isNotNull();
        assertThat(created.stepResults()).isNotEmpty();
        assertThat(created.artifacts()).isNotEmpty();
        assertThat(created.executionSummary()).containsEntry("runnerDefaultDisabled", false);
        assertThat(created.executionSummary()).containsEntry("stepResultCount", created.stepResults().size());
        assertThat(created.executionSummary()).containsEntry("artifactManifestCount", created.artifacts().size());
        assertThat(((Map<?, ?>) created.executionSummary().get("stepStatusCounts")).get("BLOCKED")).isEqualTo(created.stepResults().size());
        assertThat(created.stepResults()).allSatisfy(step -> {
            assertThat(step.status()).isEqualTo("BLOCKED");
            assertThat(step.errorCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
            assertThat(step.summary()).containsEntry("previewOnly", true);
            assertThat(step.summary()).containsEntry("credentialInjectionReady", false);
        });
        assertThat(created.artifacts()).anySatisfy(artifact -> {
            assertThat(artifact.artifactType()).isEqualTo("LOG");
            assertThat(artifact.captureStatus()).isEqualTo("CAPTURED");
            assertThat(artifact.redactionFlags()).containsEntry("previewOnly", true);
        });
        assertThat(created.artifacts()).anySatisfy(artifact -> {
            assertThat(artifact.artifactType()).isEqualTo("SCREENSHOT");
            assertThat(artifact.captureStatus()).isEqualTo("BLOCKED");
        });
    }

    @Test
    void createsBlockedPreviewRunAfterCredentialResolutionSucceeds() {
        Fixture fixture = fixture(
                true,
                List.of("https://portal.example.test"),
                List.of(new AcceptingUiE2eSecretProvider()),
                null
        );
        SeededRunRefs refs = seedApprovedSceneAndBundle(fixture);
        UUID leaseRef = acquireLease(fixture);

        UiE2eRunDetailResponse created = fixture.service().createRun(new CreateUiE2eRunCommand(
                PROJECT_ID,
                refs.sceneId(),
                refs.bundleId(),
                ENVIRONMENT_KEY,
                "env:" + ENVIRONMENT_KEY,
                leaseRef,
                "run-request-preview-002",
                "preview after credential resolve"
        ));

        assertThat(created.status()).isEqualTo("BLOCKED");
        assertThat(created.failureCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
        assertThat(created.stepResults()).allSatisfy(step -> {
            assertThat(step.summary()).containsEntry("credentialInjectionReady", true);
            assertThat(step.summary()).containsEntry("secretProviderResolved", true);
            assertThat(step.summary()).containsEntry("secretProviderCode", "unit-test-provider");
        });
    }

    private Fixture fixture(boolean managedRunner, List<String> allowlistBaseUrls) {
        return fixture(managedRunner, allowlistBaseUrls, List.of(new AcceptingUiE2eSecretProvider()), null);
    }

    private Fixture fixture(
            boolean managedRunner,
            List<String> allowlistBaseUrls,
            com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort runnerPortOverride
    ) {
        return fixture(managedRunner, allowlistBaseUrls, List.of(new AcceptingUiE2eSecretProvider()), runnerPortOverride);
    }

    private Fixture fixture(
            boolean managedRunner,
            List<String> allowlistBaseUrls,
            List<SecretProvider> secretProviders,
            com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort runnerPortOverride
    ) {
        InMemoryUiE2eRepository repository = new InMemoryUiE2eRepository();
        UiE2ePlatformContextClient contextClient = mock(UiE2ePlatformContextClient.class);
        when(contextClient.projectContext(PROJECT_ID)).thenReturn(platformContext(PROJECT_ID));
        when(contextClient.projectContext("project-beta")).thenReturn(platformContext("project-beta"));
        doNothing().when(contextClient).writeAuditEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        UiE2eActorResolver actorResolver = mock(UiE2eActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp7-run-tester");

        ObjectMapper objectMapper = new ObjectMapper();
        UiE2eProperties properties = new UiE2eProperties(
                true,
                managedRunner,
                managedRunner ? "managed" : "disabled",
                300,
                1800,
                1,
                20 * 1024 * 1024L,
                20,
                2,
                allowlistBaseUrls,
                true,
                false,
                true,
                true
        );
        UiE2eSceneService sceneService = new UiE2eSceneService(
                repository,
                contextClient,
                actorResolver,
                mock(UiE2eCrossWpReferenceService.class),
                properties,
                objectMapper
        );
        UiE2eBundleService bundleService = new UiE2eBundleService(
                repository,
                actorResolver,
                contextClient,
                properties,
                new UiE2eBundleFactory(objectMapper),
                objectMapper
        );
        ManagementStore managementStore = managementStore("https://portal.example.test");
        TestDataFixture testDataFixture = testDataFixture(secretProviders);
        return new Fixture(
                repository,
                sceneService,
                bundleService,
                testDataFixture.referenceService(),
                testDataFixture.alphaPoolId(),
                testDataFixture.betaPoolId(),
                new UiE2eRunService(
                        repository,
                        actorResolver,
                        contextClient,
                        properties,
                        runnerPortOverride != null
                                ? runnerPortOverride
                                : managedRunner
                                ? new com.songhg.veri.agent.uie2e.infrastructure.ManagedPreviewUiE2eRunnerAdapter(
                                        repository,
                                        properties,
                                        testDataFixture.referenceService()
                                )
                                : new com.songhg.veri.agent.uie2e.infrastructure.DisabledUiE2eRunnerAdapter(),
                        new UiE2eRunEnvironmentResolver(managementStore),
                        testDataFixture.referenceService(),
                        objectMapper
                )
        );
    }

    private TestDataFixture testDataFixture(List<SecretProvider> secretProviders) {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = testDataContextClient();
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-runner-tester");
        TestDataProperties properties = new TestDataProperties(true, 10, 512, 60, 120, false, true);
        ObjectMapper objectMapper = new ObjectMapper();
        SecretProviderProperties secretProperties = new SecretProviderProperties(
                "0123456789abcdef0123456789abcdef",
                "v1",
                "",
                "",
                3,
                1,
                "",
                "",
                ""
        );
        TestAccountPoolService poolService = new TestAccountPoolService(
                repository,
                contextClient,
                actorResolver,
                properties,
                secretProperties,
                objectMapper
        );
        TestAccountLeaseService leaseService = new TestAccountLeaseService(
                repository,
                contextClient,
                actorResolver,
                properties,
                objectMapper
        );
        TestDataRunnerCredentialResolver runnerCredentialResolver = new TestDataRunnerCredentialResolver(
                repository,
                secretProviders,
                secretProperties,
                objectMapper
        );
        var alphaPool = poolService.createAccountPool(new CreateTestAccountPoolCommand(
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                "pool-runner",
                "Pool runner",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                60
        ));
        poolService.addAccount(alphaPool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of("applicationId", "app-alpha"),
                SECRET_REF,
                "HEALTHY",
                null
        ));
        var betaPool = poolService.createAccountPool(new CreateTestAccountPoolCommand(
                "project-beta",
                "app-beta",
                ENVIRONMENT_KEY,
                "pool-runner-beta",
                "Pool runner beta",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                60
        ));
        poolService.addAccount(betaPool.id(), new UpsertTestPooledAccountCommand(
                "admin-02",
                "Admin 02",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of("applicationId", "app-beta"),
                SECRET_REF,
                "HEALTHY",
                null
        ));
        return new TestDataFixture(
                new TestDataCrossWpReferenceService(
                        leaseService,
                        repository,
                        contextClient,
                        properties,
                        runnerCredentialResolver,
                        objectMapper
                ),
                alphaPool.id(),
                betaPool.id()
        );
    }

    private UUID acquireLease(Fixture fixture) {
        return acquireLease(fixture, PROJECT_ID);
    }

    private UUID acquireLease(Fixture fixture, String projectId) {
        TestDataCrossWpReferenceService referenceService = fixture.testDataCrossWpReferenceService();
        return referenceService.acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                projectId,
                PROJECT_ID.equals(projectId) ? "app-alpha" : "app-beta",
                ENVIRONMENT_KEY,
                PROJECT_ID.equals(projectId) ? fixture.alphaPoolId() : fixture.betaPoolId(),
                List.of("ADMIN"),
                "run-" + UUID.randomUUID(),
                60,
                "lease-" + UUID.randomUUID()
        )).accountLeaseRef();
    }

    private SeededRunRefs seedApprovedSceneAndBundle(Fixture fixture) {
        UiE2eSceneService sceneService = fixture.sceneService();
        UiE2eBundleService bundleService = fixture.bundleService();
        var scene = sceneService.createScene(new CreateUiE2eSceneCommand(
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                "portal-run-scene-" + UUID.randomUUID().toString().substring(0, 8),
                "后台管理员登录回归",
                "APPROVED",
                "HIGH",
                List.of("smoke"),
                Map.of(),
                List.of(UiE2eSceneServiceTest.step("LOGIN"))
        ));
        var bundle = bundleService.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));
        bundleService.submitReview(bundle.id(), new ReviewUiE2eBundleCommand("ready"));
        var approved = bundleService.approve(bundle.id(), new ReviewUiE2eBundleCommand("approved"));
        return new SeededRunRefs(scene.id(), approved.id());
    }

    private ManagementStore managementStore(String webUrl) {
        ManagementStore managementStore = mock(ManagementStore.class);
        when(managementStore.findEnvironmentRuntimeRef(org.mockito.ArgumentMatchers.any(ManagementStoreParams.class)))
                .thenReturn(new EnvironmentRuntimeRef(
                        ENVIRONMENT_UUID,
                        PROJECT_UUID,
                        ENVIRONMENT_KEY,
                        "Staging",
                        "https://api.example.test/runtime",
                        "ENABLED"
                ));
        when(managementStore.findEnvironmentRef(org.mockito.ArgumentMatchers.any(ManagementStoreParams.class)))
                .thenReturn(new EnvironmentRef(
                        ENVIRONMENT_UUID,
                        "Staging",
                        "ENABLED",
                        PROJECT_UUID,
                        "Project Alpha"
                ));
        when(managementStore.findEnvironmentConnectivityTarget(org.mockito.ArgumentMatchers.any(ManagementStoreParams.class)))
                .thenReturn(new EnvironmentConnectivityTargetRow(
                        ENVIRONMENT_UUID,
                        "Staging",
                        "ENABLED",
                        webUrl,
                        "https://api.example.test/runtime",
                        "{}"
                ));
        return managementStore;
    }

    private TestDataPlatformContextClient testDataContextClient() {
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext(PROJECT_ID)).thenReturn(platformContext(PROJECT_ID));
        when(contextClient.projectContext("project-beta")).thenReturn(platformContext("project-beta"));
        doNothing().when(contextClient).writeAuditEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        return contextClient;
    }

    private PlatformContext platformContext(String projectId) {
        return new PlatformContext(
                "PROJECT",
                projectId,
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        );
    }

    private record Fixture(
            InMemoryUiE2eRepository repository,
            UiE2eSceneService sceneService,
            UiE2eBundleService bundleService,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            UUID alphaPoolId,
            UUID betaPoolId,
            UiE2eRunService service
    ) {
    }

    private record SeededRunRefs(UUID sceneId, UUID bundleId) {
    }

    private record TestDataFixture(
            TestDataCrossWpReferenceService referenceService,
            UUID alphaPoolId,
            UUID betaPoolId
    ) {
    }

    private void assertRedactedText(String value) {
        assertThat(value).isNotBlank();
        assertThat(value).containsAnyOf("[REDACTED]", "[REDACTED_SECRET_REF]", "[REDACTED_URL]");
        assertThat(value).doesNotContain(FORBIDDEN_SAMPLES.toArray(String[]::new));
    }

    private void assertRedactedObject(Object value) {
        String rendered = String.valueOf(value);
        assertThat(rendered).doesNotContain(FORBIDDEN_SAMPLES.toArray(String[]::new));
    }

    private static final class RunningRunnerStub implements com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort {

        @Override
        public RunnerValidation validate(RunnerValidationRequest request) {
            return new RunnerValidation(true, null, null);
        }

        @Override
        public RunnerRunResult run(RunnerRunRequest request) {
            return new RunnerRunResult(
                    "RUNNING",
                    "MANAGED",
                    null,
                    null,
                    List.of(
                            new RunnerStepResult(null, 1, "SUCCEEDED", 120, null, null, Map.of("stepType", "LOGIN")),
                            new RunnerStepResult(null, 2, "RUNNING", 0, null, null, Map.of("stepType", "ASSERT"))
                    ),
                    List.of(
                            new RunnerArtifactManifest(
                                    "SCREENSHOT",
                                    "artifact://shot-001",
                                    "a".repeat(64),
                                    1024,
                                    Map.of("scanStatus", "clean"),
                                    "CAPTURED"
                            ),
                            new RunnerArtifactManifest(
                                    "LOG",
                                    null,
                                    null,
                                    0,
                                    Map.of("aggregateOnly", true),
                                    "FAILED"
                            )
                    )
            );
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            return new RunnerCancelResult(true, null, null);
        }
    }

    private static final class SensitiveRunnerStub implements com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort {

        @Override
        public RunnerValidation validate(RunnerValidationRequest request) {
            return new RunnerValidation(true, null, null);
        }

        @Override
        public RunnerRunResult run(RunnerRunRequest request) {
            return new RunnerRunResult(
                    "FAILED",
                    "MANAGED",
                    "UI_E2E_RUNNER_FAILED",
                    "Authorization: Bearer ui-secret-token-123456 secret://wp8/accounts/admin-01 lease token "
                            + "cookie=ui-session-secret password=RunnerSecret-001 https://portal.example.test/private",
                    List.of(new RunnerStepResult(
                            null,
                            1,
                            "FAILED",
                            321,
                            "ACCOUNT",
                            "UI_E2E_STEP_FAILED",
                            Map.of(
                                    "note", "cookie=ui-session-secret secret://wp8/accounts/admin-01",
                                    "runnerStdout", "Authorization: Bearer ui-secret-token-123456",
                                    "blockedReason", "lease token rotation pending",
                                    "nested", Map.of(
                                            "password", "password=RunnerSecret-001",
                                            "jumpUrl", "https://portal.example.test/private"
                                    ),
                                    "samples", List.of(
                                            "token=ui-secret-token-123456",
                                            "cookie: ui-session-secret"
                                    )
                            )
                    )),
                    List.of(new RunnerArtifactManifest(
                            "LOG",
                            "https://portal.example.test/private/logs/run-001",
                            "a".repeat(64),
                            1024,
                            Map.of(
                                    "scanResult", "secret://wp8/accounts/admin-01",
                                    "stdoutExcerpt", "Authorization: Bearer ui-secret-token-123456",
                                    "cookieHeader", "cookie=ui-session-secret",
                                    "blockedReason", "lease token redaction required",
                                    "nested", Map.of(
                                            "passwordHint", "password=RunnerSecret-001",
                                            "sourceUrl", "https://portal.example.test/private"
                                    )
                            ),
                            "CAPTURED"
                    ))
            );
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            return new RunnerCancelResult(true, null, null);
        }
    }

    private static final class AcceptingUiE2eSecretProvider implements SecretProvider {

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            if (!SECRET_REF.equals(secretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(secretRef, "Resolved-Runner-Password-001", "unit-test-provider", "v1"));
        }
    }
}
