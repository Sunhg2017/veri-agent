package com.songhg.veri.agent.document.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CandidateBatchActionRequest(
        @Schema(description = "批量动作编码")
        @NotBlank
        String action,
        @Schema(description = "候选 ID 列表")
        List<UUID> candidateIds,
        @Schema(description = "候选列表或批量候选目标")
        List<CandidateBatchItemRequest> candidates,
        @Schema(description = "操作原因")
        String reason
) {
    public record CandidateBatchItemRequest(
        @Schema(description = "主键 ID")
        @NotNull UUID id,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        Long version
) {
    }
}
