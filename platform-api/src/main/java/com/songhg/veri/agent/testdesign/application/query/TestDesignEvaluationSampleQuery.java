package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

/**
 * Repository query for maintained WP5 evaluation samples.
 */
public record TestDesignEvaluationSampleQuery(
        String projectId,
        String promptKey,
        String promptVersion,
        String status,
        String coverageType,
        String baselineVersion,
        String keyword,
        PageQuery pageQuery
) {
    public int index() {
        return pageQuery == null ? 0 : pageQuery.index();
    }

    public int size() {
        return pageQuery == null ? 20 : pageQuery.size();
    }

    public int offset() {
        return pageQuery == null ? 0 : pageQuery.offset();
    }
}
