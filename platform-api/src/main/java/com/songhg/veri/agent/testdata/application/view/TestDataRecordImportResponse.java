package com.songhg.veri.agent.testdata.application.view;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataRecordImportResponse(
        UUID dataSetId,
        int importedCount,
        List<TestDataRecordResponse> records,
        Map<String, Object> policy
) {
}
