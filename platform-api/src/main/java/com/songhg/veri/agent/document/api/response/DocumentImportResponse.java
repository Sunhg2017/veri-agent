package com.songhg.veri.agent.document.api.response;

import com.songhg.veri.agent.document.application.view.ParsedRequirementResponse;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record DocumentImportResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "文档输入源 ID")
        UUID sourceId,
        @Schema(description = "文档输入源编码")
        String sourceCode,
        @Schema(description = "文档或数据源类型")
        DocumentSourceType sourceType,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯")
        String sourceRef,
        @Schema(description = "来源系统页面或文档地址")
        String sourceUrl,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "业务状态")
        DocumentImportStatus status,
        @Schema(description = "解析出的需求总数")
        int totalParsed,
        @Schema(description = "创建到资产库的需求总数")
        int totalCreated,
        @Schema(description = "本次创建或复用的需求 ID 列表")
        List<UUID> createdRequirementIds,
        @Schema(description = "需求列表")
        List<ParsedRequirementResponse> requirements,
        @Schema(description = "待处理数量")
        long pendingCount,
        @Schema(description = "已确认数量")
        long confirmedCount,
        @Schema(description = "已发布数量")
        long publishedCount,
        @Schema(description = "处理失败数量")
        long failedCount,
        @Schema(description = "错误摘要")
        String errorMessage,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
