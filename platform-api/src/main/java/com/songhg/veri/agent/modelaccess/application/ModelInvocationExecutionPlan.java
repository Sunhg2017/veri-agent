package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import java.time.Instant;
import java.util.List;

/**
 * Immutable execution context shared by routing and provider invocation stages.
 */
record ModelInvocationExecutionPlan(
        Instant startedAt,
        PromptTemplate prompt,
        String renderedPrompt,
        String messageText,
        String fullPrompt,
        String effectiveSensitivityLevel,
        String modelCapability,
        List<ModelProviderConfig> candidates,
        String routingRuleName
) {
}
