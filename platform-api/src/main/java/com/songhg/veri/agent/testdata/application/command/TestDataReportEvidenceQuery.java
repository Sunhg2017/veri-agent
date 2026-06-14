package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record TestDataReportEvidenceQuery(
        @NotBlank @Size(max = 64) String projectId,
        @Size(max = 128) String reportRef,
        List<UUID> dataSetRefs,
        List<UUID> accountLeaseRefs,
        List<UUID> cleanupTaskRefs
) {
}
