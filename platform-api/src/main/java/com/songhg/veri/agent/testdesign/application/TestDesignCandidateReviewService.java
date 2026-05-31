package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.CsvEncoder;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateActionCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateBatchActionCommand;
import com.songhg.veri.agent.testdesign.application.command.UpdateTestDesignCandidateCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateBatchActionItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateBatchActionResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReviewDiffItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReviewRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignCandidateReviewService {

    private static final Set<String> CANDIDATE_PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final int CANDIDATE_EXPORT_LIMIT = 500;
    private static final int CANDIDATE_EXPORT_PAGE_SIZE = 100;
    private static final int REVIEW_RECORD_EXPORT_LIMIT = 500;
    private static final int REVIEW_RECORD_EXPORT_PAGE_SIZE = 100;
    private final TestDesignRepository repository;
    private final AssetService assetService;
    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignCandidateQualityGate qualityGate;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignCandidateReviewService(
            TestDesignRepository repository,
            AssetService assetService,
            TestDesignPlatformContextClient contextClient,
            TestDesignActorResolver actorResolver,
            TestDesignResponseMapper responseMapper,
            TestDesignCandidateQualityGate qualityGate,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.assetService = assetService;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.responseMapper = responseMapper;
        this.qualityGate = qualityGate;
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

    public PageResponse<TestDesignCandidateResponse> candidates(TestDesignCandidateQuery query) {
        validateProjectWhenProvided(query.projectId());
        List<TestDesignCandidateResponse> items = repository.candidates(query).stream()
                .map(responseMapper::toCandidateResponse)
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countCandidates(query));
    }

    /**
     * Exports a bounded, project-scoped candidate summary for reviewer handoff.
     *
     * <p>The CSV intentionally uses a whitelist of operational fields. Free-form descriptions, preconditions, step
     * bodies, expected-result text, prompt payloads and model input context stay out of the export because they may
     * contain source-document secrets or unreleased product details.
     */
    @Transactional
    public String exportCandidatesCsv(TestDesignCandidateQuery query) {
        CandidateExportScope scope = candidateExportScope(query);
        TestDesignCandidateQuery normalizedQuery = candidateExportQuery(query, scope, 0);
        long totalMatched = repository.countCandidates(normalizedQuery);
        List<TestDesignCandidate> exportedCandidates = new ArrayList<>();
        int pageIndex = 0;
        while (exportedCandidates.size() < CANDIDATE_EXPORT_LIMIT) {
            TestDesignCandidateQuery pageQuery = candidateExportQuery(query, scope, pageIndex);
            List<TestDesignCandidate> pageCandidates = repository.candidates(pageQuery);
            if (pageCandidates.isEmpty()) {
                break;
            }
            int remaining = CANDIDATE_EXPORT_LIMIT - exportedCandidates.size();
            exportedCandidates.addAll(pageCandidates.stream().limit(remaining).toList());
            if (pageCandidates.size() < CANDIDATE_EXPORT_PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }

        StringBuilder csv = new StringBuilder();
        appendCandidateExportHeader(csv);
        appendCandidateExportSummary(csv, "exportLimit", CANDIDATE_EXPORT_LIMIT, scope);
        appendCandidateExportSummary(csv, "totalMatched", totalMatched, scope);
        appendCandidateExportSummary(csv, "exportedCount", exportedCandidates.size(), scope);
        appendCandidateExportSummary(csv, "truncated", totalMatched > exportedCandidates.size(), scope);
        appendCandidateExportSummary(csv, "filters", candidateExportFilterSummary(query), scope);
        appendCandidateExportSummary(csv, "statusCounts", candidateExportCounts(exportedCandidates, TestDesignCandidate::status), scope);
        appendCandidateExportSummary(csv, "coverageCounts", candidateExportCounts(exportedCandidates, TestDesignCandidate::coverageType), scope);
        exportedCandidates.forEach(candidate -> appendCandidateExportRow(csv, candidate));

        writeAudit("EXPORT", "TEST_DESIGN_CANDIDATE", UUID.randomUUID(), scope.projectId(),
                candidateExportAuditDetails(scope, totalMatched, exportedCandidates.size()));
        return csv.toString();
    }

    @Transactional
    public TestDesignCandidateResponse updateCandidate(UUID id, UpdateTestDesignCandidateCommand command) {
        TestDesignCandidate existing = candidateOrThrow(id);
        ensureEditable(existing);
        assertVersion(existing, command.version(), true);
        validateEditableApiId(command.apiId(), existing.projectId());
        List<TestDesignStepResponse> steps = normalizeSteps(command.steps(), responseMapper.steps(existing.stepsJson()));
        String expectedResult = expectedResultForUpdate(command.expectedResult(), steps, existing.expectedResult());
        Instant now = Instant.now();
        TestDesignCandidate updated = new TestDesignCandidate(
                existing.id(),
                existing.taskId(),
                existing.projectId(),
                existing.requirementId(),
                command.apiId(),
                command.title().trim(),
                trimToNull(command.description()),
                normalizeCoverageType(command.coverageType(), existing.coverageType()),
                normalizePriority(command.priority(), existing.priority()),
                TestDesignCandidateStatus.EDITED.name(),
                trimToNull(command.preconditions()),
                stepsJson(steps),
                expectedResult,
                tagsText(command.tags()),
                duplicateKey(existing.requirementId(), normalizeCoverageType(command.coverageType(), existing.coverageType()), command.title()),
                existing.confidence(),
                existing.promptKey(),
                existing.promptVersion(),
                existing.modelInvocationId(),
                existing.modelProviderName(),
                existing.modelName(),
                existing.assetCaseId(),
                null,
                null,
                null,
                null,
                existing.confirmedBy(),
                existing.confirmedAt(),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        qualityGate.validateReviewCandidate(updated, repository.candidatesByTask(existing.taskId()));
        repository.saveCandidate(updated);
        saveReviewRecord(existing, updated, "UPDATE", null);
        refreshTaskCounts(updated.taskId());
        return responseMapper.toCandidateResponse(updated);
    }

    private void validateEditableApiId(UUID apiId, String projectId) {
        if (apiId == null) {
            return;
        }
        try {
            ApiResponseDTO api = assetService.getApi(apiId);
            /*
             * Reviewer edits can feed directly into WP3 publish requests. Validate the API link at edit time so a
             * cross-project or stale asset reference cannot sit on a confirmed candidate until publish fails.
             */
            if (!Objects.equals(api.projectId(), projectId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选关联 API 不属于当前项目");
            }
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选关联 API 不存在");
            }
            throw exception;
        }
    }

    @Transactional
    public TestDesignCandidateResponse confirmCandidate(UUID id, TestDesignCandidateActionCommand command) {
        TestDesignCandidate candidate = changeCandidateStatus(
                id,
                command == null ? null : command.version(),
                TestDesignCandidateStatus.CONFIRMED,
                null,
                null,
                command == null ? null : command.comment()
        );
        return responseMapper.toCandidateResponse(candidate);
    }

    @Transactional
    public TestDesignCandidateResponse rejectCandidate(UUID id, TestDesignCandidateActionCommand command) {
        String reason = requiredReason(command, "驳回候选用例必须填写 reason");
        TestDesignCandidate candidate = changeCandidateStatus(
                id,
                command.version(),
                TestDesignCandidateStatus.REJECTED,
                reason,
                null,
                command.comment()
        );
        return responseMapper.toCandidateResponse(candidate);
    }

    @Transactional
    public TestDesignCandidateResponse ignoreCandidate(UUID id, TestDesignCandidateActionCommand command) {
        String reason = requiredReason(command, "忽略候选用例必须填写 reason");
        TestDesignCandidate candidate = changeCandidateStatus(
                id,
                command.version(),
                TestDesignCandidateStatus.IGNORED,
                null,
                reason,
                command.comment()
        );
        return responseMapper.toCandidateResponse(candidate);
    }

    @Transactional
    public TestDesignCandidateBatchActionResponse batchCandidateAction(TestDesignCandidateBatchActionCommand command) {
        List<TestDesignCandidateBatchTarget> targets = batchTargets(command);
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选操作必须指定 candidateIds 或 candidates");
        }
        if (targets.size() > batchActionLimit()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选操作最多支持 " + batchActionLimit() + " 项");
        }
        String action = command.action().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CONFIRM", "REJECT", "IGNORE").contains(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的候选批量动作: " + command.action());
        }
        if (("REJECT".equals(action) || "IGNORE".equals(action)) && !StringUtils.hasText(command.reason())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量驳回或忽略必须填写 reason");
        }
        List<TestDesignCandidateBatchActionItemResponse> items = new ArrayList<>();
        for (TestDesignCandidateBatchTarget target : targets) {
            try {
                TestDesignCandidateActionCommand itemCommand = new TestDesignCandidateActionCommand(
                        target.version(),
                        command.reason(),
                        command.comment()
                );
                TestDesignCandidateResponse candidate = switch (action) {
                    case "CONFIRM" -> confirmCandidate(target.id(), itemCommand);
                    case "REJECT" -> rejectCandidate(target.id(), itemCommand);
                    case "IGNORE" -> ignoreCandidate(target.id(), itemCommand);
                    default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的候选批量动作: " + action);
                };
                items.add(new TestDesignCandidateBatchActionItemResponse(target.id(), "SUCCEEDED", candidate, null, null));
            } catch (BusinessException exception) {
                items.add(new TestDesignCandidateBatchActionItemResponse(
                        target.id(),
                        "FAILED",
                        null,
                        exception.getErrorCode().name(),
                        exception.getMessage()
                ));
            }
        }
        long succeeded = items.stream().filter(item -> "SUCCEEDED".equals(item.result())).count();
        return new TestDesignCandidateBatchActionResponse(
                action,
                items.size(),
                Math.toIntExact(succeeded),
                items.size() - Math.toIntExact(succeeded),
                items
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
     * Returns a paginated review trail for a task without exposing raw diff JSON or full free-form comments.
     */
    public PageResponse<TestDesignReviewRecordResponse> reviewRecords(UUID taskId, PageQuery pageQuery) {
        TestDesignTask task = taskOrThrow(taskId);
        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(taskId));
        List<TestDesignReviewRecordResponse> records = repository.reviewRecords(task.id(), pageQuery).stream()
                .map(record -> toReviewRecordResponse(record, candidates.get(record.candidateId())))
                .toList();
        return PageResponse.of(records, pageQuery.index(), pageQuery.size(), repository.countReviewRecords(task.id()));
    }

    /**
     * Exports a bounded review history CSV for audit handoff.
     *
     * <p>The export keeps only operational metadata and a field-level diff summary. It deliberately excludes raw
     * comments, candidate descriptions, steps, expected results and diff JSON values because reviewers may paste source
     * document details or secrets into those fields.
     */
    @Transactional
    public String exportReviewRecordsCsv(UUID taskId) {
        TestDesignTask task = taskOrThrow(taskId);
        long totalMatched = repository.countReviewRecords(task.id());
        List<TestDesignReviewRecord> exportedRecords = new ArrayList<>();
        int pageIndex = 0;
        while (exportedRecords.size() < REVIEW_RECORD_EXPORT_LIMIT) {
            List<TestDesignReviewRecord> pageRecords = repository.reviewRecords(
                    task.id(),
                    PageQuery.of(pageIndex, REVIEW_RECORD_EXPORT_PAGE_SIZE)
            );
            if (pageRecords.isEmpty()) {
                break;
            }
            int remaining = REVIEW_RECORD_EXPORT_LIMIT - exportedRecords.size();
            exportedRecords.addAll(pageRecords.stream().limit(remaining).toList());
            if (pageRecords.size() < REVIEW_RECORD_EXPORT_PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }

        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(task.id()));
        StringBuilder csv = new StringBuilder();
        appendReviewRecordExportHeader(csv);
        appendReviewRecordExportSummary(csv, "exportLimit", REVIEW_RECORD_EXPORT_LIMIT, task);
        appendReviewRecordExportSummary(csv, "totalMatched", totalMatched, task);
        appendReviewRecordExportSummary(csv, "exportedCount", exportedRecords.size(), task);
        appendReviewRecordExportSummary(csv, "truncated", totalMatched > exportedRecords.size(), task);
        appendReviewRecordExportSummary(csv, "actionCounts", reviewRecordActionCounts(exportedRecords), task);
        exportedRecords.forEach(record -> appendReviewRecordExportRow(csv, record, candidates.get(record.candidateId())));

        writeAudit("EXPORT", "TEST_DESIGN_REVIEW_RECORD", UUID.randomUUID(), task.projectId(), Map.of(
                "taskId", task.id(),
                "projectId", task.projectId(),
                "totalMatched", totalMatched,
                "exportedCount", exportedRecords.size(),
                "limit", REVIEW_RECORD_EXPORT_LIMIT,
                "truncated", totalMatched > exportedRecords.size()
        ));
        return csv.toString();
    }

    private TestDesignCandidate changeCandidateStatus(
            UUID id,
            Long version,
            TestDesignCandidateStatus status,
            String rejectedReason,
            String ignoredReason,
            String comment
    ) {
        TestDesignCandidate existing = candidateOrThrow(id);
        ensureEditable(existing);
        assertVersion(existing, version, true);
        Instant now = Instant.now();
        TestDesignCandidate updated = new TestDesignCandidate(
                existing.id(), existing.taskId(), existing.projectId(), existing.requirementId(), existing.apiId(),
                existing.title(), existing.description(), existing.coverageType(), existing.priority(), status.name(),
                existing.preconditions(), existing.stepsJson(), existing.expectedResult(), existing.tags(),
                existing.duplicateKey(), existing.confidence(), existing.promptKey(), existing.promptVersion(),
                existing.modelInvocationId(), existing.modelProviderName(), existing.modelName(), existing.assetCaseId(),
                trimToNull(comment),
                rejectedReason,
                ignoredReason,
                null,
                status == TestDesignCandidateStatus.CONFIRMED ? actorResolver.currentActor() : existing.confirmedBy(),
                status == TestDesignCandidateStatus.CONFIRMED ? now : existing.confirmedAt(),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        repository.saveCandidate(updated);
        saveReviewRecord(existing, updated, status.name(), comment);
        refreshTaskCounts(updated.taskId());
        return updated;
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

    private void refreshTaskCounts(UUID taskId) {
        TestDesignTask task = taskOrThrow(taskId);
        repository.saveTask(withTaskCounts(task, repository.candidatesByTask(taskId)));
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
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("changedFields", reviewChangedFields(before, after));
            diff.put("fieldDiffs", reviewFieldDiffs(before, after));
            diff.put("status", Map.of("before", before.status(), "after", after.status()));
            diff.put("version", Map.of("before", before.version(), "after", after.version()));
            return objectMapper.writeValueAsString(diff);
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

    private List<Map<String, Object>> reviewFieldDiffs(TestDesignCandidate before, TestDesignCandidate after) {
        List<Map<String, Object>> items = new ArrayList<>();
        appendReviewFieldDiff(items, "title", before.title(), after.title());
        appendReviewFieldDiff(items, "apiId", before.apiId(), after.apiId());
        appendReviewFieldDiff(items, "coverageType", before.coverageType(), after.coverageType());
        appendReviewFieldDiff(items, "priority", before.priority(), after.priority());
        appendReviewFieldDiff(items, "description", before.description(), after.description());
        appendReviewFieldDiff(items, "preconditions", before.preconditions(), after.preconditions());
        appendReviewFieldDiff(items, "steps", stepsPreview(before.stepsJson()), stepsPreview(after.stepsJson()));
        appendReviewFieldDiff(items, "expectedResult", before.expectedResult(), after.expectedResult());
        appendReviewFieldDiff(items, "tags", before.tags(), after.tags());
        appendReviewFieldDiff(items, "status", before.status(), after.status());
        appendReviewFieldDiff(items, "version", String.valueOf(before.version()), String.valueOf(after.version()));
        return items;
    }

    private void appendReviewFieldDiff(List<Map<String, Object>> items, String field, Object before, Object after) {
        String beforePreview = candidateExportPreview(stringValue(before), 180);
        String afterPreview = candidateExportPreview(stringValue(after), 180);
        if (Objects.equals(beforePreview, afterPreview)) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("before", beforePreview);
        item.put("after", afterPreview);
        items.add(item);
    }

    private String stepsPreview(String stepsJson) {
        return responseMapper.steps(stepsJson).stream()
                .map(step -> (step.stepOrder() + 1) + ". "
                        + safePreviewPart(step.action())
                        + " => "
                        + safePreviewPart(step.expectedResult()))
                .collect(Collectors.joining(" | "));
    }

    private static String safePreviewPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private Map<UUID, TestDesignCandidate> candidateById(List<TestDesignCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(TestDesignCandidate::id, Function.identity()));
    }

    private record TestDesignCandidateBatchTarget(UUID id, Long version) {
    }

    private List<TestDesignCandidateBatchTarget> batchTargets(TestDesignCandidateBatchActionCommand command) {
        if (command.candidates() != null && !command.candidates().isEmpty()) {
            return command.candidates().stream()
                    .filter(Objects::nonNull)
                    .map(item -> {
                        if (item.id() == null) {
                            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选项 id 不能为空");
                        }
                        return new TestDesignCandidateBatchTarget(item.id(), item.version());
                    })
                    .toList();
        }
        if (command.candidateIds() == null) {
            return List.of();
        }
        return command.candidateIds().stream()
                .map(id -> new TestDesignCandidateBatchTarget(id, null))
                .toList();
    }

    private List<TestDesignStepResponse> normalizeSteps(
            List<UpdateTestDesignCandidateCommand.StepCommand> commands,
            List<TestDesignStepResponse> fallback
    ) {
        if (commands == null) {
            return fallback;
        }
        List<TestDesignStepResponse> steps = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            UpdateTestDesignCandidateCommand.StepCommand command = commands.get(index);
            if (command == null) {
                continue;
            }
            steps.add(step(index, trimToNull(command.action()), trimToNull(command.expectedResult())));
        }
        return steps;
    }

    private String stepsJson(List<TestDesignStepResponse> steps) {
        return responseMapper.stepsJson(steps);
    }

    private static TestDesignStepResponse step(int order, String action, String expectedResult) {
        return new TestDesignStepResponse(order, action, expectedResult);
    }

    private String expectedResultForUpdate(
            String requestedValue,
            List<TestDesignStepResponse> steps,
            String fallback
    ) {
        String value = trimToNull(requestedValue);
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (steps != null && !steps.isEmpty() && StringUtils.hasText(steps.getLast().expectedResult())) {
            return steps.getLast().expectedResult();
        }
        return fallback;
    }

    private void validateProjectWhenProvided(String projectId) {
        if (StringUtils.hasText(projectId)) {
            contextClient.projectContext(projectId);
        }
    }

    private CandidateExportScope candidateExportScope(TestDesignCandidateQuery query) {
        if (query == null || (query.taskId() == null && !StringUtils.hasText(query.projectId()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出候选必须指定 taskId 或 projectId");
        }
        if (query.taskId() != null) {
            TestDesignTask task = taskOrThrow(query.taskId());
            if (StringUtils.hasText(query.projectId())) {
                String requestedProjectId = contextClient.projectContext(query.projectId()).resourceId();
                if (!Objects.equals(requestedProjectId, task.projectId())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 与 projectId 不属于同一项目");
                }
            }
            return new CandidateExportScope(task.id(), task.projectId());
        }
        return new CandidateExportScope(null, contextClient.projectContext(query.projectId()).resourceId());
    }

    private TestDesignCandidateQuery candidateExportQuery(
            TestDesignCandidateQuery query,
            CandidateExportScope scope,
            int pageIndex
    ) {
        return new TestDesignCandidateQuery(
                scope.taskId(),
                scope.projectId(),
                query.requirementId(),
                trimToNull(query.status()),
                trimToNull(query.coverageType()),
                trimToNull(query.keyword()),
                PageQuery.of(pageIndex, CANDIDATE_EXPORT_PAGE_SIZE)
        );
    }

    private static void appendCandidateExportHeader(StringBuilder csv) {
        CsvEncoder.appendLine(csv,
                "recordType",
                "metric",
                "value",
                "taskId",
                "projectId",
                "candidateId",
                "requirementId",
                "apiId",
                "title",
                "coverageType",
                "priority",
                "status",
                "version",
                "tags",
                "stepsCount",
                "hasExpectedResult",
                "hasReviewNote",
                "assetCaseId",
                "qualityFlags",
                "errorMessage",
                "createdAt",
                "updatedAt"
        );
    }

    private static void appendCandidateExportSummary(
            StringBuilder csv,
            String metric,
            Object value,
            CandidateExportScope scope
    ) {
        CsvEncoder.appendLine(csv,
                "summary",
                metric,
                value,
                scope.taskId(),
                scope.projectId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void appendCandidateExportRow(StringBuilder csv, TestDesignCandidate candidate) {
        CsvEncoder.appendLine(csv,
                "candidate",
                null,
                null,
                candidate.taskId(),
                candidate.projectId(),
                candidate.id(),
                candidate.requirementId(),
                candidate.apiId(),
                candidateExportPreview(candidate.title(), 200),
                candidate.coverageType(),
                candidate.priority(),
                candidate.status(),
                candidate.version(),
                String.join("|", summaryTags(candidate.tags())),
                responseMapper.steps(candidate.stepsJson()).size(),
                StringUtils.hasText(candidate.expectedResult()),
                hasCandidateReviewNote(candidate),
                candidate.assetCaseId(),
                candidateExportQualityFlags(candidate),
                candidateExportPreview(candidate.errorMessage(), 240),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }

    private static boolean hasCandidateReviewNote(TestDesignCandidate candidate) {
        return StringUtils.hasText(candidate.reviewComment())
                || StringUtils.hasText(candidate.rejectedReason())
                || StringUtils.hasText(candidate.ignoredReason());
    }

    private static String candidateExportQualityFlags(TestDesignCandidate candidate) {
        List<String> flags = new ArrayList<>();
        if (StringUtils.hasText(candidate.errorMessage())) {
            flags.add("ERROR_PRESENT");
        }
        if (StringUtils.hasText(candidate.rejectedReason())) {
            flags.add("REJECTED_REASON_PRESENT");
        }
        if (StringUtils.hasText(candidate.ignoredReason())) {
            flags.add("IGNORED_REASON_PRESENT");
        }
        if (candidate.confidence() > 0D && candidate.confidence() < 0.8D) {
            flags.add("LOW_CONFIDENCE");
        }
        return String.join("|", flags);
    }

    private static String candidateExportCounts(
            List<TestDesignCandidate> candidates,
            Function<TestDesignCandidate, String> classifier
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TestDesignCandidate candidate : candidates) {
            String key = classifier.apply(candidate);
            if (StringUtils.hasText(key)) {
                counts.merge(key, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private String candidateExportFilterSummary(TestDesignCandidateQuery query) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("taskId", query.taskId());
        filters.put("projectId", candidateExportPreview(query.projectId(), 120));
        filters.put("requirementId", query.requirementId());
        filters.put("status", candidateExportPreview(query.status(), 80));
        filters.put("coverageType", candidateExportPreview(query.coverageType(), 80));
        filters.put("keyword", candidateExportPreview(query.keyword(), 120));
        return filters.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static String candidateExportPreview(String value, int maxLength) {
        String preview = redactedPreview(value, maxLength);
        if (!StringUtils.hasText(preview)) {
            return preview;
        }
        return preview
                .replaceAll("(?i)raw\\s*prompt|rawPrompt", "[REDACTED]")
                .replaceAll("(?i)prompt\\s*plaintext|promptPlaintext", "[REDACTED]")
                .replaceAll("(?i)model\\s*input|modelInput", "[REDACTED]");
    }

    private static Map<String, Object> candidateExportAuditDetails(
            CandidateExportScope scope,
            long totalMatched,
            int exportedCount
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", scope.taskId() == null ? "PROJECT" : "TASK");
        if (scope.taskId() != null) {
            details.put("taskId", scope.taskId());
        }
        details.put("projectId", scope.projectId());
        details.put("totalMatched", totalMatched);
        details.put("exportedCount", exportedCount);
        details.put("limit", CANDIDATE_EXPORT_LIMIT);
        details.put("truncated", totalMatched > exportedCount);
        return details;
    }

    private TestDesignReviewRecordResponse toReviewRecordResponse(
            TestDesignReviewRecord record,
            TestDesignCandidate candidate
    ) {
        ReviewDiffSummary diffSummary = reviewDiffSummary(record.diffJson());
        return new TestDesignReviewRecordResponse(
                record.id(),
                record.taskId(),
                record.candidateId(),
                candidate == null ? null : candidateExportPreview(candidate.title(), 200),
                record.projectId(),
                record.action(),
                record.beforeStatus(),
                record.afterStatus(),
                record.reviewer(),
                StringUtils.hasText(record.comment()),
                candidateExportPreview(record.comment(), 160),
                diffSummary.changedFields(),
                diffSummary.versionBefore(),
                diffSummary.versionAfter(),
                diffSummary.diffItems(),
                record.createdAt()
        );
    }

    private static void appendReviewRecordExportHeader(StringBuilder csv) {
        CsvEncoder.appendLine(csv,
                "recordType",
                "metric",
                "value",
                "taskId",
                "projectId",
                "reviewRecordId",
                "candidateId",
                "title",
                "action",
                "beforeStatus",
                "afterStatus",
                "reviewer",
                "hasComment",
                "changedFields",
                "versionBefore",
                "versionAfter",
                "createdAt"
        );
    }

    private static void appendReviewRecordExportSummary(
            StringBuilder csv,
            String metric,
            Object value,
            TestDesignTask task
    ) {
        CsvEncoder.appendLine(csv,
                "summary",
                metric,
                value,
                task.id(),
                task.projectId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void appendReviewRecordExportRow(
            StringBuilder csv,
            TestDesignReviewRecord record,
            TestDesignCandidate candidate
    ) {
        TestDesignReviewRecordResponse response = toReviewRecordResponse(record, candidate);
        CsvEncoder.appendLine(csv,
                "reviewRecord",
                null,
                null,
                record.taskId(),
                record.projectId(),
                record.id(),
                record.candidateId(),
                response.title(),
                record.action(),
                record.beforeStatus(),
                record.afterStatus(),
                record.reviewer(),
                response.hasComment(),
                String.join("|", response.changedFields()),
                response.versionBefore(),
                response.versionAfter(),
                record.createdAt()
        );
    }

    private static String reviewRecordActionCounts(List<TestDesignReviewRecord> records) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TestDesignReviewRecord record : records) {
            if (StringUtils.hasText(record.action())) {
                counts.merge(record.action(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private ReviewDiffSummary reviewDiffSummary(String diffJson) {
        if (!StringUtils.hasText(diffJson)) {
            return new ReviewDiffSummary(List.of(), null, null, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(diffJson);
            List<String> changedFields = new ArrayList<>();
            JsonNode changedFieldsNode = root.path("changedFields");
            if (changedFieldsNode.isArray()) {
                changedFieldsNode.forEach(field -> {
                    String fieldName = reviewFieldName(field.asText());
                    if (StringUtils.hasText(fieldName)) {
                        changedFields.add(fieldName);
                    }
                });
            }
            if (changedFields.isEmpty() && root.path("titleChanged").asBoolean(false)) {
                changedFields.add("title");
            }
            JsonNode statusNode = root.path("status");
            if (!statusNode.isMissingNode()
                    && !Objects.equals(textOrNull(statusNode.path("before")), textOrNull(statusNode.path("after")))) {
                changedFields.add("status");
            }
            JsonNode versionNode = root.path("version");
            Long versionBefore = longOrNull(versionNode.path("before"));
            Long versionAfter = longOrNull(versionNode.path("after"));
            if (!Objects.equals(versionBefore, versionAfter)) {
                changedFields.add("version");
            }
            return new ReviewDiffSummary(
                    changedFields.stream().distinct().toList(),
                    versionBefore,
                    versionAfter,
                    reviewDiffItems(root.path("fieldDiffs"))
            );
        } catch (JsonProcessingException exception) {
            return new ReviewDiffSummary(List.of(), null, null, List.of());
        }
    }

    private static List<TestDesignReviewDiffItemResponse> reviewDiffItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<TestDesignReviewDiffItemResponse> items = new ArrayList<>();
        for (JsonNode item : node) {
            String field = reviewFieldName(textOrNull(item.path("field")));
            if (!StringUtils.hasText(field)) {
                continue;
            }
            items.add(new TestDesignReviewDiffItemResponse(
                    field,
                    candidateExportPreview(textOrNull(item.path("before")), 180),
                    candidateExportPreview(textOrNull(item.path("after")), 180)
            ));
            if (items.size() >= 20) {
                break;
            }
        }
        return List.copyOf(items);
    }

    private static String reviewFieldName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_.-]", "");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record ReviewDiffSummary(
            List<String> changedFields,
            Long versionBefore,
            Long versionAfter,
            List<TestDesignReviewDiffItemResponse> diffItems
    ) {
    }

    private record CandidateExportScope(UUID taskId, String projectId) {
    }

    private TestDesignTask taskOrThrow(UUID id) {
        return repository.task(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + id));
    }

    private TestDesignCandidate candidateOrThrow(UUID id) {
        return repository.candidate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选用例不存在: " + id));
    }

    private void ensureEditable(TestDesignCandidate candidate) {
        if (TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已发布候选用例不可编辑");
        }
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

    private static String normalizeCoverageType(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return fallback;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CoverageType.codes().contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的覆盖类型: " + rawValue);
        }
        return normalized;
    }

    private static String normalizePriority(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return StringUtils.hasText(fallback) ? fallback : "MEDIUM";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CANDIDATE_PRIORITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的优先级: " + rawValue);
        }
        return normalized;
    }

    private static String preconditions(RequirementResponse requirement) {
        if (StringUtils.hasText(requirement.acceptanceCriteria())) {
            return "需求验收标准已明确，测试前需准备满足业务上下文的数据";
        }
        return "需求描述已确认，测试数据和账号权限已准备";
    }

    private static String redactSensitiveText(String value) {
        // WP5 must not echo obvious secrets from WP3/WP4 source text while the full WP2 context packer is still pending.
        return TestDesignSensitiveText.redact(value);
    }

    private static String redactedPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = redactSensitiveText(value).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static List<String> summaryTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.replace('，', ',').split(",")).stream()
                .map(tag -> redactedPreview(tag, 64))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    private static String tagsText(List<String> tags) {
        if (tags == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                result.add(tag.trim());
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    private String requiredReason(TestDesignCandidateActionCommand command, String message) {
        if (command == null || !StringUtils.hasText(command.reason())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return command.reason().trim();
    }

    private int batchActionLimit() {
        return properties.batchActionLimit() <= 0 ? 100 : properties.batchActionLimit();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void writeAudit(String action, String resourceType, UUID resourceId, String projectId, Map<String, Object> after) {
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), projectId, "SUCCEEDED", after);
    }

}
