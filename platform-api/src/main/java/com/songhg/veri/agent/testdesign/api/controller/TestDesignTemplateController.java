package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignTemplateService;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignTemplateCommand;
import com.songhg.veri.agent.testdesign.application.command.UpdateTestDesignTemplateCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTemplatePageRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTemplateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 用例生成模板管理接口。
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/templates")
public class TestDesignTemplateController {

    private final TestDesignTemplateService service;

    public TestDesignTemplateController(TestDesignTemplateService service) {
        this.service = service;
    }

    /**
     * 分页查询平台全局或项目可用的模板配置。
     */
    @GetMapping
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TEMPLATE_LIST)
    public PageResponse<TestDesignTemplateResponse> templates(@Valid TestDesignTemplatePageRequest request) {
        return service.templates(request.toQuery());
    }

    /**
     * 查询单个模板配置。
     */
    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TEMPLATE)
    public TestDesignTemplateResponse template(@PathVariable UUID id) {
        return service.template(id);
    }

    /**
     * 创建平台全局或项目级模板。
     */
    @PostMapping
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.TEMPLATE_REQUEST)
    public TestDesignTemplateResponse createTemplate(@Valid @RequestBody CreateTestDesignTemplateCommand command) {
        return service.createTemplate(command);
    }

    /**
     * 更新模板配置，不改变模板所属作用域。
     */
    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.TEMPLATE)
    public TestDesignTemplateResponse updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestDesignTemplateCommand command
    ) {
        return service.updateTemplate(id, command);
    }

    /**
     * 软禁用模板，保留历史审计和已创建任务快照。
     */
    @DeleteMapping("/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE, scope = TestDesignPermissionScopes.TEMPLATE)
    public TestDesignTemplateResponse disableTemplate(@PathVariable UUID id) {
        return service.disableTemplate(id);
    }
}
