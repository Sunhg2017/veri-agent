package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Shared bounded JSON and key helpers for the WP6 API automation application package.
 */
final class ApiAutomationJsonSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    ApiAutomationJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> readSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("parseSummaryUnreadable", true, "aggregateOnly", true);
        }
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAPI 摘要序列化失败");
        }
    }

    static String assetKey(String httpMethod, String path) {
        return (httpMethod == null ? "" : httpMethod.trim().toUpperCase(Locale.ROOT)) + " " + nullToEmpty(path).trim();
    }

    static List<UUID> normalizedUuidList(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    static List<String> uuidStrings(List<UUID> values) {
        return values.stream().map(UUID::toString).toList();
    }

    static String safeSourceText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return SensitiveTextSanitizer.boundedText(SensitiveTextSanitizer.redactSensitiveText(value), maxLength);
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
