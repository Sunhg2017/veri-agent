package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignContextAssemblyPolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 context assembly safety snapshot used by diagnostics, model payloads and reports.
 *
 * <p>WP5 currently assembles a redacted, digest-backed context snapshot from WP3/WP4-facing summaries. Keeping these
 * boundaries in one place prevents health checks, task responses, model payloads and archive reports from drifting on
 * whether raw context bodies, explicit asset identifiers or model payloads are allowed to persist or export.
 */
public final class TestDesignContextAssemblyPolicy {

    public static final String POLICY_VERSION = "wp5-context-assembly-policy-v2";
    public static final String ASSEMBLY_MODE = "SNAPSHOT_DIGEST_ONLY";
    public static final String DIGEST_STRATEGY = "SHA256_CONTEXT_SUMMARY";

    private TestDesignContextAssemblyPolicy() {
    }

    public static TestDesignContextAssemblyPolicyResponse response() {
        return new TestDesignContextAssemblyPolicyResponse(
                POLICY_VERSION,
                ASSEMBLY_MODE,
                DIGEST_STRATEGY,
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
                false,
                false,
                true
        );
    }

    public static TestDesignContextAssemblyPolicyResponse response(
            TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy
    ) {
        return response();
    }

    public static Map<String, Object> snapshot() {
        TestDesignContextAssemblyPolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("assemblyMode", response.assemblyMode());
        snapshot.put("digestStrategy", response.digestStrategy());
        snapshot.put("inputDigestRequired", response.inputDigestRequired());
        snapshot.put("persistedContextSummaryOnly", response.persistedContextSummaryOnly());
        snapshot.put("wp3ApplicationServiceOnly", response.wp3ApplicationServiceOnly());
        snapshot.put("rawContextBodyStored", response.rawContextBodyStored());
        snapshot.put("modelPayloadStored", response.modelPayloadStored());
        snapshot.put("digestValueExported", response.digestValueExported());
        snapshot.put("requirementBodyExported", response.requirementBodyExported());
        snapshot.put("assetSchemaExported", response.assetSchemaExported());
        snapshot.put("pageTreeExported", response.pageTreeExported());
        snapshot.put("flowJsonExported", response.flowJsonExported());
        snapshot.put("explicitAssetIdentifierListExported", response.explicitAssetIdentifierListExported());
        snapshot.put("historicalCaseStepExported", response.historicalCaseStepExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
