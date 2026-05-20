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
                phone 13800138000
                bank card 6222 0000 0000 0000
                """;

        assertThatThrownBy(() -> guard.assertSafe(content))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号、邮箱或银行卡号");

        String masked = guard.mask(content);

        assertThat(masked)
                .contains("internal_token=***", "***EMAIL***", "***PHONE***", "***BANK_CARD***")
                .doesNotContain("alice@example.com", "13800138000", "6222 0000 0000 0000");
    }
}
