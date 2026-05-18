package com.songhg.veri.agent.modelaccess.api.request;

import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvokeModelRequest(
        @NotBlank String projectId,
        String applicationId,
        String environmentId,
        String promptKey,
        Map<String, String> promptVariables,
        @NotEmpty @Valid List<ChatMessage> messages,
        UUID providerId,
        String modelName,
        Boolean allowPublicModel,
        String sensitivityLevel
) {
}
