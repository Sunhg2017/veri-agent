package com.songhg.veri.agent.apiautomation.infrastructure;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryApiAutomationRepository implements ApiAutomationRepository {

    private final ConcurrentHashMap<UUID, ApiAutomationSpec> specs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ApiAutomationEndpointSnapshot> endpointSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ApiAutomationGenerationTask> generationTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ApiAutomationCase> automationCases = new ConcurrentHashMap<>();

    @Override
    public void insertSpec(ApiAutomationSpec spec) {
        specs.put(spec.id(), spec);
    }

    @Override
    public void updateSpecParseResult(ApiAutomationSpec spec) {
        specs.put(spec.id(), spec);
    }

    @Override
    public void deleteEndpointSnapshots(UUID specId) {
        endpointSnapshots.entrySet().removeIf(entry -> specId.equals(entry.getValue().specId()));
    }

    @Override
    public void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot) {
        endpointSnapshots.put(snapshot.id(), snapshot);
    }

    @Override
    public void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot) {
        endpointSnapshots.put(snapshot.id(), snapshot);
    }

    @Override
    public void insertGenerationTask(ApiAutomationGenerationTask task) {
        generationTasks.put(task.id(), task);
    }

    @Override
    public void insertAutomationCase(ApiAutomationCase automationCase) {
        automationCases.put(automationCase.id(), automationCase);
    }

    @Override
    public Optional<ApiAutomationSpec> spec(UUID id) {
        return Optional.ofNullable(specs.get(id));
    }

    @Override
    public Optional<ApiAutomationGenerationTask> generationTask(UUID id) {
        return Optional.ofNullable(generationTasks.get(id));
    }

    @Override
    public Optional<ApiAutomationGenerationTask> generationTaskByProjectAndDigest(String projectId, String requestDigest) {
        return generationTasks.values().stream()
                .filter(task -> projectId.equals(task.projectId()))
                .filter(task -> requestDigest.equals(task.requestDigest()))
                .findFirst();
    }

    @Override
    public Optional<ApiAutomationGenerationTask> generationTaskByProjectAndKey(String projectId, String requestKey) {
        return generationTasks.values().stream()
                .filter(task -> projectId.equals(task.projectId()))
                .filter(task -> requestKey.equals(task.requestKey()))
                .findFirst();
    }

    @Override
    public Optional<ApiAutomationSpec> activeSpecByProjectAndDigest(String projectId, String specDigest) {
        return specs.values().stream()
                .filter(spec -> projectId.equals(spec.projectId()))
                .filter(spec -> specDigest.equals(spec.specDigest()))
                .filter(spec -> !"ARCHIVED".equals(spec.status()))
                .findFirst();
    }

    @Override
    public List<ApiAutomationSpec> specs(ApiAutomationSpecQuery query) {
        return filteredSpecs(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countSpecs(ApiAutomationSpecQuery query) {
        return filteredSpecs(query).count();
    }

    @Override
    public List<ApiAutomationEndpointSnapshot> endpointSnapshots(UUID specId) {
        return endpointSnapshots.values().stream()
                .filter(snapshot -> specId.equals(snapshot.specId()))
                .sorted(Comparator.comparing(ApiAutomationEndpointSnapshot::path)
                        .thenComparing(ApiAutomationEndpointSnapshot::httpMethod))
                .toList();
    }

    @Override
    public List<ApiAutomationCase> automationCases(UUID taskId) {
        return automationCases.values().stream()
                .filter(automationCase -> taskId.equals(automationCase.taskId()))
                .sorted(Comparator.comparing(ApiAutomationCase::path)
                        .thenComparing(ApiAutomationCase::coverageType))
                .toList();
    }

    @Override
    public Optional<String> specProjectScopeId(UUID id) {
        return spec(id).map(ApiAutomationSpec::projectId);
    }

    @Override
    public Optional<String> generationTaskProjectScopeId(UUID id) {
        return generationTask(id).map(ApiAutomationGenerationTask::projectId);
    }

    private Stream<ApiAutomationSpec> filteredSpecs(ApiAutomationSpecQuery query) {
        Stream<ApiAutomationSpec> stream = specs.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(spec -> query.projectId().equals(spec.projectId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(spec -> query.status().equals(spec.status()));
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().toLowerCase();
            stream = stream.filter(spec -> contains(spec.name(), keyword)
                    || contains(spec.versionLabel(), keyword)
                    || contains(spec.sourceRef(), keyword));
        }
        return stream.sorted(Comparator.comparing(ApiAutomationSpec::createdAt).reversed());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
