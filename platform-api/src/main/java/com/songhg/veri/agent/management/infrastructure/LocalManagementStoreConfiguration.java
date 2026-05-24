package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import java.lang.reflect.Proxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalManagementStoreConfiguration {

    /**
     * Keeps the local profile bootable for auth/OpenAPI tests while making management data access
     * fail explicitly. Management controller tests must use db profile and SQL fixtures.
     */
    @Bean
    public ManagementStore managementStore() {
        return (ManagementStore) Proxy.newProxyInstance(
                ManagementStore.class.getClassLoader(),
                new Class<?>[]{ManagementStore.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "FailFastLocalManagementStore";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new BusinessException(
                            ErrorCode.INVALID_STATE,
                            "管理控制面需要启用 db profile 和真实数据库"
                    );
                }
        );
    }
}
