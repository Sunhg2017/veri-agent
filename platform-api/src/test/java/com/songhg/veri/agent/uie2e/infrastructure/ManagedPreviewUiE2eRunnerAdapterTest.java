package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
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
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedPreviewUiE2eRunnerAdapterTest {

    private static final String PROJECT_ID = "8e78afe7-f4e6-441c-a2ba-0d1041e3844f";
    private static final String ENVIRONMENT_KEY = "staging";
    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";
    private static final UUID SCENE_ID = UUID.fromString("a1111111-f4e6-441c-a2ba-0d1041e3844f");
    private static final UUID BUNDLE_ID = UUID.fromString("b2222222-f4e6-441c-a2ba-0d1041e3844f");
    private static final UUID RUN_ID = UUID.fromString("c3333333-f4e6-441c-a2ba-0d1041e3844f");

    @Test
    void blocksAtLoginWhenCredentialInjectionIsPending() {
        Fixture fixture = fixture(List.of(), true);
        ManagedPreviewUiE2eRunnerAdapter adapter = fixture.adapter();
        UUID leaseRef = fixture.leaseRef();
        Map<String, Object> accountSummary = fixture.accountSummary(leaseRef);

        UiE2eRunnerPort.RunnerValidation validation = adapter.validate(new UiE2eRunnerPort.RunnerValidationRequest(
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                accountSummary
        ));

        assertThat(validation.accepted()).isTrue();
        UiE2eRunnerPort.RunnerRunResult result = adapter.run(new UiE2eRunnerPort.RunnerRunRequest(
                RUN_ID,
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                accountSummary
        ));

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.runnerMode()).isEqualTo("MANAGED");
        assertThat(result.failureCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
        assertThat(result.failureSummary()).doesNotContain("password", "token", "cookie", "secret://");
        assertThat(result.stepResults()).hasSize(2);
        assertThat(result.stepResults().get(0).stepOrder()).isEqualTo(1);
        assertThat(result.stepResults().get(0).failureBucket()).isEqualTo("ACCOUNT");
        assertThat(result.stepResults().get(0).summary())
                .containsEntry("credentialInjectionReady", false)
                .containsEntry("blockedReason", "credentialInjectionPending");
        assertThat(result.stepResults().get(1).summary())
                .containsEntry("blockedReason", "upstreamStepBlocked")
                .containsEntry("blockedByStepOrder", 1);
        assertThat(result.artifacts()).anySatisfy(artifact -> {
            assertThat(artifact.artifactType()).isEqualTo("LOG");
            assertThat(artifact.captureStatus()).isEqualTo("CAPTURED");
            assertThat(artifact.storageRef()).contains("/managed-preview");
            assertThat(artifact.redactionFlags()).containsEntry("aggregateOnly", true);
        });
        assertThat(result.artifacts()).anySatisfy(artifact -> {
            assertThat(artifact.artifactType()).isEqualTo("SCREENSHOT");
            assertThat(artifact.captureStatus()).isEqualTo("BLOCKED");
            assertThat(artifact.redactionFlags()).containsEntry("captureBlockedReason", "browserExecutionNotProvisioned");
        });
    }

    @Test
    void blocksAtFirstStepWhenSceneHasNoLoginStep() {
        Fixture fixture = fixture(List.of(new AcceptingUiE2eSecretProvider()), false);
        ManagedPreviewUiE2eRunnerAdapter adapter = fixture.adapter();
        UUID leaseRef = fixture.leaseRef();
        Map<String, Object> accountSummary = fixture.accountSummary(leaseRef);

        UiE2eRunnerPort.RunnerRunResult result = adapter.run(new UiE2eRunnerPort.RunnerRunRequest(
                RUN_ID,
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                accountSummary
        ));

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.failureCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
        assertThat(result.stepResults()).hasSize(2);
        assertThat(result.stepResults().get(0).stepOrder()).isEqualTo(2);
        assertThat(result.stepResults().get(0).summary())
                .containsEntry("credentialInjectionReady", true)
                .containsEntry("credentialPlanReady", true)
                .containsEntry("secretProviderResolved", true)
                .containsEntry("credentialPlanType", "FORM_LOGIN")
                .containsEntry("credentialFormat", "ACCOUNT_PASSWORD")
                .containsEntry("credentialSchemaId", "wp7-account-password-v1")
                .containsEntry("principalSource", "ACCOUNT_SUMMARY")
                .containsEntry("principalIdentifierPresent", true)
                .containsEntry("credentialComponentCount", 2)
                .containsEntry("blockedReason", "browserExecutionPending");
        assertThat(result.stepResults().get(1).stepOrder()).isEqualTo(5);
        assertThat(result.stepResults().get(1).summary()).containsEntry("blockedByStepOrder", 2);
    }

    @Test
    void buildsStructuredCredentialPlanWithoutLeakingPlaintext() {
        Fixture fixture = fixture(List.of(new StructuredUiE2eSecretProvider()), true);
        ManagedPreviewUiE2eRunnerAdapter adapter = fixture.adapter();
        UUID leaseRef = fixture.leaseRef();
        Map<String, Object> accountSummary = fixture.accountSummary(leaseRef);

        UiE2eRunnerPort.RunnerRunResult result = adapter.run(new UiE2eRunnerPort.RunnerRunRequest(
                RUN_ID,
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                accountSummary
        ));

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.failureCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
        assertThat(result.failureSummary()).doesNotContain("runner-admin", "Structured-Password-002", "secret://");
        assertThat(result.stepResults()).first().satisfies(step -> {
            assertThat(step.summary())
                    .containsEntry("credentialInjectionReady", true)
                    .containsEntry("credentialPlanReady", true)
                    .containsEntry("credentialFormat", "STRUCTURED_LOGIN_FORM")
                    .containsEntry("credentialSchemaId", "wp7-login-form-v1")
                    .containsEntry("principalSource", "SECRET_PAYLOAD");
            assertThat(String.valueOf(step.summary())).doesNotContain("runner-admin", "Structured-Password-002", "secret://");
        });
    }

    @Test
    void blocksWithStableCodeWhenCredentialFormatIsUnsupported() {
        Fixture fixture = fixture(List.of(new UnsupportedUiE2eSecretProvider()), true);
        ManagedPreviewUiE2eRunnerAdapter adapter = fixture.adapter();
        UUID leaseRef = fixture.leaseRef();
        Map<String, Object> accountSummary = fixture.accountSummary(leaseRef);

        UiE2eRunnerPort.RunnerRunResult result = adapter.run(new UiE2eRunnerPort.RunnerRunRequest(
                RUN_ID,
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                accountSummary
        ));

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.failureCode()).isEqualTo("UI_E2E_CREDENTIAL_FORMAT_UNSUPPORTED");
        assertThat(result.failureSummary()).doesNotContain("line-1", "line-2", "secret://");
        assertThat(result.stepResults()).first().satisfies(step -> {
            assertThat(step.failureBucket()).isEqualTo("ACCOUNT");
            assertThat(step.summary())
                    .containsEntry("credentialInjectionReady", false)
                    .containsEntry("secretProviderResolved", true)
                    .containsEntry("credentialPlanReady", false)
                    .containsEntry("blockedReason", "credentialPlanUnsupported");
        });
    }

    @Test
    void rejectsIncompleteAccountContractBeforeRun() {
        Fixture fixture = fixture(List.of(new AcceptingUiE2eSecretProvider()), true);
        ManagedPreviewUiE2eRunnerAdapter adapter = fixture.adapter();
        UUID leaseRef = fixture.leaseRef();

        UiE2eRunnerPort.RunnerValidation validation = adapter.validate(new UiE2eRunnerPort.RunnerValidationRequest(
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                Map.of("accountLeaseRef", leaseRef.toString())
        ));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.errorCode()).isEqualTo("UI_E2E_ACCOUNT_LEASE_INVALID");
        assertThat(validation.errorSummary()).contains("account contract is incomplete");
    }

    @Test
    void reportsCancelAsNotSupportedForPreviewSnapshots() {
        Fixture fixture = fixture(List.of(new AcceptingUiE2eSecretProvider()), true);
        ManagedPreviewUiE2eRunnerAdapter adapter = fixture.adapter();

        UiE2eRunnerPort.RunnerCancelResult canceled = adapter.cancel(RUN_ID);

        assertThat(canceled.accepted()).isFalse();
        assertThat(canceled.errorCode()).isEqualTo("EXECUTION_RUNNER_NOT_READY");
        assertThat(canceled.errorSummary()).contains("terminal blocked snapshots");
    }

    private Fixture fixture(List<SecretProvider> secretProviders, boolean includeLoginStep) {
        InMemoryUiE2eRepository repository = new InMemoryUiE2eRepository();
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        repository.insertScene(new UiE2eScene(
                SCENE_ID,
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                "portal-preview-scene",
                "后台管理员登录回归",
                "APPROVED",
                "HIGH",
                "{\"pageRefs\":[\"page-1\"]}",
                "[\"login\",\"smoke\"]",
                "tester",
                "tester",
                null,
                now,
                now
        ));
        repository.replaceSceneSteps(
                SCENE_ID,
                includeLoginStep
                        ? List.of(
                                sceneStep(UUID.fromString("d4444444-f4e6-441c-a2ba-0d1041e3844f"), 1, "LOGIN", now),
                                sceneStep(UUID.fromString("e5555555-f4e6-441c-a2ba-0d1041e3844f"), 3, "ASSERT", now)
                        )
                        : List.of(
                                sceneStep(UUID.fromString("f6666666-f4e6-441c-a2ba-0d1041e3844f"), 2, "NAVIGATE", now),
                                sceneStep(UUID.fromString("07777777-f4e6-441c-a2ba-0d1041e3844f"), 5, "ASSERT", now)
                        )
        );
        repository.insertBundle(new UiE2eBundle(
                BUNDLE_ID,
                SCENE_ID,
                PROJECT_ID,
                "APPROVED",
                "sha256:bundle-preview",
                "{\"sceneCode\":\"portal-preview-scene\"}",
                "{\"fixtures\":[\"lease\"]}",
                "{\"staticCheck\":\"PASSED\"}",
                "tester",
                "reviewer",
                now,
                now,
                null,
                "tester",
                "tester",
                null,
                now,
                now
        ));

        UiE2eProperties properties = new UiE2eProperties(
                true,
                true,
                "managed",
                300,
                1800,
                1,
                20 * 1024 * 1024L,
                20,
                2,
                List.of("https://portal.example.test"),
                true,
                false,
                true,
                true
        );
        TestDataFixture testDataFixture = testDataFixture(secretProviders);
        TestDataCrossWpReferenceService referenceService = testDataFixture.referenceService();
        UUID leaseRef = acquireLease(referenceService, testDataFixture.alphaPoolId());
        return new Fixture(
                new ManagedPreviewUiE2eRunnerAdapter(repository, properties, referenceService),
                referenceService,
                leaseRef
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
        return new TestDataFixture(
                new TestDataCrossWpReferenceService(
                        leaseService,
                        repository,
                        contextClient,
                        properties,
                        runnerCredentialResolver,
                        objectMapper
                ),
                alphaPool.id()
        );
    }

    private UUID acquireLease(TestDataCrossWpReferenceService referenceService, UUID alphaPoolId) {
        return referenceService.acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                alphaPoolId,
                List.of("ADMIN"),
                "run-" + UUID.randomUUID(),
                60,
                "lease-" + UUID.randomUUID()
        )).accountLeaseRef();
    }

    private UiE2eSceneStep sceneStep(UUID id, int order, String stepType, Instant now) {
        return new UiE2eSceneStep(
                id,
                SCENE_ID,
                PROJECT_ID,
                order,
                stepType,
                "{\"submitAction\":\"click\"}",
                "{\"preferred\":\"testId\"}",
                "{\"successSignal\":\"url contains /dashboard\"}",
                "{\"timeoutSeconds\":5}",
                "tester",
                "tester",
                now,
                now
        );
    }

    private TestDataPlatformContextClient testDataContextClient() {
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext(PROJECT_ID)).thenReturn(platformContext(PROJECT_ID));
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
            ManagedPreviewUiE2eRunnerAdapter adapter,
            TestDataCrossWpReferenceService referenceService,
            UUID leaseRef
    ) {
        Map<String, Object> accountSummary(UUID accountLeaseRef) {
            var contract = referenceService.runnerAccountContract(accountLeaseRef);
            return Map.of(
                    "accountLeaseRef", contract.accountLeaseRef().toString(),
                    "status", contract.status(),
                    "accountKey", contract.account().accountKey(),
                    "displayName", contract.account().displayName(),
                    "secretRefDigest", contract.account().secretRefDigest()
            );
        }
    }

    private record TestDataFixture(
            TestDataCrossWpReferenceService referenceService,
            UUID alphaPoolId
    ) {
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

    private static final class StructuredUiE2eSecretProvider implements SecretProvider {

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            if (!SECRET_REF.equals(secretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(
                    secretRef,
                    "{\"schema\":\"wp7-login-form-v1\",\"username\":\"runner-admin\",\"password\":\"Structured-Password-002\"}",
                    "unit-test-provider",
                    "v2"
            ));
        }
    }

    private static final class UnsupportedUiE2eSecretProvider implements SecretProvider {

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            if (!SECRET_REF.equals(secretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(
                    secretRef,
                    "{\"schema\":\"wp7-unsupported-v1\",\"username\":\"runner-admin\",\"password\":\"Unsupported-Password-003\"}",
                    "unit-test-provider",
                    "v3"
            ));
        }
    }
}
