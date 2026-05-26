package com.songhg.veri.agent.document.api.request;

import com.songhg.veri.agent.document.domain.DocumentSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDocumentImportRequest(
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        @NotBlank
        String projectId,
        @Schema(description = "文档或数据源类型")
        @NotNull
        DocumentSourceType sourceType,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯")
        String sourceRef,
        @Schema(description = "来源系统页面或文档地址")
        String sourceUrl,
        @Schema(description = "文档正文内容，导入后解析为候选需求")
        @NotBlank
        String content,
        @Schema(description = "字段映射配置 ID")
        UUID mappingId,
        @Schema(description = "文档输入源 ID")
        UUID sourceId
) {
}
