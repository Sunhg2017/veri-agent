package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.command.AssetReportEvidenceQuery;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetCrossWpReportEvidenceServiceTest {

    private static final String PROJECT_ID = "project-alpha";
    private static final String RAW_REQUIREMENT_BODY = "Requirement raw body should not leak";
    private static final String RAW_CASE_BODY = "Test case raw body should not leak";

    @Test
    void reportEvidenceReturnsOnlyAggregateAssetSignals() {
        InMemoryAssetRepository repository = new InMemoryAssetRepository();
        AssetCrossWpReportEvidenceService service = new AssetCrossWpReportEvidenceService(
                repository,
                new AssetProjectAuditService(new StaticPlatformContextClient())
        );
        UUID requirementId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID otherRequirementId = UUID.randomUUID();
        repository.saveRequirement(requirement(requirementId, PROJECT_ID));
        repository.saveRequirement(requirement(otherRequirementId, "project-beta"));
        repository.saveTestCase(testCase(caseId, requirementId, PROJECT_ID));
        repository.saveTraceLink(new TraceLink(UUID.randomUUID(), requirementId, null, null, null, caseId, Instant.EPOCH));
        repository.saveTraceLink(new TraceLink(UUID.randomUUID(), otherRequirementId, null, null, null, caseId, Instant.EPOCH));

        var evidence = service.reportEvidence(new AssetReportEvidenceQuery(
                PROJECT_ID,
                "report-alpha",
                List.of(requirementId),
                List.of(),
                List.of(),
                List.of(),
                List.of(caseId)
        ));

        assertThat(evidence.requirements()).singleElement()
                .satisfies(item -> {
                    assertThat(item.requirementRef()).isEqualTo(requirementId);
                    assertThat(item.status()).isEqualTo("APPROVED");
                    assertThat(item.traceLinkCount()).isEqualTo(1);
                    assertThat(item.linkedCaseCount()).isEqualTo(1);
                    assertThat(item.tagCount()).isEqualTo(2);
                });
        assertThat(evidence.testCases()).singleElement()
                .satisfies(item -> {
                    assertThat(item.testCaseRef()).isEqualTo(caseId);
                    assertThat(item.requirementRef()).isEqualTo(requirementId);
                    assertThat(item.stepCount()).isZero();
                    assertThat(item.traceLinkCount()).isEqualTo(1);
                });
        assertThat(evidence.redactionPolicy())
                .containsEntry("aggregateOnly", true)
                .containsEntry("assetBodyReturned", false)
                .containsEntry("traceIdentifierListReturned", false)
                .containsEntry("crossWpTableAccessAllowed", false);
        assertThat(evidence.toString()).doesNotContain(RAW_REQUIREMENT_BODY, RAW_CASE_BODY);
    }

    @Test
    void rejectsReportEvidenceFromAnotherProject() {
        InMemoryAssetRepository repository = new InMemoryAssetRepository();
        AssetCrossWpReportEvidenceService service = new AssetCrossWpReportEvidenceService(
                repository,
                new AssetProjectAuditService(new StaticPlatformContextClient())
        );
        UUID requirementId = UUID.randomUUID();
        repository.saveRequirement(requirement(requirementId, PROJECT_ID));

        assertThatThrownBy(() -> service.reportEvidence(new AssetReportEvidenceQuery(
                "project-beta",
                "report-beta",
                List.of(requirementId),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private AssetRequirement requirement(UUID id, String projectId) {
        return new AssetRequirement(
                id,
                "REQ-WP10",
                "Checkout requirement",
                RAW_REQUIREMENT_BODY,
                "MANUAL",
                null,
                null,
                "Acceptance should not leak",
                "APPROVED",
                "P1",
                projectId,
                "smoke,regression",
                3,
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private TestCaseRecord testCase(UUID id, UUID requirementId, String projectId) {
        return new TestCaseRecord(
                id,
                "TC-WP10",
                "Checkout smoke",
                RAW_CASE_BODY,
                projectId,
                requirementId,
                null,
                "MANUAL",
                null,
                "APPROVED",
                "P1",
                "smoke",
                List.of(),
                2,
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static class StaticPlatformContextClient implements PlatformContextClient {

        @Override
        public ProjectContext getProjectContext(String projectId) {
            return new ProjectContext(projectId, "ACTIVE", "INTERNAL", false);
        }

        @Override
        public void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
        }
    }
}
