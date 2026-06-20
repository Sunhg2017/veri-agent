package com.songhg.veri.agent.modelaccess.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

public record ModelQualityEvaluationSummaryResponse(
        @Schema(description = "评测语料版本")
        String corpusVersion,
        @Schema(description = "任务类型过滤，ALL 表示全部任务")
        String taskTypeFilter,
        @Schema(description = "本次纳入统计的场景数")
        int scenarioCount,
        @Schema(description = "当前评测门槛")
        Thresholds thresholds,
        @Schema(description = "任务级统计")
        List<TaskQualityStatsResponse> taskStats,
        @Schema(description = "总览统计")
        TaskQualityStatsResponse totalStats,
        @Schema(description = "涉及的 Prompt 绑定")
        Set<String> promptBindings,
        @Schema(description = "涉及的 provider group")
        Set<String> providerGroups
) {

    public record Thresholds(
            @Schema(description = "场景通过率下限")
            double minScenarioPassRate,
            @Schema(description = "required term recall 下限")
            double minRequiredTermRecall,
            @Schema(description = "forbidden term clean rate 下限")
            double minForbiddenTermCleanRate
    ) {
    }

    public record TaskQualityStatsResponse(
            @Schema(description = "任务类型")
            String taskType,
            @Schema(description = "场景数")
            int scenarioCount,
            @Schema(description = "通过场景数")
            int passedScenarios,
            @Schema(description = "requiredTerms 总数")
            int requiredTermCount,
            @Schema(description = "命中的 requiredTerms 数")
            int requiredTermMatches,
            @Schema(description = "forbiddenTerms 总数")
            int forbiddenTermCount,
            @Schema(description = "命中的 forbiddenTerms 数")
            int forbiddenTermMatches,
            @Schema(description = "场景通过率")
            double scenarioPassRate,
            @Schema(description = "required term recall")
            double requiredTermRecall,
            @Schema(description = "forbidden term clean rate")
            double forbiddenTermCleanRate,
            @Schema(description = "是否达到门槛")
            boolean passed,
            @Schema(description = "失败样本摘要")
            List<String> failures
    ) {
    }
}
