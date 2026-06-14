package com.songhg.veri.agent.testdata.application.view;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataCrossWpAccountSummary(
        UUID accountRef,
        UUID accountPoolRef,
        String projectId,
        String accountKey,
        String displayName,
        String status,
        List<String> roleTags,
        Map<String, Object> scopeSummary,
        String secretRefDigest,
        String lastHealthStatus
) {
}
