package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.response.DepartmentView;

public interface DepartmentOperations {

    PageResponse<DepartmentView> departments(PageQuery pageQuery);

    DepartmentView createDepartment(String name, AuthUserPrincipal actor);

    DepartmentView department(String key);

    DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor);

    DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor);
}
