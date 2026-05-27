package com.songhg.veri.agent.testdesign.infrastructure;

import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.infrastructure.mapper.TestDesignMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcTestDesignRepository implements TestDesignRepository {

    private final TestDesignMapper mapper;

    public JdbcTestDesignRepository(TestDesignMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TestDesignTask> tasks(TestDesignTaskQuery query) {
        return mapper.tasks(query);
    }

    @Override
    public long countTasks(TestDesignTaskQuery query) {
        return mapper.countTasks(query);
    }

    @Override
    public Optional<TestDesignTask> task(UUID id) {
        return Optional.ofNullable(mapper.task(id));
    }

    @Override
    public Optional<TestDesignTask> taskByIdempotencyKey(String projectId, String idempotencyKey) {
        return Optional.ofNullable(mapper.taskByIdempotencyKey(projectId, idempotencyKey));
    }

    @Override
    public void lockTaskIdempotencyKey(String projectId, String idempotencyKey) {
        mapper.lockTaskIdempotencyKey("wp5:test-design-task:" + projectId + ":" + idempotencyKey);
    }

    @Override
    public TestDesignTask saveTask(TestDesignTask task) {
        if (mapper.task(task.id()) == null) {
            mapper.insertTask(task);
        } else {
            mapper.updateTask(task);
        }
        return task;
    }

    @Override
    public boolean markTaskStatus(
            UUID id,
            TestDesignTaskStatus expectedStatus,
            TestDesignTaskStatus nextStatus,
            Instant updatedAt
    ) {
        return mapper.markTaskStatus(id, expectedStatus.name(), nextStatus.name(), updatedAt) > 0;
    }

    @Override
    public int markStaleRunningTasksFailed(Instant failedAt, Instant staleBefore, String errorMessage, int limit) {
        return mapper.markStaleRunningTasksFailed(failedAt, staleBefore, errorMessage, limit);
    }

    @Override
    public List<TestDesignCandidate> candidates(TestDesignCandidateQuery query) {
        return mapper.candidates(query);
    }

    @Override
    public long countCandidates(TestDesignCandidateQuery query) {
        return mapper.countCandidates(query);
    }

    @Override
    public List<TestDesignCandidate> candidatesByTask(UUID taskId) {
        return mapper.candidatesByTask(taskId);
    }

    @Override
    public Optional<TestDesignCandidate> candidate(UUID id) {
        return Optional.ofNullable(mapper.candidate(id));
    }

    @Override
    public TestDesignCandidate saveCandidate(TestDesignCandidate candidate) {
        if (mapper.candidate(candidate.id()) == null) {
            mapper.insertCandidate(candidate);
        } else {
            mapper.updateCandidate(candidate);
        }
        return candidate;
    }

    @Override
    public TestDesignReviewRecord saveReviewRecord(TestDesignReviewRecord record) {
        mapper.insertReviewRecord(record);
        return record;
    }

    @Override
    public TestDesignPublishRecord savePublishRecord(TestDesignPublishRecord record) {
        mapper.insertPublishRecord(record);
        return record;
    }

    @Override
    public List<TestDesignPublishRecord> publishRecords(UUID taskId) {
        return mapper.publishRecords(taskId);
    }
}
