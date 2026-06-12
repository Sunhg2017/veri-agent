package com.songhg.veri.agent.apiautomation.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WP6 OpenAPI 接口自动化控制面的运行配置。
 */
@ConfigurationProperties(prefix = "veri-agent.api-automation")
public record ApiAutomationProperties(
        /** 单个 OpenAPI 源允许的最大字节数 */
        @DefaultValue("1048576") int specMaxBytes,
        /** 单个 OpenAPI 源允许解析的最大 endpoint 数 */
        @DefaultValue("500") int endpointMaxCount,
        /** 受控 runner 是否启用；默认关闭，避免开发环境误执行外部接口 */
        @DefaultValue("false") boolean runnerEnabled,
        /** 默认 runner 超时时间 */
        @DefaultValue("120") int runnerTimeoutSeconds,
        /** 默认 runner 单次运行用例上限 */
        @DefaultValue("100") int runnerMaxCases,
        /** runner 允许访问的 baseUrl host 或模式，逗号分隔；默认空表示未配置 */
        @DefaultValue("") String runnerAllowedBaseUrlPatterns,
        /** runner 产物摘要大小上限；默认 1 MB */
        @DefaultValue("1048576") int runnerArtifactMaxBytes,
        /** WP2 Prompt key */
        @DefaultValue("wp6-api-automation-v1") String promptKey,
        /** 模型失败时是否允许确定性模板兜底 */
        @DefaultValue("true") boolean modelFallbackEnabled,
        /** runner adapter 模式；默认托管 HTTP 探测，pytest 子进程必须显式启用 */
        @DefaultValue("managed-http") String runnerMode,
        /** pytest 子进程命令；仅 runner-mode=pytest-subprocess 时使用，不经 shell 执行 */
        @DefaultValue("python3 -m pytest") String runnerPytestCommand
) {
    private static final int DEFAULT_SPEC_MAX_BYTES = 1_048_576;
    private static final int MAX_SPEC_BYTES = 5 * 1_048_576;
    private static final int DEFAULT_ENDPOINT_MAX_COUNT = 500;
    private static final int MAX_ENDPOINT_COUNT = 2_000;
    private static final int DEFAULT_RUNNER_TIMEOUT_SECONDS = 120;
    private static final int MAX_RUNNER_TIMEOUT_SECONDS = 3_600;
    private static final int DEFAULT_RUNNER_MAX_CASES = 100;
    private static final int MAX_RUNNER_MAX_CASES = 1_000;
    private static final int DEFAULT_RUNNER_ARTIFACT_MAX_BYTES = 1_048_576;
    private static final int MAX_RUNNER_ARTIFACT_MAX_BYTES = 10 * 1_048_576;
    private static final String DEFAULT_RUNNER_MODE = "managed-http";
    private static final String DEFAULT_RUNNER_PYTEST_COMMAND = "python3 -m pytest";

    @ConstructorBinding
    public ApiAutomationProperties {
    }

    public ApiAutomationProperties(
            int specMaxBytes,
            int endpointMaxCount,
            boolean runnerEnabled,
            int runnerTimeoutSeconds,
            int runnerMaxCases,
            String promptKey,
            boolean modelFallbackEnabled
    ) {
        this(
                specMaxBytes,
                endpointMaxCount,
                runnerEnabled,
                runnerTimeoutSeconds,
                runnerMaxCases,
                "",
                DEFAULT_RUNNER_ARTIFACT_MAX_BYTES,
                promptKey,
                modelFallbackEnabled,
                DEFAULT_RUNNER_MODE,
                DEFAULT_RUNNER_PYTEST_COMMAND
        );
    }

    public ApiAutomationProperties(
            int specMaxBytes,
            int endpointMaxCount,
            boolean runnerEnabled,
            int runnerTimeoutSeconds,
            int runnerMaxCases,
            String runnerAllowedBaseUrlPatterns,
            int runnerArtifactMaxBytes,
            String promptKey,
            boolean modelFallbackEnabled
    ) {
        this(
                specMaxBytes,
                endpointMaxCount,
                runnerEnabled,
                runnerTimeoutSeconds,
                runnerMaxCases,
                runnerAllowedBaseUrlPatterns,
                runnerArtifactMaxBytes,
                promptKey,
                modelFallbackEnabled,
                DEFAULT_RUNNER_MODE,
                DEFAULT_RUNNER_PYTEST_COMMAND
        );
    }

    public int effectiveSpecMaxBytes() {
        return boundedPositive(specMaxBytes, DEFAULT_SPEC_MAX_BYTES, MAX_SPEC_BYTES);
    }

    public int effectiveEndpointMaxCount() {
        return boundedPositive(endpointMaxCount, DEFAULT_ENDPOINT_MAX_COUNT, MAX_ENDPOINT_COUNT);
    }

    public int effectiveRunnerTimeoutSeconds(Integer requestedTimeoutSeconds) {
        int requested = requestedTimeoutSeconds == null ? runnerTimeoutSeconds : requestedTimeoutSeconds;
        return boundedPositive(requested, DEFAULT_RUNNER_TIMEOUT_SECONDS, MAX_RUNNER_TIMEOUT_SECONDS);
    }

    public int effectiveRunnerMaxCases() {
        return boundedPositive(runnerMaxCases, DEFAULT_RUNNER_MAX_CASES, MAX_RUNNER_MAX_CASES);
    }

    public int effectiveRunnerArtifactMaxBytes() {
        return boundedPositive(runnerArtifactMaxBytes, DEFAULT_RUNNER_ARTIFACT_MAX_BYTES, MAX_RUNNER_ARTIFACT_MAX_BYTES);
    }

    public String effectiveRunnerMode() {
        String normalized = runnerMode == null
                ? DEFAULT_RUNNER_MODE
                : runnerMode.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "managed", "managed-http" -> "managed-http";
            case "pytest", "pytest-subprocess" -> "pytest-subprocess";
            default -> DEFAULT_RUNNER_MODE;
        };
    }

    public String effectiveRunnerPytestCommand() {
        if (runnerPytestCommand == null || runnerPytestCommand.trim().isEmpty()) {
            return DEFAULT_RUNNER_PYTEST_COMMAND;
        }
        return runnerPytestCommand.trim();
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
