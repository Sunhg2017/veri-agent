package com.songhg.veri.agent.auth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.auth.infrastructure.InMemoryAuthSessionStore;
import com.songhg.veri.agent.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTokenServiceTest {

    @Test
    void rejectsTokenSecretShorterThanThirtyTwoBytes() {
        AuthTokenService tokenService = tokenService("short-token-secret");

        assertThatThrownBy(() -> tokenService.issue(user()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要 32 字节");
    }

    @Test
    void issuesAndVerifiesTokenWithStrongSecret() {
        AuthTokenService tokenService = tokenService("test-auth-secret-32-byte-minimum!");

        AuthTokenService.IssuedToken issuedToken = tokenService.issue(user());

        assertThat(tokenService.verify(issuedToken.accessToken()))
                .hasValueSatisfying(principal -> {
                    assertThat(principal.username()).isEqualTo("admin_user");
                    assertThat(principal.roles()).containsExactly("SuperAdmin");
                });
    }

    private AuthTokenService tokenService(String tokenSecret) {
        return new AuthTokenService(
                new AuthProperties(tokenSecret, 30, true, 60),
                new ObjectMapper().findAndRegisterModules(),
                new InMemoryAuthSessionStore(),
                Clock.fixed(Instant.parse("2030-05-23T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private AuthUserRecord user() {
        return new AuthUserRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "admin_user",
                "平台管理员",
                "admin@example.com",
                "$2a$10$Jzq9ZfxqSLsHwZwYBrB7F.cxtw.TCZauDIX83dGCLMGAxXAjyqdJy",
                false,
                1,
                List.of("SuperAdmin")
        );
    }
}
