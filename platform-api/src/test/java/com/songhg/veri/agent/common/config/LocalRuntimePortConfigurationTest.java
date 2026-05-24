package com.songhg.veri.agent.common.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.common.error.BusinessException;
import org.junit.jupiter.api.Test;

class LocalRuntimePortConfigurationTest {

    @Test
    void localRuntimePortsFailFastInsteadOfServingFixtureData() {
        var configuration = new LocalRuntimePortConfiguration();

        assertThatThrownBy(() -> configuration.localAssetRepository().requirements("project-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("正常业务流程需要启用 db profile 和真实数据库；测试数据必须放在 src/test fixtures");
    }
}
