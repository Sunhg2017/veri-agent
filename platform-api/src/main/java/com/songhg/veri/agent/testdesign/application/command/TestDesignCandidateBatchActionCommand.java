package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * 批量评审候选的接口入参
 */
public record TestDesignCandidateBatchActionCommand(
        @Schema(description = "批量动作编码")
        @NotBlank String action,
        @Schema(description = "候选 ID 列表")
        List<UUID> candidateIds,
        @Schema(description = "候选列表或批量候选目标")
        List<Target> candidates,
        @Schema(description = "操作原因")
        String reason,
        @Schema(description = "评审意见或补充说明")
        String comment
) {
    /**
     * 批量评审中的单个候选目标
 */
public record Target(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        Long version
) {
    }
}
