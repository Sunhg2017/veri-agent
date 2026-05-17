package com.songhg.veri.agent.bootstrap.config;

import com.songhg.veri.agent.bootstrap.application.BootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapConfiguration {
}

