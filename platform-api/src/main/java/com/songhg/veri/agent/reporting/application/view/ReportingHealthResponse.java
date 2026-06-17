package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record ReportingHealthResponse(
        @Schema(description = "服务标识")
        String service,
        @Schema(description = "状态")
        String status,
        @Schema(description = "WP10 报告控制面是否启用")
        boolean enabled,
        @Schema(description = "是否允许报告生成")
        boolean generateEnabled,
        @Schema(description = "是否将报告生成请求异步入队")
        boolean asyncGenerationEnabled,
        @Schema(description = "是否启用报告生成后台 worker")
        boolean generationWorkerEnabled,
        @Schema(description = "报告生成后台 worker 轮询间隔")
        int generationWorkerIntervalMs,
        @Schema(description = "报告生成后台 worker 启动延迟")
        int generationWorkerInitialDelayMs,
        @Schema(description = "报告生成后台 worker ID")
        String generationWorkerId,
        @Schema(description = "单次 worker tick 认领上限")
        int generationWorkerBatchSize,
        @Schema(description = "GENERATING 超时秒数")
        int generationRunningTimeoutSeconds,
        @Schema(description = "单次 worker tick stale 恢复上限")
        int generationRecoveryBatchSize,
        @Schema(description = "是否允许 AI 诊断")
        boolean diagnosisEnabled,
        @Schema(description = "是否允许生成缺陷草稿")
        boolean defectDraftEnabled,
        @Schema(description = "是否允许导出脱敏报告摘要")
        boolean exportEnabled,
        @Schema(description = "单报告 evidence manifest 上限")
        int maxEvidenceItems,
        @Schema(description = "AI 诊断上下文字符上限")
        int maxDiagnosisContextChars,
        @Schema(description = "Markdown 摘要导出字符上限")
        int maxExportMarkdownChars,
        @Schema(description = "报告 schema version")
        String schemaVersion,
        @Schema(description = "导出字段集版本")
        String fieldSetVersion,
        @Schema(description = "当前 WP10 功能边界")
        Map<String, Object> policy
) {
}
