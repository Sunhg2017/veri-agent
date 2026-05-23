package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetLifecycleStatusTest {

    @Test
    void enforcesAssetLifecycleTransitions() {
        assertThat(AssetLifecycleStatus.canTransition("ACTIVE", "ARCHIVED")).isTrue();
        assertThat(AssetLifecycleStatus.canTransition("ACTIVE", "DELETED")).isTrue();
        assertThat(AssetLifecycleStatus.canTransition("ARCHIVED", "ACTIVE")).isTrue();
        assertThat(AssetLifecycleStatus.canTransition("ARCHIVED", "DELETED")).isTrue();
        assertThat(AssetLifecycleStatus.canTransition("DELETED", "ACTIVE")).isTrue();

        assertThat(AssetLifecycleStatus.canTransition("DELETED", "ARCHIVED")).isFalse();
        assertThat(AssetLifecycleStatus.canTransition("LEGACY_UNKNOWN", "ACTIVE")).isFalse();
    }

    @Test
    void derivesDeletedLifecycleFromDeletedAt() {
        assertThat(AssetLifecycleStatus.normalize(null, null)).isEqualTo("ACTIVE");
        assertThat(AssetLifecycleStatus.normalize("ARCHIVED", null)).isEqualTo("ARCHIVED");
        assertThat(AssetLifecycleStatus.normalize("ARCHIVED", Instant.EPOCH)).isEqualTo("DELETED");
    }
}
