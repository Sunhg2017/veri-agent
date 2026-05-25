package com.songhg.veri.agent.testdesign.domain;

import java.util.Set;

public enum TestDesignCandidateStatus {
    GENERATED,
    EDITED,
    CONFIRMED,
    REJECTED,
    IGNORED,
    PUBLISHED,
    FAILED;

    public static Set<String> codes() {
        return Set.of(
                GENERATED.name(),
                EDITED.name(),
                CONFIRMED.name(),
                REJECTED.name(),
                IGNORED.name(),
                PUBLISHED.name(),
                FAILED.name()
        );
    }
}
