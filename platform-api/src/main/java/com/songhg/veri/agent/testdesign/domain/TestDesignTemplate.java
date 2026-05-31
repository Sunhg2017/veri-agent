package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 用例生成模板的领域快照。
 *
 * <p>模板只保存生成配置引用和值，不保存 Prompt 正文、上下文正文或模型输入，任务创建时会把模板解析成任务
 * 自身的 prompt、覆盖类型和上下文默认值快照。</p>
 */
public record TestDesignTemplate(
        /** 主键 ID */
        UUID id,
        /** 所属项目 ID；为空表示平台全局模板 */
        String projectId,
        /** 模板名称 */
        String name,
        /** 模板说明 */
        String description,
        /** Prompt 模板标识 */
        String promptKey,
        /** Prompt 模板版本 */
        String promptVersion,
        /** 覆盖类型列表 */
        String coverageTypes,
        /** 每个需求生成的候选数量上限 */
        int caseCountPerRequirement,
        /** 上下文默认值 JSON，仅允许保存环境键和显式资产 ID 列表等引用 */
        String contextDefaultsJson,
        /** 是否启用 */
        boolean enabled,
        /** 创建人 */
        String createdBy,
        /** 最近更新人 */
        String updatedBy,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) {
}
