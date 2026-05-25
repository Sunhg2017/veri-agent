package com.songhg.veri.agent.asset.infrastructure;

import com.songhg.veri.agent.asset.application.query.AssetListQuery;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("local")
@Primary
@Repository
public class InMemoryAssetRepository implements AssetRepository {

    private final ConcurrentHashMap<UUID, AssetRequirement> requirements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AssetApi> apis = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AssetPage> pages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AssetBusinessFlow> businessFlows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestCaseRecord> testCases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AssetVersionHistory> versionHistories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TraceLink> links = new ConcurrentHashMap<>();

    @Override
    public List<AssetRequirement> requirements(String projectId) {
        return requirements.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(AssetRequirement::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AssetRequirement> requirements(AssetListQuery query) {
        return filteredRequirements(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countRequirements(AssetListQuery query) {
        return filteredRequirements(query).count();
    }

    @Override
    public Optional<AssetRequirement> requirement(UUID id) {
        return requirementIncludingInactive(id)
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())));
    }

    @Override
    public Optional<AssetRequirement> requirementIncludingInactive(UUID id) {
        return Optional.ofNullable(requirements.get(id));
    }

    @Override
    public Optional<AssetRequirement> requirementBySourceRef(String projectId, String source, String sourceRef) {
        if (projectId == null || source == null || sourceRef == null) {
            return Optional.empty();
        }
        return requirements.values().stream()
                .filter(value -> projectId.equals(value.projectId()))
                .filter(value -> source.equals(value.source()))
                .filter(value -> sourceRef.equals(value.sourceRef()))
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())))
                .findFirst();
    }

    @Override
    public AssetRequirement saveRequirement(AssetRequirement requirement) {
        requirements.put(requirement.id(), requirement);
        return requirement;
    }

    @Override
    public boolean hasActiveRequirementCodeConflict(String projectId, String code, UUID excludeId) {
        return requirements.values().stream()
                .anyMatch(value -> projectId.equals(value.projectId())
                        && code.equals(value.code())
                        && !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt()))
                        && !value.id().equals(excludeId));
    }

    @Override
    public boolean hasActiveRequirementSourceRefConflict(String projectId, String source, String sourceRef, UUID excludeId) {
        if (projectId == null || source == null || sourceRef == null) {
            return false;
        }
        return requirements.values().stream()
                .anyMatch(value -> projectId.equals(value.projectId())
                        && source.equals(value.source())
                        && sourceRef.equals(value.sourceRef())
                        && !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt()))
                        && !value.id().equals(excludeId));
    }

    @Override
    public List<AssetVersionHistory> assetVersionHistory(String assetType, UUID assetId) {
        return versionHistories.values().stream()
                .filter(value -> assetType.equals(value.assetType()))
                .filter(value -> assetId.equals(value.assetId()))
                .sorted(Comparator.comparingInt(AssetVersionHistory::version).reversed()
                        .thenComparing(AssetVersionHistory::createdAt, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public AssetVersionHistory saveVersionHistory(AssetVersionHistory history) {
        versionHistories.put(history.id(), history);
        return history;
    }

    @Override
    public List<AssetApi> apis(String projectId) {
        return apis.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(AssetApi::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AssetApi> apis(AssetListQuery query) {
        return filteredApis(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countApis(AssetListQuery query) {
        return filteredApis(query).count();
    }

    @Override
    public Optional<AssetApi> api(UUID id) {
        return apiIncludingInactive(id)
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())));
    }

    @Override
    public Optional<AssetApi> apiIncludingInactive(UUID id) {
        return Optional.ofNullable(apis.get(id));
    }

    @Override
    public Optional<AssetApi> apiByPath(String projectId, String path, String httpMethod) {
        if (projectId == null || path == null || httpMethod == null) {
            return Optional.empty();
        }
        String normalizedMethod = httpMethod.toUpperCase(Locale.ROOT);
        return apis.values().stream()
                .filter(value -> projectId.equals(value.projectId()))
                .filter(value -> path.equals(value.path()))
                .filter(value -> normalizedMethod.equals(value.httpMethod().toUpperCase(Locale.ROOT)))
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())))
                .findFirst();
    }

    @Override
    public AssetApi saveApi(AssetApi api) {
        apis.put(api.id(), api);
        return api;
    }

    @Override
    public boolean hasActiveApiPathConflict(String projectId, String path, String httpMethod, UUID excludeId) {
        String normalizedMethod = httpMethod == null ? null : httpMethod.toUpperCase(Locale.ROOT);
        return apis.values().stream()
                .anyMatch(value -> projectId.equals(value.projectId())
                        && path.equals(value.path())
                        && normalizedMethod != null
                        && normalizedMethod.equals(value.httpMethod().toUpperCase(Locale.ROOT))
                        && !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt()))
                        && !value.id().equals(excludeId));
    }

    @Override
    public List<AssetPage> pages(String projectId) {
        return pages.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(AssetPage::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AssetPage> pages(AssetListQuery query) {
        return filteredPages(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countPages(AssetListQuery query) {
        return filteredPages(query).count();
    }

    @Override
    public Optional<AssetPage> page(UUID id) {
        return pageIncludingInactive(id)
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())));
    }

    @Override
    public Optional<AssetPage> pageIncludingInactive(UUID id) {
        return Optional.ofNullable(pages.get(id));
    }

    @Override
    public Optional<AssetPage> pageBySourceRef(String projectId, String source, String sourceRef) {
        if (projectId == null || source == null || sourceRef == null) {
            return Optional.empty();
        }
        return pages.values().stream()
                .filter(value -> projectId.equals(value.projectId()))
                .filter(value -> source.equals(value.source()))
                .filter(value -> sourceRef.equals(value.sourceRef()))
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())))
                .findFirst();
    }

    @Override
    public AssetPage savePage(AssetPage page) {
        pages.put(page.id(), page);
        return page;
    }

    @Override
    public boolean hasActivePageCodeConflict(String projectId, String code, UUID excludeId) {
        return pages.values().stream()
                .anyMatch(value -> projectId.equals(value.projectId())
                        && code.equals(value.code())
                        && !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt()))
                        && !value.id().equals(excludeId));
    }

    @Override
    public List<AssetBusinessFlow> businessFlows(String projectId) {
        return businessFlows.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(AssetBusinessFlow::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AssetBusinessFlow> businessFlows(AssetListQuery query) {
        return filteredBusinessFlows(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countBusinessFlows(AssetListQuery query) {
        return filteredBusinessFlows(query).count();
    }

    @Override
    public Optional<AssetBusinessFlow> businessFlow(UUID id) {
        return businessFlowIncludingInactive(id)
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())));
    }

    @Override
    public Optional<AssetBusinessFlow> businessFlowIncludingInactive(UUID id) {
        return Optional.ofNullable(businessFlows.get(id));
    }

    @Override
    public AssetBusinessFlow saveBusinessFlow(AssetBusinessFlow flow) {
        businessFlows.put(flow.id(), flow);
        return flow;
    }

    @Override
    public boolean hasActiveBusinessFlowCodeConflict(String projectId, String code, UUID excludeId) {
        return businessFlows.values().stream()
                .anyMatch(value -> projectId.equals(value.projectId())
                        && code.equals(value.code())
                        && !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt()))
                        && !value.id().equals(excludeId));
    }

    @Override
    public List<TestCaseRecord> testCases(String projectId) {
        return testCases.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(TestCaseRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public List<TestCaseRecord> testCases(AssetListQuery query) {
        return filteredTestCases(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countTestCases(AssetListQuery query) {
        return filteredTestCases(query).count();
    }

    @Override
    public Optional<TestCaseRecord> testCase(UUID id) {
        return testCaseIncludingInactive(id)
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())));
    }

    @Override
    public Optional<TestCaseRecord> testCaseIncludingInactive(UUID id) {
        return Optional.ofNullable(testCases.get(id));
    }

    @Override
    public Optional<TestCaseRecord> testCaseBySourceRef(String projectId, String source, String sourceRef) {
        if (projectId == null || source == null || sourceRef == null) {
            return Optional.empty();
        }
        return testCases.values().stream()
                .filter(value -> projectId.equals(value.projectId()))
                .filter(value -> source.equalsIgnoreCase(value.source()))
                .filter(value -> sourceRef.equals(value.sourceRef()))
                .filter(value -> !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt())))
                .findFirst();
    }

    @Override
    public TestCaseRecord saveTestCase(TestCaseRecord testCase) {
        testCases.put(testCase.id(), testCase);
        return testCase;
    }

    @Override
    public boolean hasActiveTestCaseCodeConflict(String projectId, String code, UUID excludeId) {
        return testCases.values().stream()
                .anyMatch(value -> projectId.equals(value.projectId())
                        && code.equals(value.code())
                        && !"DELETED".equals(lifecycleStatus(value.lifecycleStatus(), value.deletedAt()))
                        && !value.id().equals(excludeId));
    }

    @Override
    public List<TestCaseStep> testCaseSteps(UUID caseId) {
        return testCase(caseId)
                .map(TestCaseRecord::steps)
                .orElse(List.of());
    }

    @Override
    public void replaceTestCaseSteps(UUID caseId, List<TestCaseStep> steps) {
        testCase(caseId).ifPresent(existing -> testCases.put(caseId, new TestCaseRecord(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.projectId(),
                existing.requirementId(),
                existing.apiId(),
                existing.source(),
                existing.sourceRef(),
                existing.status(),
                existing.priority(),
                existing.tags(),
                steps,
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                Instant.now()
        )));
    }

    @Override
    public List<TraceLink> traceLinks(UUID requirementId, UUID apiId, UUID pageId, UUID flowId, UUID caseId) {
        Map<UUID, TraceLink> grouped = new LinkedHashMap<>();
        links.values().stream()
                .filter(value -> requirementId == null || requirementId.equals(value.requirementId()))
                .filter(value -> apiId == null || apiId.equals(value.apiId()))
                .filter(value -> pageId == null || pageId.equals(value.pageId()))
                .filter(value -> flowId == null || flowId.equals(value.flowId()))
                .filter(value -> caseId == null || caseId.equals(value.caseId()))
                .sorted(Comparator.comparing(TraceLink::createdAt))
                .forEach(value -> grouped.merge(value.requirementId(), value, InMemoryAssetRepository::mergeTraceLink));
        return grouped.values().stream()
                .sorted(Comparator.comparing(TraceLink::createdAt).reversed())
                .toList();
    }

    @Override
    public TraceLink saveTraceLink(TraceLink link) {
        links.put(link.id(), link);
        return link;
    }

    private static TraceLink mergeTraceLink(TraceLink left, TraceLink right) {
        return new TraceLink(
                left.id(),
                left.requirementId(),
                left.apiId() == null ? right.apiId() : left.apiId(),
                left.pageId() == null ? right.pageId() : left.pageId(),
                left.flowId() == null ? right.flowId() : left.flowId(),
                left.caseId() == null ? right.caseId() : left.caseId(),
                left.createdAt().isBefore(right.createdAt()) ? left.createdAt() : right.createdAt()
        );
    }

    private Stream<AssetRequirement> filteredRequirements(AssetListQuery query) {
        return requirements.values().stream()
                .filter(value -> matchesProject(query.projectId(), value.projectId()))
                .filter(value -> matchesLifecycle(value.lifecycleStatus(), value.deletedAt(), query.lifecycleStatus()))
                .filter(value -> matches(value.status(), query.status()))
                .filter(value -> matches(value.source(), query.source()))
                .filter(value -> containsKeyword(query.keyword(), value.code(), value.title(), value.description(), value.sourceRef(), value.tags()))
                .sorted(Comparator.comparing(AssetRequirement::createdAt).reversed());
    }

    private Stream<AssetApi> filteredApis(AssetListQuery query) {
        return apis.values().stream()
                .filter(value -> matchesProject(query.projectId(), value.projectId()))
                .filter(value -> matchesLifecycle(value.lifecycleStatus(), value.deletedAt(), query.lifecycleStatus()))
                .filter(value -> matches(value.status(), query.status()))
                .filter(value -> matches(value.source(), query.source()))
                .filter(value -> containsKeyword(query.keyword(), value.code(), value.summary(), value.description(), value.path(), value.sourceRef()))
                .sorted(Comparator.comparing(AssetApi::createdAt).reversed());
    }

    private Stream<AssetPage> filteredPages(AssetListQuery query) {
        return pages.values().stream()
                .filter(value -> matchesProject(query.projectId(), value.projectId()))
                .filter(value -> matchesLifecycle(value.lifecycleStatus(), value.deletedAt(), query.lifecycleStatus()))
                .filter(value -> matches(value.status(), query.status()))
                .filter(value -> matches(value.source(), query.source()))
                .filter(value -> containsKeyword(query.keyword(), value.code(), value.name(), value.urlPattern(), value.sourceRef(), value.sourceVersion()))
                .sorted(Comparator.comparing(AssetPage::createdAt).reversed());
    }

    private Stream<AssetBusinessFlow> filteredBusinessFlows(AssetListQuery query) {
        return businessFlows.values().stream()
                .filter(value -> matchesProject(query.projectId(), value.projectId()))
                .filter(value -> matchesLifecycle(value.lifecycleStatus(), value.deletedAt(), query.lifecycleStatus()))
                .filter(value -> matches(value.status(), query.status()))
                .filter(value -> containsKeyword(query.keyword(), value.code(), value.name(), value.description()))
                .sorted(Comparator.comparing(AssetBusinessFlow::createdAt).reversed());
    }

    private Stream<TestCaseRecord> filteredTestCases(AssetListQuery query) {
        return testCases.values().stream()
                .filter(value -> matchesProject(query.projectId(), value.projectId()))
                .filter(value -> matchesLifecycle(value.lifecycleStatus(), value.deletedAt(), query.lifecycleStatus()))
                .filter(value -> matches(value.status(), query.status()))
                .filter(value -> matches(value.source(), query.source()))
                .filter(value -> containsKeyword(query.keyword(), value.code(), value.title(), value.description(), value.sourceRef(), value.tags()))
                .sorted(Comparator.comparing(TestCaseRecord::createdAt).reversed());
    }

    private static boolean matchesProject(String expectedProjectId, String actualProjectId) {
        return expectedProjectId == null || expectedProjectId.equals(actualProjectId);
    }

    private static boolean matchesLifecycle(String actual, Instant deletedAt, String expected) {
        return expected.equals(lifecycleStatus(actual, deletedAt));
    }

    private static boolean matches(String actual, String expected) {
        return expected == null || expected.equalsIgnoreCase(actual);
    }

    private static boolean containsKeyword(String keyword, String... values) {
        if (keyword == null) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        if (deletedAt != null) {
            return "DELETED";
        }
        return lifecycleStatus == null ? "ACTIVE" : lifecycleStatus;
    }
}
