package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.Size;

public record UpdateDepartmentCommand(
        @Size(max = 64)
        String name
) {
}
