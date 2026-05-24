package com.songhg.veri.agent.documentinput.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.UUID;

public record DocumentParseFeedbackQuery(
        UUID candidateId,
        UUID importId,
        String projectId,
        String parseSource,
        String curationStatus,
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
