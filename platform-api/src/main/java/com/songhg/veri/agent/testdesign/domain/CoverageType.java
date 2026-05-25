package com.songhg.veri.agent.testdesign.domain;

import java.util.Set;

public enum CoverageType {
    SMOKE,
    FUNCTIONAL,
    EXCEPTION,
    BOUNDARY,
    PERMISSION,
    REGRESSION;

    public static Set<String> codes() {
        return Set.of(
                SMOKE.name(),
                FUNCTIONAL.name(),
                EXCEPTION.name(),
                BOUNDARY.name(),
                PERMISSION.name(),
                REGRESSION.name()
        );
    }
}
