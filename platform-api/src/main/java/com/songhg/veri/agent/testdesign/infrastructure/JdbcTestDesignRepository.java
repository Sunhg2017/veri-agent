package com.songhg.veri.agent.testdesign.infrastructure;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTemplateQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
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
    public long countStaleRunningTasks(Instant staleBefore) {
        return staleBefore == null ? 0L : mapper.countStaleRunningTasks(staleBefore);
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
    public List<TestDesignCandidate> publishCompensationCandidates(int limit) {
        return mapper.publishCompensationCandidates(limit);
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

    private static TestDesignAuditChainAggregate emptyAuditChainAggregate() {
        return new TestDesignAuditChainAggregate(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                "0", 0, 0, 0, 0, 0, 0, 0
        );
    }
}
