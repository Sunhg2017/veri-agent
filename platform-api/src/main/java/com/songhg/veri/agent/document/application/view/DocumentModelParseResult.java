package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.ParsedRequirementDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record DocumentModelParseResult(
        @Schema(description = "模型解析出的需求草稿列表")
        List<ParsedRequirementDraft> drafts,
        @Schema(description = "模型调用记录 ID")
        UUID invocationId,
        @Schema(description = "模型供应商名称")
        String providerName,
        @Schema(description = "模型名称")
        String modelName,
        @Schema(description = "错误编码")
        String errorCode,
        @Schema(description = "错误摘要")
        String errorMessage
) {

    public static DocumentModelParseResult disabled() {
        return new DocumentModelParseResult(List.of(), null, null, null, null, null);
    }

    public static DocumentModelParseResult succeeded(
            List<ParsedRequirementDraft> drafts,
            UUID invocationId,
            String providerName,
            String modelName
    ) {
        return new DocumentModelParseResult(drafts, invocationId, providerName, modelName, null, null);
    }

    public static DocumentModelParseResult failed(
            UUID invocationId,
            String providerName,
            String modelName,
            String errorCode,
            String errorMessage
    ) {
        return new DocumentModelParseResult(List.of(), invocationId, providerName, modelName, errorCode, errorMessage);
    }

    public boolean attempted() {
        return invocationId != null || errorCode != null || errorMessage != null || !drafts.isEmpty();
    }

    public boolean succeeded() {
        return errorCode == null && errorMessage == null && !drafts.isEmpty();
    }
}
