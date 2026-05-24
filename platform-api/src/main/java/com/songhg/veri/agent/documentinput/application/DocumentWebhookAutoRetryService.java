package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.documentinput.application.port.DocumentInputRepository;
import com.songhg.veri.agent.documentinput.application.view.DocumentInputMetrics;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;



@Service
public class DocumentWebhookAutoRetryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentWebhookAutoRetryService.class);

    private final DocumentInputRepository repository;
    private final DocumentInputService documentInputService;
    private final DocumentInputProperties properties;
    private final DocumentInputMetrics metrics;

    public DocumentWebhookAutoRetryService(
            DocumentInputRepository repository,
            DocumentInputService documentInputService,
            DocumentInputProperties properties,
            DocumentInputMetrics metrics
    ) {
        this.repository = repository;
        this.documentInputService = documentInputService;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${veri-agent.document-input.webhook-auto-retry-cron:0 */5 * * * *}")
    public void retryBySchedule() {
        if (!properties.inputEnabled() || !properties.webhookEnabled() || !properties.webhookAutoRetryEnabled()) {
            return;
        }
        retryNow();
    }

    public AutoRetryResult retryNow() {
        int maxAttempts = maxReplayAttempts();
        List<DocumentWebhookEvent> events = repository.retryableWebhookEvents(maxAttempts, autoRetryBatchSize());
        int succeeded = 0;
        int failed = 0;
        for (DocumentWebhookEvent event : events) {
            String traceId = TraceContext.createTraceId();
            TraceContext.setTraceId(traceId);
            try {
                documentInputService.replayWebhookEvent(event.id());
                succeeded++;
                metrics.recordWebhookAutoRetry("SUCCEEDED");
            } catch (RuntimeException exception) {
                failed++;
                metrics.recordWebhookAutoRetry("FAILED");
                log.warn(
                        "WP4 webhook auto retry failed, eventId={}, traceId={}, error={}",
                        event.id(),
                        traceId,
                        exception.getMessage()
                );
                log.debug("WP4 webhook auto retry failure details", exception);
            } finally {
                TraceContext.clear();
            }
        }
        return new AutoRetryResult(events.size(), succeeded, failed);
    }

    private int maxReplayAttempts() {
        return properties.webhookMaxReplayAttempts() <= 0 ? 3 : properties.webhookMaxReplayAttempts();
    }

    private int autoRetryBatchSize() {
        return properties.webhookAutoRetryBatchSize() <= 0 ? 20 : properties.webhookAutoRetryBatchSize();
    }

    public record AutoRetryResult(int attempted, int succeeded, int failed) {
    }
}
