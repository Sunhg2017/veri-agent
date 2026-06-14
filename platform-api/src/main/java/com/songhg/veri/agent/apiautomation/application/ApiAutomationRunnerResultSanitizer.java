package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Reduces runner-owned output before it reaches persistence, audit export, or API responses.
 */
final class ApiAutomationRunnerResultSanitizer {

    private static final int ERROR_SUMMARY_MAX_CHARS = 512;
    private static final String ARTIFACT_TOO_LARGE = "RUNNER_ARTIFACT_TOO_LARGE";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ApiAutomationProperties properties;
    private final ObjectMapper objectMapper;

    ApiAutomationRunnerResultSanitizer(ApiAutomationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    ApiAutomationRunnerPort.RunnerRunResult enforceRunnerArtifactLimit(
            ApiAutomationRunnerPort.RunnerRunResult attempt
    ) {
        if (attempt == null) {
            return new ApiAutomationRunnerPort.RunnerRunResult(
                    "FAILED",
                    "NOOP",
                    "RUNNER_FAILED",
                    "runner returned no result",
                    List.of()
            );
        }
        if (attempt.caseResults() == null || attempt.caseResults().isEmpty()) {
            return attempt;
        }
        List<ApiAutomationRunnerPort.RunnerCaseResult> admittedResults = attempt.caseResults().stream()
                .filter(Objects::nonNull)
                .map(this::enforceRunnerCaseArtifactLimit)
                .toList();
        boolean artifactLimited = admittedResults.stream()
                .anyMatch(result -> ARTIFACT_TOO_LARGE.equals(result.errorCode()));
        if (!artifactLimited) {
            return attempt;
        }
        /*
         * Artifact limits are a runner admission gate. Even if a future subprocess reports success, oversized
         * stdout/stderr-like summaries are treated as failed execution evidence and reduced before persistence.
         */
        return new ApiAutomationRunnerPort.RunnerRunResult(
                "FAILED",
                attempt.runnerMode(),
                ARTIFACT_TOO_LARGE,
                "runner artifact exceeded configured size limit",
                admittedResults
        );
    }

    String safeAssertionSummary(String value) {
        if (artifactTooLarge(value)) {
            return writeJson(artifactTooLargeSummary(value));
        }
        Map<String, Object> summary = readSummary(value);
        if (summary.isEmpty()) {
            return writeJson(Map.of("aggregateOnly", true, "rawRequestResponseStored", false));
        }
        /*
         * Runner adapters are an external trust boundary. Their summaries are useful for diagnosis, but must be
         * reduced to aggregate, recursively redacted evidence before persistence or export.
         */
        Map<String, Object> sanitized = new LinkedHashMap<>();
        summary.forEach((key, summaryValue) -> sanitized.put(key, sanitizeRunnerSummaryValue(key, summaryValue)));
        sanitized.put("aggregateOnly", true);
        sanitized.put("rawRequestResponseStored", false);
        sanitized.put("secretValuesStored", false);
        return writeJson(sanitized);
    }

    String safeRunnerErrorSummary(String value) {
        return safeRunnerErrorSummary(value, null);
    }

    String safeRunnerErrorSummary(String value, String normalizedBaseUrl) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value;
        if (StringUtils.hasText(normalizedBaseUrl)) {
            sanitized = sanitized.replace(normalizedBaseUrl, "[REDACTED_BASE_URL]");
        }
        return safeSourceText(sanitized, ERROR_SUMMARY_MAX_CHARS);
    }

    private ApiAutomationRunnerPort.RunnerCaseResult enforceRunnerCaseArtifactLimit(
            ApiAutomationRunnerPort.RunnerCaseResult result
    ) {
        if (result == null || !artifactTooLarge(result.assertionSummaryJson())) {
            return result;
        }
        return new ApiAutomationRunnerPort.RunnerCaseResult(
                result.caseId(),
                "ERROR",
                Math.max(0, result.durationMs()),
                writeJson(artifactTooLargeSummary(result.assertionSummaryJson())),
                ARTIFACT_TOO_LARGE,
                "runner artifact exceeded configured size limit"
        );
    }

    /**
     * Runner adapters may eventually wrap subprocess output. Oversized assertion artifacts are folded before JSON
     * parsing so malformed or huge stdout/stderr-like payloads cannot be persisted, exported or recursively expanded.
     */
    private boolean artifactTooLarge(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.getBytes(StandardCharsets.UTF_8).length > properties.effectiveRunnerArtifactMaxBytes();
    }

    private Map<String, Object> artifactTooLargeSummary(String value) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aggregateOnly", true);
        summary.put("rawRequestResponseStored", false);
        summary.put("secretValuesStored", false);
        summary.put("artifactStored", false);
        summary.put("artifactTooLarge", true);
        summary.put("errorCode", ARTIFACT_TOO_LARGE);
        summary.put("artifactBytes", value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length);
        summary.put("artifactMaxBytes", properties.effectiveRunnerArtifactMaxBytes());
        return summary;
    }

    private Object sanitizeRunnerSummaryValue(String key, Object value) {
        if (sensitiveRunnerSummaryKey(key)) {
            return "[REDACTED]";
        }
        if (value instanceof String text) {
            return safeSourceText(text, ERROR_SUMMARY_MAX_CHARS);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> sanitized.put(
                    nestedKey == null ? "" : nestedKey.toString(),
                    sanitizeRunnerSummaryValue(nestedKey == null ? "" : nestedKey.toString(), nestedValue)
            ));
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> sanitizeRunnerSummaryValue("", item)).toList();
        }
        return value;
    }

    private boolean sensitiveRunnerSummaryKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("api-key");
    }

    private Map<String, Object> readSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("parseSummaryUnreadable", true, "aggregateOnly", true);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Runner 摘要序列化失败");
        }
    }

    private String safeSourceText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return SensitiveTextSanitizer.boundedText(SensitiveTextSanitizer.redactSensitiveText(value), maxLength);
    }
}
