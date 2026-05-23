package com.songhg.veri.agent.asset.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetReviewStatusTest {

    @Test
    void enforcesRequirementAndTestCaseReviewStatusTransitions() {
        assertThat(AssetReviewStatus.canTransition("DRAFT", "REVIEWING")).isTrue();
        assertThat(AssetReviewStatus.canTransition("REVIEWING", "DRAFT")).isTrue();
        assertThat(AssetReviewStatus.canTransition("APPROVED", "DEPRECATED")).isTrue();

        assertThat(AssetReviewStatus.canTransition("APPROVED", "DRAFT")).isFalse();
        assertThat(AssetReviewStatus.canTransition("DEPRECATED", "APPROVED")).isFalse();
    }
}
