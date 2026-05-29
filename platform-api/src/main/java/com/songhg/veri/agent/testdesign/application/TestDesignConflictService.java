package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.ResolveTestDesignConflictBatchCommand;
import com.songhg.veri.agent.testdesign.application.command.ResolveTestDesignConflictCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignConflictBatchResolveItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignConflictBatchResolveResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignConflictService {

    private static final String ACTION_MANUAL_LINK_EXISTING = "MANUAL_LINK_EXISTING";
    private final TestDesignRepository repository;
    private final AssetService assetService;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignConflictService(
            TestDesignRepository repository,
            AssetService assetService,
            TestDesignActorResolver actorResolver,
            TestDesignResponseMapper responseMapper,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.assetService = assetService;
        this.actorResolver = actorResolver;
        this.responseMapper = responseMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private TestDesignTaskDetailResponse task(UUID id) {
        TestDesignTask task = taskOrThrow(id);
        List<TestDesignCandidate> candidates = repository.candidatesByTask(id);
        Map<UUID, TestDesignCandidate> candidateById = candidateById(candidates);
        List<TestDesignPublishRecordResponse> records = repository.publishRecords(id).stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidateById.get(record.candidateId())))
                .toList();
        return new TestDesignTaskDetailResponse(
                responseMapper.toTaskResponse(task),
                candidates.stream().map(responseMapper::toCandidateResponse).toList(),
                records
        );
    }

    private PageResponse<TestDesignPublishRecordResponse> publishRecords(UUID taskId) {
        taskOrThrow(taskId);
        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(taskId));
        List<TestDesignPublishRecordResponse> records = repository.publishRecords(taskId).stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidates.get(record.candidateId())))
                .toList();
        return PageResponse.of(records, 0, Math.max(1, records.size()), records.size());
    }

    /**
     * Resolves a publish conflict by explicitly linking one reviewed candidate to an existing WP3 test case.
     *
     * <p>The action is intentionally separate from normal publish. High-similarity conflicts stay blocked until a
     * reviewer with publish permission selects the target case, and the service still validates project scope,
     * requirement traceability and candidate version before mutating WP5 state.
     */
    @Transactional
    public TestDesignPublishRecordResponse resolveConflict(UUID candidateId, ResolveTestDesignConflictCommand command) {
        return resolveConflictInternal(candidateId, command);
    }

    /**
     * Resolves multiple publish conflicts with the same per-candidate validation as the single-item endpoint.
     *
     * <p>The method intentionally returns item-level outcomes instead of failing the whole batch on the first stale
     * version or invalid target. Operators can then retry only failed candidates while successful links keep their
     * audit records and WP3 trace links.
     */
    @Transactional
    public TestDesignConflictBatchResolveResponse batchResolveConflicts(ResolveTestDesignConflictBatchCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量冲突处理项不能为空");
        }
        if (command.items().size() > batchActionLimit()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量冲突处理最多支持 " + batchActionLimit() + " 项");
        }

        List<TestDesignConflictBatchResolveItemResponse> items = new ArrayList<>();
        for (ResolveTestDesignConflictBatchCommand.Item item : command.items()) {
            if (item == null || item.candidateId() == null || item.version() == null || item.caseId() == null) {
                items.add(new TestDesignConflictBatchResolveItemResponse(
                        item == null ? null : item.candidateId(),
                        "FAILED",
                        null,
                        ErrorCode.VALIDATION_ERROR.name(),
                        "批量冲突处理项必须包含 candidateId、version 和 caseId"
                ));
                continue;
            }
            try {
                ResolveTestDesignConflictCommand itemCommand = new ResolveTestDesignConflictCommand(
                        item.version(),
                        item.caseId(),
                        command.reason(),
                        command.comment()
                );
                TestDesignPublishRecordResponse record = resolveConflictInternal(item.candidateId(), itemCommand);
                String result = "SUCCEEDED".equals(record.result()) ? "SUCCEEDED" : "FAILED";
                items.add(new TestDesignConflictBatchResolveItemResponse(
                        item.candidateId(),
                        result,
                        record,
                        null,
                        record.errorMessage()
                ));
            } catch (BusinessException exception) {
                items.add(new TestDesignConflictBatchResolveItemResponse(
                        item == null ? null : item.candidateId(),
                        "FAILED",
                        null,
                        exception.getErrorCode().name(),
                        exception.getMessage()
                ));
            }
        }
        long succeeded = items.stream().filter(item -> "SUCCEEDED".equals(item.result())).count();
        return new TestDesignConflictBatchResolveResponse(
                ACTION_MANUAL_LINK_EXISTING,
                items.size(),
                Math.toIntExact(succeeded),
                items.size() - Math.toIntExact(succeeded),
                items
        );
    }

    private TestDesignPublishRecordResponse resolveConflictInternal(UUID candidateId, ResolveTestDesignConflictCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "冲突处理请求不能为空");
        }
        TestDesignCandidate candidate = candidateOrThrow(candidateId);
        TestDesignTask task = taskOrThrow(candidate.taskId());
        assertVersion(candidate, command.version(), true);
        if (!isPublishableCandidate(candidate)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有已确认或发布失败的候选用例可处理发布冲突");
        }
        TestCaseResponse testCase = assetService.getTestCase(command.caseId());
        ensureResolvableConflictTarget(candidate, testCase);
        try {
            ensureTraceLink(candidate, testCase);
            TestDesignCandidate linked = withPublishedCandidate(candidate, testCase.id(), null);
            repository.saveCandidate(linked);
            String resolutionComment = manualConflictResolutionMessage(command);
            saveReviewRecord(candidate, linked, "RESOLVE_CONFLICT", resolutionComment);
            TestDesignPublishRecord record = publishRecord(
                    task,
                    linked,
                    false,
                    ACTION_MANUAL_LINK_EXISTING,
                    "SUCCEEDED",
                    testCase.id(),
                    null,
                    actorResolver.currentActor()
            );
            repository.savePublishRecord(record);
            refreshTaskCountsAfterPublish(task.id(), task.status());
            return responseMapper.toPublishRecordResponse(record, linked);
        } catch (BusinessException exception) {
            TestDesignCandidate failed = withFailedCandidate(candidate, testCase.id(), exception.getMessage());
            repository.saveCandidate(failed);
            TestDesignPublishRecord record = publishRecord(
                    task,
                    failed,
                    false,
                    ACTION_MANUAL_LINK_EXISTING,
                    "FAILED",
                    testCase.id(),
                    exception.getMessage(),
                    actorResolver.currentActor()
            );
            repository.savePublishRecord(record);
            refreshTaskCountsAfterPublish(task.id(), task.status());
            return responseMapper.toPublishRecordResponse(record, failed);
        }
    }

    private static boolean isPublishableCandidate(TestDesignCandidate candidate) {
        return TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())
                || TestDesignCandidateStatus.FAILED.name().equals(candidate.status());
    }

    /**
     * Completes the WP3 trace-link side effect for both first-time publishes and replayed partial publishes.
     *
     * <p>WP5 and WP3 do not share a single transaction boundary. If a previous attempt created the test case and failed
     * before linking it back to the requirement, publish retry must repair the missing link before marking the candidate
     * as published.
     */
    private void ensureTraceLink(TestDesignCandidate candidate, TestCaseResponse testCase) {
        assetService.createLink(new CreateLinkRequest(
                candidate.requirementId(),
                candidate.apiId(),
                null,
                null,
                testCase.id()
        ));
    }

    private void ensureResolvableConflictTarget(TestDesignCandidate candidate, TestCaseResponse testCase) {
        if (testCase == null || testCase.id() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在");
        }
        if (!Objects.equals(candidate.projectId(), testCase.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试用例不属于候选项目: " + testCase.id());
        }
        boolean linkedToCandidateRequirement = assetService.findActiveTestCasesByRequirement(
                        candidate.projectId(),
                        candidate.requirementId()
                ).stream()
                .anyMatch(existing -> Objects.equals(existing.id(), testCase.id()));
        if (!linkedToCandidateRequirement) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试用例未关联候选需求: " + testCase.id());
        }
    }

    private static String manualConflictResolutionMessage(ResolveTestDesignConflictCommand command) {
        String reason = trimToNull(command.reason());
        String comment = trimToNull(command.comment());
        if (reason == null && comment == null) {
            return "人工确认链接既有测试用例";
        }
        if (reason == null) {
            return "人工确认链接既有测试用例: " + comment;
        }
        if (comment == null) {
            return "人工确认链接既有测试用例: " + reason;
        }
        return "人工确认链接既有测试用例: " + reason + "；" + comment;
    }

    private TestDesignPublishRecord publishRecord(
            TestDesignTask task,
            TestDesignCandidate candidate,
            boolean dryRun,
            String action,
            String result,
            UUID assetCaseId,
            String errorMessage,
            String actor
    ) {
        return new TestDesignPublishRecord(
                UUID.randomUUID(),
                task.id(),
                candidate.id(),
                candidate.projectId(),
                candidate.requirementId(),
                assetCaseId,
                dryRun,
                action,
                result,
                errorMessage,
                actor,
                Instant.now()
        );
    }

    private TestDesignTask withTaskCounts(TestDesignTask task, List<TestDesignCandidate> candidates) {
        int generatedCount = candidates.size();
        int confirmedCount = Math.toIntExact(candidates.stream()
                .filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status()))
                .count());
        int publishedCount = Math.toIntExact(candidates.stream()
                .filter(candidate -> TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status()))
                .count());
        String status = publishedCount > 0 && publishedCount == generatedCount
                ? TestDesignTaskStatus.PUBLISHED.name()
                : task.status();
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), status, task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), task.modelInvocationId(), task.modelProviderName(),
                task.modelName(), task.totalRequirements(), generatedCount, confirmedCount, publishedCount,
                task.errorMessage(), task.requestedBy(), task.idempotencyKey(), task.requestDigest(),
                task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    /**
     * Publish conflicts or partial publish results must not leave the task stuck in the transient PUBLISHING state.
     */
    private void refreshTaskCountsAfterPublish(UUID taskId, String fallbackStatus) {
        TestDesignTask task = taskOrThrow(taskId);
        TestDesignTask counted = withTaskCounts(task, repository.candidatesByTask(taskId));
        if (!TestDesignTaskStatus.PUBLISHING.name().equals(counted.status())) {
            repository.saveTask(counted);
            return;
        }
        repository.saveTask(new TestDesignTask(
                counted.id(), counted.projectId(), counted.title(), fallbackStatus, counted.requirementIds(),
                counted.coverageTypes(), counted.promptKey(), counted.promptVersion(), counted.modelInvocationId(),
                counted.modelProviderName(), counted.modelName(), counted.totalRequirements(), counted.generatedCount(),
                counted.confirmedCount(), counted.publishedCount(), counted.errorMessage(), counted.requestedBy(),
                counted.idempotencyKey(), counted.requestDigest(), counted.inputDigest(), counted.contextSummaryJson(),
                counted.createdAt(), Instant.now()
        ));
    }

    private void saveReviewRecord(TestDesignCandidate before, TestDesignCandidate after, String action, String comment) {
        repository.saveReviewRecord(new TestDesignReviewRecord(
                UUID.randomUUID(),
                after.id(),
                after.taskId(),
                after.projectId(),
                action,
                before.status(),
                after.status(),
                actorResolver.currentActor(),
                trimToNull(comment),
                reviewDiff(before, after),
                Instant.now()
        ));
    }

    private String reviewDiff(TestDesignCandidate before, TestDesignCandidate after) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "changedFields", reviewChangedFields(before, after),
                    "status", Map.of("before", before.status(), "after", after.status()),
                    "version", Map.of("before", before.version(), "after", after.version())
            ));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private static List<String> reviewChangedFields(TestDesignCandidate before, TestDesignCandidate after) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(before.title(), after.title())) {
            fields.add("title");
        }
        if (!Objects.equals(before.apiId(), after.apiId())) {
            fields.add("apiId");
        }
        if (!Objects.equals(before.coverageType(), after.coverageType())) {
            fields.add("coverageType");
        }
        if (!Objects.equals(before.priority(), after.priority())) {
            fields.add("priority");
        }
        if (!Objects.equals(before.description(), after.description())) {
            fields.add("description");
        }
        if (!Objects.equals(before.preconditions(), after.preconditions())) {
            fields.add("preconditions");
        }
        if (!Objects.equals(before.stepsJson(), after.stepsJson())) {
            fields.add("steps");
        }
        if (!Objects.equals(before.expectedResult(), after.expectedResult())) {
            fields.add("expectedResult");
        }
        if (!Objects.equals(before.tags(), after.tags())) {
            fields.add("tags");
        }
        if (!Objects.equals(before.status(), after.status())) {
            fields.add("status");
        }
        if (before.version() != after.version()) {
            fields.add("version");
        }
        return fields;
    }

    private TestDesignCandidate withPublishedCandidate(TestDesignCandidate candidate, UUID assetCaseId, String errorMessage) {
        return new TestDesignCandidate(
                candidate.id(), candidate.taskId(), candidate.projectId(), candidate.requirementId(), candidate.apiId(),
                candidate.title(), candidate.description(), candidate.coverageType(), candidate.priority(),
                TestDesignCandidateStatus.PUBLISHED.name(), candidate.preconditions(), candidate.stepsJson(),
                candidate.expectedResult(), candidate.tags(), candidate.duplicateKey(), candidate.confidence(),
                candidate.promptKey(), candidate.promptVersion(), candidate.modelInvocationId(),
                candidate.modelProviderName(), candidate.modelName(), assetCaseId, candidate.reviewComment(),
                candidate.rejectedReason(), candidate.ignoredReason(), errorMessage, candidate.confirmedBy(),
                candidate.confirmedAt(), candidate.version() + 1, candidate.createdAt(), Instant.now()
        );
    }

    private TestDesignCandidate withFailedCandidate(TestDesignCandidate candidate, UUID assetCaseId, String errorMessage) {
        return new TestDesignCandidate(
                candidate.id(), candidate.taskId(), candidate.projectId(), candidate.requirementId(), candidate.apiId(),
                candidate.title(), candidate.description(), candidate.coverageType(), candidate.priority(),
                TestDesignCandidateStatus.FAILED.name(), candidate.preconditions(), candidate.stepsJson(),
                candidate.expectedResult(), candidate.tags(), candidate.duplicateKey(), candidate.confidence(),
                candidate.promptKey(), candidate.promptVersion(), candidate.modelInvocationId(),
                candidate.modelProviderName(), candidate.modelName(), assetCaseId, candidate.reviewComment(),
                candidate.rejectedReason(), candidate.ignoredReason(), errorMessage, candidate.confirmedBy(),
                candidate.confirmedAt(), candidate.version() + 1, candidate.createdAt(), Instant.now()
        );
    }

    private Map<UUID, TestDesignCandidate> candidateById(List<TestDesignCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(TestDesignCandidate::id, Function.identity()));
    }

    private String stepsJson(List<TestDesignStepResponse> steps) {
        return responseMapper.stepsJson(steps);
    }

    private TestDesignTask taskOrThrow(UUID id) {
        return repository.task(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + id));
    }

    private TestDesignCandidate candidateOrThrow(UUID id) {
        return repository.candidate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选用例不存在: " + id));
    }

    private void assertVersion(TestDesignCandidate candidate, Long version, boolean requireVersion) {
        if (version == null) {
            if (requireVersion) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选用例版本号不能为空");
            }
            return;
        }
        if (version != candidate.version()) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选用例版本已变化，请刷新后重试");
        }
    }

    private static String preconditions(RequirementResponse requirement) {
        if (StringUtils.hasText(requirement.acceptanceCriteria())) {
            return "需求验收标准已明确，测试前需准备满足业务上下文的数据";
        }
        return "需求描述已确认，测试数据和账号权限已准备";
    }

    private static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    private int batchActionLimit() {
        return properties.batchActionLimit() <= 0 ? 100 : properties.batchActionLimit();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
