package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.api.request.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetVersionRollbackService {

    private static final Logger log = LoggerFactory.getLogger(AssetVersionRollbackService.class);
    private static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final AssetRepository repository;
    private final ObjectMapper objectMapper;
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetProjectAuditService projectAuditService;

    public AssetVersionRollbackService(
            AssetRepository repository,
            ObjectMapper objectMapper,
            AssetVersionHistoryService versionHistoryService,
            AssetProjectAuditService projectAuditService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.versionHistoryService = versionHistoryService;
        this.projectAuditService = projectAuditService;
    }

    public RequirementResponse rollbackRequirementVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        AssetRequirement existing = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        AssetVersionHistory history = versionHistoryService.historyOrThrow("REQUIREMENT", id, version);
        JsonNode snapshot = jsonNode(history.snapshotJson());
        Instant now = Instant.now();
        AssetRequirement rollback = new AssetRequirement(
                existing.id(),
                existing.code(),
                requiredSnapshotText(snapshot, "title"),
                snapshotText(snapshot, "description"),
                existing.source(),
                existing.sourceRef(),
                snapshotText(snapshot, "sourceUrl"),
                snapshotText(snapshot, "acceptanceCriteria"),
                valueIn(snapshotText(snapshot, "status"), existing.status(), REVIEW_STATUSES, "status"),
                valueIn(snapshotText(snapshot, "priority"), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                snapshotText(snapshot, "tags"),
                existing.nextVersion(),
                lifecycleStatus(snapshotText(snapshot, "lifecycleStatus"), snapshotInstant(snapshot, "deletedAt")),
                snapshotInstant(snapshot, "archivedAt"),
                snapshotInstant(snapshot, "deletedAt"),
                existing.createdAt(),
                now
        );
        if ("ACTIVE".equals(lifecycleStatus(rollback.lifecycleStatus(), rollback.deletedAt()))) {
            ensureRequirementRestoreHasNoConflict(rollback);
        }
        projectAuditService.writeProjectAudit("ROLLBACK", "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(rollback);
        versionHistoryService.recordRequirementChange(existing, stored, "ROLLBACK");
        log.info("Rolled back requirement id={} to version={}, reason={}, trace_id={}",
                id, version, trimToNull(request == null ? null : request.reason()), TraceContext.getTraceId());
        return AssetResponseMapper.toRequirementResponse(stored);
    }

    public TestCaseResponse rollbackTestCaseVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        TestCaseRecord existing = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        AssetVersionHistory history = versionHistoryService.historyOrThrow("TEST_CASE", id, version);
        JsonNode snapshot = jsonNode(history.snapshotJson());
        Instant now = Instant.now();
        TestCaseRecord rollback = new TestCaseRecord(
                existing.id(),
                existing.code(),
                requiredSnapshotText(snapshot, "title"),
                snapshotText(snapshot, "description"),
                existing.projectId(),
                snapshotUuid(snapshot, "requirementId"),
                snapshotUuid(snapshot, "apiId"),
                existing.source(),
                existing.sourceRef(),
                valueIn(snapshotText(snapshot, "status"), existing.status(), REVIEW_STATUSES, "status"),
                valueIn(snapshotText(snapshot, "priority"), existing.priority(), PRIORITIES, "priority"),
                snapshotText(snapshot, "tags"),
                snapshotSteps(snapshot.path("steps"), id),
                existing.nextVersion(),
                lifecycleStatus(snapshotText(snapshot, "lifecycleStatus"), snapshotInstant(snapshot, "deletedAt")),
                snapshotInstant(snapshot, "archivedAt"),
                snapshotInstant(snapshot, "deletedAt"),
                existing.createdAt(),
                now
        );
        validateRequirementBelongsToProject(rollback.requirementId(), rollback.projectId());
        validateApiBelongsToProject(rollback.apiId(), rollback.projectId());
        if ("ACTIVE".equals(lifecycleStatus(rollback.lifecycleStatus(), rollback.deletedAt()))) {
            ensureTestCaseRestoreHasNoConflict(rollback);
        }
        projectAuditService.writeProjectAudit("ROLLBACK", "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(rollback);
        versionHistoryService.recordTestCaseChange(existing, stored, "ROLLBACK");
        log.info("Rolled back test case id={} to version={}, reason={}, trace_id={}",
                id, version, trimToNull(request == null ? null : request.reason()), TraceContext.getTraceId());
        return AssetResponseMapper.toTestCaseResponse(stored, stored.steps());
    }

    private JsonNode jsonNode(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String snapshotText(JsonNode snapshot, String field) {
        JsonNode node = snapshot.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        String value = node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static String requiredSnapshotText(JsonNode snapshot, String field) {
        String value = snapshotText(snapshot, field);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "版本快照缺少字段: " + field);
        }
        return value;
    }

    private static Instant snapshotInstant(JsonNode snapshot, String field) {
        String value = snapshotText(snapshot, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "版本快照时间字段不合法: " + field);
        }
    }

    private static UUID snapshotUuid(JsonNode snapshot, String field) {
        String value = snapshotText(snapshot, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "版本快照 UUID 字段不合法: " + field);
        }
    }

    private static List<TestCaseStep> snapshotSteps(JsonNode stepsNode, UUID caseId) {
        if (!stepsNode.isArray()) {
            return List.of();
        }
        List<TestCaseStep> steps = new ArrayList<>();
        int order = 0;
        for (JsonNode item : stepsNode) {
            steps.add(new TestCaseStep(
                    UUID.randomUUID(),
                    caseId,
                    order++,
                    textOrDefault(item.path("action"), "待补充操作"),
                    textOrDefault(item.path("expectedResult"), "待补充预期")
            ));
        }
        return steps;
    }

    private void validateRequirementBelongsToProject(UUID requirementId, String projectId) {
        if (requirementId == null) {
            return;
        }
        AssetRequirement requirement = repository.requirement(requirementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + requirementId));
        ensureSameProject("需求", requirement.id(), requirement.projectId(), projectId);
    }

    private void validateApiBelongsToProject(UUID apiId, String projectId) {
        if (apiId == null) {
            return;
        }
        AssetApi api = repository.api(apiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + apiId));
        ensureSameProject("API", api.id(), api.projectId(), projectId);
    }

    private void ensureSameProject(String resourceName, UUID resourceId, String actualProjectId, String expectedProjectId) {
        if (!StringUtils.hasText(actualProjectId) || !actualProjectId.equals(expectedProjectId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    resourceName + "不属于当前项目: " + resourceId
            );
        }
    }

    private void ensureRequirementRestoreHasNoConflict(AssetRequirement requirement) {
        if (repository.hasActiveRequirementCodeConflict(requirement.projectId(), requirement.code(), requirement.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下需求编码已被其他资产占用，无法恢复: " + requirement.code());
        }
        if ("IMPORT".equals(requirement.source()) && StringUtils.hasText(requirement.sourceRef())
                && repository.hasActiveRequirementSourceRefConflict(
                        requirement.projectId(),
                        requirement.source(),
                        requirement.sourceRef(),
                        requirement.id()
                )) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下导入来源已被其他需求占用，无法恢复: " + requirement.sourceRef());
        }
    }

    private void ensureTestCaseRestoreHasNoConflict(TestCaseRecord testCase) {
        if (repository.hasActiveTestCaseCodeConflict(testCase.projectId(), testCase.code(), testCase.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下测试用例编码已被其他资产占用，无法恢复: " + testCase.code());
        }
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        return AssetLifecycleStatus.normalize(lifecycleStatus, deletedAt);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
