package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

/**
 * WP5 生成模板列表的应用层查询条件。
 */
public record TestDesignTemplateQuery(
        /** 所属项目 ID 过滤条件 */
        String projectId,
        /** 是否只返回启用或禁用模板 */
        Boolean enabled,
        /** 名称、说明或 Prompt 标识搜索关键字 */
        String keyword,
        /** 查询项目模板时是否包含平台全局模板 */
        boolean includeGlobal,
        /** 分页参数 */
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
