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
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.InMemoryApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.AssetTestCaseService;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                10
        ));

        assertThat(runnerPort.runCalls()).isEqualTo(1);
        assertThat(response.run().status()).isEqualTo("FAILED");
        assertThat(response.run().runnerMode()).isEqualTo("MANAGED");
        assertThat(response.run().baseUrlHost()).isEqualTo(SMOKE_ALLOWED_HOST);
        assertThat(response.run().baseUrlDigest()).hasSize(64);
        assertThat(response.run().errorSummary())
                .contains("[REDACTED_BASE_URL]")
                .doesNotContain(SMOKE_BASE_URL, "runner-token-123456", "runnersecret123456");
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
                .doesNotContain(SMOKE_BASE_URL, "runner-token-123456", "case-secret-123456", "Bearer assertionsecret");
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
                1
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
    void runnerSmokeBlocksUnreviewedTargetBeforePortExecution() {
        RecordingRunnerPort runnerPort = new RecordingRunnerPort(RunnerScenario.MANAGED_ASSERTION_FAILURE);
        RunnerFixture fixture = runnerFixture(runnerPort);

        ApiAutomationRunDetailResponse response = fixture.service().createRun(new CreateApiAutomationRunCommand(
                fixture.bundleId(),
                "qa-blocked",
                "https://blocked." + SMOKE_ALLOWED_HOST + "/service",
                List.of(fixture.caseId()),
                10
        ));

        assertThat(runnerPort.runCalls()).isZero();
        assertThat(response.run().status()).isEqualTo("BLOCKED");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_TARGET_BLOCKED");
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("BLOCKED");
            assertThat(result.assertionSummary()).containsEntry("reason", "RUNNER_TARGET_BLOCKED");
        });
    }

    private RunnerFixture runnerFixture(ApiAutomationRunnerPort runnerPort) {
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
                        1_048_576,
                        "wp6-api-automation-v1",
                        true
                ),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
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
        return new RunnerFixture(service, bundleId, generated.cases().getFirst().id());
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
        EXTERNAL_TIMEOUT
    }

    private static final class RecordingRunnerPort implements ApiAutomationRunnerPort {

        private final RunnerScenario scenario;
        private final AtomicInteger runCalls = new AtomicInteger();

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
            };
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            return new RunnerCancelResult(false, "NOT_RUNNING", "runner smoke is synchronous");
        }

        private int runCalls() {
            return runCalls.get();
        }
    }

    private record RunnerFixture(
            ApiAutomationService service,
            UUID bundleId,
            UUID caseId
    ) {
    }
}
