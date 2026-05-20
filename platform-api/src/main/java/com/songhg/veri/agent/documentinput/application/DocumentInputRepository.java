package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
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

    List<DocumentRequirementCandidate> candidates(UUID importId, int offset, int size);

    long countCandidates(UUID importId);

    List<DocumentRequirementCandidate> candidates(DocumentCandidateQuery query);

    long countCandidates(DocumentCandidateQuery query);

    Optional<DocumentRequirementCandidate> candidate(UUID id);

    DocumentRequirementCandidate saveCandidate(DocumentRequirementCandidate candidate);

    Optional<DocumentRequirementCandidate> candidateByExternalId(String projectId, String externalRequirementId);

    List<DocumentWebhookEvent> webhookEvents(DocumentWebhookEventQuery query);

    long countWebhookEvents(DocumentWebhookEventQuery query);

    Optional<DocumentWebhookEvent> webhookEvent(UUID id);

    Optional<DocumentWebhookEvent> webhookEventByIdentity(String sourceCode, String eventId, String idempotencyKey);

    DocumentWebhookEvent saveWebhookEvent(DocumentWebhookEvent event);

    int cleanupImportsBefore(Instant before);

    int cleanupWebhookEventsBefore(Instant before);
}
