package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignContextPolicyService;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignContextPolicyOverrideCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignContextPolicyOverrideCommand;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyEffectiveResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyOverrideResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 context policy operations endpoints.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/context-policies")
public class TestDesignContextPolicyController {

    private final TestDesignContextPolicyService service;

    public TestDesignContextPolicyController(TestDesignContextPolicyService service) {
        this.service = service;
    }

    /**
     * Lists sanitized project/environment context policy override metadata.
     */
    @GetMapping("/projects/{projectId}/overrides")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.POLICY_PROJECT)
    public List<TestDesignContextPolicyOverrideResponse> overrides(
            @PathVariable String projectId,
            @RequestParam(required = false) String environmentKey
    ) {
        return service.overrides(projectId, environmentKey);
    }

    /**
     * Returns the effective context clipping policy after platform, project and environment resolution.
     */
    @GetMapping("/projects/{projectId}/effective")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.POLICY_PROJECT)
    public TestDesignContextPolicyEffectiveResponse effectivePolicy(
            @PathVariable String projectId,
            @RequestParam(required = false) String environmentKey
    ) {
        return service.effectivePolicy(projectId, environmentKey);
    }

    /**
     * Requests a project-level context policy override. The request remains pending until explicitly approved.
     */
    @PostMapping("/projects/{projectId}/overrides")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.POLICY_PROJECT)
    public TestDesignContextPolicyOverrideResponse requestProjectOverride(
            @PathVariable String projectId,
            @RequestBody(required = false) RequestTestDesignContextPolicyOverrideCommand command
    ) {
        return service.requestProjectOverride(projectId, command);
    }

    /**
     * Requests an environment-level context policy override. The request remains pending until explicitly approved.
     */
    @PostMapping("/projects/{projectId}/environments/{environmentKey}/overrides")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.POLICY_PROJECT)
    public TestDesignContextPolicyOverrideResponse requestEnvironmentOverride(
            @PathVariable String projectId,
            @PathVariable String environmentKey,
            @RequestBody(required = false) RequestTestDesignContextPolicyOverrideCommand command
    ) {
        return service.requestEnvironmentOverride(projectId, environmentKey, command);
    }

    /**
     * Approves a pending context policy override so new tasks can use it.
     */
    @PostMapping("/overrides/{id}/approve")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.POLICY_OVERRIDE)
    public TestDesignContextPolicyOverrideResponse approveOverride(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewTestDesignContextPolicyOverrideCommand command
    ) {
        return service.approveOverride(id, command);
    }

    /**
     * Rejects a pending context policy override and keeps the operations record for audit.
     */
    @PostMapping("/overrides/{id}/reject")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.POLICY_OVERRIDE)
    public TestDesignContextPolicyOverrideResponse rejectOverride(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewTestDesignContextPolicyOverrideCommand command
    ) {
        return service.rejectOverride(id, command);
    }
}
