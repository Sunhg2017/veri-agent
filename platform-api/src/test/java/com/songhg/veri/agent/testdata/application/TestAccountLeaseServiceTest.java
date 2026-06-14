package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.AcquireTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.RenewTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeasePageRequest;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAccountLeaseServiceTest {

    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";

    @Test
    void acquiresRenewsReleasesAndKeepsRequestKeyIdempotent() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService.createAccountPool(poolCommand());
        var account = fixture.poolService.addAccount(pool.id(), accountCommand("admin-01", List.of("ADMIN", "APPROVER")));

        var lease = fixture.leaseService.acquireLease(leaseCommand(pool.id(), "run-001"));
        assertThat(lease.accountId()).isEqualTo(account.id());
        assertThat(lease.status()).isEqualTo("ACTIVE");
        assertThat(lease.leaseTokenDigest()).matches("[0-9a-f]{64}");
        assertThat(lease.toString()).doesNotContain(SECRET_REF, "secret://");

        var duplicate = fixture.leaseService.acquireLease(leaseCommand(pool.id(), "run-001"));
        assertThat(duplicate.id()).isEqualTo(lease.id());

        assertThatThrownBy(() -> fixture.leaseService.acquireLease(new AcquireTestAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "EXECUTION_RUN",
                "run-001",
                90,
                "lease-run-001"
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        var renewed = fixture.leaseService.renewLease(lease.id(), new RenewTestAccountLeaseCommand(90));
        assertThat(renewed.expiresAt()).isAfter(lease.expiresAt());

        var released = fixture.leaseService.releaseLease(
                lease.id(),
                new ReleaseTestAccountLeaseCommand("run finished", "AVAILABLE")
        );
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(fixture.leaseService.leases(leaseListRequest()).items()).hasSize(1);
    }

    @Test
    void rejectsSecondActiveLeaseUntilRelease() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService.createAccountPool(poolCommand());
        fixture.poolService.addAccount(pool.id(), accountCommand("admin-01", List.of("ADMIN")));
        fixture.leaseService.acquireLease(leaseCommand(pool.id(), "run-001"));

        assertThatThrownBy(() -> fixture.leaseService.acquireLease(leaseCommand(pool.id(), "run-002")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void expiresActiveLeasesAndBlocksRenewAfterExpiry() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService.createAccountPool(poolCommand());
        fixture.poolService.addAccount(pool.id(), accountCommand("admin-01", List.of("ADMIN")));
        var lease = fixture.leaseService.acquireLease(new AcquireTestAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "EXECUTION_RUN",
                "run-001",
                1,
                "lease-run-001"
        ));

        assertThatThrownBy(() -> fixture.leaseService.renewLease(lease.id(), new RenewTestAccountLeaseCommand(121)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        int expired = fixture.leaseService.expireActiveLeases(Instant.now().plusSeconds(5), 10);
        assertThat(expired).isEqualTo(1);

        assertThatThrownBy(() -> fixture.leaseService.renewLease(lease.id(), new RenewTestAccountLeaseCommand(60)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThatThrownBy(() -> fixture.leaseService.releaseLease(
                lease.id(),
                new ReleaseTestAccountLeaseCommand("expired run", "AVAILABLE")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void rejectsBusinessApisWhenControlPlaneDisabled() {
        Fixture fixture = fixture(false);

        assertThatThrownBy(() -> fixture.leaseService.leases(new TestAccountLeasePageRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    private Fixture fixture(boolean enabled) {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = contextClient();
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-tester");
        TestDataProperties properties = new TestDataProperties(enabled, 10, 512, 60, 120, false, true);
        ObjectMapper objectMapper = new ObjectMapper();
        return new Fixture(
                new TestAccountPoolService(repository, contextClient, actorResolver, properties, objectMapper),
                new TestAccountLeaseService(repository, contextClient, actorResolver, properties, objectMapper)
        );
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

    private CreateTestAccountPoolCommand poolCommand() {
        return new CreateTestAccountPoolCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                60
        );
    }

    private UpsertTestPooledAccountCommand accountCommand(String accountKey, List<String> roleTags) {
        return new UpsertTestPooledAccountCommand(
                accountKey,
                accountKey,
                "AVAILABLE",
                roleTags,
                Map.of(),
                SECRET_REF,
                "HEALTHY",
                null
        );
    }

    private AcquireTestAccountLeaseCommand leaseCommand(java.util.UUID poolId, String holderRef) {
        return new AcquireTestAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                poolId,
                List.of("ADMIN"),
                "EXECUTION_RUN",
                holderRef,
                60,
                "lease-" + holderRef
        );
    }

    private TestAccountLeasePageRequest leaseListRequest() {
        TestAccountLeasePageRequest request = new TestAccountLeasePageRequest();
        request.setProjectId("project-alpha");
        return request;
    }

    private record Fixture(TestAccountPoolService poolService, TestAccountLeaseService leaseService) {
    }
}
