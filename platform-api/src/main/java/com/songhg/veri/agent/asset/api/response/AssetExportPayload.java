package com.songhg.veri.agent.asset.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AssetExportPayload(
        @Schema(description = "文件名。")
        String fileName,
        @Schema(description = "内容类型。")
        String contentType,
        @Schema(description = "请求或导入内容正文。")
        byte[] content
) {
}
