package com.songhg.veri.agent.management.api.request;

public class AuditLogPageRequest extends ManagementPageRequest {

    private String actor = "";
    private String action = "";
    private String resourceType = "";
    private String result = "";
    private String startTime = "";
    private String endTime = "";

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor == null ? "" : actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action == null ? "" : action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType == null ? "" : resourceType;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result == null ? "" : result;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime == null ? "" : startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime == null ? "" : endTime;
    }
}
