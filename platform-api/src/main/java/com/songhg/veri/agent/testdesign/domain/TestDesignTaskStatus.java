package com.songhg.veri.agent.testdesign.domain;

import java.util.Set;

public enum TestDesignTaskStatus {
    DRAFT,
    RUNNING,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED,
    PUBLISHING,
    PUBLISHED;

    public static Set<String> codes() {
        return Set.of(
                DRAFT.name(),
                RUNNING.name(),
                SUCCEEDED.name(),
                PARTIAL_SUCCESS.name(),
                FAILED.name(),
                CANCELLED.name(),
                PUBLISHING.name(),
                PUBLISHED.name()
        );
    }
}
