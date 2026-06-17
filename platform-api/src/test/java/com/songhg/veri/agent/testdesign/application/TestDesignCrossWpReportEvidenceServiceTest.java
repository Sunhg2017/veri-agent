package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdesign.application.command.TestDesignReportEvidenceQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.infrastructure.InMemoryTestDesignRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDesignCrossWpReportEvidenceServiceTest {

    private static final String PROJECT_ID = "project-alpha";
    private static final String RAW_CANDIDATE_BODY = "Candidate generated step body should not leak";
    private static final String RAW_PROMPT = "raw prompt should not leak";

    @Test
    void reportEvidenceReturnsOnlyAggregateTaskAndCandidateSignals() {
        InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
        TestDesignCrossWpReportEvidenceService service = new TestDesignCrossWpReportEvidenceService(
                repository,
                contextClient(),
                new ObjectMapper()
        );
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        repository.saveTask(task(taskId, requirementId, PROJECT_ID));
        repository.saveCandidate(candidate(candidateId, taskId, requirementId, PROJECT_ID));
        repository.saveReportManifest(manifest(taskId, PROJECT_ID));

        var evidence = service.reportEvidence(new TestDesignReportEvidenceQuery(
                PROJECT_ID,
                "report-alpha",
                List.of(taskId),
                List.of(candidateId)
        ));

        assertThat(evidence.tasks()).singleElement()
                .satisfies(item -> {
                    assertThat(item.taskRef()).isEqualTo(taskId);
                    assertThat(item.status()).isEqualTo("COMPLETED");
                    assertThat(item.requirementRefCount()).isEqualTo(1);
                    assertThat(item.coverageTypeCount()).isEqualTo(2);
                    assertThat(item.candidateCount()).isEqualTo(1);
                    assertThat(item.reportManifestCount()).isEqualTo(1);
                    assertThat(item.aggregateReportManifestCount()).isEqualTo(1);
                    assertThat(item.latestReportManifestContentDigest()).matches("[0-9a-f]{64}");
                });
        assertThat(evidence.candidates()).singleElement()
                .satisfies(item -> {
                    assertThat(item.candidateRef()).isEqualTo(candidateId);
                    assertThat(item.taskRef()).isEqualTo(taskId);
                    assertThat(item.requirementRef()).isEqualTo(requirementId);
                    assertThat(item.status()).isEqualTo("PUBLISHED");
                    assertThat(item.confirmed()).isTrue();
                });
        assertThat(evidence.redactionPolicy())
                .containsEntry("aggregateOnly", true)
                .containsEntry("candidateBodyReturned", false)
                .containsEntry("promptReturned", false)
                .containsEntry("modelPayloadReturned", false);
        assertThat(evidence.toString()).doesNotContain(RAW_CANDIDATE_BODY, RAW_PROMPT, "raw response");
    }

    @Test
    void rejectsReportEvidenceFromAnotherProject() {
        InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
        TestDesignCrossWpReportEvidenceService service = new TestDesignCrossWpReportEvidenceService(
                repository,
                contextClient(),
                new ObjectMapper()
        );
        UUID taskId = UUID.randomUUID();
        repository.saveTask(task(taskId, UUID.randomUUID(), PROJECT_ID));

        assertThatThrownBy(() -> service.reportEvidence(new TestDesignReportEvidenceQuery(
                "project-beta",
                "report-beta",
                List.of(taskId),
                List.of()
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private TestDesignTask task(UUID id, UUID requirementId, String projectId) {
        return new TestDesignTask(
                id,
                projectId,
                "Checkout generation",
                "COMPLETED",
                requirementId.toString(),
                "API_REGRESSION,SMOKE",
                "prompt-main",
                "v1",
                UUID.randomUUID(),
                "provider-secret",
                "model-secret",
                1,
                1,
                1,
                1,
                null,
                "tester",
                "request-key",
                "a".repeat(64),
                "b".repeat(64),
                "{\"rawPrompt\":\"" + RAW_PROMPT + "\",\"safeKey\":true}",
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private TestDesignCandidate candidate(UUID id, UUID taskId, UUID requirementId, String projectId) {
        return new TestDesignCandidate(
                id,
                taskId,
                projectId,
                requirementId,
                null,
                "Checkout smoke",
                RAW_CANDIDATE_BODY,
                "API_REGRESSION",
                "P1",
                "PUBLISHED",
                "Preconditions should not leak",
                "[{\"action\":\"secret step\"}]",
                "Expected should not leak",
                "smoke",
                "dup-key",
                0.91,
                "prompt-main",
                "v1",
                UUID.randomUUID(),
                "provider-secret",
                "model-secret",
                UUID.randomUUID(),
                "review should not leak",
                null,
                null,
                null,
                "reviewer",
                Instant.EPOCH,
                2,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private TestDesignReportManifest manifest(UUID taskId, String projectId) {
        return new TestDesignReportManifest(
                UUID.randomUUID(),
                taskId,
                projectId,
                "wp5-task-report-v1",
                "aggregate-only-v1",
                "AGGREGATE_RECONCILIATION",
                10,
                16,
                true,
                false,
                "COMPLETE",
                "c".repeat(64),
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private TestDesignPlatformContextClient contextClient() {
        TestDesignPlatformContextClient contextClient = mock(TestDesignPlatformContextClient.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(platformContext("project-alpha"));
        when(contextClient.projectContext("project-beta")).thenReturn(platformContext("project-beta"));
        return contextClient;
    }

    private PlatformContext platformContext(String projectId) {
        return new PlatformContext(
                "PROJECT",
                projectId,
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.EPOCH
        );
    }
}
