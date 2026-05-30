package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * 创建 WP5 用例生成任务的接口入参
 */
public record CreateTestDesignTaskCommand(
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        @NotBlank String projectId,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "需求 ID 列表")
        @NotEmpty List<UUID> requirementIds,
        @Schema(description = "显式纳入上下文的 API 资产 ID 列表；用于补充未建立需求追踪关系但本次生成需要参考的接口")
        List<UUID> contextApiIds,
        @Schema(description = "显式纳入上下文的页面资产 ID 列表；用于补充未建立需求追踪关系但本次生成需要参考的页面")
        List<UUID> contextPageIds,
        @Schema(description = "显式纳入上下文的业务流资产 ID 列表；用于补充未建立需求追踪关系但本次生成需要参考的流程")
        List<UUID> contextFlowIds,
        @Schema(description = "可选环境键；用于解析环境级上下文策略覆盖，不参与资源归属")
        String environmentKey,
        @Schema(description = "覆盖类型列表")
        List<String> coverageTypes,
        @Schema(description = "每个需求生成的候选数量")
        Integer caseCountPerRequirement,
        @Schema(description = "幂等键，用于重复请求回放和并发去重")
        String idempotencyKey
) {
}
