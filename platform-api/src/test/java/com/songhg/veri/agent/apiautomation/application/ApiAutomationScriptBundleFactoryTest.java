package com.songhg.veri.agent.apiautomation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiAutomationScriptBundleFactoryTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiAutomationScriptBundleFactory factory = new ApiAutomationScriptBundleFactory(objectMapper);

    @Test
    void createsReviewableBundleWithoutPersistingRuntimeSecretsOrRawSource() throws Exception {
        ApiAutomationGenerationTask task = task(UUID.randomUUID());

        ApiAutomationScriptBundle bundle = factory.createScriptBundle(
                task,
                List.of(automationCase(task, "/v1/payments", "POST", "SMOKE")),
                "bundle-author",
                Instant.EPOCH
        );

        Map<String, Object> fileTree = objectMapper.readValue(bundle.fileTreeSummaryJson(), MAP_TYPE);
        Map<String, Object> dependency = objectMapper.readValue(bundle.dependencySummaryJson(), MAP_TYPE);
        Map<String, Object> staticCheck = objectMapper.readValue(bundle.staticCheckSummaryJson(), MAP_TYPE);

        assertThat(bundle.status()).isEqualTo("DRAFT");
        assertThat(bundle.fileCount()).isEqualTo(6);
        assertThat(bundle.staticCheckStatus()).isEqualTo(ApiAutomationScriptBundleFactory.STATIC_CHECK_PASSED);
        assertThat(bundle.createdBy()).isEqualTo("bundle-author");
        assertThat(bundle.updatedBy()).isEqualTo("bundle-author");
        assertThat(fileTree)
                .containsEntry("rawSourceStored", false)
                .containsEntry("secretValuesStored", false)
                .containsEntry("pytestRunnerContractReady", true);
        assertThat(fileTree.get("runtimeInputs").toString())
                .contains("WP6_RUNNER_SECRET_HEADERS_JSON", "WP6_RUNNER_SECRET_VALUE_")
                .contains("X-VA-WP6-Secret-N")
                .doesNotContain("secret://", "resolved-secret");
        assertThat(dependency.toString())
                .contains("pytest", "httpx", "ENV_JSON_TO_CONTROLLED_HEADERS")
                .doesNotContain("secret://", "resolved-secret");
        assertThat(staticCheck)
                .containsEntry("pythonSyntax", "PASSED")
                .containsEntry("runtimeSecretHeaderMapping", "PASSED")
                .containsEntry("networkAccessDuringStaticCheck", false);
    }

    @Test
    void bundleDigestIsStableWhenInputCasesArriveUnordered() {
        UUID taskId = UUID.randomUUID();
        ApiAutomationGenerationTask task = task(taskId);
        ApiAutomationCase first = automationCase(task, "/v1/payments", "POST", "SMOKE");
        ApiAutomationCase second = automationCase(task, "/v1/customers", "GET", "FUNCTIONAL");

        ApiAutomationScriptBundle ordered = factory.createScriptBundle(
                task,
                List.of(first, second),
                "bundle-author",
                Instant.EPOCH
        );
        ApiAutomationScriptBundle reversed = factory.createScriptBundle(
                task,
                List.of(second, first),
                "bundle-author",
                Instant.EPOCH
        );

        assertThat(reversed.bundleDigest()).isEqualTo(ordered.bundleDigest());
        assertThat(reversed.fileTreeSummaryJson()).isEqualTo(ordered.fileTreeSummaryJson());
        assertThat(reversed.dependencySummaryJson()).isEqualTo(ordered.dependencySummaryJson());
    }

    private ApiAutomationGenerationTask task(UUID taskId) {
        return new ApiAutomationGenerationTask(
                taskId,
                "project-alpha",
                UUID.randomUUID(),
                "request-key",
                "request-digest",
                "FALLBACK_ONLY",
                "[\"SMOKE\"]",
                "COMPLETED",
                "wp6-api-automation-v1",
                "1",
                null,
                true,
                1,
                1,
                "{}",
                null,
                "tester",
                "tester",
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private ApiAutomationCase automationCase(
            ApiAutomationGenerationTask task,
            String path,
            String method,
            String coverageType
    ) {
        return new ApiAutomationCase(
                UUID.randomUUID(),
                task.id(),
                task.projectId(),
                task.specId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                method + " " + path + " contract",
                method,
                path,
                coverageType,
                200,
                "{}",
                "{}",
                "FALLBACK",
                "DRAFT",
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
