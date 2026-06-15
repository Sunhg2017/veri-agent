package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ImportTestDataRecordsCommand(
        @NotEmpty @Size(max = 500) List<@Valid RecordItem> records
) {

    public record RecordItem(
            @jakarta.validation.constraints.NotBlank @Size(max = 128) String recordKey,
            @jakarta.validation.constraints.NotBlank @Size(max = 64) String recordDigest,
            Map<String, Object> maskedSummary,
            @Size(max = 64) String externalRefDigest,
            List<@Size(max = 64) String> tags
    ) {
    }
}
