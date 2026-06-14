package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestDataExecutionAccountLeaseResponse(
        UUID accountLeaseRef,
        String projectId,
        String status,
        Instant expiresAt,
        Instant releasedAt,
        TestDataCrossWpAccountSummary account,
        Map<String, Object> policy
) {
}
