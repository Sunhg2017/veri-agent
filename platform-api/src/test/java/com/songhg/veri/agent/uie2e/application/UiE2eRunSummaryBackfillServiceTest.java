package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.uie2e.application.command.BackfillUiE2eRunSummaryCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.view.UiE2eArtifactManifestResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunStepResultResponse;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiE2eRunSummaryBackfillServiceTest {

    private static final String PROJECT_ID = "project-alpha";
    private static final Instant NOW = Instant.parse("2026-06-21T00:00:00Z");

    @Test
    void updatesPersistedExecutionSummaryWhenBackfillFindsDrift() throws Exception {
        UiE2eRepository repository = mock(UiE2eRepository.class);
        UiE2eRunService runService = mock(UiE2eRunService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UiE2eRunSummaryBackfillService service = new UiE2eRunSummaryBackfillService(repository, runService, objectMapper);
        UUID runId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UiE2eRun persisted = run(runId, sceneId, bundleId, "{\"stepResultCount\":0}");

        when(repository.run(runId)).thenReturn(Optional.of(persisted));
        when(runService.run(runId)).thenReturn(runDetail(runId, sceneId, bundleId, Map.of("stepResultCount", 2, "artifactManifestCount", 1)));

        var response = service.backfill(new BackfillUiE2eRunSummaryCommand(PROJECT_ID, List.of(runId), null));

        ArgumentCaptor<UiE2eRun> captor = ArgumentCaptor.forClass(UiE2eRun.class);
        verify(repository).updateRun(captor.capture());
        assertThat(objectMapper.readTree(captor.getValue().executionSummaryJson()).get("stepResultCount").asInt()).isEqualTo(2);
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.unchangedCount()).isEqualTo(0);
        assertThat(response.failedCount()).isEqualTo(0);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.updated()).isTrue();
            assertThat(item.stepResultCount()).isEqualTo(1);
            assertThat(item.artifactCount()).isEqualTo(1);
        });
    }

    @Test
    void keepsRunsUnchangedWhenSummaryAlreadyMatches() {
        UiE2eRepository repository = mock(UiE2eRepository.class);
        UiE2eRunService runService = mock(UiE2eRunService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UiE2eRunSummaryBackfillService service = new UiE2eRunSummaryBackfillService(repository, runService, objectMapper);
        UUID runId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        String summaryJson = "{\"stepResultCount\":1}";
        UiE2eRun persisted = run(runId, sceneId, bundleId, summaryJson);

        when(repository.runs(any())).thenReturn(List.of(persisted));
        when(repository.run(runId)).thenReturn(Optional.of(persisted));
        when(runService.run(runId)).thenReturn(runDetail(runId, sceneId, bundleId, Map.of("stepResultCount", 1)));

        var response = service.backfill(new BackfillUiE2eRunSummaryCommand(PROJECT_ID, null, 10));

        verify(repository, never()).updateRun(any());
        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isEqualTo(0);
        assertThat(response.unchangedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(0);
    }

    @Test
    void mapsBackfillBusinessExceptionToStableErrorCode() {
        UiE2eRepository repository = mock(UiE2eRepository.class);
        UiE2eRunService runService = mock(UiE2eRunService.class);
        UiE2eRunSummaryBackfillService service = new UiE2eRunSummaryBackfillService(repository, runService, new ObjectMapper());
        UUID runId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UiE2eRun persisted = run(runId, sceneId, bundleId, "{}");

        when(repository.run(runId)).thenReturn(Optional.of(persisted));
        when(runService.run(runId)).thenThrow(new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RUN_ARCHIVED"));

        var response = service.backfill(new BackfillUiE2eRunSummaryCommand(PROJECT_ID, List.of(runId), null));

        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.updated()).isFalse();
            assertThat(item.errorCode()).isEqualTo("INVALID_STATE");
            assertThat(item.errorMessage()).isEqualTo("UI_E2E_RUN_ARCHIVED");
        });
    }

    private UiE2eRun run(UUID runId, UUID sceneId, UUID bundleId, String executionSummaryJson) {
        return new UiE2eRun(
                runId,
                sceneId,
                bundleId,
                PROJECT_ID,
                "SUCCEEDED",
                "request-key",
                "MANAGED",
                "https://portal.example.test",
                "lease-1",
                "{\"accountLeaseRef\":\"lease-1\"}",
                executionSummaryJson,
                null,
                null,
                "trc_backfill",
                "tester",
                NOW,
                NOW,
                NOW,
                NOW
        );
    }

    private UiE2eRunDetailResponse runDetail(UUID runId, UUID sceneId, UUID bundleId, Map<String, Object> executionSummary) {
        return new UiE2eRunDetailResponse(
                runId,
                PROJECT_ID,
                sceneId,
                "scene-code",
                "Scene",
                "APPROVED",
                bundleId,
                "APPROVED",
                "SUCCEEDED",
                "request-key",
                "MANAGED",
                null,
                null,
                "trc_backfill",
                Map.of("accountLeaseRef", "lease-1"),
                executionSummary,
                List.of(new UiE2eRunStepResultResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "SUCCEEDED",
                        10,
                        null,
                        null,
                        Map.of("aggregateOnly", true),
                        NOW,
                        NOW
                )),
                List.of(new UiE2eArtifactManifestResponse(
                        UUID.randomUUID(),
                        "LOG",
                        "artifact://ui-e2e/run/log-1.log",
                        "sha256:artifact",
                        10L,
                        Map.of("rawArtifactDownloadReady", true),
                        "CAPTURED",
                        NOW,
                        NOW
                )),
                null,
                NOW,
                NOW,
                NOW,
                NOW,
                false
        );
    }
}
