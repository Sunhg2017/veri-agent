package com.songhg.veri.agent.integration.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.integration")
public record PlatformIntegrationProperties(
        /** 平台集成内部调用令牌，用于服务间鉴权。 */
        String serviceToken
) {
}
