package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record AuditLogQuery(
        String search,
        String actor,
        String action,
        String resourceType,
        String result,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
    public static AuditLogQuery of(
            String search,
            String actor,
            String action,
            String resourceType,
            String result,
            String startTime,
            String endTime
    ) {
        OffsetDateTime parsedStartTime = parseTime("startTime", startTime);
        OffsetDateTime parsedEndTime = parseTime("endTime", endTime);
        if (parsedStartTime != null && parsedEndTime != null && parsedStartTime.isAfter(parsedEndTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startTime 不能晚于 endTime");
        }
        return new AuditLogQuery(
                normalize(search),
                normalize(actor),
                normalize(action),
                normalize(resourceType),
                normalize(result),
                parsedStartTime,
                parsedEndTime
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static OffsetDateTime parseTime(String field, String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " 必须是 ISO-8601 时间");
        }
    }
}
