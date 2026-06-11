package com.songhg.veri.agent.apiautomation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
        /** WP2 Prompt key */
        @DefaultValue("wp6-api-automation-v1") String promptKey,
        /** 模型失败时是否允许确定性模板兜底 */
        @DefaultValue("true") boolean modelFallbackEnabled
) {
    private static final int DEFAULT_SPEC_MAX_BYTES = 1_048_576;
    private static final int MAX_SPEC_BYTES = 5 * 1_048_576;
    private static final int DEFAULT_ENDPOINT_MAX_COUNT = 500;
    private static final int MAX_ENDPOINT_COUNT = 2_000;

    public int effectiveSpecMaxBytes() {
        return boundedPositive(specMaxBytes, DEFAULT_SPEC_MAX_BYTES, MAX_SPEC_BYTES);
    }

    public int effectiveEndpointMaxCount() {
        return boundedPositive(endpointMaxCount, DEFAULT_ENDPOINT_MAX_COUNT, MAX_ENDPOINT_COUNT);
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
