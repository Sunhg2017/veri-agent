package com.songhg.veri.agent.asset.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TraceLinkResponse(
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "关联需求 ID。")
        UUID requirementId,
        @Schema(description = "关联 API 资产 ID。")
        UUID apiId,
        @Schema(description = "关联页面资产 ID。")
        UUID pageId,
        @Schema(description = "关联业务流资产 ID。")
        UUID flowId,
        @Schema(description = "关联测试用例资产 ID。")
        UUID caseId,
        @Schema(description = "创建时间。")
        Instant createdAt
) {
}
