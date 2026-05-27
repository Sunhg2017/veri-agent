package com.songhg.veri.agent.document.application.port;

import com.songhg.veri.agent.document.application.query.DocumentCandidateQuery;
import com.songhg.veri.agent.document.application.query.DocumentImportQuery;
import com.songhg.veri.agent.document.application.query.DocumentParseFeedbackQuery;
import com.songhg.veri.agent.document.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.document.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.document.domain.DocumentFieldMapping;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportPayload;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;






public interface DocumentInputRepository {

    List<DocumentSourceConfig> sources(DocumentSourceQuery query);

    long countSources(DocumentSourceQuery query);

    Optional<DocumentSourceConfig> source(UUID id);

    Optional<DocumentSourceConfig> sourceByCode(String sourceCode);

    DocumentSourceConfig saveSource(DocumentSourceConfig source);

    DocumentFieldMapping defaultFieldMapping();

    Optional<DocumentFieldMapping> fieldMapping(UUID id);

    DocumentFieldMapping saveFieldMapping(DocumentFieldMapping mapping);

    List<DocumentImportRecord> imports(DocumentImportQuery query);

    long countImports(DocumentImportQuery query);

    Optional<DocumentImportRecord> importRecord(UUID id);

    DocumentImportRecord saveImport(DocumentImportRecord record);

    boolean markImportStatus(UUID id, DocumentImportStatus expectedStatus, DocumentImportStatus nextStatus, Instant updatedAt);

    DocumentImportPayload saveImportPayload(DocumentImportPayload payload);

    Optional<DocumentImportPayload> importPayload(UUID importId);

    List<DocumentRequirementCandidate> candidates(UUID importId, int offset, int size);

    long countCandidates(UUID importId);

    List<DocumentRequirementCandidate> candidates(DocumentCandidateQuery query);

    long countCandidates(DocumentCandidateQuery query);

    Optional<DocumentRequirementCandidate> candidate(UUID id);

    DocumentRequirementCandidate saveCandidate(DocumentRequirementCandidate candidate);

    boolean markCandidateStatus(UUID id, DocumentCandidateStatus expectedStatus, DocumentCandidateStatus nextStatus, Instant updatedAt);

    Optional<DocumentRequirementCandidate> candidateByExternalId(String projectId, String externalRequirementId);

    List<DocumentParseFeedbackSample> parseFeedbackSamples(DocumentParseFeedbackQuery query);

    long countParseFeedbackSamples(DocumentParseFeedbackQuery query);

    DocumentParseFeedbackSample saveParseFeedbackSample(DocumentParseFeedbackSample sample);

    List<DocumentWebhookEvent> webhookEvents(DocumentWebhookEventQuery query);

    long countWebhookEvents(DocumentWebhookEventQuery query);

    Optional<DocumentWebhookEvent> webhookEvent(UUID id);

    Optional<DocumentWebhookEvent> webhookEventByIdentity(String sourceCode, String eventId, String idempotencyKey);

    List<DocumentWebhookEvent> retryableWebhookEvents(int maxAttempts, int limit);

    DocumentWebhookEvent saveWebhookEvent(DocumentWebhookEvent event);

    boolean markWebhookEventStatus(UUID id, WebhookEventStatus expectedStatus, WebhookEventStatus nextStatus, Instant updatedAt);

    int archiveImportsBefore(Instant before);

    int archiveCandidatesByImportCreatedBefore(Instant before);

    int archiveWebhookEventsBefore(Instant before);

    int cleanupImportsBefore(Instant before);

    int cleanupWebhookEventsBefore(Instant before);
}
