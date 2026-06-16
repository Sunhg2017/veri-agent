package com.songhg.veri.agent.reporting.application.query;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

public record ReportQuery(
        String projectId,
        UUID executionRunId,
        String status,
        int index,
        int size
) {
    private static final Set<String> REPORT_STATUSES = Set.of(
            "QUEUED", "GENERATING", "READY", "FAILED", "ARCHIVED"
    );

    public ReportQuery {
        index = Math.max(0, index);
        size = Math.max(1, Math.min(100, size));
    }

    public static ReportQuery of(String projectId, UUID executionRunId, String status, int index, int size) {
        String normalizedStatus = normalizeStatus(status);
        return new ReportQuery(
                SensitiveTextSanitizer.boundedNullableText(projectId, 64),
                executionRunId,
                normalizedStatus,
                index,
                size
        );
    }

    public int offset() {
        return index * size;
    }

    public int limit() {
        return size;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!REPORT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_STATUS_INVALID");
        }
        return normalized;
    }
}
