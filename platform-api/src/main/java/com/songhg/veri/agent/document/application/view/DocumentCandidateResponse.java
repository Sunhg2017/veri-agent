package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record DocumentCandidateResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "文档导入任务 ID")
        UUID importId,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "业务说明或补充描述")
        String description,
        @Schema(description = "优先级")
        String priority,
        @Schema(description = "验收标准")
        String acceptanceCriteria,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达")
        String tags,
        @Schema(description = "候选生命周期状态")
        DocumentCandidateStatus status,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯")
        String sourceRef,
        @Schema(description = "来源文档片段定位信息")
        String sourceFragment,
        @Schema(description = "外部系统中的需求 ID")
        String externalRequirementId,
        @Schema(description = "解析或生成置信度，范围 0 到 1")
        double confidence,
        @Schema(description = "解析来源，例如规则解析或模型解析")
        String parseSource,
        @Schema(description = "模型调用记录 ID")
        UUID modelInvocationId,
        @Schema(description = "模型供应商名称")
        String modelProviderName,
        @Schema(description = "模型名称")
        String modelName,
        @Schema(description = "发布或匹配到的 WP3 需求资产 ID")
        UUID assetRequirementId,
        @Schema(description = "错误摘要")
        String errorMessage,
        @Schema(description = "忽略原因")
        String ignoredReason,
        @Schema(description = "确认人")
        String confirmedBy,
        @Schema(description = "确认时间")
        Instant confirmedAt,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        long version,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
