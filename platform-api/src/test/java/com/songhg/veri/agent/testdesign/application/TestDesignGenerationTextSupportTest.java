package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDesignGenerationTextSupportTest {

    @Test
    void redactedPreviewMasksSecretsBeforeTruncating() {
        String preview = TestDesignGenerationTextSupport.redactedPreview(
                "token=super-secret-value should not appear in a long exported field",
                32
        );

        assertThat(preview)
                .hasSizeLessThanOrEqualTo(32)
                .contains(TestDesignSensitiveText.REDACTED_SECRET)
                .doesNotContain("super-secret-value")
                .endsWith("...");
    }

    @Test
    void summaryTagsSplitsChineseCommaAndKeepsDistinctRedactedTags() {
        List<String> tags = TestDesignGenerationTextSupport.summaryTags(
                "smoke，regression,smoke,token=tag-secret-value"
        );

        assertThat(tags)
                .containsExactly("smoke", "regression", TestDesignSensitiveText.REDACTED_SECRET);
    }

    @Test
    void normalizeRejectsUnsupportedCoverageTypeAndPriority() {
        assertThatThrownBy(() -> TestDesignGenerationTextSupport.normalizeCoverageType("exploratory", "SMOKE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的覆盖类型");
        assertThatThrownBy(() -> TestDesignGenerationTextSupport.normalizePriority("urgent", "MEDIUM"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的优先级");
    }

    @Test
    void safeErrorMessageRedactsSecretsAndCapsLength() {
        String message = TestDesignGenerationTextSupport.safeErrorMessage(
                new RuntimeException("authorization=Bearer secret-token-value " + "x".repeat(600))
        );

        assertThat(message)
                .hasSizeLessThanOrEqualTo(500)
                .contains(TestDesignSensitiveText.REDACTED_SECRET)
                .doesNotContain("secret-token-value");
    }
}
