package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @Size(max = 64)
        String name
) {
}
