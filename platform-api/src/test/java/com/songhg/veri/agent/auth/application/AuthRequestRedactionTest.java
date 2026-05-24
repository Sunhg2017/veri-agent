package com.songhg.veri.agent.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.songhg.veri.agent.auth.application.command.ChangePasswordRequest;
import com.songhg.veri.agent.auth.application.command.LoginRequest;
import org.junit.jupiter.api.Test;

class AuthRequestRedactionTest {

    @Test
    void masksLoginPasswordInToString() {
        LoginRequest request = new LoginRequest("admin_user", "PlainPassword123");

        assertThat(request.toString())
                .contains("admin_user", "password=<masked>")
                .doesNotContain("PlainPassword123");
    }

    @Test
    void masksChangePasswordFieldsInToString() {
        ChangePasswordRequest request = new ChangePasswordRequest("PlainPassword123", "ChangedPassword123");

        assertThat(request.toString())
                .contains("oldPassword=<masked>", "newPassword=<masked>")
                .doesNotContain("PlainPassword123", "ChangedPassword123");
    }
}
