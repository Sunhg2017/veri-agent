package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.view.ExecutionRunArtifactResponse;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.uie2e.application.UiE2eRunService;
import com.songhg.veri.agent.uie2e.application.view.UiE2eArtifactManifestResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Builds one orchestration-level artifact view from downstream runner manifests without exposing raw storage refs.
 *
 * <p>WP9 currently federates runner artifacts from WP7 so callers can inspect and download evidence through one
 * execution-run surface. When a downstream provider is disabled or unavailable, the manifest list simply degrades to
 * empty instead of failing the whole run detail request.</p>
 */
final class ExecutionRunArtifactSupport {

    private static final String SOURCE_TYPE_WP7_UI_E2E = "WP7_UI_E2E";

    private final UiE2eRunService uiE2eRunService;

    ExecutionRunArtifactSupport(UiE2eRunService uiE2eRunService) {
        this.uiE2eRunService = uiE2eRunService;
    }

    List<ExecutionRunArtifactResponse> artifacts(List<ExecutionNodeRun> nodeRuns, List<ExecutionPlanNode> planNodes) {
        return artifactRefs(nodeRuns, planNodes).stream()
                .map(FederatedArtifactRef::response)
                .toList();
    }

    DownloadableArtifact downloadArtifact(UUID artifactId, List<ExecutionNodeRun> nodeRuns, List<ExecutionPlanNode> planNodes) {
        FederatedArtifactRef artifact = artifactRefs(nodeRuns, planNodes).stream()
                .filter(item -> artifactId.equals(item.response().id()))
                .findFirst()
                .orElseThrow(this::artifactNotFound);
        return switch (artifact.sourceType()) {
            case SOURCE_TYPE_WP7_UI_E2E -> downloadWp7Artifact(artifact);
            default -> throw artifactNotFound();
        };
    }

    /**
     * Resolves manifests per node attempt so retry histories keep their own evidence trails instead of being collapsed
     * onto the latest node state.
     */
    private List<FederatedArtifactRef> artifactRefs(List<ExecutionNodeRun> nodeRuns, List<ExecutionPlanNode> planNodes) {
        if (nodeRuns == null || nodeRuns.isEmpty()) {
            return List.of();
        }
        Map<UUID, ExecutionPlanNode> planNodeById = planNodes == null
                ? Map.of()
                : planNodes.stream().collect(Collectors.toMap(ExecutionPlanNode::id, Function.identity()));
        List<FederatedArtifactRef> refs = new ArrayList<>();
        for (ExecutionNodeRun nodeRun : nodeRuns) {
            refs.addAll(wp7ArtifactRefs(nodeRun, planNodeById.get(nodeRun.planNodeId())));
        }
        refs.sort(Comparator
                .comparing((FederatedArtifactRef ref) -> safeSortKey(ref.response().nodeKey()))
                .thenComparing(FederatedArtifactRef::nodeAttempt)
                .thenComparing(ref -> ref.response().createdAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ref -> ref.response().id()));
        return List.copyOf(refs);
    }

    private List<FederatedArtifactRef> wp7ArtifactRefs(ExecutionNodeRun nodeRun, ExecutionPlanNode planNode) {
        if (uiE2eRunService == null
                || nodeRun == null
                || !"WP7_UI".equals(nodeRun.runnerType())
                || !StringUtils.hasText(nodeRun.externalRunId())) {
            return List.of();
        }
        UUID sourceRunId = parseUuid(nodeRun.externalRunId());
        if (sourceRunId == null) {
            return List.of();
        }
        try {
            UiE2eRunDetailResponse wp7Run = uiE2eRunService.run(sourceRunId);
            if (wp7Run == null || wp7Run.artifacts() == null || wp7Run.artifacts().isEmpty()) {
                return List.of();
            }
            return wp7Run.artifacts().stream()
                    .map(artifact -> wp7ArtifactRef(nodeRun, planNode, sourceRunId, artifact))
                    .toList();
        } catch (BusinessException exception) {
            return List.of();
        }
    }

    private FederatedArtifactRef wp7ArtifactRef(
            ExecutionNodeRun nodeRun,
            ExecutionPlanNode planNode,
            UUID sourceRunId,
            UiE2eArtifactManifestResponse artifact
    ) {
        Map<String, Object> redactionFlags = artifact.redactionFlags() == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(artifact.redactionFlags()));
        boolean downloadReady = Boolean.TRUE.equals(redactionFlags.get("rawArtifactDownloadReady"));
        return new FederatedArtifactRef(
                new ExecutionRunArtifactResponse(
                        deterministicArtifactId(nodeRun.id(), SOURCE_TYPE_WP7_UI_E2E, artifact.id()),
                        nodeRun.id(),
                        nodeRun.planNodeId(),
                        planNode == null ? null : planNode.nodeKey(),
                        planNode == null ? null : planNode.nodeType(),
                        nodeRun.runnerType(),
                        SOURCE_TYPE_WP7_UI_E2E,
                        artifact.artifactType(),
                        artifact.artifactDigest(),
                        artifact.sizeBytes(),
                        artifact.captureStatus(),
                        downloadReady,
                        redactionFlags,
                        artifact.createdAt(),
                        artifact.updatedAt()
                ),
                SOURCE_TYPE_WP7_UI_E2E,
                sourceRunId,
                artifact.id(),
                nodeRun.attempt()
        );
    }

    private DownloadableArtifact downloadWp7Artifact(FederatedArtifactRef artifact) {
        if (uiE2eRunService == null || artifact.sourceRunId() == null || artifact.sourceArtifactId() == null) {
            throw artifactDownloadNotReady();
        }
        try {
            UiE2eRunService.DownloadableArtifact content = uiE2eRunService.downloadArtifact(
                    artifact.sourceRunId(),
                    artifact.sourceArtifactId()
            );
            return new DownloadableArtifact(
                    artifact.response().id(),
                    artifact.response().nodeRunId(),
                    artifact.response().runnerType(),
                    artifact.response().artifactType(),
                    content.fileName(),
                    content.contentType(),
                    content.content()
            );
        } catch (BusinessException exception) {
            throw artifactDownloadNotReady();
        }
    }

    private UUID deterministicArtifactId(UUID nodeRunId, String sourceType, UUID sourceArtifactId) {
        String value = String.valueOf(nodeRunId) + ":" + sourceType + ":" + sourceArtifactId;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String safeSortKey(String value) {
        return StringUtils.hasText(value) ? value : "~";
    }

    private BusinessException artifactNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "执行运行产物不存在");
    }

    private BusinessException artifactDownloadNotReady() {
        return new BusinessException(ErrorCode.NOT_FOUND, "EXECUTION_RUN_ARTIFACT_DOWNLOAD_NOT_READY");
    }

    record DownloadableArtifact(
            UUID artifactId,
            UUID nodeRunId,
            String runnerType,
            String artifactType,
            String fileName,
            String contentType,
            byte[] content
    ) {
    }

    private record FederatedArtifactRef(
            ExecutionRunArtifactResponse response,
            String sourceType,
            UUID sourceRunId,
            UUID sourceArtifactId,
            int nodeAttempt
    ) {
    }
}
