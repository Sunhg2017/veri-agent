package com.songhg.veri.agent.testdesign.application.port;

import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestDesignRepository {

    List<TestDesignTask> tasks(TestDesignTaskQuery query);

    long countTasks(TestDesignTaskQuery query);

    Optional<TestDesignTask> task(UUID id);

    Optional<TestDesignTask> taskByIdempotencyKey(String projectId, String idempotencyKey);

    /**
     * Serializes project-local idempotent creation attempts when the backing store supports transactional locks.
     */
    default void lockTaskIdempotencyKey(String projectId, String idempotencyKey) {
    }

    TestDesignTask saveTask(TestDesignTask task);

    List<TestDesignCandidate> candidates(TestDesignCandidateQuery query);

    long countCandidates(TestDesignCandidateQuery query);

    List<TestDesignCandidate> candidatesByTask(UUID taskId);

    Optional<TestDesignCandidate> candidate(UUID id);

    TestDesignCandidate saveCandidate(TestDesignCandidate candidate);

    TestDesignReviewRecord saveReviewRecord(TestDesignReviewRecord record);

    TestDesignPublishRecord savePublishRecord(TestDesignPublishRecord record);

    List<TestDesignPublishRecord> publishRecords(UUID taskId);
}
