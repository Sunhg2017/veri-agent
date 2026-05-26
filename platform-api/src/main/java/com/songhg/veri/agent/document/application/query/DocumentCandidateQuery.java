package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import java.util.UUID;

public record DocumentCandidateQuery(
        /** 导入批次 ID 过滤条件。 */
        UUID importId,
        /** 候选需求状态过滤条件。 */
        DocumentCandidateStatus status,
        /** 外部来源引用过滤条件。 */
        String sourceRef,
        /** 标题、正文等文本搜索关键字。 */
        String keyword,
        /** 分页参数。 */
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
