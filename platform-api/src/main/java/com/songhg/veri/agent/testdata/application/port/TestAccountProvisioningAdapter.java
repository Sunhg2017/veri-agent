package com.songhg.veri.agent.testdata.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opens business test accounts through a reviewed adapter and returns only a secretRef pointer for WP8 storage.
 */
public interface TestAccountProvisioningAdapter {

    boolean ready();

    String provider();

    ProvisionedAccount provision(ProvisioningRequest request);

    static TestAccountProvisioningAdapter disabled() {
        return new TestAccountProvisioningAdapter() {
            @Override
            public boolean ready() {
                return false;
            }

            @Override
            public String provider() {
                return "DISABLED";
            }

            @Override
            public ProvisionedAccount provision(ProvisioningRequest request) {
                throw new IllegalStateException("WP8 account provisioning adapter is not configured");
            }
        };
    }

    record ProvisioningRequest(
            UUID poolId,
            String projectId,
            String applicationId,
            String environmentId,
            String poolCode,
            String accountKey,
            String displayName,
            List<String> roleTags,
            Map<String, Object> scopeSummary,
            String secretRef,
            String workerId,
            Instant requestedAt
    ) {
    }

    record ProvisionedAccount(
            String accountKey,
            String displayName,
            List<String> roleTags,
            Map<String, Object> scopeSummary,
            String secretRef,
            String healthStatus,
            String healthSummary,
            Map<String, Object> summary
    ) {
        public static ProvisionedAccount fromRequest(ProvisioningRequest request) {
            return new ProvisionedAccount(
                    request.accountKey(),
                    request.displayName(),
                    request.roleTags(),
                    request.scopeSummary(),
                    request.secretRef(),
                    "HEALTHY",
                    "provisioned by WP8 adapter",
                    Map.of("adapterAccepted", true)
            );
        }
    }
}
