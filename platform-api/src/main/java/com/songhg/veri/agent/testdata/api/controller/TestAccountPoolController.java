package com.songhg.veri.agent.testdata.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdata.application.TestAccountPoolService;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolPageRequest;
import com.songhg.veri.agent.testdata.application.view.TestAccountPoolDetailResponse;
import com.songhg.veri.agent.testdata.application.view.TestAccountPoolSummaryResponse;
import com.songhg.veri.agent.testdata.application.view.TestPooledAccountResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/test-data")
public class TestAccountPoolController {

    private final TestAccountPoolService service;

    public TestAccountPoolController(TestAccountPoolService service) {
        this.service = service;
    }

    @PostMapping("/account-pools")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.ACCOUNT_POOL_REQUEST)
    public TestAccountPoolDetailResponse createAccountPool(@Valid @RequestBody CreateTestAccountPoolCommand command) {
        return service.createAccountPool(command);
    }

    @GetMapping("/account-pools")
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.ACCOUNT_POOL_LIST)
    public PageResponse<TestAccountPoolSummaryResponse> accountPools(@Valid TestAccountPoolPageRequest request) {
        return service.accountPools(request);
    }

    @GetMapping("/account-pools/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.ACCOUNT_POOL)
    public TestAccountPoolDetailResponse accountPool(@PathVariable UUID id) {
        return service.accountPool(id);
    }

    @PatchMapping("/account-pools/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.ACCOUNT_POOL)
    public TestAccountPoolDetailResponse updateAccountPool(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestAccountPoolCommand command
    ) {
        return service.updateAccountPool(id, command);
    }

    @PostMapping("/account-pools/{id}/disable")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.ACCOUNT_POOL)
    public TestAccountPoolDetailResponse disableAccountPool(@PathVariable UUID id) {
        return service.disableAccountPool(id);
    }

    @PostMapping("/account-pools/{id}/archive")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.ACCOUNT_POOL)
    public TestAccountPoolDetailResponse archiveAccountPool(@PathVariable UUID id) {
        return service.archiveAccountPool(id);
    }

    @PostMapping("/account-pools/{id}/accounts")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.ACCOUNT_POOL)
    public TestPooledAccountResponse addAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertTestPooledAccountCommand command
    ) {
        return service.addAccount(id, command);
    }

    @PatchMapping("/accounts/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.ACCOUNT)
    public TestPooledAccountResponse updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestPooledAccountCommand command
    ) {
        return service.updateAccount(id, command);
    }
}
