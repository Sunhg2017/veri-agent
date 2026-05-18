package com.songhg.veri.agent.bootstrap.application;

import com.songhg.veri.agent.bootstrap.api.request.SuperAdminBootstrapRequest;
import com.songhg.veri.agent.bootstrap.domain.BootstrapStateStore;
import com.songhg.veri.agent.bootstrap.domain.BootstrapUserDraft;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuperAdminBootstrapServiceTest {

    @Test
    void bootstrapsSuperAdminWithHashedPassword() {
        RecordingBootstrapStateStore store = new RecordingBootstrapStateStore();
        SuperAdminBootstrapService service = new SuperAdminBootstrapService(
                new BootstrapProperties("init-token"),
                store,
                new BCryptPasswordEncoder()
        );

        var response = service.bootstrap(request("init-token"));

        assertThat(response.role()).isEqualTo("SuperAdmin");
        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.userId()).isNotBlank();
        assertThat(store.createdDraft.passwordHash()).isNotEqualTo("PlainPassword123");
        assertThat(store.createdDraft.passwordHash()).startsWith("$2");
        assertThat(store.createdDraft.username()).isEqualTo("admin_user");
    }

    @Test
    void rejectsInvalidBootstrapToken() {
        SuperAdminBootstrapService service = new SuperAdminBootstrapService(
                new BootstrapProperties("init-token"),
                new RecordingBootstrapStateStore(),
                new BCryptPasswordEncoder()
        );

        assertThatThrownBy(() -> service.bootstrap(request("wrong-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsWhenBootstrapTokenIsNotConfigured() {
        SuperAdminBootstrapService service = new SuperAdminBootstrapService(
                new BootstrapProperties(""),
                new RecordingBootstrapStateStore(),
                new BCryptPasswordEncoder()
        );

        assertThatThrownBy(() -> service.bootstrap(request("init-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsRepeatedInitialization() {
        RecordingBootstrapStateStore store = new RecordingBootstrapStateStore();
        store.initialized = true;
        SuperAdminBootstrapService service = new SuperAdminBootstrapService(
                new BootstrapProperties("init-token"),
                store,
                new BCryptPasswordEncoder()
        );

        assertThatThrownBy(() -> service.bootstrap(request("init-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private SuperAdminBootstrapRequest request(String token) {
        return new SuperAdminBootstrapRequest(
                token,
                "admin_user",
                "PlainPassword123",
                "平台管理员",
                "admin@example.com"
        );
    }

    static class RecordingBootstrapStateStore implements BootstrapStateStore {

        boolean initialized;
        BootstrapUserDraft createdDraft;

        @Override
        public boolean hasSuperAdmin() {
            return initialized;
        }

        @Override
        public String createSuperAdmin(BootstrapUserDraft draft) {
            this.createdDraft = draft;
            this.initialized = true;
            return UUID.randomUUID().toString();
        }
    }
}
