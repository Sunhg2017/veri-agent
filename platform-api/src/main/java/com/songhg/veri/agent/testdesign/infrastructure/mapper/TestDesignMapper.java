package com.songhg.veri.agent.testdesign.infrastructure.mapper;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestDesignMapper {

    List<TestDesignTask> tasks(@Param("query") TestDesignTaskQuery query);

    long countTasks(@Param("query") TestDesignTaskQuery query);

    long countTasksByStatus(@Param("status") String status);

    Instant oldestTaskUpdatedAtByStatus(@Param("status") String status);

    long countStaleRunningTasks(@Param("staleBefore") Instant staleBefore);

    TestDesignTask task(@Param("id") UUID id);

    TestDesignTask taskByIdempotencyKey(
            @Param("projectId") String projectId,
            @Param("idempotencyKey") String idempotencyKey
    );

    int lockTaskIdempotencyKey(@Param("lockKey") String lockKey);

    void insertTask(TestDesignTask task);

    void updateTask(TestDesignTask task);

    int markTaskStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("updatedAt") Instant updatedAt
    );

    int markStaleRunningTasksFailed(
            @Param("failedAt") Instant failedAt,
            @Param("staleBefore") Instant staleBefore,
            @Param("errorMessage") String errorMessage,
            @Param("limit") int limit
    );

    List<TestDesignCandidate> candidates(@Param("query") TestDesignCandidateQuery query);

    long countCandidates(@Param("query") TestDesignCandidateQuery query);

    List<TestDesignCandidate> candidatesByTask(@Param("taskId") UUID taskId);

    List<TestDesignCandidate> publishCompensationCandidates(@Param("limit") int limit);

    int lockPublishCompensationCandidate(@Param("lockKey") String lockKey);

    TestDesignCandidate candidate(@Param("id") UUID id);

    void insertCandidate(TestDesignCandidate candidate);

    void updateCandidate(TestDesignCandidate candidate);

    void insertReviewRecord(TestDesignReviewRecord record);

    List<TestDesignReviewRecord> reviewRecords(
            @Param("taskId") UUID taskId,
            @Param("pageQuery") PageQuery pageQuery
    );

    List<TestDesignReviewRecord> reviewRecordsByTask(@Param("taskId") UUID taskId);

    long countReviewRecords(@Param("taskId") UUID taskId);

    void insertPublishRecord(TestDesignPublishRecord record);

    List<TestDesignPublishRecord> publishRecords(@Param("taskId") UUID taskId);

    void insertReportManifest(TestDesignReportManifest manifest);

    List<TestDesignReportManifest> reportManifestsByTask(@Param("taskId") UUID taskId);

    TestDesignAuditChainAggregate auditChainAggregate(@Param("taskId") UUID taskId);
}
