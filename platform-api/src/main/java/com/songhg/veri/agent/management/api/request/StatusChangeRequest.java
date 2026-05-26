package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StatusChangeRequest(
        @Schema(description = "业务状态。")
        @NotBlank
        @Pattern(regexp = "^(PREPARING|ACTIVE|ARCHIVED|DISABLED|ENABLED)$")
        String status
) {
}
