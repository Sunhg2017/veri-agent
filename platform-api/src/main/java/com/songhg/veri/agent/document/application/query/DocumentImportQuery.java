package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import java.util.UUID;

public record DocumentImportQuery(
        /** 所属项目 ID 过滤条件。 */
        String projectId,
        /** 文档来源 ID 过滤条件。 */
        UUID sourceId,
        /** 文档来源类型过滤条件。 */
        DocumentSourceType sourceType,
        /** 导入状态过滤条件。 */
        DocumentImportStatus status,
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
