package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * WP5 任务详情接口出参。
 */
public record TestDesignTaskDetailResponse(
        @Schema(description = "任务详情。")
        TestDesignTaskResponse task,
        @Schema(description = "候选列表或批量候选目标。")
        List<TestDesignCandidateResponse> candidates,
        @Schema(description = "发布记录列表。")
        List<TestDesignPublishRecordResponse> publishRecords
) {
}
