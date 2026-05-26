package com.songhg.veri.agent.document.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConfirmDocumentCandidateRequest(
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        Long version
) {
}
