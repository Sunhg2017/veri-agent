package com.songhg.veri.agent.documentinput.infrastructure;

import com.songhg.veri.agent.documentinput.application.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.DocumentInputRepository;
import com.songhg.veri.agent.documentinput.application.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.application.DocumentWebhookEventQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!db")
public class InMemoryDocumentInputRepository implements DocumentInputRepository {

    private static final UUID DEFAULT_MAPPING_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");

    private final ConcurrentHashMap<UUID, DocumentSourceConfig> sources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentFieldMapping> mappings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentImportRecord> imports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentRequirementCandidate> candidates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentWebhookEvent> webhookEvents = new ConcurrentHashMap<>();

    public InMemoryDocumentInputRepository() {
        Instant now = Instant.now();
        mappings.put(DEFAULT_MAPPING_ID, new DocumentFieldMapping(
                DEFAULT_MAPPING_ID,
                "default",
                "Default requirement mapping",
                "requirements",
                "title",
                "description",
                "priority",
                "acceptanceCriteria",
                "tags",
                now,
                now
        ));
    }

    @Override
    public List<DocumentSourceConfig> sources(DocumentSourceQuery query) {
        return filteredSources(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countSources(DocumentSourceQuery query) {
        return filteredSources(query).count();
    }

    @Override
    public Optional<DocumentSourceConfig> source(UUID id) {
        return Optional.ofNullable(sources.get(id));
    }

    @Override
    public Optional<DocumentSourceConfig> sourceByCode(String sourceCode) {
        if (sourceCode == null) {
            return Optional.empty();
        }
        return sources.values().stream()
                .filter(source -> source.sourceCode().equalsIgnoreCase(sourceCode.trim()))
                .findFirst();
    }

    @Override
    public DocumentSourceConfig saveSource(DocumentSourceConfig source) {
        sources.put(source.id(), source);
        return source;
    }

    @Override
    public DocumentFieldMapping defaultFieldMapping() {
        return mappings.get(DEFAULT_MAPPING_ID);
    }

    @Override
    public Optional<DocumentFieldMapping> fieldMapping(UUID id) {
        return Optional.ofNullable(mappings.get(id));
    }

    @Override
    public DocumentFieldMapping saveFieldMapping(DocumentFieldMapping mapping) {
        mappings.put(mapping.id(), mapping);
        return mapping;
    }

    @Override
    public List<DocumentImportRecord> imports(DocumentImportQuery query) {
        return filteredImports(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countImports(DocumentImportQuery query) {
        return filteredImports(query).count();
    }

    @Override
    public Optional<DocumentImportRecord> importRecord(UUID id) {
        return Optional.ofNullable(imports.get(id));
    }

    @Override
    public DocumentImportRecord saveImport(DocumentImportRecord record) {
        imports.put(record.id(), record);
        return record;
    }

    @Override
    public List<DocumentRequirementCandidate> candidates(UUID importId, int offset, int size) {
        return filteredCandidates(importId)
                .skip(offset)
                .limit(size)
                .toList();
    }

    @Override
    public long countCandidates(UUID importId) {
        return filteredCandidates(importId).count();
    }

    @Override
    public Optional<DocumentRequirementCandidate> candidate(UUID id) {
        return Optional.ofNullable(candidates.get(id));
    }

    @Override
    public DocumentRequirementCandidate saveCandidate(DocumentRequirementCandidate candidate) {
        candidates.put(candidate.id(), candidate);
        return candidate;
    }

    @Override
    public Optional<DocumentRequirementCandidate> candidateByExternalId(String projectId, String externalRequirementId) {
        if (projectId == null || externalRequirementId == null) {
            return Optional.empty();
        }
        return candidates.values().stream()
                .filter(candidate -> projectId.equals(candidate.projectId()))
                .filter(candidate -> externalRequirementId.equals(candidate.externalRequirementId()))
                .findFirst();
    }

    @Override
    public List<DocumentWebhookEvent> webhookEvents(DocumentWebhookEventQuery query) {
        return filteredWebhookEvents(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countWebhookEvents(DocumentWebhookEventQuery query) {
        return filteredWebhookEvents(query).count();
    }

    @Override
    public Optional<DocumentWebhookEvent> webhookEvent(UUID id) {
        return Optional.ofNullable(webhookEvents.get(id));
    }

    @Override
    public Optional<DocumentWebhookEvent> webhookEventByIdentity(String sourceCode, String eventId, String idempotencyKey) {
        return webhookEvents.values().stream()
                .filter(event -> sourceCode == null || sourceCode.equalsIgnoreCase(event.sourceCode()))
                .filter(event -> eventId != null && eventId.equals(event.eventId())
                        || idempotencyKey != null && idempotencyKey.equals(event.idempotencyKey()))
                .findFirst();
    }

    @Override
    public DocumentWebhookEvent saveWebhookEvent(DocumentWebhookEvent event) {
        webhookEvents.put(event.id(), event);
        return event;
    }

    private java.util.stream.Stream<DocumentSourceConfig> filteredSources(DocumentSourceQuery query) {
        return sources.values().stream()
                .filter(source -> query.sourceType() == null || source.sourceType() == query.sourceType())
                .filter(source -> query.status() == null || source.status() == query.status())
                .sorted(Comparator.comparing(DocumentSourceConfig::createdAt).reversed());
    }

    private java.util.stream.Stream<DocumentImportRecord> filteredImports(DocumentImportQuery query) {
        return imports.values().stream()
                .filter(record -> query.projectId() == null || query.projectId().equals(record.projectId()))
                .filter(record -> query.sourceId() == null || query.sourceId().equals(record.sourceId()))
                .filter(record -> query.sourceType() == null || record.sourceType() == query.sourceType())
                .filter(record -> query.status() == null || record.status() == query.status())
                .sorted(Comparator.comparing(DocumentImportRecord::createdAt).reversed());
    }

    private java.util.stream.Stream<DocumentRequirementCandidate> filteredCandidates(UUID importId) {
        return candidates.values().stream()
                .filter(candidate -> importId == null || importId.equals(candidate.importId()))
                .sorted(Comparator.comparing(DocumentRequirementCandidate::createdAt));
    }

    private java.util.stream.Stream<DocumentWebhookEvent> filteredWebhookEvents(DocumentWebhookEventQuery query) {
        return webhookEvents.values().stream()
                .filter(event -> query.sourceCode() == null || query.sourceCode().equalsIgnoreCase(event.sourceCode()))
                .filter(event -> query.status() == null || event.status() == query.status())
                .sorted(Comparator.comparing(DocumentWebhookEvent::receivedAt).reversed());
    }
}
