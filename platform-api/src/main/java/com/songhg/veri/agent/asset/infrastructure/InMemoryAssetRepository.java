package com.songhg.veri.agent.asset.infrastructure;

import com.songhg.veri.agent.asset.application.AssetRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("!db")
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
    public Optional<AssetRequirement> requirement(UUID id) {
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
                .findFirst();
    }

    @Override
    public AssetRequirement saveRequirement(AssetRequirement requirement) {
        requirements.put(requirement.id(), requirement);
        return requirement;
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
    public Optional<AssetApi> api(UUID id) {
        return Optional.ofNullable(apis.get(id));
    }

    @Override
    public AssetApi saveApi(AssetApi api) {
        apis.put(api.id(), api);
        return api;
    }

    @Override
    public List<AssetPage> pages(String projectId) {
        return pages.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(AssetPage::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<AssetPage> page(UUID id) {
        return Optional.ofNullable(pages.get(id));
    }

    @Override
    public AssetPage savePage(AssetPage page) {
        pages.put(page.id(), page);
        return page;
    }

    @Override
    public List<AssetBusinessFlow> businessFlows(String projectId) {
        return businessFlows.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(AssetBusinessFlow::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<AssetBusinessFlow> businessFlow(UUID id) {
        return Optional.ofNullable(businessFlows.get(id));
    }

    @Override
    public AssetBusinessFlow saveBusinessFlow(AssetBusinessFlow flow) {
        businessFlows.put(flow.id(), flow);
        return flow;
    }

    @Override
    public List<TestCaseRecord> testCases(String projectId) {
        return testCases.values().stream()
                .filter(value -> projectId == null || projectId.equals(value.projectId()))
                .sorted(Comparator.comparing(TestCaseRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<TestCaseRecord> testCase(UUID id) {
        return Optional.ofNullable(testCases.get(id));
    }

    @Override
    public TestCaseRecord saveTestCase(TestCaseRecord testCase) {
        testCases.put(testCase.id(), testCase);
        return testCase;
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
                existing.version() + 1,
                existing.createdAt(),
                Instant.now()
        )));
    }

    @Override
    public List<TraceLink> traceLinks(UUID requirementId, UUID apiId, UUID caseId) {
        return links.values().stream()
                .filter(value -> requirementId == null || requirementId.equals(value.requirementId()))
                .filter(value -> apiId == null || apiId.equals(value.apiId()))
                .filter(value -> caseId == null || caseId.equals(value.caseId()))
                .sorted(Comparator.comparing(TraceLink::createdAt).reversed())
                .toList();
    }

    @Override
    public TraceLink saveTraceLink(TraceLink link) {
        links.put(link.id(), link);
        return link;
    }
}
