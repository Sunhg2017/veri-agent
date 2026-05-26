package com.songhg.veri.agent.document.domain;

import java.util.UUID;

public record ParsedRequirementDraft(
        /** 标题。 */
        String title,
        /** 业务说明。 */
        String description,
        /** 优先级。 */
        String priority,
        /** 验收标准。 */
        String acceptanceCriteria,
        /** 标签。 */
        String tags,
        /** 需求资产 ID。 */
        UUID assetRequirementId,
        /** 解析来源。 */
        String parseSource,
        /** 模型调用记录 ID。 */
        UUID modelInvocationId,
        /** 模型供应商名称。 */
        String modelProviderName,
        /** 模型名称。 */
        String modelName
) {

    public ParsedRequirementDraft(
            String title,
            String description,
            String priority,
            String acceptanceCriteria,
            String tags,
            UUID assetRequirementId
    ) {
        this(title, description, priority, acceptanceCriteria, tags, assetRequirementId, "RULE", null, null, null);
    }

    public ParsedRequirementDraft withAssetRequirementId(UUID requirementId) {
        return new ParsedRequirementDraft(
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                requirementId,
                parseSource,
                modelInvocationId,
                modelProviderName,
                modelName
        );
    }

    public ParsedRequirementDraft withParseMetadata(
            String source,
            UUID invocationId,
            String providerName,
            String model
    ) {
        return new ParsedRequirementDraft(
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                assetRequirementId,
                source,
                invocationId,
                providerName,
                model
        );
    }
}
