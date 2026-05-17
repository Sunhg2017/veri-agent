package com.songhg.veri.agent.management.api;

import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @Size(max = 64)
        String name
) {
}
