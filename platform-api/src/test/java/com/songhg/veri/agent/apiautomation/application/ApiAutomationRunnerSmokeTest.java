package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.command.ReviewApiAutomationScriptBundleCommand;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunExportResponse;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.InMemoryApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.AssetTestCaseService;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiAutomationRunnerSmokeTest {

    private static final String PROJECT_ID = "project-alpha";
    private static final String SMOKE_BASE_URL = System.getProperty(
            "wp6.runner.smoke.baseUrl",
            "https://api.wp6-smoke.example.test/service"
    );
    private static final String SMOKE_ALLOWED_HOST = System.getProperty(
            "wp6.runner.smoke.allowedHost",
            URI.create(SMOKE_BASE_URL).getHost()
    );

    @Test
    void managedRunnerSmokePersistsOnlySanitizedFailureSummary() {
        RecordingRunnerPort runnerPort = new RecordingRunnerPort(RunnerScenario.MANAGED_ASSERTION_FAILURE);
        RunnerFixture fixture = runnerFixture(runnerPort);

        ApiAutomationRunDetailResponse response = fixture.service().createRun(new CreateApiAutomationRunCommand(
                fixture.bundleId(),
                "qa",
                SMOKE_BASE_URL,
                List.of(fixture.caseId()),
                10,
                List.of("secret://wp6/runner-smoke")
        ));

        assertThat(runnerPort.runCalls()).isEqualTo(1);
        assertThat(runnerPort.lastSecretRefDigests()).singleElement()
                .satisfies(digest -> assertThat(digest).startsWith("sha256:").hasSize(71));
        assertThat(runnerPort.lastSecrets()).singleElement().satisfies(secret -> {
            assertThat(secret.headerName()).isEqualTo("X-VA-WP6-Secret-1");
            assertThat(secret.secretRefDigest()).isEqualTo(runnerPort.lastSecretRefDigests().getFirst());
            assertThat(secret.value()).isEqualTo("runner-smoke-secret");
        });
        assertThat(runnerPort.lastSecretRefDigests().toString()).doesNotContain("secret://wp6/runner-smoke");
        assertThat(response.run().status()).isEqualTo("FAILED");
        assertThat(response.run().runnerMode()).isEqualTo("MANAGED");
        assertThat(response.run().baseUrlHost()).isEqualTo(SMOKE_ALLOWED_HOST);
        assertThat(response.run().baseUrlDigest()).hasSize(64);
        assertThat(response.run().errorSummary())
                .contains("[REDACTED_BASE_URL]")
                .doesNotContain(SMOKE_BASE_URL, "runner-token-123456", "runnersecret123456", "runner-smoke-secret");
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().status()).isEqualTo("FAILED");
        assertThat(response.results().getFirst().assertionSummary())
                .containsEntry("authorization", "[REDACTED]")
                .containsEntry("rawRequestResponseStored", false)
                .containsEntry("secretValuesStored", false)
                .containsEntry("aggregateOnly", true);
        assertThat(response.results().getFirst().toString())
                .doesNotContain(SMOKE_BASE_URL, "assertionsecret123456", "case-secret-123456", "session=secret123456");

        ApiAutomationRunExportResponse exported = fixture.service().exportRun(response.run().id());
        assertThat(exported.resultCounts()).containsEntry("FAILED", 1);
        assertThat(exported.redactionPolicy())
                .containsEntry("rawBaseUrlExported", false)
                .containsEntry("rawRequestResponseExported", false)
                .containsEntry("secretValuesExported", false);
        assertThat(exported.toString())
                .contains(SMOKE_ALLOWED_HOST)
                .doesNotContain(SMOKE_BASE_URL, "runner-token-123456", "case-secret-123456", "Bearer assertionsecret",
                        "runner-smoke-secret");
    }

    @Test
    void externalRunnerSmokePersistsTimeoutAsCaseLevelResult() {
        RecordingRunnerPort runnerPort = new RecordingRunnerPort(RunnerScenario.EXTERNAL_TIMEOUT);
        RunnerFixture fixture = runnerFixture(runnerPort);

        ApiAutomationRunDetailResponse response = fixture.service().createRun(new CreateApiAutomationRunCommand(
                fixture.bundleId(),
                "qa-timeout",
                SMOKE_BASE_URL,
                List.of(fixture.caseId()),
                1,
                null
        ));

        assertThat(runnerPort.runCalls()).isEqualTo(1);
        assertThat(response.run().status()).isEqualTo("TIMEOUT");
        assertThat(response.run().runnerMode()).isEqualTo("EXTERNAL");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_TIMEOUT");
        assertThat(response.run().errorSummary()).doesNotContain("timeout-token-123456");
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("TIMEOUT");
            assertThat(result.errorCode()).isEqualTo("RUNNER_TIMEOUT");
            assertThat(result.assertionSummary()).containsEntry("aggregateOnly", true);
        });
        assertThat(fixture.service().exportRun(response.run().id()).resultCounts()).containsEntry("TIMEOUT", 1);
    }

    @Test
    void runnerSmokeFoldsOversizedArtifactBeforeExport() {
        RecordingRunnerPort runnerPort = new RecordingRunnerPort(RunnerScenario.OVERSIZED_ARTIFACT);
        RunnerFixture fixture = runnerFixture(runnerPort, 128);

        ApiAutomationRunDetailResponse response = fixture.service().createRun(new CreateApiAutomationRunCommand(
                fixture.bundleId(),
                "qa-artifact-limit",
                SMOKE_BASE_URL,
                List.of(fixture.caseId()),
                10,
                null
        ));

        assertThat(runnerPort.runCalls()).isEqualTo(1);
        assertThat(response.run().status()).isEqualTo("FAILED");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_ARTIFACT_TOO_LARGE");
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("ERROR");
            assertThat(result.errorCode()).isEqualTo("RUNNER_ARTIFACT_TOO_LARGE");
            assertThat(result.assertionSummary())
                    .containsEntry("artifactTooLarge", true)
                    .containsEntry("artifactStored", false)
                    .containsEntry("artifactMaxBytes", 128)
                    .containsEntry("rawRequestResponseStored", false);
            assertThat(result.toString()).doesNotContain(RecordingRunnerPort.OVERSIZED_ARTIFACT_MARKER);
        });
        assertThat(fixture.service().exportRun(response.run().id()).toString())
                .doesNotContain(RecordingRunnerPort.OVERSIZED_ARTIFACT_MARKER);
    }

    @Test
    void runnerSmokeBlocksUnreviewedTargetBeforePortExecution() {
        RecordingRunnerPort runnerPort = new RecordingRunnerPort(RunnerScenario.MANAGED_ASSERTION_FAILURE);
        RunnerFixture fixture = runnerFixture(runnerPort);

        ApiAutomationRunDetailResponse response = fixture.service().createRun(new CreateApiAutomationRunCommand(
                fixture.bundleId(),
                "qa-blocked",
                "https://blocked." + SMOKE_ALLOWED_HOST + "/service",
                List.of(fixture.caseId()),
                10,
                null
        ));

        assertThat(runnerPort.runCalls()).isZero();
        assertThat(response.run().status()).isEqualTo("BLOCKED");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_TARGET_BLOCKED");
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("BLOCKED");
            assertThat(result.assertionSummary()).containsEntry("reason", "RUNNER_TARGET_BLOCKED");
        });
    }

    @Test
    void runnerSmokeCancelsInFlightAsyncRunThroughRunnerPort() {
        RecordingRunnerPort runnerPort = new RecordingRunnerPort(RunnerScenario.ASYNC_CANCEL_ACCEPTED);
        RunnerFixture fixture = runnerFixture(runnerPort);
        UUID runId = UUID.randomUUID();

        /*
         * createRun is intentionally synchronous today. This smoke seeds the control-plane state a future async runner
         * would leave behind after accepting work, then verifies cancelRun performs the same persisted state convergence.
         */
        fixture.repository().insertRun(inFlightRun(runId, fixture.bundleId()));

        ApiAutomationRunDetailResponse response = fixture.service().cancelRun(runId);

        assertThat(runnerPort.cancelCalls()).isEqualTo(1);
        assertThat(runnerPort.lastCanceledRunId()).isEqualTo(runId);
        assertThat(runnerPort.lastCancelRequest()).isEqualTo(new ApiAutomationRunnerPort.RunnerCancelRequest(
                runId,
                "runner-smoke-external-run-001",
                "EXTERNAL"
        ));
        assertThat(response.run().status()).isEqualTo("CANCELED");
        assertThat(response.run().runnerMode()).isEqualTo("EXTERNAL");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_CANCELED");
        assertThat(response.run().errorSummary())
                .contains("cancel accepted")
                .doesNotContain("cancel-token-123456", "cancel-secret-123456");
        assertThat(response.run().completedAt()).isNotNull();
        assertThat(fixture.repository().run(runId).orElseThrow().status()).isEqualTo("CANCELED");
        assertThat(fixture.repository().run(runId).orElseThrow().updatedBy()).isEqualTo("wp6-runner-smoke");

        ApiAutomationRunExportResponse exported = fixture.service().exportRun(runId);
        assertThat(exported.run().status()).isEqualTo("CANCELED");
        assertThat(exported.toString()).doesNotContain("cancel-token-123456", "cancel-secret-123456");
    }

    private RunnerFixture runnerFixture(ApiAutomationRunnerPort runnerPort) {
        return runnerFixture(runnerPort, 1_048_576);
    }

    private RunnerFixture runnerFixture(ApiAutomationRunnerPort runnerPort, int runnerArtifactMaxBytes) {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        when(contextClient.projectContext(PROJECT_ID)).thenReturn(new PlatformContext(
                "PROJECT",
                PROJECT_ID,
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("wp6-runner-smoke");
        ApiAutomationService service = new ApiAutomationService(
                repository,
                runnerPort,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(
                        65_536,
                        50,
                        true,
                        10,
                        10,
                        SMOKE_ALLOWED_HOST,
                        runnerArtifactMaxBytes,
                        "wp6-api-automation-v1",
                        true
                ),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper(),
                mock(AsyncTaskNotificationService.class),
                List.of(new StaticSecretProvider("secret://wp6/runner-smoke", "runner-smoke-secret"))
        );

        UUID specId = UUID.randomUUID();
        UUID assetApiId = UUID.randomUUID();
        ApiAutomationSpec spec = spec(specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/payments", "GET", "digest-payments", assetApiId));
        ApiAutomationGenerationTaskDetailResponse generated = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        PROJECT_ID,
                        specId,
                        List.of(assetApiId),
                        List.of(),
                        List.of("SMOKE"),
                        "FALLBACK_ONLY",
                        1,
                        "runner-smoke-" + UUID.randomUUID()
                )
        );
        UUID bundleId = generated.scriptBundles().getFirst().id();
        service.submitScriptBundleReview(bundleId, new ReviewApiAutomationScriptBundleCommand("runner smoke ready"));
        service.approveScriptBundle(bundleId, new ReviewApiAutomationScriptBundleCommand("runner smoke approved"));
        return new RunnerFixture(service, repository, bundleId, generated.cases().getFirst().id());
    }

    private ApiAutomationRun inFlightRun(UUID id, UUID bundleId) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationRun(
                id,
                PROJECT_ID,
                bundleId,
                "qa-async",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                SMOKE_ALLOWED_HOST,
                "RUNNING",
                30,
                1,
                "trc_wp6_async_cancel",
                "EXTERNAL",
                "runner-smoke-external-run-001",
                null,
                null,
                "wp6-runner-smoke",
                "wp6-runner-smoke",
                now,
                null,
                now,
                now
        );
    }

    private ApiAutomationSpec spec(UUID id) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationSpec(
                id,
                PROJECT_ID,
                "TEXT",
                null,
                "wp6-runner-smoke-openapi",
                "2026.06",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                128,
                "{}",
                "{}",
                "PARSED",
                OpenApiSpecParser.PARSER_VERSION,
                1,
                null,
                "tester",
                "tester",
                now,
                now,
                now
        );
    }

    private ApiAutomationEndpointSnapshot syncedEndpoint(
            ApiAutomationSpec spec,
            String path,
            String httpMethod,
            String schemaDigest,
            UUID assetApiId
    ) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationEndpointSnapshot(
                UUID.randomUUID(),
                spec.id(),
                spec.projectId(),
                "billing",
                httpMethod.toLowerCase() + "Billing",
                httpMethod,
                path,
                httpMethod + " " + path,
                "billing",
                1,
                false,
                "200,400",
                schemaDigest,
                "MATCHED",
                assetApiId,
                "{}",
                now,
                now,
                null,
                now,
                now
        );
    }

    private enum RunnerScenario {
        MANAGED_ASSERTION_FAILURE,
        EXTERNAL_TIMEOUT,
        OVERSIZED_ARTIFACT,
        ASYNC_CANCEL_ACCEPTED
    }

    private static final class RecordingRunnerPort implements ApiAutomationRunnerPort {

        private static final String OVERSIZED_ARTIFACT_MARKER = "raw-runner-artifact-should-not-persist";

        private final RunnerScenario scenario;
        private final AtomicInteger runCalls = new AtomicInteger();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private List<String> lastSecretRefDigests = List.of();
        private List<RunnerSecret> lastSecrets = List.of();
        private UUID lastCanceledRunId;
        private RunnerCancelRequest lastCancelRequest;

        private RecordingRunnerPort(RunnerScenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
            return new RunnerValidation(true, null, null);
        }

        @Override
        public RunnerRunResult run(RunnerRunRequest request) {
            runCalls.incrementAndGet();
            lastSecretRefDigests = request.secretRefDigests();
            lastSecrets = request.secrets();
            ApiAutomationCase automationCase = request.cases().getFirst();
            return switch (scenario) {
                case MANAGED_ASSERTION_FAILURE -> new RunnerRunResult(
                        "FAILED",
                        "MANAGED",
                        "RUNNER_FAILED",
                        "Assertion failed against " + request.baseUrl()
                                + " token=runner-token-123456 Bearer runnersecret123456",
                        List.of(new RunnerCaseResult(
                                automationCase.id(),
                                "FAILED",
                                42,
                                """
                                        {
                                          "statusCode": 500,
                                          "authorization": "Bearer assertionsecret123456",
                                          "checks": ["token=case-secret-123456", "response bounded"],
                                          "nested": {"cookie": "session=secret123456"}
                                        }
                                        """,
                                "ASSERTION_FAILED",
                                "response mismatch at " + request.baseUrl() + " apiKey=case-key-123456"
                        ))
                );
                case EXTERNAL_TIMEOUT -> new RunnerRunResult(
                        "TIMEOUT",
                        "EXTERNAL",
                        "RUNNER_TIMEOUT",
                        "timeout after " + request.timeoutSeconds() + "s token=timeout-token-123456",
                        List.of(new RunnerCaseResult(
                                automationCase.id(),
                                "TIMEOUT",
                                1_000,
                                "{\"durationMs\":1000,\"rawRequestResponseStored\":true}",
                                "RUNNER_TIMEOUT",
                                "case timeout token=timeout-case-token123456"
                        ))
                );
                case OVERSIZED_ARTIFACT -> new RunnerRunResult(
                        "PASSED",
                        "MANAGED",
                        null,
                        null,
                        List.of(new RunnerCaseResult(
                                automationCase.id(),
                                "PASSED",
                                33,
                                "{\"stdout\":\"" + OVERSIZED_ARTIFACT_MARKER + " ".repeat(220) + "\"}",
                                null,
                                null
                        ))
                );
                case ASYNC_CANCEL_ACCEPTED -> new RunnerRunResult(
                        "RUNNING",
                        "EXTERNAL",
                        null,
                        null,
                        List.of(),
                        "runner-smoke-external-run-001"
                );
            };
        }

        @Override
        public RunnerCancelResult cancel(RunnerCancelRequest request) {
            cancelCalls.incrementAndGet();
            lastCancelRequest = request;
            lastCanceledRunId = request == null ? null : request.runId();
            if (scenario == RunnerScenario.ASYNC_CANCEL_ACCEPTED) {
                return new RunnerCancelResult(
                        true,
                        "RUNNER_CANCELED",
                        "cancel accepted token=cancel-token-123456 secret=cancel-secret-123456"
                );
            }
            return new RunnerCancelResult(false, "NOT_RUNNING", "runner smoke is synchronous");
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            return cancel(new RunnerCancelRequest(runId, null, null));
        }

        private int runCalls() {
            return runCalls.get();
        }

        private List<String> lastSecretRefDigests() {
            return lastSecretRefDigests;
        }

        private List<RunnerSecret> lastSecrets() {
            return lastSecrets;
        }

        private int cancelCalls() {
            return cancelCalls.get();
        }

        private UUID lastCanceledRunId() {
            return lastCanceledRunId;
        }

        private RunnerCancelRequest lastCancelRequest() {
            return lastCancelRequest;
        }
    }

    private static final class StaticSecretProvider implements SecretProvider {

        private final String secretRef;
        private final String value;

        private StaticSecretProvider(String secretRef, String value) {
            this.secretRef = secretRef;
            this.value = value;
        }

        @Override
        public Optional<ResolvedSecret> resolve(String requestedSecretRef, SecretResolveContext context) {
            if (!secretRef.equals(requestedSecretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(requestedSecretRef, value, "smoke-provider", "v1"));
        }
    }

    private record RunnerFixture(
            ApiAutomationService service,
            InMemoryApiAutomationRepository repository,
            UUID bundleId,
            UUID caseId
    ) {
    }
}
