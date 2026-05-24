package com.songhg.veri.agent.management.application.view;

public record DepartmentView(
        String name,
        String parent,
        String lead,
        int members,
        String status
) {
}
