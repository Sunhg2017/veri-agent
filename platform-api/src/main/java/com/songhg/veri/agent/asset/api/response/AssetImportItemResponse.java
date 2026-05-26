package com.songhg.veri.agent.asset.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record AssetImportItemResponse(
        @Schema(description = "导入文件中的行号。")
        int row,
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        String code,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "提示消息。")
        String message,
        @Schema(description = "校验错误列表。")
        List<String> errors
) {
}
