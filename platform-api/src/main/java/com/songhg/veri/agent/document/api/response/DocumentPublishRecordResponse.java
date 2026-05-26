package com.songhg.veri.agent.document.api.response;

import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record DocumentPublishRecordResponse(
        @Schema(description = "候选 ID。")
        UUID candidateId,
        @Schema(description = "标题，用于页面展示和关键字检索。")
        String title,
        @Schema(description = "候选状态。")
        DocumentCandidateStatus candidateStatus,
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "处理结果。")
        String result,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        String projectId,
        @Schema(description = "外部系统中的需求 ID。")
        String externalRequirementId,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯。")
        String sourceRef,
        @Schema(description = "来源文档片段定位信息。")
        String sourceFragment,
        @Schema(description = "发布或匹配到的 WP3 需求资产 ID。")
        UUID assetRequirementId,
        @Schema(description = "检测到的既有 WP3 需求资产 ID。")
        UUID existingRequirementId,
        @Schema(description = "与既有需求的差异摘要。")
        String diffSummary,
        @Schema(description = "错误摘要。")
        String errorMessage,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        long version
) {
}
