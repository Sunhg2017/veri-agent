package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 单条 WP5 发布记录的接口出参
 */
public record TestDesignPublishRecordResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "候选当前状态，用于前端跨页处理冲突时展示候选上下文")
        String candidateStatus,
        @Schema(description = "候选当前版本号，用于前端跨页处理冲突时提交乐观锁版本")
        Long candidateVersion,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "关联需求 ID")
        UUID requirementId,
        @Schema(description = "发布或匹配到的 WP3 测试用例资产 ID")
        UUID assetCaseId,
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据")
        boolean dryRun,
        @Schema(description = "操作类型或动作编码")
        String action,
        @Schema(description = "处理结果")
        String result,
        @Schema(description = "错误摘要")
        String errorMessage,
        @Schema(description = "发布人")
        String publishedBy,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
