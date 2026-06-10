package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Count-only result for manual queued event replay.
 */
public record TestDesignQueuedEventReplayResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "重放类型")
        String replayType,
        @Schema(description = "请求上限")
        int requestedLimit,
        @Schema(description = "已重放生成任务事件数")
        int generationTaskEvents,
        @Schema(description = "已重放发布任务事件数")
        int publishTaskEvents,
        @Schema(description = "已纳入发布候选数")
        int publishCandidateEvents,
        @Schema(description = "是否支持重放")
        boolean replaySupported,
        @Schema(description = "是否导出事件 payload")
        boolean eventPayloadExported,
        @Schema(description = "是否导出事件明细标识")
        boolean eventIdentifierListExported,
        @Schema(description = "是否导出候选明细标识")
        boolean candidateIdentifierListExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "重放时间")
        Instant replayedAt
) {
}
