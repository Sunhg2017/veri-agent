package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * WP5 发布或预发布接口出参
 */
public record TestDesignPublishResponse(
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据")
        boolean dryRun,
        @Schema(description = "本次处理总数")
        int total,
        @Schema(description = "创建成功数量")
        int created,
        @Schema(description = "跳过数量")
        int skipped,
        @Schema(description = "失败数量")
        int failed,
        @Schema(description = "本次创建或复用的测试用例 ID 列表")
        List<UUID> createdCaseIds,
        @Schema(description = "明细记录列表")
        List<TestDesignPublishRecordResponse> records
) {
}
