package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationPolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 model-observation visibility boundary.
 *
 * <p>WP5 task diagnostics can expose bounded WP2 routing, token, latency, cost and fallback signals for operations
 * triage. Prompt payloads, payload previews, provider error bodies, actor service details and concrete trace/job/
 * invocation identifiers stay out of policy snapshots and report exports because they can reveal tenant activity,
 * internal routing and raw model context.
 */
public final class TestDesignModelObservationPolicy {

    public static final String POLICY_VERSION = "wp5-model-observation-policy-v1";
    public static final String OBSERVATION_MODE = "ROUTING_COST_LATENCY_AGGREGATE";

    private TestDesignModelObservationPolicy() {
    }

    public static TestDesignModelObservationPolicyResponse response() {
        return new TestDesignModelObservationPolicyResponse(
                POLICY_VERSION,
                OBSERVATION_MODE,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        TestDesignModelObservationPolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("observationMode", response.observationMode());
        snapshot.put("wp2InvocationReferenceTracked", response.wp2InvocationReferenceTracked());
        snapshot.put("traceIdTracked", response.traceIdTracked());
        snapshot.put("jobIdTracked", response.jobIdTracked());
        snapshot.put("routingMetadataTracked", response.routingMetadataTracked());
        snapshot.put("tokenUsageTracked", response.tokenUsageTracked());
        snapshot.put("latencyTracked", response.latencyTracked());
        snapshot.put("costTracked", response.costTracked());
        snapshot.put("fallbackTracked", response.fallbackTracked());
        snapshot.put("promptPayloadStored", response.promptPayloadStored());
        snapshot.put("payloadPreviewExported", response.payloadPreviewExported());
        snapshot.put("traceIdValueExported", response.traceIdValueExported());
        snapshot.put("jobIdValueExported", response.jobIdValueExported());
        snapshot.put("invocationIdValueExported", response.invocationIdValueExported());
        snapshot.put("providerErrorTextExported", response.providerErrorTextExported());
        snapshot.put("actorServiceExported", response.actorServiceExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
