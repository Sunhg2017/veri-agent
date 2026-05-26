package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import io.swagger.v3.oas.annotations.media.Schema;

public class DocumentSourcePageRequest extends BasePageRequest {

    @Schema(description = "文档或数据源类型")
    private DocumentSourceType sourceType;
    private DocumentSourceStatus status;

    public DocumentSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DocumentSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public DocumentSourceStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentSourceStatus status) {
        this.status = status;
    }
}
