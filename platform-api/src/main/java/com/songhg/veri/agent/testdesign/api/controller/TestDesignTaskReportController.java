package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignTaskReportService;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 任务级报告导出接口，避免任务控制器承载过多资源用例。
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/tasks/{id}/report")
public class TestDesignTaskReportController {

    private final TestDesignTaskReportService service;

    public TestDesignTaskReportController(TestDesignTaskReportService service) {
        this.service = service;
    }

    /**
     * 导出任务级全量报告 CSV，只包含任务、质量、审计和发布聚合字段。
     */
    @GetMapping(value = "/export", produces = "text/csv")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.TASK)
    public ResponseEntity<String> exportTaskReport(@PathVariable UUID id) {
        String csv = service.exportTaskReportCsv(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp5-task-report.csv\"")
                .body(csv);
    }

    /**
     * 查询任务本域审计链摘要，只聚合 WP5 任务、评审和发布记录。
     */
    @GetMapping("/audit-summary")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public TestDesignAuditSummaryResponse auditSummary(@PathVariable UUID id) {
        return service.auditSummary(id);
    }
}
