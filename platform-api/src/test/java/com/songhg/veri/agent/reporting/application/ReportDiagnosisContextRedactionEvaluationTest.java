package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDiagnosisContextRedactionEvaluationTest {

    private static final Instant NOW = Instant.parse("2026-06-17T12:00:00Z");
    private static final List<String> FORBIDDEN_SAMPLES = List.of(
            "secret://",
            "Authorization",
            "Bearer",
            "lease token",
            "raw prompt",
            "raw response",
            "runner stdout",
            "runner stderr",
            "request body",
            "response body",
            "webhook payload",
            "source code package",
            "RAW_PROMPT_VALUE",
            "RUNNER_STDOUT_VALUE",
            "WEBHOOK_PAYLOAD_VALUE",
            "REQUEST_BODY_VALUE",
            "RESPONSE_BODY_VALUE"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportingJsonSupport jsonSupport = new ReportingJsonSupport(objectMapper);
    private final ReportDiagnosisContextBuilder builder = new ReportDiagnosisContextBuilder(properties(), jsonSupport);

    @Test
    void removesForbiddenKeysAndTextBeforeModelInvocationContext() {
        ReportDiagnosisContextBuilder.DiagnosisContext context = builder.build(
                report(),
                List.of(wp9UnsafeManifest(), wp8UnsafeManifest()),
                ruleDiagnosis()
        );

        assertThat(context.responseMetadata())
                .containsEntry("contextStored", false)
                .containsEntry("rawPromptStored", false)
                .containsEntry("rawResponseStored", false)
                .containsEntry("bounded", true)
                .containsEntry("sourceEvidenceManifestCount", 2);
        assertThat(context.boundedContext()).contains("WP10_FAILURE_DIAGNOSIS_V1");
        assertThat(context.boundedContext()).contains("[REDACTED_CONTEXT]");
        assertThat(context.boundedContext()).doesNotContain("\"rawPrompt\"");
        assertThat(context.boundedContext()).doesNotContain("\"runnerStdout\"");
        assertThat(context.boundedContext()).doesNotContain("\"webhookPayload\"");
        assertThat(context.boundedContext()).doesNotContain("\"requestBody\"");
        assertThat(context.boundedContext()).doesNotContain("\"responseBody\"");
        assertThat(context.boundedContext()).doesNotContain("\"secretToken\"");
        assertThat(context.boundedContext()).doesNotContain("\"passwordHint\"");
        assertThat(context.boundedContext()).contains("rawPromptIncluded");
        assertNoForbiddenSamples(context.boundedContext());
    }

    private void assertNoForbiddenSamples(String context) {
        for (String sample : FORBIDDEN_SAMPLES) {
            assertThat(context).as("forbidden sample " + sample).doesNotContain(sample);
        }
    }

    private ReportExecutionReport report() {
        return new ReportExecutionReport(
                UUID.randomUUID(),
                "project-alpha",
                UUID.randomUUID(),
                "m7b-redaction",
                "READY",
                "wp10-test-report-v1",
                "sha256:source-run",
                jsonSupport.json(Map.of(
                        "runStatus", "FAILED",
                        "safeNote", "request body REQUEST_BODY_VALUE and response body RESPONSE_BODY_VALUE were removed",
                        "Authorization", "Bearer reporttoken123456",
                        "rawPrompt", "RAW_PROMPT_VALUE",
                        "secretToken", "secret://wp10/report",
                        "passwordHint", "never-store"
                )),
                jsonSupport.json(Map.of("aggregateOnly", true)),
                "tester",
                NOW,
                null,
                null,
                "trc_wp10_m7b",
                null,
                NOW,
                NOW
        );
    }

    private ReportEvidenceManifest wp9UnsafeManifest() {
        return manifest(
                "WP9",
                "EXECUTION_NODE",
                "sha256:wp9-node",
                List.of("status", "rawPrompt", "runnerStdout", "safeNote", "webhookPayload"),
                Map.of(
                        "status", "FAILED",
                        "safeNote", "runner stdout RUNNER_STDOUT_VALUE and webhook payload WEBHOOK_PAYLOAD_VALUE",
                        "rawPrompt", "RAW_PROMPT_VALUE",
                        "runnerStdout", "RUNNER_STDOUT_VALUE",
                        "runnerStderr", "RUNNER_STDERR_VALUE",
                        "requestBody", "REQUEST_BODY_VALUE",
                        "responseBody", "RESPONSE_BODY_VALUE",
                        "webhookPayload", "WEBHOOK_PAYLOAD_VALUE",
                        "nested", Map.of(
                                "safeNestedNote", "raw response RESPONSE_BODY_VALUE",
                                "authorizationHeader", "Authorization: Bearer nestedtoken123456"
                        )
                ),
                Map.of(
                        "aggregateOnly", true,
                        "rawRunnerArtifactStored", false,
                        "requestResponseBodyStored", false,
                        "webhookPayloadStored", false
                )
        );
    }

    private ReportEvidenceManifest wp8UnsafeManifest() {
        return manifest(
                "WP8",
                "ACCOUNT_LEASE",
                "sha256:wp8-account",
                List.of("accountStatus", "passwordHint", "safeNote"),
                Map.of(
                        "accountStatus", "LOCKED",
                        "safeNote", "lease token should stay out of the diagnosis context",
                        "passwordHint", "never-store",
                        "secretRef", "secret://wp8/account"
                ),
                Map.of(
                        "sourceWp8ReportEvidenceSanitized", true,
                        "leaseTokenStored", false,
                        "secretRefPlaintextStored", false
                )
        );
    }

    private ReportEvidenceManifest manifest(
            String sourceWp,
            String sourceType,
            String sourceRefDigest,
            List<String> summaryKeys,
            Map<String, Object> evidenceSummary,
            Map<String, Object> redactionFlags
    ) {
        return new ReportEvidenceManifest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceWp,
                sourceType,
                sourceRefDigest,
                "wp10-redaction-evidence-v1",
                jsonSupport.json(summaryKeys),
                jsonSupport.json(redactionFlags),
                jsonSupport.json(evidenceSummary),
                NOW
        );
    }

    private ReportFailureDiagnosis ruleDiagnosis() {
        return new ReportFailureDiagnosis(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RULE_READY",
                jsonSupport.json(Map.of(
                        "primaryCategory", "ASSERTION_FAILED",
                        "safeDiagnosticNote", "raw prompt should be scrubbed before model invocation",
                        "providerRawResponse", "RAW_RESPONSE_VALUE"
                )),
                null,
                new BigDecimal("0.7400"),
                true,
                jsonSupport.json(Map.of("classificationOnly", true)),
                "ASSERTION_FAILED",
                NOW,
                NOW
        );
    }

    private ReportingProperties properties() {
        return new ReportingProperties(
                true,
                true,
                false,
                true,
                5000,
                30000,
                "wp10-test-worker",
                4,
                1800,
                50,
                true,
                true,
                true,
                false,
                null,
                false,
                null,
                5000,
                200,
                12000,
                30000,
                "wp10-test-report-v1",
                "wp10-report-export-fields-v1"
        );
    }
}
