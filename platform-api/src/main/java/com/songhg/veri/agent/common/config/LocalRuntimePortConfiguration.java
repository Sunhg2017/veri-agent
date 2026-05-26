package com.songhg.veri.agent.common.config;

import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.auth.domain.AuthIdentityStore;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import com.songhg.veri.agent.authorization.application.PermissionResolver;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import java.lang.reflect.Proxy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalRuntimePortConfiguration {

    private static final String DB_REQUIRED_MESSAGE =
            "正常业务流程需要启用 db profile 和真实数据库；测试数据必须放在 src/test fixtures";

    @Bean
    @ConditionalOnMissingBean
    public AuthIdentityStore localAuthIdentityStore() {
        return failFast(AuthIdentityStore.class, "AuthIdentityStore");
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthSessionStore localAuthSessionStore() {
        return failFast(AuthSessionStore.class, "AuthSessionStore");
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionResolver localPermissionResolver() {
        return failFast(PermissionResolver.class, "PermissionResolver");
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogWriter localAuditLogWriter() {
        return failFast(AuditLogWriter.class, "AuditLogWriter");
    }

    @Bean
    @ConditionalOnMissingBean
    public AssetRepository localAssetRepository() {
        return failFast(AssetRepository.class, "AssetRepository");
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentInputRepository localDocumentInputRepository() {
        return failFast(DocumentInputRepository.class, "DocumentInputRepository");
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelAccessRepository localModelAccessRepository() {
        return failFast(ModelAccessRepository.class, "ModelAccessRepository");
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelInvocationJobRepository localModelInvocationJobRepository() {
        return failFast(ModelInvocationJobRepository.class, "ModelInvocationJobRepository");
    }

    /**
     * Keeps the local profile bootable without allowing runtime requests to read or mutate
     * process-local fixture stores. Unit tests can still provide explicit test fixtures.
     */
    private <T> T failFast(Class<T> portType, String portName) {
        return portType.cast(Proxy.newProxyInstance(
                portType.getClassLoader(),
                new Class<?>[]{portType},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "FailFastLocal" + portName;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return args != null && args.length == 1 && proxy == args[0];
                    }
                    throw new BusinessException(ErrorCode.INVALID_STATE, DB_REQUIRED_MESSAGE);
                }
        ));
    }
}
