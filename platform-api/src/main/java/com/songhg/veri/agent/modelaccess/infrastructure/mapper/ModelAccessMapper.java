package com.songhg.veri.agent.modelaccess.infrastructure.mapper;

import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import java.time.Instant;
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

    List<String> distinctProjectIds(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    List<String> distinctActorServices(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    InvocationSummaryResult invocationSummary(@Param("query") InvocationQuery query);

    void insertInvocationJob(ModelInvocationJobRecord job);

    ModelInvocationJobRecord invocationJob(@Param("jobId") UUID jobId);

    List<ModelInvocationJobRecord> queuedInvocationJobs();

    int markInvocationJobRunning(@Param("jobId") UUID jobId, @Param("startedAt") Instant startedAt);

    void markInvocationJobSucceeded(
            @Param("jobId") UUID jobId,
            @Param("finishedAt") Instant finishedAt,
            @Param("invocationId") UUID invocationId,
            @Param("responseJson") String responseJson
    );

    void markInvocationJobFailed(
            @Param("jobId") UUID jobId,
            @Param("finishedAt") Instant finishedAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    int cancelQueuedInvocationJob(
            @Param("jobId") UUID jobId,
            @Param("finishedAt") Instant finishedAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    void markInvocationJobCancelRequested(
            @Param("jobId") UUID jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    int markRunningInvocationJobsFailed(
            @Param("finishedAt") Instant finishedAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );
}
