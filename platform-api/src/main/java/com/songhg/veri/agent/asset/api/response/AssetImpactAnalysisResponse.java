package com.songhg.veri.agent.asset.api.response;

import com.songhg.veri.agent.asset.application.view.AssetImpactNodeResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record AssetImpactAnalysisResponse(
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        String projectId,
        @Schema(description = "影响分析主体资产类型。")
        String subjectType,
        @Schema(description = "影响分析主体资产 ID。")
        UUID subjectId,
        @Schema(description = "受影响需求数量。")
        int requirementCount,
        @Schema(description = "受影响 API 数量。")
        int apiCount,
        @Schema(description = "受影响页面数量。")
        int pageCount,
        @Schema(description = "受影响业务流数量。")
        int flowCount,
        @Schema(description = "受影响测试用例数量。")
        int caseCount,
        @Schema(description = "需求列表。")
        List<AssetImpactNodeResponse> requirements,
        @Schema(description = "API 资产列表。")
        List<AssetImpactNodeResponse> apis,
        @Schema(description = "页面资产列表。")
        List<AssetImpactNodeResponse> pages,
        @Schema(description = "业务流资产列表。")
        List<AssetImpactNodeResponse> flows,
        @Schema(description = "测试用例资产列表。")
        List<AssetImpactNodeResponse> testCases,
        @Schema(description = "缺口或风险项列表。")
        List<String> gaps,
        @Schema(description = "结果生成时间。")
        Instant generatedAt
) {
}
