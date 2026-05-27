package com.songhg.veri.agent.document.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.query.DocumentImportQuery;
import com.songhg.veri.agent.document.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DocumentInputEventRecoveryService {

    private static final int MAX_RECOVERY_BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(DocumentInputEventRecoveryService.class);

    private final DocumentInputRepository repository;
    private final DocumentInputEventPublisher eventPublisher;
    private final boolean enabled;
    private final int batchSize;

    public DocumentInputEventRecoveryService(
            DocumentInputRepository repository,
            DocumentInputEventPublisher eventPublisher,
            @Value("${veri-agent.document-input.event-recovery-enabled:true}") boolean enabled,
            @Value("${veri-agent.document-input.event-recovery-batch-size:100}") int batchSize
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(MAX_RECOVERY_BATCH_SIZE, batchSize));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverSafely("startup");
    }

    @Scheduled(cron = "${veri-agent.document-input.event-recovery-cron:0 */2 * * * *}")
    public void recoverBySchedule() {
        recoverSafely("schedule");
    }

    /**
     * Re-emits persisted queue states without mutating them. Consumers keep the real ownership
     * boundary by conditionally claiming the record before parsing, publishing, or webhook handling.
     */
    public RecoveryResult recoverQueuedEvents(String trigger) {
        if (!enabled) {
            return new RecoveryResult(trigger, 0, 0, 0);
        }
        int imports = recoverImports(DocumentImportStatus.MODEL_PARSE_QUEUED);
        int publishes = recoverImports(DocumentImportStatus.PUBLISH_QUEUED);
        int webhooks = recoverWebhookEvents();
        int total = imports + publishes + webhooks;
        if (total > 0) {
            log.info(
                    "WP4 document input event recovery published queued records, trigger={}, imports={}, publishes={}, webhooks={}",
                    trigger,
                    imports,
                    publishes,
                    webhooks
            );
        }
        return new RecoveryResult(trigger, imports, publishes, webhooks);
    }

    private void recoverSafely(String trigger) {
        try {
            recoverQueuedEvents(trigger);
        } catch (RuntimeException exception) {
            log.warn("WP4 document input event recovery skipped, trigger={}, message={}", trigger, exception.getMessage());
        }
    }

    private int recoverImports(DocumentImportStatus status) {
        List<DocumentImportRecord> records = repository.imports(new DocumentImportQuery(
                null,
                null,
                null,
                status,
                PageQuery.of(0, batchSize)
        ));
        records.forEach(record -> {
            if (status == DocumentImportStatus.MODEL_PARSE_QUEUED) {
                eventPublisher.publishImportRequested(record.id());
            } else {
                // Empty candidate ids make the publish consumer re-select every queued candidate for this import.
                eventPublisher.publishDocumentPublishRequested(record.id(), List.of());
            }
        });
        return records.size();
    }

    private int recoverWebhookEvents() {
        List<DocumentWebhookEvent> events = repository.webhookEvents(new DocumentWebhookEventQuery(
                null,
                null,
                null,
                WebhookEventStatus.ACCEPTED,
                null,
                null,
                PageQuery.of(0, batchSize)
        ));
        events.forEach(event -> eventPublisher.publishWebhookAccepted(event.id()));
        return events.size();
    }

    public record RecoveryResult(String trigger, int imports, int publishes, int webhooks) {
    }
}
