package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveContentGuardTest {

    private final SensitiveContentGuard guard = new SensitiveContentGuard();

    @Test
    void blocksAndMasksCommonPersonalAndEnterpriseSecrets() {
        String content = """
                internal_token=corp-abc-123456
                owner email alice@example.com
                Authorization: Bearer abcdefghijklmnop
                phone 13800138000
                bank card 6222 0000 0000 0000
                """;

        assertThatThrownBy(() -> guard.assertSafe(content))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号、邮箱或银行卡号");

        String masked = guard.mask(content);

        assertThat(masked)
                .contains("internal_token=***", "Bearer ***", "***EMAIL***", "***PHONE***", "***BANK_CARD***")
                .doesNotContain("corp-abc-123456", "abcdefghijklmnop", "alice@example.com", "13800138000", "6222 0000 0000 0000");
    }

    @Test
    void masksOverlappingRulesFromOriginalTextWithoutLeakingValues() {
        String content = "internal_token=corp-secret-123 api_key=alice@example.com 11010519491231002X";

        String masked = guard.mask(content);

        assertThat(masked)
                .isEqualTo("internal_token=*** api_key=*** ***ID_CARD***")
                .doesNotContain("corp-secret-123", "alice@example.com", "11010519491231002X");
    }
}
