package com.songhg.veri.agent.modelaccess.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application-layer result for a model invocation.
 *
 * <p>External response DTOs are derived from this result in the API layer, keeping invocation
 * orchestration free of presentation concerns.</p>
 */
public record ModelInvocationResult(
        @Schema(description = "模型调用记录 ID")
        UUID invocationId,
        @Schema(description = "模型供应商 ID")
        UUID providerId,
        @Schema(description = "模型供应商名称")
        String providerName,
        @Schema(description = "模型名称")
        String modelName,
        @Schema(description = "是否使用 fallback 结果")
        boolean fallbackUsed,
        @Schema(description = "请求或导入内容正文")
        String content,
        @Schema(description = "输入 token 数")
        int inputTokens,
        @Schema(description = "输出 token 数")
        int outputTokens,
        @Schema(description = "总成本")
        BigDecimal totalCost
) {
}
