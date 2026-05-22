package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DocumentInputRetentionCleanupService {

    private final DocumentInputRepository repository;
    private final DocumentInputProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final DocumentInputPlatformContextClient contextClient;

    @Autowired
    public DocumentInputRetentionCleanupService(
            DocumentInputRepository repository,
            DocumentInputProperties properties,
            MeterRegistry meterRegistry,
            DocumentInputPlatformContextClient contextClient
    ) {
        this(repository, properties, meterRegistry, Clock.systemUTC(), contextClient);
    }

    DocumentInputRetentionCleanupService(
            DocumentInputRepository repository,
            DocumentInputProperties properties,
            MeterRegistry meterRegistry,
            Clock clock,
            DocumentInputPlatformContextClient contextClient
    ) {
        this.repository = repository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.contextClient = contextClient;
    }

    @Scheduled(cron = "${veri-agent.document-input.retention-cleanup-cron:0 30 3 * * *}")
    public void cleanupByRetentionPolicy() {
        if (!properties.retentionCleanupEnabled()) {
            return;
        }
        cleanupNow();
    }

    public CleanupResult cleanupNow() {
        Instant now = Instant.now(clock);
        int importRetentionDays = retentionDays(properties.importRetentionDays());
        int webhookRetentionDays = retentionDays(properties.webhookEventRetentionDays());
        Instant importCutoff = now.minusSeconds(importRetentionDays * 86_400L);
        Instant webhookEventCutoff = now.minusSeconds(webhookRetentionDays * 86_400L);
        try {
            int archivedCandidates = repository.archiveCandidatesByImportCreatedBefore(importCutoff);
            int archivedImports = repository.archiveImportsBefore(importCutoff);
            int archivedWebhookEvents = repository.archiveWebhookEventsBefore(webhookEventCutoff);
            int imports = repository.cleanupImportsBefore(importCutoff);
            int webhookEvents = repository.cleanupWebhookEventsBefore(webhookEventCutoff);
            recordCleanup("import", "success", imports);
            recordCleanup("webhook_event", "success", webhookEvents);
            CleanupResult result = new CleanupResult(
                    importCutoff,
                    webhookEventCutoff,
                    importRetentionDays,
                    webhookRetentionDays,
                    archivedImports,
                    archivedCandidates,
                    archivedWebhookEvents,
                    imports,
                    webhookEvents
            );
            writeAudit("SUCCEEDED", result);
            return result;
        } catch (RuntimeException exception) {
            recordCleanup("all", "failed", 1);
            writeAudit("FAILED", new CleanupResult(
                    importCutoff,
                    webhookEventCutoff,
                    importRetentionDays,
                    webhookRetentionDays,
                    0,
                    0,
                    0,
                    0,
                    0
            ));
            throw exception;
        }
    }

    private int retentionDays(int configured) {
        return Math.max(1, configured);
    }

    private void recordCleanup(String target, String result, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("veri.agent.document_input.retention.cleanup")
                .description("WP4 records cleaned by retention policy")
                .tag("target", target)
                .tag("result", result)
                .register(meterRegistry)
                .increment(count);
    }

    private void writeAudit(String result, CleanupResult cleanupResult) {
        if (contextClient == null) {
            return;
        }
        contextClient.writeAuditEvent(
                "RETENTION_CLEANUP",
                "DOCUMENT_INPUT_RETENTION",
                "retention-cleanup",
                null,
                result,
                Map.of(
                        "importCutoff", cleanupResult.importCutoff().toString(),
                        "webhookEventCutoff", cleanupResult.webhookEventCutoff().toString(),
                        "importRetentionDays", cleanupResult.importRetentionDays(),
                        "webhookEventRetentionDays", cleanupResult.webhookEventRetentionDays(),
                        "archivedImports", cleanupResult.archivedImports(),
                        "archivedCandidates", cleanupResult.archivedCandidates(),
                        "archivedWebhookEvents", cleanupResult.archivedWebhookEvents(),
                        "imports", cleanupResult.imports(),
                        "webhookEvents", cleanupResult.webhookEvents()
                )
        );
    }

    public record CleanupResult(
            Instant importCutoff,
            Instant webhookEventCutoff,
            int importRetentionDays,
            int webhookEventRetentionDays,
            int archivedImports,
            int archivedCandidates,
            int archivedWebhookEvents,
            int imports,
            int webhookEvents
    ) {
    }
}
