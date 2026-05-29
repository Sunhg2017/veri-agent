package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignCandidateReviewService;
import com.songhg.veri.agent.testdesign.application.TestDesignPublishService;
import com.songhg.veri.agent.testdesign.application.command.TestDesignPublishCommand;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReviewRecordResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 task publish and review-history endpoints.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/tasks/{id}")
public class TestDesignTaskPublishController {

    private final TestDesignPublishService publishService;
    private final TestDesignCandidateReviewService candidateReviewService;

    public TestDesignTaskPublishController(
            TestDesignPublishService publishService,
            TestDesignCandidateReviewService candidateReviewService
    ) {
        this.publishService = publishService;
        this.candidateReviewService = candidateReviewService;
    }

    @PostMapping("/publish-dry-run")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignPublishResponse publishDryRun(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignPublishCommand command
    ) {
        return publishService.publishDryRun(id, command);
    }

    @PostMapping("/publish")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignPublishResponse publish(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignPublishCommand command
    ) {
        return publishService.publish(id, command);
    }

    @GetMapping("/publish-records")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        return publishService.publishRecords(id);
    }

    @GetMapping("/review-records")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignReviewRecordResponse> reviewRecords(
            @PathVariable UUID id,
            @Valid BasePageRequest request
    ) {
        return candidateReviewService.reviewRecords(id, request.toPageQuery());
    }

    @GetMapping(value = "/review-records/export", produces = "text/csv")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.TASK)
    public ResponseEntity<String> exportReviewRecords(@PathVariable UUID id) {
        String csv = candidateReviewService.exportReviewRecordsCsv(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp5-review-records.csv\"")
                .body(csv);
    }
}
