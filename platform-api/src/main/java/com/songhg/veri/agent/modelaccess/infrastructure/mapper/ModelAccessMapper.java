package com.songhg.veri.agent.modelaccess.infrastructure.mapper;

import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModelAccessMapper {

    List<ModelProviderConfig> providers();

    ModelProviderConfig provider(@Param("id") UUID id);

    void upsertProvider(ModelProviderConfig provider);

    List<PromptTemplate> prompts(@Param("promptKey") String promptKey);

    PromptTemplate prompt(@Param("id") UUID id);

    PromptTemplate activePrompt(@Param("promptKey") String promptKey);

    void upsertPrompt(PromptTemplate prompt);

    void deactivateActivePrompts(@Param("promptKey") String promptKey);

    void insertInvocation(InvocationRecord record);

    List<InvocationRecord> invocations(@Param("query") InvocationQuery query);

    long countInvocations(@Param("query") InvocationQuery query);

    InvocationSummaryResponse invocationSummary(@Param("query") InvocationQuery query);
}
