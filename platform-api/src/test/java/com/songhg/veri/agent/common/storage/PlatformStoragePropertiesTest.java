package com.songhg.veri.agent.common.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformStoragePropertiesTest {

    @Test
    void defaultsToOssProvider() {
        PlatformStorageProperties properties = new PlatformStorageProperties(null, "", null);

        assertThat(properties.effectiveProvider()).isEqualTo("oss");
        assertThat(properties.effectiveOss().normalizedKeyPrefix()).isEqualTo("veri-agent/storage");
    }

    @Test
    void rejectsUnsupportedProvider() {
        PlatformStorageProperties properties = new PlatformStorageProperties("s3", "", null);

        assertThatThrownBy(properties::effectiveProvider)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported platform storage provider");
    }
}
