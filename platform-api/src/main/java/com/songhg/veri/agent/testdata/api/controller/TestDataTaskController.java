package com.songhg.veri.agent.testdata.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdata.application.TestDataTaskService;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.command.RetryTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskPageRequest;
import com.songhg.veri.agent.testdata.application.view.TestDataTaskResponse;
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
@RequestMapping("/api/v1/test-data/data-tasks")
public class TestDataTaskController {

    private final TestDataTaskService service;

    public TestDataTaskController(TestDataTaskService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DATA_CLEANUP, scope = TestDataPermissionScopes.TASK_REQUEST)
    public TestDataTaskResponse createTask(@Valid @RequestBody CreateTestDataTaskCommand command) {
        return service.createTask(command);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.TASK_LIST)
    public PageResponse<TestDataTaskResponse> tasks(@Valid TestDataTaskPageRequest request) {
        return service.tasks(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.TASK)
    public TestDataTaskResponse task(@PathVariable UUID id) {
        return service.task(id);
    }

    @PostMapping("/{id}/retry")
    @RequirePermission(value = PermissionCodes.TEST_DATA_CLEANUP, scope = TestDataPermissionScopes.TASK)
    public TestDataTaskResponse retryTask(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RetryTestDataTaskCommand command
    ) {
        return service.retryTask(id, command == null ? new RetryTestDataTaskCommand(null, null) : command);
    }
}
