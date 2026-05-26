package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.List;

/**
 * Typed parameter objects for management store queries.
 *
 * <p>These records replace raw parameter bags with compile-time visible query fields. Each
 * {@code toParams()} method produces the same property names the MyBatis adapter expects.
 */
public final class ManagementQueries {

    private ManagementQueries() {
    }

    public record DepartmentQuery(
            /** 部门名称或编码搜索关键字。 */
            String search,
            /** 部门状态过滤条件。 */
            String status,
            /** 分页参数。 */
            PageQuery page
    ) {
        public ManagementStoreParams toParams() {
            ManagementStoreParams m = ManagementStoreParams.empty();
            m.put("search", search);
            m.put("status", status);
            if (page != null) {
                m.put("limit", page.size());
                m.put("offset", page.offset());
            }
            return m;
        }
    }

    public record UserQuery(
            /** 用户名、展示名或邮箱搜索关键字。 */
            String search,
            /** 角色编码过滤条件。 */
            String roleCode,
            /** 所属部门 ID 过滤条件。 */
            String departmentId,
            /** 用户状态过滤条件。 */
            String status,
            /** 分页参数。 */
            PageQuery page
    ) {
        public ManagementStoreParams toParams() {
            ManagementStoreParams m = ManagementStoreParams.empty();
            m.put("search", search);
            m.put("roleCode", roleCode);
            m.put("departmentId", departmentId);
            m.put("status", status);
            if (page != null) {
                m.put("limit", page.size());
                m.put("offset", page.offset());
            }
            return m;
        }
    }

    public record RoleQuery(
            /** 角色编码或名称搜索关键字。 */
            String search,
            /** 角色适用范围类型。 */
            String scopeType,
            /** 角色状态过滤条件。 */
            String status,
            /** 分页参数。 */
            PageQuery page
    ) {
        public ManagementStoreParams toParams() {
            ManagementStoreParams m = ManagementStoreParams.empty();
            m.put("search", search);
            m.put("scopeType", scopeType);
            m.put("status", status);
            if (page != null) {
                m.put("limit", page.size());
                m.put("offset", page.offset());
            }
            return m;
        }
    }

    public record ProjectQuery(
            /** 项目名称或编码搜索关键字。 */
            String search,
            /** 所属部门 ID 过滤条件。 */
            String departmentId,
            /** 项目状态过滤条件。 */
            String status,
            /** 当前用户 ID，用于成员关系过滤。 */
            String userId,
            /** 当前用户可见的项目编码列表。 */
            List<String> visibleProjectKeys,
            /** 分页参数。 */
            PageQuery page
    ) {
        public ManagementStoreParams toParams() {
            ManagementStoreParams m = ManagementStoreParams.empty();
            m.put("search", search);
            m.put("departmentId", departmentId);
            m.put("status", status);
            m.put("userId", userId);
            m.put("visibleProjectKeys", visibleProjectKeys);
            if (page != null) {
                m.put("limit", page.size());
                m.put("offset", page.offset());
            }
            return m;
        }
    }

    public record ApplicationQuery(
            /** 应用名称或编码搜索关键字。 */
            String search,
            /** 所属项目编码过滤条件。 */
            String projectKey,
            /** 应用状态过滤条件。 */
            String status,
            /** 当前用户 ID，用于成员关系过滤。 */
            String userId,
            /** 当前用户可见的项目编码列表。 */
            List<String> visibleProjectKeys,
            /** 分页参数。 */
            PageQuery page
    ) {
        public ManagementStoreParams toParams() {
            ManagementStoreParams m = ManagementStoreParams.empty();
            m.put("search", search);
            m.put("projectKey", projectKey);
            m.put("status", status);
            m.put("userId", userId);
            m.put("visibleProjectKeys", visibleProjectKeys);
            if (page != null) {
                m.put("limit", page.size());
                m.put("offset", page.offset());
            }
            return m;
        }
    }

    public record EnvironmentQuery(
            /** 环境名称或编码搜索关键字。 */
            String search,
            /** 所属项目编码过滤条件。 */
            String projectKey,
            /** 环境状态过滤条件。 */
            String status,
            /** 当前用户 ID，用于成员关系过滤。 */
            String userId,
            /** 当前用户可见的项目编码列表。 */
            List<String> visibleProjectKeys,
            /** 分页参数。 */
            PageQuery page
    ) {
        public ManagementStoreParams toParams() {
            ManagementStoreParams m = ManagementStoreParams.empty();
            m.put("search", search);
            m.put("projectKey", projectKey);
            m.put("status", status);
            m.put("userId", userId);
            m.put("visibleProjectKeys", visibleProjectKeys);
            if (page != null) {
                m.put("limit", page.size());
                m.put("offset", page.offset());
            }
            return m;
        }
    }
}
