package com.songhg.veri.agent.authorization.application;

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
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirePermissionAspectTest {

    private final RecordingAuditLogWriter auditLogWriter = new RecordingAuditLogWriter();
    private final GenericApplicationContext context = applicationContext();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void allowsScopedPermissionFromBeanExpression() {
        SecuredOperations operations = proxy();
        authenticate(user("ProjectOwner@PROJECT:project-1"));

        String result = operations.manageProject("project-1");

        assertThat(result).isEqualTo("project-1");
        assertThat(auditLogWriter.records).isEmpty();
    }

    @Test
    void deniesScopedPermissionAndWritesResourceContext() {
        SecuredOperations operations = proxy();
        authenticate(user("ProjectOwner@PROJECT:project-1"));

        assertThatThrownBy(() -> operations.manageProject("project-2"))
                .isInstanceOf(PlatformAccessDeniedException.class)
                .hasMessageContaining("asset:manage");

        assertThat(auditLogWriter.records)
                .singleElement()
                .satisfies(record -> assertThat(record.resourceId())
                        .isEqualTo("asset:manage@PROJECT:project-2"));
    }

    @Test
    void checksEveryScopeReturnedByExpression() {
        SecuredOperations operations = proxy();
        authenticate(user("ProjectOwner@PROJECT:project-1"));

        assertThatThrownBy(() -> operations.readTwoProjects("project-1", "project-2"))
                .isInstanceOf(PlatformAccessDeniedException.class);

        assertThat(auditLogWriter.records)
                .singleElement()
                .satisfies(record -> assertThat(record.resourceId())
                        .isEqualTo("asset:read@PROJECT:project-2"));
    }

    @Test
    void checksEveryScalarScopeReturnedByArrayExpression() {
        SecuredOperations operations = proxy();
        authenticate(user("ProjectOwner@PROJECT:project-1"));

        assertThatThrownBy(() -> operations.readTwoProjectIds("project-1", "project-2"))
                .isInstanceOf(PlatformAccessDeniedException.class);

        assertThat(auditLogWriter.records)
                .singleElement()
                .satisfies(record -> assertThat(record.resourceId())
                        .isEqualTo("asset:read@PROJECT:project-2"));
    }

    @Test
    void skipsScopedExpressionForServicePrincipal() {
        SecuredOperations operations = proxy();
        authenticate(new ServicePrincipal("wp4-document-input", "user-1"));

        String result = operations.manageProject("project-unknown");

        assertThat(result).isEqualTo("project-unknown");
        assertThat(auditLogWriter.records).isEmpty();
    }

    @Test
    void checksRepeatedPermissionAnnotations() {
        SecuredOperations operations = proxy();
        authenticate(user("ProjectOwner@PROJECT:project-1"));

        assertThatThrownBy(operations::exportAsset)
                .isInstanceOf(PlatformAccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.ASSET_EXPORT);
    }

    private SecuredOperations proxy() {
        AuthorizationService authorizationService = new AuthorizationService(
                new ScopedPermissionResolver(),
                auditLogWriter
        );
        RequirePermissionAspect aspect = new RequirePermissionAspect(authorizationService, context);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SecuredOperations());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private GenericApplicationContext applicationContext() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.registerBean("testScopeResolver", TestScopeResolver.class);
        applicationContext.refresh();
        return applicationContext;
    }

    private AuthUserPrincipal user(String role) {
        return new AuthUserPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tester",
                "Tester",
                "tester@example.com",
                false,
                1,
                List.of(role)
        );
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null)
        );
    }

    public static class SecuredOperations {

        @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = "@testScopeResolver.project(#projectId)")
        public String manageProject(String projectId) {
            return projectId;
        }

        @RequirePermission(value = PermissionCodes.ASSET_READ, scope = "@testScopeResolver.projects(#first, #second)")
        public String readTwoProjects(String first, String second) {
            return first + "," + second;
        }

        @RequirePermission(value = PermissionCodes.ASSET_READ, scope = "@testScopeResolver.projectIds(#first, #second)")
        public String readTwoProjectIds(String first, String second) {
            return first + "," + second;
        }

        @RequirePermission(PermissionCodes.ASSET_READ)
        @RequirePermission(PermissionCodes.ASSET_EXPORT)
        public String exportAsset() {
            return "asset-export";
        }
    }

    public static class TestScopeResolver {

        public ResourceScope project(String projectId) {
            return ResourceScope.project(projectId);
        }

        public List<ResourceScope> projects(String first, String second) {
            return List.of(ResourceScope.project(first), ResourceScope.project(second));
        }

        public String[] projectIds(String first, String second) {
            return new String[]{first, second};
        }
    }

    private static class ScopedPermissionResolver implements PermissionResolver {

        @Override
        public Set<String> permissionsForRoles(List<String> roles) {
            return Set.of(PermissionCodes.ASSET_READ, PermissionCodes.ASSET_MANAGE);
        }

        @Override
        public boolean hasPermission(AuthUserPrincipal principal, String permission, ResourceScope scope) {
            return principal.roles().contains("ProjectOwner@" + scope.scopeType() + ":" + scope.scopeId());
        }
    }

    private static class RecordingAuditLogWriter implements AuditLogWriter {

        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public void record(AuditRecord record) {
            records.add(record);
        }
    }
}
