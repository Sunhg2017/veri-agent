package com.songhg.veri.agent.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.PlatformAccessDeniedException;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthorizationServiceTest {

    private final RecordingAuditLogWriter auditLogWriter = new RecordingAuditLogWriter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireCurrentReturnsCurrentUserWhenPermissionGranted() {
        AuthUserPrincipal principal = principal();
        AuthorizationService service = serviceWithPermissions("asset:read");
        authenticate(principal);

        AuthUserPrincipal current = service.requireCurrent("asset:read");

        assertThat(current).isSameAs(principal);
        assertThat(auditLogWriter.records).isEmpty();
    }

    @Test
    void requireCurrentSkipsPermissionCheckForServicePrincipal() {
        AuthorizationService service = serviceWithPermissions();
        ServicePrincipal principal = new ServicePrincipal("wp4-document-input", "user-1");
        authenticate(principal);

        AuthUserPrincipal current = service.requireCurrent("asset:manage");

        assertThat(current).isNull();
        assertThat(service.currentServicePrincipal()).isSameAs(principal);
        assertThat(auditLogWriter.records).isEmpty();
    }

    @Test
    void requireCurrentDeniesUserWithoutPermissionAndWritesAudit() {
        AuthUserPrincipal principal = principal();
        AuthorizationService service = serviceWithPermissions("asset:read");
        authenticate(principal);

        assertThatThrownBy(() -> service.requireCurrent("asset:manage"))
                .isInstanceOf(PlatformAccessDeniedException.class)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("asset:manage");

        assertThat(auditLogWriter.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.actor()).isSameAs(principal);
                    assertThat(record.resourceId()).isEqualTo("asset:manage");
                    assertThat(record.result()).isEqualTo("DENIED");
                });
    }

    @Test
    void resourceScopeDenialCarriesPermissionContext() {
        AuthUserPrincipal principal = principal();
        AuthorizationService service = serviceWithPermissions("asset:read");

        assertThatThrownBy(() -> service.require(principal, "asset:manage", ResourceScope.project("project-1")))
                .isInstanceOfSatisfying(PlatformAccessDeniedException.class, exception -> {
                    assertThat(exception.getErrorCode().name()).isEqualTo("FORBIDDEN");
                    assertThat(exception.getPermission()).isEqualTo("asset:manage");
                    assertThat(exception.getResourceType()).isEqualTo("PROJECT");
                    assertThat(exception.getResourceId()).isEqualTo("asset:manage@PROJECT:project-1");
                });

        assertThat(auditLogWriter.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.resourceId()).isEqualTo("asset:manage@PROJECT:project-1");
                    assertThat(record.result()).isEqualTo("DENIED");
                });
    }

    private AuthorizationService serviceWithPermissions(String... permissions) {
        Set<String> grantedPermissions = Set.of(permissions);
        return new AuthorizationService(roles -> grantedPermissions, auditLogWriter);
    }

    private AuthUserPrincipal principal() {
        return new AuthUserPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tester",
                "Tester",
                "tester@example.com",
                false,
                1,
                List.of("role")
        );
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null)
        );
    }

    private static class RecordingAuditLogWriter implements AuditLogWriter {

        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public void record(AuditRecord record) {
            records.add(record);
        }
    }
}
