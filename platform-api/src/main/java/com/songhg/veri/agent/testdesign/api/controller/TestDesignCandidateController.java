package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignService;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateActionCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateBatchActionCommand;
import com.songhg.veri.agent.testdesign.application.command.UpdateTestDesignCandidateCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidatePageRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateBatchActionResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/candidates")
public class TestDesignCandidateController {

    private final TestDesignService service;

    public TestDesignCandidateController(TestDesignService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.CANDIDATE_LIST)
    public PageResponse<TestDesignCandidateResponse> candidates(@Valid TestDesignCandidatePageRequest request) {
        return service.candidates(request.toQuery(null));
    }

    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse updateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestDesignCandidateCommand command
    ) {
        return service.updateCandidate(id, command);
    }

    @PostMapping("/{id}/confirm")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse confirmCandidate(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignCandidateActionCommand command
    ) {
        return service.confirmCandidate(id, command);
    }

    @PostMapping("/{id}/reject")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse rejectCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody TestDesignCandidateActionCommand command
    ) {
        return service.rejectCandidate(id, command);
    }

    @PostMapping("/{id}/ignore")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse ignoreCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody TestDesignCandidateActionCommand command
    ) {
        return service.ignoreCandidate(id, command);
    }

    @PostMapping("/batch-action")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE_BATCH)
    public TestDesignCandidateBatchActionResponse batchAction(
            @Valid @RequestBody TestDesignCandidateBatchActionCommand command
    ) {
        return service.batchCandidateAction(command);
    }
}
