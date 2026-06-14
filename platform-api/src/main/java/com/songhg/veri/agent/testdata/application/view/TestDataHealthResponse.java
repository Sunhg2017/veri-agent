package com.songhg.veri.agent.testdata.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record TestDataHealthResponse(
        @Schema(description = "服务标识")
        String service,
        @Schema(description = "状态")
        String status,
        @Schema(description = "WP8 控制面是否启用")
        boolean enabled,
        @Schema(description = "是否允许执行清理动作")
        boolean cleanupEnabled,
        @Schema(description = "是否允许导出脱敏摘要")
        boolean exportEnabled,
        @Schema(description = "单数据集记录数量上限")
        int recordMaxCount,
        @Schema(description = "单记录脱敏摘要字节上限")
        int recordSummaryMaxBytes,
        @Schema(description = "默认账号租借 TTL 秒数")
        int defaultLeaseTtlSeconds,
        @Schema(description = "最大账号租借 TTL 秒数")
        int maxLeaseTtlSeconds,
        @Schema(description = "当前 WP8 功能边界")
        Map<String, Object> policy
) {
}
