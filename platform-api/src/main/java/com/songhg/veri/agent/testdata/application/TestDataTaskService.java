package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.command.RetryTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskPageRequest;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskQuery;
import com.songhg.veri.agent.testdata.application.view.TestDataTaskResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDataTaskService {

    private static final Pattern REQUEST_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:@/-]{1,128}$");
    private static final Set<String> TASK_TYPES = Set.of("PREPARE", "REFRESH", "CLEANUP", "ROLLBACK");
    private static final Set<String> TASK_STATUSES = Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELED");
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "secret", "token", "password", "passwd", "cookie", "credential", "apikey", "api_key"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final TestDataRepository repository;
    private final TestDataPlatformContextClient contextClient;
    private final TestDataActorResolver actorResolver;
    private final TestDataProperties properties;
    private final ObjectMapper objectMapper;

    public TestDataTaskService(
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient,
            TestDataActorResolver actorResolver,
            TestDataProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataTaskResponse createTask(CreateTestDataTaskCommand command) {
        assertEnabled();
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        String requestKey = boundedRequestKey(command.requestKey());
        // DB profile serializes all writers of the same requestKey before checking or changing unique ownership.
        repository.lockDataTaskRequestKey(projectId, requestKey);
        var existing = repository.dataTaskByProjectAndRequestKey(projectId, requestKey);
        if (existing.isPresent()) {
            return response(assertSameCreateTaskRequest(existing.get(), command));
        }
        UUID dataSetId = command.dataSetId();
        if (dataSetId != null) {
            TestDataSet dataSet = requireDataSet(dataSetId);
            if (!projectId.equals(dataSet.projectId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "清理任务数据集不属于当前项目");
            }
        }
        String resultSummaryJson = safeSummaryJson(command.resultSummary());
        Instant now = Instant.now();
        TestDataTask task = new TestDataTask(
                UUID.randomUUID(),
                projectId,
                dataSetId,
                normalizeTaskType(command.taskType()),
                "PENDING",
                requestKey,
                boundedNullable(command.targetRef(), 256),
                1,
                resultSummaryJson,
                null,
                null,
                TraceContext.getOrCreateTraceId(),
                actorResolver.currentActor(),
                null,
                null,
                now,
                now
        );
        if (!repository.insertDataTaskIfAbsent(task)) {
            return response(repository.dataTaskByProjectAndRequestKey(projectId, requestKey)
                    .map(duplicated -> assertSameCreateTaskRequest(duplicated, command))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "清理任务 requestKey 已存在")));
        }
        auditTask(task, "test_data.cleanup.requested", Map.of(
                "taskType", task.taskType(),
                "status", task.status(),
                "destructiveCleanupTriggered", false
        ));
        return response(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TestDataTaskResponse> tasks(TestDataTaskPageRequest request) {
        assertEnabled();
        TestDataTaskQuery query = normalizeQuery(request.toQuery());
        var items = repository.dataTasks(query).stream().map(this::response).toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countDataTasks(query));
    }

    @Transactional(readOnly = true)
    public TestDataTaskResponse task(UUID id) {
        assertEnabled();
        return response(requireTask(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataTaskResponse retryTask(UUID id, RetryTestDataTaskCommand command) {
        assertEnabled();
        TestDataTask existing = requireTask(id);
        if (!"FAILED".equals(existing.status()) && !"CANCELED".equals(existing.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有失败或取消的任务可重试");
        }
        Instant now = Instant.now();
        String nextRequestKey = StringUtils.hasText(command.requestKey())
                ? boundedRequestKey(command.requestKey()) : existing.requestKey();
        // DB profile uses a transaction-scoped advisory lock so concurrent writers return a stable conflict.
        repository.lockDataTaskRequestKey(existing.projectId(), nextRequestKey);
        TestDataTask retried = new TestDataTask(
                existing.id(),
                existing.projectId(),
                existing.dataSetId(),
                existing.taskType(),
                "PENDING",
                nextRequestKey,
                existing.targetRef(),
                existing.attempt() + 1,
                safeSummaryJson(command.resultSummary()),
                null,
                null,
                TraceContext.getOrCreateTraceId(),
                existing.createdBy(),
                null,
                null,
                existing.createdAt(),
                now
        );
        if (!repository.retryDataTaskIfCurrentAttempt(retried, existing.attempt())) {
            TestDataTask current = requireTask(id);
            if (!"FAILED".equals(current.status()) && !"CANCELED".equals(current.status())) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "清理任务状态已变更，不能重复重试");
            }
            throw new BusinessException(ErrorCode.CONFLICT, "清理任务 requestKey 已存在");
        }
        auditTask(retried, "test_data.cleanup.retried", Map.of(
                "attempt", retried.attempt(),
                "status", retried.status(),
                "destructiveCleanupTriggered", false
        ));
        return response(retried);
    }

    public String dataTaskProjectScopeId(UUID id) {
        return repository.dataTaskProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据任务不存在"));
    }

    private TestDataTaskQuery normalizeQuery(TestDataTaskQuery query) {
        return new TestDataTaskQuery(
                query.projectId() == null ? null : contextClient.projectContext(query.projectId()).resourceId(),
                query.dataSetId(),
                query.taskType() == null ? null : normalizeTaskType(query.taskType()),
                query.status() == null ? null : normalizeTaskStatus(query.status()),
                query.offset(),
                query.limit()
        );
    }

    private TestDataSet requireDataSet(UUID id) {
        return repository.dataSet(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据集不存在"));
    }

    private TestDataTask requireTask(UUID id) {
        return repository.dataTask(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据任务不存在"));
    }

    private TestDataTask assertSameCreateTaskRequest(TestDataTask existing, CreateTestDataTaskCommand command) {
        String taskType = normalizeTaskType(command.taskType());
        String targetRef = boundedNullable(command.targetRef(), 256);
        String resultSummaryJson = safeSummaryJson(command.resultSummary());
        if (!taskType.equals(existing.taskType())
                || !sameUuid(existing.dataSetId(), command.dataSetId())
                || !sameText(existing.targetRef(), targetRef)
                || !sameText(existing.resultSummaryJson(), resultSummaryJson)) {
            throw new BusinessException(ErrorCode.CONFLICT, "清理任务 requestKey 已被不同请求占用");
        }
        return existing;
    }

    private TestDataTaskResponse response(TestDataTask task) {
        return new TestDataTaskResponse(
                task.id(),
                task.projectId(),
                task.dataSetId(),
                task.taskType(),
                task.status(),
                task.requestKey(),
                task.targetRef(),
                task.attempt(),
                readMap(task.resultSummaryJson()),
                task.errorCode(),
                task.errorSummary(),
                task.traceId(),
                policy(),
                task.startedAt(),
                task.finishedAt(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private Map<String, Object> policy() {
        return Map.of(
                "cleanupEnabled", properties.cleanupEnabled(),
                "destructiveCleanupTriggered", false,
                "workerReady", false,
                "rawRecordPayloadStored", false
        );
    }

    private void auditTask(TestDataTask task, String action, Map<String, Object> afterJson) {
        contextClient.writeAuditEvent(action, "TEST_DATA_TASK", task.id().toString(), task.projectId(), "SUCCESS", afterJson);
    }

    private String normalizeTaskType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
        if (!TASK_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试数据任务类型非法");
        }
        return normalized;
    }

    private String normalizeTaskStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试数据任务状态非法");
        }
        return normalized;
    }

    private String boundedRequestKey(String value) {
        String key = boundedText(value, 128);
        if (!REQUEST_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "任务 requestKey 格式非法");
        }
        return key;
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文本长度超过上限");
        }
        return trimmed;
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必填文本不能为空");
        }
        return boundedNullable(value, maxLength);
    }

    private String safeSummaryJson(Map<String, Object> value) {
        Map<String, Object> summary = value == null ? Map.of() : value;
        assertNoSensitiveSummary(summary);
        String json = json(summary);
        if (json.getBytes(StandardCharsets.UTF_8).length > properties.effectiveRecordSummaryMaxBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "清理任务摘要超过上限");
        }
        return json;
    }

    private void assertNoSensitiveSummary(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nestedValue) -> {
                String keyText = String.valueOf(key).toLowerCase(Locale.ROOT);
                if (SENSITIVE_KEYWORDS.stream().anyMatch(keyText::contains)) {
                    throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "清理任务摘要包含敏感字段");
                }
                assertNoSensitiveSummary(nestedValue);
            });
            return;
        }
        if (value instanceof Iterable<?> values) {
            values.forEach(this::assertNoSensitiveSummary);
            return;
        }
        if (value instanceof String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.contains("secret://")
                    || normalized.contains("bearer ")
                    || normalized.contains("token=")
                    || normalized.contains("password=")
                    || normalized.contains("cookie=")) {
                throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "清理任务摘要包含敏感内容");
            }
        }
    }

    private boolean sameUuid(UUID left, UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP8 测试数据控制面已关闭");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 无法序列化");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "测试数据任务 JSON 读取失败");
        }
    }
}
