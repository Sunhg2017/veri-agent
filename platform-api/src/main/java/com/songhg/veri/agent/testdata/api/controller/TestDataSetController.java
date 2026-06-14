package com.songhg.veri.agent.testdata.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdata.application.TestDataSetService;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.command.ImportTestDataRecordsCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.query.TestDataSetPageRequest;
import com.songhg.veri.agent.testdata.application.view.TestDataRecordImportResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataSetDetailResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataSetSummaryResponse;
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
@RequestMapping("/api/v1/test-data/data-sets")
public class TestDataSetController {

    private final TestDataSetService service;

    public TestDataSetController(TestDataSetService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.DATA_SET_REQUEST)
    public TestDataSetDetailResponse createDataSet(@Valid @RequestBody CreateTestDataSetCommand command) {
        return service.createDataSet(command);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.DATA_SET_LIST)
    public PageResponse<TestDataSetSummaryResponse> dataSets(@Valid TestDataSetPageRequest request) {
        return service.dataSets(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_READ, scope = TestDataPermissionScopes.DATA_SET)
    public TestDataSetDetailResponse dataSet(@PathVariable UUID id) {
        return service.dataSet(id);
    }

    @PatchMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.DATA_SET)
    public TestDataSetDetailResponse updateDataSet(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestDataSetCommand command
    ) {
        return service.updateDataSet(id, command);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.DATA_SET)
    public TestDataSetDetailResponse archiveDataSet(@PathVariable UUID id) {
        return service.archiveDataSet(id);
    }

    @PostMapping("/{id}/records")
    @RequirePermission(value = PermissionCodes.TEST_DATA_MANAGE, scope = TestDataPermissionScopes.DATA_SET)
    public TestDataRecordImportResponse importRecords(
            @PathVariable UUID id,
            @Valid @RequestBody ImportTestDataRecordsCommand command
    ) {
        return service.importRecords(id, command);
    }
}
