package com.songhg.veri.agent.management.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import org.junit.jupiter.api.Test;

class LocalManagementStoreConfigurationTest {

    @Test
    void localManagementStoreFailsFastInsteadOfServingFixtureData() {
        var store = new LocalManagementStoreConfiguration().managementStore();

        assertThatThrownBy(() -> store.listDepartments(ManagementStoreParams.empty()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("管理控制面需要启用 db profile 和真实数据库");
    }
}
