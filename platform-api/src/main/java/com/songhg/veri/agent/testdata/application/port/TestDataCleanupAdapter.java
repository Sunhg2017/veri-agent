package com.songhg.veri.agent.testdata.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes the reviewed WP8 destructive cleanup contract outside the control-plane state machine.
 */
public interface TestDataCleanupAdapter {

    boolean ready();

    String provider();

    CleanupResult cleanup(CleanupRequest request);

    static TestDataCleanupAdapter disabled() {
        return new TestDataCleanupAdapter() {
            @Override
            public boolean ready() {
                return false;
            }

            @Override
            public String provider() {
                return "DISABLED";
            }

            @Override
            public CleanupResult cleanup(CleanupRequest request) {
                return CleanupResult.failure(
                        "CLEANUP_ADAPTER_DISABLED",
                        "WP8 cleanup adapter is not configured"
                );
            }
        };
    }

    record CleanupRequest(
            UUID taskId,
            String projectId,
            UUID dataSetId,
            String dataSetCode,
            String dataSetStatus,
            long recordCount,
            List<String> cleanupPolicyKeys,
            String taskType,
            String requestKey,
            String targetRef,
            int attempt,
            String workerId,
            Instant requestedAt
    ) {
    }

    record CleanupResult(
            boolean success,
            String externalCleanupId,
            long affectedResourceCount,
            Map<String, Object> summary,
            String errorCode,
            String errorSummary
    ) {
        public static CleanupResult success(
                String externalCleanupId,
                long affectedResourceCount,
                Map<String, Object> summary
        ) {
            return new CleanupResult(true, externalCleanupId, Math.max(0, affectedResourceCount), summary, null, null);
        }

        public static CleanupResult failure(String errorCode, String errorSummary) {
            return new CleanupResult(false, null, 0, Map.of(), errorCode, errorSummary);
        }
    }
}
