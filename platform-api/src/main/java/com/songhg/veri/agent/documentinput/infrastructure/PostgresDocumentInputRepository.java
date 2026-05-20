package com.songhg.veri.agent.documentinput.infrastructure;

import com.songhg.veri.agent.documentinput.application.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.DocumentCandidateQuery;
import com.songhg.veri.agent.documentinput.application.DocumentInputRepository;
import com.songhg.veri.agent.documentinput.application.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.application.DocumentWebhookEventQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.documentinput.infrastructure.mapper.DocumentInputMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class PostgresDocumentInputRepository implements DocumentInputRepository {

    private final DocumentInputMapper mapper;

    public PostgresDocumentInputRepository(DocumentInputMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DocumentSourceConfig> sources(DocumentSourceQuery query) {
        return mapper.sources(query);
    }

    @Override
    public long countSources(DocumentSourceQuery query) {
        return mapper.countSources(query);
    }

    @Override
    public Optional<DocumentSourceConfig> source(UUID id) {
        return Optional.ofNullable(mapper.source(id));
    }

    @Override
    public Optional<DocumentSourceConfig> sourceByCode(String sourceCode) {
        return Optional.ofNullable(mapper.sourceByCode(sourceCode));
    }

    @Override
    public DocumentSourceConfig saveSource(DocumentSourceConfig source) {
        mapper.upsertSource(source);
        return source;
    }

    @Override
    public DocumentFieldMapping defaultFieldMapping() {
        return mapper.defaultFieldMapping();
    }

    @Override
    public Optional<DocumentFieldMapping> fieldMapping(UUID id) {
        return Optional.ofNullable(mapper.fieldMapping(id));
    }

    @Override
    public DocumentFieldMapping saveFieldMapping(DocumentFieldMapping mapping) {
        mapper.upsertFieldMapping(mapping);
        return mapping;
    }

    @Override
    public List<DocumentImportRecord> imports(DocumentImportQuery query) {
        return mapper.imports(query);
    }

    @Override
    public long countImports(DocumentImportQuery query) {
        return mapper.countImports(query);
    }

    @Override
    public Optional<DocumentImportRecord> importRecord(UUID id) {
        return Optional.ofNullable(mapper.importRecord(id));
    }

    @Override
    public DocumentImportRecord saveImport(DocumentImportRecord record) {
        mapper.upsertImport(record);
        return record;
    }

    @Override
    public List<DocumentRequirementCandidate> candidates(UUID importId, int offset, int size) {
        return mapper.candidates(importId, offset, size);
    }

    @Override
    public long countCandidates(UUID importId) {
        return mapper.countCandidates(importId);
    }

    @Override
    public List<DocumentRequirementCandidate> candidates(DocumentCandidateQuery query) {
        return mapper.candidatesByQuery(query);
    }

    @Override
    public long countCandidates(DocumentCandidateQuery query) {
        return mapper.countCandidatesByQuery(query);
    }

    @Override
    public Optional<DocumentRequirementCandidate> candidate(UUID id) {
        return Optional.ofNullable(mapper.candidate(id));
    }

    @Override
    public DocumentRequirementCandidate saveCandidate(DocumentRequirementCandidate candidate) {
        mapper.upsertCandidate(candidate);
        return candidate;
    }

    @Override
    public Optional<DocumentRequirementCandidate> candidateByExternalId(String projectId, String externalRequirementId) {
        return Optional.ofNullable(mapper.candidateByExternalId(projectId, externalRequirementId));
    }

    @Override
    public List<DocumentWebhookEvent> webhookEvents(DocumentWebhookEventQuery query) {
        return mapper.webhookEvents(query);
    }

    @Override
    public long countWebhookEvents(DocumentWebhookEventQuery query) {
        return mapper.countWebhookEvents(query);
    }

    @Override
    public Optional<DocumentWebhookEvent> webhookEvent(UUID id) {
        return Optional.ofNullable(mapper.webhookEvent(id));
    }

    @Override
    public Optional<DocumentWebhookEvent> webhookEventByIdentity(String sourceCode, String eventId, String idempotencyKey) {
        return Optional.ofNullable(mapper.webhookEventByIdentity(sourceCode, eventId, idempotencyKey));
    }

    @Override
    public DocumentWebhookEvent saveWebhookEvent(DocumentWebhookEvent event) {
        try {
            mapper.upsertWebhookEvent(event);
            return event;
        } catch (DuplicateKeyException exception) {
            return webhookEventByIdentity(event.sourceCode(), event.eventId(), event.idempotencyKey())
                    .orElseThrow(() -> exception);
        }
    }

    @Override
    public int cleanupImportsBefore(Instant before) {
        mapper.softDeleteCandidatesByImportCreatedBefore(before);
        return mapper.softDeleteImportsBefore(before);
    }

    @Override
    public int cleanupWebhookEventsBefore(Instant before) {
        return mapper.softDeleteWebhookEventsBefore(before);
    }
}
