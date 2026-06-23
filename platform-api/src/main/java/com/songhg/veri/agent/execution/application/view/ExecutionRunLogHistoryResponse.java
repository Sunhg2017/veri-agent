package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ExecutionRunLogHistoryResponse(
        @Schema(description = "日志列表，按时间倒序")
        List<ExecutionRunLogEntryResponse> items,
        @Schema(description = "当前页码，从 0 开始")
        int index,
        @Schema(description = "每页条数")
        int size,
        @Schema(description = "满足条件的总数")
        long total
) {
}
