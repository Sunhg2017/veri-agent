package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.TestDataRunnerCredentialResolver;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import com.songhg.veri.agent.uie2e.infrastructure.UiE2eRunnerCredentialPlanSupport.RunnerCredentialInjectionPlan;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Bridges the WP7 control-plane contract to an isolated external HTTP worker. The adapter keeps secret resolution and
 * artifact persistence inside platform-api while allowing the actual browser runtime to live outside the host process.
 */
public class HttpWorkerUiE2eRunnerAdapter implements UiE2eRunnerPort {

    private static final String RUNNER_MODE = "HTTP_ADAPTER";
    private static final String EXECUTION_NOT_READY = "EXECUTION_RUNNER_NOT_READY";
    private static final String STEP_UNSUPPORTED = "UI_E2E_STEP_UNSUPPORTED";
    private static final String STEP_CONTRACT_INVALID = "UI_E2E_STEP_CONTRACT_INVALID";
    private static final String RUNNER_FAILED = "UI_E2E_RUNNER_FAILED";
    private static final String RUNNER_TIMEOUT = "UI_E2E_RUNNER_TIMEOUT";
    private static final String SECRET_PROVIDER_ERROR = "SECRET_PROVIDER_ERROR";
    private static final String WORKER_NOT_CONFIGURED = "UI_E2E_RUNNER_WORKER_NOT_CONFIGURED";
    private static final String WORKER_URL_INVALID = "UI_E2E_RUNNER_WORKER_URL_INVALID";
    private static final String WORKER_HTTP_FAILED = "UI_E2E_RUNNER_WORKER_HTTP_FAILED";
    private static final String CANCEL_NOT_CONFIGURED = "UI_E2E_RUNNER_CANCEL_NOT_CONFIGURED";
    private static final String CANCEL_FAILED = "UI_E2E_RUNNER_CANCEL_FAILED";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UiE2eRepository repository;
    private final UiE2eProperties properties;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;
    private final ObjectMapper objectMapper;
    private final UiE2eRunnerCredentialPlanSupport credentialPlanSupport;
    private final UiE2eArtifactStorage artifactStorage;
    private final HttpClient httpClient;

    public HttpWorkerUiE2eRunnerAdapter(
            UiE2eRepository repository,
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            UiE2eArtifactStorage artifactStorage
    ) {
        this(
                repository,
                properties,
                testDataCrossWpReferenceService,
                new ObjectMapper(),
                artifactStorage,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(properties.effectiveRunnerWorkerConnectTimeoutSeconds()))
                        .build()
        );
    }

    HttpWorkerUiE2eRunnerAdapter(
            UiE2eRepository repository,
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            ObjectMapper objectMapper,
            UiE2eArtifactStorage artifactStorage,
            HttpClient httpClient
    ) {
        this.repository = repository;
        this.properties = properties;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
        this.objectMapper = objectMapper;
        this.credentialPlanSupport = new UiE2eRunnerCredentialPlanSupport(objectMapper);
        this.artifactStorage = artifactStorage;
        this.httpClient = httpClient;
    }

    /**
     * Validates the same scene, bundle and credential readiness gates the local browser adapter uses before any remote
     * worker call is attempted. This keeps HTTP worker failures focused on runtime issues rather than contract drift.
     */
    @Override
    public RunnerValidation validate(RunnerValidationRequest request) {
        if (request == null
                || request.sceneId() == null
                || request.bundleId() == null
                || !StringUtils.hasText(request.projectId())
                || !StringUtils.hasText(request.baseUrl())
                || !StringUtils.hasText(request.accountLeaseRef())) {
            return new RunnerValidation(false, STEP_CONTRACT_INVALID, "runner request is incomplete");
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
        return validateRuntimePreparation(new RunnerRunRequest(
                null,
                request.sceneId(),
                request.bundleId(),
                request.projectId(),
                request.baseUrl(),
                request.accountLeaseRef(),
                request.accountSummary(),
                request.browserTypes(),
                request.visualRegressionEnabled(),
                request.baselineRunId(),
                request.visualMismatchThreshold()
        ), scene);
    }

    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        if (request == null || request.sceneId() == null || request.bundleId() == null) {
            return failure(
                    "BLOCKED",
                    STEP_CONTRACT_INVALID,
                    "runner request is incomplete",
                    List.of(),
                    blockedArtifacts("requestIncomplete")
            );
        }
        UiE2eScene scene = repository.scene(request.sceneId()).orElse(null);
        UiE2eBundle bundle = repository.bundle(request.bundleId()).orElse(null);
        if (scene == null || bundle == null) {
            return failure(
                    "BLOCKED",
                    EXECUTION_NOT_READY,
                    "runner dependencies are missing",
                    List.of(),
                    blockedArtifacts("runnerDependenciesMissing")
            );
        }
        RunnerValidation validation = validateRuntimePreparation(request, scene);
        if (!validation.accepted()) {
            return failure(
                    "BLOCKED",
                    validation.errorCode(),
                    validation.errorSummary(),
                    blockedSteps(repository.sceneSteps(scene.id()), validation.errorCode(), primaryFailureBucket(validation.errorCode())),
                    blockedArtifacts("runnerPreparationFailed")
            );
        }
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("wp7-http-worker-");
            RunnerCredentialInjectionPlan plan = credentialPlan(request);
            HttpResponse<String> response = sendRunRequest(request, repository.sceneSteps(scene.id()), plan);
            if (response.statusCode() / 100 != 2) {
                return failure(
                        "FAILED",
                        WORKER_HTTP_FAILED,
                        "external runner worker returned HTTP " + response.statusCode(),
                        failedSteps(repository.sceneSteps(scene.id()), "workerHttpFailed"),
                        blockedArtifacts("workerHttpFailed")
                );
            }
            return readPayload(response.body()).toResult(request.runId(), properties, workspace, artifactStorage);
        } catch (BusinessException exception) {
            String errorCode = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : RUNNER_FAILED;
            return failure(
                    "BLOCKED",
                    errorCode,
                    "runner preparation failed",
                    blockedSteps(repository.sceneSteps(scene.id()), errorCode, primaryFailureBucket(errorCode)),
                    blockedArtifacts("runnerPreparationFailed")
            );
        } catch (HttpTimeoutException exception) {
            return failure(
                    "TIMEOUT",
                    RUNNER_TIMEOUT,
                    "external runner worker timed out",
                    timeoutSteps(repository.sceneSteps(scene.id())),
                    blockedArtifacts("workerTimedOut")
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(
                    "CANCELED",
                    "UI_E2E_RUNNER_CANCELED",
                    "external runner worker was interrupted",
                    blockedSteps(repository.sceneSteps(scene.id()), "UI_E2E_RUNNER_CANCELED", "RUNNER"),
                    blockedArtifacts("workerInterrupted")
            );
        } catch (IOException exception) {
            return failure(
                    "FAILED",
                    RUNNER_FAILED,
                    "external runner worker call failed",
                    failedSteps(repository.sceneSteps(scene.id()), "workerIoFailed"),
                    blockedArtifacts("workerIoFailed")
            );
        } finally {
            deleteQuietly(workspace);
        }
    }

    /**
     * Cancellation stays best-effort because WP7 closes control-plane state regardless of downstream worker response.
     * The worker callback is still attempted when a cancel endpoint is configured so external compute can stop early.
     */
    @Override
    public RunnerCancelResult cancel(UUID runId) {
        if (runId == null) {
            return new RunnerCancelResult(false, CANCEL_FAILED, "runner cancel requires runId");
        }
        if (!properties.runnerWorkerCancelConfigured()) {
            return new RunnerCancelResult(false, CANCEL_NOT_CONFIGURED, "external runner worker cancel endpoint is not configured");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("runId", runId.toString()));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(properties.effectiveRunnerWorkerCancelUrl()))
                    .timeout(Duration.ofSeconds(properties.effectiveDefaultTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
            if (properties.runnerWorkerTokenConfigured()) {
                requestBuilder.header("Authorization", "Bearer " + properties.effectiveRunnerWorkerToken());
            }
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() / 100 != 2) {
                return new RunnerCancelResult(false, CANCEL_FAILED, "external runner worker cancel returned HTTP " + response.statusCode());
            }
            JsonNode root = readRoot(response.body());
            JsonNode payload = root.path("data").isObject() ? root.path("data") : root;
            boolean accepted = payload.path("accepted").asBoolean(true);
            String errorCode = textValue(payload, "errorCode");
            String errorSummary = textValue(payload, "errorSummary", "message");
            return new RunnerCancelResult(
                    accepted,
                    accepted ? null : SensitiveTextSanitizer.boundedNullableText(errorCode, 64),
                    SensitiveTextSanitizer.sanitizedEvidenceText(
                            accepted ? "external runner worker accepted cancellation" : errorSummary,
                            256
                    )
            );
        } catch (JsonProcessingException exception) {
            return new RunnerCancelResult(false, CANCEL_FAILED, "external runner worker cancel payload is invalid");
        } catch (HttpTimeoutException exception) {
            return new RunnerCancelResult(false, CANCEL_FAILED, "external runner worker cancel timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new RunnerCancelResult(false, CANCEL_FAILED, "external runner worker cancel was interrupted");
        } catch (IllegalArgumentException exception) {
            return new RunnerCancelResult(false, CANCEL_FAILED, "external runner worker cancel URL is invalid");
        } catch (IOException exception) {
            return new RunnerCancelResult(false, CANCEL_FAILED, "external runner worker cancel failed");
        }
    }

    private RunnerValidation validateRuntimePreparation(RunnerRunRequest request, UiE2eScene scene) {
        if (!properties.runnerWorkerConfigured()) {
            return new RunnerValidation(false, WORKER_NOT_CONFIGURED, "external runner worker is not configured");
        }
        if (!validWorkerUrl(properties.effectiveRunnerWorkerUrl())) {
            return new RunnerValidation(false, WORKER_URL_INVALID, "external runner worker URL is invalid");
        }
        try {
            credentialPlan(request);
            return validateSteps(repository.sceneSteps(scene.id()));
        } catch (BusinessException exception) {
            String code = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : RUNNER_FAILED;
            return new RunnerValidation(false, code, "runner preparation failed");
        }
    }

    private HttpResponse<String> sendRunRequest(
            RunnerRunRequest request,
            List<UiE2eSceneStep> steps,
            RunnerCredentialInjectionPlan plan
    ) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(workerRequest(request, steps, plan));
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(properties.effectiveRunnerWorkerUrl()))
                .timeout(Duration.ofSeconds(properties.effectiveDefaultTimeoutSeconds()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        if (properties.runnerWorkerTokenConfigured()) {
            requestBuilder.header("Authorization", "Bearer " + properties.effectiveRunnerWorkerToken());
        }
        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private Map<String, Object> workerRequest(
            RunnerRunRequest request,
            List<UiE2eSceneStep> steps,
            RunnerCredentialInjectionPlan plan
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "runId", request.runId() == null ? null : request.runId().toString());
        putIfPresent(payload, "sceneId", request.sceneId() == null ? null : request.sceneId().toString());
        putIfPresent(payload, "bundleId", request.bundleId() == null ? null : request.bundleId().toString());
        payload.put("projectId", boundedText(request.projectId(), 128));
        payload.put("baseUrl", boundedText(request.baseUrl(), 512));
        payload.put("browserTypes", request.browserTypes() == null || request.browserTypes().isEmpty()
                ? List.of("CHROMIUM")
                : request.browserTypes().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizedStepType)
                .toList());
        payload.put("visualRegressionEnabled", request.visualRegressionEnabled());
        if (request.baselineRunId() != null) {
            payload.put("baselineRunId", request.baselineRunId().toString());
        }
        if (request.visualMismatchThreshold() != null) {
            payload.put("visualMismatchThreshold", request.visualMismatchThreshold());
        }
        payload.put("timeoutSeconds", properties.effectiveDefaultTimeoutSeconds());
        payload.put("steps", stepPayloads(steps));
        payload.put("capturePolicy", Map.of(
                "captureScreenshotEnabled", properties.captureScreenshotEnabled(),
                "captureVideoEnabled", properties.captureVideoEnabled(),
                "captureHarEnabled", properties.captureHarEnabled(),
                "captureTraceEnabled", properties.captureTraceEnabled(),
                "captureJunitXmlEnabled", properties.captureJunitXmlEnabled(),
                "maxArtifactCount", properties.effectiveMaxArtifactCount(),
                "maxArtifactSizeBytes", properties.effectiveMaxArtifactSizeBytes()
        ));
        payload.put("credentialPlan", Map.of(
                "planType", plan.planType(),
                "format", plan.format(),
                "schemaId", plan.schemaId(),
                "principalSource", plan.principalSource(),
                "principalIdentifierPresent", plan.principalIdentifierPresent(),
                "componentCount", plan.componentCount(),
                "principalIdentifier", plan.principalIdentifier(),
                "credentialValue", plan.credentialValue()
        ));
        Map<String, Object> accountContext = new LinkedHashMap<>();
        putIfPresent(accountContext, "accountLeaseRef", boundedText(request.accountLeaseRef(), 128));
        accountContext.put("secretRefDigestPresent", credentialDigestPresent(request.accountSummary()));
        putIfPresent(accountContext, "accountKey", boundedText(firstText(request.accountSummary(), "accountKey"), 128));
        payload.put("accountContext", Map.copyOf(accountContext));
        return Map.copyOf(payload);
    }

    private RunnerPayload readPayload(String body) throws IOException {
        JsonNode root = readRoot(body);
        JsonNode payload = root.path("data").isObject() ? root.path("data") : root;
        List<RunnerStepPayload> steps = new ArrayList<>();
        if (payload.path("stepResults").isArray()) {
            for (JsonNode node : payload.path("stepResults")) {
                if (!node.isObject()) {
                    continue;
                }
                steps.add(objectMapper.convertValue(node, RunnerStepPayload.class));
            }
        }
        List<RunnerArtifactPayload> artifacts = new ArrayList<>();
        if (payload.path("artifacts").isArray()) {
            for (JsonNode node : payload.path("artifacts")) {
                if (!node.isObject()) {
                    continue;
                }
                artifacts.add(objectMapper.convertValue(node, RunnerArtifactPayload.class));
            }
        }
        return new RunnerPayload(
                textValue(payload, "status"),
                textValue(payload, "runnerMode"),
                textValue(payload, "failureCode", "errorCode"),
                textValue(payload, "failureSummary", "errorSummary", "message"),
                List.copyOf(steps),
                List.copyOf(artifacts),
                payload.path("executionSummary").isObject()
                        ? objectMapper.convertValue(payload.path("executionSummary"), MAP_TYPE)
                        : Map.of()
        );
    }

    private JsonNode readRoot(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            throw new IOException("external runner worker payload is empty");
        }
        return objectMapper.readTree(body);
    }

    private RunnerCredentialInjectionPlan credentialPlan(RunnerRunRequest request) {
        if (testDataCrossWpReferenceService == null || !testDataCrossWpReferenceService.runnerCredentialInjectionReady()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, EXECUTION_NOT_READY);
        }
        if (request == null || !StringUtils.hasText(request.accountLeaseRef()) || !StringUtils.hasText(request.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID");
        }
        try {
            TestDataRunnerCredentialResolver.RunnerCredentialResolution resolution =
                    testDataCrossWpReferenceService.resolveRunnerCredential(
                            UUID.fromString(request.accountLeaseRef()),
                            request.projectId()
                    );
            return credentialPlanSupport.buildCredentialPlan(request, resolution);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID");
        } catch (BusinessException exception) {
            String code = UiE2eRunnerCredentialPlanSupport.UNSUPPORTED_CREDENTIAL_FORMAT.equals(exception.getMessage())
                    ? UiE2eRunnerCredentialPlanSupport.UNSUPPORTED_CREDENTIAL_FORMAT
                    : SECRET_PROVIDER_ERROR;
            throw new BusinessException(ErrorCode.INVALID_STATE, code);
        }
    }

    /**
     * The remote worker receives the same bounded LOGIN/NAVIGATE/ASSERT surface as the local Playwright adapter so the
     * control plane does not claim support for step contracts that no worker implementation has been reviewed for.
     */
    private RunnerValidation validateSteps(List<UiE2eSceneStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return new RunnerValidation(false, STEP_CONTRACT_INVALID, "scene has no executable steps");
        }
        for (UiE2eSceneStep step : steps) {
            String type = normalizedStepType(step.stepType());
            Map<String, Object> actionSummary = readMap(step.actionSummaryJson());
            Map<String, Object> assertionSummary = readMap(step.assertionSummaryJson());
            if ("LOGIN".equals(type)) {
                boolean valid = StringUtils.hasText(firstText(actionSummary,
                                "principalField",
                                "usernameField",
                                "usernameSelector"))
                        && StringUtils.hasText(firstText(actionSummary,
                                "credentialField",
                                "passwordField",
                                "passwordSelector"))
                        && StringUtils.hasText(firstText(actionSummary, "submitAction"))
                        && assertContractReady(assertionSummary);
                if (!valid) {
                    return new RunnerValidation(false, STEP_CONTRACT_INVALID, "login step contract is incomplete");
                }
                continue;
            }
            if ("NAVIGATE".equals(type)) {
                if (!StringUtils.hasText(firstText(actionSummary, "path", "targetPath", "urlPath"))) {
                    return new RunnerValidation(false, STEP_CONTRACT_INVALID, "navigate step requires a bounded path");
                }
                continue;
            }
            if ("ASSERT".equals(type)) {
                if (!assertContractReady(assertionSummary)) {
                    return new RunnerValidation(false, STEP_CONTRACT_INVALID, "assert step contract is incomplete");
                }
                continue;
            }
            return new RunnerValidation(false, STEP_UNSUPPORTED, "runner supports LOGIN/NAVIGATE/ASSERT only");
        }
        return new RunnerValidation(true, null, null);
    }

    private boolean assertContractReady(Map<String, Object> assertionSummary) {
        return StringUtils.hasText(firstText(assertionSummary, "successSignal", "expected", "expectedText"))
                || StringUtils.hasText(firstText(assertionSummary, "urlContains", "pathContains"));
    }

    private List<Map<String, Object>> stepPayloads(List<UiE2eSceneStep> steps) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (UiE2eSceneStep step : steps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sceneStepId", step.id().toString());
            item.put("stepOrder", step.stepOrder());
            item.put("stepType", normalizedStepType(step.stepType()));
            item.put("actionSummary", readMap(step.actionSummaryJson()));
            item.put("locatorSummary", readMap(step.locatorStrategyJson()));
            item.put("assertionSummary", readMap(step.assertionSummaryJson()));
            item.put("timeoutPolicy", readMap(step.waitPolicyJson()));
            payload.add(Map.copyOf(item));
        }
        return List.copyOf(payload);
    }

    private RunnerRunResult failure(
            String status,
            String failureCode,
            String failureSummary,
            List<RunnerStepResult> stepResults,
            List<RunnerArtifactManifest> artifacts
    ) {
        return new RunnerRunResult(
                status,
                RUNNER_MODE,
                failureCode,
                SensitiveTextSanitizer.sanitizedEvidenceText(failureSummary, 256),
                List.copyOf(stepResults),
                List.copyOf(artifacts),
                Map.of()
        );
    }

    private List<RunnerArtifactManifest> blockedArtifacts(String blockedReason) {
        List<RunnerArtifactManifest> artifacts = new ArrayList<>();
        artifacts.add(blockedArtifact("LOG", blockedReason));
        if (properties.captureScreenshotEnabled()) {
            artifacts.add(blockedArtifact("SCREENSHOT", blockedReason));
        }
        if (properties.captureTraceEnabled()) {
            artifacts.add(blockedArtifact("TRACE", blockedReason));
        }
        if (properties.captureVideoEnabled()) {
            artifacts.add(blockedArtifact("VIDEO", blockedReason));
        }
        if (properties.captureHarEnabled()) {
            artifacts.add(blockedArtifact("HAR", blockedReason));
        }
        if (properties.captureJunitXmlEnabled()) {
            artifacts.add(blockedArtifact("JUNIT_XML", blockedReason));
        }
        return List.copyOf(artifacts);
    }

    private RunnerArtifactManifest blockedArtifact(String type, String blockedReason) {
        return new RunnerArtifactManifest(
                type,
                null,
                null,
                0,
                Map.of(
                        "aggregateOnly", true,
                        "rawArtifactStored", false,
                        "rawArtifactDownloadReady", false,
                        "captureBlockedReason", blockedReason
                ),
                "BLOCKED"
        );
    }

    private List<RunnerStepResult> blockedSteps(List<UiE2eSceneStep> steps, String errorCode, String bucket) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .map(step -> new RunnerStepResult(
                        step.id(),
                        step.stepOrder(),
                        "BLOCKED",
                        0,
                        bucket,
                        errorCode,
                        Map.of(
                                "stepType", normalizedStepType(step.stepType()),
                                "aggregateOnly", true,
                                "rawDomStored", false,
                                "rawRunnerOutputStored", false,
                                "secretPlaintextStored", false
                        )
                ))
                .toList();
    }

    private List<RunnerStepResult> timeoutSteps(List<UiE2eSceneStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .map(step -> new RunnerStepResult(
                        step.id(),
                        step.stepOrder(),
                        "TIMEOUT",
                        Math.max(1, properties.effectiveDefaultTimeoutSeconds() * 1000),
                        "ENVIRONMENT_TIMEOUT",
                        RUNNER_TIMEOUT,
                        Map.of(
                                "stepType", normalizedStepType(step.stepType()),
                                "aggregateOnly", true,
                                "rawDomStored", false,
                                "rawRunnerOutputStored", false,
                                "secretPlaintextStored", false
                        )
                ))
                .toList();
    }

    private List<RunnerStepResult> failedSteps(List<UiE2eSceneStep> steps, String reason) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .map(step -> new RunnerStepResult(
                        step.id(),
                        step.stepOrder(),
                        "FAILED",
                        0,
                        "RUNNER",
                        RUNNER_FAILED,
                        Map.of(
                                "stepType", normalizedStepType(step.stepType()),
                                "aggregateOnly", true,
                                "rawDomStored", false,
                                "rawRunnerOutputStored", false,
                                "secretPlaintextStored", false,
                                "runnerFailureReason", reason
                        )
                ))
                .toList();
    }

    private String primaryFailureBucket(String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return "RUNNER";
        }
        return switch (errorCode.trim().toUpperCase(Locale.ROOT)) {
            case "UI_E2E_ACCOUNT_LEASE_INVALID",
                    UiE2eRunnerCredentialPlanSupport.UNSUPPORTED_CREDENTIAL_FORMAT,
                    SECRET_PROVIDER_ERROR -> "ACCOUNT";
            case STEP_UNSUPPORTED, STEP_CONTRACT_INVALID -> "ASSERTION";
            default -> "RUNNER";
        };
    }

    private boolean credentialDigestPresent(Map<String, Object> accountSummary) {
        if (accountSummary == null || accountSummary.isEmpty()) {
            return false;
        }
        Object digest = accountSummary.get("secretRefDigest");
        return digest instanceof String text && StringUtils.hasText(text);
    }

    private Map<String, Object> readMap(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return Map.of();
            }
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, STEP_CONTRACT_INVALID);
        }
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String normalizedStepType(String stepType) {
        return StringUtils.hasText(stepType) ? stepType.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private String boundedText(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }

    private String textValue(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private boolean validWorkerUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        target.put(key, value);
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Best-effort cleanup only.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private record RunnerPayload(
            String status,
            String runnerMode,
            String failureCode,
            String failureSummary,
            List<RunnerStepPayload> stepResults,
            List<RunnerArtifactPayload> artifacts,
            Map<String, Object> executionSummary
    ) {
        RunnerRunResult toResult(
                UUID runId,
                UiE2eProperties properties,
                Path workspace,
                UiE2eArtifactStorage artifactStorage
        ) {
            String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "FAILED";
            String effectiveStatus = Set.of("SUCCEEDED", "FAILED", "TIMEOUT", "BLOCKED", "CANCELED").contains(normalizedStatus)
                    ? normalizedStatus
                    : "FAILED";
            String effectiveFailureCode = StringUtils.hasText(failureCode)
                    ? SensitiveTextSanitizer.boundedText(failureCode.trim().toUpperCase(Locale.ROOT), 64)
                    : "SUCCEEDED".equals(effectiveStatus) ? null : RUNNER_FAILED;
            List<RunnerStepResult> safeSteps = stepResults == null
                    ? List.of()
                    : stepResults.stream().filter(item -> item != null).map(RunnerStepPayload::toResult).toList();
            List<RunnerArtifactManifest> safeArtifacts = artifacts == null
                    ? List.of()
                    : artifacts.stream()
                    .filter(item -> item != null)
                    .limit(properties.effectiveMaxArtifactCount())
                    .map(item -> item.toManifest(runId, properties, workspace, artifactStorage))
                    .toList();
            Map<String, Object> safeSummary = sanitizeMap(executionSummary);
            if (StringUtils.hasText(runnerMode)) {
                safeSummary.put("remoteRunnerMode", SensitiveTextSanitizer.boundedText(runnerMode.trim().toUpperCase(Locale.ROOT), 64));
            }
            return new RunnerRunResult(
                    effectiveStatus,
                    RUNNER_MODE,
                    effectiveFailureCode,
                    SensitiveTextSanitizer.sanitizedEvidenceText(failureSummary, 256),
                    safeSteps,
                    safeArtifacts,
                    Map.copyOf(safeSummary)
            );
        }
    }

    private record RunnerStepPayload(
            UUID sceneStepId,
            int stepOrder,
            String status,
            int durationMs,
            String failureBucket,
            String errorCode,
            Map<String, Object> summary
    ) {
        RunnerStepResult toResult() {
            Map<String, Object> safeSummary = sanitizeMap(summary);
            safeSummary.put("aggregateOnly", true);
            safeSummary.put("rawDomStored", false);
            safeSummary.put("rawRunnerOutputStored", false);
            safeSummary.put("secretPlaintextStored", false);
            return new RunnerStepResult(
                    sceneStepId,
                    Math.max(stepOrder, 1),
                    StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "FAILED",
                    Math.max(durationMs, 0),
                    StringUtils.hasText(failureBucket) ? failureBucket.trim().toUpperCase(Locale.ROOT) : null,
                    SensitiveTextSanitizer.boundedNullableText(errorCode, 64),
                    Map.copyOf(safeSummary)
            );
        }
    }

    private record RunnerArtifactPayload(
            String artifactType,
            String storageRef,
            String artifactDigest,
            long sizeBytes,
            Map<String, Object> redactionFlags,
            String captureStatus,
            String fileName,
            String contentBase64
    ) {
        RunnerArtifactManifest toManifest(
                UUID runId,
                UiE2eProperties properties,
                Path workspace,
                UiE2eArtifactStorage artifactStorage
        ) {
            Map<String, Object> safeFlags = sanitizeMap(redactionFlags);
            UiE2eArtifactStorage.StoredArtifact storedArtifact = store(runId, workspace, artifactStorage);
            safeFlags.put("aggregateOnly", true);
            safeFlags.put("rawArtifactStored", storedArtifact != null);
            safeFlags.put("rawArtifactDownloadReady", storedArtifact != null);
            safeFlags.put("secretPlaintextStored", false);
            safeFlags.put("storageCredentialStored", false);
            long boundedSize = Math.max(0L, Math.min(sizeBytes, properties.effectiveMaxArtifactSizeBytes()));
            String digest = StringUtils.hasText(artifactDigest)
                    ? SensitiveTextSanitizer.boundedText(artifactDigest.trim().toLowerCase(Locale.ROOT), 64)
                    : SensitiveTextSanitizer.sha256Hex(String.valueOf(runId) + ":" + artifactType + ":" + boundedSize);
            return new RunnerArtifactManifest(
                    StringUtils.hasText(artifactType) ? artifactType.trim().toUpperCase(Locale.ROOT) : "LOG",
                    storedArtifact == null
                            ? SensitiveTextSanitizer.sanitizedEvidenceText(storageRef, 256)
                            : storedArtifact.storageRef(),
                    digest.matches("^[0-9a-f]{1,64}$") ? digest : SensitiveTextSanitizer.sha256Hex(digest),
                    storedArtifact == null ? boundedSize : Math.max(0L, Math.min(storedArtifact.sizeBytes(), properties.effectiveMaxArtifactSizeBytes())),
                    Map.copyOf(safeFlags),
                    StringUtils.hasText(captureStatus) ? captureStatus.trim().toUpperCase(Locale.ROOT) : "BLOCKED"
            );
        }

        /**
         * Copies inline base64 artifact bytes into controlled local storage before the temporary worker payload
         * workspace is removed. Remote URLs intentionally stay non-downloadable until a reviewed fetch path exists.
         */
        private UiE2eArtifactStorage.StoredArtifact store(
                UUID runId,
                Path workspace,
                UiE2eArtifactStorage artifactStorage
        ) {
            if (runId == null || artifactStorage == null || workspace == null || !StringUtils.hasText(contentBase64)) {
                return null;
            }
            try {
                byte[] content = Base64.getDecoder().decode(contentBase64);
                String effectiveType = StringUtils.hasText(artifactType) ? artifactType.trim().toUpperCase(Locale.ROOT) : "LOG";
                String extension = extension(fileName, effectiveType);
                Path source = workspace.resolve("artifact-" + UUID.randomUUID() + extension).normalize();
                if (!source.startsWith(workspace)) {
                    return null;
                }
                Files.write(source, content);
                return artifactStorage.store(runId, UUID.randomUUID(), effectiveType, source);
            } catch (IllegalArgumentException | IOException ignored) {
                return null;
            }
        }

        private String extension(String rawFileName, String effectiveType) {
            if (StringUtils.hasText(rawFileName)) {
                int index = rawFileName.lastIndexOf('.');
                if (index >= 0 && index < rawFileName.length() - 1) {
                    return rawFileName.substring(index).toLowerCase(Locale.ROOT);
                }
            }
            return switch (effectiveType) {
                case "SCREENSHOT" -> ".png";
                case "TRACE" -> ".zip";
                case "VIDEO" -> ".webm";
                case "HAR" -> ".har";
                case "JUNIT_XML" -> ".xml";
                default -> ".log";
            };
        }
    }

    private static Map<String, Object> sanitizeMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && value != null) {
                safe.put(
                        SensitiveTextSanitizer.boundedText(String.valueOf(key), 64),
                        sanitizeValue(value)
                );
            }
        });
        return safe;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null && item != null) {
                    safe.put(
                            SensitiveTextSanitizer.boundedText(String.valueOf(key), 64),
                            sanitizeValue(item)
                    );
                }
            });
            return Map.copyOf(safe);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(HttpWorkerUiE2eRunnerAdapter::sanitizeValue).toList();
        }
        if (value instanceof String text) {
            return SensitiveTextSanitizer.sanitizedEvidenceText(text, 256);
        }
        return value;
    }
}
