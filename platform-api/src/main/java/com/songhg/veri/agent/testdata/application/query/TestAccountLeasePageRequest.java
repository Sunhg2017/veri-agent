package com.songhg.veri.agent.testdata.application.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class TestAccountLeasePageRequest {

    @Size(max = 64)
    private String projectId;
    private UUID poolId;
    private UUID accountId;
    @Size(max = 32)
    private String status;
    @Size(max = 128)
    private String holderRef;
    @Min(0)
    private int index = 0;
    @Min(1)
    @Max(100)
    private int size = 20;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getPoolId() {
        return poolId;
    }

    public void setPoolId(UUID poolId) {
        this.poolId = poolId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHolderRef() {
        return holderRef;
    }

    public void setHolderRef(String holderRef) {
        this.holderRef = holderRef;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = Math.max(index, 0);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.min(Math.max(size, 1), 100);
    }

    public TestAccountLeaseQuery toQuery() {
        return new TestAccountLeaseQuery(
                clean(projectId),
                poolId,
                accountId,
                clean(status),
                clean(holderRef),
                (long) index * size,
                size
        );
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
