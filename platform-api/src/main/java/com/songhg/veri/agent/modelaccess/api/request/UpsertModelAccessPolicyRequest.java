package com.songhg.veri.agent.modelaccess.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public class UpsertModelAccessPolicyRequest {

    @Schema(description = "策略作用域: PLATFORM/ROLE/PROJECT/ENVIRONMENT")
    private String scopeType;
    private String scopeKey;
    private Boolean enabled;
    private Boolean modelInvocationEnabled;
    private Boolean publicModelAllowed;
    private BigDecimal dailyBudgetLimit;
    private BigDecimal costAlertWarningRatio;
    private String budgetOverrunAction;
    private String routingGroup;
    private String reason;

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getModelInvocationEnabled() {
        return modelInvocationEnabled;
    }

    public void setModelInvocationEnabled(Boolean modelInvocationEnabled) {
        this.modelInvocationEnabled = modelInvocationEnabled;
    }

    public Boolean getPublicModelAllowed() {
        return publicModelAllowed;
    }

    public void setPublicModelAllowed(Boolean publicModelAllowed) {
        this.publicModelAllowed = publicModelAllowed;
    }

    public BigDecimal getDailyBudgetLimit() {
        return dailyBudgetLimit;
    }

    public void setDailyBudgetLimit(BigDecimal dailyBudgetLimit) {
        this.dailyBudgetLimit = dailyBudgetLimit;
    }

    public BigDecimal getCostAlertWarningRatio() {
        return costAlertWarningRatio;
    }

    public void setCostAlertWarningRatio(BigDecimal costAlertWarningRatio) {
        this.costAlertWarningRatio = costAlertWarningRatio;
    }

    public String getBudgetOverrunAction() {
        return budgetOverrunAction;
    }

    public void setBudgetOverrunAction(String budgetOverrunAction) {
        this.budgetOverrunAction = budgetOverrunAction;
    }

    public String getRoutingGroup() {
        return routingGroup;
    }

    public void setRoutingGroup(String routingGroup) {
        this.routingGroup = routingGroup;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
