package com.songhg.veri.agent.document.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record DocumentCandidateBatchActionItemResponse(
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "处理结果")
        String result,
        @Schema(description = "候选详情")
        DocumentCandidateResponse candidate,
        @Schema(description = "错误编码")
        String errorCode,
        @Schema(description = "错误摘要")
        String errorMessage
) {
}
