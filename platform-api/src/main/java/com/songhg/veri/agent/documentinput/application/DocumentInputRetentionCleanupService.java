package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DocumentInputRetentionCleanupService {

    private final DocumentInputRepository repository;
    private final DocumentInputProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public DocumentInputRetentionCleanupService(
            DocumentInputRepository repository,
            DocumentInputProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(repository, properties, meterRegistry, Clock.systemUTC());
    }

    DocumentInputRetentionCleanupService(
            DocumentInputRepository repository,
            DocumentInputProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
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
        int imports = repository.cleanupImportsBefore(now.minusSeconds(retentionDays(properties.importRetentionDays()) * 86_400L));
        int webhookEvents = repository.cleanupWebhookEventsBefore(
                now.minusSeconds(retentionDays(properties.webhookEventRetentionDays()) * 86_400L)
        );
        recordCleanup("import", imports);
        recordCleanup("webhook_event", webhookEvents);
        return new CleanupResult(imports, webhookEvents);
    }

    private int retentionDays(int configured) {
        return Math.max(1, configured);
    }

    private void recordCleanup(String target, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("veri.agent.document_input.retention.cleanup")
                .description("WP4 records cleaned by retention policy")
                .tag("target", target)
                .register(meterRegistry)
                .increment(count);
    }

    public record CleanupResult(int imports, int webhookEvents) {
    }
}
