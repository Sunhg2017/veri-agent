package com.songhg.veri.agent.document.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record IgnoreDocumentCandidateRequest(
        @Schema(description = "操作原因")
        @NotBlank
        String reason,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        Long version
) {
}
