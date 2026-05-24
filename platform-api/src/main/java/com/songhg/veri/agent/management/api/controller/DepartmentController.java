package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateNamedRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.response.DepartmentResponse;
import com.songhg.veri.agent.management.application.port.DepartmentOperations;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Department management endpoint. Controller stays thin: authorization and lifecycle rules are
 * delegated to common annotations and the management use-case implementation.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class DepartmentController {

    private final DepartmentOperations departmentOperations;
    private final ManagementApiMapper mapper;

    public DepartmentController(
            DepartmentOperations departmentOperations,
            ManagementApiMapper mapper
    ) {
        this.departmentOperations = departmentOperations;
        this.mapper = mapper;
    }

    @GetMapping("/departments")
    @RequirePermission(PermissionCodes.DEPARTMENT_READ)
    public PageResponse<DepartmentResponse> departments(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toDepartmentPage(departmentOperations.departments(pageRequest.toPageQuery()));
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.DEPARTMENT_CREATE)
    public DepartmentResponse createDepartment(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(departmentOperations.createDepartment(request.name().trim(), principal));
    }

    @GetMapping("/departments/{key}")
    @RequirePermission(PermissionCodes.DEPARTMENT_READ)
    public DepartmentResponse department(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(departmentOperations.department(key.trim()));
    }

    @PatchMapping("/departments/{key}")
    @RequirePermission(PermissionCodes.DEPARTMENT_EDIT)
    public DepartmentResponse updateDepartment(
            @PathVariable String key,
            @Valid @RequestBody UpdateDepartmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(departmentOperations.updateDepartment(key.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/departments/{key}/status")
    public DepartmentResponse changeDepartmentStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(departmentOperations.changeDepartmentStatus(key.trim(), request.status(), principal));
    }
}
