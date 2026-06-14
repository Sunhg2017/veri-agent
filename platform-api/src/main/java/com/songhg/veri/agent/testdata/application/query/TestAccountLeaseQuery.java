package com.songhg.veri.agent.testdata.application.query;

import java.util.UUID;

public record TestAccountLeaseQuery(
        String projectId,
        UUID poolId,
        UUID accountId,
        String status,
        String holderRef,
        long offset,
        int limit
) {
}
