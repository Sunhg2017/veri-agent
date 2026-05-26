package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;

public record DocumentSourceQuery(
        /** 文档来源类型过滤条件 */
        DocumentSourceType sourceType,
        /** 文档来源状态过滤条件 */
        DocumentSourceStatus status,
        /** 分页参数 */
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
}
