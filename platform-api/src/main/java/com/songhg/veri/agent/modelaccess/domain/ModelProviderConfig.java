package com.songhg.veri.agent.modelaccess.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModelProviderConfig(
        /** 主键 ID */
        UUID id,
        /** 名称 */
        String name,
        /** 供应商类型 */
        ProviderType providerType,
        /** 路由分组 */
        String routingGroup,
        /** 能力列表 */
        String capabilities,
        /** 基础地址 */
        String baseUrl,
        /** API Key 密钥引用 */
        String apiKeyRef,
        /** 业务状态 */
        ProviderStatus status,
        /** 优先级 */
        int priority,
        /** 超时时间，单位毫秒 */
        int timeoutMs,
        /** 每千输入 token 成本 */
        BigDecimal inputCostPer1kTokens,
        /** 每千输出 token 成本 */
        BigDecimal outputCostPer1kTokens,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) {

    public boolean enabled() {
        return status == ProviderStatus.ENABLED;
    }
}
