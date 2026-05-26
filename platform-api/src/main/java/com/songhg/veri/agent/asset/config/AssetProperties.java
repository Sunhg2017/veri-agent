package com.songhg.veri.agent.asset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.asset")
public record AssetProperties(
        /** 资产服务内部调用令牌，用于服务间鉴权。 */
        String serviceToken
) {
}
