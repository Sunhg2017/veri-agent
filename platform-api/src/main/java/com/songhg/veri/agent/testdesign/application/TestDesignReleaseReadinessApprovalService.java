package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.AddTestDesignReleaseReadinessNoteCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignReleaseReadinessApprovalCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignReleaseReadinessApprovalCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessApprovalResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessNoteResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessApproval;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages task-level release-readiness approvals and quality-gate exceptions.
 *
 * <p>Approval decisions are bound to a digest of aggregate readiness checks. If reviewers edit candidates after an
 * exception is approved, publish must re-evaluate the digest and require a fresh approval for the new aggregate state.</p>
 */
@Service
public class TestDesignReleaseReadinessApprovalService {

    static final String READINESS_BLOCKED = "BLOCKED";

    private static final int MAX_EXCEPTION_TEXT_CHARS = 1000;
    private static final List<String> ALLOWED_REASON_CODES = List.of(
            "BUSINESS_CRITICAL_RELEASE",
            "FALSE_POSITIVE_QUALITY_GATE",
            "LOW_RISK_ACCEPTANCE",
            "TIME_BOXED_EXCEPTION",
            "SMOKE_VALIDATION"
    );

    private final TestDesignRepository repository;
    private final TestDesignQualityService qualityService;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignPlatformContextClient contextClient;

    public TestDesignReleaseReadinessApprovalService(
            TestDesignRepository repository,
            TestDesignQualityService qualityService,
            TestDesignActorResolver actorResolver,
            TestDesignPlatformContextClient contextClient
    ) {
        this.repository = repository;
        this.qualityService = qualityService;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
    }

    /**
     * Creates a pending release-readiness exception for the task's current blocked aggregate readiness.
     */
    @Transactional
    public TestDesignReleaseReadinessApprovalResponse requestApproval(
            UUID taskId,
            RequestTestDesignReleaseReadinessApprovalCommand command
    ) {
        TestDesignTask task = taskOrThrow(taskId);
        ReadinessSnapshot snapshot = blockedReadinessSnapshot(taskId);
        RequestTestDesignReleaseReadinessApprovalCommand safeCommand = command == null
                ? new RequestTestDesignReleaseReadinessApprovalCommand(null, null, null, null, null, null, null)
                : command;
        UUID approvalId = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        String exceptionSummary = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.exceptionSummary(), "exceptionSummary", MAX_EXCEPTION_TEXT_CHARS, true, true
        );
        String riskMitigation = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.riskMitigation(), "riskMitigation", MAX_EXCEPTION_TEXT_CHARS, true, true
        );
        String requestNote = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.requestNote(), "requestNote", TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS, true, false
        );
        TestDesignReleaseReadinessApproval approval = new TestDesignReleaseReadinessApproval(
                approvalId,
                task.id(),
                task.projectId(),
                TestDesignApprovalWorkflowSupport.STATUS_PENDING,
                snapshot.status(),
                snapshot.blockingCount(),
                snapshot.warningCount(),
                snapshot.digest(),
                TestDesignApprovalWorkflowSupport.reasonCode(
                        safeCommand.exceptionReasonCode(), "exceptionReasonCode", ALLOWED_REASON_CODES
                ),
                null,
                TestDesignApprovalWorkflowSupport.workOrderKey(safeCommand.workOrderKey(), approvalId, "WP5-RR"),
                TestDesignApprovalWorkflowSupport.boundedSafeText(
                        safeCommand.workOrderTitle(),
                        "workOrderTitle",
                        TestDesignApprovalWorkflowSupport.MAX_WORK_ORDER_TITLE_CHARS,
                        false,
                        false
                ),
                TestDesignApprovalWorkflowSupport.workOrderUrl(safeCommand.workOrderUrl()),
                "OPEN",
                exceptionSummary,
                TestDesignApprovalWorkflowSupport.sha256OrNull(exceptionSummary),
                riskMitigation,
                requestNote,
                null,
                actor,
                null,
                null,
                now,
                now
        );
        TestDesignReleaseReadinessApproval saved = repository.saveReleaseReadinessApproval(approval);
        if (requestNote != null) {
            appendNote(saved.id(), TestDesignApprovalWorkflowSupport.NOTE_TYPE_REQUEST, requestNote, actor, now);
        }
        writeAudit("RELEASE_READINESS_EXCEPTION_REQUEST", saved, Map.of(
                "taskId", saved.taskId(),
                "projectId", saved.projectId(),
                "qualityGateStatus", saved.qualityGateStatus(),
                "blockingCount", saved.blockingCount(),
                "warningCount", saved.warningCount(),
                "readinessDigest", saved.readinessDigest(),
                "exceptionReasonCodeCaptured", saved.exceptionReasonCode() != null,
                "requestNoteCaptured", saved.requestNote() != null,
                "workOrderKey", saved.workOrderKey()
        ));
        return toResponse(saved);
    }

    /**
     * Updates a pending approval draft while preserving project/task scope and refreshing aggregate readiness.
     */
    @Transactional
    public TestDesignReleaseReadinessApprovalResponse updateApproval(
            UUID approvalId,
            RequestTestDesignReleaseReadinessApprovalCommand command
    ) {
        TestDesignReleaseReadinessApproval current = approvalOrThrow(approvalId);
        if (!TestDesignApprovalWorkflowSupport.STATUS_PENDING.equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 PENDING 发布准出审批可更新草稿: " + current.status());
        }
        RequestTestDesignReleaseReadinessApprovalCommand safeCommand = command == null
                ? new RequestTestDesignReleaseReadinessApprovalCommand(null, null, null, null, null, null, null)
                : command;
        ReadinessSnapshot snapshot = blockedReadinessSnapshot(current.taskId());
        String nextExceptionSummary = TestDesignApprovalWorkflowSupport.replacementText(
                safeCommand.exceptionSummary(),
                current.exceptionSummary(),
                "exceptionSummary",
                MAX_EXCEPTION_TEXT_CHARS,
                true
        );
        String nextRiskMitigation = TestDesignApprovalWorkflowSupport.replacementText(
                safeCommand.riskMitigation(),
                current.riskMitigation(),
                "riskMitigation",
                MAX_EXCEPTION_TEXT_CHARS,
                true
        );
        String nextRequestNote = TestDesignApprovalWorkflowSupport.replacementText(
                safeCommand.requestNote(),
                current.requestNote(),
                "requestNote",
                TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS,
                true
        );
        String nextWorkOrderTitle = TestDesignApprovalWorkflowSupport.replacementText(
                safeCommand.workOrderTitle(),
                current.workOrderTitle(),
                "workOrderTitle",
                TestDesignApprovalWorkflowSupport.MAX_WORK_ORDER_TITLE_CHARS,
                false
        );
        String nextWorkOrderUrl = StringUtils.hasText(safeCommand.workOrderUrl())
                ? TestDesignApprovalWorkflowSupport.workOrderUrl(safeCommand.workOrderUrl())
                : current.workOrderUrl();
        String nextWorkOrderKey = StringUtils.hasText(safeCommand.workOrderKey())
                ? TestDesignApprovalWorkflowSupport.workOrderKey(safeCommand.workOrderKey(), current.id(), "WP5-RR")
                : current.workOrderKey();
        String nextExceptionReasonCode = StringUtils.hasText(safeCommand.exceptionReasonCode())
                ? TestDesignApprovalWorkflowSupport.reasonCode(
                        safeCommand.exceptionReasonCode(), "exceptionReasonCode", ALLOWED_REASON_CODES
                )
                : current.exceptionReasonCode();
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestDesignReleaseReadinessApproval updated = new TestDesignReleaseReadinessApproval(
                current.id(),
                current.taskId(),
                current.projectId(),
                current.status(),
                snapshot.status(),
                snapshot.blockingCount(),
                snapshot.warningCount(),
                snapshot.digest(),
                nextExceptionReasonCode,
                current.approvalReasonCode(),
                nextWorkOrderKey,
                nextWorkOrderTitle,
                nextWorkOrderUrl,
                current.workOrderStatus(),
                nextExceptionSummary,
                TestDesignApprovalWorkflowSupport.sha256OrNull(nextExceptionSummary),
                nextRiskMitigation,
                nextRequestNote,
                current.reviewNote(),
                current.requestedBy(),
                current.approvedBy(),
                current.reviewedAt(),
                current.createdAt(),
                now
        );
        TestDesignReleaseReadinessApproval saved = repository.saveReleaseReadinessApproval(updated);
        if (StringUtils.hasText(safeCommand.requestNote())) {
            appendNote(saved.id(), TestDesignApprovalWorkflowSupport.NOTE_TYPE_COMMENT, nextRequestNote, actor, now);
        }
        writeAudit("RELEASE_READINESS_EXCEPTION_UPDATE", saved, Map.of(
                "taskId", saved.taskId(),
                "projectId", saved.projectId(),
                "qualityGateStatus", saved.qualityGateStatus(),
                "blockingCount", saved.blockingCount(),
                "readinessDigest", saved.readinessDigest(),
                "workOrderKey", saved.workOrderKey()
        ));
        return toResponse(saved);
    }

    /**
     * Approves a pending exception against the task's current blocked aggregate readiness.
     */
    @Transactional
    public TestDesignReleaseReadinessApprovalResponse approveApproval(
            UUID approvalId,
            ReviewTestDesignReleaseReadinessApprovalCommand command
    ) {
        return reviewApproval(
                approvalId,
                command,
                TestDesignApprovalWorkflowSupport.STATUS_APPROVED,
                "RELEASE_READINESS_EXCEPTION_APPROVE"
        );
    }

    /**
     * Rejects a pending exception and keeps the operations record for audit.
     */
    @Transactional
    public TestDesignReleaseReadinessApprovalResponse rejectApproval(
            UUID approvalId,
            ReviewTestDesignReleaseReadinessApprovalCommand command
    ) {
        return reviewApproval(
                approvalId,
                command,
                TestDesignApprovalWorkflowSupport.STATUS_REJECTED,
                "RELEASE_READINESS_EXCEPTION_REJECT"
        );
    }

    /**
     * Lists sanitized release-readiness approval records for a task.
     */
    @Transactional(readOnly = true)
    public List<TestDesignReleaseReadinessApprovalResponse> approvals(UUID taskId) {
        taskOrThrow(taskId);
        return repository.releaseReadinessApprovals(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Appends an operator note to a release-readiness approval work order.
     */
    @Transactional
    public TestDesignReleaseReadinessNoteResponse addNote(
            UUID approvalId,
            AddTestDesignReleaseReadinessNoteCommand command
    ) {
        TestDesignReleaseReadinessApproval approval = approvalOrThrow(approvalId);
        AddTestDesignReleaseReadinessNoteCommand safeCommand = command == null
                ? new AddTestDesignReleaseReadinessNoteCommand(null, null)
                : command;
        String noteType = TestDesignApprovalWorkflowSupport.noteType(safeCommand.noteType());
        String noteText = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.noteText(), "noteText", TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS, true, true
        );
        TestDesignReleaseReadinessNote saved = appendNote(
                approval.id(), noteType, noteText, actorResolver.currentActor(), Instant.now()
        );
        writeAudit("RELEASE_READINESS_EXCEPTION_NOTE_ADD", approval, Map.of(
                "taskId", approval.taskId(),
                "projectId", approval.projectId(),
                "noteType", saved.noteType(),
                "noteLength", saved.noteText().length()
        ));
        return toNoteResponse(saved);
    }

    /**
     * Returns the approval work order note timeline for release operators.
     */
    @Transactional(readOnly = true)
    public List<TestDesignReleaseReadinessNoteResponse> notes(UUID approvalId) {
        approvalOrThrow(approvalId);
        return repository.releaseReadinessNotes(approvalId).stream()
                .map(this::toNoteResponse)
                .toList();
    }

    /**
     * Checks whether the task has an approved exception matching the current aggregate readiness digest.
     */
    @Transactional(readOnly = true)
    public boolean hasApprovedExceptionForCurrentReadiness(
            UUID taskId,
            TestDesignQualityReadinessResponse readiness
    ) {
        if (readiness == null || !READINESS_BLOCKED.equals(readiness.status())) {
            return false;
        }
        String currentDigest = readinessDigest(readiness);
        return repository.latestApprovedReleaseReadinessApproval(taskId)
                .filter(approval -> READINESS_BLOCKED.equals(approval.qualityGateStatus()))
                .filter(approval -> Objects.equals(currentDigest, approval.readinessDigest()))
                .isPresent();
    }

    public static String readinessDigest(TestDesignQualityReadinessResponse readiness) {
        if (readiness == null) {
            return null;
        }
        StringBuilder payload = new StringBuilder();
        payload.append(readiness.status()).append('|')
                .append(readiness.blockingCount()).append('|')
                .append(readiness.warningCount());
        List<TestDesignQualityReadinessCheckResponse> checks = readiness.checks() == null
                ? List.of()
                : readiness.checks();
        checks.stream()
                .sorted(Comparator.comparing(TestDesignQualityReadinessCheckResponse::code))
                .forEach(check -> payload.append('|')
                        .append(nullToEmpty(check.code())).append(':')
                        .append(nullToEmpty(check.status())).append(':')
                        .append(nullToEmpty(check.severity())).append(':')
                        .append(check.currentValue()).append(':')
                        .append(check.thresholdValue()).append(':')
                        .append(nullToEmpty(check.unit())));
        return TestDesignApprovalWorkflowSupport.sha256OrNull(payload.toString());
    }

    private TestDesignReleaseReadinessApprovalResponse reviewApproval(
            UUID approvalId,
            ReviewTestDesignReleaseReadinessApprovalCommand command,
            String nextStatus,
            String auditAction
    ) {
        TestDesignReleaseReadinessApproval current = approvalOrThrow(approvalId);
        if (!TestDesignApprovalWorkflowSupport.STATUS_PENDING.equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 PENDING 发布准出审批可审批: " + current.status());
        }
        ReviewTestDesignReleaseReadinessApprovalCommand safeCommand = command == null
                ? new ReviewTestDesignReleaseReadinessApprovalCommand(null, null, null)
                : command;
        ReadinessSnapshot snapshot = blockedReadinessSnapshot(current.taskId());
        String approvalReasonCode = TestDesignApprovalWorkflowSupport.reasonCode(
                safeCommand.approvalReasonCode(), "approvalReasonCode", ALLOWED_REASON_CODES
        );
        String reviewNote = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.reviewNote(), "reviewNote", TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS, true, false
        );
        String actor = actorResolver.currentActor();
        Instant now = Instant.now();
        TestDesignReleaseReadinessApproval reviewed = new TestDesignReleaseReadinessApproval(
                current.id(),
                current.taskId(),
                current.projectId(),
                nextStatus,
                snapshot.status(),
                snapshot.blockingCount(),
                snapshot.warningCount(),
                snapshot.digest(),
                current.exceptionReasonCode(),
                approvalReasonCode,
                current.workOrderKey(),
                current.workOrderTitle(),
                current.workOrderUrl(),
                TestDesignApprovalWorkflowSupport.workOrderStatus(safeCommand.workOrderStatus(), nextStatus),
                current.exceptionSummary(),
                current.exceptionSummaryDigest(),
                current.riskMitigation(),
                current.requestNote(),
                reviewNote,
                current.requestedBy(),
                actor,
                now,
                current.createdAt(),
                now
        );
        TestDesignReleaseReadinessApproval saved = repository.saveReleaseReadinessApproval(reviewed);
        if (reviewNote != null) {
            appendNote(saved.id(), TestDesignApprovalWorkflowSupport.NOTE_TYPE_REVIEW, reviewNote, actor, now);
        }
        writeAudit(auditAction, saved, Map.of(
                "taskId", saved.taskId(),
                "projectId", saved.projectId(),
                "status", saved.status(),
                "qualityGateStatus", saved.qualityGateStatus(),
                "blockingCount", saved.blockingCount(),
                "readinessDigest", saved.readinessDigest(),
                "approvalReasonCodeCaptured", saved.approvalReasonCode() != null,
                "workOrderStatus", saved.workOrderStatus(),
                "reviewNoteCaptured", saved.reviewNote() != null
        ));
        return toResponse(saved);
    }

    private ReadinessSnapshot blockedReadinessSnapshot(UUID taskId) {
        TestDesignQualityReadinessResponse readiness = qualityService.qualitySummary(taskId).readiness();
        if (readiness == null || !READINESS_BLOCKED.equals(readiness.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前任务质量门禁未阻断，无需发布准出例外审批");
        }
        return new ReadinessSnapshot(
                readiness.status(),
                readiness.blockingCount(),
                readiness.warningCount(),
                readinessDigest(readiness)
        );
    }

    private TestDesignTask taskOrThrow(UUID taskId) {
        return repository.task(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + taskId));
    }

    private TestDesignReleaseReadinessApproval approvalOrThrow(UUID approvalId) {
        return repository.releaseReadinessApproval(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "发布准出审批不存在: " + approvalId));
    }

    private TestDesignReleaseReadinessNote appendNote(
            UUID approvalId,
            String noteType,
            String noteText,
            String actor,
            Instant createdAt
    ) {
        TestDesignReleaseReadinessNote note = new TestDesignReleaseReadinessNote(
                UUID.randomUUID(),
                approvalId,
                noteType,
                noteText,
                actor,
                createdAt
        );
        return repository.saveReleaseReadinessNote(note);
    }

    private TestDesignReleaseReadinessApprovalResponse toResponse(TestDesignReleaseReadinessApproval approval) {
        List<TestDesignReleaseReadinessNote> notes = repository.releaseReadinessNotes(approval.id());
        String latestNotePreview = notes.isEmpty() ? null : notes.get(notes.size() - 1).noteText();
        return new TestDesignReleaseReadinessApprovalResponse(
                approval.id(),
                approval.taskId(),
                approval.projectId(),
                approval.status(),
                approval.qualityGateStatus(),
                approval.blockingCount(),
                approval.warningCount(),
                approval.readinessDigest(),
                approval.exceptionReasonCode() != null,
                approval.exceptionReasonCode(),
                approval.approvalReasonCode() != null,
                approval.approvalReasonCode(),
                approval.workOrderKey(),
                approval.workOrderTitle(),
                approval.workOrderUrl(),
                approval.workOrderStatus(),
                approval.exceptionSummary(),
                approval.exceptionSummaryDigest(),
                approval.riskMitigation(),
                approval.requestNote(),
                approval.reviewNote(),
                notes.size(),
                latestNotePreview,
                approval.requestedBy(),
                approval.approvedBy(),
                approval.reviewedAt(),
                approval.createdAt(),
                approval.updatedAt()
        );
    }

    private TestDesignReleaseReadinessNoteResponse toNoteResponse(TestDesignReleaseReadinessNote note) {
        return new TestDesignReleaseReadinessNoteResponse(
                note.id(),
                note.approvalId(),
                note.noteType(),
                note.noteText(),
                note.createdBy(),
                note.createdAt()
        );
    }

    private void writeAudit(String action, TestDesignReleaseReadinessApproval approval, Map<String, Object> after) {
        contextClient.writeAuditEvent(
                action,
                "TEST_DESIGN_RELEASE_READINESS_APPROVAL",
                approval.id().toString(),
                approval.projectId(),
                "SUCCEEDED",
                after
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ReadinessSnapshot(
            String status,
            long blockingCount,
            long warningCount,
            String digest
    ) {
    }
}
