package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Count-only response for a manual publish compensation run.
 */
public record TestDesignPublishCompensationRunResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "触发来源")
        String trigger,
        @Schema(description = "请求上限")
        int requestedLimit,
        @Schema(description = "扫描候选数量")
        int scannedCandidates,
        @Schema(description = "补偿成功候选数量")
        int succeededCandidates,
        @Schema(description = "补偿失败候选数量")
        int failedCandidates,
        @Schema(description = "跳过候选数量")
        int skippedCandidates,
        @Schema(description = "补偿是否启用")
        boolean compensationEnabled,
        @Schema(description = "是否支持人工运行")
        boolean manualRunSupported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "是否导出资产用例标识")
        boolean assetCaseIdentifierExported,
        @Schema(description = "是否导出候选明细标识")
        boolean candidateIdentifierListExported,
        @Schema(description = "是否导出错误明细")
        boolean errorDetailExported,
        @Schema(description = "运行时间")
        Instant runAt
) {
}
