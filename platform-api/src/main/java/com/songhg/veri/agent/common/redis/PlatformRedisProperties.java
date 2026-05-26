package com.songhg.veri.agent.common.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "veri-agent.redis")
public record PlatformRedisProperties(
        /** Redisson single server address, for example redis://localhost:6379 */
        String address,
        /** Redis password, left blank for local development */
        String password,
        /** Redis logical database */
        int database
) {

    public String safeAddress() {
        return StringUtils.hasText(address) ? address : "redis://localhost:6379";
    }

    public String safePassword() {
        return StringUtils.hasText(password) ? password : null;
    }

    public int safeDatabase() {
        return Math.max(0, database);
    }
}
