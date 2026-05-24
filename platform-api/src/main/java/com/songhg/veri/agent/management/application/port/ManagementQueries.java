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
            String search,
            String status,
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
            String search,
            String roleCode,
            String departmentId,
            String status,
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
            String search,
            String scopeType,
            String status,
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
            String search,
            String departmentId,
            String status,
            String userId,
            List<String> visibleProjectKeys,
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
            String search,
            String projectKey,
            String status,
            String userId,
            List<String> visibleProjectKeys,
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
            String search,
            String projectKey,
            String status,
            String userId,
            List<String> visibleProjectKeys,
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
