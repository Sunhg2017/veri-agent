package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.application.view.DepartmentView;

/**
 * Department management use cases. Department keys are stable identifiers used by users and audit
 * records, so implementations must avoid silent key rewrites during updates.
 */
public interface DepartmentOperations {

    /**
     * Lists departments for management screens.
     */
    PageResponse<DepartmentView> departments(PageQuery pageQuery);

    /**
     * Creates a department and attributes the mutation to the actor.
     */
    DepartmentView createDepartment(String name, AuthUserPrincipal actor);

    /**
     * Returns a department by key or fails when the key is unknown.
     */
    DepartmentView department(String key);

    /**
     * Updates department metadata while keeping the department identity stable.
     */
    DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor);

    /**
     * Changes department lifecycle status after the controller resolves the dynamic status permission.
     */
    DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor);
}
