package com.songhg.veri.agent.asset.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

public record AssetListQuery(
        /** 所属项目 ID，用于权限过滤和租户数据隔离 */
        String projectId,
        /** 生命周期状态过滤条件，未传时默认查询 ACTIVE */
        String lifecycleStatus,
        /** 业务审核状态过滤条件 */
        String status,
        /** 资产来源过滤条件 */
        String source,
        /** 标题、编码等文本字段的搜索关键字 */
        String keyword,
        /** 分页参数，进入查询前会按平台限制归一化 */
        PageQuery pageQuery
) {

    public AssetListQuery {
        projectId = trimToNull(projectId);
        lifecycleStatus = trimToNull(lifecycleStatus);
        lifecycleStatus = lifecycleStatus == null ? "ACTIVE" : lifecycleStatus;
        status = trimToNull(status);
        source = trimToNull(source);
        keyword = trimToNull(keyword);
        pageQuery = pageQuery == null ? PageQuery.of(0, 20) : PageQuery.of(pageQuery.index(), pageQuery.size());
    }

    public int index() {
        return pageQuery.index();
    }

    public int size() {
        return pageQuery.size();
    }

    public int offset() {
        return pageQuery.offset();
    }

    /**
     * Returns a literal, case-insensitive SQL LIKE pattern for PostgreSQL trigram indexes.
     * Escaping preserves the old position() substring semantics for %, _, and ! input.
     */
    public String keywordLikePattern() {
        if (keyword == null) {
            return null;
        }
        String escaped = keyword.toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
