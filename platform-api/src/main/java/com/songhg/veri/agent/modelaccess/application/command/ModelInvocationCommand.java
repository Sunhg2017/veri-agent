package com.songhg.veri.agent.modelaccess.application.command;

import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
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
        String projectId,
        String applicationId,
        String environmentId,
        String promptKey,
        Map<String, String> promptVariables,
        List<ChatMessage> messages,
        UUID providerId,
        String modelName,
        Boolean allowPublicModel,
        String sensitivityLevel,
        String capability
) {
}
