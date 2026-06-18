package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventProperties;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.event.ModelInvocationJobRequestedEvent;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobResult;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ModelInvocationJobService {

    private static final String CANCELLED_CODE = "CANCELLED";
    private static final String CANCEL_REQUESTED_CODE = "CANCEL_REQUESTED";
    private static final String WORKER_RESTARTED_CODE = "WORKER_RESTARTED";

    private final ModelAccessProperties properties;
    private final ModelInvocationJobRepository repository;
    private final ObjectMapper objectMapper;
    private final PlatformEventPublisher eventPublisher;
    private final PlatformEventProperties eventProperties;
    private final AsyncTaskNotificationService notificationService;

    public ModelInvocationJobService(
            ModelAccessProperties properties,
            ModelInvocationJobRepository repository,
            ObjectMapper objectMapper,
            PlatformEventPublisher eventPublisher,
            PlatformEventProperties eventProperties,
            AsyncTaskNotificationService notificationService
    ) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.eventProperties = eventProperties;
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void recoverPersistedJobs() {
        Instant now = Instant.now();
        Instant staleBefore = now.minusMillis(properties.safeAsyncJobRunningTimeoutMs());
        List<ModelInvocationJobRecord> staleRunningJobs = repository.runningJobsStaleBefore(staleBefore);
        repository.markRunningJobsFailed(
                now,
                staleBefore,
                WORKER_RESTARTED_CODE,
                "超过恢复保护窗口的运行中异步模型调用已标记失败，可重新提交"
        );
        notifyRecoveredJobs(staleRunningJobs);
        repository.queuedJobs().forEach(this::publishJobRequested);
    }

    /**
     * Persists the command first, then emits an event so async execution can move between local and Kafka transports.
     */
    public ModelInvocationJobResult submit(ModelInvocationCommand request, ServicePrincipal principal) {
        UUID jobId = UUID.randomUUID();
        ModelInvocationJobRecord job = new ModelInvocationJobRecord(
                jobId,
                ModelInvocationJobStatus.QUEUED,
                json(request),
                principal.callerService(),
                principal.delegatedUserId(),
                principalRoles(principal),
                TraceContext.getOrCreateTraceId(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null
        );
        repository.save(job);
        publishJobRequested(job);
        return toResult(job);
    }

    public ModelInvocationJobResult get(UUID jobId) {
        return toResult(job(jobId));
    }

    public ModelInvocationJobResult cancel(UUID jobId) {
        ModelInvocationJobRecord current = job(jobId);
        if (terminal(current.status())) {
            return toResult(current);
        }

        if (repository.cancelQueued(jobId, Instant.now(), CANCELLED_CODE, "异步模型调用已取消")) {
            ModelInvocationJobRecord canceled = job(jobId);
            notificationService.notifyModelInvocationJobCancelled(canceled);
            return toResult(canceled);
        }
        repository.markCancelRequested(jobId, CANCEL_REQUESTED_CODE, "异步模型调用取消请求已发送");
        return get(jobId);
    }

    private ModelInvocationJobRecord job(UUID jobId) {
        return repository.job(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "异步模型调用任务不存在"));
    }

    private void publishJobRequested(ModelInvocationJobRecord job) {
        PlatformEventEnvelope event = PlatformEventEnvelope.of(
                ModelInvocationJobRequestedEvent.EVENT_TYPE,
                job.jobId().toString(),
                new ModelInvocationJobRequestedEvent(job.jobId()),
                objectMapper
        );
        eventPublisher.publish(
                eventProperties.modelInvocationJobRequestedTopic(),
                event,
                Duration.ofMillis(properties.safeAsyncJobDispatchDelayMs())
        );
    }

    private void notifyRecoveredJobs(List<ModelInvocationJobRecord> staleRunningJobs) {
        staleRunningJobs.stream()
                .map(ModelInvocationJobRecord::jobId)
                .map(repository::job)
                .flatMap(Optional::stream)
                .filter(job -> job.status() == ModelInvocationJobStatus.FAILED)
                .forEach(notificationService::notifyModelInvocationJobFailed);
    }

    private ModelInvocationJobResult toResult(ModelInvocationJobRecord job) {
        return new ModelInvocationJobResult(
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

    private ModelInvocationResult response(ModelInvocationJobRecord job) {
        if (job.responseJson() == null || job.responseJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(job.responseJson(), ModelInvocationResult.class);
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

    private String principalRoles(ServicePrincipal principal) {
        String roles = principal.roles()
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return roles.isBlank() ? null : roles;
    }

    private boolean terminal(ModelInvocationJobStatus status) {
        return status == ModelInvocationJobStatus.SUCCEEDED
                || status == ModelInvocationJobStatus.FAILED
                || status == ModelInvocationJobStatus.CANCELLED;
    }
}
