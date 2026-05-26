package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import com.songhg.veri.agent.document.infrastructure.InMemoryDocumentInputRepository;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentInputRetentionCleanupServiceTest {

    @Test
    void cleansImportsCandidatesAndWebhookEventsOlderThanRetention() {
        InMemoryDocumentInputRepository repository = new InMemoryDocumentInputRepository();
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        UUID oldImportId = UUID.fromString("00000000-0000-0000-0000-000000004001");
        UUID freshImportId = UUID.fromString("00000000-0000-0000-0000-000000004002");
        UUID oldWebhookId = UUID.fromString("00000000-0000-0000-0000-000000004101");
        UUID freshWebhookId = UUID.fromString("00000000-0000-0000-0000-000000004102");

        repository.saveImport(importRecord(oldImportId, now.minusSeconds(3 * 86_400L)));
        repository.saveImport(importRecord(freshImportId, now.minusSeconds(12 * 60 * 60)));
        repository.saveCandidate(candidate(oldImportId));
        repository.saveCandidate(candidate(freshImportId));
        repository.saveWebhookEvent(webhookEvent(oldWebhookId, now.minusSeconds(3 * 86_400L)));
        repository.saveWebhookEvent(webhookEvent(freshWebhookId, now.minusSeconds(12 * 60 * 60)));
        CapturingContextClient contextClient = new CapturingContextClient();

        DocumentInputRetentionCleanupService cleanupService = new DocumentInputRetentionCleanupService(
                repository,
                properties(true, 1, 1),
                new SimpleMeterRegistry(),
                Clock.fixed(now, ZoneOffset.UTC),
                contextClient
        );

        DocumentInputRetentionCleanupService.CleanupResult result = cleanupService.cleanupNow();

        assertThat(result.imports()).isEqualTo(1);
        assertThat(result.webhookEvents()).isEqualTo(1);
        assertThat(result.archivedImports()).isEqualTo(1);
        assertThat(result.archivedCandidates()).isEqualTo(1);
        assertThat(result.archivedWebhookEvents()).isEqualTo(1);
        assertThat(result.importCutoff()).isEqualTo(Instant.parse("2026-05-19T00:00:00Z"));
        assertThat(result.webhookEventCutoff()).isEqualTo(Instant.parse("2026-05-19T00:00:00Z"));
        assertThat(repository.archivedRecordCount("IMPORT")).isEqualTo(1);
        assertThat(repository.archivedRecordCount("CANDIDATE")).isEqualTo(1);
        assertThat(repository.archivedRecordCount("WEBHOOK_EVENT")).isEqualTo(1);
        assertThat(repository.importRecord(oldImportId)).isEmpty();
        assertThat(repository.countCandidates(oldImportId)).isZero();
        assertThat(repository.importRecord(freshImportId)).isPresent();
        assertThat(repository.countCandidates(freshImportId)).isEqualTo(1);
        assertThat(repository.webhookEvent(oldWebhookId)).isEmpty();
        assertThat(repository.webhookEvent(freshWebhookId)).isPresent();
        assertThat(contextClient.action).isEqualTo("RETENTION_CLEANUP");
        assertThat(contextClient.resourceType).isEqualTo("DOCUMENT_INPUT_RETENTION");
        assertThat(contextClient.result).isEqualTo("SUCCEEDED");
        assertThat(contextClient.afterJson).containsEntry("archivedImports", 1);
        assertThat(contextClient.afterJson).containsEntry("archivedCandidates", 1);
        assertThat(contextClient.afterJson).containsEntry("archivedWebhookEvents", 1);
    }

    @Test
    void scheduledCleanupHonorsFeatureFlag() {
        CountingRepository repository = new CountingRepository();
        DocumentInputRetentionCleanupService cleanupService = new DocumentInputRetentionCleanupService(
                repository,
                properties(false, 1, 1),
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC),
                new CapturingContextClient()
        );

        cleanupService.cleanupByRetentionPolicy();

        assertThat(repository.importCleanupCalls).isZero();
        assertThat(repository.webhookCleanupCalls).isZero();
    }

    @Test
    void cleanupRecordsFailureMetricAndAudit() {
        FailingRepository repository = new FailingRepository();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapturingContextClient contextClient = new CapturingContextClient();
        DocumentInputRetentionCleanupService cleanupService = new DocumentInputRetentionCleanupService(
                repository,
                properties(true, 1, 1),
                meterRegistry,
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC),
                contextClient
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(cleanupService::cleanupNow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup failed");

        assertThat(meterRegistry.get("veri.agent.document_input.retention.cleanup")
                .tag("target", "all")
                .tag("result", "failed")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(contextClient.result).isEqualTo("FAILED");
    }

    private DocumentImportRecord importRecord(UUID id, Instant createdAt) {
        return new DocumentImportRecord(
                id,
                "project-retention",
                null,
                null,
                DocumentSourceType.TEXT,
                "source-ref",
                null,
                "Retention import",
                DocumentImportStatus.SUCCEEDED,
                1,
                0,
                "[]",
                null,
                "digest",
                createdAt,
                createdAt
        );
    }

    private DocumentRequirementCandidate candidate(UUID importId) {
        Instant now = Instant.parse("2026-05-19T00:00:00Z");
        return new DocumentRequirementCandidate(
                UUID.randomUUID(),
                importId,
                "project-retention",
                "Retention candidate",
                "desc",
                "MEDIUM",
                "criteria",
                null,
                DocumentCandidateStatus.PENDING,
                "source-ref",
                null,
                null,
                0.8,
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now
        );
    }

    private DocumentWebhookEvent webhookEvent(UUID id, Instant receivedAt) {
        return new DocumentWebhookEvent(
                id,
                null,
                null,
                "retention-source",
                "event-" + id,
                "idem-" + id,
                "requirement.created",
                "1.0",
                WebhookSignatureStatus.VALID,
                WebhookEventStatus.PROCESSED,
                "digest",
                "{}",
                null,
                0,
                null,
                null,
                null,
                receivedAt,
                receivedAt
        );
    }

    private DocumentInputProperties properties(boolean enabled, int importRetentionDays, int webhookRetentionDays) {
        return new DocumentInputProperties(
                "service-token",
                "default-secret",
                300,
                true,
                true,
                false,
                "wp4-document-requirement-parse",
                "INTERNAL",
                false,
                8000,
                16777216,
                10485760,
                "",
                30,
                20000,
                2,
                true,
                262144,
                100,
                3,
                false,
                20,
                60,
                300,
                Map.of(),
                "",
                Map.of(),
                "",
                0,
                60,
                true,
                0,
                0,
                "LOCAL_COMMAND",
                "",
                "",
                true,
                "",
                15,
                2,
                2000,
                enabled,
                importRetentionDays,
                webhookRetentionDays
        );
    }

    private static class CountingRepository extends InMemoryDocumentInputRepository {

        private int importCleanupCalls;
        private int webhookCleanupCalls;

        @Override
        public int cleanupImportsBefore(Instant before) {
            importCleanupCalls++;
            return super.cleanupImportsBefore(before);
        }

        @Override
        public int cleanupWebhookEventsBefore(Instant before) {
            webhookCleanupCalls++;
            return super.cleanupWebhookEventsBefore(before);
        }
    }

    private static class FailingRepository extends InMemoryDocumentInputRepository {

        @Override
        public int cleanupImportsBefore(Instant before) {
            throw new IllegalStateException("cleanup failed");
        }
    }

    private static class CapturingContextClient extends DocumentInputPlatformContextClient {

        private String action;
        private String resourceType;
        private String result;
        private Map<String, Object> afterJson;

        CapturingContextClient() {
            super(new PlatformIntegrationService(Optional.empty(), new ObjectMapper()));
        }

        @Override
        public void writeAuditEvent(
                String action,
                String resourceType,
                String resourceId,
                String scopeId,
                String result,
                Map<String, Object> afterJson
        ) {
            this.action = action;
            this.resourceType = resourceType;
            this.result = result;
            this.afterJson = afterJson;
        }
    }
}
