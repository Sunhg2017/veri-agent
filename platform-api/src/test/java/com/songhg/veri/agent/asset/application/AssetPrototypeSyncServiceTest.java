package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.AssetPrototypeSyncRequest;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.view.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;



class AssetPrototypeSyncServiceTest {

    private static final String PROJECT_ID = "project-prototype";
    private static final UUID PAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");

    private InMemoryAssetRepository repository;
    private RecordingPlatformContextClient contextClient;
    private AssetPrototypeSyncService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        contextClient = new RecordingPlatformContextClient();
        service = new AssetPrototypeSyncService(
                repository,
                new AssetProjectAuditService(contextClient),
                new ObjectMapper().findAndRegisterModules(),
                new AssetVersionHistoryService(repository, new ObjectMapper().findAndRegisterModules())
        );
    }

    @Test
    void dryRunsCreateWithoutWritingPagesAndWritesBatchAudit() {
        AssetPrototypeSyncResponse response = service.syncPrototypePages(request(
                true,
                pageItem("原型首页", "/home", "figma-home", "v1", Map.of("type", "frame"), "ACTIVE")
        ));

        assertThat(response.source()).isEqualTo("FIGMA");
        assertThat(response.dryRun()).isTrue();
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.failed()).isZero();
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo("CREATE");
            assertThat(item.status()).isEqualTo("PLANNED");
            assertThat(item.code()).startsWith("PAGE-");
        });
        assertThat(repository.pages(PROJECT_ID)).isEmpty();
        assertThat(contextClient.auditEvents).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("PROTOTYPE_SYNC_DRY_RUN");
            assertThat(event.resourceType()).isEqualTo("PAGE");
            assertThat(event.scopeId()).isEqualTo(PROJECT_ID);
            assertThat(event.result()).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void updatesExistingPrototypePageAndKeepsUnchangedPageLinked() {
        repository.savePage(existingPage());

        AssetPrototypeSyncResponse linked = service.syncPrototypePages(request(
                false,
                pageItem("原型首页", "/home", "figma-home", "v1", Map.of("type", "frame"), "ACTIVE")
        ));

        assertThat(linked.skipped()).isEqualTo(1);
        assertThat(linked.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo("LINK_EXISTING");
            assertThat(item.status()).isEqualTo("SUCCEEDED");
            assertThat(item.id()).isEqualTo(PAGE_ID);
        });

        AssetPrototypeSyncResponse updated = service.syncPrototypePages(request(
                false,
                pageItem("原型首页新版", "/home", "figma-home", "v2", Map.of("type", "frame", "rev", 2), "DEPRECATED")
        ));

        assertThat(updated.updated()).isEqualTo(1);
        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo("UPDATE");
            assertThat(item.status()).isEqualTo("SUCCEEDED");
            assertThat(item.id()).isEqualTo(PAGE_ID);
        });
        AssetPage stored = repository.page(PAGE_ID).orElseThrow();
        assertThat(stored.name()).isEqualTo("原型首页新版");
        assertThat(stored.sourceVersion()).isEqualTo("v2");
        assertThat(stored.componentTree()).contains("\"rev\":2");
        assertThat(stored.status()).isEqualTo("DEPRECATED");
        assertThat(contextClient.auditEvents)
                .extracting(AuditEvent::action)
                .contains("PROTOTYPE_SYNC", "PROTOTYPE_SYNC_UPDATE");
    }

    @Test
    void reportsMissingSourceRefAsRowFailure() {
        AssetPrototypeSyncResponse response = service.syncPrototypePages(request(
                false,
                pageItem("缺少来源页面", "/missing", " ", "v1", null, "ACTIVE")
        ));

        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo("INVALID");
            assertThat(item.status()).isEqualTo("FAILED");
            assertThat(item.errors()).containsExactly("sourceRef 不能为空");
        });
        assertThat(repository.pages(PROJECT_ID)).isEmpty();
    }

    private static AssetPrototypeSyncRequest request(boolean dryRun, AssetPrototypeSyncRequest.PageItem page) {
        return new AssetPrototypeSyncRequest(
                PROJECT_ID,
                "figma",
                "connector-1",
                "batch-v1",
                dryRun,
                List.of(page)
        );
    }

    private static AssetPrototypeSyncRequest.PageItem pageItem(
            String name,
            String urlPattern,
            String sourceRef,
            String sourceVersion,
            Object componentTree,
            String status
    ) {
        return new AssetPrototypeSyncRequest.PageItem(
                name,
                urlPattern,
                sourceRef,
                sourceVersion,
                componentTree,
                null,
                status
        );
    }

    private static AssetPage existingPage() {
        return new AssetPage(
                PAGE_ID,
                "PAGE-PROTOTYPE",
                "原型首页",
                "/home",
                "FIGMA",
                "figma-home",
                "v1",
                "{\"type\":\"frame\"}",
                null,
                PROJECT_ID,
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static class RecordingPlatformContextClient implements PlatformContextClient {

        private final List<AuditEvent> auditEvents = new ArrayList<>();

        @Override
        public ProjectContext getProjectContext(String projectId) {
            return new ProjectContext(projectId, "ACTIVE", "INTERNAL", false);
        }

        @Override
        public void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
            auditEvents.add(new AuditEvent(action, resourceType, resourceId, scopeId, result));
        }
    }

    private record AuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
    }
}
