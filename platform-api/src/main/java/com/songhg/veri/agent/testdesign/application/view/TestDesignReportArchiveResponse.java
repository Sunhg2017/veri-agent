package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized report archive metadata view.
 */
public record TestDesignReportArchiveResponse(
        @Schema(description = "归档记录 ID")
        UUID id,
        @Schema(description = "报告 manifest ID")
        UUID manifestId,
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "项目 ID")
        String projectId,
        @Schema(description = "托管存储后端")
        String storageBackend,
        @Schema(description = "报告内容 SHA-256")
        String contentDigest,
        @Schema(description = "报告字节数")
        long contentSizeBytes,
        @Schema(description = "报告数据行数")
        long reportRowCount,
        @Schema(description = "行级完整性索引行数")
        long lineIntegrityCount,
        @Schema(description = "归档状态")
        String status,
        @Schema(description = "归档审批状态")
        String archiveApprovalStatus,
        @Schema(description = "外发审批状态")
        String externalApprovalStatus,
        @Schema(description = "保留截止时间")
        Instant retentionUntil,
        @Schema(description = "是否已经存储归档内容")
        boolean archiveContentStored,
        @Schema(description = "行级完整性索引是否就绪")
        boolean lineIntegrityIndexReady,
        @Schema(description = "是否导出归档内容")
        boolean archiveContentExported,
        @Schema(description = "是否导出内部存储 key")
        boolean storageKeyExported,
        @Schema(description = "是否为聚合归档")
        boolean aggregateOnly,
        @Schema(description = "创建人")
        String createdBy,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
) {
}
