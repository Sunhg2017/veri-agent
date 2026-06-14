package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Owns WP6 runner admission, persisted run/result construction, and cancel metadata.
 */
final class ApiAutomationRunSupport {

    private static final Set<String> RUN_STATUSES = Set.of("BLOCKED", "QUEUED", "RUNNING", "PASSED", "FAILED", "TIMEOUT", "CANCELED");
    private static final Set<String> RUN_RESULT_STATUSES = Set.of("PASSED", "FAILED", "SKIPPED", "ERROR", "TIMEOUT", "BLOCKED");

    private final ApiAutomationRunnerPort runnerPort;
    private final ApiAutomationProperties properties;
    private final ApiAutomationRunnerResultSanitizer runnerResultSanitizer;
    private final ApiAutomationJsonSupport jsonSupport;

    ApiAutomationRunSupport(
            ApiAutomationRunnerPort runnerPort,
            ApiAutomationProperties properties,
            ApiAutomationRunnerResultSanitizer runnerResultSanitizer,
            ApiAutomationJsonSupport jsonSupport
    ) {
        this.runnerPort = runnerPort;
        this.properties = properties;
        this.runnerResultSanitizer = runnerResultSanitizer;
        this.jsonSupport = jsonSupport;
    }

    RunBlock runBlockReason(
            ApiAutomationScriptBundle bundle,
            List<ApiAutomationCase> cases,
            ApiAutomationRunTargetGuard.RunTarget target
    ) {
        if (!"APPROVED".equals(bundle.status())) {
            return new RunBlock("RUNNER_BUNDLE_NOT_APPROVED", "脚本包未审批通过，不能运行");
        }
        if (!ApiAutomationScriptBundleFactory.STATIC_CHECK_PASSED.equals(bundle.staticCheckStatus())) {
            return new RunBlock(ApiAutomationScriptBundleFactory.STATIC_CHECK_FAILED, "脚本静态校验未通过，不能运行");
        }
        if (target.blocked() || properties.runnerEnabled() && !target.allowed()) {
            return new RunBlock("RUNNER_TARGET_BLOCKED", "runner 目标地址未通过安全策略");
        }
        if (!properties.runnerEnabled()) {
            return new RunBlock("RUNNER_DISABLED", "runner 默认关闭，未执行外部请求");
        }
        ApiAutomationRunnerPort.RunnerValidation validation = runnerPort.validateBundle(bundle);
        if (!validation.accepted()) {
            return new RunBlock(
                    StringUtils.hasText(validation.errorCode())
                            ? SensitiveTextSanitizer.boundedText(validation.errorCode(), 64)
                            : "RUNNER_FAILED",
                    StringUtils.hasText(validation.errorSummary())
                            ? runnerResultSanitizer.safeRunnerErrorSummary(validation.errorSummary())
                            : "runner 校验失败"
            );
        }
        if (cases.isEmpty()) {
            return new RunBlock("RUNNER_CASE_NOT_FOUND", "没有可运行的用例");
        }
        return null;
    }

    ApiAutomationRun newRun(
            UUID runId,
            ApiAutomationScriptBundle bundle,
            CreateApiAutomationRunCommand command,
            ApiAutomationRunTargetGuard.RunTarget target,
            String status,
            int timeoutSeconds,
            int caseCount,
            String runnerMode,
            String errorCode,
            String errorSummary,
            String actor,
            Instant now,
            Instant startedAt,
            Instant completedAt
    ) {
        return new ApiAutomationRun(
                runId,
                bundle.projectId(),
                bundle.id(),
                SensitiveTextSanitizer.boundedNullableText(command.environmentId(), 128),
                target.digest(),
                target.host(),
                status,
                timeoutSeconds,
                caseCount,
                TraceContext.getOrCreateTraceId(),
                runnerMode,
                errorCode,
                errorSummary,
                actor,
                actor,
                startedAt,
                completedAt,
                now,
                completedAt == null ? now : completedAt
        );
    }

    List<ApiAutomationRunResult> blockedRunResults(
            ApiAutomationRun run,
            List<ApiAutomationCase> cases,
            RunBlock block,
            Instant now
    ) {
        return cases.stream()
                .map(automationCase -> new ApiAutomationRunResult(
                        UUID.randomUUID(),
                        run.id(),
                        automationCase.id(),
                        "BLOCKED",
                        0,
                        jsonSupport.writeJson(Map.of(
                                "aggregateOnly", true,
                                "rawRequestResponseStored", false,
                                "reason", block.errorCode()
                        )),
                        block.errorCode(),
                        block.errorSummary(),
                        now,
                        now
                ))
                .toList();
    }

    List<ApiAutomationRunResult> runnerResults(
            ApiAutomationRun run,
            List<ApiAutomationCase> cases,
            ApiAutomationRunnerPort.RunnerRunResult attempt,
            String normalizedBaseUrl,
            Instant now
    ) {
        Map<UUID, ApiAutomationRunnerPort.RunnerCaseResult> resultByCaseId = attempt.caseResults() == null
                ? Map.of()
                : attempt.caseResults().stream()
                .filter(Objects::nonNull)
                .filter(result -> result.caseId() != null)
                .collect(Collectors.toMap(
                        ApiAutomationRunnerPort.RunnerCaseResult::caseId,
                        result -> result,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        return cases.stream()
                .map(automationCase -> {
                    ApiAutomationRunnerPort.RunnerCaseResult runnerResult = resultByCaseId.get(automationCase.id());
                    String status = runnerResult == null ? runResultStatusFromRun(run.status()) : normalizeRunResultStatus(runnerResult.status());
                    return new ApiAutomationRunResult(
                            UUID.randomUUID(),
                            run.id(),
                            automationCase.id(),
                            status,
                            runnerResult == null ? 0 : Math.max(0, runnerResult.durationMs()),
                            runnerResultSanitizer.safeAssertionSummary(runnerResult == null ? null : runnerResult.assertionSummaryJson()),
                            runnerResult == null ? run.errorCode() : SensitiveTextSanitizer.boundedNullableText(runnerResult.errorCode(), 64),
                            runnerResult == null
                                    ? run.errorSummary()
                                    : runnerResultSanitizer.safeRunnerErrorSummary(runnerResult.errorSummary(), normalizedBaseUrl),
                            now,
                            now
                    );
                })
                .toList();
    }

    String normalizeRunStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(java.util.Locale.ROOT) : "FAILED";
        return RUN_STATUSES.contains(normalized) ? normalized : "FAILED";
    }

    String normalizeRunnerMode(String runnerMode) {
        String normalized = StringUtils.hasText(runnerMode) ? runnerMode.trim().toUpperCase(java.util.Locale.ROOT) : "NOOP";
        return Set.of("DISABLED", "NOOP", "MANAGED", "EXTERNAL").contains(normalized) ? normalized : "NOOP";
    }

    String runAuditResult(String status) {
        return "PASSED".equals(status) ? "SUCCESS" : "FAILED";
    }

    boolean cancelableRunStatus(String status) {
        return Set.of("QUEUED", "RUNNING").contains(status);
    }

    ApiAutomationRun runWithCancel(
            ApiAutomationRun run,
            String errorCode,
            String errorSummary,
            String actor,
            Instant now
    ) {
        return new ApiAutomationRun(
                run.id(),
                run.projectId(),
                run.bundleId(),
                run.environmentId(),
                run.baseUrlDigest(),
                run.baseUrlHost(),
                "CANCELED",
                run.timeoutSeconds(),
                run.caseCount(),
                run.traceId(),
                run.runnerMode(),
                StringUtils.hasText(errorCode) ? errorCode : "RUNNER_CANCELED",
                errorSummary,
                run.createdBy(),
                actor,
                run.startedAt(),
                now,
                run.createdAt(),
                now
        );
    }

    Map<String, Object> cancelAuditPayload(
            ApiAutomationRun run,
            ApiAutomationRunnerPort.RunnerCancelResult cancelResult
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", run.status());
        payload.put("accepted", false);
        if (cancelResult != null) {
            String errorCode = SensitiveTextSanitizer.boundedNullableText(cancelResult.errorCode(), 64);
            String errorSummary = runnerResultSanitizer.safeRunnerErrorSummary(cancelResult.errorSummary());
            if (errorCode != null) {
                payload.put("errorCode", errorCode);
            }
            if (errorSummary != null) {
                payload.put("errorSummary", errorSummary);
            }
        }
        return payload;
    }

    private String normalizeRunResultStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(java.util.Locale.ROOT) : "ERROR";
        return RUN_RESULT_STATUSES.contains(normalized) ? normalized : "ERROR";
    }

    private String runResultStatusFromRun(String runStatus) {
        return switch (runStatus) {
            case "PASSED" -> "PASSED";
            case "TIMEOUT" -> "TIMEOUT";
            case "BLOCKED" -> "BLOCKED";
            default -> "ERROR";
        };
    }

    record RunBlock(
            String errorCode,
            String errorSummary
    ) {
    }
}
