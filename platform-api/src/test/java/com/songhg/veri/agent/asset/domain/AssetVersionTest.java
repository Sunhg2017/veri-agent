package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetVersionTest {

    @Test
    void exposesInitialAndNextVersionRules() {
        assertThat(AssetVersion.initial()).isEqualTo(1);
        assertThat(AssetVersion.next(1)).isEqualTo(2);
        assertThat(requirement(3).nextVersion()).isEqualTo(4);
        assertThat(testCase(5).nextVersion()).isEqualTo(6);
    }

    @Test
    void findsExactHistoryVersion() {
        AssetVersionHistory first = history(1);
        AssetVersionHistory third = history(3);

        assertThat(AssetVersion.find(List.of(first, third), 3)).contains(third);
        assertThat(AssetVersion.find(List.of(first, third), 2)).isEmpty();
    }

    private static AssetRequirement requirement(int version) {
        Instant now = Instant.EPOCH;
        return new AssetRequirement(
                UUID.randomUUID(),
                "REQ-1",
                "Requirement",
                "Description",
                "MANUAL",
                null,
                null,
                null,
                "DRAFT",
                "MEDIUM",
                "project-1",
                null,
                version,
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }

    private static TestCaseRecord testCase(int version) {
        Instant now = Instant.EPOCH;
        return new TestCaseRecord(
                UUID.randomUUID(),
                "TC-1",
                "Test case",
                "Description",
                "project-1",
                null,
                null,
                "MANUAL",
                null,
                "DRAFT",
                "MEDIUM",
                null,
                List.of(),
                version,
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }

    private static AssetVersionHistory history(int version) {
        return new AssetVersionHistory(
                UUID.randomUUID(),
                "REQUIREMENT",
                UUID.randomUUID(),
                "project-1",
                version,
                "UPDATE",
                "tester",
                "",
                "{}",
                "{}",
                "trace-1",
                Instant.EPOCH
        );
    }
}
