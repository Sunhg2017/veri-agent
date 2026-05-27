package com.songhg.veri.agent.document.application;

import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import com.songhg.veri.agent.document.infrastructure.InMemoryDocumentInputRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DocumentInputEventRecoveryServiceTest {

    private final InMemoryDocumentInputRepository repository = new InMemoryDocumentInputRepository();
    private final DocumentInputEventPublisher eventPublisher = mock(DocumentInputEventPublisher.class);

    @Test
    void republishesPersistedQueuedEventsWithoutChangingState() {
        UUID parseImportId = UUID.randomUUID();
        UUID publishImportId = UUID.randomUUID();
        UUID queuedCandidateId = UUID.randomUUID();
        UUID publishedCandidateId = UUID.randomUUID();
        UUID webhookEventId = UUID.randomUUID();
        repository.saveImport(importRecord(parseImportId, DocumentImportStatus.MODEL_PARSE_QUEUED, "parse"));
        repository.saveImport(importRecord(publishImportId, DocumentImportStatus.PUBLISH_QUEUED, "publish"));
        repository.saveImport(importRecord(UUID.randomUUID(), DocumentImportStatus.SUCCEEDED, "done"));
        repository.saveCandidate(candidate(publishImportId, queuedCandidateId, DocumentCandidateStatus.PUBLISH_QUEUED));
        repository.saveCandidate(candidate(publishImportId, publishedCandidateId, DocumentCandidateStatus.PUBLISHED));
        repository.saveWebhookEvent(webhookEvent(webhookEventId, WebhookEventStatus.ACCEPTED, "evt-accepted"));
        repository.saveWebhookEvent(webhookEvent(UUID.randomUUID(), WebhookEventStatus.PROCESSED, "evt-processed"));
        DocumentInputEventRecoveryService service = new DocumentInputEventRecoveryService(
                repository,
                eventPublisher,
                true,
                20
        );

        DocumentInputEventRecoveryService.RecoveryResult result = service.recoverQueuedEvents("test");

        assertThat(result.trigger()).isEqualTo("test");
        assertThat(result.imports()).isEqualTo(1);
        assertThat(result.publishes()).isEqualTo(1);
        assertThat(result.webhooks()).isEqualTo(1);
        assertThat(repository.importRecord(parseImportId)).get().extracting(DocumentImportRecord::status)
                .isEqualTo(DocumentImportStatus.MODEL_PARSE_QUEUED);
        assertThat(repository.importRecord(publishImportId)).get().extracting(DocumentImportRecord::status)
                .isEqualTo(DocumentImportStatus.PUBLISH_QUEUED);
        assertThat(repository.webhookEvent(webhookEventId)).get().extracting(DocumentWebhookEvent::status)
                .isEqualTo(WebhookEventStatus.ACCEPTED);
        verify(eventPublisher).publishImportRequested(parseImportId);
        verify(eventPublisher).publishDocumentPublishRequested(publishImportId, List.of());
        verify(eventPublisher).publishWebhookAccepted(webhookEventId);
        verify(eventPublisher, never()).publishImportRequested(publishImportId);
    }

    @Test
    void skipsRecoveryWhenDisabled() {
        UUID importId = UUID.randomUUID();
        repository.saveImport(importRecord(importId, DocumentImportStatus.MODEL_PARSE_QUEUED, "parse"));
        DocumentInputEventRecoveryService service = new DocumentInputEventRecoveryService(
                repository,
                eventPublisher,
                false,
                20
        );

        DocumentInputEventRecoveryService.RecoveryResult result = service.recoverQueuedEvents("disabled");

        assertThat(result.imports()).isZero();
        assertThat(result.publishes()).isZero();
        assertThat(result.webhooks()).isZero();
        verifyNoInteractions(eventPublisher);
    }

    private DocumentImportRecord importRecord(UUID id, DocumentImportStatus status, String sourceRef) {
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        return new DocumentImportRecord(
                id,
                "project-wp4",
                null,
                null,
                DocumentSourceType.MARKDOWN,
                sourceRef,
                null,
                "Import " + sourceRef,
                status,
                0,
                0,
                "[]",
                null,
                "digest-" + sourceRef,
                now,
                now
        );
    }

    private DocumentRequirementCandidate candidate(UUID importId, UUID id, DocumentCandidateStatus status) {
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        return new DocumentRequirementCandidate(
                id,
                importId,
                "project-wp4",
                "Candidate " + id,
                "description",
                "HIGH",
                "acceptance",
                "tag",
                status,
                "REQ-1",
                "fragment",
                "REQ-1-" + id,
                0.9,
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

    private DocumentWebhookEvent webhookEvent(UUID id, WebhookEventStatus status, String eventId) {
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        return new DocumentWebhookEvent(
                id,
                UUID.randomUUID(),
                null,
                "custom-reqs",
                eventId,
                "idem-" + eventId,
                "requirement.created",
                "1.0",
                WebhookSignatureStatus.VALID,
                status,
                "digest-" + eventId,
                "{\"projectId\":\"project-wp4\"}",
                null,
                0,
                null,
                null,
                null,
                now,
                now
        );
    }
}
