package com.songhg.veri.agent.uie2e.infrastructure.mapper;

import com.songhg.veri.agent.uie2e.application.query.UiE2eSceneQuery;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UiE2eMapper {

    void insertScene(UiE2eScene scene);

    void updateScene(UiE2eScene scene);

    void archiveScene(UiE2eScene scene);

    UiE2eScene scene(@Param("id") UUID id);

    UiE2eScene sceneByProjectAndCode(@Param("projectId") String projectId, @Param("code") String code);

    List<UiE2eScene> scenes(@Param("query") UiE2eSceneQuery query);

    long countScenes(@Param("query") UiE2eSceneQuery query);

    String sceneProjectScopeId(@Param("id") UUID id);

    List<UiE2eSceneStep> sceneSteps(@Param("sceneId") UUID sceneId);

    void insertSceneStep(UiE2eSceneStep step);

    void deleteSceneSteps(@Param("sceneId") UUID sceneId);
}
