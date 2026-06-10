package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpOperationsService;
import com.songhg.veri.agent.testdesign.application.command.RequeueTestDesignAuditOutboxCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCrossWpOperationsRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditOutboxRequeueResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCrossWpOperationsDashboardResponse;
import jakarta.validation.Valid;
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
