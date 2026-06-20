package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.util.StringUtils;

/**
 * Minimal real-browser runner for WP7 that executes a bounded LOGIN/NAVIGATE/ASSERT slice through Playwright.
 * All persisted outputs remain aggregate-only and redact raw DOM, runner logs and credential plaintext.
 */
public class PlaywrightSubprocessUiE2eRunnerAdapter implements UiE2eRunnerPort {

    private static final String RUNNER_MODE = "PLAYWRIGHT_SUBPROCESS";
    private static final String STEP_UNSUPPORTED = "UI_E2E_STEP_UNSUPPORTED";
    private static final String STEP_CONTRACT_INVALID = "UI_E2E_STEP_CONTRACT_INVALID";
    private static final String RUNNER_TIMEOUT = "UI_E2E_RUNNER_TIMEOUT";
    private static final String RUNNER_FAILED = "UI_E2E_RUNNER_FAILED";
    private static final String SECRET_PROVIDER_ERROR = "SECRET_PROVIDER_ERROR";
    private static final int OUTPUT_READ_MAX_BYTES = 8_192;
    private static final int PROCESS_TIMEOUT_GRACE_SECONDS = 5;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UiE2eRepository repository;
    private final UiE2eProperties properties;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;
    private final ObjectMapper objectMapper;
    private final UiE2eRunnerCredentialPlanSupport credentialPlanSupport;
    private final UiE2eArtifactStorage artifactStorage;
    private final CommandExecutor commandExecutor;

    public PlaywrightSubprocessUiE2eRunnerAdapter(
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
                new ProcessBuilderCommandExecutor()
        );
    }

    PlaywrightSubprocessUiE2eRunnerAdapter(
            UiE2eRepository repository,
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            ObjectMapper objectMapper,
            UiE2eArtifactStorage artifactStorage,
            CommandExecutor commandExecutor
    ) {
        this.repository = repository;
        this.properties = properties;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
        this.objectMapper = objectMapper;
        this.credentialPlanSupport = new UiE2eRunnerCredentialPlanSupport(objectMapper);
        this.artifactStorage = artifactStorage;
        this.commandExecutor = commandExecutor;
    }

    /**
     * Validates runner readiness and the minimal executable contract before we try to spawn a browser subprocess.
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
                request.accountSummary()
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
                    "EXECUTION_RUNNER_NOT_READY",
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
        Instant startedAt = Instant.now();
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("wp7-playwright-runner-");
            RunnerCredentialInjectionPlan credentialPlan = credentialPlan(request);
            ProcessResult processResult = commandExecutor.execute(
                    command(request.baseUrl()),
                    environment(workspace, credentialPlan, repository.sceneSteps(scene.id())),
                    workspace,
                    properties.effectiveDefaultTimeoutSeconds()
            );
            if (processResult.timedOut()) {
                return failure(
                        "TIMEOUT",
                        RUNNER_TIMEOUT,
                        "playwright subprocess timed out",
                        timeoutSteps(repository.sceneSteps(scene.id()), startedAt),
                        blockedArtifacts("runnerTimedOut")
                );
            }
            Path resultFile = workspace.resolve("result.json");
            if (!Files.exists(resultFile)) {
                return failure(
                        "FAILED",
                        RUNNER_FAILED,
                        "playwright subprocess did not produce a bounded result summary",
                        failedSteps(repository.sceneSteps(scene.id()), "runnerResultMissing"),
                    blockedArtifacts("runnerResultMissing")
                );
            }
            return readPayload(resultFile).toResult(request.runId(), processResult.exitCode(), properties, workspace, artifactStorage);
        } catch (BusinessException exception) {
            String errorCode = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : RUNNER_FAILED;
            return failure(
                    "BLOCKED",
                    errorCode,
                    "runner preparation failed",
                    blockedSteps(repository.sceneSteps(scene.id()), errorCode, primaryFailureBucket(errorCode)),
                    blockedArtifacts("runnerPreparationFailed")
            );
        } catch (IOException exception) {
            return failure(
                    "FAILED",
                    RUNNER_FAILED,
                    "playwright subprocess workspace preparation failed",
                    failedSteps(repository.sceneSteps(scene.id()), "runnerWorkspaceFailed"),
                    blockedArtifacts("workspacePrepareFailed")
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(
                    "CANCELED",
                    "UI_E2E_RUNNER_CANCELED",
                    "playwright subprocess interrupted",
                    blockedSteps(repository.sceneSteps(scene.id()), "UI_E2E_RUNNER_CANCELED", "RUNNER"),
                    blockedArtifacts("runnerInterrupted")
            );
        } finally {
            deleteQuietly(workspace);
        }
    }

    @Override
    public RunnerCancelResult cancel(UUID runId) {
        return new RunnerCancelResult(false, "UI_E2E_RUNNER_CANCELED", "playwright subprocess runner is synchronous; cancel is best effort only");
    }

    private RunnerValidation validateRuntimePreparation(RunnerRunRequest request, UiE2eScene scene) {
        try {
            credentialPlan(request);
            return validateSteps(repository.sceneSteps(scene.id()));
        } catch (BusinessException exception) {
            String code = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : RUNNER_FAILED;
            return new RunnerValidation(false, code, "runner preparation failed");
        }
    }

    private RunnerCredentialInjectionPlan credentialPlan(RunnerRunRequest request) {
        if (testDataCrossWpReferenceService == null || !testDataCrossWpReferenceService.runnerCredentialInjectionReady()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_RUNNER_NOT_READY");
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
     * P0 keeps the executable surface intentionally small so we can provide deterministic failure semantics instead of
     * pretending arbitrary step summaries are runnable.
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

    private List<String> command(String baseUrl) {
        return List.of(
                properties.effectiveRunnerNodeCommand(),
                "--input-type=module",
                "-e",
                runnerScript(),
                baseUrl == null ? "" : baseUrl
        );
    }

    private Map<String, String> environment(
            Path workspace,
            RunnerCredentialInjectionPlan plan,
            List<UiE2eSceneStep> steps
    ) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("VA_WP7_WORKSPACE_DIR", workspace.toString());
        environment.put(
                "VA_WP7_NODE_MODULES_DIR",
                Path.of(properties.effectiveRunnerNodeModulesDir()).toAbsolutePath().normalize().toString()
        );
        environment.put("VA_WP7_CAPTURE_SCREENSHOT", String.valueOf(properties.captureScreenshotEnabled()));
        environment.put("VA_WP7_CAPTURE_TRACE", String.valueOf(properties.captureTraceEnabled()));
        environment.put("VA_WP7_STEPS_JSON", writeJson(stepPayloads(steps)));
        environment.put("VA_WP7_CREDENTIAL_PLAN_JSON", writeJson(Map.of(
                "principalIdentifier", plan.principalIdentifier(),
                "credentialValue", plan.credentialValue()
        )));
        return environment;
    }

    private List<Map<String, Object>> stepPayloads(List<UiE2eSceneStep> steps) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (UiE2eSceneStep step : steps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sceneStepId", step.id().toString());
            item.put("stepOrder", step.stepOrder());
            item.put("stepType", normalizedStepType(step.stepType()));
            item.put("actionSummary", readMap(step.actionSummaryJson()));
            item.put("assertionSummary", readMap(step.assertionSummaryJson()));
            payload.add(Map.copyOf(item));
        }
        return List.copyOf(payload);
    }

    private RunnerPayload readPayload(Path path) throws IOException {
        try {
            return objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), RunnerPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("playwright result summary is invalid", exception);
        }
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
                List.copyOf(artifacts)
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

    private List<RunnerStepResult> timeoutSteps(List<UiE2eSceneStep> steps, Instant startedAt) {
        int durationMs = (int) Math.max(1L, Duration.between(startedAt, Instant.now()).toMillis());
        return steps.stream()
                .map(step -> new RunnerStepResult(
                        step.id(),
                        step.stepOrder(),
                        "TIMEOUT",
                        durationMs,
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, RUNNER_FAILED);
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

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
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

    private String runnerScript() {
        return """
                import fs from 'node:fs/promises';
                import path from 'node:path';
                import { createRequire } from 'node:module';

                const baseUrl = String(process.argv[1] || '').replace(/\\/$/, '');
                const workspaceDir = process.env.VA_WP7_WORKSPACE_DIR;
                const nodeModulesDir = process.env.VA_WP7_NODE_MODULES_DIR;
                const captureScreenshot = process.env.VA_WP7_CAPTURE_SCREENSHOT === 'true';
                const captureTrace = process.env.VA_WP7_CAPTURE_TRACE === 'true';
                const steps = JSON.parse(process.env.VA_WP7_STEPS_JSON || '[]');
                const credentialPlan = JSON.parse(process.env.VA_WP7_CREDENTIAL_PLAN_JSON || '{}');
                const require = createRequire(path.resolve(nodeModulesDir, 'playwright', 'package.json'));
                const { chromium } = require('playwright');
                const result = {
                  status: 'FAILED',
                  runnerMode: 'PLAYWRIGHT_SUBPROCESS',
                  failureCode: 'UI_E2E_RUNNER_FAILED',
                  failureSummary: 'playwright execution failed',
                  stepResults: [],
                  artifacts: []
                };

                const sanitize = (value) => {
                  if (value == null) return value;
                  return String(value)
                    .replace(/secret:\\/\\/[^\\s,;]+/gi, '[REDACTED_SECRET_REF]')
                    .replace(/https?:\\/\\/[^\\s,;]+/gi, '[REDACTED_URL]')
                    .replace(/\\b(bearer\\s+[a-z0-9._-]{8,}|token\\s*[:=]\\s*[^\\s,;]+|password\\s*[:=]\\s*[^\\s,;]+|cookie\\s*[:=]\\s*[^\\s,;]+)/gi, '[REDACTED]');
                };

                const hash = async (filePath) => {
                  const { createHash } = await import('node:crypto');
                  const bytes = await fs.readFile(filePath);
                  return createHash('sha256').update(bytes).digest('hex');
                };

                const textFromAssertion = (summary) => {
                  if (!summary || typeof summary !== 'object') return '';
                  if (typeof summary.expectedText === 'string' && summary.expectedText.trim()) return summary.expectedText.trim();
                  if (typeof summary.expected === 'string' && summary.expected.trim()) return summary.expected.trim();
                  const signal = typeof summary.successSignal === 'string' ? summary.successSignal.trim() : '';
                  const match = signal.match(/^text contains\\s+(.+)$/i);
                  return match ? match[1].trim() : '';
                };

                const pathFromAssertion = (summary) => {
                  if (!summary || typeof summary !== 'object') return '';
                  if (typeof summary.urlContains === 'string' && summary.urlContains.trim()) return summary.urlContains.trim();
                  if (typeof summary.pathContains === 'string' && summary.pathContains.trim()) return summary.pathContains.trim();
                  const signal = typeof summary.successSignal === 'string' ? summary.successSignal.trim() : '';
                  const match = signal.match(/^url contains\\s+(.+)$/i);
                  return match ? match[1].trim() : '';
                };

                let browser;
                let context;
                let page;
                try {
                  browser = await chromium.launch({ headless: true });
                  context = await browser.newContext();
                  if (captureTrace) {
                    await context.tracing.start({ screenshots: true, snapshots: false, sources: false });
                  }
                  page = await context.newPage();

                  for (const step of steps) {
                    const startedAt = Date.now();
                    const stepType = String(step.stepType || '').toUpperCase();
                    const stepResult = {
                      sceneStepId: step.sceneStepId || null,
                      stepOrder: Number(step.stepOrder || 0),
                      status: 'FAILED',
                      durationMs: 0,
                      failureBucket: stepType === 'LOGIN' ? 'ACCOUNT' : stepType === 'ASSERT' ? 'ASSERTION' : 'RUNNER',
                      errorCode: 'UI_E2E_RUNNER_FAILED',
                      summary: {
                        stepType,
                        aggregateOnly: true,
                        rawDomStored: false,
                        rawRunnerOutputStored: false,
                        secretPlaintextStored: false
                      }
                    };
                    try {
                      if (stepType === 'LOGIN') {
                        const actionSummary = step.actionSummary || {};
                        const assertionSummary = step.assertionSummary || {};
                        const usernameSelector = String(actionSummary.principalField || actionSummary.usernameField || actionSummary.usernameSelector || '');
                        const passwordSelector = String(actionSummary.credentialField || actionSummary.passwordField || actionSummary.passwordSelector || '');
                        await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
                        await page.locator(usernameSelector.startsWith('data-testid=') ? `[data-testid="${usernameSelector.slice('data-testid='.length)}"]` : usernameSelector).first().fill(String(credentialPlan.principalIdentifier || ''));
                        await page.locator(passwordSelector.startsWith('data-testid=') ? `[data-testid="${passwordSelector.slice('data-testid='.length)}"]` : passwordSelector).first().fill(String(credentialPlan.credentialValue || ''));
                        await page.locator('button[type="submit"], input[type="submit"], button').first().click();
                        const pathContains = pathFromAssertion(assertionSummary);
                        if (pathContains) {
                          await page.waitForURL((url) => url.toString().includes(pathContains), { timeout: 5000 });
                        }
                        const expectedText = textFromAssertion(assertionSummary);
                        if (expectedText) {
                          await page.getByText(expectedText, { exact: false }).first().waitFor({ timeout: 5000 });
                        }
                        stepResult.status = 'SUCCEEDED';
                        stepResult.failureBucket = null;
                        stepResult.errorCode = null;
                        stepResult.summary.successSignalEvaluated = true;
                      } else if (stepType === 'NAVIGATE') {
                        const actionSummary = step.actionSummary || {};
                        const targetPath = String(actionSummary.path || actionSummary.targetPath || actionSummary.urlPath || '');
                        const normalizedPath = targetPath.startsWith('/') ? targetPath : `/${targetPath}`;
                        await page.goto(`${baseUrl}${normalizedPath}`, { waitUntil: 'domcontentloaded' });
                        stepResult.status = 'SUCCEEDED';
                        stepResult.failureBucket = null;
                        stepResult.errorCode = null;
                        stepResult.summary.navigated = true;
                      } else if (stepType === 'ASSERT') {
                        const assertionSummary = step.assertionSummary || {};
                        const pathContains = pathFromAssertion(assertionSummary);
                        const expectedText = textFromAssertion(assertionSummary);
                        if (pathContains && !page.url().includes(pathContains)) {
                          throw new Error(`url missing expected segment ${pathContains}`);
                        }
                        if (expectedText) {
                          await page.getByText(expectedText, { exact: false }).first().waitFor({ timeout: 5000 });
                        }
                        stepResult.status = 'SUCCEEDED';
                        stepResult.failureBucket = null;
                        stepResult.errorCode = null;
                        stepResult.summary.assertionChecked = true;
                      }
                    } catch (error) {
                      stepResult.summary.errorSummary = sanitize(error?.message || 'playwright step failed');
                      result.failureCode = stepType === 'ASSERT' ? 'UI_E2E_ASSERTION_FAILED' : 'UI_E2E_RUNNER_FAILED';
                      result.failureSummary = stepResult.summary.errorSummary;
                      result.status = /timeout/i.test(String(error?.message || '')) ? 'TIMEOUT' : 'FAILED';
                      result.stepResults.push(stepResult);
                      break;
                    } finally {
                      stepResult.durationMs = Math.max(1, Date.now() - startedAt);
                    }
                    result.stepResults.push(stepResult);
                  }

                  if (!result.stepResults.some((item) => item.status === 'FAILED' || item.status === 'TIMEOUT')) {
                    result.status = 'SUCCEEDED';
                    result.failureCode = null;
                    result.failureSummary = null;
                  }

                  if (captureScreenshot && page) {
                    const screenshotPath = path.join(workspaceDir, 'screenshot.png');
                    await page.screenshot({ path: screenshotPath, fullPage: false });
                    result.artifacts.push({
                      artifactType: 'SCREENSHOT',
                      storageRef: `summary://ui-e2e/${path.basename(workspaceDir)}/screenshot`,
                      artifactDigest: await hash(screenshotPath),
                      sizeBytes: (await fs.stat(screenshotPath)).size,
                      redactionFlags: {
                        aggregateOnly: true,
                        rawArtifactStored: false,
                        rawArtifactDownloadReady: false
                      },
                      captureStatus: 'CAPTURED',
                      workspaceFile: 'screenshot.png'
                    });
                  }
                  if (captureTrace && context) {
                    const tracePath = path.join(workspaceDir, 'trace.zip');
                    await context.tracing.stop({ path: tracePath });
                    result.artifacts.push({
                      artifactType: 'TRACE',
                      storageRef: `summary://ui-e2e/${path.basename(workspaceDir)}/trace`,
                      artifactDigest: await hash(tracePath),
                      sizeBytes: (await fs.stat(tracePath)).size,
                      redactionFlags: {
                        aggregateOnly: true,
                        rawArtifactStored: false,
                        rawArtifactDownloadReady: false
                      },
                      captureStatus: 'CAPTURED',
                      workspaceFile: 'trace.zip'
                    });
                  }
                  const runnerLogPath = path.join(workspaceDir, 'runner.log');
                  await fs.writeFile(runnerLogPath, JSON.stringify({
                    status: result.status,
                    failureCode: result.failureCode,
                    stepCount: result.stepResults.length,
                    captureScreenshot,
                    captureTrace
                  }), 'utf8');
                  result.artifacts.push({
                    artifactType: 'LOG',
                    storageRef: `summary://ui-e2e/${path.basename(workspaceDir)}/runner-log`,
                    artifactDigest: await hash(runnerLogPath),
                    sizeBytes: (await fs.stat(runnerLogPath)).size,
                    redactionFlags: {
                      aggregateOnly: true,
                      rawArtifactStored: false,
                      rawArtifactDownloadReady: false
                    },
                    captureStatus: 'CAPTURED',
                    workspaceFile: 'runner.log'
                  });
                } catch (error) {
                  result.status = 'FAILED';
                  result.failureCode = result.failureCode || 'UI_E2E_RUNNER_FAILED';
                  result.failureSummary = sanitize(error?.message || 'playwright runner failed');
                } finally {
                  if (context) await context.close().catch(() => {});
                  if (browser) await browser.close().catch(() => {});
                }

                await fs.writeFile(path.join(workspaceDir, 'result.json'), JSON.stringify(result), 'utf8');
                """;
    }

    interface CommandExecutor {

        ProcessResult execute(
                List<String> command,
                Map<String, String> environment,
                Path workingDirectory,
                int timeoutSeconds
        ) throws IOException, InterruptedException;
    }

    public record ProcessResult(
            int exitCode,
            boolean timedOut,
            String stdout,
            String stderr
    ) {
    }

    public static final class ProcessBuilderCommandExecutor implements CommandExecutor {

        @Override
        public ProcessResult execute(
                List<String> command,
                Map<String, String> environment,
                Path workingDirectory,
                int timeoutSeconds
        ) throws IOException, InterruptedException {
            Path stdoutFile = workingDirectory.resolve("playwright-stdout.log");
            Path stderrFile = workingDirectory.resolve("playwright-stderr.log");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile());
            builder.environment().putAll(environment);
            Process process = builder.start();
            boolean completed = process.waitFor(Math.max(1, timeoutSeconds) + PROCESS_TIMEOUT_GRACE_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                return new ProcessResult(124, true, readLog(stdoutFile), readLog(stderrFile));
            }
            return new ProcessResult(process.exitValue(), false, readLog(stdoutFile), readLog(stderrFile));
        }

        private String readLog(Path path) throws IOException {
            if (!Files.exists(path)) {
                return "";
            }
            try (InputStream inputStream = Files.newInputStream(path)) {
                return new String(inputStream.readNBytes(OUTPUT_READ_MAX_BYTES), StandardCharsets.UTF_8);
            }
        }
    }

    private record RunnerPayload(
            String status,
            String runnerMode,
            String failureCode,
            String failureSummary,
            List<RunnerStepPayload> stepResults,
            List<RunnerArtifactPayload> artifacts
    ) {
        RunnerRunResult toResult(
                UUID runId,
                int exitCode,
                UiE2eProperties properties,
                Path workspace,
                UiE2eArtifactStorage artifactStorage
        ) {
            String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "FAILED";
            String effectiveStatus = Set.of("SUCCEEDED", "FAILED", "TIMEOUT", "BLOCKED").contains(normalizedStatus)
                    ? normalizedStatus
                    : exitCode == 0 ? "SUCCEEDED" : "FAILED";
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
            return new RunnerRunResult(
                    effectiveStatus,
                    RUNNER_MODE,
                    effectiveFailureCode,
                    SensitiveTextSanitizer.sanitizedEvidenceText(failureSummary, 256),
                    safeSteps,
                    safeArtifacts
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
            String workspaceFile
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
         * Copies the runner-emitted file into controlled storage before the temp workspace is deleted. Failures are
         * intentionally folded into aggregate flags so one bad artifact does not erase the whole run result.
         */
        private UiE2eArtifactStorage.StoredArtifact store(
                UUID runId,
                Path workspace,
                UiE2eArtifactStorage artifactStorage
        ) {
            if (runId == null || artifactStorage == null || workspace == null || !StringUtils.hasText(workspaceFile)) {
                return null;
            }
            try {
                Path source = workspace.resolve(workspaceFile).normalize();
                if (!source.startsWith(workspace) || !Files.exists(source) || !Files.isRegularFile(source)) {
                    return null;
                }
                return artifactStorage.store(
                        runId,
                        UUID.randomUUID(),
                        StringUtils.hasText(artifactType) ? artifactType.trim().toUpperCase(Locale.ROOT) : "LOG",
                        source
                );
            } catch (IOException ignored) {
                return null;
            }
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
            return list.stream().map(PlaywrightSubprocessUiE2eRunnerAdapter::sanitizeValue).toList();
        }
        if (value instanceof String text) {
            return SensitiveTextSanitizer.sanitizedEvidenceText(text, 256);
        }
        return value;
    }
}
