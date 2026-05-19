package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentImportStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.util.UUID;

public record DocumentImportQuery(
        String projectId,
        UUID sourceId,
        DocumentSourceType sourceType,
        DocumentImportStatus status,
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
