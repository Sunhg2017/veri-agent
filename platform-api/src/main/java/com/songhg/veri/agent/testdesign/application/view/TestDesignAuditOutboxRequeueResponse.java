package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Result of a bounded WP5 audit outbox requeue operation.
 */
public record TestDesignAuditOutboxRequeueResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "请求重放的状态")
        String requestedStatus,
        @Schema(description = "请求上限")
        int requestedLimit,
        @Schema(description = "实际重新排队数量")
        int requeuedCount,
        @Schema(description = "是否支持受限重新排队")
        boolean replaySupported,
        @Schema(description = "是否导出 outbox payload")
        boolean payloadExported,
        @Schema(description = "是否导出明细标识")
        boolean detailIdentifiersExported,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
