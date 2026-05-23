package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.infrastructure.mapper.ModelAccessMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class PostgresModelAccessRepository implements ModelAccessRepository {

    private final ModelAccessMapper mapper;

    public PostgresModelAccessRepository(ModelAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ModelProviderConfig> providers() {
        return mapper.providers();
    }

    @Override
    public Optional<ModelProviderConfig> provider(UUID id) {
        return Optional.ofNullable(mapper.provider(id));
    }

    @Override
    public ModelProviderConfig saveProvider(ModelProviderConfig provider) {
        mapper.upsertProvider(provider);
        return provider;
    }

    @Override
    public List<PromptTemplate> prompts(String promptKey) {
        return mapper.prompts(promptKey);
    }

    @Override
    public Optional<PromptTemplate> prompt(UUID id) {
        return Optional.ofNullable(mapper.prompt(id));
    }

    @Override
    public Optional<PromptTemplate> activePrompt(String promptKey) {
        return Optional.ofNullable(mapper.activePrompt(promptKey));
    }

    @Override
    public PromptTemplate savePrompt(PromptTemplate prompt) {
        mapper.upsertPrompt(prompt);
        return prompt;
    }

    @Override
    public void deactivateActivePrompts(String promptKey) {
        mapper.deactivateActivePrompts(promptKey);
    }

    @Override
    public InvocationRecord saveInvocation(InvocationRecord record) {
        mapper.insertInvocation(record);
        return record;
    }

    @Override
    public List<InvocationRecord> invocations(InvocationQuery query) {
        return mapper.invocations(query);
    }

    @Override
    public long countInvocations(InvocationQuery query) {
        return mapper.countInvocations(query);
    }

    @Override
    public List<String> distinctProjectIds(Instant startTime, Instant endTime) {
        return mapper.distinctProjectIds(startTime, endTime);
    }

    @Override
    public List<String> distinctActorServices(Instant startTime, Instant endTime) {
        return mapper.distinctActorServices(startTime, endTime);
    }

    @Override
    public InvocationSummaryResponse invocationSummary(InvocationQuery query) {
        return mapper.invocationSummary(query);
    }
}
