package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class DocumentCandidatePageRequest extends BasePageRequest {
    @Schema(description = "候选生命周期状态")
    private DocumentCandidateStatus status;
    private String sourceRef;
    private String keyword;

    public DocumentCandidateStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentCandidateStatus status) {
        this.status = status;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public DocumentCandidateQuery toQuery(UUID importId) {
        return new DocumentCandidateQuery(
                importId,
                status,
                trimToNull(sourceRef),
                trimToNull(keyword),
                toPageQuery()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
