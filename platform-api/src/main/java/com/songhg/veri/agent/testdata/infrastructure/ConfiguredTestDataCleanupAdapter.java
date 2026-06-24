package com.songhg.veri.agent.testdata.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.port.TestDataCleanupAdapter;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConfiguredTestDataCleanupAdapter implements TestDataCleanupAdapter {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final TestDataProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ConfiguredTestDataCleanupAdapter(TestDataProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.effectiveCleanupAdapterTimeoutMs()))
                .build());
    }

    ConfiguredTestDataCleanupAdapter(
            TestDataProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean ready() {
        return "HTTP".equals(properties.effectiveCleanupAdapterMode())
                && StringUtils.hasText(properties.effectiveCleanupAdapterUrl());
    }

    @Override
    public String provider() {
        return ready() ? "HTTP" : properties.effectiveCleanupAdapterMode();
    }

    @Override
    public CleanupResult cleanup(CleanupRequest request) {
        if (!ready()) {
            return CleanupResult.failure("CLEANUP_ADAPTER_NOT_READY", "WP8 cleanup adapter is not ready");
        }
        try {
            HttpRequest httpRequest = httpRequest(request);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return cleanupResult(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CleanupResult.failure("CLEANUP_ADAPTER_INTERRUPTED", "WP8 cleanup adapter interrupted");
        } catch (IOException | RuntimeException exception) {
            return CleanupResult.failure(
                    "CLEANUP_ADAPTER_FAILED",
                    SensitiveTextSanitizer.sanitizedErrorSummary(
                            exception.getMessage(),
                            "WP8 cleanup adapter failed",
                            MAX_ERROR_SUMMARY_LENGTH
                    )
            );
        }
    }

    private HttpRequest httpRequest(CleanupRequest request) throws JsonProcessingException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.effectiveCleanupAdapterUrl()))
                .timeout(Duration.ofMillis(properties.effectiveCleanupAdapterTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
        if (StringUtils.hasText(properties.effectiveCleanupAdapterToken())) {
            builder.header("Authorization", "Bearer " + properties.effectiveCleanupAdapterToken());
        }
        return builder.build();
    }

    private CleanupResult cleanupResult(int statusCode, String body) {
        Map<String, Object> payload = readMap(body);
        if (statusCode < 200 || statusCode >= 300) {
            return CleanupResult.failure(
                    stringValue(payload.get("errorCode"), "CLEANUP_ADAPTER_HTTP_" + statusCode),
                    safeErrorSummary(payload.get("errorSummary"))
            );
        }
        boolean success = booleanValue(payload.get("success"), true);
        if (!success) {
            return CleanupResult.failure(
                    stringValue(payload.get("errorCode"), "CLEANUP_ADAPTER_REJECTED"),
                    safeErrorSummary(payload.get("errorSummary"))
            );
        }
        return CleanupResult.success(
                boundedNullable(payload.get("externalCleanupId"), 128),
                longValue(payload.get("affectedResourceCount")),
                safeSummaryMap(payload.get("summary"))
        );
    }

    private Map<String, Object> readMap(String body) {
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("unreadable", true);
        }
    }

    private Map<String, Object> safeSummaryMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        map.forEach((rawKey, rawValue) -> {
            String key = safeKey(rawKey);
            if (StringUtils.hasText(key)) {
                safe.put(key, safeSummaryValue(rawValue));
            }
        });
        return safe;
    }

    private Object safeSummaryValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return "[REDACTED_COMPLEX_VALUE]";
        }
        return SensitiveTextSanitizer.sanitizedEvidenceText(String.valueOf(value), 256);
    }

    private String safeKey(Object rawKey) {
        if (rawKey == null) {
            return null;
        }
        String key = String.valueOf(rawKey).trim();
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || normalized.contains("credential")
                || normalized.contains("authorization")) {
            return null;
        }
        return key.matches("^[A-Za-z0-9_.:-]{1,64}$") ? key : null;
    }

    private String safeErrorSummary(Object value) {
        return SensitiveTextSanitizer.sanitizedErrorSummary(
                value == null ? null : String.valueOf(value),
                "WP8 cleanup adapter failed",
                MAX_ERROR_SUMMARY_LENGTH
        );
    }

    private String stringValue(Object value, String fallback) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return SensitiveTextSanitizer.boundedText(text, 64);
        }
        return fallback;
    }

    private String boundedNullable(Object value, int maxLength) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        return SensitiveTextSanitizer.boundedText(text, maxLength);
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.longValue());
        }
        return 0;
    }
}
