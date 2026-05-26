package com.songhg.veri.agent.document.api.response;

import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record DocumentSourceHealthResponse(
        @Schema(description = "文档输入源 ID")
        UUID sourceId,
        @Schema(description = "文档输入源编码")
        String sourceCode,
        @Schema(description = "文档或数据源类型")
        DocumentSourceType sourceType,
        @Schema(description = "文档输入源状态")
        DocumentSourceStatus sourceStatus,
        @Schema(description = "是否支持数据流式接入")
        boolean dataFlowSupported,
        @Schema(description = "当前资源是否就绪")
        boolean ready,
        @Schema(description = "提示消息")
        String message,
        @Schema(description = "Webhook 接入路径")
        String webhookPath,
        @Schema(description = "签名算法")
        String signatureAlgorithm,
        @Schema(description = "是否已配置密钥引用")
        boolean secretRefConfigured,
        @Schema(description = "事件协议版本")
        String eventVersion,
        @Schema(description = "字段映射版本")
        String mappingVersion,
        @Schema(description = "检查时间")
        Instant checkedAt,
        @Schema(description = "最近事件接收时间")
        Instant lastEventAt,
        @Schema(description = "最近事件处理状态")
        WebhookEventStatus lastEventStatus,
        @Schema(description = "最近签名校验状态")
        WebhookSignatureStatus lastSignatureStatus,
        @Schema(description = "最近一次错误信息")
        String lastErrorMessage
) {
}
