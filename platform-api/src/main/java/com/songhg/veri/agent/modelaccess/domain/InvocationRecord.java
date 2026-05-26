package com.songhg.veri.agent.modelaccess.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvocationRecord(
        /** 主键 ID。 */
        UUID id,
        /** 所属项目 ID。 */
        String projectId,
        /** 所属应用 ID。 */
        String applicationId,
        /** 所属环境 ID。 */
        String environmentId,
        /** 调用数据敏感级别。 */
        String sensitivityLevel,
        /** Prompt 模板标识。 */
        String promptKey,
        /** Prompt 模板版本。 */
        Integer promptVersion,
        /** 供应商 ID。 */
        UUID providerId,
        /** 模型供应商名称快照。 */
        String providerName,
        /** 模型名称。 */
        String modelName,
        /** 命中的路由规则名称。 */
        String routingRuleName,
        /** 路由分组。 */
        String routingGroup,
        /** 本次调用声明的模型能力。 */
        String modelCapability,
        /** 业务状态。 */
        InvocationStatus status,
        /** 是否使用了降级供应商。 */
        boolean fallbackUsed,
        /** 提示词内容摘要。 */
        String promptDigest,
        /** 脱敏后的请求预览。 */
        String requestPreview,
        /** 脱敏后的响应预览。 */
        String responsePreview,
        /** 输入 token 数。 */
        int inputTokens,
        /** 输出 token 数。 */
        int outputTokens,
        /** 总成本。 */
        BigDecimal totalCost,
        /** 失败错误码。 */
        String errorCode,
        /** 错误摘要。 */
        String errorMessage,
        /** 调用耗时，单位毫秒。 */
        long latencyMs,
        /** 发起调用的服务编码。 */
        String actorService,
        /** 代理用户 ID。 */
        String delegatedUserId,
        /** 创建时间。 */
        Instant createdAt
) {
}
