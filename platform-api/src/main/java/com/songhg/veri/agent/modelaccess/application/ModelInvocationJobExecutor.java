package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModelInvocationJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(ModelInvocationJobExecutor.class);

    private final ModelInvocationService invocationService;
    private final ModelInvocationJobRepository repository;
    private final ObjectMapper objectMapper;

    public ModelInvocationJobExecutor(
            ModelInvocationService invocationService,
            ModelInvocationJobRepository repository,
            ObjectMapper objectMapper
    ) {
        this.invocationService = invocationService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes one queued job. The status transition is conditional, making duplicate event delivery idempotent.
     */
    public void execute(UUID jobId) {
        if (!repository.markRunning(jobId, Instant.now())) {
            log.info("Skip async model invocation job because it is no longer queued, job_id={}", jobId);
            return;
        }

        ModelInvocationJobRecord job = repository.job(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "异步模型调用任务不存在"));
        try (TraceContext.TraceScope ignored = TraceContext.open(job.traceId())) {
            ModelInvocationResult response = invocationService.invoke(request(job), principal(job));
            repository.markSucceeded(jobId, Instant.now(), response, json(response));
            log.info("Async model invocation job succeeded, job_id={}, invocation_id={}", jobId, response.invocationId());
        } catch (RuntimeException exception) {
            repository.markFailed(jobId, Instant.now(), errorCode(exception), exception.getMessage());
            log.warn("Async model invocation job failed, job_id={}, error_code={}", jobId, errorCode(exception), exception);
        }
    }

    private ModelInvocationCommand request(ModelInvocationJobRecord job) {
        try {
            return objectMapper.readValue(job.requestJson(), ModelInvocationCommand.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "异步模型调用请求载荷无法解析");
        }
    }

    private ServicePrincipal principal(ModelInvocationJobRecord job) {
        return new ServicePrincipal(job.actorService(), job.delegatedUserId(), roles(job.principalRoles()));
    }

    private List<String> roles(String principalRoles) {
        if (principalRoles == null || principalRoles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(principalRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "异步模型调用载荷无法序列化");
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }
}
