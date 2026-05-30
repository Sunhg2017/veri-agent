package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * WP5 任务本域审计链最近事件，只暴露动作、结果和资源引用。
 */
public record TestDesignAuditTimelineItemResponse(
        @Schema(description = "事件来源，TASK/REVIEW/PUBLISH")
        String source,
        @Schema(description = "动作编码")
        String action,
        @Schema(description = "处理结果或状态流转")
        String result,
        @Schema(description = "关联候选 ID")
        UUID candidateId,
        @Schema(description = "关联测试用例资产 ID")
        UUID assetCaseId,
        @Schema(description = "执行人")
        String actor,
        @Schema(description = "是否包含人工说明或错误摘要")
        boolean hasNote,
        @Schema(description = "事件创建时间")
        Instant createdAt
) {
}
