package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.api.response.ModelInvocationJobResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ModelInvocationJobService {

    private static final String CANCELLED_CODE = "CANCELLED";
    private static final String CANCEL_REQUESTED_CODE = "CANCEL_REQUESTED";
    private static final String WORKER_RESTARTED_CODE = "WORKER_RESTARTED";

    private final ModelAccessService modelAccessService;
    private final ModelAccessProperties properties;
    private final ModelInvocationJobRepository repository;
    private final ObjectMapper objectMapper;
    private final ScheduledThreadPoolExecutor executor;
    private final Map<UUID, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public ModelInvocationJobService(
            ModelAccessService modelAccessService,
            ModelAccessProperties properties,
            ModelInvocationJobRepository repository,
            ObjectMapper objectMapper
    ) {
        this.modelAccessService = modelAccessService;
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.executor = new ScheduledThreadPoolExecutor(
                properties.safeAsyncJobWorkerThreads(),
                new ModelInvocationJobThreadFactory()
        );
        this.executor.setRemoveOnCancelPolicy(true);
    }

    @PostConstruct
    public void recoverPersistedJobs() {
        repository.markRunningJobsFailed(
                Instant.now(),
                WORKER_RESTARTED_CODE,
                "服务重启后运行中的异步模型调用已标记失败，可重新提交"
        );
        repository.queuedJobs().forEach(this::schedule);
    }

    public ModelInvocationJobResponse submit(InvokeModelRequest request, ServicePrincipal principal) {
        UUID jobId = UUID.randomUUID();
        ModelInvocationJobRecord job = new ModelInvocationJobRecord(
                jobId,
                ModelInvocationJobStatus.QUEUED,
                json(request),
                principal.callerService(),
                principal.delegatedUserId(),
                TraceContext.getTraceId(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null
        );
        repository.save(job);
        schedule(job);
        return toResponse(job);
    }

    public ModelInvocationJobResponse get(UUID jobId) {
        return toResponse(job(jobId));
    }

    public ModelInvocationJobResponse cancel(UUID jobId) {
        ModelInvocationJobRecord current = job(jobId);
        if (terminal(current.status())) {
            return toResponse(current);
        }

        ScheduledFuture<?> future = futures.get(jobId);
        if (future != null) {
            future.cancel(true);
        }

        if (repository.cancelQueued(jobId, Instant.now(), CANCELLED_CODE, "异步模型调用已取消")) {
            return get(jobId);
        }
        repository.markCancelRequested(jobId, CANCEL_REQUESTED_CODE, "异步模型调用取消请求已发送");
        return get(jobId);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private ModelInvocationJobRecord job(UUID jobId) {
        return repository.job(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "异步模型调用任务不存在"));
    }

    private void schedule(ModelInvocationJobRecord job) {
        ScheduledFuture<?> future = executor.schedule(
                () -> run(job.jobId()),
                properties.safeAsyncJobDispatchDelayMs(),
                TimeUnit.MILLISECONDS
        );
        futures.put(job.jobId(), future);
    }

    private void run(UUID jobId) {
        if (!repository.markRunning(jobId, Instant.now())) {
            futures.remove(jobId);
            return;
        }

        ModelInvocationJobRecord job = job(jobId);
        try {
            TraceContext.setTraceId(job.traceId());
            MDC.put(TraceContext.MDC_TRACE_ID, job.traceId());
            InvokeModelResponse response = modelAccessService.invoke(request(job), principal(job));
            repository.markSucceeded(jobId, Instant.now(), response, json(response));
        } catch (RuntimeException exception) {
            repository.markFailed(jobId, Instant.now(), errorCode(exception), exception.getMessage());
        } finally {
            futures.remove(jobId);
            MDC.remove(TraceContext.MDC_TRACE_ID);
            TraceContext.clear();
        }
    }

    private InvokeModelRequest request(ModelInvocationJobRecord job) {
        try {
            return objectMapper.readValue(job.requestJson(), InvokeModelRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "异步模型调用请求载荷无法解析");
        }
    }

    private ServicePrincipal principal(ModelInvocationJobRecord job) {
        return new ServicePrincipal(job.actorService(), job.delegatedUserId());
    }

    private ModelInvocationJobResponse toResponse(ModelInvocationJobRecord job) {
        return new ModelInvocationJobResponse(
                job.jobId(),
                job.status(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.invocationId(),
                job.errorCode(),
                job.errorMessage(),
                job.traceId(),
                response(job)
        );
    }

    private InvokeModelResponse response(ModelInvocationJobRecord job) {
        if (job.responseJson() == null || job.responseJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(job.responseJson(), InvokeModelResponse.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "异步模型调用结果载荷无法解析");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "异步模型调用载荷无法序列化");
        }
    }

    private boolean terminal(ModelInvocationJobStatus status) {
        return status == ModelInvocationJobStatus.SUCCEEDED
                || status == ModelInvocationJobStatus.FAILED
                || status == ModelInvocationJobStatus.CANCELLED;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }

    private static final class ModelInvocationJobThreadFactory implements ThreadFactory {

        private int index = 0;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "wp2-model-invocation-job-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
