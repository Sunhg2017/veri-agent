package com.songhg.veri.agent.modelaccess.application.port;

import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import java.time.Instant;
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

    Optional<InvocationRecord> invocation(UUID id);

    List<InvocationRecord> invocations(InvocationQuery query);

    long countInvocations(InvocationQuery query);

    List<String> distinctProjectIds(Instant startTime, Instant endTime);

    List<String> distinctActorServices(Instant startTime, Instant endTime);

    InvocationSummaryResult invocationSummary(InvocationQuery query);
}
