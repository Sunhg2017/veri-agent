package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * WP7 managed preview runner persists aggregate-only, redacted execution intent so the control plane can exercise the
 * enabled path before real browser execution, raw artifact storage and credential injection land.
 */
public class ManagedPreviewUiE2eRunnerAdapter implements UiE2eRunnerPort {

    private static final String EXECUTION_NOT_READY = "EXECUTION_RUNNER_NOT_READY";

    private final UiE2eRepository repository;
    private final UiE2eProperties properties;

    public ManagedPreviewUiE2eRunnerAdapter(UiE2eRepository repository, UiE2eProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Defends the enabled runner path with the same scene, bundle and scope checks the future real runner will rely on.
     */
    @Override
    public RunnerValidation validate(RunnerValidationRequest request) {
        if (request == null
                || request.sceneId() == null
                || request.bundleId() == null
                || !StringUtils.hasText(request.projectId())
                || !StringUtils.hasText(request.baseUrl())
                || !StringUtils.hasText(request.accountLeaseRef())) {
            return new RunnerValidation(false, EXECUTION_NOT_READY, "runner preview request is incomplete");
        }
        UiE2eScene scene = repository.scene(request.sceneId()).orElse(null);
        if (scene == null || !"APPROVED".equals(scene.status()) || !request.projectId().equals(scene.projectId())) {
            return new RunnerValidation(false, "UI_E2E_SCENE_NOT_READY", "scene is not executable");
        }
        UiE2eBundle bundle = repository.bundle(request.bundleId()).orElse(null);
        if (bundle == null
                || !"APPROVED".equals(bundle.status())
                || !request.projectId().equals(bundle.projectId())
                || !request.sceneId().equals(bundle.sceneId())) {
            return new RunnerValidation(false, "UI_E2E_BUNDLE_NOT_READY", "bundle is not executable");
        }
        if (!credentialDigestPresent(request.accountSummary())) {
            return new RunnerValidation(false, "UI_E2E_ACCOUNT_LEASE_INVALID", "account contract is incomplete");
        }
        return new RunnerValidation(true, null, null);
    }

    /**
     * The preview mode intentionally stops before browser login. It returns blocked step diagnostics and manifest
     * placeholders so operators can verify runner selection, audit, idempotency and downstream evidence contracts.
     */
    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        if (request == null || request.sceneId() == null || request.bundleId() == null) {
            return blockedResult("runner preview request is incomplete", List.of(), List.of());
        }
        UiE2eScene scene = repository.scene(request.sceneId()).orElse(null);
        UiE2eBundle bundle = repository.bundle(request.bundleId()).orElse(null);
        if (scene == null || bundle == null) {
            return blockedResult("runner preview dependencies are missing", List.of(), previewArtifacts(request));
        }
        List<UiE2eSceneStep> sceneSteps = repository.sceneSteps(scene.id());
        List<RunnerStepResult> stepResults = previewStepResults(sceneSteps, request);
        return blockedResult(
                "managed preview is enabled, but credential injection and browser execution are not ready yet",
                stepResults,
                previewArtifacts(request)
        );
    }

    @Override
    public RunnerCancelResult cancel(UUID runId) {
        return new RunnerCancelResult(false, EXECUTION_NOT_READY, "preview runner persists terminal blocked snapshots only");
    }

    private RunnerRunResult blockedResult(
            String failureSummary,
            List<RunnerStepResult> stepResults,
            List<RunnerArtifactManifest> artifacts
    ) {
        return new RunnerRunResult(
                "BLOCKED",
                runnerMode(),
                EXECUTION_NOT_READY,
                SensitiveTextSanitizer.sanitizedEvidenceText(failureSummary, 256),
                stepResults,
                artifacts
        );
    }

    private List<RunnerStepResult> previewStepResults(
            List<UiE2eSceneStep> sceneSteps,
            RunnerRunRequest request
    ) {
        if (sceneSteps == null || sceneSteps.isEmpty()) {
            return List.of();
        }
        int blockerOrder = blockerStepOrder(sceneSteps);
        List<RunnerStepResult> results = new ArrayList<>();
        for (UiE2eSceneStep step : sceneSteps) {
            boolean primaryBlocker = step.stepOrder() == blockerOrder;
            String normalizedType = normalizedStepType(step.stepType());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("stepType", normalizedType);
            summary.put("previewOnly", true);
            summary.put("credentialInjectionReady", false);
            summary.put("rawDomStored", false);
            summary.put("rawRunnerOutputStored", false);
            summary.put("secretPlaintextStored", false);
            summary.put("baseUrlDigest", SensitiveTextSanitizer.sha256Hex(request.baseUrl()));
            summary.put("accountLeaseRefPresent", StringUtils.hasText(request.accountLeaseRef()));
            summary.put("secretRefDigestPresent", credentialDigestPresent(request.accountSummary()));
            if (primaryBlocker) {
                summary.put("blockedReason", "credentialInjectionPending");
                summary.put("nextAction", "enable runner credential adapter before browser login");
            } else {
                summary.put("blockedReason", "upstreamStepBlocked");
                summary.put("blockedByStepOrder", blockerOrder);
            }
            results.add(new RunnerStepResult(
                    step.id(),
                    step.stepOrder(),
                    "BLOCKED",
                    0,
                    primaryBlocker && "LOGIN".equals(normalizedType) ? "ACCOUNT" : "RUNNER",
                    EXECUTION_NOT_READY,
                    Map.copyOf(summary)
            ));
        }
        return results;
    }

    /**
     * LOGIN is the earliest place where the future runtime needs leased credentials. If a scene omits LOGIN, the
     * preview still blocks deterministically on the first executable step so downstream evidence stays stable.
     */
    private int blockerStepOrder(List<UiE2eSceneStep> sceneSteps) {
        return sceneSteps.stream()
                .filter(step -> "LOGIN".equals(normalizedStepType(step.stepType())))
                .map(UiE2eSceneStep::stepOrder)
                .findFirst()
                .orElseGet(() -> sceneSteps.stream()
                        .map(UiE2eSceneStep::stepOrder)
                        .min(Integer::compareTo)
                        .orElse(1));
    }

    private List<RunnerArtifactManifest> previewArtifacts(RunnerRunRequest request) {
        List<RunnerArtifactManifest> artifacts = new ArrayList<>();
        artifacts.add(new RunnerArtifactManifest(
                "LOG",
                "summary://ui-e2e/" + safeRunSegment(request == null ? null : request.runId()) + "/managed-preview",
                digest("preview-log", request),
                previewLogSize(request),
                Map.of(
                        "aggregateOnly", true,
                        "previewOnly", true,
                        "rawArtifactStored", false,
                        "rawArtifactDownloadReady", false,
                        "captureSummaryOnly", true
                ),
                "CAPTURED"
        ));
        if (properties.captureScreenshotEnabled()) {
            artifacts.add(blockedArtifact("SCREENSHOT"));
        }
        if (properties.captureTraceEnabled()) {
            artifacts.add(blockedArtifact("TRACE"));
        }
        if (properties.captureVideoEnabled()) {
            artifacts.add(blockedArtifact("VIDEO"));
        }
        return List.copyOf(artifacts);
    }

    private RunnerArtifactManifest blockedArtifact(String artifactType) {
        return new RunnerArtifactManifest(
                artifactType,
                null,
                null,
                0,
                Map.of(
                        "aggregateOnly", true,
                        "previewOnly", true,
                        "rawArtifactStored", false,
                        "rawArtifactDownloadReady", false,
                        "captureBlockedReason", "browserExecutionNotProvisioned"
                ),
                "BLOCKED"
        );
    }

    private long previewLogSize(RunnerRunRequest request) {
        int baseLength = 0;
        if (request != null && StringUtils.hasText(request.baseUrl())) {
            baseLength = request.baseUrl().getBytes(StandardCharsets.UTF_8).length;
        }
        return Math.min(baseLength + 96L, properties.effectiveMaxArtifactSizeBytes());
    }

    private String digest(String prefix, RunnerRunRequest request) {
        String runId = request == null || request.runId() == null ? "no-run-id" : request.runId().toString();
        String sceneId = request == null || request.sceneId() == null ? "no-scene-id" : request.sceneId().toString();
        String bundleId = request == null || request.bundleId() == null ? "no-bundle-id" : request.bundleId().toString();
        return SensitiveTextSanitizer.sha256Hex(prefix + ":" + runId + ":" + sceneId + ":" + bundleId);
    }

    private boolean credentialDigestPresent(Map<String, Object> accountSummary) {
        if (accountSummary == null || accountSummary.isEmpty()) {
            return false;
        }
        Object digest = accountSummary.get("secretRefDigest");
        return digest instanceof String text && StringUtils.hasText(text);
    }

    private String normalizedStepType(String stepType) {
        return StringUtils.hasText(stepType) ? stepType.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private String runnerMode() {
        return "http-adapter".equals(properties.effectiveRunnerMode()) ? "HTTP_ADAPTER" : "MANAGED";
    }

    private String safeRunSegment(UUID runId) {
        return runId == null ? "pending" : runId.toString();
    }
}
