package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record RetryTestDataTaskCommand(
        @Size(max = 128) String requestKey,
        Map<String, Object> resultSummary
) {
}
