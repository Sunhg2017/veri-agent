package com.songhg.veri.agent.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenSecurityTest {

    @Test
    void comparesTokenContentInsteadOfObjectIdentity() {
        assertThat(TokenSecurity.constantTimeEquals("service-token", new String("service-token"))).isTrue();
        assertThat(TokenSecurity.constantTimeEquals("service-token", "other-token")).isFalse();
    }

    @Test
    void handlesNullAsEmptyTokenOnlyWhenBothSidesAreEmpty() {
        assertThat(TokenSecurity.constantTimeEquals(null, "")).isTrue();
        assertThat(TokenSecurity.constantTimeEquals(null, "configured-token")).isFalse();
    }
}
