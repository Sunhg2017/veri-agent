package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Classifies WP10 failures from persisted aggregate-only evidence manifests.
 *
 * <p>The classifier intentionally consumes manifest summaries and digests instead of WP9/WP8 source objects. This keeps
 * the rule fallback aligned with the report detail/export redaction boundary and makes later AI context building reuse
 * the same safe input shape.</p>
 */
final class RuleFailureClassifier {

    static final String RULE_VERSION = "wp10-failure-classifier-v1";

    private static final int MAX_CANDIDATE_EVIDENCE_REFS = 8;
    private static final String NO_FAILURE = "NO_FAILURE";
    private static final String TIMEOUT = "TIMEOUT";
    private static final String DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
    private static final String ASSERTION_FAILED = "ASSERTION_FAILED";
    private static final String TEST_DATA_ACCOUNT = "TEST_DATA_ACCOUNT";
    private static final String RUNNER_FAILURE = "RUNNER_FAILURE";
    private static final String UNKNOWN = "UNKNOWN";

    private final ReportingJsonSupport jsonSupport;

    RuleFailureClassifier(ReportingJsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    /**
     * Builds the persisted RULE_READY diagnosis for a report snapshot without invoking WP2 or reading raw evidence.
     */
    ReportFailureDiagnosis classify(UUID reportId, List<ReportEvidenceManifest> evidenceManifests, Instant now) {
        List<EvidenceSignal> signals = evidenceManifests.stream().map(this::signal).toList();
        ClassificationFacts facts = facts(signals);
        String primaryCategory = primaryCategory(facts);
        String secondaryCategory = secondaryCategory(primaryCategory, facts);
        List<Map<String, Object>> candidates = rootCauseCandidates(primaryCategory, secondaryCategory, facts);
        BigDecimal confidence = confidence(primaryCategory);
        boolean manualReviewRequired = !NO_FAILURE.equals(primaryCategory);

        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("primaryCategory", primaryCategory);
        if (secondaryCategory != null) {
            classification.put("secondaryCategory", secondaryCategory);
        }
        classification.put("ruleVersion", RULE_VERSION);
        classification.put("classificationSource", "RULE");
        classification.put("failedNodeCount", facts.failedNodes().size());
        classification.put("blockedNodeCount", facts.blockedNodes().size());
        classification.put("timeoutNodeCount", facts.timeoutNodes().size());
        classification.put("accountIssueCount", facts.accountIssues().size());
        classification.put("sourceEvidenceManifestCount", signals.size());

        Map<String, Object> diagnosisSummary = new LinkedHashMap<>();
        diagnosisSummary.put("rootCauseCandidates", candidates);
        diagnosisSummary.put("candidateCount", candidates.size());
        diagnosisSummary.put("aiDiagnosisReady", false);
        diagnosisSummary.put("modelInvoked", false);
        diagnosisSummary.put("classificationOnly", true);
        diagnosisSummary.put("redactionPolicy", Map.of(
                "aggregateOnly", true,
                "evidenceValuesStored", false,
                "rawPromptStored", false,
                "rawResponseStored", false,
                "credentialPlaintextStored", false,
                "modelProviderPayloadStored", false
        ));

        return new ReportFailureDiagnosis(
                UUID.randomUUID(),
                reportId,
                "RULE_READY",
                jsonSupport.json(classification),
                null,
                confidence,
                manualReviewRequired,
                jsonSupport.json(diagnosisSummary),
                errorCode(primaryCategory),
                now,
                now
        );
    }

    private EvidenceSignal signal(ReportEvidenceManifest manifest) {
        return new EvidenceSignal(manifest, jsonSupport.readMap(manifest.evidenceSummaryJson()));
    }

    private ClassificationFacts facts(List<EvidenceSignal> signals) {
        List<EvidenceSignal> failedNodes = new ArrayList<>();
        List<EvidenceSignal> timeoutNodes = new ArrayList<>();
        List<EvidenceSignal> blockedNodes = new ArrayList<>();
        List<EvidenceSignal> assertionNodes = new ArrayList<>();
        List<EvidenceSignal> runnerNodes = new ArrayList<>();
        List<EvidenceSignal> accountIssues = new ArrayList<>();

        for (EvidenceSignal signal : signals) {
            if (signal.wp9Node() && failureNode(signal)) {
                failedNodes.add(signal);
                if (timeoutNode(signal)) {
                    timeoutNodes.add(signal);
                }
                if (blockedNode(signal)) {
                    blockedNodes.add(signal);
                }
                if (assertionNode(signal)) {
                    assertionNodes.add(signal);
                }
                if (runnerNode(signal)) {
                    runnerNodes.add(signal);
                }
            }
            if (accountIssue(signal)) {
                accountIssues.add(signal);
            }
        }
        return new ClassificationFacts(failedNodes, timeoutNodes, blockedNodes, assertionNodes, runnerNodes,
                accountIssues);
    }

    private String primaryCategory(ClassificationFacts facts) {
        if (facts.failedNodes().isEmpty() && facts.accountIssues().isEmpty()) {
            return NO_FAILURE;
        }
        if (!facts.timeoutNodes().isEmpty()) {
            return TIMEOUT;
        }
        if (!facts.blockedNodes().isEmpty()) {
            return DEPENDENCY_BLOCKED;
        }
        if (!facts.assertionNodes().isEmpty()) {
            return ASSERTION_FAILED;
        }
        if (!facts.accountIssues().isEmpty()) {
            return TEST_DATA_ACCOUNT;
        }
        if (!facts.runnerNodes().isEmpty()) {
            return RUNNER_FAILURE;
        }
        return UNKNOWN;
    }

    private String secondaryCategory(String primaryCategory, ClassificationFacts facts) {
        for (String category : List.of(TIMEOUT, DEPENDENCY_BLOCKED, ASSERTION_FAILED, TEST_DATA_ACCOUNT,
                RUNNER_FAILURE)) {
            if (!category.equals(primaryCategory) && hasCategory(category, facts)) {
                return category;
            }
        }
        return null;
    }

    private boolean hasCategory(String category, ClassificationFacts facts) {
        return switch (category) {
            case TIMEOUT -> !facts.timeoutNodes().isEmpty();
            case DEPENDENCY_BLOCKED -> !facts.blockedNodes().isEmpty();
            case ASSERTION_FAILED -> !facts.assertionNodes().isEmpty();
            case TEST_DATA_ACCOUNT -> !facts.accountIssues().isEmpty();
            case RUNNER_FAILURE -> !facts.runnerNodes().isEmpty();
            default -> false;
        };
    }

    private List<Map<String, Object>> rootCauseCandidates(
            String primaryCategory,
            String secondaryCategory,
            ClassificationFacts facts
    ) {
        if (NO_FAILURE.equals(primaryCategory)) {
            return List.of();
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate(primaryCategory, facts));
        if (secondaryCategory != null) {
            candidates.add(candidate(secondaryCategory, facts));
        }
        return candidates;
    }

    private Map<String, Object> candidate(String category, ClassificationFacts facts) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("category", category);
        candidate.put("summary", summaryText(category));
        candidate.put("confidence", confidence(category));
        candidate.put("evidenceRefs", evidenceRefs(evidenceSignals(category, facts)));
        candidate.put("nextActions", nextActions(category));
        candidate.put("manualReviewRequired", true);
        candidate.put("source", "RULE");
        return candidate;
    }

    private List<EvidenceSignal> evidenceSignals(String category, ClassificationFacts facts) {
        List<EvidenceSignal> signals = switch (category) {
            case TIMEOUT -> facts.timeoutNodes();
            case DEPENDENCY_BLOCKED -> facts.blockedNodes();
            case ASSERTION_FAILED -> facts.assertionNodes();
            case TEST_DATA_ACCOUNT -> facts.accountIssues();
            case RUNNER_FAILURE -> facts.runnerNodes();
            default -> facts.failedNodes();
        };
        return signals.isEmpty() ? facts.failedNodes() : signals;
    }

    private List<String> evidenceRefs(List<EvidenceSignal> signals) {
        return signals.stream()
                .limit(MAX_CANDIDATE_EVIDENCE_REFS)
                .map(signal -> signal.manifest().sourceWp().toLowerCase(Locale.ROOT)
                        + ":" + signal.manifest().sourceType().toLowerCase(Locale.ROOT)
                        + ":" + signal.manifest().sourceRefDigest())
                .toList();
    }

    private String summaryText(String category) {
        return switch (category) {
            case TIMEOUT -> "Rule matched timeout status or timeout error code in sanitized execution evidence.";
            case DEPENDENCY_BLOCKED -> "Rule matched blocked downstream node evidence.";
            case ASSERTION_FAILED -> "Rule matched assertion failure evidence from sanitized execution nodes.";
            case TEST_DATA_ACCOUNT -> "Rule matched account lease or cleanup evidence requiring data/account review.";
            case RUNNER_FAILURE -> "Rule matched runner failure evidence from sanitized execution nodes.";
            default -> "Rule matched a failure without a more specific category.";
        };
    }

    private List<String> nextActions(String category) {
        return switch (category) {
            case TIMEOUT -> List.of("Check timeout budget, upstream latency, and runner capacity.");
            case DEPENDENCY_BLOCKED -> List.of("Inspect the first failed predecessor before retrying blocked nodes.");
            case ASSERTION_FAILED -> List.of("Compare the assertion expectation with the current environment contract.");
            case TEST_DATA_ACCOUNT -> List.of("Review account lease status, account health, and cleanup task outcome.");
            case RUNNER_FAILURE -> List.of("Check runner mode, host health, and dispatch failure summaries.");
            default -> List.of("Review sanitized failed node evidence and rerun with targeted diagnostics if needed.");
        };
    }

    private BigDecimal confidence(String category) {
        return switch (category) {
            case NO_FAILURE -> new BigDecimal("0.9900");
            case TIMEOUT -> new BigDecimal("0.7600");
            case DEPENDENCY_BLOCKED -> new BigDecimal("0.7300");
            case ASSERTION_FAILED -> new BigDecimal("0.7400");
            case TEST_DATA_ACCOUNT -> new BigDecimal("0.6800");
            case RUNNER_FAILURE -> new BigDecimal("0.6400");
            default -> new BigDecimal("0.4500");
        };
    }

    private String errorCode(String category) {
        return NO_FAILURE.equals(category) ? null : category;
    }

    private boolean failureNode(EvidenceSignal signal) {
        String status = upper(signal.text("status", 32));
        return "FAILED".equals(status)
                || "BLOCKED".equals(status)
                || "TIMEOUT".equals(status)
                || StringUtils.hasText(signal.text("errorCode", 64));
    }

    private boolean timeoutNode(EvidenceSignal signal) {
        String status = upper(signal.text("status", 32));
        return "TIMEOUT".equals(status) || contains(signal.text("errorCode", 64), TIMEOUT);
    }

    private boolean blockedNode(EvidenceSignal signal) {
        String status = upper(signal.text("status", 32));
        return "BLOCKED".equals(status) || contains(signal.text("errorCode", 64), "BLOCKED");
    }

    private boolean assertionNode(EvidenceSignal signal) {
        return contains(signal.text("errorCode", 64), "ASSERT")
                || ("FAILED".equals(upper(signal.text("status", 32)))
                && contains(signal.text("nodeType", 64), "API_TEST"));
    }

    private boolean runnerNode(EvidenceSignal signal) {
        return contains(signal.text("errorCode", 64), "RUNNER")
                || contains(signal.text("runnerType", 64), "RUNNER");
    }

    private boolean accountIssue(EvidenceSignal signal) {
        if (!"WP8".equals(signal.manifest().sourceWp())) {
            return false;
        }
        String sourceType = signal.manifest().sourceType();
        String status = upper(signal.text("status", 32));
        String accountStatus = upper(signal.text("accountStatus", 32));
        String errorCode = upper(signal.text("errorCode", 64));
        if ("ACCOUNT_LEASE".equals(sourceType)) {
            return contains(status, "FAILED")
                    || contains(status, "EXPIRED")
                    || "LOCKED".equals(accountStatus)
                    || contains(accountStatus, "UNHEALTH")
                    || contains(errorCode, "ACCOUNT")
                    || contains(errorCode, "LEASE");
        }
        if ("CLEANUP_TASK".equals(sourceType)) {
            return contains(status, "FAILED")
                    || contains(status, "ERROR")
                    || StringUtils.hasText(errorCode);
        }
        return false;
    }

    private boolean contains(String value, String expected) {
        return upper(value).contains(expected);
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private record EvidenceSignal(ReportEvidenceManifest manifest, Map<String, Object> summary) {

        private boolean wp9Node() {
            return "WP9".equals(manifest.sourceWp()) && "EXECUTION_NODE".equals(manifest.sourceType());
        }

        private String text(String key, int maxLength) {
            Object value = summary.get(key);
            if (value == null) {
                return null;
            }
            return SensitiveTextSanitizer.sanitizedEvidenceText(String.valueOf(value), maxLength);
        }
    }

    private record ClassificationFacts(
            List<EvidenceSignal> failedNodes,
            List<EvidenceSignal> timeoutNodes,
            List<EvidenceSignal> blockedNodes,
            List<EvidenceSignal> assertionNodes,
            List<EvidenceSignal> runnerNodes,
            List<EvidenceSignal> accountIssues
    ) {
    }
}
