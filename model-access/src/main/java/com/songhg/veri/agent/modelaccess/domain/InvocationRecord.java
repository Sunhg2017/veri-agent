package com.songhg.veri.agent.modelaccess.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvocationRecord(
        UUID id,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("environment_id") String environmentId,
        @JsonProperty("sensitivity_level") String sensitivityLevel,
        @JsonProperty("prompt_key") String promptKey,
        @JsonProperty("prompt_version") Integer promptVersion,
        @JsonProperty("provider_id") UUID providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("model_name") String modelName,
        InvocationStatus status,
        @JsonProperty("fallback_used") boolean fallbackUsed,
        @JsonProperty("prompt_digest") String promptDigest,
        @JsonProperty("request_preview") String requestPreview,
        @JsonProperty("response_preview") String responsePreview,
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens,
        @JsonProperty("total_cost") BigDecimal totalCost,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("actor_service") String actorService,
        @JsonProperty("delegated_user_id") String delegatedUserId,
        @JsonProperty("created_at") Instant createdAt
) {
}
