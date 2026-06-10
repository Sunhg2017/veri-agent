package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.UUID;

/**
 * 应用层资产冲突运营查询条件。
 */
public record TestDesignConflictOperationQuery(
        String projectId,
        UUID taskId,
        String action,
        String result,
        String candidateStatus,
        String resolutionStatus,
        String keyword,
        PageQuery pageQuery
) {

    public int index() {
        return pageQuery.index();
    }

    public int size() {
        return pageQuery.size();
    }

    public int offset() {
        return pageQuery.offset();
    }

    public TestDesignConflictOperationQuery withoutResolutionStatus() {
        return new TestDesignConflictOperationQuery(
                projectId,
                taskId,
                action,
                result,
                candidateStatus,
                "ALL",
                keyword,
                pageQuery
        );
    }
}
