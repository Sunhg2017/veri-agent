package com.songhg.veri.agent.uie2e.infrastructure.mapper;

import com.songhg.veri.agent.uie2e.application.query.UiE2eBundleQuery;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundleReview;
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

    void insertBundle(UiE2eBundle bundle);

    void updateBundle(UiE2eBundle bundle);

    UiE2eBundle bundle(@Param("id") UUID id);

    UiE2eBundle activeBundleBySceneAndDigest(@Param("sceneId") UUID sceneId, @Param("bundleDigest") String bundleDigest);

    List<UiE2eBundle> bundles(@Param("query") UiE2eBundleQuery query);

    long countBundles(@Param("query") UiE2eBundleQuery query);

    List<UiE2eBundle> sceneBundles(@Param("sceneId") UUID sceneId);

    String bundleProjectScopeId(@Param("id") UUID id);

    void insertBundleReview(UiE2eBundleReview review);

    List<UiE2eBundleReview> bundleReviews(@Param("bundleId") UUID bundleId);
}
