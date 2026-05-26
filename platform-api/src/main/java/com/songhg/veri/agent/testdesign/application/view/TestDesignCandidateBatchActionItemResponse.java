package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 批量评审中单个候选的处理结果出参
 */
public record TestDesignCandidateBatchActionItemResponse(
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "处理结果")
        String result,
        @Schema(description = "候选详情")
        TestDesignCandidateResponse candidate,
        @Schema(description = "错误编码")
        String errorCode,
        @Schema(description = "错误摘要")
        String errorMessage
) {
}
