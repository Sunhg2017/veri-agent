package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eSceneQuery;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryUiE2eRepository implements UiE2eRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<UUID, UiE2eScene> scenes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UiE2eSceneStep> steps = new ConcurrentHashMap<>();

    @Override
    public void insertScene(UiE2eScene scene) {
        if (sceneByProjectAndCode(scene.projectId(), scene.code()).isPresent()) {
            throw new DuplicateKeyException("Duplicate ui-e2e scene code");
        }
        scenes.put(scene.id(), scene);
    }

    @Override
    public void updateScene(UiE2eScene scene) {
        scenes.computeIfPresent(scene.id(), (ignored, current) -> "ARCHIVED".equals(current.status()) ? current : scene);
    }

    @Override
    public void archiveScene(UiE2eScene scene) {
        scenes.computeIfPresent(scene.id(), (ignored, current) -> "ARCHIVED".equals(current.status()) ? current : scene);
    }

    @Override
    public Optional<UiE2eScene> scene(UUID id) {
        return Optional.ofNullable(scenes.get(id));
    }

    @Override
    public Optional<UiE2eScene> sceneByProjectAndCode(String projectId, String code) {
        return scenes.values().stream()
                .filter(scene -> projectId.equals(scene.projectId()))
                .filter(scene -> code.equals(scene.code()))
                .findFirst();
    }

    @Override
    public List<UiE2eScene> scenes(UiE2eSceneQuery query) {
        return filteredScenes(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countScenes(UiE2eSceneQuery query) {
        return filteredScenes(query).count();
    }

    @Override
    public Optional<String> sceneProjectScopeId(UUID id) {
        return scene(id).map(UiE2eScene::projectId);
    }

    @Override
    public void replaceSceneSteps(UUID sceneId, List<UiE2eSceneStep> newSteps) {
        steps.entrySet().removeIf(entry -> sceneId.equals(entry.getValue().sceneId()));
        if (newSteps == null || newSteps.isEmpty()) {
            return;
        }
        for (UiE2eSceneStep step : newSteps) {
            steps.put(stepKey(sceneId, step.stepOrder()), step);
        }
    }

    @Override
    public List<UiE2eSceneStep> sceneSteps(UUID sceneId) {
        return steps.values().stream()
                .filter(step -> sceneId.equals(step.sceneId()))
                .sorted(Comparator.comparingInt(UiE2eSceneStep::stepOrder))
                .toList();
    }

    private Stream<UiE2eScene> filteredScenes(UiE2eSceneQuery query) {
        return scenes.values().stream()
                .filter(scene -> query.projectId() == null || query.projectId().equals(scene.projectId()))
                .filter(scene -> query.applicationId() == null || query.applicationId().equals(scene.applicationId()))
                .filter(scene -> query.environmentId() == null || query.environmentId().equals(scene.environmentId()))
                .filter(scene -> query.status() == null || query.status().equals(scene.status()))
                .filter(scene -> query.riskLevel() == null || query.riskLevel().equals(scene.riskLevel()))
                .filter(scene -> query.tag() == null || containsTag(scene.tagsJson(), query.tag()))
                .filter(scene -> keywordMatches(scene, query.keyword()))
                .sorted(Comparator.comparing(UiE2eScene::updatedAt).reversed().thenComparing(UiE2eScene::id));
    }

    private boolean keywordMatches(UiE2eScene scene, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowered = keyword.toLowerCase(Locale.ROOT);
        return contains(scene.code(), lowered) || contains(scene.name(), lowered);
    }

    private boolean containsTag(String tagsJson, String expectedTag) {
        return readTags(tagsJson).stream().anyMatch(tag -> expectedTag.equalsIgnoreCase(tag));
    }

    private boolean contains(String value, String loweredKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(loweredKeyword);
    }

    private List<String> readTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(tagsJson, STRING_LIST_TYPE);
        } catch (Exception exception) {
            throw new UncheckedIOException(new java.io.IOException(exception));
        }
    }

    private String stepKey(UUID sceneId, int order) {
        return sceneId + ":" + order;
    }
}
