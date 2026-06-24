package com.songhg.veri.agent.testdata.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.port.TestAccountProvisioningAdapter;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConfiguredTestAccountProvisioningAdapter implements TestAccountProvisioningAdapter {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final TestDataProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ConfiguredTestAccountProvisioningAdapter(TestDataProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.effectiveAccountProvisioningAdapterTimeoutMs()))
                .build());
    }

    ConfiguredTestAccountProvisioningAdapter(
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
        String mode = properties.effectiveAccountProvisioningAdapterMode();
        return "LOCAL_SECRET_REF".equals(mode)
                || ("HTTP".equals(mode) && StringUtils.hasText(properties.effectiveAccountProvisioningAdapterUrl()));
    }

    @Override
    public String provider() {
        return ready() ? properties.effectiveAccountProvisioningAdapterMode() : "DISABLED";
    }

    @Override
    public ProvisionedAccount provision(ProvisioningRequest request) {
        if (!ready()) {
            throw new IllegalStateException("WP8 account provisioning adapter is not ready");
        }
        if ("LOCAL_SECRET_REF".equals(properties.effectiveAccountProvisioningAdapterMode())) {
            return ProvisionedAccount.fromRequest(request);
        }
        return provisionByHttp(request);
    }

    private ProvisionedAccount provisionByHttp(ProvisioningRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(httpRequest(request), HttpResponse.BodyHandlers.ofString());
            return provisionedAccount(request, response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WP8 account provisioning adapter interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(SensitiveTextSanitizer.sanitizedErrorSummary(
                    exception.getMessage(),
                    "WP8 account provisioning adapter failed",
                    MAX_ERROR_SUMMARY_LENGTH
            ), exception);
        }
    }

    private HttpRequest httpRequest(ProvisioningRequest request) throws JsonProcessingException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.effectiveAccountProvisioningAdapterUrl()))
                .timeout(Duration.ofMillis(properties.effectiveAccountProvisioningAdapterTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
        if (StringUtils.hasText(properties.effectiveAccountProvisioningAdapterToken())) {
            builder.header("Authorization", "Bearer " + properties.effectiveAccountProvisioningAdapterToken());
        }
        return builder.build();
    }

    private ProvisionedAccount provisionedAccount(ProvisioningRequest request, int statusCode, String body) {
        Map<String, Object> payload = readMap(body);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(safeErrorSummary(payload.get("errorSummary")));
        }
        return new ProvisionedAccount(
                stringValue(payload.get("accountKey"), request.accountKey(), 128),
                stringValue(payload.get("displayName"), request.displayName(), 128),
                stringList(payload.get("roleTags"), request.roleTags()),
                safeMap(payload.get("scopeSummary"), request.scopeSummary()),
                stringValue(payload.get("secretRef"), request.secretRef(), 256),
                stringValue(payload.get("healthStatus"), "HEALTHY", 32),
                stringValue(payload.get("healthSummary"), "provisioned by WP8 HTTP adapter", 512),
                safeMap(payload.get("summary"), Map.of("adapterAccepted", true))
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

    private Map<String, Object> safeMap(Object value, Map<String, Object> fallback) {
        if (!(value instanceof Map<?, ?> map)) {
            return fallback;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        map.forEach((rawKey, rawValue) -> {
            String key = safeKey(rawKey);
            if (StringUtils.hasText(key)) {
                safe.put(key, safeValue(rawValue));
            }
        });
        return safe;
    }

    private Object safeValue(Object value) {
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

    private List<String> stringList(Object value, List<String> fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return objectMapper.convertValue(value, STRING_LIST_TYPE);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String stringValue(Object value, String fallback, int maxLength) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return SensitiveTextSanitizer.boundedText(text, maxLength);
        }
        return fallback;
    }

    private String safeErrorSummary(Object value) {
        return SensitiveTextSanitizer.sanitizedErrorSummary(
                value == null ? null : String.valueOf(value),
                "WP8 account provisioning adapter failed",
                MAX_ERROR_SUMMARY_LENGTH
        );
    }
}
