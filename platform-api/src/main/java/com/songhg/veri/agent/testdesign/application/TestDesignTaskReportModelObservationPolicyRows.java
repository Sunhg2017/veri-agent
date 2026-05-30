package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportModelObservationPolicyRows {

    private TestDesignTaskReportModelObservationPolicyRows() {
    }

    /**
     * Appends model observation export boundaries without copying model invocation identifiers or payload previews.
     *
     * <p>WP5 reports need enough evidence to reconcile WP2 routing, token, latency and cost signals, while trace IDs,
     * job IDs, invocation IDs, prompt payloads, model request/response previews and provider error text must remain out
     * of the archived CSV. These rows therefore expose only booleans and bounded aggregate counts.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        TestDesignModelObservationResponse observation = task.modelObservation();
        TestDesignModelObservationPolicyResponse policy = task.modelObservationPolicy() == null
                ? TestDesignModelObservationPolicy.response()
                : task.modelObservationPolicy();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", policy.policyVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "observationMode", policy.observationMode(), null);
        appendMetadataRow(csv, task, generatedAt, "wp2InvocationReferenceTracked",
                task.modelInvocationId() != null || observation != null,
                task.modelInvocationId() != null || observation != null ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "traceIdTracked",
                observation != null && StringUtils.hasText(observation.traceId()), null);
        appendMetadataRow(csv, task, generatedAt, "jobIdTracked",
                observation != null && observation.jobId() != null, null);
        appendMetadataRow(csv, task, generatedAt, "routingMetadataTracked", routingMetadataCount(observation) > 0L, null);
        appendMetadataRow(csv, task, generatedAt, "tokenUsageTracked", tokenUsageMetricCount(observation) > 0L, null);
        appendMetadataRow(csv, task, generatedAt, "latencyTracked",
                observation != null && observation.latencyMs() != null, null);
        appendMetadataRow(csv, task, generatedAt, "costTracked",
                observation != null && observation.totalCost() != null, null);
        appendMetadataRow(csv, task, generatedAt, "fallbackTracked",
                observation != null && observation.fallbackUsed() != null, null);
        appendMetadataRow(csv, task, generatedAt, "promptPayloadStored", policy.promptPayloadStored(), null);
        appendMetadataRow(csv, task, generatedAt, "payloadPreviewExported", policy.payloadPreviewExported(), null);
        appendMetadataRow(csv, task, generatedAt, "traceIdValueExported", policy.traceIdValueExported(), null);
        appendMetadataRow(csv, task, generatedAt, "jobIdValueExported", policy.jobIdValueExported(), null);
        appendMetadataRow(csv, task, generatedAt, "invocationIdValueExported",
                policy.invocationIdValueExported(), null);
        appendMetadataRow(csv, task, generatedAt, "providerErrorTextExported",
                policy.providerErrorTextExported(), null);
        appendMetadataRow(csv, task, generatedAt, "actorServiceExported", policy.actorServiceExported(), null);
        appendMetricRow(csv, task, generatedAt, "routingMetadataFieldCount", routingMetadataCount(observation));
        appendMetricRow(csv, task, generatedAt, "tokenUsageMetricCount", tokenUsageMetricCount(observation));
        appendMetricRow(csv, task, generatedAt, "costMetricCount",
                observation != null && observation.totalCost() != null ? 1L : 0L);
        appendMetricRow(csv, task, generatedAt, "latencyMetricCount",
                observation != null && observation.latencyMs() != null ? 1L : 0L);
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success");
    }

    private static long routingMetadataCount(TestDesignModelObservationResponse observation) {
        if (observation == null) {
            return 0L;
        }
        long count = 0L;
        count += hasText(observation.providerName());
        count += hasText(observation.modelName());
        count += hasText(observation.routingRuleName());
        count += hasText(observation.routingGroup());
        count += hasText(observation.modelCapability());
        return count;
    }

    private static long tokenUsageMetricCount(TestDesignModelObservationResponse observation) {
        if (observation == null) {
            return 0L;
        }
        long count = 0L;
        count += observation.inputTokens() == null ? 0L : 1L;
        count += observation.outputTokens() == null ? 0L : 1L;
        return count;
    }

    private static long hasText(String value) {
        return StringUtils.hasText(value) ? 1L : 0L;
    }

    private static void appendMetadataRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String metric,
            Object value,
            String tone
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "modelObservationPolicy", metric, null, value, null, tone, "fullTask", null);
    }

    private static void appendMetricRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String label,
            long value
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "modelObservationPolicy", "metric", label, value, null,
                value > 0L ? "info" : "neutral", "fullTask", null);
    }
}
