package com.songhg.veri.agent.apiautomation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiAutomationRunnerResultSanitizerTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiAutomationRunnerResultSanitizer sanitizer = sanitizerWithArtifactLimit(128);

    @Test
    void nullRunnerAttemptBecomesFailedNoopResult() {
        ApiAutomationRunnerPort.RunnerRunResult result = sanitizer.enforceRunnerArtifactLimit(null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.runnerMode()).isEqualTo("NOOP");
        assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(result.caseResults()).isEmpty();
    }

    @Test
    void oversizedCaseArtifactIsFoldedBeforePersistence() throws Exception {
        String oversized = "raw-runner-artifact-should-not-persist".repeat(10);
        ApiAutomationRunnerPort.RunnerRunResult attempt = new ApiAutomationRunnerPort.RunnerRunResult(
                "PASSED",
                "EXTERNAL",
                null,
                null,
                List.of(new ApiAutomationRunnerPort.RunnerCaseResult(UUID.randomUUID(), "PASSED", 7, oversized,
                        null, null))
        );

        ApiAutomationRunnerPort.RunnerRunResult result = sanitizer.enforceRunnerArtifactLimit(attempt);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RUNNER_ARTIFACT_TOO_LARGE");
        ApiAutomationRunnerPort.RunnerCaseResult caseResult = result.caseResults().getFirst();
        assertThat(caseResult.status()).isEqualTo("ERROR");
        assertThat(caseResult.errorCode()).isEqualTo("RUNNER_ARTIFACT_TOO_LARGE");
        Map<String, Object> summary = objectMapper.readValue(caseResult.assertionSummaryJson(), MAP_TYPE);
        assertThat(summary)
                .containsEntry("aggregateOnly", true)
                .containsEntry("rawRequestResponseStored", false)
                .containsEntry("secretValuesStored", false)
                .containsEntry("artifactStored", false)
                .containsEntry("artifactTooLarge", true)
                .containsEntry("artifactMaxBytes", 128);
        assertThat(caseResult.assertionSummaryJson()).doesNotContain("raw-runner-artifact-should-not-persist");
    }

    @Test
    void assertionSummaryRecursivelyRedactsSensitiveKeys() throws Exception {
        String assertionSummary = """
                {
                  "status": "FAILED",
                  "headers": {
                    "authorization": "Bearer secret-token",
                    "x-request-id": "req-1"
                  },
                  "events": [
                    {"api_key": "secret-api-key", "message": "contains password=abc"}
                  ]
                }
                """;

        String sanitizedJson = sanitizerWithArtifactLimit(4_096).safeAssertionSummary(assertionSummary);

        Map<String, Object> summary = objectMapper.readValue(sanitizedJson, MAP_TYPE);
        assertThat(summary)
                .containsEntry("aggregateOnly", true)
                .containsEntry("rawRequestResponseStored", false)
                .containsEntry("secretValuesStored", false);
        assertThat(sanitizedJson)
                .contains("[REDACTED]")
                .doesNotContain("Bearer secret-token", "secret-api-key", "password=abc");
    }

    @Test
    void runnerErrorSummaryRedactsBaseUrlAndSensitiveText() {
        String summary = sanitizer.safeRunnerErrorSummary(
                "failed against https://api.example.test/service with token=secret-token",
                "https://api.example.test/service"
        );

        assertThat(summary)
                .contains("[REDACTED_BASE_URL]")
                .doesNotContain("https://api.example.test/service", "secret-token");
    }

    private ApiAutomationRunnerResultSanitizer sanitizerWithArtifactLimit(int artifactLimit) {
        return new ApiAutomationRunnerResultSanitizer(
                new ApiAutomationProperties(65_536, 50, true, 120, 100, "api.example.test", artifactLimit,
                        "wp6-api-automation-v1", true),
                objectMapper
        );
    }
}
