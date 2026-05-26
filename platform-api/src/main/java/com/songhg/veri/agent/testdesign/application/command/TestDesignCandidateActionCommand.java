package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单个候选确认、驳回或忽略的接口入参。
 */
public record TestDesignCandidateActionCommand(
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        Long version,
        @Schema(description = "操作原因。")
        String reason,
        @Schema(description = "评审意见或补充说明。")
        String comment
) {
}
