package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvokeModelRequest(
        @NotBlank @JsonProperty("project_id") String projectId,
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("environment_id") String environmentId,
        @JsonProperty("prompt_key") String promptKey,
        @JsonProperty("prompt_variables") Map<String, String> promptVariables,
        @NotEmpty @Valid List<ChatMessage> messages,
        @JsonProperty("provider_id") UUID providerId,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("allow_public_model") Boolean allowPublicModel,
        @JsonProperty("sensitivity_level") String sensitivityLevel
) {
}
