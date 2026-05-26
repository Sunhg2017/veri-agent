package com.songhg.veri.agent.modelaccess.api.request;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public class InvocationPageRequest extends BasePageRequest {

    @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
    private String projectId;
    private String applicationId;
    private String sensitivityLevel;
    @Schema(description = "业务状态。")
    private InvocationStatus status;
    private UUID providerId;
    private String actorService;
    @Schema(description = "查询开始时间。")
    private Instant startTime;
    private Instant endTime;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getSensitivityLevel() {
        return sensitivityLevel;
    }

    public void setSensitivityLevel(String sensitivityLevel) {
        this.sensitivityLevel = sensitivityLevel;
    }

    public InvocationStatus getStatus() {
        return status;
    }

    public void setStatus(InvocationStatus status) {
        this.status = status;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public String getActorService() {
        return actorService;
    }

    public void setActorService(String actorService) {
        this.actorService = actorService;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}
