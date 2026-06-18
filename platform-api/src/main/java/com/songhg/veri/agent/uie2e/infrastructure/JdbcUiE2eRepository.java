package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eSceneQuery;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import com.songhg.veri.agent.uie2e.infrastructure.mapper.UiE2eMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Repository
public class JdbcUiE2eRepository implements UiE2eRepository {

    private final UiE2eMapper mapper;

    public JdbcUiE2eRepository(UiE2eMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertScene(UiE2eScene scene) {
        mapper.insertScene(scene);
    }

    @Override
    public void updateScene(UiE2eScene scene) {
        mapper.updateScene(scene);
    }

    @Override
    public void archiveScene(UiE2eScene scene) {
        mapper.archiveScene(scene);
    }

    @Override
    public Optional<UiE2eScene> scene(UUID id) {
        return Optional.ofNullable(mapper.scene(id));
    }

    @Override
    public Optional<UiE2eScene> sceneByProjectAndCode(String projectId, String code) {
        return Optional.ofNullable(mapper.sceneByProjectAndCode(projectId, code));
    }

    @Override
    public List<UiE2eScene> scenes(UiE2eSceneQuery query) {
        return mapper.scenes(query);
    }

    @Override
    public long countScenes(UiE2eSceneQuery query) {
        return mapper.countScenes(query);
    }

    @Override
    public Optional<String> sceneProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.sceneProjectScopeId(id));
    }

    @Override
    @Transactional
    public void replaceSceneSteps(UUID sceneId, List<UiE2eSceneStep> steps) {
        mapper.deleteSceneSteps(sceneId);
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (UiE2eSceneStep step : steps) {
            mapper.insertSceneStep(step);
        }
    }

    @Override
    public List<UiE2eSceneStep> sceneSteps(UUID sceneId) {
        return mapper.sceneSteps(sceneId);
    }
}
