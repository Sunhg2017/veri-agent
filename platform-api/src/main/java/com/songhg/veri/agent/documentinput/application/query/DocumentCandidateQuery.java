package com.songhg.veri.agent.documentinput.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentCandidateStatus;
import java.util.UUID;

public record DocumentCandidateQuery(
        UUID importId,
        DocumentCandidateStatus status,
        String sourceRef,
        String keyword,
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
