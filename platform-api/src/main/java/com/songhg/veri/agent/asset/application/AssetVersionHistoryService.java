package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersion;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetVersionHistoryService {

    private final AssetRepository repository;
    private final ObjectMapper objectMapper;

    public AssetVersionHistoryService(AssetRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<AssetVersionHistoryResponse> responses(String assetType, UUID assetId) {
        return repository.assetVersionHistory(assetType, assetId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AssetVersionHistory historyOrThrow(String assetType, UUID assetId, int version) {
        return AssetVersion.find(repository.assetVersionHistory(assetType, assetId), version)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "资产版本不存在: " + assetType + "/" + assetId + "/v" + version
                ));
    }

    public void recordRequirementCreated(AssetRequirement requirement) {
        saveRequirementHistory(requirement, "CREATE", VersionDiff.empty());
    }

    public void recordRequirementChange(AssetRequirement before, AssetRequirement after, String changeType) {
        saveRequirementHistory(after, changeType, requirementDiff(before, after));
    }

    public void recordTestCaseCreated(TestCaseRecord testCase) {
        saveTestCaseHistory(testCase, "CREATE", VersionDiff.empty());
    }

    public void recordTestCaseChange(TestCaseRecord before, TestCaseRecord after, String changeType) {
        saveTestCaseHistory(after, changeType, testCaseDiff(before, after));
    }

    private AssetVersionHistoryResponse toResponse(AssetVersionHistory history) {
        return new AssetVersionHistoryResponse(
                history.id(),
                history.assetType(),
                history.assetId(),
                history.projectId(),
                history.version(),
                history.changeType(),
                history.actor(),
                splitChangedFields(history.changedFields()),
                jsonNode(history.diffJson()),
                jsonNode(history.snapshotJson()),
                history.traceId(),
                history.createdAt()
        );
    }

    private void saveRequirementHistory(AssetRequirement requirement, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "REQUIREMENT",
                requirement.id(),
                requirement.projectId(),
                requirement.version(),
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(requirementSnapshot(requirement)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private void saveTestCaseHistory(TestCaseRecord testCase, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "TEST_CASE",
                testCase.id(),
                testCase.projectId(),
                testCase.version(),
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(testCaseSnapshot(testCase)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private VersionDiff requirementDiff(AssetRequirement before, AssetRequirement after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "title", before.title(), after.title());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "sourceUrl", before.sourceUrl(), after.sourceUrl());
        addDiff(diff, "acceptanceCriteria", before.acceptanceCriteria(), after.acceptanceCriteria());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "tags", normalizedTags(before.tags()), normalizedTags(after.tags()));
        addDiff(diff, "lifecycleStatus", lifecycleStatus(before.lifecycleStatus(), before.deletedAt()),
                lifecycleStatus(after.lifecycleStatus(), after.deletedAt()));
        addDiff(diff, "archivedAt", before.archivedAt(), after.archivedAt());
        addDiff(diff, "deletedAt", before.deletedAt(), after.deletedAt());
        return versionDiff(diff);
    }

    private VersionDiff testCaseDiff(TestCaseRecord before, TestCaseRecord after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "title", before.title(), after.title());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "requirementId", before.requirementId(), after.requirementId());
        addDiff(diff, "apiId", before.apiId(), after.apiId());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "tags", normalizedTags(before.tags()), normalizedTags(after.tags()));
        addDiff(diff, "steps", stepSnapshot(before.steps()), stepSnapshot(after.steps()));
        addDiff(diff, "lifecycleStatus", lifecycleStatus(before.lifecycleStatus(), before.deletedAt()),
                lifecycleStatus(after.lifecycleStatus(), after.deletedAt()));
        addDiff(diff, "archivedAt", before.archivedAt(), after.archivedAt());
        addDiff(diff, "deletedAt", before.deletedAt(), after.deletedAt());
        return versionDiff(diff);
    }

    private static void addDiff(LinkedHashMap<String, Object> diff, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        LinkedHashMap<String, Object> fieldDiff = new LinkedHashMap<>();
        fieldDiff.put("before", before);
        fieldDiff.put("after", after);
        diff.put(field, fieldDiff);
    }

    private VersionDiff versionDiff(LinkedHashMap<String, Object> diff) {
        if (diff.isEmpty()) {
            return VersionDiff.empty();
        }
        return new VersionDiff(new ArrayList<>(diff.keySet()), jsonString(diff));
    }

    private static LinkedHashMap<String, Object> requirementSnapshot(AssetRequirement requirement) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", requirement.id());
        snapshot.put("code", requirement.code());
        snapshot.put("title", requirement.title());
        snapshot.put("description", requirement.description());
        snapshot.put("source", requirement.source());
        snapshot.put("sourceRef", requirement.sourceRef());
        snapshot.put("sourceUrl", requirement.sourceUrl());
        snapshot.put("acceptanceCriteria", requirement.acceptanceCriteria());
        snapshot.put("status", requirement.status());
        snapshot.put("priority", requirement.priority());
        snapshot.put("projectId", requirement.projectId());
        snapshot.put("tags", requirement.tags());
        snapshot.put("version", requirement.version());
        snapshot.put("lifecycleStatus", lifecycleStatus(requirement.lifecycleStatus(), requirement.deletedAt()));
        snapshot.put("archivedAt", requirement.archivedAt());
        snapshot.put("deletedAt", requirement.deletedAt());
        snapshot.put("createdAt", requirement.createdAt());
        snapshot.put("updatedAt", requirement.updatedAt());
        return snapshot;
    }

    private static LinkedHashMap<String, Object> testCaseSnapshot(TestCaseRecord testCase) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", testCase.id());
        snapshot.put("code", testCase.code());
        snapshot.put("title", testCase.title());
        snapshot.put("description", testCase.description());
        snapshot.put("projectId", testCase.projectId());
        snapshot.put("requirementId", testCase.requirementId());
        snapshot.put("apiId", testCase.apiId());
        snapshot.put("source", testCase.source());
        snapshot.put("sourceRef", testCase.sourceRef());
        snapshot.put("status", testCase.status());
        snapshot.put("priority", testCase.priority());
        snapshot.put("tags", testCase.tags());
        snapshot.put("steps", stepSnapshot(testCase.steps()));
        snapshot.put("version", testCase.version());
        snapshot.put("lifecycleStatus", lifecycleStatus(testCase.lifecycleStatus(), testCase.deletedAt()));
        snapshot.put("archivedAt", testCase.archivedAt());
        snapshot.put("deletedAt", testCase.deletedAt());
        snapshot.put("createdAt", testCase.createdAt());
        snapshot.put("updatedAt", testCase.updatedAt());
        return snapshot;
    }

    private static List<Map<String, Object>> stepSnapshot(List<TestCaseStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                .map(step -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", step.stepOrder());
                    item.put("action", step.action());
                    item.put("expectedResult", step.expectedResult());
                    return item;
                })
                .toList();
    }

    private static String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ServicePrincipal servicePrincipal) {
            return servicePrincipal.callerService() + ":" + servicePrincipal.delegatedUserId();
        }
        if (principal instanceof AuthUserPrincipal userPrincipal) {
            return StringUtils.hasText(userPrincipal.username())
                    ? userPrincipal.username()
                    : userPrincipal.userId().toString();
        }
        if (principal instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return "system";
    }

    private static List<String> splitChangedFields(String changedFields) {
        if (!StringUtils.hasText(changedFields)) {
            return List.of();
        }
        return Arrays.stream(changedFields.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
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

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产版本历史序列化失败");
        }
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        return AssetLifecycleStatus.normalize(lifecycleStatus, deletedAt);
    }

    private static String normalizedTags(String rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, rawTags);
        return String.join(",", tags);
    }

    private static void addTags(LinkedHashSet<String> tags, String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return;
        }
        for (String tag : rawTags.replace("，", ",").split(",")) {
            String trimmed = tag.trim();
            if (StringUtils.hasText(trimmed)) {
                tags.add(trimmed);
            }
        }
    }

    private record VersionDiff(List<String> changedFields, String diffJson) {
        private static VersionDiff empty() {
            return new VersionDiff(List.of(), "{}");
        }
    }
}
