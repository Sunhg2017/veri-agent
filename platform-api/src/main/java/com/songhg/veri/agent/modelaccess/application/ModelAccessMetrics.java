package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ModelAccessMetrics {

    private final MeterRegistry meterRegistry;

    public ModelAccessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordInvocation(InvocationRecord record, ProviderType providerType) {
        String status = value(record.status());
        String sensitivityLevel = value(record.sensitivityLevel());
        String fallbackUsed = String.valueOf(record.fallbackUsed());
        String errorCode = value(record.errorCode());
        String provider = providerType == null ? "NONE" : providerType.name();

        Counter.builder("veri.agent.model_access.invocations")
                .description("WP2 model invocation audit rows by status and policy dimensions")
                .tag("status", status)
                .tag("sensitivity_level", sensitivityLevel)
                .tag("provider_type", provider)
                .tag("fallback_used", fallbackUsed)
                .tag("error_code", errorCode)
                .register(meterRegistry)
                .increment();

        Timer.builder("veri.agent.model_access.invocation.latency")
                .description("WP2 model invocation latency including policy checks and provider call")
                .tag("status", status)
                .tag("provider_type", provider)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, record.latencyMs())));

        recordTokens("input", record.inputTokens(), provider, sensitivityLevel);
        recordTokens("output", record.outputTokens(), provider, sensitivityLevel);
        recordCost(record.totalCost(), provider, sensitivityLevel);
    }

    public void recordProviderCheck(ProviderCheckResponse response) {
        Counter.builder("veri.agent.model_access.provider.checks")
                .description("WP2 provider readiness checks by provider type and result")
                .tag("provider_type", value(response.providerType()))
                .tag("status", value(response.status()))
                .tag("error_code", value(response.errorCode()))
                .register(meterRegistry)
                .increment();

        Timer.builder("veri.agent.model_access.provider.check.latency")
                .description("WP2 provider readiness check latency")
                .tag("provider_type", value(response.providerType()))
                .tag("status", value(response.status()))
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, response.latencyMs())));
    }

    public void recordAuditEvent(String result) {
        Counter.builder("veri.agent.model_access.platform.audit.events")
                .description("WP2 attempts to write sanitized invocation audit events to WP1")
                .tag("result", value(result))
                .register(meterRegistry)
                .increment();
    }

    private void recordTokens(String direction, int tokens, String providerType, String sensitivityLevel) {
        if (tokens <= 0) {
            return;
        }
        DistributionSummary.builder("veri.agent.model_access.tokens")
                .description("WP2 model invocation token usage")
                .baseUnit("tokens")
                .tag("direction", direction)
                .tag("provider_type", providerType)
                .tag("sensitivity_level", sensitivityLevel)
                .register(meterRegistry)
                .record(tokens);
    }

    private void recordCost(BigDecimal cost, String providerType, String sensitivityLevel) {
        if (cost == null || cost.signum() <= 0) {
            return;
        }
        DistributionSummary.builder("veri.agent.model_access.cost")
                .description("WP2 model invocation cost")
                .baseUnit("currency")
                .tag("provider_type", providerType)
                .tag("sensitivity_level", sensitivityLevel)
                .register(meterRegistry)
                .record(cost.doubleValue());
    }

    private String value(Object value) {
        return value == null ? "NONE" : String.valueOf(value);
    }
}
