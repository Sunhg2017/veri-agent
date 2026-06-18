package com.songhg.veri.agent.uie2e.application.port;

import com.songhg.veri.agent.uie2e.application.query.UiE2eSceneQuery;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UiE2eRepository {

    void insertScene(UiE2eScene scene);

    void updateScene(UiE2eScene scene);

    void archiveScene(UiE2eScene scene);

    Optional<UiE2eScene> scene(UUID id);

    Optional<UiE2eScene> sceneByProjectAndCode(String projectId, String code);

    List<UiE2eScene> scenes(UiE2eSceneQuery query);

    long countScenes(UiE2eSceneQuery query);

    Optional<String> sceneProjectScopeId(UUID id);

    void replaceSceneSteps(UUID sceneId, List<UiE2eSceneStep> steps);

    List<UiE2eSceneStep> sceneSteps(UUID sceneId);
}
