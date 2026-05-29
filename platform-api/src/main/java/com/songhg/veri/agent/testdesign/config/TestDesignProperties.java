package com.songhg.veri.agent.testdesign.config;

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
        @DefaultValue("0.90") double conflictContentSimilarityThreshold
) {
}
