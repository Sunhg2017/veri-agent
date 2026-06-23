package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdata.application.command.ImportTestDataRecordsCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.command.ReviewUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleExportResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UiE2eBundleServiceTest {

    @Test
    void generatesAggregateOnlyBundleAndRunsReviewWorkflow() {
        UiE2eSceneServiceTest.Fixture fixture = UiE2eSceneServiceTest.fixture(true);
        UiE2eBundleService service = service(fixture);
        UUID pageRef = UUID.randomUUID();
        UiE2eSceneServiceTest.seedWp3Refs(fixture.assetRepository(), pageRef, null, null, "project-alpha");

        var scene = fixture.service().createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "portal-admin-review",
                "后台管理员审批回归",
                "APPROVED",
                "HIGH",
                List.of("approval", "smoke"),
                Map.of("pageRefs", List.of(pageRef.toString())),
                List.of(
                        UiE2eSceneServiceTest.step("LOGIN"),
                        UiE2eSceneServiceTest.step("APPROVAL")
                )
        ));

        var generated = service.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));
        assertThat(generated.status()).isEqualTo("DRAFT");
        assertThat(generated.bundleDigest()).hasSize(64);
        assertThat(generated.staticCheckStatus()).isEqualTo("PASSED");
        assertThat(generated.specSummary()).containsEntry("aggregateOnly", true);
        assertThat(generated.fixtureSummary()).containsEntry("credentialMode", "LEASE_INJECTION_ONLY");
        assertThat(generated.policy()).containsEntry("rawScriptStored", false);
        assertThat(generated.reviews()).isEmpty();

        var submitted = service.submitReview(generated.id(), new ReviewUiE2eBundleCommand("ready for review"));
        assertThat(submitted.status()).isEqualTo("REVIEWING");
        assertThat(submitted.submittedBy()).isEqualTo("wp7-tester");
        assertThat(submitted.reviews()).singleElement()
                .extracting(item -> item.reviewStatus())
                .isEqualTo("SUBMITTED");

        var approved = service.approve(generated.id(), new ReviewUiE2eBundleCommand("approved"));
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvedBy()).isEqualTo("wp7-tester");
        assertThat(approved.reviews()).extracting(item -> item.reviewStatus())
                .containsExactly("APPROVED", "SUBMITTED");
    }

    @Test
    void bundleFixtureSummaryIncludesWp8DataBindingOverview() {
        UiE2eSceneServiceTest.Fixture fixture = UiE2eSceneServiceTest.fixture(true);
        UiE2eBundleService service = service(fixture);
        var dataSet = UiE2eSceneServiceTest.seedWp8DataSet(
                fixture,
                "project-alpha",
                "checkout-users-bundle",
                List.of(new ImportTestDataRecordsCommand.RecordItem(
                        "record-001",
                        UiE2eSceneServiceTest.sha256("record-001"),
                        Map.of("usernameMasked", "masked-user-01"),
                        UiE2eSceneServiceTest.sha256("external-record-001"),
                        List.of("SMOKE")
                ))
        );

        var scene = fixture.service().createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "portal-admin-binding-review",
                "后台管理员绑定测试数据集脚本包",
                "APPROVED",
                "HIGH",
                List.of("wp8", "bundle"),
                Map.of(),
                List.of(
                        UiE2eSceneServiceTest.step(
                                "LOGIN",
                                Map.of(
                                        "principalField", "#username",
                                        "credentialField", "#password",
                                        "submitAction", "click",
                                        "principalValue", "{{ user.usernameMasked }}"
                                ),
                                Map.of(
                                        "dataSetCode", dataSet.code(),
                                        "recordKey", "record-001",
                                        "bindingAlias", "user"
                                )
                        )
                )
        ));

        var generated = service.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));

        assertThat(generated.fixtureSummary()).containsEntry("dataBindingStepCount", 1);
        assertThat(generated.fixtureSummary()).extractingByKey("requiredFixtures")
                .asList()
                .contains("wp8DataBinding");
        assertThat(generated.fixtureSummary()).extractingByKey("dataBindings")
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("bindingAlias", "user")
                .containsEntry("dataSetCode", dataSet.code())
                .containsEntry("recordKey", "record-001");
        assertThat(generated.specSummary()).containsEntry("dataBindingStepCount", 1);
    }

    @Test
    void blocksBundleSubmissionWhenStaticCheckFails() {
        UiE2eSceneServiceTest.Fixture fixture = UiE2eSceneServiceTest.fixture(true);
        UiE2eBundleService service = service(fixture);

        var scene = fixture.service().createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                null,
                null,
                "portal-admin-secret",
                "后台管理员危险步骤",
                "DRAFT",
                "MEDIUM",
                List.of("security"),
                Map.of(),
                List.of(new CreateUiE2eSceneCommand.SceneStepPayload(
                        "LOGIN",
                        Map.of("token", "Bearer real-token-value"),
                        Map.of("preferred", "testId"),
                        Map.of("expected", "ok"),
                        Map.of("timeoutSeconds", 5)
                ))
        ));

        var generated = service.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));
        assertThat(generated.status()).isEqualTo("STATIC_CHECK_FAILED");
        assertThat(generated.staticCheckStatus()).isEqualTo("SCRIPT_STATIC_CHECK_FAILED");
        assertThat(generated.staticCheckSummary()).extractingByKey("findingCount").isEqualTo(1);

        assertThatThrownBy(() -> service.submitReview(generated.id(), new ReviewUiE2eBundleCommand("try submit")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("SCRIPT_STATIC_CHECK_FAILED");
                });
    }

    @Test
    void requiresRejectionNote() {
        UiE2eSceneServiceTest.Fixture fixture = UiE2eSceneServiceTest.fixture(true);
        UiE2eBundleService service = service(fixture);

        var scene = fixture.service().createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                null,
                null,
                "portal-admin-reject",
                "后台管理员驳回",
                "DRAFT",
                "MEDIUM",
                List.of("review"),
                Map.of(),
                List.of(UiE2eSceneServiceTest.step("LOGIN"))
        ));

        var generated = service.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));
        service.submitReview(generated.id(), new ReviewUiE2eBundleCommand("ready"));

        assertThatThrownBy(() -> service.reject(generated.id(), new ReviewUiE2eBundleCommand(" ")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void exportsAggregateOnlyBundleSummaryWithoutReviewerFreeText() {
        UiE2eSceneServiceTest.Fixture fixture = UiE2eSceneServiceTest.fixture(true);
        UiE2eBundleService service = service(fixture);

        var scene = fixture.service().createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "portal-admin-export",
                "后台管理员导出摘要",
                "APPROVED",
                "HIGH",
                List.of("export", "smoke"),
                Map.of(),
                List.of(UiE2eSceneServiceTest.step("LOGIN"))
        ));

        var generated = service.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));
        service.submitReview(generated.id(), new ReviewUiE2eBundleCommand("ready cookie=secret-value"));
        service.approve(generated.id(), new ReviewUiE2eBundleCommand("approved by lead"));

        UiE2eBundleExportResponse exported = service.exportBundle(generated.id());

        assertThat(exported.schemaVersion()).isEqualTo("wp7-bundle-export-v1");
        assertThat(exported.bundle().id()).isEqualTo(generated.id());
        assertThat(exported.bundle().policy())
                .containsEntry("aggregateOnly", true)
                .containsEntry("reviewCommentExported", false)
                .containsEntry("reviewerIdentityExported", false);
        assertThat(exported.reviewSummary().reviewCount()).isEqualTo(2);
        assertThat(exported.reviewSummary().noteCount()).isEqualTo(2);
        assertThat(exported.reviewSummary().reviewStatuses()).containsExactly("APPROVED", "SUBMITTED");
        assertThat(exported.reviewSummary().latestReview())
                .containsEntry("reviewStatus", "APPROVED")
                .containsEntry("commentPresent", true);
        assertThat(exported.redactionPolicy())
                .containsEntry("aggregateOnly", true)
                .containsEntry("reviewCommentExported", false)
                .containsEntry("secretPlaintextExported", false);
        assertThat(exported.toString())
                .doesNotContain("approved by lead")
                .doesNotContain("ready cookie=secret-value")
                .doesNotContain("cookie=");
    }

    @Test
    void archivesBundleThroughDedicatedTransition() {
        UiE2eSceneServiceTest.Fixture fixture = UiE2eSceneServiceTest.fixture(true);
        UiE2eBundleService service = service(fixture);

        var scene = fixture.service().createScene(new CreateUiE2eSceneCommand(
                "project-alpha",
                null,
                null,
                "portal-admin-archive-bundle",
                "后台管理员归档脚本包",
                "APPROVED",
                "MEDIUM",
                List.of("archive"),
                Map.of(),
                List.of(UiE2eSceneServiceTest.step("LOGIN"))
        ));

        var generated = service.createOrRefreshBundle(new CreateUiE2eBundleCommand(scene.id()));
        var approved = service.approve(
                service.submitReview(generated.id(), new ReviewUiE2eBundleCommand("ready")).id(),
                new ReviewUiE2eBundleCommand("approved")
        );

        var archived = service.archiveBundle(approved.id());

        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(archived.archivedAt()).isNotNull();
        assertThat(archived.policy()).containsEntry("archivable", false);
        assertThat(service.archiveBundle(approved.id()).status()).isEqualTo("ARCHIVED");
    }

    private UiE2eBundleService service(UiE2eSceneServiceTest.Fixture fixture) {
        return new UiE2eBundleService(
                fixture.repository(),
                fixture.actorResolver(),
                fixture.contextClient(),
                fixture.properties(),
                new UiE2eBundleFactory(fixture.objectMapper()),
                fixture.objectMapper()
        );
    }
}
