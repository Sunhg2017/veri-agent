package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Queue alert subscription view with bounded non-secret routing metadata.
 */
public record TestDesignQueueAlertSubscriptionResponse(
        @Schema(description = "订阅 ID")
        UUID id,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "告警类型")
        String alertType,
        @Schema(description = "通知渠道")
        String channel,
        @Schema(description = "渠道目标引用，不包含密钥")
        String targetRef,
        @Schema(description = "告警阈值秒数")
        Integer thresholdSeconds,
        @Schema(description = "是否启用")
        boolean enabled,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
) {
}
