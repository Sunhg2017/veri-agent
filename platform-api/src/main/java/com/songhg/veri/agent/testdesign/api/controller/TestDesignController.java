package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignService;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignTaskCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignPublishCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidatePageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskPageRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignHealthResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design")
public class TestDesignController {

    private final TestDesignService service;

    public TestDesignController(TestDesignService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public TestDesignHealthResponse health() {
        return service.health();
    }

    @GetMapping("/tasks")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK_LIST)
    public PageResponse<TestDesignTaskResponse> tasks(@Valid TestDesignTaskPageRequest request) {
        return service.tasks(request.toQuery());
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_GENERATE, scope = TestDesignPermissionScopes.PROJECT_REQUEST)
    public TestDesignTaskDetailResponse createTask(@Valid @RequestBody CreateTestDesignTaskCommand command) {
        return service.createTask(command);
    }

    @GetMapping("/tasks/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskDetailResponse task(@PathVariable UUID id) {
        return service.task(id);
    }

    @PostMapping("/tasks/{id}/retry")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_GENERATE, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskDetailResponse retryTask(@PathVariable UUID id) {
        return service.retryTask(id);
    }

    @PostMapping("/tasks/{id}/cancel")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_GENERATE, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskDetailResponse cancelTask(@PathVariable UUID id) {
        return service.cancelTask(id);
    }

    @GetMapping("/tasks/{id}/candidates")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignCandidateResponse> taskCandidates(
            @PathVariable UUID id,
            @Valid TestDesignCandidatePageRequest request
    ) {
        return service.candidates(request.toQuery(id));
    }

    @PostMapping("/tasks/{id}/publish-dry-run")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignPublishResponse publishDryRun(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignPublishCommand command
    ) {
        return service.publishDryRun(id, command);
    }

    @PostMapping("/tasks/{id}/publish")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignPublishResponse publish(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignPublishCommand command
    ) {
        return service.publish(id, command);
    }

    @GetMapping("/tasks/{id}/publish-records")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        return service.publishRecords(id);
    }
}
