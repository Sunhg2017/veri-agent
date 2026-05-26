package com.songhg.veri.agent.modelaccess.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record CostAlertResponse(
        @Schema(description = "作用域说明或作用域编码")
        String scope,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "发起内部调用的服务标识")
        String actorService,
        @Schema(description = "统计周期开始时间")
        String periodStart,
        @Schema(description = "统计周期结束时间")
        String periodEnd,
        @Schema(description = "已消耗成本")
        BigDecimal spentCost,
        @Schema(description = "预算上限")
        BigDecimal budgetLimit,
        @Schema(description = "预算使用比例")
        BigDecimal usageRatio,
        @Schema(description = "告警级别")
        String level,
        @Schema(description = "提示消息")
        String message
) {
}
