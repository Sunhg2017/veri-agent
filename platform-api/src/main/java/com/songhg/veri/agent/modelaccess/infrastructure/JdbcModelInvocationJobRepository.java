package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.infrastructure.mapper.ModelAccessMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class JdbcModelInvocationJobRepository implements ModelInvocationJobRepository {

    private final ModelAccessMapper mapper;

    public JdbcModelInvocationJobRepository(ModelAccessMapper mapper) {
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
    public void markSucceeded(UUID jobId, Instant finishedAt, ModelInvocationResult response, String responseJson) {
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
    public int markRunningJobsFailed(Instant finishedAt, Instant staleBefore, String errorCode, String errorMessage) {
        return mapper.markRunningInvocationJobsFailed(finishedAt, staleBefore, errorCode, errorMessage);
    }
}
