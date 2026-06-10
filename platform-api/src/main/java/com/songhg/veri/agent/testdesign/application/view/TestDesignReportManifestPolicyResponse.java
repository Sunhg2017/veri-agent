package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务报告 manifest 治理策略聚合快照。
 */
public record TestDesignReportManifestPolicyResponse(
        @Schema(description = "报告清单策略版本")
        String policyVersion,
        @Schema(description = "任务报告 schema 版本")
        String schemaVersion,
        @Schema(description = "任务报告字段集版本")
        String fieldSetVersion,
        @Schema(description = "报告清单模式")
        String manifestMode,
        @Schema(description = "是否跟踪报告行数")
        boolean rowCountTracked,
        @Schema(description = "是否跟踪报告完成状态")
        boolean completionStatusTracked,
        @Schema(description = "是否具备归档核验用的聚合清单")
        boolean archiveReconciliationReady,
        @Schema(description = "是否已存储行级完整性索引")
        boolean rowIntegrityStored,
        @Schema(description = "行级完整性索引是否可查询")
        boolean rowIntegrityIndexReady,
        @Schema(description = "是否导出明细行")
        boolean detailRowsExported,
        @Schema(description = "是否导出行级完整性值")
        boolean rowIntegrityValueExported,
        @Schema(description = "是否导出行内容摘要")
        boolean rowContentSummaryExported,
        @Schema(description = "是否导出候选 ID 清单")
        boolean candidateIdentifierListExported,
        @Schema(description = "是否导出 trace ID 清单")
        boolean traceIdentifierListExported,
        @Schema(description = "是否导出审计 ID 清单")
        boolean auditIdentifierListExported,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
