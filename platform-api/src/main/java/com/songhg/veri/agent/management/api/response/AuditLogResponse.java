package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuditLogResponse(
        @Schema(description = "发生时间。")
        String time,
        @Schema(description = "操作人。")
        String actor,
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "探测目标。")
        String target,
        @Schema(description = "处理结果。")
        String result
) {
}
