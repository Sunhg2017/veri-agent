package com.songhg.veri.agent.asset.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

public record AssetListQuery(
        String projectId,
        String lifecycleStatus,
        String status,
        String source,
        String keyword,
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

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
