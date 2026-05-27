package com.songhg.veri.agent.document.infrastructure;

import com.songhg.veri.agent.document.application.query.DocumentCandidateQuery;
import com.songhg.veri.agent.document.application.query.DocumentImportQuery;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.query.DocumentParseFeedbackQuery;
import com.songhg.veri.agent.document.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.document.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentFieldMapping;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportPayload;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
@Primary
public class InMemoryDocumentInputRepository implements DocumentInputRepository {

    private static final UUID DEFAULT_MAPPING_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");

    private final ConcurrentHashMap<UUID, DocumentSourceConfig> sources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentFieldMapping> mappings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentImportRecord> imports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentImportPayload> importPayloads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentRequirementCandidate> candidates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentParseFeedbackSample> parseFeedbackSamples = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DocumentWebhookEvent> webhookEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> retentionArchive = new ConcurrentHashMap<>();

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
    public boolean markImportStatus(
            UUID id,
            DocumentImportStatus expectedStatus,
            DocumentImportStatus nextStatus,
            Instant updatedAt
    ) {
        DocumentImportRecord updated = imports.computeIfPresent(id, (ignored, existing) -> {
            if (existing.status() != expectedStatus) {
                return existing;
            }
            return new DocumentImportRecord(
                    existing.id(),
                    existing.projectId(),
                    existing.sourceId(),
                    existing.sourceCode(),
                    existing.sourceType(),
                    existing.sourceRef(),
                    existing.sourceUrl(),
                    existing.title(),
                    nextStatus,
                    existing.totalParsed(),
                    existing.totalCreated(),
                    existing.createdRequirementIds(),
                    existing.errorMessage(),
                    existing.rawDigest(),
                    existing.createdAt(),
                    updatedAt
            );
        });
        return updated != null && updated.status() == nextStatus;
    }

    @Override
    public DocumentImportPayload saveImportPayload(DocumentImportPayload payload) {
        importPayloads.put(payload.importId(), payload);
        return payload;
    }

    @Override
    public Optional<DocumentImportPayload> importPayload(UUID importId) {
        return Optional.ofNullable(importPayloads.get(importId));
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
    public List<DocumentRequirementCandidate> candidates(DocumentCandidateQuery query) {
        return filteredCandidates(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countCandidates(DocumentCandidateQuery query) {
        return filteredCandidates(query).count();
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
    public boolean markCandidateStatus(
            UUID id,
            DocumentCandidateStatus expectedStatus,
            DocumentCandidateStatus nextStatus,
            Instant updatedAt
    ) {
        DocumentRequirementCandidate updated = candidates.computeIfPresent(id, (ignored, existing) -> {
            if (existing.status() != expectedStatus) {
                return existing;
            }
            return new DocumentRequirementCandidate(
                    existing.id(),
                    existing.importId(),
                    existing.projectId(),
                    existing.title(),
                    existing.description(),
                    existing.priority(),
                    existing.acceptanceCriteria(),
                    existing.tags(),
                    nextStatus,
                    existing.sourceRef(),
                    existing.sourceFragment(),
                    existing.externalRequirementId(),
                    existing.confidence(),
                    existing.parseSource(),
                    existing.modelInvocationId(),
                    existing.modelProviderName(),
                    existing.modelName(),
                    existing.assetRequirementId(),
                    existing.errorMessage(),
                    existing.ignoredReason(),
                    existing.confirmedBy(),
                    existing.confirmedAt(),
                    existing.version() + 1,
                    existing.createdAt(),
                    updatedAt
            );
        });
        return updated != null && updated.status() == nextStatus;
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
    public List<DocumentParseFeedbackSample> parseFeedbackSamples(DocumentParseFeedbackQuery query) {
        return filteredParseFeedbackSamples(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countParseFeedbackSamples(DocumentParseFeedbackQuery query) {
        return filteredParseFeedbackSamples(query).count();
    }

    @Override
    public DocumentParseFeedbackSample saveParseFeedbackSample(DocumentParseFeedbackSample sample) {
        parseFeedbackSamples.put(sample.id(), sample);
        return sample;
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
    public List<DocumentWebhookEvent> retryableWebhookEvents(int maxAttempts, int limit) {
        int cappedLimit = Math.max(1, limit);
        return webhookEvents.values().stream()
                .filter(event -> event.status() == WebhookEventStatus.FAILED)
                .filter(event -> event.signatureStatus() == WebhookSignatureStatus.VALID)
                .filter(event -> event.rawPayload() != null)
                .filter(event -> event.retryCount() < maxAttempts)
                .sorted(Comparator.comparing(event -> firstInstant(event.processedAt(), event.receivedAt())))
                .limit(cappedLimit)
                .toList();
    }

    @Override
    public DocumentWebhookEvent saveWebhookEvent(DocumentWebhookEvent event) {
        webhookEvents.put(event.id(), event);
        return event;
    }

    @Override
    public boolean markWebhookEventStatus(
            UUID id,
            WebhookEventStatus expectedStatus,
            WebhookEventStatus nextStatus,
            Instant updatedAt
    ) {
        DocumentWebhookEvent updated = webhookEvents.computeIfPresent(id, (ignored, existing) -> {
            if (existing.status() != expectedStatus) {
                return existing;
            }
            return new DocumentWebhookEvent(
                    existing.id(),
                    existing.sourceId(),
                    existing.importId(),
                    existing.sourceCode(),
                    existing.eventId(),
                    existing.idempotencyKey(),
                    existing.eventType(),
                    existing.eventVersion(),
                    existing.signatureStatus(),
                    nextStatus,
                    existing.payloadDigest(),
                    existing.rawPayload(),
                    existing.errorMessage(),
                    existing.retryCount(),
                    existing.replayBy(),
                    existing.replayAt(),
                    existing.replayTraceId(),
                    existing.receivedAt(),
                    updatedAt
            );
        });
        return updated != null && updated.status() == nextStatus;
    }

    @Override
    public int archiveImportsBefore(Instant before) {
        return archiveImports(imports.values()
                .stream()
                .filter(record -> record.createdAt().isBefore(before))
                .toList());
    }

    @Override
    public int archiveCandidatesByImportCreatedBefore(Instant before) {
        List<UUID> importIds = imports.values()
                .stream()
                .filter(record -> record.createdAt().isBefore(before))
                .map(DocumentImportRecord::id)
                .toList();
        return archiveCandidates(candidates.values()
                .stream()
                .filter(candidate -> importIds.contains(candidate.importId()))
                .toList());
    }

    @Override
    public int archiveWebhookEventsBefore(Instant before) {
        return archiveWebhookEvents(webhookEvents.values()
                .stream()
                .filter(event -> event.receivedAt().isBefore(before))
                .toList());
    }

    @Override
    public int cleanupImportsBefore(Instant before) {
        List<UUID> importIds = imports.values()
                .stream()
                .filter(record -> record.createdAt().isBefore(before))
                .map(DocumentImportRecord::id)
                .toList();
        importIds.forEach(imports::remove);
        candidates.entrySet().removeIf(entry -> importIds.contains(entry.getValue().importId()));
        return importIds.size();
    }

    @Override
    public int cleanupWebhookEventsBefore(Instant before) {
        int beforeSize = webhookEvents.size();
        webhookEvents.entrySet().removeIf(entry -> entry.getValue().receivedAt().isBefore(before));
        return beforeSize - webhookEvents.size();
    }

    public long archivedRecordCount(String recordType) {
        return retentionArchive.values()
                .stream()
                .filter(snapshot -> recordType == null || recordType.equals(snapshot.get("recordType")))
                .count();
    }

    private int archiveImports(List<DocumentImportRecord> records) {
        int archived = 0;
        for (DocumentImportRecord record : records) {
            if (retentionArchive.putIfAbsent("IMPORT:" + record.id(), Map.of(
                    "recordType", "IMPORT",
                    "recordId", record.id().toString(),
                    "projectId", record.projectId(),
                    "sourceType", record.sourceType().name(),
                    "sourceRef", nullToEmpty(record.sourceRef()),
                    "status", record.status().name(),
                    "rawDigest", nullToEmpty(record.rawDigest()),
                    "createdAt", record.createdAt().toString()
            )) == null) {
                archived++;
            }
        }
        return archived;
    }

    private int archiveCandidates(List<DocumentRequirementCandidate> records) {
        int archived = 0;
        for (DocumentRequirementCandidate candidate : records) {
            if (retentionArchive.putIfAbsent("CANDIDATE:" + candidate.id(), Map.of(
                    "recordType", "CANDIDATE",
                    "recordId", candidate.id().toString(),
                    "importId", candidate.importId().toString(),
                    "projectId", candidate.projectId(),
                    "title", candidate.title(),
                    "status", candidate.status().name(),
                    "sourceRef", nullToEmpty(candidate.sourceRef()),
                    "externalRequirementId", nullToEmpty(candidate.externalRequirementId()),
                    "createdAt", candidate.createdAt().toString()
            )) == null) {
                archived++;
            }
        }
        return archived;
    }

    private int archiveWebhookEvents(List<DocumentWebhookEvent> records) {
        int archived = 0;
        for (DocumentWebhookEvent event : records) {
            if (retentionArchive.putIfAbsent("WEBHOOK_EVENT:" + event.id(), Map.of(
                    "recordType", "WEBHOOK_EVENT",
                    "recordId", event.id().toString(),
                    "sourceCode", event.sourceCode(),
                    "eventId", nullToEmpty(event.eventId()),
                    "idempotencyKey", nullToEmpty(event.idempotencyKey()),
                    "eventType", nullToEmpty(event.eventType()),
                    "status", event.status().name(),
                    "signatureStatus", event.signatureStatus().name(),
                    "payloadDigest", event.payloadDigest(),
                    "receivedAt", event.receivedAt().toString()
            )) == null) {
                archived++;
            }
        }
        return archived;
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

    private java.util.stream.Stream<DocumentRequirementCandidate> filteredCandidates(DocumentCandidateQuery query) {
        String sourceRef = lower(query.sourceRef());
        String keyword = lower(query.keyword());
        return candidates.values().stream()
                .filter(candidate -> query.importId() == null || query.importId().equals(candidate.importId()))
                .filter(candidate -> query.status() == null || query.status() == candidate.status())
                .filter(candidate -> sourceRef == null || contains(candidate.sourceRef(), sourceRef))
                .filter(candidate -> keyword == null || candidateMatchesKeyword(candidate, keyword))
                .sorted(Comparator.comparing(DocumentRequirementCandidate::createdAt));
    }

    private boolean candidateMatchesKeyword(DocumentRequirementCandidate candidate, String keyword) {
        return contains(candidate.title(), keyword)
                || contains(candidate.description(), keyword)
                || contains(candidate.acceptanceCriteria(), keyword)
                || contains(candidate.tags(), keyword)
                || contains(candidate.sourceFragment(), keyword)
                || contains(candidate.externalRequirementId(), keyword);
    }

    private java.util.stream.Stream<DocumentParseFeedbackSample> filteredParseFeedbackSamples(DocumentParseFeedbackQuery query) {
        return parseFeedbackSamples.values().stream()
                .filter(sample -> query.candidateId() == null || query.candidateId().equals(sample.candidateId()))
                .filter(sample -> query.importId() == null || query.importId().equals(sample.importId()))
                .filter(sample -> query.projectId() == null || query.projectId().equals(sample.projectId()))
                .filter(sample -> query.parseSource() == null || query.parseSource().equalsIgnoreCase(sample.parseSource()))
                .filter(sample -> query.curationStatus() == null || query.curationStatus().equalsIgnoreCase(sample.curationStatus()))
                .sorted(Comparator.comparing(DocumentParseFeedbackSample::createdAt).reversed());
    }

    private boolean contains(String value, String keyword) {
        String normalized = lower(value);
        return normalized != null && normalized.contains(keyword);
    }

    private String lower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Instant firstInstant(Instant first, Instant second) {
        return first == null ? second : first;
    }

    private java.util.stream.Stream<DocumentWebhookEvent> filteredWebhookEvents(DocumentWebhookEventQuery query) {
        return webhookEvents.values().stream()
                .filter(event -> query.sourceId() == null || query.sourceId().equals(event.sourceId()))
                .filter(event -> query.sourceCode() == null || query.sourceCode().equalsIgnoreCase(event.sourceCode()))
                .filter(event -> query.eventType() == null || query.eventType().equalsIgnoreCase(event.eventType()))
                .filter(event -> query.status() == null || event.status() == query.status())
                .filter(event -> query.receivedFrom() == null || !event.receivedAt().isBefore(query.receivedFrom()))
                .filter(event -> query.receivedTo() == null || !event.receivedAt().isAfter(query.receivedTo()))
                .sorted(Comparator.comparing(DocumentWebhookEvent::receivedAt).reversed());
    }
}
