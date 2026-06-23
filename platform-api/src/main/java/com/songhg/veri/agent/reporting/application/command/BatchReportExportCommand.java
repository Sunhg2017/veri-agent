package com.songhg.veri.agent.reporting.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "WP10 批量报告导出请求")
public record BatchReportExportCommand(
        @Schema(description = "报告 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        @Size(max = 50)
        List<UUID> reportIds,
        @Schema(description = "导出格式，支持 JSON / MARKDOWN / HTML / PDF / WORD / EXCEL")
        String exportType
) {
}
