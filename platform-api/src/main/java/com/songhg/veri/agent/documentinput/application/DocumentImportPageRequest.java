package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.documentinput.domain.DocumentImportStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.util.UUID;

public class DocumentImportPageRequest extends BasePageRequest {

    private String projectId;
    private UUID sourceId;
    private DocumentSourceType sourceType;
    private DocumentImportStatus status;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public DocumentSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DocumentSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public DocumentImportStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentImportStatus status) {
        this.status = status;
    }
}
