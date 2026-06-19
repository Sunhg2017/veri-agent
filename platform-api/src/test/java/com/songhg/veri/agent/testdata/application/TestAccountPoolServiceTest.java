package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolPageRequest;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAccountPoolServiceTest {

    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";

    @Test
    void rejectsBusinessApisWhenControlPlaneDisabled() {
        TestAccountPoolService service = service(false, 60, 120);

        assertThatThrownBy(() -> service.accountPools(new TestAccountPoolPageRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void createsAccountAndStoresOnlySecretRefDigest() throws Exception {
        TestDataPlatformContextClient contextClient = contextClient();
        TestAccountPoolService service = service(true, 60, 120, contextClient);

        var pool = service.createAccountPool(new CreateTestAccountPoolCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                90
        ));
        var account = service.addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                null,
                List.of("admin", "approver", "admin"),
                Map.of("applicationId", "app-alpha"),
                SECRET_REF,
                "HEALTHY",
                "login smoke passed"
        ));

        assertThat(account.secretRefDigest()).isEqualTo(sha256(SECRET_REF));
        assertThat(account.toString()).doesNotContain(SECRET_REF, "secret://");
        assertThat(account.roleTags()).containsExactly("ADMIN", "APPROVER");
        assertThat(service.accountPool(pool.id()).accounts()).singleElement()
                .extracting(item -> item.secretRefDigest())
                .isEqualTo(sha256(SECRET_REF));
        String cipherPayload = ((InMemoryTestDataRepository) repository(service)).pooledAccountSecretRefCipher(account.id())
                .orElseThrow();
        assertThat(cipherPayload).doesNotContain(SECRET_REF, "secret://");
        assertThat(cipherPayload).contains("cipherText", "authTag", "masterKeyVersion");
        verify(contextClient).writeAuditEvent(
                eq("test_data.account.updated"),
                eq("TEST_POOLED_ACCOUNT"),
                eq(account.id().toString()),
                eq("project-alpha"),
                eq("SUCCESS"),
                anyMap()
        );
    }

    @Test
    void rejectsInvalidSecretRefAndLeaseOwnedStatusInM3() {
        TestAccountPoolService service = service(true, 60, 120);
        var pool = service.createAccountPool(new CreateTestAccountPoolCommand(
                "project-alpha",
                null,
                null,
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of(),
                null
        ));

        assertThatThrownBy(() -> service.addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "LEASED",
                List.of("ADMIN"),
                Map.of(),
                SECRET_REF,
                null,
                null
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));

        assertThatThrownBy(() -> service.addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of(),
                "env:WP8_ACCOUNT_SECRET",
                null,
                null
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updatesAccountSecretAndRejectsArchivedMutation() throws Exception {
        TestAccountPoolService service = service(true, 60, 120);
        var pool = service.createAccountPool(new CreateTestAccountPoolCommand(
                "project-alpha",
                null,
                null,
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of(),
                null
        ));
        var account = service.addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of(),
                SECRET_REF,
                null,
                null
        ));

        var updated = service.updateAccount(account.id(), new UpdateTestPooledAccountCommand(
                "Admin 01 rotated",
                "LOCKED",
                List.of("ADMIN", "LOCKED"),
                Map.of("reason", "manual review"),
                "secret://wp8/accounts/admin-01-v2",
                "LOCKED",
                "manual lock"
        ));
        assertThat(updated.status()).isEqualTo("LOCKED");
        assertThat(updated.secretRefDigest()).isEqualTo(sha256("secret://wp8/accounts/admin-01-v2"));

        var archived = service.updateAccount(account.id(), new UpdateTestPooledAccountCommand(
                null,
                "ARCHIVED",
                null,
                null,
                null,
                null,
                null
        ));
        assertThat(archived.archivedAt()).isNotNull();

        assertThatThrownBy(() -> service.updateAccount(account.id(), new UpdateTestPooledAccountCommand(
                "Should fail",
                null,
                null,
                null,
                null,
                null,
                null
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void rejectsDefaultTtlAboveConfiguredMax() {
        TestAccountPoolService service = service(true, 60, 120);

        assertThatThrownBy(() -> service.createAccountPool(new CreateTestAccountPoolCommand(
                "project-alpha",
                null,
                null,
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of(),
                121
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void clearsSecretRefCipherWhenMasterKeyIsUnavailable() throws Exception {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = contextClient();
        TestAccountPoolService serviceWithMasterKey = service(
                true,
                60,
                120,
                contextClient,
                repository,
                new SecretProviderProperties(
                        "0123456789abcdef0123456789abcdef",
                        "v1",
                        "",
                        "",
                        3,
                        1,
                        "",
                        "",
                        ""
                )
        );
        var pool = serviceWithMasterKey.createAccountPool(new CreateTestAccountPoolCommand(
                "project-alpha",
                null,
                null,
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of(),
                null
        ));
        var account = serviceWithMasterKey.addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of(),
                SECRET_REF,
                null,
                null
        ));
        assertThat(repository.pooledAccountSecretRefCipher(account.id())).isPresent();

        TestAccountPoolService serviceWithoutMasterKey = service(
                true,
                60,
                120,
                contextClient,
                repository,
                new SecretProviderProperties(
                        "",
                        "v1",
                        "",
                        "",
                        3,
                        1,
                        "",
                        "",
                        ""
                )
        );
        var updated = serviceWithoutMasterKey.updateAccount(account.id(), new UpdateTestPooledAccountCommand(
                null,
                null,
                null,
                null,
                "secret://wp8/accounts/admin-01-v2",
                null,
                null
        ));

        assertThat(updated.secretRefDigest()).isEqualTo(sha256("secret://wp8/accounts/admin-01-v2"));
        assertThat(repository.pooledAccountSecretRefCipher(account.id())).isEmpty();
    }

    private TestAccountPoolService service(boolean enabled, int defaultTtlSeconds, int maxTtlSeconds) {
        return service(enabled, defaultTtlSeconds, maxTtlSeconds, contextClient());
    }

    private TestAccountPoolService service(
            boolean enabled,
            int defaultTtlSeconds,
            int maxTtlSeconds,
            TestDataPlatformContextClient contextClient
    ) {
        return service(
                enabled,
                defaultTtlSeconds,
                maxTtlSeconds,
                contextClient,
                new InMemoryTestDataRepository(),
                new SecretProviderProperties(
                        "0123456789abcdef0123456789abcdef",
                        "v1",
                        "",
                        "",
                        3,
                        1,
                        "",
                        "",
                        ""
                )
        );
    }

    private TestAccountPoolService service(
            boolean enabled,
            int defaultTtlSeconds,
            int maxTtlSeconds,
            TestDataPlatformContextClient contextClient,
            InMemoryTestDataRepository repository,
            SecretProviderProperties secretProviderProperties
    ) {
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-tester");
        return new TestAccountPoolService(
                repository,
                contextClient,
                actorResolver,
                new TestDataProperties(enabled, 10, 512, defaultTtlSeconds, maxTtlSeconds, false, true),
                secretProviderProperties,
                new ObjectMapper()
        );
    }

    private Object repository(TestAccountPoolService service) {
        try {
            var field = TestAccountPoolService.class.getDeclaredField("repository");
            field.setAccessible(true);
            return field.get(service);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private TestDataPlatformContextClient contextClient() {
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        ));
        return contextClient;
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
