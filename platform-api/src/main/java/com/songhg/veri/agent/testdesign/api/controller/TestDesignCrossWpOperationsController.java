package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpOperationsService;
import com.songhg.veri.agent.testdesign.application.command.ReplayTestDesignQueuedEventsCommand;
import com.songhg.veri.agent.testdesign.application.command.RequeueTestDesignAuditOutboxCommand;
import com.songhg.veri.agent.testdesign.application.command.RunTestDesignPublishCompensationCommand;
import com.songhg.veri.agent.testdesign.application.command.UpsertTestDesignQueueAlertSubscriptionCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCrossWpOperationsRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditOutboxRequeueResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCompensationRunbookResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpOperationsDashboardResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignOperationsAuditReportResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishCompensationRunResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQueueAlertSubscriptionResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQueuedEventReplayResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 cross-WP unified operations dashboard APIs.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/operations")
public class TestDesignCrossWpOperationsController {

    private final TestDesignCrossWpOperationsService service;

    public TestDesignCrossWpOperationsController(TestDesignCrossWpOperationsService service) {
        this.service = service;
    }

    /**
     * Returns aggregate-only scope, audit-chain and audit outbox replay readiness.
     */
    @GetMapping("/cross-wp-dashboard")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.CROSS_WP_OPERATIONS)
    public TestDesignCrossWpOperationsDashboardResponse dashboard(
            @Valid @ModelAttribute TestDesignCrossWpOperationsRequest request
    ) {
        return service.dashboard(request);
    }

    /**
     * Lists non-secret queue alert subscriptions for a project/prompt operations scope.
     */
    @GetMapping("/queue-alert-subscriptions")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.CROSS_WP_OPERATIONS)
    public List<TestDesignQueueAlertSubscriptionResponse> queueAlertSubscriptions(
            @Valid @ModelAttribute TestDesignCrossWpOperationsRequest request
    ) {
        return service.queueAlertSubscriptions(request);
    }

    /**
     * Creates or updates a bounded queue alert subscription.
     */
    @PostMapping("/queue-alert-subscriptions")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.PROJECT_REQUEST)
    public TestDesignQueueAlertSubscriptionResponse upsertQueueAlertSubscription(
            @Valid @RequestBody UpsertTestDesignQueueAlertSubscriptionCommand command
    ) {
        return service.upsertQueueAlertSubscription(command);
    }

    /**
     * Replays queued generation/publish events by project scope without exporting event payloads or identifiers.
     */
    @PostMapping("/queued-events/replay")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.PROJECT_REQUEST)
    public TestDesignQueuedEventReplayResponse replayQueuedEvents(
            @Valid @RequestBody ReplayTestDesignQueuedEventsCommand command
    ) {
        return service.replayQueuedEvents(command);
    }

    /**
     * Returns the aggregate publish compensation runbook for a project/prompt scope.
     */
    @GetMapping("/compensation-runbook")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.CROSS_WP_OPERATIONS)
    public TestDesignCompensationRunbookResponse compensationRunbook(
            @Valid @ModelAttribute TestDesignCrossWpOperationsRequest request
    ) {
        return service.compensationRunbook(request);
    }

    /**
     * Runs bounded publish compensation manually for a project/prompt scope.
     */
    @PostMapping("/publish-compensation/run")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.PROJECT_REQUEST)
    public TestDesignPublishCompensationRunResponse runPublishCompensation(
            @Valid @RequestBody RunTestDesignPublishCompensationCommand command
    ) {
        return service.runPublishCompensation(command);
    }

    /**
     * Returns aggregate-only batch operations audit counts.
     */
    @GetMapping("/audit-report")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.CROSS_WP_OPERATIONS)
    public TestDesignOperationsAuditReportResponse operationsAuditReport(
            @Valid @ModelAttribute TestDesignCrossWpOperationsRequest request
    ) {
        return service.operationsAuditReport(request);
    }

    /**
     * Requeues failed/dead WP1 audit outbox events for a project scope without exporting outbox payloads.
     */
    @PostMapping("/audit-outbox/requeue")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.AUDIT_OUTBOX_REQUEUE)
    public TestDesignAuditOutboxRequeueResponse requeueAuditOutbox(
            @Valid @RequestBody RequeueTestDesignAuditOutboxCommand command
    ) {
        return service.requeueAuditOutbox(command);
    }
}
