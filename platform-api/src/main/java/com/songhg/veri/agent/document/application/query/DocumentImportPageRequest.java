package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public class DocumentImportPageRequest extends BasePageRequest {

    @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
    private String projectId;
    private UUID sourceId;
    private DocumentSourceType sourceType;
    @Schema(description = "业务状态")
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
