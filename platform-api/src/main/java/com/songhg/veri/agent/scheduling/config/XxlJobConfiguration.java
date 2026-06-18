package com.songhg.veri.agent.scheduling.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boots the shared XXL-JOB executor used to trigger background maintenance and worker jobs.
 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "veri-agent.xxl-job", name = "enabled", havingValue = "true")
    XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.effectiveAdminAddresses());
        executor.setAccessToken(properties.effectiveAccessToken());
        executor.setTimeout(properties.effectiveRequestTimeoutSeconds());
        executor.setAppname(properties.executor().effectiveAppname());
        executor.setAddress(properties.executor().effectiveAddress());
        executor.setIp(properties.executor().effectiveIp());
        executor.setPort(properties.executor().effectivePort());
        executor.setLogPath(properties.executor().effectiveLogPath());
        executor.setLogRetentionDays(properties.executor().effectiveLogRetentionDays());
        return executor;
    }
}
