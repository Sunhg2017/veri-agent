package com.songhg.veri.agent.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformStorageProperties.class)
public class PlatformStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PlatformOpaqueStorageFactory platformOpaqueStorageFactory(
            PlatformStorageProperties properties,
            ObjectProvider<OSS> platformStorageOssClientProvider
    ) {
        return new PlatformOpaqueStorageFactory(properties, platformStorageOssClientProvider.getIfAvailable());
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "veri-agent.storage", name = "provider", havingValue = "oss", matchIfMissing = true)
    public OSS platformStorageOssClient(PlatformStorageProperties properties) {
        PlatformStorageProperties.OssProperties oss = properties.effectiveOss();
        return new OSSClientBuilder().build(
                oss.requiredEndpoint(),
                oss.requiredAccessKeyId(),
                oss.requiredAccessKeySecret()
        );
    }
}
