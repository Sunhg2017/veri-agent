package com.songhg.veri.agent.modelaccess.application.port;

import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;



public interface ModelInvocationJobRepository {

    ModelInvocationJobRecord save(ModelInvocationJobRecord job);

    Optional<ModelInvocationJobRecord> job(UUID jobId);

    List<ModelInvocationJobRecord> queuedJobs();

    boolean markRunning(UUID jobId, Instant startedAt);

    void markSucceeded(UUID jobId, Instant finishedAt, ModelInvocationResult response, String responseJson);

    void markFailed(UUID jobId, Instant finishedAt, String errorCode, String errorMessage);

    boolean cancelQueued(UUID jobId, Instant finishedAt, String errorCode, String errorMessage);

    void markCancelRequested(UUID jobId, String errorCode, String errorMessage);

    int markRunningJobsFailed(Instant finishedAt, Instant staleBefore, String errorCode, String errorMessage);
}
