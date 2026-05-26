package com.songhg.veri.agent.common.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformEventProperties.class)
public class PlatformEventConfiguration {
}
