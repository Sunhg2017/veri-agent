package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

/**
 * WP5 任务列表的应用层查询条件。
 */
public record TestDesignTaskQuery(
        /** 所属项目 ID 过滤条件。 */
        String projectId,
        /** 任务状态过滤条件。 */
        String status,
        /** 任务名称或说明搜索关键字。 */
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
