package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersion;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
public class AssetVersionHistoryService {

    private final AssetRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;

    public AssetVersionHistoryService(AssetRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    @Autowired
    public AssetVersionHistoryService(
            AssetRepository repository,
            ObjectMapper objectMapper,
            AuthorizationService authorizationService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
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

    /**
     * Computes the next immutable revision number from the append-only history ledger.
     */
    public int nextVersion(String assetType, UUID assetId) {
        return repository.assetVersionHistory(assetType, assetId).stream()
                .mapToInt(AssetVersionHistory::version)
                .max()
                .orElse(0) + 1;
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

    public void recordApiCreated(AssetApi api) {
        saveApiHistory(api, nextVersion("API", api.id()), "CREATE", VersionDiff.empty());
    }

    public void recordApiChange(AssetApi before, AssetApi after, String changeType) {
        saveApiHistory(after, nextVersion("API", after.id()), changeType, apiDiff(before, after));
    }

    public void recordPageCreated(AssetPage page) {
        savePageHistory(page, nextVersion("PAGE", page.id()), "CREATE", VersionDiff.empty());
    }

    public void recordPageChange(AssetPage before, AssetPage after, String changeType) {
        savePageHistory(after, nextVersion("PAGE", after.id()), changeType, pageDiff(before, after));
    }

    public void recordBusinessFlowCreated(AssetBusinessFlow flow) {
        saveBusinessFlowHistory(flow, nextVersion("BUSINESS_FLOW", flow.id()), "CREATE", VersionDiff.empty());
    }

    public void recordBusinessFlowChange(AssetBusinessFlow before, AssetBusinessFlow after, String changeType) {
        saveBusinessFlowHistory(after, nextVersion("BUSINESS_FLOW", after.id()), changeType, businessFlowDiff(before, after));
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

    private void saveApiHistory(AssetApi api, int version, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "API",
                api.id(),
                api.projectId(),
                version,
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(apiSnapshot(api, version)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private void savePageHistory(AssetPage page, int version, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "PAGE",
                page.id(),
                page.projectId(),
                version,
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(pageSnapshot(page, version)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private void saveBusinessFlowHistory(AssetBusinessFlow flow, int version, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "BUSINESS_FLOW",
                flow.id(),
                flow.projectId(),
                version,
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(businessFlowSnapshot(flow, version)),
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

    private VersionDiff apiDiff(AssetApi before, AssetApi after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "summary", before.summary(), after.summary());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "httpMethod", before.httpMethod(), after.httpMethod());
        addDiff(diff, "path", before.path(), after.path());
        addDiff(diff, "source", before.source(), after.source());
        addDiff(diff, "sourceRef", before.sourceRef(), after.sourceRef());
        addDiff(diff, "version", before.version(), after.version());
        addDiff(diff, "requestSchema", jsonValue(before.requestSchema()), jsonValue(after.requestSchema()));
        addDiff(diff, "responseSchema", jsonValue(before.responseSchema()), jsonValue(after.responseSchema()));
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "lifecycleStatus", lifecycleStatus(before.lifecycleStatus(), before.deletedAt()),
                lifecycleStatus(after.lifecycleStatus(), after.deletedAt()));
        addDiff(diff, "archivedAt", before.archivedAt(), after.archivedAt());
        addDiff(diff, "deletedAt", before.deletedAt(), after.deletedAt());
        return versionDiff(diff);
    }

    private VersionDiff pageDiff(AssetPage before, AssetPage after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "name", before.name(), after.name());
        addDiff(diff, "urlPattern", before.urlPattern(), after.urlPattern());
        addDiff(diff, "source", before.source(), after.source());
        addDiff(diff, "sourceRef", before.sourceRef(), after.sourceRef());
        addDiff(diff, "sourceVersion", before.sourceVersion(), after.sourceVersion());
        addDiff(diff, "componentTree", jsonValue(before.componentTree()), jsonValue(after.componentTree()));
        addDiff(diff, "screenshotUrl", before.screenshotUrl(), after.screenshotUrl());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "lifecycleStatus", lifecycleStatus(before.lifecycleStatus(), before.deletedAt()),
                lifecycleStatus(after.lifecycleStatus(), after.deletedAt()));
        addDiff(diff, "archivedAt", before.archivedAt(), after.archivedAt());
        addDiff(diff, "deletedAt", before.deletedAt(), after.deletedAt());
        return versionDiff(diff);
    }

    private VersionDiff businessFlowDiff(AssetBusinessFlow before, AssetBusinessFlow after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "name", before.name(), after.name());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "flowJson", jsonValue(before.flowJson()), jsonValue(after.flowJson()));
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "status", before.status(), after.status());
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

    private LinkedHashMap<String, Object> apiSnapshot(AssetApi api, int version) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", api.id());
        snapshot.put("code", api.code());
        snapshot.put("summary", api.summary());
        snapshot.put("description", api.description());
        snapshot.put("httpMethod", api.httpMethod());
        snapshot.put("path", api.path());
        snapshot.put("source", api.source());
        snapshot.put("sourceRef", api.sourceRef());
        snapshot.put("version", api.version());
        snapshot.put("requestSchema", jsonValue(api.requestSchema()));
        snapshot.put("responseSchema", jsonValue(api.responseSchema()));
        snapshot.put("projectId", api.projectId());
        snapshot.put("status", api.status());
        snapshot.put("revision", version);
        snapshot.put("lifecycleStatus", lifecycleStatus(api.lifecycleStatus(), api.deletedAt()));
        snapshot.put("archivedAt", api.archivedAt());
        snapshot.put("deletedAt", api.deletedAt());
        snapshot.put("createdAt", api.createdAt());
        snapshot.put("updatedAt", api.updatedAt());
        return snapshot;
    }

    private LinkedHashMap<String, Object> pageSnapshot(AssetPage page, int version) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", page.id());
        snapshot.put("code", page.code());
        snapshot.put("name", page.name());
        snapshot.put("urlPattern", page.urlPattern());
        snapshot.put("source", page.source());
        snapshot.put("sourceRef", page.sourceRef());
        snapshot.put("sourceVersion", page.sourceVersion());
        snapshot.put("componentTree", jsonValue(page.componentTree()));
        snapshot.put("screenshotUrl", page.screenshotUrl());
        snapshot.put("projectId", page.projectId());
        snapshot.put("status", page.status());
        snapshot.put("revision", version);
        snapshot.put("lifecycleStatus", lifecycleStatus(page.lifecycleStatus(), page.deletedAt()));
        snapshot.put("archivedAt", page.archivedAt());
        snapshot.put("deletedAt", page.deletedAt());
        snapshot.put("createdAt", page.createdAt());
        snapshot.put("updatedAt", page.updatedAt());
        return snapshot;
    }

    private LinkedHashMap<String, Object> businessFlowSnapshot(AssetBusinessFlow flow, int version) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", flow.id());
        snapshot.put("code", flow.code());
        snapshot.put("name", flow.name());
        snapshot.put("description", flow.description());
        snapshot.put("flowJson", jsonValue(flow.flowJson()));
        snapshot.put("priority", flow.priority());
        snapshot.put("projectId", flow.projectId());
        snapshot.put("status", flow.status());
        snapshot.put("revision", version);
        snapshot.put("lifecycleStatus", lifecycleStatus(flow.lifecycleStatus(), flow.deletedAt()));
        snapshot.put("archivedAt", flow.archivedAt());
        snapshot.put("deletedAt", flow.deletedAt());
        snapshot.put("createdAt", flow.createdAt());
        snapshot.put("updatedAt", flow.updatedAt());
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

    /**
     * Resolves the version-history actor through the shared authorization boundary.
     */
    private String currentActor() {
        if (authorizationService == null) {
            return "system";
        }
        ServicePrincipal servicePrincipal = authorizationService.currentServicePrincipal();
        if (servicePrincipal != null) {
            return serviceActor(servicePrincipal);
        }
        AuthUserPrincipal userPrincipal = authorizationService.currentUserPrincipal();
        if (userPrincipal != null) {
            return userActor(userPrincipal);
        }
        return "system";
    }

    /**
     * Keeps user audit labels stable when display metadata is partially missing.
     */
    private static String userActor(AuthUserPrincipal principal) {
        if (StringUtils.hasText(principal.username())) {
            return principal.username();
        }
        return principal.userId() == null ? "system" : principal.userId().toString();
    }

    /**
     * Preserves service caller identity while appending delegated user context when available.
     */
    private static String serviceActor(ServicePrincipal principal) {
        if (!StringUtils.hasText(principal.callerService())) {
            return "system";
        }
        if (StringUtils.hasText(principal.delegatedUserId())) {
            return principal.callerService() + ":" + principal.delegatedUserId();
        }
        return principal.callerService();
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

    private Object jsonValue(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            return rawJson;
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
