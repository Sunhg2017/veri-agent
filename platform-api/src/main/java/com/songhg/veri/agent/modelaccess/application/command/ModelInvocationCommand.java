package com.songhg.veri.agent.modelaccess.application.command;

import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application-layer command for one model invocation.
 *
 * <p>API DTOs are intentionally converted to this contract at the controller boundary so
 * modelaccess application services can evolve without depending on HTTP request packages.</p>
 */
public record ModelInvocationCommand(
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "所属应用 ID")
        String applicationId,
        @Schema(description = "环境 ID")
        String environmentId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 变量")
        Map<String, String> promptVariables,
        @Schema(description = "模型对话消息列表")
        List<ChatMessage> messages,
        @Schema(description = "模型供应商 ID")
        UUID providerId,
        @Schema(description = "模型名称")
        String modelName,
        @Schema(description = "是否允许使用公网模型处理该范围数据")
        Boolean allowPublicModel,
        @Schema(description = "敏感级别，用于模型调用和数据出域策略")
        String sensitivityLevel,
        @Schema(description = "模型能力标识")
        String capability
) {
}
