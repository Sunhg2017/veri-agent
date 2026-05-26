package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DocumentInputMetrics {

    private final MeterRegistry meterRegistry;

    public DocumentInputMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordImport(DocumentSourceType sourceType, DocumentImportStatus status, int parsedCount) {
        Counter.builder("veri.agent.document_input.imports")
                .description("WP4 document input import batches by source type and status")
                .tag("source_type", value(sourceType))
                .tag("status", value(status))
                .register(meterRegistry)
                .increment();
        if (parsedCount > 0) {
            DistributionSummary.builder("veri.agent.document_input.import.requirements")
                    .description("WP4 parsed requirement candidates per import batch")
                    .baseUnit("requirements")
                    .tag("source_type", value(sourceType))
                    .tag("status", value(status))
                    .register(meterRegistry)
                    .record(parsedCount);
        }
    }

    public void recordCandidateAction(String action, String result) {
        Counter.builder("veri.agent.document_input.candidate.actions")
                .description("WP4 candidate confirmation and ignore actions")
                .tag("action", value(action))
                .tag("result", value(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordModelParse(String result, int candidateCount) {
        Counter.builder("veri.agent.document_input.model_parse")
                .description("WP4 AI-assisted document parsing attempts through WP2")
                .tag("result", value(result))
                .register(meterRegistry)
                .increment();
        if (candidateCount > 0) {
            DistributionSummary.builder("veri.agent.document_input.model_parse.candidates")
                    .description("WP4 AI-assisted candidate count per model parse")
                    .baseUnit("candidates")
                    .tag("result", value(result))
                    .register(meterRegistry)
                    .record(candidateCount);
        }
    }

    public void recordPublish(boolean dryRun, String result, int recordCount) {
        Counter.builder("veri.agent.document_input.publishes")
                .description("WP4 publish attempts from confirmed candidates to WP3 assets")
                .tag("dry_run", String.valueOf(dryRun))
                .tag("result", value(result))
                .register(meterRegistry)
                .increment();
        if (recordCount > 0) {
            DistributionSummary.builder("veri.agent.document_input.publish.records")
                    .description("WP4 publish record count per publish operation")
                    .baseUnit("records")
                    .tag("dry_run", String.valueOf(dryRun))
                    .tag("result", value(result))
                    .register(meterRegistry)
                    .record(recordCount);
        }
    }

    public void recordWebhook(WebhookSignatureStatus signatureStatus, WebhookEventStatus eventStatus, String eventType) {
        Counter.builder("veri.agent.document_input.webhooks")
                .description("WP4 webhook ingress events by signature and processing result")
                .tag("signature_status", value(signatureStatus))
                .tag("event_status", value(eventStatus))
                .tag("event_type", value(eventType))
                .register(meterRegistry)
                .increment();
    }

    public void recordWebhookAutoRetry(String result) {
        Counter.builder("veri.agent.document_input.webhook.auto_retry")
                .description("WP4 webhook automatic retry attempts")
                .tag("result", value(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordSecretProviderHealth(String providerType, String status) {
        Counter.builder("veri.agent.document_input.secret_provider.health")
                .description("WP4 external SecretProvider health checks by provider type and status")
                .tag("provider_type", value(providerType))
                .tag("status", value(status))
                .register(meterRegistry)
                .increment();
    }

    private String value(Object value) {
        return value == null ? "NONE" : String.valueOf(value);
    }
}
