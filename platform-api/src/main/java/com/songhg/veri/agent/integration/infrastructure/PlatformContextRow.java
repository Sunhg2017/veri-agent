package com.songhg.veri.agent.integration.infrastructure;

public record PlatformContextRow(
        /** 平台资源 ID，通常为项目、应用或需求等上下文对象标识。 */
        String resourceId,
        /** 资源状态，用于调用方判断是否可用。 */
        String status,
        /** 数据敏感级别，传递给模型访问策略。 */
        String sensitivityLevel,
        /** 是否允许调用公共模型。 */
        boolean allowPublicModel
) {
}
