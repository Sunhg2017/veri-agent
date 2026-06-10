package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.PageQuery;

/**
 * Repository query for WP5 long-term prompt calibration runs.
 */
public record TestDesignCalibrationRunQuery(
        String projectId,
        String promptKey,
        String promptVersion,
        String baselineVersion,
        String status,
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
