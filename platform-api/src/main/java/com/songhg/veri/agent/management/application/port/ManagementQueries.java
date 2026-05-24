package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed parameter objects for management store queries.
 *
 * <p>These records replace {@code Map<String, Object>} parameter bags with
 * compile-time safety. Each {@code toMap()} method produces the same map
 * keys the MyBatis mapper expects.
 */
public final class ManagementQueries {

    private ManagementQueries() {
    }

    public record DepartmentQuery(
            String search,
            String status,
            PageQuery page
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
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
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
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
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
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
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
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
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
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
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
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
