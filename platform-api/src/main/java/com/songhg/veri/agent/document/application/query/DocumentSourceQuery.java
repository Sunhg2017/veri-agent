package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;

public record DocumentSourceQuery(
        DocumentSourceType sourceType,
        DocumentSourceStatus status,
        PageQuery pageQuery
) {

    public int index() {
        return pageQuery.index();
    }

    public int size() {
        return pageQuery.size();
    }

    public int offset() {
        return pageQuery.offset();
    }
}
