package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.domain.ExecutionTrigger;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

/**
 * Normalizes trigger configuration and CRON schedule fields before they are stored as digest-only evidence.
 */
final class ExecutionTriggerConfigSupport {

    private static final Set<String> TRIGGER_TYPES = Set.of("WEBHOOK", "CRON");
    private static final Set<String> TRIGGER_STATUSES = Set.of("DISABLED", "ENABLED", "PAUSED");
    private static final Set<String> SAFE_CONFIG_KEYS = Set.of(
            "source",
            "eventType",
            "eventVersion",
            "cron",
            "timezone",
            "description",
            "filters",
            "labels"
    );
    private static final Set<String> FORBIDDEN_CONFIG_KEY_PARTS = Set.of(
            "secret", "token", "password", "authorization", "payload", "body", "header", "cookie"
    );
    private static final Pattern SECRET_REF_PATTERN =
            Pattern.compile("^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]{1,247}$");
    private static final int MAX_CONFIG_TEXT_LENGTH = 256;
    private static final int MAX_CONFIG_LIST_ITEMS = 20;
    private static final String DEFAULT_CRON_TIMEZONE = "UTC";

    Map<String, Object> sanitizedConfig(Map<String, Object> config, String triggerType) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        Map<String, Object> input = config == null ? Map.of() : config;
        input.forEach((key, value) -> {
            String normalizedKey = boundedNullableText(key, 64);
            if (!StringUtils.hasText(normalizedKey)) {
                return;
            }
            String lowerKey = normalizedKey.toLowerCase(Locale.ROOT);
            if (forbiddenConfigKey(lowerKey)) {
                throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "EXECUTION_TRIGGER_CONFIG_SECRET_FIELD");
            }
            if (SAFE_CONFIG_KEYS.contains(normalizedKey) || SAFE_CONFIG_KEYS.contains(lowerKey)) {
                sanitized.put(normalizedKey, sanitizedConfigValue(value));
            }
        });
        sanitized.put("type", triggerType);
        sanitized.put("rawPayloadStored", false);
        sanitized.put("secretStored", false);
        if ("CRON".equals(triggerType)) {
            normalizeCronConfig(sanitized);
        }
        return sanitized;
    }

    String normalizeTriggerType(String triggerType) {
        String normalized = boundedNullableText(triggerType, 32);
        if (normalized != null) {
            normalized = normalized.toUpperCase(Locale.ROOT);
        }
        if (!TRIGGER_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        return normalized;
    }

    String normalizeStatus(String status, String defaultStatus) {
        String normalized = boundedNullableText(status, 32);
        normalized = normalized == null ? defaultStatus : normalized.toUpperCase(Locale.ROOT);
        if (!TRIGGER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_STATUS_INVALID");
        }
        return normalized;
    }

    String normalizedSecretRef(String secretRef, String triggerType, String status) {
        String normalized = boundedNullableText(secretRef, 256);
        if (!StringUtils.hasText(normalized)) {
            ensureRequiredSecretRef(triggerType, status, null);
            return null;
        }
        if (!SECRET_REF_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "EXECUTION_TRIGGER_SECRET_REF_INVALID");
        }
        return normalized;
    }

    void ensureRequiredSecretRef(String triggerType, String status, String secretRef) {
        if ("WEBHOOK".equals(triggerType) && "ENABLED".equals(status) && !StringUtils.hasText(secretRef)) {
            throw new BusinessException(ErrorCode.SECRET_REQUIRED, "EXECUTION_TRIGGER_SECRET_REQUIRED");
        }
    }

    Instant initialNextFireAt(
            String triggerType,
            Map<String, Object> configSummary,
            Instant requestedNextFireAt,
            Instant now
    ) {
        if (!"CRON".equals(triggerType)) {
            return requestedNextFireAt;
        }
        return requestedNextFireAt == null ? nextCronFireAt(configSummary, now) : requestedNextFireAt;
    }

    Instant updatedNextFireAt(
            ExecutionTrigger existing,
            Map<String, Object> configSummary,
            Instant requestedNextFireAt,
            boolean configChanged,
            String status,
            Instant now
    ) {
        if (!"CRON".equals(existing.triggerType())) {
            return requestedNextFireAt == null ? existing.nextFireAt() : requestedNextFireAt;
        }
        if (requestedNextFireAt != null) {
            return requestedNextFireAt;
        }
        if (configChanged || ("ENABLED".equals(status) && existing.nextFireAt() == null)) {
            return nextCronFireAt(configSummary, now);
        }
        return existing.nextFireAt();
    }

    Instant nextCronFireAt(Map<String, Object> configSummary, Instant referenceTime) {
        String cron = sanitizedConfigText(configSummary.get("cron"));
        CronExpression expression = parseCron(cron);
        ZonedDateTime reference = ZonedDateTime.ofInstant(referenceTime, cronZone(configSummary));
        ZonedDateTime next = expression.next(reference);
        if (next == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_CRON_NEXT_FIRE_UNAVAILABLE");
        }
        return next.toInstant();
    }

    String cronSourceEventId(UUID triggerId, Instant fireAt) {
        return boundedNullableText("cron:" + triggerId + ":" + fireAt, 256);
    }

    private void normalizeCronConfig(Map<String, Object> sanitized) {
        String cron = sanitizedConfigText(sanitized.get("cron"));
        if (!StringUtils.hasText(cron)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_CRON_EXPRESSION_REQUIRED");
        }
        parseCron(cron);
        ZoneId zone = cronZone(sanitized);
        sanitized.put("cron", cron);
        sanitized.put("timezone", zone.getId());
        sanitized.put("cronPayloadStored", false);
    }

    private boolean forbiddenConfigKey(String key) {
        return FORBIDDEN_CONFIG_KEY_PARTS.stream().anyMatch(key::contains);
    }

    private Object sanitizedConfigValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return boundedNullableText(text, MAX_CONFIG_TEXT_LENGTH);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String normalizedKey = key == null ? null : boundedNullableText(String.valueOf(key), 64);
                if (StringUtils.hasText(normalizedKey) && !forbiddenConfigKey(normalizedKey.toLowerCase(Locale.ROOT))) {
                    sanitized.put(normalizedKey, sanitizedConfigValue(nestedValue));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> values = new ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count >= MAX_CONFIG_LIST_ITEMS) {
                    values.add("...");
                    break;
                }
                values.add(sanitizedConfigValue(item));
                count++;
            }
            return values;
        }
        return boundedNullableText(String.valueOf(value), MAX_CONFIG_TEXT_LENGTH);
    }

    private CronExpression parseCron(String cron) {
        try {
            return CronExpression.parse(cron);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_CRON_EXPRESSION_INVALID");
        }
    }

    private ZoneId cronZone(Map<String, Object> configSummary) {
        String timezone = sanitizedConfigText(configSummary.get("timezone"));
        String zoneId = StringUtils.hasText(timezone) ? timezone : DEFAULT_CRON_TIMEZONE;
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_CRON_TIMEZONE_INVALID");
        }
    }

    private String sanitizedConfigText(Object value) {
        return value == null ? null : boundedNullableText(String.valueOf(value), MAX_CONFIG_TEXT_LENGTH);
    }

    private String boundedNullableText(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }
}
