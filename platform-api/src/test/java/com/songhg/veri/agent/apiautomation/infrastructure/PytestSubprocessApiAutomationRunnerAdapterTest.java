package com.songhg.veri.agent.apiautomation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PytestSubprocessApiAutomationRunnerAdapterTest {

    @Test
    void executesPytestSubprocessAndMapsJunitResultsWithoutLeakingSecrets() {
        UUID caseId = UUID.randomUUID();
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) -> {
            assertThat(plan.command()).containsExactly(
                    "python3",
                    "-m",
                    "pytest",
                    "-q",
                    "--disable-warnings",
                    "--junitxml",
                    "runner-results.xml",
                    "--base-url",
                    "https://api.example.test/service",
                    "tests/test_generated_api.py"
            );
            assertThat(plan.environment()).containsEntry("WP6_RUNNER_SECRET_VALUE_1", "resolved-secret-value");
            assertThat(plan.environment().get("WP6_RUNNER_SECRET_HEADERS_JSON"))
                    .contains("X-VA-WP6-Secret-1")
                    .contains("sha256:secret-ref-digest")
                    .doesNotContain("resolved-secret-value");
            assertThat(plan.runnerAdapter()).isEqualTo("PYTEST_SUBPROCESS");
            assertThat(Files.readString(workingDirectory.resolve("tests/test_generated_api.py")))
                    .contains(caseId.toString())
                    .contains("/v1/items/1")
                    .doesNotContain("resolved-secret-value");
            writeJunit(workingDirectory.resolve("runner-results.xml"), caseId, null);
            return new PytestSubprocessApiAutomationRunnerAdapter.ProcessResult(
                    0,
                    false,
                    "pytest passed token=raw-secret-value",
                    ""
            );
        });
        PytestSubprocessApiAutomationRunnerAdapter adapter = adapter(executor);

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle(),
                List.of(automationCase(caseId, "GET", "/v1/items/{id}", 200)),
                "https://api.example.test/service",
                10,
                List.of("sha256:secret-ref-digest"),
                List.of(new ApiAutomationRunnerPort.RunnerSecret(
                        "X-VA-WP6-Secret-1",
                        "sha256:secret-ref-digest",
                        "resolved-secret-value"
                ))
        ));

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.runnerMode()).isEqualTo("EXTERNAL");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.caseId()).isEqualTo(caseId);
            assertThat(caseResult.status()).isEqualTo("PASSED");
            assertThat(caseResult.assertionSummaryJson())
                    .contains("\"runnerAdapter\":\"PYTEST_SUBPROCESS\"")
                    .contains("\"sandboxed\":false")
                    .contains("\"rawRequestResponseStored\":false")
                    .contains("\"secretValuesStored\":false")
                    .contains("\"stdoutBytes\":")
                    .doesNotContain("resolved-secret-value", "raw-secret-value");
        });
        assertThat(executor.workingDirectoryDeleted()).isTrue();
    }

    @Test
    void returnsFailedWhenJunitContainsFailure() {
        UUID caseId = UUID.randomUUID();
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) -> {
            writeJunit(workingDirectory.resolve("runner-results.xml"), caseId, "failure");
            return new PytestSubprocessApiAutomationRunnerAdapter.ProcessResult(
                    1,
                    false,
                    "assert token=raw-secret-value failed",
                    ""
            );
        });
        PytestSubprocessApiAutomationRunnerAdapter adapter = adapter(executor);

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(caseId));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.status()).isEqualTo("FAILED");
            assertThat(caseResult.errorCode()).isEqualTo("ASSERTION_FAILED");
            assertThat(caseResult.assertionSummaryJson()).doesNotContain("raw-secret-value");
        });
    }

    @Test
    void returnsTimeoutWhenSubprocessDoesNotFinish() {
        UUID caseId = UUID.randomUUID();
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) ->
                new PytestSubprocessApiAutomationRunnerAdapter.ProcessResult(
                        124,
                        true,
                        "",
                        "timed out"
                ));
        PytestSubprocessApiAutomationRunnerAdapter adapter = adapter(executor);

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(caseId));

        assertThat(result.status()).isEqualTo("TIMEOUT");
        assertThat(result.errorCode()).isEqualTo("RUNNER_TIMEOUT");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.status()).isEqualTo("TIMEOUT");
            assertThat(caseResult.errorCode()).isEqualTo("RUNNER_TIMEOUT");
        });
    }

    @Test
    void failsWhenSubprocessDoesNotProduceJunitEvidence() {
        UUID caseId = UUID.randomUUID();
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) ->
                new PytestSubprocessApiAutomationRunnerAdapter.ProcessResult(
                        0,
                        false,
                        "no junit xml",
                        ""
                ));
        PytestSubprocessApiAutomationRunnerAdapter adapter = adapter(executor);

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(caseId));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.status()).isEqualTo("ERROR");
            assertThat(caseResult.errorCode()).isEqualTo("RUNNER_FAILED");
        });
    }

    @Test
    void rejectsUnsafeSecretHeaderBeforeExecutingSubprocess() {
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) -> {
            throw new AssertionError("subprocess should not run for unsafe secret header");
        });
        PytestSubprocessApiAutomationRunnerAdapter adapter = adapter(executor);
        UUID caseId = UUID.randomUUID();

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle(),
                List.of(automationCase(caseId, "GET", "/v1/secure", 200)),
                "https://api.example.test/service",
                10,
                List.of("sha256:secret-ref-digest"),
                List.of(new ApiAutomationRunnerPort.RunnerSecret(
                        "Authorization",
                        "sha256:secret-ref-digest",
                        "Bearer raw-secret-value"
                ))
        ));

        assertThat(executor.calls).isEqualTo(0);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(result.toString()).doesNotContain("Bearer raw-secret-value");
    }

    @Test
    void rejectsUnsafeCaseMetadataBeforeExecutingSubprocess() {
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) -> {
            throw new AssertionError("subprocess should not run for unsafe case metadata");
        });
        PytestSubprocessApiAutomationRunnerAdapter adapter = adapter(executor);
        UUID caseId = UUID.randomUUID();

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle(),
                List.of(automationCase(caseId, "TRACE", "https://attacker.example/v1", 200)),
                "https://api.example.test/service",
                10,
                List.of(),
                List.of()
        ));

        assertThat(executor.calls).isEqualTo(0);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.caseId()).isEqualTo(caseId);
            assertThat(caseResult.status()).isEqualTo("ERROR");
        });
    }

    @Test
    void buildsDockerSandboxCommandWhenSandboxModeIsEnabled() {
        UUID caseId = UUID.randomUUID();
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) -> {
            assertThat(plan.command()).contains(
                    "docker",
                    "run",
                    "--rm",
                    "--network",
                    "bridge",
                    "--read-only",
                    "--tmpfs",
                    "/tmp",
                    "--tmpfs",
                    "/run",
                    "--cap-drop",
                    "ALL",
                    "--security-opt",
                    "no-new-privileges",
                    "--pids-limit",
                    "256",
                    "--memory",
                    "256m",
                    "--cpus",
                    "1",
                    "--user",
                    "65534:65534",
                    "-w",
                    "/workspace",
                    "veri-agent/wp6-pytest-runner:test"
            );
            assertThat(plan.command()).contains("python3", "-m", "pytest");
            assertThat(plan.command()).anySatisfy(item -> assertThat(item).contains("/workspace:rw"));
            assertThat(plan.command()).contains("-e", "WP6_RUNNER_SECRET_VALUE_1=resolved-secret-value");
            assertThat(plan.command()).contains("-e");
            assertThat(plan.command().toString()).contains("WP6_RUNNER_SECRET_HEADERS_JSON=");
            assertThat(plan.environment()).isEmpty();
            assertThat(plan.sandboxContainerName()).startsWith("wp6-pytest-");
            assertThat(plan.runnerAdapter()).isEqualTo("PYTEST_DOCKER_SANDBOX");
            writeJunit(workingDirectory.resolve("runner-results.xml"), caseId, null);
            return new PytestSubprocessApiAutomationRunnerAdapter.ProcessResult(0, false, "", "");
        });
        PytestSubprocessApiAutomationRunnerAdapter adapter = sandboxAdapter(executor);

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle(),
                List.of(automationCase(caseId, "GET", "/v1/items/{id}", 200)),
                "https://api.example.test/service",
                10,
                List.of("sha256:secret-ref-digest"),
                List.of(new ApiAutomationRunnerPort.RunnerSecret(
                        "X-VA-WP6-Secret-1",
                        "sha256:secret-ref-digest",
                        "resolved-secret-value"
                ))
        ));

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.runnerMode()).isEqualTo("EXTERNAL");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.assertionSummaryJson())
                    .contains("\"runnerAdapter\":\"PYTEST_DOCKER_SANDBOX\"")
                    .contains("\"sandboxed\":true");
        });
        assertThat(executor.workingDirectoryDeleted()).isTrue();
    }

    @Test
    void failsClosedWhenDockerSandboxImageIsMissing() {
        RecordingExecutor executor = new RecordingExecutor((plan, workingDirectory, timeoutSeconds) -> {
            throw new AssertionError("sandbox executor should not run without an image");
        });
        PytestSubprocessApiAutomationRunnerAdapter adapter = new PytestSubprocessApiAutomationRunnerAdapter(
                new ObjectMapper(),
                executor,
                List.of("python3", "-m", "pytest"),
                PytestSubprocessApiAutomationRunnerAdapter.ExecutionBackend.DOCKER_SANDBOX,
                List.of("docker"),
                "",
                "bridge"
        );

        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(UUID.randomUUID()));

        assertThat(executor.calls).isZero();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(result.errorSummary()).isEqualTo("docker sandbox runner image is required");
    }

    private static PytestSubprocessApiAutomationRunnerAdapter adapter(RecordingExecutor executor) {
        return new PytestSubprocessApiAutomationRunnerAdapter(
                new ObjectMapper(),
                executor,
                List.of("python3", "-m", "pytest")
        );
    }

    private static PytestSubprocessApiAutomationRunnerAdapter sandboxAdapter(RecordingExecutor executor) {
        return new PytestSubprocessApiAutomationRunnerAdapter(
                new ObjectMapper(),
                executor,
                List.of("python3", "-m", "pytest"),
                PytestSubprocessApiAutomationRunnerAdapter.ExecutionBackend.DOCKER_SANDBOX,
                List.of("docker"),
                "veri-agent/wp6-pytest-runner:test",
                "bridge"
        );
    }

    private static ApiAutomationRunnerPort.RunnerRunRequest runRequest(UUID caseId) {
        return new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle(),
                List.of(automationCase(caseId, "GET", "/v1/items/{id}", 200)),
                "https://api.example.test/service",
                10,
                List.of(),
                List.of()
        );
    }

    private static void writeJunit(Path path, UUID caseId, String failureElement) throws IOException {
        String failure = failureElement == null ? "" : "<" + failureElement + " message=\"failed\"/>";
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="1" failures="%s" errors="0" skipped="0">
                  <testcase classname="tests.test_generated_api" name="test_generated_api_contract[%s]" time="0.025">%s</testcase>
                </testsuite>
                """.formatted(failureElement == null ? "0" : "1", caseId, failure);
        Files.writeString(path, xml, StandardCharsets.UTF_8);
    }

    private static ApiAutomationCase automationCase(UUID id, String method, String path, int expectedStatus) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationCase(
                id,
                UUID.randomUUID(),
                "project-alpha",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                method + " " + path,
                method,
                path,
                "SMOKE",
                expectedStatus,
                "{}",
                "{}",
                "GENERATED",
                "READY",
                now,
                now
        );
    }

    private static ApiAutomationScriptBundle scriptBundle() {
        Instant now = Instant.EPOCH;
        return new ApiAutomationScriptBundle(
                UUID.randomUUID(),
                "project-alpha",
                UUID.randomUUID(),
                "APPROVED",
                "bundle-digest",
                6,
                "{}",
                "{}",
                "PASSED",
                "{}",
                null,
                "tester",
                "tester",
                now,
                now,
                null,
                "tester",
                "tester",
                now,
                now
        );
    }

    private static final class RecordingExecutor implements PytestSubprocessApiAutomationRunnerAdapter.CommandExecutor {

        private final ExecutorCallback callback;
        private Path workingDirectory;
        private int calls;

        private RecordingExecutor(ExecutorCallback callback) {
            this.callback = callback;
        }

        @Override
        public PytestSubprocessApiAutomationRunnerAdapter.ProcessResult execute(
                PytestSubprocessApiAutomationRunnerAdapter.CommandExecutionPlan plan,
                Path workingDirectory,
                int timeoutSeconds
        ) throws IOException {
            this.calls++;
            this.workingDirectory = workingDirectory;
            return callback.execute(plan, workingDirectory, timeoutSeconds);
        }

        private boolean workingDirectoryDeleted() {
            return workingDirectory != null && Files.notExists(workingDirectory);
        }
    }

    @FunctionalInterface
    private interface ExecutorCallback {

        PytestSubprocessApiAutomationRunnerAdapter.ProcessResult execute(
                PytestSubprocessApiAutomationRunnerAdapter.CommandExecutionPlan plan,
                Path workingDirectory,
                int timeoutSeconds
        ) throws IOException;
    }
}
