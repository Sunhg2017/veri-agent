package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 批量发布冲突处理中单个候选的处理结果出参。
 */
public record TestDesignConflictBatchResolveItemResponse(
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "处理结果，SUCCEEDED 或 FAILED")
        String result,
        @Schema(description = "成功或业务失败时生成的发布记录；请求级校验失败时为空")
        TestDesignPublishRecordResponse record,
        @Schema(description = "错误编码")
        String errorCode,
        @Schema(description = "错误摘要")
        String errorMessage
) {
}
