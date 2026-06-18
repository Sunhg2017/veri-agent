package com.songhg.veri.agent.testdata.application.view;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataRecordGenerationResponse(
        UUID dataSetId,
        int generatedCount,
        List<TestDataRecordResponse> records,
        Map<String, Object> policy
) {
}
