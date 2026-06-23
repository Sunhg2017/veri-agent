package com.songhg.veri.agent.apiautomation.infrastructure;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Minimal WP6 managed runner adapter that executes generated API cases with a bounded HTTP client.
 */
public class ManagedHttpApiAutomationRunnerAdapter implements ApiAutomationRunnerPort {

    private static final String STATIC_CHECK_PASSED = "PASSED";
    private static final List<String> SUPPORTED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Pattern RUNNER_SECRET_HEADER_PATTERN = Pattern.compile("^X-VA-WP6-Secret-[1-9][0-9]*$");

    private final HttpClient httpClient;

    public ManagedHttpApiAutomationRunnerAdapter() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    ManagedHttpApiAutomationRunnerAdapter(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Accepts only script bundles that already passed control-plane review and static checks.
     */
    @Override
    public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
        if (bundle == null) {
            return new RunnerValidation(false, "RUNNER_FAILED", "script bundle is required");
        }
        if (!"APPROVED".equals(bundle.status())) {
            return new RunnerValidation(false, "RUNNER_BUNDLE_NOT_APPROVED", "script bundle is not approved");
        }
        if (!STATIC_CHECK_PASSED.equals(bundle.staticCheckStatus())) {
            return new RunnerValidation(false, "SCRIPT_STATIC_CHECK_FAILED", "script static check is not passed");
        }
        if (bundle.fileCount() <= 0 || !StringUtils.hasText(bundle.bundleDigest())) {
            return new RunnerValidation(false, "RUNNER_FAILED", "script bundle metadata is incomplete");
        }
        return new RunnerValidation(true, null, null);
    }

    /**
     * Executes generated API cases with no request body and discards response bodies so persistence receives only
     * aggregate status evidence.
     */
    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        if (request == null || request.cases() == null || request.cases().isEmpty()) {
            return new RunnerRunResult("FAILED", "MANAGED", "RUNNER_FAILED", "runner request has no cases", List.of());
        }
        List<RunnerCaseResult> results = new ArrayList<>();
        for (ApiAutomationCase automationCase : request.cases()) {
            results.add(executeCase(request, automationCase));
        }
        return aggregate(results);
    }

    /**
     * The initial managed runner is synchronous; cancel requests cannot interrupt already dispatched HTTP calls.
     */
    @Override
    public RunnerCancelResult cancel(RunnerCancelRequest request) {
        return new RunnerCancelResult(false, "RUNNER_CANCELED", "managed runner is synchronous; cancel is best effort only");
    }

    @Override
    public RunnerCancelResult cancel(UUID runId) {
        return cancel(new RunnerCancelRequest(runId, null, null));
    }

    private RunnerCaseResult executeCase(RunnerRunRequest request, ApiAutomationCase automationCase) {
        Instant startedAt = Instant.now();
        try {
            URI target = targetUri(request.baseUrl(), automationCase.path());
            String method = normalizedMethod(automationCase.httpMethod());
            if (!SUPPORTED_METHODS.contains(method)) {
                return caseResult(automationCase, "ERROR", 0, "RUNNER_FAILED", "unsupported http method");
            }
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(Math.max(1, request.timeoutSeconds())))
                    .header("User-Agent", "veri-agent-wp6-managed-runner")
                    .header("Accept", "application/json, */*")
                    .method(method, HttpRequest.BodyPublishers.noBody());
            applyRunnerSecrets(requestBuilder, request.secrets());
            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            int durationMs = durationMillis(startedAt);
            int actualStatus = response.statusCode();
            boolean matched = actualStatus == automationCase.expectedStatus();
            return new RunnerCaseResult(
                    automationCase.id(),
                    matched ? "PASSED" : "FAILED",
                    durationMs,
                    assertionSummary(automationCase.expectedStatus(), actualStatus, matched),
                    matched ? null : "ASSERTION_FAILED",
                    matched ? null : "HTTP status assertion failed"
            );
        } catch (HttpTimeoutException exception) {
            return caseResult(automationCase, "TIMEOUT", durationMillis(startedAt), "RUNNER_TIMEOUT", "HTTP request timed out");
        } catch (IllegalArgumentException exception) {
            return caseResult(automationCase, "ERROR", durationMillis(startedAt), "RUNNER_FAILED", "invalid runner request");
        } catch (IOException exception) {
            return caseResult(automationCase, "ERROR", durationMillis(startedAt), "RUNNER_FAILED", "HTTP request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return caseResult(automationCase, "ERROR", durationMillis(startedAt), "RUNNER_CANCELED", "HTTP request interrupted");
        }
    }

    /**
     * Injects only service-generated WP6 runner headers. The adapter refuses arbitrary header names so direct port
     * callers cannot overwrite Host, Authorization, Cookie or other security-sensitive HTTP headers.
     */
    private void applyRunnerSecrets(HttpRequest.Builder builder, List<RunnerSecret> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return;
        }
        for (RunnerSecret secret : secrets) {
            if (secret == null
                    || !RUNNER_SECRET_HEADER_PATTERN.matcher(nullToEmpty(secret.headerName())).matches()
                    || !StringUtils.hasText(secret.value())
                    || secret.value().contains("\r")
                    || secret.value().contains("\n")) {
                throw new IllegalArgumentException("runner secret header is unsafe");
            }
            builder.header(secret.headerName(), secret.value());
        }
    }

    private RunnerRunResult aggregate(List<RunnerCaseResult> results) {
        boolean timeout = results.stream().anyMatch(result -> "TIMEOUT".equals(result.status()));
        boolean failed = results.stream().anyMatch(result -> "FAILED".equals(result.status()) || "ERROR".equals(result.status()));
        if (timeout) {
            return new RunnerRunResult("TIMEOUT", "MANAGED", "RUNNER_TIMEOUT", "managed runner timed out", results);
        }
        if (failed) {
            return new RunnerRunResult("FAILED", "MANAGED", "RUNNER_FAILED", "managed runner found failed cases", results);
        }
        return new RunnerRunResult("PASSED", "MANAGED", null, null, results);
    }

    private URI targetUri(String baseUrl, String casePath) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(casePath)) {
            throw new IllegalArgumentException("baseUrl and case path are required");
        }
        String normalizedPath = normalizedCasePath(casePath);
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private String normalizedCasePath(String casePath) {
        String trimmed = casePath.trim();
        if (!trimmed.startsWith("/") || trimmed.contains("://") || trimmed.contains("?") || trimmed.contains("#")
                || trimmed.contains("\r") || trimmed.contains("\n")) {
            throw new IllegalArgumentException("case path is unsafe");
        }
        // OpenAPI template variables are not executable as-is; use a stable placeholder for managed execution.
        return trimmed.replaceAll("\\{[^}/]+}", "1");
    }

    private String normalizedMethod(String method) {
        return StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private RunnerCaseResult caseResult(
            ApiAutomationCase automationCase,
            String status,
            int durationMs,
            String errorCode,
            String errorSummary
    ) {
        return new RunnerCaseResult(
                automationCase.id(),
                status,
                Math.max(0, durationMs),
                assertionSummary(automationCase.expectedStatus(), null, false),
                errorCode,
                errorSummary
        );
    }

    private String assertionSummary(Integer expectedStatus, Integer actualStatus, boolean matched) {
        return """
                {"aggregateOnly":true,"rawRequestResponseStored":false,"secretValuesStored":false,"assertions":["STATUS_CODE"],"expectedStatus":%s,"actualStatus":%s,"matched":%s}
                """.formatted(
                expectedStatus == null ? "null" : expectedStatus,
                actualStatus == null ? "null" : actualStatus,
                matched
        ).trim();
    }

    private int durationMillis(Instant startedAt) {
        long millis = Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
