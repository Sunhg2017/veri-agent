package com.songhg.veri.agent.management.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.management.application.port.ManagementQueries.ProjectQuery;
import com.songhg.veri.agent.management.application.port.ManagementQueries.UserQuery;
import org.junit.jupiter.api.Test;

class ManagementStoreParamsTest {

    @Test
    void acceptsKeysEmittedByTypedQueryObjects() {
        ManagementStoreParams userParams = new UserQuery("", "Developer", "dept-a", "ENABLED", null).toParams();
        ManagementStoreParams projectParams = new ProjectQuery("", "dept-a", "ACTIVE", "user-a", null, null).toParams();

        assertThat(userParams.get("departmentId")).isEqualTo("dept-a");
        assertThat(userParams.getDepartmentId()).isEqualTo("dept-a");
        assertThat(projectParams.get("departmentId")).isEqualTo("dept-a");
    }

    @Test
    void rejectsUnknownParameterNames() {
        ManagementStoreParams params = ManagementStoreParams.empty();

        assertThatThrownBy(() -> params.put("typoKey", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知管理存储参数");
    }
}
