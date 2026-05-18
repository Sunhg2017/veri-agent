package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelAccessRepository {

    List<ModelProviderConfig> providers();

    Optional<ModelProviderConfig> provider(UUID id);

    ModelProviderConfig saveProvider(ModelProviderConfig provider);

    List<PromptTemplate> prompts(String promptKey);

    Optional<PromptTemplate> prompt(UUID id);

    Optional<PromptTemplate> activePrompt(String promptKey);

    PromptTemplate savePrompt(PromptTemplate prompt);

    void deactivateActivePrompts(String promptKey);

    InvocationRecord saveInvocation(InvocationRecord record);

    List<InvocationRecord> invocations(InvocationQuery query);

    long countInvocations(InvocationQuery query);

    InvocationSummaryResponse invocationSummary(InvocationQuery query);
}
