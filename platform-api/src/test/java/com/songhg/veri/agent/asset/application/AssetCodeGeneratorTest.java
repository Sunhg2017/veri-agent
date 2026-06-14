package com.songhg.veri.agent.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetCodeGeneratorTest {

    @Test
    void assetCodePreservesHistoricalPrefixAndUuidShortHashShape() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertThat(AssetCodeGenerator.assetCode("API", id)).isEqualTo("API-123e4567e89b");
    }
}
