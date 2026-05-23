package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.mapper.ModelAccessMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class PostgresModelInvocationJobRepository implements ModelInvocationJobRepository {

    private final ModelAccessMapper mapper;

    public PostgresModelInvocationJobRepository(ModelAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ModelInvocationJobRecord save(ModelInvocationJobRecord job) {
        mapper.insertInvocationJob(job);
        return job;
    }

    @Override
    public Optional<ModelInvocationJobRecord> job(UUID jobId) {
        return Optional.ofNullable(mapper.invocationJob(jobId));
    }

    @Override
    public List<ModelInvocationJobRecord> queuedJobs() {
        return mapper.queuedInvocationJobs();
    }

    @Override
    public boolean markRunning(UUID jobId, Instant startedAt) {
        return mapper.markInvocationJobRunning(jobId, startedAt) == 1;
    }

    @Override
    public void markSucceeded(UUID jobId, Instant finishedAt, InvokeModelResponse response, String responseJson) {
        mapper.markInvocationJobSucceeded(jobId, finishedAt, response.invocationId(), responseJson);
    }

    @Override
    public void markFailed(UUID jobId, Instant finishedAt, String errorCode, String errorMessage) {
        mapper.markInvocationJobFailed(jobId, finishedAt, errorCode, errorMessage);
    }

    @Override
    public boolean cancelQueued(UUID jobId, Instant finishedAt, String errorCode, String errorMessage) {
        return mapper.cancelQueuedInvocationJob(jobId, finishedAt, errorCode, errorMessage) == 1;
    }

    @Override
    public void markCancelRequested(UUID jobId, String errorCode, String errorMessage) {
        mapper.markInvocationJobCancelRequested(jobId, errorCode, errorMessage);
    }

    @Override
    public int markRunningJobsFailed(Instant finishedAt, String errorCode, String errorMessage) {
        return mapper.markRunningInvocationJobsFailed(finishedAt, errorCode, errorMessage);
    }
}
