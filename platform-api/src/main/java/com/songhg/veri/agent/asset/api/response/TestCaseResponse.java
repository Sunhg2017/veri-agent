package com.songhg.veri.agent.asset.api.response;

import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record TestCaseResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "业务编码，通常在同一资源范围内唯一")
        String code,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "业务说明或补充描述")
        String description,
        @Schema(description = "关联需求 ID")
        UUID requirementId,
        @Schema(description = "关联 API 资产 ID")
        UUID apiId,
        @Schema(description = "数据来源类型或来源系统标识")
        String source,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯")
        String sourceRef,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "业务状态")
        String status,
        @Schema(description = "优先级")
        String priority,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达")
        String tags,
        @Schema(description = "测试步骤列表")
        List<TestCaseStepResponse> steps,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        int version,
        @Schema(description = "资产生命周期状态，例如 ACTIVE、ARCHIVED、DELETED")
        String lifecycleStatus,
        @Schema(description = "归档时间；未归档时为空")
        Instant archivedAt,
        @Schema(description = "删除时间；未删除时为空")
        Instant deletedAt,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
