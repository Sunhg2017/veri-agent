package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.BasePageRequest;
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
import com.songhg.veri.agent.testdesign.application.view.TestDesignReviewRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 用例生成任务、发布和发布记录接口
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design")
public class TestDesignController {

    private final TestDesignService service;

    public TestDesignController(TestDesignService service) {
        this.service = service;
    }

    /**
     * 查询 WP5 服务状态、生成模式和当前配置上限
     */
    @GetMapping("/health")
    public TestDesignHealthResponse health() {
        return service.health();
    }

    /**
     * 分页查询当前调用方可见的 WP5 用例生成任务
     */
    @GetMapping("/tasks")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK_LIST)
    public PageResponse<TestDesignTaskResponse> tasks(@Valid TestDesignTaskPageRequest request) {
        return service.tasks(request.toQuery());
    }

    /**
     * 创建 WP5 用例生成任务
     *
     * @param idempotencyKey 请求头中的幂等键；与请求体幂等键二选一，优先参与任务创建回放
     * @param command 创建任务请求体
     * @return 新建或幂等回放的任务详情
     */
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_GENERATE, scope = TestDesignPermissionScopes.PROJECT_REQUEST)
    public TestDesignTaskDetailResponse createTask(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTestDesignTaskCommand command
    ) {
        return service.createTask(command, idempotencyKey);
    }

    /**
     * 查询单个 WP5 任务详情，包含候选和发布记录摘要
     */
    @GetMapping("/tasks/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskDetailResponse task(@PathVariable UUID id) {
        return service.task(id);
    }

    /**
     * 查询单个 WP5 任务摘要，不加载候选明细，用于前端分页场景下的轻量状态刷新
     */
    @GetMapping("/tasks/{id}/summary")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskResponse taskSummary(@PathVariable UUID id) {
        return service.taskSummary(id);
    }

    /**
     * 对失败或部分失败的任务发起重试
     */
    @PostMapping("/tasks/{id}/retry")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_GENERATE, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskDetailResponse retryTask(@PathVariable UUID id) {
        return service.retryTask(id);
    }

    /**
     * 取消尚未完成的 WP5 生成任务
     */
    @PostMapping("/tasks/{id}/cancel")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_GENERATE, scope = TestDesignPermissionScopes.TASK)
    public TestDesignTaskDetailResponse cancelTask(@PathVariable UUID id) {
        return service.cancelTask(id);
    }

    /**
     * 分页查询某个任务下的候选用例
     */
    @GetMapping("/tasks/{id}/candidates")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignCandidateResponse> taskCandidates(
            @PathVariable UUID id,
            @Valid TestDesignCandidatePageRequest request
    ) {
        return service.candidates(request.toQuery(id));
    }

    /**
     * 预发布检查候选，不写入 WP3 资产库
     */
    @PostMapping("/tasks/{id}/publish-dry-run")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignPublishResponse publishDryRun(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignPublishCommand command
    ) {
        return service.publishDryRun(id, command);
    }

    /**
     * 正式发布已确认候选到 WP3 测试用例资产库
     */
    @PostMapping("/tasks/{id}/publish")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_PUBLISH, scope = TestDesignPermissionScopes.TASK)
    public TestDesignPublishResponse publish(
            @PathVariable UUID id,
            @RequestBody(required = false) TestDesignPublishCommand command
    ) {
        return service.publish(id, command);
    }

    /**
     * 查询某个任务下的发布和预发布记录
     */
    @GetMapping("/tasks/{id}/publish-records")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        return service.publishRecords(id);
    }

    /**
     * 分页查询某个任务下的候选编辑和评审历史，只返回脱敏摘要字段
     */
    @GetMapping("/tasks/{id}/review-records")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public PageResponse<TestDesignReviewRecordResponse> reviewRecords(
            @PathVariable UUID id,
            @Valid BasePageRequest request
    ) {
        return service.reviewRecords(id, request.toPageQuery());
    }

    /**
     * 导出某个任务下的候选评审历史 CSV，报告内容限定为审计白名单字段
     */
    @GetMapping(value = "/tasks/{id}/review-records/export", produces = "text/csv")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_EXPORT, scope = TestDesignPermissionScopes.TASK)
    public ResponseEntity<String> exportReviewRecords(@PathVariable UUID id) {
        String csv = service.exportReviewRecordsCsv(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp5-review-records.csv\"")
                .body(csv);
    }
}
