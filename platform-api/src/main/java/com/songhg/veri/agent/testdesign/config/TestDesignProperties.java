package com.songhg.veri.agent.testdesign.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WP5 用例生成与评审的运行配置
 */
@ConfigurationProperties(prefix = "veri-agent.test-design")
public record TestDesignProperties(
        /** 服务间调用令牌，仅用于受控内部调用，不应写入日志 */
        String serviceToken,
        /** 是否允许创建生成任务 */
        @DefaultValue("true") boolean generationEnabled,
        /** 生成模式，例如规则模板或后续模型生成模式 */
        @DefaultValue("RULE_TEMPLATE") String generationMode,
        /** 默认 Prompt 模板标识 */
        @DefaultValue("wp5-test-design-v1") String promptKey,
        /** 默认 Prompt 模板版本 */
        @DefaultValue("1.0.0") String promptVersion,
        /** generationMode=MODEL 时是否在模型失败后等价切换为 MODEL_WITH_FALLBACK */
        @DefaultValue("false") boolean modelFallbackEnabled,
        /** 单个任务允许覆盖的最大需求数 */
        @DefaultValue("20") int maxRequirementsPerTask,
        /** 每个需求允许生成的最大候选数量 */
        @DefaultValue("3") int maxCasesPerRequirement,
        /** 上下文裁剪：每个需求最多纳入的追踪关联 API、页面和业务流摘要数量 */
        @DefaultValue("5") int contextLinkedAssetsPerRequirement,
        /** 上下文裁剪：创建任务时每类显式 API、页面或业务流资产最多允许纳入的数量 */
        @DefaultValue("5") int contextExplicitAssetsPerType,
        /** 上下文裁剪：每个需求最多纳入的历史用例摘要数量 */
        @DefaultValue("5") int contextExistingCasesPerRequirement,
        /** 上下文裁剪：需求描述摘要最大字符数 */
        @DefaultValue("240") int contextRequirementDescriptionChars,
        /** 上下文裁剪：需求验收标准摘要最大字符数 */
        @DefaultValue("240") int contextAcceptanceCriteriaChars,
        /** 上下文裁剪：接口 schema、页面树和流程 JSON 摘要最大字符数 */
        @DefaultValue("240") int contextAssetSchemaChars,
        /** 单次批量评审允许处理的最大候选数量 */
        @DefaultValue("100") int batchActionLimit,
        /** 创建任务后是否通过平台事件异步生成候选 */
        @DefaultValue("true") boolean asyncGenerationEnabled,
        /** 异步生成事件恢复扫描开关 */
        @DefaultValue("true") boolean eventRecoveryEnabled,
        /** 单次恢复扫描最多重新发布的排队任务数 */
        @DefaultValue("100") int eventRecoveryBatchSize,
        /** 运行中生成任务超过该秒数未更新则由恢复扫描标记失败，非正数表示关闭 */
        @DefaultValue("600") long eventRecoveryRunningTimeoutSeconds,
        /** 排队生成任务超过该秒数未更新则触发聚合队列滞留告警，非正数表示关闭 */
        @DefaultValue("120") long eventRecoveryQueueLagWarningSeconds,
        /** 任务质量准出：最低步骤完整率百分比 */
        @DefaultValue("100") double readinessMinStepCompletePercent,
        /** 任务质量准出：最低最终预期完整率百分比 */
        @DefaultValue("100") double readinessMinExpectedCompletePercent,
        /** 任务质量准出：最高低置信度占比百分比 */
        @DefaultValue("20") double readinessMaxLowConfidencePercent,
        /** 任务质量准出：最高错误候选占比百分比 */
        @DefaultValue("0") double readinessMaxErrorPercent,
        /** 任务质量准出：最高重复键碰撞数量 */
        @DefaultValue("0") long readinessMaxDuplicateKeyCollisions,
        /** 任务质量准出：最高缺需求关联数量 */
        @DefaultValue("0") long readinessMaxMissingRequirementCount,
        /** 任务质量准出：最高缺标题数量 */
        @DefaultValue("0") long readinessMaxMissingTitleCount,
        /** 发布冲突治理：同需求标题高相似阈值，范围 0-1 */
        @DefaultValue("0.86") double conflictTitleSimilarityThreshold,
        /** 发布冲突治理：同需求正文高相似阈值，范围 0-1 */
        @DefaultValue("0.90") double conflictContentSimilarityThreshold,
        /** 报告归档治理：任务报告保留天数，非正数表示使用默认值 */
        @DefaultValue("180") int reportArchiveRetentionDays,
        /** 报告归档治理：是否允许归档外发，当前仅作为报告治理口径输出 */
        @DefaultValue("false") boolean reportArchiveExternalSharingAllowed,
        /** 报告归档治理：是否需要人工审批后才能进入正式归档 */
        @DefaultValue("true") boolean reportArchiveApprovalRequired
) {
    private static final int DEFAULT_LINKED_ASSETS_PER_REQUIREMENT = 5;
    private static final int DEFAULT_EXPLICIT_ASSETS_PER_TYPE = 5;
    private static final int DEFAULT_EXISTING_CASES_PER_REQUIREMENT = 5;
    private static final int DEFAULT_REQUIREMENT_DESCRIPTION_CHARS = 240;
    private static final int DEFAULT_ACCEPTANCE_CRITERIA_CHARS = 240;
    private static final int DEFAULT_ASSET_SCHEMA_CHARS = 240;
    private static final int MAX_CONTEXT_ITEMS = 50;
    private static final int MAX_CONTEXT_PREVIEW_CHARS = 2000;
    private static final int DEFAULT_REPORT_ARCHIVE_RETENTION_DAYS = 180;
    private static final int MAX_REPORT_ARCHIVE_RETENTION_DAYS = 3650;

    public int effectiveContextLinkedAssetsPerRequirement() {
        return boundedPositive(contextLinkedAssetsPerRequirement, DEFAULT_LINKED_ASSETS_PER_REQUIREMENT, MAX_CONTEXT_ITEMS);
    }

    public int effectiveContextExplicitAssetsPerType() {
        return boundedPositive(contextExplicitAssetsPerType, DEFAULT_EXPLICIT_ASSETS_PER_TYPE, MAX_CONTEXT_ITEMS);
    }

    public int effectiveContextExistingCasesPerRequirement() {
        return boundedPositive(contextExistingCasesPerRequirement, DEFAULT_EXISTING_CASES_PER_REQUIREMENT, MAX_CONTEXT_ITEMS);
    }

    public int effectiveContextRequirementDescriptionChars() {
        return boundedPositive(contextRequirementDescriptionChars, DEFAULT_REQUIREMENT_DESCRIPTION_CHARS, MAX_CONTEXT_PREVIEW_CHARS);
    }

    public int effectiveContextAcceptanceCriteriaChars() {
        return boundedPositive(contextAcceptanceCriteriaChars, DEFAULT_ACCEPTANCE_CRITERIA_CHARS, MAX_CONTEXT_PREVIEW_CHARS);
    }

    public int effectiveContextAssetSchemaChars() {
        return boundedPositive(contextAssetSchemaChars, DEFAULT_ASSET_SCHEMA_CHARS, MAX_CONTEXT_PREVIEW_CHARS);
    }

    public Map<String, Integer> effectiveContextLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("requirementDescriptionChars", effectiveContextRequirementDescriptionChars());
        limits.put("acceptanceCriteriaChars", effectiveContextAcceptanceCriteriaChars());
        limits.put("linkedAssetsPerRequirement", effectiveContextLinkedAssetsPerRequirement());
        limits.put("explicitAssetsPerType", effectiveContextExplicitAssetsPerType());
        limits.put("linkedAssetSchemaChars", effectiveContextAssetSchemaChars());
        limits.put("existingCasesPerRequirement", effectiveContextExistingCasesPerRequirement());
        return limits;
    }

    public int effectiveReportArchiveRetentionDays() {
        return boundedPositive(reportArchiveRetentionDays, DEFAULT_REPORT_ARCHIVE_RETENTION_DAYS,
                MAX_REPORT_ARCHIVE_RETENTION_DAYS);
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
