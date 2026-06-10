package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates or updates a project/prompt scoped WP5 queue alert subscription.
 */
public record UpsertTestDesignQueueAlertSubscriptionCommand(
        @Schema(description = "所属项目 ID")
        @NotBlank String projectId,
        @Schema(description = "Prompt 模板标识；为空表示项目级订阅")
        @Size(max = 128) String promptKey,
        @Schema(description = "告警类型")
        @NotBlank @Size(max = 64) String alertType,
        @Schema(description = "通知渠道：OPS_CONSOLE、EMAIL、WEBHOOK")
        @NotBlank @Size(max = 32) String channel,
        @Schema(description = "渠道目标引用；不得包含 webhook token、payload 或明细标识")
        @NotBlank @Size(max = 180) String targetRef,
        @Schema(description = "覆盖默认阈值的秒数；为空使用系统阈值")
        @Min(0) @Max(86400) Integer thresholdSeconds,
        @Schema(description = "是否启用")
        Boolean enabled
) {
}
