package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.UUID;

public record TestDesignCandidateQuery(
        UUID taskId,
        String projectId,
        UUID requirementId,
        String status,
        String coverageType,
        String keyword,
        PageQuery page
) {
    public int index() {
        return page.index();
    }

    public int size() {
        return page.size();
    }

    public int offset() {
        return page.offset();
    }
}
