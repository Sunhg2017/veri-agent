package com.songhg.veri.agent.document.api.request;

import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpsertDocumentSourceRequest(
        @Schema(description = "文档输入源编码")
        @NotBlank
        @Size(max = 64)
        String sourceCode,
        @Schema(description = "名称，用于列表展示和人工识别")
        @NotBlank
        @Size(max = 128)
        String name,
        @Schema(description = "文档或数据源类型")
        @NotNull
        DocumentSourceType sourceType,
        @Schema(description = "业务状态")
        DocumentSourceStatus status,
        @Schema(description = "数据源或服务端点 URL")
        String endpointUrl,
        @Schema(description = "默认落库项目 ID")
        String defaultProjectId,
        @Schema(description = "字段映射配置 ID")
        UUID mappingId,
        @Schema(description = "密钥引用地址，不包含明文密钥")
        @Size(max = 128)
        String secretRef,
        @Schema(description = "事件协议版本")
        @Size(max = 32)
        String eventVersion,
        @Schema(description = "字段映射版本")
        @Size(max = 64)
        String mappingVersion,
        @Schema(description = "业务说明或补充描述")
        String description
) {
}
