package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import java.util.List;
import java.util.UUID;

/**
 * Package-private dispatch preparation models extracted from the main dispatch support to keep orchestration code
 * within repository line-count guardrails.
 */
record ApiTestDispatchPreparation(
        UUID nodeRunId,
        String claimToken,
        UUID bundleId,
        String environmentId,
        String baseUrl,
        String baseUrlSource,
        String baseUrlRef,
        List<UUID> caseIds,
        int timeoutSeconds,
        List<String> secretRefs,
        boolean runtimeCaseIdsProvided,
        boolean runtimeSecretRefsProvided,
        ExecutionRunDetailResponse replayResponse
) {
    static ApiTestDispatchPreparation replay(ExecutionRunDetailResponse replayResponse) {
        return new ApiTestDispatchPreparation(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0,
                List.of(),
                false,
                false,
                replayResponse
        );
    }
}

record UiTestDispatchPreparation(
        UUID nodeRunId,
        String claimToken,
        String projectId,
        UUID sceneId,
        UUID bundleId,
        String environmentId,
        String baseUrlRef,
        UUID accountLeaseRef,
        String requestKey,
        String reason,
        ExecutionRunDetailResponse replayResponse
) {
    static UiTestDispatchPreparation replay(ExecutionRunDetailResponse replayResponse) {
        return new UiTestDispatchPreparation(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                replayResponse
        );
    }
}

record UiTestFollowUpPreparation(
        UUID nodeRunId,
        String claimToken,
        UUID wp7RunId,
        ExecutionRunDetailResponse replayResponse
) {
    static UiTestFollowUpPreparation replay(ExecutionRunDetailResponse replayResponse) {
        return new UiTestFollowUpPreparation(null, null, null, replayResponse);
    }
}

record ResolvedDispatchTarget(String baseUrl, String baseUrlSource, String baseUrlRef, String environmentKey) {
}
