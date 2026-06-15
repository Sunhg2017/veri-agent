package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationBundleScope;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationBundleScopeService;
import com.songhg.veri.agent.execution.application.command.ExecutionDagCommand;
import com.songhg.veri.agent.execution.application.command.ExecutionDagNodeCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionDagValidatorTest {

    @Test
    void validatesDagAndRedactsSecretLikeInput() {
        UUID bundleId = UUID.randomUUID();
        ApiAutomationBundleScopeService bundleScopeService = mock(ApiAutomationBundleScopeService.class);
        when(bundleScopeService.bundleScope(bundleId))
                .thenReturn(Optional.of(new ApiAutomationBundleScope(bundleId, "project-alpha", "APPROVED")));
        ExecutionDagValidator validator = new ExecutionDagValidator(bundleScopeService, new ObjectMapper());

        ExecutionDagValidationResult result = validator.validate(
                UUID.randomUUID(),
                "project-alpha",
                new ExecutionDagCommand(List.of(
                        new ExecutionDagNodeCommand(
                                "api-smoke",
                                "API_TEST",
                                List.of(),
                                Map.of(
                                        "apiAutomationBundleId", bundleId.toString(),
                                        "secretRefs", List.of("secret://wp6/token"),
                                        "runtimeSecretRefs", List.of("secret://wp6/runtime-token")
                                ),
                                120,
                                "FAIL_FAST",
                                Map.of("maxAttempts", 1)
                        ),
                        new ExecutionDagNodeCommand(
                                "report",
                                "REPORT_HANDOFF",
                                List.of("api-smoke"),
                                Map.of("summaryOnly", true),
                                60,
                                "CONTINUE",
                                Map.of()
                        )
                )),
                Instant.EPOCH
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
        assertThat(result.dagDigest()).hasSize(64);
        assertThat(result.nodes()).extracting("nodeKey").containsExactly("api-smoke", "report");
        assertThat(result.nodePolicies()).filteredOn(policy -> "api-smoke".equals(policy.key()))
                .singleElement()
                .satisfies(policy -> assertThat(policy.inputSummary().get("secretRefs"))
                        .isEqualTo(Map.of("masked", true, "count", 1)));
    }

    @Test
    void rejectsInvalidRuntimeSecretRefs() {
        UUID bundleId = UUID.randomUUID();
        ApiAutomationBundleScopeService bundleScopeService = mock(ApiAutomationBundleScopeService.class);
        when(bundleScopeService.bundleScope(bundleId))
                .thenReturn(Optional.of(new ApiAutomationBundleScope(bundleId, "project-alpha", "APPROVED")));
        ExecutionDagValidator validator = new ExecutionDagValidator(bundleScopeService, new ObjectMapper());

        ExecutionDagValidationResult result = validator.validate(
                UUID.randomUUID(),
                "project-alpha",
                new ExecutionDagCommand(List.of(new ExecutionDagNodeCommand(
                        "api-smoke",
                        "API_TEST",
                        List.of(),
                        Map.of(
                                "apiAutomationBundleId", bundleId.toString(),
                                "runtimeSecretRefs", List.of("plain-token")
                        ),
                        120,
                        "FAIL_FAST",
                        Map.of()
                ))),
                Instant.EPOCH
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting("code")
                .contains("EXECUTION_RUNTIME_SECRET_REFS_INVALID");
    }

    @Test
    void validatesAccountLeaseOnlyForApiTestNodes() {
        UUID bundleId = UUID.randomUUID();
        UUID poolId = UUID.randomUUID();
        ApiAutomationBundleScopeService bundleScopeService = mock(ApiAutomationBundleScopeService.class);
        when(bundleScopeService.bundleScope(bundleId))
                .thenReturn(Optional.of(new ApiAutomationBundleScope(bundleId, "project-alpha", "APPROVED")));
        ExecutionDagValidator validator = new ExecutionDagValidator(bundleScopeService, new ObjectMapper());

        ExecutionDagValidationResult result = validator.validate(
                UUID.randomUUID(),
                "project-alpha",
                new ExecutionDagCommand(List.of(
                        new ExecutionDagNodeCommand(
                                "api-smoke",
                                "API_TEST",
                                List.of(),
                                Map.of(
                                        "apiAutomationBundleId", bundleId.toString(),
                                        "accountLease", Map.of(
                                                "accountPoolRef", poolId.toString(),
                                                "applicationId", "app-alpha",
                                                "environmentId", "env-staging",
                                                "roleTags", List.of("ADMIN"),
                                                "ttlSeconds", 120,
                                                "requestKey", "lease-suffix"
                                        )
                                ),
                                120,
                                "FAIL_FAST",
                                Map.of()
                        ),
                        new ExecutionDagNodeCommand(
                                "report",
                                "REPORT_HANDOFF",
                                List.of("api-smoke"),
                                Map.of("accountLease", Map.of("accountPoolRef", poolId.toString())),
                                60,
                                "CONTINUE",
                                Map.of()
                        )
                )),
                Instant.EPOCH
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.nodePolicies()).filteredOn(policy -> "api-smoke".equals(policy.key()))
                .singleElement()
                .satisfies(policy -> assertThat(policy.inputSummary().get("accountLease"))
                        .isEqualTo(Map.of(
                                "accountPoolRef", poolId.toString(),
                                "applicationId", "app-alpha",
                                "environmentId", "env-staging",
                                "roleTags", List.of("ADMIN"),
                                "ttlSeconds", 120,
                                "requestKey", "lease-suffix"
                        )));
        assertThat(result.issues()).extracting("code")
                .contains("EXECUTION_ACCOUNT_LEASE_UNSUPPORTED");
    }

    @Test
    void rejectsInvalidAccountLeaseShape() {
        UUID bundleId = UUID.randomUUID();
        ApiAutomationBundleScopeService bundleScopeService = mock(ApiAutomationBundleScopeService.class);
        when(bundleScopeService.bundleScope(bundleId))
                .thenReturn(Optional.of(new ApiAutomationBundleScope(bundleId, "project-alpha", "APPROVED")));
        ExecutionDagValidator validator = new ExecutionDagValidator(bundleScopeService, new ObjectMapper());

        ExecutionDagValidationResult result = validator.validate(
                UUID.randomUUID(),
                "project-alpha",
                new ExecutionDagCommand(List.of(new ExecutionDagNodeCommand(
                        "api-smoke",
                        "API_TEST",
                        List.of(),
                        Map.of(
                                "apiAutomationBundleId", bundleId.toString(),
                                "accountLease", Map.of(
                                        "accountPoolRef", "not-a-uuid",
                                        "ttlSeconds", 0,
                                        "roleTags", List.of("ADMIN", "BAD ROLE")
                                )
                        ),
                        120,
                        "FAIL_FAST",
                        Map.of()
                ))),
                Instant.EPOCH
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting("code")
                .contains("EXECUTION_ACCOUNT_LEASE_INVALID");
    }

    @Test
    void rejectsCyclesAndCrossProjectBundles() {
        UUID bundleId = UUID.randomUUID();
        ApiAutomationBundleScopeService bundleScopeService = mock(ApiAutomationBundleScopeService.class);
        when(bundleScopeService.bundleScope(bundleId))
                .thenReturn(Optional.of(new ApiAutomationBundleScope(bundleId, "project-other", "APPROVED")));
        ExecutionDagValidator validator = new ExecutionDagValidator(bundleScopeService, new ObjectMapper());

        ExecutionDagValidationResult result = validator.validate(
                UUID.randomUUID(),
                "project-alpha",
                new ExecutionDagCommand(List.of(
                        new ExecutionDagNodeCommand(
                                "a",
                                "API_TEST",
                                List.of("b"),
                                Map.of("apiAutomationBundleId", bundleId.toString()),
                                120,
                                "FAIL_FAST",
                                Map.of()
                        ),
                        new ExecutionDagNodeCommand("b", "REPORT_HANDOFF", List.of("a"), Map.of(), 60, "CONTINUE", Map.of())
                )),
                Instant.EPOCH
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting("code")
                .contains("EXECUTION_DAG_CYCLE", "EXECUTION_RESOURCE_SCOPE_DENIED");
    }
}
