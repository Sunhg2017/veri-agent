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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 候选用例查询、编辑和评审接口
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/candidates")
public class TestDesignCandidateController {

    private final TestDesignService service;

    public TestDesignCandidateController(TestDesignService service) {
        this.service = service;
    }

    /**
     * 分页查询当前调用方可见的候选用例
     */
    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.CANDIDATE_LIST)
    public PageResponse<TestDesignCandidateResponse> candidates(@Valid TestDesignCandidatePageRequest request) {
        return service.candidates(request.toQuery(null));
    }

    /**
     * 按当前筛选条件导出候选摘要 CSV，不返回步骤、描述、Prompt 或原始模型输入。
     */
    @GetMapping(value = "/export", produces = "text/csv")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.CANDIDATE_LIST)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.CANDIDATE_LIST)
    public ResponseEntity<String> exportCandidates(@Valid TestDesignCandidatePageRequest request) {
        String csv = service.exportCandidatesCsv(request.toQuery(null));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp5-candidates.csv\"")
                .body(csv);
    }

    /**
     * 人工编辑候选用例内容，成功后候选进入 EDITED 状态并记录差异
     */
    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse updateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestDesignCandidateCommand command
    ) {
        return service.updateCandidate(id, command);
    }

    /**
     * 确认候选用例，使其进入发布池
     */
    @PostMapping("/{id}/confirm")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse confirmCandidate(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignCandidateActionCommand command
    ) {
        return service.confirmCandidate(id, command);
    }

    /**
     * 驳回候选用例，候选不会进入发布池
     */
    @PostMapping("/{id}/reject")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse rejectCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody TestDesignCandidateActionCommand command
    ) {
        return service.rejectCandidate(id, command);
    }

    /**
     * 忽略候选用例，候选保留在任务记录中但不会进入发布池
     */
    @PostMapping("/{id}/ignore")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE)
    public TestDesignCandidateResponse ignoreCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody TestDesignCandidateActionCommand command
    ) {
        return service.ignoreCandidate(id, command);
    }

    /**
     * 批量确认、驳回或忽略候选用例，逐候选返回成功或失败明细
     */
    @PostMapping("/batch-action")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_REVIEW, scope = TestDesignPermissionScopes.CANDIDATE_BATCH)
    public TestDesignCandidateBatchActionResponse batchAction(
            @Valid @RequestBody TestDesignCandidateBatchActionCommand command
    ) {
        return service.batchCandidateAction(command);
    }
}
