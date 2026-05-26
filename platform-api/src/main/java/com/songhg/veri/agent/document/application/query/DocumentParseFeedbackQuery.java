package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.UUID;

public record DocumentParseFeedbackQuery(
        /** 候选需求 ID 过滤条件 */
        UUID candidateId,
        /** 导入批次 ID 过滤条件 */
        UUID importId,
        /** 所属项目 ID 过滤条件 */
        String projectId,
        /** 解析来源过滤条件 */
        String parseSource,
        /** 人工整理状态过滤条件 */
        String curationStatus,
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
