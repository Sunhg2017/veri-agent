package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignService;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualitySummaryResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 任务级质量运营摘要接口。
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/tasks/{id}/quality")
public class TestDesignTaskQualityController {

    private final TestDesignService service;

    public TestDesignTaskQualityController(TestDesignService service) {
        this.service = service;
    }

    /**
     * 查询任务全量候选质量摘要，只返回聚合指标和分布。
     */
    @GetMapping("/summary")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public TestDesignQualitySummaryResponse qualitySummary(@PathVariable UUID id) {
        return service.qualitySummary(id);
    }
}
