package com.songhg.veri.agent.document.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record DocumentPublishRequest(
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据")
        Boolean dryRun,
        @Schema(description = "候选 ID 列表")
        List<UUID> candidateIds
) {
}
