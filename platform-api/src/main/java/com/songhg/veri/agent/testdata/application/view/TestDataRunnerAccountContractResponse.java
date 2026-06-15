package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestDataRunnerAccountContractResponse(
        UUID accountLeaseRef,
        String status,
        Instant expiresAt,
        TestDataCrossWpAccountSummary account,
        Map<String, Object> credentialPolicy
) {
}
