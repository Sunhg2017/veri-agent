package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignReleaseReadinessApprovalService;
import com.songhg.veri.agent.testdesign.application.command.AddTestDesignReleaseReadinessNoteCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignReleaseReadinessApprovalCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignReleaseReadinessApprovalCommand;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessApprovalResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessNoteResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 release-readiness approval and quality-gate exception endpoints.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design")
public class TestDesignReleaseReadinessApprovalController {

    private final TestDesignReleaseReadinessApprovalService service;

    public TestDesignReleaseReadinessApprovalController(TestDesignReleaseReadinessApprovalService service) {
        this.service = service;
    }

    /**
     * Lists release-readiness approval work orders for a task.
     */
    @GetMapping("/tasks/{id}/release-readiness/approvals")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public List<TestDesignReleaseReadinessApprovalResponse> approvals(@PathVariable UUID id) {
        return service.approvals(id);
    }

    /**
     * Requests a quality-gate exception for the task's current blocked readiness state.
     */
    @PostMapping("/tasks/{id}/release-readiness/approvals")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignReleaseReadinessApprovalResponse requestApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) RequestTestDesignReleaseReadinessApprovalCommand command
    ) {
        return service.requestApproval(id, command);
    }

    /**
     * Updates a pending release-readiness approval draft before review.
     */
    @PutMapping("/release-readiness/approvals/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.RELEASE_READINESS_APPROVAL)
    public TestDesignReleaseReadinessApprovalResponse updateApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) RequestTestDesignReleaseReadinessApprovalCommand command
    ) {
        return service.updateApproval(id, command);
    }

    /**
     * Approves a pending quality-gate exception.
     */
    @PostMapping("/release-readiness/approvals/{id}/approve")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.RELEASE_READINESS_APPROVAL)
    public TestDesignReleaseReadinessApprovalResponse approveApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewTestDesignReleaseReadinessApprovalCommand command
    ) {
        return service.approveApproval(id, command);
    }

    /**
     * Rejects a pending quality-gate exception.
     */
    @PostMapping("/release-readiness/approvals/{id}/reject")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.RELEASE_READINESS_APPROVAL)
    public TestDesignReleaseReadinessApprovalResponse rejectApproval(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewTestDesignReleaseReadinessApprovalCommand command
    ) {
        return service.rejectApproval(id, command);
    }

    /**
     * Lists the approval work order note timeline.
     */
    @GetMapping("/release-readiness/approvals/{id}/notes")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.RELEASE_READINESS_APPROVAL)
    public List<TestDesignReleaseReadinessNoteResponse> notes(@PathVariable UUID id) {
        return service.notes(id);
    }

    /**
     * Appends an operator note to the approval work order timeline.
     */
    @PostMapping("/release-readiness/approvals/{id}/notes")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.RELEASE_READINESS_APPROVAL)
    public TestDesignReleaseReadinessNoteResponse addNote(
            @PathVariable UUID id,
            @RequestBody(required = false) AddTestDesignReleaseReadinessNoteCommand command
    ) {
        return service.addNote(id, command);
    }
}
