package com.songhg.veri.agent.testdata.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdata.application.TestAccountLeaseService;
import com.songhg.veri.agent.testdata.application.command.AcquireTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.RenewTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeasePageRequest;
import com.songhg.veri.agent.testdata.application.view.TestAccountLeaseResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/test-data/leases")
public class TestAccountLeaseController {

    private final TestAccountLeaseService service;

    public TestAccountLeaseController(TestAccountLeaseService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(value = PermissionCodes.TEST_DATA_LEASE, scope = TestDataPermissionScopes.LEASE_REQUEST)
    public TestAccountLeaseResponse acquireLease(@Valid @RequestBody AcquireTestAccountLeaseCommand command) {
        return service.acquireLease(command);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.LEASE_LIST)
    public PageResponse<TestAccountLeaseResponse> leases(@Valid TestAccountLeasePageRequest request) {
        return service.leases(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.LEASE)
    public TestAccountLeaseResponse lease(@PathVariable UUID id) {
        return service.lease(id);
    }

    @PostMapping("/{id}/renew")
    @RequirePermission(value = PermissionCodes.TEST_DATA_LEASE, scope = TestDataPermissionScopes.LEASE)
    public TestAccountLeaseResponse renewLease(
            @PathVariable UUID id,
            @Valid @RequestBody RenewTestAccountLeaseCommand command
    ) {
        return service.renewLease(id, command);
    }

    @PostMapping("/{id}/release")
    @RequirePermission(value = PermissionCodes.TEST_DATA_LEASE, scope = TestDataPermissionScopes.LEASE)
    public TestAccountLeaseResponse releaseLease(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReleaseTestAccountLeaseCommand command
    ) {
        return service.releaseLease(id, command == null ? new ReleaseTestAccountLeaseCommand(null, null) : command);
    }
}
