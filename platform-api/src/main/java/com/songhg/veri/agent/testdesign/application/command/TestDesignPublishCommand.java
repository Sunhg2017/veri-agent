package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * 发布或预发布 WP5 候选到 WP3 资产库的接口入参。
 */
public record TestDesignPublishCommand(
        @Schema(description = "候选 ID 列表。")
        List<UUID> candidateIds,
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据。")
        Boolean dryRun
) {
}
