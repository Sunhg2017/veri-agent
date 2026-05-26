package com.songhg.veri.agent.integration.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record PlatformContextResponse(
        @Schema(description = "资源类型")
        String resourceType,
        @Schema(description = "资源 ID")
        String resourceId,
        @Schema(description = "业务状态")
        String status,
        @Schema(description = "敏感级别，用于模型调用和数据出域策略")
        String sensitivityLevel,
        @Schema(description = "是否允许使用公网模型处理该范围数据")
        boolean allowPublicModel,
        @Schema(description = "平台上下文允许包含的数据范围")
        List<String> include,
        @Schema(description = "平台上下文校验时间")
        Instant validatedAt
) {
}
