package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

public record TestDesignTaskQuery(
        String projectId,
        String status,
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
