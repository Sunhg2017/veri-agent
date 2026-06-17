package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFailureClassifierQualityEvaluationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Instant NOW = Instant.parse("2026-06-17T10:00:00Z");
    private static final List<String> FORBIDDEN_SAMPLES = List.of(
            "secret://",
            "Authorization",
            "Bearer",
            "lease token",
            "raw prompt",
            "raw response",
            "runner stdout",
            "runner stderr",
            "webhook payload"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportingJsonSupport jsonSupport = new ReportingJsonSupport(objectMapper);
    private final RuleFailureClassifier classifier = new RuleFailureClassifier(jsonSupport);

    @Test
    void evaluatesTypicalFailureCategoriesWithoutSensitiveOutput() {
        assertScenario("timeout", List.of(wp9Node("TIMEOUT", "API_TEST", "NODE_TIMEOUT", "WP6_API")),
                "TIMEOUT", new BigDecimal("0.7600"), "wp9:execution_node:sha256:timeout");
        assertScenario("dependency", List.of(wp9Node("BLOCKED", "API_TEST", "DEPENDENCY_BLOCKED", "WP6_API")),
                "DEPENDENCY_BLOCKED", new BigDecimal("0.7300"), "wp9:execution_node:sha256:dependency");
        assertScenario("runner", List.of(wp9Node("FAILED", "REMOTE_COMMAND", "RUNNER_DISABLED", "HOSTED_RUNNER")),
                "RUNNER_FAILURE", new BigDecimal("0.6400"), "wp9:execution_node:sha256:runner");
        assertScenario("account", List.of(wp8AccountLease("RELEASED", "LOCKED", "ACCOUNT_LOCKED")),
                "TEST_DATA_ACCOUNT", new BigDecimal("0.6800"), "wp8:account_lease:sha256:account");
        assertScenario("webhook", List.of(wp9Node("FAILED", "WEBHOOK_TRIGGER", "WEBHOOK_IDEMPOTENCY_CONFLICT", "WEBHOOK")),
                "UNKNOWN", new BigDecimal("0.4500"), "wp9:execution_node:sha256:webhook");
    }

    private void assertScenario(
            String label,
            List<ReportEvidenceManifest> evidenceManifests,
            String expectedCategory,
            BigDecimal expectedConfidence,
            String expectedEvidenceRef
    ) {
        ReportFailureDiagnosis diagnosis = classifier.classify(UUID.randomUUID(), evidenceManifests, NOW);
        Map<String, Object> classification = readMap(diagnosis.classificationJson());
        Map<String, Object> summary = readMap(diagnosis.diagnosisSummaryJson());
        List<Map<String, Object>> candidates = candidates(summary);

        assertThat(diagnosis.status()).as(label).isEqualTo("RULE_READY");
        assertThat(diagnosis.errorCode()).as(label).isEqualTo(expectedCategory);
        assertThat(diagnosis.confidence()).as(label).isEqualByComparingTo(expectedConfidence);
        assertThat(diagnosis.manualReviewRequired()).as(label).isTrue();
        assertThat(classification.get("primaryCategory")).as(label).isEqualTo(expectedCategory);
        assertThat(classification.get("ruleVersion")).as(label).isEqualTo(RuleFailureClassifier.RULE_VERSION);
        assertThat(summary).as(label)
                .containsEntry("aiDiagnosisReady", false)
                .containsEntry("modelInvoked", false)
                .containsEntry("classificationOnly", true);
        assertThat(candidates).as(label).hasSize(1);
        assertThat(candidates.get(0).get("category")).as(label).isEqualTo(expectedCategory);
        assertThat(candidates.get(0).get("evidenceRefs")).as(label).asList().containsExactly(expectedEvidenceRef);
        assertNoForbiddenSamples(label, diagnosis);
    }

    private void assertNoForbiddenSamples(String label, ReportFailureDiagnosis diagnosis) {
        String serialized = diagnosis.classificationJson() + diagnosis.diagnosisSummaryJson();
        for (String sample : FORBIDDEN_SAMPLES) {
            assertThat(serialized).as(label + " forbidden sample " + sample).doesNotContain(sample);
        }
    }

    private ReportEvidenceManifest wp9Node(String status, String nodeType, String errorCode, String runnerType) {
        String digestKey = switch (errorCode) {
            case "NODE_TIMEOUT" -> "timeout";
            case "DEPENDENCY_BLOCKED" -> "dependency";
            case "RUNNER_DISABLED" -> "runner";
            case "WEBHOOK_IDEMPOTENCY_CONFLICT" -> "webhook";
            default -> "unknown";
        };
        return manifest("WP9", "EXECUTION_NODE", "sha256:" + digestKey, Map.of(
                "status", status,
                "nodeType", nodeType,
                "errorCode", errorCode,
                "runnerType", runnerType,
                "sanitized", true,
                "rawPromptStored", false,
                "rawResponseStored", false,
                "payloadStored", false
        ));
    }

    private ReportEvidenceManifest wp8AccountLease(String status, String accountStatus, String errorCode) {
        return manifest("WP8", "ACCOUNT_LEASE", "sha256:account", Map.of(
                "status", status,
                "accountStatus", accountStatus,
                "errorCode", errorCode,
                "accountLeaseRefDigest", "sha256:lease",
                "secretRefPlaintextStored", false,
                "leaseTokenStored", false
        ));
    }

    private ReportEvidenceManifest manifest(
            String sourceWp,
            String sourceType,
            String sourceRefDigest,
            Map<String, Object> summary
    ) {
        return new ReportEvidenceManifest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceWp,
                sourceType,
                sourceRefDigest,
                "wp10-eval-evidence-v1",
                jsonSupport.json(List.of("status", "errorCode")),
                jsonSupport.json(Map.of("summaryValuesStored", false, "aggregateOnly", true)),
                jsonSupport.json(summary),
                NOW
        );
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new AssertionError("JSON should be readable in WP10 quality eval", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> candidates(Map<String, Object> summary) {
        return (List<Map<String, Object>>) summary.get("rootCauseCandidates");
    }
}
