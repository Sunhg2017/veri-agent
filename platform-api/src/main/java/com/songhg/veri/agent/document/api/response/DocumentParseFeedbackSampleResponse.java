package com.songhg.veri.agent.document.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record DocumentParseFeedbackSampleResponse(
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "候选 ID。")
        UUID candidateId,
        @Schema(description = "文档导入任务 ID。")
        UUID importId,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        String projectId,
        @Schema(description = "文档或数据源类型。")
        String sourceType,
        @Schema(description = "脱敏输入摘要。")
        String inputDigest,
        @Schema(description = "来源引用的脱敏摘要，用于去重和审计。")
        String sourceRefDigest,
        @Schema(description = "来源片段的脱敏摘要，用于去重和审计。")
        String sourceFragmentDigest,
        @Schema(description = "解析来源，例如规则解析或模型解析。")
        String parseSource,
        @Schema(description = "模型调用记录 ID。")
        UUID modelInvocationId,
        @Schema(description = "模型供应商名称。")
        String modelProviderName,
        @Schema(description = "模型名称。")
        String modelName,
        @Schema(description = "解析反馈修正类型。")
        String correctionType,
        @Schema(description = "发生变化的字段列表。")
        String changedFields,
        @Schema(description = "修正前候选快照。")
        JsonNode beforeSnapshot,
        @Schema(description = "修正后候选快照。")
        JsonNode afterSnapshot,
        @Schema(description = "样本治理状态。")
        String curationStatus,
        @Schema(description = "创建人。")
        String createdBy,
        @Schema(description = "创建时间。")
        Instant createdAt,
        @Schema(description = "最近更新时间。")
        Instant updatedAt
) {
}
