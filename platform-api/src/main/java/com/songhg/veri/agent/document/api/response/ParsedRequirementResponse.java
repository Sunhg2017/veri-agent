package com.songhg.veri.agent.document.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ParsedRequirementResponse(
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "业务说明或补充描述")
        String description,
        @Schema(description = "优先级")
        String priority,
        @Schema(description = "验收标准")
        String acceptanceCriteria,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达")
        String tags,
        @Schema(description = "发布或匹配到的 WP3 需求资产 ID")
        UUID assetRequirementId,
        @Schema(description = "解析来源，例如规则解析或模型解析")
        String parseSource,
        @Schema(description = "模型调用记录 ID")
        UUID modelInvocationId,
        @Schema(description = "模型供应商名称")
        String modelProviderName,
        @Schema(description = "模型名称")
        String modelName
) {
}
