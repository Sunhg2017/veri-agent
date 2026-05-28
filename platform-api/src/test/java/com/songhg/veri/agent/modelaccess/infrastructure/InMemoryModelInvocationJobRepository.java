package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
@Primary
public class InMemoryModelInvocationJobRepository implements ModelInvocationJobRepository {

    private final Map<UUID, ModelInvocationJobRecord> jobs = new ConcurrentHashMap<>();

    @Override
    public ModelInvocationJobRecord save(ModelInvocationJobRecord job) {
        jobs.put(job.jobId(), job);
        return job;
    }

    @Override
    public Optional<ModelInvocationJobRecord> job(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<ModelInvocationJobRecord> jobByInvocationId(UUID invocationId) {
        return jobs.values().stream()
                .filter(job -> invocationId.equals(job.invocationId()))
                .findFirst();
    }

    @Override
    public List<ModelInvocationJobRecord> queuedJobs() {
        return jobs.values()
                .stream()
                .filter(job -> job.status() == ModelInvocationJobStatus.QUEUED)
                .sorted(Comparator.comparing(ModelInvocationJobRecord::createdAt))
                .toList();
    }

    @Override
    public boolean markRunning(UUID jobId, Instant startedAt) {
        return updateIf(jobId, ModelInvocationJobStatus.QUEUED, job -> new ModelInvocationJobRecord(
                job.jobId(),
                ModelInvocationJobStatus.RUNNING,
                job.requestJson(),
                job.actorService(),
                job.delegatedUserId(),
                job.traceId(),
                job.createdAt(),
                startedAt,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Override
    public void markSucceeded(UUID jobId, Instant finishedAt, ModelInvocationResult response, String responseJson) {
        update(jobId, job -> new ModelInvocationJobRecord(
                job.jobId(),
                ModelInvocationJobStatus.SUCCEEDED,
                job.requestJson(),
                job.actorService(),
                job.delegatedUserId(),
                job.traceId(),
                job.createdAt(),
                job.startedAt(),
                finishedAt,
                response.invocationId(),
                null,
                null,
                responseJson
        ));
    }

    @Override
    public void markFailed(UUID jobId, Instant finishedAt, String errorCode, String errorMessage) {
        update(jobId, job -> new ModelInvocationJobRecord(
                job.jobId(),
                ModelInvocationJobStatus.FAILED,
                job.requestJson(),
                job.actorService(),
                job.delegatedUserId(),
                job.traceId(),
                job.createdAt(),
                job.startedAt(),
                finishedAt,
                job.invocationId(),
                errorCode,
                errorMessage,
                job.responseJson()
        ));
    }

    @Override
    public boolean cancelQueued(UUID jobId, Instant finishedAt, String errorCode, String errorMessage) {
        return updateIf(jobId, ModelInvocationJobStatus.QUEUED, job -> new ModelInvocationJobRecord(
                job.jobId(),
                ModelInvocationJobStatus.CANCELLED,
                job.requestJson(),
                job.actorService(),
                job.delegatedUserId(),
                job.traceId(),
                job.createdAt(),
                job.startedAt(),
                finishedAt,
                null,
                errorCode,
                errorMessage,
                null
        ));
    }

    @Override
    public void markCancelRequested(UUID jobId, String errorCode, String errorMessage) {
        update(jobId, job -> terminal(job.status()) ? job : new ModelInvocationJobRecord(
                job.jobId(),
                job.status(),
                job.requestJson(),
                job.actorService(),
                job.delegatedUserId(),
                job.traceId(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.invocationId(),
                errorCode,
                errorMessage,
                job.responseJson()
        ));
    }

    @Override
    public int markRunningJobsFailed(Instant finishedAt, Instant staleBefore, String errorCode, String errorMessage) {
        int updated = 0;
        for (ModelInvocationJobRecord job : jobs.values()) {
            Instant lastTouchedAt = job.startedAt() == null ? job.createdAt() : job.startedAt();
            if (job.status() == ModelInvocationJobStatus.RUNNING && lastTouchedAt.isBefore(staleBefore)) {
                markFailed(job.jobId(), finishedAt, errorCode, errorMessage);
                updated++;
            }
        }
        return updated;
    }

    private boolean updateIf(UUID jobId, ModelInvocationJobStatus expected, JobUpdater updater) {
        synchronized (jobs) {
            ModelInvocationJobRecord current = jobs.get(jobId);
            if (current == null || current.status() != expected) {
                return false;
            }
            jobs.put(jobId, updater.update(current));
            return true;
        }
    }

    private void update(UUID jobId, JobUpdater updater) {
        synchronized (jobs) {
            ModelInvocationJobRecord current = jobs.get(jobId);
            if (current != null) {
                jobs.put(jobId, updater.update(current));
            }
        }
    }

    private boolean terminal(ModelInvocationJobStatus status) {
        return status == ModelInvocationJobStatus.SUCCEEDED
                || status == ModelInvocationJobStatus.FAILED
                || status == ModelInvocationJobStatus.CANCELLED;
    }

    private interface JobUpdater {
        ModelInvocationJobRecord update(ModelInvocationJobRecord job);
    }
}
