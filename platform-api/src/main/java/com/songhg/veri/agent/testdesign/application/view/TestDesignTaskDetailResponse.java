package com.songhg.veri.agent.testdesign.application.view;

import java.util.List;

public record TestDesignTaskDetailResponse(
        TestDesignTaskResponse task,
        List<TestDesignCandidateResponse> candidates,
        List<TestDesignPublishRecordResponse> publishRecords
) {
}
