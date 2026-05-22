package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.api.response.ModelInvocationJobResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
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

    private final ModelAccessService modelAccessService;
    private final ModelAccessProperties properties;
    private final ScheduledThreadPoolExecutor executor;
    private final Map<UUID, JobState> jobs = new ConcurrentHashMap<>();

    public ModelInvocationJobService(
            ModelAccessService modelAccessService,
            ModelAccessProperties properties
    ) {
        this.modelAccessService = modelAccessService;
        this.properties = properties;
        this.executor = new ScheduledThreadPoolExecutor(
                properties.safeAsyncJobWorkerThreads(),
                new ModelInvocationJobThreadFactory()
        );
        this.executor.setRemoveOnCancelPolicy(true);
    }

    public ModelInvocationJobResponse submit(InvokeModelRequest request, ServicePrincipal principal) {
        UUID jobId = UUID.randomUUID();
        JobState job = new JobState(
                jobId,
                request,
                principal,
                TraceContext.getTraceId(),
                Instant.now()
        );
        jobs.put(jobId, job);
        ScheduledFuture<?> future = executor.schedule(
                () -> run(job),
                properties.safeAsyncJobDispatchDelayMs(),
                TimeUnit.MILLISECONDS
        );
        job.setFuture(future);
        return job.snapshot();
    }

    public ModelInvocationJobResponse get(UUID jobId) {
        return job(jobId).snapshot();
    }

    public ModelInvocationJobResponse cancel(UUID jobId) {
        return job(jobId).cancel();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private JobState job(UUID jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "异步模型调用任务不存在");
        }
        return job;
    }

    private void run(JobState job) {
        if (!job.markRunning()) {
            return;
        }
        try {
            TraceContext.setTraceId(job.traceId());
            MDC.put(TraceContext.MDC_TRACE_ID, job.traceId());
            InvokeModelResponse response = modelAccessService.invoke(job.request(), job.principal());
            job.markSucceeded(response);
        } catch (RuntimeException exception) {
            job.markFailed(errorCode(exception), exception.getMessage());
        } finally {
            MDC.remove(TraceContext.MDC_TRACE_ID);
            TraceContext.clear();
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }

    private static final class JobState {

        private final UUID jobId;
        private final InvokeModelRequest request;
        private final ServicePrincipal principal;
        private final String traceId;
        private final Instant createdAt;
        private ScheduledFuture<?> future;
        private ModelInvocationJobStatus status = ModelInvocationJobStatus.QUEUED;
        private Instant startedAt;
        private Instant finishedAt;
        private InvokeModelResponse response;
        private String errorCode;
        private String errorMessage;

        private JobState(
                UUID jobId,
                InvokeModelRequest request,
                ServicePrincipal principal,
                String traceId,
                Instant createdAt
        ) {
            this.jobId = jobId;
            this.request = request;
            this.principal = principal;
            this.traceId = traceId;
            this.createdAt = createdAt;
        }

        private InvokeModelRequest request() {
            return request;
        }

        private ServicePrincipal principal() {
            return principal;
        }

        private String traceId() {
            return traceId;
        }

        private synchronized void setFuture(ScheduledFuture<?> future) {
            this.future = future;
            if (status == ModelInvocationJobStatus.CANCELLED) {
                future.cancel(true);
            }
        }

        private synchronized boolean markRunning() {
            if (status != ModelInvocationJobStatus.QUEUED) {
                return false;
            }
            status = ModelInvocationJobStatus.RUNNING;
            startedAt = Instant.now();
            return true;
        }

        private synchronized void markSucceeded(InvokeModelResponse response) {
            this.status = ModelInvocationJobStatus.SUCCEEDED;
            this.finishedAt = Instant.now();
            this.response = response;
            this.errorCode = null;
            this.errorMessage = null;
        }

        private synchronized void markFailed(String errorCode, String errorMessage) {
            this.status = ModelInvocationJobStatus.FAILED;
            this.finishedAt = Instant.now();
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        private synchronized ModelInvocationJobResponse cancel() {
            if (terminal()) {
                return snapshot();
            }
            if (future != null) {
                future.cancel(true);
            }
            if (status == ModelInvocationJobStatus.QUEUED) {
                status = ModelInvocationJobStatus.CANCELLED;
                finishedAt = Instant.now();
                errorCode = CANCELLED_CODE;
                errorMessage = "异步模型调用已取消";
            } else {
                errorCode = CANCEL_REQUESTED_CODE;
                errorMessage = "异步模型调用取消请求已发送";
            }
            return snapshot();
        }

        private boolean terminal() {
            return status == ModelInvocationJobStatus.SUCCEEDED
                    || status == ModelInvocationJobStatus.FAILED
                    || status == ModelInvocationJobStatus.CANCELLED;
        }

        private synchronized ModelInvocationJobResponse snapshot() {
            return new ModelInvocationJobResponse(
                    jobId,
                    status,
                    createdAt,
                    startedAt,
                    finishedAt,
                    response == null ? null : response.invocationId(),
                    errorCode,
                    errorMessage,
                    traceId,
                    response
            );
        }
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
