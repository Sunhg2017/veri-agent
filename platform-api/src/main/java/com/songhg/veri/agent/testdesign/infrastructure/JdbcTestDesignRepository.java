package com.songhg.veri.agent.testdesign.infrastructure;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
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
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpOperationsAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSample;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSampleSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignOperationsAuditAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignQueueAlertSubscription;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessApproval;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTemplate;
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
    public List<TestDesignTemplate> templates(TestDesignTemplateQuery query) {
        return mapper.templates(query);
    }

    @Override
    public long countTemplates(TestDesignTemplateQuery query) {
        return mapper.countTemplates(query);
    }

    @Override
    public Optional<TestDesignTemplate> template(UUID id) {
        return Optional.ofNullable(mapper.template(id));
    }

    @Override
    public Optional<TestDesignTemplate> templateByScopeAndName(String projectId, String name) {
        return Optional.ofNullable(mapper.templateByScopeAndName(projectId, name));
    }

    @Override
    public TestDesignTemplate saveTemplate(TestDesignTemplate template) {
        if (mapper.template(template.id()) == null) {
            mapper.insertTemplate(template);
        } else {
            mapper.updateTemplate(template);
        }
        return template;
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
    public long countTasksByStatus(TestDesignTaskStatus status) {
        return status == null ? 0L : mapper.countTasksByStatus(status.name());
    }

    @Override
    public Optional<Instant> oldestTaskUpdatedAtByStatus(TestDesignTaskStatus status) {
        return status == null ? Optional.empty() : Optional.ofNullable(mapper.oldestTaskUpdatedAtByStatus(status.name()));
    }

    @Override
    public long countTasksByStatus(String projectId, String promptKey, TestDesignTaskStatus status) {
        return status == null ? 0L : mapper.countTasksByStatusInScope(projectId, promptKey, status.name());
    }

    @Override
    public Optional<Instant> oldestTaskUpdatedAtByStatus(
            String projectId,
            String promptKey,
            TestDesignTaskStatus status
    ) {
        return status == null ? Optional.empty()
                : Optional.ofNullable(mapper.oldestTaskUpdatedAtByStatusInScope(projectId, promptKey, status.name()));
    }

    @Override
    public long countStaleRunningTasks(Instant staleBefore) {
        return staleBefore == null ? 0L : mapper.countStaleRunningTasks(staleBefore);
    }

    @Override
    public long countStaleRunningTasks(String projectId, String promptKey, Instant staleBefore) {
        return staleBefore == null ? 0L : mapper.countStaleRunningTasksInScope(projectId, promptKey, staleBefore);
    }

    @Override
    public long countCandidatesByStatus(TestDesignCandidateStatus status) {
        return status == null ? 0L : mapper.countCandidatesByStatus(status.name());
    }

    @Override
    public Optional<Instant> oldestCandidateUpdatedAtByStatus(TestDesignCandidateStatus status) {
        return status == null ? Optional.empty()
                : Optional.ofNullable(mapper.oldestCandidateUpdatedAtByStatus(status.name()));
    }

    @Override
    public long countCandidatesByStatus(String projectId, String promptKey, TestDesignCandidateStatus status) {
        return status == null ? 0L : mapper.countCandidatesByStatusInScope(projectId, promptKey, status.name());
    }

    @Override
    public Optional<Instant> oldestCandidateUpdatedAtByStatus(
            String projectId,
            String promptKey,
            TestDesignCandidateStatus status
    ) {
        return status == null ? Optional.empty()
                : Optional.ofNullable(mapper.oldestCandidateUpdatedAtByStatusInScope(
                        projectId,
                        promptKey,
                        status.name()
                ));
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
    public boolean markCandidateStatus(
            UUID id,
            TestDesignCandidateStatus expectedStatus,
            TestDesignCandidateStatus nextStatus,
            Instant updatedAt
    ) {
        return mapper.markCandidateStatus(id, expectedStatus.name(), nextStatus.name(), updatedAt) > 0;
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
    public List<TestDesignCandidate> publishQueuedCandidates(int limit) {
        return mapper.publishQueuedCandidates(limit);
    }

    @Override
    public List<TestDesignTask> queuedTasksForReplay(String projectId, String promptKey, int limit) {
        return mapper.queuedTasksForReplay(projectId, promptKey, limit);
    }

    @Override
    public List<TestDesignCandidate> publishQueuedCandidatesForReplay(String projectId, String promptKey, int limit) {
        return mapper.publishQueuedCandidatesForReplay(projectId, promptKey, limit);
    }

    @Override
    public int markStalePublishingCandidatesFailed(
            Instant failedAt,
            Instant staleBefore,
            String errorMessage,
            int limit
    ) {
        return mapper.markStalePublishingCandidatesFailed(failedAt, staleBefore, errorMessage, limit);
    }

    @Override
    public long countStalePublishingCandidates(Instant staleBefore) {
        return staleBefore == null ? 0L : mapper.countStalePublishingCandidates(staleBefore);
    }

    @Override
    public long countStalePublishingCandidates(String projectId, String promptKey, Instant staleBefore) {
        return staleBefore == null ? 0L : mapper.countStalePublishingCandidatesInScope(projectId, promptKey, staleBefore);
    }

    @Override
    public List<TestDesignCandidate> publishCompensationCandidates(int limit) {
        return mapper.publishCompensationCandidates(limit);
    }

    @Override
    public List<TestDesignCandidate> publishCompensationCandidates(String projectId, String promptKey, int limit) {
        return mapper.publishCompensationCandidatesInScope(projectId, promptKey, limit);
    }

    @Override
    public long countPublishCompensationCandidates(String projectId, String promptKey) {
        return mapper.countPublishCompensationCandidates(projectId, promptKey);
    }

    @Override
    public void lockPublishCompensationCandidate(UUID candidateId) {
        mapper.lockPublishCompensationCandidate("wp5:publish-compensation:" + candidateId);
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
    public List<TestDesignReviewRecord> reviewRecords(UUID taskId, PageQuery pageQuery) {
        return mapper.reviewRecords(taskId, pageQuery);
    }

    @Override
    public List<TestDesignReviewRecord> reviewRecordsByTask(UUID taskId) {
        return mapper.reviewRecordsByTask(taskId);
    }

    @Override
    public long countReviewRecords(UUID taskId) {
        return mapper.countReviewRecords(taskId);
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

    @Override
    public List<TestDesignConflictOperationRecord> conflictOperations(TestDesignConflictOperationQuery query) {
        return mapper.conflictOperations(query);
    }

    @Override
    public long countConflictOperations(TestDesignConflictOperationQuery query) {
        return mapper.countConflictOperations(query);
    }

    @Override
    public TestDesignConflictOperationSummary conflictOperationSummary(TestDesignConflictOperationQuery query) {
        TestDesignConflictOperationSummary summary = mapper.conflictOperationSummary(query);
        return summary == null ? emptyConflictOperationSummary() : summary;
    }

    @Override
    public TestDesignReportManifest saveReportManifest(TestDesignReportManifest manifest) {
        mapper.insertReportManifest(manifest);
        return mapper.reportManifestsByTask(manifest.taskId()).stream()
                .filter(current -> manifest.schemaVersion().equals(current.schemaVersion()))
                .filter(current -> manifest.fieldSetVersion().equals(current.fieldSetVersion()))
                .filter(current -> manifest.contentDigest().equals(current.contentDigest()))
                .findFirst()
                .orElse(manifest);
    }

    @Override
    public List<TestDesignReportManifest> reportManifestsByTask(UUID taskId) {
        return mapper.reportManifestsByTask(taskId);
    }

    @Override
    public TestDesignAuditChainAggregate auditChainAggregate(UUID taskId) {
        TestDesignAuditChainAggregate aggregate = mapper.auditChainAggregate(taskId);
        return aggregate == null ? emptyAuditChainAggregate() : aggregate;
    }

    @Override
    public TestDesignCrossWpOperationsAggregate crossWpOperationsAggregate(String projectId, String promptKey) {
        TestDesignCrossWpOperationsAggregate aggregate = mapper.crossWpOperationsAggregate(projectId, promptKey);
        return aggregate == null ? emptyCrossWpOperationsAggregate() : aggregate;
    }

    @Override
    public int requeueAuditOutbox(
            String projectId,
            String status,
            int limit,
            String reason,
            String actor,
            Instant now
    ) {
        return mapper.requeueAuditOutbox(projectId, status, limit, reason, actor, now);
    }

    @Override
    public List<TestDesignQueueAlertSubscription> queueAlertSubscriptions(String projectId, String promptKey) {
        return mapper.queueAlertSubscriptions(projectId, promptKey);
    }

    @Override
    public Optional<TestDesignQueueAlertSubscription> queueAlertSubscription(UUID id) {
        return Optional.ofNullable(mapper.queueAlertSubscription(id));
    }

    @Override
    public Optional<TestDesignQueueAlertSubscription> queueAlertSubscriptionByKey(
            String projectId,
            String promptKey,
            String alertType,
            String channel,
            String targetRef
    ) {
        return Optional.ofNullable(mapper.queueAlertSubscriptionByKey(
                projectId,
                promptKey,
                alertType,
                channel,
                targetRef
        ));
    }

    @Override
    public TestDesignQueueAlertSubscription saveQueueAlertSubscription(
            TestDesignQueueAlertSubscription subscription
    ) {
        if (mapper.queueAlertSubscription(subscription.id()) == null) {
            mapper.insertQueueAlertSubscription(subscription);
        } else {
            mapper.updateQueueAlertSubscription(subscription);
        }
        return mapper.queueAlertSubscription(subscription.id());
    }

    @Override
    public TestDesignOperationsAuditAggregate operationsAuditAggregate(String projectId, String promptKey) {
        TestDesignOperationsAuditAggregate aggregate = mapper.operationsAuditAggregate(projectId, promptKey);
        return aggregate == null ? emptyOperationsAuditAggregate() : aggregate;
    }

    @Override
    public TestDesignContextPolicyOverride saveContextPolicyOverride(TestDesignContextPolicyOverride override) {
        if (mapper.contextPolicyOverride(override.id()) == null) {
            mapper.insertContextPolicyOverride(override);
        } else {
            mapper.updateContextPolicyOverride(override);
        }
        return mapper.contextPolicyOverride(override.id());
    }

    @Override
    public TestDesignContextPolicyNote saveContextPolicyNote(TestDesignContextPolicyNote note) {
        mapper.insertContextPolicyNote(note);
        return note;
    }

    @Override
    public Optional<TestDesignContextPolicyOverride> contextPolicyOverride(UUID id) {
        return Optional.ofNullable(mapper.contextPolicyOverride(id));
    }

    @Override
    public List<TestDesignContextPolicyOverride> contextPolicyOverrides(String projectId, String environmentKey) {
        return mapper.contextPolicyOverrides(projectId, environmentKey);
    }

    @Override
    public List<TestDesignContextPolicyNote> contextPolicyNotes(UUID overrideId) {
        return mapper.contextPolicyNotes(overrideId);
    }

    @Override
    public Optional<TestDesignContextPolicyOverride> latestApprovedProjectContextPolicyOverride(String projectId) {
        return Optional.ofNullable(mapper.latestApprovedProjectContextPolicyOverride(projectId));
    }

    @Override
    public Optional<TestDesignContextPolicyOverride> latestApprovedEnvironmentContextPolicyOverride(
            String projectId,
            String environmentKey
    ) {
        return Optional.ofNullable(mapper.latestApprovedEnvironmentContextPolicyOverride(projectId, environmentKey));
    }

    @Override
    public TestDesignReleaseReadinessApproval saveReleaseReadinessApproval(TestDesignReleaseReadinessApproval approval) {
        if (mapper.releaseReadinessApproval(approval.id()) == null) {
            mapper.insertReleaseReadinessApproval(approval);
        } else {
            mapper.updateReleaseReadinessApproval(approval);
        }
        return mapper.releaseReadinessApproval(approval.id());
    }

    @Override
    public TestDesignReleaseReadinessNote saveReleaseReadinessNote(TestDesignReleaseReadinessNote note) {
        mapper.insertReleaseReadinessNote(note);
        return note;
    }

    @Override
    public Optional<TestDesignReleaseReadinessApproval> releaseReadinessApproval(UUID id) {
        return Optional.ofNullable(mapper.releaseReadinessApproval(id));
    }

    @Override
    public List<TestDesignReleaseReadinessApproval> releaseReadinessApprovals(UUID taskId) {
        return mapper.releaseReadinessApprovals(taskId);
    }

    @Override
    public List<TestDesignReleaseReadinessNote> releaseReadinessNotes(UUID approvalId) {
        return mapper.releaseReadinessNotes(approvalId);
    }

    @Override
    public Optional<TestDesignReleaseReadinessApproval> latestApprovedReleaseReadinessApproval(UUID taskId) {
        return Optional.ofNullable(mapper.latestApprovedReleaseReadinessApproval(taskId));
    }

    @Override
    public List<TestDesignEvaluationSample> evaluationSamples(TestDesignEvaluationSampleQuery query) {
        return mapper.evaluationSamples(query);
    }

    @Override
    public long countEvaluationSamples(TestDesignEvaluationSampleQuery query) {
        return mapper.countEvaluationSamples(query);
    }

    @Override
    public Optional<TestDesignEvaluationSample> evaluationSample(UUID id) {
        return Optional.ofNullable(mapper.evaluationSample(id));
    }

    @Override
    public Optional<TestDesignEvaluationSample> evaluationSampleByProjectAndKey(String projectId, String sampleKey) {
        return Optional.ofNullable(mapper.evaluationSampleByProjectAndKey(projectId, sampleKey));
    }

    @Override
    public TestDesignEvaluationSample saveEvaluationSample(TestDesignEvaluationSample sample) {
        if (mapper.evaluationSample(sample.id()) == null) {
            mapper.insertEvaluationSample(sample);
        } else {
            mapper.updateEvaluationSample(sample);
        }
        return mapper.evaluationSample(sample.id());
    }

    @Override
    public TestDesignEvaluationSampleSummary evaluationSampleSummary(String projectId, String promptKey) {
        TestDesignEvaluationSampleSummary summary = mapper.evaluationSampleSummary(projectId, promptKey);
        return summary == null ? emptyEvaluationSampleSummary() : summary;
    }

    @Override
    public List<TestDesignCalibrationRun> calibrationRuns(TestDesignCalibrationRunQuery query) {
        return mapper.calibrationRuns(query);
    }

    @Override
    public long countCalibrationRuns(TestDesignCalibrationRunQuery query) {
        return mapper.countCalibrationRuns(query);
    }

    @Override
    public TestDesignCalibrationRun saveCalibrationRun(TestDesignCalibrationRun run) {
        mapper.insertCalibrationRun(run);
        return run;
    }

    @Override
    public TestDesignCalibrationSummary calibrationSummary(String projectId, String promptKey) {
        TestDesignCalibrationSummary summary = mapper.calibrationSummary(projectId, promptKey);
        return summary == null ? emptyCalibrationSummary() : summary;
    }

    @Override
    public Optional<TestDesignCalibrationRun> latestCalibrationRun(
            String projectId,
            String promptKey,
            String promptVersion
    ) {
        return Optional.ofNullable(mapper.latestCalibrationRun(projectId, promptKey, promptVersion));
    }

    private static TestDesignAuditChainAggregate emptyAuditChainAggregate() {
        return new TestDesignAuditChainAggregate(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                "0", 0, 0, 0, 0, 0, 0, 0
        );
    }

    private static TestDesignCrossWpOperationsAggregate emptyCrossWpOperationsAggregate() {
        return new TestDesignCrossWpOperationsAggregate(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );
    }

    private static TestDesignOperationsAuditAggregate emptyOperationsAuditAggregate() {
        return new TestDesignOperationsAuditAggregate(0, 0, 0, 0, 0, 0, 0, 0, null);
    }

    private static TestDesignConflictOperationSummary emptyConflictOperationSummary() {
        return new TestDesignConflictOperationSummary(0, 0, 0, 0, null);
    }

    private static TestDesignEvaluationSampleSummary emptyEvaluationSampleSummary() {
        return new TestDesignEvaluationSampleSummary(0, 0, 0, 0, 0, 0, null);
    }

    private static TestDesignCalibrationSummary emptyCalibrationSummary() {
        return new TestDesignCalibrationSummary(0, 0, 0, 0, null, null);
    }
}
