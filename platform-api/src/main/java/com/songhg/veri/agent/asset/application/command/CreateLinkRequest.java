package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateLinkRequest(
        @Schema(description = "关联需求 ID")
        @NotNull UUID requirementId,
        @Schema(description = "关联 API 资产 ID")
        UUID apiId,
        @Schema(description = "关联页面资产 ID")
        UUID pageId,
        @Schema(description = "关联业务流资产 ID")
        UUID flowId,
        @Schema(description = "关联测试用例资产 ID")
        UUID caseId
) {
}
