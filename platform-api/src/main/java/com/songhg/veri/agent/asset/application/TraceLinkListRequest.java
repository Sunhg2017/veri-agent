package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.common.api.BasePageRequest;
import java.util.UUID;

public class TraceLinkListRequest extends BasePageRequest {

    private UUID requirementId;
    private UUID apiId;
    private UUID pageId;
    private UUID flowId;
    private UUID caseId;

    public UUID getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(UUID requirementId) {
        this.requirementId = requirementId;
    }

    public UUID getApiId() {
        return apiId;
    }

    public void setApiId(UUID apiId) {
        this.apiId = apiId;
    }

    public UUID getPageId() {
        return pageId;
    }

    public void setPageId(UUID pageId) {
        this.pageId = pageId;
    }

    public UUID getFlowId() {
        return flowId;
    }

    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }
}
