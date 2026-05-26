package com.songhg.veri.agent.common.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("redis")
@EnableConfigurationProperties(PlatformRedisProperties.class)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(PlatformRedisProperties properties) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(properties.safeAddress())
                .setDatabase(properties.safeDatabase());
        if (properties.safePassword() != null) {
            singleServerConfig.setPassword(properties.safePassword());
        }
        return Redisson.create(config);
    }
}
