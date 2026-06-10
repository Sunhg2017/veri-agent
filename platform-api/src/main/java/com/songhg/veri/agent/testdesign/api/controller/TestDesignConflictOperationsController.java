package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignConflictOperationsService;
import com.songhg.veri.agent.testdesign.application.query.TestDesignConflictOperationPageRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignConflictOperationsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 资产冲突运营台接口。
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/conflicts")
public class TestDesignConflictOperationsController {

    private final TestDesignConflictOperationsService service;

    public TestDesignConflictOperationsController(TestDesignConflictOperationsService service) {
        this.service = service;
    }

    /**
     * 分页查询正式发布冲突，供运营台集中检索、筛选和人工处理。
     */
    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.CONFLICT_OPERATIONS)
    public TestDesignConflictOperationsResponse conflicts(@Valid TestDesignConflictOperationPageRequest request) {
        return service.conflictOperations(request.toQuery());
    }
}
