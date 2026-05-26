package com.songhg.veri.agent.modelaccess.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Application result for bounded daily model cost reporting.
 */
public record CostReportResult(
        @Schema(description = "查询开始日期。")
        LocalDate startDate,
        @Schema(description = "查询结束日期。")
        LocalDate endDate,
        @Schema(description = "报表行数据。")
        List<CostReportRowResult> rows
) {

    public record CostReportRowResult(
        @Schema(description = "统计日期。")
        LocalDate date,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        String projectId,
        @Schema(description = "所属应用 ID。")
        String applicationId,
        @Schema(description = "本次处理总数。")
        long total,
        @Schema(description = "成功数量。")
        long succeeded,
        @Schema(description = "失败数量。")
        long failed,
        @Schema(description = "被阻断数量。")
        long blocked,
        @Schema(description = "输入 token 数。")
        long inputTokens,
        @Schema(description = "输出 token 数。")
        long outputTokens,
        @Schema(description = "总成本。")
        BigDecimal totalCost
) {
    }
}
