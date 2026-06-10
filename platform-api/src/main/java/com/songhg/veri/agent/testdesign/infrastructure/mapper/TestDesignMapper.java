package com.songhg.veri.agent.testdesign.infrastructure.mapper;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCalibrationRunQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignConflictOperationQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationSampleQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTemplateQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationRun;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpAuditDetailBucket;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpOperationsAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSample;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSampleSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignModelObservationBucket;
import com.songhg.veri.agent.testdesign.domain.TestDesignOperationsAuditAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignQueueAlertSubscription;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessApproval;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestDesignMapper {

    List<TestDesignTemplate> templates(@Param("query") TestDesignTemplateQuery query);

    long countTemplates(@Param("query") TestDesignTemplateQuery query);

    TestDesignTemplate template(@Param("id") UUID id);

    TestDesignTemplate templateByScopeAndName(
            @Param("projectId") String projectId,
            @Param("name") String name
    );

    void insertTemplate(TestDesignTemplate template);

    void updateTemplate(TestDesignTemplate template);

    List<TestDesignTask> tasks(@Param("query") TestDesignTaskQuery query);

    long countTasks(@Param("query") TestDesignTaskQuery query);

    long countTasksByStatus(@Param("status") String status);

    Instant oldestTaskUpdatedAtByStatus(@Param("status") String status);

    long countTasksByStatusInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("status") String status
    );

    Instant oldestTaskUpdatedAtByStatusInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("status") String status
    );

    long countStaleRunningTasks(@Param("staleBefore") Instant staleBefore);

    long countStaleRunningTasksInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("staleBefore") Instant staleBefore
    );

    long countCandidatesByStatus(@Param("status") String status);

    Instant oldestCandidateUpdatedAtByStatus(@Param("status") String status);

    long countCandidatesByStatusInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("status") String status
    );

    Instant oldestCandidateUpdatedAtByStatusInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("status") String status
    );

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

    int markCandidateStatus(
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

    List<TestDesignCandidate> publishQueuedCandidates(@Param("limit") int limit);

    List<TestDesignTask> queuedTasksForReplay(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("limit") int limit
    );

    List<TestDesignCandidate> publishQueuedCandidatesForReplay(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("limit") int limit
    );

    int markStalePublishingCandidatesFailed(
            @Param("failedAt") Instant failedAt,
            @Param("staleBefore") Instant staleBefore,
            @Param("errorMessage") String errorMessage,
            @Param("limit") int limit
    );

    long countStalePublishingCandidates(@Param("staleBefore") Instant staleBefore);

    long countStalePublishingCandidatesInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("staleBefore") Instant staleBefore
    );

    List<TestDesignCandidate> publishCompensationCandidates(@Param("limit") int limit);

    List<TestDesignCandidate> publishCompensationCandidatesInScope(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("limit") int limit
    );

    long countPublishCompensationCandidates(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

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

    List<TestDesignConflictOperationRecord> conflictOperations(
            @Param("query") TestDesignConflictOperationQuery query
    );

    long countConflictOperations(@Param("query") TestDesignConflictOperationQuery query);

    TestDesignConflictOperationSummary conflictOperationSummary(
            @Param("query") TestDesignConflictOperationQuery query
    );

    void insertReportManifest(TestDesignReportManifest manifest);

    List<TestDesignReportManifest> reportManifestsByTask(@Param("taskId") UUID taskId);

    TestDesignAuditChainAggregate auditChainAggregate(@Param("taskId") UUID taskId);

    TestDesignCrossWpOperationsAggregate crossWpOperationsAggregate(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    int requeueAuditOutbox(
            @Param("projectId") String projectId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("reason") String reason,
            @Param("actor") String actor,
            @Param("now") Instant now
    );

    List<TestDesignQueueAlertSubscription> queueAlertSubscriptions(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    TestDesignQueueAlertSubscription queueAlertSubscription(@Param("id") UUID id);

    TestDesignQueueAlertSubscription queueAlertSubscriptionByKey(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("alertType") String alertType,
            @Param("channel") String channel,
            @Param("targetRef") String targetRef
    );

    void insertQueueAlertSubscription(TestDesignQueueAlertSubscription subscription);

    void updateQueueAlertSubscription(TestDesignQueueAlertSubscription subscription);

    TestDesignOperationsAuditAggregate operationsAuditAggregate(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    List<TestDesignModelObservationBucket> modelObservationBuckets(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    List<TestDesignCrossWpAuditDetailBucket> crossWpAuditDetailBuckets(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    void insertContextPolicyOverride(TestDesignContextPolicyOverride override);

    void updateContextPolicyOverride(TestDesignContextPolicyOverride override);

    void insertContextPolicyNote(TestDesignContextPolicyNote note);

    TestDesignContextPolicyOverride contextPolicyOverride(@Param("id") UUID id);

    List<TestDesignContextPolicyOverride> contextPolicyOverrides(
            @Param("projectId") String projectId,
            @Param("environmentKey") String environmentKey
    );

    List<TestDesignContextPolicyNote> contextPolicyNotes(@Param("overrideId") UUID overrideId);

    TestDesignContextPolicyOverride latestApprovedProjectContextPolicyOverride(@Param("projectId") String projectId);

    TestDesignContextPolicyOverride latestApprovedEnvironmentContextPolicyOverride(
            @Param("projectId") String projectId,
            @Param("environmentKey") String environmentKey
    );

    void insertReleaseReadinessApproval(TestDesignReleaseReadinessApproval approval);

    void updateReleaseReadinessApproval(TestDesignReleaseReadinessApproval approval);

    void insertReleaseReadinessNote(TestDesignReleaseReadinessNote note);

    TestDesignReleaseReadinessApproval releaseReadinessApproval(@Param("id") UUID id);

    List<TestDesignReleaseReadinessApproval> releaseReadinessApprovals(@Param("taskId") UUID taskId);

    List<TestDesignReleaseReadinessNote> releaseReadinessNotes(@Param("approvalId") UUID approvalId);

    TestDesignReleaseReadinessApproval latestApprovedReleaseReadinessApproval(@Param("taskId") UUID taskId);

    List<TestDesignEvaluationSample> evaluationSamples(@Param("query") TestDesignEvaluationSampleQuery query);

    long countEvaluationSamples(@Param("query") TestDesignEvaluationSampleQuery query);

    TestDesignEvaluationSample evaluationSample(@Param("id") UUID id);

    TestDesignEvaluationSample evaluationSampleByProjectAndKey(
            @Param("projectId") String projectId,
            @Param("sampleKey") String sampleKey
    );

    void insertEvaluationSample(TestDesignEvaluationSample sample);

    void updateEvaluationSample(TestDesignEvaluationSample sample);

    TestDesignEvaluationSampleSummary evaluationSampleSummary(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    List<TestDesignCalibrationRun> calibrationRuns(@Param("query") TestDesignCalibrationRunQuery query);

    long countCalibrationRuns(@Param("query") TestDesignCalibrationRunQuery query);

    void insertCalibrationRun(TestDesignCalibrationRun run);

    TestDesignCalibrationSummary calibrationSummary(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey
    );

    TestDesignCalibrationRun latestCalibrationRun(
            @Param("projectId") String projectId,
            @Param("promptKey") String promptKey,
            @Param("promptVersion") String promptVersion
    );
}
