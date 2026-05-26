package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.UUID;

/**
 * WP5 候选列表的应用层查询条件。
 */
public record TestDesignCandidateQuery(
        /** 任务 ID 过滤条件。 */
        UUID taskId,
        /** 所属项目 ID 过滤条件。 */
        String projectId,
        /** 关联需求 ID 过滤条件。 */
        UUID requirementId,
        /** 候选用例状态过滤条件。 */
        String status,
        /** 覆盖类型过滤条件。 */
        String coverageType,
        /** 标题、说明等文本搜索关键字。 */
        String keyword,
        /** 分页参数。 */
        PageQuery page
) {
    public int index() {
        return page.index();
    }

    public int size() {
        return page.size();
    }

    public int offset() {
        return page.offset();
    }
}
