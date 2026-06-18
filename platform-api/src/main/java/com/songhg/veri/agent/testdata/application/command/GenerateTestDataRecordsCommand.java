package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerateTestDataRecordsCommand(
        @NotNull @Min(1) @Max(200) Integer count,
        @Size(max = 96) String recordKeyPrefix,
        List<@Size(max = 64) String> tags
) {
}
