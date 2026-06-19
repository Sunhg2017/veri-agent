package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.uie2e.application.query.UiE2eBundleQuery;
import com.songhg.veri.agent.uie2e.application.query.UiE2eFlakyMarkQuery;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunQuery;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.domain.UiE2eArtifactManifest;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundleReview;
import com.songhg.veri.agent.uie2e.domain.UiE2eFlakyMark;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import com.songhg.veri.agent.uie2e.domain.UiE2eRunStepResult;
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

    @Override
    public void insertBundle(UiE2eBundle bundle) {
        mapper.insertBundle(bundle);
    }

    @Override
    public void updateBundle(UiE2eBundle bundle) {
        mapper.updateBundle(bundle);
    }

    @Override
    public Optional<UiE2eBundle> bundle(UUID id) {
        return Optional.ofNullable(mapper.bundle(id));
    }

    @Override
    public Optional<UiE2eBundle> activeBundleBySceneAndDigest(UUID sceneId, String bundleDigest) {
        return Optional.ofNullable(mapper.activeBundleBySceneAndDigest(sceneId, bundleDigest));
    }

    @Override
    public List<UiE2eBundle> bundles(UiE2eBundleQuery query) {
        return mapper.bundles(query);
    }

    @Override
    public long countBundles(UiE2eBundleQuery query) {
        return mapper.countBundles(query);
    }

    @Override
    public List<UiE2eBundle> sceneBundles(UUID sceneId) {
        return mapper.sceneBundles(sceneId);
    }

    @Override
    public Optional<String> bundleProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.bundleProjectScopeId(id));
    }

    @Override
    public void insertBundleReview(UiE2eBundleReview review) {
        mapper.insertBundleReview(review);
    }

    @Override
    public List<UiE2eBundleReview> bundleReviews(UUID bundleId) {
        return mapper.bundleReviews(bundleId);
    }

    @Override
    public void insertRun(UiE2eRun run) {
        mapper.insertRun(run);
    }

    @Override
    public void updateRun(UiE2eRun run) {
        mapper.updateRun(run);
    }

    @Override
    public Optional<UiE2eRun> run(UUID id) {
        return Optional.ofNullable(mapper.run(id));
    }

    @Override
    public Optional<UiE2eRun> runByProjectSceneAndRequestKey(String projectId, UUID sceneId, String requestKey) {
        return Optional.ofNullable(mapper.runByProjectSceneAndRequestKey(projectId, sceneId, requestKey));
    }

    @Override
    public List<UiE2eRun> runs(UiE2eRunQuery query) {
        return mapper.runs(query);
    }

    @Override
    public long countRuns(UiE2eRunQuery query) {
        return mapper.countRuns(query);
    }

    @Override
    public Optional<String> runProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.runProjectScopeId(id));
    }

    @Override
    public void replaceRunStepResults(UUID runId, List<UiE2eRunStepResult> stepResults) {
        mapper.deleteRunStepResults(runId);
        if (stepResults == null || stepResults.isEmpty()) {
            return;
        }
        stepResults.forEach(mapper::insertRunStepResult);
    }

    @Override
    public List<UiE2eRunStepResult> runStepResults(UUID runId) {
        return mapper.runStepResults(runId);
    }

    @Override
    public void replaceArtifacts(UUID runId, List<UiE2eArtifactManifest> manifests) {
        mapper.deleteArtifacts(runId);
        if (manifests == null || manifests.isEmpty()) {
            return;
        }
        manifests.forEach(mapper::insertArtifact);
    }

    @Override
    public List<UiE2eArtifactManifest> artifacts(UUID runId) {
        return mapper.artifacts(runId);
    }

    @Override
    public void upsertFlakyMark(UiE2eFlakyMark flakyMark) {
        if (flakyMark.runId() != null && mapper.flakyMarkByRun(flakyMark.runId()) != null) {
            mapper.updateFlakyMarkByRun(flakyMark);
            return;
        }
        if (flakyMark.sceneId() != null && mapper.flakyMarkByScene(flakyMark.sceneId()) != null) {
            mapper.updateFlakyMarkByScene(flakyMark);
            return;
        }
        mapper.insertFlakyMark(flakyMark);
    }

    @Override
    public Optional<UiE2eFlakyMark> flakyMark(UUID id) {
        return Optional.ofNullable(mapper.flakyMark(id));
    }

    @Override
    public Optional<UiE2eFlakyMark> flakyMarkByScene(UUID sceneId) {
        return Optional.ofNullable(mapper.flakyMarkByScene(sceneId));
    }

    @Override
    public Optional<UiE2eFlakyMark> flakyMarkByRun(UUID runId) {
        return Optional.ofNullable(mapper.flakyMarkByRun(runId));
    }

    @Override
    public List<UiE2eFlakyMark> flakyMarks(UiE2eFlakyMarkQuery query) {
        return mapper.flakyMarks(query);
    }

    @Override
    public long countFlakyMarks(UiE2eFlakyMarkQuery query) {
        return mapper.countFlakyMarks(query);
    }
}
