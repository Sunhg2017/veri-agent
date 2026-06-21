package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.uie2e.application.command.BatchCreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiE2eBatchRunServiceTest {

    private static final String PROJECT_ID = "project-alpha";

    @Test
    void createsBatchRunsWithDeduplicatedSceneOrderAndPartialOutcomes() {
        UiE2eRepository repository = mock(UiE2eRepository.class);
        UiE2eRunService runService = mock(UiE2eRunService.class);
        UiE2eBatchRunService service = new UiE2eBatchRunService(repository, runService, properties(5));
        UUID sceneIdOne = UUID.randomUUID();
        UUID sceneIdTwo = UUID.randomUUID();
        UUID missingSceneId = UUID.randomUUID();
        UUID bundleIdOne = UUID.randomUUID();
        UUID bundleIdTwo = UUID.randomUUID();

        when(repository.scene(sceneIdOne)).thenReturn(Optional.of(scene(sceneIdOne, "portal-login")));
        when(repository.scene(sceneIdTwo)).thenReturn(Optional.of(scene(sceneIdTwo, "portal-dashboard")));
        when(repository.scene(missingSceneId)).thenReturn(Optional.empty());
        when(repository.sceneBundles(sceneIdOne)).thenReturn(List.of(bundle(bundleIdOne, sceneIdOne)));
        when(repository.sceneBundles(sceneIdTwo)).thenReturn(List.of(bundle(bundleIdTwo, sceneIdTwo)));
        when(runService.createRun(any()))
                .thenReturn(runDetail(sceneIdOne, bundleIdOne, false))
                .thenReturn(runDetail(sceneIdTwo, bundleIdTwo, true));

        var response = service.createRuns(new BatchCreateUiE2eRunCommand(
                PROJECT_ID,
                List.of(sceneIdOne, sceneIdOne, sceneIdTwo, missingSceneId),
                "staging",
                "env:staging",
                UUID.randomUUID(),
                "batch-request",
                "nightly smoke",
                List.of("CHROMIUM", "FIREFOX"),
                true,
                null,
                0.02D
        ));

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.requestedCount()).isEqualTo(3);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.replayedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.items()).extracting(item -> item.sceneId()).containsExactly(sceneIdOne, sceneIdTwo, missingSceneId);
        assertThat(response.items()).extracting(item -> item.outcome()).containsExactly("CREATED", "REPLAYED", "FAILED");
        assertThat(response.items().get(2).errorCode()).isEqualTo("UI_E2E_SCENE_NOT_FOUND");
        verify(runService, times(2)).createRun(any());
    }

    @Test
    void mapsBusinessExceptionToStableBatchFailureCode() {
        UiE2eRepository repository = mock(UiE2eRepository.class);
        UiE2eRunService runService = mock(UiE2eRunService.class);
        UiE2eBatchRunService service = new UiE2eBatchRunService(repository, runService, properties(5));
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();

        when(repository.scene(sceneId)).thenReturn(Optional.of(scene(sceneId, "portal-login")));
        when(repository.sceneBundles(sceneId)).thenReturn(List.of(bundle(bundleId, sceneId)));
        when(runService.createRun(any())).thenThrow(new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID"));

        var response = service.createRuns(new BatchCreateUiE2eRunCommand(
                PROJECT_ID,
                List.of(sceneId),
                "staging",
                "env:staging",
                UUID.randomUUID(),
                "batch-request",
                "nightly smoke",
                List.of("CHROMIUM"),
                false,
                null,
                null
        ));

        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.outcome()).isEqualTo("FAILED");
            assertThat(item.errorCode()).isEqualTo("INVALID_STATE");
            assertThat(item.errorMessage()).isEqualTo("UI_E2E_ACCOUNT_LEASE_INVALID");
        });
    }

    private UiE2eScene scene(UUID sceneId, String code) {
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        return new UiE2eScene(
                sceneId,
                PROJECT_ID,
                "app-alpha",
                "staging",
                code,
                code,
                "APPROVED",
                "HIGH",
                "{}",
                "[]",
                "tester",
                "tester",
                null,
                now,
                now
        );
    }

    private UiE2eBundle bundle(UUID bundleId, UUID sceneId) {
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        return new UiE2eBundle(
                bundleId,
                sceneId,
                PROJECT_ID,
                "APPROVED",
                "sha256:bundle",
                "{}",
                "{}",
                "{}",
                "tester",
                "tester",
                now,
                now,
                null,
                "tester",
                "tester",
                null,
                now,
                now
        );
    }

    private UiE2eRunDetailResponse runDetail(UUID sceneId, UUID bundleId, boolean replayed) {
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        return new UiE2eRunDetailResponse(
                UUID.randomUUID(),
                PROJECT_ID,
                sceneId,
                "scene-code",
                "Scene",
                "APPROVED",
                bundleId,
                "APPROVED",
                replayed ? "BLOCKED" : "QUEUED",
                "request-key",
                "MANAGED",
                null,
                null,
                "trc_batch",
                Map.of("accountLeaseRef", "lease-1"),
                Map.of("aggregateOnly", true),
                List.of(),
                List.of(),
                null,
                now,
                now,
                now,
                now,
                replayed
        );
    }

    private UiE2eProperties properties(int maxScenesPerRun) {
        return new UiE2eProperties(
                true,
                true,
                "managed",
                300,
                1800,
                maxScenesPerRun,
                20 * 1024 * 1024L,
                20,
                2,
                List.of("https://portal.example.test"),
                true,
                false,
                false,
                true,
                false,
                true,
                "node",
                "../portal-web/node_modules",
                ""
        );
    }
}
