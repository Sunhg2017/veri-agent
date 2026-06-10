package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.AddTestDesignReportArchiveNoteCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignReportArchiveApprovalCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignReportArchiveApprovalCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveApprovalResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveIntegrityResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveNoteResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchive;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchiveApproval;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchiveNote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Operates WP5 report archives, approvals and integrity summaries.
 *
 * <p>All responses are metadata-only. The managed archive content, storage key and row digest values remain server-side
 * even when operators approve final archive status or external sharing.</p>
 */
@Service
public class TestDesignReportArchiveService {

    public static final String APPROVAL_TYPE_ARCHIVE = "ARCHIVE";
    public static final String APPROVAL_TYPE_EXTERNAL_SHARE = "EXTERNAL_SHARE";

    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String APPROVAL_NOT_REQUESTED = "NOT_REQUESTED";
    private static final int MAX_REQUEST_TEXT_CHARS = 1000;
    private static final List<String> ALLOWED_REASON_CODES = List.of(
            "RETENTION_POLICY",
            "COMPLIANCE_AUDIT",
            "CUSTOMER_REQUEST",
            "REGULATED_EXPORT",
            "SMOKE_VALIDATION"
    );

    private final TestDesignRepository repository;
    private final TestDesignProperties properties;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignPlatformContextClient contextClient;

    public TestDesignReportArchiveService(
            TestDesignRepository repository,
            TestDesignProperties properties,
            TestDesignActorResolver actorResolver,
            TestDesignPlatformContextClient contextClient
    ) {
        this.repository = repository;
        this.properties = properties;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
    }

    /**
     * Lists archive metadata for a task; content, storage key and row digests are not returned.
     */
    @Transactional(readOnly = true)
    public List<TestDesignReportArchiveResponse> archives(UUID taskId) {
        taskOrThrow(taskId);
        return repository.reportArchivesByTask(taskId).stream()
                .map(this::toArchiveResponse)
                .toList();
    }

    /**
     * Returns aggregate line-integrity readiness for a stored archive.
     */
    @Transactional(readOnly = true)
    public TestDesignReportArchiveIntegrityResponse integrity(UUID archiveId) {
        TestDesignReportArchive archive = archiveOrThrow(archiveId);
        long indexedRows = repository.countReportArchiveLineIntegrity(archive.id());
        return new TestDesignReportArchiveIntegrityResponse(
                archive.id(),
                archive.reportRowCount(),
                indexedRows,
                "SHA-256",
                indexedRows == archive.reportRowCount(),
                false,
                false,
                false,
                true
        );
    }

    /**
     * Lists archive and external-share approval work orders for an archive.
     */
    @Transactional(readOnly = true)
    public List<TestDesignReportArchiveApprovalResponse> approvals(UUID archiveId) {
        archiveOrThrow(archiveId);
        return repository.reportArchiveApprovals(archiveId).stream()
                .map(this::toApprovalResponse)
                .toList();
    }

    /**
     * Requests archive finalization approval if there is no current pending archive approval.
     */
    @Transactional
    public TestDesignReportArchiveApprovalResponse requestArchiveApproval(
            UUID archiveId,
            RequestTestDesignReportArchiveApprovalCommand command
    ) {
        TestDesignReportArchive archive = archiveOrThrow(archiveId);
        return requestApproval(archive, APPROVAL_TYPE_ARCHIVE, command, "WP5-ARCH");
    }

    /**
     * Requests external sharing approval after archive finalization.
     */
    @Transactional
    public TestDesignReportArchiveApprovalResponse requestExternalShareApproval(
            UUID archiveId,
            RequestTestDesignReportArchiveApprovalCommand command
    ) {
        TestDesignReportArchive archive = archiveOrThrow(archiveId);
        if (!properties.reportArchiveExternalSharingAllowed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前环境未开启报告归档外发");
        }
        if (!STATUS_ARCHIVED.equals(archive.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 ARCHIVED 报告可申请外发: " + archive.status());
        }
        return requestApproval(archive, APPROVAL_TYPE_EXTERNAL_SHARE, command, "WP5-SHARE");
    }

    /**
     * Approves a pending archive or external-share work order and updates archive state.
     */
    @Transactional
    public TestDesignReportArchiveApprovalResponse approveApproval(
            UUID approvalId,
            ReviewTestDesignReportArchiveApprovalCommand command
    ) {
        return reviewApproval(
                approvalId,
                command,
                TestDesignApprovalWorkflowSupport.STATUS_APPROVED,
                "REPORT_ARCHIVE_APPROVE"
        );
    }

    /**
     * Rejects a pending archive or external-share work order and updates archive state.
     */
    @Transactional
    public TestDesignReportArchiveApprovalResponse rejectApproval(
            UUID approvalId,
            ReviewTestDesignReportArchiveApprovalCommand command
    ) {
        return reviewApproval(
                approvalId,
                command,
                TestDesignApprovalWorkflowSupport.STATUS_REJECTED,
                "REPORT_ARCHIVE_REJECT"
        );
    }

    /**
     * Appends a bounded operator note to a report archive work order.
     */
    @Transactional
    public TestDesignReportArchiveNoteResponse addNote(UUID approvalId, AddTestDesignReportArchiveNoteCommand command) {
        TestDesignReportArchiveApproval approval = approvalOrThrow(approvalId);
        AddTestDesignReportArchiveNoteCommand safeCommand = command == null
                ? new AddTestDesignReportArchiveNoteCommand(null, null)
                : command;
        String noteType = TestDesignApprovalWorkflowSupport.noteType(safeCommand.noteType());
        String noteText = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.noteText(), "noteText", TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS, true, true
        );
        TestDesignReportArchiveNote saved = appendNote(
                approval.id(), noteType, noteText, actorResolver.currentActor(), Instant.now()
        );
        writeAudit("REPORT_ARCHIVE_NOTE_ADD", approval, Map.of(
                "archiveId", approval.archiveId(),
                "taskId", approval.taskId(),
                "projectId", approval.projectId(),
                "approvalType", approval.approvalType(),
                "noteType", saved.noteType(),
                "noteLength", saved.noteText().length()
        ));
        return toNoteResponse(saved);
    }

    /**
     * Returns the work-order note timeline.
     */
    @Transactional(readOnly = true)
    public List<TestDesignReportArchiveNoteResponse> notes(UUID approvalId) {
        approvalOrThrow(approvalId);
        return repository.reportArchiveNotes(approvalId).stream()
                .map(TestDesignReportArchiveService::toNoteResponse)
                .toList();
    }

    private TestDesignReportArchiveApprovalResponse requestApproval(
            TestDesignReportArchive archive,
            String approvalType,
            RequestTestDesignReportArchiveApprovalCommand command,
            String workOrderPrefix
    ) {
        repository.latestReportArchiveApproval(archive.id(), approvalType)
                .filter(approval -> TestDesignApprovalWorkflowSupport.STATUS_PENDING.equals(approval.status()))
                .ifPresent(approval -> {
                    throw new BusinessException(ErrorCode.INVALID_STATE,
                            approvalType + " 已存在 PENDING 审批: " + approval.id());
                });
        RequestTestDesignReportArchiveApprovalCommand safeCommand = command == null
                ? new RequestTestDesignReportArchiveApprovalCommand(null, null, null, null, null, null)
                : command;
        UUID approvalId = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        String requestSummary = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.requestSummary(), "requestSummary", MAX_REQUEST_TEXT_CHARS, true, true
        );
        String requestNote = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.requestNote(), "requestNote", TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS, true, false
        );
        TestDesignReportArchiveApproval approval = new TestDesignReportArchiveApproval(
                approvalId,
                archive.id(),
                archive.taskId(),
                archive.projectId(),
                approvalType,
                TestDesignApprovalWorkflowSupport.STATUS_PENDING,
                TestDesignApprovalWorkflowSupport.reasonCode(safeCommand.reasonCode(), "reasonCode", ALLOWED_REASON_CODES),
                null,
                TestDesignApprovalWorkflowSupport.workOrderKey(safeCommand.workOrderKey(), approvalId, workOrderPrefix),
                TestDesignApprovalWorkflowSupport.boundedSafeText(
                        safeCommand.workOrderTitle(),
                        "workOrderTitle",
                        TestDesignApprovalWorkflowSupport.MAX_WORK_ORDER_TITLE_CHARS,
                        false,
                        false
                ),
                TestDesignApprovalWorkflowSupport.workOrderUrl(safeCommand.workOrderUrl()),
                "OPEN",
                requestSummary,
                TestDesignApprovalWorkflowSupport.sha256OrNull(requestSummary),
                requestNote,
                null,
                actor,
                null,
                null,
                now,
                now
        );
        TestDesignReportArchiveApproval saved = repository.saveReportArchiveApproval(approval);
        if (requestNote != null) {
            appendNote(saved.id(), TestDesignApprovalWorkflowSupport.NOTE_TYPE_REQUEST, requestNote, actor, now);
        }
        updateArchiveApprovalState(archive, approvalType, TestDesignApprovalWorkflowSupport.STATUS_PENDING, now);
        writeAudit("REPORT_ARCHIVE_APPROVAL_REQUEST", saved, Map.of(
                "archiveId", saved.archiveId(),
                "taskId", saved.taskId(),
                "projectId", saved.projectId(),
                "approvalType", saved.approvalType(),
                "reasonCodeCaptured", saved.reasonCode() != null,
                "workOrderKey", saved.workOrderKey()
        ));
        return toApprovalResponse(saved);
    }

    private TestDesignReportArchiveApprovalResponse reviewApproval(
            UUID approvalId,
            ReviewTestDesignReportArchiveApprovalCommand command,
            String nextStatus,
            String auditAction
    ) {
        TestDesignReportArchiveApproval current = approvalOrThrow(approvalId);
        if (!TestDesignApprovalWorkflowSupport.STATUS_PENDING.equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 PENDING 归档审批可评审: " + current.status());
        }
        ReviewTestDesignReportArchiveApprovalCommand safeCommand = command == null
                ? new ReviewTestDesignReportArchiveApprovalCommand(null, null, null)
                : command;
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        String reviewNote = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.reviewNote(), "reviewNote", TestDesignApprovalWorkflowSupport.MAX_NOTE_CHARS, true, false
        );
        TestDesignReportArchiveApproval reviewed = new TestDesignReportArchiveApproval(
                current.id(),
                current.archiveId(),
                current.taskId(),
                current.projectId(),
                current.approvalType(),
                nextStatus,
                current.reasonCode(),
                TestDesignApprovalWorkflowSupport.reasonCode(
                        safeCommand.approvalReasonCode(), "approvalReasonCode", ALLOWED_REASON_CODES
                ),
                current.workOrderKey(),
                current.workOrderTitle(),
                current.workOrderUrl(),
                TestDesignApprovalWorkflowSupport.workOrderStatus(safeCommand.workOrderStatus(), nextStatus),
                current.requestSummary(),
                current.requestSummaryDigest(),
                current.requestNote(),
                reviewNote,
                current.requestedBy(),
                TestDesignApprovalWorkflowSupport.STATUS_APPROVED.equals(nextStatus) ? actor : current.approvedBy(),
                now,
                current.createdAt(),
                now
        );
        TestDesignReportArchiveApproval saved = repository.saveReportArchiveApproval(reviewed);
        if (reviewNote != null) {
            appendNote(saved.id(), TestDesignApprovalWorkflowSupport.NOTE_TYPE_REVIEW, reviewNote, actor, now);
        }
        updateArchiveApprovalState(archiveOrThrow(saved.archiveId()), saved.approvalType(), nextStatus, now);
        writeAudit(auditAction, saved, Map.of(
                "archiveId", saved.archiveId(),
                "taskId", saved.taskId(),
                "projectId", saved.projectId(),
                "approvalType", saved.approvalType(),
                "status", saved.status(),
                "approvalReasonCodeCaptured", saved.approvalReasonCode() != null,
                "workOrderStatus", saved.workOrderStatus()
        ));
        return toApprovalResponse(saved);
    }

    private void updateArchiveApprovalState(
            TestDesignReportArchive archive,
            String approvalType,
            String approvalStatus,
            Instant now
    ) {
        String archiveStatus = archive.status();
        String archiveApprovalStatus = archive.archiveApprovalStatus();
        String externalApprovalStatus = archive.externalApprovalStatus();
        if (APPROVAL_TYPE_ARCHIVE.equals(approvalType)) {
            archiveApprovalStatus = approvalStatus;
            if (TestDesignApprovalWorkflowSupport.STATUS_APPROVED.equals(approvalStatus)) {
                archiveStatus = STATUS_ARCHIVED;
            } else if (TestDesignApprovalWorkflowSupport.STATUS_REJECTED.equals(approvalStatus)) {
                archiveStatus = STATUS_REJECTED;
            } else {
                archiveStatus = STATUS_PENDING_APPROVAL;
            }
        } else if (APPROVAL_TYPE_EXTERNAL_SHARE.equals(approvalType)) {
            externalApprovalStatus = approvalStatus;
            archiveStatus = STATUS_ARCHIVED;
        }
        repository.updateReportArchiveStatus(archive.id(), archiveStatus, archiveApprovalStatus, externalApprovalStatus, now);
    }

    private TestDesignReportArchiveNote appendNote(
            UUID approvalId,
            String noteType,
            String noteText,
            String actor,
            Instant now
    ) {
        return repository.saveReportArchiveNote(new TestDesignReportArchiveNote(
                UUID.randomUUID(),
                approvalId,
                noteType,
                noteText,
                actor,
                now
        ));
    }

    private TestDesignReportArchive archiveOrThrow(UUID archiveId) {
        return repository.reportArchive(archiveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告归档不存在: " + archiveId));
    }

    private TestDesignReportArchiveApproval approvalOrThrow(UUID approvalId) {
        return repository.reportArchiveApproval(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告归档审批不存在: " + approvalId));
    }

    private void taskOrThrow(UUID taskId) {
        repository.task(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + taskId));
    }

    private TestDesignReportArchiveResponse toArchiveResponse(TestDesignReportArchive archive) {
        return new TestDesignReportArchiveResponse(
                archive.id(),
                archive.manifestId(),
                archive.taskId(),
                archive.projectId(),
                archive.storageBackend(),
                archive.contentDigest(),
                archive.contentSizeBytes(),
                archive.reportRowCount(),
                archive.lineIntegrityCount(),
                archive.status(),
                archive.archiveApprovalStatus(),
                archive.externalApprovalStatus(),
                archive.retentionUntil(),
                archive.contentSizeBytes() > 0,
                archive.lineIntegrityCount() == archive.reportRowCount(),
                false,
                false,
                true,
                archive.createdBy(),
                archive.createdAt(),
                archive.updatedAt()
        );
    }

    private TestDesignReportArchiveApprovalResponse toApprovalResponse(TestDesignReportArchiveApproval approval) {
        List<TestDesignReportArchiveNote> notes = repository.reportArchiveNotes(approval.id());
        TestDesignReportArchiveNote latestNote = notes.isEmpty() ? null : notes.getLast();
        return new TestDesignReportArchiveApprovalResponse(
                approval.id(),
                approval.archiveId(),
                approval.taskId(),
                approval.projectId(),
                approval.approvalType(),
                approval.status(),
                approval.reasonCode() != null,
                approval.reasonCode(),
                approval.approvalReasonCode() != null,
                approval.approvalReasonCode(),
                approval.workOrderKey(),
                approval.workOrderTitle(),
                approval.workOrderUrl(),
                approval.workOrderStatus(),
                approval.requestSummary(),
                approval.requestSummaryDigest(),
                approval.requestNote(),
                approval.reviewNote(),
                notes.size(),
                latestNote == null ? null : preview(latestNote.noteText()),
                approval.requestedBy(),
                approval.approvedBy(),
                approval.reviewedAt(),
                approval.createdAt(),
                approval.updatedAt()
        );
    }

    private static TestDesignReportArchiveNoteResponse toNoteResponse(TestDesignReportArchiveNote note) {
        return new TestDesignReportArchiveNoteResponse(
                note.id(),
                note.approvalId(),
                note.noteType(),
                note.noteText(),
                note.createdBy(),
                note.createdAt()
        );
    }

    private static String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 117) + "...";
    }

    private void writeAudit(String action, TestDesignReportArchiveApproval approval, Map<String, Object> after) {
        contextClient.writeAuditEvent(
                action,
                "TEST_DESIGN_REPORT_ARCHIVE_APPROVAL",
                approval.id().toString(),
                approval.projectId(),
                "SUCCEEDED",
                after
        );
    }
}
