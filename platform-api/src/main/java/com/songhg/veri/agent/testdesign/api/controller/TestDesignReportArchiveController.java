package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignReportArchiveService;
import com.songhg.veri.agent.testdesign.application.command.AddTestDesignReportArchiveNoteCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignReportArchiveApprovalCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignReportArchiveApprovalCommand;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveApprovalResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveIntegrityResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveNoteResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 report archive metadata, approval work-order and integrity endpoints.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design")
public class TestDesignReportArchiveController {

    private final TestDesignReportArchiveService service;

    public TestDesignReportArchiveController(TestDesignReportArchiveService service) {
        this.service = service;
    }

    /**
     * Lists stored aggregate report archives for a task without content, storage key or row digest values.
     */
    @GetMapping("/tasks/{id}/report/archives")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public List<TestDesignReportArchiveResponse> archives(@PathVariable UUID id) {
        return service.archives(id);
    }

    /**
     * Returns aggregate line-integrity index readiness for a stored report archive.
     */
    @GetMapping("/report-archives/{id}/integrity")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.REPORT_ARCHIVE)
    public TestDesignReportArchiveIntegrityResponse integrity(@PathVariable UUID id) {
        return service.integrity(id);
    }

    /**
     * Lists archive finalization and external-share approval work orders for an archive.
     */
    @GetMapping("/report-archives/{id}/approvals")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.REPORT_ARCHIVE)
    public List<TestDesignReportArchiveApprovalResponse> approvals(@PathVariable UUID id) {
        return service.approvals(id);
    }

    /**
     * Requests archive finalization approval when the current archive work order is not pending.
     */
    @PostMapping("/report-archives/{id}/archive-approvals")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.REPORT_ARCHIVE)
    public TestDesignReportArchiveApprovalResponse requestArchiveApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) RequestTestDesignReportArchiveApprovalCommand command
    ) {
        return service.requestArchiveApproval(id, command);
    }

    /**
     * Requests external-share approval for an archived report when external sharing is enabled by configuration.
     */
    @PostMapping("/report-archives/{id}/external-approvals")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.REPORT_ARCHIVE)
    public TestDesignReportArchiveApprovalResponse requestExternalShareApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) RequestTestDesignReportArchiveApprovalCommand command
    ) {
        return service.requestExternalShareApproval(id, command);
    }

    /**
     * Approves a pending archive or external-share work order.
     */
    @PostMapping("/report-archive-approvals/{id}/approve")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.REPORT_ARCHIVE_APPROVAL)
    public TestDesignReportArchiveApprovalResponse approveApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewTestDesignReportArchiveApprovalCommand command
    ) {
        return service.approveApproval(id, command);
    }

    /**
     * Rejects a pending archive or external-share work order.
     */
    @PostMapping("/report-archive-approvals/{id}/reject")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.REPORT_ARCHIVE_APPROVAL)
    public TestDesignReportArchiveApprovalResponse rejectApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewTestDesignReportArchiveApprovalCommand command
    ) {
        return service.rejectApproval(id, command);
    }

    /**
     * Lists the approval work-order note timeline.
     */
    @GetMapping("/report-archive-approvals/{id}/notes")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.REPORT_ARCHIVE_APPROVAL)
    public List<TestDesignReportArchiveNoteResponse> notes(@PathVariable UUID id) {
        return service.notes(id);
    }

    /**
     * Appends a bounded operator note to the approval work-order timeline.
     */
    @PostMapping("/report-archive-approvals/{id}/notes")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.REPORT_ARCHIVE_APPROVAL)
    public TestDesignReportArchiveNoteResponse addNote(
            @PathVariable UUID id,
            @RequestBody(required = false) AddTestDesignReportArchiveNoteCommand command
    ) {
        return service.addNote(id, command);
    }
}
