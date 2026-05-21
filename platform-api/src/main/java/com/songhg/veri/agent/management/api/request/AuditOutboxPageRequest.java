package com.songhg.veri.agent.management.api.request;

public class AuditOutboxPageRequest extends ManagementPageRequest {

    private String status = "";
    private String traceId = "";

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId == null ? "" : traceId;
    }
}
