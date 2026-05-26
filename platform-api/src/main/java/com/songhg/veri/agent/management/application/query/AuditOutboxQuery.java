package com.songhg.veri.agent.management.application.query;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.List;

public record AuditOutboxQuery(
        /** 模糊搜索关键字。 */
        String search,
        /** outbox 状态过滤条件。 */
        String status,
        /** 链路追踪 ID 过滤条件。 */
        String traceId
) {
    private static final List<String> STATUSES = List.of("PENDING", "PROCESSING", "DONE", "FAILED", "DEAD");

    public static AuditOutboxQuery of(String search, String status, String traceId) {
        String normalizedStatus = normalize(status).toUpperCase();
        if (!normalizedStatus.isBlank() && !STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "audit outbox status 不合法");
        }
        return new AuditOutboxQuery(
                normalize(search),
                normalizedStatus,
                normalize(traceId)
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
