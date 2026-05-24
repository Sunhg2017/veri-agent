package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetVersionHistoryServiceTest {

    private static final UUID REQUIREMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final String PROJECT_ID = "project-history";

    private InMemoryAssetRepository repository;
    private AssetVersionHistoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        service = new AssetVersionHistoryService(repository, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsRequirementDiffAndSnapshot() {
        AssetRequirement before = requirement(1, "登录需求", "DRAFT", "alpha,beta", null);
        AssetRequirement after = requirement(2, "登录需求V2", "REVIEWING", "alpha，gamma", null);

        service.recordRequirementChange(before, after, "UPDATE");

        AssetVersionHistoryResponse response = service.responses("REQUIREMENT", REQUIREMENT_ID).getFirst();
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.changeType()).isEqualTo("UPDATE");
        assertThat(response.actor()).isEqualTo("system");
        assertThat(response.changedFields()).containsExactly("title", "status", "tags");
        assertThat(response.diff().path("title").path("before").asText()).isEqualTo("登录需求");
        assertThat(response.diff().path("title").path("after").asText()).isEqualTo("登录需求V2");
        assertThat(response.diff().path("tags").path("after").asText()).isEqualTo("alpha,gamma");
        assertThat(response.snapshot().path("title").asText()).isEqualTo("登录需求V2");
        assertThat(response.snapshot().path("lifecycleStatus").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void recordsTestCaseStepsInSnapshotOrder() {
        TestCaseRecord before = testCase(1, List.of(
                new TestCaseStep(UUID.randomUUID(), CASE_ID, 1, "后置操作", "后置预期"),
                new TestCaseStep(UUID.randomUUID(), CASE_ID, 0, "前置操作", "前置预期")
        ));
        TestCaseRecord after = testCase(2, List.of(
                new TestCaseStep(UUID.randomUUID(), CASE_ID, 0, "前置操作", "前置预期"),
                new TestCaseStep(UUID.randomUUID(), CASE_ID, 1, "执行操作", "执行预期")
        ));

        service.recordTestCaseChange(before, after, "STEPS_UPDATE");

        AssetVersionHistoryResponse response = service.responses("TEST_CASE", CASE_ID).getFirst();
        assertThat(response.changedFields()).containsExactly("steps");
        assertThat(response.snapshot().path("steps").get(0).path("order").asInt()).isZero();
        assertThat(response.snapshot().path("steps").get(0).path("action").asText()).isEqualTo("前置操作");
        assertThat(response.snapshot().path("steps").get(1).path("action").asText()).isEqualTo("执行操作");
    }

    @Test
    void findsExactHistoryVersionOrThrows() {
        AssetRequirement created = requirement(1, "登录需求", "DRAFT", null, null);
        service.recordRequirementCreated(created);

        assertThat(service.historyOrThrow("REQUIREMENT", REQUIREMENT_ID, 1).version()).isEqualTo(1);

        assertThatThrownBy(() -> service.historyOrThrow("REQUIREMENT", REQUIREMENT_ID, 2))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage())
                            .contains("资产版本不存在: REQUIREMENT/" + REQUIREMENT_ID + "/v2");
                });
    }

    @Test
    void recordsUserActorFromAuthorizationService() {
        service = new AssetVersionHistoryService(
                repository,
                new ObjectMapper().findAndRegisterModules(),
                authorizationService()
        );
        authenticate(new AuthUserPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "history-user",
                "History User",
                "history@example.com",
                false,
                1,
                List.of("asset-owner")
        ));

        service.recordRequirementCreated(requirement(1, "登录需求", "DRAFT", null, null));

        AssetVersionHistoryResponse response = service.responses("REQUIREMENT", REQUIREMENT_ID).getFirst();
        assertThat(response.actor()).isEqualTo("history-user");
    }

    @Test
    void recordsServiceActorFromAuthorizationService() {
        service = new AssetVersionHistoryService(
                repository,
                new ObjectMapper().findAndRegisterModules(),
                authorizationService()
        );
        authenticate(new ServicePrincipal("wp4-document-input", "user-001"));

        service.recordRequirementCreated(requirement(1, "登录需求", "DRAFT", null, null));

        AssetVersionHistoryResponse response = service.responses("REQUIREMENT", REQUIREMENT_ID).getFirst();
        assertThat(response.actor()).isEqualTo("wp4-document-input:user-001");
    }

    private static AuthorizationService authorizationService() {
        return new AuthorizationService(roles -> Set.of(), record -> {
        });
    }

    private static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null)
        );
    }

    private static AssetRequirement requirement(
            int version,
            String title,
            String status,
            String tags,
            Instant deletedAt
    ) {
        return new AssetRequirement(
                REQUIREMENT_ID,
                "REQ-1",
                title,
                "Description",
                "MANUAL",
                null,
                null,
                null,
                status,
                "MEDIUM",
                PROJECT_ID,
                tags,
                version,
                "ACTIVE",
                null,
                deletedAt,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static TestCaseRecord testCase(int version, List<TestCaseStep> steps) {
        return new TestCaseRecord(
                CASE_ID,
                "TC-1",
                "登录用例",
                "Description",
                PROJECT_ID,
                REQUIREMENT_ID,
                null,
                "MANUAL",
                null,
                "DRAFT",
                "MEDIUM",
                null,
                steps,
                version,
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
