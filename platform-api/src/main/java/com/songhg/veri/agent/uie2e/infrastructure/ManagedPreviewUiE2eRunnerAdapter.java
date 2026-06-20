package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.TestDataRunnerCredentialResolver;
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
    private static final String UNSUPPORTED_CREDENTIAL_FORMAT = "UI_E2E_CREDENTIAL_FORMAT_UNSUPPORTED";
    private static final String ACCOUNT_PASSWORD_SCHEMA = "wp7-account-password-v1";
    private static final String LOGIN_FORM_SCHEMA = "wp7-login-form-v1";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UiE2eRepository repository;
    private final UiE2eProperties properties;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManagedPreviewUiE2eRunnerAdapter(
            UiE2eRepository repository,
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService
    ) {
        this.repository = repository;
        this.properties = properties;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
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
            return blockedResult("EXECUTION_RUNNER_NOT_READY", "runner preview request is incomplete", List.of(), List.of());
        }
        UiE2eScene scene = repository.scene(request.sceneId()).orElse(null);
        UiE2eBundle bundle = repository.bundle(request.bundleId()).orElse(null);
        if (scene == null || bundle == null) {
            return blockedResult(
                    "EXECUTION_RUNNER_NOT_READY",
                    "runner preview dependencies are missing",
                    List.of(),
                    previewArtifacts(request)
            );
        }
        CredentialPreviewState credentialState = resolveCredentialState(request);
        if (!credentialState.ready()) {
            return blockedResult(
                    credentialState.failureCode(),
                    credentialState.failureSummary(),
                    previewStepResults(
                            repository.sceneSteps(scene.id()),
                            request,
                            credentialState.ready(),
                            credentialState.resolution(),
                            credentialState.plan(),
                            credentialState.failureCode()
                    ),
                    previewArtifacts(request)
            );
        }
        List<UiE2eSceneStep> sceneSteps = repository.sceneSteps(scene.id());
        List<RunnerStepResult> stepResults = previewStepResults(
                sceneSteps,
                request,
                credentialState.ready(),
                credentialState.resolution(),
                credentialState.plan(),
                EXECUTION_NOT_READY
        );
        return blockedResult(
                EXECUTION_NOT_READY,
                "managed preview built a redacted credential injection plan, but browser execution is not ready yet",
                stepResults,
                previewArtifacts(request)
        );
    }

    @Override
    public RunnerCancelResult cancel(UUID runId) {
        return new RunnerCancelResult(false, EXECUTION_NOT_READY, "preview runner persists terminal blocked snapshots only");
    }

    private RunnerRunResult blockedResult(
            String failureCode,
            String failureSummary,
            List<RunnerStepResult> stepResults,
            List<RunnerArtifactManifest> artifacts
    ) {
        return new RunnerRunResult(
                "BLOCKED",
                runnerMode(),
                failureCode,
                SensitiveTextSanitizer.sanitizedEvidenceText(failureSummary, 256),
                stepResults,
                artifacts
        );
    }

    private List<RunnerStepResult> previewStepResults(
            List<UiE2eSceneStep> sceneSteps,
            RunnerRunRequest request,
            boolean credentialReady,
            TestDataRunnerCredentialResolver.RunnerCredentialResolution credentialResolution,
            RunnerCredentialInjectionPlan credentialPlan,
            String blockerErrorCode
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
            summary.put("credentialInjectionReady", credentialReady);
            summary.put("rawDomStored", false);
            summary.put("rawRunnerOutputStored", false);
            summary.put("secretPlaintextStored", false);
            summary.put("baseUrlDigest", SensitiveTextSanitizer.sha256Hex(request.baseUrl()));
            summary.put("accountLeaseRefPresent", StringUtils.hasText(request.accountLeaseRef()));
            summary.put("secretRefDigestPresent", credentialDigestPresent(request.accountSummary()));
            summary.put("secretProviderResolved", credentialResolution != null);
            summary.put("credentialPlanReady", credentialPlan != null);
            if (credentialResolution != null) {
                summary.put("secretProviderCode", credentialResolution.provider());
                summary.put("secretVersion", credentialResolution.version());
            }
            if (credentialPlan != null) {
                summary.put("credentialPlanType", credentialPlan.planType());
                summary.put("credentialFormat", credentialPlan.format());
                summary.put("credentialSchemaId", credentialPlan.schemaId());
                summary.put("principalSource", credentialPlan.principalSource());
                summary.put("principalIdentifierPresent", credentialPlan.principalIdentifierPresent());
                summary.put("credentialComponentCount", credentialPlan.componentCount());
            }
            if (primaryBlocker) {
                summary.put("blockedReason", primaryBlockedReason(credentialResolution, credentialPlan));
                summary.put("nextAction", nextAction(credentialResolution, credentialPlan));
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
                    blockerErrorCode,
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

    private boolean credentialInjectionReady() {
        return testDataCrossWpReferenceService != null && testDataCrossWpReferenceService.runnerCredentialInjectionReady();
    }

    private CredentialPreviewState resolveCredentialState(RunnerRunRequest request) {
        if (testDataCrossWpReferenceService == null || !credentialInjectionReady()) {
            return new CredentialPreviewState(
                    false,
                    null,
                    null,
                    EXECUTION_NOT_READY,
                    "managed preview is enabled, but credential injection adapter is not ready yet"
            );
        }
        if (request == null || !StringUtils.hasText(request.accountLeaseRef()) || !StringUtils.hasText(request.projectId())) {
            return new CredentialPreviewState(
                    false,
                    null,
                    null,
                    "UI_E2E_ACCOUNT_LEASE_INVALID",
                    "runner preview account lease is incomplete"
            );
        }
        try {
            TestDataRunnerCredentialResolver.RunnerCredentialResolution resolution =
                    testDataCrossWpReferenceService.resolveRunnerCredential(
                            UUID.fromString(request.accountLeaseRef()),
                            request.projectId()
                    );
            return resolvedCredentialState(request, resolution);
        } catch (IllegalArgumentException exception) {
            return new CredentialPreviewState(
                    false,
                    null,
                    null,
                    "UI_E2E_ACCOUNT_LEASE_INVALID",
                    "runner preview account lease is invalid"
            );
        } catch (BusinessException exception) {
            return new CredentialPreviewState(
                    false,
                    null,
                    null,
                    "SECRET_PROVIDER_ERROR",
                    "runner credential resolution failed"
            );
        } catch (RuntimeException exception) {
            return new CredentialPreviewState(
                    false,
                    null,
                    null,
                    "SECRET_PROVIDER_ERROR",
                    "runner credential resolution failed"
            );
        }
    }

    /**
     * The preview runner now exercises the same runner-only credential planning boundary the future browser executor
     * will consume. Plaintext still stays inside the adapter and is reduced to aggregate-only readiness metadata.
     */
    private CredentialPreviewState resolvedCredentialState(
            RunnerRunRequest request,
            TestDataRunnerCredentialResolver.RunnerCredentialResolution resolution
    ) {
        try {
            return new CredentialPreviewState(
                    true,
                    resolution,
                    buildCredentialPlan(request, resolution),
                    null,
                    null
            );
        } catch (BusinessException exception) {
            if (UNSUPPORTED_CREDENTIAL_FORMAT.equals(exception.getMessage())) {
                return new CredentialPreviewState(
                        false,
                        resolution,
                        null,
                        UNSUPPORTED_CREDENTIAL_FORMAT,
                        "runner credential payload is not supported by managed preview"
                );
            }
            return new CredentialPreviewState(
                    false,
                    resolution,
                    null,
                    "SECRET_PROVIDER_ERROR",
                    "runner credential resolution failed"
            );
        }
    }

    private RunnerCredentialInjectionPlan buildCredentialPlan(
            RunnerRunRequest request,
            TestDataRunnerCredentialResolver.RunnerCredentialResolution resolution
    ) {
        // The managed preview accepts either a plain password paired with the leased accountKey or a small structured
        // login-form payload. Both stay process-local and are summarized only through non-sensitive readiness fields.
        String secretValue = resolution == null ? null : resolution.secretValue();
        if (!StringUtils.hasText(secretValue)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        String trimmedSecret = secretValue.trim();
        if (trimmedSecret.startsWith("{")) {
            return structuredCredentialPlan(trimmedSecret);
        }
        String accountKey = safeText(request == null || request.accountSummary() == null
                ? null
                : request.accountSummary().get("accountKey"));
        if (!StringUtils.hasText(accountKey)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        return new RunnerCredentialInjectionPlan(
                "FORM_LOGIN",
                "ACCOUNT_PASSWORD",
                ACCOUNT_PASSWORD_SCHEMA,
                "ACCOUNT_SUMMARY",
                true,
                2,
                accountKey,
                trimmedSecret
        );
    }

    private RunnerCredentialInjectionPlan structuredCredentialPlan(String secretValue) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(secretValue, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        String schemaId = safeText(payload.get("schema"));
        String normalizedSchema = StringUtils.hasText(schemaId) ? schemaId.trim() : LOGIN_FORM_SCHEMA;
        if (!LOGIN_FORM_SCHEMA.equalsIgnoreCase(normalizedSchema)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        String principal = firstPresent(payload, "username", "principal", "accountKey");
        String credential = firstPresent(payload, "password", "credential", "value");
        if (!StringUtils.hasText(principal) || !StringUtils.hasText(credential)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        return new RunnerCredentialInjectionPlan(
                "FORM_LOGIN",
                "STRUCTURED_LOGIN_FORM",
                LOGIN_FORM_SCHEMA,
                "SECRET_PAYLOAD",
                true,
                2,
                principal,
                credential
        );
    }

    private String firstPresent(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String text = safeText(payload.get(key));
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String safeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || text.contains("\r") || text.contains("\n")) {
            return null;
        }
        return SensitiveTextSanitizer.boundedText(text, 512);
    }

    private String primaryBlockedReason(
            TestDataRunnerCredentialResolver.RunnerCredentialResolution credentialResolution,
            RunnerCredentialInjectionPlan credentialPlan
    ) {
        if (credentialPlan != null) {
            return "browserExecutionPending";
        }
        if (credentialResolution != null) {
            return "credentialPlanUnsupported";
        }
        return "credentialInjectionPending";
    }

    private String nextAction(
            TestDataRunnerCredentialResolver.RunnerCredentialResolution credentialResolution,
            RunnerCredentialInjectionPlan credentialPlan
    ) {
        if (credentialPlan != null) {
            return "provision real browser runner before login";
        }
        if (credentialResolution != null) {
            return "align runner credential payload with the supported preview contract";
        }
        return "enable runner credential adapter before browser login";
    }

    private record CredentialPreviewState(
            boolean ready,
            TestDataRunnerCredentialResolver.RunnerCredentialResolution resolution,
            RunnerCredentialInjectionPlan plan,
            String failureCode,
            String failureSummary
    ) {
    }

    private record RunnerCredentialInjectionPlan(
            String planType,
            String format,
            String schemaId,
            String principalSource,
            boolean principalIdentifierPresent,
            int componentCount,
            String principalIdentifier,
            String credentialValue
    ) {
        @Override
        public String toString() {
            return "RunnerCredentialInjectionPlan[planType=%s, format=%s, schemaId=%s, principalSource=%s, componentCount=%s, principalIdentifier=****, credentialValue=****]"
                    .formatted(planType, format, schemaId, principalSource, componentCount);
        }
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
